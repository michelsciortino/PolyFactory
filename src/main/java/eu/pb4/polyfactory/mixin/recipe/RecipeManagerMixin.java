package eu.pb4.polyfactory.mixin.recipe;

import com.llamalad7.mixinextras.sugar.Local;
import eu.pb4.polyfactory.DynamicContent;
import eu.pb4.polyfactory.fluid.FactoryFluids;
import eu.pb4.polyfactory.fluid.FluidStack;
import eu.pb4.polyfactory.mixin.PotionBrewingAccessor;
import eu.pb4.polyfactory.recipe.drain.SimpleDrainRecipe;
import eu.pb4.polyfactory.recipe.mixing.BrewingMixingRecipe;
import eu.pb4.polyfactory.recipe.spout.SimpleSpoutRecipe;
import eu.pb4.polyfactory.util.PolyFactoryConfig;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.SortedMap;
import java.util.function.BiConsumer;

import static eu.pb4.polyfactory.util.FactoryUtil.id;
import static eu.pb4.polyfactory.util.FactoryUtil.recipeKey;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;<init>(I)V"))
    private void injectDynamicBrewingRecipes(ResourceManager manager, ProfilerFiller profiler, CallbackInfoReturnable<RecipeMap> cir,
                                      @Local(name = "recipes") SortedMap<Identifier, Recipe<?>> recipes) {
        if (!PolyFactoryConfig.get().dynamicBrewingRecipes) {
            return;
        }

        for (var recipe : ((PotionBrewingAccessor) PotionBrewing.bootstrap(FeatureFlagSet.of(FeatureFlags.VANILLA))).getPotionMixes()) {
            var from = FactoryFluids.getPotion(recipe.from());
            var to = FactoryFluids.getPotion(recipe.to());
            var b = new StringBuilder("mixing/brewing/");
            b.append(getShortString(recipe.from()));
            b.append("_with_");
            for (var stack : recipe.ingredient().items().toList()) {
                //noinspection deprecation
                b.append(getShortString(stack));
                b.append("_");
            }
            b.append("to_");
            b.append(getShortString(recipe.to()));

            var key = id(b.toString());

            if (recipes.containsKey(key)) {
                continue;
            }

            recipes.put(key,
                    new BrewingMixingRecipe(getShortString(recipe.to()).replace("long_", "").replace("strong_", ""), recipe.ingredient(), from, to, FluidConstants.BOTTLE, FluidConstants.BOTTLE * 6,
                            20, 15, 30, 0.7f, 2f)
            );
        }

        BiConsumer<ResourceKey<Recipe<?>>, Recipe<?>> exported = (key, recipe) -> {
            if (!recipes.containsKey(key.identifier())) {
                recipes.put(key.identifier(), recipe);
            }
        };

        for (var recipe : DynamicContent.GENERATE_FLUID_BUCKET_RECIPES) {
            fluidBase(exported, recipe.getSecond(), Items.BUCKET, recipe.getFirst().ofBucket(), recipe.getFirst().type().insertSoundEvent(), recipe.getFirst().type().extractSoundEvent());
        }
    }

    private void fluidBase(BiConsumer<ResourceKey<Recipe<?>>, Recipe<?>> exporter, Item withFluid, Item emptyContainer, FluidStack<?> fluid, SoundEvent fillSound, SoundEvent emptySound) {
        var base = BuiltInRegistries.ITEM.getKey(withFluid).getPath();

        exporter.accept(recipeKey("drain/from_" + base), SimpleDrainRecipe.fromItem(withFluid, fluid, emptyContainer, emptySound));
        exporter.accept(recipeKey("drain/to_" + base), SimpleDrainRecipe.toItem(emptyContainer, fluid, withFluid, fillSound));
        exporter.accept(recipeKey("spout/to_" + base), SimpleSpoutRecipe.toItem(emptyContainer, fluid, withFluid, fillSound));
    }


    @Unique
    private String getShortString(Holder<?> entry) {
        //noinspection OptionalGetWithoutIsPresent
        var key = entry.unwrapKey().get().identifier();

        return key.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? key.getPath().replace("/", "_") : key.toDebugFileName();
    }
}
