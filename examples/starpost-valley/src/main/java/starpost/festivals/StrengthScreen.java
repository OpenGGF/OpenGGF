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
import java.util.List;

/**
 * The fair's spring test (an overlay): a tall striped column with the bell at the top, a spring
 * at its foot and a power meter beside it. Jump stops the meter; the spring launches the farmer,
 * curled into a ball, up the column under Sonic's own gravity; ring the bell for the big prize.
 */
final class StrengthScreen implements Screen {
    private static final int COST = 10;

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

    /**
     * The booth: the striped column with the bell (a ring) at its head and the spring at its
     * foot, the power meter beside it, and the last go's result to the right.
     */
    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = 260, h = 168, x = (canvas.width() - w) / 2, y = 30;
        FestivalScreen.solidPanel(canvas, x, y, w, h);
        Text.shadow(canvas, "SPRING TEST", x + 10, y + 8, Text.YELLOW);
        int colX = x + 26, base = y + h - 22, top = y + 44, column = base - top;
        // The column: Green Hill's brown and orange in stripes, marks every quarter.
        for (int yy = top; yy < base; yy += 8) {
            canvas.fill(colX, yy, 14, Math.min(8, base - yy), (yy - top) / 8 % 2 == 0 ? 0xFFB66D24 : 0xFF6D2400);
        }
        for (int q = 1; q < 4; q++) {
            canvas.fill(colX - 6, base - column * q / 4, 6, 1, 0xFFFFFFFF);
        }
        // The bell: a ring at the top; the spring at the foot.
        SceneSpriteSet ring = shell.art.ring;
        boolean ringing = flying && rang || shell.ticks - resultAt < 40 && result.startsWith("DING");
        canvas.draw(ring.frame(ringing ? 4 + (int) (shell.ticks / 4 % 4) : (int) (shell.ticks / 8 % 4)), colX + 7,
                top - 10, SceneDraw.plain());
        FestivalSystem.standSprite(canvas, shell.art.spring, flying && height < 30 ? 1 : 0, colX + 7, base + 8,
                SceneDraw.plain());
        // The farmer, curled up, rising with the launch.
        SceneSpriteSet set = shell.art.farmer(shell.game.farmer);
        int[] roll = set.animationFrames(Anim.ROLL);
        SceneSprite ball = set.frame(roll == null || roll.length == 0 ? 0 : roll[(int) (ticks / 3 % roll.length)]);
        float by = base - 6 - height * column / Fair.BELL;
        canvas.draw(ball, colX + 7, by - 15, SceneDraw.plain());
        // The meter.
        int power = flying ? 0 : Fair.meter((int) (shell.ticks - meterAt));
        int mx = x + 58, mh = column, my = top;
        canvas.fill(mx, my, 12, mh, 0xFF000000);
        int fill = (mh - 2) * power / 100;
        canvas.fill(mx + 1, my + mh - 1 - fill, 10, fill, power > 90 ? 0xFFFF4924 : power > 60 ? 0xFFFFDB00 : 0xFF24B6FF);
        // The words: what to do, and how the last go went.
        int tx = x + 90, tw = w - 100;
        Text.shadow(canvas, flying ? "UP SHE GOES!" : "JUMP: LAUNCH", tx, top, flying ? Text.YELLOW : Text.WHITE);
        Text.shadow(canvas, "BACK: LEAVE", tx, top + 14, Text.GREY);
        if (!result.isEmpty() && shell.ticks - resultAt < 300) {
            List<String> rows = Text.wrap(canvas, result, tw);
            for (int i = 0; i < Math.min(4, rows.size()); i++) {
                Text.shadow(canvas, rows.get(i), tx, top + 40 + i * 12, result.startsWith("DING") ? Text.YELLOW : Text.WHITE);
            }
        }
        Text.shadow(canvas, COST + " RINGS A GO", tx, base - 8, Text.GREY);
    }
}
