package com.openggf.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.resources.QueueDiagnosticSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLoadQueueTraceComparison {

    @Test
    void parsesStrictCanonicalQueueEvent() {
        TraceEvent event = TraceEvent.parseJsonLine("""
                {"frame":7,"event":"load_queue_state","kind":"s1_nemesis_plc",
                "busy":true,"prepared":true,"active_source":1193046,
                "active_destination":837,"total_work":17,"remaining_work":9,
                "queued_fingerprints":["e1cb5d156a023180550e107c0fa41e1de038683d4cbe8c6176b3395bb4dae2fa"],
                "service_observations":[]}
                """, new ObjectMapper());

        assertInstanceOf(TraceEvent.LoadQueueState.class, event);
    }

    @Test
    void rejectsMalformedOrNonCanonicalQueueEvent() {
        assertThrows(IllegalArgumentException.class, () -> TraceEvent.parseJsonLine("""
                {"frame":7,"event":"load_queue_state","kind":"s1_nemesis_plc",
                "busy":false,"prepared":true,"active_source":1,
                "active_destination":-1,"total_work":-1,"remaining_work":-1,
                "queued_fingerprints":[],"service_observations":[]}
                """, new ObjectMapper()));
        assertThrows(IllegalArgumentException.class, () -> TraceEvent.parseJsonLine("""
                {"frame":7,"event":"load_queue_state","kind":"s1_nemesis_plc",
                "busy":true,"prepared":true,"active_source":-1,
                "active_destination":-1,"total_work":-1,"remaining_work":9,
                "queued_fingerprints":[],
                "service_observations":[{"boundary":"LEVEL_MAIN","budget":3}]}
                """, new ObjectMapper()));
    }

    @Test
    void queueMismatchBecomesOrdinaryFrameError() {
        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        TraceFrame frame = TraceFrame.of(7, 0, (short) 1, (short) 2,
                (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0);
        binder.compareFrame(frame, frame.x(), frame.y(), frame.xSpeed(),
                frame.ySpeed(), frame.gSpeed(), frame.angle(), frame.air(),
                frame.rolling(), frame.groundMode());
        TraceEvent.LoadQueueState expected = new TraceEvent.LoadQueueState(
                7, "s3k_kos_direct", true, true, 0x12233, 0xFF8000,
                -1, -1, List.of(), List.of());
        QueueDiagnosticSnapshot actual = new QueueDiagnosticSnapshot(
                QueueDiagnosticSnapshot.Kind.S3K_KOS_DIRECT,
                true, true, 0x12234, 0xFF8000, -1, -1, List.of(),
                List.of());

        binder.compareLoadQueues(7, List.of(expected), List.of(actual));

        assertTrue(binder.comparisonForFrame(7)
                .hasErrorInField("queue.s3k_kos_direct.active_source"));
    }

    @Test
    void signed68kRamDestinationComparesAtZeroTolerance() {
        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        TraceFrame frame = frame(7);
        binder.compareFrame(frame, frame.x(), frame.y(), frame.xSpeed(),
                frame.ySpeed(), frame.gSpeed(), frame.angle(), frame.air(),
                frame.rolling(), frame.groundMode());
        TraceEvent.LoadQueueState expected = new TraceEvent.LoadQueueState(
                7, "s3k_kos_direct", true, true, 0x3A566C, 0xFFFFD000,
                -1, -1, List.of(), List.of());
        QueueDiagnosticSnapshot actual = new QueueDiagnosticSnapshot(
                QueueDiagnosticSnapshot.Kind.S3K_KOS_DIRECT,
                true, true, 0x3A566C, 0xFFFFD000,
                -1, -1, List.of(), List.of());

        binder.compareLoadQueues(7, List.of(expected), List.of(actual));

        assertFalse(binder.comparisonForFrame(7).hasError());
    }

    @Test
    void advertisedCapabilityRejectsIncompleteFrameSet(@TempDir Path temp)
            throws Exception {
        Path metadataPath = temp.resolve("metadata.json");
        Files.writeString(metadataPath, """
                {"game":"s3k","zone":"aiz","act":1,"bk2_frame_offset":0,
                "trace_frame_count":1,
                "aux_schema_extras":["load_queue_state_per_frame"]}
                """);
        TraceData trace = new TraceData(
                TraceMetadata.load(metadataPath),
                List.of(TraceFrame.of(0, 0, (short) 0, (short) 0,
                        (short) 0, (short) 0, (short) 0,
                        (byte) 0, false, false, 0)),
                Map.of(0, List.of(new TraceEvent.LoadQueueState(
                        0, "s3k_kos_direct", false, false,
                        -1, -1, -1, -1, List.of(), List.of()))),
                null);

        assertThrows(IllegalArgumentException.class,
                trace::validateAdvertisedLoadQueueStates);
    }

    @Test
    void advertisedCapabilityRequiresHeartbeatOnFirstMiddleAndLastStoredRows(
            @TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("metadata.json"), """
                {"game":"s1","zone":"ghz","act":1,"bk2_frame_offset":0,
                 "trace_frame_count":3,
                 "aux_schema_extras":["load_queue_state_per_frame"]}
                """);
        TraceMetadata metadata = TraceMetadata.load(temp.resolve("metadata.json"));
        List<TraceFrame> frames = List.of(frame(0), frame(1), frame(2));
        TraceEvent.LoadQueueState idle0 = idleQueue(0);
        TraceEvent.LoadQueueState idle1 = idleQueue(1);
        TraceEvent.LoadQueueState idle2 = idleQueue(2);
        TraceData complete = new TraceData(metadata, frames,
                Map.of(0, List.of(idle0), 1, List.of(idle1), 2, List.of(idle2)),
                null);

        assertDoesNotThrow(complete::validateAdvertisedLoadQueueStates);

        TraceData missingLast = new TraceData(metadata, frames,
                Map.of(0, List.of(idle0), 1, List.of(idle1)), null);
        assertThrows(IllegalArgumentException.class,
                missingLast::validateAdvertisedLoadQueueStates);
    }

    @Test
    void acceptsSigned68kRamDestinationForPreparedS3kDirectQueue(
            @TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("metadata.json"), """
                {"game":"s3k","zone":"aiz","act":1,"bk2_frame_offset":0,
                 "trace_frame_count":1,
                 "aux_schema_extras":["load_queue_state_per_frame"]}
                """);
        TraceEvent parsedDirect = TraceEvent.parseJsonLine("""
                {"frame":0,"event":"load_queue_state",
                 "kind":"s3k_kos_direct","busy":true,"prepared":true,
                 "active_source":3823212,"active_destination":-12288,
                 "total_work":-1,"remaining_work":-1,
                 "queued_fingerprints":[],"service_observations":[]}
                """, new ObjectMapper());
        assertInstanceOf(TraceEvent.LoadQueueState.class, parsedDirect);
        TraceData trace = new TraceData(
                TraceMetadata.load(temp.resolve("metadata.json")),
                List.of(frame(0)),
                Map.of(0, List.of(
                        parsedDirect,
                        new TraceEvent.LoadQueueState(
                                0, "s3k_kos_module", false, false,
                                -1, -1, -1, -1, List.of(), List.of()))),
                null);

        assertDoesNotThrow(trace::validateAdvertisedLoadQueueStates);
    }

    @Test
    void rejectsS3kDirectDestinationsOutsideSigned68kWorkRam(
            @TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("metadata.json"), """
                {"game":"s3k","zone":"aiz","act":1,"bk2_frame_offset":0,
                 "trace_frame_count":1,
                 "aux_schema_extras":["load_queue_state_per_frame"]}
                """);
        TraceMetadata metadata = TraceMetadata.load(
                temp.resolve("metadata.json"));

        for (int destination : List.of(-65537, -1, 0, 0xD000)) {
            TraceData trace = new TraceData(metadata, List.of(frame(0)),
                    Map.of(0, List.of(
                            new TraceEvent.LoadQueueState(
                                    0, "s3k_kos_direct", true, true,
                                    0x3A566C, destination,
                                    -1, -1, List.of(), List.of()),
                            new TraceEvent.LoadQueueState(
                                    0, "s3k_kos_module", false, false,
                                    -1, -1, -1, -1,
                                    List.of(), List.of()))),
                    null);
            assertThrows(IllegalArgumentException.class,
                    trace::validateAdvertisedLoadQueueStates,
                    () -> "accepted destination " + destination);
        }
    }

    private static TraceFrame frame(int frame) {
        return TraceFrame.of(frame, 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0,
                (byte) 0, false, false, 0);
    }

    private static TraceEvent.LoadQueueState idleQueue(int frame) {
        return new TraceEvent.LoadQueueState(frame, "s1_nemesis_plc",
                false, false, -1, -1, -1, -1, List.of(), List.of());
    }
}
