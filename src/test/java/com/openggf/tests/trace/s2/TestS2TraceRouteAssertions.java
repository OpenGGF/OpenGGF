package com.openggf.tests.trace.s2;

import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TestS2TraceRouteAssertions {

    @ParameterizedTest
    @CsvSource({
            "arz,arz,2,15,1",
            "arz2,arz,2,15,2",
            "cnz,cnz,3,12,1",
            "cnz2,cnz,3,12,2",
            "cpz,cpz,1,13,1",
            "cpz2,cpz,1,13,2",
            "dez_ending,dez,10,14,1",
            "htz,htz,4,7,1",
            "htz2,htz,4,7,2",
            "mcz,mcz,5,11,1",
            "mcz2,mcz,5,11,2",
            "mtz,mtz,7,4,1",
            "mtz2,mtz,7,4,2",
            "mtz3,mtz,7,5,3",
            "ooz,ooz,6,10,1",
            "ooz2,ooz,6,10,2",
            "scz,scz,8,16,1",
            "wfz,wfz,9,6,1"
    })
    void generatedLevelSelectFixturesHaveRouteMetadata(String route,
                                                       String zoneSlug,
                                                       int engineZoneId,
                                                       int romZoneId,
                                                       int metadataAct) throws IOException {
        S2TraceRouteAssertions.assertRoute(
                TraceData.load(Path.of("src/test/resources/traces/s2").resolve(route)),
                zoneSlug,
                engineZoneId,
                romZoneId,
                metadataAct);
    }

    @Test
    void acceptsRouteWithFrameZeroZoneActMarker() throws IOException {
        Path dir = createTraceDir("""
            {"frame":0,"event":"zone_act_state","actual_zone_id":13,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        S2TraceRouteAssertions.assertRoute(
                TraceData.load(dir),
                "cpz",
                Sonic2ZoneConstants.ZONE_CPZ,
                Sonic2ZoneConstants.ROM_ZONE_CPZ,
                1);
    }

    @Test
    void acceptsRouteWithFrameZeroGameplayStartCheckpoint() throws IOException {
        Path dir = createTraceDir("""
            {"frame":0,"event":"checkpoint","name":"gameplay_start","actual_zone_id":13,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        S2TraceRouteAssertions.assertRoute(
                TraceData.load(dir),
                "cpz",
                Sonic2ZoneConstants.ZONE_CPZ,
                Sonic2ZoneConstants.ROM_ZONE_CPZ,
                1);
    }

    @Test
    void rejectsRouteWithoutFrameZeroGameplayMarker() throws IOException {
        Path dir = createTraceDir("""
            {"frame":1,"event":"checkpoint","name":"gameplay_start","actual_zone_id":13,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        assertThrows(AssertionError.class, () ->
                S2TraceRouteAssertions.assertRoute(
                        TraceData.load(dir),
                        "cpz",
                        Sonic2ZoneConstants.ZONE_CPZ,
                        Sonic2ZoneConstants.ROM_ZONE_CPZ,
                        1));
    }

    @Test
    void rejectsRawRomZoneIdStoredAsCatalogZoneId() throws IOException {
        Path dir = Files.createTempDirectory("s2-route-raw-zone");
        writeMetadata(dir, Sonic2ZoneConstants.ROM_ZONE_CPZ, Sonic2ZoneConstants.ROM_ZONE_CPZ);
        writePhysics(dir);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"zone_act_state","actual_zone_id":13,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        assertThrows(AssertionError.class, () ->
                S2TraceRouteAssertions.assertRoute(
                        TraceData.load(dir),
                        "cpz",
                        Sonic2ZoneConstants.ZONE_CPZ,
                        Sonic2ZoneConstants.ROM_ZONE_CPZ,
                        1));
    }

    private static Path createTraceDir(String auxState) throws IOException {
        Path dir = Files.createTempDirectory("s2-route-ok");
        writeMetadata(dir, Sonic2ZoneConstants.ZONE_CPZ, Sonic2ZoneConstants.ROM_ZONE_CPZ);
        writePhysics(dir);
        Files.writeString(dir.resolve("aux_state.jsonl"), auxState);
        return dir;
    }

    private static void writeMetadata(Path dir, int engineZoneId, int romZoneId) throws IOException {
        Files.writeString(dir.resolve("metadata.json"), String.format("""
            {
              "game": "s2",
              "zone": "cpz",
              "zone_id": %d,
              "rom_zone_id": %d,
              "act": 1,
              "bk2_frame_offset": 1234,
              "trace_frame_count": 1,
              "start_x": "0x0060",
              "start_y": "0x0290",
              "recording_date": "2026-05-14",
              "lua_script_version": "9.0-s2",
              "trace_schema": 8,
              "csv_version": 6,
              "trace_profile": "level_gated_reset_aware",
              "bizhawk_version": "2.11",
              "genesis_core": "Genplus-gx",
              "route": "cpz",
              "source_bk2": "s2-lvl-select-CPZ.bk2",
              "rom_checksum": "ABCDEF",
              "characters": ["sonic", "tails"],
              "main_character": "sonic",
              "sidekicks": ["tails"]
            }
            """, engineZoneId, romZoneId));
    }

    private static void writePhysics(Path dir) throws IOException {
        Files.writeString(dir.resolve("physics.csv"), """
            0000,0000,0060,0290,0000,0000,0000,00,0,0,0
            """);
    }
}
