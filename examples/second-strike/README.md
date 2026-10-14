# Second Strike

A difficulty prototype over the ordinary **Sonic 3 & Knuckles** campaign.
Angel Island Act 1 keeps its intro, terrain, checkpoints, fire reload, real boss,
results and onward progression. Lives, death, game over, saves, character abilities
and stage rewards belong to the native game.

The opening has three readable enemy roles: spotters alert nearby defenders;
guards commit to a fast flaming charge and expose themselves while braking;
artillery locks its target before firing and then reloads. Spotters and artillery
take two separated hits, guards three. The guard is invulnerable during its dash;
dodge its committed direction and punish the brake or slow return to its post.
Three extra guards make the native
vines, slopes, logs and landings more dangerous. Final blows retain native
animals, score and destruction effects.

Only **five original ring placements** remain across forest and fire together:
three forest footholds and two fire-section pickups. Ring recollection still
works. Non-speed-shoe ordinary monitors are visibly native Robotnik boxes and
hurt their collector; the original speed-shoe box is retained. Six hidden native
boss/results reward boxes remain unchanged; none contains rings. Checkpoints,
giant rings, scripted actors and stage prizes keep their native behavior. Five
placed rings alone cannot reach a checkpoint's twenty-ring bonus threshold.
Stage rewards can raise the ring counter: this is scarcity, not a counter cap.

Fire Breath gains a delayed, warned second wave after a breath cycle at three
hits or fewer. The first Chaos Emerald board gains twelve turn gates; its blues,
ring conversion and reward remain native. Gumball has three consumable springs
instead of four; REP and its native prizes still work.

## Build and play

Requires Java 25, Maven and your original locked-on ROM. No ROM assets are
distributed in the mod.

```bash
python3 examples/second-strike/build.py
python3 examples/second-strike/build.py --run --rom "/absolute/path/to/original.gen"
```

The development launcher opens Sonic 3 & Knuckles; use the normal title and
save-slot UI. The badge identifies the patched game. The launcher creates an
owned profile at `target/second-strike/player-profile`, with separate native
saves. It never edits the checkout's linked config or your stock save directory.
Use `--profile /your/owned/directory` to retain a profile outside build output.
Sonic defaults to CPU Tails; `--solo` disables him when creating a new profile.
`--character tails` or `--character knuckles` sets a new profile's selection.
Existing profiles retain their settings; change characters through the launch UI.

Move, jump, spindash, fly and glide use your normal engine bindings. No custom
retry button is needed: death and checkpoint respawn follow the game. Removing
the patch restores stock content. Loading the jar manually uses the host's save
root; use a separate profile for this difficulty mode.

## Prototype boundary

The authored slice is the forest opening and early fire continuation of Act 1.
Later physical Act 2 content continues normally. Other zones' levels, other special
boards and other bonus-stage families remain stock; the first-board and Gumball
variants are available through their normal stage detours throughout the campaign.
Sonic, Tails and Knuckles
retain their abilities; the observed route/support matrix and validation limits
are recorded in the [design and evidence](../../docs/architecture/plans/2026-10-13-second-strike-prototype.md).

`walkthroughs/routes/` contains reproducible ordinary controller recipes. A
positioned local demonstration is supplementary evidence, not a cold campaign
completion. Routine tests and footage are headless/offscreen; desktop automation
is not part of this example.

The cold-route driver supports `-Dsecondstrike.route.video=true` for continuous
native FFV1/FLAC footage; its default records state with milestone images only.
The phase-2 pair and solo recipes record the earlier, gentler prototype. Their
full-route movies do not certify the five-ring/multi-hit revision. A fast native
kill may legitimately finish the boss before its extra attack is scheduled.
