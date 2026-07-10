package com.openggf.tools.net;

import com.openggf.net.hub.RoomHost;
import com.openggf.net.hub.HostRoundEngine;
import com.openggf.net.hub.RoomHostConfig;
import com.openggf.net.hub.RoomHostHooks;
import com.openggf.net.hub.TrackValidationProfile;
import com.openggf.net.hub.TrackValidationProfileSource;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ControlMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Deterministic in-JVM hub CPU scale gate; no ROM or graphics runtime required. */
public final class GhostLoadTestTool {
    public enum Behavior {
        NORMAL, TELEPORT, PACING_SLOW, OVERSIZED, FLOOD,
        HANDSHAKE_ABANDON, ADVERSARIAL_MIX
    }

    public record LoadReport(double meanTickMillis, double p99TickMillis,
                             long maxQueuedBytesAnyClient,
                             long healthyClientsFinished,
                             long adversariesSanctioned) {
    }

    private GhostLoadTestTool() {
    }

    public static LoadReport run(int n, Behavior mix, Duration duration, Path dataDir)
            throws Exception {
        if (n < 1 || n > 256 || duration == null || duration.isNegative()
                || duration.isZero()) {
            throw new IllegalArgumentException("invalid load-test arguments");
        }
        Files.createDirectories(dataDir);
        long[] now = {1_000_000};
        String fingerprint = "load-test:deterministic";
        PlayerIdentity host = PlayerIdentity.loadOrCreate(dataDir.resolve("host"));
        RoomHost room = new RoomHost(new RoomHostConfig("Load", "s3k", 0, 0,
                "OPEN", null, n, fingerprint), host, () -> now[0],
                TrackValidationProfileSource.none(),
                new RoomHostHooks(true, null, null, null, null));
        room.applyTrackValidationProfile(new TrackValidationProfile(
                100_000, 10_000, 32, 60));

        List<BotClient> bots = new ArrayList<>(n);
        for (int index = 0; index < n; index++) {
            Behavior behavior = behaviorFor(mix, index, n);
            bots.add(new BotClient(room, behavior,
                    dataDir.resolve("bot-" + index), fingerprint));
        }
        room.requestStartRound(new ControlMessage.RoundConfig(
                "s3k", 0, 0, Math.max(60, (int) duration.toSeconds() + 10),
                "OPEN", null));
        now[0] += HostRoundEngine.COUNTDOWN_MILLIS;
        room.tick();

        int ticks = Math.max(1, (int) Math.ceil(duration.toMillis() / 50.0)) + 2;
        long[] tickNanos = new long[ticks];
        for (int tick = 0; tick < ticks; tick++) {
            for (BotClient bot : bots) {
                bot.publishTick();
            }
            now[0] += 50;
            long started = System.nanoTime();
            room.tick();
            tickNanos[tick] = System.nanoTime() - started;
        }
        for (BotClient bot : bots) {
            bot.finish();
        }
        room.tick();

        long caught = bots.stream().filter(BotClient::adversaryCaught).count();
        long healthyFinished = room.round().standings().size();
        long maxQueued = bots.stream().mapToLong(BotClient::maxObservedQueuedBytes)
                .max().orElse(0);
        for (BotClient bot : bots) {
            bot.disconnect();
        }
        Arrays.sort(tickNanos);
        double mean = Arrays.stream(tickNanos).average().orElse(0) / 1_000_000.0;
        int p99Index = Math.min(tickNanos.length - 1,
                (int) Math.ceil(tickNanos.length * 0.99) - 1);
        double p99 = tickNanos[p99Index] / 1_000_000.0;
        return new LoadReport(mean, p99, maxQueued, healthyFinished, caught);
    }

    private static Behavior behaviorFor(Behavior mix, int index, int n) {
        if (mix != Behavior.ADVERSARIAL_MIX) {
            return mix;
        }
        Behavior[] adversaries = {Behavior.TELEPORT, Behavior.PACING_SLOW,
                Behavior.OVERSIZED, Behavior.FLOOD, Behavior.HANDSHAKE_ABANDON};
        int firstAdversary = Math.max(0, n - adversaries.length);
        return index < firstAdversary ? Behavior.NORMAL : adversaries[index - firstAdversary];
    }

    public static void main(String[] args) throws Exception {
        int n = intArg(args, "--n", 32);
        int seconds = intArg(args, "--duration", 5);
        String mix = stringArg(args, "--mix", "normal");
        Behavior behavior = mix.equalsIgnoreCase("adversarial")
                ? Behavior.ADVERSARIAL_MIX : Behavior.NORMAL;
        System.out.println("In-JVM hub CPU load gate (socket throughput is not measured)");
        System.out.println(run(n, behavior, Duration.ofSeconds(seconds),
                Path.of("target", "ghost-load-test")));
    }

    private static int intArg(String[] args, String name, int fallback) {
        return Integer.parseInt(stringArg(args, name, Integer.toString(fallback)));
    }

    private static String stringArg(String[] args, String name, String fallback) {
        for (int index = 0; index + 1 < args.length; index++) {
            if (args[index].toLowerCase(Locale.ROOT).equals(name)) {
                return args[index + 1];
            }
        }
        return fallback;
    }
}
