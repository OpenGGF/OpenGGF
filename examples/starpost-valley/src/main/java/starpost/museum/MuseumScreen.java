package starpost.museum;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.ui.CompactFont;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.core.Item;
import starpost.festivals.FestivalContent;
import starpost.ruins.RuinsContent;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.scene.Systems;
import starpost.ui.Text;

/**
 * Inside Tails's Workshop Museum: a page per collection (Minerals, the Scrap Collection, Relics)
 * with everything donated in colour and everything missing as a silhouette, the selected piece's
 * name and where to look, the collection's milestones and their rewards, and the Sound Test shelf
 * (the Records found so far), which links to the Lamppost Inn's jukebox rather than playing them
 * itself. Q/E or the tab row switch pages; confirm donates everything carried that a page lacks.
 */
final class MuseumScreen implements Screen {
    private static final int SOUND = 3;
    private static final int SILHOUETTE = 0xFF34407C;

    private final MuseumSystem sys;
    private final List<Exhibits.Exhibit> exhibits;
    private final List<String> records;
    /** 0-2 the collections, 3 the Sound Test shelf. */
    int tab;
    /** The selected cell, or -1 on the tab row. */
    private int cursor;
    private String note;
    private long noteAt = -1000;

    MuseumScreen(MuseumSystem sys) {
        this.sys = sys;
        this.exhibits = sys.exhibits;
        this.records = records(sys.shell);
    }

    @Override
    public boolean overlay() {
        return true;
    }

    private int tabs() {
        return exhibits.size() + 1;
    }

    private boolean soundTab() {
        return tab >= exhibits.size();
    }

    private List<String> cells(Shell shell) {
        return soundTab() ? records : exhibits.get(tab).items();
    }

    /** Columns of the grid on a page. */
    private int columns() {
        if (soundTab()) {
            return 3;
        }
        return switch (exhibits.get(tab).id()) {
            case Exhibits.MINERALS -> 8;
            case Exhibits.SCRAP -> 7;
            default -> 5;
        };
    }

    @Override
    public void update(Shell shell) {
        if (shell.in.back || shell.in.menu && !shell.in.confirm) {
            shell.pop();
            return;
        }
        tab = Math.min(tab, tabs() - 1);
        int n = cells(shell).size(), cols = columns();
        if (shell.in.prevTool || shell.in.nextTool || cursor < 0 && (shell.in.leftPressed || shell.in.rightPressed)) {
            tab = Math.floorMod(tab + (shell.in.prevTool || shell.in.leftPressed ? -1 : 1), tabs());
            cursor = Math.min(cursor, cells(shell).size() - 1);
            shell.sfx(Sfx.SWITCH);
            return;
        }
        if (cursor < 0) {
            if (shell.in.downPressed && n > 0) {
                cursor = 0;
                shell.sfx(Sfx.SWITCH);
            }
        } else if (shell.in.rightPressed) {
            cursor = (cursor + 1) % n;
        } else if (shell.in.leftPressed) {
            cursor = (cursor + n - 1) % n;
        } else if (shell.in.downPressed) {
            cursor = cursor + cols < n ? cursor + cols : cursor % cols;
        } else if (shell.in.upPressed) {
            cursor = cursor - cols >= 0 ? cursor - cols : -1;
        }
        if (!shell.in.confirm) {
            return;
        }
        if (soundTab()) {
            shell.sfx(Sfx.DOOR_OPEN);
            shell.push(Systems.jukebox(sys.play));
            return;
        }
        donate(shell, exhibits.get(tab));
    }

