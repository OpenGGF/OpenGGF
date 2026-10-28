# TAS Competition support assessment — 2026-10-28

## Scope and source

Assessments `s3k-competition:1` and `s3k-standalone:2` describe one feature:
**standalone Sonic 3 Competition**, including its title entry, menu, five native
tracks, independent humans, goal/results flow and replay. The worker owns the
competition package and precise competition tests. The parent owns shared title
contracts, classification, discrepancy/changelog reconciliation and combined
validation.

Initial source: `8945c0a789b27be57b2361c70f561f31872a76ca`, branch
`feature/ai-tas-support-competition-20261028`, isolated workspace
`/mnt/LinuxData/T3/worktrees/OpenGGF/t3-c9c8b8e5`. Hooks installed. Inherited dirty
`docs/s1disasm` and `docs/s2disasm` were preserved. The S3K disassembly was
initialized at its immutable pin `8b2cf3c4178f2382f844da67798de4befc50c5e0`.

## Base inspection and cartridge distinction

The base already contains the native miniature actors, terrain, five arena
bindings, menu composition, records, per-human state/cameras, session rules,
rendering and rewind. These owners are implemented for **locked-on S3&K**.
`Sonic3kGameModule` does not register `NativeTitleModeProvider`; manually opening
`S3kCompetitionNativeHost` does not itself make the product reachable from title.
The historical missing-host observation is therefore still relevant at this
source. It is not evidence that the entire competition engine is absent.

