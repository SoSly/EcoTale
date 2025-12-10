package org.sosly.ecotale.events;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.commands.FlowFieldCommands;
import org.sosly.ecotale.commands.RoostCommands;
import org.sosly.ecotale.navigation.FlowFieldManager;

@Mod.EventBusSubscriber(modid = EcoTale.MOD_ID)
public class ServerEvents {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FlowFieldCommands.register(event.getDispatcher());
        RoostCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        FlowFieldManager.getInstance().start(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FlowFieldManager.getInstance().stop();
    }
}
