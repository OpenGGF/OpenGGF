# Beta Flight mutator (2026-11-26)

Mutator Lab's Beta Flight replaces native Knuckles' glide in Sonic 3 & Knuckles with
the flight routine left in the Sonic 3 Winter CES cartridge. Branch
`feature/ai-knuckles-beta-flight`, based on develop `3db8a1af73`.

## Source and method

- **Build.** Hidden Palace's "Sonic The Hedgehog 3 (Dec 22, 1993 demo)", cartridge
  label *Sonic 3 CES 12/22/93*, released 2026-11-26. The owner supplied the archive
  (ZIP MD5 `24c4bd2e33b044429ab84e93d7a59bea`, identical to the host's published
  ETag). It holds one 1 MiB image; its header reads `(C)SEGA 1993.NOV`. The image
  is not committed, copied into a worktree or redistributed. No part of its code,
  data or a listing is reproduced here.
- **Static only.** The image was never executed. Code was decoded with Capstone
  (68000 mode) in a scratch directory. Regions were attributed by aligning 12-byte
  windows against the final Sonic 3 and S&K builds that `docs/skdisasm` produces
  (`s3built.bin`, `skbuilt.bin`) and reading labels from `s3.lst` / `sonic3k.lst`.
  About 12.6 KB of the image matches neither build.
- **Addresses** below are the CES image's ROM offsets. No disassembly of this build
  exists in the repository, so they are not citation-checked labels.

## What the build contains (observed)

| Fact | Evidence |
| --- | --- |
| A third player object at $16C36–$18286, a copy of Sonic's object. | Its subroutines align with final S3 `Obj_Sonic`, `Sonic_Init`, `Sonic_MdNormal`, `Sonic_Jump`, `Sonic_Spindash`, `Sonic_Display`, `Sonic_Water`, `Player_SlopeRepel` and `Player_LevelBound`. |
| It is Knuckles. | Its animation table at $1806A matches S&K `AniKnuckles` (General/Sprites/Knuckles/Anim - Knuckles.asm), including script $20 (`$1F,$C0,$FF`). |
| It is player-controlled, not scripted. | It reads `Ctrl_1_held_logical` ($F602) and `Ctrl_1_pressed_logical` ($F603), runs `Player_LevelBound`, `MoveSprite2`, `Player_JumpAngle` and Sonic's air level collision. No timer or script drives it. |
| It is unreachable in this cartridge. | No absolute long or PC-relative `lea`/`jsr`/`jmp`/`bsr`/`bra` reference to its entry exists. Its mappings ($11FD40), DPLC table ($1211B8) and art base ($100000) lie past the end of the 1 MiB image. |
| Its airborne mode branches on `double_jump_flag`. | $17014: zero runs Sonic's ordinary air path; non-zero runs the flight path at $17044. |

## The routine

**Entry** ($1794C–$179D4, reached from the copied `Sonic_JumpHeight` once `y_vel`
is at or past -$400, or -$200 underwater): with `double_jump_flag` clear and A, B
or C newly pressed, it
1. unrolls with default radii and the usual y-position correction,
2. clears the roll-jump bit and sets `double_jump_flag` to 1,
3. takes `ground_vel`. Zero becomes $400 toward the facing. Otherwise its magnitude
   is raised to at least $400 and the facing follows its sign. The result is written
   to both `ground_vel` and `x_vel`,
4. writes anim $20. `y_vel` is not changed. The rest of that frame is the ordinary
   air path (`Sonic_ChgJumpDir`, gravity, the underwater step).

**Each flight frame** ($17044–$1705E), in order:
1. **Vertical** ($17060–$170D6), a copy of final S3 `Tails_Move_FlySwim`
   (s3.asm:24874-24926) that reads player 1's pad and drops the flight timer.
   - State 1: a new A/B/C press with `y_vel >= -$100` starts a flap (state 2). It
     is refused underwater while `Flying_carrying_Sonic_flag` ($F73E) is set.
     `y_vel` += 8 either way.
   - States 2–$1F: below -$100 the flap ends (state 1). Otherwise `y_vel` -= $20
     and the state increments; at $20 it returns to 1.
   - With `y_vel < 0` and `y_pos <= Camera_min_Y_pos + $10`, `y_vel` becomes 0.
   - It writes anim $20.
2. **Steering** ($170D8–$17130): `Sonic_ChgJumpDir`'s left/right logic without the
   roll-jump test or apex drag, using `ground_vel` ($1C) instead of `x_vel`, then
   the camera-bias tail. Only facing and `ground_vel` change.
3. `Player_LevelBound`, `MoveSprite2` (no gravity), `Player_JumpAngle`, then Sonic's
   air level collision. Landing goes through `Player_TouchFloor`, which clears
   `double_jump_flag`. The ordinary air path's underwater -$28 is absent.

The build has no flight time limit, sound, tired or swim pose, wall grab or
glide-specific collision. Hurt uses the object's separate hurt routine.

## Conclusions and conjecture

- **Evidence:** player-controlled physics. Knuckles was a copy of Sonic with Tails'
  flight bolted on, plus a minimum sideways launch.
- **Inference:** the steering writing `ground_vel` while `MoveSprite2` reads `x_vel`
  makes left/right inert except for turning. A landing rewrites `ground_vel` from
  `x_vel`. This looks unfinished, but the cartridge ships it.
- **Conjecture (not evidenced):** whether the team intended flight or a glide; how
  frame $C0 looked in the build's own art; the build date relative to S&K's glide.

## Engine model

- `BetaFlightRuntime` (world-owned, rewind key `beta-flight`) holds the admission
  policy, one captured ownership bit and the routine's arithmetic.
  `PlayableSpriteMovement` keeps the frame ordering through its existing helpers.
- The air-ability gate is shared with the glide. Only `activateGlide()` is replaced,
  and only for an admitted, non-CPU, glide-ability leader.
- While the ownership bit is set, the glide dispatches (`doubleJumpFlag` 1–5) are
  bypassed. That keeps a flight in progress out of the glide state machine, even if
  the mutator is turned off mid-flight. The bit ends when the native landing,
  object-control or death owners clear the flag.
- **Fixed Steering** (option, off by default, not in the cartridge) runs the same
  steering arithmetic on `x_vel`, the field `Sonic_ChgJumpDir` steers, and leaves
  `ground_vel` alone. Left and right then accelerate him by twice the run acceleration
  per frame up to his top speed. Off, the routine runs as found.
- Art is the user's S&K Knuckles: anim $20, frame $C0, the same script the
  cartridge's table names.

## Gaps

- Objects that test Knuckles' `double_jump_flag` for the glide (breakable walls,
  monitors, some badniks and bosses) also react to flight state 1, which shares the
  value. The CES build contains no such Knuckles-aware objects to compare with.
- Not route-certified: water, hurt mid-flight, Super/Hyper Knuckles, KiS2, and S1/S2
  donors. The Lab admits native S3K Knuckles only.
- The native glide's Super transform path is unaffected. The CES entry has no
  Super test, and the engine keeps its shared gate.

## Rejected approaches

- A new state owner separate from `doubleJumpFlag`. Rejected: the cartridge uses
  `double_jump_flag` itself, and landing/object-control owners already clear it.
- Reusing `TailsFlightController`. Rejected: its flight timer, audio and Tails pose
  selection are absent from the CES routine.
