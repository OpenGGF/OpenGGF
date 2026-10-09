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
