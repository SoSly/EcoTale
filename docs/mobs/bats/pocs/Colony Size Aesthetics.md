---
type: poc
validates: "[[Colony Health Architecture]]"
assumption: Large colonies can distribute bats spatially while maintaining functional navigation and natural-looking behavior
status: pending
---

## Goal

Determine whether large colonies (15-20+ bats) can work mechanically and aesthetically, or whether smaller colonies (4-6 bats) produce better results.

This matters for two reasons:

1. **Mechanical:** Bats sleep on surfaces near the roost, not on the roost block itself. A 20-bat colony needs 20 valid sleeping positions within range. Those bats also need unobstructed paths to the flow field for cave exit/entry. If bats end up clumped, stuck, or pathing through each other, large colonies are mechanically broken.

2. **Aesthetic:** Real bat colonies have a characteristic look—bats clustered but not stacked, emerging in streams at dusk, returning in waves at dawn. Does a 20-bat colony capture that feel, or does it look like a laggy mob farm? Do 5 small colonies distributed through a cave system feel more natural than one big colony?

The Colony Health architecture needs this answer to set MAX_ROOST_SIZE and inform worldgen density.

## Approach

Build test caves with roost blocks and observe bat behavior across colony sizes.

**Test configurations:**
- Config A: 1 roost with 20 bats in a medium cave chamber
- Config B: 5 roosts with 4 bats each, distributed through connected chambers
- Config C: 1 roost with 20 bats in a tight cave (stress-test spacing)

**What to observe:**

*Roosting behavior:*
- Do all bats find valid sleeping positions?
- How spread out are they? Clustered naturally or artificially uniform?
- Do any bats fail to settle and fly continuously?

*Exit behavior (dusk):*
- Do bats exit in a natural-looking stream or all at once?
- Do they collide/bunch at the flow field entrance?
- How long does it take for all bats to clear the cave?

*Return behavior (dawn):*
- Do bats return in waves or cluster at the entrance?
- Do they path correctly to sleeping positions?
- Any bats that fail to return and get stuck outside?

**Recording:**
- Screen capture each configuration at dusk and dawn
- Note any mechanical failures (stuck bats, pathing failures)
- Subjective assessment: which configuration "feels like bats"

## Success Criteria

This is primarily qualitative, but with concrete observables:

**Mechanical (pass/fail):**

| Criterion | Pass | Fail |
|-----------|------|------|
| All bats find sleeping positions | 100% settle within 30 seconds | Any bat fails to settle |
| Exit navigation works | All bats exit within 2 minutes of dusk | Any bat stuck in cave at full night |
| Return navigation works | All bats return within 2 minutes of dawn | Any bat stuck outside at full day |
| No persistent collisions | Bats don't cluster in one block | 3+ bats occupying same block-space |

**Aesthetic (subjective rating 1-5):**

| Aspect | Excellent (5) | Good (3-4) | Acceptable (2) | Failure (1) |
|--------|---------------|------------|----------------|-------------|
| Roosting appearance | Looks like a real bat colony | Looks plausible | Looks gamey but acceptable | Looks broken |
| Exit behavior | Natural streaming emergence | Reasonable flow | Clumpy but functional | Mob farm vibes |
| Return behavior | Gradual settling | Orderly return | Mechanical but works | Chaotic mess |

**Overall assessment:**
- If Config A scores 3+ on all aesthetic aspects and passes all mechanical checks: large colonies are viable
- If Config B consistently scores higher aesthetically: prefer smaller colonies
- If Config C fails mechanical checks: large colonies need spacing constraints

## Results

*Not yet run.*

## Conclusions

*Pending results.*
