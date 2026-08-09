package eu.pb4.polyfactory.item.util;

import eu.pb4.factorytools.api.advancement.TriggerCriterion;
import eu.pb4.factorytools.api.block.MultiBlock;
import eu.pb4.polyfactory.ModInit;
import eu.pb4.polyfactory.advancement.FactoryTriggers;
import eu.pb4.polyfactory.block.data.DataUser;
import eu.pb4.polyfactory.block.data.providers.TinyPotatoSpringBlock;
import eu.pb4.polyfactory.block.mechanical.RotationUser;
import eu.pb4.polyfactory.block.mechanical.machines.TallItemMachineBlock;
import eu.pb4.polyfactory.block.network.NetworkComponent;
import eu.pb4.polyfactory.block.other.FilledStateProvider;
import eu.pb4.polyfactory.block.other.MachineInfoProvider;
import eu.pb4.polyfactory.item.FactoryItems;
import eu.pb4.polyfactory.item.configuration.WrenchHandler;
import eu.pb4.polyfactory.util.PotatoWisdom;
import eu.pb4.polyfactory.util.ServerPlayNetExt;
import eu.pb4.sidebars.api.Sidebar;
import eu.pb4.sidebars.api.lines.LineBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

public class MultimeterHandler {
    private final Sidebar sidebar = new Sidebar(Sidebar.Priority.HIGH);

    public MultimeterHandler(ServerGamePacketListenerImpl handler) {
        this.sidebar.addPlayer(handler);
        this.sidebar.setDefaultNumberFormat(BlankFormat.INSTANCE);
    }

    public static MultimeterHandler of(ServerPlayer player) {
        return ((ServerPlayNetExt) player.connection).polyFactory$getMultimeterHandler();
    }

    public void tickDisplay(ServerPlayer player) {
        if (!player.getMainHandItem().is(FactoryItems.MULTIMETER) && !player.getOffhandItem().is(FactoryItems.MULTIMETER)) {
            this.sidebar.hide();
            return;
        }

        var hitResult = WrenchHandler.getTarget(player);

        if (hitResult.getType() == HitResult.Type.MISS) {
            this.sidebar.hide();
        } else if (hitResult instanceof BlockHitResult blockHitResult) {
            this.sidebar.setTitle(Component.translatable("item.polyfactory.multimeter.title")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)));

            var hasElements = new boolean[]{false};

