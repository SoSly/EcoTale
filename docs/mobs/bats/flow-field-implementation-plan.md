# Flow Field Navigation Implementation Plan

This document outlines the build order for the flow field navigation system described in `flow-field-navigation.md`. The goal is to implement in testable increments, with debug visualization available early.

## Key Decisions

These decisions were made during planning and supersede any conflicting details in the design doc:

| Decision | Value               | Rationale                                                                                                   |
|----------|---------------------|-------------------------------------------------------------------------------------------------------------|
| Cell resolution | 8 blocks            | Aligns with chunk sections, minimal memory footprint (max ~4096 cells), coarse is fine for macro navigation |
| Search radius | 128 blocks          | 16 cells per axis at 8-block resolution, reasonable cave size limit                                         |
| Search termination | Early exit          | Stop when we find one exit, don't map the whole cave                                                        |
| Exit precision | Stored separately   | Flow field gets bat to the region, stored coordinates give exact target                                     |
| Local navigation | FlyingPathNavigator | Vanilla handles obstacle avoidance and tight squeezes                                                       |
| Generation timeout | 5 seconds           | If we haven't found a solution by then, the search is too expensive                                         |
| Thread safety | Fail gracefully     | Wrap Level access in try-catch; if chunks unload, fail and let validation trigger retry                     |
| Validation interval | 500 game ticks      | How often roosts check if their flow field is still valid                                                   |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      RoostBlockEntity                       │
│  - Owns FlowFieldSolution                                   │
│  - Requests generation via FlowFieldManager                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     FlowFieldManager                        │
│  - Singleton, owns the generation queue                     │
│  - Processes requests on worker thread                      │
│  - Returns results to roosts on main thread                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    FlowFieldSolution                        │
│  - Sparse map of cell positions → direction vectors         │
│  - Exit point (precise coordinates)                         │
│  - Bounds information for validation                        │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Phases

### Phase 1: Data Structures

**Goal:** Define the core types without any generation logic.

**Files:**
- `src/main/java/org/sosly/ecotale/navigation/FlowFieldCell.java`
- `src/main/java/org/sosly/ecotale/navigation/FlowFieldSolution.java`
- `src/main/java/org/sosly/ecotale/navigation/FlowFieldManager.java` (stub)

**FlowFieldCell:**
```java
public record FlowFieldCell(int x, int y, int z) {
    public static final int RESOLUTION = 8;

    public static FlowFieldCell fromBlockPos(BlockPos pos) {
        return new FlowFieldCell(
                Math.floorDiv(pos.getX(), RESOLUTION),
                Math.floorDiv(pos.getY(), RESOLUTION),
                Math.floorDiv(pos.getZ(), RESOLUTION)
        );
    }

    public BlockPos centerBlockPos() {
        return new BlockPos(
                x * RESOLUTION + RESOLUTION / 2,
                y * RESOLUTION + RESOLUTION / 2,
                z * RESOLUTION + RESOLUTION / 2
        );
    }
}
```

**FlowFieldSolution:**
- `Map<FlowFieldCell, Vec3> outwardField` - directions toward exit
- `Map<FlowFieldCell, Vec3> inwardField` - directions toward roost
- `Map<FlowFieldCell, BlockPos> hubCache` - cached hub positions for passability checks during validation
- `BlockPos exitPoint` - precise exit coordinates (single exit for now)
- `BlockPos roostPos` - the roost this solution belongs to
- `boolean isFailed()` - check if generation failed
- `boolean isValid(Level)` - path re-trace validation (stub implementation, returns true)
- `static FlowFieldSolution failed(BlockPos roostPos)` - factory for failed generation
- `static FlowFieldSolution create(...)` - factory for successful generation
- `CompoundTag save()` / `static FlowFieldSolution load(CompoundTag)` - NBT serialization
- `forEachOutwardCell()` / `forEachInwardCell()` - iteration helpers for debug rendering
- `getOutwardCellCount()` / `getInwardCellCount()` - for debug logging

