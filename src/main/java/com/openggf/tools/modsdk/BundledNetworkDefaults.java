package com.openggf.tools.modsdk;

import com.openggf.net.client.MasterDiscoveryCheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Build step that writes this build's multiplayer defaults into the bundled-mod generated-resource
 * overlay ({@link BundledModPackager}). The multiplayer channel (the POM's
 * {@code openggf.multiplayer.channel}: CI passes the branch, local builds default to
 * {@code develop}) selects a host from the single catalogue
 * {@code racing/deploy/master-channels.properties}; an unknown channel fails the build. The
 * optional {@code openggf.multiplayer.defaultMaster} override replaces the host for private
 * builds and is syntax-checked with the engine's master-address parser, without network I/O.
 *
 * <p>The overlay directory is recreated and {@value #RESOURCE} is always written, so a value from
 * an earlier build never survives. Players type and see only the host; its port is discovered
 * from DNS SRV at connect time.
 *
 * <p>Usage: {@code BundledNetworkDefaults --catalogue=<file> --channel=<name>
 * --default-master=<address or empty> --output=<overlay-directory>}.
 *
 * <p>Origin: hostname-only multiplayer endpoints (2026-11-01).
 */
public final class BundledNetworkDefaults {
    public static final String RESOURCE = "network-defaults.properties";
    public static final String DEFAULT_MASTER_KEY = "multiplayer.defaultMaster";
    public static final String CHANNEL_KEY = "multiplayer.channel";

    /** One catalogue line: the host players type and the port its SRV record publishes. */
    public record Channel(String host, int port) { }

    private BundledNetworkDefaults() { }

    public static void main(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            int equals = arg.indexOf('=');
            if (!arg.startsWith("--") || equals < 0 || options.put(arg.substring(2, equals), arg.substring(equals + 1)) != null) {
                usage();
            }
        }
        if (!options.keySet().equals(java.util.Set.of("catalogue", "channel", "default-master", "output"))) usage();
        try {
            String master = defaultMaster(readCatalogue(Path.of(options.get("catalogue"))),
                    options.get("channel"), options.get("default-master"));
            write(Path.of(options.get("output")), options.get("channel").strip(), master);
            System.out.println("Bundled multiplayer default (" + options.get("channel").strip() + "): " + master);
        } catch (IOException | IllegalArgumentException failure) {
            System.err.println("Bundled multiplayer defaults failed: " + failure.getMessage());
            System.exit(1);
        }
    }

    private static void usage() {
        System.err.println("Usage: BundledNetworkDefaults --catalogue=<file> --channel=<name> "
                + "--default-master=<address or empty> --output=<directory>");
        System.exit(2);
    }

    /**
     * Strict {@code channel=host:port} lines; blank lines and {@code #} comments are ignored.
     * Hosts must be bare multi-label DNS names; duplicate channels are rejected.
     */
    public static Map<String, Channel> readCatalogue(Path file) throws IOException {
        Map<String, Channel> channels = new LinkedHashMap<>();
        int number = 0;
        for (String raw : Files.readString(file, StandardCharsets.UTF_8).split("\n", -1)) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int equals = line.indexOf('=');
            int colon = line.lastIndexOf(':');
            String name = equals < 0 ? "" : line.substring(0, equals).strip();
            if (!name.matches("[a-z][a-z0-9-]*") || colon < equals) {
                throw new IllegalArgumentException(file + ":" + number + ": expected channel=host:port");
            }
            String host = line.substring(equals + 1, colon).strip();
            int port;
            try {
                port = Integer.parseInt(line.substring(colon + 1).strip());
            } catch (NumberFormatException malformed) {
                throw new IllegalArgumentException(file + ":" + number + ": invalid port");
            }
            if (port < 1 || port > 65_535) throw new IllegalArgumentException(file + ":" + number + ": invalid port");
            String normalized;
            try {
                normalized = MasterDiscoveryCheck.requireHostName(host);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(file + ":" + number + ": invalid host " + host);
            }
            if (!normalized.equals(host)) {
                throw new IllegalArgumentException(file + ":" + number + ": write the host in canonical form: " + normalized);
            }
            if (channels.put(name, new Channel(host, port)) != null) {
                throw new IllegalArgumentException(file + ":" + number + ": duplicate channel " + name);
            }
        }
        if (channels.isEmpty()) throw new IllegalArgumentException(file + ": no channels");
        return Map.copyOf(channels);
    }

    /** The build's default master: the override when given, otherwise the channel's host. */
    public static String defaultMaster(Map<String, Channel> catalogue, String channel, String override) {
        String name = Objects.requireNonNullElse(channel, "").strip();
        Channel selected = catalogue.get(name);
        if (selected == null) {
            throw new IllegalArgumentException("Unknown multiplayer channel '" + name + "'; expected one of "
                    + new java.util.TreeSet<>(catalogue.keySet()));
        }
        String custom = Objects.requireNonNullElse(override, "").strip();
        if (custom.isEmpty()) return selected.host();
        try {
            return MasterDiscoveryCheck.requireAddress(custom);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid openggf.multiplayer.defaultMaster: " + custom);
        }
    }

    /** Recreates {@code overlay} holding only {@value #RESOURCE}. */
    public static void write(Path overlay, String channel, String defaultMaster) throws IOException {
        if (Files.exists(overlay, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> walk = Files.walk(overlay)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        Files.createDirectories(overlay);
        Files.writeString(overlay.resolve(RESOURCE), "# Generated by BundledNetworkDefaults; do not commit.\n"
                + CHANNEL_KEY + "=" + channel + "\n"
                + DEFAULT_MASTER_KEY + "=" + defaultMaster + "\n", StandardCharsets.UTF_8);
    }
}
