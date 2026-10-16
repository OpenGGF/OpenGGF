# Kosinski module queue and results contracts


`Queue_Kos_Module` / `Process_Kos_Module_Queue` is a separate, gameplay-visible
scheduler. Do not replace `Kos_modules_left` polling with a guessed frame count,
and do not treat already-decompressed Java renderer art as proof that the native
readiness gate has opened.

- The FIFO has four six-byte entries: archive address (long) plus VRAM byte
  destination (word). Only the first, empty-queue enqueue reads and initializes
  its header immediately. Later enqueues must retain only those raw fields: do
  not read, validate, or derive header/module metadata until the entry shifts
  into slot zero. A malformed later header therefore enters the FIFO and fails
  only at that shift-time initialization boundary.
- Init consumes the big-endian size header, maps `$A000` to `$8000`, stores the
  active payload source as `archive+2`, derives `$800`-word modules, and stores
  the exact last-module word count. Reject invalid zero/one-byte headers rather
  than inventing a module.
- A normal module needs a start dispatch and a later DMA-completion dispatch.
  Completing an archive shifts the FIFO and initializes the next header, but
  cannot start that archive in the same call.
- After decompression from source `S` ends at `E`, the next source is
  `E + ((S-E) & $F)`: alignment preserves the payload source's low-nibble
  residue; it is not absolute 16-byte alignment.
- DMA destination advances by `$1000` bytes for a full module and by
  `Kos_last_module_size*2` for the final module. Use the header-derived word
  count (including its odd-byte truncation), not measured Java output length.
- Gameplay rewind must snapshot FIFO order, initialized/uninitialized entries,
  active payload source, evolving destination, modules-left raw phase/bit 7,
  last-module words, and the in-progress decompressor end. Recreated objects
  must use side-effect-free shells so queue restore is not followed by duplicate
  enqueues.

### Entry readiness and later native submissions

**An idle entry FIFO is not permanent art quiescence.** An omitted title-card
presentation can leave its native owner alive. It may later submit LoadEnemyArt
during ordinary player updates even though the physical queues were empty at
the local entry boundary. HCZ31538 reached supported, locally art-ready entry,
then stopped on a fixture's continuing global-idle guard when the genuine enemy
batch arrived. Attribute actual handles, owner epoch and descriptors; preserve
local art and control checks. Do not force service or pause the input policy to
make the queue idle. This distinction does not remove explicit ROM owner waits:
results creation still polls global Kos_modules_left as described below.

**A selected module can be an engine-owned JDK proxy.** Its class CodeSource
may be null even when the invocation handler belongs to the verified engine.
HCZ43267's observer dereferenced that location before logging its class and
failed at setup, with no pad rows. A trusted host diagnostic can use the
existing pure `NativeRewindAdapterPublication.nativeRoot` lineage contract;
preserve outer world identity and stop on rejected or unknown ancestry. Read
the initialized native provider field, not a lazy art getter. Adapter
reserve/register operations are mutations and are not observation substitutes.

### Results-screen false-green checklist

`Obj_LevelResults` queues General -> `$520`, apparent-act Num1/Num2 -> `$568`,
and the selected character name -> `$578` or `$5A0`. `Obj_LevelResultsCreate`
returns without advancing its 360-frame wait while modules remain. It then
allocates twelve real `ObjArray_LevResults` SSTs: failure of the initial
`AllocateObjectAfterCurrent` retries without publication; failure of a later
`CreateNewSprite4` publishes the allocated prefix but leaves the parent's native
child count at twelve, so Wait2 retains the missing suffix. Do not heal it.
Children retire from the prior `Render_Sprites` bit-7 result using their own
width and native 320-pixel screen-space bounds; crossing an edge deletes on the
next object dispatch. Embedded arrays, fixed 9-frame gates, fixed offscreen
thresholds, and synthetic post-results retirement timers are false greens.

Focused validation:

```bash
mvn "-Dtest=TestKosinskiModuleQueue,TestKosinskiModuleQueueGameplayIntegration" test "-Ds3k.rom.path=$S3K_ROM_PATH"
mvn "-Dtest=TestS3kResultsKosQueueAndChildren,TestS3kResultsElementObjectInstance" test "-Ds3k.rom.path=$S3K_ROM_PATH"
```

**Resident patterns can change without changing identity.** KosM DMA updates
`Sonic3kLevel` patterns in place. A sprite sheet decoded from resident VRAM
must register its native source tile range with the object-art provider so
`refreshAffectedRenderers` republishes its virtual atlas. A ready renderer and
nonzero CPU patterns do not prove GPU pixels are current. The S3K ending
Tornado at input600 had6845 nonzero pixels and a ready virtual base137948
while remaining invisible: an identity-only cache missed the upload and the
sheet lacked `levelArtTileRanges`. Use the existing range refresh owner, not
a hash scan, extra art job or fitted delay (2026-10-13 ending lifecycle task).


### Paired physical/timed module publication

The native module tail polls the complete direct FIFO, not merely a renderer or
a parent handle. `S3kKosModuleQueue` emits its actual direct-child start/claim
receipts; `S3kPatternModuleTransfer` binds them to an already admitted physical
FIFO entry and retains them after the parent claim. The physical completion
queues immutable ROM bytes for the next supported VInt DMA drain. Repeated
rendering and completed parent claims do not upload them. Explicit standalone
queues retain their separate declared contract.

Capture admitted FIFO position as well as the actual submitted handle and
fingerprint. Identical source/destination predecessors cannot safely be matched
by descriptor alone, and copied snapshots cannot preserve Java entry identity.
Shift positions only when the physical archive actually retires; a module
advance, failed admission or clear must not invent a shift. Snapshot pending
DMA independently from presented pattern writes, so both restore directions
retain publication. Real duplicate-predecessor, multi-module, full-capacity,
claim-race and320/800 full-world pixel tests cover these boundaries.

A global producer without a sprite-owned handle still needs one actual timed
job and a captured automatic claim. Preflight logical and physical capacity
before mutation, and bind the resident DMA target. Timing-only terrain/title/
fire-overlay jobs that own no physical journal entry must not be guessed into
this pairing. Origin: 2026-10-14 ending Master publication, base69e4f51.


**Decoded frontend sheets are not physical pattern residency.** A frontend can
render a virtual sheet correctly while the following level decoder replaces
its unwritten native pattern gaps with zero. S3K's initial Master span at
VRAM $A5C0 was exactly 2,176 bytes of title-banner ROM $35026C+$5C0, not new
Master art. Retain only admitted frontend CPU uploads at their literal native
destinations, seeded from the current stock Level's bounded physical patterns;
never copy virtual atlases or comparison VRAM. Overlay writes of zero still
overwrite retained bytes, and retained extent must not move the append cursor.

Freeze immutable bytes and resource-owner generation before an async build;
validate that owner is still installed before GPU publication. Equal bytes and
generation in a detached owner are insufficient. A menu's world replacement
may transfer these bytes only through a capture-created token binding the
exact reused data source and supported owner type. Consume the seed at real
resource installation, preserve it over ordinary title reset, and clear it on
a fresh context/hardware lifetime. Newer admitted SaveScreen uploads supersede
title art. This does not model untouched name tables, SAT or every level-to-level
gap (2026-10-14 ending ingress; d0fabc3ef candidate4d401bcc).
