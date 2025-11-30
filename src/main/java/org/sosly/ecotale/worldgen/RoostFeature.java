package org.sosly.ecotale.worldgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.sosly.ecotale.blocks.GuanoLayerBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.slf4j.Logger;
import org.sosly.ecotale.blocks.BlockRegistry;

import java.util.Map;

public class RoostFeature extends Feature<NoneFeatureConfiguration> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_DEPTH_BELOW_SURFACE = 30;
    private static final int MAX_GUANO_LAYERS = 5;
    private static final int MAX_GUANO_SEARCH_DEPTH = 20;

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
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int surfaceHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX(), origin.getZ());
        int minY = surfaceHeight - MAX_DEPTH_BELOW_SURFACE;

        boolean placedAny = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(origin.getX(), 0, origin.getZ());

        for (int y = surfaceHeight; y >= minY; y--) {
            pos.setY(y);

            BlockState state = level.getBlockState(pos);
            Block roost = STONE_TO_ROOST.get(state.getBlock());
            if (roost == null) {
                continue;
            }

            if (!level.getBlockState(pos.below()).isAir()) {
                continue;
            }

            level.setBlock(pos, roost.defaultBlockState(), 2);
            LOGGER.debug("Placed {} at {}", roost, pos.immutable());
            placedAny = true;

            placeGuanoBelow(level, pos, random);
        }

        return placedAny;
    }

    private void placeGuanoBelow(WorldGenLevel level, BlockPos roostPos, RandomSource random) {
        int layers = random.nextInt(MAX_GUANO_LAYERS + 1);
        if (layers == 0) {
            return;
        }

        BlockPos.MutableBlockPos searchPos = new BlockPos.MutableBlockPos(
                roostPos.getX(), roostPos.getY() - 1, roostPos.getZ());

        for (int i = 0; i < MAX_GUANO_SEARCH_DEPTH; i++) {
            BlockState belowState = level.getBlockState(searchPos.below());
            if (Block.isFaceFull(belowState.getCollisionShape(level, searchPos.below()), Direction.UP)) {
                if (level.getBlockState(searchPos).isAir()) {
                    BlockState guanoState = BlockRegistry.GUANO.get().defaultBlockState()
                            .setValue(GuanoLayerBlock.LAYERS, layers);
                    level.setBlock(searchPos, guanoState, 2);
                    LOGGER.debug("Placed {} layers of guano at {}", layers, searchPos.immutable());
                }
                return;
            }
            searchPos.move(Direction.DOWN);
        }
    }
}
