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
import java.util.*;

/** One live course, independent golfer worlds, and a ledger outside course rollback. */
public final class GolfMode implements GameplayFrameController, RewindSnapshottable<GolfMode.State> {
    public record State(GolfMatch.State match, ShotMeter.State meter, List<CourseCheckpoint> lies,
                        CourseCheckpoint neutralLie, long tick, int initialSteps, int watchSteps,
                        int dwell, int previousX, int previousY, boolean paused, boolean startHeld,
                        boolean inputBlocked, boolean survey, int surveyX, int surveyY, int pauseRow, boolean cHeld, String connectionError, boolean landingSpinPending,
                        RewindAllowance.State allowance, ShotReplay.State replay, GolfOutcome.Candidates review,
                        boolean rewindHeld, boolean rewindQueued) {
        public State { lies = List.copyOf(lies); }
    }
    private GolfMatch match;
    private ShotMeter meter = new ShotMeter();
    private RewindAllowance allowance = new RewindAllowance(RewindAllowance.Rules.defaults());
    private final ShotReplay replay = new ShotReplay();
    private GolfOutcome.Candidates review;
    private boolean rewindHeld, rewindQueued;
    private final CourseCheckpoint[] lies = new CourseCheckpoint[2];
    private CourseCheckpoint neutralLie;
    private long tick, renderRevision;
    private int initialSteps, watchSteps, dwell, previousX, previousY, remoteFeedback;
    private boolean paused, startHeld, inputBlocked, survey, cHeld, titleRequest, landingSpinPending;
    private int surveyX, surveyY, pauseRow;
    private GolfMenu.Selection selection;
    private int finishX, finishY, viewport = 320;
    private GolfOnline online;
    private SceneViewPresenter presenter;
    private String connectionError = "";

