package starpost.people;

/**
 * One instruction of a heart event's script. Positions are pixels from where the farmer stood
 * when the event began (east is positive), so a scene plays wherever it triggers.
 *
 * @param op      what to do
 * @param who     a villager id, {@code farmer}, or {@code narrator} (for {@link Op#SAY})
 * @param a       an x offset, a count, a duration, a direction or an id, by op
 * @param b       a second number (depth on the farm, walking speed in tenths of a pixel)
 * @param text    words to say, an item id, a flag, an emote or a pose, by op
 * @param pics    the picture version of a line (animals before the translator), or null
 * @param farmer  when set, the step plays only when this hero farms
 */
public record Step(Op op, String who, int a, int b, String text, String[] pics, String farmer) {
    /** The instructions. Blocking ones wait to finish before the next step starts. */
    public enum Op {
        /** Puts an actor at a spot and shows it (instant). */
        PLACE,
        /** Walks to a spot (blocking). */
        WALK,
        /** Starts walking to a spot without waiting. */
        MOVE,
        /** Turns: {@code a} -1 west, 1 east, 0 toward the farmer (for the farmer: toward {@code who2} in text). */
        FACE,
        /** A bubble over the head for {@code a} ticks (blocking). */
        EMOTE,
        /** Speaks in the dialogue box (blocking until confirmed). */
        SAY,
        /** Waits {@code a} ticks. */
        WAIT,
        /** Gives the farmer {@code a} of item {@code text} (if the item exists). */
        GIVE,
        /** Sets a story flag. */
        FLAG,
        /** Plays a song: {@code text} the game, {@code a} the id. */
        MUSIC,
        /** Plays a sound effect {@code a}. */
        SFX,
        /** Holds a pose ({@code laugh}, {@code surprise}, {@code look_up}, {@code idle}...). */
        POSE,
        /** A little jump on the spot (blocking). */
        HOP,
        /** Walks off to {@code a} and disappears (blocking). */
        LEAVE,
        /** Fades the screen to black ({@code a} 1) or back ({@code a} 0), blocking. */
        FADE,
        /** The farmer turns to face {@code who}. */
        TURN_FARMER
    }
}
