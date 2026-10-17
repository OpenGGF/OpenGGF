package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Independent Obj_HCZBlock -> SolidObjectFull2 visibility/underside contract. */
@RequiresRom(SonicGame.SONIC_3K)
class TestHczBlockFull2Visibility {
    private static final int X = 0x400;
    private static final int Y = 0x400;

    @ParameterizedTest
    @ValueSource(ints = {0x380, 0x420, 0x500})
    void full2UndersideDoesNotDependOnVisibility(int cameraY) {
        var fixture = setup(cameraY);
        var p2 = sidekick();
        var block = block();
        if (cameraY == 0x380) {
            assertTrue(p2.isRenderFlagOnScreen());
            assertTrue(block.isWithinSolidContactBounds());
        } else if (cameraY == 0x420) {
            assertTrue(p2.isRenderFlagOnScreen());
            assertFalse(block.isWithinSolidContactBounds());
        } else {
            assertFalse(p2.isRenderFlagOnScreen());
            assertFalse(block.isWithinSolidContactBounds());
        }
        resolveAndAssert(fixture);
    }

    @Test
    void offscreenFull2RestoresTwiceAndReplaysTheWholeWorld() {
        var fixture = setup(0x500);
        var objects = GameServices.level().getObjectManager();
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        var original = block();
        resolveAndAssert(fixture);
        fixture.stepIdleFrames(2);
        var after = registry.capture();
        for (int replay = 0; replay < 2; replay++) {
            registry.restore(before);
            assertNotSame(original, block());
            assertWorld(before, registry.capture());
            resolveAndAssert(fixture);
            fixture.stepIdleFrames(2);
            assertWorld(after, registry.capture());
        }
    }

    private static HeadlessTestFixture setup(int cameraY) {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1)
                .withSkippedZoneIntro().build();
        fixture.sprite().setCentreX((short) 0x600);
        fixture.sprite().setCentreY((short) 0x580);
        var objects = GameServices.level().getObjectManager();
        objects.createDynamicObject(() -> new HCZBlockObjectInstance(
                new ObjectSpawn(X, Y, Sonic3kObjectIds.HCZ_BLOCK, 0, 0, false, 0)));
        fixture.camera().setFrozen(true);
        fixture.camera().setX((short) 0x380);
        fixture.camera().setY((short) cameraY);
        // Admit the real dynamic object through its ordinary first dispatch;
        // do not mistake the engine's initial contact guard for visibility.
        fixture.stepIdleFrames(1);
        assertFalse(block().isSkipSolidContactThisFrame());
        var p2 = sidekick();
        assertTrue(p2.isCpuControlled());
        p2.setObjectControlled(false);
        p2.setOnObject(false);
        p2.setRolling(true);
        p2.setAir(true);
        p2.setCentreX((short) X);
        p2.setCentreY((short) (Y + 0x19));
        p2.setSubpixelRaw(0x3500, 0xA500);
        p2.setXSpeed((short) -0x100);
        p2.setYSpeed((short) -0x300);
        p2.setGSpeed((short) 0x123);
        assertEquals(0xE, p2.getYRadius());
        assertEquals(0xF, p2.getStandYRadius());
        objects.snapshotTouchResponseState();
        GameServices.sprites().refreshPlayableRenderFlags(fixture.camera());
        return fixture;
    }

    private static AbstractPlayableSprite sidekick() {
        return GameServices.sprites().getSidekicks().getFirst();
    }

    private static HCZBlockObjectInstance block() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof HCZBlockObjectInstance && o.getX() == X && o.getY() == Y)
                .map(HCZBlockObjectInstance.class::cast).findFirst().orElseThrow();
    }

    private static void resolveAndAssert(HeadlessTestFixture fixture) {
        var p2 = sidekick();
        GameServices.level().getObjectManager().processImmediateInlineSolidCheckpoint(
                block(), fixture.sprite(), List.of(p2));
        // loc_1F3CA passes d2=$10. SolidObject_cont uses current radiusE and
        // default radiusF; loc_1E104 subtracts d3 and preserves y_sub.
        // Full2 bypasses both player and object render_flags gates.
        assertEquals(Y + 0x10 + p2.getStandYRadius(), p2.getCentreY() & 0xFFFF,
                "Full2 underside separation applies off screen");
        assertEquals(0xA500, p2.getYSubpixelRaw());
        assertEquals(0, p2.getYSpeed());
        assertEquals(0, p2.getGSpeed());
        assertTrue(p2.getAir());
        assertTrue(p2.getRolling());
        assertFalse(p2.isOnObject());
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + ": " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
