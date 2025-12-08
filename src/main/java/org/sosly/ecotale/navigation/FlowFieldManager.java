package org.sosly.ecotale.navigation;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.sosly.ecotale.blocks.RoostBlockEntity;

/**
 * Manages flow field generation requests on a dedicated worker thread.
 * All generation and validation runs off the main thread to prevent lag spikes.
 */
public class FlowFieldManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int QUEUE_POLL_TIMEOUT_SECONDS = 1;

    private static FlowFieldManager instance;

    private final BlockingQueue<FlowFieldRequest> requestQueue;
    private ExecutorService workerThread;
    private MinecraftServer server;
    private volatile boolean running;

    private FlowFieldManager() {
        this.requestQueue = new LinkedBlockingQueue<>();
    }

    public static FlowFieldManager getInstance() {
        if (instance == null) {
            instance = new FlowFieldManager();
        }
        return instance;
    }

    /**
     * Starts the flow field worker thread. Called on server start.
     */
    public void start(MinecraftServer server) {
        if (running) {
            return;
        }

        this.server = server;
        this.running = true;
        this.workerThread = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "EcoTale-FlowField-Worker");
            thread.setDaemon(true);
            return thread;
        });

        workerThread.submit(this::workerLoop);
        LOGGER.info("FlowField worker thread started");
    }

    /**
     * Stops the flow field worker thread. Called on server stop.
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        requestQueue.clear();

        if (workerThread != null) {
            workerThread.shutdown();
            try {
                if (!workerThread.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerThread.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerThread.shutdownNow();
                Thread.currentThread().interrupt();
            }
            workerThread = null;
        }

        server = null;
        LOGGER.info("FlowField worker thread stopped");
    }

    /**
     * Queues a generation request for the given roost.
     */
    public void requestGeneration(RoostBlockEntity roost) {
        Level level = roost.getLevel();
        if (level == null || !running) {
            return;
        }

        BlockPos roostPos = roost.getBlockPos();
        FlowFieldRequest request = FlowFieldRequest.generation(
                roostPos,
                level,
                solution -> deliverResult(roost, solution)
        );
        requestQueue.offer(request);
    }

    /**
     * Queues a validation request for the given roost.
     */
    public void requestValidation(RoostBlockEntity roost, FlowFieldSolution solution) {
        Level level = roost.getLevel();
        if (level == null || !running || solution == null) {
            return;
        }

        BlockPos roostPos = roost.getBlockPos();
        FlowFieldRequest request = FlowFieldRequest.validation(
                roostPos,
                level,
                solution,
                valid -> deliverValidationResult(roost, valid)
        );
        requestQueue.offer(request);
    }

    private void workerLoop() {
        while (running) {
            try {
                FlowFieldRequest request = requestQueue.poll(QUEUE_POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (request == null) {
                    continue;
                }

                processRequest(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Error in FlowField worker", e);
            }
        }
    }

    private void processRequest(FlowFieldRequest request) {
        long startTime = System.nanoTime();

        if (request.type() == FlowFieldRequest.RequestType.GENERATE) {
            processGeneration(request, startTime);
        } else {
            processValidation(request, startTime);
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
            LOGGER.debug("FlowField generation at {}: {}ms, {} outward cells, {} inward cells, exit at {}",
                    request.roostPos(),
                    elapsed / 1_000_000.0,
                    solution.getOutwardCellCount(),
                    solution.getInwardCellCount(),
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
    }

    private void deliverValidationResult(RoostBlockEntity roost, Boolean valid) {
        if (roost.isRemoved()) {
            return;
        }
        roost.onValidationComplete(valid);
    }
}
