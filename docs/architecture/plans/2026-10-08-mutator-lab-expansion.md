# Mutator Lab: the complete original catalogue across three games

## Outcome and base

Expand the existing Mutator Lab example and PR 215 to the original catalogue in
Sonic 1, Sonic 2 and Sonic 3 & Knuckles. The user explicitly selected all three
games. Preserve the lifecycle contract: enabling, disabling and each option
choose their own safe boundary. Existing settings widgets, atomic save before
admission, immutable publication, owner fault isolation and historical rewind
remain the foundation.

Starting Lab: `7f496fc183cadedeca43dc8192d7370b038c263a`.
Actual development base: `098053c4a01c2af283ca6797463bb5051442ef0b`.
Private intent composition: `f0e55f305` (no expansion behavior yet).
Main checkout and its unrelated dirt remain untouched. Update the existing
feature PR rather than create a second Mutator Lab PR. The original blueprint
is [the design authority](../designs/2026-10-07-mutators-blueprint.md); this plan
records the new implementation and any decisions resolved against current code.
Earlier prototype evidence remains attributed to its original source.

## Catalogue and admission boundaries

| Mutator | Toggle / option scope | Required behavior |
|---|---|---|
| Gravity | LIVE / LIVE | Bounded native fall acceleration; preserve jump impulse, native sequencing and excluded motion owners. |
| Ringfall Manipulator | LIVE / LIVE | 10–100% of the native recoverable scatter, optional hard cap; optional full-inventory spill past the native 32; losing inventory remains independent from spawning. |
| Big Head Mode | LIVE / LIVE | Enlarge reviewed head pixels about a neck anchor after native sprite admission; preserve body, feet, collision and camera. |
| Stealth | LIVE / LIVE | Hide native player art and optional tagged attached effects; retain gameplay, world effects, HUD and audio. |
| No Powerups | LOAD / LOAD | Remove selected semantic monitor placements before object creation; preserve placement identity and restore on later qualifying reload. |
| No Checkpoints | LOAD / LOAD | Disable death respawn banking, with checkpoint art and independent special/bonus entry still functional. |
| No Rings | LOAD / LOAD | Remove ordinary level rings and deny main-level ring awards before lives/audio/touch consumption; stage puzzles retain their own rules. |
| Game Speed | LIVE / LIVE | Slower and faster whole native steps with fractional remainder, latched input edges, responsive settings and explicit audio behavior. |
| No Special Stages | LIVE / LIVE | Deny semantic entry before player capture; finish an already admitted transition. |
| No Bonus Stages | LIVE / LIVE | Independently deny S3K bonus entry before capture; unavailable visibly in games without bonus stages. |
| Violent Explosions | LIVE / LIVE | Bound amplified native badnik-defeat knockback; no decorative explosion or unrelated hazard mutation. |

All definitions default disabled. LIVE applies on explicit Resume; LOAD on a
qualifying full assembly/restart/death reload/stage-return assembly. Preview,
checkpoint restoration, editor swaps and seamless handoffs do not masquerade
as full reloads. A policy revision is admitted atomically before its consumers.
Installation of new Java code remains boot scoped; live installation is separate
from the requested catalogue expansion.

## Ownership and dependencies

Root owns the public typed policy contract, host composition, per-world adapter,
common settings/title catalogue, creator example registration, support matrix,
integration, final validation, existing PR and refreshed promo delivery.

Three bounded source investigations establish independent implementation seams:

- Admission: semantic monitor/ring placement, all ring award owners, death checkpoint
  eligibility and special/bonus admission before player capture.
- Physics: native gravity, deferred scatter allocation, resolved defeat rebound,
  fractional whole-step pacing and one-shot input ownership.
- Presentation: reviewed native head masks/anchors and post-admission player/effect
  presentation. Native pixels come from ROM; metadata is not replacement art.

Implementation consumers start only after their shared engine-owned ports and
immutable values are pinned. Each worker receives an isolated worktree and exact
file ownership. Shared runtime packages cannot depend on concrete `mods`
implementation classes. Keep new orchestration in focused collaborators rather
than grow GameLoop, LevelManager or playable-class source ratchets. No dependency
on the separate Multigame or Hardened feature branches is introduced.

## Known risks and acceptance

- Zero-step slow motion must retain a press until a simulation body consumes it;
  pause/menu/fade and external trace/movie contracts must remain well defined.
- A monitor removal decision belongs to placement admission, not reward suppression
  after a player has stood on its solid body.
- No Rings owns all ordinary award producers, including attraction, lost rings,
  prizes and direct S3K awards; resetting a counter each frame is rejected.
- Checkpoint death eligibility differs from stage-entry and stage-return banking.
- Special/bonus admission is checked before hidden/control-lock writes, including
  sanctuary challenge selection, with a final transition defense.
- Big Head requires frame-specific reviewed masks/anchors. Whole-body scaling,
  guessed topmost-piece selection, doubled original heads and gameplay resizing
  are rejected. Unsupported art must remain visibly qualified.
- Stealth includes elemental/insta shield and Super/Hyper attachment overrides;
  subject identity must not conflate two identical character codes.
- Scatter count is latched before deferred allocation and captured by rewind.
- Existing preferences are preserved or explicitly migrated/reset per definition;
  a new schema must not silently discard unrelated valid settings.

Acceptance requires actual native behavior in all three games, sensible bounded
settings, safe enable/disable/reload, per-world isolation, historical restore and
forward replay. Supported native character/art and stage cells are stated in the
matrix; unavailable options are explained in the menu. No green claim follows
merely from registration, rendering one screenshot or collecting aggregate counts.

## Consumer coverage obligations

These are delivery obligations, not claims that queued checks have passed. Native
assembly uses the existing World REV01 S1/S2 and locked-on S3K ROMs. Their CRC32
and SHA-1 were independently rechecked in this task; no ROM copies or links were
created for the examples. A canonical capture-tool step remains one native tick
and therefore cannot certify the interactive Game Speed setting.

| Boundary | Required evidence |
|---|---|
| Per-game catalogue | Eleven definitions, game-specific labels/cues, preserved old preferences, long-list/monitor-option scrolling, unsupported cells explained, successful and failed-save ownership. |
| Placement and rewards | Native S1/S2/S3K assembly, unchanged placement identities/slots/order, selected monitor filtering through a decorated registry, ordinary ring touch/attraction/prizes/direct grants blocked before consumption, active stage interiors retained. |
| Death and stage entry | No checkpoint death restore; independent post art and stage triggers; denial before player capture; captured entry completes once despite a later toggle; restore replays within the same owner; retired worlds reject held permits. |
| Scatter and defeat | Native stock control; bounded scatter count before deferred allocation/rewind; actual badnik rebound at 150–300% and cap; horizontal/native collision ownership preserved. |
| Slow and fast presentation | 25–400% whole native bodies, zero-body input retention, pause/configuration/fade/rewind interruption, canonical tools unaffected, optional SMPS service and PCM rates at 50/60 Hz, actual normal-loop footage. |
| Head and Stealth | Reviewed ROM masks at 100/150/200%, native bank/SAT admission and priorities retained, ball/unreviewed art fallback explained, attached effects hidden without removing world effects, actual all-game scene frames inspected. |
| World history | Policies, pending stage permits, fractional pacing and unconsumed player edges restored together; upcoming historical speed affects only scheduling before its tick; preferences and host quarantine remain outside history. |
| Composed delivery | Current compiled candidate pin, SDK/Javadoc/artifact verification, mandatory S3K bootstrap/loading/decoding/AIZ cases, affected trace domain, full actual-base categories and fresh unchanged-budget guards, explicit inherited assertion/skip comparison. |

The native-tick configuration journal does not promise replay of the original
wall-clock duration of settings menus or several edits made before one native
tick. Modified interactive recordings remain a separate contract; no trace or
movie row is used to supply gameplay state. Native game mechanics and assets
remain authoritative when every mutator is disabled.

## Verification and delivery

Run meaningful focused producer/consumer tests and real-ROM assembly/play/rewind
checks, the four mandatory S3K bootstrap/decoding/loading/AIZ checks, compiled
candidate API pin plus SDK/Javadoc/artifact checks, affected native/trace fixtures,
and the actual-base combined category selection with fresh structural guards.
Inspect the actual plan and preflight Java21/Lua5.4/PowerShell before launch.
Shared physics, pacing and public contracts require the normal broad gate.
Compare concrete failure identities and full messages with a qualified matched
baseline, including skips. The completed actual-main `5d1ff9b` run
`20261008T053400Z-a3c0531f` selected 3027 ordinary classes and produced 3025
reports/26422 cases: 27 inherited assertions, no errors and 62 causal skips;
86 fresh guard reports/672 cases passed without skips. The previous FBZ-to-SOZ
timeline failure now passes, so the older 28-failure list is superseded.
Read-only comparison from `5d1ff9b` through the task's actual `098053c4` base
found no production Java/resources, POM, hooks or runner delta. Changed SOZ
capture tests have separately recorded bounded baseline/current qualification.
This establishes the runtime and failing-test baseline; it is not a claim that
the whole `098053c4` test source was executed by the earlier full run.
Queue Maven normally and preserve unrelated work/locks/processes.

