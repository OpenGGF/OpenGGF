package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.events.Sonic3kMHZEvents;
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native sub_76782 damage floor and loc_767F6 delayed terminal handoff. */
class TestMhzEndBossNativeDamage {
    @Test
    void preArenaDamageClampsAtTwoButStillFlashesAndStopsTheAttacker() {
        var harness = new Harness(PlayerCharacter.SONIC_ALONE);
        var boss = harness.boss;
        boss.setCustomFlag(0x38, 0x48); // no bit7: the camera chase has not begun
        var player = new TestablePlayableSprite("sonic", (short) 0x4100, (short) 0x02E0);
        player.setXSpeed((short) 0x600);
        boss.onPlayerAttack(player, null);
        assertEquals(1, boss.getState().hitCount,
                "accepted touch publishes the transient HP1 before the object's routine tail");
        harness.advance(1);
        assertEquals(2, boss.getState().hitCount,
                "loc_767A0 clamps d0<=1 to2 using the post-movement $38 bit7");
        assertEquals(0, player.getXSpeed(), "even a floor-clamped hit clears the attacker's x_vel");
        assertEquals(31, boss.getState().invulnerabilityTimer);
        assertEquals(List.of(Sonic3kSfx.BOSS_HIT.id), harness.sounds);
        harness.advance(31);
        assertFalse(boss.isDefeated());
        assertEquals(0xCF, boss.getCollisionFlags());
        boss.onPlayerAttack(player, null);
        assertEquals(1, boss.getState().hitCount, "the next accepted touch is also transient");
        harness.advance(1);
        assertEquals(2, boss.getState().hitCount, "the pre-arena floor survives repeated legal hits");
    }

    @ParameterizedTest
    @EnumSource(value = PlayerCharacter.class, names = {"SONIC_ALONE", "TAILS_ALONE", "KNUCKLES"})
    void arenaFinalHitRetainsOneUntilTheExactFlashExpiryAndThenStartsEscape(PlayerCharacter character) {
        var harness = new Harness(character);
        var boss = harness.boss;
        boss.onPlayerAttack(null, null);
        assertEquals(1, boss.getState().hitCount);
        assertFalse(boss.isDefeated(), "loc_767B0 starts the flash, not the escape");
        assertEquals(32, boss.getState().invulnerabilityTimer);
        assertEquals(0, harness.proxy.getCollisionFlags());
        harness.advance(31);
        assertFalse(boss.isDefeated());
        assertEquals(1, boss.getState().invulnerabilityTimer);
        assertEquals(0, harness.proxy.getCollisionFlags());
        int priorX = boss.getState().x;
        harness.advance(1);
        assertTrue(boss.isDefeated(), "loc_767F6 branches loc_76822 at HP1 when $20 reaches0");
        assertEquals(1, boss.getState().hitCount, "native terminal collision_property remains1");
        assertEquals(priorX + 1, boss.getState().x,
                "loc_75FD4 executes the old movement before installing the escape at its tail");
        int escapeVelocity = character == PlayerCharacter.KNUCKLES ? 0x500 : 0x400;
        assertEquals(escapeVelocity, boss.getState().xVel);
        assertEquals(0, boss.getCustomFlag(0x38) & 0x40);
        assertEquals(0, harness.proxy.getCollisionFlags(), "terminal damage must never re-enable the hit proxy");
        harness.advance(1);
        assertEquals(priorX + 1 + (escapeVelocity >> 8), boss.getState().x);
    }

    @Test
    void nonterminalArenaHitRestoresCollisionAtFlashExpiry() {
        var harness = new Harness(PlayerCharacter.SONIC_ALONE);
        harness.boss.getState().hitCount = 3;
        harness.proxy.onPlayerAttack(null, null);
        harness.advance(32);
        assertEquals(2, harness.boss.getState().hitCount);
        assertFalse(harness.boss.isDefeated());
        assertEquals(0xCF, harness.boss.getCollisionFlags());
        assertEquals(0x25, harness.proxy.getCollisionFlags());
    }

    @Test
    void anotherAttackDuringTheFlashCannotConsumeTheTerminalCount() {
        var harness = new Harness(PlayerCharacter.KNUCKLES);
        harness.boss.onPlayerAttack(null, null);
        harness.boss.onPlayerAttack(null, null);
        harness.proxy.onPlayerAttack(null, null);
        assertEquals(1, harness.boss.getState().hitCount);
        assertEquals(32, harness.boss.getState().invulnerabilityTimer);
        assertEquals(1, harness.sounds.size());
    }

