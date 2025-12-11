---
level: 4
parent: "[[AI Behavior Architecture]]"
status: review
---

The Flow Field Navigation subsystem generates and caches precomputed directional data that guides bats through cave systems. It serves the AI Behavior Architecture by providing the navigation layer that enables bats to exit caves at dusk and return at dawn.

This specification covers the flow field data structures, generation algorithm, caching mechanism, and query interface. It does not cover how bats use the flow field—that belongs to the Bat AI specification. The boundary is: Flow Field Navigation answers "which direction should I go from here?" and Bat AI decides when to ask and what to do with the answer.

## Data Structures

### Flow Field Cell

A coarse grid cell representing a region of the world.

**What it represents:** A cubic region used for flow field navigation. Cells provide the coordinate system for flow field data.

**Shape:**

- Three integer coordinates (x, y, z) derived from world position.
- Resolution is fixed at 4 blocks per cell. World coordinates divide by 4 (floored) to get cell coordinates.

**Invariants:**

- Cell coordinates are deterministic for any world position.
- Two world positions in the same 4×4×4 region map to the same cell.

### Flow Field Solution

A complete navigation solution for a roost.

**What it represents:** Everything a roost needs to support bat navigation between the roost and a cave exit.

**Shape:**

- **Outward field** — Maps cells to direction vectors pointing toward the exit.
- **Inward field** — Maps cells to direction vectors pointing toward the roost.
- **Hub cache** — Maps cells to specific navigable positions within each cell. These serve as waypoints for precise navigation.
- **Exit point** — The position where bats emerge from the cave, elevated above the cave opening for clear outdoor flight.
- **Roost position** — The roost this solution was generated for.
- **Start cell** — The cell where navigation begins (at or adjacent to the roost).
- **Failure flag** — Indicates whether generation failed.

**Invariants:**

- Both directional fields contain the same set of cells.
- Outward and inward vectors for the same cell are opposite directions.
- Every cell in the directional fields has a corresponding hub in the cache.
- The inward field extends beyond the cave exit into open air, so returning bats can pick up the flow field before entering the cave.
- A failed solution contains no navigation data.

**Persistence:** Solutions persist with their roost via NBT serialization. When a roost saves, its solution saves. When a roost loads, its solution loads. Solutions should not require regeneration merely because the world was reloaded.

**Serialization format:**

```
FlowFieldSolution {
    failed: boolean
    roost: long                        // packed BlockPos
    exit: long                         // packed BlockPos, omitted if failed
    startCellX, startCellY, startCellZ: int  // omitted if failed
    outward: list<VectorEntry>         // omitted if failed
    inward: list<VectorEntry>          // omitted if failed
    hubs: list<HubEntry>               // omitted if failed
}

VectorEntry {
    cellX, cellY, cellZ: int
    dirX, dirY, dirZ: double
}

HubEntry {
    cellX, cellY, cellZ: int
    pos: long                          // packed BlockPos
}
```

On deserialization failure, treat as missing solution and regenerate.

### Navigation Hub

Each cell in a flow field needs a specific position that bats can navigate through—not just "somewhere in this cell" but a concrete waypoint.

**What it represents:** An air block within a cell that has line-of-sight to adjacent cells' hubs, enabling smooth flight paths.

**Invariants:**

- A hub is always an air block.
- A hub is reachable from at least one adjacent cell's hub via unobstructed line-of-sight.
- The hub for the start cell is reachable from the roost position.

## Algorithm Description

### Flow Field Generation

Generation finds a path from the roost to a cave exit, then builds directional data along that path.

**Goal:** Given a roost position, produce a flow field solution that guides bats out of the cave and back.

**Approach:** BFS flood-fill from the roost, expanding through navigable cells until finding one with sky access. Then trace the path back and build direction vectors.

**Phase 1: Find a navigable starting point**

The roost block may be placed against a ceiling or wall. The algorithm must find a nearby cell where navigation can begin.

1. Check if the roost's own cell contains a reachable hub.
2. If not, check adjacent cells.
3. If no navigable starting point exists, generation fails.

**Phase 2: Search for an exit**

Expand outward from the start cell using breadth-first search:

1. For each cell, check if it contains an exit (an air block with sky access).
2. If not, identify passable neighbors and continue searching.
3. Two cells are passable if there exists an unobstructed path between their hubs, crossing the shared boundary through air.
4. Stop when an exit is found or the search bounds are exceeded.

