---
type: poc
validates: "[[Colony Health]]"
assumption: "A single large colony is more performant than multiple small colonies with equivalent total bats"
status: pending
---

## Goal

Determine whether worldgen should favor fewer large-capacity roosts or more small-capacity roosts from a performance perspective.

This matters because bat colonies tick continuously—brain updates, navigation queries, stress calculations. If 100 bats distributed across 25 small colonies perform worse than 100 bats in 5 large colonies, that constrains worldgen density. Conversely, if small colonies are equivalent or better (due to reduced per-colony overhead), we have more flexibility.

The Colony Health architecture needs this answer before committing to worldgen parameters and colony spacing rules.

## Approach

Build a minimal test harness that spawns bat entities with brain ticking enabled but no actual navigation or world interaction.

**Test configurations:**
- Baseline: 0 bats (establish server idle TPS)
- Config A: 1 colony of 20 bats
- Config B: 5 colonies of 4 bats each
- Config C: 1 colony of 100 bats
- Config D: 25 colonies of 4 bats each

**What to measure:**
- Server TPS (ticks per second) over 60-second sample
- MSPT (milliseconds per tick) average and 95th percentile
- Memory allocation rate (if accessible via spark or similar)

**Test conditions:**
- Flat world, no other entities
- Player stationary within render distance of all colonies
- Run each configuration 3 times, average results

**Tools:**
- Spark profiler for TPS/MSPT
- `/tick` command for manual verification
- Custom command to spawn test configurations

## Success Criteria

Comparing equivalent total bat counts (Config A vs B, Config C vs D):

| Outcome | MSPT Difference | Interpretation |
|---------|-----------------|----------------|
| Excellent | < 5% difference | Colony count doesn't matter; optimize for gameplay feel |
| Good | 5-15% difference | Slight preference for fewer colonies at scale |
| Acceptable | 15-30% difference | Meaningful preference; factor into worldgen |
| Failure | > 30% difference | Strong constraint on worldgen; must limit colony density |

Additionally, absolute performance matters:

| Outcome | MSPT at 100 bats |
|---------|------------------|
| Excellent | < 10 MSPT |
| Good | 10-20 MSPT |
| Acceptable | 20-35 MSPT |
| Failure | > 35 MSPT (threatens 20 TPS) |

## Results

*Not yet run.*

## Conclusions

*Pending results.*
