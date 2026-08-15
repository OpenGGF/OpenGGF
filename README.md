# OpenGGF - The Open-Source Java-Based Speedy Erinaceidae Engine

> This project is a work in progress. For the current state, please see the latest version in the
> Releases section of this document.

## Introduction

OpenGGF is a community-made, fan-made, open-source Java game engine for research and preservation
of classic Mega Drive / Genesis platform games, specifically the mainline Sonic the Hedgehog
series. It aims to faithfully reimplement the physics and rendering behaviour of the original
hardware using data loaded from user-supplied ROM images. The project's primary goal
is accuracy: physics, collision, and audio are all verified against community-maintained
disassemblies of titles in the Sonic the Hedgehog series. No copyrighted assets are included in
this repository; a legally obtained ROM is required to run the engine.

The engine also aims to provide modern tooling such as a level editor and an open framework for
modding and customisation.

> **Disclaimer:** OpenGGF is a community-made fan project. It is not affiliated with, sponsored by,
> approved by, or endorsed by Sega. Sonic the Hedgehog and all related characters, names, and
> trademarks are the property of Sega Corporation. No ROM images or other copyrighted game data are
> included in this repository. Users must supply their own legally obtained ROM files to use this
> software.
>
> The disclaimer is also shown in-engine on startup; it can be disabled by setting
> `startup.legalDisclaimer: false` in `config.yaml`.

## User Guide

A comprehensive user guide is available in [`docs/guide/`](docs/guide/index.md), covering:

- **Players:** [Getting started](docs/guide/playing/getting-started.md), [controls](docs/guide/playing/controls.md), [configuration](docs/guide/playing/configuration.md), [game status](docs/guide/playing/game-status.md), and [troubleshooting](docs/guide/playing/troubleshooting.md).
- **Contributors:** [Dev setup](docs/guide/contributing/dev-setup.md), [architecture overview](docs/guide/contributing/architecture.md), [adding zones](docs/guide/contributing/adding-zones.md), [adding bosses](docs/guide/contributing/adding-bosses.md), [audio system](docs/guide/contributing/audio-system.md), [testing](docs/guide/contributing/testing.md), and [trace replay testing](docs/guide/contributing/trace-replay.md).
- **Cross-referencers:** [68000 primer](docs/guide/cross-referencing/68000-primer.md), [mapping exercises](docs/guide/cross-referencing/mapping-exercises.md), [per-game notes](docs/guide/cross-referencing/per-game-notes.md), and [tooling](docs/guide/cross-referencing/tooling.md).

Contributor tests are JUnit 5 / Jupiter only. Do not add JUnit 4 tests, rules, runners, or `org.junit.*` imports.

## Configuration

The engine reads runtime settings from `config.yaml` in the working directory. A legacy
`config.json` is migrated automatically on first run. Key bindings can be written either as GLFW
integer codes or as human-readable names such as `SPACE`, `Q`, or `F9`. See
[`CONFIGURATION.md`](CONFIGURATION.md) and the player guide for the full reference.

## Controls

Keyboard and standard GLFW gamepads are supported for gameplay and the basic
startup/title/data-select menus.

### Player Controls

| Key | Action |
|-----|--------|
| Arrow Keys | Movement |
| Space | Player 1 action A / jump |
| Right Shift | Player 2 action A / jump |
| Enter | Pause / unpause |

The bundled `config.yaml` exposes keyboard bindings under `input.pause`,
`input.player1`, and `input.player2`. Keyboard B/C are unbound by default;
gamepads map west/south/east face buttons to Mega Drive A/B/C. On Xbox-style
pads that is X/A/B; on PlayStation-style pads that is Square/Cross/Circle.
Additional bindable inputs, including Start and controller assignment, are
documented in [`CONFIGURATION.md`](CONFIGURATION.md); keys omitted from the
template still use the engine defaults until added explicitly.

### Debug Controls

| Key | Action |
|-----|--------|
| F1 | Show/Hide Debug Overlay (text and bounding boxes) |
| F2 | Show/Hide Shortcuts Overlay |
| F3 | Show/Hide Player Panel |
| F4 | Show/Hide Sensor Labels |
| F5 | Show/Hide Object Labels |
| F6 | Show/Hide Camera Bounds |
| F7 | Show/Hide Player Bounds |
| F8 | Show/Hide Object Points |
| F9 | Show/Hide Ring Bounds |
| F10 | Show/Hide Plane Switchers |
| F11 | Show/Hide Touch Response |
| F12 | Show/Hide Art Viewer |
| Page Up | Cycle Acts (`debug.keys.nextAct`) |
| Page Down | Cycle Zones (`debug.keys.nextZone`) |

`F9` is also the default level-select shortcut (`debug.keys.levelSelect`), so it
can both open level select and toggle ring bounds while debug overlays are enabled.

### Editor Controls

| Key | Action |
|-----|--------|
| Shift+Tab | Toggle between gameplay and the experimental editor overlay (`debug.flags.editor` must be `true`) |
| F5 | Restart the playtest from editor mode |

## FAQ

### What does "GGF" stand for?

Gotta Go Fast!

### Is this an emulator?

No. OpenGGF is an independent reimplementation of the game logic and physics, written in Java
from scratch. It does not emulate the Mega Drive CPU or VDP. Instead, it reads data (level
layouts, art, music) from original ROM images and runs its own implementation of the game rules.
The implementation is developed and verified against the community-maintained disassemblies
([s1disasm], [s2disasm], [skdisasm]) to achieve pixel-accurate behaviour. The audio engine is a
partial exception: it features software emulation of the YM2612 FM synthesiser and SN76489 PSG
chips (based on [libvgm] and [Genesis Plus GX] reference cores) driven by a reimplemented SMPS
sound driver.

[libvgm]: https://github.com/ValleyBell/libvgm
[Genesis Plus GX]: https://github.com/ekeeke/Genesis-Plus-GX

[s1disasm]: https://github.com/sonicretro/s1disasm
[s2disasm]: https://github.com/sonicretro/s2disasm
[skdisasm]: https://github.com/sonicretro/skdisasm

### Which games are supported?

| Game | Status |
|------|--------|
| Sonic the Hedgehog (S1) | Most complete. Includes all zones, bosses, special stages, title screen, ending, and credits. |
| Sonic the Hedgehog 2 (S2) | Broadly playable. Includes all zones, bosses, special stages, Tails AI, ending, and credits. |
| Sonic 3 & Knuckles (S3K) | Work in progress. AIZ through LBZ have substantial route coverage, but this is not full parity: the AIZ miniboss napalm FallingShot and AIZ2 end-boss splash children now have native implementations with route/trace validation still outstanding, while Knuckles' LBZ Big Arm handoff remains inert. |

Work is ongoing across all three games. See `CHANGELOG.md` for detailed, per-merge history.

### Where do I get ROMs?

We do not supply ROM images. You must provide your own legally obtained copies. The engine expects
these specific revisions, placed in the working directory:

| Game | Expected filename | Expected revision and hash |
|------|-------------------|----------------------------|
| Sonic 1 | `s1.gen` | World, Revision 01; CRC32 `AFE05EEE`; SHA-1 `69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B` |
| Sonic 2 | `s2.gen` | World, Revision 01; CRC32 `7B905383`; SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` |
| Sonic 3&K | `s3k.gen` | World lock-on combined ROM; CRC32 `63522553`; SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` |

Other revisions (REV00, etc.) are untested and will likely produce incorrect results, as
ROM addresses are verified against these specific builds. ROM filenames are configurable via
`config.yaml` (see `roms.sonic1`, `roms.sonic2`, and `roms.sonic3k`).

### What is cross-game feature donation?

A feature that lets a donor game (S2 or S3K) provide player sprites, spindash mechanics, sound
effects, and the data select (save/load) screen while you play a different base game (e.g.
Sonic 1). This means you can play S1 levels with S2's Sonic and Tails sprites, spindash, and
sidekick AI — and when S3K is the donor, you also get the full S3K data select screen with
save slots and team selection before gameplay begins.
When S3K is the donor, that donated data select now also uses host-specific emerald presentation
and runtime-generated S1/S2 zone preview screenshots. Data select donation is only enabled when
`crossGame.enabled` is `true` and `crossGame.source` is `"s3k"`. Enable it in
`config.yaml`:

```yaml
crossGame:
  enabled: true
  source: "s3k"
```

Both the base game ROM and the donor game ROM must be present.

### Why Java?

We knew Java, and nobody had done it before. Every other Sonic engine reimplementation out there is
written in C, C++, or C#. A Java implementation proves it can be done on a managed runtime, and
the JVM's cross-platform nature means it runs on Windows, macOS, and Linux without platform-specific
builds (though a GraalVM native image is also available for those who prefer it).

### Will Sega shut this down?

This project contains no copyrighted material. No ROM data, sprites, music, or other Sega assets
are included in the repository. The engine is an independent reimplementation, developed and
verified against the community-maintained disassemblies, that requires users to supply their own
legally obtained ROM files. We have no affiliation with Sega and make no claim to any of their
intellectual property.

### What platforms does it run on?

Anywhere Java 21 and LWJGL run: Windows, macOS, and Linux. The engine uses OpenGL 4.1 core profile
(chosen for macOS compatibility). A GraalVM native image build is also supported for ahead-of-time compiled
binaries.

### Did you use AI to write this? / This is AI slop!

Various agents (Claude, Codex, and Gemini, in various models, versions and forms) have all been used at various points in the project's history, and
the commit history doesn't hide it; you'll see `Co-Authored-By` tags throughout. But the project
has been in development since 2013, long before AI coding assistants existed.

The pre-AI core — the engine framework and architecture, the rendering pipeline, the physics
engine and its subpixel movement model, and the sensor-based collision system — was designed and
coded by hand over years, long before any agent touched the repo. Other subsystems were built
with heavy AI assistance under direct human oversight; the SMPS audio engine, in particular, was
AI-built and steered against reference implementations rather than hand-written. AI was brought in for bulk analysis and research, to accelerate
object and boss implementation, debugging, validation, and unit tests; all with accuracy verified
against the original ROM disassemblies. Every commit is reviewed, tested, and corrected where
needed.

