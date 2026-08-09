package eu.pb4.polyfactory.recipe.spout;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.polyfactory.fluid.FluidStack;
import eu.pb4.polyfactory.item.FactoryDataComponents;
import eu.pb4.polyfactory.item.component.FluidComponent;
import eu.pb4.polyfactory.recipe.FactoryRecipeSerializers;
import eu.pb4.polyfactory.recipe.drain.DrainRecipe;
import eu.pb4.polyfactory.recipe.input.SingleItemWithFluid;
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

public record ContainerFillSpoutRecipe(Ingredient ingredient) implements SpoutRecipe {
    public static final MapCodec<ContainerFillSpoutRecipe> CODEC = RecordCodecBuilder.mapCodec(x -> x.group(
                    Ingredient.CODEC.fieldOf("item").forGetter(ContainerFillSpoutRecipe::ingredient)
            ).apply(x, ContainerFillSpoutRecipe::new)
    );

    @Override
    public boolean matches(SingleItemWithFluid input, Level world) {
        return this.ingredient.test(input.stack()) && !fluidInput(input).isEmpty();
    }

    @Override
    public ItemStack assemble(SingleItemWithFluid input) {
        var stack = input.stack().copy();
        var fluids = input.stack().getOrDefault(FactoryDataComponents.FLUID, FluidComponent.DEFAULT);

        for (var fluid : fluidInput(input)) {
            fluids = fluids.insert(fluid.instance(), fluid.amount(), false).component();
        }

        stack.set(FactoryDataComponents.FLUID, fluids);

        return stack;
    }

    @Override
    public RecipeSerializer<ContainerFillSpoutRecipe> getSerializer() {
        return FactoryRecipeSerializers.SPOUT_CONTAINER_FILL;
    }

    @Override
    public List<FluidStack<?>> fluidInput(SingleItemWithFluid input) {
        var stacks = new ArrayList<FluidStack<?>>();
        var fluids = input.stack().getOrDefault(FactoryDataComponents.FLUID, FluidComponent.DEFAULT);
        var max = fluids.empty();


        for (var fluid : input.fluidContainer().fluids()) {
            var amount = Math.min(input.fluidContainer().get(fluid), max);

            stacks.add(fluid.stackOf(amount));
            max -= amount;

            if (max == 0) {
                break;
            }
        }

        return stacks;
    }

    @Override
    public Holder<SoundEvent> soundEvent(SingleItemWithFluid input) {
        var fluids = input.stack().getOrDefault(FactoryDataComponents.FLUID, FluidComponent.DEFAULT);

        for (var fluid : fluids.fluids()) {
            return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(fluid.insertSoundEvent());
        }

        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);
    }

    @Override
    public double time(SingleItemWithFluid input) {
        var time = 0d;

        for (var fluid : fluidInput(input)) {
            time += SpoutRecipe.getTime(fluid.instance(), fluid.amount());
        }

        return time;
    }
}
