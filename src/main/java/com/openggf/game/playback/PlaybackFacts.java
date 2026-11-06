package com.openggf.game.playback;

import com.openggf.i18n.UiMessages;
import com.openggf.game.recording.RecordingVersionWarning;
import com.openggf.game.recording.UserRecordingEntry;
import com.openggf.trace.catalog.TraceEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Player-facing wording for playback library facts; formatting only, no launch or availability policy. */
public final class PlaybackFacts {
    /** A labelled fact; {@code tone} selects its status colour in the presenting menu. */
    public record Fact(String label, String value, Tone tone) {
        public Fact(String label, String value) { this(label, value, Tone.NORMAL); }
    }

    public enum Tone { NORMAL, READY, WARNING, ERROR }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private PlaybackFacts() { }

    public static String gameName(String id) {
        return switch (id == null ? "" : id) {
            case "s1" -> UiMessages.text("editor.editor_project_workspace.sonic_1_1t0f5st");
            case "s2" -> UiMessages.text("editor.editor_project_workspace.sonic_2_1t0f5su");
            case "s3k" -> UiMessages.text("editor.editor_project_workspace.sonic_3_knuckles_138eayj");
            case "" -> UiMessages.text("menu.playbackfacts.unknown_game");
            default -> id;
        };
    }

    /** One-based zone and act, matching how players count them. */
    public static String zoneAct(int zone, int act) {
        return UiMessages.text("menu.playback.zone_act", Map.of("zone", zone + 1, "act", act + 1));
    }

    public static String traceStart(TraceEntry trace) {
        if (trace.isRun()) return UiMessages.text("menu.playback.run_segments",
                Map.of("count", trace.runManifest().segments().size(),
                        "value", Integer.toString(trace.runManifest().segments().size())));
        var stage = trace.specialStageNumber();
        return stage.isPresent() ? UiMessages.text("menu.playback.special_stage", Map.of("stage", stage.getAsInt()))
                : zoneAct(trace.zone(), trace.act());
    }

    static String gameStart(String game, String start) {
        return UiMessages.text("menu.playback.game_start", Map.of("game", gameName(game), "start", start));
    }

    public static String team(String main, List<String> sidekicks) {
        List<String> members = new ArrayList<>();
        members.add(main);
        if (sidekicks != null) members.addAll(sidekicks);
        return String.join(" & ", members.stream().filter(name -> name != null && !name.isBlank())
                .map(name -> Character.toUpperCase(name.charAt(0)) + name.substring(1)).toList());
    }

    /** Approximate wall-clock length at 60 rows per second, for comparing entries at a glance. */
    public static String duration(int frames) {
        int seconds = Math.max(0, frames) / 60;
        return seconds >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** Duration plus the exact count in its own coordinate system, e.g. {@code 1:30, 5,400 BK2 rows}. */
    public static String length(PlaybackEntry.Extent extent) {
        return UiMessages.text("menu.playback.duration_extent",
                Map.of("duration", duration(extent.count()), "extent", extent.label()));
    }

    public static String date(Instant at) {
        return at == null ? UiMessages.text("menu.playbackfacts.unknown")
                : DATE_TIME.withLocale(UiMessages.locale()).format(at.atZone(ZoneId.systemDefault()));
    }

    /** Short consequence of a build mismatch, or null when the recording matches this build. */
    public static String versionWarning(RecordingVersionWarning warning) {
        return warning == null ? null : switch (warning) {
            case NONE -> null;
            case MISSING_METADATA -> UiMessages.text("menu.playback.warning.unknown_build");
            case OFFICIAL_VERSION_MISMATCH -> UiMessages.text("menu.playback.warning.other_release");
            case PRERELEASE_BUILD_MISMATCH -> UiMessages.text("menu.playback.warning.other_dev_build");
            case DIRTY_BUILD -> UiMessages.text("menu.playback.warning.local_changes");
        };
    }

    /** Facts for an engine recording, shared by the library details and the recording's own details page. */
    public static List<Fact> recording(UserRecordingEntry entry) {
        List<Fact> facts = new ArrayList<>();
        var manifest = entry.manifest();
        var launch = manifest == null ? null : manifest.launchContext();
        facts.add(new Fact(UiMessages.text("editor.stageplay_menu_presentation.length_11kyxom"), length(new PlaybackEntry.Extent.MovieRows(entry.frameCount()))));
        if (launch != null) {
            facts.add(new Fact(UiMessages.text("editor.stageplay_hud.start_1br2gy"), gameStart(launch.gameId(), zoneAct(launch.zone(), launch.act()))));
            facts.add(new Fact(UiMessages.text("launch.item.team"), team(launch.mainCharacter(), launch.sidekickCharacters())));
        } else facts.add(new Fact(UiMessages.text("editor.stageplay_hud.start_1br2gy"), UiMessages.text("menu.playbackfacts.unknown"), Tone.WARNING));
        facts.add(new Fact(UiMessages.text("menu.playbackfacts.recorded"), date(manifest != null && manifest.createdAt() != null
                ? manifest.createdAt() : entry.modifiedAt())));
        if (manifest != null && manifest.engineIdentity() != null)
            facts.add(new Fact(UiMessages.text("menu.playbackfacts.build"), UiMessages.text("menu.playback.build_version",
                    Map.of("version", manifest.engineIdentity().displayVersion()))));
        String warning = versionWarning(entry.versionWarning());
        if (warning != null) facts.add(new Fact(UiMessages.text("menu.playbackfacts.warning"), warning, Tone.WARNING));
        facts.add(new Fact(UiMessages.text("editor.stageplay_menu_presentation.file_1axlo"), entry.path().toString()));
        return List.copyOf(facts);
    }
}
