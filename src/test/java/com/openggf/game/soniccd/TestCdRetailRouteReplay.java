package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.program.CdStageSelection;
import com.openggf.game.soniccd.title.CdStageSelectProgram;
import com.openggf.game.soniccd.title.CdStageSelectScreen;
import com.openggf.game.soniccd.title.CdTitleScreen;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native check for the program flow: each stage fixture's own movie, recorded on hardware from
 * power-on through the retail title and its stage-select secret, is replayed through the engine's
 * title, stage select and level load, and must take the same route to the same stage program.
 *
 * <p>Compared: the title's exit code (8, the stage-select secret, {@code ObjMenu_StageSelCheat}), the
 * stage-select row chosen (the fixture's {@code route.selection_index}), and the stage program the engine
 * loads (the fixture's payload, which its native trace confirms). Not compared: frame counts, boot, logo
 * and disc-load timing, palettes or pictures, the stage itself.
 *
 * <p>Alignment, which is the one thing the replay decides: rows before the third Start press belong to
 * programs the engine does not host (BIOS boot screen, the MDINIT SEGA logo, the opening FMV), each left
 * with one Start, and are dropped. When a hosted program first reads input (the title's menu object, the
 * stage select's main loop), replay continues at the next non-neutral row; the neutral rows skipped are
 * time native spends in those unhosted programs, fades and disc loads. Every other row is fed in order, one
 * per engine frame. The stage select's hold therefore starts on its first main-loop pass; native's phase
 * within the 8-frame repeat is not observable from the fixture. A control replays one movie with the
 * cheat's final B press removed and must not reach the stage.
 */
@EnabledOnOs(OS.LINUX)
class TestCdRetailRouteReplay {
    /** Three zones, three periods, both menu directions. */
    private static final CdStagePayload[] FIXTURES = {CdStagePayload.R11A, CdStagePayload.R43C, CdStagePayload.R61B};
    private static final int FRAME_BUDGET = 4000;
    private static final int PREFIX_STARTS = 3;

    private record Route(int titleExit, int selection, CdStagePayload stage, int framesStepped, int rowsFed,
                         List<String> decisions) { }

    private static boolean neutral(Bk2FrameInput row) {
        return row.p1InputMask() == 0 && row.p1ActionMask() == 0 && !row.p1StartPressed();
    }

    private static int afterPrefix(List<Bk2FrameInput> rows) {
        int starts = 0;
        for (int i = 0; i < rows.size(); i++) {
            boolean press = rows.get(i).p1StartPressed() && (i == 0 || !rows.get(i - 1).p1StartPressed());
            if (press && ++starts == PREFIX_STARTS) return i + 1;
        }
        throw new IllegalStateException("movie has fewer than " + PREFIX_STARTS + " Start presses");
    }

    private static int nextInput(List<Bk2FrameInput> rows, int from) {
        int i = from;
        while (i < rows.size() && neutral(rows.get(i))) i++;
        return i;
    }

    private static Route replay(GameLoop loop, List<Bk2FrameInput> rows) {
        loop.initializeTitleScreenMode();
        CdTitleScreen title = (CdTitleScreen) loop.getTitleScreenProvider();
        CdStageSelectScreen select = (CdStageSelectScreen) GameServices.module().getLevelSelectProvider();
        CdSceneCoordinator scenes = GameServices.module().getGameService(CdSceneCoordinator.class);
        int cursor = afterPrefix(rows), fed = 0, titleExit = -1, selection = -1;
        int decisionsBefore = scenes.route().size();
        boolean titleAligned = false, selectAligned = false;
        Bk2FrameInput previous = null;
        int frame = 0;
        try (var lease = RecordedInputOwnership.acquire(loop.getInputHandler())) {
            for (; frame < FRAME_BUDGET && loop.getCurrentGameMode() != GameMode.LEVEL; frame++) {
                GameMode mode = loop.getCurrentGameMode();
                boolean titleReads = mode == GameMode.TITLE_SCREEN && title.program() != null
                        && title.program().acceptsMenuInput();
                boolean selectReads = mode == GameMode.LEVEL_SELECT && select.program() != null
                        && select.program().phase() == CdStageSelectProgram.Phase.MAIN;
                if (titleReads && !titleAligned) { cursor = nextInput(rows, cursor); titleAligned = true; }
                if (selectReads && !selectAligned) { cursor = nextInput(rows, cursor); selectAligned = true; }
                Bk2FrameInput row = (titleAligned && cursor < rows.size()) ? rows.get(cursor++) : null;
                if (row != null) fed++;
                Bk2FrameInput current = row != null ? row
                        : new Bk2FrameInput(frame, 0, 0, false, "neutral");
                loop.getInputHandler().setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
                previous = current;
                loop.step();
            }
        }
        CdStagePayload stage = loop.getCurrentGameMode() == GameMode.LEVEL
                && GameServices.level().getCurrentLevel() instanceof CdLevel level ? level.payload() : null;
        assertTrue(scenes.notHosted().isEmpty() || stage == null, "the retail route runs only hosted programs");
        List<String> decisions = scenes.route().subList(decisionsBefore, scenes.route().size());
        for (String decision : decisions) {
            if (titleExit < 0 && decision.startsWith("title exit ")) titleExit = Integer.parseInt(decision.substring(11));
            if (selection < 0 && decision.startsWith("stage select ")) selection = Integer.parseInt(decision.substring(13));
        }
        return new Route(titleExit, selection, stage, frame, fed, decisions);
    }

    @Test
    void fixtureMoviesTakeTheRetailStageSelectRouteToTheirStage() throws Exception {
        String oldGl = System.getProperty("openggf.headless.gl");
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        GameServices.configuration().setSessionOverride(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
        ObjectMapper json = new ObjectMapper();
        StringBuilder report = new StringBuilder();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            boot.setBootToTitle(true);
            GameLoop loop = boot.boot(disc, new SonicCdGameModule(), 0, 0);
            CdProgramFlow flow = GameServices.module().getGameService(CdSceneCoordinator.class).flow();
            for (CdStagePayload payload : FIXTURES) {
                Path dir = CdTestMedia.nativeFile("rehost-fixtures/" + payload + "/manifest.json").getParent();
                int expectedIndex = json.readTree(dir.resolve("manifest.json").toFile())
                        .get("route").get("selection_index").asInt();
                List<Bk2FrameInput> rows = new Bk2MovieLoader().load(dir.resolve("movie.bk2")).getFrames();
                Route route = replay(loop, rows);
                String line = payload + ": " + route;
                System.out.println("CD retail route: " + line);
                report.append(line).append('\n');
                assertEquals(8, route.titleExit(), "title exit through the stage-select secret: " + line);
                assertEquals(expectedIndex, route.selection(), "stage-select row: " + line);
                CdStageSelection row = flow.selections().get(route.selection());
                assertEquals(payload, row.stage().orElseThrow(), "StageSels row names the fixture stage: " + line);
                assertEquals(payload, route.stage(), "stage program loaded: " + line);
            }

            // Control: the same movie without the cheat's last press (B) must not reach the stage.
            Path dir = CdTestMedia.nativeFile("rehost-fixtures/R11B/manifest.json").getParent();
            List<Bk2FrameInput> rows = new ArrayList<>(new Bk2MovieLoader().load(dir.resolve("movie.bk2")).getFrames());
            for (int i = 0; i < rows.size(); i++) {
                Bk2FrameInput r = rows.get(i);
                if (r.p1ActionMask() == 2 && i < 1800) rows.set(i, new Bk2FrameInput(r.frameIndex(), 0, 0, false, "control"));
            }
            Route control = replay(loop, rows);
            System.out.println("CD retail route control (no B): " + control);
            assertNotEquals(8, control.titleExit(), "the control must not enter the stage-select secret");
            assertNotEquals(CdStagePayload.R11B, control.stage(), "the control must not reach the fixture stage");
        } finally {
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }
}
