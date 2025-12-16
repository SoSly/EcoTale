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
import org.sosly.ecotale.api.IGraphProvider;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.EntityRegistry;
import org.sosly.ecotale.navigation.Graph;
import org.sosly.ecotale.navigation.Manager;

public class RoostBlockEntity extends BlockEntity implements IGraphProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_GRAPH = "graph";
    private static final String TAG_FAILURE_COUNT = "failureCount";
    private static final String TAG_NEXT_RETRY_TICK = "nextRetryTick";
    private static final int BASE_RETRY_DELAY = 600;
    private static final int MAX_RETRY_DELAY = 6000;

    private Graph graph;
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
        if (graph == null && !pendingRequest) {
            requestGraphGeneration();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (graph != null) {
            tag.put(TAG_GRAPH, graph.save());
        }
        if (failureCount > 0) {
            tag.putInt(TAG_FAILURE_COUNT, failureCount);
            tag.putLong(TAG_NEXT_RETRY_TICK, nextRetryTick);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        graph = tag.contains(TAG_GRAPH) ? Graph.load(tag.getCompound(TAG_GRAPH)) : null;
        failureCount = tag.getInt(TAG_FAILURE_COUNT);
        nextRetryTick = tag.getLong(TAG_NEXT_RETRY_TICK);
    }

    @Override
    public Graph getGraph() {
        return graph;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RoostBlockEntity roost) {
        if (roost.graph == null && !roost.pendingRequest && level.getGameTime() >= roost.nextRetryTick) {
            roost.requestGraphGeneration();
        }
    }

    private void requestGraphGeneration() {
        Level level = getLevel();
        if (level == null || level.isClientSide() || pendingRequest) {
            return;
        }
        pendingRequest = true;
        Manager.getInstance().requestGeneration(
            getBlockPos(),
            level,
            EntityRegistry.BAT.get(),
            this::onGraphGenerationComplete
        );
    }

    public void forceRegenerate() {
        graph = null;
        failureCount = 0;
        nextRetryTick = 0;
        pendingRequest = true;
        setChanged();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            Manager.getInstance().requestPriorityGeneration(
                getBlockPos(),
                level,
                EntityRegistry.BAT.get(),
                this::onGraphGenerationComplete
            );
        }
    }

    public void onGraphGenerationComplete(Graph graph) {
        pendingRequest = false;
        if (graph == null) {
            handleGenerationFailure();
            return;
        }
        this.graph = graph;
        failureCount = 0;
        nextRetryTick = 0;
        setChanged();
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
        int count = random.nextIntBetweenInclusive(1, 4);
        spawnColony(level.getLevel(), count, random);
    }

    public int spawnColony(ServerLevel level, int count) {
        return spawnColony(level, count, level.getRandom());
    }

    private int spawnColony(ServerLevel level, int count, RandomSource random) {
        BlockPos roostPos = this.getBlockPos();
        GlobalPos home = GlobalPos.of(level.dimension(), roostPos);
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            EcoTaleBat bat = EntityRegistry.BAT.get().create(level);
            if (bat == null) {
                continue;
            }

            double x = roostPos.getX() + 0.1 + random.nextDouble() * 0.8;
            double y = roostPos.getY() - 0.1;
            double z = roostPos.getZ() + 0.1 + random.nextDouble() * 0.8;

            bat.moveTo(x, y, z, random.nextFloat() * 360F, 0F);
            bat.setResting(true);
            bat.setHome(home);
            bat.setRoost(home);
            level.addFreshEntity(bat);
            spawned++;
        }

        return spawned;
    }

}
