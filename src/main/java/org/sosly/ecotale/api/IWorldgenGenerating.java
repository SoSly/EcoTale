package org.sosly.ecotale.api;

import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Implemented by worldgen features that generate their own configured and placed feature entries.
 *
 * <p>This interface enables self-contained worldgen registration, keeping feature configuration
 * colocated with the feature it applies to rather than scattered across a monolithic provider.</p>
 */
public interface IWorldgenGenerating {
    /**
     * Registers the configured feature for this worldgen feature.
     *
     * @param context the bootstrap context for configured feature registration
     */
    void generateConfiguredFeature(BootstapContext<ConfiguredFeature<?, ?>> context);

    /**
     * Registers the placed feature for this worldgen feature.
     *
     * @param context the bootstrap context for placed feature registration
     */
    void generatePlacedFeature(BootstapContext<PlacedFeature> context);
}
