package eu.pb4.polyfactory;

import com.mojang.datafixers.util.Pair;
import eu.pb4.polyfactory.fluid.FluidInstance;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public class DynamicContent {
    public static final List<Pair<FluidInstance<?>, Item>> GENERATE_FLUID_BUCKET_RECIPES = new ArrayList<>();
}
