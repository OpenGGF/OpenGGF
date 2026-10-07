package com.openggf.tools.challenge;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;

/** GPU/PCM isolation oracle. Inputs are ROMs plus a held-pad file; originating task: 2026-10-07 prototype. */
public final class ChallengeProbe {
    private ChallengeProbe() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException(
                    "Usage: ChallengeProbe <s1-ROM> <s2-ROM> <s3k-ROM> <common.pad> <outside-repo output>");
        Path output = Path.of(args[4]).toAbsolutePath();
        Files.createDirectories(output);
        ChallengeInputProgram program = ChallengeInputProgram.read(Path.of(args[3]));
        List<ChallengeHost.Member> members = List.of(new ChallengeHost.Member("s1", "s1", Path.of(args[0])),
                new ChallengeHost.Member("s2", "s2", Path.of(args[1])),
                new ChallengeHost.Member("s3k", "s3k", Path.of(args[2])));
        List<List<String>> solo = new ArrayList<>();
        for (int i = 0; i < 3; i++)
            solo.add(run(List.of(members.get(i)), program, 100 + i, output.resolve("solo-" + i), null));
        var twoWorlds = runAll(members.subList(0, 2), program, 150, output.resolve("two-worlds"));
        for (int i = 0; i < 2; i++)
            if (!solo.get(i).equals(twoWorlds.get(i)))
                throw new AssertionError("Two-world production media mismatch");
        List<List<String>> actual = runAll(members, program, 200, output.resolve("triple"));
        for (int i = 0; i < 3; i++)
            if (!solo.get(i).equals(actual.get(i)))
                throw new AssertionError("Solo/shared media mismatch for " + members.get(i).game()
                        + " first=" + first(solo.get(i), actual.get(i)));
        var duplicates = runAll(List.of(new ChallengeHost.Member("copy-a", "s1", members.getFirst().rom()),
                                        new ChallengeHost.Member("copy-b", "s1", members.getFirst().rom())),
                program, 300, output.resolve("duplicate"));
        if (!duplicates.get(0).equals(duplicates.get(1)) || !duplicates.get(0).equals(solo.getFirst()))
            throw new AssertionError("Duplicate game isolation mismatch");
        var reversed = runAll(List.of(members.get(2), members.get(1), members.get(0)), program, 400,
                output.resolve("reversed"));
        for (int i = 0; i < 3; i++)
            if (!solo.get(i).equals(reversed.get(2 - i)))
                throw new AssertionError("Member order changed media");
        isolation(members.getFirst(), program);
        Files.writeString(output.resolve("result.txt"),
                "PASS: " + program.length()
                        + (" common ticks; solo/shared RGBA+pre-focus PCM+native state agree for s1/s2/s3k; "
                                + "duplicate S1 and reversed member order agree; sibling close/reload/crash "
                                + "leaves survivor media unchanged; all managed processes stopped.\n"));
        System.out.println(Files.readString(output.resolve("result.txt")));
    }
    private static List<String> run(List<ChallengeHost.Member> members, ChallengeInputProgram p, long gen,
            Path out, List<String> expected) throws Exception {
        return runAll(members, p, gen, out).getFirst();
    }
    private static List<List<String>> runAll(List<ChallengeHost.Member> members, ChallengeInputProgram p,
            long gen, Path output) throws Exception {
        Files.createDirectories(output);
        List<List<String>> hashes = new ArrayList<>();
        for (var ignored : members) hashes.add(new ArrayList<>());
        List<Long> pids;
        long boot = System.nanoTime();
        long[] costs = new long[p.length()];
        long maxRss = 0;
        try (var capture = new ChallengeCapture(output); var host = new ChallengeHost(members, gen)) {
            host.prepare();
            pids = host.pids();
            host.start();
            long bootMs = (System.nanoTime() - boot) / 1_000_000;
            for (int t = 0; t < p.length(); t++) {
                long start = System.nanoTime();
                var frames = host.step(p.heldAt(t));
                costs[t] = System.nanoTime() - start;
                for (int i = 0; i < frames.size(); i++) hashes.get(i).add(hash(frames.get(i)));
                capture.frame(frames, p.heldAt(t), frames.getFirst().pcm());
                if (t == 179 || t == 599 || t == p.length() - 1)
                    for (int i = 0; i < frames.size(); i++)
                        ChallengeCapture.saveFrame(
                                output.resolve("member-" + i + "-tick-" + (t + 1) + ".png"), frames.get(i));
                if (t % 60 == 0)
                    maxRss = Math.max(maxRss, rss(pids));
            }
            Arrays.sort(costs);
            Files.writeString(output.resolve("budget.txt"),
                    String.format(Locale.ROOT,
                            "boot_ms=%d ticks=%d p50_ms=%.3f p95_ms=%.3f p99_ms=%.3f peak_worker_rss_kib=%d "
                                    + "bytes_rgba_per_tuple=%d queue_depth_per_member=1%n",
                            bootMs, p.length(), costs[costs.length / 2] / 1e6,
                            costs[(int) (costs.length * .95)] / 1e6,
                            costs[Math.min(costs.length - 1, (int) (costs.length * .99))] / 1e6, maxRss,
                            members.size() * ChallengeProtocol.RGBA_BYTES));
        }
        for (long pid : pids)
            if (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                throw new AssertionError("Leaked worker " + pid);
        return hashes;
    }
    private static void isolation(ChallengeHost.Member member, ChallengeInputProgram p) throws Exception {
        try (var survivor = new ProcessGameEndpoint(member.game(), member.rom(), 500);
                var oracle = new ProcessGameEndpoint(member.game(), member.rom(), 501)) {
            await(survivor.prepare());
            await(oracle.prepare());
            await(survivor.start());
            await(oracle.start());
            ProcessGameEndpoint sibling = null;
            try {
                for (int t = 0; t < 180; t++) {
                    if (t == 20 || t == 80) {
                        sibling = new ProcessGameEndpoint(member.game(), member.rom(), 600 + t);
                        await(sibling.prepare());
                        await(sibling.start());
                    }
                    if (t == 50) {
                        sibling.close();
                        sibling = null;
                    }
                    if (t == 110) {
                        sibling.crashForDiagnostic();
                        try {
                            await(sibling.step(0));
                            throw new AssertionError("Killed worker accepted a tick");
                        } catch (IOException expected) {
                        }
                        sibling.close();
                        sibling = null;
                    }
                    var a = survivor.step(p.heldAt(t));
                    var b = oracle.step(p.heldAt(t));
                    if (!hash(await(a)).equals(hash(await(b))))
                        throw new AssertionError("Sibling lifecycle changed survivor at " + t);
                }
            } finally {
                if (sibling != null)
                    sibling.close();
            }
        }
        // Stale generation rejection is before media publication, independent of worker implementation.
        var stale = new ChallengeProtocol.Frame(
                1, 0, new byte[ChallengeProtocol.RGBA_BYTES], new short[0], "LEVEL", 0, 0, 0, 0, 0, 0);
        try {
            ProcessGameEndpoint.requireIdentity(stale, 2, 0);
            throw new AssertionError("Stale generation accepted");
        } catch (IOException expected) {
        }
    }
    private static ChallengeProtocol.Frame await(
            java.util.concurrent.CompletableFuture<ChallengeProtocol.Frame> future) throws IOException {
        return ProcessGameEndpoint.await(future, Duration.ofSeconds(45));
    }
    private static String hash(ChallengeProtocol.Frame f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        d.update(f.rgba());
        for (short s : f.pcm()) {
            d.update((byte) s);
            d.update((byte) (s >>> 8));
        }
        d.update((f.mode() + ":" + f.x() + ":" + f.y() + ":" + f.rings() + ":" + f.vInt() + ":"
                + f.levelFrame() + ":" + f.polledMask())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(d.digest());
    }
    private static int first(List<String> a, List<String> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++)
            if (!a.get(i).equals(b.get(i)))
                return i + 1;
        return Math.min(a.size(), b.size()) + 1;
    }
    private static long rss(List<Long> pids) {
        long total = 0;
        for (long pid : pids) try {
                for (String line : Files.readAllLines(Path.of("/proc", Long.toString(pid), "status")))
                    if (line.startsWith("VmRSS:"))
                        total += Long.parseLong(line.replaceAll("[^0-9]", ""));
            } catch (IOException ignored) {
            }
        return total;
    }
}
