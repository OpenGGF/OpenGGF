# Sonic3K Chaos Mode

A code mod over native Sonic 3 & Knuckles. It has 105 stable effect identities backed by 27 parameterized, dormant mutator definitions. Pool selection is separate from persistent definition enable flags: the captured scheduler activates only the selected effect for a bounded lease.

The S3K ROM is required and supplies runtime game art and audio. The jar contains Java code and its manifest. Native Sonic, Tails and Knuckles launches use the stock intro, followed by an explicit **Start Chaos** or **Play normally** choice; both retain native data select, save slots, team selection and progression. Chaos capabilities are unavailable with cross-game features enabled. Launching this patch is not a claim that every effect has a qualified target in every act.

## Build and launch

From a Java 25 checkout:

```sh
python3 examples/sonic3k-chaos-mode/build.py
python3 examples/sonic3k-chaos-mode/build.py --run
```

The build writes `target/sonic3k-chaos-mode/sonic3k-chaos-mode.jar`. Install that jar through the Mod Hub and grant its code trust, or use the development launcher above. The launcher uses its own settings root under `target/sonic3k-chaos-mode/player-settings`; pass `--save-root /absolute/path` to retain a different development settings directory. No ROM is copied or bundled.

## Controls and settings

The title and pause menus share a two-list catalogue: **Available** and **Selected**. Choose a list with Left/Right, navigate with Up/Down and confirm to add or remove an effect. Mouse clicks also move entries. The controls panel changes category filter, search, interval and duration; it can add all or clear the pool. Escape/back returns from configuration. A highlighted entry shows its category, severity, admitted duration and short description.

During play, Start or the configured pause key opens the Chaos menu before the host pause toggle. Resume applies saved settings. Pause Chaos clears owned active effects and freezes selection; Clear active effects, Restart act and Return to hub use the existing host flow. Leaving with unsaved storage does not proceed. Settings are written synchronously to this mod's owner-local `settings.txt`, with a backup; incompatible or unrecoverable corrupt bytes are preserved until an explicit **Reset defaults**. A valid backup can recover a corrupt primary.

Defaults are a 30-second interval, 1× duration and 95 selected effects. The five optional sidegames and five high-risk context effects start unselected. A fresh act/respawn has five seconds of grace before the normal interval. An empty pool is valid and consumes no selection RNG.

Severity tickets are 90% ordinary, 9% disruptive and 1% major. Missing tiers fall downward only; a pool containing only major effects does not turn them into common effects. Major draws have a three-minute cooldown. Recent effects are avoided when another eligible entry exists. At most two effects overlap, with at most one nonvisual effect and one lease per family. Runtime target, placement, route and art qualifications can reject an entry or explain that it is unavailable.

## Timing, rewind and sidegames

Selection and timed leases use logical active presentations, independently of native gameplay tick pacing. Paused/setup/stage/transition/rewind phases do not draw effects. Zero-duration actions wait for native acknowledgement, including at presentations where slow pacing executes no native tick. Head pulses and Time Moves With Sonic replace options on the existing lease without renewing its identity or expiry.

The captured run includes RNG, draw deadline, cooldown, recent history, admitted effect configuration, active identities, terminal-result history, chat state and minigame state. Native completion earns five seconds of respite once, including a result reported on the final tick before lease expiry. Failed actions leave a readable notice after acknowledgement. Retained results do not renew rewards or notices every frame. Restoring a snapshot hydrates state; it never reruns a policy factory or grants an action/reward again. Loads and world closure revoke owned leases. Saved preferences are an explicit external publication and are not rolled back by gameplay rewind. `DecisionSource` currently uses local captured randomness; future remote decisions require a captured outcome journal and are not automatically enabled.

Ring Pong, Ring Snake, Monitor Stack, Shield Beat and Badnik Defence implement bounded paddle, Snake, falling-block, rhythm and lane-defence games. Their composite lease acquires a host-owned gameplay hold. Directions control the paddle/board; A/B/C select rhythm/defence lanes, and A hard-drops a stack piece. Start or Escape cancels immediately. Held presentations advance only the game, paused presentations advance neither clock, and the lease has a maximum 20-second deadline. Three successful Ring Pong returns submit one host-bounded five-ring reward. Score also buys at most 30 seconds of Chaos respite. Cancellation awards nothing and resumes the native act. These are optional captured sidegames, not external stream integration.

## Effect families and qualifications

| Family | Catalogue numbers |
|---|---|
| Head / stealth | 1–2 / 3 |
| Sprite / scene / HUD presentation | 4, 10, 37–40 / 5–9, 97–99 / 31–36 |
| Dry / signed gravity | 11–12 / 23–24 |
| Player motion / input control / pacing | 13–15, 22, 72–73, 83, 91 / 18–19, 71, 90 / 16–17, 56 |
| Hurt scatter / defeat rebound / springs | 20 / 21 / 48–49 |
| Object fields / ring economy | 41–47 / 50, 62, 75, 100 |
| Native encounters / enemy rules | 51–52, 54–55, 60–61, 64, 70, 80, 87–88 / 53, 57–58, 65, 77, 86, 89 |
| Power-ups / transport / relocation | 63, 67–69 / 78–79, 81 / 25–30 |
| Challenges / projectile rules / local fluid | 59, 66, 76, 92–93 / 74, 84–85 / 82 |
| Audio / simulated chat / Pong / other sidegames | 94–95 / 96 / 101 / 102–105 |

Dry gravity applies to dry native Sonic. Head scaling uses reviewed native head masks, with player/team scope. Ring Confetti only qualifies with more than 32 carried rings and makes full inventory hurt scatter recoverable; hurt still loses the full carried inventory. Sleepy Springs weakens qualified downward launches; upward/horizontal routes retain their native minimum unless the host has a reviewed weaker route contract. Team swap needs an eligible sidekick. Spawned solids, rescue platforms, vines, carry, checkpoint moves and local fluid require host-owned safe geometry and bounded native lifetimes. Bosses, anchored enemy graphs and mandatory route actions remain subject to native qualifications.

Wrong-Zone Scenery samples the prepared Act 1 backgrounds of AIZ, HCZ, LBZ and DEZ, excluding the current zone. The public scenery policy admits indices 0–13 for host qualification; the other donor indices currently have no prepared background and are not sampled by this mod.

The focused example tests check actual scheduler decisions and expiry, replay, rejected saves, callback pacing, option replacement, bounded game mechanics and package registration. Preparing all 105 catalogue entries is API/schema coverage; it does not certify their native consumers, ROM assets or every route context. Those require the host's separate behavior and rendering checks. Sidegames require the injected native ROM art decoder. Their ring, monitor, badnik, spring, character and shield motifs are cached from verified S3K ROM tables and drawn through the injected pixel canvas; missing or rejected art makes them unavailable, without substitute game artwork. The real-ROM art check covers actual decoded motif pixels and board movement without a desktop display.
