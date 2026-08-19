package com.openggf.tests.trace.s2;

import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

/**
 * Compared replay of {@code seg10_cpz2} — the CPZ act 2 level segment the run
 * resumes after {@code ss_6} (bk2 offset 82342, 7088 rows), manifest segment 15
 * as {@link com.openggf.tests.trace.runs.TestS2CompleteEmeraldRunChain} counts
 * them.
 *
 * <p>Exists for the same reason as
 * {@link TestS2Cpz1Seg8CompleteEmeraldsSegmentTraceReplay}: the chain's
 * per-segment comparator report is only written when a segment closes, so a
 * divergence bad enough to kill the player is never reported there — it
 * surfaces only as "segment 15 lost production ownership before source
 * closure" with no frame and no field.
 *
 * <p><b>Status (landed red, deliberately).</b> 15202 errors, 0 bootstrap
 * errors, over 7088 rows. The first 393 rows match exactly on every physics
 * field, so the segment's seeded entry state is sound; the first physics
 * divergence is frame 394, where Sonic is running right down the flattening
 * CPZ act 2 slope at {@code x=0x142D}: the ROM reports {@code y=0x05DC} and
 * {@code angle=0x0A}, the engine {@code y=0x05DB} and {@code angle=0x0C}.
 *
 * <p><b>Root cause (measured, not a floor-probe defect).</b> The two probes
 * and the {@code Sonic_Angle} min-distance/tie rules (s2.asm:43048-43077,
 * 43120-43146; {@code FindFloor} at s2.asm:43413-43470) are modelled
 * correctly, and the engine's chunk word, collision-index entry, curve angle
 * and height column at the divergent sensor all match the ROM data exactly.
 * What differs is which of the two per-zone 16x16 collision index arrays the
 * probe reads. {@code AnglePos} selects {@code Secondary_Collision} and passes
 * {@code d5 = top_solid_bit} whenever {@code top_solid_bit != $C}
 * (s2.asm:43002-43011); the engine has the player on the primary path
 * ({@code $C}) for this whole segment. Replaying {@code FindFloor} against the
 * ROM's CPZ layout, 128x128 mappings and both CPZ collision index arrays over
 * every grounded, object-free frame of this segment: on the 57 frames where
 * the two paths predict different results, {@code top_solid_bit = $E} plus
 * {@code Secondary_Collision} reproduces the recorded {@code y} and
 * {@code angle} on 43 and the primary path on 0. Frame 394 is simply the first
 * frame at which the two arrays disagree. Chunk 265 at
 * {@code (0x142,0x5E)} carries primary solidity only ({@code word=0x3509}), so
 * on path 2 the probe falls through it to the full-height chunk below and
 * reports distance 1 with angle {@code 0x0A} -- exactly the recorded row.
 *
 * <p><b>Why the engine is on the wrong path.</b> {@code Obj79_SaveData} copies
 * {@code MainCharacter+top_solid_bit} into {@code Saved_Solid_bits} when the
 * star post is hit and {@code Obj79_LoadData} restores it when the level
 * reloads on the special-stage return (s2.asm:44740, 44787), because
 * {@code Obj01_Init} only writes {@code $C} when {@code Last_star_pole_hit}
 * is zero (s2.asm:36192-36199). The recorded run therefore resumes CPZ act 2
 * already on path 2. An earlier revision of this note claimed the v5 schema
 * records no {@code top_solid_bit}, and that was wrong: the S2 aux recorder
 * has always emitted {@code top_solid_bit}/{@code lrb_solid_bit} on its
 * {@code state_snapshot} event, but it read them from SST offsets +0x46/+0x47,
 * which are S3K's (S3K's player SST is 0x4A bytes). S2's is 0x40 bytes and
 * defines {@code top_solid_bit = $3E} (s2.constants.asm:70-71), so
 * PlayerBase+0x46 landed in the *sidekick* slot and the field carried
 * out-of-slot garbage -- 0x39/0xE2 in the installed fixture, never $0C/$0E.
 * With the offsets corrected, a scratch recapture of this movie reads
 * {@code top_solid_bit = $0E} at seg10 entry and {@code $0C} at seg8/seg9
 * entry, which is exactly the save/restore behaviour described above and
 * confirms the collision-path diagnosis from ROM state rather than from a
 * value fitted to this fixture. The lane stays red until the regenerated
 * fixture is installed and its entry state seeded from it; the
 * chain does play the star post, so the fix that matters there is upstream --
 * the CPZ act 2 plane switcher that should leave the player on path 2 before
 * the star post is hit.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestS2Cpz2Seg10CompleteEmeraldsSegmentTraceReplay extends AbstractTraceReplayTest {

    @Override
    protected SonicGame game() {
        return SonicGame.SONIC_2;
    }

    @Override
    protected int zone() {
        return Sonic2ZoneConstants.ZONE_CPZ;
    }

    @Override
    protected int act() {
        return 1;
    }

    @Override
    protected Path traceDirectory() {
        return Path.of("src", "test", "resources", "traces", "s2", "runs",
                "s2-sonic-tails-complete-emeralds", "seg10_cpz2");
    }
}
