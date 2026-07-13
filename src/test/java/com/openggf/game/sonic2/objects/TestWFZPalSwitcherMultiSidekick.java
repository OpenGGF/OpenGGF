package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestWFZPalSwitcherMultiSidekick {
    @Test
    void initializesCrossingStateForEveryExtensionPlayer() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x300);
        TestablePlayableSprite p2 = player("tails", 0x300);
        TestablePlayableSprite extra1 = player("knuckles", 0x500);
        TestablePlayableSprite extra2 = player("sonic-extra", 0x500);
        WFZPalSwitcherObjectInstance switcher = new WFZPalSwitcherObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x8B, 1, 0, false, 0), "WFZPalSwitcher");
        switcher.setServices(new QueryServices(main, List.of(p2, extra1, extra2)));

        switcher.update(0, main);

        assertEquals(2, extensionState(switcher).size());
    }

    @Test
    void reorderAndRewindKeepCrossingStateWithPlayerIdentity() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x300);
        TestablePlayableSprite p2 = player("tails", 0x300);
        TestablePlayableSprite extension = player("knuckles", 0x500);
        QueryServices services = new QueryServices(main, List.of(p2, extension));
        WFZPalSwitcherObjectInstance switcher = new WFZPalSwitcherObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x8B, 1, 0, false, 0), "WFZPalSwitcher");
        switcher.setServices(services);
        switcher.update(0, main);

        services.sidekicks = List.of(extension, p2);
        switcher.update(1, main);
        var blob = CompactFieldCapturer.capture(switcher, context(main, extension, p2));
        TestablePlayableSprite replacementMain = player("replacement-main", 0x300);
        TestablePlayableSprite replacementP2 = player("replacement-p2", 0x500);
        TestablePlayableSprite replacementExtension = player("replacement-extension", 0x300);
        CompactFieldCapturer.restore(switcher, blob,
                context(replacementMain, replacementP2, replacementExtension));

        assertTrue((Boolean) field(switcher, "tailsPastTrigger"));
        assertTrue(extensionState(switcher).containsKey(replacementExtension));
        assertFalse(extensionState(switcher).containsKey(p2));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static RewindCaptureContext context(PlayableEntity main, PlayableEntity... sidekicks) {
        RewindIdentityTable ids = new RewindIdentityTable();
        ids.registerPlayer(main, PlayerRefId.mainPlayer());
        for (int i = 0; i < sidekicks.length; i++) ids.registerPlayer(sidekicks[i], PlayerRefId.sidekick(i));
        return RewindCaptureContext.withIdentityTable(ids);
    }

    private static TestablePlayableSprite player(String code, int x) {
        return new TestablePlayableSprite(code, (short) x, (short) 0x300);
    }

    @SuppressWarnings("unchecked")
    private static Map<PlayableEntity, Boolean> extensionState(WFZPalSwitcherObjectInstance switcher) throws Exception {
        Field field = WFZPalSwitcherObjectInstance.class.getDeclaredField("extraPlayerPastTrigger");
        field.setAccessible(true);
        return (Map<PlayableEntity, Boolean>) field.get(switcher);
    }

    private static final class QueryServices extends TestObjectServices {
        private final PlayableEntity main;
        private List<? extends PlayableEntity> sidekicks;
        private QueryServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks) {
            this.main = main; this.sidekicks = sidekicks;
        }
        @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> main, () -> sidekicks); }
    }
}
