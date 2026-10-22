package com.openggf.game.sonic2.competition;

import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameStateManager;
import com.openggf.game.CharacterKey;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.LevelState;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native REV01 competition ledger, controller slots and results transitions. */
public final class Sonic2CompetitionSession implements NativeCompetitionRoster,
        RewindSnapshottable<Sonic2CompetitionSession.Snapshot> {
    public enum Phase { SELECT, ACT, ACT_RESULTS, ZONE_RESULTS, GAME_RESULTS,
        SPECIAL_STAGE, SPECIAL_ACT_RESULTS, SPECIAL_ZONE_RESULTS, FINISHED }
    private final Sonic2CompetitionPlayerState[] progress = {
            new Sonic2CompetitionPlayerState(), new Sonic2CompetitionPlayerState()};
    private final AbstractPlayableSprite[] players = new AbstractPlayableSprite[2];
    private final Camera[] cameras = new Camera[2];
    // Native Results_Data_2P has three rows per zone; null represents the $FFFF unplayed word.
    private final List<Sonic2CompetitionResults.Wins> ledger = new ArrayList<>(Collections.nCopies(12, null));
    private Phase phase = Phase.SELECT;
    private int selection, zone, act, firstFinisher = -1, loserTimeLeft, specialIndex, specialSector;
    private int teleportTimer;
    private boolean spinComplete;
    private int timeOverMask;
    private int pendingTimeOverMask;
    private boolean gameOver;
    private int presentationFrame;
    private boolean resultsPlcReady;
    private List<Integer> specialRings = new ArrayList<>(Collections.nCopies(6, 0));
    private Sonic2CompetitionResults.Metrics p1Result, p2Result;

    public record Snapshot(Phase phase, int selection, int zone, int act, int firstFinisher,
                           int loserTimeLeft, boolean spinComplete, int specialIndex, int specialSector,
                           int teleportTimer, int timeOverMask, int pendingTimeOverMask, boolean gameOver, int presentationFrame,
                           boolean resultsPlcReady,
                           List<Integer> specialRings,
                           List<Sonic2CompetitionResults.Wins> ledger,
                           Sonic2CompetitionPlayerState.State player1, Sonic2CompetitionPlayerState.State player2,
                           Sonic2CompetitionResults.Metrics p1Result, Sonic2CompetitionResults.Metrics p2Result) {
        public Snapshot {
            specialRings = List.copyOf(specialRings);
            ledger = Collections.unmodifiableList(new ArrayList<>(ledger));
        }
    }

    public Phase phase() { return phase; }
    public int presentationFrame() { return presentationFrame; }
    public void advancePresentation() { presentationFrame++; }
    public boolean resultsPlcReady() { return resultsPlcReady; }
    public void setResultsPlcReady(boolean ready) { resultsPlcReady = ready; }
    public int selection() { return selection; }
    public int currentZone() { return zone; }
    public int act() { return act; }
    public int romZone() { return switch (zone) { case 0 -> 0; case 1 -> 11; case 2 -> 12; default -> -1; }; }
    public int firstFinisher() { return firstFinisher; }
    public int signpostFace() { return firstFinisher < 0 ? -1 : firstFinisher + 3; }
    public int loserTimeLeft() { return loserTimeLeft; }
    public int specialStageIndex() { return specialIndex; }
    public int specialSector() { return specialSector; }
    public List<Integer> specialRings() { return List.copyOf(specialRings); }
    public int teleportTimer() { return teleportTimer; }
    public boolean teleportWhite() { return teleportTimer >= 0x10; }
    public boolean timeOver(int slot) { checkSlot(slot); return (timeOverMask & (1 << slot)) != 0; }
    /** Actor owner consumes each KillCharacter request once; flags persist through the death card. */
    public int consumeTimeOverRequests() { int value = pendingTimeOverMask; pendingTimeOverMask = 0; return value; }
    public Sonic2CompetitionResults.Metrics resultMetrics(int slot) { checkSlot(slot); return slot == 0 ? p1Result : p2Result; }
    public Sonic2CompetitionPlayerState playerState(int slot) { checkSlot(slot); return progress[slot]; }
    public LevelState levelState(int slot) { return playerState(slot); }
    public GameStateManager gameState(int slot) { return playerState(slot).gameState(); }
    public AbstractPlayableSprite player(int slot) { checkSlot(slot); return players[slot]; }
    public Camera camera(int slot) { checkSlot(slot); return cameras[slot]; }
    public void bindPlayers(AbstractPlayableSprite sonic, AbstractPlayableSprite tails) {
        if (sonic == null || tails == null || sonic == tails || sonic.isCpuControlled() || tails.isCpuControlled()
                || !CharacterKey.SONIC.equals(sonic.characterKey()) || !CharacterKey.TAILS.equals(tails.characterKey()))
            throw new IllegalArgumentException("Native competition requires distinct human Sonic P1 and Tails P2");
        players[0] = sonic; players[1] = tails;
    }
    public void bindCameras(Camera p1, Camera p2) {
        if (p1 == null || p2 == null || p1 == p2) throw new IllegalArgumentException("Two independent cameras required");
        cameras[0] = p1; cameras[1] = p2;
    }
    private static void checkSlot(int slot) { if (slot < 0 || slot > 1) throw new IllegalArgumentException("slot " + slot); }
    public Sonic2CompetitionResults.Wins actResult(int zoneIndex, int row) {
        if (zoneIndex < 0 || zoneIndex > 3 || row < 0 || row > 2) throw new IllegalArgumentException("ledger row");
        return ledger.get(zoneIndex * 3 + row);
    }
    public boolean completed(int zoneIndex) { return actResult(zoneIndex, 0) != null && actResult(zoneIndex, 1) != null; }
    public Sonic2CompetitionResults.Wins zoneWins(int zoneIndex) { return countRowWins(zoneIndex * 3, 3); }
    public Sonic2CompetitionResults.Wins gameWins() { return countRowWins(0, 12); }
    private Sonic2CompetitionResults.Wins countRowWins(int offset, int count) {
        int one = 0, two = 0;
        for (int i = offset; i < offset + count; i++) {
            var row = ledger.get(i);
            if (row != null && row.winner() > 0) one++;
            else if (row != null && row.winner() < 0) two++;
        }
        return new Sonic2CompetitionResults.Wins(one, two);
    }

    /** Both control press bytes are ORed; opposite directions still toggle each axis just once. */
    public void updateMenu(LogicalInputSnapshot input, boolean ready) {
        if (input == null) return;
        int presses = input.player1().pressedMask() | input.player2().pressedMask();
        if (phase == Phase.SELECT) {
            if ((presses & 3) != 0) selection ^= 2;
            if ((presses & 12) != 0) selection ^= 1;
        }
        if (!ready || (!input.player1().startPressed() && !input.player2().startPressed())) return;
        switch (phase) {
            case SELECT -> {
                if (completed(selection)) return;
                zone = selection; act = 0;
                if (zone == 3) startSpecial(4); else startAct();
            }
            case ACT_RESULTS -> {
                if (act == 0) { act = 1; startAct(); }
                else if (countRowWins(zone * 3, 2).winner() == 0) { act = 2; startSpecial(zone + 1); }
                else phase = Phase.ZONE_RESULTS;
            }
            case SPECIAL_ACT_RESULTS -> {
                if (zone != 3) phase = Phase.ZONE_RESULTS;
                else if (act == 0 || (act == 1 && countRowWins(9, 2).winner() == 0)) {
                    act++; startSpecial(specialIndex + 1);
                } else phase = Phase.SPECIAL_ZONE_RESULTS;
            }
            case ZONE_RESULTS, SPECIAL_ZONE_RESULTS -> {
                boolean all = true;
                for (int z = 0; z < 4; z++) all &= completed(z);
                phase = all ? Phase.GAME_RESULTS : Phase.SELECT;
                if (!all && gameOver) {
                    Collections.fill(ledger, null);
                    for (var state : progress) state.resetSession();
                    gameOver = false;
                }
            }
            case GAME_RESULTS -> phase = Phase.FINISHED;
            default -> { }
        }
    }
    private void startAct() {
        for (var state : progress) state.resetAct();
        firstFinisher = -1; loserTimeLeft = 0; spinComplete = false;
        teleportTimer = timeOverMask = pendingTimeOverMask = 0;
        p1Result = p2Result = null;
        phase = Phase.ACT;
    }
    private void startSpecial(int index) {
        specialIndex = index; specialSector = 0;
        specialRings = new ArrayList<>(Collections.nCopies(6, 0));
        for (var state : progress) state.setRings(0);
        phase = Phase.SPECIAL_STAGE;
    }
    /** Obj0D_Init arms this when the signpost enters native object lifetime, before any arrival. */
    public void initializeSignpost() { if (phase == Phase.ACT) loserTimeLeft = 0x3C3C; }
    /** Called once after canonical LevelState.update; never advances either bank's timer itself. */
    public void advanceActFlow() {
        if (phase != Phase.ACT) return;
        if (progress[0].isTimeOver() && !timeOver(0)) {
            requestTimeOver(0);
            if (!progress[1].isTimerPaused()) requestTimeOver(1);
        }
        if (progress[1].isTimeOver() && !timeOver(1)) requestTimeOver(1);
        if (teleportTimer > 0) teleportTimer--;
        if (loserTimeLeft != 0 && (!progress[0].isTimerPaused() || !progress[1].isTimerPaused())) {
            int sub = (loserTimeLeft & 0xFF) - 1;
            if (sub > 0) loserTimeLeft = (loserTimeLeft & 0xFF00) | sub;
            else {
                int seconds = (loserTimeLeft >>> 8) - 1;
                if (seconds < 0) {
                    loserTimeLeft = 0;
                    if (!progress[0].isTimerPaused()) {
                        requestTimeOver(0);
                        if (!progress[1].isTimerPaused()) requestTimeOver(1);
                    } else if (!progress[1].isTimerPaused()) requestTimeOver(1);
                }
                else loserTimeLeft = (seconds << 8) | 60;
            }
        }
        tryActResults();
    }
    private void requestTimeOver(int slot) {
        progress[slot].markTimeOver();
        timeOverMask |= 1 << slot;
        pendingTimeOverMask |= 1 << slot;
    }
    /** Native actor/card owner calls this after Obj39 dismisses; timeout alone never finishes a race. */
    public void deathCardDismissed(int slot, boolean timedOut) {
        checkSlot(slot);
        if (phase != Phase.ACT) throw new IllegalStateException("No act active");
        if (timedOut) {
            timeOverMask = 0;
            publishActResults();
        } else {
            // Obj39_Check2PMode writes $FEFF for P1 game over, or $FFFE for P2.
            ledger.set(zone * 3 + act, slot == 0 ? new Sonic2CompetitionResults.Wins(254, 255)
                    : new Sonic2CompetitionResults.Wins(255, 254));
            gameOver = true;
            phase = Phase.ZONE_RESULTS;
        }
    }
    /** Obj0D_Main (s2.asm:34671-34740) reads unsigned centre-X minus signpost-X. */
    public boolean observeGoal(int slot, int centreX, int signpostX) {
        checkSlot(slot);
        if (phase != Phase.ACT || progress[slot].isTimerPaused() || ((centreX - signpostX) & 0xFFFF) >= 0x20) return false;
        progress[slot].pauseTimer();
        // Both Obj0D crossing branches restart the spin; the final arrival must finish its spin too.
        spinComplete = false;
        if (cameras[slot] != null) cameras[slot].setMinX(cameras[slot].getMaxX());
        if (firstFinisher < 0) { firstFinisher = slot; loserTimeLeft = 0x3C3C; }
        tryActResults();
        return true;
    }
    public void completeSignpostSpin() { spinComplete = true; tryActResults(); }
    private void tryActResults() {
        if (phase != Phase.ACT || timeOverMask != 0 || !spinComplete || !progress[0].isTimerPaused() || !progress[1].isTimerPaused()) return;
        publishActResults();
    }
    private void publishActResults() {
        p1Result = progress[0].metrics(); p2Result = progress[1].metrics();
        ledger.set(zone * 3 + act, Sonic2CompetitionResults.compare(p1Result, p2Result));
        phase = Phase.ACT_RESULTS;
    }
    /** Obj5A_VSReset records six words; each sector resets both independent ring counters. */
    public void finishSpecialSector() { recordSpecialSector(progress[0].getRings(), progress[1].getRings()); }
    public void recordSpecialSector(int p1Rings, int p2Rings) {
        if (phase != Phase.SPECIAL_STAGE || specialSector >= 3) throw new IllegalStateException("No competitive sector active");
        specialRings.set(specialSector * 2, p1Rings & 0xFFFF);
        specialRings.set(specialSector * 2 + 1, p2Rings & 0xFFFF);
        specialSector++;
        for (var state : progress) state.setRings(0);
        if (specialSector == 3) {
            int one = 0, two = 0;
            for (int i = 0; i < 6; i += 2) {
                int comparison = Integer.compare(specialRings.get(i), specialRings.get(i + 1));
                if (comparison > 0) one++; else if (comparison < 0) two++;
            }
            ledger.set(zone * 3 + act, new Sonic2CompetitionResults.Wins(one, two));
            phase = Phase.SPECIAL_ACT_RESULTS;
        }
    }
    public int monitorContents(int levelFrameCounter, boolean teleportOnly) {
        int content = teleportOnly ? 8 : (levelFrameCounter & 7) + 1;
        return content == 8 && (progress[0].isTimerPaused() || progress[1].isTimerPaused()) ? 7 : content;
    }
    /** Counter ownership follows native named-life routines, not always the breaker. */
    public void awardMonitor(int breaker, int content) {
        checkSlot(breaker);
        int owner = content == 1 ? 0 : content == 2 ? 1 : breaker;
        progress[owner].brokenMonitor();
        if (content == 1 || content == 2) gameState(owner).addLife();
        else if (content == 4) {
            int lives = gameState(breaker).getLives();
            progress[breaker].addRings(10);
            // REV01 super_ring branches to named 1-up, incrementing the monitor bank again.
            if (gameState(breaker).getLives() > lives) progress[breaker].brokenMonitor();
        }
    }
    public boolean teleport(Sonic2CompetitionHost host) {
        if (phase != Phase.ACT || progress[0].isTimerPaused() || progress[1].isTimerPaused()) return false;
        if (!host.swapNativeActorWorldState()) return false;
        teleportTimer = 0x40;
        host.freezeObjects(0x40);
        return true;
    }
    public void reset() {
        phase = Phase.SELECT; selection = zone = act = specialIndex = specialSector = teleportTimer = 0;
        firstFinisher = -1; loserTimeLeft = timeOverMask = pendingTimeOverMask = 0; spinComplete = gameOver = false;
        presentationFrame = 0;
        resultsPlcReady = false;
        Collections.fill(ledger, null); specialRings = new ArrayList<>(Collections.nCopies(6, 0));
        for (var state : progress) state.resetSession();
        p1Result = p2Result = null;
    }
    @Override public String key() { return "sonic2-native-competition"; }
    @Override public Snapshot capture() {
        return new Snapshot(phase, selection, zone, act, firstFinisher, loserTimeLeft, spinComplete,
                specialIndex, specialSector, teleportTimer, timeOverMask, pendingTimeOverMask, gameOver, presentationFrame,
                resultsPlcReady,
                specialRings, ledger,
                progress[0].capture(), progress[1].capture(), p1Result, p2Result);
    }
    @Override public void restore(Snapshot s) {
        phase = s.phase(); selection = s.selection(); zone = s.zone(); act = s.act();
        firstFinisher = s.firstFinisher(); loserTimeLeft = s.loserTimeLeft(); spinComplete = s.spinComplete();
        specialIndex = s.specialIndex(); specialSector = s.specialSector(); teleportTimer = s.teleportTimer();
        timeOverMask = s.timeOverMask(); pendingTimeOverMask = s.pendingTimeOverMask(); gameOver = s.gameOver();
        presentationFrame = s.presentationFrame();
        resultsPlcReady = s.resultsPlcReady();
        specialRings = new ArrayList<>(s.specialRings()); ledger.clear(); ledger.addAll(s.ledger());
        progress[0].restore(s.player1()); progress[1].restore(s.player2()); p1Result = s.p1Result(); p2Result = s.p2Result();
    }
    @Override public void resetForMissingSnapshot() { reset(); }
}
