package com.openggf.tools;

import com.openggf.route.Capability;
import com.openggf.route.InteractionKind;
import com.openggf.route.LaunchContract;
import com.openggf.route.LevelId;
import com.openggf.route.ObjectView;
import com.openggf.route.PlacementKey;
import com.openggf.route.Provenance;
import com.openggf.route.RomIdentity;
import com.openggf.route.Roster;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteFiles;
import com.openggf.route.RouteReplay;
import com.openggf.route.RouteSample;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner-reported object facts reach route samples: the SSZ1 arrival bridge (the placed $77
 * cutscene bridge, placement 12) reports its own retraction phase in the owner's order, and samples stay reproducible
 * (the same rows give the same facts in a second cold playback).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestEngineRouteObjectFacts {
    @Test
    void theSszArrivalBridgeReportsItsRetractionPhaseByPlacement() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        var hosts = EngineRouteHosts.withRoms(Map.of("s3k", rom));
        var launch = LaunchContract.coldLevel(RomIdentity.ofFile("s3k", rom), new LevelId("s3k", 10, 0),
                new Roster("sonic", "tails"), 320, 0);
        var rows = RouteFiles.read(Path.of("src/test/resources/routes/s3k/ssz1-sonic-tails-cold-complete-320.route.json"))
                .tape().slice(0, 1360);
        var route = RouteArtifact.of(launch, rows, new Provenance("test", "", "", List.of()));
        var phases = new ArrayList<String>();
        var bridges = new ArrayList<ObjectView>();
        int airflow = 0;
        RouteSample end;
        try (var playback = new RouteReplay(hosts).open(route)) {
            while (!playback.finished()) {
                RouteSample sample = playback.step();
                for (ObjectView object : sample.objects()) {
                    if (!object.typeKey().equals("SszCutsceneBridgeObjectInstance")) continue;
                    bridges.add(object);
                    if (phases.isEmpty() || !phases.getLast().equals(object.phase())) phases.add(object.phase());
                }
                airflow += (int) sample.interactions().stream().filter(i -> i.kind() == InteractionKind.AIRFLOW).count();
            }
            end = playback.sample();
        }
        assertFalse(bridges.isEmpty(), "the arrival bridge is observed");
        ObjectView bridge = bridges.getFirst();
        assertTrue(bridge.has(Capability.PHASE));
        assertEquals(List.of("waiting", "retracting", "extended"), bridge.phaseNames());
        assertEquals(12, bridge.placement().map(PlacementKey::layoutIndex).orElse(-1));
        assertEquals(0x77, bridge.placement().map(PlacementKey::objectId).orElse(-1));
        // The owner's phases only advance: waiting, then retracting, then (once at rest) extended.
        assertTrue(phases.contains("retracting"), "the arrival cutscene retracts the bridge: " + phases);
        assertEquals(List.of("waiting", "retracting", "extended").subList(0, phases.size()), phases,
                "phases advance in the owner's order: " + phases);
        System.out.println("SSZ1 rows 0..1359: bridge phases " + phases + ", airflow interactions " + airflow);
        try (var again = new RouteReplay(hosts).open(route)) {
            assertEquals(end, again.stepTo(again.size()), "object facts are reproducible from the same rows");
        }
    }
}
