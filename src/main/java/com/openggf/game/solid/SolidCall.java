package com.openggf.game.solid;

/**
 * The input registers of one ROM solid-helper call, made from the object's own
 * routine at its call site.
 *
 * <p>Every helper of the SolidObject family takes the same four registers:
 * {@code d1} half-width, {@code d2} half-height used while the player is in the
 * air, {@code d3} half-height used while standing, and {@code d4} the object's
 * x position before the routine moved it. The standing branch copies d4 to d2
 * and carries the rider by {@code x_pos(a0) - d2}: {@code MvSonicOnPtfm} /
 * {@code loc_19BA2} (s2.asm:35641-35662), {@code MvSonicOnPtfm} /
 * {@code loc_1E1CA} / {@code loc_1E1F4} (sonic3k.asm:41687-41742), S1
 * {@code MvSonicOnPtfm2} / {@code MoveWithPlatform} (_incObj/sub
 * MvSonicOnPtfm.asm:18-42). A routine that moves its body and then calls the
 * helper therefore carries the rider by its own movement and nothing else.
 *
 * <p>Which helper runs (full, top-only, sloped, monitor) is the provider's
 * {@code SolidRoutineProfile}; anchor offsets that place the solid box away
 * from {@code x_pos}/{@code y_pos} remain the provider's {@code SolidObjectParams}.
 */
@com.openggf.game.ModApi
public record SolidCall(int d1HalfWidth, int d2AirHalfHeight, int d3GroundHalfHeight, int d4CarryX) {
}
