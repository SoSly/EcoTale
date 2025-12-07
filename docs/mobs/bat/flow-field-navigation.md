# Bat Flow Field Navigation System

Bats use a flow field navigation system that allows them to navigate from their roost to cave exits and back.

## Flow Field Structure

Each flow field will be stored on a Roost BlockEntity and consists of two 3D vector fields covering the cave volume:

1. An "outward" field where each cell stores a direction vector pointing toward the nearest cave exit
2. An "inward" field where each cell stores a direction vector pointing toward the roost

Generation should use flood-fill starting from the destination (exit for outward, roost for inward). Each cell's vector should point toward the neighbor closest to the destination. Fields should be stored sparsely—only cells that are air blocks inside the cave need data.

Resolution can be coarse, one cell per 4-8 blocks. Bats don't need precision.

During generation, the system should identify exit points—air blocks at the boundary between cave interior and the outside world. For each exit, an elevated position above the terrain at the cave mouth should be found (clear air with no blocks above). These elevated positions should be stored as the actual exit/entrance coordinates. This ensures bats have unobstructed line-of-sight during the handoff between flow field navigation and outdoor flight.

The inward flow field should extend to these elevated entrance points (a few blocks above terrain, outside the cave). This ensures returning bats can pick up the flow field smoothly when they arrive at the entrance, rather than having a gap between "flying toward entrance" and "following flow field."

## Flow Field Generation Threading

All flow field generation must occur on a dedicated worker thread, never on the main game thread. The generation process can be expensive and must not cause frame drops or server lag.

A single flow field generation queue should handle all requests. Roosts should join this queue when they need a flow field generated. The queue should process one roost at a time.

When generation completes:
- On success: the result should be passed back to the roost on the main thread
- On failure: the roost should rejoin the queue at the back to retry later

Reasons for failure might include: chunks not loaded, generation taking too long, or no valid exit found within search bounds.

## Flow Field Sharing

Bats access the flow field by looking up the Roost BlockEntity at their HOME position. No explicit sharing with bats is needed—once the roost has a solution, its bats can use it.

When a roost successfully generates a flow field solution, it should advertise the solution to other roosts that share the same starting cell.

Nearby roosts that receive an advertised solution should adopt it rather than generating their own, reducing redundant computation. They still need to validate that the solution works for their position (the inward field must be reachable from their location).

## Solution Validation

Roosts should not regenerate flow fields blindly. Before requesting generation, a roost must check whether it already has a valid solution.

A solution is valid if:
- The flow field data still exists and hasn't been garbage collected
- The exit point(s) are still air blocks (spot check, not full validation)
- The path from roost to exit hasn't been obviously blocked (optional: test a few cells along a rough line)

Only request new generation if validation fails. This prevents unnecessary recalculation when the world hasn't changed.

When geometry changes near a roost (block breaks or placements within the flow field bounds), the solution should be marked as needing revalidation.

## Bat Brain Integration

Bats use Minecraft's brain system. The existing ROOST and FORAGE activities handle all navigation phases. A memory tracks whether the bat is currently outside the cave.

**Activities:**

- ROOST: Handles both returning to the cave and resting at the roost
- FORAGE: Handles both exiting the cave and wandering outside

**Schedule:**

The bat should use the existing schedule: ROOST during daytime, FORAGE starting at dusk (12000), ROOST resuming before dawn (23500). The schedule handles time-based transitions only.

**Memories:**

- HOME: Already exists. Stores the roost BlockPos. Used to look up the Roost BlockEntity and its flow field.
- IS_OUTSIDE: New memory. Boolean flag. Set to true when the bat reaches an exit point, cleared when the bat reaches the roost. Determines which sub-behavior runs within each activity.

**FORAGE activity logic:**

- If IS_OUTSIDE is false or absent: bat is still in the cave. Follow the outward flow field toward the exit. When the bat reaches the elevated exit point, set IS_OUTSIDE to true.
- If IS_OUTSIDE is true: bat is outside. Wander randomly with obstacle avoidance.

**ROOST activity logic:**

- If IS_OUTSIDE is true: bat is outside and needs to return. Fly toward the elevated entrance point. When close enough, the bat will enter the inward flow field's range and begin following it. Once the bat reaches the roost vicinity, clear IS_OUTSIDE.
- If IS_OUTSIDE is false or absent: bat is in the cave near home. Execute existing RestAtRoost behavior.

**Edge case—schedule transition while in transit:**

If dawn arrives while the bat is still exiting (IS_OUTSIDE is false, activity switches to ROOST), the bat simply turns around and follows the inward flow field home. It never made it outside, which is fine.

If dusk arrives while the bat is still returning (IS_OUTSIDE is true, activity switches to FORAGE), the bat is outside and immediately begins wandering. It gets a short night but no logic breaks.

## Runtime Navigation

When following a flow field (exiting or returning through the cave), the bat should sample the appropriate flow field at its current position to get a direction vector. This direction should be used to select a waypoint some distance ahead, and the FlyingPathNavigator should move the bat toward that waypoint. The flow field determines where to go; the navigator handles the actual movement and collision avoidance.

When outside (IS_OUTSIDE is true and activity is FORAGE), the bat picks random directions, raycasts to avoid terrain collisions, and wanders freely. This phase requires no sophisticated navigation.

If a bat needs to navigate but its roost has no valid flow field yet, the navigation behavior should yield (do nothing that tick) until a solution becomes available.
