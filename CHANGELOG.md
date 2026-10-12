# Changelog

Notable changes to Immersive Apotheosis. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/).

## [1.1.1] - 2026-10-12

First release since 1.0.0; 1.1.0 was never published, so its changes ship here.

### Added
- **Java API** (`com.otectus.immersiveapotheosis.api`). Other mods can widen or narrow a player's crafting assistance
  with an `IPerformanceAssistSource`. Register it through `ImmersiveApotheosisAPI.registerAssistSource`, or send an
  IMC message to `immersive_apotheosis` with the method `assist_source` and a `Supplier<IPerformanceAssistSource>`
  payload. When a performance starts, the server multiplies `timingWindowMultiplier` by every source's answer. Each
  answer is clamped to 0.5–2.0 and the product is clamped again. A source that throws or answers a non-finite value
  counts as 1.0. The client receives the final window and simulates the same work.
- The verdict is written to chat if the table's menu closes before the player dismisses it. It is also written to chat
  if the verdict arrives after the menu has closed, as an abandonment charged on closing does.

### Changed
- **The verdict stays on screen until it is dismissed.** Previously it disappeared after 2.5 seconds. Dismiss it with
  Esc, Enter, the work key or a click. Enter, the work key and clicks are ignored for the first 0.75 seconds, so the
  stroke that finished the work cannot dismiss it. A key still held from the work, or its auto-repeat, never dismisses
  it.
- The workbench now uses Minecraft's own look, matching Immersive Smithing. It draws the vanilla container panel,
  slots and bars, with dark grey labels, and a resource pack that restyles vanilla containers restyles it too:
  - redrawn workpieces for each station, in a dark display;
  - a row of steps that marks each of the process's phases as it is completed;
  - a label naming the station's tool;
  - a "Prepare your tools" countdown over the darkened workpiece;
  - a verdict with the outcome band as its headline, coloured by band, and an animated score bar.
- Performed salvaging now hands outputs that the table cannot hold to Apotheosis's own `SalvagingMenu#giveItem`, as an
  instant salvage does. Mods that hook that method, such as Runic Skills' salvage bonus, now apply to performed
  salvage too.

### Fixed
- A held Esc no longer confirms an abandon through key repeat. Only a second, separate press confirms.
- The count-in and verdict panels now render above the workpiece's text and tool markings.

### Compatibility
- The network protocol is unchanged from 1.0.0. The assistance window already travelled in the start message.

## [1.0.0] - 2026-09-26

Initial release.

### Added
- **Performed stations.** Salvaging, socketing a gem at the smithing table, gem cutting and reforging become hands-on
  processes on a workbench over Apotheosis's own tables:
  - a three-second count-in that Esc cancels for free;
  - mouse work with primary and contextual tools, plus rebindable Space and Left Shift keys;
  - a score from 0 to 100 that picks an outcome band: Perfect, Stable, Frayed, Weak, Failed or Shattered.

  Apotheosis still decides every recipe, item, affix and cost. The server runs the same deterministic simulation as
  the client and judges every input.
- **Socket floors for player-made work.** Fine and Masterwork pieces from Immersive Smithing, Tanning and Jewelry
  always have at least one and two sockets respectively.
- **Apotheosis categories for Immersive Jewelry.** Rings, amulets, bracelets and circlets each have their own affix
  pool. Gems fit jewelry by borrowing a melee, ranged, body-armor or helmet bonus. Worn pieces apply affixes and gems
  under Jewelry's rules, and their tooltips show what they grant and which affixes are dormant. The jewelry affixes are
  ordinary datapack files.
- **Family integration.** The Smith's Forge refuses gear with socketed gems. Salvaging yields Binding Leather.
  Apotheosis's gem dust makes Polishing Compound.
- **ApothicCurios compatibility.** Jewelry is left out of ApothicCurios's hook-affix list, so no affix fires twice.
- The `/immersiveapotheosis report` operator command.
- A per-world server config covering socket floors, jewelry balance and the stations.
- Two station bypasses, each switchable in the config: one for creative players, off by default, and one for players
  with the `immersive_apotheosis.bypass_performances` permission, on by default.
