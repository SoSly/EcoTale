package org.sosly.ecotale.navigation;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.sosly.ecotale.blocks.RoostBlockEntity;

/**
 * Manages flow field generation requests using a thread pool.
 * All generation and validation runs off the main thread to prevent lag spikes.
 */
public class FlowFieldManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int POOL_SIZE = 2;

    private static FlowFieldManager instance;

    private final Map<FlowFieldCell, Set<WeakReference<RoostBlockEntity>>> roostsByStartCell;
    private ExecutorService workerPool;
    private ExecutorService priorityWorker;
    private MinecraftServer server;
    private volatile boolean running;
    private volatile boolean paused;

    private FlowFieldManager() {
        this.roostsByStartCell = new ConcurrentHashMap<>();
    }

    public static FlowFieldManager getInstance() {
        if (instance == null) {
            instance = new FlowFieldManager();
        }
        return instance;
    }

    /**
     * Starts the flow field worker pool. Called on server start.
     */
    public void start(MinecraftServer server) {
        if (running) {
            return;
        }

        this.server = server;
        this.running = true;
        this.workerPool = Executors.newFixedThreadPool(POOL_SIZE, new java.util.concurrent.ThreadFactory() {
            private int threadNum = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "EcoTale-FlowField-" + threadNum++);
                thread.setDaemon(true);
                return thread;
            }
        });

        this.priorityWorker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "EcoTale-FlowField-Priority");
            thread.setDaemon(true);
            return thread;
        });

        LOGGER.info("FlowField worker pool started with {} threads + priority", POOL_SIZE);
    }

    /**
     * Stops the flow field worker pool. Called on server stop.
     */
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

        server = null;
        LOGGER.info("FlowField worker pool stopped");
    }

    /**
     * Pauses normal generation/validation requests. Priority requests still run.
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
        LOGGER.info("FlowField generation {}", paused ? "paused" : "resumed");
    }

    public boolean isPaused() {
        return paused;
    }

    /**
     * Submits a generation request for the given roost.
     */
    public void requestGeneration(RoostBlockEntity roost) {
        if (!running) {
            return;
        }
        if (paused) {
            return;
        }
        Level level = roost.getLevel();
        if (level == null) {
            return;
        }

        BlockPos roostPos = roost.getBlockPos();
        FlowFieldRequest request = FlowFieldRequest.generation(
                roostPos,
                level,
                solution -> deliverResult(roost, solution)
        );
        workerPool.submit(() -> {
            try {
                processRequest(request);
            } catch (Exception e) {
                LOGGER.error("Error processing flow field generation at {}", roostPos, e);
            }
        });
    }

    /**
     * Submits a validation request for the given roost.
     */
    public void requestValidation(RoostBlockEntity roost, FlowFieldSolution solution) {
        if (!running) {
            return;
        }
        if (paused) {
            return;
        }
        if (solution == null) {
            return;
        }
        Level level = roost.getLevel();
        if (level == null) {
            return;
        }

        BlockPos roostPos = roost.getBlockPos();
        FlowFieldRequest request = FlowFieldRequest.validation(
                roostPos,
                level,
                solution,
                valid -> deliverValidationResult(roost, valid)
        );
        workerPool.submit(() -> {
            try {
                processRequest(request);
            } catch (Exception e) {
                LOGGER.error("Error processing flow field validation at {}", roostPos, e);
            }
        });
    }

    /**
     * Submits a priority generation request that bypasses the normal queue.
     */
    public void requestPriorityGeneration(RoostBlockEntity roost) {
        if (!running) {
            return;
        }
        Level level = roost.getLevel();
        if (level == null) {
            return;
        }

        BlockPos roostPos = roost.getBlockPos();
        FlowFieldRequest request = FlowFieldRequest.generation(
                roostPos,
                level,
                solution -> deliverResult(roost, solution)
        );
        priorityWorker.submit(() -> {
            try {
                processPriorityRequest(request);
            } catch (Exception e) {
                LOGGER.error("Error processing priority flow field generation at {}", roostPos, e);
            }
        });
    }

    private void processRequest(FlowFieldRequest request) {
        waitWhilePaused();
        if (!running) {
            return;
        }
        executeRequest(request);
    }

    private void processPriorityRequest(FlowFieldRequest request) {
        if (!running) {
            return;
        }
        executeRequest(request);
    }

    private void executeRequest(FlowFieldRequest request) {
        long startTime = System.nanoTime();

        if (request.type() == FlowFieldRequest.RequestType.GENERATE) {
            processGeneration(request, startTime);
        } else {
            processValidation(request, startTime);
        }
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

    private void processGeneration(FlowFieldRequest request, long startTime) {
        FlowFieldGenerator generator = new FlowFieldGenerator(request.roostPos(), request.level());
        FlowFieldSolution solution = generator.generate();

        long elapsed = System.nanoTime() - startTime;

        if (solution.isFailed()) {
            LOGGER.debug("FlowField generation FAILED at {}: {}ms",
                    request.roostPos(), elapsed / 1_000_000.0);
        } else {
            LOGGER.debug("FlowField generation at {}: {}ms, {} outward cells, {} inward cells, {} extension cells, exit at {}",
                    request.roostPos(),
                    elapsed / 1_000_000.0,
                    solution.getOutwardCellCount(),
                    solution.getInwardCellCount(),
                    generator.getExtensionCellCount(),
                    solution.getExitPoint());
        }

        scheduleCallback(request, solution);
    }

    private void processValidation(FlowFieldRequest request, long startTime) {
        FlowFieldSolution existing = request.existingSolution();
        boolean valid = existing.isValid(request.level());

        long elapsed = System.nanoTime() - startTime;
        LOGGER.debug("FlowField validation at {}: {}ms, valid={}",
                request.roostPos(), elapsed / 1_000_000.0, valid);

        FlowFieldSolution result = valid ? existing : null;
        scheduleCallback(request, result);
    }

    private void scheduleCallback(FlowFieldRequest request, FlowFieldSolution result) {
        if (server == null || !running) {
            return;
        }

        server.execute(() -> request.callback().accept(result));
    }

    private void deliverResult(RoostBlockEntity roost, FlowFieldSolution solution) {
        if (roost.isRemoved()) {
            return;
        }
        roost.onGenerationComplete(solution);

        if (solution != null && !solution.isFailed()) {
            registerRoost(roost, solution.getStartCell());
            broadcastSolution(roost, solution);
        }
    }

    private void broadcastSolution(RoostBlockEntity source, FlowFieldSolution solution) {
        FlowFieldCell startCell = solution.getStartCell();
        if (startCell == null) {
            return;
        }

        Set<WeakReference<RoostBlockEntity>> roosts = roostsByStartCell.get(startCell);
        if (roosts == null) {
            return;
        }

        Iterator<WeakReference<RoostBlockEntity>> iter = roosts.iterator();
        while (iter.hasNext()) {
            RoostBlockEntity target = iter.next().get();
            if (target == null) {
                iter.remove();
                continue;
            }
            if (target == source || target.isRemoved()) {
                continue;
            }
            target.offerSolution(solution);
        }
    }

    private void deliverValidationResult(RoostBlockEntity roost, Boolean valid) {
        if (roost.isRemoved()) {
            return;
        }
        roost.onValidationComplete(valid);
    }

    public void registerRoost(RoostBlockEntity roost, FlowFieldCell startCell) {
        if (startCell == null) {
            return;
        }
        roostsByStartCell.computeIfAbsent(startCell, k -> ConcurrentHashMap.newKeySet())
            .add(new WeakReference<>(roost));
    }

    public void unregisterRoost(RoostBlockEntity roost) {
        for (Set<WeakReference<RoostBlockEntity>> roosts : roostsByStartCell.values()) {
            roosts.removeIf(ref -> {
                RoostBlockEntity target = ref.get();
                return target == null || target == roost;
            });
        }
    }
}
