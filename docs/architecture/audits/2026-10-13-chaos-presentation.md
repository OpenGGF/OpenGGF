# Chaos presentation implementation and qualification

Implementation starts from shared contract `7ec4bfa0ddf741960f7091872fa21f2d41ba353b`
in `feature/ai-sonic3k-chaos-presentation`. The scene, sprite and HUD policy
record schemas remain unchanged. Native object producers expose immutable
`ObjectPresentationMetadata` roles and anchors; the shared renderer does not
infer badnik/pickup identity from object classes.

## Implemented behavior

- 4: smooth private sprite hue rotation; native palette textures remain owned.
- 10: at most 24 admitted native body/appendage poses, at most 256 tiles per
  pose, with frozen DPLC versions and exact admitted rows/flips/priority.
- 37–40: producer-anchored whole-object scale, rotation and area-preserving
  squash. Reviewed anatomy composition happens before whole-picture geometry;
  suppression takes precedence over current artwork and retained trails.
- 5–9 and 97–99: a private world-picture pass before native HUD, including
  inversion, mirrors, bounded shake/zoom, logical-input squash, terrain outline,
  and independently decoded ROM background pixels and scroll state.
- 31–36: compact native readouts with each label attached to its independently
  published current value, including warnings. Native HUD preparation stays
  unchanged; a pure draw projection applies one captured logical pose to both
  retained HUD SAT and newer HUD_COUNTERS packets. Chaos UI draws separately.
  DVD/Shy move the complete compact plaque with one captured velocity; orbit
  uses the player's screen centre with a 32×24 radius; scale stays at 75–125%.
  Shuffle samples one row permutation per activation.

`LivePresentationRuntime` owns the logical clock, input picture state, HUD state,
private scenery state and trail history in one world rewind graph. ACTIVE
presentations advance it once, independently of native tick count and GPU
draws. Pause/hold/rewind freeze its clock and surviving poses, while lease
removal immediately clears cancelled contributions and owned resources.
Ordinary-play departure, load, failure and
retirement release only its resources and clear its contributions.

## Decisions and rejected approaches

Keeping old DPLC addresses for trails was rejected: queued GL commands would
all sample the last upload at those addresses. Each owner instead leases one
6,144-slot private bank, with separate slots for each of the 24 poses.
The initial implementation used four faint poses and a 1,024-slot bank; the
[2026-10-14 revision](../plans/2026-10-14-chaos-intensity-and-reel.md) expands
the logical history and residency together, retaining ten complete owner banks.
Engine caller identity and a host/owner/generation-bound handle gate access.
Expiry releases exactly that bank; restoring a prior logical snapshot can
reacquire residency from the frozen versions without restoring gameplay.
Review also caught two boundary errors before qualification: a deferred quad
could lose its priority shader when submission flags were restored, and a
visible sibling part could admit the same player's hidden historical body.
Commands now capture their priority shader choice and reset GL caches on
program switches; trail admission matches both producer identity and part.
The pixel fixture exercises native low/high priority and visible attachment
cases, rather than only a wholly invisible player.

Transforming HUD during native preparation was rejected: retained labels and
VBlank counter refreshes would use different poses. Draw projection leaves
native counter publication and warning latches intact and applies one pose to
both packets. Moving the empty native column down to the lives row was also
rejected; the presentation uses compact readout bounds.
The first HUD implementation moved readouts independently, orbited the screen
centre and scaled at 60–160%. Review of the row 31–36 behavior rejected those
choices before qualification; the final implementation uses the plaque and
bounds above.

Drawing-time scenery advancement was rejected: skipped GPU frames and rewind
would change the result of stateful native scroll handlers. Scenery advances
on logical presentations and captures the private handler state and exact
scanline outputs. Draws consume prepared RGB pixels. Full RGB screens do not
belong in every rewind snapshot.

