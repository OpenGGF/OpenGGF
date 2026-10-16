package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.difficulty.ClamerRecipe;
import com.openggf.game.sonic3k.difficulty.IczStalagtiteRecipe;
import com.openggf.game.sonic3k.difficulty.LbzAlarmRecipe;
import com.openggf.game.sonic3k.objects.badniks.Flybot767BadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Second Strike C1-east native recipes: each preset changes only the bounded
 * values its enum names, and STOCK equals the shipped behaviour.
 */
@FullReset
@ExtendWith(SingletonResetExtension.class)
class TestSecondStrikeEastNativeRecipes {
    @AfterEach void cleanup() { AbstractObjectInstance.resetCameraBoundsForTests(); }

    // ---------------------------------------------------------------- enums

    @Test void presetsAreFiniteAndStockEqualsShippedConstants() {
        assertArrayEquals(new ClamerRecipe[]{ClamerRecipe.STOCK, ClamerRecipe.CADENCE,
                ClamerRecipe.STAGGERED_CADENCE}, ClamerRecipe.values());
        assertEquals(0x60, ClamerRecipe.STOCK.autoCloseRange());      // loc_88FEC cmpi.w #$60 (96)
        assertEquals(0, ClamerRecipe.STOCK.tellHold());
        assertEquals(0x90, ClamerRecipe.CADENCE.autoCloseRange());    // 144
        assertEquals(0, ClamerRecipe.CADENCE.tellHold());
        assertEquals(0x90, ClamerRecipe.STAGGERED_CADENCE.autoCloseRange());
        assertEquals(0x20, ClamerRecipe.STAGGERED_CADENCE.tellHold()); // 32
        assertArrayEquals(new IczStalagtiteRecipe[]{IczStalagtiteRecipe.STOCK, IczStalagtiteRecipe.WARNED},
                IczStalagtiteRecipe.values());
        assertEquals(0x70, IczStalagtiteRecipe.STOCK.triggerRange());  // loc_8B1AE cmpi.w #$70
        assertEquals(0x0F, IczStalagtiteRecipe.STOCK.shakeTimerStart());
        assertArrayEquals(new LbzAlarmRecipe[]{LbzAlarmRecipe.STOCK, LbzAlarmRecipe.BOUNDED}, LbzAlarmRecipe.values());
        assertThrows(NullPointerException.class, () -> new ClamerObjectInstance(spawn(0xA3, 0, 0), null));
        assertThrows(NullPointerException.class, () -> new IczStalagtiteObjectInstance(spawn(0xB5, 0, 0), null));
        assertThrows(NullPointerException.class, () -> new LbzAlarmObjectInstance(spawn(0x22, 0, 0), null));
    }

    // ------------------------------------------------------------- stalagtite

    private static int shakeUpdates(IczStalagtiteRecipe recipe, int playerDx) {
        IczStalagtiteObjectInstance stalagtite = new IczStalagtiteObjectInstance(
                new ObjectSpawn(0x3200, 0x05C0, Sonic3kObjectIds.ICZ_STALAGTITE, 0, 0, false, 0), recipe);
        PlayableEntity player = playerCentreX(0x3200 + playerDx);
        try {
            AbstractObjectInstance.updateCameraBounds(0x3200 - 160, 0x05C0 - 112, 0x3200 + 160, 0x05C0 + 112, 0);
            stalagtite.update(0, null);
            stalagtite.refreshPostCameraRenderState();
            for (int frame = 1; frame <= 3; frame++) stalagtite.update(frame, null);
        } finally {
            AbstractObjectInstance.resetCameraBoundsForTests();
        }
        assertEquals("WAITING", stalagtite.getPhaseNameForTesting());
        stalagtite.update(10, player);
        if (!"SHAKING".equals(stalagtite.getPhaseNameForTesting())) return -1;
        int updates = 0;
        while ("SHAKING".equals(stalagtite.getPhaseNameForTesting()) && updates < 200) {
            stalagtite.update(11 + updates, player);
            updates++;
        }
        assertEquals("FALLING", stalagtite.getPhaseNameForTesting());
        return updates;
    }

