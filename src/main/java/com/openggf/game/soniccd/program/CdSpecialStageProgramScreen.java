package com.openggf.game.soniccd.program;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.special.CdSpecialStageProvider;
import org.lwjgl.opengl.GL11;

/** Hands a stage requested by the title to the existing special-stage runtime. */
final class CdSpecialStageProgramScreen implements NativeMenuScreen {
    private final CdProgramRequest.SpecialStage request;
    private final CdSceneCoordinator coordinator;
    private Exit exit = Exit.NONE;

    CdSpecialStageProgramScreen(CdProgramRequest.SpecialStage request, CdSceneCoordinator coordinator) {
        this.request = request;
        this.coordinator = coordinator;
    }

    @Override
    public void initialize() {
        var provider = (CdSpecialStageProvider) GameServices.module().getSpecialStageProvider();
        provider.prepareMenuStage(request.entry(), mode(), result -> {
            coordinator.nativeMenuStageEnded(request, result);
            exit = new Exit(Destination.TITLE_SCREEN, null, false);
        });
        exit = new Exit(Destination.SPECIAL_STAGE, null, false);
    }

    @Override public void update(InputHandler input) { }
    @Override public void draw() { }
    @Override public void setClearColor() {
        if (!GameServices.graphics().isHeadlessMode()) GL11.glClearColor(0f, 0f, 0f, 1f);
    }
    @Override public void reset() { exit = Exit.NONE; }

    @Override
    public Exit consumeExit() {
        Exit next = exit;
        exit = Exit.NONE;
        return next;
    }
}
