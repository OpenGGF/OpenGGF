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

**Native 50 Hz follow-up (ending-pal lane, 2026-10-16).** This adds one declared
native PAL product: Sonic/+2, D01 at 320, from the scene through credits and terminal to
`Title_Screen`.

Native is GPGX with only `Region=Europe` changed; native-reference setup is otherwise as
before. The engine runs at `REGION=PAL`, 50 Hz, with LIMITED + RAW_RGB output. Both
queued runs started from the lane worktree:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -Dexec.executable=python3 '-Dexec.args=<lane>/run_native_pal.py' exec:exec -B
python3 tools/testing/maven_queue.py -Dmse=off -Dexec.executable=python3 '-Dexec.args=<lane>/run_engine_pal.py <head> %classpath' exec:exec -B
```

Native finished 20:24:07 UTC (1:10 Maven) and the engine on `e94f6c4e7` finished
20:30:58 UTC (10.9 s).

Results:
- Raw RGB matches at polls 1–3071.
- Level frame, V-int and RNG are equal for 13,888 frames.
- Credits durations are equal (7,415 frames).
- First divergence: LF 3778, where native PAL finishes the AIZ1 Kos modules four
  frames earlier. That gap belongs to the Kos CPU-budget model.

The other eight PAL products remain open. Details are in the
[ending matrix](../validation/levels/s3k-ending.md#native-pal-50-hz-sonic2-product-ending-pal-lane-2026-10-16).

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


### Bubbler persistent identity and literal radii (2026-10-14)

Source64f419a3b plus five literal tests gives two genuine follower-Sonic
rolling failures in50826 at13:19:59 UTC. Replacing registration-name authority
with CharacterKey makes all five pass in14102 at13:28:25 UTC; its one legacy
red is a Tails-named generic sprite that still declares Sonic identity.
After explicitly declaring that fixture's identity, seven actual character
vectors add Knuckles controls:59172 at13:30:17 UTC has two genuine native
radius15/Java19 failures, while all six legacy and two recreation cases pass.
Native`Obj_Bubbler`64719/`loc_2FC5A` owns both branches: Sonic clears Roll,
9/19,Y−5; non-Sonic retains Roll,9/15,Y−1. No frame/fixture/gameplay-row fit.
The object uses NativePositionOps for the non-Sonic word adjustment and keeps
all eligibility, pickup ordering, clocks and consumed-state owners unchanged.

Final production SHA256`3e7fa18a88a569edf34a078978a4d4dcccb9d43273bd20c4faf71b79b60c88b7`
has seven typed vectors, two full object recreation cases and two ROM HCZ
breathing consumers green in62555 at13:34:15 UTC. That35-case mixed pilot
has one remaining legacy radius assertion copied from its generic standing
default. Only that test premise changes:17992 at13:36:36 UTC passes all six
legacy cases plus390 SOZ products,396/0/0/0. Queue waiting is separate;
actual terminal results precede every source edit. These are focused scopes,
not a fabricated green35 invocation, full suite or native whole-scene credit.
Selectors are`TestBubblerCharacterIdentity,TestBubblerObjectInstance,
TestS3kUtilityMotionRewind,TestS3kBreathingBubbles`; first two pure negative
runs use --lean, actual ROM consumers normal queued admission/surfaceless EGL
with original absolute ROMs, Java25, no desktop and null OpenAL. The source
is unchanged between the final typed pass and corrected legacy pass.


### SOZ and Doomsday actual reload qualification (2026-10-14)

Final three test drafts on d35fbaa41 plus the independent Bubbler owner repair
replace manual FadeManager service with the asserted GameLoop owner and
pending-render drain. Eight independent SOZ witnesses and ten DDZ cases pass
in62555 at13:34:15 UTC; its only red is the separately corrected legacy
bubble radius assertion. DDZ now has two actual natural ring-out/fall/kill
reloads at every preset, exact life loss and released-flight full-world replay
after both loads; five declared ending-boundary cases retain progression-save
and actual ending-load isolation. These are not preceding-fight certifications.

17992 completes terminal0 at13:36:36 UTC,396/0/0/0 (1:36 Maven): all300
SOZ solo products (10 physical posts × six supported character/donor pairs ×
five presets) and90 finite Sonic-led mixed/duplicate team products, plus six
corrected legacy Bubbler tests. All SOZ products now use two actual deaths,
exact life loss at assembly/release, fresh runtime/manager, old-history isolation
and two complete forward/reverse45-tick world cycles after EACH release.
Solo physical-contact replay also compares all keys twice. The independent
short witnesses are ordered before the large products. No setup calls a post's
save/activation or imports native gameplay values. Stale team rock pointer is
an explicitly declared outgoing stimulus; native reload must clear it.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestBubblerObjectInstance,TestSozTeamCheckpointResetProduction#mixedAndDuplicateTeamsSurviveTwoConsecutiveCheckpointReloads,TestSozCheckpointReloadProduction#touchCheckpointThenDeathReloadsItsNativePosition+otherPlacedPostsActivateAndReload' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Pilot selector is`TestBubblerCharacterIdentity,TestBubblerObjectInstance,
TestS3kUtilityMotionRewind,TestS3kBreathingBubbles,
TestSozTeamCheckpointResetProduction#shortCheckpointTeamsReplayReleasedIncomingWorlds,
TestSozCheckpointReloadProduction#shortSoloCheckpointsReplayReleasedIncomingWorlds,
TestS3kDdzLifecycleProduction`. Same actual Java25, original absolute three
ROMs, normal admission and no desktop. Exact SHA256s: solo
`a3af5378c0d894d42fb86e6f7aa0fa9665405677f3e4a5ddd98c64320542bfc7`, team
`3d3bcc58d544e63b32c87620358e546c34c61a459cf86aecdfaacb62d51f9098`, DDZ
`7580299b9f92bcbae446c9226013e6b475afb113550dd4bdfcd789b6d695ec01`.
Actual terminal is observed before writes; consumed redirects are removed after
this account. Broader mechanism/donor/roster/cold/native/auxiliary products,
strict frontiers, combined smoke/guards, integration/push/cleanup remain pending.


## Native opening-bridge contact follow-up (2026-10-14)

Independently fresh native320 Sonic+Tails and engine scenes consume original
AD40 suffix448920 after a separate neutral initial ProcessSprites pass,
polls0/LFC0. Losing that first ordinary row caused the earlier Death Egg X
drift; it was a host-boundary issue. Native blue/gold circles are present in
both captures; a player-row SAT count is not a circle-row overflow witness.
Corrected P1 coordinates/camera agree through poll1341. At1342 the native
bridge moves to894/3208 and acquires P1 at808/3179; engine had808/3192,
airborne. Decision callbacks and full RAM identify the exact bridge standing
bit, with no shared terrain landing. Updraft first moves P1 to3192; the native
surface3208-9 gives d0=-16 and relative Y3179. The former d2=16 profile
misses that contact. Existing provider flags now select d3, reject exact zero
and preserve the relative snap before TouchFloor. Shared solver/clocks,
allocator and fixture-directed runtime branches are unchanged.

Literal `TestSszBridgeNativeTopContact` on656e21eb1 plus its test draft has
10/6/0/0 in37975 at13:57:53 UTC. Final affected81009 is terminal0 at
14:02:18 UTC,54/0/0/0 (1:26 Maven): ten controls, one original-controller
contact/replay,13 profiles,25 bridge/death/reload capture products and five
Knuckles bridge cases. The real original-input case compares every registry
key twice around45 inputs, retaining current owners/lives and actual ride;
no raw restore crosses a load. Normal queued admission, Java25, surfaceless
EGL, original absolute ROMs, null OpenAL and no ambient desktop are used.

Native/engine external task evidence: `native-ssz-contact-pair-ntsc-v1` and
`ssz-contact-boundary-656e21eb1-e3b0c44298fc-v1` under the task spot-checks.
Their provenance/command JSON pins exact argv, original ROM/BK2, immutable
bf3c539 video host and passive decision instrumentation. Native762427 epochs
1791985804.0648851–1791985816.9969594; Java762737/mainSszContactBoundaryProbe
1791985817.4445887–1791985820.1126516, both0 (request8791 at13:50:20 UTC).
Its C# adapter SHA256 is6c8bf0e09852af0ab0df216ff969a1a914c11841264e30f7b181fc6592c459c0.
The callback CSV mislabeled06/07 as radii: those are visual height/width24.
Actual9/19 is verified at1F/1E in unchanged raw RAM; no radius inference uses
those mislabeled columns. OriginalRGB and prepared/published identities are
retained. The prior14908 helper int/uint compile error is terminal1 at
13:47:54 UTC, before emulator/engine-probe launch; engine compilation succeeds.
Actual child intervals are relayed to the authorized performance coordinator.
This is bounded owner/contact evidence, not whole-scene pixel/audio, every
cold product, full suite or combined delivery. Maintained cold completions
and corrected final engine spot captures follow. Consumed target redirects
are removed after this durable account.


## SSZ cold boundaries and bonus return pilots (2026-10-14)

The corrected opening bridge is retained. Matched unchanged maintained route
checks on pre-bridge656e21eb1 and candidate9c9c27f67 both fail the same two
expectations: native320 lacks enough neutral tail for DEZ, and paired800 looks
for its intro at an obsolete fixed input. This attributes neither failure to
the bridge correction. Independent ordinary-loop observation on90897f41d
shows both320 drawing policies load DEZ at20550, assemble20551 and release
controls20633;800 loads20874, assembles20875 and releases20957. The actual
launch fade takes21 color presentations plus black-completion publication.
The previous local route assertions were baked into double-serviced fading;
runtime clocks are not changed to fit those expectations.

The320 author script now appends180 neutral rows. All20540 original rows are
preserved byte-for-byte, total20720; InputLogAuthorTool compilation and BK2
round-trip are checked. The tests observe the actual load and assembly, retain
all native fight/health/ring predicates, and compare every registry key in two
forward/reverse45-input cycles. Incoming assembly and released-world windows
are independent; no raw restore crosses a full load. The native320 completion
records50 windows and paired800 records44, including actual DEZ introduction
and release. Older fixed-row expectations are superseded, not removed without
an equivalent incoming-world obligation.

Request12506 is terminal0 at2026-10-14 15:17:45 UTC,1:53 Maven:
`TestSszColdRouteCapture,TestSszWidePairedColdRouteCapture,TestS3kBonusStageReturnProducts`
passes8/0/0/0 on b930df4868e01a116f6e76578101080ef98791ca plus these drafts.
The command uses normal queued admission, `-Dmse=off`,
`-Dopenggf.headless.gl=surfaceless-egl`, the exact three-class `-Dtest` selector,
`test -B`, original absolute S1/S2/S3K ROM properties, Java25,
`LUA_BIN=/usr/bin/lua5.4`, null OpenAL and unset DISPLAY/WAYLAND_DISPLAY.
Five bonus pilots cover logical production requests, actual stage load and
return, fresh managers and retired history; they do not claim physical portal
traversal, reward mechanisms or cold routes. Gumball and Pachinko have two
18-tick full-world replay cycles; Slot advertises no whole-stage rewind and
is checked by ordinary18 ticks. All five origins have two45-tick full-world
cycles after real fade/title/control release. The [bonus matrix](../validation/levels/s3k-bonus-stage-transitions.md)
records supported pilot axes and remaining obligations.

The passive corrected9c engine scene1432 observations exactly matches native
P1 X/Y, X/Y speed, ground speed, mapping, camera/copies, level counter and rings.
P2 still differs at one X row119 and74 Y rows within119–209; this is an open
arrival ownership/allocation issue, not accepted intentional divergence or
whole-scene RGB/audio parity. Native/engine source domains and original images
remain in the external task directory. Launch intervals have been relayed to
the performance coordinator; compiler helpers retain the intended source
worktree build lease, including external class output, and release before
queued tests. No performance result is inferred from these checks.

Qualified test hashes:
- `src/test/java/com/openggf/tools/TestSszColdRouteCapture.java`: SHA256`d2c7be7a67708b0ab1ab9f54b9e604508f79df498d054b994c1bdb73cb7b946c`.
- `src/test/java/com/openggf/tools/TestSszWidePairedColdRouteCapture.java`: SHA256`2d23891c38affa20014278c385d080700faae1bf455efb5975e399149bf2a5c7`.
- `src/test/java/com/openggf/tests/TestS3kBonusStageReturnProducts.java`: SHA256`37ac77e5fbb8e44c720696317afc002fbf5c9d4ce6a9b280cdfbf191eeb7fefe`.


## Finite bonus boundary qualification (2026-10-14)

Coherent69e4f51d3 plus `TestS3kBonusStageReturnProducts` final draft passes
425/0/0/0 in70993, terminal0 at19:17:41 UTC. Maven4:51, class218.0s,733s
queue waiting is excluded. Five independent pilots run before the420 finite
products (existing140 generator × three stages). All five viewports including
528, all six admitted main/donor pairs and the finite solo/mixed/duplicate/
seven-follower rosters now traverse real production loads and returns. Ordered
CPU chains and distinct duplicate identities are checked before entry and
after return, along with ROM-backed renderer/animation resources. Every returned
world compares all registry keys in two45-tick cycles and both restore
directions; interior rewind follows the advertised two-supported/Slot-unsupported
contract. Per-case configuration and donor/session globals are restored.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kBonusStageReturnProducts' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Final test SHA256`318a460ca946bdfe420eb31c95ad253360f56a68ceb01b8edaeb02d0821436be`. This closes the finite logical load/return
product; physical portal contact, actual reward/exit mechanisms, matched native
whole-interior pictures/audio and wider campaign obligations are still separate.
Consumed Maven redirect is deleted after this durable summary; no validation
receipt or archived test log is retained. This is focused qualification, not
combined smoke/guards, full-suite or integrated delivery credit.


## Qualified native video port integration (2026-10-14)

TraceChaser diagnostic sourcebf3c5391c948dbf31018f8779e805437fd1621bf
is integrated into origin/main as4fdf73880f93a3172d23bf9e157b15de3c78fc32
and the exact remote SHA is verified. Integration uses a separate owned main
worktree; the shared detached native checkout is preserved. The merged file
tree is byte-identical to the qualified feature tip. Its13 native host cases
and32 Python policy cases retain their prior scope; repository, reachable
history and documentation scanners pass on the integration tree, including
the new merge commit. Root independently reviews current-generation invalidation,
owned array copies and strict domain lengths. No canonical fixture, generic
callback port, recorder profile or ordinary Advance flags change. This is
focused diagnostic-port qualification, not a full recorder/engine suite or
ending pixel acceptance. The parent gitlink records the pushed integrated SHA.

Additional passive SSZ observer72373 completes terminal0 at19:19:47 UTC
(16.324s Maven execution). Native main `SszArrivalAllocationProbe` launches
at1792005572.151551 /19:19:32.151558 UTC and ends1792005587.5887856 /
19:19:47.588791 UTC. Its adapter SHA256 is
`84adead99db9a42e4efb699220d22ea0d544c45fb5dc4e6e4fc1dcdbd633d3bf`.
Same original ROM/AD40 movie, title/bootstrap setup and1431 ordinary polls
as the retained contact pair; eight full state/VDP/image controls, including
complete1432-row state.csv, are byte-identical. The observer adds read-only
allocation registers and snapshots118/119/120, not gameplay writes.

Atpoll/LFC109, loc57D18 has A0=B128(slot4), A1=B000(Player1),
D0lowword20, byte38=FF,byte39=03,word3A=0020. Collision-reset slot2
(code6C2C) is occupied; slot3 is empty. CreateNewSprite4 returnsB0DE(slot3)
and D0low1D. Obj57DCC first executes atpoll/LFC110, the following object
pass. These are literal native register/slot facts supporting a local
continuation scan; they do not justify a fitted birth delay or shared allocator
change. Root arrival light/cycle retirement remain the same owning routine's
missing state. Engine repair and negative/green qualification follow separately.

Output `native-ssz-arrival-allocation-ntsc-v1` remains under the explicit
external task spot-check directory with exact argv/spec hashes/provenance.
C# compilation additionally holds the native source/classes worktree lease
and inherits its FD; normal Maven admission retains the baseline tree through
all execution. Actual child bounds are relayed to the performance coordinator.
No measurement credit, pause/preemption or foreign resource change is requested.


## Native SSZ arrival owner qualification (2026-10-14)

Candidate parent558e74833c2256688b4ef4bcb795006416056589.
Native72373's literal registers establish the owning scan and missing light
state. Production captures byte39/word3A, reads word_4670C from the ROM,
uses the carried DBEQ budget through actual live/reserved owners, gates the
helper on Sonic main/P2 and checks cycle zero before the settle light tail.
Existing shared allocator, physics clocks and public snapshot shapes are unchanged.

Matched pre-repair request38301 is terminal1 at19:32:10 UTC:21/21 failures,
zero errors/skips, covering early higher-slot helper dispatch, absent light
writes, erroneous Tails-led branch and immediate settle retirement. The three
pressure cases were added later and receive no fabricated negative credit.
Candidate79082 is terminal1 at19:54:33 UTC:143/6/0/0 (1:23 Maven,0s queue).
All119 neighbor identities and18 new identities pass. Six new fixture failures
are resolved without production changes: preloaded placement controller7/10
must be behind helper3 rather than asserted at native cold slot4; settle must
advance ordinary palette-ownership epochs instead of repeating direct updates.
Corrected exact-class21831 is terminal0 at20:09:21 UTC,24/0/0/0,25.233s Maven,
2.813s class execution,162s queue. All143 distinct affected identities are
therefore green across two runs; this is not one green143-case invocation.
An erroneously early unadmitted follow-up53114 is cancelled terminal130,
verified zero children/no JVM; it supplies no evidence and no foreign request
is changed. The retained writer5076 completes before21831 is submitted.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kSszArrivalContinuation,TestS3kSszArrivalHeadless,TestS3kSszKnucklesArrivalHeadless,TestS3kSszTeleporterPads,TestS3kHpzTeleporterHeadless,TestS3kHpzGraphRewind,TestObjectManagerFixedSstExecution,TestObjectManagerFixedSstInitialization,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
# Corrected24 alone, after the actual fixture-writer completion:
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kSszArrivalContinuation' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Passive recapture25248 terminal0 at20:12:52 UTC,3.242s Maven/170s queue:
`-Dexec.executable=python3 '-Dexec.args=<task-scratch>/run_ssz_arrival_engine.py %classpath' exec:exec -B`
through the same normal wrapper/cwd. Javac20:12:49.304367–20:12:49.669124 UTC
(epoch1792008769.304361–1792008769.6691213), external output directory, retains
the queued parent's source/classes worktree lease. Java main
`SszArrivalBoundaryProbe`20:12:49.672490–20:12:52.240184 UTC
(epoch1792008769.6724887–1792008772.2401783), JVM1647775 terminal0.
Observer SHA2566016797a066d4a595a996c82efcf3d6d76899610d737e0ecace6b5d45732a2bf;
production diff SHA2563f19b8b1d04c59c5174c2678f9af0016aad6a9cea0aaa5e3afa53cd9a649a976.
Original ROM/AD40 hashes and exact complete argv are external capture provenance.
No shadow runtime class, trace hydration or later input/gameplay write is used.

Both complete1432-row arms match the recorded P1 X/Y, X/Y velocities,
ground speed and mapping frame, P2 X/Y, cameras/copies, level clock and rings.
That CSV does not record P2 velocity or body/tail animation. Prior P2
one-X/74-Y deficits are removed. Pictures are inspected after reading state:
title-card suppression differs at input0, and the native published-producer
column is not sufficient to justify a global previous-row pixel shift.
That rejected shift worsens differences. Native330/current-engine330 still
has360 differing logical three-bit pixels; all71680 original-RGB pixels
separately differ because the conversion ladders remain distinct. This is a
bounded state/owner qualification, not full-scene pixel/audio, all campaign
products, combined smoke/guards or integrated delivery credit. External output
`ssz-arrival-boundary-558e74833-3f19b8b1d04c-v1` retains exact provenance.
Consumed target redirects are deleted after this account, never archived.


## Updated develop composition and conflict qualification (2026-10-14)

Qualified AIZ FallingShot/common-shot tip1255286c3616a96ef36fce7efde5dd92e3b2263b
is composed as774959d302c474d680c8b190e44e9787f93ad198. Both independently
appended skill lessons are retained without replacing the root's earlier
ending/SSZ lesson edits. Its worker707/0/0/0 and111/0/0/0 guards keep their
recorded scope; strict four controls and unchanged chain/alternate deficits
remain in the frontier audit. No unchanged broad check is repeated.

The root then composes updated destination7277cef4040af3ae95d86a794bce7f75a16cc483.
Five conflicts are resolved by owner intent: both S3K skill mirrors and both
independent implementation-pitfall sections survive; the stock-ROM identity
guard precedes the native HPZ timer/HUD initialization; the qualified raw/SST
flare retains stock Init, F4 outer draw and next-pass clear. Only an opted-in
warning has its captured bounded counter and absent-parent retirement. Its
birth Init precedes the admitted16..60 delay and a longer warning retains the
last raw pose, using ROM bytes rather than the old copied animation sequence.
The old stock16-update assertion becomes the independently qualified18-retained/
19th-clear native contract. Its mock fixture explicitly supplies byte_6913F.

Two failed compilation launches69583/47327 are terminal1 with zero tests:
wrong mock ROM-reader package, then an unnecessary checked exception handler.
Neither receives test credit. After the verified test-only corrections,
build-only23086 passes test-compile at20:37:45 UTC (24.596s, no tests).
Corrected focused65772 is terminal0 at20:39:33 UTC:99/0/0/0,27.467s Maven,
0s queue. Native flare19, SecondStrike policy12, profile8 and mandatory S3K
startup/loading/bootstrap/decoder60 pass. Read-only frontier peer review
confirms stock semantics, captured primitive warning state and both slot-order/
post-F4 replay coverage. Composed smoke/guards and final integration are pending.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off test-compile -B
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestSecondStrikeBoss,TestAizMinibossFlareNativeGraph,TestSonic3kLevelInitProfile,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Consumed root redirects are deleted after this account; no validation receipt
or archived invocation log is retained. The original pre-task integration
base5b2ff8e7851b99dd40998dfc41fbfd21c80031ef remains the combined validation base.


## Bonus portal and mechanism qualification (2026-10-14)

Source parente46f61820069f0d959e59220a46fba9a80aa4426; new test SHA256
6f381f29976a71c2aa9b60f0a9736a9d937c83de0cb18f30851da2782b2814a0.
Build-only13838 passes test-compile at20:44:54 UTC,22.906s,zero tests.
Short2258 completes20:46:52 UTC,5/0/0/0,28.313s Maven/5.876s class/0s queue.
Finite41801 completes20:54:42 UTC,420/0/0/0,5:06 Maven/280.8s class/69s queue.
These are425 distinct physical-owner products across two invocations, not one
single425 run; no unchanged logical products are rerun for this qualification.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off test-compile -B
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kBonusPortalMechanismProducts#shortPortalAndMechanismPilotsLoadAndReturnThroughRealBoundaries' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl '-Dtest=TestS3kBonusPortalMechanismProducts#finitePortalAndExitProductsRetainRosterAndBothLoadBoundaries' "-Dsonic1.rom.path=$S1_TASK_ROM" "-Dsonic2.rom.path=$S2_TASK_ROM" "-Ds3k.rom.path=$S3K_TASK_ROM" test -B
```

