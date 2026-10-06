package paradise;

import com.openggf.game.GameServices;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.presentation.*;
import paradise.model.*;
import paradise.net.*;
import paradise.ui.GolfMenu;
import java.io.IOException;
import java.util.List;

/** Bounded room/scene bridge. Guests never restore snapshots or step gameplay. */
final class GolfOnline implements AutoCloseable {
    private final GolfRoom room;
    private SceneViewPresenter presenter;
    private ScenePresentationFrame lastFrame;
    private GolfPacket.ShotId turn;
    private long revision, cue;
    private String error = "";
    private boolean completed;
    private int concededOwner = -1;
    private GolfPacket.ShotId accepted;
    private GolfPacket.ShotStatus shotStatus;
    private GolfPacket.ShotId resolved;

    GolfOnline(GolfMenu.Selection selection, CourseControl course) throws IOException {
        var fingerprints = new GolfPacket.Fingerprints(GolfCodec.SCHEMA, course.apiIdentity(),
                course.engineIdentity(), course.modContentSha256(), GolfRules.fingerprint(selection.rewinds()),
                course.romSha1(), "competition", course.viewportWidth(), 224);
        room = selection.mode() == GolfMenu.Mode.HOST
                ? GolfRoom.host(selection.port(), fingerprints, selection.playerOne().code())
                : GolfRoom.join(selection.address(), selection.port(), fingerprints, selection.playerOne().code());
    }
    GolfRoom.State state() { return room.state(); }
    String message() { return error.isEmpty() ? room.state().message() : error; }
    int concededOwner() { return concededOwner; }
    String notice() { return error; }
    boolean complete() { return completed || room.state().ended(); }
    GolfPacket.ShotId turn() { return turn; }
    boolean guestAccepted() { return accepted != null && accepted.equals(turn); }
    GolfPacket.ShotStatus shotStatus() { return shotStatus; }
    List<GolfRoom.Event> tick() { room.tick(); return room.drainEvents(); }
    void receive(GolfPacket packet) {
        if (packet instanceof GolfPacket.TurnOpened opened) {
            if (turn == null || !turn.equals(opened.id())) { accepted = null; shotStatus = null; }
            turn = opened.id(); lastFrame = null; error = "";
        } else if (packet instanceof GolfPacket.ViewFrame frame) {
            try {
                if (presenter == null) presenter = GameServices.level().createScenePresenter();
                var decoded = SceneFrameCodec.decode(frame.payload());
                if (presenter.accept(decoded)) lastFrame = decoded;
            } catch (IOException | IllegalArgumentException invalid) {
                error = "INVALID COURSE VIEW"; room.close();
            }
        } else if (packet instanceof GolfPacket.SoundCue sound) {
            if ("charge".equals(sound.sound()) && sound.id().owner() == 0) GameServices.audio().playSfx(com.openggf.audio.GameSound.SPINDASH_CHARGE);
            else if ("release".equals(sound.sound())) GameServices.audio().playSfx(com.openggf.audio.GameSound.SPINDASH_RELEASE);
        } else if (packet instanceof GolfPacket.ShotAccepted value) { accepted = value.id(); error = ""; }
        else if (packet instanceof GolfPacket.ShotStatus value) shotStatus = value;
        else if (packet instanceof GolfPacket.Rejected rejected) { accepted = null; error = rejected.reason(); }
        else if (packet instanceof GolfPacket.TurnCommitted committed) { completed = committed.nextOwner() < 0; resolved = committed.id(); }
        else if (packet instanceof GolfPacket.Leave leave && GolfRoom.CONCEDED_REASON.equals(leave.reason())) concededOwner = leave.owner();
    }
    void open(GolfMatch.State state, GolfMatch.ShotId id, CourseControl.PlayerState ball, boolean readyRequired) {
        turn = wire(id); cue = 0;
        room.publishTurn(new GolfPacket.TurnOpened(turn, ball.x(), ball.y(), ball.angle() & 255,
                GameServices.camera().getFocusedSprite().getYRadius() - GameServices.camera().getFocusedSprite().getRollYRadius(),
                score(state, 0), score(state, 1), readyRequired));
    }
    /** The authoritative turn opening this peer has adopted, or null before the first one. */
    GolfPacket.TurnOpened opened() {
        var opened = room.state().remoteTurnOpened();
        return opened != null && opened.id().equals(turn) ? opened : null;
    }
    /** Guest view: the host is holding the current turn until its owner's READY is accepted. */
    boolean awaitingReady() {
        var opened = opened();
        return opened != null && opened.readyRequired()
                && (shotStatus == null || shotStatus.phase() == GolfPacket.ShotPhase.HANDOFF);
    }
    void rejectShot(GolfPacket.ShotId id, String reason) { room.rejectRemote(id, reason); }
    /**
     * Owner whose accepted shot is in play but not yet in the published scores, or -1. Wire scores
     * arrive at turn boundaries; the host charges the stroke at acceptance, so the spectator adds it
     * for display until the committed result (or the refund) replaces it.
     */
    int unpublishedStroke() {
        var status = shotStatus;
        if (status == null || turn == null || !status.id().equals(turn) || turn.equals(resolved)) return -1;
        return switch (status.phase()) {
            case CHARGING, WATCH, REWINDING -> turn.owner();
            default -> -1;
        };
    }
    /** Camera of the latest accepted authoritative view, for overlay anchors; null before the first. */
    int[] viewCamera() { return lastFrame == null ? null : new int[]{lastFrame.cameraX(), lastFrame.cameraY()}; }
    GolfPacket.ShotId wire(GolfMatch.ShotId id) {
        return new GolfPacket.ShotId(room.state().match(), id.actIndex() + 1, id.turnSequence(), id.shotSequence(), id.player());
    }
    boolean accepts(GolfPacket.ShotRequest request, long tick) { return room.acceptShot(request, tick).newlyAccepted(); }
    void acceptLocal(GolfShot shot, long tick) {
        if (!accepts(new GolfPacket.ShotRequest(turn, shot.direction(), shot.elevationDegrees(),
                shot.normalizedPower(), shot.spin()), tick)) throw new IllegalStateException("Host shot was not accepted");
    }
    boolean submit(GolfShot shot) {
        error = "";
        return turn != null && room.submitShot(new GolfPacket.ShotRequest(turn, shot.direction(), shot.elevationDegrees(),
                shot.normalizedPower(), shot.spin()));
    }
    boolean control(GolfPacket.ShotAction action) {
        return turn != null && room.submitControl(new GolfPacket.ShotControl(turn, action));
    }
    void status(GolfMatch.ShotId id, GolfPacket.ShotPhase phase, RewindAllowance budget) {
        shotStatus = new GolfPacket.ShotStatus(wire(id), phase, budget.holeRemaining(id.player(), id.actIndex()), budget.turnRemaining(id));
        room.publishStatus(shotStatus);
    }
    void rewound(GolfMatch.State state, GolfMatch.ShotId id, CourseControl.PlayerState ball) {
        room.publishCommitted(new GolfPacket.TurnCommitted(wire(id), GolfPacket.Outcome.REWOUND, ball.x(), ball.y(),
                score(state, 0), score(state, 1), state.activePlayer()));
    }
    void committed(GolfMatch.State state, GolfMatch.ShotId id, GolfOutcome outcome, CourseControl.PlayerState ball) {
        var value = outcome == GolfOutcome.FINISH ? GolfPacket.Outcome.FINISHED
                : outcome.isPenalty() ? GolfPacket.Outcome.PENALTY : GolfPacket.Outcome.SETTLED;
        room.publishCommitted(new GolfPacket.TurnCommitted(wire(id), value, ball.x(), ball.y(),
                score(state, 0), score(state, 1),
                state.status() == GolfMatch.Status.PLAYING ? state.activePlayer() : -1));
    }
    private GolfPacket.Score score(GolfMatch.State state, int player) {
        var golfer = state.golfers().get(player);
        return new GolfPacket.Score(golfer.holes().stream().mapToInt(GolfMatch.HoleScore::strokes).sum(),
                golfer.holes().stream().mapToInt(GolfMatch.HoleScore::penalties).sum(), golfer.holes().get(state.actIndex()).finished());
    }
    void publish(long tick, PlayerPresentationPose pose, int finishX, int finishY, long wave) {
        if (turn == null || !room.state().ready() || room.state().ended()) return;
        try {
            var frame = paradise.presentation.GolfScene.withFinishFlag(
                    GameServices.level().captureScene(++revision, pose, GameServices.camera().getWidth() / 2, 112), finishX, finishY, wave);
            room.publishView(new GolfPacket.ViewFrame(turn, revision, tick, SceneFrameCodec.encode(frame)));
        } catch (IOException oversized) { error = "COURSE VIEW TOO LARGE"; room.close(); }
    }
    void publish(long tick, ScenePresentationFrame recorded) {
        if (turn == null || !room.state().ready() || room.state().ended()) return;
        try {
            var frame = paradise.presentation.ShotReplay.revision(recorded, ++revision);
            room.publishView(new GolfPacket.ViewFrame(turn, revision, tick, SceneFrameCodec.encode(frame)));
        } catch (IOException oversized) { error = "COURSE VIEW TOO LARGE"; room.close(); }
    }
    void sound(long tick, String sound) { if (turn != null) room.cue(new GolfPacket.SoundCue(turn, ++cue, tick, sound)); }
    record GuideBasis(int centreX, int centreY, int angle, int rollOffset, int cameraX, int cameraY) { }
    GuideBasis guestGuide() {
        var opened = room.state().remoteTurnOpened();
        if (lastFrame == null || opened == null || room.state().owner() != 1 || !opened.id().equals(turn)) return null;
        return new GuideBasis(opened.lieX(), opened.lieY(), opened.surfaceAngle(), opened.rollOffset(),
                lastFrame.cameraX(), lastFrame.cameraY());
    }
    boolean draw(int dx, int dy, PlayerPresentationPose pose) {
        if (presenter != null && presenter.revision() >= 0) {
            var opened = room.state().remoteTurnOpened();
            if (room.state().owner() == 1 && opened != null && !guestAccepted()
                    && pose.kind() != PlayerPresentationPose.Kind.NATIVE)
                presenter.draw(dx, dy, new ScenePlayerPose(room.state().guestCharacter(), opened.lieX(), opened.lieY(), pose));
            else presenter.draw(dx, dy);
        }
        return true; // No local guest world is displayed before the first authoritative view.
    }
    void concede() { concededOwner = room.state().localOwner(); room.concede(); }
    void pause(boolean paused) { if (paused) room.pause("PAUSED - START TO RESUME"); else room.resume(); }
    @Override public void close() { room.close(); if (presenter != null) { presenter.close(); presenter = null; } }
}
