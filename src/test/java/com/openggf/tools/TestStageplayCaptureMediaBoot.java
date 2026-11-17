package com.openggf.tools;

import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Production two-frame exports exercise both boot paths, including their real encoder lifecycle. */
class TestStageplayCaptureMediaBoot {
    @TempDir Path root;

    @Test @RequiresRom(SonicGame.SONIC_2)
    void cartridgeExportBootsThroughTheExactReader() throws Exception {
        export("s2", 0, RomTestUtils.ensureSonic2RomAvailable().toPath());
    }

    @Test void discExportBootsThroughTheVerifiedHostedSource() throws Exception {
        export("scd", 1, com.openggf.game.soniccd.CdTestMedia.usaCue());
    }

    private void export(String game, int stage, Path media) throws Exception {
        Path scene = root.resolve(game + ".stageplay"), film = root.resolve(game + ".mkv");
        StageplayStore.write(scene, StageplayDocument.empty(game, 0, stage, 0, 0, 160, 112).withDurationFrames(2));
        assertEquals(film, StageplayCaptureTool.run(StageplayCaptureTool.Arguments.parse(new String[]{
                "--scene", scene.toString(), "--out", film.toString(), "--" + game + "-rom", media.toString()})));
        assertTrue(Files.size(film) > 0, "The production encoder must publish a completed film");
    }
}
