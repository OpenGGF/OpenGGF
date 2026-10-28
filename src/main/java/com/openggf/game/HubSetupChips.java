package com.openggf.game;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileStore;
import com.openggf.game.launch.LaunchToggle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The setup chips under the selected game on the hub: what Start will launch, in Game Setup's
 * reading order (the donor game first, because it decides who can play). Amber chips are values
 * set for this game only. Chips are status only; they take no focus and run no action, so changes
 * go through Game Setup. When the row cannot hold every chip, {@link #status} names the rest in
 * words, so nothing Start will use is hidden.
 */
final class HubSetupChips {
    private HubSetupChips() { }

    /** {@code row} is null for the mods chip and the "+N MORE" count. */
    record Chip(String label, boolean thisGameOnly, LaunchProfile.Row row) { }

    /** A run of the status line; amber runs are set for this game only and draw the legend's marker. */
    record Segment(String text, boolean thisGameOnly) { }

    /** Words for the team when the Sonic 3&K save screen chooses it (no team chip is shown then). */
    static final String SAVE_SCREEN_TEAM = "Team chosen on the Sonic 3&K save screen";

    static List<Chip> forStock(MasterTitleScreen.GameEntry game, LaunchProfile profile, LaunchProfileStore store,
                               SonicConfigurationService config, int modCount) {
        List<Chip> chips = new ArrayList<>();
        if (!"off".equals(profile.crossGameSource())) {
            String donor = LaunchConfigPanel.borrowLabel(profile.crossGameSource()).toUpperCase(Locale.ROOT);
            chips.add(new Chip("FROM " + donor, true, LaunchProfile.Row.CROSS_GAME));
        }
        // Under the Sonic 3&K donor its save screen picks the team: the donor chip already says
        // where the team comes from, and the status line says it in words.
        if (!profile.usesS3kDataSelectCharacters()) {
            String main = store.displayValue(profile, LaunchProfile.Row.MAIN_CHARACTER, game);
            String side = store.displayValue(profile, LaunchProfile.Row.SIDEKICK, game);
            String team = "none".equals(profile.sidekick()) ? main : main + " & " + side;
            boolean stockTeam = profile.isStock(LaunchProfile.Row.MAIN_CHARACTER, game)
                    && profile.isStock(LaunchProfile.Row.SIDEKICK, game);
            chips.add(new Chip(team.toUpperCase(Locale.ROOT), !stockTeam, LaunchProfile.Row.MAIN_CHARACTER));
        }
        boolean aspectOverride = !"global".equals(profile.aspect());
        String aspect = aspectOverride
                ? store.displayValue(profile, LaunchProfile.Row.WIDESCREEN, game)
                : aspectLabel(config.getString(SonicConfiguration.DISPLAY_ASPECT));
        chips.add(new Chip(aspect, aspectOverride, LaunchProfile.Row.WIDESCREEN));
        addToggle(chips, "REWIND", profile.rewind(), config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED),
                LaunchProfile.Row.REWIND);
        addToggle(chips, "DEBUG", profile.debugTools(), config.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED),
                LaunchProfile.Row.DEBUG_TOOLS);
        if (modCount > 0) {
            chips.add(new Chip(modCount == 1 ? "1 MOD" : modCount + " MODS", false, null));
        }
        return List.copyOf(chips);
    }

    /** A switch earns a chip when it is on, or when this game overrides it. */
    private static void addToggle(List<Chip> chips, String label, LaunchToggle toggle, boolean allGames,
                                  LaunchProfile.Row row) {
        boolean effective = toggle.resolve(allGames);
        if (!effective && toggle.isDefault()) return;
        String text = label + (effective ? " ON" : " OFF");
        chips.add(new Chip(text, !toggle.isDefault(), row));
    }

    /**
     * Bound the chip row, reserving room for an honest count of hidden options. The count is
     * amber when anything it hides is set for this game only.
     */
    static List<Chip> fit(List<Chip> chips, int width) {
        List<Chip> shown = new ArrayList<>(chips);
        while (!shown.isEmpty()) {
            int hidden = chips.size() - shown.size();
            String more = "+" + hidden + " MORE";
            int used = shown.stream().mapToInt(c -> MenuChrome.chipWidth(c.label()) + 4).sum() - 4;
            if (hidden > 0) used += 4 + MenuChrome.chipWidth(more);
            if (used <= width) {
                if (hidden > 0) shown.add(new Chip(more, anyThisGameOnly(chips.subList(shown.size(), chips.size())), null));
                return List.copyOf(shown);
            }
            shown.removeLast();
        }
        return chips.isEmpty() ? List.of() : List.of(new Chip("+" + chips.size() + " MORE", anyThisGameOnly(chips), null));
    }

    /** The chips a fitted row hides behind its "+N MORE" count (empty when every chip is shown). */
    static List<Chip> hidden(List<Chip> all, List<Chip> shown) {
        if (shown.isEmpty() || !isMoreCount(shown.getLast())) return List.of();
        return List.copyOf(all.subList(shown.size() - 1, all.size()));
    }

    static boolean isMoreCount(Chip chip) {
        return chip.row() == null && chip.label().startsWith("+") && chip.label().endsWith(" MORE");
    }

    private static boolean anyThisGameOnly(List<Chip> chips) {
        return chips.stream().anyMatch(Chip::thisGameOnly);
    }

    /**
     * The status line under the hub buttons, as runs: the save-screen team when the Sonic 3&K
     * donor picks it, then every chip the row hid ("Also rewind on, debug on, 2 mods"). Empty
     * when the chips already say everything. Shown on the card and on Start, the two places a
     * player decides to launch.
     */
    static List<Segment> status(List<Chip> all, List<Chip> shown, boolean saveScreenTeam) {
        List<Chip> hidden = hidden(all, shown);
        List<Segment> line = new ArrayList<>();
        if (saveScreenTeam) line.add(new Segment(hidden.isEmpty() ? SAVE_SCREEN_TEAM
                : "Team on the 3&K save screen", true));
        for (int i = 0; i < hidden.size(); i++) {
            String joint = i > 0 ? ", " : saveScreenTeam ? "; also " : "Also ";
            line.add(new Segment(joint, false));
            line.add(new Segment(phrase(hidden.get(i)), hidden.get(i).thisGameOnly()));
        }
        return List.copyOf(line);
    }

    /** The status line as plain text (tests, logs and the line's width without markers). */
    static String plain(List<Segment> line) {
        StringBuilder text = new StringBuilder();
        for (Segment segment : line) text.append(segment.text());
        return text.toString();
    }

    /** Pixel width of the status line as drawn: each amber run is preceded by the 6 px marker. */
    static int statusWidth(List<Segment> line) {
        int width = 0;
        for (Segment segment : line) {
            width += MenuChrome.compactWidth(segment.text()) + (segment.thisGameOnly() ? MARKER_ROOM : 0);
        }
        return width;
    }

    /**
     * The status line broken into at most two lines of {@code width} pixels at phrase boundaries.
     * One line is the norm (every launch-profile combination fits at 320); a second line is used
     * only if a longer label ever needs it, and nothing is cut. A phrase that starts a new line
     * drops its comma, and "; also" becomes "Also".
     */
    static List<List<Segment>> lines(List<Segment> line, int width) {
        List<List<Segment>> lines = new ArrayList<>();
        List<Segment> current = new ArrayList<>();
        for (int i = 0; i < line.size(); i++) {
            Segment segment = line.get(i);
            List<Segment> unit = new ArrayList<>(List.of(segment));
            boolean joint = !segment.thisGameOnly() && (segment.text().equals(", ") || segment.text().endsWith("also ")
                    || segment.text().equals("Also "));
            if (joint && i + 1 < line.size()) unit.add(line.get(++i));
            List<Segment> trial = new ArrayList<>(current);
            trial.addAll(unit);
            if (!current.isEmpty() && statusWidth(trial) > width) {
                lines.add(List.copyOf(current));
                current = new ArrayList<>();
                if (joint) unit.set(0, new Segment(unit.getFirst().text().equals(", ") ? "" : "Also ", false));
                unit.removeIf(part -> part.text().isEmpty());
            }
            current.addAll(unit);
        }
        if (!current.isEmpty()) lines.add(List.copyOf(current));
        return List.copyOf(lines);
    }

    /** The changed marker (4 px square and its 1 px shadow) and a 2 px gap before an amber run. */
    static final int MARKER_ROOM = 7;

    static String aspectLabel(String aspect) {
        return switch (aspect) {
            case "WIDE_16_10" -> "16:10";
            case "WIDE_16_9" -> "16:9";
            case "ULTRA_21_9" -> "21:9";
            case "SUPER_32_9" -> "32:9";
            default -> "4:3";
        };
    }

    /** A chip as words in a sentence: names keep their capitals ("Sonic & Tails", "from Sonic 3&K"). */
    static String phrase(Chip chip) {
        if (chip.row() == LaunchProfile.Row.MAIN_CHARACTER) return titleCaseWords(chip.label());
        if (chip.row() == LaunchProfile.Row.CROSS_GAME && chip.label().startsWith("FROM "))
            return "from " + titleCaseWords(chip.label().substring(5));
        return chip.label().toLowerCase(Locale.ROOT);
    }

    private static String titleCaseWords(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.toLowerCase(Locale.ROOT).split(" ")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(word.length() > 1 && Character.isLetter(word.charAt(0))
                    ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word.toUpperCase(Locale.ROOT));
        }
        return out.toString();
    }
}
