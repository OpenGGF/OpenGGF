package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import starpost.farm.FarmView;
import starpost.ui.Text;

/**
 * The opening (design doc §1): Sonic 1's ending, carried on. The farmer runs west through Green
 * Hill to its ending theme with the freed animals streaming behind; this time, at the old gate,
 * the run stops. The view folds down into an overgrown farm, the camera looks the damage over, a
 * Flicky lands on the hero's head, a note explains, and the old Star Post lights: the place to
 * come back to. It is played by the real game with scripted input, so what the player sees is
 * the game itself. Confirm skips it.
 */
final class IntroScreen implements Screen {
    private static final int TRAIL = 160;

    private PlayScreen play;
    private int t;
    private int phase;
    private int phaseAt;
    private boolean noteShown;
    private boolean noteClosed;
    private int litAt = -1;
    private final float[] trailX = new float[TRAIL];
    private final float[] trailY = new float[TRAIL];
    private int trailHead;
    private final List<Companion> companions = new ArrayList<>();
    private float flickyX;
    private float flickyY;

    /** A freed animal following the hero: a hopper on the ground or a Flicky in the air. */
    private final class Companion implements Actor {
        final String animal;
        final int lag;
        final boolean flyer;
        float x;
        float y;
        float hop;
        boolean facingLeft;

        Companion(String animal, int lag, boolean flyer) {
            this.animal = animal;
            this.lag = lag;
            this.flyer = flyer;
        }

        @Override
        public int view() {
            return play.onFarm() ? FARM : VALLEY;
        }

        @Override
        public float x() {
            return x;
        }

        @Override
        public float y() {
            return y;
        }

        @Override
        public void update(Shell shell, PlayScreen p) {
            int i = Math.floorMod(trailHead - lag, TRAIL);
            float nx = trailX[i], ny = trailY[i];
            facingLeft = nx < x || Math.abs(nx - x) < 0.5f && facingLeft;
            x = nx;
            y = ny;
            hop = flyer ? (float) Math.sin((t + lag * 7) / 9.0) * 6 - 24
                    : -Math.abs((float) Math.sin((t + lag * 5) / 7.0)) * 10;
        }