**Search bounds:** Generation searches up to 128 blocks from the roost. Caves with no exit within this range fail generation.

**Multiple exits:** BFS naturally finds the nearest exit (by cell distance). If a cave has multiple openings, the closest one is used. This is intentional—bats take the shortest path out.

**Phase 3: Build the path**

Once an exit is found:

1. Trace backward from the exit to the start, recording each cell.
2. For each cell on the path, compute direction vectors (inward points toward roost, outward points toward exit).
3. Record the hub position for each cell.

**Phase 4: Extend outside the cave**

The inward field must extend beyond the cave mouth so returning bats can pick up navigation before entering:

1. From the exit cell, extend upward and outward (away from the cave).
2. Each extension cell's inward vector points toward the previous cell.
3. Continue until reaching a cell that is fully outside.
4. The final extension cell becomes the exit point—bats emerge here and return here.

**Termination condition:** A cell is "fully outside" when:
- Every block in the cell (all 64) is air.
- Every column in the cell (all 16) has sky access—meaning nothing is above that column.

This ensures bats have unobstructed space for transitioning between cave navigation and outdoor flight.

### Hub Discovery

Each cell needs a hub—a specific air block that serves as the navigation waypoint for that cell.

**Goal:** Given a cell, find an air block within it that has line-of-sight to the entry point (the position from which the cell was reached during BFS).

**Discovery procedure:**

1. Check the cell's center block first. If it is air and has line-of-sight to the entry point, use it as the hub.
2. If the center fails, search in expanding cubic shells around the center:
   - Shell 1: all blocks at distance 1 from center (up to 26 positions)
   - Shell 2: all blocks at distance 2 from center
   - Continue expanding until the entire cell has been checked (all 64 blocks)
3. For each position in a shell, check:
   - The position is within cell bounds.
   - The position is air.
   - The position has line-of-sight to the entry point (raycast passes through only air).
4. Use the first position that passes all checks.

**For the start cell:** The entry point is the roost position. The hub must have line-of-sight to the roost.

**For subsequent cells:** The entry point is the boundary crossing position from which this cell was reached during BFS.

**Failure:** If no position in the cell passes all checks, the cell has no navigable hub. During BFS, this means the cell is skipped. For the start cell, this triggers a search of adjacent cells.

### Cell Passability

Two adjacent cells are passable if a bat can fly between them.

**Requirements:**

1. Both cells have navigable hubs.
2. There exists at least one air block on the shared boundary face.
3. The boundary crossing has line-of-sight to both cells' hubs.

**Boundary checking procedure:**

The shared face between 4×4×4 cells is 4×4 = 16 blocks. Check in this order, stopping as soon as one passes:

1. Check the center block of the face.
2. Check the four corner blocks of the face.
3. If none of the 5 quick checks pass, check all remaining blocks on the face exhaustively.

For each boundary block checked:
1. Verify the block is air.
2. Verify the block on the other side of the boundary is also air.
3. Raycast from the source cell's hub to the boundary block—must pass through only air.
4. Raycast from the boundary block to the destination cell's hub—must pass through only air.

If any boundary block passes all four checks, the cells are passable.

**Line-of-sight:** Uses Minecraft's built-in raycast (Level.clip or equivalent). A raycast passes if it hits nothing before reaching the target.

### Solution Validation

Cached solutions may become invalid when the world changes. Validation checks whether a solution is still usable.

**Validation procedure:**

Validation traces the existing path and performs the same checks used during generation, without the BFS search:

1. Verify the exit point is still air with sky access.
2. Starting from the exit cell, follow the inward vectors toward the roost.
3. For each cell along the path:
   - Verify the hub position is still air.
   - Verify passability to the next cell using the same boundary checking procedure as generation.
4. Continue until reaching the start cell.

If any check fails, the solution is invalid.

**When to validate:** Periodically while the roost is loaded. Validation should be frequent enough to catch player modifications (blocked exits, filled caves) but not so frequent that it wastes resources on unchanged geometry.

**Validation frequency:** Every 30 seconds, triggered by the roost block entity's tick. Each roost maintains a countdown ticker; when it reaches zero, validation runs and the ticker resets.

**On validation failure:** The solution is discarded and regeneration is requested.

### Query Interface

Bats query the flow field to get navigation direction.

**Input:** A world position and a field type (outward or inward).

