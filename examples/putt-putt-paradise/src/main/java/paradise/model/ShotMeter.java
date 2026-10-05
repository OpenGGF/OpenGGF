package paradise.model;

import java.util.List;
import java.util.Objects;

/**
 * One turn's deterministic presentation state. Call once per logical presentation tick;
 * menus/disconnection freeze it by not calling tick. Events are emitted only on transitions.
 * The adapter applies duck/charge/cancel/launch commands and native SFX; no pitch is computed here.
 */
public final class ShotMeter {
    public enum Stage { AIM, FIRST_CHARGE, SECOND_CHARGE, FEEDBACK, PRE_RELEASE, WATCH }
    public enum Kind { DUCK, CHARGE, COMMIT, RELEASE, CANCEL }

    public record Input(boolean up, boolean down, boolean left, boolean right, boolean a, boolean b) {
        public static Input none() { return new Input(false, false, false, false, false, false); }
    }

    /** shot is present on COMMIT/RELEASE and absent on pose/charge/cancel events. */
    public record Event(Kind kind, GolfShot shot) {
        public Event {
            Objects.requireNonNull(kind, "kind");
            if ((kind == Kind.COMMIT || kind == Kind.RELEASE) != (shot != null)) {
                throw new IllegalArgumentException("Only commit/release events carry a locked shot");
            }
        }
    }

    public record State(Stage stage, int direction, int elevationDegrees, int meterPhase,
                        int firstCharge, int secondCharge, int stageTicksRemaining,
                        boolean aHeld, boolean bHeld, GolfShot shot) {
        public State {
            Objects.requireNonNull(stage, "stage");
            new GolfShot(direction, elevationDegrees, firstCharge, secondCharge);
            if (meterPhase < 0 || meterPhase >= GolfRules.METER_PERIOD_TICKS || stageTicksRemaining < 0) {
                throw new IllegalArgumentException("Invalid meter clock");
            }
            boolean committed = stage == Stage.FEEDBACK || stage == Stage.PRE_RELEASE || stage == Stage.WATCH;
            if (committed != (shot != null)) throw new IllegalArgumentException("Invalid shot stage");
            if (shot != null && !shot.equals(new GolfShot(direction, elevationDegrees, firstCharge, secondCharge))) {
                throw new IllegalArgumentException("Locked selection differs from meter state");
            }
            int limit = stage == Stage.FEEDBACK ? GolfRules.CHARGE_FEEDBACK_TICKS
                    : stage == Stage.PRE_RELEASE ? GolfRules.PRE_RELEASE_TICKS : 0;
            if (stageTicksRemaining > limit || (limit > 0 && stageTicksRemaining == 0)) {
                throw new IllegalArgumentException("Invalid stage countdown");
            }
        }
    }

    private Stage stage = Stage.AIM;
    private int direction = 1;
    private int elevationDegrees;
    private int meterPhase;
    private int firstCharge;
    private int secondCharge;
    private int stageTicksRemaining;
    private boolean aHeld;
    private boolean bHeld;
    private GolfShot shot;

    public State snapshot() {
        return new State(stage, direction, elevationDegrees, meterPhase, firstCharge, secondCharge,
                stageTicksRemaining, aHeld, bHeld, shot);
    }

    public void restore(State saved) {
        Objects.requireNonNull(saved, "saved");
        stage = saved.stage(); direction = saved.direction(); elevationDegrees = saved.elevationDegrees();
        meterPhase = saved.meterPhase(); firstCharge = saved.firstCharge(); secondCharge = saved.secondCharge();
        stageTicksRemaining = saved.stageTicksRemaining(); aHeld = saved.aHeld(); bHeld = saved.bHeld(); shot = saved.shot();
    }

    public int meterValue() { return GolfRules.chargeAtPhase(meterPhase); }

    public List<Event> tick(Input input) {
        Objects.requireNonNull(input, "input");
        boolean aPressed = input.a() && !aHeld;
        boolean bPressed = input.b() && !bHeld;
        aHeld = input.a(); bHeld = input.b();
        if (bPressed && (stage == Stage.FIRST_CHARGE || stage == Stage.SECOND_CHARGE)) {
            stage = Stage.AIM; firstCharge = 0; secondCharge = 0; meterPhase = 0;
            return List.of(new Event(Kind.CANCEL, null));
        }
        switch (stage) {
            case AIM -> {
                int change = (input.up() ? 1 : 0) - (input.down() ? 1 : 0);
                elevationDegrees = Math.clamp(elevationDegrees + change * GolfRules.ELEVATION_STEP_DEGREES,
                        0, GolfRules.MAX_ELEVATION_DEGREES);
                if (input.left() != input.right()) direction = input.left() ? -1 : 1;
                if (aPressed && !input.b()) {
                    stage = Stage.FIRST_CHARGE; meterPhase = 0;
                    return List.of(new Event(Kind.DUCK, null));
                }
            }
            case FIRST_CHARGE -> {
                if (aPressed) {
                    firstCharge = meterValue(); stage = Stage.SECOND_CHARGE; meterPhase = 0;
                    return List.of(new Event(Kind.CHARGE, null));
                }
                meterPhase = (meterPhase + 1) % GolfRules.METER_PERIOD_TICKS;
            }
            case SECOND_CHARGE -> {
                if (aPressed) {
                    secondCharge = meterValue(); shot = new GolfShot(direction, elevationDegrees, firstCharge, secondCharge);
                    stage = Stage.FEEDBACK; stageTicksRemaining = GolfRules.CHARGE_FEEDBACK_TICKS;
                    return List.of(new Event(Kind.CHARGE, null), new Event(Kind.COMMIT, shot));
                }
                meterPhase = (meterPhase + 1) % GolfRules.METER_PERIOD_TICKS;
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
