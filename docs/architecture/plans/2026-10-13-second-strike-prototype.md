# Second Strike — normal-campaign difficulty prototype

Integration base: `develop` `13add5b0b918a1f7ff7e343c8ccc858ad4527d85`.
The user authorized implementation of all brainstormed difficulty concepts.
Post Two Ambush remains a separate useful encounter concept; its trial flow is
not the template for this delivery.

## Product boundary

Build a maintained, packaged S3&K patch named **Second Strike**. The first
coherent slice is ordinary Angel Island Act 1, including its native fire bank
reload, first boss, results and subsequent progression. Keep stock physics,
native lives/death/game-over, physical checkpoints, save-slot UI, emerald
rewards, stage returns and act transitions. The launcher uses a separate player
configuration/save root while retaining normal save behavior. No custom course
controller, instant retry menu, forced fresh position or scripted victory.

| Concept | Working demonstration | Acceptance |
| --- | --- | --- |
| Badnik roles | Native-art spotter, guard and artillery replacements | Cooperation changes a real encounter; destroying the caller ends new assistance |
| Provoke/dodge/punish | Committed dash/volleys, visible brake/reload openings | Native attacking, shield deflection, hurt, destruction, score and life behavior |
| Meaningful rings | Curated original ring groups and recovery footholds | Real scarcity, usable safe/exposed routes, attainable native bonus entry |
| Dangerous shortcuts | Hardened enemies on existing fast/upper paths | Readable fast solution and viable cautious alternative |
| Environmental combinations | Native enemies beside existing vines, logs, slopes and landings | Native terrain/mechanics and transition objects stay intact |
| Boss second trick | A bounded delayed extra breath wave late in Fire Breath | Native HP, arena, defeat, results and transition; cancellation on killing hit |
| Character opportunities | Existing momentum, flight, rock and glide paths | Native Sonic, Tails and Knuckles abilities; support advertised only with observed cells |
| Worthwhile stage detours | First Chaos board turn gates and three-spring Gumball variant | Genuine native play, unchanged rewards/return; other layouts/families remain stock |

Prototype scope is the authored opening and representative stage variants, not
a completed whole-game hard-mode campaign. It must continue normally into
unmodified later content. Native disabled behavior is the reference; new rules
exist only when the tagged patch/service graph is active.

## Ownership and implementation

- Root: normal module/title delegation, launcher/packaging, native class-preserving
  decoded placement admission, owner-scoped boss/stage seams, candidate API pin,
  documentation, integration and combined validation.
- Enemy worker: `secondstrike/enemies/**`, `TestSecondStrikeEnemies*`; bounded
  recreated object state, native art/SFX and truthful attack/vulnerability cues.
- Route worker: `secondstrike/routes/**`, `TestSecondStrikeRoutes*`; immutable
  original placement/ring selection, native anchors and character opportunities.
- Boss/stage worker: `secondstrike/boss/**`, `secondstrike/stages/**`, corresponding
  tests; authored policy values and real native behavior/restore evidence.

Each worker has its own worktree/build directory. Shared engine contracts are
sequenced by root before consumer builds. No target copying, foreign process or
queue-policy intervention, shared-desktop focus/input, or ambient audio.

The existing `LevelPatch.apply()` returns a `MutableLevel` snapshot. That loses
the concrete `Sonic3kLevel` required by native AIZ fire transitions. This approach
is rejected for the prototype. An explicit placement-only admission path must
preserve the actual native instance, publish validated object/ring edits once at
load, retain identities and leave geometry/art/transition ownership untouched.

New narrow S3K service policies select a bounded late-breath follow-up, validated
ROM-layout hazard edits, and bounded Gumball spring offsets. Absence retains
literal native behavior. Creator arrays are isolated, copied and validated inside
their trusted callback boundary; invalid returns must identify the true owner.
Pending boss follow-ups are captured with native object state and cancelled on
fatal hit/defeat/load. Stage changes are admitted at native initialization, not
reapplied each frame or after a rewind restore.

## Verification and delivery

Use Java 25, Lua 5.4 and original absolute main ROM paths. Focused tests establish
enemy phases/children/rewind, placement provenance/native class preservation,
stage validity/reward/return and boss scheduling/cancellation. Keep the four
mandatory S3K startup/loading checks green. Inspect the pinned-base category plan;
shared/public-contract changes require the normal change-based **smoke** selection
and separate fresh guards. Full-suite is release-only and is not requested.

Complete a cold native-input opening route plus bounded character/width/lifecycle
cells and actual stage/boss demonstrations. Offscreen production captures use
surfaceless EGL, unset desktop display variables, a private runtime directory and
null audio. Inspect state before images; record real PCM contributions and use
declared positioned setups only as supplementary local evidence. No trace rows
hydrate gameplay. Preserve captures outside Git and consume temporary diagnostics.

Before final delivery, reconcile the actual updated destination, compare any
disputed failures by identity/full assertion against bounded matched baseline
tests, integrate into the main checkout's current branch without switching it,
push only that branch, and account for all owned child changes before cleanup.
Execution outcomes and observed support cells will be appended here.

## Observed prototype and ownership outcomes

