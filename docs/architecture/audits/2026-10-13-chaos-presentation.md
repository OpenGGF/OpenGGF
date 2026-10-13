# Chaos presentation implementation and qualification

Implementation starts from shared contract `7ec4bfa0ddf741960f7091872fa21f2d41ba353b`
in `feature/ai-sonic3k-chaos-presentation`. The scene, sprite and HUD policy
record schemas remain unchanged. Native object producers expose immutable
`ObjectPresentationMetadata` roles and anchors; the shared renderer does not
infer badnik/pickup identity from object classes.

## Implemented behavior

- 4: smooth private sprite hue rotation; native palette textures remain owned.
- 10: at most four admitted native body/appendage poses, at most 256 tiles per
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
draws. Pause/hold/rewind preserve it. Ordinary-play departure, load, failure and
retirement release only its resources and clear its contributions.

## Decisions and rejected approaches

Keeping old DPLC addresses for trails was rejected: queued GL commands would
all sample the last upload at those addresses. Each owner instead leases one
1,024-slot private bank, with separate slots for each of the four poses.
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

## Verification and limits

The focused CPU and owned offscreen EGL checks are designed to cover native and wider viewports,
source flips and scanline clipping, whole-picture pivots, distinct trail art
generations, HUD label/value pairing, deterministic rewind/forward replay,
expiry and ownership cleanup, private scene pixels and fault restoration.
Exact completed commands, outcomes and skips will be recorded after execution.
The implementation checkpoint is explicitly unverified while the coordinated
focused request remains pending. Build-only `LUA_BIN=/usr/bin/lua5.4 python3
tools/testing/maven_queue.py -Dmse=off test-compile -B` passed on the repaired
source (69 seconds reported by Maven); this executed no tests. The current
0.7 candidate API pin matches a fresh canonical snapshot; the descriptor and
`ModApiVersion.CURRENT` remain unchanged. The coordinator may integrate this consumer
checkpoint to unblock combined compilation; it is not a passing-test claim.

Preflight with the default Lua command found the host's older Lua selection.
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base
7ec4bfa0ddf741960f7091872fa21f2d41ba353b --preflight` passed Java 25, Lua 5.4 and
PowerShell. This did not run smoke or any engine tests. S1 World REV01, S2 World
REV01 and S3&K locked-on root ROM files were verified against the repository's
CRC32 and SHA-1 values and passed by absolute property paths to the focused run.

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

This work does not certify donor/custom character routes, stage gameplay,
every S3K boss/act encounter, or every detached background profile. Native
BADNIK/PICKUP overrides and additional detached ROM art are integrated by the
native pack owner; the final combined route matrix belongs to the coordinator.
Retained sprite/HUD packets that predate an activation receive producer labels
at the next native publication. No extra native execution or SAT rebuild is
performed merely to start a picture effect.
