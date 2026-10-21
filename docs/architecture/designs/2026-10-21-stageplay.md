# OpenGGF Stageplay

Status: approved product direction; first linear authoring delivery in progress.

## Outcome

Stageplay makes the engine and its supplied resources a directing environment for
sprite films, animatics and playable cutscenes. Both the in-engine editor and the
existing OpenGGF Editor desktop application must author the same scene files.
The user approved implementation on 2026-10-21 after explicitly requiring both
authoring interfaces. Existing branch, verification and delivery policy applies.

The first delivery is a complete linear scene workflow: choose a ROM-backed set,
cast stock characters, direct or record movement, select animations or fixed
mapping frames, author camera changes, captions, ROM sound/music and imported
voice clips, save/reopen, seek visually, render synchronized media, and play a
scene during a game with explicit completion/skip/cancel behavior.

## Shared ownership

- Engine-owned immutable scene data, evaluation, editing commands, history,
  persistence, resources and playback. No ImGui dependency in the engine.
- Separate Stageplay authoring controller/workspace alongside the level editor.
  Level mutations remain level edits; scene edits do not mutate terrain.
- In-engine controller-friendly HUD and desktop ImGui panels use that workspace.
  Both can complete the basic workflow. Layout/focus preferences are not scene data.
- Playback consumes the same document independently of either editor. Preview
  undo history is separate from runtime rewind. Export owns an independent session.
- Stock art is resolved through the supplied ROM pipeline. Scene files contain
  references and authored values, never bundled extracted ROM artwork.

The current `EditorWindowChrome.Host` is level-specific, so add an optional
Stageplay workspace capability and route by selected authoring workspace. Keep
input ownership explicit: Stageplay consumes authoring ticks and cannot paint
terrain accidentally. Frontends queue mutations for update, not render.

## Scene contract

Version 1 uses fixed 60 Hz integer frame coordinates, a bounded JSON document and
immutable lists. A scene includes name, duration, logical dimensions, set source,
actors, camera keys, caption intervals and sound cues. A set source identifies
game/zone/act, an optional exact ROM SHA-1 and an optional relative authored-level
project reference. Character actors identify their supplied game and character.

Actor keys contain position, animation or fixed mapping-frame pose, facing,
visibility, scale and interpolation. Camera keys contain origin, zoom and
interpolation. HOLD, LINEAR and smoothstep interpolation are explicit. Animation
phase survives position-only keys. Captions use half-open intervals. Sound cues
are events, dispatched by forward playback rather than pure frame evaluation.

The core limits are 36,000 frames, 32 actors, 100,000 total keys, 1,024 captions,
4,096 audio cues and 8 MiB encoded documents. Validation rejects duplicate IDs,
unordered/out-of-range keys, invalid/unsupported versions, unsafe asset paths and
nonfinite transforms. Save validates the staged file before atomic publication.
Failure must preserve the preceding document and file. Same-version unknown
fields fail explicitly rather than being silently discarded.

## Playback and handover

The visual evaluator is deterministic and side-effect free. Seeking, undo and
rewind restoration do not emit historical audio or commit campaign changes.
Forward playback schedules cues exactly once. Voice assets use bounded WAV/Ogg
loading, with project-relative resolution in tools and owner-confined assets in mods.

Staged playback owns its cast and camera. Live cutscenes require an explicit
admission/ownership boundary; existing controlled-frame support can hold native
physics and pass native input when inactive. The existing one-controller-per-module
restriction must remain visible and enforced. Live actor binding, if supported,
uses native position operations and explicit ending policy, never arbitrary trace
comparison rows. Completion and skip apply the authored ending once; cancellation
restores the entry state. Load/teardown cannot leave input or camera ownership held.

Recorded authored performances are distinct from parity trace fixtures. Baked
visual takes reproduce poses and motion; native-input replay additionally needs
matching simulation starting conditions. Do not claim one as the other.

## First delivery boundaries

Keep linear scenes, stock character art, simple transform/pose/animation keys,
caption text, camera keys and audio cues. Resource providers determine available
sets and animation frames and must report missing content clearly. ROM level kits
represent load-state terrain/background art, not all dynamic water/object/event
states; retain this visible limitation where that renderer is used.

Arbitrary branching, skeletal animation, automatic lip sync, collaborative editing,
general-purpose video editing and arbitrary code-to-timeline round trips are later
work. The first UI may use numeric/key-list editing; both interfaces retain the same
document capabilities. No separate Stageplay repository is required.

## Acceptance

1. Create and edit a scene through each interface; save in one and reopen in the
   other without conversion or dropped supported data.
2. Edit actor/camera/caption/audio data, undo/redo, and preserve saved dirty state.
3. Seek forward/backward to equivalent visual frames without firing cues.
4. Render ROM-backed actors and scenery; verify animation/fixed poses and camera.
5. Export complete video/audio with deterministic frame/sample accounting and
   explicit failure/cancellation cleanup.
6. Exercise cutscene start, finish, skip, cancel, replay/rewind and gameplay return.
7. Verify both input frontends prevent accidental terrain/gameplay input.
8. Verify focused behavior, affected ROM paths and change-based smoke once;
   investigate disputed failures with bounded matched baseline tests.

## Evidence and decisions

- Reuse `SceneRomArt` and scene canvas rendering instead of inventing a second
  asset pipeline. `levelKit` offers broader sets than the curated zone-picture API.
- Reuse the editor chrome pattern, but do not treat its existing level controller
  as a general authoring API.
- Rejected: separate frontend document formats, arbitrary code as the project
  source of truth, and gameplay-run API overrides that violate its stock-run policy.
- The current desktop repository is a prototype; integration needs its own tests
  and offscreen visual checks, not assumptions from its README.


### Implementation evidence (2026-10-21)

- The same version-1 demo scene packages as a validated mod (zero validator
  findings) and exports through `StageplayCaptureTool`. FFprobe and decoded PCM
  inspection confirmed 900 FFV1 video frames at 60 Hz, 320×224, 15.000 seconds,
  FLAC stereo at 48 kHz and exactly 720,000 sample frames. Selected frames were
  visually inspected; both ROM-backed actors and the set render.
- A separate imported-WAV export probe used a 24 kHz mono tone at frame 30,
  gain 0.5. The 48 kHz stereo result was silent for the first 24,000 sample frames,
  contained the tone for exactly 48,000 frames (peak 5,000), then returned to
  silence. This checks cue placement, resampling, gain and final PCM capture.
- S2 mapping frame zero has no visible pieces for Sonic and Tails. Early picture
  fixtures exposed this; standing keys now use animation 5 with pose −1, and both
  editor picture fixtures assert nontransparent resolved ROM pixels for every
  pictured actor. Explicit mapping pose 1 remains exercised.
- Native direction uses an engine-owned lease, not creator cleanup callbacks.
  Seven ROM-backed tests cover entry/final handover, cancellation, teardown,
  callback failure, owner quarantine and rewinding revoked capabilities.
- The external editor's first real render exposed font/range addresses retained
  after imgui-java released Java arrays. Its bundled fonts now load into native
  atlas-owned memory; a GC-before-upload/context-recreation regression and actual
  EGL screenshots pass without upgrading the pinned binding.
- Save As checks relative references throughout undo/redo history before changing
  asset roots. Export cancellation retains process descendants before terminating
  the parent and removes only its owned snapshot and staging paths.

The tested implementation is currently the pinned engine base plus task changes;
final candidate commit, combined smoke results and delivery are recorded in the
[implementation plan](../plans/2026-10-21-stageplay.md). Media review artifacts are
outside the repository, under the task's `stageplay-delivery` directory.
