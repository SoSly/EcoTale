---
level: 5
parent: "[[Navigation Graph Specification]]"
status: implemented
---

This build plan migrates from the existing Flow Field implementation to the Navigation Graph design specified in the parent document. The old flow field classes will be deleted and replaced, but significant portions of proven, working code will be adapted rather than rewritten from scratch.

## Key Decisions

| Decision | Value | Rationale |
|----------|-------|-----------|
| Cell resolution | 4×4×4 blocks | Unchanged from current impl; proven to work for bat navigation |
| Search radius | 128 blocks | Unchanged; balances coverage vs generation time |
| Generation timeout | 5 seconds | Unchanged; prevents runaway generation in complex caves |
| Worker thread count | 2 | Unchanged; sufficient for typical server load |
| Hub search order | Center first, then expanding shells Y→Z→X | Deterministic; prefers lower positions (floor-hugging) |
| Retry initial delay | 30 seconds | Long enough to avoid spam, short enough to recover |
| Retry max delay | 5 minutes | Caps exponential backoff |
| Block change debounce | 500ms | Coalesces rapid changes (e.g., player mining) into single regen |
| Extension termination | All 64 blocks air + all 16 columns sky access | Matches spec; ensures clear exit space |
| BlockPos packing | BlockPos.asLong() | Standard Minecraft approach for NBT storage |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     RoostBlockEntity                        │
│  - owns Graph                                               │
│  - listens for block changes                                │
│  - requests generation via Manager                          │
└─────────────────────┬───────────────────────────────────────┘
                      │ owns
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                         Graph                               │
│  - id (roost BlockPos)                                      │
│  - entityType (IFlyingMob)                                  │
│  - cells: Map<BlockPos, Cell>                               │
│  - destinations: Set<BlockPos>                              │
│  - graphStart, graphExits                                   │
└─────────────────────────────────────────────────────────────┘
                      │ contains
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                         Cell                                │
│  - bounds (AABB)                                            │
│  - hub (BlockPos)                                           │
│  - paths: Map<BlockPos, BlockPos>  (dest → next hop)        │
└─────────────────────────────────────────────────────────────┘

Generation Pipeline:
  Manager (queue) → GraphGenerator (worker thread) → Graph (result)

Navigation Query:
  Entity → Graph.getNextHop(currentHub, destination) → BlockPos
```

## Code Reuse

The existing flow field implementation contains proven, battle-tested code. This section identifies what to adapt vs. rewrite.

### Adapt (minimal changes)

**FlowFieldValidators → Validators.java**
- `isCellTransitionValid()` — boundary sampling, raycast logic, direction detection. Works correctly.
- `isHubValid()`, `isExitValid()` — simple air/sky checks. Keep as-is.
- Changes needed: rename class, update type references from `FlowFieldCell` to `Cell`

**FlowFieldManager → Manager.java**
- Thread pool setup with daemon threads
- Priority worker bypass for debug commands
- Pause/resume capability
- Solution broadcasting via weak references
- Graceful shutdown logic
- Changes needed: add debouncing, add cancellation for in-flight work, remove validation requests

**FlowFieldGenerator → GraphGenerator.java (partial)**
- `findReachableStartCell()` — start cell discovery with neighbor fallback
- `findHubReachableFromEntry()` / `findHubReachableFromRoost()` — hub discovery with LOS validation
- `isExit()` / `findPreciseExitPoint()` — exit detection within cell
- `isCellFullyOutside()` / `extendInwardFieldOutside()` — extension logic until fully outside
- BFS flood fill structure (queue, visited set, cameFrom map)
- Timeout checking pattern with interval sampling
- World access helpers: `isAir()`, `hasSkyAccess()`, `raycastClear()`, `isWithinCell()`
- Changes needed: don't stop at first exit, keep all cells, build paths instead of vectors

**FlowFieldDebugRenderer → DebugRenderer.java**
- Tesselator/RenderSystem patterns for line and box rendering
- Arrow drawing with perpendicular offsets
- Per-roost toggle capability
- Changes needed: new color scheme, render paths instead of vectors

### Rewrite (different design)

**FlowFieldCell → Cell.java**
- Old: just coordinates (x, y, z record)
- New: bounds + hub + paths map
- The coordinate math (`fromBlockPos`, `contains`, `centerBlockPos`) can be adapted, but the class structure changes fundamentally

**FlowFieldSolution → Graph.java**
- Old: inward/outward Vec3 fields, single exit, path cells only
- New: cells map, destinations set, multiple exits, all discovered cells
- Complete rewrite required

**FlowFieldNavigator**
- Deleted entirely
- Navigation becomes a two-line method: `cells.get(hub).paths.get(destination)`

**FlowFieldDirection**
- Deleted entirely
- No longer needed; paths map stores explicit next-hop, not direction vectors

**Direction vector building**
- `buildInwardField()`, `buildOutwardField()` — deleted
- Replaced by path table construction using reverse BFS

## File Structure

All navigation code moves to a new package structure:

```
src/main/java/org/sosly/ecotale/navigation/
├── Cell.java                 # Navigation cell with paths map
├── Graph.java                # Complete navigation solution
├── GraphGenerator.java       # BFS generation algorithm
├── Manager.java              # Thread pool and request queue
├── Validators.java           # Shared validation utilities
├── IFlyingMob.java           # Interface for supported entity types
├── DebugRenderer.java        # Client-side visualization
└── DebugPacket.java          # Server→client debug data

