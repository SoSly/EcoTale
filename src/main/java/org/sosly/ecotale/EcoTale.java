package org.sosly.ecotale;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(EcoTale.MOD_ID)
public class EcoTale {
    public static final String MOD_ID = "ecotale";
    private static final Logger LOGGER = LogUtils.getLogger();

    public EcoTale() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("EcoTale initialized");
    }
}
