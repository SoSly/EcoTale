package org.sosly.ecotale.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.ecotale.client.FlowFieldDebugRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RoostStatusPacket {
    private final boolean clear;
    private final List<BlockPos> withFlowField;
    private final List<BlockPos> withoutFlowField;

    public RoostStatusPacket(List<BlockPos> withFlowField, List<BlockPos> withoutFlowField) {
        this.clear = false;
        this.withFlowField = withFlowField;
        this.withoutFlowField = withoutFlowField;
    }

    private RoostStatusPacket(boolean clear) {
        this.clear = clear;
        this.withFlowField = List.of();
        this.withoutFlowField = List.of();
    }

    public static RoostStatusPacket clearAll() {
        return new RoostStatusPacket(true);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(clear);
        if (clear) {
            return;
        }

        buf.writeVarInt(withFlowField.size());
        for (BlockPos pos : withFlowField) {
            buf.writeBlockPos(pos);
        }

        buf.writeVarInt(withoutFlowField.size());
        for (BlockPos pos : withoutFlowField) {
            buf.writeBlockPos(pos);
        }
    }

    public static RoostStatusPacket decode(FriendlyByteBuf buf) {
        boolean clear = buf.readBoolean();
        if (clear) {
            return clearAll();
        }

        int withCount = buf.readVarInt();
        List<BlockPos> withFlowField = new ArrayList<>(withCount);
        for (int i = 0; i < withCount; i++) {
            withFlowField.add(buf.readBlockPos());
        }

        int withoutCount = buf.readVarInt();
        List<BlockPos> withoutFlowField = new ArrayList<>(withoutCount);
        for (int i = 0; i < withoutCount; i++) {
            withoutFlowField.add(buf.readBlockPos());
        }

        return new RoostStatusPacket(withFlowField, withoutFlowField);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        if (clear) {
            FlowFieldDebugRenderer.clearRoostStatus();
            return;
        }

        FlowFieldDebugRenderer.setRoostStatus(withFlowField, withoutFlowField);
    }
}
