package com.openggf.route;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** The manifest is the launch authority and binds its rows by count and hash. */
class TestRouteFiles {
    private static final Provenance PROVENANCE = new Provenance("test", "engine-x", "unit", List.of("note"));

    private static RouteArtifact sample() {
        var launch = new LaunchContract(RomIdentity.standard("s3k"), new Inlet.ColdLevel(new LevelId("s3k", 1, 0), false),
                new Roster("knuckles", ""), 424, "off",
                new InitialState(17, new Seeds(0x300, 0x500, true, 25, "0123012", false, false, null)));
        var tape = InputTape.of(List.of(PadRow.of(PlayerSlot.P1, PhysicalButton.RIGHT),
                PadRow.of(PlayerSlot.P1, PhysicalButton.RIGHT, PhysicalButton.C), PadRow.NEUTRAL,
                new PadRow(0, PhysicalButton.START.bit())));
        return RouteArtifact.of(launch, tape, PROVENANCE);
    }

    private static ReplayEvidence evidence(RouteArtifact route) {
        return new ReplayEvidence(ReplayScope.ENGINE, route.content(), "test-env", route.tape().size(), List.of("rows"));
    }

    @Test
    void writtenRoutesReadBackWithTheirLaunchRowsAndStamp(@TempDir Path dir) throws Exception {
        RouteArtifact route = sample();
        var written = RouteFiles.writeNew(dir.resolve("aiz-route"), route, evidence(route));
        assertEquals(dir.resolve("aiz-route.route.json"), written.manifest());
        RouteArtifact read = RouteFiles.read(written.manifest());
        assertEquals(route.launch(), read.launch());
        assertEquals(route.tape(), read.tape());
        assertEquals(route.content(), read.content());
        assertEquals(PROVENANCE, read.manifest().provenance());
        assertEquals(1, read.manifest().qualification().size());
        assertTrue(read.manifest().qualification().getFirst().passed());
        assertEquals(route.content(), read.manifest().qualification().getFirst().content());
    }

    @Test
    void contentIdentityCoversLaunchAndRowsOnly() {
        RouteArtifact route = sample();
        var facts = new NativeFacts(Optional.empty(),
                List.of(new NativeFacts.LagFact(2, "harness", route.content())));
        var annotated = new RouteArtifact(route.manifest().withNativeFacts(facts)
                .withProvenance(new Provenance("other", "", "", List.of()))
                .withQualification(List.of(evidence(route).stamp())), route.tape());
        assertEquals(route.content(), annotated.content(), "stamps, provenance and native facts are not content");
        var otherClock = RouteArtifact.of(route.launch().withInitial(new InitialState(18, route.launch().initial().seeds())),
                route.tape(), PROVENANCE);
        assertNotEquals(route.content(), otherClock.content(), "the initial V_int is launch identity");
        var otherRows = RouteArtifact.of(route.launch(), route.tape().slice(0, 3), PROVENANCE);
        assertNotEquals(route.content(), otherRows.content());
    }

    @Test
    void writingNeedsMatchingEngineEvidenceAndNeverReplaces(@TempDir Path dir) throws Exception {
        RouteArtifact route = sample();
        RouteArtifact other = RouteArtifact.of(route.launch(), route.tape().slice(0, 2), PROVENANCE);
        assertThrows(IllegalArgumentException.class, () -> RouteFiles.writeNew(dir.resolve("a"), route, evidence(other)));
        assertThrows(IllegalArgumentException.class, () -> RouteFiles.writeNew(dir.resolve("a"), route,
                new ReplayEvidence(ReplayScope.BIZHAWK, route.content(), "native", 4, List.of())));
        assertThrows(IllegalArgumentException.class, () -> RouteFiles.writeNew(dir.resolve("a"), route, null));
        RouteFiles.writeNew(dir.resolve("a"), route, evidence(route));
        assertThrows(IOException.class, () -> RouteFiles.writeNew(dir.resolve("a"), route, evidence(route)));
        var replaced = RouteFiles.replace(dir.resolve("a"), other, evidence(other));
        assertEquals(2, RouteFiles.read(replaced.manifest()).tape().size());
    }

