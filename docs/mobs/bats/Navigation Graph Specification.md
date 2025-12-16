---
level: 4
parent: "[[AI Behavior Architecture]]"
status: draft
---

The Navigation Graph subsystem generates and caches precomputed path data that guides entities through cave systems. It serves the AI Behavior Architecture by providing the navigation layer that enables entities to travel between their home and cave exits.

This specification covers the graph data structures, generation algorithm, caching mechanism, and query interface. It does not cover how entities use the graph—that belongs to the entity's AI specification. The boundary is: Navigation Graph answers "what's the next waypoint toward my destination?" and the entity AI decides when to ask and what to do with the answer.

**Current limitation:** This specification only supports flying entities (those implementing `IFlyingMob`). Ground-based navigation requires different constraints (floor detection, slope limits, jump distances) and is not yet supported. Attempting to generate a graph for a non-flying entity type will throw an error.

## Data Structures

### Cell

A navigation cell representing a cubic region of the world with routing information.

**What it represents:** A 4×4×4 block region that serves as a node in the navigation graph. Each cell knows how to route toward any destination by specifying which hub to visit next.

**Shape:**

- **bounds** — Axis-aligned bounding box defining the cell's 4×4×4 block region.
- **paths** — A map from destination hub positions to next hub positions. For each destination the graph knows about, this map stores which hub to visit next. The next hub is in a neighboring cell along the route. A null value means this cell contains the destination (you've arrived).

**Hubs:** A cell's hubs are not stored explicitly. They are the positions that neighboring cells' paths point to within this cell's bounds. A cell may have multiple hubs if different routes through it require different waypoints (e.g., entering from west vs entering from east).

**Invariants:**

- Every hub (any position appearing as a nextHop that falls within this cell's bounds) is an air block.
- Every hub has line-of-sight to both the boundary crossing from which it is approached AND the boundary crossing toward which it exits. This enables the entity to fly in, hit the hub, and fly out.
- For each destination that has a path entry, following path entries eventually reaches that destination (no cycles, no dead ends).
- A cell may have path entries for only some destinations if cave geometry prevents valid routes to others. Missing entries mean "no route exists."

### Graph

A complete navigation solution covering all reachable cells and all known destinations.

**What it represents:** Everything needed to navigate between any point in a cave system and any destination (the origin or any exit).

**Shape:**

- **id** — The BlockPos of the origin this graph was generated for. Serves as the unique identifier.
- **entityType** — The type of entity this graph was generated for. Must implement `IFlyingMob`. Passability checks during generation use this entity's dimensions, so a graph generated for bats may have paths a larger flying creature cannot traverse.
- **cells** — A map from cell bounds min corner to Cell objects. Given any position, the cell key is computed directly: `floor(coord / 4) * 4` for each axis.
- **destinations** — The set of all destination hub positions (the GraphStart hub plus all GraphExit hubs).
- **graphStart** — The hub position of the cell containing or beneath the origin. This is the "home" destination.
- **graphExits** — The set of hub positions for cells that serve as cave exits. These are "outside" destinations.

**Invariants:**

- The graphStart is always in the destinations set.
- All graphExits are in the destinations set.
- Cells may have path entries for some destinations but not others (see Phase 5: Incomplete paths).
- The graph contains all cells discovered during BFS, not just cells on the shortest path.
- All passability checks used the entityType's dimensions during generation.

**Persistence:** Graphs persist via NBT serialization with their origin. Graphs should not require regeneration merely because the world was reloaded.

**Serialization format:**

```
Graph {
    id: long                           // packed BlockPos of origin
    entityType: string                 // registry name (e.g., "ecotale:bat")
    graphStart: long                   // packed BlockPos of start hub
    graphExits: list<long>             // packed BlockPos of each exit hub
    cells: list<Cell>
}

Cell {
    boundsMin: long                    // packed BlockPos of min corner
    paths: list<Path>                  // routing table entries
}

Path {
    destination: long                  // packed BlockPos of destination hub
    nextHop: long                      // packed BlockPos of next hub, or 0 if arrived
}
```

Cell bounds max corner is not serialized—it is computed as `boundsMin + (3,3,3)` since all cells are 4×4×4.

The `destinations` set is not serialized—it is reconstructed from `graphStart` and `graphExits` on load.

On deserialization failure, treat as missing graph and regenerate.

### Origin

A block entity that owns and manages a navigation graph.

**What it represents:** The anchor point for navigation graph generation and the manager of graph lifecycle (generation, periodic refresh, persistence).

**Shape:**

- **position** — The BlockPos of the origin block in the world.
- **entityType** — The type of entity this origin generates graphs for. Must implement `IFlyingMob`.
- **graph** — The cached navigation graph, or null if not yet generated.

**Responsibilities:**

- Requesting graph generation when needed (on placement, on block changes within radius).
- Listening for block changes within the search radius.
- Persisting and loading the graph via NBT.

**Invariants:**

- An origin always has a position.
- An origin has at most one graph at a time.
- If a graph exists, its id matches the origin's position.

> **Bat-specific note:** For bats, the origin is the Roost block entity.

## Algorithm Description

### Graph Generation

Generation explores the cave from the origin, discovers all reachable cells and exits, then builds routing tables.

**Goal:** Given an origin position, produce a navigation graph that covers the entire reachable cave system and enables navigation to/from any point.

**Approach:** BFS flood-fill from the origin, keeping all discovered cells. Identify all exits. Build path tables by tracing from each cell to each destination.

#### Phase 1: Find a navigable starting point

The origin block may be placed against a ceiling or wall. The algorithm must find a nearby cell where navigation can begin.

1. Check if the origin's own cell contains navigable air space with at least one passable boundary to an adjacent cell.
2. If not, check adjacent cells for the same criteria.
3. If no navigable starting point exists, generation fails.

The start cell's hub is discovered using single-boundary hub discovery (see Hub Discovery: For destination cells). The hub only needs line-of-sight to one boundary crossing since entities arrive here, they don't pass through. This hub becomes the graphStart destination.

#### Phase 2: Discover all reachable cells

Expand outward from the start cell using breadth-first search. Do not stop at the first exit—continue until all reachable cells are discovered.

1. Maintain a queue of cells to explore and a map of visited cells.
2. For each cell, check boundary passability to neighbors (see Cell Passability: Liberal Exploration).
3. Record the parent cell for each discovered cell (needed for path building).
4. Record bidirectional adjacency: when a cell connects to a neighbor, add each to the other's neighbor list. Also record which boundary crossing position connects them. This temporary structure is needed for Phase 5.
5. Mark cells that contain exits (air blocks with sky access) but continue exploring.
6. Stop when: the queue is empty, or search bounds are exceeded, or timeout occurs.

**Search bounds:** Generation searches up to 128 blocks from the origin, measured using Minecraft's `dist3d` (Euclidean distance). Before adding a cell to the BFS queue, check that the cell's center is within range.

**Chunk loading:** Generation works with whatever chunks are currently loaded. If some chunks within the search radius are unloaded, those areas are simply not explored. This is acceptable—block changes trigger regeneration anyway, so missed exits will be discovered when those chunks load and cause block update events.

**What we keep:** All discovered cells, not just path cells. An entity anywhere in the cave should be able to navigate.

#### Phase 3: Extend exits outside the cave

For each cell identified as an exit during Phase 2:

1. From the exit cell, extend directly upward (+Y).
2. For each extension cell, check if it is "fully outside."
3. If not fully outside, continue extending upward.
4. The first fully-outside cell becomes an exit cell. Its hub is discovered using single-boundary hub discovery (see Hub Discovery: For destination cells)—it only needs line-of-sight to one boundary crossing since entities arrive here when exiting. This hub becomes a graphExit destination.

**Extension direction:** Always +Y (upward). Exit cells have sky access by definition, so extending upward will eventually reach open air. This handles the common case of vertical cave openings. Horizontal cave mouths (opening into a cliff face) will still work—the extension rises above the terrain until clear.

**Termination condition:** A cell is "fully outside" when:

- Every block in the cell (all 64) is air.
- Every column in the cell (all 16) has sky access.

This ensures entities have unobstructed space for transitioning between cave navigation and outdoor flight.

#### Phase 4: Build the destinations set

Collect all destination hubs:

- The graphStart hub (from Phase 1)
- All graphExit hubs (from Phase 3)

#### Phase 5: Build path tables

For each destination, run a BFS over the adjacency graph starting from the destination cell. For each cell visited during BFS, attempt to build a path entry pointing toward that destination.

**Algorithm for each destination:**

1. Start BFS from the destination cell. Mark it as visited with path entry = null (arrived).
2. For each cell C visited, examine its adjacent cells.
3. For each adjacent cell A not yet visited:
   - Let Q = the boundary crossing from A to C (recorded during Phase 2).
   - Let R = the boundary crossing from C toward the next cell on the path to destination (from C's already-computed path entry). For the destination cell itself, there is no R.
   - Attempt hub discovery in C: find a hub with LOS to both Q and R.
   - If hub discovery succeeds: record A's path entry as (destination → discovered hub in C). Mark A as visited.
   - If hub discovery fails: try alternative routes (see below).
4. Continue until BFS completes or all reachable cells have path entries.

**Alternative path algorithm:**

When the direct route from A through C fails hub discovery, search for an alternative path:

1. Run a secondary BFS from A over the adjacency graph, looking for any cell that already has a valid path entry for this destination.
2. For each candidate route found, attempt hub discovery for the A→X transition (where X is the first cell on the alternative path).
3. Use the first alternative that succeeds hub discovery.
4. If no alternative works, A has no path entry for this destination. This is acceptable—the cell remains in the graph but cannot route to this specific destination.

**Complexity:** O(cells × destinations) for the primary BFS passes, with additional work for alternative path searches when routes fail. In practice, most routes succeed on the first try.

**Incomplete paths:** A cell may have path entries for some destinations but not others. This occurs when cave geometry prevents any valid route. The cell remains in the graph—an entity at that position can still navigate to destinations it CAN reach, and the entity AI handles missing path entries gracefully.

**Result:** For cells that have a path entry, following entries eventually reaches the destination. Cells without a path entry for a destination cannot reach it through this graph.

### Hub Discovery

Hubs are discovered when establishing connectivity between cells. A hub serves as a waypoint for a specific route through a cell—it must have line-of-sight to both the entry boundary (where the entity comes from) and the exit boundary (where the entity goes next).

**Goal:** Given a cell with an entry boundary crossing Q and an exit boundary crossing R, find an air block within the cell that has line-of-sight to both Q and R.

**Discovery procedure:**

1. Check the cell's center block first. If it is air and has line-of-sight to both Q and R, use it as the hub.
2. If the center fails, search in expanding cubic shells around the center:
   - Shell 1: all blocks at distance 1 from center (up to 26 positions)
   - Shell 2: all blocks at distance 2 from center
   - Continue until the entire cell has been checked
3. Within each shell, iterate positions in Y-ascending order (bottom to top), then Z-ascending, then X-ascending. This ensures deterministic hub selection.
4. For each position, check:
   - The position is within cell bounds.
   - The position is air.
   - The position has line-of-sight to Q (entry boundary).
   - The position has line-of-sight to R (exit boundary).
5. Use the first position that passes all checks.

**When hub discovery runs:** During path table construction (Phase 5), when determining the route from cell A through cell B toward a destination. The entry boundary Q is the crossing from A to B. The exit boundary R is the crossing from B toward the next cell on the path.

**Multiple hubs:** A cell may accumulate multiple hubs if different routes through it require different waypoints. For example, a route entering from the west and exiting east might use hub H1, while a route entering from the north and exiting south might use hub H2. This is expected and correct.

**For destination cells:** Cells containing graphStart or graphExit only need line-of-sight to the entry boundary (there is no exit—the entity arrives). Use the simpler single-boundary check for these.

**Failure:** If no position has line-of-sight to both boundaries, the route through this cell is not viable. The path must use a different cell or the destination is unreachable via this path.

### Cell Passability

Cell passability is checked in two phases: liberal exploration during BFS, then strict validation during path construction.

#### During BFS (Phase 2): Liberal Exploration

Two adjacent cells are considered passable for exploration if:

1. There exists sufficient clearance on the shared boundary face for the entity's dimensions.
2. The destination cell contains navigable air space.

**Boundary checking procedure:**

The shared face between 4×4×4 cells is 4×4 = 16 blocks. Check in this order:

1. Check the center block of the face.
2. Check the four corner blocks of the face.
3. If none of the 5 quick checks pass, check remaining blocks exhaustively.

For each boundary block, verify sufficient clearance exists for the entity's dimensions. If any boundary position passes, record the cells as adjacent and store the boundary crossing position.

**Why liberal:** At BFS time, we don't know the full route through each cell. A cell might be traversable via one path (west→east) but not another (north→south). We explore everything and validate specific routes later.

#### During Path Construction (Phase 5): Strict Validation

When building a specific route A→B→C, validate that:

1. A valid hub exists in B with line-of-sight to both the A-B boundary crossing AND the B-C boundary crossing.
2. The entity can physically fly from the A-B crossing to the hub, and from the hub to the B-C crossing.

If no valid hub can be found, this specific route fails. The algorithm tries alternative paths through the adjacency graph. If no route works, cell A has no path entry for the destination via B.

**Line-of-sight:** Raycasts should account for entity dimensions, not just point-to-point visibility. An entity that cannot physically fit through a passage should not have that passage marked as passable.

### Navigation Query

Entities query the graph to get their next waypoint.

**Cell lookup (O(1)):**

Given any position, compute the cell key directly:

```
cellKey.x = floor(pos.x / 4) * 4
cellKey.y = floor(pos.y / 4) * 4
cellKey.z = floor(pos.z / 4) * 4
```

Look up `cellKey` in the cells map. If present, you have the cell. No iteration required.

**Navigation flow:**

1. Compute cell key from entity's current position.
2. Look up cell in the cells map.
3. Look up destination in that cell's paths map.
4. If result is null, entity has arrived.
5. If no entry exists for the destination, no route exists from this cell. Entity AI handles this gracefully.
6. Otherwise, result is the next hub—fly to it.
7. When entity reaches the hub, repeat from step 1 with new position.

**Position not in graph:** If the computed cell key has no entry in the cells map, the position is outside the graph. The entity's AI handles this gracefully (fallback navigation, wait for graph, etc.).

**No route to destination:** If the cell exists but has no path entry for the requested destination, no valid route was found during generation. The entity's AI handles this gracefully (try a different exit, wait, etc.).

### Change-Triggered Refresh

The world changes over time—players dig new passages, block exits, or open caves to the sky. Rather than validating the existing graph or regenerating on a timer, the origin listens for block changes and regenerates when the world changes.

**Why regenerate instead of validate:** Validation that catches all changes (blocked passages, new exits) requires the same world reads as generation. Regeneration is simpler and naturally discovers new exits that validation would miss.

**Trigger:** When a block changes within the origin's search radius (128 blocks), the origin requests regeneration. The preemptible queue coalesces rapid changes naturally—see Threading Model.

**Refresh behavior:** When the new graph completes, it replaces the old one. If generation fails (e.g., all exits now blocked), the origin retries with normal backoff. Entities using the old graph during regeneration continue uninterrupted until the new graph is ready.

## Threading Model

Graph generation is computationally expensive and must not block the game thread.

**Requirements:**

1. Generation runs on background threads, never on the main game thread.
2. Multiple generations can run concurrently (for different origins).
3. Results are delivered to the origin on the main thread.
4. Generation can be cancelled if a newer request arrives for the same origin.

**Thread pool:** A fixed pool of 2 worker threads handles generation requests.

**Preemptible queue:** The generation queue supports cancellation and coalescing:

- Each origin has at most one pending request in the queue.
- If a request arrives for an origin that already has a queued (not yet started) request, the new request is ignored—the pending one will pick up the changes.
- If a request arrives for an origin with an in-flight generation, the in-flight work is cancelled and a new request is queued.
- Cancelled generations stop at the next convenient checkpoint and discard partial results.

This naturally handles rapid block changes: many changes while a generation is in-flight result in one cancellation and one new generation, not a backlog.

**Timeout:** Generation aborts after 5 seconds.

**Priority requests:** Debug commands and forced regeneration bypass the normal queue.

## Failure Handling

### Generation Failures

| Failure            | Cause                                    | Response                             |
| ------------------ | ---------------------------------------- | ------------------------------------ |
| Invalid entityType | entityType does not implement IFlyingMob | Throw error; do not retry            |
| No start cell      | Origin surrounded by solid blocks        | Retry later; geometry may change     |
| No exit found      | Cave has no sky access within loaded chunks | Retry later; player may open an exit or chunks may load |
| Timeout            | Cave too large or complex                | Retry later with backoff             |

**Retry behavior:** Exponential backoff to avoid flooding the worker pool:

- Initial delay: 30 seconds
- Delay doubles with each consecutive failure
- Maximum delay: 5 minutes
- Backoff resets on successful generation

### Runtime Failures

**Entity position not in graph:** Query returns nothing. Entity AI uses fallback behavior.

**Origin block destroyed:** Graph is discarded. In-flight generation requests are cancelled or ignored.

### Refresh Failures

If periodic regeneration fails, the old graph remains in use. The origin continues retrying with normal backoff until regeneration succeeds.

## Debug Visualization

Debug visualization renders the graph structure for development and troubleshooting.

### What to Render

**Cell coloring (filled boxes around cell bounds):**

- Cyan — GraphStart cell (the cell containing or adjacent to the origin)
- Red — GraphExit cells (cells serving as cave exits)
- White — All other cells

**Hub visualization:**

- Purple boxes marking hub positions

Hubs are derived by collecting all `nextHop` positions from path entries and grouping them by which cell bounds they fall within. A cell may have multiple hubs rendered if different routes through it use different waypoints.

**Path visualization (lines between hubs):**

- Blue lines — Edges along paths toward the GraphStart destination
- Orange lines — Edges along paths toward GraphExit destinations

Each cell draws one blue line (to its next hop toward graphStart) and one orange line per exit (to its next hop toward each graphExit). Lines connect hub positions, showing the actual graph edges.

### Rendering Approach

The debug renderer receives graph data via a debug packet and draws:

1. For each cell: a colored wireframe box around the cell bounds
2. For each hub: a small purple box at the hub position (hubs derived from path nextHop values)
3. For each cell: lines from that cell to each of its next-hop hubs for each destination

**Performance consideration:** Large caves may have many cells. Consider limiting render distance or allowing filtering by destination.

### Debug Packet

The debug packet transmits graph data from server to client for visualization.

**Contents:**

- Graph ID (origin position)
- GraphStart hub position
- List of GraphExit hub positions
- For each cell: bounds and path entries (destination → nextHop)

Hubs are not transmitted separately—they are derived on the client by collecting unique nextHop positions.

**Triggering:** Debug visualization is toggled via command. When the user requests visualization, the server reads the installed graph on the main thread and sends the packet. Packets are only constructed on-demand—there is no ongoing cost when visualization is disabled.

## Integration Points

### Depends On

**Origin:** See data structure definition above. The origin owns the graph and manages its lifecycle.

**World/Level:** Generation reads block state and performs raycasts. Requires loaded chunks.

**Server tick scheduling:** Results must be delivered on the main thread.

### Provides To

**Entity AI:** Query interface for next waypoint toward a destination. For bats, this is the Bat AI system.

**Debug tools:** Commands for forcing regeneration, inspecting state, and visualizing graphs.

## Validation Criteria

### Correctness

**Path completeness:** For any cell that has a path entry for a destination, following those entries eventually reaches that destination.

**No cycles:** Following path entries always makes progress toward the destination.

**Hub validity:** Every hub (any nextHop position in the graph) is an air block with line-of-sight to both its entry boundary crossing and exit boundary crossing. This is verified during construction; boundaries are not stored in the final graph, so post-hoc validation requires regeneration.

**Partial destination coverage:** Cells may have path entries for some destinations but not others. Missing entries indicate no valid route exists due to cave geometry. This is correct behavior, not a bug.

### Performance

**Generation speed:** Typical caves complete in under one second. Complex caves complete within the timeout.

**Query speed:** Navigation lookup is O(1)—two map lookups.

**Storage:** Keeps all discovered cells, but bounded by search radius.

### Robustness

**Graceful degradation:** Failed graphs don't break the origin or navigating entities. The system retries.

**Thread safety:** Background generation never corrupts main-thread state.

**Persistence reliability:** Graphs survive world save/load without requiring regeneration.

## Open Questions

1. **Block change subscription scope:** Listening to all block changes within 128 blocks of every origin could be expensive if many origins exist. May need spatial indexing or other optimization if this becomes a performance issue.
