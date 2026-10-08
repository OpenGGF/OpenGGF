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