    @Test
    void readingRejectsTamperedRowsUnknownKeysAndMissingClocks(@TempDir Path dir) throws Exception {
        RouteArtifact route = sample();
        var written = RouteFiles.writeNew(dir.resolve("r"), route, evidence(route));
        String manifest = Files.readString(written.manifest());
        String script = Files.readString(written.script());

        Files.writeString(written.script(), script + "1 R\n");
        assertTrue(assertThrows(IOException.class, () -> RouteFiles.read(written.manifest())).getMessage()
                .contains("compiles to 5 rows"));
        Files.writeString(written.script(), script.replace("1 R+C", "1 R+B"));
        assertThrows(IOException.class, () -> RouteFiles.read(written.manifest()), "same count, different rows");
        Files.writeString(written.script(), script);
        assertDoesNotThrow(() -> RouteFiles.read(written.manifest()));

        Files.writeString(written.manifest(), manifest.replace("\"width\"", "\"zone\" : 3, \"width\""));
        assertTrue(assertThrows(IOException.class, () -> RouteFiles.read(written.manifest())).getMessage()
                .contains("unknown launch key"));
        Files.writeString(written.manifest(), manifest.replace("\"vIntStart\" : 17", "\"unused\" : 17"));
        assertThrows(IOException.class, () -> RouteFiles.read(written.manifest()), "V_int start is mandatory");
        Files.writeString(written.manifest(), manifest.replace("\"r.script\"", "\"../r.script\""));
        assertThrows(IOException.class, () -> RouteFiles.read(written.manifest()));
        Files.writeString(written.manifest(), manifest.replace("63522553", "63522554"));
        assertTrue(assertThrows(IOException.class, () -> RouteFiles.read(written.manifest())).getMessage()
                .contains("no passed engine qualification"), "another launch is another content; its stamp is stale");
        assertFalse(RouteFiles.readDraft(written.manifest()).launch().rom().matches(RomIdentity.standard("s3k")),
                "ROM identity is read as declared, never corrected; the host resolver rejects the mismatch");
    }

    @Test
    void draftsAreSavedMarkedAndRefusedByConsumers(@TempDir Path dir) throws Exception {
        RouteArtifact route = sample();
        var written = RouteFiles.writeDraft(dir.resolve("d"), route);
        assertTrue(Files.readString(written.manifest()).contains("\"draft\" : true"));
        assertTrue(assertThrows(IOException.class, () -> RouteFiles.read(written.manifest())).getMessage()
                .contains("unqualified draft"));
        RouteArtifact draft = RouteFiles.readDraft(written.manifest());
        assertTrue(draft.manifest().draft());
        assertTrue(draft.manifest().qualification().isEmpty());
        assertEquals(route.content(), draft.content(), "the draft flag is not content");
        RouteFiles.writeDraft(dir.resolve("d"), route); // a draft may replace a draft
        RouteFiles.replace(dir.resolve("d"), route, evidence(route));
        assertFalse(RouteFiles.read(written.manifest()).manifest().draft(), "qualifying clears the draft flag");
        assertThrows(IOException.class, () -> RouteFiles.writeDraft(dir.resolve("d"), route),
                "a draft never replaces a qualified route");
    }

    @Test
    void transportTextCarriesLaunchGoalsAndRowsLosslessly() throws Exception {
        var goals = GoalProgram.of(Guard.safe(),
                new Leg("reach", new RouteGoal.Achieve(new SamplePredicate.All(List.of(
                        new SamplePredicate.PlayerIn(PlayerSlot.P1, new Region(100, 200, 300, 400)),
                        new SamplePredicate.Settled(PlayerSlot.P1, 64)))), Guard.NONE, 600),
                new Leg("load", new RouteGoal.EnterBoundary(), Guard.safe(), 900),
                new Leg("arrive", new RouteGoal.Achieve(new SamplePredicate.LevelIs(10, 1)), Guard.NONE, 10));
        RouteArtifact route = sample().withChecks(goals);
        assertNotEquals(sample().content(), route.content(), "checks are content");
        RouteArtifact copy = RouteCodec.parseTransport(RouteCodec.formatTransport(route));
        assertEquals(route.manifest(), copy.manifest());
        assertEquals(route.tape(), copy.tape());
        assertEquals(goals, RouteCodec.parseGoals(RouteCodec.formatGoals(goals)));
        assertEquals(route.launch(), RouteCodec.parseLaunch(RouteCodec.formatLaunch(route.launch())));
        String tampered = RouteCodec.formatTransport(route).replace("\"rows\" : 4", "\"rows\" : 5");
        assertThrows(IOException.class, () -> RouteCodec.parseTransport(tampered));
    }

    @Test
    void stampsCarryTheirFirstDivergingRow(@TempDir Path dir) throws Exception {
        RouteArtifact route = sample();
        var written = RouteFiles.writeNew(dir.resolve("n"), route, evidence(route));
        var nativeStamp = QualificationStamp.diverged(ReplayScope.BIZHAWK, 2, "x off by 3", route.content(),
                "bizhawk 2.11", 3);
        RouteFiles.addStamp(written.manifest(), nativeStamp, NativeFacts.NONE);
        RouteArtifact read = RouteFiles.read(written.manifest());
        var stamp = read.manifest().qualification().stream().filter(s -> s.scope() == ReplayScope.BIZHAWK)
                .findFirst().orElseThrow();
        assertFalse(stamp.passed());
        assertEquals(2, stamp.divergedRow().orElseThrow());
        assertEquals("x off by 3", stamp.reason());
        assertTrue(read.engineQualified(), "a native verdict does not replace the engine one");
    }

}
