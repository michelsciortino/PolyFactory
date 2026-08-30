package eu.pb4.polyfactory.mixin.machines;


import eu.pb4.polyfactory.block.other.FilteredBlockEntity;
import eu.pb4.polyfactory.models.HopperModel;
import eu.pb4.polyfactory.models.MinecartHopperModel;
import eu.pb4.polyfactory.util.filter.FilterData;
import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartHopper.class)
public abstract class MinecartHopperMixin extends AbstractMinecartContainer implements FilteredBlockEntity {
    @Unique
    private ItemStack filterStack = ItemStack.EMPTY;
    @Unique
    private FilterData filter = FilterData.EMPTY_TRUE;

    @Nullable
    @Unique
    private MinecartHopperModel model;

    protected MinecartHopperMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void writeFilterNbt(ValueOutput view, CallbackInfo ci) {
        if (!this.filterStack.isEmpty()) {
            view.store("polyfactory:filter", ItemStack.CODEC, this.filterStack);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readFilterNbt(ValueInput view, CallbackInfo ci) {
        polyfactory$setFilter(view.read("polyfactory:filter", ItemStack.OPTIONAL_CODEC).orElse(view.read("polydex:filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY)));
    }

    @Override
    public void polyfactory$setFilter(ItemStack stack) {
        this.filterStack = stack;
        this.filter = FilterData.of(stack, true);

        if (this.model == null && !this.filterStack.isEmpty()) {
            this.createModel();
        } else if (this.model != null && this.filterStack.isEmpty()) {
            this.model.destroy();
            this.model = null;
        } else if (this.model != null) {
            this.model.setFilter(this.filter);
            this.model.tick();
        }
    }

    @Override
    public ItemStack polyfactory$getFilter() {
        return this.filterStack;
    }

    @Override
    public boolean polyfactory$matchesFilter(ItemStack itemStack) {
        return filter.test(itemStack);
    }

    @Unique
    private void createModel() {
        if (!(this.level() instanceof ServerLevel serverWorld)) {
            return;
        }
        var model = new MinecartHopperModel((MinecartHopper) (Object) this);
        model.setFilter(this.filter);
        EntityAttachment.ofTicking(model, this);
        this.model = model;
    }
}
