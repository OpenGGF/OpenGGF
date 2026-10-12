# Three Islands: distinct expeditions

Task: replace repeated exterior clearings and mirrored three-room dungeons with
substantial entrances, authored routes, puzzles and increasing exploration challenge.
Current checkout: `feature/ai-three-islands`, base
`30398f3e679873a4620fdce1e9d2329b54d60eab`.

## Design and evidence

`AreaLayout` owns explicit room/passage graphs. Adjacent rooms do not automatically
connect. Every post-Green-Hill exterior has its own winding route, side districts and
mechanism-controlled crossing; the seaside landscape retains its already expanded
bell garden, flooded orchard and coastal mysteries. Dungeon entrances move with their
landmarks rather than assuming northwest coordinates. Return, camp and travel code
uses those authored positions. Solid 160-pixel-wide facades have ROM-textured walls,
buttresses, recessed doors and steps; nearby camera framing exposes their upper walls.
Non-Green-Hill ground is subdued to separate traversable terrain from structures.

Interiors grow from six rooms including the side crypt in Green Hill to sixteen in
the Death Egg. The first sentries protect entry into the wings; the last passage needs
both the inner sentries and the area's mechanism puzzle. Later interiors add patrols
and longer routes. Ordinary encounters remain avoidable and existing chapter-level
catch-up is retained; difficulty comes from exploration and deduction rather than
mandatory grinding. See the example README for the per-zone route/task catalogue.

`MechanismPuzzle` has three rules: independent repairs, ordered sequences and reversible
three-lamp circuits. Themes differ by area: floodgate rescue, astronomical alignment,
freight power, compass bearings, reservoir pressure, a miner's verse, memorial vows,
tidal pressure, a flight checklist and archive chronology. The final sequence combines
four controls. Instructions are local inscriptions and become journal entries only
when read. No timers, consumed keys, unseen-objective markers or irreversible mistakes.

Existing scene flags store individual repairs and latched puzzle completion. Partial
sequence/circuit attempts reset on re-entry; a save cannot preserve half a phrase while
forgetting its lamps. Completed legacy interiors retain access without new puzzle flags.
An existing relay discovery also holds its crossing open: otherwise an old sanctuary
checkpoint on the far side could strand the player away from the new controls. The
save format, story rewards, party recruitment and outdoor anchor requirements stay intact.

Rejected retaining the reflected GHZ template with different labels: it would not change
routefinding. Initial flood fills caught a facade obstructing its north approach; moving
the doorway toward the south of its clearing left a real path around the footprint.
A proposed static layout cache failed the creator validator's static-state restriction;
layouts are immutable instance-owned records instead. Native review replaced stripe-heavy
floor samples, separated floors from structures, and corrected entrance camera framing.

## Verification

The change-based plan against the pinned base selects 3,075 ordinary classes plus guards
because examples are unclassified (also listing an unrelated untracked user BK2). Use
proportionate focused mod validation: no shared engine implementation or API changed.
Stock Sonic zone matrices, trace physics and structural engine tests do not exercise
these custom scene graphs.

Model checks flood-fill each locked/unlocked graph, verify every control can be reached,
prevent bypassing either guard or the puzzle, test safe routes around patrols, reject
duplicate/diagonal rooms, retry wrong sequences and reversed circuit handles, and check
save round trips and legacy access. Production scene checks exercise all ten interiors
through real interaction input, both sentry battles, puzzle controls, partial indoor
save/continue, rewards, return/re-entry, outdoor controls, relays, bosses and travel.

Final command (base plus the task's working changes):

```sh
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestThreeIslandsExample,TestThreeIslandsScene' \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PWD/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$PWD/Sonic 3 & Knuckles (W) [!].gen" test
```

The local queue needs elevated access to its `.git` lock. Native `ExampleModCapture`
uses the documented macOS `-XstartOnFirstThread` launch with display access. Temporary
captures under `/tmp/three-islands-expedition-*` show all ten facades and entrance rooms
at 400 x 224, scale 2. No runtime assets come from those images; every texture is decoded
from the supplied ROMs. The temporary capture script is directly regenerable from
`AreaLayout` and is not a new maintained tool.

Completed focused run at 13:29:27 BST on 2026-10-09: 54 creator model tests,
package validation, and 11 production scene checks passed, with zero failures or skips.
All ten zones and all ten interiors were exercised. This is focused mod validation,
not a full engine-suite pass. The extended combat simulation was then changed to consume
every authored interior encounter, including the new patrols, instead of the former
fixed three-fight sequence. Its focused rerun (`--lean -Dmse=off
'-Dtest=TestThreeIslandsExample' test`) completed at 13:30:30 BST: all 54 model tests
and package validation passed without skips. It covers twenty seeds per zone, including
level-one Sonic in Green Hill. The last copy pass names the actual circuit handles in
the clue and matches outdoor control labels to their repairs; the final package validator
and native doorway captures checked that build.

Native inspection covered all twenty exterior/interior views. A final focused capture
of Mystic Cave, Emerald Hill and Launch Base verified the dark recessed door drawn from
a cached ROM-palette image and removed water-textured trim from the dry workshop entrance.
One attempted capture overlapped Maven's test-class recompilation and could not load
`ExampleModCapture`; rerunning after compilation completed produced the final images.

Built and locally installed with `python3 examples/three-islands/build.py --skip-engine
--install`; zero package findings. The built jar, installed jar and enabled/trusted
mod-state hash match. Existing saves and unrelated user BK2 files were preserved.

## Commit and push follow-up

The subsequent music-continuity task tested these unchanged expedition sources together
with its audio changes over base `30398f3e679873a4620fdce1e9d2329b54d60eab`.
Its queued Java 21 `TestThreeIslandsExample,TestThreeIslandsScene` run completed at
13:41:50 BST with 63 nested creator tests and 11 production scene cases plus packaging,
zero failures/errors/skips, exercising all ten zones/interiors. The audio follow-up
at 13:43:18 passed 64 nested creator tests and the real shrine-entry music case.
See [music validation](2026-10-08-three-islands.md#area-music-continuity-2026-10-09).
No expedition code changed after those checks; committing this work does not require
repeating them. The installed jar already contains both changes. This remains focused
mod validation, not a full engine-suite pass.
