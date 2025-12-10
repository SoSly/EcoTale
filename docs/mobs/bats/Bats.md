Bats are a keystone species in many ecosystems. They serve as pollinators, seed dispersers, and—most relevantly—voracious insect predators. A single bat can eat thousands of insects per night, many of which would otherwise damage crops.

Bats are social creatures that roost in colonies, returning to the same location day after day. They are nocturnal, sleeping during the day and emerging at dusk to hunt. They navigate and hunt using echolocation.

Humans have historically viewed bats with suspicion, but agricultural communities increasingly recognize their value. Texas cotton farmers, for example, have found that healthy bat populations provide natural pest control worth millions of dollars annually in reduced pesticide costs. Bat guano is also a valuable fertilizer, historically significant enough to have sparked international disputes.

## Vanilla Minecraft

In vanilla, bats are purely ambient. They spawn in caves, fly around erratically, and serve no mechanical purpose. Players have no reason to interact with them and no consequence for killing them.

Vanilla incentivizes ignoring bats entirely. They provide nothing, threaten nothing, and mean nothing.

## Bats in EcoTale

In EcoTale, bats become partners in your farming operation. A healthy colony near your farm means better crop yields, free fertilizer, and safer nights. In exchange, you respect their home—visit briefly to harvest guano, but don't light up their cave or linger too long.

Players who go further can invest in their colony's success. Crops planted nearby attract the insects bats eat. A water source gives them somewhere to drink. These improvements don't just keep the colony alive—they make it thrive.

The player who discovers a bat colony near their base has found something valuable worth protecting—and worth improving.

### Systems Overview

| System | What It Is | Player Benefit |
|--------|------------|----------------|
| AI Behavior | Autonomous bat actions: foraging, roosting, visiting farms, fleeing | Bats feel alive; behavior communicates colony state |
| Guano | Layered blocks that accumulate below roosts | Bone meal equivalent; crafts into brown dye; smelts into gunpowder |
| Pollination | Bats visit nearby farms at night | Accelerated crop growth |
| Seed Discovery | Bats occasionally plant crops on empty farmland | Access to crop types you haven't found yet |
| Mob Dampening | Reduced hostile spawns in bat territory | Safer nights; no phantoms |
| Colony Health | Observable state that reflects disturbance and care | Feedback on whether you're helping or hurting |
| Bat Boxes | Craftable roosts with reduced sensitivity to player activity | Enables colonies in existing worlds; trades peak output for stability |

### Foundation Mapping

How bats satisfy each design pillar:

**Autonomous Ecology:** Colonies persist and evolve without player input. Colonies degrade from disturbance and recover in peace. Colony state—thriving, calm, agitated, disrupted, abandoned—changes whether or not the player is watching. All states are observable through bat behavior.

**Resource Exchange:** Bats provide guano, crop pollination, seed discovery, and pest control. Players provide undisturbed habitat, and optionally food and water sources. Both sides give something concrete.

**Conservation Incentive:** There's nothing to exploit. Bats can't be farmed, bred, or forced to produce faster. Disturbance causes colony loss; stewardship enables steady returns. Automation and industrial approaches create disturbance that undermines productivity.

**Layered Engagement:** Passive stewardship (restraint) yields baseline benefits. Active stewardship (crops, bat boxes) raises the ceiling. Neither path is broken; both are viable; investment is worthwhile.

### Feedback Loops

**Player → Environment (Harmful):**
- Spending time near a roost disturbs the colony
- Lighting a cave disturbs the colony
- Prolonged disruption causes the colony to abandon their roost

**Player → Environment (Helpful):**
- Crops near the roost attract insects, improving colony health
- Bat boxes provide stable shelter with reduced stress from player activity

**Environment → Player:**
- Healthy colonies produce guano you can harvest
- Thriving colonies produce more guano
- Bats visiting your farm accelerate crop growth
- Bat territory is safer at night (fewer hostile spawns, no phantoms)
- Stressed colonies stop providing benefits
- Abandoned roosts provide nothing

### Resource Exchange

**What bats provide:**
- **Guano** - accumulates below roosts; functions as bone meal, crafts into brown dye, smelts into gunpowder
- **Crop pollination** - bats visiting farms have a chance to advance crop growth
- **Seed discovery** - bats can cause crops to spawn on empty farmland, potentially introducing crops you haven't found yet
- **Pest control** - reduced hostile mob spawns, spider deterrence, phantom suppression in bat territory

