# TAS publication trick regression coverage — 2026-10-26

The [representative verdict follow-up](2026-10-26-tas-trick-verdicts.md) evaluates
concrete named tricks with executed support or limitation scenarios. This record
preserves the first pass's stricter family-wide assessment and component evidence;
its historical classifications are not the follow-up inventory.

This work turns the 48 partially supported or unverified trick families from the
TASVideos publication audit into explicit test obligations. The audit covered
all 55 current and obsolete publications returned by the supplied
[Sonic 1](https://tasvideos.org/248G), [Sonic 2](https://tasvideos.org/199G), and
[Sonic 3/Knuckles](https://tasvideos.org/127G) game pages. Its engine evidence was
pinned to `99b16240df435f6034880aef29f9271ec8d7f659`; this test work starts from
`c50876b184612428ccaf22763412f88ffe505432`.

The rows below are a coverage inventory, not an assertion that every exploit or
publication works. Official lock-on variants retain their own setup and rules.
The original submission notes define which behavior is at issue; ROM/disassembly
or native observations define expected results. No movie synchronization is
inferred from passing component tests.

## Test obligations and evidence

Each game matrix records the specific setup, independent expectation, named test
selector, execution result and remaining gap for every family. Distinguish:

- **Mechanism regression:** the production path reproduces a named trick with
  its required entry state and observable exit, and the assertion detects a
  relevant boundary or deliberately broken control.
- **Component evidence:** a required production mechanic is checked, but the
  full exploit entry, contact ordering or route remains unqualified.
- **Native fixture pending:** a concrete original-ROM input/state/observation
  is still needed. The row identifies the publication, location, character,
  entry and output to capture; an empty or disabled test is not evidence.
- **Observed mismatch:** an executable check reaches the intended setup but
  disagrees with independent evidence. Keep the failure visible and separate
  from a passing coverage claim.

Use native 320×224 stock configuration and the exact publication character/team
for TAS claims. Wider-view and donor tests can establish engine contracts but
cannot substitute for the original setup. Positioned component checks must say
so; authentic cold/movie routes require continuous input and qualified initial
state. Captured physics/aux rows remain comparison-only.

For stateful tests, record applicable capture/restore/forward obligations and
any untested boundaries. This task supplements the
[level test standard](../../guide/contributing/level-test-standard.md), without
certifying the broader per-act, viewport, donor or roster matrices.

## Coverage inventory

| Game | Partial families | Scenario and execution matrix |
| --- | ---: | --- |
| Sonic 1 | 15 | [Sonic 1 regressions](2026-10-26-tas-tricks-s1.md) |
| Sonic 2 / official KiS2 | 12 | [Sonic 2 regressions](2026-10-26-tas-tricks-s2.md) |
| Sonic 3 / S&K / S3&K | 21 | [Sonic 3/Knuckles regressions](2026-10-26-tas-tricks-s3k.md) |

Current family dispositions after the arithmetic repair:

| Game | Exact local mechanism | Component evidence | Native fixture pending |
| --- | ---: | ---: | ---: |
| Sonic 1 | 3 | 9 | 3 |
| Sonic 2 / KiS2 | 0 | 12 | 0 |
| Sonic 3 / S&K / S3&K | 0 | 14 | 7 |
| Total | 3 | 35 | 10 |

These are mutually exclusive family ratings, not counts of outstanding routes.
All component rows still have publication-specific fixture obligations; the three
local S1 mechanisms do not certify their complete publication routes. The executed scoring mismatch and the related popup mappings are repaired;
qualification is recorded below.

The stable IDs below preserve the scope independently of later support ratings.
Each family must have exactly one owning row in its game matrix. A family may
need multiple distinct scenarios; one representative test does not qualify all
uses across its publications.

### Sonic the Hedgehog (Genesis)

| Audit ID | Mechanism | Original note references |
| --- | --- | --- |
| `S1_REVERSE_START` | Opposite-direction braking acceleration / left-right start | [1437S](https://tasvideos.org/1437S); [1847S](https://tasvideos.org/1847S); [2457S](https://tasvideos.org/2457S) |
| `S1_ROLL_JUMP` | Rolling-jump standing radii and changed launch height | [3402S](https://tasvideos.org/3402S); [5702S](https://tasvideos.org/5702S) |
| `S1_ROLL_REJUMP` | Immediate jump after a rolling-jump landing | [3402S](https://tasvideos.org/3402S) |
| `S1_DAMAGE` | Intentional damage, recoil and temporary invulnerability | [907S](https://tasvideos.org/907S); [1383S](https://tasvideos.org/1383S); [1437S](https://tasvideos.org/1437S); [1847S](https://tasvideos.org/1847S); [2457S](https://tasvideos.org/2457S); [3378S](https://tasvideos.org/3378S); [3402S](https://tasvideos.org/3402S); [3994S](https://tasvideos.org/3994S); [4348S](https://tasvideos.org/4348S); [5702S](https://tasvideos.org/5702S); [7988S](https://tasvideos.org/7988S) |
| `S1_PUSH_BLOCK` | Faster Marble block pushing using height or enemy contact | [907S](https://tasvideos.org/907S); [1383S](https://tasvideos.org/1383S) |
| `S1_OBJECT_PHASE` | Obstacle phase, spawn timing, platforms and switch manipulation | [907S](https://tasvideos.org/907S); [1383S](https://tasvideos.org/1383S); [1847S](https://tasvideos.org/1847S); [2457S](https://tasvideos.org/2457S); [3378S](https://tasvideos.org/3378S); [3994S](https://tasvideos.org/3994S) |
| `S1_EJECTION` | Terrain and solid-object positional ejection / wall entry | [907S](https://tasvideos.org/907S); [1383S](https://tasvideos.org/1383S); [1847S](https://tasvideos.org/1847S); [2457S](https://tasvideos.org/2457S); [3378S](https://tasvideos.org/3378S); [3402S](https://tasvideos.org/3402S); [3994S](https://tasvideos.org/3994S) |
| `S1_ZIP` | Embedded-terrain zip and high-speed wall/ceiling penetration | [907S](https://tasvideos.org/907S); [1437S](https://tasvideos.org/1437S); [1847S](https://tasvideos.org/1847S); [2457S](https://tasvideos.org/2457S); [3402S](https://tasvideos.org/3402S); [3994S](https://tasvideos.org/3994S); [4348S](https://tasvideos.org/4348S); [5702S](https://tasvideos.org/5702S); [7988S](https://tasvideos.org/7988S) |
| `S1_COG` | SBZ2 cog/wheel clip and high-speed jump | [3994S](https://tasvideos.org/3994S); [5702S](https://tasvideos.org/5702S) |
| `S1_BOSS` | Boss hit cadence, bounce positioning and final-hit timing | [907S](https://tasvideos.org/907S); [2457S](https://tasvideos.org/2457S); [3378S](https://tasvideos.org/3378S); [3994S](https://tasvideos.org/3994S); [5702S](https://tasvideos.org/5702S) |
| `S1_CAPSULE_RNG` | Animal/capsule RNG and pause manipulation | [1847S](https://tasvideos.org/1847S); [2457S](https://tasvideos.org/2457S); [3402S](https://tasvideos.org/3402S); [4348S](https://tasvideos.org/4348S) |
| `S1_WATER` | Air-budget routing and water-exit timing | [907S](https://tasvideos.org/907S); [1383S](https://tasvideos.org/1383S); [1847S](https://tasvideos.org/1847S) |
| `S1_POWERUPS` | Speed-shoes and invincibility route optimization | [3378S](https://tasvideos.org/3378S) |
| `S1_SPECIAL_COLLISION` | Special-stage collision quirks and immediate jumping | [7988S](https://tasvideos.org/7988S) |
| `S1_CAMERA_END` | Camera/trigger, offscreen signpost and end-input optimization | [907S](https://tasvideos.org/907S); [1437S](https://tasvideos.org/1437S); [1847S](https://tasvideos.org/1847S); [2457S](https://tasvideos.org/2457S); [3994S](https://tasvideos.org/3994S); [5702S](https://tasvideos.org/5702S) |

### Sonic the Hedgehog 2 (Genesis), including official Knuckles lock-on

| Audit ID | Mechanism | Original note references |
| --- | --- | --- |
| `s2-monitor-momentum` | Monitor-edge rolling-to-running conversion (monitor madness) | [5373S](https://tasvideos.org/5373S); [5385S](https://tasvideos.org/5385S); [5735S](https://tasvideos.org/5735S) |
| `s2-zips` | Object clips, terrain ejection and local zips | [1198S](https://tasvideos.org/1198S); [3101S](https://tasvideos.org/3101S); [3557S](https://tasvideos.org/3557S); [4932S](https://tasvideos.org/4932S); [5373S](https://tasvideos.org/5373S); [5385S](https://tasvideos.org/5385S); [5735S](https://tasvideos.org/5735S); [7453S](https://tasvideos.org/7453S); [8376S](https://tasvideos.org/8376S); [8629S](https://tasvideos.org/8629S); [8621S](https://tasvideos.org/8621S) |
| `s2-spindash-storage` | Stored spindash state on seesaws or moving launchers | [1198S](https://tasvideos.org/1198S); [5385S](https://tasvideos.org/5385S) |
| `s2-oil-hops` | Oil-surface hopping and boss-approach vertical repositioning | [1198S](https://tasvideos.org/1198S); [3101S](https://tasvideos.org/3101S); [3557S](https://tasvideos.org/3557S); [4932S](https://tasvideos.org/4932S); [5373S](https://tasvideos.org/5373S); [5735S](https://tasvideos.org/5735S) |
| `s2-camera-streaming` | Camera delay, offscreen object avoidance and level-ending camera tricks | [1198S](https://tasvideos.org/1198S); [3101S](https://tasvideos.org/3101S); [4932S](https://tasvideos.org/4932S); [5373S](https://tasvideos.org/5373S); [5385S](https://tasvideos.org/5385S); [5735S](https://tasvideos.org/5735S) |
| `s2-capsule-rng` | Capsule animal/explosion RNG and tally manipulation | [4932S](https://tasvideos.org/4932S); [5373S](https://tasvideos.org/5373S); [5735S](https://tasvideos.org/5735S); [8621S](https://tasvideos.org/8621S) |
| `s2-boss-optimization` | Early boss hits, bounce shaping and defeat-sequence skips | [1198S](https://tasvideos.org/1198S); [3101S](https://tasvideos.org/3101S); [3557S](https://tasvideos.org/3557S); [4932S](https://tasvideos.org/4932S); [5373S](https://tasvideos.org/5373S); [5385S](https://tasvideos.org/5385S); [5735S](https://tasvideos.org/5735S) |
| `s2-path-switch` | Collision-path desynchronization / layer-switch bypass | [7453S](https://tasvideos.org/7453S); [8376S](https://tasvideos.org/8376S); [8629S](https://tasvideos.org/8629S) |
| `s2-ring-attack` | Ring attack, extra lock-on monitors and buried-ring routes | [5385S](https://tasvideos.org/5385S) |
| `s2-special-stage-tally` | Minimum-ring special-stage tally optimization | [8376S](https://tasvideos.org/8376S) |
| `s2-damage-bounce` | Intentional damage, enemy rebounds and jump/bounce shaping | [658S](https://tasvideos.org/658S); [1198S](https://tasvideos.org/1198S); [3101S](https://tasvideos.org/3101S); [3557S](https://tasvideos.org/3557S); [4932S](https://tasvideos.org/4932S); [5373S](https://tasvideos.org/5373S); [5385S](https://tasvideos.org/5385S); [5735S](https://tasvideos.org/5735S); [7453S](https://tasvideos.org/7453S); [8376S](https://tasvideos.org/8376S); [8629S](https://tasvideos.org/8629S); [8621S](https://tasvideos.org/8621S) |
| `s2-combo-score` | Sky Chase enemy-combo retention | [5385S](https://tasvideos.org/5385S) |

### Sonic 3 & Knuckles (including official Sonic 3 and Sonic & Knuckles publications grouped by TASVideos)

| Audit ID | Mechanism | Original note references |
| --- | --- | --- |
| `s3k-movement` | Spindash charging, slope launch, rolling and ordinary damage boosts | [261S](https://tasvideos.org/261S); [632S](https://tasvideos.org/632S); [1330S](https://tasvideos.org/1330S); [1378S](https://tasvideos.org/1378S); [1508S](https://tasvideos.org/1508S); [1935S](https://tasvideos.org/1935S); [2416S](https://tasvideos.org/2416S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2802S](https://tasvideos.org/2802S); [2877S](https://tasvideos.org/2877S); [2953S](https://tasvideos.org/2953S); [4087S](https://tasvideos.org/4087S); [4118S](https://tasvideos.org/4118S); [4289S](https://tasvideos.org/4289S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [5427S](https://tasvideos.org/5427S); [5486S](https://tasvideos.org/5486S); [5639S](https://tasvideos.org/5639S); [6240S](https://tasvideos.org/6240S); [6655S](https://tasvideos.org/6655S); [8223S](https://tasvideos.org/8223S); [8453S](https://tasvideos.org/8453S); [8498S](https://tasvideos.org/8498S); [8501S](https://tasvideos.org/8501S); [9155S](https://tasvideos.org/9155S); [9169S](https://tasvideos.org/9169S); [9430S](https://tasvideos.org/9430S); [10042S](https://tasvideos.org/10042S) |
| `s3k-zip` | Terrain penetration, wall/floor zips and subpixel entries | [632S](https://tasvideos.org/632S); [1330S](https://tasvideos.org/1330S); [1378S](https://tasvideos.org/1378S); [1508S](https://tasvideos.org/1508S); [1935S](https://tasvideos.org/1935S); [2416S](https://tasvideos.org/2416S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2802S](https://tasvideos.org/2802S); [2877S](https://tasvideos.org/2877S); [2953S](https://tasvideos.org/2953S); [4087S](https://tasvideos.org/4087S); [4110S](https://tasvideos.org/4110S); [4118S](https://tasvideos.org/4118S); [4289S](https://tasvideos.org/4289S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [5427S](https://tasvideos.org/5427S); [5486S](https://tasvideos.org/5486S); [5639S](https://tasvideos.org/5639S); [6240S](https://tasvideos.org/6240S); [6655S](https://tasvideos.org/6655S); [8223S](https://tasvideos.org/8223S); [8453S](https://tasvideos.org/8453S); [8498S](https://tasvideos.org/8498S); [8501S](https://tasvideos.org/8501S); [9155S](https://tasvideos.org/9155S); [9169S](https://tasvideos.org/9169S); [9430S](https://tasvideos.org/9430S); [10042S](https://tasvideos.org/10042S) |
| `s3k-stair` | Downward stair clipping and landing-depth thresholds | [4087S](https://tasvideos.org/4087S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [5427S](https://tasvideos.org/5427S); [5486S](https://tasvideos.org/5486S); [5639S](https://tasvideos.org/5639S); [6655S](https://tasvideos.org/6655S); [8453S](https://tasvideos.org/8453S); [8498S](https://tasvideos.org/8498S); [9430S](https://tasvideos.org/9430S) |
| `s3k-offscreen` | Offscreen solid-object passage and object loading/unloading | [632S](https://tasvideos.org/632S); [1330S](https://tasvideos.org/1330S); [1508S](https://tasvideos.org/1508S); [1935S](https://tasvideos.org/1935S); [2416S](https://tasvideos.org/2416S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2802S](https://tasvideos.org/2802S); [2877S](https://tasvideos.org/2877S); [4087S](https://tasvideos.org/4087S); [4118S](https://tasvideos.org/4118S); [4289S](https://tasvideos.org/4289S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [5427S](https://tasvideos.org/5427S); [5486S](https://tasvideos.org/5486S); [5639S](https://tasvideos.org/5639S); [6240S](https://tasvideos.org/6240S); [6655S](https://tasvideos.org/6655S); [8223S](https://tasvideos.org/8223S); [8453S](https://tasvideos.org/8453S); [8498S](https://tasvideos.org/8498S); [8501S](https://tasvideos.org/8501S); [9155S](https://tasvideos.org/9155S); [9169S](https://tasvideos.org/9169S); [9430S](https://tasvideos.org/9430S); [10042S](https://tasvideos.org/10042S) |
| `s3k-camera` | Camera lag, locks, look-down offsets and low-camera boss setups | [632S](https://tasvideos.org/632S); [1330S](https://tasvideos.org/1330S); [1935S](https://tasvideos.org/1935S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2802S](https://tasvideos.org/2802S); [2877S](https://tasvideos.org/2877S); [4087S](https://tasvideos.org/4087S); [4289S](https://tasvideos.org/4289S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [5427S](https://tasvideos.org/5427S); [5486S](https://tasvideos.org/5486S); [5639S](https://tasvideos.org/5639S); [6240S](https://tasvideos.org/6240S); [6655S](https://tasvideos.org/6655S); [8453S](https://tasvideos.org/8453S); [8498S](https://tasvideos.org/8498S); [8501S](https://tasvideos.org/8501S); [9155S](https://tasvideos.org/9155S); [9169S](https://tasvideos.org/9169S); [9430S](https://tasvideos.org/9430S); [10042S](https://tasvideos.org/10042S) |
| `s3k-slope` | Slope glitch / retained adhesion, antigravity and animation state | [1935S](https://tasvideos.org/1935S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2877S](https://tasvideos.org/2877S); [4087S](https://tasvideos.org/4087S); [4118S](https://tasvideos.org/4118S); [4289S](https://tasvideos.org/4289S); [4514S](https://tasvideos.org/4514S); [5427S](https://tasvideos.org/5427S); [5486S](https://tasvideos.org/5486S); [5639S](https://tasvideos.org/5639S); [6655S](https://tasvideos.org/6655S); [8223S](https://tasvideos.org/8223S); [8453S](https://tasvideos.org/8453S); [8498S](https://tasvideos.org/8498S); [9169S](https://tasvideos.org/9169S); [9430S](https://tasvideos.org/9430S) |
| `s3k-coop` | Two-player Tails carries, pushes, attacks and offscreen assistance | [1330S](https://tasvideos.org/1330S); [1508S](https://tasvideos.org/1508S); [1935S](https://tasvideos.org/1935S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2802S](https://tasvideos.org/2802S); [2877S](https://tasvideos.org/2877S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [6240S](https://tasvideos.org/6240S) |
| `s3k-respawn` | Tails forced return, manual respawn jumps and AI positioning | [1330S](https://tasvideos.org/1330S); [1508S](https://tasvideos.org/1508S); [1935S](https://tasvideos.org/1935S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2802S](https://tasvideos.org/2802S); [2877S](https://tasvideos.org/2877S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [6240S](https://tasvideos.org/6240S) |
| `s3k-slots` | Ordinary object-slot allocation order and capacity manipulation | [1935S](https://tasvideos.org/1935S); [2877S](https://tasvideos.org/2877S); [9155S](https://tasvideos.org/9155S); [10042S](https://tasvideos.org/10042S) |
| `s3k-act` | Boss/act skips, transition deaths, timer resets and checkpoint routing | [1330S](https://tasvideos.org/1330S); [1508S](https://tasvideos.org/1508S); [1935S](https://tasvideos.org/1935S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2802S](https://tasvideos.org/2802S); [2877S](https://tasvideos.org/2877S); [4087S](https://tasvideos.org/4087S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [5486S](https://tasvideos.org/5486S); [6655S](https://tasvideos.org/6655S); [8498S](https://tasvideos.org/8498S); [9155S](https://tasvideos.org/9155S); [9169S](https://tasvideos.org/9169S); [9430S](https://tasvideos.org/9430S); [10042S](https://tasvideos.org/10042S) |
| `s3k-glide` | Knuckles gliding, wall climbing, superglides and collision entries | [1378S](https://tasvideos.org/1378S); [4118S](https://tasvideos.org/4118S); [4289S](https://tasvideos.org/4289S); [5427S](https://tasvideos.org/5427S); [5486S](https://tasvideos.org/5486S); [5639S](https://tasvideos.org/5639S); [8223S](https://tasvideos.org/8223S); [8498S](https://tasvideos.org/8498S); [9169S](https://tasvideos.org/9169S); [9430S](https://tasvideos.org/9430S) |
| `s3k-flight` | Solo Tails flight and flight-assisted collision routing | [4087S](https://tasvideos.org/4087S) |
| `s3k-nolr` | No Left/Right directional-input completion | [9169S](https://tasvideos.org/9169S) |
| `s3k-shields` | Fire/bubble/lightning shield actions and collision boosts | [2416S](https://tasvideos.org/2416S); [2877S](https://tasvideos.org/2877S); [4289S](https://tasvideos.org/4289S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [6240S](https://tasvideos.org/6240S); [6655S](https://tasvideos.org/6655S); [8453S](https://tasvideos.org/8453S); [8501S](https://tasvideos.org/8501S) |
| `s3k-sphere-ring` | Blue Sphere outside-fill / red-path conversion exploit | [5639S](https://tasvideos.org/5639S) |
| `s3k-sphere-motion` | Blue Sphere turns, red-sphere avoidance and bumper movement | [1378S](https://tasvideos.org/1378S); [2416S](https://tasvideos.org/2416S); [5427S](https://tasvideos.org/5427S); [5639S](https://tasvideos.org/5639S); [8223S](https://tasvideos.org/8223S); [8453S](https://tasvideos.org/8453S) |
| `s3k-sphere-timing` | Special-stage entry buffering and emerald-spawn positioning | [5639S](https://tasvideos.org/5639S); [8453S](https://tasvideos.org/8453S) |
| `s3k-ngplus` | Newgame+ starting emerald/save state | [2802S](https://tasvideos.org/2802S); [4515S](https://tasvideos.org/4515S); [6240S](https://tasvideos.org/6240S); [8501S](https://tasvideos.org/8501S) |
| `s3k-rings` | Ring attack, lightning attraction and cooperative ring collection | [4289S](https://tasvideos.org/4289S); [6240S](https://tasvideos.org/6240S) |
| `s3k-rng` | Pause/universal-clock/RNG and allocation-order manipulation | [1330S](https://tasvideos.org/1330S); [1935S](https://tasvideos.org/1935S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2877S](https://tasvideos.org/2877S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [5486S](https://tasvideos.org/5486S); [8498S](https://tasvideos.org/8498S) |
| `s3k-boss` | Boss quick hits, capsule timing and unusual hitbox/arena routes | [261S](https://tasvideos.org/261S); [632S](https://tasvideos.org/632S); [1330S](https://tasvideos.org/1330S); [1378S](https://tasvideos.org/1378S); [1508S](https://tasvideos.org/1508S); [1935S](https://tasvideos.org/1935S); [2416S](https://tasvideos.org/2416S); [2460S](https://tasvideos.org/2460S); [2784S](https://tasvideos.org/2784S); [2802S](https://tasvideos.org/2802S); [2877S](https://tasvideos.org/2877S); [4087S](https://tasvideos.org/4087S); [4110S](https://tasvideos.org/4110S); [4118S](https://tasvideos.org/4118S); [4289S](https://tasvideos.org/4289S); [4514S](https://tasvideos.org/4514S); [4515S](https://tasvideos.org/4515S); [5427S](https://tasvideos.org/5427S); [5486S](https://tasvideos.org/5486S); [5639S](https://tasvideos.org/5639S); [6655S](https://tasvideos.org/6655S); [8223S](https://tasvideos.org/8223S); [8453S](https://tasvideos.org/8453S); [8498S](https://tasvideos.org/8498S); [8501S](https://tasvideos.org/8501S); [9155S](https://tasvideos.org/9155S); [9169S](https://tasvideos.org/9169S); [9430S](https://tasvideos.org/9430S); [10042S](https://tasvideos.org/10042S) |

## Validation and delivery checkpoint

The three game test branches are combined at
`dcd8eff7bf9a7f25ece71d15fb45135e07868c6c`, against integration base
`c50876b184612428ccaf22763412f88ffe505432`. Their focused runs passed 76 new cases
and 94 selected existing cases across separate invocations, with no skips in the
retained passing selections. These totals are not a single combined run.

The S2 enemy-contact diagnostic exposed a production mismatch: the sixteenth
ordinary chained kill awarded 1,000 points instead of the ROM's 10,000. The
user authorized a shared scoring repair with additional validation. Commit
`5160b94bb3967f6a52280c6675c8c4a5fc7f2862` repairs the arithmetic;
`b943cad36bd723ae64bb9e2d0497aebf575c2b0e` repairs its popup consumers. The S2
matrix records the original red diagnostic and the observed red/green outcomes.

Tool preflight passed with `LUA_BIN=/usr/bin/lua5.4`; the default Lua command failed
the version prerequisite before any tests ran. Root original-ROM identities were
checked: S1 REV01 `AFE05EEE`, S2 REV01 `7B905383`, locked-on S3&K `63522553`, KiS2
`2AC1E7C6` (CRC32). ROMs remain local.

The parent inspected the change-based plan at
`506c7b4e87c4e038c55fe36bf76a42c0cfd13208`: 3,531 smoke candidate classes from a
4,173-class inventory, plus guards. That scope is disproportionate to this
bounded arithmetic edit inside the large shared player class. Under the
repository's proportionate-validation rule, the combined selection contains all
12 new TAS classes, scoring/reset/rewind and direct-consumer checks, and the
required S3K gates (including both same-named level-loading classes). No broad
smoke or release-only full suite is claimed.

The 25-class run on `506c7b4e` completed with **205 passed, zero failures, errors
or skips** at `2026-10-26T11:13:55Z`. Queue waiting was 396 seconds; Maven execution
was 1:30, and command wall time was 487.96 seconds. The exact 25 class identities
and their totals were checked. Read-only native review accepted the arithmetic,
word wrap and captured-state units, but found a newly exposed display error:
S2 and S3K popup mappings defaulted 10,000 points to the 100-point frame. The S2
1,000-point mapping also selected the wrong frame. The Sol/high follow-up
corrects both mappings and adds actual non-null-player coverage for the Turtloid,
Rexon and deferred ordinary-enemy consumers; their older destruction tests bypass
scoring with null players. All three new consumer scenarios reach kill 16 through
15 real prior destruction callbacks and verify both the score delta and frame-5
draw payload. The mapping regression checks construction and later score updates
for 100/200/500/1,000/10,000 in all three games. These are draw-payload checks, not
pixel or complete-route verification.

No intentional divergence was introduced or retired. The S3K discrepancy ledger
links this correction as a scope note without adding an accepted deviation. The
discovered arithmetic failure and its red/green evidence belong in this task's
S2 matrix; no unresolved arithmetic bug is added to the open-bug ledger.

### Combined command

`OPENGGF_MAIN` denotes the absolute main-checkout path holding the existing
original ROMs. The commands below substitute that variable for the resolved
absolute paths used in the executed invocations.

Executed in the integration worktree at `506c7b4e`, with
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk` and `LUA_BIN=/usr/bin/lua5.4`:

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=com.openggf.game.sonic1.TestTasS1Damage,com.openggf.game.sonic1.objects.TestTasS1PushBlock,com.openggf.game.sonic1.specialstage.TestTasS1SpecialStageContacts,com.openggf.game.sonic2.TestTasS2OilMomentum,com.openggf.game.sonic2.objects.TestTasS2EnemyContact,com.openggf.game.sonic2.objects.TestTasS2PathSwitch,com.openggf.game.sonic2.objects.TestTasS2SpecialStageTally,com.openggf.game.sonic2.objects.TestTasS2StoredSpindash,com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereMotion,com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereRingConversion,com.openggf.sprites.playable.TestTasS3kRespawnBypass,com.openggf.tests.TestTasS1Movement,com.openggf.sprites.playable.TestBadnikChainScoring,com.openggf.sprites.playable.TestAbstractPlayableSpriteRewindCapture,com.openggf.level.objects.TestPoweredScreenAttack,com.openggf.level.objects.TestDestructionEffects,com.openggf.game.sonic2.objects.badniks.TestTurtloidBadnikInstance,com.openggf.sprites.playable.TestPlayableGroundTransitions,com.openggf.game.rewind.TestS2RexonSolOwnershipRewind,com.openggf.level.objects.TestS3kEnemyScorePresentation,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils' \
  "-Dsonic1.rom.path=${OPENGGF_MAIN}/s1.gen" \
  "-Dsonic2.rom.path=${OPENGGF_MAIN}/s2.gen" \
  "-Ds3k.rom.path=${OPENGGF_MAIN}/s3k.gen" test
```

The 87 new cases at this checkpoint consist of 39 S1, 22 S2, 16 S3K, nine
shared scoring boundaries and one repeated-rewind test. They are included in
the 205-case run; initial worker totals are not added to that run's count. The
remaining 118 cases are existing checks. The later popup follow-up adds its own
consumer coverage and is recorded separately.

### Final source qualification

The popup follow-up contributes 33 new cases in two classes. The final change
contains **120 new cases in 15 new classes and one existing rewind class**. Its
red run produced nine expected mapping/consumer failures out of 33 cases, with
zero errors or skips; the worker's corrected 39-case selection passed. These
worker runs are distinct from the parent's combined validation.

Upstream `adb58c90fb4da67c7d2f3ee2500c20f7cbbd89bf` added documentation only in
the native-gap plan and trace-frontier log. It merged without conflicts. The
composed source is `138b8a4c8c179a884b1349902da92b298ee57982`; only the popup
production changes and their new tests differ from the 205-case tested source.
The parent's affected mapping/consumer checks and required S3K gates passed on
that source: **99 tests in eight classes, zero failures, errors or skips**,
completed `2026-10-26T11:22:29Z`. Queue wait was zero; Maven execution was 1:37
and wall time was 98.63 seconds. Unchanged arithmetic/TAS checks were not repeated.

```bash
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=com.openggf.level.objects.TestEnemyChainPointsMapping,com.openggf.level.objects.TestS2ChainScoreConsumers,com.openggf.level.objects.TestS3kEnemyScorePresentation,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils' \
  "-Ds3k.rom.path=${OPENGGF_MAIN}/s3k.gen" test
```

Static checks verified all 48 IDs occur in exactly one owning game-matrix row,
central/guide links resolve, mirrored S2 pitfall guidance matches, and whitespace
checks pass. Strict citation checking over the new tests/matrices and both popup
production files passed: 72 exact anchors and 13 nearby valid anchors. The
worker also matched the shared sprite file's 11 stale citations and one missing
file to baseline; no new citation debt was introduced. The local disassembly
checkouts differ from the pins, so verification used immutable pinned objects.

Consumed Maven diagnostics were removed. The 205-case and 99-case parent runs
overlap and must not be added as distinct coverage. They establish bounded
component/mechanism qualification, not a broad smoke pass or movie playback.
Remaining native route captures and the existing global P1/P2 and enemy/block
counter-sharing limits are explicitly retained in the game matrices.
