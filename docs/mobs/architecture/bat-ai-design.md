# Bat AI Architecture Design

## Overview

The system should replace vanilla bat's tick-based random movement with a Brain-powered AI system. EcoTale bats should be intelligent cave dwellers that roost in colonies, respond to environmental stress, and provide benefits to nearby farms.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        RoostBlockEntity                         │
│  - stress: int (accumulates/decays)                            │
│  - colonyState: enum (CALM, AGITATED, DISRUPTED, ABANDONED)    │
│  - colonySize: int (1-4 bats)                                  │
│  - flowField: FlowFieldSolution (shared navigation data)       │
│  - spawnBats() on first load / stress recovery                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ bats query stress + flow field via HOME memory
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         EcoTaleBat                              │
│  extends: net.minecraft.world.entity.ambient.Bat               │
│  - brain: Brain<EcoTaleBat> with sensors, memories, activities │
│  - accesses home roost via HOME memory (GlobalPos)             │
└─────────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┼─────────────────┐
            ▼                 ▼                 ▼
       ┌─────────┐      ┌──────────┐     ┌───────────┐
       │ Sensors │ ──▶  │ Memories │ ◀── │ Behaviors │
       └─────────┘      └──────────┘     └───────────┘
```

## Component Details

### EcoTaleBat Entity

**Purpose:** Brain-powered bat that extends vanilla Bat for compatibility.

**Inheritance:**
- Extends `net.minecraft.world.entity.ambient.Bat`
- Passes `instanceof Bat` checks
- Inherits sounds, hitbox, animations, no-push behavior

**Overridden Methods:**
- `brainProvider()` - returns Brain.Provider with custom sensors/memories
- `customServerAiStep()` - ticks the Brain instead of vanilla random movement
- `readAdditionalSaveData()` / `addAdditionalSaveData()` - persist HOME memory
- `defineSynchedData()` - add any client-synced state beyond vanilla flags

**Key Behaviors:**
- Should query home roost for colony stress state and flow field
- Should navigate using flow field when in cave
- Should forage/pollinate during active hours outside
- Should flee when colony becomes disrupted

### RoostBlockEntity

**Purpose:** Manages colony state, stress accumulation, and flow field navigation data. Single source of truth for bat populations and cave navigation.

**State:**
- `stress: int` - accumulates when players nearby, decays otherwise
- `colonyState: ColonyState` - derived from stress thresholds
- `colonySize: int` - number of bats (1-4), set at worldgen or recovery
- `flowField: FlowFieldSolution` - cached navigation data (see flow-field-navigation.md)

**Stress Formula:**
```
On tick (every 20 ticks):
  if player within detection range:
    stress += (light_level + 1) * (DETECTION_RANGE - distance)
  else:
    stress -= DECAY_RATE

  stress = clamp(stress, 0, MAX_STRESS)
  colonyState = calculateState(stress)
```

**Colony States:**
| State | Stress Range | Bat Behavior |
|-------|--------------|--------------|
| CALM | 0-25% | Normal activity, guano production |
| AGITATED | 25-50% | Warning sounds/particles, reduced activity |
| DISRUPTED | 50-75% | Bats cluster at roost, no guano |
| ABANDONED | 75-100% | Bats despawn, roost goes dormant |

**Spawning:**
- Worldgen roosts: spawn 1-4 bats on first chunk load
- Player-crafted roosts: start at max stress, spawn bats when stress reaches 0

**Flow Field Management:**
- Roosts request flow field generation via a threaded queue system
- Successful solutions are cached and shared with nearby roosts
- See flow-field-navigation.md for full details

### Vanilla Bat Suppression

**Purpose:** Prevent ambient bat spawns so roosts are the only bat source.

**Implementation:** Forge event listener

```
EntityJoinLevelEvent:
  if entity.type == EntityType.BAT && !(entity instanceof EcoTaleBat):
    event.cancel()
```

**Rationale:** Cleaner than mixin, explicit about intent, allows EcoTaleBat through.

### Brain System

**Purpose:** Organize bat AI into sensors (perception), memories (state), and behaviors (action).

**Why Brain over Goals:**
1. Home roost position is a natural Memory with codec serialization
2. Colony state sensing benefits from periodic Sensor evaluation
3. Daily cycles map to Brain's schedule system
4. Activities cleanly separate resting vs foraging behavior sets

**Architecture:**
```
Sensors (run periodically, update memories)
    │
    ▼
Memories (state the bat "knows")
    │
    ▼
Activities (groups of behaviors for different contexts)
    │
    ▼
