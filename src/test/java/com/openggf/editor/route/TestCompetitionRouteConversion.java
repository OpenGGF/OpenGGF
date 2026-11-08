package com.openggf.editor.route;

import com.openggf.route.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Editor contracts: untouched bytes, predicate validation, and native-export resolution. */
class TestCompetitionRouteConversion {
    @TempDir Path dir;

    @Test void frozenPairSaveKeepsEveryByte() throws Exception {
        for(String name:List.of("cnz1-slot-prefix","cnz1-slot-flip")) {
            Path source=Path.of("src/test/resources/routes/s2/competition",name);
            Path stem=dir.resolve(name), manifest=Path.of(stem+".route.json"), script=Path.of(stem+".script");
            Files.copy(Path.of(source+".route.json"),manifest); Files.copy(Path.of(source+".script"),script);
            byte[] m=Files.readAllBytes(manifest), t=Files.readAllBytes(script);
            var route=RouteFiles.read(manifest);
            // Evidence is minted by replay, not a fabricated test stamp: the source qualification
            // is exercised via the real worker in TestCompetitionRouteWorker. This checks draft
            // preservation with the same raw syntax (including comment/header/whitespace).
            var draftManifest=route.manifest();
            var draft=new RouteManifest(draftManifest.launch(),draftManifest.tapeHash(),draftManifest.rowCount(),
                    draftManifest.checks(),true,draftManifest.provenance(),List.of(),draftManifest.nativeFacts());
            Files.writeString(manifest,RouteCodec.formatManifest(draft,name+".script"));
            m=Files.readAllBytes(manifest);
            RouteEditorFiles.draft(stem,RouteFiles.readDraft(manifest));
            assertArrayEquals(m,Files.readAllBytes(manifest)); assertArrayEquals(t,Files.readAllBytes(script));
        }
    }

    @Test void knownUnknownAndMissingFactKeysAreExplicit() {
        RouteCompetitionFacts.requireKey("competition.p2.score");
        assertThrows(IllegalArgumentException.class,()->RouteCompetitionFacts.requireKey("competition.p2.scroe"));
        assertThrows(IllegalArgumentException.class,()->RouteCompetitionFacts.requireKey(""));
        var workspace=new RouteTapeWorkspace(new RouteTapeWorkspace.Host(){public boolean competitionAvailable(){return true;}});
        workspace.createCompetition(dir.resolve("new.route.json"),2);
        workspace.invoke(RouteTapeWorkspace.Action.ADD_LEG);
        workspace.menu().setValue("goal","fact-equals");
        workspace.menu().setValue("fact","competition.p2.scroe");
        workspace.menu().submit(); assertNotNull(workspace.menu().error());
        assertTrue(workspace.menu().error().contains("Unknown fact key"));
        assertTrue(workspace.goals().legs().isEmpty());
        workspace.menu().setValue("fact","competition.p2.score");
        workspace.menu().submit(); assertNull(workspace.menu().error());
        assertEquals("P2",workspace.goalRows().getFirst().player());
        workspace.invoke(RouteTapeWorkspace.Action.ADD_LEG);
        workspace.menu().setValue("goal","facts-equal");
        workspace.menu().setValue("fact","competition.zoneWins.p1");
        workspace.menu().setValue("otherFact","competition.zoneWins.p2");
        workspace.menu().submit(); assertNull(workspace.menu().error());
        assertEquals("Both",workspace.goalRows().getLast().player());
        workspace.invoke(RouteTapeWorkspace.Action.MENU);
        assertTrue(workspace.menu().actionView().rows().stream().noneMatch(row ->
                row.label().equals(RouteTapeWorkspace.Action.EXPORT_BK2.label())), "Blocked export is not offered by the HUD action menu");
    }

    @Test void bothPositionAuthoringAndSummariesUseOneCoordinateGrammar() {
        var w=new RouteTapeWorkspace(new RouteTapeWorkspace.Host(){public boolean competitionAvailable(){return true;}});
        w.createCompetition(dir.resolve("both.route.json"),2); w.invoke(RouteTapeWorkspace.Action.ADD_LEG);
        w.menu().setValue("player","Both"); w.menu().setValue("x","1296");w.menu().setValue("y","900");w.menu().setValue("radius","16");
        w.menu().submit(); assertNull(w.menu().error());
        assertEquals("Both",w.goalRows().getFirst().player());
        assertEquals("P1 in (1280,884)–(1312,916) + P2 in (1280,884)–(1312,916)",w.goalRows().getFirst().predicate());
        assertNotEquals(RouteEditorStyle.playerColour("P1"),RouteEditorStyle.playerColour("P2"));
        assertNotEquals(RouteEditorStyle.playerColour("P1"),RouteEditorStyle.playerColour("Both"));
        assertNotEquals(0x4C9AFF,RouteEditorStyle.lockedColour(0x4C9AFF));
    }

