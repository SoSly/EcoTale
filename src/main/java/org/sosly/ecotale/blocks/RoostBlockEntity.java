package org.sosly.ecotale.blocks;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.EntityRegistry;
import org.sosly.ecotale.navigation.FlowFieldManager;
import org.sosly.ecotale.navigation.FlowFieldSolution;

public class RoostBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_FLOW_FIELD = "flowField";
    private static final String TAG_FAILURE_COUNT = "failureCount";
    private static final String TAG_NEXT_RETRY_TICK = "nextRetryTick";
    private static final int VALIDATION_INTERVAL = 500;
    private static final int BASE_RETRY_DELAY = 500;
    private static final int MAX_RETRY_DELAY = 6000;

    private FlowFieldSolution flowFieldSolution;
    private int validationTicker = VALIDATION_INTERVAL;
    private int failureCount;
    private long nextRetryTick;
    private boolean pendingRequest;

    public RoostBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.ROOST.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (flowFieldSolution == null && !pendingRequest) {
            requestGenerationWithBackoff();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (flowFieldSolution != null) {
            tag.put(TAG_FLOW_FIELD, flowFieldSolution.save());
        }
        if (failureCount > 0) {
            tag.putInt(TAG_FAILURE_COUNT, failureCount);
            tag.putLong(TAG_NEXT_RETRY_TICK, nextRetryTick);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_FLOW_FIELD)) {
            flowFieldSolution = FlowFieldSolution.load(tag.getCompound(TAG_FLOW_FIELD));
        }
        failureCount = tag.getInt(TAG_FAILURE_COUNT);
        nextRetryTick = tag.getLong(TAG_NEXT_RETRY_TICK);
    }

    public FlowFieldSolution getFlowFieldSolution() {
        return flowFieldSolution;
    }

    public void setFlowFieldSolution(FlowFieldSolution solution) {
        this.flowFieldSolution = solution;
        if (solution != null && !solution.isFailed()) {
            failureCount = 0;
            nextRetryTick = 0;
        }
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RoostBlockEntity roost) {
        roost.validationTicker--;
        if (roost.validationTicker <= 0) {
            roost.validationTicker = VALIDATION_INTERVAL;
            roost.validateAndRegenerate();
        }
    }

    private void validateAndRegenerate() {
        Level level = getLevel();
        if (level == null || level.isClientSide() || pendingRequest) {
            return;
        }

        long gameTime = level.getGameTime();

        if (flowFieldSolution == null || flowFieldSolution.isFailed()) {
            if (gameTime >= nextRetryTick) {
                requestGenerationWithBackoff();
            }
            return;
        }

        pendingRequest = true;
        FlowFieldManager.getInstance().requestValidation(this, flowFieldSolution);
    }

    private void requestGenerationWithBackoff() {
        if (pendingRequest) {
            return;
        }

        pendingRequest = true;
        FlowFieldManager.getInstance().requestGeneration(this);
    }

    public void forceRevalidate() {
        failureCount = 0;
        nextRetryTick = 0;
        pendingRequest = false;
        validateAndRegenerate();
    }

    public void onGenerationComplete(FlowFieldSolution solution) {
        pendingRequest = false;

        if (solution == null || solution.isFailed()) {
            handleGenerationFailure();
            return;
        }

        flowFieldSolution = solution;
        failureCount = 0;
        nextRetryTick = 0;
        setChanged();
    }

    public void onValidationComplete(Boolean valid) {
        pendingRequest = false;

        if (!valid) {
            requestGenerationWithBackoff();
        }
    }

    private void handleGenerationFailure() {
        Level level = getLevel();
        if (level == null) {
            return;
        }

        failureCount++;
        int delay = Math.min(BASE_RETRY_DELAY * (1 << (failureCount - 1)), MAX_RETRY_DELAY);
        nextRetryTick = level.getGameTime() + delay;
        setChanged();
    }

    public void spawnColony(WorldGenLevel level, RandomSource random) {
        ServerLevel serverLevel = level.getLevel();
        BlockPos roostPos = this.getBlockPos();
        GlobalPos home = GlobalPos.of(serverLevel.dimension(), roostPos);
        int count = random.nextIntBetweenInclusive(1, 4);

        for (int i = 0; i < count; i++) {
            EcoTaleBat bat = EntityRegistry.BAT.get().create(serverLevel);
            if (bat == null) {
                continue;
            }

            double x = roostPos.getX() + 0.1 + random.nextDouble() * 0.8;
            double y = roostPos.getY() - 0.1;
            double z = roostPos.getZ() + 0.1 + random.nextDouble() * 0.8;

            bat.moveTo(x, y, z, random.nextFloat() * 360F, 0F);
            bat.setResting(true);
            bat.setHome(home);
            level.addFreshEntity(bat);
        }
    }
}
