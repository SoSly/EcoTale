package org.sosly.ecotale.entities.ai.control;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

public class FlyingMobMoveControl extends MoveControl {
    private final int maxTurn;
    private final boolean hoversInPlace;

    public FlyingMobMoveControl(Mob mob, int maxTurn, boolean hoversInPlace) {
        super(mob);
        this.maxTurn = maxTurn;
        this.hoversInPlace = hoversInPlace;
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            handleIdle();
            return;
        }

        this.mob.setNoGravity(true);

        double dx = this.wantedX - this.mob.getX();
        double dy = this.wantedY - this.mob.getY();
        double dz = this.wantedZ - this.mob.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq < 0.25) {
            this.operation = Operation.WAIT;
            this.mob.setYya(0.0F);
            this.mob.setZza(0.0F);
            return;
        }

        double totalDist = Math.sqrt(distSq);
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        float speed = calculateSpeed();
        float horizRatio = (float) (horizDist / totalDist);

        applyHorizontalMovement(dx, dz, horizDist, speed, horizRatio);
        applyVerticalMovement(dy, speed);
        applyPitch(dy, horizDist);
    }

    private void handleIdle() {
        if (!this.hoversInPlace) {
            this.mob.setNoGravity(false);
        }
        this.mob.setYya(0.0F);
        this.mob.setZza(0.0F);
    }

    private float calculateSpeed() {
        if (this.mob.onGround()) {
            return (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
        }
        return (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.FLYING_SPEED));
    }

    private void applyHorizontalMovement(double dx, double dz, double horizDist, float speed, float horizRatio) {
        if (horizDist < 1.0E-5F) {
            this.mob.setSpeed(0);
            return;
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), targetYaw, 90.0F));
        this.mob.setSpeed(speed * horizRatio);
    }

    private void applyVerticalMovement(double dy, float speed) {
        if (Math.abs(dy) < 1.0E-5F) {
            return;
        }

        double verticalVelocity = Math.signum(dy) * speed;
        this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0, verticalVelocity * 0.5, 0));
    }

    private void applyPitch(double dy, double horizDist) {
        if (Math.abs(dy) < 1.0E-5F && horizDist < 1.0E-5F) {
            return;
        }

        float targetPitch = (float) (-(Mth.atan2(dy, horizDist) * (180F / Math.PI)));
        this.mob.setXRot(this.rotlerp(this.mob.getXRot(), targetPitch, (float) this.maxTurn));
    }
}
