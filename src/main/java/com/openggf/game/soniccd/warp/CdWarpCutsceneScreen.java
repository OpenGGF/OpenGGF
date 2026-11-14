package com.openggf.game.soniccd.warp;

import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.scene.CdWarpAssets;
import com.openggf.game.soniccd.title.CdTitleScreen;

import java.util.Objects;

/**
 * Hosts the warp cutscene in the {@code OPTIONS} native-program mode between the two stages of a time
 * warp. When the program returns ({@code rts} after {@code FadeToWhite}, Main.asm:191-199) it reports to
 * the {@link CdSceneCoordinator}, which names the destination stage; the screen then exits to the title
 * host, which starts that stage without running the title.
 */
public final class CdWarpCutsceneScreen implements NativeMenuScreen {
    private final CdSceneCoordinator coordinator;
    private final CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
    private CdWarpAssets assets;
    private CdWarpCutscene program;
    private boolean exiting;

    public CdWarpCutsceneScreen(CdSceneCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override public GameMode mode() { return GameMode.OPTIONS; }

    @Override
    public void initialize() {
        if (assets == null) assets = CdWarpCutscene.assets(coordinator.disc());
        // Start clears the program's variables (Main.asm:125-131); the V-INT counter starts at 0.
        program = new CdWarpCutscene(assets, 0);
        renderer.reset();
        exiting = false;
    }

    @Override
    public void update(InputHandler input) {
        if (program == null || exiting) return;
        program.frame(CdTitleScreen.pad(input.logical().player1()));
        for (int id : program.drainSounds()) com.openggf.game.GameServices.audio().playSfx(id);
        if (program.done()) {
            coordinator.programReturned();
            exiting = true;
        }
    }

    @Override public void draw() { if (program != null) renderer.draw(program.screen()); }

    @Override public void setClearColor() { if (program != null) renderer.setClearColor(program.screen()); }

    @Override
    public void reset() {
        program = null;
        exiting = false;
    }

    @Override
    public Exit consumeExit() {
        if (!exiting) return Exit.NONE;
        exiting = false;
        return new Exit(Destination.TITLE_SCREEN, null, false);
    }

    /** The running program, for tests. */
    public CdWarpCutscene program() { return program; }
}
