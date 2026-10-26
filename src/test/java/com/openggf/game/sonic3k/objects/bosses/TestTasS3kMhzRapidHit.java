package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.reel.S3kTestReelPresentation;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 3422M/5427S MHZ2 early-hit claim. Real initialization spawns both children;
 * one accepted gliding proxy contact precedes actual weather-child destruction.
 * No HP, routine, flash, damaged collision flag, or child-signal state is seeded.
 * Native loc_76004 reinitializes parent flags/HP on every routine0 dispatch;
 * sub_76782 observes proxy flags independently, even during the hit flash. */
class TestTasS3kMhzRapidHit {
    @AfterEach
    void cleanup() {
        com.openggf.game.session.SessionManager.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oneEarlyProxyHitRepeatsNativeDamageAfterWeatherDestruction(boolean earlyHit) {
        try (var reel = S3kTestReelPresentation.open()) {
            var h = new Harness();
            h.present(reel);
            if (earlyHit) h.attackProxy();
            assertEquals(9, h.boss.getState().hitCount, "proxy contact has not dispatched the parent tail");
            assertEquals(0, h.boss.getState().invulnerabilityTimer);
            assertEquals(0, h.hitSounds());
            assertEquals(earlyHit ? -2 : -1, h.proxy.getCollisionProperty());
            assertEquals(earlyHit ? 0 : 0x25, h.proxy.getCollisionFlags());
            h.releaseWeather(reel);
            assertEquals(earlyHit ? 8 : 9, h.boss.getState().hitCount,
                    "last routine0 resets HP9 before consuming the cleared proxy");
            for (int i = 0; i < 6; i++) {
                h.advance(1);
                h.present(reel);
                assertEquals(earlyHit ? 7 - i : 9, h.boss.getState().hitCount,
                        "each owner update consumes the retained proxy byte without another contact");
            }
            assertEquals(0x39, h.boss.getCustomFlag(0x2E));
            assertEquals(earlyHit ? 2 : 9, h.boss.getState().hitCount);
            assertEquals(earlyHit ? 1 : 0, h.proxyAttacks, "one initial attack, never six synthetic callbacks");
            assertEquals(earlyHit ? 1 : 0, h.hitSounds(), "repeated damage does not restart the flash");
            if (earlyHit) {
                assertTrue(h.boss.getState().invulnerable);
                assertEquals(24, h.boss.getState().invulnerabilityTimer);
                assertEquals(0xCF, h.boss.getCollisionFlags(), "routine0 restored the parent collision byte");
                assertEquals(0, h.proxy.getCollisionFlags(), "the proxy remains independently cleared");
            }
        }
    }

    @Test
    void earlyBodyOnlyHitIsDiscardedByRoutineZeroWithoutDamagingTheProxy() {
        try (var reel = S3kTestReelPresentation.open()) {
            var h = new Harness();
            h.player.setXSpeed((short) 0x600);
            var beforeContact = h.registry.capture();
            h.boss.onPlayerAttack(h.player, null);
            assertEquals(8, h.boss.getState().hitCount, "the struck body loses its own hit point at contact");
            assertEquals(0x25, h.proxy.getCollisionFlags(), "contact must not clear the unstruck proxy");
            assertEquals(-1, h.proxy.getCollisionProperty());
            assertEquals(0, h.boss.getState().invulnerabilityTimer);
            assertEquals(0x600, h.player.getXSpeed(), "only an admitted damage tail may stop the attacker");
            var afterContact = h.registry.capture();
            h.releaseWeather(reel);
            h.advance(6);
            h.present(reel);
            assertEquals(9, h.boss.getState().hitCount,
                    "routine0 resets a body-only hit; an untouched proxy cannot publish repeated damage");
            assertEquals(0x25, h.proxy.getCollisionFlags());
            assertFalse(h.boss.getState().invulnerable);
            assertEquals(0, h.boss.getState().invulnerabilityTimer);
            assertEquals(0, h.hitSounds(), "sub_76782 returns before starting a flash");
            assertEquals(0x39, h.boss.getCustomFlag(0x2E));
            assertEquals(0x600, h.player.getXSpeed());
            var afterTail = h.registry.capture();
            h.restore(afterContact, 0);
            h.releaseWeather(reel);
            h.advance(6);
            same(afterTail, h.registry.capture(), "body-only pending contact restore-forward");
            assertEquals(0x600, h.player.getXSpeed());
            h.restore(beforeContact, 0);
            h.boss.onPlayerAttack(h.player, null);
            same(afterContact, h.registry.capture(), "body-only pre-contact restore-forward");
            h.releaseWeather(reel);
            h.advance(6);
            same(afterTail, h.registry.capture(), "body-only complete local inlet replay");
        }
    }

    @Test
    void earlyHitGraphRestoresThroughWeatherDamageClampAndFinalDefeat() {
        try (var reel = S3kTestReelPresentation.open()) {
            var h = new Harness();
            var beforeAttack = h.registry.capture();
            h.attackProxy();
            var afterAttack = h.registry.capture();
            h.releaseWeather(reel);
            var beforeTail = h.registry.capture();
            h.advance(6);
            var afterTail = h.registry.capture();
            assertEquals(2, h.boss.getState().hitCount);

            h.restore(beforeTail, 2);
            h.advance(6);
            same(afterTail, h.registry.capture(), "six-update damage tail from fresh parent/proxy graph");
            h.restore(afterAttack, 0);
            h.releaseWeather(reel);
            same(beforeTail, h.registry.capture(), "weather destruction after restoring the cleared proxy");
            h.advance(6);
            same(afterTail, h.registry.capture(), "weather plus damage replay");
            h.restore(beforeAttack, 0);
            assertEquals(0x25, h.proxy.getCollisionFlags());
            h.attackProxy();
            same(afterAttack, h.registry.capture(), "one accepted contact after pre-contact restore");
            h.releaseWeather(reel);
            h.advance(6);
            same(afterTail, h.registry.capture(), "full local inlet replay");

            // Continue the reached state, without seeding HP/routine/arena flags.
            for (int i = 0; i < 24; i++) {
                h.advance(1);
                assertEquals(2, h.boss.getState().hitCount, "pre-arena floor survives every remaining flash tick");
                assertFalse(h.boss.isDefeated());
            }
            assertFalse(h.boss.getState().invulnerable);
            assertEquals(0, h.boss.getState().invulnerabilityTimer);
            assertEquals(0xCF, h.boss.getCollisionFlags());
            assertEquals(0x25, h.proxy.getCollisionFlags());
            assertEquals(-1, h.proxy.getCollisionProperty(), "flash expiry restores the proxy hit property");
            var afterFlash = h.registry.capture();
            h.advance(1);
            var next = h.registry.capture();
            assertEquals(2, h.boss.getState().hitCount, "restored proxy ends repeated damage");
            h.restore(afterFlash, 32);
            h.advance(1);
            same(next, h.registry.capture(), "collision-restoration boundary replay");

            List<Integer> phases = new ArrayList<>();
            phases.add(h.boss.getState().routine);
            while ((h.boss.getCustomFlag(0x38) & 0x80) == 0 && h.clock < 240) {
                h.advance(1);
                int phase = h.boss.getState().routine;
                if (phases.getLast() != phase) phases.add(phase);
            }
            assertEquals(List.of(4, 6, 8, 0xA, 0xC), phases);
            assertEquals(0x80, h.boss.getCustomFlag(0x38) & 0x80, "real movement callbacks admit the arena");
            assertNull(h.weather, "native weather wait retired its slot");
            assertEquals(2, h.boss.getState().hitCount);
            h.attackProxy(); // A distinct ordinary terminal hit AFTER the early-hit tail and flash expiry.
            assertEquals(2, h.boss.getState().hitCount, "terminal proxy contact waits for its parent tail");
            assertEquals(0, h.boss.getState().invulnerabilityTimer);
            int terminalClock = h.clock;
            var terminalFlash = h.registry.capture();
            h.advance(31);
            assertFalse(h.boss.isDefeated());
            assertEquals(1, h.boss.getState().invulnerabilityTimer);
            h.advance(1);
            assertTrue(h.boss.isDefeated());
            assertEquals(1, h.boss.getState().hitCount);
            assertEquals(0x500, h.boss.getState().xVel);
            assertEquals(0, h.proxy.getCollisionFlags());
            var defeated = h.registry.capture();
            h.restore(terminalFlash, terminalClock);
            h.advance(32);
            same(defeated, h.registry.capture(), "ordinary terminal flash after the reached early-hit chain");
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> label + " " + key + ": " + diff);
        }
    }

