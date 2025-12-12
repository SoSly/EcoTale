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

**What it represents:** A 4×4×4 block region that serves as a node in the navigation graph. Each cell contains a navigable waypoint (hub) and knows how to route toward any destination in the graph.

**Shape:**

- **bounds** — Axis-aligned bounding box defining the cell's 4×4×4 block region.
- **hub** — A specific air block within the cell that serves as the navigation waypoint.
- **paths** — A map from destination hub positions to next hub positions. For each destination the graph knows about, this map stores which hub to visit next. A null value means this cell contains the destination (you've arrived).

**Invariants:**

- The hub is always an air block within the cell's bounds.
- The hub has line-of-sight to the entry point from which the cell was discovered (which connects to the parent cell's hub via the boundary crossing).
- Every destination in the graph appears as a key in the paths map.
- Following the path entries from any cell eventually reaches the destination (no cycles, no dead ends).

### Graph

A complete navigation solution covering all reachable cells and all known destinations.

**What it represents:** Everything needed to navigate between any point in a cave system and any destination (the origin or any exit).

**Shape:**

- **id** — The BlockPos of the origin this graph was generated for. Serves as the unique identifier.
- **entityType** — The type of entity this graph was generated for. Must implement `IFlyingMob`. Passability checks during generation use this entity's dimensions, so a graph generated for bats may have paths a larger flying creature cannot traverse.
- **cells** — A map from hub positions to Cell objects. Keyed by hub position for O(1) lookup during navigation.
- **destinations** — The set of all destination hub positions (the GraphStart hub plus all GraphExit hubs).
- **graphStart** — The hub position of the cell containing or beneath the origin. This is the "home" destination.
- **graphExits** — The set of hub positions for cells that serve as cave exits. These are "outside" destinations.

**Invariants:**

- The graphStart is always in the destinations set.
- All graphExits are in the destinations set.
- Every cell in the graph has a route entries for every destination.
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
    boundsMax: long                    // packed BlockPos of max corner
    hub: long                          // packed BlockPos
    paths: list<Link>
}

Link {
    destination: long                  // packed BlockPos of destination hub
    nextHop: long                      // packed BlockPos of next hub, or 0 if arrived
}
```

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

1. Check if the origin's own cell contains a reachable hub.
2. If not, check adjacent cells.
3. If no navigable starting point exists, generation fails.

The hub of the starting cell becomes the graphStart destination.

#### Phase 2: Discover all reachable cells

Expand outward from the start cell using breadth-first search. Do not stop at the first exit—continue until all reachable cells are discovered.

1. Maintain a queue of cells to explore and a map of visited cells.
2. For each cell, find its hub and check passability to neighbors.
3. Record the parent cell for each discovered cell (needed for path building).
4. Record bidirectional adjacency: when a cell connects to a neighbor, add each to the other's neighbor list. This temporary structure is needed for Phase 5.
5. Mark cells that contain exits (air blocks with sky access) but continue exploring.
6. Stop when: the queue is empty, or search bounds are exceeded, or timeout occurs.

**Search bounds:** Generation searches up to 128 blocks from the origin, measured using Minecraft's `dist3d` (Euclidean distance). Before adding a cell to the BFS queue, check that its hub position is within range.

**Chunk loading:** Generation works with whatever chunks are currently loaded. If some chunks within the search radius are unloaded, those areas are simply not explored. This is acceptable—block changes trigger regeneration anyway, so missed exits will be discovered when those chunks load and cause block update events.

**What we keep:** All discovered cells, not just path cells. An entity anywhere in the cave should be able to navigate.

#### Phase 3: Extend exits outside the cave

For each cell identified as an exit during Phase 2:

1. From the exit cell, extend directly upward (+Y).
2. For each extension cell, check if it is "fully outside."
3. If not fully outside, continue extending upward.
4. The first fully-outside cell's hub becomes a graphExit destination.

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

For each cell in the graph, for each destination:

1. If this cell's hub equals the destination, store null (arrived).
2. Otherwise, trace toward the destination using parent relationships from BFS.
3. Store the next hub along that path.

**Path tracing approach:** BFS recorded parent relationships pointing toward the start. For the graphStart destination, following parents directly gives the path. For graphExit destinations, we need the reverse direction.

**Recommended approach:** Run a BFS from each exit over the already-discovered graph. For each cell visited, record "next hop toward this exit." This is O(cells × exits).

Note: The reverse BFS requires knowing which cells connect to which. During the initial BFS (Phase 2), track adjacency relationships as a temporary working structure. Use this for the reverse passes in Phase 5, then discard it—once paths are built, adjacency is no longer needed.

The result must be: for any cell and any destination, paths.get(destination) returns the next hub toward that destination.

### Hub Discovery

Each cell needs a hub—a specific air block that serves as the navigation waypoint.

**Goal:** Given a cell, find an air block within it that has line-of-sight to the entry point (the position from which the cell was reached during BFS).

**Discovery procedure:**

1. Check the cell's center block first. If it is air and has line-of-sight to the entry point, use it as the hub.
2. If the center fails, search in expanding cubic shells around the center:
   - Shell 1: all blocks at distance 1 from center (up to 26 positions)
   - Shell 2: all blocks at distance 2 from center
   - Continue until the entire cell has been checked
3. Within each shell, iterate positions in Y-ascending order (bottom to top), then Z-ascending, then X-ascending. This ensures deterministic hub selection.
4. For each position, check:
   - The position is within cell bounds.
   - The position is air.
   - The position has line-of-sight to the entry point.
5. Use the first position that passes all checks.

**For the start cell:** The entry point is the origin position.

**For subsequent cells:** The entry point is the boundary crossing position from which this cell was reached during BFS.

**Failure:** If no position passes all checks, the cell has no navigable hub and is not added to the graph.

### Cell Passability

Two adjacent cells are passable if an entity of the graph's entityType can travel between them.

**Requirements:**

1. Both cells have navigable hubs.
2. There exists sufficient clearance on the shared boundary face for the entity's dimensions.
3. The boundary crossing has line-of-sight to both cells' hubs, accounting for entity size.

**Boundary checking procedure:**

The shared face between 4×4×4 cells is 4×4 = 16 blocks. Check in this order:

1. Check the center block of the face.
2. Check the four corner blocks of the face.
3. If none of the 5 quick checks pass, check remaining blocks exhaustively.

For each boundary block:

1. Verify sufficient clearance exists for the entity's dimensions.
2. Raycast from source hub to boundary, using the entity's hitbox—must be unobstructed.
3. Raycast from boundary to destination hub, using the entity's hitbox—must be unobstructed.

If any boundary position passes all checks, the cells are passable.

**Line-of-sight:** Raycasts should account for entity dimensions, not just point-to-point visibility. An entity that cannot physically fit through a passage should not have that passage marked as passable.

### Navigation Query

Entities query the graph to get their next waypoint.

**Entry (once, when entity starts navigating):**

1. Linear scan over cells to find which one contains the entity's current position.
2. Entity stores that cell's hub as its current target.

The linear scan is O(n) where n is the number of cells, but n is bounded by the 128-block search radius—typically a few hundred cells at most. This is acceptable for a one-time lookup when navigation begins.

**Per-tick navigation (O(1)):**

1. Look up current hub in the cells map.
2. Look up destination in that cell's paths map.
3. If result is null, entity has arrived.
4. Otherwise, result is the next hub—pathfind to it.
5. When entity reaches the hub, repeat from step 1 with new position.

**Position not in graph:** If an entity's position doesn't match any cell, the query returns nothing. The entity's AI handles this gracefully (fallback navigation, wait for graph, etc.).

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

- Purple boxes marking each cell's hub position

**Path visualization (lines between hubs):**

- Blue lines — Edges along paths toward the GraphStart destination
- Orange lines — Edges along paths toward GraphExit destinations

Each cell draws one blue line (to its next hop toward graphStart) and one orange line per exit (to its next hop toward each graphExit). Lines connect hub to hub, showing the actual graph edges.

### Rendering Approach

The debug renderer receives graph data via a debug packet and draws:

1. For each cell: a colored wireframe box around the cell bounds
2. For each cell: a small purple box at the hub position
3. For each cell: lines from the hub to next hops for each destination

**Performance consideration:** Large caves may have many cells. Consider limiting render distance or allowing filtering by destination.

### Debug Packet

The debug packet transmits graph data from server to client for visualization.

**Contents:**

- Graph ID (origin position)
- GraphStart hub position
- List of GraphExit hub positions
- For each cell: bounds, hub position, and next-hop positions for each destination

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

**Path completeness:** From any cell, following path entries for any destination eventually reaches that destination.

**No cycles:** Following path entries always makes progress toward the destination.

**Hub validity:** Every hub is an air block with line-of-sight to at least one neighbor's hub.

**Destination coverage:** Every cell has path entries for every destination in the graph.

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
