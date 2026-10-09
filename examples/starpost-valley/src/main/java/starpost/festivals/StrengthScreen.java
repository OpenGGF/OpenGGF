package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import starpost.art.Anim;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;
import starpost.valley.Runner;

/**
 * The fair's spring test (an overlay): a tall striped column with the bell at the top, a spring
 * at its foot and a power meter beside it. Jump stops the meter; the spring launches the farmer,
 * curled into a ball, up the column under Sonic's own gravity; ring the bell for the big prize.
 */
final class StrengthScreen implements Screen {
    private static final int COST = 10;
    private static final int COLUMN = 150;

    private final FairScreen fair;
    private long meterAt;
    private boolean flying;
    private float height;
    private float speed;
    private int peak;
    private boolean rang;
    private String result = "";
    private long resultAt = -1000;
    private long ticks;

    StrengthScreen(FairScreen fair) {
        this.fair = fair;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void enter(Shell shell) {
        meterAt = shell.ticks;
    }

    @Override
    public void update(Shell shell) {
        ticks++;
        var game = shell.game;
        if (flying) {
            height += speed;
            speed -= Runner.GRAVITY;
            peak = Math.max(peak, Math.round(height));
            if (!rang && height >= Fair.BELL) {
                rang = true;
                height = Fair.BELL;
                speed = -1;
                shell.sfx(Sfx.RING);
            }
            if (height <= 0) {
                height = 0;
                flying = false;
                int rings = Fair.strengthRings(peak);
                game.rings += rings;
                result = rang ? "DING! YOU RANG THE BELL! +" + rings + " RINGS" : rings > 0 ? peak + " FEET! +" + rings
                        + " RINGS" : peak + " FEET. PUT YOUR BACK INTO IT!";
                resultAt = shell.ticks;
                if (rang && fair.festivals.takePrize("bell." + FestivalBook.FAIR) && game.catalog.hasItem("chili_dog")) {
                    game.inventory.add(game.item("chili_dog"), 1);
                    result += " + A CHILI DOG";
                }
                meterAt = shell.ticks;
            }
            return;
        }
        if (shell.in.back) {
            shell.pop();
            return;
        }
        if (shell.in.confirm) {
            if (game.rings < COST) {
                shell.sfx(Sfx.ERROR);
                return;
            }
            game.rings -= COST;
            int power = Fair.meter((int) (shell.ticks - meterAt));
            speed = Fair.launch(power);
            height = 0;
            peak = 0;
            rang = false;
            flying = true;
            shell.sfx(Sfx.SPRING);
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = 200, h = 196, x = (canvas.width() - w) / 2, y = 14;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, "SPRING TEST", x + 10, y + 8, Text.YELLOW);
        int colX = x + 70, base = y + h - 30, top = base - COLUMN;
        // The column: Green Hill's brown and orange in stripes, marks every quarter.
        for (int yy = top; yy < base; yy += 8) {
            canvas.fill(colX, yy, 14, 8, (yy - top) / 8 % 2 == 0 ? 0xFFB66D24 : 0xFF6D2400);
        }
        for (int q = 1; q < 4; q++) {
            int my = base - COLUMN * q / 4;
            canvas.fill(colX - 6, my, 6, 1, 0xFFFFFFFF);
        }
        // The bell: a ring at the top; the spring at the foot.
        SceneSpriteSet ring = shell.art.ring;
        boolean ringing = flying && rang || shell.ticks - resultAt < 40 && result.startsWith("DING");
        canvas.draw(ring.frame(ringing ? 4 + (int) (shell.ticks / 4 % 4) : (int) (shell.ticks / 8 % 4)), colX - 1,
                top - 18, SceneDraw.plain());
        FestivalSystem.standSprite(canvas, shell.art.spring, flying && height < 30 ? 1 : 0, colX + 7, base + 8,
                SceneDraw.plain());
        // The farmer, curled up, rising with the launch.
        SceneSpriteSet set = shell.art.farmer(shell.game.farmer);
        int[] roll = set.animationFrames(Anim.ROLL);
        SceneSprite ball = set.frame(roll == null || roll.length == 0 ? 0 : roll[(int) (ticks / 3 % roll.length)]);
        float by = base - 6 - height * COLUMN / Fair.BELL;
        canvas.draw(ball, colX + 7, by - 15, SceneDraw.plain());
        // The meter.
        int power = flying ? 0 : Fair.meter((int) (shell.ticks - meterAt));
        int mx = x + 140, mh = 120, my = base - mh;
        canvas.fill(mx, my, 12, mh, 0xFF000000);
        int fill = mh * power / 100;
        canvas.fill(mx + 1, my + mh - fill, 10, fill, power > 90 ? 0xFFFF4924 : power > 60 ? 0xFFFFDB00 : 0xFF24B6FF);
        Text.shadow(canvas, flying ? "" : "JUMP!", mx - 6, my - 12, Text.WHITE);
        Text.shadow(canvas, COST + " RINGS A GO", x + 10, y + h - 18, Text.GREY);
        if (!result.isEmpty() && shell.ticks - resultAt < 300) {
            Text.note(canvas, result, x + 10, y + 22, w - 20, result.startsWith("DING") ? Text.YELLOW : Text.WHITE);
        }
    }
}
