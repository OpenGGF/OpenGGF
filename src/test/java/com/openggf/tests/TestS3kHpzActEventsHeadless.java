package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.HPZPaletteControlObjectInstance;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hidden Palace ($1601) HPZ_ScreenInit / HPZ_ScreenEvent / HPZ_BackgroundInit through the real level loop. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzActEventsHeadless {
    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private SonicConfigurationService config;

    @BeforeEach
    void setup() {
        config = SonicConfigurationService.getInstance();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) {
                saved.put(key, config.getConfigValue(key));
            }
        }
    }

    @AfterEach
    void cleanup() {
        config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private HeadlessTestFixture boot(String mainCharacter) {
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, mainCharacter);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HPZ, 1)
                .build();
    }

    private static HpzZoneRuntimeState hpzState() {
        return S3kRuntimeStates.currentHpz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static boolean paletteControlActive() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(HPZPaletteControlObjectInstance.class::isInstance);
    }

    @Test
    void sonicStartKeepsTheLeftLimitAndAllocatesPaletteControlOnce() {
        HeadlessTestFixture fixture = boot("sonic");
        assertTrue((fixture.sprite().getCentreY() & 0xFFFF) >= 0x480,
                "the ROM start at Y $AEC is below the $480 upper-route test");

        fixture.stepIdleFrames(90);

        assertNotEquals(0xAA0, GameServices.camera().getMinX() & 0xFFFF);
        assertTrue(hpzState().paletteControlAllocated());
        assertTrue(paletteControlActive());
        assertEquals(1, GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(HPZPaletteControlObjectInstance.class::isInstance).count());
    }

    @Test
    void knucklesStartLimitsTheCameraAndPatchesTheBackgroundLayout() {
        HeadlessTestFixture fixture = boot("knuckles");
        Level level = GameServices.level().getCurrentLevel();

        fixture.stepIdleFrames(2);

        assertEquals(0xAA0, GameServices.camera().getMaxX() & 0xFFFF);
        assertEquals(0x8E, Byte.toUnsignedInt(level.getMap().getValue(1, 3, 1)));
        assertEquals(0x8F, Byte.toUnsignedInt(level.getMap().getValue(1, 4, 1)));
    }

    @Test
    void foregroundCollapseWritesChunk61IntoRowSeven() {
        HeadlessTestFixture fixture = boot("sonic");
        Level level = GameServices.level().getCurrentLevel();
        fixture.stepIdleFrames(2);
        assertNotEquals(0x61, Byte.toUnsignedInt(level.getMap().getValue(0, 0x30, 7)),
                "the collapse chunk must not already be present");

        hpzState().requestForegroundCollapse();
        fixture.stepIdleFrames(2);

        assertEquals(0x61, Byte.toUnsignedInt(level.getMap().getValue(0, 0x30, 7)));
        assertEquals(0x61, Byte.toUnsignedInt(level.getMap().getValue(0, 0x31, 7)));
    }

    @Test
    void anPalHpzCyclesLineFourColoursOneAndTwoAwayFromTheMasterEmerald() {
        HeadlessTestFixture fixture = boot("sonic");
        Level level = GameServices.level().getCurrentLevel();

        // AnPal_PalHPZ frames 0-3 are ($E,$A); frame 4 at byte offset 16 is ($C,$8).
        boolean reachedFrameFour = false;
        for (int i = 0; i < 8 * 6 && !reachedFrameFour; i++) {
            fixture.stepIdleFrames(1);
            reachedFrameFour = colorWord(level.getPalette(3).getColor(1)) == 0x000C
                    && colorWord(level.getPalette(3).getColor(2)) == 0x0008;
        }

        assertTrue(reachedFrameFour, "AnPal_HPZ must reach its fifth frame within 40 passes");
    }

    private static int colorWord(com.openggf.level.Palette.Color color) {
        byte[] data = new byte[2];
        for (int word = 0; word <= 0x0EEE; word += 2) {
            data[0] = (byte) (word >>> 8);
            data[1] = (byte) word;
            com.openggf.level.Palette.Color candidate = new com.openggf.level.Palette.Color();
            candidate.fromSegaFormat(data, 0);
            if (candidate.r == color.r && candidate.g == color.g && candidate.b == color.b) {
                return word;
            }
        }
        return -1;
    }

    @Test
    void paletteControlSwitchesToPalHpzWhenTheCameraCrosses460() {
        HeadlessTestFixture fixture = boot("sonic");
        fixture.stepIdleFrames(60);
        var registry = GameServices.paletteOwnershipRegistry();
        assertNotEquals(S3kPaletteOwners.HPZ_PALETTE_CONTROL, registry.ownerAt(PaletteSurface.NORMAL, 1, 0),
                "no write while the camera stays left of $460");

        NativePositionOps.writeXPosPreserveSubpixel(fixture.sprite(), 0x0700);
        for (int i = 0; i < 240 && (GameServices.camera().getX() & 0xFFFF) < 0x460; i++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue((GameServices.camera().getX() & 0xFFFF) >= 0x460);
        fixture.stepIdleFrames(1);
        assertEquals(S3kPaletteOwners.HPZ_PALETTE_CONTROL, registry.ownerAt(PaletteSurface.NORMAL, 1, 0));
        assertEquals(4, paletteControl().selectionForTestPublic());
    }

    private static HPZPaletteControlObjectInstance paletteControl() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(HPZPaletteControlObjectInstance.class::isInstance)
                .map(HPZPaletteControlObjectInstance.class::cast)
                .findFirst().orElseThrow();
    }
}
