package starpost.festivals;

import starpost.core.Calendar;
import starpost.core.Game;
import starpost.people.People;
import starpost.scene.PlayScreen;
import starpost.scene.Screen;
import starpost.scene.Shell;

/**
 * Debug commands for captures ({@code jump=festival_...}, {@code jump=board_...}); players never
 * reach these. Ids with underscores are rejoined.
 * <ul>
 *   <li>{@code festival day ID [HHMM]}: that festival's day (at HHMM, default as it opens), a new
 *       day's play screen, standing at its sign;</li>
 *   <li>{@code festival start ID}: as {@code day}, then straight into the event;</li>
 *   <li>{@code festival trophies}: every trophy on the shelf; {@code festival secret ID}: this
 *       year's secret friend; {@code festival population N}: the valley's population (the
 *       Flickies); {@code festival exit}: in the maze, a step from Mecha Sonic; {@code festival
 *       at X}: during a festival in the valley, the farmer to x; {@code festival catch N}: the Ice
 *       Cap Festival's fishing contest ends now with N points;</li>
 *   <li>{@code board}: open the board; {@code board calendar}, {@code board records}: open it on that page;
 *       {@code board post N}: N mornings' notes; {@code board accept}: take on the first note;
 *       {@code board fill}: the goods for every delivery taken on.</li>
 * </ul>
 */
public final class FestivalDebug {
    private FestivalDebug() {
    }

    public static boolean apply(Shell shell, String[] p) {
        Game game = shell.game;
        Festivals festivals = game == null ? null : game.section(Festivals.class);
        if (festivals == null) {
            return false;
        }
        if (p[0].equals("board")) {
            return board(shell, festivals, p);
        }
        if (p.length < 2) {
            return false;
        }
        switch (p[1]) {
            case "day", "start" -> {
                boolean timed = p.length > 3 && p[p.length - 1].matches("\\d{3,4}");
                String id = String.join("_", java.util.Arrays.copyOfRange(p, 2, timed ? p.length - 1 : p.length));
                Festival f = festivals.book.get(id);
                if (f == null) {
                    return false;
                }
                int minutes = f.open;
                if (timed) {
                    int hhmm = Integer.parseInt(p[p.length - 1]);
                    minutes = hhmm / 100 * 60 + hhmm % 100;
                }
                game.calendar.set(game.calendar.year(), f.season, f.day, minutes);
                game.weather = f.season == Calendar.WINTER ? Game.SNOW : Game.SUN;
                game.raining = false;
                PlayScreen play = new PlayScreen(shell);
                shell.goNow(play);
                FestivalSystem sys = FestivalSystem.of(play);
                if (sys == null) {
                    return false;
                }
                play.placeInValley(sys.anchorX(f) - 40);
                if (p[1].equals("start")) {
                    sys.start(f);
                }
            }
            case "catch" -> {
                if (festivals.contestAway == null) {
                    return false;
                }
                festivals.contestAway.accept(Integer.parseInt(p[2]));
            }
            case "population" -> game.population = Math.max(6, Math.min(Game.MAX_POPULATION, Integer.parseInt(p[2])));
            case "exit" -> {
                if (!(shell.screen() instanceof MazeScreen maze)) {
                    return false;
                }
                maze.debugNearExit();
            }
            case "at" -> {
                if (!(shell.screen() instanceof FestivalScreen screen)) {
                    return false;
                }
                screen.play.valley().pose.x = Float.parseFloat(p[2]);
            }
            case "trophies" -> {
                for (Festival f : festivals.book.all()) {
                    if (Festivals.hasTrophy(f.id)) {
                        festivals.takePrize("trophy." + f.id);
                    }
                }
            }
            case "secret" -> {
                People people = game.section(People.class);
                if (people == null || p.length < 3 || people.cast.get(p[2]) == null) {
                    return false;
                }
                festivals.debugSecret(p[2], game.calendar.year());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static boolean board(Shell shell, Festivals festivals, String[] p) {
        Game game = shell.game;
        Screen screen = shell.screen();
        PlayScreen play = screen instanceof PlayScreen ps ? ps : null;
        FestivalSystem sys = play == null ? null : FestivalSystem.of(play);
        String what = p.length > 1 ? p[1] : "open";
        switch (what) {
            case "open", "calendar", "records" -> {
                if (sys == null) {
                    return false;
                }
                BoardScreen board = new BoardScreen(sys);
                shell.push(board);
                if (what.equals("calendar")) {
                    board.debugCalendar();
                } else if (what.equals("records")) {
                    board.debugRecords();
                }
            }
            case "post" -> {
                int n = p.length > 2 ? Integer.parseInt(p[2]) : 1;
                for (int i = 0; i < n; i++) {
                    game.calendar.nextDay();
                    festivals.board.morning(game, game.section(People.class));
                }
            }
            case "accept" -> {
                if (festivals.board.posted().isEmpty()) {
                    return false;
                }
                festivals.board.accept(festivals.board.posted().get(0), game);
            }
            case "fill" -> {
                for (Request r : festivals.board.active()) {
                    if (r.delivery() && game.catalog.hasItem(r.item)) {
                        game.inventory.add(game.item(r.item), r.count);
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
