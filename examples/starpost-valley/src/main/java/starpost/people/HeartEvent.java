package starpost.people;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import starpost.people.Step.Op;

/**
 * A heart event: a short scene that plays once, when the farmer comes to the right place at the
 * right time with enough hearts. Written fluently by the cast:
 *
 * <pre>
 * cast.event("tails_2", "tails", 2).near("workshop", 80).between(900, 1800).dry()
 *         .enter("tails", 1, 200, 36)
 *         .say("tails", "{FARMER}! LOOK WHAT I BUILT!")
 *         .flag("chirp_translator");
 * </pre>
 */
public final class HeartEvent {
    public final String id;
    public final String villager;
    public final int hearts;
    private boolean farm;
    private String anchor;
    private int radius = 96;
    private int from = Schedule.minutes(600);
    private int to = Schedule.minutes(2600);
    private int seasons = 15;
    private int weather = Schedule.ANY;
    private String requires;
    private String farmerOnly;
    private boolean partner;
    private String summary = "";
    private final List<Step> steps = new ArrayList<>();

    HeartEvent(String id, String villager, int hearts) {
        this.id = id;
        this.villager = villager;
        this.hearts = hearts;
    }

    // ------------------------------------------------------------------ trigger

    /** In the valley, within {@code radius} pixels of an anchor. */
    public HeartEvent near(String valleyAnchor, int radius) {
        this.anchor = valleyAnchor;
        this.radius = radius;
        return this;
    }

    /** Anywhere on the farm (belt view). */
    public HeartEvent onFarm() {
        farm = true;
        anchor = null;
        return this;
    }

    public HeartEvent between(int fromHhmm, int toHhmm) {
        from = Schedule.minutes(fromHhmm);
        to = Schedule.minutes(toHhmm);
        return this;
    }

    public HeartEvent seasons(int... list) {
        seasons = 0;
        for (int s : list) {
            seasons |= 1 << s;
        }
        return this;
    }

    public HeartEvent dry() {
        weather = Schedule.DRY;
        return this;
    }

    public HeartEvent rain() {
        weather = Schedule.RAIN;
        return this;
    }

    /** Needs another event seen (or a story flag set) first. */
    public HeartEvent requires(String eventOrFlag) {
        requires = eventOrFlag;
        return this;
    }

    /** Only when this hero farms. */
    public HeartEvent farmer(String code) {
        farmerOnly = code;
        return this;
    }

    /** The 10-heart event: they become a Partner when it ends. */
    public HeartEvent partner() {
        partner = true;
        return this;
    }

    /** What happened, for the design doc's tables and the debug list. */
    public HeartEvent summary(String text) {
        summary = text;
        return this;
    }

    // ------------------------------------------------------------------ script

    /** Puts an actor {@code dx} from the farmer (and {@code depth} deeper on the farm) and shows it. */
    public HeartEvent place(String who, int dx) {
        return step(Op.PLACE, who, dx, 0, null, null);
    }

    public HeartEvent place(String who, int dx, int depth) {
        return step(Op.PLACE, who, dx, depth, null, null);
    }

    /** Walks in from off screen: appears {@code from} pixels away on side {@code side}, stops {@code stop} away. */
    public HeartEvent enter(String who, int side, int from, int stop) {
        step(Op.PLACE, who, side * from, 0, null, null);
        return step(Op.WALK, who, side * stop, 0, null, null);
    }

    public HeartEvent walk(String who, int dx) {
        return step(Op.WALK, who, dx, 0, null, null);
    }

    /** Walks at a speed in tenths of a pixel per tick (10 is a stroll, 40 a sprint). */
    public HeartEvent walk(String who, int dx, int speedTenths) {
        return step(Op.WALK, who, dx, speedTenths, null, null);
    }

    public HeartEvent move(String who, int dx) {
        return step(Op.MOVE, who, dx, 0, null, null);
    }

    /** Turns west (-1), east (1) or toward the farmer (0). */
    public HeartEvent face(String who, int direction) {
        return step(Op.FACE, who, direction, 0, null, null);
    }

