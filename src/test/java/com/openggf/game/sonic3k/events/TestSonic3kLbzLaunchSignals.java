package com.openggf.game.sonic3k.events;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.camera.Camera;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.LevelConstants;
import com.openggf.level.Pattern;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(SingletonResetExtension.class)
class TestSonic3kLbzLaunchSignals {

    @BeforeEach
    void setUp() {
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        SessionManager.clear();
    }

    @Test
    void launchSignalWrappersDelegateWithoutReusingEventsFg5ReloadFlag() throws Exception {
        Sonic3kLBZEvents events = new Sonic3kLBZEvents();
        events.setEventsFg5(true);
        LbzZoneRuntimeState state = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        GameServices.zoneRuntimeRegistry().install(state);

        events.requestLaunchStart();
        events.requestPadCollapseStart();
        events.requestFinalFall();
        events.requestLbz2RideAnimatedTiles();
        state.setLaunchActive(true);
        state.setLaunchYDelta(-5);
        events.registerLaunchRiderAnchor(0x3456);

        assertTrue(eventsFg5(events), "LBZ1 -> LBZ2 reload flag must stay independent");
        assertTrue(events.isLaunchActive());
        assertEquals(-5, events.getLaunchYDelta());
        assertTrue(events.consumeLaunchStartRequested());
        assertFalse(events.consumeLaunchStartRequested());
        assertTrue(events.consumePadCollapseStartRequested());
        assertFalse(events.consumePadCollapseStartRequested());
        assertTrue(events.consumeFinalFallRequested());
        assertFalse(events.consumeFinalFallRequested());
        assertTrue(events.consumeLbz2RideAnimatedTilesRequested());
        assertFalse(events.consumeLbz2RideAnimatedTilesRequested());
        assertEquals(0x3456, state.getLaunchRiderAnchorId().orElseThrow());
        assertTrue(eventsFg5(events), "consuming launch signals must not consume eventsFg5");
    }

    @Test
    void act2LaunchStartInitializesCameraWaterShakeAndMotionState() {
        Sonic3kLBZEvents events = new Sonic3kLBZEvents();
        LbzZoneRuntimeState state = installAct2State();
        Camera camera = GameServices.camera();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x4480, (short) 0x0700);
        camera.setFocusedSprite(player);
        camera.setX((short) 0x4300);
        camera.setY((short) 0x0660);

        events.requestLaunchStart();
        events.update(1, 1);

