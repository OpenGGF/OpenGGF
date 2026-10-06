package paradise.ui;

import paradise.model.ShotMeter;
import paradise.model.GolfRules;
import java.util.List;

/** Pure presentation policy: remote phase and turn ownership never grant local shot controls. */
public final class GolfHud {
    public enum RemotePhase { AIM, CHARGING, WATCH, REWINDING, HANDOFF }
    public record ShotView(String stage, String hint, boolean showShotControls) { }
    private GolfHud() { }

    public static ShotView shot(ShotMeter.State meter, boolean ownsTurn, boolean guest,
                                RemotePhase authoritativePhase, boolean replaying,
                                boolean canRewind, String rewindControl) {
        if (replaying || authoritativePhase == RemotePhase.REWINDING) {
            return new ShotView("REWIND", "REWINDING SHOT TO ITS START", false);
        }
        boolean remoteShot = guest && authoritativePhase != null
                && authoritativePhase != RemotePhase.AIM;
        if (!ownsTurn || remoteShot) {
            String stage = authoritativePhase == RemotePhase.CHARGING ? "CHARGING"
                    : authoritativePhase == RemotePhase.WATCH ? "WATCH" : "WAITING";
            String hint = !ownsTurn ? "OTHER PLAYER'S TURN - START PAUSE"
                    : canRewind ? rewindHint(rewindControl) : "WATCH THE BALL - START PAUSE";
            return new ShotView(stage, hint, false);
        }
        String hint = switch (meter.stage()) {
            case AIM -> "UP/DOWN LOFT  LEFT/RIGHT AIM  A SHOT";
            case SPIN -> "UP/DOWN HIT POINT  A STOP MARKER";
            case POWER -> "A STOP POWER - ONE RISE AND FALL";
            case FEEDBACK, PRE_RELEASE -> meter.timedOut() ? "MISSED POWER - SOFT SHOT"
                    : meter.power() == GolfRules.MAX_POWER ? "FULL POWER - RELEASING" : "SHOT COMMITTED - RELEASING";
            case WATCH -> canRewind ? rewindHint(rewindControl) : "WATCH THE BALL - START PAUSE";
        };
        return new ShotView(meter.stage().name(), hint, true);
    }

    /**
     * The incoming golfer's turn is held until they confirm. Only the owner sees a key to press;
     * a spectator is told who the course is waiting for. No shot controls are shown either way.
     */
    public static ShotView handoff(boolean viewerOwns, boolean requested, String button, int owner, String name) {
        String hint = !viewerOwns ? "WAITING FOR P" + (owner + 1) + " " + name + " - START PAUSE"
                : requested ? "READY - WAITING FOR THE HOST"
                : (button.isEmpty() ? "A" : button) + " WHEN READY  START PAUSE";
        return new ShotView(viewerOwns ? "HANDOFF" : "WAITING", hint, false);
    }

    /** An unbound rewind control still leaves the Start menu's Rewind Shot. */
    private static String rewindHint(String control) {
        return control.isEmpty() ? "START MENU TO REWIND" : control + " REWIND  START PAUSE";
    }

    /** Default and remapped printable keys are shown accurately; Start's menu works with every binding/movie. */
    public static String rewindControl(int key) {
        if (key == 32) return "SPACE/LB";
        return key >= '0' && key <= '9' || key >= 'A' && key <= 'Z' ? (char) key + "/LB" : "LB";
    }

    public static String scoreLabel(int player, GolfOverlay.PlayerScore score) {
        return "P" + (player + 1) + " " + score.total()
                + (score.penalties() > 0 ? " (+" + score.penalties() + " PEN)" : "")
                + (score.dnf() ? " DNF" : score.finished() ? " DONE" : "");
    }

    public static GolfOverlay.View overlay(String mode, ShotView shot, ShotMeter.State meter, int meterValue,
                                           int owner, List<GolfOverlay.PlayerScore> scores, int act,
                                           String message, boolean paused, boolean results) {
        boolean controls = shot.showShotControls() && !paused && !results;
        return new GolfOverlay.View(mode, shot.stage(), controls ? meter.elevationDegrees() : 0,
                controls ? meter.direction() : 1, controls ? meter.power() : 0,
                controls ? meter.spin() : 0, controls ? meter.targetSpin() : 0, controls ? meterValue : 0,
                owner, scores, act, message, paused, results, controls);
    }

    public static String stageLabel(String stage) {
        return switch (stage) {
            case "AIM" -> "AIM SHOT";
            case "SPIN" -> "HIT POINT";
            case "POWER" -> "SET POWER";
            case "FEEDBACK", "CHARGING" -> "CHARGING";
            case "PRE_RELEASE" -> "READY";
            case "WATCH" -> "WATCH SHOT";
            case "REWIND" -> "REWIND";
            case "WAITING" -> "WAITING";
            case "HANDOFF" -> "GET READY";
            default -> stage;
        };
    }
}
