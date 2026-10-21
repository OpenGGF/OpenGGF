package com.openggf.game.rewind;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.badniks.MushmeanieBadnikInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Obj_Mushmeanie's shell child reads its parent only until launch: loc_8DC24 follows
 * parent3 while status bit 6 is clear, loc_8DC42 reads the attacker's x_vel through the
 * parent once, then loc_8DC62 runs MoveSprite_LightGravity / Sprite_CheckDeleteXY with no
 * parent dereference (sonic3k.asm:193744-193768). A second hit can therefore delete the
 * body while the launched shell is still flying. Registry capture must not refuse that
 * frame with a dangling {@code ShellChild#parent} (800px MHZ1 route, frames 6827-6860),
 * and restore must replay the shell's flight exactly.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMushmeanieShellRewind {
    private static SharedLevel sharedLevel;
    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_MHZ, 0);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @Test
    void launchedShellOutlivesItsDefeatedBodyAcrossCaptureAndReplay() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        AbstractPlayableSprite sprite = fixture.sprite();
        fixture.camera().updatePosition(true);
        GameServices.level().postCameraObjectPlacementSync();
        ObjectManager objectManager = GameServices.level().getObjectManager();
        objectManager.reset(fixture.camera().getX());
        fixture.stepIdleFrames(1);

        // On screen, but beyond Find_SonicTails' $80 wake range (loc_8DB3E), so the body sleeps.
        int bodyX = sprite.getCentreX() + 0x90;
        int bodyY = sprite.getCentreY() - 0x20;
        MushmeanieBadnikInstance body = objectManager.createDynamicObject(
                () -> new MushmeanieBadnikInstance(new ObjectSpawn(bodyX, bodyY, 0x8D, 0, 0, false, 0)));
        body.update(1, sprite);   // Obj_WaitOffscreen release
        body.update(2, sprite);   // loc_8DB1E: CreateChild1_Normal shell
        var shell = objectManager.activeObjectsOfType(MushmeanieBadnikInstance.ShellChild.class)
                .stream().findFirst().orElseThrow();

        // First hit: loc_8DC9C negates the attacker's x_vel and sets status bit 6; the shell
        // launches on its next pass, away from the (now rightward) attacker.
        sprite.setXSpeed((short) -0x0300);
        body.onPlayerAttack(sprite, null);
        shell.update(3, sprite);
        assertEquals(-0x0200, shell.getXVelocity(), "loc_8DC42 launch speed");
        // sub_8DC6E restores collision_flags $D7 after its $20-frame timer.
        for (int frame = 4; frame < 4 + 0x20; frame++) {
            body.update(frame, sprite);
            shell.update(frame, sprite);
        }
        // Second hit: the body is destroyed while the shell keeps flying.
        body.onPlayerAttack(sprite, null);
        assertTrue(body.isDestroyed());
        fixture.stepIdleFrames(1);
        assertTrue(!shell.isDestroyed()
                        && objectManager.getActiveObjects().contains(shell)
                        && !objectManager.getActiveObjects().contains(body),
                "the launched shell must outlive its deleted body");

        RewindRegistry registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot saved = assertDoesNotThrow(registry::capture,
                "a launched shell no longer depends on its deleted body");
        fixture.stepIdleFrames(12);
        CompositeSnapshot expected = registry.capture();
        registry.restore(saved);
        same(saved, registry.capture(), "restore");
        fixture.stepIdleFrames(12);
        same(expected, registry.capture(), "replay");
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
