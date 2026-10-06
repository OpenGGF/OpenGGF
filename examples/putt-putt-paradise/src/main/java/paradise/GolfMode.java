package paradise;

import com.openggf.control.*;
import com.openggf.game.GameServices;
import com.openggf.game.mode.*;
import com.openggf.game.presentation.*;
import com.openggf.game.rewind.RewindSnapshottable;
import paradise.model.*;
import paradise.net.*;
import paradise.ui.*;
import paradise.presentation.ShotReplay;
import paradise.presentation.GolfPoseClock;
import java.util.*;

/** One live course, independent golfer worlds, and a ledger outside course rollback. */
public final class GolfMode implements GameplayFrameController, RewindSnapshottable<GolfMode.State> {
    public record State(GolfMatch.State match, ShotMeter.State meter, List<CourseCheckpoint> lies,
                        CourseCheckpoint neutralLie, long tick, int initialSteps, int watchSteps,
                        int dwell, int previousX, int previousY, boolean paused, boolean startHeld,
                        boolean inputBlocked, boolean survey, int surveyX, int surveyY, int pauseRow, boolean cHeld, String connectionError, boolean landingSpinPending,
                        RewindAllowance.State allowance, ShotReplay.State replay,
                        boolean rewindHeld, boolean rewindQueued, GolfPoseClock.State poseClock,
                        TurnReadiness.State readiness, GolfFeedback.State feedback, int startX, List<Integer> lieX,
                        List<Integer> shownTotals, int pauseAge, int guestHole) {
        public State { lies = List.copyOf(lies); lieX = List.copyOf(lieX); shownTotals = List.copyOf(shownTotals); }
    }
    private GolfMatch match;
    private ShotMeter meter = new ShotMeter();
    private RewindAllowance allowance = new RewindAllowance(RewindAllowance.Rules.defaults());
    private final ShotReplay replay = new ShotReplay();
    private final GolfPoseClock poseClock = new GolfPoseClock();
    private final TurnReadiness readiness = new TurnReadiness();
    private final GolfFeedback feedback = new GolfFeedback();
    private boolean rewindHeld, rewindQueued;
    private final CourseCheckpoint[] lies = new CourseCheckpoint[2];
    private CourseCheckpoint neutralLie;
    private long tick, renderRevision;
    private int initialSteps, watchSteps, dwell, previousX, previousY, remoteFeedback;
    private boolean paused, startHeld, inputBlocked, survey, cHeld, titleRequest, landingSpinPending;
    private int surveyX, surveyY, pauseRow, pauseAge, guestHole;
    // Buttons held on the current row; a turn opened while one is held needs it released first.
    private boolean actionHeld;
    // Course progress: hole start and each golfer's latest lie (course x, -1 unknown).
    private int startX = -1;
    private final int[] lieX = {-1, -1}, shownTotals = {-1, -1};
    private GolfMenu.Selection selection;
    private int finishX, finishY, viewport = 320;
    private GolfOnline online;
    private SceneViewPresenter presenter;
    private String connectionError = "";
    // Derived each row from the engine's live bindings; never part of course or match state.
    private String actionLabel = "", rewindLabel = "";

    public void configure(GolfMenu.Selection choice) {
        close(); selection = choice; match = null; neutralLie = null; Arrays.fill(lies, null);
        meter = new ShotMeter(); paused = false; startHeld = true; inputBlocked = true; landingSpinPending = false;
        survey = false; cHeld = false; titleRequest = false; tick = 0; initialSteps = 0;
        surveyX = surveyY = pauseRow = pauseAge = guestHole = 0; connectionError = "";
        allowance = new RewindAllowance(choice.rewinds()); replay.clear(); rewindHeld = rewindQueued = false; poseClock.reset();
        readiness.newHole(); feedback.restore(new GolfFeedback().snapshot());
        startX = -1; Arrays.fill(lieX, -1); Arrays.fill(shownTotals, -1);
    }
    public void finishGate(int x, int y) { finishX = x; finishY = y; }
    public int finishX() { return finishX; }
    public int finishY() { return finishY; }
    @Override public String key() { return "mode:putt-putt-paradise"; }
    @Override public State capture() {
        var saved = new ArrayList<CourseCheckpoint>();
        if (lies[0] != null) saved.add(lies[0]);
        if (lies[1] != null) saved.add(lies[1]);
        return new State(match == null ? null : match.snapshot(), meter.snapshot(), saved, neutralLie,
                tick, initialSteps, watchSteps, dwell, previousX, previousY, paused, startHeld,
                inputBlocked, survey, surveyX, surveyY, pauseRow, cHeld, connectionError, landingSpinPending, allowance.snapshot(),
                replay.snapshot(), rewindHeld, rewindQueued, poseClock.snapshot(), readiness.snapshot(), feedback.snapshot(),
                startX, List.of(lieX[0], lieX[1]), List.of(shownTotals[0], shownTotals[1]), pauseAge, guestHole);
    }
    @Override public void restore(State state) {
        if (state.match() == null) match = null;
        else {
            match = state.match().mode() == GolfMatch.Mode.PRACTICE
                    ? GolfMatch.practice(state.match().golfers().getFirst().character(), state.match().initialActIndex())
                    : GolfMatch.competition(state.match().golfers().get(0).character(), state.match().golfers().get(1).character());
            match.restore(state.match());
        }
        meter.restore(state.meter()); Arrays.fill(lies, null);
        for (int i = 0; i < state.lies().size(); i++) lies[i] = state.lies().get(i);
        neutralLie = state.neutralLie(); tick = state.tick(); initialSteps = state.initialSteps();
        watchSteps = state.watchSteps(); dwell = state.dwell(); previousX = state.previousX();
        previousY = state.previousY(); paused = state.paused(); startHeld = state.startHeld();
        inputBlocked = state.inputBlocked(); survey = state.survey(); surveyX = state.surveyX();
        surveyY = state.surveyY(); pauseRow = state.pauseRow(); cHeld = state.cHeld(); connectionError = state.connectionError(); landingSpinPending = state.landingSpinPending();
        allowance.restore(state.allowance()); replay.restore(state.replay());
        rewindHeld = state.rewindHeld(); rewindQueued = state.rewindQueued();
        poseClock.restore(state.poseClock()); readiness.restore(state.readiness()); feedback.restore(state.feedback());
        startX = state.startX(); lieX[0] = state.lieX().get(0); lieX[1] = state.lieX().get(1);
        shownTotals[0] = state.shownTotals().get(0); shownTotals[1] = state.shownTotals().get(1);
        pauseAge = state.pauseAge(); guestHole = state.guestHole();
    }
    public GolfMatch.State matchState() { return match == null ? null : match.snapshot(); }
    public ShotMeter.State shotState() { return meter.snapshot(); }
    public TurnReadiness.State readinessState() { return readiness.snapshot(); }
    public GolfRoom.State roomState() { return online == null ? null : online.state(); }
    @Override public boolean presentationPaused() { return paused || online != null && online.state().held(); }
    @Override public RewindPresentation rewindPresentation() {
        if (presentationPaused()) return RewindPresentation.NONE;
        boolean remoteRewind = online != null && !online.state().host() && online.shotStatus() != null
                && online.shotStatus().phase() == GolfPacket.ShotPhase.REWINDING;
        return replay.playing() || remoteRewind
                ? new RewindPresentation(1, replay.playing() ? Math.min(4, replay.speed()) : 1)
                : RewindPresentation.NONE;
    }
    // Shot undo owns live rewind input in every mode, including when its allowance is off.
    @Override public boolean allowsDebugRewind() { return false; }
    @Override public boolean consumeTitleRequest() { boolean requested = titleRequest; titleRequest = false; return requested; }