Deleted files (old flow field):
├── FlowFieldCell.java
├── FlowFieldSolution.java
├── FlowFieldGenerator.java
├── FlowFieldManager.java
├── FlowFieldNavigator.java
├── FlowFieldDirection.java
├── FlowFieldValidators.java
├── FlowFieldRequest.java
├── FlowFieldDebugRenderer.java
└── FlowFieldDebugPacket.java

New test resources:
└── src/test/resources/data/ecotale/structures/nav_test_cave.nbt
```

## Phased Build Order

### Phase 1: Data Structures

**Goal:** Define the core data types that everything else builds on.

**Files:** `Cell.java`, `Graph.java`, `IFlyingMob.java`

**Details:**

`IFlyingMob` interface:
- Method to get entity dimensions (width, height)
- Marker interface that EcoTaleBat will implement

`Cell` record/class:
- `AABB bounds` — the 4×4×4 region
- `BlockPos hub` — navigable waypoint within bounds
- `Map<BlockPos, BlockPos> paths` — destination hub → next hub (null = arrived)
- Factory method `fromHubPosition(BlockPos hub)` that computes bounds
- Method `contains(BlockPos)` for position lookup

`Graph` class:
- Fields per spec: id, entityType, cells map, destinations set, graphStart, graphExits
- `getCell(BlockPos hub)` — O(1) lookup
- `findContainingCell(BlockPos pos)` — linear scan, returns cell whose bounds contain pos
- `getNextHop(BlockPos currentHub, BlockPos destination)` — the navigation query
- NBT save/load methods (can be stubs initially)

**Validation:**
- Unit tests for Cell bounds calculation (hub at center of bounds, contains returns true for positions inside)
- Unit tests for Graph.findContainingCell with various positions (inside cell, outside all cells, on boundary)
- Unit tests for Graph.getNextHop returning correct next hop / null at destination
- Unit tests for Graph invariants on manually-constructed test graphs:
  - All cells have path entries for every destination
  - Following paths from any cell reaches destination (terminates with null)
  - Path length never exceeds cell count (no cycles)

---

### Phase 2: Core Generation Algorithm

**Goal:** Generate a navigation graph from a roost position using BFS.

**Files:** `GraphGenerator.java`, `Validators.java`

**Details:**

`Validators` — adapt from `FlowFieldValidators`:
- Copy `isCellTransitionValid()` with its helper methods (`getDirectionBetweenCells`, `getBoundaryStart`, `getPositionOnBoundary`, `getSamplePositions`, `checkCrossing`, `raycastClear`)
- Copy `isHubValid()`, `isExitValid()`
- Add `isFullyOutside(Level, AABB)` — adapted from `FlowFieldGenerator.isCellFullyOutside()`
- Update type references: `FlowFieldCell` → `Cell`
- Update `raycastClear()` to accept entity and pass it to `ClipContext` — vanilla handles hitbox-aware raycasting for us

`GraphGenerator` — adapt BFS structure from `FlowFieldGenerator`:

**Methods to copy/adapt from FlowFieldGenerator:**
- `findReachableStartCell()` → finds navigable start with neighbor fallback
- `findHubReachableFromRoost()` → hub discovery for start cell
- `findHubReachableFrom()` / `findHubReachableFromEntry()` → hub discovery for BFS expansion
- `isExit()` → checks if cell has sky access
- `findPreciseExitPoint()` / `elevateExitPoint()` → precise exit location
- `isCellFullyOutside()` → termination check for extension
- Extension loop from `extendInwardFieldOutside()` → but extend ALL exits, not just one
- `checkTimeout()` pattern with `TIMEOUT_CHECK_INTERVAL`
- World access helpers: `isAir()`, `hasSkyAccess()`, `raycastClear()`, `isWithinCell()`

**Key changes from FlowFieldGenerator:**

Phase 1 (Find Start):
```
startHub = findNavigableHub(level, roostPos, entityDims)
if startHub is null: return failed graph
startCell = Cell.fromHubPosition(startHub)
```

Phase 2 (BFS Flood Fill):
```
queue = [startCell]
visited = {startCell.hub: startCell}
parents = {startCell.hub: null}
adjacency = {} // temporary, for Phase 5
exitCells = []