**FlowFieldManager:**
- Singleton with `getInstance()` accessor
- `requestGeneration(RoostBlockEntity roost)` runs synchronously and logs timing/cell counts
- Will become the threaded queue manager in Phase 5

**Validation:** Code compiles.

---

### Phase 2: Debug Visualization

**Goal:** Render flow field data visually for debugging.

**Files:**
- `src/main/java/org/sosly/ecotale/commands/FlowFieldCommands.java`
- `src/main/java/org/sosly/ecotale/client/FlowFieldDebugRenderer.java`
- `src/main/java/org/sosly/ecotale/network/FlowFieldDebugPacket.java`

**Commands:**

All flow field commands live under `/ecotale flowfield` (with `/ecotale ff` as a shorthand alias):

```
/ecotale flowfield visualize 100 64 -200
/ecotale ff visualize ~ ~-5 ~
/ecotale flowfield clear
/ecotale flowfield revalidate 100 64 -200
```

**`/ecotale flowfield visualize <pos>`**

Toggles persistent flow field visualization using debug lines.

1. Look up the block at the specified position
2. If it's not a roost block, error: "No roost at that position"
3. If roost has no FlowFieldSolution or it failed, error: "No flow field generated for this roost"
4. Send a `FlowFieldDebugPacket` to the player's client
5. Client toggles visualization on/off for that roost position
6. When enabled, renders:
   - Outward field: orange arrows along direction vectors (offset slightly for visibility)
   - Inward field: blue arrows along direction vectors (offset opposite direction)
   - Exit point: green vertical column
7. Visualization persists until toggled off or cleared

**`/ecotale flowfield clear`**

Clears all active flow field visualizations for the player.

**`/ecotale flowfield revalidate <pos>`**

Forces immediate revalidation, bypassing the 500-tick interval. Useful for testing.

1. Look up the block at the specified position
2. If it's not a roost block, error: "No roost at that position"
3. Run validation checks immediately
4. Report result: "Flow field valid" or "Flow field invalid, regeneration queued"
5. Reset failure backoff to allow immediate retry if regeneration is needed

**Implementation Notes:**
- Debug rendering uses `DEBUG_LINES` vertex format with depth testing disabled for visibility through terrain
- Arrows are rendered with 4-prong arrowheads for 3D clarity
- Outward and inward arrows are offset perpendicular to their direction so both are visible in the same cell
- The offset direction is deterministic per cell (based on position hash) so it's consistent across frames

**Validation:** All three commands exist and report appropriate errors for missing roosts or flow fields.

---

### Phase 3: Flood Fill Algorithm (Single-Threaded)

**Goal:** Generate actual flow field data. Runs on main thread initially.

**Files:**
- `src/main/java/org/sosly/ecotale/navigation/FlowFieldGenerator.java`

**Algorithm:**

The flood fill starts at the roost and expands outward using BFS. This means `cameFrom` naturally tracks paths back toward the roost, giving us the **inward** field directly. We then invert vectors to get the outward field.

BFS is the right choice here: all cell transitions have equal cost (unweighted graph), so a simple FIFO queue naturally produces shortest paths by cell count. A priority queue would add complexity without benefit.

**Key Implementation Decision:** The solution stores only cells on the actual path from roost to exit, not all visited cells. After finding the exit, we trace back through `cameFrom` to build a `pathCells` set, then only include those cells in the inward/outward fields. This significantly reduces memory for large caves where BFS explores many dead ends.

