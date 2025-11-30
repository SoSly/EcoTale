package org.sosly.ecotale.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.worldgen.FeatureRegistry;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModWorldgenProvider extends DatapackBuiltinEntriesProvider {
    private static final int ATTEMPTS_PER_CHUNK = 10;

    public static final ResourceKey<ConfiguredFeature<?, ?>> ROOST_CONFIGURED =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation(EcoTale.MOD_ID, "roost"));

    public static final ResourceKey<PlacedFeature> ROOST_PLACED =
            ResourceKey.create(Registries.PLACED_FEATURE, new ResourceLocation(EcoTale.MOD_ID, "roost"));

    private static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.CONFIGURED_FEATURE, ModWorldgenProvider::bootstrapConfigured)
            .add(Registries.PLACED_FEATURE, ModWorldgenProvider::bootstrapPlaced);

    public ModWorldgenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(EcoTale.MOD_ID));
    }

    private static void bootstrapConfigured(BootstapContext<ConfiguredFeature<?, ?>> context) {
        context.register(ROOST_CONFIGURED,
                new ConfiguredFeature<>(FeatureRegistry.ROOST.get(), NoneFeatureConfiguration.INSTANCE));
    }

    private static void bootstrapPlaced(BootstapContext<PlacedFeature> context) {
        var configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        List<PlacementModifier> placements = List.of(
                CountPlacement.of(ATTEMPTS_PER_CHUNK),
                InSquarePlacement.spread(),
                BiomeFilter.biome()
        );

        context.register(ROOST_PLACED,
                new PlacedFeature(configuredFeatures.getOrThrow(ROOST_CONFIGURED), placements));
    }
}
