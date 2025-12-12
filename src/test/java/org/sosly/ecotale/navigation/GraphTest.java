package org.sosly.ecotale.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GraphTest {

    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void findContainingCellReturnsCorrectCellWhenPositionIsInside() {
        BlockPos hub1 = new BlockPos(0, 0, 0);
        BlockPos hub2 = new BlockPos(8, 8, 8);
        Cell cell1 = Cell.fromHubPosition(hub1);
        Cell cell2 = Cell.fromHubPosition(hub2);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub1)
            .addCell(cell1)
            .addCell(cell2)
            .build();

        assertEquals(cell1, graph.findContainingCell(new BlockPos(0, 0, 0)));
        assertEquals(cell1, graph.findContainingCell(new BlockPos(2, 2, 2)));
        assertEquals(cell2, graph.findContainingCell(new BlockPos(8, 8, 8)));
        assertEquals(cell2, graph.findContainingCell(new BlockPos(10, 10, 10)));
    }

    @Test
    void findContainingCellReturnsNullWhenPositionIsOutside() {
        BlockPos hub = new BlockPos(0, 0, 0);
        Cell cell = Cell.fromHubPosition(hub);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub)
            .addCell(cell)
            .build();

        assertNull(graph.findContainingCell(new BlockPos(10, 10, 10)));
        assertNull(graph.findContainingCell(new BlockPos(-10, -10, -10)));
    }

    @Test
    void findContainingCellHandlesBoundaryPositionsCorrectly() {
        BlockPos hub = new BlockPos(2, 2, 2);
        Cell cell = Cell.fromHubPosition(hub);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub)
            .addCell(cell)
            .build();

        assertEquals(cell, graph.findContainingCell(new BlockPos(0, 0, 0)));
        assertNull(graph.findContainingCell(new BlockPos(4, 0, 0)));
        assertNull(graph.findContainingCell(new BlockPos(0, 4, 0)));
        assertNull(graph.findContainingCell(new BlockPos(0, 0, 4)));
        assertNull(graph.findContainingCell(new BlockPos(4, 4, 4)));
    }

    @Test
    void getNextHopReturnsCorrectNextHopForValidCellAndDestination() {
        BlockPos hub1 = new BlockPos(0, 0, 0);
        BlockPos hub2 = new BlockPos(8, 8, 8);
        Cell cell1 = Cell.fromHubPosition(hub1);
        Cell cell2 = Cell.fromHubPosition(hub2);

        cell1.setPath(hub2, hub2);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub1)
            .addCell(cell1)
            .addCell(cell2)
            .build();

        assertEquals(hub2, graph.getNextHop(hub1, hub2));
    }

    @Test
    void getNextHopReturnsNullWhenAlreadyAtDestination() {
        BlockPos hub = new BlockPos(0, 0, 0);
        Cell cell = Cell.fromHubPosition(hub);

        cell.setPath(hub, null);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub)
            .addCell(cell)
            .build();

        assertNull(graph.getNextHop(hub, hub));
    }

    @Test
    void getNextHopReturnsNullWhenCellDoesNotExist() {
        BlockPos hub1 = new BlockPos(0, 0, 0);
        BlockPos hub2 = new BlockPos(8, 8, 8);
        BlockPos nonExistentHub = new BlockPos(100, 100, 100);
        Cell cell1 = Cell.fromHubPosition(hub1);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub1)
            .addCell(cell1)
            .build();

        assertNull(graph.getNextHop(nonExistentHub, hub2));
    }

    @Test
    void getNextHopReturnsNullWhenDestinationNotInPaths() {
        BlockPos hub1 = new BlockPos(0, 0, 0);
        BlockPos hub2 = new BlockPos(8, 8, 8);
        BlockPos unknownDestination = new BlockPos(100, 100, 100);
        Cell cell1 = Cell.fromHubPosition(hub1);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub1)
            .addCell(cell1)
            .build();

        assertNull(graph.getNextHop(hub1, unknownDestination));
    }

    @Test
    void allCellsHavePathEntriesForEveryDestination() {
        BlockPos hub1 = new BlockPos(0, 0, 0);
        BlockPos hub2 = new BlockPos(8, 8, 8);
        BlockPos hub3 = new BlockPos(16, 16, 16);
        BlockPos exit1 = new BlockPos(24, 24, 24);

        Cell cell1 = Cell.fromHubPosition(hub1);
        Cell cell2 = Cell.fromHubPosition(hub2);
        Cell cell3 = Cell.fromHubPosition(hub3);
        Cell exitCell = Cell.fromHubPosition(exit1);

        cell1.setPath(hub1, null);
        cell1.setPath(exit1, hub2);
        cell2.setPath(hub1, hub1);
        cell2.setPath(exit1, hub3);
        cell3.setPath(hub1, hub2);
        cell3.setPath(exit1, exit1);
        exitCell.setPath(hub1, hub3);
        exitCell.setPath(exit1, null);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub1)
            .addGraphExit(exit1)
            .addCell(cell1)
            .addCell(cell2)
            .addCell(cell3)
            .addCell(exitCell)
            .build();

        Set<BlockPos> expectedDestinations = graph.getDestinations();
        assertEquals(2, expectedDestinations.size());
        assertTrue(expectedDestinations.contains(hub1));
        assertTrue(expectedDestinations.contains(exit1));

        for (Cell cell : graph.getCells().values()) {
            for (BlockPos destination : expectedDestinations) {
                assertTrue(cell.getPaths().containsKey(destination),
                    "Cell at " + cell.getHub() + " missing path to " + destination);
            }
        }
    }

    @Test
    void followingPathsFromAnyCellReachesDestination() {
        BlockPos hub1 = new BlockPos(0, 0, 0);
        BlockPos hub2 = new BlockPos(8, 8, 8);
        BlockPos hub3 = new BlockPos(16, 16, 16);

        Cell cell1 = Cell.fromHubPosition(hub1);
        Cell cell2 = Cell.fromHubPosition(hub2);
        Cell cell3 = Cell.fromHubPosition(hub3);

        cell1.setPath(hub1, null);
        cell1.setPath(hub3, hub2);
        cell2.setPath(hub1, hub1);
        cell2.setPath(hub3, hub3);
        cell3.setPath(hub1, hub2);
        cell3.setPath(hub3, null);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub1)
            .addGraphExit(hub3)
            .addCell(cell1)
            .addCell(cell2)
            .addCell(cell3)
            .build();

        for (BlockPos startHub : graph.getCells().keySet()) {
            for (BlockPos destination : graph.getDestinations()) {
                BlockPos current = startHub;
                int steps = 0;

                while (!current.equals(destination) && steps < 100) {
                    BlockPos next = graph.getNextHop(current, destination);
                    assertNotNull(next, "Path from " + startHub + " to " + destination
                        + " broken at " + current);
                    current = next;
                    steps++;
                }

                assertEquals(destination, current,
                    "Path from " + startHub + " to " + destination + " did not reach destination");
            }
        }
    }

    @Test
    void pathLengthNeverExceedsCellCount() {
        BlockPos hub1 = new BlockPos(0, 0, 0);
        BlockPos hub2 = new BlockPos(8, 8, 8);
        BlockPos hub3 = new BlockPos(16, 16, 16);
        BlockPos hub4 = new BlockPos(24, 24, 24);

        Cell cell1 = Cell.fromHubPosition(hub1);
        Cell cell2 = Cell.fromHubPosition(hub2);
        Cell cell3 = Cell.fromHubPosition(hub3);
        Cell cell4 = Cell.fromHubPosition(hub4);

        cell1.setPath(hub1, null);
        cell1.setPath(hub4, hub2);
        cell2.setPath(hub1, hub1);
        cell2.setPath(hub4, hub3);
        cell3.setPath(hub1, hub2);
        cell3.setPath(hub4, hub4);
        cell4.setPath(hub1, hub3);
        cell4.setPath(hub4, null);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub1)
            .addGraphExit(hub4)
            .addCell(cell1)
            .addCell(cell2)
            .addCell(cell3)
            .addCell(cell4)
            .build();

        int maxCells = graph.getCells().size();

        for (BlockPos startHub : graph.getCells().keySet()) {
            for (BlockPos destination : graph.getDestinations()) {
                BlockPos current = startHub;
                int steps = 0;

                while (!current.equals(destination) && steps <= maxCells) {
                    BlockPos next = graph.getNextHop(current, destination);
                    if (next == null) {
                        break;
                    }
                    current = next;
                    steps++;
                }

                assertTrue(steps <= maxCells,
                    "Path from " + startHub + " to " + destination + " took " + steps
                    + " steps (max: " + maxCells + "). Possible cycle detected.");
            }
        }
    }

    @Test
    void builderThrowsWhenGraphStartNotSet() {
        Graph.Builder builder = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT);

        IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
        assertEquals("graphStart must be set before building", exception.getMessage());
    }

    @Test
    void builderDestinationsSetIncludesGraphStartAndExits() {
        BlockPos graphStart = new BlockPos(0, 0, 0);
        BlockPos exit1 = new BlockPos(8, 8, 8);
        BlockPos exit2 = new BlockPos(16, 16, 16);

        Cell startCell = Cell.fromHubPosition(graphStart);
        Cell exitCell1 = Cell.fromHubPosition(exit1);
        Cell exitCell2 = Cell.fromHubPosition(exit2);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(graphStart)
            .addGraphExit(exit1)
            .addGraphExit(exit2)
            .addCell(startCell)
            .addCell(exitCell1)
            .addCell(exitCell2)
            .build();

        Set<BlockPos> destinations = graph.getDestinations();
        assertEquals(3, destinations.size());
        assertTrue(destinations.contains(graphStart));
        assertTrue(destinations.contains(exit1));
        assertTrue(destinations.contains(exit2));
    }

    @Test
    void builderDestinationsSetOnlyIncludesGraphStartWhenNoExits() {
        BlockPos graphStart = new BlockPos(0, 0, 0);
        Cell startCell = Cell.fromHubPosition(graphStart);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(graphStart)
            .addCell(startCell)
            .build();

        Set<BlockPos> destinations = graph.getDestinations();
        assertEquals(1, destinations.size());
        assertTrue(destinations.contains(graphStart));
    }

    @Test
    void getCellReturnsCorrectCell() {
        BlockPos hub = new BlockPos(0, 0, 0);
        Cell cell = Cell.fromHubPosition(hub);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub)
            .addCell(cell)
            .build();

        assertEquals(cell, graph.getCell(hub));
    }

    @Test
    void getCellReturnsNullForNonExistentHub() {
        BlockPos hub = new BlockPos(0, 0, 0);
        Cell cell = Cell.fromHubPosition(hub);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub)
            .addCell(cell)
            .build();

        assertNull(graph.getCell(new BlockPos(100, 100, 100)));
    }

    @Test
    void getIdReturnsCorrectId() {
        BlockPos id = new BlockPos(10, 20, 30);
        BlockPos hub = new BlockPos(0, 0, 0);
        Cell cell = Cell.fromHubPosition(hub);

        Graph graph = Graph.builder(id, EntityType.BAT)
            .setGraphStart(hub)
            .addCell(cell)
            .build();

        assertEquals(id, graph.getId());
    }

    @Test
    void getEntityTypeReturnsCorrectType() {
        BlockPos hub = new BlockPos(0, 0, 0);
        Cell cell = Cell.fromHubPosition(hub);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub)
            .addCell(cell)
            .build();

        assertEquals(EntityType.BAT, graph.getEntityType());
    }

    @Test
    void getCellsReturnsUnmodifiableMap() {
        BlockPos hub1 = new BlockPos(0, 0, 0);
        BlockPos hub2 = new BlockPos(8, 8, 8);
        Cell cell1 = Cell.fromHubPosition(hub1);
        Cell cell2 = Cell.fromHubPosition(hub2);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(hub1)
            .addCell(cell1)
            .addCell(cell2)
            .build();

        assertEquals(2, graph.getCells().size());
        assertEquals(cell1, graph.getCells().get(hub1));
        assertEquals(cell2, graph.getCells().get(hub2));
    }

    @Test
    void getGraphStartReturnsCorrectPosition() {
        BlockPos graphStart = new BlockPos(5, 10, 15);
        Cell cell = Cell.fromHubPosition(graphStart);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(graphStart)
            .addCell(cell)
            .build();

        assertEquals(graphStart, graph.getGraphStart());
    }

    @Test
    void getGraphExitsReturnsUnmodifiableSet() {
        BlockPos graphStart = new BlockPos(0, 0, 0);
        BlockPos exit1 = new BlockPos(8, 8, 8);
        BlockPos exit2 = new BlockPos(16, 16, 16);

        Cell startCell = Cell.fromHubPosition(graphStart);
        Cell exitCell1 = Cell.fromHubPosition(exit1);
        Cell exitCell2 = Cell.fromHubPosition(exit2);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(graphStart)
            .addGraphExit(exit1)
            .addGraphExit(exit2)
            .addCell(startCell)
            .addCell(exitCell1)
            .addCell(exitCell2)
            .build();

        Set<BlockPos> exits = graph.getGraphExits();
        assertEquals(2, exits.size());
        assertTrue(exits.contains(exit1));
        assertTrue(exits.contains(exit2));
    }

    @Test
    void getDestinationsReturnsUnmodifiableSet() {
        BlockPos graphStart = new BlockPos(0, 0, 0);
        BlockPos exit = new BlockPos(8, 8, 8);

        Cell startCell = Cell.fromHubPosition(graphStart);
        Cell exitCell = Cell.fromHubPosition(exit);

        Graph graph = Graph.builder(new BlockPos(0, 0, 0), EntityType.BAT)
            .setGraphStart(graphStart)
            .addGraphExit(exit)
            .addCell(startCell)
            .addCell(exitCell)
            .build();

        Set<BlockPos> destinations = graph.getDestinations();
        assertEquals(2, destinations.size());

        Set<BlockPos> copy = new HashSet<>(destinations);
        assertEquals(destinations, copy);
    }
}
