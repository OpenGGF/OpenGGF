package com.openggf.game.soniccd;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.soniccd.backupram.CdBackupRamHost;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.program.CdStageResult;
import com.openggf.game.soniccd.timeattack.CdTimeAttackScreen;
import com.openggf.game.soniccd.title.CdTitleScreen;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;
import static org.lwjgl.opengl.GL11.glGetError;

/**
 * Time Attack through the engine's hosts, from a save with crafted records (the native
 * {@code rehost-timeattack/inputs/bram-ta-records.bin}): the title's TIME ATTACK entry opens the menu in the
 * {@code OPTIONS} host; PALMTREE PANIC, GO starts its act 1 from the disc in {@code LEVEL} with
 * {@code timeAttackMode} set; the stage's end (reported as the results owner will, through
 * {@link CdSceneCoordinator#stageEnded} with the level's time) returns to the menu, which inserts the record,
 * takes the initials and commits the backup RAM once. A gameplay-rewind restore taken in the stage puts the
 * devices and the Time Attack state back and commits nothing.
 *
 * <p>Picture (native check): the menu as drawn by {@code CdProgramScreenRenderer}, against the native
 * screenshot {@code rehost-timeattack/ta-records/postframe-02300.ppm} of the same save at 3-bit colour
 * levels. The selected row's bar blinks with the frame counter, so the best of 40 consecutive menu frames is
 * compared; it must equal native on every pixel. A one-pixel shift of the native picture must fail.
 */
@EnabledOnOs(OS.LINUX)
class TestCdTimeAttackFlow {
    private GameLoop loop;
    private Bk2FrameInput previous;

    private void step(int directions, int actions, boolean start) {
        Bk2FrameInput current = new Bk2FrameInput(0, directions, actions, start, "time attack flow");
        loop.getInputHandler().setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
        previous = current;
        loop.step();
    }

    private void idle(int frames) { for (int i = 0; i < frames; i++) step(0, 0, false); }

    private int until(GameMode mode, int budget) {
        int f = 0;
        while (loop.getCurrentGameMode() != mode && f++ < budget) idle(1);
        assertEquals(mode, loop.getCurrentGameMode(), "reached " + mode);
        return f;
    }

