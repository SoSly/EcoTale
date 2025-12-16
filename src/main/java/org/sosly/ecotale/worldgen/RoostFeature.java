package org.sosly.ecotale.worldgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.slf4j.Logger;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.api.generators.IWorldgenGenerating;
import org.sosly.ecotale.blocks.BlockRegistry;
import org.sosly.ecotale.blocks.GuanoLayerBlock;
import org.sosly.ecotale.blocks.RoostBlockEntity;

public class RoostFeature extends Feature<NoneFeatureConfiguration> implements IWorldgenGenerating {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_DEPTH_BELOW_SURFACE = 24;
    private static final int MAX_GUANO_LAYERS = 5;
    private static final int ATTEMPTS_PER_CHUNK = 1;

    public static final ResourceKey<ConfiguredFeature<?, ?>> CONFIGURED_KEY =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation(EcoTale.MOD_ID, "roost"));

    public static final ResourceKey<PlacedFeature> PLACED_KEY =
            ResourceKey.create(Registries.PLACED_FEATURE, new ResourceLocation(EcoTale.MOD_ID, "roost"));

    private static final Map<Block, Block> STONE_TO_ROOST = Map.of(
            Blocks.STONE, BlockRegistry.STONE_ROOST.get(),
            Blocks.DEEPSLATE, BlockRegistry.DEEPSLATE_ROOST.get(),
            Blocks.BLACKSTONE, BlockRegistry.BLACKSTONE_ROOST.get(),
            Blocks.BASALT, BlockRegistry.BASALT_ROOST.get(),
            Blocks.TUFF, BlockRegistry.TUFF_ROOST.get(),
            Blocks.CALCITE, BlockRegistry.CALCITE_ROOST.get(),
            Blocks.DRIPSTONE_BLOCK, BlockRegistry.DRIPSTONE_ROOST.get()
    );

    public RoostFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        long startTime = System.nanoTime();

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int surfaceHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX(), origin.getZ());
        int minY = surfaceHeight - MAX_DEPTH_BELOW_SURFACE;

        boolean placedAny = false;
        int roostsPlaced = 0;
        int guanoPlaced = 0;
        long roostTime = 0;
        long guanoTime = 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(origin.getX(), 0, origin.getZ());

        for (int y = surfaceHeight; y >= minY; y--) {
            pos.setY(y);

            BlockState state = level.getBlockState(pos);
            Block roost = STONE_TO_ROOST.get(state.getBlock());
            if (roost == null) {
                continue;
            }

            if (!hasAirBelow(level, pos, 3)) {
                continue;
            }

            long roostStart = System.nanoTime();
            level.setBlock(pos, roost.defaultBlockState(), 2);
            placedAny = true;
            roostsPlaced++;

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RoostBlockEntity roostEntity) {
                roostEntity.spawnColony(level, random);
            }
            roostTime += System.nanoTime() - roostStart;

            long guanoStart = System.nanoTime();
            if (placeGuanoBelow(level, pos, random)) {
                guanoPlaced++;
            }
            guanoTime += System.nanoTime() - guanoStart;
        }

        if (roostsPlaced > 0) {
            long totalTime = System.nanoTime() - startTime;
            LOGGER.debug("RoostFeature at {}: {}ms total, {} roosts ({}ms), {} guano ({}ms)",
                    origin,
                    totalTime / 1_000_000.0,
                    roostsPlaced, roostTime / 1_000_000.0,
                    guanoPlaced, guanoTime / 1_000_000.0);
        }

        return placedAny;
    }

    private boolean hasAirBelow(WorldGenLevel level, BlockPos pos, int count) {
        for (int i = 1; i <= count; i++) {
            if (!level.getBlockState(pos.below(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    private boolean placeGuanoBelow(WorldGenLevel level, BlockPos roostPos, RandomSource random) {
        int layers = random.nextInt(MAX_GUANO_LAYERS + 1);
        if (layers == 0) {
            return false;
        }

        BlockPos.MutableBlockPos searchPos = roostPos.below().mutable();
        int minY = level.getMinBuildHeight();

        while (searchPos.getY() > minY) {
            BlockState belowState = level.getBlockState(searchPos.below());
            if (!Block.isFaceFull(belowState.getCollisionShape(level, searchPos.below()), Direction.UP)) {
                searchPos.move(Direction.DOWN);
                continue;
            }

            if (!level.getBlockState(searchPos).isAir()) {
                return false;
            }

            BlockState guanoState = BlockRegistry.GUANO.get().defaultBlockState()
                    .setValue(GuanoLayerBlock.LAYERS, layers);
            level.setBlock(searchPos, guanoState, Block.UPDATE_NEIGHBORS);
            return true;
        }
        return false;
    }

    @Override
    public void generateConfiguredFeature(BootstapContext<ConfiguredFeature<?, ?>> context) {
        context.register(CONFIGURED_KEY,
                new ConfiguredFeature<>(FeatureRegistry.ROOST.get(), NoneFeatureConfiguration.INSTANCE));
    }

    @Override
    public void generatePlacedFeature(BootstapContext<PlacedFeature> context) {
        var configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        List<PlacementModifier> placements = List.of(
                CountPlacement.of(ATTEMPTS_PER_CHUNK),
                InSquarePlacement.spread(),
                BiomeFilter.biome()
        );

        context.register(PLACED_KEY,
                new PlacedFeature(configuredFeatures.getOrThrow(CONFIGURED_KEY), placements));
    }
}
