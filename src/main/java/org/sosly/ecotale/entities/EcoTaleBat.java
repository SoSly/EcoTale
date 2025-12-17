package org.sosly.ecotale.entities;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
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
import org.sosly.ecotale.entities.ai.control.FlyingMobMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.sosly.ecotale.entities.ai.navigation.BatPathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.jetbrains.annotations.NotNull;
import org.sosly.ecotale.api.entities.IFlyingMob;
import org.sosly.ecotale.blocks.RoostBlockEntity;
import org.sosly.ecotale.entities.ai.Activities;
import org.sosly.ecotale.entities.ai.MemoryModuleTypes;
import org.sosly.ecotale.entities.ai.Schedules;
import org.sosly.ecotale.entities.ai.SensorTypes;
import org.sosly.ecotale.entities.ai.behavior.FlyingRandomStroll;
import org.sosly.ecotale.entities.ai.behavior.bat.DespawnIfHomeless;
import org.sosly.ecotale.entities.ai.behavior.bat.DropGuano;
import org.sosly.ecotale.entities.ai.behavior.bat.ExitCave;
import org.sosly.ecotale.entities.ai.behavior.bat.RestAtRoost;
import org.sosly.ecotale.entities.ai.behavior.bat.ReturnToRoost;
import org.sosly.ecotale.entities.ai.behavior.bat.WakeUp;
import org.sosly.ecotale.entities.ai.behavior.bat.WakeIfRoostDistant;
import org.sosly.ecotale.navigation.Graph;

public class EcoTaleBat extends Bat implements IFlyingMob<EcoTaleBat> {
    private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.HOME,
            MemoryModuleType.PATH,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleTypes.FLY_TARGET.get(),
            MemoryModuleTypes.ROOST.get()
    );
    private static final ImmutableList<SensorType<? extends Sensor<? super EcoTaleBat>>> SENSOR_TYPES =
            ImmutableList.of(SensorTypes.HOME.get());

    public EcoTaleBat(EntityType<? extends Bat> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMobMoveControl(this, 20, true);
        this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Bat.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.FLYING_SPEED, 0.2D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D);
    }

    @Override
    protected @NotNull BatPathNavigation createNavigation(@NotNull Level level) {
        BatPathNavigation nav = new BatPathNavigation(this, level);
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
                Pair.of(0, new WakeIfRoostDistant()),
                Pair.of(1, new RestAtRoost()),
                Pair.of(2, new DropGuano()),
                Pair.of(3, new ReturnToRoost()),
                Pair.of(4, new FlyingRandomStroll<>())
        ));

        brain.addActivity(Activities.FORAGE.get(), ImmutableList.of(
                Pair.of(0, new ExitCave()),
                Pair.of(1, new FlyingRandomStroll<>()),
                Pair.of(2, new WakeUp())
        ));

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
    public boolean isSleeping() {
        return this.isResting();
    }

    @Override
    protected float getFlyingSpeed() {
        return this.getSpeed();
    }

    @Override
    protected void customServerAiStep() {
        if (this.isSleeping() && this.getNavigation().isInProgress()) {
            this.getNavigation().stop();
        }

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
        this.getBrain().getMemory(MemoryModuleTypes.ROOST.get()).ifPresent(globalPos -> {
            tag.putString("RoostDimension", globalPos.dimension().location().toString());
            tag.putLong("RoostPos", globalPos.pos().asLong());
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
        if (tag.contains("RoostPos")) {
            ResourceKey<Level> dimension = ResourceKey.create(
                    Registries.DIMENSION,
                    new ResourceLocation(tag.getString("RoostDimension"))
            );
            BlockPos pos = BlockPos.of(tag.getLong("RoostPos"));
            this.getBrain().setMemory(MemoryModuleTypes.ROOST.get(), GlobalPos.of(dimension, pos));
        }
    }

    public void setHome(GlobalPos pos) {
        this.getBrain().setMemory(MemoryModuleType.HOME, pos);
    }

    public void setRoost(GlobalPos pos) {
        this.getBrain().setMemory(MemoryModuleTypes.ROOST.get(), pos);
    }

    @Override
    public BlockPos getAnchorPoint() {
        GlobalPos home = this.getBrain()
                .getMemory(MemoryModuleType.HOME)
                .orElse(null);
        if (home == null) {
            return null;
        }
        if (!home.dimension().equals(this.level().dimension())) {
            return home.pos();
        }

        BlockEntity blockEntity = this.level().getBlockEntity(home.pos());
        if (!(blockEntity instanceof RoostBlockEntity roost)) {
            return home.pos();
        }

        Graph graph = roost.getGraph();
        if (graph == null || graph.getGraphExits().isEmpty()) {
            return home.pos();
        }

        return graph.getGraphExits().iterator().next();
    }

    @Override
    public double getMaxWanderDistance() {
        return 64.0;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToPlayer) {
        return false;
    }
}
