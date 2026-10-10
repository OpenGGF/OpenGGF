package starpost.fishing;

import com.openggf.mods.state.SnapshotRandom;

/**
 * The Bubble Bar (design doc §6.6), engine-free. A column of water; the farmer holds the button
 * to make a Labyrinth air bubble rise and lets go to let it sink, keeping the hooked catch inside
 * it. Inside, the catch reels in; outside, the line pulls taut, the tension shrinks the bubble and
 * the catch works loose. Reel it all the way in to land it; let it all the way out and it escapes.
 *
 * <p>The submerged badniks move as their ROM objects do, at the bar's scale: the Chopper's leap
 * ({@code Chop_ChgSpeed}: launch at -$700, gravity $18 a frame, back to where it started), Jaws's
 * swim ({@code Jaws_Swim}: constant speed, turning every 64 frames per subtype), the Jawz torpedo
 * ({@code Obj_Jawz}: launched at $200 toward the player and never steered again) and the
 * Blastoid's burst ({@code AniRaw_BlastoidAttack}: 128 frames' wait, three shots 15 frames apart,
 * 64 frames' rest). Each shot kicks the turret up the column.
 */
public final class BubbleBar {
    /** The column's inside height in pixels; y runs down from the top. */
    public static final float HEIGHT = 144;
    public static final float FISH_HALF = 6;
    public static final float MIN_HALF = 8;
    /** The bubble: rising while held, sinking otherwise, bouncing a little off the bed. */
    public static final float RISE = 0.30f;
    public static final float SINK = 0.24f;
    public static final float MAX_SPEED = 4.5f;
    public static final float BOUNCE = 0.4f;
    /** Ticks at the start when the catch cannot slip (the hook is setting). */
    public static final int GRACE = 45;
    public static final float START_PROGRESS = 0.3f;

    public static final int PLAYING = 0;
    public static final int CAUGHT = 1;
    public static final int ESCAPED = 2;

    /** Chop_ChgSpeed: launch -$700, gravity $18 (pixels per frame). */
    static final float CHOP_LAUNCH = -0x700 / 256f;
    static final float CHOP_GRAVITY = 0x18 / 256f;
    /** The leap's height in the ROM: v^2 / 2g. */
    static final float CHOP_APEX = CHOP_LAUNCH * CHOP_LAUNCH / (2 * CHOP_GRAVITY);
    /** Obj_Jawz: Set_VelocityXTrackSonic with $200. */
    static final float JAWZ_SPEED = 0x200 / 256f;
    /** Jaws_Main: -$40, shown five times faster in the column. */
    static final float JAWS_SPEED = 0x40 / 256f * 5;

    public final int difficulty;
    public final int motion;
    public final float baseHalf;
    /** The bubble's centre and speed. */
    public float bubble;
    public float bubbleSpeed;
    /** The catch's centre and speed. */
    public float fish;
    public float fishSpeed;
    private float target;
    /** 0 escaped, 1 landed. */
    public float progress = START_PROGRESS;
    /** 0 slack to 1 taut: shrinks the bubble. */
    public float tension;
    public int ticks;
    /** Never once out of the bubble. */
    public boolean perfect = true;
    /** The Blastoid's animation frame (1 while firing), for drawing. */
    public int firing;
    public boolean facingLeft;
    // The badniks' ROM motions.
    private float romY;
    private float romSpeed;
    private int timer;
    private int turnDelay;
    private boolean darting;

    /**
     * @param difficulty 0-100
     * @param motion     a {@link FishDef} motion
     * @param baseHalf   the slack bubble's half height ({@link #bubbleHalf})
     */
    public BubbleBar(int difficulty, int motion, float baseHalf, SnapshotRandom rng) {
        this.difficulty = Math.max(0, Math.min(100, difficulty));
        this.motion = motion;
        this.baseHalf = Math.max(MIN_HALF, baseHalf);
        fish = HEIGHT - FISH_HALF - 4;
        if (motion == FishDef.BLASTOID) {
            fish = HEIGHT * 0.72f;
            timer = 0;
        } else if (motion == FishDef.JAWS) {
            fish = HEIGHT / 2;
            turnDelay = 64 * (1 + rng.nextInt(2)) - 1;
            timer = turnDelay;
            fishSpeed = -JAWS_SPEED;
            facingLeft = true;
        } else if (motion != FishDef.CHOPPER && motion != FishDef.RED_CHOPPER) {
            fish = HEIGHT * (0.3f + rng.nextInt(40) / 100f);
        }
        target = fish;
        bubble = Math.max(this.baseHalf, Math.min(HEIGHT - this.baseHalf, fish));
    }

