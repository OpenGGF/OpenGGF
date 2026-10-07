package com.openggf.tools;

import com.sun.management.ThreadMXBean;
import java.io.BufferedWriter;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;

/** Isolated diagnostic for the 2026-10-07 test-throughput task. Inputs: JSONL,
 * GC mode, repetitions, sample interval and explicit JUnit selectors. Never on
 * the ordinary test classpath. Global peaks include the profiler and workers;
 * allocation counts include only the thread executing each test callback. */
public final class MemoryProfileLauncher implements TestExecutionListener, AutoCloseable {
    private final BufferedWriter output;
    private final String gcMode;
    private final Map<String, Window> active = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "test-memory-sampler"); t.setDaemon(true); return t;
    });
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threads;
    private TestPlan plan;
    private int run, executed, skipped, failures, aborted, containerFailures;

    private record Sample(long heap, long direct, long mapped, Long rss, long gcCount, long gcMillis) {}
    private record Floor(long heap, boolean observed) {}
    private static final class Window {
        final Map<String, Object> identity;
        final Sample before;
        final long start = System.nanoTime(), thread = Thread.currentThread().threadId();
        final long allocated;
        final boolean gcObserved;
        long peakHeap, peakDirect, peakMapped;
        Long peakRss;
        Window(Map<String, Object> identity, Sample before, long allocated, boolean gcObserved) {
            this.identity = identity; this.before = before; this.allocated = allocated;
            this.gcObserved = gcObserved; observe(before);
        }
        synchronized void observe(Sample s) {
            peakHeap = Math.max(peakHeap, s.heap); peakDirect = Math.max(peakDirect, s.direct);
            peakMapped = Math.max(peakMapped, s.mapped);
            if (s.rss != null) peakRss = peakRss == null ? s.rss : Math.max(peakRss, s.rss);
        }
    }

    private MemoryProfileLauncher(Path path, String gcMode, int interval) throws Exception {
        output = Files.newBufferedWriter(path);
        this.gcMode = gcMode;
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        threads = bean instanceof ThreadMXBean extended && extended.isThreadAllocatedMemorySupported()
                ? extended : null;
        if (threads != null && !threads.isThreadAllocatedMemoryEnabled()) threads.setThreadAllocatedMemoryEnabled(true);
        sampler.scheduleWithFixedDelay(() -> {
            if (!active.isEmpty()) { Sample s = sample(); active.values().forEach(w -> w.observe(s)); }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private Sample sample() {
        long direct = 0, mapped = 0, count = 0, millis = 0;
        for (BufferPoolMXBean b : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (b.getName().equals("direct")) direct += b.getMemoryUsed();
            if (b.getName().equals("mapped")) mapped += b.getMemoryUsed();
        }
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, g.getCollectionCount()); millis += Math.max(0, g.getCollectionTime());
        }
        Long rss = null;
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("VmRSS:")) { rss = Long.parseLong(line.split("\\s+")[1]) * 1024; break; }
            }
        } catch (Exception ignored) { /* RSS unavailable on other platforms. */ }
        return new Sample(memory.getHeapMemoryUsage().getUsed(), direct, mapped, rss, count, millis);
    }

    private Floor collect() {
        long count = sample().gcCount;
        memory.gc();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        Sample s;
        do {
            s = sample();
            if (s.gcCount > count) return new Floor(s.heap, true);
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        } while (System.nanoTime() < deadline);
        return new Floor(s.heap, false);
    }

    private long allocation(long thread) { return threads == null ? -1 : threads.getThreadAllocatedBytes(thread); }

    private Map<String, Object> identity(TestIdentifier id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("run", run); row.put("id", id.getUniqueId()); row.put("displayName", id.getDisplayName());
        TestIdentifier cursor = id;
        while (cursor != null) {
            var source = cursor.getSource().orElse(null);
            if (source instanceof MethodSource m) {
                row.put("className", m.getClassName()); row.put("methodName", m.getMethodName()); break;
            }
            if (source instanceof ClassSource c) { row.put("className", c.getClassName()); break; }
            cursor = plan.getParent(cursor).orElse(null);
        }
        return row;
    }

    private boolean classContainer(TestIdentifier id) {
        return !id.isTest() && id.getSource().orElse(null) instanceof ClassSource;
    }

    @Override public void testPlanExecutionStarted(TestPlan plan) { this.plan = plan; }

    @Override public void executionStarted(TestIdentifier id) {
        if (!id.isTest() && !classContainer(id)) return;
        boolean gc = gcMode.equals("test") && id.isTest() || gcMode.equals("class") && classContainer(id);
        Floor floor = gc ? collect() : null;
        Sample before = sample();
        active.put(id.getUniqueId(), new Window(identity(id), before,
                allocation(Thread.currentThread().threadId()), floor != null && floor.observed));
    }

    @Override public void executionSkipped(TestIdentifier id, String reason) {
        if (id.isTest()) skipped++;
        Map<String, Object> row = identity(id);
        row.put("type", "skipped"); row.put("scope", id.isTest() ? "test" : "container"); row.put("reason", reason);
        emit(row);
    }

    @Override public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (result.getStatus() == TestExecutionResult.Status.FAILED) {
            if (id.isTest()) failures++; else containerFailures++;
        }
        if (id.isTest()) {
            executed++;
            if (result.getStatus() == TestExecutionResult.Status.ABORTED) aborted++;
        }
        Window w = active.remove(id.getUniqueId());
        if (w == null) return;
        Sample after = sample(); w.observe(after);
        long allocated = allocation(w.thread);
        boolean gc = gcMode.equals("test") && id.isTest() || gcMode.equals("class") && classContainer(id);
        Floor floor = gc ? collect() : null;
        Map<String, Object> row = w.identity;
        row.put("type", id.isTest() ? "test" : "class"); row.put("status", result.getStatus().name());
        row.put("elapsedSeconds", (System.nanoTime() - w.start) / 1e9);
        row.put("threadAllocatedBytes", allocated >= 0 && w.allocated >= 0 ? allocated - w.allocated : null);
        row.put("peakHeapBytes", w.peakHeap); row.put("peakDirectBytes", w.peakDirect);
        row.put("peakMappedBytes", w.peakMapped); row.put("peakRssBytes", w.peakRss);
        row.put("heapDeltaBytes", after.heap - w.before.heap);
        row.put("postGcHeapDeltaBytes", floor != null && floor.observed && w.gcObserved ? floor.heap - w.before.heap : null);
        row.put("gcCount", after.gcCount - w.before.gcCount); row.put("gcMillis", after.gcMillis - w.before.gcMillis);
        result.getThrowable().ifPresent(t -> row.put("failure", t.toString()));
        emit(row);
        if (classContainer(id)) System.out.println("Memory profile pass " + run + ": " + id.getDisplayName() + " " + result.getStatus());
    }

    private synchronized void emit(Map<String, Object> row) {
        try { output.write(json(row)); output.newLine(); output.flush(); }
        catch (Exception e) { throw new IllegalStateException("Cannot write memory observations", e); }
    }
    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            map.forEach((k, v) -> j.add(json(k.toString()) + ":" + json(v))); return j.toString();
        }
        StringBuilder s = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '"' -> s.append("\\\""); case '\\' -> s.append("\\\\");
                case '\n' -> s.append("\\n"); case '\r' -> s.append("\\r"); case '\t' -> s.append("\\t");
                default -> { if (c < 32) s.append(String.format("\\u%04x", (int)c)); else s.append(c); }
            }
        }
        return s.append('"').toString();
    }
    @Override public void close() throws Exception { sampler.shutdownNow(); output.close(); }

    public static void main(String[] args) throws Exception {
        if (args.length < 5 || !Set.of("none", "class", "test").contains(args[1]))
            throw new IllegalArgumentException("JSONL gc-mode repetitions interval-ms explicit-class[#method]...");
        int repeats = Integer.parseInt(args[2]), interval = Integer.parseInt(args[3]);
        if (repeats < 1 || interval < 10) throw new IllegalArgumentException("Positive repeats, interval >= 10ms required");
        var builder = LauncherDiscoveryRequestBuilder.request()
                .filters(EngineFilter.includeEngines("junit-jupiter"))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false");
        for (int i = 4; i < args.length; i++) {
            builder.selectors(args[i].contains("#") ? DiscoverySelectors.selectMethod(args[i]) : DiscoverySelectors.selectClass(args[i]));
        }
        int code;
        try (var listener = new MemoryProfileLauncher(Path.of(args[0]), args[1], interval)) {
            Launcher launcher = LauncherFactory.create();
            launcher.registerTestExecutionListeners(listener);
            for (listener.run = 1; listener.run <= repeats; listener.run++) {
                Floor before = listener.collect();
                launcher.execute(builder.build());
                // Execute has returned: class teardown and JUnit contexts have unwound.
                listener.plan = null;
                Floor after = listener.collect();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", "run"); row.put("run", listener.run);
                row.put("beforeHeapBytes", before.heap); row.put("afterHeapBytes", after.heap);
                row.put("gcObserved", before.observed && after.observed); listener.emit(row);
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("type", "finished"); summary.put("runs", repeats); summary.put("executed", listener.executed);
            summary.put("skipped", listener.skipped); summary.put("aborted", listener.aborted);
            summary.put("failures", listener.failures); summary.put("containerFailures", listener.containerFailures);
            summary.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
            summary.put("javaVersion", System.getProperty("java.version"));
            listener.emit(summary);
            code = listener.executed == 0 || listener.failures + listener.containerFailures > 0 ? 1 : 0;
        }
        if (code != 0) System.exit(code);
    }
}
