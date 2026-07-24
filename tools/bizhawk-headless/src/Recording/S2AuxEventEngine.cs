using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S2 Lua trace recorder's aux_state.jsonl event
    /// generation (tools/bizhawk/s2_trace_recorder.lua v9.12-s2): the arm
    /// block (<see cref="EmitArmEvents"/>: frame -1 player_history_snapshot
    /// / cpu_state_snapshot / per-slot object_state_snapshot, then the
    /// arm-time zone_act_state and "gameplay_start" checkpoint), the step-1
    /// per-frame events (<see cref="ProcessFrameStart"/>: zone_act_state
    /// plus act-transition checkpoints, emitted BEFORE the CSV row), and
    /// the frame-shared events after the CSV row (<see cref="ProcessFrame"/>:
    /// character-scoped mode/routine changes, per-frame Tails cpu_state,
    /// the CNZ slot-machine diagnostic, S2-extended state snapshots incl.
    /// the hardcoded snapshot windows, the object scan with sonic+tails
    /// proximity subjects, and cursor_state).
    ///
    /// One instance carries all persistent tracker state across the
    /// recording; a reset-aware discard throws the instance away with the
    /// recording. Per recorded trace row call
    /// <see cref="ProcessFrameStart"/> before the CSV row is formatted and
    /// <see cref="ProcessFrame"/> after it. Lines are returned WITHOUT the
    /// trailing '\n'; the caller terminates every line with a single LF.
    /// </summary>
    public sealed class S2AuxEventEngine
    {
        private const int ObjectProximity = 160;
        private const int SnapshotInterval = 60;

        // Tails CPU input delay: (0x10 << 2) + 4 frames, used as a raw byte
        // offset into the 4-byte-stride Sonic history buffers (ROM
        // convention; the Lua reuses it verbatim).
        private const int CpuHistoryDelay = (0x10 << 2) + 4;

        // Hardcoded snapshot frame windows baked into the Lua recorder since
        // v9.6 (debugging leftovers). They are part of the recorder's byte
        // contract — reproduce verbatim (spec s2-trace-recorder-behavior.md
        // §7 step 7); they are recorder-output state, not a replay carve-out.
        private const int SnapshotWindow1Start = 5104;
        private const int SnapshotWindow1End = 5106;
        private const int SnapshotWindow2Start = 5995;
        private const int SnapshotWindow2End = 6005;

        /// <summary>
        /// Per-character previous state (Lua prev_character_state.sonic /
        /// .tails); all fields start 0.
        /// </summary>
        private sealed class CharacterTracker
        {
            public int Status;
            public int Routine;
            public int CtrlLock;
        }

        private readonly CharacterTracker sonic = new CharacterTracker();
        private readonly CharacterTracker tails = new CharacterTracker();
        private int prevOplScreen = -1;       // -1 so the first frame always fires
        private readonly byte[] knownObjects = new byte[S2Ram.TotalObjectSlots];

        // Arm context (§3.3): set by EmitArmEvents. startRomZoneId gates the
        // per-frame CNZ diagnostic (-1 = unarmed, never CNZ); startZoneName
        // and startAct drive act-transition checkpoint naming.
        private int startRomZoneId = -1;
        private int startAct;
        private string startZoneName = "unknown";

        // zone_act_state dedup key ("%d:%d:%d:%d:%d:%d" incl. the frame
        // number, so in practice only the frame-0 duplicate of the arm-time
        // emission is suppressed) and the once-per-name checkpoint set.
        private string lastZoneActStateKey;
        private readonly HashSet<string> emittedCheckpoints =
            new HashSet<string>();

        /// <summary>
        /// rom_joypad_to_mask (lib/oggf_trace_common.lua): directions pass
        /// through bits 0-3; any of A/B/C (bits 4-6) collapses to 0x10.
        /// Used only for the state_snapshot raw/logical input diagnostics.
        /// </summary>
        public static int RomJoypadToMask(int raw)
        {
            int mask = raw & 0x0F;
            if ((raw & 0x70) != 0)
            {
                mask |= 0x10;
            }
            return mask;
        }

        /// <summary>
        /// Emits the frame-shared aux events for trace row
        /// <paramref name="traceFrame"/> in the recorder's exact order:
        /// mode/routine changes for sonic then tails, the snapshot gate,
        /// the object scan, then cursor_state.
        /// </summary>
        public IList<string> ProcessFrame(int traceFrame, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            var lines = new List<string>();
            // The Lua re-reads vfc at the top of each helper; it is constant
            // within a frame, so one read per frame is byte-identical.
            int vfc = S2Ram.U16(host, S2Ram.FrameCount);

            // Proximity subjects use the same reads as the CSV row: Sonic's
            // block unconditionally, Tails' behind the slot-1 presence check.
            int sonicX = S2Ram.U16(host, S2Ram.PlayerBase + S2Ram.OffXPos);
            int sonicY = S2Ram.U16(host, S2Ram.PlayerBase + S2Ram.OffYPos);
            bool tailsPresent =
                S2Ram.U8(host, S2Ram.SidekickBase + S2Ram.OffId) != 0;
            int tailsX = 0;
            int tailsY = 0;
            if (tailsPresent)
            {
                tailsX = S2Ram.U16(host, S2Ram.SidekickBase + S2Ram.OffXPos);
                tailsY = S2Ram.U16(host, S2Ram.SidekickBase + S2Ram.OffYPos);
            }

            CheckModeChanges(lines, traceFrame, vfc, "sonic", S2Ram.PlayerBase,
                sonic, host);
            CheckModeChanges(lines, traceFrame, vfc, "tails", S2Ram.SidekickBase,
                tails, host);

            // Lua order: write_tails_cpu_per_frame then
            // write_cnz_slot_machine_state, between the tails
            // check_mode_changes call and the snapshot gate.
            lines.Add(FormatCpuState(traceFrame, vfc, host));
            if (startRomZoneId == S2Zones.CnzRomZoneId)
            {
                lines.Add(FormatCnzSlotMachineState(traceFrame, vfc, host));
            }

            if (IsSnapshotFrame(traceFrame))
            {
                AddStateSnapshot(lines, traceFrame, vfc, "sonic",
                    S2Ram.PlayerBase, host);
                AddStateSnapshot(lines, traceFrame, vfc, "tails",
                    S2Ram.SidekickBase, host);
            }

            ScanObjects(lines, traceFrame, vfc, sonicX, sonicY,
                tailsPresent, tailsX, tailsY, host);
            CheckCursorState(lines, traceFrame, vfc, host);
            return lines;
        }

        /// <summary>
        /// Snapshot gate: every 60th frame, plus the two hardcoded windows.
        /// </summary>
        public static bool IsSnapshotFrame(int traceFrame)
        {
            return traceFrame % SnapshotInterval == 0
                || (traceFrame >= SnapshotWindow1Start
                    && traceFrame <= SnapshotWindow1End)
                || (traceFrame >= SnapshotWindow2Start
                    && traceFrame <= SnapshotWindow2End);
        }

        /// <summary>
        /// Arm-time emission (spec §3.3 steps 4-6): stores the recording's
        /// start context, then returns, in the recorder's exact order, the
        /// pre-trace frame -1 events (player_history_snapshot,
        /// cpu_state_snapshot, one object_state_snapshot per occupied slot
        /// 1..127 ascending), the arm-time zone_act_state with frame=0
        /// (priming the dedup key so the first recorded frame does not
        /// re-emit it), and the "gameplay_start" checkpoint. game_mode is
        /// read live (0x0C by definition of the arm predicate).
        /// </summary>
        public IList<string> EmitArmEvents(
            int romZoneIdAtArm, int actAtArm, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            startRomZoneId = romZoneIdAtArm;
            startAct = actAtArm;
            startZoneName = S2Zones.ZoneName(romZoneIdAtArm);

            var lines = new List<string>();
            // Each Lua writer re-reads vfc; constant within the frame.
            int vfc = S2Ram.U16(host, S2Ram.FrameCount);
            lines.Add(FormatPlayerHistorySnapshot(vfc, host));
            lines.Add(FormatCpuStateSnapshot(vfc, host));
            // Slot 0 (Sonic) is skipped — replay hydrates the main player
            // from metadata.start_x/start_y; slot 1 (Tails) is included.
            for (int slot = 1; slot < S2Ram.TotalObjectSlots; slot++)
            {
                int addr = S2Ram.SlotAddress(slot);
                byte objId = S2Ram.U8(host, addr);
                if (objId != 0)
                {
                    lines.Add(FormatObjectStateSnapshot(
                        vfc, slot, objId, addr, host));
                }
            }

            int gameMode = S2Ram.U8(host, S2Ram.GameMode);
            int engineZoneId = S2Zones.EngineZoneId(romZoneIdAtArm);
            int apparentAct = S2Zones.ApparentAct(romZoneIdAtArm, actAtArm);
            AddZoneActState(lines, 0, romZoneIdAtArm, engineZoneId, actAtArm,
                apparentAct, gameMode);
            AddCheckpointOnce(lines, 0, "gameplay_start", romZoneIdAtArm,
                engineZoneId, actAtArm, apparentAct, gameMode);
            return lines;
        }

        /// <summary>
        /// Step-1 events for recorded row <paramref name="traceFrame"/>,
        /// emitted BEFORE the CSV row (Lua emit_current_zone_act_state):
        /// the per-frame zone_act_state (the frame number is part of the
        /// dedup key, so this emits every recorded frame except the frame-0
        /// duplicate of the arm-time emission) plus the once-per-name
        /// act-transition checkpoint when the live act differs from the
        /// armed start act. Neither event carries a vfc field.
        /// </summary>
        public IList<string> ProcessFrameStart(int traceFrame, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            var lines = new List<string>();
            int rawZoneId = S2Ram.U8(host, S2Ram.Zone);
            int engineZoneId = S2Zones.EngineZoneId(rawZoneId);
            int actualAct = S2Ram.U8(host, S2Ram.Act);
            int apparentAct = S2Zones.ApparentAct(rawZoneId, actualAct);
            int gameMode = S2Ram.U8(host, S2Ram.GameMode);
            AddZoneActState(lines, traceFrame, rawZoneId, engineZoneId,
                actualAct, apparentAct, gameMode);
            if (actualAct != startAct)
            {
                // Name uses the START zone name and the CURRENT apparent act.
                AddCheckpointOnce(lines, traceFrame,
                    "act_transition_to_" + startZoneName
                        + Dec(apparentAct + 1),
                    rawZoneId, engineZoneId, actualAct, apparentAct, gameMode);
            }
            return lines;
        }

        private void AddZoneActState(
            List<string> lines,
            int frame,
            int rawZoneId,
            int engineZoneId,
            int actualAct,
            int apparentAct,
            int gameMode)
        {
            string key = Dec(frame) + ":" + Dec(rawZoneId) + ":"
                + Dec(engineZoneId) + ":" + Dec(actualAct) + ":"
                + Dec(apparentAct) + ":" + Dec(gameMode);
            if (key == lastZoneActStateKey)
            {
                return;
            }
            lastZoneActStateKey = key;
            lines.Add("{\"frame\":" + Dec(frame)
                + ",\"event\":\"zone_act_state\",\"actual_zone_id\":"
                + Dec(rawZoneId)
                + ",\"engine_zone_id\":" + Dec(engineZoneId)
                + ",\"actual_act\":" + Dec(actualAct)
                + ",\"apparent_act\":" + Dec(apparentAct)
                + ",\"game_mode\":" + Dec(gameMode) + "}");
        }

        /// <summary>
        /// emit_checkpoint_once: each checkpoint name fires at most once per
        /// recording. The Lua's optional notes suffix is never non-empty in
        /// level scope and is not ported.
        /// </summary>
        private void AddCheckpointOnce(
            List<string> lines,
            int frame,
            string name,
            int rawZoneId,
            int engineZoneId,
            int actualAct,
            int apparentAct,
            int gameMode)
        {
            if (!emittedCheckpoints.Add(name))
            {
                return;
            }
            lines.Add("{\"frame\":" + Dec(frame)
                + ",\"event\":\"checkpoint\",\"name\":\"" + JsonEscape(name)
                + "\",\"actual_zone_id\":" + Dec(rawZoneId)
                + ",\"engine_zone_id\":" + Dec(engineZoneId)
                + ",\"actual_act\":" + Dec(actualAct)
                + ",\"apparent_act\":" + Dec(apparentAct)
                + ",\"game_mode\":" + Dec(gameMode) + "}");
        }

        /// <summary>
        /// write_player_history_snapshot: the 64-entry Sonic position and
        /// stat record buffers as decimal lists (entries stride 4 bytes).
        /// </summary>
        private static string FormatPlayerHistorySnapshot(
            int vfc, IGpgxHost host)
        {
            var xHistory = new StringBuilder();
            var yHistory = new StringBuilder();
            var inputHistory = new StringBuilder();
            var statusHistory = new StringBuilder();
            for (int i = 0; i < 64; i++)
            {
                int offset = i * 4;
                if (i > 0)
                {
                    xHistory.Append(',');
                    yHistory.Append(',');
                    inputHistory.Append(',');
                    statusHistory.Append(',');
                }
                xHistory.Append(Dec(S2Ram.U16(
                    host, S2Ram.SonicPosRecordBuf + offset)));
                yHistory.Append(Dec(S2Ram.U16(
                    host, S2Ram.SonicPosRecordBuf + offset + 2)));
                inputHistory.Append(Dec(S2Ram.U16(
                    host, S2Ram.SonicStatRecordBuf + offset)));
                statusHistory.Append(Dec(S2Ram.U8(
                    host, S2Ram.SonicStatRecordBuf + offset + 2)));
            }

            int historyPos = S2Ram.U16(host, S2Ram.SonicPosRecordIndex) & 0xFF;
            return "{\"frame\":-1,\"vfc\":" + Dec(vfc)
                + ",\"event\":\"player_history_snapshot\",\"history_pos\":"
                + Dec(historyPos)
                + ",\"x_history\":[" + xHistory
                + "],\"y_history\":[" + yHistory
                + "],\"input_history\":[" + inputHistory
                + "],\"status_history\":[" + statusHistory + "]}";
        }

        /// <summary>
        /// write_tails_cpu_snapshot: emitted unconditionally, even when
        /// Tails is absent from slot 1.
        /// </summary>
        private static string FormatCpuStateSnapshot(int vfc, IGpgxHost host)
        {
            return "{\"frame\":-1,\"vfc\":" + Dec(vfc)
                + ",\"event\":\"cpu_state_snapshot\",\"character\":\"tails\""
                + ",\"control_counter\":"
                + Dec(S2Ram.U16(host, S2Ram.TailsControlCounter))
                + ",\"respawn_counter\":"
                + Dec(S2Ram.U16(host, S2Ram.TailsRespawnCounter))
                + ",\"cpu_routine\":"
                + Dec(S2Ram.U16(host, S2Ram.TailsCpuRoutine))
                + ",\"target_x\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.TailsCpuTargetX))
                + "\",\"target_y\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.TailsCpuTargetY))
                + "\",\"interact_id\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.TailsInteractId))
                + "\",\"jumping\":"
                + Dec(S2Ram.U8(host, S2Ram.TailsCpuJumping)) + "}";
        }

        private static string FormatObjectStateSnapshot(
            int vfc, int slot, byte objId, int addr, IGpgxHost host)
        {
            return "{\"frame\":-1,\"vfc\":" + Dec(vfc)
                + ",\"event\":\"object_state_snapshot\",\"slot\":" + Dec(slot)
                + ",\"object_type\":\"0x" + Hex2(objId)
                + "\",\"fields\":" + BuildObjectFields(addr, host) + "}";
        }

        /// <summary>
        /// build_object_fields: 64 raw-byte entries "off_00".."off_3F"
        /// (uppercase-hex keys), then the semantic word/byte aliases in the
        /// Lua's exact order. The velocity aliases are s16be reads with
        /// +0x10000 on negatives — byte-identical to the raw unsigned word.
        /// </summary>
        private static string BuildObjectFields(int addr, IGpgxHost host)
        {
            var fields = new StringBuilder("{");
            for (int off = 0; off < S2Ram.ObjectSlotSize; off++)
            {
                if (off > 0)
                {
                    fields.Append(',');
                }
                fields.Append("\"off_").Append(Hex2(off))
                    .Append("\":\"0x")
                    .Append(Hex2(S2Ram.U8(host, addr + off)))
                    .Append('"');
            }
            fields.Append(",\"x_pos\":\"0x")
                .Append(Hex4(S2Ram.U16(host, addr + S2Ram.OffXPos)))
                .Append("\",\"x_sub\":\"0x")
                .Append(Hex4(S2Ram.U16(host, addr + S2Ram.OffXSub)))
                .Append("\",\"y_pos\":\"0x")
                .Append(Hex4(S2Ram.U16(host, addr + S2Ram.OffYPos)))
                .Append("\",\"y_sub\":\"0x")
                .Append(Hex4(S2Ram.U16(host, addr + S2Ram.OffYSub)))
                .Append("\",\"x_vel\":\"0x")
                .Append(Hex4(S2Ram.U16(host, addr + S2Ram.OffXVel)))
                .Append("\",\"y_vel\":\"0x")
                .Append(Hex4(S2Ram.U16(host, addr + S2Ram.OffYVel)))
                .Append("\",\"id\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffId)))
                .Append("\",\"render_flags\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffRenderFlags)))
                .Append("\",\"status\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffStatus)))
                .Append("\",\"routine\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffRoutine)))
                .Append("\",\"routine_secondary\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffRoutineSecondary)))
                .Append("\",\"mapping_frame\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffMappingFrame)))
                .Append("\",\"anim\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffAnimId)))
                .Append("\",\"anim_frame\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffAnimFrame)))
                .Append("\",\"anim_frame_timer\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffAnimFrameTimer)))
                .Append("\",\"subtype\":\"0x")
                .Append(Hex2(S2Ram.U8(host, addr + S2Ram.OffSubtype)))
                .Append("\"}");
            return fields.ToString();
        }

        /// <summary>
        /// write_tails_cpu_per_frame: emitted every recorded frame, even
        /// when Tails is absent (the slot reads then return the empty-slot
        /// bytes). The delayed index is a raw byte offset into the
        /// 4-byte-stride history buffers, and "interact" is the u8 at
        /// 0xF70E rendered through the 4-wide hex specifier. The field
        /// names deliberately do NOT match the RAM variables (idle_timer is
        /// the control counter, flight_timer the respawn counter);
        /// auto_fly_timer is a literal 0 baked into the template.
        /// </summary>
        private static string FormatCpuState(
            int traceFrame, int vfc, IGpgxHost host)
        {
            int recordIndex =
                S2Ram.U16(host, S2Ram.SonicPosRecordIndex) & 0xFF;
            int delayedIndex = (recordIndex - CpuHistoryDelay) & 0xFF;
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"cpu_state\",\"character\":\"tails\""
                + ",\"interact\":\"0x"
                + Hex4(S2Ram.U8(host, S2Ram.TailsInteractId))
                + "\",\"idle_timer\":"
                + Dec(S2Ram.U16(host, S2Ram.TailsControlCounter))
                + ",\"flight_timer\":"
                + Dec(S2Ram.U16(host, S2Ram.TailsRespawnCounter))
                + ",\"cpu_routine\":"
                + Dec(S2Ram.U16(host, S2Ram.TailsCpuRoutine))
                + ",\"target_x\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.TailsCpuTargetX))
                + "\",\"target_y\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.TailsCpuTargetY))
                + "\",\"auto_fly_timer\":0,\"auto_jump_flag\":"
                + Dec(S2Ram.U8(host, S2Ram.TailsCpuJumping))
                + ",\"ctrl2_held\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.Ctrl2Held))
                + "\",\"ctrl2_pressed\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.Ctrl2Pressed))
                + "\",\"ctrl2_raw_held\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.Ctrl2Raw))
                + "\",\"ctrl1_logical\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.Ctrl1Logical))
                + "\",\"pos_table_index\":\"0x" + Hex2(recordIndex)
                + "\",\"delayed_index\":\"0x" + Hex2(delayedIndex)
                + "\",\"delayed_x\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.SonicPosRecordBuf + delayedIndex))
                + "\",\"delayed_y\":\"0x"
                + Hex4(S2Ram.U16(
                    host, S2Ram.SonicPosRecordBuf + delayedIndex + 2))
                + "\",\"delayed_input\":\"0x"
                + Hex4(S2Ram.U16(
                    host, S2Ram.SonicStatRecordBuf + delayedIndex))
                + "\",\"delayed_status\":\"0x"
                + Hex2(S2Ram.U8(
                    host, S2Ram.SonicStatRecordBuf + delayedIndex + 2))
                + "\",\"tails_status\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SidekickBase + S2Ram.OffStatus))
                + "\",\"tails_interact\":\"0x"
                + Hex2(S2Ram.U8(
                    host, S2Ram.SidekickBase + S2Ram.OffStandOnObj))
                + "\",\"tails_inertia\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.SidekickBase + S2Ram.OffInertia))
                + "\"}";
        }

        /// <summary>
        /// write_cnz_slot_machine_state: every recorded frame when the
        /// recording ARMED in CNZ (start_rom_zone_id, not the live zone
        /// byte). Carries both vfc and the hex vbc VBlank word.
        /// </summary>
        private static string FormatCnzSlotMachineState(
            int traceFrame, int vfc, IGpgxHost host)
        {
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"vbc\":\"0x" + Hex4(S2Ram.U16(host, S2Ram.VblankWord))
                + "\",\"event\":\"cnz_slot_machine_state\",\"in_use\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.SlotMachineInUse))
                + "\",\"routine\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SlotMachineRoutine))
                + "\",\"timer\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SlotMachineTimer))
                + "\",\"index\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SlotMachineIndex))
                + "\",\"reward\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.SlotMachineReward))
                + "\",\"slot1_pos\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.SlotMachineSlot1Pos))
                + "\",\"slot1_speed\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SlotMachineSlot1Speed))
                + "\",\"slot1_routine\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SlotMachineSlot1Routine))
                + "\",\"slot2_pos\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.SlotMachineSlot2Pos))
                + "\",\"slot2_speed\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SlotMachineSlot2Speed))
                + "\",\"slot2_routine\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SlotMachineSlot2Routine))
                + "\",\"slot3_pos\":\"0x"
                + Hex4(S2Ram.U16(host, S2Ram.SlotMachineSlot3Pos))
                + "\",\"slot3_speed\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SlotMachineSlot3Speed))
                + "\",\"slot3_routine\":\"0x"
                + Hex2(S2Ram.U8(host, S2Ram.SlotMachineSlot3Routine))
                + "\"}";
        }

        /// <summary>
        /// json_escape (oggf_trace_common.lua): backslash then quote, no
        /// surrounding quotes.
        /// </summary>
        private static string JsonEscape(string value)
        {
            return (value ?? string.Empty)
                .Replace("\\", "\\\\")
                .Replace("\"", "\\\"");
        }

        private void CheckModeChanges(
            List<string> lines,
            int traceFrame,
            int vfc,
            string character,
            int baseAddress,
            CharacterTracker prev,
            IGpgxHost host)
        {
            // Absent character: zero the prev state, emit nothing.
            if (S2Ram.U8(host, baseAddress + S2Ram.OffId) == 0)
            {
                prev.Status = 0;
                prev.Routine = 0;
                prev.CtrlLock = 0;
                return;
            }

            byte status = S2Ram.U8(host, baseAddress + S2Ram.OffStatus);
            byte routine = S2Ram.U8(host, baseAddress + S2Ram.OffRoutine);

            bool wasAir = (prev.Status & S2Ram.StatusInAir) != 0;
            bool isAir = (status & S2Ram.StatusInAir) != 0;
            if (wasAir != isAir)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, character, "air", wasAir, isAir));
                AddStateSnapshot(lines, traceFrame, vfc, character,
                    baseAddress, host);
            }

            bool wasRolling = (prev.Status & S2Ram.StatusRolling) != 0;
            bool isRolling = (status & S2Ram.StatusRolling) != 0;
            if (wasRolling != isRolling)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, character, "rolling", wasRolling, isRolling));
            }

            bool wasOnObject = (prev.Status & S2Ram.StatusOnObject) != 0;
            bool isOnObject = (status & S2Ram.StatusOnObject) != 0;
            if (wasOnObject != isOnObject)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, character, "on_object",
                    wasOnObject, isOnObject));
            }

            int ctrlLock = S2Ram.U16(host, baseAddress + S2Ram.OffMoveLock);
            bool wasLocked = prev.CtrlLock > 0;
            bool isLocked = ctrlLock > 0;
            if (wasLocked != isLocked)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, character, "control_locked",
                    wasLocked, isLocked));
            }
            prev.CtrlLock = ctrlLock;

            if (routine != prev.Routine)
            {
                lines.Add(FormatRoutineChange(traceFrame, vfc, character,
                    prev.Routine, routine, status, baseAddress, host));
                if (routine == S2Ram.RoutineHurt || routine == S2Ram.RoutineDeath)
                {
                    AddStateSnapshot(lines, traceFrame, vfc, character,
                        baseAddress, host);
                }
            }
            prev.Routine = routine;
            prev.Status = status;
        }

        private static string FormatModeChange(
            int traceFrame, int vfc, string character, string field,
            bool from, bool to)
        {
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"mode_change\",\"character\":\"" + character
                + "\",\"field\":\"" + field
                + "\",\"from\":" + (from ? "1" : "0")
                + ",\"to\":" + (to ? "1" : "0") + "}";
        }

        private static string FormatRoutineChange(
            int traceFrame,
            int vfc,
            string character,
            int fromRoutine,
            int toRoutine,
            byte status,
            int baseAddress,
            IGpgxHost host)
        {
            byte standOnObj = S2Ram.U8(host, baseAddress + S2Ram.OffStandOnObj);
            ushort x = S2Ram.U16(host, baseAddress + S2Ram.OffXPos);
            ushort y = S2Ram.U16(host, baseAddress + S2Ram.OffYPos);
            short xVel = S2Ram.S16(host, baseAddress + S2Ram.OffXVel);
            short yVel = S2Ram.S16(host, baseAddress + S2Ram.OffYVel);
            short inertia = S2Ram.S16(host, baseAddress + S2Ram.OffInertia);

            string objContext = "";
            if (standOnObj > 0 && standOnObj < S2Ram.TotalObjectSlots)
            {
                int objAddr = S2Ram.SlotAddress(standOnObj);
                objContext =
                    ",\"stand_obj_slot\":" + Dec(standOnObj)
                    + ",\"stand_obj_type\":\"0x"
                    + Hex2(S2Ram.U8(host, objAddr)) + "\""
                    + ",\"stand_obj_x\":\"0x"
                    + Hex4(S2Ram.U16(host, objAddr + S2Ram.OffXPos)) + "\""
                    + ",\"stand_obj_y\":\"0x"
                    + Hex4(S2Ram.U16(host, objAddr + S2Ram.OffYPos)) + "\""
                    + ",\"stand_obj_routine\":\"0x"
                    + Hex2(S2Ram.U8(host, objAddr + S2Ram.OffRoutine)) + "\"";
            }

            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"routine_change\",\"character\":\"" + character
                + "\",\"from\":\"0x" + Hex2(fromRoutine)
                + "\",\"to\":\"0x" + Hex2(toRoutine)
                + "\",\"x\":\"0x" + Hex4(x)
                + "\",\"y\":\"0x" + Hex4(y)
                + "\",\"x_vel\":" + Dec(xVel)
                + ",\"y_vel\":" + Dec(yVel)
                + ",\"inertia\":" + Dec(inertia)
                + ",\"status\":\"0x" + Hex2(status)
                + "\",\"stand_on_obj\":" + Dec(standOnObj)
                + objContext + "}";
        }

        /// <summary>
        /// write_state_snapshot: returns without emitting when the
        /// character's id byte is 0. Both characters' snapshots embed
        /// CONTROLLER 1's raw/logical input bytes (the Lua reads
        /// 0xF604/0xF602 regardless of character).
        /// </summary>
        private static void AddStateSnapshot(
            List<string> lines,
            int traceFrame,
            int vfc,
            string character,
            int baseAddress,
            IGpgxHost host)
        {
            if (S2Ram.U8(host, baseAddress + S2Ram.OffId) == 0)
            {
                return;
            }

            int ctrlLock = S2Ram.U16(host, baseAddress + S2Ram.OffMoveLock);
            byte animId = S2Ram.U8(host, baseAddress + S2Ram.OffAnimId);
            byte status = S2Ram.U8(host, baseAddress + S2Ram.OffStatus);
            byte routine = S2Ram.U8(host, baseAddress + S2Ram.OffRoutine);
            sbyte yRadius = S2Ram.S8(host, baseAddress + S2Ram.OffRadiusY);
            sbyte xRadius = S2Ram.S8(host, baseAddress + S2Ram.OffRadiusX);
            byte topSolid = S2Ram.U8(host, baseAddress + S2Ram.OffTopSolidBit);
            byte lrbSolid = S2Ram.U8(host, baseAddress + S2Ram.OffLrbSolidBit);
            byte rawInput = S2Ram.U8(host, S2Ram.Ctrl1Raw);
            byte logicalInput = S2Ram.U8(host, S2Ram.Ctrl1Logical);

            lines.Add("{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"state_snapshot\",\"character\":\"" + character
                + "\",\"control_locked\":" + Bool(ctrlLock > 0)
                + ",\"move_lock\":\"0x" + Hex4(ctrlLock)
                + "\",\"anim_id\":" + Dec(animId)
                + ",\"status_byte\":\"0x" + Hex2(status)
                + "\",\"routine\":\"0x" + Hex2(routine)
                + "\",\"y_radius\":" + Dec(yRadius)
                + ",\"x_radius\":" + Dec(xRadius)
                + ",\"top_solid_bit\":\"0x" + Hex2(topSolid)
                + "\",\"lrb_solid_bit\":\"0x" + Hex2(lrbSolid)
                + "\",\"raw_input\":\"0x" + Hex2(rawInput)
                + "\",\"raw_input_mask\":\"0x" + Hex2(RomJoypadToMask(rawInput))
                + "\",\"logical_input\":\"0x" + Hex2(logicalInput)
                + "\",\"logical_input_mask\":\"0x"
                + Hex2(RomJoypadToMask(logicalInput))
                + "\",\"on_object\":" + Bool((status & S2Ram.StatusOnObject) != 0)
                + ",\"pushing\":" + Bool((status & S2Ram.StatusPushing) != 0)
                + ",\"underwater\":" + Bool((status & S2Ram.StatusUnderwater) != 0)
                + ",\"roll_jumping\":" + Bool((status & S2Ram.StatusRollJump) != 0)
                + "}");
        }

        /// <summary>
        /// scan_objects: slots 1..127 ascending. Per slot: appeared, removed,
        /// tornado diagnostic, then proximity against the subjects in order
        /// sonic then tails; slot_dump (dynamic slots 16..127) after the loop
        /// iff any object appeared this frame.
        /// </summary>
        private void ScanObjects(
            List<string> lines,
            int traceFrame,
            int vfc,
            int sonicX,
            int sonicY,
            bool tailsPresent,
            int tailsX,
            int tailsY,
            IGpgxHost host)
        {
            bool anyAppeared = false;

            for (int slot = 1; slot < S2Ram.TotalObjectSlots; slot++)
            {
                int addr = S2Ram.SlotAddress(slot);
                byte objId = S2Ram.U8(host, addr);
                byte prevId = knownObjects[slot];

                if (objId != 0 && objId != prevId)
                {
                    lines.Add("{\"frame\":" + Dec(traceFrame)
                        + ",\"vfc\":" + Dec(vfc)
                        + ",\"event\":\"object_appeared\",\"slot\":" + Dec(slot)
                        + ",\"object_type\":\"0x" + Hex2(objId)
                        + "\",\"x\":\"0x"
                        + Hex4(S2Ram.U16(host, addr + S2Ram.OffXPos))
                        + "\",\"y\":\"0x"
                        + Hex4(S2Ram.U16(host, addr + S2Ram.OffYPos))
                        + "\"}");
                    anyAppeared = true;
                }

                if (objId == 0 && prevId != 0)
                {
                    lines.Add("{\"frame\":" + Dec(traceFrame)
                        + ",\"vfc\":" + Dec(vfc)
                        + ",\"event\":\"object_removed\",\"slot\":" + Dec(slot)
                        + ",\"object_type\":\"0x" + Hex2(prevId) + "\"}");
                }

                if (objId != 0)
                {
                    int objX = S2Ram.U16(host, addr + S2Ram.OffXPos);
                    int objY = S2Ram.U16(host, addr + S2Ram.OffYPos);
                    byte objStatus = S2Ram.U8(host, addr + S2Ram.OffStatus);
                    byte objRoutine = S2Ram.U8(host, addr + S2Ram.OffRoutine);

                    if (objId == S2Ram.TornadoObjectId)
                    {
                        lines.Add(FormatTornadoState(traceFrame, vfc, slot,
                            addr, objX, objY, objRoutine, objStatus, host));
                    }

                    // Subject order: sonic (slot 0, always present) then
                    // tails (slot 1; self-slot excluded).
                    if (Math.Abs(objX - sonicX) <= ObjectProximity
                        && Math.Abs(objY - sonicY) <= ObjectProximity)
                    {
                        lines.Add(FormatObjectNear(traceFrame, vfc, "sonic",
                            slot, objId, objX, objY, objRoutine, objStatus));
                    }
                    if (tailsPresent && slot != 1
                        && Math.Abs(objX - tailsX) <= ObjectProximity
                        && Math.Abs(objY - tailsY) <= ObjectProximity)
                    {
                        lines.Add(FormatObjectNear(traceFrame, vfc, "tails",
                            slot, objId, objX, objY, objRoutine, objStatus));
                    }
                }

                knownObjects[slot] = objId;
            }

            if (anyAppeared)
            {
                lines.Add("{\"frame\":" + Dec(traceFrame)
                    + ",\"vfc\":" + Dec(vfc)
                    + ",\"event\":\"slot_dump\",\"slots\":" + BuildSlotDump(host)
                    + "}");
            }
        }

        private static string FormatObjectNear(
            int traceFrame, int vfc, string character, int slot, byte objId,
            int objX, int objY, byte objRoutine, byte objStatus)
        {
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"object_near\",\"character\":\"" + character
                + "\",\"slot\":" + Dec(slot)
                + ",\"type\":\"0x" + Hex2(objId)
                + "\",\"x\":\"0x" + Hex4(objX)
                + "\",\"y\":\"0x" + Hex4(objY)
                + "\",\"routine\":\"0x" + Hex2(objRoutine)
                + "\",\"status\":\"0x" + Hex2(objStatus)
                + "\"}";
        }

        /// <summary>
        /// s2_tornado_state (ObjB2, SCZ/WFZ route diagnostic): emitted
        /// unconditionally for the slot, not gated on proximity. y_vel is an
        /// UNSIGNED u16be read (unlike the CSV's signed-through-uhex speeds).
        /// </summary>
        private static string FormatTornadoState(
            int traceFrame, int vfc, int slot, int addr, int objX, int objY,
            byte objRoutine, byte objStatus, IGpgxHost host)
        {
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"s2_tornado_state\",\"slot\":" + Dec(slot)
                + ",\"x\":\"0x" + Hex4(objX)
                + "\",\"y\":\"0x" + Hex4(objY)
                + "\",\"y_sub\":\"0x"
                + Hex4(S2Ram.U16(host, addr + S2Ram.OffYSub))
                + "\",\"y_vel\":\"0x"
                + Hex4(S2Ram.U16(host, addr + S2Ram.OffYVel))
                + "\",\"routine\":\"0x" + Hex2(objRoutine)
                + "\",\"routine_secondary\":\"0x"
                + Hex2(S2Ram.U8(host, addr + S2Ram.OffRoutineSecondary))
                + "\",\"status_byte\":\"0x" + Hex2(objStatus)
                + "\",\"objoff_2e\":\"0x" + Hex2(S2Ram.U8(host, addr + 0x2E))
                + "\",\"objoff_2f\":\"0x" + Hex2(S2Ram.U8(host, addr + 0x2F))
                + "\",\"objoff_30\":\"0x" + Hex2(S2Ram.U8(host, addr + 0x30))
                + "\",\"objoff_31\":\"0x" + Hex2(S2Ram.U8(host, addr + 0x31))
                + "\"}";
        }

        private static string BuildSlotDump(IGpgxHost host)
        {
            var dump = new StringBuilder("[");
            bool first = true;
            for (int slot = S2Ram.FirstDynamicSlot;
                slot < S2Ram.TotalObjectSlots;
                slot++)
            {
                byte objId = S2Ram.U8(host, S2Ram.SlotAddress(slot));
                if (objId == 0)
                {
                    continue;
                }
                if (!first)
                {
                    dump.Append(',');
                }
                first = false;
                dump.Append('[').Append(Dec(slot))
                    .Append(",\"0x").Append(Hex2(objId)).Append("\"]");
            }
            return dump.Append(']').ToString();
        }

        private void CheckCursorState(
            List<string> lines, int traceFrame, int vfc, IGpgxHost host)
        {
            int oplScreen = S2Ram.U16(host, S2Ram.OplScreen);
            if (oplScreen == prevOplScreen)
            {
                return;
            }

            string dir = (prevOplScreen >= 0 && oplScreen < prevOplScreen)
                ? "L"
                : "R";
            lines.Add("{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"cursor_state\",\"opl_screen\":\"0x"
                + Hex4(oplScreen)
                + "\",\"fwd_ptr\":\"0x"
                + Hex8(S2Ram.U32(host, S2Ram.OplDataForward))
                + "\",\"bwd_ptr\":\"0x"
                + Hex8(S2Ram.U32(host, S2Ram.OplDataBackward))
                + "\",\"fwd_ctr\":"
                + Dec(S2Ram.U8(host, S2Ram.ObjStateForwardCounter))
                + ",\"bwd_ctr\":"
                + Dec(S2Ram.U8(host, S2Ram.ObjStateBackwardCounter))
                + ",\"dir\":\"" + dir + "\"}");
            prevOplScreen = oplScreen;
        }

        private static string Bool(bool value)
        {
            return value ? "true" : "false";
        }

        private static string Dec(long value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        private static string Hex2(int value)
        {
            return value.ToString("X2", CultureInfo.InvariantCulture);
        }

        private static string Hex4(int value)
        {
            return value.ToString("X4", CultureInfo.InvariantCulture);
        }

        private static string Hex8(uint value)
        {
            return value.ToString("X8", CultureInfo.InvariantCulture);
        }
    }
}
