using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the Lua trace recorder's per-frame aux_state.jsonl
    /// event generation (tools/bizhawk/s1_trace_recorder.lua). One instance
    /// carries all persistent tracker state across the recording; call
    /// <see cref="ProcessFrame"/> once per recorded trace row, AFTER the CSV
    /// row has been formatted for that frame. Lines are returned WITHOUT the
    /// trailing '\n'; the caller terminates every line with a single LF.
    /// </summary>
    public sealed class S1AuxEventEngine
    {
        private const int ObjectProximity = 160;
        private const int SnapshotInterval = 60;

        private int prevStatus;               // starts 0
        private int prevRoutine;              // starts 0
        private int prevCtrlLock;             // starts 0
        private int prevOplScreen = -1;       // -1 so the first frame always fires
        private readonly byte[] knownObjects = new byte[S1Ram.TotalObjectSlots];

        /// <summary>
        /// Emits all aux events for trace row <paramref name="traceFrame"/>
        /// in the recorder's exact order: mode changes + routine change,
        /// periodic snapshot, object scan (appeared/removed/obj64/near +
        /// slot_dump), then cursor_state.
        /// </summary>
        public IList<string> ProcessFrame(int traceFrame, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            var lines = new List<string>();
            int vfc = S1Ram.U16(host, S1Ram.FrameCount);
            byte status = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffStatus);
            byte routine = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffRoutine);
            ushort playerX = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffXPos);
            ushort playerY = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffYPos);

            CheckModeChanges(lines, traceFrame, vfc, status, routine, host);
            prevStatus = status;

            if (traceFrame % SnapshotInterval == 0)
            {
                lines.Add(FormatStateSnapshot(traceFrame, vfc, host));
            }

            ScanObjects(lines, traceFrame, vfc, playerX, playerY, host);
            CheckCursorState(lines, traceFrame, vfc, host);
            return lines;
        }

        private void CheckModeChanges(
            List<string> lines,
            int traceFrame,
            int vfc,
            byte status,
            byte routine,
            IGpgxHost host)
        {
            bool wasAir = (prevStatus & S1Ram.StatusInAir) != 0;
            bool isAir = (status & S1Ram.StatusInAir) != 0;
            if (wasAir != isAir)
            {
                lines.Add(FormatModeChange(traceFrame, vfc, "air", wasAir, isAir));
                lines.Add(FormatStateSnapshot(traceFrame, vfc, host));
            }

            bool wasRolling = (prevStatus & S1Ram.StatusRolling) != 0;
            bool isRolling = (status & S1Ram.StatusRolling) != 0;
            if (wasRolling != isRolling)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, "rolling", wasRolling, isRolling));
            }

            bool wasOnObject = (prevStatus & S1Ram.StatusOnObject) != 0;
            bool isOnObject = (status & S1Ram.StatusOnObject) != 0;
            if (wasOnObject != isOnObject)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, "on_object", wasOnObject, isOnObject));
            }

            int ctrlLock = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffCtrlLock);
            bool wasLocked = prevCtrlLock > 0;
            bool isLocked = ctrlLock > 0;
            if (wasLocked != isLocked)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, "control_locked", wasLocked, isLocked));
            }
            prevCtrlLock = ctrlLock;

            if (routine != prevRoutine)
            {
                lines.Add(FormatRoutineChange(
                    traceFrame, vfc, prevRoutine, routine, status, host));
                if (routine == 0x04 || routine == 0x06)
                {
                    lines.Add(FormatStateSnapshot(traceFrame, vfc, host));
                }
            }
            prevRoutine = routine;
        }

        private static string FormatModeChange(
            int traceFrame, int vfc, string field, bool from, bool to)
        {
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"mode_change\",\"field\":\"" + field
                + "\",\"from\":" + (from ? "1" : "0")
                + ",\"to\":" + (to ? "1" : "0") + "}";
        }

        private static string FormatRoutineChange(
            int traceFrame,
            int vfc,
            int fromRoutine,
            int toRoutine,
            byte status,
            IGpgxHost host)
        {
            byte standOnObj = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffStandOnObj);
            ushort sonicX = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffXPos);
            ushort sonicY = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffYPos);
            short xVel = S1Ram.S16(host, S1Ram.PlayerBase + S1Ram.OffXVel);
            short yVel = S1Ram.S16(host, S1Ram.PlayerBase + S1Ram.OffYVel);
            short inertia = S1Ram.S16(host, S1Ram.PlayerBase + S1Ram.OffInertia);

            string objContext = "";
            if (standOnObj > 0 && standOnObj < S1Ram.TotalObjectSlots)
            {
                int objAddr = S1Ram.SlotAddress(standOnObj);
                objContext =
                    ",\"stand_obj_slot\":" + Dec(standOnObj)
                    + ",\"stand_obj_type\":\"0x"
                    + Hex2(S1Ram.U8(host, objAddr)) + "\""
                    + ",\"stand_obj_x\":\"0x"
                    + Hex4(S1Ram.U16(host, objAddr + S1Ram.OffXPos)) + "\""
                    + ",\"stand_obj_y\":\"0x"
                    + Hex4(S1Ram.U16(host, objAddr + S1Ram.OffYPos)) + "\""
                    + ",\"stand_obj_routine\":\"0x"
                    + Hex2(S1Ram.U8(host, objAddr + S1Ram.OffRoutine)) + "\"";
            }

            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"routine_change\",\"from\":\"0x" + Hex2(fromRoutine)
                + "\",\"to\":\"0x" + Hex2(toRoutine)
                + "\",\"sonic_x\":\"0x" + Hex4(sonicX)
                + "\",\"sonic_y\":\"0x" + Hex4(sonicY)
                + "\",\"x_vel\":" + Dec(xVel)
                + ",\"y_vel\":" + Dec(yVel)
                + ",\"inertia\":" + Dec(inertia)
                + ",\"status\":\"0x" + Hex2(status)
                + "\",\"stand_on_obj\":" + Dec(standOnObj)
                + objContext + "}";
        }

        private static string FormatStateSnapshot(
            int traceFrame, int vfc, IGpgxHost host)
        {
            int ctrlLock = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffCtrlLock);
            byte animId = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffAnimId);
            byte status = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffStatus);
            byte routine = S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffRoutine);
            sbyte yRadius = S1Ram.S8(host, S1Ram.PlayerBase + S1Ram.OffRadiusY);
            sbyte xRadius = S1Ram.S8(host, S1Ram.PlayerBase + S1Ram.OffRadiusX);

            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"state_snapshot\",\"control_locked\":"
                + Bool(ctrlLock > 0)
                + ",\"anim_id\":" + Dec(animId)
                + ",\"status_byte\":\"0x" + Hex2(status)
                + "\",\"routine\":\"0x" + Hex2(routine)
                + "\",\"y_radius\":" + Dec(yRadius)
                + ",\"x_radius\":" + Dec(xRadius)
                + ",\"on_object\":" + Bool((status & S1Ram.StatusOnObject) != 0)
                + ",\"pushing\":" + Bool((status & S1Ram.StatusPushing) != 0)
                + ",\"underwater\":" + Bool((status & S1Ram.StatusUnderwater) != 0)
                + ",\"roll_jumping\":" + Bool((status & S1Ram.StatusRollJump) != 0)
                + "}";
        }

        private void ScanObjects(
            List<string> lines,
            int traceFrame,
            int vfc,
            int playerX,
            int playerY,
            IGpgxHost host)
        {
            bool anyAppeared = false;

            for (int slot = 1; slot < S1Ram.TotalObjectSlots; slot++)
            {
                int addr = S1Ram.SlotAddress(slot);
                byte objId = S1Ram.U8(host, addr);
                byte prevId = knownObjects[slot];

                if (objId != 0 && objId != prevId)
                {
                    lines.Add("{\"frame\":" + Dec(traceFrame)
                        + ",\"vfc\":" + Dec(vfc)
                        + ",\"event\":\"object_appeared\",\"slot\":" + Dec(slot)
                        + ",\"object_type\":\"0x" + Hex2(objId)
                        + "\",\"x\":\"0x"
                        + Hex4(S1Ram.U16(host, addr + S1Ram.OffXPos))
                        + "\",\"y\":\"0x"
                        + Hex4(S1Ram.U16(host, addr + S1Ram.OffYPos))
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
                    if (objId == 0x64)
                    {
                        lines.Add(FormatObj64State(traceFrame, vfc, slot, addr, host));
                    }
                    int objX = S1Ram.U16(host, addr + S1Ram.OffXPos);
                    int objY = S1Ram.U16(host, addr + S1Ram.OffYPos);
                    if (Math.Abs(objX - playerX) <= ObjectProximity
                        && Math.Abs(objY - playerY) <= ObjectProximity)
                    {
                        lines.Add("{\"frame\":" + Dec(traceFrame)
                            + ",\"vfc\":" + Dec(vfc)
                            + ",\"event\":\"object_near\",\"slot\":" + Dec(slot)
                            + ",\"type\":\"0x" + Hex2(objId)
                            + "\",\"x\":\"0x" + Hex4(objX)
                            + "\",\"y\":\"0x" + Hex4(objY)
                            + "\",\"routine\":\"0x"
                            + Hex2(S1Ram.U8(host, addr + S1Ram.OffRoutine))
                            + "\",\"status\":\"0x"
                            + Hex2(S1Ram.U8(host, addr + S1Ram.OffStatus))
                            + "\"}");
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

        private static string FormatObj64State(
            int traceFrame, int vfc, int slot, int addr, IGpgxHost host)
        {
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"s1_obj64_state\",\"slot\":" + Dec(slot)
                + ",\"x\":\"0x" + Hex4(S1Ram.U16(host, addr + S1Ram.OffXPos))
                + "\",\"y\":\"0x" + Hex4(S1Ram.U16(host, addr + S1Ram.OffYPos))
                + "\",\"routine\":\"0x"
                + Hex2(S1Ram.U8(host, addr + S1Ram.OffRoutine))
                + "\",\"status\":\"0x"
                + Hex2(S1Ram.U8(host, addr + S1Ram.OffStatus))
                + "\",\"render_flags\":\"0x"
                + Hex2(S1Ram.U8(host, addr + S1Ram.OffRenderFlags))
                + "\",\"subtype\":\"0x"
                + Hex2(S1Ram.U8(host, addr + S1Ram.OffSubtype))
                + "\",\"anim\":\"0x"
                + Hex2(S1Ram.U8(host, addr + S1Ram.OffAnimId))
                + "\",\"objoff_32\":\"0x" + Hex2(S1Ram.U8(host, addr + 0x32))
                + "\",\"objoff_33\":\"0x" + Hex2(S1Ram.U8(host, addr + 0x33))
                + "\",\"objoff_34\":\"0x" + Hex4(S1Ram.U16(host, addr + 0x34))
                + "\",\"objoff_36\":\"0x" + Hex4(S1Ram.U16(host, addr + 0x36))
                + "\",\"objoff_38\":\"0x" + Hex4(S1Ram.U16(host, addr + 0x38))
                + "\",\"objoff_3c\":\"0x" + Hex8(S1Ram.U32(host, addr + 0x3C))
                + "\"}";
        }

        private static string BuildSlotDump(IGpgxHost host)
        {
            var dump = new StringBuilder("[");
            bool first = true;
            for (int slot = S1Ram.FirstDynamicSlot;
                slot < S1Ram.TotalObjectSlots;
                slot++)
            {
                byte objId = S1Ram.U8(host, S1Ram.SlotAddress(slot));
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
            int oplScreen = S1Ram.U16(host, S1Ram.OplScreen);
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
                + Hex8(S1Ram.U32(host, S1Ram.OplDataForward))
                + "\",\"bwd_ptr\":\"0x"
                + Hex8(S1Ram.U32(host, S1Ram.OplDataBackward))
                + "\",\"fwd_ctr\":"
                + Dec(S1Ram.U8(host, S1Ram.ObjStateForwardCounter))
                + ",\"bwd_ctr\":"
                + Dec(S1Ram.U8(host, S1Ram.ObjStateBackwardCounter))
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