[You can't prompt your way to ROM accuracy (yet!)](docs/AI_JOURNEY.md). But we certainly prompted our way through object
implementations, research and boilerplate code a lot faster than would have been possible by hand.

For the visual version of that story, the [Development Timeline](docs/project/development-timeline.md) is a
captioned gallery of real dev builds — bugs and all — from a 2015 white-box prototype through to
the present, including the audio engine slowly un-mangling itself.

### How can I contribute?

The project is open source. Start with [`CONTRIBUTING.md`](CONTRIBUTING.md), then check the issue
tracker, OBJECT_CHECKLIST.md for unimplemented game objects, and CHANGELOG.md for the current state
of each game. The codebase uses a provider-based architecture that makes it relatively
straightforward to add new objects, zones, and game-specific behaviour.

## Releases

- **Managed agent scratch storage (2026-08-14):** Codex, Claude Code, trace
  diagnostics and benchmark retention now use a validated disk-backed
  `$AGENT_SCRATCH_ROOT`; Claude Code also receives `TMPDIR`, `TMP`, and `TEMP`
  under its managed `claude` child. Bounded cleanup, mirrored agent guidance,
  and a daily user timer keep the scratch area controlled. The helper rejects
  unsafe roots and symlink races, while
  the documented Windows path provisions durable output through WSL instead of
  `/tmp` or a Windows temp directory. The user-unit verifier retains the
  session runtime directory it needs, so canonical installs can activate the
  timer without depending on a checkout or worktree.
- **Sonic 3 & Knuckles trace parity (`bugfix/s3k-traces`, merged 2026-08-10):** brings 15 of 16 previously-failing S3K trace-replay classes to green — AIZ, CNZ, HCZ, ICZ, LBZ and MGZ (standalone and complete-run), the gumball, pachinko and slots bonus stages, the special stage, and the hardware-timing replay — with no previously-green class regressing. Merged after a three-lane review (`docs/architecture/audits/2026-08-10-s3k-traces-branch-review.md`) whose two blocking findings were fixed first: a sidekick on-screen predicate that added three conditions the ROM does not have, and an art submission that only ran when a trace was driving the replay.

### v0.6.prerelease (Current development snapshot)

- **A 128px spike strip was reading as offscreen (`bugfix/ai-mgz-4716-tails-death`, merged
  2026-08-15).** The largest single frontier advance of the session: MGZ **4716 → 10709**
  (+5993), errors 4953 → 3950. The field dump gave a Tails-only cluster — routine `0x02→0x06`,
  `y_speed → -0x0700`, anim `0x18`, x frozen — the exact signature of `Kill_Character`. Shared
  rings held steady, which rules out `HurtCharacter` (for Player 2 outside competition mode it
  skips the ring test entirely and writes different values). **So the ROM killed Tails and the
  engine didn't** — "should have stopped" was right in outcome, wrong in mechanism. The killer is
  a floor-spike strip whose `Spikes_Dimensions` entry gives `width_pixels = $40`
  (`sonic3k.asm:48926-48940`), spanning 128px. **`Sonic3kSpikeObjectInstance` overrode
  `getOnScreenHalfHeight()` but not the width sibling**, so `AbstractObjectInstance`'s flat
  default of 16 made the engine judge the strip offscreen and skip `SolidObjectFull` entirely.
  Thirteen lines. **P60 records the general contract**: every solid object has *two* ROM widths —
  the collision `d1` and the `Render_Sprites` footprint — and a `getOnScreenHalfHeight()` override
  with no width sibling is the tell. Cross-game.

- **The MGZ dash trigger re-arms every frame (`bugfix/ai-mgz-dash-trigger-rearm`, merged
  2026-08-15).** The fixture's aux named the object in one step — slot 4's `object_code` is
  `Obj_MGZDashTrigger`'s main routine. Its launch maths was **already correct**: the ROM's helper
  reproduces both recorded velocities from ROM data alone (`GetArcTan` → `$1A`, `GetSineCosine` →
  sin `$98` / cos `$CD`, giving `-$59B` and `-$428`, both matching the recording exactly). The
  trigger simply wasn't armed. `loc_25D9C` runs the contact probe and **both** `move.w #$3C,$30(a0)`
  arm stores **above** the countdown (`sonic3k.asm:51493-51545`), so the store is a per-frame
  *reload* and the 60-frame window runs from the **last** qualifying frame. The engine wrapped its
  arm loop in `if (armTimer == 0)` and `break`ed after the first player, so its window started
  ~60 frames earlier and had lapsed — and the `break` also suppressed P2's independent arm, which
  the ROM evaluates separately (`andi.b #$11` vs `#$22`). Nine lines. Frontier **4603 → 4716**,
  errors 6493 → 4953. Catalogued as **P59**.

- **A Tunnelbot on the wrong ceiling probe, and the `V_int_run_count+3` address trap
  (`bugfix/ai-mgz-tunnelbot-ceiling-probe`, merged 2026-08-15).** MGZ's "sign inversion" at 1909
  was not a direction defect — **the ROM performs the same inversion, one frame later**. It is the
  boss-bounce triple `neg.w` (`sonic3k.asm:20913-20915`) firing early because `Obj_Tunnelbot`
  entered its rumble phase a frame late and a pixel low: through the ceiling-rise phase engine and
  ROM agree pixel-for-pixel, then the engine measures one extra pixel of clearance and takes one
  more `subq.w #1,y_pos`. Cause: `TunnelbotBadnikInstance` called the legacy S1/S2 object-ceiling
  entry instead of the native probe modelling `ObjCheckCeilingDist`'s `eori.w #$F` low-nibble
  transform — and **`MgzMinibossInstance` ports the same ROM routine and was already on the native
  probe**, so two ports of one routine disagreeing was the tell. Frontier **1909 → 4603**
  (+2694), errors 8269 → 6493. **Second defect found and fixed:** `V_int_run_count` is a
  **longword** (`addq.l #1`, `:543`), so `move.b (V_int_run_count+3).w,d1` selects its *low byte* —
  `+3` is an **address**, not arithmetic. Read as arithmetic it inverts bit 0 and flips the whole
  rumble step. Two further sites carry the identical misreading and were **deliberately left**,
  each needing its own blast radius. Catalogued as **P58**.

- **Monitor solid exemptions are acquire-time only (`bugfix/ai-s3k-mgz-camera-321`, merged
  2026-08-15).** MGZ's frame-321 `camera_y` was **derived**: the air bit selects `MoveCameraY`'s
  arm, and player x/y still agreed at 321. **The camera subsystem was not involved.** The cause is
  a monitor, in two halves. `SolidObject_Monitor_SonicKnux` opens
  `btst d6,status(a0) / bne Monitor_ChkOverEdge` (`sonic3k.asm:40564-40566`), so the roll-anim,
  Knuckles-glide and competition exemptions are **acquire-time only** — once riding, release comes
  solely from `Monitor_ChkOverEdge` on `Status_InAir` or leaving the span. The engine re-tested the
  exemptions every frame, so pressing down atop a monitor started the roll and instantly unseated
  the rider. Fixing that alone reached only frame 384: `.notonmonitor` also does
  `bclr d6,status(a0)` (`:40617`), and the engine skipped clearing `P1_STANDING` on
  contact-cleared, so a latched bit made `Obj_MonitorBreak` force `Status_InAir` on a grounded
  player rolling into that same monitor to break it. Clearing only `P1_STANDING` — pushing stays
  independent, matching the ROM's separate mask — moves the frontier **321 → 1909** (+1588) over
  an unchanged 30831 compared rows. Catalogued as **P57**.

- **The S3K top-solid residual is a 16-frame ground-sensor lag, not a landing defect
  (`bugfix/ai-s3k-aiz-topsolid-approach`, docs merged 2026-08-15).** Third round on this, and the
  premise is refuted: **nothing stops the engine's player descending.** With the zero-distance flag
  flipped, Sonic *does* descend 3px and *does* land inside the ROM's `[-16,-1]` window — **16
  frames late**. A y-write stack probe names the owner precisely: the ground-sensor floor-distance
  snap (`Sonic_AnglePos`/`Sonic_WalkVertical`'s `add.w d1,y_pos`), nothing in the solid path. The
  arithmetic closes independently — the ride seat `objY − d3 − y_radius` gives `$381` for Tails and
  `$37D` for Sonic, both matching the fixture — and **Tails is a clean control**: he arrives at a
  different y, the probe reports `d0 = −2`, and engine and ROM agree field-for-field. Only Sonic's
  *approach timing* differs. Confirming the diagnosis, the `21/20` reject counter is an exact fit
  for this 16-frame lag, so it is compensation for the lag and should be deleted with the flip
  rather than replaced. **Newly excluded:** the landing boundary, the seat arithmetic, `y_radius`,
  object position and motion, and the sidekick path.

- **The two top-solid compensations are confirmed fitted — and do not own the reds
  (`bugfix/ai-s3k-topsolid-zero-boundary`, docs merged 2026-08-15).** Both `Obj_CNZTrapDoor` and
  `Obj_AIZTransitionFloor` are plain `SolidObjectTop` callers with no landing gate or phase quirk,
  so both engine compensations are pure compensation. The AIZ one — a per-player counter of
  rejected zero-distance passes with thresholds **21/20** selected on the y sub-pixel — is a
  fitted constant with no ROM owner at all. And the CNZ history sample is **contradicted by the
  fixture**: at the landing frame the fraction advances by exactly `y_speed`, so the ROM landed
  from the **current** position with `d0 = -1`; against the previous frame's position the same
  computation gives `d0 = +2`, which `bhi` rejects outright. **But removing both alongside the
  flag produces exactly the same five reds, same frames, same counts** as the flag alone — so they
  are necessary but not sufficient, and the propagation chain is elsewhere. Held again under the
  same discriminator. Two mechanisms now excluded, and the residual is narrowed to an
  approach-phase defect: the ROM's rider is ≥1px inside on the landing frame while the engine sits
  at exactly the surface for 16 frames.

- **LRZ 208 root-caused to an unsigned compare, and held
  (`bugfix/ai-lrz-208-topsolid-zero-boundary`, docs merged 2026-08-15).** The briefed
  "sub-pixel accumulation" reading is **refuted**: `tails_y_sub` is *identical* at 208 and Tails'
  free fall matches the ROM byte-for-byte through it. All eight differing fields flip together,
  and the engine's frame-208 state is bit-for-bit the recording's frame-**209** state — a
  one-frame-early landing. Every S3K top-solid landing path converges on `loc_1E45A`
  (`sonic3k.asm:42005-42015`), where `cmpi.w #-$10,d0` / **`blo`** is an *unsigned* compare
  against `$FFF0`: with `d0 == 0` it branches away **without landing**, so the accepted window is
  `d0 ∈ [−16,−1]` and the rider must already be a pixel inside. A signed reading accepts zero —
  which is what the engine does, via `CollisionRules.topSolidLandingAllowsZeroDist = true`.
  **Held, not landed**: flipping it moves LRZ 11942 → 6480 errors and the frontier 208 → 361, but
  reds five previously-green S3K classes with zero newly green. The owners are located —
  `CnzTrapDoorInstance` sidesteps the same symptom by sampling the *previous* frame's player
  position, a competing compensation layered on the wrong boundary — but the chain is not fully
  traced, so the discriminator says hold. Catalogued as **P56**. Also found: a landed test asserts
  the engine's behaviour while citing the exact ROM lines that say the opposite.

- **The LRZ collapsing bridge, ported from the ROM
  (`bugfix/ai-lrz-collapsing-bridge`, merged 2026-08-15).** The sibling of the FBZ launcher:
  `Obj_LRZCollapsingBridge` (SKL id `$31`) was a name-only registry entry falling through to a
  placeholder, so the bridge was not solid and Tails fell through the level at frame 100. Verified
  from the **ROM binary**, not the listing — `0x39C50` is `20bc 00039ca8` (`move.l #$39CA8,(a0)`,
  literally the recorded `object_code`) and `0x39E20` is `0015`, a `dbf` count giving the 22 debris
  children the recorder shows spawning. LRZ frontier **100 → 208**. Two things worth recording:
  the port's first draft modelled the ROM's break frame as *not solid* — literally true of the
  ROM, but the engine's generic platform path reads that as a ride exit and launched both
  characters, so **staying solid throughout is the accurate model** for a slab whose riders the
  ROM never touches (**P55**). And `TestNoServicesInObjectConstructors` correctly flagged three
  construction violations, all fixed properly rather than allow-listed. The new frontier at 208 is
  a different defect: sidekick sub-pixel accumulation on the fall between the two bridges.

- **The FBZ/DEZ player launcher, ported from the ROM
  (`bugfix/ai-fbz-dez-player-launcher`, merged 2026-08-15).** FBZ's frame-64 3px `y` was not a
  physics or coordinate issue at all. The fixture's own aux shows Sonic's `interact` becoming a
  slot whose `object_code` is `0x0003B97A` — the main routine of `Obj_FBZDEZPlayerLauncher`
  (`sonic3k.asm:79394-79488`, id `$78`). The engine had the **name** in its registry but no
  instance class, so it spawned a placeholder, the pad was not solid, and Sonic ran straight past.
  Now ported: top-solid `SolidObjectTop`, the 12-frame run timer and 4-frame doubling counter, the
  rider handling and the walk back to home x. Two ROM contracts needed care — the landing test and
  `MvSonicOnPtfm`'s re-seat share the same bare `d3`, and `loc_3B9AC` loads `d4` *after* the move
  so the carry is zero and the rider must **not** be dragged, while `loc_3BA4A` stacks the pre-move
  x and *does* carry. FBZ frontier 64 → 116, with the whole launcher sequence now matching.
  **LRZ identified but not fixed**: the same method names `Obj_LRZCollapsingBridge` (recorded
  `object_code 0x00039CA8` is literally what the ROM writes at `:77385`) — a second name-only
  registry entry, so Tails falls through a bridge that is not solid. Catalogued as **P54**.

- **The HCZ segment class goes GREEN: the geyser must survive to re-queue enemy art
  (`bugfix/ai-hcz-geyser-enemy-art-reload`, merged 2026-08-15).**
  `TestS3kSonicTailsHczSegmentTraceReplay` **1135 errors → 0 over 3519 compared frames** — the
  second S3K segment class closed this session. The missing KosM ordinals 108–111 were **not new
  art**: their fingerprints are byte-identical to 103–106, i.e. `PLCKosM_HCZ1`'s four badnik
  archives submitted a *second* time by `HCZGeyser_ReloadEnemyArtAndDelete`
  (`sonic3k.asm:65002-65005`), which re-queues the VRAM the horizontal geyser sheet overwrote. It
  is reached from `HCZGeyser_CleanupDelay`'s 150-frame countdown (`:64996-64999`). **The producer
  was already implemented** — the engine's geyser reproduced the recorded schedule exactly, then
  was camera-unloaded **29 ticks short** of that countdown. `Obj_HCZWaterWall` has **no
  `out_of_range` or `MarkObjGone` anywhere in its body**; its only deletes are the routine-0
  player-Y guard, the vertical branch's range check (already modelled) and the cleanup expiry. One
  method declaring a custom out-of-range check that never fires. Catalogued as **P53**. The
  second unproduced burst at frames 2957–2967 is confirmed as the same producer.

- **HCZ 2478 traced through five links to a missing KosM producer
  (`bugfix/ai-hcz-2478-kosm-ordinal-skew`, docs merged 2026-08-15).** A player `x_speed` value
  turns out to be four objects deep. All five fields diverging at 2478 are the signature of
  `HCZ_WaterTunnels` engaging (`sonic3k.asm:8848-8899`) — confirmed by the fixture advancing x by
  exactly 8/frame with `x_sub` frozen, which is 4px of normal movement plus the routine's own
  `add.l d0,x_pos` (`:8875-8879`). The engine cannot engage because the tunnel's gate
  `btst d5,(_unkF7C7).w` (`:8870`) is stuck: the byte is cleared by `HCZLargeFan_Main` after its
  8-frame drop (`:65634`), the engine's fan **activates on exactly the right frame**, and then
  sits in art-loading forever. **Root cause: a four-deep KosM ordinal skew.** The fan's submission
  takes ordinal 108 while the fixture records its completion as ordinal **112** — and recorded
  ordinals 108–111 have *no engine producer at all*. Since release requires kind + ordinal +
  fingerprint, an ordinal four behind can never be released by any row: permanent deadlock. The
  fan, the gate byte and the tunnel are all correct downstream consumers. Catalogued as **P52**;
  the unidentified producer is the next target.

- **MegaChopper's offscreen gate and kill bounce, with one deliberate red
  (`bugfix/ai-megachopper-waitoffscreen-enemydefeated`, merged 2026-08-15).** `Obj_MegaChopper`'s
  first instruction is `jsr (Obj_WaitOffscreen).l`, which suppresses **every** routine including
  Init until the sprite is drawn; and its `Touch_Special` defeat path owes the player the
  `EnemyDefeated` bounce itself (`loc_85758`'s `subi.w #$100,y_vel(a1)`). Both are now modelled.
  HCZ **segment** frontier 1434 → 2478, errors 1445 → 1135.
  **`TestS3kHczCompleteRunTraceReplay` becomes deliberately red** (2 errors), and the propagation
  is traced end to end: at frame 1481 the badnik sits at x=3840 with the fix and 3839 without —
  and the run's own aux records `0x0F00` = 3840, so **the fix is the ROM-correct side**. That
  pixel shifts slot occupancy on 16,289 of 31,482 rows, so 27,600 frames later a scattered ring
  lands in a different slot and `Obj_Bouncing_Ring`'s slot-keyed floor probe collects it four
  frames early. **The previous green was accidental** — occupancy diverges from ROM on 2387 of
  2387 sampled frames on *both* trees, and the class compares no object identity at all. Marked in
  the class's own javadoc, with the mechanism and removal condition in `known-discrepancies`.

- **There is no S3K object identity to map: the recorded value is a live program counter
  (`bugfix/ai-s3k-object-code-identity`, docs merged 2026-08-15).** The obvious fix for the entry
  below — invert the object pointer tables to turn code addresses into ids — **cannot work**, and
  the reason is structural. The S3K SST has **no `id` field at any offset**: its conventions begin
  `code = 0 ; longword` (`sonic3k.constants.asm`), and objects **overwrite their own dispatch
  pointer with internal sub-routine addresses to advance state** — 1,758 `move.l #<label>,(a0)`
  sites in `sonic3k.asm`. So the recorded value is a program counter, not a type at any width.
  Measured against both tables read from the ROM at the addresses the object loader itself uses:
  only **4.26%** of `slot_dump` entries are table entries (2.6% of the full stream), and they are
  exactly the objects that dispatch via a `routine` byte instead. A containment rule
  (nearest preceding table entry) was tested and **rejected as fitted** — it reproduces four
  inferred labels but misplaces others into unrelated gaps. Consequence: the earlier
  41/23/24/12 mixture is **withdrawn, not refined** — the permutation-versus-population split is
  not currently measurable. What survives, because it never needed identity: the 2387/2387 frame
  headline, the 19,519 genuine presence/absence entries, and mean occupancy 19.9 engine vs 23.9
  ROM.

- **The occupancy comparator compares the wrong number space
  (`bugfix/ai-s3k-slot-occupancy-scoping`, docs merged 2026-08-15).** Scoping the blind spot below
  corrected its own headline. S3K keeps a **32-bit ROM code pointer** in the first SST long, not an
  id byte — `Process_Sprites` does `move.l (a0),d0 / movea.l d0,a1 / jsr (a1)`
  (`sonic3k.asm:35985-35988`) — and both the probe and the **committed** `TraceBinder.compareObjectNear`
  truncate it to its low byte before comparing against the engine's *layout* object id. The tell:
  every "ROM id" printed is even, because addresses are. **That is 67.6% of the reported
  divergence**; the genuine presence/absence figure is 19,519 entries, not 60,274. So enabling
  `compareObjectNearEvents()` for S3K today would measure nothing interpretable — fixing the
  identity mapping is the prerequisite, which **inverts** the recommended first step. The residual
  is a near-even mixture (41% correct slot, 23% permuted, 24% short, 12% excess), not a
  permutation, and the ROM allocator is **already modelled faithfully** — including the
  pre-increment that makes the first dynamic slot unreachable, and the `.lookup` division the
  disassembly itself flags as a mistake, which was verified to evaluate correctly at all 90 parent
  positions. Scoped `larger-than-one-round`.

- **A comparison blind spot: S3K object-slot occupancy is never compared, and diverges from ROM
  everywhere (`bugfix/ai-hcz-29095-ring-slot-phase`, docs merged 2026-08-15).** Chasing why a held
  MegaChopper fix moved a ring collection 27,600 frames later produced a finding worth more than
  the fix. `TestS3kHczCompleteRunTraceReplay` compares **no object identity, slot or position** —
  `compareObjectNearEvents()` defaults false and is not overridden — while its fixture carries
  394,205 `object_state`, 342,381 `object_near` and 2,388 `slot_dump` events. With the probe
  armed, engine-vs-ROM occupancy diverges on **2387 of 2387 sampled frames on both trees**. The
  test has been green for its entire life while its object graph disagreed with the ROM
  everywhere; its green is evidence about **player physics only**. This is not cosmetic:
  `Obj_Bouncing_Ring` gates its floor probe on its own SST slot
  (`sonic3k.asm:35629-35632`, where `d7` is `Process_Sprites`' live slot countdown), so a ring in
  a different slot bounces on different frames — which is exactly the four-frame-early collection
  that held the MegaChopper pair off `develop`. **Neither the pair nor the ring code is wrong;
  both are ROM-faithful.** The residual is accumulated occupancy drift that both trees carry.

- **ICZ 2336 traced to the cold-start boundary, not an engine defect
  (`bugfix/ai-icz-2336-giant-ring-emeralds`, docs merged 2026-08-15).** The `rings` 80-vs-30
  divergence is a **single +50 award in one row** — every other ring change in the 18,043-row
  segment is +1 — and it is the giant ring's `moveq #50,d0 / jmp (AddRings).l` at `loc_61794`.
  The cascade actually opens one frame earlier with `player_animation_id` `0x1C` and
  `player_mapping_frame` `0x00`, which are literally the capture branch's writes at `loc_6173A`.
  A probe shows both ROM-derived terms the engine can evaluate are **correct** — the zone-half
  classification is right and the earlier Super Emerald fix is not implicated. The single false
  term is the emerald count: the replay holds **0** where the recorded run held **7**, which the
  run manifest records. So this is the **standalone-segment bootstrap boundary** — run-level
  progression is not carried in, exactly as start position is not — and seeding it would be
  rule-4 hydration. Nothing landed; recorded as a known consequence of the existing bootstrap
  debt, with pitfall **P50** ("some object branches read persistent run state, not level state")
  added to both skill trees.

- **Star Pointer orbit points die with their parent, as the ROM's child tail-call does
  (`bugfix/ai-icz-1983-anim`, merged 2026-08-15).** ROM child objects delegate their own lifetime
  to their tail call: `Child_DrawTouch_Sprite` (`sonic3k.asm:178053-178058`) reads `parent3`,
  tests `btst #7,status(a1)` and jumps to `Go_Delete_Sprite` **before**
  `Add_SpriteToCollisionResponseList` — and `Touch_EnemyNormal` sets exactly that bit on the
  badnik it destroys (`:20953`). `StarPointerBadnikInstance`'s orbit point never tested its
  parent, so after the engine bounced the parent identically to the ROM at ICZ frame 2141, a
  surviving orbit point hurt Sonic eleven frames later. `OrbinautBadnikInstance`'s orb — the
  sibling implementation of the same ROM contract — already had the check: two paths that should
  agree, and didn't. The fix is confined to the orbit branch, because the launched and breaking
  routines tail into a different helper that does *not* test the parent, where deleting would be
  the mirror-image bug. **The briefed suspect and the briefed frame were both wrong**: frame 1983
  is a 2-error self-healing blip, and the real cascade began at 2152 with the leader in the hurt
  routine. Errors 2970 → 2862, real frontier 2152 → 2336. Catalogued as S3K pitfall P49.

- **A compensating pair removed together: the roll-stop push clear and the delay-17 status bridge
  (`bugfix/ai-icz-rollstop-push-pair`, merged 2026-08-15).** Two defects, one masking the other,
  landed as one change because either alone makes things worse. **(A)** The engine cleared
  `Status_Push` inside the roll-stop movement path; the ROM's roll-stop block writes only the
  roll bit, radii, `anim` and `y_pos` (`sonic3k.asm:22979-22990`, `:28216-28231`, and **S2's
  `Sonic_CheckRollStop` `s2.asm:37051-37061` matches**), while `Animate_Sonic`/`Animate_Tails`
  clear Push on `anim != prev_anim` — **after `Sonic_RecordPos`**. So the engine cleared one
  routine early and a push-free byte entered the follower history. **(B)** To compensate, the ICZ
  swinging platform opted into a **second, ROM-absent status read** at delay 17;
  `TailsCPU_Normal` loads the delayed input and the delayed status byte from the *same* buffer
  slot (`:26696-26705`) and performs no one-frame-later read. Removing (A) alone reds the ICZ
  complete-run; removing both leaves it **green** and moves the ICZ segment frontier 1818 → 1983.
  Two unit tests pinned the removed behaviour and were **inverted with equal strength and ROM
  citations**, not relaxed.

- **Diagnosed and withheld: the roll-stop push clear runs one routine too early
  (`bugfix/ai-icz-1818-rollstop-push-timing`, docs merged 2026-08-15).** The ROM's roll-stop
  block (`sonic3k.asm:22981-22986`) clears only the roll bit and sets radii and `anim`; it never
  touches `Status_Push`. Push is cleared by `Animate_Sonic`/`Animate_Tails` on
  `anim != prev_anim` (`:29364`, `:29686`) — **which runs after `Sonic_RecordPos`**. The engine
  clears it inside the roll-stop movement path instead, so a push-free byte enters the follower
  history ring and Tails' CPU routine 6 reads the wrong bit sixteen frames later. Removing the
  eager clear moves the ICZ segment frontier 1818 → 1983 — **and reds
  `TestS3kIczCompleteRunTraceReplay`, the named stop condition, so it was not landed.** Root
  cause of that regression identified: the eager clear was **masking a separate leader push
  over-set** around complete-run frame 6100. The ordering correction is right and must land
  together with the second fix; both are recorded with probe evidence.

- **REFUTED: the S3K bottom camera clamp is not dead code, and the engine was correct
  (`bugfix/ai-s3k-camera-bottom-clamp-refutation`, merged 2026-08-15).** Three rounds concluded
  `loc_1C202` was unreachable because `Get_LevelSizeStart` writes `Screen_Y_wrap_value = -1`
  (`sonic3k.asm:38093`), making its subtraction unable to borrow. That write is real but
  **survives exactly one `DeformBgLayer` call**: ten lines later `j_LevelSetup` runs
  unconditionally for every level and writes `move.w #$FFF,(Screen_Y_wrap_value).w`
  (`:102205`). With `$FFF` the subtraction borrows, `bcs` is taken, and `loc_1C216`'s
  `move.w 6(a2),d1` performs the hard clamp — every gameplay frame. Instrumentation confirms
  the engine does exactly this: it computes the full `$600` grounded step to `$396` and clamps
  to `$390`, matching the ROM in **outcome and mechanism**. So the earlier
  12-red/0-green measurement was hardware correctly reporting a wrong change, the "knowingly
  wrong in mechanism" caveat is **withdrawn**, and the discarded `CameraRules` gate must never
  land. *(A follow-up claim that the engine never enables S3K vertical wrap was itself refuted —
  see the next entry.)*

- **Also refuted: S3K vertical camera wrap is implemented and heavily exercised
  (`bugfix/ai-s3k-vertical-wrap-reachability`, merged 2026-08-15).** The suggestion that the
  engine never takes the ROM's *wrap* arm is false on both clauses. `LevelManager` calls
  `setVerticalWrapEnabled` for every level whose `LevelSizes` ystart is negative, and the engine
  implements both arms of `loc_1C202`. Execution evidence: a probe during the ICZ segment replay
  printed `enabled=true range=0x800` five times — exactly ICZ1's ROM `Screen_Y_wrap_value`
  (`$7FF`) + 1. Scanning all 202 committed S3K fixtures for wrap-magnitude single-row drops
  found the arm **heavily exercised and replaying correctly**: ICZ1 ten bottom wraps on the
  Sonic+Tails route and three on Knuckles', SOZ2 three bottom plus one *top* wrap, MGZ1 two at
  `$1000` magnitude. Correction to the record: the `$7FF` writer at `:110069` is **ICZ1**
  (`loc_53648`), not ICZ2 — ICZ2 has ystart 0 and does not loop. Two residual gaps are recorded
  but unmeasurable on current fixtures: non-looping levels with yend `$1000` would wrap on ROM
  where the engine clamps, and ICZ1 changes its wrap value mid-act in a way a per-level latch
  cannot follow.

- **Investigated and deliberately not landed: the S3K per-frame bottom camera clamp
  (`bugfix/ai-s3k-perframe-bottom-clamp`, 2026-08-15).** S3K has three camera-Y limits and only
  one is dead: `loc_1BF9C` is a live load-time `max_Y` clamp, `loc_1C1F4` a live per-frame *top*
  clamp, and `loc_1C202` a per-frame *bottom* clamp made unreachable by `Screen_Y_wrap_value = -1`.
  Gating the engine's per-frame clamp to match reds twelve classes for zero greens, and
  hand-tracing `MoveCameraY` explains why: for AIZ1 frame 0 **the ROM listing itself predicts
  `$396`** — exactly what the engine produces ungated — **while hardware records `$390`**. The
  recordings contradict not just the dead-clamp reading but the ROM's own scroll arithmetic,
  which means the ROM is not executing `MoveCameraY` there at all; the fixture confirms the
  camera and player never move for 350+ frames, both axes frozen at the load-time formula. So the
  engine's clamp stays, knowingly wrong in mechanism, compensating for a scroll step the ROM
  never takes. The correctly-posed question — why the engine takes a camera step where the ROM
  takes none — is recorded with a probe order for whoever picks it up.

- **The Super Emerald arena gets no title card, as the ROM branches around it
  (`bugfix/ai-s3k-arena-restart-no-title-card`, merged 2026-08-15):** `Level:` installs
  `Obj_TitleCard` only at `loc_62B6`, which first compares `Current_zone_and_act` against
  `$1701` and branches away (`sonic3k.asm:7730-7736`) — so the arena requested by `loc_618AC`
  never creates the title owner and never enters the `loc_62CC` wait loop; its load assembles
  and presents inside one iteration. The recording agrees: the frame after the last MHZ row is
  already the fully assembled arena, with zero held rows. The ROM's own literal comparison is
  ported into the existing per-game zone-feature hook that already carries the AIZ1-intro arm of
  the same branch, so **no shared transition machinery changed** — one method, one game module.
  MHZ segment errors fall 13 → 9, with `x`, `y` and `camera_x` now matching exactly.

- **A Super Emerald restart no longer places the player at the big-ring return position
  (`bugfix/ai-s3k-arena-restart-start-position`, merged 2026-08-15):** `loc_618AC` writes
  `move.b #0,(Last_star_post_hit).w` as part of the restart request (`sonic3k.asm:128419`), and
  `loc_1BE46` restores a saved position **only while that flag is non-zero**
  (`:38148-38151`) — otherwise the load reads `Sonic_Start_Locations`, whose last entry is the
  arena itself (`:38144`). The engine had no model of that flag, so its big-ring-return branch
  placed the player at the MHZ capture position instead of the arena start. Probe-verified:
  placement moves from `0x01D6,0x0678` to `0x1640,0x03AC`. **This lands as accuracy work that
  stands without any trace — the assertion does not move**, because a separate fresh-title-card
  transition boundary re-zeroes both player slots after the load and is not released until an
  iteration that has not happened by the compared row. That boundary is now precisely located
  and is the next target. Flagged and not fixed: the modelled flag has no engine re-arm site
  because the arena exit is not implemented.

- **The giant ring takes its Super Emerald branch in S&K-half levels
  (`bugfix/ai-s3k-ssentryring-super-emerald-branch`, merged 2026-08-15):**
  `SSEntryRing_Main`'s collision branch (`sonic3k.asm:128283-128291`) reaches the
  `moveq #50,d0 / jmp (AddRings)` payout only when `SK_alone_flag` is set, when
  `SSEntry_CheckLevel` reports an S3 level (`Current_zone` below 7 and not 4), or when
  `Super_emerald_count` is also 7 — otherwise it falls through and locks the player into the
  capture sequence. The engine's predicate stopped at "all Chaos Emeralds collected", so MHZ's
  ring paid out 50 rings where the ROM starts a Super Emerald run. `SSEntryFlash_GoSS` then
  restarts into zone `$17` act 1 rather than entering a special stage, which the engine also
  lacked. **The discriminating evidence was not the reported field:** dumping every field at the
  failing frame showed `rings` expected 3, actual 53 — the engine had *gained* 50 rings, naming
  the branch in one step where the animation id alone would not have. MHZ segment errors fall
  36 → 13 over an unchanged 1265 compared rows, and its residual is now a single clean cause:
  the zone `$17` arena's start position, camera and Kosinski art.

- **MHZ1's cutscene stops vertical speed, and the HCZ bar releases on a fresh press
  (`bugfix/ai-s3k-mhz-cutscene-stop-and-hcz-bar-release`, merged 2026-08-15):** two independent
  defects that shared a misleading appearance. `Obj_MHZ1CutsceneKnuckles` calls `Stop_Object`,
  which clears `x_vel`, `y_vel` **and** `ground_vel` (`sonic3k.asm:177552-177556`); the engine
  cleared only two of the three, so accumulated gravity carried into the cutscene clamp. And the
  HCZ breakable bar's release test masks the **pressed** byte of `(Ctrl_1).w` only —
  `andi.w #button_A_mask|…,d1` with low-byte masks (`:42820`), the held byte living at `+8` as
  the neighbouring `btst #button_up+8,d1` shows — while the engine also accepted a *held* jump,
  releasing the grab after one frame instead of the ROM's fifteen. Frontiers move 315 → 1211
  (errors 210 → 36) and 561 → 1434.

- **The ICZ snowboard ignores its scripted slopes while airborne, as the ROM does
  (`bugfix/ai-s3k-icz-snowboard-airborne-slope-gate`, merged 2026-08-15):** the ROM reaches the
  scripted-slope x-window tests only through `loc_39502`, entered solely when the air bit is
  clear — `btst #Status_InAir,status(a2) / beq.s loc_39502` (`sonic3k.asm:76797-76798`). The
  airborne branch instead caps `x_vel` to `$1000` and `y_vel` to `-$200` and jumps past the
  windows (`:76799-76811`). `IczSnowboardIntroInstance` tested those windows unconditionally, so
  a player crossing `$1310` mid-jump was handed to the slope table the ROM ignores. **The
  "engine is exactly half a pixel behind" reading was an artefact of field ordering** — `x_sub`
  merely sorts ahead of `x`, and at the failing frame `x`, `y`, `y_sub`, `y_speed`, `camera_x`
  and `camera_y` all diverge together. The ICZ segment frontier moves 470 → 1818 and its errors
  fall 5197 → 3031 over an unchanged 17947 compared rows.

- **The walk/run animation handler reads past its script terminator, as the ROM does
  (`bugfix/ai-s3k-walkrun-script-overflow`, merged 2026-08-15):** the ROM's walk/run handler
  fetches its frame byte with an unchecked `move.b 1(a1,d1.w),d0` and tests only for the `-1`
  terminator (`sonic3k.asm:24859-24864`) — there is **no bounds check on the index**. After a
  Tails carry that is reachable: `Sonic_Control` skips `Animate_Sonic` entirely while
  `object_control` bit 1 is set (`:22008-22010`), the carry attach writes
  `move.w #$22<<8,anim(a1)` which zeroes `prev_anim` too (`:27390`) so the release compare
  matches and `anim_frame` is never reset, and `Tails_Carry_Sonic` has meanwhile advanced that
  shared `anim_frame` across a 17-entry table. `AniSonic00` is only 10 bytes, so index 13 reads
  *into `AniSonic01`* and the hardware shows mapping frame `$24`. The engine clamped the
  out-of-range index to 0 and showed `$07`. Animation scripts now carry the raw ROM bytes
  following their own terminator, so an out-of-range index resolves through the real ROM window
  instead of being clamped. Instrumented rather than inferred: a probe printed
  `idx=13 remaining=9`, predicting an 11-frame hold of `$24` — which is exactly what the
  recording holds, followed by `$07`, `$08`, `$01`. The MHZ segment frontier moves 75 → 315 over
  an unchanged 1265 compared rows.

- **S3K's zone/level table covers all 24 ROM zones
  (`bugfix/ai-s3k-zone-level-table`, merged 2026-08-15):** eleven segment classes could not load
  at all, throwing `Index 23 out of bounds for length 22` and friends from
  `LevelManager.loadCurrentLevel`'s `levels.get(zone).get(act)` against a 22-entry registry.
  Four independent 48-entry ROM tables — `LevelPtrs`, `LevelSizes`, `LevelMusic_Playlist` and
  `OffsAnPal` — are all indexed `zone*2 + act`, so the ROM's zone axis runs 0–23 with two acts
  each, and three of them label every slot: zone `$16` is LRZ Boss / HPZ, zone `$17` is DEZ Boss
  / the Super Emerald special-stage arena, and zone `$0D` is the AIZ intro / ending scene. The
  registry is rebuilt to 24×2 with names, music and start positions transcribed from those
  tables. **The trace directory names were misleading and are not evidence:** `dez23` is not
  Death Egg act 2 — seven of its eight segments are the zone-`$17` special-stage arena, each
  sitting between an MHZ segment and a special stage, confirmed independently by
  `Sonic_Start_Locations` entry 47. All eleven now load and fail attributably at frame 0 on the
  known cold-start shape, which was the goal: an unloadable segment became a comparable one.
  Forced by the same tables, the competition-zone ids were found to be off by one — the ROM has
  ALZ `$0E` … EMZ `$12`, not `$0D`…`$11`, because `$0D` is the intro/ending pair — and the
  palette cycler and three tests that encoded the old ids were corrected to the ROM's.

- **The S3K special-stage entry ring no longer freezes the camera
  (`bugfix/ai-s3k-ssentry-ring-capture`, merged 2026-08-15):** `Obj_SSEntryRing`'s capture tail
  (`sonic3k.asm:128292-128304`) writes `mapping_frame = 0`, `anim = $1C` and
  `object_control = $53` on Player 1 — repeating the triple on Player 2 only under
  `Flying_carrying_Sonic_flag` — and **never touches the camera**. `Camera_X_pos` settles on its
  own because `object_control` bit 0 skips `Sonic_Modes`, so `ScrollHoriz` still runs its last
  step on the capture frame. The engine instead called `camera.setFrozen(true)`, an invention
  with no ROM counterpart, which dropped that step, and it never wrote the animation state. Both
  are now ported and cited. **`TestS3kSonicTailsAizSegmentTraceReplay` goes green at
  `error_count 0` over the same 2290 rows as before** — the first S3K segment class to go green
  from this frontier — and LBZ and CNZ improve by 2659 and 397 errors, with their residual
  honestly re-labelled as the separate defect it is.

- **Leftover pending hardware submissions at close are reported, not fatal
  (`bugfix/ai-demote-pending-submissions`, merged 2026-08-14):** the sibling of the tripwire
  below, and the same two-events-one-message shape — leftover submissions at close are a
  genuine contract concern in a converged run, but in a diverged run they are a downstream
  symptom of the engine never reaching the ROM's submission or drain points. The assertion
  gated 14 S3K segment classes and hid large physics reports inside each one (Aiz2 concealed
  1033 errors with its first at frame 0; Icz concealed 5196, first at frame 470). The single
  final-run path now raises a dedicated exception that the replay port catches **only when the
  driver has opted in** by owning a comparison report, recording an exact
  `hardware_timing.pending_submissions` error on the last row the comparison reached — so it
  can never displace an earlier divergence as the first error. Everything else still aborts,
  including the prefix-end check, which belongs to a different close contract. Nothing is
  released, and the demotion cannot become silence: a driver with nowhere to record still gets
  the original abort, and an opted-in driver that never drains cannot install another run.
  Error counts confirm nothing was silenced (1033 hidden → 1034 reported; 5196 → 5197). The
  red-class set is identical by name in both directions.

  **This reshaped the S3K map.** Ten of the fourteen unmasked first errors are at frame 0 and
  are unseeded segment-entry state, joining the existing frame-0 clusters — which makes
  segment-bootstrap seeding the largest single S3K cause by a wide margin rather than a
  nine-class cluster.

- **An unmatched recorded hardware completion is reported, not fatal
  (`bugfix/ai-demote-unmatched-recorded-completion`, merged 2026-08-14):** the replay port
  aborted the whole comparison the first time a recorded completion had no engine-pending
  counterpart, which **masked and outranked the physics divergence that caused it** — across
  ~45 S3K classes the real first error was hundreds of frames earlier. The assertion conflated
  two events: in a converged run an unmatched completion is a genuine contract violation, but
  in a diverged run it is a downstream symptom of the engine never reaching the ROM's
  submission point, so it cannot carry verdict authority. The three unmatched paths (no engine
  head, ordinal/fingerprint mismatch, unprepared head) now drop the edge and record it as an
  exact `hardware_timing.unmatched_completions` comparison error; boundary, kind and
  outside-a-run violations still abort. **The release side is unchanged** — a dropped edge
  never reaches `admitReadiness()`, so it releases nothing and creates no work — and an
  undrained dropped edge fails the run, so the demotion cannot become silence. 18 classes move
  from abort to a counted failure with their true first error exposed (HCZ frame 561
  `x_speed`, MGZ 321 `camera_y`, MHZ 75 `player_mapping_frame`); the red-class set is identical
  by name in both directions.

- **A held jump button no longer fires a press when control unlocks
  (`bugfix/ai-jump-press-edge-from-raw-pad`, merged 2026-08-14):** the ROM's pressed byte is
  produced once per frame by `Poll_Controller` straight from the hardware pad, independently
  of any control lock, and `Sonic_Control` copies the *whole word* — held byte and pressed
  byte together — into `Ctrl_1_logical`, skipping the copy entirely while `Ctrl_1_locked` is
  set. So a button already held when the lock lifts contributes no press on the unlock frame;
  its edge was consumed several frames earlier. The engine derived the edge from its own
  filtered held bit, which is forced false for the whole lock, and so manufactured a press on
  every control-unlock frame with jump held. In the AIZ1 intro that turned the ROM's spin dash
  into a jump, diverging 5px low at frame 1097. Affects all three games; no zone, route or
  frame is involved. The AIZ segment frontier moves 1097 → 2247 and the
  `KOS_DECOMPRESSION_QUEUE` aborts clear on both affected classes, confirming those were a
  tripwire downstream of the physics divergence rather than a queue defect. Verified across
  the full trace profile with the red-class set identical by name in both directions, so no
  comparator starvation.

- **Sonic 2 special-stage results tail stays inside its own segment (2026-08-14):** the
  ROM leaves its special-stage object loop when `SS_Check_Rings_flag` rises, but the
  emerald check, both fades and the whole bonus tally still run under
  `GameModeID_SpecialStage` -- `Game_Mode` is not rewritten until the level reload. The
  production replay path tested the engine's own `SPECIAL_STAGE` mode directly, so it
  read the engine's internal results split as leaving the recorded segment and aborted
  481 rows early. Both launcher sites now use the shared predicate that already carried
  this ROM rule.

- **Sonic 2 special-stage object passes are paced on the production replay path
  (2026-08-14):** the ROM's special-stage loop waits on its own V-int before each
  `RunObjects`, so it is paced by 68K pass duration rather than one pass per V-blank,
  and a single recorded row can own two completed object passes. That pacing existed
  but was installed only by the run-chain test harness; the production path skipped
  lag rows outright and lost the passes they carried. Ownership now lives in a shared
  owner both paths install, and the production visual replay reaches 5,200 of the
  first special stage's 5,681 rows instead of stopping at its first comparison error.

- **Sonic 2 DMA-queue-only VBlank owns its own lag row (2026-08-14):** the ROM waits
  on `Vint_CtrlDMA` before entering the special-stage fade, and that handler drains
  the DMA queue without ever polling the joypad -- so the row is a lag row for
  publication purposes even though a fade is active around it. The engine let the
  fade pre-claim the frame, silently dropping the row's own lag claim and publishing
  three player art transfers a frame early. The production visual replay now matches
  the recording 289 rows further into the first special stage.

- **Sonic 2 production visual trace bootstrap ownership (2026-08-14):** prepared
  production visual replay now adopts the state produced by the real title card,
  and the direct level-start sidekick keeps the ROM-owned leader-history prefill
  through its first CPU init. A strict EHZ1 visual canary now passes through
  cursor 4479; the original five synthetic complete-emerald chain axes are
  unchanged. The next independent frontier is special-stage frame 136 dynamic-art
  readiness, recorded separately rather than folded into this ownership change.

- **Sonic 2 spends its pre-level fade on the special-stage return (2026-08-13):** the
  ROM runs `Pal_FadeToBlack` -- 22 counted V-blank passes -- inside `Level:` before the
  title card object exists. Returning from a special stage the engine overlapped that
  fade with the title card instead of preceding it, so the return spent 87 frames where
  the ROM spends 109. Sonic 1 already declared the identical 22-frame span; Sonic 2's
  level-init profile now declares it too.

- **Sonic 2 special-stage bonus tally drains two countdowns (2026-08-13):** the ROM
  seeds a separate bonus countdown from each player's own ring word and decrements
  both, plus the emerald's 1000-point total, in the same pass -- so the tally lasts as
  long as the *longer* countdown, never their sum. The engine ran a single countdown
  holding the combined total and so overran by the smaller player's ring count, a
  per-stage error of up to 67 frames. Ring counts, score awarded and the surrounding
  hold durations are unchanged.

- **Sonic 2 level-entry seam admission census (2026-08-13):** the run recorder now
  records the main-loop admission outcome for every physical frame of a level-entry
  transition, and replay spends those rows as lag rows under the existing
  main-loop-admission contract. The seam's untimed synchronous load work -- Kosinski
  and Nemesis decode, map construction, collision conversion -- is `Vint_Lag` in the
  ROM's own terms, and the regenerated capture reproduces the hand-measured run
  decomposition independently, including per-zone `LoadZoneTiles` costs derived from
  the disassembly with no recording involved. Total art-edge error across the
  complete-emerald level seam halves (398 to 196), with two edge pairs landing within
  one row of the recording. The census carries lengths only, never a row index, and a
  lag row runs no gameplay -- it is a scheduling outcome, not trace-driven state.
  A companion fix removes a 21-frame reveal fade the engine performed on returning to
  a level: the ROM's level-entry path contains no `Pal_FadeFromBlack` at all, showing
  the title card at full intensity via `PalLoad_Now`. With both landed, the
  complete-emerald level seam's title-card art transfers now match the recording
  exactly, and the seam's total art-edge error falls by more than half. A third fix
  submits the returning level's player art where the ROM does -- at `InitPlayers`,
  after the zone tile, block map, animated block, background, and collision loads --
  rather than instantly at the start of that span, so those transfers now match the
  recording exactly too.
  Alongside it, two ROM-derived seam fixes: `Pal_FadeToBlack`'s 22 counted V-blanks
  are no longer stepped through with the movie clock frozen, and `LoadZoneTiles` now
  spends one V-blank per `$1000`-byte DMA chunk as the ROM does.

- **Sonic 1 complete-run audio observer terminal freeze (2026-08-13):** the
  source-authentic deferred tail transfer and contemporaneous M68K A7 binding
  now carry the pinned 225,101-row movie through the configured terminal gate
  on two independently built, byte-identical ABI-v4 GPGX installations. The
  freeze also re-authenticates managed collector/capability identities, real
  S2/S3K boundaries, Reset/Power lifecycle, and bounded paired performance;
  it publishes no ROM, movie, native binary, or reference capture and makes no
  semantic audio-MATCH claim.
- **Sonic 2/Sonic 3&K source-owned audio parity observer (2026-08-11):** a
  reproducible, separately installed BizHawk 2.11 GPGX observer now records
  ordered Z80 service, YM2612, PSG, DAC, DPCM, and SEGA-PCM ownership from
  reviewed disassembly boundaries. Exact movie/hash gates, Reset/Power reupload,
  savestate epochs, stock/disabled/enabled identity, bounded performance, and
  duplicate complete S2 and S3K runs are locked without shipping ROMs or native
  binaries in the repository.
- **Sonic 1 GHZ1 gameplay-audio causal timeline (2026-08-09):** the pinned
  complete-game replay now records raw caller/ROM queue requests separately
  from resolved driver/presentation admissions. Both producers agree on the
  first jump request (`$A0`, ordinal 1, frame 958); the first mismatch is now
  the meaningful one-frame admission delay (OpenGGF 958, REV01 959), while
  ring requests retain raw `$B5` and admit resolved `$CE`. The hardened runner
  pins the installed BizHawk core assembly and GPGX binary, proves both music/SFX
  and SFX/SFX contention, and discards failed producer staging without publication.
- **Shared movement and S3K trace-parity corrections (2026-08-06):** the
  shared movement path now keeps ROM `move_lock` semantics through a full
  dispatch, and the shared oscillator no longer gains a duplicate transition
  tick. S3K replay work also corrects Bubbler/Air Countdown off-screen
  lifetimes, results/title-card queue ownership, and fresh-level destination
  terrain admission without changing trace fixtures.
- **Sonic 1 GHZ music-driver parity harness (2026-08-09):** a pinned BizHawk
  sound-test movie and read-only driver observer now produce deterministic
  logical-state and ordered YM2612/PSG captures for comparison with OpenGGF.
  The local command verifies the ROM, movie, emulator, callback or opcode-manifest
  capture source, and repeated musical cycle before reporting the first mismatch;
  the initial reference run identifies DAC base-frequency state at tick zero
  without authorizing speculative chip-port reordering.
- **Dead and unfinished code sweep (2026-08-08):** seven unreachable Java
  types (339 lines) were removed after caller, registry, resource, CLI, and
  reflection checks; an unused 125-line Kosinski reference moved out of runtime
  resources into architecture research. Caller-free compatibility members and
  duplicate unverified results constants were also removed. Coherent but
  unwired work—including the S3K special-stage projection, debug primitive/text
  rendering, and ROM-derived CNZ boss animations—was deliberately retained.
  The accompanying audit ranks live unfinished paths without changing runtime
  behavior, including Big Arm and S3K SMPS meta commands; the separately tracked
  Mecha Sonic move-ordering debt is now resolved by the REV01 outer-loop parity fix.
- **S2 Mecha Sonic outer-loop parity (2026-08-08):** the existing DEZ ObjAF
  implementation now aligns the LED/sensor children before one outer-loop
  `ObjectMove`, matching shipped ROM `loc_398C0`/`loc_39D4A`. Focused tests and
  existing graph rewind coverage remain green; the dedicated DEZ ending replay
  passes 1/1 on both base `5cc94d457` and candidate `4b4572cc3` with verified
  REV01 ROM, with ObjAF present from auxiliary frame 127 and no frontier change.
- **S2 DEZ window and Special Stage checkpoint visuals (2026-08-09):** DEZ now
  samples Plane B from the ROM-seeded background-camera Y, restoring the moving
  star field behind the opening exterior window. In Special Stages, the
  checkpoint wings stay fixed while only the separate hand/thumb sprite bobs,
  matching the two peer Obj5A objects in the shipped ROM.
- **Headless visual-run parity driver (2026-08-05):** whole trace runs can now be
  driven through the production visual-session owners without a window, so a
  Trace Test Mode defect is reproducible in a test rather than a screenshot.
  Its first find: a run can now cross from a special stage's return
  presentation bridge back into its own act's gameplay.
- **Special Stage entry frame parity (2026-08-04):** the game mode now changes on
  the frame the ROM changes it, with the white-out owned by the special stage
  rather than the level it was entered from. The S1 giant-ring handoff no longer
  arrives 22 frames late, which had aborted complete-run visual playback at the
  destination row.
- **Visual run presentation clock parity (2026-08-04):** complete-run playback now
  keeps the shared input, PLC, dynamic-art, and trace HUD clocks continuous across
  title cards, special stages, inter-act gaps, and the terminal movie tail.
- **Visual special-stage handoff parity (2026-08-04):** held-white S1 entry
  no longer replays the transition SFX, and complete-run returns rebind the
  BK2 input cursor at level load so GHZ2 consumes and compares the same
  fall-through rows as headless replay.
- **Visual complete-run special-stage parity (2026-08-04):** S1 trace-accurate
  startup now waits for the ROM readiness boundary; special stages share the
  normal trace HUD and recorded input display; GHZ2 rebinds through the run
  boundary probe; and the live capture chord remains usable during playback.
- **Visual Special Stage physical-row handoff (2026-08-04):** giant-ring
  transitions retain the destination BK2 row until local Special Stage
  admission, keeping white-screen inputs, lag rows, and the trace HUD on one
  clock.
- **Visual Special Stage transition dispatch (2026-08-04):** shared transition
  gaps now consume engine-raised Special Stage requests even while native level
  gameplay is suppressed, so the held-white S1 results fade enters the stage
  instead of replaying inputs behind a stuck white screen.

Development since `v0.5.20260411` is the active 0.6 prerelease line. The release focus is S3K playable vertical-slice parity, trace-driven ROM accuracy, release hardening, and gameplay-scoped rewind reliability.

- **The Egg Prison button stayed pressed forever (2026-08-13):** ROM `loc_3F354`
  restores the button's stored base `y_pos` on *every* frame and re-applies its
  8-pixel depression only while something is actually standing on it — the sole
  latched value is `objoff_32`, the flag the capsule body polls to know it has been
  opened (`s2.asm:84937-84950`, `:84884-84886`). The engine latched the depression
  as well, so once either player had pressed the button its solid surface stayed
  8px low for the rest of the act. The recording shows the real button oscillating
  between `$03FA` and `$0402` throughout. The consequence appeared 3,383 frames
  later and looked like nothing to do with a button: Tails fell through the sunken
  surface where the ROM's raised one caught him, reported as a one-pixel
  `sidekick_y` difference. It was neither a pixel drift nor — as the two previous
  one-pixel reports had been — a hurt divergence; it was a **landing**, with the
  ROM going air-to-grounded and the engine simply never landing. Segment 11 falls
  from 4,192 errors to **287**, every sidekick, player and dynamic-art error in the
  segment eliminated, and the entire residual is now one subsystem: the Nemesis PLC
  queue. An invented `y_speed >= 0` trigger gate was removed at the same time; the
  ROM tests the standing mask alone.
- **The Egg Prison's button cleared the push bit its own body had set
  (2026-08-13):** ROM `Obj3E` allocates the capsule body, button, lock and broken
  half into four separate object slots (`s2.asm:84832-84865`), and
  `SolidObject_TestClearPush` releases the player's push status only when the
  *calling* object's own pushing bit is set, otherwise leaving `status(a1)`
  untouched (`:35462-35466`, `:35483-35490`). The engine keyed its push latch on
  the shared spawn rather than the slot, so within a single object pass the body
  set the bit and the button cleared it — measured directly, both objects using the
  same latch key. Sonic's animation handler then ran with pushing false and
  published walk frame `$0F` where the ROM publishes push frame `$48`. Opting the
  three Egg Prison classes into the existing per-slot latch hook, alongside 26
  prior users of it, takes segment 11 from 4,215 errors to 4,192 and advances its
  frontier 244 frames. Worth recording as a pattern: this is the second defect in
  as many days where the ROM allocates several real object slots and the engine
  models them as one — the bridge subsprites were the first, and both surfaced as
  something entirely unrelated (a sidekick despawn, and a player animation frame).
  It also disproved the hypothesis it was sent to test: the divergence is not
  inherited from the preceding transition gap, whose errors are Tails' DPLC and
  which are byte-identical either side of this fix.
- **A fifth hidden comparison, and the V-int phase behind it (2026-08-13):**
  segment 11 of the emerald run carried 4,215 physics errors that nothing asserted
  — the tail-exhaust walk-failure was rethrown before the segment report was
  written, so no report existed and no axis was reported. Writing the report first
  takes the chain from 4 axes to 5, which is a failing comparison becoming visible
  rather than a regression. That is the fifth computed-but-never-reached comparison
  in this work. The walk-failure itself is a real engine defect and is now traced
  end to end: the engine leaves `LEVEL` 28 rows before the recording does; all 27
  remaining rows are genuine level rows; `Load_EndOfAct` fires 28 frames early
  because `Obj3E` triggers it on the first frame no animal remains
  (`s2.asm:85004-85012`) and the engine's last animal dies at row 3517 against the
  ROM's 3545; the animal spawn gate is `Vint_runcount & 7`
  (`s2.asm:84969-84974`), the recording's 22 spawns land exactly on rows where the
  counter is 0 mod 8, and the engine produces 23 — because **its object-visible
  V-int counter runs a constant 33,555 behind the recorded ROM counter, and
  33555 mod 8 = 3**, so every such gate in the segment fires three phases out. The
  owning knob is per-game: S2 uses a disabled trace-playback profile where S1
  declares measured V-blank alignment values. Deriving S2's from the ROM's
  interrupt-disabled blocks is the follow-up; inventing them from this fixture
  would be exactly the fitted model this project forbids, so nothing was changed
  here.
- **The sidekick stayed clamped to a dead boss arena (2026-08-13):**
  `LevEvents_EHZ2_Routine4` is a terminal routine that does nothing until the boss
  dies and then re-copies `Camera_Max_X_pos` into `Tails_Max_X_pos` and
  `Camera_X_pos` into `Tails_Min_X_pos` on every subsequent frame. The engine
  stopped updating those bounds at defeat, so the sidekick remained clamped against
  the frozen arena maximum while the camera moved on — measured precisely, the
  right-boundary clamp zeroed its x-speed at `0x2940 + screen_width − 24 + $40`,
  exactly the value the comparator reported. Segment 11 of the emerald run falls
  from 11,849 errors to 4,215. Recorded because the headline number misleads on its
  own: the chain's `[segment-physics]` axis disappears from the failure list not
  because those errors are resolved but because the walk now aborts earlier, before
  the segment report is written. They are unasserted, not fixed — the same
  computed-but-unreached shape this work has already found four times — and
  restoring that comparison is the next target rather than a completed one.
- **A boss defeat that ran a frame early, a camera bound it was masking, and a
  contract built on a distinction the ROM does not make (2026-08-13):** three
  connected findings. `Obj56` dispatches read-once-at-head (`s2.asm:63420-63424`)
  and its defeat write sits downstream of that read (`:63665`), so the ROM runs
  the defeat routine on the *following* frame; the engine ran it immediately and
  submitted the animal/explosion art a frame early. Landing that alone was a **net
  regression**, because it stopped masking a second defect: `loc_2F460` does
  `addq.w #2,(Camera_Max_X_pos).w` straight to the boundary word
  (`:63584-63599`), while the engine went through a target with easing that runs
  ahead of the object pass — deferring every step by a frame. The pair had to land
  together, and the 171-error `camera_x` span that appeared with the first fix
  alone does not appear with both. Third, the `defeatDeferralAppliesToThisBoss()`
  contract claimed the discriminator was primary routine versus `routine_secondary`
  "dispatched fresh every frame," and forbade the deferral for Wing Fortress on
  that basis. All four bosses — `Obj56`, `ObjC5`, `ObjAF`, `Obj5D` — use the
  identical read-once-at-head idiom, and ObjC5's defeat write is downstream of its
  own head read too, so the distinction does not exist in the ROM. The javadoc now
  states read-once-at-head versus re-read-per-dispatch, and three drifted
  `loc_39CF0` citations are corrected from `:78003-78004` (which is
  `mapping_frame`/`x_pos`) to `:78091-78095`. That citation predated the change and
  had propagated into the restatement by copy — plausibly how the rule came to be
  written around the wrong distinction in the first place. Wing Fortress is
  recorded as an open question rather than changed on inference, and ARZ/MTZ
  dispatch on `boss_subtype`, which the corrected criterion does not settle.
- **Player physics is now byte-accurate across the whole emerald run
  (2026-08-13):** the last player-physics divergence came from the EHZ2 boss
  skipping the frame the ROM spends inside `Obj56_Init` (`s2.asm:63256-63325`,
  routine advanced at `:63278` and `rts` at `:63325`). The engine applied init at
  construction, so the boss ran one frame ahead, its spike landed a frame early,
  and the hurt arrived on the wrong row. Three things this corrected about the
  diagnosis it started from. The reported first mismatch — `x_speed` sign-flipped
  at frame 1259 — was **not a reflection**: ROM and engine agreed exactly on `x`,
  `y` and `g_speed` there, only the derived speed components disagreed, and they
  reconverged two rows later. It was a `CalcSine` decomposition artefact of the
  lead, and the substantive divergence began 470 rows later. The lead was **one**
  frame, not the two that had been inferred — `engine[N] == ROM[N+1]` on every
  compared row of the boss's slot. And the fix was the object-local half, **not**
  the shared zone-event spawn cadence: because the engine already initialises at
  allocation, also adopting the ROM's "an event-spawned object does not execute on
  its allocation frame" ordering would have overshot by a frame. That cadence
  change had been deliberately left unlanded as too broad to verify; it turns out
  it would also have been wrong here. Across the entire eleven-segment run, **zero
  rows now mismatch on x, y, x_speed, y_speed, g_speed, angle, air or rolling**,
  down from 1,451. Segment 11 falls from 30,707 errors to 11,893 and its frontier
  moves off player physics entirely, onto a PLC-queue field.
- **The EHZ2 boss moved before it checked where it was, and the camera took the
  blame (2026-08-13):** the emerald run died in its last level, and the visible
  symptom was a camera 61–80px right of the recording — which turned out to be a
  *downstream* effect measured 30 rows after the real divergence. The engine
  matches the ROM exactly through row 1267; the player diverges at 1268 and the
  camera only at 1298. The ROM's camera is simply pinned to the EHZ2 boss arena
  bounds (`LevEvents_EHZ2_Routine2` writes `$28F0`/`$2940`, `s2.asm:20428-20441`),
  which the engine already had right — it sat elsewhere in that band because its
  player was elsewhere. The cause was the boss vehicle running four frames ahead.
  `loc_2F27C` and `loc_2F2BA` both compare the arrival position *before* stepping,
  so the ROM spends each arrival frame in the follow-on routine without moving; the
  engine moved first and tested after, and clamped a y the ROM never clamps. Since
  the damaging spike sits at vehicle x − `$36`, it was 6–8px left of the ROM's, a
  leftward-rolling player reached it a frame late, and the hurt tail's
  `subq.w #5,y_pos` radius restore landed late. The knock-on was severe and
  entirely invisible: the shifted hit cost the player a spilled ring whose
  on-screen latch never set, so its floor probe was skipped, it fell through the
  floor, and the engine met the next hit with no rings and **died** — restarting
  the level and breaking segment ownership. With the ordering corrected the run
  survives, and **segment 11 produces a comparator report for the first time**
  (30,707 errors). Two further frames of the boss's lead are measured and cited but
  not landed: the ROM does not execute the boss on its allocation frame, and
  `Obj56_Init` consumes another — both touch shared zone-event spawn cadence.
- **Bridges never let a player balance, and they allocate real child objects
  (2026-08-13):** two ROM facts about `Obj11` that the engine had modelled by
  approximation. First, the ROM writes a **fixed** `move.b #$80,width_pixels(a0)`
  in `Obj11_Init` (`s2.asm:21951`) regardless of how many logs a bridge has, and
  `Tails_Move` reads that field for its balance window (`:39712`) — since a
  bridge's standable span is at most ±96, a `$80`-derived window is unreachable,
  so **the ROM never lets Sonic or Tails balance on a bridge at all**. The engine
  derived the width from real log geometry (96) and let Tails balance, which
  pinned his tails object on Blank and suppressed a dynamic-art transfer. Second,
  `Obj11_Init` allocates one or two **real object slots** per bridge via
  `Obj11_MakeBdgSegment` (`:21966-22009`), each inheriting the parent id `0x11`;
  the engine drew those subsprites as overlays and allocated nothing. That left
  the slot the sidekick had landed on empty, so `TailsCPU_CheckDespawn`'s
  `cmp.b id(a3),d0` (`:39423-39425`) mismatched and despawned Tails where the ROM
  keeps him — the engine landing on slot 21 where the ROM lands on 23, exactly the
  two-slot deficit. Segment 7 of the emerald run falls from 22,458 physics errors
  to **zero**, and every one of its eleven segment reports is now clean. The first
  attempt at the child allocation also broke rewind outside the trace profile
  (`RewindIdentityTable is required for player-reference rewind fields`), caught
  only because the classes live in `com.openggf.game.rewind.**` which
  `**/tests/trace/**` cannot see; the links are now relinked by parent lookup as
  the ARZ platform, Egg Prison and checkpoint-dongle children already do, with no
  baseline exemption.
- **A third unasserted comparison, and the one-frame sidekick behind it
  (2026-08-12):** the emerald chain looked two axes from done. It was not: segment
  7 carried **149,522 physics errors** with `complete: true` and nothing asserted
  them, because the assertion covered only special-stage returns and that segment
  is entered by `level_advance`. That is the third comparison in these chains found
  computed-but-unasserted, after the returned-level segment (58,184) and the
  transition-gap journal, which had never been evaluated once. Registering every
  segment attached over a level boundary surfaced it — one axis added to a class
  already red, with the profile numerically identical and the red-class list
  unchanged. What it exposed was precise: the **sidekick was exactly one frame
  ahead at the destination's row 0** while the player matched exactly, with the
  engine's value at row N equalling the ROM's at row N+1. `GameLoop` carries a
  one-row latch meaning "this gap row still belongs to the source level's main
  loop"; a locked title-card iteration returns `SETUP_ONLY` and never reaches the
  gap-body suppression test, so across a level advance the latch survived all 26
  title-card rows and was consumed by the release row — a 27th pre-row-0 object
  pass where the ROM gives 26, and the only such row in the whole 35-segment run.
  The ROM cannot dispatch there: the release is `Level_StartGame`
  (`s2.asm:5081-5082`) and `Level_MainLoop` runs `PauseGame` and `WaitForVint`
  before its `jsr (RunObjects).l` (`:5088-5095`), an ordering that holds in S1 and
  S3K too. Segment 7 falls to 23,128 errors, the player stops dying short of the
  star post, its `starpost_special` boundary is observed for the first time,
  segments 8–10 report zero, and the walk advances four segments. It also clears a
  pre-existing `TestS1CompleteEmeraldVisualRun` error.
- **The engine ran a level body across a run-chain transition gap, and a static
  latch decided gap behaviour from whatever ran before (2026-08-12):** the
  emerald run's `seg4_ehz1 → seg5_ehz2` gap emitted 72 art edges against a
  recorded 12. A stack-trace probe put every surplus edge on
  `SpriteManager.tickPlayablePhysics` — the ordinary level body, running for all
  163 gap rows with the mode never leaving `LEVEL`. The production suppression
  gate was already correct: `suppressesRunNativeLevelBody` requires an installed
  `TraceRunFrameDriver`, and the chain harness installed one for special-stage
  interiors and terminal tails but not for level→level gaps, so the gate was inert
  and the adapter stepped the whole gap with a bare `loop.step()`. Driving those
  steps through an installed driver removes all 60 surplus edges using only
  pre-existing production rules — no count, window, offset or position was
  introduced. The same work left a static latch armed at one of eight `gapOpened`
  sites and consumed on a different path, so the value any gap saw came from an
  earlier gap or an earlier test class in a reused surefire fork — this project's
  documented flaky-test shape. It is now a per-session field on
  `GameplayModeContext` and the static is deleted, with behaviour identical across
  isolated, paired and full-profile runs.
- **The Sonic-and-Tails all-emeralds S3K run is captured, and the trace profile
  has the heap it measures (2026-08-12):** the run lost in an earlier revert is
  re-captured and landed — 63 segments, 514,619 rows, 40 transitions, with 64 new
  replay classes. The revert is vindicated by measurement rather than argument:
  the recorder's Kosinski "backreference precedes output" check was kept fully
  intact and **never fired**, confirming that its removal was never necessary and
  that the sampling-race fix was the real cause. Three special-stage classes pass
  outright; the rest are frontier harnesses reporting precise first divergences,
  and they surface genuinely unimplemented territory — `HPZ22`, `DEZ23` and `DDZ`
  act 2 have no engine level-list entries at all, this being the first committed
  route to reach them. The capture also made the profile die with an
  `OutOfMemoryError` that presented as a *better* result — 401 tests, 1 failure, 2
  errors — because every class after `TestS3kMegaRunChain` alphabetically, including
  the 64 new ones, never ran. Measured with GC logging, the profile's peak live set
  is 2,069 MB, 21 MB above the 2 GB it had; it now runs at 3 GB and completes all
  834 tests. The trace-replay profile therefore grows from 770 to 834 tests, with
  the new run's payloads committed compressed (133 MB, largest single file 11 MB)
  under `src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/`.
- **The S2 title card holds for its PLC drain, not a fitted 60 frames
  (2026-08-12):** `TitleCardManager` carried `DISPLAY_HOLD_DURATION = 60` under a
  comment openly rationalising it as a stand-in for hardware decompression time —
  the sixth invented duration found in this line of work. It needed no recorded
  data to remove, because the ROM computes it: `Level_TtlCard` loops while the
  zone-name piece is off-target **or** `tst.l (Plc_Buffer).w` is nonzero
  (`s2.asm:4914-4924`), and `ProcessDPLC` decompresses exactly six patterns per
  VBlank (`:2202-2213`, the ROM's own comment noting S1 processed nine). The hold
  is therefore outstanding patterns ÷ 6, and S2 now matches the S1 implementation
  that already modelled it. Recorded plainly: this has **zero** measured effect on
  any trace axis, because the replay path routes headless loads through the
  skipped-presentation lifecycle and never executes this code — it is an accuracy
  fix for the visual path, and nothing in the suite yet compares visual
  title-card length against a recording. The investigation that produced it also
  retired a live design question: the surplus gap edges were thought to need the
  recorded hardware-timing sidecar, but with the drain rate a ROM constant the
  duration is fully derivable, so no contract amendment is warranted. The 60
  surplus edges and the 144 un-vsynced level-load rows behind them remain
  unexplained.
- **The post-act fade is a frozen fade, and the chain admits a level the way
  production does (2026-08-12):** two more divergences behind the emerald run.
  ROM `Level_MainLoop` tests `Level_Inactive_flag` in the instruction immediately
  after `RunObjects` and branches straight back to `Level` (`s2.asm:5095-5097`),
  which runs `ClearPLC` then `Pal_FadeToBlack` — a `move.w #$15,d4` plus `dbf`
  loop of 22 iterations doing `WaitForVint`, palette work and `RunPLC_RAM`, with
  **no `RunObjects` at all** (`:3370-3382`). The engine ran those same 22 frames as
  live gameplay, submitting a fresh player DPLC on nearly every one. The span
  length already matched; only its content was wrong. An earlier attempt at this
  was correctly rejected as a net wash because it introduced a `camera_y`
  mismatch — that turned out to be a missing one-object-pass split rather than the
  freeze itself, and with the split landed as its precondition the regression does
  not appear. Separately, the chain harness admitted a level destination one-shot
  where production polls `beforeAdmission` every tick and steps while denied; the
  coordinator's refusal was correct, since the destination act's title card was
  still running and the cursor sat 150 rows short. Stepping until admissible is
  additive — a boundary already admissible exits on iteration zero. Surplus gap
  edges fall 84 to 60 and the run now reaches past segment 7's last recorded row.
  Recorded honestly: neither closes its axis. The remaining 60 surplus edges are
  **not** the fade, no segment-7 report is emitted yet so nothing establishes that
  EHZ2 compares clean, and segment 6 still carries 5 errors on its terminal row.
- **Four S2 gameplay divergences behind the emerald run's last segment
  (2026-08-12):** with the halfpipe chain green, the emerald chain's remaining
  failures decomposed into four real engine defects, each found by a comparison
  that had only just become capable of failing. The special-stage return never
  re-established the sidekick's level boundaries — ROM `LevelSizeLoad` writes
  `Tails_Min/Max_X/Y_pos` from the same `LevelSize` longs as the camera bounds on
  every entry (`s2.asm:14695-14706`) — so `Tails_Max_Y_pos` stayed unset and
  **Tails' kill plane was disabled for the whole rest of the run**; the dead-fall
  threshold resolver returned `Integer.MIN_VALUE` on all 864 calls. End of act
  fired on the bug-*fixed* branch of `Obj0D_Main_State3`, a `fixBugs` site
  (`:34815-34838`) whose shipped `fixBugs = 0` path lets an airborne player skip
  only the control lock and still trigger — the engine instead burned 29 extra
  airborne frames before queueing the results art. `CheckpointState` mirrored the
  ROM's `Saved_*` set but omitted `Saved_Timer` (`:44783-44785`), so the act timer
  restarted at every return and the time bonus tallied from 12 seconds instead of
  188. And the results screen carried yet another invented duration — a 60-frame
  slide where the ROM derives 16 — the fourth fitted "N seconds" constant found in
  a results sequence this session. Segment 6 falls from 13,836 errors to 5, every
  ROM phase length now matches exactly (16/180/27/180), and the level-load
  boundary the run had never reached is now reached.
- **`TestS2EhzHalfpipeRoundTripChain` is green, and the S2 special stage runs the
  ROM's results length (2026-08-12):** three fixes closed the last of the run
  chains' transition-gap divergences. The gap journal was opening on a ledger its
  own boundary batch had already mutated, and the harness read the snapshot before
  the close that records it — two opposite-signed instances of the same
  wrong-moment fault, one leaving an outstanding transfer missing from the opening
  ledger and the other leaving a retired one present. Separately, the engine's
  special-stage results screen carried two invented durations — a 60-frame "1
  second slide-in" where `Obj6F` slides 288px at 16px/frame and arrives on frame
  19, and a 180-frame "3 seconds after tally" where the ROM holds `$78` = 120
  (`s2.asm:28537`, `:27494`, `:28399-28400`, `:28428-28430`) — a combined 101
  frames, with the ROM's real length being ring-count dependent, which is why the
  overrun varied per run. Finally, gap art edges were stamped from a playback
  cursor pre-seeked to the destination and frozen there (measured: 3,908 calls
  announcing the same row). The engine's cadence was already exactly right,
  anchored at the *end* of the gap — its edge iterations counting back from
  admission are −26, −26, −25, −25, −10, −9, −2, −1 in all five gaps, matching the
  recorded offsets identically — so edges are now stamped by counting rows that
  passed with nothing announced. That is an identity where the cursor is live and
  the only available answer where it is frozen; counting *iterations* instead
  regressed the S1 visual run, whose gap genuinely advances its cursor, because
  iterations and movie rows are different clocks and only S2's freezes. The
  halfpipe chain now passes in full, every `run_gap` axis of the emerald chain is
  green, and the profile improves to 770 tests / 1 failure / 3 errors.
- **The run chains adopt the destination row that already ran in the gap
  (2026-08-12):** the last segment-physics error on both S2 chains was a row the
  harness could not compare at all. The destination segment's row 0 — the
  recorder's row 0, at `bk2_frame_offset` — is executed while the run is still
  structurally in the transition gap, because admission is polled between steps and
  the destination cannot report `LEVEL` mode until the row has already run. Art
  submitted on it was therefore stamped `run_gap` and never compared as a segment
  row, which the lifecycle's own javadoc described as a deliberate skip. Rather
  than move the transition mid-frame — a new coordination point across four
  components — the gap-resident opening row is now adopted when admission fires:
  the last iteration's ledger tail moves into the opening segment as row 0, with
  only submissions re-stamped (completions whose submission genuinely stayed in the
  gap keep `run_gap`), re-buffered at logical frame 0 and published through the
  ordinary comparator inside the existing first-publication window. The clock
  compensation it replaces survives intact for gaps with real gap-side production.
  The row is *compared*, not relabelled — a new test fails if any adopting site
  stops comparing, verified by mutation in both directions. Production compares it
  too, closing a two-paths-should-agree gap where the launcher had published it
  uncompared. The bonus-stage interior is explicitly excluded: its consumed row is
  not row 0's analogue, since it re-anchors past rows the engine never ran, and
  that path skips locally so it must not silently take a new branch. Both chains
  lose their returned-level segment-4 physics axis and the surplus edge on the
  later gaps; the emerald chain drops to 8 failing axes and the halfpipe chain to
  3, all now dynamic-art gap row-placement rather than engine behaviour.
- **A throwaway fixture load was priming the art ledger before the replay
  started (2026-08-12):** with the run chains' gap comparison finally reachable and
  the surplus-work defects closed, the gap axes failed only on identity and clock.
  Both had mundane causes and neither was in the engine's art pipeline. The
  engine's transfer ids ran a constant +2 and edge ordinals +4 ahead of the
  recording because a throwaway `HeadlessTestFixture` level load ran before the
  replay's own load and primed both playables' DPLCs — transfers 0 and 1, ordinals
  0 to 3 — with nothing resetting the dynamic-art run, since the gameplay context
  only begins a run when none is active. It is the same class of pre-boot leakage
  the replay bootstrap already zeroes for the RNG seed, and it is now zeroed there
  too. Separately, the chain announced the real movie row only for rows owned by a
  frame driver; ordinary segment rows, mode waits and boundary crossings announced
  nothing, so the ledger fell back to counting production iterations from zero.
  It now states the row from the shared playback cursor, the same source the
  production visual path uses. Every transfer-id and edge-ordinal error is gone
  from both chains, one gap is fully green, and gap field errors fall 107 → 68 on
  the halfpipe chain and 190 → 138 on the emerald one. Two residuals are named
  rather than absorbed: a gap's edges all carry the destination segment's own
  `bk2_frame_offset` where the recorder spreads them over the preceding 26 rows,
  which is harness choreography and was deliberately not closed with an offset; and
  the engine runs more production iterations during a special-stage segment than
  the movie has rows (83 and 66 on the halfpipe run), which is plausibly by design
  but is now stated rather than counted, so it no longer distorts this axis.
- **`RunObjects` never reaches the level-only slots while the title card holds
  (2026-08-12):** the engine was making two dynamic-art transfers at every
  special-stage return that the ROM never makes — the whole of the transition
  gap's edge-count divergence. A BizHawk PC-execute capture on `Obj05_Main` showed
  the real ROM executing it first at `Level_frame_counter == 1` on *both* a fresh
  entry and a return, with hooks armed 326 and 302 frames earlier recording none.
  The reason is structural rather than anything to do with Tails: `RunObjects`
  picks its slot count from the game mode, walking only the first `$80` slots
  unless `Game_Mode` equals `GameModeID_Level` exactly
  (`s2.asm:29805-29819`), and `Level` sets `GameModeFlag_TitleCard` on entry
  (`:4758`) which only `Level_StartGame` clears just before `Level_MainLoop`
  (`:5087`). So every pre-main-loop pass runs with the flag set and never reaches
  `LevelOnly_Object_RAM`, which begins at `Tails_Tails`. The engine ran those slots
  anyway, and Tails' tails object took 16 spurious passes while the parked,
  control-locked Tails had animated into Wait. Being a slot-range rule it also
  covers spindash dust, shields, bubbles and invincibility stars. S3K's
  `Process_Sprites` has no such gate and S1 has no level-only fixed-slot family, so
  it is a `TitleCardProvider` predicate rather than a game-name branch. Four
  surplus edges leave each return gap in both chains; `ss → seg2_ehz1` now matches
  exactly, and the single edge still surplus on the later gaps is the separately
  tracked row-0-executed-in-gap defect.
- **The gap journal sampled its opening ledger one batch too late (2026-08-12):**
  with the gap comparison finally reachable, the level→special-stage gaps reported
  `run_gap.edge_count` expected 1, actual 0 — the engine appearing to emit nothing.
  It emits exactly the right edge; the journal sampled the ledger *after*
  `endComparisonSegmentAtRomModeChange()` had appended the boundary batch, so the
  edge fell outside the compared slice and its transfer had already left the
  opening ledger. Recording gap-opening state at the segment boundary itself, in
  both implementations of the contract, restores the recorded edge on every such
  gap with phase, owner, mapping frame and requests all matching. The comparison
  got *stricter*, not looser — more edges are now present and compared, so field
  comparison counts rise (halfpipe 135 → 162, emerald 206 → 271). No axis closed
  yet, but three residual defects are now cleanly separable: a wrong
  `movie_logical_frame` clock base (the chain never calls `setMovieLogicalFrame`,
  so it counts production iterations from zero while the production visual path
  announces the real movie row — the error is exactly the first segment's
  `bk2_frame_offset`), an identity skew where the engine's transfer ids and edge
  ordinals run ahead of the recording, and an extra player-art owner at every
  return, where the engine submits three DPLC transfers against the recording's
  two.
- **The run chains' transition-gap comparison had never once been evaluated
  (2026-08-12):** `DynamicArtGapJournalProbe.verify(run)` sat after
  `assertChainReplay`'s try/catch, and the catch rethrew — so any earlier assertion
  aborted the run before the gap ledger was ever compared. Both S2 chains died in
  `assertReturnedLevelSegmentPhysics`, meaning the transition-gap comparison graded
  nothing for the entire life of those tests. This is the same defect shape as the
  unasserted returned-level segment above, one layer down, and it is why that
  segment's fixes could look complete while the gap ledger stayed wrong. Every
  chain axis is now recorded rather than thrown — segment physics, every structural
  gap, the terminal tail, and any walk failure — and a run fails once at the end
  enumerating all failing axes, each tagged, with a gap report artifact written
  whichever axis failed. Only the throw *site* moved: no predicate relaxed, no
  comparison made advisory, no field excluded. The newly visible truth is larger
  than expected. The halfpipe chain fails on 5 axes and the emerald chain on 9,
  including a previously invisible segment-6 physics divergence of 13,837 errors
  and a segment-6 boundary that is never observed. The level→special-stage gaps are
  missing their recorded edges entirely (expected 1–2, actual 0), and the return
  gaps carry 12–13 against 8 expected while diverging on ordinal, transfer id,
  owner, mapping frame and `movie_logical_frame` (8,933 against a recorded 9,675).
  Recorded honestly, and corrected after a later round measured it properly: **no
  chain outside S2 actually exercises this axis.** The S3K chains report zero gaps
  because their walks error before any gap forms. The S1 chains' two mid-run gaps
  contain no recorded edges at all — `s1-ghz-maze-roundtrip`'s four gap transitions
  sit at movie frames 748 and 9071, outside both gaps — so they compare
  expected-0 against actual-0 and pass trivially. Both are **unmeasured**, not
  green, and S1 is not the working reference it first appeared to be.
- **The special stage and the level share Sonic 2's three last-loaded-DPLC
  registers (2026-08-12):** dumping the run chains' transition-gap art ledger
  against the fixture showed the engine never submitting Tails' body art at a
  level reload — it submitted Tails' *tail* art instead. The ROM has exactly three
  such registers, one per player art bank (`Sonic_LastLoadedDPLC`,
  `Tails_LastLoadedDPLC`, `TailsTails_LastLoadedDPLC` at
  `s2.constants.asm:1556,1625-1626`), and the special stage *shares* them with the
  level: `Obj09`/`Obj10`/`Obj88` initialise them to `#1` and the stage's own DPLC
  loaders keep deduplicating against those same bytes. `Level_ClrRam` then zeroes
  all three on every level load, since they lie inside `Misc_Variables`
  (`:1484-1629`) — the same clear behind two earlier fixes today. The engine had
  given the special-stage submitters a private dedup namespace and never cleared
  the level's, so the returned level suppressed a player's first mapping-frame
  transfer against a value stale since before the stage. Dedup now keys on the ROM
  register rather than the comparison owner, and the registers are cleared where
  `Level_ClrRam` clears them. Both gaps then carried both recorded owners at the
  recorded row in the recorded order **as measured by a bespoke probe** — but read
  through the real gap comparator, once that was made reachable (see the entry
  above), the gaps still diverge substantially on count, ordinal, owner and frame.
  So this is a genuine ROM correction, not a closure of the gap ledger; the
  narrower claim is what the evidence supports. S1 has the same shape (`v_sonframenum`
  inside `clearRAM v_levelvariables`, `sonic.asm:2742`). Measured en route and
  worth recording: placing the clear in the mid-gameplay art-refresh path instead
  wipes an established dedup mid-segment and turns both standalone oracles red at
  3,051 and 2,232 errors — the oracles caught it before it landed.
- **One title-card tail for displayed and omitted cards, and both S2 run chains
  clear the returned level (2026-08-12):** `TitleCardManager` held two
  implementations of ROM `Obj34_WaitAndGoAway` (`s2.asm:27605-27637`), the routine
  whose slide-off fires the two `LoadPLC` calls for standard-water and animal art.
  The omitted-presentation tail modelled it exactly; the displayed-presentation
  overlay re-derived the same event from a state-transition pass that consumed a
  frame without moving the piece, and from the overlay's viewport-relative
  `hasExited()` rather than the ROM's `x_pixel > $200` test. `Level` writes routine
  `$16` and `anim_frame_duration = $2D` to the surviving pieces at `:5066-5080`,
  after the leave loop and before `Level_MainLoop`, whether or not the card was
  displayed — so the 45-wait plus 8-slide count always starts on the first
  main-loop iteration. The standalone segment class takes the omitted path and the
  run chains take the displayed one, which is exactly why identical rows produced
  identical queue-event sequences with the art loads landing two compared rows
  late. Both paths now share one owner armed at the ROM's own arming point, with
  no constant introduced. Both chains take returned-level segment 2 from 65 errors
  to zero, clear segment 3 as well, and advance to a new segment-4 frontier — a
  single error where the engine attributes the first art edge to the run gap and
  the recorder attributes it to the segment.
- **The title card was not treated as a DMA service boundary, and four player
  DPLC transfers rode across the gap (2026-08-12):** with the returned-level
  segment asserting its physics and the frame counter fixed, both S2 chains
  converged on one shared first mismatch — `dynamic_art.edges` at frame 1 — which
  turned out to be a single wrong rule. `DynamicArtDmaServiceModel` classified
  `LEVEL_TITLE_CARD` as *not* a dynamic-art DMA service boundary, while the enum's
  own doc comment already listed `Vint_TitleCard` among the `ProcessDMAQueue`
  callers. The ROM sides with the comment: the `Level_TtlCard` wait loop
  (`s2.asm:4914-4925`) and the 25 leave-loop iterations after `InitPlayers`
  (`:5060-5066`) both set `Vint_routine = VintID_TitleCard`, and that V-int
  (`:1005`) calls `ProcessDMAQueue` at `:1046`. So the four player DPLC transfers
  queued by the returned level's pre-`Level_MainLoop` passes were never drained
  during the transition gap; the engine carried all four across it and retired
  them together on the first serviced V-blank, publishing spurious `completed`
  edges on the segment's first row and skewing every later edge ordinal for the
  rest of the segment. Moving one enum constant between switch arms took the
  emerald chain's returned segment 8,633 → 65 errors and the halfpipe chain's
  7,662 → 65, with both now diverging first at the same deeper frontier
  (`queue.s2_nemesis_plc.busy` at frame 52) — a PLC-queue question rather than a
  dynamic-art one.
- **The special-stage return inherited the previous segment's level frame
  counter, and Tails stopped jumping (2026-08-12):** with the returned-level
  segment finally asserting its own physics, the S2 emerald chain surfaced an
  engine-only player death mid-segment — a divergence the extra title-card ticks
  had been masking, and one the segment's production-ownership assertion caught
  rather than hid. The kill was spikes, 906px off-route and 1,678 rows
  downstream of anything real. The actual onset was Tails failing to make a jump
  the recording makes: her CPU auto-jump is gated on `andi.b #$3F` over
  `Level_frame_counter` (`s2.asm:39368-39376`), and the engine reset that counter
  only when a load requested a sprite-lifecycle change, which a returned-level
  load does not — so the level inherited `0xE9B` where the ROM had `0x0002` and
  the gate never lined up. `GM_Level` clears the counter on every non-demo entry
  (`:4771-4773`), the only increment before `Level_MainLoop` is that loop's own
  `addq` (`:5092`), and the counter lives in `CrossResetRAM`
  (`s2.constants.asm:1661-1665`) so `Level_ClrRam` never touches it. Resetting at
  the title-card release boundary rather than at load also drops the 26
  pre-main-loop passes' worth of drift the ROM's passes do not produce. The
  emerald chain stops dying and reaches its physics assert at 8,633 errors;
  halfpipe segment 2 falls 22,426 → 7,662. Both chains now share one first
  mismatch, `dynamic_art.edges` at frame 1.
- **S2 run chains: the returned-level segment now asserts its own physics, and
  the title card stops ticking players (2026-08-12):** both S2 run chains were
  computing a full physics comparison for the segment they return into after a
  special stage — 58,184 and 39,645 errors — writing it to the segment report
  with `complete: true`, and then asserting only the boundary observation and
  dynamic art. The chains sailed past it and failed downstream, so thirteen
  rounds of candidate fixes were graded by which wrong route a
  broken-but-unasserted segment happened to take, while the standalone segment
  class replayed the identical rows to zero. Asserting the error count the chain
  already computed made both chains fail at the real defect and turned that into
  one measurable target. Two ROM divergences fell out of it. The engine ran the
  player objects for the whole returned-level title card, where `Level_ClrRam`
  has just cleared `Object_RAM` (`s2.asm:4808`) so they do not exist for the
  `Level_TtlCard` loop at all — `InitPlayers` only runs afterwards, giving the
  ROM one `RunObjects` pass plus 25 leave-loop iterations. Those ~103 extra
  playable ticks let the sidekick finish her catch-up ramp and settle, so she was
  compared at rest instead of mid-ramp; the same clear is why her sub-pixel is
  zero at `InitPlayers` on every entry including a re-entry. Separately, the
  checkpoint restore wrote a banked left-scroll camera position over the level
  re-init's snap, where the ROM discards its saved camera and falls into the same
  `x-$A0` clamp tail as a fresh start (`s2.asm:14775-14814`, matching shapes in
  S1 and S3K). Halfpipe segment 2 fell 39,612 → 22,426 and the sidekick stopped
  being its frontier field. Removing the title-card ticks also exposed a
  load-bearing compensator: they had been masking a genuine engine-only player
  death mid-segment on the emerald route, which the ownership assertion caught
  rather than hid, and which is the next frontier.
- **Repeated SMPS playback no longer recopies whole audio assets (2026-08-11):**
  music, base/donor SFX, and named SFX now reuse one immutable,
  generation-aware program/DAC/configuration catalog entry while retaining
  independent live sequencers, tracks, and rewind cursors. Prepared SFX
  admission replaces the ordinary whole-music-driver rollback snapshot with
  hardware-bounded conflict state. In the authenticated public-API comparison,
  repeated SFX allocation fell from 27,328–8,415,808 bytes per trigger to a
  flat 1,344 bytes, with zero program-, DAC-, or unrelated-music-size slope;
  repeated music allocation also fell by 5.1%. See the
  [performance audit](docs/architecture/audits/2026-08-10-smps-repeated-playback-performance.md).

- **Strict trace-v5 recorder fleet (2026-08-04):** the native BizHawk recorder,
  contract tests, and published S1/S2/S3K fixtures now share one strict v5
  schema with deterministic gzip payloads and no legacy runtime compatibility
  path. The original S1 credits captures remain archived as predecessor evidence;
  the native credits differential gate and standalone S3K special/bonus aliases
  are green.
- **Strict trace-v5 contract remediation (2026-08-04):** every present
  `hardware_timing.jsonl` stream now uses one complete module/direct registry;
  absent and explicitly empty streams are distinct, and generic mixed-policy
  tests are explicit. Publication evidence pins the installed inventory,
  protected S1 credits archive, exact deletion/rename manifest, and native
  no-gate validation.
- **Shared visual/headless represented-row harness (2026-08-03):** complete-run
  special stages now use one production-row driver for input, hardware timing,
  dynamic-art publication, comparison, cursor advancement, and segment closure
  in both visual and headless replay. The visual launcher drains an admitted row
  before boundary work, and fixture defaults share playable/fixed-slot dispatch.
  S1 giant-ring completion also tracks the ROM's deleted native player slot as
  explicit rewind state, keeping the emerald route's end-card animation stable
  while all 3,728 represented special-stage rows are verified atomically.
- **Visual/headless S1 run lifecycle parity (2026-08-03):** visual complete
  runs now carry the same ROM-visible object VBlank pacing across compressed
  act/title-card transitions as headless chains, keeping non-emerald GHZ3
  capsule/results PLC timing in sync. S1 end-act results use the ROM's fixed
  `v_endcard` slot and commit cue 16 through the signpost/capsule producer;
  giant-ring completion no longer creates a competing card or restarts clear
  music. Dynamic-art comparison also opens at the destination's already
  consumed row cursor, preserving atomic publication ownership.
- **Visual trace inter-act handoff parity (2026-08-03):** complete runs now
  keep a destination level's comparator, input, hardware-timing, and
  dynamic-art owners closed while its production initial title card is
  pending. The existing level load is remembered and admitted when the title
  card releases, preventing an S1 GHZ1-to-GHZ2 run from aborting without
  introducing a second load or another music restart. If admission occurs
  inside GHZ2's first production wrapper, that wrapper now transfers its
  deferred dynamic-art publication owner and rebases the before-snapshot in
  the destination generation, keeping row zero atomic and drained before row
  one.
- **Visual trace locked-input parity (2026-08-03):** recorded A/B/C press
  identity now survives input-only trace rows but reaches movement only after
  queued object control and the ROM control lock are applied. S1 signpost
  walk-off retains forced Right against recorded Left+jump, while objects still
  observe the raw controller edge. Rewind replay-forward reconstructs the same
  pending input without storing trace-owned state in the player sprite.
- **Visual trace title-card and row-zero parity (2026-08-03):** level-backed
  traces and complete runs selected from the master title now show the full
  production title card and continue into replay in the same loaded gameplay
  context, so the level and music are not restarted. A checked timing-epoch
  handoff and reserved unpublished dynamic-art window preserve exact headless
  PLC/DPLC/Kosinski row-zero comparison. Recorded input uses the logical-input
  path, allowing native scripted control such as S1's signpost walk-off to win.
  Standalone special stages also render trace progress, install base-game ring
  SFX routing, and leave S1's terminal white hold through a deterministic
  return to the trace picker.

- **S3K native recorder timing attribution (2026-08-03):** recorder 6.42 now
  attributes held-frame KosM retirements from canonical FIFO transitions and
  emits module `post_objects` before direct `pre_main_loop` work. Two
  byte-identical full-route captures validated the publication: 15 metadata
  files and 14 timing streams moved, with exactly 27 boundary-only
  `vint_service`-to-`post_objects` substitutions; physics, auxiliary state,
  ordinals, fingerprints, and ending timing remain unchanged.
- **MHZ fixed-SST and route-object parity (2026-08-03):** pollen and plane
  switchers now use slot-owned fixed-SST installation and cleanup, while the
  MHZ cutscene door, pulley, curled vine, Madmole, enemy bounce, and mushroom
  cap paths follow their ROM lifetime, geometry, and contact semantics. Rewind
  preserves the cutscene child identity, and the MHZ complete-run physics
  frontier advances from frame 5,509 to 6,337. The three-ROM suite retains the
  pre-existing failure/error baseline with no new failing test.

- **Visual trace launch parity and diagnostics (2026-08-02):** the master-title
  picker now exposes grouped legacy S1/S3K complete runs alongside their level
  entries, dispatches all three games' typed special-stage traces, and accepts
  the narrow profile/timing compatibility needed by older committed captures.
  Level traces hand off any completed automatic PLC/DPLC diagnostics window
  before establishing row-zero comparison ownership, using the same
  production-published lifecycle as whole-run replay. Selecting a
  trace first renders a loading screen; parser, bootstrap, and replay failures
  return to a persistent in-picker diagnostic instead of disappearing into the
  console.

- **Landing width and the AIZ intro title-card handoff modelled from ROM
  (2026-08-02):** two follow-ups salvaged from the S3K trace pass. The solid
  landing clamp now models the object's own width byte as a first-class provider
  field with a routine-family default, matching ROM in all three games — S1
  `Solid_Landed` re-reads `obActWid`, S2 `SolidObject_Landed` and S3K
  `loc_1E154` re-read `width_pixels(a0)`, while top-solid platform routines land
  on the caller's `d1` directly. That replaces a controller heuristic which
  inferred "the provider overrode the width" by comparing it against the
  collision half-width, and so silently failed whenever an override returned the
  same value. Separately, ROM installs the AIZ intro's in-level title card from
  the Knuckles cutscene exit, which only *allocates* the object slot — the
  allocator returns a slot the current object pass has already walked, so the
  art submission first dispatches a frame later. AIZ complete-run divergence
  groups fall 60 to 52 with every other segment byte-identical.
- **S3K queue lifecycle recovery Wave 2 (2026-08-02):** runtime enemy-art
  admission now uses exact owner leases across initial titles, carried
  CNZ/MGZ/LBZ results titles, and ICZ's transactional resource handoff; the
  generic placement lifecycle also carries CNZ's later special-stage entry
  ring through its second retirement. The measured 64-class fleet keeps all
  30 S1 and 20 S2 classes green and the strict authority matrix 142/142, while
  advancing CNZ standard to direct `#24` and CNZ complete through `#203/#204`
  to `#205`. The first full sweep also found a shared frame-33 early-title
  queue regression across eight S3K route classes. It was attributed to the
  skipped-title owner observing its last higher-slot child in the same
  dispatch and corrected to preserve native SST order; the final sweep removes
  all eight shared groups per class and restores the earlier route frontiers.

- **S3K queue lifecycle recovery Wave 1 (2026-08-02):** standalone
  Gumball, Pachinko, and Slots replays now verify and close only their
  recorder-declared bonus prefix instead of treating the outer return-to-level
  timing tail as fixture-owned. LBZ now performs and rewind-owns both native
  miniboss-box KosM submissions, moving its missing-production terminal from
  direct `#279` at raw 17604 to `#282` at raw 19871. The strict
  timing/authority matrix is 142/142 green and the S1/S2 trace fleets remain
  fully green.

- **Object update clocks use ROM-accurate terminology (2026-08-02):** all 809
  `update(int, PlayableEntity)` boundaries now name their first argument
  `vIntRunCount`, matching the `ObjectManager.vblaCounter` value actually
  dispatched and distinguishing it from executed-frame and
  `Level_frame_counter` clocks. A symbol-attributed refactor carried the name
  through 154 framework hooks/overrides, 219 unanimous private-helper flows,
  25 retained fields, and one local alias while excluding mixed-clock and
  animation/index counters. The object scaffold emits the same terminology and
  an attributed source guard prevents drift.

- **Native-recorder provenance, route lifetimes, and held-row Kos timing
  (2026-08-02):** the S2 special-stage contract now pins the committed native
  fixture's `1.4-s2ss-native` stamp while continuing to pin the Lua recorder's
  `1.4-s2ss` source version. A route-led `RememberState` audit corrected AIZ
  Draw Bridge and MHZ Swing Vine range lifetimes, including their native fixed
  `$280` anchor checks, so roots no longer retain dynamic slots after leaving
  range. S3K schema-2 replay can now admit an exact prepared direct-Kos
  completion recorded on a VBlank-only row, confined to the timing port and
  production coordinator post-hook: AIZ direct `#35` and dependent module
  `#15` advance, while unprepared module `#16` and the HCZ/MHZ missing-work
  terminals still fail closed. The full-suite comparison improves the existing
  red baseline by two tests with no new regression; a newly exposed S1 results
  test now fully resets inherited module state. The broad object-update
  `frameCounter` to `vIntRunCount` rename was deliberately left to the dedicated
  quiet-tree follow-up above.

- **Visual complete-run trace playback (2026-08-01):** a run selected on the
  master title now stays active across level/act replacement, bonus and special
  stages, return loads, and its terminal movie tail. Visual and headless replay
  share fail-closed transition/admission policy, S1/S2/S3K special-stage row
  clocks, return comparisons, and production-ordered PLC/DPLC/Kosinski gap
  evidence. Recorded data remains comparison-only except for controller input
  and the existing bounded hardware-readiness delay; failures remain visible in
  the picker until acknowledged.

- **S1 and S2 trace-replay fleet green (2026-08-01):** the last red,
  `TestS2SpecialStageTraceReplay`, closes its final 9 errors at the
  stage-finish boundary. The cause was not a comparison gap but the replay
  harness stepping two kinds of observation in the wrong V-int lifecycle
  phase: an observation that runs a `RunObjects` pass is never a lag V-blank
  (the ROM waits on `VintID_S2SS` immediately before the pass), and the
  39-row window where the finish's art stays outstanding is
  `Pal_FadeToWhite`'s 22 fade V-blanks plus the interrupts-disabled results
  setup, none of which service the DMA queue. Both corrections are general
  rules rather than finish-specific cases, and the engine itself is unchanged.

- **S2 special stage compares DPLC work by pass identity end to end
  (2026-08-01):** `TestS2SpecialStageTraceReplay` drops from 17747 errors (first
  at frame 436) to 9 (first at frame 5181) — every pass, submission, retirement,
  and animation cycle across the stage's 5180 gameplay rows now compares clean.
  Three pieces, all driven by the recorded `run_objects_end` bindings the replay
  already consumes: a pass whose completion cursor precedes its bound
  observation now retires its submissions within that observation (on hardware
  the V-blank had already run `ProcessDMAQueue` over its queued work); paced
  submission edges bind to the earliest pass whose cursor covers their
  wall-clock crossing, in both directions, since the recorder can publish an
  edge before its pass's bound row as readily as after; and crossing stamps and
  outstanding-id windows follow that binding. This removes the post-start scope
  boundary the previous entry set, which held for passes but not for individual
  edges. The 9 residual errors are the stage-finish terminal-pass choreography
  (rows 5180-5220), left as a scoped follow-up.

- **Special-stage submission spills compared by pass, not publication row
  (2026-08-01):** the S2 special-stage intro is now byte-aligned through frame
  423, taking the frontier from 181 to 436. The remaining intro divergence was
  not an engine defect: when a `RunObjects` pass overruns its frame, the later
  objects' submissions carry the *lag* row as their `logical_frame` and surface a
  row later, and which objects spill depends on sub-frame 68K execution time
  inside a single pass — recorder row 176 shows three submissions with no spill
  despite a following lag row, so it is not predictable from lag adjacency. The
  engine publishes each pass atomically and cannot derive that split point. This
  is the same class as the recorder power-on epoch normalisation already applied
  to `transfer_id`/`edge_ordinal`: an observation artifact being compared as
  though it were ROM state. `DynamicArtSpillNormalization` rebinds only
  *submission* edges whose recorded `logical_frame` differs from their
  `publication_frame`, only before the recorded `SpecialStage_Started` transition
  (after which recorded pass bindings already pace each pass), moving them to the
  latest non-lag row at or before their logical frame. Cardinality, in-pass
  ordinal order, owner, phase, mapping frame and every `requests[]` field stay
  absolute, and dedicated tests assert that a missing submission, an extra
  submission and a wrong-owner attribution all still fail. The engine-side
  alternative — consuming the recorded spill boundaries under the hardware-timing
  contract — was rejected: that contract governs when engine-created work becomes
  *ready*, and pacing the publication of diagnostic rows is not readiness.

- **S2 special-stage intro pass pipeline and Obj88 startup tick (2026-08-01):**
  two more ROM-cited corrections to the special stage's early frames, taking it
  from 20468 errors at frame 165 to 18230 at frame 181. Before
  `SpecialStage_Started` each ROM wait-loop `RunObjects` completes within its own
  observation, and only the slow first post-fade iteration spills into the next
  row — a spill that is ledger-invisible because its mapping-frame-0 art is
  deduped; the engine had been deferring *every* pre-start pass to the following
  observation. Separately, Obj88 (Tails' tails) has no routine gate, so its whole
  body including `AnimateSprite` runs on the startup pass that created it, unlike
  Obj09/Obj10 whose init returns via `LoadSS*DynPLC` before `SSPlayer_Animate` —
  the engine had it running a pass behind from birth. An earlier reading that the
  ROM's animation clock stalls for 29 frames was wrong: the per-row CSV shows
  `player_anim_frame_timer=4` and a three-executed-frame advance period on both
  sides, the apparent gap being the 22-frame `Pal_FadeFromWhite` freeze plus lag
  rows. No fade-gating was needed and none was added.

- **Visual trace playback now follows the headless replay contract
  (2026-08-01):** traces launched from the master title prepare their applied
  BK2 row before ROM pause admission, including S3K's current-row validation /
  previous-row gameplay split. Forward play and visual rewind share the same
  one-row/one-VBlank suppressed closure, including the held title-card
  `VINT_SERVICE -> POST_OBJECTS -> PRE_MAIN_LOOP` scan. PLC and Kosinski queue
  state stays comparison-only at its native post-service point, while player
  DPLC state is sampled only after the outer lifecycle publishes the row.
  Bootstrap/input alignment errors now appear in the live comparator, and an
  incomplete launch, Esc, or production failure removes recorded timing
  authority and returns cleanly to the master title without strict-closing an
  unfinished hardware schedule.

- **Special-stage dynamic art starts publishing (2026-08-01):** both special-stage
  traces diverged early with the engine publishing *no* dynamic-art edges at all
  where the ROM publishes its first few. The players' art was only submitted from
  the first recurring V-int pass, but `LoadSSSonicDynPLC` / `LoadSSTailsDynPLC` /
  `LoadSSTailsTailsDynPLC` run inside the startup `RunObjects` pass, and the
  startup sequence then waits on `VintID_CtrlDMA` — a handler that is nothing but
  `ProcessDMAQueue` — so that V-blank retires the queued transfers even though the
  surrounding `Pal_FadeFromWhite` loop never polls the joypad and reads as a lag
  frame. The S1 side was separately pre-consuming its first Sonic DPLC change.
  Both frontiers move; the special stages are not yet green and the residual is
  recorded as a recurring-pass attribution gap.

- **Final Zone stopped running underwater; ROM `v_act` split from the feature act
  (2026-08-01):** an earlier fix in this line answered a `v_act` question through
  `getRemappedFeatureAct`, which also keys water and palette lookups where SBZ3 is
  deliberately reported as the synthetic pair (SBZ, act 2). Reporting act 2 for
  Final Zone so the ported `SignpostArtLoad` gate could see `act3` collided FZ
  with SBZ3's water entry, and Final Zone ran the entire level underwater —
  `runAcceleration` halves the base `$0C` to `$06` under water physics, which is
  exactly the frame-0 `x_speed` divergence the trace reported. The two identities
  are genuinely distinct in the ROM: `id_LZ_act4 = $0103` is SBZ3 and
  `id_FZ = $0502`, water is enabled for the `id_LZ` slot only, so the ROM never
  reaches water through `id_SBZ` at all. `GameModule.getRomAct` and
  `LevelManager.getRomActId` now provide the `v_act` partner to the existing
  `getRomZoneId`, so the signpost gate asks a `v_act` question through a `v_act`
  channel and water keeps its own; SBZ3 correctly stops skipping the gate, its
  `v_act` being act4. The routing test that should have caught this was asking the
  water system about the logical act rather than the pair it is keyed by.

- **CPZ2 green — two cancelling engine defects (2026-08-01):** the last S2 level
  trace closed on a pair of bugs that had been hiding each other. The ROM's CPZ
  boss pump is single-pass: `Obj5D_Pipe_Pump_4`'s repeat branch writes its restart
  state but has no `rts` and falls through into the tail that switches to
  `Obj5D_Pipe_Retract` and deletes the pump head unconditionally, so the repeat
  timer is dead code on hardware — the engine honoured it and ran ~430-frame
  two-pass pumps. Separately the ROM's dripper is pipe-independent, parented to
  the main vehicle and deleted only on the defeat bit or after its own twelve fill
  cycles, where the engine anchored it to the pipe control. These cancelled
  exactly: the over-long pump kept the pipe alive just long enough for the
  pipe-coupled dripper to finish its twelve pulses, so every player-visible timing
  matched and the only observable trace was the pipe control surviving to defeat
  and drawing one extra `RandomNumber`. Fixing the pump alone collapsed the fight
  (5140 errors), which is what confirmed the coupling. Every trace in the
  capsule/PLC-busy family — GHZ3, MTZ3, CPZ2, SYZ3, SLZ3, MZ3, LZ3 — now passes,
  and with this the entire Sonic 2 level trace fleet is green: the only remaining
  S2 red is the special stage, which is a separate long-standing frontier.

- **All four S1 act-3 capsule stragglers greened (2026-08-01):** SYZ3, SLZ3, MZ3
  and LZ3 closed on three ROM-cited fixes, none of which lived where the failing
  comparator field pointed. The busy flag was mirroring *when the egg-prison
  capsule decided the act was over*, and that decision was landing on the wrong
  frame for three unrelated reasons. ROM `Pri_Switch` writes the routine and
  timer then returns, so the first explosion pass is always the frame *after* the
  button trigger; the engine ran the explosion phase on the body object, so
  wherever the level layout ordered the body's slot after the button's, the body
  ticked the 60-frame timer once too early — which is precisely why GHZ3, whose
  layout orders them the other way, was immune. MZ's boss stored the lava
  countdown as `$40+(d&$1F)` where `BMZ_ShipStart` stores the raw low byte, and
  drew once at init where the ROM draws not at all. And LZ's conveyor wheels were
  immortal: `LCon_Wheel` ends in `bra.w RememberState`, which deletes out of range
  and frees the slot, but the engine modelled `RememberState` as always
  persistent, so nine wheels pinned slots 41-88 through act end and pushed the
  last animals past the `Pri_EndAct` slot-63 scan ceiling. That last one is
  recorded as its own defect class rather than an LZ3 quirk, with a follow-up to
  audit every `isPersistent()` override against its cited ROM `RememberState` /
  `DeleteObject` tail — the same misclassification will reproduce anywhere slot
  pressure or `FindFreeObj` ordering matters.
- **S3K complete-run trace parity: 9,808 divergence groups down to 997, verified
  frames up from ~30k to ~74k (2026-08-01):** a sustained pass over all seven S3K
  complete-run segments. The largest single gain was frame-0 `Status_InAir`:
  the engine already modelled `SpawnLevelMainSprites`' per-zone `bset`s
  correctly, but a premature ground probe in the test fixture and a bootstrap
  seed that copied a recorded *post*-frame row in as *pre*-frame state were both
  clobbering it, so every segment spent a surplus gravity tick before frame 0.
  Removing both took the fleet from 9,808 groups to 1,185 in one change. Further
  fixes modelled ROM behaviours the engine had approximated: the title-card
  teardown window derived from `objoff_2E` plus the element cull rather than a
  calibrated constant; the Kosinski module state step moved to its ROM frame
  phase in the `LevelLoop` tail; the MHZ pollen spawner installed where it
  survives level load, restoring ~3,000 missing `Random_Number` draws; and
  same-frame fall-through modelled for the SS entry ring, the Madmole arm and the
  HCZ water wall. Right-edge inclusivity now defaults per ROM routine family
  (`bhi` for full-solid, `bhs`/`blo` for top-solid, consistent across all three
  disassemblies), fixing 18 silently-wrong providers with no per-object patches.
  A recorder defect was also found and fixed — `LoadQueueStateProjector` scaled an
  already-byte VRAM address by 32 a second time — and the S3K fixture corpus was
  regenerated and revalidated across 115 segments with physics bytes unchanged.
  Sixteen test regressions introduced along the way were found and closed,
  including two that *hung* rather than failed, and the trace comparator's heap
  retention was cut from 643 MB to 171 MB after truncated runs were found masking
  real failures as passes.

- **Final Zone stopped crashing on the signpost PLC gate (2026-08-01):** the
  `Level_MainLoop` tail wired earlier in this line reproduced `SignpostArtLoad`'s
  six ROM gates faithfully — including `cmpi.b #act3,(v_act).w` — but compared
  the ROM's `act3` constant against the *engine's* logical act. The ROM has no
  separate Final Zone level: FZ is SBZ act 3, so `v_act` reads act3 there and the
  routine returns before `NewPLC` ever runs. The engine models FZ as its own
  logical zone whose act index restarts at 0 (probed at the hook: zone 6, act 0,
  against the level's own ROM zone index of 5), so the gate never fired and the
  submission landed while the Nemesis decoder was mid-run, throwing
  `cannot mutate queued PLC entries while the decoder is active` before replay
  could start. The S1 module's feature-act remap — which already carried the
  sibling case of SBZ act 3 being loaded from the LZ zone slot — now reports act 3
  for Final Zone, and the gate reads the ROM-effective act, so the divergence is
  modelled at the per-game boundary rather than by testing a zone id for
  behaviour. Final Zone returns to being an ordinary comparator frontier instead
  of a crash.

- **Boss-defeat RNG stream desync greened the GHZ3 and MTZ3 capsules
  (2026-08-01):** the last `queue.*_nemesis_plc.busy` frontiers turned out not to
  be a PLC defect at all. Instrumenting the queue showed all three traces
  submitting an identical PLC and draining it at an identical rate, with the whole
  busy episode merely time-shifted — and by different amounts in different
  directions, which no queue defect can produce. The busy flag was faithfully
  mirroring when the egg-prison capsule decided the act was over, and that
  decision (scan for `ObjID_Animal`; when none remain, `Load_EndOfAct`) was
  landing on the wrong frame because the RandomNumber stream had desynced
  *upstream*, in boss-defeat code. The capsule machinery itself measured exact —
  spawn cadence, slot assignment, burst positions and despawn scan all match the
  ROM frame for frame; only the random spawn offsets and species differed, which
  is a stream-position signature. GHZ's wrecking ball runs `BossDefeated` for
  `$60` frames after Eggman's defeat and then converts itself into an explosion
  (12 draws the engine never made, since it destroyed the ball instantly), and the
  S1 capsule's offset negation tested bit 15 of the returned value where ROM
  `tst.w d1` tests the *new* seed. MTZ's boss spawned a defeat explosion every
  frame of its defeat window — 179 draws — where `Boss_LoadExplosion` gates on
  `(Vint_runcount+3)&7` before allocating, giving the 23 draws the recording
  actually contains. CPZ2 improved but still carries one extra draw: the ROM runs a
  second pipe-docking cycle before defeat that the engine never runs, so its
  waiting pipe activates at defeat and draws once. Verified across the full 57-class
  S1/S2 fleet at 49 passing.

- **V-blank counter clarity (2026-08-01):** two clock-related pieces of the
  engine were documented as doing something other than what they do, and the
  discrepancy cost real investigation time during the S1/S2 trace re-green.
  `ObjectServices#vIntRunCounter` advertised resolving S3K's `V_int_run_count`
  "from the object-update clock", implying a conversion; it actually returned its
  argument plus a phase offset that is zero in normal gameplay — the identity
  function — and three separate investigations proposed routing a fix through it
  before that was noticed. It is now `resolveVIntRunCount`, documented as
  performing no conversion, because the value callers pass is already the V-blank
  counter: `ObjectExecutionController` dispatches
  `instance.update(objects.vblaCounter(), …)`, so every object receives
  `V_int_run_count` under a parameter merely *named* `frameCounter`. Separately
  the counter itself was advanced from nine scattered sites with its contract
  written down nowhere; it is measurably correct — probed against the recorded ROM
  value it matches exactly, every frame — so rather than relocate anything, the
  inline increment now routes through the single `advanceVblaCounter()` mutation
  point and the field states the invariant it satisfies (exactly one tick per
  serviced V-blank, matching the ROM's V-int-exit increment whichever routine the
  mode jump table dispatched), names the consumers a de-phasing would silently
  break, and records the two deliberate divergences in
  `docs/status/known-discrepancies.md`. Both changes are behaviour-identical and
  verified against the full S1/S2 trace fleet, the trace/rewind/PLC guards and
  S3K parity.

- **S1/S2 trace-replay suites re-greened after the PLC/DPLC queue landed
  (2026-08-01):** the Pattern Load Cue and DPLC queue work left the Sonic 1 and
  Sonic 2 trace-replay fleets failing 42 tests; 39 are green again from 26
  disassembly-cited engine fixes, with all trace, rewind and PLC guards and S3K
  cross-game parity holding throughout. The defects clustered into one recurring
  class — a ROM routine or init frame the engine never ran at the right point —
  and the fixes read as a tour of it: the title-card `Card_ChangeArt` PLC pair
  hung off a presentation predicate a headless load never executes; the player
  DPLC ledger dropped a logical row on every ROM lag frame; the engine had no
  equivalent of the `Level_MainLoop` tail, so `SignpostArtLoad` never queued
  `plcid_Signpost`; S2's `CheckLoadSignpostArt` was implemented but never wired;
  the Tornado skipped its `ObjB2_Init` routine-0 frame, so the only Tails-bank
  DPLC submitter in Wing Fortress ran a frame early; Obj05 executed outside its
  `LevelOnly_Object_RAM` dispatch slot and recomputed latched directional state
  every frame; the Oil Ocean surface never ran in its reserved object-RAM band;
  and the replay boundary skipped the extra `Level_MainLoop` iteration the ROM
  performs after the recorder's last sampled frame, which alone closed seven
  traces. One comparator defect sat underneath the rest: `transfer_id` and
  `edge_ordinal` are the native recorder's own run-scoped counters, epoched at
  emulator power-on, so every fixture cut later in a movie opened at a large
  non-zero origin while an engine replay necessarily opens at zero — no ROM in
  the fleet carries a cumulative transfer identity, so absolute-equality on those
  ids was never meaningful and the comparison is now epoch-normalised per segment
  with list cardinality and every content field still compared absolutely.
  **Correction (2026-08-01): the "39 of 42" figure below overstated the result by
  five.** It counted only the 46-class verification set used during the campaign,
  which was chosen early and never re-examined; five originally-failing S1
  complete-run traces sat outside it throughout. Measured on the full 57-class
  fleet the true figure is **34 of 42 green**, with 8 remaining — see
  `docs/status/trace-frontier-log.md`. Three traces were open at the time of
  writing (`Ghz3CompleteRun`, `Mtz3LevelSelect`, `Cpz2LevelSelect`), all on
  `queue.*_nemesis_plc.busy`; the first two have since greened. The busy-window length
  is already correct and Cpz2's inverted polarity reads as a missing submission
  rather than a mistimed one. `docs/status/trace-frontier-log.md` records the
  remaining frontier together with six hypotheses ruled out by direct
  measurement, including two that were implemented and proved inert — the
  V-blank clock is measurably exact and must not be relocated, and the capsule
  spawn gates turned out to be reading the correct counter already (objects are
  dispatched with the V-blank counter under a parameter merely *named*
  `frameCounter`), so the substitution resolved to the value already in use.

- **Special Stage exit crash (2026-07-31):** leaving a Special Stage no longer
  aborts with "a native blocking fade is already active". A finished stage
  re-raises the results transition every frame, and the exit fade-to-white
  parks at full white for one frame before its completion runs — so that frame
  looked like Sonic 1's pre-started hold, entered the results screen a second
  time and opened a second native blocking fade while the first was still
  owned by the pending completion.
- **HCZ vertical geyser visibility, trigger and priority (2026-07-31):** the
  Hydrocity geyser no longer stands visible in the level before it fires. The
  ROM only draws the column from its eruption and falling routines, so it is
  absent from the sprite table while it waits, loads art and rises. Its trigger
  is also a pair of narrow unsigned windows rather than the symmetric box the
  engine used, which had it erupting about half a screen early, and every piece
  of the object — column, debris, spray and splash — now renders in its native
  priority bucket instead of in front of the scenery.
- **S3K underwater breathing bubbles and drowning digits (2026-07-31):** the
  small bubbles the player exhales underwater are drawn again, along with the
  countdown digits that replace them below twelve air. The fixed
  `Breathing_bubbles` controllers were already producing children with native
  cadence, but they rendered nothing: bubble frames now come from the shared
  `Map_Bubbler` art the HCZ bubbler uses, and the ten digit frames — which in
  the ROM select art by DMA into a shared VRAM window rather than by mapping —
  are rebuilt from the ROM geometry against `ArtUnc_AirCountdown`. The object
  also runs its real animation script table and the remaining routines, so a
  digit parks in screen space, flashes, and clears once its owner recovers air.
- **Level music restoration after temporary themes (2026-07-30):** the extra-life jingle now uses the ROM's single music-save slot, while ordinary invincibility and Super themes replace music normally; when their ROM-timed expiry allows it, level music is reissued without silencing the act after chained 1-ups or transformations.
- **Native trace fleet queue-audit publication (2026-07-30):** all 152
  reproducible S1, S2, and S3K trace destinations were regenerated with one
  frozen headless BizHawk build and installed through an immutable,
  deterministic-gzip inventory. S1/S2 captures expose exact PLC and DPLC
  lifecycle timing; S3K captures expose both direct and moduled Kosinski queue
  state under hardware-timing schema 2. The complete replay sweep and every
  first-error frontier are published in the trace frontier log and validation
  report.
- **Portable worktree resource-link policy (2026-07-29):** linked-worktree
  checkout scaffolding now uses relative filesystem-only targets, while hooks
  and all-branch CI reject generated configuration/disassembly links,
  repository-wide ROM-like assets (`.gen`, `.smd`, `.bin`, `.sms`, `.gg`, and
  `.32x`), absolute symlinks, machine-local user-home paths, and root
  merge/handover scratch files before they can be published. Merge resolutions
  and new branch histories receive the same validation as ordinary commits.
- **S1/S2 native PLC readiness (2026-07-29):** Sonic 1 and Sonic 2 Pattern Load Cues now retain their ROM-derived FIFO progress across native VBlank service boundaries instead of completing at host decompression speed. Final Zone, Aquatic Ruin, results, special-stage, and other represented consumers wait on the shared game-owned queue; art remains eagerly available for rendering, rewind restores the queue state, and trace data has no completion authority.
- **Typed production ROM location policy (2026-07-29):** runtime and trace tools now share a filesystem-neutral, provenance-carrying resolver for configured S1, S2, and S3&K paths. Production still selects a nonblank configured path even when it is missing, `RomManager` preserves legacy raw-path diagnostics and null/unknown-to-S2 compatibility, while trace capture and benchmark metadata now fail before headless boot when configuration is blank or the game id is unknown.
- **Knuckles complete super-emerald trace run (2026-07-30):** Raiscan's 434,417-input S3K playthrough is now preserved as one manifest-driven run with 67 compressed segments and 48 transitions, covering all seven chaos emeralds, all seven super emerald stages, repeated bonus-stage detours, and the Knuckles route through HPZ. The native 6.38 schema-2 capture is comparison-only; exact payload/manifest hashes and timing edges are frozen in the committed publication fleet, with independent segment and terminal-tail review.
- **S3K seamless-transition Kosinski parity (2026-07-29):** a disassembly-wide audit now tracks all 187 direct/module queue call instructions across 124 submission clusters. HCZ, MGZ, and LBZ act transitions submit their ROM-ordered chunk, block, and module workloads, wait on the global module-queue predicate, and retain rewind-safe payload ownership instead of omitting direct work or approximating completion with fixed delays.
- **Profiled normal-play load timing (2026-07-29):** `gameplay.loadTimeSimulation: PROFILED` now reproduces deterministic S3K Kosinski queue pacing from five native movie replays. The published manifest covers 170 exact direct-job fingerprints and uses a held-out-validated ROM command-stream estimator for unseen jobs, while trace replay remains governed exclusively by recorded hardware-timing edges.
- **Medium-risk ownership consolidation (2026-07-29):** ROM detection, trace-tool argument parsing, recorded input-row loading, and special-stage replay helpers now reuse named shared owners instead of duplicate local glue, with the intended behavior preserved at their existing public boundaries.
- **Low-risk reuse consolidation (2026-07-29):** trace fixture readers now share one plain/gzip UTF-8 I/O boundary, Sonic ROM header detectors share their orchestration while retaining the existing public extension surface, and the three game HUD providers assemble static art through one profile-driven factory. Focused architecture and compatibility guards pin the consolidated ownership without changing game-specific policy.
- **Post-backport non-trace suite remediation (2026-07-28):** clean JDK 21 validation separated stale and trace-dependent reports from reproducible runtime defects. Fresh S3K loads again publish their one-shot setup pass; results art survives pending, ready, and claimed rewind states without hardware resubmission; bonus-stage bootstrap ownership is game-provided; architecture/rewind guards are green; and ROM-backed fixes cover sidekick centre coordinates, landing-radius signs, hardware queue ownership, AIZ art/event state, and seamless transition clocks. Trace replay remains outside this validation gate until PLC, DPLC, and decompression queues are deterministic.
- **S3K direct Kosinski queue parity (2026-07-28, historical pre-v5 evidence):** ordinary Kosinski work and KosM child streams now share the ROM's four-entry FIFO, with rewind-safe production ownership and native queue-empty progression for the AIZ intro and ICZ act transition. The former schema-2/direct-queue and schema-1 fixture notes are retained as migration history only; v5 is the sole live trace contract and legacy schema selectors are not runtime inputs.

- **Validated `develop` backports restored (2026-07-28):** S3K dynamic allocation and initial dispatch now follow the ROM's full 90-probe window over absolute SST slots 4–93, rewind null-snapshot failures identify their adapter class, modifier-documentation checks no longer contaminate sibling calls, and AIZ ROM integration tests service the production hardware-timing boundaries. The trace fleet retains its existing failure/frontier set.

- **S3K signpost integration regression repaired (2026-07-28):** a prior merge retained the ROM-backed signpost tests while dropping their matching production behavior, leaving every test and trace-replay sweep blocked at compilation. The falling signpost again checks sparkle/player contact before signed-word gravity and movement, honors the ROM cooldown and bump range, and keeps the results-child timing adjustment independent from the newer short-retire-tail lifecycle.

- **AIZ results-to-title handoff restored (2026-07-28):** Act 1 results now publish the ROM end-level signal before the independent end-sign owner restores player control, then initialize the mutated Act 2 title owner on its following dispatch. ROM-width child retirement and rewind-safe title-job rebinding advance the AIZ hardware frontier from title job 23 at frame 8800 to the separate MonkeyDude runtime-art job 27 at frame 8943.

- **Token-efficient trace fleet routing (2026-07-28):** the trace bug-fixing fleet now routes bounded discovery, triage, fixes, and verification across GPT-5.6 Terra and Sol with explicit reasoning effort, objective escalation triggers, sequential worktree ownership, and fail-closed result semantics. A pinned S1/S2/S3K benchmark protocol records model routes, token telemetry when available, ROM/disassembly evidence, independent regression guards, acceptance decisions, and durable reports so routing policies can be compared by verified outcomes rather than nominal token price.

Highlights:

- **Evidence-gated runtime performance follow-up (2026-07-28):** measured candidates now cache rewind type-dispatch routes, index observed trace auxiliary-event classes, feed live S3K slot state directly to the panel animator, and scalarize private background sampling. The accepted paths cut their focused allocations by 55–100% and timings by 18–99%; rewind and trace contract suites remained stable, the slot panel's 1,026-frame lossless capture stayed identical, and three real-GL background capture pairs were byte-identical. A related test-only change shares the timing-authority guard's immutable source catalogue, while a retained JFR audit scopes eager trace-comparison formatting as separate follow-up work.
- **Audio performance metric restored (2026-07-28):** the performance overlay once again measures unified audio presentation and device pumping, and keeps the aggregate audio row visible within its six-line legend.
- **S3K hardware-timing replay and complete trace fleet (2026-07-27):** trace replay now models the ROM-visible Kosinski Moduled work queue with rewind-safe FIFO identity, resumable decoding, and completion boundaries that distinguish genuine VInt-only loading stalls from the held-counter title-card object loop. The native recorder is the canonical fixture publisher, and recorder 6.37 regenerated all 47 S3K fixture destinations—including LBZ data and eight later-route segments through the ending—with immutable hashes, strict loading, compressed payloads, and ROM-backed differential gates. LBZ fixture data was regenerated for James's branch without attempting to advance or remediate its engine frontier.
- **Audio and test reliability (2026-07-27):** OpenAL speaker playback now keeps enough buffered PCM to absorb ordinary frame jitter without clicking, and pausing/focus changes no longer accumulate silent audio before resume. Three S3K object-registry tests now reset their loaded-level state, removing JVM-fork-order flakiness.
- **Key bindings carry their own modifiers (2026-07-25):** a binding was a bare key code, so any shortcut that wanted a modifier hardcoded it at its call site — `capture.toggleKey` let a player change the key while the Shift it was really pressed with lived in `Engine`, where it could be neither seen nor changed. `KeyChord` now parses and formats `"CTRL+SHIFT+O"` and `"META+LEFT_BRACKET"`, matching a binding's declared modifiers exactly and requiring the others released, so a plain `"O"` no longer fires while Ctrl is down. Everything that parsed before still parses to the same key with no modifiers, so existing `config.yaml` files keep their meaning, and an existing `capture.toggleKey: O` migrates to the new `SHIFT+O` default automatically. Three review rounds each found the same failure class in the round before it — a fix applied wider or narrower than the defect it targeted, under a comment asserting the result was safe — so the closing round was scoped deliberately against that pattern, with every fix verified RED by reverting it. The debug-only Ctrl+P stats copy stays behind the `debug.viewEnabled` gate and the overlay toggle stands down for it only while it can actually run; promoting the copy above the gate to keep the keystroke alive had instead given every shipped install a shortcut that silently overwrote the OS clipboard. That copy also now requires the **left** Ctrl, because `CTRL` in a chord means either one and right Ctrl is player two's default Start — the same oversight as right Shift being player two's jump, one key over. The unbound-binding guard moved out of a single call site into `InputHandler.isKeyDown` itself, closing it for the six callers that never guarded: with `rewind.liveKey` unbound, both sides of the pad-substitution comparison were `-1`, so a held bumper reported every unbound binding as held, and player one's B and C ship unbound. And the guard that polices the per-binding modifier table now follows a read hoisted into a local, which was the dominant call style in the one method it existed to sweep.
- **Harness suite runs in parallel and can be gated by game or movie — 957s to 371s (2026-07-25):** the native differential suite replays ~1.4 million frames of real Genesis emulation, and ran them one capture at a time. It now runs across a worker pool: **371s at the default `--jobs 8`**, with a per-test outcome set identical to `--jobs 1`. The scheduling policy is deliberately one line — order longest-first from a recorded timings file — because the 466,334-row S3K complete-run gate takes 368s on its own and no concurrency makes a full run shorter than that; `--jobs 4` measures 388s, so 8 is comfortable headroom rather than a throughput claim. Memory is deliberately not modelled, since a capture is now flat in RSS regardless of movie length. New selection flags `--game`, `--movie`, `--gates-only` and `--no-gates` compose with the existing `--filter`, and a `--no-gates` tier runs in 4s. Parallelising surfaced three real hazards, all fixed rather than worked around: `NativeStandardOutputSilencer` `dup2()`s `/dev/null` onto fds 1 and 2 process-wide, so the first parallel run destroyed concurrent output and reported 34 of 352 tests while exiting 0 — the CLI-driving classes are now serial; gate scratch moved off `/tmp` (a 16 GiB tmpfs that concurrent gates filled, three failing with ENOSPC) to `.scratch/`; and the runner now fails a run whose result count does not match its selection, so a suite that loses a test can no longer exit 0.
- **A recording started on the master title screen keeps its audio into gameplay (2026-07-25):** entering a game rebuilds the audio presentation *twice*, and the first rebuild is not the backend swap — `Engine.exitMasterTitleScreen` runs `resetForGameplayFromMasterTitle`, and its `AudioManager.resetState()`, before the real backend is installed. The previous fix carried the recording's audio lease across the backend swap only, so `resetState` still retired it first and the recording was already dead by the time the carry could apply; the reported `IllegalStateException: Live capture audio handle is no longer attached` was never actually removed. The recorder caught it, logged one warning for the entire session and substituted phase-correct silence, so the recording did not fail — it just wrote a silent audio track, which is worse. A mode transition rebuilds the presentation; it does not end a recording of the window, so `resetState` now marks the lease for rebind on the same terms as a backend swap, and only `destroy()` — the genuine teardown — retires it. That also carries a recording across a level restart, for the same reason. The cause was found by measuring the real `AudioManager`, producer, music source and recorder across the transition *before* editing: that measurement ruled out both standing hypotheses (a packet-size change, then a sample-rate change — OpenAL negotiates the same 48 kHz the title screen assumes, so the geometry never moves) and reproduced the reported failure byte for byte. The same measurement surfaced a second defect: on a genuine rate change the carry threw `IllegalArgumentException: snapshot clock rate does not match this clock` straight out of the presentation builder, which most of `AudioManager` reaches. A recording is muxed at the rate captured when it started (ffmpeg `-ar`), so following the producer across a rate change would write pitch-shifted audio that looks like it worked; the lease is now retired with a warning naming both rates, and nothing escapes the rebind. The regression test was the other half of the problem — it asserted a *frame count* against a backend that produced silence anyway, so it passed identically whether audio flowed or not, which is why the partial fix looked verified. It now asserts the PCM the recorder is actually handed, driven by a real music source through a real producer, with recorder and speaker proven byte-identical on one packet.
- **Run captures stream to disk and gzip on the way, cutting peak memory 85% (2026-07-25):** `S1RunCaptureRunner` buffered a whole segment and `S2RunCaptureRunner` the whole run in UTF-16 `StringBuilder`s before publishing anything, so the S2 complete-emeralds capture peaked at 1.51 GiB RSS — an order of magnitude above the *larger* S3K complete-run pass, purely because that one already streamed. Both run machines were shown to guarantee every armed segment reaches exactly one finalize, so the buffers were protecting nothing and no staging-file fallback was needed either; both runners now write rows straight through a shared segment sink. Measured peak RSS: S2 complete-emeralds **1,581,460 kB → 232,716 kB** and slightly faster (3:41.8 → 3:34.0), S1 complete-run 620,728 → 231,532 kB (1:36.4 → 1:27.9). Memory is now flat regardless of movie length — the ~231 MB residue is the emulator core itself. Compression additionally moved *into* the stream: a streamed payload is written through a gzip stream into its staging file, so the uncompressed form never exists on disk (S1 complete-run staging 638 MB → 36.8 MB). Verify-before-destroy is preserved exactly rather than weakened for the streaming — the plaintext is SHA-256'd and counted on its way into the compressor, and the finished gzip is decompressed and compared by hash *and* length before it may join the publication set.
- **Trace replay test harness deduplicated (2026-07-25):** ~660 lines of comparison, diagnostic-string and report-writing code duplicated across `AbstractTraceReplayTest`, `AbstractCreditsDemoTraceReplayTest` and the three per-game special-stage bases are re-homed into `TraceFieldComparisons`, `TraceReplayDiagnostics` and `TraceReportWriter`, with two bespoke probes moved to `S2SkyChaseBadnikDiagnostics` / `S3kSidekickCylinderDiagnostics` and `TestS3kAizTraceReplay`'s repeated config save/restore replaced by an `AutoCloseable` scope. Behaviour-neutrality was proven on the divergence data rather than on pass/fail alone: running one replay per game through each refactored base on both trees produced whole-JSON-identical trace reports, including an 866 KB report carrying 3,258 individual error records. Also removes the dead `verifyFrame` component from `TraceCaptureTool.Args` (zero references repo-wide; the live field is `verifyFrames`).
- **Trace payloads compress at capture, and an uncompressed one can no longer be committed (2026-07-25):** trace payloads must never reach a commit uncompressed — an S3K complete-run aux stream is ~254 MB raw against ~12 MB gzipped (~21×), so an uncompressed one is past GitHub's 100 MB per-file hard limit, and hook-enabled full runs have breached this before. Two independent enforcement points, neither sufficient alone. The native harness now gzips `physics*.csv` / `aux_state*.jsonl` inside `NoReplacePublisher`'s all-or-nothing publication, porting `tools/traces/compress-traces.ps1`'s semantics — same name patterns, same 1 MiB threshold, and the same verify-before-destroy ordering, where the gzip is decompressed and compared by SHA-256 *and* length before the staging file is discarded. It runs before the first `link(2)`, so a verification failure publishes nothing at all. Compression is on by default (`--no-compress` opts out, `--compress-threshold` retunes) because an opt-in flag fails exactly when someone installing a fixture forgets it; pairing the default with the inherited threshold makes the harness and the repo's commit policy agree by construction. Separately, `TestTraceFixtureCompressionGuard` and a `.githooks/validate-policy` check reject an uncompressed payload committed under `src/test/resources/traces/`, covering the Lua and stable-retro routes the harness cannot, with the 36 pre-existing files grandfathered in an explicit baseline.
- **S3K recorders read the lives counter as a V-blank counter; all 39 fixtures regenerated (2026-07-25):** the second and last defect of the same class as the frame-counter fix below. Both S3K recorders sampled `0xFE12` for `vblank_counter`, which is `Life_count`; `V_int_run_count` is a `ds.l` at `0xFE0C`, so the correct low word is `0xFE0E` — exactly what the S1 and S2 recorders have always used. The column consequently held two to four distinct values across entire multi-hour runs, the lives count in the high byte. Both recorders are fixed, all 39 S3K fixture directories regenerated (35 carry the column; the four special-stage-profile dirs do not), the native port unified, and every S3K differential gate re-pinned. **No frontier moved**: all 15 replay classes report byte-identical first non-camera divergences, and the special-stage suite stayed green. The change was predicted before it was made and the prediction held — rows where the gameplay frame counter and lag counter both plateaued would flip `FULL_LEVEL_FRAME` → `VBLANK_ONLY`, forecast at 2,094 for `hcz_completerun` and 0 for five named fixtures, measured at 2,091 and exactly 0. It also turned a long-red test green: `TestTraceExecutionModel` asserts that a V-blank ran while `Level_frame_counter` did not, which was unprovable while the column read a frozen `0x0B00` — eleven lives. The assertion was right; the data was wrong.
- **S3K standard recorder read a dead RAM address; release-slice fixtures regenerated (2026-07-25):** `s3k_trace_recorder.lua` sampled `0xFE08` (`Debug_placement_mode`) instead of `0xFE04` (`Level_frame_counter`), so `gameplay_frame_counter` and every aux `vfc` / `level_frame_counter` field was constant zero for the recorder's entire life — meaning the AIZ→HCZ, CNZ and MGZ replay frontiers, the primary release slice, were being diagnosed against a value the ROM never produces. `6564667eb` had fixed the identical bug in the sibling complete-run recorder four days earlier; it did not propagate, because each of the six recorders carries its own copy of these constants. The recorder is fixed (v6.31-s3k), the three canonical fixtures are regenerated with every delta categorised against a named cause before installing, and the native port's now-redundant per-profile address fork is deleted. **All three frontiers held** — first divergences unchanged at f2696 `x_speed`, f185 `y_speed` and f5164 `air` — confirming no engine regression. The regeneration also exposed a test-harness defect: `TestS3kAizTraceReplay`'s private stepper mishandled `ADVANCE_ONLY` rows that a dead-zero counter had previously made unreachable, running non-ROM level ticks through the AIZ plane-intro prefix; routing scenario frames through the same closure driver the whole-trace loop already used took that class from 14 failures to 1. A cross-recorder audit of all six recorders' ROM constants now lives in `tools/bizhawk/SHARED_MODULE_HANDOFF.md` alongside the original extraction plan, together with one remaining defect of the same class — `ADDR_VBLA_WORD` reads `Life_count` in both S3K recorders — left queued so its own fixture regeneration stays attributable.
- **Native headless GPGX S3K complete-run recorder — the Lua recorder fleet is fully migrated (2026-07-25):** the native harness now records Sonic 3 & Knuckles complete-run, bonus-stage and special-stage traces, replacing `s3k_complete_run_recorder.lua` and completing the migration of **every** Lua recorder (S1, S2, S3K standard, S3K complete-run) to byte-parity-gated native ports. One untruncated pass over the 466,334-row playthrough movie reproduces all seven `*_completerun` segment fixtures byte-identically in 5m57s at 235 MB peak RSS, and `--run-id` reproduces the 25-segment Knuckles multi-bonus run plus its `run_manifest.json` and the four published standalone bonus/special-stage fixtures — physics, aux and manifest compared by raw sha256 with zero normalization, metadata differing only in `recording_date`. Adversarial review caught an input column indexed by last-applied frame rather than BK2 row, and a whole-run in-memory buffer. The legacy `runs/s3-knux-multibonus-ss/` fixture set — a 2026-07-19 Windows capture three recorder versions behind, un-reproducible by any current recorder because of CRLF line endings, a dead-zero frame-counter column and armed diagnostic hooks — was regenerated at 6.32 so it too gates byte-exactly. That recapture exposed and fixed a real engine defect: trace replay seeded `LevelManager`'s frame counter from the first driven row instead of the previous completed frame, running every frame-counter-keyed object phase one frame ahead of ROM, which a dead-zero counter column had hidden since the contract was written.
- **Native headless GPGX S3K standard trace recorder (2026-07-25):** the native Linux/Mono harness now records Sonic 3 & Knuckles standard traces in both canonical profiles — `aiz_end_to_end` (the AIZ1 → AIZ2 → HCZ handoff, surviving the in-level reload that ends other profiles) and `level_gated_reset_aware` (arm/discard/re-arm across soft resets, stopping on zone-leave). Output is byte-identical to `s3k_trace_recorder.lua` on all three canonical fixtures — AIZ end-to-end (20,798 frames), CNZ (42,253) and MGZ (35,912) — each locked by a permanent ROM-backed differential gate with zero normalization on `physics.csv` and `aux_state.jsonl`, and metadata deltas pinned exactly per fixture rather than by loose pattern. The hook-driven aux families are explicitly deferred rather than silently dropped: all three fixtures were captured with the diagnostic hooks unset, a test pins that absence against the fixture bytes, and the CLI refuses every unmodeled `OGGF_*` recorder variable instead of producing non-canonical output. Adversarial review widened that refusal from 3 variables to 11 and stopped non-discarding captures from buffering the whole run in memory. `s3k_complete_run_recorder.lua` and its `6.32-s3k-completerun` fixtures remain Lua-only.
- **S2 complete-game trace capture (2026-07-24):** `s2_trace_recorder.lua` v9.13-s2 run mode now survives in-level reloads (deaths, time overs, act and zone transitions) as `death_restart`/`level_advance` manifest transitions instead of ending the run, and its special-stage segments carry the standalone SS recorder's full aux event stream. The native harness mirrors all of it, accepts recorded Genesis FM chip models, and both implementations were proven content-identical on a new 259,590-frame canonical run — Sonic+Tails completing the game with all 7 emeralds, installed at `traces/s2/runs/s2-sonic-tails-complete-emeralds/` (35 segments, 34 transitions) with a permanent per-segment differential gate; the halfpipe fixture set was regenerated for the enriched SS aux.
- **Native headless GPGX S1 complete-run and run-mode recorder (2026-07-24):** the native harness now covers `s1_complete_run_recorder.lua`: one pass over the canonical 195,493-row complete-run movie reproduces all 19 `*_completerun` fixture segments byte-identically, and `--run-id` run mode reproduces the GHZ maze round trip (level → special stage → level, giant-ring transitions, `run_manifest.json`) plus the standalone special-stage fixture with zero normalization beyond `recording_date` and the pinned version-marker line. Nine permanent ROM-backed differential gates now guard S1 and S2 capture parity; adversarial review removed a duplicated stage-free capture engine in favor of the shared run-mode runner.
- **Native headless GPGX S2 trace recorder (2026-07-24):** the native Linux/Mono harness (`tools/bizhawk-headless/`) now records full canonical Sonic 2 traces in all three `s2_trace_recorder.lua` modes — plain `gameplay_unlock`, `level_gated_reset_aware` with `--gameplay-segment` selection, and `--run-id` run mode with special-stage detours and `run_manifest.json`. Output is byte-identical to the Lua on four canonical fixture sets (EHZ1 full run, ARZ segments 0 and 1 from one movie, and the EHZ half-pipe round trip's five segments plus manifest), each locked by a permanent ROM-backed differential gate; a second S1 gate against the canonical MZ1 full run was added alongside. Adversarial review also aligned the plain-mode movie-end stop ordering with the Lua's post-advance `on_frame_end` semantics on paths the fixtures cannot exercise. The Lua recorders remain the reference implementation and the non-Linux capture path.
- **Native headless GPGX S1 trace recorder (2026-07-24):** a Linux/Mono C# harness (`tools/bizhawk-headless/`) now drives BizHawk 2.11's GPGX core directly — no EmuHawk, X11, or Lua — and records full canonical Sonic 1 traces (`--mode trace`: physics.csv v7, aux_state.jsonl, metadata.json trace_schema 4) with auto-detected BK2 frame offset. Output is byte-identical to the Lua `s1_trace_recorder.lua` on the canonical GHZ1 fixture (only `recording_date` differs), gated by a permanent ROM-backed differential test; adversarial review also fixed a movie-exhaustion end-path off-by-one the fixture could not exercise. The Lua recorder remains the reference implementation and the non-Linux capture path.
- **S3K structural trace replay bootstrap (2026-07-23):** S3K replay no longer relies on `pre_level_intro_prefix`, `sidekick_seed_frame_prelude`, or `pre_trace_osc_frames` metadata, nor on frame-zero motion-shape heuristics. Fresh level starts preserve the ROM's grounded first-dispatch lifecycle, AIZ pre-level input-only rows no longer tick the resident level across headless/capture/live/rewind playback, and the regenerated AIZ CSV input column now follows the canonical BK2 offset contract. The standalone AIZ and CNZ traces clear their fixture/input/bootstrap regressions and reach later true parity frontiers at AIZ f2707 and CNZ f185; the broader remaining S3K fleet debt stays explicit in the frontier log.
- **S3K universal CSV v7 fixtures regenerated (2026-07-23):** standalone, complete-run, bonus, and special-stage recordings now install physics, aux, and metadata from the same BizHawk 2.11 capture instead of mixing v7 CSV/metadata with stale v5 aux. S3K recorder execution hooks are opt-in for focused diagnostics, Linux captures suppress Mono Lua-console repaint churn by default, and replay input sampling follows each profile's physical BK2-row convention. The consistent fixtures expose new AIZ/CNZ comparison frontiers for follow-up rather than failing at the former input-alignment guard.
- **Configurable recording codecs, container and ffmpeg commands (2026-07-25):**
  `capture.codec` selects `ffv1` (default), `h264` or `h265` — all three
  lossless — and `capture.audioCodec` selects `flac` (default) or the lossy
  `aac` / `mp3`, marked as lossy where they are configured. `capture.container`
  sets the file extension so MP4 can be written directly. For anything the
  codec keys do not cover, `capture.ffmpegPass1Args` and
  `capture.ffmpegPass2Args` replace either ffmpeg pass outright; emptying the
  second skips muxing and records video only. H.264 and H.265 encode RGB
  directly rather than the conventional `yuv444p`, which is lossless in the
  codec's own colour space but does not return the submitted pixels — a
  measured round trip guards that. The bundled configuration template is now
  also written to `config.yaml.example` on every run, so its comments and
  worked ffmpeg recipes stay visible next to a `config.yaml` that has already
  been written.
- **Recording tells you when it stops, and stops filling `/tmp` (2026-07-25):** a
  recording that ends for a reason you did not ask for — the window being
  resized, or a capture failure — now replaces the red-dot/`REC` indicator with
  a red `REC STOPPED: RESIZED` or `REC STOPPED: ERROR` notice for three seconds,
  instead of the indicator silently vanishing exactly as if you had pressed the
  toggle. Alongside it, three temporary-file faults that between them broke long
  recordings outright: the lossless intermediate was written to the system temp
  directory rather than beside the finished file, ffmpeg's own diagnostics were
  discarded so a full disk surfaced only as `Stream closed`, and building an
  input handler without a configuration leaked a directory per call — hundreds
  of thousands of them across test runs. Tests now clean up after themselves,
  with a guard to keep them honest and the test JVM's temp directory pointed
  inside `target/`.
- **Unified audio presentation (2026-07-25):** every audible source — SMPS music
  and SFX, fallback WAV, pitched SFX, and raw SEGA PCM — is now mixed by one
  allocation-free presentation producer that owns cadence, final PCM, history,
  rewind, and capture taps. Each presented frame chooses exactly one forward,
  silent, or reverse audio mode, and OpenAL became a bounded sink for that one
  final packet rather than a set of independent sources. The speaker and any
  recording receive independent views of the same producer-selected packet, so
  toggling a recording can no longer remove music, rings, or effects, and a
  recording started mid-rewind picks up the next audible reverse packet. The
  temporary deterministic-runtime and recording-lease switches this replaced
  are removed, and offline trace capture now records the same final packets as
  live recording. ROM-backed tests assert non-zero final PCM for Sonic 1, 2 and
  3&K across title, gameplay, ring and special-stage routes.
- **Lossless live viewport recording (2026-07-23):** press `Shift+O` to toggle a
  viewport-only MKV recording during normal play. OpenGGF writes synchronized
  FFV1 video and stereo FLAC audio—including pause/frame-step silence and
  rewind presentation—to `capture.outputDir`; `ffmpeg` must be available on
  `PATH`. A red-dot/white-`REC` indicator appears in the window while active
  but is excluded from both the recording and F12 screenshots. Changing the
  viewport or framebuffer size stops and finalizes the current file. If the
  audio tap fails mid-recording the video continues with phase-correct stereo
  silence rather than aborting the file. This is independent of the Shift+F9
  input/movie recorder; see
  [CONFIGURATION.md](CONFIGURATION.md#capture) for configuration and output
  details.
- **CPZ2 and DEZ trace regressions restored (2026-07-23):** Sonic 2 automatic Tails recovery flight now clamps its delayed Y target to the gameplay waterline using the effective ROM feature-zone key, matching `TailsCPU_Flying_Part2` and closing CPZ2's f7206 CPU-target mismatch. DEZ's title-card bootstrap now selects Tornado ordering only when a live ROM-loaded ObjB2 exists, preserving the native level-start anchor without hydrating trace snapshots; both complete level-select traces and the focused S2 bootstrap suite are green.
- **Sonic 2 sidekick trace regression cleanup (2026-07-23):** trace replay now respects the recorded per-frame sidekick-presence bit when SCZ/WFZ suppress the configured Tails sprite, while dormant CPU RAM remains available for diagnostics. The S2 fresh-render-entry counter delay is also constrained to its native lower-boundary state, restoring CNZ2, MCZ2, and OOZ1 without regressing OOZ2 and advancing CPZ2 to its later independent CPU target frontier.
- **ICZ replay frontier completed (2026-07-22):** the ICZ trace-fidelity lane closes its recorded replay frontier with ROM-state-driven corrections across the boss, frozen-block and freezer lifecycle, moving and tension platforms, snow/steam scheduling, terrain handoffs, and end-of-act ownership. The accompanying rewind lifecycle correction drops a freezer parent's detached capture-cloud identity when its slot unloads, preventing stale child references from being restored.
- **Sonic 1 100% whole-movie trace playback (2026-07-22):** the complete-run recorder and manifest-driven chain now carry a 225,104-input-frame movie through repeated level arms, deaths, all six emerald stages, glitch-heavy MZ/SLZ routing, Final Zone, credits, and the post-credits return to the title screen. The terminal 10,943-row tail reports `finalMode=TITLE_SCREEN`; ROM-state fixes made along the route cover object lifecycle, collision/contact cadence, event state, title-card/transition handling, rewind restoration, and Final Zone cylinder/plasma/boss timing. Comparator mismatches remain explicit parity frontiers rather than being hydrated or route/frame-carved out.

- **Develop red-suite remediation and origin integration (2026-07-21):** the frozen 36-method develop red set is green after rewind-graph closure, canonical touch/physics/lifetime ownership fixes, behavior-neutral manager extractions, ROM-timed CNZ/MGZ fixture corrections, and a bonus-stage transition coordinator that restores checkpoint, ring, water, and persistent respawn state before fresh object materialization. The branch also incorporates the latest `origin/develop` respawn-persistence work, including failure-safe one-shot cleanup. Verification completed with the owning guard/package matrix and two consecutive full-suite runs of 12,473 passed, zero failures/errors, and 15 fixture-dependent skips.

- **Respawn-remember table persists across the star-post bonus round-trip — seg2 chain 11651 -> 5968 comparator errors (2026-07-21):** objects the player broke or collected before entering a star-post bonus now stay broken/collected on return, matching the locked-on ROM where Respawn_table_keep shields the respawn and ring tables through the bonus reload. The triple-proven root (BizHawk PC-execute probes + ROM byte disassembly + the recorded break event): a monitor broken on the first AIZ pass reloaded intact and solid in the engine, forming a phantom wall the ROM's broken shell never presents — the recording's monitor reloads as an inert Sprite_OnScreen_Test stub. The placement controller's remembered/stay-active state is now captured at bonus entry and restored after the return reload. A matching death-respawn latent gap is documented as a cited follow-up.

- **Trace-run tooling cleanup + monitor pass-through parity (2026-07-21):** the trace capture tool now rejects run-manifest entries with a clear message instead of failing opaquely mid-capture, the run catalog defends against synthetic fixtures appearing under a future runs directory, and the chain harness's comparator-frame-base contract is consolidated into one authoritative javadoc (ending a class of repeated diagnostic misreads). Separately, monitors now model the locked-on ROM's Knuckles exemptions: a gliding or post-glide-sliding Knuckles passes through and breaks monitors instead of being blocked, with the character gate keeping Sonic's insta-shield unaffected. (The seg2 chain frontier's broken-monitor respawn-persistence fix — triple-proven root, design approved — remains in flight on its branch; the probe evidence is banked in tools/bizhawk.)

- **Helper-state rewind coverage guard (2026-07-21):** a third coverage lane in the rewind analyzer now verifies that every final helper-object field on a spawnable object either routes through the capturer's own public capture predicates (explicit codecs, RewindStateful, or the name-heuristic plain-state-holder path) or is deliberately policy-exempt — closing the blind spot where a helper class whose name misses the heuristic, or a future non-codec field knocking a holder off the in-place path, would silently lose state across rewind. A codebase-wide sweep confirmed zero existing gaps, so the guard's baseline starts empty and any future violation fails immediately. The guard's first dry run itself caught an under-specified filter in the design (the policy-registry gate), corrected before landing.

- **AIZ ride-vine held-player animation parity — mega-run chain AIZ segment 56 -> 4 comparator errors (2026-07-21):** the vine hold force-rewrote the hang animation every frame, but the ROM writes it once at grab and never again — so the first floor contact during a hold runs the player's touch-floor routine and its result latches for the rest of the grab. The fix models the disassembly's actual two-gate structure (verified from the full Knuckles touch-floor body): a Status_Roll-gated walk reset (fires only when the player grabbed while rolling) and a separate glide-family reset that a held vine player can never trigger, plus the swing branch's unconditional walk write. Rewind coverage rides the existing automatic plain-state-holder capture (no bespoke snapshot), with the new latch fields pinned by a test extension. Adversarially reviewed through two rejection-driven iterations that caught a real cross-character regression (a not-rolling grab must keep the hang animation) and corrected the capture-mechanism approach.

- **CNZ Act 2 rival-Knuckles encounters and magnetic end boss corrections (2026-07-21, parallel session):** both rival-Knuckles cutscenes run their native raw animation scripts with exact camera stops and facing bits, the subtype-6 button decodes its native start/width pairs and drives the whole-scene shake in sync with the Knuckles theme, the magnetic end boss runs the locked-on ROM encounter path with native SFX cadence, hover fans stop unrelated PLC refresh work, spring init-only SST execution is preserved, and the CNZ complete-run reaches full physics/animation green on its recorded route.

- **Knuckles push-animation parity — mega-run chain AIZ segment 168 -> 56 comparator errors (2026-07-21):** the Knuckles animation profile was missing the flag that routes Status_Push through the ROM's walk-script sub-handler, so pushing Knuckles either advanced held mapping frames early (at rest) or exposed a PUSH anim byte the ROM never publishes (while moving) — one omission, two symptoms across all twelve recorded push windows. The fix also models a genuine Knuckles/Sonic handler difference found in the disassembly: Knuckles reloads its push-freeze timer with a >>8 shift where Sonic uses >>6, captured as a per-character profile field with the default preserving byte-identical behavior for every other character and game. Character-profile-scoped; verified regression-free across the S1/S2 chains, S2 level replays, and all six stage comparators. Remaining AIZ-segment residuals (vine release timing, push release lag, glide-anim lifecycle, a roll-path ordering frame) are triaged with disasm-cited briefs and assigned to lanes; the frontier-log diagnosis narrative is calibrated to separate directly-observed evidence from inference.

- **Gumball chain interior 3709 -> 9 comparator errors + a live-play ring carry-over fix (2026-07-21):** the mega-run chain's first bonus interior closed on two roots — the BONUS title-card-exit fall-through frame is the interior's first gameplay tick, but the forced-input bridge armed only after the mode flip, so that tick read neutral input and the player free-fell instead of taking the recorded grounded nudge (the bridge now re-arms in the bonus branch of exitTitleCard); and bonus-stage exit now restores the returning level's rings from the interior's live HUD count, modeling the ROM's Ring_count -> Saved_ring_count exit copy (the gumball ring ball's transient +20 to the saved count is discarded exactly as the ROM discards it — the former reward-sum reconstruction over-carried by 10). The recovered interior RNG prime (segment-entry seed applied at the chain boundary via the standalone bootstrap seam) proved out: the ball series now replays faithfully. The chain clears the full gumball round trip and the following AIZ segment; the logged frontier is the ROM's ~150-frame post-catch exit choreography, which the engine still shortcuts.

- **Star-post bonus round-trips made ROM-faithful in the chain (2026-07-21):** three roots — the chain driver no longer pre-seeks the live-cursor bonus interiors (that was an SS-only need), star posts now keep a persistent activation mark modeling the ROM respawn bit (the ROM zeroes the checkpoint index on bonus entry and never restores it — the respawn bit is what prevents re-triggering), and the bonus return restores the player to the star post's recorded position rather than the live touch centre. The mega-run chain now clears the gumball interior's boundary/checkpoint/positional assertions; the remaining root is interior RNG fidelity under organic entry.

- **Knuckles glide activation freed from a Sonic-only gate (2026-07-21):** the glide branch sat behind the invincibility check modeling Sonic_FireShield's Status_Invincible test — the ROM's Knux_Test_For_Glide carries no such suppression, so gliding while star-invincible was wrongly refused. With the branch reordered, the mega-run chain replays the entire AIZ Knuckles segment into the first bonus stage; the frontier moved to the chain driver's bonus-interior exit handling.
- **Knuckles glide-slide landing parity (2026-07-21):** the glide->fall->slide landing now matches the ROM bit-exactly — the slide runs airborne-flagged as loc_1693E never clears Status_InAir, the floor probe applies Sonic_CheckFloor's odd-angle rule, and the fall/slide run move-before-accel in ROM order (the prior ordering biased every fall by one air-accel step per frame). Fixes the mega-run chain's vine-grab miss; the AIZ frontier advanced to a distinct glide-activation root now under investigation.

- **S2 halfpipe round-trip chain GREEN + a live-play oscillator parity fix (2026-07-20):** the ROM only advances the global oscillator inside `Level_MainLoop` — never during title-card wait loops — but the engine ticked it every locked title-card frame, phase-offsetting every oscillation-driven platform after any title card. Holding it at the ROM baseline through the title card (cited: s2.asm:4914-5108, mirrored in S1) closes the S2 chain end-to-end through both halfpipe cycles. Two of three chain tests are now green; the S3K mega-run chain remains at its Knuckles glide frontier. Documented follow-up: title-card duration parity for CPU-sidekick catch-up diagnostics.

- **Chain-replay foundation for trace runs (2026-07-20):** the run walker is now fully manifest-driven (no hardcoded segment counts), with per-entry-kind boundary assertion helpers spanning all four transition kinds, a derived step-cap that turns frozen-cursor hangs into diagnostic failures, and a named seam for per-frame special-stage comparison. Three chain tests consume the committed S1/S2/S3K runs (the S1 maze round trip reached green on its lane); the S2 special-stage-return handoff was rebuilt (cursor pre-seek, input-override release, fall-through comparator attach — the base-class supersets from both lanes reconciled), and the chains exposed genuine engine frontiers now logged: the SS-return title-card duration drifts the free-running oscillator phase versus the ROM, and the Knuckles mega-run surfaced glide/vine parity gaps (its lane banked Knuckles glide centre/sensor/anim fixes and interior reports now write before boundary asserts throw). Real fixes banked en route: manifest act indexing into level loads, Knuckles glide activation/anim fidelity, TraceReplayDriver ground-snap contract.

- **Pachinko bonus comparator GREEN — ALL SIX STAGE COMPARATORS AT ZERO (2026-07-20):** pachinko closed 391 -> 0 through the reward subtype/ring coupling (the ROM awards a shield, not rings, from the recorded orb), a two-bug bumper bounce compound, flipper catch/ride/launch fidelity, the bumper off-screen self-despawn, touch-response-path bumper collision, a bonus-exit frame skip in LevelFrameStep modeling the ROM Restart_level_flag branch (note: this also applies in live play for all S3K bonus-stage exits — ROM-faithful and review-verified), and rewind-policy registration for the new bumper fields. With gumball, slots, blue spheres, the S1 maze, and the S2 halfpipe interior, every stage trace comparator in the engine now reports zero errors.
- **Gumball and slots bonus comparators GREEN (2026-07-20):** gumball closed 74 -> 0 via the spring child's landing-snap override (the shared solid-contact override was clobbering SolidObjectFull2_1P relative placement) and a shared-animation fix — the SWITCH ($FD) end-action no longer eagerly syncs prev_anim, matching Animate_Sonic exactly. Slots closed 182 -> 0 across eight more roots: VBlank-true counter advancement, reward-drain ticking, cage release timing, tile-anchor reconstruction, bumper launch frame alignment, reel-wall flash cadence, and the goal-exit pair — with a routine-override seam so the comparator reads the slot player object routine faithfully.

- **Bonus-stage green campaigns round 2 (2026-07-20):** sixteen more disassembly-cited roots across the three bonus comparators. Gumball's frontier drove f380→f895 (ejected balls now self-poll the ROM's `Check_PlayerInRange` box instead of the generic touch framework; a single wrong push velocity decomposed into three compounding fidelity bugs; the dispense cadence now models the 29-frame `Animate_RawNoSSTMultiDelay` cycle). Pachinko halved again, 896→391, with nine flipper/orb roots (baseline slope, lock/launch ordering, catch animation restart, capture control bits, single-touch item-orb release gate). Slots surfaced a capture-data blocker: reel outcomes seed from `V_int_run_count`, which the recorder stored as a frozen placeholder — the recorder now captures it at bonus-segment arm, the deterministic re-capture verified byte-identical physics rows, and replay primes the reel counter from metadata — the cited reel divergence is fixed, with later reel-cycle roots unmasked for the next round.
- **S3K bonus-stage physics campaign: ten disassembly-cited roots (2026-07-19):** the gumball/pachinko/slots comparators drove out a shared bootstrap ground-snap bug plus nine stage-specific roots — slot-runtime subpixel truncation and fabricated angles, unclamped reversal-decel, cage-capture subpixel preservation, pachinko orbit negate-before-shift ordering and roll-entry height, gumball bumper fallback-bounce removal, inclusive right-edge contact, and RNG reseed provenance. Frontiers advanced from frame 0 deep into each run (pachinko f427+, gumball f380, slots f47+); remaining divergences are catalogued for the next campaign round.
- **S1 maze special stage near-green: 503 → 13 errors (2026-07-19):** eight campaign iterations modeled the ROM's 44-VBlank pre-physics hold (mined from the S2 halfpipe's TRACE_ACCURATE precedent), the mid-hold rotation-init boundary, setup-time palette-cycle advance, byte-truncation-ordered angle negation, the four-cell fall-probe scan, bumper flash-lockout, and the emerald-sparkle exit arming — every fix disassembly-cited. The 13 remaining errors are one exit-ramp/GOAL-approach cluster at the trace tail.
- **S3K blue spheres trace-GREEN (2026-07-19):** the first stage comparator to reach zero errors — four campaign iterations fixed the comparator's stale-RAM frame-0 basis, the player's turn-rotation early-return, per-frame vs stepped-frame routine pacing, and the bumper unlock/perfect-tally branch structure, all disassembly-cited. `TestS3kSpecialStageTraceReplay` now reports 0 errors over the 4,630-row Knuckles capture.
- **All six stage round-trip recordings captured and live (2026-07-19):** the three round-trip movies (S1 GHZ maze, S2 EHZ double-halfpipe, S3K Knuckles multi-bonus mega-run) are captured and committed — 33 segments across three run manifests, including two S2 halfpipe detours in one movie, five slot-machine visits, and three blue-sphere stages with emeralds 0→3. All six stage replay comparators (S1 maze, S3K gumball/pachinko/slots/blue-spheres, plus the S2 interior) now run against real traces; the five new baselines all frontier at frame 0 (spawn/bootstrap state) and seed the stage green campaigns. Recorders gained Player_mode-derived team metadata (Knuckles routes label correctly), multi-detour segment naming, and verified capture-launch documentation.
- **S2 halfpipe round-trip trace recording (2026-07-19):** the Sonic 2 level recorder gains an opt-in run mode (`OGGF_TRACE_RUN_ID`) that captures a level→special-stage→level star-post round trip as a manifest-backed trace run — per-segment directories, an embedded 48-column halfpipe writer with a real lag column, and boundary records carrying the ROM's saved return position and ring/emerald state. The existing level-select workflow is byte-stable without the flag, the hook-based interior special-stage recorder is untouched, and a synthetic three-segment fixture pins the emitter's exact output shape. This completes the multi-stage trace-run slate: every S1/S2/S3K special and bonus stage now has a recording + replay path awaiting its round-trip recordings.
- **S1 maze special-stage trace pipeline (2026-07-19):** the Sonic 1 rotating maze gains the same trace surface as the S3K special stage — a 15-field comparison snapshot, the `s1_special_stage` 14-column schema (16.16 player position, rotation, rings/emeralds), the S1 complete-run recorder v3.15 with the giant-ring detour state machine and run-manifest emission funneled through a single end-of-run finalize, and a single-player VBlank-paced replay harness with delta-based ring comparison. The provider is proven to boot headlessly with no new init hook; the replay test activates when the GHZ maze round-trip recording lands.
- **S3K slot-machine bonus replay (2026-07-19):** the slot machine joins gumball/pachinko in trace replay — the deferred-setup seam builds the slot runtime headlessly, and a camera-focus-keyed sprite seam aligns the comparator with the runtime's player swap (proven by a headless boot test). The replay test activates when a Sonic-solo slots recording lands. Two guard escapes inherited from the previous merge (a naming-rule violation and an unbaselined test setup) were also fixed.
- **S3K blue-spheres trace pipeline (2026-07-19):** the special stage now has a full trace surface — a 16-field comparison snapshot, the `s3k_special_stage` 20-column schema with a twice-re-derived phase-overlay RAM map, recorder v6.31 emitting real `ss/` segments for giant-ring detours, a VBlank-paced replay harness whose finish boundary anchors on the exit-spin completion (covering success and failure exits), and `fresh_load`-driven launch config. The special-stage provider is proven to boot headlessly through the real ROM art path; the replay test activates when the blue-spheres round-trip recording lands.
- **Visual trace-run playback (2026-07-19):** trace runs now appear in the test-mode picker as single entries and play back visually as one continuous session — per-segment comparator/HUD/camera swapping driven by game-mode flips, cursor re-seeks at segment boundaries, pause-on-first-divergence in every segment, and a per-game special-stage launch-config seam. This completes the multi-stage trace-run foundation (plans a-d); stage-interior schemas and the round-trip recordings activate it end to end.
- **Chained trace-run driver (2026-07-19):** trace replay can now drive one continuous engine through level → bonus-stage → level transitions — non-consuming transition peeks, a BONUS_STAGE playback bridge (recorded input + BK2 cursor advance during bonus interiors), a segment walker with boundary-window assertions observed inside the frame-observer callback, and per-segment cursor re-seeks. The round-trip chain test activates automatically once the named bonus recordings land.
- **S3K bonus-stage replay slice (2026-07-19):** the trace framework can now replay gumball/pachinko bonus segments headlessly — a bonus-entry bootstrap seam mirrors the live entry sequence (provider registration, ring restore, HUD, pachinko trap injection), skip-if-missing replay tests activate automatically once round-trip recordings land, and a ROM-backed smoke test proves both bonus zones boot on the level pipeline today. The recording procedure (with the corrected `giant_ring`-selector ring ranges) is documented in the BizHawk tooling README.
- **Multi-stage trace-run foundation (2026-07-19):** trace runs now bundle typed segments under a `run_manifest.json` (new `TraceRunManifest` schema + parser, per-segment run metadata, committed synthetic detour fixture), and the S3K complete-run BizHawk recorder (v6.30) gained a level-family mode guard — fixing silent segment pollution on special/bonus-stage detours — plus a stage-detour state machine that records bonus zones as `s3k_bonus_stage` segments and special-stage passages as merged `giant_ring` transition boundaries. Regenerating the AIZ segment from the committed movie stayed byte-identical; no trace frontiers moved.
- **S3K AIZ/HCZ/MGZ parity polish (2026-07-18):** AIZ waterfall and path-switch priorities, HCZ transition bubbles, MGZ2 collapse and end-boss sequencing, MGZ surprise Robotnik terrain occlusion, and level-transition music timing now follow the ROM more closely. The cross-game replay checkpoint retained its documented S1, S2, and S3K trace frontiers.
- **Merge verification (2026-07-18):** the S1, S2, and S3K replay suite was rerun before integrating this parity batch; its existing documented frontier failures and error remained unchanged.
- **Sonic 3 & Knuckles route coverage:** the Sonic/Tails path has completed AIZ through LBZ coverage, with ongoing work across bosses, events, objects, bonus stages, scroll/parallax, animated tiles, palette/PLC state, transitions, and rendering parity.
- **AIZ1 intro Tornado priority parity (2026-07-17):** Super Sonic and every Tornado aircraft piece now use the ROM's `$280` sprite bucket while the water splashes use `$100`, placing the propeller and rocket booster behind the waves as on original hardware.
- **AIZ trace parity closeout (2026-07-11):** both the focused level-select route and complete-run route now replay fully green through AIZ and the HCZ handoff. The campaign restored native object/RNG/SST cadence across the intro, traversal objects, act transition, battleship, miniboss and end boss, capsule/results control, Knuckles/button/drawbridge cutscene, mutable water flag, and CPU-Tails decision-time comparison while retaining green S1 and S2 trace fleets.
- **HCZ blocky waterfall/slide rendering fix (2026-07-14):** StillSprites (obj 0x2F) now live their ROM `Sprite_OnScreen_Test` lifetime instead of self-deleting on their first update, restoring HCZ's waterfall curtains, HCZ2's slide-crossing tube pieces, and decorative overlays across AIZ/MGZ/LBZ/MHZ/LRZ that previously vanished in sprite-sized blocks. HCZ2 additionally keeps Plane B a 512px VDP window through the whole act (following the wall BG camera during the chase like `DrawBGAsYouMove`, with ROM row-pointer overflow), and the BG high-priority replays now mirror the compositing pass's window base and plane-period wrap exactly. Verified against BizHawk reference frames; one shallow known knock-on (`s3k_hcz` complete-run f29096 scattered-ring re-collect) is recorded in `docs/status/trace-frontier-log.md`.
- **HCZ trace and rewind closeout (2026-07-13):** the complete Hydrocity route now replays green through its seamless act transition and the MGZ handoff. Turbo Spiker shells transfer lifetime ownership when launched, HCZ results/capsule/boss links restore through closed rewind graphs, CPU Tails consumes the ROM-recorded follower press byte, and the large-fan module queue retains its registered rewind-owned state across acts; all previously green S1, S2, and AIZ traces remain green.
- **Sonic 2 trace closeout:** the full S2 level-select trace suite now passes, including the late OOZ2, ARZ2, CNZ2, and MTZ3 boss/event frontiers and the S2 impatient-wait input gate.
- **Sonic 1 trace progress:** multiple complete-run frontiers advanced or turned green through ROM-order platform, camera, spring, ring, badnik, conveyor, seesaw, staircase, and collision fixes.
- **Sonic 1 bug batch (2026-07-05):** a 25-bug triage-and-fix pass closed 10 player-reported issues (special-stage jump input, egg-prison/lamppost/lavafall/glass-reflection visual lifetimes, Yadrin spike geometry, hurt-spring control recovery, bumper bounce direction, LZ1 door-gated current, and six rewind-state gaps spanning breath/conveyor/speed-shoes/invincibility-music/boss-spikeball state), confirmed 4 reports as genuine ROM behavior, and documented capture recipes for the 4 that need real-hardware evidence; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Sonic 1/2 bug batch wave 2 (2026-07-06):** play-testing follow-ups: starpost twirl now rests dead-centre (ROM 32-step terminal angle), lava geyser maker no longer flashes the prior cycle's ending frame, the dormant SYZ Roller is hidden exactly as ROM never displays it, the ROM-verified 62px glass-reflection shimmer is pinned by test, rewind is blocked while special/bonus-stage transitions are pending (S2 softlock fix), and boss child objects are re-adopted by identity with orphan reconciliation after rewind (EHZ boss desync fix); see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Rewind hardening wave 3 (2026-07-06):** boss children recreated by rewind now re-register with their parent through a single central mechanism (closing the EHZ wheel orphan gap and unifying DEZ/MTZ registration), trace-session rewind gained the same transition-freeze gate as live rewind, and rewind engagement is now blocked while any completion-bearing fade is in flight (closing silently-dropped or softlocked death/act/giant-ring transitions) while gameplay itself keeps ticking through those fades exactly as before; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Rewind relink hardening wave 4 (2026-07-06):** rewind recreate-time parent relinks are now bounded by per-object geometric radii (rejecting far same-class matches and dropping the child instead of silently adopting the wrong parent, e.g. a checkpoint twirl attaching to a different lamppost), with unbounded lookup kept only as a named opt-in where a parent legitimately roams; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Rewind and audio debt wave 5 (2026-07-06):** held rewind now clamps before committing to a keyframe captured mid-fade with an unrestorable completion callback (closing the scrub-through-fade softlock), the static-state rewind coverage guard now also audits per-GameModule services (four newly visible gaps baselined with justification), S1 SFX id 0xD0 dispatches through the ROM's special SFX pointer table, and the rewind round-trip probe report was regenerated; an empirical DEZ rewind test additionally uncovered (and documented for priority follow-up) an active child-state-loss bug for dynamically event-spawned bosses; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Dynamic-boss rewind reconstruction fix wave 6 (2026-07-06):** dynamically event-spawned bosses (SYZ3/GHZ bosses, S3K minibosses) no longer lose all child state on forced rewind reconstruction: phase-1 child adoption now parks unresolved entries and retries to a fixed point while parent reconstruction populates the scratch pool, and codec probe construction no longer leaks real wrongly-parented child objects; see `docs/architecture/plans/s1-bug-batch-ledger-2026-07-05.md`.
- **Bonus-stage rewind (2026-07-07):** held live rewind now works *within* the Sonic 3 & Knuckles Gumball and Pachinko bonus stages via a per-provider `supportsRewind()` capability, a widened rewindable-mode gate, a per-frame capture hook in the bonus-stage update, and a coordinator adapter that snapshots the ring/life/shield reward accumulators; the timeline is severed at the mode boundary in both directions. The Slot Machine bonus stage stays non-rewindable pending a dedicated runtime snapshot (planned alongside Sonic 1's Special Stage); see `docs/architecture/plans/2026-07-06-bonus-stage-rewind-gumball-pachinko.md`.
- **Special-stage rewind (2026-07-08):** held live rewind now works within Sonic 1 special stages through a provider capability gate, special-stage replay stepper, and Sonic 1 runtime snapshot adapter. Level entry/exit boundaries intentionally remain rewind timeline boundaries.
- **Gumball rewind capture hardening (2026-07-09):** S3K Gumball Machine capture now ignores stale removed dispenser/spring object references and rebuilds those live links after restore, preventing `RewindIdentityTable` crashes during bonus-stage rewind.
- **Test-suite and rewind determinism hardening (2026-07-09):** the full Maven suite is green again after capturing fixed-skid dust cadence in playable rewind state, rebuilding ARZ Obj83 child slots through identity-preserving reconstruction, correcting Sonic 2 blink/get-up donor mappings and delayed sidekick jump edges, tightening singleton test isolation, and restoring architecture/test-quality guard baselines.
- **S3K Blue Spheres visual parity (2026-07-09):** solo Tails now uses the ROM's player-2 palette line for his special-stage body and tail appendage instead of Sonic's palette line.
- **S2 special-stage trace capture & replay (2026-07-09):** a new BizHawk Lua recorder and PowerShell driver capture Sonic 2 half-pipe special-stage traces (48-column physics schema with BK2 input-alignment verification, real lag-frame flagging, and aux checkpoint/message/results events), with a committed 5299-frame MVP trace for Special Stage 1 (Sonic+Tails). A headless trace-paced replay harness (`TestS2SpecialStageTraceReplay` plus a determinism test) boots the production special-stage provider and diffs every frame against the recorded ROM run via the new read-only `Sonic2SpecialStageManager.captureComparisonState()` accessor — divergences are recorded to the trace report (ratchet intentionally disabled at the MVP frontier; see `docs/status/trace-frontier-log.md`). The trace catalog and test-mode picker gained special-stage-aware profiles/labels, a `GameLoop` special-stage skip-gate enables live lag-paced visual SS trace sessions in test mode, and special-stage team resolution now uses the standard two-key character config (making Tails actually spawn in team play); see `docs/architecture/designs/2026-07-09-s2-special-stage-trace-design.md`.
- **S2 Special Stage 1 trace-green closeout (2026-07-10):** ROM-ordered bootstrap, deferred `RunObjects`, controller sampling, object collisions, checkpoint/emerald completion, per-player rings, swap/timer state, and refresh-gated rings-to-go comparisons now replay 3,228 compared frames with 0 errors and 0 warnings. Every comparator is release-blocking, the scheduled trace profile explicitly keeps the run green, and normal play uses a rewind-safe deterministic lag model derived from the committed 5,299-frame recording. Normal entry now compresses the hidden ROM bootstrap, keeps the transition opaque until the full scene is ready, and starts music with the reveal; trace validation retains the exact startup cadence.
- **Rewind reliability:** gameplay rewind now covers more object graphs, level-trigger/static manager state, child recreation, object identity links, audio history boundaries, and route-specific boss/ride/carry state, with guards for future coverage gaps.
- **Controller support:** gamepad bindings now cover gameplay controls, live rewind, frame advance, debug movement, pause, title/credits confirmations, level-select menus, and launch-option navigation.
- **Player-facing systems:** ROM-backed SEGA boot screens, S3K data select and save/load support, cross-game donation, ROM-derived master-title previews, the legal-disclaimer startup flow, display shaders, pause/HUD fixes, multi-sidekick behavior, user recording/playback, and level-editor plumbing all moved forward.
- **Tails flight, swimming, and carry parity (2026-07-11):** manual S3K Tails flight/swimming now follows the ROM state machine across native and cross-game play, including donor-aware availability, main-character-only grabs, underwater exhaustion, shared CNZ/MGZ carry handling, and rewind-safe flight/carry state. The shared animation pass preserves the native flying and swimming body sprites selected by `Tails_Set_Flying_Animation`, matching CPU recovery presentation; swim body frames suppress the redundant Obj05 tail overlay, and idle swimming uses only the native `+$08` flight gravity instead of the non-flight underwater reduction. Native Sonic 1/2 and Sonic 2-donated play retain their original no-manual-flight behavior.
- **Rendering and performance:** live rewind gained the VHS picture-search effect, rewind tilemap rebuilds were reduced, display/title rendering was tightened, trace ghost rendering was added, and the PSG/audio implementation was cleaned up around the single Genesis Plus GX-derived core.
- **Project-wide performance pass (2026-07-10):** deterministic audio command processing now avoids more than 98% of its measured steady-state allocations, audio rewind history ownership halves the default retained PCM memory, and stereo FIR resampling halves tap-window traversal. Render and special-stage hot paths now reuse frame-owned state, visibility/SAT/overlay buffers, decoded/static geometry, pooled deferred commands, palette uploads, and page-aware virtual-pattern batches; GL teardown is recreatable and verified across repeated reinitialization. The banked series passed the full 11,405-test suite plus the green Sonic 2 special-stage trace and replay-determinism gates.
- **Performance follow-up (2026-07-11):** scrolling background tilemaps now upload only the two entering columns (8,192 B → 256 B for a normal window, 96.875% less), inactive trace rendering eliminates 16 captured callbacks per frame, sprite suppression resolves once per render pass, and warm rewind hits bypass keyframe/callback expansion setup. Repeated source-guard scans were deduplicated, cutting the focused guard median by 15.4%. The integrated batch passed 11,452 tests with zero failures and preserved render ordering, rewind rollback, audio, boundary, SAT, and Gumball determinism checks.
- **Rewind snapshot memory follow-up (2026-07-11):** composite key layouts are now shared across captures while each keyframe owns only its aligned value storage and immutable compatibility view. Same-layout restores use direct indexed access; historical layouts retain key-based resets, delayed game-RNG restoration, and callback ordering. Identical escaping probes reduced 24-subsystem capture allocation from 2,752 to 192 bytes (93.0% less), and the batch passed 11,460 tests with zero failures.
- **Special-stage/render-rewind performance continuation (2026-07-11):** Sonic 2 special-stage object/player draw ordering now uses stable grow-only renderer scratch, reducing a 32-object/8-player escaping allocation probe from 472 to 0 bytes while preserving equal-key order and reset/exception cleanup. Empty special-render and advanced-render rewind captures now return canonical immutable snapshots, reducing their combined legacy-equivalent capture from about 600 to 0 bytes. The two-task batch passed 11,478 tests with zero failures.
- **Animated-tile phase performance (2026-07-11):** installed animated-tile channels now keep live phase state in indexed primitive arrays while preserving public map snapshots, fresh callback contexts, cross-layout rewind restore, and callback-time install/clear behavior. A 32-channel escaping-context oracle improved from 1,792 to 1,280 allocated bytes per update (28.6% less), and the full 11,490-test suite passed with zero failures.
- **Performance-pass closure (2026-07-11):** animated-tile rewind captures now share immutable key layouts and own compact primitive payloads, reducing capture allocation from 3,288 to 320 bytes (90.3%) and the identity-aware 1,000-snapshot estimate from 2,378,048 to 356,728 bytes (85.0%). SMPS driver snapshots now hash a shared external fallback once per capture rather than once per SFX, reducing a 32-SFX fixture from 4.96 ms to 0.179 ms (96.4%) while still detecting source mutation between captures. Proposed empty-stage render gates were measured and rejected when their apparent benefit failed isolated reproduction, closing the pass without speculative branch churn. Identical opt-in baseline and optimized rewind runs retained the same pre-existing 120-frame long-tail frontier and first `object-manager.dynamicId` divergence; the 1,200-frame gate remains open and was not weakened. The full 11,504-test suite passed with zero failures.
- **Time-attack verifier benchmarks (2026-07-07):** two non-gating JUnit benchmarks measure the replay verifier's real cost against the trace corpus (per-run single-core cost, per-level retained footprint, and warm pooled-reuse behaviour), giving future verifier work a measured basis; the warm-reuse probe surfaced a headless palette-write accumulator leak in the shared `LevelFrameStep` frame step (the per-frame `PaletteOwnershipRegistry` drain was owned solely by `GameLoop`, so headless replay grew O(n^2) writes), now fixed by draining at the single-source frame step; see `docs/architecture/plans/2026-07-04-time-attack-phase5-verifier.md`.
- **Release hardening:** policy hooks, trace and rewind invariants, BizHawk/stable-retro trace tooling, object-service boundaries, ROM-only runtime asset rules, singleton lifecycle checks, architecture guards, test quality gates, and agent workflow docs were tightened for the prerelease line. Opt-in frame-level S1/S2 PLC and S3K Kosinski/KosM queue diagnostics now make load timing mismatches ordinary trace frontiers instead of ambiguous downstream symptoms.
- **Queue-aware agent workflow (2026-07-30):** every mirrored project skill now routes PLC/Kosinski queue and `dynamic_art` evidence to the trace/PLC owners. The operational skills document native `--load-queue-state` capture, capability checks, S1/S2 Nemesis and DPLC lifecycle reports, S3K direct-vs-KosM schema-2 timing, zero-tolerance comparison rules, and frontier-log accounting.
- **Rewind object regressions (2026-07-11):** live rewind now restores Sonic 2's Masher badnik to its exact captured fixed-point trajectory (subpixel phase, jump origin, velocities) instead of resuming from a partially-restored state, and the held live-rewind monitor presentation gained dedicated coverage for player identity, release decay, and held/replayable boundaries; see `docs/architecture/plans/2026-07-11-rewind-object-regressions.md`.
- **Rewind reference-closure hardening (2026-07-12):** Sonic 2 and Sonic 3 & Knuckles trace replay now validates every compact-captured object reference on each compared gameplay frame, with lifecycle and schema fixes for MCZ rotating platforms, MHZ Knuckles, ICZ freezer clouds, and AIZ intro glow state plus guard coverage that detects future identity gaps before manual play-testing.
- **Playable animation trace verification (2026-07-13):** normal-gameplay BizHawk traces now record Player and Sidekick animation IDs plus displayed mapping frames. Trace replay can gate all fields together or maintain independent physics and animation frontiers with `-Dtrace.verification=all|physics|animation`, so animation fixes cannot hide movement regressions.
- **Sonic 2 Wing Fortress Zone object & boss polish (2026-07-16):** a batch of ROM-verified WFZ fixes — the Tornado plays the scatter sound (not the ring-loss jingle) when it is gunned down; the belt platform (obj 0xBD) uses its ROM palette line; the hook-on-chain (obj 0x80) renders its chain/hook art with the ROM's `_Fudge` tile base; the rivet (obj 0xC2) plays its explosion sound and drops the player into the room below; the vertical propeller's helicopter sound is localized on-screen; the palette-switcher debug box respects the live debug overlay; and the WFZ boss (obj 0xC5) now draws its lens behind the cover with a laser beam that actually harms the player, plus rewind hardening for the boss child recreate paths. In the WFZ ending, Sonic now hangs correctly on Robotnik's getaway ship (the invisible grabber no longer suppresses the touch pass, so the breakable-plating grab fires) instead of showing the standing pose, and the getaway ship's foreground graphics are fixed — WFZ shares the SCZ tileset and overlays a pattern supplement (`ArtKos_WFZ`, like HTZ) that the engine previously loaded only for HTZ, so the ship (built from those supplement tiles) had rendered as garbage. A `dev.cmd` fast launcher (incremental compile + run from `target/classes` via a `dev-run` exec profile) was added for rapid iteration.
- **Sonic 2 Wing Fortress ending visual parity (2026-07-17):** the post-boss sequence now preserves the Mega Drive's history-dependent Plane-B nametable instead of rebuilding the whole background from current layout data. Incremental 64×32 ring updates, rewind capture, runtime PLC refreshes, interleaved foreground/background layout-RAM writes, and CPU/GPU wrapped sampling keep the sky blue until the late horizon reaches space without early black or wrapped space strips. The boss barriers flicker, the background ship retains its hull and alternating thruster flames, and the ending Tornado carries its ROM rocket pod and flame; headless trace-video gates cover the ship cadence, booster, sky, horizon, and first-black timing.
- **Shared BizHawk trace-recorder Lua module & Linux tooling (2026-07-23):** the six per-game BizHawk trace recorders now `loadfile` one shared `tools/bizhawk/lib/oggf_trace_common.lua` for their byte-identical leaf helpers (`bk2_input_mask`, `hex`, `angle_to_ground_mode`, `read_speed`, `rom_joypad_to_mask`, `write_aux`, `json_escape`/`json_quote`, `INPUT_*`) instead of copy-pasting them, with a launcher-provided loader and the schema writers/fast-headless block deliberately left inline. New Linux launch scripts (`run_bizhawk_lua.sh`, `record_trace.sh`) mirror the Windows `.bat` templates and run EmuHawk under mono. Linux-compat fixes: `client.invisibleemulation` (removed from the Lua API in BizHawk 2.11.1) is guarded like the neighbouring `client.SetSoundOn`, and `s3k_complete_run_recorder` was kept under Lua 5.4's 200-local cap (`luac5.4` verifies it; the `luac 5.5` in `$PATH` does not). Every recorder was regenerated against the repo-local BizHawk 2.11.1 Linux build and produced `physics.csv`/`aux_state.jsonl`/`metadata.json` byte-identical (SHA256-matched) to the pre-refactor `develop` recorder.

For details, see [`CHANGELOG.md`](CHANGELOG.md); for trace frontier movements and evidence, see [`docs/status/trace-frontier-log.md`](docs/status/trace-frontier-log.md); for the previous verbose v0.6 merge ledger, see [`docs/changelog/v0.6-prerelease-detailed.md`](docs/changelog/v0.6-prerelease-detailed.md).

### v0.5.20260411 (Released 2026-04-11)

A primarily architectural release. The engine internals have been restructured to prepare for level
editor support, safe gameplay-mode teardown, and multi-instance play-testing, while Sonic 3 & Knuckles
gameplay coverage has expanded across Angel Island and Hydrocity. AIZ2 now has the Flying Battery
bombing sequence, end boss, post-boss capsule/cutscene flow, and AIZ-to-HCZ transition represented,
while HCZ now has a larger object/event pass and HCZ1-to-HCZ2 progression.

- **Two-tier service architecture:** all 180+ game object classes migrated from direct singleton
  access to a two-tier dependency injection pattern (`GameServices` global facade + `ObjectServices`
  context-scoped injection). NoOp sentinels replace null checks throughout.
- **Gameplay session ownership:** this release introduced the first explicit gameplay-state
  ownership layer, later superseded by `SessionManager`, `WorldSession`, and
  `GameplayModeContext`. Enables safe editor mode enter/exit and level rebuilds.
- **LevelManager decomposition:** the engine's largest class is now a thin compatibility coordinator
  over focused collaborators including `LevelTilemapManager`, `LevelRenderer`,
  `LevelPlayableArtInitializer`, `LevelDirtyRegionDispatcher`, `LevelWaterCoordinator`,
  `LevelCheckpointCoordinator`, `LevelActTransitionExecutor`, `LevelTransitionCoordinator`,
  and `LevelDebugRenderer`.
- **MutableLevel:** snapshot, mutation, and dirty-region tracking for level tile data — the
  foundation for the upcoming level editor's undo/redo and real-time tile editing.
- **Common code extraction (5 phases):** 15+ abstract base classes, 10+ shared utilities, and
  systematic deduplication across all three games, including `SubpixelMotion`, `AnimationTimer`,
  `FboHelper`, `AbstractMonitorObjectInstance`, `AbstractSpikeObjectInstance`,
  `AbstractZoneScrollHandler`, and more.
- **Knuckles** is now a playable character with full glide/climb state machine, ROM-accurate
  jump height, wall grab, ledge climb, and sliding physics. Works in S3K natively and via
  cross-game donation into S1/S2 with correct palette and HUD from the lock-on ROM.
- **Sonic 3&K** expands with title screen (SEGA logo, Sonic morph animation, interactive menu),
  level select screen (SONICMILES background, zone icons, sound test), AIZ miniboss completion
  (defeat flow, napalm attack, staggered explosions), AIZ2 Flying Battery bombing/end-boss work,
  signpost and results screen, Blue Ball special stages (WIP) with per-character art/palette,
  S3K bonus-stage work across Gumball, Glowing Sphere/Pachinko, and Slots, per-character physics
  profiles, palette cycling for all zones, HCZ water rush / conveyor / fan / block / door /
  miniboss coverage, and many new badniks/objects including CollapsingBridge, MegaChopper,
  Poindexter, Blastoid, Buggernaut, Bubbler, TurboSpiker, and InvisibleHurtBlockH.
- **Insta-shield** fully implemented with ROM parity: activation, hitbox expansion, persistent
  lifecycle, cross-game donation, and DPLC cache management.
- **Multi-sidekick system** with configurable sidekick chains, per-character respawn strategies,
  virtual VRAM bank allocation, and VDP-accurate sprite priority ordering.
- **Tails AI rework:** ROM-accurate respawn gating, PANIC mode rewrite, flying/despawn
  improvements, P2 manual override, and per-zone boss/event wiring.
- **Cross-game donation** now bidirectional: S1 can donate into S2/S3K, with `DonorCapabilities`
  interface, `CanonicalAnimation` vocabulary, and `AnimationTranslator` for any game pair.
- **Rendering pipeline:** PatternAtlas slot reclamation, batched DPLC updates, virtual pattern ID
  validation, SAT sprite-mask replay ordering for mixed-priority S3K bonus-stage art, and
  fail-fast shader error handling.
- **Trace replay testing:** automated accuracy verification that records per-frame physics state
  from the real ROM, then replays the same inputs through the engine and compares every field.
  First trace (S1 GHZ1, 3,905 frames) passes with 0 errors; the latest GHZ bridge pass fixes
  the F2967 rider Y divergence by keeping Bri_Solid's final `Plat_NoXCheck` width and updating
  the rider bend log before sag calculation (`docs/s1disasm/_incObj/11 Bridge.asm:98-114`,
  `135-152`, `_incObj/sub PlatformObject.asm:19-42`, `58-76`, `_incObj/sub ExitPlatform.asm:8-23`).
  A second baseline (S1 MZ1, 7,936 frames) now passes after the Obj52 Moving Block
  jump-carry fix: S1 `MBlock_StandOn` clears Sonic's on-object status via
  `ExitPlatform`, then still moves the block and applies one final `MvSonicOnPtfm2`
  carry on the jump-off frame (`docs/s1disasm/_incObj/52 Moving Blocks.asm:65-83`,
  `_incObj/sub ExitPlatform.asm:5-24`, `_incObj/15 Swinging Platforms.asm:177-194`).
  Supports both BizHawk (Windows, Lua) and **stable-retro** (cross-platform,
  Python) as recording backends — both produce identical output consumed by the same Java test
  infrastructure.
- Comprehensive user guide, 15+ design specs and implementation plans, and broad test coverage
  improvements including automated singleton lifecycle testing.

See CHANGELOG.md for full details.

### v0.4.20260304 (Released 2026-03-04)

A release-sized update focused on expanding playable coverage, ending sequences, and engine maturity.

- **Package rename** from `uk.co.jamesj999.sonic` to `com.openggf` across the entire codebase.
- **Master title screen** implemented: engine-wide PNG-based title screen with animated clouds, game
  selection, and pixel font renderer. Displayed on startup before entering game-specific title flow.
- **Sonic 1** has moved from initial support to feature complete: title screen flow, special
  stages, major per-zone event scripting, extensive object and badnik additions, multiple boss
  implementations (GHZ, MZ, SYZ, LZ, SLZ, FZ), Labyrinth water/drowning/splash behaviour,
  ending/credits work, SBZ post-level-end sequence, demo playback, edge balance and push block
  collision corrections, and slope crest sensor guard. Expect minor bugs, but the game should be playable
  from beginning to end.
- **Sonic 2** adds title screen support, major object passes for MTZ/SCZ/WFZ/OOZ, 9 boss fights
  (MCZ, MTZ, WFZ, and both DEZ bosses — Mecha Sonic and Death Egg Robot, plus Robotnik escape),
  a complete credits and ending cutscene system with ROM-accurate visuals, expanded per-zone event
  architecture, demo playback, signpost/badnik palette/stair block art fixes, and a systematic
  TODO resolution pass with disassembly validation.
- **Sonic 3&K** sees major AIZ progress including intro cutscene systems, hollow tree and vine
  traversal parity work, miniboss object set bring-up, initial badnik implementations, shield/PLC
  integration fixes, a full water system with provider architecture and underwater palettes,
  seamless AIZ fire transition flow, and related regressions/tests.
- **Cross-game feature donation** implemented: a donor game (S2 or S3K) can provide player sprites,
  spindash dust, physics, palettes, and SFX while the base game handles levels, collision, objects,
  and music. Now includes cross-game Super Sonic delegation.
- **Per-game physics** and Super Sonic state/control flow (implemented for S2, with cross-game
  delegation to S1 and S2 game modules).
- **Profile-driven level loading:** declarative `LevelInitProfile` system with 13 ROM-aligned
  steps per game, replacing the monolithic `loadLevel()` path.
- **Testability refactor:** `GameContext`, `SharedLevel`, `HeadlessTestFixture` builder, and
  profile-driven test teardown. Test grouping by level and 8-JVM parallel execution.
- **Engine fixes:** solid object edge jitter fix, S1 slope crest sensor guard, jump-while-airborne
  guard, fade transition flash fix, results screen rendering fix, HTZ earthquake fixes, SFX
  channel replacement fix.
- PLC/art-loader refactors, RomOffsetFinder/ObjectDiscoveryTool enhancements, configuration
  documentation, and broad audio/stability/performance hardening.

See CHANGELOG.md for full details.

### v0.3.20260206

A massive release covering 366 commits across every major subsystem.

- **Tails** (Miles Prower) is now a playable character with ROM-accurate CPU AI follower behaviour,
  input replay, flight, and configurable sidekick toggle.
- **Multi-game architecture:** The engine has been refactored to support multiple games via a
  provider-based abstraction layer, with initial Sonic 1 ROM support (level select, title cards, HUD,
  audio with S1-specific SMPS driver configuration) alongside the existing Sonic 2 support.
- **Physics:** The physics engine has been completely rewritten to match ROM behaviour.
- **Bosses and objects:** Boss fights are implemented for 5 zones (EHZ, CPZ, HTZ, CNZ, ARZ), along
  with 15+ new badniks and 50+ new game objects spanning all implemented zones.
- **Water:** A full water system with drowning mechanics is in place for CPZ and ARZ.
- **Graphics:** The graphics backend has been migrated from JOGL to LWJGL with a GPU-accelerated
  rendering pipeline (pattern atlas, tilemap shader, instanced sprite batching, priority FBOs).
- **Audio:** Major accuracy improvements to YM2612 FM synthesis (based on Genesis-Plus-GX reference)
  and the SMPS driver.
- **Infrastructure:** Per-game ROM configuration, a HeadlessTestRunner for physics integration
  testing, visual and audio regression test suites, a multi-game test annotation framework, GraalVM
  native build support, and significant performance optimisations throughout.

See CHANGELOG.md for full details.

### v0.2.20260117

Improvements and fixes across the board. Special stages are now implemented, feature complete with a
few known issues. Physics have been improved, parallax backgrounds implemented and complete for EHZ,
CPZ, ARZ and MCZ. Some sound improvements, title cards, level outros, etc.

### v0.1.20260110

Now vaguely resembles the actual Sonic 2 game. Real collision and graphics data is loaded from the
Sonic 2 ROM and rendered on screen. The majority of the physics are in place, although it is far
from perfect. A system for loading game objects has been created, along with an implementation for
most of the objects and badniks in Emerald Hill Zone. Rings are implemented, life and score tracking
is implemented. SFX and music are implemented. Everything has room for improvement, but this now
resembles a playable game.

### v0.05 (2015-04-09)

Little more than a tech demo. Sonic is able to run and jump and collide with terrain in a reasonably
correct way. No graphics have yet been implemented so it's a moving white box on a black background.

### v0.01 (Pre-Alpha, first documented 2013-05-22)

A moving black box. This version will be complete when we have an unskinned box that can traverse
terrain in the same way Sonic would in the original game.
