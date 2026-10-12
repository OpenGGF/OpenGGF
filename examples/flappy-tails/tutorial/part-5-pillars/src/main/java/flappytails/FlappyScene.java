package flappytails;

import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneKeys;

/**
 * Tutorial part 5: a game. Pillars cut from Angel Island's own ground slide in from the right
 * ({@link ZoneArt}, {@link Course}); each one passed scores a point; touching one, or the
 * ground, ends the flight, and the best score is saved. This is all of Flappy Bird. The rest of
 * the tutorial turns it into the finished example: the tour of five zones, rules with rings,
 * the screens, and the polish.
 */
public final class FlappyScene implements ModScene {
    private static final int TAILS_X = 112;
    private static final int START_Y = 100;
    /** Tails' touch box ({@code Touch_NoInstaShield}): 8 pixels either side, y_radius - 3 above and below. */
    private static final int HALF_WIDTH = 8;
    private static final int HALF_HEIGHT = 0x0F - 3;
    private static final int Y_RADIUS = 0x0F;
    private static final int SFX_FLYING = 0xBA;        // sfx_Flying
    private static final int SFX_DEATH = 0x35;         // sfx_Death
    private static final int SFX_GATE = 0x65;          // sfx_BlueSphere
    private static final String BEST_FILE = "best.txt";

    private ZoneArt art;
    private TailsArt tails;
    private final Course course = new Course();
    private final Flight flight = new Flight();
    private boolean flying;
    private long crashedAt = -1;
    private long ticks;
    private double scenery;
    private int score;
    private int best;
    private long seed = 1;

    @Override
    public void enter(SceneContext ctx) {
        art = ZoneArt.load(ctx.art().rom(), Zone.tour().get(0));
        tails = TailsArt.load(ctx.art().rom());
        best = ctx.storage().read(BEST_FILE).map(FlappyScene::number).orElse(0);
        course.reset(seed, 0);
        ctx.audio().playMusic(Zone.tour().get(0).music());
    }

    @Override
    public void update(SceneContext ctx) {
        ticks++;
        if (crashedAt >= 0) {
            if (ticks - crashedAt > 90) restart();
            return;
        }
        int speed = Zone.tour().get(0).speed();
        scenery += speed / 256.0;
        boolean pressed = ctx.buttonPressed(SceneButtons.ACTIONS) || ctx.keyPressed(SceneKeys.SPACE)
                || ctx.mouse().leftPressed();
        if (!flying) {
            if (!pressed) return;
            flying = true;
            flight.start(START_Y);
        }
        course.advance(speed);
        flight.tick(pressed, (ticks & 1) == 1, 0);
        flight.refill(Flight.FULL_TIMER);
        if (Flight.buzzDue(ticks)) ctx.audio().playSfx(SFX_FLYING);

        int x = course.scrollX() + TAILS_X;
        int y = flight.y();
        boolean crash = y + Y_RADIUS >= Course.GROUND_Y;
        for (Course.Gate gate : course.gates()) {
            if (!gate.passed && gate.x + ZoneArt.PILLAR_WIDTH / 2 < x) {
                gate.passed = true;
                score++;
                ctx.audio().playSfx(SFX_GATE);
            }
            if (x + HALF_WIDTH > gate.x && x - HALF_WIDTH < gate.right()
                    && (y - HALF_HEIGHT < gate.top() || y + HALF_HEIGHT > gate.bottom())) {
                crash = true;
            }
        }
        if (crash) {
            crashedAt = ticks;
            ctx.audio().playSfx(SFX_DEATH);
            if (score > best) {
                best = score;
                ctx.storage().write(BEST_FILE, Integer.toString(best));
            }
        }
    }

    private void restart() {
        crashedAt = -1;
        flying = false;
        score = 0;
        course.reset(++seed, 0);
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        int width = ctx.width();
        if (art.backdrop != null) {
            canvas.drawBackdrop(art.backdrop, Zone.tour().get(0).backdropTop(), scenery, ticks);
        } else {
            canvas.clear(0x3070D0);
        }
        for (Course.Gate gate : course.gates()) {
            int x = gate.x - course.scrollX();
            if (art.pillar != null) {
                // The pillar picture's surface line is 12 rows down; a top pillar is the same picture upside down.
                canvas.draw(art.pillar, x, gate.bottom() - 12, SceneDraw.plain());
                canvas.draw(art.pillar, x, gate.top() + 12 - ZoneArt.PILLAR_HEIGHT, SceneDraw.plain().withFlipY(true));
            } else {
                canvas.fill(x, 0, ZoneArt.PILLAR_WIDTH, gate.top(), 0xFF2E9A3A);
                canvas.fill(x, gate.bottom(), ZoneArt.PILLAR_WIDTH, Course.GROUND_Y - gate.bottom(), 0xFF2E9A3A);
            }
        }
        if (art.ground != null) {
            int tile = art.ground.width();
            for (int x = -Math.floorMod((int) scenery, tile); x < width; x += tile) {
                canvas.draw(art.ground, x, Course.GROUND_Y - 12, SceneDraw.plain());
            }
        }
        float y = flying || crashedAt >= 0 ? flight.y() : START_Y + (float) Math.sin(ticks * Math.PI * 2 / 64) * 4;
        int animation = crashedAt >= 0 ? TailsArt.ANIM_HURT : flying ? flight.animation() : TailsArt.ANIM_FLY;
        if (tails != null) tails.draw(canvas, animation, ticks, TAILS_X, y, SceneDraw.plain());
        String text = Integer.toString(score);
        canvas.text(text, (width - canvas.textWidth(text)) / 2, 12, 0xFFFFFFFF);
        String bestText = "BEST " + best;
        canvas.text(bestText, width - 8 - canvas.textWidth(bestText), 12, 0xFFFFFFFF);
        if (!flying) {
            String hint = "PRESS A, B, C, SPACE OR CLICK TO FLAP";
            canvas.text(hint, (width - canvas.textWidth(hint)) / 2, 160, 0xFFFFFFFF);
        }
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
