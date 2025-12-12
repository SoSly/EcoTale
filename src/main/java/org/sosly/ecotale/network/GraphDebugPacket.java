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
            buf.writeLong(cellData.boundsMax.asLong());
            buf.writeBlockPos(cellData.hub);

            buf.writeVarInt(cellData.paths.size());
            for (Map.Entry<BlockPos, BlockPos> entry : cellData.paths.entrySet()) {
                buf.writeBlockPos(entry.getKey());
                BlockPos nextHop = entry.getValue();
                buf.writeBlockPos(nextHop != null ? nextHop : BlockPos.ZERO);
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
            BlockPos boundsMax = BlockPos.of(buf.readLong());
            BlockPos hub = buf.readBlockPos();

            Map<BlockPos, BlockPos> paths = new HashMap<>();
            int pathCount = buf.readVarInt();
            for (int j = 0; j < pathCount; j++) {
                BlockPos dest = buf.readBlockPos();
                BlockPos nextHop = buf.readBlockPos();
                paths.put(dest, nextHop.equals(BlockPos.ZERO) ? null : nextHop);
            }

            cells.put(hub, new CellData(boundsMin, boundsMax, hub, paths));
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
        public final BlockPos hub;
        public final Map<BlockPos, BlockPos> paths;

        public CellData(BlockPos boundsMin, BlockPos boundsMax, BlockPos hub, Map<BlockPos, BlockPos> paths) {
            this.boundsMin = boundsMin;
            this.boundsMax = boundsMax;
            this.hub = hub;
            this.paths = paths;
        }

        public static CellData fromCell(Cell cell) {
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
            return new CellData(boundsMin, boundsMax, cell.getHub(), new HashMap<>(cell.getPaths()));
        }
    }
}
