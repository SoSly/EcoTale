package org.sosly.ecotale.entities.ai;

import java.util.Optional;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;

public class MemoryModuleTypes {
    public static final DeferredRegister<MemoryModuleType<?>> MEMORY_MODULE_TYPES =
            DeferredRegister.create(ForgeRegistries.MEMORY_MODULE_TYPES, EcoTale.MOD_ID);

    public static final RegistryObject<MemoryModuleType<WalkTarget>> FLY_TARGET =
            MEMORY_MODULE_TYPES.register("fly_target", () ->
                    new MemoryModuleType<>(Optional.empty()));

    public static final RegistryObject<MemoryModuleType<GlobalPos>> ROOST =
            MEMORY_MODULE_TYPES.register("roost", () ->
                    new MemoryModuleType<>(Optional.of(GlobalPos.CODEC)));

    public static void register(IEventBus eventBus) {
        MEMORY_MODULE_TYPES.register(eventBus);
    }
}