    @Test void stockStalagtiteIgnoresPlayerBeyondSeventyAndShakesSixteenUpdates() {
        assertEquals(-1, shakeUpdates(IczStalagtiteRecipe.STOCK, 0x90));
        assertEquals(16, shakeUpdates(IczStalagtiteRecipe.STOCK, 0x6F));
    }

    @Test void warnedStalagtiteTriggersEarlierAndShakesFortyEightUpdates() {
        assertEquals(48, shakeUpdates(IczStalagtiteRecipe.WARNED, 0x90));
        assertEquals(-1, shakeUpdates(IczStalagtiteRecipe.WARNED, 0xA0), "range is exclusive, as stock");
        assertEquals(48, shakeUpdates(IczStalagtiteRecipe.WARNED, 0x6F));
    }

    // ----------------------------------------------------------------- clamer

    private static int tellHoldSeen(ClamerRecipe recipe, int layoutIndex, int playerDx) {
        // layoutIndex varies only to prove the native owner ignores record identity.
        ClamerObjectInstance clamer = new ClamerObjectInstance(
                new ObjectSpawn(0x0C98, 0x0470, 0xA3, 0, 1, false, 0, layoutIndex), recipe);
        clamer.setServices(new TestObjectServices());
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) (0x0C98 + playerDx));
        player.setCentreY((short) 0x0470);
        clamer.testRunProductionInitializationAfterOffscreenWait();
        clamer.update(0x1000, player);
        if (clamer.testRoutine() != 0x06) return -1;
        player.setCentreX((short) (0x0C98 + 0x200)); // out of range after the commit
        int longest = 0;
        for (int i = 0; i < 200 && clamer.testRoutine() == 0x06; i++) {
            clamer.update(0x1001 + i, player);
            longest = Math.max(longest, clamer.testAutoCloseAnimTimer());
        }
        assertEquals(0x02, clamer.testRoutine(), "auto-close completes and reopens");
        return longest;
    }

    @Test void stockClamerCommitsWithinSixtyAndNeverAddsTellHold() {
        assertEquals(-1, tellHoldSeen(ClamerRecipe.STOCK, 1, 0x70));
        assertEquals(0x2F, tellHoldSeen(ClamerRecipe.STOCK, 1, 0x40));
        assertEquals(0x2F, tellHoldSeen(ClamerRecipe.STOCK, 2, 0x40));
    }

    @Test void cadenceWidensRangeAndKeepsTheStockTellForEveryRecordIdentity() {
        assertEquals(0x2F, tellHoldSeen(ClamerRecipe.CADENCE, 2, 0x70));
        assertEquals(0x2F, tellHoldSeen(ClamerRecipe.CADENCE, 3, 0x70), "identity parity is not a preset input");
        assertEquals(-1, tellHoldSeen(ClamerRecipe.CADENCE, 2, 0x90), "range is exclusive, as stock");
    }

    @Test void staggeredCadenceHoldsTheTellLongerForEveryRecordIdentity() {
        assertEquals(0x2F + 0x20, tellHoldSeen(ClamerRecipe.STAGGERED_CADENCE, 2, 0x70));
        assertEquals(0x2F + 0x20, tellHoldSeen(ClamerRecipe.STAGGERED_CADENCE, 3, 0x70));
        assertEquals(-1, tellHoldSeen(ClamerRecipe.STAGGERED_CADENCE, 2, 0x90));
    }

    // ------------------------------------------------------------------ alarm

    private static final class RecordingServices extends StubObjectServices {
        final ObjectManager objectManager = mock(ObjectManager.class);
        final com.openggf.level.objects.ObjectQuery query = mock(com.openggf.level.objects.ObjectQuery.class);
        final List<AbstractObjectInstance> children = new ArrayList<>();
        final List<Integer> playedSfx = new ArrayList<>();
        @Override public com.openggf.level.objects.ObjectQuery objectQuery() { return query; }
        @Override public void playSfx(int id) { playedSfx.add(id); }
        RecordingServices() {
            doAnswer(call -> { children.add(call.getArgument(0)); return null; })
                    .when(objectManager).addDynamicObjectAfterCurrent(any(AbstractObjectInstance.class));
        }
        @Override public ObjectManager objectManager() { return objectManager; }
    }

    private static TouchResponseResult specialTouch() {
        return new TouchResponseResult(0x17, 0x10, 0x10, TouchCategory.SPECIAL, 0);
    }

    private static int touchAndRun(LbzAlarmObjectInstance alarm, PlayableEntity player, int frame) {
        alarm.onTouchResponse(player, specialTouch(), frame);
        int f = frame;
        do { alarm.update(f++, player); } while (alarm.alarmTimerForTesting() != 0 && f < frame + 400);
        return f;
    }

    @Test void stockAlarmSummonsEveryTouchAfterItsSirenEnds() {
        RecordingServices services = new RecordingServices();
        LbzAlarmObjectInstance alarm = new LbzAlarmObjectInstance(spawn(Sonic3kObjectIds.LBZ_ALARM, 0, 0));
        alarm.setServices(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x500);
        int f = touchAndRun(alarm, player, 0);
        assertEquals(0xD7, alarm.getCollisionFlags());
        touchAndRun(alarm, player, f);
        assertEquals(2, services.children.size(), "stock alarm is unbounded: one Flybot per touch");
        for (AbstractObjectInstance child : services.children) {
            assertFalse(child instanceof Flybot767BadnikInstance escort && escort.isBoundedEscort());
        }
    }

    @Test void boundedAlarmSummonsOneEscortThenIgnoresLatchedOverlapAndStaysInert() {
        RecordingServices services = new RecordingServices();
        LbzAlarmObjectInstance alarm = new LbzAlarmObjectInstance(
                spawn(Sonic3kObjectIds.LBZ_ALARM, 0, 0), LbzAlarmRecipe.BOUNDED);
        alarm.setServices(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x500);
        int f = touchAndRun(alarm, player, 0);
        assertEquals(1, services.children.size());
        Flybot767BadnikInstance escort = assertInstanceOf(Flybot767BadnikInstance.class, services.children.get(0));
        assertTrue(escort.isBoundedEscort());
        assertEquals(0x10C0, escort.getSpawn().x(), "native alarm offset ($C0 right, $60 above) is unchanged");
        assertEquals(0x04A0, escort.getSpawn().y());
        assertEquals(0, alarm.getCollisionFlags(), "a summoned alarm publishes no touch region");
        alarm.onTouchResponse(player, specialTouch(), f);
        for (int i = 0; i < 400; i++) alarm.update(f + i, player);
        assertEquals(1, services.children.size(), "a latched overlap cannot re-arm the spent alarm");
        assertEquals(0, alarm.getCollisionProperty());
    }

    @Test void blockedBoundedAlarmStaysSilentAndArmedThenSummonsOnceWhenClear() {
        RecordingServices services = new RecordingServices();
        var live = new Flybot767BadnikInstance(spawn(Sonic3kObjectIds.FLYBOT_767, 0, 0)).asBoundedEscort();
        org.mockito.Mockito.when(services.query.activeObjectsOfType(Flybot767BadnikInstance.class))
                .thenReturn(List.of(live));
        LbzAlarmObjectInstance alarm = new LbzAlarmObjectInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.LBZ_ALARM, 0, 0, false, 0), LbzAlarmRecipe.BOUNDED);
        alarm.setServices(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x500);
        alarm.onTouchResponse(player, specialTouch(), 0);
        alarm.update(0, player);
        assertEquals(0, alarm.alarmTimerForTesting(), "blocked: no siren timer");
        assertTrue(services.playedSfx.isEmpty(), "blocked: no alarm cue");
        assertTrue(services.children.isEmpty(), "blocked: no allocation");
        assertEquals(0, alarm.getCollisionFlags(), "blocked: no touch region is published");
        assertEquals(0, alarm.getCollisionProperty(), "the blocked latch is consumed");
        org.mockito.Mockito.when(services.query.activeObjectsOfType(Flybot767BadnikInstance.class))
                .thenReturn(List.of());
        assertEquals(0xD7, alarm.getCollisionFlags(), "re-armed as soon as the escort is gone");
        alarm.onTouchResponse(player, specialTouch(), 1);
        alarm.update(1, player);
        assertEquals(1, services.children.size());
        assertEquals(0x81, alarm.alarmTimerForTesting());
        assertEquals(0, alarm.getCollisionFlags());
        for (int i = 2; i < 400; i++) alarm.update(i, player);
        alarm.onTouchResponse(player, specialTouch(), 400);
        alarm.update(400, player);
        assertEquals(1, services.children.size(), "one attempt per allocated alarm instance");
    }

    @Test void failedAllocationIsStillTheAlarmsOnlyAttempt() {
        RecordingServices services = new RecordingServices();
        LbzAlarmObjectInstance alarm = new LbzAlarmObjectInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.LBZ_ALARM, 0, 0, false, 0), LbzAlarmRecipe.BOUNDED);
        alarm.setServices(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x500);
        alarm.onTouchResponse(player, specialTouch(), 0);
        alarm.update(0, player);
        assertEquals(1, services.children.size());
        services.children.get(0).setDestroyed(true); // lost allocation: nothing is live
        for (int i = 1; i < 400; i++) alarm.update(i, player);
        alarm.onTouchResponse(player, specialTouch(), 400);
        alarm.update(400, player);
        assertEquals(1, services.children.size(), "no retry after a failed or lost allocation");
        assertEquals(0, alarm.getCollisionFlags());
    }

    @Test void delayedBoundedAlarmIsBlockedAtTriggerBeforeItsSiren() {
        RecordingServices services = new RecordingServices();
        var live = new Flybot767BadnikInstance(spawn(Sonic3kObjectIds.FLYBOT_767, 0, 0)).asBoundedEscort();
        org.mockito.Mockito.when(services.query.activeObjectsOfType(Flybot767BadnikInstance.class))
                .thenReturn(List.of(live));
        LbzAlarmObjectInstance alarm = new LbzAlarmObjectInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.LBZ_ALARM, 0x03, 0, false, 0), LbzAlarmRecipe.BOUNDED);
        alarm.setServices(services);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x500);
        alarm.onTouchResponse(player, specialTouch(), 0);
        for (int i = 0; i < 100; i++) alarm.update(i, player);
        assertEquals(0, alarm.alarmTimerForTesting());
        assertTrue(services.playedSfx.isEmpty());
        assertTrue(services.children.isEmpty());
    }

    // -------------------------------------------------------------- escort

    private static Object get(Object o, String field) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(field); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException next) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(field);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void set(Object o, String field, Object value) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(field); f.setAccessible(true);
                if (f.getType().isEnum()) value = Enum.valueOf((Class) f.getType(), (String) value);
                f.set(o, value); return;
            } catch (NoSuchFieldException next) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(field);
    }

    private static Flybot767BadnikInstance liveEscort(boolean escort) throws Exception {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
        Flybot767BadnikInstance flybot = new Flybot767BadnikInstance(
                new ObjectSpawn(0x0200, 0x0120, Sonic3kObjectIds.FLYBOT_767, 0, 0, false, -1));
        if (escort) flybot.asBoundedEscort();
        flybot.setServices(new TestObjectServices());
        set(flybot, "waitingForOnscreen", false);
        return flybot;
    }

    @Test void escortLeavesAfterItsFirstReboundInsteadOfReturningToTheChaseLoop() throws Exception {
        Flybot767BadnikInstance flybot = liveEscort(true);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0140);
        set(flybot, "state", "REBOUND");
        set(flybot, "originY", 0x0120);
        set(flybot, "yVelocity", -0x200);
        flybot.update(0, player);
        assertEquals("EXIT", get(flybot, "state").toString());
        assertFalse(flybot.isDestroyed());
        for (int i = 1; i < 400 && !flybot.isDestroyed(); i++) flybot.update(i, player);
        assertTrue(flybot.isDestroyed(), "escort retires silently once it has risen off screen");
    }

    @Test void stockFlybotStillWaitsAndReturnsToTheChaseLoop() throws Exception {
        Flybot767BadnikInstance flybot = liveEscort(false);
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0140);
        set(flybot, "state", "REBOUND");
        set(flybot, "originY", 0x0120);
        set(flybot, "yVelocity", -0x200);
        flybot.update(0, player);
        assertEquals("WAIT_RETURN", get(flybot, "state").toString());
    }

    @Test void ordinaryObjectControlDoesNotRetireTheEscort() throws Exception {
        Flybot767BadnikInstance flybot = liveEscort(true);
        // Far from the native attack trigger so the chase state holds.
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0400, (short) 0x0140);
        set(flybot, "state", "CHASE");
        player.setObjectControlled(true); // spring / tube / grabber style control, not an LBZ script
        for (int i = 0; i < 5; i++) flybot.update(i, player);
        assertEquals("CHASE", get(flybot, "state").toString());
    }

    @Test void nativeLbzScriptFlagsRetireTheEscort() throws Exception {
        for (int flag = 0; flag < 3; flag++) {
            Flybot767BadnikInstance flybot = liveEscort(true);
            var registry = new com.openggf.game.zone.ZoneRuntimeRegistry();
            var lbz = new com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState(
                    0, com.openggf.game.PlayerCharacter.SONIC_AND_TAILS);
            registry.install(lbz);
            flybot.setServices(new TestObjectServices() {
                @Override public com.openggf.game.zone.ZoneRuntimeRegistry zoneRuntimeRegistry() { return registry; }
            });
            TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0400, (short) 0x0140);
            set(flybot, "state", "CHASE");
            flybot.update(0, player);
            assertEquals("CHASE", get(flybot, "state").toString(), "no script yet, flag " + flag);
            switch (flag) {
                case 0 -> lbz.setRobotnikIntroActive(true);
                case 1 -> lbz.setLaunchActive(true);
                default -> lbz.setLbz1KnucklesCutsceneControlLocked(true);
            }
            flybot.update(1, player);
            assertEquals("EXIT", get(flybot, "state").toString(), "script flag " + flag + " retires the escort");
        }
    }

    // ---------------------------------------------------------------- facades

    @Test void zoneFactoriesBuildNativeOwnersForExactNativeIdsOnly() {
        assertInstanceOf(ClamerObjectInstance.class, com.openggf.mods.objects.CarnivalNightFactory
                .clamer(spawn(0xA3, 0x0100, 0x0100), ClamerRecipe.STAGGERED_CADENCE));
        assertInstanceOf(IczStalagtiteObjectInstance.class, com.openggf.mods.objects.IceCapFactory
                .stalactite(spawn(0xB5, 0x0100, 0x0100), IczStalagtiteRecipe.WARNED));
        assertInstanceOf(LbzAlarmObjectInstance.class, com.openggf.mods.objects.LaunchBaseFactory
                .alarm(spawn(0x22, 0x0100, 0x0100), LbzAlarmRecipe.BOUNDED));
        assertThrows(IllegalArgumentException.class, () -> com.openggf.mods.objects.CarnivalNightFactory
                .clamer(spawn(0xA5, 0, 0), ClamerRecipe.CADENCE));
        assertThrows(IllegalArgumentException.class, () -> com.openggf.mods.objects.IceCapFactory
                .stalactite(spawn(0xA3, 0, 0), IczStalagtiteRecipe.WARNED));
        assertThrows(IllegalArgumentException.class, () -> com.openggf.mods.objects.LaunchBaseFactory
                .alarm(spawn(0xB5, 0, 0), LbzAlarmRecipe.BOUNDED));
        assertThrows(NullPointerException.class, () -> com.openggf.mods.objects.CarnivalNightFactory
                .clamer(spawn(0xA3, 0, 0), null));
    }

    // ---------------------------------------------------------------- helpers

    private static ObjectSpawn spawn(int id, int x, int y) {
        return new ObjectSpawn(x, y, id, 0, 0, false, y);
    }

    private static PlayableEntity playerCentreX(int x) {
        PlayableEntity player = mock(PlayableEntity.class);
        org.mockito.Mockito.when(player.getCentreX()).thenReturn((short) x);
        return player;
    }
}
