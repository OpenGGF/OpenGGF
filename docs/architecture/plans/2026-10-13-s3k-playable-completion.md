# S3K playable routes and endings, 2026-10-13

The requested completion target is playable game routes and endings, fixing
blockers first. Strict trace, audio and full-scene pixel parity remain separate
acceptance claims. The integration base is develop
`5e2f51f07917d5eff8ac120007dac55327f93f32`; unrelated main-workspace changes and
other worktrees are preserved.

## Work and acceptance

| Lane | Current evidence | Required result |
| --- | --- | --- |
| MHZ | Authored Tails/team pulley checks, wide world restoration and paired FBZ history isolation fail in the October stock audit | Reproduce current identities, distinguish runtime defects from entry/input/observation errors, and complete affected cold routes with restoration and forward replay |
| LRZ | Tails/team routes die; Knuckles/wide HPZ endpoints disagree | Resolve first blockers against ROM owners, qualify actual live destination and history isolation, retain unqualified breadth explicitly |
| SSZ | Sonic/team replica routes die; Tails child slots differ on replay and DEZ endpoint disagrees; wide team continuation is incomplete | Repair reachable native object/route behavior and prove production DEZ handoff; complete the outstanding wide continuation |
| DEZ | Positioned incoming final fights and emerald-team route die; ordinary routes disagree at ending entry | Qualify the actual full-load boundary and live roster, repair the first real fight blocker, and exercise normal and DDZ departures |
| Ending | No native rescue/credits owner for internal `$0D01`; Knuckles final defeat holds before native ending children | Implement the native Sonic/Tails rescue, Knuckles ending, progress-dependent credits/post-credits and terminal handoffs using ROM assets and registered rewind state |

Existing September milestones are historical evidence, not current passing
qualification. The October stock audit records the concrete inherited failing
identities. Early-zone matrices and trace frontiers are inspected to preserve
AIZ → HCZ stability; historical dedicated-trace failures do not automatically
become playable-route blockers.

## Native ending decisions

The initial generic-provider hypothesis is insufficient. In locked-on source,
`S3Credits` is an `rts`; DDZ requests an ordinary level load of `$0D01`, while
Knuckles' SSZ2 ending calls `sub_5B18E` inside its level. The implementation
must follow those game-owned event/object boundaries. Native progress is the
`-2/0/+2` marker from `sub_5FE82` for incomplete/Chaos/Super emerald progress,
not a substituted good/bad flag.

The owners to connect are `Ending_ScreenInit` / `Obj_5D86A`,
`loc_5E6C0`, `sub_5B18E`, and `Obj_5EF68`. Resource bytes must come through the
ROM pipeline. Source tables supply stages, timers, art, palette and mapping
selection; controller fixtures are comparison/verification inputs only.

## Validation and delivery

Use short, independent regressions for each cause, followed by the affected
cold controller routes and complete-world restore/replay windows. Observe the
current live player and actual load/title/fade boundary before interpreting a
fixed destination coordinate as a gameplay defect. Inspect moving ending
output at native and representative wide widths.