**What players provide:**
- Undisturbed darkness (passive)
- Nearby crops (active)
- Bat boxes as alternative shelter (active)

### Conservation Incentive

**Why stewardship beats exploitation:**

There's nothing to exploit. You can't farm bats, breed bats, or force bats to produce more. You can only create conditions where they thrive or conditions where they leave.

A player who respects their colony gets steady guano production, passive crop bonuses, and safer nights. A player who disrupts their colony—whether through carelessness or attempted optimization—loses all of that.

**Why stewardship beats a large farm:**

Bat visits spread across your total farm area. A small farm near a healthy colony receives more attention per crop than a massive farm with the same colony.

**Why active investment has limits:**

Habitat improvements help, but placement matters. Crops too close to the roost disturb the colony; crops too far away won't be found during nightly foraging. Machinery and automation create disturbance. The optimal bat habitat looks like a carefully planned farm, not a compact industrial operation.

### Layered Engagement

**Passive stewardship (the baseline):**

A player who simply respects the colony—harvesting guano briefly and leaving, keeping the cave dark—receives steady benefits. No investment required beyond restraint. This is the floor: reliable but modest.

**Active stewardship (raising the ceiling):**

Players who invest in their colony's habitat see improved returns:

- **Food sources:** Crops planted within foraging range attract insects. Well-fed colonies produce more guano and grow faster.
- **Bat boxes:** Crafted roosts offer stability. They're less sensitive to player proximity than natural roosts, making them easier to manage. However, natural roosts remain more productive—bat boxes trade peak output for reliability.

Active investment cannot replace passive care. A colony with perfect habitat but constant disturbance will still fail. The layers stack: restraint first, investment second.

### Observable States

Colonies have visible states that reflect their health. Players who pay attention can read the system at a glance.

| State | What You See | What It Means |
|-------|--------------|---------------|
| Thriving | More bats, increased activity | Colony is growing; enhanced guano production |
| Calm | Normal behavior, idle animations | Colony is healthy, producing guano |
| Agitated | Squeaking, particle effects | You're pushing it—back off soon |
| Disrupted | Bats cluster together, face threats | No guano production, colony at risk |
| Abandoned | There are no bats | Colony is gone |

Colonies don't fail suddenly—they degrade through these states, giving players time to correct course. Likewise, colonies don't thrive by accident—reaching the top requires active investment in habitat.

### Recovery

Abandoned roosts are not permanently lost. When an abandoned roost has been undisturbed long enough, a new colony will eventually spawn. This applies to both natural roosts and bat boxes.

Active investment speeds recovery. An abandoned roost with nearby food and water sources will repopulate faster than a bare cave.

## Retrofit System

For existing worlds without naturally-generated colonies:

- Players craft bat boxes
- New bat boxes start abandoned—they need time to settle
- Place in suitable locations (dark, sufficient space, away from other colonies)
- When a bat box has been undisturbed long enough, a colony spawns
- Providing food nearby speeds colonization

This shifts the fantasy from "discover and protect" to "build and cultivate."

## Balance Targets

Qualitative goals that guide implementation tuning.

### Colony Stability

- Brief harvesting visits should not trigger agitation
- Permanent light installations near roosts should cause slow but inevitable colony loss
- Dark caves recover faster than lit ones
- Losing a colony should sting—recovery takes real time
- Recovery should feel achievable, not hopeless; players should be encouraged to check back on abandoned roosts rather than write them off

### Guano Economy

Guano-smelted gunpowder provides a sustainable source for casual and peaceful players, but should not replace creeper farms for technical players.

- Colony size limits total output per roost
- Thriving colonies produce more, but there's still a ceiling
- The colony health system prevents "always be harvesting" strategies

### Farm Scaling

Bat pollination naturally balances farm size. Visits spread across total farm area, so small farms receive more attention per crop than large farms served by the same colony. Combined with other farming systems, this rewards engagement without explicit penalties for larger builds.

### Mob Dampening

Bat territory should feel noticeably safer at night—enough that players feel comfortable observing nocturnal behavior—but not so strong that hostile mobs are eliminated entirely. Benefits scale with colony health and size.
