package org.sosly.ecotale.blocks;

import net.minecraft.world.level.block.Block;
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
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.2F)
                    .sound(SoundType.MUD)));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