    private static final class Harness {
        final ObjectManager manager;
        final RewindRegistry registry = new RewindRegistry();
        final List<Integer> sounds = new ArrayList<>();
        final Knuckles player = new Knuckles("knuckles", (short) 0, (short) 0);
        MhzEndBossInstance boss;
        MhzEndBossHitProxyChild proxy;
        MhzEndBossWeatherMachineChild weather;
        int clock;
        int proxyAttacks;

        Harness() {
            TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
            ObjectManager[] holder = new ObjectManager[1];
            var runtime = new MhzZoneRuntimeState(1, PlayerCharacter.KNUCKLES);
            var roster = new com.openggf.sprites.managers.SpriteManager() {
                @Override public Knuckles getMainPlayable() { return player; }
            };
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public com.openggf.sprites.managers.SpriteManager spriteManager() { return roster; }
                @Override public MhzZoneRuntimeState zoneRuntimeState() { return runtime; }
                @Override public void playSfx(int id) { sounds.add(id); }
            };
            manager = new ObjectManager(List.of(), null, 0, null, null, null, null, services);
            holder[0] = manager;
            manager.reset(0);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            boss = manager.createDynamicObject(() -> new MhzEndBossInstance(
                    new ObjectSpawn(0x40C0, 0x2E0, Sonic3kObjectIds.MHZ_END_BOSS, 0, 0, false, 0)));
            boss.update(0, null);
            refresh();
            assertEquals(9, boss.getState().hitCount);
            assertEquals(0, boss.getState().routine);
            assertEquals(0x25, proxy.getCollisionFlags());
            assertNotEquals(0, weather.getCollisionFlags());
            registry.register(manager.rewindSnapshottable());
            registry.register(services.rng());
        }

