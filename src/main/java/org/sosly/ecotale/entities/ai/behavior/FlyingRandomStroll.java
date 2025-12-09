package org.sosly.ecotale.entities.ai.behavior;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.phys.Vec3;
import org.sosly.ecotale.Constants;
import org.sosly.ecotale.api.entities.IFlyingMob;
import org.sosly.ecotale.entities.ai.util.FlyingMobRandomPos;

public class FlyingRandomStroll<T extends Mob & IFlyingMob<T>> extends Behavior<T> {
    private static final int HORIZONTAL_RANGE = 8;
    private static final int VERTICAL_RANGE = 8;
    private static final double CLOSE_ENOUGH = 1.5;

    private Vec3 targetPos;
    private long lastNavTick;

    public FlyingRandomStroll() {
        super(ImmutableMap.of(), 1, 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, T mob) {
        if (mob.isSleeping()) {
            return false;
        }
        if (mob.getNavigation().isInProgress()) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, T mob, long gameTime) {
        if (mob.isSleeping()) {
            return false;
        }
        if (targetPos == null) {
            return false;
        }
        return !mob.position().closerThan(targetPos, CLOSE_ENOUGH);
    }

    @Override
    protected void start(ServerLevel level, T mob, long gameTime) {
        BlockPos anchor = mob.getAnchorPoint();
        double maxDistance = mob.getMaxWanderDistance();
        targetPos = FlyingMobRandomPos.getPos(mob, HORIZONTAL_RANGE, VERTICAL_RANGE, anchor, maxDistance);
        lastNavTick = 0;
    }

    @Override
    protected void tick(ServerLevel level, T mob, long gameTime) {
        if (gameTime - lastNavTick < Constants.NAV_INTERVAL_TICKS) {
            return;
        }
        lastNavTick = gameTime;

        if (targetPos != null && !mob.getNavigation().isInProgress()) {
            mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
        }
    }

    @Override
    protected void stop(ServerLevel level, T mob, long gameTime) {
        targetPos = null;
    }
}