    private void donate(Shell shell, Exhibits.Exhibit e) {
        Game game = shell.game;
        Museum museum = sys.museum;
        Museum.Donation d = museum.donate(game, e);
        if (d.given().isEmpty() && d.paid().isEmpty()) {
            say(shell, d.waiting() ? "A REWARD WAITS: MAKE ROOM IN YOUR MONITORS" : "NOTHING YOU CARRY IS MISSING HERE");
            shell.sfx(Sfx.ERROR);
            return;
        }
        StringBuilder text = new StringBuilder();
        if (!d.given().isEmpty()) {
            text.append(d.given().size() == 1 ? "DONATED THE " + game.item(d.given().get(0)).name()
                    : "DONATED " + d.given().size() + " PIECES");
            game.xp(starpost.core.Skills.SCRAPPING, 5 * d.given().size());
        }
        for (Exhibits.Milestone m : d.paid()) {
            text.append(text.isEmpty() ? "" : ". ").append("REWARD: ").append(m.text());
        }
        if (d.waiting()) {
            text.append(". A REWARD WAITS FOR ROOM");
        }
        say(shell, text.toString());
        shell.sfx(d.paid().isEmpty() ? Sfx.REGISTER : Sfx.PERFECT);
        if (museum.complete(e) && !d.given().isEmpty()) {
            shell.toast(e.name() + " COMPLETE!");
        }
        if (museum.capJustReturned(game)) {
            shell.pop();                       // Hazel wants to see it
            sys.capReturned();
        }
    }

    private void say(Shell shell, String text) {
        note = text;
        noteAt = shell.ticks;
    }

    /** Every Record the game knows (the Ruins', the festivals', the museum's), in catalogue order. */
    private static List<String> records(Shell shell) {
        List<String> out = new ArrayList<>();
        for (Item item : shell.catalog.items()) {
            if (recordFlag(item.id()) != null) {
                out.add(item.id());
            }
        }
        return out;
    }

