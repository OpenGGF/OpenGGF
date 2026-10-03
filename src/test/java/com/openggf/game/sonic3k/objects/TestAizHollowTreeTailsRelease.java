package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestAizHollowTreeTailsRelease {
    @AfterEach
    void resetCharacter() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @Test
    void releaseUsesFixedNativeRadiiUntilTailsLandsAndReplaysAfterRestore() {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0)
                .withSkippedZoneIntro().build();
        var player = fixture.sprite();
        assertInstanceOf(Tails.class, player);
        assertEquals(15, player.getYRadius());
        var tree = new AizHollowTreeObjectInstance(new ObjectSpawn(0x2D00, 0x3CC, 3, 0, 0, false, 0));
        tree.setServices(new TestObjectServices().withCamera(fixture.camera()));
        ObjectControlState.none().applyTo(player);
        player.setControlLocked(false);
        player.setObjectMappingFrameControl(false);
        player.setCentreX((short) 0x2CF1);
        player.setCentreY((short) 0x456);
        player.setXSpeed((short) 0xBF6);
        player.setGSpeed((short) 0xBF6);
        player.setAir(false);
        tree.update(0, player);
        assertTrue(player.isOnObject());
        // The upper tree exit takes AIZTree_FallOff once y_pos-treeY < -$90.
        player.setCentreX((short) 0x2D7B);
        player.setCentreY((short) (0x3CC - 0x92));
        player.setYSpeed((short) 0);
        var identities = new RewindIdentityTable();
        identities.registerPlayer(player, PlayerRefId.mainPlayer());
        var context = RewindCaptureContext.withIdentityTable(identities);
        var treeState = CompactFieldCapturer.capture(tree, context);
        var playerState = player.captureRewindState();
        int firstLandingY = -1;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) {
                player.restoreRewindState(playerState);
                CompactFieldCapturer.restore(tree, treeState, context);
            }
            tree.update(1, player);
            assertTrue(player.getAir());
            assertFalse(player.isOnObject());
            assertEquals(0x33A, player.getCentreY(), "native radius writes preserve centre");
            assertEquals(9, player.getXRadius());
            assertEquals(19, player.getYRadius(), "AIZTree_FallOff writes $13 even for Tails");
            for (int frame = 0; frame < 12 && player.getAir(); frame++) {
                fixture.stepFrame(false, false, false, true, false);
            }
            assertFalse(player.getAir(), "real terrain collision must complete the release");
            assertEquals(15, player.getYRadius(), "Tails landing restores its default radius");
            if (replay == 0) firstLandingY = player.getCentreY();
            else assertEquals(firstLandingY, player.getCentreY(), "restored release lands identically");
        }
    }
}