        assertTrue(state.isLaunchActive());
        assertTrue(state.isDeathEggRumble());
        assertEquals(0x4390, Short.toUnsignedInt(camera.getMaxX()));
        assertEquals(0x0668, Short.toUnsignedInt(camera.getMaxY()));
        assertEquals(0x0668, Short.toUnsignedInt(camera.getMaxYTarget()));
        assertEquals(0x3B, state.getPreLaunchDelay(),
                "the launch start frame consumes one pre-launch delay tick after initialization");
        assertEquals(0x1E00, state.getFgLaunchSpeed());
        assertEquals(0x6200, state.getBgLaunchSpeed());
        assertEquals(0, state.getLaunchYDelta(), "pre-launch delay should publish no rider delta");
    }

    @Test
    void launchMotionPublishesYDeltaAndWaterTargetAfterPrelaunchDelay() {
        Sonic3kLBZEvents events = new Sonic3kLBZEvents();
        LbzZoneRuntimeState state = installAct2State();
        Camera camera = GameServices.camera();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x4430, (short) 0x0700);
        camera.setFocusedSprite(player);
        camera.setX((short) 0x4390);
        camera.setY((short) 0x0668);

        events.requestLaunchStart();
        events.update(1, 1);
        state.setPreLaunchDelay(0);
        events.update(1, 2);

        assertEquals(0x1E00, state.getFgAccum());
        assertEquals(0x6200, state.getBgAccum());
        assertEquals(0, state.getLaunchYDelta(),
                "first subpixel launch accumulation remains below one whole pixel");

        boolean sawRiderDelta = false;
        for (int frame = 3; frame < 16; frame++) {
            events.update(1, frame);
            sawRiderDelta |= state.getLaunchYDelta() > 0;
        }

        assertTrue(sawRiderDelta,
                "combined foreground/background high-word delta is the rider/cameo coupling value");
        assertTrue(state.getWaterTargetY() > 0x0668,
                "launch motion drains water by moving the target downward");
    }

    @Test
    void padCollapseAndFinalFallSignalsDriveLaunchPhases() {
        Sonic3kLBZEvents events = new Sonic3kLBZEvents();
        LbzZoneRuntimeState state = installAct2State();
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(new TestablePlayableSprite("sonic", (short) 0x4430, (short) 0x0700));
        camera.setX((short) 0x4390);
        camera.setY((short) 0x0668);
        events.requestLaunchStart();
        events.update(1, 1);
        state.setPreLaunchDelay(0);

        events.requestPadCollapseStart();
        events.update(1, 2);

        assertTrue(state.isPadCollapseActive());
        assertTrue(state.getFgLaunchSpeed() < 0x1E00,
                "pad collapse starts the falling deceleration path");
        assertTrue(state.isLaunchActive(),
                "event-owned launch runtime should remain active through pad collapse");

        events.requestFinalFall();
        int beforeY = Short.toUnsignedInt(camera.getY());
        events.update(1, 3);

        assertTrue(state.isFinalFallActive());
        assertTrue(state.isLaunchActive(),
                "event-owned final fall should not clear launchActive; ship release owns no runtime shutdown");
        assertEquals(beforeY - 2, Short.toUnsignedInt(camera.getY()),
                "final fall scrolls the camera upward two pixels per frame");
    }

    @Test
    void padCollapseWaitsForNegativeBgSpeedBeforeDetachAndTerrainClear() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .build();
        Sonic3kLBZEvents events = new Sonic3kLBZEvents();
        LbzZoneRuntimeState state = S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        Level level = GameServices.level().getCurrentLevel();
        Camera camera = fixture.camera();
        camera.setFocusedSprite(new TestablePlayableSprite("sonic", (short) 0x4430, (short) 0x0700));
        camera.setX((short) 0x4390);
        camera.setY((short) 0x0668);
        seedLaunchPadBlocks(level, 0x51);

        state.setLaunchActive(true);
        state.setPreLaunchDelay(0);
        state.setFgLaunchSpeed(0);
        state.setBgLaunchSpeed(0x0200);
        state.setWaterTargetY(0x0668);

        events.requestPadCollapseStart();
        for (int frame = 4; frame <= 8; frame += 4) {
            events.update(1, frame);
        }

        assertTrue(state.isPadCollapseActive());
        assertEquals(0, state.getDetachScroll(),
                "detach scroll must not tick while pad collapse is still waiting for negative BG speed");
        assertFalse(state.isWaterDisabled(),
                "water should stay enabled until the collapse enters the detach phase");
        assertLaunchPadBlocks(level, 0x51,
                "terrain must not clear before bgLaunchSpeed goes negative");

        int frame = 9;
        while (state.getBgLaunchSpeed() >= 0) {
            events.update(1, frame++);
        }

        assertTrue(state.isWaterDisabled(),
                "negative bgLaunchSpeed starts the detach phase and clears the water flag");
        assertEquals(0, state.getDetachScroll(),
                "the transition frame arms detach but does not consume the every-four-frame scroll tick");
        assertLaunchPadBlocks(level, 0x51,
                "terrain remains intact on the frame detach is armed");

        int detachTicks = 0;
        while (state.isPadCollapseActive()) {
            events.update(1, frame++);
            if (((frame - 1) & 3) == 0) {
                detachTicks++;
            }
            assertTrue(detachTicks <= 0x28,
                    "pad collapse should finish after exactly $28 every-four-frame detach ticks");
        }

        assertEquals(0x28, detachTicks);
        assertEquals(0, state.getDetachScroll());
        assertTrue(state.isLaunchActive(),
                "event-owned collapse completion should leave launchActive set for final-fall/transition consumers");
        assertLaunchPadBlocks(level, 0,
                "terrain clear must happen only when the detach scroll reaches $28");
    }

    @Test
    void deathEggRumbleClearsWhenScreenShakeStops() {
        Sonic3kLBZEvents events = new Sonic3kLBZEvents();
        LbzZoneRuntimeState state = installAct2State();
        state.setLaunchActive(true);
        state.setDeathEggRumble(true);
        state.setPreLaunchDelay(0);
        state.setFgLaunchSpeed(0);
        state.setBgLaunchSpeed(0);
        GameServices.gameState().setScreenShakeActive(false);

        events.update(1, 1);

        assertFalse(state.isDeathEggRumble(),
                "Death Egg rumble latch should clear as soon as the screen-shake flag is off");
    }

    @Test
    void deathEggTerrainSwapThresholdQueuesOneShotWithoutEventsFg5() throws Exception {
        Sonic3kLBZEvents events = new Sonic3kLBZEvents();
        LbzZoneRuntimeState state = installAct2State();
        events.setEventsFg5(true);
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3BC0);
        camera.setY((short) 0x0500);

        events.update(1, 1);
        events.update(1, 2);

        assertTrue(state.isDeathEggTerrainSwapQueued());
        assertFalse(state.isDeathEggTerrainSwapApplied(),
                "the applied latch is reserved for a completed level-backed mutation");
        assertTrue(eventsFg5(events), "terrain-swap routing must not repurpose the act-transition flag");
    }

    @Test
    void deathEggTerrainSwapAppliesRomBackedBlocksChunksAndArtOnce() throws IOException {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 1)
                .build();
        Sonic3kLBZEvents events = new Sonic3kLBZEvents();
        LbzZoneRuntimeState state = S3kRuntimeStates.currentLbz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        Level level = GameServices.level().getCurrentLevel();
        int[] originalChunkZero = level.getChunk(0).saveState();

        ResourceLoader loader = new ResourceLoader(GameServices.rom().getRom());
        byte[] expected16x16 = loader.loadSingle(
                LoadOp.kosinskiBase(Sonic3kConstants.LBZ2_16X16_DEATH_EGG_KOS_ADDR));
        byte[] expected128x128 = loader.loadSingle(
                LoadOp.kosinskiBase(Sonic3kConstants.LBZ2_128X128_DEATH_EGG_KOS_ADDR));
        byte[] expected8x8 = loader.loadSingle(
                LoadOp.kosinskiMBase(Sonic3kConstants.LBZ2_8X8_DEATH_EGG_KOSM_ADDR));
        byte[] expectedDeathEgg2 = loader.loadSingle(
                LoadOp.kosinskiMBase(Sonic3kConstants.ART_KOSM_LBZ2_DEATH_EGG_2_8X8_ADDR));

        fixture.camera().setX((short) 0x3BC0);
        fixture.camera().setY((short) 0x0500);
        events.update(1, 1);
        events.update(1, 2);

        assertTrue(state.isDeathEggTerrainSwapQueued());
        assertTrue(state.isDeathEggTerrainSwapApplied());
        assertArrayEquals(expectedChunkState(expected16x16, 0, originalChunkZero),
                level.getChunk(0).saveState(),
                "LBZ2 Death Egg swap must restore 16x16 chunk descriptors from the ROM Kosinski block table");
        assertArrayEquals(expectedBlockState(expected128x128, 0),
                level.getBlock(0).saveState(),
                "LBZ2 Death Egg swap must restore 128x128 block descriptors from the ROM Kosinski chunk table");
        assertArrayEquals(expectedPattern(expected8x8, 0),
                snapshot(level.getPattern(0)),
                "LBZ2 Death Egg terrain art must replace VRAM tile $000 from the ROM KosM stream");
        assertArrayEquals(expectedPattern(expectedDeathEgg2, 0),
                snapshot(level.getPattern(Sonic3kConstants.ART_TILE_LBZ2_DEATH_EGG_2)),
                "Death Egg 2 art must land at tile $05A0 for launch pillars/explosion terrain");
    }

    private static LbzZoneRuntimeState installAct2State() {
        LbzZoneRuntimeState state = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        GameServices.zoneRuntimeRegistry().install(state);
        return state;
    }

    private static void seedLaunchPadBlocks(Level level, int blockIndex) {
        LevelMutationSurface surface = LevelMutationSurface.forLevel(level);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                surface.setBlockInMap(0, 0x87 + col, 0x0B + row, blockIndex);
            }
        }
    }

    private static void assertLaunchPadBlocks(Level level, int expectedBlock, String message) {
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                assertEquals((byte) expectedBlock,
                        level.getMap().getValue(0, 0x87 + col, 0x0B + row),
                        message + " at pad cell (" + col + "," + row + ")");
            }
        }
    }

    private static boolean eventsFg5(Sonic3kLBZEvents events) throws Exception {
        Field field = Sonic3kLBZEvents.class.getDeclaredField("eventsFg5");
        field.setAccessible(true);
        return field.getBoolean(events);
    }

    private static int[] expectedChunkState(byte[] bytes, int chunkIndex, int[] originalChunkState) {
        int srcOffset = chunkIndex * Chunk.CHUNK_SIZE_IN_ROM;
        int[] state = new int[Chunk.PATTERNS_PER_CHUNK + 2];
        for (int pattern = 0; pattern < Chunk.PATTERNS_PER_CHUNK; pattern++) {
            state[pattern] = readWord(bytes, srcOffset + pattern * 2);
        }
        state[Chunk.PATTERNS_PER_CHUNK] = originalChunkState[Chunk.PATTERNS_PER_CHUNK];
        state[Chunk.PATTERNS_PER_CHUNK + 1] = originalChunkState[Chunk.PATTERNS_PER_CHUNK + 1];
        return state;
    }

    private static int[] expectedBlockState(byte[] bytes, int blockIndex) {
        int srcOffset = blockIndex * LevelConstants.BLOCK_SIZE_IN_ROM;
        int chunksPerBlock = LevelConstants.BLOCK_SIZE_IN_ROM / 2;
        int[] state = new int[chunksPerBlock];
        for (int chunk = 0; chunk < chunksPerBlock; chunk++) {
            state[chunk] = readWord(bytes, srcOffset + chunk * 2);
        }
        return state;
    }

    private static byte[] expectedPattern(byte[] art, int tileOffset) {
        Pattern expected = new Pattern();
        int src = tileOffset * Pattern.PATTERN_SIZE_IN_ROM;
        expected.fromSegaFormat(Arrays.copyOfRange(art, src, src + Pattern.PATTERN_SIZE_IN_ROM));
        return snapshot(expected);
    }

    private static byte[] snapshot(Pattern pattern) {
        byte[] pixels = new byte[Pattern.PATTERN_SIZE_IN_MEM];
        pattern.copyInto(pixels, 0);
        return pixels;
    }

    private static int readWord(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
