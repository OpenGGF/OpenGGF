package openggf.racing.server.tools;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/** Bounded process and latency observations; inputs are owned JVMs in the 2026-10 scale task. */
final class RacingScaleMetrics {
    private RacingScaleMetrics() { }

    record Latency(long samples, int retainedSamples, double p50Millis, double p95Millis,
                   double p99Millis, double maxMillis) { }

    static final class Latencies {
        private final long[] retained = new long[16384];
        private final SplittableRandom random = new SplittableRandom(0x5343414c45L);
        private long count;
        private long max;

        synchronized void add(long nanos) {
            nanos = Math.max(0, nanos);
            max = Math.max(max, nanos);
            long ordinal = ++count;
            if (ordinal <= retained.length) retained[(int) ordinal - 1] = nanos;
            else {
                long selected = random.nextLong(ordinal);
                if (selected < retained.length) retained[(int) selected] = nanos;
            }
        }

        synchronized Latency report() {
            long[] sorted = Arrays.copyOf(retained, (int) Math.min(count, retained.length));
            Arrays.sort(sorted);
            return new Latency(count, sorted.length, percentile(sorted, .50),
                    percentile(sorted, .95), percentile(sorted, .99), max / 1e6);
        }

        private static double percentile(long[] sorted, double quantile) {
            return sorted.length == 0 ? 0
                    : sorted[Math.max(0, (int) Math.ceil(sorted.length * quantile) - 1)] / 1e6;
        }
    }

    record ProcessReport(long pid, long startEpochMillis, long endEpochMillis,
                         double elapsedSeconds, double processCpuSeconds,
                         long gcCollections, long gcMillis, long rssAtStartBytes,
                         long rssAtEndBytes, long peakSampledRssBytes,
                         long lifetimePeakRssBytes, long peakSampledUsedHeapBytes,
                         int rssSamples, int availableProcessors) { }

    private record Snapshot(long cpuNanos, long gcCollections, long gcMillis, long rss,
                            long highWaterRss, long heap) {
        static Snapshot read() {
            long count = 0, millis = 0;
            for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                count += Math.max(0, bean.getCollectionCount());
                millis += Math.max(0, bean.getCollectionTime());
            }
            long rss = -1, highWater = -1;
            try {
                for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                    if (line.startsWith("VmRSS:")) rss = kibibytes(line);
                    if (line.startsWith("VmHWM:")) highWater = kibibytes(line);
                }
            } catch (java.io.IOException ignored) {
                // Unsupported /proc remains explicitly unknown, never a zero measurement.
            }
            long cpu = ProcessHandle.current().info().totalCpuDuration()
                    .map(java.time.Duration::toNanos).orElse(-1L);
            return new Snapshot(cpu, count, millis, rss, highWater,
                    Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        }

        private static long kibibytes(String line) {
            return Long.parseLong(line.substring(line.indexOf(':') + 1).trim().split("\\s+")[0]) * 1024L;
        }
    }

    static final class ProcessWindow implements AutoCloseable {
        private final long startEpoch = System.currentTimeMillis();
        private final long startNanos = System.nanoTime();
        private final Snapshot before = Snapshot.read();
        private final Thread sampler;
        private volatile boolean stopped;
        private long peakRss = -1, peakHeap;
        private int samples;
        private ProcessReport report;

        ProcessWindow() {
            sample(before);
            sampler = Thread.ofPlatform().daemon().name("racing-scale-process-sampler").start(() -> {
                while (!stopped) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(50);
                    } catch (InterruptedException interrupted) {
                        return;
                    }
                    if (!stopped) sample(Snapshot.read());
                }
            });
        }

        private synchronized void sample(Snapshot snapshot) {
            if (snapshot.rss() >= 0) {
                peakRss = Math.max(peakRss, snapshot.rss());
                samples++;
            }
            peakHeap = Math.max(peakHeap, snapshot.heap());
        }

        synchronized ProcessReport report() {
            if (report == null) throw new IllegalStateException("process window is still running");
            return report;
        }

        @Override
        public void close() throws InterruptedException {
            if (report != null) return;
            stopped = true;
            sampler.interrupt();
            sampler.join();
            Snapshot after = Snapshot.read();
            sample(after);
            report = new ProcessReport(ProcessHandle.current().pid(), startEpoch,
                    System.currentTimeMillis(), (System.nanoTime() - startNanos) / 1e9,
                    before.cpuNanos() < 0 || after.cpuNanos() < 0 ? -1
                            : Math.max(0, after.cpuNanos() - before.cpuNanos()) / 1e9,
                    after.gcCollections() - before.gcCollections(), after.gcMillis() - before.gcMillis(),
                    before.rss(), after.rss(), peakRss, after.highWaterRss(), peakHeap, samples,
                    Runtime.getRuntime().availableProcessors());
        }
    }
}