    /** The farmer turns toward someone. */
    public HeartEvent turnTo(String who) {
        return step(Op.TURN_FARMER, who, 0, 0, null, null);
    }

    /** A bubble over the head: {@code !}, {@code ?}, {@code heart}, {@code ...}, {@code note}, {@code sweat}, {@code anger}. */
    public HeartEvent emote(String who, String emote) {
        return step(Op.EMOTE, who, 50, 0, emote, null);
    }

    public HeartEvent say(String who, String text) {
        return step(Op.SAY, who, 0, 0, check(text), null);
    }

    /** A line with its picture version (for an animal before the translator). */
    public HeartEvent say(String who, String text, String... pictures) {
        return step(Op.SAY, who, 0, 0, check(text), pictures.clone());
    }

    /** A line only when a particular hero farms (hero villagers address each farmer differently). */
    public HeartEvent sayIf(String farmerCode, String who, String text) {
        steps.add(new Step(Op.SAY, who, 0, 0, check(text), null, farmerCode));
        return this;
    }

    /** A farmer-specific line with its picture version. */
    public HeartEvent sayIf(String farmerCode, String who, String text, String... pictures) {
        steps.add(new Step(Op.SAY, who, 0, 0, check(text), pictures.clone(), farmerCode));
        return this;
    }

    /** Narration, without a speaker. */
    public HeartEvent narrate(String text) {
        return step(Op.SAY, "narrator", 0, 0, check(text), null);
    }

    public HeartEvent pause(int ticks) {
        return step(Op.WAIT, null, ticks, 0, null, null);
    }

    public HeartEvent give(String item, int count) {
        return step(Op.GIVE, null, count, 0, item, null);
    }

    public HeartEvent flag(String storyFlag) {
        return step(Op.FLAG, null, 0, 0, storyFlag, null);
    }

    public HeartEvent music(String game, int id) {
        return step(Op.MUSIC, null, id, 0, game, null);
    }

    public HeartEvent sfx(int id) {
        return step(Op.SFX, null, id, 0, null, null);
    }

    public HeartEvent pose(String who, String pose) {
        return step(Op.POSE, who, 0, 0, pose, null);
    }

    public HeartEvent hop(String who) {
        return step(Op.HOP, who, 0, 0, null, null);
    }

    /** Walks off to {@code dx} and disappears. */
    public HeartEvent leave(String who, int dx) {
        return step(Op.LEAVE, who, dx, 0, null, null);
    }

    public HeartEvent fadeOut() {
        return step(Op.FADE, null, 1, 0, null, null);
    }

    public HeartEvent fadeIn() {
        return step(Op.FADE, null, 0, 0, null, null);
    }

    private HeartEvent step(Op op, String who, int a, int b, String text, String[] pics) {
        steps.add(new Step(op, who, a, b, text, pics, null));
        return this;
    }

    private static String check(String text) {
        if (!text.equals(text.toUpperCase())) {
            throw new IllegalArgumentException("Lines are upper case: " + text);
        }
        return text;
    }

    // ------------------------------------------------------------------ queries

    public boolean onFarmView() {
        return farm;
    }

    public String anchor() {
        return anchor;
    }

    public int radius() {
        return radius;
    }

    public boolean isPartner() {
        return partner;
    }

    public String requirement() {
        return requires;
    }

    public String farmerOnly() {
        return farmerOnly;
    }

    public String summary() {
        return summary;
    }

    public List<Step> steps() {
        return Collections.unmodifiableList(steps);
    }

    /** Whether the time, date, weather, farmer and prerequisites allow it (place and hearts aside). */
    public boolean timely(Situation s) {
        return (seasons & 1 << s.season()) != 0 && s.minutes() >= from && s.minutes() < to
                && (weather == Schedule.ANY || weather == (s.raining() ? Schedule.RAIN : Schedule.DRY))
                && (farmerOnly == null || farmerOnly.equals(s.farmer()))
                && (requires == null || s.seenEvent(requires) || s.flags().contains(requires));
    }
}