Scene composition preserves the caller's draw/read framebuffer, viewport and
GL state. It does not assume framebuffer zero. Terrain outline runs before
native player/hazard artwork. Scenery replaces the picture background without
changing native terrain, palettes, PLC, collision, targeting or object admission.
The existing S3K provider prepares only scenery source IDs **0, 1, 6, 11**:
AIZ1, HCZ1, LBZ1 and DEZ1 (zero-based act 0). Catalogue sampling must choose
from that subset and exclude the current native source. The policy's broader
0–13 index range does not imply prepared artwork: external unsupported requests
receive a readable `Scenery unavailable: Native ROM scenery <index> is not prepared`
receipt and no fallback picture. Index 13 maps to HPZ's sanctuary act and remains
unsupported. No native zone/scroll profiles were added for this pack.

## Verification and limits

The focused CPU and owned offscreen EGL checks are designed to cover native and wider viewports,
source flips and scanline clipping, whole-picture pivots, distinct trail art
generations, HUD label/value pairing, deterministic rewind/forward replay,
expiry and ownership cleanup, private scene pixels and fault restoration.
Implementation checkpoint `ccb70c8c42bbda83e080ade9a9ab8641dbf87e05` was explicitly
unverified when supplied for integration. Build-only `LUA_BIN=/usr/bin/lua5.4 python3
tools/testing/maven_queue.py -Dmse=off test-compile -B` passed on the repaired
source (69 seconds reported by Maven); this executed no tests. The current
0.7 candidate API pin matches a fresh canonical snapshot; the descriptor and
`ModApiVersion.CURRENT` remain unchanged. The coordinator may integrate this consumer
checkpoint to unblock combined compilation; it was not a passing-test claim.

The initial focused command below completed with **156 tests, 2 failures,
13 errors and 4 skips**, exit 1. Identity comparison incorrectly treated
negative zero as a nonidentity affine transform; private trail bank release
left immutable CPU pixel versions behind. Those two failures led to numeric
identity comparison and exact private-bank CPU sample cleanup. All 13 HUD
errors came from a fixture lacking the installed UI pipeline/camera, before
test behavior ran; the fixture now supplies those real dependencies. The four
GL checks skipped because GLFW's null-platform window could not provide GL 4.1.
The repair run uses the production surfaceless EGL pbuffer explicitly, with
creation failure treated as a failure rather than a skip or display fallback.
ROM-backed source and native player/head checks, API pin inspection, and the
remaining stock renderer/HUD regressions passed with no skips in that run.

Preflight with the default Lua command found the host's older Lua selection.
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
7ec4bfa0ddf741960f7091872fa21f2d41ba353b --preflight` passed Java 25, Lua 5.4 and
PowerShell. This did not run smoke or any engine tests. S1 World REV01, S2 World
REV01 and S3&K locked-on root ROM files were verified against the repository's
CRC32 and SHA-1 values and passed by absolute property paths to the focused run.

The initial focused command was (set `OPENGGF_ROM_ROOT` to the existing root
checkout containing the verified ROM files):

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSpritePictureTransform,TestLivePresentationRuntime,TestPresentationGraphicsAccess,TestPresentationSpritePixels,TestHudPresentationRuntime,TestScenePresentationPass,TestSceneBackgroundPreparation,TestHudRenderManager,TestHudRenderManagerWidescreen,TestLevelSpritePresentation,TestLevelSpritePresentationLifecycle,TestSpritePresentation,TestPlayableMutatorPresentation,TestPlayerHeadPresentation,TestObjectHeadPresentation,TestLevelRendererBackgroundViewport,TestLevelRendererBucketInvalidation,TestRomSceneBackground,TestModApiSignatureSurface' \
  "-Dsonic1.rom.path=$OPENGGF_ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$OPENGGF_ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test -B
```

The coordinated focused selection contains:
`TestSpritePictureTransform,TestLivePresentationRuntime,TestPresentationGraphicsAccess,
TestPresentationSpritePixels,TestHudPresentationRuntime,TestScenePresentationPass,
TestSceneBackgroundPreparation,TestHudRenderManager,TestHudRenderManagerWidescreen,
TestLevelSpritePresentation,TestLevelSpritePresentationLifecycle,TestSpritePresentation,
TestPlayableMutatorPresentation,TestPlayerHeadPresentation,TestObjectHeadPresentation,
TestLevelRendererBackgroundViewport,TestLevelRendererBucketInvalidation,
TestRomSceneBackground,TestModApiSignatureSurface`.
It uses the ordinary focused Maven lane rather than the lean lane because it
includes native ROM art and recursive API inspection. The queued request stays
alive during resource admission; queue waiting is separate from execution.