while queue not empty and within timeout:
    current = queue.poll()
    for each adjacent cell position:
        if already visited: continue
        neighbor = tryCreateCell(level, adjacentPos, current, entityDims)
        if neighbor is null: continue  // no valid hub
        if not isCellTransitionValid(current, neighbor): continue

        visited[neighbor.hub] = neighbor
        parents[neighbor.hub] = current.hub
        adjacency[current.hub].add(neighbor.hub)
        adjacency[neighbor.hub].add(current.hub)  // bidirectional
        queue.add(neighbor)

        if hasSkyAccess(neighbor.hub):
            exitCells.add(neighbor)
```

Phase 3 (Extend Exits):
```
graphExits = []
for each exitCell in exitCells:
    extended = extendUpward(exitCell)
    graphExits.add(extended.hub)
    // add extension cells to visited
```

Phase 4 (Build Destinations):
```
destinations = {startCell.hub} ∪ graphExits
```

Phase 5 (Build Path Tables):
```
// Paths toward graphStart: follow parents directly
for each cell in visited:
    cell.paths[graphStart] = parents[cell.hub]  // null if this is start

// Paths toward each exit: reverse BFS from exit
for each exitHub in graphExits:
    bfsFromExit(exitHub, adjacency, visited)
    // sets cell.paths[exitHub] for all cells
