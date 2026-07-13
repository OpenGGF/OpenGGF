package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHczMinibossMultiSidekick {
    @Test
    void vortexPullProcessesAdditionalSidekicksAfterNativeP2() throws Exception {
        HczMinibossInstance boss = new HczMinibossInstance(
                new ObjectSpawn(0x1800, 0x0600, 0x99, 0, 0, false, 0));
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite nativeP2 = player("tails");
        TestablePlayableSprite extra = player("knuckles");
        boss.setServices(new QueryServices(main, List.of(nativeP2, extra)));

        pull(boss, main);

        assertTrue(main.isObjectControlled());
        assertTrue(nativeP2.isObjectControlled());
        assertTrue(extra.isObjectControlled(), "HCZ vortex must not strand third and later sidekicks");
    }

    @Test
    void omissionDeathAndUnloadReleaseOnlyVortexOwnedPlayers() throws Exception {
        HczMinibossInstance boss = new HczMinibossInstance(
                new ObjectSpawn(0x1800, 0x0600, 0x99, 0, 0, false, 0));
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite nativeP2 = player("tails");
        TestablePlayableSprite omitted = player("knuckles");
        TestablePlayableSprite dead = player("sonic");
        TestablePlayableSprite unrelated = player("tails");
        unrelated.setObjectControlled(true);
        unrelated.setForcedAnimationId(3);
        List<PlayableEntity> sidekicks = new ArrayList<>(List.of(nativeP2, omitted, dead, unrelated));
        boss.setServices(new QueryServices(main, sidekicks));

        pull(boss, main);
        sidekicks.remove(omitted);
        dead.setDead(true);
        pull(boss, main);

        assertFalse(omitted.isObjectControlled(), "roster omission must release vortex ownership");
        assertFalse(dead.isObjectControlled(), "death must release vortex ownership");
        boss.onUnload();
        assertFalse(main.isObjectControlled());
        assertFalse(nativeP2.isObjectControlled());
        assertTrue(unrelated.isObjectControlled(), "unload must preserve unrelated object control");
        assertTrue(unrelated.getForcedAnimationId() == 3);
    }

    @Test
    void rewindRelinksVortexOwnershipToReplacementPlayers() throws Exception {
        HczMinibossInstance boss = new HczMinibossInstance(
                new ObjectSpawn(0x1800, 0x0600, 0x99, 0, 0, false, 0));
        TestablePlayableSprite oldMain = player("sonic");
        TestablePlayableSprite oldP2 = player("tails");
        TestablePlayableSprite oldExtra = player("knuckles");
        boss.setServices(new QueryServices(oldMain, List.of(oldP2, oldExtra)));
        pull(boss, oldMain);
        var snapshot = boss.captureRewindState(RewindCaptureContext.withIdentityTable(
                identities(oldMain, oldP2, oldExtra)));

        TestablePlayableSprite newMain = player("sonic");
        TestablePlayableSprite newP2 = player("tails");
        TestablePlayableSprite newExtra = player("knuckles");
        boss.setServices(new QueryServices(newMain, List.of(newP2, newExtra)));
        boss.restoreRewindState(snapshot, RewindCaptureContext.withIdentityTable(
                identities(newMain, newP2, newExtra)));
        Field ownersField = HczMinibossInstance.class.getDeclaredField("vortexControlledPlayers");
        ownersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var restoredOwners = (java.util.Map<PlayableEntity, Boolean>) ownersField.get(boss);
        assertTrue(restoredOwners.containsKey(newExtra), "rewind must relink the extension owner: " + restoredOwners.keySet());
        ObjectControlState.nativeBit7FullControl().applyTo(newExtra);
        newExtra.setForcedAnimationId(Sonic3kAnimationIds.FLOAT2.id());

        boss.onUnload();

        assertFalse(newExtra.isObjectControlled());
        assertTrue(oldExtra.isObjectControlled(), "rewind cleanup must not target stale player instances");
    }

    @Test
    void freshBossRestoreUsesCompactRocketBubbleAndPlayerReferences() throws Exception {
        HczMinibossInstance source = new HczMinibossInstance(
                new ObjectSpawn(0x1800, 0x0600, 0x99, 0, 0, false, 0));
        TestablePlayableSprite oldMain = player("sonic");
        TestablePlayableSprite oldP2 = player("tails");
        TestablePlayableSprite oldExtra = player("knuckles");
        source.setServices(new QueryServices(oldMain, List.of(oldP2, oldExtra)));
        pull(source, oldMain);
        Object oldBubble = bubble(0x1800, 0x0748);
        bubbleList(source).add(oldBubble);
        setRocketField(source, 2, "phaseX", 0x57);
        setRocketField(source, 2, "routine", 8);

        RewindIdentityTable captured = identities(oldMain, oldP2, oldExtra);
        ObjectRefId bubbleId = ObjectRefId.dynamic(7, 3, 41);
        captured.registerObject((com.openggf.level.objects.ObjectInstance) oldBubble, bubbleId);
        var snapshot = source.captureRewindState(RewindCaptureContext.withIdentityTable(captured));
        assertNotNull(snapshot.compactGenericState(), "HCZ boss must not fall back to legacy generic capture");

        HczMinibossInstance restored = new HczMinibossInstance(
                new ObjectSpawn(0x1800, 0x0600, 0x99, 0, 0, false, 0));
        TestablePlayableSprite newMain = player("sonic");
        TestablePlayableSprite newP2 = player("tails");
        TestablePlayableSprite newExtra = player("knuckles");
        restored.setServices(new QueryServices(newMain, List.of(newP2, newExtra)));
        Object newBubble = bubble(0x1800, 0x0748);
        RewindIdentityTable replacement = identities(newMain, newP2, newExtra);
        replacement.registerObject((com.openggf.level.objects.ObjectInstance) newBubble, bubbleId);
        restored.restoreRewindState(snapshot, RewindCaptureContext.withIdentityTable(replacement));

        assertEquals(0x57, rocketInt(restored, 2, "phaseX"));
        assertEquals(8, rocketInt(restored, 2, "routine"));
        assertEquals(1, bubbleList(restored).size());
        assertSame(newBubble, bubbleList(restored).getFirst(), "bubble graph must relink to the recreated child");
        assertTrue(vortexOwners(restored).containsKey(newExtra), "player ownership must relink through PlayerRefId");
        assertFalse(vortexOwners(restored).containsKey(oldExtra));
    }

    private static Object bubble(int x, int y) throws Exception {
        Class<?> type = Class.forName(HczMinibossInstance.class.getName() + "$VortexBubbleChild");
        Constructor<?> constructor = type.getDeclaredConstructor(int.class, int.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(x, y, 0x16, x, y);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> bubbleList(HczMinibossInstance boss) throws Exception {
        Field field = HczMinibossInstance.class.getDeclaredField("vortexBubbles");
        field.setAccessible(true);
        return (List<Object>) field.get(boss);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<PlayableEntity, Boolean> vortexOwners(HczMinibossInstance boss) throws Exception {
        Field field = HczMinibossInstance.class.getDeclaredField("vortexControlledPlayers");
        field.setAccessible(true);
        return (java.util.Map<PlayableEntity, Boolean>) field.get(boss);
    }

    private static void setRocketField(HczMinibossInstance boss, int index, String name, int value) throws Exception {
        Object rocket = rockets(boss)[index];
        Field field = rocket.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(rocket, value);
    }

    private static int rocketInt(HczMinibossInstance boss, int index, String name) throws Exception {
        Object rocket = rockets(boss)[index];
        Field field = rocket.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(rocket);
    }

    private static Object[] rockets(HczMinibossInstance boss) throws Exception {
        Field field = HczMinibossInstance.class.getDeclaredField("rockets");
        field.setAccessible(true);
        return (Object[]) field.get(boss);
    }

    private static void pull(HczMinibossInstance boss, TestablePlayableSprite main) throws Exception {
        Method pull = HczMinibossInstance.class.getDeclaredMethod(
                "applyVortexPull", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        pull.setAccessible(true);
        pull.invoke(boss, main);
    }

    private static RewindIdentityTable identities(TestablePlayableSprite main,
                                                   TestablePlayableSprite p2,
                                                   TestablePlayableSprite extra) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerPlayer(main, PlayerRefId.mainPlayer());
        table.registerPlayer(p2, PlayerRefId.sidekick(0));
        table.registerPlayer(extra, PlayerRefId.sidekick(1));
        return table;
    }

    private static TestablePlayableSprite player(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x1800, (short) 0x0748);
        player.setCentreX((short) 0x1800);
        player.setCentreY((short) 0x0748);
        return player;
    }

    private static final class QueryServices extends TestObjectServices {
        private final ObjectPlayerQuery query;

        private QueryServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            query = new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return query;
        }
    }
}
