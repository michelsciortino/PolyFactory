package eu.pb4.polyfactory.block.configurable;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public interface ConfigurableBlock {
    List<BlockConfig<?>> getBlockConfiguration(@Nullable ServerPlayer player, BlockPos blockPos, Direction side, BlockState state);

    default void wrenchTick(ServerPlayer player, BlockHitResult hit, BlockState state) {}
}
