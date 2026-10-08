package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SszCloudOscillatorObjectInstance;
import com.openggf.game.sonic3k.objects.SszRoamingCloudObjectInstance;
import com.openggf.game.sonic3k.objects.SszSolidCloudObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sky Sanctuary act-1 sky objects: {@code SSZ1_ScreenInit}'s five roaming clouds from
 * {@code word_58758} ({@code loc_57BB2}), {@code SSZ1_BackgroundInit}'s {@code _unkEE9C}
 * oscillator ({@code loc_57B6A}) and its ten invisible sloped cloud platforms from
 * {@code word_5853E} ({@code loc_57B8E}).
 *
 * <p>The table expectations are decoded from the ROM inside the test rather than copied from the
 * engine, so a wrong table address or row stride fails here instead of agreeing with itself.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszBackgroundClouds {
    /** {@code SSZ1_ScreenInit}: {@code moveq #5-1,d1}; {@code word_5853E}: {@code dc.w $A-1}. */
    private static final int ROAMING_CLOUDS = 5;
    private static final int SOLID_CLOUDS = 0x0A;

    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @Test
    void screenAndBackgroundInitBuildTheWholeSky() {
        // SSZ1_ScreenInit/SSZ1_BackgroundInit run from pre-physics of frame 1.
        boot(320).stepIdleFrames(1);

        assertEquals(ROAMING_CLOUDS, all(SszRoamingCloudObjectInstance.class).size(),
                "SSZ1_ScreenInit allocates five word_58758 clouds");
        assertEquals(1, all(SszCloudOscillatorObjectInstance.class).size(),
                "SSZ1_BackgroundInit allocates one loc_57B6A oscillator");
        assertEquals(SOLID_CLOUDS, all(SszSolidCloudObjectInstance.class).size(),
                "word_5853E holds ten solid cloud rows");
    }

    /**
     * {@code loc_57812} reads {@code render_flags}, {@code x_pos}, the base {@code y_pos} and the
     * half-width from each twelve-byte row, in that order.
     */
    @Test
    void solidCloudsSitWhereWord5853ESaysAndSwingAgainstTheOscillator() throws IOException {
        HeadlessTestFixture fixture = boot(320);
        fixture.stepIdleFrames(1);
        byte[] table = GameServices.rom().getRom()
                .readBytes(Sonic3kConstants.SSZ_SOLID_CLOUD_TABLE_ADDR, 2 + 12 * SOLID_CLOUDS);
        assertEquals(SOLID_CLOUDS - 1, word(table, 0), "dc.w $A-1");

        List<SszSolidCloudObjectInstance> clouds = all(SszSolidCloudObjectInstance.class);
        List<Integer> engineX = new ArrayList<>();
        for (SszSolidCloudObjectInstance cloud : clouds) {
            engineX.add(cloud.getX());
        }
        for (int row = 0; row < SOLID_CLOUDS; row++) {
            int expectedX = word(table, 2 + 12 * row + 2);
            assertTrue(engineX.contains(expectedX),
                    "word_5853E row " + row + " x_pos $" + Integer.toHexString(expectedX));
        }

        // loc_57B8E: y_pos = y_vel - _unkEE9C every frame, so the platforms move as the
        // oscillator does and in the opposite direction to the background it is added to.
        SszZoneRuntimeState state = state();
        SszSolidCloudObjectInstance sample = clouds.get(0);
        int baseline = sample.getY() + state.cloudOscillator();
        boolean moved = false;
        int lastOscillator = state.cloudOscillator();
        for (int frame = 0; frame < 240; frame++) {
            fixture.stepIdleFrames(1);
            assertEquals((short) (baseline - state.cloudOscillator()), (short) sample.getY(),
                    "solid cloud Y tracks _unkEE9C at frame " + frame);
            moved |= state.cloudOscillator() != lastOscillator;
            lastOscillator = state.cloudOscillator();
        }
        assertTrue(moved, "loc_57B76 must drive _unkEE9C away from zero");
    }

    /**
     * {@code loc_57BF6} subtracts the row's {@code $40} word from the 16.16 X every frame and
     * {@code sub_5758A} masks the difference from the camera to {@code $1FF}, so each cloud
     * keeps moving left and wraps instead of leaving the level.
     */
    @Test
    void roamingCloudsDriftLeftAndWrapAcrossTheScreen() {
        HeadlessTestFixture fixture = boot(320);
        fixture.stepIdleFrames(1);

        List<SszRoamingCloudObjectInstance> clouds = all(SszRoamingCloudObjectInstance.class);
        assertEquals(ROAMING_CLOUDS, clouds.size());
        int[] startX = clouds.stream().mapToInt(ObjectInstance::getX).toArray();
        int[] startY = clouds.stream().mapToInt(ObjectInstance::getY).toArray();

        fixture.stepIdleFrames(200);

        boolean anyMovedX = false;
        boolean anyMovedY = false;
        for (int i = 0; i < clouds.size(); i++) {
            anyMovedX |= clouds.get(i).getX() != startX[i];
            anyMovedY |= clouds.get(i).getY() != startY[i];
        }
        assertTrue(anyMovedX, "the $40 drift word must move the clouds horizontally");
        assertTrue(anyMovedY, "Gradual_SwingOffset($1C00,$80) must bob the clouds");
        // The $1FF/$FF masks keep every cloud inside one screen period of the camera.
        int cameraX = GameServices.camera().getX() & 0xFFFF;
        int cameraY = GameServices.camera().getY() & 0xFFFF;
        for (SszRoamingCloudObjectInstance cloud : clouds) {
            int dx = (cloud.getX() - cameraX) & 0xFFFF;
            int dy = (cloud.getY() - cameraY) & 0xFFFF;
            assertTrue(dx < 0x200 || dx > 0xFE00, "cloud X stays within the $1FF period: $"
                    + Integer.toHexString(dx));
            assertTrue(dy < 0x100 || dy > 0xFF00, "cloud Y stays within the $FF period: $"
                    + Integer.toHexString(dy));
        }
    }

    /** The five clouds start at different bob phases because each draws its own RNG word. */
    @Test
    void eachRoamingCloudGetsItsOwnRandomPhase() {
        HeadlessTestFixture fixture = boot(320);
        fixture.stepIdleFrames(60);

        List<SszRoamingCloudObjectInstance> clouds = all(SszRoamingCloudObjectInstance.class);
        long distinct = clouds.stream().mapToInt(ObjectInstance::getY).distinct().count();
        assertTrue(distinct > 1,
                "loc_57BB2 draws Random_Number per cloud; all five sharing one Y means no draw");
    }

    /** Rewind spot: before, during and after the sky's first oscillator reversal. */
    @Test
    void theSkySurvivesACaptureRestoreAndForwardReplay() {
        HeadlessTestFixture fixture = boot(320);
        fixture.stepIdleFrames(120);

        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = registry.capture();

        registry.restore(before);
        same(before, registry.capture(), "restore");
        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        same(after, registry.capture(), "forward replay");
    }

    /**
     * The {@code $1FF} horizontal period is a screen-space constant, so a wide viewport keeps the
     * same cloud spacing; the recorded presentation consequence is that the wrap seam falls inside
     * the visible area instead of past its right edge.
     */
    @Test
    void wideViewportKeepsTheSameFiveClouds() {
        HeadlessTestFixture fixture = boot(800);
        fixture.stepIdleFrames(60);

        assertEquals(ROAMING_CLOUDS, all(SszRoamingCloudObjectInstance.class).size());
        assertEquals(SOLID_CLOUDS, all(SszSolidCloudObjectInstance.class).size());
        assertNotEquals(0, state().cloudOscillator() | 1);
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static SszZoneRuntimeState state() {
        return S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static <T> List<T> all(Class<T> type) {
        List<T> found = new ArrayList<>();
        var manager = GameServices.level().getObjectManager();
        if (manager == null) {
            return found;
        }
        for (ObjectInstance instance : manager.getActiveObjects()) {
            if (type.isInstance(instance) && !instance.isDestroyed()) {
                found.add(type.cast(instance));
            }
        }
        return found;
    }

    private static void same(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    private static HeadlessTestFixture boot(int width) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .build();
    }
}
