package org.sosly.ecotale.entities.ai.navigation;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;

public class BatPathNavigation extends FlyingPathNavigation {

    public BatPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected boolean canMoveDirectly(Vec3 from, Vec3 to) {
        boolean hasFlyTarget = mob.getBrain()
            .getMemory(MemoryModuleTypes.FLY_TARGET.get())
            .isPresent();

        if (hasFlyTarget) {
            return false;
        }

        return super.canMoveDirectly(from, to);
    }

}
