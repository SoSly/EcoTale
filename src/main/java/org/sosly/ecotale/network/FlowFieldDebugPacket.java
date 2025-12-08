package org.sosly.ecotale.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.ecotale.client.FlowFieldDebugRenderer;
import org.sosly.ecotale.navigation.FlowFieldCell;
import org.sosly.ecotale.navigation.FlowFieldSolution;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class FlowFieldDebugPacket {
    private final BlockPos roostPos;
    private final boolean clear;
    private final FlowFieldSolution solution;

    public FlowFieldDebugPacket(BlockPos roostPos, FlowFieldSolution solution) {
        this.roostPos = roostPos;
        this.clear = false;
        this.solution = solution;
    }

    private FlowFieldDebugPacket(BlockPos roostPos, boolean clear, FlowFieldSolution solution) {
        this.roostPos = roostPos;
        this.clear = clear;
        this.solution = solution;
    }

    public static FlowFieldDebugPacket clearAll() {
        return new FlowFieldDebugPacket(BlockPos.ZERO, true, null);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(roostPos);
        buf.writeBoolean(clear);

        if (clear || solution == null || solution.isFailed()) {
            buf.writeBoolean(false);
            return;
        }

        buf.writeBoolean(true);
        buf.writeBlockPos(solution.getExitPoint());

        Map<FlowFieldCell, Vec3> outward = new HashMap<>();
        Map<FlowFieldCell, Vec3> inward = new HashMap<>();
        solution.forEachOutwardCell(outward::put);
        solution.forEachInwardCell(inward::put);

        buf.writeVarInt(outward.size());
        for (Map.Entry<FlowFieldCell, Vec3> entry : outward.entrySet()) {
            writeCell(buf, entry.getKey());
            writeVec3(buf, entry.getValue());
        }

        buf.writeVarInt(inward.size());
        for (Map.Entry<FlowFieldCell, Vec3> entry : inward.entrySet()) {
            writeCell(buf, entry.getKey());
            writeVec3(buf, entry.getValue());
        }

        Map<FlowFieldCell, BlockPos> hubs = new HashMap<>();
        for (FlowFieldCell cell : outward.keySet()) {
            solution.getHubPosition(cell).ifPresent(hub -> hubs.put(cell, hub));
        }
        buf.writeVarInt(hubs.size());
        for (Map.Entry<FlowFieldCell, BlockPos> entry : hubs.entrySet()) {
            writeCell(buf, entry.getKey());
            buf.writeBlockPos(entry.getValue());
        }
    }

    public static FlowFieldDebugPacket decode(FriendlyByteBuf buf) {
        BlockPos roostPos = buf.readBlockPos();
        boolean clear = buf.readBoolean();

        if (clear) {
            return clearAll();
        }

        boolean hasSolution = buf.readBoolean();
        if (!hasSolution) {
            return new FlowFieldDebugPacket(roostPos, false, null);
        }

        BlockPos exitPoint = buf.readBlockPos();

        Map<FlowFieldCell, Vec3> outward = new HashMap<>();
        int outwardCount = buf.readVarInt();
        for (int i = 0; i < outwardCount; i++) {
            FlowFieldCell cell = readCell(buf);
            Vec3 vec = readVec3(buf);
            outward.put(cell, vec);
        }

        Map<FlowFieldCell, Vec3> inward = new HashMap<>();
        int inwardCount = buf.readVarInt();
        for (int i = 0; i < inwardCount; i++) {
            FlowFieldCell cell = readCell(buf);
            Vec3 vec = readVec3(buf);
            inward.put(cell, vec);
        }

        Map<FlowFieldCell, BlockPos> hubs = new HashMap<>();
        int hubCount = buf.readVarInt();
        for (int i = 0; i < hubCount; i++) {
            FlowFieldCell cell = readCell(buf);
            BlockPos hub = buf.readBlockPos();
            hubs.put(cell, hub);
        }

        FlowFieldCell startCell = FlowFieldCell.fromBlockPos(roostPos);
        FlowFieldSolution solution = FlowFieldSolution.create(
            outward, inward, hubs, exitPoint, roostPos, startCell
        );

        return new FlowFieldDebugPacket(roostPos, false, solution);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        if (clear) {
            FlowFieldDebugRenderer.clear();
            return;
        }

        if (solution != null) {
            FlowFieldDebugRenderer.toggle(roostPos, solution);
        }
    }

    private static void writeCell(FriendlyByteBuf buf, FlowFieldCell cell) {
        buf.writeVarInt(cell.x());
        buf.writeVarInt(cell.y());
        buf.writeVarInt(cell.z());
    }

    private static FlowFieldCell readCell(FriendlyByteBuf buf) {
        return new FlowFieldCell(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private static void writeVec3(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeFloat((float) vec.x);
        buf.writeFloat((float) vec.y);
        buf.writeFloat((float) vec.z);
    }

    private static Vec3 readVec3(FriendlyByteBuf buf) {
        return new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
