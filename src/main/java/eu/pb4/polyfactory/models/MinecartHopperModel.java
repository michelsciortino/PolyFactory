package eu.pb4.polyfactory.models;

import eu.pb4.factorytools.api.virtualentity.BlockModel;
import eu.pb4.factorytools.api.virtualentity.ItemDisplayElementUtil;
import eu.pb4.polyfactory.util.filter.FilterData;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class MinecartHopperModel extends ElementHolder {
    private final FilterIcon icon = new FilterIcon(this);
    private final ItemDisplayElement model;
    private final MinecartHopper hopper;

    public MinecartHopperModel(MinecartHopper minecartHopper) {
        this.hopper = minecartHopper;
        this.model = ItemDisplayElementUtil.createSimple(GenericParts.FILTER_MESH);
        this.model.setOffset(new Vec3(0, 0.05, 0));
        this.icon.setOffset(new Vec3(0, 0.05, 0));
        this.model.setTeleportDuration(this.hopper.getType().updateInterval());
        this.icon.setTeleportDuration(this.hopper.getType().updateInterval());
        var scale =  1.5f / 2 - 0.02f;
        this.model.setTransformation(BlockModel.mat().translate(0, (minecartHopper.getDisplayOffset() * scale + 17)  / 16f, 0).scale(scale * 2));
        this.icon.setTransformation(BlockModel.mat().translate(0, (minecartHopper.getDisplayOffset() + 17) * scale / 16f, 0.37f * scale).rotateX(Mth.HALF_PI * 0.3f)
                .scale(0.25f, 0.25f, 0.005f).scale(scale));
        this.addElement(this.model);
    }

    @Override
    protected void onTick() {
        this.icon.setYaw(this.hopper.getYRot());
        this.icon.setPitch(this.hopper.getXRot());
        this.model.setYaw(this.hopper.getYRot());
        this.model.setPitch(this.hopper.getXRot());

    }

    public void setFilter(FilterData data) {
        this.icon.setFilter(data);
    }
}
