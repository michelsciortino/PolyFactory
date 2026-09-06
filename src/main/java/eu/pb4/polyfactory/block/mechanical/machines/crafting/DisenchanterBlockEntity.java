package eu.pb4.polyfactory.block.mechanical.machines.crafting;

import eu.pb4.factorytools.api.virtualentity.BlockModel;
import eu.pb4.mapcanvas.api.font.DefaultFonts;
import eu.pb4.polyfactory.block.FactoryBlockEntities;
import eu.pb4.polyfactory.block.fluids.FluidInput;
import eu.pb4.polyfactory.block.mechanical.RotationUser;
import eu.pb4.polyfactory.block.mechanical.conveyor.SimpleMovingItemContainerBlockEntity;
import eu.pb4.polyfactory.block.mechanical.machines.TallItemMachineBlock;
import eu.pb4.polyfactory.block.mechanical.machines.TallItemMachineBlockEntity;
import eu.pb4.polyfactory.block.other.ItemOutputBufferBlock;
import eu.pb4.polyfactory.block.other.OutputContainerOwner;
import eu.pb4.polyfactory.enchant.EnchantmentTransferHelper;
import eu.pb4.polyfactory.fluid.FactoryFluids;
import eu.pb4.polyfactory.fluid.FluidContainer;
import eu.pb4.polyfactory.fluid.FluidContainerImpl;
import eu.pb4.polyfactory.fluid.FluidContainerUtil;
import eu.pb4.polyfactory.fluid.FluidInteractionMode;
import eu.pb4.polyfactory.item.FactoryItemTags;
import eu.pb4.polyfactory.ui.GuiTextures;
import eu.pb4.polyfactory.ui.UiResourceCreator;
import eu.pb4.polyfactory.ui.fluid.FluidTextures;
import eu.pb4.polyfactory.util.FactoryUtil;
import eu.pb4.polyfactory.util.inventory.SubContainer;
import eu.pb4.polyfactory.util.language.TextUncenterer;
import eu.pb4.polyfactory.util.movingitem.SimpleMovingItemContainer;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class DisenchanterBlockEntity extends TallItemMachineBlockEntity implements OutputContainerOwner, SimpleMovingItemContainerBlockEntity, FluidInput.ContainerBased {
    public static final int SOURCE_SLOT = 0;
    public static final int BLANK_BOOK_SLOT = 1;
    public static final int OUTPUT_BOOK_SLOT = 2;
    public static final int OUTPUT_TOOL_SLOT = 3;

    private static final int[] SOURCE_SLOTS = {SOURCE_SLOT};
    private static final int[] BLANK_BOOK_SLOTS = {BLANK_BOOK_SLOT};
    private static final int[] OUTPUT_BOOK_SLOTS = {OUTPUT_BOOK_SLOT};
    private static final int[] OUTPUT_TOOL_SLOTS = {OUTPUT_TOOL_SLOT};
    private static final double REQUIRED_SPEED = 1;
    private static final long BASE_COST = 1000L;
    private static final Component NEED_BOOK_TEXT = Component.translatable("text.polyfactory.state.need_book").withStyle(ChatFormatting.YELLOW);
    private static final Component NEED_XP_FLUID_TEXT = Component.translatable("text.polyfactory.state.need_xp_fluid").withStyle(ChatFormatting.YELLOW);
    private static final Component TOOL_OUTPUT_BLOCKED_TEXT = Component.translatable("text.polyfactory.state.tool_output_blocked").withStyle(ChatFormatting.YELLOW);

    // Anchors in the block model's own coordinates (assets/polyfactory/models/block/disenchanter.json).
    // The model is two blocks tall (0-32) and rendered so one model unit is 1/16 of a block, with
    // model -Z pointing at INPUT_FACING and model +X at its clockwise side. See modelPoint.
    private static final double ANVIL_Z = 6;
    private static final double ANVIL_ITEM_Y = 21.7;
    private static final double ANVIL_LEFT_X = 9.4;
    private static final double ANVIL_RIGHT_X = 6.6;
    private static final float ANVIL_ITEM_SCALE = 0.3f;

    // Only the two worked items sit on the anvil; both books are drawn by the model itself, hovering
    // in front of it, so their containers stay detached from it.
    private final SimpleMovingItemContainer[] containers = new SimpleMovingItemContainer[]{
            new SimpleMovingItemContainer(SOURCE_SLOT, this::addMoving, this::removeMoving),
            new SimpleMovingItemContainer(),
            new SimpleMovingItemContainer(),
            new SimpleMovingItemContainer(OUTPUT_TOOL_SLOT, this::addMoving, this::removeMoving)
    };

    private static final int[] ANVIL_SLOTS = {SOURCE_SLOT, OUTPUT_TOOL_SLOT};

    // Book output only: the tool output is a separate port with its own face, so a produced book
    // must never spill into it once this slot is full.
    private final Container outputContainer = new SubContainer(this, OUTPUT_BOOK_SLOT, OUTPUT_BOOK_SLOT + 1);
    private final FluidContainerImpl fluidContainer = FluidContainerImpl.singleFluid(1000 * 20, this::setChanged);

    private double process = 0;
    private boolean active;
    private boolean multi;
    @Nullable
    private DisenchanterBlock.Model model;
    @Nullable
    private Direction modelFacing;

    public DisenchanterBlockEntity(BlockPos pos, BlockState state) {
        super(FactoryBlockEntities.DISENCHANTER, pos, state);
    }

    public static <T extends BlockEntity> void ticker(Level world, BlockPos pos, BlockState state, T t) {
        var self = (DisenchanterBlockEntity) t;

        if (self.model == null) {
            self.model = (DisenchanterBlock.Model) BlockBoundAttachment.get(world, pos).holder();
            for (var slot : ANVIL_SLOTS) {
                self.updatePosition(slot);
                self.containers[slot].maybeAdd(self.model);
            }
        }

        var facing = state.getValue(TallItemMachineBlock.INPUT_FACING);
        if (self.modelFacing != facing) {
            self.modelFacing = facing;
            for (var slot : ANVIL_SLOTS) {
                self.updatePosition(slot);
            }
        }

        if (self.model != null) {
            var topFluid = self.fluidContainer.topFluid();
            self.model.setFluidVisible(topFluid != null && topFluid.type() == FactoryFluids.EXPERIENCE);
            self.model.setChamberContents(self.containers[BLANK_BOOK_SLOT].getStack(),
                    !self.containers[OUTPUT_BOOK_SLOT].isContainerEmpty());
            self.model.tick();
        }

        self.state = null;

        if (self.process < 0) {
            var speed = Math.max(Math.abs(RotationUser.getRotation(world, pos.above()).speed()), 0);
            self.process += speed / 120;
            self.active = true;
            return;
        }

        var sourceContainer = self.containers[SOURCE_SLOT];
        if (sourceContainer.isContainerEmpty()) {
            self.process = 0;
            self.active = false;
            return;
        }

        var inputStack = sourceContainer.getStack();
        var enchantments = inputStack.getEnchantments();
        if (enchantments.isEmpty()) {
            self.process = 0;
            self.active = false;
            self.state = INCORRECT_ITEMS_TEXT;
            return;
        }

        var transferableCount = (int) enchantments.entrySet().stream().filter(entry -> isTransferable(entry.getKey())).count();
        if (transferableCount == 0) {
            self.process = 0;
            self.active = false;
            self.state = INCORRECT_ITEMS_TEXT;
            return;
        }

        var rot = RotationUser.getRotation(world, pos.above());
        var speed = Math.max(Math.abs(rot.speed()), 0);

        if (speed < REQUIRED_SPEED) {
            self.active = false;
            self.state = rot.getStateTextOrElse(rot.hasNoActiveProviders() ? TOO_SLOW_TEXT : TOO_SLOW_DISCONNECTED_TEXT);
            return;
        }

        var maxTransfer = self.multi ? transferableCount : 1;
        var helper = EnchantmentTransferHelper.transferEnchantments(inputStack, maxTransfer, self.multi);
        if (helper.transferred.isEmpty()) {
            self.process = 0;
            self.active = false;
            self.state = INCORRECT_ITEMS_TEXT;
            return;
        }

        if (self.getItem(BLANK_BOOK_SLOT).isEmpty()) {
            self.state = NEED_BOOK_TEXT;
            self.process = 0;
            self.active = false;
            return;
        }

        if (!self.canInsertBookResult(helper.bookResult)) {
            self.state = OUTPUT_FULL_TEXT;
            self.process = 0;
            self.active = false;
            return;
        }

        long cost = 0;
        for (var transferred : helper.transferred) {
            cost += xpFluidCostForLevel(transferred.level());
        }

        var top = self.fluidContainer.topFluid();
        if (top == null || top.type() != FactoryFluids.EXPERIENCE || self.fluidContainer.get(top) < cost) {
            self.state = NEED_XP_FLUID_TEXT;
            self.process = 0;
            self.active = false;
            return;
        }

        self.active = true;
        self.process += speed / 100;

        if (self.process < 1) {
            return;
        }

        self.fluidContainer.extract(top, cost, false);

        var books = self.getItem(BLANK_BOOK_SLOT);
        books.shrink(1);
        self.setItem(BLANK_BOOK_SLOT, books);

        var outBook = helper.bookResult.copy();
        FactoryUtil.tryInsertingRegular(self.getOutputContainer(), outBook);

        var outSource = helper.sourceResult;
        var remaining = outSource.getEnchantments();
        if (remaining.isEmpty()) {
            if (self.getItem(OUTPUT_TOOL_SLOT).isEmpty()) {
                self.setItem(OUTPUT_TOOL_SLOT, outSource);
                self.setItem(SOURCE_SLOT, ItemStack.EMPTY);
            } else {
                self.setItem(SOURCE_SLOT, outSource);
                self.state = TOOL_OUTPUT_BLOCKED_TEXT;
            }
        } else {
            self.setItem(SOURCE_SLOT, outSource);
        }

        self.process = -0.6;
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        this.writeInventoryView(view);
        view.putDouble("Progress", this.process);
        view.putBoolean("Multi", this.multi);
        this.fluidContainer.writeData(view, "fluid");
        super.saveAdditional(view);
    }

    @Override
    public void loadAdditional(ValueInput view) {
        this.readInventoryView(view);
        this.process = view.getDoubleOr("Progress", 0);
        this.multi = view.getBooleanOr("Multi", false);
        this.fluidContainer.readData(view, "fluid");
        super.loadAdditional(view);
    }

    @Override
    public void removeComponentsFromTag(ValueOutput view) {
        super.removeComponentsFromTag(view);
        view.discard("fluid");
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        // left = source item, front = blank book, right = tool output, rear = book output; up/down are fluid-only
        var facing = this.getBlockState().getValue(TallItemMachineBlock.INPUT_FACING);
        if (facing == side) {
            return BLANK_BOOK_SLOTS;
        } else if (facing.getClockWise() == side) {
            return SOURCE_SLOTS;
        } else if (facing.getCounterClockWise() == side) {
            return OUTPUT_TOOL_SLOTS;
        } else if (facing.getOpposite() == side) {
            return OUTPUT_BOOK_SLOTS;
        }
        return new int[0];
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SOURCE_SLOT || (slot == BLANK_BOOK_SLOT && stack.is(Items.BOOK));
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        var facing = this.getBlockState().getValue(TallItemMachineBlock.INPUT_FACING);
        if (slot == SOURCE_SLOT) {
            return dir == null || facing.getClockWise() == dir;
        }
        if (slot == BLANK_BOOK_SLOT) {
            // the front face only ever feeds this slot; a non-book item just sits there unusable
            return dir == null || facing == dir;
        }
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        var facing = this.getBlockState().getValue(TallItemMachineBlock.INPUT_FACING);
        return switch (slot) {
            case SOURCE_SLOT -> facing.getClockWise() == dir;
            case BLANK_BOOK_SLOT -> facing == dir;
            case OUTPUT_BOOK_SLOT -> facing.getOpposite() == dir;
            case OUTPUT_TOOL_SLOT -> facing.getCounterClockWise() == dir;
            default -> false;
        };
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        SimpleMovingItemContainerBlockEntity.super.setItem(slot, stack);
    }

    @Override
    public Container getOwnOutputContainer() {
        return this.outputContainer;
    }

    @Override
    public Container getOutputContainer() {
        return ItemOutputBufferBlock.getOutputContainer(this.outputContainer, this.level, this.getBlockPos(),
                this.getBlockState().getValue(TallItemMachineBlock.INPUT_FACING).getOpposite());
    }

    @Override
    public boolean isOutputConnectedTo(Direction dir) {
        return this.getBlockState().getValue(TallItemMachineBlock.INPUT_FACING).getOpposite() == dir;
    }

    @Override
    public @Nullable FluidContainer getFluidContainer(Direction direction) {
        // top (of the top part) and bottom (ground face) only; the top face is reached via DisenchanterBlock's FluidInput.Getter redirect
        return (direction == Direction.UP || direction == Direction.DOWN) ? this.fluidContainer : null;
    }

    @Override
    public FluidContainer getMainFluidContainer() {
        return this.fluidContainer;
    }

    @Override
    public InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hitResult.getDirection() == Direction.UP || hitResult.getDirection() == Direction.DOWN) {
            var interaction = FluidContainerUtil.interactWithInWorld(this.fluidContainer, player, itemStack, hand, FluidInteractionMode.ANY, FluidInteractionMode.INSERT);
            if (interaction != null) {
                return interaction;
            }
        }
        return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public InteractionResult onPlayerAttack(BlockState state, Level world, BlockPos pos, Player player) {
        var stack = player.getMainHandItem();
        if (stack.is(FactoryItemTags.FLUID_CONTAINER_INTERACTABLE_ON_ATTACK)) {
            var interaction = FluidContainerUtil.interactWithInWorld(this.fluidContainer, player, stack, InteractionHand.MAIN_HAND, FluidInteractionMode.ANY, FluidInteractionMode.EXTRACT);
            if (interaction != null) {
                return interaction;
            }
        }

        return super.onPlayerAttack(state, world, pos, player);
    }

    @Override
    public void updatePosition(int id) {
        var container = this.containers[id];
        if (container.isContainerEmpty()) {
            return;
        }

        var facing = this.getBlockState().getValue(TallItemMachineBlock.INPUT_FACING);

        // Both share the anvil's top face, the item being stripped on the left, the freed tool on the
        // right - the same sides their ports are on when facing the machine.
        if (id != SOURCE_SLOT && id != OUTPUT_TOOL_SLOT) {
            return;
        }

        var x = id == SOURCE_SLOT ? ANVIL_LEFT_X : ANVIL_RIGHT_X;

        container.getContainer().setPos(this.modelPoint(facing, x, ANVIL_ITEM_Y, ANVIL_Z));
        container.getContainer().scale(ANVIL_ITEM_SCALE);
    }

    /**
     * Turns a position in the block model's coordinate space into a world one, following the
     * rotation the model itself is rendered with: -Z faces {@code facing}, +X its clockwise side.
     */
    private Vec3 modelPoint(Direction facing, double x, double y, double z) {
        return Vec3.atBottomCenterOf(this.worldPosition)
                .add(0, y / 16, 0)
                .relative(facing, (8 - z) / 16)
                .relative(facing.getClockWise(), (x - 8) / 16);
    }

    @Override
    public @Nullable BlockModel getModel() {
        return this.model;
    }

    @Override
    public SimpleMovingItemContainer[] getContainers() {
        return this.containers;
    }

    public void setMulti(boolean multi) {
        this.multi = multi;
        this.setChanged();
    }

    public double getStress() {
        return this.active ? PressBlockEntity.getActiveStress(REQUIRED_SPEED) : 0;
    }

    private boolean canInsertBookResult(ItemStack bookResult) {
        if (bookResult.isEmpty()) {
            return true;
        }

        var output = this.getOutputContainer();
        var copy = new SimpleContainer(output.getContainerSize());
        for (int i = 0; i < output.getContainerSize(); i++) {
            copy.setItem(i, output.getItem(i).copy());
        }

        var testStack = bookResult.copy();
        FactoryUtil.tryInsertingInv(copy, testStack, null);
        return testStack.isEmpty();
    }

    private static boolean isTransferable(Holder<Enchantment> enchantment) {
        return !enchantment.is(EnchantmentTags.CURSE);
    }

    private static long xpFluidCostForLevel(int level) {
        var scaled = Math.log10(level + 1);
        return (long) Math.ceil(BASE_COST * scaled);
    }

    public void createGui(ServerPlayer player) {
        new Gui(player);
    }

    private class Gui extends SimpleGui {
        private static final Component MODE_LABEL = Component.translatable("text.polyfactory.disenchanter.mode");
        private static final int TITLE_ORIGIN_X = 8;
        private static final int CHECKBOX_RIGHT_X = 8 + 4 * 18 + 16 + 3;
        private int lastFluidUpdate = -1;
        private int delayTick = -1;

        private Gui(ServerPlayer player) {
            super(MenuType.GENERIC_9x3, player, false);
            this.updateTitleAndFluid();

            var fluidSlot = FluidContainerUtil.guiElement(fluidContainer, true);
            this.setSlot(1, fluidSlot);
            this.setSlot(10, fluidSlot);
            this.setSlot(19, fluidSlot);

            this.setSlot(11, new Slot(DisenchanterBlockEntity.this, SOURCE_SLOT, 0, 0));
            this.setSlot(20, new Slot(DisenchanterBlockEntity.this, BLANK_BOOK_SLOT, 1, 0));
            this.setSlot(13, GuiTextures.PROGRESS_HORIZONTAL.get(displayProgress()));
            this.setSlot(15, new FurnaceResultSlot(player, DisenchanterBlockEntity.this, OUTPUT_BOOK_SLOT, 2, 0));
            this.setSlot(24, new FurnaceResultSlot(player, DisenchanterBlockEntity.this, OUTPUT_TOOL_SLOT, 3, 0));

            this.updateModeButton();
            this.open();
        }

        private void updateTitleAndFluid() {
            var text = GuiTextures.DISENCHANTER.apply(
                    Component.empty()
                    .append(Component.literal(GuiTextures.DISENCHANTER_FLUID_OFFSET + "").setStyle(UiResourceCreator.STYLE))
                            .append(FluidTextures.MIXER.render(DisenchanterBlockEntity.this.fluidContainer::provideRender))
                    .append(Component.literal(GuiTextures.DISENCHANTER_FLUID_OFFSET_N + "").setStyle(UiResourceCreator.STYLE))
                            .append(modeLabel())
                            .append(DisenchanterBlockEntity.this.getBlockState().getBlock().getName())
            );

            if (!text.equals(this.getTitle())) {
                this.setTitle(text);
            }

            this.lastFluidUpdate = DisenchanterBlockEntity.this.fluidContainer.updateId();
        }

        // Header label placed just right of the mode checkbox (slot 4). Net-zero horizontal advance so the block name stays at the title origin.
        private Component modeLabel() {
            var label = MODE_LABEL.copy();
            var width = DefaultFonts.REGISTRY.getWidth(label, 8);
            var start = Math.max(0, CHECKBOX_RIGHT_X - TITLE_ORIGIN_X);
            return Component.empty()
                    .append(TextUncenterer.filler(start))
                    .append(label)
                    .append(GuiTextures.negativeSpace(start + width));
        }

        private float progress() {
            return (float) Mth.clamp(DisenchanterBlockEntity.this.process, 0, 1);
        }

        private float displayProgress() {
            return Math.max(0.07f, progress());
        }

        private void updateModeButton() {
            var modeText = Component.translatable(DisenchanterBlockEntity.this.multi
                    ? "text.polyfactory.disenchanter.mode.multi"
                    : "text.polyfactory.disenchanter.mode.single");

            this.setSlot(4, (DisenchanterBlockEntity.this.multi ? GuiTextures.CHECKBOX_CHECKED : GuiTextures.CHECKBOX_UNCHECKED).get()
                    .setName(MODE_LABEL.copy().append(": ").append(modeText))
                    .addLoreLine(Component.translatable("text.polyfactory.disenchanter.mode.toggle").withStyle(ChatFormatting.GRAY))
                    .setCallback(clickType -> {
                        DisenchanterBlockEntity.this.setMulti(!DisenchanterBlockEntity.this.multi);
                        this.updateModeButton();
                    }));
        }

        @Override
        public void onTick() {
            if (player.position().distanceToSqr(Vec3.atCenterOf(DisenchanterBlockEntity.this.worldPosition)) > (18 * 18)) {
                this.close();
                return;
            }

            if (DisenchanterBlockEntity.this.fluidContainer.updateId() != this.lastFluidUpdate && delayTick < 0) {
                delayTick = 3;
            }
            if (this.delayTick-- == 0) {
                this.updateTitleAndFluid();
            }

            this.setSlot(13, GuiTextures.PROGRESS_HORIZONTAL.get(displayProgress()));
            super.onTick();
        }
    }
}