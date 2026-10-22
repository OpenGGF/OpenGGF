# OpenGGF Stageplay

Status: first linear authoring implementation verified for integration; validation limits are recorded in the plan.

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

The mutable authoring workspace belongs to `editor.stageplay`. Preview sessions
receive an immutable document, playhead and asset root, with explicit error/status
handoff to the host. Runtime Stageplay has no dependency on editor or tool classes.
Saved sets load through the internal `level.resources` authored-level decoder
port; the editor's `.ogfproject` provider supplies an isolated level snapshot.
Engine-classloader service discovery selects that typed provider without moving
project persistence into runtime playback or exposing it as creator API.

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
- Architecture checks at `9ba9a034e` rejected the initial runtime imports of the
  editor workspace and project store. Moving all preview/export classes into the
  editor would instead violate its audio/data and mod-host ownership boundaries.
  Move only the authoring workspace, decouple the preview host, and use a typed
  level-resource decoder for saved sets. Preserve the cycle guard and legacy
  cluster unchanged; admit only the resulting one-way dependencies with narrow
  owner checks. Final affected guard evidence is in the implementation plan.
- The broad warmed-audio regression exposed a per-frame iterator allocation in
  the new additive voice-source loop. Indexed traversal preserves the existing
  zero-allocation presentation contract without changing mix order or ownership.

The tested runtime implementation is `d74015656`;
final candidate commit, combined smoke results and delivery are recorded in the
[implementation plan](../plans/2026-10-21-stageplay.md). Media review artifacts are
outside the repository, under the task's `stageplay-delivery` directory.

## Directing extension (2026-10-21)

The follow-up adds sampled accessory poses, camera directing/shake/zoom, temporary
window constraints, scene transitions, and a noninteractive authoring workflow.
Version 2 appends `cameraEffects` and `sceneChanges`; strict version-1 reads migrate
with empty lists, and the original Java constructor remains available. Every scene
boundary has actor and camera keys. The evaluator holds the outgoing segment before
a boundary and restarts animation phase after it. Shake is deterministic from frame,
frequency and seed; the unshaken camera remains available for native handover.

A transition is a cut or a bounded fade/wipe straddling the switch. At its midpoint
the incoming set is selected beneath full coverage. Rendering overlays transitions
after captions. All sets preflight before playback; mod-authored levels and voices
share a 64 MiB encoded asset budget and typed LoadOps. A native binding rejects
cross-set handover before capturing native state. Staged scenery can cross games
without loading or mutating the held native world.

S2 Obj05 reuses Tails' body art, so testing only for a separate tail bank was wrong.
The ROM provider now samples accessory relationships using shared native selection
and animation tables. Stageplay consumes a generic pose/latch API; it has no new
game-name branch. Fixed mapping holds infer an accessory from a matching ROM script.

Both frontends expose camera mode, effect/scene CRUD and atomic boundary insertion.
The host owns temporary 16:9/minimum sizing, monitor fallback and restoration; the
scene's pixel dimensions remain independent and letterboxed. Gameplay configuration
is not rewritten.

The authoring tool compiles shot-local JSON into the canonical v2 document. It adds
boundary anchors and hides absent roles, rather than requiring agents to calculate
global frames or navigate menus. ROM catalog queries expose actual scripts and set
bounds. Compilation and inspection require no ROM; capture validates resources and
uses the existing no-device headless host. Rejected: a second playback evaluator,
implicit cross-game native loads, copying ROM art into production sources, and
schema coercions which could silently move cues. This initial one-way limitation
is superseded by the lossless screenplay revision below.


### Dialogue and visual feedback revision

User review rejected fractional nearest-neighbour zoom, idle performances and
unclear speakers in the first short. Camera zoom now holds until its next key;
position interpolation remains independent. New editor keys use integer framing.
The revised production uses zoom one, entrances, reactions, movement and exits.
Rejected: a global GL_LINEAR switch, which would blur text and risks straight-alpha
sprite fringes; smooth animated zoom needs a separate bounded world presentation pass.

Version 3 appends document-owned dialogue, typer and skin lists. Strict v1/v2
migration inserts empty lists; legacy constructors remain. Speaker identity is a
real actor reference or explicit offscreen name, independent of caption text.
Typing samples a grapheme reveal schedule from scene time; full-text wrapping
precedes reveal, and names appear immediately. It adds no mutable clock or draw
side effects. Profiles and optional nine-slice/portrait PNGs are portable to both
editors, mod registration and offline export. PNG headers and decoded aggregate
pixels are bounded before allocation, and creator assets retain the existing
ModAssetRoot/typed LoadOp ownership and shared budget.