```

Return `Graph` with all cells, destinations, graphStart, graphExits.

**Validation:**
- GameTest with test cave structure (create `nav_test_cave.nbt` — small cave, ~10 cells, 1-2 exits):
  - Generation completes without timeout
  - Programmatic verification of generated graph:
    - All cells have path entries for every destination (graphStart + all graphExits)
    - Following paths from any cell to any destination terminates with null
    - Path length never exceeds cell count
    - graphStart and all graphExits are in destinations set
    - Every hub is within its cell's bounds
  - Cell count matches expected for test structure
  - Exit count matches expected (1-2 depending on structure)
- Visual verification of graph structure happens in Phase 3

---

### Phase 3: Debug Visualization

**Goal:** Render navigation graph so we can visually verify generation is correct.

**Files:** `DebugPacket.java`, `DebugRenderer.java`

**Details:**

**Copy from FlowFieldDebugRenderer:**
- `RenderSystem` / `Tesselator` setup patterns
- `renderBox()` helper for cell bounds
- `renderArrow()` / arrow head drawing with perpendicular offsets
- Per-roost data storage and toggle logic
- `RenderLevelStageEvent` subscription

**Copy from FlowFieldDebugPacket:**
- Encode/decode structure for network transmission
- `TYPE` and `STREAM_CODEC` registration pattern

**Changes for new design:**

Color scheme:
- Cyan filled box: graphStart cell
- Red filled box: graphExit cells
- White filled box: other cells
- Yellow filled box: cells on highlighted path (from/to command)
- Purple small box: hub positions
- Blue lines: edges toward graphStart
- Orange lines: edges toward graphExits

`DebugPacket` contents:
- Graph ID (origin position)
- graphStart hub
- List of graphExit hubs
- For each cell: bounds, hub, and next-hop for each destination

`DebugRenderer`:
- Receive packet, store graph data
- Each frame: render cells, hubs, path edges

**Command structure:** `/ecotale graph <origin> {subcommand}`
- `/ecotale graph <x y z> debug` — toggle visualization on/off
- `/ecotale graph <x y z> regenerate` — force regeneration
- `/ecotale graph <x y z> info` — print graph stats (cell count, exit count, etc.)
- `/ecotale graph <x y z> from <x y z> to <x y z>` — highlight path between two positions in yellow (clears previous path highlight first)

**Validation:**
- Place roost, trigger generation (sync on main thread for now)
- Run `/ecotale graph <pos> debug` to enable visualization
- Verify cells render with correct colors
- Verify path lines connect hubs correctly
- Verify multiple exits show distinct paths
- Visually confirm graph covers expected cave area
- Run `/ecotale graph <pos> from <cave pos> to <exit pos>` and verify yellow path highlights correct route

---

### Phase 4: Threading and Request Management

**Goal:** Run generation off main thread with cancellation support.

**Files:** `Manager.java`

**Details:**

**Copy from FlowFieldManager:**
- `start()` / `stop()` — thread pool lifecycle with daemon threads
- `workerPool` + `priorityWorker` structure
- `setPaused()` / `isPaused()` / `waitWhilePaused()` — pause capability
- `scheduleCallback()` — main thread result delivery via `server.execute()`
- `registerRoost()` / `unregisterRoost()` — weak reference tracking
- `broadcastSolution()` — solution sharing between roosts with same start cell

**Remove from FlowFieldManager:**
- `requestValidation()` and `processValidation()` — no longer needed
- `deliverValidationResult()` — no longer needed
- `FlowFieldRequest` usage — delete the class entirely; Manager tracks request state internally

**Add to Manager:**
- Request coalescing: new request for same roost cancels in-flight work
- Block change debouncing: 500ms delay before triggering regen
- Priority queue bypass for `/ecotale graph <pos> regenerate` command
- Result delivery on main thread unchanged

Request lifecycle:
```
requestGeneration(roost, entityType):
    if roost has pending request: ignore (existing will pick up changes)
    if roost has in-flight generation: cancel it
    enqueue new generation request

onBlockChange(pos):
    for each roost within 128 blocks of pos:
        scheduleDebounced(roost, 500ms)

scheduleDebounced(roost, delay):
    if roost already scheduled: reset timer
    else: schedule requestGeneration after delay
```

**Validation:**
- Request generation, verify callback fires on main thread (log thread name in callback)
- Request generation twice rapidly (within debounce window), verify only one generation runs
- Start generation, request again mid-flight, verify first is cancelled and second completes
- Trigger 10 block changes within 100ms, verify single generation after debounce (not 10)
- Verify debug visualization still works with async generation
- Verify `/ecotale graph <pos> regenerate` bypasses queue and runs immediately

---

### Phase 5: Block Change Subscription

**Goal:** Detect world changes and trigger graph regeneration.

**Files:** Modify `RoostBlockEntity.java`, possibly event handler registration

**Details:**

**Approach:** Use Forge block events (`BlockEvent.BreakEvent`, `BlockEvent.EntityPlaceEvent`). These cover the common cases (player mining, player building, entity interactions) and are simpler than chunk section listeners.

Register a handler that:
```
onBlockChange(event):
    pos = event.getPos()
    for each active roost:
        if dist(pos, roost) <= 128:
            Manager.onBlockChange(roost)
