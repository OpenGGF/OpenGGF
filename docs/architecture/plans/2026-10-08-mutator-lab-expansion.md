# Mutator Lab: the complete original catalogue across three games

## Outcome and base

Expand the existing Mutator Lab example and PR215 to the original catalogue in
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
| Ringfall Manipulator | LIVE / LIVE | 10–100% of the native recoverable scatter, optional hard cap; losing inventory remains independent from spawning. |
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
