package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.sprites.playable.Tails;
import com.openggf.graphics.ScreenshotCapture;
import org.junit.jupiter.api.Test;
import com.openggf.sprites.ghost.GhostTraceRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceFixtures;
import com.openggf.trace.TraceFrame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Production ROM art and EGL drawing: pictures must never clock ghost animation. */
class TestTraceGhostPresentationCadence {
    @ParameterizedTest
    @ValueSource(strings = {"s1", "s2"})
    void repeatedPicturesDoNotAdvanceGhostAnimation(String game) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom(game), 0, 0, settings);
            var renderer = new GhostTraceRenderer();
            var metadata = TraceFixtures.metadata(game, 0, 0);
            TraceFrame row = row(1, session.player());
            var comparator = comparator(TraceFixtures.trace(metadata, List.of(row)), renderer);
            comparator.afterFrameAdvanced(input(0), false);
            renderer.render(session.player(), List.of());
            var ghost = ghost(renderer, "main:sonic");
            var before = pose(ghost);
            for (int picture = 0; picture < 20; picture++) {
                renderer.render(session.player(), List.of());
            }
            assertEquals(before, pose(ghost), "repeated pictures must preserve script duration and mapping");
            assertFalse(ghost.getAnimationManager().hasDynamicArtDecisionOwner());
            var tiles = session.render(false);
            var firstPicture = picture(session, renderer);
            var repeatedPicture = picture(session, renderer);
            assertFalse(java.util.Arrays.equals(tiles.pixels(), firstPicture.pixels()),
                    "the original ROM ghost must actually draw pixels");
            assertArrayEquals(firstPicture.pixels(), repeatedPicture.pixels(),
                    "repeated full pictures must preserve exact RGBA output");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"s1", "s2"})
    void omittedAndVaryingPicturesPreserveEveryAdmittedAnimationStep(String game) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom(game), 0, 0, settings);
            var trace = TraceFixtures.trace(TraceFixtures.metadata(game, 0, 0),
                    java.util.stream.IntStream.range(0, 24).mapToObj(i -> row(i, session.player())).toList());
            var omitted = new GhostTraceRenderer();
            var pictured = new GhostTraceRenderer();
            var a = comparator(trace, omitted);
            var b = comparator(trace, pictured);
            List<Object> first = null;
            for (int row = 0; row < trace.frameCount(); row++) {
                a.afterFrameAdvanced(input(row), false);
                b.afterFrameAdvanced(input(row), false);
                if (first == null) first = pose(ghost(omitted, "main:sonic"));
                for (int picture = 0; picture < row % 5; picture++) {
                    pictured.render(session.player(), List.of());
                    // Nonmatching layer passes must not advance the visual either.
                    pictured.renderForLayer(session.player(), List.of(), 99, true);
                }
                assertEquals(pose(ghost(omitted, "main:sonic")), pose(ghost(pictured, "main:sonic")),
                        "picture count must not change row " + row);
            }
            assertArrayEquals(picture(session, omitted).pixels(), picture(session, pictured).pixels(),
                    "the same admitted prefix must draw identically after omitted or varying pictures");
            assertNotEquals(first, pose(ghost(omitted, "main:sonic")),
                    "omitting all pictures still advances the actual animation script");
            a.seekForRewind(9);
            var prefix = new GhostTraceRenderer();
            prefix.synchronizeVisualRows(trace, 9, true);
            assertEquals(pose(ghost(prefix, "main:sonic")), pose(ghost(omitted, "main:sonic")));
            for (int row = 9; row < 24; row++) a.afterFrameAdvanced(input(row), false);
            assertEquals(pose(ghost(pictured, "main:sonic")), pose(ghost(omitted, "main:sonic")),
                    "replaying a backward seek must recover identical animation history");
            a.seekForRewind(0);
            assertEquals(first, pose(ghost(omitted, "main:sonic")), "seek to start reconstructs row zero");
            a.setVisualRowObserver(omitted::synchronizeVisualRows);
            var rebound = ghost(omitted, "main:sonic");
            assertEquals(0, rebound.getAnimationFrameIndex(), "rebinding cursor zero discards the old script");
            a.afterFrameAdvanced(input(0), false);
            assertEquals(first, pose(ghost(omitted, "main:sonic")));
        }
    }

    @Test
    void lagVisibilityAndIndependentSidekickLayersCannotClockAnimation() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s2"), 0, 0, settings);
            var main = session.player();
            var sidekick = new Tails("tails", (short) 0, (short) 0);
            sidekick.setCentreX(main.getCentreX());
            sidekick.setCentreY(main.getCentreY());
            sidekick.setPriorityBucket(3);
            sidekick.setHighPriority(true);
            TraceFrame full = withSidekick(row(0, main), 0, 0, sideState(main, false, true));
            TraceFrame lag = withSidekick(row(1, main), 0, 1, sideState(main, false, true));
            TraceFrame next = withSidekick(row(2, main), 1, 2, sideState(main, true, true));
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0), List.of(full, lag, next));
            var renderer = new GhostTraceRenderer();
            var comparator = comparator(trace, renderer);
            var realBefore = pose(main);
            comparator.afterFrameAdvanced(input(0), false);
            var mainBefore = pose(ghost(renderer, "main:sonic"));
            var tailBefore = pose(ghost(renderer, "sidekick0:tails"));
            comparator.afterFrameAdvanced(input(1), true);
            assertEquals(mainBefore, pose(ghost(renderer, "main:sonic")), "lag row does not dispatch Animate");
            assertEquals(tailBefore, pose(ghost(renderer, "sidekick0:tails")));
            comparator.afterFrameAdvanced(input(2), false);
            assertTrue(ghost(renderer, "sidekick0:tails").getRolling());
            assertFalse(ghost(renderer, "main:sonic").getRolling());
            var mainFinal = pose(ghost(renderer, "main:sonic"));
            var tailFinal = pose(ghost(renderer, "sidekick0:tails"));
            for (int i = 0; i < 10; i++) {
                renderer.renderForLayer(main, List.of(sidekick), 99, false);
                renderer.renderForLayer(main, List.of(sidekick), main.getPriorityBucket(), main.isHighPriority());
                renderer.renderForLayer(main, List.of(sidekick), 3, true);
            }
            assertEquals(mainFinal, pose(ghost(renderer, "main:sonic")));
            assertEquals(tailFinal, pose(ghost(renderer, "sidekick0:tails")));
            assertEquals(realBefore, pose(main), "comparison values must never hydrate gameplay");

            // A coincident, invisible main still advances its private visual history.
            var coincident = new com.openggf.sprites.playable.Sonic("sonic", full.x(), full.y());
            coincident.setCentreX(full.x());
            coincident.setCentreY(full.y());
            renderer.render(coincident, List.of());
            assertEquals(mainFinal, pose(ghost(renderer, "main:sonic")));
            comparator.seekForRewind(1);
            assertEquals(mainBefore, pose(ghost(renderer, "main:sonic")));
            assertEquals(tailBefore, pose(ghost(renderer, "sidekick0:tails")));
        }
    }

    @Test
    void resetAndReloadRecreateVisualsWithoutReusingOldLevelArtBanks() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s2"), 0, 0, settings);
            var renderer = new GhostTraceRenderer();
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0),
                    java.util.stream.IntStream.range(0, 12).mapToObj(i -> row(i, session.player())).toList());
            renderer.synchronizeVisualRows(trace, 12, true);
            var old = ghost(renderer, "main:sonic");
            var expected = pose(old);
            renderer.reset();
            assertEquals(0, slotCount(renderer));
            renderer.synchronizeVisualRows(trace, 12, true);
            assertNotSame(old, ghost(renderer, "main:sonic"));
            assertEquals(expected, pose(ghost(renderer, "main:sonic")));
            old = ghost(renderer, "main:sonic");
            GameServices.level().loadZoneAndAct(0, 0);
            renderer.synchronizeVisualRows(trace, 12, false);
            assertNotSame(old, ghost(renderer, "main:sonic"), "reload retires old art and script ownership");
            assertEquals(expected, pose(ghost(renderer, "main:sonic")));
        }
    }

    @Test
    void rowIdentityAndSidekickPresenceSurviveHiddenPictures() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom("s2"), 0, 0, settings);
            var rows = new java.util.ArrayList<TraceFrame>();
            // Distinct semantic rows deliberately share a raw CSV frame label.
            for (int i = 0; i < 6; i++) {
                rows.add(withSidekick(row(0, session.player()), i, i,
                        i == 4 ? TraceCharacterState.absent() : sideState(session.player(), false, true)));
            }
            var trace = TraceFixtures.trace(TraceFixtures.metadata("s2", 0, 0), rows);
            var renderer = new GhostTraceRenderer();
            var comparator = comparator(trace, renderer);
            comparator.afterFrameAdvanced(input(0), false);
            var firstMain = pose(ghost(renderer, "main:sonic"));
            var firstTail = pose(ghost(renderer, "sidekick0:tails"));
            for (int i = 1; i < 4; i++) comparator.afterFrameAdvanced(input(i), false);
            assertNotEquals(firstMain, pose(ghost(renderer, "main:sonic")),
                    "raw frame labels cannot deduplicate distinct admitted rows");
            comparator.afterFrameAdvanced(input(4), false);
            assertFalse(ghost(renderer, "sidekick0:tails").isNativeSlotPresent());
            comparator.afterFrameAdvanced(input(5), false);
            assertEquals(firstTail, pose(ghost(renderer, "sidekick0:tails")),
                    "a returning sidekick starts its own fresh visual script");
            var main = pose(ghost(renderer, "main:sonic"));
            comparator.seekForRewind(6);
            assertEquals(main, pose(ghost(renderer, "main:sonic")), "seek reconstructs the entire same-label prefix");
            assertEquals(firstTail, pose(ghost(renderer, "sidekick0:tails")));
        }
    }

    static LiveTraceComparator comparator(TraceData trace, GhostTraceRenderer renderer) {
        var comparator = new LiveTraceComparator(trace, ToleranceConfig.DEFAULT, 0, () -> null);
        comparator.setVisualRowObserver(renderer::synchronizeVisualRows);
        return comparator;
    }

    static Bk2FrameInput input(int index) {
        return new Bk2FrameInput(index, AbstractPlayableSprite.INPUT_RIGHT, 0, false, "right");
    }

    static TraceFrame withSidekick(TraceFrame row, int gameplay, int vblank, TraceCharacterState sidekick) {
        return new TraceFrame(row.frame(), row.input(), row.x(), row.y(), row.xSpeed(), row.ySpeed(),
                row.gSpeed(), row.angle(), row.air(), row.rolling(), row.groundMode(), 0, 0, 2, -1, -1, -1,
                0, gameplay, -1, vblank, 0, -1, -1, sidekick);
    }

    static TraceCharacterState sideState(AbstractPlayableSprite main, boolean rolling, boolean present) {
        return new TraceCharacterState(present, (short) (main.getCentreX() + 130), main.getCentreY(),
                (short) 0x400, (short) 0, (short) 0x400, (byte) 0, false, rolling, 0, 0, 0, 2, 0, -1);
    }

    static int slotCount(GhostTraceRenderer renderer) throws Exception {
        Field field = GhostTraceRenderer.class.getDeclaredField("slots");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(renderer)).size();
    }

    static com.openggf.graphics.RgbaImage picture(GameplayCaptureSession session, GhostTraceRenderer renderer) {
        session.render(false);
        renderer.render(session.player(), List.of());
        GameServices.graphics().flush();
        org.lwjgl.opengl.GL11.glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    static Path rom(String game) {
        String key = game.equals("s1") ? "sonic1.rom.path" : "sonic2.rom.path";
        String configured = System.getProperty(key);
        assertNotNull(configured, "pass the original ROM with -D" + key);
        Path path = Path.of(configured).toAbsolutePath();
        assertTrue(java.nio.file.Files.isRegularFile(path));
        return path;
    }

    static TraceFrame row(int index, AbstractPlayableSprite real) {
        return new TraceFrame(index, AbstractPlayableSprite.INPUT_RIGHT,
                (short) (real.getCentreX() + 96), real.getCentreY(),
                (short) 0x600, (short) 0, (short) 0x600, (byte) 0,
                false, false, 0, 0, 0, 2, -1, -1, -1, 0,
                index, -1, index, 0);
    }

    static AbstractPlayableSprite ghost(GhostTraceRenderer renderer, String key) throws Exception {
        Field field = GhostTraceRenderer.class.getDeclaredField("slots");
        field.setAccessible(true);
        Object slot = ((Map<?, ?>) field.get(renderer)).get(key);
        assertNotNull(slot, "ghost art slot should exist");
        Method sprite = slot.getClass().getDeclaredMethod("sprite");
        sprite.setAccessible(true);
        return (AbstractPlayableSprite) sprite.invoke(slot);
    }

    static List<Object> pose(AbstractPlayableSprite sprite) {
        return List.of(sprite.getAnimationId(), sprite.getMappingFrame(), sprite.getAnimationFrameIndex(),
                sprite.getAnimationTick(), sprite.getRenderHFlip(), sprite.getRenderVFlip(),
                sprite.getAnimationManager().captureRewindState());
    }
}
