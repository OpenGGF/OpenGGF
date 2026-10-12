# Three Islands: exploration through evidence

Task: make the adventure less prescriptive and expand Green Hill's mysteries in the
current `feature/ai-three-islands` checkout. Base: `2a14fea0c29f071fe09b1cda82417e506c447a2c`.

Green Hill now measures 1536 x 1024 rather than 960 x 640. Existing camps, shrine,
relay, patrols and boss keep their identities and positions. The eastern trail moves
with the field boundary, including backtracking arrival and its on-screen sign.
Other fields and dungeon geometry retain their original dimensions.

The extension has a bell garden, a southern grove and bridge, a flooded orchard and
a shoreline echo. The garden inscription encodes sunset, sunrise, high sun; wrong
notes silence the response and allow an immediate retry, without a timer or penalty.
The completed phrase yields a shield and a drawing of separate skies. Two independently
persistent sluices reveal an orchard crossing in either order. Its letter is dated
tomorrow. The shoreline reflection shows a plane before Sonic meets its pilot.
Neither puzzle gates bosses, travel, recruitment or chapter catch-up levels.

`Field` owns water/collision and mechanism state. The renderer uses that water predicate
for the revealed crossing and the same bank rectangles as collision. Existing scene
flags preserve solved bells, each sluice and rewards through the current save codec;
a partial bell phrase is deliberately local and resets when the field reloads. There
is no new save format. New discoveries do not affect the two chapter discoveries'
level milestone calculation.

Removed the permanent objective banner, map-wide unseen content markers, and journal
entries for unknown discoveries. The journal retains only witnessed scenes, including
those found outside while visiting an interior. Nearby action prompts, route signs,
combat controls and rest/save feedback remain. Reworked repeated compass directions
into character observations, especially the opening and traveller conversations.
The existing automatic arrival conversations and main plot are retained.

Rejected simply enlarging the empty clearing and adding more quest markers: that
would increase walking while preserving the original prescription problem. New
content uses optional deductions and changed terrain, with visible/textual responses.
The puzzles use existing ROM props; no engine, public Mod API or ROM physics changes.

## Verification

The change-based plan against the base selects 3,075 ordinary classes plus guards
because examples are unclassified (and includes the user's unrelated untracked BK2).
Use proportionate focused validation: model connectivity/puzzle regressions, creator
packaging, all Three Islands ROM scene checks, and native visual inspection. The engine
suite, trace parity and stock-zone matrices do not exercise this custom scene's geometry.

Results and capture details are recorded after completion below.

Final focused command, on the base above plus these uncommitted changes:

```sh
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestThreeIslandsExample,TestThreeIslandsScene' \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PWD/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$PWD/Sonic 3 & Knuckles (W) [!].gen" test
```

Completed 2026-10-09 at 11:10:55 BST: 50 creator model tests, packaging/validation,
and 9 production scene tests passed with no failures or skips. All 10 zones and
10 interiors were exercised. The new scene route uses actual interaction input for
the inscription, bells, wheels and letter, checks repeat rewards, and continues the
saved puzzle state. This is focused mod validation, not a full engine-suite pass.
The queue initially required access to its `.git` lock outside the filesystem sandbox;
the authorized elevated invocation ran successfully.

Native `ExampleModCapture` at 400 x 224 (scale 2) verified the garden, flooded orchard,
revealed crossing and letter dialogue under `/tmp/three-islands-exploration-final`.
The final crossing uses ROM checkerboard stone; its final native frame is
`/tmp/three-islands-exploration-stone/frame-00120.png`. Visual inspection removed a
doubled Starpost prop from the mechanisms. Capture scripts use `back`, not `start`,
for the Start key. One premature capture during Maven test recompilation had no
capture class to launch; the completed build was used for the successful final check.
Temporary capture builds and saves are disposable, not checked-in assets.

Built and locally installed with `python3 examples/three-islands/build.py --skip-engine
--install`; the package validator reports zero findings. Existing saves remain intact.


## Green Hill water follow-up

Replaced the static, half-size chunk crop with GHZ's original 16 x 16 background
reflection block. Sonic 1 World REV01 ROM identity was verified against SHA-1
`69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b`. Decoding `Blk16_GHZ` at `$3C19C`
identifies block `$61` as `$40F6,$40F7,$40F8,$40F9`: four unflipped, row-major
patterns in palette line 2. The mod reads them through `SceneRomArt.tiles` from
`Nem_GHZ_1st` at `$3CB3C`, and reads all four colour steps from
`Pal_GHZCyc_Water` at `$1B7E`. `PCycGHZ_Go` writes colours 8–B every six ticks
(timer reload 5). The four images are cached during stage loading and shared
by field, dialogue and battle rendering. No runtime disassembly assets or new
engine APIs are involved.

The investigated `Art_GhzWater` at `$66A96` matches the disassembly's two raw
frames, but is vertical waterfall animation. It is not the horizontal surface
reflection used here. Keeping the old scaled crop and merely tinting it would
retain the wrong texture density and would not preserve palette indices.

Repeated the focused Maven command above on base `2a14fea0c2` plus the complete
exploration/water changes; completed 2026-10-09 at 11:20:47 BST. All 12 outer tests
passed, zero failures/errors/skips: creator model-suite execution and packaging,
plus 10 production scene tests exercising all 10 zones and interiors. The new
regression independently decodes the ROM patterns and checks every water pixel
through 25 ticks, including six-tick holds and cycle wrap. This remains focused
mod validation, not a full engine suite. The inspected change-based plan still
selects 3,075 classes plus guards for unclassified examples; the unchanged engine
and public API justify the focused scope.

Native `ExampleModCapture` verified consecutive palette steps at 400 x 224,
scale 2, in `/tmp/three-islands-water/frame-00048.png` and `frame-00054.png`:

```sh
java -XstartOnFirstThread -cp "target/test-classes:target/classes:$(cat target/test-classpath.txt)" \
  com.openggf.mods.code.ExampleModCapture --rom 'Sonic 3 & Knuckles (W) [!].gen' \
  --mod examples/three-islands --out /tmp/three-islands-water --jump field:ghz \
  --script '1:jump=position:360:270' --ticks 60 --every 6
```

The first native launch omitted macOS's required `-XstartOnFirstThread` and
stopped before capture; the corrected invocation above completed successfully.
Built and installed again with `python3 examples/three-islands/build.py
--skip-engine --install`, with zero package validation findings. The installed
jar hash matches both the built jar and the enabled/trusted local mod-state entry.
