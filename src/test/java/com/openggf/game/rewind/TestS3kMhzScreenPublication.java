package com.openggf.game.rewind;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A presentation rebuild must not replace the animation/collision owner's publication. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMhzScreenPublication {
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    @Test void renderingAnotherCameraCannotChangeTheRetainedWorld() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 1)
                .withSkippedZoneIntro().startPosition((short) 15008, (short) 704)
                .startPositionIsCentre().build();
        fixture.stepIdleFrames(2);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        var handler = GameServices.parallax().getHandler(Sonic3kZoneIds.ZONE_MHZ);
        handler.update(new int[224], fixture.camera().getX() + 0x180,
                fixture.camera().getY(), GameServices.level().getFrameCounter(), 1);
        var after = registry.capture();
        for (var key : before.entries().keySet()) {
            if (!key.toLowerCase().contains("parallax")) {
                assertTrue(RewindSnapshotDiff.keyEquals(key, before.get(key), after.get(key)),
                        () -> key + ": " + RewindSnapshotDiff.diffKey(key, before.get(key), after.get(key)));
            }
        }
    }
    @Test void bossDefeatPreparesTimedShakeForTheFollowingScreenEvent() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 1)
                .withSkippedZoneIntro().startPosition((short) 15568, (short) 704)
                .startPositionIsCentre().build();
        fixture.stepIdleFrames(2);
        var boss = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance.class::isInstance)
                .map(com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance.class::cast)
                .findFirst().orElseThrow();
        // Component entry at loc_76164's threshold; run the production flag writer.
        var defeat = boss.getClass().getDeclaredMethod("startFinalDefeatFadeWait");
        defeat.setAccessible(true); defeat.invoke(boss);
        var events = ((com.openggf.game.sonic3k.Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider()).getMhzEvents();
        assertEquals(20, events.getScreenShakeFlag());
        events.setAct2BackgroundRoutineForTest(0x18);
        events.update(1, GameServices.level().getFrameCounter());
        assertEquals(19, events.getScreenShakeFlag());
        assertEquals(-5, events.getScreenShakeOffset());
        int unshaken = fixture.camera().getX();
        fixture.camera().setXCopy((short) unshaken);
        events.update(1, GameServices.level().getFrameCounter() + 1);
        assertEquals((short) (unshaken - 5), fixture.camera().getXCopy());
        var saved = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepIdleFrames(3);
        var forward = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.gameplayMode().getRewindRegistry().restore(saved);
        fixture.stepIdleFrames(3);
        var replay = fixture.gameplayMode().getRewindRegistry().capture();
        for (var key : forward.entries().keySet())
            assertTrue(RewindSnapshotDiff.keyEquals(key, forward.get(key), replay.get(key)), key);
    }
    @Test void ordinaryShakeMovesTheRenderCameraAndSpriteDrawExactlyOnce() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 1)
                .withSkippedZoneIntro().startPosition((short) 15568, (short) 704)
                .startPositionIsCentre().build();
        var events = ((com.openggf.game.sonic3k.Sonic3kLevelEventManager)
                GameServices.module().getLevelEventProvider()).getMhzEvents();
        events.setAct2BackgroundRoutineForTest(0x18);
        events.requestScreenShake(20);
        fixture.stepIdleFrames(2); // ordinary camera -> events -> parallax -> render bounds
        assertEquals(-5, events.appliedScreenShakeOffset(), "second dispatch consumes the preceding setup");
        var camera = fixture.camera();
        int expectedCamera = (short) (camera.getX() - 5);
        var graphics = GameServices.graphics();
        var world = com.openggf.level.render.SpritePresentationRenderer.prepare(
                graphics, 0, 0, fixture.sprite()::draw);
        var drawn = com.openggf.level.render.SpritePresentationRenderer.prepare(
                graphics, camera.getXWithShake(), camera.getYWithShake(), fixture.sprite()::draw);
        assertFalse(drawn.tiles().isEmpty(), "actual loaded playable mapping emits sprite tiles");
        assertEquals(world.tiles().size(), drawn.tiles().size());
        var cameraLeft = com.openggf.level.objects.AbstractObjectInstance.class.getDeclaredMethod("cameraLeft");
        cameraLeft.setAccessible(true);
        assertAll(
                () -> assertEquals(expectedCamera, camera.getXWithShake(), "render camera consumes one ROM offset"),
                () -> assertEquals(expectedCamera, cameraLeft.invoke(null), "Render_Sprites visibility bounds"),
                () -> {
                    for (int i = 0; i < drawn.tiles().size(); i++)
                        assertEquals(world.tiles().get(i).x() - expectedCamera, drawn.tiles().get(i).x(),
                                "actual sprite draw tile " + i + " uses the singly shaken origin");
                });
    }
}
