package com.openggf.tools;

import com.openggf.game.GameModule;
import com.openggf.route.LaunchContract;
import com.openggf.route.ReplayWorld;
import com.openggf.route.RomIdentity;
import com.openggf.route.RouteHostException;
import com.openggf.route.RouteWorld;
import com.openggf.route.RouteWorldFactory;
import com.openggf.version.AppVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * The production gameplay host factory: each world is a {@code GameplayCaptureSession} booted
 * from a launch contract, stepping {@code GameLoop.step()} with the row as recorded input.
 * ROMs come from a local resolver and must match the launch's hash before boot. One world at
 * a time per process: sessions are process-global. This is the trusted composition point that
 * joins the route core to the capture session; the core itself never reaches the gameplay runtime.
 */
public final class EngineRouteHosts implements RouteWorldFactory {
    /** Bumped when host stepping, observation or checkpoint semantics change. */
    static final String HOST_REVISION = "engine-host/1";

    private final Map<String, Path> roms;
    private final Map<Path, RomIdentity> verified = new HashMap<>();
    private final UnaryOperator<GameModule> moduleDecorator;

    private EngineRouteHosts(Map<String, Path> roms, UnaryOperator<GameModule> moduleDecorator) {
        this.roms = Map.copyOf(roms);
        this.moduleDecorator = moduleDecorator;
    }

    /** Hosts resolving ROMs by game id ({@code s1}, {@code s2}, {@code s3k}). */
    public static EngineRouteHosts withRoms(Map<String, Path> roms) {
        return new EngineRouteHosts(roms, null);
    }

    /** Hosts resolving ROMs from the test properties ({@code sonic1.rom.path}, ...); absent games are unavailable. */
    public static EngineRouteHosts fromSystemProperties() {
        var roms = new HashMap<String, Path>();
        put(roms, "s1", System.getProperty("sonic1.rom.path"));
        put(roms, "s2", System.getProperty("sonic2.rom.path"));
        put(roms, "s3k", System.getProperty("s3k.rom.path"));
        return withRoms(roms);
    }

    private static void put(Map<String, Path> roms, String game, String path) {
        if (path != null && !path.isBlank() && Files.isRegularFile(Path.of(path))) roms.put(game, Path.of(path));
    }

    /** Engine-only view of a route playback, for domain assertions; see {@code RoutePlayback#view}. */
    public interface EngineView {
        GameplayCaptureSession session();
    }

    /** A test seam: decorate the launched module (for example with a hostile creator controller). */
    EngineRouteHosts withModuleDecorator(UnaryOperator<GameModule> decorator) {
        return new EngineRouteHosts(roms, Objects.requireNonNull(decorator, "decorator"));
    }

    /** Whether a ROM matching {@code identity} is available. */
    public boolean has(RomIdentity identity) {
        Path path = roms.get(identity.game());
        return path != null && verify(identity.game(), path).matches(identity);
    }

    @Override
    public RouteWorld openSearch(LaunchContract launch) {
        return open(launch);
    }

    @Override
    public ReplayWorld openColdReplay(LaunchContract launch) {
        return open(launch);
    }

    @Override
    public String environment() {
        return "openggf " + AppVersion.get() + " " + HOST_REVISION;
    }

    private EngineRouteWorld open(LaunchContract launch) {
        Path rom = resolve(launch.rom());
        Path donorRom = null;
        if (!launch.donor().equals("off")) {
            donorRom = roms.get(launch.donor());
            if (donorRom == null) throw new RouteHostException(RouteHostException.Reason.ROM_UNAVAILABLE,
                    "no donor ROM for " + launch.donor());
        }
        return EngineRouteWorld.boot(launch, rom, donorRom, moduleDecorator);
    }

    private Path resolve(RomIdentity identity) {
        Path path = roms.get(identity.game());
        if (path == null) throw new RouteHostException(RouteHostException.Reason.ROM_UNAVAILABLE,
                "no ROM configured for " + identity.game());
        RomIdentity actual = verify(identity.game(), path);
        if (!actual.matches(identity)) throw new RouteHostException(RouteHostException.Reason.ROM_UNAVAILABLE,
                path + " is " + actual.crc32Hex() + "/" + actual.sha1() + ", launch wants "
                        + identity.crc32Hex() + "/" + identity.sha1());
        return path;
    }

    private synchronized RomIdentity verify(String game, Path path) {
        return verified.computeIfAbsent(path, p -> {
            try {
                return RomIdentity.ofFile(game, p);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
    }
}
