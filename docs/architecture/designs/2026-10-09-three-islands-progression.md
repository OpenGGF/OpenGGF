# Three Islands character story and anchor progression

Work in `feature/ai-three-islands`, based on
`0ee4ada968741d57b1597ebdd4d93278dacb852d`. The requested change replaces freely
accessible onward areas and narrator-led mystery with character motives, a clear
ultimate goal, and discoverable local progression.

## Story and rules

Sonic promises to restore the islands' separate skies. The shrine rescue exposes
Eggman's borrowed power; later emerald anchors lead toward the Convergence Engine.
Tails contributes the technical means to reverse its current, and Knuckles knows
how the Master Emerald can safely return the power. The archive provides the
original destinations before the final confrontation. Dialogue replaces omniscient
scene introductions, prophecy and mechanical tutorials; inscriptions and physical
observations remain in the optional garden and orchard stories.

Every area requires its dungeon discovery, relay and outdoor guardian(s). The
inner sentries protect local knowledge or a control component. That discovery
allows the relay to drain the guardian shield. The surviving guardian still holds
the road closed until defeated. Death Egg uses the same preparation before its two
bosses and ending. Optional puzzles, supplies, traveller conversations and ordinary
patrols do not become completion chores.

`Field` owns shield readiness and the physical eastern seal. Its collision spans
the field edge, including both endpoints; running or walking around the trail
cannot bypass it. `FieldScreen` draws the curtain and guardian shields and gates
actual interactions. `Game.travel` independently rejects forward travel until the
local chapter is cleared. Island crossings retain the all-anchors requirement.
Backtracking stays open. Victory refreshes the current field without moving the
player; dungeon exits refresh discoveries before relay admission.

Existing discovery and clear flags remain the save contract. Previously cleared
areas stay open without requiring retroactive discoveries. Partially explored old
saves keep both discoveries even if found in the former reverse order. Dungeon
checkpoints, repeat rewards and missing-ROM chapter skipping remain in place.
The journal recalls the arrival's goal plus actual discoveries, without revealing
unvisited locations. Traveller follow-ups are authored per area, replacing a generic
narrator response.

## Decisions and verification

Rejected retaining free local travel with only an exit message: it would preserve
the reported immediate Green Hill-to-Star Light bypass. Rejected gating on every
optional puzzle/cache/patrol: it would turn exploration into a completion checklist.
The required chain uses the existing story dungeons and local power system instead.

The change-based plan selects 3,075 ordinary classes plus guards because example
paths are unclassified (and includes a pre-existing untracked S2 movie, untouched by
this work). Proportionate validation applies to this isolated mod: no engine,
public API, build policy or stock gameplay changes. Focused checks cover model
collision across all edge heights, discovery readiness, legacy clears, puzzles,
packaging, and the ROM scene-host route through all ten areas. The scene route uses
real interaction input for dungeon sentries, relay and boss admission, save/continue,
then victory and physical onward travel; debug victory resolves combat, while the
existing battle and balance checks exercise combat separately.

Focused command on the above base plus this change:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestThreeIslandsExample,TestThreeIslandsScene' \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PWD/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$PWD/Sonic 3 & Knuckles (W) [!].gen" test
```

The first run passed the new ten-area route but caught an interaction check
accidentally inserted into relay drawing: rendering could open dialogue. Removed
that mutation from drawing. The corrected run finished on 2026-10-09 at 11:36:31
BST: all 50 creator tests, package validation and 10 scene-host tests passed, zero
failures or skips. All ten interiors and their subsequent relay/guardian/travel
chains were exercised. This is focused mod validation, not an engine-suite pass.

Native `ExampleModCapture` with `-XstartOnFirstThread` verified Green Hill's sealed
edge, shielded guardian and party dialogue at 400x224, scale 2. Visual inspection
moved the exit label away from the party/HUD; a fresh native capture verified that
presentation-only adjustment. The capture rebuilt and validated the final sources.
An initial capture attempted during test recompilation could not load the capture
class; the completed compilation was used for the successful captures.

Installation uses `python3 examples/three-islands/build.py --skip-engine --install`
against the unchanged local engine. It preserves saves and other mod entries and
updates only Three Islands' installed jar and exact-hash trust entry.

## Grounded story review

Follow-up requested after `1327bef32ed8858c2b0f60b118410f78c23a4a16`: sense-check the
whole script and replace the "cheesy mysterious" voice. Reviewed all 122 scenes
against `Game`, `FieldScreen`, `Dungeon`, zone rewards and optional interaction order.
This supersedes the earlier story treatment above; progression gates remain intact.

| Problem in the first pass | Revised account |
| --- | --- |
| Unexplained tomorrow letters, prophetic logs, talking stone and reflected aircraft | Local maintenance records, personal concerns, a work-bell mechanism and a gardener's supplies; no time-travel claims without a supporting story. |
| A stolen lens apparently inside both the Bomb and the relay | Lens on the relay, emerald in the Bomb; the vault's maintenance setting unlocks the relay lens. |
| Vague reversal of current, water, power and sky treated as interchangeable | Local shield circuits, a pump power transfer, a turbine bypass, and a distinct main engine. Disabling a shield does not also stop its controller or the road barrier. |
| Angel Island shares underground water with other islands despite floating | Hydrocity's intake, chambers and spillways are all inside Angel Island. |
| Characters announce changes to scenery that the interaction does not render | Reports concern local controls, gauges or the operating state; no restored waterfall animation is promised. |
| A flight in the Tornado despite actual ring travel | Service rings handle island and ship crossings. Escape pods cover the shutdown; the Tornado remains at the workshop. |
| Knuckles rejects obvious written proof, then changes his mind after a fight | He believes the anchor stabilises the island during emerald repairs, refuses a risky shutdown, then examines a load report and the cable routing. He switches off his own controller and joins to recover the emerald. |
| Optional camps and NPCs know unvisited discoveries | Camps work before or after local progress. Follow-ups require the dungeon record; Green Hill requires the actual reunion. A legacy relay-only save cannot trigger them. |
| Main engine must remain a threat after all local anchors fall | Local controllers secure Eggman's installations; the Master Emerald still powers the central engine. The archive's survey and discharge settings prepare a safe core shutdown. |
| Bell reward, journal and sluice labels tell different stories | The bell compartment and journal both contain equipment and a crew photograph. Turning the wheels closes the inlets; the prompts now say Open/Closed. |

Removed repeated one-liners, slogan-like declarations and unsupported technical
confidence. Sonic asks direct questions and offers practical help; Tails explains
what he can measure and where he is uncertain; Knuckles has a specific responsibility
and mistake. Written puzzle clues remain optional, with no new objective checklist.
The unused legacy prologue/village scenes, title, credits and manifest were also
reviewed so debug/replay text does not reintroduce the old premise.

The change-based plan again selects 3,075 classes plus guards through the examples
fallback. The isolated mod still qualifies for proportionate validation: content,
UI labels and one bounded dialogue selection correction; no shared engine changes.
The focused commands above cover all creator rules and the full ten-area scene route.
A new host regression visits every traveller with relay-only, dungeon and reunion
flags, exercising the actual interaction and checking the chosen script.

Validation completed on 2026-10-09 against the follow-up base above plus this change:
all 50 creator tests, package validation and 11 ROM scene-host tests passed with zero
failures or skips. All ten dungeon/relay/guardian routes and the new traveller-state
checks ran. Story scene coverage, font coverage and the 160-character dialogue limit
passed. This is focused mod validation, not a full engine-suite pass. Installation
uses the same build/install command and preserves existing save files.
