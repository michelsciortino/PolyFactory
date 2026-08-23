package eu.pb4.polyfactory.block.fluids.transport;

import com.kneelawk.graphlib.api.graph.user.BlockNode;
import eu.pb4.factorytools.api.block.RedstoneConnectable;
import eu.pb4.factorytools.api.virtualentity.BlockModel;
import eu.pb4.factorytools.api.virtualentity.ItemDisplayElementUtil;
import eu.pb4.polyfactory.block.base.AxisAndFacingBlock;
import eu.pb4.polyfactory.block.configurable.BlockConfig;
import eu.pb4.polyfactory.block.configurable.BlockValueFormatter;
import eu.pb4.polyfactory.block.configurable.ConfigurableBlock;
import eu.pb4.polyfactory.block.configurable.WrenchModifyBlockValue;
import eu.pb4.polyfactory.block.data.CableConnectable;
import eu.pb4.polyfactory.block.data.DataReceiver;
import eu.pb4.polyfactory.block.data.util.DataCache;
import eu.pb4.polyfactory.block.network.NetworkComponent;
import eu.pb4.polyfactory.block.property.FactoryProperties;
import eu.pb4.polyfactory.data.DataContainer;
import eu.pb4.polyfactory.nodes.DirectionNode;
import eu.pb4.polyfactory.nodes.data.ChannelProviderDirectionNode;
import eu.pb4.polyfactory.nodes.data.ChannelReceiverDirectionNode;
import eu.pb4.polyfactory.nodes.data.DataReceiverNode;
import eu.pb4.polyfactory.util.FactoryUtil;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.BlockBoundAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public class SmartValvePipeBlock extends PipeBaseBlock implements ConfigurableBlock, RedstoneConnectable, CableConnectable, NetworkComponent.Data, DataReceiver {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty FIRST_AXIS = FactoryProperties.FIRST_AXIS;

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    private static final List<BlockConfig<?>> WRENCH_ACTIONS = List.of(
            BlockConfig.CHANNEL_WITH_DISABLED, BlockConfig.FACING,
            BlockConfig.of("axis", FIRST_AXIS, (value, world, pos, side, state) -> Component.literal(AxisAndFacingBlock.getAxis(state).getSerializedName())),
            BlockConfig.INVERTED,
            BlockConfig.ofBlockEntity("allowed_fluid_amount", ExtraCodecs.NON_NEGATIVE_LONG, SmartValvePipeBlockEntity.class,
                    BlockValueFormatter.text(FactoryUtil::fluidTextGeneric), SmartValvePipeBlockEntity::targetAmount,
                    SmartValvePipeBlockEntity::setTargetAmount, WrenchModifyBlockValue.ofAltCustomInputBlockEntity(
                            Component.literal("// Todo"), SmartValvePipeBlockEntity.class, SmartValvePipeBlockEntity::setTargetAmount,
                            FactoryUtil::parseFluidText, FactoryUtil::stringifyFullFluidAmount), Component.translatable("text.polyfactory.range_inclusive_to_infinite", 0))
    );
    private final Identifier model;
    private final Identifier modelPowered;
    private final Identifier modelLocked;

    public SmartValvePipeBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false).setValue(INVERTED, false).setValue(LOCKED, false));
        this.model = settings.blockIdOrThrow().identifier().withPrefix("block/");
        this.modelPowered = settings.blockIdOrThrow().identifier().withPrefix("block/").withSuffix("_powered");
        this.modelLocked = settings.blockIdOrThrow().identifier().withPrefix("block/").withSuffix("_locked");
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getNearestLookingDirection();
        for (var x : ctx.getNearestLookingDirections()) {
            if (x.getAxis() != ctx.getClickedFace().getAxis()) {
                facing = x.getOpposite();
                break;
            }
        }

        return waterLog(ctx, this.defaultBlockState().setValue(FACING, facing).setValue(FIRST_AXIS,
                (AxisAndFacingBlock.getAxis(facing, true) == ctx.getClickedFace().getAxis())));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, FIRST_AXIS, POWERED, INVERTED, LOCKED);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess tickView, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        tickWater(state, world, tickView, pos);
        return super.updateShape(state, world, tickView, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.is(state.getBlock())) {
            this.updatePowered(world, pos, state);
        }
        super.onPlace(state, world, pos, oldState, notify);
    }

    private void updatePowered(Level world, BlockPos pos, BlockState state) {
        boolean powered = world.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            world.setBlock(pos, state.setValue(POWERED, powered), 4);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, @Nullable Orientation wireOrientation, boolean notify) {
        this.updatePowered(world, pos, state);
        super.neighborChanged(state, world, pos, sourceBlock, wireOrientation, notify);
    }

    public EnumSet<Direction> getFlowDirections(BlockState state) {
        if (!state.getValue(LOCKED)) {
            var axis = AxisAndFacingBlock.getAxis(state);
            return EnumSet.of(axis.getPositive(), axis.getNegative());
        }
        return EnumSet.noneOf(Direction.class);
    }

    @Override
    public Collection<BlockNode> createPipeNodes(BlockState state, ServerLevel world, BlockPos pos) {
        return !state.getValue(LOCKED) ? super.createPipeNodes(state, world, pos) : List.of();
    }

    @Override
    public boolean checkModelDirection(BlockState state, Direction direction) {
        return AxisAndFacingBlock.getAxis(state) == direction.getAxis();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmartValvePipeBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return SmartValvePipeBlockEntity::tick;
    }

    @Override
    public @Nullable ElementHolder createElementHolder(ServerLevel world, BlockPos pos, BlockState initialBlockState) {
        return new Model(initialBlockState);
    }

    @Override
    public List<BlockConfig<?>> getBlockConfiguration(ServerPlayer player, BlockPos blockPos, Direction side, BlockState state) {
        return WRENCH_ACTIONS;
    }

    @Override
    public boolean canRedstoneConnect(BlockState state, @Nullable Direction dir) {
        return dir == null || AxisAndFacingBlock.getAxis(state) != dir.getAxis();
    }

    public static State getState(BlockState state) {
        if (state.getValue(LOCKED)) {
            return State.LOCKED;
        }

        var powered = state.getValue(POWERED);
        var inverted = state.getValue(INVERTED);
        return powered != inverted ? State.PASSTHROUGH : State.UNLOCKED;
    }

    @Override
    public boolean canCableConnect(LevelReader world, int cableColor, BlockPos pos, BlockState state, Direction dir) {
        return dir == state.getValue(FACING);
    }

    @Override
    public Collection<BlockNode> createDataNodes(BlockState state, ServerLevel world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof SmartValvePipeBlockEntity be) {
            return List.of(new ChannelReceiverDirectionNode(state.getValue(FACING), be.channel()));
        }

        return List.of();
    }

    @Override
    protected boolean isSameNetworkType(Block block) {
        return super.isSameNetworkType(block) || block instanceof NetworkComponent.Data;
    }

    @Override
    protected void updateNetworkAt(LevelReader world, BlockPos pos) {
        super.updateNetworkAt(world, pos);
        NetworkComponent.Data.updateDataAt(world, pos);
    }

    @Override
    public boolean receiveData(ServerLevel world, BlockPos selfPos, BlockState selfState, int channel, DataContainer data, DataReceiverNode node, BlockPos sourcePos, @Nullable Direction sourceDir, int dataId) {
        if (node instanceof DirectionNode node1 && selfState.getValue(FACING) == node1.direction() && world.getBlockEntity(selfPos) instanceof SmartValvePipeBlockEntity be) {
            be.setTargetAmountAndClear(Math.max(data.asLong(), 0));
            be.setCachedData(data, dataId);
            return true;
        }
        return false;
    }

    private final class Model extends BlockModel {
        private final ItemDisplayElement main;

        private Model(BlockState state) {
            this.main = ItemDisplayElementUtil.createSimple();
            this.main.setScale(new Vector3f(2f));

            this.updateStatePos(state);
            this.addElement(this.main);
        }

        private void updateStatePos(BlockState state) {
            if (state.getValue(LOCKED)) {
                this.main.setItem(ItemDisplayElementUtil.getModel(modelLocked).get());
            } else if (state.getValue(POWERED)) {
                this.main.setItem(ItemDisplayElementUtil.getModel(modelPowered).get());
            } else {
                this.main.setItem(ItemDisplayElementUtil.getModel(model).get());
            }

            var mat = mat();
            mat.rotate(state.getValue(FIRST_AXIS) ? Mth.HALF_PI : 0, state.getValue(FACING).step());
            mat.rotate(state.getValue(FACING).getRotation());
            mat.scale(2f);
            this.main.setTransformation(mat);
        }

        @Override
        public void notifyUpdate(HolderAttachment.UpdateType updateType) {
            if (updateType == BlockBoundAttachment.BLOCK_STATE_UPDATE) {
                updateStatePos(this.blockState());
                this.tick();
            }
        }
    }


    public enum State {
        PASSTHROUGH,
        UNLOCKED,
        LOCKED;

        public Component component() {
            return Component.translatable("block.polyfactory.smart_valve_pipe.state." + this.name().toLowerCase(Locale.ROOT));
        }
    }
}
