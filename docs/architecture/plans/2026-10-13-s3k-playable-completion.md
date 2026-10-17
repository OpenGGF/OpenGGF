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


## Current-source SSZ arrival input1342 (2026-10-17)

One fresh engine picture on `78472d97a75a0eea56874d165b73037bb4a2068c`
matches the original native `native-ssz-arrival-allocation-ntsc-v1/input-1342.ppm`:
0/71680 differing original RGB pixels, both320×224, no crop/normalization/
rescale/shift/mask/clock search. Both images were inspected; the retained native
PNG preview was first verified RGB-identical to the original PPM. See the
[bounded matrix observation and method](../validation/levels/s3k-ssz-act1.md#current-source-arrival-input1342-2026-10-17).

The temporary adapter preserves the qualified directSSZ ProcessSprites inlet
(`loc_6468`→`$6484`, LFC0, native VInt0/RNG0 declaration), restores
`S3K_SKIP_INTROS=false` after capture-session construction and before boot,
steps/renders one neutral initial ProcessSprites, then renders each original
AD40 input448920..450262 (1343 inputs) and reads back one image at ordinal1342.
Private `user.dir` precedes all services and is restored. Existing production
`HeadlessGameBoot`/`DisplayColorProfileController.applySavedSettings` applies
requested colour modes; renderer getters confirm LIMITED/RAW_RGB, NTSC,
320×224, FAST, Java25.0.4.1. Main config SHA256 and mtime remain unchanged.

Source/runtime/input identity (SHA256 unless noted):

| Item | Hash |
| --- | --- |
| Production Java source tree | `9bec82f789dd039680e972c65b4f8d1dd3933a1c65db51bd37a3298a66bfd178` |
| Compiled runtime class tree | `2af69e4b17e86ac23e7db9c42b09c6eb5d55da4efdb0b9ca85c262be173898b9` |
| Temporary adapter | `18aa0f87268efade9c3aa240d7a68b948de1625ec27f1e775efd9900d2b08a16` |
| Original S3K ROM (SHA1) | `cfbf98c36c776677290a872547ac47c53d2761d6` |
| Original Sonic+Tails complete-emeralds BK2 | `ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0` |
| Native original PPM | `c4635aa6996b5a0d5b2eb06552e5b17557ed701a69ebd6792d222d90577738e0` |
| Engine PNG | `26c88ee3612b5c0ab4c1cbd5bae1bc8e78a810c20ea277c8cd0564c2374edfb2` |

`$MAIN_TREE` denotes the unchanged Main workspace and `$TASK_EVIDENCE_ROOT`
the external task capture root; exact absolute paths remain in provenance.
Exactly one queued invocation75052 completed terminal0, Maven3.398s,
2026-10-17T11:43:44.578546–11:43:47.653156UTC:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 python3 "$MAIN_TREE/tools/testing/maven_queue.py" --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl -Dexec.executable=python3 '-Dexec.args=target/ssz1342-helper/run.py %classpath' exec:exec -B
```

The normal queued Maven parent in `t3-2f458d35` retained worktree/admission/activity
leases through the Java25 compiler and capture JVM. Durable
`$TASK_EVIDENCE_ROOT/ssz-input1342-main803-v1/`
retains exact compiler/JVM argv, source/runtime hash protocols, adapter source,
state/effective-mode/provenance/comparison data and one engine PNG. Own temporary
adapter/classes/launcher/log were inspected then removed under metadata slot
alone; original Knux driver/native33c are preserved. No runtime/source fix,
new native run, additional capture or tests were made. This is one declared
inlet/moment, not cold-route, inherited-VRAM, whole-scene, audio, timing or
wide/roster qualification; existing gaps remain open.


### AIZ physical retained-control R3 — 2026-10-17 checkpoint

Same t3-98d036a9/branch, native01a1496e-feed-7130-9b66-9f402f964177, base7ed6ba5b8.
Only AizMinibossInstance runtime changes; final SHA256
1878f322bfa03f2c20970c5dd2a371839a4e90481b93c872c332695331027626.
The [R3 matrix](../validation/levels/s3k-aiz2.md#physical-miniboss-handoff-and-alternate-ring-blocker--2026-10-17-r3)
records exact controls59027/58516, old-red38946/59829/33946, source pin, commands,
real clock/cursor mappings and rejected missing-static/invented63 hypotheses.
Native post-routine fatal poll, literal63/64 later entries, global independent
music, physical loc68C02 replacement/fresh119 and existing generic DoStart
replace the auxiliary flow and13/catch-up camera compensation. Independent
subtype10 explosion tail survives replacement and recreation.

75120 passes77/0F0E0S (wait36s/Maven1m15s);56721 passes3/0F0E0S (no wait/59.417s):
original20463 rows zero errors, exact20 standing assertions/SST12, segments0/2/4
complete zero errors. Single distinct alternate33952 is11 ring errors, first13740
rings1/2; prior76 queue/animation frontier is gone but ten new ring spans are
unattributed. Authorized unchanged observation86679 through13742 identifies
engine LostRing SST13 at1BB0/01EE as the extra own-dispatch increment, but native
filtered aux lacks ring positions/routines and no passive actor-increment hook
exists. No LostRing/touch/debris owner is added here. Root owns next evidence and
ring attribution; **alternate UNQUALIFIED and integration blocked**.

Four native loc68E98 debris children remain an inherited occupancy omission.
Fullaux/configuration/roster/BK2/cold/nativepixels/fullchain limits remain. Guards40441 completed134/1F0E0S at12:05:39UTC:133pass, one exact raw-insert
inventory stale10/current9 after removing AIZ inline insertion. The ratchet is
tightened10→9; only that method27275 passes1/0F0E0S at12:07:05UTC (24.683s/no wait),
with no repeated134. No duplicate replay or broad/native run.
R1/R2 results remain historical, not erased. Consumed diagnostics/helpers are
removed under the metadata lease. Target is final1878 plus maintained test/guard
compilation; all jobs terminal/released. Local commit/clean handoff only, no Main edits,
new worktree, integration or push.


## HCZ7134 bounded coupled-pipeline decision — 2026-10-17

Test-only follow-up on3896: one declared floor/wall regression plus no-nudge
control executes ordinary CPU history -> actual sprite scheduler -> underwater
acceleration -> projected wall response -> integration. Focused86494 passes
7/0F/0E/0S. Nudge yields00F6/ground0/Push; control01F6/ground01F6/noPush, both
ending at the same X. Initial56102's2 failures were an incorrect test accessor
stub, corrected without production changes. Generic ordering is disproved for
these conditions;7134 still needs the live engine CPU branch, full pre-wall
position/velocity/path and actual terrain probe/distance. No new traces/native
observations, runtime edit, broad suite, Main write or push. Prior strict
7134sidekick_x/9482air and exact GLFW limits remain. [Math, exact focused
command and causal blocker](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#hcz7134-coupled-cpu-and-ground-wall-decision--2026-10-17).


## Exact published-base alternate control and pending animal owner — 2026-10-17

Root creates an owned pinned8030832d2ab6080e5324cdfe35e971677fd97b41 baseline
with the normal Main exclusive inventory lease, released12:50:54.920041UTC.
The clean checkout uses a private regular config copy, not Main's config link;
tracked sources and fixtures are unchanged. No build tree is copied. One exact
`TestS3kAizZoneSliceTraceReplay#replayMatchesTrace` uses the current Main absolute
queue wrapper from that checkout, `--normal -Dmse=off -Ptrace-replay`, explicit
original S1/S2/S3K ROM properties, single-fork/reuse/alphabetical settings,
`JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`, the same CLI graphics
property, surfaceless EGL, null OpenAL, Lua5.4 and no ambient display.

Session78232 exits1 at12:52:40.260085UTC:1 test/1F/0E/0S, JUnit11.272s,
Maven1m25s;26041 compared observations. Its complete errors array contains only
one physics span,26179 rings72/0; animation/warnings/bootstrap errors and warnings
are zero. Eight advertised auxiliary schemas are absent. Configuration bytes
remain equal to Main, whose observed modification time remains the pre-run
16October09:09:02.849921UTC. This control establishes that R3's ten earlier
13740..16669 ring spans are new relative to the exact pre-task published base.
They remain integration blockers; an older unmatched alternate result is not
used to classify them. No broad baseline run or passing-route claim follows.

The worker's unchanged original-driver low-SST journal84110 completes11994
rows with zero comparisons errors/warnings/bootstrap errors (1/0F/0E/0S,
12:44:57UTC). Its event history traces the earlier physical-pool permutation to
an ordinary subtype0 animal: native retirement9375 follows the previous
Render_Sprites bit7 clear at9374; the engine holds the same animal until9418.
This changes PathSwap, decoration159, level-size-worker and later ring slots.
The prior vertical-admission, monitor-lifetime, static/AutoSpin-cull and
ring-allocator hypotheses were disconfirmed; no slot or ring-phase fit is
justified. Independent source review confirms the native lifetime branch and
S3K render geometry (width8, height_pixels0, prior-bit consumption), preserving
S1/S2 rules and captured prior-render state. A short independent red-first
animal-owner unit is authorized; no animal correction or alternate closure is
yet claimed. Raw observations are consumed after extraction, not archived.

### Ending raw-water boundary checked without a runtime change

Native CheckLevelForWater's dry branch writes raw/mean/target$1000 and clears
Water_flag; the per-frame writer skips a clear flag, and the ending's
Load_Level/LoadSolids path preserves those values. C5 prices AIZ1_Deform, which
does not read water. The separate AIZ1_ApplyDeformWater uses the dry branch at
cameraY$01E0; current SwScrlEnding composes that branch directly rather than
substituting WaterSystem's zero into a raw-register comparison. The absent raw
numeric API is therefore a future-consumer dependency, not an observed current
ending defect. Source: sonic3k.asm CheckLevelForWater/WaterHeight/ending load and
s3.asm AIZ1_Deform/AIZ1_ApplyDeformWater. Caller-window budgets, Hyper/Kos6+46 and
PAL/PCM remain open. No JVM, capture or runtime edit was needed for this check.


## R6 actual HCZ7134 live wall input witness — 2026-10-17

Own tree fast-forwards to Root86171; native4fdf difference preserved. One normal
canonical prefix56073 stops after7135. Execution1/0F/0E/0S is not parity green:
open HCZ comparator217=20physics+197animation, lag53,7136/14208 rows, first
non-camera7134sidekick_x0175/0176. Earlier206 errors already exist by7132.
Actual CPU+1, acceleration026A→0270 and projected0180/0838 agree with native;
wall call leaves0270/ground0270/noPush instead of0170/ground0/Push, producing
0176.F800. BG collision is active; selected BG distance/terrain/transforms and
native layer equivalence remain uncaptured. Actual engine top/lrb0E/0F. Stop at
this wall-boundary handback, without runtime edit or second run. No independent
slice/alternate/guard/native/media/broad claim. Temporary observer/diagnostics
are consumed and removed. [Exact fields, clocks, command and limits](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r6-actual-hcz7134-cpu-and-wall-input-witness--2026-10-17).


## R7 partial selected BG observation;7134 still missing — 2026-10-17

Native aux inventory confirms P1-only0E/0F snapshots; no native P2 layer, raw
BGflag/diff/mask fields. Native wall X05BE plus owning source implies offset0
and diffs0200/0500; it is inference, not a native register sample. One engine
request67242,1/1F/0E/0S, stops after7133 on an incorrect observer assertion:
shared probe also runs floor/left/right tails, so4 calls do not mean4 wall-owner
calls. Qualified projected wall sample7133 selects BG on equality, FG/BG/combined
+18, empty BG terrain, provider0200/0500 and translatedFF7D/0338. Passivity
checks and comparator206=11physics+195animation agree with R6 through7133;
7134/7135 are not executed. No second run, production edit, qualification or
frontier advance. Vertical tail's wrong pooled-result read is excluded. [Exact
source inventory, frozen fingerprint, selected result and failure](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r7-native-inventory-and-partial-selected-bg-witness--2026-10-17).


## R8 corrected lexical wall witness; actual7134 BG+31 — 2026-10-17

Runtime86171, own docs5daf source, actual child ordinal9. The observer now filters
only the real resolveGroundWallCollision lexical call, allowing floor/clamp
scans. Sole corrected prefix44688 reaches7135:1/0F/0E/0S observation success,
7136/14208 HCZ rows,217=20physics+197animation,lag53,bootstrap/warnings0;
first non-camera7134sidekick_x0175/0176 remains. Exact7134 probe0180/0838
translatesFF80/0338 with active/stable0200/0500 provider; actualFG/BG/combined
are+31, selectedBG on equality, empty BG descriptor/collision indices0 and no
flips. One actual wall call/tick plus three legitimate shared-probe tails,
unchanged pooled return/playable/provider and same R6 comparator/poststates.
Native P2 layers/raw BG state/selected tile are absent from the original aux;
source inference is separate and negativeX does not establish a wrapping bug.
No production edit or retry/new qualification. Generic test-only helper retained,
temporary runner/consumed diagnostics removed; all jobs terminal. [Exact command,
fingerprint, all windows and native limit](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r8-lexical-wall-owner-observer-and-actual7134-bg-result--2026-10-17).


## R9 word-layout provenance; cross-load design handback — 2026-10-17

Own tree safely FF32a, native4fdf retained, R9 exactID/ordinal10 registered.
Root's retained native16359 proves BG7134-1 vs engine+31 with equal raw layers/
provider. FF907F94 is Block_table+007F, low byte of current common HCZ E094;
ROM3B91F8's entire0558-byte output matches all four native snapshots. It is not
unknown stale layout RAM. Adjacent FF703E0179 instead belongs to HCZ1's retained
128×128 tail6F80–79FF; HCZ2's shorter overwrite ends6F80. One cold loader/provider
regression32483 errors at missing block224 before lookup assertions (1/0F/1E/0S),
so no qualified old-red. No retry or production/API change. Stop before new
encoded-layout addressing and resident-table/cross-load/rewind contracts; Root
owns implementation authorization. Temporary test/diagnostics consumed. [Exact
writers, command, source checks and bounded design](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r9-native-word-layout-byte-provenance-and-resident-table-boundary--2026-10-17).


## 2026-10-17 producer-owned ending ApplyDeformation work

This independent unit reuses `$OWNED_TREE` (`t3-fa8da232`) and its own
`feature/ai-s3k-ending-aiz-deform-work-gpt61` branch, safely fast-forwarded from
C5 to Root `50a9823c778fef4d3c2af8fed8cbd2c550a01f58` under the intended-tree
build lease. No owned live job existed before writes. Separate ownership entry
`gpt61_ending_apply_deformation_work` records this scope and expiry
2026-10-18T12:57:46UTC. The unrelated TraceChaser checkout remains
`4fdf73880f93a3172d23bf9e157b15de3c78fc32`, unstaged and untouched. Root owns
later composition, integration and combined coverage.

**Production boundary.** Ending_BackgroundEvent entry states0/4 now publish one
ApplyDeformation descriptor at their actual logical event tail: after
`streamRows` (native Draw_TileRow), before palette cycling (`loc_5AFAA`). This is
separate from HScroll-bank preparation, so an already-prepared bank cannot
suppress actual work. The descriptor uses the post-dispatch table `$5B144` or
`$5B15A`, post-preparation background Y and native224 lines. Entry states8/12/16
and delayed redraw20/24 remain outside this producer, including a delayed redraw
that changes the current routine to4. Draw fallback, pure rebuild and repeated
same-frame preparation publish no extra term; the next actual event does.

The helper authenticates every140 code bytes at `$4F0CE..$4F15A`, including
operands and the RTS, against original-body SHA-256
`ea1cce9bf7b73fc63cffc164ebfe10658ec8d25e0a2a78e385c395ce295dafee`.
The immutable original ROM remains SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`. Both band tables are read from the ROM
pipeline. Scalar count/frame/table/Y/lines/cycles/qualification are captured in
existing ending RAM; immutable ROM bindings are cached without per-frame
allocation. Unsupported code or input refuses qualification with an unavailable
`-1` term while ordinary scroll output still runs unchanged.

**Independent evidence and limits.** The existing R16 instruction ledger
(`tools/timing/s3k-instruction-ledgers/deform/deform_cost.py`) supplies63 maintained
source-oracle vectors: negative/zero/positive Y and both sides of every boundary
in the two shipped band tables. An independent one-per-line-band hand sum is
`40+132+112*24+111*10+14+30=4014` cycles. Every supported code byte is mutated
individually in memory; an operand negative also exercises the production seam
and preserves the ordinary output/HScroll bank. The original ROM file is never
modified or copied. R16's retained native evidence for ApplyDeformation covers
entry/exit, not its input-dependent body; these controls do not add native
coverage or consume native PCs/counts/trace values at runtime.

This is a closed instruction term, not an available frame/window budget.
Ending caller, other ApplyDeformation paths, AIZ water/delayed redraw, other
callers, interrupt acceptance, refresh, Z80 bus, VDP FIFO and DMA freeze remain
unpriced. C5's1094 AIZ1_Deform callee is preserved separately. No timing consumer,
manifest, profile, readiness or service count changes. In particular this BG0/4
unit cannot close the BG16 head19 Sonic3/Tails4 difference, Hyper/Kos6+46 or
PAL/PCM. The existing late +2 Sonic/team RGB residuals remain open.

**Commands and observed results.** Commands ran from the owned tree using the
current Main absolute wrapper, Java25, normal admission, surfaceless EGL, null
OpenAL and no ambient display. `OGGF_MAIN`/`OWNED_TREE` below are absolute original
checkout placeholders; all three original ROM paths were supplied and their
SHA-1 identities verified. Sources stayed frozen while each job ran.

```bash
cd "$OWNED_TREE"
Q=(env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH
   EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null
   JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4
   python3 "$OGGF_MAIN/tools/testing/maven_queue.py"
   --normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl
   "-Dsonic1.rom.path=$OGGF_MAIN/s1.gen"
   "-Dsonic2.rom.path=$OGGF_MAIN/s2.gen"
   "-Ds3k.rom.path=$OGGF_MAIN/s3k.gen")
