package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import starpost.art.Anim;
import starpost.art.Art;
import starpost.core.Game;
import starpost.scene.Sfx;
import starpost.ui.Text;
import starpost.valley.Runner;

/**
 * The Great Valley Race on its own Green Hill circuit (see {@link RaceTrack}): twice round on the
 * ported controller, springs and loops and all, against the rivals of {@link Race} (the heroes on
 * the same physics with scripted pads, Robotnik flying the Egg Mobile). A countdown of three
 * lets everyone rev a spin dash; Sonic 3 &amp; Knuckles' Knuckles theme plays; the signpost at
 * the line spins for the winner, stopping on Robotnik's face if the Egg Mobile got there first.
 */
final class RaceScreen extends FestivalScreen {
    private static final int KNUCKLES_THEME = 0x1F;     // Sonic3kMusic KNUCKLES
    private static final int START_X = 96;
    private static final float LOOP_CX = 126;
    private static final float LOOP_CY = 111;
    private static final float LOOP_R = 60;
    /** Give up waiting this long after the last rival crosses the line. */
    private static final int LATE = 60 * 25;

    /** One racer on foot: the controller, its animation and its loop. */
    private static final class Lane {
        final String who;
        final Runner runner;
        final Race.Rival rival;
        final Anim anim = new Anim();
        boolean looping;
        float loopAngle;
        int loopBase;
        int finish = -1;
        long springAt = -100;

        Lane(String who, Runner runner, Race.Rival rival) {
            this.who = who;
            this.runner = runner;
            this.rival = rival;
        }
    }

    private RaceTrack track;
    private Lane farmer;
    private final List<Lane> lanes = new ArrayList<>();
    private final List<Race.Rival> rivals = new ArrayList<>();
    private Race.Rival egg;
    private float camX;
    private float camY;
    private int go;           // ticks since GO (negative during the countdown)
    private int done = -1;
    private int lastFinish = -1;
    private long signAt = -1000;
    private boolean eggWon;

    RaceScreen(FestivalSystem sys, Festival festival) {
        super(sys, festival);
    }

    @Override
    void begin() {
        track = new RaceTrack(shell.art);
        Game game = shell.game;
        int floor = track.floorBelow(START_X, 0);
        farmer = new Lane(game.farmer, new Runner(START_X, floor), null);
        lanes.add(farmer);
        int slot = 1;
        for (String who : Race.rivals(game)) {
            Race.Rival r = new Race.Rival(who, START_X - slot * 22, track.floorBelow(START_X - slot * 22, 0));
            rivals.add(r);
            if (r.flies()) {
                egg = r;
            } else {
                lanes.add(new Lane(who, r.runner, r));
            }
            slot++;
        }
        go = -Race.COUNTDOWN;
        camX = START_X - shell.width() / 2f;
        camY = floor - 150;
        shell.music.want("s3k", KNUCKLES_THEME);
    }

    private int distance() {
        return Race.LAPS * track.length();
    }

    @Override
    void step() {
        go++;
        if (go < 0 && (-go) % 60 == 0) {
            shell.sfx(Sfx.SWITCH);
        }
        if (go == 0) {
            shell.sfx(Sfx.STARPOST);
        }
        float leader = leaderX();
        for (Lane lane : lanes) {
            stepLane(lane, leader);
        }
        if (egg != null) {
            egg.step(track, go, leader, track.length());
            if (egg.finish < 0 && egg.eggX - START_X >= distance()) {
                egg.finish = go;
                crossed(egg.who);
            }
        }
        // Over the line, the camera stays on the signpost while it spins (Sonic 1's act end).
        float target = farmer.finish >= 0 ? START_X + distance() - shell.width() / 2f + 40
                : farmer.runner.x - shell.width() / 2f + (farmer.runner.facingLeft ? -24 : 24);
        camX += (target - camX) * 0.2f;
        camY += (Math.max(-64, Math.min(Art.BLOCK - shell.height(), farmer.runner.y - 150)) - camY) * 0.15f;
        if (done < 0 && farmer.finish >= 0) {
            done = t;
        }
        if (done < 0 && lastFinish >= 0 && go - lastFinish > LATE && allRivalsIn()) {
            done = t;            // the farmer gave up a long way back: placed last
        }
        if (done >= 0 && t - done == 150) {
            end();
        }
    }

    private boolean allRivalsIn() {
        for (Race.Rival r : rivals) {
            if (r.finish < 0) {
                return false;
            }
        }
        return true;
    }

    private float leaderX() {
        float best = -Float.MAX_VALUE;
        for (Lane lane : lanes) {
            best = Math.max(best, lane.runner.x);
        }
        if (egg != null) {
            best = Math.max(best, egg.eggX);
        }
        return best;
    }