```
generate():
    actualStart = findReachableStartCell()  // see below
    if actualStart is null:
        return FlowFieldSolution.failed(roostPos)

    exitCell = runFloodFill(actualStart)
    if exitCell is null:
        return FlowFieldSolution.failed(roostPos)

    exitPoint = findPreciseExitPoint(exitCell)
    if exitPoint is null:
        return FlowFieldSolution.failed(roostPos)

    // Trace back from exit to roost to get only path cells
    pathCells = tracePathToExit(exitCell)

    // Build fields using only path cells
    inwardField = buildInwardField(pathCells)
    extendInwardFieldOutside(inwardField, exitCell, exitPoint)
    outwardField = buildOutwardField(inwardField)

    // Only include hub cache entries for cells in the solution
    pathHubCache = filter hubCache to pathCells

    return FlowFieldSolution.create(outwardField, inwardField, pathHubCache, exitPoint, roostPos)

runFloodFill(startCell):
    frontier = Queue (FIFO)
    visited = Set<FlowFieldCell>

    frontier.add(startCell)
    visited.add(startCell)

    while frontier is not empty:
        current = frontier.poll()

        if isExit(current):
            return current  // early termination

        if distanceFromStart(current) > MAX_RADIUS_CELLS:
            continue  // bound the search

        for neighbor in getPassableNeighbors(current):
            if neighbor not in visited:
                visited.add(neighbor)
                frontier.add(neighbor)
                cameFrom.put(neighbor, current)

    return null  // no exit found

tracePathToExit(exitCell):
    pathCells = Set<FlowFieldCell>
    current = exitCell
    while current is not null:
        pathCells.add(current)
        current = cameFrom.get(current)
    return pathCells
```

**Reachable Start Cell:**

The roost block might be positioned such that its geometric cell's hub isn't directly reachable (e.g., roost is in a corner and the cell center is behind a wall). We handle this by checking reachability and falling back to neighboring cells:

```
findReachableStartCell():
    hub = findHub(startCell)
    if hub is not null and canReachHub(hub):
        hubCache.put(startCell, hub)
        return startCell

    // Try all 6 neighbors
    for neighbor in adjacent cells:
        neighborHub = findHub(neighbor)
        if neighborHub is not null and canReachHub(neighborHub):
            hubCache.put(neighbor, neighborHub)
            return neighbor

    return null  // roost is completely enclosed

canReachHub(hub):
    // Raycast from roost position (below the roost block) to hub
    return raycastClear(roostPos.below().center(), hub.center())
```

**Cell Passability:**
`getPassableNeighbors(current, level)` returns adjacent cells that a bat can actually fly to. An 8-block cell can have complex mixed geometry, so "contains air" isn't sufficient—we need to verify the bat can traverse between cells.

Two checks are required:
1. **Boundary check:** The shared face has open air (can cross between cells)
2. **Internal check:** Clear line of sight from crossing point to neighbor's hub (can traverse the neighbor)

The internal check matters because a cell might have air at multiple boundaries but a wall through its center. Without it, A→B→C could fail if B has a wall separating its A-side from its C-side.

**Cell Hub:**
The "hub" is an air block near the cell's geometric center, used as the internal connectivity reference. The geometric center itself might be solid.

```
findHub(cell, level):
    center = cell.centerBlockPos()
    if isAir(center, level):
        return center

    // Spiral outward from center until we find air
    for radius in 1 to 4:
        for each block within radius of center (still inside cell):
            if isAir(block, level):
                return block

    return null  // Cell has no hub; effectively impassable
```

Hub positions can be cached per cell during generation since they don't change.

