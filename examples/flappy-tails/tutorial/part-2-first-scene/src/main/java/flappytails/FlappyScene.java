package flappytails;

import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelStage;
import com.openggf.mods.scene.SceneRomArt;
import java.util.List;

/**
 * Tutorial part 2: a scene of your own. Angel Island's background scrolls with its stock
 * parallax, and a strip of the act's real ground, cut from the level, scrolls past at the
 * bottom. Nothing here is shipped with the mod: both pictures come from the player's ROM.
 */
public final class FlappyScene implements ModScene {
    /** The screen row the ground's surface sits on. */
    private static final int GROUND_Y = 200;
    /** The backdrop row at the top of the screen: Angel Island's sky, with the sea's horizon low down. */
    private static final int BACKDROP_TOP = 220;
    /** How far the world scrolls each frame, in pixels. */
    private static final double SPEED = 2;
    private static final int MUSIC_AIZ1 = 0x01;

    private SceneBackdrop backdrop;
    private SceneImage ground;
    private double scroll;
    private long ticks;

    @Override
    public void enter(SceneContext ctx) {
        SceneRomArt rom = ctx.art().rom();
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
        scroll += SPEED;
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
        String title = "FLAPPY TAILS";
        canvas.text(title, (width - canvas.textWidth(title)) / 2, 40, 0xFFFFFFFF);
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
