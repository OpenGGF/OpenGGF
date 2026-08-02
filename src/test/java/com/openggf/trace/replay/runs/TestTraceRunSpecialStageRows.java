package com.openggf.trace.replay.runs;

import com.openggf.game.resources.PlcLifecyclePhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceRunSpecialStageRows {

    @Test
    void s1AdmissionsSkipLagButAlwaysPreserveVblank(@TempDir Path dir) throws Exception {
        writeMetadata(dir, "s1", "s1_special_stage", 2, true);
        Files.writeString(dir.resolve("physics.csv"), """
                frame,input,lag,x_pos,y_pos,vel_x,vel_y,inertia,status,ss_angle,ss_rotate,bg_anim,rings,emeralds
                0,00,0,00000000,00000000,0000,0000,0000,00,00,00,00,00,00
                1,00,1,00000000,00000000,0000,0000,0000,00,00,00,00,00,00
                """);

        TraceRunSpecialStageRows rows =
                TraceRunSpecialStageRows.load("s1_special_stage", dir);
        var ordinary = rows.admission(0);
        var lag = rows.admission(1);

        assertAll(
                () -> assertEquals(2, rows.rowCount()),
                () -> assertTrue(ordinary.executeGameplay()),
                () -> assertTrue(ordinary.syntheticPlcPhase().isEmpty()),
                () -> assertTrue(ordinary.advancePreservedVblankIfUnchanged()),
                () -> assertTrue(ordinary.admitHardwareTiming()),
                () -> assertFalse(lag.executeGameplay()),
                () -> assertEquals(PlcLifecyclePhase.LAG,
                        lag.syntheticPlcPhase().orElseThrow()),
                () -> assertTrue(lag.advancePreservedVblankIfUnchanged()),
                () -> assertTrue(lag.admitHardwareTiming()));
    }

    @Test
    void s2AdmissionsSkipLagWithoutS1VblankAdvance(@TempDir Path dir) throws Exception {
        writeMetadata(dir, "s2", "s2_special_stage", 2, true);
        Files.writeString(dir.resolve("physics.csv"),
                s2Header() + s2Row(0, false) + s2Row(1, true));

        TraceRunSpecialStageRows rows =
                TraceRunSpecialStageRows.load("s2_special_stage", dir);
        var ordinary = rows.admission(0);
        var lag = rows.admission(1);

        assertAll(
                () -> assertTrue(ordinary.executeGameplay()),
                () -> assertTrue(ordinary.syntheticPlcPhase().isEmpty()),
                () -> assertFalse(ordinary.advancePreservedVblankIfUnchanged()),
                () -> assertTrue(ordinary.admitHardwareTiming()),
                () -> assertFalse(lag.executeGameplay()),
                () -> assertEquals(PlcLifecyclePhase.LAG,
                        lag.syntheticPlcPhase().orElseThrow()),
                () -> assertFalse(lag.advancePreservedVblankIfUnchanged()),
                () -> assertTrue(lag.admitHardwareTiming()));
    }

    @Test
    void s3kAdmissionsPreserveRecordedLagWithGenericVblankFallback(
            @TempDir Path dir) throws Exception {
        writeMetadata(dir, "s3k", "s3k_special_stage", 1, false);
        Files.writeString(dir.resolve("physics.csv"),
                s3kHeader() + s3kRow(0, true));

        TraceRunSpecialStageRows rows =
                TraceRunSpecialStageRows.load("s3k_special_stage", dir);
        var admission = rows.admission(0);

        assertAll(
                () -> assertFalse(admission.executeGameplay()),
                () -> assertTrue(admission.syntheticPlcPhase().isEmpty()),
                () -> assertTrue(admission.advancePreservedVblankIfUnchanged()),
                () -> assertTrue(admission.admitHardwareTiming()));
    }

    @Test
    void unadvertisedS1AndS2LagRowsDoNotInventSyntheticPlcLifecycle(
            @TempDir Path root) throws Exception {
        Path s1 = Files.createDirectory(root.resolve("s1"));
        writeMetadata(s1, "s1", "s1_special_stage", 1, false);
        Files.writeString(s1.resolve("physics.csv"), """
                frame,input,lag,x_pos,y_pos,vel_x,vel_y,inertia,status,ss_angle,ss_rotate,bg_anim,rings,emeralds
                0,00,1,00000000,00000000,0000,0000,0000,00,00,00,00,00,00
                """);
        Path s2 = Files.createDirectory(root.resolve("s2"));
        writeMetadata(s2, "s2", "s2_special_stage", 1, false);
        Files.writeString(s2.resolve("physics.csv"), s2Header() + s2Row(0, true));

        var s1Admission = TraceRunSpecialStageRows
                .load("s1_special_stage", s1).admission(0);
        var s2Admission = TraceRunSpecialStageRows
                .load("s2_special_stage", s2).admission(0);

        assertAll(
                () -> assertTrue(s1Admission.syntheticPlcPhase().isEmpty()),
                () -> assertTrue(s2Admission.syntheticPlcPhase().isEmpty()));
    }

    @Test
    void rejectsUnsupportedSpecialStageProfile(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> TraceRunSpecialStageRows.load("unknown_special_stage", dir));
    }

    private static void writeMetadata(Path dir, String game, String profile, int rows,
            boolean advertisedDynamicArt) throws Exception {
        String extras = advertisedDynamicArt
                ? ",\n  \"aux_schema_extras\": [\"dynamic_art_transfer_state_per_frame_v1\"]"
                : "";
        Files.writeString(dir.resolve("metadata.json"), String.format("""
                {
                  "game": "%s",
                  "act": 0,
                  "bk2_frame_offset": 0,
                  "trace_frame_count": %d,
                  "trace_schema": 3,
                  "trace_profile": "%s",
                  "start_x": "0000",
                  "start_y": "0000"%s
                }
                """, game, rows, profile, extras));
        if (advertisedDynamicArt) {
            StringBuilder aux = new StringBuilder();
            for (int row = 0; row < rows; row++) {
                aux.append(String.format("""
                        {"frame":%d,"event":"dynamic_art_transfer_state",\
                        "edges":[],"outstanding_transfer_ids":[]}
                        """, row));
            }
            Files.writeString(dir.resolve("aux_state.jsonl"), aux);
        }
    }

    private static String s2Header() {
        return "frame,input,input_p2,lag,speed_factor,track_anim,track_anim_frame,"
                + "track_drawing_index,track_orientation,track_duration_timer,current_segment,"
                + "player_anim_frame_timer,rings_togo_bcd,check_rings_flag,tails_control_counter,"
                + "swap_positions_flag,sonic_present,sonic_ss_x,sonic_ss_x_sub,sonic_ss_y,"
                + "sonic_ss_y_sub,sonic_ss_z,sonic_angle,sonic_routine,sonic_routine_secondary,"
                + "sonic_status,sonic_anim,sonic_anim_frame,sonic_rings_bcd,sonic_hurt_timer,"
                + "sonic_slide_timer,sonic_flip_timer,tails_present,tails_ss_x,tails_ss_x_sub,"
                + "tails_ss_y,tails_ss_y_sub,tails_ss_z,tails_angle,tails_routine,"
                + "tails_routine_secondary,tails_status,tails_anim,tails_anim_frame,"
                + "tails_rings_bcd,tails_hurt_timer,tails_slide_timer,tails_flip_timer\n";
    }

    private static String s2Row(int frame, boolean lag) {
        String[] fields = new String[48];
        Arrays.fill(fields, "0");
        fields[0] = Integer.toString(frame);
        fields[3] = lag ? "1" : "0";
        return String.join(",", fields) + "\n";
    }

    private static String s3kHeader() {
        return "frame,input,input_p2,lag,anim_frame,x_pos,y_pos,angle,velocity,turning,"
                + "jumping,fade_timer,spheres_left,ring_count,rings_left,rate,rate_timer,"
                + "clear_timer,clear_routine,started\n";
    }

    private static String s3kRow(int frame, boolean lag) {
        String[] fields = new String[20];
        Arrays.fill(fields, "0");
        fields[0] = Integer.toString(frame);
        fields[3] = lag ? "1" : "0";
        return String.join(",", fields) + "\n";
    }
}
