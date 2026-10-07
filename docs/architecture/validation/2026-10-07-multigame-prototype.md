# Multigame prototype implementation evidence

Original base: `6d817a9d74f135714f3da59ab9aab156cc09473e`.
Branch: `feature/ai-multigame-prototype`, isolated review delivery against develop.
The [accepted blueprint](../designs/2026-10-07-multigame-blueprint.md) remains the
future MVP/product roadmap. This record concerns the process-host prototype.

## Implementation and decisions

- One managed JVM owns each real production game. No singleton-root swapping,
  historic GameRuntime or speculative engine-wide GameInstance refactor.
- `ExclusiveLiveGameDriver` is a lifetime input/step lease; one common held byte
  admits one LIVE native iteration and each native polling owner derives edges.
  Physical/debug/movie/trace/recording/pacing routes cannot compete. Genesis Start
  stays game input, not host pause. Transport ordinals never supply gameplay.
- Production hidden-GL bootstrap and complete native renderer produce top-down
  320×224 RGBA; production no-device SMPS presentation produces48k stereo PCM.
  A worker has no OpenAL device; one host sink applies post-synthesis focus.
- Title, loading, ready/countdown, linked pause/restart/fault/exit, keyboard and
  standard-pad control live in small host owners. ROM-backed menu cues have their
  own standalone presentation clock, separate from all three workers.
- Protocol v1 bounds commands, identities, frame/text/PCM sizes and one outstanding
  request. Host cancellation checks health under the same monitor as tuple commit;
  GPU publication checks generation again on the UI thread.
- Worker directories are owned temporary paths under this tree's target. Cwd/class
  path and environment whitelist exclude linked config and injected JVM switches.
  Normal/forced process close is bounded; remaining owner releases continue after
  any cleanup failure and preserve the failure for honest reporting.

Rejected approaches: active-root swapping and three SessionManager opens retain
shared mutable oscillators/media or destroy the previous mode (owning source in
blueprint). Logical input override alone leaves physical/debug input and
fast-forward pumps reachable; the exclusive lifetime seam closes that surface.
Using capture settings unchanged forces skipped AIZ intro/audio off, so workers
use the underlying production boot with an explicit native recipe instead.

## Independent review

The initial read-only ownership audit found failed OpenAL-sink reuse on retry,
cleanup short-circuiting and commit-after-close races. Source fixes dispose sound
owners before retry, aggregate all releases, and reserve/commit admissions under
the close monitor. Regressions exercise transported PCM copy/pause/flush, owner
release after injected failure, and a collected tuple rejected after closure.
Final integrated review and executed results are recorded below when complete.

## Support and honest gates

Native Sonic GHZ1/EHZ1 and Sonic+CPU Tails AIZ1,320×224,NTSC60, REALISTIC live
loading, native intros, no donor/custom packages. This is simultaneous playable
opening-level feasibility. No opening-act common completion, full campaign,
linked rewind/checkpoint/replay resume, leaderboards, native-image or all-platform
claim. Unsupported special/bonus/ending renderer branches stop recoverably.

The maintained common program is a neutral-entry/movement/jump witness, not a
winning route. Existing engine rewind remains in each fresh session, but this
host intentionally exposes no linked restore; future checkpoints must include
poll baselines, all independent clocks and a transactional host cursor.

No new zone/act or route certification is delivered, so existing level matrices
and AIZ→HCZ obligations remain inherited; a load/movement witness does not close
their coverage gaps. No trace frontier change or trace hydration is introduced.
No public ModApi surface/version/pin changes. Cross-team overlap is confined to
GameLoop/InputHandler/external ownership and the final-PCM sink intake. Hardened's
nativePlayerInput/fresh-launch hooks and Mutators' overlay/settings contracts are
separate; this branch has no code dependency on either team.

## Verification accounting

Tool preflight with Java21.0.12.1 and `LUA_BIN=lua5.4` passed. Default `lua` is5.5,
so guard runs use the explicit5.4 binary. ROM identities verified against the
AGENTS table through original absolute files. Fetch checkpoint still matched the
original destination SHA. The initial runner plan selected3006 ordinary classes
plus fresh-JVM guards; the final selection is reviewed after integration.

Implementation/build/GPU/device results are pending at this source checkpoint.
No executed pass, GPU observation, audible output or budget is claimed here yet.