Real SOZ1 post1 activation and four-star orbit/lifetime precede actual portal
overlap; only declared position/carried-ring inlets are seeded. Gumball exit
range, Pachinko captured countdown and actual Slot goal-cell collision request
their own exits; no logical entry/exit test hooks are used. Both actual loads,
ordered roster/resources, all-key interior/returned replay and timeline floors
are checked. Complete cold stage traversal, reward mechanisms, native pixels/
audio and whole-Slot rewind are not credited. Its copied runtime board/reel/
reward/animation tables expose an additional concrete ROM-pipeline obligation:
matched original layout1024B at1E4084 (SHA256e5fbad9116c0febbfb792fad5ed0445291c1f49ebfd2bf04b81c187cb32dfe2d),
start4B at1E4080, prize word4C8A4, target4C8B4, reels4C8CC/4C8D4/4C8DC,
transient scripts4B5EC/4B622/4B656/4B688. Only addresses/hashes are inventoried;
no disassembly asset is used as runtime fallback. These remain active work.

The ending matrix's stale blanket pressure statement is corrected to existing
17 real-child cases (19591 plus two unchanged43234 pilots) and separate11-core7093,
with impossible fixed8/48 and Knuckles-only victims excluded by native owners.
Actual loc5DB76/5DD6A full-table dispatch and downstream scene composition are
separate remaining qualification (director-owned dispatch and its progress/Player_mode 3
gates are now covered headlessly; see the ending matrix's 2026-10-17 continuation section). Consumed root redirects are deleted after this
account; combined smoke/guards and integrated delivery remain pending.


## Native carry and Saved2 corrective composition (2026-10-14)

Qualified workeraedc137a173f48ecf71ae76253731b52ff7645cb is merged after
root585540848's actual portal/exit checks release. The merge has no conflicts;
source review confirms the updated develop stock-ROM script guard remains
ahead of HPZ initialization while the native fresh/checkpoint/selected-Saved2
eligibility is published before player assembly and survives bank consumption.
The engine-internal carry port retains every configured participant/order and
uses a capable native P2 or the existing intrinsic native-art Tails role for
native CNZ/MHZ story ownership. Public snapshot/Mod API shapes remain unchanged.

Worker2571 is terminal0 at20:51:39 UTC,399/0/0/0 (1:54 Maven): CNZ140 entry
and140 real two-reload/incoming-world products; authority3; capture-host28;
native story10, including incapable-first natural retirement/recreation and
reverse absence; real Saved2 representatives8; pedestal representatives8;
mandatory S3K neighbors60. Earlier72 matched/pure carry neighbors retain their
separate source scope. Source-owned negative and fixture-writer attribution
are recorded in the owning CNZ/HPZ matrices; no native ring/camera writer is
suppressed. Root build-only70500 passes compile on the union at21:09:01 UTC,
51.439s,zero tests. Actual finite hub/outcome/palette qualification continues;
composed smoke/guards and main integration remain pending.

A separately discovered private-record diagnostic access bug in
RewindSnapshotDiff.collectDiffs can conceal diffKey paths; keyEquals comparison
and direct native state/load/receipt/pixel assertions remain distinct evidence.
A bounded fail-closed helper repair is owned by the frontier lane and affected
world assertions must be checked with that helper before final delivery credit.
No valid queued/running request is cancelled because of this discovery.
Consumed root build redirect is removed after the durable account.

## Paired pattern transfer composition (2026-10-14)

After actual carry merge3e080ca11 releases, qualified workerdf8a26e936 is
composed. Three documentation append conflicts preserve both independent
lessons, including byte-identical object-skill mirrors. The profile retains
the updated develop stock-ROM guard and Saved2 assembly contract; its VInt
tail additionally publishes pending paired pattern DMA. Build-only29114
passes on the composed tree at21:19:22 UTC,1:09 Maven,zero tests. The worker
qualifies18 transfer/world checks and289 distinct affected consumers across
the exact scoped runs documented in the ending matrix. No raw queue behavior
or public snapshot signature is widened. Corrected helper replay, combined
smoke/guards, destination integration and push remain pending. Consumed build
redirect is removed after this account; durable native v7 media is external.

## Captured-content comparator repair (2026-10-14)

Qualified helperc3d2e75b41950961ec6300b38c30b02345a6143a is composed
after paired transfer7cc10d369. Private record inspection is explicit and
fail closed; top-level arrays and opaque state blobs compare their full nested
content. Five independent failing vectors precede candidate77331, which
passes14/0/0/0 at21:16:10 UTC,1:04 Maven,zero queue wait. No gameplay state
is removed, hydrated or filtered. One measurement-hazard append conflict
preserves both the unrelated editor projection lesson and the new comparator
lesson. Six additionally omitted captured gameplay/identity manager fields
are under a separate negative/regression repair; derived cache dirtiness and
allocator telemetry require their own justified exclusion. Historical
diffKey-only world equality remains provisional pending composed verification;
independent direct lifecycle/state/DMA/pixel assertions retain their scope.

## Slot asset ownership corrective qualification (2026-10-14)

Root0a05aa118 is the composed base for the immutable Slot ROM bank. Runtime
start/layout1E4080/1E4084, signed prize words4C8A4, target rows4C8B4,
reels4C8CC/4C8D4/4C8DC and cell scripts4B5EC/4B622/4B656/4B688 come from
the ROM pipeline. Byte identity and the native labels/routine pointers were
checked against the original locked-on ROM; no reference bytes hydrate gameplay.
Pure fixtures explicitly inject their separate synthetic bank. All consumed
literal asset arrays are removed from production, including the older optional
cell animator. Provider registration remains independent of unselected assets.

Actual build23604 passes at21:26:54 UTC,1:09,zero tests. Focused5254 reports
166/1/0/0 at21:27:47 UTC,28.461s,zero wait: the new immutable getters introduced
a real warmed-draw allocation regression, detected by the existing oracle.
The package read-only view repair yields27/0/0/0 in14603 at21:29:53 UTC,1:18.
Actual bonus portals/exit owners plus mandatory60 and cell/resource neighbors
pass75/0/0/0 in28066 at21:32:10 UTC,1:20. Complete first-zero termination
checks pass13/0/0/0 in71315 at21:35:24 UTC,1:14. All queued waits are zero;
all declared ROM tests execute, with zero skips. Exact selections are
`TestS3kSlot*` named mechanics/objects plus `TestMutatorBonusRingProducers`
for5254; allocation/resource/option/display for14603; resource/legacy animation,
`TestS3kBonusPortalMechanismProducts#shortPortalAndMechanismPilotsLoadAndReturnThroughRealBoundaries`
and the four mandatory S3K classes for28066; resource/render-buffer/legacy
animation for71315. Commands use the local normal wrapper, EGL, null audio,
unset ambient displays and absolute original-ROM properties; only bounded
14603/71315 use lean. Overlapping run totals retain separate source scopes.

Complete Slot rewind/recreation, other physical rewards, cold stage traversal,
native images/audio and composed smoke/guards are still active obligations.
Consumed target redirects are removed after inspection; no validation receipts
or copied raw logs are retained.

## Complete captured gameplay comparator composition (2026-10-14)

Qualified six-fieldd8da363885 and Palette7001b1eef0 are composed onto the
Slot-resource root0f95ee5bb without conflicts. Object-manager equality now
includes captured solid contact, plane switchers, touch overlap, collision
response, dynamic identity counter and the actual allocation/execution cursor.
Only source-proven cache dirtiness and peak allocator telemetry retain cosmetic
exclusions. Palette leaves compare all16 rawRGB triples and report the exact
changed component; no palette normalization or private-state filtering occurs.
Independent negative vectors and final72-case worker qualification precede
Root55826, which passes72/0/0/0 on this composed source at21:42:41 UTC,1:13
Maven. Its normal queue waiting is distinct from execution. This closes the
comparison-contract defects, not the gameplay replay mismatches that stricter
comparison can reveal. HPZ finite980 selector and140Saved2 worlds now pass in
the worker’s1510-case run, while283 ceremony/preowner published-scroll failures
remain active owner work. Native AIZ cutscene/transition qualification likewise
remains uncommitted while strict traces identify the next ROM lifecycle owner.
Combined Root smoke/guards and destination delivery remain pending. Consumed
Root comparator redirect is removed after inspection.


## Native SSZ cloud/HUD spot correction (2026-10-14)

On complete-comparator da27f4f3d, original current-poll SAT and ROM mappings
identify five missing local art-word high-priority bits and the dedicated
RINGS R glyph. Column-only attribution did not fix330 and is explicitly
rejected; the real native330 pair is roaming clouds at2D4+3C. Focused73
passes with no failures/errors/skips in10369 at22:12:06 UTC. Final passive
23561 matches diagnostic three-bit picture bins at330/680/1430;1342 retains
475 Tails-area differences, and original RGB expansion remains unaccepted.
The [SSZ matrix](../validation/levels/s3k-ssz-act1.md#native-cloud-priority-and-rings-glyph-2026-10-14)
records negative controls, exact source/media hashes, commands and limits.
Whole-world sky496→304 prepared-tile replay is independently reproduced on
unchanged da27f4f3d and remains an active publication owner repair. No broader
parity, combined validation or destination delivery is claimed.


## Native sanctuary and initial-scroll composition (2026-10-14)

Qualified route d726c82d4 is composed after cloud/HUD0b4b31967. One
measurement-hazard append conflict preserves both editor projection and
complete-comparator lessons. The S3K initialization profile keeps the develop
authored/stock guard, native Saved2 flag, pending paired DMA publication and
adds the admitted fresh stock-only initial deformation before Process_Sprites.
Native slot8 title setup and crystal setup-only/overshoot are retained. The
worker's1120 actual pedestal/Saved2 products pass with the complete comparator
in its1510-case run; all283 initially failing ceremony/preowner-scroll cases
plus99 affected setup neighbors pass382/0/0/0 in11111 at21:50:12 UTC,2:39.
These are separately qualified selections, not one current green1510 run.
Native d726 screenshots retain missing beams at850 and do not certify original
whole-scene pixels. Root affected verification and existing sky publication
repair follow this composition; combined smoke/guards and delivery are pending.


## Captured SSZ beam art qualification (2026-10-14)

The actual generic recreation probe supplied HPZ's constructor art selector
to an SSZ beam. Native slot/motion data restored, but192 prepared beam tiles
were absent. Capturing the structural selector and retaining its native local
priority removes the inherited sky replay failure. Literal negative6748 and
final22/0/0/0 affected97786 with all five viewport replay products are recorded
in the [SSZ matrix](../validation/levels/s3k-ssz-act1.md#beam-structural-art-and-complete-sky-replay-2026-10-14).
Two actual Slot portal negatives remain correctly red on missing complete
interior rewind; the next owner repair covers stage RAM, object rebinding and
lifecycle exit state. Whole-scene original RGB, Tails1342, composed broad checks
and destination integration/push remain pending.


## Complete Slot runtime candidate and native SSZ parent header (2026-10-14)

On committed Root3d633ffe5 plus the source draft, real Slot entry now owns a
complete stage/runtime snapshot: ordered position queues, in-place layout/cell
buffers, both visible-buffer indices, legacy and live cell animators, native
scroll accumulators/last output, movement/exit phases and stable reward/cage
identities. Restore rebuilds actors before rebinding their owners and reproduces
the completed scroll output without executing another native tick. The selected
Slot bank reads background deformation599BE and eight background rows599DE
from the actual ROM; unselected providers do not read those resources. The
generic live-replay path now runs the same title overlay and coordinator hooks
as ordinary gameplay. The real floor/held/seek negatives exposed that missing
overlay owner; corrected request23100 passes both independent Slot pilots.

Request58926 is terminal1 at23:25:57 UTC:319 tests, one failure, eight errors,
zero skips,4:11 Maven execution. Its separately reported finite Slot method
passes140/0/0/0 in225.1s, covering every admitted width/donor/main/ordered roster
with real placed-post entry, title/fade, live held-backward/forward/seek replay,
ring, bumper, spike, wall, cage/reel/payout actors, actual reward recreation,
goal wind-down/fade and real origin return. Every registry key remains compared
in both restore directions and two forward runs. Local free-movement inlets
are declared; these are not cold whole-stage traversal or native pixel/audio
acceptance. The other170 passing checks are a separate affected selection,
not permission to call the whole invocation green. Six errors were synthetic
coordinator fixtures lacking an explicit ROM bank; two were new null-context
regressions in the shared replay helper. Both source repairs are prepared,
not yet positively qualified.

The one assertion failure is the native SSZ moving cutscene-bridge header.
Original RAM at arrival inputs1341–1343 points P2's interacting-object word
toB73A, code44FA2, native negative status90/98. Obj_SSZCutsceneBridge sets
status bit7; Tails loc14A16 selects Wait5 and skips the object-edge Balance
branch for that header. The engine produced Balance6 at the first genuine
pre-dispatch stationary witness1338. The object-local no-balance flag is now
prepared; its positive run and new passive capture remain pending. This owner
is not the separate flat/diagonal bridge. Engine P2 observations at1342 read
anim6/map9A and tail anim0/map0, while selected original RAM reads anim5/mapAD
and tail anim1/map22. The earlier1432-row state comparison covered P2 X/Y,
not those additional P2 fields.

A source-only follow-up audit found that Slot copied the original Tails tail
controller, whose final parent still points to the retired level sprite.
Native loc4B9E8 creates Obj_Tails_Tail with the live Slot parent in$30. A new
actual coordinator/late-tail-dispatch/render-coordinate regression is prepared;
no new production tail correction or executed negative is credited yet. The
suspected dynamic-art capability omission was rejected: S3K deliberately uses
EVERY_CLAIM_WITHOUT_PLAYER_ART_AUDIT and supplies no such owner already.
No Slot-specific dynamic-art capability is added. Goal-fade DPLC bank ownership
is a separate unresolved native lead.

Farrell's23:30–00:30 UTC M1 measurement window defers new heavy validation.
Every existing Root/worker request completed normally before the window;
the last worker56020 ended23:29:18 UTC and its two leases were observed free
at23:30:28 UTC. Source-only fixture/header repairs retain the normal worktree
lease. No queued work was cancelled, restarted or preempted. Positive focused
checks, further finite products, composed smoke/guards and integration/push
remain pending.


### Slot presentation audit after the finite functional pass

The140-product pass renders gameplay, but did not assert active mapping
validity or retained body/tail bank bytes. S1 Sonic's configured renderer
has88 mappings; it rejects the host fade script's literal96–9A indices and
can draw no body. Native Slot init assigns Map_Sonic/Map_Tails/Map_Knuckles
and selects the host animator. Scoped host-resource borrowing must preserve
configured donation and restore its resources on return. A new short Knuckles
pilot supplements the two original Slot pilots; source-only active-pose and
raw body/tail-bank restore/replay assertions are prepared, not executed.

The fade bank owner is source/ROM-proven: loc4BC1E's003C write changes
character_id to0 while retaining mappings. Perform_Player_DPLC selects
Sonic_Load_PLC2. Verified PLC_Sonic96–9A entriesFBA2/FBB2/FBC2/FBD2/FBE2 write
16 tiles intoD000–D1FF. Knuckles shares that destination and Player_prev_frame
with Sonic, so unchanged mappings initially suppress the transfer; the next
changed mapping replaces its bank. Tails usesD400 and a separate previous-
frame register; Sonic writes leave its last body bank unchanged while the
tail continues atD600. The current renderer couples retained mappings to
the old character DPLC/source. Prepared checks cover admission, selected
Sonic bytes, retained Tails bytes and exact restoration without RGB conversion.
Production correction remains pending. Mapping replacement, immediate blank
banks, global donor disable and global player-art audit enablement are rejected.

Copied dust and Super controllers retain the retired original player too.
Their shared dispatch paths can run in Slot; normal entry leaves those effects
inactive. Native loc6B76 bypasses ordinary player/dust creation, so Slot must
not receive unsupported copied controllers. Original controller identities
and state must survive shutdown unchanged; no new Slot effects are proposed.


### Slot corrective execution chain before the M1 window

All times below are UTC2026-10-14, converted from Maven's+01:00 timestamps.
These are overlapping, source-specific development selections, not summed
green coverage or a broad-suite result. The owning wrapper selection is the
local normal queue with unset ambient displays, EGL, null audio and the three
absolute original ROM paths; only known bounded pure selections use lean.

| Scope | Terminal time | Tests/failures/errors/skips | Attribution |
|---|---|---|---|
| Initial Slot capture builds |22:32:39,22:33:48|No tests|Compile prerequisites repaired; no validation credit|
| First complete-state pilot|22:36:19|6/0/2/0|Ordered position queues require explicit capture rather than unsupported collection reflection|
| Ordered state +publication|22:41:44|9/2/0/0|Published sprite/scroll output differs after restore|
| Scroll ownership diagnostic|22:45:14|2/2/0/0|Restore must reproduce completed output without ticking scroll/band RAM|
| Scroll focused|22:50:21|19/0/8/0|Eight synthetic fixtures lack the selected ROM background bank|
| ROM background focused|22:58:26|32/1/0/0|Unit fixture accidentally supplied a longer live animation; its independent first-zero premise expected the wrong tick|
| Actual live controller +bank|23:00:59|7/2/0/0|Fresh-load controller is a placeholder; actual interior controller installs on the next ordinary host step|
| Installed-controller diagnostic|23:02:38|2/2/0/0|Confirms actual installation boundary|
| Installed live replay|23:03:35|2/2/0/0|Replay omitted the ordinary title-overlay update|
| Overlay helper compile|23:05:40|No tests|Use the world session's module; no pass credit|
| Shared overlay positive|23:07:30|2/0/0/0|Actual load floor, held backward/forward and seek replay qualify both original pilots|
| Native Slot contacts|23:11:43|2/2/0/0|Cage inlet used the rotating rendered marker instead of native fixed460/430; earlier ring/bumper/spike/reel contacts reached their owners|
| Payout +SSZ negative|23:15:07|3/1/0/0|Both complete Slot pilots pass; SSZ flat-bridge premise has no witness|
| Physical SSZ rider negative|23:17:21|1/1/0/0|First post-step zero speed still entered dispatch moving; Walk0 is not yet the standing premise|
| Finite Slot +affected selection|23:25:57|319/1/8/0|140 Slot products pass; genuine stationary SSZ Balance6 plus six ROM-fixture and two null-context errors remain red|

The latest new visual assertions inspect actual resident body/tail pattern
bytes through thirteen-tick wind/fade intervals and both restore directions;
that prime-length window avoids mistaking a completed short animation cycle
for bank restoration. They retain all registered-world keys and do not
normalize pixels, choose gameplay values from observations or alter input
rows. New ownership/pose/priority checks await execution after the window.
Consumed Root slot/SSZ Maven redirects are removed after this scoped account;
external capture provenance and original images remain at their documented
task paths. No raw Maven log is archived.

### User-requested colour expansion feature

The user requests a separate end-user Full/Limited choice, with Full as the
current default and Limited's maximum238. This supersedes the private native
comparison-profile proposal. Forward scaling final RGB by238/255 maps every
canonical ROM channel level0,36,73,109,146,182,219,255 exactly to0,34,68,102,
136,170,204,238 and handles arbitrary palette RGB without reconstructing a
CRAM word. The feature keeps display profiles and fades as independent
operations, persists display.colorExpansion and invalidates direct/derived
underwater palette uploads when the preference changes. Default Full preserves
current output. Feature source/UI/config/API-pin drafts are worker-owned and
unexecuted during the window; no private colour-word/rewind migration is
needed for this range setting. Exact physical CRAM and shadow/highlight
acceptance remain separate. Final feature tests and fresh Full/Limited SSZ
spot captures are required before delivery.

## Opus delivery takeover and native Slot banks (2026-10-15)

The user moved remaining implementation to Opus 5.5 at about00:30 UTC. Root Codex
became coordinator; an Opus delivery lead adopted this tree and assigned three
Opus lanes through T3 to the released frontier (`ai-s3k-parity-trace`: colour
expansion, AIZ end-boss CheckHit order, HCZ attribution), route
(`ai-s3k-parity-hpz`: finite700, Gumball/Pachinko parity and native spot pairs)
and ending (`ai-s3k-parity-allocation` plus the native TraceChaser tree: exact
KosM v2 costs, full-table dispatch, Tails reunion spots) trees.

The untested Root repairs passed first (00:47:46, 16/0/0/0). The resident-bank
owner then followed the native command path rather than a clamp: a dedicated
`S3kSlotPlayerArt` installs host mappings/scripts/banks and a fresh tail,
performs `Perform_Player_DPLC` after Animate with the native previous-frame bytes
and switches to Sonic cues at the goal `character_id` write; package-private
renderer hooks reached through the unannotated `PlayerResidentBanks` bridge keep
the `@ModApi` surface unchanged (the draft public `TailsTailsController.forNewParent`
was dropped for that reason; the fresh tail uses the existing constructor).
Results and the matched glass-test control are in the
[bonus matrix](../validation/levels/s3k-bonus-stage-transitions.md#native-slot-sprite-banks-and-complete-rewind--2026-10-15).

Rejected: enabling the global S3K player-art audit, routing direct DPLC through
the ordinary Kos queue, reusing the borrowed donor renderer (S1's 88 frames cannot
draw the fade's `$96`-`$9A`), and keying the bank on parent identity. Goal-frame
Animate phase remains the existing engine order (the goal override applies after
that frame's animation); Sonic mains are unaffected and no trace currently
distinguishes the Tails/Knuckles goal frame, so it is recorded rather than changed.

The stale Slot rewind entries in the general discrepancy file and S3K known-bug #27
are retired, and the eight finite-restart act matrices are now linked from the
coverage backlog.

### Composed route-movie regressions and capture parity (2026-10-15)

A sweep of all 43 route-movie test classes on composed Root `54c24da2f`
(1042 run / 84 failures / 1 error / 2 opt-in skips, finished 02:58:06 UTC)
exposed delivery-introduced regressions: the same pre-existing classes pass
85/0/0/0 at develop base `5b2ff8e78`. Matched controls attributed them:
- 17 (later 20) are the fade-owner class: `8179ccd80` correctly stopped
  direct-level captures servicing the fade twice, so each full-load fade-out
  now lasts 22 frames with gameplay and VInt frozen. Movies authored under the
  doubled fade were retimed by the new `FadeWindowRetimeTool` (verified by
  uninterrupted replay, old/new hashes in each owning matrix), not by restoring
  the harness defect. All retimed rows pass.
- 51 are registry restore/replay comparison mismatches (touch-overlap buffer
  parity, solid pushing-bit keys, a destroyed object left in the overlap
  buffer, presentation replay) owned by a rewind-determinism lane.
- The remainder are route behaviour changes owned by a behaviour lane, which
  bisects each signature and either fixes an engine regression or re-authors
  controller input after a ROM-cited correct change.
`b6fc90c77` (native object-loader parking) is not implicated by those controls.

`TraceCaptureTool` and `TraceBenchmarkTool` never applied the bonus-stage entry
bootstrap, so bonus trace captures were plain level loads; `7b30ce222` fixes
that, explaining the earlier Gumball HUD/priority and Pachinko 1316 capture
mismatches (Pachinko 1316 now differs by 681 px). Direct Limited/Raw-RGB
comparisons against native stills then showed real Slot/Gumball/Pachinko
presentation gaps (one-frame-early layout/background publication relative to
palette, a missing bonus HUD lives row, Gumball player priority in LEVEL-mode
captures and an early Slot goal fade), assigned to a presentation-parity lane.
SSZ arrival spots at 118/119/330/680/1341-1343/1430 match native exactly after
`f950a720c`; 0 and 120 are attributed declared-inlet limits.

## Exact Kos observer native publication and pin (2026-10-16)

TraceChaser observer `de43cf78c25478b5d23d69cf7bd541faa9923745` (read-only
schema-2 occupied-head Kos FIFO service observer) is integrated into TraceChaser
main as `33c05c1623c2e7c2ebb7664e111a4ed3c0505e9d`, a fast-forward from the
previous pin `4fdf73880f93a3172d23bf9e157b15de3c78fc32`; the merge tree is
byte-identical to the qualified observer commit. It is published on the GitHub
origin named by `.gitmodules`; the read-only Gitea mirror rejected the push and
is not a pin prerequisite. Source-only CI run 38949410410 on that exact SHA
passed (Python 3.12, 185 source-contract tests with no failures, errors or
skips; 5 Lua behaviour tests; repository, history, shell-syntax, v5 conformance
and documentation policy). Its optional native-integration job was skipped.
Native evidence keeps its recorded scope: ExactKosMeasurement 11/0/0,
HardwareTiming 70/0/0 and the actual v2 run's 30/30 agreement with an
independent head oracle. The parent gitlink now records that SHA. This is a
pin and source-contract qualification, not a full native gate or engine suite.

## Native CPU-ledger diagnostics pin (2026-10-17)

The parent gitlink moves to TraceChaser main
`4ae64e075d2e233539f5637fd16bd5ae7aa7d76e`, a fast-forward from
`33c05c1623c2e7c2ebb7664e111a4ed3c0505e9d`. It adds:

- the observation-only GPGX 68000 stall counters (stall ABI v1, published at
  `0c0cef7`) and the diagnostic final-PCM window runner;
- the callback-free 68000 CPU ledger (stall ABI v2, `393f0be`, hardened in
  `5afbc3d`);
- exact Kos service samples beside load-time v1 (`a081a02`);
- the reviewed r2 documentation (`0801da8`). It explicitly limits the claims:
  FIFO, rewind, lockup, STOPPED and H-int paths are not harness-tested; input
  identity is separate from external conservation; SMS is outside the
  contract.

Source-only CI run 38991106862 on that SHA succeeded. Its optional
native-integration job was skipped. The retained native proof is 12/12 within
the documented unsupported-shape limits. This is a diagnostic pin: it is not
full native timing or hardware closure, and supplies no 6+46 parity credit or
engine gameplay input.

### Blue Sphere player projection and shadow publication (2026-10-17)

Root compared the retained seven-Chaos native-v2 original PPM/VRAM/CRAM/RAM
with engine twin-v6. Stage reveal+0/+10/+22 have 446/667/1112 raw RGB differences;
281/433/782 lie in the lower player region. Independently rasterizing the
published native SAT finds 643 body/tail foreground samples: every sample
matches the actual engine art at y-2 for native138/148/160 versus engine24/34/46.
This is a diagnostic translation witness, not a normalized full-image pass.
The original images and their declared event fences remain unchanged.

`sub_953E` and `GetScalars2`/`sub_A206` explain the position error. The shipped
ScalarTable2 pitch `$E0` has sine -11585 and cosine 11585; its integer projection
places P1 at y162 and P2 at y179 on the ground. The renderer used y160/y172
plus a fitted linear jump offset. It now loads the scalar bytes through the
existing ROM data loader, preserves MULS/SWAP then signed DIVS truncation,
and includes P2's native depth -$20. No trace row or reference snapshot supplies
runtime state, and no clocks, movement or animation are changed.

The native `word_94FC` shadow is a fixed 4x4 operator sprite at (144,150), with
P2 at (144,167) only for Player_mode zero. The ROM art contains index15 shadow
operators, while the engine loaded it without drawing it. The regular
`loc_84C2` loop appends shadows after all ordinary SAT entries; the boot sequence
does not. The renderer uses the existing multiplicative shadow batch behind
stage sprites, gated by a captured `mainLoopSpritesPublished` phase. Restoring
the boot/last-wait snapshot removes future shadows; restoring the published
snapshot and two forward replays restores them. Shadows remain fixed while
players jump. No mapping-piece priority defect was proved in these idle spots.

Old-source projection request5293 on `ac1e1403c` plus its regression fails
7/7/0/0 at 06:50:12 UTC, reproducing the ground and nonlinear jump errors.
The projection-only change passes 37/0/0/0 at 06:52:20 UTC. Shadow request56500
then fails 4/4/0/0 at 06:53:37 UTC, after its negative boot/fade checks pass.
Combined request31083 passes 106/0/0/0 at 06:55:21 UTC, using the main absolute
queue wrapper from the ending worktree, `--normal -Dmse=off`, the original
locked-on ROM (`-Ds3k.rom.path=$S3K_TASK_ROM`, CRC32 `63522553`), surfaceless EGL,
unset DISPLAY/WAYLAND_DISPLAY and nullAL. Exact selector:
`TestS3kSpecialStageShadowPublication,TestS3kSpecialStagePlayerProjection,TestSonic3kSpecialStageRenderer,TestSonic3kSpecialStageWidescreen,TestSonic3kSpecialStageManagerSnapshot,TestSonic3kSpecialStageVisualSnapshot,TestSonic3kSpecialStageGameplaySnapshot,TestSonic3kSpecialStageRewindAdapter,TestS3kSpecialStageHeadlessBoot,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.

This is component command/phase/typed-manager rewind evidence at native320/800,
all four character/team modes, seven jump heights, donor off. It does not yet
qualify a new framebuffer, complete stage/cold route, configured donor/roster
breadth, PAL or PCM. The independent native whole-frame residual and later
sphere/HUD phase differences remain open until the composed capture is compared.

Fresh `--lean -Dmse=off -Dtest=TestArchitecturalSourceGuard test -B` then
passes 72/0/0/0 at 06:57:02 UTC (3.154s test execution). No API signature or
configuration default changed; the new projection/phase helpers are package-private.

### Saved-slot HPZ to SSZ roundtrips (2026-10-17)

Root recovered the stopped Opus lane at `0197c5a5a`, preserving its uncommitted
`HeadlessGameBoot` ROM/audio release and `TestS3kSaveRouteRoundtrip`. The
original four cases failed with four errors at 01:59:30 UTC: a second title
launch rebuilt its SMPS loader from the ROM already closed by the first
headless session (`AudioManager.setAudioProfile` → `Sonic3kSmpsLoader` →
`Rom.readBytes`, null FileChannel). Releasing the audio base-ROM reference at
its owning headless close/reboot boundary fixed this without changing title
or save routing. Existing teardown failure/retry ordering is retained.

The lane's unchanged production repair passed 89 checks at 02:12:52 UTC:
four route cases, headless close/audio identity, standalone/catalog boots,
physical data-select launch, cold save-site consumers and required S3K
loading checks. After the lane added Sonic+Tails, Root ran the current full
class through the current main absolute Maven wrapper from the intended
worktree: `--normal -Dmse=off -Dtest=TestS3kSaveRouteRoundtrip
-Ds3k.rom.path=$S3K_TASK_ROM test -B`, with the original locked-on ROM
(CRC32 `63522553`), surfaceless EGL, no DISPLAY/WAYLAND_DISPLAY and nullAL.
It passes 5/0/0/0 at 06:19:13 UTC, class execution 21.28s. Queue waiting is
separate from execution. The
[HPZ matrix](../validation/levels/s3k-hpz-act.md#saved-slot-hpz-exits-and-title-reload--2026-10-17)
records the three real exits/reloads, two no-exit negatives and two replay
cycles. Initial saved payload, native 320/donor-off scope and inherited
configuration/cold/native-presentation limits remain explicit.

The stronger actual follower check then passed separately at 06:47:35 UTC:
`-Dtest=TestS3kSaveRouteRoundtrip#sonicAndTailsAltarExitSaveRoundTripsThroughTheTitleToSkySanctuaryOne`
with the same normal-lane environment, 1/0/0/0 (12.14s class execution;
1329s queue wait). Fresh `--lean -Dmse=off -Dtest=TestArchitecturalSourceGuard
test -B` passed 72/0/0/0 at 06:47:59 UTC. The first stronger assertion had
incorrectly compared the role name `tails_p2` with the saved character key
`tails`; that test-oracle error was corrected to
`sidekick.characterKey().persisted()`, without changing production behavior.
The two solo cases' empty identity lists are equivalent to their previous
zero-roster checks. No additional cold, wide, donor or native audiovisual
coverage is claimed.

#### Composed presentation qualification and native RGB — 2026-10-17

The free ending tree composes the retained Root `3aad23d38`, ending order
`ac1e1403c`, projection/shadows `4cce16113`, develop preflight `707b3c208`
and saved-slot `81a2e707f` as `a259e695e`. Source merges were clean; append-only
research/matrices/pitfalls and release prose retained both sides' intent.
The original Root category run remains frozen on `3aad23d38` while it completes.

Two minimal test-service repairs address the observed broad errors: the neutral
GameModule fixture supplies its required SONIC_2 rules (matching its test setup),
and the admission-only mutator fixture explicitly supplies RuntimeArtCoordinator.NONE
for its final accepted STOCK branch. The latter failure followed successful
denial checks; it was not a denied mutation. No production null fallback is added.
With these repairs the composed exact focused selection passes all 101 GameLoop,
10 mutator-admission, five ending-state, two ending-GPU and five saved-slot cases,
plus projection, shadows, snapshots and required S3K consumers. Request17744
finishes 07:02:50 UTC with 219 tests, 1 failure, 0 errors and 1 skip. The sole
failure is `TestTitleInputOwnership.captureChordIsConsumedWhileBlockedAndCannotFireAfterReturningWithKeyHeld`,
expected true/actual false at line105. Fresh matched baseline request24091 on
unchanged develop `707b3c208` has the same failure (8/1/0/0, 07:01:26 UTC).
`TestForegroundWindowRendering` still aborts its GLFW-null EGL setup even with
EGL_PLATFORM=surfaceless; its framebuffer remains unqualified. This is not an
all-green or full-suite result.

Maintained `S3kSevenChaosGatewayCaptureTool` request31444 on that composed source
completes exit0 at 07:04:46 UTC via the normal main queue wrapper,
`-Dmse=off compile exec:java -Dexec.mainClass=com.openggf.tools.S3kSevenChaosGatewayCaptureTool`,
with `--rom $S3K_TASK_ROM --out-dir NEW_ABSOLUTE_DIRECTORY`, Limited/RAW_RGB,
surfaceless EGL, unset DISPLAY/WAYLAND_DISPLAY and nullAL. Its 174 files
(172 actual PNGs and two CSVs) are retained in the existing external task's
`spot-checks/seven-chaos-engine-player-v8`. State.csv and ledger.csv are
byte-identical to v6; events still end in LRZ2 return2423.

Compared directly with unchanged original native-v2 PPMs, using the declared
native active crop `(14,8)..(334,232)` only, stage reveal+0..22 by2 has raw RGB
difference counts `165,165,187,214,214,234,299,299,314,330,330,330`, down from
`446,446,578,643,643,667,732,732,747,763,763,1112`. Every pixel at y128..223
matches at the inspected +0/+10/+22 spots; all +22 residuals lie in bbox
(9,21)..(276,57), above the player and shadow. The actual +22 pair was viewed.
Results reveal+0..32 by2 still has zero differing pixels at all17 spots. There
is no colour normalization, alignment shift or difference mask in these full
image counts. The comparison pair lives in `seven-chaos-compare-player-v8`.

Maven reports the existing Discord presence worker lingering for15s at CLI
shutdown; its owned Maven/JVM invocation is terminal. This is diagnostic CLI
and bounded picture evidence, not a suite pass. Remaining upper HUD/distant
object phase, complete stage/results timeline, cold/donor/PAL/PCM and other
native configurations remain open.


### Blue Sphere distant mapping frames — 2026-10-17

The composed v8 capture's remaining330 stage pixels were all owned by distant
native SAT entries23..31, rather than the HUD. Shipped `Map_SStageSphere_`
(`$A464`, 166 bytes) has19 word pointers: frames10/11 share the2x2 `$6C`
piece,12/13 share2x2 `$70`,14/15 share1x1 `$74`; later16..18 are outside
Draw_SSSprites' admitted0..15 perspective range. The former formula incorrectly
advanced tiles at11 and shrank12/13. The manager now loads the mapping bytes
through the ROM pipeline; sphere/red/bumper/spring/touched branches use the
existing bounded mapping-piece reader. Ring/emerald selection, gameplay,
clocks and snapshot state are unchanged.

Independent `TestS3kSpecialStageSphereMappings` reads each actual ROM pointer
and piece as its oracle. The unchanged old mapper fails5 of16 cases (11..15),
with11 passing, no errors/skips (20891, 07:10:30 UTC). With the correction,
38930 completes138/0/0/0 at07:13:36 UTC:16 admitted mapping frames, each five
cell/palette branches at320/800, plus layout bank, projection, shadow publication,
rendering/wide, typed manager/visual/gameplay snapshots, rewind, headless boot
and required S3K loading consumers. Command uses the current main absolute
queue wrapper from `t3-2f458d35` at `4eb88883c` plus the mapping correction,
`--normal -Dmse=off`, original absolute `-Ds3k.rom.path`, Java25, surfaceless EGL,
unset DISPLAY/WAYLAND_DISPLAY and nullAL. Exact selector adds
`TestS3kSpecialStageSphereMappings,TestS3kSpecialStageLayoutBank` to the previous
106-case presentation selection. This is not a full suite or full-route claim.

The maintained capture CLI then completes36070 exit0 at07:16:41 UTC on that
same source, using the preceding documented command and new external output
`spot-checks/seven-chaos-engine-sphere-v9`. All174 files are retained. Against
unchanged native-v2 original RGB and its declared active crop only, stage
reveal+0..22 by2 now differs by `19,19,43,43,43,43,46,46,46,46,46,46` pixels.
The17 results samples still match exactly. State.csv and ledger.csv remain
byte-identical to v6, including LRZ2 return2423. The actual +22 paired image
was inspected in `seven-chaos-compare-sphere-v9`; no colour normalization,
translation, rescaling or masking enters the counts.

The remaining46 pixels occupy bbox(91,22)..(260,43). Native published SAT29
at(155,22) and30 at(189,24) overlap earlier high-priority pieces25/26;28 and31
also carry low priority. `Draw_SSSprites/loc_9A04` clears priority when the
perspective word is at least `$54` (the second axis has the same branch).
The renderer still paints those cells with unconditional high priority.
This is a separate composition gap, not a reason to alter mapping pointers or
ignore those pixels. Exact full-stage RGB, the complete results timeline,
cold/donor/other native configurations, PAL and PCM remain open.

Fresh mapping-source guards27554 complete83/0/0/0 at07:20:41 UTC: architectural
source guard72, trace replay invariant10 and default hydration switch1, via
`--lean -Dmse=off -Dtest=TestArchitecturalSourceGuard,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault test -B`.
The two latter names resolve their existing trace packages; all three ran with
no skips. Diff whitespace, affected relative links and mirrored pitfalls pass.


### Immediate-scene fade publication after rewind — 2026-10-17

The retained Root3aad smoke's gateway and sanctuary matrix cases exposed a
new regression in the local white-palette presentation change. The restored
level publication could hold a displayed fade even after the scene had
released its level palette-upload latch. `PaletteUploadPresentation.release`
only flushed when that latch was set, so subsequent SPECIAL_STAGE publications
never released the independent rewind hold. Gameplay/world comparison detected
`displayedFade` NONE/0/mask0/pendingfalse versus FROM_WHITE/7/mask-1/pendingtrue.
This was production behavior; the comparator and world assertions are retained.

Composed-source reproduction27986 is4/2F/0E/0S at07:21:49 UTC: both seven-Chaos
gateway cases fail that exact four-field comparison; both seven-Super payout
controls pass. New headless `TestPaletteUploadLatch` reproduction1577 is
5/1F/0E/0S at07:22:43 UTC: an unlatched scene restores displayed step1, requests
step3, then release incorrectly leaves step1/pendingtrue. Its four existing
latch checks pass. The correction always invokes the normal palette release
flush when graphics exists, including this unlatched rewind state. The restored
picture still holds its captured generation until the next scene publication;
subsequent direct writes are immediate. No new timing, snapshot field or API.

Focused83390 passes112/0/0/0 at07:27:53 UTC in11 resolved classes: palette latch5,
gateway4, native white GPU14, underwater publication16, fade snapshots7, level
presentation lifecycle6 and required S3K loading60. The exact selector is
`TestPaletteUploadLatch,TestS3kLrz2SevenChaosGiantRingGateway,TestNativeWhitePaletteFade,TestUnderwaterPaletteUpload,TestFadeManagerRewindSnapshot,TestLevelSpritePresentationLifecycle,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`.

The broad run's affected matrix batch had1853 failures; its retained failed-case
preview was capped, so sample similarity alone was not treated as complete
attribution. Root then ran both complete exact affected classes on the correction:
`TestS3kSanctuaryOriginReturnLifecycle,TestS3kSanctuaryPedestalLifecycle`.
57173 completes2006/0/0/0 at07:35:05 UTC:580 origin/return cases (124.688s) and
1426 pedestal cases (170.765s). Maven execution5:16 is separate from queue wait.
Both commands use the current main absolute queue wrapper from the intended
composed worktree at68cb5d183 plus this two-file correction, `--normal -Dmse=off`,
absolute original S3K and (for the complete matrices) S1/S2 ROM properties,
Java25, surfaceless EGL, unset DISPLAY/WAYLAND_DISPLAY and nullAL. No lean
reservation was used for these ROM-heavy matrix products.

The retained broad run stays frozen until terminal; no duplicate broad/full
suite is submitted. The corrected behavior is bounded to releasing an already
restored presentation hold through the existing scene-publication owner.
Affected mechanism, GPU, level consumers and complete failing matrices qualify
this delta; previously covered unaffected ordinary batches are not repeated.
Whole-stage RGB, cold/configuration products not in those classes, PAL/PCM and
strict trace/timing closure remain separate obligations.


Fresh source guards43534 pass83/0/0/0 at07:37:24 UTC, same exact architectural,
trace-invariant and default-hydration selector recorded above. To close the
capped failure-detail gap,64631 then checks the other48 exact classes from
original Root3aad ordinary batch61 (plan.tests[3000:3050], excluding the two
already-qualified matrices), using their full names in `com.openggf.tests`:

```text
TestS3kMhzMinibossViewport,TestS3kMhzTailsAuthoredRoute,TestS3kMhzTeamAuthoredRoute,TestS3kMhzWideAuthoredRoute
TestS3kMonkeyDudePlacedGraph,TestS3kPaletteFadeTimerGatesPaletteCycles,TestS3kPenguinatorBadnik,TestS3kReverseGravityBoundary
TestS3kReverseGravityCameraLook,TestS3kReverseGravityDezCorridor,TestS3kReverseGravityFlagLifecycle,TestS3kReverseGravityIntegration
TestS3kReverseGravitySolidObject,TestS3kReverseGravitySpringLaunch,TestS3kReverseGravityTerrain,TestS3kScreenEventCameraCopies
TestS3kScriptedIntroCarryAdmission,TestS3kSlotPresentationParity,TestS3kSnaleBlasterBadnik,TestS3kSpecialStageHeadlessBoot
TestS3kSpecialStageReturnWaterRestore,TestS3kSpringObjectInstance,TestS3kSszAct2ColdFight,TestS3kSszAct2FinalFight
TestS3kSszArrivalContinuation,TestS3kSszArrivalHeadless,TestS3kSszBackgroundClouds,TestS3kSszBackgroundLayout
TestS3kSszCarriersAndSprings,TestS3kSszColdRoutes,TestS3kSszCompatibilityMatrix,TestS3kSszCraneRouteHeadless
TestS3kSszEggRobo,TestS3kSszGhzArenaHeadless,TestS3kSszKnucklesArrivalHeadless,TestS3kSszKnucklesBridgeHeadless
TestS3kSszLifecycleProduction,TestS3kSszMechaSpawnHeadless,TestS3kSszMtzArenaHeadless,TestS3kSszPlacementCensus
TestS3kSszTeleporterPads,TestS3kSszTraversalPlatforms,TestS3kTensionBridgeObject,TestS3kZoneRuntimeRegistrationHeadless
TestSaxmanDecompressor,TestSbz1CreditsDemoBug,TestScriptedVelocityAnimationProfile,TestSczObjectArtPalette
```

The command retains `--normal -Dmse=off`, absolute original S3K/S1/S2 ROM
properties and the same headless environment. It completes440/0/0/0 at07:40:23
UTC (Maven1:10). Together with the2006 matrix cases, this qualifies all2446
cases from that original50-class batch in two bounded parts, with no repeated
matrix run and no hidden omitted-failure assumption.

Maintained capture27778 then completes exit0 at07:44:37 UTC (Maven19.947s;
queue wait separate). Its new external `seven-chaos-engine-fade-release-v10`
has the same174 filenames and every file is byte-identical to v9, including
172 actual PNGs, state.csv and ledger.csv. The actual stage+046 picture was
viewed. Consequently the original-RGB comparisons retain the same19..46 stage
residuals and17 exact results samples. This is a fresh production-path capture,
not framebuffer input to gameplay or a full PAL/PCM/cold/configuration claim.

A read-only audit also rejects a tempting priority shortcut for the separate
46-pixel gap: original native stage+160 RAM has23 valid perspective pointers
(0..22). Frames2/10 have7 admitted high-priority cells after the first low-priority
cell; turn frames16..22 have11/16/30/32/40/40/39. Painting all low sprites before
all high sprites therefore cannot preserve first-SAT-pixel ownership generally.
The eventual correction needs exact sprite ownership and plane composition,
not a fitted layer split that only matches this still. No such renderer change
or broader priority acceptance is included in the fade release repair.


### Wide-route coarse camera discontinuity — 2026-10-17

Root's original3490-candidate smoke stopped at its90-minute execution deadline
midbatch65/70:64 completed batches plus partial65,35269 tests/1860 failures/
32 errors/65 skips. This is incomplete; guards never ran. Its inspected runner
diagnostics were acknowledged and deleted. The remaining original290 candidates
are preserved as six unchanged pending batches65..70, including all of partial65.
The runner has no resume option; continuation uses queued lifecycle `test` with
original smoke includes and all four original absolute ROM properties, then
fresh guards. Partial65 counts must not be double-counted. This is bounded
smoke coverage, not the release-only full suite.

Three failures were new against current develop707b3c208: wide LRZ2 door8 stayed
closed, the wide LRZ boss remained in zone9 instead of22, and wide MHZ died at
input13946. Exact matched Main baselines24200 and91400 pass2+1 cases at07:45:42
and07:50:10 UTC. Composed2056 reproduction16428 fails all three. Reverting only
the camera-ease delta32472 preserves all three failures; that ROM-backed change
is exonerated. Removing only the new backward borrow guard46130 passes3/0/0/0
at07:59:31 UTC. Neither diagnostic reversion is the production repair.

Temporary observation4379, removed before qualification, establishes the cause:
LRZ visible camera-3 ->0 has masked coarse65408 ->0, both cursors455 and active0;
MHZ-16 ->8 has the same coarse change, both cursors554 and active0. Negative
visible X is possible with the wide camera. Masking changes its representation,
so the old comparison mistakenly selects backward scrolling. The valid native
borrow guard then leaves the exhausted cursor parked. Native `loc_1B864` really
does skip the back cursor on `$80` subtraction borrow, while right trimming
still runs. Removing that branch or fitting a width-specific bypass would undo
the native Knuckles credits-pad repair.

The repair adds raw/coarse direction disagreement to the existing teleport
window-reconstruction condition. It changes neither signed alignment globally
nor native-camera framing policy. A real backward `$1CF ->0` has agreeing
negative directions and retains its borrow skip. Comparing coarse distance to
the viewport span was rejected: it would also change ordinary quantized jumps
near the existing catch-up threshold.

New focused test7630 fails on2056 at08:09:47 UTC, expecting two entry objects
but finding none after-3 ->0. The strengthened regression runs0 ->negative ->
nonnegative, asserts cursor exhaustion first, then exact ordered active/pending
ownership, strict native640/wide928 edges, distant-entry exclusion and two
snapshot restore/replays. It exercises320/800 and LEGACY/S2 strategies; the S2
product is a controller boundary check, not a production S2 route certificate.
Existing independent native first-chunk borrow coverage is retained.

Focused85219 completes31/0/0/0 at08:16:00 UTC:
`--lean -Dmse=off -Dtest=TestObjectPlacementManager,TestObjectPlacementControllerS1Counter,TestObjectManagerVerticalPlacement test -B`.
Full route qualification89369 completes63/0/0/0 at08:22:34 UTC:
`--normal -Dmse=off -Dtest=TestLrzWideActTwoColdRouteCapture,TestLrzWideBossColdRouteCapture,TestMhzWideColdRouteCapture,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils -Ds3k.rom.path="${OPENGGF_ROM_DIRECTORY}/s3k.gen" test -B`.
LRZ2 retains104 two-cycle rewind windows, LRZ boss71, and MHZ25 named whole-world
replay spots. Both commands use the updated main absolute queue wrapper from
the isolated ending worktree `t3-2f458d35` at2056 plus this repair,
Java25, Lua5.4, unset DISPLAY/WAYLAND_DISPLAY, `EGL_PLATFORM=surfaceless`, nullAL
and the retained exact EGL JVM flag. All resolved test classes execute with no
skips. Source-only peer review found no duplicate-allocation or latch-loss path
through the existing reconstruction; execution evidence remains the tests above.

The updated local707 tool preflight also passes on Root2056 after normal shared
activity waiting, with no outer temporary activity shim; that consumed shim was
removed. Native credits lifecycle coverage is included in the pending original
tail selection, not redundantly submitted as another full route request. Strict
AIZ/HCZ frontiers, native sprite/plane pixel ownership, complete negative-origin
window semantics, PAL/PCM and other unqualified matrix products remain separate.

Fresh source/invariant/hydration guard84418 completes83/0/0/0 at08:28:00 UTC
(Maven30.936s), with the exact three-class selector recorded above. Matrix
relative links and their target heading, whitespace, and mirrored camera-pitfall
bytes pass. No architecture budget, comparator, API pin or gameplay fixture was
changed. The original six-batch tail and final guards remain required before
integration; this local commit is not a delivery claim.


## Retained HCZ repair worker checkpoint — 2026-10-17

Worker `t3-3aa3bc3c`, local `feature/ai-s3k-hcz-gpt61-repair`: `b6c4898e2`
composes final pass4 and retained carrier/water WIP onto Root `2056a99cd`;
`b9c1c8b3d` merges qualified prerequisite `534a69e74`. Stored targets, X-lock
release ownership and actual manager/player/camera restore/replay are complete;
Root-only rules, mapping access and title helpers remain. Candidate API gets the
necessary semantic drowning flag while retaining its prior constructor and 0.7.0
unpublished descriptor. Focused1591/0/0/4 (four external authored-AIZ JAR skips),
named guards113/0/0/0; extra boss/transition37/1/0/0 fails only GLFW init.

All three single qualifiers are assertion-red: returned HCZ's first reported
non-camera physics is7134 `sidekick_x` over a longer window through14207, with
no owned exit; segment8 load7126 stays separate. HCZ slice4353 first9482 `air`
is not matched-baseline attributed; alternate has one26179 `rings` span.
[Exact evidence/limits](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#final-single-invocation-strict-qualification),
[HCZ1](../validation/levels/s3k-hcz1-sonic.md#retained-hcz-handlers-and-incoming-act-2-carrier--2026-10-17)
and [HCZ2](../validation/levels/s3k-hcz2.md#retained-hcz1-to-hcz2-carrier-boundary--2026-10-17)
retain the open obligations. No zero frontier, broad smoke/full-suite, Main write,
push or retained-tree cleanup; Root owns follow-up and delivery validation.


## Bounded HCZ native load ownership followup — 2026-10-17

Same worker after866e68501 establishes an independent short old-red4/3F, then moves Nemesis submission to Level's load frame and closes GameLoop's first title-loop preparation tail. Obj_TitleCardInit retains the four archive submissions; no public API, new snapshot fields, readiness authority, fitted clock or budget changes. New source digest8b1e770e6904e76fec9822267e2fc75ad994a8c1068c86d3bdeb7901c5920232: focused134 and named guards131 pass without skips; actual native captures corroborate queued/unprepared ->56 ->50 and destination publication. The canonical prefix through the first segment9 row passes and fully closes segment8's four7126 fields; the distinct alternate remains strict-red solely26179 `rings`72/0. Returned HCZ11/full canonical/full slice are not requalified on this source.

Matched pre-fix dc338 versus866 slice coverage is now recorded: same window,4699 versus4353 spans,1132 exact common, mixed removals/additions; newly mismatching field/row10834 CPU respawn counter. Historical3738 is not a matched baseline. Other matrix/auxiliary/cold products, missing returned-HCZ exit and exact GLFW oracle remain open. [Evidence and stopping boundary](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#bounded-followup-qualification-and-limits). Root owns combination with its later source; no Main edit/push, broad run or child. Existing task ownership expiry refreshed24h, no new taskId returned, all worker requests terminal. Preserve local branch and the existing TraceChaser checkout mismatch for Root review.

### Blue Sphere SAT ownership and Plane A composition — 2026-10-17

Implementation checkout `feature/ai-s3k-sprite-priority` at
`${TASK_WORKTREE}` starts from Main
`707b3c2082433c4833da93fbf855a0a748dd58b1` and merges the qualified composed
prerequisite `2056a99cd4011dff1b725f401a905f8baa7ff779` as `9311b333e` under
its own build-slot lease. This is local qualification, not Main publication.

Native `Draw_SSSprites` (`loc_99CC` / `loc_9A84`) emits direct grid traversal;
`Draw_SSSprite_Normal` emits mapping pieces forward and adds the adjusted
base art word with 16-bit wrap. The unsigned ORIGINAL perspective word at
`$54` or above clears the base priority bit (`loc_9A04` / `loc_9ABC`).
Published native stage+160 entries 25/26 (high) precede 29/30 (low) over the
same horizon pixels. Neither Y sorting, an all-low/all-high split, nor reverse
painting with per-sprite plane clipping reproduces that ownership. The prior
call-order test admitted a wrong pixel winner and remains a geometry check;
actual framebuffer regressions now exercise the ownership consequence.

A package-private special-stage layer samples the immutable indexed bytes of
its own ROM-backed uploaded patterns, claims the first opaque sprite pixel,
and only then masks a winning low pixel behind an opaque high Plane A pixel.
Masked fragments stay in the existing private special-stage atlas range;
unchanged fragments reuse their original pattern, changed masks reuse slots,
and uploads are batched. Loaded art is checked against the reserved mask
range. The helper tracks atlas generation and rebuilds masks on replacement.
No public API or normalized signature pin changes, shared SAT replay changes,
new framebuffer, stencil, comparison-data input, or fixture-dependent gate is
introduced. Rendering preserves an already-bound capture framebuffer.

Resource inspection: the helper owns two fixed 320×224 boolean maps, one
64-index scratch pattern and one descriptor. Its mask list grows only to its
high-water mark inside the fixed 8,192-ID reservation; unchanged masks retain
the same Pattern and atlas entry, and changed masks overwrite the same IDs.
There is no per-frame range registration or helper-owned GL texture/FBO.
Stage reset uncaches owned slots; the shared atlas may retain a bounded CPU
sample for those IDs until its normal cleanup. Atlas disposal deletes its
page textures; a replacement atlas invalidates retained mask contents. Two
small command-scope objects per GPU render preserve delayed shader state;
this is bounded allocation, not a claim of an allocation-free whole renderer.
Changed mask uploads use the atlas's existing immutable CPU pattern-version
records. The pre-existing player DPLC temporary arrays remain unchanged.
No separate performance benchmark was requested or run.

Plane A number cells join the plane mask; icons/banner, appendages, sidekick
body and main body retain their native priority buckets before grid traversal.
Shadows follow the grid in SAT ownership and cannot darken an earlier sprite
or shine through an earlier low sprite hidden by a high plane. The local
shader scope restores both submission and delayed-execution flags.

All commands ran from this checkout at `9311b333e` plus this commit's local
implementation/test changes, with Java 25.0.4.1. Main's absolute queue wrapper
was used throughout; no foreign target tree, ambient display, nested queue,
policy override, or native agent child was used. Root owns delivery-wide
validation; this lane ran focused checks only, never a broad smoke or full suite.

Focused renderer/ROM/GPU selection (finished 08:15:51 UTC): **51 tests, 0
failures, 0 errors, 0 skips**. Maven execution was 1:10; queue wait was 281 s.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null \
  python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py \
  --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Ds3k.rom.path=${OPENGGF_ROM_DIRECTORY}/s3k.gen \
  -Dtest=TestS3kSpecialStageSpritePixels,TestSonic3kSpecialStageRenderer,TestSonic3kSpecialStageWidescreen,TestSonic3kSpecialStageVisualSnapshot,TestS3kSpecialStageSphereMappings,TestS3kSpecialStageShadowPublication,TestS3kSpecialStagePlayerProjection test -B
```

After adding stage-reset resource retirement, the affected GPU/static-geometry
checks plus source guards finished 08:22:11 UTC: **100 tests, 0 failures, 0
errors, 0 skips** (Maven 1:15; queue wait 138 s). The 5 GPU cases include
600 renders per context, mask/scratch/entry reuse, fixed atlas-page count,
stage reset/re-entry, real context replacement and page-texture deletion.
The selection repeats only checks affected by the new lifecycle edit:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null \
  python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py \
  --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Ds3k.rom.path=${OPENGGF_ROM_DIRECTORY}/s3k.gen \
  -Dtest=TestS3kSpecialStageSpritePixels,TestSonic3kSpecialStageVisualSnapshot,TestArchitecturalSourceGuard,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault test -B
```

GPU assertions cover front-low/rear-high/high-plane → plane,
front-high/rear-low → front, either priority pair without a high plane →
earlier SAT, transparent flipped front → rear, equal and inverted screen Y,
earlier mapping piece, original perspective `$53` / `$54` / `$8050`,
16-bit art-word addition, number planes versus icon sprites, ordinary player
before grid, late shadow operators and an already-bound offscreen framebuffer.
Both unbatched and batched sprite submission and native 320 / outer 800 widths
are covered. A final test refinement alternates actual GL viewport/projection
width (320 / 800), asserts retained mask/entry/texture identity and verifies
the unbatched delayed-shader scope. The same 5 GPU cases pass at 08:27:57 UTC,
0 failures/errors/skips (Maven 27.826 s; queue wait 206 s):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null \
  python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py \
  --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dtest=TestS3kSpecialStageSpritePixels test -B
```

An independent GPT-6.1 source review found no blocking defect and requested a
distinct-colour 2×2 flipped overlap witness. After the preceding request was
terminal, that single new regression passes at 08:29:13 UTC, 1 test and
0 failures/errors/skips (Maven 26.661 s; queue wait 0 s). It checks every pixel
of the earlier H+V 2×2 piece, its four distinct source tiles/corner colours,
and the partially masked rear piece's exposed strip, with both batching modes
and widths. No runtime source changes followed the qualified capture.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null \
  python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py \
  --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS3kSpecialStageSpritePixels#multiTileFlipsRetainColumnMajorArtAndEarlierSatOwnership' test -B
```

Fresh maintained capture finished 08:22:33 UTC, exit 0 (Maven 21.337 s; queue
wait 65 s):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null \
  python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py \
  --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl compile exec:java \
  -Dexec.mainClass=com.openggf.tools.S3kSevenChaosGatewayCaptureTool \
  "-Dexec.args=--rom ${OPENGGF_ROM_DIRECTORY}/s3k.gen --out-dir ${CAPTURE_ROOT}/s3k-sprite-priority-20261017-t3-3be884f6/engine-v11" -B
```

The original ROM SHA-1 is `CFBF98C36C776677290A872547AC47C53D2761D6`.
The capture contains the same 174 filenames as v10 (172 PNGs and two CSVs);
70 PNGs change. `state.csv` (196,072 bytes, SHA-256
`ad2498e3b7ee7ed788da9dc7feb715b68b4f350eea24ce4cd37bbe15c7af7c3e`)
and `ledger.csv` (217 bytes, SHA-256
`d03075823b756908df74a3c2604eb04af9b7b368680ccd4b2f13f207c4dfc514`)
are byte-identical to `seven-chaos-engine-fade-release-v10`. Events remain
contact 338, HPZ first 403, release 1295, pedestal 1425, special stage 1441,
results 1789 and LRZ2 return 2423. Maven emits a `discord-presence-worker`
shutdown warning, then exits 0; it does not leave this capture JVM running.

Original native input remains
`${CAPTURE_ROOT}/s3k-parity-coverage-2026-10-14/spot-checks/seven-chaos-native-v2`.
No native asset or observation is fed into engine rendering/gameplay.
The unchanged comparator command is:

```bash
python3 ${TASK_SCRATCH}/s3k-parity-coverage-20261014/seven-chaos-native/compare/pair_reveal_v2.py \
  ${CAPTURE_ROOT}/s3k-sprite-priority-20261017-t3-3be884f6/engine-v11 \
  ${CAPTURE_ROOT}/s3k-parity-coverage-2026-10-14/spot-checks/seven-chaos-native-v2 \
  ${CAPTURE_ROOT}/s3k-sprite-priority-20261017-t3-3be884f6/compare-v11
```

Native PPMs are 348×240 with the declared active crop `(14,8)..(334,232)`;
engine PNGs are 320×224. Raw RGB is compared without normalization, shifting,
rescaling or masking, paired by first nonwhite published reveal after the
uniform-white minimum. Stage reveal is engine +24 / native +138; results
is engine +52 / native +128. **All 12 stage comparisons (+0..22 by 2) have
0 differing pixels**, replacing `19,19,43,43,43,43,46,46,46,46,46,46`.
**All 17 results comparisons (+0..32 by 2) remain 0**. The actual paired
stage +22 (`engine046/native160`) and results +32 (`engine084/native160`)
images were inspected, not inferred from successful file creation.

This closes the sampled 46-pixel sprite-priority mismatch. Grid collection
still admits every mapped piece: inherited SAT capacity and per-scanline
admission are not modeled here, so dense or turning scenes are not certified
as complete VDP parity. The existing visible plane painter also does not fully
compose transparent HUD Plane A pixels with Plane B; this fix changes their
priority mask without expanding into that inherited plane-composition work.
Full-stage RGB, complete timelines, other native character
configurations, cold/donor/PAL/PCM products and strict timing/trace closure
remain outside this bounded result. Resource checks establish reuse and
cleanup, not a performance or frame-time claim. The task branch stays local;
Root owns integration/publication.

## 2026-10-17 maintained positioned ending sampler

The recurring ending observation protocol now lives in
[`S3kEndingSamplerTool`](../../../src/main/java/com/openggf/tools/S3kEndingSamplerTool.java)
and the fail-closed [`compare_native_rgb.py`](../../../tools/bizhawk/compare_native_rgb.py).
This bounded slice promotes external `EndingEnginePalProbeV2` (SHA-256
`da6bff78c4c1ebd47f0305e3602990962644bf430284a1c5f7cfb8177f82b056`)
from the retained ending-pal/native v2 experiment. It changes no ending runtime
owner. The separate [ending route matrix](../validation/levels/s3k-ending.md)
continues to distinguish positioned observations from final-fight continuations.

The supported CLI product is explicitly **Tails solo / +2 / NTSC / native
320×224 / LIMITED (238 maximum) / RAW_RGB**. All six options are required;
other products, region/progress values, unknown/duplicate options, malformed,
duplicate, empty or out-of-range outer indices fail. Output must be a new directory.
The ingress is the frozen title-owner v2 protocol: production title setup through
ACTIVE, a real logical Start directly through the title owner yielding EXITING,
no data-select initialization, then an explicitly positioned fresh `$0D01` load
with seven Super Emeralds. VInt0/RNG0 are authored once before initial dispatch.
Every outer step thereafter uses neutral controller input through
`GameplayCaptureSession` and renders the normal scene. This is not a cold
campaign or a native-state replay. TITLE_SCREEN mode is the terminal check;
the capture host does not certify interactive hub recovery.

The caller supplies engine outer indices, one canonical decimal index per line.
For retained native v2, select only `input-*` snapshots strictly before its first
non-ending controller ordinal, assert the requested count, then author the
observation list with **native poll p → engine outer p+1**. This mapping never
enters a gameplay owner. Native inputs, physics, aux rows and screenshots supply
no engine state. The tool records startup ownership, per-outer scene/clock/player/
credits state, requested PNGs, ending-exit/title events and completion ledger,
plus ROM/spot/source/runtime and final CSV hashes. Fresh preferences live inside
the new output directory, so a checkout config link cannot migrate Main settings. It requires a source checkout;
source and compiled runtime fingerprints must remain stable during capture.
The external probe's reflective SST inventories, plane/texture dumps and palette
reconstruction are deliberately outside this recurring raw-RGB question.

Use the current integration checkout's queue wrapper by absolute path from the
intended worktree. Build-only compilation takes its intended-tree/shared activity
leases; release those before the queued capture. On Linux preserve surfaceless
EGL and null audio with no ambient DISPLAY/WAYLAND_DISPLAY. For example, with
`QUEUE`, `ROM`, `CAPTURE` and `NATIVE` set to absolute paths and a newly named
capture label:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  python3 "$QUEUE" -Dmse=off compile -B
env -u DISPLAY -u WAYLAND_DISPLAY EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  python3 "$QUEUE" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  exec:java -Dexec.mainClass=com.openggf.tools.S3kEndingSamplerTool \
  "-Dexec.args=--rom '$ROM' --product tails-solo --region ntsc --progress +2 --out-dir '$CAPTURE/engine-current-v1' --spots '$CAPTURE/engine-outers.txt'"
python3 tools/bizhawk/compare_native_rgb.py \
  --native-dir "$NATIVE" --engine-dir "$CAPTURE/engine-current-v1" \
  --spots "$CAPTURE/native-polls.txt" --mapping native-poll-plus-one \
  --expected-count 165 --size 320x224 --report "$CAPTURE/comparison-current-v1.json"
```

The comparator requires every pair and equal resulting dimensions, including the
explicit `--size` assertion. Default is **no crop**; optional `--native-crop` and
`--engine-crop` each require an in-bounds `x,y,width,height` rectangle. The Tails
product uses neither. Knuckles' separate 348×240 product must not inherit a Tails
crop or acceptance claim. Raw R/G/B bytes are compared without normalization,
shift, rescale, mask or alignment search. Reports include every pair's hashes,
original dimensions, mismatch pixel count and first differing coordinate/bytes.
Missing/bad files, duplicate/invalid spots, count/dimension/crop errors exit2;
any pixel difference exits1; an exact comparison exits0. Existing report paths
are refused. No pair can disappear silently.

Focused controls:
`python3 -m unittest discover -s tools/bizhawk -p test_compare_native_rgb.py -v`
and queued `-Dtest=TestS3kEndingSamplerTool` cover missing/corrupt PNGs, dimensions
with equal byte counts, exact RGB, low-bit pixel changes, explicit mapping,
invalid/duplicate spots, crop bounds and report preservation, plus Java product/
CLI and observation-plan rejection. No broad smoke/full suite, benchmark or PCM
claim belongs to this tool-only slice. Current-source capture evidence follows
only after its single completed run; historical Oct15 pictures are not acceptance
inputs. The later retained after5 comparison supersedes those old errors and
reports all165 pre-exit spots zero, including5119/6143, without proving current
source acceptance.

### Single current-source result

One new capture, `${CAPTURE_ROOT}/s3k-ending-sampler-20261017-t3-0485d89b/engine-current-v1`,
completed on 2026-10-17 at09:06:24UTC. Capture source was frozen composition
`dc338d3e3a30916559a32d4684ff46e9ad77492c` plus the uncommitted maintained sampler;
its exact Java file SHA-256 is
`b47871f82488802ea2c9a356218825eb372b29c518804f65f57d24fdbe45161c`.
The source-tree SHA-256 is
`ad531ab7730e83ff55e2c9a8950f1fc08accb379985a1fc1f20f909f8af7fdb4`;
the compiled runtime-directory SHA-256 is
`975519879e1c303d01d312164797851f05dd443b641d2c59cebf3393bab43673`.
The tool checked both source/runtime fingerprints again after capture. The later
local commit packages those same sampler bytes; it does not retroactively turn
the capture's recorded source HEAD into a clean committed build.

Original native input is
`${TASK_SCRATCH}/s3k-parity-coverage-20261014/ending-pal-native/native-tails-super-320-ntsc-v2`.
Its `state.csv` SHA-256 is
`efa88767158a71826df9e3f8540c35847fa10208ffcfcc49edd44b603c8769d6`,
and `ledger.csv` is
`ba925de564a6d0ef4681aa4990905d4d0e09f6bed705d20ef42d23167c0c7f63`.
Retained `pal-spot-plan-v2.json` SHA-256 is
`1bf7895e9ee47c18a8daed3fb0d2c6cfa2276041347c94b765a1b711dd217fe3`;
retained `run_engine_v2.py` is
`be9a09a2441011cf87d4952500e6b777c6f77f442e7df9f46b288c86b1b75e6f`.
The native ledger has167 `input-*` snapshots. Native first non-ending poll15531
excludes16383/17407, leaving exactly165 pre-exit requests. No set de-duplication
or missing-picture skip reduced that count. The native-poll file hash is
`9c209bd9e707f582920b793302e99551289121505648a98296d3e9edb2ce5335`;
its explicit p+1 engine observation file is
`f490ac9d7e083e83e2c54d8804d3de442f1f884a8ebb05784f663a39b00a53b8`.
These hashes and protocol-source hashes are retained in the external task's
`observation-provenance.json`; native rows selected only observation moments.

Queued command sessions were build16052, focused31401 and capture54720, each
observed by this worker only and now terminal0. Commands from this worktree used
`${MAIN}/tools/testing/maven_queue.py`, Java25, `EGL_PLATFORM=surfaceless`,
`ALSOFT_DRIVERS=null` and no DISPLAY/WAYLAND_DISPLAY. Build16052 ran
`-Dmse=off compile -B` successfully after at least270s waiting for isolated
measurement activity; Maven execution reported59.993s. After the material
fresh-configuration isolation edit, focused31401 recompiled final sources and
ran the following unchanged selection (81s queue wait; Maven1:20):

```bash
python3 "$QUEUE" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS3kEndingSamplerTool,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=$ROM" test -B
```

All62 tests pass, with0 failures/errors/skips: sampler2, bootstrap6, decoding3,
level-loading36+7 and AIZ skip8. Python's nine comparator controls pass with no
skips. Test XML identities/counts were inspected before removing consumed owned
reports. The change-based preview againstdc338d3e3 selected3496 smoke classes
because these new tools are unclassified and an unrelated TraceChaser gitlink
is present. The requested focused tool/domain/capture checks replace that
selection: no gameplay owner, shared algorithm, timing contract or build policy
changed. No broad/full suite, benchmark or PCM workload was launched. Root owns
combined guards and integration. The unrelated gitlink is preserved unstaged.

Capture54720 used the `exec:java` command above (plus `-Djava.awt.headless=true`),
was admitted immediately and completed in12.919s Maven time. Actual title ACTIVE
required305 neutral steps; real Start yielded EXITING with no data select.
State outer0 has progress2, VInt0 and RNG0. Ending exit and TITLE_SCREEN mode
occur at outer15532. The ledger ends
`DONE,-1,requested=165;captured=165;title_mode=true`; its SHA-256 is
`4392567cc986efa196380f84cb9f72792e7946ee2cf6a969925b4d443d24a60f`.
Final engine `state.csv` SHA-256 is
`95996e38208a631811fa6d7c1772bcf04a7dd558035e0cff48f5609328bc7f0e`.
FAST load-time simulation remains the default; runtime logs include deterministic
estimates for missing measured Kos profiles, not native readiness hydration.

Configuration isolation was verified against the loader's actual JVM path:
`SonicConfigurationService` reads through `resolveRelativeFile`, which uses the
current `user.dir` for relative `config.yaml`/legacy JSON, and publishes its
example beside `resolveConfigFile()`. The actual generated
`engine-current-v1/configuration/config.yaml.example` exists (15419 bytes,
mtime09:06:12.610630UTC), inside this capture's private scope. No private
`config.yaml` existed to migrate. The checkout's pre-existing config link points
at Main; that target's observed last modification remains
2026-10-16T09:09:02.849921UTC, before this capture. Thus this run did not migrate
Main config. The tool restores `user.dir` after closing the capture session.

The maintained comparator (SHA-256
`903bae9e12cdc4b2d0e29dc39b983a14c012e2ca2dc84b8a1af5d47dd0e4939f`)
was then executed once with the exact165 requests, `native-poll-plus-one`,
`--size 320x224` and **neither crop option**. It exited0 and reports **165/165
pairs,0 mismatching pairs,0 differing raw RGB pixels**. Every original native
and engine dimension in the report is320×224. Poll5119→outer5120 and
poll6143→outer6144 each have0 differences; they are fresh current-source
observations, not the superseded Oct15 errors. `comparison-current-v1.json`
SHA-256 is `3a1ed2882c0a953e85a500b6367562ca00ff6b0d52f3d2dd7f6f79baac74b32a`.
The actual outer6144 credit-scene PNG was inspected after reading its state.

This establishes only the declared165 positioned Tails/+2 NTSC pictures and
terminal mode for this exact source/runtime. It does not certify the unsampled
frames, full prepared palette timeline, cold campaign, other products, PAL,
Knuckles crops, PCM, strict hardware timing or interactive hub recovery. No
ending runtime fix or pixel fitting was made. Worktree/branch remain local for
Root to account; no Main integration, push or worktree removal belongs to this
worker's slice. All worker execution handles are terminal; the stopping boundary
is the maintained tool plus this one completed comparison.


### Conditional V-int instruction diagnostic intake — 2026-10-17

At composed prerequisite `dc338d3e3`, Root intakes the retained slice-5 diagnostic delta by three-way application,
preserving the newer `forcePlayerOneReload()` giant-ring and Super invalidation semantics. The retained
`7bbf1eb2b` plus uncommitted work had a historical 93/0F/0E/0S focused result; that is not current-source
qualification. No readiness, gameplay, load-profile estimate or canonical trace contract consumes the new term.

`Record.instructions()` separates closed-handler instruction cycles from the unpriced DrawLevel, HUD and
Nemesis callees and from interrupt acceptance, refresh, bus/FIFO waits and DMA freeze. Production refuses busy
Kos, water, Hyper flash, unknown state/region/producers and unsupported routines. Region, queue emptiness and
water presence come from engine owners; `Demo_timer` is a fresh-level mirror captured/restored with the ledger.
Other timer writers and every unobserved wait-loop precondition remain explicit limits. The 1,920-state source
oracle and 42 historical native instruction buckets do not establish predictive six-ending/46-fingerprint closure.

Independent review found sparse opcode pins accepted timing-sensitive operand changes. On the composed dirty
candidate, two new negatives both fail against the old checker at 09:06:36 UTC: PAL delay immediate at `$5BA`
and absolute callee operand after `$920` (2 tests, 2 assertion failures, 0 errors/skips; Maven 1:11). The replacement
checks every byte through `$4E80F`, including `SpecialVInt_Array` routine-0 `rts`, against original-ROM prefix
SHA-256 `29bc98328ecbdb7d8a8a6f00c98af597949b16fdd53ffe9276610aee4a7e4743`.
The full input ROM SHA-1 was verified as `CFBF98C36C776677290A872547AC47C53D2761D6` before deriving that hash.
A minimum-prefix positive and a one-byte truncation negative protect the actual production slice boundary.
Changed bytes anywhere in this conservative prefix refuse pricing; code after it is not authenticated by this check.

The composed dirty candidate passes 211 tests, 0 failures, 0 errors, 0 skips at 09:17:36 UTC (Maven 1:24),
including the two operand/target negatives, all 1,920 source-grid states, production capture/restore, existing
body/DMA consumers, mandatory S3K loading controls and source/trace/hardware-authority guards. Sixteen exact
selectors resolve seventeen classes because both existing `TestSonic3kLevelLoading` classes match. Command:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY ALSOFT_DRIVERS=null EGL_PLATFORM=surfaceless \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --normal -Dmse=off \
  '-Dtest=TestS3kVIntInstructionCycles,TestS3kVIntInstructionProduction,TestS3kVIntDmaLedger,TestS3kVIntDmaLedgerProduction,TestS3kVIntDmaBodyDplcProduction,TestS3kVIntDmaBodyCacheProduction,TestS3kVIntDma,TestSonic3kLevelInitProfile,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestArchitecturalSourceGuard,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault,TestHardwareTimingAuthorityGuard' \
  "-Dsonic1.rom.path=${S1_ROM}" "-Dsonic2.rom.path=${S2_ROM}" \
  "-Ds3k.rom.path=${S3K_ROM}" "-Dkis2.rom.path=${KIS2_ROM}" test -B
```

Independent review found no blocker in the prefix identity, authority or rewind handling. The native fresh
attract-level path initializes the same 1800 timer before GetDemoPtr; S3K currently supplies no generic
EndingProvider/CREDITS_DEMO path. That permitted future phase is not certified: a future S3K provider must supply
its real timer writer or refuse pricing. No fitted timer adjustment was added. This is local diagnostic
qualification only; combined guards, Main integration and push remain Root-owned. No broad/full suite,
performance gain or predictive six-ending/46-fingerprint timing acceptance is claimed for this slice.


### Completion of interrupted smoke selection and bounded composition — 2026-10-17

Main `develop` was fetched/fast-forward-pulled under its normal writer lease and remains
`707b3c2082433c4833da93fbf855a0a748dd58b1`; its foreign submodule dirt and untracked files are preserved.
The original selection used integration base `d3fb0625e179d00ad6eadebbfcc49d38ecd45c9f`, 3,490 candidate classes
in 70 ordinary smoke batches. The retained run on `3aad23d38` timed out during batch65; its consumed diagnostics
were inspected/acknowledged. Completed earlier coverage is retained with the separately recorded focused repair
qualification, not presented as a clean one-SHA smoke pass. The old partial65 is not added to the complete65 below.

After focused repairs, the exact original candidates65–70 (290 classes; no manual selector narrowing) completed
on `dc338d3e3`. Each at-most-50 batch used the updated Main queue wrapper with `--normal -Dmse=off -Psmoke`,
its original `surefire.includesFile`, the original four absolute ROM properties, isolated report/tmp paths and
`test -B`. `JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`, null OpenAL, surfaceless EGL and no ambient
DISPLAY/WAYLAND were retained. Admission/tree leases released between batches; queue waits were not execution
timeouts. Continuations stopped at each failure for inspection and exact baseline attribution, without rerunning
completed unchanged batches. This is smoke coverage, never a release-only full-suite run.

| Original batch | Candidates | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: | ---: |
| 65 | 50 | 292 | 0 | 0 | 2 |
| 66 | 50 | 353 | 1 | 2 | 1 |
| 67 | 50 | 226 | 0 | 0 | 2 |
| 68 | 50 | 357 | 0 | 0 | 0 |
| 69 | 50 | 366 | 1 | 0 | 0 |
| 70 | 40 | 249 | 0 | 0 | 0 |

The six resumed batches total **1,843 tests, 2 failures, 2 errors and 5 skips**. All four failure/error identities
match Main707 in fresh bounded checks with the same launcher environment/ROM properties:

- `TraceCaptureBootDimensionsTest.realBootKeepsNativeGameplayCameraAndWidePresentationCaches(int)[1]` and `[2]`:
  `IllegalStateException: Unable to initialize GLFW`. The matched selection includes preceding
  `TestSurfacelessEglContext`, which clears the inherited EGL property in its existing teardown.
- `TestCompleteRunAudioCli.freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`:
  line182 `expected true, actual false`; the child launcher inherits the Java-options notice before its required
  first-byte diagnostic. Both Main707 and dc338 matched 18 tests / 1 failure / 2 errors / 0 skips in the exact
  `TestSurfacelessEglContext,TraceCaptureBootDimensionsTest,TestCompleteRunAudioCli` selection.
- `TestOrdinaryTitleScreenCommandEvidence.masterTitleDrawExpandsBackdropAndFitsActionPaneAtEveryWidth`:
  `card-row footer hint START at 320`, expected true/actual false. The exact method separately fails once on
  both Main707 and dc338, with the same assertion type/message and zero errors/skips.

The five skips are explicit opt-in/native-mod prerequisites: Slot glass native oracle (`openggf.test.gl.native`),
Second Strike HCZ air mod, packaged Second Strike MGZ arena, deterministic BizHawk S1 audio reference and an
unrequested S1 audio timeline capture. No resumed-batch skip was a missing base ROM. Earlier retained-run skip
limits remain recorded separately; these five do not erase them.

The shared camera consumer neighbors also pass at `534a69e74`: exact
`TestS2ObjectWindowing,TestS2ColocatedObjectPlacement,TestObjectManagerExecLoopParity`, 14/0F/0E/0S at
09:02:37 UTC (Maven26.980s). This supplements the recorded wide LRZ/MHZ fixes and genuine backward-borrow tests.

Root then fast-forwards to `dbae340c26b71ab0770ee0cb3e8e53d134466293`: qualified sampler prerequisite merge
`957c78a54` contains R1`2f058df9a` and diagnostic`a5470cef2`; the ICZ docs-only `e8d51ddf5` adds the two exact
unsuccessful natural-death input attempts and preserves that coverage gap. No unqualified HCZ repair or ending
R2 source is included. R1's 165-pair raw-RGB reference comparison, new-tool focused62 and diagnostic focused211
are bounded separate qualifications with their exact source identities above. Root independently inspected
native poll6143 and engine outer6144; a temporary lossless PNG view of the PPM was deleted after viewing.

Fresh combined guards on this composition are pending. Main integration, packaging, push and accounted cleanup
are not yet claimed. Strict AIZ/HCZ frontiers, ICZ natural death/respawn/rewind, ending/PAL/PCM/timing breadth,
SAT/scanline capacity and other documented parity limits remain open.


Fresh combined guards completed on `dbae340c2` plus this prose: session20097, terminal1 at 09:43:10 UTC,
Maven5m14s, **715 tests / 6 failures / 1 error / 0 skips**. This is a complete red guard result, not a pass.
Two findings belong to this delivery: FadeManager's new white-fade rule lookup crosses from graphics into
game/session orchestration; and the virtual-pattern guard rejects the special-stage mask initializer.
The mask ID was already derived from `SPECIAL_STAGE_PLAYFIELD.endExclusive() - 0x2000`, rather than a literal
ID. The guard recognizes only `.base()` initializers; the equivalent `base() + size() - 0x2000` preserves
exactly the same `$E000` start and 8192-slot bounds without changing the guard or public atlas registry.
The original red guard is its regression witness; focused verification is pending.

The white-fade ownership repair is isolated in the existing diagnostic composition tree. Main707 matched
`TestArchUnitRules,TestProductionSingletonClosureGuard` is queued once to attribute the remaining MenuAudioProbe,
HubMusic and frozen-store findings by exact identity; no baseline result is inferred yet. Root integration,
packaging, push and cleanup remain pending. No full suite or performance acceptance is claimed.



## 2026-10-17 nine declared NTSC ending products

This follow-up extends the maintained CLI beyond the historical R1 Tails-only
support described above. It changes only tooling, configuration isolation,
observation completion and diagnostic provenance; no ending gameplay owner or
readiness/timing authority changes. Prerequisite merge
`957c78a54c49cb9a2358ab6ed8637d4874f0a852` combines R1 `2f058df9a` with Root's
qualified diagnostic-only VInt operand intake
`a5470cef29ea3f90320c2627f93bb562386f8fa2`. The sole conflict was this append-only
Plan. Both complete suffixes were retained and checked against their common
`dc338d3e3` base. R1 private configuration and source/runtime checks were preserved.

The explicit product is now `sonic-solo`, `tails-solo` or `sonic-tails`; region
remains exactly `ntsc`. Frozen `declared-scene-spec-v2.json` and observation plans
v1/v2 supply the declarations: progress `-2` → `0000000`, `0` → `1111111`,
`+2` → `3333333`. Sonic+Tails configures main Sonic and sidekick Tails (native
Player_option0); solo Sonic/Tails use native options1/2. Product, region,
progress, emerald inventory, settings, declared sidekick, pre-dispatch observed
roster and completion provenance are dynamic. Initial production progress is
checked against the declared inventory. None of these values come from native
physics/auxiliary observation rows.

`--completion spots` steps through the largest requested engine outer index,
requires every unique requested picture, verifies the ending remains in LEVEL
mode at that bound, and reports actual last outer/mode. It does not claim an
ending exit. `--completion title` preserves the R1 default when omitted: a neutral
stream must actually reach ending exit and TITLE_SCREEN within60000 outer steps.
Neutral title completion accepts only progress+2; other progress products must
explicitly select the bounded spot contract. Unsupported product, region,
progress, completion and repeated/unknown flags fail. All images remain native
320×224, LIMITED238/RAW_RGB, and the frozen real-title-owner positioned ingress
sets VInt0/RNG0 once before initial dispatch. No new game mode, clock fitting,
scene mutation, native input hydration or normalization was introduced.

The original early-scene native references use `input-0001.ppm` (four-digit
zero-padding), while dense retained Tails v2 uses `input-00001.ppm`. The comparator
therefore adds explicit `--native-digits 4|5`, with5 retaining the R1 default.
There is no filename discovery, fallback, renaming, copied reference or new ROM
link. Every requested pair must exist and both original dimensions are asserted
320×224. All comparisons here omit both crop options and retain strict raw RGB,
with no shifts, masks, rescaling or alignment search. A new negative control
proves four-digit selection cannot silently fall back to five-digit files.

The existing native per-product provenance case and ledger hashes were checked
against frozen plan cases, not guessed from filenames. Early native ledgers
contain exactly the11 poll ordinals
`1,31,63,511,1023,1535,2047,3071,4095,5119,6143`, paired only to engine p+1.
Dense Tails uses the retained v2 first non-ending poll15531: exactly165 snapshots
precede it, with16383/17407 excluded as post-exit. Thus this slice requests eight
11-pair products and one165-pair product,253 comparisons total. Native screenshot
ordinals select observation moments only; they never supply gameplay state.

Focused validation from the owned worktree, with current Main's absolute queue
wrapper, Java25, surfaceless EGL/null audio and no ambient DISPLAY/WAYLAND:

```bash
python3 "$QUEUE" -Dmse=off compile -B
python3 -m unittest discover -s tools/bizhawk -p test_compare_native_rgb.py -v
python3 "$QUEUE" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestS3kEndingSamplerTool,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault' \
  "-Ds3k.rom.path=$ROM" test -B
```

Build86103 passed (56.996s Maven). Focused81696 was admitted immediately and
passed75 tests,0 failures/errors/skips (27.693s Maven): sampler4, required S3K60,
trace replay invariant10 and hydrate-default1. Sampler controls exercise all nine
roster/inventory settings, exact progress labels/native options, R1 default title,
unsupported combinations and strict observation plans. Python's existing nine
RGB controls plus explicit padding control pass10 tests with no skips. The source
and CLI changes are bounded tooling; no broad/full suite, new native production,
benchmark or PCM workload belongs to this slice. Root owns remaining combined
smoke/guards and integration. No timing/performance or6+46 acceptance is inferred.

All captures use one source/runtime fingerprint, one new output per product and
one queued `exec:java` invocation per product, sequentially observed by session
29080. No request is retried or replaced while waiting. The new immutable labels
live under `${CAPTURE_ROOT}/s3k-ending-sampler-20261017-t3-0485d89b/r2-nine-ntsc-v1`;
retained native references and R1 captures remain untouched. Representative
commands for an early product are:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  python3 "$QUEUE" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Djava.awt.headless=true exec:java \
  -Dexec.mainClass=com.openggf.tools.S3kEndingSamplerTool \
  "-Dexec.args=--rom '$ROM' --product sonic-tails --region ntsc --progress 0 --completion spots --out-dir '$CAPTURE/engine-team-chaos-320' --spots '$CAPTURE/team-chaos-320-engine-outers.txt'"
python3 tools/bizhawk/compare_native_rgb.py \
  --native-dir "$NATIVE" --native-digits 4 --engine-dir "$CAPTURE/engine-team-chaos-320" \
  --spots "$CAPTURE/team-chaos-320-native-polls.txt" --mapping native-poll-plus-one \
  --expected-count 11 --size 320x224 --report "$CAPTURE/comparison-team-chaos-320.json"
```

Dense Tails explicitly uses `--product tails-solo --progress +2 --completion title`,
`--native-digits 5` and `--expected-count 165`. Actual commands, declaration/plan/
spot/ledger hashes, each product's source/runtime hashes and outcomes are in the
external `observation-provenance.json`, alongside per-product comparison JSON
and the sampler's own immutable output/provenance. Generated private
`configuration/config.yaml.example` files prove actual loader output paths,
not just a changed property; each output records its hash and restored user.dir.
The linked Main config's hash and mtime are measured before/after the sequential
captures to detect migration. No validation pass cache or new repository record
type is created.

### Completed current-source observations and stopping boundary

All nine engine invocations and all complete comparisons finished. Seven products
have zero RGB mismatches. Two products have current-source differences, so this
is **partial pixel qualification**, not an all-green ending result. Each comparator
required its complete plan and original320×224 dimensions; both crop fields are
null. No native capture was produced, no source was changed during the nine
attempts, and no product was retried. All eight early products ended at outer6144
in LEVEL; only dense Tails actually reached TITLE_SCREEN at15532. There are253
required pairs,249 equal pairs and4 differing pairs across two products.

| Declared product / progress | Required/compared pairs | Differing RGB pixels per native poll | Actual last outer / mode |
| --- | --- | --- | --- |
| sonic-solo / 0 | 11/11 | 0 | 6144 / LEVEL |
| tails-solo / +2 | 165/165 | 0 | 15532 / TITLE_SCREEN |
| sonic-solo / -2 | 11/11 | 0 | 6144 / LEVEL |
| sonic-solo / +2 | 11/11 | 171 at5119;194 at6143 (first9 zero) | 6144 / LEVEL |
| tails-solo / -2 | 11/11 | 0 | 6144 / LEVEL |
| tails-solo / 0 | 11/11 | 0 | 6144 / LEVEL |
| sonic-tails / -2 | 11/11 | 0 | 6144 / LEVEL |
| sonic-tails / 0 | 11/11 | 0 | 6144 / LEVEL |
| sonic-tails / +2 | 11/11 | 171 at5119;194 at6143 (first9 zero) | 6144 / LEVEL |

All captures ran on prerequisite HEAD
`957c78a54c49cb9a2358ab6ed8637d4874f0a852` plus the same uncommitted sampler:
SHA-256 `f674bfe82b5d6432a64ef8237c85c107112a5077689459eb0e8aa421c3d05050`.
Every output records the identical full Java-source fingerprint
`03c45342d00011337acae72d9c46bf9a14456079daacd5ade058cce8735c79a3`
and compiled runtime fingerprint
`fbee8a466206df679d586c986f70e32135483da26b70df202496f93f3a8e38e4`.
Source and runtime were verified unchanged after each capture. The final local
commit packages those same sampler bytes; it does not change the recorded dirty
capture HEAD into a clean committed build. The revised comparator SHA-256 is
`b90da677fe6c666aaa32672ca0c2aa96602bd22e67085faa1f8160f268e02ef3`.

Frozen `declared-scene-spot-plan-v1.json` SHA-256: `feffd33db7cd9e86f35d5b7d763ad223e1bf4e8bcdf3e77cd2d6876a4ff11c01`.

Frozen `declared-scene-spot-plan-v2.json` SHA-256: `a687c92a1db070b060cc9c41fc292ee8de8966ad692cbb9a4b134633f41131d3`.

Frozen `declared-scene-spec-v2.json` SHA-256: `7711c315a9b10fa7cb94fa9c3465fb280509a338d18cf8ebfa0d80ad3fcfc7cb`.

Frozen `engine-title-owner-ingress-spec-v2.json` SHA-256: `768e172c3555a840b929f7154398b5358556a8f411b214087a55fd0e9cb7787a`.

Both Sonic solo/+2 and Sonic+Tails/+2 first differ at native poll5119 paired to
engine outer5120:171 raw RGB pixels. Their native and engine PNG/PPM hashes are
identical between these two products at that pair, but differ between engines
and native. The first differing coordinate is `(7,160)`: native RGB
`(238,238,238)`, engine `(136,204,238)`. The other differing requested pair is
poll6143→outer6144,194 pixels per product. Every earlier requested pair for those
two products is zero. Comparator exits1 for these two products and0 for the other
seven; every engine invocation exits0. Those nonzero RGB verdicts were preserved,
not treated as skipped pictures or coerced to pass.

At the first mismatch the engine reports LEVEL, progress2, level frame5120,
object ordinal5121, VInt5120, foreground routine56 (`$38`), background routine4,
RNG`c7892900`, player centre192/221 and mapping186. Its camera is512/516 and
published copy512/515; credits routine4, part0, row12, delay−1, speed`cf00`.
Native's last published ledger row for that poll records completed hardware5687,
VInt5120, level frame5120, Process/producer5121, publication5120, mode`0C`,
zone/act`0D01`, progress2, RNG`C7892900`, and camera copy`0200/0204`.
Native player mode is1 for solo Sonic and0 for team; native P2 code is zero at
this late spot. Team outputs nevertheless record the actual declared pre-dispatch
roster `sonic,tails`, while solo outputs record their correct single leader.
These are scene/timing observations, not causal attribution. Matching the older
171/194 shape does not make it a newly introduced Root defect, identify an
owning routine, or authorize a clock adjustment. Qualification of each mismatching
product stopped at this completed result. Remaining roster/progress products were
independent declared coverage, not retries; no runtime repair was attempted.
Root's separate read-only investigation may use these exact immutable outputs.

The first queued capture waited512s, dense Tails92s, final team/+2 31s;
the other six were admitted immediately. These are queue waits, not execution.
Maven execution reported6.688s (Sonic0),13.173s (dense Tails+2),5.415s (Sonic−2),
5.571s (Sonic+2),5.646s (Tails−2),6.527s (Tails0),5.443s (team−2),5.399s
(team0),6.110s (team+2). They are diagnostic invocation durations, not benchmark
or performance acceptance, and are not summed with overlapping foreign work.

Each of the nine emitted configuration examples resolves under its own private
capture directory; all have SHA-256
`a52e0507338cce515e7f404c7fb269b963dc423664cf3900119d30de1b739fb4` and
`user.dir.restored=true`. The actual JVM loader resolution remains
`resolveRelativeFile(user.dir)` for config/legacy JSON and example publication
beside `resolveConfigFile()`. Main's linked config before/after hash is exactly
`6abd111c03f798dbe2a618cb4985aeb599127f44668edb2a8edbde118d11f7da`, and its
mtime is unchanged at1792141742849921547ns. This run did not migrate Main config.
Per-product ledger/state/spot/reference-image hashes, the matched native ledger
rows and exact commands remain in external provenance and per-product JSON.

No broad/full suite, additional trace/native pass, new reference production,
PAL/Knuckles/wide/cold/PCM product, terminal breadth for the eight early products,
strict hardware timing or interactive hub certificate follows. There is no6+46
acceptance or readiness activation. Root owns combined guards, runtime diagnosis,
integration and eventual push. The local branch/worktree and unrelated unstaged
TraceChaser gitlink remain for Root accounting. The bounded stopping boundary is
nine fresh captures and their complete comparisons; no further capture or Java
workload is needed for this slice.


### Composed guard attribution and unchanged allocation record — 2026-10-17

Root `74fb068e1` passed focused session31023: **78 tests / 0 failures / 0 errors / 0 skips** at
09:50:48 UTC (Maven1m11s): six actual GPU special-stage sprite/atlas lifecycle tests and 72 architectural
source guards. This qualifies the equivalent range-base expression and newly composed ending sampler source;
the exact ArchUnit range rule will be checked with the remaining architecture fixes.

Main707 session6602 completed the exact `-Pguards -Dtest=TestArchUnitRules,TestProductionSingletonClosureGuard`
selection with **73 tests / 4 failures / 0 errors / 0 skips**. The same original ROM/launcher environment was
retained. Both MenuAudioProbe singleton failures and both HubMusic provider/shared-layer failures match
Root's fresh guard result by class, test name, exception type and complete assertion message. They remain
inherited and unfixed; no failing result is described as a pass.

The additional frozen-store error was caused by the shared AnPal-bank overload: the existing nullable
`PaletteOwnershipRegistry` fallback moved from Sonic3kPaletteCycler's six-argument constructor to the same
class's seven-argument constructor receiving `Sonic3kGlobalAnimationState`. Source comparison confirms the
identical conditional allocation occurs once both before and after this change. Only that existing stored
exception's constructor signature/location is reconciled; the two stored dependencies and unchanged S2 entry
remain. No new exception is added, no production allocation is introduced, and automatic store creation or
updates remain disabled. This preserves the existing ownership debt without hiding an additional dependency.
The exact current ArchUnit check remains pending after the separate white-fade composition repair.




### White-fade policy composition repair — 2026-10-17

The fresh combined guards on `dbae340c2` found 13 new low-level/runtime edges:
`FadeManager` queried `SessionManager` and the world's `PaletteWhiteFadeModel`
during update/render/publication. Keep the public host rule and its existing
signatures; `GameplaySessionFactory` now translates it once into palette/overlay
and FromWhite-period values through `FadeManagerInternalAccess`. Graphics no
longer discovers gameplay orchestration. The bound values survive logical reset
and snapshot restore; renderer attachment still limits CRAM publication to its
active owner. Detached UI managers keep their overlay default. No guard or
frozen exception store was changed.

Focused validation in `feature/ai-s3k-vint-instruction-refusal`, based on
`dbae340c2`, used Java 25 and the current main absolute `maven_queue.py --normal
-Dmse=off`, `test -B`, no ambient DISPLAY/WAYLAND_DISPLAY, surfaceless EGL, null
OpenAL and the original absolute S1/S2/S3K ROM properties. The first selection
also used `-Dopenggf.headless.gl=surfaceless-egl`:

- `-Dtest=TestWhiteFadePresentationBinding,TestNativeWhitePaletteFade,TestUnderwaterPaletteUpload,TestFadeManager,TestFadeManagerRewindSnapshot,TestPaletteUploadLatch,TestPaletteWhiteFadeModel,TestLevelSpritePresentationLifecycle,TestS3kLrz2SevenChaosGiantRingGateway,TestGameplayModeContextRewindRegistry,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`: 150/0F/0E/0S, finished 09:52:10 UTC. This resolves 15 classes because two classes share the LevelLoading simple name. Native white GPU14, underwater16, rewind7, immediate-scene latch5 and gateway4 genuinely ran.
- After strengthening only the new test's explicit snapshot-restore assertion, `-Dtest=TestWhiteFadePresentationBinding,TestGameplayModeTeardownFailures,TestSessionManager,TestSessionManagerReadAccessors,TestGameplayModeContextSpecialStageRewindAdapter,TestGameLoopRewindBoundaryPolicy,TestModEngineWiringSeams`: 57/0F/0E/0S, finished 09:53:19 UTC. The new tests verify factory-bound S3K semantics despite a later ambient S2 world, reset and snapshot restore, plus a detached overlay while an S3K world exists. Production retains its non-null rules contract.

The unchanged native component algorithms, 22-V-int duration and `2056a99cd`
immediate-scene rewind-release path remain covered. Source inspection confirms
no `SessionManager`/`game.rules` dependency in `FadeManager`. Root owns the
combined architecture-class check after its independent mask/store repairs,
integration and push; this slice does not claim a fresh green architecture or
broad suite. Consumed local logs/XML were removed after checking all skips.


### Final affected architecture qualification — 2026-10-17

Composition `3f6758cef0b528eef888874671e37ae7c3760fbf` includes the qualified white-fade binding
`b3cf0337a`, equivalent mask-range expression, existing AnPal fallback-signature reconciliation, ending
sampler `3896035af` and ICZ authoring-limit record `04506e5cd`. The white-fade repair separately passed
150 focused native-GPU/palette/rewind/gateway/required-S3K tests and 57 binding/reset/restore/session
construction/lifecycle neighbors, each with zero failures, errors or skips. Public rule/API signatures
are unchanged. Complete appended Plan evidence from both parents was preserved during the sole doc conflict.

The final affected command used the current Main queue wrapper from the intended Root checkout:
`--normal -Dmse=off -Pguards -Dtest=TestArchUnitRules,TestProductionSingletonClosureGuard,TestArchitecturalSourceGuard`
plus the original four absolute ROM properties, isolated report/tmp paths and `test -B`. The same
surfaceless-EGL/null-OpenAL/no-ambient-display/Lua5.4 launcher environment was preserved. Session94450
completed terminal1 at **09:56:49 UTC**, Maven1m19s: **145 tests / 4 failures / 0 errors / 0 skips**.
All four failure identities, exception types and complete messages exactly equal Main707's matched73-case
result: MenuAudioProbe's two singleton checks and HubMusic's provider/shared-layer checks. The FadeManager
lower-layer violation, mask initializer finding and frozen-store error are absent. No exception was added,
guard weakened, automatic frozen-store update enabled or unrelated baseline code fixed. This is bounded
validation with inherited failures, not a green suite.

The original 70-batch smoke selection is covered across the retained completed batches, exact resumed
65–70 and separately recorded focused repairs. Neither the interrupted partial65 nor overlapping focused
products are added to manufacture a one-SHA test total. The completed715-case guard run and final affected
145-case comparison have their distinct source identities above; unchanged guard coverage was not repeated
as a ceremony. The full suite was not run. Main707 was fetched/fast-forward-pulled again and remained
unchanged, preserving all foreign dirt. Integration, package-with-tests-skipped, push and accounted cleanup
are the next delivery steps, not already claimed here.

All nine current NTSC ending products were sampled exactly once:253 required pairs,249 exact and four
different. Sonic/+2 and Sonic+Tails/+2 share171/194 late cloud/water pixels; seven products match, including
Tails/+2's165-pair title-reaching run. Read-only image/native-state analysis finds a one-pass reunion
background phase deficit; production queue causality is being observed separately without changing timings.
ICZ's third finite controller attempt also ended alive at4594, so natural death/respawn/history/rewind
qualification remains open. Strict HCZ work and the retained native diagnostic attribution stay separate
from this delivery. No all-S3K, PAL, PCM, predictive six-ending/46-fingerprint timing or performance acceptance
is claimed.




## 2026-10-17 passive reunion queue causality

This R3 follow-up starts from local R2 commit
`3896035afa98fc0d52611e982cd2a82b1b92f3f9`, without adopting later Root source.
Exactly two metadata-only invocations observe solo Sonic/+2 and solo Tails/+2,
NTSC/native320/LIMITED238/RAW_RGB, default FAST, neutral inputs and the same frozen
real title-owner VInt0/RNG0 ingress. This is positioned protocol, not a cold
campaign. Native rows never enter gameplay or readiness. R2's Sonic/team late
171/194 RGB results remain unresolved; no pictures, native reference production,
retry, timing repair or manifest edit occurred here.

The recurring [queue probe](../../../src/main/java/com/openggf/tools/S3kEndingQueueProbeTool.java)
reuses the sampler's private configuration scope and installs the existing
session boundary observer only when it is unowned. It restores the observer and
`user.dir` on exit. Its CLI accepts only the two solo/+2/NTSC products and an
explicit inclusive level-frame window of 1..128 frames. All timing policies must
remain LIVE, recorded admission must be inactive, and art diagnostic
`serviceObservations` must remain empty. The JSONL projection includes real
handle kind/ordinal/submission fingerprint, descriptors, parent/child identity,
readiness/claim/retirement, profile activation, assigned/remaining service frames,
eligible boundaries, decision source/model, physical queue head/module metadata,
last serviced boundary and scene clocks. Prepared bytes, decoder/rewind snapshots
and native data are not serialized or applied. Diagnostic `queuedFingerprints`
are explicitly distinct from timing submission fingerprints.

Immutable output label: `${CAPTURE_ROOT}/r3-reunion-queues-v1/`, with
`engine-sonic-super-320` and `engine-tails-super-320`. Each invocation exits0,
emits187 JSONL rows including every47 after-step LF4400..4446, stops at outer4446
in LEVEL, and emits zero image files. Both journals are byte-identical, SHA-256
`3223626a036b68b151048cd9f7e8c74c1dcf6d92dcf77f880e626540da18552e`.
Per-product state differs as expected; each full4447-row state prefix exactly
matches its own retained R2 state prefix. This is a bounded passivity control,
not a full world-state/rewind equivalence proof. Both provenance records prove
FAST, LIVE, observer restoration and private configuration publication under
that output's `configuration/config.yaml.example`; Main's linked config hash and
mtime remain unchanged. Existing loader resolution via `user.dir` is preserved;
no Main config migration occurs.

Captured source is HEAD389 plus the exact uncommitted tool bytes packaged by
this follow-up. SHA-256 sampler:
`f3911f7a1512f210564c64369e5c873531d564797d9a1d531bf4c06dcc8142c9`;
queue probe:
`c1462eaebb7667897fdecabbd3513a411be26efcdf4817e0906976397f0499b7`;
aggregate Java source:
`0daa47e529d01366cb7d452e25e2abffb0511b2c26754b418efc1f186cbf896a`;
compiled runtime:
`06fb46e90ad4eef2e7d13a9fc8be3ba016dcb248110a78f237ffdcfe53ea48ad`.
Unchanged FAST manifest SHA-256:
`c9a0e04c59be87e8bfdf3ef3cf6bff3e342b161a0f6202bbe3f4f2e8a2fbcf06`.
Sonic state SHA-256:
`fd859dddd235f585334628581ba33c6cf69208fdde5fa692431c1311bdb68d88`;
Tails state:
`f5656e31fc09de9fd8d863d701b42ea5c3cb4785db93837d61e27ebfba8f6772`.
Both ledgers:
`76a6bc10d8589776d0f1bb2a66f3786b8512b70e7b2919e94f3ec3fb7b5e8531`.
Exact commands, source/output hashes and configuration proof remain in the
existing external `observation-provenance.json` and product provenance files.

### Observed production chain and retained native control

Published ROM `loc_5ADCE` queues `SSZ2_128x128_Kos` at `$1D17A6`, then
`SSZ2_16x16_Kos` to Block_table, then moduled art; `loc_5AE0A` tests
`Kos_modules_left`. The following are actual production handles, not an inferred
mapping from the facade's diagnostic fingerprints. All direct jobs below use
`KOS_DECOMPRESSION_QUEUE`; all parents use `KOS_MODULE_QUEUE`.

| Actual handle/source | Profile activation / assigned services | First ready/retired after-step LF | First claimed LF |
| --- | --- | --- | --- |
| Direct19 / `$1D17A6` | 4402 /4 | 4405 | 4444 |
| Direct20 / `$1CF2D4` Block_table | 4405 /3 | 4408 | 4444 |
| Direct21 / `$1CFB46`, child of parent8 `$1CFB44` | 4408 /4 | 4412 | 4413 |
| Parent8 / `$1CFB44` | child sequence | 4426 | 4427 |
| Parent9 / `$163418` | child sequence | 4433 | 4434 |
| Parent10 / `$162914` | child sequence | 4440 | 4441 |
| Direct29 / `$160D8E`, child of parent11 `$160D8C` | 4441 /2 | 4442 | 4443 |
| Parent11 / `$160D8C` | final child | 4443 | 4444 |

Direct19's **actual submission fingerprint** is
`sha256:5b4280f6fbe1de79558386df9153a4f16e91d1abfc34b719c02a9f57292bcde3`.
It is MEASURED, `s3k-kos-v1`, profileActive, assigned4 and eligible only at
PRE_MAIN_LOOP for both products. Its remaining count is4 after POST_OBJECTS at
LF4402, then3/2/1/0 at PRE_MAIN_LOOP observations LF4402/4403/4404/4405.
The timing job is already ready and physicallyRetired at the last callback;
the physical facade still lists it until the subsequent art-after hook, with
Direct20 visible after-step4405 and not serviced at that same boundary.
Direct21's actual child fingerprint is
`sha256:e56bd0cfc2bfc32728102336cd4703f19f9f5b54dd431dbd355b011d8edf96a9`.
The parent/child progression and later service/claim chain are identical between
Sonic and Tails. Physical module state becomes IDLE/modules0 after-step4443;
logical `modulesLeft` is false then, and foreground40 advances to44 at LF4444
for both. These observations bind the formerly hypothetical four-service head
to the production readiness chain and gate.

Boundary phase matters: PRE_MAIN_LOOP's existing callback is after timing
service but before runtime art's after-service hook; VINT/POST callbacks run
after both. The next outer step's VINT callback still reads the previous LF,
then gameplay increments LF before POST/PRE. Rows therefore retain outer,
level-before-step, level-at-observation and callback phase separately. PRE is
the completed loop tail, not an extra next-frame service. Timing retirement and
physical facade membership are separate observations. No clock correction or
alignment search is used.

Retained native evidence under `${TASK_SCRATCH}/ending-native-reference-prerequisites/`:
`native-kos-budget-sonic-super-ntsc-320-v7/kos-service.csv` SHA-256
`63e60a24c5bb8abbc6eb0f828115a675def132576881ec792a965fee5cac4528`,
and the corresponding Tails file SHA-256
`4efcc5a54266f254f5c51a8efb733bd09402b7fde561b3216671aca5f99aa70d`.
Native Sonic services `$1D17A6` at LF4402..4404 and reaches its end at4404;
Tails services4402..4405 and reaches its end at4405. Native Block_table retires
4407/4408 and the first module child4411/4412 respectively. Retained
`native-cloud-transition-{sonic,tails}-super-320-v5/branches.csv` records final
modules0 at4442/4443 and gate foreground44 at4443/4444. No native run is repeated.

**Finding and blocker:** the current production profile collapses the native
three-versus-four service distinction into four for both products, preserving
the downstream one-pass gate difference for Sonic while matching Tails. This is
the missing production causal link, not a new-root regression claim or a pixel
repair qualification. The [existing budget investigation](../designs/2026-10-13-s3k-ending-lifecycle.md)
already measured nearly identical head work (about211k instruction cycles) with
different available service windows. `S3kKosServiceModel` models the ROM decoder
with caller-supplied instruction-cycle budgets and explicit BOOKMARKED versus
INTERRUPTED_LIVE handling; it does not supply the caller's budget or activate a
runtime policy. A supported budget still needs exact per-object/player/tail
animation/DPLC/sprite/screen CPU paths, phase to the next VInt, refresh/Z80/HInt
and interrupt acceptance, VInt body/callees and VDP/DMA stalls outside the decoder.
`S3kVIntInstructionCycles` explicitly leaves VIntDrawLevel, UpdateHUD and
ProcessNemQueue2 unpriced; the VInt/DMA ledger is diagnostic-only. Available
metadata does not provide those hardware inputs. Changing4 to3 would fit Sonic
and lose the Tails control. No character/route-keyed cost, manifest adjustment,
readiness activation or calibrated budget is justified. The causal observation
ends here; deriving a complete budget is a separate evidence-led slice.

### Focused verification and stopping boundary

Java25 build-only: `python3 "$QUEUE" -Dmse=off compile -B` exits0 (session16453).
Focused command, from this tree with current Main absolute `$QUEUE`:

```sh
python3 "$QUEUE" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl   '-Dtest=TestS3kEndingSamplerTool,TestS3kEndingQueueProbeTool,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault' test -B
```

Session76536 exits0:18 tests,0 failures/errors/skips (sampler4, probe3,
trace-authority10+1). Controls cover declared CLI/window refusals, actual
parent/child projection without payloads or decoder snapshots, and LIVE-only
refusal without policy mutation. Runtime sources are unchanged from389; mandatory
S3K4's retained R2 sixty checks are not repeated. The unchanged Python comparator
is not rerun; there is no broad/full/PCM/benchmark or snapshot/replay workload.
No semantic red test is promoted.

Each product is invoked once, in the same session94261, using:

```sh
python3 "$QUEUE" -Dmse=off -Dopenggf.headless.gl=surfaceless-egl   -Djava.awt.headless=true exec:java   -Dexec.mainClass=com.openggf.tools.S3kEndingQueueProbeTool   "-Dexec.args=--rom '$ROM' --out-dir '$CAPTURE_ROOT/r3-reunion-queues-v1/engine-$PRODUCT-super-320' --product $PRODUCT-solo --region ntsc --progress +2 --level-window 4400:4446"
```

`PRODUCT` is explicitly `sonic` then `tails`; ROM is the discovered original
absolute file, never copied/linked. Every command uses JDK25, surfaceless EGL,
null OpenAL, no DISPLAY/WAYLAND_DISPLAY and the normal queue/activity wrappers.
Both observations admit immediately; Maven execution reports4.766s/4.300s.
Build56.729s and focused28.563s are invocation durations, not performance claims.
Consumed focused reports are inspected then removed under the metadata lease.
No jobs/observers remain. The worktree, branch, immutable observations and unrelated
TraceChaser gitlink remain for Root accounting; no Main edit, integration or push.
This does not certify new RGB pairs, team causality, later exit, PAL/Knuckles/wide,
cold campaign, strict hardware timing or6+46 acceptance.


### Integrated delivery and published native diagnostic follow-up — 2026-10-17

Main `develop` fast-forwarded `707b3c208` to **`460715cbca03d81b4f289051ccb9779cdc8ff5be`**, preserving
its exact foreign dirty-path inventory and current branch. Main session38756 ran the current queue wrapper
with `-Dmse=off -DskipTests package -B`: terminal0, Maven1m28s, **10:01:43 UTC**, explicit `Tests are skipped`.
The resulting develop push was remote-verified at that exact commit. This delivers the qualified runtime
and matrix changes recorded above; it does not close the remaining parity limits or turn baseline failures
into passes. All consumed Root/Main validation diagnostics and one-off tail controllers were removed
under normal metadata leases; no receipts, pass caches or raw-log archives were created.

The separate passive ending observation tool `fe7efc795` is now composed locally. Its original18 focused
checks and exactly two metadata-only products are recorded above; no new pictures or timing-profile edit
is implied. Production observations establish the static profile gives the same four services to both
reunion heads, while native Sonic and Tails have different caller-window budgets. A character-specific
three-service override would break the Tails control and was not made.

The retained native three-file FIFO repair was committed as **`322851cbfa2e1c1803332142e11ab74b4eb34eea`**,
parent `4ae64e075`, then fast-forwarded/pushed through the existing native main integration tree. Root
independently reviewed the busy-head end-cursor allowance and its positive/negative controls. Two forced-fresh
current/published builds ran exactly four disputed selectors per source: each20pass/40fail/2skip. Thirty-nine
S1/S2 behavioral failures match the previously published source by exact identity/assertion. The remaining
failure on both builds is the historical executable hash pin (fresh builds differ from its frozen binary);
no pin or fixture was refreshed. Fourteen earlier prerequisite failures remain explicit, unrerun limits.
The unchanged18ExactKos,70HardwareTiming and CLI control results remain separate qualifications. Both native
repository/history policies and guidance mirrors passed; consumed native diagnostics were deleted.

Native source-only CI [39023704546](https://github.com/OpenGGF/TraceChaser/actions/runs/39023704546) completed
**success** on322851c; its optional native-integration job was **skipped**. This is not native-gate or full-suite
acceptance. The engine's optional immutable gitlink and its two literal boundary assertions now select that
published revision. The foreign Main native checkout at `e0a2443e0` was preserved, including its independent
KiS2 work. No native schema/ABI, gameplay writer, canonical fixture or CPU-budget prediction was changed.
Focused engine tool/boundary/source checks on this follow-up composition are pending; its later Main
integration/package/push and fully accounted worktree cleanup are not claimed yet.


Follow-up composition `c6cc2db2f9f75e1284633a3f5f3609b8125f8f93` completed focused session55438 at
**10:13:42 UTC**, Maven1m21s: **159 tests / 2 failures / 0 errors / 0 skips**. Exact selection was
`-Pguards -Dtest=TestS3kEndingQueueProbeTool,TestS3kEndingSamplerTool,TestHardwareTimingAuthorityGuard,TestArchitecturalSourceGuard,TestProductionSingletonClosureGuard,TestTraceChaserBoundaryGuard,TestBuildToolingGuard#traceChaserStaysExactOptionalAndOutsideOrdinaryBuilds`
through the current Main queue wrapper, `--normal -Dmse=off`, isolated report/tmp paths and `test -B`, with
EGL/null-OpenAL/no ambient display and Lua5.4. These are non-ROM unit/source/boundary selectors.

The seven products contain probe3, sampler4, hardware-authority25, source72, singleton42, optional-boundary12
and the single exact-pin build-contract method. The only failures are the two previously matched
MenuAudioProbe singleton assertions, with unchanged test identity/type/message. All tool/boundary/authority
checks pass and no selector is skipped; the existing HubMusic failures were outside this bounded follow-up
and remain inherited from the prior exact145-case comparison. No runtime timing/profile change or fresh
pixel/route/full-suite certification is inferred. This bounded selection covers the added passive tool and
immutable optional pin without repeating unchanged smoke or native captures.

Cleanup accounting found the untracked Knuckles diagnostic source in the free ending tree still has
unknown provenance. Its complete11782-byte source was preserved exactly in task scratch with
SHA256`1c4916a595d98e7955f12389761a3cd372443af6dc50328222352c8be0863b14`, but the original tree/branch
will also remain `cleanup_pending`; no unknown file is deleted. Active/unmerged HCZ work and old retained
lanes likewise remain preserved. Other named delivery trees are eligible only after this follow-up's
Main verification/push, using exact paths and normal Main inventory leases, with no global pruning or
submodule deinitialization that could alter the foreign Main checkout.


### Published follow-up and manual-version isolation correction — 2026-10-17

Follow-up **467d070a35e1ba311641a1864830ef1099df8f66** was integrated into Main
`develop` and its push was remote-verified. Main package94281 exited0 with
`-Dmse=off -DskipTests package -B`, explicit `Tests are skipped`, Maven1m25s,
finished **10:20:32 UTC**. The preceding159-case focused result remains the
qualification; no unchanged smoke/native capture was repeated. Consumed reports
and the package log were removed under normal metadata leases. Root's delivery
tree and six completed lane trees were removed after exact ancestry/status checks;
merged engine task branches were deleted and native canonical `main` retained.
The unknown Knuckles source/tree and active/unmerged lanes remain preserved.

The resumed CNZ worker subsequently ran the manual shell command
`mvn -v | head -5` in `t3-fa8da232`, outside the queue/activity/tree leases.
Its owned command record bounds execution conservatively at
**10:26:05.341–10:26:05.550 UTC**, epochs1792232765.341–1792232765.550.
The isolated performance pair's audit was1792232627.9009695–1792232765.819888;
the entire probe bracket falls inside that audit. The reported Java PID2167043
has no retained PID/argv binding to the probe. Script inspection shows the
Maven launcher invokes both a suppressed `java -version` helper and
`org.codehaus.plexus.classworlds.launcher.Launcher -v`; these are possible
identities, not observed process argv. The performance owner excluded the whole
pair; no acceptance or optimization rejection follows. No foreign signal, source,
lease, reservation or policy change was made, and existing queued work was preserved.

The fixed category preflight already leases its Maven version probe; the manual
command bypassed that implementation. The agent mirrors and testing guide now
show the leased wrapper command for independent version checks, and the existing
measurement hazard records the manual path and multiple-JVM launcher. This is a
documentation correction only, with mirror, whitespace, link and command-routing
scenario checks; no new JVM, engine tests or scheduler implementation is needed.


## R3 composed strict HCZ qualification — 2026-10-17

Local merge21327d78a combines worker760 with published Main460715cbc, preserving
both append-only Plan contributions, Root's fresh-title/DPLC/white-fade APIs and
the actual clean TraceChaser4fdf checkout difference. Frozen executable digest
8b2bb7e6e6bc297554c97501fa1051555bbdd20e018e6da4b6f95a04a9d7048d.
Canonical3815 finishes6/1F/0E/0S: all3 prefixes and standalone AIZ/initial HCZ pass;
returned HCZ11 consumes0–14207 and reports134710 field mismatches
(115689physics/19021animation), bootstrap/warnings0, first reported non-camera
6674 `rings`114/0; giant-ring exit absent. The summary does not identify first
camera/animation or a full span history. HCZ90977 finishes2/1F/0E/0S, all31482
reference rows,29302 comparison records,4353 spans(3424physics/929animation),
first9482 `air`1/0, first animation9486, Poindexter passes. Its entire error array
matches the prior4353 digest, not merely its total. Matched dc3384699/8664353
remains mixed; historical3738 is unmatched. Alternate760's one26179 ring span is
reused with no post-merge alternate pass claimed.

No runtime fix followed: a separate fresh-load37-ring semantic trial70863 failed
2/2F, but native canonical6674 is an active in-level boundary and does not prove
that load owner caused it. The trial is removed, committed checks untouched.
Obj_TitleCardWait's real child-movement/reset ownership still needs matched live
parent/latch evidence; no fitted dispatch delay is justified. Slice9482's stale
interact pointers likewise do not prove deletion support ownership. Exact GLFW
oracle, full route exit/auxiliary/products remain unqualified. No broad/full,
children/new tree, Main writes or push; no own live jobs. [Exact selections,
windows, all groups, rejected attribution and limits](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r3-current-composed-strict-qualification--2026-10-17).


## R4 retained HCZ title-parent movement gate — 2026-10-17

After98e2c6029, native instruction observation proves retained slot15 with
children16/18/19/20: final movement69754, parent clears$3469755 and resets rings
only69756 (canonical6680). The old manager countdown reset at6674 while the act
child still moved. HCZ now requests the existing native-wait sentinel; request
normalization/carry preserves it and the carried SST uses the existing literal
child graph. Requested control lock and hold90 remain unchanged. No new state,
public API, timing input, row/zone fit or source-budget allowance.

Independent final old-red48402 is3/3F; focused65721 is182/0F/0E/0S including
whole-world recreation/replay twice, actual higher-slot hole/latch order and
mandatory S3K4; guards22781 are131/0/0/0. Frozen executable digest
e49c68da90e35b1bb8173ba57053464cefa9e5dbf273715f754cd0e453e01773.
Affected canonical chain2015 is1/1F/0E/0S: same14208-row returned-HCZ window,
133771 field errors(114800physics/18971animation), first reported non-camera
**7134 sidekick_x0175/0176**;6674 rings is closed. Exit remains absent; first
camera/animation is not reported. Distinct-BK2 HCZ65594 is2/1F/0E/0S and its
entire4353-span error array is unchanged:3424physics/929animation, first9482air,
first animation9486, Poindexter passes. This is bounded proof, not a green route
or second clean repaired-owner traversal. Alternate760 is reused, no new pass;
matched4699/4353 remains mixed and3738 unmatched. Exact GLFW, broader products,
auxiliary fields and later exit remain unqualified. One fix, no further sweep,
broad/full, Main writes or push; own jobs terminal. [Native argv/UTC/pins, exact
selectors, all groups and limits](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r4-retained-title-parent-and-child-movement-latch--2026-10-17).


### Current integration-base HCZ attribution — 2026-10-17

Root isolated tree `ai-s3k-current-parity-composition`, unmodified executable
base **8030832d2ab6080e5324cdfe35e971677fd97b41**, runs exactly
`-Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds`
with the current Main queue wrapper, `--normal -Dmse=off`, explicit surfaceless
EGL, null OpenAL, no ambient display, original verified S1/S2/S3K ROM properties
and the R4 single-fork/reuse/alphabetical settings. Session66140 exits1 at
**10:47:35 UTC**, Maven1m40s: **1 test /1F/0E/0S**, test24.447s. The generated
worktree config link was replaced by an owned private exact copy before any JVM;
Main config hash and modification time were unchanged after execution.

Ordinary segments0/2/4/6/9 have zero physics and animation errors. Segment8 is
complete with4 physics errors, first7126 `x` native640/engine0. Segment11 is
**incomplete**, with81981 field errors (69334 physics,12647 animation), first
non-camera1768 `y` native2160/engine2161. Its recent-mismatch ring ends at5725;
that ring is not a complete span history or proof of the exact consumed-window
length. The walk loses production ownership at BK2 cursor68801 in HCZ act1
(internal act0). Warnings/bootstrap errors are zero.

The qualified HCZ lane3896deae8 closes segment8's load boundary and consumes
the represented14208-row returned-HCZ window, first non-camera7134
`sidekick_x`,133771 field errors. Those totals cover different scopes from
the incomplete base and must not be compared as an equal-window error reduction.
The distinct independent HCZ slice retains4353 spans. The remaining returned
route, giant-ring exit, auxiliary schemas and later campaign remain open.
Root composes the source-backed fixes for affected validation; no clean broad
run or new whole-chain pass is implied by this merge. The public drowning-rule
addition retains the four-argument constructor and normalized mutable0.7 pin;
the descriptor remains unpublished candidate0.7.0 with no published baselines.


## CNZ1 fixed-input miniboss completion recovery — 2026-10-17

Owned branch `feature/ai-s3k-cnz-natural-miniboss-edge`, task checkout
`t3-fa8da232`. Original Claude Opus5.5 work stopped at its provider limit with
exactly two task-owned uncommitted files: `CnzMinibossInstance` and
`TestS3kCnz1MinibossCompletionEdge`. GPT-6.1 resumed those files, verified no
owned live job, and fast-forwarded from `0197c5a5a` to delivered Main
`467d070a35e1ba311641a1864830ef1099df8f66` under the normal intended-tree build
slot. Both original byte sequences were preserved through adoption, without a
stash or Main mutation. Main's boss file had no intervening changes. Hooks were
installed; integration/push and combined smoke remain lead-owned.

The retained run2 had646 tests,17 failures and1 error. It already contained
successful native Sonic solo/pair, wide Sonic solo/pair and wide Knuckles
completion timelines; native320 Knuckles remained dead at tick1294 in its first
retained candidate. Recovery ran each known successful input policy exactly once
(native Sonic variant0, wide Sonic6, wide Knuckles9) to retain its buttons, then
removed authoring/search policy entirely from maintained tests. Three fixed RLE
button resources now drive five independent rows. No uncovered pilot was
reauthored or swept. The [CNZ1 matrix](../validation/levels/s3k-cnz-act1.md#fixed-input-miniboss-completion-and-nine-rewind-spots--2026-10-17)
records the declared positioned inlet, exact milestone table and remaining axes.

Two production owners changed, each with shipped-ROM evidence and a short red:

- `Obj_CNZMiniboss` at `$6D99C` compares camera X with `$31E0` and returns below
  it (`303c31e0b078ee7863024e75`). The prior extra engine `$3000` precondition
  allowed wide object admission to dispatch Init before that gate. The retained
  gate correction is preserved. On the current composed base with only that
  correction reverted, session92170's observed owned Python2168003 run completed
  at10:27:43 UTC:2 tests,1 failure,0 errors/skips. Wide800 failed at tick186,
  routine2 versus0; native320 passed. After the correction, both short rows pass
  and all completion rows observe the actual120-tick arena wait.
- `loc_6DDD2` explicitly clears top collision, calls `Displace_PlayerOffObject`
  (`4eb90008594e`) and then deletes (`4ef9000852a0`). The omitted displacement
  left Tails riding the deleted top at native tick2308: captured spawn
  `($326E,$02CC)`, `$A6`, slot10; restored riding list empty. Strict full-world
  equality correctly failed. The bounded local top correction consumes each
  standing bit, releases only that owner's folded support, clears OnObj and sets
  InAir, through the existing extended native-P2 participation policy. It never
  writes position/velocity or changes the shared contact/snapshot owners.
  Session67600's one-case old-source witness was red at10:40:18 UTC on the first
  standing-bit assertion. The corrected control releases P1/P2 and preserves a
  third participant's other support. Native scroll restore now passes twice.

Maintained full-world coverage has nine checked spots per row: prespawn, live
attack/children, killing hit, post-defeat scroll, FG refresh, end sign, results
before reload, held-title CNZ2 and released CNZ2. Each restores immediately and
replays the same45 inputs twice; the results spot uses4 to remain before owner
replacement. The visited-spot assertion prevents silent window overlap/skips.
All registered-world fields remain in comparison, including object and contact
state, world mutation, players and admitted art/pattern contents. This is not a
live GameLoop-history or raw cross-owner-replacement restore claim.

Rejected setup approaches are preserved by their concrete failures, not logs:
retaining a prior module title owner allowed stale runtime-art leases in the
isolated replay method (5 errors); resetting to the generic bootstrap alone
selected S2 against the S3K ROM (14 setup errors, out-of-bounds `$555557F0`);
a checked-ROM getter draft and one missing method-reference import were compile
failures, not test results. The final test uses the existing fresh
`configureGameModuleFixture(SONIC_3K)` before each independent declared inlet.
Isolated phase tests explicitly consume the real one-update Go→Start handoff
before testing their active phase; the separate false→true gate witness stays
unchanged. No gameplay exception was added to preserve the old unit escape.

Commands below ran from the owned checkout with Java25, original absolute ROMs
and the current Main **absolute** queue wrapper; `OGGF_MAIN` denotes that main
checkout, and the ROM variables denote its original `s1.gen`, `s2.gen`, `s3k.gen`
(no copies/links). The locked-on S3K SHA1 was
`CFBF98C36C776677290A872547AC47C53D2761D6`. Common invocation:

```bash
Q=(env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH
   EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null
   JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
   python3 "$OGGF_MAIN/tools/testing/maven_queue.py" --normal
   -Dmse=off -Dopenggf.headless.gl=surfaceless-egl)
"${Q[@]}" "-Ds3k.rom.path=$S3K_ROM" "-Dsonic1.rom.path=$S1_ROM" "-Dsonic2.rom.path=$S2_ROM" \
  '-Dtest=TestS3kCnz1MinibossCompletionEdge,TestCnzMinibossAnimationArt,TestCnzMinibossDefeatPhase,TestCnzMinibossInstanceBase,TestCnzMinibossOpeningPhase,TestCnzMinibossPaletteOwnership,TestCnzMinibossRegistered,TestCnzMinibossSwingPhase,TestCnzMinibossTopPhysics,TestCnzMinibossConstants,TestCnzMinibossArenaEntry,TestS3kBossDefeatStopsLevelTimer,TestS3kBossTouchResponseProfiles,TestScalarOnlyCodecDeletion,TestS3kCnzMinibossArenaHeadless,TestS3kCnzMinibossHeadless,TestS3kCnzAct1EventFlow,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestObjectSolidContactController' test -B
"${Q[@]}" -Pguards "-Ds3k.rom.path=$S3K_ROM" '-Dtest=TestArchitecturalSourceGuard' test -B
"${Q[@]}" "-Ds3k.rom.path=$S3K_ROM" '-Dtest=TestS3kCnz1MinibossCompletionEdge#recordedOrdinaryInputReplaysAndRewindsEveryEdgeBoundaryTwice' test -B
```

The final affected invocation79883 completed terminal0 at10:43:10 UTC,
**710/0 failures/0 errors/0 skips**, Maven39.833s, no queue wait. Its22 selectors
resolve23 concrete classes because both packages contain
`TestSonic3kLevelLoading`; the four mandatory S3K selectors total60 cases.
The CNZ/codec selection647 includes the new class14; solid-contact controls3
and the mandatory S3K60 complete the710 cases. Source guard85415 ran in a separate fresh JVM:72/0/0/0,
terminal0 at10:45:06 UTC,60s queue wait and Maven27.426s. The standalone
five-row replay4166 completed5/0/0/0 at10:45:45 UTC, Maven31.584s, resolving
its earlier isolated title-lease risk. Earlier correction iteration93270 was
55/0/0/0; final invocation adds the visited-spot assertion. These focused
selections are not broad smoke, a full suite, strict replay or act certification.
No pass cache/validation receipt or raw diagnostic archive is retained.

The bounded engine-only visual command56421 completed terminal0 at10:46:17 UTC,
Maven18.792s, using the first260 buttons of the maintained Sonic800 stream:

```bash
"${Q[@]}" -Dexec.mainClass=com.openggf.tools.GameplayCaptureTool \
  "-Dexec.args=--game s3k --rom $S3K_ROM --zone cnz --act 1 --x 0x2CC0 --y 0x2A8 --width 800 --main sonic --sidekick none --input $CNZ_CAPTURE/input.txt --frames 260 --every 1 --stills 180,220,250 --no-video --out-dir $CNZ_CAPTURE/current" exec:java -B
```

`CNZ_CAPTURE` is the external `cnz-recovery-gate-spot` task directory in the
session's Codex scratch root. All260 frames rendered; the inspected frame220
has alive P1, LEVEL mode and camera `$3008` below the arena gate, with the boss
absent at its visible placement. Its own production capture boot is not asserted
to align with fixture tick indices. Existing native intro images are retained
as intro evidence only: late-arena native pixels and timing remain open.
The capture warned about the existing Discord presence worker during shutdown;
the command exited and no task-owned JVM remained. No ambient display was used.

One tooling protocol error was separately reported to the lead: a direct
`mvn -v | head -5` version-only probe ran outside activity leases during
10:26:05.341–10:26:05.550 UTC. Its startup script launches a short Java version
helper and the Maven classworlds JVM; exact OS PID binding was not observed.
The lead excluded the overlapping unrelated measurement pair. All subsequent
JVM work used current Main's absolute wrapper and ordinary admission, including
startup helpers. Queue-submit times were never used as execution timestamps.


### Exact original AIZ standing-SST unit — 2026-10-17

GPT-6.1 implementation checkout `t3-98d036a9`, local branch
`bugfix/ai-s3k-aiz-bridge-standing-gpt61`, base8030832d2, qualifies original
fullrun rows9510–9513 at actual SST12, the normal bridge identity and P1 centres.
The [AIZ2 certificate](../validation/levels/s3k-aiz2.md#original-fullrun-normal-bridge-standing-sst--2026-10-17)
contains the commands and exact evidence. Initial prefix77167 has four inner
12/10 standing differences; authorized shorter prefix85012 pins the earlier
signpost22/stub23 instead of native10/11. One runtime owner repair anchors
`S3kBossDefeatSignpostFlow`'s signpost allocation to the retained native control.
Independent short owner73232 is old-red18/20 after two whole-world restore/replay
cycles; corrected detached/in-place/unset products pass with the same complete
comparison. Focused60576 is88/0F/0E/0S, single corrected prefix99559 is1/0F/0E/0S
(twenty assertions), fresh guards83910 are134/0F/0E/0S. Reports were inspected;
queue waits are11s for focused controls and187s for guards, execution is separate.

No allocator/bounds/min-Y owner, trace payload, original driver or Main source
changed. The missing min-Y SST and exact three-worker lifetime/rewind, absent aux
observations, other movies/configurations, full-prefix physics/animation and
whole-chain/act certification remain separate obligations. The2828-class smoke
preview was inspected; Root owns combined validation, composition with HCZ/CNZ,
integration/push and eventual tree cleanup. This worker stops at its qualified
local commit and preserves its checkout/branch for that handoff.
### 2026-10-17 cold Knuckles eyecatch offset 619

One current-engine observation closes the first previously differing Knuckles
eyecatch checkpoint beyond its initial 32 matching pictures. Base `8030832d2`
contains the native sprite-order repair `ac1e1403c`; no gameplay/rendering source
was changed in this slice. The retained ending-pal driver is positively attributed
to authorized Claude Opus 5.5 child ordinal 5: durable positions 828/833 create and
copy it, and 862/889/891/954 transform it into the preserved 11782-byte source,
blob `3c595210b4dda561c1b737d1e2557b44e3d7979e`, SHA256
`1c4916a595d98e7955f12389761a3cd372443af6dc50328222352c8be0863b14`.
Reconstructing those exact command/Edit payloads equals both original and saved
copy; attribution does not rely on a filename or checkpoint alone.

`S3kKnucklesTerminalCaptureTool` promotes its reusable cold-route/event/controller
mechanism into production capture infrastructure. The temporary original stays
unchanged. The standalone tool does not need the test ROM extension, which would
initialize GameServices before method-level preference isolation. It establishes
a fresh private `user.dir` before constructing `GameplayCaptureSession`, restores
it afterwards, refuses existing outputs and verifies the original ROM. Its sole
product inlet remains seven Super Emeralds after ordinary Mecha defeat; it writes
no position, RNG, V-int, image, phase or native comparison state. Unlike the
Sonic/Tails positioned sampler, this follows shipped LRZ → HPZ → SSZ2.

Let `K` be the external task directory `ending-pal-native/knux`. Immutable evidence:
`K/engine-knux-main803-offset619-v1-inputs.json`, its frozen source copy under
`K/engine-source/S3kKnucklesTerminalCaptureTool-main803-offset619.java`, and
`K/engine-knux-main803-offset619-v1/{capture.properties,frames.csv,eyecatch.png,comparison.json}`.
The input manifest contains the exact argv/environment/paths and input hashes.
Normal Main-absolute queue invocation used Java 25, unset DISPLAY/WAYLAND_DISPLAY,
EGL_PLATFORM=surfaceless, ALSOFT_DRIVERS=null and Lua 5.4:

```text
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl -Dtest=TestS3kKnucklesTerminalCaptureTool \
  -Dexec.mainClass=com.openggf.tools.S3kKnucklesTerminalCaptureTool \
  "-Dexec.args=--rom '$S3K_TASK_ROM' --route '$COLD_KNUX_BK2' --native-input '$NATIVE_KNUX_BK2' --native-init 432709 --offset 619 --out-dir '$K/engine-knux-main803-offset619-v1'" \
  test exec:java -B
```

`COLD_KNUX_BK2` is `src/test/resources/routes/s3k/lrz-knuckles-cold-hpz-ssz2-ending-320.bk2`
(SHA256 `6408ae378f849a01615260c5cdec641b01d72a9b1d35dd8df56f18ecfcdf009a`);
`NATIVE_KNUX_BK2` is the retained canonical
`src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/s3k-knuckles-complete-superemeralds.bk2`
(SHA256 `aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc`).
The three CLI/private-scope controls passed 3/0F/0E/0S. The same invocation then
performed the only capture, completed 10:56:56 UTC, Java 25.0.4.1, FAST,
NTSC320/LIMITED238/RAW_RGB. Inlet frame 58252, actual Obj_5EF68 init 71034;
observation 71653 = init+619, phase 6. It consumed 71654 inputs: 71035 cold-route
rows including init, followed by 619 canonical native rows ending at input 433328.
Private config example publication and user.dir restoration were observed; Main
config SHA256 and mtime were identical before/after. Native checkout 33c05c16 and
the original temporary driver were preserved.

Exact RGB comparison used existing `compare_native_rgb.load_rgb`: original
`native-knux-terminal-v1/eyecatch-init-seq032-in433328.ppm` is 348×240, explicit
active crop `(14,8,320,224)`; engine PNG is unmodified 320×224. Result: **0 differing
pixels out of 71680**, no normalization, shift, rescale, mask or search. Native
file SHA256 `c33b24e86ad6648fd9b3a4b169206b083f6f31274fc8642cca219a1cd03b7131`;
engine `a0bfd34a79ab4b07749537b39bfa3da883b2cbb414c64d4d39706e5fd7325e15`.
Tool source SHA256 `27256fb78ae3fe8f4475563d912fd80d17179a068e467323059658e6bc359bd5`;
production-source tree `178db901fc94c16853e9e308e036218cc5dacb815c99d586b1d56d09006d42cd`;
compiled runtime `957706e945dde22535731a2fab1f697ee1b34e1cc894f5f20af312d534cf525e`.
The image was inspected. No runtime defect appeared, and captures stopped after
this pair. This does not qualify the remaining sequence, older terminal-init
pictures with undeclared V-int/HScroll inlets, PAL, PCM, cold-run native parity,
wide products or predictive timing. Root owns integration and broader validation.

### AIZ three level-size workers R2 — 2026-10-17

Separate bounded owner on R1 `da70653308ecf6290b3df020ebed3fd624399789`, same
`t3-98d036a9`/branch. Only `AizMinibossInstance` runtime changes: replace the
remembered-results reservation and direct min-Y snap with all three existing
AfterCurrent workers in ROM order. The [R2 matrix](../validation/levels/s3k-aiz2.md#three-act2-level-size-workers--2026-10-17-r2)
records source pin/labels, hashes, exact commands, scopes and limits.
Old-red12271 is2/2F/0E/0S after two whole-world45-scan replays: actual old
workers21/22 skip first eligible19 and omit min-Y; nontrivial min-Y snaps.
Corrected production scans observe X19/min-Y21/max-Y22 and exact native
accelerating retirement, including min-Y's creation-pass deletion at target.
Both before-handoff and live-worker snapshots force recreation and replay twice.

Focused55530 is70/0F/0E/0S including mandatory S3K4; strengthened recreation93137
is13/0F/0E/0S; the single R2 original9513 prefix36591 retains all20 assertions,
1/0F/0E/0S. Guards87743 are134/0F/0E/0S. Distinct existing BK2 45125 is red76,
first11350 animation13/5; authorized matched exact R1 control75679 has the same
complete76-entry errors array, warnings/bootstrap/groups/missing-aux arrays.
Eight queue and67 animation entries plus trailing26179 rings72/0 remain inherited;
this is no green alternate or title-frontier repair. Queue wait77s belongs only
to45125; execution times are separately recorded in the matrix.

Exact R2 source SHA256`3aa35fede24c479ef72af6ead674f4f1cbf9595837ea600c0874ffb59ec15cd2`
is restored after that baseline control; new test bytes are unchanged. Target
classes are last compiled R1 control, not R2. No further Maven is requested;
Root fresh-compiles composed source and owns combined smoke/integration. The
actual task-tree dry preview selects3040 smoke classes/guards, not executed here.
No shared flow/worker/allocator, title timing, trace/input/config or Main edits.
The three-worker owner obligation is closed within the matrix's component scope;
allocation exhaustion, full native SST/aux/configuration breadth, inherited title
frontier and full chain remain open. Consumed diagnostics/helper are removed;
the local tree/branch remain for Root, without push or integration.


### Lead-owned composed validation boundary — 2026-10-17

The current isolated composition is based on pinned Main8030832d2 and includes
HCZ3896deae8, CNZad1a689cd, AIZda7065330/bfa9d38b0 and the Knuckles tool78472d97a.
Only append-only evidence conflicts were resolved by preserving both sources;
production sources merged without conflict. The Root baseline observation above
is retained, with its incomplete-window limit. A read-only independent review of
the shared drowning/water/CPU/candidate-API delta found no material issue; it did
not execute tests and does not certify carry-boundary or drowning-boundary replay.

The next fresh compilation belongs to Root: the AIZ worker restored exact source
SHA2563aa35fede24c479ef72af6ead674f4f1cbf9595837ea600c0874ffb59ec15cd2 after
its matched parent control, while that worker target still held R1 classes.
Root will run six exact canonical cases (original AIZ comparison and four-row
standing identity, three chain prefixes, full emerald chain) under trace-replay;
four independent segment cases (AIZ alternate, HCZ initial, HCZ full slice and
Poindexter) use trace-segments. Remaining red frontiers are compared by exact
identity, fields and represented window, never totals alone.

The final local change-based runner plan is inspected against8030832d2 before
launch. Shared movement, water and API changes justify the complete smoke
selection plus fresh guards. The anticipated3511 candidates form71 ordinary
batches of at most50; the actual launch plan is authoritative. Expected cost is
roughly90–120 execution minutes plus variable queue waits, not a wall ETA.
Use --max-minutes150, excluding queue wait, with the unchanged10-minute no-output
limit; input changes or an unreported launcher/JVM failure stop the runner.
Reported JUnit failures aggregate across batches. No release-only full suite,
manual selection narrowing or duplicate baseline/development/integrated broad
run is planned. Java25/Lua5.4/PowerShell preflight already passed at10:56:23UTC
in the actual EGL/nullAL/no-ambient launch environment. The original ROM paths
are discovered from Main without copies/links. All validation is pending here;
Root owns observed results, integration/push and accounted cleanup.


## 2026-10-17 producer-owned AIZ deformation instruction work

This independent follow-up reuses `$OWNED_TREE` (existing `t3-fa8da232`),
fast-forwarded under its normal build lease to composition
`ba48017e9280c103f4df8d8baec5a2558d979041` (including CNZ `ad1a689cd`). Only
its own branch was renamed from `feature/ai-s3k-cnz-natural-miniboss-edge` to
`feature/ai-s3k-ending-aiz-deform-work-gpt61`. Separate registry entry
`gpt61_ending_aiz_deform_work` retains that alias, scope and a 24-hour expiry.
There was no owned live request before source writes; the foreign TraceChaser
`4fdf73880f93a3172d23bf9e157b15de3c78fc32` checkout remains untouched.
Root owns integration and combined validation; this is a later composition unit.

**Production boundary.** Ending_BackgroundEvent states 8/$C/$10 already call
`SwScrlEnding.prepareAizBackgroundEvent` before drawing. That logical owner now
records one closed `AIZ1_Deform` instruction term after the existing fixed-point
callee. Its full 150-byte body at `$23B03E..$23B0D4` must match immutable original
ROM SHA-256 `e437770a9eae297587d89a6ba317e97ad6df65d651f11abb4a6dff77c0af157f`.
The original locked-on ROM remains SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`; the original file was never modified or duplicated.
The closed **1094 instruction cycles** come from the existing R16 instruction
hand sum and input-independent full-PC comparison in 28 retained native ending
buckets (`tools/timing/s3k-instruction-ledgers/deform/README.md`). No new native
capture or research was performed, and native PCs/counts/trace rows are never
runtime inputs.

The state-$10 background-Y subtraction is now a separate caller helper, with
unchanged arithmetic. It is outside the authenticated callee term. Same-frame
preparation, output composition, its compatibility fallback and pure rebuilding
publish no duplicate instruction work. The session ROM binding is immutable and
cached; each actual call updates scalar ordinal/frame/qualification without
per-frame allocation. These live scalars are captured with existing ending RAM.
Unsupported/truncated code, including altered operands, refuses qualification
(`-1`, never zero) while the existing gameplay calculation still runs.

**Deliberate limit.** This follows the existing V-int diagnostic-term exposure
pattern; it introduces no generic budget framework or readiness consumer. Caller,
ApplyDeformation, water deformation, other frame callers, interrupt acceptance,
refresh, Z80 bus, VDP FIFO and DMA freeze remain explicitly unpriced. In
particular, `WaterSystem`'s no-water zero is not a source for the raw ROM
`Water_level=$1000` operand. The common 1094-cycle callee cannot explain native
Sonic three versus Tails four services. Static measured head19/fingerprint
`5b4280f6…` stays four services for both products. Profiles, manifests, timing
admission and readiness/service counts are unchanged. The late +2 Sonic/team
171/194-pixel residual, full CPU/window budget and 6+46 acceptance remain open.

**Red, fixture correction and final checks.** All commands below ran from the
owned tree with the current Main absolute wrapper, Java 25, surfaceless EGL,
null OpenAL and no ambient display. Here `OGGF_MAIN` is the absolute original
Main checkout and `OWNED_TREE` the absolute retained task checkout; they are
path placeholders only. The original absolute ROM was supplied:

```bash
cd "$OWNED_TREE"
Q=(env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH
   EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null
   JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
   python3 "$OGGF_MAIN/tools/testing/maven_queue.py"
   --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl
   "-Ds3k.rom.path=$OGGF_MAIN/s3k.gen")
"${Q[@]}" '-Dtest=TestS3kEndingAizDeformWork#actualBackgroundEventPublishesOneClosedCalleeTerm' test -B
"${Q[@]}" '-Dtest=TestS3kEndingAizDeformWork,TestS3kAizDeformInstructionWork,TestSwScrlEnding,TestSwScrlEndingResources' test -B
"${Q[@]}" '-Dtest=TestS3kEndingAizDeformWork,TestS3kEndingInitialBackground,TestS3kVIntInstructionCycles,TestS3kVIntInstructionProduction,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test -B
"${Q[@]}" -Pguards '-Dtest=TestArchitecturalSourceGuard,TestHardwareTimingAuthorityGuard,TestRewindArchitectureGuard,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault' test -B
```

- Old-red session54490: terminal1 at11:19:18 UTC, **1/1F/0E/0S**. After adding
  the captured diagnostic surface but before wiring it, actual background event
  execution expected ordinal1 and observed0. The test took0.244s; initial full
  compilation plus test took75s, queue wait0s. This was an assertion failure.
- Session94617: terminal1 at11:22:08 UTC, **21/3F/0E/0S**, Maven74s, wait0s.
  All three failures were new fixed-point fixture checks: only camera copies had
  been seeded, but ScreenEvents replaces them from the live camera before dispatch.
  Correcting the fixture's live camera source changed no production code. The
  auth3 and existing scroll11 controls already passed and were not repeated.
- Session76285: terminal0 at11:23:33 UTC, **83/0F/0E/0S**, Maven31.032s,
  queue wait23s. This includes corrected owner7, existing ending-background10,
  V-int instruction5/production1, and mandatory S3K60 (the LevelLoading selector
  resolves both 36-case and 7-case classes).
- Session72319: terminal0 at11:24:31 UTC, **112/0F/0E/0S**, Maven32.257s,
  wait0s: source72, hardware authority25, rewind architecture4, trace invariant10,
  hydrate-default1. Final bounded coverage is **97 focused +112 guards**, not a
  broad smoke or full-suite result.

The short owner fixtures declare background state8/12/16; they do not claim a
cold route or another character product. They retain the inherited `$3C` drift
vector and state-$10 caller subtraction, require exactly one term on the next
logical call, and prove draw-only preparation cannot invent a term. An in-memory
operand mutation preserves semantic RAM while refusing qualification; another
control mutates every callee byte independently, including operands and RTS.
Six actual logical frames are captured and fully restored/replayed twice,
comparing every registered world snapshot key and the live work ordinal.

Final executable/test input identity: base `ba48017e` plus the five owned Java
files, concatenated as path/NUL/content in runtime-state, scroll, timing-work,
owner-test, timing-test order, SHA-256
`fa262c8f35c00c20e00669c487a7d6f3b13a606402c65476d337b71c330e9cc9`.
No executable source changed after those affected checks. Consumed XML/text
reports are inspected and removed under `worktree_metadata_slot` alone; no raw
archives or validation receipts are retained. No media, native, full-chain,
broad-suite, integration or push was performed for this unit.