    private static RgbaImage capture(CdTimeAttackScreen screen) {
        GraphicsManager graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        while (glGetError() != GL_NO_ERROR) { /* drain a previous boot's stale error */ }
        screen.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT);
        screen.draw();
        graphics.flush();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }

    private static int same(RgbaImage engine, RgbaImage reference) {
        int same = 0;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (TestCdTitlePicture.level(engine.argb(x, y)) == TestCdTitlePicture.level(reference.argb(x, y))) same++;
            }
        }
        return same;
    }

    @Test
    void timeAttackRunsFromTheTitleThroughAStageAndRecordsItsTime() throws Exception {
        byte[] save = Files.readAllBytes(CdTestMedia.nativeFile("rehost-timeattack/inputs/bram-ta-records.bin"));
        RgbaImage reference = TestCdTitlePicture.ppm(CdTestMedia.nativeFile("rehost-timeattack/ta-records/postframe-02300.ppm"));
        int[] shiftedPixels = new int[320 * 224];
        for (int y = 0; y < 224; y++) for (int x = 1; x < 320; x++) shiftedPixels[y * 320 + x] = reference.argb(x - 1, y);
        RgbaImage shifted = new RgbaImage(320, 224, shiftedPixels);
        List<CdBackupRamHost.Images> commits = new ArrayList<>();
        String oldGl = System.getProperty("openggf.headless.gl");
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        GameServices.configuration().setSessionOverride(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            SonicCdGameModule module = new SonicCdGameModule();
            CdSceneCoordinator scenes = module.getGameService(CdSceneCoordinator.class);
            scenes.attachBackupRamHost(new CdBackupRamHost() {
                @Override public Optional<Images> load() { return Optional.of(new Images(save.clone(), null)); }
                @Override public void commit(Images images) { commits.add(images); }
            }, 0);
            boot.setBootToTitle(true);
            loop = boot.boot(disc, module, 0, 0);
            CdProgramFlow flow = scenes.flow();
            assertEquals(0x30, flow.titleFlags(), "the save shows TIME ATTACK on the title");
            CdTitleScreen title = assertInstanceOf(CdTitleScreen.class, loop.getTitleScreenProvider());
            try (var lease = RecordedInputOwnership.acquire(loop.getInputHandler())) {
                int f = 0;
                while (!(title.program() != null && title.program().acceptsMenuInput()) && f++ < 2000) idle(1);
                step(0, 0, true);
                idle(60);                                    // PRESS START: NEW GAME
                step(8, 0, false);
                idle(60);                                    // Right: TIME ATTACK
                step(0, 0, true);
                until(GameMode.OPTIONS, 600);
                CdTimeAttackScreen menu = assertInstanceOf(CdTimeAttackScreen.class, scenes.lastProgramScreen());

                // The menu picture, settled.
                idle(300);
                int best = -1;
                RgbaImage bestImage = null;
                for (int i = 0; i < 40; i++) {
                    idle(1);
                    RgbaImage image = capture(menu);
                    int s = same(image, reference);
                    if (s > best) { best = s; bestImage = image; }
                }
                int control = same(bestImage, shifted);
                Path out = Path.of("target", "soniccd-timeattack");
                Files.createDirectories(out);
                ScreenshotCapture.savePNG(bestImage, out.resolve("engine-menu.png"));
                System.out.println("CD Time Attack menu picture: " + best + " of 71680 pixels equal native; one-pixel-shift control " + control);
                assertEquals(320 * 224, best, "menu picture against native frame 2300");
                assertTrue(control < 320 * 224 * 99 / 100, "a picture moved by one pixel must not match");

                step(0, 1, false);
                idle(120);                                   // A: Palmtree Panic's panel, NEXT lit
                step(8, 0, false);
                idle(60);                                    // Right: GO
                step(0, 1, false);                           // A: EnterSelectedStage, level 1
                until(GameMode.LEVEL, 600);
                assertTrue(flow.timeAttackMode(), "timeAttackMode set (IPX.asm:1230)");
                assertEquals(CdProgramRequest.Route.TIME_ATTACK, flow.route());
                assertEquals(CdStagePayload.R11A, ((CdLevel) GameServices.level().getCurrentLevel()).payload(), "from the disc");
                idle(30);

                RewindSnapshottable<Object> adapter = rewind(module);
                Object inStage = adapter.capture();
                // The results owner reports the cleared act: one life, 1'32"16 (time 00 01 20 10).
                scenes.stageEnded(CdStageResult.cleared(1, CdStageResult.TIME_PRESENT, false, 0).withTime(0x00012010),
                        GameServices.level());
                until(GameMode.OPTIONS, 900);
                idle(200);
                step(0, 2, false);
                idle(60);                                    // B ends the new rank's blink
                step(0, 0, true);
                idle(120);                                   // Start keeps the initials
                assertEquals(1, commits.size(), "one durable commit: the record with its initials");
                var record = flow.backupRam().readSave();
                assertEquals(0x00012010, record.time(0, 0), "first place on Palmtree Panic act 1");
                assertEquals(0x0001232D, record.time(0, 1), "the old first place moved down");
                assertEquals(record.defaultInitials(), record.initials(0, 0));

                adapter.restore(inStage);
                assertEquals(1, commits.size(), "a rewind restore commits nothing");
                assertEquals(0x0001232D, flow.backupRam().readSave().time(0, 0), "the device is back as it was in the stage");
                assertTrue(flow.timeAttackMode());
                assertEquals(CdProgramRequest.Route.TIME_ATTACK, flow.route());
            }
        } finally {
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }

    @SuppressWarnings("unchecked")
    private static RewindSnapshottable<Object> rewind(SonicCdGameModule module) {
        // The module also registers the time-travel and audio adapters; this is the program flow's.
        var flowAdapter = module.getGameService(com.openggf.game.soniccd.program.CdSceneCoordinator.class)
                .rewindAdapter();
        assertEquals(1, module.rewindAdapters().stream().filter(a -> a == flowAdapter).count(),
                "the flow's adapter is registered once, as one instance");
        return (RewindSnapshottable<Object>) (RewindSnapshottable<?>) flowAdapter;
    }
}
