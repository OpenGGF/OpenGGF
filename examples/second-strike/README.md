# Second Strike

A difficulty prototype over the ordinary **Sonic 3 & Knuckles** campaign.
Angel Island Act 1 keeps its intro, terrain, checkpoints, fire reload, real boss,
results and onward progression. Lives, death, game over, saves, character abilities
and stage rewards belong to the native game.

The opening has three readable enemy roles: spotters alert nearby defenders;
guards commit to a charge and expose themselves while braking; artillery locks
its target before firing and then reloads. Remove the spotter, dodge the committed
attack, and punish the opening. Existing vines, slopes, logs and landings shape
the encounters. Ring banks reward detours while faster exposed lines offer less
insurance. All monitors, checkpoints, giant rings and scripted actors remain.

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
Use the phase-2 pair or solo recipe to show the late boss follow-up. A fast native
kill may legitimately finish the boss before that extra attack is scheduled.
