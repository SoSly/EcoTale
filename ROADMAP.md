# Roadmap

## Bats

The first EcoTale release focuses on bats as a proof-of-concept for the mod's core design pillars: feedback loops, resource exchange, and conservation incentives.

### Core Loop
Establish bat colonies as functional ecosystem actors. Players discover colonies, harvest guano, and learn the stress system through natural feedback.

- [x] Guano blocks exist with layers similar to snow blocks
- [x] Roost blocks exist for every stone type (shaped recipe: stone + 8 guano)
- [x] Roosts are generated during worldgen; guano is generated below them
- [ ] Bat AI overhaul
  - [x] EcoTaleBat entity with Brain system (extends vanilla Bat for instanceof compatibility)
  - [x] Memory and sensor registration (home roost, colony state)
  - [ ] FlyingPathNavigation for cave navigation
  - [x] Daily cycle behaviors (rest at roost during day, forage at night, return at dawn)
- [ ] Roost colony management
  - [x] Suppress vanilla bat spawns; roosts are the only bat source
  - [x] RoostBlockEntity spawns and tracks colony (1-4 bats)
  - [x] Bats query home roost for state; homeless bats despawn
- [ ] Stress system and colony states
  - [ ] Stress accumulates from player proximity, decays when alone
  - [ ] Colony states (CALM → AGITATED → DISRUPTED → ABANDONED) with visual/audio feedback
  - [ ] ABANDONED colonies despawn; roost goes dormant until stress recovers
- [x] Guano production and harvesting
- [x] Guano uses (bone meal, brown dye, gunpowder)

### Ecosystem Integration
Connect bats to farming and nighttime safety. Healthy colonies provide passive benefits that reward attentive players.

- [ ] Bats locate and remember farmland
- [ ] Bats share farmland memory with nearby bats
- [ ] Bats improve crop growth
- [ ] Bats seed empty farmland with random crops
- [ ] Bats reduce hostile mob spawns in chunks they have visited
- [ ] Phantoms do not spawn within a certain radius of bats 

### Configuration and Polish
Finalize the bat system with proper configuration options, complete assets, and version compatibility.

- [ ] Add config file for roost tuning (detection range, stress thresholds, decay rates, colony size)
- [ ] Add config for bat behavior tuning (crop detection, crop growth, hostile mob spawn reduction, phantom spawn elimination, seed spreading, guano production)
- [ ] Create guano block textures
- [ ] Create guano item texture
- [ ] Create roost block textures for each stone variant
- [ ] Add support for Minecraft 1.21.10 / NeoForge 21.10.x
