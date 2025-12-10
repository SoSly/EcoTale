---
level: 1
status: stable
---

EcoTale transforms Minecraft's relationship with animals and crops from "resources to exploit" into "partners to steward." The most intuitive, humane actions should also be the most mechanically optimal actions.

A world where nothing happens without player input is not a living world—it's a museum. Environmental actors should have autonomous behaviors that reward attentive players—the kind who build farms for practical needs and aesthetics, not technical optimization.

## The Pillars

### 1. Autonomous Ecology

Environmental actors persist and evolve without player input. A population left alone will thrive; one disturbed will decline. The world changes whether or not the player is watching.

**Key constraint:** Autonomous changes must be observable and predictable. Players should anticipate how a system will evolve based on its visible state, without needing external documentation.

**What this means in practice:**
- Systems have internal state that changes over time, even when the player is absent
- Visual indicators show current state and trajectory
- Players learn system behavior through observation, not tutorials
- Neglect has consequences, not just active harm

### 2. Resource Exchange

Environmental actors provide tangible benefits in return for stewardship. Conservation is not its own reward—players get something concrete for being good stewards.

**Key constraint:** Benefits must be meaningful enough to influence player behavior. A purely cosmetic reward does not satisfy this pillar.

**What this means in practice:**
- Every environmental system offers something players want
- Benefits scale with engagement, not just time
- EcoTale's unique benefits cannot be obtained through exploitation
- Conservation creates ongoing value, not one-time rewards

### 3. Conservation Incentive

For the benefits EcoTale provides, the optimal strategy should also be the humane strategy. Stewardship outperforms exploitation.

**Key constraint:** The unique benefits of stewardship cannot be industrialized. Conservation is the only path to what EcoTale offers.

**What this means in practice:**
- For EcoTale benefits, sustainable approaches yield better long-term returns than exploitation
- Scale without care produces diminishing returns
- There is no optimal "ignore and harvest" strategy for EcoTale systems
- The best farms look like healthy ecosystems

### 4. Layered Engagement

Every system must support both passive and active stewardship. Passive stewardship—careful, non-disruptive interaction—should yield baseline benefits. Active stewardship—investment that improves conditions—should raise the ceiling.

**Key constraint:** Players should not be forced into active management to receive benefits, nor should passive care be the only path. Both modes must be viable; active investment must be worthwhile.

**What this means in practice:**
- Careful harvesting works without additional investment
- Active investment (habitat, food sources) provides additional returns
- Players choose their level of involvement based on goals, not because one path is broken
- Exploitation fails regardless of effort level; stewardship succeeds at any effort level

## Design Questions

When evaluating any environmental actor for EcoTale treatment, ask these questions in order. These questions are written with fauna in mind; flora systems may require adaptation.

### Understanding the Real-World Analog

1. What ecological role does this organism serve in the real world?
2. What behaviors or growth patterns define it?
3. What relationship do humans have with it? (Domestication, cultivation, pest control, resource harvesting, etc.)
4. Are there any real-world examples of conservation efforts benefitting humans?

### Understanding Vanilla Minecraft

1. How does vanilla Minecraft currently implement this?
2. What is the gap between its real-world role and its Minecraft implementation?
3. What player behaviors does vanilla incentivize? (Often: kill on sight, factory farm, or ignore entirely)

### Designing the EcoTale Version

1. What feedback loops could connect player behavior to environmental outcomes?
2. What tangible resource or benefit could this provide in exchange for stewardship?
3. How can humane treatment be made mechanically superior to exploitation?
4. What warning states exist between "healthy" and "gone"? (Graceful degradation, not sudden failure)
5. What passive stewardship options exist (careful, non-disruptive interaction)? What active stewardship options exist (investment that improves conditions)?

## Anti-Patterns

These are explicit non-goals. If a design starts heading here, stop and reconsider.

### Punishment-First Design
Do not add mechanics whose primary purpose is to punish players for "bad" behavior. Negative outcomes should be the natural absence of positive ones, not additional penalties stacked on top.

**Why it's tempting:** It is easy to confuse consequences with punishment—both cause negative outcomes. Punishment feels like teaching the player a lesson.

**Why it's wrong:** Punishment is the designer imposing values. Consequences emerge from the system. If a colony dies because you disturbed it, that is the autonomous ecology at work. If a colony dies AND you get a debuff AND angry villagers spawn, that is punishment stacked on top. Good consequences pass the "of course that happened" test—players understand why without needing to be told.

### Complexity Theater
Do not add systems that feel deep but do not create meaningful choices. If a mechanic can be reduced to "always do X" after five minutes of play, it is not adding depth. If a system requires external documentation to understand, it has failed the Autonomous Ecology pillar regardless of depth.

**Why it's tempting:** Complexity signals effort and seriousness. It feels like adding value.

**Why it's wrong:** The goal is legible autonomous systems. Complexity that obscures system state violates the core constraint that players should anticipate outcomes from visible information. If you need a spreadsheet to optimize, you have built a puzzle, not an ecology.

### Guilt Trips
Do not use sad animal sounds, accusatory messages, or emotional manipulation to discourage exploitation. The mechanics should speak for themselves.

**Why it's tempting:** Emotional impact is easy to create and feels meaningful. You want players to care about the animals.

**Why it's wrong:** If the mechanics are good, players will choose conservation because it is optimal, not because the game made them feel bad. Guilt is a crutch for weak design. Players who conserve because they feel guilty will resent the mod; players who conserve because it works will evangelize it.

### Vanilla Replacement
Do not make vanilla strategies impossible—just make EcoTale strategies better. Players who want to ignore these systems and play vanilla-style should be able to, they just will not benefit from the new content.

**Why it's tempting:** If you are confident your way is better, why allow the bad option? It feels cleaner to remove the wrong path entirely.

**Why it's wrong:** Players resent being forced. They embrace being persuaded. A player who chooses conservation over exploitation has bought into the vision. A player forced into it is just annoyed, and will uninstall.

## Success Criteria

EcoTale is achieving its goals when:

1. A new player can correctly predict how an unattended environmental system will evolve after observing it briefly.
2. Wherever players interact with ecological systems, humane decisions result in benefits that cannot be gained by exploitation.
3. Players can trace the connection between their actions (or inaction), the system's visible state, and the benefits they receive.
4. Every system offers both passive stewardship (careful interaction) and active stewardship (investment), and both yield meaningful benefits.
