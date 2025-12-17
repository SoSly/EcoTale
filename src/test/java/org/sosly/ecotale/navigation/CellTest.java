package org.sosly.ecotale.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class CellTest {

    @Test
    void fromWorldPositionComputesBoundsForPositiveCoordinates() {
        BlockPos pos = new BlockPos(5, 10, 15);
        Cell cell = Cell.fromWorldPosition(pos);

        AABB bounds = cell.getBounds();
        assertEquals(4.0, bounds.minX);
        assertEquals(8.0, bounds.minY);
        assertEquals(12.0, bounds.minZ);
        assertEquals(8.0, bounds.maxX);
        assertEquals(12.0, bounds.maxY);
        assertEquals(16.0, bounds.maxZ);
    }

    @Test
    void fromWorldPositionUsesFloorDivForNegativeCoordinates() {
        BlockPos pos = new BlockPos(-1, 0, 0);
        Cell cell = Cell.fromWorldPosition(pos);

        AABB bounds = cell.getBounds();
        assertEquals(-4.0, bounds.minX);
        assertEquals(0.0, bounds.minY);
        assertEquals(0.0, bounds.minZ);
        assertEquals(0.0, bounds.maxX);
        assertEquals(4.0, bounds.maxY);
        assertEquals(4.0, bounds.maxZ);
    }

    @Test
    void fromWorldPositionWorksForPositionAtCellCenter() {
        BlockPos pos = new BlockPos(2, 2, 2);
        Cell cell = Cell.fromWorldPosition(pos);

        AABB bounds = cell.getBounds();
        assertEquals(0.0, bounds.minX);
        assertEquals(0.0, bounds.minY);
        assertEquals(0.0, bounds.minZ);
        assertEquals(4.0, bounds.maxX);
        assertEquals(4.0, bounds.maxY);
        assertEquals(4.0, bounds.maxZ);
    }

    @Test
    void fromWorldPositionWorksForPositionAtCellEdge() {
        BlockPos pos = new BlockPos(0, 0, 0);
        Cell cell = Cell.fromWorldPosition(pos);

        AABB bounds = cell.getBounds();
        assertEquals(0.0, bounds.minX);
        assertEquals(0.0, bounds.minY);
        assertEquals(0.0, bounds.minZ);
        assertEquals(4.0, bounds.maxX);
        assertEquals(4.0, bounds.maxY);
        assertEquals(4.0, bounds.maxZ);
    }

    @Test
    void fromWorldPositionWorksForNegativePositionAtCellEdge() {
        BlockPos pos = new BlockPos(-4, -8, -12);
        Cell cell = Cell.fromWorldPosition(pos);

        AABB bounds = cell.getBounds();
        assertEquals(-4.0, bounds.minX);
        assertEquals(-8.0, bounds.minY);
        assertEquals(-12.0, bounds.minZ);
        assertEquals(0.0, bounds.maxX);
        assertEquals(-4.0, bounds.maxY);
        assertEquals(-8.0, bounds.maxZ);
    }

    @Test
    void containsReturnsTrueForPositionInsideCell() {
        Cell cell = Cell.fromWorldPosition(new BlockPos(2, 2, 2));

        assertTrue(cell.contains(new BlockPos(0, 0, 0)));
        assertTrue(cell.contains(new BlockPos(2, 2, 2)));
        assertTrue(cell.contains(new BlockPos(3, 3, 3)));
    }

    @Test
    void containsReturnsFalseForPositionOutsideCell() {
        Cell cell = Cell.fromWorldPosition(new BlockPos(2, 2, 2));

        assertFalse(cell.contains(new BlockPos(4, 0, 0)));
        assertFalse(cell.contains(new BlockPos(0, 4, 0)));
        assertFalse(cell.contains(new BlockPos(0, 0, 4)));
        assertFalse(cell.contains(new BlockPos(-1, 2, 2)));
        assertFalse(cell.contains(new BlockPos(5, 5, 5)));
    }

    @Test
    void containsMinCoordinatesAreInclusive() {
        Cell cell = Cell.fromWorldPosition(new BlockPos(2, 2, 2));

        assertTrue(cell.contains(new BlockPos(0, 0, 0)));
        assertTrue(cell.contains(new BlockPos(0, 1, 1)));
        assertTrue(cell.contains(new BlockPos(1, 0, 1)));
        assertTrue(cell.contains(new BlockPos(1, 1, 0)));
    }

    @Test
    void containsMaxCoordinatesAreExclusive() {
        Cell cell = Cell.fromWorldPosition(new BlockPos(2, 2, 2));

        assertFalse(cell.contains(new BlockPos(4, 0, 0)));
        assertFalse(cell.contains(new BlockPos(0, 4, 0)));
        assertFalse(cell.contains(new BlockPos(0, 0, 4)));
        assertFalse(cell.contains(new BlockPos(4, 4, 4)));
    }

    @Test
    void containsWorksForNegativeCoordinates() {
        Cell cell = Cell.fromWorldPosition(new BlockPos(-2, -2, -2));

        assertTrue(cell.contains(new BlockPos(-4, -4, -4)));
        assertTrue(cell.contains(new BlockPos(-2, -2, -2)));
        assertTrue(cell.contains(new BlockPos(-1, -1, -1)));
        assertFalse(cell.contains(new BlockPos(0, -2, -2)));
        assertFalse(cell.contains(new BlockPos(-5, -2, -2)));
    }

    @Test
    void setPathStoresDestinationAndNextHop() {
        Cell cell = Cell.fromWorldPosition(new BlockPos(0, 0, 0));
        BlockPos destination = new BlockPos(10, 10, 10);
        BlockPos nextHop = new BlockPos(4, 4, 4);

        cell.setPath(destination, nextHop);

        assertEquals(nextHop, cell.getPaths().get(destination));
    }

    @Test
    void setPathSupportsNullNextHopForDestinationCell() {
        Cell cell = Cell.fromWorldPosition(new BlockPos(0, 0, 0));
        BlockPos destination = new BlockPos(0, 0, 0);

        cell.setPath(destination, null);

        assertTrue(cell.getPaths().containsKey(destination));
        assertNull(cell.getPaths().get(destination));
    }

    @Test
    void getPathsReturnsUnmodifiableMap() {
        Cell cell = Cell.fromWorldPosition(new BlockPos(0, 0, 0));
        BlockPos destination = new BlockPos(10, 10, 10);
        BlockPos nextHop = new BlockPos(4, 4, 4);

        cell.setPath(destination, nextHop);

        assertEquals(1, cell.getPaths().size());
        assertEquals(nextHop, cell.getPaths().get(destination));
    }

    @Test
    void getBoundsReturnsCorrectAABB() {
        BlockPos pos = new BlockPos(5, 10, 15);
        Cell cell = Cell.fromWorldPosition(pos);

        AABB bounds = cell.getBounds();
        assertNotNull(bounds);
        assertEquals(4.0, bounds.minX);
        assertEquals(8.0, bounds.minY);
        assertEquals(12.0, bounds.minZ);
        assertEquals(8.0, bounds.maxX);
        assertEquals(12.0, bounds.maxY);
        assertEquals(16.0, bounds.maxZ);
    }

    @Test
    void getGridKeyReturnsCorrectPosition() {
        BlockPos pos = new BlockPos(5, 10, 15);
        Cell cell = Cell.fromWorldPosition(pos);

        assertEquals(new BlockPos(4, 8, 12), cell.getGridKey());
    }
}
