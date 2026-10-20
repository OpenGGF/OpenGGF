# Super Sonic Bros. animation facing audit — 2026-10-20

Base: `4ffa7d4f50461d29c444388f8cba28bd8218b9ad`, current checkout
`feature/ai-super-sonic-bros`. Changes remain uncommitted in this checkout.

## Cause and evidence

`SceneCanvas.draw(SceneSprite, ...)` mirrors pixels and bounds around the sprite
origin. `RecordingCanvas` implements that contract. A raw mapping is not a
semantic facing: the source can face either direction, contain a deliberate turn,
or be a piece of an assembly. `FighterArt.facesRight` previously assumed a single
orientation per fighter, including the incorrect right-facing default for Mecha.

Decoded all mapping frames from $78 onward for the three heroes and Super Sonic,
and all frames for Mecha Sonic, Rhinobot, Egg Robo and Robotnik using the production
`SceneRomArtFactory` and the mod's `Art`. ROM SHA-1:
`cfbf98c36c776677290a872547ac47c53d2761d6`. Images were inspected as temporary
contact sheets, not committed ROM assets.

| Consumer | Finding and correction |
| --- | --- |
| Sonic ledge/climb | $80 is a bar-hang pose; use the left-facing wall hang $B4 with an explicit mirror, including Super Sonic. |
| Knuckles ledge/climb | Wall hang $B5 faces left; mirror relative to logical facing. |
| First ledge catch | Publish inward facing on entry, not one tick later in `ledgeActions`. |
| Mecha Sonic | Raw directional art faces left. Correct the base orientation across movement, attacks, grabs and recovery. |
| Rhinobot | Raw 0/1 face left but 3 faces right. Account for 3 in locomotion, victory, horn attacks and recovery. |
| Rhinobot rear attacks | Tail Swipe, Mule Kick and Dust Kick already place effects behind the fighter; keep the tail at the rear by removing their extra body mirror. |
| Skids | `groundActions` accepts new logical facing before entering SKID. Mirror the old-travel poses for Sonic, Tails, Knuckles, Mecha and Rhinobot. |
| Static lying | Sonic $CC and Tails $B6 face left; mirror these specific uses. |
| Egg Robo spins | Use final pixel mirror for child offsets, including pose overrides and spin phase. |
| Side fire/exhaust | Scaling a downward flame cannot turn it sideways. Rotate the generated flame pixels into a rightward asset with its base as pivot, then use x-flip. Covers side fire, missile and rocket-punch exhaust. |

ROM references: `Animate_Sonic` / `loc_12634` separates status direction from
render flags; `AniKnuckles05` contains the $DC/$DD/$DE gesture; `Map_Rhinobot`
and `Obj_Rhinobot` own its turn/brake frames. Egg Robo `sub_91930` explicitly
negates child X offsets from the parent's `render_flags` and copies that flag to
the child. Core gameplay has additional slope/tumble owners in
`PlayableSpriteAnimation`; changing the shared renderer's mirror convention
would break these consumers and is not justified by the mod's errors.

Rejected punch inversion: the initial crowded mapping sheet was misread as a
left-pointing Knuckles $DE. An isolated both-facing pose sheet showed the proposed
flip made forward punches point backward. Removed that change; retained checks
for existing forward/back punch orientation. Visual evidence must validate the
expected test direction, not just the renderer's boolean.

Rejected blanket frame normalization: Sonic $CC is also part of a complete
rotation, Tails' swipe exposes its tail opposite its face, and Mecha's victory
cycle intentionally turns. Preserve those authored sequences. Correct the
specific use or source bank convention instead. Existing missile/gun/laser
mirrors agree with their raw source direction.

## Validation

The change-based category plan selects 3,510 smoke classes because example
sources are unclassified. Focused ROM-backed mod validation replaces that broad
selection: runtime changes are confined to this mod, with no shared engine/API,
physics, build or selection-policy changes.

Command (Java 25, through shared Maven admission):

```sh
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestSuperSonicBrosScene#romPoseFacingAndMultipartOrigins+moveListDemonstratesEveryFighter' \
  "-Ds3k.rom.path=$PWD/Sonic 3 & Knuckles (W) [!].gen" test
```

The new check compiles the external mod and its checks, verifies real ROM sprites
and both draw orientations, and checks Egg Robo child positions across spin and
explicit pose flips. It also checks horizontal flame direction and first catch
facing for every fighter at both edges. The existing move-list check exercises
seven fighters across six representative moves each through the production scene
host. These are headless draw-recording checks, not desktop automation or a full
engine-suite claim.

Final result at base SHA plus the uncommitted files listed above: **2 tests passed,
0 failures/errors/skips**, completed 2026-10-20 16:19:10 Europe/London. Both methods
ran after removing the rejected punch change. The completed Maven invocation
reported 46.528 seconds; this is command duration, not total investigation time.
Rebuilt with `python3 examples/super-sonic-bros/build.py --skip-engine` against the
engine compiled by that invocation: package validation reported 0 findings.
Final isolated pose sheet visually confirmed left/right hangs, retained forward
and back punches, Mecha's claw, both Rhinobot locomotion frames, and Egg Robo's
spin assembly. `git diff --check` passed. No broad smoke or trace suite was run.
