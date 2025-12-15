package org.sosly.ecotale.events;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.commands.GraphCommands;
import org.sosly.ecotale.commands.RoostCommands;
import org.sosly.ecotale.navigation.Manager;

@Mod.EventBusSubscriber(modid = EcoTale.MOD_ID)
public class ServerEvents {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RoostCommands.register(event.getDispatcher());
        GraphCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Manager.getInstance().start(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Manager.getInstance().stop();
    }
}
