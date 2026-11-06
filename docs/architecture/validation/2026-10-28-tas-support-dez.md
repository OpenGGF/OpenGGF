# S2 ordered object-art publication — 2026-10-28

Scenario: `s2-dez-art-corruption:1`. This record qualifies the shared art-slot
mechanism separately from the publication author's quick-kill causal route.

## Source and native expectation

The assigned base is `8945c0a789b27be57b2361c70f561f31872a76ca`, in
the assigned isolated worktree on
`bugfix/ai-tas-support-dez-art-20261028`. The original local
`s2.gen` (the parent-provided absolute original, passed as `S2_ROM`) was read directly; CRC32
`7B905383`, SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.

Native evidence uses the unchanged disassembly pin
`34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`:

- `Obj58_Init (s2.asm:61314-61326)` keeps the fiery-explosion mapping and
  tile base. `Obj58_Main (s2.asm:61332-61341)` advances its seven frames.
- `PlrList_FieryExplosion (s2.asm:89867-89869)` publishes fiery art at `$580`.
- `PlrList_DezAnimals (s2.asm:89909-89912)` publishes Pig at `$580` and Chicken
  at `$594`. The actual ROM Nemesis headers contain 100 fiery tiles, 18 Pig
  tiles and 16 Chicken tiles. Animals-last retains fiery tiles `$592-$593`
  and `$5A4-$5E3`; the final image therefore has four source runs. Later
  overlapping entries supply the pixels read by the unchanged mapping. This
  requires ordered game resource ownership, not hardware emulation.
- `Map_obj58_000E` through `Map_obj58_004A` in `mappings/sprite/obj58.asm`
  independently specify one 2×2 piece starting at tile 0, then six 4×4 pieces
  starting at tiles `$04,$14,$24,$34,$44,$54`.

## Historical evidence and current inspection

The 2026-10-26 audit at `4f5eb1ee2de0b6128c39728289516c079d71064e`
reached both real PLC orders and observed an independently allocated explosion
sheet staying fiery. The recorded animals-last mismatch was pixel 3,
expected 10 versus actual 0. That historical negative evidence remains valid;
the regression now requires the native expected behavior.

A fresh-red-first request was initially queued but did not execute against the
old owner before implementation. The parent explicitly authorized proceeding
from the historical reached contradiction, current owner inspection and pinned
independent expectation. The same request was retained and executed against
this candidate's Java source; it is not an executed baseline red.

At the assigned base, `preparePlcs` still skips already registered sheets and
`publishPreparedPlc` only appends sheets. The newer ordered-write mechanisms in
`KosinskiModuleQueue` and the S2 competition host do not publish into these
object sheets. They are left unchanged. `PatternSpriteRenderer.updatePatternRange`
already owns existing atlas and pattern-bounds refresh.

## Scope and qualification

The bounded owner binds the native animal and fiery-explosion sheets across S2
zones. It consumes every runtime PLC entry's intersecting tile range in order;
there is no zone/PLC/Pig rendering branch. Each view has a private mutable copy
so the loader's cached ROM art remains pristine. Existing renderer identities
and allocations survive publication; `updatePatternRange` refreshes changed
atlas pixels and pixel bounds. Registration return values and renderer-cache
allocation callbacks retain their existing meaning.

`PlcProgressSnapshot` gains optional immutable ROM/world provenance and a
coalesced final sparse image of ROM recipes. Every previous constructor,
including the previous full S3K signature, remains available with no-image
defaults. Restoring validates provenance and source/destination bounds before
mutation, reconstructs recipes from the original ROM and resolves each binding
against its baseline plus the final image. A fresh same-ROM/same-zone provider
does not need a publication-history handle. Empty initial images retain
provenance. Identity never selects gameplay behavior.

Composite/dynamic sheets and externally mutated art are outside this owner.
Patch-provided overlays targeting a participating key retain their own sheet
ownership and are excluded from the ROM-static binding. Initial whole-level
tile residency, exact intermediate Nemesis service cadence and unrelated
render consumers are not certified by this bounded eager-art qualification.

## Focused verification

