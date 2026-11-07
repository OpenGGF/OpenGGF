package openggf.racing.server.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openggf.game.GameServices;
import openggf.timeattack.TimeAttackTrackCatalog;
import com.openggf.level.Level;
import com.openggf.net.hub.TrackValidationProfile;
import com.openggf.tools.HeadlessGameBoot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exports pure numeric validation bounds for every eligible Time Attack act from user-supplied ROM-backed levels. */
public final class TrackProfileExportTool {
    private static final Map<String, String> ROM_PROPERTIES = Map.of(
            "s1", "sonic1.rom.path", "s2", "sonic2.rom.path", "s3k", "s3k.rom.path");

    private TrackProfileExportTool() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(argument(args, "--output",
                "racing/server/src/main/resources/net/track-validation-profiles.json"));
        if (java.util.Arrays.asList(args).contains("--arenas-only")) {
            exportArenas(Path.of(System.getProperty("s3k.rom.path","s3k.gen")),output);
            return;
        }
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (String gameId : List.of("s1", "s2", "s3k")) {
            Path rom = Path.of(System.getProperty(ROM_PROPERTIES.get(gameId), System.getProperty(gameId+".rom.path",gameId+".gen")));
            if (!Files.isRegularFile(rom)) {
                System.err.println("Skipping " + gameId + ": ROM not found at " + rom);
                continue;
            }
            List<TimeAttackTrackCatalog.Track> tracks =
                    TimeAttackTrackCatalog.eligibleFor(gameId);
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
        Path arenas = Path.of(System.getProperty("s3k.rom.path","s3k.gen"));
        if(Files.isRegularFile(arenas)) exportArenas(arenas,output);
        int total=new ObjectMapper().readTree(output.toFile()).path("profiles").size();
        System.out.println("Wrote " + total + " profiles to " + output);
    }

    /** Export arena bounds and S2 source caps, preserving existing act bounds. */
    public static void exportArenas(Path rom, Path output) throws Exception {
        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(output.toFile());
        var entries = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("profiles");
        for (int i=entries.size()-1;i>=0;i--)
            if (entries.get(i).path("key").asText().contains(":arena:")) entries.remove(i);
        try (HeadlessGameBoot boot = new HeadlessGameBoot(320,224)) {
            boot.boot(rom,0,0);
            var engine = com.openggf.game.session.EngineServices.current();
            var module = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getWorldSession().getGameModule();
            var provider = module.getGameService(com.openggf.game.arena.HostedArenaProvider.class);
            var context = new com.openggf.game.nativemenu.NativeMenuHostContext(engine,module,
                    new com.openggf.control.InputHandler(),ignored -> {});
            for (int arena=0;arena<5;arena++) {
                try (var session = provider.open(new com.openggf.game.arena.HostedArenaRequest(arena,"sonic",true),context)) {
                    var geometry = session.geometry();
                    var level = GameServices.level().getCurrentLevel();
                    var entry = entries.addObject();
                    entry.put("key","s3k:arena:"+arena);
                    entry.put("levelWidthPx",level.getLayerWidthBlocks(0)*level.getBlockPixelSize());
                    entry.put("levelHeightPx",level.getLayerHeightBlocks(0)*level.getBlockPixelSize());
                    entry.put("maxSpeedPxPerFrame",32); entry.put("maxFramesPerSecond",60);
                    entry.put("wrapWidthPx",geometry.wrapWidthPx()); entry.put("wrapHeightPx",geometry.wrapHeightPx());
                    entry.put("lapCount",geometry.lapCount());
                    long wheels = level.getObjects().stream().filter(spawn -> spawn.objectId()==0x64).count();
                    entry.put("maxSwapSources",wheels*(geometry.lapCount()+1));
                }
            }
        }
        Path s2 = Path.of(System.getProperty("sonic2.rom.path","s2.gen"));
        if (Files.isRegularFile(s2)) {
            try (HeadlessGameBoot boot = new HeadlessGameBoot(320,224)) {
                var tracks = TimeAttackTrackCatalog.eligibleFor("s2");
                boot.boot(s2,tracks.getFirst().zone(),tracks.getFirst().act());
                for (var track:tracks) {
                    GameServices.level().loadZoneAndAct(track.zone(),track.act());
                    long monitors=GameServices.level().getCurrentLevel().getObjects().stream()
                            .filter(spawn -> spawn.objectId()==0x26).count();
                    for(var node:entries) if(node.path("key").asText().equals("s2:"+track.zone()+":"+track.act()))
                        ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("maxSwapSources",monitors);
                }
            }
        }
        json.writeValue(output.toFile(),root);
    }

    private static void addCurrent(List<Map<String, Object>> profiles,
                                   TimeAttackTrackCatalog.Track track) {
        Level level = GameServices.level().getCurrentLevel();
        int blockSize = level.getBlockPixelSize();
        int width = Math.multiplyExact(level.getLayerWidthBlocks(0), blockSize);
        int height = Math.multiplyExact(level.getLayerHeightBlocks(0), blockSize);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", track.key());
        entry.put("levelWidthPx", width);
        entry.put("levelHeightPx", height);
        entry.put("maxSpeedPxPerFrame",
                TrackValidationProfile.GLOBAL_SPEED_CEILING_PX_PER_FRAME);
        entry.put("maxFramesPerSecond", 60);
        entry.put("maxSwapSources", "s2".equals(track.gameId())
                ? level.getObjects().stream().filter(spawn -> spawn.objectId()==0x26).count() : 0);
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
