package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindDeterminismAuditor;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Local production-owner witness, not a cold CNZ route: the ROM-placed late CNZ1 ring is allowed to form normally,
 * then a declared inlet immediately outside its collision box enters it through an ordinary controller frame.
 * loc_6173A writes Player_prev_frame=$FF even for solo Tails, and object_control=$53 skips ordinary Animate/Load_PLC.
 * Observe those writes at the ring owner, then the diagnostic refusal while its unrecorded object owner is active.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kGiantRingDmaOwnership {
    private static final int RING_X = 0x2DC0;
    private static final int RING_Y = 0x064C;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private static S3kVIntDmaLedger ledger() {
        return S3kRuntimeArtCoordinator.from(GameServices.level().getObjectManager().getObjectServices())
                .vintDmaLedger();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sonic", "tails", "knuckles"})
    void realRingCapturePublishesNativeMappingOwnershipAndPreviousByteThenReplays(String character) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(3, 0)
                .startPosition((short) (RING_X - 0x60), (short) RING_Y).startPositionIsCentre().build();
        var camera = GameServices.camera();
        camera.setMinX((short) 0x2D20);
        camera.setMaxX((short) 0x2D20);
        camera.setMinY((short) 0x05C0);
        camera.setMaxY((short) 0x05C0);
        camera.setX((short) 0x2D20);
        camera.setY((short) 0x05C0);

        var spawn = GameServices.level().getObjectManager().getAllSpawns().stream()
                .filter(s -> s.objectId() == 0x85 && s.x() == RING_X && s.y() == RING_Y && s.subtype() == 4)
                .findFirst().orElseThrow(() -> new AssertionError("decoded ROM late CNZ1 ring"));
        Sonic3kSSEntryRingObjectInstance ring = null;
        for (int frame = 0; frame < 90; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            assertFalse(fixture.sprite().getDead(), "formation inlet must stay alive");
            ring = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(Sonic3kSSEntryRingObjectInstance.class::isInstance)
                    .map(Sonic3kSSEntryRingObjectInstance.class::cast)
                    .filter(r -> r.getSpawn() == spawn).findFirst().orElse(null);
            if (ring != null && !ring.isForming() && ring.isMainState()) break;
        }
        assertTrue(ring != null && !ring.isForming() && ring.isMainState(), "real ring formed before entry");
        var player = fixture.sprite();
        assertFalse(player.isHidden(), "no entry before the declared collision inlet");
        assertFalse(PlayableSpriteInternalAccess.objectOwnsMappingFrame(player));
        assertNotEquals(0, player.getMappingFrame(), "ordinary animation has established a nonblank frame");
        // Component inlet only: -$19 is outside SSEntry_Range's -$18 edge; an ordinary rightward step crosses it.
        NativePositionOps.writeXPosResetSubpixel(player, RING_X - 0x19);
        NativePositionOps.writeYPosResetSubpixel(player, RING_Y);
        player.setXSpeed((short) 0x200);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0x200);
        player.setAir(true);

        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        var expected = captureAndHold(fixture);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            fixture.runner().primeInputState(new Bk2FrameInput(0, 0, 0, false, ""));
            assertWorld(before, registry.capture(), "immediate restore " + cycle);
            assertEquals(expected, captureAndHold(fixture), "all DMA records replay " + cycle);
            assertWorld(after, registry.capture(), "whole-world replay " + cycle);
        }
    }

    private static List<S3kVIntDmaLedger.Record> captureAndHold(HeadlessTestFixture fixture) {
        fixture.stepFrame(false, false, false, true, false);
        var player = GameServices.sprites().getMainPlayable();
        assertTrue(player.isHidden(), "ordinary collision entered the real ring");
        assertAll("loc_6173A's writes are visible at the owning object pass",
                () -> assertTrue(PlayableSpriteInternalAccess.objectOwnsMappingFrame(player), "$53 includes bit 1"),
                () -> assertEquals(0xFF, ledger().capture().player1PreviousFrame(), "global Player_prev_frame=$FF"));
        var records = new ArrayList<S3kVIntDmaLedger.Record>();
        for (int frame = 0; frame < 6; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            assertTrue(PlayableSpriteInternalAccess.objectOwnsMappingFrame(player));
            var record = ledger().lastRecord();
            records.add(record);
            if (frame >= 1) {
                assertEquals(S3kVIntDmaLedger.FreezeStatus.PRODUCER_STATE_UNKNOWN, record.freezeStatus(),
                        "unrecorded object ownership must not claim a known conditional subtotal");
                assertEquals(-1, record.queueFreezeMclk());
                assertEquals(-1, record.conditionalFreezeMclk());
            }
        }
        return List.copyOf(records);
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        var differences = new ArrayList<String>();
        assertFalse(new RewindDeterminismAuditor(differences::add).report(0, 7, expected, actual),
                () -> context + ": " + String.join("\n", differences));
    }
}
