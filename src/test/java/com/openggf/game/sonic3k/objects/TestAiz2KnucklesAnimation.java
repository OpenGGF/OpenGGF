package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestAiz2KnucklesAnimation {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test
    void nativeSetupRawCursorCadenceAndLaughJumpCommands() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var manager = GameServices.level().getObjectManager();
        var knux = manager.createDynamicObject(CutsceneKnucklesAiz2Instance::createDefault);
        knux.update(0, fixture.sprite());
        assertEquals(0x16, state(knux).mappingFrame);
        for (int i = 0; i < 120; i++) knux.update(i, fixture.sprite());
        assertEquals(0x16, state(knux).mappingFrame, "wait callback does not animate run-in");
        knux.update(0, fixture.sprite());
        assertEquals(0x0B, state(knux).mappingFrame, "raw preincrement reads the second entry");
        assertEquals(1, state(knux).animFrame);
        assertEquals(4, state(knux).animFrameTimer);
        for (int i = 0; i < 4; i++) {
            knux.update(i, fixture.sprite());
            assertEquals(0x0B, state(knux).mappingFrame);
        }
        knux.update(0, fixture.sprite());
        assertEquals(0x0C, state(knux).mappingFrame);
        while (!phase(knux).equals("LAUGH_1")) knux.update(0, fixture.sprite());
        assertEquals(0x1C, state(knux).mappingFrame);
        for (int i = 0; i < 9; i++) knux.update(i, fixture.sprite());
        assertEquals(0x1D, state(knux).mappingFrame, "intro holds each entry for eight dispatches");
        for (int i = 0; i < 8; i++) knux.update(i, fixture.sprite());
        assertEquals(0x666BF, state(knux).script, "F8 changes the retained script pointer");
        assertEquals(0x1E, state(knux).mappingFrame);
        while (!phase(knux).equals("JUMP")) knux.update(0, fixture.sprite());
        assertEquals(8, state(knux).mappingFrame);
        knux.update(0, fixture.sprite());
        assertEquals(4, state(knux).mappingFrame, "jump setup already published entry zero");
        knux.update(0, fixture.sprite());
        assertEquals(4, state(knux).mappingFrame);
        knux.update(0, fixture.sprite());
        assertEquals(8, state(knux).mappingFrame);
    }

    @Test
    void actualRecreationPreservesRawScriptCursorCountdownAndForwardPhase() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        ObjectManager manager = GameServices.level().getObjectManager();
        var current = manager.createDynamicObject(CutsceneKnucklesAiz2Instance::createDefault);
        current.update(0, fixture.sprite());
        for (String checkpoint : new String[]{"LAUGH_1", "JUMP", "LAUGH_2"}) {
            for (int entry = 0; !phase(current).equals(checkpoint) && entry < 600; entry++) {
                current.update(entry, fixture.sprite());
            }
            assertEquals(checkpoint, phase(current));
            for (int entry = 0; entry < 11; entry++) current.update(entry, fixture.sprite());
            var expected = state(current).captureRewindStateValue();
            var snapshot = fixture.gameplayMode().getRewindRegistry().capture();
            manager.removeDynamicObject(current);
            fixture.gameplayMode().getRewindRegistry().restore(snapshot);
            var restored = manager.getActiveObjects().stream()
                    .filter(CutsceneKnucklesAiz2Instance.class::isInstance)
                    .map(CutsceneKnucklesAiz2Instance.class::cast).findFirst().orElseThrow();
            assertNotSame(current, restored);
            assertEquals(expected, state(restored).captureRewindStateValue());
            for (int entry = 0; entry < 24; entry++) {
                current.update(entry, fixture.sprite());
                restored.update(entry, fixture.sprite());
                assertEquals(state(current).captureRewindStateValue(), state(restored).captureRewindStateValue());
                assertEquals(phase(current), phase(restored));
                assertEquals(current.getX(), restored.getX());
                assertEquals(current.getY(), restored.getY());
            }
            current = restored;
        }
    }

    private static S3kRawAnimation.State state(CutsceneKnucklesAiz2Instance object) throws Exception {
        var field = CutsceneKnucklesAiz2Instance.class.getDeclaredField("animation");
        field.setAccessible(true);
        return (S3kRawAnimation.State) field.get(object);
    }

    private static String phase(CutsceneKnucklesAiz2Instance object) throws Exception {
        var field = CutsceneKnucklesAiz2Instance.class.getDeclaredField("phase");
        field.setAccessible(true);
        return field.get(object).toString();
    }
}
