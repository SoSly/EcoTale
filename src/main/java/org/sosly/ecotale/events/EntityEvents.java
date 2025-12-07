package org.sosly.ecotale.events;

import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.entities.EcoTaleBat;

@Mod.EventBusSubscriber(modid = EcoTale.MOD_ID)
public class EntityEvents {
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity().getType() == EntityType.BAT
                && !(event.getEntity() instanceof EcoTaleBat)) {
            event.setCanceled(true);
        }
    }
}
