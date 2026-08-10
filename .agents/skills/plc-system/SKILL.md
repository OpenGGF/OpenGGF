---
name: plc-system
description: Use when working with Pattern Load Cue systems across any game — runtime art loading, PLC table parsing, art decompression queuing.
---

# Cross-Game Pattern Load Cue (PLC) System

Reference for the shared PLC binary format used across S1, S2, and S3K, and the `PlcParser` utility.

## Agent Workflow Tooling

For S3K PLC/art intake, use the dedicated tool and runbook (prefer the S&K half; the tool flags `s3.asm`-sourced labels — the S3L standalone half — as a caution, since an object with no S&K equivalent may legitimately use them):

- **`RomArtIntakeTool`** — S3K ROM-backed art/mapping/PLC intake; wraps `RomOffsetFinder --game s3k`, recommends `StandaloneArtEntry` vs `LevelArtEntry`, and suggests `Sonic3kConstants` names + `Sonic3kPlcArtRegistry` hints. Accepts multiple labels:
  `mvn exec:java "-Dexec.mainClass=com.openggf.tools.RomArtIntakeTool" "-Dexec.args=ArtNem_AIZSwingVine Map_AIZSwingVine"`
- **Doc:** `docs/agent-workflow/runbooks/runbook-rom-art-mappings-plc.md` — end-to-end ROM art/mappings/PLC intake runbook.

## Shared Binary Format

All three Sonic games use an identical PLC table format:

### Offset Table
A list of 2-byte word offsets, one per PLC ID, relative to the table start.

### Per-PLC Data Block
- **Header word** (2 bytes): count-1 (for `dbf` loop). Negative (bit 15 set) = empty PLC.
- **Entries** (6 bytes each): `dc.l nemesis_rom_addr` (4 bytes), `dc.w vram_dest_bytes` (2 bytes)

VRAM destination stores `tile_index * 32`. To recover tile index: `vramDest / 32`.

## Per-Game Table Addresses

| Game | Constant | Address | Entry Count |
|------|----------|---------|-------------|
| S1 | `Sonic1Constants.ART_LOAD_CUES_ADDR` | `0x01DD86` | ~16 PLC IDs |
| S2 | `Sonic2Constants.ART_LOAD_CUES_ADDR` | `0x42660` | ~67 PLC IDs |
| S3K | `Sonic3kConstants.OFFS_PLC_ADDR` | `0x09238C` | 124 PLC IDs (0x00-0x7B) |

## PlcParser API

Located in `com.openggf.level.resources.PlcParser`.

### Records
- `PlcParser.PlcEntry(int romAddr, int tileIndex)` -- single PLC entry
- `PlcParser.PlcDefinition(int plcId, List<PlcEntry> entries)` -- parsed PLC with all entries

### Methods
```java
// Parse a PLC definition from any game's ROM
PlcParser.PlcDefinition parse(Rom rom, int tableAddr, int plcId)

// Convert entries to LoadOps for LevelResourcePlan (writes to level pattern buffer)
List<LoadOp> toPatternOps(PlcParser.PlcDefinition definition)

// --- Standalone decompression (no level buffer involvement) ---

// Decompress a single entry into standalone Pattern[] (no VRAM conflicts)
Pattern[] decompressEntry(Rom rom, PlcEntry entry)

// Batch decompress all entries into List<Pattern[]> (one array per entry)
List<Pattern[]> decompressAll(Rom rom, PlcDefinition definition)

// Decompress a single entry into raw bytes (for level buffer application)
byte[] decompressEntryRaw(Rom rom, PlcEntry entry)
```

### Standalone vs Level-Buffer Decompression

PLCs can be used in two ways:

| Mode | Method | Use Case |
|------|--------|----------|
| **Level buffer** | `toPatternOps()` / `decompressEntryRaw()` | Level init, act transitions — writes into shared level pattern buffer |
| **Standalone** | `decompressEntry()` / `decompressAll()` | Object/boss art — returns independent `Pattern[]` arrays |

