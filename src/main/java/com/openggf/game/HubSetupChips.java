package com.openggf.game;

import com.openggf.i18n.UiMessages;
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
    record Chip(String label, boolean thisGameOnly, LaunchProfile.Row row, String summary) {
        Chip(String label, boolean thisGameOnly, LaunchProfile.Row row) { this(label, thisGameOnly, row, label); }
    }

    static List<Chip> forStock(MasterTitleScreen.GameEntry game, LaunchProfile profile, LaunchProfileStore store,
                               SonicConfigurationService config, int modCount) {
        List<Chip> chips = new ArrayList<>();
        if (profile.usesS3kDataSelectCharacters()) {
            chips.add(new Chip(UiMessages.text("hub.chip.team_on_save_screen"), true, LaunchProfile.Row.CROSS_GAME, UiMessages.text("hub.chip.team_on_save_screen_summary")));
        } else {
            String main = store.displayValue(profile, LaunchProfile.Row.MAIN_CHARACTER, game);
            String side = store.displayValue(profile, LaunchProfile.Row.SIDEKICK, game);
            String team = "none".equals(profile.sidekick()) ? main : UiMessages.text("hub.chip.team", java.util.Map.of("main", main, "sidekick", side));
            String label = "none".equals(profile.sidekick()) ? chipCharacter(profile.mainCharacter(), main)
                    : UiMessages.text("hub.chip.team", java.util.Map.of("main", chipCharacter(profile.mainCharacter(), main),
                            "sidekick", chipCharacter(profile.sidekick(), side)));
            boolean stockTeam = profile.isStock(LaunchProfile.Row.MAIN_CHARACTER, game)
                    && profile.isStock(LaunchProfile.Row.SIDEKICK, game);
            chips.add(new Chip(label, !stockTeam, LaunchProfile.Row.MAIN_CHARACTER, team));
        }
        boolean aspectOverride = !"global".equals(profile.aspect());
        String aspect = aspectOverride
                ? store.displayValue(profile, LaunchProfile.Row.WIDESCREEN, game)
                : aspectLabel(config.getString(SonicConfiguration.DISPLAY_ASPECT));
        chips.add(new Chip(aspect, aspectOverride, LaunchProfile.Row.WIDESCREEN));
        if (!"off".equals(profile.crossGameSource())) {
            String donor = LaunchConfigPanel.borrowLabel(profile.crossGameSource());
            chips.add(new Chip(UiMessages.text("hub.chip.borrow", java.util.Map.of("game", donor)), true, LaunchProfile.Row.CROSS_GAME, UiMessages.text("hub.chip.borrow_summary", java.util.Map.of("game", donor))));
        }
        addToggle(chips, "hub.chip.rewind", profile.rewind(), config.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED),
                LaunchProfile.Row.REWIND);
        addToggle(chips, "hub.chip.debug", profile.debugTools(), config.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED),
                LaunchProfile.Row.DEBUG_TOOLS);
        if (modCount > 0) {
            chips.add(new Chip(UiMessages.text("hub.chip.mods", java.util.Map.of("count", modCount)), false, null));
        }
        return List.copyOf(chips);
    }

    /** A switch earns a chip when it is on, or when this game overrides it. */
    private static void addToggle(List<Chip> chips, String label, LaunchToggle toggle, boolean allGames,
                                  LaunchProfile.Row row) {
        boolean effective = toggle.resolve(allGames);
        if (!effective && toggle.isDefault()) return;
        String state = effective ? "on" : "off";
        chips.add(new Chip(UiMessages.text(label, java.util.Map.of("state", state)), !toggle.isDefault(), row,
                UiMessages.text(label + "_summary", java.util.Map.of("state", state))));
    }

    /** Bound the status line, reserving room for an honest count of hidden options. */
    static List<Chip> fit(List<Chip> chips, int width) {
        List<Chip> shown = new ArrayList<>(chips);
        while (!shown.isEmpty()) {
            int hidden = chips.size() - shown.size();
            String more = UiMessages.text("hub.chip.more", java.util.Map.of("count", hidden));
            int used = shown.stream().mapToInt(c -> MenuChrome.chipWidth(c.label()) + 4).sum() - 4;
            if (hidden > 0) used += 4 + MenuChrome.chipWidth(more);
            if (used <= width) {
                if (hidden > 0) shown.add(new Chip(more, false, null));
                return List.copyOf(shown);
            }
            shown.removeLast();
        }
        return chips.isEmpty() ? List.of() : List.of(new Chip(UiMessages.text("hub.chip.more", java.util.Map.of("count", chips.size())), false, null));
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

    private static String chipCharacter(String stable, String creatorLabel) {
        return switch (stable) {
            case "sonic", "tails", "knuckles" -> UiMessages.text("hub.chip.character." + stable);
            default -> creatorLabel;
        };
    }

    /** A complete source message consumes semantic summaries; it never parses chip wording. */
    static String startSummary(String gameName, List<Chip> chips) {
        Chip team = chips.stream().filter(chip -> chip.row() == LaunchProfile.Row.MAIN_CHARACTER).findFirst().orElse(null);
        String options = String.join(", ", chips.stream().filter(chip -> chip.row() != null && chip != team)
                .map(Chip::summary).toList());
        if (team != null) return UiMessages.text("hub.start_summary_team", java.util.Map.of("game", gameName,
                "team", team.summary(), "hasOptions", options.isEmpty() ? "no" : "yes", "options", options));
        return UiMessages.text("hub.start_summary", java.util.Map.of("game", gameName,
                "hasOptions", options.isEmpty() ? "no" : "yes", "options", options));
    }
}
