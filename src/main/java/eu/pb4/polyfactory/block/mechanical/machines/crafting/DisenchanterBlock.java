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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
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
        private final ItemDisplayElement main;
        private final ItemDisplayElement gears;

        private Model(BlockState state) {
            this.main = ItemDisplayElementUtil.createSimple(state.getBlock().asItem());
            this.main.setScale(new Vector3f(2));
            this.main.setTranslation(new Vector3f(0, 0.5f, 0));
            this.gears = LodItemDisplayElement.createSimple(GenericParts.SMALL_GEAR_DOUBLE.get(), this.getUpdateRate(), 0.3f, 0.5f);
            this.gears.setViewRange(0.4f);

            this.updateStatePos(state);
            this.updateAnimation(true, 0, isNegativeRotation(state.getValue(INPUT_FACING)));
            this.addElement(this.main);
            this.addElement(this.gears);
        }

        private static boolean isNegativeRotation(Direction dir) {
            return (dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE) == (dir.getAxis() == Direction.Axis.X);
        }

        private void updateStatePos(BlockState state) {
            var direction = state.getValue(INPUT_FACING);
            this.main.setYaw(direction.toYRot());
            this.gears.setYaw(direction.toYRot());
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
        }
    }
}