    private void stepLane(Lane lane, float leader) {
        Runner r = lane.runner;
        if (lane.finish >= 0) {
            // Over the line: coast to a stop.
            r.step(track, false, false, false, false, false, go);
            animate(lane);
            return;
        }
        if (lane.looping) {
            stepLoop(lane);
        } else if (lane.rival != null) {
            if (lane.rival.step(track, go, leader, track.length())) {
                shellJump(lane);
            }
        } else {
            var in = shell.in;
            boolean countdown = go < 0;
            boolean down = in.down || countdown && r.dashing;
            if (r.step(track, !countdown && in.left, !countdown && in.right, down, in.jump, in.jumpHeld, go)) {
                shell.sfx(Sfx.JUMP);
            }
            if (r.dashing && in.jump) {
                shell.sfx(Sfx.SPINDASH);
            }
        }
        // The spring on the totem ledge, and the loops, for everyone on foot.
        int spring = Math.floorDiv(Math.round(r.x), track.length()) * track.length() + track.springOffset();
        if (Math.abs(r.x - spring) < 12 && r.y >= Art.FLOOR - 2 && (r.onGround || r.ySpeed > 0) && !lane.looping) {
            r.spring(10);
            lane.springAt = ticks;
            if (lane == farmer) {
                shell.sfx(Sfx.SPRING);
            }
        }
        if (!lane.looping && r.onGround && Math.abs(r.speed) >= 4 && r.speed > 0 && track.loopEntry(r.x)) {
            lane.looping = true;
            lane.loopAngle = -0.35f;
            lane.loopBase = track.loopStart(r.x);
        }
        if (r.x - START_X >= distance() && lane.finish < 0) {
            lane.finish = go;
            if (lane.rival != null) {
                lane.rival.finish = go;
            }
            crossed(lane.who);
        }
        animate(lane);
    }

    private void shellJump(Lane lane) {
        if (Math.abs(lane.runner.x - farmer.runner.x) < shell.width() / 2f) {
            shell.sfx(Sfx.JUMP);
        }
    }

    /** Someone crossed the line: the first spins the signpost. */
    private void crossed(String who) {
        if (lastFinish < 0) {
            signAt = ticks;
            eggWon = who.equals("robotnik");
            shell.sfx(Sfx.SIGNPOST);
        }
        lastFinish = go;
    }

    /** The scripted loop, as the valley does it: round the circle at the runner's speed. */
    private void stepLoop(Lane lane) {
        Runner r = lane.runner;
        lane.loopAngle += Math.max(0.07f, r.speed / LOOP_R);
        if (lane.loopAngle >= (float) (Math.PI * 2) - 0.1f) {
            lane.looping = false;
            r.x = lane.loopBase + 184;
            r.y = Art.FLOOR;
            r.onGround = true;
            return;
        }
        r.x = lane.loopBase + LOOP_CX + (float) Math.sin(lane.loopAngle) * LOOP_R;
        r.y = LOOP_CY + (float) Math.cos(lane.loopAngle) * LOOP_R;
    }

    private void animate(Lane lane) {
        Runner r = lane.runner;
        float speed = Math.abs(r.speed);
        Anim anim = lane.anim;
        if (lane.looping || r.rolling || !r.onGround && !r.sprung) {
            anim.set(Anim.ROLL, Math.max(0, 4 - (int) Math.max(speed, lane.looping ? 6 : 0)));
        } else if (r.sprung) {
            anim.set(Anim.SPRING, 2);
        } else if (r.dashing) {
            anim.set(Anim.SPINDASH, 0);
        } else if (r.ducking) {
            anim.set(Anim.DUCK, 6);
        } else if (speed > 0.05f) {
            anim.set(speed >= Runner.TOP ? Anim.RUN : Anim.WALK, Math.max(0, 8 - (int) speed));
        } else {
            anim.set(Anim.WAIT, 6);
        }
        anim.tick();
    }

    private int place() {
        return Race.place(farmer.finish, rivals);
    }

