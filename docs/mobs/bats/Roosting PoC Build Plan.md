---
level: 5
parent: "[[AI Behavior Architecture]]"
status: draft
---

This build plan implements the minimum roosting mechanics needed to run the Colony Size Aesthetics PoC. It is explicitly throwaway scaffolding—we expect to revisit and likely rewrite these systems after PoC results inform the design.

## Goal

Enable bats to choose individual hanging spots within a cave, fly to those spots, and rest there. This allows us to observe whether large colonies (15-20 bats) distribute naturally or clump unnaturally.

## Key Decisions

| Decision              | Value                  | Rationale                                                                          |
| --------------------- | ---------------------- | ---------------------------------------------------------------------------------- |
| HOME expiration       | 1200 ticks (1 minute)  | Long enough that bats don't constantly re-search, short enough to adapt to changes |
| Search radius         | 16 blocks from ROOST   | Covers a reasonable cave chamber without expensive searches                        |
| PerchSensor tick rate | 40 ticks (2 seconds)   | Fast enough to respond when HOME expires, slow enough to not spam searches         |
| Bat limit per block   | Skipped for PoC        | Will add later; for now just observe natural distribution                          |
| Guano max level       | 8 (like snow)          | Matches existing guano implementation                                              |

## Architecture Overview

```
PerchSensor
    ├── Finds valid hanging spots near ROOST
    └── Sets HOME memory (temporary, 1200 ticks)

RestAtRoost (modified)
    ├── Flies to HOME position (not ROOST)
    └── Rests with random X/Z offset

ReturnToRoost (modified)
    ├── Uses flow field to reach start cell
    └── Ends when entering final cell (not distance-based)

ExitCave (modified)
    └── Flies to start cell first, then follows flow field out

WakeIfRoostDistant (modified)
    └── Compares to HOME position (the hanging spot)
```

## Memory Renaming

Current code uses `HOME` for the RoostBlockEntity. We're renaming:

| Old Name                | New Name | Type                   | Purpose                                |
| ----------------------- | -------- | ---------------------- | -------------------------------------- |
| `MemoryModuleType.HOME` | `ROOST`  | Permanent              | The RoostBlockEntity position (colony) |
| (new)                   | `HOME`   | Temporary (1200 ticks) | The specific block this bat hangs from |

This means `ROOST` is the colony, `HOME` is where the individual bat sleeps. Semantically clearer.

## File Structure

```
src/main/java/org/sosly/ecotale/entities/ai/
├── MemoryModuleTypes.java          # Add ROOST, rename HOME references
├── sensor/
│   ├── HomeSensor.java             # Rename to RoostSensor, update logic
│   └── PerchSensor.java            # NEW: finds valid hanging spots, sets HOME
└── behavior/bat/
    ├── RestAtRoost.java            # Update to use HOME
    ├── ReturnToRoost.java          # Update termination condition
    ├── ExitCave.java               # Update to fly to start cell first
    └── WakeIfRoostDistant.java     # Update to compare HOME
```

## Phased Build Order

### Phase 1: Memory Renaming

**Goal:** Rename existing HOME usage to ROOST throughout the codebase.

**Files:**

- `MemoryModuleTypes.java` — Add `ROOST` as a new `GlobalPos` memory
- `EcoTaleBat.java` — Update save/load, `setHome` → `setRoost`, memory references
- `HomeSensor.java` → `RoostSensor.java` — Rename class, update to check ROOST memory
- `SensorTypes.java` — Update registration
- All behaviors — Replace `MemoryModuleType.HOME` with `MemoryModuleTypes.ROOST.get()`
- `RoostBlockEntity.java` — Update `spawnColony` to set ROOST instead of HOME

**Validation:**

- Build succeeds
- Grep for `MemoryModuleType.HOME` in bat-related code—should return no results (all references now use `MemoryModuleTypes.ROOST.get()`)
- Grep for `ROOST` and `HOME` usages—verify ROOST refers to the colony/RoostBlockEntity, HOME is not yet used (added in Phase 2)
- Existing bat spawning still works (bats have ROOST set)
- Bats still navigate using flow field (ROOST points to roost block entity)

### Phase 2: PerchSensor

**Goal:** Create sensor that finds valid hanging spots and sets HOME memory.

**Files:**

