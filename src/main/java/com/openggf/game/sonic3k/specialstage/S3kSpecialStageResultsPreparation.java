package com.openggf.game.sonic3k.specialstage;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.LevelBackdropResultsScreen;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.ResultsScreen;
import com.openggf.game.internal.ResultsResourcePreparation;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.Sonic3kObjectArt;
import com.openggf.game.sonic3k.resources.S3kKosModuleQueue;
import com.openggf.game.timing.HardwareWorkHandle;
import com.openggf.graphics.GLCommand;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Native loc_2E0C4: drains the results art queues before installing the results
 * screen. Lives only in non-rewindable results mode and holds no global state.
 */
final class S3kSpecialStageResultsPreparation
        implements ResultsScreen, LevelBackdropResultsScreen, ResultsResourcePreparation {
    private final S3kKosModuleQueue modules;
    private final Sonic3kLevelTitlePlcService nemesis;
    private final List<HardwareWorkHandle> handles;
    private final Supplier<? extends ResultsScreen> factory;
    private ResultsScreen screen;
    private int viewportWidth = 320;

    S3kSpecialStageResultsPreparation(Rom rom, PlayerCharacter character,
            S3kKosModuleQueue modules, Sonic3kLevelTitlePlcService nemesis,
            Supplier<? extends ResultsScreen> factory) throws IOException {
        this.modules = modules;
        this.nemesis = nemesis;
        this.factory = factory;
        nemesis.beginSpecialStageResults();
        handles = new Sonic3kObjectArt(null, RomByteReader.fromRom(rom))
                .queueSpecialStageResultsArt(rom, character, modules);
    }

    @Override public boolean isPreparingResults() { return screen == null; }

    @Override public void finishResultsPreparationIteration() {
        // These are the ROM's Kos_modules_left / Nem_decomp_queue tests, after
        // Process_Nem_Queue_Init and Process_Kos_Module_Queue, never a frame count.
        if (screen != null || nemesis.isBusy() || modules.hasPendingPhysicalModules()
                || !handles.stream().allMatch(modules::isReady)) return;
        handles.forEach(modules::claim);
        screen = factory.get();
        screen.setViewportWidth(viewportWidth);
    }

    @Override public void update(int frameCounter, Object context) {
        if (screen != null) screen.update(frameCounter, context);
    }
    @Override public boolean isComplete() { return screen != null && screen.isComplete(); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (screen != null) screen.appendRenderCommands(commands);
    }
    @Override public void setViewportWidth(int width) {
        viewportWidth = width;
        if (screen != null) screen.setViewportWidth(width);
    }
    @Override public boolean drawsLevelBackdrop() {
        return screen instanceof LevelBackdropResultsScreen backdrop && backdrop.drawsLevelBackdrop();
    }
    @Override public void prepareLevelBackdropDraw() {
        if (screen instanceof LevelBackdropResultsScreen backdrop) backdrop.prepareLevelBackdropDraw();
    }
}
