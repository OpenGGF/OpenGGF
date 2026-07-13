package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2TeamExtensionRewind {

    @Test
    void movingVineRestoresExtraSidekickGrabAndReleaseDelayByPlayerIdentity() {
        TestablePlayableSprite extra = player("knuckles");
        MovingVineObjectInstance vine = new MovingVineObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic2ObjectIds.MOVING_VINE, 0, 0, false, 0),
                "MovingVine");
        identityMap(vine, "extraPlayerGrabbed").put(extra, true);
        identityMap(vine, "extraPlayerReleaseDelay").put(extra, 23);
        RewindCaptureContext context = contextForExtra(extra);

        RewindObjectStateBlob blob = CompactFieldCapturer.captureDefaultObjectSubclassScalars(vine, context);
        identityMap(vine, "extraPlayerGrabbed").clear();
        identityMap(vine, "extraPlayerReleaseDelay").clear();
        CompactFieldCapturer.restoreDefaultObjectSubclassScalars(vine, blob, context);

        assertTrue((Boolean) identityMap(vine, "extraPlayerGrabbed").get(extra));
        assertEquals(23, identityMap(vine, "extraPlayerReleaseDelay").get(extra));
    }

    @Test
    void wfzPaletteSwitcherRestoresExtraSidekickCrossingByPlayerIdentity() {
        TestablePlayableSprite extra = player("knuckles");
        WFZPalSwitcherObjectInstance switcher = new WFZPalSwitcherObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, 0x8B, 0, 0, false, 0),
                "WFZPalSwitcher");
        identityMap(switcher, "extraPlayerPastTrigger").put(extra, true);
        RewindCaptureContext context = contextForExtra(extra);

        RewindObjectStateBlob blob = CompactFieldCapturer.captureDefaultObjectSubclassScalars(switcher, context);
        identityMap(switcher, "extraPlayerPastTrigger").clear();
        CompactFieldCapturer.restoreDefaultObjectSubclassScalars(switcher, blob, context);

        assertTrue((Boolean) identityMap(switcher, "extraPlayerPastTrigger").get(extra));
    }

    private static RewindCaptureContext contextForExtra(PlayableEntity extra) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerPlayer(extra, PlayerRefId.sidekick(1));
        return RewindCaptureContext.withIdentityTable(table);
    }

    private static TestablePlayableSprite player(String code) {
        return new TestablePlayableSprite(code, (short) 0, (short) 0);
    }

    @SuppressWarnings("unchecked")
    private static IdentityHashMap<PlayableEntity, Object> identityMap(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (IdentityHashMap<PlayableEntity, Object>) field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to access " + fieldName, e);
        }
    }
}
