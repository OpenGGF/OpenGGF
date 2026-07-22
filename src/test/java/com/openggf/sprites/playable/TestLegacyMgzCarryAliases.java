package com.openggf.sprites.playable;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Sensor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLegacyMgzCarryAliases {

    @Test
    void legacyOwnerHandoffThenOwnerClearClearsEveryAlias() {
        LegacySprite sprite = new LegacySprite();
        ObjectInstance owner = new TestOwner();

        sprite.setMgzTopPlatformCarrySolidContactObject(owner);
        sprite.recordMgzTopPlatformSpringHandoff(0x320, -0x680);
        sprite.setMgzTopPlatformCarrySolidContactObject(null);

        assertNull(sprite.legacyOwner());
        assertFalse(sprite.hasMgzTopPlatformSpringHandoffPending());
        assertFalse(sprite.legacyHandoffPending());
        assertEquals(0, sprite.getMgzTopPlatformSpringHandoffXVel());
        assertEquals(0, sprite.getMgzTopPlatformSpringHandoffYVel());
    }

    @Test
    void directProtectedAliasWritesDriveTheCompatibilityMethods() {
        LegacySprite sprite = new LegacySprite();
        ObjectInstance owner = new TestOwner();

        sprite.writeLegacyOwner(owner);
        assertTrue(sprite.isMgzTopPlatformCarryOwnedBy(owner));
        sprite.recordMgzTopPlatformSpringHandoff(0x240, -0x500);
        assertTrue(sprite.hasMgzTopPlatformSpringHandoffPending());

        sprite.writeLegacyHandoff(true, 0x111, -0x222);
        assertEquals(0x111, sprite.getMgzTopPlatformSpringHandoffXVel());
        assertEquals(-0x222, sprite.getMgzTopPlatformSpringHandoffYVel());

        sprite.writeLegacyOwner(null);
        assertFalse(sprite.isMgzTopPlatformCarryOwnedBy(owner));
        assertFalse(sprite.allowsSolidContactsWhileObjectControlled(owner));
    }

    @Test
    void objectControlReleaseDebugAndDeferredCleanupClearBothRepresentations() {
        LegacySprite sprite = new LegacySprite();
        ObjectInstance owner = new TestOwner();

        sprite.seedLegacyCarry(owner);
        sprite.setObjectControlled(false);
        sprite.assertLegacyCarryCleared();

        sprite.seedLegacyCarry(owner);
        sprite.releaseFromObjectControl(42);
        sprite.assertLegacyCarryCleared();

        sprite.seedLegacyCarry(owner);
        sprite.toggleDebugMode();
        sprite.assertLegacyCarryCleared();

        sprite.seedLegacyCarry(owner);
        sprite.deferObjectControlRelease();
        sprite.endOfTick();
        sprite.assertLegacyCarryCleared();
    }

    private static final class LegacySprite extends AbstractPlayableSprite {
        private LegacySprite() {
            super("legacy-mgz", (short) 0, (short) 0);
        }

        private void writeLegacyOwner(ObjectInstance owner) {
            mgzTopPlatformCarrySolidContactObject = owner;
        }

        private void writeLegacyHandoff(boolean pending, int xVelocity, int yVelocity) {
            mgzTopPlatformSpringHandoffPending = pending;
            mgzTopPlatformSpringHandoffXVel = xVelocity;
            mgzTopPlatformSpringHandoffYVel = yVelocity;
        }

        private void seedLegacyCarry(ObjectInstance owner) {
            writeLegacyOwner(owner);
            writeLegacyHandoff(true, 0x111, -0x222);
            assertTrue(isMgzTopPlatformCarryOwnedBy(owner));
        }

        private void assertLegacyCarryCleared() {
            assertNull(mgzTopPlatformCarrySolidContactObject);
            assertFalse(mgzTopPlatformSpringHandoffPending);
            assertEquals(0, mgzTopPlatformSpringHandoffXVel);
            assertEquals(0, mgzTopPlatformSpringHandoffYVel);
            assertFalse(hasMgzTopPlatformSpringHandoffPending());
        }

        private ObjectInstance legacyOwner() {
            return mgzTopPlatformCarrySolidContactObject;
        }

        private boolean legacyHandoffPending() {
            return mgzTopPlatformSpringHandoffPending;
        }

        @Override
        protected void defineSpeeds() {
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override
        public void draw() {
        }
    }

    private static final class TestOwner extends AbstractObjectInstance {
        private TestOwner() {
            super(new ObjectSpawn(0, 0, 1, 0, 0, false, 0), "legacy-mgz-owner");
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
