package org.sosly.ecotale.client;

import java.util.HashMap;
import java.util.Map;
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

@OnlyIn(Dist.CLIENT)
public final class RoostDebugRenderer {
    private static Map<BlockPos, Boolean> activeRoosts = new HashMap<>();
    private static boolean enabled = false;

    private static final float SUCCESS_R = 0.0f;
    private static final float SUCCESS_G = 1.0f;
    private static final float SUCCESS_B = 0.5f;

    private static final float FAILED_R = 1.0f;
    private static final float FAILED_G = 0.0f;
    private static final float FAILED_B = 0.0f;

    private static final float LINE_ALPHA = 1.0f;

    private RoostDebugRenderer() {
    }

    public static void toggle(Map<BlockPos, Boolean> roostStatuses) {
        if (enabled) {
            enabled = false;
            activeRoosts.clear();
        } else {
            enabled = true;
            activeRoosts = new HashMap<>(roostStatuses);
        }
    }

    public static void update(Map<BlockPos, Boolean> roostStatuses) {
        if (enabled) {
            activeRoosts = new HashMap<>(roostStatuses);
        }
    }

    public static void clear() {
        enabled = false;
        activeRoosts.clear();
    }

    public static void render(PoseStack poseStack) {
        if (!enabled || activeRoosts.isEmpty()) {
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

        for (Map.Entry<BlockPos, Boolean> entry : activeRoosts.entrySet()) {
            renderRoostBox(buffer, matrix, entry.getKey(), entry.getValue());
        }

        tesselator.end();

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderRoostBox(BufferBuilder buffer, Matrix4f matrix, BlockPos pos, boolean hasGraph) {
        float r = hasGraph ? SUCCESS_R : FAILED_R;
        float g = hasGraph ? SUCCESS_G : FAILED_G;
        float b = hasGraph ? SUCCESS_B : FAILED_B;

        float minX = pos.getX();
        float minY = pos.getY();
        float minZ = pos.getZ();
        float maxX = pos.getX() + 1;
        float maxY = pos.getY() + 1;
        float maxZ = pos.getZ() + 1;

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
}
