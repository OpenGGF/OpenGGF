# Second Strike

A difficulty prototype over the ordinary **Sonic 3 & Knuckles** campaign,
currently being expanded across both apparent Angel Island acts and the remaining
zones. Native owners retain terrain, progression, checkpoints, lives, saves and
character abilities. Complete hard-mode routes are still being verified; authored
source and positioned demonstrations are not campaign-completion certificates.

The opening has three readable enemy roles: spotters alert nearby defenders;
guards commit to a fast flaming charge and expose themselves while braking;
artillery locks its target before firing and then reloads. Spotters and artillery
take two separated hits, guards three. The guard charges up to 225 pixels at ten
pixels per update, with a shorter final stride; native floor and gap checks can
end it earlier. This is 150% further than the previous actual 90-pixel charge.
The guard is invulnerable during its dash;
dodge its committed direction and punish the brake or slow return to its post.
Selected native Caterkillers use twelve-body chains, keeping their native head,
segment art and firing roles. Wide artillery throws two ten-seed fans twelve updates
apart; selected split-fan plants leave a traversal lane with eight seeds per release.
At most twenty seeds belong to one plant. Committed seeds survive its defeat and
retire on unload. Guards defend native vines, slopes, logs and landings. Selected
brittle logs have a visible pre-contact cue and release after ten support updates;
a completed hollow-tree exit can admit one native Monkey Dude lookout at a clear,
terrain-checked landing, with no retry after the one birth attempt. Final blows retain native
animals, score and destruction effects.

Each apparent AIZ act retains **five original ring placements**: three forest
pickups plus two in the fire continuation for Act 1, then five in true Act 2.
Native scatter, recollection and stage prizes can raise the counter; this is
scarcity, not a ring cap. Ordinary non-speed-shoe monitors use truthful native
Robotnik contents. Original shoes, scripted rewards and native checkpoint flow stay
with their owners.

Selected posts offer earned, zero-ring native bonus invitations: forest post 102
selects Gumball, and Act 2 post 476 selects Glowing Spheres. The native run, entry
permit, activation and reward gates still apply. Positioned tests observe an earned
life or rings and a stable return to LEVEL. Knuckles's actual eligible-post route,
deliberate revisit/death-after-entry lifetime and bonus-flow durable saves remain open; there is no claimed act-wide
once-entry guarantee from those short observations.

Fire Breath gains a delayed second wave at three hits or fewer, with sixteen visible
warning presentations. The waterfall boss's finite alternating-arm pattern has a
24-update native warning, staggered arms and preserved character-specific shot and
recovery behavior. Bombing-run port staggering keeps native wraps and progression.
All fourteen Chaos/Super Blue Sphere boards have bounded authored gates while
retaining native blue/ring cells and conversion; individual full clears/perfects
are still pending. Gumball uses three consumable springs with native prizes.
Glowing Spheres currently retains its native gameplay. A positioned Sonic visit
clears authored Giant board 0, earns its native emerald/results and returns; a
separate native-menu save-slot visit persists the emerald and loads it in a fresh
process. The other boards, character routes and complete cold progression remain
open.

The first campaign batch adds native-owned ingredients to Hydrocity, Marble Garden,
Carnival Night, IceCap and Launch Base: brittle supports, alert badniks, finite boss
cadence, warned falling ice and bounded alarm escorts. Initial ICZ2/LBZ2 content is
resource retention only; their encounters, other missing zone concepts and playable
routes remain open. The [campaign scope](../../docs/architecture/designs/2026-10-15-second-strike-campaign-scope.md)
records the remaining work instead of treating these ingredients as completed zones.

## Build and play

Requires Java 25, Maven and your original locked-on ROM. No ROM assets are
distributed in the mod.

```bash
python3 examples/second-strike/build.py
python3 examples/second-strike/build.py --run --rom "/absolute/path/to/original.gen"
# Subsequent launches keep the existing profile settings.
python3 examples/second-strike/build.py --run
```

The development launcher opens Sonic 3 & Knuckles; use the normal title and
save-slot UI. The badge identifies the patched game. The launcher creates an
owned profile at `target/second-strike/player-profile`, with separate native
saves. It never edits the checkout's linked config or your stock save directory.
Use `--profile /your/owned/directory` to retain a profile outside build output.
Sonic defaults to CPU Tails; `--solo` disables him when creating a new profile.
`--character tails` or `--character knuckles` sets a new profile's selection.
Existing profiles retain their settings; change characters through the launch UI.
`--rom`, `--character` and `--solo` are new-profile options; the launcher rejects
them for an existing profile instead of ignoring them. The build holds one
worktree lease through compilation and strict validation (`--warnings error`).
A failed build preserves the previously published jar.
Difficulty content and the title badge apply to every selected S3K character.
Observed route support remains limited to the native Sonic, Tails and Knuckles
cells in the evidence matrix; custom-character compatibility is not certified.

Move, jump, spindash, fly and glide use your normal engine bindings. No custom
retry button is needed: death and checkpoint respawn follow the game. Removing
the patch restores stock content. Loading the jar manually uses the host's save
root; use a separate profile for this difficulty mode.

## Prototype boundary

The [binding AIZ scope and full-campaign roadmap](../../docs/architecture/designs/2026-10-15-second-strike-campaign-scope.md)
separates required work from current implementation. Both apparent AIZ acts and the
first campaign ingredients are authored in source. One fresh Sonic-solo route
defeats Fire Breath and reaches its native results and apparent Act 2; all four
reference routes still have unmet playable-HCZ gates. Positioned behavior,
rewind and pixel checks are narrower evidence. Lives, abilities and progression
remain native; complete checkpoint, character, viewport and save coverage is pending.

See the [partial AIZ matrix](../../docs/architecture/validation/levels/s3k-second-strike-aiz.md)
and [delivery plan](../../docs/architecture/plans/2026-10-15-second-strike-campaign-delivery.md)
for exact source, observed checks and limitations. The [earlier prototype evidence](../../docs/architecture/plans/2026-10-13-second-strike-prototype.md)
is retained with its original, gentler scope.

`walkthroughs/routes/` contains reproducible ordinary controller recipes. A
positioned local demonstration is supplementary evidence, not a cold campaign
completion. Routine tests and footage are headless/offscreen; desktop automation
is not part of this example.

The cold-route driver supports `-Dsecondstrike.route.video=true` for continuous
native FFV1/FLAC footage; its default records state with milestone images only.
The phase-2 pair and solo recipes record the earlier, gentler prototype. Their
full-route movies do not certify the five-ring/multi-hit revision. A fast native
kill may legitimately finish the boss before its extra attack is scheduled.

The packaged example uses the supported candidate Mod API, with no unsupported
engine-reference warnings. Creator-kit builds pair the engine fatjar and SDK
classifier from the same source; the SDK classifier contains tools/templates and
is not a standalone runtime. Exported walkthroughs are ROM-free, and native
creator tests require your original locked-on ROM (`--s3k`).
