package org.sosly.ecotale.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.sosly.ecotale.EcoTale;

public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(EcoTale.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private NetworkHandler() {
    }

    public static void register() {
        CHANNEL.messageBuilder(FlowFieldDebugPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
            .decoder(FlowFieldDebugPacket::decode)
            .encoder(FlowFieldDebugPacket::encode)
            .consumerMainThread(FlowFieldDebugPacket::handle)
            .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
