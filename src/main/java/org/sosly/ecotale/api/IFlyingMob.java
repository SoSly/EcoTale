package org.sosly.ecotale.api;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

public interface IFlyingMob<T extends Mob> {
    Brain<?> getBrain();
    PathNavigation getNavigation();
    boolean isResting();
    Level level();
    @Nullable
    BlockPos getAnchorPoint();

    double getMaxWanderDistance();
}