Finish implementation and documentation before a fresh hands-on Claude Opus5.5
polish round. Integrate accepted polish, verify changed behavior, then delegate a
separate refreshed promo using real final-source footage and observed sound.
Update the existing PR title/body around the complete implementation, link it to
this thread and report the PR/video links with remaining material limits.

## Progress

- Private tree composed with pinned develop; all-three-game scope confirmed.
- Shared typed policy contracts and capability scopes implemented. Normal queued
  request `96231` completed on the unchanged core source at 10:36:28 UTC:
  `TestMutatorExpandedPolicies`, `TestMutatorSessionState` and
  `TestMutatorPreferenceStore` produced 29 passes, no failures/errors/skips.
  The immutable dependency patch was byte-identical before export. The retained
  all-file fingerprint named in an intermediate status was unavailable, so no
  all-file hash comparison is claimed.
- Fresh compiled candidate surface: 20543 lines, SHA256
  `9b08d6a092ab0b6f25044fc5fe8fcf50e045de206f037c848f24313136a6afd1`,
  151 additions and no removals; unpublished candidate descriptor unchanged.
- Catalogue/UI commit `fdf5d4bd2` passed 51 focused cases, zero skips,
  including all three catalogue registrations and the existing real S2 world
  revision/recreation replay. Compiled core pin remained byte-identical.
- Physics owner `dcbc5ea45` has passing observations for 355 selected cases
  across the initial run and repaired fixtures, zero skips. This is not one
  green 355-case invocation. The rejected ringfall oracle removed surviving
  Obj37 slots on a later spill; the native pool intentionally retains them.
  The correction asserts old identities plus the new capped scatter, without
  changing production behavior to fit the fixture.
- Admission owns 64 exact source/test paths and has passing observations for
  265 unique focused cases, zero skips, including all three actual ROM assembly
  and death/re-enable cells. Its compiled API has zero delta. The pre-commit
  source heuristic incorrectly includes a record method body and treats an
  explicit formerly implicit no-arg constructor as a signature change. No
  hook bypass or synthetic pin change was used. Root applies the exact patch
  within the coherent composition with the genuine presentation API addition.
- Presentation owner `340595d81` passed 77 final affected cases, zero skips;
  43 unchanged singleton/resource guard cases passed in its earlier run.
  Native art has 223 reviewed head masks across 553 frames, with explicit ball,
  empty and unreviewed fallbacks. Final 112-pixel review cells include uncropped
  150/200% output. The rejected S3K A1 polygon included a raised glove; reviewed
  native pixels narrowed the mask and a body/glove preservation assertion now
  guards that edge. No guessed pose inference or replacement artwork landed.
- Presentation introduces four genuine candidate signatures: its permanent
  native atlas reserve, scalar fragment-bank lookup and two scalar renderer
  status methods. Its own 20547-line export matches the candidate pin, +4/-0
  against the core. The candidate descriptor remains unpublished 0.7.
- Root is composing policies, stage permits, fractional pacing and pending
  native inputs into one world rewind adapter before player/object recreation.
  Twelve new all-game service cases and current-leader fallback guidance await
  the composed compilation. SDK, domain, final captures and combined ordinary
  plus fresh guards remain outstanding. Fresh Opus polish and promo follow the
  verified implementation, not the isolated child results.
- First coherent root request `62794` completed 2026-10-08 13:02:37 UTC:
  25 fresh class reports / 268 passes, no failures/errors/skips. This includes
  the twelve all-game composite service cases, six long-catalogue/leader-guidance
  cases, actual native assembly, existing S2 world recreation, pacing/audio,
  presentation and required S3K bootstrap/load/decoding/AIZ checks. Normal
  verify completed SDK/Javadoc/artifact verification. All 11835 frozen files
  were unchanged. Actual composed export remains 20547 lines, SHA256
  `ad9a9f3b9314bcbd40efe6a2b3d01b50a2451ead3b1139854e1207aae0131ffc`.
  This is focused pre-framework composition, not a whole-suite claim.
- Published `d740b7a0f` incorporates substantial creator framework/API/caller
  changes. Its actual-main `bf7c56e` run selected 3056 ordinary classes,
  produced 3054 reports / 26514 cases, 27 completely matched inherited
  assertions, zero errors and 62 identical causal skips; 87 fresh guard
  reports / 674 cases passed. The content-admission successor has separate
  32 Python and 137 Java policy checks. Root will intent-compose this actual
  published destination and verify its real compiled surface and runtime seams
  before final capture/domain/full-candidate qualification. No new full
  baseline is needed; comparison uses complete identities and assertions.

- Framework composition main-source compile completed normally at 13:54:22 UTC:
  3817 production files, no source deltas across 11980 frozen inputs. Actual
  compiled candidate export is 17714 lines, SHA256
  `57fadd857339b253bd5fa9e5c42c57a09e53d92b57f58453d5cea15c87bdee6e`,
  +442/-0 against published `d740b7a0f`; descriptor remains candidate 0.7.
  This compile did not execute JUnit or artifact verification.
- The expanded sprite access exceeded its structural budget by one effective
  line after the framework merge. A named engine-only `SpriteGraphicsAccess`
  bridge preserves each sprite's bound graphics context without a playable
  class getter. Direct and hidden creator calls and context rebinding have
  focused regression coverage; unchanged structural budgets still require a
  fresh guard invocation.
- Source audit established a material Game Speed gap: native special/bonus
  interiors silently remained at stock speed. Completing interactive stage
  pacing requires native activity queries, exact mode/provider/load boundaries,
  retained controller-sample admission (including S2 live lag) and special
  journal/rewind advancement. A mode-only OR is rejected. Implementation and
  real-provider verification remain outstanding; no stage-speed support is
  claimed by the preceding level-pacing results.

- Current framework-focused request `42401` completed 2026-10-08
  14:51:31 UTC: 31 selected simple class names, 32 fresh reports, 331 passes,
  no failures/errors/skips. All 11982 frozen inputs were unchanged. The native
  wrapper retains catalogue/world-provider services in all three games; forbidden
  shared service/decoded-patch transactions remain poisoned. Bound sprite graphics
  survive context rebinding and direct/hidden creator calls are rejected.
  SDK/Javadoc/package artifact verification completed normally. Re-export after
  the bound-graphics bridge remains the exact 17714-line pin above. This is
  focused composition, not a full candidate or fresh structural-guard pass.
- Canonical input authoring round-tripped 2353-frame S1/S2 and 5353-frame S3K
  title/catalogue recipes. S3K adds a thirty-second native entry/restart input
  margin and longer normal-window waits; no game readiness or physics is forced.
  Scene/control/PCM observations are now recorded below; none certifies interactive
  Game Speed. No Checkpoints prose describes death ignoring the native bank while
  post presentation and independent stage triggers continue to work.

### Native capture decorator and creator boundary follow-up

The first current-package title-first walkthroughs (`02f194c2`, all three native
ROMs) exposed a tool contract defect: `DevelopmentPatchLoader` applied all three
explicit game patches. Three nested Lab screens let the outer ACTIVE screen draw
over Sonic 1's SEGA/intro presentation. Production registration already scopes
`stockScenePlans()` by game. The capture loader now selects that same concrete
plan, respects the launch/team predicate and supplies the explicit patch context.
The original captures are diagnostic, before this repair; they are not accepted
final title footage. Sonic 1's authored route also contains a native enemy death.

The example package's two `NON_API_ENGINE_REFERENCE` warnings came from global
`GameServices` access after the framework candidate narrowed its public surface.
Native support now reads the donor bit through the supported `PatchContext`
configuration supplied at apply. A host screen factory copies four native SFX IDs
and binds the current prepared world without returning mutable engine services.
Its existing world lifetime gate prevents retired screens from admitting changes
or playing cues into a replacement world. Package validation now treats warnings
as errors in the maintained compiler/package regression. Request `49527` passed
10 cases but skipped three native render controls because the launch omitted ROM
properties; it is not a 13-case pass. The corrected original-ROM request `91760`
passed all 19 native render/API cases with zero skips. Request `17052` subsequently
passed the packaged three-game and injected-versus-ambient donor disagreement
regression with zero skips. All 11983 frozen inputs were unchanged. Strict package
validation reports zero findings. The genuine compiled candidate export adds
only the host factory: 17715 lines, SHA256
`99a29b708a7841737c1bfd5fa5401fa6f8228925795849bf8f867b31d4d6c2fa`.
These checks do not imply a full candidate, fresh guards or audible menu cues.