```

Performance concern: If many roosts exist, iterating all of them per block change is expensive. For MVP, accept this. Document as known limitation for future spatial indexing.

RoostBlockEntity changes:
- Register with Manager on placement/load
- Unregister on removal/unload
- Remove periodic validation tick (replaced by event-driven refresh)

**Validation:**
- GameTest: Place block in cave, verify regeneration triggers after debounce, verify new graph has different cell/path structure
- GameTest: Break block outside 128-block range, verify no regeneration (graph unchanged)
- Manual: Enable visualization, break block within cave, verify visualization updates

---

### Phase 6: NBT Serialization

**Goal:** Persist graphs across world save/load.

**Files:** `Graph.java`, `Cell.java`

**Details:**

Serialization format per spec:
```
Graph:
  id: long (packed BlockPos)
  entityType: string (registry name)
  graphStart: long
  graphExits: LongArrayTag
  cells: ListTag<Cell>

Cell:
  boundsMin: long
  boundsMax: long
  hub: long
  paths: ListTag<Link>

Link:
  destination: long
  nextHop: long (0 if arrived, since BlockPos.ZERO is invalid cave position)
```

Deserialization:
- Parse all fields
- Reconstruct destinations set from graphStart + graphExits
- On any parse error: log warning, return null (triggers regeneration)

RoostBlockEntity integration:
- `saveAdditional()`: if graph exists, serialize it
- `load()`: attempt to deserialize graph, null is fine (will generate)

**Validation:**
- Save world with active roost
- Reload world, verify graph loads without regeneration (check logs)
- Use `/ecotale graph <pos> debug` to verify loaded graph matches pre-save
- Corrupt NBT manually, verify graceful regeneration

---

### Phase 7: Integration and Cleanup

**Goal:** Wire everything together, delete old code.

**Files:** Multiple integration points

**Details:**

EcoTaleBat changes:
- Implement `IFlyingMob` interface
- Replace FlowFieldNavigator usage with direct Graph queries
- Navigation becomes: `graph.getNextHop(currentHub, destination)`
- Track current hub, update when entering new cell

RoostBlockEntity changes:
- Replace `FlowFieldSolution` field with `Graph` field
- Update generation request calls
- Update NBT save/load calls

Delete old files:
- All `FlowField*` classes
- Old debug packet/renderer

Update imports and references throughout codebase.

**Validation:**
- GameTest: Spawn bat outside cave, verify it navigates to roost via graph (update existing `ReturnToRoostGameTest` to use new Graph)
- GameTest: Spawn bat at roost, trigger exit behavior, verify it follows graph to exit
- GameTest: Generate graph, place block to open new passage, wait for regen, verify graph includes new cells
- GameTest: Delete roost while bat has HOME set to it, verify no crash
- Manual: Full day/night cycle with multiple bats navigating simultaneously
- Manual: Block the only exit, observe generation failure and bat fallback behavior

---

## Testing Milestones

| After Phase | Testable Behavior |
|-------------|-------------------|
| 1 | Data structures compile, unit tests pass |
| 2 | Graph generates for test roost, paths are complete |
| 3 | Debug visualization shows graph structure, verifies generation |
| 4 | Generation runs async, callbacks fire correctly |
| 5 | Block changes trigger regeneration, visualization updates |
| 6 | Graphs persist across world reload |
| 7 | Bats navigate using new system end-to-end |

## Future Work

Explicitly deferred from this build:

- **Spatial indexing for block change listeners** — Current O(roosts) per block change is acceptable for small roost counts. Optimize if performance becomes an issue.
- **Ground-based navigation** — Spec explicitly limits to IFlyingMob. Ground nav is a different problem.
- **Graph merging** — Multiple roosts in the same cave could share graph data. Not implemented; each roost generates independently.
- **Partial regeneration** — When a block changes, we regenerate the entire graph. Could optimize to only update affected regions.
