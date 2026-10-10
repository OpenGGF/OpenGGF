package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.ui.CompactFont;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.people.People;
import starpost.people.VillagerDef;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * The Signpost Board, up close (an overlay; the clock stops): three pages, the requests pinned
 * to it, the season's calendar and the festivals' records. The cursor starts on the page tabs
 * (left and right turn the page); down goes into the page. Requests are taken on with jump; the
 * calendar shows the festivals and the neighbours' birthdays, and the bottom line says what the
 * chosen day holds; the records give each festival's result this year, its best and its trophy.
 * Prizes waiting for room in the monitors are handed over on opening. The board says what
 * happened on its own bottom line (a toast would land on its rows).
 */
final class BoardScreen implements Screen {
    private static final int W = 368;
    private static final int H = 192;

    private static final int REQUESTS = 0;
    private static final int CALENDAR = 1;
    private static final int RECORDS = 2;
    /** How long the board's own message stays on its bottom line. */
    private static final int SAY_TICKS = 150;

    private final FestivalSystem sys;
    private int page = REQUESTS;
    private String said = "";
    private long saidAt = -1000;
    /** -1 on the tabs; otherwise the row (requests) or the day cell (calendar). */
    private int cursor = -1;
    private int season;
    private long opened;

    BoardScreen(FestivalSystem sys) {
        this.sys = sys;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void enter(Shell shell) {
        opened = shell.ticks;
        season = shell.game.calendar.season();
        List<String> handed = sys.festivals.collect(shell.game);
        if (!handed.isEmpty()) {
            say(shell, "COLLECTED " + String.join(", ", handed));
            shell.sfx(Sfx.PERFECT);
        }
    }

    /** Puts a message on the board's bottom line for a while. */
    private void say(Shell shell, String text) {
        said = text;
        saidAt = shell.ticks;
    }

    /** Debug: opens on the calendar, today's cell chosen. */
    void debugCalendar() {
        page = CALENDAR;
        cursor = 12;
    }

    /** Debug: opens on the records. */
    void debugRecords() {
        page = RECORDS;
    }

    /** The requests page's rows: postings first, then what's been taken on. */
    private List<Request> rows() {
        List<Request> out = new ArrayList<>(sys.festivals.board.posted());
        out.addAll(sys.festivals.board.active());
        return out;
    }

    @Override
    public void update(Shell shell) {
        var in = shell.in;
        if (in.back || in.menu && !in.confirm) {
            shell.pop();
            return;
        }
        if (cursor < 0) {
            if (in.leftPressed || in.rightPressed) {
                page = Math.floorMod(page + (in.rightPressed ? 1 : -1), 3);
                shell.sfx(Sfx.SWITCH);
            } else if ((in.downPressed || in.confirm) && page != RECORDS) {
                cursor = page == CALENDAR ? shell.game.calendar.day() - 1 : 0;
                if (page == REQUESTS && rows().isEmpty()) {
                    cursor = -1;
                }
                if (page == CALENDAR && season != shell.game.calendar.season()) {
                    cursor = 0;
                }
            }
            return;
        }
        if (page == CALENDAR) {
            updateCalendar(shell);
        } else {
            updateRequests(shell);
        }
    }

    private void updateCalendar(Shell shell) {
        var in = shell.in;
        if (in.upPressed) {
            cursor -= 7;
            if (cursor < 0) {
                cursor = -1;
            }
        } else if (in.downPressed) {
            cursor = Math.min(Calendar.DAYS_PER_SEASON - 1, cursor + 7);
        } else if (in.leftPressed) {
            if (--cursor < 0) {
                season = (season + 3) % 4;
                cursor = Calendar.DAYS_PER_SEASON - 1;
            }
        } else if (in.rightPressed) {
            if (++cursor >= Calendar.DAYS_PER_SEASON) {
                season = (season + 1) % 4;
                cursor = 0;
            }
        }
    }

    private void updateRequests(Shell shell) {
        var in = shell.in;
        List<Request> rows = rows();
        if (rows.isEmpty()) {
            cursor = -1;
            return;
        }
        if (in.upPressed) {
            cursor--;
        } else if (in.downPressed) {
            cursor = Math.min(rows.size() - 1, cursor + 1);
        }
        if (cursor < 0 || !in.confirm) {
            return;
        }
        Request r = rows.get(Math.min(cursor, rows.size() - 1));
        Game game = shell.game;
        if (r.accepted) {
            say(shell, r.type == Request.POP ? progress(r, game)
                    : "YOU " + progress(r, game) + ". TALK TO " + name(r.villager, game));
            return;
        }
        Board board = sys.festivals.board;
        if (board.active().size() >= Board.MAX_ACTIVE) {
            say(shell, "THREE AT A TIME! FINISH ONE FIRST");
            shell.sfx(Sfx.ERROR);
            return;
        }
        shell.push(new Ask("TAKE THIS ON?", () -> {
            if (board.accept(r, game)) {
                shell.sfx(Sfx.REGISTER);
                say(shell, r.type == Request.POP ? "TAKEN ON. POP THEM ON THE FARM OR IN THE RUINS"
                        : "TAKEN ON. BRING THEM TO " + name(r.villager, game) + " AND TALK");
            }
        }));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int x = (canvas.width() - W) / 2, y = 14;
        FestivalScreen.solidPanel(canvas, x, y, W, H);
        Text.shadow(canvas, "SIGNPOST BOARD", x + 10, y + 8, Text.YELLOW);
        String tab = switch (page) {
            case CALENDAR -> Calendar.seasonName(season) + " CALENDAR";
            case RECORDS -> "RECORDS";
            default -> "REQUESTS";
        };
        int tabColour = cursor < 0 && (shell.ticks / 16) % 2 == 0 ? Text.YELLOW : Text.WHITE;
        Text.right(canvas, "< " + tab + " >", x + W - 10, y + 8, cursor < 0 ? tabColour : Text.GREY);
        canvas.fill(x + 8, y + 21, W - 16, 1, 0xFFB66D24);
        String note = switch (page) {
            case CALENDAR -> drawCalendar(shell, canvas, x, y);
            case RECORDS -> drawRecords(shell, canvas, x, y);
            default -> drawRequests(shell, canvas, x, y);
        };
        boolean saying = !said.isEmpty() && shell.ticks - saidAt < SAY_TICKS;
        canvas.fill(x + 8, y + H - 21, W - 16, 1, 0x80B66D24);
        Text.note(canvas, saying ? said : note, x + 10, y + H - 16, W - 20, saying ? Text.GREEN : Text.YELLOW);
    }

    /**
     * The notes: what is wanted on the first line (an item's icon at the end), who wants it and
     * for how long, with the pay, on the second. Postings first, then what's been taken on.
     */
    private String drawRequests(Shell shell, SceneCanvas canvas, int x, int y) {
        Game game = shell.game;
        List<Request> rows = rows();
        int today = game.calendar.dayNumber();
        if (rows.isEmpty()) {
            Text.centred(canvas, "NOTHING PINNED UP TODAY.", y + 70, Text.GREY);
            Text.centred(canvas, "NEIGHBOURS POST NEW REQUESTS EACH MORNING.", y + 86, Text.GREY);
        }
        int postedCount = sys.festivals.board.posted().size();
        for (int i = 0; i < rows.size(); i++) {
            Request r = rows.get(i);
            int ry = y + 28 + i * 23 + (i >= postedCount && postedCount > 0 ? 6 : 0);
            if (i == postedCount && postedCount > 0) {
                canvas.fill(x + 8, ry - 5, W - 16, 1, 0x80B66D24);       // above: posted; below: taken on (green)
            }
            if (i == cursor) {
                canvas.fill(x + 6, ry - 3, W - 12, 22, 0x60B66D24);
            }
            SceneImage face = sys.peopleArt().head(body(r.villager, game));
            if (face != null) {
                int fh = Math.min(18, face.height());
                canvas.drawRegion(face, 0, 0, face.width(), fh, x + 12, ry - 1, face.width(), fh, SceneDraw.plain());
            }
            boolean special = r.type == Request.SPECIAL;
            String what = special ? Board.specialHeadline(r, game) : Board.describe(r, game);
            int colour = r.accepted ? Text.GREEN : special ? Text.RED : Text.WHITE;
            Text.shadow(canvas, Text.fit(canvas, what, W - 80), x + 42, ry, colour);
            if (r.delivery() && game.catalog.hasItem(r.item)) {
                shell.art.icons.draw(canvas, game.item(r.item), x + W - 30, ry - 3, SceneDraw.plain());
            }
            String sub = "FOR " + name(r.villager, game) + ", " + r.daysLeft(today) + (r.daysLeft(today) == 1
                    ? " DAY LEFT" : " DAYS LEFT");
            if (r.accepted) {
                sub += " - " + progress(r, game);
            }
            CompactFont.shadowed(canvas, CompactFont.fit(sub, W - 116, 1), x + 42, ry + 11, 1, 0xFFB6B6B6, 0xFF000000);
            String pay = r.rings + " RINGS";
            CompactFont.shadowed(canvas, pay, x + W - 12 - CompactFont.width(pay, 1), ry + 11, 1, 0xFFFFDB00,
                    0xFF000000);
        }
        if (cursor >= 0 && cursor < rows.size()) {
            Request r = rows.get(cursor);
            if (r.accepted) {
                return r.delivery() ? "TALK TO " + name(r.villager, game) + " WITH THE GOODS" : "POP THEM ANYWHERE";
            }
            if (r.type == Request.SPECIAL) {
                return "(" + r.count + " WILL DO.) PAID PARTLY IN ROBO COLA.";
            }
            return "JUMP: TAKE IT ON (" + sys.festivals.board.active().size() + " OF " + Board.MAX_ACTIVE + " TAKEN)";
        }
        return "DOWN: CHOOSE A REQUEST.  LEFT/RIGHT: PAGES";
    }

    /** What a taken-on request still needs, in the second line's words. */
    private String progress(Request r, Game game) {
        int done = sys.festivals.board.progress(r, game);
        return r.type == Request.POP ? "POPPED " + done + " OF " + r.count : "HAVE " + done + " OF " + r.count;
    }

    static String name(String villager, Game game) {
        People people = game.section(People.class);
        VillagerDef v = people == null ? null : people.cast.get(villager);
        return v == null ? villager.toUpperCase() : v.name;
    }

    private String body(String villager, Game game) {
        People people = game.section(People.class);
        VillagerDef v = people == null ? null : people.cast.get(villager);
        return v == null ? FestivalScreen.body(villager) : v.body();
    }

    /**
     * The season's four weeks: festivals in brown with a ring, birthdays with the neighbour's
     * face, today framed in yellow; under the grid, the chosen festival's blurb.
     */
    private String drawCalendar(Shell shell, SceneCanvas canvas, int x, int y) {
        Game game = shell.game;
        FestivalBook book = sys.festivals.book;
        People people = game.section(People.class);
        int cw = 48, ch = 26, gx = x + (W - 7 * cw) / 2, gy = y + 36;
        for (int d = 0; d < 7; d++) {
            CompactFont.shadowed(canvas, Calendar.weekdayName(d), gx + d * cw + 2, gy - 9, 1, 0xFFB6B6B6, 0xFF000000);
        }
        for (int i = 0; i < Calendar.DAYS_PER_SEASON; i++) {
            int day = i + 1;
            int cx = gx + i % 7 * cw, cy = gy + i / 7 * ch;
            boolean isToday = season == game.calendar.season() && day == game.calendar.day();
            Festival f = book.on(season, day);
            canvas.fill(cx + 1, cy + 1, cw - 2, ch - 2, f != null ? 0xFF6D2400 : 0xFF101838);
            if (isToday) {
                canvas.fill(cx, cy, cw, 1, 0xFFFFDB00);
                canvas.fill(cx, cy + ch - 1, cw, 1, 0xFFFFDB00);
                canvas.fill(cx, cy, 1, ch, 0xFFFFDB00);
                canvas.fill(cx + cw - 1, cy, 1, ch, 0xFFFFDB00);
            }
            if (i == cursor) {
                canvas.fill(cx + 1, cy + 1, cw - 2, ch - 2, 0x60FFFFFF);
            }
            CompactFont.shadowed(canvas, Integer.toString(day), cx + 3, cy + 3, 1, isToday ? 0xFFFFDB00 : 0xFFFFFFFF,
                    0xFF000000);
            if (f != null) {
                canvas.draw(shell.art.ring.frame((int) (shell.ticks / 8 % 4)), cx + cw - 12, cy + 9, SceneDraw.plain());
            }
            VillagerDef birthday = birthdayOn(people, season, day);
            SceneImage face = birthday == null ? null : sys.peopleArt().head(birthday.body());
            if (face != null) {
                // Beside the day's number, not over it.
                int fh = Math.min(ch - 4, face.height());
                int fw = Math.min(cw / 2, face.width());
                canvas.drawRegion(face, 0, 0, fw, fh, cx + 17, cy + ch - 2 - fh, fw, fh, SceneDraw.plain());
            }
        }
        if (cursor < 0) {
            return "DOWN: CHOOSE A DAY.  RINGS: FESTIVALS. FACES: BIRTHDAYS";
        }
        int day = cursor + 1;
        Festival f = book.on(season, day);
        VillagerDef birthday = birthdayOn(people, season, day);
        String date = Calendar.seasonName(season) + " " + day + ": ";
        if (f != null) {
            List<String> blurb = wrapCompact(f.where + ", " + f.hours() + ". " + f.blurb, W - 20);
            for (int i = 0; i < Math.min(2, blurb.size()); i++) {
                CompactFont.shadowed(canvas, blurb.get(i), x + 10, gy + 4 * ch + 5 + i * 9, 1, 0xFFFFFFFF, 0xFF000000);
            }
            return date + f.name + (birthday != null ? ". " + birthday.name + "'S BIRTHDAY TOO" : "");
        }
        return date + (birthday != null ? birthday.name + "'S BIRTHDAY" : "NOTHING ON");
    }

    /**
     * Every festival: when it falls, this year's result, its best and, for the six contests, the
     * trophy (a gold ring when it is on the shelf). The requests done go underneath.
     */
    private String drawRecords(Shell shell, SceneCanvas canvas, int x, int y) {
        Game game = shell.game;
        Festivals festivals = sys.festivals;
        int year = game.calendar.year();
        int i = 0;
        for (Festival f : festivals.book.all()) {
            int ry = y + 27 + i++ * 18;
            boolean trophy = Festivals.hasTrophy(f.id) && festivals.prizeTaken("trophy." + f.id);
            Text.shadow(canvas, f.name, x + 30, ry, trophy ? Text.YELLOW : Text.WHITE);
            String when = Calendar.seasonName(f.season) + " " + f.day;
            String best = festivals.bestLine(f.id);
            CompactFont.shadowed(canvas, when + (best.isEmpty() ? "" : "  -  BEST " + best), x + 30, ry + 10, 1,
                    0xFFB6B6B6, 0xFF000000);
            String result = festivals.resultLine(f.id, year);
            Text.right(canvas, result, x + W - 12, ry + 4, result.equals("WON") ? Text.YELLOW
                    : result.equals("-") ? Text.GREY : Text.WHITE);
            if (trophy) {
                canvas.draw(shell.art.ring.frame((int) ((shell.ticks / 8 + i) % 4)), x + 18, ry + 8, SceneDraw.plain());
            } else if (Festivals.hasTrophy(f.id)) {
                canvas.draw(shell.art.ring.frame(0), x + 18, ry + 8, SceneDraw.plain().withAlpha(0.25f));   // still to win
            }
        }
        Board board = festivals.board;
        return "YEAR " + year + ".  REQUESTS DONE: " + board.completed()
                + (board.specialOrders() > 0 ? ".  ROBOTNIK'S ORDERS: " + board.specialOrders() : "");
    }

    /** Splits text into lines of the compact font no wider than {@code width} pixels. */
    static List<String> wrapCompact(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (CompactFont.width(next, 1) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    /** Whose birthday falls on a date (the neighbours met or not; never a pet's), or null. */
    private static VillagerDef birthdayOn(People people, int season, int day) {
        if (people == null) {
            return null;
        }
        for (VillagerDef v : people.cast.all()) {
            if (!v.isPet() && v.birthdaySeason() == season && v.birthdayDay() == day) {
                return v;
            }
        }
        return null;
    }
}
