package org.sosly.ecotale;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.sosly.ecotale.blocks.BlockEntityRegistry;
import org.sosly.ecotale.blocks.BlockRegistry;
import org.sosly.ecotale.items.ItemRegistry;
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

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("EcoTale initialized");
    }
}
