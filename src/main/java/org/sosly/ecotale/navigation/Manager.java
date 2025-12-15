package org.sosly.ecotale.navigation;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.sosly.ecotale.blocks.RoostBlockEntity;
import org.sosly.ecotale.entities.EntityRegistry;

public class Manager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int POOL_SIZE = 2;
    private static final int BLOCK_CHANGE_RADIUS = 128;
    private static final long DEBOUNCE_DELAY_MS = 500;

    private static Manager instance;

    private final Map<BlockPos, GenerationState> inFlightRequests;
    private final Map<BlockPos, WeakReference<RoostBlockEntity>> activeRoosts;
    private final Map<BlockPos, ScheduledFuture<?>> pendingBlockChanges;
    private ExecutorService workerPool;
    private ExecutorService priorityWorker;
    private ScheduledExecutorService scheduler;
    private MinecraftServer server;
    private volatile boolean running;
    private volatile boolean paused;

    private Manager() {
        this.inFlightRequests = new ConcurrentHashMap<>();
        this.activeRoosts = new ConcurrentHashMap<>();
        this.pendingBlockChanges = new ConcurrentHashMap<>();
    }

    public static Manager getInstance() {
        if (instance == null) {
            instance = new Manager();
        }
        return instance;
    }

    public void start(MinecraftServer server) {
        if (running) {
            return;
        }

        this.server = server;
        this.running = true;
        this.workerPool = Executors.newFixedThreadPool(POOL_SIZE, new ThreadFactory() {
            private int threadNum = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "EcoTale-NavGraph-" + threadNum++);
                thread.setDaemon(true);
                return thread;
            }
        });

        this.priorityWorker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "EcoTale-NavGraph-Priority");
            thread.setDaemon(true);
            return thread;
        });

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "EcoTale-NavGraph-Scheduler");
            thread.setDaemon(true);
            return thread;
        });

        LOGGER.info("Navigation graph worker pool started with {} threads + priority", POOL_SIZE);
    }

    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            workerPool = null;
        }

        if (priorityWorker != null) {
            priorityWorker.shutdown();
            try {
                if (!priorityWorker.awaitTermination(2, TimeUnit.SECONDS)) {
                    priorityWorker.shutdownNow();
                }
            } catch (InterruptedException e) {
                priorityWorker.shutdownNow();
                Thread.currentThread().interrupt();
            }
            priorityWorker = null;
        }

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        server = null;
        inFlightRequests.clear();
        pendingBlockChanges.clear();
        LOGGER.info("Navigation graph worker pool stopped");
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        LOGGER.info("Navigation graph generation {}", paused ? "paused" : "resumed");
    }

    public boolean isPaused() {
        return paused;
    }

    public void requestGeneration(BlockPos origin, Level level, EntityType<?> entityType,
                                   Consumer<Graph> callback) {
        if (!running) {
            return;
        }
        if (paused) {
            return;
        }

        GenerationState existing = inFlightRequests.get(origin);
        if (existing != null) {
            existing.task().cancel(true);
        }

        Future<?> task = workerPool.submit(() -> {
            try {
                waitWhilePaused();
                if (!running) {
                    return;
                }
                processGeneration(origin, level, entityType, callback);
            } catch (Exception e) {
                LOGGER.error("Error processing navigation graph generation at {}", origin, e);
            } finally {
                inFlightRequests.remove(origin);
            }
        });

        inFlightRequests.put(origin, new GenerationState(task, System.currentTimeMillis()));
    }

    public void requestPriorityGeneration(BlockPos origin, Level level, EntityType<?> entityType,
                                          Consumer<Graph> callback) {
        if (!running) {
            return;
        }

        priorityWorker.submit(() -> {
            try {
                if (!running) {
                    return;
                }
                processGeneration(origin, level, entityType, callback);
            } catch (Exception e) {
                LOGGER.error("Error processing priority navigation graph generation at {}", origin, e);
            }
        });
    }

    public void registerRoost(RoostBlockEntity roost) {
        activeRoosts.put(roost.getBlockPos(), new WeakReference<>(roost));
    }

    public void unregisterRoost(RoostBlockEntity roost) {
        activeRoosts.remove(roost.getBlockPos());
    }

    public Map<BlockPos, Boolean> getRoostStatuses() {
        Map<BlockPos, Boolean> statuses = new HashMap<>();
        for (Map.Entry<BlockPos, WeakReference<RoostBlockEntity>> entry : activeRoosts.entrySet()) {
            RoostBlockEntity roost = entry.getValue().get();
            if (roost != null) {
                boolean hasGraph = roost.getGraph() != null;
                statuses.put(entry.getKey(), hasGraph);
            }
        }
        return statuses;
    }

    public void onBlockChange(BlockPos changedPos, Level level) {
        if (!running || paused) {
            return;
        }

        int radiusSquared = BLOCK_CHANGE_RADIUS * BLOCK_CHANGE_RADIUS;

        for (Map.Entry<BlockPos, WeakReference<RoostBlockEntity>> entry : activeRoosts.entrySet()) {
            WeakReference<RoostBlockEntity> roostRef = entry.getValue();
            RoostBlockEntity roost = roostRef.get();

            if (roost == null) {
                continue;
            }

            BlockPos roostPos = roost.getBlockPos();
            if (changedPos.distSqr(roostPos) <= radiusSquared) {
                scheduleDebounced(roost, level);
            }
        }
    }

    private void scheduleDebounced(RoostBlockEntity roost, Level level) {
        BlockPos roostPos = roost.getBlockPos();

        ScheduledFuture<?> existingFuture = pendingBlockChanges.get(roostPos);
        if (existingFuture != null) {
            existingFuture.cancel(false);
        }

        ScheduledFuture<?> newFuture = scheduler.schedule(
                () -> requestGeneration(roostPos, level, EntityRegistry.BAT.get(), roost::onGraphGenerationComplete),
                DEBOUNCE_DELAY_MS,
                TimeUnit.MILLISECONDS
        );

        pendingBlockChanges.put(roostPos, newFuture);
    }

    private void waitWhilePaused() {
        while (paused && running) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void processGeneration(BlockPos origin, Level level, EntityType<?> entityType,
                                    Consumer<Graph> callback) {
        long startTime = System.nanoTime();

        GraphGenerator generator = new GraphGenerator(origin, level, entityType);
        Graph graph = generator.generate();

        long elapsed = System.nanoTime() - startTime;

        if (graph == null) {
            LOGGER.debug("Navigation graph generation FAILED at {}: {}ms",
                    origin, elapsed / 1_000_000.0);
        } else {
            LOGGER.debug("Navigation graph generation at {}: {}ms, {} cells, {} exits",
                    origin,
                    elapsed / 1_000_000.0,
                    graph.getCells().size(),
                    graph.getGraphExits().size());
        }

        scheduleCallback(callback, graph);
    }

    private void scheduleCallback(Consumer<Graph> callback, Graph result) {
        if (server == null || !running) {
            return;
        }

        server.execute(() -> callback.accept(result));
    }

    private record GenerationState(Future<?> task, long startedAt) {
    }
}