"${Q[@]}" '-Dtest=TestS3kEndingApplyDeformationWork#actualEventPublishesApplyCallEvenWhenTheScrollBankIsAlreadyPrepared' test -B
"${Q[@]}" '-Dtest=TestS3kEndingApplyDeformationWork,TestS3kEndingApplyDeformationCycles,TestS3kEndingAizDeformWork,TestSwScrlEnding,TestSwScrlEndingResources' test -B
"${Q[@]}" '-Dtest=TestS3kEndingApplyDeformationWork,TestS3kEndingInitialBackground,TestS3kEndingSceneState,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' test -B
"${Q[@]}" -Pguards '-Dtest=TestArchitecturalSourceGuard,TestHardwareTimingAuthorityGuard,TestRewindArchitectureGuard,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault,TestZoneEventRewindSchemaGuard,TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard' test -B
```

- Old-red64353: terminal1 at13:01:18UTC, **2/2F/0E/0S**, Maven75s, wait0s.
  Before the production hook, both actual event states expected count1 and
  observed0. These were assertion failures, independent of a long ending route.
- First focused67382: terminal1 at13:05:16UTC, **33/0F/2E/0S**, Maven74s,
  wait0s. All21 cost/auth/C5/scroll cases and10 owner cases passed, including
  both twice-recreated worlds. Only the two final full-tick steps errored:
  manually advancing the fixture Level_frame_counter conflicted with initial
  Process_Sprites epoch `expected1/0/1, received1/1/1`. Invalidating the prepared
  bank cache instead fixes the fixture without advancing production clocks;
  no runtime source changed. This was a fixture error, not a gameplay failure.
- Final focused14976: terminal0 at13:06:56UTC, **87/0F/0E/0S**, Maven29.078s,
  wait0s: corrected owner12, initial background10, scene state5 and mandatory
  S3K60 (the LevelLoading selector resolves both36-case and7-case classes).
- Guards5753: terminal0 at13:08:42UTC, **132/0F/0E/0S**, Maven32.515s, wait0s:
  source72, authority25, rewind architecture4, trace invariant10,
  hydrate-default1, zone schema18, world coverage1 and helper coverage1.

Final bounded coverage is **108 focused +132 guards**, reusing the21 unchanged
passing focused cases without repeating them. Both declared BG0/4 fixtures
force object recreation, compare every registered world snapshot key immediately
after restore, and repeat the same six-frame forward state twice, including the
live descriptor. They are phase controls, not cold routes or character breadth.
Independent read-only source review found no material issue; it did not execute
the tests or establish additional runtime coverage.

Final executable/test identity is base `50a9823c` plus path/NUL/content of the
six owned inputs in this order: Sonic3kEndingEvents, EndingZoneRuntimeState,
S3kEndingApplyDeformationWork, TestS3kEndingApplyDeformationWork,
TestS3kEndingApplyDeformationCycles and
`src/test/resources/timing/s3k-ending-apply-deformation-r16-v1.txt`.
SHA-256: `207f0b23cb192d7b0c7b3657329e8db9e09b0fdba4ad26037ab4ae68e91653fd`.
No executable source changed after qualification. Consumed XML/text diagnostics
are inspected and removed under `worktree_metadata_slot` alone, with no raw
archive or validation receipt. No trace frontier moved; no native/media capture,
broad/full suite, integration or push was performed for this unit.


### AIZ ring birth R4 causal checkpoint — 2026-10-17

Same owned tree, safe FF5da817090, runtime1878 unchanged; R3 preserved. Exact R4
task/24h expiry/native identity are registered separately. One unchanged prefix69634
through13742 reproduces first13740 rings1/2,1/1F0E0S,12:17:26UTC,1m11s/no wait.
Engine uses actual firstFree12; native uses11. Birth-ring initial coordinates/count
agree, but the positive00C4 ring isengine13/native12. Earlier placement permutation,
not skipping a free reservation, is now the boundary. Exact source identities/ROM
records256/258/265 and native appearance/retirement events are in the
[R4 matrix](../validation/levels/s3k-aiz2.md#alternate-ring-birth-and-earlier-placement-admission--2026-10-17-r4).
Root authorizes one next unchanged prefix ending13026 for those three placement
events and real camera/deferred/cursor state. No production change, phase/slot fix
or new native run. The ten new alternate ring spans remain integration blockers.


R4 exact-placement continuation11174 ends13026,1/0F0E0S,12:27:37UTC,
Maven32.766s/no wait. All256/258/265 birth/retire/reload rows match native;258
is correctly deferred until13025 and the real cameras/Y coarse agree. The
three-owner vertical-gate/monitor-timing hypothesis is rejected. Initial30691
was an observer null-spawn error after3278 rows, not an engine failure; fixed
diagnostic only, no behavioral credit. Before first256 admission, engine5 is
already rock240 (native6); this shifts the later free-hole path. Existing native
full dumps and actual appearance/retirement identify earlier208/209 and rock240/
bridge243 as the next bounded admission question. No production edit or further
Maven is authorized without a source-owned proposal. Native/engine phases and
all known alternate blockers remain as recorded in the R4 matrix.


R4 predecessor continuation81466 ends12555 on5da/runtime1878 unchanged,
12:35:40UTC,1/0F0E0S,Maven32.568s/no wait,12527 compared rows/zero errors,
warnings/bootstrap. All208/209 births12217 and retirements12554 match native;
209 is AutoSpin(id26/sub80), not spikes. The completed12553 live-camera range
query is already out-of-range, while that pass used prior18FD; the next pass's
1901 correctly unloads both12554. Thus this is not the proposed culling repair.
Engine X worker already occupies5 at12216; native corresponding84A48 is21
(born12001,retired12335), so this is an earlier allocation permutation, not an
extra worker/lifetime finding. Rock/bridge12510 then use actual engine holes
5/7/9/10 versus native6/8/9/17. Native worker's birth pool5–20 is occupied;
slot5 is exact source159 static1037/0322,id30/sub0,ROM1E4C30,first9509/retire12018.
The engine source159/pool at worker birth12001 was not emitted by this bounded
four-target observation. No source-backed independent old-red/runtime fix is
justified until that earlier boundary is known. Detailed clocks/cameras/pools,
source labels/pin and rejected hypotheses are in the existing R4 matrix.
Observer source restored exactly HEAD; target retains its last temporary
compilation.81466 terminal/released, no other workload. Prior11174 four reports
consumed;81466 inspected outputs consumed normally after facts recorded.
No production change, native, full alternate, canonical, guards or broad run;
the alternate's ten new ring spans remain integration blockers. Root owns the
next evidence/owner scope. R1/R2/R3 results and task entries remain preserved.


### R5 animal lifetime implementation checkpoint — 2026-10-17

R4 Plan/matrix only committed separately e207faf7d with syntax/link/diff checks,
seven parsed policy trailers/final GPT6.1 coauthor/hooks; clean before R5.
R5 exact task from gpt61_aiz_ring_pool_r5 registry, native01a1496e-feed-7130-9b66-9f402f964177,
same tree/branch/base5da/runtime1878;24h expiry refreshed. Single passive84110
through12019:1/0F0E0S,12:44:57UTC,Maven45.503s/no wait,11994rows/zeroerrors,
warnings/bootstrap.362 low-SST ownership/state events,2,423,252bytes under10MB.
159's actual first9077/retire9230/reload9509/retire12018 all match native rows;
its engine8/native5 permutation predates DoStart. Flow4/nativeControlSlot4 at12000
correctly consumes actual free5 for X and22 for maxY at12001. No allocator fix.
The recorded earlier animal5 birth9279, matching pop/floor9323, native deletion9375
versus engine9418, native PathSwap5 reuse9378, and decor152/159 re-admissions pin
an actual wrong lifetime. Native loc_2CAD0 polls prior Render_Sprites bit7;
engine S3K selects fresh onScreen(64). Native width8/height_pixels0 must be
preserved separately from y_radiusC/S2approx32. Full evidence/source pin and
phase limits in existing R5 matrix. Observer removed; inspected journal/reports
consumed, no archive. Authorized next: one independent old-red natural animal
birth/flight edge/reuse plus twice forced world recreation, smallest lifetime
repair only, affected animal/S2/RNG/rewind/S3K4 then alternate and exactstanding/
original/twoSS each once. Stop on unexpected divergence; no more ancestor/native/
fullchain/broad. Root arranges independent review/composition; Main remains803.


Independent old-red15578 on unchanged runtime1878: terminal1 at12:55:08UTC,
Maven24.170s/no wait,2tests/2F0E0S. Natural flight reaches width8 left edge,
but cached bit stays true, next-pass retirement and actual free-hole reuse fail;
geometry has eight expected failures covering outside/height0/wrap and MAIN
pre-motion order. Two complete forced-recreation/restores from birth and live
outside-edge snapshots replayed successfully even on the old owner. No seeded
routine/timer/slot/RNG. Runtime repair is limited to AnimalObjectInstance and
existing GameRules.SONIC_3K animal lifetime flag: existing camera semantic
useScreenYWrapValueForVisibility selects copied signed16X and zero-height
word-maskedY; S1/S2 rules/geometry remain untouched. MAIN polls before motion;
walk/fly retain their after-work poll. Captured init/romRenderOnScreen state is
not recomputed during restore. Physics/RNG/points/allocator remain unchanged.
Focused tests and bounded affected traces pending; no green integration claim.


Post-fix focused27140 terminal0 at12:57:40UTC,Maven1m11s/no queue wait:
73/0F0E0S across ten actual classes (the selector matches two LevelLoading
packages). New animal2, AnimalRNG3, S2animal1, DestructionEffects6,
EggPrisonAnimalRewindRendering1 plus mandatory S3K60 all pass. Naturaledge
pass92,X0D77/dx-9 now cachedfalse, next-pass retirementtrue, normal hole4 reused;
birth and live-edge whole-world recreation/replay twice passes. Independent
exact-source review reports no material finding, without claiming execution.
Animal source73f1c45485ca81a7ef055d58c0e26abda1664089456eba4f4f4829dff734b5ed,
GameRules7b694eba04238262861a76cc6977f85eb93a8709f30464c1eb891fe4fecaeb1c.
Root exact matched803 alternate78232 retains only26179rings72/0; the ten early
R3 spans are confirmed new, not waivable. One corrected alternate next; exact
standing/original/twoSS remain pending subject to first unexpected divergence.
All27140 reports inspected/consumed. No additional runtime edits or broad run.


R5 final bounded qualification: corrected alternate39799 terminal1 at12:59:18UTC,
Maven36.231s/no wait,1/1F0E0S,26041rows. All11rings field/span/expected/actual
identities match R3,first13740rings1/2,0animation/warnings/bootstrap;tenearlyspans
remainNEWvsRoot80378232(one26179rings72/0). Baseline profiletrace-replay differs
fromworkertrace-segments onlyinselection;exactcase/runtimefork/envverified,
no literal sameprofile claim or rerun. Oneanimalrepairalone doesNOTclose rings.
Three maintained controls6148 terminal0 at13:01:20UTC,Maven1m01/no wait,
3/0F0E0S: original20463rowszeroerrors/warnings/bootstrap;all20standingassertions
9510..13actual12,parent12/display17;twoSSprefix0/2/4complete/zeroerrors,
five dynamicartgapszero failures. No new/worsened field vsstartingR3.
Animal73focused/source-review and3controls qualifyonlythiscorrectnativeowner;
alternate UNQUALIFIED blocks integration. No extraowner/prefix/alternate/native/
media/guards/broad. Allinspecteddiagnosticsconsumedmetadataalone,immutableinputs
preserved;targetfinalanimalsource+maintainedtests from6148,alljobsterminal/released.
Final localcommit includesexistingmatrix/backlog/frontier/known/changelog and
mirroredpitfall,sevenpolicytrailers/finalparsedGPT6.1coauthor/hooks;R4e207separate.
Root ownsremainingringcausality/composition/combinedsmoke/sourceguards;Main803
unchanged. Fullaux/config/BK2/cold/wide/roster/fullchain/nonzeroanimal/ending and
four-debris omissions remain explicit limits, with no integration clearance.


R5 local commit attempt is blocked by the normal pre-commit policy hook:
"detectable @ModApi surface changes require updating the current candidate
signature pin." The sole GameRules change is an existing SONIC_3K static
preset argument false to true; no declaration/constructor/record component
changed. The current shell surface detector keeps record preset initializer
text and therefore reports a signature delta that the normalized API snapshot
cannot express. No signature pin fabrication, hook edit or bypass. Eleven
owned files remain staged on R4e207; all tested hashes/results and consumed
diagnostic state are preserved. Root has the exact policy-owner handoff;
no extra runtime/source variant or Maven run is justified by this blocker.


### R6 actual post-animal route witness — 2026-10-17

Exact task gpt61_aiz_post_animal_pool_r6, same native01a1496e/tree98d/branch,
24h expiry registered, R5 entry preserved. Root explicitly allowed the one
original-driver13742 diagnostic on frozen e207+11stagedR5files while its separate
policy owner fixes the normal commit hook. Not a committed-R5 execution.
Animal73f1c45485…b5ed/GameRules7b694eba…b1c/Aiz1878 unchanged. One9577,
trace-segments exact #observePostAnimalLivePoolReadOnly, normal current Main
absolute wrapper/all3ROM/EGL/nullAL/noambient/JTOsingle/singlefork/reuse/alpha.
Terminal1 at13:14:15UTC,Maven41.509s/no wait,1/1F0E0S;13713comparedrows,
known13740..13742rings1/2 only,0animation/warnings/bootstrap,eightmissingaux.
223events/5,805,290bytes<10MB. Temporary observer source restored exactly and
all11R5 staged path/blob identities unchanged. Temporary9577 observer class
removed with its inspected diagnostics, no maintained-build claim. No additional JVM/native/media/variant.

Actual animal rules are bothtrue;9373D80/2EC dx-8 rendertrue,9374D7D/2EC dx-9
renderfalse,9375destroyed/free5. PathSwap1235 reuses9378→retire9463;15210/15332
at9463,1595/1608 at9509,159retires12018 now match existing native facts. Thus
isolated lifetime tests do reflect a real route correction despite unchanged11rings.
The first remaining source-proven branch: engine destroys normal body10/arm15/
barrels21..23 at fatal10837 via onDefeatStarted250..254; native keeps allfive
through10900, installs Delete_Current_Sprite at10901 parent$38bit4, frees10902.
Natural component births9768 match in both; no missing initial child allocation.
Global SongFade therefore engine10/native34 at10901; sign15/stub21 versusnative
sign10/stub15 at11021; X10/maxY23 versusnativeX21/maxY23 at12001. X10 then
displaces layout220 to11 at12235, bridge display10/native17 at12510, monitor265
engine11/native10 at12967, firsthurt-ring12/native11 andpositive00C4 engine13/
phase96 versusnative12/countdown97 at13568. No allocator/phase defect or fix.

Existing matrix records exact sourcepin044fa467/labels, ordered pools, separate
realObjectVInt/LF/executed/BK2 clocks (13740VInt3934/LF358F/executed13711/
nextcursor14682), nativeTouch_Process phase distinction and parentbit4 source
versus absent directaux writer. Four loc68E98 debris remain outside this unit.
Next proposal only: one independent red-first production normal-child fatal→64wait
→physicalreplacement/bit4→next-own-pass retirement/reuse, with twice-recreated
whole-world replay and independentmusic; use existing local native-pending paths
and preserve physical-owner/recreation linkage. No new runtimeowner or API edit
is authorized by this observation. Root has the causal handback; journal/reports
are consumed metadataalone after facts recorded. Alternate remainsUNQUALIFIED,
tennewvs803 block integration; R5 historical73+3 retain their exact limits.


R5 normal local commit completed after exact tested policy adoption: prerequisite
b834344890603c1f3919c302be245ffadd863994 duplicates only Root ff9e4be617c3d16ab3bb1f2c23ad0893c10694e5's
three policy files; --only preserved all11staged R5 path/blobs and R6 doc bytes.
R5 is3cce122ffcdb5e0c5371ed32f7a7158163b544c8, original11files with unchanged
tested animal/rules hashes, seven parsed policy trailers/final exactGPT6.1coauthor
and normal hooks. Root39policy cases/shell+PS syntax/review reused, no extraJava.
R6 seven inspected diagnostic/class files consumed metadataalone, no archives,
no live handles. Only existing R6 Plan/matrix evidence remains for its separate
normal docs commit; R4e207 and R5 remain separate, Main803 untouched.


### Whole-objective checkpoint — 2026-10-17 13:45 UTC

Read-only completion audit pinned Root `28fd18de8212f8a844763d8399c9bcd000684a79`
and published Main `8030832d2ab6080e5324cdfe35e971677fd97b41`. Root is an
unpublished composition; its target was last built at the older `7ed6ba5b8`
epoch. No delivery-wide current-source pass or completion is inferred.

| Obligation | Observed scope and next owner/check |
|---|---|
| Strict AIZ/HCZ | AIZ R7 owns premature five-component retirement and the ten new alternate ring spans; HCZ R10 owns encoded-word addressing and retained destination writes. Their focused results do not replace final strict replays. |
| Ending caller/window/Hyper | C5 and ApplyDeformation are qualified callee producers only. Production still assigns four head services to Sonic and Tails where native Sonic uses three. Caller and hardware inputs, six measured counts and 46 contexts remain unowned acceptance work; do not close with another isolated callee. |
| Ending products/terminal | Nine NTSC products retain 249/253 equal RGB pairs and four Hyper differences. Tails/+2 alone reaches title in that sampler. Full-table dispatch checks are declared pressure, not native full-capacity comparison; remaining terminals need their own fixed products. |
| Ending PAL/PCM | One early PAL Sonic/+2 product diverges from LF3778 on Kos timing; other PAL products and matched rendered PCM remain open. Preserve request-ledger versus physical-audio distinctions. |
| Bonus presentation | Preserved branch candidates `d56216d5e` and `7380f7b1f` are absent from Root. Historical XML has no current source fingerprint; four unknown shield edits in their tree are untouched. Do not adopt the whole candidate. |
| Pachinko mechanisms | New independent GPT-6.1 popup owner task starts from Root28: actual-contact missing Obj_EnemyScore allocation, allocation-failure score preservation, recreation and relevant existing traces. The draft's unconditional ten-hit cap conflicts with native respawn-address gating and is excluded. Broader bonus RGB, reward collection/expiry, spring/flipper/magnet/platform and absent auxiliary observations remain separate limits. |
| Title/tiles/credits | Delivered publication and credits repairs remain delivered. The recorded title36/35 residual needs an actual child-drain/SST-deletion/art-admission witness; the existing short hold test ends at counter34. Native pictures of the precise AIZ2-tree/HCZ upload moments remain absent. |
| SSZ/cold breadth | Solo Sonic cold campaign `030731207` supersedes the stale LRZ-boss gap. Pair/Tails/Knuckles continuations and one SSZ arrival1342 RGB pair plus one cold-derived Knuckles619 pair are scoped controls, not whole-terminal/wide/donor/PAL certificates. |
| ICZ | Three physical post1 candidates never died. Semantic pit-death/reload checks do not prove natural lethal contact, actual saved-post respawn, retired history or twice-forward replay across those boundaries. No production defect follows from failed route authorship. |
| MHZ | Identity/rider/shield and wide25-window work supersedes the stale generic identity-gap label. The distinct Cluckoid explicit-removal arrow defect still needs offscreen/death/removal/recreation controls preserving detached leaves. |
| Entry/save/giant-ring | 140 held-Right entry floors per eight acts are entry products, not mechanic routes. Saved-slot HPZ covers Knuckles/Tails/team; solo Sonic and donor/wide products, full giant-ring timelines and their load/history boundaries remain explicit missing products. |
| SAT/colour breadth | Full255/Limited238 and sampled SAT priority are delivered. Scanline capacity, transparent-plane and arbitrary-product native breadth remain unqualified; no reopening of delivered colour settings is required. |

Audit corrections are evidence corrections, not newly run checks. All other
checked recorded qualified engine commits were accounted for in Root28;
HCZ `9f0f429d1` is evidence documentation in the active R10 lineage.

At this checkpoint AIZ's independent exact-hash review found two newly
reachable native mismatches in the surviving wait: the barrel suppressed its
own routine on defeat, and the body froze animation. The worker is correcting
those within the authorized physical-parent unit before long replays; the
initial 207 focused passes do not certify those paths. HCZ's genuine transition
old-red and first repaired owner checks pass; mutation/rewind/scene and strict
7135 qualification remain pending. No repeated broad smoke, full suite, package
or Main publication has been submitted. Final source-frozen canonical/segment
checks and one complete local change-based smoke/guard run remain Root-owned.


### Matched R10 test-prerequisite attribution — 2026-10-17

Root `0684ba5f2adc5d81e233854f84d5967d5a619386` (runtime identical to Root28,
without R10) ran one exact baseline through the current Main wrapper:
`--normal -Dmse=off -Dopenggf.headless.gl=surfaceless-egl` plus all three
original absolute ROM properties and
`-Dtest=TestZoneLayoutMutationPipeline#levelFrameStepFlushesQueuedMutationsAfterCameraScrollAndEventsBeforeBoundary test -B`.
JAVA_TOOL_OPTIONS retained the single required EGL property; EGL/nullAL/no
ambient display/Lua5.4 environment matched the worker. Session61070 terminal1
at **13:47:37 UTC**, Maven1m12s, observed admission wait2s, total parent wall
74.543s: **1 test / 0 failures / 1 error / 0 skips**. Actual XML inspection
against R10's retained XML gives exact test-name, exception-type, message and
first four stack-line equality. Both fail because the mock GameModule's
`getRules()` is null at `GameplaySessionFactory.attachManagers:60` before
`paletteWhiteFade()`. This attributes R10 focused4439's sole error to its
unchanged prerequisite; it is not a pass or a new layout failure.

R10 guards45410's two MenuAudioProbe singleton assertions retain the actual
Main707 session6602 / Root94450 / follow-up55438 identities recorded above.
The guard and MenuAudioProbeTool files are byte-unchanged from707 through this
Root; no new singleton debt or blanket guard waiver is introduced. All other
R10 failures remain subject to their own comparison. No duplicate baseline,
mock repair or broad suite was submitted. The two inspected baseline reports
and owned invocation log were consumed under the metadata lease alone.


### Ending caller closure boundary — 2026-10-17

Read-only source/dependency research on Root `2d8008148` establishes that the
remaining budget is a coupled instruction/bus timeline, not another callee
subtotal or `framePeriod - subtotal` passed to Kos. Refresh is tested at each
opcode fetch; Z80/DMA/FIFO stalls advance the same master clock, and interrupt
acceptance depends on `(MCLK/7)%10`. Current VInt projection explicitly refuses
the busy-Kos input needed for the deciding reunion window. Missing inputs must
remain unavailable; VInt counters and scene-entry clock assignments do not
supply legitimate master-clock phase.

