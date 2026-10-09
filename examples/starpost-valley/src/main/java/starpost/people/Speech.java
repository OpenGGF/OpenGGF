package starpost.people;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import starpost.ui.Controls;
import starpost.ui.Text;

/**
 * One speaker's turn in the dialogue box: the speaker's ROM sprite at 1x in a frame, their name,
 * and paged text typed out a couple of letters a tick, or, for an animal before the Chirp
 * Translator, a speech bubble of pictures shown one by one. Confirm (or the action button)
 * finishes the page, then turns it.
 */
final class Speech {
    static final int BOX_H = 68;
    static final int TEXT_CHARS = 30;
    static final int LINES_PER_PAGE = 3;
    private static final int CHARS_PER_TICK = 2;
    private static final int TICKS_PER_PICTURE = 9;

    final String speaker;
    private final String name;
    private final String body;
    private final List<String> pages = new ArrayList<>();
    private final String[] pictures;
    private int page;
    private int shown;

    /**
     * @param speaker  a villager id, {@code farmer} or {@code narrator}
     * @param pictures the picture version, used instead of the text when non-null
     */
    Speech(PeopleSystem sys, String speaker, String text, String[] pictures) {
        this.speaker = speaker;
        VillagerDef v = sys.people.cast.get(speaker);
        if (v != null) {
            name = v.name;
            body = v.body();
        } else if (speaker.equals("farmer")) {
            name = People.words("{FARMER}", sys.shell.game);
            body = "hero:" + sys.shell.game.farmer;
        } else {
            name = "";
            body = null;
        }
        this.pictures = pictures;
        if (pictures == null) {
            paginate(People.words(text, sys.shell.game), body == null ? TEXT_CHARS + 7 : TEXT_CHARS);
        }
    }

    /** A villager's line, in pictures when they speak in pictures. */
    static Speech of(PeopleSystem sys, String speaker, Line line) {
        VillagerDef v = sys.people.cast.get(speaker);
        boolean pictures = v != null && sys.people.speaksInPictures(v, sys.shell.game);
        return new Speech(sys, speaker, line.text(), pictures ? Pictures.of(line, sys.shell.catalog, sys.people.cast) : null);
    }

    private void paginate(String text, int width) {
        for (String chunk : text.split("\\|")) {
            List<String> lines = wrap(chunk.trim(), width);
            for (int i = 0; i < lines.size(); i += LINES_PER_PAGE) {
                pages.add(String.join("\n", lines.subList(i, Math.min(lines.size(), i + LINES_PER_PAGE))));
            }
        }
        if (pages.isEmpty()) {
            pages.add("");
        }
    }

    /** Word wrap by the menu font's fixed advance (characters per line). */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private int length() {
        return pictures != null ? pictures.length * TICKS_PER_PICTURE : pages.get(page).length();
    }

    /** One tick; true when the speaker has finished. */
    boolean update(Controls in) {
        if (shown < length()) {
            shown = Math.min(length(), shown + (pictures != null ? 1 : CHARS_PER_TICK));
        }
        if (in.confirm || in.act) {
            if (shown < length()) {
                shown = length();
            } else if (pictures == null && page + 1 < pages.size()) {
                page++;
                shown = 0;
            } else {
                return true;
            }
        }
        return false;
    }

    int pageCount() {
        return pictures != null ? 1 : pages.size();
    }

    // ------------------------------------------------------------------ drawing

