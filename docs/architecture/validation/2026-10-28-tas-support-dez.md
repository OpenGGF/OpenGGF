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
