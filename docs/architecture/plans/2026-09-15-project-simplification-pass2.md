# Project simplification — second pass

## Scope and base

Implement the seven source-backed candidates approved after the second discovery
pass. Base: `6cd8ec1881fcab438705ec4fd736804dd6630a50` on develop. Use isolated
`feature/ai-simplification-pass2`; preserve the main workspace's existing changes.
Java 21, existing libraries, unchanged public Mod API. Keep behavior at the owning
callers; extract repeated mechanics only. ROM hashing and editor overlay extras
are deferred. First-pass retained gates remain closed.

## Work list

- [x] Preview cache validation/loading: share manifest and PNG mechanics across
  S1/S2 managers. Preserve versions, zones, lazy hash and failure order, second
  manifest read, exception handling, and immutable all-or-empty results.
- [x] Preview scaling/publication: share exact nearest-neighbor scaling and file
  publication. Preserve game-specific capture coordinates, equal-size copy,
  image-before-manifest order, partial output and cleanup exception behavior.
- [x] Raw patterns: delegate the S2/S3K conversion loops to existing
  PatternDecompressor.fromBytes after current caller validation and ROM reads.
- [x] S1 voices: share operator normalization for music, SFX and zero-address voices;
  preserve address/bounds branches and fresh arrays.
- [x] Audio reference files: share identical path/digest primitives across S2/S3K
  producers and matching process/snapshot sites. Keep request validation order,
  output checks, publication ownership and error identities local.
- [x] GPU scroll uploads: private upload tail per HScrollBuffer/VScrollBuffer;
  retain source staging, signed values, zero fill, lazy allocation and GL lifetime.
- [x] Playable-sheet ordering: retained after the repository coupling hook rejected
  implementation-only reader changes without a candidate API-pin change. No public
  signature changes justify pin regeneration; fixing the coarse hook would expand
  this small extraction into unrelated API-policy work. Original code restored.
- [ ] Review, focused and combined verification, integrate, push, clean up.

## Verification

Workers own disjoint files in one task tree. Queue all Maven commands. Preview
checks cover invalidation, malformed/missing files, ordered lazy hashing, pixel
scaling and copy isolation, publication failure/cleanup. Pattern/voice checks
exercise production consumers, malformed input, exact pixels/operator bytes and
mutation isolation. Path checks cover noncanonical paths, symlinks, digests,
exceptions and producer ownership. Ordering checks distinguish UTF-8 from UTF-16
and retain reader/writer integration. GPU checks require native array/view texture
readback and cleanup/reinitialization, beyond existing headless staging coverage.

Finish edits before a combined category run against the pinned base. Inspect the
plan and preflight; shared changes require full ordinary/guard selection. No
tracked-file edits during that run. Use no injected MAVEN_OPTS: the runner adds
stock ROM paths. Inspect all failures/skips; match inherited failures by exact
identity and message. Attribute uncertain failures with bounded baseline checks.
Run mandatory S3K loading/bootstrap tests and applicable audio checks. Reconcile
upstream and verify integration by actual changed behavior; do not repeat unchanged
checks without cause. Acknowledge/delete consumed diagnostics. Record results here
before delivery; preserve only durable decisions, not raw logs.

## Results

Six implementations are retained; the comparator candidate is closed as retained duplication. Two Astra low workers implemented
preview and ROM/audio conversions; each cross-reviewed other owned code. Root
reviewed callers and native upload paths. No further actionable issue remained.

A copy-and-normalize voice prototype changed the exception class for malformed
`voiceId=85899345`: multiplication produces offset 2147483625 and offset+25
wraps negative, admitting the existing guard. The original zero-address
Arrays.copyOfRange throws IllegalArgumentException; System.arraycopy instead
throws ArrayIndexOutOfBoundsException. Retained original copies in all callers
and shared only in-place operator normalization. A regression covers this edge.
No arithmetic or bounds policy was repaired as part of the extraction.

Preview extraction retains typed local manifests and the second disk read;
wrapper tests replace the manifest from the hash supplier to protect that order.
Whole-generation transactionality and asynchronous ownership were not changed.
The final implementation removes 128 production Java lines overall, including
four new internal helpers. The reduction is smaller than discovery estimates:
explicit adapters preserve caller policies without a callback-heavy framework.

