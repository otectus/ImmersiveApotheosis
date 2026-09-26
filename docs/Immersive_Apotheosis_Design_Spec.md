# Immersive Apotheosis — Design Spec (Forge 1.20.1)

The design authority for this mod. It follows the approved integration plan (Apotheosis × Immersive Smithing,
Tanning, Jewelry and Enchanting) and records the decisions that plan left open.

## 1. Purpose

Immersive Apotheosis is the Immersive family's hub for Apotheosis 7.x:

1. **Bridges.** Every piece of Apotheosis-facing glue for Immersive Smithing, Tanning and Jewelry lives here.
   The sister mods stay Apotheosis-agnostic and expose only generic hooks. Immersive Enchanting keeps its own table
   adapter, because table adapters are its domain.
2. **Performed stations.** Apotheosis's Reforging, Salvaging, Socketing and Gem Cutting become short, skill-based
   performances in the family's style.

**Family rule.** Apotheosis decides *what* is rolled; the player's skill decides *how well* it lands, never outside
Apotheosis's own bounds. Craft quality (the Immersive mods) and magic (Apotheosis) compose and never double-count.

Hard dependencies: Apotheosis 7.x (with its Adventure module for most features), Placebo, Apothic Attributes.
Everything else is optional and detected with `ModList` before any class that links against it is loaded.

## 2. Verified facts this design relies on (Apotheosis 7.4.8)

| Fact | Consequence |
|---|---|
| Socketing, add-socket, withdrawal and unnaming recipes extend `SmithingTransformRecipe` with an empty result. | Immersive Smithing's smithing-table suppression skips them; the regression test lives here. |
| Reforging copies the stack and replaces only `affix_data` (affixes, rarity, name); sockets become `max(rolled, getSockets())`. | Smithing, Tanning and Jewelry NBT survive reforging. A socket *bonus* must be a floor, never additive. |
| `SocketHelper.getSockets` reads NBT, then posts `GetItemSocketsEvent`. | Socket floors hook here, with no NBT writes. |
| `LootCategory.forItem` returns the first registered category whose predicate matches; `LootCategory.register(prior, name, predicate, slots)` is public. | Jewelry categories register before ApothicCurios's lazily created `curios:<slot>` ones. |
| ApothicCurios applies attribute affixes only when the item's category *is* `curios:<slot>`, but applies hook affixes for every equipped curio, and its fake slot is `LEGS`. | Jewelry categories avoid doubled attributes automatically; doubled hooks are prevented by excluding jewelry from ApothicCurios (§4.3). |
| Salvaging destroys socketed gems; `leather_horse_armor` salvages into vanilla leather. | Forge melt guard (§3.2); leather rewrite (§3.3). |

## 3. Bridges

### 3.1 Socket floors (Smithing, Tanning, Jewelry)
A `GetItemSocketsEvent` listener raises `sockets` to a floor, never adding to it:

