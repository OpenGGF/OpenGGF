# Bug List

Last updated: 2026-08-09

## Open Bugs

- [ ] When Sonic jumps, his shadow should stay on the floor, not follow him up.
- [ ] Emerald shadow is too far away, it should be directly in front of Sonic's shadow, touching
- [ ] First Checkpoint for Special Stage 1 comes too quickly, so rings are hidden to prevent rainbow pattern on them
- [ ] "SONIC RINGS" text is incorrectly hidden during "GET XX RINGS" text
- [ ] Sonic does not animate during start of Special Stage. He should be animated from the start.
- [ ] It's easier to run up the right side of the half pipe than the left.
- [ ] Not having enough rings for the emerald should have a proper exit routine. Instead sonic rapidly gets ejected from the stage.
- [ ] Support Tails
- [ ] Support Sonic & Tails (GUI changes, Sonic/Tails swapping, delayed movement for Tails, distinct ring count per character, etc.)

## Resolved Bugs

- [x] "COOL" and "NOT ENOUGH RINGS" checkpoint wings stay fixed while only
  the independent hand peer bobs (`862ed8689`; ROM Obj5A peer-object split,
  renderer and rewind coverage).

## ROM-Accurate Investigation Plan

Each fix must be verified against the original `docs/s2disasm/` disassembly (REV01). Do not tune constants or approximate behavior.

## Verification Approach

1. **Lock revision**: Use REV01 ROM and matching disassembly build (`fixBugs=0`)
2. **Create repro scripts**: Emulator savestate + input movie for each bug
3. **Log RAM variables**: Track key state (player status bits, object routines, timers)
4. **Compare frame-by-frame**: ROM vs recreation until first divergence
5. **Translate exact logic**: Match condition ordering, sign handling, and early-outs
