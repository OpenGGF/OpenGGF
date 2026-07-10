package com.openggf.tools.net;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openggf.game.GameServices;
import com.openggf.game.timeattack.TimeAttackTrackCatalog;
import com.openggf.level.Level;
import com.openggf.net.hub.TrackValidationProfile;
import com.openggf.tools.HeadlessGameBoot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exports pure numeric validation bounds from user-supplied ROM-backed levels. */
public final class TrackProfileExportTool {
    private static final Map<String, String> ROM_PROPERTIES = Map.of(
            "s1", "s1.rom.path", "s2", "s2.rom.path", "s3k", "s3k.rom.path");

    private TrackProfileExportTool() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(argument(args, "--output",
                "src/main/resources/net/track-validation-profiles.json"));
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (String gameId : List.of("s1", "s2", "s3k")) {
            Path rom = Path.of(System.getProperty(ROM_PROPERTIES.get(gameId), gameId + ".gen"));
            if (!Files.isRegularFile(rom)) {
                System.err.println("Skipping " + gameId + ": ROM not found at " + rom);
                continue;
            }
            List<TimeAttackTrackCatalog.Track> tracks =
                    TimeAttackTrackCatalog.tracksFor(gameId);
            if (tracks.isEmpty()) {
                continue;
            }
            try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
                TimeAttackTrackCatalog.Track first = tracks.getFirst();
                boot.boot(rom, first.zone(), first.act());
                addCurrent(profiles, first);
                for (int i = 1; i < tracks.size(); i++) {
                    TimeAttackTrackCatalog.Track track = tracks.get(i);
                    GameServices.level().loadZoneAndAct(track.zone(), track.act());
                    addCurrent(profiles, track);
                }
            }
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("profiles", profiles);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(output.toFile(), root);
        System.out.println("Wrote " + profiles.size() + " profiles to " + output);
    }

    private static void addCurrent(List<Map<String, Object>> profiles,
                                   TimeAttackTrackCatalog.Track track) {
        Level level = GameServices.level().getCurrentLevel();
        int blockSize = level.getBlockPixelSize();
        int width = Math.multiplyExact(level.getLayerWidthBlocks(0), blockSize);
        int height = Math.multiplyExact(level.getLayerHeightBlocks(0), blockSize);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", track.gameId() + ":" + track.zone() + ":" + track.act());
        entry.put("levelWidthPx", width);
        entry.put("levelHeightPx", height);
        entry.put("maxSpeedPxPerFrame",
                TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME);
        entry.put("maxFramesPerSecond", 60);
        profiles.add(entry);
    }

    private static String argument(String[] args, String name, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }
}
