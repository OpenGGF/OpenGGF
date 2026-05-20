package com.openggf.game.sonic3k.objects;

import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHCZHandLauncherObjectInstance {

    @Test
    void grabbedPlayerUsesFullObjectControlPolicy() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);

        updateUntilGrabbed(launcher, player);

        assertTrue(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
    }

    @Test
    void jumpEscapeClearsObjectControlPolicy() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);
        updateUntilGrabbed(launcher, player);

        player.setJumpInputPressed(true);
        launcher.update(20, player);

        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
    }

    @Test
    void automaticLaunchReleaseClearsObjectControlPolicy() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x0100);
        ProbeHandLauncher launcher = buildLauncher(player);
        updateUntilGrabbed(launcher, player);

        for (int frame = 20; frame < 180 && player.isObjectControlled(); frame++) {
            launcher.update(frame, player);
        }

        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
    }

    private static ProbeHandLauncher buildLauncher(TestablePlayableSprite player) {
        ObjectSpawn spawn = new ObjectSpawn(0x0208, 0x0100, 0x3A, 0, 0, false, 0);
        ProbeHandLauncher launcher = new ProbeHandLauncher(spawn);
        launcher.setServices(new TestObjectServices());
        launcher.setCheckpointBatch(standingBatch(launcher, player));
        return launcher;
    }

    private static void updateUntilGrabbed(ProbeHandLauncher launcher, TestablePlayableSprite player) {
        for (int frame = 0; frame < 16 && !player.isObjectControlled(); frame++) {
            launcher.update(frame, player);
        }
        assertTrue(player.isObjectControlled(), "Hand launcher should grab player from standing checkpoint");
    }

    private static SolidCheckpointBatch standingBatch(
            HCZHandLauncherObjectInstance launcher,
            TestablePlayableSprite player) {
        PlayerSolidContactResult result = new PlayerSolidContactResult(
                ContactKind.TOP,
                true,
                false,
                false,
                false,
                PreContactState.ZERO,
                new PostContactState((short) 0, (short) 0, false, true, false),
                0);
        return new SolidCheckpointBatch(launcher, Map.of(player, result));
    }

    private static final class ProbeHandLauncher extends HCZHandLauncherObjectInstance {
        private SolidCheckpointBatch checkpointBatch = new SolidCheckpointBatch(this, Map.of());

        private ProbeHandLauncher(ObjectSpawn spawn) {
            super(spawn);
        }

        private void setCheckpointBatch(SolidCheckpointBatch checkpointBatch) {
            this.checkpointBatch = checkpointBatch;
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            return checkpointBatch;
        }
    }
}