```
getPassableNeighbors(current, level, hubCache):
    neighbors = []

    for each of 6 axis-aligned neighbors (±X, ±Y, ±Z):
        neighbor = adjacent cell in that direction

        // Use cached hub if available, otherwise compute and cache
        neighborHub = hubCache.get(neighbor)
        if neighborHub is null:
            neighborHub = findHub(neighbor, level)
            if neighborHub is null:
                continue  // No air in this cell
            hubCache.put(neighbor, neighborHub)

        boundaryFace = shared 8×8 plane between current and neighbor

        if tryBoundaryCrossing(boundaryFace, neighborHub, level):
            neighbors.add(neighbor)

    return neighbors

tryBoundaryCrossing(boundaryFace, neighborHub, level):
    // Sample first: center, 4 corners, 4 edge midpoints (9 positions)
    // Most openings are larger than one block, so sampling usually succeeds
    samplePositions = [
        center of boundaryFace,
        4 corners,
        4 edge midpoints
    ]

    for position in samplePositions:
        if checkCrossing(position, neighborHub, level):
            return true

    // Exhaustive fallback for single-block openings
    for each block position on boundaryFace:
        if position in samplePositions:
            continue  // Already checked
        if checkCrossing(position, neighborHub, level):
            return true

    return false

checkCrossing(position, neighborHub, level):
    // Boundary check: air on both sides
    if not isAir(position, level) or not isAir(position + directionToNeighbor, level):
        return false

    // Internal check: can reach neighbor's hub from crossing point
    crossingPoint = position + directionToNeighbor
    return raycastClear(crossingPoint, neighborHub, level)
```

This isn't perfect—pathological geometry could still cause issues—but it catches the common cases of internal walls and solid centers. The sampling optimization avoids checking all 64 boundary positions when large openings exist.

**Exit Detection:**
A cell is an exit if it contains air blocks with sky access.

**Precise Exit Point:**
When we find an exit cell, we need to locate the actual cave mouth, not just any sky-access block. We know which direction we came from via `cameFrom`, so we know which cell face is the cave entrance.

Algorithm:
1. Determine the inward-facing boundary of the exit cell (the face adjacent to the cell we came from)
2. Scan that boundary face for air blocks with sky access
3. Select the one closest to where the flood fill entered
4. From that position, move up 2-3 blocks to get clearance for smooth outdoor flight

This finds the actual cave mouth because that's literally where we crossed from cave interior to exit cell.

**Inward Field Extension:**
After building the core inward field, extend it 2-3 cells (16-24 blocks) outside the cave entrance. This ensures returning bats can pick up the flow field smoothly when they arrive at the entrance, rather than having a navigation gap.

Extension behavior:
1. Compute the outward direction from `cameFrom`: the exit cell came from some previous cell, so `exitCell → cameFrom[exitCell]` points inward; invert it to get the actual exit direction
2. Starting at the exit point cell, step along that outward direction for 2-3 cells
3. Each extension cell's vector points toward the exit point, funneling the bat toward the entrance
4. Only extend into cells that have sky access—this prevents accidentally extending into adjacent enclosed spaces
5. Once the bat reaches the exit point vicinity, the normal inward field takes over and guides them to the roost

Using `cameFrom` instead of "roost to exit" handles curved and L-shaped caves correctly. The BFS knows which direction it was actually traveling when it found the exit—that's the real exit direction, not a straight line through rock.

The sky access check is the same one we use for exit detection. If an "outside" cell doesn't have sky access, it's probably a canyon wall or another cave, and we don't extend there.

**Hook into FlowFieldManager:**
`requestGeneration()` calls the generator synchronously and stores the result.

**Validation:**
1. Place a roost in a cave
2. Run `/ecotale flowfield visualize <roost_pos>`
3. Verify inward field particles (blue) point toward roost
4. Verify outward field particles (orange) point toward exit
5. Verify exit point is detected and inward field extends outside

---

### Phase 4: Validation and Regeneration

**Goal:** Roosts detect stale flow fields and request regeneration.

**Files:**
- Modify `FlowFieldSolution.java`
- Modify `RoostBlockEntity.java`

**Validation Interval:**
Roosts validate their flow field every 500 game ticks (~25 seconds). This is a tuning lever we can adjust later based on performance and gameplay feel.

**Validation Checks:**
- Solution exists and hasn't been garbage collected
- Exit point is still an air block with sky access
- Full path re-trace from roost to exit (see below)

**Path Re-Trace:**
The flow field doesn't store an explicit path, so validation traces it by following outward vectors from roost to exit:

