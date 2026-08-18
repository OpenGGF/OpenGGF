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
 * <p><b>Status at the commit that added it (landed red, deliberately).</b>
 * 15202 errors, 0 bootstrap errors, over 7088 rows. The first 393 rows match
 * exactly on every physics field, so the segment's seeded entry state is
 * sound; the first physics divergence is frame 394, where Sonic is running
 * right down the flattening CPZ act 2 slope at {@code x=0x142D}: the ROM
 * reports {@code y=0x05DC} and {@code angle=0x0A}, the engine {@code y=0x05DB}
 * and {@code angle=0x0C}. Both come out of the same decision --
 * {@code AnglePos} probes the floor under Sonic's right edge then his left
 * edge and {@code Sonic_Angle} keeps whichever distance is smaller, taking the
 * left (Secondary) angle on a tie (docs/s2disasm/s2.asm:43048-43077,
 * 43120-43146; the probe itself is {@code FindFloor}, s2.asm:43413-43470) --
 * so the engine is resolving one of the two sensors a pixel higher and
 * inheriting that sensor's angle. Note the ROM's angle run here is
 * {@code 0E, 0E, 0E, 0A, 0A, 08 ...} and never passes through {@code 0C}.
 * Twelve frames later the ROM leaves the ground ({@code air} 0 -> 1, frames
 * 406-416) and the engine stays attached, and the run diverges from there.
 * That cascade is what kills the chain: the chain's walk reaches BK2 cursor
 * 83819, this segment's row 1477, before the player dies and the engine
 * reloads the act. Reported, not fixed -- the floor-probe divergence is not
 * root-caused and no engine change belongs in a coverage lane.
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
