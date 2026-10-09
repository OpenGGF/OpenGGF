package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.ui.CompactFont;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.core.Item;
import starpost.ui.Text;

/**
 * The Lamppost Inn: Clementine's counter (food for Momentum) and the jukebox, the game's Sound
 * Test (design doc §6.8). The jukebox starts with three Sonic 1 tracks; each Record found in the
 * Ruins and slotted in unlocks the next. Left and right switch between the two.
 */
final class InnMenu implements Screen {
    private static final int ROWS = 7;

    /**
     * A jukebox track: which ROM and driver id, and what opens it: {@code null} always, a Ruins
     * Record's found flag, or "slot" (the next one opens each time a Record is slotted in for good).
     */
    private record Track(String name, String game, int id, String unlock) {
    }

    private final PlayScreen play;
    private boolean jukebox;
    private int cursor;
    private int top;
    private String playing;

    InnMenu(PlayScreen play) {
        this.play = play;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    private List<Item> food(Shell shell) {
        List<Item> out = new ArrayList<>();
        for (String id : new String[] {"radish_soup", "loop_pie", "chili_dog"}) {
            if (shell.catalog.hasItem(id)) {
                out.add(shell.game.item(id));
            }
        }
        return out;
    }

    /** Every track in order. */
    private static List<Track> tracks(Shell shell) {
        List<Track> out = new ArrayList<>();
        out.add(new Track("GREEN HILL", "s1", Music.S1_GHZ, null));
        out.add(new Track("SPRING YARD", "s1", Music.S1_SYZ, null));
        out.add(new Track("STAR LIGHT", "s1", Music.S1_SLZ, null));
        // Records found in the Ruins open their own Sonic 1 songs (RuinsContent.recordFlag).
        out.add(new Track("MARBLE", "s1", Music.S1_MZ, "record.s1.83"));
        out.add(new Track("LABYRINTH", "s1", Music.S1_LZ, "record.s1.82"));
        out.add(new Track("SCRAP BRAIN", "s1", Music.S1_SBZ, "record.s1.86"));
        out.add(new Track("INVINCIBILITY", "s1", 0x87, "record.s1.87"));
        out.add(new Track("BOSS", "s1", 0x8C, "record.s1.8c"));
        out.add(new Track("FINAL ZONE", "s1", 0x8D, "record.s1.8d"));
        out.add(new Track("DROWNING", "s1", 0x92, "record.s1.92"));
        out.add(new Track("SONIC 1 ENDING", "s1", Music.S1_ENDING, "evaluated_y1"));
        // Records won at festivals open their own songs (FestivalContent.recordSong).
        out.add(new Track("SPECIAL STAGE", "s1", 0x89, "record.s1.89"));
        out.add(new Track("THE MIGRATION", "s3k", 0x32, "record.s3k.32"));
        out.add(new Track("SLOT BONUS", "s3k", 0x1D, "record.s3k.1d"));
        out.add(new Track("DEATH EGG", "s3k", 0x16, "record.s3k.16"));
        out.add(new Track("ICECAP (SONIC 3)", "s3k", 0x10B, "record.s3k.10b"));
        // Slotting a Record in for good opens the next of these.
        out.add(new Track("ANGEL ISLAND", "s3k", 0x01, "slot"));
        out.add(new Track("MUSHROOM HILL", "s3k", 0x0F, "slot"));
        out.add(new Track("ICECAP", "s3k", 0x0B, "slot"));
        out.add(new Track("CARNIVAL NIGHT", "s3k", 0x07, "slot"));
        out.add(new Track("KNUCKLES' THEME", "s3k", 0x1F, "slot"));
        if (shell.ctx.art().rom("s2") != null) {
            out.add(new Track("EMERALD HILL", "s2", 0x81, "slot"));     // Sonic2Music.EMERALD_HILL
            out.add(new Track("CASINO NIGHT", "s2", 0x83, "slot"));     // Sonic2Music.CASINO_NIGHT
        }
        return out;
    }

    private static int slotted(Game game) {
        int records = 0;
        for (String flag : game.flags) {
            if (flag.startsWith("jukebox_record_")) {
                records++;
            }
        }
        return records;
    }

    /** Whether a track is playable. */
    private static boolean open(Game game, List<Track> tracks, int index) {
        Track track = tracks.get(index);
        if (track.unlock() == null) {
            return true;
        }
        if (!track.unlock().equals("slot")) {
            return game.flags.contains(track.unlock());
        }
        int order = 0;
        for (int i = 0; i < index; i++) {
            if ("slot".equals(tracks.get(i).unlock())) {
                order++;
            }
        }
        return order < slotted(game);
    }

    @Override
    public void update(Shell shell) {
        Game game = shell.game;
        if (shell.in.back || shell.in.menu && !shell.in.confirm) {
            if (playing != null) {
                play.chooseMusic(shell);   // back to the place's own music
            }
            shell.pop();
            return;
        }
        if (shell.in.leftPressed || shell.in.rightPressed) {
            jukebox = !jukebox;
            cursor = 0;
            top = 0;
            shell.sfx(Sfx.SWITCH);
            return;
        }
        int size = jukebox ? tracks(shell).size() + 1 : food(shell).size();
        if (shell.in.downPressed) {
            cursor = (cursor + 1) % size;
        } else if (shell.in.upPressed) {
            cursor = (cursor + size - 1) % size;
        }
        top = Math.max(0, Math.min(top, cursor));
        if (cursor >= top + ROWS) {
            top = cursor - ROWS + 1;
        }
        if (!shell.in.confirm) {
            return;
        }
        if (!jukebox) {
            Item item = food(shell).get(cursor);
            int price = item.price() * 2;
            if (game.rings < price || !game.inventory.fits(item, 1)) {
                shell.sfx(Sfx.ERROR);
                return;
            }
            game.rings -= price;
            game.inventory.add(item, 1);
            shell.sfx(Sfx.REGISTER);
        } else if (cursor == 0) {
            slotRecord(shell, game);
        } else {
            List<Track> tracks = tracks(shell);
            Track track = tracks.get(cursor - 1);
            if (!open(game, tracks, cursor - 1)) {
                shell.sfx(Sfx.ERROR);
                return;
            }
            playing = track.name();
            shell.music.want(track.game(), track.id());
        }
    }

    /** Puts a Record from the farmer's monitors into the jukebox: the next track unlocks. */
    private static void slotRecord(Shell shell, Game game) {
        Inventory inv = game.inventory;
        for (int i = 0; i < inv.size(); i++) {
            String id = inv.id(i);
            if (id != null && id.startsWith("record")) {
                inv.useOne(i);
                game.flags.add("jukebox_record_" + slotted(game));
                shell.sfx(Sfx.PERFECT);
                shell.toast("A BONUS TRACK FOR THE JUKEBOX!");
                return;
            }
        }
        shell.toast("RECORDS TURN UP DEEP IN THE RUINS");
        shell.sfx(Sfx.ERROR);
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        int w = 320, h = 34 + ROWS * 20 + 26, x = (canvas.width() - w) / 2, y = 16;
        Text.panel(canvas, x, y, w, h);
        Text.shadow(canvas, "LAMPPOST INN", x + 10, y + 8, Text.YELLOW);
        Text.right(canvas, (jukebox ? "< JUKEBOX >" : "< COUNTER >"), x + w - 10, y + 8, Text.WHITE);
        if (!jukebox) {
            List<Item> food = food(shell);
            for (int i = 0; i < food.size(); i++) {
                Item item = food.get(i);
                int ry = y + 28 + i * 20;
                if (i == cursor) {
                    canvas.fill(x + 6, ry - 2, w - 12, 19, 0x60B66D24);
                }
                shell.art.icons.draw(canvas, item, x + 10, ry, SceneDraw.plain());
                Text.shadow(canvas, item.name(), x + 32, ry + 4, i == cursor ? Text.YELLOW : Text.WHITE);
                Text.right(canvas, item.price() * 2 + "", x + w - 10, ry + 4, Text.WHITE);
            }
            Item sel = food.get(Math.min(cursor, food.size() - 1));
            Text.note(canvas, "+" + sel.momentum() + " MOMENTUM. " + sel.text(), x + 10, y + h - 18, w - 20, Text.GREY);
            return;
        }
        List<Track> tracks = tracks(shell);
        int open = 0;
        for (int i = 0; i < tracks.size(); i++) {
            if (open(game, tracks, i)) {
                open++;
            }
        }
        for (int i = top; i < Math.min(tracks.size() + 1, top + ROWS); i++) {
            int ry = y + 28 + (i - top) * 20;
            if (i == cursor) {
                canvas.fill(x + 6, ry - 2, w - 12, 19, 0x60B66D24);
            }
            if (i == 0) {
                Text.shadow(canvas, "SLOT IN A RECORD", x + 14, ry + 4, i == cursor ? Text.YELLOW : Text.GREEN);
                continue;
            }
            Track track = tracks.get(i - 1);
            boolean have = open(game, tracks, i - 1);
            String name = have ? track.name() : "? ? ?";
            Text.shadow(canvas, (track.name().equals(playing) ? "> " : "  ") + name, x + 14, ry + 4,
                    have ? (i == cursor ? Text.YELLOW : Text.WHITE) : Text.GREY);
            if (have) {
                CompactFont.shadowed(canvas, track.game().toUpperCase(), x + w - 40, ry + 6, 1, 0xFF92DBFF, 0xFF000000);
            }
        }
        Text.note(canvas, open + " OF " + tracks.size() + " TRACKS. RECORDS PLAY THEIR OWN SONG; SLOT ONE FOR A BONUS.",
                x + 10, y + h - 18, w - 20, Text.GREY);
    }
}
