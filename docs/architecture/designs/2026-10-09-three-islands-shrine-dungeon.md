# Three Islands story dungeons

Task: replace the landmark cutaways with playable exploration, then review all shrines,
give them distinct interiors, validate, install, commit and push. Work uses the existing
`feature/ai-three-islands` checkout, based on `d461190b769b84fb27741e4c13156699b613c318`.

## Behavior and ownership

Each zone's northwest memory landmark opens an interior. `Dungeon` supplies the location,
ROM level-kit source, music, encounter groups and north/south room arrangement. `Field`
owns both movement and physical gates. `DungeonArt` extracts textured opaque fragments
from the decoded ROM blocks and draws masonry around those same boundaries; the outdoor
stage and its route remain available for returning, but are not the interior graphics.

| Area | Interior | ROM terrain |
|---|---|---|
| Green Hill | Seaside Shrine | S1 Marble |
| Star Light | Observatory Vault | S1 Star Light |
| Spring Yard | Freight Catacombs | S1 Marble |
| Emerald Hill | Workshop Caverns | S2 Mystic Cave 1 |
| Chemical Plant | Pump Station | S2 Chemical Plant |
| Mystic Cave | Lantern Shrine | S2 Mystic Cave 2 |
| Angel Island | Guardian Shrine | S3K Lava Reef 2 |
| Hydrocity | Tidal Sanctuary | S3K Hydrocity 2 |
| Launch Base | Mooring Vault | S3K Lava Reef 1 |
| Death Egg | Sky Archive | S3K Death Egg 2 |

Two sentry groups guard physical gates, with a patrol and supplies in the middle chamber.
The inner-room interaction plays the existing discovery and awards catch-up progression
and supplies once. The first shrine rescues the Flicky; she follows the player to the exit,
where the sisters reunite. The player can leave at any time, rest at the indoor Starpost,
revisit a cleared location or replay its discovery without receiving duplicate rewards.

The optional `dungeon` save flag distinguishes an indoor checkpoint from an outdoor camp.
Old saves default to outdoors. Indoor continues and defeat retries reconstruct the parent
field and interior, restore cleared encounters and start in the entrance room. Outdoor
bosses, emeralds and party recruitment have separate completion rules. Existing discovery
keys stay valid for journals/reward migration, and a recorded landmark remains enterable.

## Review findings and rejected implementation

The initial uncommitted pass put only Green Hill in dungeon mode, retained its outdoor
clearing/collision underneath a screen-fixed tint and columns, reused a chapter boss and
its dialogue, and let an outdoor clear mark the indoor boss defeated. It also gave dialogue
to Tails before he joined and omitted route validation. That presentation and special-case
flow were replaced by the independent interiors above; shared field/battle services remain.

A flood-fill regression found a one-pixel bypass at the closed edge of a vertically mirrored
gate. Gate bounds now cover both endpoints of the mirrored corridor. The scene reload test
also had to wait through the actual loading transition before taking its new field reference.

Marble's unsuitable outdoor route does not imply its decoded art is unusable: interior art
samples block pixels directly, without asking the stock route finder for a camera position.
No engine or shared Mod API production code changes are required.

## Validation

The change-based plan selects all ordinary classes because example paths are unclassified.
Use proportionate verification of Three Islands' model tests, packaging and ROM scene host;
engine gameplay is unchanged. The added scene route covers entry, indoor battles, partial
save/continue, discovery rewards, return and re-entry across all ten available ROM areas.
Model tests cover locked-room reachability, legacy save/entrance migration, independence
from chapter clears, fleeing, and complete encounter sequences including level-one Sonic.

Final focused invocation on the above base plus this change:

```sh
python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=TestThreeIslandsExample,TestThreeIslandsScene' \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PWD/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$PWD/Sonic 3 & Knuckles (W) [!].gen" test
```

Passed: 47 example Jupiter tests, packaging/validation, and 8 production scene-host checks;
zero failures or skips. The scene route reported 10 of 10 interiors exercised. The final
run completed on 2026-10-09 at 10:45 BST. This is focused mod validation, not an engine-suite
pass. No unrelated engine tests were run.

`ExampleModCapture` with `-XstartOnFirstThread` rendered all ten interiors at 400x224
(scale 2) against the supplied ROMs. Inspected every entrance room: distinct materials,
world-anchored walls/gates, legible actors, map and HUD. Replaced the initial small Marble
roof ornament used as flooring with a complete 32-pixel brick repeat; Mystic Cave, Lava
Reef and Hydrocity also use curated larger repeats. Native captures confirmed the result.

`python3 examples/three-islands/build.py --skip-engine --install` builds and validates the
mod against the unchanged engine, then installs, enables and trusts its exact jar. The
installed jar hash and trust entry are checked against that build. Existing saves are kept.