**Output:** A direction vector pointing toward the destination, and optionally a hub position for precise waypoint targeting. Returns nothing if the position is not covered by the flow field.

**Behavior:** Convert the world position to a cell, look up that cell in the requested field, return the stored direction. Lookup should be constant-time.

## Threading Model

Flow field generation is computationally expensive and must not block the game thread.

**Requirements:**

1. Generation runs on background threads, never on the main game thread.
2. Multiple generations can run concurrently (for different roosts).
3. Results are delivered to roosts on the main thread.
4. Generation can be interrupted if it takes too long.

**Thread pool:** A fixed pool of 2 worker threads handles generation requests. Generation is CPU-bound; more threads provide diminishing returns and risk starving the game thread.

**Timeout:** Generation aborts after 5 seconds. Pathological cave geometry should not hang the worker pool.

**Priority requests:** Debug commands and forced regeneration should bypass the normal queue to provide immediate feedback.

## Failure Handling

### Generation Failures

Generation can fail for several reasons:

| Failure | Cause | Response |
|---------|-------|----------|
| No start cell | Roost surrounded by solid blocks | Retry later; geometry may change |
| No exit found | Cave has no sky access within range | Retry later; player may open an exit |
| Timeout | Cave too large or complex | Retry later with backoff |
| Chunk unloaded | World state unavailable during generation | Retry when chunks reload |

**Retry behavior:** Failed generations retry with exponential backoff to avoid flooding the worker pool:

- Initial delay: 30 seconds
- Delay doubles with each consecutive failure
- Maximum delay: 5 minutes
- Backoff resets immediately on successful generation

### Runtime Failures

**Bat queries missing cell:** If a bat's position isn't covered by the flow field, the query returns nothing. Bat AI should handle this gracefully (yield, use fallback navigation, or wait for a solution).

**Roost destroyed:** When a roost is removed, its solution is discarded. Any in-flight generation requests should be cancelled or their results ignored.

### Validation Failures

When validation fails, the solution is discarded and regeneration is requested. The roost operates without a solution until regeneration completes.

## Solution Sharing

Multiple roosts in the same cave can share a single flow field solution, reducing redundant computation.

**Sharing criteria:** A roost can adopt another roost's solution if:

1. Both roosts are in the same starting cell (very close together).
2. The receiving roost can reach the solution's navigation hubs.
3. The solution validates against current world state.

**Adoption timing:** A roost only checks for adoptable solutions when it needs a solution—on startup with no cached solution, or after its current solution fails validation. Roosts do not proactively adopt solutions while they have a valid one.

**Sharing mechanism:** When a roost successfully generates a solution, it advertises to nearby roosts. Roosts needing solutions check for available candidates before requesting generation.

> **Implementation note:** The current sharing implementation has known issues. The sharing radius and mechanism will be revised based on PoC testing of roost density patterns.

## Integration Points

### Depends On

**Roost block entity:** Solutions are cached on roosts. Roosts manage validation timing, retry scheduling, and persistence.

**World/Level:** Generation reads block state and performs raycasts. Requires loaded chunks.

**Server tick scheduling:** Results must be delivered on the main thread.

### Provides To

**Bat AI:** Query interface for navigation direction and waypoints.

**Debug tools:** Commands for forcing regeneration, inspecting state, and visualizing flow fields.

## Validation Criteria

### Correctness

**Path completeness:** Following outward vectors from the start cell reaches the exit. Following inward vectors from the exit reaches the start cell.

**No cycles:** The path is acyclic; following directions always makes progress.

**Hub validity:** Every hub is an air block with line-of-sight to its neighbors' hubs.

**Boundary passability:** Every cell transition crosses through air on both sides of the boundary.

### Performance

**Generation speed:** Typical caves complete in under one second. Complex caves complete within the timeout.

**Query speed:** Direction lookup is constant-time.

**Storage efficiency:** Only path cells are stored, not the entire cave volume.

### Robustness

**Graceful degradation:** Failed solutions don't break roosts or bats. The system continues operating and retries.

**Thread safety:** Background generation never corrupts main-thread state.

**Persistence reliability:** Solutions survive world save/load without requiring regeneration.

## Open Questions

1. **Sharing radius:** The current sharing radius is very conservative (same 4-block cell). Should roosts further apart share solutions if their paths overlap? This depends on typical roost density, which is being evaluated in PoC testing. See Colony Health Architecture open questions about roost spacing.
