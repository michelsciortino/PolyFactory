package eu.pb4.polyfactory.recipe.drain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polyfactory.fluid.FluidStack;
import eu.pb4.polyfactory.item.FactoryDataComponents;
import eu.pb4.polyfactory.item.component.FluidComponent;
import eu.pb4.polyfactory.recipe.FactoryRecipeSerializers;
import eu.pb4.polyfactory.recipe.input.DrainInput;
import eu.pb4.polyfactory.recipe.spout.SpoutRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public record ContainerEmptyDrainRecipe(Ingredient ingredient) implements DrainRecipe {
    public static final MapCodec<ContainerEmptyDrainRecipe> CODEC = RecordCodecBuilder.mapCodec(x -> x.group(
                    Ingredient.CODEC.fieldOf("item").forGetter(ContainerEmptyDrainRecipe::ingredient)
            ).apply(x, ContainerEmptyDrainRecipe::new)
    );

    @Override
    public boolean matches(DrainInput input, Level world) {
        return this.ingredient.test(input.stack()) && !fluidOutput(input).isEmpty();
    }

    @Override
    public ItemStack assemble(DrainInput input) {
        var stack = input.stack().copy();
        var fluids = input.stack().getOrDefault(FactoryDataComponents.FLUID, FluidComponent.DEFAULT);

        for (var fluid : fluidOutput(input)) {
            fluids = fluids.extract(fluid.instance(), fluid.amount(), false).component();
        }

        stack.set(FactoryDataComponents.FLUID, fluids);

        return stack;
    }

    @Override
    public RecipeSerializer<ContainerEmptyDrainRecipe> getSerializer() {
        return FactoryRecipeSerializers.DRAIN_CONTAINER_EMPTY;
    }

    @Override
    public List<FluidStack<?>> fluidOutput(DrainInput input) {
        var stacks = new ArrayList<FluidStack<?>>();
        var max = input.fluidContainer().empty();

        var fluids = input.stack().getOrDefault(FactoryDataComponents.FLUID, FluidComponent.DEFAULT);

        for (var fluid : fluids.fluids()) {
            var amount = Math.min(fluids.get(fluid), max);

            stacks.add(fluid.stackOf(amount));
            max -= amount;

            if (max == 0) {
                break;
            }
        }

        return stacks;
    }

    @Override
    public List<FluidStack<?>> fluidInput(DrainInput input) {
        return List.of();
    }

    @Override
    public Holder<SoundEvent> soundEvent(DrainInput input) {
        var fluids = input.stack().getOrDefault(FactoryDataComponents.FLUID, FluidComponent.DEFAULT);

        for (var fluid : fluids.fluids()) {
            return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(fluid.extractSoundEvent());
        }

        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);
    }

    @Override
    public double time(DrainInput input) {
        var time = 0d;

        for (var fluid : fluidOutput(input)) {
            time += SpoutRecipe.getTime(fluid.instance(), fluid.amount());
        }

        return time;
    }
}