### Native scoped capture evidence after creator-boundary repair

The frozen dirty production snapshot compiled by `91760` correctly chooses only
the selected native game decorator. Canonical GPU/PCM captures for S1, S2 and
S3K traverse the native title, Help, common settings, reviewed Big Head poses,
Stealth and restoration. The capture driver advances one canonical native body,
so this is not interactive Game Speed evidence. Configuration hold intervals
keep player position, velocity and camera constant. The S1 authored route dies
natively; it is not a death-free walkthrough.

Matched stock/No Rings opening inputs remove the native ring groups in all three
games; the EHZ maximum changes from2 to0 and AIZ from9 to0. The GHZ route collects
no stock rings, so it supports visual placement removal rather than award denial.
Matched No Powerups frames remove the GHZ ring monitor. A 528-pixel EHZ capture
shows the shield monitor removed while nearby rings, Sonic and terrain remain.
The AIZ lower route never shows its upper monitor, so those identical frames are
not visual evidence of No Powerups; actual all-game assembly tests cover the
policy and re-enable load.

An input-only EHZ damage comparison at frame1382 carries two rings, then loses
the full inventory in both runs. The100% Ringfall setting with cap enabled and
maximum1 changes scattered-ring pixels while native state through the hit is
identical. The first authored cap attempt entered the wrong option after Back
reset the catalogue cursor; the corrected input uses eleven Down presses. No
production behavior was changed for that authoring error.

Neutral700-frame title controls match authored PCM before the first menu input.
S1 navigation first contributes a difference at600 and S3K at601. S2 navigation
at600/602 produces no PCM difference; Confirm contributes at604. These are
observed offline SMPS contributions, not speaker/device or isolated-waveform
certification. Preserve the S2 navigation issue for the hands-on polish pass.

The first real-window default attempt finds the owned Engine window, then the
native hub-to-S2 transition crashes at the eager `GameServices.module()` read in
`GameLoop.stepPresentationFrame`, before recorders begin. This is an owned
presentation-entry regression, not a successful desktop capture or a diagnosed
window-manager failure. The stage-pacing owner will add a no-current-world control
and resolve the module from the existing context only after its frozen request
drains. All own Engine/helper processes and Pulse modules were cleaned.

### Published successor composition: `17ae561ad`

After the creator-boundary commit `774e2edf1`, the actual published destination
advanced from `d740b7a0f` to `17ae561ad`. Its 37 paths add bounded generic ROM-part
cues and Sitar feedback/network behavior, a shorter SOZ controller fixture, and
experimental GraalVM research. This is not a documentation-only successor. The
private intent merge retains both Mutator and ROM-part cue API descriptions.
There is no conflict in Lab/native pacing consumers. Normal request `82365`
completed at 18:25:46 UTC: all seven selected reports were fresh, 49 cases passed
with no failures/errors/skips, and SDK/Javadoc/artifact verification completed.
All 11,995 frozen source files were unchanged. Actual compiled export has 17,717
lines (SHA-256 `0d03969dba73973ba1f1a574e4961466cbc47673f63dfb520a7f92c38bcc95c8`),
byte-equal to the candidate pin: 443 own additions and zero removals against the
published 17,274-line destination. This establishes the previously provisional
union of the factory and upstream cue additions, not the full candidate gate.
Final physics/rewind composition and full candidate gates remain outstanding.
Prior source-specific captures retain their original frozen-production attribution.

The stage worker's native reproduction `43032` separately completed on frozen
source `871e597`: eight cases, five assertion failures, one fixture error and no
skips. Actual S2 older-keyframe resimulation reproduced a pacing remainder changing
from 50 to 0 at 75% and 150% despite matching native comparison state and sample ordinal.
A real retained-context hub test reproduced the eager `GameServices.module()`
startup exception. These authorize bounded engine-owned remainder-history and
inactive-context repairs. Pulse/main/stock-lag controls had used standalone input
bindings rather than the Engine supplier, and the ownership fixture nested mock
stubbing; those failures do not establish production defects. Corrected controls
are queued as `27621` before further input/ownership repair claims. This stage tree
is based on `02f194c2`, so composed `17ae` runtime qualification still follows integration.


### Native stage and admitted-input history completion

Stage commit `7cca6aef357153908dd990d1af6e7e024c266ccc` extends pacing to
interactive S1/S2/S3K special stages and native Slots/Gumball/Pachinko. Native
startup, initial ProcessSprites, results, death, fades, pause and external
canonical ownership remain unchanged. A mode/world/level/provider/entry change
ends the current fast budget. Readiness uses native semantic state rather than
the presence of a provider or a shared banner: S3K PERFECT is playable.

Correct Engine-bound controls in request `27621` established the retained tap,
main-level released jump, 50-point remainder and no-world defects before repair;
stock 100% lag and PERFECT controls passed. Request `92041` then passed the eight
regressions, admitted-input/history alignment and unchanged consumers: 253 cases,
251 passes and two new Gumball/Pachinko setup failures, no errors/skips. The
readiness refinement lets one canonical native setup iteration finish before
modified gameplay. Request `33905` passed all 147 selected cases with no skips;
108 unchanged passes from `92041` are retained, yielding 255 unique focused cases,
not one 255-case invocation or a whole-suite pass. Exact final stage source SHA
is `145342e43028c9fb8c5f97b230cbb62af2aff633eb2a52d4c1455615dc6b7671`.

An aligned engine-owned metadata ring captures delivered immutable P1/P2 input
and post-tick pacing state. Native acceptance ordinals distinguish actual S2
ReadJoypads sampling from a lag iteration. Replay retains BK2/menu/debug input,
uses stock fallback when metadata is absent, and restores only within the same
live world/level/provider lifetime. Held rewind discards fresh host edges while
preserving restored historical ones. Older-keyframe 75/150% and released main
jumps now replay correctly. The native hub no longer eagerly resolves an absent
module through a retained retired context. No trace comparison fields hydrate
gameplay and no native arithmetic or structural budget was tuned.

The internal pacing SPI trusts concrete engine-classloader implementations; it
is not ROM provenance for arbitrary classpath test doubles. Concrete same-loader
acceptance, foreign-loader denial and the real owner-bound creator proxy are
covered. Inherited native getters retain native provider identity. One bounded
read-only review matched the frozen source and found the setup readiness gap and
this precise trust/coverage caveat; no additional reachable history/input defect
was established. Slots remains non-rewindable. The stage's actual compiled
17,714-line export is unchanged; root's factory and cue union still needs the
fresh composed 17,717-line check.

Published `913c5a351` adds only native packaging tools/workflow/prose beyond
`17ae561ad`; engine/test/API/POM/hooks/runner inputs are identical. Private merge
`7f6a238ce` preserves both native-semantic and image-retention pitfalls. Root's
stage intent composition has no textual conflict. Combined native Lab startup,
API/SDK, mandatory S3K, domain and full actual-base gates remain pending, followed
by fresh Opus hands-on polish and a separate refreshed promo for PR 215.

Combined request `20639` completed 2026-10-08 19:44:17 UTC after 1707 seconds
of normal queue waiting and 115 seconds execution. All 22 exact selected reports
were fresh: 198 passes, no failures/errors/skips, including retained-context hub,
native stage/older-keyframe, all-game world services, title/save/overlay/package,
composed audio, API/SDK and mandatory S3K controls. All 12,029 frozen inputs were
unchanged. SDK/Javadoc/artifact verification completed. Actual compiled export
is the same 17,717-line candidate SHA `0d03969dba73973ba1f1a574e4961466cbc47673f63dfb520a7f92c38bcc95c8`,
byte-equal to the pin; the internal stage/history changes add no creator surface.
Normal-window/PCM, affected trace and full candidate/fresh-guard gates were
pending at that checkpoint. The compiled build records the pre-commit dirty intent composition; its
source fingerprint, rather than cached window title metadata, owns attribution.


### Normal-window native controls

The completed composed source `e239433b9` ran through default-WM Engine.loop
walkthroughs in S1/S2/S3K; no Engine window/input changes or frameless fallback
were needed. Actual 640×448 movies decoded 7078/7818/9862 frames for S2/S1/S3K,
with real stereo 48 kHz device PCM and clean application/recorder/private-sink
teardown. The native-game matrix records observed controls and precise limits.
S3K master selection uses Sonic+Tails; canonical solo captures remain separate.

