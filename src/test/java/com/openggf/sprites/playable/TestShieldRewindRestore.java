package com.openggf.sprites.playable;

import com.openggf.game.InstaShieldHandle;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PowerUpObject;
import com.openggf.game.PowerUpSpawner;
import com.openggf.game.ShieldType;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestShieldRewindRestore {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void refreshKeepsLiveMatchingShieldAndRequestsArtRefresh() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        RecordingShield restoredShield = new RecordingShield(ShieldType.FIRE);
        RecordingPowerUpSpawner spawner = new RecordingPowerUpSpawner(restoredShield);
        sprite.setPowerUpSpawner(spawner);
        sprite.giveShield(ShieldType.FIRE);

        sprite.refreshPowerUpObjectsAfterRewindRestore();

        assertSame(restoredShield, sprite.getShieldObject(),
                "A live shield object matching the restored shield type should remain linked");
        assertFalse(restoredShield.isDestroyed(),
                "Refresh must not destroy the restored matching shield object");
        assertEquals(1, restoredShield.artRefreshRequests,
                "Kept shields must force art/DPLC refresh after rewind restore");
        assertEquals(1, spawner.spawnShieldCalls,
                "The existing matching shield should be kept instead of respawned");
    }

    @Test
    void refreshRespawnsMissingShieldAndRequestsArtRefresh() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        RecordingShield spawnedShield = new RecordingShield(ShieldType.LIGHTNING);
        RecordingPowerUpSpawner spawner = new RecordingPowerUpSpawner(spawnedShield);
        sprite.setPowerUpSpawner(spawner);
        sprite.setShieldStateForTest(true, ShieldType.LIGHTNING);

        sprite.refreshPowerUpObjectsAfterRewindRestore();

        assertSame(spawnedShield, sprite.getShieldObject());
        assertEquals(1, spawnedShield.artRefreshRequests,
                "Respawned shields must also force art/DPLC refresh after rewind restore");
        assertEquals(1, spawner.spawnShieldCalls);
    }

    @Test
    void refreshRespawnsDestroyedShieldAndRequestsArtRefresh() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        RecordingShield destroyedShield = new RecordingShield(ShieldType.BUBBLE);
        RecordingShield replacementShield = new RecordingShield(ShieldType.BUBBLE);
        RecordingPowerUpSpawner spawner = new RecordingPowerUpSpawner(destroyedShield, replacementShield);
        sprite.setPowerUpSpawner(spawner);
        sprite.giveShield(ShieldType.BUBBLE);
        destroyedShield.destroy();

        sprite.refreshPowerUpObjectsAfterRewindRestore();

        assertSame(replacementShield, sprite.getShieldObject());
        assertTrue(destroyedShield.isDestroyed());
        assertEquals(1, replacementShield.artRefreshRequests,
                "Replacement shields must force art/DPLC refresh after rewind restore");
        assertEquals(2, spawner.spawnShieldCalls);
    }

    @Test
    void refreshRespawnsWrongTypeShieldAndRequestsArtRefresh() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 100, (short) 200);
        RecordingShield wrongTypeShield = new RecordingShield(ShieldType.BUBBLE);
        RecordingShield replacementShield = new RecordingShield(ShieldType.FIRE);
        RecordingPowerUpSpawner spawner = new RecordingPowerUpSpawner(wrongTypeShield, replacementShield);
        sprite.setPowerUpSpawner(spawner);
        sprite.giveShield(ShieldType.FIRE);

        sprite.refreshPowerUpObjectsAfterRewindRestore();

        assertSame(replacementShield, sprite.getShieldObject());
        assertTrue(wrongTypeShield.isDestroyed(),
                "A live shield object for a different type should be discarded");
        assertEquals(1, replacementShield.artRefreshRequests,
                "Replacement shields must force art/DPLC refresh after rewind restore");
        assertEquals(2, spawner.spawnShieldCalls);
    }

    private static final class RecordingPowerUpSpawner implements PowerUpSpawner {
        private final Queue<PowerUpObject> shields = new ArrayDeque<>();
        private int spawnShieldCalls;

        private RecordingPowerUpSpawner(PowerUpObject... shields) {
            this.shields.addAll(java.util.List.of(shields));
        }

        @Override
        public PowerUpObject spawnShield(PlayableEntity player, ShieldType type) {
            spawnShieldCalls++;
            return shields.poll();
        }

        @Override
        public PowerUpObject spawnInvincibilityStars(PlayableEntity player) {
            return null;
        }

        @Override
        public InstaShieldHandle createInstaShield(PlayableEntity player) {
            return null;
        }

        @Override
        public void registerObject(PowerUpObject obj) {
        }

        @Override
        public void spawnSplash(PlayableEntity player) {
        }
    }

    private static final class RecordingShield implements PowerUpObject {
        private final ShieldType type;
        private boolean destroyed;
        private int artRefreshRequests;

        private RecordingShield(ShieldType type) {
            this.type = type;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        @Override
        public boolean isDestroyed() {
            return destroyed;
        }

        @Override
        public void setVisible(boolean visible) {
        }

        @Override
        public boolean matchesShieldType(ShieldType restoredType) {
            return type == restoredType;
        }

        @Override
        public void refreshArtAfterRewindRestore() {
            artRefreshRequests++;
        }
    }
}
