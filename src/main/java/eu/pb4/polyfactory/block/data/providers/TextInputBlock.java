package eu.pb4.polyfactory.block.data.providers;

import eu.pb4.polyfactory.block.data.DataProvider;
import eu.pb4.polyfactory.block.data.util.ChanneledDataBlockEntity;
import eu.pb4.polyfactory.data.*;
import eu.pb4.polyfactory.block.configurable.BlockConfig;
import eu.pb4.polyfactory.item.util.MultimeterHandler;
import eu.pb4.polyfactory.ui.GuiTextures;
import eu.pb4.polyfactory.util.FactoryUtil;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import it.unimi.dsi.fastutil.objects.ReferenceSortedSets;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class TextInputBlock extends OrientableCabledDataProviderBlock implements MultimeterHandler.Provider {
    public static final EnumProperty<DataType> MODE = EnumProperty.create("mode", DataType.class);

    public TextInputBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.defaultBlockState().setValue(MODE, DataType.STRING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() && getFacing(state).getOpposite() != hit.getDirection()
                && player instanceof ServerPlayer serverPlayer && world.getBlockEntity(pos) instanceof ChanneledDataBlockEntity be) {
            if (be.checkUnlocked(player)) {
                new Gui(serverPlayer, be);
            }
            return InteractionResult.SUCCESS_SERVER;
        }

        return super.useWithoutItem(state, world, pos, player, hit);
    }
    private static class Gui extends AnvilInputGui {
        private final ChanneledDataBlockEntity blockEntity;

        public Gui(ServerPlayer player, ChanneledDataBlockEntity blockEntity) {
            super(player, false);
            this.blockEntity = blockEntity;
            this.setTitle(GuiTextures.TEXT_INPUT.apply(blockEntity.getDisplayName()));
            this.updateDone();
            this.setSlot(2, GuiTextures.BUTTON_CLOSE.get().setName(CommonComponents.GUI_BACK).setCallback(x -> {
                FactoryUtil.playSoundToPlayer(player,SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.UI, 0.5f, 1);
                this.close();
            }));
            this.open();
        }

        @Override
        public void onInput(String input) {
            super.onInput(input);
            this.updateDone();
            if (this.wrappedMenu != null) {
                this.wrappedMenu.setRemoteSlot(2, ItemStack.EMPTY);
            }
        }

        private void updateDone() {
            var data = this.blockEntity.getBlockState().getValue(MODE).parse(this.getInput());
            if (data != null) {
                this.setSlot(1, GuiTextures.BUTTON_DONE.get().setName(CommonComponents.GUI_DONE).setCallback(x -> {
                    FactoryUtil.playSoundToPlayer(player,SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.UI, 0.5f, 1);
                    DataProvider.sendData(blockEntity.getLevel(), blockEntity.getBlockPos(), data);
                    this.close();
                }));
            } else {
                this.setSlot(1, GuiTextures.BUTTON_DONE_BLOCKED.get().setName(Component.empty().append(CommonComponents.GUI_DONE).withStyle(ChatFormatting.GRAY)));
            }
        }

        @Override
        public void setDefaultInputValue(String input)  {
            super.setDefaultInputValue(input);
            if (this.blockEntity != null) {
                updateDone();
            }
            var itemStack = GuiTextures.EMPTY.asStack();
            itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(input));
            itemStack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, ReferenceSortedSets.emptySet()));
            this.setSlot(0, itemStack, Objects.requireNonNull(this.getGuiElement(0)).getGuiCallback());
        }

        @Override
        public void onTick() {
            if (this.blockEntity.isRemoved()
                    || player.position().distanceToSqr(Vec3.atCenterOf(this.blockEntity.getBlockPos())) > (18 * 18)) {
                this.close();
                return;
            }
            super.onTick();
        }
    }

    @Override
    public List<BlockConfig<?>> getBlockConfiguration(ServerPlayer player, BlockPos blockPos, Direction side, BlockState state) {
        return List.of(
                BlockConfig.CHANNEL,
                this.facingAction,
                BlockConfig.of("mode", MODE, (basicDataType, world, pos, sidex, statex) -> basicDataType.text())
        );
    }

    @Override
    public void provideMultimeterDataAtTheEnd(MultimeterHandler.Builder builder, ServerLevel level, BlockPos pos, BlockState state, @org.jspecify.annotations.Nullable BlockEntity blockEntity, ServerPlayer player) {
        builder.addLineDirect("mode", state.getValue(MODE).text());
    }

    public enum DataType implements StringRepresentable {
        STRING("string", StringData::ofLimited),
        INTEGER("integer", x -> new LongData(Long.parseLong(x))),
        DECIMAL("decimal", x -> new DoubleData(Double.parseDouble(x))),
        BOOLEAN("boolean", x -> BoolData.of(Boolean.parseBoolean(x))),
        FLUID_AMOUNT("fluid_amount", x -> new LongData(FactoryUtil.parseFluidText(x))),
        ;

        private final String name;
        private final Function<String, DataContainer> parser;
        private final Component text;


        DataType(String name, Function<String, @Nullable DataContainer> parser) {
            this.name = name;
            this.parser = parser;
            this.text = Component.translatable("item.polyfactory.wrench.action.mode.arithmetic." + name);

        }

        @Nullable
        public DataContainer parse(String input) {
            try {
                return this.parser.apply(input);
            } catch (Throwable e) {
                return null;
            }
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public Component text() {
            return this.text;
        }
    }
}
