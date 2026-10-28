# Remaining TAS engine support — 2026-10-28

## Objective and base

The user requested a delegated team to support the twelve engine-scope scenarios
from the remaining seventeen-assessment audit. The pinned integration base is
`8945c0a789b27be57b2361c70f561f31872a76ca`, the main workspace's `develop` after
fetch/fast-forward. Six implementation streams cover shared mechanisms; a seventh
investigates the P2 intro's native cause before deciding implementation scope.
This plan records ownership and acceptance, not completed support.

Game algorithms, state, output and resource ownership are in scope. CPU/cycle,
video-beam and bus/register emulation are excluded. Three KosM adjacent-memory
overwrite assessments and exact native halfpipe hardware lag remain deliberately
unsupported. Native traces are comparison-only. The prior full reel keeps its
original tested-source identity.

## Streams and acceptance

| Stream | Historical assessments | Implementation and discriminating evidence |
| --- | --- | --- |
| Pause | `S1_CAPSULE_RNG:2`, `s2-pause-cycles:1`, `s2-capsule-rng:2`, `s3k-rng:1`, `s3k-ddz:2` | One semantic global tick per admitted paused frame, frozen gameplay/RNG, prepared PLC boundary, held/fresh Start and same-token controls, capsule consequences and restore-forward. |
| Reset | `s3k-reset:1`, `s3k-nolr:3` | Typed actual movie actions, explicit native warm/cold lifecycle, correct cursor admission through non-level menus, recreated world/art/audio and retained durable save semantics; no fabricated crash/RAM chain. |
| Competition | `s3k-competition:1`, `s3k-standalone:2` | Qualify existing host and original-S3 cartridge resource profile through menu, five native arenas, participant play/results/restart, real rendering and state replay. Locked-on success alone does not certify standalone S3. |
| Standalone SK | `s3k-standalone:1` | Verified original SK cartridge view, native title/selection/progress and Knuckles MHZ startup through real intro/event/camera owners. No S3 donor fallback or route-token-only proof. |
| DEZ art | `s2-dez-art-corruption:1` | Generic ordered logical art-slot replacement, both real PLC orders and actual pixels, overlap/non-overlap, reload and snapshot restoration. No Pig/DEZ condition fitted to observations. |
| ORT | `s3k-ort:1` | Independent placement/ORT cursors and real mid-pass allocation failure, duplicate occurrence identities, ordinary full-pool/unload/respawn controls and strict graph restore. No downstream KosM overwrite. |
| P2 intro | `s2-intro-p2-pause:1` | Diagnose original input/load/object-init cause with bounded synchronized native controls; implement only a demonstrated engine-semantic effect. No one-frame timer shortcut. |

The existing catalogue resolves original SK and S3 cartridge byte windows from
supplied combined ROMs. Workers independently identified their original hashes.
No new ROM file, copy or link is required. Actual cartridge resource/revision
bindings still need qualification; bytes being available does not establish mode
support.

## Owned shared seams

- Pause owns `LevelFrameStep`/clock admission. Reset owns only its narrow early
  dispatch, menu-frame completion and host-reset seams in `GameLoop`, plus
  reset-only special/bonus/ending visit retirement. No competing clock edits.
- Competition owns its package and module native-provider registration. Its
  `NativeTitleModeProvider` contextual overload receives an immutable explicit
  engine/root-module/input/publisher context; existing no-arg behavior stays
  compatible. Parent updates the two shared menu call sites.
- Standalone owns native cartridge/title/bootstrap/MHZ startup and generic
  `NativeMenuScreen.Exit` zone/act extension, retaining the old constructor.
  It changes only launch coordinates in `GameLoopNativeMenus`; parent composes
  module data-select gating, title Exit supplier and reset-policy registration.
- Competition may add an explicit `Sonic3kLevel.CollisionProfile` constructor
  for original-S3 competition assets. Existing constructors retain locked-on
  addresses. Standalone owns `Sonic3k` bootstrap sanity; no conflicting global
  scanner/constants relaxation.
- DEZ owns the optional immutable art-range recipes and ROM/zone identity in
  `PlcProgressSnapshot`, preserving every old constructor. This captures
  ROM-static animal/explosion sheet ownership; dynamic composites are excluded
  from that recipe claim.
- ORT owns narrow `ObjectManager` placement/admission/graph identity sections.
  Existing S3K-unused cursor snapshot carriers may hold native absolute ORT
  indices after consumer audit; strict IDs stay unique per occurrence.

`Sonic3kTitleState` is module/title-manager-owned. Ordinary title reset retains
selection, SK icon initialization normalizes it, and cold start initializes0.
Reset captures/applies native warm state without retaining a retired-world
reference. A dependency commit or parent composition supplies this API to the
reset stream; no temporary source stub is accepted.

## Validation and delivery

Workers run focused native/ordinary/replay checks through the Maven queue and
report tested source, counts, errors/skips, queue delay versus execution, and
route limitations. Parent owns change-based combined smoke planning against the
pinned base, required four S3K gates and affected structural/API checks. No
release-only full suite and no duplicated broad baseline/development runs.
Existing unrelated dirty submodules are preserved; native citation checks use
immutable source objects.

Each worker commits owned source/tests and a dated stream validation record with
hooks and exact model coauthors. Parent reconciles shared hunks, historical
classifications, release/discrepancy prose and coverage obligations, then merges
and pushes only `develop`. Worktree removal requires clean/accounted work and
reachable source history. Ownership/native task IDs and 24-hour expiry are held
in the external temporary-agent registry. Current task status belongs in that
existing registry/checkpoint rather than pass caches or archived Maven logs.
