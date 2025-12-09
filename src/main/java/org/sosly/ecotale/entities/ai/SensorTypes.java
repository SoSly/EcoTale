package org.sosly.ecotale.entities.ai;

import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.entities.ai.sensor.HomeSensor;
import org.sosly.ecotale.entities.ai.sensor.SkySensor;

public class SensorTypes {
    public static final DeferredRegister<SensorType<?>> SENSOR_TYPES =
            DeferredRegister.create(ForgeRegistries.SENSOR_TYPES, EcoTale.MOD_ID);

    public static final RegistryObject<SensorType<HomeSensor>> HOME =
            SENSOR_TYPES.register("home", () -> new SensorType<>(HomeSensor::new));

    public static final RegistryObject<SensorType<SkySensor>> SKY =
            SENSOR_TYPES.register("sky", () -> new SensorType<>(SkySensor::new));

    public static void register(IEventBus eventBus) {
        SENSOR_TYPES.register(eventBus);
    }
}