    /** The story flag that marks a Record found, whichever system owns it, or null for other items. */
    static String recordFlag(String id) {
        if (RuinsContent.recordSong(id) >= 0) {
            return RuinsContent.recordFlag(id);
        }
        String festival = FestivalContent.recordFlag(id);
        return festival != null ? festival : MuseumContent.recordFlag(id);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        Museum museum = sys.museum;
        int x = 8, y = 6, w = canvas.width() - 16, h = canvas.height() - 12;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, "TAILS' WORKSHOP MUSEUM", x + 10, y + 7, Text.YELLOW);
        int total = 0, have = 0;
        for (Exhibits.Exhibit e : exhibits) {
            total += e.items().size();
            have += museum.count(e);
        }
        Text.right(canvas, have + "/" + total + " ON SHOW", x + w - 10, y + 7, Text.WHITE);
        drawTabs(shell, canvas, x, y + 21, w);
        List<String> cells = cells(shell);
        cursor = Math.min(cursor, cells.size() - 1);
        if (soundTab()) {
            drawShelf(shell, canvas, cells, x + 10, y + 40, w - 20);
        } else {
            drawGrid(shell, canvas, exhibits.get(tab), x + 10, y + 40, w - 20);
            drawMilestones(shell, canvas, exhibits.get(tab), x + 10, y + 160, w - 20);
        }
        drawInfo(shell, canvas, cells, x + 10, y + 128, w - 20);
        String footer = soundTab() ? "CONFIRM: TO THE INN'S JUKEBOX    Q/E: PAGES    BACK: LEAVE"
                : "CONFIRM: DONATE (" + museum.donatable(game, exhibits.get(tab)).size()
                        + " TO GIVE)    Q/E: PAGES    BACK: LEAVE";
        CompactFont.shadowed(canvas, footer, x + 10, y + h - 11, 1, 0xFF92DBFF, 0xFF000000);
    }

    private void drawTabs(Shell shell, SceneCanvas canvas, int x, int y, int w) {
        int n = tabs(), tw = (w - 12) / n;
        for (int i = 0; i < n; i++) {
            boolean sound = i >= exhibits.size();
            String name = sound ? "RECORDS" : exhibits.get(i).id().equals(Exhibits.SCRAP) ? "SCRAP" : exhibits.get(i).name();
            boolean done = !sound && sys.museum.complete(exhibits.get(i));
            int tx = x + 6 + i * tw;
            if (i == tab) {
                canvas.fill(tx, y - 2, tw - 4, 14, cursor < 0 ? 0x90B66D24 : 0x50B66D24);
            }
            int colour = done ? Text.GREEN : i == tab ? Text.YELLOW : Text.WHITE;
            String label = Text.fit(canvas, name, tw - 8);
            canvas.text(label, tx + (tw - 4 - canvas.textWidth(label)) / 2, y + 1, colour);
        }
        canvas.fill(x + 6, y + 13, w - 12, 1, Text.EDGE);
    }

    /** A collection: found pieces in colour, missing ones as silhouettes (lit up when one is carried). */
    private void drawGrid(Shell shell, SceneCanvas canvas, Exhibits.Exhibit e, int x, int y, int w) {
        Game game = shell.game;
        int cols = columns(), n = e.items().size();
        int rows = (n + cols - 1) / cols;
        int cw = w / cols, ch = rows == 1 ? 66 : 42;
        int top = rows == 1 ? y + 8 : y;
        for (int i = 0; i < n; i++) {
            String id = e.items().get(i);
            int cx = x + (i % cols) * cw, cy = top + (i / cols) * ch;
            boolean found = sys.museum.donated(id), carried = !found && Museum.held(game, id);
            canvas.fill(cx + 2, cy + 2, cw - 4, ch - 4, found ? 0x40FFDB92 : carried ? 0x50FFDB00 : 0x40000000);
            if (i == cursor) {
                int c = (int) (shell.ticks / 8 % 2) == 0 ? 0xFFFFDB00 : 0xFFB69200;
                canvas.fill(cx + 1, cy + 1, cw - 2, 1, c);
                canvas.fill(cx + 1, cy + ch - 2, cw - 2, 1, c);
                canvas.fill(cx + 1, cy + 1, 1, ch - 2, c);
                canvas.fill(cx + cw - 2, cy + 1, 1, ch - 2, c);
            }
            SceneDraw style = found ? SceneDraw.plain() : SceneDraw.plain().withFlash(SILHOUETTE);
            drawPiece(shell, canvas, game.item(id), cx + cw / 2f, cy + ch / 2f, style);
            if (carried) {
                CompactFont.shadowed(canvas, "!", cx + cw - 9, cy + 4, 1, 0xFFFFDB00, 0xFF000000);
            }
        }
    }

    /** One piece centred at ({@code cx}, {@code cy}): a badnik or relic at the ROM's size, small things doubled. */
    private void drawPiece(Shell shell, SceneCanvas canvas, Item item, float cx, float cy, SceneDraw style) {
        String badnik = MuseumArt.badnikKey(item);
        SceneImage picture = badnik != null ? sys.art.badnikPicture(badnik) : sys.art.relic(item.id());
        if (picture != null) {
            int scale = Math.max(picture.width(), picture.height()) <= 20 ? 2 : 1;
            canvas.draw(picture, cx - picture.width() * scale / 2f, cy - picture.height() * scale / 2f,
                    style.withScale(scale));
            return;
        }
        shell.art.icons.draw(canvas, item, cx - 16, cy - 16, style.withScale(2));
    }

    /** The Sound Test shelf: every Record, found ones by name; the jukebox itself is at the Inn. */
    private void drawShelf(Shell shell, SceneCanvas canvas, List<String> records, int x, int y, int w) {
        Game game = shell.game;
        int cols = columns(), cw = w / cols, rh = 17;
        int found = 0;
        for (int i = 0; i < records.size(); i++) {
            String id = records.get(i);
            boolean have = game.flags.contains(recordFlag(id));
            found += have ? 1 : 0;
            int cx = x + (i % cols) * cw, cy = y + (i / cols) * rh;
            if (i == cursor) {
                canvas.fill(cx, cy - 1, cw - 4, rh - 1, 0x60B66D24);
            }
            shell.art.icons.draw(canvas, game.item(id), cx + 2, cy, have ? SceneDraw.plain()
                    : SceneDraw.plain().withFlash(SILHOUETTE));
            String name = have ? game.item(id).name().replace("RECORD: ", "") : "? ? ?";
            CompactFont.shadowed(canvas, CompactFont.fit(name, cw - 28, 1), cx + 22, cy + 5, 1,
                    have ? 0xFFFFFFFF : 0xFF92929F, 0xFF000000);
        }
        int sy = y + 120;
        CompactFont.shadowed(canvas, "SOUND TEST: " + found + " OF " + records.size() + " RECORDS FOUND", x, sy, 1,
                0xFFFFDB00, 0xFF000000);
        small(canvas, "THE LAMPPOST INN'S JUKEBOX IS THE VALLEY'S SOUND TEST: EVERY RECORD PLAYS ITS SONG THERE,"
                + " AND SLOTTING ONE IN OPENS A BONUS TRACK.", x, sy + 11, w, 0xFFDBDBDB, 3);
    }

    private void drawInfo(Shell shell, SceneCanvas canvas, List<String> cells, int x, int y, int w) {
        if (note != null && shell.ticks - noteAt < 240) {
            small(canvas, note, x, y + 2, w, 0xFF92FF49, 3);
            return;
        }
        if (cursor < 0 || cursor >= cells.size()) {
            small(canvas, soundTab() ? "RECORDS FROM THE RUINS, THE FESTIVALS AND THE MUSEUM ITSELF."
                    : "HAZEL DUSTS EVERY PIECE, FAST. PRESS DOWN TO LOOK CLOSER.", x, y + 2, w, 0xFFB6B6B6, 2);
            return;
        }
        Game game = shell.game;
        String id = cells.get(cursor);
        Item item = game.item(id);
        boolean shown = soundTab() ? game.flags.contains(recordFlag(id)) : sys.museum.donated(id);
        if (shown) {
            Text.shadow(canvas, item.name(), x, y, Text.YELLOW);
            small(canvas, item.text(), x, y + 13, w, 0xFFDBDBDB, 2);
        } else if (!soundTab() && Museum.held(game, id)) {
            Text.shadow(canvas, item.name(), x, y, Text.YELLOW);
            small(canvas, "YOU HAVE ONE! CONFIRM TO DONATE IT.", x, y + 13, w, 0xFF92FF49, 2);
        } else {
            Text.shadow(canvas, "? ? ?", x, y, Text.GREY);
            small(canvas, hint(id, game), x, y + 13, w, 0xFFB6B6B6, 2);
        }
    }

    /** Small text wrapped on words to {@code w} pixels, at most {@code lines} lines. */
    static void small(SceneCanvas canvas, String text, int x, int y, int w, int argb, int lines) {
        StringBuilder line = new StringBuilder();
        int n = 0;
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (CompactFont.width(next, 1) > w && !line.isEmpty()) {
                CompactFont.shadowed(canvas, line.toString(), x, y + n * 9, 1, argb, 0xFF000000);
                if (++n >= lines) {
                    return;
                }
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            CompactFont.shadowed(canvas, line.toString(), x, y + n * 9, 1, argb, 0xFF000000);
        }
    }

    /** Where to look for a missing piece. */
    static String hint(String id, Game game) {
        if (id.equals(MuseumContent.CAP)) {
            return game.flags.contains(Museum.MISSING) ? "HAZEL BURIES THINGS UNDER THE PALMS EAST OF TOWN. AND FORGETS."
                    : "THE MUSEUM'S OWN. IT SHOULD BE RIGHT HERE...";
        }
        if (id.startsWith("record")) {
            return id.startsWith("record_") && RuinsContent.recordSong(id) >= 0 ? "DEEP IN THE MARBLE RUINS."
                    : FestivalContent.recordFlag(id) != null ? "WON AT A FESTIVAL." : "A MUSEUM MILESTONE'S PRIZE.";
        }
        for (int band = 0; band < 3; band++) {
            for (String relic : Finds.ruinsRelics(band)) {
                if (relic.equals(id)) {
                    return "IN THE RUINS' " + new String[] {"MARBLE", "LABYRINTH", "SCRAP BRAIN"}[band]
                            + " ROCKS, OR DUG UP IN " + digSeasons(id) + ".";
                }
            }
        }
        return switch (id) {
            case "motobug_shell" -> "POP THE MOTOBUGS THAT RAID THE FARM.";
            case "buzz_bomber_wing", "caterkiller_segment", "batbrain_wing", "yadrin_spike" ->
                    "POP ONE IN THE MARBLE RUINS (CHAMBERS 1-15).";
            case "jaws_fin" -> "POP ONE IN THE LABYRINTH CHAMBERS, OR FISH THE LAKE IN FALL AND WINTER.";
            case "burrobot_drill", "orbinaut_core" -> "POP ONE IN THE LABYRINTH CHAMBERS (16-30).";
            case "bomb_fuse", "ball_hog_cannon" -> "IN THE SCRAP BRAIN CHAMBERS (31-40). BOMBS GO OFF ON THEIR OWN.";
            case "chopper_shell" -> "IT BITES AT THE POND AND THE LAKE.";
            case "red_chopper_shell" -> "BARNABY'S STORY TELLS OF A RED ONE.";
            case "jawz_torpedo" -> "THE LAKE, ON WET DAYS.";
            case "blastoid_cannon" -> "THE LAKE, IN A STORM.";
            case "marble_geode", "tide_geode", "scrap_geode" -> "IN THE RUINS' ROCKS. DON'T LET TAILS CRACK IT.";
            default -> "BREAK ROCKS IN THE MARBLE RUINS.";
        };
    }

    private static String digSeasons(String id) {
        StringBuilder out = new StringBuilder();
        String[] names = {"SPRING", "SUMMER", "FALL", "WINTER"};
        for (int s = 0; s < 4; s++) {
            for (String relic : Finds.buried(s)) {
                if (relic.equals(id)) {
                    out.append(out.isEmpty() ? "" : " OR ").append(names[s]);
                }
            }
        }
        return out.isEmpty() ? "THE GROUND" : out.toString();
    }

    private void drawMilestones(Shell shell, SceneCanvas canvas, Exhibits.Exhibit e, int x, int y, int w) {
        Game game = shell.game;
        int have = sys.museum.count(e);
        CompactFont.shadowed(canvas, "MILESTONES - " + have + " OF " + e.items().size() + " DONATED", x, y, 1,
                0xFFFFDB00, 0xFF000000);
        int n = e.milestones().size(), mw = w / Math.max(1, n);
        for (int i = 0; i < n; i++) {
            Exhibits.Milestone m = e.milestones().get(i);
            boolean claimed = sys.museum.claimed(e, m), reached = have >= m.count();
            int mx = x + i * mw, my = y + 9;
            canvas.fill(mx, my, mw - 4, 28, claimed ? 0x5024B624 : reached ? 0x50FFDB00 : 0x40000000);
            SceneDraw style = claimed || reached ? SceneDraw.plain() : SceneDraw.plain().withAlpha(0.5f);
            if (m.item() != null) {
                shell.art.icons.draw(canvas, game.item(m.item()), mx + 2, my + 6, style);
            } else {
                var ring = shell.art.ring;
                if (ring != null && ring.frameCount() > 0) {
                    canvas.draw(ring.frame(0), mx + 10, my + 14, style);   // the ring's origin is its middle
                }
            }
            String status = claimed ? "GOT IT" : reached ? "WAITS FOR ROOM" : "AT " + m.count();
            CompactFont.shadowed(canvas, status, mx + 21, my + 2, 1, claimed ? 0xFF92FF49 : reached ? 0xFFFFDB00 : 0xFFB6B6B6,
                    0xFF000000);
            small(canvas, m.text(), mx + 21, my + 11, mw - 26, claimed ? 0xFF92FF49 : 0xFFFFFFFF, 2);
        }
    }
}
