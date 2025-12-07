package org.sosly.ecotale.entities.ai;

import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;

public class Schedules {
    public static final DeferredRegister<Schedule> SCHEDULES =
            DeferredRegister.create(ForgeRegistries.SCHEDULES, EcoTale.MOD_ID);

    public static final RegistryObject<Schedule> BAT_DEFAULT = SCHEDULES.register("bat_default", Schedule::new);

    public static void register(IEventBus eventBus) {
        SCHEDULES.register(eventBus);
        eventBus.addListener(Schedules::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            new ScheduleBuilder(BAT_DEFAULT.get())
                    .changeActivityAt(0, Activities.ROOST.get())
                    .changeActivityAt(12000, Activities.FORAGE.get())
                    .changeActivityAt(23500, Activities.ROOST.get())
                    .build();
        });
    }
}