**Why standalone matters:** On real hardware, boss PLCs intentionally overwrite existing VRAM tiles (e.g., boss fire art at 0x0482 overwrites spike/spring art at 0x0494). The ROM restores the overwritten art after the boss is defeated. Standalone decompression avoids this conflict entirely by keeping art in separate `Pattern[]` arrays, paired with mappings to create `ObjectSpriteSheet` instances.

**Pattern for standalone PLC art loading:**
```java
PlcDefinition plc = PlcParser.parse(rom, tableAddr, plcId);
List<Pattern[]> artArrays = PlcParser.decompressAll(rom, plc);

// Pair each entry's patterns with its mappings
ObjectSpriteSheet sheet = new ObjectSpriteSheet(
    artArrays.get(entryIndex),
    S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr),
    paletteIndex, 1);
```

## Per-Game Integration

### S1 (Level Init)
PLCs are parsed during level loading in `Sonic1.readPatternLoadCues()` via `PlcParser.parse()`. Both primary and secondary ArtLoadCues are loaded and passed to `Sonic1Level.loadPatterns()`.

### S3K (Level Init + Runtime)
- **Level load:** PLCs converted to `LoadOp` entries via `Sonic3kPlcLoader.toPatternOps()` -> `LevelResourcePlan`
- **Runtime:** Zone events call `Sonic3kPlcLoader.applyToLevel()` for act transitions and boss art
- **Pre-decompression:** `Sonic3kPlcLoader.preDecompress()` for hitch-free transitions (AIZ intro)
- See `s3k-plc-system` skill for S3K-specific PLC ID catalog and runtime patterns

### S2 (Level Init)
S2 ArtLoadCues are parsed via `Sonic2PlcLoader.java`, which uses `PlcParser.parse()` with `Sonic2Constants.ART_LOAD_CUES_ADDR`. Zone-specific PLCs are loaded during level init. S2 can also use `PlcParser.decompressEntry()` for standalone boss/object art loading.

## Key Files

| File | Purpose |
|------|---------|
| `level/resources/PlcParser.java` | Shared PLC format parser |
| `game/sonic2/Sonic2PlcLoader.java` | S2-specific PLC parsing via `PlcParser` |
| `game/sonic3k/Sonic3kPlcLoader.java` | S3K-specific PLC application, GPU refresh |
| `game/sonic1/Sonic1.java` | S1 PLC parsing via `PlcParser` |
| `game/sonic1/Sonic1Level.java` | S1 pattern loading from PLC entries |

## Frame-Level Queue Diagnostics

Trace fixtures that advertise `load_queue_state_per_frame` compare physical load
queues at `END_OF_LOGICAL_FRAME`. Treat these fields as ordinary zero-tolerance
frontier fields:

- `s1_nemesis_plc` and `s2_nemesis_plc` cover the native six-byte PLC queue;
- `s3k_kos_direct` covers only physical direct Kosinski entries;
- `s3k_kos_module` covers physical KosM parents, never their direct children.

Use `GameplayModeContext.captureQueueDiagnostics()` for engine-side state. Keep
diagnostics read-only: snapshots must never accept trace data or release timing
jobs. A ready-but-unclaimed S3K timing job is no longer physically queued and
must not appear busy. Ordered fingerprints are part of the exact comparison
contract. In schema version 1, `service_observations` is reserved and must be an
empty array. This is mandatory schema padding, not evidence that service did
not run. Do not infer a consumed sub-frame boundary from end-frame state;
diagnose service and retirement from membership, prepared, and remaining-work
transitions.

For S3K, do not conflate submission with preparation. A direct Kos descriptor
queued at `POST_OBJECTS` is busy but unprepared until `PRE_MAIN_LOOP` sets the
retail queue-count sign bit. A KosM parent is prepared when the low seven bits
of `Kos_modules_left` are nonzero; bit 7 means its direct child is in progress,
not that the parent was initialized.

When a trace first diverges under `queue.<kind>.*`, investigate the owning queue
lifecycle before changing downstream audio, objects, events, or physics. For
S1/S2 recorder work, remember that `RunPLC` overwrites the active queue source
with the Nemesis decoder cursor; preserve the original descriptor by observing
the lifecycle before preparation rather than guessing it from an end-frame
sample.

