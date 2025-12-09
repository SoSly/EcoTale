package org.sosly.ecotale.api.entities;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

/**
 * Implemented by flying mobs that use EcoTale's brain-based AI behaviors.
 *
 * <p>This interface provides the contract for flying mob behaviors like wandering and
 * navigation, allowing generic behaviors such as {@code FlyingRandomStroll} to work
 * with any implementing entity.</p>
 *
 * @param <T> the concrete mob type implementing this interface
 */
public interface IFlyingMob<T extends Mob> {
    /**
     * Returns the mob's path navigation controller.
     *
     * @return the navigation instance
     */
    PathNavigation getNavigation();

    /**
     * Returns the anchor point that constrains wandering behavior.
     *
     * <p>For roost-based mobs, this is typically the cave exit point when outside
     * or the roost position when inside. Wandering behaviors use this to keep
     * the mob within range of its home.</p>
     *
     * @return the anchor position, or null if unconstrained
     */
    @Nullable
    BlockPos getAnchorPoint();

    /**
     * Returns the maximum distance the mob should wander from its anchor point.
     *
     * @return the maximum wander distance in blocks
     */
    double getMaxWanderDistance();
}