            try {
                this.sidebar.set((lineBuilder) -> {
                    var b = new Builder(lineBuilder);
                    var blockState = player.level().getBlockState(blockHitResult.getBlockPos());
                    var entity = player.level().getBlockEntity(blockHitResult.getBlockPos());

                    if (entity == null) {
                        if (blockState.getBlock() instanceof MultiBlock multiBlock) {
                            entity = player.level().getBlockEntity(multiBlock.getCenter(blockState, blockHitResult.getBlockPos()));
                        } else if (blockState.getBlock() instanceof TallItemMachineBlock
                                && blockState.getValue(TallItemMachineBlock.PART) == TallItemMachineBlock.Part.TOP) {
                            entity = player.level().getBlockEntity(blockHitResult.getBlockPos().below());
                        }
                    }

                    if (blockState.getBlock() instanceof Provider provider) {
                        provider.provideMultimeterDataAtTheBeginning(b, player.level(), blockHitResult.getBlockPos(), blockState, entity, player);
                    }

                    if (entity instanceof Provider provider) {
                        provider.provideMultimeterDataAtTheBeginning(b, player.level(), blockHitResult.getBlockPos(), blockState, entity, player);
                    }

                    var redstone = player.level().getBestNeighborSignal(blockHitResult.getBlockPos());
                    var redstoneDirect = player.level().getSignal(blockHitResult.getBlockPos(), blockHitResult.getDirection());

                    if (redstone != 0 || redstoneDirect != 0) {
                        b.addLine("redstone", redstone, redstoneDirect);
                    }

                    var rot = blockState.getBlock() instanceof RotationUser user
                            ? RotationUser.getNullableRotation(player.level(), user.offsetRotationReadingPosition(blockHitResult.getBlockPos(), blockState), user.getRotationReadingNodePredicate(player.level(), blockHitResult.getBlockPos(), blockHitResult.getLocation(), blockState, entity))
                            : RotationUser.getNullableRotation(player.level(), blockHitResult.getBlockPos());


                    if (rot != null) {
                        b.addLine("rotation_speed", (int) (rot.speedRPM()));
                        b.addLine("rotation_stress", rot.isOverstressed() ? TextColor.RED : TextColor.YELLOW, (int) Math.round(rot.directStressUsage()), (int) Math.round(rot.directStressCapacity()));
                    }

                    var data = blockState.getBlock() instanceof DataUser user
                            ? NetworkComponent.Data.getLogicNullable(player.level(), user.offsetDataReadingPosition(blockHitResult.getBlockPos(), blockState), user.getDataReadingNodePredicate(player.level(), blockHitResult.getBlockPos(), blockHitResult.getLocation(), blockState, entity))
                            : NetworkComponent.Data.getLogicNullable(player.level(), blockHitResult.getBlockPos());

                    if (data != null) {
                        if (blockState.getBlock() instanceof DataUser user) {
                            var name = user.getDataNetworkName(player.level(), blockHitResult.getBlockPos(), blockHitResult.getLocation(), blockState, entity);
                            if (name != null) {
                                b.addLineDirect("data_network_name", name);
                            }
                        }

                        b.addLine("data_providers",
                                data.providers().getOrDefault(0, Set.of()).size(),
                                data.providers().getOrDefault(1, Set.of()).size(),
                                data.providers().getOrDefault(2, Set.of()).size(),
                                data.providers().getOrDefault(3, Set.of()).size());
                        b.addLine("data_receivers",
                                data.receivers().getOrDefault(0, Set.of()).size(),
                                data.receivers().getOrDefault(1, Set.of()).size(),
                                data.receivers().getOrDefault(2, Set.of()).size(),
                                data.receivers().getOrDefault(3, Set.of()).size());
                    }

                    var fluid = NetworkComponent.Pipe.getLogicNullable(player.level(), blockHitResult.getBlockPos());
                    if (fluid != null) {
                        fluid.runPushFlows(blockHitResult.getBlockPos(), () -> true, (direction, strength) -> {
                            b.addLineDirect("pipe_flow_" + direction.getSerializedName(), String.format(Locale.ROOT, "%.2f", strength * 2_000));
                        });
                    }

                    if (entity instanceof MachineInfoProvider provider) {
                        var text = provider.getCurrentState();
                        if (text != null) {
                            b.addLineDirect("machine_state", text);
                        }
                    }

                    if (entity instanceof FilledStateProvider provider) {
                        var text = provider.getFilledStateText();
                        if (text != null) {
                            b.addLineDirect("filled_amount", text);
                        }
                    } else if (blockState.getBlock() instanceof FilledStateProvider.Remote remote
                            && remote.getFilledStateProvider(player.level(), blockHitResult.getBlockPos(), blockState, entity) instanceof FilledStateProvider provider) {
                        var text = provider.getFilledStateText();
                        if (text != null) {
                            b.addLineDirect("filled_amount", text);
                        }
                    }
                    
                    if (blockState.getBlock() instanceof Provider provider) {
                        provider.provideMultimeterDataAtTheEnd(b, player.level(), blockHitResult.getBlockPos(), blockState, entity, player);
                    }
                    
                    if (entity instanceof Provider provider) {
                        provider.provideMultimeterDataAtTheEnd(b, player.level(), blockHitResult.getBlockPos(), blockState, entity, player);
                    }

                    hasElements[0] = !lineBuilder.getLines().isEmpty();
                });
            } catch (Throwable e) {
                ModInit.LOGGER.error("Failed to create multimeter display!", e);
            }

            if (hasElements[0]) {
                TriggerCriterion.trigger(player, FactoryTriggers.MULTIMETER_MEASURE);
                this.sidebar.show();
            } else {
                this.sidebar.hide();
            }
        } else if (hitResult instanceof EntityHitResult entityHitResult) {
            this.sidebar.hide();
        }
    }

    public interface Provider {
         default void provideMultimeterDataAtTheBeginning(Builder builder, ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ServerPlayer player) {}
         default void provideMultimeterDataAtTheEnd(Builder builder, ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ServerPlayer player) {}
    }

    public record Builder(LineBuilder builder) {
        public void addLineDirect(String name, Component value) {
            var base = "item.polyfactory.multimeter." + name;
            builder.add(Component.translatable(base + ".title").append(": "), Component.empty().append(value).withColor(TextColor.YELLOW));
        }

        public void addLineDirect(String name, String value) {
            var base = "item.polyfactory.multimeter." + name;
            builder.add(Component.translatable(base + ".title").append(": "), Component.literal(value).withColor(TextColor.YELLOW));
        }

        public void addLine(String name, Object... values) {
            addLine(name, TextColor.YELLOW, values);
        }

        public void addLine(String name, TextColor textColor, Object... values) {
            var base = "item.polyfactory.multimeter." + name;
            builder.add(Component.translatable(base + ".title").append(": "), Component.translatable(base + ".value", values).withColor(textColor));
        }
    }
}
