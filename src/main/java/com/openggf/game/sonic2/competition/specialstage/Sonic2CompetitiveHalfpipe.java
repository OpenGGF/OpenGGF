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
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageBomb;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageObject;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStagePlayer;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRing;
import com.openggf.game.sonic2.specialstage.Sonic2TrackFrameDecoder;
import com.openggf.graphics.GraphicsManager;
import com.openggf.physics.TrigLookupTable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * ROM-backed two-human halfpipe. This owner never constructs an ordinary stage manager,
 * runs its CPU controller, or enters the ordinary ring-requirement/emerald campaign.
 * The host owns mode publication and registers this instance with its rewind registry.
 */
public final class Sonic2CompetitiveHalfpipe implements Sonic2CompetitiveSpecialStageAdapter.Halfpipe,
        RewindSnapshottable<Sonic2CompetitiveHalfpipe.Snapshot>, AutoCloseable {
    public enum Phase { EMPTY, FADE_IN, INTRO, RUNNING, COMPLETE }
    private final CompetitiveHalfpipeRomData data;
    private final CompetitiveHalfpipeTrack track;
    private final CompetitiveHalfpipeRenderer renderer;
    private final com.openggf.game.sonic2.specialstage.Sonic2PerspectiveData perspective;
    private final AudioManager audio;
    private final IntSupplier verticalInterruptCount;
    private final CompetitiveHalfpipePalettes palettes;
    private final List<Sonic2SpecialStageObject> objects = new ArrayList<>();
    private final List<Phrase> phrases = new ArrayList<>();
    private final List<FlyingPart> bannerParts = new ArrayList<>();
    private CompetitiveSpecialStagePlayerAccess.Pair players;
    private CompetitiveHalfpipeCheckpoint checkpoint;
    private Sonic2CompetitiveSpecialStageAdapter.Events events;
    private boolean terminalRequested;
    private final Sonic2CompetitiveSpecialStageAdapter.Events checkpointEvents = new Sonic2CompetitiveSpecialStageAdapter.Events() {
        @Override public void collectedRing(int slot) { events.collectedRing(slot); }
        @Override public void hitBomb(int slot) { events.hitBomb(slot); }
        @Override public void checkpoint() { events.checkpoint(); }
        @Override public void archivedSector(int sector, int p1, int p2) { events.archivedSector(sector, p1, p2); }
        @Override public void tiedSector(int sector) { events.tiedSector(sector); }
        @Override public void stageComplete() { terminalRequested = true; }
    };
    private Phase phase = Phase.EMPTY;
    private int stage = -1, ticks, fadeTicks, lastLoadedSegment, objectGroup, perspectiveFrame;
    private int bannerY, bannerRoutine, bannerTimer, checkpointPhrase;
    private int[] tiles;
    private int decodedFrame = -1;
    private boolean decodedFlip;
    private LogicalInputSnapshot previousInput = LogicalInputSnapshot.neutral();

    public record FlyingPart(int frame, int x, int y, int angle) { }
    public record Phrase(int timer, List<CompetitiveHalfpipeRenderer.MessageLetter> letters, List<Integer> angles) {
        public Phrase { letters = List.copyOf(letters); angles = List.copyOf(angles); }
    }
    public record Snapshot(Phase phase, int stage, int ticks, int fadeTicks, int lastLoadedSegment,
                           int objectGroup, int perspectiveFrame, int bannerY, int bannerRoutine,
                           int bannerTimer, int checkpointPhrase, CompetitiveHalfpipeTrack.State track,
                           CompetitiveHalfpipeCheckpoint.State checkpoint, Object players,
                           List<Object> objects, List<Phrase> phrases, List<FlyingPart> bannerParts,
                           CompetitiveHalfpipePalettes.State palettes, LogicalInputSnapshot previousInput) {
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
        perspective = CompetitiveHalfpipeItems.perspective(data);
    }

    @Override public boolean supportsCompetitiveStage(int index) { return index >= 0 && index < 7; }
    @Override public void loadCompetitiveStage(int index, Sonic2CompetitiveSpecialStageAdapter.Events events) {
        if (!supportsCompetitiveStage(index)) throw new IllegalArgumentException("Competitive stage " + index);
        reset();
        this.events = Objects.requireNonNull(events, "events");
        try { renderer.initialize(index); }
        catch (IOException failure) { reset(); throw new UncheckedIOException(failure); }
        stage = index;
        palettes.load(index);
        track.load(index);
        players = CompetitiveSpecialStagePlayerAccess.create();
        checkpoint = new CompetitiveHalfpipeCheckpoint(data);
        lastLoadedSegment = -1;
        checkpointPhrase = -1;
        // SpecialStage (s2.asm:6645-6674): draw the first mapping, then prepare
        // the initial object group at slice four before the first RunObjects.
        do { track.tickPreRoll(); } while (track.drawingIndex() != 0);
        do {
            track.tickPreRoll();
            loadSegment();
        } while (track.capture().durationTimer() != 1);
        track.scrollAfterPreRoll();
        perspectiveFrame = track.frame();
        bannerY = -0x40; bannerRoutine = 2;
        phrase(8);
        tickBanner(); // Obj5F_Init falls through to its first movement.
        tickItems();
        fadeTicks = 22; // Pal_FadeFromWhite's $15 DBF loop, using CtrlDMA rather than Vint_S2SS.
        phase = Phase.FADE_IN;
        audio.playMusic(GameMusic.SPECIAL_STAGE);
    }

    @Override public void step(InputHandler input) {
        Objects.requireNonNull(input, "input");
        if (phase == Phase.EMPTY || phase == Phase.COMPLETE) return;
        ticks++;
        if (phase == Phase.FADE_IN) {
            previousInput = input.logical();
            palettes.fadeStep();
            if (--fadeTicks == 0) phase = Phase.INTRO;
            return;
        }
        boolean cyclePalette = phase == Phase.RUNNING;
        // The pre-start loop copies logical controllers before its VInt wait;
        // the running loop copies them after it (SpecialStage, s2.asm:6674-6721).
        var logical = phase == Phase.INTRO ? previousInput : input.logical();
        previousInput = input.logical();
        track.tick();
        loadSegment();
        if (track.drawingIndex() == 4) perspectiveFrame = track.frame();
        tickPhrases();
        // Both logical samples enter their own actor directly. Obj10's CPU
        // control returns immediately for SS_2p_Flag (s2.asm:70576-70584).
        tickPlayer(0, logical.player1());
        tickPlayer(1, logical.player2());
        tickBanner();
        tickItems();
        int oldSector = checkpoint.sector();
        var action = checkpoint.tick(track.previousFrame(), track.drawingIndex() == 4,
                player(0).getRings(), player(1).getRings(), checkpointEvents);
        if (checkpoint.sector() != oldSector) audio.playSfx(GameSound.CHECKPOINT);
        if (action.archived()) players.resetRings();
        if (action.fadeMusic()) audio.fadeOutMusic();
        if (cyclePalette) palettes.tick(verticalInterruptCount.getAsInt(),
                checkpoint.capture().phase() == CompetitiveHalfpipeCheckpoint.Phase.RAINBOW);
        int message = checkpoint.overlay().phraseId();
        if (message != checkpointPhrase) {
            checkpointPhrase = message;
            if (message >= 0) phrase(message);
        }
        if (checkpoint.complete()) {
            objects.clear(); bannerParts.clear(); phrases.clear();
            players.clearPlayers();
            phase = Phase.COMPLETE;
            if (!terminalRequested) throw new IllegalStateException("Native terminal event was not requested");
            terminalRequested = false;
            // RingCheckTrigger clears Object_RAM and the displayed SAT before
            // the host is told to leave this world. No callback sees live actors.
            events.stageComplete();
        }
    }

    private void tickPlayer(int slot, PlayerInputState input) {
        var player = player(slot);
        boolean spilling = player.isHurt() && player.getHurtTimer() == 0;
        boolean wasHurt = player.isHurt();
        int held = (input.heldMask() & 0x0F) | InputActionMasks.toMegaDriveButtonBits(input.actionHeldMask());
        int pressed = (input.pressedMask() & 0x0F)
                | InputActionMasks.toMegaDriveButtonBits(input.actionPressedMask());
        players.step(slot, held, pressed, track.animationTimer());
        if (!wasHurt && player.isHurt() && player.getRings() != 0) audio.playSfx(GameSound.RING_SPILL);
        // SSHurt_Animation creates Obj5B at its first $08 angle, one actor
        // pass after entering hurt; that object's first update owns ring loss.
        if (spilling && player.getHurtTimer() == 8) {
            players.loseRings(slot);
            events.hitBomb(slot);
        }
    }

    private void loadSegment() {
        if (track.drawingIndex() != 4 || track.segment() == lastLoadedSegment) return;
        lastLoadedSegment = track.segment();
        var layout = data.segments(stage);
        if (lastLoadedSegment >= layout.size() || objectGroup >= layout.size()) return;
        var group = layout.get(objectGroup++);
        int length = track.segmentLength(layout.get(lastLoadedSegment).geometry());
        for (var item : group.items()) {
            objects.add(CompetitiveHalfpipeItems.create(data, item.bomb(),
                    (length + item.distance()) * 4, item.angle()));
        }
        if (group.marker() == 0xFE || group.marker() == 0xFD) checkpoint.request();
    }

    private void tickItems() {
        for (var object : objects) {
            CompetitiveHalfpipeItems.advance(object, perspectiveFrame, perspective,
                    track.flipped(), track.drawingIndex() == 4);
            if ((object.isRing() || object.isOnScreen()) && object.isCollidable()) {
                int first = player(0).getSSZPos() < player(1).getSSZPos() ? 0 : 1;
                if (!touch(object, first)) touch(object, 1 - first);
            }
            CompetitiveHalfpipeItems.afterTouch(object);
        }
        objects.removeIf(Sonic2SpecialStageObject::shouldRemove);
    }

    private boolean touch(Sonic2SpecialStageObject object, int slot) {
        var player = player(slot);
        if (player.getRoutine() != Sonic2SpecialStagePlayer.RoutineState.NORMAL || player.isHurt()) return false;
        int radius = object.isRing() ? 10 : 8;
        // Obj61_TestCollision compares wrapped bytes and excludes the upper edge.
        if (((player.getAngle() - object.getAngle() + radius) & 0xFF) >= radius * 2) return false;
        if (object instanceof Sonic2SpecialStageRing ring && ring.collect()) {
            players.collectRing(slot); events.collectedRing(slot); audio.playSfx(GameSound.RING);
        } else if (object instanceof Sonic2SpecialStageBomb bomb && bomb.explode()) {
            player.triggerHit();
            // Blinking is not a bomb-touch gate: the actor rejects a queued
            // hurt later, but the bomb still explodes (Obj61_TestCollision).
            audio.playSfx(GameSound.SLOW_SMASH);
        } else return false;
        return true;
    }

    private void tickBanner() {
        tickFlyingParts();
        if (bannerRoutine == 2) {
            if (++bannerY == 0x48) { bannerRoutine = 4; bannerTimer = 15; }
        } else if (bannerRoutine == 4 && --bannerTimer == 0) {
            // Obj5F uses the original frame2 piece X offsets for CalcAngle.
            int mapping = 0x7240 + data.rom().readU16BE(0x7244);
            for (int i = 0; i < 7; i++) {
                int x = (short) data.rom().readU16BE(mapping + 8 + i * 10);
                int angle = TrigLookupTable.calcAngle((short) x, (short) -0x28);
                bannerParts.add(new FlyingPart(i + 2, 0x80, bannerY, angle));
            }
            tickFlyingParts(); // Newly allocated later slots run during this same object pass.
            bannerRoutine = 10; bannerTimer = 30;
        } else if (bannerRoutine == 10 && --bannerTimer < 0) {
            bannerRoutine = 0; phase = Phase.RUNNING; phrase(10);
        }
    }

    private void tickFlyingParts() {
        for (int i = bannerParts.size() - 1; i >= 0; i--) {
            var part = bannerParts.get(i);
            int x = part.x() + (TrigLookupTable.cosHex(part.angle()) * 8 >> 8);
            int y = part.y() + (TrigLookupTable.sinHex(part.angle()) * 8 >> 8);
            if (x < 0 || x > 256 || y < 0) bannerParts.remove(i);
            else bannerParts.set(i, new FlyingPart(part.frame(), x, y, part.angle()));
        }
    }

    private void phrase(int id) {
        var letters = renderer.decodePhrase(id);
        phrases.add(new Phrase(70, letters, letters.stream().map(letter -> TrigLookupTable.calcAngle(
                (short) (letter.x() - 0x80), (short) (letter.y() - 0x70))).toList()));
    }
    private void tickPhrases() {
        for (int i = phrases.size() - 1; i >= 0; i--) {
            var phrase = phrases.get(i);
            if (phrase.timer() > 0) { phrases.set(i, new Phrase(phrase.timer() - 1, phrase.letters(), phrase.angles())); continue; }
            var letters = new ArrayList<CompetitiveHalfpipeRenderer.MessageLetter>();
            var angles = new ArrayList<Integer>();
            for (int index = 0; index < phrase.letters().size(); index++) {
                var letter = phrase.letters().get(index);
                int angle = phrase.angles().get(index);
                int x = letter.x() + (TrigLookupTable.cosHex(angle) * 8 >> 8);
                int y = letter.y() + (TrigLookupTable.sinHex(angle) * 8 >> 8);
                if (x >= 0 && x <= 256 && y >= 0) {
                    letters.add(new CompetitiveHalfpipeRenderer.MessageLetter(letter.mappingFrame(), x, y));
                    angles.add(angle);
                }
            }
            if (letters.isEmpty()) phrases.remove(i); else phrases.set(i, new Phrase(0, letters, angles));
        }
    }

    @Override public void draw() {
        if (phase == Phase.EMPTY) throw new IllegalStateException("No competitive halfpipe loaded");
        if (decodedFrame != track.frame() || decodedFlip != track.flipped()) {
            tiles = Sonic2TrackFrameDecoder.decodeFrame(data.trackFrame(track.frame()), track.flipped());
            decodedFrame = track.frame(); decodedFlip = track.flipped();
        }
        renderer.setGeometryOffset(track.offsetX(), track.offsetY());
        renderer.setPalettes(palettes.palettes());
        renderer.setSectorWins(checkpoint.wins(0), checkpoint.wins(1));
        if (phase == Phase.COMPLETE) { renderer.drawCleared(tiles, track.bgScroll(), track.vScroll()); return; }
        var overlay = checkpoint.overlay();
        renderer.draw(perspectiveFrame, tiles, track.bgScroll(), track.vScroll(), players.players(), objects,
                player(0).getRings(), player(1).getRings(), new CompetitiveHalfpipeRenderer.Overlay(
                        bannerRoutine == 2 || bannerRoutine == 4, bannerY, -1,
                        overlay.checkpointHand(), overlay.handY(), overlay.winner(), overlay.rainbow(),
                        bannerParts.stream().map(p -> new CompetitiveHalfpipeRenderer.BannerPart(p.frame(), p.x(), p.y())).toList(),
                        phrases.stream().flatMap(p -> p.letters().stream()).toList()));
    }

    public Phase phase() { return phase; }
    public int stage() { return stage; }
    public int ticks() { return ticks; }
    public Sonic2SpecialStagePlayer player(int slot) {
        if (players == null) throw new IllegalStateException("No competitive players loaded");
        return players.player(slot);
    }
    public List<Sonic2SpecialStageObject> objects() { return List.copyOf(objects); }
    @Override public String key() { return "sonic2.competition.halfpipe"; }
    @Override public Snapshot capture() {
        return new Snapshot(phase, stage, ticks, fadeTicks, lastLoadedSegment, objectGroup, perspectiveFrame,
                bannerY, bannerRoutine, bannerTimer, checkpointPhrase, track.capture(),
                checkpoint == null ? null : checkpoint.capture(), players == null ? null : players.capture(),
                objects.stream().map(CompetitiveHalfpipeItems::capture).toList(), phrases, bannerParts, palettes.capture(), previousInput);
    }
    @Override public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.phase() == Phase.EMPTY) { reset(); return; }
        if (events == null) throw new IllegalStateException("Bind competitive events before restoring a loaded stage");
        if (stage != snapshot.stage()) {
            try { renderer.initialize(snapshot.stage()); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        phase = snapshot.phase(); stage = snapshot.stage(); ticks = snapshot.ticks(); fadeTicks = snapshot.fadeTicks();
        lastLoadedSegment = snapshot.lastLoadedSegment(); objectGroup = snapshot.objectGroup();
        perspectiveFrame = snapshot.perspectiveFrame(); bannerY = snapshot.bannerY(); bannerRoutine = snapshot.bannerRoutine();
        bannerTimer = snapshot.bannerTimer(); checkpointPhrase = snapshot.checkpointPhrase();
        track.restore(snapshot.track());
        if (players == null) players = CompetitiveSpecialStagePlayerAccess.create();
        players.restore(snapshot.players());
        if (checkpoint == null) checkpoint = new CompetitiveHalfpipeCheckpoint(data);
        checkpoint.restore(snapshot.checkpoint());
        palettes.restore(snapshot.palettes());
        previousInput = snapshot.previousInput();
        terminalRequested = false;
        objects.clear(); for (Object object : snapshot.objects()) objects.add(CompetitiveHalfpipeItems.restore(data, object));
        phrases.clear(); phrases.addAll(snapshot.phrases()); bannerParts.clear(); bannerParts.addAll(snapshot.bannerParts());
        decodedFrame = -1;
    }
    @Override public void reset() {
        renderer.reset(); phase = Phase.EMPTY; stage = -1; ticks = fadeTicks = 0;
        objects.clear(); phrases.clear(); bannerParts.clear(); players = null; checkpoint = null; events = null;
        lastLoadedSegment = -1; objectGroup = perspectiveFrame = bannerY = bannerRoutine = bannerTimer = 0;
        checkpointPhrase = decodedFrame = -1; tiles = null; decodedFlip = false;
        previousInput = LogicalInputSnapshot.neutral();
        terminalRequested = false;
    }
    @Override public void resetForMissingSnapshot() { reset(); }
    @Override public void close() { reset(); }
}
