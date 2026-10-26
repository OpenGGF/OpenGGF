package com.openggf.sprites.playable;

import com.openggf.game.GameServices;
import com.openggf.game.PowerUpObject;
import com.openggf.game.PowerUpSpawner;
import com.openggf.game.ShieldType;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Private cache publication is observable from existing power-up callbacks. */
@Isolated
class TestInvincibilityRewindPublicationOrder {
    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @EnumSource(value = SonicGame.class, names = {"SONIC_1", "SONIC_2", "SONIC_3K"})
    void realPlayerPublishesClearAndReplacementBeforeCallbacksAndRetainsItOnFailure(SonicGame game)
            throws Exception {
        TestEnvironment.configureGameModuleFixture(game);
        // The real manager represents the rebuilt world, while the concrete
        // Sonic still holds a transient handle from the abandoned world.
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        Field managerField = LevelManager.class.getDeclaredField("objectManager");
        managerField.setAccessible(true);
        managerField.set(GameServices.level(), manager);
        Sonic player = new Sonic("sonic", (short) 100, (short) 200);
        PowerUpSpawner spawner = mock(PowerUpSpawner.class);
        PowerUpObject abandoned = mock(PowerUpObject.class, "abandonedStars");
        PowerUpObject replacement = mock(PowerUpObject.class, "replacementStars");
        when(spawner.spawnInvincibilityStars(player)).thenReturn(abandoned);
        player.setPowerUpSpawner(spawner);
        player.giveInvincibility(120);
        var saved = player.captureRewindState();
        player.setInvincibleFrames(0);
        player.restoreRewindState(saved);
        assertEquals(120, player.getInvincibleFrames());
        assertSame(abandoned, cachedStars(player));
        assertTrue(manager.getActiveObjects().isEmpty(), "the cached handle is not in the restored world");
        var events = new ArrayList<String>();
        doAnswer(call -> {
            assertSame(abandoned, cachedStars(player),
                    "old destroy runs before the private cache is cleared");
            events.add("destroy:old");
            return null;
        }).when(abandoned).destroy();
        when(spawner.spawnInvincibilityStars(player)).thenAnswer(call -> {
            assertNull(cachedStars(player),
                    "the private cache must already be cleared when the replacement factory enters");
            events.add("spawn:null");
            return replacement;
        });
        IllegalStateException refreshFailure = new IllegalStateException("observed refresh callback failure");
        doAnswer(call -> {
            assertSame(replacement, cachedStars(player),
                    "the replacement must already be published when its art callback enters");
            events.add("refresh:new");
            throw refreshFailure;
        }).when(replacement).refreshArtAfterRewindRestore();

        IllegalStateException actualFailure = assertThrows(IllegalStateException.class,
                player::refreshPowerUpObjectsAfterRewindRestore);

        assertSame(refreshFailure, actualFailure, "retain the callback's original failure");
        assertEquals(List.of("destroy:old", "spawn:null", "refresh:new"), events);
        assertSame(replacement, cachedStars(player),
                "an exceptional callback must not roll back the already-published private cache");
        assertSame(replacement, player.getInvincibilityObject());
        assertEquals(120, player.getInvincibleFrames(), "visual reconstruction does not advance protection");
        verify(abandoned).destroy();
        verify(replacement).refreshArtAfterRewindRestore();
        verify(replacement, never()).destroy();
        verify(spawner, times(2)).spawnInvincibilityStars(player); // initial grant, then restored visual
    }

    static Stream<Arguments> spawnerMutations() {
        return Stream.of(SonicGame.SONIC_1, SonicGame.SONIC_2, SonicGame.SONIC_3K)
                .flatMap(game -> Stream.of(Arguments.of(game, true), Arguments.of(game, false)));
    }

