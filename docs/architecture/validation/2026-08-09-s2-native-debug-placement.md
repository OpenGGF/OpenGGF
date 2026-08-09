# Sonic 2 Native Debug Placement Contract Validation

**Date:** 2026-08-09
**Branch:** `feature/ai-s2-native-debug-placement`
**Base:** `e2aa50cd5980efc720f70c1c2a6209b2637b3042` (`origin/develop` at execution)

## Outcome

Native Sonic 2 level debug placement remains unavailable, but the deferral is
now exact and reproducible rather than CPZ-centric. A test-only ROM decoder
pins the World REV01 catalog, shipped conditional rows, and current object
factory gap. The reviewed design assigns the future preview, spawn,
controller, global-gate, rewind, and trace owners and forbids partial production
scaffolding.

No `src/main` file or runtime behavior changed. Engine free-fly remains a
separate supported tool, CPZ retains its free-fly entry guard, and
`DebugModeProvider.hasLevelDebug()` remains false.

## Review record

The design at
`docs/architecture/designs/2026-08-09-s2-native-debug-placement.md` initially
split production preview and placement-lifecycle work ahead of the controller.
Review rejected that sequencing because those APIs would be dormant
scaffolding. The amended green design makes all pre-activation slices
test/research-only and lands every production component with the coherent
controller/activation route.

The executable plan at
`docs/architecture/plans/2026-08-09-s2-native-debug-placement.md` was separately
reviewed green. It scopes this branch to the ROM contract ratchet and current
documentation only.

## Source and ROM authority

- ROM: Sonic 2 World REV01
- SHA-1: `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`
- build conditionals: `gameRevision = 1`, `fixBugs = 0`
- entry: `docs/s2disasm/s2.asm:36224-36230,38164-38170,38255-38261`
- controller: `s2.asm:88453-88740`
- table/macros/rows: `s2.asm:88742-89079`
- globals: `docs/s2disasm/s2.constants.asm:1666-1671,1896`

The native `DebugObjectLists` table is at ROM address `$41D0C`. Its raw 17-word
offset table is:

```text
0034 0022 0022 0022 00CE 00CE 01E0 02E2 03DC
0022 03DC 04E6 05A8 066A 0022 072C 0816
```

The decoded result is:

| Measure | Result |
|---|---:|
| Zone-table slots | 17 |
| Zone-expanded rows | 340 |
| Rows across distinct list definitions | 265 |
| Unique object IDs | 117 |
| IDs with current registry factories | 113 |

HPZ and OOZ alias the same 33-row definition. The five undefined/unused zone
slots and DEZ use the two-row Ring/Monitor default. The shipped EHZ waterfall
subtype `$00` preview uses blank frame `$00`, and the shipped CNZ list retains
the parent-dependent Obj `$D3` row; both are `fixBugs = 0` behavior.

The four catalog IDs without registry factories are:

| ID | Native row / current owner | Remaining work |
|---:|---|---|
| `$25` | Ring / `RingManager` | Add a placement-safe dynamic stage-ring route with collection and rewind semantics. |
| `$46` | OOZ Ball, unused beta leftover | Deliberately port its shipped debug-reachable routine or resolve the product/revision policy; do not substitute Obj `$45`'s child. |
| `$73` | Rotating Rings | Port and construction-test the catalog subtype/lifecycle. |
| `$D3` | CNZ Bomb Prize | Model the shipped standalone debug allocation despite its ordinary parent dependency, including its resulting `fixBugs = 0` behavior. |

The 113 factories are an inventory, not proof that their catalog subtypes can
all be directly constructed. Zone-aware and parent/child implementations still
need row-by-row lifecycle probes.

## Architectural findings

### Preview

The ROM preview loads each row's mappings pointer, mapping frame, and VDP
`art_tile` into the player object. OpenGGF's S2 object-art path instead assigns
decompressed named sheets to virtual atlas IDs; it does not preserve the
catalog's VDP destination as the render key. Directly rendering
`art_tile + mappingTile` would address the wrong pattern space.

The next evidence action is a test/research-only row matrix that joins existing
PLC definitions, their ROM sources/destinations, the art registry/provider,
and each catalog `(mappings, frame, art_tile)` tuple. It must classify all 265
distinct-list rows without adding production PLC metadata or a dormant preview
resolver.

### Lifecycle and global state

Native allocation is forward first-free and transfers centre coordinates,
object ID, subtype, render/status state, and `no_balancing` behavior. The next
lifecycle matrix must exercise every row through existing ring, registry,
object-manager, and rewind harnesses and distinguish factory presence from a
placement-safe construction.

More than thirty compiled REV01 behavioral reads of
`Debug_placement_mode` remain outside the controller/reset paths. They cover
player, collision, camera/scroll, event, tube, oil, vine, launcher, checkpoint,
signpost, and zone-object boundaries. Full activation needs a distinct
module-owned native state and source-backed ownership for every gate; it must
not redefine `isDebugMode()` or add a CPZ/zone carve-out.

## Verification

Maven reported Java 21.0.11 from `/usr/lib/jvm/java-21-openjdk`.

Focused ROM contract:

```text
mvn -Dmse=off \
  -Dsonic2.rom.path=".../Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dtest=com.openggf.game.sonic2.debug.TestSonic2DebugPlacementRomContract test
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The build's hook-install step logged that the shared main-workspace
`.git/config` was read-only in the sandbox, but Maven continued and the test
compiled and ran successfully. Repository policy is run separately on the
staged diff before commit.

Adjacent boundary/lifecycle sweep:

```text
mvn -Dmse=off \
  -Dsonic2.rom.path=".../Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dtest=com.openggf.game.sonic2.debug.TestSonic2DebugPlacementRomContract,com.openggf.game.sonic2.TestSonic2SpecialStageModuleGraph,com.openggf.game.sonic2.objects.TestOOZPlacedObjectGaps \
  test
Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Selected architecture and rewind guards:

```text
mvn -Dmse=off \
  -Dsonic2.rom.path=".../Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dtest=com.openggf.game.TestProductionSingletonClosureGuard,com.openggf.game.rewind.coverage.TestRewindCoverageGuard \
  test
Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The rewind guard produced no tracked or untracked report in the worktree.
`git diff --cached --check` and `.githooks/run-policy pre-commit` both exited
zero on the complete staged candidate. Before commit, worktree status contained
only the eight intended staged files and no unstaged or untracked change.

## Evidence not obtained

No dedicated native-debug BK2 or end-to-end OpenGGF placement replay exists.
Ordinary S2 fixtures leave `$FE08` (`Debug_placement_mode`) at zero; historical
recorders also misused that address as a frame counter. Those fixtures cannot
prove entry, preview, selection, allocation, global gates, or exit.

The exact future capture action is to record a controller-authored REV01 BK2
that enables the retail debug cheat, enters from player 1 with B, cycles in both
directions, places a ring and a normal object, exercises a full-slot allocation
failure, and exits. A second movie must cover representative CPZ and SCZ global
gates. The observer may compare native state and slots but must not write memory
or hydrate OpenGGF gameplay.

## Disposition

This branch materially narrows and safeguards an unfinished feature; it does
not claim implementation. The next two permissible changes are test/research
readiness probes and reference capture. Production catalog, preview, ring/spawn
APIs, controller, gates, rewind, replay, and `hasLevelDebug()` activation land
together only when every reviewed design gate is satisfied.
