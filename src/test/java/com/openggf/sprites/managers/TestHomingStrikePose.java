package com.openggf.sprites.managers;

import com.openggf.game.CanonicalAnimation;
import com.openggf.level.objects.HomingSheetFixture;
import com.openggf.physics.Direction;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static com.openggf.game.CanonicalAnimation.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestHomingStrikePose {
    @Test void octantsAndIntegerVelocitiesOnBothSidesOfBoundaries() {
        for (int facing : new int[]{-1, 1}) {
            assertDirection(facing, 10000, 0, HOMING_DIVE_FORWARD);
            assertDirection(facing, 10000, -10000, HOMING_DIVE_FORWARD_UP);
            assertDirection(facing, 0, -10000, HOMING_DIVE_UP);
            assertDirection(facing, 10000, 10000, HOMING_DIVE_FORWARD_DOWN);
            assertDirection(facing, 0, 10000, HOMING_DIVE_DOWN);
            assertDirection(facing, 10000, 4142, HOMING_DIVE_FORWARD);
            assertDirection(facing, 10000, 4143, HOMING_DIVE_FORWARD_DOWN);
            assertDirection(facing, 10000, -4142, HOMING_DIVE_FORWARD);
            assertDirection(facing, 10000, -4143, HOMING_DIVE_FORWARD_UP);
            assertDirection(facing, 10000, 24142, HOMING_DIVE_FORWARD_DOWN);
            assertDirection(facing, 10000, 24143, HOMING_DIVE_DOWN);
            assertDirection(facing, 10000, -24142, HOMING_DIVE_FORWARD_UP);
            assertDirection(facing, 10000, -24143, HOMING_DIVE_UP);
            assertDirection(facing, 1, -10000, HOMING_DIVE_UP);
            assertDirection(facing, 1, 10000, HOMING_DIVE_DOWN);
            assertDirection(facing, 0, 0, HOMING_DIVE_FORWARD);
        }
    }

    private void assertDirection(int facing, int x, int y, CanonicalAnimation expected) {
        assertEquals(expected, HomingStrikePose.direction(facing * x, y), x + ":" + y + ":" + facing);
    }

    @Test void directionOnlySheetFallsBackToNativeWhenExactDirectionIsAbsent() throws Exception {
        var scripts = HomingSheetFixture.sheet(HOMING_DIVE_UP).animationSet();
        assertEquals(259, HomingStrikePose.select(scripts, 0, -1000));
        assertEquals(-1, HomingStrikePose.select(scripts, 1000, 0));
        assertEquals(-1, HomingStrikePose.select(scripts, 0, 1000));
    }

    @Test void genericOnlySheetRetainsSameScriptForEveryVelocity() throws Exception {
        var scripts = HomingSheetFixture.sheet(HOMING_DIVE).animationSet();
        for (int x : new int[]{-1000, 0, 1000}) for (int y : new int[]{-1000, 0, 1000})
            assertEquals(256, HomingStrikePose.select(scripts, x, y));
        assertEquals(java.util.List.of(3, 4, 5), scripts.getScript(256).frames());
    }

    @Test void exactDirectionWinsOverGeneric() throws Exception {
        var scripts = HomingSheetFixture.sheet(HOMING_DIVE, HOMING_DIVE_DOWN).animationSet();
        assertEquals(261, HomingStrikePose.select(scripts, 0, 1000));
        assertEquals(256, HomingStrikePose.select(scripts, 0, -1000));
    }

    @Test void directionChangesKeepCursorAndTimerAndMirrorOnHeldTicks() throws Exception {
        var art = HomingSheetFixture.sheet(HOMING_DIVE, HOMING_DIVE_FORWARD, HOMING_DIVE_UP,
                HOMING_DIVE_DOWN, HOMING_DIVE_FORWARD_UP, HOMING_DIVE_FORWARD_DOWN);
        var sprite = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sprite.setAnimationSet(art.animationSet());
        sprite.setAnimationFrameCount(24);
        sprite.setForcedAnimationId(257);
        sprite.getAnimationManager().update(0);
        assertEquals(6, sprite.getMappingFrame());
        assertEquals(1, sprite.getAnimationFrameIndex());
        assertEquals(2, sprite.getAnimationTick());
        sprite.setForcedAnimationId(259);
        sprite.setDirection(Direction.LEFT);
        sprite.getAnimationManager().update(1);
        assertEquals(12, sprite.getMappingFrame());
        assertEquals(1, sprite.getAnimationFrameIndex());
        assertEquals(1, sprite.getAnimationTick());
        assertTrue(sprite.getRenderHFlip());
        sprite.setForcedAnimationId(256);
        sprite.getAnimationManager().update(2);
        assertEquals(3, sprite.getMappingFrame());
        assertEquals(1, sprite.getAnimationFrameIndex());
        assertEquals(0, sprite.getAnimationTick());
        sprite.setForcedAnimationId(261);
        sprite.getAnimationManager().update(3);
        assertEquals(19, sprite.getMappingFrame());
        assertEquals(2, sprite.getAnimationFrameIndex());
        assertEquals(2, sprite.getAnimationTick());
    }
}
