package org.sosly.ecotale.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.ecotale.client.GraphDebugRenderer;
import org.sosly.ecotale.navigation.Cell;
import org.sosly.ecotale.navigation.Graph;

public class GraphDebugPacket {
    private final BlockPos id;
    private final boolean clear;
    private final BlockPos graphStart;
    private final Set<BlockPos> graphExits;
    private final Map<BlockPos, CellData> cells;
    private final Set<BlockPos> highlightedPath;

    public GraphDebugPacket(BlockPos id, BlockPos graphStart, Set<BlockPos> graphExits,
                            Map<BlockPos, CellData> cells, Set<BlockPos> highlightedPath) {
        this.id = id;
        this.clear = false;
        this.graphStart = graphStart;
        this.graphExits = graphExits;
        this.cells = cells;
        this.highlightedPath = highlightedPath;
    }

    private GraphDebugPacket(BlockPos id, boolean clear) {
        this.id = id;
        this.clear = clear;
        this.graphStart = null;
        this.graphExits = null;
        this.cells = null;
        this.highlightedPath = null;
    }

    public static GraphDebugPacket clearAll() {
        return new GraphDebugPacket(BlockPos.ZERO, true);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(id);
        buf.writeBoolean(clear);

        if (clear) {
            return;
        }

        buf.writeBlockPos(graphStart);

        buf.writeVarInt(graphExits.size());
        for (BlockPos exit : graphExits) {
            buf.writeBlockPos(exit);
        }

        buf.writeVarInt(cells.size());
        for (CellData cellData : cells.values()) {
            buf.writeLong(cellData.boundsMin.asLong());

            buf.writeVarInt(cellData.hubs.size());
            for (BlockPos hub : cellData.hubs) {
                buf.writeBlockPos(hub);
            }

            buf.writeVarInt(cellData.paths.size());
            for (Map.Entry<BlockPos, BlockPos> entry : cellData.paths.entrySet()) {
                buf.writeBlockPos(entry.getKey());
                BlockPos nextHop = entry.getValue();
                buf.writeBoolean(nextHop != null);
                if (nextHop != null) {
                    buf.writeBlockPos(nextHop);
                }
            }
        }

        buf.writeVarInt(highlightedPath.size());
        for (BlockPos hub : highlightedPath) {
            buf.writeBlockPos(hub);
        }
    }

    public static GraphDebugPacket decode(FriendlyByteBuf buf) {
        BlockPos id = buf.readBlockPos();
        boolean clear = buf.readBoolean();

        if (clear) {
            return clearAll();
        }

        BlockPos graphStart = buf.readBlockPos();

        int exitCount = buf.readVarInt();
        Set<BlockPos> graphExits = new HashSet<>();
        for (int i = 0; i < exitCount; i++) {
            graphExits.add(buf.readBlockPos());
        }

        int cellCount = buf.readVarInt();
        Map<BlockPos, CellData> cells = new HashMap<>();
        for (int i = 0; i < cellCount; i++) {
            BlockPos boundsMin = BlockPos.of(buf.readLong());
            BlockPos boundsMax = new BlockPos(
                boundsMin.getX() + Cell.RESOLUTION,
                boundsMin.getY() + Cell.RESOLUTION,
                boundsMin.getZ() + Cell.RESOLUTION
            );

            int hubCount = buf.readVarInt();
            Set<BlockPos> hubs = new HashSet<>();
            for (int j = 0; j < hubCount; j++) {
                hubs.add(buf.readBlockPos());
            }

            Map<BlockPos, BlockPos> paths = new HashMap<>();
            int pathCount = buf.readVarInt();
            for (int j = 0; j < pathCount; j++) {
                BlockPos dest = buf.readBlockPos();
                boolean hasNextHop = buf.readBoolean();
                BlockPos nextHop = hasNextHop ? buf.readBlockPos() : null;
                paths.put(dest, nextHop);
            }

            cells.put(boundsMin, new CellData(boundsMin, boundsMax, hubs, paths));
        }

        int highlightCount = buf.readVarInt();
        Set<BlockPos> highlightedPath = new HashSet<>();
        for (int i = 0; i < highlightCount; i++) {
            highlightedPath.add(buf.readBlockPos());
        }

        return new GraphDebugPacket(id, graphStart, graphExits, cells, highlightedPath);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        if (clear) {
            GraphDebugRenderer.clear();
            return;
        }

        GraphDebugRenderer.toggle(id, graphStart, graphExits, cells, highlightedPath);
    }

    public static class CellData {
        public final BlockPos boundsMin;
        public final BlockPos boundsMax;
        public final Set<BlockPos> hubs;
        public final Map<BlockPos, BlockPos> paths;

        public CellData(BlockPos boundsMin, BlockPos boundsMax, Set<BlockPos> hubs, Map<BlockPos, BlockPos> paths) {
            this.boundsMin = boundsMin;
            this.boundsMax = boundsMax;
            this.hubs = hubs;
            this.paths = paths;
        }

        public static CellData fromCell(Cell cell, Graph graph) {
            BlockPos boundsMin = new BlockPos(
                (int) cell.getBounds().minX,
                (int) cell.getBounds().minY,
                (int) cell.getBounds().minZ
            );
            BlockPos boundsMax = new BlockPos(
                (int) cell.getBounds().maxX,
                (int) cell.getBounds().maxY,
                (int) cell.getBounds().maxZ
            );
            Set<BlockPos> hubs = graph.getHubsForCell(cell);
            return new CellData(boundsMin, boundsMax, hubs, new HashMap<>(cell.getPaths()));
        }
    }
}
