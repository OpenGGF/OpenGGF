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
- Three isolated owners are implementing native admission, physics/pacing and
  anatomical rendering/attachments. Their focused requests remain pending.
  The root-owned all-game catalogue, registration and common UI also await their
  focused request. Native consumer, composed rewind, SDK, domain, capture and
  broad verification are not yet claimed. Fresh Opus polish and promo follow
  verified implementation.
