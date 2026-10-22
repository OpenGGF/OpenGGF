package com.openggf.tools;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpritePresentation.*;
import com.openggf.level.PatternDesc;

import java.lang.management.ManagementFactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bounded synthetic preparation CPU/allocation comparison, originating in the
 * 2026-10-22 sprite-Tile reuse task. Inputs: fork ID, warmup frames, measured
 * frames per arm and paired rounds. Precompile with the engine runtime classpath;
 * run this class only through tools/performance/run_isolated.py in a dedicated
 * worktree. See the task audit for exact commands, source pins and limitations.
 * No ROM, rendering, whole-engine timing, assertions about FPS or JMH equivalence.
 */
public final class SpriteTileReuseProbe {
    private static final HeadSatReplay HEAD_REPLAY = (graphics, entries, emitter) -> { };
    private static final HeadMask MASK = mask();
    private static final java.util.function.Function<Object, Attributes> DECODER = descriptor -> {
        PatternDesc desc = (PatternDesc) descriptor;
        return new Attributes(desc.getPaletteIndex(), desc.getHFlip(), desc.getVFlip(), desc.getPriority());
    };
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final MethodHandle HINT_PREPARE = hintPrepare();
    private static volatile Frame escaped;
    private static volatile long consumed;

    /** Optional historical experiment: never add a production signature just to run this probe. */
    private static MethodHandle hintPrepare() {
        try {
            return MethodHandles.publicLookup().findStatic(SpritePresentation.class, "prepare",
                    MethodType.methodType(Frame.class, GraphicsManager.class, int.class, int.class,
                            Runnable.class, java.util.function.Function.class, Frame.class));
        } catch (NoSuchMethodException absent) { return null; }
        catch (IllegalAccessException failure) { throw new ExceptionInInitializerError(failure); }
    }

    private enum Scene {
        WORLD_IDLE(false, false, false, false), WORLD_SCROLL(true, false, false, false),
        SUBJECT_IDLE(false, true, false, false), SUBJECT_SCROLL(true, true, false, false),
        HEAD_EQUAL_MAP(false, true, true, false), HEAD_REBUILT_MASK(false, true, true, true),
        HEAD_SCROLL(true, true, true, true);
        final boolean scroll, subjects, head, rebuildMask;
        Scene(boolean scroll, boolean subjects, boolean head, boolean rebuildMask) {
            this.scroll = scroll; this.subjects = subjects; this.head = head; this.rebuildMask = rebuildMask;
        }
    }

    private static HeadMask mask() {
        return new HeadMask(0, 4, Set.of(0), List.of(new MaskPoint(0, 0),
                new MaskPoint(64, 0), new MaskPoint(64, 4), new MaskPoint(0, 4)));
    }

