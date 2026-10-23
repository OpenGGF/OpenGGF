package com.openggf.game.sonic1.events;

import com.openggf.LevelFrameResult;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.sonic1.Sonic1ZoneFeatureProvider;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.RecordingFrameDriver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native pre-object wind routine observes the VBlank that admitted this
 * gameplay iteration. These are positioned production-frame contracts, with
 * no movie-derived gameplay state or comparator values.
 *
 * <p>ROM: VBlank_Exit increments v_vblank_count before returning
 * (sonic.asm:684-687); Level_MainLoop then calls LZWaterFeatures before
 * ExecuteObjects (sonic.asm:2998-3006). Under shipped FixBugs=0,
 * LZWindTunnels reuses d0 after its sound gate (_inc/LZWaterFeatures.asm:376-403),
 * so the waterfall tick also causes a two-pixel suction write.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1LZWaterPreObjectClock {
    private static final int START_X = 0x0D00;
    private static final int START_Y = 0x0650;
    private static final int X_FRACTION = 0x4200;
    private static final int Y_FRACTION = 0x3500;
    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private RecordingFrameDriver frameDriver;
    private PlayerClockState beforePhysics;

    @BeforeAll
    static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_1, 3, 2);
    }

    @AfterAll
    static void disposeLevel() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    @BeforeEach
    void prepareTunnelPlayer() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        AbstractPlayableSprite player = fixture.sprite();
        player.setRolling(false);
        player.setAir(true);
        player.setOnObject(false);
        NativePositionOps.writeXPosResetSubpixel(player, START_X);
        NativePositionOps.writeYPosResetSubpixel(player, START_Y);
        player.setSubpixelRaw(X_FRACTION, Y_FRACTION);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        fixture.camera().updatePosition(true);
        assertInstanceOf(Sonic1ZoneFeatureProvider.class,
                GameServices.level().getZoneFeatureProvider());
        frameDriver = new RecordingFrameDriver(player);
        frameDriver.setStepWrapper((section, step) -> {
            if ("physics".equals(section)) {
                beforePhysics = currentState();
            }
            step.run();
        });
    }

    @ParameterizedTest
    @CsvSource({
            "62, 0",
            "63, 2",
            "64, 0",
            "4863, 2",
            "65535, 2"
    })
    void currentVblankControlsSuctionBeforeTheObjectDispatcher(
            int previousVblank, int expectedSuction) {
        objects().initVblaCounter(previousVblank);

        stepGameplay();

        assertEquals(previousVblank + 1, objects().getVblaCounter(),
                "one production gameplay frame publishes exactly one VBlank tick");
        assertSuction(expectedSuction);
    }

    @Test
    void aLagPredecessorAdvancesTheClockWithoutRunningTunnelSuction() {
        objects().initVblaCounter(62);
        int previousGameplayFrame = GameServices.level().getFrameCounter();
        frameDriver.setBk2Movie(new Bk2Movie(
                Path.of("synthetic-vblank-predecessor.bk2"), "", Map.of(),
                List.of(new Bk2FrameInput(0, 0, 0, false, "")), 1), 0);

        frameDriver.skipFrameFromRecording();

        assertEquals(63, objects().getVblaCounter(), "the lag interrupt still ticks");
        assertEquals(previousGameplayFrame, GameServices.level().getFrameCounter(),
                "the lag closure must not execute a gameplay iteration");
        assertEquals(START_X, fixture.sprite().getCentreX());
        assertEquals(START_Y, fixture.sprite().getCentreY(),
                "the lag interrupt must not execute LZWindTunnels");

        stepGameplay();

        assertEquals(64, objects().getVblaCounter());
        assertSuction(2);
    }

    @Test
    void restoreReplaysTheSamePreObjectSuctionAcrossByteRollover() {
        objects().initVblaCounter(0x12FF);
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();

        stepGameplay();
        assertSuction(2);
        PlayerClockState expected = currentState();
        stepGameplay();

        registry.restore(before);

        assertEquals(0x12FF, objects().getVblaCounter(), "restore reinstates the clock owner");
        assertEquals(START_X, fixture.sprite().getCentreX());
        assertEquals(START_Y, fixture.sprite().getCentreY());
        assertEquals(Y_FRACTION, fixture.sprite().getYSubpixelRaw());
        stepGameplay();
        assertSuction(2);
        assertEquals(expected, currentState(),
                "restore plus forward execution preserves the gate and native word write");
    }

    private void assertSuction(int expectedSuction) {
        Sonic1ZoneFeatureProvider features = (Sonic1ZoneFeatureProvider)
                GameServices.level().getZoneFeatureProvider();
        assertTrue(features.isWaterTunnelActive(), "the real zone provider must own transport");
        assertNotNull(beforePhysics, "the complete production frame must reach player physics");
        assertEquals(START_X + 4, beforePhysics.x(),
                "the native tunnel word write precedes the player's velocity integration");
        assertEquals(START_Y + expectedSuction, beforePhysics.y(),
                "the sound-tick d0 clobber controls the native two-pixel suction");
        assertEquals(Y_FRACTION, beforePhysics.yFraction(),
                "the ROM's add.w suction preserves the subpixel word");
    }

    private void stepGameplay() {
        beforePhysics = null;
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                frameDriver.stepFrame(false, false, false, false, false));
    }

    private ObjectManager objects() {
        return GameServices.level().getObjectManager();
    }

    private PlayerClockState currentState() {
        AbstractPlayableSprite player = fixture.sprite();
        return new PlayerClockState(objects().getVblaCounter(),
                player.getCentreX(), player.getCentreY(),
                player.getXSubpixelRaw(), player.getYSubpixelRaw(),
                player.getXSpeed(), player.getYSpeed(),
                player.getAir(), player.getAnimationId(), player.getMappingFrame());
    }

    private record PlayerClockState(int vblank, int x, int y, int xFraction,
                                   int yFraction, int xSpeed, int ySpeed,
                                   boolean airborne, int animation, int mapping) { }
}
