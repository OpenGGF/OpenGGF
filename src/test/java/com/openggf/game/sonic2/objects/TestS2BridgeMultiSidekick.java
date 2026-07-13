package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2BridgeMultiSidekick {
    @Test
    void extensionRidersRetainIndependentLogIndices() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x3D0);
        TestablePlayableSprite p2 = player("tails", 0x3E0);
        TestablePlayableSprite extra1 = player("knuckles", 0x400);
        TestablePlayableSprite extra2 = player("sonic-extra", 0x420);
        ProbeBridge bridge = new ProbeBridge(new ObjectSpawn(0x400, 0x300, 0x11, 8, 0, false, 0));
        bridge.setServices(new QueryServices(main, List.of(p2, extra1, extra2)));
        bridge.batch = standingBatch(bridge, main, p2, extra1, extra2);

        bridge.update(0, main);

        assertEquals(2, extensionLogs(bridge).size());
    }

    @Test
    void omissionPrunesExtensionAndRewindUsesReplacementIdentity() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x3D0);
        TestablePlayableSprite p2 = player("tails", 0x3E0);
        TestablePlayableSprite extension = player("knuckles", 0x420);
        QueryServices services = new QueryServices(main, List.of(p2, extension));
        ProbeBridge bridge = new ProbeBridge(new ObjectSpawn(0x400, 0x300, 0x11, 8, 0, false, 0));
        bridge.setServices(services);
        bridge.batch = standingBatch(bridge, main, p2, extension);
        bridge.update(0, main);
        var blob = CompactFieldCapturer.capture(bridge, context(main, p2, extension));

        TestablePlayableSprite replacementMain = player("replacement-main", 0x3D0);
        TestablePlayableSprite replacementP2 = player("replacement-p2", 0x3E0);
        TestablePlayableSprite replacementExtension = player("replacement-extension", 0x420);
        CompactFieldCapturer.restore(bridge, blob,
                context(replacementMain, replacementP2, replacementExtension));
        assertTrue(extensionLogs(bridge).containsKey(replacementExtension));
        assertFalse(extensionLogs(bridge).containsKey(extension));

        services.sidekicks = List.of(p2);
        bridge.batch = standingBatch(bridge, main, p2);
        bridge.update(1, main);
        assertTrue(extensionLogs(bridge).isEmpty());
    }

    private static RewindCaptureContext context(PlayableEntity main, PlayableEntity... sidekicks) {
        RewindIdentityTable ids = new RewindIdentityTable();
        ids.registerPlayer(main, PlayerRefId.mainPlayer());
        for (int i = 0; i < sidekicks.length; i++) ids.registerPlayer(sidekicks[i], PlayerRefId.sidekick(i));
        return RewindCaptureContext.withIdentityTable(ids);
    }

    private static TestablePlayableSprite player(String code, int x) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) x, (short) 0x2F8);
        return player;
    }

    private static SolidCheckpointBatch standingBatch(BridgeObjectInstance bridge, TestablePlayableSprite... players) {
        PlayerSolidContactResult result = new PlayerSolidContactResult(ContactKind.TOP, true, false, false, false,
                PreContactState.ZERO, new PostContactState((short) 0, (short) 0, false, true, false), 0);
        Map<PlayableEntity, PlayerSolidContactResult> contacts = new IdentityHashMap<>();
        for (TestablePlayableSprite player : players) contacts.put(player, result);
        return new SolidCheckpointBatch(bridge, contacts);
    }

    @SuppressWarnings("unchecked")
    private static Map<PlayableEntity, Integer> extensionLogs(BridgeObjectInstance bridge) throws Exception {
        Field field = BridgeObjectInstance.class.getDeclaredField("extensionLogIndices");
        field.setAccessible(true);
        return (Map<PlayableEntity, Integer>) field.get(bridge);
    }

    private static final class ProbeBridge extends BridgeObjectInstance {
        @RewindTransient(reason = "test-only injected checkpoint batch")
        private SolidCheckpointBatch batch = new SolidCheckpointBatch(this, Map.of());
        private ProbeBridge(ObjectSpawn spawn) { super(spawn, "Bridge"); }
        @Override protected SolidCheckpointBatch checkpointAll() { return batch; }
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
