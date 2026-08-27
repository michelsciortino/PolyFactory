package eu.pb4.polyfactory.recipe.trommel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import eu.pb4.factorytools.api.recipe.OutputStack;
import eu.pb4.polyfactory.block.mechanical.machines.crafting.GrinderBlockEntity;
import eu.pb4.polyfactory.recipe.FactoryRecipeSerializers;
import eu.pb4.polyfactory.util.FactoryUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record SimpleTrommelRecipe(String group, Ingredient input, List<OutputStack> output, double time, double minimumSpeed, double optimalSpeed) implements TrommelRecipe {
    public static final MapCodec<SimpleTrommelRecipe> CODEC = RecordCodecBuilder.mapCodec(x -> x.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(SimpleTrommelRecipe::group),
                    Ingredient.CODEC.fieldOf("input").forGetter(SimpleTrommelRecipe::input),
                    OutputStack.LIST_CODEC.fieldOf("output").forGetter(SimpleTrommelRecipe::output),
                    Codec.DOUBLE.fieldOf("time").forGetter(SimpleTrommelRecipe::time),
                    Codec.DOUBLE.optionalFieldOf("minimum_speed", 0d).forGetter(SimpleTrommelRecipe::minimumSpeed),
                    Codec.DOUBLE.optionalFieldOf("optimal_speed", 0d).forGetter(SimpleTrommelRecipe::optimalSpeed)
            ).apply(x, SimpleTrommelRecipe::new)
    );

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, Ingredient ingredient, double time, double minimumSpeed, double optimalSpeed, OutputStack... outputs) {
        return new RecipeHolder<>(FactoryUtil.recipeKey("trommel/" + string), new SimpleTrommelRecipe( "", ingredient, List.of(outputs), time, minimumSpeed, optimalSpeed));
    }

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, Ingredient ingredient, double time, double optimalSpeed, OutputStack... outputs) {
        return new RecipeHolder<>(FactoryUtil.recipeKey("trommel/" + string), new SimpleTrommelRecipe( "", ingredient, List.of(outputs), time, 0, optimalSpeed));
    }

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, String group, Ingredient ingredient, double time, double optimalSpeed, OutputStack... outputs) {
        return new RecipeHolder<>(FactoryUtil.recipeKey("trommel/" + string), new SimpleTrommelRecipe( group, ingredient, List.of(outputs), time, 0, optimalSpeed));
    }

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, String group, Ingredient ingredient, double time, double minimumSpeed, double optimalSpeed, OutputStack... outputs) {
        return new RecipeHolder<>(FactoryUtil.recipeKey("trommel/" + string), new SimpleTrommelRecipe( group, ingredient, List.of(outputs), time, minimumSpeed, optimalSpeed));
    }

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, Ingredient ingredient, double time, double optimalSpeed, ItemStackTemplate... outputs) {
        return new RecipeHolder<>(FactoryUtil.recipeKey("trommel/" + string), new SimpleTrommelRecipe( "", ingredient, Arrays.stream(outputs).map(x -> new OutputStack(x, 1, 1)).toList(), time, 0, optimalSpeed));
    }

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, String group, Ingredient ingredient, double time, double optimalSpeed, ItemStackTemplate... outputs) {
        return new RecipeHolder<>(FactoryUtil.recipeKey("trommel/" + string), new SimpleTrommelRecipe( group, ingredient, Arrays.stream(outputs).map(x -> new OutputStack(x, 1, 1)).toList(), time, 0, optimalSpeed));
    }

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, Ingredient ingredient, double time, double minimumSpeed, double optimalSpeed, ItemStackTemplate... outputs) {
        return new RecipeHolder<>(FactoryUtil.recipeKey("trommel/" + string), new SimpleTrommelRecipe( "", ingredient, Arrays.stream(outputs).map(x -> new OutputStack(x, 1, 1)).toList(), time, minimumSpeed, optimalSpeed));
    }

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, Ingredient ingredient, double time, double minimumSpeed, double optimalSpeed, ItemLike... outputs) {
        return new RecipeHolder<>(FactoryUtil.recipeKey("trommel/" + string), new SimpleTrommelRecipe( "", ingredient, Arrays.stream(outputs).map(x -> new OutputStack(new ItemStackTemplate(x.asItem()), 1, 1)).toList(), time, minimumSpeed, optimalSpeed));
    }

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, Ingredient ingredient, double time, double optimalSpeed, ItemLike... outputs) {
        return of(string, "", ingredient, time, optimalSpeed, outputs);
    }

    public static RecipeHolder<SimpleTrommelRecipe> of(String string, String group, Ingredient ingredient, double time, double optimalSpeed, ItemLike... outputs) {
        return new RecipeHolder<>(FactoryUtil.recipeKey("trommel/" + string), new SimpleTrommelRecipe( group, ingredient, Arrays.stream(outputs).map(x -> new OutputStack(new ItemStackTemplate(x.asItem()), 1, 1)).toList(), time, 0, optimalSpeed));
    }

    @Override
    public String group() {
        return this.group;
    }

    @Override
    public boolean matches(SingleRecipeInput inventory, Level world) {
        return this.input.test(inventory.item());
    }

    @Deprecated
    @Override
    public ItemStack assemble(SingleRecipeInput inventory) {
        return this.output.isEmpty() ? ItemStack.EMPTY : this.output.getFirst().stack().create();
    }

    @Override
    public RecipeSerializer<SimpleTrommelRecipe> getSerializer() {
        return FactoryRecipeSerializers.TROMMEL_SIMPLE;
    }

    @Override
    public List<ItemStack> output(SingleRecipeInput input, @Nullable RandomSource random) {
        var items = new ArrayList<ItemStack>();

        for (var out : this.output) {
            for (int a = 0; a < out.roll(); a++) {
                if (random == null || random.nextFloat() < out.chance()) {
                    items.add(out.stack().create());
                }
            }
        }

        return items;
    }

    @Override
    public double time(SingleRecipeInput input) {
        return this.time;
    }

    @Override
    public double minimumSpeed(SingleRecipeInput input) {
        return this.minimumSpeed;
    }

    @Override
    public double optimalSpeed(SingleRecipeInput input) {
        return this.optimalSpeed;
    }
}