Two S1-labelled recipes selected too early and actually stayed in S2. The
launcher opens the shared package's declared S2 development base automatically.
Finishing its intro and returning with Escape before native game selection
reaches S1/S3K correctly. The hub defaults to S2 again after Return; maintained
recipes explicitly reselect their game. This corrects input authoring without
changing stock startup, physics or readiness. S2's restart still catches a fade;
S1 rings 0 before LOAD admission follow native hurt. Neither is presented as
No Rings proof. S1 also reports its native GHZ Edge Wall mapping 0xE8DF warning;
the mapping loader and constant are unchanged by this task, and no matched
runtime attribution or whole-act art qualification is claimed.

The parity owner's mandatory normal 3060-class run
`20261008T194559Z-9deb33e8` is terminal at frozen `863683b092` against
`f5de9524a9`: 3058 reports, 26570 cases, 26 owner-attributed inherited failures,
zero errors and 62 literal inherited skips; fresh 87 reports/674 guards pass
without skips. Evidence-only successor `5662ad2c2291` is unpublished. Incoming
remote `d4993a730724` includes engine/API changes; the owner is composing it
privately and maintains the main/local and remote-publication hold. Root retained
the complete light 26-failure/62-skip table attributed to tested 863. Its sole
absent CLI failure is owner-attributed to inherited `LD_LIBRARY_PATH`, not a
source fix. This does not establish incoming/final-destination equivalence.

### Completed native domain checks and comparison method

Normal queued profiles `trace-replay` and `trace-replay-r7` completed on clean
`afb16ca18` at 21:46 UTC. All 12,029 frozen files stayed unchanged; exact wrapper
PIDs were gone and no owned Python/Java/native/ffmpeg execution remained before
releasing the freeze. Ten r6 reports contain 26 cases: 23 pass, three assertions,
zero errors/skips. Three r7 reports contain three passes, zero errors/skips.
S1 GHZ/special, both S2 EHZ segments/special, S3K Sonic+Tails special and Slots,
and Knuckles Slots/Gumball/Pachinko pass. The three full AIZ/AIZ-slice/HCZ assertion
strings literally match historical clean2fc evidence: 57 errors/first20302
animation0→5, 99/first25589 animation0x13→5, and 4699/first9482 air1→0. Final
published-destination attribution remains outstanding; this is not a whole trace
profile pass or an actual-final-base qualification.

The repeated task-local assertion comparator is preserved as
`tools/testing/compare_category_outcomes.py`, with synthetic controls for exact
identities, failure kind/type/assertions, skip causes, incomplete/omitted/duplicate
evidence, capped assertion recovery and the narrowly named SSZ blob exception.
It consumes existing bounded summaries, requires explicit baseline provenance,
and changes neither Maven selection nor baseline reuse authority. Complete
Surefire outcome inventories remain a separate, stronger all-outcome contract.
The initial task probe's implicit local reference/exclusion was rejected for the
maintained tool; absent failures always require explicit review.

No main or foreign-request actions are authorized by this private expansion.
Final published-destination composition/domain attribution and candidate/full
guards, Opus polish, refreshed promo and PR 215 delivery remain outstanding.

### Qualified private parity composition

The parity owner qualified private `94bbd3bda56c3dcc81cfcc89a750b8955cb14dc6`
against canonical published `d4993a7307241bf90f004e0d7cf90936f075cf46`: normal
run `20261008T232550Z-db7081e2`, 3068 selected/3066 ordinary reports/26598 cases,
26 fully matched inherited assertions, zero errors and 63 literal matching skips;
fresh 87 reports/674 guards pass without skips. The extra skip relative to older
bases is an explicit incoming OpenGL 4.1 renderer availability condition. Clean
`0135fac91da0f85f4ac4d91c641f41acd88a7d18` changes only prose from that tested
composition. These are owner-qualified upstream results, not expanded Lab results.

Root intent-composed that exact private checkpoint from clean `cf2507d4e`; both
prose conflicts preserve the Mutator launch hazard/catalogue and incoming parity
hazards/Starfall catalogue. The playable death-radius correction auto-merged and
was checked against its native owner; mutator policy remains intact. The fresh composed
compilation/export and SDK verification now establish the incoming scene/image
API union. The normal focused composition checks death, stage pacing/admission/
rewind, all-game policy/presentation, native results, trace sidecar decoding,
scene/API consumers and mandatory S3K controls. No expanded full
candidate, fresh whole guards or final published-base qualification is claimed.

Parity main is integrated and held at `b317e94ebdce60c6f81553113543295c75b1d826`
against pinned published d499 for its mandatory actual-main qualification. Root
keeps main/remote publication untouched and waits for the final published SHA and
light complete assertion/skip table before final expanded-candidate selection.


The exact root normal invocation completed at 2026-10-09T02:00:16Z after 3443
seconds of queue waiting and 117 seconds execution: 39 actual fresh XML suites,
348 cases, 347 passing, no failures/errors and one explicit
`TestSceneRenderer#changingBatchSizesPreservesEveryPixelAndStreamingUpdates`
skip (`Assumption failed: OpenGL 4.1 unavailable`). All 12136 frozen source inputs
were unchanged. Both S3K level-loading classes were deliberately selected by
fully qualified name; bootstrap, decoding and AIZ startup controls pass without
ROM skips. The API/SDK/Javadoc and package artifact verification completed in
the same normal `verify dependency:build-classpath` invocation, with original
absolute S1/S2/S3K ROM properties and `-Dmse=off`. Exact selector, command and
case inventories remain in the task's outside light summary.

The actual compiled candidate surface has 17739 lines, SHA-256
`e08a807c0ab1c1ed5dc7d7fb3f4258227a45c9d7835c94268f9701abbb89aef1`, and
is byte-equal to the current pin. It retains all root APIs and adds the 22
incoming lines; compared with private0135, the expanded Lab adds443 lines and
removes none. Descriptor candidate0.7 remains unchanged under ordinary pin
regeneration policy. This is focused composition evidence, not a full expanded
candidate or fresh whole-guard pass. The exact queue wrapper is gone and no
own-worktree Maven/Surefire execution survives.

Read-only stage-capture preparation established two limits before native
measurement: the canonical movie caller cannot qualify interactive Game Speed,
and its renderer lacks a native special-stage branch. A configured outside
sampler must declare direct-level boot (the normal hub initializes GL), retain
actual configuration-bound input, use the normal Engine interactive caller and
one outer audio owner, and only observe native clocks/epochs/input/remainders.
A declared host LIVE edit inside an already-admitted stage is distinct from
physical menu footage: the current Lab overlay opens only during LEVEL. Real
window stage rendering and headless native clock/PCM observations remain
separate gates. The completed measurements below supersede that preparation-only limitation; canonical captures still do not qualify interactive pacing.

Actual main b317/base d499 admitted at 2026-10-09T01:56:57Z as
`20261009T015657Z-e417e53f`; the adjacent owner holds main tracked inputs, HEAD
and publication through terminal and delivery. Root private composition neither
changes those holds nor requests another baseline. Final published-destination
comparison, native stage evidence, expanded full candidate, Opus polish and
refreshed PR215 promo remain pending.


### Interactive native stage measurements and reusable observer

At clean private `0e2167ce8`, the registered packaged Lab was observed in 48
bounded native cells. These use the shared `HeadlessGameBoot` hidden-GL setup,
`DevelopmentPatchLoader` before world creation, configured P1 input, the actual
LIVE save/admission and semantic manager stage request. Direct level entry,
solo Sonic, no donor and S3K intro omission are declared setup. Neither trace
comparison data nor logical/movie inputs supply state. One interactive
`stepPresentationFrame` and one Engine outer audio boundary run per sample.

| Cells | Observed result |
|---|---|
| 24 clock/input cells: S1/S2/S3K special and S3K Gumball/Glowing Sphere/Slots, at 25/100/150/400% | All 768 measured outer rows match native journal ticks and fractional remainder, using the actual incoming phase; 32 outer rows produce 8/32/48/128 native ticks. Configured A input is retained through zero-body or lag rows until native acceptance. Bonus V-int deltas match native body counts. World/provider/entry epoch remain stable. |
| 12 audio-follow controls: each stage at 25/400% | Native clock, input and gameplay streams match the no-follow control, and each outer audio packet remains 800 stereo frames at 48 kHz. All twelve measured PCM windows differ with nonconstant AC. This proves an audio contribution, not isolated pitch/tempo metrology or physical speaker output. |
| 12 semantic denial cells: each stage before entry and at outer index9 after admission | Six pre-entry requests remain LEVEL for 120 outer steps. Six later LIVE revisions retain the admitted stage/world/provider/epoch and match the positive control's native observations for the entire 32-row bound. This is a host admission probe, not an in-stage settings-menu walkthrough. |

Three additional native title-to-level controls settle through the actual title,
press configured Start, reach native control and open the Lab overlay with
configured input; the 32 held outer rows preserve the world frame. They do not
measure a stage or certify physical desktop input. All sampler JVMs exit and
release their owners. Useful CSV/PCM and compact source/command/ROM/identity
summaries remain in the explicit task directory; consumed temporary logs are
removed.