    @Test
    void midFlashFullObjectRestoreReplaysTheTerminalHandoff() {
        var harness = new Harness(PlayerCharacter.KNUCKLES);
        harness.boss.onPlayerAttack(null, null);
        harness.advance(11);
        var registry = new RewindRegistry();
        registry.register(harness.manager.rewindSnapshottable());
        var checkpoint = registry.capture();
        harness.advance(21);
        assertTrue(harness.boss.isDefeated());
        var terminal = registry.capture();
        var original = harness.boss;
        registry.restore(checkpoint);
        harness.boss = harness.manager.getActiveObjects().stream()
                .filter(MhzEndBossInstance.class::isInstance).map(MhzEndBossInstance.class::cast)
                .findFirst().orElseThrow();
        assertNotSame(original, harness.boss, "this boundary must restore through fresh native object recreation");
        harness.proxy = harness.manager.getActiveObjects().stream()
                .filter(MhzEndBossHitProxyChild.class::isInstance).map(MhzEndBossHitProxyChild.class::cast)
                .findFirst().orElseThrow();
        sameObjects(checkpoint, registry.capture(), "fresh moving-pair restoration");
        harness.clock = 11;
        assertFalse(harness.boss.isDefeated());
        assertEquals(21, harness.boss.getState().invulnerabilityTimer);
        harness.advance(21);
        sameObjects(terminal, registry.capture(), "terminal handoff replay");
    }

    @Test
    void admissionSetsArenaBitBeforeClampingTheAcceptedHit() {
        var harness = new Harness(PlayerCharacter.KNUCKLES);
        var boss = harness.boss;
        boss.getState().routine = 0x0A;
        boss.setCustomFlag(0x38, 0x48);
        boss.setCustomFlag(0x2E, 0);
        boss.onPlayerAttack(null, null);
        assertEquals(1, boss.getState().hitCount, "accepted touch precedes the movement dispatch");
        harness.advance(1);
        assertEquals(0x80, boss.getCustomFlag(0x38) & 0x80,
                "loc_760C4 sets bit7 before sub_76782's clamp on this same dispatch");
        assertEquals(1, boss.getState().hitCount, "the newly admitted arena allows the pending terminal hit");
        assertEquals(31, boss.getState().invulnerabilityTimer);
        harness.advance(31);
        assertTrue(boss.isDefeated());
        assertEquals(0x500, boss.getState().xVel);
    }