    private PlayerInputState controls(LogicalInputSnapshot input) {
        // Either local pad operates the active golfer; one merged edge stream prevents double shots.
        var one = input.player1(); var two = input.player2();
        return new PlayerInputState(one.heldMask() | two.heldMask(), one.pressedMask() | two.pressedMask(),
                one.actionHeldMask() | two.actionHeldMask(), one.actionPressedMask() | two.actionPressedMask(),
                one.startHeld() || two.startHeld(), one.startPressed() || two.startPressed());
    }
    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
        refreshLabels(course);
        boolean advanced = updateBeforeTick(course, input);
        poseClock.update(poseKind(course), !presentationPaused());
        pauseAge = paused ? pauseAge + 1 : 0;
        updatePresentation();
        return advanced;
    }

    /**
     * The incoming golfer's prompt names their own action button: P1 and P2 bindings locally,
     * this machine's player online. A P2 without a binding or pad falls back to P1's, because
     * either local pad operates the active golfer.
     */
    private void refreshLabels(CourseControl course) {
        int owner = readiness.owner() >= 0 ? readiness.owner() : match == null ? 0 : match.snapshot().activePlayer();
        int player = online == null ? owner : 0;
        var label = course.buttonLabel(player, ButtonPrompts.Button.A);
        if (label.isEmpty() && player == 1) label = course.buttonLabel(0, ButtonPrompts.Button.A);
        actionLabel = label.orElse("").toUpperCase(Locale.ROOT);
        rewindLabel = course.buttonLabel(player, ButtonPrompts.Button.REWIND).orElse("").toUpperCase(Locale.ROOT);
    }

    private boolean updateBeforeTick(CourseControl course, LogicalInputSnapshot input) {
        tick++; viewport = course.viewportWidth();
        boolean entryFadeActive = neutralLie == null && course.advanceEntryPresentation();
        var playerInput = controls(input);
        actionHeld = playerInput.actionHeldMask() != 0;
        boolean rewindPressed = course.rewindHeld() && !rewindHeld; rewindHeld = course.rewindHeld();
        if (rewindPressed && ownsShot() && canRewind()) rewindQueued = true;
        boolean start = playerInput.startHeld();
        if (online == null && connectionError.isEmpty() && selection != null
                && (selection.mode() == GolfMenu.Mode.HOST || selection.mode() == GolfMenu.Mode.JOIN)) {
            try { online = new GolfOnline(selection, course); }
            catch (java.io.IOException | IllegalArgumentException failure) { connectionError = "ROOM: " + failure.getMessage(); }
        }
        // A guest never advances its own course, so it dismisses the native entry art here. Held
        // rows still count the ROM's entry music down, so the guest hears the zone music on time.
        if (online != null && !online.state().host()) course.finishInitialPresentation();
        if (online != null) {
            for (var event : online.tick()) if (event instanceof GolfRoom.Received received) {
                var packet = received.packet();
                if (packet instanceof GolfPacket.Leave) online.receive(packet);
                if (!online.state().host()) guestReceive(packet);
                else hostReceive(course, packet);
            }
            if (online.state().ended() && match != null && match.snapshot().status() == GolfMatch.Status.PLAYING)
                match.concede(online.concededOwner() >= 0 ? online.concededOwner() : 1);
        }
        if (start && !startHeld) {
            if (results()) titleRequest = true;
            else { paused = !paused; pauseRow = 0; inputBlocked = true; readiness.disarm(); if (online != null) online.pause(paused); }
        }
        startHeld = start;
        if (paused && rewindQueued) { paused = false; if (online != null) online.pause(false); }
        if (paused) {
            int change = (playerInput.pressedMask() & 2) != 0 ? 1 : (playerInput.pressedMask() & 1) != 0 ? -1 : 0;
            pauseRow = Math.floorMod(pauseRow + change, 4);
            if ((playerInput.actionPressedMask() & InputActionMasks.ACTION_B) != 0) { paused = false; if (online != null) online.pause(false); }
            else if ((playerInput.actionPressedMask() & InputActionMasks.ACTION_A) != 0) {
                if (pauseRow == 0) { paused = false; if (online != null) online.pause(false); }
                else if (pauseRow == 1) {
                    if (ownsShot() && canRewind()) { rewindQueued = true; paused = false; if (online != null) online.pause(false); }
                }
                else if (pauseRow == 2) {
                    if (match != null) match.concede(online == null ? match.snapshot().activePlayer() : online.state().localOwner());
                    if (online != null) online.concede();
                    paused = false;
                }
                else titleRequest = true;
            }
            return false;
        }
        if (!connectionError.isEmpty() || online != null && (!online.state().ready() || online.state().held() || online.complete())) return false;
        if (online != null && !online.state().host()) {
            var status = online.shotStatus();
            if (rewindQueued) { online.control(GolfPacket.ShotAction.REWIND); rewindQueued = false; }
            if (status != null && status.phase() == GolfPacket.ShotPhase.REWINDING) return false;
            return guestTick(course, playerInput);
        }
        if (match == null) {
            if (selection == null || selection.mode() == GolfMenu.Mode.PRACTICE)
                match = GolfMatch.practice(character(course.playerState().character()), course.actIndex());
            else match = GolfMatch.competition(character(online == null ? selection.playerOne().code() : online.state().hostCharacter()),
                    character(online == null ? selection.playerTwo().code() : online.state().guestCharacter()));
        }
        if (match.snapshot().status() != GolfMatch.Status.PLAYING) return false;
        if (neutralLie == null) {
            course.finishInitialPresentation();
            var ball = course.playerState();
            // Settle the initial lie through the entry fade, then hold its world while
            // the native text exits. Capturing later must not run extra course physics.
            return initialSteps == 0 || entryFadeActive || !ball.floorSupport() || Math.abs(ball.groundSpeed()) > 0x40;
        }
        if (rewindQueued) { beginRewind(course); rewindQueued = false; }
        if (replay.playing()) {
            if (replay.step()) finishRewind(course);
            return false;
        }
        if (readiness.waiting()) {
            // The course stays held. Only the incoming golfer's own fresh A confirms; online,
            // a guest's turn waits for its accepted READY instead of host input.
            boolean viewerOwns = online == null || match.snapshot().activePlayer() == 0;
            if (viewerOwns && readiness.press(playerInput.actionHeldMask() != 0,
                    (playerInput.actionPressedMask() & InputActionMasks.ACTION_A) != 0)) confirmReady();
            return false;
        }
        if (online != null && match.snapshot().activePlayer() == 1 && match.snapshot().pending() == null) return false;
        if (remoteFeedback > 0 && --remoteFeedback == 0) charge();
        return meterTick(course, online != null && match.snapshot().activePlayer() == 1 ? PlayerInputState.neutral() : playerInput, false);
    }

    /** Host acceptance of a readiness confirmation, local or remote: one authoritative AIM publication. */
    private void confirmReady() {
        readiness.accept(); inputBlocked = true; feedback.ready();
        publishStatus(GolfPacket.ShotPhase.AIM);
    }

    private void hostReceive(CourseControl course, GolfPacket packet) {
        if (packet instanceof GolfPacket.ShotRequest request && match != null) {
            boolean currentTurn = neutralLie != null && online.wire(match.nextShotId()).equals(request.id());
            if (currentTurn && readiness.waiting()) online.rejectShot(request.id(), "not ready");
            else if (currentTurn && !paused && meter.snapshot().stage() == ShotMeter.Stage.AIM
                    && match.snapshot().activePlayer() == 1 && online.accepts(request, tick)) {
                var shot = new GolfShot(request.facing(), request.elevationDegrees(), request.normalizedPower(), request.spin());
                startRecording(course); match.commit(match.nextShotId(), shot);
                publishStatus(GolfPacket.ShotPhase.CHARGING);
                meter.restore(new ShotMeter.State(ShotMeter.Stage.FEEDBACK, shot.direction(), shot.elevationDegrees(),
                        0, shot.normalizedPower(), shot.spin(), shot.spin(), GolfRules.CHARGE_FEEDBACK_TICKS, true, false, shot, false));
                remoteFeedback = shot.isPutt() ? 0 : 2; inputBlocked = false; charge(); feedback.committed();
            }
        }
        if (packet instanceof GolfPacket.ShotControl control && match != null && control.id().owner() == 1
                && !paused && !online.state().held() && match.snapshot().status() == GolfMatch.Status.PLAYING) {
            var pending = match.snapshot().pending();
            if (control.action() == GolfPacket.ShotAction.READY) {
                // Owner-only, current-turn-only and idempotent: duplicates and stale turns only re-publish status.
                if (readiness.waiting() && readiness.owner() == 1 && match.snapshot().activePlayer() == 1 && pending == null
                        && neutralLie != null && online.wire(match.nextShotId()).equals(control.id())) confirmReady();
                else if (neutralLie != null) publishStatus(currentPhase());
            } else if (pending != null && online.wire(pending.id()).equals(control.id())) {
                beginRewind(course);
                if (match.snapshot().pending() != null) publishStatus(currentPhase());
            }
        }
    }

    private void guestReceive(GolfPacket packet) {
        var old = online.turn(); online.receive(packet);
        if (packet instanceof GolfPacket.Rejected rejected && Objects.equals(rejected.id(), online.turn())) {
            meter = new ShotMeter(); inputBlocked = true;
        }
        if (packet instanceof GolfPacket.TurnOpened opened && !Objects.equals(old, online.turn())) {
            meter = new ShotMeter(); inputBlocked = true; survey = false;
            boolean newHole = opened.id().hole() != guestHole;
            int owner = opened.id().owner();
            if (newHole) { guestHole = opened.id().hole(); startX = opened.lieX(); Arrays.fill(lieX, -1); }
            if (readiness.owner() != owner) feedback.clearTrail();
            readiness.adopt(owner, opened.readyRequired(), newHole, actionHeld);
            lieX[owner] = opened.lieX();
            if (opened.readyRequired()) feedback.handoff();
            else if (newHole) feedback.holeIntro();
        }
        if (packet instanceof GolfPacket.ShotStatus status && status.id().equals(online.turn())) {
            if (status.phase() != GolfPacket.ShotPhase.HANDOFF && readiness.waiting()) {
                // The host accepted readiness: the confirming press must be released before aiming.
                readiness.accept(); inputBlocked = true; feedback.ready();
            } else if (status.phase() == GolfPacket.ShotPhase.HANDOFF) readiness.reopen();
        }
        if (packet instanceof GolfPacket.TurnCommitted committed) {
            int owner = committed.id().owner();
            lieX[owner] = committed.lieX();
            var score = owner == 0 ? committed.player0() : committed.player1();
            switch (committed.outcome()) {
                case FINISHED -> feedback.finished(owner, score.strokes() + score.penalties(),
                        committed.player0().finished() && committed.player1().finished());
                case PENALTY -> feedback.penalty(GolfFeedback.Toast.PENALTY);
                case REWOUND -> feedback.refunded(online.shotStatus() == null ? 0 : online.shotStatus().holeRemaining());
                default -> { }
            }
        }
    }

    private GolfMatch.Character character(String code) { return "tails".equals(code) ? GolfMatch.Character.TAILS : GolfMatch.Character.SONIC; }
    private boolean guestTick(CourseControl course, PlayerInputState input) {
        if (online.state().owner() != 1 || online.turn() == null) return false;
        if (readiness.waiting()) {
            // A guest asks; only the host's AIM status (guestReceive) accepts. One request per press.
            if (readiness.press(input.actionHeldMask() != 0, (input.actionPressedMask() & InputActionMasks.ACTION_A) != 0)
                    && !online.control(GolfPacket.ShotAction.READY)) readiness.reopen();
            return false;
        }
        if (meter.snapshot().stage() == ShotMeter.Stage.FEEDBACK && !online.guestAccepted()) return false;
        meterTick(course, input, true); return false;
    }
    private boolean meterTick(CourseControl course, PlayerInputState input, boolean guest) {
        if (!guest) GolfSwing.resumeVerticalDrive(meter.snapshot());
        int held = input.heldMask(), actions = input.actionHeldMask();
        if (inputBlocked) {
            if (actions == 0 && !input.startHeld()) inputBlocked = false;
            return meter.snapshot().stage() == ShotMeter.Stage.WATCH && !guest;
        }
        boolean c = (actions & InputActionMasks.ACTION_C) != 0;
        if (c && !cHeld && meter.snapshot().stage() == ShotMeter.Stage.AIM) { survey = !survey; surveyX = surveyY = 0; }
        cHeld = c;
        if (survey) {
            surveyX = Math.clamp(surveyX + (((held & 8) != 0 ? 1 : 0) - ((held & 4) != 0 ? 1 : 0)) * 4, -viewport / 2, viewport / 2);
            surveyY = Math.clamp(surveyY + (((held & 2) != 0 ? 1 : 0) - ((held & 1) != 0 ? 1 : 0)) * 4, -112, 112);
            return false;
        }
        var controls = new ShotMeter.Input((held & 1) != 0, (held & 2) != 0, (held & 4) != 0, (held & 8) != 0,
                (actions & InputActionMasks.ACTION_A) != 0, (actions & InputActionMasks.ACTION_B) != 0);
        for (var event : meter.tick(controls)) switch (event.kind()) {
            case DUCK -> feedback.dismissIntro();
            case CANCEL -> { }
            case CHARGE -> { if (guest) GolfSwing.chargeSound(); else charge(); }
            case COMMIT -> {
                feedback.committed();
                if (guest) { if (!online.submit(event.shot())) meter = new ShotMeter(); }
                else {
                    startRecording(course); match.commit(match.nextShotId(), event.shot());
                    if (online != null) online.acceptLocal(event.shot(), tick);
                    publishStatus(GolfPacket.ShotPhase.CHARGING);
                }
            }
            case RELEASE -> {
                if (!guest) {
                    launch(course, event.shot());
                    if (online != null) online.sound(tick, "release"); watchSteps = dwell = 0;
                    publishStatus(GolfPacket.ShotPhase.WATCH);
                }
            }
        }
        return meter.snapshot().stage() == ShotMeter.Stage.WATCH && !guest;
    }
    private boolean ownsShot() {
        if (online != null && !online.state().host()) return online.state().owner() == 1 && online.guestAccepted();
        return match != null && match.snapshot().pending() != null && (online == null || match.snapshot().activePlayer() == 0);
    }
    private boolean canRewind() {
        if (online != null && !online.state().host()) return online.shotStatus() != null && online.shotStatus().canRewind();
        return match != null && match.snapshot().pending() != null && meter.snapshot().stage() == ShotMeter.Stage.WATCH
                && !replay.playing() && allowance.available(match.snapshot().pending().id());
    }
    private ScenePresentationFrame recordScene() {
        return paradise.presentation.GolfScene.withFinishFlag(GameServices.level().captureScene(0,
                PlayerPresentationPose.nativePose(), 0, 0), finishX, finishY, feedback.clock());
    }
    private void startRecording(CourseControl course) {
        replay.clear(); feedback.clearTrail();
        if (allowance.available(match.nextShotId())) {
            var originPose = course.playerState().rolling() ? PlayerPresentationPose.nativePose()
                    : new PlayerPresentationPose(PlayerPresentationPose.Kind.IDLE, 0, meter.snapshot().direction());
            replay.record(0, paradise.presentation.GolfScene.withFinishFlag(
                    GameServices.level().captureScene(0, originPose, 0, 0), finishX, finishY, feedback.clock()));
        }
    }
    private GolfPacket.ShotPhase currentPhase() {
        return replay.playing() ? GolfPacket.ShotPhase.REWINDING
                : meter.snapshot().stage() == ShotMeter.Stage.WATCH ? GolfPacket.ShotPhase.WATCH
                : match.snapshot().pending() != null ? GolfPacket.ShotPhase.CHARGING
                : readiness.waiting() ? GolfPacket.ShotPhase.HANDOFF : GolfPacket.ShotPhase.AIM;
    }
    private void publishStatus(GolfPacket.ShotPhase phase) {
        if (online != null && online.state().host() && match != null) {
            var id = match.snapshot().pending() == null ? match.nextShotId() : match.snapshot().pending().id();
            online.status(id, phase, allowance);
        }
    }
    private boolean beginRewind(CourseControl course) {
        if (!canRewind() || neutralLie == null) return false;
        var id = match.snapshot().pending().id();
        if (!allowance.spend(id)) return false;
        replay.begin(watchSteps, recordScene()); survey = false; landingSpinPending = false; feedback.clearTrail();
        // The ROM sound driver keeps playing through the reverse view; a global SFX stop would also
        // silence the zone music until the next sound effect.
        inputBlocked = true; publishStatus(GolfPacket.ShotPhase.REWINDING); return true;
    }
    private void finishRewind(CourseControl course) {
        var id = match.snapshot().pending().id(); course.restore(neutralLie);
        if (match.rewind(id) != GolfMatch.Decision.ACCEPTED) throw new IllegalStateException("Rewound shot lost its ledger");
        if (online != null) online.rewound(match.snapshot(), id, course.playerState());
        feedback.refunded(allowance.holeRemaining(id.player(), id.actIndex()));
        openTurn(course);
    }
    private void launch(CourseControl course, GolfShot shot) {
        GolfSwing.launch(course, shot);
        landingSpinPending = !shot.isPutt() && shot.spin() != 0;
    }
    private void charge() { GolfSwing.chargeSound(); if (online != null) online.sound(tick, "charge"); }
    @Override public boolean retainRolling() { return meter.snapshot().stage() == ShotMeter.Stage.WATCH; }
    @Override public void afterTick(CourseControl course, boolean advanced) {
        if (advanced) {
            applyLandingSpin(course);
            if (match != null && match.snapshot().pending() != null && allowance.available(match.snapshot().pending().id())
                    && replay.wantsSample(watchSteps + 1)) replay.record(watchSteps + 1, recordScene());
            observe(course);
            if (meter.snapshot().stage() == ShotMeter.Stage.WATCH) {
                var ball = course.playerState(); feedback.trail(ball.x(), ball.y());
                if (match != null) lieX[match.snapshot().activePlayer()] = ball.x();
            }
        }
        else if (neutralLie == null && match != null && !presentationPaused()) observe(course);
        if (online != null && online.state().host() && tick % 3 == 0) {
            if (replay.playing()) online.publish(tick, replay.frame(0));
            else online.publish(tick, pose(), finishX, finishY, feedback.clock());
        }
        refreshLabels(course); // A turn opened in this row prompts with its new golfer's binding.
    }
    private void applyLandingSpin(CourseControl course) {
        if (!landingSpinPending || meter.snapshot().stage() != ShotMeter.Stage.WATCH) return;
        if (GolfSwing.applyLandingSpin(course, meter.snapshot().shot())) landingSpinPending = false;
    }
    private void observe(CourseControl course) {
        var ball = course.playerState();
        if (neutralLie == null) {
            initialSteps++;
            if (course.presentationReady() && ball.floorSupport() && Math.abs(ball.groundSpeed()) <= 0x40) {
                // Native EHZ2 opens its final camera bound during the boss escape
                // (loc_2F460). The golf course has no boss, so admit its ROM gate
                // directly before any lie is captured. The 160px half-screen is
                // the native 320px design width used by player boundary physics.
                var camera = GameServices.camera();
                camera.setMaxX((short) Math.max(camera.getMaxX(), finishX - 160));
                var initial = course.capture();
                for (int i = 0; i < match.snapshot().golfers().size(); i++) {
                    course.restore(initial); course.selectCharacter(match.snapshot().golfers().get(i).character().name().toLowerCase(Locale.ROOT));
                    lies[i] = course.capture();
                    lieX[i] = ball.x();
                }
                startX = ball.x();
                course.restore(lies[match.snapshot().activePlayer()]); openTurn(course);
            } else if (initialSteps >= 600) { connectionError = "COURSE SPAWN DID NOT SETTLE"; }
            return;
        }
        watchSteps++;
        boolean still = ball.floorSupport() && Math.abs(ball.groundSpeed()) <= 0x40
                && Math.abs(ball.x() - previousX - ball.supportDx()) <= 1 && Math.abs(ball.y() - previousY - ball.supportDy()) <= 1;
        dwell = still ? dwell + 1 : 0;
        var candidates = new GolfOutcome.Candidates(ball.hurt(), ball.dead(),
                crossesGate(previousX, previousY, ball.x(), ball.y(), finishX, finishY), dwell >= GolfRules.SETTLE_DWELL_TICKS,
                ball.y() > course.courseMaxY() + GolfRules.LOST_BALL_MARGIN, watchSteps >= GolfRules.WATCHDOG_TICKS);
        previousX = ball.x(); previousY = ball.y();
        if (GolfOutcome.choose(candidates) == GolfOutcome.NONE) return;
        var pending = match.snapshot().pending(); if (pending == null) return;
        resolveShot(course, candidates);
    }
    private void resolveShot(CourseControl course, GolfOutcome.Candidates candidates) {
        var pending = match.snapshot().pending(); if (pending == null) return;
        int owner = pending.id().player(); var result = match.resolve(pending.id(), candidates);
        if (result.decision() != GolfMatch.Decision.ACCEPTED) return;
        if (result.outcome().isPenalty()) course.restore(neutralLie);
        else if (result.outcome() == GolfOutcome.SETTLED) course.settle();
        lies[owner] = course.capture(); lieX[owner] = course.playerState().x();
        announce(owner, result);
        if (online != null) online.committed(match.snapshot(), pending.id(), result.outcome(), course.playerState());
        if (result.holeAdvanced()) {
            course.loadLevel(0, 1); neutralLie = null; Arrays.fill(lies, null); meter = new ShotMeter(); initialSteps = 0;
            readiness.newHole(); startX = -1; Arrays.fill(lieX, -1); feedback.clearTrail(); return;
        }
        if (match.snapshot().status() == GolfMatch.Status.PLAYING) {
            course.restore(lies[match.snapshot().activePlayer()]); openTurn(course);
        }
    }
    /** Presentation for one accepted outcome; the ledger has already decided everything. */
    private void announce(int owner, GolfMatch.Resolution result) {
        var golfer = match.snapshot().golfers().get(owner);
        switch (result.outcome()) {
            case FINISH -> feedback.finished(owner, golfer.holes().get(result.holeAdvanced() ? 0 : match.snapshot().actIndex()).total(),
                    result.holeAdvanced() || match.snapshot().status() != GolfMatch.Status.PLAYING);
            case DAMAGE -> feedback.penalty(GolfFeedback.Toast.PENALTY_DAMAGE);
            case LOST_BALL -> feedback.penalty(GolfFeedback.Toast.PENALTY_LOST);
            case WATCHDOG -> feedback.penalty(GolfFeedback.Toast.PENALTY_TIME);
            default -> { }
        }
    }
    /** Liang-Barsky segment clipping prevents diagonal bounding-box false finishes. */
    public static boolean crossesGate(int x0, int y0, int x1, int y1, int gateX, int gateY) {
        double lo = 0, hi = 1; double dx = x1 - x0, dy = y1 - y0;
        double[] p = {-dx, dx, -dy, dy}, q = {x0 - gateX, gateX + 32 - x0, y0 - (gateY - 96), gateY + 48 - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) { if (q[i] < 0) return false; }
            else { double t = q[i] / p[i]; if (p[i] < 0) lo = Math.max(lo, t); else hi = Math.min(hi, t); if (lo > hi) return false; }
        }
        return true;
    }
    private void openTurn(CourseControl course) {
        neutralLie = course.capture(); int owner = match.snapshot().activePlayer(); lies[owner] = neutralLie;
        meter = new ShotMeter(); watchSteps = dwell = 0; remoteFeedback = 0; landingSpinPending = false;
        poseClock.reset(); poseClock.update(poseKind(course), false);
        replay.clear(); rewindQueued = false;
        survey = false; inputBlocked = true; previousX = course.playerState().x(); previousY = course.playerState().y();
        // Competition holds a new golfer's turn for their ready press; a retry or a lone golfer never waits.
        boolean firstOfHole = readiness.snapshot().lastOwner() < 0;
        if (readiness.snapshot().lastOwner() != owner) feedback.clearTrail();
        boolean waiting = readiness.open(owner, selection != null && selection.mode() != GolfMenu.Mode.PRACTICE, actionHeld);
        if (waiting) feedback.handoff(); else if (firstOfHole) feedback.holeIntro();
        lieX[owner] = course.playerState().x();
        if (online != null) {
            online.open(match.snapshot(), match.nextShotId(), course.playerState(), waiting);
            publishStatus(waiting ? GolfPacket.ShotPhase.HANDOFF : GolfPacket.ShotPhase.AIM);
        }
    }
    private PlayerPresentationPose.Kind poseKind(CourseControl course) {
        var m = meter.snapshot();
        // A fresh roster starts at a blank native mapping; HOLD must not tick
        // physics to make it visible. Settled balls retain their displayed pose.
        // Guests keep the host scene in AIM; their local world never simulates the lie.
        if (m.stage() == ShotMeter.Stage.AIM && (online == null || online.state().host())
                && !course.playerState().rolling())
            return PlayerPresentationPose.Kind.IDLE;
        var kind = m.stage() == ShotMeter.Stage.SPIN
                || (m.stage() == ShotMeter.Stage.POWER && m.elevationDegrees() == 0) ? PlayerPresentationPose.Kind.DUCK
                : m.stage() == ShotMeter.Stage.POWER || m.stage() == ShotMeter.Stage.FEEDBACK || m.stage() == ShotMeter.Stage.PRE_RELEASE
                ? PlayerPresentationPose.Kind.SPINDASH : PlayerPresentationPose.Kind.NATIVE;
        return kind;
    }

    private PlayerPresentationPose pose() {
        return poseClock.pose(meter.snapshot().direction());
    }
    @Override public boolean drawScene() {
        if (online != null && !online.state().host()) return online.draw(surveyX, surveyY, pose());
        if (match == null) return false;
        if (presenter == null) presenter = GameServices.level().createScenePresenter();
        presenter.accept(replay.playing() ? replay.frame(++renderRevision) : paradise.presentation.GolfScene.withFinishFlag(
                GameServices.level().captureScene(++renderRevision, pose(), survey ? viewport / 2 : 0, survey ? 112 : 0),
                finishX, finishY, feedback.clock()));
        presenter.draw(surveyX, surveyY); return true;
    }
    private boolean results() { return match != null && match.snapshot().status() != GolfMatch.Status.PLAYING || online != null && online.complete() || !connectionError.isEmpty(); }

    // ---- presentation ------------------------------------------------------------------------
    private List<GolfOverlay.PlayerScore> scores() {
        var scores = new ArrayList<GolfOverlay.PlayerScore>();
        if (match != null) {
            var s = match.snapshot();
            for (var golfer : s.golfers()) scores.add(new GolfOverlay.PlayerScore(golfer.character().name(),
                    golfer.holes().stream().mapToInt(GolfMatch.HoleScore::strokes).sum(), golfer.holes().stream().mapToInt(GolfMatch.HoleScore::penalties).sum(),
                    golfer.holes().get(s.actIndex()).finished(), golfer.dnf()));
        } else if (online != null && online.state().hostCharacter() != null && online.state().guestCharacter() != null) {
            var s = online.state(); int pending = online.unpublishedStroke();
            scores.add(new GolfOverlay.PlayerScore(s.hostCharacter().toUpperCase(Locale.ROOT), s.player0().strokes() + (pending == 0 ? 1 : 0),
                    s.player0().penalties(), s.player0().finished(), online.concededOwner() == 0));
            scores.add(new GolfOverlay.PlayerScore(s.guestCharacter().toUpperCase(Locale.ROOT), s.player1().strokes() + (pending == 1 ? 1 : 0),
                    s.player1().penalties(), s.player1().finished(), online.concededOwner() == 1));
        }
        if (scores.isEmpty()) {
            scores.add(new GolfOverlay.PlayerScore(selection == null ? "SONIC" : selection.playerOne().name(), 0, 0, false, false));
            if (selection != null && selection.mode() != GolfMenu.Mode.PRACTICE)
                scores.add(new GolfOverlay.PlayerScore(selection.playerTwo().name(), 0, 0, false, false));
        }
        return scores;
    }
    private int activeOwner() {
        if (match != null) return match.snapshot().activePlayer();
        return online == null ? 0 : Math.clamp(online.state().owner(), 0, 1);
    }
    private int holeIndex() {
        if (match != null) return match.snapshot().actIndex();
        return online == null || online.turn() == null ? 0 : online.turn().hole() - 1;
    }
    /** Advances presentation once per row, raising score pops, results and ROM sound cues. */
    private void updatePresentation() {
        var scores = scores();
        for (int i = 0; i < scores.size(); i++) {
            int total = scores.get(i).total();
            if (shownTotals[i] >= 0 && total != shownTotals[i]) feedback.scored(i);
            shownTotals[i] = total;
        }
        if (results() && !feedback.resultsShown()) {
            boolean celebrate = connectionError.isEmpty() && scores.stream().noneMatch(GolfOverlay.PlayerScore::dnf)
                    && (match == null || match.snapshot().status() == GolfMatch.Status.COMPLETE);
            feedback.results(celebrate);
        }
        boolean hud = GolfOverlay.controlStage(overlayView(scores));
        int target = scores.stream().mapToInt(GolfOverlay.PlayerScore::total).max().orElse(0);
        for (var cue : feedback.tick(!presentationPaused(), hud, target)) GolfSounds.play(cue);
    }
    private GolfOverlay.View overlayView(List<GolfOverlay.PlayerScore> scores) {
        var shotView = hudShotView();
        String mode = selection == null ? "PRACTICE" : selection.mode().name();
        String message = !connectionError.isEmpty() ? connectionError
                : paused ? "ARROWS CHOOSE  A SELECT  START RESUME"
                : results() ? resultMessage(scores)
                : online != null && (!online.state().ready() || online.state().held() || !online.notice().isEmpty())
                    ? online.message()
                : survey ? "SURVEY TERRAIN: ARROWS PAN  C RETURN" : shotView.hint();
        return GolfHud.overlay(mode, shotView, meter.snapshot(), meter.meterValue(), activeOwner(), scores, holeIndex(),
                message, paused, results());
    }

    @Override public void drawOverlay() {
        var graphics = GameServices.graphics();
        var canvas = new GolfCanvas(graphics, Math.clamp(viewport, 320, 800), 224);
        var scores = scores(); var m = meter.snapshot();
        var view = overlayView(scores);
        boolean local = online == null || online.state().host();
        if (local && !survey && !results() && match != null) {
            var camera = GameServices.camera();
            GolfCards.trail(canvas, feedback.trailPoints(), camera.getX(), camera.getY());
        }
        var shotView = hudShotView();
        boolean ownsAim = online == null || online.state().owner() == online.state().localOwner();
        if (!paused && !results() && !survey && ownsAim && shotView.showShotControls()
                && (m.stage() == ShotMeter.Stage.AIM || m.stage() == ShotMeter.Stage.SPIN
                || m.stage() == ShotMeter.Stage.POWER) && (match != null || online != null)) drawPreview(canvas);
        if (!paused && !results() && (feedback.card() == GolfFeedback.Card.HANDOFF || feedback.toast() == GolfFeedback.Toast.TEE_OFF)
                && scores.size() > 1) drawGolferTag(canvas, scores);
        GolfOverlay.draw(canvas, view, motion(scores));
        if (!results()) {
            int owner = readiness.owner() >= 0 ? readiness.owner() : activeOwner();
            GolfCards.hole(canvas, feedback, holeIndex() + 1, scores.getFirst().name() + " PRACTICE");
            GolfCards.handoff(canvas, feedback, new GolfCards.Handoff(owner, scores.get(Math.min(owner, scores.size() - 1)).name(),
                    viewerOwnsTurn(owner), actionLabel, readiness.requested(), readiness.firstOfHole(), holeIndex() + 1));
            GolfCards.toast(canvas, feedback, scores);
        } else if (feedback.toast() == GolfFeedback.Toast.FINISH && feedback.scorecardAge() < 12) {
            GolfCards.toast(canvas, feedback, scores);
        }
        if (paused) {
            int holeLeft = 0, turnLeft = 0;
            if (match != null) {
                var id = match.nextShotId();
                holeLeft = allowance.holeRemaining(id.player(), id.actIndex()); turnLeft = allowance.turnRemaining(id);
            } else if (online != null && online.shotStatus() != null) {
                holeLeft = online.shotStatus().holeRemaining(); turnLeft = online.shotStatus().turnRemaining();
            }
            boolean rewind = canRewind() && ownsShot();
            GolfOverlay.pause(canvas, List.of("RESUME", rewind ? "REWIND SHOT" : "REWIND UNAVAILABLE", "CONCEDE", "MAIN MENU"),
                    List.of(true, rewind, true, true), pauseRow,
                    "REWINDS LEFT  HOLE " + RewindAllowance.Rules.label(holeLeft) + "  TURN " + RewindAllowance.Rules.label(turnLeft), pauseAge);
        }
    }
    private boolean viewerOwnsTurn(int owner) { return online == null || owner == online.state().localOwner(); }
    private GolfOverlay.Motion motion(List<GolfOverlay.PlayerScore> scores) {
        String detail = "";
        if (!results() && (hudShotView().stage().equals("WATCH")) && ownsShot()) {
            int holeLeft = 0, turnLeft = 0;
            if (match != null && match.snapshot().pending() != null) {
                var id = match.snapshot().pending().id();
                holeLeft = allowance.holeRemaining(id.player(), id.actIndex()); turnLeft = allowance.turnRemaining(id);
            } else if (online != null && online.shotStatus() != null) {
                holeLeft = online.shotStatus().holeRemaining(); turnLeft = online.shotStatus().turnRemaining();
            }
            int left = holeLeft < 0 ? turnLeft : turnLeft < 0 ? holeLeft : Math.min(holeLeft, turnLeft);
            if (canRewind()) detail = left < 0 ? "(UNLIMITED)" : "(" + left + " LEFT)";
        }
        GolfOverlay.Progress progress = null;
        // A guest never loads the next act, so its EHZ1 gate cannot place EHZ2 progress.
        boolean gateKnown = online == null || online.state().host() || GameServices.level().getCurrentAct() == holeIndex();
        if (startX >= 0 && gateKnown && finishX > startX) {
            var xs = new ArrayList<Integer>();
            for (int i = 0; i < scores.size(); i++) xs.add(lieX[i]);
            progress = new GolfOverlay.Progress(startX, finishX, xs);
        }
        int target = scores.stream().mapToInt(GolfOverlay.PlayerScore::total).max().orElse(0);
        return new GolfOverlay.Motion(feedback.hudShown(), feedback.lockFlash(), feedback.pop(0), feedback.pop(1),
                feedback.clock(), feedback.shake(), feedback.tally(target), feedback.tallyDone(), feedback.scorecardAge(),
                detail, progress, feedback.card() != GolfFeedback.Card.HANDOFF);
    }
    private void drawGolferTag(GolfCanvas canvas, List<GolfOverlay.PlayerScore> scores) {
        int owner = readiness.owner() >= 0 ? readiness.owner() : activeOwner(), x, y;
        if (online != null && !online.state().host()) {
            var opened = online.opened(); var camera = online.viewCamera();
            if (opened == null || camera == null) return;
            x = opened.lieX() - camera[0]; y = opened.lieY() - camera[1];
        } else {
            var p = GameServices.camera().getFocusedSprite();
            x = p.getCentreX() - GameServices.camera().getX(); y = p.getCentreY() - GameServices.camera().getY();
        }
        GolfCards.tag(canvas, feedback.clock(), x, y, owner, scores.get(Math.min(owner, scores.size() - 1)).name());
    }
    /** Immutable HUD policy is useful to both rendering and two-peer integration checks. */
    public GolfHud.ShotView hudShotView() {
        var status = online == null ? null : online.shotStatus();
        int owner = readiness.owner() >= 0 ? readiness.owner() : activeOwner();
        if (readiness.waiting() && !results()) {
            var scores = scores();
            return GolfHud.handoff(viewerOwnsTurn(owner), readiness.requested(), actionLabel, owner,
                    scores.get(Math.min(owner, scores.size() - 1)).name());
        }
        boolean ownsTurn = online == null || online.state().owner() == online.state().localOwner();
        return GolfHud.shot(meter.snapshot(), ownsTurn, online != null && !online.state().host(),
                status == null ? null : GolfHud.RemotePhase.valueOf(status.phase().name()), replay.playing(), ownsShot() && canRewind(),
                rewindLabel);
    }

    private String resultMessage(List<GolfOverlay.PlayerScore> scores) {
        if (scores.size() == 1) return scores.getFirst().dnf() ? "DNF - START FOR MENU" : "HOLE COMPLETE - START FOR MENU";
        int winner;
        if (scores.get(0).dnf()) winner = 1;
        else if (scores.get(1).dnf()) winner = 0;
        else if (match != null) winner = match.snapshot().winner();
        else if (online != null && online.state().ended()) return "ROOM ENDED - START FOR MENU";
        else winner = scores.get(0).total() < scores.get(1).total() ? 0 : scores.get(0).total() > scores.get(1).total() ? 1 : -1;
        return (winner < 0 ? "DRAW" : "P" + (winner + 1) + " " + scores.get(winner).name() + " WINS") + " - START FOR MENU";
    }
    // S2 ObjectMoveAndFall: gravity $38 in native 8.8; y(t) adds half g*t^2.
    private static final double PREVIEW_HALF_GRAVITY = 0x38 / 512.0;
    private void drawPreview(GolfCanvas canvas) {
        var m = meter.snapshot();
        int centreX, centreY, surfaceAngle, radiusDelta, cameraX, cameraY;
        if (online != null && !online.state().host()) {
            var basis = online.guestGuide();
            if (basis == null || online.guestAccepted()) return;
            centreX = basis.centreX(); centreY = basis.centreY(); surfaceAngle = basis.angle();
            radiusDelta = basis.rollOffset(); cameraX = basis.cameraX(); cameraY = basis.cameraY();
        } else {
            var p = GameServices.camera().getFocusedSprite();
            centreX = p.getCentreX(); centreY = p.getCentreY(); surfaceAngle = p.getAngle();
            radiusDelta = p.getYRadius() - p.getRollYRadius();
            cameraX = GameServices.camera().getX(); cameraY = GameServices.camera().getY();
        }
        double angle = (surfaceAngle & 255) * Math.PI / 128;
        // Full-power intended hit point during SPIN; actual stopped hit/current power during POWER.
        var velocity = GolfRules.launchVelocity(m.direction(), m.elevationDegrees(),
                GolfRules.speedFixed(m.stage() == ShotMeter.Stage.POWER ? meter.meterValue() : GolfRules.MAX_POWER),
                surfaceAngle, m.stage() == ShotMeter.Stage.SPIN ? m.targetSpin() : m.spin());
        double vx = velocity.x() / 256.0, vy = velocity.y() / 256.0;
        // Native standing-to-ball centre correction, from local or authoritative turn values.
        int x = centreX - (int) Math.round(Math.sin(angle) * radiusDelta) - cameraX;
        int y = centreY + (int) Math.round(Math.cos(angle) * radiusDelta) - cameraY;
        int index = 0;
        for (int t = 3; t <= 24; t += 3, index++) {
            // Departure guide only: do not invent a post-contact arc or landing spin on unseen terrain.
            if (m.elevationDegrees() > 0 && -Math.sin(angle) * vx * t
                    + Math.cos(angle) * (vy * t + PREVIEW_HALF_GRAVITY * t * t) > 0) break;
            int px = x + (int) Math.round(vx * t), py = y + (int) Math.round(vy * t + (m.elevationDegrees() > 0 ? PREVIEW_HALF_GRAVITY * t * t : 0));
            if (px >= 1 && px + 3 <= viewport && py >= 1 && py + 3 <= 224) {
                // A brightness wave travels outward along the guide, dot by dot.
                float wave = GolfMotion.pulse(feedback.clock() - index * 4L, 32);
                canvas.rect(px - 1, py - 1, 4, 4, GolfText.INK, 0.95f);
                canvas.rect(px, py, 2, 2, GolfMotion.mix(GolfText.GOLD, GolfOverlay.WHITE, wave), 0.8f);
            }
        }
        if (m.stage() != ShotMeter.Stage.POWER) {
            String hint = m.elevationDegrees() == 0 ? "GUIDE: FULL-POWER PUTT" : "GUIDE: FULL POWER + SPIN";
            float shown = feedback.hudShown();
            var slid = canvas.offset(0, Math.round((1 - shown) * 46));
            slid.panel(6, 165, GolfText.width(hint, 1) + 8, 12, GolfOverlay.NIGHT, 0.88f, GolfText.CYAN);
            slid.text(hint, 10, 168, 1, GolfText.CREAM);
        }
    }
    @Override public void close() {
        if (online != null) { online.close(); online = null; }
        if (presenter != null) { presenter.close(); presenter = null; }
        replay.clear();
    }
}