The supplied original `${S3K_ROM}` (the absolute, user-supplied `s3k.gen` path
passed to every Maven request) is 4 MiB,
CRC32 `63522553`, SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`.
There is no need for a separate standalone-S3 file: `RomImageCatalogue` and
`RomImageClassifier` expose its original upper cartridge as logical
`RomIdentity.S3`, an immutable 2 MiB window at physical offset `$200000`.
That original cartridge has CRC32 `9BC192CE` and SHA-1
`75E9C4705259D84112B3E697A6C00A0813D47D71`, matching `RomIdentityTable`.
No ROM was copied, linked, patched or downloaded.

Original S3 `Competition_Menu` (s3.asm:11589-11670) loads the S3 menu resources.
Locked-on `Competition_Menu` (sonic3k.asm:13480-13550) loads assets in the upper
cartridge, but executes a different program and uses its own tables. The current
asset tests independently pin the **locked-on routine operands** and complete
compressed asset hashes. Sharing some cartridge art does not establish identical
program behavior.

Runtime prerequisites identified at the base (resource/decode items are implemented
in the worker candidate below; product qualification is still pending):

- The competition runtime delegates game/level decoding to `Sonic3k`, whose
  constants and scanner are for the locked-on layout.
  `Sonic3kRomScanner.findSolidIndexes` requires the first S3 terrain pointers to
  be at least `$260000`; no such pointer can address the original 2 MiB S3 view.
- Competition resources still use locked-on program/table addresses: examples
  include miniature animation tables, item/goal scripts, menu copy/object lists,
  start and placement tables, palette pointers and scroll profiles. A verified
  standalone resource profile must replace those bindings together with level
  decoding. Subtracting `$200000` from every address is invalid: many actual
  owning routines/tables reside in the S&K half.
- A production title/menu composition needs explicit session publication,
  owned GPU resources, save-record persistence and teardown. It must compose
  with the separate standalone-startup work, rather than overwrite its native
  title service.
- Complete standalone input routes and applicable level obligations remain to
  be qualified. Locked-on start pictures and positioned goal crossings cannot
  certify the standalone Balloon Park height behavior or full five-track TAS.

No locked-on fallback, instruction simulation, beam/status polling, hardware
register/bus protocol or observed trace-row hydration is a remedy for these
prerequisites. No title action should advertise standalone Competition solely
because the existing host can be manually constructed with a locked-on ROM.

## Implementation checkpoint

Current local dependency head: `0ca89fa42`, merging the parent-approved audio
identity prerequisite `30fb4fae0d684b274df14f566c36275ff2c2e8b7`. Competition
implementation/tests are checkpointed while focused execution remains pending. The new
`getMusicId` reads original `LevelMusic_Playlist` at `$4680` and calls
`Sonic3kAudioResourceProfile.SONIC_3.musicId`; it no longer returns a placeholder.
Menu/selection entries request native music `$2D`, result entries request `$28`,
and retiring a visit stops its music without restarting the mode menu. Full
SMPS constructor binding belongs to the separate audio owner and is not implied
by these request IDs.


The parent subsequently approved original-S3 resource binding, a narrow explicit
`Sonic3kLevel.CollisionProfile` constructor and a competition-only
`S3CompetitionGame`; no global scanner/table changes. The contextual title
provider now composes a fresh host/view visit. `NativeMenuHostContext` carries
explicit engine/module/input/publication capabilities, and the default provider
overload preserves prior no-argument providers. The independent single-player
exit supplier defaults to `NONE`; parent owns final title-call-site composition.
Native record bytes use existing `SaveManager` ownership and separate cartridge
save keys. Original title presentation/audio remain separate prerequisites until
independently qualified.

## Validation checkpoint

One focused queued Maven request was submitted against the unchanged base using
Java 25, Lua 5.4, the absolute original locked-on ROM path and surfaceless EGL,
with both `DISPLAY` and `WAYLAND_DISPLAY` unset. It selects the existing native
host, session, screen, assets, arena/menu pixels, roster restoration and pause
tests. The receiving candidate passed 97 cases, zero failures/errors/skips. The queue
wait was 1,292 seconds; Maven build/execution was 1 minute 41 seconds. Exact
selection: `TestS3kCompetitionNativeHost,TestS3kCompetitionSession,TestS3kCompetitionScreen,TestS3kCompetitionAssets,TestS3kCompetitionArenaPixels,TestS3kCompetitionMenuPixels,TestS3kCompetitionRosterRestore,TestS3kCompetitionPause`. This run took place
after the resource/profile port was written, so it is receiving evidence, not
an untouched-base baseline. An explicit-profile validation-placement refinement
was made during compilation; the final profile/product check will qualify the
settled source. No runtime clock/physics algorithms changed in that refinement.

A separate focused `TestS3CompetitionProduct` request qualifies original-S3 and
locked-on visits independently, including controller-driven GP traversal. It
is pending; no original-S3 gameplay success is asserted yet. An initial
build-only compile exposed an ambiguous overload with a pre-existing nullable
`TerrainArtSources` argument. Prefixing the new explicit `CollisionProfile`
argument removed that source ambiguity without changing old constructors. A
second build-only preparation was cancelled before worktree-lock admission
because the receiving test request already owned identical preparation.

The parent owns broad smoke and the four mandatory S3K gates. No release suite,
native desktop probe, main-workspace operation, push or integration is performed
by this worker.

## Route-obligation ledger

This competition work uses canonical raw arena IDs `$0E`..`$12`, act zero.
Act-one entries are preview/resource aliases, not five extra races. The parent
will link the final supported scope from the central coverage/classification
records. The following ledger is a qualification plan until each row has an
executed result; file presence alone does not discharge an obligation.

| Native arena | ENTRY / actual countdown / P1-P2 controls | PRESENT / both cameras / destructive REWIND | ROUTE / goal / outgoing edge |
| --- | --- | --- | --- |
| Azure Lake `$0E00` | Product check, original and locked-on | Product check; inherited locked-on pixel controls | Controller GP candidate, pending |
| Balloon Park `$0F00` | Product check, original and locked-on | Product check, original and locked-on | Controller GP candidate, pending |
| Desert Palace `$1000` | Product check; Knuckles P1 / Tails P2 | Product check, original and locked-on | Controller GP candidate, pending |
| Chrome Gadget `$1100` | Product check, original and locked-on | Product check, original and locked-on | Controller GP candidate, pending |
| Endless Mine `$1200` | Product check, original and locked-on | Product check, original and locked-on | Controller GP candidate, pending |

Three native events are Grand Prix, Match Race and one-human Time Attack.
Existing receiving checks cover exact lap boundaries, false starts, finish waits,
win/tie rules, Grand Prix continuation, pause/back and record insertion. That
component evidence is distinguished from original-cartridge complete routes.
The product route uses ordinary controller inputs and actual world mechanics;
no goal crossing, lap count or progress value is assigned from a trace or probe.
Only engine-owned snapshots are restored in the destructive replay control.

Inherited/unqualified breadth: all production viewport presets, every character
route/duplicate pairing on original S3, complete Match Race and Time Attack
routes, independent original-S3 native pixels/audio, and title presentation.
The existing miniature mode has one/two human slots rather than campaign CPU
followers; Time Attack has exactly P1. No donor-movement or arbitrary team
breadth is inferred from campaign settings. A native-width receiving pass does
not certify those omitted obligations or the full publication movie.

Preparation checkpoint: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off test-compile -B`
completed successfully in Maven wall time 1m32s. This verifies compilation only,
including the contextual API, original resource loader, native music requests and
new product/contract tests. A later route-driver-only refinement suppresses
repeated airborne jump presses so this ordinary route does not activate Tails'
flight; the queued product lifecycle will compile that final test input.