    /** The slack bubble for a fishing level (0-10), bigger after Barnaby's lesson. */
    public static float bubbleHalf(int fishingLevel, boolean lesson) {
        return 22 + fishingLevel * 1.2f + (lesson ? 5 : 0);
    }

    /** The bubble's half height now: tension takes up to half of it. */
    public float half() {
        return Math.max(MIN_HALF, baseHalf * (1 - 0.5f * tension));
    }

    public boolean inside() {
        return Math.abs(fish - bubble) <= half();
    }

    /** One frame; returns {@link #PLAYING}, {@link #CAUGHT} or {@link #ESCAPED}. */
    public int step(boolean hold, SnapshotRandom rng) {
        ticks++;
        moveBubble(hold);
        moveFish(rng);
        float d = difficulty / 100f;
        if (inside()) {
            progress += 1f / (150 + difficulty * 1.5f);
            tension = Math.max(0, tension - 1f / 45);
        } else {
            if (ticks > GRACE) {
                perfect = false;
                progress -= (1 + tension) / (240 - difficulty);
            }
            tension = Math.min(1, tension + (0.5f + d) / 60);
        }
        if (progress >= 1) {
            progress = 1;
            return CAUGHT;
        }
        if (progress <= 0) {
            progress = 0;
            return ESCAPED;
        }
        return PLAYING;
    }

