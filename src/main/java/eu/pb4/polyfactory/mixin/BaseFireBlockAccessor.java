package eu.pb4.polyfactory.mixin;

import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.gen.Invoker;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.world.level.block.BaseFireBlock.class)
public interface BaseFireBlockAccessor {
    @Invoker
    boolean callCanBurn(final BlockState state);
}
