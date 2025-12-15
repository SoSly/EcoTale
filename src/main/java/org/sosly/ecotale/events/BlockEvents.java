package org.sosly.ecotale.events;

import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.navigation.Manager;

@Mod.EventBusSubscriber(modid = EcoTale.MOD_ID)
public class BlockEvents {
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            Manager.getInstance().onBlockChange(event.getPos(), level);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            Manager.getInstance().onBlockChange(event.getPos(), level);
        }
    }
}
