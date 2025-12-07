package org.sosly.ecotale;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.sosly.ecotale.blocks.BlockEntityRegistry;
import org.sosly.ecotale.blocks.BlockRegistry;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.EntityRegistry;
import org.sosly.ecotale.entities.ai.Activities;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;
import org.sosly.ecotale.entities.ai.Schedules;
import org.sosly.ecotale.entities.ai.SensorTypes;
import org.sosly.ecotale.items.ItemRegistry;
import org.sosly.ecotale.network.NetworkHandler;
import org.sosly.ecotale.worldgen.FeatureRegistry;

@Mod(EcoTale.MOD_ID)
public class EcoTale {
    public static final String MOD_ID = "ecotale";
    private static final Logger LOGGER = LogUtils.getLogger();

    public EcoTale() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        BlockRegistry.register(modEventBus);
        BlockEntityRegistry.register(modEventBus);
        ItemRegistry.register(modEventBus);
        FeatureRegistry.register(modEventBus);
        EntityRegistry.register(modEventBus);
        MemoryModuleTypes.register(modEventBus);
        SensorTypes.register(modEventBus);
        Activities.register(modEventBus);
        Schedules.register(modEventBus);

        NetworkHandler.register();

        modEventBus.addListener(this::onEntityAttributeCreation);

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("EcoTale initialized");
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(EntityRegistry.BAT.get(), EcoTaleBat.createAttributes().build());
    }
}