```
validatePath(solution, level):
    current = FlowFieldCell.fromBlockPos(solution.roostPos)
    visited = Set<FlowFieldCell>

    while current != FlowFieldCell.fromBlockPos(solution.exitPoint):
        if current in visited:
            return false  // Loop detected, field is corrupt

        visited.add(current)
        direction = solution.outwardField.get(current)
        if direction is null:
            return false  // Gap in field

        next = cellInDirection(current, direction)
        if not isPassable(current, next, level):  // Same check as generation
            return false  // Path blocked

        current = next

    return true
```

At 8-block cells and 128-block max radius, this traces at most ~16 cells. Each step is a passability check (boundary + hub raycast). This is cheap compared to full regeneration and catches mid-path blockages that exit-only checks would miss.

**Note on `cellInDirection`:** This function converts a normalized Vec3 to a discrete neighbor cell. Since neighbors are axis-aligned (±X, ±Y, ±Z only), direction vectors have exactly one dominant axis—just check which component has the largest absolute value and step in that direction.

```
cellInDirection(cell, direction):
    // Find dominant axis
    absX, absY, absZ = abs(direction.x), abs(direction.y), abs(direction.z)
    maxComponent = max(absX, absY, absZ)

    // Assert axis-aligned assumption: dominant axis should be ~1.0 for unit vectors
    // Diagonal neighbors would have components ~0.707; this catches that
    assert maxComponent > 0.7, "Direction vector not axis-aligned; diagonal neighbors not supported"

    if absX == maxComponent:
        return cell offset by sign(direction.x) in X
    else if absY == maxComponent:
        return cell offset by sign(direction.y) in Y
    else:
        return cell offset by sign(direction.z) in Z
```

This turns a silent assumption into a loud failure if the axis-aligned invariant is ever violated.

**Trigger Regeneration:**
- On roost first load (if no solution)
- On validation failure (checked every 500 ticks)
- When notified of nearby block changes (optional optimization)

**Validation — Alternate Exit:**
1. Generate flow field for a cave with two exits, verify it works
2. Block the primary exit with dirt
3. Force revalidation via `/ecotale flowfield revalidate`
4. Verify roost requests new generation
5. Verify new flow field routes to the alternate exit

**Validation — Sealed Cave:**
1. Create a roost in a cave with only one exit
2. Generate flow field, verify it works
3. Seal the exit completely
4. Force revalidation, verify `FlowFieldSolution.failed()` is returned
5. Wait and verify backoff behavior: no immediate retry, increasing delay between attempts
6. Spawn bats at the roost
7. Set time to dusk, verify bats remain clustered at roost (no navigation, no errors)
8. Unseal the exit
9. Verify next generation attempt succeeds and bats can navigate out

---

### Phase 5: Threaded Generation

**Goal:** Move generation off the main thread.

**Files:**
- Modify `FlowFieldManager.java`
- Add `src/main/java/org/sosly/ecotale/navigation/FlowFieldRequest.java`

**Queue System:**
- Single worker thread processing requests
- Roosts submit requests, get callbacks on main thread when done
- Failed generations (chunks unloaded, no exit found) requeue at back
- Validation requests use the same queue as generation requests

**Why Shared Queue:**
Validation (path re-trace) and generation (BFS flood fill) both need to read Level data and operate on the same FlowFieldSolution. Running them on the same single-threaded queue prevents race conditions—a roost can't be mid-generation while also being validated. This is simpler than fine-grained locking and sufficient for our throughput needs.

**Thread Safety via Defensive Access:**
Minecraft's Level isn't thread-safe, but we read directly from it anyway and handle failures gracefully:

1. Wrap all `Level.getBlockState()` calls in try-catch
2. If an exception occurs (chunk unloaded, concurrent modification, etc.), abort generation and mark as failed
3. Failed generations requeue at the back; the roost will retry later
4. The validation system (Phase 4) handles cases where geometry changed mid-generation

This is simpler than snapshotting and avoids memory overhead. The worst case is a wasted generation attempt, which the existing retry logic handles.

**Timeout:**
If generation exceeds 5 seconds, abort and mark as failed. This prevents pathological cave geometries from blocking the queue.