        @Override
        public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
            SceneSpriteSet set = flyer ? shell.art.flicky : shell.art.animal(animal);
            if (set == null) {
                return;
            }
            int frame = flyer ? (int) (t / 4 % 2) : hop < -4 ? 1 : 0;
            SceneSprite s = set.frame(Math.min(frame, set.frameCount() - 1));
            canvas.draw(s, x - cx, y + hop - cy - (s.height() - s.originY()), tint.withFlipX(!facingLeft));
        }
    }

    @Override
    public void enter(Shell shell) {
        play = new PlayScreen(shell);
        play.clockStopped = true;
        play.debugPlace(false, 1720, 0);
        play.valley().runner.speed = -6;
        play.valley().runner.facingLeft = true;
        String[] hoppers = {"pocky", "cucky", "picky", "ricky", "pecky", "rocky"};
        for (int i = 0; i < hoppers.length; i++) {
            companions.add(new Companion(hoppers[i], 14 + i * 13, false));
        }
        for (int i = 0; i < 3; i++) {
            companions.add(new Companion("flicky", 8 + i * 22, true));
        }
        // A cutscene must not change the save: nothing is picked up on the way.
        play.actors.removeIf(actor -> actor instanceof starpost.valley.Pickups.Pickup);
        play.actors.addAll(companions);
        play.hudHidden = true;
        play.valley().labels = false;
        for (int i = 0; i < TRAIL; i++) {
            trailX[i] = 1760;
            trailY[i] = 192;
        }
        shell.music.want("s1", Music.S1_ENDING);
    }

    @Override
    public void update(Shell shell) {
        boolean skip = shell.in.menu || shell.in.confirm && phase < 3;
        if (skip && t > 30) {
            finish(shell);
            return;
        }
        t++;
        script(shell);
        recordTrail();
        play.update(shell);
    }

    /** The scripted input for this tick, written over the real input. */
    private void script(Shell shell) {
        var in = shell.in;
        boolean wantsNote = phase == 3;
        in.consume();
        in.left = in.right = in.up = in.down = in.jumpHeld = false;
        FarmView farm = play.farm();
        switch (phase) {
            case 0 -> { // the run west through the valley, until the gate folds into the farm
                in.left = true;
                if (play.onFarm() && !play.folding()) {
                    next(1);
                }
            }
            case 1 -> { // walking in past the gate post
                play.gateLocked = true;
                in.left = t - phaseAt < 46;
                if (t - phaseAt > 70) {
                    farm.cameraTarget = 420;
                    next(2);
                }
            }
            case 2 -> { // the camera looks the overgrown field over, then comes back
                if (t - phaseAt == 150) {
                    farm.cameraTarget = Float.NaN;
                }
                if (t - phaseAt > 120) {
                    float tx = farm.runner.x, ty = farm.feetY() - 38;
                    flickyX += (tx - flickyX) * 0.06f;
                    flickyY += (ty - flickyY) * 0.06f;
                } else {
                    flickyX = farm.runner.x - 140;
                    flickyY = 40;
                }
                if (t - phaseAt > 230) {
                    next(3);
                }
            }
            case 3 -> { // the note
                if (!noteShown) {
                    noteShown = true;
                    shell.push(new NoteScreen(noteHeading(shell), note(shell), () -> noteClosed = true));
                }
                if (noteClosed) {
                    next(4);
                }
            }
            case 4 -> { // back to the old Star Post at the gate
                in.right = farm.runner.x < FarmView.GATE_X - 60;
                if (!in.right && Math.abs(farm.runner.speed) < 0.5f && litAt < 0) {
                    litAt = t;
                    shell.sfx(Sfx.STARPOST);
                }
                if (litAt >= 0 && t - litAt > 90) {
                    finish(shell);
                }
            }
            default -> {
            }
        }
        if (wantsNote) {
            in.left = in.right = false;
        }
    }

    private void next(int phase) {
        this.phase = phase;
        phaseAt = t;
    }

    private void recordTrail() {
        trailHead = (trailHead + 1) % TRAIL;
        if (play.onFarm()) {
            trailX[trailHead] = play.farm().runner.x + (play.farm().runner.facingLeft ? 18 : -18);
            trailY[trailHead] = play.farm().feetY() + 2;
        } else {
            trailX[trailHead] = play.valley().runner.x + 22;
            trailY[trailHead] = play.valley().runner.y;
        }
    }

    private static String noteHeading(Shell shell) {
        return shell.game.farmer.equals("tails") ? "A NOTE FROM SONIC" : "A NOTE FROM TAILS";
    }

    private static String note(Shell shell) {
        String farmer = shell.game.farmer.toUpperCase();
        if (shell.game.farmer.equals("tails")) {
            return "TAILS! THE ANIMALS WON'T STOP FOLLOWING ME, AND I CAN'T STOP RUNNING. "
                    + "YOU'RE THE ONE WHO FIXES THINGS. THIS OLD FARM IS YOURS IF YOU WANT IT. "
                    + "I'LL SWING BY. PROBABLY FAST. - SONIC";
        }
        return farmer + "! ROBOTNIK'S BADNIKS CHEWED UP HALF OF GREEN HILL, AND ALL THE ANIMALS YOU FREED "
                + "HAVE NOWHERE TO GO. NOBODY HAS FARMED THIS VALLEY SINCE THE OLD STAR POST WENT OUT. "
                + "IT'S YOURS IF YOU WANT IT! I LEFT YOU A WATER SHIELD AND SOME RING RADISH SEEDS. "
                + "COME SEE ME AT THE WORKSHOP IN TOWN. - TAILS";
    }

    private void finish(Shell shell) {
        play.clockStopped = false;
        shell.go(new MorningCard(() -> new PlayScreen(shell)));
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        play.draw(shell, canvas);
        int w = canvas.width(), h = canvas.height();
        // Letterbox bars over the HUD: this is a cutscene.
        canvas.fill(0, 0, w, 30, 0xFF000000);
        canvas.fill(0, h - 26, w, 26, 0xFF000000);
        if (phase == 2 && t - phaseAt > 120 && play.onFarm()) {
            FarmView farm = play.farm();
            SceneSprite f = shell.art.flicky.frame((int) (t / (t - phaseAt > 200 ? 12 : 4) % 2));
            canvas.draw(f, flickyX - farm.camera(), flickyY, SceneDraw.plain());
        }
        if (phase == 0 && t < 200) {
            int alpha = (int) Math.max(0, Math.min(255, (200 - t) * 4));
            // A dark band behind the title, so the town's signs never show through it.
            canvas.fill(0, 42, w, 52, (alpha * 13 / 16) << 24);
            shell.art.cardFont.centred(canvas, "GREEN HILL", 50, SceneDraw.plain().withAlpha(alpha / 255f));
            Text.centred(canvas, "AFTER THE CREDITS", 80, alpha << 24 | 0xFFDB00);
        }
        if (litAt >= 0) {
            long age = t - litAt;
            FarmView farm = play.farm();
            float sx = FarmView.GATE_X - farm.camera(), sy = FarmView.FIELD_TOP + 46 - 40;
            for (int i = 0; i < 6; i++) {
                double a = age / 6.0 + i * Math.PI / 3;
                int frame = 4 + (int) (age / 6 % 4);
                canvas.draw(shell.art.ring.frame(frame), sx + (float) Math.cos(a) * 18, sy + (float) Math.sin(a) * 10,
                        SceneDraw.plain());
            }
            if (age < 10) {
                canvas.fill(0, 0, w, h, (int) ((10 - age) * 20) << 24 | 0xFFFFFF);
            }
        }
        Text.right(canvas, "START: SKIP", w - 6, h - 18, Text.GREY);
    }
}
