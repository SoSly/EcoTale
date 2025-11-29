# EcoTale Design Pillars

EcoTale transforms Minecraft's relationship with animals and crops from "resources to exploit" into "partners to steward." The most intuitive, humane actions should also be the most mechanically optimal actions.

A world where nothing happens without player input is not a living world—it's a museum. Environmental actors should have autonomous behaviors that reward attentive players.

## The Pillars

### 1. Feedback Loops

Player actions affect the environment, and environmental changes affect the player. This creates a living system rather than a static resource pool.

**Key constraint:** Negative consequences must feel like natural outcomes ("of course that happened") rather than arbitrary punishment. If a player does something harmful and suffers for it, they should understand *why* without needing a wiki.

### 2. Resource Exchange

Environmental actors provide tangible benefits in return for stewardship. Conservation is not its own reward—players get something concrete for being good stewards.

**Key constraint:** Benefits must be meaningful enough to influence player behavior. A purely cosmetic reward does not satisfy this pillar.

### 3. Conservation Incentive

The optimal strategy should also be the humane strategy. Cruel or exploitative approaches should be mechanically inferior to careful management.

**Key constraint:** This is not about punishing factory farms—it is about making thoughtful stewardship *better*. A small, well-managed operation should match or exceed a large, poorly-managed one.

## Design Questions

When evaluating any mob for EcoTale treatment, ask these questions in order:

### Understanding the Real Animal

1. What ecological role does this animal serve in the real world?
2. What behaviors define it? (Diet, social structure, habitat, daily patterns)
3. What relationship do humans have with this animal? (Domestication, pest control, resource harvesting, etc.)
4. Are there any real-world examples of conservation efforts benefitting human effort?

### Understanding Vanilla Minecraft

1. How does vanilla Minecraft currently use this mob?
2. What is the gap between the real animal's role and its Minecraft implementation?
3. What player behaviors does vanilla incentivize? (Often: kill on sight, factory farm, or ignore entirely)

### Designing the EcoTale Version

1. What feedback loops could connect player behavior to environmental outcomes?
2. What tangible resource or benefit could this mob provide in exchange for stewardship?
3. How can humane treatment be made mechanically superior to exploitation?
4. What warning states exist between "healthy" and "gone"? (Graceful degradation, not sudden failure)

## Anti-Patterns

These are explicit non-goals. If a design starts heading here, stop and reconsider.

### Punishment-First Design
Do not add mechanics whose primary purpose is to punish players for "bad" behavior. Negative outcomes should be the natural absence of positive ones, not additional penalties stacked on top.

### Complexity Theater
Do not add systems that feel deep but do not create meaningful choices. If a mechanic can be reduced to "always do X" after five minutes of play, it is not adding depth. If a system requires external documentation to understand, it has failed the Feedback Loops pillar regardless of depth.

### Guilt Trips
Do not use sad animal sounds, accusatory messages, or emotional manipulation to discourage exploitation. The mechanics should speak for themselves.

### Vanilla Replacement
Do not make vanilla strategies impossible—just make EcoTale strategies better. Players who want to ignore these systems and play vanilla-style should be able to, they just will not benefit from the new content.
