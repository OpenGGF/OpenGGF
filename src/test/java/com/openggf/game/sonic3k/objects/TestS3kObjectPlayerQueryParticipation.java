package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kObjectPlayerQueryParticipation {

    @Test
    void aizCollapsingLogBridgeUsesPlayerQueryParticipantsForSidekickStanding() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x0100, 0x0200);
        TestablePlayableSprite sidekick = player("tails_p2", 0x0100, 0x0200);
        sidekick.setCpuControlled(true);

        TestAizBridge bridge = new TestAizBridge(new ObjectSpawn(
                0x0100, 0x0200, 0x2C, 0, 0, false, 0));
        bridge.batch = new SolidCheckpointBatch(bridge, Map.of(sidekick, standingContact(2)));
        bridge.setServices(new QueryOnlyPlayerServices(main, List.of(sidekick)));

        bridge.update(0, main);

        assertTrue(readBoolean(bridge, "collapseArmedByStanding"),
                "AIZ collapsing log bridge should consume query-only sidekick standing state");
    }

    @Test
    void corkFloorUsesPlayerQueryParticipantsForSidekickRollBreak() throws Exception {
        TestablePlayableSprite main = player("sonic", 0x0100, 0x0200);
        TestablePlayableSprite sidekick = player("tails_p2", 0x0100, 0x0200);
        sidekick.setCpuControlled(true);

        TestCorkFloor floor = new TestCorkFloor(new ObjectSpawn(
                0x0100, 0x0200, 0x2A, 1, 0, false, 0));
        floor.batch = new SolidCheckpointBatch(floor, Map.of(sidekick, standingContact(2)));
        floor.setServices(new QueryOnlyPlayerServices(main, List.of(sidekick)));

        floor.update(0, main);

        assertTrue(readBoolean(floor, "broken"),
                "Cork floor should consume query-only sidekick roll-break state");
        assertTrue(sidekick.getAir(),
                "Roll-breaking query-only sidekick should receive the launch handoff");
    }

    private static TestablePlayableSprite player(String code, int x, int y) {
        return new TestablePlayableSprite(code, (short) x, (short) y);
    }

    private static PlayerSolidContactResult standingContact(int preContactAnimationId) {
        return new PlayerSolidContactResult(
                ContactKind.TOP,
                true,
                false,
                false,
                false,
                new PreContactState((short) 0, (short) 0x180, preContactAnimationId == 2,
                        preContactAnimationId),
                PostContactState.ZERO,
                0);
    }

    private static boolean readBoolean(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static final class TestAizBridge extends AizCollapsingLogBridgeObjectInstance {
        private SolidCheckpointBatch batch;

        private TestAizBridge(ObjectSpawn spawn) {
            super(spawn);
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            return batch;
        }
    }

    private static final class TestCorkFloor extends CorkFloorObjectInstance {
        private SolidCheckpointBatch batch;

        private TestCorkFloor(ObjectSpawn spawn) {
            super(spawn);
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            return batch;
        }
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final ObjectPlayerQuery playerQuery;

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.playerQuery = new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
            withCamera(new Camera());
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return playerQuery;
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }
    }
}
