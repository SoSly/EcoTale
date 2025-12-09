package org.sosly.ecotale.entities.ai;

import java.util.Optional;

import com.mojang.serialization.Codec;
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

    public static final RegistryObject<MemoryModuleType<Boolean>> IS_OUTSIDE =
            MEMORY_MODULE_TYPES.register("is_outside", () ->
                    new MemoryModuleType<>(Optional.of(Codec.BOOL)));

    public static void register(IEventBus eventBus) {
        MEMORY_MODULE_TYPES.register(eventBus);
    }
}
