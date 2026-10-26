package com.openggf.game.sonic3k.competition;

import com.openggf.audio.NullAudioBackend;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.LevelArtInternalAccess;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

@Isolated
class TestS3kCompetitionArenaPixels {
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void actualNativeArenasRenderBothHumansWithoutAdvancingFromDraw() throws Exception {
        runArenas(false);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void actualAlzRetainsPublishedPixelsThroughDestructiveHumanRestore() throws Exception {
        runArenas(true);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void actualAlzCountdownStartsBothClocksAndHumanInputSeparatesPublishedViews() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            capture.step(null);
            var engine = EngineServices.current();
            var graphics = engine.graphics();
            assertEquals(NullAudioBackend.class, engine.audio().getBackend().getClass());
            graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
            graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
            var input = new InputHandler();
            Path pictures = Path.of("target/capture/s3k-competition/arenas");
            Files.createDirectories(pictures);
            try (var views = new S3kCompetitionViews(graphics)) {
                var host = new S3kCompetitionNativeHost(engine, new Sonic3kGameModule(), input,
                        capture.loop()::setGameplayMode, new S3kCompetitionRecords(new byte[80]),
                        ignored -> { }, views, LevelArtInternalAccess::initializeObjectArt);
                try {
                    host.openArena(new S3kCompetitionScreen.Launch(new S3kCompetitionSession.Config(
                            S3kCompetitionSession.Mode.MATCH_RACE, S3kCompetitionSession.Arena.AZURE_LAKE, true, 2),
                            List.of(S3kCompetitionPlayer.Character.SONIC, S3kCompetitionPlayer.Character.TAILS)));
                    var session = host.session();
                    var context = host.world().context();
                    assertFalse(session.started(), "a fresh race must wait for the actual neon animation");
                    int countdownFrames = 0;
                    // Test-only deadlock protection, not a replacement countdown or a runtime timing constant.
                    while (!session.started() && countdownFrames < 1_200) {
                        assertEquals(0, session.nativeLapTimerFrames(0), "P1 clock waits for the neon GO frame");
                        assertEquals(0, session.nativeLapTimerFrames(1), "P2 clock waits for the neon GO frame");
                        step(host, input, LogicalInputSnapshot.neutral());
                        countdownFrames++;
                    }
                    assertTrue(session.started(), "native goal/neon never started the race: " + session.capture());
                    assertTrue(countdownFrames > 0);
                    assertEquals(1, session.nativeTimerFlag(0));
                    assertEquals(1, session.nativeTimerFlag(1));
                    assertFalse(session.controlsLocked());
                    assertEquals(host.players().get(0).centreX(), host.players().get(1).centreX(),
                            "neutral countdown preserves the common starting X");
                    assertEquals(host.scroll().view(0).camera().x(), host.scroll().view(1).camera().x(),
                            "neutral countdown leaves the two horizontal cameras together");
                    glViewport(0, 0, 320, 224);
                    views.drawArena(context, host, host.scroll());
                    var startingPixels = ScreenshotCapture.captureFramebuffer(320, 224);
                    ScreenshotCapture.savePNG(startingPixels, pictures.resolve("azure_lake_live_start.png"));
                    var startingRegisters = views.capture().registers();
                    int firstX = host.players().get(0).centreX();
                    int stationaryXFixed = host.players().get(1).capture().xFixed();
                    int stationaryTimer = session.nativeLapTimerFrames(1);
                    var right = PlayerInputState.of(8, 0, 0, 0, false, false);
                    var p1RightOnly = LogicalInputSnapshot.ofPlayers(right, PlayerInputState.neutral());
                    boolean viewsSeparated = false;
                    int movingFrames = 0;
                    // Require visible travel through the native terrain, rather than one subpixel change.
                    while (!viewsSeparated && movingFrames < 600) {
                        step(host, input, p1RightOnly);
                        movingFrames++;
                        var published = views.capture().registers();
                        int travel = (host.players().get(0).centreX() - firstX) & host.scroll().profile().xWrapMask();
                        viewsSeparated = travel >= 64
                                && host.scroll().view(0).camera().x() != host.scroll().view(1).camera().x()
                                && (short) (published.horizontalScroll().get(0) >>> 16)
                                != (short) (published.horizontalScroll().get(108) >>> 16)
                                && !startingRegisters.horizontalScroll().equals(published.horizontalScroll());
                    }
                    assertTrue(viewsSeparated, "P1 RIGHT never separated native positions/cameras/publication: "
                            + host.players().stream().map(S3kCompetitionPlayer::capture).toList()
                            + ", scroll=" + host.scroll().capture() + ", views=" + views.capture());
                    assertTrue(host.players().get(0).xVelocity() > 0, "P1 consumes its real RIGHT input");
                    assertEquals(stationaryXFixed, host.players().get(1).capture().xFixed(),
                            "P1 RIGHT must not become P2 movement");
                    assertEquals(0, host.players().get(1).xVelocity(), "neutral P2 retains its own controller");
                    assertEquals(stationaryTimer + movingFrames, session.nativeLapTimerFrames(1),
                            "stationary P2's allocated HUD clock still advances once per world frame");
                    assertEquals(1, session.nativeTimerFlag(1));
                    assertEquals(session.nativeLapTimerFrames(1), host.levelState(1).getTimerFrames());
                    var pair = host.spritePages().uploaded();
                    assertTrue(pair.player1().pieces().stream().anyMatch(p -> p.nativeSlot() == 0), "P1 published body");
                    assertTrue(pair.player2().pieces().stream().anyMatch(p -> p.nativeSlot() == 1), "P2 published body");
                    var beforePlayers = host.players().stream().map(S3kCompetitionPlayer::capture).toList();
                    var beforeSession = session.capture();
                    var beforePages = host.spritePages().capture();
                    var beforeRegisters = views.capture();
                    var beforeScroll = host.scroll().capture();
                    int beforeFrame = context.getLevelManager().getFrameCounter();
                    views.drawArena(context, host, host.scroll(), host.planes(), withoutHumans(pair));
                    var terrain = ScreenshotCapture.captureFramebuffer(320, 224);
                    var withoutOwnBodies = new S3kCompetitionSpritePages.Pair(
                            new S3kCompetitionSpritePages.Frame(0, pair.player1().pieces().stream()
                                    .filter(piece -> piece.nativeSlot() != 0).toList()),
                            new S3kCompetitionSpritePages.Frame(1, pair.player2().pieces().stream()
                                    .filter(piece -> piece.nativeSlot() != 1).toList()));
                    views.drawArena(context, host, host.scroll(), host.planes(), withoutOwnBodies);
                    var otherHumans = ScreenshotCapture.captureFramebuffer(320, 224);
                    views.drawArena(context, host, host.scroll(), host.planes(), pair);
                    var movedPixels = ScreenshotCapture.captureFramebuffer(320, 224);
                    int[] humanPixels = new int[2], ownBodyPixels = new int[2];
                    int changedFromStart = 0;
                    for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
                        int slot = y < 108 ? 0 : 1;
                        if (terrain.argb(x, y) != movedPixels.argb(x, y)) humanPixels[slot]++;
                        if (otherHumans.argb(x, y) != movedPixels.argb(x, y)) ownBodyPixels[slot]++;
                        if (startingPixels.argb(x, y) != movedPixels.argb(x, y)) changedFromStart++;
                    }
                    assertTrue(humanPixels[0] > 0 && humanPixels[1] > 0, "both live views contain human pixels");
                    assertTrue(ownBodyPixels[0] > 0, "moving P1 body contributes pixels in its own view");
                    assertTrue(ownBodyPixels[1] > 0, "stationary P2 body contributes pixels in its own view");
                    assertTrue(changedFromStart > 0, "actual input/camera flow changes the rendered frame");
                    ScreenshotCapture.savePNG(movedPixels, pictures.resolve("azure_lake_live_separated.png"));
                    assertEquals(beforePlayers, host.players().stream().map(S3kCompetitionPlayer::capture).toList());
                    assertEquals(beforeSession, session.capture(), "drawing cannot advance either native clock");
                    assertEquals(beforePages, host.spritePages().capture(), "drawing cannot build or publish a SAT page");
                    assertEquals(beforeRegisters, views.capture(), "drawing retains the last VInt register publication");
                    assertEquals(beforeScroll, host.scroll().capture(), "drawing cannot track either native camera");
                    assertEquals(beforeFrame, context.getLevelManager().getFrameCounter());
                    assertEquals(GL_NO_ERROR, glGetError());
                } finally { host.closeArena(); }
            } finally {
                graphics.cleanup(); RenderContext.reset();
            }
        }
    }

    private void runArenas(boolean destructiveRestore) throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            capture.step(null);
            var engine = EngineServices.current();
            var graphics = engine.graphics();
            assertEquals(NullAudioBackend.class, engine.audio().getBackend().getClass());
            graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
            graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
            var input = new InputHandler();
            Files.createDirectories(Path.of("target/capture/s3k-competition/arenas"));
            try (var views = new S3kCompetitionViews(graphics)) {
                var host = new S3kCompetitionNativeHost(engine, new Sonic3kGameModule(), input,
                        capture.loop()::setGameplayMode, new S3kCompetitionRecords(new byte[80]),
                        ignored -> { }, views, LevelArtInternalAccess::initializeObjectArt);
                try {
                    var arenas = destructiveRestore ? List.of(S3kCompetitionSession.Arena.AZURE_LAKE)
                            : java.util.Arrays.asList(S3kCompetitionSession.Arena.values());
                    for (var arena : arenas) {
                        host.openArena(new S3kCompetitionScreen.Launch(new S3kCompetitionSession.Config(
                                S3kCompetitionSession.Mode.MATCH_RACE, arena, true, 2),
                                List.of(S3kCompetitionPlayer.Character.SONIC, S3kCompetitionPlayer.Character.TAILS)));
                        for (int frame = 0; frame < 90; frame++) step(host, input, LogicalInputSnapshot.neutral());
                        var context = host.world().context();
                        var pair = host.spritePages().uploaded();
                        assertTrue(pair.player1().pieces().stream().anyMatch(p -> p.nativeSlot() == 0));
                        assertTrue(pair.player2().pieces().stream().anyMatch(p -> p.nativeSlot() == 1));
                        var beforePlayers = host.players().stream().map(S3kCompetitionPlayer::capture).toList();
                        var beforePages = host.spritePages().capture();
                        var beforeRegisters = views.capture();
                        int levelFrame = context.getLevelManager().getFrameCounter();
                        glViewport(0, 0, 320, 224);
                        views.drawArena(context, host, host.scroll(), host.planes(), withoutHumans(pair));
                        var terrain = ScreenshotCapture.captureFramebuffer(320, 224);
                        views.drawArena(context, host, host.scroll(), host.planes(), pair);
                        var picture = ScreenshotCapture.captureFramebuffer(320, 224);
                        int[] changed = new int[2];
                        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++)
                            if (terrain.argb(x, y) != picture.argb(x, y)) changed[y < 108 ? 0 : 1]++;
                        assertTrue(changed[0] > 0, arena + " actual P1 miniature pixels");
                        assertTrue(changed[1] > 0, arena + " actual P2 miniature pixels");
                        ScreenshotCapture.savePNG(picture, Path.of("target/capture/s3k-competition/arenas/"
                                + arena.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
                        assertEquals(beforePlayers, host.players().stream().map(S3kCompetitionPlayer::capture).toList());
                        assertEquals(beforePages, host.spritePages().capture());
                        assertEquals(beforeRegisters, views.capture());
                        assertEquals(levelFrame, context.getLevelManager().getFrameCounter(), "drawing owns no world tick");
                        assertEquals(GL_NO_ERROR, glGetError(), arena.toString());

                        if (destructiveRestore) {
                            host.session().signalGo();
                            var snapshot = context.getRewindRegistry().capture();
                            var right = PlayerInputState.of(8, 0, 0, 0, false, false);
                            var logical = LogicalInputSnapshot.ofPlayers(right, right);
                            for (int frame = 0; frame < 4; frame++) step(host, input, logical);
                            views.drawArena(context, host, host.scroll());
                            var forwardPixels = ScreenshotCapture.captureFramebuffer(320, 224);
                            var forwardPlayers = host.players().stream().map(S3kCompetitionPlayer::capture).toList();
                            var forwardViews = views.capture();
                            var retired = host.player(0);
                            context.getSpriteManager().removeSprite(host.player(0).getCode());
                            context.getSpriteManager().removeSprite(host.player(1).getCode());
                            context.getRewindRegistry().restore(snapshot);
                            assertNotSame(retired, host.player(0), "generic SpriteManager actor recreation");
                            for (int frame = 0; frame < 4; frame++) step(host, input, logical);
                            views.drawArena(context, host, host.scroll());
                            var replayPixels = ScreenshotCapture.captureFramebuffer(320, 224);
                            assertEquals(forwardPlayers, host.players().stream().map(S3kCompetitionPlayer::capture).toList());
                            assertEquals(List.of(), RewindSnapshotDiff.diffKey("native-views", forwardViews, views.capture()));
                            for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++)
                                assertEquals(forwardPixels.argb(x, y), replayPixels.argb(x, y), "ALZ replay " + x + "," + y);
                        }
                    }
                } finally { host.closeArena(); }
            } finally {
                // Retire context-owned and static sprite resources while this EGL context is current.
                graphics.cleanup(); RenderContext.reset();
            }
        }
    }

    private static void step(S3kCompetitionNativeHost host, InputHandler input, LogicalInputSnapshot logical) {
        input.setLogicalOverride(logical);
        var frame = host.world().context().plcFrameLifecycle().latchBeforeFadeUpdate();
        try { NativeCompetitionIterationAccess.runAdmitted(frame, () -> host.stepArena(logical, host.session())); }
        finally { frame.finish(); }
    }

    private static S3kCompetitionSpritePages.Pair withoutHumans(S3kCompetitionSpritePages.Pair pair) {
        return new S3kCompetitionSpritePages.Pair(withoutHumans(pair.player1()), withoutHumans(pair.player2()));
    }
    private static S3kCompetitionSpritePages.Frame withoutHumans(S3kCompetitionSpritePages.Frame frame) {
        return new S3kCompetitionSpritePages.Frame(frame.slot(),
                frame.pieces().stream().filter(p -> p.nativeSlot() != 0 && p.nativeSlot() != 1).toList());
    }
}