    private void moveBubble(boolean hold) {
        bubbleSpeed += hold ? -RISE : SINK;
        bubbleSpeed = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, bubbleSpeed));
        bubble += bubbleSpeed;
        float half = half();
        if (bubble > HEIGHT - half) {
            bubble = HEIGHT - half;
            bubbleSpeed = bubbleSpeed > 1 ? -bubbleSpeed * BOUNCE : 0;
        } else if (bubble < half) {
            bubble = half;
            bubbleSpeed = 0;
        }
    }

    private void moveFish(SnapshotRandom rng) {
        float before = fish;
        switch (motion) {
            case FishDef.CHOPPER -> leap(1);
            case FishDef.RED_CHOPPER -> redChopper(rng);
            case FishDef.JAWS -> jaws();
            case FishDef.JAWZ -> jawz();
            case FishDef.BLASTOID -> blastoid();
            case FishDef.LOOPS -> loops();
            default -> seek(rng);
        }
        fish = Math.max(FISH_HALF, Math.min(HEIGHT - FISH_HALF, fish));
        if (fish != before && motion != FishDef.JAWS) {
            facingLeft = fish < before;
        }
    }

    /** The leap's scale in the column: a full leap reaches the top. */
    private static float leapScale() {
        return (HEIGHT - 2 * FISH_HALF - 6) / CHOP_APEX;
    }

    private static float bed() {
        return HEIGHT - FISH_HALF - 2;
    }

    /**
     * One frame of the Chopper's leap from the bed. Returns true when it is back on the bed,
     * where Chop_ChgSpeed launches it again at {@code strength} of -$700.
     */
    private boolean leap(float strength) {
        romY += romSpeed;
        romSpeed += CHOP_GRAVITY;
        boolean landed = romY >= 0;
        if (landed) {
            romY = 0;
            romSpeed = CHOP_LAUNCH * strength;
        }
        fish = bed() + romY * leapScale();
        return landed;
    }

    /** Leaps of every height, and now and then a dart along the bed before the next one. */
    private void redChopper(SnapshotRandom rng) {
        if (darting) {
            seek(rng);
            if (--timer <= 0) {
                darting = false;
                romY = Math.min(0, (fish - bed()) / leapScale());
                romSpeed = CHOP_LAUNCH * (0.6f + rng.nextInt(41) / 100f);
            }
            return;
        }
        if (leap(0.6f + rng.nextInt(41) / 100f) && rng.nextInt(3) == 0) {
            darting = true;
            timer = 30 + rng.nextInt(60);
            target = HEIGHT * (0.5f + rng.nextInt(45) / 100f);
        }
    }

    /** Jaws swims at a constant speed and turns on its timer (or at the column's ends). */
    private void jaws() {
        fish += fishSpeed;
        if (--timer < 0 || fish <= FISH_HALF + 1 || fish >= HEIGHT - FISH_HALF - 1) {
            timer = turnDelay;
            fishSpeed = -fishSpeed;
            facingLeft = !facingLeft;
        }
    }

    /** The Jawz charges where the bubble is, never steering; at an end it turns and charges again. */
    private void jawz() {
        if (timer > 0) {
            timer--;
            if (timer == 0) {
                fishSpeed = bubble < fish ? -JAWZ_SPEED : JAWZ_SPEED;
            }
            return;
        }
        if (fishSpeed == 0) {
            fishSpeed = bubble < fish ? -JAWZ_SPEED : JAWZ_SPEED;
        }
        fish += fishSpeed;
        if (fish <= FISH_HALF || fish >= HEIGHT - FISH_HALF) {
            fishSpeed = 0;
            timer = 24;
        }
    }

    /** The Blastoid waits, fires three shots (each kicking it up), rests, and sinks back between. */
    private void blastoid() {
        int t = timer++ % 227;
        boolean shot = t == 128 || t == 143 || t == 158;
        firing = t >= 128 && t < 163 && (t - 128) % 15 < 5 ? 1 : 0;
        if (shot) {
            fishSpeed = -2.6f;
        }
        fishSpeed += 0.06f;
        fishSpeed *= 0.97f;
        fish += fishSpeed;
        float rest = HEIGHT * 0.78f;
        if (fish > rest && fishSpeed > 0) {
            fish = rest;
            fishSpeed = 0;
        }
    }

    /** The Loop Pike: steady loops up and down the column, faster on harder days. */
    private void loops() {
        float period = 200 - difficulty;
        fish = HEIGHT / 2 + (float) Math.sin(ticks * 2 * Math.PI / period) * HEIGHT * 0.38f
                + (float) Math.sin(ticks * 2 * Math.PI / (period * 0.37)) * HEIGHT * 0.06f;
    }

    /** A fish: picks somewhere new now and then and swims toward it. */
    private void seek(SnapshotRandom rng) {
        float d = difficulty / 100f;
        float chance = switch (motion) {
            case FishDef.DART -> 0.012f + 0.03f * d;
            case FishDef.MIXED -> 0.009f + 0.02f * d;
            default -> 0.006f + 0.012f * d;
        };
        if (rng.nextInt(10000) < chance * 10000) {
            float reach = HEIGHT * (0.2f + 0.55f * d) * (motion == FishDef.DART ? 1.3f : 1);
            float bias = motion == FishDef.SINKER ? HEIGHT * 0.18f : motion == FishDef.FLOATER ? -HEIGHT * 0.18f : 0;
            target = fish + (rng.nextInt(2001) - 1000) / 1000f * reach + bias;
            target = Math.max(FISH_HALF, Math.min(HEIGHT - FISH_HALF, target));
        }
        float stiffness = (motion == FishDef.SMOOTH ? 0.004f : motion == FishDef.DART ? 0.02f : 0.01f) * (0.6f + d);
        fishSpeed += (target - fish) * stiffness;
        fishSpeed *= motion == FishDef.DART ? 0.8f : 0.88f;
        fish += fishSpeed;
    }
    public record Snapshot(int difficulty,int motion,float baseHalf,float bubble,float bubbleSpeed,float fish,
        float fishSpeed,float target,float progress,float tension,int ticks,boolean perfect,int firing,
        boolean facingLeft,float romY,float romSpeed,int timer,int turnDelay,boolean darting) {}
    public Snapshot capture() {
        return new Snapshot(difficulty,motion,baseHalf,bubble,bubbleSpeed,fish,fishSpeed,target,progress,tension,
            ticks,perfect,firing,facingLeft,romY,romSpeed,timer,turnDelay,darting);
    }
    public static BubbleBar restore(Snapshot s) {
        var b=new BubbleBar(s.difficulty(),s.motion(),s.baseHalf(),new SnapshotRandom(1));
        b.bubble=s.bubble(); b.bubbleSpeed=s.bubbleSpeed(); b.fish=s.fish(); b.fishSpeed=s.fishSpeed();
        b.target=s.target(); b.progress=s.progress(); b.tension=s.tension(); b.ticks=s.ticks(); b.perfect=s.perfect();
        b.firing=s.firing(); b.facingLeft=s.facingLeft(); b.romY=s.romY(); b.romSpeed=s.romSpeed();
        b.timer=s.timer(); b.turnDelay=s.turnDelay(); b.darting=s.darting(); return b;
    }
}
