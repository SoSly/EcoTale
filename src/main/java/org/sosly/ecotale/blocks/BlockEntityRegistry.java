package org.sosly.ecotale.blocks;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;

public class BlockEntityRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EcoTale.MOD_ID);

    public static final RegistryObject<BlockEntityType<RoostBlockEntity>> ROOST =
            BLOCK_ENTITIES.register("roost", () -> BlockEntityType.Builder.of(
                    RoostBlockEntity::new,
                    BlockRegistry.STONE_ROOST.get(),
                    BlockRegistry.DEEPSLATE_ROOST.get(),
                    BlockRegistry.BLACKSTONE_ROOST.get(),
                    BlockRegistry.BASALT_ROOST.get(),
                    BlockRegistry.TUFF_ROOST.get(),
                    BlockRegistry.CALCITE_ROOST.get(),
                    BlockRegistry.DRIPSTONE_ROOST.get()
            ).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
