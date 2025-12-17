package org.sosly.ecotale.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.sosly.ecotale.network.GraphDebugPacket;

@OnlyIn(Dist.CLIENT)
public final class GraphDebugRenderer {
    private static final Map<BlockPos, GraphData> ACTIVE_RENDERS = new ConcurrentHashMap<>();

    private static final float GRAPH_START_R = 0.0f;
    private static final float GRAPH_START_G = 1.0f;
    private static final float GRAPH_START_B = 1.0f;

    private static final float GRAPH_EXIT_R = 1.0f;
    private static final float GRAPH_EXIT_G = 0.0f;
    private static final float GRAPH_EXIT_B = 0.0f;

    private static final float CELL_R = 1.0f;
    private static final float CELL_G = 1.0f;
    private static final float CELL_B = 1.0f;

    private static final float HIGHLIGHT_R = 1.0f;
    private static final float HIGHLIGHT_G = 1.0f;
    private static final float HIGHLIGHT_B = 0.0f;

    private static final float HUB_R = 0.8f;
    private static final float HUB_G = 0.0f;
    private static final float HUB_B = 1.0f;

    private static final float LINE_ALPHA = 1.0f;
    private static final float HUB_SIZE = 0.15f;

    private GraphDebugRenderer() {
    }

    public static void toggle(BlockPos id, BlockPos graphStart, Set<BlockPos> graphExits,
                              Map<BlockPos, GraphDebugPacket.CellData> cells,
                              Set<BlockPos> highlightedPath) {
        if (ACTIVE_RENDERS.containsKey(id)) {
            ACTIVE_RENDERS.remove(id);
        } else {
            ACTIVE_RENDERS.put(id, new GraphData(graphStart, graphExits, cells, highlightedPath));
        }
    }

    public static void clear() {
        ACTIVE_RENDERS.clear();
    }

