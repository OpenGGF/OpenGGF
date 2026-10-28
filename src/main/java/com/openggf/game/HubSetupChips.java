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
 * The setup chips under the selected game on the hub: what Start will launch.
 * Amber chips are values set for this game only. Chips are status only; they take
 * no focus and run no action, so changes go through Game Setup.
 */
final class HubSetupChips {
    private HubSetupChips() { }

    /** {@code row} is null for the mods chip. */
    record Chip(String label, boolean thisGameOnly, LaunchProfile.Row row) { }

    static List<Chip> forStock(MasterTitleScreen.GameEntry game, LaunchProfile profile, LaunchProfileStore store,
                               SonicConfigurationService config, int modCount) {
        List<Chip> chips = new ArrayList<>();
        if (profile.usesS3kDataSelectCharacters()) {
            chips.add(new Chip("TEAM ON SAVE SCREEN", true, LaunchProfile.Row.CROSS_GAME));
        } else {
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
        if (!"off".equals(profile.crossGameSource())) {
            String donor = LaunchConfigPanel.borrowLabel(profile.crossGameSource()).toUpperCase(Locale.ROOT);
            chips.add(new Chip("FROM " + donor, true, LaunchProfile.Row.CROSS_GAME));
        }
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

    /** Bound the status line, reserving room for an honest count of hidden options. */
    static List<Chip> fit(List<Chip> chips, int width) {
        List<Chip> shown = new ArrayList<>(chips);
        while (!shown.isEmpty()) {
            int hidden = chips.size() - shown.size();
            String more = "+" + hidden + " MORE";
            int used = shown.stream().mapToInt(c -> MenuChrome.chipWidth(c.label()) + 4).sum() - 4;
            if (hidden > 0) used += 4 + MenuChrome.chipWidth(more);
            if (used <= width) {
                if (hidden > 0) shown.add(new Chip(more, false, null));
                return List.copyOf(shown);
            }
            shown.removeLast();
        }
        return chips.isEmpty() ? List.of() : List.of(new Chip("+" + chips.size() + " MORE", false, null));
    }

    static String aspectLabel(String aspect) {
        return switch (aspect) {
            case "WIDE_16_10" -> "16:10";
            case "WIDE_16_9" -> "16:9";
            case "ULTRA_21_9" -> "21:9";
            case "SUPER_32_9" -> "32:9";
            default -> "4:3";
        };
    }

    /** "Start Sonic 2 as Sonic & Tails, 4:3, rewind on". */
    static String startSummary(String gameName, List<Chip> chips) {
        StringBuilder text = new StringBuilder("Start ").append(gameName);
        for (int i = 0; i < chips.size(); i++) {
            Chip chip = chips.get(i);
            if (chip.row() == null) continue;
            String part = chip.row() == LaunchProfile.Row.MAIN_CHARACTER ? titleCaseWords(chip.label())
                    : chip.label().toLowerCase(Locale.ROOT);
            if (chip.row() == LaunchProfile.Row.CROSS_GAME && chip.label().startsWith("FROM ")) {
                part = "from " + titleCaseWords(chip.label().substring(5));
            }
            boolean first = i == 0;
            text.append(first && chip.row() == LaunchProfile.Row.MAIN_CHARACTER ? " as " : first ? ": " : ", ")
                    .append(part);
        }
        return text.toString();
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
