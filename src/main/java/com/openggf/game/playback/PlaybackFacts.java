package com.openggf.game.playback;

import com.openggf.game.recording.RecordingVersionWarning;
import com.openggf.game.recording.UserRecordingEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            case "s1" -> "Sonic 1";
            case "s2" -> "Sonic 2";
            case "s3k" -> "Sonic 3 & Knuckles";
            case "" -> "Unknown game";
            default -> id;
        };
    }

    /** One-based zone and act, matching how players count them. */
    public static String zoneAct(int zone, int act) { return "Zone " + (zone + 1) + ", act " + (act + 1); }

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
    public static String length(int count, String unit) {
        return duration(count) + ", " + String.format(Locale.ROOT, "%,d", count) + " " + unit;
    }

    public static String date(Instant at) {
        return at == null ? "Unknown" : DATE_TIME.format(at.atZone(ZoneId.systemDefault()));
    }

    /** Short consequence of a build mismatch, or null when the recording matches this build. */
    public static String versionWarning(RecordingVersionWarning warning) {
        return warning == null ? null : switch (warning) {
            case NONE -> null;
            case MISSING_METADATA -> "Recorded build is unknown; replay may drift";
            case OFFICIAL_VERSION_MISMATCH -> "Recorded with another release; may drift";
            case PRERELEASE_BUILD_MISMATCH -> "Recorded with another dev build; may drift";
            case DIRTY_BUILD -> "Recorded with local changes; may drift";
        };
    }

    /** Facts for an engine recording, shared by the library details and the recording's own details page. */
    public static List<Fact> recording(UserRecordingEntry entry) {
        List<Fact> facts = new ArrayList<>();
        var manifest = entry.manifest();
        var launch = manifest == null ? null : manifest.launchContext();
        facts.add(new Fact("Length", length(entry.frameCount(), "BK2 rows")));
        if (launch != null) {
            facts.add(new Fact("Start", gameName(launch.gameId()) + ", " + zoneAct(launch.zone(), launch.act())));
            facts.add(new Fact("Team", team(launch.mainCharacter(), launch.sidekickCharacters())));
        } else facts.add(new Fact("Start", "Unknown", Tone.WARNING));
        facts.add(new Fact("Recorded", date(manifest != null && manifest.createdAt() != null
                ? manifest.createdAt() : entry.modifiedAt())));
        if (manifest != null && manifest.engineIdentity() != null)
            facts.add(new Fact("Build", "OpenGGF " + manifest.engineIdentity().displayVersion()));
        String warning = versionWarning(entry.versionWarning());
        if (warning != null) facts.add(new Fact("Warning", warning, Tone.WARNING));
        facts.add(new Fact("File", entry.path().toString()));
        return List.copyOf(facts);
    }
}
