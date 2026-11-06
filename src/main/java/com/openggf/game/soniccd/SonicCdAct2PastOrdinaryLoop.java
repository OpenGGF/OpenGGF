package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdR12bPlayerAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;

import java.util.Objects;
import java.util.Optional;

/**
 * Native Main333-432 / VInt_Level821-847 scheduling only, N1ecc5f3a.
 * Root owns actual source/World/epoch/thread/phase, Core/Host adoption, art publication,
 * physical dispatch and every reached data read. This kernel owns no native value,
 * clock, input algorithm, capture participant, object pool, renderer or completion DTO.
 */
final class SonicCdAct2PastOrdinaryLoop {
    enum Cut {
        BEFORE_SELECTOR8, PREPARE_INPUT, PUBLISH_LEVEL, UPDATE_ANIM_TILES, COPY_CAMERA_DRAW_LEVEL,
        SERVICE_SLOW_PLC, UPDATE_HUD, MAIN_MUSIC, SETTLE_SELECTED_CPU, ADVANCE_LEVEL_FRAME,
        SPAWN_OBJECTS, RUN_OBJECTS, POST_OBJECT_CAMERA, DRAW_OBJECTS, PALETTE_CYCLE_IF_TIME_RUNNING,
        UPDATE_SECTION_ART, PREPARE_PLC, UPDATE_GLOBAL_ANIMATIONS, RETURN
    }

    interface Ports {
        /** Validate real private invocation/source/World/epoch at every cut; active Frame callbacks cannot capture it. */
        void requireCurrent(Cut cut, CdPlayerFrameRuntime frame, CdR12bTerrainAssets terrain, CdR12bPlayerAssets player);
        /** Select VScroll and observe the existing old CPU; return its actual immutable borrowed reference. */
        Optional<CpuSat> beforeSelector8();
        /** Actual palette/HScroll/SAT publication after ReadControllers, before the Frame's bound Sonic art callback. */
        void publishLevel(Optional<CpuSat> selectedCpu);
        void updateAnimTiles();
        void copyCameraAndDrawLevel();
        void serviceSlowPlc();
        void updateHud();
        /** Root's represented Engine VBlank/music cut, after the Frame already completed Finish/count/pause. */
        void mainMusic();
        /** Settle the same selected CPU against legitimately updated art/planes; never reborrow a later CPU. */
        void settleSelectedCpu(Optional<CpuSat> selectedCpu);
        void spawnObjects();
        /** Actual live increasing physical walk/adoption; returning a constructed result cannot prove that walk. */
        void runObjects(int actualLevelFrame);
        /**
         * Main405-418: actual demo/restart checks, then death bounds or ordinary LevelScroll/feedback.
         * A reached LevelStart/unsupported transfer must not return ordinary-tail authority.
         */
        void postObjectCamera();
        /** Existing component ports retain their ordinary tail; actual World alone overrides the native transfer. */
        default boolean postObjectCameraTransferred(){postObjectCamera();return false;}
        void drawObjects();
        void paletteCycleIfTimeRunning();
        void updateSectionArt();
        void preparePlc();
        void updateGlobalAnimations();
    }

    private final CdPlayerFrameRuntime frame;
    private final CdR12bTerrainAssets terrain;
    private final CdR12bPlayerAssets player;
    private final Ports ports;
    private boolean running;
    private boolean failed;

    SonicCdAct2PastOrdinaryLoop(CdPlayerFrameRuntime frame, CdR12bTerrainAssets terrain,
                               CdR12bPlayerAssets player, Ports ports) {
        this.frame = Objects.requireNonNull(frame, "actual Frame");
        this.terrain = Objects.requireNonNull(terrain, "actual R12B terrain");
        this.player = Objects.requireNonNull(player, "actual R12B player");
        this.ports = Objects.requireNonNull(ports, "actual Root ports");
        if (!frame.stageSource().ownsAct2Past(terrain, player)) {
            throw new IllegalArgumentException("Ordinary loop requires the Frame's exact retained R12B pair");
        }
        requireIdle();
    }

    void run(int heldByte) {
        // Rejected/caught nested entry must not reach this run's failure catch or poison its valid outer scope.
        requireIdle();
        running = true;
        try {
            check(Cut.BEFORE_SELECTOR8);
            var selectedCpu = Objects.requireNonNull(ports.beforeSelector8(), "borrowed selected CPU observation");
            check(Cut.PREPARE_INPUT);
            var selected = frame.prepareInput(heldByte);
            boolean[] published = {false}; // Ephemeral completion observation, never an execution/capture authority.
            frame.serviceVBlank(selected.phase(),
                    () -> {
                        at(Cut.PUBLISH_LEVEL, () -> ports.publishLevel(selectedCpu));
                        published[0] = true; // Only after the actual publication callback completed.
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
            check(Cut.POST_OBJECT_CAMERA);
            if(ports.postObjectCameraTransferred())return; // Healthy native LevelStart transfer: no ordinary tail or failure retirement.
            at(Cut.DRAW_OBJECTS, ports::drawObjects);
            at(Cut.PALETTE_CYCLE_IF_TIME_RUNNING, ports::paletteCycleIfTimeRunning);
            at(Cut.UPDATE_SECTION_ART, ports::updateSectionArt);
            at(Cut.PREPARE_PLC, ports::preparePlc);
            at(Cut.UPDATE_GLOBAL_ANIMATIONS, ports::updateGlobalAnimations);
            check(Cut.RETURN);
        } catch (RuntimeException | Error original) {
            failed = true;
            // Root owns terminal Core/Host/Frame teardown. Preserve every actual prefix and this exact failure.
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
        if (!running || failed) throw new IllegalStateException("No active ordinary-loop scope");
        ports.requireCurrent(cut, frame, terrain, player);
    }

    private void requireIdle() {
        if (running) throw new IllegalStateException("Ordinary-loop reentry");
        if (failed || frame.closed()) throw new IllegalStateException("Ordinary-loop admission is closed");
    }
}
