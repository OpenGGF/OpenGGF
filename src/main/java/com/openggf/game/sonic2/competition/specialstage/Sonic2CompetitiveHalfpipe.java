package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameMusic;
import com.openggf.audio.GameSound;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter;
import com.openggf.game.sonic2.specialstage.CompetitiveSpecialStagePlayerAccess;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageObject;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageLagModel;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStagePlayer;
import com.openggf.game.sonic2.specialstage.Sonic2TrackFrameDecoder;
import com.openggf.graphics.GraphicsManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

/** ROM-backed two-human halfpipe; the host owns mode publication and rewind registration. */
public final class Sonic2CompetitiveHalfpipe implements Sonic2CompetitiveSpecialStageAdapter.Halfpipe,
        RewindSnapshottable<Sonic2CompetitiveHalfpipe.Snapshot>, AutoCloseable {
    public enum Phase { EMPTY, LOAD_HOLD, STARTUP_DRAW, STARTUP_DURATION, STARTUP_DMA, FADE_IN, INTRO, RUNNING, COMPLETE }
    private final CompetitiveHalfpipeRomData data;
    private final CompetitiveHalfpipeTrack track;
    private final CompetitiveHalfpipeRenderer renderer;
    private final AudioManager audio;
    private final IntSupplier verticalInterruptCount;
    private final CompetitiveHalfpipePalettes palettes;
    private CompetitiveSpecialStagePlayerAccess.Pair players;
    private CompetitiveHalfpipeObjectMachine machine;
    private Sonic2CompetitiveSpecialStageAdapter.Events events;
    private Phase phase = Phase.EMPTY;
    private int loadHoldFrames;
    private boolean liveLagSimulationEnabled = true;
    private int stage = -1, ticks, fadeTicks, perspectiveFrame;
    private int[] tiles;
    private int decodedFrame = -1;
    private boolean decodedFlip;
    private LogicalInputSnapshot previousInput = LogicalInputSnapshot.neutral();

    public record FlyingPart(int frame, int x, int y, int angle) { }
    public record Phrase(int timer, List<CompetitiveHalfpipeRenderer.MessageLetter> letters, List<Integer> angles) {
        public Phrase { letters = List.copyOf(letters); angles = List.copyOf(angles); }
    }
    public record Snapshot(Phase phase, int stage, int ticks, int fadeTicks, int loadHoldFrames, boolean liveLagSimulationEnabled, int lastLoadedSegment,
                           int objectGroup, int perspectiveFrame, int bannerY, int bannerRoutine,
                           int bannerTimer, int checkpointPhrase, CompetitiveHalfpipeTrack.State track,
                           CompetitiveHalfpipeCheckpoint.State checkpoint, Object players,
                           List<Object> objects, List<Phrase> phrases, List<FlyingPart> bannerParts,
                           CompetitiveHalfpipePalettes.State palettes, LogicalInputSnapshot previousInput, CompetitiveHalfpipeObjectMachine.State machine) {
        public Snapshot {
            objects = List.copyOf(objects); phrases = List.copyOf(phrases); bannerParts = List.copyOf(bannerParts);
        }
    }

    public Sonic2CompetitiveHalfpipe(CompetitiveHalfpipeRomData data, GraphicsManager graphics,
                                    SpecialStageViewport viewport, AudioManager audio, IntSupplier verticalInterruptCount) {
        this.data = Objects.requireNonNull(data, "data");
        this.audio = Objects.requireNonNull(audio, "audio");
        this.verticalInterruptCount = Objects.requireNonNull(verticalInterruptCount, "verticalInterruptCount");
        palettes = new CompetitiveHalfpipePalettes(data);
        track = new CompetitiveHalfpipeTrack(data);
        renderer = new CompetitiveHalfpipeRenderer(data, graphics, viewport);
    }
    @Override public boolean supportsCompetitiveStage(int index) { return index >= 0 && index < 7; }
    @Override public void loadCompetitiveStage(int index, Sonic2CompetitiveSpecialStageAdapter.Events events) {
        if (!supportsCompetitiveStage(index)) throw new IllegalArgumentException("Competitive stage " + index);
        reset(); this.events = Objects.requireNonNull(events, "events");
        try { renderer.initialize(index); }
        catch (IOException failure) { reset(); throw new UncheckedIOException(failure); }
        stage = index; palettes.load(index); track.load(index);
        players = CompetitiveSpecialStagePlayerAccess.create();
        machine = new CompetitiveHalfpipeObjectMachine(data, players, audio, events);
        machine.load(index);
        // SpecialStage (s2.asm:6613-6669): synchronous load precedes the
        // two S2SS wait loops and the single CtrlDMA wait. No controllers yet.
        loadHoldFrames = Sonic2SpecialStageLagModel.ENTRY_LOAD_LAG_FRAMES;
        phase = Phase.LOAD_HOLD;
    }
    /** Disable only for externally paced callers and logical-pass unit tests. */
    public void setLiveLagSimulationEnabled(boolean enabled) { liveLagSimulationEnabled = enabled; }

    @Override public void step(InputHandler input) {
        Objects.requireNonNull(input, "input");
        if (phase == Phase.EMPTY || phase == Phase.COMPLETE) return;
        ticks++;
        if (phase == Phase.LOAD_HOLD) {
            if (--loadHoldFrames == 0) phase = Phase.STARTUP_DRAW;
            return;
        }
        // The presentation/V-int clock advances even when Vint_Lag holds the
        // picture (VintRet, s2.asm:507-508), as in the one-player manager.
        if (liveLagSimulationEnabled && (phase == Phase.INTRO || phase == Phase.RUNNING)
                && Sonic2SpecialStageLagModel.shouldSkipLiveUpdate(
                ticks, 12, track.geometry())) {
            previousInput = LogicalInputSnapshot.ofPlayers(withoutEdges(previousInput.player1()),
                    withoutEdges(previousInput.player2()));
            return;
        }
        if (phase == Phase.STARTUP_DRAW) {
            track.tickPreRoll();
            if (track.drawingIndex() == 0) phase = Phase.STARTUP_DURATION;
            return;
        }
        if (phase == Phase.STARTUP_DURATION) {
            track.tickPreRoll(); machine.prepareSegment(track);
            if (track.capture().durationTimer() == 1) {
                track.scrollAfterPreRoll(); perspectiveFrame = track.frame();
                machine.initializeHiddenMessages(); machine.runPass(track, perspectiveFrame, ignored -> { });
                phase = Phase.STARTUP_DMA;
            }
            return;
        }
        if (phase == Phase.STARTUP_DMA) {
            fadeTicks = 22; phase = Phase.FADE_IN; audio.playMusic(GameMusic.SPECIAL_STAGE);
            return;
        }
        if (phase == Phase.FADE_IN) {
            palettes.fadeStep();
            if (--fadeTicks == 0) phase = Phase.INTRO;
            return;
        }
        boolean cyclePalette = phase == Phase.RUNNING;
        // SpecialStage's pre-start loop copies controllers before WaitForVint;
        // its running loop copies them afterwards (s2.asm:6674-6721).
        var logical = phase == Phase.INTRO ? previousInput : input.logical(); previousInput = input.logical();
        track.tick(); machine.prepareSegment(track);
        if (track.drawingIndex() == 4) perspectiveFrame = track.frame();
        machine.runPass(track, perspectiveFrame, slot -> tickPlayer(slot, slot == 0 ? logical.player1() : logical.player2()));
        if (machine.started()) phase = Phase.RUNNING;
        if (cyclePalette) palettes.tick(verticalInterruptCount.getAsInt(), machine.rainbowActive());
        if (machine.complete()) {
            phase = Phase.COMPLETE;
            // RunObjects has already cleared every native object and displayed slot.
            events.stageComplete();
        }
    }
    private static PlayerInputState withoutEdges(PlayerInputState state) {
        return PlayerInputState.of(state.heldMask(), 0, state.actionHeldMask(), 0, state.startHeld(), false);
    }
    private void tickPlayer(int slot, PlayerInputState input) {
        var player = player(slot);
        boolean spillEdge = player.isHurt() && player.getHurtTimer() == 0;
        boolean wasHurt = player.isHurt();
        int held = (input.heldMask() & 15) | InputActionMasks.toMegaDriveButtonBits(input.actionHeldMask());
        int pressed = (input.pressedMask() & 15) | InputActionMasks.toMegaDriveButtonBits(input.actionPressedMask());
        players.step(slot, held, pressed, track.animationTimer());
        if (!wasHurt && player.isHurt() && player.getRings() != 0) audio.playSfx(GameSound.RING_SPILL);
        // Allocation owns the one-shot timer-$08 attempt. A full table keeps
        // the rings; a later Obj5B init owns the debit and particle allocations.
        if (spillEdge && player.getHurtTimer() == 8) machine.requestSpill(slot);
    }
    @Override public void draw() {
        if (phase == Phase.EMPTY) throw new IllegalStateException("No competitive halfpipe loaded");
        if (decodedFrame != track.frame() || decodedFlip != track.flipped()) {
            tiles = Sonic2TrackFrameDecoder.decodeFrame(data.trackFrame(track.frame()), track.flipped());
            decodedFrame = track.frame(); decodedFlip = track.flipped();
        }
        renderer.setGeometryOffset(track.offsetX(), track.offsetY()); renderer.setPalettes(palettes.palettes());
        if (phase == Phase.COMPLETE) renderer.drawCleared(tiles, track.bgScroll(), track.vScroll());
        else if (phase == Phase.LOAD_HOLD || phase == Phase.STARTUP_DRAW
                || phase == Phase.STARTUP_DURATION || phase == Phase.STARTUP_DMA) renderer.drawCleared(tiles, track.bgScroll(), track.vScroll());
        else renderer.drawNative(tiles, track.bgScroll(), track.vScroll(), players.players(), machine.renderFrame());
    }
    public Phase phase() { return phase; }
    public int stage() { return stage; }
    public int ticks() { return ticks; }
    public Sonic2SpecialStagePlayer player(int slot) {
        if (players == null) throw new IllegalStateException("No competitive players loaded");
        return players.player(slot);
    }
    public List<Sonic2SpecialStageObject> objects() { return machine == null ? List.of() : machine.objects(); }
    CompetitiveHalfpipeObjectMachine objectMachine() { return machine; }
    @Override public String key() { return "sonic2.competition.halfpipe"; }
    @Override public Snapshot capture() {
        return new Snapshot(phase, stage, ticks, fadeTicks, loadHoldFrames, liveLagSimulationEnabled, machine == null ? -1 : machine.stream().capture().lastSegment(),
                machine == null ? 0 : machine.stream().capture().publishedCursor(), perspectiveFrame, 0, 0, 0, -1,
                track.capture(), machine == null ? null : machine.checkpoint().capture(), players == null ? null : players.capture(),
                objects().stream().map(CompetitiveHalfpipeItems::capture).toList(), List.of(), List.of(), palettes.capture(), previousInput,
                machine == null ? null : machine.capture());
    }
    @Override public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.phase() == Phase.EMPTY) { reset(); return; }
        if (events == null) throw new IllegalStateException("Bind competitive events before restoring a loaded stage");
        if (stage != snapshot.stage()) {
            try { renderer.initialize(snapshot.stage()); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        phase = snapshot.phase(); stage = snapshot.stage(); ticks = snapshot.ticks(); fadeTicks = snapshot.fadeTicks();
        loadHoldFrames = snapshot.loadHoldFrames(); liveLagSimulationEnabled = snapshot.liveLagSimulationEnabled();
        perspectiveFrame = snapshot.perspectiveFrame(); track.restore(snapshot.track());
        if (players == null) players = CompetitiveSpecialStagePlayerAccess.create(); players.restore(snapshot.players());
        if (machine == null) machine = new CompetitiveHalfpipeObjectMachine(data, players, audio, events);
        machine.restore(Objects.requireNonNull(snapshot.machine(), "native object RAM"));
        palettes.restore(snapshot.palettes()); previousInput = snapshot.previousInput(); decodedFrame = -1;
    }
    @Override public void reset() {
        loadHoldFrames = 0;
        renderer.reset(); phase = Phase.EMPTY; stage = -1; ticks = fadeTicks = perspectiveFrame = 0;
        players = null; machine = null; events = null; decodedFrame = -1; tiles = null; decodedFlip = false;
        previousInput = LogicalInputSnapshot.neutral();
    }
    @Override public void resetForMissingSnapshot() { reset(); }
    @Override public void close() { reset(); }
}
