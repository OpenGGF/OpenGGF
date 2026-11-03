package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kSharedCutsceneButton {
    @Test void everySubtypeSharesNativeObjectDataPriorityAndSetup() {
        for (var button : List.of(new S3kCutsceneButtonObjectInstance(spawn(100, 100, 0)),
                new Hcz2CutsceneButtonInstance(spawn(100, 100, 2)),
                new Cnz2CutsceneButtonInstance(spawn(100, 100, 4)),
                new Cnz2CutsceneButtonInstance(spawn(100, 100, 6)))) {
            assertEquals(104, button.getY());
            assertEquals(2, button.getPriorityBucket(), "ObjDat_CutsceneButton priority=$100");
            assertTrue(button.isHighPriority(), "ObjDat_CutsceneButton art bit 15 is set");
        }
    }

    @Test void commonRangeIncludesLowerEndpointsAndExcludesUpperEndpoints() {
        for (int[] target : new int[][]{{76, 80}, {123, 127}}) {
            var button = new Probe(100, 100);
            button.target = new AbstractS3kCutsceneButtonInstance.TargetPosition(target[0], target[1]);
            button.update(0, null);
            button.update(1, null);
            assertEquals(1, button.actions, "press action executes once, in the detection pass");
        }
        for (int[] target : new int[][]{{75, 104}, {124, 104}, {100, 79}, {100, 128}}) {
            var button = new Probe(100, 100);
            button.target = new AbstractS3kCutsceneButtonInstance.TargetPosition(target[0], target[1]);
            button.update(0, null);
            assertEquals(0, button.actions);
        }
    }

    @Test void nativeWordRangeDoesNotTurnAWrappedIntervalIntoAJavaDeltaRange() {
        var button = new Probe(0x7FF8, 100);
        button.target = new AbstractS3kCutsceneButtonInstance.TargetPosition(0x7FF8, 104);
        button.update(0, null);
        assertEquals(0, button.actions, "signed endpoints straddle $8000, so ROM comparisons reject");
        var absent = new Probe(100, 100);
        absent.update(0, null);
        assertEquals(0, absent.actions, "an absent target must not synthesize coordinates");
    }

    @Test void subtypeZeroAndTwoKeepDistinctActions() {
        Aiz2BossEndSequenceState.reset();
        com.openggf.game.sonic3k.Sonic3kLevelTriggerManager.reset();
        try {
            var services = org.mockito.Mockito.mock(com.openggf.level.objects.ObjectServices.class);
            var player = org.mockito.Mockito.mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
            var aiz = new S3kCutsceneButtonObjectInstance(spawn(100, 100, 0)) {
                @Override protected TargetPosition targetPosition() { return new TargetPosition(100, 104); }
            };
            aiz.setServices(services);
            aiz.update(0, player);
            assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
            org.mockito.Mockito.verify(services, org.mockito.Mockito.never()).playSfx(org.mockito.ArgumentMatchers.anyInt());
            org.mockito.Mockito.verify(player).setControlLocked(false);
            org.mockito.Mockito.verify(player).clearForcedInputMask();
            assertFalse(com.openggf.game.sonic3k.Sonic3kLevelTriggerManager.testBit(8, 0));
            var hcz = new Hcz2CutsceneButtonInstance(spawn(100, 100, 2)) {
                @Override protected TargetPosition targetPosition() { return new TargetPosition(100, 104); }
            };
            hcz.update(0, null);
            assertTrue(com.openggf.game.sonic3k.Sonic3kLevelTriggerManager.testBit(8, 0));
            assertTrue(com.openggf.game.sonic3k.Sonic3kLevelTriggerManager.testBit(8, 7));
        } finally {
            Aiz2BossEndSequenceState.reset();
            com.openggf.game.sonic3k.Sonic3kLevelTriggerManager.reset();
        }
    }

    private static ObjectSpawn spawn(int x, int y, int subtype) {
        return new ObjectSpawn(x, y, 0x83, subtype, 0, false, 0);
    }

    private static final class Probe extends AbstractS3kCutsceneButtonInstance {
        TargetPosition target;
        int actions;
        Probe(int x, int y) { super(spawn(x, y, 0), "probe"); }
        @Override protected TargetPosition targetPosition() { return target; }
        @Override protected void onPressed(PlayableEntity player) { actions++; }
    }
}
