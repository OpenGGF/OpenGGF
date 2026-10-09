package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * The fair's slot booth (an overlay): three reels on Sonic 2's strips, paid by Casino Night's own
 * reward rules ({@link Fair#slotReward}). Jump pays for a spin and sets the reels going; each
 * further press stops the next reel on the face in its window, so a sharp eye can aim. The faces
 * are Casino Night's when Sonic 2 is supplied, else Sonic 3's slot bonus faces.
 */
final class SlotsScreen implements Screen {
    /** Faces a tick while spinning: slow enough to aim for. */
    private static final float SPIN = 1 / 9f;

    private final FairScreen fair;
    private final float[] pos = new float[3];
    private final boolean[] spinning = new boolean[3];
    private final boolean[] stopping = new boolean[3];
    private int next = -1;
    private String result = "";
    private int resultColour = Text.WHITE;
    private long resultAt = -1000;

    SlotsScreen(FairScreen fair) {
        this.fair = fair;
        for (int r = 0; r < 3; r++) {
            pos[r] = r * 3;
        }
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void update(Shell shell) {
        var in = shell.in;
        Game game = shell.game;
        for (int r = 0; r < 3; r++) {
            if (spinning[r]) {
                pos[r] = (pos[r] + SPIN) % 8;
                if (stopping[r] && Math.abs(pos[r] - Math.round(pos[r])) < SPIN / 2 + 0.001f) {
                    pos[r] = Math.round(pos[r]) % 8;
                    spinning[r] = false;
                    shell.sfx(Sfx.SWITCH);
                    if (r == 2) {
                        pay(shell);
                    }
                }
            }
        }
        if (in.back && next < 0) {
            shell.pop();
            return;
        }
        if (!in.confirm) {
            return;
        }
        if (next < 0) {
            if (game.rings < Fair.SPIN_COST) {
                shell.sfx(Sfx.ERROR);
                result = "NOT ENOUGH RINGS";
                resultAt = shell.ticks;
                return;
            }
            game.rings -= Fair.SPIN_COST;
            for (int r = 0; r < 3; r++) {
                spinning[r] = true;
                stopping[r] = false;
            }
            next = 0;
            result = "";
            shell.sfx(Sfx.REGISTER);
        } else if (next < 3 && !stopping[next]) {
            stopping[next] = true;
            next++;
        }
    }

    private int face(int reel) {
        return Fair.strip(reel)[Math.round(pos[reel]) % 8];
    }

    private void pay(Shell shell) {
        Game game = shell.game;
        int a = face(0), b = face(1), c = face(2);
        int pay = Fair.payout(a, b, c);
        next = -1;
        resultAt = shell.ticks;
        if (pay < 0) {
            int taken = Math.min(game.rings, -pay);
            game.rings -= taken;
            result = "ROBOTNIK! HIS SPIKE BALLS TAKE " + taken + " RINGS";
            resultColour = Text.RED;
            shell.sfx(Sfx.RING_LOSS);
            fair.caption("robotnik", "HO HO HO! THE HOUSE ALWAYS WINS. I AM THE HOUSE.");
            return;
        }
        game.rings += pay;
        resultColour = pay >= 100 ? Text.YELLOW : Text.WHITE;
        result = pay == 0 ? "NOTHING. SPIN AGAIN?" : "+" + pay + " RINGS!";
        shell.sfx(pay > 0 ? Sfx.RING : Sfx.ERROR);
        if (a == b && b == c && fair.festivals.takePrize("record." + FestivalBook.FAIR)) {
            List<String> notices = new ArrayList<>();
            Prizes.give(game, fair.festivals, "record_slots", 1, notices);
            shell.toast(notices.isEmpty() ? "THREE OF A KIND!" : notices.get(0));
            shell.sfx(Sfx.PERFECT);
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        FestivalArt art = fair.sys.art;
        int w = 220, h = 150, x = (canvas.width() - w) / 2, y = 36;
        // The cabinet: red body, gold trim, three reel windows.
        canvas.fill(x, y, w, h, 0xFF240000);
        canvas.fill(x + 2, y + 2, w - 4, h - 4, 0xFF920000);
        canvas.fill(x + 2, y + 2, w - 4, 3, 0xFFFFDB00);
        canvas.fill(x + 2, y + h - 5, w - 4, 3, 0xFFDB9200);
        String title = art.casinoNight() ? "CASINO NIGHT SLOTS" : "SLOT BONUS";
        Text.centred(canvas, title, y + 10, Text.YELLOW);
        int rx0 = x + 34;
        for (int r = 0; r < 3; r++) {
            int rx = rx0 + r * 54, ry = y + 34;
            canvas.fill(rx - 3, ry - 3, 38, 46, 0xFFFFDB00);
            canvas.fill(rx - 1, ry - 1, 34, 42, 0xFF000000);
            canvas.clip(rx - 1, ry - 1, 34, 42);
            float p = pos[r];
            int base = (int) Math.floor(p);
            float frac = p - base;
            int[] strip = Fair.strip(r);
            for (int k = -1; k <= 1; k++) {
                SceneImage face = art.slotFace(strip[Math.floorMod(base + k, 8)]);
                float fy = ry + 4 - (k - frac) * -36 - 0;
                fy = ry + 4 + (k - frac) * 36;
                if (face != null) {
                    canvas.draw(face, rx, fy, SceneDraw.plain());
                }
            }
            canvas.unclip();
        }
        Text.centred(canvas, Fair.SPIN_COST + " RINGS A SPIN.  RINGS: " + shell.game.rings, y + 92, Text.WHITE);
        String hint = next < 0 ? "JUMP: SPIN    BACK: LEAVE" : "JUMP: STOP REEL " + (next + 1);
        Text.centred(canvas, hint, y + 108, Text.GREY);
        if (!result.isEmpty() && shell.ticks - resultAt < 240) {
            Text.centred(canvas, result, y + 126, resultColour);
        }
    }
}