The coherent internal ownership boundary is the physical head at PRE_MAIN_LOOP,
through the existing runtime-art boundary/preparation service. It requires:

- Captured CPU/bus phase, SR/IRQ/continuation and refresh/DMA/Z80/FIFO/HInt state;
  an instruction-boundary clock that advances the existing pure Kos model.
- Actual ordered caller/object/player/tail/render/HUD/rings/palette/plane work
  from their production owners, with authenticated operands and branch inputs.
  GPU commands, occupied-slot totals and native oracle counts are insufficient.
- Complete VInt callees and DMA submission/trigger order, including object-owned
  player DPLC intervals, multiple loop visits and VBLANK straddles.
- Coupled resumable physical-head preparation and semantic readiness admission.
  DirectPreparation currently decodes on first PRE; a separate four-service
  profile still gates publication, so changing only one connection cannot fix
  native Sonic3 versus Tails4. Do not rewrite the immutable manifest to fit it.

Existing C5/Apply, VInt/DMA subtotals and retained source models are prerequisites,
not evidence that these production inputs exist. This unit spans caller/render/
palette, hardware/audio-bus and queue/admission owners. Its acceptance needs
hand-derived phase/stall/bookmark checks, twice-forward captured-timeline replay,
then independent byte/stop-PC/retirement predictions for the six original ending
counts (NTSC Tails+2=4, Sonic+2=3, team+2=3, Sonic0=4, Sonic-2=4; PAL Tails+2=3)
and the eligible 46-context corpus before activation and Hyper/PAL/PCM checks.

Retained B is `s3k-parity-20261014/ending-native-reference-prerequisites` under
task scratch; D is this task's `s3k-parity-coverage-20261014` scratch root. B's
`native-kos-budget-v7-job-cycles.txt` and v6/v7p/v7c captures anchor the counts;
D's `kos46-research-20261016/context-inventory-next/validation-inputs.draft-v1.json`
(SHA256 `7da2b0859cf20c3f498d74a5a722128a77fedbd09cb1087030b1cce1fccbd249`)
anchors the 46 identities and regeneration commands. Actual retained schema2
corpora now contain 399 samples across all46: 334 classified/65 unclassified,
with classified coverage only45/46. The sole wholly unclassified identity is
`fdf98ef424cd800a5a3729e70bfbc4d186580cd83e9e26df0b347fe156e7a8fb`
(ArtKosM_SStageChaosEmerald module0, source0AE3EE toFFFFD000). Resolve its
comparator explicitly; do not normalize these observations into the old327
aggregates. The committed63 supplied-budget consumer windows and native closure
checker recordings do not predict the six counts or qualify readiness.


### R7 normal AIZ five-child retirement owner — 2026-10-17

Exact gpt61_aiz_child_retirement_r7 task/native/workspace/24h expiry registered;
base0ee/R5/R6 preserved. Independent production-birth old-red42418 terminal1
13:29:52UTC,Maven24.901s/queue39s,1/1F0E0S. Natural callback creates actual
fivechildren in28..32 after physicalcontrol17; fatalPass183,retainedXY11F0/028A.
Four native-lifetime assertions fail (fatal,mid64wait,wordzero,pendingpass);
whole-world forced recreation from beforefatal/livewait/pending/deleted each
replays twice without mismatch. Fifth reuse assertion exposed an unrelated
pre-existing lower free5, so the maintained control must fill actual ordinary
free holes until it reaches a retired child hole, without assigned slots.
That test assumption is rejected; no runtime slot fit or second old-red run.
Final-hit HP1 is declared component stimulus; no timer/routine/slot/RNG/trace
state seeding. All real CreateChild1_AfterCurrent children lie above control;
no impossible lower-slot variants.

Root authorized local physical-SST child migration and minimal captured known-field
shared flow projection after exact contract handback. No generic boss dispatch or
allocator changes. Source044fa467:body136974/arm136993/barrel137461 each finishes
Child_Draw*Sprite2; parent$38bit4 (178173..178197) installs Delete_Current via
Go_Delete_Sprite_2 (179232..179235), then deletion on next own entry. Existing
Java AbstractBossChild finalbosslink/clockgate/inlineparentlist cannot survive
physicalparentreplacement; new AIZ-local scalar parentSlot/ordinarySST recreation
will replace that dependency. Flow's retained x/y/renderflags/$38/defeated fields
are package-private projection with captured scalars, not fabricated full native
header zeros. Runtime edits have not yet begun;42418 terminal/released.


R7 initial post-fix9555 terminal1 at13:37:55UTC,Maven1m04/no wait, no tests
executed: migrated seamless unit used StubObjectServices instead of the existing
fluent TestObjectServices. Corrected that owned test-only compile prerequisite.
Production8files compiled successfully; their exact hashes remain frozen in R7
registry. No runtime failure/trace result is inferred from this invocation.

R7 corrected prerequisite32436 terminal0 at13:39:53UTC,Maven24.866s/queue67s,
14/0F0E0S across normal child retirement2,physical handoff4,graph recreation4,
shared flow4. Natural children28..32/control17 survive fatal and literalwait;
bit4 installs pending and the next own pass frees them; realhole28 reused after
ordinary lower holes are filled. Beforefatal/livewait/pending/deleted snapshots
each force recreation twice and replay; independent known-header pose/flag/offset
recreation passes. No generic boss/allocator/public constructor change.

R7 remaining affected80769 terminal0 at13:41:57UTC,Maven31.229s/no wait,
193/0F0E0S across22selectors (23actual classes: both LevelLoading packages).
Actual cutscene/shot/flare/falling-shot/napalm/explosion/seamless callers and
three-worker controls pass, mandatory S3K4 included (8+36+7+6+3 tests).
Commands use normal current Main absolute queue wrapper, MSEoff, explicit EGL,
all3originalabsoluteROMs,singlefork/reuse/alpha, nullAL/noambient as R6.
Runtime8files unchanged since9555 compilation; stable for independent review.

R7 review on exact8runtime hashes found two material masked branches: barrel
defeated→IDLE suppresses own routine/pair birth through64wait and finalbit4pass;
body defeated→frame2 freezes Animate_Raw before status7 collision clear. Root
authorized local known-header bit1 write and FallingShot scalar parent status
read if necessary; no generic boss/API/allocator widening. Long traces held
unsubmitted until corrected short controls. Existing70775 preserved terminal1
13:44:05UTC,Maven1m37/no wait,118/1F0E0S:117green, terminology guard found
three owned dummy update argument names (R3count/R5frame/R7count). Renamed
only those arguments to vIntRunCount, no gameplay changes; rerun only this
affected guard after correction. Independent active-barrel old-red prepared
from natural SWING_PREP_COMPLETE wordzero, no phase/timer/slot seeding.

Active-sequence20704 terminal1 at13:46:31UTC,Maven28.140s/queue20s,1/1F0E0S.
Body freeze red is meaningful; barrel pair assertions were NOT qualified because
the declared default Sonic inlet does not activate native Knuckles-only barrels.
Corrected only the character/camera/boss-position component inlet to Knuckles,
with an explicit resolved-character assertion; no runtime/timer/routine edits.
One corrected short old-red is necessary before the source-backed branch edit.

9329 terminal1 at13:47:59UTC,Maven21.360s/queue37s,no tests: corrected
character assertion used the wrong S3kRuntimeStates package. Fixed that test
qualification to existing sonic3k.runtime; no runtime change or old-red claim.

Corrected Knuckles active old-red22347 terminal1 at13:48:35UTC,24.672s/no wait,
1/1F0E0S. Resolved Knuckles/control11/fatal379, four source assertions red: body
Animate_Raw continuation, delayed thirdbarrel FIRING, finalflare/FallingShot pair.
All three boundary whole-world forced-recreation cycles replay twice. Runtime
correction removes only defeated→IDLE and bodyfreeze; ownroutine precedes bit4
tail even on replacement. Package-private retained38 bclr1 supports that final
work; FallingShot grandparent status now reads captured physicalslot via known
header projection. Removes its obsolete Java parent field/one explicit captured
policy entry, preserves live barrel ObjectRef and standalone constructors.
No new generic dispatch/interface/allocator or public API. Fatal generic attack
cleanup is unchanged; no separate projectile algorithm/physics/RNG changes.

Finding-resolution independent read-only review matched all10 actual runtime
hashes to fresh registry; both material findings resolved, no remaining material
objection in bounded unit. No execution/pass inferred.91691 terminal1 at
13:51:44UTC,Maven1m01/no wait,no tests: one migrated NapalmRoute assertion used
checked reflection helper inside an unchecked method. Switched that assertion
to its existing typed unchecked helper, no runtime bytes changed. Rerun only
the same pending10class affected short selection before long traces.

Final reviewed-runtime short34345 terminal0 at13:53:23UTC,Maven28.184s/queue27s,
65/0F0E0S across10affectedclasses. Natural active Knuckles fatal379/control11
now creates exactly one final-pass FallingShot plus flare; INIT runs before the
retained status7 pending marker, next ownentry clears. Body animation continues.
Original fivechild/freehole and retainedheader XY/flags/bclr1/offset controls pass;
all captured whole-world boundaries recreate/replay twice. Earlier207 and117
guard passes retain their tested epochs; only the affected exact terminology
and changed-policy-entry guards are repeated. All pending long traces remain
once each, unchanged driver/bootstrap/input/lag; no further runtime edit.

R7 final exact guards31137 terminal0 at13:55:16UTC,Maven1m19/queue7s,2/0F0E0S.
Original/standing/twoSS99318 terminal0 at13:56:45UTC,59.964s/no wait,3/0F0E0S:
original20463zero,all20standing actual12 at9510..13;seg0/2/4completezero,
lag0/11/14,5dynamicartgaps/0failures. Once alternate72859 terminal1 at13:58:19UTC,
37.343s/queue29s,1/1F0E0S:26041rows,ONLY26179rings72/0 (single-row,
noncascading physics),animation/warnings/bootstrap0. Exact array matches the
remaining Root80378232 identity/values; all ten new13740..16669 ring spans close.
Raw alternate remains red for that inherited trailing field. No further runtime,
probe/native/media/ancestor/broad/fullchain run. Existing matrix records exact
commands/source hashes/epochs/limits; all ten runtime hashes matched independent
finding-resolution review. R5 animal73f1/GameRules7b694 remain unchanged.
Only final docs/staging/normal local commit and inspected report cleanup remain;
Root owns composition/combined validation, Main803 untouched. All JVMs terminal.

R7 inspected46 latest Surefire identities across their recorded epochs (330cases,
sole alternate trailing-field failure,0errors/0skips), all six trace diagnostic
arrays/owners and the23-line rewind disposition inventory. Consumed105 owned
files (92 XML/TXT,12traceJSONinclowners,1rewinddiagnostic) under metadata slot
alone, no archives/receipts. All handles/leases terminal/released. Latesttarget
is72859 final maintained runtime/tests compilation, no temporaryobserver/variant.
Syntax/diff checks, affected local link targets and skillmirror equality pass.
Preparing normal local owner commit, not integration or publication clearance.


### Pachinko popup producer handback — 2026-10-17

