---
level: 5
parent: "[[Navigation Graph Specification]]"
status: draft
---

This build plan brings the navigation graph implementation into alignment with the Navigation Graph Specification. The core system exists and works, but deviates from the spec in several structural ways that affect performance and capability.

## Key Decisions

| Decision | Value | Rationale |
|----------|-------|-----------|
| Cell map key | `floor(coord/4)*4` (grid-aligned BlockPos) | O(1) lookup; spec requirement |
| Hub storage | Implicit (derived from path nextHop values) | Supports multiple hubs per cell for different routes |
| Path algorithm | Dual-LOS hub discovery + alternative paths | Handles complex cave geometry |
| Edge coloring | Hue-wheel (keep current) | More informative than spec's blue/orange; update spec to match |
| Serialization | Omit boundsMax | Computed as boundsMin + (3,3,3); reduces storage |

## Architecture Overview

The Navigation Graph provides precomputed paths for flying entities through caves. Key changes from current implementation:

1. **Cell keying**: Currently keys by hub position. Change to grid-aligned key (`floor(coord/4)*4`) for O(1) position-to-cell lookup.

2. **Multi-hub model**: Currently each cell stores one explicit hub. Change to implicit hubs derived from path entries. A cell may have multiple hubs when different routes require different waypoints.

3. **Path building**: Currently uses parent-chain from BFS. Change to spec's dual-LOS algorithm: for each route A-B-C, find hub in B with line-of-sight to both A-B boundary and B-C boundary. If no hub works, search alternative paths through adjacency graph.

## File Structure

```
src/main/java/org/sosly/ecotale/navigation/
├── Cell.java           # Remove hub field; bounds only
├── Graph.java          # Change cells map key; update serialization
├── GraphGenerator.java # Implement dual-LOS hub discovery + alternative paths
└── Validators.java     # No changes needed

src/main/java/org/sosly/ecotale/network/
└── GraphDebugPacket.java  # Update serialization (omit boundsMax)

docs/mobs/bats/
└── Navigation Graph Specification.md  # Update edge coloring description
```

## Phased Build Order

### Phase 1: Cell Keying Refactor

**Goal:** Change cells map from hub-keyed to grid-keyed for O(1) lookup.

**Files:** `Cell.java`, `Graph.java`

**Details:**

- Remove `hub` field from Cell (keep `bounds`, `paths`)
- Add `getGridKey()` method: returns BlockPos at bounds min corner
- Change `Graph.cells` from `Map<BlockPos, Cell>` (hub) to `Map<BlockPos, Cell>` (gridKey)
- Remove `Graph.getCell(BlockPos hub)`
- Add `Graph.getCellAt(BlockPos worldPos)` that computes grid key directly
- Update `Graph.findContainingCell()` to use O(1) lookup via grid key computation

**Validation:**

- Unit test: `getCellAt()` returns correct cell for any position within cell bounds
- Unit test: Positions outside graph return null
- Existing graph generation still produces valid graphs

### Phase 2: Multi-Hub Model

**Goal:** Remove explicit hub storage; hubs become implicit via path entries.

**Files:** `Cell.java`, `Graph.java`, `GraphGenerator.java`

**Details:**

- Cell stores only `bounds` and `paths` (destination to nextHop)
- Hubs are the nextHop positions that fall within a cell's bounds
- A cell can have multiple hubs (different routes use different waypoints)
- Add `Cell.getHubs()` method that collects unique nextHop values from paths that point INTO this cell
- GraphGenerator builds paths with explicit nextHop positions (hubs discovered during path building)

**Key insight:** The `paths` map already stores nextHop positions. The hub(s) for a cell are the set of nextHop values from NEIGHBORING cells that point into this cell's bounds.

**Validation:**

- Unit test: `getHubs()` returns all unique waypoints used by routes through the cell
- Visual inspection: Debug renderer shows multiple purple markers when cell has multiple hubs
- Graph generation completes for test cave structures

### Phase 3: Path Building Algorithm

**Goal:** Implement spec's dual-LOS hub discovery with alternative path search.

**Files:** `GraphGenerator.java`

**Details:**

The spec's path building algorithm (Phase 5 in spec):

1. For each destination, BFS from destination cell outward
2. For cell C visited, examine adjacent cell A
3. Let Q = boundary crossing from A to C
4. Let R = boundary crossing from C toward next cell on path to destination
5. Find hub in C with LOS to both Q and R
6. If found: record A's path entry as (destination to hub in C)
7. If not found: search alternative paths through adjacency graph

Pseudocode for hub discovery:

```
findHub(cell, entryBoundary Q, exitBoundary R):
    check center block first
    expand in shells (distance 1, 2, ...)
    for each position in shell (Y-ascending, Z-ascending, X-ascending):
        if isAir(pos) AND hasLOS(pos, Q) AND hasLOS(pos, R):
            return pos
    return null  // route fails
```

Alternative path search when direct route fails:

```
findAlternativePath(cellA, destination, adjacency):
    BFS from A looking for any cell with valid path entry for destination
    for each candidate route found:
        attempt hub discovery for A to first cell on alternative
        if succeeds: use this route
    if all fail: A has no path entry for this destination
```

**Validation:**

- Unit test: Hub discovery finds valid hubs with dual-LOS
- Unit test: Alternative paths work when direct routes fail
- Integration test: Complex cave generates complete paths
- Visual inspection: Path highlighting shows valid routes

### Phase 4: Serialization Cleanup

**Goal:** Remove redundant boundsMax from serialization.

**Files:** `Graph.java`, `GraphDebugPacket.java`

**Details:**

- `Graph.save()`: Don't write boundsMax, compute as boundsMin + (3,3,3)
- `Graph.load()`: Compute boundsMax from boundsMin
- `GraphDebugPacket.encode()`: Don't write boundsMax
- `GraphDebugPacket.decode()`: Compute boundsMax from boundsMin
- Update `CellData` class accordingly

**Validation:**

- Round-trip test: Save graph, load graph, verify identical structure
- Existing saved worlds still load (backwards compatibility check)

### Phase 5: Spec Update

**Goal:** Update specification to document current edge coloring approach.

**Files:** `docs/mobs/bats/Navigation Graph Specification.md`

**Details:**

Update Debug Visualization section:

- Change "Blue lines - paths toward GraphStart" to hue-wheel description
- Document that each exit gets a unique hue
- Document that overlapping edges blend colors
- Explain rationale (better differentiation with multiple exits)

**Validation:**

- Spec accurately describes implementation behavior

## Testing Milestones

| After Phase | Testable Behavior |
|-------------|-------------------|
| 1 | O(1) cell lookup via `getCellAt(worldPos)` |
| 2 | Cells report multiple hubs when routes diverge |
| 3 | Complex caves have complete path coverage; alternative paths work |
| 4 | Graphs save/load correctly with smaller serialization |
| 5 | Spec matches implementation |

## Future Work

- **Ground-based navigation**: Spec explicitly excludes non-flying entities. Future work to support ground pathfinding.
- **Performance optimization**: If block change subscription becomes expensive with many roosts, spatial indexing may be needed.
- **Chunk boundary handling**: Current implementation works with loaded chunks only. Cross-chunk navigation deferred.