Combined validation uses the change-based smoke selection against the pinned
integration SHA and separate guards where required. Record class count, cost
and stopping rule before launching it. Java 25 is available and the existing
locked-on ROM has SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`.
Preflight requires `LUA_BIN=/usr/bin/lua5.4` on this host; with that setting
the actual tool preflight passes. No release-only full suite is requested.

Update the owning per-act matrices and shared coverage/backlog only from
completed evidence. Merge verified lane commits into the development tree,
then into the main workspace's current develop branch without switching it;
push only integrated develop. Account for every owned worktree change before
removing fully merged task worktrees and branches.

## Execution record

Current investigation and queued reproductions are in progress. No route or
ending completion is certified by this plan.

The first confirmed shared defect is the omitted fresh title presentation:
`FreshLevelTransitionBoundaryController` parks decoded destination players at
zero until its assembly is completed. `skipPendingInitialTitleCardPresentation`
consumed the request without completing that assembly. The short capture-session
regression fails on the untouched runtime with an orphaned boundary. Independent
LRZ and SSZ probes find the same current live P1 (not a cached reference) behind
the pending boundary. In HPZ, its zero Y also selects the upper-route camera
minimum. A candidate handoff now completes the assembly before the skipped
animation/PLC tail. The candidate passes seven cases with no failures, errors
or skips using `maven_queue.py --lean -Dmse=off` with the absolute locked-on
ROM and selectors `TestGameplayCaptureFreshLoad`,
`TestFreshLevelBoundaryRewindHeadless`, and `TestS3kLevelContinuationHeadless`
at 14:09 BST. Combined smoke and delivery remain pending.

Rejected explanation: replacing `GameplayCaptureSession.player()` references or
correcting endpoint coordinates would not publish destination state and would
retain the wrong HPZ camera branch. Cached/current/focused player identity is
equal in the independent witnesses.

MHZ baseline reproduction completes five cases with four assertion failures and
no errors/skips: authored Tails and team late-pulley ownership, paired FBZ history
isolation, and wide frame-19500 runtime deformation bytes after restoration.
The first short load regression completes one failure with no errors/skips.
Those are completed negative evidence; queued commands are not passes.

The [ending route matrix](../validation/levels/s3k-ending.md) records the native
rescue, fixed-slot plane/master/catcher, shared V-int/RNG cloud emitter, screen
and scroll RAM, AIZ/SSZ resource handoffs, progress-dependent credit decoder,
Knuckles flight and terminal obligations. Multiple build-only test compilations
pass. The initial four ROM-backed credits decoder cases pass; composed behavior,
new short regressions and moving output are still pending.

The first ending focused checks exposed two implementation defects. The rescue
plane was assigned a slot outside the current dynamic allocation window: native
`loc_5D96A` addresses `Dynamic_object_RAM+object_size*5` and `*45`, whose global
SST indices are 8 and 48 because that base follows three slots. The intermediate
5/45 translation also used the wrong base and was rejected directly from the
constants before verification. The actor-count regression rejects the original
missing-plane implementation. Separately, restoring an initial ending snapshot
ran the derived-parallax callback as a native background tick, changing its
captured `MIN_VALUE` dispatch marker and accumulator. The first correction, a
next-update flag on the handler, was rejected by the completed seven-case rerun:
two restoration assertions still changed the initial dispatch marker. Before the
first parallax update, the manager's active handler is unset, so that handler
never received the flag. The replacement is an internal `ScrollOutputRebuilder`
hook selected explicitly by post-restore reconciliation, including initial
handler setup; its focused rerun remains pending. Four pure deformation
arithmetic cases and three animal lifecycle
cases pass with no failures, errors or skips; these do not certify the complete
ending. The production D01 lifecycle cases follow actual phase boundaries,
both credit parts and Start return, rather than stopping at a fixed video length.

The MHZ wide movie enters `SPECIAL_STAGE` at input 19466 after missing the late
pulley. The mode changes the active camera while retaining the outgoing level.
Capturing that level registry and restoring it against the special-stage camera
explains the frame-19500 deformation mismatch; it is not evidence of a scroll
arithmetic defect. The route needs ordinary controller repair. Level replay
checkpoints now reject mode and fresh-load crossings before interpreting a
snapshot mismatch. The two unchanged LRZ Knuckles/wide-team routes reach live
Hidden Palace and pass their whole-world replay checks after the common assembly
fix (two cases, no failures, errors or skips).

The replacement wide MHZ movie has 29744 inputs. A fresh uninterrupted production
replay reaches the FBZ load at 28981 and released ordinary movement by the tail;
the whole-world replay test remains queued. Tails needs one additional held-right
input before its first Act2 jump: its 35815-input replacement reproduces the old
late pulley and flight positions with that input shift, requests FBZ at 35454,
and reaches the released destination in a fresh uninterrupted replay. The team
movie's first jump can be repaired similarly, but later curled-vine/badnik
navigation remains open. These production authoring observations used immutable
classes from `8438d7040`, the verified locked-on ROM and ordinary inputs only;
they are not substitutes for queued route/rewind qualification.

Ending footage exposed another composition defect despite nonzero CPU art and
ready renderers: the rescue plane's 6845 nonzero pattern pixels never reached its
virtual GPU atlas. Resident VRAM overlays mutate `Pattern` objects in place;
the identity-only cache does not observe those writes, and the new sheets had no
registered source tile range for the existing DMA refresh path. The replacement
registers that range rather than introducing another upload clock or art job.
An independent rendered-body check at 320/800 now accompanies the range oracle.
A separate pre-entry restore witness finds P1 `(96,300)` changed to `(0,0)` by an
uninitialized rescue director. Its restore callback now publishes scripted
presentation only after routine 0 has claimed P1. Verification remains pending.

The LRZ ring-counter investigation rejected two tempting explanations before any
runtime patch. Native LRZ saves counters before `StartNewLevel` and restores them
at its destination event. The observed sequence is ring5 before the load, ring0
on the load-producing tick, then ring5 on the first destination event and later
ticks. Neither the ordinary clear alone nor a suspected deferred-assembly erase
explains the released boundary. Retain the native carry assertion and repair the
controller approach to the actual fight.

Main develop advanced to `b3464b31b` with two documentation-only commits. Fetch
and fast-forward pull report up to date; root merge `4846db63e` reconciles the
coordination guidance and Chaos research. Runtime source is unchanged by those
commits, so the pinned pre-task source baseline remains `5e2f51f079`.

The MHZ team repair now has 44004 ordinary inputs. A fresh uninterrupted
production replay (immutable `8438d7040` runtime) passes all inputs without a
death, reaches the actual FBZ load at 43234 and ends with live Sonic `(201,1219)`
and a registered Tails follower in the released destination. The repair uses a
spindash around the lower loop, sustained jumps past the upper spikes, a release
on the swing vine's rightward arc, and a brake into the shared final pulley.
Both players first occupy the shared handle at 35047 and reach its top at 35734.
Repeated short jumps and release at the vine's low arc were rejected: they lose
rings at the badnik or fall into the lower bowl before the upper passage. The
legacy boss suffix still defeats the boss; its original capsule approach misses
the button, so the replacement explicitly opens it and observes ship carry.
The committed script is recompiled with `InputLogAuthorTool`; its Input Log
bytes are identical to the selected authoring movie. Native Sonic/Tails/team
whole-registry checks and paired live-history isolation are queued together;
this production observation is not their passing qualification.

A bounded current source/resource audit finds no additional missing native exit
owner beyond these repair lanes. Earlier backlog references to SOZ Knuckles Act2
and DEZ800 Act2 are superseded by their later dated entries: fixed 35315/35440
SOZ2 Knuckles movies and two 48-window tests reach LRZ through `loc_77A3E` /
`loc_77A5E`; the 53564-input DEZ800 Act2 movie and its 110-window test reach
`$1700`. The separate 60298-input ordinary wide DEZ ending route also exists.
This audit establishes current source/input availability, not new execution
proof or complete native-presentation/breadth certification. Combined validation
will report the actual tested identities and inherited gaps.

The bounded ending breadth now also covers the actual five viewport presets:
thirty supported S1/S2 donor reunion vectors and thirty configured roster entry
checks (Sonic/Tails, duplicate/mixed/large follower lists). The roster checks
compare every registered snapshot key before retirement, after entry and after
forward replay, then reload ordinary AIZ and verify roster and render origin.
The seven-follower row is a large configured team, not a claimed maximum: the
current configuration parser has no explicit participant cap. The first test-only
build caught an omitted `IOException` declaration for that reload; no tests ran.
After its correction, Java25 `maven_queue.py -Dmse=off test-compile -B` passes
at 16:33 BST (22.675s). Formal source-frozen checks remain queued; this build is
not their outcome.


### Completed focused evidence and restore corrections, 2026-10-13

The repaired MHZ800 cold route passes its complete one-case qualification with
25 named whole-world restore/replay spots, both cycles and retained horizons,
zero failures/errors/skips (development tree with the revised 29744-input movie,
completed 16:42:43 BST).
Command: `python3 tools/testing/maven_queue.py -Dmse=off` with the absolute
verified S3K ROM and `-Dtest=TestMhzWideColdRouteCapture`, `test -B` in
`ai-s3k-playable-finish`. The native three-character and paired-history checks
remain in their existing queued request; this result does not imply their pass.

The SSZ lane's six selected cold/complete routes pass with no failures, errors
or skips: Tails native/wide (16:37:58), solo/team replica routes (16:38:32),
team complete (16:41:11) and solo complete (16:45:58). Their repaired inputs,
assertions and exact commands are committed in `f01268432`, integrated here as
`99486686f`. The mandatory release/control/roster assertions were retained.
The destination ring publication fix passes 14 boundary cases and all 60
mandatory S3K startup cases, no skips, in `f7d1aea0b` (here `71b357e7a`).
Among four current LRZ pair/HPZ checks, three pass; only the intermediate
`TestLrzActTwoColdRouteCapture` door 6 at 39980 assertion is still under
investigation. Tails' first Act2 climb also still needs controller repair.

Ending GPU rescue-body checks pass at 320/800 (2 cases). Native cloud, scene
resource, pure scroll and parallax restoration checks pass 13 cases, and ordinary
render-origin reset checks pass 19. Five-width credit Plane A plus exact PLC
mapping and renderer corruption checks pass 8. Post-credits dispatch plus the
ordinary Mecha-spark neighbor pass 7. These are bounded checks of their named
owners, with no skips; they do not certify an entire ending lifecycle.

Composed rescue and lifecycle negatives identified real rewind gaps. Retiring
permanent followers at native rescue entry lost them when restoring the
pre-entry roster. A derived resource registry now retains complete wired
followers while captured membership determines which are active; fresh world
loads clear retained bindings. Calling a missing one-argument construction
probe also dropped Super Tails' captured flock before its existing owner codec
could restore it. The native reunion RAM clear (loc_5ABC0/loc_5AD8E) erases the
fixed Shield slot while retaining P1; stale registration intent manufactured
an InstaShield after restore. Native auxiliary retirement now clears that handle
and intent, and generic reconciliation preserves a captured absent auxiliary
while keeping explicit pending-handle restoration. Focused reruns are pending.
No permanent follower is relabeled temporary, and no snapshot entry is filtered.

Independent moving footage found native Camera_X reset to 0 with retained 459
arena words at the wide Knuckles credits/terminal. Masking the stale world
interval hid the native centre 400 body. A semantic screen presentation origin
now places only the mask at 240..560, leaving native camera bounds unchanged.
The initial flight's gray flanks are intentional arena-mask grain, so treating
Pattern0 as corrupt was rejected. Negative-progress credit completion also
needed the native loc_5B2CE cloud-emission-mask clear; velocity words survive
and already allocated clouds retire naturally. A new actual-fight-to-terminal
GPU regression checks the real ROM Mecha pixels, native words and emitter
retirement. Its qualification remains pending.

The initial short graph/width checks had fixture defects: unprojected native
camera 0 at wide widths, a child-pressure slot outside the managed4..93 range,
and native routine 0 allocation incorrectly treated as initialized progress.
The affected fixtures now declare their actual native camera/dispatch boundary.
Tails' Chaos-only rescue takes native routine 8 without Super activation or a
50-ring award; that incorrect donor oracle is corrected. The composed effect
restore failures above were preserved and fixed as runtime defects.

Develop's verified queue update `4a9bec1ea` is merged here as `2e992da7d`.
All three workers received the coordination update. Existing requests remain
alive without duplicates; subsequent focused invocations use the updated main
wrapper from the intended tree. Combined coverage will use this tree's updated
local category runner, retaining the complete selection while yielding admission
between batches of at most 50 candidates and before guards. Required surfaceless
EGL flags remain set. This is an admission opportunity, not a runtime ceiling.
The original integration pin remains unchanged.


### Native Knuckles campaign admission audit

The September MHZ Knuckles exclusions are historical task scope, not an
exclusion from this playable-game completion. Source contains the native
Knuckles switch/miniboss/end-boss/FBZ owners, but a cold MHZ1→2→FBZ route remains
unqualified. The source-only suggestion that these owners implied no concrete
MHZ blocker was rejected by the actual cold production probe below.

LBZ has a separate concrete prerequisite: the placed `$CD` upper-arena entry
is an unconditional placeholder. Verified placement bytes `4410 8440 CD00` at
ROM `$1F11CC` belong to native `Obj_LBZFinalBossKnux` / `word_72FEA`. Existing
`$CA` / `$CC` Big Arm/carrier tests manually insert the later owner and bypass
this admission. Its native gate must precede the same-slot `$CA` transition;
the `$328` target comes from the second bounds record and survives into carrier
escape. A character-only `$CA` activation wait is also being checked against
`loc_72A54` / `loc_72A5A`. The isolated LBZ lane preserves a frozen negative
candidate while implementing the owning gate and timer continuation separately.
No manually inserted later boss certifies this actual entry.

A fresh native320 Knuckles MHZ production probe, immutable jar SHA256
`5e301d171c582d4d0cf26fc05b14e821e6e29fcdb02f585119870ccafb2c9ff7`, runtime
source SHA256`0ea2ae103a353717b07a8db96e26d3c4bc5cb17ebd8f34d4d4d9dc547e71af9e`,
uses the existing23775-input ordinary Sonic movie without gameplay seeds. It
remains alive but stationary at `1269/2604` from about input 1000 to its end,
with camera`$560/$948`. Independent up/left/jump candidates cannot release it.
The placed `CutsceneKnucklesSkIntroInstance` seizes control because it checks
Knuckles and checkpoint state but omits native `SK_alone_flag`. Primary
`CutsceneKnux_SKIntro` explicitly BEQ-deletes for flag0 before any camera/P1
writes. The supported locked-on ROM always has0; playing as Knuckles is a
separate prerequisite. Independent source adjudication agrees with the gate.

The runtime spawn constructor now supplies the supported false ROM mode;
standalone component fixtures explicitly supply true and retain their existing
lifecycle assertions. The mode is ordinary captured state rather than derived
only from spawn, preserving generic reconstruction. A short actual-ROM placed
owner regression at 320/352/400/528/800 captures/restores/replays its manager
boundary before any long route. The raw `$5C3/$9F5` placement is verified from
Object Pos/1.bin: its complete block matches ROM `$1F2296`, row 270. The earlier
CSV's `$5B7` was the object's moved spawn after twelve leftward steps, not the
ROM placement; that tentative test oracle was corrected before qualification.

The corrected source compiles. An immutable patched production replay passes
the old lock point, follows ordinary camera motion, and reaches the next lower
loop with live Knuckles by input 1000. Continuous right input preserves momentum
through that loop; repeated jumps lose it. Avoiding the next spike blower still
needs controller authoring. These author observations are not passing route or
rewind qualification. The gate/17 explicit standalone fixtures and new loaded
width cases are in their unique queued focused request.

A separate ending production probe rejects the apparent full-centre grain as an
intentional wait: Tails Super departure at 2500–3700 keeps physical Camera_X=0
while `foregroundPosition` deliberately publishes render-copy608. The screen
mask origin and screen-actor compensation used physical words, hiding the native
centre. The local correction uses the renderer's copies+shake while preserving
physical/copy RAM and native-world Knuckles motion. This is distinct from the
already corrected retained459 terminal bounds and intentional gray flanks.

One retired-roster request ended unexpectedly with exit 143 while still waiting;
its log had no admission or tests. Cause remains unobserved. It is incomplete,
not a pass or JUnit failure. A single replacement uses the updated wrapper;
all other live requests were retained without duplicates.

The repaired MHZ Sonic, Tails and team authored routes passed all three full-world replay cases on `2e992da7d` plus the current route and locked-on intro changes (request 62839, 2026-10-13 17:51 BST, zero skips). The separate paired live-history case failed its outgoing-history prerequisite: its end-relative activation at input 43524 was after the actual FBZ load at input 43234. This was a fixture sequencing error, not a history-manager defect. Activate at the already authored live capsule approach, input 41705, and retain the outgoing history and actual destination reset assertions. Only the changed paired case needs rerunning.

The native intro gate and existing MHZ cutscene checks pass 93 cases with no
failures, errors or skips (request 78871, 18:02:16 BST). Independent review
confirms the three native prerequisites and captured standalone fixture mode.
The paired MHZ live-history rerun passes one case at 18:04:43 BST (request 50505,
48.141 seconds Maven), retaining actual outgoing history and load isolation.
Qualified LRZ paired continuation and Tails Act1 evidence are integrated as
`a1cf9181c` and `7144c7618`; the later Tails Act2 route remains in authoring.
