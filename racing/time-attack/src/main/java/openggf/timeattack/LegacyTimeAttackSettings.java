package openggf.timeattack;

import com.openggf.mods.ModStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Imports the old engine's Time Attack settings without changing its configuration files. */
final class LegacyTimeAttackSettings {
    private static final int MAX_CONFIG_BYTES = 1024 * 1024;
    private static final int MAX_BACKUPS = 1000;

    private LegacyTimeAttackSettings() { }

    static void importOnce(ModStorage storage, Path directory) throws IOException {
        if (storage == null || storage.list().contains(TimeAttackSettings.FILE_NAME)) return;
        TimeAttackSettings settings = new TimeAttackSettings();
        for (String name : List.of("config.json", "config.yaml")) {
            // The engine preserves original files before converting/replacing unknown settings.
            List<Path> sources = backups(directory, name);
            sources.add(directory.resolve(name));
            for (Path source : sources) {
                if (!Files.isRegularFile(source)) continue;
                byte[] bytes;
                try (var input = Files.newInputStream(source)) {
                    bytes = input.readNBytes(MAX_CONFIG_BYTES + 1);
                    if (bytes.length > MAX_CONFIG_BYTES) throw new IOException("Legacy configuration exceeds 1 MiB");
                }
                ObjectMapper mapper = name.endsWith(".json") ? new ObjectMapper() : new YAMLMapper();
                Object parsed = mapper.readValue(bytes, Object.class);
                Map<String, String> values = new LinkedHashMap<>();
                flatten("", parsed, values, 0);
                apply(settings, values);
            }
        }
        if (!settings.save(storage)) throw new IOException("Could not save imported Time Attack settings");
    }

    private static List<Path> backups(Path directory, String name) throws IOException {
        List<Path> sources = new ArrayList<>();
        if (!Files.isDirectory(directory)) return sources;
        try (var entries = Files.newDirectoryStream(directory, name + ".bak*")) {
            for (Path entry : entries) {
                String suffix = entry.getFileName().toString().substring(name.length() + 4);
                if (!suffix.isEmpty() && !suffix.matches("\\.[0-9]+")) continue;
                if (sources.size() >= MAX_BACKUPS) throw new IOException("Too many legacy configuration backups");
                sources.add(entry);
            }
        }
        sources.sort(Comparator.comparingLong(path -> backupNumber(path.getFileName().toString(), name)));
        return sources;
    }

    private static long backupNumber(String file, String name) {
        String suffix = file.substring(name.length() + 4);
        if (suffix.isEmpty()) return 0;
        try { return Long.parseLong(suffix.substring(1)); }
        catch (NumberFormatException invalid) { return Long.MAX_VALUE; }
    }

    private static void flatten(String prefix, Object value, Map<String, String> output, int depth) throws IOException {
        if (depth > 32) throw new IOException("Legacy configuration is nested too deeply");
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) continue;
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                // Ignore unrelated configuration subtrees entirely.
                if (path.equals("timeAttack") || path.startsWith("timeAttack.") || path.startsWith("TIME_ATTACK_"))
                    flatten(path, entry.getValue(), output, depth + 1);
            }
        } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            output.put(prefix, String.valueOf(value));
        }
    }

    static void apply(TimeAttackSettings settings, Map<String, String> values) {
        for (var entry : values.entrySet()) {
            String value = entry.getValue();
            try {
                switch (entry.getKey()) {
                    case "timeAttack.retryKey", "TIME_ATTACK_RETRY_KEY" -> settings.setRetryKey(RetryKey.parse(value));
                    case "timeAttack.net.hostPort", "TIME_ATTACK_NET_HOST_PORT" -> settings.setHostPort(Integer.parseInt(value));
                    case "timeAttack.net.lastJoinAddress", "TIME_ATTACK_NET_LAST_JOIN_ADDRESS" -> settings.setLastJoinAddress(value);
                    case "timeAttack.net.displayName", "TIME_ATTACK_NET_DISPLAY_NAME" -> settings.setDisplayName(value);
                    case "timeAttack.net.masterUrl", "TIME_ATTACK_NET_MASTER_URL" -> settings.setMasterUrl(value);
                    case "timeAttack.net.masterTrustInsecure", "TIME_ATTACK_NET_MASTER_TRUST_INSECURE" -> {
                        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))
                            settings.setMasterTrustInsecure(Boolean.parseBoolean(value));
                    }
                    case "timeAttack.hud.minimap", "TIME_ATTACK_HUD_MINIMAP" -> {
                        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))
                            settings.setMinimap(Boolean.parseBoolean(value));
                    }
                    default -> { }
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed old value must not prevent importing other valid settings.
            }
        }
    }

}