| Grade (the sister mod's own label) | Floor |
|---|---|
| Fine | 1 |
| Masterwork | 2 |

- Only player-made work qualifies:
  - Smithing: a Maker's Mark `SmithUUID`, not Faulty;
  - Tanning: the bench's record of the stitching player (`ImmersiveTanningAPI.playerMade`); a crafter's name alone
    does not count, because villager leatherwork carries one too (MCA villagers have names);
  - Jewelry: a non-null `maker`.
- The item's loot category must not be `NONE`.
- Loot is graded Standard at most, and villager work carries no maker, so neither gets a floor.
- The floor still counts toward the Sigil of Socketing's maximum (3), so Masterwork saves up to two sigils. The
  floor is idempotent under reforging, because reforging writes `max(rolled, getSockets())`.
- Config (`sockets`): `enabled`, `fineFloor` (1), `masterworkFloor` (2), plus one switch per source
  (`smithing`, `tanning`, `jewelry`).

### 3.2 Forge melt guard (Smithing)
An `IMeltVeto` refuses items that hold socketed gems: "Pry the gems out first (Sigil of Withdrawal)". Affixed gear
without gems may still be melted. Config: `smithing.refuseSocketedMelting` (true).

### 3.3 Leather salvage (Tanning)
On `ServerStartedEvent` and `OnDatapackSyncEvent`, every `SalvagingRecipe` output of `minecraft:leather` is rebuilt to
output `immersive_tanning:binding_leather` with the same count range. This keeps Tanning's rule that every route to
leather ends at the Tanner's Rack. The result is listed in the report. Config: `tanning.bindingLeatherSalvage` (true).

### 3.4 Gem dust (Jewelry)
`apotheosis:gem_dust` joins `#immersive_jewelry:polishing_dusts`, so it makes Polishing Compound. This is data only;
Jewelry's own dust is displayed as "Gem Grit".

### 3.5 Report
`/immersiveapotheosis report` (permission 2) prints:
- which bridges are active, and the Apotheosis and sister-mod versions;
- recipes rewritten;
- jewelry categories registered;
- the ApothicCurios exclusion status;
- station availability.

## 4. Immersive Jewelry: full Apotheosis support, fully configurable

### 4.1 Categories
Four loot categories are registered: `immersive_apotheosis:ring`, `amulet`, `bracelet` and `circlet`.
- Each predicate matches an `immersive_jewelry` piece of that form whose form is enabled in config.
- They are registered at common setup, so they exist before affix and gem data load, and they are placed before
  any `curios:*` category.
- They are registered always, even without Immersive Jewelry: a category that matches nothing is harmless, while
  data naming a missing category would be dropped.
- Slots are `{LEGS}`, ApothicCurios's convention. Vanilla never equips jewelry in the legs slot, and the hub clears
  jewelry's `LEGS` attribute modifiers so no "When on Legs" tooltip appears.

### 4.2 Runtime
An `IJewelryEffectContributor` turns a piece's affixes and socketed gems into one Jewelry effect. Only
`maxActiveAffixes` affixes act: stat affixes first, then by id. Jewelry's abilities come from its own traits, so
Apotheosis adds stats first, and its ability affixes act only when a pack allows more. The rest are dormant and shown
in the tooltip. The effect provides:
- **attribute effect**: one per piece. It adds every affix's and gem's attribute modifiers (through Apotheosis's own
  `addModifiers` with slot `LEGS`), scaled by `affixValueMultiplier` / `gemValueMultiplier`, under per-slot ids.
- **hook effect**: dispatches Apotheosis's affix hooks (`doPostAttack`, `doPostHurt`, `onHurt`, `onBlockBreak`,
  `onArrowFired`, `onShieldBlock`) through the matching Jewelry hooks.

Jewelry's own gating therefore applies for free: a broken piece is inert, and pieces beyond `max<Form>Slots` are
inactive. Jewelry's prism amplification never touches contributed effects.

### 4.3 ApothicCurios
ApothicCurios would also dispatch hook affixes for worn jewelry. A `@Pseudo` mixin (`require = 0`) filters
`immersive_jewelry` stacks out of `ApothicCuriosMod#getEquippedCurios`.
- If the mixin does not apply (a changed ApothicCurios), the hub stops dispatching hooks itself (no doubling) and
  the report says so.
- Attributes are never doubled either way, thanks to the category-identity gate.

### 4.4 Data
- The jewelry affix set lives under `data/immersive_apotheosis/affixes/jewelry/<form>/`, written by
  `tools/generate_jewelry_affixes.py`:
  - it reuses Apotheosis's own affixes (same value tables, so gear parity before `affixValueMultiplier`) retargeted
    to one form each: rings offense, amulets defense, bracelets dexterity, circlets mind;
  - it adds Iron's Spellbooks attributes for circlets behind `forge:mod_loaded`;
  - every form has at least five stat affixes at every rarity, so Apotheosis's rarity rules always fill.
