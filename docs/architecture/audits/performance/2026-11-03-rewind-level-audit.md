# Cross-level rewind cache audit

The follow-up to the [LRZ1/SOZ2 investigation](2026-11-02-rendered-rewind.md)
found another unconditional terrain invalidator in Sky Sanctuary Act 2. The ending
scene resource adapter restored scene layout and called `LevelManager.setLevel`
on every restore, before the registered S3K collision-layout adapter restored the
same geometry again. Removing those duplicate operations leaves resident art
restoration intact and makes the existing collision-layout adapter the geometry
owner. Actual scene changes still invalidate through that owner.

## Workload and coverage

The manual `TestRenderedRewindCacheMatrix` probe boots real gameplay with EGL,
steps six neutral frames, draws, captures the whole registry, then restores and
draws that snapshot five times. It counts foreground array replacements and
background full rebuilds. It asserts reuse for ordinary, unwrapped, revision-zero
planes without a retained descriptor owner. Wrapped windows and event-owned
planes have separate restore rules and are reported rather than forbidden.

The matrix has 68 entries:

- S1: all 19 ordinary acts, GHZ through Final Zone.
- S2: all 20 ordinary acts, EHZ through Death Egg, including MTZ3.
- S3K: both act slots of AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ, MHZ, SOZ, LRZ,
  SSZ and DEZ; DDZ `$0C00`, ending `$0D01`, LRZ boss `$1600`, HPZ `$1601`,
  and DEZ boss `$1700`.

These are **entry-state checks**, not complete routes. Competition, bonus and
special stages, sanctuary `$1701`, alternate characters, donors, widescreen,
late event states and every boss phase are outside this matrix. The prior SOZ2
recorded pre-boss route remains the late-route performance witness. A source audit
also checked S1 door reconciliation, S2 HTZ earthquake restoration, and S3K AIZ,
CNZ, FBZ and retained-plane restore owners; actual transitions retain their
invalidations.

The 320 by 224 probe uses Sonic alone in S1 and Sonic/Tails in S2/S3K. Existing
verified ROMs use the identities in the repository ROM setup table. Rendering is
surfaceless EGL with `DISPLAY` and `WAYLAND_DISPLAY` unset; no desktop input or
audio device is used. This matrix measures work counts, not service latency.

## Evidence and limits

The baseline was `1b22c86eb427f27fff272db071f4435f66f77d78`. Its SSZ2 row rebuilt
both full-layout planes on all five restores; all five engine pictures matched.
The first candidate eliminated those rebuilds and kept all five picture matches.
An attempted conditional geometry rebind then failed the real ending AIZ scene
handoff: the earlier resource adapter still saw the forward encoded layout's
width. That approach was rejected in favor of removing duplicate geometry
ownership entirely.

Four baseline rows had zero exact engine picture matches: AIZ2, HCZ2, FBZ2 and
ending. The first candidate had the same four failures, with identical matching
counts. These picture diagnostics are separate from the cache assertions; a cache
pass does not certify rewind pictures, ROM parity or complete routes. The original
combined cache/picture assertion was split after recording that distinction, and
all picture match counts remain in the JSON output. No expected-failure list
suppresses new cache failures.

Final rendered and held-rewind results are pending at this checkpoint.

## Regression checks

Ordinary coverage adds SSZ2 to
`TestS3kCollisionLayoutTilemapRestore.unchangedWholeWorldRestoresReuseBothTilemapsAndCapturedMapBytes`
and checks ending map identity plus resident-art rollback in
`TestS3kEndingSceneResources`. Its existing AIZ scene-handoff check still requires
the captured width and subsequent native scene behavior to restore. The rendered
ending presentation checks exercise native scroll, palette and descriptor owners.

The focused command below passed 140 cases, with no failures, errors or skips,
after the rejected handoff implementation was replaced:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off \
 '-Dtest=TestS3kEndingSceneResources,TestS3kEndingSceneState,TestS3kEndingPresentationCapture,TestS3kCollisionLayoutTilemapRestore,TestLevelRewindSnapshotAdapter,TestLevelTilemapManagerRewindReset,TestIncrementalBgTilemapWindow,TestS3kSszAct2ColdFight,TestS3kSszAct2FinalFight,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
 '-Ds3k.rom.path=/absolute/path/to/existing/locked-on.gen' \
 -Dopenggf.headless.gl=surfaceless-egl test -B
```

The change-based planner selected 4,418 smoke classes because this scene-resource
file is unclassified. That selection is disproportionate to removing duplicate
operations in one internal restore adapter. The focused owner, scene-handoff,
rendered presentation, SSZ2 fight, tilemap, shared level restore and required S3K
loading checks replace it. This is bounded validation, not a full-suite pass.

## Reproduction

Select the matrix explicitly with `-Pbenchmarks -Dtest=TestRenderedRewindCacheMatrix`
and all three absolute ROM properties, plus the EGL property above.
`openggf.cacheAudit.output` selects its JSON destination;
`openggf.cacheAudit.entry=s3k:10:1` selects SSZ2 alone. The standalone main is
`com.openggf.level.TestRenderedRewindCacheMatrix`. Missing ROMs or boot failures
are reported as failures, not silently skipped.

Use `TestRenderedRewindPerformance` with `openggf.perf.zone=10` and
`openggf.perf.act=1` for a live Engine held-rewind witness. Its latency output is
not a repeated baseline/candidate performance comparison. Runtime timing claims
require the separate isolation and counterbalanced protocol in the prior record.
