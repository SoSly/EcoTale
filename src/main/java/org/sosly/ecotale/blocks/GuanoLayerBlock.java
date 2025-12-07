package org.sosly.ecotale.blocks;

import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.registries.ForgeRegistries;
import org.sosly.ecotale.api.IBlockStateGenerating;
import org.sosly.ecotale.api.ILootTableGenerating;
import org.sosly.ecotale.items.ItemRegistry;

import java.util.function.BiConsumer;

public class GuanoLayerBlock extends FallingBlock implements IBlockStateGenerating, ILootTableGenerating {
    public static final int MAX_HEIGHT = 8;
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;

    protected static final VoxelShape[] SHAPE_BY_LAYER = new VoxelShape[] {
            Shapes.empty(),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)
    };

    public GuanoLayerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_LAYER[state.getValue(LAYERS)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_LAYER[state.getValue(LAYERS) - 1];
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE_BY_LAYER[state.getValue(LAYERS)];
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_LAYER[state.getValue(LAYERS)];
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        if (type == PathComputationType.LAND) {
            return state.getValue(LAYERS) < 5;
        }
        return false;
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (!context.getItemInHand().is(this.asItem())) {
            return false;
        }
        if (state.getValue(LAYERS) >= MAX_HEIGHT) {
            return false;
        }
        if (context.replacingClickedOnBlock()) {
            return context.getClickedFace() == Direction.UP;
        }
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existingState = context.getLevel().getBlockState(context.getClickedPos());
        if (existingState.is(this)) {
            int currentLayers = existingState.getValue(LAYERS);
            return existingState.setValue(LAYERS, Math.min(MAX_HEIGHT, currentLayers + 1));
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState belowState = level.getBlockState(pos.below());
        if (belowState.is(this) && belowState.getValue(LAYERS) == MAX_HEIGHT) {
            return true;
        }
        return Block.isFaceFull(belowState.getCollisionShape(level, pos.below()), Direction.UP);
    }

    @Override
    protected void falling(FallingBlockEntity entity) {
        entity.disableDrop();
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity entity) {
        BlockPos targetPos = pos;
        BlockState targetState = level.getBlockState(targetPos);

        if (!targetState.is(this)) {
            targetPos = pos.below();
            targetState = level.getBlockState(targetPos);
        }

        if (!targetState.is(this)) {
            entity.spawnAtLocation(this);
            return;
        }

        BlockState fallingState = entity.getBlockState();
        int existingLayers = targetState.getValue(LAYERS);
        int fallingLayers = fallingState.getValue(LAYERS);
        int totalLayers = existingLayers + fallingLayers;

        if (totalLayers <= MAX_HEIGHT) {
            level.setBlock(targetPos, targetState.setValue(LAYERS, totalLayers), Block.UPDATE_ALL);
        } else {
            level.setBlock(targetPos, targetState.setValue(LAYERS, MAX_HEIGHT), Block.UPDATE_ALL);
            int overflow = totalLayers - MAX_HEIGHT;
            BlockPos abovePos = targetPos.above();
            if (level.getBlockState(abovePos).isAir()) {
                level.setBlock(abovePos, this.defaultBlockState().setValue(LAYERS, overflow), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void generateBlockState(BlockStateProvider provider) {
        String blockName = ForgeRegistries.BLOCKS.getKey(this).getPath();
        ResourceLocation texture = new ResourceLocation("minecraft", "block/mycelium_top");

        provider.getVariantBuilder(this).forAllStates(state -> {
            int layers = state.getValue(LAYERS);
            int height = layers * 2;

            ModelFile model;
            if (layers == MAX_HEIGHT) {
                model = provider.models().cubeAll(blockName + "_height16", texture);
            } else {
                model = provider.models()
                        .withExistingParent(blockName + "_height" + height, "minecraft:block/snow_height" + height)
                        .texture("texture", texture)
                        .texture("particle", texture);
            }
            return ConfiguredModel.builder().modelFile(model).build();
        });
    }

    @Override
    public void generateLootTable(Block block, BiConsumer<Block, LootTable.Builder> register) {
        LootItem.Builder<?> itemBuilder = LootItem.lootTableItem(ItemRegistry.GUANO.get());

        for (int i = 1; i <= MAX_HEIGHT; i++) {
            itemBuilder.apply(SetItemCountFunction.setCount(ConstantValue.exactly((float) i))
                    .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                            .setProperties(StatePropertiesPredicate.Builder.properties()
                                    .hasProperty(LAYERS, i))));
        }

        register.accept(block, LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(itemBuilder)));
    }
}