### An S1/S2 lag row carries the held iteration's `RunPLC`

On every ordinary row the level V-blank services its patterns and the loop
tail's `RunPLC` arms the next head, and both are visible in the same
end-of-frame sample: a completion row shows the *next* entry prepared with its
full pattern count, never `prepared=false`. The one exception is a row whose
iteration did not finish. `Level_MainLoop` re-arms `v_vblank_routine` at its top
(docs/s1disasm/sonic.asm:3000) and bumps `v_framecount` in the instruction after
`WaitForVBlank` returns (3001-3002), so a recorded row where `vblank_counter`
advanced but `gameplay_frame_counter` did not is `VBlank_Lag` (sonic.asm:709)
fired *inside* the previous row's iteration. `RunPLC` (3032) sits behind
`ExecuteObjects` (3010), `DeformLayers` (3025), `BuildSprites` (3028),
`ObjPosLoad` (3029) and `PaletteCycle` (3031) — all of the loop's cost — with
only `OscillateNumDo`, `SynchroAnimate` and `SignpostArtLoad` (3033-3035) after
it, so a held iteration has not reached `RunPLC` either and arms the next head
on the lag closure instead. Modelled as
`TraceExecutionModel.isIterationHeldIntoNextRow` plus
`PlcFrameLifecycleCoordinator.markRepresentedIterationDefersLoopTailPreparation`.

The shape is rare: across every audited fixture in the repo there is exactly one
such row (`s1/runs/s1-sonic-complete-withemeralds/ghz2_2` frame 107). Every
other `busy && !prepared` row is the `SignpostArtLoad` case, where the level
loop's tail submits *after* `RunPLC` so the work is only armed the next row.
This query finds both classes in any fixture:

```bash
python3 -c "
import gzip,json,sys
q={};lag={}
for line in gzip.open(sys.argv[1],'rt'):
    d=json.loads(line); f=d.get('frame')
    if f is None or f<0: continue
    if d.get('event')=='load_queue_state': q[f]=d
    elif d.get('event')=='lag_state': lag[f]=d.get('lagged')
print([(f,'lag-next' if lag.get(f+1) else 'loop-tail-submit')
       for f in sorted(q) if q[f]['busy'] and not q[f]['prepared']])" <dir>/aux_state.jsonl.gz
```

## Dynamic-Art Reports and Routing

Native audited fixture captures must use `--load-queue-state`. Confirm
`metadata.json` advertises `load_queue_state_per_frame`; DPLC/player-art audits
also require `dynamic_art_transfer_state_per_frame_v1`. The latter records each
frame's ordered submissions and completions, request descriptors, outstanding
transfer IDs, and the run-gap ledger carry (`dynamic_art_initial_ledger_*` and
`dynamic_art_gap_transitions` in schema 2 manifests).

Treat `queue.*` and `dynamic_art.*` report fields as zero-tolerance,
comparison-only evidence. They may diagnose production ROM work but may never
hydrate gameplay or create work.

### Run-gap edge field contracts (defined by the recorder, not inferred)

Both of these were got wrong by inference during the S1 emerald-route
special-stage-return work; read the recorder before reasoning about a
`run_gap.edge[N].*` mismatch.

- **`movie_logical_frame` is the physical BK2 movie row**, not a count of
  production iterations. `S1RunCaptureRunner`'s `rowsConsumed` counts every
  movie row from zero
  (`tools/bizhawk-headless/src/Recording/S1RunCaptureRunner.cs`:199-215) and
  reaches the observer through `PrepareDynamicArtCursor` (:212, :420-430) and
  `S1DynamicArtObserver.MarkAdvanceBoundary`
  (`.../S1DynamicArtObserver.cs`:108-128);
  `ReclassifyBoundaryCallbacksAsGap` (:476-508) then **overwrites** each
  boundary edge's frame with it at :483. Engine side, the driver states the row
  it already holds via `DynamicArtLifecycleService.setMovieLogicalFrame`
  (`src/main/java/com/openggf/game/resources/DynamicArtLifecycleService.java`
  :598-613); the self-counting fallback in `finishProductionIteration`
  (:807-815) loses a row for every suppressed production iteration, so a
  counter-derived value drifts further the longer the run.
