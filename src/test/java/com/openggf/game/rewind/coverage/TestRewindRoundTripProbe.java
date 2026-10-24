package com.openggf.game.rewind.coverage;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindDeferred;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.objects.SozSpringVineObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical rewind round-trip probe.
 *
 * <p>For each concrete spawnable object it can construct without ROM/OpenGL,
 * this test:
 * <ol>
 *   <li>Constructs the object with a minimal {@code StubObjectServices} fixture.</li>
 *   <li>Captures its rewind state via {@code captureRewindState()}.</li>
 *   <li>Constructs a fresh instance from the same spawn.</li>
 *   <li>Restores captured state onto the fresh instance via {@code restoreRewindState()}.</li>
 *   <li>Reflectively diffs all non-static, non-synthetic scalar/enum/primitive fields
 *       from the object hierarchy (excluding {@code AbstractObjectInstance} base fields
 *       that are managed separately, and schema {@code TRANSIENT} fields).</li>
 * </ol>
 *
 * <p>Any compared field whose value differs after restore is a REAL gap (not just a static
 * flag from the coverage analyzer). Objects that cannot be constructed without ROM
 * are SKIPPED and recorded as "unprobed".
 *
 * <p>The test writes a full report to {@code docs/status/rewind-round-trip-gaps.md}.
 * The test itself only fails if the report write fails; the gap list is
 * informational (not a hard gate).
 *
 * @see RewindRoundTripProbe
 */
@Tag("rewind-probe")
class TestRewindRoundTripProbe {

    @Test
    void probeReturnsNonEmptyResults() {
        RewindRoundTripProbe probe = new RewindRoundTripProbe();
        RewindRoundTripProbe.ProbeReport report = probe.run(Set.of());

        assertNotNull(report, "probe must return a non-null report");
        assertTrue(report.totalClasses() > 0,
                "probe must discover at least some object classes to probe");
        // At least some objects must be probed (even without ROM, simple badniks can be constructed)
        assertTrue(report.probed() > 0,
                "at least some objects must be constructable without ROM; got 0 probed from "
                        + report.totalClasses() + " total");
    }

    @Test
    void probeReportIsWrittenToDisk() throws IOException {
        RewindRoundTripProbe probe = new RewindRoundTripProbe();
        RewindRoundTripProbe.ProbeReport report = probe.run(Set.of());

        Path outDir = Paths.get("docs/status");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("rewind-round-trip-gaps.md");
        probe.writeReport(report, outFile);

        assertTrue(Files.exists(outFile), "docs/status/rewind-round-trip-gaps.md must exist after write");
        String content = Files.readString(outFile);
        assertTrue(content.contains("# Rewind Round-Trip Probe"), "report must have title");
        assertTrue(content.contains("Probed:"), "report must include probed count");
        assertTrue(content.contains("Skipped/Unprobed:"), "report must include unprobed count");
    }

    @Test
    void probeCoverageIsHonestlyReported() {
        RewindRoundTripProbe probe = new RewindRoundTripProbe();
        RewindRoundTripProbe.ProbeReport report = probe.run(Set.of());

        // Skipped count + probed count must equal total
        assertEquals(report.totalClasses(), report.probed() + report.skipped(),
                "probed + skipped must equal total classes discovered");

        // Probe fraction must be reported (0.0-1.0)
        double fraction = report.probedFraction();
        assertTrue(fraction >= 0.0 && fraction <= 1.0,
                "probe fraction must be in [0.0, 1.0], got: " + fraction);

        // We must not silently over-claim: if an object is unprobed, it must have a reason
        for (RewindRoundTripProbe.SkipRecord skip : report.skipRecords()) {
            assertNotNull(skip.reason(), "every skip must have a non-null reason");
            assertFalse(skip.reason().isBlank(), "every skip must have a non-empty reason");
            assertFalse(skip.className().isBlank(), "every skip must name the class");
        }
    }

    @Test
    void realGapRecordsHaveFieldDetails() {
        RewindRoundTripProbe probe = new RewindRoundTripProbe();
        RewindRoundTripProbe.ProbeReport report = probe.run(Set.of());

        for (RewindRoundTripProbe.GapRecord gap : report.realGaps()) {
            assertFalse(gap.className().isBlank(),
                    "gap record must have a class name");
            assertFalse(gap.fieldName().isBlank(),
                    "gap record must identify the differing field");
            // before/after may be null for Object types, but the field name must be present
        }
    }

    @Test
    void comparatorExcludesJavaTransientDelta() throws Exception {
        var original = new ComparatorFixture();
        var restored = new ComparatorFixture();
        original.javaCache = 17;
        restored.javaCache = -1;

        assertEquals(List.of(), diff(original, restored));
    }

    @Test
    void comparatorExcludesAnnotatedTransientDelta() throws Exception {
        var original = new ComparatorFixture();
        var restored = new ComparatorFixture();
        original.annotatedCache = 23;
        restored.annotatedCache = -1;

        assertEquals(List.of(), diff(original, restored));
    }

