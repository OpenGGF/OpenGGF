package paradise.model;

import java.util.List;
import java.util.Objects;

/**
 * Dream Course shot flow adapted to Sonic: chips time spin, then all shots time
 * one power sweep. Call once per presentation tick; paused/held rooms do not tick.
 * Events request native poses and sounds. No pitch or random accuracy is computed.
 */
public final class ShotMeter {
    public enum Stage { AIM, SPIN, POWER, FEEDBACK, PRE_RELEASE, WATCH }
    public enum Kind { DUCK, CHARGE, COMMIT, RELEASE, CANCEL }

    public record Input(boolean up, boolean down, boolean left, boolean right, boolean a, boolean b) {
        public static Input none() { return new Input(false, false, false, false, false, false); }
    }
    public record Event(Kind kind, GolfShot shot) {
        public Event {
            Objects.requireNonNull(kind, "kind");
            if ((kind == Kind.COMMIT || kind == Kind.RELEASE) != (shot != null))
                throw new IllegalArgumentException("Only commit/release events carry a locked shot");
        }
    }
    public record State(Stage stage, int direction, int elevationDegrees, int meterPhase,
                        int power, int spin, int targetSpin, int stageTicksRemaining,
                        boolean aHeld, boolean bHeld, GolfShot shot, boolean timedOut) {
        public State {
            Objects.requireNonNull(stage, "stage");
            var selection = new GolfShot(direction, elevationDegrees, power, spin);
            if (targetSpin < -GolfRules.MAX_SPIN || targetSpin > GolfRules.MAX_SPIN)
                throw new IllegalArgumentException("Invalid target spin");
            int limit = stage == Stage.SPIN ? GolfRules.SPIN_PERIOD_TICKS - 1 : GolfRules.POWER_SWEEP_TICKS;
            if (meterPhase < 0 || meterPhase > limit || stageTicksRemaining < 0
                    || (stage == Stage.SPIN && elevationDegrees == 0))
                throw new IllegalArgumentException("Invalid meter clock/stage");
            boolean committed = stage == Stage.FEEDBACK || stage == Stage.PRE_RELEASE || stage == Stage.WATCH;
            if (committed != (shot != null) || (shot != null && !shot.equals(selection))
                    || (timedOut && (!committed || power != 0)))
                throw new IllegalArgumentException("Invalid locked shot");
            int countdown = stage == Stage.FEEDBACK ? GolfRules.CHARGE_FEEDBACK_TICKS
                    : stage == Stage.PRE_RELEASE ? GolfRules.PRE_RELEASE_TICKS : 0;
            if (stageTicksRemaining > countdown || (countdown > 0 && stageTicksRemaining == 0))
                throw new IllegalArgumentException("Invalid stage countdown");
        }
    }

    private Stage stage = Stage.AIM;
    private int direction = 1, elevationDegrees, meterPhase, power, spin, targetSpin, stageTicksRemaining;
    private boolean aHeld, bHeld, timedOut;
    private GolfShot shot;

    public State snapshot() {
        return new State(stage, direction, elevationDegrees, meterPhase, power, spin, targetSpin,
                stageTicksRemaining, aHeld, bHeld, shot, timedOut);
    }
    public void restore(State saved) {
        Objects.requireNonNull(saved, "saved");
        stage = saved.stage(); direction = saved.direction(); elevationDegrees = saved.elevationDegrees();
        meterPhase = saved.meterPhase(); power = saved.power(); spin = saved.spin();
        targetSpin = saved.targetSpin();
        stageTicksRemaining = saved.stageTicksRemaining(); aHeld = saved.aHeld(); bHeld = saved.bHeld();
        shot = saved.shot(); timedOut = saved.timedOut();
    }
    public int meterValue() {
        return stage == Stage.SPIN ? GolfRules.spinAtPhase(meterPhase)
                : stage == Stage.POWER ? GolfRules.powerAtPhase(meterPhase) : power;
    }
    private List<Event> commit(int selectedPower, boolean expired) {
        power = selectedPower; timedOut = expired;
        shot = new GolfShot(direction, elevationDegrees, power, spin);
        stage = Stage.FEEDBACK; stageTicksRemaining = GolfRules.CHARGE_FEEDBACK_TICKS;
        return List.of(new Event(Kind.CHARGE, null), new Event(Kind.COMMIT, shot));
    }
    public List<Event> tick(Input input) {
        Objects.requireNonNull(input, "input");
        boolean aPressed = input.a() && !aHeld, bPressed = input.b() && !bHeld;
        aHeld = input.a(); bHeld = input.b();
        if (bPressed && (stage == Stage.SPIN || stage == Stage.POWER)) {
            stage = Stage.AIM; power = spin = targetSpin = meterPhase = 0;
            return List.of(new Event(Kind.CANCEL, null));
        }
        switch (stage) {
            case AIM -> {
                int change = (input.up() ? 1 : 0) - (input.down() ? 1 : 0);
                elevationDegrees = Math.clamp(elevationDegrees + change * GolfRules.ELEVATION_STEP_DEGREES,
                        0, GolfRules.MAX_ELEVATION_DEGREES);
                if (input.left() != input.right()) direction = input.left() ? -1 : 1;
                if (aPressed && !input.b()) {
                    stage = elevationDegrees == 0 ? Stage.POWER : Stage.SPIN; meterPhase = 0;
                    return List.of(new Event(Kind.DUCK, null));
                }
            }
            case SPIN -> {
                targetSpin = Math.clamp(targetSpin + ((input.up() ? 1 : 0) - (input.down() ? 1 : 0)) * GolfRules.SPIN_STEP,
                        -GolfRules.MAX_SPIN, GolfRules.MAX_SPIN);
                if (aPressed) {
                    spin = GolfRules.stoppedSpin(meterPhase, targetSpin); stage = Stage.POWER; meterPhase = 0;
                    return List.of(new Event(Kind.CHARGE, null));
                }
                meterPhase = (meterPhase + 1) % GolfRules.SPIN_PERIOD_TICKS;
            }
            case POWER -> {
                if (aPressed) return commit(meterValue(), false);
                if (meterPhase == GolfRules.POWER_SWEEP_TICKS) return commit(0, true);
                meterPhase++;
            }
            case FEEDBACK -> {
                int elapsed = GolfRules.CHARGE_FEEDBACK_TICKS - --stageTicksRemaining;
                boolean charge = elapsed % GolfRules.EXTRA_CHARGE_INTERVAL_TICKS == 0
                        && elapsed / GolfRules.EXTRA_CHARGE_INTERVAL_TICKS
                        <= shot.normalizedPower() / GolfRules.EXTRA_CHARGE_POWER_STEP;
                if (stageTicksRemaining == 0) {
                    stage = Stage.PRE_RELEASE; stageTicksRemaining = GolfRules.PRE_RELEASE_TICKS;
                }
                if (charge) return List.of(new Event(Kind.CHARGE, null));
            }
            case PRE_RELEASE -> {
                if (--stageTicksRemaining == 0) {
                    stage = Stage.WATCH;
                    return List.of(new Event(Kind.RELEASE, shot));
                }
            }
            case WATCH -> { }
        }
        return List.of();
    }
}
