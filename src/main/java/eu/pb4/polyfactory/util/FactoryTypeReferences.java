package eu.pb4.polyfactory.util;

import com.mojang.datafixers.DSL;
import eu.pb4.polyfactory.data.DataContainer;
import net.minecraft.util.datafix.fixes.References;

public interface FactoryTypeReferences {
    DSL.TypeReference DATA_CONTAINER = References.reference("polyfactory:data_container");
    DSL.TypeReference FLUID_INSTANCE = References.reference("polyfactory:fluid_instance");
}
