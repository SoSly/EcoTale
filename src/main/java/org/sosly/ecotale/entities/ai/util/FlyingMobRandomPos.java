package org.sosly.ecotale.entities.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.sosly.ecotale.api.entities.IFlyingMob;

import javax.annotation.Nullable;

public final class FlyingMobRandomPos {
    private static final int ATTEMPTS = 10;

    private FlyingMobRandomPos() {
    }

    @Nullable
    public static <T extends Mob & IFlyingMob<T>> Vec3 getPos(T mob, int horizontalRange, int verticalRange) {
        return getPos(mob, horizontalRange, verticalRange, null, 0);
    }

    @Nullable
    public static <T extends Mob & IFlyingMob<T>> Vec3 getPos(
            T mob, int horizontalRange, int verticalRange,
            @Nullable BlockPos anchor, double maxDistanceFromAnchor) {
        RandomSource random = mob.getRandom();

        for (int i = 0; i < ATTEMPTS; i++) {
            BlockPos candidate = generateCandidate(mob, random, horizontalRange, verticalRange);
            if (!isValidFlyingTarget(mob, candidate)) {
                continue;
            }
            if (anchor != null && !candidate.closerThan(anchor, maxDistanceFromAnchor)) {
                continue;
            }
            return Vec3.atCenterOf(candidate);
        }

        return null;
    }

    private static <T extends Mob & IFlyingMob<T>> BlockPos generateCandidate(
            T mob, RandomSource random, int hRange, int vRange) {
        int x = random.nextInt(2 * hRange + 1) - hRange;
        int y = random.nextInt(2 * vRange + 1) - vRange;
        int z = random.nextInt(2 * hRange + 1) - hRange;
        return mob.blockPosition().offset(x, y, z);
    }

    private static <T extends Mob & IFlyingMob<T>> boolean isValidFlyingTarget(T mob, BlockPos pos) {
        Level level = mob.level();

        if (!level.isEmptyBlock(pos)) {
            return false;
        }
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.isEmptyBlock(pos.above())) {
            return false;
        }

        Vec3 start = mob.position();
        Vec3 end = Vec3.atCenterOf(pos);
        BlockHitResult hit = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob
        ));
        return hit.getType() == HitResult.Type.MISS;
    }
}
