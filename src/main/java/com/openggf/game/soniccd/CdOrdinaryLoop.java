package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.CpuSat;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;

import java.util.Objects;
import java.util.Optional;

/**
 * Shared standard Main333-432 / VInt_Level821-847 scheduling, N1ecc5f3a.
 * Original source-bound Worlds supply their actual primary, zone and transfer owners.
 * Tidal deferred service uses the same Main coordinator with its typed original Frame owner.
 * The real private ports own source/World/epoch/thread/current pair and every reached
 * producer. This component owns no native values, input/clock algorithm, pool,
 * capture participant, current art, physical walk or World invocation authority.
 */
final class CdOrdinaryLoop {
    enum Cut {
        BEFORE_SELECTOR8, PREPARE_INPUT, PUBLISH_LEVEL, UPDATE_ANIM_TILES, COPY_CAMERA_DRAW_LEVEL,
        SERVICE_SLOW_PLC, UPDATE_HUD, MAIN_MUSIC, SETTLE_SELECTED_CPU, ADVANCE_LEVEL_FRAME,
        SPAWN_OBJECTS, RUN_OBJECTS, POST_OBJECT_CAMERA, DRAW_OBJECTS, READ_TIME_STOP_WORD,
        PALETTE_CYCLE, UPDATE_SECTION_ART, PREPARE_PLC, UPDATE_GLOBAL_ANIMATIONS, RETURN
    }

    /** Completed private owner outcome; the enum itself grants no transfer authority. */
    enum MainContinuation { ORDINARY_TAIL, TRANSFERRED }

    interface Ports {
        /** Actual private invocation/source/epoch/current-pair validation at each reached cut. */
        void requireCurrent(Cut cut, CdPlayerFrameRuntime frame, CdStagePlayerSource source);
        default Optional<CdPlayerFrameRuntime.OriginalProgrammeOwner> originalProgrammeOwner(){return Optional.empty();}
        /** Standard retained PPZ VBlank owner, distinct from TT deferred-water service. */
        default Optional<CdPlayerFrameRuntime.OriginalProgrammeOwner> originalStandardProgrammeOwner(){return Optional.empty();}
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
         * Main405-418: actual demo/restart tests, then death bounds or zone events/scroll.
         * TRANSFERRED requires the actual owner to have completed its native transfer prefix.
         * A reached unowned lifecycle action throws its original typed fault before the tail.
         */
        MainContinuation postObjectCamera();
        void drawObjects();
        /** Reached TST.W timeStopTimer (Main423), not a byte/boolean projection. */
        int timeStopWord();
        void paletteCycle();
        /** Actual source section owner, including a source-proven RTS programme. */
        void updateSectionArt();
        void preparePlc();
        void updateGlobalAnimations();
    }

    private final CdPlayerFrameRuntime frame;
    private final CdStagePlayerSource source;
    private final Ports ports;
    private boolean running;
    private boolean failed;

    CdOrdinaryLoop(CdPlayerFrameRuntime frame, CdStagePlayerSource source, Ports ports) {
        this.frame = Objects.requireNonNull(frame, "actual Frame");
        this.source = Objects.requireNonNull(source, "actual retained stage source");
        this.ports = Objects.requireNonNull(ports, "actual private World ports");
        if (frame.stageSource() != source) {
            throw new IllegalArgumentException("Ordinary loop requires the Frame's exact retained source");
        }
        requireIdle();
    }

    void run(int heldByte) {
        // A caught nested entry or invalid idle input must not poison a healthy outer run.
        requireIdle();
        if ((heldByte & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native held byte");
        var water=Objects.requireNonNull(ports.originalProgrammeOwner(),"water programme selection");
        var standard=Objects.requireNonNull(ports.originalStandardProgrammeOwner(),"standard programme selection");
        if(water.isPresent()&&standard.isPresent())throw new IllegalStateException("Mutually exclusive ordinary programme owners required");
        running = true;
        try {
            check(Cut.BEFORE_SELECTOR8);
            var selectedCpu = Objects.requireNonNull(ports.beforeSelector8(), "borrowed selected CPU observation");
            check(Cut.PREPARE_INPUT);
            var selected = frame.prepareInput(heldByte);
            boolean[] published = {false}; // Ephemeral completed callback observation, never owner/capture authority.
            if(standard.isPresent()){frame.serviceOriginalStandardProgrammeVBlank(selected.phase(),standard.orElseThrow());published[0]=selected.phase()!=com.openggf.game.resources.PlcLifecyclePhase.LAG;}
            else if(water.isPresent()){frame.serviceOriginalProgrammeVBlank(selected.phase(),water.orElseThrow());published[0]=selected.phase()!=com.openggf.game.resources.PlcLifecyclePhase.LAG;}
            else frame.serviceVBlank(selected.phase(),
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
            check(Cut.POST_OBJECT_CAMERA);
            if (Objects.requireNonNull(ports.postObjectCamera(), "actual Main continuation")
                    == MainContinuation.TRANSFERRED) return;
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
        if (!running || failed) throw new IllegalStateException("No active Ordinary-loop scope");
        ports.requireCurrent(cut, frame, source);
    }

    private void requireIdle() {
        if (running) throw new IllegalStateException("Ordinary-loop reentry");
        if (failed || frame.closed()) throw new IllegalStateException("Ordinary-loop admission is closed");
    }
}
