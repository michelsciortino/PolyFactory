package eu.pb4.polyfactory.block.data.output;

import com.kneelawk.graphlib.api.graph.user.BlockNode;
import eu.pb4.polyfactory.block.FactoryBlockEntities;
import eu.pb4.polyfactory.block.configurable.BlockConfig;
import eu.pb4.polyfactory.block.configurable.ConfigurableBlock;
import eu.pb4.polyfactory.block.data.DataReceiver;
import eu.pb4.polyfactory.block.data.util.ChanneledDataCache;
import eu.pb4.polyfactory.block.data.util.DirectionalCabledDataBlock;
import eu.pb4.polyfactory.data.DataContainer;
import eu.pb4.polyfactory.data.DataContainerOps;
import eu.pb4.polyfactory.data.ListData;
import eu.pb4.polyfactory.data.MapData;
import eu.pb4.polyfactory.item.FactoryItems;
import eu.pb4.polyfactory.nodes.data.ChannelReceiverSelectiveSideNode;
import eu.pb4.polyfactory.nodes.data.DataReceiverNode;
import eu.pb4.polyfactory.util.StringOps;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConfiguratorBlock extends DirectionalCabledDataBlock implements DataReceiver {
    public ConfiguratorBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public List<BlockConfig<?>> getBlockConfiguration(ServerPlayer player, BlockPos blockPos, Direction side, BlockState state) {
        return List.of(
                BlockConfig.CHANNEL,
                this.facingAction
        );
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty() && !player.getItemInHand(InteractionHand.MAIN_HAND).is(FactoryItems.MULTIMETER)) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }

        var targetPos = pos.relative(state.getValue(FACING));
        var targetState = level.getBlockState(targetPos);
        var targetSide = state.getValue(FACING).getOpposite();

        var configs = targetState.getBlock() instanceof ConfigurableBlock configurableBlock
                ? configurableBlock.getBlockConfiguration(null, targetPos, targetSide, targetState)
                : List.<BlockConfig<?>>of();

        var body = new ArrayList<DialogBody>();

        if (configs.isEmpty()) {
            body.add(new PlainMessage(Component.translatable("text.polyfactory.is_not_configurable", targetState.getBlock().getName()).setStyle(Style.EMPTY.withItalic(true).withColor(TextColor.RED)), 300));
        } else {
            for (var config : configs) {
                body.add(new PlainMessage(Component.empty()
                        .append(config.name())
                        .append(" -> ").append(config.id()).append(CommonComponents.NEW_LINE)
                        .append(config.configuratorAllowedDisplay().copy().withColor(TextColor.GRAY))
                        , 300));
            }
        }

        player.openDialog(Holder.direct(new NoticeDialog(new CommonDialogData(state.getBlock().getName(), Optional.empty(), true, false,
                DialogAction.CLOSE, body, List.of()),
                new ActionButton(new CommonButtonData(CommonComponents.GUI_BACK, 150), Optional.empty()))));

        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean receiveData(ServerLevel world, BlockPos selfPos, BlockState selfState, int channel, DataContainer data, DataReceiverNode node, BlockPos sourcePos, @Nullable Direction sourceDir, int dataId) {
        if (world.getBlockEntity(selfPos) instanceof ChanneledDataCache be && channel == be.channel()) {
            be.setCachedData(data, dataId);

            var targetPos = selfPos.relative(selfState.getValue(FACING));
            var targetState = world.getBlockState(targetPos);
            var targetSide = selfState.getValue(FACING).getOpposite();

            if (!(targetState.getBlock() instanceof ConfigurableBlock configurableBlock)) {
                return true;
            }

            var configs = configurableBlock.getBlockConfiguration(null, targetPos, targetSide, targetState).stream()
                    .collect(Collectors.toMap(BlockConfig::id, Function.identity()));

            if (data instanceof MapData(var map)) {
                for (var entry : map.entrySet()) {
                    //noinspection unchecked
                    var config = (BlockConfig<Object>) configs.get(entry.getKey());
                    if (config == null) {
                        continue;
                    }

                    config.codec().parse(DataContainerOps.PARSING, entry.getValue()).ifSuccess(val -> config.value().setValue(val, world, targetPos, targetSide, targetState));
                }
            } else if (data instanceof ListData(var list)) {
                for (var entry : list) {
                    var str = entry.asString().split(" ", 2);
                    if (str.length != 2) continue;

                    var config = (BlockConfig<Object>) configs.get(str[0]);
                    if (config == null) {
                        continue;
                    }

                    config.codec().parse(StringOps.INSTANCE, str[1]).ifSuccess(val -> config.value().setValue(val, world, targetPos, targetSide, targetState));
                }
            } else {
                var str = data.asString().split(" ", 2);
                if (str.length != 2) return true;

                var config = (BlockConfig<Object>) configs.get(str[0]);
                if (config == null) {
                    return true;
                }

                config.codec().parse(StringOps.INSTANCE, str[1]).ifSuccess(val -> config.value().setValue(val, world, targetPos, targetSide, targetState));
            }

            return true;
        }
        return false;
    }

    @Override
    public Collection<BlockNode> createDataNodes(BlockState state, ServerLevel world, BlockPos pos) {
        return List.of(new ChannelReceiverSelectiveSideNode(getDirections(state), getChannel(world, pos), true));
    }

    @Override
    public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
        return Blocks.IRON_BLOCK.defaultBlockState();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return type == FactoryBlockEntities.NIXIE_TUBE_CONTROLLER ? NixieTubeControllerBlockEntity::tick : null;
    }
}