The affected follow-up command is:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestHudPresentationRuntime,TestLivePresentationRuntime,TestSpritePictureTransform,TestPresentationSpritePixels,TestScenePresentationPass' \
  -Dopenggf.headless.gl=surfaceless-egl test -B
```

It also covers paused clear/held cancellation with no clock or trail advancement,
world isolation, and the readable unsupported scenery receipt. Its outcome is
**35 tests, 1 failure, 1 error, 0 skips**, exit 1, on compiled repair checkpoint
`1b244ab4d17f1fe0ce327276ac6a0d4dafc0d506`. Queue admission took 5,782 seconds;
Maven execution took 25.958 seconds. All 17 HUD tests, all five geometry tests,
and all six actual surfaceless EGL pixel tests passed, including native/wider
viewports, flips, clipping, distinct frozen trail versions, stealth precedence,
priority masking, scene composition and GL fault restoration.
The unsupported scenery test incorrectly supplied index 13 as magnitude;
the fixed fixture uses the existing four-argument constructor. The ownership
failure exposed a production allocator bug: `IdentityHashMap.containsValue`
compared boxed bank addresses by identity, allowing two owners to reserve the
same range. Numeric reservation checks now compare values, with a dedicated
two-owner range/pixel/release regression. The affected follow-up is:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestLivePresentationRuntime,TestPresentationGraphicsAccess' test -B
```

The bounded CPU follow-up completed on `2538ca2ccab65372fbe58aecc0eaf9db195bc4f1`:
all seven runtime and three bank checks passed, zero failures/errors/skips.
Queue waiting took 4,189 seconds and Maven execution 27.144 seconds. Unchanged
completed GL/HUD/geometry/ROM checks were not repeated. This closes the two
owned failures; final-root load guards and union qualification are recorded
separately in the [integration plan](../plans/2026-10-13-sonic3k-chaos-mode-implementation.md).
The coordinator requested a compiled bank repair checkpoint while that sole
CPU request waited. Build-only `test-compile -B` passed on the numeric allocator
and corrected fixtures (Maven reported 1 minute 5 seconds), executing no tests.
This compilation was not behavior qualification; the completed CPU result above is the subsequent evidence.
The coordinator requested a second compiled checkpoint while that sole request
waited for admission. The same build-only `test-compile -B` command passed on
all repair sources (Maven reported 1 minute 8 seconds), with no test execution.
This does not qualify the repaired behavior or the newly enabled GL checks.

This work does not certify donor/custom character routes, stage gameplay,
every S3K boss/act encounter, or every detached background profile. Native
BADNIK/PICKUP overrides and additional detached ROM art are integrated by the
native pack owner; the final combined route matrix belongs to the coordinator.
Retained sprite/HUD packets that predate an activation receive producer labels
at the next native publication. No extra native execution or SAT rebuild is
performed merely to start a picture effect.

## CI pixel-oracle follow-up, 2026-10-21