Focused verification passed: 277 tests, zero failures/errors/skips, 52.852 seconds
(excluding queue wait), on the final source tree based on `6cd8ec188`. This includes
native array/view texture readback across two contexts and repeated resource
lifetimes, caller-level overflow behavior, both preview wrappers, ROM-backed art,
and mandatory S3K bootstrap/loading tests. Command:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestPreviewCacheFiles,TestPreviewImageFiles,TestS1DataSelectImageCacheManager,TestS2DataSelectImageCacheManager,TestS3kDataSelectPresentation,TestSonic1SfxData,TestSmpsDataEndianParsing,TestSmpsAssetCatalog,TestSmpsFmVoiceWriteProfiles,TestPatternDecompressor,TestSonic3kObjectArtProvider,TestObjectArtPatternCapacity,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestCompleteRunAudioFiles,TestCompleteRunAudioInputSnapshot,TestTraceChaserAudioProcess,TestS2CompleteRunReferenceProducer,TestS3kCompleteRunReferenceProducer,TestPlayableSheetV2,TestHScrollBufferCompatibility,TestVScrollColumnCount,TestScrollBufferUploadNative' \
  -Dopenggf.scrollNative=true \
  -Dsonic1.rom.path=${OPEN_GGF_REPO}/s1.gen \
  -Dsonic2.rom.path=${OPEN_GGF_REPO}/s2.gen \
  -Ds3k.rom.path=${OPEN_GGF_REPO}/s3k.gen test -B
```

Broad plan selects 2,592 classes, all ordinary categories plus fresh-JVM guards.
Combined verification is pending. Java 21/Lua 5.4/PowerShell
preflight passed. Initial hook installation encountered the expected read-only
Git sandbox boundary; the escalated hook installation succeeded.

Commit-hook scope correction: the focused run also included the proposed shared
UTF-8 comparator, which passed its checks. The coupling hook treats any source
change in an @ModApi-annotated file as an API delta. The reader/writer and their
test were restored byte-for-byte to the pinned base, and the new comparator was
removed. No signature pin, API declaration or policy was altered to bypass the
hook. The remaining source is unchanged from the 277-test focused run; the broad
run will validate the final retained scope.

## First broad run and reconciliation

At `48ab5ea1e`, command
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 6cd8ec1881fcab438705ec4fd736804dd6630a50 --run`
completed run `20260915T103934Z-56a46939`:

- Ordinary: 2,592 reports, 20,498 tests, one failure, zero errors, 19 skips;
  746.97 seconds. TestRemainingRewindTailInventory expected 1010/790 total/passed
  but found 1011/791 after the pre-existing SOZ quicksand addition. Upstream
  `c2812dc11` independently corrects this inventory. The corrected test passed
  on current develop (one test, zero failures/errors/skips).
- Guards: 84 reports, 668 tests, five failures, zero errors/skips; 173.25 seconds.
  Two task regressions: Sonic1FmVoiceDecoder was in the shared audio package rather
  than game.sonic1; CompleteRunAudioFiles used the overly generic parameter name
  `expected`, which the authority guard rejects in authenticated tooling. Move the
  helper to game.sonic1.audio.smps and name the fixed identity `pinnedDigest`.
  No guard or baseline is weakened; digest checking supplies no gameplay data.
- The other three failures reproduce on develop `316788395` with identical method
  identities and complete messages: TestRewindArchitectureGuard reports SOZ's two
  RewindTransient annotations; TestBuildToolingGuard still expects old direct-Maven
  guidance; TestNoAssertionFreeDiagnostics flags FbzRouteEvidenceProbe#printEvidence
  and LevelSolidityMapProbe#writeSolidityMap. Matched command:
  `LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards
  -Dtest=TestRewindArchitectureGuard,TestBuildToolingGuard,TestNoAssertionFreeDiagnostics test -B`.
  Baseline: 123 tests, three failures, zero errors/skips.
- All 19 skip identities/reasons were inspected. Eighteen are the inherited opt-in
  routes/benchmarks/native/local-reference checks and CPZ assumption from pass one.
  The additional native scroll check is opt-in and already passed in the focused
  native run; no stock-ROM prerequisites were missing.

Upstream since the pinned base includes resource-aware Maven admission/cleanup,
the SOZ inventory correction, and KiS2 native act-entry/gap ownership changes.
They are disjoint from the simplification source. Preserve them, run a final
integrated selection, and report its exact commit/results. Focused guard fixes
and reconciliation are in progress.

### Guard correction result

The authority guard passed all 25 tests after the parameter became pinnedDigest.
The first relocation recheck exposed the second architecture boundary: shared
Sonic1SmpsData cannot depend on a helper in game.sonic1. The final owner is the
semantic shared `audio.smps.FmVoiceOperatorOrder.swapMiddleOperatorsInPlace`:
it performs only the six middle-operator swaps; S1 callers own when it is needed.
No architecture rule or frozen baseline changed. Original copying and overflow
behavior stay in the callers. Obsolete compiled helper classes were removed
before checking the moved source.

`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Pguards -Dtest=TestArchUnitRules test -B`
passed 29 tests, zero failures/errors/skips (53.083 seconds). Combined authority
and architecture recheck before the final relocation had 54 tests, one failure;
that failure was the now-corrected downward dependency. Consumed first broad-run
diagnostics were acknowledged and deleted. No raw reports are archived.
