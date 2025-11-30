package org.sosly.ecotale.datagen;

import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.blocks.BlockRegistry;
import org.sosly.ecotale.blocks.GuanoLayerBlock;
import org.sosly.ecotale.items.ItemRegistry;

import java.util.List;
import java.util.Set;

public class ModBlockLootTableProvider extends BlockLootSubProvider {
    protected ModBlockLootTableProvider() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    public static LootTableProvider create(PackOutput output) {
        return new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootTableProvider::new, LootContextParamSets.BLOCK)));
    }

    @Override
    protected void generate() {
        add(BlockRegistry.GUANO.get(), this::createGuanoLayerDrops);
        dropSelf(BlockRegistry.GUANO_BLOCK.get());
    }

    private LootTable.Builder createGuanoLayerDrops(Block block) {
        return LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(ItemRegistry.GUANO.get())
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F))
                                        .when(layerCondition(block, GuanoLayerBlock.LAYERS, 1)))
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(2.0F))
                                        .when(layerCondition(block, GuanoLayerBlock.LAYERS, 2)))
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(3.0F))
                                        .when(layerCondition(block, GuanoLayerBlock.LAYERS, 3)))
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(4.0F))
                                        .when(layerCondition(block, GuanoLayerBlock.LAYERS, 4)))
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(5.0F))
                                        .when(layerCondition(block, GuanoLayerBlock.LAYERS, 5)))
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(6.0F))
                                        .when(layerCondition(block, GuanoLayerBlock.LAYERS, 6)))
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(7.0F))
                                        .when(layerCondition(block, GuanoLayerBlock.LAYERS, 7)))
                                .apply(SetItemCountFunction.setCount(ConstantValue.exactly(8.0F))
                                        .when(layerCondition(block, GuanoLayerBlock.LAYERS, 8)))));
    }

    private LootItemCondition.Builder layerCondition(Block block, IntegerProperty property, int value) {
        return LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(property, value));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return BlockRegistry.BLOCKS.getEntries().stream().map(RegistryObject::get)::iterator;
    }
}
