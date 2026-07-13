package com.openggf.game.sonic3k.objects;

import com.openggf.game.GroundMode;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAutoSpinObjectInstance {

    @Test
    void nativeP2QuerySidekickCanTriggerAutoSpinWhenRawSidekickListIsEmpty() {
        AutoSpinObjectInstance trigger = new AutoSpinObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.AUTO_SPIN, 0, 0, false, 0));
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x0080, (short) 0x0100);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0x00F0, (short) 0x0100);
        trigger.setServices(new QueryOnlyPlayerServices(main, List.of(sidekick)));

        trigger.update(0, main);
        sidekick.setCentreX((short) 0x0100);
        trigger.update(1, main);

        assertTrue(sidekick.getPinballMode(),
                "Obj_AutoSpin has only native P1/P2 crossing flags, so P2 must come from ObjectPlayerQuery NATIVE_P1_P2");
        assertTrue(sidekick.getRolling());
        assertEquals((short) 0x0580, sidekick.getGSpeed());
    }

    @Test
    void verticalAutoSpinPreservesXPositionWhenForcedRollChangesWallModeWidth() {
        AutoSpinObjectInstance trigger = new AutoSpinObjectInstance(
                new ObjectSpawn(0x2AB8, 0x0590, Sonic3kObjectIds.AUTO_SPIN, 0x04, 0, false, 0));
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x2AB3, (short) 0x058E);
        main.setGroundMode(GroundMode.RIGHTWALL);
        main.setCentreXPreserveSubpixel((short) 0x2AB3);
        main.setCentreYPreserveSubpixel((short) 0x058E);
        trigger.setServices(new QueryOnlyPlayerServices(main, List.of()));

        trigger.update(0, main);
        main.setCentreYPreserveSubpixel((short) 0x059B);
        trigger.update(1, main);

        assertTrue(main.getRolling());
        assertEquals((short) 0x2AB3, main.getCentreX(),
                "ROM Obj_AutoSpin changes radii and y_pos only; x_pos must not move when wall-mode width shrinks");
        assertEquals((short) 0x05A0, main.getCentreY());
    }

    @Test
    void additionalSidekicksKeepIndependentCrossingStateAcrossNativeP2Reorder() {
        AutoSpinObjectInstance trigger = new AutoSpinObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.AUTO_SPIN, 0, 0, false, 0));
        TestablePlayableSprite main = new TestablePlayableSprite("sonic", (short) 0x0080, (short) 0x0100);
        TestablePlayableSprite nativeP2 = new TestablePlayableSprite("tails", (short) 0x00F0, (short) 0x0100);
        TestablePlayableSprite extra = new TestablePlayableSprite("knuckles", (short) 0x00E0, (short) 0x0100);
        java.util.ArrayList<PlayableEntity> sidekicks = new java.util.ArrayList<>(List.of(nativeP2, extra));
        trigger.setServices(new QueryOnlyPlayerServices(main, sidekicks));
        trigger.update(0, main);

        sidekicks.clear();
        sidekicks.add(extra);
        sidekicks.add(nativeP2);
        nativeP2.setCentreX((short) 0x0100);
        extra.setCentreX((short) 0x0100);
        trigger.update(1, main);

        assertTrue(nativeP2.getPinballMode(), "demoted native P2 must retain its own crossing state");
        assertTrue(extra.getPinballMode(), "promoted extension must retain its own crossing state");
    }

    @Test
    void rewindRelinksExtensionCrossingStateToReplacementPlayers() {
        AutoSpinObjectInstance trigger = new AutoSpinObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.AUTO_SPIN, 0, 0, false, 0));
        TestablePlayableSprite oldMain = new TestablePlayableSprite("sonic", (short) 0x0080, (short) 0x0100);
        TestablePlayableSprite oldP2 = new TestablePlayableSprite("tails", (short) 0x00F0, (short) 0x0100);
        TestablePlayableSprite oldExtra = new TestablePlayableSprite("knuckles", (short) 0x00E0, (short) 0x0100);
        trigger.setServices(new QueryOnlyPlayerServices(oldMain, List.of(oldP2, oldExtra)));
        trigger.update(0, oldMain);
        RewindIdentityTable captured = identities(oldMain, oldP2, oldExtra);
        var snapshot = trigger.captureRewindState(RewindCaptureContext.withIdentityTable(captured));

        TestablePlayableSprite newMain = new TestablePlayableSprite("sonic", (short) 0x0080, (short) 0x0100);
        TestablePlayableSprite newP2 = new TestablePlayableSprite("tails", (short) 0x00F0, (short) 0x0100);
        TestablePlayableSprite newExtra = new TestablePlayableSprite("knuckles", (short) 0x00E0, (short) 0x0100);
        trigger.setServices(new QueryOnlyPlayerServices(newMain, List.of(newP2, newExtra)));
        trigger.restoreRewindState(snapshot,
                RewindCaptureContext.withIdentityTable(identities(newMain, newP2, newExtra)));
        newP2.setCentreX((short) 0x0100);
        newExtra.setCentreX((short) 0x0100);
        trigger.update(1, newMain);

        assertTrue(newP2.getPinballMode());
        assertTrue(newExtra.getPinballMode());
        assertTrue(!oldP2.getPinballMode() && !oldExtra.getPinballMode());
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

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> queriedSidekicks;

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.main = main;
            this.queriedSidekicks = queriedSidekicks;
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }
    }
}
