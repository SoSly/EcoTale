package org.sosly.ecotale.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EcoTale.MOD_ID);

    public static final RegistryObject<EntityType<EcoTaleBat>> BAT = ENTITIES.register("bat",
            () -> EntityType.Builder.of(EcoTaleBat::new, MobCategory.AMBIENT)
                    .sized(0.5F, 0.9F)
                    .clientTrackingRange(5)
                    .build("bat"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
