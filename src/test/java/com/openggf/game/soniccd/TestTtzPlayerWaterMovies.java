package com.openggf.game.soniccd;

import com.openggf.audio.AudioRequestObserver;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native movies recorded for Tidal Tempest's player water beyond the stock rehost fixtures
 * ({@code <soniccd.native.root>/rehost-zph/<name>/}, written by that directory's
 * {@code _tools/record_zph.py}). Trace data is comparison-only: the BK2 drives the engine.
 * <ul>
 *   <li>{@code drown-R41C-left}: Sonic enters the water at row 346 and then pushes left against
 *   a wall far from any bubble vent until he drowns. It covers the water entry, the air
 *   countdown in the bubbles slot ({@code PlayerBubblesObject_0_RoutineA},
 *   src/objects/r4/player_bubbles.asm:181-333: the first air second on the entry frame, then
 *   one every $3C passes, thirty of them), the drowning ({@code move.b #$17,obj.anim_id},
 *   object control $81, speeds cleared) and the 120-pass sink to the restart.</li>
 *   <li>{@code current-R41C}: a roll at x $240 lowers Sonic's centre into the stage's first
 *   current record ({@code [$220,$240) x [$2F0,$440)}, speed (0, $400); src/r4/
 *   water_currents_1c_1d.asm) for one frame with Down held ({@code CheckWaterCurrents},
 *   src/r4/main.asm:776-858: speeds, pose $0F, air, the Down nudge); the next frame's landing
 *   leaves the box and the routine restores the walk pose and clears the flag (:860-871).</li>
 * </ul>
 */
class TestTtzPlayerWaterMovies {
    @TempDir
    Path tempDir;

    @Test
    void drowningFollowsTheBubblesSlotCountdown() throws Exception {
        var outcome = replay("drown-R41C-left", CdStagePayload.R41C, Integer.MAX_VALUE, 2100, 2340);
        assertTrue(outcome.firstErrorRow() < 0,
                () -> "drown-R41C-left diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
        assertTrue(outcome.rowsCompared() >= 2300, "the movie runs to the restart");
        int drowned = -1;
        for (int row = 2100; row <= 2300 && drowned < 0; row++) {
            if ((outcome.trace().getFrame(row).animationId() & 0xFF) == 0x17) drowned = row;
        }
        assertTrue(drowned > 0, "the native trace drowns");
        var at = outcome.row(drowned);
        assertEquals(0x17, at.anim(), "drowning pose");
        assertEquals(0, at.xSpeed());
        // The sink: the slot moves the player with the old speed, then adds $10 (player_bubbles.asm:241-256).
        var next = outcome.row(drowned + 1);
        assertEquals(0x10, next.ySpeed(), "first sink step");
    }

    /**
     * The drowning's sound requests by row and ID, against a native probe of this movie (write hooks on the FM queues
     * {@code fm_sound_1..3} at $FFF00A-C and on the sub-CPU command word GACOMCMD0, rows from the trace offset): FM $A2
     * at the water entry (row 346), the countdown chime FM $DF on the air seconds at drown_timer 12, 11, 2, 1 and 0
     * whose {@code stage_frames} bit 5 is clear (player_bubbles.asm:196-207), then FM $B6 for the drowning on the same
     * row (player_bubbles.asm:220-221), and no music or sub-CPU command from the entry to the drowning: the native
     * {@code PlayerResetDrown} calls {@code PlayFmMusic} only below $D seconds (scddisasm/src/objects/r4/player_bubbles.asm:153-172), and in the
     * shipped program that call lands on an {@code rts} ({@code jsr $2025AC}, $4E75, before {@code PlayFmSound} at
     * $2025AE; src/common/sound.asm:3-4).
     */
    @Test
    void drowningRequestsTheNativeSounds() throws Exception {
        List<String> requests = new ArrayList<>();
        int[] current = {0};
        boolean[] observing = {false};
        Path movie = CdTestMedia.nativeFile("rehost-zph/drown-R41C-left/movie.bk2");
        var fixture = new CdFixtureReplay.Fixture(CdStagePayload.R41C, movie.getParent().resolve("trace"), movie);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, Files.createDirectories(tempDir.resolve("audio")), fixture, 2200, false, -1, -1,
                    new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            current[0] = row + 1;
                            if (observing[0]) return;
                            observing[0] = true;
                            sonic.currentAudioManager().setRequestObserver((kind, id) -> {
                                if (current[0] < 300 || id == 0xAB) return;   // FM_CHARGESTOP: the player's, not water's
                                requests.add(current[0] + ":" + (kind == AudioRequestObserver.RequestClass.MUSIC
                                        || kind == AudioRequestObserver.RequestClass.COMMAND ? "cmd" : "fm")
                                        + Integer.toHexString(id).toUpperCase());
                            });
                        }
                    });
        }
        assertEquals(List.of("346:fmA2", "429:fm95", "1433:fmDF", "1493:fmDF", "2033:fmDF", "2093:fmDF", "2153:fmDF",
                "2153:fmB6"), requests, "sound requests from row 300 to 2200 (row:kind+ID)");
    }

    @Test
    void currentPushesForOneFrameAndReleases() throws Exception {
        var outcome = replay("current-R41C", CdStagePayload.R41C, Integer.MAX_VALUE, 436, 446);
        assertTrue(outcome.firstErrorRow() < 0,
                () -> "current-R41C diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
        var pushed = outcome.row(441);
        assertEquals(0x0F, pushed.anim(), "the current's float pose");
        assertEquals(0x400, pushed.ySpeed());
        assertEquals(0, pushed.xSpeed());
        assertEquals(0x2F2, pushed.y(), "the Down nudge after the roll's $2F1");
        assertEquals(0, outcome.row(442).anim(), "out of the box: walk pose");
    }

    private CdFixtureReplay.Outcome replay(String name, CdStagePayload payload, int lastRow, int keepFrom, int keepTo)
            throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-zph/" + name + "/movie.bk2");
        var fixture = new CdFixtureReplay.Fixture(payload, movie.getParent().resolve("trace"), movie);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            Path config = Files.createDirectories(tempDir.resolve(name));
            return CdFixtureReplay.replay(disc, config, fixture, lastRow, false, keepFrom, keepTo);
        }
    }
}
