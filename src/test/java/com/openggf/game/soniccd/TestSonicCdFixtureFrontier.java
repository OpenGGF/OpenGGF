package com.openggf.game.soniccd;

import com.openggf.trace.FieldComparison;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceFrame;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The work queue for the object and zone lanes: replays every stage's native fixture
 * ({@code <soniccd.native.root>/rehost-fixtures/<payload>/}, or
 * {@code -Dsoniccd.fixtures.root}) through {@link CdFixtureReplay} to its first diverging
 * row and writes a frontier table. It asserts no divergence: each stage's frontier is a
 * finding, attributed to a suspected cause from the trace's own aux events. The R11A
 * K2 assertion stays strict in {@link TestSonicCdR11aTraceReplay}.
 *
 * <p>{@code -Dsoniccd.fixture.payloads=R11A,R11B} limits the run;
 * {@code -Dsoniccd.frontier.out=<file>} writes the Markdown table.
 * {@code -Dsoniccd.frontier.continue=true} replays every row past the first divergence and
 * appends the second-divergence view (does a one-off first divergence re-converge?).
 */
class TestSonicCdFixtureFrontier {
    @TempDir
    Path tempDir;

    /** The causes the table distinguishes. */
    enum Cause { NONE, UNHOSTED_OBJECT, ZONE_PLAYER_HOOK, WATER, UNKNOWN, HARNESS }

    record Frontier(CdStagePayload payload, int rows, int compared, int firstError, String fields,
                    Cause cause, String evidence) {
        String markdown() {
            String first = firstError < 0 ? "green" : Integer.toString(firstError);
            return "| " + payload + " | " + compared + " / " + rows + " | " + first + " | " + fields + " | "
                    + label(cause) + " | " + evidence + " |";
        }
    }

    @Test
    void writesTheFrontierOfEveryStageFixture() throws Exception {
        Path cue = CdTestMedia.usaCue();
        String configured = System.getProperty("soniccd.fixtures.root");
        Path root = configured != null && !configured.isBlank() ? Path.of(configured)
                : CdTestMedia.nativeFile("rehost-fixtures/README.md").getParent();
        Assumptions.assumeTrue(Files.isDirectory(root), "No rehost fixtures at " + root);
        List<CdStagePayload> payloads = selected();
        List<Frontier> table = new ArrayList<>();
        boolean continuing = Boolean.getBoolean("soniccd.frontier.continue");
        List<String> second = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            for (CdStagePayload payload : payloads) {
                var fixture = CdFixtureReplay.Fixture.under(root, payload);
                if (!fixture.present()) continue;
                Path config = Files.createDirectories(tempDir.resolve(payload.name()));
                Frontier frontier;
                try {
                    var outcome = CdFixtureReplay.replay(disc, config, fixture, Integer.MAX_VALUE, true, -1, -1);
                    frontier = attribute(outcome);
                    if (continuing && outcome.firstErrorRow() >= 0) {
                        // A separate pass, so an engine failure after the first divergence
                        // cannot replace the frontier above.
                        try {
                            second.add(secondDivergence(CdFixtureReplay.replay(disc,
                                    Files.createDirectories(tempDir.resolve(payload.name() + "-continued")), fixture,
                                    Integer.MAX_VALUE, false, -1, -1)));
                        } catch (Exception | AssertionError e) {
                            second.add("| " + payload + " | replay failed after the first divergence: "
                                    + e.getClass().getSimpleName() + " | | | | |");
                        }
                    }
                    for (CdFixtureReplay.Row row : outcome.rows()) {
                        System.out.println("[scd-frontier-context] " + payload + " " + row.format());
                    }
                } catch (Exception | AssertionError e) {
                    frontier = new Frontier(payload, -1, 0, 0, "-", Cause.HARNESS,
                            e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
                }
                System.out.println("[scd-frontier] " + frontier.markdown());
                table.add(frontier);
            }
        }
        assertTrue(!table.isEmpty(), "No fixtures under " + root);
        String markdown = render(table);
        if (continuing) {
            markdown += "\n| Payload | Rows compared | Rows in error | Re-converges at | Second divergence row | Fields |\n"
                    + "|---|---|---|---|---|---|\n" + String.join("\n", second) + "\n";
        }
        System.out.println(markdown);
        String out = System.getProperty("soniccd.frontier.out");
        if (out != null && !out.isBlank()) Files.writeString(Path.of(out), markdown);
        List<Frontier> harness = table.stream().filter(f -> f.cause() == Cause.HARNESS).toList();
        assertEquals(List.of(), harness, "Every fixture should replay to a comparison");
    }

    private static List<CdStagePayload> selected() {
        String list = System.getProperty("soniccd.fixture.payloads", "");
        if (list.isBlank()) return Arrays.asList(CdStagePayload.values());
        return Arrays.stream(list.split(",")).map(String::trim).map(CdStagePayload::valueOf).toList();
    }

