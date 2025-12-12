package org.sosly.ecotale.navigation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.blocks.RoostBlockEntity;
import org.sosly.ecotale.entities.EcoTaleBat;
import org.sosly.ecotale.entities.EntityRegistry;
import org.sosly.ecotale.entities.ai.AIConstants;
import org.sosly.ecotale.utils.TestUtils;

@PrefixGameTestTemplate(false)
@GameTestHolder(EcoTale.MOD_ID)
public class GraphGeneratorGameTest {

    @GameTest(template = "nav_test_cave", timeoutTicks = 100)
    public void graphGenerationProducesValidGraph(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos roostPos = TestUtils.findRoostInStructure(helper);
        if (roostPos == null) {
            helper.fail("No roost block found in structure");
            return;
        }

        BlockPos absoluteRoost = helper.absolutePos(roostPos);

        GraphGenerator generator = new GraphGenerator(absoluteRoost, level, EntityType.BAT);
        Graph graph = generator.generate();

        if (graph == null) {
            helper.fail("Graph generation returned null");
            return;
        }

        helper.succeedWhen(() -> {
            verifyGenerationCompleted(graph);
            verifyAllCellsHavePathEntries(graph);
            verifyPathsTerminate(graph);
            verifyNoPathCycles(graph);
            verifyDestinationsIncludeStartAndExits(graph);
            verifyHubsWithinBounds(graph);
        });
    }

    private void verifyGenerationCompleted(Graph graph) {
        if (graph == null) {
            throw new GameTestAssertException("Graph is null - generation did not complete");
        }

        if (graph.getCells().isEmpty()) {
            throw new GameTestAssertException("Graph has no cells - generation incomplete");
        }
    }

    private void verifyAllCellsHavePathEntries(Graph graph) {
        Set<BlockPos> destinations = graph.getDestinations();
        Map<BlockPos, Cell> cells = graph.getCells();

        for (Cell cell : cells.values()) {
            Map<BlockPos, BlockPos> paths = cell.getPaths();

            for (BlockPos destination : destinations) {
                if (!paths.containsKey(destination)) {
                    throw new GameTestAssertException(
                        "Cell at " + cell.getHub() + " is missing path entry for destination " + destination
                    );
                }
            }
        }
    }

    private void verifyPathsTerminate(Graph graph) {
        Map<BlockPos, Cell> cells = graph.getCells();
        Set<BlockPos> destinations = graph.getDestinations();

        for (Cell startCell : cells.values()) {
            for (BlockPos destination : destinations) {
                BlockPos current = startCell.getHub();
                Set<BlockPos> visited = new HashSet<>();

                while (current != null && !current.equals(destination)) {
                    if (visited.contains(current)) {
                        throw new GameTestAssertException(
                            "Path from " + startCell.getHub() + " to " + destination
                                + " contains a cycle at " + current
                        );
                    }

                    visited.add(current);
                    Cell currentCell = cells.get(current);
                    if (currentCell == null) {
                        throw new GameTestAssertException(
                            "Path from " + startCell.getHub() + " to " + destination
                                + " references non-existent cell " + current
                        );
                    }

                    BlockPos nextHop = currentCell.getPaths().get(destination);
                    if (nextHop == null) {
                        if (!current.equals(destination)) {
                            throw new GameTestAssertException(
                                "Path from " + startCell.getHub() + " to " + destination
                                    + " terminates prematurely at " + current
                            );
                        }
                        break;
                    }

                    current = nextHop;
                }

                if (!current.equals(destination)) {
                    throw new GameTestAssertException(
                        "Path from " + startCell.getHub() + " to " + destination
                            + " does not reach destination (ended at " + current + ")"
                    );
                }
            }
        }
    }

    private void verifyNoPathCycles(Graph graph) {
        Map<BlockPos, Cell> cells = graph.getCells();
        Set<BlockPos> destinations = graph.getDestinations();
        int maxPathLength = cells.size();

        for (Cell startCell : cells.values()) {
            for (BlockPos destination : destinations) {
                BlockPos current = startCell.getHub();
                int pathLength = 0;

                while (current != null && !current.equals(destination)) {
                    pathLength++;
                    if (pathLength > maxPathLength) {
                        throw new GameTestAssertException(
                            "Path from " + startCell.getHub() + " to " + destination
                                + " exceeds maximum length of " + maxPathLength + " (likely a cycle)"
                        );
                    }

                    Cell currentCell = cells.get(current);
                    BlockPos nextHop = currentCell.getPaths().get(destination);
                    if (nextHop == null) {
                        break;
                    }

                    current = nextHop;
                }
            }
        }
    }

