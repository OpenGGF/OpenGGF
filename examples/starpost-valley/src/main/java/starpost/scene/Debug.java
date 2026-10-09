package starpost.scene;

import starpost.core.Game;
import starpost.core.Plot;

/**
 * Debug commands for the capture tool's {@code jump=} steps (an underscore or space separates
 * arguments). Players never reach these.
 * <ul>
 *   <li>{@code new FARMER} — a fresh game as sonic, tails or knuckles, straight into play;</li>
 *   <li>{@code season S}, {@code day D}, {@code time HHMM}, {@code rain on|off};</li>
 *   <li>{@code valley X} — walk out to the valley at x; {@code farm X DEPTH} — the farm;</li>
 *   <li>{@code give ITEM N}, {@code rings N}, {@code momentum N}, {@code select SLOT};</li>
 *   <li>{@code demo} — a planted, half-grown field to look at; {@code sleep} — end the day;</li>
 *   <li>{@code music on|off};</li>
 *   <li>{@code people ...} — the neighbours (see {@code starpost.people.PeopleDebug}).</li>
 * </ul>
 */
final class Debug {
    private Debug() {
    }

    static boolean apply(Shell shell, String command) {
        String[] p = command.trim().split("[\\s_]+");
        try {
            switch (p[0]) {
                case "new" -> {
                    shell.game = shell.newGame(1234, p.length > 1 ? p[1] : "sonic");
                    shell.goNow(new PlayScreen(shell));
                }
                case "season" -> shell.game.calendar.set(shell.game.calendar.year(), Integer.parseInt(p[1]),
                        shell.game.calendar.day(), shell.game.calendar.minutes());
                case "day" -> shell.game.calendar.set(shell.game.calendar.year(), shell.game.calendar.season(),
                        Integer.parseInt(p[1]), shell.game.calendar.minutes());
                case "time" -> {
                    int hhmm = Integer.parseInt(p[1]);
                    shell.game.calendar.set(shell.game.calendar.year(), shell.game.calendar.season(),
                            shell.game.calendar.day(), hhmm / 100 * 60 + hhmm % 100);
                }
                case "weather" -> {
                    shell.game.weather = Integer.parseInt(p[1]);
                    shell.game.raining = shell.game.weather == starpost.core.Game.RAIN || shell.game.weather == starpost.core.Game.STORM;
                }
                case "aurora" -> shell.game.aurora = p[1].equals("on");
                case "give" -> {
                    // Item ids contain underscores: the last part is the count when it is a number.
                    boolean counted = p.length > 2 && p[p.length - 1].matches("\\d+");
                    String id = String.join("_", java.util.Arrays.copyOfRange(p, 1, counted ? p.length - 1 : p.length));
                    shell.game.inventory.add(shell.game.item(id), counted ? Integer.parseInt(p[p.length - 1]) : 1);
                }
                case "rings" -> shell.game.rings = Integer.parseInt(p[1]);
                case "momentum" -> shell.game.momentum = Integer.parseInt(p[1]);
                case "select" -> shell.game.inventory.select(Integer.parseInt(p[1]));
                case "valley", "farm" -> {
                    if (!(shell.screen() instanceof PlayScreen play)) {
                        return false;
                    }
                    play.debugPlace(p[0].equals("farm"), Float.parseFloat(p[1]), p.length > 2 ? Float.parseFloat(p[2]) : 30);
                }
                case "demo" -> demo(shell.game);
                case "close" -> shell.closeOverlays();
                case "yearend" -> {
                    shell.game.calendar.set(2, 0, 1, starpost.core.Calendar.DAY_START);
                    shell.goNow(new YearEndScreen(() -> new MorningCard(() -> new PlayScreen(shell))));
                }
                case "pest" -> {
                    if (!(shell.screen() instanceof PlayScreen play)) {
                        return false;
                    }
                    play.actors.add(starpost.farm.Pests.motobug(shell.game, Integer.parseInt(p[1]), Float.parseFloat(p[2])));
                }
                case "intro" -> {
                    shell.game = shell.newGame(1234, p.length > 1 ? p[1] : "sonic");
                    shell.goNow(new IntroScreen());
                }
                case "sleep" -> shell.go(new DayEndScreen(false));
                case "music" -> shell.music.setEnabled(p[1].equals("on"));
                case "ruins" -> {
                    return starpost.ruins.RuinsSystem.debug(shell, p);
                }
                case "people" -> {
                    return starpost.people.PeopleDebug.apply(shell, p);
                }
                default -> {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Clears and plants the first twenty columns at mixed ages, for looking at. */
    private static void demo(Game game) {
        String[] crops = {"ring_radish", "sunflower", "checker_cauliflower", "spin_spud", "palm_bean", "spring_tulip"};
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 20; c++) {
                Plot plot = game.farm.raw(r, c);
                plot.cover = Plot.GRASS;
                if (c % 7 == 6) {
                    continue;
                }
                plot.tilled = true;
                plot.watered = (r + c) % 3 != 0;
                plot.crop = crops[(c / 7 * 2 + r / 3) % crops.length];
                plot.age = Math.min(game.catalog.crop(plot.crop).days(), (r * 3 + c) % (game.catalog.crop(plot.crop).days() + 2));
            }
        }
    }
}