    @ParameterizedTest(name = "{0}: destroy callback binds replacement spawner = {1}")
    @MethodSource("spawnerMutations")
    void destroyCallbackRebindingSpawnerAndShieldTakesEffectBeforeFollowingVisualWork(
            SonicGame game, boolean installReplacementSpawner) throws Exception {
        TestEnvironment.configureGameModuleFixture(game);
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        Field managerField = LevelManager.class.getDeclaredField("objectManager");
        managerField.setAccessible(true);
        managerField.set(GameServices.level(), manager);
        Sonic player = new Sonic("sonic", (short) 100, (short) 200);
        PowerUpSpawner entrySpawner = mock(PowerUpSpawner.class, "entrySpawnerA");
        PowerUpSpawner callbackSpawner = mock(PowerUpSpawner.class, "callbackSpawnerB");
        PowerUpObject abandoned = mock(PowerUpObject.class, "abandonedStars");
        PowerUpObject replacement = mock(PowerUpObject.class, "replacementStars");
        PowerUpObject callbackShield = mock(PowerUpObject.class, "callbackCreatedShield");
        when(entrySpawner.spawnInvincibilityStars(player)).thenReturn(abandoned);
        when(entrySpawner.spawnShield(player, ShieldType.BASIC)).thenReturn(callbackShield);
        when(callbackSpawner.spawnShield(player, ShieldType.BASIC)).thenReturn(callbackShield);
        player.setPowerUpSpawner(entrySpawner);
        player.giveInvincibility(120);
        var saved = player.captureRewindState();
        player.setInvincibleFrames(0);
        player.restoreRewindState(saved);
        assertSame(abandoned, cachedStars(player));
        assertNull(cachedShield(player), "the shield does not exist when the helper is entered");
        assertTrue(manager.getActiveObjects().isEmpty());
        var events = new ArrayList<String>();
        doAnswer(call -> {
            assertFalse(call.getArgument(0, Boolean.class));
            assertSame(callbackShield, cachedShield(player),
                    "giveShield must publish the actual private shield reference before its callback");
            PowerUpObject observed = cachedStars(player);
            assertTrue(observed == abandoned || observed == replacement || observed == null);
            events.add(observed == abandoned ? "hide:old" : observed == replacement ? "hide:new" : "hide:null");
            return null;
        }).when(callbackShield).setVisible(anyBoolean());
        doAnswer(call -> {
            assertSame(abandoned, cachedStars(player), "the old cache remains published during its destroy callback");
            events.add("destroy:old");
            // Public player APIs only: install a new shield from within the
            // callback, then leave either B or null as the player's spawner.
            // The null case uses A only to create the shield before clearing A.
            player.setPowerUpSpawner(installReplacementSpawner ? callbackSpawner : entrySpawner);
            player.giveShield(ShieldType.BASIC);
            player.setPowerUpSpawner(installReplacementSpawner ? callbackSpawner : null);
            events.add(installReplacementSpawner ? "bind:B" : "bind:null");
            return null;
        }).when(abandoned).destroy();
        when(entrySpawner.spawnInvincibilityStars(player)).thenAnswer(call -> {
            fail("the spawner captured at helper entry must not be invoked after the destroy callback rebinds it");
            return null;
        });
        when(callbackSpawner.spawnInvincibilityStars(player)).thenAnswer(call -> {
            assertTrue(installReplacementSpawner, "clearing the spawner must suppress replacement creation");
            assertSame(callbackSpawner, player.getPowerUpSpawner());
            assertNull(cachedStars(player), "the old private cached owner is cleared before invoking B");
            events.add("spawn:B");
            return replacement;
        });
        doAnswer(call -> {
            assertSame(replacement, cachedStars(player), "B's result is published before refresh");
            assertSame(callbackShield, cachedShield(player));
            events.add("refresh:new");
            return null;
        }).when(replacement).refreshArtAfterRewindRestore();

        player.refreshPowerUpObjectsAfterRewindRestore();

        assertEquals(installReplacementSpawner
                        ? List.of("destroy:old", "hide:old", "bind:B", "spawn:B", "refresh:new", "hide:new")
                        : List.of("destroy:old", "hide:old", "bind:null", "hide:null"),
                events, "follow-on work rereads the current spawner and current shield, not entry copies");
        assertSame(installReplacementSpawner ? callbackSpawner : null, player.getPowerUpSpawner());
        assertSame(installReplacementSpawner ? replacement : null, cachedStars(player));
        assertSame(callbackShield, cachedShield(player));
        assertTrue(player.hasShield());
        assertEquals(120, player.getInvincibleFrames(), "the callbacks changed presentation ownership, not the timer");
        verify(entrySpawner).spawnInvincibilityStars(player); // initial grant only
        verify(callbackSpawner, times(installReplacementSpawner ? 1 : 0)).spawnInvincibilityStars(player);
        verify(callbackShield, times(2)).setVisible(false); // creation, then the helper's live follow-on
        verify(callbackShield, never()).destroy();
        verify(replacement, times(installReplacementSpawner ? 1 : 0)).refreshArtAfterRewindRestore();
    }

    private static PowerUpObject cachedShield(Sonic player) throws ReflectiveOperationException {
        Field field = AbstractPlayableSprite.class.getDeclaredField("shieldObject");
        field.setAccessible(true);
        return (PowerUpObject) field.get(player);
    }

    private static PowerUpObject cachedStars(Sonic player) throws ReflectiveOperationException {
        // Read the actual private field, independently of any bridge/helper
        // return value or getter an extraction might accidentally defer.
        Field field = AbstractPlayableSprite.class.getDeclaredField("invincibilityObject");
        field.setAccessible(true);
        return (PowerUpObject) field.get(player);
    }
}
