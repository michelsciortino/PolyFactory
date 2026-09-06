package eu.pb4.polyfactory.block.mechanical.machines.crafting;

import eu.pb4.polyfactory.block.FactoryBlockEntities;
import eu.pb4.polyfactory.enchant.EnchantmentTransferHelper;
import eu.pb4.polyfactory.fluid.FluidInstance;
import eu.pb4.polyfactory.fluid.FluidContainerImpl;
import eu.pb4.polyfactory.block.mechanical.RotationUser;
import eu.pb4.polyfactory.block.mechanical.machines.TallItemMachineBlockEntity;
import eu.pb4.polyfactory.block.other.ItemOutputBufferBlock;
import eu.pb4.polyfactory.util.FactoryUtil;
import eu.pb4.polyfactory.util.inventory.SubContainer;
import eu.pb4.polyfactory.util.movingitem.SimpleMovingItemContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedBookItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class DisenchanterBlockEntity extends TallItemMachineBlockEntity implements OutputContainerOwner, SimpleMovingItemContainerBlockEntity {
    public static final int SOURCE_SLOT = 0;
    public static final int BLANK_BOOK_SLOT = 1;
    public static final int OUTPUT_BOOK_SLOT = 2;
    public static final int OUTPUT_TOOL_SLOT = 3;

    private final SimpleMovingItemContainer[] containers = new SimpleMovingItemContainer[]{
            new SimpleMovingItemContainer(0, this::addMoving, this::removeMoving),
            new SimpleMovingItemContainer(),
            new SimpleMovingItemContainer(2, this::addMoving, this::removeMoving),
            new SimpleMovingItemContainer(3, this::addMoving, this::removeMoving)
    };

    private final Container outputContainer = new SubContainer(this, OUTPUT_BOOK_SLOT);
    private final FluidContainerImpl container = FluidContainerImpl.singleFluid(1000 * 20, this::setChanged);

    private double process = 0;
    private boolean active = false;
    private boolean multi = false; // multi mode -> all selected enchants into one book
    private Component state;

    public DisenchanterBlockEntity(BlockPos pos, BlockState state) {
        super(FactoryBlockEntities.PRESS, pos, state); // TODO: new BE type when registering
    }

    public static <T extends BlockEntity> void ticker(Level world, BlockPos pos, BlockState state, T t) {
        var self = (DisenchanterBlockEntity) t;

        if (self.process < 0) {
            var speed = Math.max(Math.abs(RotationUser.getRotation(world, pos.above()).speed()), 0);
            self.process += speed / 120;
            self.active = true;
            return;
        }

        var sourceContainer = self.containers[0];
        if (sourceContainer.isContainerEmpty()) {
            self.process = 0;
            self.active = false;
            return;
        }

        var inputStack = sourceContainer.getStack();
        var map = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(inputStack);
        if (map.isEmpty()) {
            // nothing to do
            self.process = 0;
            self.active = false;
            return;
        }

        var rot = RotationUser.getRotation(world, pos.above());
        var speed = Math.max(Math.abs(rot.speed()), 0);
        var requiredSpeed = 1; // TODO: choose proper

        if (speed < requiredSpeed) {
            self.state = rot.getStateTextOrElse(rot.hasNoActiveProviders() ? Component.literal("Too slow") : Component.literal("Disconnected"));
            return;
        }

        self.active = true;
        self.process += speed / 100;

        if (self.process >= 1) {
            // attempt transfer
            int maxTransfer;
            if (self.multi) {
                // transfer all non-curse enchantments
                var filteredCount = (int) net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(inputStack).entrySet().stream().filter(e -> {
                    try {
                        return !e.getKey().isCurse();
                    } catch (NoSuchMethodError ex) {
                        return true;
                    }
                }).count();
                maxTransfer = Math.max(1, filteredCount);
            } else {
                maxTransfer = 1;
            }

            var helper = EnchantmentTransferHelper.transferEnchantments(inputStack, maxTransfer, self.multi);
            if (!helper.transferred.isEmpty()) {
                // ensure we have blank book(s)
                if (self.getItem(BLANK_BOOK_SLOT).isEmpty()) {
                    self.state = Component.literal("Need blank book");
                    self.process = 0;
                } else {
                    long cost = 0;
                    for (var inst : helper.transferred) {
                        cost += xpFluidCostForLevel(inst.level);
                    }
                    FluidInstance<?> top = self.container.topFluid();
                    if (top == null || self.container.get(top) < cost) {
                        self.state = Component.literal("No XP fluid");
                        self.process = 0;
                    } else {
                        self.container.extract(top, cost, false);

                        // consume one blank book
                        var bb = self.getItem(BLANK_BOOK_SLOT);
                        bb.shrink(1);
                        self.setItem(BLANK_BOOK_SLOT, bb);

                        // push book to output (try inserting into output buffer / connected inventories)
                        FactoryUtil.tryInsertingRegular(self.getOutputContainer(), helper.bookResult);

                        // if source is now fully disenchanted, move it to the output tool slot (preserve durability)
                        var outSource = helper.sourceResult;
                        var remaining = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(outSource);
                        if (remaining.isEmpty()) {
                            if (self.getItem(OUTPUT_TOOL_SLOT).isEmpty()) {
                                self.setItem(OUTPUT_TOOL_SLOT, outSource);
                                self.setItem(SOURCE_SLOT, ItemStack.EMPTY);
                            } else {
                                // couldn't put into dedicated output slot; leave the disenchanted tool in the source slot
                                self.setItem(SOURCE_SLOT, outSource);
                                self.state = Component.literal("Tool output blocked");
                            }
                        } else {
                            // not finished yet, keep the partially-disenchanted tool in the source slot
                            self.setItem(SOURCE_SLOT, outSource);
                        }

                        self.process = -0.6; // cooldown
                    }
                }
            } else {
                self.process = 0;
            }
        }
    }

    @Override
    public Container getOwnOutputContainer() {
        return this.outputContainer;
    }

    @Override
    public Container getOutputContainer() {
        return ItemOutputBufferBlock.getOutputContainer(this.outputContainer, this.level, this.getBlockPos(), this.getBlockState().getValue(PressBlock.INPUT_FACING).getOpposite());
    }

    @Override
    public boolean isOutputConnectedTo(Direction dir) {
        return this.getBlockState().getValue(PressBlock.INPUT_FACING).getOpposite() == dir;
    }

    @Override
    public @Nullable net.minecraft.world.Container getModel() {
        return null;
    }

    @Override
    public SimpleMovingItemContainer[] getContainers() {
        return this.containers;
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        view.putDouble("Progress", this.process);
        this.container.writeData(view, "fluid");
        super.saveAdditional(view);
    }

    @Override
    public void loadAdditional(ValueInput view) {
        this.process = view.getDoubleOr("Progress", 0);
        this.container.readData(view, "fluid");
        super.loadAdditional(view);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        var facing = this.getBlockState().getValue(PressBlock.INPUT_FACING);
        if (facing == side) {
            return new int[]{SOURCE_SLOT};
        } else if (facing.getOpposite() == side || side == Direction.DOWN) {
            return new int[]{OUTPUT_BOOK_SLOT, OUTPUT_TOOL_SLOT};
        } else if (facing.getClockWise().getAxis() == side.getAxis()) {
            return new int[]{BLANK_BOOK_SLOT};
        }
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        if (slot == SOURCE_SLOT) return dir == null || this.getBlockState().getValue(PressBlock.INPUT_FACING) == dir;
        if (slot == BLANK_BOOK_SLOT) return stack.getItem() == net.minecraft.world.item.Items.BOOK;
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        var facing = this.getBlockState().getValue(PressBlock.INPUT_FACING);
        return (slot == SOURCE_SLOT && facing == dir) || (slot != SOURCE_SLOT && (facing.getOpposite() == dir || dir == Direction.DOWN));
    }

    public void setMulti(boolean multi) {
        this.multi = multi;
        this.setChanged();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // attach model if needed
        super.setItem(slot, stack);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput view) {
        super.removeComponentsFromTag(view);
        view.discard("fluid");
    }

    private static long xpFluidCostForLevel(int level) {
        final long BASE = 1000L;
        // log10(level + 1) * BASE rounded up
        double v = Math.log10(level + 1);
        return (long) Math.ceil(BASE * v);
    }
}
