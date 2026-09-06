package eu.pb4.polyfactory.block.mechanical.machines.crafting;

import eu.pb4.factorytools.api.util.WorldPointer;
import eu.pb4.factorytools.api.virtualentity.ItemDisplayElementUtil;
import eu.pb4.factorytools.api.virtualentity.LodItemDisplayElement;
import eu.pb4.polyfactory.block.FactoryBlockEntities;
import eu.pb4.polyfactory.block.fluids.FluidInput;
import eu.pb4.polyfactory.block.fluids.transport.PipeConnectable;
import eu.pb4.polyfactory.block.mechanical.RotationUser;
import eu.pb4.polyfactory.block.mechanical.machines.TallItemMachineBlock;
import eu.pb4.polyfactory.item.FactoryItems;
import eu.pb4.polyfactory.models.GenericParts;
import eu.pb4.polyfactory.models.RotationAwareModel;
import eu.pb4.polyfactory.util.movingitem.MovingItemConsumer;
import eu.pb4.polyfactory.util.movingitem.MovingItemContainerHolder;
import eu.pb4.polyfactory.util.movingitem.MovingItemProvider;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class DisenchanterBlock extends TallItemMachineBlock implements PipeConnectable, FluidInput.Getter, MovingItemConsumer, MovingItemProvider {
    public static final eu.pb4.factorytools.api.util.LazyItemStack FLUID_MODEL = ItemDisplayElementUtil.getModel(eu.pb4.polyfactory.util.FactoryUtil.id("block/disenchanter_fluid"));
    public static final eu.pb4.factorytools.api.util.LazyItemStack BOOK_MODEL = ItemDisplayElementUtil.getModel(eu.pb4.polyfactory.util.FactoryUtil.id("block/disenchanter_book"));

    public DisenchanterBlock(Properties props) {
        super(props);
    }

    @Override
    public boolean pushItemTo(WorldPointer self, Direction pushDirection, Direction relative, BlockPos conveyorPos, MovingItemContainerHolder conveyor) {
        if (self.getBlockState().getValue(PART) == Part.TOP || conveyor.isContainerEmpty()) {
            return false;
        }

        var facing = self.getBlockState().getValue(INPUT_FACING);
        var be = (DisenchanterBlockEntity) self.getBlockEntity();

        if (relative == facing) {
            // front face: blank book storage (any item is accepted, only a book makes the machine function)
            var stack = be.getItem(DisenchanterBlockEntity.BLANK_BOOK_SLOT);
            if (stack.isEmpty()) {
                be.setItem(DisenchanterBlockEntity.BLANK_BOOK_SLOT, conveyor.pullAndDestroy().get());
                return true;
            }

            var container = conveyor.getContainer();

            if (ItemStack.isSameItemSameComponents(container.get(), stack)) {
                var inserted = Math.min(container.get().getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(inserted);
                container.get().shrink(inserted);
            }

            if (container.get().isEmpty()) {
                conveyor.clearContainer();
            }

            return true;
        }

        if (relative != facing.getClockWise()) {
            // only the left face accepts the enchanted item input
            return false;
        }

        var source = be.getContainerHolder(DisenchanterBlockEntity.SOURCE_SLOT);
        if (source.isContainerEmpty()) {
            source.pushAndAttach(conveyor.pullAndRemove());
            return true;
        }

        var targetStack = source.getContainer().get();
        var sourceStack = conveyor.getContainer().get();

        if (ItemStack.isSameItemSameComponents(targetStack, sourceStack)) {
            var count = Math.min(targetStack.getCount() + sourceStack.getCount(), source.getMaxStackCount(sourceStack));
            if (count != targetStack.getCount()) {
                var moved = count - targetStack.getCount();
                targetStack.grow(moved);
                sourceStack.shrink(moved);
            }

            if (sourceStack.isEmpty()) {
                conveyor.clearContainer();
            }
        }

        return true;
    }

    @Override
    public void getItemFrom(WorldPointer self, Direction pushDirection, Direction relative, BlockPos conveyorPos, MovingItemContainerHolder conveyor) {
        if (!conveyor.isContainerEmpty() || self.getBlockState().getValue(PART) == Part.TOP) {
            return;
        }

        var facing = self.getBlockState().getValue(INPUT_FACING);
        var be = (DisenchanterBlockEntity) self.getBlockEntity();

        if (relative == facing.getOpposite()) {
            tryOutputFromSlot(be, DisenchanterBlockEntity.OUTPUT_BOOK_SLOT, pushDirection, facing, conveyor);
        } else if (relative == facing.getCounterClockWise()) {
            tryOutputFromSlot(be, DisenchanterBlockEntity.OUTPUT_TOOL_SLOT, pushDirection, facing, conveyor);
        }
    }

    private static boolean tryOutputFromSlot(DisenchanterBlockEntity be, int slot, Direction pushDirection, Direction inputDir, MovingItemContainerHolder conveyor) {
        var out = be.getContainerHolder(slot);

        if (out.isContainerEmpty()) {
            return false;
        }
        var stack = out.getContainer().get();
        var amount = Math.min(stack.getCount(), out.getMaxStackCount(stack));

        if (stack.getCount() == amount) {
            conveyor.pushAndAttach(out.pullAndRemove());
        } else {
            stack.shrink(amount);
            conveyor.setMovementPosition(pushDirection == inputDir.getOpposite() ? 0 : 0.5);
            conveyor.pushNew(stack.copyWithCount(amount));
        }

        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world instanceof ServerLevel && type == FactoryBlockEntities.DISENCHANTER ? DisenchanterBlockEntity::ticker : null;
    }

    @Override
    protected BlockEntity createSourceBlockEntity(BlockPos pos, BlockState state) {
        return new DisenchanterBlockEntity(pos, state);
    }

    @Override
    protected ElementHolder createModel(ServerLevel serverWorld, BlockPos pos, BlockState initialBlockState) {
        return new Model(initialBlockState);
    }

    @Override
    public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
        return Blocks.ANVIL.defaultBlockState();
    }

    @Override
    public boolean canPipeConnect(LevelReader world, BlockPos pos, BlockState state, Direction dir) {
        return (state.getValue(PART) == Part.MAIN && dir == Direction.DOWN) || (state.getValue(PART) == Part.TOP && dir == Direction.UP);
    }

    @Override
    public FluidInput getFluidInput(ServerLevel world, BlockPos pos, Direction direction) {
        var state = world.getBlockState(pos);
        var mainPos = state.getValue(PART) == Part.MAIN ? pos : pos.below();
        return world.getBlockEntity(mainPos) instanceof FluidInput input ? input : null;
    }

    public static final class Model extends RotationAwareModel {
        // Both books hover in front of the anvil, at the middle of its height, in the block model's
        // own coordinates - see DisenchanterBlockEntity.modelPoint for how those map to the world.
        private static final float BOOK_LEFT_X = 10;
        private static final float BOOK_RIGHT_X = 6;
        private static final float BOOK_Y = 18.6f;
        private static final float BOOK_Z = 2.6f;
        private static final float BOOK_SCALE = 0.25f;
        private static final float STRAY_ITEM_SCALE = 0.375f;
        private static final float BOOK_TILT = 80 * Mth.DEG_TO_RAD;
        private static final double BOOK_LOOK_RANGE = 4;

        private final ItemDisplayElement main;
        private final ItemDisplayElement gears;
        private final ItemDisplayElement fluid;
        private final ItemDisplayElement blankBook;
        private final ItemDisplayElement enchantedBook;
        private boolean fluidVisible;
        private ItemStack blankSlotStack = ItemStack.EMPTY;
        private boolean blankSlotIsBook;
        private boolean enchantedBookVisible;
        private float bookRotation;
        private float bookTargetRotation;

        private Model(BlockState state) {
            this.main = ItemDisplayElementUtil.createSimple(state.getBlock().asItem());
            this.main.setScale(new Vector3f(2));
            this.main.setTranslation(new Vector3f(0, 0.5f, 0));
            this.gears = LodItemDisplayElement.createSimple(GenericParts.SMALL_GEAR_DOUBLE.get(), this.getUpdateRate(), 0.3f, 0.5f);
            this.gears.setViewRange(0.4f);
            this.fluid = ItemDisplayElementUtil.createSimple();
            this.fluid.setScale(new Vector3f(2));
            this.fluid.setTranslation(new Vector3f(0, 0.5f, 0));
            this.fluid.setViewRange(0.6f);
            this.blankBook = LodItemDisplayElement.createSimple(ItemStack.EMPTY, 2, 0.4f, 0.8f);
            this.blankBook.setViewRange(0.5f);
            this.enchantedBook = LodItemDisplayElement.createSimple(ItemStack.EMPTY, 2, 0.4f, 0.8f);
            this.enchantedBook.setViewRange(0.5f);

            this.updateStatePos(state);
            this.updateAnimation(true, 0, isNegativeRotation(state.getValue(INPUT_FACING)));
            this.addElement(this.main);
            this.addElement(this.gears);
            this.addElement(this.fluid);
            this.addElement(this.blankBook);
            this.addElement(this.enchantedBook);
        }

        public void setFluidVisible(boolean visible) {
            if (this.fluidVisible == visible) {
                return;
            }
            this.fluidVisible = visible;
            this.fluid.setItem(visible ? FLUID_MODEL.get().copy() : ItemStack.EMPTY);
        }

        /**
         * The blank book port takes any item, so anything that isn't a book is shown as itself
         * rather than being dressed up as one.
         */
        public void setChamberContents(ItemStack blankSlot, boolean enchantedBook) {
            if (!ItemStack.isSameItemSameComponents(this.blankSlotStack, blankSlot)) {
                this.blankSlotStack = blankSlot.copy();
                this.blankSlotIsBook = blankSlot.is(Items.BOOK);
                this.blankBook.setItem(blankSlot.isEmpty() ? ItemStack.EMPTY
                        : this.blankSlotIsBook ? bookModel(false) : blankSlot.copy());
            }

            if (this.enchantedBookVisible != enchantedBook) {
                this.enchantedBookVisible = enchantedBook;
                this.enchantedBook.setItem(enchantedBook ? bookModel(true) : ItemStack.EMPTY);
            }
        }

        private static ItemStack bookModel(boolean glowing) {
            var stack = BOOK_MODEL.get().copy();
            if (glowing) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            return stack;
        }

        private static boolean isNegativeRotation(Direction dir) {
            return (dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE) == (dir.getAxis() == Direction.Axis.X);
        }

        private void updateStatePos(BlockState state) {
            var direction = state.getValue(INPUT_FACING);
            this.main.setYaw(direction.toYRot());
            this.gears.setYaw(direction.toYRot());
            this.fluid.setYaw(direction.toYRot());
            this.blankBook.setYaw(direction.toYRot());
            this.enchantedBook.setYaw(direction.toYRot());
        }

        /**
         * Mirrors the hover of {@code minecraft:enchanting_table}'s book: a slow bob, and a spin that
         * turns the open side towards the closest nearby player, drifting on its own when there is none.
         */
        private void updateBooks() {
            var facing = this.blockState().getValue(INPUT_FACING);
            var pos = this.blockPos();
            var player = this.getAttachment() == null ? null : this.getAttachment().getWorld()
                    .getNearestPlayer(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, BOOK_LOOK_RANGE, false);

            if (player != null) {
                // The book spins inside the model's own frame, so aim it with the player offset expressed there.
                var right = facing.getCounterClockWise();
                var dx = player.getX() - (pos.getX() + 0.5);
                var dz = player.getZ() - (pos.getZ() + 0.5);
                this.bookTargetRotation = (float) Mth.atan2(
                        -(dx * right.getStepX() + dz * right.getStepZ()),
                        -(dx * facing.getStepX() + dz * facing.getStepZ()));
            } else {
                // Vanilla drifts by 0.02 every tick; this runs every other one.
                this.bookTargetRotation += 0.04f;
            }

            this.bookRotation += wrapRadians(this.bookTargetRotation - this.bookRotation) * 0.4f;
            this.bookRotation = wrapRadians(this.bookRotation);
            this.bookTargetRotation = wrapRadians(this.bookTargetRotation);

            var bob = Mth.sin(this.getTick() * 0.1f) * 0.01f;
            this.updateHoverTransform(this.blankBook, BOOK_LEFT_X, bob, this.blankSlotIsBook);
            this.updateHoverTransform(this.enchantedBook, BOOK_RIGHT_X, bob, true);
        }

        /**
         * A book lies open and tilted the way the enchanting table's does; a stray item just stands
         * upright, both of them turning with the same hover.
         */
        private void updateHoverTransform(ItemDisplayElement element, float modelX, float bob, boolean asBook) {
            var mat = mat();
            mat.translate((8 - modelX) / 16, BOOK_Y / 16 + bob, (8 - BOOK_Z) / 16);
            mat.rotateY(this.bookRotation);
            if (asBook) {
                mat.rotateZ(BOOK_TILT);
            }
            mat.scale(asBook ? BOOK_SCALE : STRAY_ITEM_SCALE);
            element.setTransformation(mat);
        }

        private static float wrapRadians(float value) {
            var wrapped = value % Mth.TWO_PI;
            if (wrapped >= Mth.PI) {
                wrapped -= Mth.TWO_PI;
            }
            if (wrapped < -Mth.PI) {
                wrapped += Mth.TWO_PI;
            }
            return wrapped;
        }

        private void updateAnimation(boolean updateGears, float rotation, boolean negative) {
            var mat = mat();
            mat.translate(0, 0.5f, 0);

            if (updateGears) {
                mat.rotateY(negative ? Mth.HALF_PI : -Mth.HALF_PI);
                mat.translate(0, 1 / 8f - 2 / 16f + 0.0001f, 0);
                mat.rotateZ(-rotation);
                this.gears.setTransformation(mat);
            }
        }

        @Override
        public void notifyUpdate(HolderAttachment.UpdateType updateType) {
            if (updateType == BlockBoundAttachment.BLOCK_STATE_UPDATE) {
                this.updateStatePos(this.blockState());
            }
        }

        @Override
        protected void onTick() {
            var updateGears = this.getTick() % this.getUpdateRate() == 0;
            var dir = this.blockState().getValue(INPUT_FACING);

            this.updateAnimation(updateGears,
                    updateGears ? RotationUser.getRotation(this.getAttachment().getWorld(), this.blockPos().above()).rotation() : 0,
                    isNegativeRotation(dir));

            this.gears.startInterpolationIfDirty();

            if ((!this.blankSlotStack.isEmpty() || this.enchantedBookVisible) && this.getTick() % 2 == 0) {
                this.updateBooks();
                this.blankBook.startInterpolationIfDirty();
                this.enchantedBook.startInterpolationIfDirty();
            }
        }
    }
}