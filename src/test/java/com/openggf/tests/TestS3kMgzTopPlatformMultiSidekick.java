package com.openggf.tests;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.MGZTopPlatformObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.ObjectControlState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kMgzTopPlatformMultiSidekick {
    @Test
    void deadExtensionRiderIsReleasedAfterNativeP1P2Prefix() throws Exception {
        Harness harness = harness();
        harness.extension.setDead(true);

        harness.platform.update(1, harness.main);

        assertFalse(harness.platform.isPlayerGrabbed(harness.extension));
        assertFalse(harness.extension.isObjectControlled());
    }

    @Test
    void omittedExtensionRiderIsReleasedWithoutTouchingReplacementControl() throws Exception {
        Harness harness = harness();
        harness.extension.setAnimationId(5);
        harness.extension.setMgzTopPlatformCarrySolidContactObject(null);
        harness.sidekicks.remove(harness.extension);

        harness.platform.update(1, harness.main);

        assertFalse(harness.platform.isPlayerGrabbed(harness.extension));
        assertTrue(harness.extension.isObjectControlled(),
                "roster reconciliation must not clear control no longer owned by this platform");
    }

    private static Harness harness() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite nativeP2 = player("tails");
        TestablePlayableSprite extension = player("knuckles");
        List<PlayableEntity> sidekicks = new ArrayList<>(List.of(nativeP2, extension));
        ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> sidekicks);
        MGZTopPlatformObjectInstance platform = new MGZTopPlatformObjectInstance(
                new ObjectSpawn(0x2000, 0x0800, Sonic3kObjectIds.MGZ_TOP_PLATFORM, 1, 0, false, 0));
        platform.setServices(new StubObjectServices().withPlayerQuery(query));

        Object state = newPlayerState();
        writeInt(state, "routine", 4);
        writeBoolean(state, "grabbed", true);
        playerStates(platform).put(extension, state);
        ObjectControlState.nativeBit7FullControl().applyTo(extension);
        extension.setMgzTopPlatformCarrySolidContactObject(platform);
        extension.setAnimationId(0);
        return new Harness(platform, main, nativeP2, extension, sidekicks);
    }

    private static TestablePlayableSprite player(String code) {
        return new TestablePlayableSprite(code, (short) 0x2100, (short) 0x0800);
    }

    private static Object newPlayerState() throws Exception {
        Class<?> type = Class.forName(MGZTopPlatformObjectInstance.class.getName() + "$PlayerGrabState");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @SuppressWarnings("unchecked")
    private static Map<PlayableEntity, Object> playerStates(MGZTopPlatformObjectInstance platform) throws Exception {
        Field field = MGZTopPlatformObjectInstance.class.getDeclaredField("playerStates");
        field.setAccessible(true);
        return (IdentityHashMap<PlayableEntity, Object>) field.get(platform);
    }

    private static void writeInt(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void writeBoolean(Object target, String name, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private record Harness(MGZTopPlatformObjectInstance platform, TestablePlayableSprite main,
                           TestablePlayableSprite nativeP2, TestablePlayableSprite extension,
                           List<PlayableEntity> sidekicks) {}
}
