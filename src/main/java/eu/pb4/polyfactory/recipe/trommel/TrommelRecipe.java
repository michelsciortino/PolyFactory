package eu.pb4.polyfactory.recipe.trommel;

import eu.pb4.polyfactory.recipe.FactoryRecipeTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface TrommelRecipe extends Recipe<SingleRecipeInput> {
    default RecipeType<TrommelRecipe> getType() {
        return FactoryRecipeTypes.TROMMEL;
    }

    default RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CAMPFIRE;
    }

    default boolean isSpecial() {
        return true;
    }

    default PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    default ItemStack assemble(SingleRecipeInput input) {
        var x = this.output(input,  null);
        return x != null ? x.getFirst() : ItemStack.EMPTY;
    }

    @Override
    default boolean showNotification() {
        return false;
    }

    List<ItemStack> output(SingleRecipeInput input, @Nullable RandomSource random);

    double time(SingleRecipeInput input);

    double minimumSpeed(SingleRecipeInput input);

    double optimalSpeed(SingleRecipeInput input);
}