Behaviors (individual actions, check memories for preconditions)
```

**Memories:**
| Memory | Type | Purpose |
|--------|------|---------|
| HOME | GlobalPos | Roost location, used to look up BlockEntity |
| IS_OUTSIDE | Boolean | Whether bat has exited the cave |
| FLY_TARGET | WalkTarget | Current navigation target |
| PATH | Path | Current pathfinding path |
| LOOK_TARGET | PositionTracker | Where the bat is looking |

**Sensors:**
| Sensor | Purpose |
|--------|---------|
| HomeSensor | Validates HOME still points to a valid roost block, clears if destroyed |

**Activities:**
| Activity | When | Behaviors |
|----------|------|-----------|
| ROOST | Daytime (0-12000, 23500-24000) | RestAtRoost, DropGuano, ReturnToRoost (if IS_OUTSIDE) |
| FORAGE | Nighttime (12000-23500) | ExitCave (if not IS_OUTSIDE), Wander (if IS_OUTSIDE) |

### Navigation

**Purpose:** Navigate caves to return to roost, exit caves at night, return home at dawn.

**Approach:** Flow field navigation with FlyingPathNavigation for local movement.

Cave navigation uses precomputed flow fields rather than runtime pathfinding. The flow field tells the bat which direction to go; FlyingPathNavigation handles the actual movement to nearby waypoints.

**See flow-field-navigation.md for complete details on:**
- Flow field structure (outward and inward fields)
- Threaded generation with queue system
- Solution sharing between nearby roosts
- Validation and regeneration
- Runtime sampling and waypoint selection
- IS_OUTSIDE memory transitions

**Summary:**
- Bat should sample flow field at current position to get direction
- Direction should be translated to a waypoint some distance ahead
- FlyingPathNavigation should move bat toward waypoint
- When bat reaches elevated exit point, IS_OUTSIDE should be set true
- When bat reaches roost, IS_OUTSIDE should be cleared

## Data Flow

### Stress Detection
```
RoostBlockEntity ticks
    → calculates stress based on nearby players
    → updates colonyState

EcoTaleBat's behaviors check stress
    → reads HOME memory to get BlockPos
    → queries RoostBlockEntity at that position
    → adjusts behavior based on colony state
```

### Daily Cycle
```
Brain schedule triggers activity change based on time:
    → 0-12000: ROOST activity
    → 12000-23500: FORAGE activity  
    → 23500-24000: ROOST activity

Within each activity, IS_OUTSIDE memory determines sub-behavior:

FORAGE activity:
    → IS_OUTSIDE false: follow outward flow field to exit
    → IS_OUTSIDE true: wander randomly outside

ROOST activity:
    → IS_OUTSIDE true: fly to entrance, follow inward flow field home
    → IS_OUTSIDE false: RestAtRoost, DropGuano
```

### Roost Loss
```
EcoTaleBat's HomeSensor ticks
    → queries HOME memory position
    → block is no longer a RoostBlock (broken/replaced)
    → clears HOME memory

DespawnIfHomeless behavior activates (CORE activity)
    → bat is discarded
```

## Registration

### Entity Registration
```java
BAT = ENTITIES.register("bat", () ->
    EntityType.Builder.of(EcoTaleBat::new, MobCategory.AMBIENT)
        .sized(0.5F, 0.9F)
        .clientTrackingRange(5)
        .build("bat"));
```

### Memory Module Registration
```java
// HOME is vanilla MemoryModuleType.HOME (GlobalPos)

FLY_TARGET = MEMORY_MODULE_TYPES.register("fly_target", () ->
    new MemoryModuleType<>(Optional.empty()));

IS_OUTSIDE = MEMORY_MODULE_TYPES.register("is_outside", () ->
    new MemoryModuleType<>(Optional.of(Codec.BOOL)));
```

### Sensor Registration
```java
HOME = SENSOR_TYPES.register("home", () ->
    new SensorType<>(HomeSensor::new));
```

### Activity Registration
```java
ROOST = ACTIVITIES.register("roost", () -> new Activity("roost"));
FORAGE = ACTIVITIES.register("forage", () -> new Activity("forage"));
```

### Schedule Registration
```java
BAT_DEFAULT = SCHEDULES.register("bat_default", Schedule::new);

// In common setup:
new ScheduleBuilder(BAT_DEFAULT.get())
    .changeActivityAt(0, Activities.ROOST.get())
    .changeActivityAt(12000, Activities.FORAGE.get())
    .changeActivityAt(23500, Activities.ROOST.get())
    .build();
```

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Entity approach | Extend Bat | instanceof compatibility, inherit sounds/visuals |
| AI system | Brain | Sensors for stress, memories for roost, schedules for daily cycle |
| Spawn model | Roost-only | Roosts as single source of truth, supports conservation theme |
| Stress ownership | RoostBlockEntity | Colony-level concern, not individual bat state |
| Vanilla suppression | Event cancellation | Simpler than mixin, explicit intent |
| Navigation | Flow fields | Precomputed cave navigation, avoids expensive runtime pathfinding |
| Activity count | Two (ROOST, FORAGE) | Schedule handles time; IS_OUTSIDE memory handles sub-phases |
| Flow field storage | RoostBlockEntity | Bats access via HOME memory lookup |

## File Structure

```
src/main/java/org/sosly/ecotale/
├── entities/
│   ├── EcoTaleBat.java
│   └── EntityRegistry.java
├── entities/ai/
│   ├── MemoryModuleTypes.java
│   ├── SensorTypes.java
│   ├── Activities.java
│   ├── Schedules.java
│   ├── sensor/
│   │   └── HomeSensor.java
│   └── behavior/bat/
│       ├── DespawnIfHomeless.java
│       ├── RestAtRoost.java
│       ├── DropGuano.java
│       ├── ExitCave.java
│       ├── ReturnToRoost.java
│       └── Wander.java
├── blocks/
│   ├── AbstractRoostBlock.java
│   └── RoostBlockEntity.java
├── navigation/
│   ├── FlowField.java
│   ├── FlowFieldSolution.java
│   ├── FlowFieldGenerator.java
│   └── FlowFieldQueue.java
└── events/
    └── EntityEvents.java
```

## Open Questions

1. **Colony size persistence** - stored on roost or derived from living bat count?
2. **Client sync needs** - what state needs to render on client (stress particles, etc)?
3. **Flow field memory limits** - how large can caves get before we need to cap search bounds?

## Related Documents

- **flow-field-navigation.md** - Complete specification for flow field navigation system