The tested source is the assigned base plus the six owned Java files in this
change. Their path/NUL/content/NUL SHA-256 fingerprint, in Git path order, is
`a3695389d203a3a0f808e941bc008eea6404fe6980f57b13be17e84598dd890d`.
The implementation is committed as
`b5c3f559436749e5b92d3e236efe7c73b485b304`; the successful recovery below
ran at that exact commit. The earlier alias run compiled identical Java source
before the commit. No executable source changed after this fingerprint was pinned. `JAVA_HOME`
selects JDK 25 and `LUA_BIN` selects Lua 5.4. `S2_ROM` below expands to the
parent-provided absolute original ROM, whose identity is recorded above.

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dsonic2.rom.path=$S2_ROM" '-Dtest=TestTasS2DezArtAlias' test
```

Passed: 2 tests, 0 failures, 0 errors, 0 skips. This is candidate validation,
not an executed old-owner baseline red. Maven compiled with release 25 and
reported 1m37s total execution after 1813s queue waiting; individual test time
was 0.377s. Exact end-to-end wall time was not separately measured.

```sh
env -u DISPLAY -u WAYLAND_DISPLAY \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl "-Dsonic2.rom.path=$S2_ROM" \
  '-Dtest=TestSonic2PlcSharedArtRendering,TestSonic2PlcArtSlotSnapshot,TestSonic2PlcArtRewindSnapshot,TestSonic2RuntimePlcRendererRefresh,TestSonic2PlcProducerCoverage' test
```

The first disjoint rendering/lifecycle request (session `76218`) terminated
externally with exit 143 (SIGTERM) while still waiting for admission; its last
reported queue wait was 1837s. Maven did not execute, so there are no test
outcomes or skips for that request. The parent confirmed that it had not
cancelled the request; the SIGTERM cause remains unknown. This was not a test
failure or a policy cancellation.

The parent authorized one recovery from that external termination, with the
same source, selection, flags and resource limits and no active duplicate.
Recovery session `67971` completed successfully at the implementation commit
above: **53 tests, 0 failures, 0 errors, 0 skips**. Maven reported 34.340s total
execution after 2180s queue waiting. The queue intervals overlap other requests
and are not summed as delivery elapsed time; exact delivery wall time was not
separately measured.

| Class | Executed | Failures / errors / skips |
| --- | ---: | --- |
| `TestSonic2PlcArtRewindSnapshot` | 5 | 0 / 0 / 0 |
| `TestSonic2PlcArtSlotSnapshot` | 4 | 0 / 0 / 0 |
| `TestSonic2PlcProducerCoverage` | 24 | 0 / 0 / 0 |
| `TestSonic2PlcSharedArtRendering` | 8 | 0 / 0 / 0 |
| `TestSonic2RuntimePlcRendererRefresh` | 12 | 0 / 0 / 0 |

Together with the disjoint alias selection, **55 focused cases passed with zero
skips**. All eight display-free EGL pixel cases executed: the two real PLC
orders, ordinary fiery controls, all seven native explosion frames, both native
animal consumers, repeat/short overlap/gap/tail/non-overlap controls, snapshots
before/after publication, fresh-provider restore without publication history,
reject-before-mutation cases, same-zone reload, cross-zone cached-art reload,
and production runtime PLC publication. Expected pixels are computed from the
original ROM plus independently pinned native mapping dimensions/offsets, then
compared with actual production GPU readback against a cleared target. The
oracle rejects an all-black witness. No observed pixels feed the engine.

The focused citation check against the immutable pin passed (7 verified
citations across this record and the pixel regression). The provider's two
new native citations also passed; its two pre-existing unverifiable citations
were left unchanged. No disassembly pin or dirty reference was changed.

## Handoff and limits

The scenario ID remains `s2-dez-art-corruption:1`. Its regression selector is now
`TestTasS2DezArtAlias#nativeLastWriterOwnsTheExplosionTiles`, replacing
`#currentLimitationAnimalPlcCannotOverwriteTheExplosionSheet`; the historical
negative result remains recorded above and in the original audit. This
qualifies the bounded native art-slot mechanism and rendered representative,
with no new engine failures observed in the selected checks.

The parent owns central classification/discrepancy/changelog reconciliation,
combined smoke validation, integration, push and cleanup. No whole quick-kill
movie, exact PLC service timing, full DEZ route or complete zone/character matrix
is claimed by this component qualification. No broad or release-only full suite
was run by this worker. Both successful test requests are complete, the
interrupted request is exited, and no native probe was launched. Consumed
worker diagnostics are removed before handoff; the clean task branch/worktree
is retained for the parent.

## Common-art identity correction — 2026-10-28

The parent resumed this worker after broad smoke at
`88b2e5d483e7aa24c23dbf8c1da103071a254c18` reached
`TestSonic2PlcParser#commonOnlyArtLoadsRealRomSheetsWithoutLookingUpAZoneHeader`
and reported `IllegalArgumentException: Invalid art-slot ROM/world identity`.
The retained clean worktree fast-forwarded to that local parent commit; no new
checkout, main-workspace operation or delegate was created.