- `MemoryModuleTypes.java` — Add `HOME` as `GlobalPos` with no codec (transient, we won't persist it)
- `PerchSensor.java` — New sensor
- `SensorTypes.java` — Register new sensor
- `EcoTaleBat.java` — Add HOME to memory types, add sensor to sensor list

**PerchSensor logic:**

```
doTick(level, bat):
    // Only search if we don't have a valid HOME
    if bat has HOME memory:
        return

    roost = bat.getBrain().getMemory(ROOST)
    if roost is null:
        return

    // Search random blocks near roost
    candidates = []
    for i in 0..10:  // Try 10 random positions
        pos = roost.pos + random(-16, 16) for x, y, z
        if isValidHangingSpot(level, pos):
            candidates.add(pos)

    if candidates not empty:
        chosen = random(candidates)
        bat.getBrain().setMemoryWithExpiry(HOME, GlobalPos.of(dimension, chosen), 1200)

isValidHangingSpot(level, pos):
    // Block must be solid (this is where the bat hangs FROM)
    if not level.getBlockState(pos).isSolid():
        return false

    // Two air blocks directly beneath
    below1 = pos.below()
    below2 = pos.below(2)
    if not level.getBlockState(below1).isAir():
        return false
    if not level.getBlockState(below2).isAir():
        return false

    // Third block down: air or non-full guano
    below3 = pos.below(3)
    state3 = level.getBlockState(below3)
    if state3.isAir():
        return true
    if state3.getBlock() instanceof GuanoBlock:
        layers = state3.getValue(LAYERS)  // Like snow
        return layers < 8
    return false
```

**Validation:**

- Spawn bat near roost
- Observe that bat gains HOME memory pointing to a valid block
- HOME memory expires after 1200 ticks
- New HOME is selected after expiration

### Phase 3: Update RestAtRoost

**Goal:** Make bats fly to their HOME position (hanging spot) instead of ROOST.

**Files:**

- `RestAtRoost.java`

**Changes:**

- `checkExtraStartConditions`: Check for HOME memory presence, not ROOST
- `start`: Get hanging position from HOME, not ROOST
- `tick`: Navigate to HOME position
- `stop`: Position bat at HOME with X/Z offset, set resting

**Key change:** The bat now hangs from `HOME.pos().below()` (one block below the solid ceiling block).

**Note:** When a bat has no HOME memory, RestAtRoost won't start. The bat falls through to `FlyingRandomStroll` until PerchSensor finds a valid spot and sets HOME.

**Validation:**

- Bat with HOME memory flies to that specific block
- Bat rests hanging from the ceiling at HOME position
- Bat without HOME memory wanders until PerchSensor sets HOME

### Phase 4: Update ReturnToRoost

**Goal:** ReturnToRoost ends when bat enters the start cell, not based on distance to roost.

**Files:**

- `ReturnToRoost.java`

**Changes:**

- `checkExtraStartConditions`: Still requires ROOST, checks that bat is outside or not in start cell
- `canStillUse`: Return false when bat is in the flow field's start cell
- Remove distance-based termination

**Logic:**

```
canStillUse(level, bat, gameTime):
    if bat.isSleeping():
        return false

    roost = bat.getBrain().getMemory(ROOST)
    if roost is null:
        return false

    // Get flow field solution
    blockEntity = level.getBlockEntity(roost.pos())
    if not RoostBlockEntity:
        return false  // Can't navigate, stop trying

    solution = blockEntity.getFlowFieldSolution()
    if solution is null or failed:
        return false

    // Stop when we reach the start cell
    currentCell = FlowFieldCell.fromBlockPos(bat.blockPosition())
    return not currentCell.equals(solution.getStartCell())
```

**Validation:**

- Bat returns from outside, follows flow field
- ReturnToRoost ends when bat enters start cell
- PerchSensor finds a hanging spot, then RestAtRoost takes over

### Phase 5: Update ExitCave

**Goal:** ExitCave starts by flying to the start cell before following the flow field.

**Files:**

- `ExitCave.java`

**Changes:**

- Add initial navigation to start cell hub before following outward flow
- If bat is not in a cell with outward direction, navigate to start cell first

**Logic addition:**

```
tick(level, bat, gameTime):
    // ... existing rate limiting ...

    currentCell = FlowFieldCell.fromBlockPos(bat.blockPosition())
    direction = solution.getOutwardDirection(currentCell)

    if direction.isEmpty():
        // Not in flow field yet - navigate to start cell
        startCell = solution.getStartCell()
        startHub = solution.getHubPosition(startCell)
        if startHub.isPresent():
            navigateToHub(bat, startHub.get())
            return

    // ... existing flow-following logic ...
```

**Validation:**

- Bat at arbitrary position navigates to start cell first
- Once at start cell, follows flow field outward
- Bat eventually reaches exit and sets IS_OUTSIDE

### Phase 6: Update WakeIfRoostDistant

**Goal:** Wake bat if it drifts from its HOME position (hanging spot), not ROOST.

**Files:**

- `WakeIfRoostDistant.java`

**Changes:**

- Check HOME memory instead of ROOST
- Compare bat position to HOME.pos().below() (the hanging position)

**Validation:**

- Sleeping bat at HOME position stays asleep
- Sleeping bat pushed away from HOME wakes up
- Bat without HOME memory does not trigger this behavior

## Testing Milestones

| After Phase | Testable Behavior                                      |
| ----------- | ------------------------------------------------------ |
| 1           | Bats spawn with ROOST memory, navigate via flow field  |
| 2           | Bats gain HOME memory pointing to valid ceiling blocks |
| 3           | Bats fly to HOME and rest there                        |
| 4           | Bats return from outside and stop at start cell        |
| 5           | Bats exit cave by going to start cell first            |
| 6           | Bats wake if displaced from HOME                       |

## Future Work (Explicit Deferrals)

- **Bat limit per hanging spot:** Currently skipped; observe natural distribution first
- **Optimized hanging spot search:** Currently random sampling; may need spatial indexing
- **HOME persistence:** Currently transient; decide if bats should remember spots across save/load
- **Stress-responsive roost selection:** Agitated bats might pick different spots
- **Multiple hanging spot preferences:** Some bats prefer tighter clusters, others spread out

## PoC Readiness

After completing all phases, you can run the Colony Size Aesthetics PoC:

1. Create test caves with roost blocks
2. Spawn colonies of various sizes (4, 10, 20 bats)
3. Observe roosting distribution
4. Observe exit/return behavior
5. Record findings in the PoC document
