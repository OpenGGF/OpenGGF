# Sonic CD as the fourth official game

Date: 2026-10-23. Status: proposed implementation architecture.
Implementation: [delivery plan](../plans/2026-10-23-sonic-cd-implementation.md).

This design targets the original Sega CD and Mega CD releases. USA is the
first supported release. Japan and Europe remain required for completion.
"Full parity" means matching the supported shipped releases, including their
bugs, timing, presentation, audio, progression, and persistent data behavior.
This document and the reference checkout do not implement Sonic CD.

## Problem

OpenGGF already separates game modules, semantic rules, level providers, and
session state. Its stock media path still assumes a cartridge. Sonic CD adds
disc files, program overlays, asynchronous hardware work, sampled audio,
CD audio, video, and persistent state across time periods. Adding a fourth
module without addressing those boundaries would spread disc assumptions
through shared gameplay and make USA defaults difficult to remove later.

The unit of support must become an identified game release in a verified
execution environment. Region and revision are separate dimensions. Neither
a menu label nor a selected soundtrack establishes a supported release.

## Evidence and limits

The engine grounding baseline is `b979916808b7f9379ede2b5dcdde1bcdc8bf8357`.
The user identified [James' ai-development branch](https://github.com/OpenGGF/scddisasm/tree/ai-development)
as the reference. OpenGGF pins
`1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff` at `docs/scddisasm`.
That commit was the branch tip when fetched on 2026-10-23.

The reference [README](../../scddisasm/README.md) reports regional byte-exact
reconstruction. Its [semantic review](../../scddisasm/docs/semantic-review.md)
explicitly separates byte equality from correct names, schemas, and calling
conventions. Some routines and packed data still need interpretation.
The upstream's historical comparisons are evidence from upstream, not fresh
OpenGGF parity tests. This task ran its structural audit, not retail rebuilds
or native gameplay comparisons.

The audit command is `python3 tools/audit_disassembly.py --check`, run inside
the pinned reference. It found no missing active includes or case mismatches.
Source inventory comes from the active include graph and consumers, not from
assuming every structured-looking file participates in the retail build.

All future constants and branch decisions need the owning label and source
location, plus verification against the matching user-supplied release.
Address identity includes the program image and load domain. A CPU address
alone does not identify a CD routine across overlays.

## Native contracts that drive the design

These observations were checked in the pinned reference. They require early
production probes rather than assumptions based on cartridge loading.

- `LoadFile`, `src/CD System Program/SP.asm:137-152`, starts file work,
  waits through VSync/status polling, and retries failed operations.
  `ReadSectors`, in the same file at line 505, owns sector read progression.
- `SPXStart` and `.WaitCommand`, `src/CD System Program/SPX.asm:298-335`,
  dispatch commands and restore interrupt ownership. The command table binds
  level, audio, menu, save, and presentation behavior.
- `MainLoop`, `src/Special Stage/Sub.asm:255-309`, starts stamp graphics,
  runs objects and updates the timer, then waits for graphics completion
  before handing Word RAM back. Gameplay and graphics readiness have
  different owners even though they execute in one native program.
- `SyncWithPCM`, `src/FMV/Sub (Opening).asm:220-237`, waits on the PCM
  playback address. The same file documents the shipped one-byte streaming
  gaps at lines 59-62. Fixing that behavior would change the target.
- `LoadTimeWarpData`, `src/Level/Load Saved Data.asm:7-43`, restores
  coordinates, velocities, player flags, rings, time, water, and camera state.
  A time-period change cannot be modeled as replacing scenery alone.

All paths above are relative to `docs/scddisasm`. These source locations are
for this pinned commit. Do not treat an address-derived name as proof of its
meaning without tracing callers and data flow.

## How the engine works today

### Stock launch and data ownership

`GameModuleRegistry.createStockModule` chooses S1, S2, or S3K.
`GameId` also contains standalone content. Standalone is a creator-content
category, not a fourth official game.
`RomIdentity` names logical cartridge windows, including lock-on components.
`RomIdentityTable` identifies known fingerprints but permits unknown
header-matching images. It does not select revision-specific behavior.

`GameDataSource` is the durable source retained by `WorldSession` across
runtime rebuilds. `RomDataSource` supplies an open ROM and rejects named
assets. Stock `GameModule.createGame(GameDataSource)` and its default touch
table path unwrap `rom()`. Disc support therefore needs explicit stock
bootstrap and module work. Passing an empty ROM into existing defaults is
not a CD implementation.

### Gameplay, modes, and rewind

`GameModule` supplies registries and providers. Shared gameplay consumes the
narrow records in `GameRules`. Zone behavior belongs to zone providers and
events. Object differences belong to object profiles or local implementations.
The [rule placement policy](../per-game-rule-placement.md) remains the owner
selection rule for CD.

`WorldSession` owns loaded-world identity and the durable data source.
Module `rewindAdapters()` registers session services. New time-travel,
disc-operation, audio, graphics, and persistent-save transaction state must
participate in the same ownership model. Managers own behavior. `Engine`
remains the composition and presentation shell.

The native stock route and OpenGGF extensions have different acceptance
contracts. Native Sonic solo at the original display and execution settings
establishes retail parity. Wider views, donors, teams, editor operations,
Stageplay, mods, and multiplayer require explicit support or rejection
contracts. They cannot change the native rules or count as retail evidence.

### Timing and audio

`HardwareTimingService` already separates preparation from readiness.
Its current `HardwareWorkKind` values describe cartridge decompression work.
`HardwareWorkSubmission` and its fingerprint contain cartridge source
addresses. Those records cannot represent disc work by inventing a ROM offset.

`LevelTimer` has 60-frame assumptions. `AudioFrameClock` accepts an integer
frame rate and keeps a remainder. Neither observation establishes a complete
PAL execution model. Native simulation, video refresh, CD sectors, chip
clocks, sample clocks, and host presentation must have explicit relationships.

The current sound stack provides SMPS, YM2612, PSG, presentation, capture,
and rewind infrastructure. CD PCM, CDDA, streaming, and video synchronization
need new owners and independent validation.

### Why these boundaries must survive

The [runtime ownership migration](2026-04-07-runtime-ownership-migration-design.md)
places the world and module in sessions so editor/context replacement does
not reset gameplay ownership. The [hardware timing contract](2026-07-27-cross-game-hardware-timing-trace-contract.md)
limits trace authority to matching prepared, production-submitted work.
These recorded constraints rule out a global CD singleton, wall-clock-driven
gameplay completion, and restoring gameplay values from reference traces.

The reference also proves that software region and hardware cadence cannot
be collapsed. `ObjSonic_Jump` and `ObjSonic_Float`,
`src/Special Stage/Objects/Sonic/Main.asm:197-255`, select different European
arithmetic at assembly time. `VInterrupt` and `VInt_Lag`,
`src/Level/Main.asm:681-697,737-750`, separately read the PAL console bit.
Those require release-bound behavior and a verified execution profile.

## Source inventory for the complete game

`src/_Include/System.inc` declares 70 retail stage file IDs. For each source
zone number below, Acts 1 and 2 have A, B, C, and D payloads. Act 3 has C and D.
A means Present, B Past, C Good Future, and D Bad Future. These are program
and data variants, not 70 independently certified routes.

| Zone | Source number | Payload families |
| --- | --- | --- |
| Palmtree Panic | R1 | R11, R12, R13 |
| Collision Chaos | R3 | R31, R32, R33 |
| Tidal Tempest | R4 | R41, R42, R43 |
| Quartz Quadrant | R5 | R51, R52, R53 |
| Wacky Workbench | R6 | R61, R62, R63 |
| Stardust Speedway | R7 | R71, R72, R73 |
| Metallic Madness | R8 | R81, R82, R83 |

The same pinned `System.inc` has 127 `FID_` declarations and 215 `SCMD_`
declarations. Reproduce those counts with
`rg -c '^[[:space:]]*FID_' src/_Include/System.inc` and the analogous
`SCMD_` selector. A file is not necessarily an executable program, and a
command is not necessarily asynchronous work. The catalogue must join these
identifiers with build outputs, actual disc contents, and runtime dispatch.
Neither list alone is the complete game inventory.

R2 is not a missing implementation slot. Derive this catalogue from the file
table, build graph, and real dispatch. Inspect demos and debug-only entries
separately from normal progression.

The inventory also includes the following programs and systems.

- Initial and system programs, IPX and SPX dispatch, Mega Drive initialization,
  file tables, sub-CPU commands, transfers, and hardware handoffs.
- Title, attract demos, native selection and continue flow, stage select,
  sound test, regional secret screens, staff times, and unlock conditions.
- All seven ordinary special stages, results and Time Stone progression.
  The source also has an eighth stage path. Establish its native secret
  entry and disposition instead of discarding it as unreachable.
- Time Attack menus, normal-stage and special-stage records, qualification,
  record persistence, replay or ghost features only where the retail source
  actually provides them, and every unlock dependent on those records.
- Time-warp cutscene, opening and both endings, credits and Thank You,
  Visual Mode, D.A. Garden, pencil test, and the Comin' Soon program.
- Backup RAM initialization, manager, sub-program, capacity/error handling,
  native record layout, and save/load/delete behavior.
- FM driver, all PCM banks and effects, disc tracks, pause/fade/seek/loop
  behavior, video streams, regional media, and synchronization.

Completion includes shipped secret/debug content reachable by native inputs.
Inactive source leftovers receive an evidence-backed disposition. The plan
does not promise to reproduce an entire CD BIOS user interface or unrelated
system software. Any BIOS behavior observable through Sonic CD remains in scope.

## Usage from the caller

The following calls describe proposed internal APIs. They are not current
OpenGGF methods or an expansion of the creator API.

```java
var release = stockGames.resolve(userMedia, requestedEnvironment);
var launch = stockGames.prepare(release, NativeLaunch.title());
var session = sessions.open(launch);
```

Resolution authenticates the whole required media set. Preparation binds one
module, one immutable data source, one release profile, and fresh mutable
services to that session. The normal caller does not coordinate disc reads,
overlays, audio commands, or timing queues.

```java
var location = CdStageKey.act(PALMTREE_PANIC, ACT_1, PRESENT);
var launch = stockGames.prepare(usaRelease, NativeLaunch.stage(location));
```

Direct stage entry is a development tool. It cannot satisfy an acceptance
gate that requires native title, save, warp, or special-stage entry.

```java
var checkpoint = rewind.capture(session);
driveInputUntilTransitionCompletes(session);
rewind.restore(session, checkpoint);
assertForwardReplayMatches(session, checkpoint);
```

Restore validates release identity and recreates in-flight work from captured
production descriptors. It never imports native comparison rows into gameplay.

## Shape

Use a normal `SonicCdGameModule` with a session-owned CD service implementation.
The shared engine keeps its player, object, mode, presentation, and rewind
ownership. CD services own the bounded hardware protocols used by the game.
A semantic scene coordinator requests native transitions and owns program/load
identity and the handoff payload. `GameLoop` remains the sole writer of the
active `GameMode`. Sonic CD's SP/IPX/SPX sequencing and command algorithms remain game code inside this
module. Pure disc, PCM/CDDA, and stamp-graphics devices expose only operations
and readbacks. There is no generic `MegaCdMachine` facade. The coordinator does
not replace the shared loop with another whole-game runner.

### Describe releases independently of media layout

The proposed data shape is deliberately internal. Concrete names can change
when CD-01/CD-02 establish the contracts.

```java
record ReleaseId(String value) { }
record RevisionId(String value) { }
record StockGameKey(String value) { }
enum Market { JAPAN, USA, EUROPE }
record ReleaseDescriptor(
	ReleaseId id, StockGameKey game, RevisionId revision,
	Set<Market> markets, MediaManifest media,
	List<ExecutionProfile> environments) { }
record ResolvedRelease(
	ReleaseDescriptor descriptor, VerifiedMedia media,
	ExecutionProfile environment) { }
```

`StockGameKey` is an internal release-catalogue key. Existing public `GameId`
values project into that catalogue with a consistency guard. Public
`GameId.SCD` joins stock admission in CD-07 with the affected candidate pins.
CD-03 can describe recognized CD media without changing cartridge factories.

A World cartridge has a market set. The selected console environment is a
separate validated value. A revision is the verified product revision, not
an inferred date or a region suffix. A release profile binds actual code/data
variants, timing, and presentation. Soundtrack, language, and video timing
are resolved properties, not alternative identities. This framework does not
certify every older S1/S2/S3K revision. Admit those later through their own
verified release bindings and affected checks.

`MediaManifest` is a sum of cartridge composition and disc track manifests.
Existing `RomIdentity` retains its meaning for lock-on windows. Disc content
identity includes TOC and required track payloads, independent of container
file names. Program-aware asset slices use a file/program identifier plus a
bounded byte range. They do not expose a virtual cartridge address space.

Execution identity also includes firmware semantics and the drive model.
The proposed runtime uses high-level reimplementation of the game-visible
BIOS services. It does not execute BIOS instructions or require BIOS bytes
at runtime. CD-02 must certify that choice against identified native firmware.
If it cannot, reopen the architecture before freezing these contracts.

```java
sealed interface FirmwareContract permits NoFirmware, SegaCdBiosHle { }
record NoFirmware() implements FirmwareContract { }
record SegaCdBiosHle(
	ContractVersion contract, Set<FirmwareFingerprint> certifiedReferences)
	implements FirmwareContract { }
record ExecutionProfile(
	HardwareRegion console, NativeTimingProfile clocks,
	FirmwareContract firmware, DriveTimingProfile drive) { }
```

Capture metadata identifies the actual BIOS hash. HLE snapshots and saves
identify the certified semantic contract. Firmware compatibility is verified,
not inferred from a disc's market. Any future instruction-level firmware path
needs verified user-supplied bytes through the media pipeline and a deliberate
architecture change.

### Keep one stock launch and one source owner

An internal stock resolver produces the existing session's module and durable
`GameDataSource` together with its immutable resolved release. Cartridge
sources continue serving `Rom`. A disc source supplies verified bounded
assets and transport capabilities. New stock launch uses the explicit-source
`WorldSession` constructor. Its legacy `MissingDataSource` path resolves the
current global ROM lazily and cannot authenticate a disc session. Stock CD admission replaces or overrides
ROM-only defaults where its actual source consumer requires it. First inventory the
API and all caller assumptions. CD-03 wraps current cartridge resolution with
release descriptors and preserves factories. CD-07 makes source-based launch
canonical only as needed by the first real disc consumer. Migrate affected
modules and callers together and update the candidate pins in that change.
Audit `data.Game.getRom()`, relocation/export methods, audio setup, hosted-run
checksums, and trace launch. The existing `getRom()` contract allows null for
standalone content. A disc path must never reach a shared caller that assumes
it is a cartridge, nor add a null/default stub merely to make launch compile.
`level.resources.LoadSource` also needs a bounded native-media alternative. Its current cartridge-address and creator-asset variants
cannot represent a stock disc file. No stock path routes CD through creator-owned standalone
content or a second world/session implementation.

Audit all stock launch, source, donor, tooling, and recording callers before
changing shared signatures. Prefer internal composition and existing providers.
Any necessary change to `@ModApi` types, including enum additions, follows the
candidate descriptor/version/signature policy. Do not add CD concepts to the
creator API merely because the implementation needs them internally.

### Derive providers from a complete program catalogue

One release-bound `CdProgramCatalogue` owns native file roles, executable
programs, scene entry/exit bindings, and command dispositions. Stock providers
and test inventories derive from that catalogue or have a consistency guard.
Do not grow a hand-maintained `Engine` switch for every disc file or secret.
The catalogue describes program behavior implemented by ordinary game owners.
It does not execute 68000 instructions or own a second gameplay loop.

Create a reviewed metadata inventory from source and actual media during
CD-01. Committed metadata can let ordinary builds and tests stay independent
of optional disassemblies. An explicit source-audit lane checks correspondence
with the pinned source. A missing reference may skip that optional audit, but
cannot satisfy a required parity acceptance row.

The intended host-mode mapping is below. These are proposed bindings to
existing engine modes, not claims that CD behavior already exists there.

| Native program family | Host mode and transition writer |
| --- | --- |
| Ordinary stages, native attract demos | `LEVEL` with native/demo input ownership |
| Title and native stage select | `TITLE_SCREEN` and `LEVEL_SELECT` |
| Special stages and their results | `SPECIAL_STAGE` and `SPECIAL_STAGE_RESULTS` |
| Native ending sequence | `ENDING_CUTSCENE`, with credits/Thank You program phases under its provider |
| Opening replay, warp presentation, Time Attack menus, D.A. Garden, Visual Mode, sound test/secrets, BRAM manager, Comin' Soon | One proposed `NATIVE_PROGRAM` host mode with a CD-owned program dispatcher |

`GameLoop` commits every mode change through the existing mode lifecycle.
The CD coordinator requests it and receives the committed boundary. A program
transition inside `NATIVE_PROGRAM` changes only its owned program state.
`MOD_SCENE` is not used to host stock programs. `GameMode` is `@ModApi`, so
adding `NATIVE_PROGRAM` is a deliberate CD-07 candidate API change, reviewed
with its consumers and pins. If the existing mode host can express these
programs without that new value, prefer it after proving the lifecycle.

### Give CD services state with a finite contract

The CD implementation owns disc transport, file/program transfer, Word
RAM ownership, stamp graphics preparation, PCM/CDDA transport, and native
backup RAM protocols. Expose typed operations at their owning boundary.
A private native-command dispatcher maps the complete command catalogue to
these semantic operations and native scene behavior. It records invalid or
inactive entries with evidence. It does not make every command a timing job.
Raw BIOS calls, command bytes, register layouts, and container details remain
inside the implementation. The conceptual signatures are:

```java
interface CdDiscTransport {
	CdReadHandle submit(CdFileRead request);
	PreparedDiscBytes consume(CdReadHandle ready);
}
interface CdStampGraphics {
	CdGraphicsHandle submit(StampRenderRequest request);
	PreparedStampImage consume(CdGraphicsHandle ready);
}
interface CdSceneCoordinator {
	void request(NativeSceneTransition transition);
	void advance(NativeServiceBoundary boundary);
}
```

A handle identifies one production-submitted operation. Consuming the result
requires preparation, native readiness, and matching ownership/generation.
The boundary adapter coordinates native clocks and operations once. It does
not expose arbitrary callbacks that can execute gameplay inside a timing job.
Special-stage sub-CPU object movement and timer updates remain gameplay.

Host workers can prepare immutable bytes or decoded output early. Only the
native deterministic service model makes results observable. A cache hit
must not complete a load earlier than the selected native environment.
Host slowness may stall presentation while preparation catches up. It cannot
silently add simulation ticks or change the native completion decision.

Every operation family needs an explicit source for live timing before its
implementation is admitted.

- Derive algorithmic costs and device completion from cited source, actual
  request data, and verified device/clock rules where possible.
- For environment-dependent drive/firmware latency, use a documented measured
  manifest under the existing load-time/timing policy. Key it by the verified
  firmware contract and drive profile. Validate independent inputs, cold/warm
  states, retries, and variability. Never fit a per-route or per-frame constant.
- If neither approach supplies a defensible live model, mark that family
  unresolved. Recorded readiness may diagnose or compare its eligible jobs.
  It cannot certify the playable game or close USA MVP parity.

CD-02 records this classification. CD-05 validates both its source and its
predictions. If most families remain unresolved, or the semantic model needs
repeated timing exceptions, revisit the program/device cycle model and the
capsule alternative before broad gameplay work. Do not conceal the gap with
instant loads or a fixture-fitted delay.

Extend the existing timing descriptor with a typed disc-origin alternative
only after the probe establishes the required identity. The design must keep
current cartridge fingerprints stable and update producer, consumer, and
guards together. Adding an enum kind alone does not implement recorded timing.

### Preserve native clocks and every mutable owner

`SPXStart`, `src/CD System Program/SPX.asm:307-308`, installs `RunPCMDriver`
on the sub-CPU level-3 timer interrupt. PCM sequencing therefore needs an
explicit native timer domain. It cannot tick once per video frame. Active
MMD headers also select native V-int/H-int regimes. The module owns their
program semantics while device clocks remain observable and rewindable.

Use rational time and integer accumulators for relationships between native
refresh, main/sub-system work, disc sectors, PCM, CDDA, and host output.
Profiles carry verified native rates. Preserve current cartridge cadence
while removing only assumptions that the measured CD/PAL contract contradicts.
Never change gameplay speed by changing a host sleep or an audio playback knob.

A session snapshot covers the scene coordinator, program/load identity,
transport queues and cursors, pending preparation/readiness/consumption,
clock remainders, Word RAM/graphics state, audio/stream phases, and native
backup RAM state. Existing gameplay owners capture gameplay. Immutable media
and caches are referenced by verified identity. They are not copied per frame.
Pending host workers are recreated or rejected by generation after restore.
Test retained memory and rewind cost before choosing buffer storage strategy.

Backup RAM has three explicit owners. The CD device/HLE service owns device
bytes, capacity, firmware file-system behavior, and status/errors. The CD save
provider owns the pure game-record codec and save requests derived from the
campaign's authoritative state. Host persistence owns atomic durable commits.
It does not decode game progression or mutate the virtual device during replay.
Save compatibility includes the firmware semantic contract, device format,
and verified game-payload compatibility, not just release identity.

Native persistent state and host file commits have separate lifecycles.
Restoring or replaying a snapshot cannot write the save file again. Region or
release replacement creates a new session and rejects incompatible snapshots.

### Represent stage identity and world history directly

```java
sealed interface CdStageKey permits CdTraversalStage, CdBossStage {
	static CdStageKey act(CdZone zone, TraversalAct act, CdPeriod period) {
		return new CdTraversalStage(zone, act, period);
	}
}
enum CdPeriod { PRESENT, PAST, GOOD_FUTURE, BAD_FUTURE }
enum FutureOutcome { GOOD, BAD }
record CdTraversalStage(CdZone zone, TraversalAct act, CdPeriod period)
	implements CdStageKey { }
record CdBossStage(CdZone zone, FutureOutcome future)
	implements CdStageKey { }
```

`TraversalAct` admits Acts 1 and 2. `CdBossStage` admits only future outcomes.
These shapes prevent a nonexistent Act 3 Past payload. Native file IDs and
source zone numbers stay in one release-bound catalogue. The authoritative CD
location supplies any legacy zone/act projection. Do not keep a second writable
copy in a scene manager. Shared engine code
continues consuming semantic level identity and providers.

Time travel has a captured state machine and a native handoff payload.
Per-act generator and projector flags, current period, good-future progress,
checkpoint/respawn state, and the global Time Stone set have separate owners
and lifetimes derived from source. Derive destination and boss-future choice
from that state. Do not synchronize several independently writable future flags.
Time Stones retain their native identity and progression semantics. Generalize
shared special-stage reward vocabulary if needed instead of persisting them
as Chaos Emeralds.

### Put code under its actual owner

| Proposed owner | Responsibility and existing seam |
| --- | --- |
| `data.media` | Immutable disc layout, bounded track/sector/file reads, content identity and provenance. No game or creator-content dependency. |
| `game.release` | Stock release resolution and execution/content bindings above logical cartridge identities. |
| `game.soniccd` | Stock module, level decoders, native registries, providers, rules and composition. |
| `game.soniccd.system` | Session-owned native program/command protocols, transition requests and hardware state. No player/object updates inside hardware jobs. |
| `game.soniccd` campaign/zone owners | Progression, period identity, warp carry, zone events and object-local behavior with one writer per state. |
| Existing `level.resources` and `game.timing` | Explicit typed native-media loading and readiness extensions, preserving cartridge descriptors and guards. |
| Existing audio and presentation owners | Shared mixer/capture/rewind lifecycle. CD implementations supply native PCM, CDDA, stream decoding and presentation. |
| Existing save and special-stage owners | Native backup payload and Time Stone semantics behind deliberate provider changes. |
| TraceChaser | Native capture and v5 publication. OpenGGF owns replay and comparison fixtures. |

This is a module map, not an instruction to create every package before the
first probe. Keep implementations package-private where possible. Add an
interface when another owner needs the capability, not for every native routine.

### Keep the public interface small

The normal stock caller resolves, prepares, and opens a session. It does not
learn CD service order. Game providers expose semantic capabilities. Only the
CD composition and owners see disc protocol types. That concentrates media
and native handoff complexity without adding a forwarding layer to each
shared gameplay call.

## Synthesis decision

The counsel used GPT-6-Astra, Claude Opus 5.5, and Claude Fable 5.1.
Each independently grounded the source and compared two structural shapes.
The integration lead is GPT-6.1-Sol. The user-selected panel overrides the
plugin's configured default runners. All reports were read before this
synthesis. No counsel member implemented engine code or claimed test execution.

Astra's normal module with session-owned services is the integration base.
Opus' direct-module argument narrows that proposal. SP/SPX algorithms belong
to the game, device implementations stay pure, and the sub-CPU timer remains
explicit. Fable's program/command catalogue becomes the completeness contract
and source for providers and acceptance inventories. The result keeps one
stock runtime and one writer for each state while accounting for non-level
programs and media operations.

The comparison used five criteria. The chosen composition preserves native
timing authority, avoids duplicate runtime ownership, keeps cartridge and
creator contracts bounded, represents verified releases/media honestly, and
has a finite parity inventory. A full-program capsule has a smaller external
API but hides a second gameplay lifecycle. A generic machine facade exposes
many devices without owning the game's command semantics. Provider sprawl
leaves sequencing to callers. Each loses on ownership or interface depth.

A fresh Opus cross-judge accepted the overall composition and returned
REVISE on timing evidence, firmware identity, phase dependencies, mode/BRAM
writers, and API sequencing. The lead addressed those findings in this design
and the plan. Stamp-device proof precedes title; later-zone demos wait for
those zones; Palmtree Panic closure joins the special-stage work. Firmware and
drive contracts are explicit, mode and BRAM writers are named, and factory
migration waits for the disc consumer. The final consistency checks cover the
revised phase graph and document contracts. There was no second review round
claiming that unimplemented native behavior passes.

The following counsel proposals were declined or constrained.

- Fable's blanket mapping of sub-CPU commands to readiness jobs is too broad.
  A blocking command or file load can belong to main-loop admission. Add a
  recorded kind only under the existing pending-work/polling contract.
- Opus' generic machine alternative is rejected. Its useful device and clock
  separation is retained inside the direct-module design.
- Fable's vertical-slice MVP label is rejected. The slice proves architecture.
  The user's USA MVP retains the complete USA retail feature scope.
- Neither a new trace schema nor a permissive fallback profile is adopted.
  V5 remains the sole live contract. New CD metadata/work representations
  require coordinated producer/consumer/guard changes within repository policy.
- Replacing existing unknown-cartridge acceptance is outside this task.
  Preserve that development behavior while keeping uncertified images out of
  parity claims. Unknown or mismatched discs cannot use a certified USA profile.
- The cross-judge's suggestion to certify unmodeled live behavior using only
  recorded readiness is rejected. It can narrow a diagnostic claim. The
  complete playable USA parity gate still requires a defensible live model.
- Market eligibility is a set, not a World hardware region. Do not force all
  possible disc/console pairings into a supported profile. Certify native
  environments first and reject or label other combinations from evidence.

Model the Domain shaped separate release/environment and traversal/boss types.
Exhaust the Design Space required comparing complete ownership alternatives.
Sequence Work into Verifiable Units made native-oracle and high-risk probes
precede broad zone production. Those choices apply the requested Poteto style
without adding approval ceremonies or recurring agent machinery.

## Tradeoffs accepted

- We accept early media/oracle work before broad zone output because source
  readability and cartridge-era capture support do not prove CD behavior.
- We accept an explicit native timer and finite device state because PCM,
  graphics, and memory handoffs remain observable between video frames.
- We accept focused changes to shared source/release/provider contracts where
  their cartridge assumptions are real. We avoid a parallel CD-only engine.
- We accept complete USA scope before regional certification. Japan/Europe
  bindings and PAL modeling remain explicit from the beginning.
- We accept a canonical CUE/BIN path before other input containers. Complete
  game parity requires all necessary content, not support for every container.
- We accept source metadata inventories and consistency checks because a
  finished level registry cannot prove that menus, secrets, and media exist.

## Alternatives considered

| Shape | Complexity hidden | Complexity left with callers | Decision |
| --- | --- | --- | --- |
| Direct module plus program catalogue and finite device services | Media, native program sequencing, clocks, handoffs and release binding | Semantic game operations and ordinary stock/session lifecycle | Chosen synthesis |
| Full native-program capsule with advance/capture/restore | Nearly all main/sub-program execution | Adapter must reconcile object, mode, frame and rewind ownership with the engine | Rejected pending contrary probe evidence |
| Generic Mega CD machine with pluggable sub-programs | Device register protocols and console state | Game still coordinates SPX, scene and frame admission through many device calls | Rejected as unnecessary generalization with split timing risk |
| Add unrelated providers and GameMode branches for each CD feature | Individual decoders/devices | Callers coordinate program loads, command order and completeness | Rejected for shallow interfaces and scattered ownership |

Reopen the chosen shape if independent warp, video, and graphics probes require
repeated bypasses of its ownership rules. Preserve their failing evidence and
redesign before later zones depend on the workaround.

## Open questions and evidence gates

These are implementation research gates. They do not block saving this plan.

1. Which exact retail disc pressings and revisions will the first release
   manifest certify? Record actual hashes and TOCs from user-owned media.
2. Can the pinned BizHawk core and headless recorder load those discs and
   observe sub-CPU, Word RAM, stamp graphics, PCM, CDDA, and drive readiness
   at stable service boundaries? Prove this before committing to an oracle.
3. Does the proposed BIOS HLE contract reproduce the identified firmware's
   game-visible behavior, including backup-RAM layout and errors? Which drive
   profile and timing evidence define live loads? Measure repeatability and
   name permissible variation instead of claiming identical physical drives.
4. Which regional differences are code, data, media, execution environment,
   or revision differences? Audit regional branches and binary consumers.
5. Can deterministic semantic services reproduce all observed handoffs
   without a general-purpose CPU interpreter? Test warp, video streaming,
   and stamp-rendered special stages early.

The absence of a disc or native observation is a missing prerequisite.
An upstream byte match is not an answer to a gameplay timing question.

## Next implementation step

Execute the plan's media and native-oracle probes, then freeze the smallest
internal source and timing contracts that those probes establish.
