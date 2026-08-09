package eu.pb4.polyfactory.block.fluids.transport;

import eu.pb4.polyfactory.block.FactoryBlockEntities;
import eu.pb4.polyfactory.block.data.util.ChanneledDataCache;
import eu.pb4.polyfactory.block.network.NetworkComponent;
import eu.pb4.polyfactory.data.DataContainer;
import eu.pb4.polyfactory.fluid.FluidInstance;
import eu.pb4.polyfactory.item.util.MultimeterHandler;
import eu.pb4.polyfactory.util.FactoryUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class SmartValvePipeBlockEntity extends PipeLikeBlockEntity implements ChanneledDataCache, MultimeterHandler.Provider {
    private int channel = 0;
    private DataContainer lastData = DataContainer.empty();
    private int dataId = 0;
    private long targetAmount = FluidConstants.BLOCK;
    private long passedAmount = 0;

    public SmartValvePipeBlockEntity(BlockPos pos, BlockState state) {
        super(FactoryBlockEntities.SMART_VALVE_PIPE, pos, state);
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T t) {
        if (!(t instanceof SmartValvePipeBlockEntity pipe)) {
            return;
        }
        pipe.preTick();

        var redstoneReset = state.getValue(SmartValvePipeBlock.POWERED) != state.getValue(SmartValvePipeBlock.INVERTED);

        if (redstoneReset) {
            if (pipe.passedAmount >= pipe.targetAmount) {
                pipe.clearPassedAmount();
            }
            if (state.getValue(SmartValvePipeBlock.LOCKED)) {
                level.setBlockAndUpdate(pos, state.setValue(SmartValvePipeBlock.LOCKED, false));
            }
        }

        if (pipe.passedAmount < pipe.targetAmount) {
            if (state.getValue(SmartValvePipeBlock.LOCKED)) {
                level.setBlockAndUpdate(pos, state.setValue(SmartValvePipeBlock.LOCKED, false));
            }

            if (pipe.container.isNotEmpty()) {
                NetworkComponent.Pipe.getLogic((ServerLevel) level, pos).runPushFlows(pos, pipe.container::isNotEmpty, pipe::handlePushFluid);
            }

            if (pipe.container.isNotFull() && pipe.passedAmount < pipe.targetAmount) {
                NetworkComponent.Pipe.getLogic((ServerLevel) level, pos).runPullFlows(pos, pipe.container::isNotFull, pipe::pullFluid);
            } else if (pipe.passedAmount >= pipe.targetAmount) {
                level.setBlockAndUpdate(pos, state.setValue(SmartValvePipeBlock.LOCKED, true));
            }
        } else if (!state.getValue(SmartValvePipeBlock.LOCKED)) {
            level.setBlockAndUpdate(pos, state.setValue(SmartValvePipeBlock.LOCKED, true));
        }

        pipe.postTick();
    }

    private void handlePushFluid(Direction direction, double strength) {
        this.pushFluid(direction, strength, this.targetAmount - this.passedAmount);
        this.passedAmount += this.fluidPush.getPushedTotal();
        this.fluidPush.clearTotal();
    }

    @Override
    public long insertFluid(FluidInstance<?> type, long amount, Direction direction) {
        var lowered = switch (SmartValvePipeBlock.getState(this.getBlockState())) {
            case LOCKED -> 0;
            case UNLOCKED -> Math.max(Math.min(this.targetAmount - this.passedAmount - this.container.stored(), amount), 0);
            case PASSTHROUGH -> amount;
        };

        return super.insertFluid(type, lowered, direction) + amount - lowered;
    }

    public void clearPassedAmount() {
        this.passedAmount = 0;
    }

    public long passedAmount() {
        return passedAmount;
    }

    public void setTargetAmountAndClear(long targetAmount) {
        this.setTargetAmount(targetAmount);
        this.clearPassedAmount();
    }

    public void setTargetAmount(long targetAmount) {
        this.targetAmount = targetAmount;
    }

    public long targetAmount() {
        return targetAmount;
    }

    @Override
    protected boolean hasDirection(Direction direction) {
        var state = this.getBlockState();
        return !state.getValue(SmartValvePipeBlock.LOCKED) && ((PipeBaseBlock) state.getBlock()).checkModelDirection(this.getBlockState(), direction);
    }

    @Nullable
    public DataContainer getCachedData() {
        return this.lastData;
    }

    public void setCachedData(DataContainer lastData, int dataId) {
        this.lastData = lastData;
        this.dataId = dataId;
        this.setChanged();
    }

    @Override
    public void loadAdditional(ValueInput view) {
        super.loadAdditional(view);
        this.lastData = view.read("data", DataContainer.CODEC).orElse(DataContainer.empty());
        this.dataId = view.getIntOr("data_id", 0);
        setChannel(view.getIntOr("channel", 0));
        this.passedAmount = view.getLongOr("passed_amount", 0);
        this.targetAmount = view.getLongOr("target_amount", FluidConstants.BLOCK);
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);
        view.putInt("channel", this.channel);
        view.store("data", DataContainer.CODEC, this.lastData);
        view.putInt("data_id", this.dataId);
        view.putLong("passed_amount", this.passedAmount);
        view.putLong("target_amount", this.targetAmount);
    }

    public int channel() {
        return this.channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
        if (this.hasLevel()) {
            NetworkComponent.Data.updateDataAt(this.level, this.worldPosition);
            this.setChanged();
        }
    }

    public int dataId() {
        return this.dataId();
    }

    @Override
    public void provideMultimeterDataAtTheEnd(MultimeterHandler.Builder b, ServerLevel level, BlockPos pos, BlockState state, @org.jspecify.annotations.Nullable BlockEntity blockEntity, ServerPlayer player) {
        b.addLineDirect("machine_state", SmartValvePipeBlock.getState(state).component());
        b.addLine("throughput", FactoryUtil.fluidTextGeneric(this.passedAmount()), FactoryUtil.fluidTextGeneric(this.targetAmount()));
    }
}
