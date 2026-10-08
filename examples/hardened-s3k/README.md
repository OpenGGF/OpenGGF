# Post Two Ambush

One short encounter beside Mushroom Hill Act 1's **real second starpost**.
Watch the sentry's warning, move after its aim commits, dodge two volleys and
follow the exit marker. This JVM example preserves the native level, checkpoint,
terrain, enemies, collision, events, art loading and sound driver.

This is a local encounter prototype. The full hardened MHZ act, chapter packs,
campaign, other characters and Kaizo/Stage Trials remain later blueprint stages.

## Build and play

Use Java 21, Maven and Python 3 from this checkout:

```sh
python3 examples/hardened-s3k/build.py
python3 examples/hardened-s3k/build.py --run --rom '/absolute/path/to/your/Sonic3-and-Knuckles.gen'
```

The first command queues the engine build, compiles maintained creator Java with
`javac --release 21`, then packages and validates
`target/hardened-s3k/hardened-s3k.jar`. The second launches through `ggfmod run`
using an isolated `target/hardened-s3k/run/config.yaml`. It does not edit the
checkout's configuration or attach a stock save slot. `--skip-engine` reuses a
previous engine build; use it only when that build matches the current sources.

Only classes and the manifest are packaged. Supply the locked-on S3&K ROM with
CRC32 `63522553` / SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6` at runtime.
No ROM bytes, images, music or disassembly files are included. A missing ROM
argument fails before launch; an unrecognized or corrupt supplied ROM uses the
engine's recovery screen. The engine and the code-bearing mod are two artifacts;
arbitrary compiled JVM mods are not native-image compatible.

## Walkthrough and controls

1. Choose **How to Play**, then begin. The menu explains the warning, real post,
   one safe recovery ring and exit. Its native MHZ music establishes the encounter's setting.
2. The entry panel advances neutral native loading frames and releases movement
   after the fade, title and actual terrain/enemy-art owners finish. Touch
   the starpost yourself; the mod never fabricates its saved state.
3. Read the tell. The sentry's spiked crown means it cannot be bopped like a stock
   Mushmeanie; the crown turns red exactly while its body hurts. A yellow sight
   follows Sonic and closes in during the charge, then turns red, flashes and
   plays the native lock-on cue when the aim commits. Both volleys fly to that
   marked point, so moving afterward gives a safe solution. A safe retreat
   remains available; waiting does not require taking a hit or spending a powerup.
4. Survive both volleys and wait for recovery. A native switch cue opens the
   **Exit** marker on the upper path. The bottom coaching line names the current
   phase throughout. The clear screen shows the run time and kept rings, then
   offers another attempt or a return to the title.
5. A fatal hit plays Sonic's native death arc on neutral rows, then the result
   panel rises before the native restart row, so no life is lost and the native
   game-over flow never runs. It offers a native checkpoint retry. Before
   touching the post, the menu explicitly offers **Start Fresh**; after contact,
   the real post's saved position wins over the fresh encounter entrance. Full
   fresh launch is a separate operation reached by returning to the title and
   beginning again.

| Action | Example keyboard | Primary controller |
| --- | --- | --- |
| Move / menu choice | Arrows | D-pad / native movement bindings |
| Jump | Space | A/B/C native action binding |
| Confirm menu | Enter or Space | A/B or Start |
| Ambush pause/retry menu | Backspace | Start |
| Back from a menu | Esc | C/back action |
| Independent host pause | P | Ambush Start menu owns its Start edge |

On-screen prompts name the live bindings and follow the last device used: a
keyboard player sees keys such as `ENTER` and `BACKSPACE`, a pad player sees
that pad family's buttons. The isolated launch configuration selects `P` for
host pause to keep menu Enter clear. Normal installed play retains configured bindings. The engine's configured
escape-to-master-title path remains available. Logical replay inputs own gameplay
and menu actions; UI polling does not inject physical keys into a replay.
The ambush menu holds gameplay while ambient music and menu cues continue;
independent host pause silences presentation through the engine's existing owner.

The [maintained controller walkthroughs](walkthroughs/README.md) reproduce the safe
and fatal paths with production GPU frames, state and PCM. They include source
programs, BK2 inputs and exact capture commands.

## Supported cells and isolation

The implemented encounter target is native solo Sonic, donor off, 320×224, no-save,
MHZ1. Unsupported teams, donation or widths are rejected before challenge assembly
with instructions to use the supported launch configuration. The prototype is a
dedicated enabled patch mode; disable its package to play stock S3K. It introduces
no stock-wide enemy-rate, health, physics or ring reward changes. Stock saves remain
the engine's own files, with no campaign progress flags written by this example.

The [affected MHZ1 matrix](../../docs/architecture/validation/levels/s3k-mhz-act1.md)
records local encounter obligations and inherited full-route gaps. Other viewports,
teams, donors, a cold full-act route, boss changes, bonus/special stages and human
difficulty certification are not promised by this prototype.

## Creator map

| Source | Responsibility |
| --- | --- |
| `HardenedS3kMod` | Transactional factories/placement registration; supported launch admission; native module delegation |
| `EncounterPlan` | Surveyed bounds, explicit safe ring, entry/sentry/exit coordinates and finite budgets |
| `EncounterState` | Session-owned phase/aim/volley/checkpoint outcome with a rewind adapter |
| `Sentry` / `Spore` | Resident Mushmeanie ROM mappings; readable tell; native hurt contact; bounded recreatable children |
| `Title` | Title/lesson navigation and a session-owned fresh launch |
| `AmbushFlow` | Native-input opt-in, neutral native entry, menu holds, native death arc, real post retry, clear/failure/exit transitions, HUD and coaching line |
| `Canvas` | Engine UI font at whole-number scales, live binding prompts and queued screen-space geometry; no separate graphics/audio backend |
| `Marks` | Outlined world-space sight, crown and spore spikes queued around the ROM sprites |

Read the [agent authoring walkthrough](../../docs/modding/quickstarts/hardened-s3k.md)
before changing the room. The prototype's compiled Java values are the accepted
authoring format; the blueprint's future YAML sketch is not a supported parser.

## Troubleshooting

If the package validator reports a candidate API mismatch, rebuild both artifacts
against this checkout. The unpublished `0.7.0` candidate may change its signature
pin without a version bump. Do not grant extra trust to silence a structural
validation error; code-bearing mods require explicit trust under the normal
[mod trust boundary](../../docs/modding/concepts/trust.md).

If you see the ordinary master title, select S3&K using the supported solo/donor-off
profile, or use the isolated launch command. If the supported-cell admission fails,
the error tells you which preset to select. Do not silently force another player's
preferences. A missing resident renderer keeps the sentry harmless until art is
ready; a failed owner aborts the session through the existing fault boundary.

Capture and verification evidence is recorded in the dated implementation artifact
and PR. A headless test alone does not prove readable GPU presentation or audible
output; a positioned local walkthrough does not certify a whole MHZ act.