The lead compiles 3,850 production sources using Java 25. The ROM-free creator
jar passes the real packager with zero errors and eight deliberate internal API
warnings: native render priority, points/animal destruction helpers and title
graphics lookup. These are compatibility limits, not a published API promise.
The compiled normalized candidate surface has 20,123 lines: 22 additions and zero
removals against the pinned base. Candidate status/version remains `0.7.0`.

Native placement admission passed 35 focused legacy/transaction cases and three
real owner/ROM consumer cases. The actual `Sonic3kLevel` keeps its geometry and
native AIZ fire-terrain mutation consumer. Native plans admit bindings only;
retained rings must be original record instances in their original order. The
two inventory-gated plans retain 68/104 forest rings and 45/69 early fire rings,
with 10/3 owned enemy placements. Later physical Act 2 objects/rings are untouched.

Enemy phases, cooperative queries, bounded projectiles and recreation passed
seven focused cases. Native boss/stage cases passed 14 checks, then 23 stage and
existing owner checks. The completed wrapper/eligibility follow-up passed 60
checks, including canonical-owner faults, malicious layout mutation, retained
array isolation, proxy aliases, unrelated controls and VM-fatal identity. Two
new test sources initially used a package-private helper across package boundaries;
moving the fixture to its existing owner package repaired compilation without
widening engine access. Creator static Lists/Sets and an array were rejected by
the real packager; invocation-local immutable authoring values replaced them.

| Observed cell | Native outcome | Limit |
| --- | --- | --- |
| Sonic + CPU Tails cold phase-2 recipe | 8,746 ordinary inputs; fire 4598, physical post 4814 with 30 rings/four bonus stars, cue 6993, extra wave 7013, results 7788, playable Act 2 at 8745 | Fixed exported BK2 replay matches all non-input state rows; no death or post-intro hurt |
| Solo Sonic cold phase-2 recipe | 9,193 ordinary inputs; same cue/wave, results 7689, playable Act 2 at 9192 | Fresh exported BK2 matches all non-input rows; no follower, death or post-intro hurt |
| Blue Sphere first board | Twelve admitted red gates, 102 blues, native turn/jump, failure/results and normal level return | Direct entry; no emerald completion claim; matched stock offscreen draw also lacks Sonic's silhouette |
| Gumball | Three springs, native depletion, real 1UP (lives 3→4), natural exit and level return retaining life | Direct entry; REP generation observed separately, restoration covered by owner tests |
| Tails crown | Native flight and one original crown ring collected, alive through 380 frames | Positioned local cell, not a whole Tails route |
| Knuckles forest rock | Unrolled walking breaks native rock #69; native glide follows | Positioned local cell, later native enemy death; climbing remains unproved |
| Knuckles fire-bank glide | Native glide, alive 255 frames | Direct Act 2 native bounds prevent the requested earlier rock access; no camera override |

Both successful cold recipes use the ordinary intro (including its native punch),
real hurt/collision owners, fire reload, boss and results. Controller authoring
repaired the stock movie's approaches to the new guard, vines and rock steps;
runtime physics and fixtures were never tuned to make the route pass. A rejected
lower-bank wall heuristic was removed after the native Sonic upper-route approach
completed. Positioned cells are supplementary and never stand in for cold reachability.

Graphics were actually surfaceless EGL on AMD's integrated Raphael/Mendocino
renderer, GL 4.6 Compatibility/Mesa 26.2.4, not an asserted discrete GPU. Display
variables were unset; runtime/config/save directories were private and host audio
used the null device. Native SMPS PCM is offline output, not speaker certification.
Gumball and Blue Sphere moving witnesses retain 593/1,143 frames with matching
48 kHz stereo audio. Useful state, input, images, movies and exact launch hashes
live outside Git under the task's explicit scratch directory.

The continuous pair witness retains all 8,746 frames and 6,996,800 stereo PCM
frames. Every non-input state field matches the lightweight cold replay. Its
late-boss tell is a brief native white/blue barrel puff, followed by four native
parent-attached, staggered flames; it is not an independent travelling projectile
or a large warning banner. Lossless audio has nonzero AC variance in intro, level,
boss and results windows. The 145.767-second moving derivative and canonical
pair/solo proof are retained under the external `second-strike-route-survey`
task directory, with the selected cue/wave images and audio analysis.

The initial upper-left title badge overlapped the native title's Sonic artwork in
frame 899. Its replacement occupies the free footer strip between Press Start and
copyright; final pixels were inspected. An owned offscreen menu probe observes
native title → data select, Right selecting empty slot 1, and fresh Start emitting
`NEW_SLOT_START` for Sonic+Tails at frame 1622 through the normal fade. It records
the callback action, not an Engine launch or save. The original title-first
capture stalled because `HeadlessGameBoot` omits Engine's data-select launch
callback and `GameplayCaptureSession` lacks data-select drawing. The probe uses
the native provider's draw path; shared capture/input/fade contracts are unchanged.
A new delegation fixture also incorrectly expected two default data-select
factory calls to share identity; the corrected fixture supplies one known provider
and verifies forwarding.

Final build-only SDK/Javadoc/binary artifact verification passed; its tests were
deliberately skipped. Remaining delivery gates: the repaired delegation fixture,
bounded native save/launch tests, change-selected smoke/guards, destination
composition and main integration/push. No full-suite or finished delivery is
claimed here.
