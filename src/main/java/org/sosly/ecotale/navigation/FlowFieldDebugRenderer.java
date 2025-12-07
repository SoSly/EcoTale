package org.sosly.ecotale.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class FlowFieldDebugRenderer {
    private static final int PARTICLES_PER_DIRECTION = 3;
    private static final double PARTICLE_SPACING = 1.0;
    private static final int EXIT_COLUMN_HEIGHT = 5;

    private FlowFieldDebugRenderer() {
    }

    public static void render(ServerLevel level, FlowFieldSolution solution) {
        solution.forEachOutwardCell((cell, direction) ->
            renderDirectionParticles(level, cell, direction, true));

        solution.forEachInwardCell((cell, direction) ->
            renderDirectionParticles(level, cell, direction, false));

        BlockPos exitPoint = solution.getExitPoint();
        if (exitPoint != null) {
            renderExitPoint(level, exitPoint);
        }
    }

    private static void renderDirectionParticles(
            ServerLevel level,
            FlowFieldCell cell,
            Vec3 direction,
            boolean isOutward) {
        BlockPos center = cell.centerBlockPos();
        double x = center.getX() + 0.5;
        double y = center.getY() + 0.5;
        double z = center.getZ() + 0.5;

        Vec3 normalized = direction.normalize();

        for (int i = 0; i < PARTICLES_PER_DIRECTION; i++) {
            double offset = i * PARTICLE_SPACING;
            double px = x + normalized.x * offset;
            double py = y + normalized.y * offset;
            double pz = z + normalized.z * offset;

            if (isOutward) {
                level.sendParticles(ParticleTypes.FLAME, px, py, pz, 1, 0, 0, 0, 0);
            } else {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void renderExitPoint(ServerLevel level, BlockPos exitPoint) {
        double x = exitPoint.getX() + 0.5;
        double z = exitPoint.getZ() + 0.5;

        for (int i = 0; i < EXIT_COLUMN_HEIGHT; i++) {
            double y = exitPoint.getY() + i;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 3, 0.2, 0.2, 0.2, 0);
        }
    }
}
