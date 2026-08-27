package eu.pb4.polyfactory.polydex.pages;

import eu.pb4.polydex.api.v1.recipe.PageBuilder;
import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
import eu.pb4.polydex.api.v1.recipe.PolydexIngredient;
import eu.pb4.polydex.api.v1.recipe.PolydexStack;
import eu.pb4.polyfactory.block.mechanical.machines.crafting.GrinderBlockEntity;
import eu.pb4.polyfactory.polydex.PolydexCompatImpl;
import eu.pb4.polyfactory.recipe.trommel.SimpleTrommelRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SimpleTrommelRecipePage extends TrommelRecipePage<SimpleTrommelRecipe> {
    private final PolydexStack<?>[] output;
    private final List<PolydexIngredient<?>> ingredients;

    public SimpleTrommelRecipePage(RecipeHolder<SimpleTrommelRecipe> recipe) {
        super(recipe);
        this.output = PolydexCompatImpl.createOutput(this.recipe.output());
        this.ingredients = List.of(PolydexIngredient.of(recipe.value().input()));
    }

    @Override
    public List<PolydexIngredient<?>> ingredients() {
        return ingredients;
    }

    @Override
    public ItemStack getOutput(@Nullable PolydexEntry polydexEntry, MinecraftServer minecraftServer) {
        return this.recipe.output().getFirst().stack().create();
    }

    @Override
    public boolean isOwner(MinecraftServer server, PolydexEntry entry) {
        for (var i : this.output) {
            if (entry.isPartOf(i)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack entryIcon(@Nullable PolydexEntry entry, ServerPlayer player) {
        return this.recipe.output().getFirst().stack().create();
    }

    @Override
    public void createPage(@Nullable PolydexEntry entry, ServerPlayer player, PageBuilder layer) {
        layer.setIngredient(4, 1, this.recipe.input());
        layer.set(7, 2, PolydexCompatImpl.requiredRotation(this.recipe.minimumSpeed(), this.recipe.optimalSpeed(), GrinderBlockEntity::getActiveStress));

        var i = 0;
        for (; i < this.output.length; i++) {
            layer.setOutput(2 + i, 3, this.output[i]);
        }
        for (; i < 5; i++) {
            layer.setEmpty(2 + i, 3);
        }
    }
}
