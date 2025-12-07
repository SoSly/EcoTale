# Ideas

Incomplete concepts. These need design work before implementation.

## Crop Bolting

Crops left unharvested eventually bolt.

**Known elements:**
- Bolted crops yield fewer vegetables, more poisonous variants
- Crops harvested at optimal timing yield bonus produce
- Particles appear during optimal harvest window
- Shears freeze a crop at its current growth stage

**Unresolved:**
- Should bolting be a block state or an auto-harvest event?
- Block state enables spreading to adjacent farmland but creates mod compatibility issues
- Auto-harvest is simpler but loses spreading behavior
- Possible compromise: auto-harvest drops seeds that plant themselves on adjacent tilled soil
- Optimal harvest window duration needs tuning

## Predator Behavior

Vanilla predator AI causes extinction cascades. Wolves and foxes kill indiscriminately, prey animals have no effective defense or population recovery, and players cannot maintain mixed-species environments.

**The problem:**
- Predators hunt until prey is gone, then starve or wander off
- No territorial behavior limits hunting pressure
- Prey populations cannot recover once depleted
- Players who want wolves AND sheep must segregate them completely

**Possible directions:**
- Satiation mechanics (fed predators stop hunting)
- Territorial limits on hunting range
- Prey defensive behaviors and escape chances
- Population recovery mechanics for prey species

**Why this is deferred:**
This needs the same species-by-species design treatment bats received. Each predator and prey should be examined for ecological role, feedback loops, and player interaction opportunities. Attempting a quick fix would likely create new problems.

## Mushroom Farm Block

A block for fungus cultivation. Original concept: place block, assign species, produce mushrooms over time. Accelerate with decomposing food (rotten flesh, poisonous food, guano, etc).

**Problems:**
- No ecological basis
- No feedback loop
- No conservation incentive
- Flagged as Complexity Theater

## Roosts

Do we even need multiple roosts?  What if naturally occurring roosts just sample the adjacent blocks to determine what stone type they should mimic/drop on mining?  We shouldn't allow players to craft or manipulate roosts anyways; that destroys their value.  They can craft Bat Boxes to optimistically provide a new home for bats, instead.
