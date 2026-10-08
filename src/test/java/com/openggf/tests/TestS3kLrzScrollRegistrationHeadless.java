package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.scroll.SwScrlHpz;
import com.openggf.game.sonic3k.scroll.SwScrlLrz;
import com.openggf.game.sonic3k.scroll.SwScrlS3kDefault;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which handler and which runtime state a real Lava Reef load resolves.
 *
 * <p>Before this slice zone 9 got {@code SwScrlS3kDefault} and had no runtime state, and the whole
 * of zone {@code $16} got {@code SwScrlHpz} because the provider keyed on the zone alone. Only act
 * 1 of zone {@code $16} is Hidden Palace; act 0 is the Lava Reef boss act.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzScrollRegistrationHeadless {

    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private final SonicConfigurationService config = SonicConfigurationService.getInstance();

    @BeforeEach
    void captureConfiguration() {
        for (SonicConfiguration key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) {
                saved.put(key, config.getConfigValue(key));
            }
        }
    }

    @AfterEach
    void cleanup() {
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        saved.clear();
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void bothPlayableActsResolveTheLavaReefHandlerAndRuntimeState(int act) {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, act).build();

        assertInstanceOf(SwScrlLrz.class, liveHandler(), "zone 9 act " + (act + 1) + " scroll handler");
        LrzZoneRuntimeState state = lrz();
        assertEquals(Sonic3kZoneIds.ZONE_LRZ, state.zoneIndex());
        assertEquals(act, state.actIndex());
    }

    /** {@code $1600} is the Lava Reef boss act; {@code SwScrlLrz3} is slice 9. */
    @Test
    void bossActUsesTheLavaReefRuntimeStateAndNoLongerBorrowsHiddenPalaceScroll() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ, 0).build();

        ZoneScrollHandler handler = liveHandler();
        assertFalse(handler instanceof SwScrlHpz, "$1600 must not run HPZ_BackgroundEvent");
        assertInstanceOf(com.openggf.game.sonic3k.scroll.SwScrlLrz3.class, handler);
        LrzZoneRuntimeState state = lrz();
        assertEquals(Sonic3kZoneIds.ZONE_LRZ_BOSS_HPZ, state.zoneIndex());
        assertEquals(0, state.actIndex());
    }

    /** Regression: {@code $1601} keeps the Hidden Palace handler and its own runtime state. */
    @Test
    void hiddenPalaceKeepsItsScrollHandlerAndRuntimeState() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1).build();

        assertInstanceOf(SwScrlHpz.class, liveHandler());
        assertInstanceOf(HpzZoneRuntimeState.class,
                GameServices.zoneRuntimeRegistry().current());
        assertTrue(S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).isEmpty());
    }

    /**
     * {@code ApplyDeformation} writes one word per scanline from
     * {@code Camera_Y_pos_BG_copy} and the band table, so widening the viewport must leave the
     * 224 background words alone. Only the horizontal extent the renderer samples changes.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void wideViewportProducesTheSameDeformation(int act) {
        int[] narrow = scanlineWordsAt(320, act);
        int[] wide = scanlineWordsAt(640, act);
        assertArrayEquals(narrow, wide,
                "act " + (act + 1) + " deformation must not depend on the viewport width");
        assertFalse(Arrays.stream(narrow).distinct().count() == 1,
                "the Lava Reef bands must give more than one background word on screen");
    }

    /** Rewind: the new runtime state round-trips every word it owns. */
    @Test
    void runtimeStateCaptureRestoreRoundTrips() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        LrzZoneRuntimeState state = lrz();

        state.screenShake().writeFlag(0x1E);
        state.advanceScreenShake(0x11, false);
        state.setBackgroundRoutine(0xC);
        state.setChunkEditRequest(-1);
        state.setRocksRoutine(2);
        state.publishDeformationWords(0x123, 0x45, 0x111, 0x100);
        state.storeCameraBounds(0x10, 0x2000, 0x20, 0x800);
        byte[] captured = state.captureBytes();

        state.screenShake().clear();
        state.setBackgroundRoutine(0);
        state.setChunkEditRequest(0);
        state.setRocksRoutine(0);
        state.publishDeformationWords(0, 0, 0, 0);
        state.storeCameraBounds(0, 0, 0, 0);
        state.advanceScreenShake(0, false);

        state.restoreBytes(captured);

        assertArrayEquals(captured, state.captureBytes());
        assertEquals(0xC, state.backgroundRoutine());
        assertEquals(-1, state.chunkEditRequest());
        assertEquals(2, state.rocksRoutine());
        assertEquals(0x123, state.backgroundCameraX());
        assertEquals(0x45, state.backgroundCameraY());
        assertEquals(0x111, state.animationPhaseX0());
        assertEquals(0x100, state.animationPhaseX1());
        assertEquals(0x2000, state.cameraStoredMaxX());
        assertEquals(0x800, state.cameraStoredMaxY());
    }

    /** LRZ2 loc_5705C streams Draw_TileRow; source Y=$100 must not wrap to row zero. */
    @ParameterizedTest
    @EnumSource(WidescreenAspect.class)
    void actTwoBackgroundKeepsLayoutRowsAcrossTheVdpBoundaryAndRewind(WidescreenAspect aspect) {
        int width = aspect.pixelWidth();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 1)
                .startPosition((short) 0x2438, (short) 0x629).startPositionIsCentre().build();
        for (int i = 0; i < 3; i++) fixture.stepFrame(false, false, false, false, false);
        assertEquals(width, fixture.camera().getWidth());
        assertActTwoBackgroundRows();

        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        fixture.stepFrame(false, false, false, false, false);
        assertActTwoBackgroundRows();
        byte[] expected = GameServices.level().getTilemapManager().getBackgroundTilemapData().clone();
        registry.restore(before);
        fixture.stepFrame(false, false, false, false, false);
        assertActTwoBackgroundRows();
        assertArrayEquals(expected, GameServices.level().getTilemapManager().getBackgroundTilemapData());
    }

    private static void assertActTwoBackgroundRows() {
        var level = GameServices.level();
        // This also ensures the same background cache consumed by the renderer is built.
        level.captureBackgroundVdpPlane();
        var tilemap = level.getTilemapManager();
        assertTrue(tilemap.getBackgroundTilemapHeightTiles() > 32,
                "LRZ2 streams the layout beyond the initial 256-pixel VDP window");
        assertEquals(0, tilemap.getBackgroundVdpWrapHeightTiles(),
                "the source layout must not wrap at the physical nametable boundary");
        byte[] data = tilemap.getBackgroundTilemapData();
        for (int y : new int[] {248, 256, 264, 384}) {
            for (int x = 0; x < 512; x += 8) {
                int offset = ((y / 8) * tilemap.getBackgroundTilemapWidthTiles() + x / 8) * 4;
                int g = data[offset + 1] & 0xFF;
                int descriptor = (data[offset] & 0xFF) | ((g & 7) << 8)
                        | ((g & 0x18) << 10) | ((g & 0x60) << 6) | ((g & 0x80) << 8);
                assertEquals(level.getBackgroundTileDescriptorAtWorld(x, y), descriptor,
                        "background source cell at " + x + "," + y);
            }
        }
    }

    private int[] scanlineWordsAt(int width, int act) {
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();

        HeadlessTestFixture fixture =
                HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, act).build();
        assertEquals(width, fixture.camera().getWidth() & 0xFFFF);

        ZoneScrollHandler handler = liveHandler();
        assertInstanceOf(SwScrlLrz.class, handler);
        int[] buffer = new int[com.openggf.level.scroll.M68KMath.VISIBLE_LINES];
        handler.update(buffer, 0x0800, 0x0320, 0, act);
        return buffer;
    }

    private static ZoneScrollHandler liveHandler() {
        return GameServices.parallax().getHandler(GameServices.level().getFeatureZoneId());
    }

    private static LrzZoneRuntimeState lrz() {
        return S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }
}