Inspection of both that source and the assigned historical base shows that
the method first completes the documented `loadArtForZone(-1)` common-sheet
checks, then loads `-2` and `17` to verify the existing parser's out-of-table
fallback. The new `beginZone(-2)` identity construction blocked that parser
boundary. This is a regression in the new art owner, not evidence that the
common-only ROM sheets are missing. The parent's reached error is the defect
evidence; this worker did not rerun or report a fresh baseline red.

`Sonic2ObjectArt.loadForZone` uses its default animal pair outside the native
table, while `Sonic2PlcLoader.getZonePlcIds` returns the common `Std1` list IDs
for such selectors. The provider now records these resulting static bindings
with the existing common-art identity (`nativeZone = -1`), preserving the
original selector and parser calls. Native table entries retain their exact
native-zone identities. `ArtSlotIdentity` validation is unchanged: values below
`-1` or above `0xFFFF` remain invalid, and restore/publication still reject
foreign ROM/native-world provenance before changing bytes, pixels or epoch.
An out-of-table selector does not create a new native world. Provenance remains
the ROM SHA-1 plus the static art world actually loaded, including empty images.

The correction changes only `Sonic2ObjectArtProvider` and two exact owned test
files. The frozen candidate is parent `88b2e5d48` plus those three Java changes:
Git tree `f2384e82804c47bcf9b4314cdce926e88d131ab0`, with source subtree
`d77dcad02b9e08f9e94cf5c60c3a5510979e1e48`. The original absolute S2 ROM's
SHA-1/CRC32 above were verified again. No shared snapshot/store, S3K, graphics
contract or test owned by another worker changed.

The focused request selects the exact failing parser method, snapshot bounds,
three common-art EGL parameter cases (`-1`, `-2`, `17`), native/common/foreign
rejections, native load retirement, and two runtime renderer/rewind consumers:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic2.rom.path=$S2_ROM" \
  '-Dtest=com.openggf.game.sonic2.TestSonic2PlcParser#commonOnlyArtLoadsRealRomSheetsWithoutLookingUpAZoneHeader,TestSonic2PlcArtSlotSnapshot#recipeAndIdentityBoundsRejectOverflowAndMalformedInputs,TestSonic2PlcSharedArtRendering#commonArtProvenanceSurvivesPublicationReloadAndFreshRestore+foreignWorldEmptyImageAndInvalidRomRecipeAreRejectedBeforeMutation+zoneLoadRetiresThePriorSlotImageAndReloadDoesNotReuseMutatedCachedArt,TestSonic2RuntimePlcRendererRefresh#runtimePlcPublishesNewRendererInSameFrameAndRefreshesOnce+rewindReplayThroughZoneEventReusesRendererWithoutDuplicateRefreshOrAllocation' test
```

Request `81435` completed with exit 0: **9 tests, 0 failures, 0 errors, 0
skips**, finishing at `2026-10-28T23:26:44Z`. Java 25 compiled the frozen
candidate; Maven took 1m42s after 61s queue waiting. End-to-end wall time was
not separately measured. The request had one command session and observer,
with no resource overrides or duplicate/restarted admission request.

| Selected class | Executed | Failures / errors / skips |
| --- | ---: | --- |
| `TestSonic2PlcParser` — exact failed method | 1 | 0 / 0 / 0 |
| `TestSonic2PlcArtSlotSnapshot` — bounds | 1 | 0 / 0 / 0 |
| `TestSonic2PlcSharedArtRendering` — common, foreign, load | 5 | 0 / 0 / 0 |
| `TestSonic2RuntimePlcRendererRefresh` — publication, rewind | 2 | 0 / 0 / 0 |

All five selected EGL cases performed production GPU readback. The three
common-art cases reached distinct fiery and animal-overwritten pixels, restored
before/after publication and the initial empty image, replayed forward, retained
renderer identity/allocation on reload, and rebuilt a fresh common target from
the final ROM recipes. Empty common/native images, prepared native art, foreign
ROM and a foreign out-of-table zone identity were rejected without mutation.
The selected ordinary native-load and runtime renderer/rewind consumers passed.

The previous 55-case qualification remains attributed to its recorded candidate;
the five genuinely affected prior cases above were repeated, while the other
unchanged 50 cases were reused. No broad suite or parent root guards were run
here. This bounded identity correction does not expand the DEZ route,
quick-kill, PLC cadence or dynamic-art claims; the scenario ID remains
`s2-dez-art-corruption:1`. The test request is closed, no native probe was
launched, and consumed worker diagnostics are removed before handoff. The
retained branch/worktree remains available to the parent; integration, central
reconciliation, combined validation and cleanup remain parent-owned.