        void attackProxy() {
            // Declared valid gliding contact boundary. No boss/child state is written.
            player.setCentreX((short) proxy.getX());
            player.setCentreY((short) proxy.getY());
            player.setAir(true);
            player.setDoubleJumpFlag(1);
            player.setXSpeed((short) 0x600);
            proxyAttacks++;
            proxy.onPlayerAttack(player, null);
            assertEquals(0x600, player.getXSpeed(), "proxy contact defers the stop to the parent damage tail");
        }

        void releaseWeather(S3kTestReelPresentation reel) {
            boss.update(1, player);
            assertEquals(0, boss.getState().routine);
            weather.onPlayerAttack(player, null);
            weather.update(1, player);
            present(reel);
            assertEquals(4, boss.getCustomFlag(0x38) & 4, "actual weather destruction raises the signal");
            clock = 2;
            boss.update(clock, player);
            present(reel);
            assertEquals(4, boss.getState().routine);
            assertEquals(0x3F, boss.getCustomFlag(0x2E));
        }

        void advance(int count) {
            for (int i = 0; i < count; i++) {
                boss.update(++clock, player);
                proxy.update(clock, player);
                if (weather != null) {
                    weather.update(clock, player);
                    // Retire the actual linked visual children before releasing
                    // their weather slot; the snapshot must not retain orphans.
                    for (var object : List.copyOf(manager.getActiveObjects())) {
                        if (object instanceof MhzEndBossWeatherVisualChild visual) {
                            visual.update(clock, player);
                            if (visual.isDestroyed()) manager.removeDynamicObject(visual);
                        }
                    }
                    if (weather.isDestroyed()) {
                        manager.removeDynamicObject(weather);
                        weather = null;
                    }
                }
            }
        }

        void restore(CompositeSnapshot checkpoint, int restoredClock) {
            var previous = boss;
            registry.restore(checkpoint);
            refresh();
            assertNotSame(previous, boss, "the replay must use freshly recreated owners");
            clock = restoredClock;
            same(checkpoint, registry.capture(), "exact graph restore");
        }

        void refresh() {
            boss = find(MhzEndBossInstance.class);
            proxy = find(MhzEndBossHitProxyChild.class);
            weather = manager.getActiveObjects().stream().filter(MhzEndBossWeatherMachineChild.class::isInstance)
                    .map(MhzEndBossWeatherMachineChild.class::cast).findFirst().orElse(null);
        }

        <T> T find(Class<T> type) {
            return manager.getActiveObjects().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
        }

        long hitSounds() {
            return sounds.stream().filter(id -> id == Sonic3kSfx.BOSS_HIT.id).count();
        }

        void present(S3kTestReelPresentation reel) {
            reel.afterObjects(manager.getActiveObjects().stream().map(AbstractObjectInstance.class::cast).toList(),
                    7, 1, 0x40C0, 0x270, Sonic3kObjectArtKeys.MHZ_END_BOSS, Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        }
    }
}