    public void configure(GolfMenu.Selection choice) {
        close(); selection = choice; match = null; neutralLie = null; Arrays.fill(lies, null);
        meter = new ShotMeter(); paused = false; startHeld = true; inputBlocked = true; landingSpinPending = false;
        survey = false; cHeld = false; titleRequest = false; tick = 0; initialSteps = 0;
        surveyX = surveyY = pauseRow = 0; connectionError = "";
        allowance = new RewindAllowance(choice.rewinds()); replay.clear(); review = null; rewindHeld = rewindQueued = false;
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
                inputBlocked, survey, surveyX, surveyY, pauseRow, cHeld, connectionError, landingSpinPending, allowance.snapshot(), replay.snapshot(), review, rewindHeld, rewindQueued);
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
        allowance.restore(state.allowance()); replay.restore(state.replay()); review = state.review();
        rewindHeld = state.rewindHeld(); rewindQueued = state.rewindQueued();
    }
    public GolfMatch.State matchState() { return match == null ? null : match.snapshot(); }
    public ShotMeter.State shotState() { return meter.snapshot(); }
    public GolfRoom.State roomState() { return online == null ? null : online.state(); }
    @Override public boolean presentationPaused() { return paused || online != null && online.state().held(); }
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
        tick++; viewport = course.viewportWidth();
        if (neutralLie == null) course.advanceEntryPresentation();
        var playerInput = controls(input);
        boolean rewindPressed = course.rewindHeld() && !rewindHeld; rewindHeld = course.rewindHeld();
        if (rewindPressed && ownsShot() && canRewind()) rewindQueued = true;
        boolean start = playerInput.startHeld();
        if (online == null && connectionError.isEmpty() && selection != null
                && (selection.mode() == GolfMenu.Mode.HOST || selection.mode() == GolfMenu.Mode.JOIN)) {
            try { online = new GolfOnline(selection, course); }
            catch (java.io.IOException | IllegalArgumentException failure) { connectionError = "ROOM: " + failure.getMessage(); }
        }
        if (online != null) {
            for (var event : online.tick()) if (event instanceof GolfRoom.Received received) {
                var packet = received.packet();
                if (packet instanceof GolfPacket.Leave) online.receive(packet);
                if (!online.state().host()) {
                    var old = online.turn(); online.receive(packet);
                    if (packet instanceof GolfPacket.Rejected rejected && Objects.equals(rejected.id(), online.turn())) {
                        meter = new ShotMeter(); inputBlocked = true;
                    }
                    if (packet instanceof GolfPacket.TurnOpened && !Objects.equals(old, online.turn())) {
                        meter = new ShotMeter(); inputBlocked = true; survey = false;
                    }
                } else if (packet instanceof GolfPacket.ShotRequest request && match != null
                        && !paused && meter.snapshot().stage() == ShotMeter.Stage.AIM
                        && match.snapshot().activePlayer() == 1 && online.wire(match.nextShotId()).equals(request.id())
                        && online.accepts(request, tick)) {
                    var shot = new GolfShot(request.facing(), request.elevationDegrees(), request.normalizedPower(), request.spin());
                    startRecording(); match.commit(match.nextShotId(), shot);
                    publishStatus(GolfPacket.ShotPhase.CHARGING);
                    meter.restore(new ShotMeter.State(ShotMeter.Stage.FEEDBACK, shot.direction(), shot.elevationDegrees(),
                            0, shot.normalizedPower(), shot.spin(), shot.spin(), GolfRules.CHARGE_FEEDBACK_TICKS, true, false, shot, false));
                    remoteFeedback = shot.isPutt() ? 0 : 2; inputBlocked = false; charge(course);
                }
                if (online.state().host() && packet instanceof GolfPacket.ShotControl control && match != null
                        && match.snapshot().pending() != null && online.wire(match.snapshot().pending().id()).equals(control.id())
                        && control.id().owner() == 1 && !paused && !online.state().held()) {
                    if (control.action() == GolfPacket.ShotAction.REWIND) beginRewind(course);
                    else if (review != null && !replay.playing()) keepShot(course);
                    if (match.snapshot().pending() != null) publishStatus(currentPhase());
                }
            }
            if (online.state().ended() && match != null && match.snapshot().status() == GolfMatch.Status.PLAYING)
                match.concede(online.concededOwner() >= 0 ? online.concededOwner() : 1);
        }
        if (start && !startHeld) {
            if (results()) titleRequest = true;
            else { paused = !paused; pauseRow = 0; inputBlocked = true; if (online != null) online.pause(paused); }
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
            if (status != null && (status.phase() == GolfPacket.ShotPhase.REVIEW || status.phase() == GolfPacket.ShotPhase.REWINDING)) {
                if (status.phase() == GolfPacket.ShotPhase.REVIEW
                        && (playerInput.actionPressedMask() & InputActionMasks.ACTION_A) != 0)
                    online.control(GolfPacket.ShotAction.KEEP);
                return false;
            }
            return guestTick(course, playerInput);
        }
        if (match == null) {
            if (selection == null || selection.mode() == GolfMenu.Mode.PRACTICE)
                match = GolfMatch.practice(character(course.ball().character()), course.actIndex());
            else match = GolfMatch.competition(character(online == null ? selection.playerOne().code() : online.state().hostCharacter()),
                    character(online == null ? selection.playerTwo().code() : online.state().guestCharacter()));
        }
        if (match.snapshot().status() != GolfMatch.Status.PLAYING) return false;
        if (neutralLie == null) { course.finishInitialPresentation(); return true; }
        if (rewindQueued) { beginRewind(course); rewindQueued = false; }
        if (replay.playing()) {
            if (replay.step()) finishRewind(course);
            return false;
        }
        if (review != null) {
            if (ownsShot() && (playerInput.actionPressedMask() & InputActionMasks.ACTION_A) != 0) keepShot(course);
            return false;
        }
        if (online != null && match.snapshot().activePlayer() == 1 && match.snapshot().pending() == null) return false;
        if (remoteFeedback > 0 && --remoteFeedback == 0) charge(course);
        return meterTick(course, online != null && match.snapshot().activePlayer() == 1 ? PlayerInputState.neutral() : playerInput, false);
    }
    private GolfMatch.Character character(String code) { return "tails".equals(code) ? GolfMatch.Character.TAILS : GolfMatch.Character.SONIC; }
    private boolean guestTick(CourseControl course, PlayerInputState input) {
        if (online.state().owner() != 1 || online.turn() == null) return false;
        if (meter.snapshot().stage() == ShotMeter.Stage.FEEDBACK && !online.guestAccepted()) return false;
        meterTick(course, input, true); return false;
    }
    private boolean meterTick(CourseControl course, PlayerInputState input, boolean guest) {
        if (!guest) resumeVerticalDrive();
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
            case DUCK, CANCEL -> { }
            case CHARGE -> { if (guest) course.chargeSound(); else charge(course); }
            case COMMIT -> {
                if (guest) { if (!online.submit(event.shot())) meter = new ShotMeter(); }
                else {
                    startRecording(); match.commit(match.nextShotId(), event.shot());
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
        return match != null && match.snapshot().pending() != null && !replay.playing() && allowance.available(match.snapshot().pending().id());
    }
    private ScenePresentationFrame recordScene() {
        return paradise.presentation.GolfScene.withFinishFlag(GameServices.level().captureScene(0,
                PlayerPresentationPose.nativePose(), 0, 0), finishX, finishY);
    }
    private void startRecording() {
        replay.clear(); review = null;
        if (allowance.available(match.nextShotId())) replay.record(0, recordScene());
    }
    private GolfPacket.ShotPhase currentPhase() {
        return replay.playing() ? GolfPacket.ShotPhase.REWINDING : review != null ? GolfPacket.ShotPhase.REVIEW
                : meter.snapshot().stage() == ShotMeter.Stage.WATCH ? GolfPacket.ShotPhase.WATCH
                : match.snapshot().pending() != null ? GolfPacket.ShotPhase.CHARGING : GolfPacket.ShotPhase.AIM;
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
        replay.begin(watchSteps, recordScene()); review = null; survey = false; landingSpinPending = false;
        inputBlocked = true; GameServices.audio().stopAllSfx(); publishStatus(GolfPacket.ShotPhase.REWINDING); return true;
    }
    private void finishRewind(CourseControl course) {
        var id = match.snapshot().pending().id(); course.restore(neutralLie);
        if (match.rewind(id) != GolfMatch.Decision.ACCEPTED) throw new IllegalStateException("Rewound shot lost its ledger");
        if (online != null) online.rewound(match.snapshot(), id, course.ball());
        openTurn(course);
    }
    private void launch(CourseControl course, GolfShot shot) {
        var player = GameServices.camera().getFocusedSprite();
        var velocity = GolfRules.launchVelocity(shot.direction(), shot.elevationDegrees(), shot.speedFixed(), player.getAngle(), shot.spin());
        // The candidate CourseControl contract stops at 75 degrees. Reuse its native
        // curl, centre correction, support release and sound before the mod's steeper departure.
        course.launch(shot.direction(), Math.min(75, shot.elevationDegrees()), shot.speedFixed());
        if (shot.elevationDegrees() > 75 || shot.spin() != 0) {
            player.setXSpeed((short) velocity.x()); player.setYSpeed((short) velocity.y());
            player.setGSpeed((short) velocity.ground());
        }
        landingSpinPending = !shot.isPutt() && shot.spin() != 0;
    }
    private void resumeVerticalDrive() {
        var state = meter.snapshot();
        if (state.stage() != ShotMeter.Stage.WATCH || state.shot() == null
                || state.shot().elevationDegrees() != GolfRules.MAX_ELEVATION_DEGREES) return;
        var player = GameServices.camera().getFocusedSprite();
        if (player.getAir() && player.getYSpeed() < 0 && player.getXSpeed() == 0
                && !player.getDead() && !player.isHurt() && !player.getSpringing()) {
            // Retry a wall-stopped forward bias while ascending. Native collision
            // still clamps each step; never push during descent or overwrite a bounce.
            player.setXSpeed((short) GolfRules.launchVelocity(state.shot().direction(), 90,
                    state.shot().speedFixed(), 0, state.shot().spin()).x());
        }
    }
    private void charge(CourseControl course) { course.chargeSound(); if (online != null) online.sound(tick, "charge"); }
    @Override public boolean retainRolling() { return meter.snapshot().stage() == ShotMeter.Stage.WATCH; }
    @Override public void afterTick(CourseControl course, boolean advanced) {
        if (advanced) {
            applyLandingSpin(course);
            if (match != null && match.snapshot().pending() != null && allowance.available(match.snapshot().pending().id())
                    && replay.wantsSample(watchSteps + 1)) replay.record(watchSteps + 1, recordScene());
            observe(course);
        }
        if (online != null && online.state().host() && tick % 3 == 0) {
            if (replay.playing()) online.publish(tick, replay.frame(0));
            else online.publish(tick, pose(), finishX, finishY);
        }
    }
    private void applyLandingSpin(CourseControl course) {
        if (!landingSpinPending || meter.snapshot().stage() != ShotMeter.Stage.WATCH) return;
        var player = GameServices.camera().getFocusedSprite();
        if (player.getAir() || !course.ball().floorSupport() || player.getDead()
                || player.isHurt() || player.getSpringing()) return;
        int ground = GolfRules.landingSpeed(player.getGSpeed(), meter.snapshot().shot());
        double angle = (player.getAngle() & 255) * Math.PI / 128;
        player.setGSpeed((short) ground);
        player.setXSpeed((short) Math.round(ground * Math.cos(angle)));
        player.setYSpeed((short) Math.round(ground * Math.sin(angle)));
        landingSpinPending = false;
    }
    private void observe(CourseControl course) {
        var ball = course.ball();
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
                }
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
        if (allowance.available(pending.id())) {
            review = candidates; replay.record(watchSteps, recordScene());
            publishStatus(GolfPacket.ShotPhase.REVIEW); return;
        }
        resolveShot(course, candidates);
    }
    private void keepShot(CourseControl course) {
        if (review == null || replay.playing()) return;
        var kept = review; review = null; resolveShot(course, kept);
    }
    private void resolveShot(CourseControl course, GolfOutcome.Candidates candidates) {
        var pending = match.snapshot().pending(); if (pending == null) return;
        int owner = pending.id().player(); var result = match.resolve(pending.id(), candidates);
        if (result.decision() != GolfMatch.Decision.ACCEPTED) return;
        if (result.outcome().isPenalty()) course.restore(neutralLie);
        else if (result.outcome() == GolfOutcome.SETTLED) course.settle();
        lies[owner] = course.capture();
        if (online != null) online.committed(match.snapshot(), pending.id(), result.outcome(), course.ball());
        if (result.holeAdvanced()) {
            course.loadAct(1); neutralLie = null; Arrays.fill(lies, null); meter = new ShotMeter(); initialSteps = 0; return;
        }
        if (match.snapshot().status() == GolfMatch.Status.PLAYING) {
            course.restore(lies[match.snapshot().activePlayer()]); openTurn(course);
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
        neutralLie = course.capture(); lies[match.snapshot().activePlayer()] = neutralLie;
        meter = new ShotMeter(); watchSteps = dwell = 0; remoteFeedback = 0; landingSpinPending = false;
        replay.clear(); review = null; rewindQueued = false;
        survey = false; inputBlocked = true; previousX = course.ball().x(); previousY = course.ball().y();
        if (online != null) { online.open(match.snapshot(), match.nextShotId(), course.ball()); publishStatus(GolfPacket.ShotPhase.AIM); }
    }
    private PlayerPresentationPose pose() {
        var m = meter.snapshot(); var kind = m.stage() == ShotMeter.Stage.SPIN
                || (m.stage() == ShotMeter.Stage.POWER && m.elevationDegrees() == 0) ? PlayerPresentationPose.Kind.DUCK
                : m.stage() == ShotMeter.Stage.POWER || m.stage() == ShotMeter.Stage.FEEDBACK || m.stage() == ShotMeter.Stage.PRE_RELEASE
                ? PlayerPresentationPose.Kind.SPINDASH : PlayerPresentationPose.Kind.NATIVE;
        return new PlayerPresentationPose(kind, tick, m.direction());
    }
    @Override public boolean drawScene() {
        if (online != null && !online.state().host()) return online.draw(surveyX, surveyY, pose());
        if (match == null) return false;
        if (presenter == null) presenter = GameServices.level().createScenePresenter();
        presenter.accept(replay.playing() ? replay.frame(++renderRevision) : paradise.presentation.GolfScene.withFinishFlag(
                GameServices.level().captureScene(++renderRevision, pose(), survey ? viewport / 2 : 0, survey ? 112 : 0), finishX, finishY));
        presenter.draw(surveyX, surveyY); return true;
    }
    private boolean results() { return match != null && match.snapshot().status() != GolfMatch.Status.PLAYING || online != null && online.complete() || !connectionError.isEmpty(); }
    @Override public void drawOverlay() {
        var scores = new ArrayList<GolfOverlay.PlayerScore>(); var m = meter.snapshot();
        int owner = 0, act = 0; String mode = selection == null ? "PRACTICE" : selection.mode().name();
        if (match != null) {
            var s = match.snapshot(); owner = s.activePlayer(); act = s.actIndex();
            for (var golfer : s.golfers()) scores.add(new GolfOverlay.PlayerScore(golfer.character().name(),
                    golfer.holes().stream().mapToInt(GolfMatch.HoleScore::strokes).sum(), golfer.holes().stream().mapToInt(GolfMatch.HoleScore::penalties).sum(),
                    golfer.holes().get(act).finished(), golfer.dnf()));
        } else if (online != null && online.state().hostCharacter() != null && online.state().guestCharacter() != null) {
            var s = online.state(); owner = Math.clamp(s.owner(), 0, 1); act = online.turn() == null ? 0 : online.turn().hole() - 1;
            scores.add(new GolfOverlay.PlayerScore(s.hostCharacter().toUpperCase(Locale.ROOT), s.player0().strokes(), s.player0().penalties(), s.player0().finished(), online.concededOwner() == 0));
            scores.add(new GolfOverlay.PlayerScore(s.guestCharacter().toUpperCase(Locale.ROOT), s.player1().strokes(), s.player1().penalties(), s.player1().finished(), online.concededOwner() == 1));
        }
        if (scores.isEmpty()) {
            scores.add(new GolfOverlay.PlayerScore(selection == null ? "SONIC" : selection.playerOne().name(), 0, 0, false, false));
            if (selection != null && selection.mode() != GolfMenu.Mode.PRACTICE)
                scores.add(new GolfOverlay.PlayerScore(selection.playerTwo().name(), 0, 0, false, false));
        }
        String message = !connectionError.isEmpty() ? connectionError : paused ? "ARROWS CHOOSE  A SELECT  START RESUME"
                : results() ? resultMessage(scores)
                : online != null && (!online.state().ready() || online.state().held() || !online.notice().isEmpty()) ? online.message()
                : replay.playing() || online != null && online.shotStatus() != null && online.shotStatus().phase() == GolfPacket.ShotPhase.REWINDING ? "REWINDING SHOT TO ITS START"
                : review != null || online != null && online.shotStatus() != null && online.shotStatus().phase() == GolfPacket.ShotPhase.REVIEW ? "A KEEP SHOT  REWIND TO RETRY"
                : survey ? "SURVEY TERRAIN: ARROWS PAN  C RETURN"
                : switch (m.stage()) {
                    case AIM -> "UP/DOWN LOFT  LEFT/RIGHT AIM  A SHOT";
                    case SPIN -> "UP/DOWN HIT POINT  A STOP MARKER";
                    case POWER -> "A STOP POWER - ONE RISE AND FALL";
                    case FEEDBACK, PRE_RELEASE -> m.timedOut() ? "MISSED POWER - SOFT SHOT"
                            : m.power() == GolfRules.MAX_POWER ? "FULL POWER - RELEASING" : "SHOT COMMITTED - RELEASING";
                    case WATCH -> "WATCH THE BALL - START PAUSE";
                };
        boolean ownsAim = online == null || online.state().owner() == (online.state().host() ? 0 : 1);
        if (!paused && !survey && ownsAim && (m.stage() == ShotMeter.Stage.AIM || m.stage() == ShotMeter.Stage.SPIN
                || m.stage() == ShotMeter.Stage.POWER) && (match != null || online != null)) drawPreview();
        var status = online == null ? null : online.shotStatus();
        String stageLabel = replay.playing() || status != null && status.phase() == GolfPacket.ShotPhase.REWINDING ? "REWIND"
                : review != null || status != null && status.phase() == GolfPacket.ShotPhase.REVIEW ? "REVIEW" : m.stage().name();
        GolfOverlay.draw(GameServices.graphics(), viewport, new GolfOverlay.View(mode, stageLabel, m.elevationDegrees(), m.direction(),
                m.power(), m.spin(), m.targetSpin(), meter.meterValue(), owner, scores, act, message, paused, results()));
        if (!paused && !results()) {
            int holeLeft = 0, turnLeft = 0;
            if (match != null) { var id = match.nextShotId(); holeLeft = allowance.holeRemaining(id.player(), id.actIndex()); turnLeft = allowance.turnRemaining(id); }
            else if (online != null && online.shotStatus() != null) { holeLeft = online.shotStatus().holeRemaining(); turnLeft = online.shotStatus().turnRemaining(); }
            GolfText.panel(GameServices.graphics(), 8, 32, 198, 10, GolfText.INK, 0.85f);
            GolfText.draw(GameServices.graphics(), "REWINDS H:" + RewindAllowance.Rules.label(holeLeft)
                    + " T:" + RewindAllowance.Rules.label(turnLeft) + "  START MENU", 11, 34, 1, GolfText.CREAM);
        }
        if (paused) for (int i = 0; i < 4; i++) GolfText.centered(GameServices.graphics(), (pauseRow == i ? "> " : "  ")
                + List.of("RESUME", canRewind() && ownsShot() ? "REWIND SHOT" : "REWIND UNAVAILABLE", "CONCEDE", "MAIN MENU").get(i), viewport, 93 + i * 12, 1, pauseRow == i ? GolfText.GOLD : GolfText.CREAM);
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
    private void drawPreview() {
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
        for (int t = 3; t <= 24; t += 3) {
            // Departure guide only: do not invent a post-contact arc or landing spin on unseen terrain.
            if (m.elevationDegrees() > 0 && -Math.sin(angle) * vx * t
                    + Math.cos(angle) * (vy * t + 0.109375 * t * t) > 0) break;
            int px = x + (int) Math.round(vx * t), py = y + (int) Math.round(vy * t + (m.elevationDegrees() > 0 ? 0.109375 * t * t : 0));
            if (px >= 1 && px + 3 <= viewport && py >= 1 && py + 3 <= 224) {
                GolfText.panel(GameServices.graphics(), px - 1, py - 1, 4, 4, GolfText.INK, 0.95f);
                GolfText.panel(GameServices.graphics(), px, py, 2, 2, GolfText.GOLD, 0.8f);
            }
        }
        GolfText.draw(GameServices.graphics(), m.stage() == ShotMeter.Stage.POWER ? "GUIDE: CURRENT POWER + SPIN" : m.elevationDegrees() == 0 ? "GUIDE: FULL-POWER PUTT"
                : "GUIDE: FULL POWER + SPIN", 8, 168, 1, GolfText.CREAM);
    }
    @Override public void close() {
        if (online != null) { online.close(); online = null; }
        if (presenter != null) { presenter.close(); presenter = null; }
        replay.clear();
    }
}
