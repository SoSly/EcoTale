package org.sosly.ecotale.entities.ai;

import net.minecraft.world.entity.schedule.Activity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;

public class Activities {
    public static final DeferredRegister<Activity> ACTIVITIES =
            DeferredRegister.create(ForgeRegistries.ACTIVITIES, EcoTale.MOD_ID);

    public static final RegistryObject<Activity> ROOST =
            ACTIVITIES.register("roost", () -> new Activity("roost"));
    public static final RegistryObject<Activity> FORAGE =
            ACTIVITIES.register("forage", () -> new Activity("forage"));

    public static void register(IEventBus eventBus) {
        ACTIVITIES.register(eventBus);
    }
}
