package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeArtAdmissionPolicy;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFixedSstTransitionRewind {
    @ParameterizedTest
    @CsvSource({"7,4", "1,5"})
    void carriedFixedOccupantReplacesFreshInitializationOwner(int zone, int slot) throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withSharedLevel(SharedLevel.load(SonicGame.SONIC_3K, zone, 0)).build();
        var level = GameServices.level();
        var previous = level.getObjectManager();
        var original = previous.getActiveObjects().stream()
                .filter(o -> o instanceof AbstractObjectInstance a && a.getSlotIndex() == slot)
                .findFirst().orElseThrow();
        var originalId = previous.captureIdentityContext().requireIdentityTable().encodeObject(original);
        level.executeActTransition(SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(zone, 1)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.TITLE_OWNER)
                .objectSurvivalPolicy(SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.PERSISTENT_EXACT_SST)
                .preserveLevelGamestate(true)
                .build());

        var manager = level.getObjectManager();
        var instaShield = fixture.sprite().getInstaShieldObject();
        assertNotNull(instaShield);
        assertEquals(1, manager.getActiveObjects().stream().filter(o -> o == instaShield).count());
        // Offset application follows manager rebuilding. The next player update
        // must not register this already-carried fixed owner a second time.
        fixture.stepFrame(false, false, false, false, false);
        assertEquals(1, manager.getActiveObjects().stream().filter(o -> o == instaShield).count(),
                "the first post-load player update must retain one Insta-Shield owner");
        var registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        var snapshot = assertDoesNotThrow(registry::capture,
                "the seamless boundary must not register fresh and carried owners under the same identity");
        assertEquals(1, manager.getActiveObjects().stream()
                .filter(original.getClass()::isInstance).count());
        assertTrue(manager.getActiveObjects().contains(original));
        assertEquals(slot, ((AbstractObjectInstance) original).getSlotIndex());
        assertSame(original, manager.captureIdentityContext().requireIdentityTable().resolve(originalId));
        assertDoesNotThrow(() -> registry.restore(snapshot));
        assertEquals(1, manager.getActiveObjects().stream()
                .filter(original.getClass()::isInstance).count());
        assertDoesNotThrow(registry::capture);
    }

    /**
     * Shield and Invincibility_stars are fixed Level_object_RAM slots
     * (sonic3k.constants.asm:325-328). MHZ1_BackgroundEvent's Load_Level reload
     * (sonic3k.asm:112320-112338) never clears them, so a shield gained in act 1 keeps
     * running in act 2. The persistent-only carry must not drop those owners while the
     * player keeps the shield status, or registry restore re-spawns an object the live
     * world no longer had (fresh 320px MHZ route, act-two release at frame 9257).
     */
    @ParameterizedTest
    @CsvSource({"FIRE,false", "LIGHTNING,false", "BUBBLE,true"})
    void playerBoundPowerUpsSurviveThePersistentExactSstCarry(ShieldType type, boolean invincible)
            throws Exception {
        var fixture = HeadlessTestFixture.builder()
                .withSharedLevel(SharedLevel.load(SonicGame.SONIC_3K, 7, 0)).build();
        var level = GameServices.level();
        var player = fixture.sprite();
        player.giveShield(type);
        if (invincible) player.giveInvincibility();
        fixture.stepFrame(false, false, false, false, false);
        var shield = player.getShieldObject();
        var stars = player.getInvincibilityObject();
        assertNotNull(shield);
        assertEquals(invincible, stars != null);
        int shieldSlot = ((AbstractObjectInstance) shield).getSlotIndex();

        level.executeActTransition(SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(7, 1)
                .runtimeArtAdmissionPolicy(RuntimeArtAdmissionPolicy.TITLE_OWNER)
                .objectSurvivalPolicy(SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.PERSISTENT_EXACT_SST)
                .preserveLevelGamestate(true)
                .build());
        fixture.stepFrame(false, false, false, false, false);

        var manager = level.getObjectManager();
        assertTrue(player.hasShield());
        assertSame(shield, player.getShieldObject());
        assertEquals(1, manager.getActiveObjects().stream().filter(o -> o == shield).count(),
                "the act-two manager must run the act-one shield owner once");
        assertEquals(shieldSlot, ((AbstractObjectInstance) shield).getSlotIndex());
        if (invincible) {
            assertEquals(1, manager.getActiveObjects().stream().filter(o -> o == stars).count(),
                    "the act-two manager must run the act-one invincibility stars once");
        }

        RewindRegistry registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot saved = registry.capture();
        for (int frame = 0; frame < 8; frame++) fixture.stepFrame(false, false, false, false, false);
        CompositeSnapshot expected = registry.capture();
        registry.restore(saved);
        same(saved, registry.capture(), "restore");
        for (int frame = 0; frame < 8; frame++) fixture.stepFrame(false, false, false, false, false);
        same(expected, registry.capture(), "replay");
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
