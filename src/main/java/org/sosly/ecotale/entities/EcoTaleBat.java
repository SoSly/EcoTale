package org.sosly.ecotale.entities;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.jetbrains.annotations.NotNull;
import org.sosly.ecotale.entities.ai.Activities;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;
import org.sosly.ecotale.entities.ai.Schedules;
import org.sosly.ecotale.entities.ai.SensorTypes;
import org.sosly.ecotale.entities.ai.behavior.bat.DespawnIfHomeless;
import org.sosly.ecotale.entities.ai.behavior.bat.DropGuano;
import org.sosly.ecotale.entities.ai.behavior.bat.RestAtRoost;

public class EcoTaleBat extends Bat {
    private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.HOME,
            MemoryModuleType.PATH,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleTypes.FLY_TARGET.get()
    );
    private static final ImmutableList<SensorType<? extends Sensor<? super EcoTaleBat>>> SENSOR_TYPES =
            ImmutableList.of(SensorTypes.HOME.get());

    public EcoTaleBat(EntityType<? extends Bat> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Bat.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D);
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(false);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected Brain.@NotNull Provider<EcoTaleBat> brainProvider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    @Override
    protected @NotNull Brain<?> makeBrain(@NotNull Dynamic<?> dynamic) {
        Brain<EcoTaleBat> brain = this.brainProvider().makeBrain(dynamic);
        registerBrainGoals(brain);
        return brain;
    }

    private void registerBrainGoals(Brain<EcoTaleBat> brain) {
        brain.addActivity(Activity.CORE, ImmutableList.of(
                Pair.of(0, new DespawnIfHomeless())
        ));

        brain.addActivity(Activities.ROOST.get(), ImmutableList.of(
                Pair.of(0, new RestAtRoost()),
                Pair.of(1, new DropGuano())
        ));

        brain.addActivity(Activities.FORAGE.get(), ImmutableList.of());

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setSchedule(Schedules.BAT_DEFAULT.get());
        brain.setDefaultActivity(Activities.ROOST.get());
        brain.setActiveActivityIfPossible(Activities.ROOST.get());
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull Brain<EcoTaleBat> getBrain() {
        return (Brain<EcoTaleBat>) super.getBrain();
    }

    @Override
    protected void customServerAiStep() {
        ServerLevel level = (ServerLevel) this.level();
        this.level().getProfiler().push("ecoTaleBatBrain");
        this.getBrain().tick(level, this);
        this.level().getProfiler().pop();
        this.getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.getBrain().getMemory(MemoryModuleType.HOME).ifPresent(globalPos -> {
            tag.putString("HomeDimension", globalPos.dimension().location().toString());
            tag.putLong("HomePos", globalPos.pos().asLong());
        });
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomePos")) {
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION,
                    new ResourceLocation(tag.getString("HomeDimension"))
            );
            BlockPos pos = BlockPos.of(tag.getLong("HomePos"));
            this.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(dimension, pos));
        }
    }

    public void setHome(GlobalPos pos) {
        this.getBrain().setMemory(MemoryModuleType.HOME, pos);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToPlayer) {
        return false;
    }
}