    private void verifyDestinationsIncludeStartAndExits(Graph graph) {
        Set<BlockPos> destinations = graph.getDestinations();
        BlockPos graphStart = graph.getGraphStart();
        Set<BlockPos> graphExits = graph.getGraphExits();

        if (!destinations.contains(graphStart)) {
            throw new GameTestAssertException(
                "Destinations do not include graphStart " + graphStart
            );
        }

        for (BlockPos exit : graphExits) {
            if (!destinations.contains(exit)) {
                throw new GameTestAssertException(
                    "Destinations do not include exit " + exit
                );
            }
        }
    }

    private void verifyHubsWithinBounds(Graph graph) {
        Map<BlockPos, Cell> cells = graph.getCells();

        for (Cell cell : cells.values()) {
            BlockPos hub = cell.getHub();

            if (!cell.contains(hub)) {
                throw new GameTestAssertException(
                    "Hub " + hub + " is not within cell bounds " + cell.getBounds()
                );
            }
        }
    }

    @GameTest(template = "nav_test_cave", timeoutTicks = 600)
    public void batNavigatesToRoostViaGraph(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos roostPos = TestUtils.findRoostInStructure(helper);
        if (roostPos == null) {
            helper.fail("No roost block found in structure");
            return;
        }

        BlockPos absoluteRoost = helper.absolutePos(roostPos);
        BlockEntity blockEntity = level.getBlockEntity(absoluteRoost);
        if (!(blockEntity instanceof RoostBlockEntity roost)) {
            helper.fail("Roost block entity not found at " + absoluteRoost);
            return;
        }

        helper.runAfterDelay(200, () -> {
            Graph graph = roost.getGraph();
            if (graph == null) {
                helper.fail("Graph not generated after delay");
                return;
            }

            if (graph.getGraphExits().isEmpty()) {
                helper.fail("Graph has no exits");
                return;
            }

            BlockPos exitPos = graph.getGraphExits().iterator().next();

            EcoTaleBat bat = EntityRegistry.BAT.get().create(level);
            if (bat == null) {
                helper.fail("Failed to create bat");
                return;
            }

            bat.moveTo(exitPos.getX() + 0.5, exitPos.getY() + 0.5, exitPos.getZ() + 0.5, 0, 0);
            bat.setHome(GlobalPos.of(level.dimension(), absoluteRoost));
            bat.setResting(false);
            level.addFreshEntity(bat);

            helper.succeedWhen(() -> {
                BlockPos hangPos = absoluteRoost.below();
                if (!bat.blockPosition().closerThan(hangPos, AIConstants.ROOST_APPROACH_DISTANCE)) {
                    throw new GameTestAssertException(
                        "Bat has not reached roost (distance: "
                            + bat.blockPosition().distSqr(hangPos) + ")"
                    );
                }
            });
        });
    }

    @GameTest(template = "nav_test_cave", timeoutTicks = 600)
    public void batExitsCaveViaGraph(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos roostPos = TestUtils.findRoostInStructure(helper);
        if (roostPos == null) {
            helper.fail("No roost block found in structure");
            return;
        }

        BlockPos absoluteRoost = helper.absolutePos(roostPos);
        BlockEntity blockEntity = level.getBlockEntity(absoluteRoost);
        if (!(blockEntity instanceof RoostBlockEntity roost)) {
            helper.fail("Roost block entity not found at " + absoluteRoost);
            return;
        }

        helper.runAfterDelay(200, () -> {
            Graph graph = roost.getGraph();
            if (graph == null) {
                helper.fail("Graph not generated after delay");
                return;
            }

            BlockPos graphStart = graph.getGraphStart();

            EcoTaleBat bat = EntityRegistry.BAT.get().create(level);
            if (bat == null) {
                helper.fail("Failed to create bat");
                return;
            }

            bat.moveTo(graphStart.getX() + 0.5, graphStart.getY() + 0.5, graphStart.getZ() + 0.5, 0, 0);
            GlobalPos roostGlobalPos = GlobalPos.of(level.dimension(), absoluteRoost);
            bat.setHome(roostGlobalPos);
            bat.setRoost(roostGlobalPos);
            bat.setResting(false);
            level.addFreshEntity(bat);

            helper.succeedWhen(() -> {
                if (!level.canSeeSky(bat.blockPosition())) {
                    throw new GameTestAssertException("Bat has not exited cave (cannot see sky)");
                }
            });
        });
    }
}
