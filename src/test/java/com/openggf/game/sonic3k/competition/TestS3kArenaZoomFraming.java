package com.openggf.game.sonic3k.competition;

import com.openggf.control.InputActionMasks;
import com.openggf.control.PlayerInputState;
import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hosted zoom over five recorded Desert Palace laps: wherever the ROM camera keeps the
 * miniature inside P1's window, the full-size body is admitted and lands on screen, and its
 * frame carries the same slope-angle group the ROM animator derives from the physics angle.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kArenaZoomFraming {
    static final String ROUTE = "/routes/s3k/dpz-arena-5lap-s19.tape";
    static final int DESERT_PALACE = 3;

    static List<Integer> route() throws IOException {
        var masks = new ArrayList<Integer>();
        try (InputStream in = TestS3kArenaZoomFraming.class.getResourceAsStream(ROUTE)) {
            assertNotNull(in, ROUTE);
            for (String line : new String(in.readAllBytes(), StandardCharsets.US_ASCII).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] run = line.trim().split(" ");
                for (int i = 0; i < Integer.parseInt(run[1]); i++) masks.add(Integer.parseInt(run[0]));
            }
        }
        return masks;
    }

    static PlayerInputState input(int mask, int previous) {
        int pressed = mask & ~previous;
        return PlayerInputState.of(mask, pressed, (mask & 16) != 0 ? InputActionMasks.ACTION_A : 0,
                (pressed & 16) != 0 ? InputActionMasks.ACTION_A : 0, false, false);
    }

    /** Animate_SonicKnuckles_2P loc_12DDA..loc_12E0A (sonic3k.asm:25383-25414): the walk angle group. */
    static int romAngleGroup(S3kCompetitionPlayer player) {
        int angle = player.angle();
        if ((byte) angle > 0) angle = (angle - 1) & 255;
        if (!player.facingLeft()) angle = ~angle & 255;
        return (((angle + 0x10) & 255) >>> 5) & 3;
    }

    @Test void fullSizeBodyStaysInsideTheRomWindowWithAngleCorrectFrames() throws Exception {
        var route = route();
        try (var fixture = new HostedArenaFixture()) {
            var arena = new S3kHostedArenaSession(fixture.host, new HostedArenaRequest(DESERT_PALACE, "sonic", false), () -> {});
            var host = fixture.host;
            var poses = host.fullSizeArt().poses();
            var used = new TreeSet<Integer>();
            int previous = 0, inWindow = 0, angled = 0, agreeing = 0, sloped = 0;
            for (int frame = 0; frame < route.size(); frame++) {
                int mask = route.get(frame);
                var in = input(mask, previous);
                previous = mask;
                fixture.iteration(() -> arena.step(in));
                var player = host.session().player(0);
                var scroll = host.scroll();
                var camera = scroll.view(0).camera();
                var zoomed = host.spritePages().projectZoomed(host.world().context().getObjectManager(),
                        host.players(), scroll, S3kSoloArenaViews.WINDOW_ROWS);
                int row = ((player.centreY() - camera.y() + 12) & scroll.profile().yWrapMask()) - 12;
                var body = zoomed.actors().stream().filter(actor -> !actor.tail()).findFirst();
                if (row >= 0 && row < S3kSoloArenaViews.WINDOW_ROWS && player.bodyVisible()) {
                    inWindow++;
                    assertTrue(body.isPresent(), "ROM window holds the body but the zoom lost it at frame " + frame);
                    int[] drop = S3kSoloArenaViews.groundOffset(player);
                    int x = 2 * (body.get().windowX() - S3kSoloArenaViews.windowLeft(320)) + drop[0];
                    int y = 2 * body.get().windowY() + drop[1];
                    assertTrue(x >= 0 && x < 320 && y >= 0 && y < 224,
                            "body centre off screen at frame " + frame + ": " + x + "," + y);
                }
                int mini = player.mappingFrame();
                used.add(mini);
                boolean walking = mini >= 1 && mini <= 12, running = mini >= 13 && mini <= 20;
                if (player.animation() == 0 && player.flipAngle() == 0 && (walking || running)) {
                    int full = poses.frame(S3kCompetitionPlayer.Character.SONIC, player.animation(), false, mini);
                    int miniGroup = walking ? (mini - 1) / 3 : (mini - 13) / 2;
                    int fullGroup = walking ? (full - 1) / 8 : (full - 0x21) / 4;
                    assertEquals(miniGroup, fullGroup, "full-size frame changed the slope group at frame " + frame);
                    angled++;
                    if (romAngleGroup(player) == fullGroup) agreeing++;
                    if (fullGroup != 0) sloped++;
                }
            }
            assertTrue(arena.trackFinished(), "the recorded route must still finish its five laps");
            assertTrue(inWindow > route.size() / 2, "too few framed frames to prove anything: " + inWindow);
            assertTrue(sloped > 50, "route must exercise slope frames: " + sloped);
            // The animator samples the angle once per frame; the physics angle can move after it.
            assertTrue(agreeing >= angled * 97 / 100, "angle group agreement " + agreeing + "/" + angled);
            var unmapped = new TreeSet<Integer>();
            for (int frame : poses.unmappedFrames(S3kCompetitionPlayer.Character.SONIC)) unmapped.add(frame);
            used.retainAll(unmapped);
            assertEquals(new TreeSet<Integer>(), used, "route uses miniature frames with no ROM-paired pose");
        }
    }
}