    public static void render(PoseStack poseStack) {
        if (ACTIVE_RENDERS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (GraphData graphData : ACTIVE_RENDERS.values()) {
            renderGraph(buffer, matrix, graphData);
        }

        tesselator.end();

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static boolean cellContains(GraphDebugPacket.CellData cellData, Set<BlockPos> positions) {
        return positions.stream().anyMatch(pos ->
            pos.getX() >= cellData.boundsMin.getX() && pos.getX() < cellData.boundsMax.getX()
                && pos.getY() >= cellData.boundsMin.getY() && pos.getY() < cellData.boundsMax.getY()
                && pos.getZ() >= cellData.boundsMin.getZ() && pos.getZ() < cellData.boundsMax.getZ()
        );
    }

    private static float[] getCellColor(boolean isGraphStart, boolean isGraphExit, boolean isHighlighted) {
        if (isGraphStart) {
            return new float[]{GRAPH_START_R, GRAPH_START_G, GRAPH_START_B};
        }
        if (isGraphExit) {
            return new float[]{GRAPH_EXIT_R, GRAPH_EXIT_G, GRAPH_EXIT_B};
        }
        if (isHighlighted) {
            return new float[]{HIGHLIGHT_R, HIGHLIGHT_G, HIGHLIGHT_B};
        }
        return new float[]{CELL_R, CELL_G, CELL_B};
    }

    private static void renderGraph(BufferBuilder buffer, Matrix4f matrix, GraphData graphData) {
        for (GraphDebugPacket.CellData cellData : graphData.cells.values()) {
            boolean isGraphStart = cellData.hubs.stream().anyMatch(hub -> hub.equals(graphData.graphStart));
            boolean isGraphExit = cellContains(cellData, graphData.graphExits);
            boolean isHighlighted = cellData.hubs.stream().anyMatch(hub -> graphData.highlightedPath.contains(hub));

            float[] cellColor = getCellColor(isGraphStart, isGraphExit, isHighlighted);
            renderCellBoundingBox(buffer, matrix, cellData.boundsMin, cellData.boundsMax,
                cellColor[0], cellColor[1], cellColor[2]);

            for (BlockPos hub : cellData.hubs) {
                renderHub(buffer, matrix, hub, HUB_R, HUB_G, HUB_B);
            }

            for (BlockPos hub : cellData.hubs) {
                Map<BlockPos, float[]> edgeColors = new HashMap<>();
                int destIndex = 0;
                int destCount = cellData.paths.size();

                for (Map.Entry<BlockPos, BlockPos> pathEntry : cellData.paths.entrySet()) {
                    BlockPos destination = pathEntry.getKey();
                    BlockPos nextHop = pathEntry.getValue();
                    if (nextHop != null) {
                        float[] destColor = hueToRgb((float) destIndex / destCount);
                        float[] existing = edgeColors.get(nextHop);
                        if (existing == null) {
                            edgeColors.put(nextHop, new float[]{destColor[0], destColor[1], destColor[2], 1});
                        } else {
                            existing[0] += destColor[0];
                            existing[1] += destColor[1];
                            existing[2] += destColor[2];
                            existing[3] += 1;
                        }
                    }
                    destIndex++;
                }

                for (Map.Entry<BlockPos, float[]> entry : edgeColors.entrySet()) {
                    float[] color = entry.getValue();
                    float count = color[3];
                    renderEdge(buffer, matrix, hub, entry.getKey(),
                        color[0] / count, color[1] / count, color[2] / count);
                }
            }
        }
    }

    private static float[] hueToRgb(float hue) {
        int i = (int) (hue * 6);
        float f = hue * 6 - i;
        float q = 1 - f;
        float t = f;

        return switch (i % 6) {
            case 0 -> new float[]{1, t, 0};
            case 1 -> new float[]{q, 1, 0};
            case 2 -> new float[]{0, 1, t};
            case 3 -> new float[]{0, q, 1};
            case 4 -> new float[]{t, 0, 1};
            default -> new float[]{1, 0, q};
        };
    }

    private static void renderCellBoundingBox(BufferBuilder buffer, Matrix4f matrix,
                                               BlockPos boundsMin, BlockPos boundsMax,
                                               float r, float g, float b) {
        float minX = boundsMin.getX();
        float minY = boundsMin.getY();
        float minZ = boundsMin.getZ();
        float maxX = boundsMax.getX();
        float maxY = boundsMax.getY();
        float maxZ = boundsMax.getZ();

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
    }

    private static void renderHub(BufferBuilder buffer, Matrix4f matrix, BlockPos hub, float r, float g, float b) {
        float x = hub.getX() + 0.5f;
        float y = hub.getY() + 0.5f;
        float z = hub.getZ() + 0.5f;

        float minX = x - HUB_SIZE;
        float maxX = x + HUB_SIZE;
        float minY = y - HUB_SIZE;
        float maxY = y + HUB_SIZE;
        float minZ = z - HUB_SIZE;
        float maxZ = z + HUB_SIZE;

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, LINE_ALPHA).endVertex();
    }

    private static void renderEdge(BufferBuilder buffer, Matrix4f matrix,
                                    BlockPos from, BlockPos to, float r, float g, float b) {
        float x1 = from.getX() + 0.5f;
        float y1 = from.getY() + 0.5f;
        float z1 = from.getZ() + 0.5f;

        float x2 = to.getX() + 0.5f;
        float y2 = to.getY() + 0.5f;
        float z2 = to.getZ() + 0.5f;

        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, LINE_ALPHA).endVertex();
    }

    private static class GraphData {
        public final BlockPos graphStart;
        public final Set<BlockPos> graphExits;
        public final Map<BlockPos, GraphDebugPacket.CellData> cells;
        public final Set<BlockPos> highlightedPath;

        GraphData(BlockPos graphStart, Set<BlockPos> graphExits,
                  Map<BlockPos, GraphDebugPacket.CellData> cells,
                  Set<BlockPos> highlightedPath) {
            this.graphStart = graphStart;
            this.graphExits = graphExits;
            this.cells = new HashMap<>(cells);
            this.highlightedPath = highlightedPath;
        }
    }
}
