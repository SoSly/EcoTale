package org.sosly.ecotale.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.api.generators.IWorldgenGenerating;
import org.sosly.ecotale.worldgen.FeatureRegistry;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModWorldgenProvider extends DatapackBuiltinEntriesProvider {
    private static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.CONFIGURED_FEATURE, ModWorldgenProvider::bootstrapConfigured)
            .add(Registries.PLACED_FEATURE, ModWorldgenProvider::bootstrapPlaced);

    public ModWorldgenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(EcoTale.MOD_ID));
    }

    private static void bootstrapConfigured(BootstapContext<ConfiguredFeature<?, ?>> context) {
        for (RegistryObject<Feature<?>> entry : FeatureRegistry.FEATURES.getEntries()) {
            Feature<?> feature = entry.get();
            if (feature instanceof IWorldgenGenerating generator) {
                generator.generateConfiguredFeature(context);
            }
        }
    }

    private static void bootstrapPlaced(BootstapContext<PlacedFeature> context) {
        for (RegistryObject<Feature<?>> entry : FeatureRegistry.FEATURES.getEntries()) {
            Feature<?> feature = entry.get();
            if (feature instanceof IWorldgenGenerating generator) {
                generator.generatePlacedFeature(context);
            }
        }
    }
}
