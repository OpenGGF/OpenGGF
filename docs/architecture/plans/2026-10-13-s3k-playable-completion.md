# S3K playable routes and endings, 2026-10-13

The requested completion target is playable game routes and endings, fixing
blockers first. Strict trace, audio and full-scene pixel parity remain separate
acceptance claims. The integration base is develop
`5e2f51f07917d5eff8ac120007dac55327f93f32`; unrelated main-workspace changes and
other worktrees are preserved.

The bounded playable-route/ending work is delivered on develop; final source,
qualification and cleanup are recorded in [the delivery closure](#delivered-source-and-owned-cleanup).

## Work and acceptance

| Lane | Initial blocker evidence | Required result |
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

The following execution entries preserve initial observations and rejected
approaches. Later completed evidence supersedes their pending labels; final
combined validation and delivery are recorded separately.

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

At the pinned initial baseline, LBZ has a separate concrete prerequisite:
the placed `$CD` upper-arena entry is an unconditional placeholder. Verified placement bytes `4410 8440 CD00` at
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


### Integrated native owners and remaining qualification, 2026-10-13

The native ending feature `c2103d5f7` is integrated as `d63bfe397` after merging
current develop `1ead8d9b4` in `bfdf381a2`. The upstream AIZ native dispatch slots,
drawbridge standing latch and selected-world physics-profile initialization are
preserved. DDZ roster retirement, pure restored scroll output and seven
continuous incoming scenarios are integrated from `3a9e6252a` as `1fab3806c`.
Focused D01 lifecycle/roster/projection checks pass 48 cases without skips;
Knuckles lifecycle/transition/donor/graph checks pass 58, and native scene-clear
and auxiliary-owner regressions pass 2 and 33 respectively. These are bounded
qualification, not full-scene native parity. The ending matrix records the
individual commands, source states and corrected fixture-only premises.

The placed LBZ gate `efbef3d38` is integrated as `976863de8`. Its initial
five-width negative witness resolves CD to a placeholder. The corrected actual
ROM placement admits native camera bounds, retains the negative CD music timer
across same-slot CA initialization, and captures exact parent/sibling edges
through pure child reconstruction. Fifty-seven entry/schema cases, 60 mandatory
S3K startup cases and ten carrier graph cases pass without skips. Positioned
CD→CA→CC→capsule/results/floor/carrier→released MHZ composition passes; its last
ordinary Right-input assertion was corrected from an accidentally neutral mask
and rerun independently. This does not establish a cold LBZ traversal or every
transient detached-child/allocation prefix. See the [LBZ2 matrix](../validation/levels/s3k-lbz-act2.md).

LRZ Tails' repaired first-act and middle routes are integrated as `7144c7618`
and `c67886b74`; their production whole-world checks pass. Late Tails boss/HPZ
and the cold Knuckles MHZ1→2→FBZ controller route remain in authoring. Knuckles
passes the corrected standalone-mode gate, actual lower vine, upper pulley and
reaches the real six-hit MHZ1 miniboss alive without gameplay-state seeds.
These observations do not yet certify the complete route.

The current change-based plan against the original integration pin selects all
ten smoke categories (3,201 candidate classes before the remaining route test),
with batches of at most 50 and separate guards. Root owns combined validation;
workers run focused checks. No broad run or integration push has completed yet.


The final LRZ Tails continuation `f52eb1810` is integrated as `f2b4962a6`.
Its final 53,259/62,524-input movies exactly preserve the qualified 36,714-input
incoming prefix. Three focused methods pass with zero failures/errors/skips at
19:02:16 BST (37.95 seconds in class, 1:02 minutes Maven): actual Act1 history
isolation, 71 Act2 and 62 boss/HPZ whole-world windows, all fourteen boss-health
states, earned FIRE/ring12 approach, capsule/results, no encounter hurt and
released HPZ `393/2800/ring3`. The raw cross-act registry checkpoint was invalid
across an intentional seamless history barrier; the apparently active second
fade was produced by a callback-bearing authoring fork. Fresh uninterrupted
replay and the checkpoint guard reject that fork as evidence of a runtime PLC
or history defect.

Cold Knuckles ordinary rapid fresh jump presses defeat the real MHZ1 miniboss
at input 28613 and reach MHZ2 at 29044. Native body `$CF` has a 32-dispatch hit
cooldown, while flame children `$9A` remain independently harmful during body
hit-flash and use V-int parity; longer airborne re-presses also trigger glide.
Sonic's long jump cadence therefore cannot be assumed suitable for Knuckles.
These are controller choices, not changes to physics or damage handling.
The new 30,000-input Act1 fixture is byte-identical to its selected uninterrupted
production prefix and reaches released Act2 without a death.

The first formal Knuckles check, request 36128, completes one error, zero
assertion failures/skips at 19:00:15 BST (19.40 seconds class, 1:29 minutes Maven)
on `fd46e1bed` plus the new controller test/resources. Actual live history after
the MHZ2 load captures `Mhz2KnucklesRouteSwitchChild#parent`, which refers to the
immediately deleted Knuckles parent and has no registered rewind identity.
This is a real ownership defect, not a reason to disable live history. Native
`loc_6311A` creates the switch and deletes the parent; `loc_632CA` gives the
Knuckles switch its own position/frame/respawn state and `Sprite_OnScreen_Test`.
Only Sonic/Tails keep `loc_63308` / `Child_Draw_Sprite` and a live parent edge.
A short actual-ROM ownership regression and pure shell correction are in the
isolated worker; the unchanged full controller assertions will be rerun after
integration. Later MHZ2 input authoring remains independent of that correction.


The switch fix `3596237c3` is integrated as `bed44fae6`; 173 short entry/cutscene/
graph/startup cases pass at 19:08:47 BST, no skips. The unchanged 30,000-input cold
Knuckles Act1 test then passes one case at 19:16:41 BST (21.40 seconds class,
1:32 minutes Maven), covering 43 two-cycle whole-world windows, every health
value and actual seamless history retirement. Source includes current develop
`843aa4738` reconciled without conflicts in `accf871d9`: native placement patches,
optional S3K difficulty contracts and their candidate API pins are preserved.
No stock rule or authoring-input change was made to accommodate those upstream
contracts. The original combined selection pin remains `5e2f51f079`.


### MHZ native terminal ownership and current route replay

The cold Knuckles authoring prefix exposed a real terminal mismatch: after an
admitted final hit, the old owner retained health one indefinitely instead of
starting native escape. `sub_76782` runs after movement, including the same-update
`loc_760C4` arena admission. Its pre-arena health floor therefore belongs in that
tail, not immediately in the collision callback. `loc_767F6` / `loc_76822` starts
escape at health one after the 32-dispatch flash; a ninth hit to zero is not the
ROM contract. The correction preserves character-specific speed, the `$3F`
underflow wait, clock stop before score, and the fractional low word while
`loc_761A4` applies arena repetition after the unrebased escape threshold.
Worker `e3dc92ea5` integrates as `b143f272e`; 169 affected boss, native damage,
graph and schema checks pass at 20:53:51 BST with no failures, errors or skips.
The 60 mandatory startup checks passed on the same normal boss owner at
20:45:17 BST; later exact-parent reconstruction does not change normal offsets.

The hit proxy now recreates its captured placement and exact captured boss
reference. A two-live-boss negative proved the nearest-parent factory selected
another owner. An earlier single-parent spawn mismatch was incomplete fixture
dispatch: advancing the real moving parent and proxy together passed without
that metadata change. Treating it as a production spawn-drift defect was rejected.

On root `b143f272e` plus the native health assertions, the command below passes
four cases at 21:05:03 BST, zero failures, errors or skips: native Sonic, solo
Tails, native Sonic/Tails and paired live-history isolation. This is focused
qualification before the subsequent native child-retirement follow-up; final
combined source verification remains pending.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestS3kMhzAct2AuthoredRoute,TestMhzPairColdRouteCapture' "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Independent full-drawing controller replay on the immutable `b143f272e` runtime
jar observes wide Sonic health nine through one, terminal flash at input 24797,
escape at 24828, and the actual FBZ load at 28981. Cold native Knuckles retains
the qualified 30,000-input Act1 prefix, reaches terminal health one at 63690,
escapes at 63721 and loads FBZ at 66053. Input 66054 publishes the actual
destination at `(96,1900)`, rings zero, and accepts ordinary right input.
The selected safe endpoint at 66270 is grounded at `(779,1388)` with seven earned
rings. These producer observations do not substitute for the new full-route
world-replay and timeline-isolation contract, whose qualification is pending.

Read-only review also identifies a native lifetime gap: `loc_76164` publishes
parent status bit seven at `$44D0` while retaining the boss body. Both
`Child_AddToTouchList` and spike `loc_76674` consume that signal and retire their
children. The previous Java children only tested actual parent destruction;
the misleading status-bit fixture tested that fallback. A captured local owner
signal and graph-before/after regression are being qualified separately. The
head's `$38` bit five and weather machine's own child owner remain distinct.


The retirement follow-up `2688b2cf1` integrates as `f9ac22318`. Its174 affected
native/component/graph/schema checks pass at21:11:56 BST with no failures,
errors or skips. The native-threshold negatives fail exactly on the still-live
proxy and spikes. Independent subtype negatives prove nearest-parent spike
recreation can select another boss; captured placement and exact links now
retain the real owner. A new explosion replay initially omitted the fixture's
production RNG from its bounded registry; registering that existing RNG resolves
the failure without changing production behavior or weakening comparisons.

Final source `f9ac22318` plus the controller tests passes two cases at21:15:27 BST
(1:57 Maven, zero failures/errors/skips): the new cold native320 Knuckles full
chain and the existing800px Sonic chain. Knuckles's66271-input movie preserves
all30000 Act1 inputs, remains alive without transformation or hidden respawn,
visits every native boss health value9..1 and escapes at one, restores/replays
22 Act2 windows twice and isolates live history at the actual FBZ load66053.
Its final grounded FBZ endpoint has seven earned rings and released movement.
The separate Act1 contract retains43 two-cycle windows. Wide Sonic retains all
25 windows, health9..1 and actual load28981 with released ordinary FBZ control.
The exact focused command is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestMhzWideColdRouteCapture,TestMhzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndIsolatesTheFbzTimeline' "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

These are completed bounded controller/rewind results, not whole-game parity or
all viewport/donor/lifecycle certification. The earlier native Sonic/Tails/team
four-case pass is before the retirement follow-up; final combined smoke includes
those identities on the consolidated source. No strict trace frontier is moved.


### Consolidated ending presentation and combined launch

Ending repair `f664a1e1a` is merged normally as `7df0c44cb`, retaining the
original `c2103d5f7` capture commit in ancestry. Only the independently appended
ROM pitfall sections conflict; both are retained in identical skill mirrors.
No bootstrap-physics or candidate API change is overwritten. The lane qualifies
50 distinct affected checks across completed runs, with no unresolved failures,
errors or skips; this is not a claim of one50-case invocation. IslandLift's
ROM art/animation graph, independent Master Emerald palette helper and explicit
AIZ hold palette owner close the three previously catalogued omissions.
The [ending matrix](../validation/levels/s3k-ending.md#repaired-native-presentation-owners--2026-10-13)
records allocation/rewind, publication-generation pixel premises and inherited
unchecked full-capacity SST-overwrite emulation. Two new85-second excerpts
(Sonic Chaos320 and Tails Super800) have completed CSV/video/hash/full-decode
checks; the original films and their historical source identities are preserved.

The updated local category runner's complete change-based plan against original
integration base `5e2f51f07917d5eff8ac120007dac55327f93f32` selects3214 candidate
classes across all ten ordinary smoke categories and separate guards. The shared
and upstream changes justify that complete selection; no manual narrowing is
used. The actual launch environment's Java25/Lua5.4/PowerShell preflight passed
at19:27 BST; required EGL/null-audio flags and correct existing absolute ROM
paths remain in place. Main develop fetch/fast-forward check at21:18 BST remains
`5d9dd8138`, already included in the development ancestry.

Expected ROM-backed execution cost is60–120 minutes, in65 batches of at most50
candidate classes, followed by fresh guards. Queue waiting is excluded from the
combined120-minute execution limit; ten minutes without output is the no-output
stopping rule. A timeout is incomplete validation, not a pass. The lead owns this
one combined run; workers perform no duplicate broad checks. Source edits remain
frozen while its worktree lease is live. Final failures/skips will be inspected
by identity, disputed failures attributed by bounded matched tests, and consumed
run diagnostics acknowledged before delivery. Packaging uses`-DskipTests`
after the required validation.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null python3 tools/testing/run_categories.py --base 5e2f51f07917d5eff8ac120007dac55327f93f32 --rom-directory "$S3K_TASK_ROM_DIRECTORY" --max-minutes 120 --run
```

`S3K_TASK_ROM_DIRECTORY` denotes the original main checkout containing the three
verified user ROMs. This launch plan is not a completed smoke/guard result.
Integration into develop, push and owned-worktree cleanup remain pending.


## Pinned upstream composition, 2026-10-14

The retained task smoke source is `7d0a9acbb`, based on destination
`5d9dd8138`; it does not qualify later upstream code. An isolated integration
branch first merges the qualified ending follow-ups through `c893df427`, then
pinned upstream `ea8c9c627`. The ending merge preserves the retained route
owners and changes only `SwScrlEnding`, its resource contract test and the
stale title-card reflection fixture under `src/`. The upstream delta has 309
paths, with 18,512 added and 843 removed lines.

The upstream merge automatically combines all eight intersecting runtime
owners: `GameLoop`, `Sonic3kGameModule`, `Sonic3kObjectArtProvider`,
`EggRoboBadnikInstance`, `LevelManager`, `SpriteManager`,
`SpriteManagerInternalAccess` and `AbstractPlayableSprite`. The sole textual
conflict is the coverage backlog introduction: retain both the task's playable
completion record and upstream's issue-sweep/Chaos records. No owner is replaced
wholesale. In particular, preserve the ending Start/white-entry semantics,
resident-art DMA refresh registration, reversible follower retirement, captured
InstaShield absence and fresh render-origin reset alongside upstream policy,
motion and hurt-landing changes.

The behavioral risks are composed consumers, despite clean Java merges:
upstream changes setup-only host input-edge consumption, ordinary native-tick
policy hooks and accepted world-history replay, motion/fluid bindings for newly
registered roster members, S2/S3K native hurt landing through object control,
and shared SAT/priority/background-FBO/shader rendering. Stock worlds without
prepared mutator definitions retain a null policy graph; this limits active
Chaos mechanics to configured content, but does not remove the shared drawing
and input changes. The new capture launch/decorator and optional presentation
pacing paths also require their normal CLI consumers; old media keeps its
original immutable source and is not relabeled as combined-source parity.

Upstream executable tree `a2280d37b:src` equals `ea8c9c627:src`
(`534374ca3d17755a6001dfd4ba6f9662a6ddeeee`); the [issue-sweep record](../validation/2026-10-13-github-issues.md)
records 383 movement/API/rewind/loading cases, ten graphics cases, nine SOZ
routes and 680 fresh guards, all without failures, errors or skips. These are
completed unchanged upstream baseline checks, not proof for this combined
source. The earlier Chaos broad run retains its dated, matched inherited reds;
CI EGL package provisioning has no recorded hosted-run result here.

Build and final validation remain lead-owned delivery gates. Inspect the
change-based smoke selection from the original pre-task destination `5e2f51f079`
after composition; shared movement/history/rendering changes justify a new
source qualification rather than repeating unchanged task checks. Preserve
exact test identities and skips when comparing the retained run and upstream
baseline. Focused ending entry/Start, roster/effect reverse restore, D01 and
actual Knuckles terminal lifecycles, changed presentation pixels, and real cold
route/handoff consumers should run on the composed source wherever they are
outside the selected smoke lane. Do not add another broad baseline run or infer
native scene conformance from these bounded checks.


The isolated merge commits are `b01eaeb6a` (ending) and `b01a0c665`
(upstream). On the composed `b01a0c665` source, build-only
`python3 tools/testing/maven_queue.py -Dmse=off compile` succeeds at
00:13:39 BST on 2026-10-14: 3,964 production sources, Java release 25,
50.669 seconds Maven time, no tests executed. The normal hooks and exact final
coauthor parse passed both merges; all eight Java intersections were automatic.

The read-only plan command
`python3 tools/testing/run_categories.py --base 5e2f51f079 --json` selects
3,253 of 3,253 smoke candidate classes across all ten categories: 66 ordinary
batches of at most 50 classes plus the separate fresh guard lane. This is the
complete bounded smoke selection, not the public-release full suite. The
ending lifecycle/Knuckles/pixel/entry/donor/roster, changed native ROM-table,
MHZ/DEZ/LRZ/SSZ route and PLC-registry consumers are present in that selection;
`TestPatternSpriteRendererCorruptionGuard` belongs to the separate guard lane.
The lead still owns preflight in the actual launch environment, execution cost
and stopping rule, identity-by-identity comparison, integration and push. No
engine test or new broad run is started by this preparation work.


## Composed-source focused qualification and launch plan, 2026-10-14

On `33fe50206`, the exact 17 retained-smoke error identities plus six new
ending-resource cases pass **23 tests, zero failures/errors/skips** at
00:16:32 BST: 30.124 seconds Maven execution, 24 seconds queue waiting excluded.
The selector contains the 16 failed provider methods, the one
`TestSonic3kTitleCardKosQueue#freshLevelTerrainWaitsForTitleAndNemesisButNotChildExit`
method, and `TestSwScrlEndingResources`. The owning
[ending design](../designs/2026-10-13-s3k-ending-lifecycle.md#deferred-ending-deformation-resources--2026-10-13)
records the deferred ROM-read negatives and the 55 affected follow-up checks.
The integrated `SwScrlEnding` blob remains
`0afdeaa074c34c97e3901574cc1c886fe381a09a`; its private paired cache changes neither
native arithmetic nor captured RAM. The corrected title-card fixture blob is
`9bfabae99334876b4a7fddd67077537d57be59cb`. Its prior four-argument reflective
constructor no longer matched the task's two destination coordinates, rings,
roster and lifecycle arguments. The corrected fixture supplies the current destination
rings. All 11 title-card cases passed on `5cbcec969`; the matched one-case
negative on task-intermediate `2e992da7d` has the exact `NoSuchMethodException`.
That intermediate is not an executed original-base result for this identity.
The bounded independent review finds no uncovered consumer requiring another
unchanged 55/11-case campaign.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py --lean -Dmse=off "-Ds3k.rom.path=$S3K_TASK_ROM" '-Dtest=com.openggf.game.sonic3k.TestFbzScrollHandler#providerReloadCreatesFreshFbzAccumulatorOwner,com.openggf.game.sonic3k.TestSonic3kCnzScroll#providerReturnsCnzHandlerForCnzZone,com.openggf.game.sonic3k.scroll.SwScrlHczTest#providerRoutesHczToDedicatedHandler,com.openggf.game.sonic3k.scroll.SwScrlHpzTest#giantRingDestinationUsesSanctuaryScrollButBossActKeepsFallback+providerRoutesHpzToDedicatedHandler,com.openggf.game.sonic3k.scroll.SwScrlIczTest#providerRoutesIczToDedicatedHandler,com.openggf.game.sonic3k.scroll.SwScrlLbzTest#providerRoutesLbzToDedicatedHandler,com.openggf.game.sonic3k.scroll.SwScrlLrzTest#bossActsNoLongerBorrowTheHiddenPalaceHandler+initForZoneMakesTheZoneOnlyLookupActAware+providerRoutesBothLavaReefActsToTheDedicatedHandler+zoneOnlyLookupStillReturnsTheHiddenPalaceHandler,com.openggf.game.sonic3k.scroll.SwScrlMgzTest#providerRoutesMgzToDedicatedHandler,com.openggf.game.sonic3k.scroll.SwScrlMhzTest#providerUsesMhzDeformForMushroomHill,com.openggf.game.sonic3k.scroll.SwScrlSlotsTest#providerRoutesSlotsToDedicatedHandler,com.openggf.game.sonic3k.scroll.TestS3kSszScrollBands#providerRoutesSkySanctuaryToItsOwnHandler,com.openggf.game.sonic3k.scroll.TestSwScrlS3kDezFinalBoss#providerSeparatesFinalBossFromSanctuary,com.openggf.game.sonic3k.titlecard.TestSonic3kTitleCardKosQueue#freshLevelTerrainWaitsForTitleAndNemesisButNotChildExit,com.openggf.game.sonic3k.scroll.TestSwScrlEndingResources' test -B
```

All six resource cases executed, including
the two ROM-dependent cases; no missing-ROM skip occurred.

The separately qualified wide SSZ lane `c22a1c451` merges cleanly as
`967b6db0a`. On that newly composed source the complete paired 800px case passes
**1 test, zero failures/errors/skips**, at 00:31:25 BST: 52.897 seconds Maven,
27.91 seconds test execution and 637 seconds queue waiting excluded. All 21,014
ordinary inputs and the first 9,914 unchanged inputs remain intact. All three
bosses visit health 8..0; the 44 two-cycle 45-input world spots, live native P2,
P1 survival/lives and full-load history retirement pass. Actual Death Egg load
is input 20,863, fresh assembly is 20,864, and the native intro owner releases
both pad locks at 20,946. Assembly publication is not ordinary control release.
The grounded destination has three earned rings. No runtime or movie change
was needed for the newer movement source. Source-specific authoring negatives
and retained artifacts are in the
[SSZ1 matrix](../validation/levels/s3k-ssz-act1.md#current-native-800px-paired-cold-completion-2026-10-14).

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py --lean -Dmse=off "-Ds3k.rom.path=$S3K_TASK_ROM" '-Dtest=TestSszWidePairedColdRouteCapture#coldCompleteWideRouteDefeatsAllBossesAndIsolatesTheDezTimeline' test -B
```

Actual Java 25/Lua 5.4/PowerShell preflight passes in the intended headless
launch environment. The current plan from original integration base `5e2f51f079`
still selects **3,253 of 3,253 smoke candidate classes**, all ten categories,
66 batches of at most 50 classes and fresh guards. This complete selection
includes the new wide method; it is not narrowed manually. Expected execution
cost is 60–120 minutes. The execution stopping rule is 120 minutes combined,
with ten minutes without output; queue waiting is excluded. The lead preserves
the original retained smoke through completion before starting this new-source
run. Both failed/skipped identities and actual completed lanes will be inspected,
and consumed diagnostics acknowledged. This is a launch plan, not a completed
composed smoke result. Integration into develop, push, reproduction normalization
and owned cleanup remain outstanding.


### Inherited child-launcher prefix and preserved graphics

The retained run's additional assertion is exactly
`TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`,
line182: stderr must start with `PRODUCER_UNAVAILABLE:`. Its preceding expected
status4 and empty-stdout assertions pass. The [upstream issue record](../validation/2026-10-13-github-issues.md)
already supplies a matched original-`5e2f51f079` run: five cases, one identical
CLI assertion failure, zero errors/skips, 103 seconds. Two direct subprocess
controls both return4/empty stdout; Java's `JDK_JAVA_OPTIONS` notice precedes
the correct unavailable-producer diagnostic only when that variable is present.
Test blob `87c070511453f2b1447648269ddd8a747cce0206`, tool blob
`20d8d2417cee8dba47e59ca8da7a48061fad669e`, and complete-run production tree
`a7e86e12af500d7018650188f338b9e2e2dca760` match original5e2, baseline2e992,
retained7d0, composed2d55 and upstreamea8. The retained runner has the same
headless JDK option. Its compact assertion does not retain the actual child
stderr bytes; this attribution does not claim a new byte-for-byte observation.

A current environment-controlled qualification on `2d55d6927` passes the one
exact method at00:44:42 BST: zero failures/errors/skips, 25.942 seconds Maven,
0.440 seconds test and no queue waiting. No production/test edit is made.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput' test -B
```

The complete category runner has no extra Maven-argument option. Installed
Maven3.9.16 keeps `MAVEN_OPTS` JVM properties in request system properties;
Surefire3.2.5's effective-properties assembly forwards explicit configuration
and Maven user properties, including CLI `-D`, not those parent JVM properties.
This POM has no EGL forwarding in `argLine`/`systemPropertyVariables`.
The queue's exact-flag whitelist is admission support, not proof that a test
fork receives the flag. Therefore the next complete category run retains the
known `JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl` environment and
its real graphics coverage, with this precisely attributed inherited assertion
reported if it repeats. Do not weaken the unrelated assertion, change scheduling
or silently lose graphics coverage to obtain a green total.

The user's coordination window starts only after the entire original retained
run, including fresh guards, actually releases. Defer the not-yet-submitted
66-batch composed run for up to five minutes, preserving all existing requests.
Keep source stable and submit no additional tests/builds during that opportunity;
report its actual start/deadline to the runtime-memory coordinator. Another
retained-tree obstacle does not extend the window implicitly.


## Completed retained run and guard follow-up

The original frozen `7d0a9acbb` run `20261013T202202Z-d4bd5b37` completes all
65 ordinary batches: **26,921 tests, one assertion failure, 17 errors and
64 skips**, 4,740.81 seconds execution. Its fresh guard lane completes **678
tests, four failures, zero errors/skips**, 264 seconds execution. The runner
is terminal with exit1; observed lease release is23:57:02 UTC on October13
(00:57:02 BST on October14). This is a completed red smoke/guard result, not a
full-suite or green delivery claim. The 17 resource/fixture errors have the
qualified corrections recorded above; the CLI-prefix assertion is exactly
attributed to the original base and passes the controlled current invocation.

All64 skips are inspected with no omitted identities. They include optional
measurement/capture jobs,32 conditional Infinite Sonic terrain scenarios,
six graphics-context assumptions, three optional native graphics checks and
the optional slots-glass capture, the S1 local-reference audio capture and its
unrequested timeline capture. `TestCPZObjectBugs#testSpinTubeForcesRolling`
aborts because its tube fixture does not capture/release Sonic; this is an
unverified fixture premise, not a pass. Existing ROM paths are present and
verified; none of these skips is claimed as missing-ROM coverage.

The four exact guard obligations are owned by this task:

- `TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction`:
  the actual strip/filter counter is original5e2/oldmain5d9 3,258, retained7d0
  3,270, upstreamea8 3,255, and composed02f39 3,267, against unchanged budget
  3,258. The ending additions contribute12 lines; upstream saves3. Raising the
  allowance or compressing unrelated code is rejected. Existing package-private
  `PendingInstaShieldRewindExtra` now owns captured-absence destruction before
  hydration and a pure refresh-admission predicate. The facade becomes3,258
  with unchanged public/protected declarations, fields and one-field snapshot
  record payload. The existing Shield16, sprite-rewind14 and reset/ground5
  cases are the meaningful behavior checks, alongside the exact size guard.
- `TestRewindArchitectureGuard#objectRewindAnnotationsDoNotGrowWithoutExplicitBaselineTriage`:
  delete only the two stale MHZ proxy/spike transient-count1 entries; change
  LBZ final-boss1 count13 to5. `efbef` captured four exact object links centrally
  and four scalar fields generically; `e3dc`/`2688` captured exact MHZ parent
  identities and recreation metadata. Their qualified graph evidence is already
  recorded above. Retain every other annotation/resource inventory and budget.
- `TestObjectUpdateClockTerminologyGuard#objectUpdateClockUsesVIntRunCountTerminologyAcrossBoundaryAndFrameworkHooks`:
  rename the seven ending update parameters from `vInt` to `vIntRunCount` and
  their six uses. Captured `nativeVInt` fields, values, arithmetic and clocks
  remain unchanged. The guard launches a2GiB attribution child and needs normal
  focused admission, not the lean lane.
- `TestSonic3kObjectProfileRegistryGuard#profileImplementedIdsMatchRegistryForEveryZone`:
  the registry is the guard's expected set and the profile its actual set.
  The concrete LBZ `$CD` registry owner added by `976863de8` was absent from
  `Sonic3kObjectProfile.LBZ_ONLY_IDS`. Add only that implemented metadata and
  extend the existing LBZ-positive/MHZ-negative profile regression. Native
  record `$1F11CC` places the `$CD` prelude; `Obj_LBZFinalBossKnux` has a camera
  gate and changes to `$CA` in the same slot, without a character-admission
  branch. Registry admission, probe context and guard comparison stay intact.

The requested five-minute opportunity has deadline00:02:02 UTC on October14.
The user reports the original RSS control complete and releases the unused
portion early; observed acknowledgment is00:01:40 UTC. A separate subsequently
admitted diagnostic briefly holds linked source leases. Normal EX writers reject
mutation until its reported terminal release; no lease/process is forced and
no stale-source tests/builds are submitted. All guard edits are consolidated
under the normal worktree lock before lead-owned focused validation.

### Consolidated guard/helper check and additional composition growth

The normal focused request completes on October14 at01:19:40 BST, session27573
exit1, after258 seconds queued and130 seconds Maven execution. On committed
`02f39f8bc` plus the12 consolidated owned edits, the exact four guard methods
and full `TestSonic3kObjectProfile`, `TestShieldRewindRestore`,
`TestAbstractPlayableSpriteRewindCapture` and `TestPlayableGroundTransitions`
classes execute **46 tests: one failure, zero errors/skips**. The same headless
JDK flag, Lua5.4, null audio and all three verified absolute ROM properties are
used. The35 shield/sprite/ground cases, seven profile cases and three other
guards pass. The size guard accepts AbstractPlayableSprite blob
`4b4b2b86b2da0594fc782b3d74e190c881674b0b` and its helper blob
`f6666d0c32aba1a645c2b7b855b83b164b22932a`.

That guard exposes additional composed owners: GameLoop is3,319 against3,313
(blob `8f29efd4b0b1941f65c32b2567d2bf397b3edd0e`) and LevelManager is3,145
against3,144 (blob `02c795c31b51c60bba2fc69c73cbd6698d94e4be`). This is a
new composition blocker, not a qualified pass. Keep budgets unchanged. Original
5e2/retained7d0/upstreamea8 counts are3,295/3,305/3,309 for GameLoop and
3,140/3,141/3,144 for LevelManager: both separate branches fit, their union
does not. Raising budgets or compressing unrelated lines is rejected.

The existing GameLoopPlcLifecycle now owns the same pre-/post-load presentation
predicate and sampled white/black entry dispatch. Module lookup boundaries,
logging/music order, fade deferral, wrapping and callbacks remain unchanged.
GameLoop becomes3,312. The skipped-presentation player animation method and
native-order comments move verbatim into existing LevelPlayableArtInitializer,
which already owns the identical injected SpriteManager; its sole caller
delegates directly and LevelManager becomes3,120. No runtime fields, public
APIs, snapshot records or rewind state change. The trace-isolation owner list
follows the moved production owner. Four bounded observable-order cases cover
main/follower leading object passes before VBlank, absent/inactive service and
zero passes; existing presentation tests did not exercise the positive loop.
The already-passing unrelated focused methods need no ceremonial repeat.

### Presentation extraction qualification

The consolidated follow-up completes October14 at01:27:11 BST, session27132
exit0, zero queue wait and73 seconds Maven execution: **21 tests, zero failures,
errors or skips**. Command uses normal `maven_queue.py -Dmse=off`, the retained
headless JDK/Lua5.4/null-audio environment and all three verified absolute ROM
properties, with this exact selector and `test -B`:

```text
com.openggf.tests.TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction,com.openggf.TestS3kEndingTransitionPresentation,com.openggf.TestGameLoopFreshLevelHandoff,com.openggf.game.resources.TestNativePlcFadeOwnerCoverage,com.openggf.level.TestLevelManagerInitialPresentationPlcLifecycle,com.openggf.tests.TestFreshLevelBoundaryRewindHeadless,com.openggf.tools.TestGameplayCaptureFreshLoad,com.openggf.trace.TestSkippedPresentationPlcTraceIsolationGuard
```

This qualifies the unchanged size ratchet, three actual native white-entry
cases, four generic/fresh handoff cases, two native fade-owner contracts, six
initial-presentation cases (including the four new order/zero/service cases),
two boundary/rewind cases, one real capture fresh-load case and two production
trace-isolation guards. All owned source writers are terminal. The35 existing
shield/sprite/ground checks, seven profile checks and three other guard passes
from the prior completed46-case invocation remain qualified on unchanged owners.
Full composed smoke still remains a separate pending delivery gate.

## Completed composed-source qualification

Frozen source `aaff73747c3ada697008d7744952b0db85dad743`, including upstream
`ea8c9c627`, completes run `20261014T002805Z-f1c80d7d`. Session57904 is terminal
exit1, observed October14 at01:57:33 UTC; the entire retained lease is released.
Actual preflight in the launch environment passes before submission. The inspected
change-based plan selects all3,253 smoke candidate classes and all10 categories,
66 batches of at most50 candidates, then fresh guards. `full:true` in the plan
denotes complete smoke selection, not the release-only full suite. The command
uses `MAIN_WORKSPACE` for the absolute main-checkout ROM directory:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4 ALSOFT_DRIVERS=null python3 tools/testing/run_categories.py --base 5e2f51f07917d5eff8ac120007dac55327f93f32 --rom-directory "$MAIN_WORKSPACE" --max-minutes 120 --run
```

All66 ordinary batches complete: **27,200 tests, one failure, zero errors,
64 skips**,3,223 reports,4,459.28 execution seconds. Fresh guards complete
**680 tests, zero failures/errors/skips**,88 reports,270.45 execution seconds.
Combined execution is4,729.73 seconds (78.83 minutes); recorded admission waits
total608 seconds and are excluded from the timeout. No failed or skipped identity
is omitted. This is completed bounded smoke/guard qualification, not a full-suite
or completely green invocation. No new or worsened failure remains identified.

The sole failure is exactly
`com.openggf.tools.audio.completerun.TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`,
line182, `AssertionFailedError: expected <true> but was <false>`. The preceding
exit4 and empty-stdout assertions pass. Its matched original-base source/assertion
and controlled current pass are recorded above. The retained headless environment
explains the inherited first-byte stderr premise; this run does not independently
retain the child's raw stderr bytes. Keep graphics enabled and report this
precisely attributed pre-existing assertion rather than weakening the test.

All64 current skips are inspected:32 conditional Infinite Sonic terrain/stock
platform scenarios; six individual graphics-context assumptions;24 opt-in
measurement, soak, balance, route or capture checks; the CPZ spin-tube fixture's
unverified capture/release premise; and one unavailable local deterministic
BizHawk audio reference. None is a missing-ROM skip. Among those opt-in checks,
is `com.openggf.tools.audio.timeline.TestS1Ghz1OpenGgfAudioTimelineCapture#captureRequestedOutput`,
which aborts because no local timeline capture was requested. These groups match
the preserved original64-skip scope; individual historical reason-byte equality
is not claimed after consumed diagnostics are acknowledged. Bare-context skips
do not erase the separately qualified owned ending pixels or certify those
unrun graphics cases. Remaining donor/viewport/lifecycle/native-pixel products
and strict trace frontiers stay explicitly open in the matrices.

Both retained diagnostic runs are inspected and acknowledged/deleted. Temporary
Maven logs are discarded after their summaries are consumed. The completed
source delivery and cleanup are recorded below.


## Delivered source and owned cleanup

Develop is fetched and fast-forwarded at `ea8c9c627`, already included in the
qualified composition. Merge `f38e9b36e4235b9f526d83f5363f8363417a9d36` integrates
`1d5efa71e` without conflicts and is pushed to `origin/develop`. Exact `src`,
POM, testing-tool, hook and workflow trees match tested `aaff73747`; only
qualification documentation follows it. The CI-style branch-policy validator
and normal push hook pass. Main-workspace package command
`python3 tools/testing/maven_queue.py -Dmse=off -DskipTests package -B` exits0,
BUILD SUCCESS in1:18 at03:09:17 BST, compiling3,964 production and3,904 test
sources with Java25 while executing no tests. Unchanged bounded coverage is
not repeated as an integration ceremony. The inherited CLI failure and64
skips remain exactly as qualified above; delivery is not a wholly green suite.

All eight owned S3K task worktrees and local branches are removed after push.
The baseline LBZ untracked draft is accounted by its committed stronger native
viewport assertion. LRZ's nine commits are cherry-equivalent. SSZ's remaining
intent-equivalent commits map to `71b357e7a`, `1fab3806c` and `c2103d5f7`;
LBZ-dev maps to `976863de8`, `bed44fae6`, `b143f272e` and `f9ac22318`. Those
three nonancestor branch tips are deleted only after this source/intent
accounting; no synthetic merge is used. The remaining tips are ancestors.
Known generated targets, links, caches and probe outputs are removed, along
with34 approved scratch directories totaling607.43 MiB. Main's dirty
disassembly submodules and unrelated files remain intact.

The latest12-chapter61-second ending reel is identified in the
[ending matrix](../validation/levels/s3k-ending.md#moving-evidence). The live
external `ending-capture-index.json` records the final pushed develop
reconstruction target and a Java25/POM-named fat-jar recipe. This changes
current status and reconstruction pointers, never historical capture
commands, source hashes,11 capture records or the immutable reel source-index
snapshot. Retain the ed7dc650 historical runtime jar/classpath and presentation
rebuild drafts. A rebuilt movie is new evidence; the retained mixed-source
films do not become latest-integrated or whole-scene native pixel parity.
Formal route breadth and strict trace frontiers stay open as recorded.


## Remaining parity and coverage follow-up — 2026-10-14

The follow-up request is to complete the remaining recorded S3K parity and
coverage limits. It starts from delivered develop
`5b2ff8e7851b99dd40998dfc41fbfd21c80031ef`; the prior bounded delivery and
historical captures remain qualified within their recorded scope. Main's normal
fetch/fast-forward waits for the admitted runtime-memory pair5 lease and then
completes unchanged. The separately excluded swapped performance cohort has no
parity or performance acceptance credit. Four new isolated worktrees own the
coordinator, HPZ breadth, native continuation allocation and trace diagnosis.

| Obligation | Current actionable owner | Planned evidence |
| --- | --- | --- |
| Native ending wait audio | `loc_5E80A` / Knuckles flight | Native VInt low-nibble request, expiry ordering, adjacent-routine negative and replay, then separate production PCM/native acceptance |
| Full-table continuation writes | `Create_Continue_Sprite` / native SST projection and manager slot transfer | Independent partial-write mask; retained live state, slot93 ownership, execution cursor, no synthetic unload and both restore directions; all legal scene occupants |
| Configuration and lifecycle | Production presets and each owning act matrix | Entry/reload/death products, meaningful interactions and two-cycle replay across supported donors/characters/team contracts; preserve existing coverage and unique custom-width checks |
| Route continuity | LRZ→HPZ→SSZ and remaining materially distinct paths | Ordinary cold inputs, actual loads/control release and full-world replay/history isolation; positioned success does not close a cold route |
| Strict trace frontiers | Original/alternate AIZ and initial/returned HCZ, then downstream canonical lanes | Pinned matched source/fixtures, exact first-error field/row, separate bootstrap limits, ROM-owned fixes and another BK2 without hydration |
| Native presentation/audio | Ending and distinct per-act presentation owners | Independent synchronized native320 NTSC/PAL scene checkpoints and contiguous transitions, actual SAT/CRAM publication, complete waveforms/requests; wide extensions remain separately specified behavior |

The recorded gaps include absent AIZ2/HCZ2/MGZ/CNZ/ICZ matrices, LRZ/SSZ
physical checkpoint products, MHZ/FBZ/SOZ/DEZ/DDZ remaining supported products,
sanctuary/auxiliary dispositions, rendered native acceptance and strict trace
frontiers. Existing MHZ/FBZ/SOZ/DEZ physical-post products and completed native
route cases are retained. Historical pending labels do not reopen completed
checks. Actual menu presets replace obsolete512/640 mandatory rows; those raw
custom widths retain separate useful regression intent. Uncapped team admission
requires general ownership properties and concrete large-roster evidence, not
a fabricated participant maximum.

First red qualification on unchanged production5b2ff8e78 is three tests,
three exact failures, zero errors/skips; Maven1:17, completed08:10:46 BST.
Native wind request is absent at aligned VInt0, replay observes no `$CE`
requests, and expiry emits only music21 instead of wind before music. These
new diagnostic checks use original ROM data and literal source predicates; no
trace values supply gameplay state. Full follow-up completion remains pending
until every applicable recorded obligation has actual evidence.

### Quiet-wind repair qualification

`loc_5E80A` now requests native `sfx_WindQuiet` (`$CE`) before animation and
word-timer expiry, through `Play_SFX_Continuous`'s received
`V_int_run_count & $F == 0` admission. The entire affected
`TestS3kKnucklesEnding` class passes16 tests, zero failures/errors/skips;
Maven1:14, completed08:15:18 BST. The three red checks become green, and all13
existing native flight, viewport, palette and replay cases remain passing.
This is request/owner timing evidence, not complete PCM or native scene parity.

Both runs use the local updated wrapper, lean bounded memory, original locked-on
ROM and explicit EGL property (without launcher notices):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS -u JAVA_TOOL_OPTIONS ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$MAIN_WORKSPACE/Sonic and Knuckles & Sonic 3 (W) [!].gen" '-Dtest=TestS3kKnucklesEnding#quietWindUsesOnlyTheNativeVIntLowNibbleDuringTheWait+expiryDispatchRequestsWindBeforeRestoringMusicAndAdjacentJumpDoesNot+restoringTheWaitingOwnerReplaysTheSameNativeWindRequests' test -B
# After the native runtime correction:
env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS -u JAVA_TOOL_OPTIONS ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl "-Ds3k.rom.path=$MAIN_WORKSPACE/Sonic and Knuckles & Sonic 3 (W) [!].gen" '-Dtest=TestS3kKnucklesEnding' test -B
```

The source audit also finds `loc_5DC2C`/`loc_5DD0E` rescue-plane child lists
represented partly as inline drawing. Managed native flame, propeller, passenger
and Master-child allocation/retirement is a new concrete parity obligation. It
is coupled to legal full-table continuation residue and whole-scene SAT
acceptance; selected earlier pixel tests do not establish this missing graph.
The root owns that graph while the allocation worker owns live-SST projection
and transfer. Source mutation uses normal worktree leases and waits for admitted
controls; no foreign job is paused, restarted or preempted.

#### Native D01 child-graph regression boundary (2026-10-14 follow-up)

On `feature/ai-s3k-parity-coverage`, production bytes at `d34e4fc8c`, new
`TestS3kRescueFlightChildren` was run through the local queued lean wrapper
with Java25, explicit surfaceless EGL and the original absolute locked-on ROM.
Session30523 completed exit1 at07:40:43UTC: three tests, three failures,
zero errors/skips,1.761s test time and24.832s Maven. All three actual production
RESCUE phase22 entries reached their own boundary; none had a managed child,
against native groups of4 (Sonic incomplete) or5 (Sonic super/Tails hyper).
Command: `env -u DISPLAY -u WAYLAND_DISPLAY -u JDK_JAVA_OPTIONS -u JAVA_TOOL_OPTIONS ALSOFT_DRIVERS=null LUA_BIN=lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Ds3k.rom.path=<repository-root>/Sonic and Knuckles & Sonic 3 (W) [!].gen' '-Dtest=TestS3kRescueFlightChildren' test -B`.

Implementation follows `loc_5DC2C`'s independent CreateChild6/1 groups,
`loc_5DD0E`'s small-plane group, `loc_5DD34`'s flag5 and palette helper, and
`loc_5DE1A`'s flag1. Native `Child_Draw_Sprite2` schedules deletion for the
following dispatch: the initial test's immediate-retirement premise was corrected
from the helper body before qualification. Existing Knuckles child tests likewise
now assert the pending status/code-owner pass before slot release.
Native AIZ return uses the same two flames and propeller, without a passenger.
All parent reads resolve the current physical SST code owner; no comparison RAM
or old Java parent reference supplies gameplay. Qualification remains pending.

#### Composed child graph and native lifetime qualification

The coordinator merged independent core `ae632fe2a6eec9bece7cafb4f2643c74365565c3`
into local `d34e4fc8c`, resolving the director projection by actual writers.
The core's41 distinct passing cases and their overlaps are recorded in the
existing ending lifecycle design. Native rendered/audio parity and complete
composed pressure are not inferred from those tests.

The source-frozen composed invocation (session51235,07:57:02UTC,90s Maven,24s
queue wait) ran75 cases:72 passed, three Knuckles small-propeller retirements
failed, zero errors/skips. Breakdown: continuation status1; Rescue36; Knuckles
ending16; child graph6 (three failures); actual rescue/AIZ return16. The
failed getter omitted `loc_5E9E2`'s flags38 bit1 and invented a D01-only bit5;
pre-init render flags also now remain cleared. No gameplay timer or position
was tuned. Exact affected three phase cases plus one full-registry changing-graph
replay pass4/0/0/0 in session8687 (07:59:47UTC,73s Maven,0.660s tests).
These are complementary completed checks, not one pristine75-case run.

Independent palette-flash delayed-deletion regression fails1/1/0/0 in60405
(08:04:00UTC,23.462s Maven): expected retained owner, actual immediate delete.
Both sprite-clear regressions fail2/2/0/0 in68032 (08:06:56UTC,23.435s):
expected retained global `_unkFAA4` slot93, actual reset−1.
`loc_5FC78`/`Go_Delete_Sprite` now retain status7/code ownership for one dispatch;
`loc_5ABC0`/`loc_5AD8E` clear only sprite RAM through Kos_decomp_buffer, preserving
the global catcher address. The correction run55930 passes13/0/0/0
(08:10:39UTC,81s Maven): palette1, clear2 (two full-registry replay cycles each),
and all10 Sonic/Tails×production-preset actual hyper AIZ handoffs.
The graph/boundary qualification totals77 distinct cases, with overlaps retained
and not double counted. Root source guards pass3/0/0/0 in47749
(08:20:10UTC,25.468s Maven): extracted facade budget, critical class budgets,
and no concrete Sonic dependency in ObjectManager.

All engine runs used the local `maven_queue.py --lean`, known bounded exact
selectors, `-Dmse=off`, original absolute `-Ds3k.rom.path`, explicit
`-Dopenggf.headless.gl=surfaceless-egl`, `ALSOFT_DRIVERS=null`, Lua5.4 and no
ambient display or launcher-option variables. Completed diagnostics were
consumed/deleted under the exclusive worktree lease. These checks are focused
validation, not smoke or full-suite acceptance.

Selectors (each with `test -B`):

- 51235: `TestS3kRescueFlightChildren,TestS3kEndingRescue,TestS3kKnucklesEnding,TestS3kKnucklesEndingChildren,TestS3kEndingContinuationPressure#nativeDirectorStatusRetirementDoesNotInventControlBitOne`.
- 8687: `TestS3kKnucklesEndingChildren#nativePhaseListsAreManagedAndRetireOnTheirOwningFlags+completeRegistryRestoresParentLinksAndReplaysTheChangingGraph`.
- 60405: `TestS3kKnucklesEndingChildren#allocatedPaletteFlashPreservesColorZeroAndReplaysTheNativeHold`.
- 68032: `TestS3kEndingNativeObjectClear`.
- 55930: `TestS3kEndingNativeObjectClear,TestS3kKnucklesEndingChildren#allocatedPaletteFlashPreservesColorZeroAndReplaysTheNativeHold,TestS3kRescueFlightChildren#actualHyperSceneHandoffAllocatesOnlyTheNativeAizReturnGroups`.
- 47749: `TestArchitecturalSourceGuard#releaseCriticalLargeClassesDoNotGrowWithoutExtraction+objectManagerFacadeStaysWithinExtractedCollaboratorBudget+objectManagerDoesNotGainConcreteSonicDependencies` (no ROM/EGL/audio requirement).

Native reference prerequisites use the separately owned archive-pinned official
BizHawk2.11 setup, qualified through normal admission in55641, not an assumed
installed version. Independent future declared-scene comparisons use fixed
VInt0/RNG0 before the first production object pass. Historical Knuckles fixture
clock410766 is comparison-derived (2026-09-17 SSZ plan, native RAM observation)
and is preserved with its historical declared-entry scope; it is not repurposed
as an independent pixel setup. Natural cold full-chain comparisons remain
separate. Historical ending media/index bytes are unchanged.

### Production presets, incomplete donors and PAL credits qualification

On source `1c8cfbe33` plus test-only follow-up, the actual production
`DISPLAY_ASPECT` resolver is selected before donor entry and asserted again after
the fresh load. The nine S1 Sonic / S2 Sonic / S2 Tails progress -2/0/+2
combinations run at all five presets320/352/400/528/800:45 cases, including15 new
incomplete-progress entries. Each verifies native body/tail ownership, the
progress-specific transformation gate, whole-registry replay, both binding
restore directions and ordinary fresh-load donor rebinding. These are declared
reunion entries, not nine cold campaigns.

The first focused command completed08:40:03 UTC (09:40:03 BST), session18273,
Maven1:24 plus5s queue wait:64 tests, zero failures/errors/skips. It comprises
donor45, actual-preset Hyper AIZ return10 and nine literal locked-on PAL credit
tables. PAL tables cover signed delay expiry, both text parts, two replay cycles
and a single completion edge. The revised AIZ return10 establishes production
preset selection; the earlier matching raw-width checks remain historical
geometry evidence.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kCreditsState#everyLockedOnPalTableReplaysDelayBothTextPartsAndSingleCompletion,TestS3kEndingDonorSceneEntry,TestS3kRescueFlightChildren#actualHyperSceneHandoffAllocatesOnlyTheNativeAizReturnGroups' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

A separate production-region lifecycle check completed08:44:17 UTC (09:44:17
BST), session78089, Maven1:33 plus57s queue wait:12 tests, zero
failures/errors/skips. Nine declared D01 Sonic/Tails/team progress entries and
three actual SSZ2 Knuckles final-fight continuations select REGION=PAL before
boot, assert the literal PAL speed through the production coordinator, replay
real phases, complete both credit parts and reach their actual title-return
boundary. Knuckles retains the existing declared410766 VInt/prefix; this does
not promote that comparison-derived historical clock to an independent native
reference. Audio is disabled in these GPU lifecycle checks. They establish
PAL-table production selection and per-frame lifecycle behavior, not a 50Hz
wall-clock, PCM or whole-scene BizHawk parity claim.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kEndingLifecycleCapture#palEndingCompletesBothCreditPartsAndReturnsToTitleWithPhaseReplay,TestS3kKnucklesEndingLifecycleCapture#palFinalFightDepartsThroughBothCreditPartsAndTheActualTerminalWithPhaseReplay' "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Both commands use the original, identity-verified absolute S1 REV01, S2 REV01
and locked-on S3K paths as applicable. No runtime source changed in this slice;
combined follow-up validation and integration remain pending. Consumed local
redirects are removed after this summary.

### Ordinary S2 RAM and matching-controller binding qualification

The inherited ordinary S2 controller returned a null RAM snapshot. On unchanged
`99bfb689d` runtime, three new MGZ-hosted S2 donor regressions fail only that
assertion: session40516,3 failures/0 errors/skips,26.843s Maven, completed
08:46:39 UTC. The repair captures existing core/drain/palette fields, keeps
immutable normal scripts through revert, and resolves each current world-owned
Obj7E on activation/revert. It replays no activation audio, allocation or ring
writes while restoring. The shipped signed delay/drain and high-byte-only
`Palette_frame` residual remain unchanged.

The first controller/default/KiS2/spindash check passes24/0/0/0 (26357,
08:49:54 UTC,1:17 Maven,4s wait). Affected binding check58625 has55 tests and
five S2 Tails/+2 reverse-boundary failures (08:53:19,1:20,0s wait). Exact
diff24604 isolates captured `playerExtra.jump`1664 (`$680`) becoming2048
(`$800`):45 tests/5 failures/0 errors/skips,08:57:05,30.727s,99s wait. The
currently attached S2 owner had decoded a tiered S3 scene snapshot before the
scene adapter restored the actual binding. Matching-owner RAM must be reapplied
after the resource swap in both directions, not merely to the previous live
controller.

The next60-case candidate73808 retains59 passes but its new transforming-owner
entry detects the early scene's normal-physics overwrite: expected2048,
actual1664 (09:07:03 UTC,1:21,16s wait). A default-false local S3 scene-physics
hook now lets the private bound scene preserve captured physics even before
its first powered-flag writer. Candidate43844 retains59 passes but the two new
real-entry vectors encounter quake index254 (09:13:41,1:21,105s wait). The
opposite interim crossing had decoded S2's unset presentation tier `-1` as
packed S3 quake255; the subsequent binding restored the ordinary owner but left
that cached scene resource poisoned. Both local owners now reject the other
snapshot shape before the matching adapter reapplies its full RAM. The scene
compares exactly `-1`, because legitimate packed S3 companion fields may be
signed-negative. A duplicate scene override stopped22266 at compile
(09:17:11,48.346s):zero tests, zero behavioral credit. It was folded into the
existing render-context override.

A further fresh-entry obligation restores before entry from an actually
progressed17-frame native Super palette. Fresh ensure replays the scene's
immutable constructor/load-ROM baseline after binding, while active restore
replays captured progressed state. The source baseline is never a trace row,
future scene or fitted clock. New boundary vectors43178 pass3/0/0/0
(09:22:33 UTC,1:17,0s wait), covering actual SUPER with its allocated stars,
TRANSFORMING, and progressed native→before→fresh-forward entry. The active
ordinary context declares a running incoming HUD timer; these are local
owner-boundary witnesses, not cold campaign continuations.

Final coherent request61774 passes64/0/0/0 at09:26:06 UTC (10:26:06 BST),
Maven32.626s, no wait, from `99bfb689d` plus the repair:
ordinary S2 full-world3; native ending-controller11; donor binding48;
ordinary jump1; debug-grant1. All six newly introduced world/binding cases
exercise two complete forward/reverse registry cycles. The final donor48
includes the45 production-preset/progress rows already qualified for its
explicit local scene scope. Completed checks use the original identity-verified
S1 REV01/S2 REV01/locked-on S3K absolute paths; early KiS2 neighbors also used
the existing independently classified lock-on image. There are no ROM skips.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestSonic2DonorSuperStateRewind,TestS3kEndingDonorSceneEntry,TestSonic3kEndingSuperState,TestMutatorDebugRingGrant,TestPlayableSpriteMovement#s2JumpTriggerStillActivatesSuperSonic' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

The red command selected only `TestSonic2DonorSuperStateRewind`; initial green
selected it plus `TestSuperStateController,TestCrossGameSuperSonic,TestKis2SuperStateController,TestSuperSpindashTable`.
Affected58625 selected ordinary3, donor45, `TestSonic3kModSuperFormGate`, debug
and jump;24604 selected donor45 for exact messages.73808/43844 selected
ordinary3, donor and native ending11.43178 selected
`TestS3kEndingDonorSceneEntry#ordinaryPoweredDonorRamRestoresAfterItsMatchingControllerIsRebound+progressedNativeSceneDoesNotSupplyRamToReplayedFreshEntry`;
22266 selected only the former and failed compile. All use the same local
queue/lean/EGL/unset-desktop/null-driver shape. No public snapshot/API shape or
settings changed. Independent read-only review accepted matching-owner and
constructor-baseline boundaries. No full native pixel, PCM, ordinary S2 cold
route or additional cold donor-campaign claims follow. Consumed nine local
redirects are removed; combined follow-up smoke/integration remain pending.

### Initial collision publication and real checkpoint boundaries

Root's `feature/ai-s3k-parity-coverage`, parent `9f6475aa0`, found a real
LRZ2 checkpoint death/title-boundary capture error: erased horizontal spike
balls from SST5..12 remained in both collision views; SST5 already held a
star-post child and SST6..12 were empty. The capturing adapter belonged to the
current manager. This was not a stale world or a missing recreation factory.
The shared execution wrapper published before its caller's range deletion,
whereas `Sprite_CheckDeleteTouch3` / `loc_1B666` checks range before AddList.
Publication now follows the actual caller's deletion/range tail, preserving
routine execution, solid completion, slot order and the list owner's native
pending-delete eligibility. Fixed initial handlers publish after their wrapper.
A separate full-reset regression proves that retaining collision buffers while
clearing actor identities is also invalid; reset now retires both views. No
public API, snapshot shape or slot encoding changes.

Rejected diagnostics: 29060 (5 tests,1 failure,1 error),38099 (1 error),
7748 (25 tests,1 failure,1 error;23 pure cases pass),72498 (2 tests,1 failure,
1 error),20644 and34187 (each2 tests,1 failure,1 error) kept the LRZ missing-owner
error or exposed invalid SSZ setup assumptions.64116 is the independent pure
full-reset red (1 test,1 failure).68890 and22572 stopped at test compilation
and ran zero cases. The reset-only candidate did not close the LRZ error.
A diagnostic that checked only the previous list missed initial-build removals
from the current list; all temporary instrumentation was removed.

Corrected focused gate94957 completes90 tests with1 new SSZ opening assertion
failure,0 errors/skips at10:39:13 UTC (31.502s Maven). Its89 passing cases are
84 manager/collision/reference-closure and startup consumers, the independent
15-placement census, and four local layout contact/two-death witnesses. The
failed new opening assertion stopped at the arrival script's saved index1;
this is not contact with the authored subtype2 post. Replacement9651 and
47382 each run1 test with1 no-contact bound failure; the latter uses the
already maintained cold-complete320 inputs and remains alive through6000.
96786 uses the existing upper-walkway boundary7911 and passes1/0/0/0 at
10:45:09 UTC (26.005s Maven), naturally saving literal post2 ($640,$5E8) in
`GameplayCaptureSession` with no position or checkpoint writes. Thus90 distinct
focused identities pass across retained89 plus corrected1; this is not one
pristine90-case invocation. The harness now drains pending render tasks after
each production step, matching the established route helper; this final harness
contract will be included in the pending product qualification.

All checks use normal queued Maven, Java25, explicit surfaceless EGL, unset
DISPLAY/WAYLAND_DISPLAY, null OpenAL driver and original absolute identity-
verified ROM paths. The gate selection is:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestObjectManagerRewindSnapshot,TestObjectCollisionResponseList,TestObjectManagerRewindReferenceClosure,TestLrzSszCheckpointRoutes#independentPlacementInventoryIncludesTheNoPostKnucklesFinalArena+firstPostOfEachLayoutHasARealContactAndDeathBoundary+ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

The corrected standalone selector is
`TestLrzSszCheckpointRoutes#ordinarySszOpeningPhysicallySavesTheFirstAuthoredPost`.
The separate local SSZ products declare saved index1 before initial dispatch to
select the no-arrival branch. Their physical posts2..4 remain unseeded; this
synthetic local bootstrap is not an authored post1 or a cold-route claim. Full
880 products, composed source guards/traces, combined smoke, integration and
push remain pending. User-requested emulator screenshot spot checks are added
to this follow-up; unchanged verified native screenshots can be reused, while
current engine pictures retain exact source/input and observation provenance.

### Stable player-key association encoding

The route worker's eight-act gate (1186 tests,1 failure,8 setup errors,0 skips)
found AIZ2 production entry row140: S2-donor Tails at800 with seven Sonic
followers. Immediately restoring its six-tick world snapshot swapped
AutoSpin's serialized refs5/3 at scalar bytes26/61. The live associations were
unchanged. Independent source review traced `RewindCodecs.MapCodec.capture`
to `IdentityHashMap.entrySet()` bucket order; clear/reinsert and recreated Java
identities can change that order. Root owns the shared repair; the worker owns
the actual AIZ2 consumer and the separately corrected mock-art setup.

On `eea476471`, a deterministic replacement-identity/bucket-order regression
49321 fails1/1/0/0 at10:56:08 UTC, scalar byte4 expected1/actual0. Its prior
69723 attempt stopped at a missing test import, zero behavioral credit. The
fixture preserves two distinct players with the same character label, a null
key, all three values, the final map instance and actual live iteration order.
Capture now orders only player-keyed `IdentityHashMap` associations by their
existing `PlayerRefId`; it changes neither the live map nor linked/sorted map
ordering, public API or snapshot schema. An independent ordered-map negative
retains sidekick-before-main order. Other map shapes remain unchanged.

Coherent lean request29652 passes27/0/0/0 at10:59:18 UTC (1:23 Maven): player
references5, collections8, object references4, helpers6, records2 and field
scratch2. This is a pure serialization qualification, not yet the actual
AIZ2 replacement or combined smoke. The command is:

```bash
python3 tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestRewindPlayerReferenceCodecs,TestRewindCollectionCodecs,TestRewindObjectReferenceCodecs,TestRewindHelperCodecs,TestRewindRecordCodecs,TestRewindCodecFieldScratchReuse' test -B
```

The actual affected consumer is
`TestS3kAiz2EntryMatrix#normalEntryAppliesTheProductionPresetAndEveryDeclaredParticipantThenReplaysTwice`,
parameter140 in the worker's recorded row inventory. Preserve full world
comparisons and actor identities; new/worsened failures still block delivery.

### Normal S2 Tails profile and checkpoint setup attribution

On composed `c92dae73a`, the first full physical checkpoint gate16301 runs
882 tests:155 failures,0 errors/skips,727 passes at11:09:42 UTC. The new
880 products expose95 S2-donor Tails replay differences (camera/body/balance)
and60 LRZ1 post5 setup contacts. Neither is labeled an inherited failure.
The derived S2 normal controller profile was always Sonic: native Obj02_Init
/loc_1B8D8 retains Tails' $0F radius, single-facing balance and its object-balance
shift. Captured live radius fields alone concealed the wrong derived profile
until forward replay. Matching normal profile dispatch now follows the existing
Sonic2PhysicsProvider character rule; KiS2 has its independent controller/profile.

Independent red43014 completes5/5/0/0 at11:20:43 UTC. Its direct normal
S2-donor Tails owner restore changes the entire literal profile from Tails to
Sonic; the four recorded product failure kinds retain their comparisons.
The repaired focused62698 completes92 tests,5 failures,0 errors/skips at
11:37:20 UTC (1:16 Maven; queue waiting excluded). All84 controller/ending
consumers pass: ordinary S2 owner4, shared super7, cross-game3, KiS2 3,
spindash8, native ending11 and donor binding48. Three physical-product
witnesses also pass, but5 stop before replay because the attempted universal
64px approach leaves the intended ledge or behind terrain. That setup change
is rejected. These87 passing identities are not a green92-case invocation,
and the full880 corrected products remain pending. Independent read-only review
confirms character/profile dispatch and the direct regression's narrow scope.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestLrzSszCheckpointRoutes#firstRecordedFailureOfEachKind+firstPostOfEachLayoutHasARealContactAndDeathBoundary,TestSonic2DonorSuperStateRewind,TestSuperStateController,TestCrossGameSuperSonic,TestKis2SuperStateController,TestSuperSpindashTable,TestS3kEndingDonorSceneEntry,TestSonic3kEndingSuperState' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Original identity-verified absolute S1/S2/S3K paths execute without ROM skips.
Only the normal S2 controller and its direct regression are committed in this
repair; the physical matrix and its corrected production fade driver remain
separate qualification work. No public API or snapshot shape changes.

### Single fade ownership in captures and actual death drivers

A read-only peer review found that GameplayCaptureSession.step explicitly
retained the older direct-level UI fade tick even though HeadlessGameBoot
attaches a runtime-ready context and GameLoop's PLC lifecycle owns the same
manager. Live Engine already guards its UI fallback with
`!loop.ownsGameplayFadeLifecycle()`. Root's actual-session59550 red confirms
frameCount2 after one presentation of an independently started90-frame fade,
expected1. The request completes9 tests,2 failures,0 errors/skips at
11:40:56 UTC (25.826s Maven): the other failure is the invalid LRZ1 post5
left-wall approach; seven contact/two-death witnesses pass with the corrected
loop-only death driver. No tests or runtime values are supplied by native rows.

The direct capture now uses Engine's existing ownership predicate for every
launch. Title-first behavior is unchanged; pre-runtime UI fallback remains.
A separate local post5 setup approaches from the open right ledge, preserving
the literal8px horizontal activation window, untouched-post assertion and
actual contact. Universal64px and post5-left32px approaches were rejected as
terrain setup failures, not repaired in gameplay.

On `be23fffb5` (qualified Route79630ebd entry/early-donor composition),49253
passes48/0/0/0 at11:45:57 UTC (1:23 Maven): open-right post5 real contact and
two deaths1; actual direct fade owner1; deferred destination1; native S1/S2
omitted-title movement2; fully drawn title/data-select/results consumers8;
data-select launch3; configured donor entry28; S3K ending entry4. The command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestLrzSszCheckpointRoutes#lrz1PostFiveHasARealContactAndDeathBoundary,TestGameplayCaptureFadeOwnership,TestGameplayCaptureFreshLoad,TestGameplayCaptureSkippedTitles,TestGameplayCaptureFrameRendering,TestGameplayCaptureDataSelectLaunch,TestGameplayCaptureDonorEntry,TestGameplayCaptureS3kEndingEntry' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

All three original absolute ROM paths execute, with no skips. Root separately
composes the qualified camera guard4fd998fa9 as22d5e6ffc. Its final combined
capture-boundary consumers, complete880 physical products and affected cold
route timing remain pending. Short placement/contact checks run before the
independent ordinary SSZ opening and the long product method. The four duplicate
frontier diagnostic parameters are removed; the distinct post5 terrain witness
remains. This source repair does not certify historical movies under the new
driver or native whole-scene/PCM parity.


### Checkpoint product qualification (2026-10-14)

The final `TestLrzSszCheckpointRoutes` is test-only on composed
`a8c9f892e855855f96c7ecad368e127880c9af3e`; test SHA256 is
`3ed4c7346c79cd06d694e7fc17cf625aaa941d4951db606702734d631115f818`.
Its15-post literal census contains six LRZ1, five LRZ2, one LRZ boss and three
SSZ1 posts; SSZ2 Knuckles has zero.880 products comprise430 supported solo
and450 six-roster examples across320/352/400/528/800. Every contact is ordinary
movement from outside the native8px touch box. LRZ1 post5 approaches from its
open right ledge; the rejected universal left64px setup misses five contacts,
and its left32px variant misses this terrain-specific post. These are setup
negatives, not a reason to enlarge collision or write the target checkpoint.
SSZ local saved index1 selects the no-arrival branch before initial objects;
physical posts2..4 stay unseeded. Its independent maintained cold opening
saves post2 through ordinary input with no position or checkpoint writes.

The previous composed565257d23 gate90158 passes931/0/0/0 at11:55:43 UTC:
887 checkpoint cases plus28 donor,1 capture-fade,11 fresh-load and4 ending-entry
consumers (4:01 Maven execution). That version does not yet capture/replay a
released incoming world. Read-only review identifies that missing obligation
rather than accepting graph existence as enough. The final helper checks one
lost life at assembly and title release, then captures the full newly loaded
world and runs two forward/reverse45-input cycles after EACH actual death,
requiring an alive leader, unchanged lives/index and usable configured owners.
It waits through actual LEVEL/title/fade/control and fresh-assembly gates;
no snapshot-key filtering, retired-world restore or hidden restart is accepted.

Six independent pilot cases pass6/0/0/0 in request4813 at12:30:40 UTC (1:16
Maven). The complete final class request99024 passes888/0/0/0 at12:40:30 UTC,
269.8s in class /4:54 Maven; queue waiting is separate. It includes the880
products, census, four layout witnesses, distinct post5 witness, S2 Tails with
seven followers at800 witness and independent cold SSZ post2 contact. The
short witnesses are ordered before the cold route and large matrix.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestLrzSszCheckpointRoutes' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Pilot selector is
`TestLrzSszCheckpointRoutes#firstPostOfEachLayoutHasARealContactAndDeathBoundary+lrz1PostFiveHasARealContactAndDeathBoundary+s2TailsWithSevenFollowersReplaysAfterEachCheckpointDeath`.
Both commands use Java25, normal queued admission, original absolute
identity-verified ROMs from root AGENTS.md and no ambient desktop. Each process
is observed terminal0 before source writes. This is focused local contact and
load/replay qualification, not full-suite, per-product cold route, native
visual/audio or overall parity acceptance. Remaining source fixes, strict
frontiers, combined smoke/guards, integration/push and cleanup remain pending.
Consumed redirects are removed after this durable account.


### Existing checkpoint driver qualification (2026-10-14)

Source `262e9bbb10bb317d183dbf289dcfa8568fa0258c` plus four test-only files
removes manual FadeManager updates from MHZ/FBZ/DEZ's actual-death drivers.
Each loop asserts production fade ownership, drains pending rendering, waits
through title/fade/pending completion/fresh assembly/control release, and
checks exactly one life at assembly and release. `CheckpointIncomingReplay`
then compares all registry keys around two forward/reverse45-input cycles
after EACH death, checking current manager/runtime, alive leader, unchanged
lives/post and configured owners; no raw branch crosses a load.

The short first-post native320/S2-Tails800 witnesses pass12/0/0/0 in93678,
completed12:58:56 UTC (1:20 Maven). Complete changed consumers in63733 pass
1077/0/0/0 at13:13:31 UTC (3:32 Maven): MHZ315, FBZ440, DEZ322. The latter
includes280 standard products plus42 duplicate-S1/trio cases and the actual
placed inverted-gravity post5 branch. Queue waiting is separate. Original
ROMs, normal admission, Java25, surfaceless EGL, unset desktop and null OpenAL
match the prior checkpoint command. All requests are observed terminal0 before
source writes. Other unchanged physical/contact checks are not repeated.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestMhzCheckpointRoutes#physicalCheckpointSurvivesTwoRealDeathReloads+supportedDonorTeamsKeepTheirRulesAndArtAcrossPhysicalCheckpointDeaths,TestFbzCheckpointRoutes#physicalCheckpointSurvivesTwoRealDeathReloads+supportedDonorTeamsKeepTheirRulesAndArtAcrossPhysicalCheckpointDeaths,TestDezCheckpointRoutes#physicalCheckpointSurvivesTwoRealDeathReloads+supportedDonorTeamsKeepTheirRulesAndArtAcrossPhysicalCheckpointDeaths+extendedTeamsSurviveRepeatedCheckpointReloads' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Pilot selector uses each of those three classes' method
`firstPostOfEachActAndS2TailsReplaysTheIncomingWorld`.
SHA256s of the qualified test files are MHZ
`a6939b3c717f7bc97e1ff06e0be60a4d251906fd0c5f2aa7db451b9fbb99d3b5`,
FBZ`59cd8fb315c99224254d7cb9a8956bc74d196d9a3f120ba810db249fb5211594`,
DEZ`8d90eccc2f335e37face11948942cd4ed1951f8daa2d55a8331a5323748d2176`,
shared helper`bfb5effb0f65b2fe20fc091a54cface69ebb0ff4f514296d66670bb6dfcfc8e7`.
This adds real incoming-world replay, not per-product cold traversal or native
visual/audio certification. Existing MHZ enumerates Sonic/Tails/pair; Knuckles
and additional roster products are the next gap, not silently counted here.
SOZ/DDZ's older manual fade drivers and other remaining obligations still need
their affected follow-up. Combined smoke/guards/integration/push/cleanup remain
pending. Consumed local redirects are removed after this account.


### MHZ Knuckles checkpoint qualification (2026-10-14)

Source64f419a3b plus final test draft keeps the existing315 Sonic/Tails/pair
and donor selectors explicit and adds two independent Knuckles methods:
`knucklesPhysicallyTouchesAndReplaysEveryPost` (nine actual contact/replay
cases) and `knucklesPreservesItsRouteAcrossBothDeathsAndIncomingWorlds`
(45 real two-death/released-world products across all posts and five presets).
Request50826, Java25/normal queued admission/surfaceless EGL/original three
absolute ROMs, selects these methods plus`TestBubblerCharacterIdentity`.
Terminal1 at13:19:59 UTC: MHZ54/0/0/0 (10.55s class); the separate Bubbler
five cases have two literal Sonic registration-alias rolling failures.
The latter prove a remaining Obj_Sonic-identity branch defect; MHZ's qualified
360 distinct lifecycle products span this run and63733, not a fabricated
single360 pass. Act1=200 and Act2=160. No new cold traversal/native acceptance.
The source freeze ended before the subsequent owner correction.
