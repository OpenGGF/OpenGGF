# Object pipeline performance — 2026-10-22

Implementation starts at develop `8aa692b045c195ef8a42896b6bbc9549158470c0` in
the T3-owned task worktree, local branch
`bugfix/ai-object-pipeline-performance-20261022`. This record accompanies the
implementation commits; the lead owns combined validation and integration.
The tested native-query foundation was split at the lead's request into
`4b437189c7d89fe94302847abac98b3078f0ce6b` while the final rendering request
remained queued. Committing changed no queued source inputs.
No physics, collision, animation, allocation or ROM clock constants change.

## Decisions and structural evidence

1. **HCZ water children:** `NativeObjectQueries.firstLive` indexes the current
   manager population, retains candidates for one requested type, and chooses
   the lowest current owned slot among live candidates. The three child classes
   no longer build a list, identity set and sorted stream for each query. The
   candidate list is not a latched parent identity: lower-slot arrivals,
   deletion, replacement, slot transfer and rewind adoption remain observable.
   The 23-query seam performs no additional slot reads on unrelated objects
   after preparing the index. Existing child creation/recreation is unchanged.
2. **Optional picture geometry:** each draw-local body value shares one policy
   iteration and affine/trigonometric computation. Adjacent compatible
   `PatternRenderCommand` presentation tiles form one bounded geometry command,
   preserving the original fan's diagonal as ordered triangles. A 23-tile run
   becomes one geometry submission and three VBO uploads instead of 23
   submissions and 69 uploads; 513 tiles split into 512 + 1. Atlas page, palette,
   priority shader/bit, occlusion mask, ghost state, hue, opacity, normal instance
   commands and primitives remain barriers. Deferred palette resolvers retain
   their per-tile invocation boundary even when the callback identity is equal:
   the callback contract does not promise a constant row or pure side effects.
   Row cropping/flips still use the original resolved UVs. The stock path retains
   instancing, with an explicit restart when leaving transformed geometry, and
   allocates no transform map without picture policies. Native SAT admission,
   head composition and trails remain in their existing owners.
3. **ICZ frost puffs:** reusable, transient update/draw snapshots preserve stable
   public append order, ascending native execution, descending painter order and
   stable ties. An identity membership set replaces a linear `contains` per
   update; stable compaction replaces repeated expiry shifts. Adds and whole
   graph retirement update membership during a pass; new children retain the
   old snapshot's next-pass behavior. Scratch is rebuilt from authoritative
   restored children and cleared after use, including exceptional exits. Native
   reservations still release in execution order and are never reallocated by
   scratch restoration. Partial allocations stay partial.
4. **SSZ Mecha children:** all four known-parent scans use typed owned-slot
   lookup. The index uses `getSlotIndex()`, never `execOrder` or execution-slot
   aliases, and rereads destruction state. Membership changes invalidate before
   removal notifications, rather than waiting for the pass tail; slot setters,
   overwrite/adoption and rewind invalidate derived storage. A native pointer
   follows a same-type replacement at that address. The deferred collision-child
   delete and parentless charge/projectile/glow branches retain their existing
   behavior. Unrelated LRZ/DEZ parent lifetimes are outside this change.
5. **Pattern ranges/results/snowboard:** `updatePatternRange` batches atlas
   writes and invalidates exactly the same bounds/head cache. Atlas nesting is
   depth-counted, so an inner range cannot flush an outer owner's writes.
   S1/S2 results rewrite/upload changed counter ranges independently; score and
   fixed-zero art remain separate. Snowboard DPLC ranges inherit the batching
   through the same renderer. Small dirty batches still upload individual tiles
   under one texture bind according to the existing atlas threshold: this is
   not a claim of one GL upload for every range. Content equality is deliberately
   not cached in the generic renderer or snowboard bank: another sheet can own
   the shared virtual residency, so equal local bytes do not prove residency.
   Mutable pattern versions still publish through the existing path.
6. **Touch profiles:** finite immutable value tables cover the existing decode
   modes, four boolean flags and shield words 0/8 (160 compatibility and 128
   canonical profiles). Every provider flag is still sampled on every call;
   dynamic multi-region presence and explicit adapter arguments remain live.
   Unusual integer shield words and custom adapter policies use uncached records
   without truncation. The compatibility boolean adapter avoids its intermediate
   canonical record, and Rockn returns the already correctly decoded superclass
   profile. No provider/class identity cache or public API signature changes.
   Reuse is demonstrated by record identity checks; JIT allocation elimination
   was not measured, so no measured heap or frame-time improvement is claimed.

Rejected shortcuts: keeping a child bound to its spawning column/parent identity;
using stale execution slots as physical ownership; caching mutable touch providers;
regrouping nonadjacent geometry; coalescing arbitrary palette callbacks; skipping
uploads based only on a renderer's last local pixels. Each loses a live contract
that the focused regression seams exercise or the existing residency owners require.

## Focused verification

