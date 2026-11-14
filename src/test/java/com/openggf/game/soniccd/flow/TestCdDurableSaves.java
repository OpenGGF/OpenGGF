package com.openggf.game.soniccd.flow;

import com.openggf.game.GameMode;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SavePaths;
import com.openggf.game.soniccd.backupram.CdBackupRamHost;
import com.openggf.game.soniccd.backupram.CdSaveVariables;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.title.CdTitleScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interactive startup's durable backup RAM ({@code SonicCdGameModule.attachInteractiveBackupRam}): a New Game
 * commits IPX's save record to the save owner's slot, and a later session's Continue (IPX {@code LoadGame},
 * IPX.asm:137-160) reads the saved zone from it. The save root is a test directory.
 */
@EnabledOnOs(OS.LINUX)
class TestCdDurableSaves {
    private static Path root() throws Exception {
        Path root = Path.of("target", "soniccd-durable-saves").toAbsolutePath();
        if (Files.exists(root)) try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
        Files.createDirectories(root);
        return root;
    }

    @Test
    void newGameWritesTheSaveAndContinueLoadsTheSavedZone() throws Exception {
        Path root = root();
        String old = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty(SavePaths.ROOT_PROPERTY, root.toString());
        try {
            // A New Game's save (RunR1, IPX.asm:162-174) commits through the durable host; then the save
            // RunR4 writes after Palmtree Panic and Collision Chaos (RunR4, IPX.asm:212-214):
            // savedLevel zone 2, titleFlags bits 6 and 5 (IPX.asm:177-178), Time Attack unlocked to 6.
            try (CdFlowHarness h = new CdFlowHarness(m -> m.attachInteractiveBackupRam())) {
                h.newGame();
                var ram = h.scenes.flow().backupRam();
                assertEquals(1, ram.commits(), "one game-driven commit");
                ram.writeVariables(new CdSaveVariables(0x0200, 0, h.scenes.flow().titleFlags() | 0x60, 6, 0, 0, 0));
                assertEquals(2, ram.commits());
            }
            SaveManager saves = new SaveManager(root);
            saves.flushPendingWrites();
            assertTrue(CdBackupRamHost.durable(saves).load().isPresent(), "the slot holds the backup RAM images");
            try (CdFlowHarness h = new CdFlowHarness(m -> m.attachInteractiveBackupRam())) {
                var flow = h.scenes.flow();
                int savedZone = flow.savedLevel() >> 8;
                assertTrue((flow.titleFlags() & 0x40) != 0, "titleFlags bit 6: the title offers CONTINUE first");
                assertEquals(2, savedZone, "ReadSaveData at IPX Start (IPX.asm:48) read the durable slot");
                h.until(() -> h.loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
                var title = (CdTitleScreen) h.loop.getTitleScreenProvider();
                h.until(() -> title.program() != null && title.program().acceptsMenuInput(), 3000, 0);
                h.step(0, 0, true);
                h.idle(60);
                h.step(0, 0, true);                          // CONTINUE is the highlighted option
                h.until(() -> h.loop.getCurrentGameMode() == GameMode.LEVEL, 900, 0);
                assertEquals(savedZone, h.level().payload().stage().zone().ordinal(), "LoadGame runs the saved zone");
                assertEquals(1, h.level().payload().stage().actNumber());
                assertEquals(CdProgramRequest.Route.CAMPAIGN, flow.route());
                assertEquals(3, h.row().lives(), "LoadGame: move.b #3,lives (IPX.asm:139)");
            }
        } finally {
            if (old == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, old);
        }
    }
}