**Failure Handling:**
When a roost receives `FlowFieldSolution.failed()`, it doesn't retry immediately. This prevents queue thrashing when caves genuinely have no exit.

Retry policy:
1. On first failure: wait one validation interval (500 ticks) before retrying
2. On repeated failures: double the wait time, up to a maximum of 5 minutes (6000 ticks)
3. Track failure count on the roost; reset to zero on success
4. If a nearby block change notification arrives, reset backoff and allow immediate retry (geometry changed, might work now)

Roosts with failed solutions still function—bats just can't navigate out. They'll cluster at the roost until a valid exit is found. This is actually reasonable behavior for a cave with no exit.

**Validation:**
1. Place roost in cave
2. Verify no lag spike during generation
3. Verify flow field appears after short delay
4. Verify generation fails gracefully if chunks unload mid-generation
5. Verify failed generation retries with backoff, not immediately
6. Verify block changes reset backoff and trigger retry

---

### Phase 6: Solution Sharing

**Goal:** Nearby roosts share flow field solutions to reduce redundant computation.

**Files:**
- Modify `FlowFieldManager.java`
- Modify `RoostBlockEntity.java`

**Sharing Rules:**
- All roosts must share a starting cell
- Receiving roost validates the solution works for its position (this happens during the regular validation cycle rather than immediately)

**Implementation:**
When a roost receives a successful solution, it broadcasts to nearby roosts. Those roosts check if the solution's inward field is reachable from their position. If yes, they adopt it.

**Validation:**
1. Place two roosts near each other in a cave
2. First roost generates flow field
3. Second roost adopts the shared solution without generating its own
4. Verify both roosts have valid navigation

---

### Phase 7: Brain Integration

**Goal:** Bats use the flow field to navigate.

**Files:**
- Modify `src/main/java/org/sosly/ecotale/entities/ai/behavior/bat/ExitCave.java`
- Modify `src/main/java/org/sosly/ecotale/entities/ai/behavior/bat/ReturnToRoost.java`
- Modify `src/main/java/org/sosly/ecotale/entities/EcoTaleBat.java` (persistence)

**Persistence:**
The following must be saved in `addAdditionalSaveData` and restored in `readAdditionalSaveData`:

| Memory | Type | Why |
|--------|------|-----|
| HOME | GlobalPos | Already planned in bat-ai-design.md |
| IS_OUTSIDE | boolean | Bat must know if it was outside when world saved |
| Cached exit point | BlockPos | Bat needs this to return if roost chunk is unloaded |

Without IS_OUTSIDE persistence, a bat that was foraging outside would reload thinking it's inside the cave, then try to follow the outward field and fly further away from home.

**Vicinity Distances:**
| Location | Distance | Rationale |
|----------|----------|-----------|
| Exit point | 8 blocks | Larger radius for smooth handoff to outdoor wandering; exit is a transition zone, not a precise target |
| Roost | 4 blocks | Smaller radius so bat is genuinely home before stopping navigation; roost is a specific block |

These are tuning levers. Too small and bats overshoot and oscillate. Too large and transitions trigger while the bat is still mid-journey.

**ExitCave Behavior:**
1. Get home roost from HOME memory
2. Get flow field solution from roost
3. Sample outward field at bat's current cell
4. Convert direction to a candidate waypoint ~8 blocks ahead
5. Raycast from bat's position to waypoint; if obstructed, shorten to the obstruction point
6. Set FlyingPathNavigator to move toward validated waypoint
7. When bat is within 8 blocks of exit point, set IS_OUTSIDE = true

**ReturnToRoost Behavior:**
1. If far from cave entrance, fly toward the exit point
2. Once near entrance, sample inward field
3. Convert direction to a candidate waypoint ~8 blocks ahead
4. Raycast from bat's position to waypoint; if obstructed, shorten to the obstruction point
5. Follow validated waypoints to roost
6. When bat is within 4 blocks of roost, clear IS_OUTSIDE

