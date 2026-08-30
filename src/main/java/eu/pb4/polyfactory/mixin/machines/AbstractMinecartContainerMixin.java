package eu.pb4.polyfactory.mixin.machines;

import eu.pb4.polyfactory.block.other.FilteredBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecartContainer.class)
public abstract class AbstractMinecartContainerMixin extends AbstractMinecart {
    protected AbstractMinecartContainerMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "destroy", at = @At("TAIL"))
    private void dropFilter(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (this instanceof FilteredBlockEntity filteredBlockEntity) {
            this.spawnAtLocation(level, filteredBlockEntity.polyfactory$getFilter());
            filteredBlockEntity.polyfactory$setFilter(ItemStack.EMPTY);
        }
    }
}