One bounded GPT-6.1 implementation in owned `t3-649fee32`, branch
`feature/ai-s3k-pachinko-score-popup-gpt61`, fast-forwarded cleanly from
Main `8030832d2` to Root `28fd18de8212f8a844763d8399c9bcd000684a79` before edits.
[Source, exact commands, hashes and coverage](../validation/levels/s3k-bonus-stage-transitions.md#pachinko-bumper-score-popup-producer--2026-10-17)
record native `sub_32F56` credit → ordinary allocation → centre/frame 4 and
the authorized S3K-only points header dependency (bucket 1/high bit).
Retained draft `d56216d5ea199e49d848ef8d9f6505f513db8d9e` was inspected,
not adopted: its unconditional cap contradicts native zero-respawn bypass.
No foreign shield work or Slot follow-up `7380f7b1f` was changed.

Real-contact old-red 86734 fails only missing popup after score +10;
presentation old-red 67734 exposes all five S3K header values while S1/S2
controls pass. Final 37738 qualifies85 focused tests, including fresh-object
whole-world contact/live-popup natural-deletion replay twice and mandatory
S3K4. Canonical 75651 qualifies all three bonus traces. Segment63341 passes
Pachinko3 and Gumball; the other two fail at inherited122 rings /469 x_speed.
Matched Root28 runtime-only34084 reproduces the complete1638/1257 error arrays,
including all fields/spans/values/groups, with zero warnings/bootstrap/skips.
No frontier was tuned, omitted or reclassified. Candidate bytes restored
exactly; last local target is baseline-compiled, so Root compiles its final
composition afresh. No extra Maven/capture/review run is needed for this unit.

Owned private config is now an exact regular-file copy of Main's unchanged
config instead of T3's symlink. Source/Git changes used the intended-tree
lease; Maven used current Main's absolute wrapper from this cwd. All terminal
sessions, temporary backup and consumed diagnostic cleanup are accounted for.
Only the generated ignored private config remains for Root's tree cleanup.
No Main integration/push, branch/tree removal, RGB certificate, native run or
whole-objective qualification is included; Root owns remaining delivery gates.
## R10 HCZ word-address and destination-residency owner — 2026-10-17

Local e757 (Root067 merge64b77, then pre-edit Root28 merge) implements the two
R9 production owners: encoded native collision address selection and retained
128×128 destination bytes through actual HCZ1→HCZ2 construction/commit. Current
ROM prefix ownership also models positive MOVEA.W aliases after independent
review exposed incorrect RAM substitution. The safe refusal first stopped74271
at AIZ segment8 after2195 rows/ROM00007F; Root explicitly authorized the bounded
immutable current-ROM extension. No fixture/native RAM hydration or public API.

Genuine transition21043 old-red→6557 green; reviewed sign-domain38709 old-red;
ROM-owner53171 old-red→final3897 seven affected cases green. Twice-forced whole-
world recreation/restoration/forward replay covers live16×16/retained128×128/map
mutations, scene geometry, and immutable ROM binding after the intentional load
history reset. Focused4439=122/1 inherited mock error (exact Root61070 baseline);
guards45410=179/2 exact inherited MenuAudio failures, other177 pass. No unrelated
mock/guard edits or repeated broad selection.

Final original prefix49095 reaches7135 inclusive. Actual native7134 FG31/BG-1/
combined-1 and P20175.F800/vx0170/g0/push match. Returned HCZ7136/14208 rows still
contains206 errors (11camera physics+195animation); no non-camera physics
mismatch through7135. This is bounded closure, not zero frontier/full parity.
No full canonical/slice/alternate/GLFW claim; Root owns further qualification.
[Source hashes, exact commands, all windows and preserved intermediate failures](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r10-encoded-collision-words-resident-destinations-and-rom-aliases--2026-10-17).


### Composed strict qualification at152d07 — 2026-10-17

Root merged AIZR7 `e6ac5a0ef`, Pachinko `f1c319492` and HCZR10 `fa289e22e`
as `152d07ce90d512d2b75dfddfd10c777b0788acea`. Every changed runtime/test byte
matches its qualified local commit; only append-evidence conflicts were resolved,
with complete sections and both skill mirrors preserved. Main was fetched/FF
again and remains803; its exact unrelated status is preserved.

One current-Main absolute-wrapper canonical6 request93212, normal ROM lane,
original3ROMs/EGL/nullAL/noambient/Lua5.4/singlefork/reuse/alphabetical,
`-Ptrace-replay` originalAIZ+standing20, CompleteEmeraldRunPrefix, and fullchain
selector completed terminal1 at **14:12:04 UTC**, Maven2m46s, queue0s,
parent wall166.958s: **6 tests / 1 failure / 0 errors / 0 skips**. Original AIZ
and all three prefix methods pass. Fullchain's complete gameplay windows0/2/4/
6/8/9 have zero physics/animation/warnings/bootstrap errors (lags0/11/14/16/62/55).
ReturnedHCZ11 completes14208 rows with133258 errors =113873physics+19385animation,
lag59, warning/bootstrap0. First non-camera mismatch advances from7134 to
**7807 sidekick_y native066F/engine0669**; giant_ring exit remains absent.
This is not strict green, a full campaign pass or a blanket inherited waiver;
Main803's old HCZ result was incomplete and cannot certify these later rows.

The independent HCZ worker resumes bounded R11 on this exact source: existing
aux/source first, one original-driver passive7808 prefix, then only a proven
owner's independent old-red/minimal correction. No broad/ancestor/native sweep.
Root's four distinct segment checks are next; broad smoke is not yet submitted.
Actual local change-based plan3521smokeclasses/all10categories+guards remains
complete, with planned150execution-minute/no-output/source-fingerprint limits.
The separate Slot R2 tree owns VInt publication and five originalRGB moments;
it cannot edit this frozen validation tree. Ending's coherent caller/bus/readiness
closure and the whole-objective ledger above remain open, not covered by this run.


### Distinct slice qualification and matched published baseline — 2026-10-17

Root `a58bdc058` (runtime `152d07ce9`) ran exactly three distinct segment
classes through current Main's absolute wrapper, normal ROM lane, all three
original absolute ROM properties, `-Dmse=off -Ptrace-segments`, EGL/nullAL,
no ambient display, single fork/reuse/alphabetical: `-Dtest=TestS3kAizZoneSliceTraceReplay,TestS3kSonicTailsHczSegmentTraceReplay,TestS3kHczZoneSliceTraceReplay`.
Session67653 completed at14:16:10UTC, queue0, Maven48.013s/parent48.757s:
**4 tests / 2 failures / 0 errors / 0 skips**. AIZ slice retains only26179
rings72/0, matching actual803 baseline78232; team HCZ and the independent
Poindexter check pass. HCZ slice completes29302 observations with4353 errors
(3424physics+929animation), first9482 air1/0, warning/bootstrap0.

A single matched baseline17155 ran only
`TestS3kHczZoneSliceTraceReplay#replayMatchesTrace` on actual Main803, same
profile/runtime flags/ROMs/environment, with isolated owned report paths.
It completed at14:24:23UTC, queue0, Maven35.057s/parent35.759s:
**1 test / 1 failure / 0 errors / 0 skips**, complete29302 observations,
4699 errors (3973physics+726animation), same first9482 air1/0. Main's exact
foreign status and config hash/mtime remained unchanged. No branch switch,
source edit or copied build tree was used.

Full error-record comparison shares1132 exact records; current3221 and
baseline3567 records are unmatched, partly because consecutive spans split
or coalesce. These numbers are not counts of newly wrong gameplay values.
The earliest current field/row absent from all baseline error spans is
**10834 tails_cpu_respawn_counter expected0000/actual0001**. Baseline has no
error for that field at10834. Both runs have identical total observations,
latest zone/act, warnings/bootstrap and eight missing advertised aux schemas.
The lower total therefore does not establish nonregression; the mixed slice
trajectory remains an integration blocker pending causal attribution. A
read-only source/history audit owns that question, while R11 independently
owns the original-route7807 block contact. No tolerance, field omission or
fixture hydration was introduced.

R11 and Slot R2 remain separate local trees. Once their bounded owner work is
accounted for, Root will compose before the one complete change-based smoke
selection (currently3521 candidate classes/all10 categories plus guards),
with150 execution-minute and10-minute no-output limits, queue excluded.
No broad run has yet been submitted. The full suite remains release-only;
ending's coherent CPU/bus/readiness timeline and other ledger limits remain
open. Consumed report cleanup follows the read-only attribution audit; no
raw-log archive or validation receipt is retained.


### Slot native publication R2 handback — 2026-10-17

Owned `t3-649fee32` fast-forwarded cleanly to Root `e8f2d15e2`, then created
local `feature/ai-s3k-slot-native-publication-gpt61` while preserving the R1 ref,
registry and private regular config. Recovered only the zero-scalar native +$400
step and actual queued-row/VInt_DrawLevel publication owner; rejected retained
7380's next-update approximation. Typed S3K profile invocation is after DMA and
physical-token guarded, with captured pending and immutable actual published-plane
state. Unchanged captures share one defensive value; actual initialization/scene/
runtime identity invalidates it. Two forced complete-world cycles cover both
sides of queuing and publication. Independent GPT-6.1 native review found no
material finding at the recorded final hashes, no reviewer execution.

Old-red85042/93592 proves each missing behavior. World12642 exposed missing
published bytes, then repaired within the authorized handler. Final51197 passes34
and reuses unaffected73 from92667 (S3K4/profile/animated-publication). Slot3566
Knuckles1024rows and78564 team5259rows pass with no errors/warnings/bootstrap/skips.
Single five-PNG capture28913 yields fixed971/974/1017/1022/1026 residuals
661/360/50/52/58 original RGB pixels, all visually inspected; no native rerun,
normalization, search, media encode, extra test or zero-parity claim.
[Exact sources, selectors, failures, hashes and RGB provenance](../validation/levels/s3k-bonus-stage-transitions.md#slot-zero-step-and-native-row-publication--2026-10-17).
All jobs/leases terminal, candidate compilation current; consumed diagnostics and
temporary helpers cleaned, private config/durable originals retained. Root owns
composition/combined smoke/guards/integration/push; no Main change or tree removal.


### Slot inter-cell painter priority R3/R4 handback — 2026-10-17

R3 retained SAT/VRAM/CRAM identifies earlier nonzero-index black hidden by the
later wall's blue at row1017. R4 in owned649, branch
`feature/ai-s3k-slot-cell-native-priority-gpt61`, over670cf, changes only the
Slot cell traversal to reverse painter order; frame-piece ordering and shared
render/profile/API/geometry/gameplay remain unchanged. Genuine actual-ROM GPU
old-red32764 (frame11 from $33B+$A9; black0/blue102) precedes the runtime edit;
five earlier fixture-prerequisite failures are separately recorded. Final40686
passes24 affected cases, including repeat/passivity/reversed/separated controls.
Root's bounded source inspection has no material finding; R2 unaffected checks,
S3K4 and both Slot traces are reused with exact original coverage limits.

Single fixed five-PNG48918, same original references/inlet/profile as R2, yields
599/295/41/52/57 residuals versus661/360/50/52/58:137 matching original pixels
recovered, zero new bad pixels. All ten pictures inspected; original578/267
components remain identical/unattributed, HUD native4/engine3 stays an unqualified
bootstrap limit. [Exact evidence, failures, commands, hashes and provenance](../validation/levels/s3k-bonus-stage-transitions.md#slot-native-cell-priority-r3r4--2026-10-17).
Source/test frozen before capture; all handles terminal, consumed diagnostics/
helpers removed; private config/durable pictures/R1R2R3 registry results preserved.
Local commit only; no extra review/trace/owner, Main/Root edits or publication.


## R11 HCZBlock offscreen Full2 owner — 2026-10-17

Owned3aa3 branch `bugfix/ai-s3k-hcz-7807-causal-owner` fast-forwarded fa→152d07,
preserving R10 evidence and foreign native4fdf. Root93212's first non-camera7807
sidekick_y066F/0669 was an actual block-owner miss, not ceiling terrain. The
one passive prefix46246 observes empty ceiling+20 and block21 at09B0/0650
returning NONE despite native Full2 underside separation. Native loc1F3CA calls
SolidObjectFull2, bypassing BOTH Player2 render and object loc1DF88 gates.
Only HCZBlock's existing bypassesOffscreenSolidGate capability now returns true.
No shared solver/movement/API/new state or source/clock fit.

Initial89593 was a first-dispatch test setup error (3/3F including control).
Corrected ordinary admission85345 gives genuine3/2F: on-screen passes, two
offscreen cases miss six pixels. Final focused41923 passes157/0F/0E/0S, including
three visibility controls, twice-forced whole-world recreation/replay, block and
solid neighbors and mandatoryS3K4. Affected guards22063 pass118/0F/0E/0S.

Temporary sensor/checkpoint observers were removed before sole final prefix7687.
Original driver/input/lag from boot through7808: HCZ7809/14208 rows has206
errors=11camera physics+195animation, lag53, no warnings/bootstrap or non-camera
physics mismatch. Actual7807/7808 endpoints match native. Matched pre-fix46246
same7809-row window had214=19physics+195animation; eight non-camera fields close.
Earlier complete gameplay windows0/2/4/6/8/9 remain zero errors. First remaining
all-group error3475 sidekick_animation_id17/1C; next non-camera beyond7808 is
unmeasured. No fullcanonical/slice/alternate/GLFW/native/broad claim or next fix.
Root owns further qualification/integration; local commit only.
[Source, exact commands/hashes, clocks, all windows and limits](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r11-hczblock-full2-visibility-owner-closes7807-through7808--2026-10-17).


### R8 ICZ1 bounded natural post-death controller authoring — 2026-10-17

Same owned98d/native01a1496e/GPT6.1, clean AIZ branch FF to Root
`a58bdc058ec440d6e2ce70f3be38ee0ebcecc9b5`; previous branch ref retained,
new `feature/ai-s3k-icz-natural-post-coverage-gpt61` in the same tree. Exact
returned R8 taskID is registered with native/workspace/24h expiry; R1–R7
records are untouched. Own config is now a regular exact Main byte copy,
not the T3 symlink. Main SHA-256 `6abd111c…f7da` and mtime_ns
`1792141742849921547` were verified unchanged before/after all execution.

Read the existing three failed authored routes, ROM placements and disassembly
pin044fa467. The old temporary blob is unavailable in this object inventory,
so reused the durable literal4594 input stream and production
`CanonicalEntryMatrixSupport.boot` / `GameLoop` input contract. Original S3K
ROM SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` verified. Block1EE3CA
confirms post3BD0/076F, harmful ice3BF3/06CE and3D10/0658, platform3CE0/0784
and springs3CB6/07B3,3E0A/07B0. `sub_8B0B0` needs standing lower contact,
speed at least200hex and matching facing; static ice subtype0 uses ordinary touch.

ONE offline policy observed ground/air/position and live platform geometry to
choose buttons: seek a grounded leftward approach from its right side, jump,
steer toward its centre, release A at the observed ascent end, move right at
platform height. Cap1200 additional inputs/12 jump starts, stop on natural
death or cap. Velocities/hurt/rings/lives were observations only. No gameplay
state writes, hydration, raw restore, API/runtime change, native/media,
branch sweep, second candidate or extra validation JVM.

All requests used the current Main absolute Maven wrapper from own cwd,
normal ROM lane/all3 original ROMs/Java25/EGL/nullAL/no ambient display,
Lua5.4/exact single-token JTO, single fork/reuse/alphabetical. Source was
frozen queued/live; every queue wait was0. Prerequisites are separate:

- 90341 terminal1 14:28:52UTC, Maven1m01: testCompile-only nonexistent
  `GameStateManager.getRings`, no tests/gameplay. Corrected two read-only
  observation calls to playable.getRingCount.
- 3516 terminal1 14:30:41UTC, Maven23.411s,1/0F1E0S: input-tool separator
  error BEFORE boot/GameLoop, zero gameplay. Added only the statement separator.
- 53312 terminal1 14:32:25UTC, Maven22.963s,1/0F1E0S: exact1701/3523/4594
  assertions pass, then optional dump called default getX on a null-spawn
  synthetic owner. Zero continuation inputs/jumps. Root explicitly authorized
  ONE exact rerun after the dump fix. Null metadata now avoids getX/getY;
  optional isDestroyed is omitted because some overrides can mutate lifetime.

Final53551 terminal1 at14:35:23UTC, Maven25.214s,1/1F0E0S,class1.797s:
exact prefix assertions GREEN; sole continuation consumed1200 inputs/10 jump
starts and capped alive at5794, x3D04/y0765, airtrue/hurtfalse/deadfalse,
rings0/inv0/lives3/post1active, LF16A1/camera3C74/0720,gs0/VX−247/VY−195.
No observer failure; only the explicit natural-death assertion fails.
Actual platform ride sampled5482/5582 (X speeds−305/−318, below native200hex
threshold); later samples return to the lower channel. No lethal owner or
runtime defect proved. Exact literal31-block RLE, tested probe SHA-256
`5648de0e5ac7fd924860e572af36e33bd2b30952cf48b7d16b84456741fc578f`,
command/source/clock/endpoint/limits and prior prerequisites are recorded in
[the existing ICZ1 matrix](../validation/levels/s3k-icz-act1.md#r8-ground-aware-post1-continuation--2026-10-17).
Natural post death/life loss/saved-post reload/world replacement/history
retirement and pre-contact/after-release twice-world replay remain OPEN.
Existing semantic restart qualifications are unchanged; no runtime fix,
changelog, strict trace frontier, integration or publication claim follows.
Only docs syntax/link/diff/policy checks and local commit/consumed cleanup
remain. Root owns composition/broad/Main; all four handles are terminal.
Target last compiled the temporary observer, with no maintained rebuild.


R8 final cleanup: removed the owned temporary source under the intended-tree
lease, then consumed seven inspected diagnostic/input/compiled-class files
(76451bytes) under the metadata slot alone. No archives/receipts remain.
Exact durable RLE equals the executed literal (1200inputs/10 A holds/31blocks);
new local links/anchors, Markdown fences and diff checks pass. Source diff is
empty. Main config hash/mtime and own regular exact byte copy verified again.
No extra Java execution. Preparing the normal local evidence-only commit;
all seven policy trailers and final GPT-6.1 coauthor are parsed before commit.

### Ending Kos CPU/bus core dependency — 2026-10-17

GPT-6.1 owns this bounded dependency in retained `t3-fa8da232`, branch
`feature/ai-s3k-kos-bus-timeline-gpt61`, based on Root
`bef8f27ca5069ec296e6912b1e1bc7f735375c2f`. The preceding Apply branch ref and
unknown TraceChaser checkout `4fdf7388` are preserved. The intended-tree build
lease protected branch creation/fast-forward; hooks are installed. Ignored
`config.yaml` is a private regular exact-byte Main copy; Main was read-only.

`S3kKosBusTimeline` is package-private and has **no production registration or
initializer**. It advances the existing Kos semantic switch one instruction at
a time, rather than subtracting a known subtotal from a frame period. Captured
state includes Kos registers/RAM, monotonic MCLK, explicit native phase origin,
refresh deadline, SR/IRQ, nested exception return frames and interval work.
GPGX `051d430d3d1b54625f9900c8f152d7f232e06daf` owns the rules:
`m68kcpu.c:m68k_run` refreshes at opcode fetch, resets its deadline to fetch+896
MCLK and charges14; `m68kcpu.h:m68ki_exception_interrupt` indexes
`{50,59,58,57,56,55,54,53,52,51}` by `(counter/7)%10`;
`system.c` rebases CPU and refresh counters together. Explicit origin rebasing
preserves the native phase without assigning a scene clock.

Per-instruction inputs must explicitly resolve fetch/execution stalls and
post-instruction IRQ. Interrupt inputs separately resolve vector, post-ack IRQ
and acceptance-stack waits before the phase lookup. Missing work refuses before
clock or Kos RAM mutation. `m68kops.h:RTE` restores PC/SR and calls
`m68ki_set_sr` **before** charging its20 base cycles; an inline pending VInt
therefore receives those cycles in its new interval. Actual Set_Kos_Bookmark
writes occur in their handler instructions; Backup's16+92+16 instructions run
after RTE in the following interval. The original budget consumer remains an
oracle; both consumers share the instruction seam.

All 296 bytes `$1BF0..$1D18` are authenticated, including branch/immediate
operands and Backup redirect target (SHA256
`071f3b96cee3219995dbdb672901740653bc1dbfbf643c53c28e3fc3f9a236a9`).
The existing Kos model independently authenticates its254-byte body
`$1C1A..$1D18` (SHA256
`0df7c3df3656a11694522ab3cd50046d2fe7f58e8b6d5e999f0fd690011ee0cd`).
ROM arrays and captures are defensive. The only supported RAM instruction is
`V_int_jump` at `$FFFFF0`: a future live engine RAM owner must supply all six
`4EF9` bytes and the selected aligned ROM target; exact12-cycle JMP semantics
are checked. No initialized bytes or arbitrary RAM instruction execution exist.

Observed validation, all via current Main absolute
`${OPENGGF_MAIN}/tools/testing/maven_queue.py` (`OPENGGF_MAIN` is the absolute
Main-workspace root; the task thread retains the expanded host commands)
from this intended tree, `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`,
`LUA_BIN=/usr/bin/lua5.4`, `--normal -Dmse=off`, no DISPLAY/WAYLAND or
LD_LIBRARY_PATH, `EGL_PLATFORM=surfaceless`, `ALSOFT_DRIVERS=null`,
`-Dopenggf.headless.gl=surfaceless-egl` and absolute Main
`-Dsonic1.rom.path=${OPENGGF_MAIN}/s1.gen`,
`-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen`,
`-Ds3k.rom.path=${OPENGGF_MAIN}/s3k.gen`, suffix `test -B`:

- Red98448: `-Dtest=TestS3kKosServiceModel#refusesChangedOperandEvenWhenEveryOpcodeStillMatches`:
  1 assertion failure/0 errors/0 skips, old opcode pins accepted immediate 1C29.
  Scaffold 33983 separately errored on its unimplemented API; that is not a
  gameplay failure or an additional old-red assertion. Intermediate 95409 passed 29.
- Pre-P2 focused38250: `-Dtest=TestS3kKosBusTimeline,TestS3kKosServiceModel,TestS3kVIntInstructionCycles,TestS3kVIntInstructionProduction,TestS3kVIntDma,TestS3kVIntDmaLedger,TestHardwareTimingService,TestHardwareTimingRewind,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils`:
  **157/0F/0E/0S**, Maven 77s, queue 10s, 13 resolved classes. Core 20 covers
  hand-derived refresh/ordering, all10 phases, non-whole-cycle stalls, explicit
  ack, both RTE paths, bookmark/Backup/resume, nested IRQ, six-byte RAM negatives,
  atomic refusal and two restored forward continuations with real Kos RAM/output.
  Existing Kos 15 retains all 63 comparison-only native service windows; mandatory
  S3K4 contributes 60. This is bounded focused validation, not a full suite.
- Pre-P2 guards25449: `-Pguards -Dtest=TestArchitecturalSourceGuard,TestHardwareTimingAuthorityGuard,TestRewindArchitectureGuard,TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault,TestZoneEventRewindSchemaGuard,TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard`:
  **132/0F/0E/0S**, Maven 37.067s, queue 6s. Runtime/test sources stayed frozen.

Tested source blobs for that pre-P2 epoch (before documentation/local commit): timeline
`670266e8380b6d265cb8eca179c4e85e68b7712d`, Kos model
`3616911bd8dfaef28c8e53c070c4e823c922562d`, core tests
`ccb36e301dc093598ccee108f4d9aa6b28d20850`, Kos tests
`1d33ea8dbd045b65a2fad978f8ef57bd2020d369`. Runtime SHA256 values are
`b532630730183a75fc00890f2686d961341282064baed303ae7cc849e1b8f493` and
`e50315982110d8501376cd8b13b420e71f170a7c89a9e53b3dcebeec1a8debbe`.

**Limits:** captured pure state is not a legitimate clock initializer or whole-world
registration. Production boot/RAM phase, ordered caller/object/render/palette/HUD/
plane/Nemesis work, complete handler producers, DMA/Z80/FIFO/HInt event producers
and readiness coupling remain missing. Ordinary external handler system-SR writes,
multiple differently targeted acceptances in one step, IRQ7, DMA rewind and
VDP-write delayed IRQ semantics refuse/are unsupported rather than approximated.
There is no frame-period budget, character/route fit, trace hydration, manifest,
profile or service-count change. The original 6 measured counts, eligible 46-context
prediction, Hyper/PAL/PCM and full ending acceptance remain OPEN. No native,
media, performance, full-chain or broad job was launched; Root owns composition.

Independent source review found one P2 before commit: an unsupported Kos source
could change fetch/stall timing before rejecting, and a literal's source cursor
could advance before its unsupported destination rejected. Old-red63834 selected
`-Dtest=TestS3kKosBusTimeline#unsupportedLiteralDestinationRefusesBeforeSourceCursorOrAnyCapturedStateChanges+unsupportedSourceRefusesBeforeFetchStallRefreshOrAnyCapturedStateChanges`
and observed **2 assertion failures/0 errors/0 skips** against complete timeline+
Kos state equality (Maven 26.675s, admission 648s; waiting was not execution).
The correction prevalidates every variable byte/word/long memory operand before
clock, SR, register or RAM mutation, including both sides of a postincrement copy.
It retains signed copy offsets, 24-bit addressing and supported RAM alignment.
This is bounded address checking, not a speculative decode or 64KiB per-instruction
copy; no bus value or emulated cost is invented. A focused independent source-only
review cleared the exact correction; that reviewer did not execute tests.

Final affected checks used the same normal wrapper/environment/ROM prefix above:

- 2023, `-Dtest=TestS3kKosBusTimeline,TestS3kKosServiceModel test -B`:
  **37/0F/0E/0S** (core 22 + existing Kos 15), Maven 72s, admission 86s.
- 83355, `-Pguards -Dtest=TestArchitecturalSourceGuard test -B`:
  **72/0F/0E/0S**, Maven 26.251s, admission 27s, separate fresh JVM.

Those final runtime blobs are timeline `1026a5c928672d063de8bd191b5dc82fa2696db0`
and model `91fbac1c7a45226bdce415a9652fb9b95453d17f`; core-test blob is
`2e7673cc12f938e8812271fffcb1a4175a50d95d`, with the Kos-test blob unchanged.
Final runtime SHA256 values are
`116c8bcec841278dea325b2cddb8f63714472b66308f301fb05a763fae0828ff` and
`2f00bd16f5b807600d9e095e51b4ca3e35060dcd3bf39c313e37461b8e3c27d2`.
The earlier 157/132 epochs remain identified separately; unaffected neighboring
checks were not rerun. No production field, registration, timing authority or
profile changed in the P2 correction. All owned jobs are terminal; inspected
reports and the temporary commit message are consumed under the metadata lease
alone before handoff, with no archives. Only the preserved unknown TraceChaser
checkout and accounted private config remain outside the local commit.

### Current composition, slice attribution and broad-validation boundary — 2026-10-17

Root7397c5368 merges Slot R2 `670cf353d`; Root60d6fe135 merges HCZ R11
`cd94d7ca4`; Rootbef8f27ca merges ICZ R8 evidence `c8703d172`. Every affected
runtime/test byte matches its locally qualified commit. Append-evidence merge
conflicts retain both complete sections and identical skill mirrors. ICZ R8
is docs-only: the one executed continuation reaches5794 alive, so natural
lethal-contact/respawn/history coverage remains open. Slot's five original
RGB residuals661/360/50/52/58 also remain open; nativeHUD4/engine3 is an
unqualified carried-life/bootstrap difference, not attributed life loss.

Read-only slice attribution confirms current complete error array equals
historical R3 (SHA256 of `json.dumps(errors, sort_keys=True)`:
`71f91e9494d9a6926cd2f032dcc174a0d289355d9f9a6658b3752ae61d96d251`).
Matched Main803 array is `9c41e6af15d487b927e3ec81a53a4462653aeac299e257d3c189cfb23287551b`.
The10834 counter consequence predates R3/R4 title and R10/R11 layout/block
owners; earliest demonstrated boundary is compound `b6c4898e2`. Its native
carrier target/X-lock correction supports earlier camera improvement, but no
retained single-change observation explains10834. Normal CPU counter logic
is unchanged: on-screen resets0, off-screen increments. Native10833..35
has render_flags84/routine6/counter0; actual engine visibility decision remains
unobserved. R12 owns one passive slice-prefix question, not a counter fit.

One necessary post-composition slice58673 onbef8f27ca, same exact method,
trace-segments/profile/ROMs/environment/single-fork flags as17155, completed
at14:45:55UTC, queue0, Maven1m23s/parent83.857s:1/1F0E0S,29302observations,
4353errors=3424physics+929animation, warnings/bootstrap0/eightmissingaux.
Its ENTIRE sorted error-array hash equals the historical/current hash above.
Thus SlotR2/HCZR11 introduce no further slice error-vector delta, but10834
still blocks integration relative to published803. Both original matched
reports and all inspected canonical/segment output were consumed; no archives.

The actual local plan atbef8f27ca/base803 selects **3523 candidate smoke
classes, all10 categories, plus separate guards**. Root now owns ONE complete
`python3 tools/testing/run_categories.py --base 8030832d2ab6080e5324cdfe35e971677fd97b41 --run --rom-directory "$OPENGGF_MAIN" --max-minutes 150`
in this intended tree. Estimated execution90–120minutes, variable queue time
extra;150 execution-minute and10-minute no-output limits, source fingerprint
or launcher failure stops incomplete coverage. Ordinary batches contain at
most50 candidates and yield shared admission between batches/before guards.
No release-only full suite, manual narrowing, duplicate baseline broad run,
or package-triggered testing is selected. This broad check can find other
regressions while R12 and the bounded Slot painter owner work independently;
it does not waive known trace/RGB/ending/coverage blockers. Root source stays
frozen during the retained run. Later isolated fixes require their meaningful
affected checks before composition; repeated unchanged broad coverage is not
a ceremony. Whole ending CPU/bus/readiness acceptance remains a separate gate.

## R12 HCZ slice visibility consequence and composed route frontier — 2026-10-17

Same3aa fast-forwarded cd94→bef8, preserving R10/R11 refs, native4fdf and an
isolated exact-byte private config. No R12 production/maintained-test edit.
One original82EA slice prefix68487 stops10835:1/1F/0E/0S,104spans/10773 compared
rows, first9482air1/0, no warnings/bootstrap, eight missing aux schemas.
Actual10833 camera copy019A versus native0199 places P2x0181,width18 one pixel
outside instead of exactly on the inclusive edge. Its published valid cached
false correctly increments the following NORMAL CPU counter0→1 at10834;
fresh-entry rule/consumption are false. Next publication true resets10835.
No counter/cache/geometry correction is justified. A temporary PRE_MAIN_LOOP
label was wrong: it is the post-camera loop tail here. Causal proof uses prior
end-row publication and next post-CPU sample, not an invented pre-CPU hook.

The precise upstream gap is focused P1/selected history, H_scroll delay/forced
coordinate/clamp inputs at camera entry. Native final poses/copies/index exist;
selected Pos_table contents and scroll latch are absent from actual read aux.
Independent real-refresh/next-CPU edge controls should disconfirm the consumer;
a genuine camera-producer old-red needs those source inputs first, not native
output0199 assigned as a fixture. No new test or runtime change at this boundary.

Root separately required one maintained full AD40 canonical after removing all
temporary observation source/classes.17194 on exactbef8 is1/1F/0E/0S. Six earlier
gameplay windows0/2/4/6/8/9 complete at zero errors; returnedHCZ compares all14208
rows,125612=107498physics+18114animation, lag59, warning/bootstrap0. First
non-camera moves7807→8329 y_speed0000/-0680. Matched pre-R11 Root93212 was133258
(113873+19385) over the same rows. Exit giant_ring is still never observed; no
onward segment/playable exit qualifies. Four suppressed unmatched hardware
completions remain unattributed. No8329 repair/rerun/extra selection or broad.
Root owns further owner authorization, smoke/integration/push.
[Exact inputs, clocks, seam correction, commands, windows and limits](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r12-slice-cached-visibility-consequence-and-full-composed-canonical--2026-10-17).


## R13 residency smoke regressions repaired locally — 2026-10-17

Same3aa branchbugfix/ai-s3k-residency-smoke-regressions-gpt61, pre-edit merge25d
of Root02b into R12bd2; both append sections/old refs/native4fdf retained. Exact
pre-edit43333 reproduces15/1F/14E/0S. DDZ two component boot helpers now declare
existing fresh-start lifecycle (native Sonic_Init has no floor pass); a separate
ROM-derived FF82AF/definition20 probe still refuses unowned bytes. No DDZ runtime
fallback. LBZ native loc544C6 copies two row pointer words, while old code copied
154 bytes through four-byte BG rows. Independent ROM owner5006 old-red1/1F at
pointer8EF8 vs8F00 -> final same control green. Internal pointer COW/decoded view/
alias reindex and BG producer flag preserve source bytes, live mutation aliases
and two forced whole-world restore/forward cycles. No public API/pin expansion.

Frozen runtime363e7328… / runtime+tests581a2665… has Root read-only no-material-
finding review (no tests), reused.16322 passes86/0F/0E/0S including mandatory
S3K4=60 and native-word/overlay7;48957 passes118 affected guards, no skips.
All four own jobs terminal, no further selection. No trace/native/media/broad or
Root/Main writes; Root15826 remains foreign. R12 HCZ8329/exit and GLFW/slice limits
remain unqualified on the new source. Root owns composition/delivery. Exact
commands/measurements/owner classification are in the [existing audit](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r13-residency-smoke-regressions-native-row-pointers-and-ddz-fixture-entry--2026-10-17).


### R9 ICZ/LRZ residency smoke owner — 2026-10-17

Same98d/native01a1496e/GPT6.1, new local branch
`bugfix/ai-s3k-icz-lrz-residency-smoke-gpt61`. R8 c8703d172 and prior AIZ refs
preserved. Clean FF through frozen Root02b38825c → merge25d3500cd → qualified
R13 `0cd178d8f4bf8e9ea54e10b133aa2260a68483be` before new edits; all native/
submodule identities retained. Own config re-isolated as regular exact Main
bytes after hook, Main6abd111c…f7da/mtime1792141742849921547 unchanged.
Exact returned R9 taskID/native/workspace/24h expiry in own registry entry;
previous entries untouched. Root owns baseline/composition/broad15826.

Source-read the exact ICZ in-frame deferred128x128 assertion and LRZ terrain
request after restore. ONE preedit command selects only
`TestS3kIczAct1TransitionHeadless#act1OutdoorTransitionReloadsIcz2AndAppliesRomCameraBounds,TestS3kLrzBossCameraHeadless#freshEntryConsumesTwoIndependentSignalsAndRewindsTerrainEdits`.
Current Main absolute queue wrapper from own cwd, --normal/-Dmse=off,
all3original absolute ROMs/Java25/EGLsurfaceless/nullAL/noambient/Lua5.4,
exact JTOsingle token/singlefork/reuse/alphabetical; no profile. Source frozen
queued/live. No Main baseline duplication, palettegateway edits, native/media,
ancestor/trace/broad/full run or natural-death authoring. No runtime cause or
pass claimed before results; precise owner proposal precedes shared extension.


R9 preedit58042 terminal1 at16:37:42UTC, queue835s/Maven1m09,
2tests/2failures/0errors/0skips. Exact expected ICZ235→517 deferred128 index0
and LRZ83 expected23/actual20 reproduce on adoptedR13; no other case ran.
These existing reds discriminate the stale oracles, so no duplicate old-red.
Native044 ICZ1BGE_Transition→Load_Level copies1000 layout bytes without RAMclear;
Sonic3k only carries resident128 during an executing transition. Test-only ICZ
oracle now immutably captures every known outgoing target128 byte before actual
handoff, checks common ROMprimary/endA00 and distinct detachedconstructor values;
all16/pattern/timing/admission/claimed/payload checks remain. LRZ adapter restores
new encodedowner/Map, while real mutation resolves currentLevel. Rebind after
restore; native ROMlayout0A766C row9→81A8, bytes81A9/81AA=14 independently anchor
literal17 writes, currentdecodedcells/encodedbytes/COW and twiceforcedworld
replay; oldMap is explicit detached-negative control. No runtime extension.

Root's separate batch58 DDZ2 synthetic-floor errors are a distinct accepted
R13 inlet cause: Sonic_Init has no floor read and loc81554 owns control81.
Only the two TestS3kDdzFlightControllerHeadless build calls add the existing
fresh-start lifecycle; no runtime/RAM/fallback/assertion change. Upcoming focused
selector includes those exact2 and existing TestDdzRomPalettes unknown-definition
negative, not its14 unchanged functional cases. Source hashes relayed frozen;
stop on any new postboot assertion/owner before edits or further runs.


Root-provided matched Main80371290, NOT an own execution: terminal0
2026-10-17T16:36:32.760938Z,4/0F0E0S (ICZ1 .124s,LRZ1 .119s,DDZ2 .806s),
queue853s/Maven24.405s/parent878.591s. Root inspected all four identities/skips;
all3 test files are byte-identical Main803..Root02b. Main HEAD/foreignstatus/
config preserved and inspected reports consumed. This independently attributes
all four new Root failures; no duplicate baseline run in this tree.


### R9 green LRZ/DDZ split and R10 ICZ evidence boundary — 2026-10-17

R9 final54296 terminal1 at16:40:51UTC, queue0/Maven28.788s,
72tests/1failure/0errors/0skips across11 actual classes. Exactly71 pass:
LRZ current-map/encoded/COW/detached-negative/twice-forced-world case1 (.107s),
DDZ controller2 (.134s), DDZ unknown-definition refusal1 (.037s), native-word
residency4 (.201s), overlay3 (.078s), and mandatoryS3K4 selectors60 (both
same-simple-name loading classes43, skip8, bootstrap6, decoding3).
First NEW failure is ICZ258→536 deferred16x16 index0 after the corrected128
checks pass. Pattern and later publication assertions remain unreached. No
further code edit/run/guard followed that STOP. Root source review found no
material finding on the exact three test hashes, but does not replace results.
All26 inspected own XML/TXT files (4preedit+22focused) consumed under metadata
slot alone; no archives. Main config/hash/mtime unchanged, all handles released.

Actual focused command, current Main absolute wrapper from own cwd (set
`OPENGGF_MAIN` to the unchanged Main checkout; original ROMs are absolute):

```bash
env -u DISPLAY -u WAYLAND_DISPLAY EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  LUA_BIN=/usr/bin/lua5.4 JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal \
  -Dmse=off -Dopenggf.headless.gl=surfaceless-egl \
  -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical \
  '-Dtest=TestS3kIczAct1TransitionHeadless#act1OutdoorTransitionReloadsIcz2AndAppliesRomCameraBounds,TestS3kLrzBossCameraHeadless#freshEntryConsumesTwoIndependentSignalsAndRewindsTerrainEdits,TestS3kDdzFlightControllerHeadless#entryRingAwardRetainsHudDigitsAndReplaysTheLaterDrainRefresh+introFlightTransformationAndReleaseFollowTheControllerRoutines,TestDdzRomPalettes#freshEntryDoesNotFabricateDefinitionsForAnUndeclaredTerrainProbe,TestS3kNativeWordLayoutResidency,TestSonic3kLevelOverlayRewindIsolation,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" \
  "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" \
  "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" test -B
```

Fresh ICZ R10 (distinct from HCZ R10fa289) registers its exact returned taskID/
native01a1496e/workspace/24h expiry in its own entry; R1–R9 results untouched.
User authorizes FIRST a separate local green-slice commit: only reviewed LRZ
and two DDZ inlet source changes plus their accurate existing docs. Reuse54296
proof without rerunning71. ICZ partial test stays UNSTAGED at exact SHA-256
`e06e322770e26e6f19f2c2f211d00d331c9adb65e5face390230cfeade9fe5cc`;
no red ICZ/runtime/whole-suite pass is committed. Normal hooks/7trailers/final
GPT6.1 coauthor, exact-path commit; report accounting before any ICZ execution.
Then ONE passive original maintained ICZ driver observation may inspect actual
16-table counts/complete bounded words/ROM destinations/128 references at the
outgoing, detached-constructor and actual in-frame/prePOST boundaries. No runtime
extension, unknown-zero normalization or dropped comparison is authorized.
Current source candidate: loadChunks has no retained16 input and constructor
capacity validation can add empty entries after retained128 references expand.
Actual counts/words were unavailable in the original assertion; neither missing
retention nor capacity-only extent is proved. Hand back that evidence and an
independent old-red proposal before any production extension. Existing ICZ
pattern/publication tail, runtime parity and R8 natural death remain open.
No broad/trace/native/media/extra worker; Root owns composition/delivery.


### ICZ R10 observed retained16 loss and handback — 2026-10-17

Green LRZ/DDZ split `00272ae474529412e3c02b7af05349499bc6b1c0` is complete under
normal hooks/exact6path commit; no ICZ source or runtime in that commit. Seven
policy trailers and final GPT6.1 coauthor parsed. New same-tree branch
`bugfix/ai-s3k-icz-retained-16-owner-gpt61` preserves all old refs/native checkout/
regular private config. ICZ e06 partial source remains unstaged, not qualified.
Exact new ICZ R10 task/ordinal10/native/workspace/24h registry entry is separate
from HCZ R10fa289; all earlier registry results remain untouched.

Sole diagnostic3364 terminal1 at16:51:31UTC, queue14s/Maven25.677s,
1/1F0E0S,class.576s, exact maintainedICZtransition method/driver/all original
flags and all3ROMs as R9, observer811f3544…67e1. All3 read-only boundaries
captured; no observer error or extra run. Journal284621bytes/1777lines (<10MiB).
Outgoing688chunks/230blocks has559 known target entries129..687 matching every
word of ROM3D5958/1178hex bytes; detached129chunks/20blocks hasall589 target
entries absent; actual688chunks/230blocks hasthose559 entries all native4zero.
First129 C131/C132/C133/C134 (collisionFF/FF) →sixzeros,12 references/firstblock30.
Max retained128reference687 explains capacity688 versus decoded129. This proves
both absent-vs-placeholder extent AND genuine loss of559 known pattern groups.
30 target entries688..717 remainABSENT/unknown atall boundaries; no zero
normalization/skip/narrowing. Collision metadata180differences remain a separate
LoadSolids concern, not authority to copy predecessor indices. ActualLF0 belongs
to this unchanged component driver, not native object-clock/trace timing proof.

LoadChunks has no retained16 input; reference validation pads from retained128
max687. Native044 ICZtransition→Load_Level copies onlylayout/no16clear, then
LoadSolids owns collision indices. All initial128/queue/unclaimed checks pass;
same16index0 fails, pattern/laterpublication tail unreached. Proposed next
independent red is known entry129 across the real handoff, independent target
collision indices and complete claimed ROM payloads, preserving unknown30limit.
Potential private immutable16-pattern carry parallel128/COW/rewind is handed
back BEFORE production extension; no runtime change or extra validation is
made. No generic capacity/allocator/API/pin/fallback/clock/physics edit.
[Exact phase counts, full first words, ROM sources/hashes and limits](../validation/levels/s3k-icz-act1.md#r10-retained16-ownership-observation--2026-10-17)
are durable; temporary observer restored exactlye06, inspected diagnostics/class
consumed under metadata slot alone, no archives/rebuild. Latest target compiled
the observer and is not a fresh maintained test build. Root owns next scope.


R10 evidence cleanup/checkpoint: exact observer-free ICZ e06 restored under the
intended-tree lease, then four inspected journal/XML/TXT/compiled-observer files
(340244bytes) consumed under metadata slot alone. No archives, raw reference
changes or extra JVM. Source/main delta remains empty. Main config hash/mtime
and own regular exact bytecopy verified unchanged. New local links/anchors,
Markdown fences and diff checks pass. Preparing docs-only evidence commit;
ICZ partial source remains deliberately unstaged/red. Root must authorize the
next independent old-red/production owner unit; no extension is silently made.

**R14 bounded camera-input handback (2026-10-17, base0cd178d8f):** production
and maintained tests unchanged. Build-only82622 passes; sole original82EA
prefix63258 is1/1F/0E/0S,104=92physics+12animation spans/10773 compared rows
through10835, first9482air1/0;10834counter0/1 remains. Queue1026s/Maven30.850s.
20 passive samples/58494bytes prove actual camera-entry history selection:
10833currentP10231.CD00/latest23A/slot19/delay1 selects23A, so186+14=19A is
correct; no bounds/cap/forced/defer latch explains a camera-owner fault. Native
aux has index/copies but lacks selected history/delay/entry P1/forced state;
final pose cannot replace RecordPos/Camera-entry values. No Camera/CPU fix or
old-red is supported. Byte-read native entry pins1C0A8/RecordPos10D66 are distinct
from retained named10D80 operand pin; capture code/fixtures remain untouched.
No further execution; observer/classes and six consumed diagnostics removed,
all own jobs terminal. R10..R13 refs/native4fdf/private config preserved. Root
owns native-plan review, broader route/8329/exit and delivery.
[Exact original fixture hashes, source pins, clocks and causal limit](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r14-slice-camera-entry-history-inputs-no-camera-or-cpu-repair--2026-10-17).

**R15 native-input plan checkpoint (2026-10-17, source72d1be929):** same owned
3aa/new local feature branch, engine/native checkout unchanged. No matching
original82EA retained fullRAM identified; AD40 dumps do not close the question.
External real-instruction RecordPos/MoveCameraX observer is compiled-only0 under
writer/shared activity/inheritedFDs, native count0. Frozen source/command/opcodes/
original power-on bootstrap and four endpoint equality controls handed to Root;
parent technical review is required before one normally admitted native pass
through10835. No engine replay/runtime fix or source/fixture authority mutation.
[Concrete pending plan and hashes](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r15-original-native-camerahistory-input-plan--2026-10-17).

**R15 actual one-pass result (2026-10-17, unchanged72d1be929 production):** Root
reviewed the concrete plan, then authorized only launcher provenance rechecks
and one normal native execution.46154 terminal0, queue248s/native10.3544623s,
FDs5/6/7; all four endpoint controls and60 ordered/passive callbacks match.
Native10833 RecordPos writes0239.E0, next index48→4C; entrydelay0100 selects
actual0239@FFE548 ->camera0199. Engine R14 selects actual023A ->019A correctly.
Native/engine selection agrees by intent; differing P1 producer input already
precedes the camera/CPUcounter consequence. NativeRecordPosreturn239.E0→Deform
231.E0 brackets an unobserved writer, not an attributed HCZBlock/carrier fix.
No Camera/CPU/shared runtime correction or genuine old-red is supported. Proposed
independent real-record/solid-correction delay1/0 control is disconfirming and
not run. All own jobs terminal, no second native/engine test or route certificate;
Root owns the earlier P1 writer,8329/exit, combined validation and delivery.
[Actual source pins, phases, identities and limits](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r15-original-native-camerahistory-input-plan--2026-10-17).


### Combined smoke interruption and matched consumer gates — 2026-10-17

The unchanged Root `02b38825ce28a41ab360a74d50b75a7e9cf3443c` invocation
`python3 tools/testing/run_categories.py --base 8030832d2ab6080e5324cdfe35e971677fd97b41 --run --rom-directory "$OPENGGF_MAIN" --max-minutes 150`
selected3523 smoke candidates/all10 categories/71 ordinary batches plus guards.
Run `20261017T145127Z-5f175bf9`, parent command15826, returned143. The reason
for that signal is unknown; it is not recorded as the runner's execution timeout.
The original runner was gone while admitted batch66 Maven2980730 and fork2980920
remained alive with inherited tree/admission/activity descriptors. Root remained
frozen, neither child was signalled or restarted, and both later became absent.

The last runner-owned aggregate records **65/71 batches,35353 tests,
2788 failures,26 errors,86 skips,4527.09 execution seconds: INCOMPLETE**.
Preparation had passed23.34s. Queue intervals are additional; execution sums are
not elapsed delivery time. The failed-case list caps at1000 and omits1814 cases;
family attribution used bounded current XML and rolling final summaries, not the
capped list alone. Batch66 subsequently closed all50 class XMLs with289 tests,
1 failure,2 errors,3 skips. These are separately observed class results: the
parent never recorded that batch or a final Maven status. The remaining223
ordinary candidates in batches67–71 and all guards were not executed. No full
smoke/full-suite pass is claimed. There is no supported runner resume; slicing
includes or silently repeating the unchanged broad selection is rejected. The
next combined validation must use the resulting source and its actual change-
based plan after focused repairs, with the uncompleted coverage explicit.

Known families were inspected and attributed by identity/message, not totals:

- Actual Main803 controls retain title-input ownership, two null-rules/white-fade
  mock errors, and the HCZ native-rendering GLFW initialization failure. These
  results do not qualify pixels or repair unrelated mocks.
- The candidate's new restored-fade pending false/true families include donor,
  LRZ/SSZ, carried-art, SOZ, portal/return, reward mechanics, gateway and sanctuary
  consumers. Qualified local759 has119 focused/83 guards plus SOZ84980 and the
  **entire892** LRZ/carried-art population green; actual Main96596 also passed
  that892 population. This does not waive all other consumers.
- Candidate759's complete portal/return34577 is **993/142F0E0S**. All142 messages
  share the same publishedPlane byte-preview, replacing the older pending-field
  failure;851 cases passed. The two affected short pilots are[3] and[5]. Main61811
  ran all five short pilots:5/0F0E0S,0 skips, normal queue624s/Maven29.851s,
  terminal16:50:35.133086UTC, unchanged Main/source/config. Main lacks the new
  plane state key, so that pass does not prove current PlaneB determinism.
- R7A88468's one unchanged pilot[3] reproduces the original plane failure with
 81/81 passive checks. Raw and cached bytes agree; the first replay publication
  rebuilds the physical plane and adds46 bytes outside the matching current row
  batch. The exact invalidator is not uniquely observed. A separate real
  geometry-restore/invalidation partition30571 is4/1F0E0S: untouched byte619 is
  lost; three no-retained/live-mutation/scene-replacement controls pass. The
  retained-resource/derived-cache repair remains a separate bounded unit.
- Native LBZ row-pointer ownership/DDZ fresh-start declarations are qualified
  locally0cd (86 focused/118 guards). LRZ current-map/DDZ-flight declarations are
  split as00272, excluding unqualified ICZ work. Actual Main71290 passed all four
  matching ICZ/LRZ/DDZ-flight cases; no blanket old-baseline attribution is made.
- ICZ3364 independently proves559 known outgoing16x16 pattern groups disappear
  before deferred publication; the first native4 `C131/C132/C133/C134` becomes
  zero. Thirty further entries remain absent/unknown. Target collision indices
  are a different owner and must not be copied from the outgoing level. R10
  evidence077767 excludes the uncommitted partial ICZ test. Only a synchronous,
  known-extent private carry is under red-first design; no unknown-zero fallback.
- All seven DEZ incoming/final route errors are real gameplay collision reads:
  five `$FF80F9`/definition144 and two `$FF8103`/definition184. Main8712's exact
  solo-Sonic and ordinary-team methods pass2/0F0E0S, normal queue0/Maven57.117s,
  terminal16:58:34.519763UTC, Main/config unchanged. Candidate lookup exposes
  missing native residency; visual-width wrapping is not an accepted repair.
- Batch66's new `sonicColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory`
  fails `cold death49613`; both TraceCaptureBootDimensions widths error while
  initializing GLFW. Their matched Main control24967 is pending. These are
  unattributed until that actual result; neither inherited nor fixed is assumed.

All86 aggregate skips were inspected: opt-in measurements/captures, unavailable
GLFW/EGL,32 unavailable platform/flyer route assumptions, external mod JAR/setup
prerequisites and the explicit CPZ spin-tube assumption. Batch66's three skips
are opt-in Slot glass, external SecondStrike air-mod and packaged MGZ arena.
No wrong-ROM skip was observed. No skipped visual/native consumer is certified.
Main96596/71290/61811/8712 diagnostics were inspected and consumed under the
metadata lease alone. This run is likewise acknowledged only after the admitted
children were absent and its remaining class outcomes inspected; no raw archive,
pass cache or validation receipt is retained.

Additional bounded frontier evidence remains separate from smoke. Native R15
9bdf827aa proves original82EA10833 records239.E0 and correctly selects239 for
camera199; R14 engine records23A and correctly selects23A for19A. The earlier
P1 producer differs, but the intervening writer is not attributed. No Camera or
CPU fit is justified. HCZ skim0c7d9f20 uses the native logical press edge:
80 focused+25 authority green; its diagnostic prefix9812 remains honestly1F
because of an unsupported zero-animation assertion. The maintained canonical
49197 is1F: returnedHCZ completes14208 rows with26153=23312physics+2841animation,
first non-camera8717 sidekick_air1/0, then reaches the original giant-ring exit
and SS return. It later loses hcz_3 act ownership at BK2cursor88306, with only
partial later observations. No strict full-route or SS certificate follows.

Read-only ending inventory also identified three schema2 fdf jobs with4/5/4
aggregate services; the old classifier excludes their caller/mode. Their caller,
per-window initialization and actual hardware phase are unqualified. The current
host can expose CPU registers/RAM but no proven passive master-clock/YM/FIFO/DMA
phase getter was found. A first-stage original-movie producer/context plan is
only a proposal; it cannot initialize the unused coupled timeline from aggregate
counts. Ending6+46, Hyper/PAL/PCM, Slot845 foreground pixels/life bootstrap,
ICZ natural death/reload, the mixed slice and later route coverage remain open.



### Presentation rewind smoke repair R5 — 2026-10-17

Owned649 branch `bugfix/ai-s3k-presentation-rewind-smoke-gpt61` starts at
Slot R4 `28916874df24e770ebf9184fa191d82b55ad0582`. Before edits it merges
frozen Root `02b38825ce28a41ab360a74d50b75a7e9cf3443c` as local
`485f21a94b55eb166aa6637e8e1ec38ae6254561`; three append-only doc conflicts
preserve both streams. Slot renderer SHA256 remains
`148ce5f1b44beb40bacfe7979b88581547cd3f5717f47fa5b2e53adb2284455c`.
Root owns combined broad15826 and the separate SecondStrike barrel failure;
this unit neither edits that owner nor claims combined smoke green.

Root-provided Main803 exact-six-class control89086 completed15:12:57UTC:
18tests/1F0E0S, sole pre-existing GLFW failure; both selected R5 cases pass.
Own pre-edit isolated24274 on485 reproduces the donor failure,1/1F0E0S at
15:29:49UTC, Maven1m24/queue573s:
`after cycle0 level-sprite-presentation.displayedFade.pending A=false/B=true`.
Thus the broad's19 predecessors are not required to trigger this failure; no
predecessor/broad rerun follows. No exact introducing commit is attributed.

Tables.restore reinstalls the displayed fade including pending; subsequent
Sonic3kLevelAnimationManager restore rebinds its logical NONE/FROM_BLACK
request. GraphicsManager previously treated even a matching held request as
new pending work. The six-line shared semantic correction preserves the
captured pending bit only when held mode/steps/lineMask already match. True
stays true; different requests and ordinary palette latches retain the old
publication path. No snapshot field/key/assertion, game gate, Mod API, timing
owner or runtime results wait changes. Independent old-red51248 completed
15:32:57UTC,4tests/2 intendedF0E0S, Maven30.239s/queue29s: both NONE and
FROM_BLACK4 controls fail pending false→true; captured-true control passes.
The migrated production EndSignControl/results first/89/90 test also passes.
Native pinned044 `loc_2DCC0` / Wait2 own literal90; the obsolete removed13
reflection/construction was the test defect. No private routine/timer/slot/RNG
write or trace hydration is used by the new inlet.

Frozen reviewed source SHA256: GraphicsManager
`7155b074fd52dc0ed72beb62337955ce7d2d67c540ca45088a977b4f77c5174a`;
TestPaletteUploadLatch
`7f194e2d04a3330094261559e94f7b620550f0c1bf2c2433fec7a5484ebe9bed`;
TestS3kResultsScreenObjectInstance
`1ec02f171f002e4b2083b69c3a08e6aed10742a64879728e5fb0299abed685ec`.
Root's independent source inspection found no material issue; no review
execution or inferred pass is counted.

Exact executed command template below uses the original Main root ROMs and
current Main wrapper from the owned cwd; MAIN/OWNED_TREE denote those existing
workspaces, not new copies. Inherited other JVM environment is preserved.

```bash
cd "$OWNED_TREE"
r5_presentation_maven() {
  env -u DISPLAY -u WAYLAND_DISPLAY JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
    JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl \
    EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null LUA_BIN=/usr/bin/lua5.4 \
    python3 "$MAIN/tools/testing/maven_queue.py" -Dmse=off \
    "-Dsonic1.rom.path=$MAIN/Sonic The Hedgehog (W) (REV01) [!].gen" \
    "-Dsonic2.rom.path=$MAIN/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
    "-Ds3k.rom.path=$MAIN/Sonic and Knuckles & Sonic 3 (W) [!].gen" "$@" test
}
r5_presentation_maven '-Dtest=TestSonic2DonorSuperStateRewind#midTransformationRestoresPaletteTimerDrainAndOrdinaryMapping'
r5_presentation_maven '-Dtest=TestPaletteUploadLatch#restoredOwnerRebindDoesNotInventAPendingFadeWhenTheDisplayedGenerationAlreadyMatches+matchingRestoredOwnerRebindPreservesAnAlreadyCapturedPendingPublication,TestS3kResultsScreenObjectInstance#aizBossFlowDoesNotRepeatDisplacedOwnerEntriesInResultsWait'
r5_presentation_maven '-Dtest=TestPaletteUploadLatch,TestNativeWhitePaletteFade,TestSonic2DonorSuperStateRewind,TestS3kResultsScreenObjectInstance,TestS3kResultsKosQueueAndChildren,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils'
r5_presentation_maven -Pguards '-Dtest=TestTraceReplayInvariantGuard,TestTraceHydrateSwitchDefault,TestArchitecturalSourceGuard'
```

Final affected3511 completed15:37:27UTC,119/0F0E0S, Maven1m31/queue135s,
fresh Java25 compile on485+the three frozen diffs. Ten actual classes include
both LevelLoading packages36+7, donor4, bootstrap6, decoding3, results16,
Kos/children17, native-white14, upload8 and AIZ1skip8. Inspected XML has no
failures/errors/skips. Warnings include FAST missing-manifest→NONE, missing
measured Kos profile→deterministic estimate, collision-index negative control,
and compiler/JOML deprecations; no strict native timing qualification is claimed.
Focused guards40112 completed15:39:46UTC,83/0F0E0S, Maven27.766s/queue65s;
architectural72, trace-invariant10, hydrate-default1 in a fresh guards JVM.
All XML and skip counts inspected. Target last compiled the maintained repair
and tests; no restored baseline or temporary probe is left compiled. No new trace/native/capture,
R4 media repeat, broad run, Main/Root edit, integration or push. R1–R4 evidence,
refs, native artifacts and private config are preserved.


R5 terminal cleanup: inspected all13 class XML summaries including0 skips, then
consumed26 own Surefire files under the metadata slot alone; diagnostic directory
was empty, no archive or pass cache. All four Maven handles24274/51248/3511/40112
are terminal; no retained waiter or compiler remains. Main config hash/mtime
remain unchanged; owned config is a regular private0600 exact byte copy.
Documentation links/fences, identical skill mirrors and diff checks pass.
Normal seven-trailer local commit retains the reviewed and tested runtime bytes;
Root owns later composition/combined qualification, with no Main publication here.


### Slot large residual classification R6 — 2026-10-17

On preserved clean759acb131 before documentation, offline original-domain
analysis assigns both large remaining R4 components to Slot's foreground
priority-promotion owner, not cell traversal. Native971's578pixels are sole
SAT30/31 colored-wall frame9;974's267 are soleSAT19 peppermint frame3. All845
native SAT/pattern/CRAM pixels match original PNGs; all845 retained engine
wrong colors equal native low-priority, nontransparent Plane A. Their published
scroll-projected world positions lie exactly inside
`S3kSlotBonusStageRuntime.ensureForegroundGlassPriority`'s promoted
`$430..$497/$400..$467` square. Its runtime bytes remain R4-identical.
Pinned044 source, exact publication/CPU distinction, samples, full-component
counts and the independent production-render disconfirming question are in the
[existing bonus matrix](../validation/levels/s3k-bonus-stage-transitions.md#slot-large-residual-owner-classification-r6--2026-10-17).
Root received the concrete owner handback BEFORE any runtime proposal/edit or
new Slot execution. Engine visible-list/FBO descriptor metadata is not retained;
no new screenshot equality is inferred from source. No source/branch/tree/worker,
native/capture/test fixture or Root/Main change. R4 residual totals remain
599/295/41/52/57 and HUD carry difference stays unqualified.

Separate explicitly authorized R5 affected-consumer qualification on unchanged
759: job84980 exact
`TestFbzSandopolisTimelineHeadless#productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice`
passes1/0F0E0S at15:48:12UTC, Maven37.246s/queue64s. Same recorded
`r5_presentation_maven` environment/all3ROMs/current Main absolute wrapper/owned
cwd, with `-Dsurefire.forkCount=1 -Dsurefire.reuseForks=true`. Inspected its full
XML/test identity and consumed2 reports under metadata alone. No duplicated
baseline/broad/119-selection or runtime edit. Root owns Main803 attribution.
Authorized NORMAL request82303 on the same candidate is exactly
`-Dtest=TestLrzSszCheckpointRoutes,TestNativeCarriedPlayerArtBinding` with those
same flags plus `--normal`; terminal0,892/0F0E0S at16:03:46UTC,
Maven4m46/queue618s. Full populations888 checkpoint routes+4 carried-art;
inspected all XML/zero skips. Metadata94256 consumed4 reports961787bytes
after waiting for shared compatibility while holding the intended-tree lock;
no outer activity, cancelled request or target race.


Root-provided actual Main803 baseline96596 is892/0F0E0S at16:08:36.081607UTC,
Maven4m48/queue874s, with all779 failed Root case identities included and both
class sources byte-identical803..02b. This is independent provided evidence,
not this worker's execution. No repeated baseline is claimed or needed.

Next explicitly authorized normal request34577 is EXACT
`-Dtest=TestS3kBonusPortalMechanismProducts,TestS3kBonusStageReturnProducts`,
same recorded environment/ROMs/owned cwd/current Main wrapper,
`--normal -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true`. It completes
16:28:21UTC,993/142F0E0S,Maven11m27/queue748s, unchanged759 runtime/tests.
Full portal568 has142F (460.163s); return425 all pass (202.967s). All142
messages are identical publishedPlane byte differences, no pending/otherfield.
The existing bonus matrix records exact affected method names, shortcases[3]
(SLOT_MACHINE/tails/sonic,sonic/400/off) and[5]
(SLOT_MACHINE/sonic/sonic,sonic/800/s1), plus the actual20-element assertion
preview and its limits. No complete-array/span attribution is invented.
Root's original993 pending messages were all inspected despite its then-current
1000-detail/797-omitted results cap. Current851 pass/142 different-field fail
is bounded progress, not combined-smoke green.

**STOP new field honored.** No planned728 request (Gumball/Pachinko726 plus
the named LRZ2 gateway method's2 cases), additional execution, runtime repair,
new source hypothesis or baseline was submitted. Root owns short-pilot Main
attribution; no R2 cause is assigned. Root's independent four-original-image
visual inspection corroborates R6's Plane A attribution without adding a native
or engine picture. Foreground845 remains uncorrected; all five residual totals
and the separate unqualified HUD life bootstrap difference remain unchanged.

**Queued R7 technical gate:** exact verified task
`node:delegated-task:command%3Amcp%3Ae0f15981-d194-439f-81ab-b848692b7d95%3Athread-send%3As3k-gpt61-slot-rom-foreground-priority-owner-20261017-r7`
is preserved, NOT started in R6. After R6 accounting it may READ ONLY prepare
the actual Plane B publication/restore lifecycle question. No runtime edit, GPU
test or five-PNG capture until Root authorizes a concrete bounded published-plane
owner unit or clears that gate. No R7 branch/worker/tree was created.


R6 final accounting: all Maven handles84980/82303/34577 and metadata94256
are terminal. Inspected SOZ2 reports, LRZ/carried4 (961787bytes), portal/return4
(3578565bytes) were consumed under metadata ownership alone; diagnostics empty.
No raw archive/pass cache, temporary decoder file, additional request or source
change. Private config remains regular0600/exact bytes; Main config hash/mtime
unchanged. Docs-only links/fences/diff and unchanged reviewed runtime hashes
verified, normal seven-trailer GPT-6.1 local commit follows. R1–R5 branch refs,
results, originals and private config preserved; no Root/Main edit or push.


#### R7A passive single-pilot handback — no repair authorized

Exact followup task ordinal8:
`node:delegated-task:command%3Amcp%3Ae0f15981-d194-439f-81ab-b848692b7d95%3Athread-send%3As3k-gpt61-slot-published-plane-wind-causal-witness-20261017-r7a`.
Owned649 branch `bugfix/ai-s3k-slot-rom-foreground-priority-gpt61`, base333df955b,
unchanged759 runtime. Build55336 terminal0; ONE actual original pilot[3] witness
88468 terminal1,1/1F0E0S at16:51:04UTC,Maven28.729s/queue277s. No observer or
bootstrap failure. Exact temporary method, command, source hash, full46-byte
comparison and source/observation limits are in the existing bonus matrix R7A
section.81 observations/81 passivity checks,11 verified full8192-byte payloads.

Raw live PlaneB equals cached publishedPlane at every observed hook. Exact bytes
return immediately on restore but authority=false/dirty=true; first matching
replay publication replaces the actual raw array and changes46 EXTRA bytes outside
the identical legitimate row batch. Those46 persist after13 wind steps. Owner
identities/scroll/row batch match; no cache-only or full-width explanation.
Source relation: parallax restores retained bytes before later level restoration;
conditional geometry-reference invalidation can clear retained authority without
clearing restoredForRewind, then generic reset/ensure can rebuild before row writes.
No geometry-array identities/call stacks were retained, so that conditional
caller is not uniquely observed. Concrete handback is the retained-plane restore
versus later cache invalidation/ensure lifecycle, not an unqualified R2 assignment.

Smallest independent old-red question: production-order retained-plane restore,
actual level geometry-reference restoration/invalidation, postrestore reset and
one ordinary row publication must preserve untouched retained rows while rebuilding
geometry-derived caches; keep real layout/scene reset controls. Not executed or
implemented. Root-provided Main61811 passes5 original short pilots,0skips; its803
source lacks the publishedPlane key, so this cannot qualify retained-plane replay.
No duplicate Main run,728 request, runtime fix, GPU test, native or image capture.
R7 foreground845 priority remains deferred pending a separate concrete owner grant.

R7A final accounting: both55336/88468 terminal; owned temporary source removed
under intended-tree lock, journal/two reports/four witness class files consumed
metadata-slot alone (7 files/354004 bytes).
No raw archive, additional execution or runtime change. Last compilation included
the now-removed temporary witness; no fresh maintained-source compile is claimed.
Private config remains regular0600/exact bytes; Main config SHA256/mtime unchanged.
R1–R6 refs/results/native originals/captures and foreign jobs preserved. Docs-only
local commit; no integration/push, foreground845 unit still gated.



### Second Strike warning fixture physical ownership — 2026-10-17

Root02b's broad15826 exposed the sole new failure
`TestSecondStrikeBoss#childFirstAndParentFirstBarrelSlotsKeepEveryWarningPresentation`:
expected3/actual0 warnings. Root's matched Main803 method89643 passed1/0F0E0S
at15:35:54UTC and its diagnostics were consumed. This is not an inherited
baseline failure or a reason to restore Java parent references. R7 captures
`AizMinibossComponent.parentSlot` at birth; moving the same Java boss from its
original SST to70 leaves those physical parent3 words unchanged. The follow-up
owner selects the family at its current root slot, correctly finding none.
Native `loc_68A94` creates body/arm/barrels with `CreateChild1_Normal`, whose
`move.w a0,parent3(a1)` follows `AllocateObjectAfterCurrent`
(skdisasm044fa467,sonic3k.asm137334–137342/177010–177040).

Local merge4e65da92e7d538015468114cccc56160fde269d5 preserves kernel16b and
both complete append-only Plan streams. The test-only migration keeps a real
root-before-five-child graph. Ordinary unrelated allocations before child birth
are removed before warning admission, supplying real free holes before barrel
anchors; the dense graph supplies warning slots after them. No assigned slots
or relocated roots are used by either positive case. Assertions retain root
ObjectRef/slot, all five captured parent3 values, each warning's actual barrel
ObjectRef/slot, physical relative order and the pose visible at its own SST pass.
Both cases present all16 cues, emit one four-flame wave, run its short tail and
repeat the continuation twice after forced Java graph recreation. Comparison
uses the existing strict canonical comparison of every registered object and
game-state snapshot key. This fixture does not register the full terrain/player/
art world, use a cold campaign inlet, or supply native visual evidence. The
relocated-root scenario remains an explicitly invalid-inlet negative: captured
parent3 does not change and no warning or invisible follow-up wave is admitted.

Focused session3990 on4e65da92 plus test SHA256
`eda34c2c5660b75130095fdee847bac9e4ae5dbc74c2d901c64f0b655cb5a54a`
passed21/0F0E0S at15:48:40UTC: entire `TestSecondStrikeBoss`14,
`TestS3kAizMinibossGraphRewind`4 and `TestAizMinibossChildRetirement`3.
Queue245s and Maven27.255s are distinct; no broad/S3K4 repetition or new capture.
XML counts/skips were inspected and the owned diagnostics consumed. Exact command
from the owned tree, with `${OPENGGF_MAIN}` the unchanged Main workspace:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null \
  JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN}/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=${OPENGGF_MAIN}/s1.gen" \
  "-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen" \
  "-Ds3k.rom.path=${OPENGGF_MAIN}/s3k.gen" \
  '-Dtest=TestSecondStrikeBoss,TestS3kAizMinibossGraphRewind,TestAizMinibossChildRetirement' test -B
```

No runtime, allocator, reparent API, readiness/profile or service-count changes.
No trace frontier moves; existing route/viewport/donor/native coverage limits and
ending production caller/bus inputs/6+46/Hyper/PAL/PCM blockers remain open.
Root owns later composition and integration; this branch is local only.


### Canonical HCZ8329 skim logical-press owner — 2026-10-17

The [bounded owner evidence](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#canonical-returned-hcz8329-water-skim-input-owner--2026-10-17)
records original AD40 prefix21228 (observer success1/0F0E0S, live comparator218
errors through8330), actual raw/logical no-press state and the post-terrain/
pre-object launch. Native385D2 masks logical pressed bits; the HCZ skim owner
incorrectly tested held input. Independent5337 reproduces both wrong branches
as2/2F0E0S. Runtime change is confined to that owner's existing predicate.
Current source53d plus HCZWaterSkimHandler/test edits; unrelated native4fdf,
private config, kernel16b and warning53d refs remain preserved. No shared input,
movement, camera/counter/visibility, native/media, broad or ending readiness work.

Exact invocation shape from the owned tree uses unchanged current Main tooling
and original absolute ROMs, with the selection substituted below:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH \
  EGL_PLATFORM=surfaceless ALSOFT_DRIVERS=null JAVA_HOME=/usr/lib/jvm/java-25-openjdk \
  JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl LUA_BIN=/usr/bin/lua5.4 \
  python3 "${OPENGGF_MAIN}/tools/testing/maven_queue.py" --normal -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  "-Dsonic1.rom.path=${OPENGGF_MAIN}/s1.gen" "-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen" \
  "-Ds3k.rom.path=${OPENGGF_MAIN}/s3k.gen" "-Dtest=${SELECTION}" test -B
```

- Build-only99674 used `-DskipTests -Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical test-compile -B` (no tests),0/Maven23.656s.
- Original witness21228 selection `TestS3kCanonicalHcz8329Witness#originalAd40ThroughHczFirstVelocityDisagreement` additionally used `-Ptrace-replay -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical`; its JTO had exactly the single EGL token, with no additional CLI lag/driver override. No CLI EGL token was needed because JTO supplied it. Queue914s/Maven48.519s; partial evidence, not parity green.
- Old-red5337 selection `TestHCZWaterSkimHandler#heldActionAcrossActivationAndSustainDoesNotCreateJump+logicalEdgeWithoutRawHeldTriggersTheNativeJumpExit`,2/2F0E0S,queue882s/Maven24.283s.
- Focused9066 selection `TestHCZWaterSkimHandler,TestHCZWaterSkimExitOnCurve,TestHczWaterRewindStaticAdapters,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils` passed80/0F0E0S at16:39:02UTC,queue55s/Maven1m18s. Seven selectors matched eight classes because both level-loading classes share a simple name. Separate authority7703 passed25/0F0E0S (16:41:21UTC,queue20s/Maven29.089s). One postrow-only originalprefix9812 completed1/1F0E0S (16:42:13UTC,queue29s/Maven50.427s):8329vy0/no falsejump assertions pass,8330native velocity matches; a newly added unsupported animation0 diagnostic assertion fails on195. Live206=11physics+195animation,firstNonCameraPhysicsMismatch null,0warnings/bootstrap,54lag through8330. No automatic rerun/relaxation/source widening; Root owns retained-animation classification. Root subsequently authorized one required original maintained full canonical after classifying the zero-animation diagnostic mistake; terminal outcome is recorded below.

Short tests cover declared semantic input and the captured owner/player world,
not a natural route or new viewport/donor/native visual row. Full canonical
completion/giant-ring exit, the separate82EA/R14 camera question and ending
production caller/bus/6+46/Hyper/PAL/PCM acceptance remain open.

- Authority7703 selection `TestHardwareTimingAuthorityGuard` used the same ordinary environment in a fresh JVM;25/0F0E0S.
- Prefix9812 selection `TestS3kCanonicalHczSkimPrefix#originalAd40HeldSkimDoesNotLaunchThrough8330` used the original21228 trace-replay/single-fork/reuse/alphabetical environment, no CLI lag/driver override, JTO single EGL token. Its failed extra animation expectation is documented, not hidden by its successful8329 causal checks. The subsequent required maintained canonical49197 is recorded below.

Root disposition: existing R10/R11 already record195 animation through7135/7808,
first3475 sidekick17/1C. Current9812 stderr confirms that first field/head; no
full195 vector identity or precise maximum/all-field extraction is available
from compact reports. Remove temporary diagnostic code, retain its1F result,
then run exactly one maintained selection
`-Ptrace-replay -Dtest=TestS3kSonicTailsCompleteEmeraldRunChain#aiz1ToDoomsdayCollectingEverySevenEmeralds -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true -Dsurefire.runOrder=alphabetical`
with the original AD40 boot/lag driver and21228 environment/all3ROMs, no cutoff,
observer or additional assertion. No further fix/probe/slice/selection follows
its actual next frontier without a new handoff.


Required maintained canonical49197 used that exact original AD40 selection and
single-EGL-token environment, no cutoff/observer/extra assertion. On53dbe8fd plus
reviewed runtime99957690/test2dd28e9b it finished16:51:28UTC, queue151s,
Maven52.840s/JUnit27.372s, **1/1F0E0S**. Seg11 completes14208rows:
26153=23312physics+2841animation,0warnings/bootstrap,lag59; first non-camera
moves8329 y_speed0/-680→8717 sidekick_air1/0. Prior17194 same-window125612
(107498+18114) is a bounded aggregate comparison, not full-vector acceptance.
Earlier0/2/4/6/8/9 remain complete-zero;13 gaps have0failures, including actual
hcz_2→ss_6→hcz_3 (manifest giant_ring/stage_exit). The giant-ring exit is reached,
while the strict chain remains red. Seg13 is incomplete:63295=54642physics+
8653animation,0warnings/bootstrap,lag6,first287 y06D4/06DA, recent row3208;
walk loses ownership at BK2cursor88306 (LEVEL/gen10/zone1/act1; manifest expects
act2). Four future hardware edges3256/3257/5356/5357 remain suppressed unmatched;
no hydration, descriptor fitting or SS parity total is inferred. Full details
and exact reviewed hashes are in the linked audit. Stop: no further probe/fix/
selection; Root owns next frontier, composition and ending6+46/PAL/PCM limits.


### Composed source and shared-fork graphics prerequisite — 2026-10-17

After all orphaned batch66 children became absent, the inspected incomplete run
was acknowledged normally. Root239bf414e records its limits, not a pass.
Rootf5c3f9858 composes native LBZ/DDZ0cd and R15 camera evidence9bdf;
Root550066842 composes qualified LRZ/DDZ-flight00272 and ICZ evidence077767;
Roote74390c57 composes held-palette759/Slot painter289/R7A evidence5c59;
Root00c54ec82 composes logical skim0c7, warning fixture53d and the unused native
bus kernel16b. Every append-conflict resolution preserves both complete evidence
streams; skill mirrors agree. No Main write or integration acceptance follows.
The ICZ16 repair70e8 is separately qualified but not yet composed at this point.
The DEZ128 continuation candidate remains uncommitted/unqualified:99 focused and
29 guards pass, but its complete7-case route check has5 later death assertions.
Slot R7C's175 affected/108 guards pass while the original five pilots still fail
[3]/[5]; source cause remains under the separately bounded R7D investigation.

Actual Main24967, exact cold-Sonic campaign plus both boot-width cases, passes
3/0F0E0S at17:06:07.162791UTC (queue0/Maven58.216s/parent59.017s), with Main
HEAD, foreign status and config unchanged. The cold campaign reaches Mecha49728,
results50375 and DEZ release52150. Candidate cold death49613 therefore remains
unqualified. Isolated boot-width passes alone do not explain the broad errors.

A concrete shared-fork test cleanup defect was then found: TestSurfacelessEglContext
unconditionally clears the launcher-provided headless backend after each case.
The next HeadlessGameBoot consequently chooses GLFW without its null-platform
hint. Exact matched Main23165 runs that3-case class before the two real boot-width
cases in one alphabetical reused fork:5/0F2E0S, both errors Unable to initialize
GLFW, normal queue0/Maven26.438s, terminal17:14:30.091350UTC; Main state/config
preserved. This independent reproduction proves the leak, not the unrecorded
property value at the interrupted broad's constructors, and does not explain143
or the cold-route death.

Only the test cleanup changes: save the incoming property before each case and
restore that exact value afterward, including absence. No graphics/runtime owner,
queue flag, resource budget or production API changes. Root65482 on composed
00c54 plus that test delta passes the SAME alphabetical/reused-fork pair:
5/0F0E0S, class counts3+2, no skips, queue0/Maven1m16s, terminal17:22:20UTC.
Fresh Java25 compilation covers composed sources, but those five checks are not
combined smoke or a route certificate. All corresponding XML identities/counts
were inspected; Main baseline and Root result directories are consumed normally.



### ICZ R11 synchronous known16 repair checkpoint — 2026-10-17

Same98d/native01a1496e/branch from077767, exact R11 task registered separately
(ordinal12); R1–R10 untouched. Preserved e06 original128 migration, no new
observer/native/trace or broad run. Independent real-handoff old-red31728:
terminal1 17:08:14UTC, queue0/Maven24.467s,1/1F0E0S, first entry129 nativeword0
C131→0000 (test .276s/class .532s). Independent ICZ1secondary3D5958 gives
C131/C132/C133/C134; target SolidIndexes27A761 gives8/8, versus oldFF/FF.
Full559 retention, unknown30 absence and complete589 publication tail are
maintained assertions; old red stops at first native pattern loss.

Two-file runtime correction is frozen for Root independent source review before
corrected execution: synchronous executing predecessor only; immutable sparse
known8byte groups bounded by actual Block_table1800bytes, target ROM prefix
wins, current target collision tables decode. Validation-created placeholders
have no native pattern writer. Private geometry marker follows existing COW
arrays/whole-world restore; collision-only clones and existing opaque value
snapshots preserve it. No public snapshot/API/pin, generic capacity behavior,
async preparation input, queue ownership/readiness or physics change. Cold/
detached/prepared construction receives an empty16 predecessor. Corrected
focused selection will cover real publication with two forced whole-world
restores/replays in the new ICZ2 timeline, independent sparse/zero/collision/
value-snapshot provenance, existing deferred/overlay owners and mandatoryS3K4.
No qualified green claim yet; Root owns composed smoke and delivery.


R11 executed epochs: old-red31728 above; corrected45686 terminal0 at17:12:27UTC,
queue0/Maven1m06,77/0F0E0S/11actualclasses. OriginalICZ1 now reaches all128/16/
pattern/admission/claimed/publication/ordinary-load tail; independenthandoff1 and
sparse2 exercise two forced world restores/replays, target8/8 versus oldFF/FF,
known559/unknown30 and full589 payload. Existing deferred6/overlay3/nativeword4/
mandatory60 green, no skips. Frozen runtime1ec70e/fdefa5 reviewed independently.
Review found P2: opaque688 capture→temporary717 overlay→prefix restore left718
and falsely exported717. Exact newmethod9826 terminal1 17:14:47UTC,queue17s/
Maven24.073s,1/1F0E0S proves688/718. Minimum private correction restores saved
extent for owned opaque snapshots, preserves external six-word prefix semantics.
Final frozen runtime Sonic3k1ec70e427f97c3fa8da696b4ec8818d66e5d608e14af47aa273e5c5440bfab2f;
Sonic3kLeveld392aa6c8f807dc12b1d4787250ead0b33c3dea33efc0134516554a8316c3ab8.
Affected68955 terminal0 17:16:43UTC,queue0/Maven1m11,4/0F0E0S (sparse3 and
opaque-overlayrestore1), including temporary-tail/unknown-hole/COW/externalprefix/
twice-forced-world controls. Preserve77 as earlier construction/publication epoch;
no77 rerun. Production rg finds only AizIntroTerrainSwap precompute uses opaque
snapshot/restore, neither actualICZ method does; no ICZ handoff repeat warranted.

All commands use current Main absolute queue from own cwd with `--normal -Dmse=off`,
all3 original absolute ROM properties, Java25, EGL/nullAL/noambient/Lua5.4,
exact single-token JTO, singlefork/reuse/alphabetical (same flags as R9 command).
Selectors were exactly:

```text
31728 -Dtest=TestS3kIczRetainedChunkHandoff#synchronousHandoffRetainsKnownPatternsWithTargetCollisionUntilPublication
45686 -Dtest=TestS3kIczRetainedChunkHandoff,TestS3kIczAct1TransitionHeadless,TestSonic3kResidentChunkPatterns,TestSonic3kDeferredLevelResourceLoading,TestSonic3kLevelOverlayRewindIsolation,TestS3kNativeWordLayoutResidency,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
9826 -Dtest=TestSonic3kResidentChunkPatterns#opaqueRestoreRetiresTemporaryTailWithoutChangingExternalPrefixWrites
68955 -Dtest=TestSonic3kResidentChunkPatterns,TestSonic3kLevelOverlayRewindIsolation#restoreChunksLeavesPreviouslyCapturedChunkArrayUntouched
```

Normal `test -B`, no profile in these four runs. No extra observer/preparedAIZ/
trace/native/media/broad run. Inspected diagnostics consumed under metadata slot
alone (2oldred,22focused/P2,4finalaffected files); no archives. Latest target
contains maintained source compiled for the affected epoch, not an old observer.
R8 natural death/reload and full aux/config/native/BK2/cold/fullchain/combinedsmoke
remain open; async16 carry needs independent predecessor identity before extension.
Root owns composition/delivery. [Full bounded ICZ evidence](../validation/levels/s3k-icz-act1.md#r11-synchronous-known16-handoff-repair--2026-10-17).


Final source/API89633 terminal0 17:19:02UTC,queue0/Maven26.094s,5/0F0E0S:
ModApiSignatureSurface2 (.581s) and ArchitecturalSourceGuard3 (1.008s), fresh
`-Pguards` JVM, exact selector below with the same wrapper/environment/ROM flags.
No entire guard suite was repeated. Root independently inspected finald392aa6c
restore1000..1020 + sparse2680a205, accepted the bounded P2 resolution and all4
actual affected controls; no remaining material review objection, no extra round.

```text
89633 -Pguards -Dtest=TestArchitecturalSourceGuard#runtimeProductionCodeDoesNotReadDisassemblyAssets+productionGameplayCodeDoesNotBypassLevelMutationSurfaceWithRawLevelMutators+hardwareTimingAuthorityExceptionStaysDocumentedAndAgentGuidanceStaysMirrored,TestModApiSignatureSurface#candidateZeroSevenSurfaceIsPinnedToTheCurrentSurface+engineOnlyRuntimeHelpersDoNotLeakIntoTheZeroSevenSurface
```

All five own Maven handles31728/45686/9826/68955/89633 terminal/released; inspected
2+22+4+4 diagnostic files consumed under metadata slot alone, no archives. Main
configSHA2566abd111c…f7da/mtime_ns1792141742849921547 remains unchanged; own config
is regular/exact bytes, original ROMs/submodules/previous refs preserved. Target
contains maintained Java source compiled before this normal local commit (077767
plus exact frozen source), with no temporary observer; no postcommit rebuild is
claimed. New links/anchors, changed fence parity, skill mirrors and diff checks
pass. Whole-file frontier fence count845 was already odd at077767 and unchanged;
no unrelated Markdown repair. Local-only qualified owner commit follows under
normal hooks/seven parsed trailers/final GPT6.1 coauthor. Root owns integration/
combined smoke; no Main edit/push/tree cleanup or whole-act certification.


### Root current-source and stopped-witness checkpoint — 2026-10-17 17:40 UTC

Root composition `ace8f89c59e8a9de5a98dabe6eb8fb869cd9ad37` includes the
qualified ICZ16 `70e8d10168d92cc30781e4072dc456f9d48093b8` and EGL-test cleanup
`0d0c075ba987cf7fd7f330f3d910cb8d92e5c3e7`. Main remains `8030832d2`;
no integration or push. Root's last maintained compilation was the EGL pair
before the ICZ merge. Do not describe the combined current source as tested.
The current change-based dry plan against Main selects **3,527 candidate classes,
all ten smoke categories and fresh guards**. This was planning only, not a new
run or a release-only full suite. The interrupted 65/71-batch run is acknowledged;
its unexecuted tail and guards remain incomplete. A future current-source run is
required after the focused blocking owners are qualified.

Slot R7E request9026 finished 28/1F/0E/0S (17:30:12UTC, queue0/Maven1m17).
The same real Slot registration-order unit still loses untouched byte2048,
expected9/actual-79, after its level/map/animation/foreground prerequisites.
The other27 checks pass; dedicated encoded-layout geometry rebind is insufficient.
No fresh guards or original pilots were submitted on this red source. Nine owned
paths remain uncommitted in the isolated Slot tree. R7F authorizes exactly one
passive same-unit diagnostic to record actual flag-clearing and rebuild callers,
with source fingerprints separate from the retained candidate. It authorizes no
third source hypothesis, priority change, GPU check or five-image capture.

The DEZ128 continuation candidate remains uncommitted. Its ordinary/emerald team
routes pass, but the seven-case route selection has five later deaths, including
soloSonic60811, versus a passing matched Main solo control. Diagnostic41244
ended 1/0F/1E/0S at input60422: its own terrain observer dereferenced a legitimate
null sensor result during a checkpoint helper. No death60811 was observed.
The incomplete 64-step window contains only live states and is not evidence for
a kill/support writer. Temporary instrumentation and consumed diagnostics were
removed; the four previously accounted candidate/test/prose paths remain.
No automatic retry followed the postboot observer failure. The next source-only
question is a minimal rising-death writer/cause observation, before execution.

Cold-campaign R16 request42765 independently reproduced original death49613 on
committed `00c54ec82` (1/1F/0E/0S, queue0/Maven52.688s/JUnit30.105s,
17:32:52UTC). Its observer completed without error. The final terrain event sees
P1 alive; the first cloud-solid checkpoint already sees P1 dead. That brackets
the writer but does not attribute it to a cloud. CollisionTrace has no touch or
damage callback. Its 1,860 forwarded callbacks comprise474 terrain,693 solid
entry and693 solid result events. Shared touch damage is a source-supported
possibility, not an observed call. All63 sampled inputs already have zero rings;
no earlier ring-loss cause is inferred. No campaign runtime repair is qualified.

Read-only stock-core audit established a possible path to the unused Kos kernel's
initial CPU state without a native rebuild. The stock ELF contains `m68k.cycles`
(+280C, master clocks), `refresh_cycles` (+2810) and encoded IRQ-pin state (+28B8).
These are not cumulative debugger CPU clocks. Waterbox maps ELF PT_LOAD virtual
addresses while its owning host is active; synchronous execution callbacks retain
that mapping. Existing memory-domain readers demonstrate nested same-thread
monitor-scoped direct reads. This does not permit asynchronous readers or pointers
retained after callback/host disposal. The relevant data symbols are STB_LOCAL,
so the existing global-symbol resolver does not export them. A separate pure-leaf
PCM mapping is not the live core, and SaveState callback reentrancy is unproven.

The audit used retained BizHawk source pin427556b5ef3ac437eba754d90c5e7e9096c9a8df
and the hash-identified stock ELF. Before any adapter execution, authentication
must bind the actually loaded image, validated symbol ranges, an exported live
mapping anchor and monitor lifetime. The loader does not expose a consumed-stream
hash. A separate file hash alone is not that attestation. No adapter, emulator,
compiler or native execution occurred in this read-only audit. Even a qualified
initial-state reader would leave ordered per-instruction bus/IRQ/handler inputs
open; the unused kernel is not production acceptance or a fitted readiness budget.


### R16 cold Sonic campaign death bracket — 2026-10-17

[Exact command, source and primitive evidence](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r16-cold-sonic-campaign-first-death-bracket--2026-10-17).
Production00c54 is broader than failed Root02b and passing Main803; maintained
case/movie/inlet are unchanged. Sole actual42765 reproduces `cold death49613`,
1/1F0E0S, queue0/Maven52.688s,17:32:52UTC. Initial build PID outcome stays unknown;
7156 is preboot getter compile failure only; repaired build19431 passes.

63 observed inputs49551..49613/1860 collision callbacks retain an alive final
terrain pose19BF.2A00/061D.8600 (-339/-656), then an already-dead pose
19BF.2A00/0618.8600 (0/-1792) before the first cloud solid checkpoint. Direct
solid callback names are clouds and teleporter only. CollisionTrace has no
TouchResponse/damage callback: no exact kill writer/provider is observed.
Mecha state/ROM collision bytes and shared touch source route are recorded as
controls, not causal attribution. All samples already have rings0; the earlier
ring-loss/trajectory owner is outside this observation. Stop at the missing
first rising-dead writer/cause; no runtime fix or additional execution.
Temporary observer and owned diagnostics are consumed after durable facts;
local docs only, prior refs/native4fdf/private config preserved. No campaign
completion, native parity, regression-isolation or broad/delivery claim.


#### R7B genuine independent old-red — runtime design pending

Exact task ordinal9:
`node:delegated-task:command%3Amcp%3Ae0f15981-d194-439f-81ab-b848692b7d95%3Athread-send%3As3k-gpt61-retained-plane-rewind-invalidation-oldred-20261017-r7b`.
Same649/native01a14a19/priority branch from5c59, new ownership key
`gpt61_slot_plane_restore_r7b`; all prior entries/results/refs preserved.
Only existing TestLevelTilemapManagerRewindReset source plus these two docs change.
Normal current-Main-wrapper request30571, exact4 new methods/all3ROM/recorded
Java25 EGL/nullAL/Lua environment/single fork, terminal1 at17:04:16UTC:
4/1F0E0S,Maven23.972s/queue85s. Actual owner geometry-reference swap and derived
FG/pattern rebuild prerequisites pass; full8192 retainedPlane oracle failscycle0
byte619 expected-1/actual0, outside current rows0/1. Three no-retained/live-mutation/
real-scene-replacement controls all pass. Second partition cycle not reached;
no whole-world/canonical-pilot/native/priority/capture execution is claimed.

Bonus matrix R7B records exact selector/sourcehash and minimal owner design.
Preserve exact physicalPlane only for geometry-restore invalidation when BOTH
existing authoritative/restoredForRewind flags hold; still rebuild derived caches.
Ordinary/live/scene invalidation unchanged. Adapter subpackage access would require
a narrow nonModApi internal bridge plus package-private tilemap helper, not a new
public/protected method on either ModApi manager. This additional internal seam is
explicitly handed back BEFORE implementation. No runtime edits or further execution
until Root's bounded design decision. Original five pilots remain unchanged for
later whole-world validation; no full142/993/892/broad repeat. R7A conditional
invalidator caller is not uniquely attributed by this independent partition test.

R7B handback accounting:30571 terminal; both inspected reports27094bytes consumed
metadata-slot alone. No live owned request, raw archive or runtime edit. Main
config hash/mtime unchanged, private config regular/exact bytes. Reviewable owned
test+two-doc diff retained uncommitted at5c59 pending the runtime design decision;
no failing-test commit, integration or push. Last compilation includes these tests.


#### R7C STOP: independent owner unit green, same production pilots still red

Exact ordinal10/task and frozen3-runtime/test SHA256 are in bonus matrix R7C.
Same649/native/priority branch over5c59, newkeygpt61_slot_plane_restore_r7c;
all prior refs/results/privateconfig preserved. Authorized unannotated internal
bridge/package-private helper only changes level geometry-restore invalidation;
ordinary/live/scene invalidation/API/pins unchanged. Root reviewed exact4paths,
no material finding, no additional review execution. Source frozen throughout.

22973 passes175/0F0E0S,14 actual classes (13 simple names, duplicate loading
class selects36+7),17:10:48UTC,Maven1m22/queue0. All14XML inspected after fixing
an offline report-count assumption; no Maven repeat. R7B primary now completes
both partition cycles; three original negative controls plus added forward-retained
without-exact-restore control pass. Existing persistent/stateless/geometry rewind,
exact ModAPI pins and S3K4 green. Fresh85536 authority/source guards108/0F0E0S,
17:12:02UTC,Maven33.878s/queue0,all4XML inspected.

ONCE-only same five original short portal pilots6665 FAIL5/2F0E0S,
17:13:26UTC,Maven29.831s/queue0,class5.465s. Exact[3]tails/team/400/off and
[5]sonic/team/800/s1 remain at the original publishedPlane20-byte-preview family,
not a new pending/field. No full-array witness or full-world-two-cycle pass is
inferred. Root Main61811 passes all5 without the new key; it does not qualify
this plane state. The geometry-restore hypothesis is independently valid but
insufficient to close actual142 failures. Concrete invalidator/flag state in the
real restore/publication interval remains missing; no caller is assigned.

STOP/no tuning/new probe/replay/runtime edit or population/capture. Keep reviewed
candidate/test/two-doc diff UNCOMMITTED for Root's decision. No normal bugfix
commit or release completion claim before the required production gate passes.
Foreground845/GPU/five-PNG unit remains gated; broader pending populations,
combined broad tail/guards and whole objective remain unqualified. No Root/Main
source change, integration/push, tree/ref cleanup or foreign-job action.

R7C terminal accounting:22973/85536/6665 all released;38 inspected affected,
guard and pilot XML/text reports540590bytes consumed metadata-slot alone. No
raw archive or live owned request. All four reviewed source hashes unchanged;
Main config SHA256/mtime unchanged, private config regular/exact bytes. HEAD5c59
and six reviewable owned paths (three runtime, one test, two docs) retained;
new bridge untracked, no commit/push or cleanup of branches/native evidence.


R7D initial independent-unit execution50619 (17:21:19UTC,normal/all3ROM/env,
Maven25.395s/queue0) is1/1F0E0S, NOT a retained-plane old-red. New method
`TestS3kSlotPlanePublication#encodedSlotLayoutRestoreKeepsPublishedPlaneThroughTheRealWorldRegistrationOrder`
boots native Slot21 headlessly and verifies actual encoded-layout key, production
parallax<level<collision registration order, real retained history matching the
published-plane snapshot, missing-encoded no-op and same-level/new-map ownership.
It then fails a mistaken test prerequisite at line133: assertSame on map-data
arrays. Map's normal constructor allocates and copies data (Map.java:30–38),
so byte equality is the source-backed oracle; array identity is not promised.
Dimensions/animation/FG/final8192-plane checks are NOT reached. Source is unchanged
R7C candidate plus new test SHA256dd5282fa23a96d198f4f272e0ef17e87afac1c6acd7dc648bc0175bd8bfb7935.
Test error acknowledged to Root; stopped before test edit/rerun/runtime edit or
caller attribution. This is not proof of the actual142 caller or an old-red repair
qualification. R7C175+108 passes and2/5 production failures remain separate epochs.


R7D corrected independent-unit old-red59005 completes17:25:02UTC,
1/1F0E0S,class.576s/Maven24.142s/queue0. ONLY test prerequisite identity assertion
became byte equality; corrected TestS3kSlotPlanePublication SHA256
3dc6f7c12d7ff8586884b1aafaafc2323c9b09b8d65fb68d8b952ee99c029e19.
All actual Slot21 encoded-key/order/published-plane, missing-state no-op, current
map/dimensions, same animation owner/all logical counter+extra arrays and FG
geometry prerequisites PASS. Complete8192-plane oracle then failsbyte2048,
expected9/actual-79, in untouched row8 after one publication into rows0/1.
This is an independent source-backed owner interaction, NOT an actual-pilot
full46 identity or unique142 caller certificate. The source factory caches the
animation manager by same Level identity/zone; animator/cycler retain Level,
not a Map reference, and logical state already restores under pattern-animator.
Proposed three-path fix: dedicated existing internal bridge to package-private
LevelManager current-level geometry/dimension rebind, updateGeometry then R7C
restore invalidator; collision adapter switches only its post-scene restore call.
No ordinary setLevel/writeCurrentLevel/initAnimatedContent or API/pin change.
Root accepted this genuine red/design for a separately tracked bounded continuation;
R7C's175+108 passes and2/5 pilot failures remain separate tested epochs.


#### R7E implementation phase STOP: independent real-owner unit still red

R7E is an actual STEERED phase of existing ordinal11/R7D taskId (recorded in
bonus matrix), not a new task/native ordinal. Source freeze/new three-path hashes
76193c067/19bafeeb/cf8b1689, actual unit3dc6f7c1 and unchanged R7C source/test
hashes reviewed by Root with no material source finding. Only current-level
geometry/dimension rebind plus internal bridge and collision adapter's one call;
no ordinary scene/animation/API/pin behavior change.

9026 is ACTUAL28/1F0E0S/six classes at17:30:12UTC,Maven1m17/queue0,
normal/all3ROM/current Main wrapper/owned cwd/recorded JVM env/single fork.
All six XML/case populations inspected. Partition5+native-word/encoded scene4+
overlay3+API15 pass. SAME independent Slot unit still loses untouched row8
byte2048 expected9/actual-79, with all preceding key/order/geometry/animation/FG
checks green. This disconfirms the three-path rebind as a sufficient repair;
no unique remaining writer/caller is observed. No actual full46 identity claim.

STOP before tuning/new caller query or source extension. Fresh108 guards and
same five production pilots NOT SUBMITTED on this epoch. Previous R7C175/108
and2/5 pilot red stay separate; full populations/broad/native/picture/priority
scope remain open. Preserve nine owned uncommitted paths (five runtime, two tests,
two docs), HEAD5c59/R1–R7D evidence/native/privateconfig. No normal fix commit,
Main/Root integration or push before required unit/production qualification.

R7E handback accounting:9026 terminal/released; all12 inspected XML/text reports
150870bytes consumed under metadata-slot alone. No live owned request or archive.
Main config SHA256/mtime unchanged; private config remains regular/exact bytes.
Seven reviewed source/test hashes unchanged, last target compiled this candidate.
Nine owned paths retained uncommitted at5c59; normal hooks were not bypassed and
no commit/integration/push/branch/native/capture cleanup was attempted.


#### R7F evidence-only handback: independent cold unit lacks the exact restore

Exact fresh ordinal12/taskId and full source fingerprints/command are in bonus
matrix R7F. ONE passive6231 execution of unchanged independent inlet/assertions,
normal/all3ROM/env/current Main wrapper/single fork, terminal1 at17:42:23UTC,
1/1F0E0S,class.546s/Maven1m16/queue0. Original2048(9/-79) repeats, no diagnostic
error/retry.17 owner events+4 markers/30357bytes; contiguous and identical in
Maven/XML, below200/1MiB. No observation-side ensure/capture call or gameplay write.

Observed first collision-layout rebind enters auth=true/restored=false/dirty=false;
updateGeometry dirties it, restore-helper correctly falls through ordinary
invalidation and clears authority. Postrestore callback resets the derived cache;
next row publication takes dirty/shifted=false full-build, replacing raw array
and expanding64x32→208x32 before retained conversion. ZERO exact retained-restore
method events/keep-both-flags events; stable level/tilemap, STATIC/revision0/
wrap=false/fullwidth=false. This is NOT an invalidator after exact restoration.

The unit's snapshot-ownership prerequisite is insufficient: it captures world
`before`, then asserts on a fresh direct scroll capture, never the stored
parallax value. Source routes capture/restore by active currentZone (initial-1,
initialized by production parallax), while this cold fixture has no first frame.
Inactive handler is a source inference, not an observed private-field value.
R7D/E counts remain real but the claimed after-exact-restore old-red meaning is
superseded. Do not relax both-flags preservation to fit this inlet. R7A actual
portal restored=true evidence/142 failures remain distinct and unqualified.

Hand back smallest boundary: assert actual stored parallax snapshot owns the
nonempty plane after ordinary production parallax initialization and retained
history capture; no private flags/hydration/new fixture variant. NO automatic
inlet/runtime fix, second query/pilot/guard/fullpopulation/GPU/capture/priority.
All nine candidate hashes restored and verified before doc facts appended;
compiled instrumented owners will be removed, not credited as candidate build.
Root owns next bounded authorization. No commit/integration/push/foreign action.

R7F final accounting:6231 terminal/released; exactly10 owned diagnostic files
286110bytes consumed metadata-slot alone (Maven log,two reports,two source
backups,five instrumented compiled owners). Temporary source/log/counters/markers
are gone; all seven candidate source/test hashes exact, and all nine candidate
path hashes had matched BEFORE this authorized durable evidence append. No
remaining diagnostic archive/live job. Last compilation was instrumented and its
owner classes were removed, so a future authorized run needs a fresh compile.
Main config hash/mtime unchanged, private config regular/exact; HEAD5c59 and
nine owned candidate paths preserved uncommitted. R7G identity is queued
separately after this accounting; no R7G execution or runtime fix is started here.

#### R7G actual captured-plane inlet and bounded repair qualification — 2026-10-17

The R7F correction supersedes the claimed after-exact-restore meaning of R7D/E:
those cold independent-unit checkpoints did not restore a retained plane.
R7F observed21 events (17 owners/four markers), including no retained-restore
call; authoritative=true/restored=false correctly rejected preservation. This is
an inlet defect, not evidence against the strict both-flags runtime contract.
The original R7B partition old-red30571 remains valid: explicit physical restore,
actual geometry-reference swap/invalidation, then legitimate row publication;
cycle0 failed byte619(-1/0), so cycle1 was not reached on that old source.

R7G changes only that independent unit's inlet: ordinary
`ParallaxManager.initZone` receives actual loaded zone/act/camera before retained
history. Before restoring, it now asserts the ACTUAL stored
`before.get("parallax")` handler state is nonnull and its published8192 bytes
exactly equal retained history. The separate fresh-handler capture assertion is
removed. No other driver/oracle, runtime behavior, flag, provider, adapter or
plane bytes are injected. This prerequisite now passes in both matched runs.

The corrected test SHA256 is
`bf56ddd9c3e20862f71a0d88c0a991ad77d9ff03442babed8bcfe612566dfae1`.
From HEAD5c59, reconstruct ONLY the three R7E files to R7C: LevelManager HEAD
`6807b61ff1302c82ba523c5469292de3bacde803bad81bd825417370630d1731`,
collision adapter HEAD
`f9963d074da0941524cfd5e04ad855e5e101588efad8f46d8162dbef6b6fb06b`,
and R7C-only internal bridge
`086f81fa94aa2e797fd12c092a4016c160c69ed2107368726a1fffb0541a0f6f`.
Tilemapde1/LevelSnapshot6a9/partition06ea remain unchanged. Source-only diff and
hashes were checked before launch; the corrected test stays byte-identical.

- 77160 old-source terminal1 at17:53:09UTC:1T/1F/0E/0S,class0.487s,
 Maven1m16s,queue0. Actual stored-parallax bytes, registry order, encoded map,
 missing-state no-op, dimensions, animation owner/logical state and FG cache
 prerequisites pass. The final untouched-plane assertion alone fails at byte2048,
 expected9/actual-79. This is genuine R7E old-red credit, replacing59005/9026's
 invalid inlet interpretation.
- 50305 exact restored R7E terminal0 at17:55:39UTC:1T/0F/0E/0S,
 class0.614s,Maven1m15s,queue0. Same corrected test, complete8192-byte assertion
 and all owner prerequisites pass. The independent unit is one restore/row
 partition, not by itself a twice-recreated whole-world certificate.
- 82138 same FIVE ORIGINAL maintained short portal pilots terminal0 at17:56:41UTC:
 5T/0F/0E/0S,class6.919s,Maven31.620s,queue0. All five actual XML identities
 inspected: Gumball Sonic/Tails320 off; spheres Knuckles/Tails+Sonic352 off;
 Slot Tails/Sonic+Sonic400 off; Gumball Tails/Sonic+Sonic800 S2;
 Slot Sonic/Sonic+Sonic800 S1. Unchanged production driver retains both-cycle
 whole-world interior/return replay and Slot wind/fade picture/readback checks,
 including the previous Slot[3]/[5] failures. No new capture variant is created.

Every invocation uses currentMain absolute
`${MAIN_ROOT}/tools/testing/maven_queue.py` (MAIN_ROOT is the original main checkout) from owned649,
`--normal -Dmse=off -Dsurefire.forkCount=1 -Dsurefire.reuseForks=true`, all three
original absolute root ROM paths, Java25, exact
`JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`,
`EGL_PLATFORM=surfaceless`, `ALSOFT_DRIVERS=null`, `LUA_BIN=/usr/bin/lua5.4`,
no DISPLAY/WAYLAND, inherited other JVM environment preserved. The exact unit
selector is `-Dtest=TestS3kSlotPlanePublication#encodedSlotLayoutRestoreKeepsPublishedPlaneThroughTheRealWorldRegistrationOrder`;
the final selector is
`-Dtest=TestS3kBonusPortalMechanismProducts#shortPortalAndMechanismPilotsLoadAndReturnThroughRealBoundaries`.
Source frozen during each request; no direct JVM, queued duplicates or retries.
Warnings are existing compile removal/unchecked warnings, FAST no-manifest NONE
and pilot Kos deterministic estimates; no bootstrap/observer errors or skips.

Final runtime SHA256 values remain the reviewed R7E/R7C candidate:

| Owner | SHA256 |
|---|---|
| LevelManager | `76193c0671bb235a3b7b494f37d170b25c3b58ebfc0861644990ca7b48eee3bd` |
| LevelTilemapRewindAccess | `19bafeeb89887444a2a56af60fe4baa2cbc66483af0f5e56512626b7ff662310` |
| S3kCollisionLayoutRewindAdapter | `cf8b1689a5568c8d0bb67d3468fa49847c1f2b2a1eb995b82285b8f2151e1cf8` |
| LevelTilemapManager | `de1ab9483d5f0e580f3f61d830a418d57ae0400fadcf275d07f04dde19d7936c` |
| LevelRewindSnapshotAdapter | `6a9c14bf4d6fae4394cfd229ddadeac6a16e809d9b3b98aeb0bcd78a5b665a71` |

The nine-path source fingerprints (SHA256 canonical sorted JSON of base+path
hashes) are old551b5a98b74478de3d2e35325dc95bd6b0d8cb13614be331d0cbe2503c4bc236
and fixed516c2479f7e782524f7e89c7b6de37d24b8331556846c7bedf6ad5b0561fed4a,
before this documentation fold. Root's exact source reviews found no material
objection; no additional reviewer, Maven, guard or capture round is required here.

Coverage epochs stay distinct: R7C175/14classes includes both-cycle partition,
ordinary/no-retained/forward-without-restore/live/new-scene controls and API15;
R7C108 guards are NOT final R7E all-source guards. R7E9026 has27 passing cases
(five partitions,four native-word,three overlay,API15), plus its now-disqualified
cold-unit red. Reuse those unaffected results honestly; R7G adds the valid matched
unit and final five-pilot world qualification. Ordinary `setLevel` same/new,
live mutation and scene/load invalidation remain unchanged. The internal current-
level restore rebind retains animation lifecycle while updating geometry and
all cached dimensions; only authoritative&&restored physical PlaneB survives.
Derived FG/pattern/geometry caches and incremental baselines are still invalidated.

This qualifies the bounded owner repair, not a uniquely observed cause of all
142 product failures or all568 portal cases. No full population, complete route,
trace or RGB certificate is claimed. Lead combined3527-class smoke/guards remain
required. Foreground845 priority/GPU/five-PNG work stays separately gated.
Main/private config hashes and mtimes are unchanged; private config stays regular.
Normal local commit only; Root owns composition/current-source validation/push.

R7G cleanup: six inspected XML/text reports and three invocation logs across
three requests, plus three owned original-source backups, are consumed under
metadata-slot alone (final six files 281648bytes; earlier six files78250bytes).
No live request/helper/log archive remains. Last candidate compilation/execution
is82138; final source is exactR7E plus correctedtest.


### R17 cold Sonic campaign death writer — 2026-10-17

After R16 eeb79 completion, one original-case prefix48401 on unchanged production
00c54 plus passive logs reproduces colddeath49613:1/1F0E0S,
queue0/Maven51.878s,17:45:46UTC. Build-only66285 passed first.
[Exact source/run and real stack](../audits/trace/2026-10-14-s3k-monkey-dude-sst.md#r17-cold-sonic-campaign-rising-death-writer--2026-10-17).
Actual shared touch selects Mecha parent class (not secondary child), HURT,
NORMAL, hadRingsfalse/sourceX19C0, then applyHurtOrDeath→applyDeath→rising setDead.
The preassignment P1 pose19BF.2A00/061D.8600 matches R16's last alive sample.
Only7,745bytes of targeted logs; no collision observer/replacement or gameplay
writes. Temporary source/classes and owned XML/text consumed after facts.
This closes the missing engine writer, not upstream regression/native-death
causality. No runtime repair or additional execution; Root owns the next scope.