**Waypoint Validation:**
Flow field cells are coarse (8 blocks), so obstacles can exist between the bat and its target waypoint. Before committing to a waypoint:
1. Cast a ray from the bat's current position to the candidate waypoint
2. If the ray hits an obstacle, use the hit point (minus a small margin) as the waypoint instead
3. This ensures bats don't try to fly through walls even when the flow field says "go that direction"

FlyingPathNavigator handles fine-grained collision avoidance, but pre-validating waypoints reduces unnecessary navigator corrections and produces smoother flight paths.

**Edge Cases:**
- No flow field yet: yield (do nothing this tick)
- Flow field invalid: yield, roost will regenerate
- Bat outside flow field bounds: fly toward roost directly
- Roost chunk unloaded while bat is outside: see below

**Cached Exit Point:**
When a bat exits the cave (IS_OUTSIDE becomes true), it caches the exit point in its own memory. This allows the bat to return even if its roost's chunk unloads while foraging.

Return behavior when roost chunk is unloaded:
1. Bat still has HOME (BlockPos) and cached exit point
2. Fly toward cached exit point
3. When close, fly toward HOME directly
4. FlyingPathNavigator handles collision avoidance
5. As the bat approaches, the chunk will load (entity tracking range), restoring access to the flow field
6. If chunk loads before reaching the entrance, switch back to normal inward field navigation

The cached exit point should be persisted with the bat's save data so it survives chunk unload/reload cycles.

**Validation:**
1. Spawn bat at roost
2. Set time to dusk
3. Observe bat navigate out of cave using flow field
4. Set time to dawn
5. Observe bat return to roost

**Validation — Chunk Unload:**
1. Spawn bat at roost, let it exit at dusk
2. Unload roost chunk (fly far away or use commands)
3. Set time to dawn
4. Verify bat flies toward cached exit point without errors
5. Approach the roost area, verify chunk loads and bat completes return

---

## Testing Milestones

| After Phase | Testable Behavior |
|-------------|-------------------|
| 2 | Debug command exists, reports appropriate errors |
| 3 | Flow field visualization shows correct directions and exit detection |
| 4 | Blocking exits triggers regeneration |
| 5 | Generation completes without main thread lag |
| 6 | Multiple roosts share solutions |
| 7 | Bats navigate caves correctly |

---

## Future Work

### Multiple Exit Support

The current implementation finds one exit and terminates. If performance proves acceptable and gameplay benefits from it, we could extend to support multiple exits.

**Data Structure Changes:**
- `FlowFieldSolution.exitPoint` becomes `List<BlockPos> exitPoints`
- Each exit needs an associated "catchment area" of cells that should route to it

**Algorithm Changes:**
- Remove early termination (`break` becomes `continue` when finding an exit)
- Continue flood fill until all cells within radius are visited or timeout
- Track which exit each cell is closest to
- Build per-exit outward vectors: each cell points toward its nearest exit, not a single global exit

**Brain Integration Changes:**
- ExitCave: bat picks the nearest exit from `exitPoints` and follows vectors toward that specific exit
- ReturnToRoost: bat flies toward whichever entrance is closest, then picks up inward field

**Tradeoffs:**
| Aspect | Single Exit | Multiple Exits |
|--------|-------------|----------------|
| Generation time | Fast (early termination) | Slower (full exploration) |
| Memory | Minimal | Proportional to cave size |
| Resilience | One blocked exit = regenerate | Fallback exits available |
| Bat behavior | All bats use same path | Bats spread across exits |
| Complexity | Simple | Moderate |

**When to Consider:**
- Large cave systems where single-exit feels unnatural
- Caves with multiple natural openings that players expect bats to use
- If exit-blocking becomes a common griefing vector and regeneration is too slow

For now, single exit is sufficient. The architecture doesn't preclude multiple exits—it's mostly a matter of removing early termination and adding nearest-exit logic to the vector generation.
