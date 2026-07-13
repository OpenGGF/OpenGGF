package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class TestLbzResidualCompatibility {

    @Test
    void loweringGrappleCapturesMainAndThreeSidekicksAndReleasesOmittedExtension() {
        Roster roster = roster(0x100, 0xE0);
        LbzLoweringGrappleObjectInstance grapple = new LbzLoweringGrappleObjectInstance(
                new ObjectSpawn(0x100, 0x50, Sonic3kObjectIds.LBZ_LOWERING_GRAPPLE, 0x1A, 0, false, 0));
        grapple.setServices(roster.services);
        grapple.update(0, roster.main);
        for (TestablePlayableSprite player : roster.all()) assertTrue(player.isObjectControlled());

        roster.services.sidekicks = List.of(roster.first, roster.third);
        grapple.update(1, roster.main);
        assertFalse(roster.second.isObjectControlled());
        assertTrue(roster.first.isObjectControlled());
        assertTrue(roster.third.isObjectControlled());
    }

    @Test
    void rollingDrumCarriesMainAndThreeSidekicksAndReleasesOmittedRider() {
        Roster roster = roster(0x1800, 0x05AD);
        LbzRollingDrumInstance drum = new LbzRollingDrumInstance(
                new ObjectSpawn(0x1800, 0x0600, Sonic3kObjectIds.LBZ_ROLLING_DRUM, 0x40, 0, false, 0));
        drum.setServices(roster.services);
        drum.update(0, roster.main);
        for (TestablePlayableSprite player : roster.all()) assertTrue(player.isOnObject());

        roster.services.sidekicks = List.of(roster.first, roster.third);
        drum.update(1, roster.main);
        assertFalse(roster.second.isOnObject());
        assertTrue(roster.third.isOnObject());
    }

    @Test
    void explodingTriggerAttributesExtensionTouchToThatIdentity() {
        Roster roster = roster(0x1800, 0x0600);
        LbzExplodingTriggerInstance trigger = new LbzExplodingTriggerInstance(
                new ObjectSpawn(0x1800, 0x0600, 0x13, 5, 0, false, 0));
        trigger.setServices(roster.services);
        roster.third.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        roster.third.setXSpeed((short) 0x200);
        trigger.onTouchResponse(roster.third,
                new TouchResponseResult(6, 16, 16, TouchCategory.SPECIAL), 0);
        roster.services.sidekicks = List.of(roster.third, roster.first, roster.second);

        trigger.update(0, roster.main);

        assertTrue(trigger.isExplodingForTest());
        assertEquals((short) -0x200, roster.third.getXSpeed());
        assertEquals(0, roster.main.getXSpeed());
    }

    @Test
    void compactRewindRelinksAllExtensionStateToReplacementPlayers() throws Exception {
        Roster old = roster(0x1800, 0x05AD);
        LbzLoweringGrappleObjectInstance grapple = new LbzLoweringGrappleObjectInstance(
                new ObjectSpawn(0x1800, 0x051D, Sonic3kObjectIds.LBZ_LOWERING_GRAPPLE, 0x1A, 0, false, 0));
        grapple.setServices(old.services); grapple.update(0, old.main);
        LbzRollingDrumInstance drum = new LbzRollingDrumInstance(
                new ObjectSpawn(0x1800, 0x0600, Sonic3kObjectIds.LBZ_ROLLING_DRUM, 0x40, 0, false, 0));
        drum.setServices(old.services); drum.update(0, old.main);
        LbzExplodingTriggerInstance trigger = new LbzExplodingTriggerInstance(
                new ObjectSpawn(0x1800, 0x0600, 0x13, 5, 0, false, 0));
        trigger.setServices(old.services); trigger.onTouchResponse(old.third,
                new TouchResponseResult(6, 16, 16, TouchCategory.SPECIAL), 0);
        setNestedState(grapple, "extensionStates", old.second, "grabbed", false);
        setNestedState(grapple, "extensionStates", old.second, "cooldown", 37);
        setNestedState(grapple, "extensionStates", old.third, "grabbed", true);
        setNestedState(grapple, "extensionStates", old.third, "cooldown", 12);
        setNestedState(drum, "extensionStates", old.second, "riding", false);
        setNestedState(drum, "extensionStates", old.second, "angle", 0x93);
        setNestedState(drum, "extensionStates", old.third, "riding", true);
        setNestedState(drum, "extensionStates", old.third, "angle", 0xA5);
        RewindCaptureContext oldContext = context(old);
        var grappleBlob = CompactFieldCapturer.capture(grapple, oldContext);
        var drumBlob = CompactFieldCapturer.capture(drum, oldContext);
        var triggerBlob = CompactFieldCapturer.capture(trigger, oldContext);

        Roster replacement = roster(0x1800, 0x05AD);
        LbzLoweringGrappleObjectInstance restoredGrapple = new LbzLoweringGrappleObjectInstance(
                new ObjectSpawn(0x1800, 0x051D, Sonic3kObjectIds.LBZ_LOWERING_GRAPPLE, 0x1A, 0, false, 0));
        LbzRollingDrumInstance restoredDrum = new LbzRollingDrumInstance(
                new ObjectSpawn(0x1800, 0x0600, Sonic3kObjectIds.LBZ_ROLLING_DRUM, 0x40, 0, false, 0));
        LbzExplodingTriggerInstance restoredTrigger = new LbzExplodingTriggerInstance(
                new ObjectSpawn(0x1800, 0x0600, 0x13, 5, 0, false, 0));
        restoredGrapple.setServices(replacement.services);
        restoredDrum.setServices(replacement.services);
        restoredTrigger.setServices(replacement.services);
        CompactFieldCapturer.restore(restoredGrapple, grappleBlob, context(replacement));
        CompactFieldCapturer.restore(restoredDrum, drumBlob, context(replacement));
        CompactFieldCapturer.restore(restoredTrigger, triggerBlob, context(replacement));

        assertReplacementKeys(restoredGrapple, "extensionStates", old, replacement);
        assertReplacementKeys(restoredDrum, "extensionStates", old, replacement);
        assertSame(replacement.main, fieldValue(restoredGrapple, "player1Owner"));
        assertSame(replacement.first, fieldValue(restoredGrapple, "player2Owner"));
        assertSame(replacement.main, fieldValue(restoredDrum, "player1Owner"));
        assertSame(replacement.first, fieldValue(restoredDrum, "player2Owner"));
        assertNestedState(restoredGrapple, "extensionStates", replacement.second, "grabbed", false);
        assertNestedState(restoredGrapple, "extensionStates", replacement.second, "cooldown", 37);
        assertNestedState(restoredGrapple, "extensionStates", replacement.third, "grabbed", true);
        assertNestedState(restoredGrapple, "extensionStates", replacement.third, "cooldown", 12);
        assertNestedState(restoredDrum, "extensionStates", replacement.second, "riding", false);
        assertNestedState(restoredDrum, "extensionStates", replacement.second, "angle", 0x93);
        assertNestedState(restoredDrum, "extensionStates", replacement.third, "riding", true);
        assertNestedState(restoredDrum, "extensionStates", replacement.third, "angle", 0xA5);
        Map<?, ?> touchers = identityMap(restoredTrigger, "extensionTouchers");
        assertTrue(touchers.containsKey(replacement.third));
        assertFalse(touchers.containsKey(old.third));
    }

    @Test
    void supportedViewportWidthsDoNotChangeWorldSpaceInteractionSemantics() {
        for (int width : List.of(320, 352, 400, 528, 800)) {
            com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(0, 0, width, 224, 0);
            Roster roster = roster(0x100, 0xE0);
            LbzLoweringGrappleObjectInstance grapple = new LbzLoweringGrappleObjectInstance(
                    new ObjectSpawn(0x100, 0x50, Sonic3kObjectIds.LBZ_LOWERING_GRAPPLE, 0x1A, 0, false, 0));
            grapple.setServices(roster.services); grapple.update(0, roster.main);
            assertTrue(roster.main.isObjectControlled(), "capture must remain world-space at width " + width);
            assertFalse(grapple.isDestroyed());
        }
        com.openggf.level.objects.AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @Test
    void deathUnloadAndUnrelatedControlCleanupReleaseOnlyOwnedState() {
        Roster roster = roster(0x100, 0xE0);
        LbzLoweringGrappleObjectInstance grapple = new LbzLoweringGrappleObjectInstance(
                new ObjectSpawn(0x100, 0x50, Sonic3kObjectIds.LBZ_LOWERING_GRAPPLE, 0x1A, 0, false, 0));
        grapple.setServices(roster.services); grapple.update(0, roster.main);
        roster.third.setDead(true);
        grapple.update(1, roster.main);
        assertFalse(roster.third.isObjectControlled(), "dead captured extensions must be released");

        roster.second.setAnimationId(Sonic3kAnimationIds.WALK.id());
        roster.services.sidekicks = List.of(roster.first, roster.third);
        grapple.update(2, roster.main);
        assertTrue(roster.second.isObjectControlled(),
                "omission cleanup must not clear control whose presentation was replaced by another owner");
        grapple.onUnload();
        assertFalse(roster.main.isObjectControlled());
        assertFalse(roster.first.isObjectControlled());

        Roster drumRoster = roster(0x1800, 0x05AD);
        LbzRollingDrumInstance drum = new LbzRollingDrumInstance(
                new ObjectSpawn(0x1800, 0x0600, Sonic3kObjectIds.LBZ_ROLLING_DRUM, 0x40, 0, false, 0));
        drum.setServices(drumRoster.services); drum.update(0, drumRoster.main);
        drumRoster.third.setDead(true); drum.update(1, drumRoster.main);
        assertFalse(drumRoster.third.isOnObject());
        drum.onUnload();
        assertFalse(drumRoster.main.isOnObject());
        assertFalse(drumRoster.first.isOnObject());
    }

    private static void assertReplacementKeys(Object object, String field, Roster old, Roster replacement) throws Exception {
        Map<?, ?> states = identityMap(object, field);
        assertTrue(states.containsKey(replacement.second));
        assertTrue(states.containsKey(replacement.third));
        assertFalse(states.containsKey(old.second));
        assertFalse(states.containsKey(old.third));
    }

    @SuppressWarnings("unchecked")
    private static Map<PlayableEntity, ?> identityMap(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true);
        Object value = field.get(object);
        if (value instanceof Map<?, ?> map) return (Map<PlayableEntity, ?>) map;
        Map<PlayableEntity, Boolean> map = new java.util.IdentityHashMap<>();
        ((java.util.Set<PlayableEntity>) value).forEach(player -> map.put(player, true));
        return map;
    }

    private static Object fieldValue(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true);
        return field.get(object);
    }

    private static void setNestedState(Object object, String mapField, PlayableEntity player,
                                       String valueField, Object value) throws Exception {
        Object holder = identityMap(object, mapField).get(player);
        assertNotNull(holder);
        Field field = holder.getClass().getDeclaredField(valueField); field.setAccessible(true);
        field.set(holder, value);
    }

    private static void assertNestedState(Object object, String mapField, PlayableEntity player,
                                          String valueField, Object expected) throws Exception {
        Object holder = identityMap(object, mapField).get(player);
        assertNotNull(holder);
        Field field = holder.getClass().getDeclaredField(valueField); field.setAccessible(true);
        assertEquals(expected, field.get(holder));
    }

    private static RewindCaptureContext context(Roster roster) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(roster.main, PlayerRefId.mainPlayer());
        identities.registerPlayer(roster.first, PlayerRefId.sidekick(0));
        identities.registerPlayer(roster.second, PlayerRefId.sidekick(1));
        identities.registerPlayer(roster.third, PlayerRefId.sidekick(2));
        return RewindCaptureContext.withIdentityTable(identities);
    }

    private static Roster roster(int x, int y) {
        TestablePlayableSprite main = player("main", x, y);
        TestablePlayableSprite first = player("first", x - 4, y);
        TestablePlayableSprite second = player("second", x + 4, y);
        TestablePlayableSprite third = player("third", x + 8, y);
        MutableServices services = new MutableServices(main, List.of(first, second, third));
        return new Roster(main, first, second, third, services);
    }

    private static TestablePlayableSprite player(String code, int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) x, (short) y);
        player.setAir(false);
        return player;
    }

    private record Roster(TestablePlayableSprite main, TestablePlayableSprite first,
                          TestablePlayableSprite second, TestablePlayableSprite third,
                          MutableServices services) {
        List<TestablePlayableSprite> all() { return List.of(main, first, second, third); }
    }

    private static final class MutableServices extends TestObjectServices {
        private final PlayableEntity main;
        private List<? extends PlayableEntity> sidekicks;
        private MutableServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            this.main = main; this.sidekicks = sidekicks;
        }
        @Override public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }
    }
}
