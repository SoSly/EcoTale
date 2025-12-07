package org.sosly.ecotale.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.sosly.ecotale.navigation.FlowFieldCell;
import org.sosly.ecotale.navigation.FlowFieldSolution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class FlowFieldDebugRenderer {
    private static final Map<BlockPos, FlowFieldSolution> ACTIVE_RENDERS = new ConcurrentHashMap<>();

    private static final float OUTWARD_R = 1.0f;
    private static final float OUTWARD_G = 0.5f;
    private static final float OUTWARD_B = 0.0f;

    private static final float INWARD_R = 0.2f;
    private static final float INWARD_G = 0.6f;
    private static final float INWARD_B = 1.0f;

    private static final float EXIT_R = 0.2f;
    private static final float EXIT_G = 1.0f;
    private static final float EXIT_B = 0.2f;

    private static final float LINE_ALPHA = 1.0f;
    private static final int EXIT_COLUMN_HEIGHT = 5;
    private static final float LINE_OFFSET = 0.15f;
    private static final float ARROWHEAD_LENGTH = 0.8f;
    private static final float ARROWHEAD_WIDTH = 0.4f;

    private FlowFieldDebugRenderer() {
    }

    public static void toggle(BlockPos roostPos, FlowFieldSolution solution) {
        if (ACTIVE_RENDERS.containsKey(roostPos)) {
            ACTIVE_RENDERS.remove(roostPos);
        } else {
            ACTIVE_RENDERS.put(roostPos, solution);
        }
    }

    public static void clear() {
        ACTIVE_RENDERS.clear();
    }

    public static boolean isRendering(BlockPos roostPos) {
        return ACTIVE_RENDERS.containsKey(roostPos);
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

        for (FlowFieldSolution solution : ACTIVE_RENDERS.values()) {
            renderSolution(buffer, matrix, solution);
        }

        tesselator.end();

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderSolution(BufferBuilder buffer, Matrix4f matrix, FlowFieldSolution solution) {
        solution.forEachOutwardCell((cell, direction) ->
            renderCellArrow(buffer, matrix, cell, direction, LINE_OFFSET, OUTWARD_R, OUTWARD_G, OUTWARD_B));

        solution.forEachInwardCell((cell, direction) ->
            renderCellArrow(buffer, matrix, cell, direction, -LINE_OFFSET, INWARD_R, INWARD_G, INWARD_B));

        BlockPos exitPoint = solution.getExitPoint();
        if (exitPoint != null) {
            renderExitColumn(buffer, matrix, exitPoint);
        }
    }

    private static void renderCellArrow(
            BufferBuilder buffer,
            Matrix4f matrix,
            FlowFieldCell cell,
            Vec3 direction,
            float offset,
            float r, float g, float b) {
        BlockPos center = cell.centerBlockPos();
        Vec3 normalized = direction.normalize();

        Vec3 perpendicular = getConsistentPerpendicular(cell);
        float offsetX = (float) perpendicular.x * offset;
        float offsetY = (float) perpendicular.y * offset;
        float offsetZ = (float) perpendicular.z * offset;

        float x = center.getX() + 0.5f + offsetX;
        float y = center.getY() + 0.5f + offsetY;
        float z = center.getZ() + 0.5f + offsetZ;

        float length = FlowFieldCell.RESOLUTION * 0.9f;

        float endX = x + (float) normalized.x * length;
        float endY = y + (float) normalized.y * length;
        float endZ = z + (float) normalized.z * length;

        buffer.vertex(matrix, x, y, z).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, endX, endY, endZ).color(r, g, b, LINE_ALPHA).endVertex();

        Vec3 arrowPerpendicular = findPerpendicular(normalized);
        renderArrowhead(buffer, matrix, endX, endY, endZ, normalized, arrowPerpendicular, r, g, b);
    }

    private static Vec3 getConsistentPerpendicular(FlowFieldCell cell) {
        int hash = cell.x() * 73856093 ^ cell.y() * 19349663 ^ cell.z() * 83492791;
        double angle = (hash & 0xFFFF) / 65535.0 * Math.PI * 2;
        return new Vec3(Math.cos(angle), 0, Math.sin(angle)).normalize();
    }

    private static void renderArrowhead(
            BufferBuilder buffer,
            Matrix4f matrix,
            float tipX, float tipY, float tipZ,
            Vec3 direction,
            Vec3 perpendicular,
            float r, float g, float b) {
        Vec3 perpendicular2 = direction.cross(perpendicular).normalize();

        float baseX = tipX - (float) direction.x * ARROWHEAD_LENGTH;
        float baseY = tipY - (float) direction.y * ARROWHEAD_LENGTH;
        float baseZ = tipZ - (float) direction.z * ARROWHEAD_LENGTH;

        float wing1X = baseX + (float) perpendicular.x * ARROWHEAD_WIDTH;
        float wing1Y = baseY + (float) perpendicular.y * ARROWHEAD_WIDTH;
        float wing1Z = baseZ + (float) perpendicular.z * ARROWHEAD_WIDTH;

        float wing2X = baseX - (float) perpendicular.x * ARROWHEAD_WIDTH;
        float wing2Y = baseY - (float) perpendicular.y * ARROWHEAD_WIDTH;
        float wing2Z = baseZ - (float) perpendicular.z * ARROWHEAD_WIDTH;

        float wing3X = baseX + (float) perpendicular2.x * ARROWHEAD_WIDTH;
        float wing3Y = baseY + (float) perpendicular2.y * ARROWHEAD_WIDTH;
        float wing3Z = baseZ + (float) perpendicular2.z * ARROWHEAD_WIDTH;

        float wing4X = baseX - (float) perpendicular2.x * ARROWHEAD_WIDTH;
        float wing4Y = baseY - (float) perpendicular2.y * ARROWHEAD_WIDTH;
        float wing4Z = baseZ - (float) perpendicular2.z * ARROWHEAD_WIDTH;

        buffer.vertex(matrix, tipX, tipY, tipZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, wing1X, wing1Y, wing1Z).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, tipX, tipY, tipZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, wing2X, wing2Y, wing2Z).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, tipX, tipY, tipZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, wing3X, wing3Y, wing3Z).color(r, g, b, LINE_ALPHA).endVertex();

        buffer.vertex(matrix, tipX, tipY, tipZ).color(r, g, b, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, wing4X, wing4Y, wing4Z).color(r, g, b, LINE_ALPHA).endVertex();
    }

    private static Vec3 findPerpendicular(Vec3 v) {
        if (Math.abs(v.x) < 0.9) {
            return v.cross(new Vec3(1, 0, 0)).normalize();
        }
        return v.cross(new Vec3(0, 1, 0)).normalize();
    }

    private static void renderExitColumn(BufferBuilder buffer, Matrix4f matrix, BlockPos exitPoint) {
        float x = exitPoint.getX() + 0.5f;
        float z = exitPoint.getZ() + 0.5f;
        float bottomY = exitPoint.getY();
        float topY = exitPoint.getY() + EXIT_COLUMN_HEIGHT;

        buffer.vertex(matrix, x, bottomY, z).color(EXIT_R, EXIT_G, EXIT_B, LINE_ALPHA).endVertex();
        buffer.vertex(matrix, x, topY, z).color(EXIT_R, EXIT_G, EXIT_B, LINE_ALPHA).endVertex();
    }
}