    private static final class Source implements Runnable {
        final GraphicsManager graphics;
        final Scene scene;
        final PatternDesc descriptor = new PatternDesc();
        final List<Map<Integer, PatternVersion>> art = new java.util.ArrayList<>();
        int frame;
        Source(GraphicsManager graphics, Scene scene) {
            this.graphics = graphics; this.scene = scene;
            for (int actor = 0; actor < 16; actor++) {
                Map<Integer, PatternVersion> patterns = new HashMap<>();
                for (int tile = 0; tile < 8; tile++)
                    patterns.put(1000 + actor * 8 + tile, new PatternVersion(tile, actor, 3, 4));
                art.add(Map.copyOf(patterns));
            }
        }
        Frame prepare(int frame, Frame previous) {
            this.frame = frame;
            if (HINT_PREPARE == null) return SpritePresentation.prepare(graphics, cameraX(), 200, this, DECODER);
            try {
                // Pre-resolved and identical in both arms; later reruns include this fixed invocation cost.
                return (Frame) HINT_PREPARE.invokeExact(graphics, cameraX(), 200, (Runnable) this, DECODER, previous);
            } catch (RuntimeException | Error failure) { throw failure; }
            catch (Throwable failure) { throw new IllegalStateException("Historical preparation failed", failure); }
        }
        private int cameraX() { return 100 + (scene.scroll ? frame : 0); }
        @Override public void run() {
            SpritePresentation.layer(graphics, Layer.OBJECT);
            for (int actor = 0; actor < 16; actor++) {
                final int index = actor;
                Subject subject = Subject.WORLD;
                if (scene.subjects) {
                    HeadTransform head = scene.head ? new HeadTransform(scene.rebuildMask ? mask() : MASK,
                            0, 100 + actor * 64, 224, false, false, 150, 30000 + actor * 256,
                            new HashMap<>(art.get(actor)), HEAD_REPLAY) : null;
                    subject = new Subject(new String("actor-" + actor), Part.OBJECT_BODY, false, 100,
                            head, new Body(Role.BADNIK, 100 + actor * 64, 224));
                }
                Subject selected = subject;
                SpritePresentation.withSubject(graphics, selected, () -> {
                    if (scene.head) SpritePresentation.bindPatternVersions(graphics, art.get(index));
                    for (int tile = 0; tile < 8; tile++) graphics.renderPatternWithId(1000 + index * 8 + tile,
                            descriptor, 100 + index * 64 + tile * 8, 224);
                });
            }
            SpritePresentation.layer(graphics, Layer.HUD);
            for (int i = 0; i < 32; i++)
                graphics.renderPatternWithId(2000 + i, descriptor, cameraX() + 8 + i * 8, 208);
        }
    }

    /** Volatile publication makes every frame and tile escape; sample consumption observes input movement. */
    private static long consume(Frame frame) {
        escaped = frame;
        Tile a = frame.tiles().getFirst(), b = frame.tiles().get(127), c = frame.tiles().getLast();
        return 31L * a.patternId() + Float.floatToIntBits(a.x()) + Float.floatToIntBits(b.x())
                + Float.floatToIntBits(c.x()) + frame.tiles().size() + frame.patternVersions().size();
    }

    private static long loop(Source source, boolean hint, int frames) {
        Frame previous = source.prepare(0, null);
        long checksum = 0;
        for (int i = 1; i <= frames; i++) {
            Frame next = source.prepare(i, hint ? previous : null);
            checksum += consume(next);
            previous = next;
        }
        consumed = checksum;
        return checksum;
    }

    private static void verify(Source source) {
        Frame previous = source.prepare(0, null);
        for (int i = 1; i <= 16; i++) {
            Frame fresh = source.prepare(i, null), hinted = source.prepare(i, previous);
            if (!fresh.equals(hinted)) throw new AssertionError("Frame mismatch: " + source.scene + "/" + i);
            int reused = 0;
            for (int j = 0; j < 160; j++) if (previous.tiles().get(j) == hinted.tiles().get(j)) reused++;
            int expected = HINT_PREPARE == null ? 0 : source.scene.rebuildMask || source.scene.scroll ? 32 : 160;
            if (reused != expected) throw new AssertionError("Reuse mismatch: " + source.scene + ": " + reused);
            previous = hinted;
        }
        long fresh = loop(source, false, 32), hinted = loop(source, true, 32);
        if (fresh != hinted) throw new AssertionError("Consumed output mismatch: " + source.scene);
    }

