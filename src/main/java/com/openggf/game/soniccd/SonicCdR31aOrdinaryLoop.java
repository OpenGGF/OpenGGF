package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdR31aAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;

import java.util.Objects;
import java.util.Optional;

/**
 * R3 semantic Main333-432 / VInt_Level821-847 scheduling, N1ecc5f3a.
 * The real private ports own source/World/epoch/thread/current pair and every reached
 * producer. This component owns no native values, input/clock algorithm, pool,
 * capture participant, current art, physical walk or World invocation authority.
 */
final class SonicCdR31aOrdinaryLoop {
    enum Cut {
        BEFORE_SELECTOR8, PREPARE_INPUT, PUBLISH_LEVEL, UPDATE_ANIM_TILES, COPY_CAMERA_DRAW_LEVEL,
        SERVICE_SLOW_PLC, UPDATE_HUD, MAIN_MUSIC, SETTLE_SELECTED_CPU, ADVANCE_LEVEL_FRAME,
        SPAWN_OBJECTS, RUN_OBJECTS, POST_OBJECT_CAMERA, DRAW_OBJECTS, READ_TIME_STOP_WORD,
        PALETTE_CYCLE, UPDATE_SECTION_ART, PREPARE_PLC, UPDATE_GLOBAL_ANIMATIONS, RETURN
    }

    interface Ports {
        /** Actual private invocation/source/epoch/current-pair validation at each reached cut. */
        void requireCurrent(Cut cut, CdPlayerFrameRuntime frame, CdR31aAssets assets);
        /** Source VScroll selection and the existing OLD CPU image; never a newly built SAT. */
        Optional<CpuSat> beforeSelector8();
        /** After actual ReadControllers, before the Frame's bound Sonic art publication. */
        void publishLevel(Optional<CpuSat> selectedCpu);
        void updateAnimTiles();
        void copyCameraAndDrawLevel();
        void serviceSlowPlc();
        void updateHud();
        /** Represented engine VBlank/music cut after actual Frame Finish/count/pause. */
        void mainMusic();
        /** The exact borrowed OLD image, with genuinely serviced art/planes; do not reborrow NOW. */
        void settleSelectedCpu(Optional<CpuSat> selectedCpu);
        void spawnObjects();
        /** Actual increasing physical walk; a returned data value cannot attest its completion. */
        void runObjects(int actualLevelFrame);
        /**
         * Main405-418: actual demo/restart tests, then death bounds or CC events/scroll.
         * A reached unowned LevelStart/lifecycle transfer must throw its original typed fault;
         * returning grants only continuation to the ordinary Draw/tail, never transfer success.
         */
        void postObjectCamera();
        void drawObjects();
        /** Reached TST.W timeStopTimer (Main423), not a byte/boolean projection. */
        int timeStopWord();
        void paletteCycle();
        /** Actual R31 section owner; not the R13 Act3 RTS profile. */
        void updateSectionArt();
        void preparePlc();
        void updateGlobalAnimations();
    }

    private final CdPlayerFrameRuntime frame;
    private final CdR31aAssets assets;
    private final Ports ports;
    private boolean running;
    private boolean failed;

    SonicCdR31aOrdinaryLoop(CdPlayerFrameRuntime frame, CdR31aAssets assets, Ports ports) {
        this.frame = Objects.requireNonNull(frame, "actual Frame");
        this.assets = Objects.requireNonNull(assets, "actual original R31 assets");
        this.ports = Objects.requireNonNull(ports, "actual private R31 ports");
        if (!frame.stageSource().ownsR31aAssets(assets)) {
            throw new IllegalArgumentException("Ordinary loop requires the Frame's exact retained R31 pair");
        }
        requireIdle();
    }

    void run(int heldByte) {
        // A caught nested entry or invalid idle input must not poison a healthy outer run.
        requireIdle();
        if ((heldByte & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native held byte");
        running = true;
        try {
            check(Cut.BEFORE_SELECTOR8);
            var selectedCpu = Objects.requireNonNull(ports.beforeSelector8(), "borrowed selected CPU observation");
            check(Cut.PREPARE_INPUT);
            var selected = frame.prepareInput(heldByte);
            boolean[] published = {false}; // Ephemeral completed callback observation, never owner/capture authority.
            frame.serviceVBlank(selected.phase(),
                    () -> {
                        at(Cut.PUBLISH_LEVEL, () -> ports.publishLevel(selectedCpu));
                        published[0] = true;
                    },
                    () -> {
                        at(Cut.UPDATE_ANIM_TILES, ports::updateAnimTiles);
                        at(Cut.COPY_CAMERA_DRAW_LEVEL, ports::copyCameraAndDrawLevel);
                        at(Cut.SERVICE_SLOW_PLC, ports::serviceSlowPlc);
                        at(Cut.UPDATE_HUD, ports::updateHud);
                    },
                    () -> at(Cut.MAIN_MUSIC, ports::mainMusic));
            if (published[0]) at(Cut.SETTLE_SELECTED_CPU, () -> ports.settleSelectedCpu(selectedCpu));
            if (frame.paused()) {
                check(Cut.RETURN);
                return;
            }
            check(Cut.ADVANCE_LEVEL_FRAME);
            int actualLevelFrame = frame.advanceLevelMainFrame();
            at(Cut.SPAWN_OBJECTS, ports::spawnObjects);
            at(Cut.RUN_OBJECTS, () -> ports.runObjects(actualLevelFrame));
            at(Cut.POST_OBJECT_CAMERA, ports::postObjectCamera);
            at(Cut.DRAW_OBJECTS, ports::drawObjects);
            check(Cut.READ_TIME_STOP_WORD);
            int timeStop = ports.timeStopWord();
            if ((timeStop & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected the native timeStop WORD");
            if (timeStop == 0) at(Cut.PALETTE_CYCLE, ports::paletteCycle);
            at(Cut.UPDATE_SECTION_ART, ports::updateSectionArt);
            at(Cut.PREPARE_PLC, ports::preparePlc);
            at(Cut.UPDATE_GLOBAL_ANIMATIONS, ports::updateGlobalAnimations);
            check(Cut.RETURN);
        } catch (RuntimeException | Error original) {
            failed = true;
            // The real owner retains each completed prefix and owns teardown. No rollback/replay.
            throw original;
        } finally {
            running = false;
        }
    }

    private void at(Cut cut, Runnable operation) {
        check(cut);
        operation.run();
    }

    private void check(Cut cut) {
        if (!running || failed) throw new IllegalStateException("No active R31 ordinary-loop scope");
        ports.requireCurrent(cut, frame, assets);
    }

    private void requireIdle() {
        if (running) throw new IllegalStateException("R31 ordinary-loop reentry");
        if (failed || frame.closed()) throw new IllegalStateException("R31 ordinary-loop admission is closed");
    }
}
