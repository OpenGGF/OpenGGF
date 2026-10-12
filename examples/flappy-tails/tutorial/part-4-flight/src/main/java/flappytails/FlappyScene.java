package flappytails;

import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneRomArt;
import java.util.List;

/**
 * Tutorial part 4: flight. Pressing A, B, C, Space or the mouse flaps, through {@link Flight},
 * a port of the ROM's own flight routine. Touching the ground is a crash; a second later Tails
 * is back in the air. Until the first press he waits, bobbing, as Flappy Bird does.
 */
public final class FlappyScene implements ModScene {
    /** The screen row the ground's surface sits on. */
    private static final int GROUND_Y = 200;
    /** The backdrop row at the top of the screen: Angel Island's sky, with the sea's horizon low down. */
    private static final int BACKDROP_TOP = 220;
    /** How far the world scrolls each frame, in pixels. */
    private static final double SPEED = 2;
    private static final int MUSIC_AIZ1 = 0x01;

    /** Tails' screen column and resting row. */
    private static final int TAILS_X = 112;
    private static final int TAILS_Y = 100;
    /** Tails' standing y_radius ($F): his feet are this far below his centre. */
    private static final int Y_RADIUS = 0x0F;
    private static final int SFX_FLYING = 0xBA;        // sfx_Flying
    private static final int SFX_DEATH = 0x35;         // sfx_Death

    private SceneBackdrop backdrop;
    private TailsArt tails;
    private final Flight flight = new Flight();
    private boolean flying;
    private long crashedAt = -1;
    private SceneImage ground;
    private double scroll;
    private long ticks;

    @Override
    public void enter(SceneContext ctx) {
        SceneRomArt rom = ctx.art().rom();
        tails = TailsArt.load(rom);
        if (rom != null && rom.hasZonePictures(0, 0)) {
            backdrop = rom.zoneBackdrop(0, 0);
            // Runs of the act's floor at least 400 pixels wide, with 96 clear above, rising at most 8.
            List<SceneLevelStage> stages = rom.levelStages(0, 0, 400, 96, 8);
            if (!stages.isEmpty()) {
                SceneLevelStage stage = stages.get(0);
                int x = stage.x() + 150;                       // past a waterfall at the stage's left end
                int floor = stage.floorAt(x + 125);
                ground = rom.levelForeground(0, 0, x, floor - 12, 250, 48);
            }
        }
        ctx.audio().playMusic(MUSIC_AIZ1);
    }

    @Override
    public void update(SceneContext ctx) {
        ticks++;
        if (crashedAt >= 0) {
            if (ticks - crashedAt > 60) {               // a second to take it in, then go again
                crashedAt = -1;
                flying = false;
            }
            return;
        }
        scroll += SPEED;
        boolean pressed = ctx.buttonPressed(SceneButtons.ACTIONS) || ctx.keyPressed(SceneKeys.SPACE)
                || ctx.mouse().leftPressed();
        if (!flying) {
            if (!pressed) return;
            flying = true;
            flight.start(Math.round(bobY()));
        }
        flight.tick(pressed, (ticks & 1) == 1, 0);
        flight.refill(Flight.FULL_TIMER);               // flight never tires, as in the native sample
        if (Flight.buzzDue(ticks)) ctx.audio().playSfx(SFX_FLYING);
        if (flight.y() + Y_RADIUS >= GROUND_Y) {
            crashedAt = ticks;
            ctx.audio().playSfx(SFX_DEATH);
        }
    }

    private float bobY() {
        return TAILS_Y + (float) Math.sin(ticks * Math.PI * 2 / 64) * 4;
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        int width = ctx.width();
        if (backdrop != null) {
            canvas.drawBackdrop(backdrop, BACKDROP_TOP, scroll, ticks);
        } else {
            canvas.clear(0x3070D0);
        }
        drawGround(canvas, width);
        float y = flying ? flight.y() : bobY();
        int animation = crashedAt >= 0 ? TailsArt.ANIM_HURT : flying ? flight.animation() : TailsArt.ANIM_FLY;
        if (tails != null) {
            tails.draw(canvas, animation, ticks, TAILS_X, y, SceneDraw.plain());
        } else {
            canvas.fill(TAILS_X - 10, Math.round(y) - 12, 20, 24, 0xFFFFA020);
        }
        String hint = flying ? "" : "PRESS A, B, C, SPACE OR CLICK TO FLAP";
        canvas.text(hint, (width - canvas.textWidth(hint)) / 2, 160, 0xFFFFFFFF);
    }

    /** The ground strip, repeated across the screen, alternate copies mirrored so the joins match. */
    private void drawGround(SceneCanvas canvas, int width) {
        if (ground == null) {
            canvas.fill(0, GROUND_Y, width, canvas.height() - GROUND_Y, 0xFF3C8C24);
            return;
        }
        int tile = ground.width();
        int offset = (int) Math.floorMod((long) scroll, 2L * tile);
        for (int i = 0, x = -offset; x < width; i++, x += tile) {
            canvas.draw(ground, x, GROUND_Y - 12, SceneDraw.plain().withFlipX(i % 2 == 1));
        }
    }
}