    @Test
    void escapeThresholdResetsNativeWaitBeforeMusicFadeUnderflow() {
        var harness = new Harness(PlayerCharacter.SONIC_ALONE);
        var boss = harness.boss;
        boss.getState().defeated = true;
        boss.getState().hitCount = 1;
        boss.getState().x = 0x44CC;
        boss.getState().xFixed = 0x44CC << 16;
        boss.getState().xVel = 0x400;
        harness.advance(1);
        assertEquals(0x3F, boss.getCustomFlag(0x2E),
                "BossDefeated_StopTimer falls through BossDefeated and replaces the borrowed dash wait");
        harness.advance(0x3F);
        assertEquals(0, boss.getCustomFlag(0x2E));
        assertEquals(0, harness.manager.getActiveObjects().stream()
                .filter(com.openggf.game.sonic3k.objects.SongFadeTransitionInstance.class::isInstance).count());
        harness.advance(1);
        assertEquals(119, boss.getCustomFlag(0x2E), "loc_85674 installs (2*60)-1 only after signed underflow");
        assertEquals(-0x200, boss.getState().yVel, "loc_761B2 is selected on that underflow update");
        assertEquals(1, harness.manager.getActiveObjects().stream()
                .filter(com.openggf.game.sonic3k.objects.SongFadeTransitionInstance.class::isInstance).count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void escapeRepeatsOnlyAfterTheThresholdCheck(boolean threshold) throws Exception {
        var events = new Sonic3kMHZEvents();
        var repeat = Sonic3kMHZEvents.class.getDeclaredField("levelRepeatOffset");
        repeat.setAccessible(true);
        repeat.setInt(events, 0x200);
        var harness = new Harness(PlayerCharacter.SONIC_ALONE, events);
        var boss = harness.boss;
        int before = threshold ? 0x44CC : 0x4100;
        boss.getState().defeated = true;
        boss.getState().hitCount = 1;
        boss.getState().x = before;
        boss.getState().xFixed = (before << 16) | 0xBEEF;
        boss.getState().xVel = 0x400;
        harness.advance(1);
        assertEquals(before + 4 - 0x200, boss.getState().x,
                "loc_761A4 subtracts Level_repeat_offset once after the threshold check");
        assertEquals(0xBEEF, boss.getState().xFixed & 0xFFFF,
                "native sub.w changes only x_pos, preserving its fractional word");
        assertEquals(threshold ? 0x3F : 1000, boss.getCustomFlag(0x2E),
                "a wrapping threshold frame still installs the native wait before rebasing");
    }

    @Test
    void arenaRepeatPreservesTheNativeFractionalWord() throws Exception {
        var events = new Sonic3kMHZEvents();
        var repeat = Sonic3kMHZEvents.class.getDeclaredField("levelRepeatOffset");
        repeat.setAccessible(true);
        repeat.setInt(events, 0x200);
        var harness = new Harness(PlayerCharacter.SONIC_ALONE, events);
        harness.boss.getState().x = 0x4100;
        harness.boss.getState().xFixed = (0x4100 << 16) | 0xBEEF;
        harness.advance(1);
        assertEquals(0x3F01, harness.boss.getState().x);
        assertEquals(0xBEEF, harness.boss.getState().xFixed & 0xFFFF,
                "loc_75FD4's normal repeat subtraction also preserves the native fraction");
    }

    @Test
    void freshMovingProxyRetainsItsExactParentWhenAnotherBossIsCloser() throws Exception {
        var harness = new Harness(PlayerCharacter.KNUCKLES);
        harness.advance(11);
        harness.manager.removeDynamicObject(harness.proxy);
        var other = harness.manager.createDynamicObject(() -> new MhzEndBossInstance(new ObjectSpawn(
                harness.boss.getState().x - 0xC0 + 0x10, 0x02E0, Sonic3kObjectIds.MHZ_END_BOSS,
                0, 0, false, 1)));
        other.setCustomFlag(0x100, 1);
        other.setCustomFlag(0x2E, 1000);
        other.getState().routine = 0x0E;
        other.update(11, null);
        harness.proxy = harness.manager.createDynamicObject(() -> new MhzEndBossHitProxyChild(harness.boss));
        var originalId = harness.manager.captureIdentityContext().requireIdentityTable().idFor(harness.boss);
        var registry = new RewindRegistry();
        registry.register(harness.manager.rewindSnapshottable());
        var checkpoint = registry.capture();
        registry.restore(checkpoint);
        var restoredParent = harness.manager.getActiveObjects().stream()
                .filter(MhzEndBossInstance.class::isInstance).map(MhzEndBossInstance.class::cast)
                .filter(boss -> originalId.equals(harness.manager.captureIdentityContext()
                        .requireIdentityTable().idFor(boss))).findFirst().orElseThrow();
        var restoredProxy = harness.manager.getActiveObjects().stream()
                .filter(MhzEndBossHitProxyChild.class::isInstance).map(MhzEndBossHitProxyChild.class::cast)
                .findFirst().orElseThrow();
        var link = MhzEndBossHitProxyChild.class.getDeclaredField("parent");
        link.setAccessible(true);
        assertSame(restoredParent, link.get(restoredProxy), "native parent3 identity must never select the nearer boss");
        sameObjects(checkpoint, registry.capture(), "two-parent moving proxy restoration");
    }

    private static void sameObjects(com.openggf.game.rewind.CompositeSnapshot expected,
                                    com.openggf.game.rewind.CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (var key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> label + " " + key + ": " + diff);
        }
    }

    private static final class Harness {
        final List<Integer> sounds = new ArrayList<>();
        final ObjectManager manager;
        MhzEndBossInstance boss;
        MhzEndBossHitProxyChild proxy;
        int clock;

        Harness(PlayerCharacter character) {
            this(character, null);
        }

        Harness(PlayerCharacter character, Sonic3kMHZEvents events) {
            ObjectManager[] holder = new ObjectManager[1];
            var runtime = events == null ? new MhzZoneRuntimeState(1, character)
                    : new MhzZoneRuntimeState(1, character, events);
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public MhzZoneRuntimeState zoneRuntimeState() { return runtime; }
                @Override public void playSfx(int id) { sounds.add(id); }
            };
            manager = new ObjectManager(List.of(), null, 0, null, null, null, null, services);
            holder[0] = manager;
            manager.reset(0);
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            boss = manager.createDynamicObject(() -> new MhzEndBossInstance(
                    new ObjectSpawn(0x40C0, 0x02E0, Sonic3kObjectIds.MHZ_END_BOSS, 0, 0, false, 0)));
            // Bounded component boundary: ordinary dash dispatch, with native child admission already complete.
            boss.setCustomFlag(0x100, 1);
            boss.setCustomFlag(0x38, 0xC8);
            boss.setCustomFlag(0x2E, 1000);
            boss.getState().routine = 0x0E;
            boss.getState().hitCount = 2;
            boss.getState().xVel = 0x100;
            proxy = manager.createDynamicObject(() -> new MhzEndBossHitProxyChild(boss));
        }

        void advance(int count) {
            for (int i = 0; i < count; i++) {
                boss.update(++clock, null);
                proxy.update(clock, null);
            }
        }
    }
}