- **`gap_edge_index` resets per logical frame, not per gap.**
  `S1DynamicArtObserver.PublishGap` keys its counter dictionary on the edge's
  `LogicalFrame` (:228-265, keying at :247-252), matching the in-segment
  `LogicalEdgeIndex` keying at :409-414. Two edges sharing a frame are 0 and 1,
  and a later frame in the same gap restarts at 0. A per-gap reset agrees only
  while every gap holds exactly one pair.

### The S1 load pair sits 26 rows before its segment start

Every S1 `run_gap` player-DPLC load pair is stamped at exactly
`segment bk2_frame_offset - 26`. The 26 is two counted ROM loops, not a fitted
number: `Level_Delay` runs 4 `WaitForVBlank` rows (`move.w #4-1,d1`,
docs/s1disasm/sonic.asm:2957-2963) and the `PalFadeIn_Alt` call after it
(sonic.asm:2966) runs 22 more (`move.w #22-1,d4`,
`docs/s1disasm/_inc/Palette Fading.asm`:32-51), with no wait between the fade
and `Level_MainLoop`. Modelled as `LevelInitProfile.preLevelMainLoopDelayFrames()`
(S1 override in `Sonic1LevelInitProfile`:70-82).

Verified across the whole `s1-sonic-complete-withemeralds` fixture — all 22 load
pairs, zero variance, GHZ/MZ/SYZ/LZ/SLZ/SBZ alike:

```bash
python3 -c "
import json,collections
m=json.load(open('src/test/resources/traces/s1/runs/s1-sonic-complete-withemeralds/run_manifest.json'))
f=collections.Counter(t['dynamic_art_gap_edge']['movie_logical_frame'] for t in m['dynamic_art_gap_transitions'])
o=sorted(s['bk2_frame_offset'] for s in m['segments'])
print([(x, min([q-x for q in o if q>=x], default=None)) for x in sorted(f)])"
```

Two SBZ frames answer 219/220 — earlier post-signpost tally-walk DPLCs, not load
pairs; those acts' own load pairs are still exactly 26. Frames past the last
segment offset belong to the terminal tail.

**Use the zone-invariance as a diagnostic.** GHZ, MZ, SYZ, LZ, SLZ and SBZ have
wildly different art payloads, PLC list sizes, and Nem/Kos decompression costs.
Elapsed hardware decode cost cannot produce a zone-invariant constant, so a
delta against this 26 is a flush-placement error in the engine, never
un-modelable load time. A 37-row divergence on this field was attributed to
hardware load cost for two rounds of work; the one-line query above refuted it.

**No S1 or S2 capture emits `hardware_timing.jsonl`.** `HardwareTimingEventEngine`
is constructed only by `S3KCompleteRunCaptureRunner` (:428) and
`S3KTraceCaptureRunner` (:297), and the file appears only in
`CommandLineOptions.S3kTraceOutputFileNames`, never in `TraceOutputFileNames` or
the S1/S2 run sink (`StagedRunSegmentSink`:47-49). Re-recording an S1 or S2 run
to obtain recorded timing is not a thing the recorder can do today — the
cross-game wording of hard rule 4 describes what the *contract permits*, not what
the recorder implements.

One narrow exception: recorded **hardware timing** may drive a **delay** in the
art-loading pipelines of all three games — S1 PLC, S2 DPLC, and S3K Kosinski
queues. It may defer or release *when* already-submitted, engine-created work
becomes ready, and nothing else. It must not carry gameplay values, create work
the engine did not submit, fabricate readiness, use physics/aux comparison data
as its signal, or key on a frame index, zone, route or game name. If a proposed
change decides *what* happens rather than *when*, it is outside the exception no
matter how well the ROM behaviour is cited. See `trace-replay-bug-fixing` for the
full contract; `TestHardwareTimingAuthorityGuard` enforces it. Preserve the first failing frame, field, and
error count in `docs/status/trace-frontier-log.md`. Use
`trace-replay-bug-fixing` for comparator triage and `trace-green-fleet` for
multi-trace frontier work.