Java 25.0.5 and Maven 3.10.0 were verified with
`python3 tools/testing/maven_queue.py -v`. Every command below ran in this
worktree, with a private `target/`, against the base plus this implementation.
For portable command records, `ROM_ROOT` below denotes the existing main-workspace
ROM directory; each expanded ROM property was supplied as an absolute path.
Results are bounded checks, not a broad smoke/full-suite or ROM parity claim.
The measurement-hazard catalogue was read before attributing the results.

- Build: `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests compile` — passed.
- Initial component/regression selection:
  `python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestNativeObjectQueries,TestTouchResponseProfileMapping,TestPresentationPatternBatching,TestPatternAtlasDirtyUploads,TestGraphicsManagerPatternAtlasPageOrdering,TestS3kHczEndBossGraphRewind,TestS3kIczEndBossGraphRewind,TestIczEndBossRenderBuckets,TestS3kIczEndBossObject,TestModApiSignatureSurface' test`
  — 130 passed, no failures/errors/skips. Admission waited 45 seconds.
- Initial render/range selection:
  `env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestPresentationSpritePixels,TestNativeObjectQueries,TestResultsPatternRanges,TestPlayerHeadPresentation,TestObjectHeadPresentation,TestSpritePictureTransform,TestGraphicsManagerReinit,TestPooledRenderCommandDiscard' test`
  — 32 passed, no failures/errors/skips. The new actual-pixel comparison uses
  23 overlapping, rotated, translucent tiles with varying flips/source crops,
  compares all framebuffer bytes to batching disabled and rejects a blank image.
  Existing pixel coverage also exercises water/terrain priority, rainbow state,
  trails with historical DPLC versions and hidden bodies/appendages.
- ROM/index selection:
  `python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestNativeObjectQueries,TestS3kHczEndBossGraphRewind,TestS3kIczEndBossGraphRewind,TestHczEndBossInstance,TestHczEndBossWaterColumnControlPolicy,TestHczEndBossChildBuckets,TestS3kSszMechaSpawnHeadless,TestIczSnowboardArtLoader,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kBadnikTouchResponseProfiles,TestS3kBossTouchResponseProfiles' "-Dsonic1.rom.path=${ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test`
  — 148 passed, no failures/errors/skips. Admission waited 302 seconds; this is
  queue delay, separate from execution. It includes six index regressions,
  existing encounter/rewind checks, snowboard DPLC behavior and all four required
  S3K stability classes (both classes named `TestSonic3kLevelLoading` ran).
  SHA-1/CRC32 of all three existing absolute ROMs match the repository's S1 REV01,
  S2 REV01 and locked-on S3K identities. No ROM files or links were created.
- Refined rendering/ICZ/API selection:
  `env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestPresentationPatternBatching,TestPresentationSpritePixels,TestScopedPalettePresentationRewind,TestIczEndBossRenderBuckets,TestS3kIczEndBossObject,TestModApiSignatureSurface,TestPooledRenderCommandDiscard' "-Ds3k.rom.path=${ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test`
  — 92 executed, one assertion failure, no errors/skips. Admission waited 370
  seconds. The new page-barrier fixture incorrectly assumed different virtual
  pattern IDs implied different physical atlas pages; both allocations actually
  occupied the same 1024-pixel page and correctly merged. No production defect
  was indicated. The fixture now uses a one-tile page and explicitly verifies
  distinct physical page indices. All 87 checks outside that five-test class
  passed, including actual EGL pixels, dynamic palette-bank replay, ICZ ordered
  expiry/rewind neighbors, pooling and unchanged public API signature pins.
- Corrected fixture:
  `python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestPresentationPatternBatching' test`
  — all five passed, no failures/errors/skips; admission waited 152 seconds.
  Only the fixture changed after the refined run, leaving all production source
  and the other 87 passing checks unchanged. Together these qualify the affected
  refined selection; this is not a claim of one pristine 92-test passing run.
  No focused failures remain unaccounted for.

The ROM/index selection supersedes the initial index checks for immediate
removal invalidation. Final refined checks below cover the conservative
palette-callback barrier, draw-local transform reuse count and scratch cleanup.
API signature pins remain unchanged; the signature test passed in the initial
selection and is checked again with the final affected render checks.

## Coverage and handoff limits

Existing HCZ2/ICZ2/SSZ2 act matrices retain their route, donor/team, viewport,
load/respawn and native audiovisual gaps. Component/graph checks do not certify
those products. No trace fixture or frontier moved. Combined change-based smoke,
integration, push and worktree cleanup belong to the lead.

The subsequent bounded rendering wave below owns the mask-only and frozen-art
restore overlaps. Other live-slot consumers and the later primitive-slot index
repair have a separate lead-owned audit; the original foundation here changes
only the HCZ/SSZ sites. The consolidated release entry also records the lead's
combined CPU wave (FBZ squeeze/projection reuse, SOZ vine work reuse, signed
surface sampling with defensive bulk reads, bounded solid parameters and the
CNZ/LRZ/DEZ/SSZ/SOZ owned-slot migrations). Its focused validation and coverage
notes belong to those owners.

## Local commit gate correction