    /**
     * The suspected cause, from what the native trace itself shows on the frontier row
     * and the three rows before it. Comparison-only: these values are read after the
     * replay and never reach the engine.
     */
    static Frontier attribute(CdFixtureReplay.Outcome o) {
        int rows = o.trace().frameCount();
        if (o.firstErrorRow() < 0) {
            return new Frontier(o.payload(), rows, o.rowsCompared(), -1, "-", Cause.NONE, "");
        }
        int r = o.firstErrorRow();
        String fields = o.fields().stream().map(FieldComparison::fieldName).distinct()
                .collect(Collectors.joining(", "));
        String detail = o.fields().stream().limit(3)
                .map(f -> f.fieldName() + " " + f.expected() + "/" + f.actual()).collect(Collectors.joining("; "));
        List<String> evidence = new ArrayList<>();
        evidence.add(detail + " (native/engine)");
        Cause cause = Cause.UNKNOWN;

        boolean underwater = false;
        boolean onObject = false;
        int routine = -1;
        for (int i = Math.max(0, r - 3); i <= r; i++) {
            TraceFrame f = o.trace().getFrame(i);
            underwater |= (f.statusByte() & 0x40) != 0;              // oFlags bit 6: underwater
            onObject |= (f.statusByte() & 0x08) != 0 || f.standOnObj() > 0;
            routine = f.routine();
        }
        Set<String> near = new TreeSet<>();
        Set<String> appeared = new LinkedHashSet<>();
        for (TraceEvent e : o.trace().getEventsInRange(Math.max(0, r - 3), r)) {
            if (e instanceof TraceEvent.ObjectNear n && touching(o.trace().getFrame(e.frame()), n)) {
                near.add(n.objectType());
            }
            if (e instanceof TraceEvent.ObjectAppeared a) appeared.add(a.objectType());
            if (e instanceof TraceEvent.StateSnapshot s && Boolean.TRUE.equals(s.fields().get("underwater"))) {
                underwater = true;
            }
        }
        boolean cameraOnly = !o.fields().isEmpty()
                && o.fields().stream().allMatch(f -> f.fieldName().startsWith("camera"));
        if (cameraOnly) {
            evidence.add("camera fields only; player state equal");
        } else if (underwater) {
            cause = Cause.WATER;
            evidence.add("native underwater bit set");
        } else if (onObject || routine == 4 || routine == 6 || !near.isEmpty()) {
            cause = Cause.UNHOSTED_OBJECT;
            if (onObject) evidence.add("native standing on an object");
            if (routine == 4 || routine == 6) evidence.add("native routine " + routine);
            if (!near.isEmpty()) evidence.add("objects near: " + String.join(",", near));
        } else if (o.payload().stage().zone() == CdZone.WACKY_WORKBENCH) {
            cause = Cause.ZONE_PLAYER_HOOK;
            evidence.add("Wacky Workbench has its own player routine");
        }
        CdFixtureReplay.Row last = o.row(r);
        if (last != null && cause == Cause.UNKNOWN) evidence.add(last.format());
        return new Frontier(o.payload(), rows, o.rowsCompared(), r, fields, cause, String.join("; ", evidence));
    }

    /**
     * Within {@value #CONTACT_RANGE}px of the native player on both axes: wider than any
     * hosted touch box plus Sonic's radii, so a contact the ROM made is not missed.
     */
    private static boolean touching(TraceFrame player, TraceEvent.ObjectNear object) {
        return Math.abs((short) (object.x() - player.x())) < CONTACT_RANGE
                && Math.abs((short) (object.y() - player.y())) < CONTACT_RANGE;
    }

    private static final int CONTACT_RANGE = 0x40;

    static String secondDivergence(CdFixtureReplay.Outcome o) {
        String fields = o.secondFields().stream().limit(3)
                .map(f -> f.fieldName() + " " + f.expected() + "/" + f.actual()).collect(Collectors.joining("; "));
        return "| " + o.payload() + " | " + o.rowsCompared() + " | " + o.errorRows() + " | "
                + (o.reconvergeRow() < 0 ? "-" : Integer.toString(o.reconvergeRow())) + " | "
                + (o.secondErrorRow() < 0 ? "-" : Integer.toString(o.secondErrorRow())) + " | "
                + (fields.isEmpty() ? "-" : fields) + " |";
    }

    static String label(Cause cause) {
        return switch (cause) {
            case NONE -> "-";
            case UNHOSTED_OBJECT -> "unhosted object";
            case ZONE_PLAYER_HOOK -> "zone player hook";
            case WATER -> "water";
            case UNKNOWN -> "unknown";
            case HARNESS -> "harness error";
        };
    }

    static String render(List<Frontier> table) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Payload | Rows compared / trace rows | First divergence row | Fields | Suspected cause | Evidence |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (Frontier f : table) sb.append(f.markdown()).append('\n');
        return sb.toString();
    }
}
