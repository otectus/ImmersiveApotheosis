# Immersive Apotheosis

*Forge 1.20.1, for Apotheosis 7.x.* The Immersive family's home for Apotheosis.

Apotheosis decides what is rolled; your skill decides how well it lands, never beyond Apotheosis's own bounds.

- **Performed stations.** Reforging, salvaging, socketing a gem and cutting a gem become hands-on crafting processes.
- **Master craftsmanship leaves room for gems.** Player-made Fine and Masterwork smithing, leatherwork and jewelry
  always have sockets.
- **Jewelry takes affixes and gems.** Immersive Jewelry pieces reforge, socket and salvage like gear, and their
  affixes and gems work while worn, under Jewelry's own rules.
- **The family fits together.** The Smith's Forge will not melt socketed gems, salvaging yields Binding Leather, and
  Apotheosis's gem dust makes Polishing Compound.

Hard dependencies: Apotheosis 7.x, Placebo and Apothic Attributes. Immersive Smithing, Tanning and Jewelry, Curios
and ApothicCurios are optional; each part of this mod turns on only when its mods are present.

## Performed stations

Use Apotheosis's tables as usual. Committing to a salvage, cut, setting or reforge opens a workbench over the
existing table. The highlighted workpiece, tool targets and stage instructions show what to do next.

- **Three-second preparation.** Esc during this count-in cancels for free.
- **Work directly on the material.** Aim with the mouse. Left click/drag/hold uses the primary tool; right
  click/drag/hold uses the contextual tool. Space and Left Shift are the rebindable keyboard alternatives
  (*Options > Controls > Immersive Apotheosis*). Watch the on-screen targets and safe ranges.
- **Different processes.** Salvaging cuts seams and extracts components; socketing aligns a gem and bends
  opposing retainers; cutting aligns, grinds and polishes facets; reforging heats magical bindings, strikes their
  anchors and traces a seal.
- **Learn and recover.** Release to reposition, cool a facet, or correct insufficient pressure. Overcutting,
  excessive force and off-path work reduce the attempt's quality. These marks do not add permanent item damage.

Familiar operations target 20–40 seconds, with up to 60 seconds to finish. Rarer work adds components, facets or
anchors. A completed operation resolves immediately; the time limit judges unfinished work, while an abandoned
or disconnected session follows the existing failure rules. Esc once asks to abandon; Esc again confirms (a held Esc
does not). The verdict stays on the table until you dismiss it (Esc, Enter or a click); if the menu closes first, the
verdict is written to chat.

The score (0–100) gives the existing outcome band: **Perfect** 90+, **Stable** 80+, **Frayed** 65+, **Weak** 50+,
**Failed** 35+, **Shattered** below 35. Essential unfinished stages cannot grant a successful setting, cut or reforge.
Apotheosis still determines every recipe, item, affix and cost.

| Station | Process | Result |
|---|---|---|
| Salvaging Table | Sever bindings, then pull components along their extraction paths; one operation for the batch | Each output stays inside Apotheosis's count range: 65 gives its average, 100 its maximum. Abandoning yields the minimum. Small batches remain instant. |
| Smithing table (socketing a gem) | Position and rotate the gem, then bend opposing retainers to balanced pressure | Weak or better sets the gem exactly as Apotheosis would. Failure keeps the gem untouched. |
| Gem Cutting Table | Rotate each facet to the wheel, grind to depth while managing heat, then polish its surface | Stable or better performs Apotheosis's upgrade; Perfect gives half the gem dust back. Failure keeps both gems and spends the configured dust/materials. |
| Reforging tables | Heat magical bindings, aim and charge hammer strikes, then trace the new seal | Stable or better keeps the selected roll. Frayed/Weak keeps its affixes and rarity at lower rolls. Failure spends gem dust and levels, keeps rarity materials, and rolls new choices. |

The operation locks the table's inventory while it runs. Clients send pointer/tool input; the server runs the same
simulation and decides the result. These workbenches also function without the other Immersive mods installed.
Update the mod on both the client and server together; the new work input protocol replaces the previous one.

## Master craftsmanship

Player-made work always has at least this many sockets:

| | Fine | Masterwork |
|---|---|---|
| Sockets | 1 | 2 |

"Player-made" means:
- Immersive Smithing pieces with a Maker's Mark, never Faulty;
- Immersive Tanning leatherwork a player stitched;
- Immersive Jewelry pieces with a jeweler's name (Jewelry's Fine and Exceptional count as Fine).

Loot and villager work have no maker and no floor. This is a floor, not a bonus: reforging and Sigils of Socketing
work as usual, and floor sockets count toward the sigils' maximum.

## Immersive Jewelry and Apotheosis

Rings, amulets, bracelets and circlets have their own Apotheosis categories:
- **rings** roll offensive affixes: critical strikes, life steal, armor pierce;
- **amulets** defensive: health, armor, healing, damage reduction;
- **bracelets** dexterity: draw speed, arrow damage, attack speed, mining;
- **circlets** the mind: experience, reach, and spell power with Iron's Spellbooks.

Every gem, including other mods' gems, fits jewelry by borrowing one of its own bonuses:
- rings take a gem's melee bonus;
- bracelets its ranged bonus;
- amulets its body-armor bonus;
- circlets its helmet bonus.

Worn pieces apply their affixes and gems through Immersive Jewelry's rules:
- a broken piece does nothing;
- only as many pieces of a form count as Jewelry allows;
- the jewelry settings below keep five slots' worth of power in proportion.