- **Gems are not overridden.** After every gem reload (and on clients after the registry sync, through Placebo's
  `RegistryCallback`), each gem without a jewelry bonus borrows one of its own:
  - rings prefer melee, bracelets ranged, amulets body armor, circlets helmet;
  - special, durability and enchantment bonuses never transfer.

  Every gem therefore fits jewelry, including other mods' gems and gems a pack changed, and no gem file can go
  stale.

### 4.5 Config (`jewelry`)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | true | master switch for jewelry categories and runtime |
| `rings`, `amulets`, `bracelets`, `circlets` | true | per form |
| `maxRarity` | `apotheosis:epic` | worn jewelry counts as at most this rarity (affix values are capped) |
| `affixValueMultiplier`, `gemValueMultiplier` | 0.5, 0.5 | attribute scaling for jewelry |
| `maxSockets` | 1 | clamp in the same socket event, also idempotent |
| `maxActiveAffixes` | 2 | extra affixes are dormant (tooltip) |

Packs that want every curio to take affixes can target ApothicCurios's `curios:*` categories with their own data;
this mod keeps to Immersive Jewelry. Finished jewelry does not appear in loot tables; if a pack adds it, Apotheosis's
own loot conversion applies, bounded by `maxRarity`.

## 5. Performed stations

Each station owns a pure deterministic spatial simulation in `minigame/`, with a common `WorkDefinition`,
`WorkInput`, immutable render state and `WorkResult`. The simulations contain no Minecraft imports. Sister mods
provide design inspiration, not required runtime engines: Tanning's directional surface work, Jewelry's cutting
and opposing settings, Smithing's spatial strikes, and Enchanting's magical feedback.

### Processes

- **Salvaging:** cut the displayed seams, then pull exposed components along their extraction paths. Off-path
  cutting, attached bindings and strained extraction lower recovery. One board represents the full batch; rarity
  scales complexity. Unrecovered components contribute no recovery quality.
- **Socketing:** drag and rotate the gem into its seat, then bend four or six retainers in opposing order. Position,
  pressure and balance affect quality; insufficient pressure can be corrected, while excessive pressure leaves a
  penalty. The active retainer, bend direction, pressure target and resulting tilt remain visible.
- **Gem cutting:** rotate facets into alignment with the grinding wheel, hold contact to remove material, release
  near the target depth, and cool between contacts. Overcutting and heat harm quality. Sweep the polishing tool over
  the worked facets to finish. Apotheosis's original upgrade ingredients and outcome remain authoritative.
- **Reforging:** heat magical bindings, aim charged hammer strikes at their anchors, then trace the connecting seal.
  Heat, force and spatial accuracy affect binding quality. The selected Apotheosis result is fixed throughout;
  these are magical bindings, so the same process applies to nonmetal equipment and jewelry.

### Client and input

The workbench remains an overlay over Apotheosis's existing container screen: replacing that screen would close
its menu. It displays captured item previews, distinct procedural workpieces/tools, process feedback, the current
objective and localized controls. Primary input is left mouse/Space; contextual input is right mouse/Left Shift,
with rebindable keys. Pointer positions are normalized to the workspace and quantized to 12 bits.

A three-second count-in is outside the simulation. Normal skilled active work targets 20–40 seconds, with a hard
60-second allowance for corrections. Completion resolves immediately. An unfinished normal run receives partial
quality, capped below success when essential setting/cutting/reforging stages remain. Visible damage affects only
the attempt's score, not saved item data.

Client and server execute the same 20 ms fixed steps. `InputsC2S` sends contiguous pointer/button frames every
100 ms in batches of at most 64; the entire operation is capped at 3,000 frames. No client score or crafted item is
accepted. The server validates bounds, sequence, session identity and elapsed time before replaying a whole batch.
Duplicate batches are ignored; gaps, overlaps and implausibly early input terminate invalid streams. The protocol
version includes the simulation version, and old clients must update with the server.

