package org.sosly.ecotale.network;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.ecotale.client.RoostDebugRenderer;

public class RoostDebugPacket {
    private final Map<BlockPos, Boolean> roostStatuses;

    public RoostDebugPacket(Map<BlockPos, Boolean> roostStatuses) {
        this.roostStatuses = roostStatuses;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(roostStatuses.size());
        for (Map.Entry<BlockPos, Boolean> entry : roostStatuses.entrySet()) {
            buf.writeBlockPos(entry.getKey());
            buf.writeBoolean(entry.getValue());
        }
    }

    public static RoostDebugPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<BlockPos, Boolean> statuses = new HashMap<>();
        for (int i = 0; i < count; i++) {
            BlockPos pos = buf.readBlockPos();
            boolean hasGraph = buf.readBoolean();
            statuses.put(pos, hasGraph);
        }
        return new RoostDebugPacket(statuses);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        RoostDebugRenderer.toggle(roostStatuses);
    }
}
