using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S2 Lua trace recorder's frame-shared
    /// aux_state.jsonl event generation (tools/bizhawk/s2_trace_recorder.lua
    /// v9.12-s2): character-scoped mode/routine changes, S2-extended state
    /// snapshots (incl. the hardcoded snapshot windows), the object scan
    /// (appeared/removed/tornado/near/slot_dump) with sonic+tails proximity
    /// subjects, and cursor_state. The remaining per-frame events
    /// (zone_act_state/checkpoint, cpu_state, cnz_slot_machine_state) and the
    /// frame -1 pre-trace snapshots are ported in a later stage and slot into
    /// the marked positions of <see cref="ProcessFrame"/>.
    ///
    /// One instance carries all persistent tracker state across the
    /// recording; call <see cref="ProcessFrame"/> once per recorded trace
    /// row, AFTER the CSV row has been formatted for that frame. Lines are
    /// returned WITHOUT the trailing '\n'; the caller terminates every line
    /// with a single LF.
    /// </summary>
    public sealed class S2AuxEventEngine
    {
        private const int ObjectProximity = 160;
        private const int SnapshotInterval = 60;

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

            // (Lua order: cpu_state then cnz_slot_machine_state are emitted
            // here — ported in a later stage.)

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