The first configured-Engine sampler was rejected: presentation preparation
replaced its supplied headless backend, and it had not performed boot-time mod
registration. Source inspection found that launch seam inherited, not an
expansion regression. The maintained hidden-GL boot supplies real module
registration and audio ownership without changing Engine startup. A second
rejected assumption treated every native stage entry as fractional phase zero;
S1/S2/S3K special and bonus boundaries need their observed incoming phase. The
corrected control comparison passes without changing gameplay or fixtures.

The reusable observer is preserved as `MutatorStageProbeTool`, with syntax-only
argument tests and [launch/setup limits](../../../tools/media/README.md#native-mutator-stage-observations).
It emits observations when a native owner ends before its requested bound, so
exit zero alone is not certification. A fresh externally bounded JVM, measured
completion phase and stable owners remain required. The normal five-selector `verify dependency:build-classpath` completed at
2026-10-09T03:26:19Z: five fresh suites/17 cases, no failures/errors/skips,
unchanged 12138-input fingerprint, package/SDK/Javadoc/artifact verification
complete. The actual compiled surface remains the byte-equal 17739-line pin
`e08a807c...`. A fresh JVM of the maintained tool then repeats the S2 quarter-speed
control: 32 outer rows/eight native ticks, configured A retained until acceptance,
and one 800-frame stereo PCM packet per outer. Expanded full-candidate guards
remain a separate gate.

Normal Engine.loop clips use actual X11 press/release, scoped Pulse output and
an explicitly owned frameless mapping workaround. Saved 150% profiles remain
semantically unchanged in the corrected six-stage recipe. S2 halfpipe, S3K
Blue Sphere and all three bonus interiors are visible; Gumball returns to AIZ
within the clip. The short S1 clip stays washed out and lacks continuous focus observation, so
it is rejected as rendering proof. An independent bounded current control uses
observed owned focus through all neutral waits and an owner-thread read-only
`Engine.update` observer after the unchanged update. The native stage renders
after four seconds, reaches results and returns to Green Hill by thirty. Its
698 stage rows show the same graphics/session fade, no pause, successful reveal
and 904 accepted native samples; the saved 150% profile remains unchanged.
This establishes a working native path without a gameplay/shader fix. The old
clip's exact transient cause is unproved; no “150% freezes fade” claim is made. Debug shortcuts
are declared setup and bypass semantic entry denial; the separate native
manager probe above owns that gate's evidence. No physical HID/speaker or
universal window-manager claim is made. Earlier clips with erroneous early
hub navigation or edited Gravity settings remain explicitly rejected controls.


The adjacent parity owner reports actual-main `b317e94eb`/base `d499` terminal
at 2026-10-09T03:23:31Z: 3068 selected/3066 reports/26598 ordinary cases,
26 fully matched inherited assertions, no errors and 63 literal causal skips;
87 fresh guard reports/674 cases pass without skips. This is source-qualified
inherited-failure evidence, not a green whole-suite claim. That owner subsequently
published the prose-only successor `019dd454b0d63b10a1d0585450bb28f34e360c04`
and released the hold. The source-attributed full negative-case table is linked
from the stock-parity audit; this branch's actual-base gate uses that publication.


### Final published-base run and owned repair frontier

Published develop `019dd454b0d63b10a1d0585450bb28f34e360c04` is the
prose-only successor of the adjacent owner's qualified actual-main `b317e94eb`.
The owner's complete 26-assertion/63-causal-skip table is in the stock-parity
audit. The expansion's unchanged `1217399ecfd8e7042cf8ddc6cec74d48056e0b8b`
ran the normal full selection against that actual destination as
`20261009T073723Z-e6dc65ee`: 3108 selected classes, 3106 ordinary reports,
26878 cases, 33 failures, two errors and 158 skips (4975.65 seconds).
Fresh guards completed 88 reports/675 cases with seven failures and no errors
or skips (241.33 seconds). The source fingerprint remained unchanged.
All 26 inherited full assertions and 63 inherited first causal skip lines match;
seven additional assertions, two errors, 95 additional skips and seven guard
failures block handoff. The run was inspected, consumed and acknowledged;
no passing full-suite or readiness claim is made.

The guard messages identify the new probe's singleton/backend reads, a generic
mixer fallback in the fractional session path, concrete graphics-to-gameplay
head-mask/replay dependencies, one new sprites-to-util dependency, and an owned
fixture's misleading object-update parameter name. Three original team members
own disjoint narrow repairs in fresh worktrees, preserving the original child
source and histories. No guard baseline, allowlist or size budget is relaxed.
The owner-bound deferred-load, Eggman's Sky menu and two golf title assertions
are separately reproduced against a matched clean `019dd454` control before
attribution; creator quarantine remains active.

Prerequisite investigation found that the lead tree deliberately has no ROM
aliases. Some older fixtures still open relative `s2.gen`; donor configuration
also retained relative ROM names. Injecting primary absolute paths through
`JDK_JAVA_OPTIONS` does not satisfy those reads and adds a launcher notice to
fresh-JVM stderr. It also reaches a shell test that explicitly rejects that
environment variable. A maintained optional `--rom-directory` now passes verified
original filenames as individual Maven arguments without links or Java launch
injection. Relevant fixtures use the existing game-specific ROM helper instead
of checking an additional alias. The legacy S2 helper retains explicit legacy
selection precedence and otherwise accepts the game-specific property.
An ignored private configuration names the existing original ROM files and
catalogue directory; no user configuration or ROM is changed. Gameplay/timing
assertions remain untouched. These repairs still require normal focused and
final composed verification.

The comparison tool's first run remained invalid because Surefire placed a
JUnit abort stack in its skip-message attribute. Its new bounded parser accepts
only a complete JUnit abort causal line followed entirely by stack frames;
chained causes, arbitrary multiline text and capped messages remain invalid.
It preserves new skip identities and changed causes as blocking differences.
No invalid run is retroactively presented as a maintained comparison pass.


Bounded root prerequisite checks used normal queued Maven at the unchanged
expansion source plus the owned fixture/tool repairs. The first 31 selectors
produced 209 cases: 202 passed, two assertions failed, two errors and three skips.
The remaining seven selectors, after binding secondary donor paths and the
fresh golf catalogue explicitly, produced 23 cases: 22 passed, one assertion
failed, no errors or skips. The complete identity intersection proves that all
95 previously additional skip identities now execute and pass in these focused
checks; a final combined run still owns broad completion and attribution.
No ROM aliases were created and no gameplay assertions were changed.

A separate clean published `019dd454` control executed the nine previously
additional ordinary identities with the same original three ROM properties,
tracked-default private configuration, no Java launcher injection and inherited
`LD_LIBRARY_PATH`. It reproduced the two Tails donor errors, donor HUD palette
assertion, both golf title assertions, shell launch assertion and Eggman's Sky
menu assertion exactly. The deferred creator-load assertion and fresh-JVM CLI
case passed on the control. Only the deferred creator-load failure establishes
an expansion runtime regression in this matched check.

Secondary-ROM fixtures now bind the same provided original file to their
runtime catalogue after test-state reset; the primary `@RequiresRom` fixture
alone did not populate that catalogue. The fresh golf boot's standalone
configuration likewise names the supplied S2 ROM. The shell security fixture
launches each positive or negative arm with its declared trusted environment,
then adds the specific injection under test. It still rejects loader injection,
including a newly explicit `LD_LIBRARY_PATH` negative control. Production
security and ROM loaders are unchanged. All of these corrected cases pass.
Eggman's Sky's unchanged `BatchMode`/`SurfaceMode` assertion still reproduces
literally on both the clean destination and this focused candidate; it is a
bounded inherited finding, not a repaired gameplay claim.

The maintained Python safety checks complete 248 cases with no failures or
errors and one expected absent-preserved-report skip. Tool preflight succeeds
with Java 21 and explicit Lua 5.4. These are tool/fixture checks, not engine-wide
qualification. Runtime, producer, graphics and full fresh-guard composition
remain the lead's delivery gates.


## Checked load failure and native recovery

The final ordinary candidate at `1217399ecfd8e7042cf8ddc6cec74d48056e0b8b`
exposed a deferred creator-load regression: the extracted `LevelLoadAttempt`
returned `CallbackAborted` directly where `LevelManager.loadLevel` had preserved
its declared `IOException` boundary. The matched clean
`019dd454b0d63b10a1d0585450bb28f34e360c04` control passed that same owner-bound
loader assertion. Its Eggman's Sky menu assertion and both golf development-boot
assertions failed identically to the candidate; the golf failures occurred at
the patched-module prerequisite check before title input. No example or input
behavior was changed to hide those inherited failures.

An abort from the actual deferred `InitStep.execute()` now has a host-constructed,
final checked load carrier
that retains the original abort object. Module/profile construction and
policy/roster admission aborts remain direct. The frame and creator fault boundaries
accept only this carrier, directly
or as the immediate cause of the existing plain runtime load wrapper; the fault
boundary also handles its own private checked-callback wrapper. Arbitrary cause
chains remain untrusted, and fatal failures retain their existing escape behavior.
The bonus-entry and ending-demo IO fallbacks explicitly propagate this trusted
abort instead of swallowing it. Restoring `IOException` without those recovery
paths was rejected because it would lose the original creator's fault or let a
load consumer take its ownership. Quarantine and native arithmetic are unchanged.

The current matched reproduction used the original three absolute ROM properties,
private tracked-reference configuration, no ROM aliases, no Java option injection,
and the inherited native-library path. Its four identities produced four assertion
failures with no errors or skips; only the deferred-load identity differed from
the clean control. The initial inherited-main-configuration arm is recorded
separately and is not treated as a matched default control. Focused repair
validation and the root's combined fresh ordinary/guard gate remain separate.

The normal-lane repair check used
`maven_queue.py -Dmse=off -Dtest=TestLevelLoadAttempt,TestOwnerBoundGamePatch,TestModContextAndFaultBoundary,TestEngineModCallbackAbortBoundary,TestGameLoop,TestModGameplayPolicyFaultBoundary test dependency:build-classpath`
with those same explicit ROM properties and environment. It completed at
2026-10-09T10:05:45Z: six fresh selected reports, 160 cases, no failures/errors/skips,
and unchanged source fingerprint `8a39084efffc506d33f1f3dc7a356a82a80dd0936ebcb27cc434d46c86e86fb0`.
Old unselected Eggman/golf XML reports belong to the earlier reproduction and
are excluded from that focused result. The genuine export from this tree's
compiled classes and its own dependency classpath remains byte-equal to the
17739-line candidate pin (`e08a807c0ab1c1ed5dc7d7fb3f4258227a45c9d7835c94268f9701abbb89aef1`),
with zero added/removed signatures. No synthetic pin or guard change is needed.
The unchanged GameLoop source budget remains 3381; the repaired source is 3372
effective lines. The actual change-based plan selects all 3108 ordinary classes
and fresh guards for the root's composed candidate; this focused pass does not
replace that gate or certify the three inherited example failures.

Root source review of `0606c7a25eacb416f28c8e71a1a8007cd485db3c` found that
the first recording catch also enclosed module lookup, profile lookup and
step-list construction. That broader recording was rejected before integration:
the catch now surrounds only `InitStep.execute()`. The historical 160-case pass
above remains attributed to its exact source. An affected-only normal follow-up
(`-Dtest=TestLevelLoadAttempt,TestOwnerBoundGamePatch`) completed at
2026-10-09T10:13:12Z with two fresh reports/26 cases, no failures/errors/skips,
and unchanged source fingerprint
`01e99ef0b7c6f21c29579b27b6dcea47f9e0ee371d78873f8afe3ce8de007eb9`.
Its three new negative cells preserve the exact direct HostAbort from the module
supplier, profile getter and step-list constructor. The unchanged original
cancellation control and real deferred creator-load/consumer ownership tests pass.
The final compiled API export was rechecked after this compile and remains the
byte-equal 17739-line pin. No broader repeat was submitted; the root combined
ordinary/guard gate is still required.

## Verified repair composition at published develop 8668

The private lead intent-merged published
`8668a901216717d8f3696945a95bbfe8e628b652` as `ea9edfa167c42fd606531a75ec264569adb9a2ce`.
The two prose conflicts retained both the Mutator methods and incoming creator
links. The incoming owner-bound scene SFX contract, Eggman's Sky voice bank,
Flappy Tails example and terminology guard remain intact. No main-workspace
change or feature publication is implied by this private merge.

The normal 47-selector composed focus at working source `e5300754bc` plus the
staged 8668 merge completed 47 fresh reports/518 cases: 517 passes, one assertion,
no errors or skips. Every selector reconciles, including both distinct S3K
level-loading classes; all 60 mandatory S3K cases pass. The remaining assertion,
`TestEggmansSkyScene#productionMenusRememberSelectionAndJournalDoesNotChangeExpedition`,
is `expected: <BatchMode> but was: <SurfaceMode>`. A clean exact-8668 one-method
normal control reproduces the identity, exception type and entire assertion
literally. Both sources remain unchanged. This bounded control is separate from
the recorded whole-suite baseline; it does not add a failure to its totals or
claim a whole-8668 pass.

Separate normal `-DskipTests=true verify dependency:build-classpath` completes
binary, SDK, Javadoc and artifact verification; tests are deliberately skipped.
The actual compiled public export has 17,740 lines/1,840,912 bytes, SHA-256
`b16ac37ab871df34d5c9db37f282846e8eb50e7c7126190e900a069e34130101`,
byte-equal to candidate0.7. The sole addition to the earlier composed export is
the incoming `SceneAudio.playSfx(String)` method; no signature is removed.
The candidate descriptor is unchanged. Normal full fresh `-Pguards test` then
completes 88 reports/675 passing cases, zero errors or skips, including all seven
originally failing guard identities. Guard allowlists and size budgets are unchanged.

Two bounded native S2-special controls use those actual composed classes, the
unchanged verified creator package and original absolute ROMs. At 25% speed,
each advances eight journal ticks over 32 outer frames, accepts five input
samples including the native A tap, and emits exactly 800 stereo frames per
outer frame. Owner identity and all clock/input CSV columns match between
audio-follow false/true; their nonconstant PCM differs. The maintained probe
does not render stage images, and this is not desktop, speaker or pitch-metrology
evidence. The true control's native JVM exits zero and records complete evidence
and cleanup, but its wrapper exits one after an agent metadata write appends an
invalid JSON suffix to the lifecycle registry. That original exit is retained.
After the narrow registry repair, the lead independently executes all 14 final
assertions against each saved control and verifies matching clocks/input and
differing PCM. No capture or source test is rerun to repair bookkeeping, and all
owned processes/groups are absent.

The stronger recorded destination baseline is the voice owner's actual-main
`0103b9bdc880c142301073fba9024750ccf4f1c2`, run
`20261009T044919Z-b2966220`: 3,070 selected/3,068 reports/26,609 ordinary cases,
26 complete inherited assertions, no errors and 63 literal causal skips;
87 fresh reports/674 guards pass. Its delivery record states full equivalence
to the parity audit's negative-case table. Independent Git comparison shows
that production Java/resources, POM, API descriptor, hooks and category-tool
inputs are identical through published 8668. Incoming Flappy example/tests and
the bounded terminology-guard successor have their own recorded focused checks.
This is source-qualified reuse with explicit limits, not a whole-8668 full-suite
claim. The final normal candidate plan selects all 3,115 ordinary classes and
fresh guards against actual base 8668; that combined run remains required before
implementation handoff, Opus polish, promo and the existing PR215 update.

## Completed full candidate and bounded skip-projection repair

The final normal candidate run `20261009T130202Z-cdeaf173` completed at clean
`af7b51865b344852ff7d545c268a63c7c4e593e3` against published 8668. All 3,115
selected classes reconcile to 3,113 ordinary reports/26,933 cases: 26,843 passes,
27 assertions, no errors and 63 skips, in 5,154.23 seconds. Separate fresh guards
complete 88 reports/675 passing cases, no skips, in 243.01 seconds. Source hashes
are unchanged. All 95 previously additional skipped identities execute and pass;
all 60 mandated S3K cases and 218 mutator/native-pacing cases pass without skips.

All 26 recorded baseline assertions match their complete kind/type/message, and
all 63 complete first causal skip lines match literally. The additional Eggman
menu assertion is the same identity/type/full `expected: <BatchMode> but was:
<SurfaceMode>` message reproduced by the separate clean exact-8668 control;
that bounded attribution does not change the full baseline's counts. Only the
previously verified named SSZ blob hashes and exception prefix are normalized.
The run is consumed and acknowledged, with its exact processes absent.

Qualification remains blocked by an evidence projection defect: the maintained
comparison returns invalid because 32 Infinite Sonic skip stack envelopes reach
the 4 KiB cap. Their observed first causes match, but the capped tail cannot prove
that only stack frames follow. That invalid verdict is retained. The producer
now validates the entire uncapped JUnit abort stack before retaining its full
causal line and bounded projection metadata. Unknown/chained/malformed tails and
overlong causal lines remain invalid; the comparator is unchanged.

Four regression controls cover message/text XML, long valid stacks, invalid tails
past the cap, long or non-abort causes, and changed/new skips. They reproduce the
cap failure before the fix and pass afterward. The Python safety suite completes
252 tests with one expected skip; actual Java 21/Lua 5.4/PowerShell preflight
passes. This change is confined to runner summarization, its tests and prose,
without Java, selection-policy, POM, workflow or hook changes. A single normal
bounded Infinite Sonic check will qualify the 32 affected records against the
unchanged fully tested runtime/test inputs. No unchanged whole-engine rerun or
new baseline is required for this projection repair; supplemental evidence must
remain separately attributed before implementation handoff.

The bounded follow-up at clean `35e831e08076b52d56a5eda805f25abffe5e6edd`
completes one normal `TestInfiniteSonic` invocation: 238 source-reconciled cases,
206 passes and exactly 32 expected skips, no failures/errors or ROM skips.
Each uncapped abort envelope has 64 complete Java frames and 5,701–5,725
characters; all 32 projected causes and identities match the full-run observations
and qualified baseline literally. Runtime/resource/test/POM/hook/API inputs are
identical to the completed full candidate, and source/config hashes are unchanged
during this 98.564-second execution. Raw XML, the bounded log and owned temporary
directory are consumed and deleted; exact processes/groups are absent.

A new explicitly supplemental light comparison combines the completed full
candidate with only those separately measured 32 causal records. The unchanged
comparator returns valid exit 1: all 26 full baseline assertions and all 63 causes
match, with the sole difference being the separately controlled Eggman menu
assertion described above. Its bounded clean-8668 attribution remains separate
from the full baseline counts. The original invalid comparison is byte-preserved.
This qualifies the implementation with inherited failures; it is neither another
full execution, new fresh guards, a whole-8668 baseline pass nor a green suite.
No verification blocker remains before the requested isolated Opus 5.5 hands-on
polish. Promo, existing PR215 publication and accountable cleanup remain pending.

## Opus hands-on polish (round 3) and ring-scaled Big Head

Isolated worktree `feature/ai-mutators-expansion-opus-polish` from clean `7655c0d86`.
Inspection used title-first GPU `GameplayCaptureTool` captures (canonical one
tick per input frame, offline PCM) in all three games, then real `Engine.loop`
window walkthroughs through the maintained helper with explicit hub reselection.

Established issues and fixes:

- Backing out of a mutator's options returned focus to the first catalogue row, so
  a long list lost its place (and How to play/Configure returned to Start). Back now
  lands on the row that opened the page. The maintained BK2 scripts and window
  walkthroughs were re-counted for this; their earlier recorded evidence remains
  attributed to the earlier source.
- Row boundary text said "full restart / death reload" on the title, where the
  notice correctly said Start, and its "/ Enter: options" suffix was clipped in play.
  Rows now name Start before play and "restart or death" in play, with the live
  confirm label. Unavailable rows named a boundary that cannot apply; they now show
  the first sentence of their reason, and a saved unavailable toggle shows On with
  "Change to switch off".
- Thirteen example descriptions/help strings were cut at the two-line, 46-column
  detail panel; the copy is now within two lines at 320 px.
- Choosing Restart or Return to game hub resumed native play during the ~22-frame
  fade-out (Sonic visibly moved; input, damage and reopening the menu were live).
  `GameLoopConfigurationCommands` now holds native play and input behind that fade;
  the restart load owner releases it, and a finished or cancelled fade or a mode
  change ends it. The restart still loads on the same frame in all three games.

Rejected: a three-line detail panel (would remove the boundary legend for every
creator) and tracking the restart inside the creator-facing screen (no completion
signal; risk of a permanent hold).

PCM cue isolation against a same-length neutral title control shows a distinct
onset for navigate, confirm, edit, refusal and back in Sonic 1, Sonic 2 and
Sonic 3 & Knuckles; first PCM difference is the first cue frame. This is
offline SMPS output, not speaker certification.

User-requested addition: Big Head **Scale with rings** (default off). The head is
native size at zero rings and grows linearly to the Head size value at 100 rings,
clamped above. `MutatorPolicy.BigHead` gains `scaleWithRings` with its original
fixed-size constructor retained (three additive pin entries). The world adapter
reads each eligible target's live native ring count at presentation, so pickup,
hurt, reloads and rewind need no copied state; No Rings yields a normal head and
Stealth still hides it. A same-schema saved entry predating the option takes its
declared default instead of discarding every saved preference. Native frames at
0/25/50/100/150 starting rings show 100/125/150/200/200% heads in all three games;
zero rings is pixel-identical to Big Head off, and 100 vs 150 differ only in HUD
digits. Natural pickup (S3K 0→11 rings) and the in-play readout were observed;
hurt-driven shrinking is covered by the live-read test, not a captured hurt.



### Preventing automated desktop input takeover (2026-10-09)

The subsequent Opus polish's native-window walkthroughs validated their own
Engine PID but used shared `DISPLAY=:0`, repeatedly acquiring input focus and
sending keys for 181–321 seconds. The user reported the interruption. All five
owned walkthroughs were already terminal, with Engine/recorders/private sinks
absent; the existing normal Maven validation remains preserved. That ownership
check established which window received input, not isolation from the user's
session. Repeated real-time window routes were the wrong routine validation path.

Use existing headless engine tests and offscreen gameplay captures for behavioral
checks, presentation inspection and promo footage. Reserve window automation for
focus/close/window questions. The maintained helper now requires its own fresh
Xvfb server, allocated through `-displayfd` without touching existing X11 locks.
Its Xlib connection, Engine and ffmpeg share only that server; inherited Wayland
routing is removed. Missing/failed isolation rejects launch before Engine, audio
or focus/key actions. There is no shared-display fallback. Independent cleanup
closes clients before reaping the owned server, preserving primary errors.

The regression reproduces ambient desktop access before the repair. All eleven
media controls pass afterward, including missing dependency, explicit private
connection/child environment, invalid or reused display numbers, startup timeout,
real controlled-child protocol/teardown, and independent cleanup failures.
`python3 -m unittest discover -s tools/media -p 'test_*.py'` and Python compilation
pass. A real helper invocation on this host, where Xvfb is unavailable, exits one
with the isolation error, zero Engine/recorder processes, no audio module and
clean cleanup. This is fail-closed verification, not a real Xvfb renderer/window
claim. Documentation links, AGENTS/CLAUDE equality and diff whitespace pass.

The inspected change-based plan falls back to all 3,115 ordinary classes for an
unclassified media script. This repair changes only the Python diagnostic and
its guidance; Java/runtime timing, API, POM, hooks and test-selection policy are
unchanged. Proportionate media regression and launch/lifecycle checks address its
actual consumers without repeating the engine suite. Opus's separate substantive
polish validation continues at its own frozen source. Existing PR215 delivery,
separate promo and fully accounted cleanup remain pending.


### Accepted Opus polish qualification (2026-10-09)

Opus committed the hands-on fixes and optional ring-scaled Big Head at
`345ec13b2505c5c08db53c7d8f0f1de623d2dff9`. Its existing normal run
`20261009T183630Z-a821da3b`, based on the completed `7655c0d8` implementation,
finished 3,116 selected classes / 3,114 ordinary reports / 26,938 cases, with
27 failures, zero errors and 63 skips. All 26 complete qualified baseline
assertions match; the additional Eggman menu assertion is literal-equal to the
separately measured clean-8668 control. All 63 skip identities and full first
causes match. Only the existing exception-prefix removal and verified named SSZ
blob identity normalization apply. Fresh guards have 88 reports / 675 passing
cases, no failures or skips. The run is consumed and acknowledged. This is
inherited-failure qualification, not a green suite or fresh whole-8668 baseline.

The normal queued `-B -Dmse=off -DskipTests verify` also completed with exit zero
(31.448 seconds execution after 280 seconds queue wait), including SDK/Javadoc
packaging and artifact verification. Tests were deliberately skipped in this
separate packaging command. Root subsequently exported the actual compiled API
from that fat jar: 17,743 lines, SHA-256
`c9c807ab99d19d00ee3d10cd9beff39d1d3332110abc86ec58fcd07f8960589d`, byte-equal to
the normalized candidate pin. Three entries are additive for the ring-scaled
option; the original fixed-size constructor and candidate descriptor remain.

Root accepts that verified polish and composes it with the isolated-window
repair above. The only conflict was the dated plan's appended evidence; both
records are retained. Java production/tests/resources, POM, hooks and category
runner inputs are byte-identical to the verified Opus source. The additional
Python diagnostic and guidance have their own eleven passing controls and
fail-closed launch check. There is no reason to repeat the unchanged full run.
The Opus session limit interrupted its final prose handoff after packaging,
not the completed compilation/tests. Root retains the inspected terminal commands
and light comparisons; separate Opus promo and PR215 delivery remain pending.

### PR215 composition with published develop (2026-10-09)

The PR delivery tree fast-forwarded from its original `7f496fc1` to accepted
polish/safety composition `1136bf343`, then intent-merged published develop
`256ec7192aa321e587dda17143cdfb6aad8c412b`. Only the measurement catalogue
conflicted; both the Mutator Lab and incoming decoder/drawing notes remain.
The promo reads the frozen accepted source and compiled Opus artifacts in their
original worktrees while this separate PR worktree owns composition verification.

Relative to the qualified `8668a901` destination, incoming production changes
are native object/scroll low-byte clock corrections and existing art-admission
and session-PCM forwarding. They do not overlap the mutator source, redesign
its algorithms, or alter the API pin/POM. The reviewed clock changes keep the
ROM owners: `V_int_run_count` for object gates, `Level_frame_counter` where the
native routine reads that word, and address offsets rather than fitted tick
offsets. Native source/test changes remain exactly the published versions.
The merged guidance preserves build-only admission and prohibits shared-desktop
focus/key automation. AGENTS and CLAUDE remain identical.

The actual destination plan selects all 3,119 ordinary classes plus fresh
guards because it includes the complete feature. Its substantive implementation
and polish full runs are already completed above. This disjoint composition
uses proportionate focused verification of the changed native gates, their
art/audio consumers, mutator world/load/pacing/rewind/menu/head consumers, API
reflection, real packaged example, and both native level-loading classes plus
the other three mandated S3K checks. A separate fresh guard JVM and actual
compiled SDK/pin verification cover the composition boundary. The initial
preflight rejected ambient Lua 5.5; explicit `LUA_BIN=/usr/bin/lua5.4` passed.
No runner selection is edited and no prior full result is relabelled as a
whole-current-destination pass. These focused composition results and promo
acceptance completed before PR publication.

At composed `c9806fd53904cbbcbed2147e2de8611b4435de24`, normal queued
`-Dmse=off -Dtest=<33 fully-qualified classes> verify dependency:build-classpath`
completed with 33 fresh reports / 315 passing cases, no failures/errors/skips,
no unmatched selectors and an unchanged source fingerprint. Both native
level-loading classes and all four mandated S3K checks execute against the
rehashed original ROMs. The same invocation completed SDK/Javadoc and artifact
verification. A separate normal `-Pguards test` produced 88 fresh reports / 675
passing cases without skips. The exact focused class list and commands are in
the light `pr215-composition-256-summary.json` under task scratch; consumed raw
XML/text reports are deleted rather than archived. No whole-current-base result
is inferred from this focused composition.

The actual compiled fat-jar API remains 17,743 lines with the `c9c807ab...` SHA-256
recorded above, byte-equal to the candidate pin. Creator compilation/package
against those fresh classes produces 15 ROM-free jar entries and the same
`a77d49b2...428f9d56` hash as the accepted ring-option example. No target trees
are shared or copied; only the packaged creator deliverable is exported to the
task's durable `deliverables` directory. The final evidence/README follow-up
changes documentation only.

The separate Opus 5.5 promo completed without a provider limit. Its
`opus-expansion-promo/out/mutator-lab-expanded-promo.mp4` is 81.4 seconds,
1920×1080/60 H.264 with stereo 48 kHz AAC, SHA-256
`2d10f2598d5481118954909459aa66f19a5c9463521896a3405c78cbc47d6e34`.
Thumbnail, chapter/source manifest and reproducible ffmpeg edit recipe remain
alongside it outside Git. Root independently decoded the entire movie without
errors and inspected first/middle/last, catalogue and all-three ring-head grids.
The edit uses earlier precisely attributed offscreen working-copy captures;
their metadata is not relabelled as final-build footage. All eleven native
option pages, three-game play, Big Head, Stealth, Resume/restart and ring scales
are shown. Gravity physics differences, spill/filter/death behavior, interactive
speed, denied entry, amplified rebound and ring-loss shrink are described in
captions rather than demonstrated in this movie. No desktop-window capture or
synthetic OS input is used by the promo stage.

PR215 is updated in place; main is not integrated or switched. Saved provider
conversations, useful media/results and the open-PR source are retained. Twelve
registered temporary ancestor worktrees are accounted for: 65 admission dirty
paths match final source or exact initial import; the four physics files are
known peer copies, including the pre-retirement runtime superseded by the
accepted admission owner. Config copies and an ignored rewind note are preserved
before cleanup, with no unknown source discarded. Actual feature publication
and cleanup completion are recorded in the final task result, not inferred here.

## Full-inventory Ringfall (gameplay promo correction, 2026-10-10)

The corrective gameplay promo needed spills larger than the native 32-ring
scatter. Ringfall gains an explicit **Drop full inventory** checkbox (default off,
LIVE) backed by `MutatorPolicy.Ringfall.fullInventory`; composed policies keep the
native ceiling unless every Ringfall contribution asks to lift it. The coordinator
latches `(count, beyondNativeLimit)` before allocation, including the deferred S3K
queue and its rewind snapshot, so a later LIVE edit cannot shrink a queued spill.
Only the native first 32 rings reserve or allocate Obj37 SST slots; the remainder
are slotless `LostRingObjectInstance` continuations (the existing S3K logical
overflow path, now reachable in all three games) bounded by the 999 ring counter.
The legacy 32-entry mirror stays native-sized. Rings 0–47 follow the native fan
generator with its ceiling removed; later 48-ring cycles rotate by the 4-bit
bit-reversal of the $10 angle step so extra rings do not stack on earlier paths.

Rejected: public `RingManager` overloads (they widened the `@ModApi` surface; the
seams are package-private behind the unannotated `RingManagerInternalAccess`), and
letting extras claim free dynamic slots (that would starve native object loading
for the spill's lifetime). Known limits: touch collection remains one ring per frame,
and Sonic 3 & Knuckles' 63-entry collision-response list still bounds the spilled
objects each frame can touch.

Focused validation: `TestLevelRingfallIntegration` (production RingManager/ObjectManager
spills of 150/75/20 in S1/S2/S3K, 32 native slots, slotless remainder, rotated fan),
`TestLevelLostRingSpawnCoordinator` (resolution table, deferred 32-slot reservation
and latch across restore; its fixture now supplies explicit services instead of relying
on an ambient gameplay runtime left by an earlier class), `TestMutatorExpandedPolicies`,
`TestModApiSignatureSurface`/`TestModApiPinPolicy`, `TestExampleMutatorsPackage` and the
existing lost-ring/ring-manager suites.

### Gameplay-first replacement promo and root review

The menu-heavy expansion reel was rejected: listing configurable effects did not
show why stacking them is fun. The replacement uses actual input-only gameplay:
a 173-ring spill and head shrink/regrowth during recollection, five native badnik
stomps with amplified rebounds, live gravity on/off, a No Rings restart, and a
211-ring stacked finale. Two starting inventories are disclosed on screen; they
are demonstration setup, not a claim of earning those rings in a cold run. The
edit uses matched native controls and about seven seconds of menus in a
52-second movie. A death caption was corrected to say the hit is fatal rather
than claiming exhausted lives. Game Speed is not demonstrated.

The recurring observation and layout probes are maintained as
`MutatorGameplayCaptureTool` and `LevelLayoutDumpTool`. They require the opt-in
surfaceless backend before opening a session, consume ordinary capture inputs,
and read native owners without changing gameplay during a take. The historical
source receipts still identify the original scratch drivers. Existing Sonic 1
zone-alias ordering remains an observed tool limitation; the requested string
alone does not establish the loaded zone.

Root review found that GLFW initialization hints survive termination. The new
surfaceless boot selected the null platform without releasing that hint for the
next native-window owner. A display-free mocked regression reproduced the
missing reset after initialization failure; the fix restores automatic selection
in `finally` after initialization, leaving the already initialized null platform
unchanged. Reference: [GLFW initialization hints](https://www.glfw.org/docs/3.4/intro_guide.html#init_hints).
The initial validation launch used a nonexistent Debian-style JDK path and ran
no tests; the corrected environment uses the verified Arch Java 21 installation.
Root checks also exercise all three games' actual 999-ring allocation limit.

Root verification used the normal queue with Java 21 and the desktop environment
removed: `-Dtest=TestSurfacelessEglContext,TestMutatorGameplayCaptureTool,TestLevelRingfallIntegration,HeadlessGameBootTest,TestHeadlessGameBootAudioIdentity,TestModApiSignatureSurface,TestModApiPinPolicy verify`.
All seven classes completed: 43 cases, no failures, errors or skips; the real EGL
back-buffer readback and all three 999-ring allocations ran. Binary SDK/Javadoc
artifact verification passed, and compiled reflection matched the candidate pin.
The promoted capture driver replayed the first 760 frames of the Green Hill
hero take: all PNGs, state/observation rows, badnik rows and 608,000 stereo PCM
frames were byte-identical to the original scratch driver. Temporary duplicate
media were removed after comparison. These are focused checks; the normal
combined category run and fresh structural guards remain required.
