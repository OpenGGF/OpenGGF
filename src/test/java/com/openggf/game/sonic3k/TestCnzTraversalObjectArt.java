package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestCnzTraversalObjectArt {

    @Test
    public void carnivalNightTraversalSheetsRegisterForVisibleObjects() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        Sonic3kObjectArtProvider provider = currentCnzObjectArtProvider();

        ObjectSpriteSheet balloon = provider.getSheet("cnz_balloon");
        ObjectSpriteSheet risingPlatform = provider.getSheet("cnz_rising_platform");

        assertNotNull(balloon);
        assertEquals(25, balloon.getFrameCount());
        SpriteMappingPiece subtype4Body = balloon.getFrame(20).pieces().get(0);
        assertEquals(0x223, subtype4Body.tileIndex(),
                "CNZ balloon subtype 4 body uses the ArtTile_CNZBalloon PLC slot");
        assertTrue(hasOpaquePixels(balloon.getPatterns()[subtype4Body.tileIndex()]),
                "CNZ balloon subtype 4 body art should be spliced from ArtKosM_CNZBalloon");
        ObjectSpriteSheet cannon = provider.getSheet("cnz_cannon");
        assertNotNull(cannon);
        assertEquals(10, cannon.getFrameCount());
        assertTrue(cannon.getPatterns().length >= 100,
                "CNZ cannon should expose the dedicated Cannon.bin art, not a placeholder sheet");
        assertEquals(11, cannon.getFrame(0).pieces().size(),
                "CNZ cannon frame 0 must compose the rotating child sprite plus the fixed base frame");
        assertEquals(0x12, cannon.getFrame(0).pieces().get(0).tileIndex(),
                "CNZ cannon frame 0 should put fixed base frame 9 first so it renders in front of the child chamber");
        assertEquals(0x242, cannon.getFrame(0).pieces().get(6).tileIndex(),
                "CNZ cannon frame 0 DPLC-loaded tile should come from Cannon.bin source $42 in the standalone source bank");
        assertEquals(0, cannon.getFrame(0).pieces().get(7).tileIndex(),
                "CNZ cannon frame 0 chamber/body tile 0 should remain CNZ misc level art, not DPLC source art");
        assertEquals(0x12, cannon.getFrame(4).pieces().get(0).tileIndex(),
                "CNZ cannon frame 4 should start with fixed base frame 9");
        assertEquals(0x200, cannon.getFrame(4).pieces().get(6).tileIndex(),
                "CNZ cannon frame 4 DPLC-loaded tile should come from Cannon.bin source $00 after the base pieces");
        assertNotNull(risingPlatform);
        assertEquals(3, risingPlatform.getFrameCount());
        ObjectSpriteSheet trapDoor = provider.getSheet("cnz_trap_door");
        ObjectSpriteSheet lightBulb = provider.getSheet("cnz_light_bulb");
        ObjectSpriteSheet hoverFan = provider.getSheet("cnz_hover_fan");
        assertNotNull(trapDoor);
        assertEquals(3, trapDoor.getFrameCount());
        assertNotNull(lightBulb, "CNZ Act 2 places Obj_CNZLightBulb and it should render from Map_CNZLightBulb");
        assertEquals(2, lightBulb.getFrameCount());
        assertNotNull(hoverFan);
        assertEquals(8, hoverFan.getFrameCount());
        ObjectSpriteSheet cylinder = provider.getSheet("cnz_cylinder");
        assertNotNull(cylinder);
        assertEquals(4, cylinder.getFrameCount());
        assertTrue(cylinder.getPatterns().length > 0,
                "CNZ cylinder should load the ROM-parsed Map - Cylinder.asm sheet");
        ObjectSpriteSheet bumper = provider.getSheet("cnz_bumper");
        assertNotNull(bumper);
        assertEquals(2, bumper.getFrameCount());
        assertNull(provider.getSheet("cnz_vacuum_tube"),
                "Vacuum Tube stays controller-only because Obj_CNZVacuumTube has inline S&K-side logic and no mappings/make_art_tile ownership");
        assertNull(provider.getSheet("cnz_spiral_tube"),
                "Spiral Tube stays controller-only because its S&K-side off_33320 controller routes still have no mappings/make_art_tile ownership");
    }

    @Test
    public void carnivalNightAct2BalloonBodyArtMatchesAct1PlcArt() {
        ObjectSpriteSheet act1Balloon = loadCnzBalloonSheet(0);
        ObjectSpriteSheet act2Balloon = loadCnzBalloonSheet(1);

        assertNotNull(act1Balloon);
        assertNotNull(act2Balloon);
        assertEquals(25, act2Balloon.getFrameCount());

        int[] tileIndices = {0x0, 0x8, 0xA, 0xB, 0xF, 0x20, 0x28, 0x40, 0x48, 0x60, 0x68,
                0x223, 0x22B, 0x22D};
        for (int tileIndex : tileIndices) {
            assertPatternEquals(act1Balloon.getPatterns()[tileIndex],
                    act2Balloon.getPatterns()[tileIndex],
                    "CNZ Act 2 balloon tile $" + Integer.toHexString(tileIndex)
                            + " should use the same CNZ balloon art as Act 1");
        }
    }

    @Test
    public void carnivalNightActTransitionKeepsHybridBalloonSheet() throws Exception {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .startPosition((short) 0x32D0, (short) 0x04AC)
                .startPositionIsCentre()
                .build();

        Sonic3kObjectArtProvider provider = currentCnzObjectArtProvider();
        ObjectSpriteSheet before = provider.getSheet(Sonic3kObjectArtKeys.CNZ_BALLOON);
        assertNotNull(before);

        GameServices.level().executeActTransition(SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                .showInLevelTitleCard(false)
                .preserveOffsetCameraPosition(true)
                .postTransitionMinX(0x01E0)
                .postTransitionMaxX(0x0260)
                .postTransitionMinY(0x02E0)
                .postTransitionMaxY(0x0500)
                .postTransitionMaxYTarget(0x0500)
                .playerOffset(-0x3000, 0x0200)
                .cameraOffset(-0x3000, 0x0200)
                .build());

        ObjectSpriteSheet after = provider.getSheet(Sonic3kObjectArtKeys.CNZ_BALLOON);
        assertNotNull(after);
        assertEquals(25, after.getFrameCount());

        int[] hybridTileIndices = {0x0, 0x8, 0xA, 0xB, 0xF, 0x20, 0x28,
                0x223, 0x22B, 0x22D};
        for (int tileIndex : hybridTileIndices) {
            assertPatternEquals(before.getPatterns()[tileIndex],
                    after.getPatterns()[tileIndex],
                    "CNZ Act 2 transition must preserve hybrid balloon tile $"
                            + Integer.toHexString(tileIndex));
        }
    }

    private static Sonic3kObjectArtProvider currentCnzObjectArtProvider() {
        return (Sonic3kObjectArtProvider) GameModuleRegistry.getCurrent().getObjectArtProvider();
    }

    private static ObjectSpriteSheet loadCnzBalloonSheet(int act) {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, act)
                .build();

        return currentCnzObjectArtProvider().getSheet(Sonic3kObjectArtKeys.CNZ_BALLOON);
    }

    private static void assertPatternEquals(Pattern expected, Pattern actual, String message) {
        assertNotNull(expected, message);
        assertNotNull(actual, message);
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                assertEquals(expected.getPixel(x, y), actual.getPixel(x, y), message);
            }
        }
    }

    private static boolean hasOpaquePixels(Pattern pattern) {
        if (pattern == null) {
            return false;
        }
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                if (pattern.getPixel(x, y) != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
