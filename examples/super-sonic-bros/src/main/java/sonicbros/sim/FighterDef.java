package sonicbros.sim;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything that makes one fighter different: physics, size, weight, the pose for each basic
 * action, every attack and special, and the throws.
 */
public final class FighterDef {
    /** Which input runs which move. */
    public enum Slot {
        JAB, FTILT, UTILT, DTILT, DASH, FSMASH, USMASH, DSMASH,
        NAIR, FAIR, BAIR, UAIR, DAIR,
        NSPEC, SSPEC, USPEC, DSPEC,
        LEDGE_ATTACK, GETUP_ATTACK, GRAB, PUMMEL
    }

    /**
     * Movement and body.
     *
     * @param hover     pixels the body floats above the floor (Robotnik's Egg Mobile)
     */
    public record Stats(double weight, double walk, double run, double groundAccel, double friction,
            double airSpeed, double airAccel, double airFriction, double gravity, double fall, double fastFall,
            double jump, double shortHop, double airJump, int airJumps, double width, double height,
            double hover, int jumpSquat) { }

    /**
     * Poses for the basic actions. Each array is an animation cycle of pose ids that the view
     * turns into sprites; {@code period} is frames per pose for the idle, walk and air cycles.
     */
    public record Poses(int[] idle, int[] walk, int[] run, int[] jump, int[] fall, int[] airJump, int[] crouch,
            int[] land, int[] hurt, int[] tumble, int[] shield, int[] ledge, int[] dodge, int[] dizzy,
            int[] victory, int[] helpless, int[] hold, int[] skid, int[] lying, int period) { }

    /** A throw: how the held fighter is launched and on which frame. */
    public record Throw(int frame, int duration, double damage, int angle, double baseKb, double growth, int pose,
            double holdX, double holdY) { }

    public final String id;
    public final String name;
    public final String blurb;
    public final Stats stats;
    public final Poses poses;
    public final Map<Slot, Move> moves;
    public final Throw forwardThrow;
    public final Throw backThrow;
    public final Throw upThrow;
    public final Throw downThrow;
    /** Up-special height gain, for the CPU's recovery planning. */
    public final double recoveryRise;
    /** Up-special sideways reach. */
    public final double recoveryReach;

    public FighterDef(String id, String name, String blurb, Stats stats, Poses poses, Map<Slot, Move> moves,
            Throw forwardThrow, Throw backThrow, Throw upThrow, Throw downThrow, double recoveryRise,
            double recoveryReach) {
        this.id = id;
        this.name = name;
        this.blurb = blurb;
        this.stats = stats;
        this.poses = poses;
        this.moves = new EnumMap<>(moves);
        this.forwardThrow = forwardThrow;
        this.backThrow = backThrow;
        this.upThrow = upThrow;
        this.downThrow = downThrow;
        this.recoveryRise = recoveryRise;
        this.recoveryReach = recoveryReach;
    }

    public Move move(Slot slot) { return moves.get(slot); }
}