Chosen over a new executable provider registry: portable data meets the requested
speaker styles, text reveal and dialogue decoration without loading mod code in
the external editor/exporter. Programmable glyph callbacks, custom fonts and
automatic typing audio remain outside this surface; timed sound cues remain available.
There is no creator-reported owner or failure-swallowing fallback. Missing resources
and overflowing dialogue fail preflight. The corrected promo skills distinguish
authored timeline evidence from observed gameplay and require moving-footage review.


### Physical puppets and intent direction

Follow-up review rejected the hand-keyed jump and requested default floor support,
real jumping, and players directed toward a destination at a chosen urgency. The
new performance owner uses the production playable tick, movement, animation and
terrain collision. It does not fit a parabola or write destination coordinates
into each frame. Move-to and stop cues produce controller inputs; jump cues hold
and release the native jump button. Urgency is a desired fraction of the native
maximum speed, expressed through acceleration/coasting/braking inputs. A target
is an intention, not guaranteed pathfinding: actual arrival and lack of progress
are visible to authoring tools, and a blocked character never teleports.

Each puppet owns a construction-bound runtime context and private collision,
camera, timer, state and silent-audio peers over detached ROM/authored terrain.
The existing gameplay context remains the default for ordinary characters. A
collision-only level initializer avoids gameplay boot, art/events initialization
and global session swapping. The actor's module supplies native movement rules
and art; the set's module supplies terrain layout semantics. Initial placement
snaps to a nearby valid floor by default, with native radii, headroom and explicit
collision path; subsequent floor following and airborne landing use real sensors.

Canonical schema 4 adds document-owned puppet tracks without redefining legacy
ActorKey meaning. Version 1–3 documents migrate with no puppet tracks, preserving
old choreography. New actors and version-two screenplays default to physical
puppets; manual choreography remains an explicit option. Existing boundary keys
place actors at shot starts; interior physical position tweening is rejected.
Move-to/stop commands and independent jump edges are portable data. One shared
runtime resolver produces actual mapping frames, flips, velocities and accessory
state for editor preview, playback, capture and native handover. The pure timeline
labels physical poses unresolved; it must not present an authored goal as a
measured position. Seeking restores/replays isolated native state deterministically,
and repeated reads/draws of one frame do not advance the simulation.

## Native-world and complete-cast continuation (2026-10-22)

New version-six documents use one native session for both LIVE and TERRAIN_ONLY.
The latter clears initial layout objects and rings before the native load; it is
a contents choice, not a different physics, camera or renderer. A clear is not a
permanent ban on later event-created objects. Native world ownership remains in
the isolated process and production LevelFrameStep; held interactive playback
uses value poses and the existing visit-bound CourseControl player lease.

Strict v1–v5 migration records LEGACY_TERRAIN_ONLY or LEGACY_LIVE, preserving
previous choreography, simulation, donor constraints and presentation. Older
screenplays compile through that same migration. New version-four screenplays
select the current model. Compatibility is retained explicitly rather than
silently replacing old detached physics with object/event simulation. Saved sets
are isolated decoder snapshots; each current native performer has a donor module
context over the shared world's terrain, collision, clocks and rewind owners.

Editor preview/export transport exact logical-ROM descriptors, including source
windows and fingerprints. Workers reconstruct immutable readers without joining
or copying ROM files. Authored sets use a confined editor root or the original
bounded bytes from the mod registration transaction, never the worker cwd.

Presentation composes native viewport tiles into an opaque world texture, bounded
to 4096 pixels per side and 8 MiPixels. Fractional sampling operates only on the
completed world. Posed cast joins that picture before sampling; captions, dialogue
and transitions follow it at output resolution. No global texture filter changes
or straight-alpha sprite resampling are admitted. Native 224-line buffers,
physics, camera dimensions and ordinary rendering keep their owners. Directed
background phase and separated-ensemble activation are subsequent camera work.

The user confirmed that Free means the real ROM routine, including arena-bound
boss initialization that ignores a placed anchor. Do not add object-specific
spatial adapters. Both editors, coverage and authoring diagnostics must display
“Free: native arena position, anchor ignored” and cite the owning initialization
routine. An unsupported native set/context fails explicitly. Puppet/Pose
relocation must preserve composite child relationships; visual direction must
never be described as relocated Free collision or arena behavior.

Rejected: replacing legacy worlds silently; copying joined ROM images to temporary
files; forwarding only whole-image paths; a global GL_LINEAR switch; resizing or
reticking native scroll/physics owners to generate a larger picture; guessing
object motion from private field names. Verification, evidence and final commit
hashes are recorded in the live-world plan.

Directed object modes use visual puppetry: only Free runs native routines and
world effects. Puppet/Pose suspend native parent/child AI, drive position and
ROM-backed pure animation/mapping presentation, and preserve native part offsets
as render followers. State-machine-only appearance has an explicit held/authored
frame fallback and cited per-object coverage exclusion. Never relocate the picture
while leaving native effects active. Full private native graph ownership is deferred
with the write-boundary analysis in the live-world plan.