The develop CI run [39594989371](https://github.com/OpenGGF/OpenGGF/actions/runs/39594989371)
at `bd042f08094155a5b7da76e552f25d43d86d98d7` failed the exact SQUASH framebuffer
comparison at `(19,36)`, with expected 0 and actual 255. Explicit surfaceless EGL
passed locally, while Mesa software rendering through the test's GLFW null-platform
EGL path reproduced the CI result. The temporary diagnostic used the original
strength 120 and projection offsets of -1/16, zero, and +1/16 pixel. At width 320
without flips, the first two offsets disagreed at exactly ten pixels, x=19..28 on
row 36; the positive offset had no mismatches. SCALE and ROTATE matched at all
three offsets. The renderer reported llvmpipe, LLVM 23.1.1, Mesa 26.2.4, and eight
subpixel bits.

The clipped quad's bottom edge was y=36.5. The CPU inverse-floor oracle excluded
that sample-center edge, whose ownership OpenGL leaves implementation-defined.
This does not justify a renderer epsilon or relaxed RGB assertions. A proposed
strength 125 was rejected by the existing SQUASH bounds of 100..120 during the
diagnostic; those bounds remain unchanged. The corrected fixture uses strength
115, whose bottom edge is approximately y=36.609. It also independently checks
the native four-color tile, clipped rows, black surrounding pixels, and both
flips at widths 320 and 640. Every original transform and trail assertion remains.
All diagnostic offsets, aggregation, and logging were removed.

The experiment ran from `.worktrees/ai-develop-ci-smoke-fix-20261021` on the pinned
base above, with this queued command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LIBGL_ALWAYS_SOFTWARE=1 \
  __EGL_VENDOR_LIBRARY_FILENAMES=/usr/share/glvnd/egl_vendor.d/50_mesa.json \
  python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestPresentationSpritePixels#admittedRowsFlipsWholeBodyTransformsAndDistinctFrozenTrailVersionsRenderAt320And640' test -B
```

The diagnostic exited 1 with no skips because the out-of-range candidate stopped
the experiment after reproducing the original mismatch. Its purpose was causal
evidence, not validation of the corrected fixture. Final validation is recorded
below.

### Corrected-fixture validation

The final source in the same worktree, based on `bd042f080`, passed the following
affected-class command through CI's GLFW null-platform EGL path. It reported
37 tests, zero failures, zero errors, and two skips. The skipped methods were
`savedExpansionReuploadsResidentPaletteAndChangesActualFramebufferPixels` and
`paletteBackdropClearUsesTheDisplayedProfileAndExpansion`, which explicitly
require the surfaceless property. The original failing transform method executed
and passed. Queue waiting was 1,143 seconds; Maven execution was 27.367 seconds.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LIBGL_ALWAYS_SOFTWARE=1 \
  __EGL_VENDOR_LIBRARY_FILENAMES=/usr/share/glvnd/egl_vendor.d/50_mesa.json \
  python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestTitleInputOwnership,TestOrdinaryTitleScreenCommandEvidence,TestZoneLayoutMutationPipeline,TestLevelManagerModPatternWindows,TestAizMinibossBarrelShotChild,TestOwnedNativeAttackLifetime,TestPresentationSpritePixels' test -B
```

The explicit surfaceless and neighbor command below then passed 123 tests with
zero failures, errors, or skips. All three pixel methods executed. This includes
both `TestSonic3kLevelLoading` classes, AIZ1 skip, bootstrap, decoding, native shot
and flare graphs, title layout, white-fade binding, transform geometry, and private
graphics-bank ownership. Queue waiting was 1,100 seconds; Maven execution was
32.124 seconds. The two queue-wait intervals overlap and must not be added as
delivery elapsed time. Root ROM hashes matched the repository's canonical S1,
S2, and S3K identities; both injected animation slices also matched the S3K ROM
byte-for-byte.

Set `OPENGGF_ROM_ROOT` to the existing checkout containing those verified root
ROMs before repeating this command.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=lua5.4 LIBGL_ALWAYS_SOFTWARE=1 \
  __EGL_VENDOR_LIBRARY_FILENAMES=/usr/share/glvnd/egl_vendor.d/50_mesa.json \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestPresentationSpritePixels,TestSpritePictureTransform,TestPresentationGraphicsAccess,TestMasterTitleScreenLayout,TestWhiteFadePresentationBinding,TestAizMinibossFallingShotNativeGraph,TestAizMinibossFlareNativeGraph,TestS3kAiz1SkipHeadless,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.tests.TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=$OPENGGF_ROM_ROOT/s1.gen" \
  "-Dsonic2.rom.path=$OPENGGF_ROM_ROOT/s2.gen" \
  "-Ds3k.rom.path=$OPENGGF_ROM_ROOT/s3k.gen" test -B
```

The change-based plan selected 2,638 smoke classes and guards before the prose
updates. Focused affected and neighbor checks replaced that selection because
only seven test fixtures changed; runtime, build, selection policy, and workflows
were untouched. This is bounded fixture qualification, not a full smoke, full
suite, trace replay, or route-certification result. Independent review found no
correctness blockers. The initial available-title repair exposed a missing mock
launch profile and aspect setting, which were supplied before these final runs.
