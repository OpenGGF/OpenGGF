# Three Islands: combat depth, equipment and optional content

Task: a broad polish and content pass over the Three Islands creator mod, with freedom to
change existing design. Branch `feature/ai-three-islands` in the main checkout, base
`78ad7aea74`. No engine, Mod API, stock gameplay, physics or audio-driver code changed.

## What changed and why

Review started from native `ExampleModCapture` frames and the creator balance simulation.
Three problems stood out: battles reused a walkable-ground search that stacked heroes on
top of each other and of bosses, with field labels (for example "Bridge winch") showing
through; bosses differed only in stats and a shared charge-up; and late-game fights against
a full trio ended in 2.0 rounds on average.

- **Statuses.** Fire, Water and Lightning hits can burn (damage at the owner's turn start),
  soak (two-thirds speed) or stun (lose the next turn). Bosses cannot be stunned; guarding
  halves the chance; healing cures; an existing status is not re-rolled.
- **Boss signatures** (`EnemyKind.signature`): summons (Bomb King, Coconuts Chief, the
  Convergence Engine's Egg Robos), bomb fuses, half-health power-ups with dialogue (Eggman,
  Knuckles, golden double-acting Mecha Sonic), the Grabber Queen's grip, Silver Sonic's
  rebounding spikes, Flame Craft napalm, Screw Mobile depth charges, a tech-only barrier and a
  cycling core weakness. Signature turns fall at `turnsTaken % 4 == 2`; charge-ups moved
  from every third to every fourth turn so a cycle mixes normal, signature and charge turns.
  Helpers leave when their summoner falls and pay a third of normal rewards.
- **Accessories** (`Gear`): one per hero, stored on `Hero` so every stat reader sees it.
  Saves add `gear=` and `equip.<HERO>=` keys; decoding applies accessories before hero HP so
  bonus HP survives a reload, and saves without these keys load unchanged.
- **Content.** Each authored layout gains a final branch ending in a secret room: a lost
  animal outdoors (Green Hill uses a fixed southern grove spot) and a treasure monitor indoors.
  Puzzle controls now use every alcove except that room, so existing control placement is
  unchanged. Rescues pay rings, with gifts at 3/6/10. Four optional Rift Echoes pair earlier
  bosses after each island's final anchor (Death Egg: after Mecha Sonic) at area level + 3.
- **Puzzles.** A ROTATE rule (quarter-turn dials from a written clue) is used for Emerald
  Hill's interior and the Death Egg's outdoor relays; Chemical Plant's outdoor pumps became an
  ordered sequence and Hydrocity's intakes a circuit.
- **Presentation.** Battles draw the unchanged field camera without landmarks and dimmed,
  with fixed left/right stage positions, depth-sorted fighters, shadows, fuse counters, stance
  rings, status tags and planted charges. Outdoor layouts render as terraces (ROM cliff faces,
  edge shadows). Emerald Hill's floor uses the kit's greenest opaque tile instead of the
  checkerboard cliff, which had made floor and walls indistinguishable.

## Rejected or revised during the task

- Bomb King's first summon tuning (two full-HP helpers, 12-power explosions) lost the
  story-only simulation at Star Light seed 1: a lone level-3 Sonic spent every turn on
  helpers. Helpers now have 40% HP, at most one per hero, explosions use power 10, and the
  summoners' HP fell (Bomb King 700 to 560, Coconuts Chief 700 to 600).
- Flame Craft napalm on every signature turn *plus* a 20% random chance burned a duo down in
  the story-only route. It is now signature-turn only with a 45% burn chance.
- The simulated player's item rule used `battle.events().isEmpty()`, effectively random. It
  now heals itself or a badly hurt ally, guards against a planted charge and hits a fuse at 1.
  This changes the scripted policy, not the game's difficulty.
- Trio ordinary-foe HP scaling went from 260% to 470% after 380% still gave 2.5 rounds.
- Two-boss echoes against a lone Sonic took 22 rounds; echo foes facing one hero use 55% HP.
- A first summon placement reused the summon's slot number, which would stack two living
  helpers in the same spot in a long fight; helpers now take the first free stage position.

## Verification

Creator suite (fast loop: javac plus the Jupiter console against `target/test-classpath.txt`):
75 tests, zero failures. Balance results on the final code: ordinary fights 60/60 in every
zone at 2.9-4.4 rounds; all twelve bosses 60/60 at 4.6-8.3 rounds; echoes 40/40, 38/40, 20/40
and 20/40 at 7-12 rounds. These measure the scripted policy, not human difficulty.

The change-based plan against `78ad7aea74` selects the full ordinary suite because
`examples/` is unclassified. Proportionate focused validation applies: only the creator mod
and its engine test bridge changed. Final queued command (Java 21, absolute root ROMs):

```sh
python3 tools/testing/maven_queue.py -B -Dmse=off '-Dtest=TestThreeIslandsExample,TestThreeIslandsScene' \
  "-Dsonic1.rom.path=$PWD/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$PWD/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$PWD/Sonic 3 & Knuckles (W) [!].gen" test
```

Result: 14 tests, zero failures or skips; 10 of 10 zones and 10 of 10 interiors exercised,
including the dial puzzles through real interaction input. `build.py --skip-engine` reported
zero validator findings. Native captures were inspected for boss, ordinary and Bomb King
battles (summon, fuse and burn), the shrine treasure room, a lost animal, the shop's accessory
tab, the Equip preview, Emerald Hill and Chemical Plant terraces and a Launch Base echo. Not
covered: the full engine suite, a human playthrough at real speed and real-device audio.