### Complete native cast contract

Canonical v7 appends object roles and timed world commands; strict v1–v6 migration
adds empty tracks, preserving old execution policy and data. Screenplay v5 carries
shot-local object keys/actions and clear/removal cues. Object source context and
portable layout tuple are document data; runtime slots/generations remain internal.
Released animals have an explicit source kind using their existing factory instead
of aliasing an unrelated S3K layout ID. The shared authoring forms expose every
new field in both frontends.

Registry enumeration is generic and constructor-free. Its complete ID/context
report distinguishes registration, context acceptance, loaded ROM art and actual
visual graph readiness. Game-owned metadata declares native arena restrictions;
a semantic boss role alone does not establish that an anchor is ignored. New Free
roles require matching native game/zone/act, giving a generic actionable rejection
for incompatible arenas. No object-specific spatial relocation adapters are added.

The object-manager direction owner freezes graph routines, contacts, unload effects
and native drawing, separately from gameplay mutator policy ports. Immutable visual
parts carry captured ROM patterns, palette values, mapping alternatives and pure
animation scripts. Stable child paths follow parent creation relationships; changing
frame or position cannot invoke the state machine. Missing/gated initialization and
state-dependent appearance are explicit exclusions, with held/authored-frame fallback
and citations. New constructor-ready visual preparation uses a short-lived owned
offline context, retired before the scene; no continuously ticking private boss graph
is installed. Existing initialized Free graphs can be captured at a directed mode key.
Clear/removal suppression and visual state participate in rewind with native state.

Directed visual capture preserves native per-tile terrain-occlusion masks and row
clipping. It never infers that a missing SAT-control draw is an invisible child:
active mask/control producers cause an explicit unsupported-appearance result.
For example, SKL's DEZ miniboss and native SpriteMask entry carry a conditional
exclusion citing `Obj_SpriteMask / loc_86308`; the S3KL alias does not inherit it.
Templates retain child creation paths across rewind, including ordinal gaps left
by deleted children. Draw-owned visual counters remain read-only during capture.
Ordinary Free rendering and native SAT behavior keep their existing path.

### Directed camera presentation and ensemble activation (complete-cast phase3)

Current native worlds interpolate zoom with camera position using HOLD/LINEAR/SMOOTH.
Only the completed opaque world framebuffer is filtered; text/dialogue/transitions
remain outside that pass. Existing4096-side/8MiPixel bounds remain preflight gates.
Legacy policies retain their previous held/fixed zoom and native-phase backgrounds.

A game handler captures immutable animation/event phase at the real native update.
A pure projection composes that phase for a directed camera and complete bounded
raster. It never initializes, updates or restores a gameplay handler while drawing.
The world pass samples once and slices that result across native viewport tiles;
line/column origins remain global, including partial16pixel columns. Cinematic
foreground/background CPU descriptors and GPU residency are private, preserving actual native
persistent cells/ring origins and extending exposed regions through the ROM level
pipeline. The internal Stageplay module opts into snapshot capture; ordinary modules
never allocate these directed phase/descriptor snapshots. Descriptor images are
bounded to 2097152 cells (8 MiB) per plane. Ordinary gameplay retains its native
publication, physics and picture.

A session-owned, rewind-captured activation policy supplies disjoint native-sized
windows around performers. Supplemental layout admissions use portable identity,
the existing native factories/SST, creator/removal gates and remembered destruction
state. Standard visibility/contact/retirement helpers admit those windows without
moving the gameplay camera. Free routines keep their native state machines and
world effects; native damage/destruction is never suppressed. Object slot exhaustion
fails explicitly and suggests clearing unnecessary objects or splitting the shot.
S1 supplemental admissions retain the canonical layout even when initial contents
are cleared or posed, project the native counter from both held loader cursors,
and preserve lower bits plus loaded/kill/remember state. Counter aliases or shipped
FixBugs=0 cursor drift fail explicitly rather than renumbering native identities.
Only geometric culls admit supplemental windows; semantic retirement remains native.

## Lossless screenplay revision (phase 4)

Canonical version seven remains the sole playback contract. Screenplay version six
adds literal cue/command/transition IDs, typed exact track-order permutations and
whole cross-shot intervals owned by their starting shot. A deterministic decompiler
emits ordinary shot-local fields and verifies recompiled document equality. It
preserves unused profiles and legacy execution policies as well as current object
casts. Original source formatting/comments/shot names are not document data.

The screenplay input cap grows to 32 MiB for this expansion; old screenplay versions
retain 8 MiB and canonical files remain 8 MiB. Reject opaque canonical blobs, splitting
intervals (which would restart reveal/phase) and generated prefixes on already valid
64-character IDs. Both frontends share import/export actions and relative-asset
semantics. Import creates an unsaved canonical document; export preserves save and
undo state. This adds no public Mod API surface or playback ownership change.