A piece's tooltip shows exactly what it grants when worn, which affixes are dormant, and whether its rarity is capped.

## Configuration

`serverconfig/immersive_apotheosis-server.toml` is per world and synced to clients.

| Section | Option | Default | Notes |
|---|---|---|---|
| sockets | `enabled` | true | socket floors for player-made work |
| | `fineFloor`, `masterworkFloor` | 1, 2 | 0-3 |
| | `smithing`, `tanning`, `jewelry` | true | which mods' work gets floors |
| smithing | `refuseSocketedMelting` | true | the Smith's Forge refuses gear with socketed gems (pry them out with a Sigil of Withdrawal) |
| tanning | `bindingLeatherSalvage` | true | salvaging yields Binding Leather instead of vanilla leather |
| jewelry | `enabled` | true | master switch for jewelry's Apotheosis categories |
| | `rings`, `amulets`, `bracelets`, `circlets` | true | per form |
| | `maxRarity` | `apotheosis:epic` | worn jewelry counts as at most this rarity |
| | `affixValueMultiplier`, `gemValueMultiplier` | 0.5, 0.5 | scale worn jewelry's attributes; 1.0 is gear parity |
| | `maxSockets` | 1 | 0-3 |
| | `maxActiveAffixes` | 2 | stat affixes act first; the rest are dormant |
| stations | `salvaging`, `socketing`, `gemCutting`, `reforging` | true | off: Apotheosis's instant behaviour |
| | `creativeBypass` | false | creative players use the stations instantly |
| | `allowPermissionBypass` | true | players with `immersive_apotheosis.bypass_performances` use them instantly |
| | `timingWindowMultiplier` | 1.0 | 0.5-2.0; above 1 widens spatial, alignment, pressure and heat tolerances |
| | `sessionGraceSeconds` | 5 | extra seconds allowed for input after the 60-second work limit |
| | `salvageMinOutputs` | 4 | batches whose best case yields fewer items salvage instantly |
| | `reforgeFrayedRetention`, `reforgeWeakRetention` | 0.75, 0.45 | share of each affix's roll a partial reforge keeps |
| | `reforgeFailureCost` | `DUST_AND_LEVELS` | also `FULL` or `NONE` |
| | `gemCuttingFailureCost` | `BOTH` | dust and materials; also `DUST`, `MATERIALS`, `NONE` |
| | `gemCuttingPerfectDustRefund` | 0.5 | |
| | `socketingFailureDurability` | 0 | durability a failed setting costs |

## Datapacks

The jewelry affixes are ordinary Apotheosis affix files under
`data/immersive_apotheosis/affixes/jewelry/<form>/`, targeting the categories `immersive_apotheosis:ring`,
`immersive_apotheosis:amulet`, `immersive_apotheosis:bracelet` and `immersive_apotheosis:circlet`. A datapack can
override them or add its own, and a gem file can give these categories their own bonus (it is used instead of a
borrowed one).

## Commands

`/immersiveapotheosis report` (operators) shows:
- which bridges are active;
- the jewelry categories and how many gem bonuses were borrowed;
- whether ApothicCurios leaves jewelry to Immersive Jewelry;
- which salvaging recipes now yield Binding Leather.

## Compatibility

- **ApothicCurios** runs every equipped curio's hook affixes. Jewelry runs its own through Immersive Jewelry, so this
  mod leaves jewelry out of ApothicCurios's list, and nothing fires twice. The report shows whether that worked. If it
  ever does not, jewelry stops running hooks itself rather than doubling them.
- **Immersive Enchanting** has its own Apotheosis enchanting-table support; nothing here changes enchanting.
- **EasyMagic** does not take over Apotheosis's enchanting table.
- Clients need the mod: performances are drawn and played on the client, and the server judges them.

## Java API

`com.otectus.immersiveapotheosis.api.ImmersiveApotheosisAPI`. Register during mod construction or
`FMLCommonSetupEvent`, or send an IMC message to `immersive_apotheosis` whose method is `assist_source` and whose
payload is a `Supplier<IPerformanceAssistSource>`.

- `IPerformanceAssistSource.windowMultiplier(player, station)`: per-player crafting assistance. When a performance
  starts, the server's `timingWindowMultiplier` is multiplied by every registered source's answer (each clamped to
  0.5..2.0, the product clamped again), and the client receives and simulates that final window. A skill mod widens
  the window for a practised player this way; the server still judges every input.
- Performed salvaging hands the outputs the table cannot hold to Apotheosis's own `SalvagingMenu#giveItem`, exactly
  as an instant salvage does, so a mod that hooks that method sees performed salvage too.

## Building and testing

```
python3 tools/sync_libs.py          # the sister mods' jars (from their builds) and ApothicCurios, into libs/
./gradlew build                     # jar in build/libs, plus the JUnit suite (the performance engine)
./gradlew runGameTestServer         # server GameTests: bridges, jewelry, and the four stations on real tables
./gradlew runGameTestServer -PapothicCurios                     # with ApothicCurios
./gradlew runGameTestServer -PnoSmithing -PnoTanning -PnoJewelry -PnoEnchanting -PnoCurios   # standalone
./gradlew runClient -PsmokeTest     # client agent: completes all four processes through real mouse/key routing
./gradlew build smokeTestJar && python3 tools/packtest/ultima_pack_test.py   # the Ultima modpack (isolated copy)
python3 tools/generate_jewelry_affixes.py --check   # the jewelry affixes against Apotheosis's own
```

## License

GPL-3.0. See `LICENSE`.
