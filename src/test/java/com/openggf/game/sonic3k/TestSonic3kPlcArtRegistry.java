package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.resources.CompressionType;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class TestSonic3kPlcArtRegistry {
    private static final int MAX_SANE_FRAMES = 256;
    private static final int MAX_SANE_FRAME_PIECES = 80;
    private static final int MAX_SANE_FRAME_TILES = 512;
    private static final int MAX_SANE_FRAME_SPAN_PIXELS = 1024;
    private static final int MAX_SANE_ABS_PIECE_OFFSET_PIXELS = 2048;
    private static final int STANDALONE_S3_HALF_START = 0x200000;
    private static final int FIRST_SKL_ZONE = 0x07;
    private static final Set<String> REVIEWED_S3_SIDE_ART_REFERENCES = Set.of(
            // sonic3k.asm DPLCPtr_CutsceneKnux uses the shared cutscene sheet;
            // RomOffsetFinder resolves these labels only from the Sonic 3 half.
            reviewedS3SideReference(Sonic3kObjectArtKeys.CUTSCENE_KNUCKLES, "artAddr", 0x382DC6),
            reviewedS3SideReference(Sonic3kObjectArtKeys.CUTSCENE_KNUCKLES, "mappingAddr", 0x364016),
            reviewedS3SideReference(Sonic3kObjectArtKeys.CUTSCENE_KNUCKLES, "dplcAddr", 0x36430E)
    );

    @Test
    public void sharedLevelArtEntriesIncludeSpikesAndSprings() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x00, 0);

        assertNotNull(plan);
        assertTrue(plan.levelArt().size() >= 7, "Expected at least 7 shared level art entries");

        boolean hasSpikes = plan.levelArt().stream()
                .anyMatch(e -> Sonic3kObjectArtKeys.SPIKES.equals(e.key()));
        assertTrue(hasSpikes, "Expected spikes entry in level art");
    }

    @Test
    public void aiz1PlanIncludesBadnikAndLevelArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x00, 0);
        assertEquals(9, plan.standaloneArt().size());

        // Verify Bloominator
        Sonic3kPlcArtRegistry.StandaloneArtEntry bloominator = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.BLOOMINATOR))
                .findFirst().orElse(null);
        assertNotNull(bloominator);

        // Verify Rhinobot has DPLC
        Sonic3kPlcArtRegistry.StandaloneArtEntry rhinobot = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.RHINOBOT))
                .findFirst().orElse(null);
        assertNotNull(rhinobot);
        assertTrue(rhinobot.dplcAddr() > 0);

        // 7 shared + 3 shared AIZ + 4 act-1 specific = 14
        assertTrue(plan.levelArt().size() > 7);
    }

    @Test
    public void aiz2PlanHasAct2SpecificEntries() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x00, 1);
        assertEquals(9, plan.standaloneArt().size());
        boolean hasAiz2Rock = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.AIZ2_ROCK));
        assertTrue(hasAiz2Rock);
        // Act 2 should NOT have Act 1 tree
        boolean hasAiz1Tree = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.AIZ1_TREE));
        assertFalse(hasAiz1Tree);
    }

    @Test
    public void hcz1PlanHasBlastoidNotJawz() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x01, 0);
        assertNotNull(plan);
        assertEquals(20, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_LARGE_FAN)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_BLASTOID)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_JAWZ)));
    }

    @Test
    public void hcz2PlanHasJawzNotBlastoid() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x01, 1);
        assertNotNull(plan);
        assertEquals(20, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_LARGE_FAN)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_JAWZ)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.HCZ_BLASTOID)));
    }

    @Test
    public void mgz1PlanHasMinibossAndDiagonalSpringOverride() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x02, 0);
        assertNotNull(plan);
        assertEquals(10, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_SPIKER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MINIBOSS)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MINIBOSS_SPIRE)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MINIBOSS_DEBRIS)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MANTIS)));

        // Diagonal spring should use MGZ/MHZ art tile
        Sonic3kPlcArtRegistry.LevelArtEntry diag = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SPRING_DIAGONAL))
                .findFirst().orElse(null);
        assertNotNull(diag);
        assertEquals(0x0478, diag.artTileBase());
    }

    @Test
    public void mgz2PlanHasMantisNotMiniboss() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x02, 1);
        assertNotNull(plan);
        assertEquals(12, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MANTIS)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_ENDBOSS)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ROBOTNIK_SHIP)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_ENDBOSS_DEBRIS)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MGZ_MINIBOSS)));
    }

    @Test
    public void mgz2PlanLoadsEndBossScaledCueFromUncompressedArtScaledBlob() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x02, 1);

        Sonic3kPlcArtRegistry.StandaloneArtEntry scaled = plan.standaloneArt().stream()
                .filter(e -> "mgz_endboss_scaled".equals(e.key()))
                .findFirst().orElse(null);

        assertNotNull(scaled, "loc_6CFF4 uses ArtScaled_MGZEndBoss for the air-attack zoom cue");
        assertEquals(0x36C572, scaled.artAddr());
        assertEquals(CompressionType.UNCOMPRESSED, scaled.compression());
        assertEquals(0x1000, scaled.artSize(), "ArtScaled_MGZEndBoss is 128 raw 4bpp tiles");
        assertEquals(1, scaled.palette(), "ObjDat3_6D7B4 uses make_art_tile(ArtTile_MGZEndBossScaled,1,0)");
    }

    @Test
    public void cnzPlanHasBadniksAndKeepsBalloonOutOfStandaloneArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x03, 0);
        assertNotNull(plan);
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_SPARKLE)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_BATBOT)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_CLAMER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_CLAMER_SHOT)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CNZ_BALLOON)),
                "CNZ balloon uses buildCnzBalloonSheet level-art splicing, not a standalone full mapping sheet");
    }

    @Test
    public void fbzPlanOverridesSpikes() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x04, 0);
        assertNotNull(plan);
        assertEquals(8, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.FBZ_BLASTER)));

        // Spikes should use FBZ art tile
        Sonic3kPlcArtRegistry.LevelArtEntry spikes = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SPIKES))
                .findFirst().orElse(null);
        assertNotNull(spikes);
        assertEquals(0x0200, spikes.artTileBase());
    }

    @Test
    public void iczPlanHasCollapsingBridgeAndBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x05, 0);
        assertNotNull(plan);
        assertEquals(8, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ICZ_SNOWDUST)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ICZ_STAR_POINTER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ICZ_PENGUINATOR)));

        // Penguinator should have DPLC
        Sonic3kPlcArtRegistry.StandaloneArtEntry penguin = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.ICZ_PENGUINATOR))
                .findFirst().orElse(null);
        assertNotNull(penguin);
        assertTrue(penguin.dplcAddr() > 0);

        // Collapsing bridge level-art should still be present
        boolean hasBridge = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_ICZ));
        assertTrue(hasBridge);
    }

    @Test
    public void lbzPlanHasFourBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x06, 0);
        assertNotNull(plan);
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SNALE_BLASTER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ORBINAUT)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.RIBOT)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CORKEY)));
    }

    @Test
    public void lbzPlanIncludesRideGrappleLevelArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x06, 0);

        Sonic3kPlcArtRegistry.LevelArtEntry grapple = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.LBZ_RIDE_GRAPPLE))
                .findFirst().orElse(null);

        assertNotNull(grapple, "Obj_LBZRideGrapple uses resident LBZ misc art and must be in the LBZ level-art plan");
        assertEquals(Sonic3kConstants.MAP_LBZ_RIDE_GRAPPLE_ADDR, grapple.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_LBZ_MISC + 0x70, grapple.artTileBase());
        assertEquals(1, grapple.palette());
    }

    @Test
    public void lbzPlanIncludesFlameThrowerLevelArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x06, 0);

        Sonic3kPlcArtRegistry.LevelArtEntry flameThrower = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.LBZ_FLAME_THROWER))
                .findFirst().orElse(null);

        assertNotNull(flameThrower, "Obj_LBZFlameThrower uses resident LBZ misc art and must be in the LBZ level-art plan");
        assertEquals(Sonic3kConstants.MAP_LBZ_FLAME_THROWER_ADDR, flameThrower.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_LBZ_MISC - 0x17, flameThrower.artTileBase());
        assertEquals(2, flameThrower.palette());
    }

    @Test
    public void lbzPlanIncludesTubeElevatorLevelArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x06, 0);

        Sonic3kPlcArtRegistry.LevelArtEntry elevator = plan.levelArt().stream()
                .filter(e -> e.key().equals("lbz_tube_elevator"))
                .findFirst().orElse(null);

        assertNotNull(elevator, "Obj_LBZTubeElevator uses resident LBZ tube transport art");
        assertEquals(Sonic3kConstants.MAP_LBZ_TUBE_ELEVATOR_ADDR, elevator.mappingAddr());
        assertEquals(Sonic3kConstants.ARTTILE_LBZ_TUBE_TRANS, elevator.artTileBase());
        assertEquals(1, elevator.palette());
    }

    @Test
    public void lbzTubeElevatorMappingsMatchRomShape() throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 3K ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 3K ROM");
            RomByteReader reader = RomByteReader.fromRom(rom);
            var frames = S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_LBZ_TUBE_ELEVATOR_ADDR);

            assertEquals(7, frames.size(), "Map_LBZTubeElevator has six shell frames plus the side child frame");
            assertEquals(10, frames.get(0).pieces().size());
            assertEquals(10, frames.get(1).pieces().size());
            assertEquals(7, frames.get(2).pieces().size());
            assertEquals(10, frames.get(3).pieces().size());
            assertEquals(7, frames.get(4).pieces().size());
            assertEquals(10, frames.get(5).pieces().size());
            assertEquals(3, frames.get(6).pieces().size());
        }
    }

    @Test
    public void lbzRideGrappleMappingsMatchRomShape() throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 3K ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 3K ROM");
            RomByteReader reader = RomByteReader.fromRom(rom);
            var frames = S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_LBZ_RIDE_GRAPPLE_ADDR);

            assertEquals(3, frames.size(), "Map_LBZRideGrapple has top, chain-link, and handle frames");
            assertRideGrapplePiece(frames.get(0), 2, 2, 0);
            assertRideGrapplePiece(frames.get(1), 1, 1, 4);
            assertRideGrapplePiece(frames.get(2), 2, 2, 5);
        }
    }

    @Test
    public void lbzFlameThrowerMappingsMatchRomShape() throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 3K ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 3K ROM");
            RomByteReader reader = RomByteReader.fromRom(rom);
            var frames = S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_LBZ_FLAME_THROWER_ADDR);

            assertEquals(9, frames.size(), "Map_LBZFlameThrower has the nozzle plus eight flame frames");
            assertLbzFlameThrowerFrame(frames.get(0), new int[][]{{4, 4, 0x3B}});
            assertLbzFlameThrowerFrame(frames.get(1), new int[][]{{2, 1, 0x4B}, {2, 1, 0x4D}});
            assertLbzFlameThrowerFrame(frames.get(2), new int[][]{{2, 1, 0x4D}, {2, 1, 0x4F}, {2, 1, 0x51}, {2, 2, 0x57}});
            assertLbzFlameThrowerFrame(frames.get(3), new int[][]{{2, 1, 0x4B}, {2, 1, 0x4D}, {2, 1, 0x4F}, {2, 1, 0x51}, {2, 2, 0x53}, {2, 2, 0x57}});
            assertLbzFlameThrowerFrame(frames.get(4), new int[][]{{2, 1, 0x4B}, {2, 1, 0x4D}, {2, 1, 0x4F}, {2, 1, 0x51}, {2, 2, 0x53}, {2, 2, 0x57}});
            assertLbzFlameThrowerFrame(frames.get(5), new int[][]{{2, 1, 0x4D}, {2, 1, 0x4F}, {2, 1, 0x51}, {2, 2, 0x53}, {2, 2, 0x53}, {2, 2, 0x5B}});
            assertLbzFlameThrowerFrame(frames.get(6), new int[][]{{2, 1, 0x4D}, {2, 1, 0x4F}, {2, 1, 0x51}, {2, 2, 0x53}, {2, 2, 0x53}, {2, 2, 0x5B}});
            assertLbzFlameThrowerFrame(frames.get(7), new int[][]{{2, 1, 0x4B}, {2, 1, 0x4D}, {2, 1, 0x4F}, {2, 2, 0x5B}});
            assertLbzFlameThrowerFrame(frames.get(8), new int[][]{{2, 1, 0x4B}, {2, 1, 0x5F}});
        }
    }

    @Test
    public void surfaceSplashArtMatchesRomShapeAndStaysWithinSplashSheet() throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 3K ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 3K ROM");
            RomByteReader reader = RomByteReader.fromRom(rom);

            // ArtUnc_SplashDrown is 124 tiles; shares Map_DashDust + DPLC_DashSplashDrown.
            var tiles = S3kSpriteDataLoader.loadArtTiles(reader,
                    Sonic3kConstants.ART_UNC_SPLASH_DROWN_ADDR,
                    Sonic3kConstants.ART_UNC_SPLASH_DROWN_SIZE);
            var mappings = S3kSpriteDataLoader.loadMappingFrames(reader,
                    Sonic3kConstants.MAP_DASH_DUST_ADDR);
            var dplcs = S3kSpriteDataLoader.loadDplcFrames(reader,
                    Sonic3kConstants.DPLC_DASH_DUST_ADDR);

            assertEquals(124, tiles.length, "ArtUnc_SplashDrown is 124 tiles (3968 bytes).");
            assertEquals(30, mappings.size(), "Map_DashDust has 30 frames ($00-$1D).");
            assertEquals(30, dplcs.size(), "DPLC_DashSplashDrown has 30 frames ($00-$1D).");

            // Anim 4 (Ani_DashSplashDrown byte_18DE8) plays mapping frames $16-$1D.
            // ROM source tiles: $16->0(x12), $17->12, $18->28, $19->44, $1A->60,
            // $1B->76, $1C->92, $1D->108(x16); 108+16 == 124 exactly.
            int[] expectedStart = {0, 12, 28, 44, 60, 76, 92, 108};
            for (int i = 0; i < expectedStart.length; i++) {
                int frame = 0x16 + i;
                var requests = dplcs.get(frame).requests();
                assertEquals(1, requests.size(),
                        "Splash frame 0x" + Integer.toHexString(frame) + " loads one tile run.");
                var req = requests.get(0);
                assertEquals(expectedStart[i], req.startTile(),
                        "Splash frame 0x" + Integer.toHexString(frame) + " start tile.");
                assertTrue(req.startTile() + req.count() <= tiles.length,
                        "Splash frame 0x" + Integer.toHexString(frame)
                                + " must stay within the 124-tile splash sheet (no art corruption).");
                // Mapping for these frames references bank-relative tile 0.
                for (SpriteMappingPiece piece : mappings.get(frame).pieces()) {
                    assertEquals(0, piece.tileIndex(),
                            "Splash mapping frame 0x" + Integer.toHexString(frame)
                                    + " uses bank-relative tile indices.");
                }
            }
        }
    }

    @Test
    public void lbzMovingPlatformMappingsMatchRomShape() throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 3K ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 3K ROM");
            RomByteReader reader = RomByteReader.fromRom(rom);
            var frames = S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_LBZ_MOVING_PLATFORM_ADDR);

            assertEquals(3, frames.size(), "Map_LBZMovingPlatform has two 32px-wide frames and one low-profile frame");
            assertLbzMovingPlatformPiece(frames.get(0), 0, 4, 2, 0);
            assertLbzMovingPlatformPiece(frames.get(0), 1, 4, 2, 0);
            assertLbzMovingPlatformPiece(frames.get(1), 0, 4, 2, 8);
            assertLbzMovingPlatformPiece(frames.get(1), 1, 4, 2, 0);
            assertLbzMovingPlatformPiece(frames.get(2), 0, 4, 1, 0);
            assertLbzMovingPlatformPiece(frames.get(2), 1, 4, 1, 0);
        }
    }

    @Test
    public void mhz1PlanHasFourBadniksNoArrow() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x07, 0);
        assertNotNull(plan);
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.MADMOLE)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CLUCKOID)));
        assertFalse(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CLUCKOID_ARROW)));
    }

    @Test
    public void mhz1PlanIncludesAllRenderedObjectArtKeys() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x07, 0);

        assertStandaloneArtKeys(plan,
                Sonic3kObjectArtKeys.MADMOLE,
                Sonic3kObjectArtKeys.MUSHMEANIE,
                Sonic3kObjectArtKeys.DRAGONFLY,
                Sonic3kObjectArtKeys.BUTTERDROID,
                Sonic3kObjectArtKeys.CLUCKOID,
                Sonic3kObjectArtKeys.MHZ_MINIBOSS,
                Sonic3kObjectArtKeys.MHZ_MINIBOSS_LOG,
                Sonic3kObjectArtKeys.KNUX_INTRO_LAYING,
                Sonic3kObjectArtKeys.CUTSCENE_KNUCKLES,
                Sonic3kObjectArtKeys.MHZ1_CUTSCENE_KNUCKLES_PEER);

        assertLevelArtKeys(plan,
                Sonic3kObjectArtKeys.MHZ_PULLEY_LIFT,
                Sonic3kObjectArtKeys.MHZ_CURLED_VINE,
                Sonic3kObjectArtKeys.MHZ_STICKY_VINE,
                Sonic3kObjectArtKeys.MHZ_SWING_BAR_HORIZONTAL,
                Sonic3kObjectArtKeys.MHZ_SWING_BAR_VERTICAL,
                Sonic3kObjectArtKeys.MHZ_SWING_VINE,
                Sonic3kObjectArtKeys.MHZ_MUSHROOM_CAP_LIGHT,
                Sonic3kObjectArtKeys.MHZ_MUSHROOM_CAP_DARK,
                Sonic3kObjectArtKeys.MHZ_MUSHROOM_PLATFORM,
                Sonic3kObjectArtKeys.MHZ_MUSHROOM_PARACHUTE,
                Sonic3kObjectArtKeys.MHZ_MUSHROOM_CATAPULT_CAPS,
                Sonic3kObjectArtKeys.MHZ_MUSHROOM_CATAPULT_CENTER,
                Sonic3kObjectArtKeys.BUTTON,
                Sonic3kObjectArtKeys.MHZ_MINIBOSS_TREE,
                Sonic3kObjectArtKeys.MHZ1_CUTSCENE_KNUCKLES_DOOR,
                Sonic3kObjectArtKeys.MHZ_BIG_LEAVES,
                Sonic3kObjectArtKeys.MHZ_POLLEN_SPRING,
                Sonic3kObjectArtKeys.MHZ_POLLEN_SEASONAL);
    }

    @Test
    public void mhz1CutsceneStandaloneArtMappingsStayInsideDecompressedBanks() throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 3K ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 3K ROM");
            Sonic3kObjectArt art = new Sonic3kObjectArt(null, RomByteReader.fromRom(rom));

            for (String key : new String[]{
                    Sonic3kObjectArtKeys.CUTSCENE_KNUCKLES,
                    Sonic3kObjectArtKeys.MHZ1_CUTSCENE_KNUCKLES_PEER
            }) {
                Sonic3kPlcArtRegistry.StandaloneArtEntry entry = Sonic3kPlcArtRegistry.getPlan(0x07, 0)
                        .standaloneArt().stream()
                        .filter(e -> key.equals(e.key()))
                        .findFirst()
                        .orElseThrow();

                ObjectSpriteSheet sheet = art.loadStandaloneSheet(rom, entry);

                assertNotNull(sheet);
                assertTrue(sheet.getPatterns().length > 0);
                assertMappingTilesWithinSheet(sheet,
                        key + " mappings must parse from the table boundary and fit the decompressed bank");
            }
        }
    }

    @Test
    public void mhzPollenMappingsMatchRomShape() throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 3K ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 3K ROM");
            RomByteReader reader = RomByteReader.fromRom(rom);
            var frames = S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_MHZ_POLLEN_ADDR);

            assertEquals(4, frames.size(), "MHZ pollen mapping table should have four animation frames");
            for (int i = 0; i < frames.size(); i++) {
                assertEquals(1, frames.get(i).pieces().size(), "MHZ pollen frame " + i + " should have one piece");
                SpriteMappingPiece piece = frames.get(i).pieces().get(0);
                assertEquals(1, piece.widthTiles(), "MHZ pollen frame " + i + " width");
                assertEquals(1, piece.heightTiles(), "MHZ pollen frame " + i + " height");
                assertEquals(0, piece.tileIndex(), "MHZ pollen frame " + i + " tile");
            }
        }
    }

    @Test
    public void s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits() throws IOException {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null && romFile.exists(), "Sonic 3K ROM not available");

        try (Rom rom = new Rom()) {
            assumeTrue(rom.open(romFile.getPath()), "Failed to open Sonic 3K ROM");
            RomByteReader reader = RomByteReader.fromRom(rom);
            Sonic3kObjectArt art = new Sonic3kObjectArt(null, reader);

            for (int zone = 0x00; zone <= 0x0D; zone++) {
                for (int act = 0; act <= 1; act++) {
                    Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(zone, act);
                    String context = "zone 0x" + Integer.toHexString(zone) + " act " + act;

                    for (Sonic3kPlcArtRegistry.LevelArtEntry entry : plan.levelArt()) {
                        if (entry.mappingAddr() <= 0) {
                            continue;
                        }
                        List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(
                                reader, entry.mappingAddr(), entry.mappingFormat());
                        if (entry.frameFilter() != null) {
                            List<SpriteMappingFrame> filtered = new ArrayList<>();
                            for (int frameIndex : entry.frameFilter()) {
                                filtered.add(frames.get(frameIndex));
                            }
                            frames = filtered;
                        }
                        assertSaneMappingFrames(context + " level art " + entry.key(), frames, -1);
                    }

                    for (Sonic3kPlcArtRegistry.StandaloneArtEntry entry : plan.standaloneArt()) {
                        assertSaneStandaloneEntry(context, reader, rom, art, entry);
                    }
                }
            }
        }
    }

    @Test
    public void sklObjectArtRegistryDoesNotUseStandaloneSonic3Addresses() {
        List<String> violations = new ArrayList<>();
        for (int zone = FIRST_SKL_ZONE; zone <= 0x0D; zone++) {
            for (int act = 0; act <= 1; act++) {
                Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(zone, act);
                String context = "zone 0x" + Integer.toHexString(zone) + " act " + act;

                for (Sonic3kPlcArtRegistry.StandaloneArtEntry entry : plan.standaloneArt()) {
                    collectSAndKRuntimeAddressViolation(violations,
                            context + " standalone art " + entry.key() + " artAddr",
                            entry.key(), "artAddr", entry.artAddr());
                    collectSAndKRuntimeAddressViolation(violations,
                            context + " standalone art " + entry.key() + " mappingAddr",
                            entry.key(), "mappingAddr", entry.mappingAddr());
                    collectSAndKRuntimeAddressViolation(violations,
                            context + " standalone art " + entry.key() + " dplcAddr",
                            entry.key(), "dplcAddr", entry.dplcAddr());
                }

                for (Sonic3kPlcArtRegistry.LevelArtEntry entry : plan.levelArt()) {
                    collectSAndKRuntimeAddressViolation(violations,
                            context + " level art " + entry.key() + " mappingAddr",
                            entry.key(), "mappingAddr", entry.mappingAddr());
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "SKL object art registry contains standalone Sonic 3 addresses. S3KL zones may legitimately reference "
                        + "the Sonic 3 half, but SKL/S&K-zone object art needs either an S&K-half address or an "
                        + "explicit reviewed exception:\n" + String.join("\n", violations));
    }

    @Test
    public void mhz2PlanHasCluckoidArrowAndDiagonalSpringOverride() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x07, 1);
        assertNotNull(plan);
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CLUCKOID_ARROW)));

        // Diagonal spring should use MGZ/MHZ art tile
        Sonic3kPlcArtRegistry.LevelArtEntry diag = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SPRING_DIAGONAL))
                .findFirst().orElse(null);
        assertNotNull(diag);
        assertEquals(0x0478, diag.artTileBase());
    }

    @Test
    public void mhz2PlanIncludesAct2SpecificRenderedObjectArtKeys() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x07, 1);

        assertStandaloneArtKeys(plan,
                Sonic3kObjectArtKeys.CLUCKOID_ARROW,
                Sonic3kObjectArtKeys.MHZ_END_BOSS,
                Sonic3kObjectArtKeys.MHZ_END_BOSS_SPIKES,
                Sonic3kObjectArtKeys.MHZ_END_BOSS_PILLAR,
                Sonic3kObjectArtKeys.MHZ_SHIP_PROPELLER,
                Sonic3kObjectArtKeys.MHZ2_CUTSCENE_KNUCKLES_PRESS,
                Sonic3kObjectArtKeys.MHZ2_CUTSCENE_KNUCKLES_SWITCH);

        assertLevelArtKeys(plan,
                Sonic3kObjectArtKeys.MHZ2_CUTSCENE_KNUCKLES_LEAVES);
    }

    @Test
    public void sozPlanHasThreeBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x08, 0);
        assertNotNull(plan);
        assertEquals(8, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SKORP)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SANDWORM)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.ROCKN)));
    }

    @Test
    public void lrzPlanHasRocksAndBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x09, 0);
        assertNotNull(plan);
        assertEquals(8, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.FIREWORM_SEGMENTS)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.IWAMODOKI)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.TOXOMISTER)));

        // Level-art rock should be act 1 variant
        boolean hasLrz1Rock = plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.LRZ1_ROCK));
        assertTrue(hasLrz1Rock);
    }

    @Test
    public void sszPlanHasEggRobo() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x0A, 0);
        assertNotNull(plan);
        assertEquals(6, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SSZ_EGG_ROBO)));

        // EggRobo should use palette 0
        Sonic3kPlcArtRegistry.StandaloneArtEntry eggRobo = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SSZ_EGG_ROBO))
                .findFirst().orElse(null);
        assertNotNull(eggRobo);
        assertEquals(0, eggRobo.palette());
    }

    @Test
    public void dezPlanHasTwoBadniks() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x0B, 0);
        assertNotNull(plan);
        assertEquals(7, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SPIKEBONKER)));
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.CHAINSPIKE)));
    }

    @Test
    public void ddzPlanHasEggRobo() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x0C, 0);
        assertNotNull(plan);
        assertEquals(6, plan.standaloneArt().size());
        assertTrue(plan.standaloneArt().stream().anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.DDZ_EGG_ROBO)));
        Sonic3kPlcArtRegistry.StandaloneArtEntry ddzEggRobo = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.DDZ_EGG_ROBO))
                .findFirst().orElse(null);
        assertNotNull(ddzEggRobo);
        assertEquals(0, ddzEggRobo.palette());
    }

    @Test
    public void pachinkoPlanHasBonusStageObjectArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x14, 0);
        assertNotNull(plan);

        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_BUMPER)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_TRIANGLE_BUMPER)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_FLIPPER)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_ENERGY_TRAP)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_INVISIBLE_UNKNOWN)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_PLATFORM)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_ITEM_ORB)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_GUMBALLS)));

        Sonic3kPlcArtRegistry.LevelArtEntry bumper = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_BUMPER))
                .findFirst().orElse(null);
        assertNotNull(bumper);
        assertEquals(Sonic3kConstants.ARTTILE_PACHINKO_MAIN, bumper.artTileBase());

        Sonic3kPlcArtRegistry.LevelArtEntry gumballs = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_GUMBALLS))
                .findFirst().orElse(null);
        assertNotNull(gumballs);
        assertEquals(Sonic3kConstants.ARTTILE_PACHINKO_GUMBALLS, gumballs.artTileBase());

        Sonic3kPlcArtRegistry.LevelArtEntry invisibleBeam = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.PACHINKO_INVISIBLE_UNKNOWN))
                .findFirst().orElse(null);
        assertNotNull(invisibleBeam);
        assertEquals(Sonic3kConstants.ARTTILE_PACHINKO_MAIN + 0x97, invisibleBeam.artTileBase());
    }

    @Test
    public void slotsPlanHasBonusStageObjectArt() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x15, 0);
        assertNotNull(plan);

        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_COLORED_WALL)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_GOAL)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_BUMPER)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_R_LABEL)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_PEPPERMINT)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_MACHINE_FACE)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_BONUS_CAGE)));
        assertTrue(plan.levelArt().stream()
                .anyMatch(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_SPIKE_REWARD)));

        Sonic3kPlcArtRegistry.LevelArtEntry cage = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_BONUS_CAGE))
                .findFirst().orElse(null);
        assertNotNull(cage);
        assertEquals(Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x146, cage.artTileBase());
        assertEquals(Sonic3kConstants.MAP_SLOT_BONUS_CAGE_ADDR, cage.mappingAddr());
        assertEquals(S3kSpriteDataLoader.MappingFormat.STANDARD, cage.mappingFormat());

        Sonic3kPlcArtRegistry.LevelArtEntry spike = plan.levelArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_SPIKE_REWARD))
                .findFirst().orElse(null);
        assertNotNull(spike);
        assertEquals(Sonic3kConstants.ARTTILE_SLOTS_BLOCKS + 0x155, spike.artTileBase());
        assertEquals(Sonic3kConstants.MAP_SLOT_SPIKE_REWARD_ADDR, spike.mappingAddr());
        assertEquals(S3kSpriteDataLoader.MappingFormat.STANDARD, spike.mappingFormat());

        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_COLORED_WALL).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_GOAL).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_BUMPER).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_R_LABEL).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_PEPPERMINT).mappingFormat());
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X,
                findLevelArt(plan, Sonic3kObjectArtKeys.SLOT_MACHINE_FACE).mappingFormat());

        Sonic3kPlcArtRegistry.StandaloneArtEntry ring = plan.standaloneArt().stream()
                .filter(e -> e.key().equals(Sonic3kObjectArtKeys.SLOT_RING_STAGE))
                .findFirst().orElse(null);
        assertNotNull(ring);
        assertEquals(S3kSpriteDataLoader.MappingFormat.LEGACY_BYTE_X, ring.mappingFormat());
    }

    private static Sonic3kPlcArtRegistry.LevelArtEntry findLevelArt(
            Sonic3kPlcArtRegistry.ZoneArtPlan plan, String key) {
        return plan.levelArt().stream()
                .filter(e -> e.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static void assertStandaloneArtKeys(Sonic3kPlcArtRegistry.ZoneArtPlan plan, String... keys) {
        Set<String> actual = new HashSet<>();
        for (Sonic3kPlcArtRegistry.StandaloneArtEntry entry : plan.standaloneArt()) {
            actual.add(entry.key());
        }
        for (String key : keys) {
            assertTrue(actual.contains(key), "Missing standalone art key: " + key);
        }
    }

    private static void assertLevelArtKeys(Sonic3kPlcArtRegistry.ZoneArtPlan plan, String... keys) {
        Set<String> actual = new HashSet<>();
        for (Sonic3kPlcArtRegistry.LevelArtEntry entry : plan.levelArt()) {
            actual.add(entry.key());
        }
        for (String key : keys) {
            assertTrue(actual.contains(key), "Missing level art key: " + key);
        }
    }

    private static void assertMappingTilesWithinSheet(ObjectSpriteSheet sheet, String message) {
        int patternCount = sheet.getPatterns().length;
        for (int frameIndex = 0; frameIndex < sheet.getFrameCount(); frameIndex++) {
            SpriteMappingFrame frame = sheet.getFrame(frameIndex);
            for (SpriteMappingPiece piece : frame.pieces()) {
                int endExclusive = piece.tileIndex() + (piece.widthTiles() * piece.heightTiles());
                assertTrue(piece.tileIndex() >= 0 && endExclusive <= patternCount,
                        message + " frame=" + frameIndex
                                + " tileRange=0x" + Integer.toHexString(piece.tileIndex())
                                + "-0x" + Integer.toHexString(endExclusive - 1)
                                + " patterns=" + patternCount);
            }
        }
    }

    private static void assertRideGrapplePiece(SpriteMappingFrame frame, int widthTiles, int heightTiles, int tileIndex) {
        assertEquals(1, frame.pieces().size(), "LBZ ride grapple frame should have one mapping piece");
        SpriteMappingPiece piece = frame.pieces().getFirst();
        assertEquals(widthTiles, piece.widthTiles());
        assertEquals(heightTiles, piece.heightTiles());
        assertEquals(tileIndex, piece.tileIndex());
    }

    private static void assertLbzMovingPlatformPiece(SpriteMappingFrame frame, int pieceIndex,
                                                     int widthTiles, int heightTiles, int tileIndex) {
        assertEquals(2, frame.pieces().size(), "LBZ moving platform frame should have two mapping pieces");
        SpriteMappingPiece piece = frame.pieces().get(pieceIndex);
        assertEquals(widthTiles, piece.widthTiles());
        assertEquals(heightTiles, piece.heightTiles());
        assertEquals(tileIndex, piece.tileIndex());
    }

    private static void assertLbzFlameThrowerFrame(SpriteMappingFrame frame, int[][] expectedPieces) {
        assertEquals(expectedPieces.length, frame.pieces().size(), "LBZ flame thrower frame piece count");
        for (int i = 0; i < expectedPieces.length; i++) {
            SpriteMappingPiece piece = frame.pieces().get(i);
            assertEquals(expectedPieces[i][0], piece.widthTiles(), "LBZ flame thrower piece " + i + " width");
            assertEquals(expectedPieces[i][1], piece.heightTiles(), "LBZ flame thrower piece " + i + " height");
            assertEquals(expectedPieces[i][2], piece.tileIndex(), "LBZ flame thrower piece " + i + " tile");
        }
    }

    private static void assertSaneObjectSpriteSheet(String context, ObjectSpriteSheet sheet) {
        assertTrue(sheet.getPatterns().length > 0, context + " should have decompressed patterns");
        List<SpriteMappingFrame> frames = new ArrayList<>();
        for (int i = 0; i < sheet.getFrameCount(); i++) {
            frames.add(sheet.getFrame(i));
        }
        assertSaneMappingFrames(context, frames, sheet.getPatterns().length);
    }

    private static void assertSaneStandaloneEntry(
            String context,
            RomByteReader reader,
            Rom rom,
            Sonic3kObjectArt art,
            Sonic3kPlcArtRegistry.StandaloneArtEntry entry) throws IOException {
        if (entry.mappingAddr() > 0) {
            List<SpriteMappingFrame> rawFrames = entry.mappingFrameCount() > 0
                    ? S3kSpriteDataLoader.loadMappingFrames(reader, entry.mappingAddr(), entry.mappingFrameCount())
                    : S3kSpriteDataLoader.loadMappingFrames(reader, entry.mappingAddr(), entry.mappingFormat());
            assertSaneMappingFrames(context + " standalone raw mappings " + entry.key(), rawFrames, -1);
        }

        try {
            ObjectSpriteSheet sheet = art.loadStandaloneSheet(rom, entry);
            assertNotNull(sheet, context + " standalone art " + entry.key() + " should build a sheet");
            assertSaneObjectSpriteSheet(context + " standalone art " + entry.key(), sheet);
        } catch (IOException e) {
            assertTrue(isKnownSplitStandaloneSheet(entry),
                    context + " standalone art " + entry.key() + " failed to build: " + e.getMessage());
        }
    }

    private static boolean isKnownSplitStandaloneSheet(Sonic3kPlcArtRegistry.StandaloneArtEntry entry) {
        return Sonic3kObjectArtKeys.MGZ_ENDBOSS.equals(entry.key());
    }

    private static void collectSAndKRuntimeAddressViolation(List<String> violations, String context,
            String key, String field, int address) {
        if (address < 0) {
            return;
        }
        if (address >= STANDALONE_S3_HALF_START
                && !REVIEWED_S3_SIDE_ART_REFERENCES.contains(reviewedS3SideReference(key, field, address))) {
            violations.add(context + " uses S3-side address 0x" + Integer.toHexString(address));
        }
    }

    private static String reviewedS3SideReference(String key, String field, int address) {
        return key + ":" + field + ":0x" + Integer.toHexString(address);
    }

    private static void assertSaneMappingFrames(
            String context,
            List<SpriteMappingFrame> frames,
            int patternCount) {
        assertNotNull(frames, context + " mappings should not be null");
        assertTrue(frames.size() <= MAX_SANE_FRAMES,
                context + " frame count " + frames.size() + " exceeds " + MAX_SANE_FRAMES);

        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            SpriteMappingFrame frame = frames.get(frameIndex);
            assertNotNull(frame, context + " frame " + frameIndex + " should not be null");
            List<SpriteMappingPiece> pieces = frame.pieces();
            assertNotNull(pieces, context + " frame " + frameIndex + " pieces should not be null");
            assertTrue(pieces.size() <= MAX_SANE_FRAME_PIECES,
                    context + " frame " + frameIndex + " piece count " + pieces.size()
                            + " exceeds " + MAX_SANE_FRAME_PIECES);

            int totalTiles = 0;
            boolean first = true;
            int minX = 0;
            int minY = 0;
            int maxX = 0;
            int maxY = 0;
            for (int pieceIndex = 0; pieceIndex < pieces.size(); pieceIndex++) {
                SpriteMappingPiece piece = pieces.get(pieceIndex);
                assertNotNull(piece, context + " frame " + frameIndex + " piece " + pieceIndex + " is null");
                assertTrue(piece.widthTiles() >= 1 && piece.widthTiles() <= 4,
                        context + " frame " + frameIndex + " piece " + pieceIndex + " invalid width");
                assertTrue(piece.heightTiles() >= 1 && piece.heightTiles() <= 4,
                        context + " frame " + frameIndex + " piece " + pieceIndex + " invalid height");
                assertTrue(Math.abs(piece.xOffset()) <= MAX_SANE_ABS_PIECE_OFFSET_PIXELS
                                && Math.abs(piece.yOffset()) <= MAX_SANE_ABS_PIECE_OFFSET_PIXELS,
                        context + " frame " + frameIndex + " piece " + pieceIndex + " extreme offset");

                int pieceTiles = piece.widthTiles() * piece.heightTiles();
                totalTiles += pieceTiles;
                assertTrue(totalTiles <= MAX_SANE_FRAME_TILES,
                        context + " frame " + frameIndex + " tile count exceeds " + MAX_SANE_FRAME_TILES);
                assertTrue(piece.tileIndex() >= 0,
                        context + " frame " + frameIndex + " piece " + pieceIndex + " negative tile index");
                if (patternCount >= 0) {
                    assertTrue(piece.tileIndex() + pieceTiles <= patternCount,
                            context + " frame " + frameIndex + " piece " + pieceIndex
                                    + " references tile range [" + piece.tileIndex() + ","
                                    + (piece.tileIndex() + pieceTiles) + ") outside pattern count "
                                    + patternCount);
                }

                int left = piece.xOffset();
                int top = piece.yOffset();
                int right = left + piece.widthTiles() * 8 - 1;
                int bottom = top + piece.heightTiles() * 8 - 1;
                if (first) {
                    minX = left;
                    minY = top;
                    maxX = right;
                    maxY = bottom;
                    first = false;
                } else {
                    minX = Math.min(minX, left);
                    minY = Math.min(minY, top);
                    maxX = Math.max(maxX, right);
                    maxY = Math.max(maxY, bottom);
                }
            }
            if (!first) {
                int width = maxX - minX + 1;
                int height = maxY - minY + 1;
                assertTrue(width <= MAX_SANE_FRAME_SPAN_PIXELS && height <= MAX_SANE_FRAME_SPAN_PIXELS,
                        context + " frame " + frameIndex + " bounds span " + width + "x" + height);
            }
        }
    }

    @Test
    public void allZonesReturnNonNullPlan() {
        // All 14 zone indices (0x00-0x0D) must return non-null plans for both acts
        for (int zone = 0x00; zone <= 0x0D; zone++) {
            for (int act = 0; act <= 1; act++) {
                Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(zone, act);
                assertNotNull(plan, "Zone 0x" + Integer.toHexString(zone) + " act " + act + " returned null");
                assertNotNull(plan.standaloneArt());
                assertNotNull(plan.levelArt());
                // All zones should have at least the 7 shared level-art entries
                assertTrue(plan.levelArt().size() >= 7, "Zone 0x" + Integer.toHexString(zone) + " act " + act
                                + " has fewer than 7 level art entries");
            }
        }
    }

    @Test
    public void allPopulatedZonesHaveStandaloneEntries() {
        // Every zone except HPZ (0x0D) should have at least one standalone entry
        int[] populatedZones = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C};
        for (int zone : populatedZones) {
            Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(zone, 0);
            assertFalse(plan.standaloneArt().isEmpty(), "Zone 0x" + Integer.toHexString(zone) + " should have standalone entries");
        }
    }

    @Test
    public void noDuplicateKeysInPlan() {
        // No plan should contain duplicate keys across standalone + level art
        for (int zone = 0x00; zone <= 0x0D; zone++) {
            for (int act = 0; act <= 1; act++) {
                Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(zone, act);
                Set<String> keys = new HashSet<>();
                for (var e : plan.standaloneArt()) {
                    assertTrue(keys.add(e.key()), "Duplicate standalone key '" + e.key() + "' in zone 0x"
                            + Integer.toHexString(zone) + " act " + act);
                }
                for (var e : plan.levelArt()) {
                    assertTrue(keys.add(e.key()), "Duplicate level-art key '" + e.key() + "' in zone 0x"
                            + Integer.toHexString(zone) + " act " + act);
                }
            }
        }
    }

    @Test
    public void unknownZoneReturnsSharedOnlyPlan() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0xFF, 0);

        assertNotNull(plan);
        assertTrue(plan.levelArt().size() >= 7, "Expected at least 7 shared level art entries");
        assertEquals(5, plan.standaloneArt().size(), "Expected shared standalone art only for unknown zone");
    }
}
