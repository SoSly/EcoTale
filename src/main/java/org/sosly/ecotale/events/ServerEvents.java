package org.sosly.ecotale.events;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.commands.FlowFieldCommands;

@Mod.EventBusSubscriber(modid = EcoTale.MOD_ID)
public class ServerEvents {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FlowFieldCommands.register(event.getDispatcher());
    }
}
