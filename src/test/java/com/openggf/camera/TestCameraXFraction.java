package com.openggf.camera;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.replay.TraceSegmentCarriedStateImport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S3K {@code Camera_X_pos} low word is one value. {@code Get_LevelSizeStart} writes
 * only the word (sonic3k.asm:38241), so a level load keeps it; {@code Title_Screen}'s
 * {@code Camera_RAM} clear (sonic3k.asm:5448) is modelled by a fresh camera and
 * {@link Camera#resetState()}. A cold trace segment takes it from {@code level_entry_ram}.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestCameraXFraction {
    private static final Path ZONE0C = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/zone0c");

    @AfterEach
    void reset() {
        SessionManager.clear();
    }

    @Test
    void lowWordSurvivesLevelLoadsAndRewindAndClearsWithCameraRam() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var camera = GameServices.camera();
        assertEquals(0, CameraSubpixelAccess.xFraction(camera), "a new session starts cleared");
        CameraSubpixelAccess.setXFraction(camera, 0x2700);
        GameServices.level().loadZoneAndAct(1, 0);
        assertEquals(0x2700, CameraSubpixelAccess.xFraction(GameServices.camera()),
                "Get_LevelSizeStart writes only the word");

        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        CameraSubpixelAccess.setXFraction(GameServices.camera(), 0x1234);
        registry.restore(saved);
        assertEquals(0x2700, CameraSubpixelAccess.xFraction(GameServices.camera()));

        GameServices.camera().resetState();
        assertEquals(0, CameraSubpixelAccess.xFraction(GameServices.camera()));
    }

    @Test
    void segmentEntryImportsTheRecordedLowWordOnce(@TempDir Path dir) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        var mapper = new ObjectMapper();
        var meta = (ObjectNode) mapper.readTree(ZONE0C.resolve("metadata.json").toFile());
        var ram = meta.has("level_entry_ram") ? (ObjectNode) meta.get("level_entry_ram")
                : meta.putObject("level_entry_ram");
        ram.put("camera_x_pos", 0x00012700);
        Files.writeString(dir.resolve("metadata.json"), mapper.writeValueAsString(meta));
        var camera = GameServices.camera();
        CameraSubpixelAccess.setXFraction(camera, 0x0800); // an add the boot's own load already made
        TraceSegmentCarriedStateImport.importAtSegmentEntry(
                TraceData.loadMetadataOnly(dir), GameServices.sprites().getMainPlayable());
        assertEquals(0x2F00, CameraSubpixelAccess.xFraction(camera));

        ram.remove("camera_x_pos");
        Files.writeString(dir.resolve("metadata.json"), mapper.writeValueAsString(meta));
        TraceSegmentCarriedStateImport.importAtSegmentEntry(
                TraceData.loadMetadataOnly(dir), GameServices.sprites().getMainPlayable());
        assertEquals(0x2F00, CameraSubpixelAccess.xFraction(camera), "absent: nothing is invented");
    }

    @Test
    void returnCameraUsesSelectedRestoreBoundAndRetainsDistinctDeathCheckpoint() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        var trace = TraceData.loadMetadataOnly(Path.of(
                "src/test/resources/traces/s3k/runs/s3k-tails-full-chain-all-emeralds/aiz_3"));
        var player = GameServices.sprites().getMainPlayable();
        player.setCentreX(trace.metadata().startX());
        player.setCentreY(trace.metadata().startY());
        var carried = TraceSegmentCarriedStateImport.importAtSegmentEntry(trace, player);
        var checkpoint = (com.openggf.game.CheckpointState) GameServices.level().getCheckpointState();
        int deathBound = checkpoint.getSavedCameraMaxY();
        assertTrue(deathBound < carried.restore().cameraMaxY(), "the recording has distinct saved blocks");

        TraceSegmentCarriedStateImport.initializeCameraAtEntry(carried, GameServices.level(),
                com.openggf.game.session.LevelEntryProvenance.POSITIONED_RETURN_SPAWN);

        assertEquals(carried.restore().cameraMaxY(), GameServices.camera().getMaxY());
        assertTrue(GameServices.camera().getY() > deathBound,
                "the current return must not clamp to its unrelated death checkpoint");
        assertEquals(deathBound, checkpoint.getSavedCameraMaxY(), "later death retains Saved_*");
    }
}