    @Test
    void comparatorReportsOrdinaryStateLoss() throws Exception {
        var original = new ComparatorFixture();
        original.ordinaryState = 37;
        var restored = new ComparatorFixture();
        // The fixture deliberately loses this captured scalar in its restore hook.
        restored.restoreRewindState(original.captureRewindState());

        assertEquals(List.of(gap(ComparatorFixture.class, "ordinaryState", "37", "0", false)),
                diff(original, restored));
    }

    @Test
    void comparatorReportsFinalConstructorScalars() throws Exception {
        var original = new ComparatorFixture();
        var restored = new ComparatorFixture(2, "after", ConstructorMode.AFTER);

        assertEquals(Set.of(
                        gap(ComparatorFixture.class, "constructorState", "1", "2", true),
                        gap(ComparatorFixture.class, "constructorLabel", "before", "after", true),
                        gap(ComparatorFixture.class, "constructorMode", "BEFORE", "AFTER", true)),
                Set.copyOf(diff(original, restored)));
    }

    @Test
    void comparatorReportsDeferredScalarDelta() throws Exception {
        var original = new ComparatorFixture();
        var restored = new ComparatorFixture();
        original.deferredState = 41;

        assertEquals(List.of(gap(ComparatorFixture.class, "deferredState", "41", "0", false)),
                diff(original, restored));
    }

    @Test
    void comparatorMatchesHiddenFieldPoliciesByDeclaringField() throws Exception {
        var original = new HiddenChildFixture();
        var restored = new HiddenChildFixture();
        // Each name is transient in one declaring class and ordinary in the other.
        ((HiddenParentFixture) original).cache = 11;
        ((HiddenParentFixture) original).state = 13;
        original.cache = 17;
        original.state = 19;

        assertEquals(Set.of(
                        gap(HiddenChildFixture.class, "cache", "17", "0", false),
                        gap(HiddenChildFixture.class, "state", "13", "0", false)),
                Set.copyOf(diff(original, restored)));
    }

    @Test
    void comparatorExcludesVineDerivedCacheInvalidation() throws Exception {
        var original = vine();
        var restored = vine();
        restored.restoreRewindState(original.captureRewindState());
        var cache = SozSpringVineObjectInstance.class.getDeclaredField("generatedPivot");
        cache.setAccessible(true);
        assertEquals(96, cache.getInt(original));
        assertEquals(-1, cache.getInt(restored));
        assertArrayEquals(original.getSlopeData(), restored.getSlopeData());

        assertEquals(List.of(), diff(original, restored));
    }

    @Test
    void comparatorReportsVineAuthoritativeScalarLoss() throws Exception {
        var original = vine();
        var restored = vine();
        restored.restoreRewindState(original.captureRewindState());
        var pivot = SozSpringVineObjectInstance.class.getDeclaredField("pivot");
        pivot.setAccessible(true);
        pivot.setInt(restored, 0); // Deliberate loss alongside legitimate cache invalidation.

        assertEquals(List.of(gap(SozSpringVineObjectInstance.class, "pivot", "96", "0", false)),
                diff(original, restored));
    }

    // Match the existing coverage-test convention: exercise private predicates reflectively.
    @SuppressWarnings("unchecked")
    private static List<RewindRoundTripProbe.GapRecord> diff(
            AbstractObjectInstance original, AbstractObjectInstance restored) throws Exception {
        var method = RewindRoundTripProbe.class.getDeclaredMethod("diffFields", String.class,
                AbstractObjectInstance.class, AbstractObjectInstance.class, Class.class);
        method.setAccessible(true);
        return (List<RewindRoundTripProbe.GapRecord>) method.invoke(new RewindRoundTripProbe(),
                original.getClass().getName(), original, restored, original.getClass());
    }

    private static RewindRoundTripProbe.GapRecord gap(
            Class<?> type, String field, String before, String after, boolean isFinal) {
        return new RewindRoundTripProbe.GapRecord(type.getName(), field, before, after, isFinal);
    }

    private static SozSpringVineObjectInstance vine() {
        var vine = new SozSpringVineObjectInstance(new ObjectSpawn(0x200, 0x300, 0x3F, 0, 0, false, 0));
        vine.setServices(new StubObjectServices());
        return vine;
    }

    private enum ConstructorMode { BEFORE, AFTER }

    private static class ComparatorFixture extends AbstractObjectInstance {
        private transient int javaCache;
        @RewindTransient(reason = "Derived diagnostic fixture cache")
        private int annotatedCache;
        private int ordinaryState;
        @RewindDeferred(reason = "Diagnostic fixture state with a separate owner")
        private int deferredState;
        private final int constructorState;
        private final String constructorLabel;
        private final ConstructorMode constructorMode;

        ComparatorFixture() {
            this(1, "before", ConstructorMode.BEFORE);
        }

        ComparatorFixture(int state, String label, ConstructorMode mode) {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "ComparatorFixture");
            constructorState = state;
            constructorLabel = label;
            constructorMode = mode;
        }

        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }

        @Override protected void afterGenericRewindStateRestored(RewindCaptureContext context) {
            ordinaryState = 0;
        }
    }

    private static class HiddenParentFixture extends ComparatorFixture {
        @RewindTransient(reason = "Inherited diagnostic fixture cache")
        private int cache;
        private int state;
    }

    private static final class HiddenChildFixture extends HiddenParentFixture {
        private int cache;
        private transient int state;
    }
}