    void draw(PeopleSystem sys, SceneCanvas canvas, long ticks) {
        int w = canvas.width(), h = canvas.height();
        int x = 8, y = h - BOX_H - 6, bw = w - 16;
        canvas.fill(x, y, bw, BOX_H, 0xFF101848);     // opaque: the hotbar must not show through
        Text.panel(canvas, x, y, bw, BOX_H);
        int textX = x + 12;
        if (body != null) {
            drawPortrait(sys, canvas, x + 6, y + 5, ticks);
            textX = x + 72;
        }
        if (!name.isEmpty()) {
            int nw = canvas.textWidth(name) + 12;
            Text.panel(canvas, x + 6, y - 13, nw, 15);
            Text.shadow(canvas, name, x + 12, y - 10, Text.YELLOW);
        }
        if (pictures != null) {
            drawPictures(sys, canvas, textX, y + 12, x + bw - 10 - textX);
        } else {
            String text = pages.get(page).substring(0, Math.min(shown, pages.get(page).length()));
            int ly = y + 12;
            for (String row : text.split("\n")) {
                Text.shadow(canvas, row, textX, ly, Text.WHITE);
                ly += 15;
            }
        }
        if (shown >= length() && ticks / 16 % 2 == 0) {
            int ax = x + bw - 16, ay = y + BOX_H - 12;
            canvas.fill(ax, ay, 7, 2, Text.YELLOW);
            canvas.fill(ax + 1, ay + 2, 5, 1, Text.YELLOW);
            canvas.fill(ax + 2, ay + 3, 3, 1, Text.YELLOW);
            canvas.fill(ax + 3, ay + 4, 1, 1, Text.YELLOW);
        }
    }

    /** The speaker at 1x in a frame of Green Hill sky, feet on the frame's floor. */
    private void drawPortrait(PeopleSystem sys, SceneCanvas canvas, int x, int y, long ticks) {
        int size = 58;
        canvas.fill(x, y, size, size, 0xFF000000);
        canvas.fill(x + 1, y + 1, size - 2, size - 2, 0xFF2449B6);
        canvas.fill(x + 1, y + size - 12, size - 2, 11, 0xFF246D00);
        canvas.fill(x + 1, y + size - 12, size - 2, 2, 0xFF49B600);
        SceneSprite sprite = sys.art.portrait(body, ticks);
        if (sprite == null) {
            return;
        }
        if (sprite.height() > size - 4) {
            // Taller than the frame (the totem pole): its top, at 1x.
            SceneImage image = sprite.image();
            int rows = size - 4, w = Math.min(image.width(), size - 2);
            canvas.drawRegion(image, (image.width() - w) / 2, 0, w, rows, x + (size - w) / 2f, y + 2, w, rows,
                    SceneDraw.plain().withFlipX(PeopleArt.facesLeft(body)));
            return;
        }
        float feet = y + size - 6 - Bodies.lift(body, ticks) * 0.5f;
        boolean flip = PeopleArt.facesLeft(body);   // portraits face the text, to the right
        SceneDraw style = SceneDraw.plain().withFlipX(flip);
        float originY = feet - (sprite.height() - sprite.originY());
        if (body.equals("hero:tails")) {
            sys.art.tails(canvas, x + size / 2f, originY, ticks, style);
        }
        canvas.draw(sprite, x + size / 2f, originY, style);
    }

    /** A white speech bubble; pictures appear one at a time. */
    private void drawPictures(PeopleSystem sys, SceneCanvas canvas, int x, int y, int maxW) {
        int count = Math.min(pictures.length, shown / TICKS_PER_PICTURE + 1);
        int gap = 6, total = 0, tall = 16;
        for (String token : pictures) {
            total += sys.art.tokenWidth(canvas, token, sys.shell.catalog, sys.people.cast, sys.shell.game.farmer, true)
                    + gap;
            String farmer = sys.shell.game.farmer;
            tall = Math.max(tall, sys.art.tokenHeight(token, sys.people.cast, farmer)
                    * sys.art.scale(token, sys.people.cast, farmer));
        }
        int bw = Math.min(maxW, total + 14), bh = Math.min(46, tall + 8);
        canvas.fill(x + 1, y, bw - 2, bh, 0xFF000000);
        canvas.fill(x, y + 1, bw, bh - 2, 0xFF000000);
        canvas.fill(x + 1, y + 1, bw - 2, bh - 2, 0xFFFFFFFF);
        canvas.fill(x - 4, y + bh / 2 - 2, 5, 5, 0xFF000000);
        canvas.fill(x - 3, y + bh / 2 - 1, 5, 3, 0xFFFFFFFF);
        int px = x + 8;
        for (int i = 0; i < count; i++) {
            px += sys.art.drawToken(canvas, pictures[i], px, y + 4, bh - 8, sys.shell.catalog, sys.people.cast,
                    sys.shell.game.farmer, SceneDraw.plain(), true) + gap;
        }
    }
}