    @Test void importedUnknownFactRemainsVisibleAndCanBeRepaired() throws Exception {
        Path source=Path.of("src/test/resources/routes/s2/competition/cnz1-slot-flip.route.json");
        Path manifest=dir.resolve("unknown.route.json");
        var original=RouteFiles.readDraft(source);
        // Isolate fact editing from the traversal route's evolving waypoint order.
        var oneFact=GoalProgram.of(Guard.NONE,Leg.of("fact",new RouteGoal.Achieve(
                new SamplePredicate.FactEquals("competition.slot.inUse",1)),300));
        var factRoute=original.withChecks(oneFact);
        String text=RouteCodec.formatManifest(factRoute.manifest(),"unknown.script")
                .replace("competition.slot.inUse","competition.slot.unknown");
        Files.writeString(manifest,text);Files.copy(source.resolveSibling("cnz1-slot-flip.script"),dir.resolve("unknown.script"));
        var w=new RouteTapeWorkspace(new RouteTapeWorkspace.Host(){}); w.open(manifest);
        assertTrue(w.goalRows().getFirst().predicate().startsWith("Error:"));
        w.editGoal(0);
        assertNotNull(w.menu().error()); assertEquals("competition.slot.unknown",w.menu().values().get("fact"));
        assertTrue(w.menu().formFields().stream().filter(f->f.key().equals("fact")).findFirst().orElseThrow().choices().contains("competition.slot.unknown"));
        w.menu().setValue("fact","competition.slot.inUse");w.menu().submit();assertNull(w.menu().error());
        assertFalse(w.goalRows().getFirst().predicate().startsWith("Error:"));
    }

    @Test void missingObservedKeysAndAnchorUndoAreExplicit() throws Exception {
        final RouteArtifact[] requested={null}; final int[] polls={0};
        var w=new RouteTapeWorkspace(new RouteTapeWorkspace.Host(){
            public boolean competitionAvailable(){return true;}
            public void startJob(RouteTapeJob.Request r){requested[0]=r.route(); polls[0]=0;}
            public RouteTapeJob.Update pollJob(){
                return new RouteTapeJob.Update(1,++polls[0]==1?RouteTapeJob.Phase.RUNNING:RouteTapeJob.Phase.SCANNED,
                        RouteTapeJob.Kind.SCAN,1,1,1,1,"scan",requested[0].content().toString(),1200,null,null,null,null,List.of(),Map.of("competition.phase",1));
            }
        });
        w.createCompetition(dir.resolve("anchored.route.json"),2);
        w.invoke(RouteTapeWorkspace.Action.SCAN); w.tick(new com.openggf.control.InputHandler());
        assertEquals(Map.of("competition.phase",1),w.observedFacts());
        w.invoke(RouteTapeWorkspace.Action.ADD_LEG);w.menu().setValue("goal","fact-equals");w.menu().setValue("fact","competition.slot.inUse");
        w.menu().submit();assertNotNull(w.menu().error());assertTrue(w.menu().error().contains("Missing fact at cursor"));
        Path witness=dir.resolve("witness.script");Files.writeString(witness,"2 -\n");
        w.invoke(RouteTapeWorkspace.Action.NATIVE_FACTS);w.menu().setValue("movie",witness.toString());
        w.menu().setValue("nativeRows","2");w.menu().setValue("engineRows","1");w.menu().submit();assertNull(w.menu().error());
        assertTrue(w.working().manifest().nativeFacts().entry().isPresent());
        assertTrue(w.exportBlocker().contains("Replay"));
        w.invoke(RouteTapeWorkspace.Action.UNDO);assertTrue(w.working().manifest().nativeFacts().entry().isEmpty());
        w.invoke(RouteTapeWorkspace.Action.REDO);assertTrue(w.working().manifest().nativeFacts().entry().isPresent());
    }

    @Test void exportResolvesWitnessAtRouteRootAndSplicesBothPads() throws Exception {
        Path root=dir.resolve("project"), folder=root.resolve("src/test/resources/routes/s2/competition");
        Files.createDirectories(folder);
        var witness=RouteFiles.tapeFromScript("2 R / L+A\n");
        Path file=root.resolve("native.script"); Files.writeString(file,RouteFiles.scriptText(witness));
        var tape=RouteFiles.tapeFromScript("1 S\n2 L / R+B\n");
        var launch=com.openggf.stageplay.route.StageplayCompetitionRouteHosts.launch();
        var source=RouteArtifact.of(launch,tape,new Provenance("test","","",List.of()));
        var entry=new NativeFacts.NativeEntry("native.script",2,witness.hash(),1,"first-ordinary-level-lfc-zero");
        var route=new RouteArtifact(source.manifest().withNativeFacts(new NativeFacts(Optional.of(entry),List.of())),tape);
        Path output=dir.resolve("export.bk2");
        RouteEditorFiles.export(route,folder.resolve("route.route.json"),output);
        var exported=RouteFiles.tapeFromMovie(output,4);
        assertEquals(witness.append(tape.slice(1,3)),exported);
        Files.writeString(file,"2 -\n");
        assertThrows(java.io.IOException.class,()->RouteEditorFiles.export(route,folder.resolve("route.route.json"),dir.resolve("bad.bk2")));
    }
}
