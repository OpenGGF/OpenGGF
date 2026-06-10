package com.openggf.tests.trace;
import com.openggf.trace.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class TestTraceDataParsing {

    private static final Path SYNTHETIC_3FRAMES =
        Path.of("src/test/resources/traces/synthetic/basic_3frames");
    private static final Path SYNTHETIC_EXECUTION_V3 =
        Path.of("src/test/resources/traces/synthetic/execution_v3_2frames");

    @Test
    public void testMetadataLoading() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        TraceMetadata meta = data.metadata();

        assertEquals("s1", meta.game());
        assertEquals("ghz", meta.zone());
        assertEquals(1, meta.act());
        assertEquals(100, meta.bk2FrameOffset());
        assertEquals(3, meta.traceFrameCount());
        assertEquals((short) 0x0050, meta.startX());
        assertEquals((short) 0x03B0, meta.startY());
    }

    @Test
    void metadataParsesRecordedTeamWhenPresent() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-team");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "ehz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 899,
              "trace_frame_count": 1,
              "start_x": "0x0060",
              "start_y": "0x0290",
              "recording_date": "2026-04-21",
              "lua_script_version": "4.0-s2",
              "trace_schema": 4,
              "csv_version": 4,
              "rom_checksum": "",
              "notes": "",
              "main_character": "sonic",
              "sidekicks": ["tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0060,0290,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0230,0000,00,0001,00,0001,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);
        TraceMetadata meta = data.metadata();

        assertTrue(meta.hasRecordedTeam());
        assertEquals("sonic", meta.mainCharacter());
        assertEquals(List.of("tails"), meta.recordedSidekicks());
    }

    @Test
    void parsesExtendedS2LevelSelectMetadata() throws IOException {
        Path dir = Files.createTempDirectory("s2-level-select-meta");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "cpz",
              "zone_id": 1,
              "rom_zone_id": 13,
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
              "notes": "test",
              "characters": ["sonic", "tails"],
              "main_character": "sonic",
              "sidekicks": ["tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            0000,0000,0060,0290,0000,0000,0000,00,0,0,0
            """);

        TraceMetadata metadata = TraceData.load(dir).metadata();

        assertEquals("s2", metadata.game());
        assertEquals("cpz", metadata.zone());
        assertEquals(1, metadata.zoneId());
        assertEquals(13, metadata.romZoneId());
        assertEquals(6, metadata.csvVersion());
        assertEquals("level_gated_reset_aware", metadata.traceProfile());
        assertEquals("2.11", metadata.bizhawkVersion());
        assertEquals("Genplus-gx", metadata.genesisCore());
        assertEquals("cpz", metadata.route());
        assertEquals("s2-lvl-select-CPZ.bk2", metadata.sourceBk2());
    }

    @Test
    void metadataParsesInitialRngSeedWhenPresent() throws IOException {
        Path dir = Files.createTempDirectory("trace-meta-rng");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 1,
              "bk2_frame_offset": 3171,
              "trace_frame_count": 1,
              "start_x": "0x0018",
              "start_y": "0x0600",
              "rng_seed": "0x89ABCDEF",
              "recording_date": "2026-04-23",
              "lua_script_version": "3.2-s3k",
              "trace_schema": 3,
              "csv_version": 4,
              "rom_checksum": "",
              "notes": ""
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0018,0600,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0500,0000,00,0001,00,0001,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceMetadata meta = TraceData.load(dir).metadata();

        assertEquals(0x89ABCDEFL, meta.initialRngSeed());
    }

    @Test
    void parsesPlayerHistorySnapshot() {
        TraceEvent event = TraceEvent.parseJsonLine(
            """
            {"frame":-1,"event":"player_history_snapshot","history_pos":62,"x_history":[96,96,97],"y_history":[656,656,656],"input_history":[0,8,8],"status_history":[0,0,0]}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());

        assertInstanceOf(TraceEvent.PlayerHistorySnapshot.class, event);
        TraceEvent.PlayerHistorySnapshot snapshot = (TraceEvent.PlayerHistorySnapshot) event;
        assertEquals(62, snapshot.historyPos());
        assertArrayEquals(new short[]{96, 96, 97}, snapshot.xHistory());
        assertArrayEquals(new short[]{656, 656, 656}, snapshot.yHistory());
        assertArrayEquals(new short[]{0, 8, 8}, snapshot.inputHistory());
        assertArrayEquals(new byte[]{0, 0, 0}, snapshot.statusHistory());
    }

    @Test
    void parsesCpuStateSnapshot() {
        TraceEvent event = TraceEvent.parseJsonLine(
            """
            {"frame":-1,"event":"cpu_state_snapshot","character":"tails","control_counter":0,"respawn_counter":299,"cpu_routine":6,"target_x":"0x0613","target_y":"0x0264","interact_id":"0x10","jumping":1}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());

        assertInstanceOf(TraceEvent.CpuStateSnapshot.class, event);
        TraceEvent.CpuStateSnapshot snapshot = (TraceEvent.CpuStateSnapshot) event;
        assertEquals("tails", snapshot.character());
        assertEquals(0, snapshot.controlCounter());
        assertEquals(299, snapshot.respawnCounter());
        assertEquals(6, snapshot.cpuRoutine());
        assertEquals((short) 0x0613, snapshot.targetX());
        assertEquals((short) 0x0264, snapshot.targetY());
        assertEquals(0x10, snapshot.interactId());
        assertTrue(snapshot.jumping());
    }

    @Test
    void v5TraceParsesRecordedSidekickState() throws IOException {
        Path dir = Files.createTempDirectory("trace-v5-sidekick");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "ehz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 899,
              "trace_frame_count": 1,
              "start_x": "0x0060",
              "start_y": "0x0290",
              "recording_date": "2026-04-21",
              "lua_script_version": "5.0-s2",
              "trace_schema": 5,
              "csv_version": 5,
              "rom_checksum": "",
              "notes": "",
              "main_character": "sonic",
              "sidekicks": ["tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0008,0060,0290,000C,0000,000C,00,0,0,0,0C00,0000,02,0000,0230,0000,00,0001,00,02AC,0000,1,0050,0288,0010,FFF0,0010,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);
        TraceFrame frame = data.getFrame(0);

        assertNotNull(frame.sidekick());
        assertTrue(frame.sidekick().present());
        assertEquals((short) 0x0050, frame.sidekick().x());
        assertEquals((short) 0x0288, frame.sidekick().y());
        assertEquals((short) 0x0010, frame.sidekick().xSpeed());
        assertEquals((short) -0x0010, frame.sidekick().ySpeed());
        assertEquals((byte) 0x08, frame.sidekick().angle());
        assertTrue(frame.sidekick().air());
        assertEquals(0x8000, frame.sidekick().xSub());
        assertEquals(0x4000, frame.sidekick().ySub());
        assertEquals(0x02, frame.sidekick().routine());
        assertEquals(0x0A, frame.sidekick().statusByte());
        assertEquals(0x03, frame.sidekick().standOnObj());
    }

    @Test
    void v5TraceExposesTrackedCharactersByName() throws IOException {
        Path dir = Files.createTempDirectory("trace-v5-characters");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "ehz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 899,
              "trace_frame_count": 1,
              "start_x": "0x0060",
              "start_y": "0x0290",
              "recording_date": "2026-04-21",
              "lua_script_version": "5.0-s2",
              "trace_schema": 5,
              "csv_version": 5,
              "rom_checksum": "",
              "notes": "",
              "main_character": "sonic",
              "sidekicks": ["tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0008,0060,0290,000C,0000,000C,00,0,0,0,0C00,0000,02,0000,0230,0000,00,0001,04,02AC,0000,1,0050,0288,0010,FFF0,0010,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("sonic", "tails"), data.recordedCharacters());

        TraceCharacterState sonic = data.characterState(0, "sonic");
        TraceCharacterState tails = data.characterState(0, "tails");

        assertNotNull(sonic);
        assertNotNull(tails);
        assertEquals((short) 0x0060, sonic.x());
        assertEquals((short) 0x0290, sonic.y());
        assertEquals((short) 0x000C, sonic.xSpeed());
        assertEquals(0x0C00, sonic.xSub());
        assertEquals(0x04, sonic.standOnObj());
        assertEquals((short) 0x0050, tails.x());
        assertEquals((short) 0x0288, tails.y());
        assertEquals((short) 0x0010, tails.xSpeed());
        assertEquals(0x03, tails.standOnObj());
        assertNull(data.characterState(0, "knuckles"));
    }

    @Test
    void v6TraceParsesExplicitSonicAndTailsBlocks() throws IOException {
        Path dir = Files.createTempDirectory("trace-v6-characters");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "ehz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 899,
              "trace_frame_count": 1,
              "start_x": "0x0060",
              "start_y": "0x0290",
              "recording_date": "2026-04-21",
              "lua_script_version": "6.0-s2",
              "trace_schema": 6,
              "csv_version": 6,
              "rom_checksum": "",
              "notes": "",
              "characters": ["sonic", "tails"]
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,camera_x,camera_y,rings,gameplay_frame_counter,vblank_counter,lag_counter,sonic_present,sonic_x,sonic_y,sonic_x_speed,sonic_y_speed,sonic_g_speed,sonic_angle,sonic_air,sonic_rolling,sonic_ground_mode,sonic_x_sub,sonic_y_sub,sonic_routine,sonic_status_byte,sonic_stand_on_obj,tails_present,tails_x,tails_y,tails_x_speed,tails_y_speed,tails_g_speed,tails_angle,tails_air,tails_rolling,tails_ground_mode,tails_x_sub,tails_y_sub,tails_routine,tails_status_byte,tails_stand_on_obj
            0000,0008,0000,0230,0000,0001,02AC,0000,1,0060,0290,000C,0000,000C,00,0,0,0,0C00,0000,02,00,04,1,0050,0288,0010,FFF0,0010,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);
        TraceMetadata meta = data.metadata();
        TraceFrame frame = data.getFrame(0);

        assertTrue(meta.hasRecordedTeam());
        assertEquals(List.of("sonic", "tails"), meta.recordedCharacters());
        assertEquals(List.of("tails"), meta.recordedSidekicks());

        TraceCharacterState sonic = data.characterState(0, "sonic");
        TraceCharacterState tails = data.characterState(0, "tails");

        assertNotNull(sonic);
        assertNotNull(tails);
        assertEquals((short) 0x0060, frame.x());
        assertEquals((short) 0x0290, frame.y());
        assertEquals((short) 0x000C, frame.xSpeed());
        assertEquals(0x0C00, frame.xSub());
        assertEquals(0x04, frame.standOnObj());
        assertEquals((short) 0x0060, sonic.x());
        assertEquals((short) 0x0290, sonic.y());
        assertEquals((short) 0x0050, tails.x());
        assertEquals((short) 0x0288, tails.y());
        assertEquals((short) 0x0010, tails.xSpeed());
        assertEquals(0x03, tails.standOnObj());
    }

    @Test
    void s2RouteFixturesReplayRecordedSidekickTeam() throws IOException {
        for (String route : List.of("scz", "wfz")) {
            TraceData data = TraceData.load(Path.of("src/test/resources/traces/s2", route));
            TraceMetadata meta = data.metadata();

            assertEquals(List.of("sonic", "tails"), meta.recordedCharacters(), route + " recorded characters");
            assertEquals(List.of("tails"), meta.recordedSidekicks(), route + " recorded sidekicks");
            assertNotNull(data.characterState(0, "tails"), route + " should preserve tails metadata");
            assertNotNull(data.getFrame(0).sidekick(), route + " CSV still carries the diagnostic Tails block");
            assertFalse(data.getFrame(0).sidekick().present(), route + " frame 0 Tails block should be absent");
        }
    }

    @Test
    void activeS2RouteFixturesKeepSidekickBootstrapPolicy() throws IOException {
        TraceData data = TraceData.load(Path.of("src/test/resources/traces/s2/ehz1_fullrun"));

        assertEquals(List.of("tails"), data.metadata().recordedSidekicks());
        assertTrue(data.getFrame(0).sidekick().present());
    }

    @Test
    public void testFrameCount() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        assertEquals(3, data.frameCount());
    }

    @Test
    public void testFrameParsing() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);

        TraceFrame frame0 = data.getFrame(0);
        assertEquals(0, frame0.frame());
        assertEquals(0, frame0.input());
        assertEquals((short) 0x0050, frame0.x());
        assertEquals((short) 0x03B0, frame0.y());
        assertEquals((short) 0, frame0.xSpeed());
        assertFalse(frame0.air());
        assertFalse(frame0.rolling());
        assertEquals(0, frame0.groundMode());

        TraceFrame frame1 = data.getFrame(1);
        assertEquals(0x0008, frame1.input());
        assertEquals((short) 0x0051, frame1.x());
        assertEquals((short) 0x000C, frame1.xSpeed());
        assertEquals((short) 0x000C, frame1.gSpeed());

        TraceFrame frame2 = data.getFrame(2);
        assertEquals((byte) 0x10, frame2.angle());
    }

    @Test
    public void testAuxEventsParsing() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);

        List<TraceEvent> frame0Events = data.getEventsForFrame(0);
        assertEquals(1, frame0Events.size());
        assertInstanceOf(TraceEvent.StateSnapshot.class, frame0Events.get(0));

        List<TraceEvent> frame2Events = data.getEventsForFrame(2);
        assertEquals(1, frame2Events.size());
        TraceEvent.ModeChange mc = (TraceEvent.ModeChange) frame2Events.get(0);
        assertEquals("angle", mc.field());
        assertEquals(0, mc.from());
        assertEquals(16, mc.to());
    }

    @Test
    void loadsAuxEventsFromGzipWhenPlainJsonlIsAbsent() throws IOException {
        Path dir = Files.createTempDirectory("trace-gz-aux");
        writeMinimalTraceFiles(dir);
        writeGzipString(dir.resolve("aux_state.jsonl.gz"), """
            {"frame":0,"event":"checkpoint","name":"gz_aux_loaded","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);

        List<TraceEvent> events = data.getEventsForFrame(0);
        assertEquals(1, events.size());
        TraceEvent.Checkpoint checkpoint = assertInstanceOf(TraceEvent.Checkpoint.class, events.getFirst());
        assertEquals("gz_aux_loaded", checkpoint.name());
    }

    @Test
    void plainAuxJsonlTakesPrecedenceOverGzipSidecar() throws IOException {
        Path dir = Files.createTempDirectory("trace-plain-aux-first");
        writeMinimalTraceFiles(dir);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"checkpoint","name":"plain_aux_loaded","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);
        writeGzipString(dir.resolve("aux_state.jsonl.gz"), """
            {"frame":0,"event":"checkpoint","name":"gzip_aux_loaded","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);

        List<TraceEvent> events = data.getEventsForFrame(0);
        assertEquals(1, events.size());
        TraceEvent.Checkpoint checkpoint = assertInstanceOf(TraceEvent.Checkpoint.class, events.getFirst());
        assertEquals("plain_aux_loaded", checkpoint.name());
    }

    @Test
    void loadsPhysicsFramesFromGzipWhenPlainCsvIsAbsent() throws IOException {
        Path dir = Files.createTempDirectory("trace-gz-physics");
        writeMinimalMetadata(dir);
        writeGzipString(dir.resolve("physics.csv.gz"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000
            0001,0008,0081,03A0,000C,0000,000C,00,0,0,0,0C00,0000,02,0000,0000,0000,00,0002,00,0002,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);

        assertEquals(2, data.frameCount());
        assertEquals(0x0008, data.getFrame(1).input());
        assertEquals((short) 0x0081, data.getFrame(1).x());
    }

    @Test
    public void testNoEventsForFrame() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        List<TraceEvent> events = data.getEventsForFrame(1);
        assertTrue(events.isEmpty());
    }

    @Test
    public void testEventRange() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        List<TraceEvent> events = data.getEventsInRange(0, 2);
        assertEquals(2, events.size());
    }

    @Test
    public void testV22TraceStillParsesWithMissingExecutionCounters() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        TraceFrame frame0 = data.getFrame(0);

        assertEquals(-1, frame0.vblankCounter());
        assertEquals(-1, frame0.lagCounter());
        assertEquals(100, data.initialVblankCounter());
    }

    @Test
    public void testV3TraceParsesExecutionCounters() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_EXECUTION_V3);
        TraceMetadata meta = data.metadata();
        TraceFrame frame0 = data.getFrame(0);
        TraceFrame frame1 = data.getFrame(1);

        assertEquals(3, meta.traceSchema());
        assertEquals(0x3456, frame0.gameplayFrameCounter());
        assertEquals(0x0120, frame0.vblankCounter());
        assertEquals(0, frame0.lagCounter());
        assertEquals(0x3456, frame1.gameplayFrameCounter());
        assertEquals(0x0121, frame1.vblankCounter());
        assertEquals(0, frame1.lagCounter());
        assertEquals(0x0120, data.initialVblankCounter());
    }

    @Test
    void parsesCheckpointEvent() {
        TraceEvent event = TraceEvent.parseJsonLine(
            """
            {"frame":1200,"event":"checkpoint","name":"aiz2_main_gameplay","actual_zone_id":0,"actual_act":1,"apparent_act":0,"game_mode":12,"notes":"resume strict replay"}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());

        assertInstanceOf(TraceEvent.Checkpoint.class, event);
        TraceEvent.Checkpoint checkpoint = (TraceEvent.Checkpoint) event;
        assertEquals("aiz2_main_gameplay", checkpoint.name());
        assertEquals(0, checkpoint.actualZoneId());
        assertEquals(1, checkpoint.actualAct());
        assertEquals(0, checkpoint.apparentAct());
        assertEquals(12, checkpoint.gameMode());
        assertEquals("resume strict replay", checkpoint.notes());
    }

    @Test
    void parsesCharacterScopedAuxEvents() {
        TraceEvent modeEvent = TraceEvent.parseJsonLine(
            """
            {"frame":4325,"event":"mode_change","character":"tails","field":"on_object","from":0,"to":1}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());
        TraceEvent routineEvent = TraceEvent.parseJsonLine(
            """
            {"frame":4325,"event":"routine_change","character":"tails","from":"0x02","to":"0x04","x":"0x2360","y":"0x0318"}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());
        TraceEvent nearEvent = TraceEvent.parseJsonLine(
            """
            {"frame":4325,"event":"object_near","character":"tails","slot":24,"type":"0x06","x":"0x236E","y":"0x0320","routine":"0x04","status":"0x00"}
            """.trim(),
            new com.fasterxml.jackson.databind.ObjectMapper());

        assertInstanceOf(TraceEvent.ModeChange.class, modeEvent);
        assertInstanceOf(TraceEvent.RoutineChange.class, routineEvent);
        assertInstanceOf(TraceEvent.ObjectNear.class, nearEvent);

        TraceEvent.ModeChange mode = (TraceEvent.ModeChange) modeEvent;
        TraceEvent.RoutineChange routine = (TraceEvent.RoutineChange) routineEvent;
        TraceEvent.ObjectNear near = (TraceEvent.ObjectNear) nearEvent;

        assertEquals("tails", mode.character());
        assertEquals("on_object", mode.field());
        assertEquals("tails", routine.character());
        assertEquals((short) 0x2360, routine.x());
        assertEquals((short) 0x0318, routine.y());
        assertEquals("tails", near.character());
        assertEquals(24, near.slot());
    }

    @Test
    void latestCheckpointLookupReturnsNearestEarlierCheckpoint() throws IOException {
        Path dir = Files.createTempDirectory("s3k-trace");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "aiz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 3,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-21",
              "lua_script_version": "3.1-s3k",
              "trace_schema": 3,
              "csv_version": 4,
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000
            0001,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0002,00,0002,0000
            0002,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0003,00,0003,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"checkpoint","name":"intro_begin","actual_zone_id":null,"actual_act":null,"apparent_act":null,"game_mode":12}
            {"frame":1,"event":"zone_act_state","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            {"frame":2,"event":"checkpoint","name":"gameplay_start","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);
        TraceEvent.Checkpoint checkpoint = data.latestCheckpointAtOrBefore(2);
        TraceEvent.ZoneActState state = data.latestZoneActStateAtOrBefore(2);

        assertEquals("gameplay_start", checkpoint.name());
        assertEquals(0, state.actualZoneId());
        assertEquals(0, state.actualAct());
        assertEquals(0, state.apparentAct());
    }

    @Test
    void reportsAdvertisedCageAuxSchemasMissingFromAuxStream() throws IOException {
        Path dir = Files.createTempDirectory("s3k-trace");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-29",
              "lua_script_version": "test",
              "trace_schema": 3,
              "csv_version": 4,
              "aux_schema_extras": ["cage_state_per_frame", "cage_execution_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"checkpoint","name":"gameplay_start","actual_zone_id":3,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("cage_state_per_frame", "cage_execution_per_frame"),
                data.missingAdvertisedAuxSchemas());
    }

    @Test
    void parsesPerFrameCpuStateFollowRingDiagnostics() throws IOException {
        Path dir = Files.createTempDirectory("s2-cpu-state-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "mtz",
              "zone_id": 4,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "characters": ["sonic", "tails"],
              "main_character": "sonic",
              "sidekicks": ["tails"],
              "recording_date": "2026-06-10",
              "lua_script_version": "9.8-s2",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["cpu_state_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000,1,0050,0288,0010,FFF0,000C,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"vfc":1,"event":"cpu_state","character":"tails","interact":"0x0011","idle_timer":7,"flight_timer":299,"cpu_routine":6,"target_x":"0x0613","target_y":"0x0264","auto_fly_timer":0,"auto_jump_flag":1,"ctrl2_held":"0x08","ctrl2_pressed":"0x10","ctrl2_raw_held":"0x00","ctrl1_logical":"0x4000","pos_table_index":"0x44","delayed_index":"0x00","delayed_x":"0x0500","delayed_y":"0x0200","delayed_input":"0x0800","delayed_status":"0x08","tails_status":"0x02","tails_interact":"0x11","tails_inertia":"0x000C"}
            """);

        TraceData data = TraceData.load(dir);

        assertTrue(data.metadata().hasPerFrameCpuState());
        assertTrue(data.missingAdvertisedAuxSchemas().isEmpty());
        TraceEvent.CpuState state = data.cpuStateForFrame(0, "tails");
        assertNotNull(state);
        assertEquals(0x44, state.posTableIndex());
        assertEquals(0x00, state.delayedIndex());
        assertEquals(0x0800, state.delayedInput());
        assertEquals(0x08, state.delayedStatus());
        assertEquals(0x11, state.tailsInteract());
    }

    @Test
    void reportsAdvertisedCpuStateMissingFromAuxStream() throws IOException {
        Path dir = Files.createTempDirectory("s2-missing-cpu-state");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s2",
              "zone": "mtz",
              "zone_id": 4,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-06-10",
              "lua_script_version": "9.8-s2",
              "trace_schema": 3,
              "csv_version": 4,
              "aux_schema_extras": ["cpu_state_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"checkpoint","name":"gameplay_start","actual_zone_id":4,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("cpu_state_per_frame"), data.missingAdvertisedAuxSchemas());
    }

    @Test
    void latestAuxStateLookupsDoNotRebuildFrameIndexPerCall() throws IOException {
        Path dir = Files.createTempDirectory("trace-latest-aux-index");
        int frameCount = 10_000;
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "aiz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": %d,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-05-07",
              "lua_script_version": "test",
              "trace_schema": 3,
              "csv_version": 4,
              "rom_checksum": "test"
            }
            """.formatted(frameCount));

        StringBuilder physics = new StringBuilder(
                "frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter\n");
        StringBuilder aux = new StringBuilder();
        for (int frame = 0; frame < frameCount; frame++) {
            physics.append("%04X,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,%04X,00,%04X,0000%n"
                    .formatted(frame, frame + 1, frame + 1));
            aux.append("""
                    {"frame":%d,"event":"checkpoint","name":"cp_%d","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
                    {"frame":%d,"event":"zone_act_state","actual_zone_id":0,"actual_act":0,"apparent_act":0,"game_mode":12}
                    """.formatted(frame, frame, frame));
        }
        Files.writeString(dir.resolve("physics.csv"), physics.toString());
        Files.writeString(dir.resolve("aux_state.jsonl"), aux.toString());

        TraceData data = TraceData.load(dir);

        assertTimeoutPreemptively(Duration.ofMillis(250), () -> {
            for (int frame = 0; frame < frameCount; frame++) {
                assertEquals("cp_" + frame, data.latestCheckpointAtOrBefore(frame).name());
                assertEquals(0, data.latestZoneActStateAtOrBefore(frame).actualZoneId());
            }
        });
    }

    @Test
    void parsesS3kSidekickDiagnosticAuxEventsAndMetadataFlags() throws IOException {
        Path dir = Files.createTempDirectory("s3k-sidekick-diag-trace");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-29",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["tails_cpu_normal_step_per_frame", "sidekick_interact_object_per_frame", "sonic_record_pos_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000,1,0050,0288,0010,FFF0,000C,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"vfc":1,"event":"tails_cpu_normal_step","character":"tails","status":"0x00","object_control":"0x00","ground_vel":"0x000C","x_vel":"0x0000","delayed_stat":"0x08","delayed_input":"0x0800","loc_13dd0_branch":"leader_on_object","ctrl2_logical":"0x0808","ctrl2_held_logical":"0x08","path_pre_ground_vel":"0x000C","path_pre_x_vel":"0x0000","path_pre_status":"0x00","path_post_ground_vel":"0x000C","path_post_x_vel":"0x000C","path_post_status":"0x00"}
            {"frame":0,"vfc":1,"event":"sidekick_interact_object","character":"tails","interact":"0xB128","interact_slot":4,"tails_render_flags":"0x80","tails_object_control":"0x03","tails_status":"0x08","tails_on_object":true,"object_code":"0x000220C2","object_routine":"0x02","object_status":"0x10","object_x":"0x2D95","object_y":"0x0420","object_subtype":"0x40","object_render_flags":"0x80","object_object_control":"0x00","object_active":true,"object_destroyed":false,"object_p1_standing":false,"object_p2_standing":true}
            {"frame":0,"vfc":1,"event":"sonic_record_pos","hits":[{"pc":"0x10D80","pos_table_index":"0x3C","ctrl1_logical":"0x4000","ctrl1_locked":0,"ctrl1_raw":"0x0404","object_control":"0x00","status":"0x07","status_secondary":"0x11","x":"0x49EE","y":"0x01A7"}]}
            """);

        TraceData data = TraceData.load(dir);

        assertTrue(data.metadata().hasPerFrameTailsCpuNormalStep());
        assertTrue(data.metadata().hasPerFrameSidekickInteractObject());
        assertTrue(data.metadata().hasPerFrameSonicRecordPos());
        TraceEvent.TailsCpuNormalStep cpu = data.tailsCpuNormalStepForFrame(0, "tails");
        assertNotNull(cpu);
        assertEquals(0x000C, cpu.pathPostGroundVel());
        assertEquals("leader_on_object", cpu.loc13dd0Branch());
        TraceEvent.SidekickInteractObjectState interact = data.sidekickInteractObjectStateForFrame(0, "tails");
        assertNotNull(interact);
        assertEquals(4, interact.interactSlot());
        assertEquals(0x000220C2, interact.objectCode());
        assertTrue(interact.objectP2Standing());
        TraceEvent.SonicRecordPos recordPos = data.sonicRecordPosForFrame(0);
        assertNotNull(recordPos);
        assertEquals(0x3C, recordPos.hits().getFirst().posTableIndex());
        assertEquals(0x4000, recordPos.hits().getFirst().ctrl1Logical());
    }

    @Test
    void reportsAdvertisedSidekickDiagnosticsMissingFromAuxStream() throws IOException {
        Path dir = Files.createTempDirectory("s3k-missing-sidekick-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-29",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["tails_cpu_normal_step_per_frame", "sidekick_interact_object_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000,1,0050,0288,0010,FFF0,000C,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"event":"checkpoint","name":"gameplay_start","actual_zone_id":3,"actual_act":0,"apparent_act":0,"game_mode":12}
            """);

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("tails_cpu_normal_step_per_frame", "sidekick_interact_object_per_frame"),
                data.missingAdvertisedAuxSchemas());
    }

    @Test
    void parsesS3kPositionWriteDiagnosticsAndMetadataFlag() throws IOException {
        Path dir = Files.createTempDirectory("s3k-position-write-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-30",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["position_write_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000,1,7F00,0000,0000,0000,0000,00,1,0,0,0000,0000,02,02,09
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"vfc":1,"event":"position_write","character":"tails","x_pos_writes":[{"pc":"0x13ECA","val":"0x7F00"},{"pc":"0x1E1CA","val":"0x6125"}],"y_pos_writes":[{"pc":"0x13ECA","val":"0x0000"}]}
            """);

        TraceData data = TraceData.load(dir);

        assertTrue(data.metadata().hasPerFramePositionWrite());
        TraceEvent.PositionWrite write = data.positionWriteForFrame(0, "tails");
        assertNotNull(write);
        assertEquals(2, write.xPosWrites().size());
        assertEquals(0x1E1CA, write.xPosWrites().get(1).pc());
        assertEquals(0x6125, write.xPosWrites().get(1).value());
        assertEquals(0x13ECA, write.yPosWrites().getFirst().pc());
    }

    @Test
    void parsesS3kAizBoundaryDiagnosticsAndMetadataFlag() throws IOException {
        Path dir = Files.createTempDirectory("s3k-aiz-boundary-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "aiz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-29",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["aiz_boundary_state_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,2E2B,0339,0600,0000,0600,00,0,0,0,DA00,3700,02,2D8B,02E0,0049,00,0466,04,058C,0000,1,2D95,040F,0000,0000,0000,00,1,0,0,0000,3A00,06,02,27
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"vfc":1126,"event":"aiz_boundary_state","character":"tails","camera_min_x":"0x2D80","camera_max_x":"0x4000","camera_min_y":"0x0000","camera_max_y":"0x0300","tree_pre_x":"0x2D40","tree_pre_y":"0x0402","tree_pre_x_vel":"0x00F7","tree_pre_y_vel":"0x0198","tree_post_x":"0x2D95","tree_post_y":"0x040F","tree_post_x_vel":"0x0000","tree_post_y_vel":"0x0000","boundary_pre_x":"0x2D95","boundary_pre_y":"0x040F","boundary_pre_x_vel":"0x0000","boundary_pre_y_vel":"0x0000","boundary_post_x":"0x2D95","boundary_post_y":"0x040F","boundary_post_x_vel":"0x0000","boundary_post_y_vel":"0x0000","boundary_action":"none","post_move_x":"0x2D95","post_move_y":"0x040F","post_move_x_vel":"0x0000","post_move_y_vel":"0x0000"}
            """);

        TraceData data = TraceData.load(dir);

        assertTrue(data.metadata().hasPerFrameAizBoundaryState());
        TraceEvent.AizBoundaryState state = data.aizBoundaryStateForFrame(0, "tails");
        assertNotNull(state);
        assertEquals(0x2D80, state.cameraMinX());
        assertEquals((short) 0x2D95, state.treePostX());
        assertEquals("none", state.boundaryAction());
        assertEquals((short) 0x040F, state.postMoveY());
        assertTrue(data.missingAdvertisedAuxSchemas().isEmpty());
    }

    @Test
    void reportsAdvertisedAizBoundaryDiagnosticsMissingFromAuxStream() throws IOException {
        Path dir = Files.createTempDirectory("s3k-missing-aiz-boundary-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "aiz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-29",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["aiz_boundary_state_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,2E2B,0339,0600,0000,0600,00,0,0,0,DA00,3700,02,2D8B,02E0,0049,00,0466,04,058C,0000,1,2D95,040F,0000,0000,0000,00,1,0,0,0000,3A00,06,02,27
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("aiz_boundary_state_per_frame"),
                data.missingAdvertisedAuxSchemas());
    }

    @Test
    void parsesS3kAizTransitionFloorDiagnosticsAndMetadataFlag() throws IOException {
        Path dir = Files.createTempDirectory("s3k-aiz-transition-floor-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "aiz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-30",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["aiz_transition_floor_solid_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,2FCD,0379,0000,0000,0000,00,0,0,0,CA00,F700,02,2F10,02E0,0049,00,1406,04,1700,0000,1,2FB1,0380,0000,0000,0000,00,0,0,0,9A00,3200,02,08,04
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"vfc":1700,"event":"aiz_transition_floor_solid","slot":4,"object_status":"0x90","object_x":"0x2FB0","object_y":"0x03A0","p1_standing":false,"p2_standing":true,"p1_path":"first_reject","p2_path":"standing","p1_d1":"0x00A0","p1_d2":"0x0010","p1_d3":"0x0010","p1_status":"0x00","p1_object_control":"0x00","p1_y_radius":"0x13","p1_x":"0x2FCD","p1_y":"0x0379","p1_y_vel":"0x0000","p1_interact_slot":4,"p2_d1":"0x00A0","p2_d2":"0x0140","p2_d3":"0x0010","p2_status":"0x08","p2_object_control":"0x00","p2_y_radius":"0x10","p2_x":"0x2FB1","p2_y":"0x0380","p2_y_vel":"0x0000","p2_interact_slot":4}
            """);

        TraceData data = TraceData.load(dir);

        assertTrue(data.metadata().hasPerFrameAizTransitionFloorSolid());
        TraceEvent.AizTransitionFloorSolidState state =
                data.aizTransitionFloorSolidStateForFrame(0);
        assertNotNull(state);
        assertEquals(4, state.slot());
        assertTrue(state.p2Standing());
        assertEquals("first_reject", state.p1Path());
        assertEquals("standing", state.p2Path());
        assertEquals(0x00A0, state.p1D1());
        assertEquals(0x0140, state.p2D2());
        assertEquals(0x13, state.p1YRadius());
        assertEquals(0x0380, state.p2Y());
        assertTrue(data.missingAdvertisedAuxSchemas().isEmpty());
    }

    @Test
    void reportsAdvertisedAizTransitionFloorDiagnosticsMissingFromAuxStream() throws IOException {
        Path dir = Files.createTempDirectory("s3k-missing-aiz-transition-floor-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "aiz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-30",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["aiz_transition_floor_solid_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,2FCD,0379,0000,0000,0000,00,0,0,0,CA00,F700,02,2F10,02E0,0049,00,1406,04,1700,0000,1,2FB1,0380,0000,0000,0000,00,0,0,0,9A00,3200,02,08,04
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("aiz_transition_floor_solid_per_frame"),
                data.missingAdvertisedAuxSchemas());
    }

    @Test
    void parsesS3kAizHandoffTerrainDiagnosticsAndMetadataFlag() throws IOException {
        Path dir = Files.createTempDirectory("s3k-aiz-handoff-terrain-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "aiz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-30",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["aiz_handoff_terrain_state_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,2FCD,0379,0000,0000,0000,00,0,0,0,CA00,F700,02,2F10,02E0,0049,00,1406,04,1700,0000,1,2FB1,0380,0000,0000,0000,00,0,0,0,9A00,3200,02,08,04
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"vfc":1700,"event":"aiz_handoff_terrain_state","events_bg":"0x0010","draw_pos":"0x00A0","draw_rows":"0x0004","kos_modules_left":"0x00","current_zone_act":"0x0000","dynamic_resize":"0x00","object_load":"0x00","rings_manager":"0x00","p1_x":"0x2FCD","p1_y":"0x0379","p1_status":"0x00","p1_y_radius":"0x13","p1_top_solid":"0x0C","sonic_floor_seen":true,"sonic_floor_distance":"0x0000","sonic_floor_angle":"0x00","sonic_floor_probe_x":"0x2FE0","sonic_floor_probe_y":"0x038C","solid_vertical_seen":true,"solid_pre_y":"0x0379","solid_surface_y":"0x0390","solid_delta":"0x0000"}
            """);

        TraceData data = TraceData.load(dir);

        assertTrue(data.metadata().hasPerFrameAizHandoffTerrainState());
        TraceEvent.AizHandoffTerrainState state =
                data.aizHandoffTerrainStateForFrame(0);
        assertNotNull(state);
        assertEquals(0x0010, state.eventsBg());
        assertEquals(0x0004, state.drawRows());
        assertEquals(0x0379, state.p1Y());
        assertTrue(state.sonicFloorSeen());
        assertEquals(0, state.sonicFloorDistance());
        assertEquals(0x2FE0, state.sonicFloorProbeX());
        assertTrue(state.solidVerticalSeen());
        assertEquals(0x0390, state.solidSurfaceY());
        assertTrue(data.missingAdvertisedAuxSchemas().isEmpty());
    }

    @Test
    void reportsAdvertisedAizHandoffTerrainDiagnosticsMissingFromAuxStream() throws IOException {
        Path dir = Files.createTempDirectory("s3k-missing-aiz-handoff-terrain-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "aiz",
              "zone_id": 0,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-30",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["aiz_handoff_terrain_state_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,2FCD,0379,0000,0000,0000,00,0,0,0,CA00,F700,02,2F10,02E0,0049,00,1406,04,1700,0000,1,2FB1,0380,0000,0000,0000,00,0,0,0,9A00,3200,02,08,04
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("aiz_handoff_terrain_state_per_frame"),
                data.missingAdvertisedAuxSchemas());
    }

    @Test
    void parsesS3kFixedAirCountdownDiagnosticsAndMetadataFlag() throws IOException {
        Path dir = Files.createTempDirectory("s3k-air-countdown-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 2,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-05-25",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["air_countdown_state_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000,1,0050,0288,0010,FFF0,000C,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"vfc":1,"event":"air_countdown_state","owner":"p2","fixed_slot":95,"object_code":"0x00018164","routine":"0x0A","subtype":"0x81","obj30":"0x0000","obj36":"0x00","obj37":"0x01","obj38":"0xFF","obj3a":"0x0000","obj3c":"0x0032","obj3e":"0x0016","owner_ptr":"0xFFFFB04A","owner_resolved":"p2","owner_air_left":"0x18","owner_status":"0x40","owner_status_secondary":"0x00","owner_facing_left":true,"owner_underwater":true,"rng_seed":"0x89ABCDEF","visible_children":[{"slot":6,"object_code":"0x00018164","routine":"0x02","subtype":"0x06","x":"0x17A2","y":"0x0A94","x_sub":"0x0000","y_sub":"0x0000","y_vel":"0xFF00","render_flags":"0x84","anim":"0x06","mapping_frame":"0x01","anim_frame":"0x02","anim_frame_timer":"0x0E","angle":"0x40","obj34":"0x17A8","obj3c":"0x0000","parent_ptr":"0xFFFFB04A"}]}
            """);

        TraceData data = TraceData.load(dir);

        assertTrue(data.metadata().hasPerFrameAirCountdownState());
        List<TraceEvent.AirCountdownState> states = data.airCountdownStatesForFrame(0);
        assertEquals(1, states.size());
        TraceEvent.AirCountdownState state = states.getFirst();
        assertEquals("p2", state.owner());
        assertEquals(95, state.fixedSlot());
        assertEquals(0x00018164, state.objectCode());
        assertEquals(0x0032, state.obj3c());
        assertEquals(0x89ABCDEF, state.rngSeed());
        assertTrue(state.ownerUnderwater());
        assertEquals(1, state.visibleChildren().size());
        assertEquals(6, state.visibleChildren().getFirst().slot());
        assertEquals(0x17A2, state.visibleChildren().getFirst().x());
        assertEquals(0xFF00, state.visibleChildren().getFirst().yVel());
        assertEquals(0x84, state.visibleChildren().getFirst().renderFlags());
        assertEquals(0x06, state.visibleChildren().getFirst().anim());
        assertEquals(0x17A8, state.visibleChildren().getFirst().obj34());
        assertTrue(data.missingAdvertisedAuxSchemas().isEmpty());
    }

    @Test
    void parsesS3kRngCallDiagnosticsAndMetadataFlag() throws IOException {
        Path dir = Files.createTempDirectory("s3k-rng-call-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 2,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-05-25",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["rng_call_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000,1,0050,0288,0010,FFF0,000C,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), """
            {"frame":0,"vfc":1,"event":"rng_call","hits":[{"pc":"0x01D24","caller_pc":"0x031754","source":"CNZBalloon.init","seed_before":"0x12345678","seed_after":"0x89ABCDEF","result":"0x12340099","result_byte":"0x99","a0_ptr":"0xB2C0","a0_slot":9,"a0_object_code":"0x00031754","a0_routine":"0x00","a0_subtype":"0x02","a0_x":"0x10E8","a0_y":"0x06B0","a1_ptr":"0x0000","a1_slot":-1,"a1_object_code":"0x00000000","a1_routine":"0x00","a1_subtype":"0x00","a1_x":"0x0000","a1_y":"0x0000"}]}
            """);

        TraceData data = TraceData.load(dir);

        assertTrue(data.metadata().hasPerFrameRngCall());
        TraceEvent.RngCall rngCall = data.rngCallForFrame(0);
        assertNotNull(rngCall);
        assertEquals(1, rngCall.hits().size());
        TraceEvent.RngCall.Hit hit = rngCall.hits().getFirst();
        assertEquals(0x1D24, hit.pc());
        assertEquals(0x31754, hit.callerPc());
        assertEquals("CNZBalloon.init", hit.source());
        assertEquals(0x12345678, hit.seedBefore());
        assertEquals(0x89ABCDEF, hit.seedAfter());
        assertEquals(0x12340099, hit.result());
        assertEquals(0x99, hit.resultByte());
        assertEquals(9, hit.a0().slot());
        assertEquals(0x00031754, hit.a0().objectCode());
        assertEquals(0x10E8, hit.a0().x());
        assertTrue(data.missingAdvertisedAuxSchemas().isEmpty());
    }

    @Test
    void reportsAdvertisedAirCountdownDiagnosticsMissingFromAuxStream() throws IOException {
        Path dir = Files.createTempDirectory("s3k-missing-air-countdown-diag");
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 2,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-05-25",
              "lua_script_version": "test",
              "trace_schema": 5,
              "csv_version": 5,
              "aux_schema_extras": ["air_countdown_state_per_frame"],
              "rom_checksum": "test"
            }
            """);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,sidekick_status_byte,sidekick_stand_on_obj
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000,1,0050,0288,0010,FFF0,000C,08,1,0,0,8000,4000,02,0A,03
            """);
        Files.writeString(dir.resolve("aux_state.jsonl"), "");

        TraceData data = TraceData.load(dir);

        assertEquals(List.of("air_countdown_state_per_frame"),
                data.missingAdvertisedAuxSchemas());
    }

    private static void writeMinimalTraceFiles(Path dir) throws IOException {
        writeMinimalMetadata(dir);
        Files.writeString(dir.resolve("physics.csv"), """
            frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,vblank_counter,lag_counter
            0000,0000,0080,03A0,0000,0000,0000,00,0,0,0,0000,0000,02,0000,0000,0000,00,0001,00,0001,0000
            """);
    }

    private static void writeMinimalMetadata(Path dir) throws IOException {
        Files.writeString(dir.resolve("metadata.json"), """
            {
              "game": "s3k",
              "zone": "cnz",
              "zone_id": 3,
              "act": 1,
              "bk2_frame_offset": 0,
              "trace_frame_count": 1,
              "start_x": "0x0080",
              "start_y": "0x03A0",
              "recording_date": "2026-04-27",
              "lua_script_version": "test",
              "trace_schema": 3,
              "csv_version": 4,
              "rom_checksum": "test"
            }
            """);
    }

    private static void writeGzipString(Path path, String contents) throws IOException {
        try (OutputStream out = Files.newOutputStream(path);
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}