Native foundation `4b437189c7d89fe94302847abac98b3078f0ce6b` is committed.
The remainder initially hit the coarse record API detector despite passing exact
signature tests. Bash and PowerShell now share a lexical declaration probe for
records: components, visible methods/constructors, annotations, public constants
and nested public contracts remain observable; private state/helpers and executable
bodies do not masquerade as new signatures. Other type handling is unchanged.
`python3 -m unittest discover -s tools/testing -p test_release_snapshot_policy.py`
passes all 40 tests, including actual Bash and PowerShell runs and new positive/
negative record scenarios. Pins and API authority remain unchanged. The initial
machine-local evidence paths were replaced with portable `ROM_ROOT` notation.

The lead authorized the subsequent bounded mask, physical-residency and final
emission-culling work; its evidence follows in later sections of this same record.


## Bounded rendering follow-up

The initial remainder and policy correction landed as
`306b960af2be7cea46dc8d1358d6c4507962fa40`. The follow-up starts there and leaves
native indexing and the independently owned Tile/prepare reuse unchanged.

- **Mask boundaries:** the internal presentation bridge asks whether the actual
  instanced batch is active, rather than inferring that from GPU support or a
  preference. Mask 15 then 0 on one physical page yields one instanced command;
  the stored per-instance mask floats retain 15 and 0. The legacy uniform backend
  keeps two commands. Object bucket rendering and immutable picture replay use
  this rule. Shader, ghost, atlas-page and transformed/direct boundaries remain
  in their existing owners; no public Mod API signature was added.
- **Frozen art:** restore compares all 64 desired nibble bytes against the exact
  physical slot in the CPU atlas mirror. Matching bytes skip unpacking, mirror
  writes and dirty marking. Scene sampling still publishes the immutable version.
  Headless/preinitialization sampling alone never proves residency; aliases and
  recycled slots read the current physical bytes. Changed bytes write directly
  from the packed version and mark dirty before any upload attempt. Equality
  never clears pending full-page or failed-upload dirty state, so retries remain
  possible. Ordinary mutable `cachePattern` semantics remain unchanged. A virtual
  ID/version cache was rejected: samples can exist before textures, and an alias
  can replace physical bytes without changing that ID's sample.
- **Final emission culling:** replay captures supported axis-aligned orthographic
  logical bounds from the current projection, including centered safe area and
  FBO display height. Missing, nonfinite, sheared or perspective matrices fail
  open; preparation always fails open. The test is against the final transformed
  visible-row quad, or the actual integer native strip/scaled geometry. It runs
  after pattern publication/native SAT admission and before any batch-state
  change. Partial and boundary-touching geometry remains. An untransformed actor
  bound was rejected because rotation can bring an initially offscreen tile back
  into view. No update, lifetime or SAT allocation path changes.

Structural evidence is command/byte work, not measured FPS or CPU improvement.
The frozen-art seam observes one 64-byte tile upload for unchanged A/A, three for
A/B/A, no new upload for an equal physical-slot reuse, and retained full-page or
failed-upload retries. The cull seam keeps one visible batch across an invisible
shader/ghost tile instead of submitting that tile and splitting visible work.

Follow-up focused checks (base `306b960af` plus the changes in this section):

- `python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestFrozenPatternResidency,TestPresentationViewport,TestPresentationPatternBatching,TestPatternAtlasDirtyUploads,TestGraphicsManagerPatternAtlasPageOrdering,TestModApiSignatureSurface' test`
  — 47 passed, no failures/errors/skips. This includes nine existing page/order
  checks, ten ordinary dirty-upload checks and eleven unchanged API signature
  checks. Subsequent test-only assertions inspect the actual instance mask floats
  and scene sample publication; the EGL selection below includes those classes.
- `env -u DISPLAY -u WAYLAND_DISPLAY python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestPresentationSpritePixels,TestPresentationPatternBatching,TestFrozenPatternResidency,TestScopedPalettePresentationRewind,TestGraphicsManagerReinit,TestPooledRenderCommandDiscard,TestPlayerHeadPresentation,TestObjectHeadPresentation,TestSpritePictureTransform' "-Ds3k.rom.path=${ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test`
  — 43 passed, no failures/errors/skips. The five actual-pixel tests include a
  byte-for-byte comparison of culled replay with unknown-projection fail-open
  replay at 320/640, cropped edge rows and rotation bringing outside geometry
  onscreen. Both images must contain visible pixels. Existing scoped palette
  rewind, head fragments, trail versions, transformed pixels, command discard
  and render-host recreation checks also passed. The strengthened eight-test
  batching seam verifies actual mask floats 15/0 in the same instance buffer;
  the six frozen-art checks verify scene publication alongside physical bytes.

Both follow-up commands used one retained request/session each. Admission waited
at least the reported 60/120-second milestones respectively; precise admission
durations were not recorded, so no exact waiting or execution-time comparison is
claimed. No failed or skipped follow-up checks remain. These are bounded focused
checks; the lead still owns combined change-based smoke after integration. No
remaining render candidate from this bounded follow-up was rejected or deferred.