    private void end() {
        Game game = shell.game;
        int place = place();
        int time = farmer.finish >= 0 ? farmer.finish : go;
        List<String> lines = new ArrayList<>();
        lines.add(farmer.finish >= 0 ? "TIME " + seconds(time) + "  -  " + ordinal(place) + " PLACE"
                : "DID NOT FINISH - " + ordinal(place));
        lines.addAll(Race.reward(game, festivals, place, time));
        for (Race.Rival r : rivals) {
            Prizes.friendship(game, r.who, 30);
        }
        finish(place == 1 ? "YOU WIN" : placeWord(place) + " PLACE", lines);
        if (eggWon) {
            caption("robotnik", "HO HO HO! THE EGG MOBILE IS A VEHICLE. THE RULES SAY NOTHING. I CHECKED. I WROTE THEM.");
        } else if (place == 1) {
            caption(rivals.get(0).who, rivals.get(0).who.equals("tails") ? "YOU'RE SO FAST! TEACH ME THAT SPIN DASH AGAIN!"
                    : "NOT BAD. NOT BAD AT ALL.");
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void paint(SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        var look = shell.art.season(shell.game.calendar.season());
        int cx = Math.round(camX), cy = Math.round(camY);
        canvas.drawBackdrop(look.backdrop(0), 0, 0, w, h, Math.max(0, Math.min(32, Math.round(8 + camY * 0.1f))), camX,
                shell.ticks);
        SceneDraw tint = SceneDraw.plain();
        for (int column = Math.floorDiv(cx, Art.BLOCK); column <= Math.floorDiv(cx + w, Art.BLOCK); column++) {
            int id = track.blocks[Math.floorMod(column, track.blocks.length)];
            SceneImage block = look.block(id);
            if (block == null) {
                continue;
            }
            int x = column * Art.BLOCK - cx;
            canvas.draw(block, x, -cy, tint);
            canvas.drawRegion(block, 0, Art.BLOCK - 32, Art.BLOCK, 32, x, Art.BLOCK - cy, Art.BLOCK, 32, tint);
        }
        int lap = track.length();
        int firstLap = Math.floorDiv(cx - 200, lap);
        for (int l = firstLap; l <= firstLap + 2; l++) {
            int spring = l * lap + track.springOffset();
            boolean fired = false;
            for (Lane lane : lanes) {
                fired |= ticks - lane.springAt < 12 && Math.abs(lane.runner.x - spring) < 40;
            }
            FestivalSystem.standSprite(canvas, shell.art.spring, fired ? 1 : 0, spring - cx,
                    track.floorBelow(spring, 0) - cy, tint);
            // The line each lap: Sonic 1's signpost; at the finish it spins for the first across.
            int line = l * lap + START_X;
            if (l >= 0 && l <= Race.LAPS && Math.abs(line - cx - w / 2) < w) {
                int frame = 4;
                if (l == Race.LAPS && signAt >= 0) {
                    long age = ticks - signAt;
                    frame = age < 90 ? 1 + (int) (age / 3 % 3) : eggWon ? 0 : 4;
                }
                FestivalSystem.standSprite(canvas, shell.art.signpost, frame, line - cx,
                        track.floorBelow(line, 0) - cy, tint);
            }
        }
        if (egg != null) {
            drawEgg(canvas, egg.eggX - cx, egg.eggY - cy);
        }
        for (int i = lanes.size() - 1; i >= 0; i--) {
            drawLane(canvas, lanes.get(i), cx, cy);
        }
    }

    private void drawLane(SceneCanvas canvas, Lane lane, int cx, int cy) {
        Runner r = lane.runner;
        SceneSpriteSet set = shell.art.farmer(lane.who);
        SceneSprite pose = lane.anim.pose(set);
        SceneDraw style = SceneDraw.plain().withFlipX(r.facingLeft);
        float feet = r.y - cy;
        float originY = lane.looping ? feet : lane.anim.id() == Anim.ROLL ? feet - 15 : feet - (pose.height() - pose.originY());
        if (lane.who.equals("tails")) {
            Anim.drawTails(canvas, shell.art.tailsTails, lane.anim.id(), shell.ticks, r.x - cx, originY, style);
        }
        canvas.draw(pose, r.x - cx, originY, style);
    }

    /** The Egg Mobile: Map_RobotnikShip's ship (5) with Robotnik in it (2), as Sitar Hero stacks them. */
    private void drawEgg(SceneCanvas canvas, float x, float y) {
        SceneSpriteSet ship = sys.art.eggMobile();
        if (ship == null || ship.frameCount() < 7) {
            return;
        }
        SceneDraw style = SceneDraw.plain().withFlipX(true);
        if (egg.boosting() && ticks / 2 % 2 == 0) {
            canvas.draw(ship.frame(6), x - 30, y + 10, style);     // the booster's flame
        }
        canvas.draw(ship.frame(5), x, y + 8, style);
        canvas.draw(ship.frame(2), x, y - 8, style);
    }

    @Override
    void paintOver(SceneCanvas canvas) {
        var hud = shell.art.hud;
        int time = farmer.finish >= 0 ? farmer.finish : Math.max(0, go);
        canvas.draw(hud.time, 16, 6, SceneDraw.plain());
        hud.number(canvas, clock(time), 66, 2);
        int lap = Math.min(Race.LAPS, 1 + (int) Math.max(0, (farmer.runner.x - START_X) / track.length()));
        Text.shadow(canvas, "LAP " + lap + "/" + Race.LAPS, 150, 10, Text.YELLOW);
        int position = 1;
        for (Race.Rival r : rivals) {
            float rx = r.flies() ? r.eggX : r.runner.x;
            if (r.finish >= 0 && (farmer.finish < 0 || r.finish < farmer.finish) || farmer.finish < 0 && r.finish < 0
                    && rx > farmer.runner.x) {
                position++;
            }
        }
        hud.number(canvas, Integer.toString(position), canvas.width() - 70, 2);
        Text.shadow(canvas, ordinal(position).substring(1), canvas.width() - 54, 10, Text.WHITE);
        if (go < 0) {
            countdown(canvas, -go, Race.COUNTDOWN);
            Text.centred(canvas, "HOLD DOWN, TAP JUMP: REV A SPIN DASH", canvas.height() - 16, Text.YELLOW);
        } else if (go < 45) {
            countdown(canvas, -go, Race.COUNTDOWN);
        }
        if (farmer.finish >= 0 && done >= 0 && !showingResults()) {
            shell.art.cardFont.centred(canvas, placeWord(place()), 90, SceneDraw.plain());
        }
    }
}
