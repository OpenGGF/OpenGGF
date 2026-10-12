package starpost.people;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.List;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * The social page, reached from the monitor slots (UP on the top row, or E): everyone met, with
 * their hearts, birthday, this week's gifts, whether you have talked today, and Partners. The
 * selected neighbour's line and discovered loved gifts show underneath.
 */
public final class SocialPage implements Screen {
    private static final int ROWS = 6;
    private static final int ROW_H = 20;
    private final Runnable back;
    private int cursor;
    private int top;

    /** {@code back} reopens the screen this page was reached from (the inventory). */
    public SocialPage(Runnable back) {
        this.back = back;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    private People people(Shell shell) {
        return shell.game.section(People.class);
    }

    @Override
    public void update(Shell shell) {
        People people = people(shell);
        if (shell.in.back || shell.in.menu && !shell.in.confirm || shell.in.prevTool) {
            boolean closeAll = shell.in.menu;
            shell.pop();
            if (back != null && !closeAll) {
                back.run();
            }
            return;
        }
        if (people == null) {
            return;
        }
        int n = people.met().size();
        if (n == 0) {
            return;
        }
        if (shell.in.downPressed) {
            cursor = (cursor + 1) % n;
            shell.sfx(Sfx.SWITCH);
        } else if (shell.in.upPressed) {
            if (cursor == 0 && back != null) {
                shell.pop();
                back.run();
                return;
            }
            cursor = (cursor + n - 1) % n;
            shell.sfx(Sfx.SWITCH);
        }
        top = Math.max(0, Math.min(top, cursor));
        if (cursor >= top + ROWS) {
            top = cursor - ROWS + 1;
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        People people = people(shell);
        Game game = shell.game;
        int w = 340, h = 34 + ROWS * ROW_H + 46, x = (canvas.width() - w) / 2, y = 8;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, "NEIGHBOURS", x + 10, y + 8, Text.YELLOW);
        boolean translator = people != null && people.translator(game);
        Text.right(canvas, translator ? "TRANSLATOR ON" : "TRANSLATOR OFF", x + w - 10, y + 8,
                translator ? Text.GREEN : Text.GREY);
        if (people == null || people.met().isEmpty()) {
            Text.centred(canvas, "SAY HELLO TO SOMEONE IN TOWN!", y + 70, Text.GREY);
            return;
        }
        List<VillagerDef> met = people.met();
        for (int i = top; i < Math.min(met.size(), top + ROWS); i++) {
            VillagerDef v = met.get(i);
            Bond bond = people.bond(v.id);
            int ry = y + 26 + (i - top) * ROW_H;
            if (i == cursor) {
                canvas.fill(x + 6, ry - 2, w - 12, ROW_H - 1, 0x60B66D24);
            }
            SceneImage face = shell.art.signpost == null ? null : headOf(shell, v);
            if (face != null) {
                int fh = Math.min(face.height(), ROW_H - 2);
                canvas.drawRegion(face, 0, 0, Math.min(face.width(), 32), fh, x + 10 + (32 - Math.min(32, face.width())) / 2f,
                        ry - 1 + (ROW_H - 2 - fh), Math.min(face.width(), 32), fh, SceneDraw.plain());
            }
            Text.shadow(canvas, v.name, x + 46, ry + 1, i == cursor ? Text.YELLOW : Text.WHITE);
            if (bond.partner) {
                Text.shadow(canvas, "PARTNER", x + 46, ry + 10, Text.GREEN);
            } else {
                String bd = Calendar.seasonName(v.birthdaySeason()).substring(0, 3) + " " + v.birthdayDay();
                canvas.text(v.isPet() ? "PET" : bd, x + 46, ry + 10, Text.GREY);
            }
            drawHearts(canvas, x + 172, ry + 2, bond.hearts(), v.maxHearts(), bond.points % People.POINTS_PER_HEART);
            // Gifts this week (two boxes) and today's talk (a speech dot).
            for (int g = 0; g < People.GIFTS_PER_WEEK; g++) {
                int gx = x + w - 46 + g * 11;
                canvas.fill(gx, ry + 3, 9, 9, 0xFF000000);
                canvas.fill(gx + 1, ry + 4, 7, 7, g < bond.giftsThisWeek ? 0xFFFF2424 : 0xFF203060);
                if (g < bond.giftsThisWeek) {
                    canvas.fill(gx + 4, ry + 4, 1, 7, 0xFFFFDB00);
                }
            }
            canvas.fill(x + w - 20, ry + 5, 6, 5, bond.talkedToday ? 0xFFFFFFFF : 0xFF203060);
        }
        if (met.size() > ROWS) {
            Text.shadow(canvas, (cursor + 1) + "/" + met.size(), x + 116, y + 8, Text.GREY);
        }
        VillagerDef sel = met.get(Math.min(cursor, met.size() - 1));
        Bond bond = people.bond(sel.id);
        int dy = y + h - 46;
        canvas.fill(x + 6, dy - 4, w - 12, 1, Text.EDGE);
        java.util.List<String> about = Speech.wrap(sel.about(), 32);
        for (int i = 0; i < Math.min(2, about.size()); i++) {
            Text.shadow(canvas, about.get(i), x + 10, dy + i * 11, Text.GREY);
        }
        dy += 12;
        StringBuilder loves = new StringBuilder();
        for (String id : bond.knownLoves) {
            if (shell.catalog.hasItem(id)) {
                loves.append(loves.isEmpty() ? "" : ", ").append(shell.catalog.item(id).name());
            }
        }
        String lovesText = loves.isEmpty() ? "LOVES: ???" : "LOVES: " + loves;
        if (lovesText.length() > 32) {
            lovesText = lovesText.substring(0, 31) + ".";
        }
        Text.shadow(canvas, lovesText, x + 10, dy + 12, Text.BLUE);
        if (sel.canPartner() && !bond.partner) {
            Text.right(canvas, "CAN BECOME A PARTNER", x + w - 10, dy + 12, Text.GREEN);
        }
        Text.centred(canvas, "UP/Q: MONITORS   BACK: CLOSE", y + h + 4, Text.GREY);
    }

    private static SceneImage headOf(Shell shell, VillagerDef v) {
        // A fresh PeopleArt is not built here (draw must stay cheap); the heads come from the
        // play screen's system when there is one.
        if (shell.screen() instanceof starpost.scene.PlayScreen play) {
            PeopleSystem sys = PeopleSystem.of(play);
            if (sys != null) {
                return sys.art.face(v.body());
            }
        }
        return null;
    }

    /** Ten heart slots: red for each heart, a partial fill for the next, grey beyond the cap. */
    static void drawHearts(SceneCanvas canvas, int x, int y, int hearts, int cap, int partial) {
        for (int i = 0; i < People.MAX_HEARTS; i++) {
            int hx = x + i * 11;
            int colour = i < hearts ? 0xFFFF2424 : i < cap ? 0xFF203060 : 0xFF404040;
            heart(canvas, hx, y, colour);
            if (i == hearts && i < cap && partial > 0) {
                int fill = Math.max(1, partial * 9 / People.POINTS_PER_HEART);
                canvas.fill(hx, y + 8, fill, 2, 0xFFFF9292);
            }
        }
    }

    private static void heart(SceneCanvas canvas, int x, int y, int colour) {
        canvas.fill(x + 1, y, 3, 1, 0xFF000000);
        canvas.fill(x + 5, y, 3, 1, 0xFF000000);
        canvas.fill(x, y + 1, 9, 4, 0xFF000000);
        canvas.fill(x + 1, y + 5, 7, 1, 0xFF000000);
        canvas.fill(x + 2, y + 6, 5, 1, 0xFF000000);
        canvas.fill(x + 3, y + 7, 3, 1, 0xFF000000);
        canvas.fill(x + 1, y + 1, 7, 4, colour);
        canvas.fill(x + 2, y + 5, 5, 1, colour);
        canvas.fill(x + 3, y + 6, 3, 1, colour);
        canvas.fill(x + 2, y + 1, 1, 1, 0x80FFFFFF);
    }
}
