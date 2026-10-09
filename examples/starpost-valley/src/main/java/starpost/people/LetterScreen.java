package starpost.people;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.List;
import starpost.core.Game;
import starpost.core.Item;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;

/**
 * The morning post: each waiting letter on a sheet of paper, one at a time. An animal's letter
 * is drawn in pictures until the Chirp Translator. Anything enclosed is taken when the letter is
 * put away (and the letter waits for tomorrow when the monitors are full).
 */
final class LetterScreen implements Screen {
    /** A letter ready to show. */
    record View(String from, String text, String[] pictures, String item, int count) {
    }

    /** Letter text wraps at this many characters, up to this many lines (tested for every letter). */
    static final int LINE_CHARS = 29;
    static final int MAX_LINES = 7;

    private final PeopleSystem sys;
    private String id;
    private View view;
    private long opened;

    LetterScreen(PeopleSystem sys) {
        this.sys = sys;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void enter(Shell shell) {
        next(shell);
    }

    private void next(Shell shell) {
        id = sys.people.nextLetter();
        view = id == null ? null : resolve(sys, id, shell.game);
        opened = shell.ticks;
    }

    /** A mailbox entry's sender, words, pictures and enclosure. */
    static View resolve(PeopleSystem sys, String id, Game game) {
        People people = sys.people;
        Letter letter = people.cast.letter(id);
        if (letter != null) {
            return view(sys, game, letter.from, letter.text(), letter.pictures(), letter.item(), letter.count());
        }
        String villager = id.substring(id.indexOf(':') + 1);
        VillagerDef v = people.cast.get(villager);
        if (id.startsWith("thanks:") && v != null) {
            Line line = people.thanksLine(v, game);
            String text = line == null ? "THANK YOU!" : line.text();
            return view(sys, game, v.id, text, line == null ? null : line.pictures(), null, 0);
        }
        if (id.startsWith("birthday:") && v != null) {
            List<String> loves = v.lovedItems();
            String loved = null;
            for (String item : loves) {
                if (game.catalog.hasItem(item)) {
                    loved = item;
                    break;
                }
            }
            String text = "PIP'S POST: TOMORROW IS " + v.name + "'S BIRTHDAY!"
                    + (loved != null ? " " + v.name + " LOVES " + game.item(loved).name() + "." : "");
            String[] pics = loved != null ? new String[] {"who:" + v.id, "gift", "clock", "item:" + loved, "heart"}
                    : new String[] {"who:" + v.id, "gift", "clock", "!"};
            VillagerDef pip = people.cast.get("pip");
            return view(sys, game, pip != null && people.present(pip, game) ? "pip" : "post", text, pics, null, 0);
        }
        return new View("post", "THE LETTER IS TOO SMUDGED TO READ.", null, null, 0);
    }

    private static View view(PeopleSystem sys, Game game, String from, String text, String[] pics, String item, int count) {
        VillagerDef v = sys.people.cast.get(from);
        boolean pictures = v != null && sys.people.speaksInPictures(v, game);
        String[] shown = pictures ? (pics != null ? pics : Pictures.fromText(text, game.catalog, sys.people.cast)) : null;
        return new View(from, People.words(text, game), shown, item, count);
    }

    @Override
    public void update(Shell shell) {
        if (view == null) {
            shell.pop();
            return;
        }
        if (shell.ticks - opened < 12 || !(shell.in.confirm || shell.in.act || shell.in.back)) {
            return;
        }
        Game game = shell.game;
        if (view.item() != null && shell.catalog.hasItem(view.item())) {
            Item item = game.item(view.item());
            if (!game.inventory.fits(item, view.count())) {
                shell.toast("NO ROOM FOR THE " + item.name() + ". IT WILL WAIT.");
                shell.sfx(Sfx.ERROR);
                sys.heldLetter = id;
                shell.pop();
                return;
            }
            game.inventory.add(item, view.count());
            shell.toast("GOT " + item.name() + (view.count() > 1 ? " X" + view.count() : "") + "!");
            shell.sfx(Sfx.PERFECT);
        } else {
            shell.sfx(Sfx.SWITCH);
        }
        sys.people.read(id);
        shell.in.consume();
        next(shell);
        if (view == null) {
            shell.pop();
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        if (view == null) {
            return;
        }
        int w = 320, h = 166;
        int x = (canvas.width() - w) / 2, y = 56;     // below the toast line
        // Paper: cream with a darker edge, a fold line and a turned corner.
        canvas.fill(x - 2, y - 2, w + 4, h + 4, 0xFF492400);
        canvas.fill(x, y, w, h, 0xFFFFF2DB);
        canvas.fill(x, y + h / 2, w, 1, 0xFFE6D2B6);
        canvas.fill(x + w - 14, y, 14, 14, 0xFFE6D2B6);
        canvas.text("FROM: " + sender(shell), x + 12, y + 10, 0xFF6D2400);
        SceneImage face = faceOf(view.from());
        if (face != null) {
            canvas.draw(face, x + w - 22 - face.width(), y + 6, SceneDraw.plain());
        }
        if (view.pictures() != null) {
            int px = x + 16, py = y + 56;
            for (String token : view.pictures()) {
                px += sys.art.drawToken(canvas, token, px, py, 36, shell.catalog, sys.people.cast, shell.game.farmer,
                        SceneDraw.plain(), true) + 10;
            }
        } else {
            int ly = y + 32;
            List<String> rows = Speech.wrap(view.text(), LINE_CHARS);
            for (int i = 0; i < Math.min(MAX_LINES, rows.size()); i++) {
                canvas.text(rows.get(i), x + 12, ly, 0xFF241848);
                ly += 13;
            }
        }
        if (view.item() != null && shell.catalog.hasItem(view.item())) {
            Item item = shell.game.item(view.item());
            int ey = y + h - 40;
            canvas.fill(x + 10, ey - 3, w - 20, 22, 0xFFFFDBB6);
            shell.art.icons.draw(canvas, item, x + 14, ey, SceneDraw.plain());
            canvas.text("ENCLOSED: " + item.name() + (view.count() > 1 ? " X" + view.count() : ""), x + 36, ey + 4,
                    0xFF6D2400);
        }
        int left = sys.people.mailbox().size();
        String footer = left > 1 ? "CONFIRM: NEXT LETTER (" + (left - 1) + " MORE)" : "CONFIRM: PUT IT AWAY";
        canvas.text(footer, x + w - 12 - canvas.textWidth(footer), y + h - 14, 0xFFB6926D);
    }

    private String sender(Shell shell) {
        VillagerDef v = sys.people.cast.get(view.from());
        return v != null ? v.name : "THE FLICKY POST";
    }

    private SceneImage faceOf(String from) {
        VillagerDef v = sys.people.cast.get(from);
        if (v != null) {
            return sys.art.face(v.body());
        }
        return sys.art.art.flicky.frame(0).image();
    }
}