    private record Sample(long cpu, long bytes, long wall, long checksum, long gcCount, long gcMillis) { }
    private static Sample sample(Source source, boolean hint, int frames) {
        long threadId = Thread.currentThread().threadId();
        long gcCount = gcCount(), gcMillis = gcMillis();
        long cpu = THREADS.getCurrentThreadCpuTime(), bytes = THREADS.getThreadAllocatedBytes(threadId);
        long wall = System.nanoTime();
        long checksum = loop(source, hint, frames);
        wall = System.nanoTime() - wall;
        bytes = THREADS.getThreadAllocatedBytes(threadId) - bytes;
        cpu = THREADS.getCurrentThreadCpuTime() - cpu;
        return new Sample(cpu, bytes, wall, checksum, gcCount() - gcCount, gcMillis() - gcMillis);
    }
    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionCount())).sum();
    }
    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum();
    }
    private static void print(int fork, int pair, Scene scene, boolean hint, boolean first, int frames, Sample sample) {
        System.out.printf(Locale.ROOT,
                "{\"fork\":%d,\"pair\":%d,\"scene\":\"%s\",\"hint\":%s,\"first\":%s,\"frames\":%d,"
                        + "\"cpu_ns\":%d,\"allocated_bytes\":%d,\"wall_ns\":%d,\"checksum\":%d,\"gc_count\":%d,\"gc_ms\":%d}%n",
                fork, pair, scene, hint, first, frames, sample.cpu(), sample.bytes(), sample.wall(),
                sample.checksum(), sample.gcCount(), sample.gcMillis());
    }

    public static void main(String[] args) {
        boolean verifyOnly = args.length == 1 && args[0].equals("--verify-only");
        if (!verifyOnly && args.length != 4)
            throw new IllegalArgumentException("Use --verify-only or: fork warmup-frames measured-frames pairs");
        int fork = verifyOnly ? 0 : Integer.parseInt(args[0]), warmup = verifyOnly ? 4096 : Integer.parseInt(args[1]);
        int frames = verifyOnly ? 4096 : Integer.parseInt(args[2]), pairs = verifyOnly ? 4 : Integer.parseInt(args[3]);
        if (warmup < 4096 || warmup > 65536 || frames < 4096 || frames > 65536 || pairs < 4 || pairs > 12)
            throw new IllegalArgumentException("bounded inputs: 4096..65536 warmup/measurement frames; 4..12 pairs");
        if (!verifyOnly && HINT_PREPARE == null)
            throw new IllegalStateException("Rejected reuse hint is absent; use --verify-only, or the pinned historical experiment");
        if (!THREADS.isThreadAllocatedMemorySupported() || !THREADS.isCurrentThreadCpuTimeSupported())
            throw new IllegalStateException("CPU/allocation counters unavailable");
        THREADS.setThreadAllocatedMemoryEnabled(true); THREADS.setThreadCpuTimeEnabled(true);
        GraphicsManager.destroyForReinit();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GraphicsManager graphics = GraphicsManager.getInstance(); graphics.initHeadless();
        try {
            Source[] sources = java.util.Arrays.stream(Scene.values()).map(scene -> new Source(graphics, scene)).toArray(Source[]::new);
            for (Source source : sources) verify(source);
            if (verifyOnly) {
                System.out.println("Verified seven scenes: full-frame equality, expected reuse and consumed output; no timing.");
                return;
            }
            // Warm both arms of every source before observing any timed samples.
            for (int round = 0; round < 3; round++) for (Source source : sources) {
                loop(source, (round & 1) == 0, warmup);
                loop(source, (round & 1) != 0, warmup);
            }
            for (int pair = 0; pair < pairs; pair++) for (int offset = 0; offset < sources.length; offset++) {
                Source source = sources[(offset + pair + fork) % sources.length];
                boolean hintFirst = ((pair + source.scene.ordinal() + fork) & 1) == 0;
                Sample first = sample(source, hintFirst, frames), second = sample(source, !hintFirst, frames);
                if (first.checksum() != second.checksum()) throw new AssertionError("paired output mismatch");
                print(fork, pair, source.scene, hintFirst, true, frames, first);
                print(fork, pair, source.scene, !hintFirst, false, frames, second);
            }
            if (escaped == null || consumed == 0) throw new AssertionError("outputs were not consumed");
        } finally { SessionManager.clear(); GraphicsManager.destroyForReinit(); }
    }
}
