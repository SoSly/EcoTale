package org.sosly.ecotale.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;

public class BlockRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, EcoTale.MOD_ID);

    public static final RegistryObject<Block> GUANO = BLOCKS.register("guano",
            () -> new GuanoLayerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.1F)
                    .sound(SoundType.MUD)
                    .noOcclusion()));

    public static final RegistryObject<Block> GUANO_BLOCK = BLOCKS.register("guano_block",
            () -> new GuanoBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.2F)
                    .sound(SoundType.MUD)));

    public static final RegistryObject<Block> STONE_ROOST = BLOCKS.register("stone_roost",
            () -> new RoostBlock(BlockBehaviour.Properties.copy(Blocks.STONE),
                    () -> Blocks.STONE, "minecraft:block/stone"));

    public static final RegistryObject<Block> DEEPSLATE_ROOST = BLOCKS.register("deepslate_roost",
            () -> new RoostBlock(BlockBehaviour.Properties.copy(Blocks.DEEPSLATE),
                    () -> Blocks.DEEPSLATE, "minecraft:block/deepslate"));

    public static final RegistryObject<Block> BLACKSTONE_ROOST = BLOCKS.register("blackstone_roost",
            () -> new RoostBlock(BlockBehaviour.Properties.copy(Blocks.BLACKSTONE),
                    () -> Blocks.BLACKSTONE, "minecraft:block/blackstone"));

    public static final RegistryObject<Block> BASALT_ROOST = BLOCKS.register("basalt_roost",
            () -> new RoostBlock(BlockBehaviour.Properties.copy(Blocks.BASALT),
                    () -> Blocks.BASALT, "minecraft:block/basalt_side"));

    public static final RegistryObject<Block> TUFF_ROOST = BLOCKS.register("tuff_roost",
            () -> new RoostBlock(BlockBehaviour.Properties.copy(Blocks.TUFF),
                    () -> Blocks.TUFF, "minecraft:block/tuff"));

    public static final RegistryObject<Block> CALCITE_ROOST = BLOCKS.register("calcite_roost",
            () -> new RoostBlock(BlockBehaviour.Properties.copy(Blocks.CALCITE),
                    () -> Blocks.CALCITE, "minecraft:block/calcite"));

    public static final RegistryObject<Block> DRIPSTONE_ROOST = BLOCKS.register("dripstone_roost",
            () -> new RoostBlock(BlockBehaviour.Properties.copy(Blocks.DRIPSTONE_BLOCK),
                    () -> Blocks.DRIPSTONE_BLOCK, "minecraft:block/dripstone_block"));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