### Resolution and cancellation

Station contexts capture their inputs and previews before work. Player inventory clicks are blocked for the
active menu. Captured inputs are revalidated before any payout; externally replaced inputs are never charged.
Resolution removes the session before changing items, so duplicate input/cancel/close events cannot apply twice.

Esc during the count-in cancels for free. Afterward Esc twice, closing the table, disconnecting or failing to send
remaining input before the deadline plus grace counts as abandonment. Menu-removal hooks charge while the captured
items are still present. Existing station switches, creative/permission bypass and failure-cost settings remain.
`timingWindowMultiplier` retains its config name and becomes crafting assistance for spatial and material tolerances.

| Station | Apotheosis entry | Outcome |
|---|---|---|
| Salvaging | `SalvagingMenu#clickMenuButton` | One operation per batch, instant below `salvageMinOutputs` (4). Count = `min + round((max - min) * clamp((score - 30) / 70))`: 65 gives the mean, 100 the maximum. Abandoning yields the minimum. |
| Socketing | vanilla smithing result slot (`SocketingRecipe`) | Weak+ takes Apotheosis's result. Failure keeps the gem; optional configured durability cost is unchanged. |
| Gem cutting | `GemCuttingMenu#clickMenuButton` | Stable+ replays Apotheosis's upgrade; Perfect refunds the configured dust share (50% default). Failure retains both gems and charges configured dust/materials. |
| Reforging | chosen `ReforgingResultSlot` | Stable+ preserves the chosen roll. Frayed/Weak retain 0.75/0.45 of its affix levels by default. Failure charges configured costs and rerolls choices. |

Outcome thresholds remain Perfect 90+, Stable 80+, Frayed 65+, Weak 50+, Failed 35+, Shattered below 35.
No new blocks, recipes, dependencies, persistent item defects or world migrations are introduced.

## 6. Balance summary

| Power source | Bound |
|---|---|
| Durability | Smithing 0.5–1.25 × Tanning Harness 1.5 × Unbreaking × Durable affix, all separate hooks |
| Base damage, armor, speed | craft quality touches base modifiers only |
| Affixes | a performed reforge never exceeds Apotheosis's roll |
| Sockets | floor Fine 1 / Masterwork 2, maximum 3 by sigils; jewelry maximum 1 |
| Salvage | inside Apotheosis's `[min, max]` |
| Gem upgrades | about 1.1× Apotheosis's expected cost for an average player |
| Jewelry | rarity ≤ epic, ×0.5 values, 2 active affixes, 1 socket (all configurable) |

## 7. Testing
- **JUnit:** deterministic spatial simulations across stations, tiers and seeds; reference play, mistakes and
  recovery; idle/mashing/static-hold resistance; geometry and assistance bounds; duration limits; unchanged salvage
  yield mapping; contiguous input admission, duplicates, invalid bounds and wall-clock limits.
- **GameTests:** real station success/failure costs and item data; full input codec replay; closing, disconnecting,
  timing out, duplicate resolution, changing inputs and blocked inventory clicks; packet bounds and start descriptor.
  Existing socket-floor, jewelry, smithing and tanning bridge tests remain part of acceptance.
- **Client smoke test:** all four processes completed through screen mouse/key routing, a separate free cancel,
  focus-loss tool release, GUI scale changes during work, stage and verdict screenshots, server inventory assertions,
  and result JSON.
- **Ultima pack test:** an isolated instance with all five family jars; the station smoke flow plus enchanting-table
  ownership, loot-modifier order, ApothicCurios exclusion, bridges and jewelry affixes under the pack's patches.

## 8. Pack decisions (outside this mod)
- EasyMagic is inert for enchanting whenever Apotheosis replaces the table. Removing it from Ultima is optional.
- Tuned defaults for Ultima ship via `defaultconfigs` after the playtest.
