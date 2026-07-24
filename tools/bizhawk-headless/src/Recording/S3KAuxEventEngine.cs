using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Recorder profile selected by OGGF_S3K_TRACE_PROFILE in the Lua
    /// (tools/bizhawk/s3k_trace_recorder.lua v6.30-s3k). The profile
    /// changes arm/stop rules (runner concern), the checkpoint
    /// vocabulary, and the aiz_fire_transition profile gate (both
    /// handled here).
    /// </summary>
    public enum S3KTraceProfile
    {
        GameplayUnlock,
        AizEndToEnd,
        LevelGatedResetAware
    }

    /// <summary>
    /// Byte-exact port of the S3K standard Lua trace recorder's
    /// frame-polled aux_state.jsonl event surface
    /// (tools/bizhawk/s3k_trace_recorder.lua v6.30-s3k, spec
    /// docs/s3k-aux-events.md). Covers every family present in the three
    /// gated fixtures: the first-recorded-frame pre-trace snapshots
    /// (<see cref="EmitPreTraceSnapshots"/>), the per-frame cascade after
    /// the CSV row (<see cref="ProcessFrame"/>, exact Lua on_frame_end
    /// order), and the level-gated finalisation checkpoint
    /// (<see cref="EmitFinalization"/>).
    ///
    /// Lightweight-mode contract: the fixtures were captured with
    /// OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS unset, so no exec/memwrite hook
    /// event can ever appear; the hybrid aiz_handoff_terrain_state family
    /// still emits per in-window frame with its hook-fed fields pinned to
    /// the lightweight defaults (false / 0x0000), exactly as in the AIZ
    /// fixture. Hook-driven families (tails_cpu_normal_step,
    /// aiz_boundary_state, aiz_transition_floor_solid, cage_execution,
    /// velocity_write, position_write, aiz_ship_loop, sonic_record_pos,
    /// rng_call, cnz_cylinder_execution, solid_object_cont_entry,
    /// collision_response_list_per_frame) and the env-armed cnz_event_ram
    /// are explicitly deferred (spec §5) — they are absent from every
    /// fixture, and the runner must reject the enabling env vars loudly
    /// rather than record a silently-different stream.
    ///
    /// Frame windows are the Lua defaults (all OGGF_S3K_*_RANGE overrides
    /// unset during fixture capture). Lines are returned WITHOUT the
    /// trailing '\n'; the caller terminates every line with a single LF
    /// and flushes per line.
    /// </summary>
    public sealed class S3KAuxEventEngine
    {
        public const int ObjectProximity = 160;
        public const int SnapshotInterval = 60;

        // Default poll windows (inclusive), Lua fixture-capture values.
        public const int CnzCylinderWindowStart = 4490;
        public const int CnzCylinderWindowEnd = 4512;
        public const int AizFireWindowStart = 5200;
        public const int AizFireWindowEnd = 5600;
        public const int AizHandoffWindowStart = 5430;
        public const int AizHandoffWindowEnd = 5438;
        public const int TerrainWallWindowStart = 7549;
        public const int TerrainWallWindowEnd = 7560;
        public const int CrlWindowStart = 618;
        public const int CrlWindowEnd = 624;

        private readonly S3KTraceProfile profile;

        // P1 previous-state latches (Lua prev_status / prev_routine /
        // prev_ctrl_lock, all starting 0).
        private int prevStatus;
        private int prevRoutine;
        private int prevCtrlLock;

        // player_mode_set / control_lock_state latches (nil in Lua, so
        // the first sample always emits).
        private int? prevPlayerMode;
        private int? prevCtrl1LockedGlobal;
        private int? prevCtrl2LockedGlobal;
        private int? prevCtrl1Logical;
        private int? prevCtrl2Logical;

        // zone_act_state dedup key ("zone|act|apparent|mode" — NO frame
        // component, unlike S2) and the once-per-name checkpoint set.
        private string lastZoneActStateKey;
        private readonly HashSet<string> emittedCheckpoints =
            new HashSet<string>();

        // act-transition edge latches (nil in Lua until the first frame).
        private int? prevZoneIdForTransition;
        private int? prevActForTransition;

        // scan_objects known-object cache: full 32-bit object codes.
        private readonly uint[] knownObjects =
            new uint[S3KRam.TotalObjectSlots];

        public S3KAuxEventEngine(S3KTraceProfile profile)
        {
            this.profile = profile;
        }

        /// <summary>
        /// First-recorded-frame pre-trace one-shots, written BEFORE row
        /// 0's CSV row: cpu_state_snapshot (frame -1), then one
        /// object_state_snapshot (frame -1) per dynamic slot 3..109 whose
        /// object code is the CNZ balloon (the only code mapped by the
        /// Lua's snapshot_object_id_for_code).
        /// </summary>
        public IList<string> EmitPreTraceSnapshots(IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            var lines = new List<string>();
            lines.Add("{\"frame\":-1,\"vfc\":"
                + Dec(S3KRam.U16(host, S3KRam.FrameCount))
                + ",\"event\":\"cpu_state_snapshot\",\"character\":\"tails\""
                + ",\"control_counter\":"
                + Dec(S3KRam.U16(host, S3KRam.TailsCpuIdleTimer))
                + ",\"respawn_counter\":"
                + Dec(S3KRam.U16(host, S3KRam.TailsCpuFlightTimer))
                + ",\"cpu_routine\":"
                + Dec(S3KRam.U16(host, S3KRam.TailsCpuRoutine))
                + ",\"target_x\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.TailsCpuTargetX))
                + "\",\"target_y\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.TailsCpuTargetY))
                + "\",\"interact_id\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.TailsCpuAutoFlyTimer))
                + "\",\"jumping\":"
                + Dec(S3KRam.U8(host, S3KRam.TailsCpuAutoJumpFlag)) + "}");

            int vfc = S3KRam.U16(host, S3KRam.FrameCount);
            for (int slot = S3KRam.FirstDynamicSlot;
                slot < S3KRam.TotalObjectSlots;
                slot++)
            {
                int addr = S3KRam.SlotAddress(slot);
                uint objCode = S3KRam.U32(host, addr);
                if (objCode == S3KRam.ObjCnzBalloon)
                {
                    lines.Add("{\"frame\":-1,\"vfc\":" + Dec(vfc)
                        + ",\"event\":\"object_state_snapshot\",\"slot\":"
                        + Dec(slot)
                        + ",\"object_type\":\"0x"
                        + Hex2(S3KRam.CnzBalloonSnapshotId)
                        + "\",\"object_code\":\"0x" + Hex8(objCode)
                        + "\",\"fields\":" + BuildObjectFields(addr, host)
                        + "}");
                }
            }
            return lines;
        }

        /// <summary>
        /// The per-frame aux cascade for recorded row
        /// <paramref name="traceFrame"/>, emitted AFTER the CSV row in the
        /// Lua on_frame_end source order: semantic events (zone_act_state
        /// + checkpoints) → player_mode_set → mode/routine changes →
        /// cpu_state → aiz_handoff_terrain_state (windowed poll, hook
        /// fields pinned) → aiz_fire_transition → oscillation_state →
        /// object_state* → interact_state (sonic, tails) →
        /// sidekick_interact_object → air_countdown_state (p1, p2) →
        /// cnz_cylinder_state* → cage_state* → state_snapshot (every 60) →
        /// control_lock_state → terrain_wall_sensor →
        /// collision_response_list_end_of_frame → scan_objects.
        /// </summary>
        public IList<string> ProcessFrame(int traceFrame, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            var lines = new List<string>();
            // The Lua re-reads vfc (0xFE08) inside every writer; RAM is
            // static within on_frame_end so one read is byte-identical.
            int vfc = S3KRam.U16(host, S3KRam.FrameCount);

            EmitSemanticEvents(lines, traceFrame, host);
            EmitPlayerModeEvent(lines, traceFrame, vfc, host);

            int status = S3KRam.U8(host, S3KRam.PlayerBase + S3KRam.OffStatus);
            int routine = S3KRam.U8(host, S3KRam.PlayerBase + S3KRam.OffRoutine);
            CheckModeChanges(lines, traceFrame, vfc, status, routine, host);
            prevStatus = status;

            lines.Add(FormatCpuState(traceFrame, vfc, host));

            EmitAizHandoffTerrainState(lines, traceFrame, vfc, host);
            EmitAizFireTransition(lines, traceFrame, vfc, host);
            lines.Add(FormatOscillationState(traceFrame, vfc, host));

            int p1X = S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffXPos);
            int p1Y = S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffYPos);
            bool sidekickPresent =
                S3KRam.U32(host, S3KRam.SidekickBase) != 0;
            int p2X = 0;
            int p2Y = 0;
            if (sidekickPresent)
            {
                p2X = S3KRam.U16(host, S3KRam.SidekickBase + S3KRam.OffXPos);
                p2Y = S3KRam.U16(host, S3KRam.SidekickBase + S3KRam.OffYPos);
            }

            EmitObjectStates(lines, traceFrame, vfc, p1X, p1Y,
                sidekickPresent, p2X, p2Y, host);
            EmitInteractStates(lines, traceFrame, vfc, sidekickPresent, host);
            if (sidekickPresent)
            {
                lines.Add(FormatSidekickInteractObject(traceFrame, vfc, host));
            }
            EmitAirCountdownStates(lines, traceFrame, vfc, host);
            EmitCnzCylinderStates(lines, traceFrame, vfc, host);
            EmitCageStates(lines, traceFrame, vfc, host);

            if (traceFrame % SnapshotInterval == 0)
            {
                lines.Add(FormatStateSnapshot(traceFrame, vfc, host));
            }
            EmitControlLockState(lines, traceFrame, vfc,
                traceFrame % SnapshotInterval == 0, host);
            EmitTerrainWallSensor(lines, traceFrame, vfc, host);
            EmitCollisionResponseListEndOfFrame(lines, traceFrame, vfc, host);
            ScanObjects(lines, traceFrame, vfc, p1X, p1Y, host);
            return lines;
        }

        /// <summary>
        /// Finalisation-path checkpoint (Lua main loop, after on_frame_end
        /// sets finished): level_gated_reset_aware only, one gameplay_end
        /// with frame = the final trace_frame (one past the last recorded
        /// row) and the zone/act/apparent/game_mode read at finalize time.
        /// </summary>
        public IList<string> EmitFinalization(int traceFrame, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            var lines = new List<string>();
            if (profile == S3KTraceProfile.LevelGatedResetAware)
            {
                AddCheckpointOnce(lines, traceFrame, "gameplay_end",
                    S3KRam.U8(host, S3KRam.Zone),
                    S3KRam.U8(host, S3KRam.Act),
                    S3KRam.U8(host, S3KRam.ApparentAct),
                    S3KRam.U8(host, S3KRam.GameMode));
            }
            return lines;
        }

        /// <summary>
        /// emit_s3k_semantic_events: zone_act_state (on change of the
        /// zone/act/apparent-act/game-mode tuple; the dedup key has NO
        /// frame component) followed by the profile checkpoint set. The
        /// level-gated gameplay_start is zone-3-literal in the Lua, which
        /// is why the MGZ fixture's only checkpoint is gameplay_end —
        /// reproduce the quirk, do not generalize it.
        /// </summary>
        private void EmitSemanticEvents(
            List<string> lines, int frame, IGpgxHost host)
        {
            int zone = S3KRam.U8(host, S3KRam.Zone);
            int act = S3KRam.U8(host, S3KRam.Act);
            int apparentAct = S3KRam.U8(host, S3KRam.ApparentAct);
            int gameMode = S3KRam.U8(host, S3KRam.GameMode);
            int moveLock =
                S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffMoveLock);
            int ctrlLocked = S3KRam.U8(host, S3KRam.Ctrl1Locked);
            int eventsFg5 = S3KRam.U16(host, S3KRam.EventsFg5);
            int levelStarted = S3KRam.U8(host, S3KRam.LevelStartedFlag);

            string key = Dec(zone) + "|" + Dec(act) + "|" + Dec(apparentAct)
                + "|" + Dec(gameMode);
            if (key != lastZoneActStateKey)
            {
                lastZoneActStateKey = key;
                lines.Add("{\"frame\":" + Dec(frame)
                    + ",\"event\":\"zone_act_state\",\"actual_zone_id\":"
                    + Dec(zone)
                    + ",\"actual_act\":" + Dec(act)
                    + ",\"apparent_act\":" + Dec(apparentAct)
                    + ",\"game_mode\":" + Dec(gameMode) + "}");
            }

            if (profile == S3KTraceProfile.LevelGatedResetAware)
            {
                if (zone == 3
                    && gameMode == S3KRam.GameModeLevel
                    && levelStarted != 0
                    && moveLock == 0
                    && ctrlLocked == 0)
                {
                    AddCheckpointOnce(lines, frame, "gameplay_start",
                        zone, act, apparentAct, gameMode);
                }
                if (prevZoneIdForTransition == 3
                    && prevActForTransition == 0
                    && zone == 3
                    && act == 1)
                {
                    AddCheckpointOnce(lines, frame, "act_transition_to_cnz2",
                        zone, act, apparentAct, gameMode);
                }
            }

            prevZoneIdForTransition = zone;
            prevActForTransition = act;

            if (profile != S3KTraceProfile.AizEndToEnd)
            {
                return;
            }

            if (frame == 0)
            {
                AddCheckpointOnce(lines, frame, "intro_begin",
                    zone, act, apparentAct, gameMode);
            }
            if (levelStarted != 0 && gameMode == S3KRam.GameModeLevel
                && moveLock == 0 && ctrlLocked == 0)
            {
                AddCheckpointOnce(lines, frame, "gameplay_start",
                    zone, act, apparentAct, gameMode);
            }
            if (zone == 0 && act == 0 && eventsFg5 != 0)
            {
                AddCheckpointOnce(lines, frame, "aiz1_intro_refresh_begin",
                    zone, act, apparentAct, gameMode);
            }
            if (zone == 0 && act == 1 && apparentAct == 0)
            {
                AddCheckpointOnce(lines, frame, "aiz2_reload_resume",
                    zone, act, apparentAct, gameMode);
            }
            if (zone == 0 && act == 1 && moveLock == 0 && ctrlLocked == 0)
            {
                AddCheckpointOnce(lines, frame, "aiz2_main_gameplay",
                    zone, act, apparentAct, gameMode);
            }
            if (zone == 1 && act == 0 && moveLock == 0 && ctrlLocked == 0)
            {
                AddCheckpointOnce(lines, frame, "hcz_handoff_complete",
                    zone, act, apparentAct, gameMode);
            }
        }

        /// <summary>
        /// emit_checkpoint_once: at most once per name per recording. The
        /// Lua's optional notes suffix is nil for every emitted checkpoint
        /// in the standard recorder and is not ported.
        /// </summary>
        private void AddCheckpointOnce(
            List<string> lines, int frame, string name,
            int zone, int act, int apparentAct, int gameMode)
        {
            if (!emittedCheckpoints.Add(name))
            {
                return;
            }
            lines.Add("{\"frame\":" + Dec(frame)
                + ",\"event\":\"checkpoint\",\"name\":\"" + name
                + "\",\"actual_zone_id\":" + Dec(zone)
                + ",\"actual_act\":" + Dec(act)
                + ",\"apparent_act\":" + Dec(apparentAct)
                + ",\"game_mode\":" + Dec(gameMode) + "}");
        }

        private void EmitPlayerModeEvent(
            List<string> lines, int traceFrame, int vfc, IGpgxHost host)
        {
            int playerMode = S3KRam.U16(host, S3KRam.PlayerMode);
            if (prevPlayerMode == null || playerMode != prevPlayerMode)
            {
                lines.Add("{\"frame\":" + Dec(traceFrame)
                    + ",\"vfc\":" + Dec(vfc)
                    + ",\"event\":\"player_mode_set\",\"mode\":"
                    + Dec(playerMode) + "}");
                prevPlayerMode = playerMode;
            }
        }

        /// <summary>
        /// check_mode_changes (P1 only): fixed transition order air
        /// [+state_snapshot], rolling, on_object, control_locked, then
        /// routine_change [+state_snapshot when the new routine is hurt
        /// 0x04 or death 0x06].
        /// </summary>
        private void CheckModeChanges(
            List<string> lines, int traceFrame, int vfc,
            int status, int routine, IGpgxHost host)
        {
            bool wasAir = (prevStatus & S3KRam.StatusInAir) != 0;
            bool isAir = (status & S3KRam.StatusInAir) != 0;
            if (wasAir != isAir)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, "air", wasAir, isAir));
                lines.Add(FormatStateSnapshot(traceFrame, vfc, host));
            }

            bool wasRolling = (prevStatus & S3KRam.StatusRolling) != 0;
            bool isRolling = (status & S3KRam.StatusRolling) != 0;
            if (wasRolling != isRolling)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, "rolling", wasRolling, isRolling));
            }

            bool wasOnObj = (prevStatus & S3KRam.StatusOnObject) != 0;
            bool isOnObj = (status & S3KRam.StatusOnObject) != 0;
            if (wasOnObj != isOnObj)
            {
                lines.Add(FormatModeChange(
                    traceFrame, vfc, "on_object", wasOnObj, isOnObj));
            }

            int ctrlLock =
                S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffMoveLock);
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
                if (routine == S3KRam.RoutineHurt
                    || routine == S3KRam.RoutineDeath)
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

        /// <summary>
        /// routine_change: x_vel/y_vel/inertia are SIGNED decimal; the
        /// stood-on object context suffix appears only when the resolved
        /// stand_on_obj slot is in 1..109.
        /// </summary>
        private static string FormatRoutineChange(
            int traceFrame, int vfc, int fromRoutine, int toRoutine,
            int status, IGpgxHost host)
        {
            int standOnObj = S3KRam.ReadStandOnSlot(host, S3KRam.PlayerBase);
            int x = S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffXPos);
            int y = S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffYPos);
            short xVel = S3KRam.S16(host, S3KRam.PlayerBase + S3KRam.OffXVel);
            short yVel = S3KRam.S16(host, S3KRam.PlayerBase + S3KRam.OffYVel);
            short inertia =
                S3KRam.S16(host, S3KRam.PlayerBase + S3KRam.OffInertia);

            string objContext = "";
            if (standOnObj > 0 && standOnObj < S3KRam.TotalObjectSlots)
            {
                int objAddr = S3KRam.SlotAddress(standOnObj);
                objContext = ",\"stand_obj_slot\":" + Dec(standOnObj)
                    + ",\"stand_obj_type\":\"0x"
                    + Hex8(S3KRam.U32(host, objAddr))
                    + "\",\"stand_obj_x\":\"0x"
                    + Hex4(S3KRam.U16(host, objAddr + S3KRam.OffXPos))
                    + "\",\"stand_obj_y\":\"0x"
                    + Hex4(S3KRam.U16(host, objAddr + S3KRam.OffYPos))
                    + "\",\"stand_obj_routine\":\"0x"
                    + Hex2(S3KRam.U8(host, objAddr + S3KRam.OffRoutine))
                    + "\"";
            }

            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"routine_change\",\"from\":\"0x"
                + Hex2(fromRoutine)
                + "\",\"to\":\"0x" + Hex2(toRoutine)
                + "\",\"sonic_x\":\"0x" + Hex4(x)
                + "\",\"sonic_y\":\"0x" + Hex4(y)
                + "\",\"x_vel\":" + Dec(xVel)
                + ",\"y_vel\":" + Dec(yVel)
                + ",\"inertia\":" + Dec(inertia)
                + ",\"status\":\"0x" + Hex2(status)
                + "\",\"stand_on_obj\":" + Dec(standOnObj)
                + objContext + "}";
        }

        /// <summary>
        /// write_state_snapshot (P1 only, no character field): three
        /// trigger sites share this template — after an air mode_change,
        /// on a hurt/death routine_change, and the every-60-frames
        /// baseline. y_radius/x_radius are SIGNED (s8).
        /// </summary>
        private static string FormatStateSnapshot(
            int traceFrame, int vfc, IGpgxHost host)
        {
            int ctrlLock =
                S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffMoveLock);
            int animId = S3KRam.U8(host, S3KRam.PlayerBase + S3KRam.OffAnimId);
            int status = S3KRam.U8(host, S3KRam.PlayerBase + S3KRam.OffStatus);
            int routine =
                S3KRam.U8(host, S3KRam.PlayerBase + S3KRam.OffRoutine);
            sbyte yRadius =
                S3KRam.S8(host, S3KRam.PlayerBase + S3KRam.OffRadiusY);
            sbyte xRadius =
                S3KRam.S8(host, S3KRam.PlayerBase + S3KRam.OffRadiusX);

            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"state_snapshot\",\"control_locked\":"
                + Bool(ctrlLock > 0)
                + ",\"anim_id\":" + Dec(animId)
                + ",\"status_byte\":\"0x" + Hex2(status)
                + "\",\"routine\":\"0x" + Hex2(routine)
                + "\",\"y_radius\":" + Dec(yRadius)
                + ",\"x_radius\":" + Dec(xRadius)
                + ",\"on_object\":"
                + Bool((status & S3KRam.StatusOnObject) != 0)
                + ",\"pushing\":"
                + Bool((status & S3KRam.StatusPushing) != 0)
                + ",\"underwater\":"
                + Bool((status & S3KRam.StatusUnderwater) != 0)
                + ",\"roll_jumping\":"
                + Bool((status & S3KRam.StatusRollJump) != 0)
                + "}";
        }

        /// <summary>
        /// write_tails_cpu_per_frame: emitted every recorded frame, even
        /// when Tails is absent. The field names keep the Lua's legacy
        /// aliases (idle_timer = control counter at 0xF702, flight_timer =
        /// respawn counter at 0xF704).
        /// </summary>
        private static string FormatCpuState(
            int traceFrame, int vfc, IGpgxHost host)
        {
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"cpu_state\",\"character\":\"tails\""
                + ",\"interact\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.TailsCpuInteract))
                + "\",\"idle_timer\":"
                + Dec(S3KRam.U16(host, S3KRam.TailsCpuIdleTimer))
                + ",\"flight_timer\":"
                + Dec(S3KRam.U16(host, S3KRam.TailsCpuFlightTimer))
                + ",\"cpu_routine\":"
                + Dec(S3KRam.U16(host, S3KRam.TailsCpuRoutine))
                + ",\"target_x\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.TailsCpuTargetX))
                + "\",\"target_y\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.TailsCpuTargetY))
                + "\",\"auto_fly_timer\":"
                + Dec(S3KRam.U8(host, S3KRam.TailsCpuAutoFlyTimer))
                + ",\"auto_jump_flag\":"
                + Dec(S3KRam.U8(host, S3KRam.TailsCpuAutoJumpFlag))
                + ",\"ctrl2_held\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.Ctrl2Held))
                + "\",\"ctrl2_pressed\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.Ctrl2Pressed))
                + "\",\"pos_table_index\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.PosTableIndex))
                + "\"}";
        }

        /// <summary>
        /// V69_AIZ.flush_aiz_handoff_terrain_state: hybrid poll+hook that
        /// emits once per in-window frame (frames 5430..5438, zone 0)
        /// regardless of hook registration. In lightweight mode the
        /// hook-fed fields stay at their defaults — sonic_floor_seen /
        /// solid_vertical_seen false and every hook word 0x0000 — which is
        /// exactly what the AIZ fixture's 9 events contain.
        /// current_zone_act is the u16be read at 0xFE10 (zone&lt;&lt;8|act).
        /// </summary>
        private static void EmitAizHandoffTerrainState(
            List<string> lines, int traceFrame, int vfc, IGpgxHost host)
        {
            if (traceFrame < AizHandoffWindowStart
                || traceFrame > AizHandoffWindowEnd)
            {
                return;
            }
            if (S3KRam.U8(host, S3KRam.Zone) != 0)
            {
                return;
            }

            lines.Add("{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"aiz_handoff_terrain_state\",\"events_bg\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.EventsRoutineBg))
                + "\",\"draw_pos\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.DrawDelayedPosition))
                + "\",\"draw_rows\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.DrawDelayedRowCount))
                + "\",\"kos_modules_left\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.KosModulesLeft))
                + "\",\"current_zone_act\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.Zone))
                + "\",\"dynamic_resize\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.DynamicResizeRoutine))
                + "\",\"object_load\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.ObjectLoadRoutine))
                + "\",\"rings_manager\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.RingsManagerRoutine))
                + "\",\"p1_x\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffXPos))
                + "\",\"p1_y\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffYPos))
                + "\",\"p1_status\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.PlayerBase + S3KRam.OffStatus))
                + "\",\"p1_y_radius\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.PlayerBase + S3KRam.OffRadiusY))
                + "\",\"p1_top_solid\":\"0x"
                + Hex2(S3KRam.U8(host,
                    S3KRam.PlayerBase + S3KRam.OffTopSolidBit))
                + "\",\"sonic_floor_seen\":false"
                + ",\"sonic_floor_distance\":\"0x0000\""
                + ",\"sonic_floor_angle\":\"0x00\""
                + ",\"sonic_floor_probe_x\":\"0x0000\""
                + ",\"sonic_floor_probe_y\":\"0x0000\""
                + ",\"solid_vertical_seen\":false"
                + ",\"solid_pre_y\":\"0x0000\""
                + ",\"solid_surface_y\":\"0x0000\""
                + ",\"solid_delta\":\"0x0000\"}");
        }

        /// <summary>
        /// V628_AIZ_FIRE.write: aiz_end_to_end profile only, zone 0,
        /// frames 5200..5600 (401 events in the AIZ fixture).
        /// </summary>
        private void EmitAizFireTransition(
            List<string> lines, int traceFrame, int vfc, IGpgxHost host)
        {
            if (profile != S3KTraceProfile.AizEndToEnd)
            {
                return;
            }
            if (traceFrame < AizFireWindowStart
                || traceFrame > AizFireWindowEnd)
            {
                return;
            }
            if (S3KRam.U8(host, S3KRam.Zone) != 0)
            {
                return;
            }

            lines.Add("{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"aiz_fire_transition\",\"camera_y_bg_copy\":\"0x"
                + Hex8(S3KRam.U32(host, S3KRam.CameraYBgCopy))
                + "\",\"camera_y_bg_rounded\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.CameraYBgRounded))
                + "\",\"events_bg_00_word\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.EventsBg00))
                + "\",\"events_bg_02_word\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.EventsBg02))
                + "\",\"events_routine_bg\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.EventsRoutineBg))
                + "\",\"events_fg_5\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.EventsFg5))
                + "\",\"camera_x\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.CameraX))
                + "\",\"camera_min_x\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.CameraMinX))
                + "\",\"camera_max_x\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.CameraMaxX))
                + "\",\"player_x\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.PlayerBase + S3KRam.OffXPos))
                + "\",\"act\":\"0x"
                + Hex2(S3KRam.U8(host, S3KRam.Act))
                + "\"}");
        }

        /// <summary>
        /// write_oscillation_per_frame: vfc and level_frame_counter are
        /// the SAME 0xFE08 read; osc_table is the 0x42 Oscillating_table
        /// bytes as one concatenated uppercase-hex string.
        /// </summary>
        private static string FormatOscillationState(
            int traceFrame, int vfc, IGpgxHost host)
        {
            var table = new StringBuilder(S3KRam.OscTableSize * 2);
            for (int i = 0; i < S3KRam.OscTableSize; i++)
            {
                table.Append(Hex2(S3KRam.U8(host, S3KRam.OscTable + i)));
            }
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"oscillation_state\",\"level_frame_counter\":"
                + Dec(vfc)
                + ",\"osc_table\":\"" + table + "\"}";
        }

        /// <summary>
        /// write_object_states_per_frame: one event per OST slot 1..109
        /// with a non-zero object code within 160 px (per-axis) of P1 OR
        /// P2 (P2 arm only when the sidekick is present). Slot 1 (Tails)
        /// is itself included by the loop.
        /// </summary>
        private static void EmitObjectStates(
            List<string> lines, int traceFrame, int vfc,
            int p1X, int p1Y, bool sidekickPresent, int p2X, int p2Y,
            IGpgxHost host)
        {
            for (int slot = 1; slot < S3KRam.TotalObjectSlots; slot++)
            {
                int addr = S3KRam.SlotAddress(slot);
                uint objCode = S3KRam.U32(host, addr);
                if (objCode == 0)
                {
                    continue;
                }
                int objX = S3KRam.U16(host, addr + S3KRam.OffXPos);
                int objY = S3KRam.U16(host, addr + S3KRam.OffYPos);
                bool nearP1 = Math.Abs(objX - p1X) <= ObjectProximity
                    && Math.Abs(objY - p1Y) <= ObjectProximity;
                bool nearP2 = sidekickPresent
                    && Math.Abs(objX - p2X) <= ObjectProximity
                    && Math.Abs(objY - p2Y) <= ObjectProximity;
                if (!nearP1 && !nearP2)
                {
                    continue;
                }
                lines.Add("{\"frame\":" + Dec(traceFrame)
                    + ",\"vfc\":" + Dec(vfc)
                    + ",\"event\":\"object_state\",\"slot\":" + Dec(slot)
                    + ",\"object_code\":\"0x" + Hex8(objCode)
                    + "\",\"routine\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + S3KRam.OffRoutine))
                    + "\",\"status\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + S3KRam.OffStatus))
                    + "\",\"subtype\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + S3KRam.OffSubtype))
                    + "\",\"x\":\"0x" + Hex4(objX)
                    + "\",\"y\":\"0x" + Hex4(objY)
                    + "\",\"x_radius\":"
                    + Dec(S3KRam.U8(host, addr + S3KRam.OffRadiusX))
                    + ",\"y_radius\":"
                    + Dec(S3KRam.U8(host, addr + S3KRam.OffRadiusY))
                    + "}");
            }
        }

        /// <summary>
        /// write_interact_state_per_frame: sonic always, tails only when
        /// the sidekick slot is occupied.
        /// </summary>
        private static void EmitInteractStates(
            List<string> lines, int traceFrame, int vfc,
            bool sidekickPresent, IGpgxHost host)
        {
            lines.Add(FormatInteractState(
                traceFrame, vfc, "sonic", S3KRam.PlayerBase, host));
            if (sidekickPresent)
            {
                lines.Add(FormatInteractState(
                    traceFrame, vfc, "tails", S3KRam.SidekickBase, host));
            }
        }

        private static string FormatInteractState(
            int traceFrame, int vfc, string character, int baseAddress,
            IGpgxHost host)
        {
            int interactAddr =
                S3KRam.U16(host, baseAddress + S3KRam.OffInteract);
            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"interact_state\",\"character\":\"" + character
                + "\",\"interact\":\"0x" + Hex4(interactAddr)
                + "\",\"interact_slot\":"
                + Dec(S3KRam.InteractAddrToSlot(interactAddr))
                + ",\"status\":\"0x"
                + Hex2(S3KRam.U8(host, baseAddress + S3KRam.OffStatus))
                + "\",\"status_secondary\":\"0x"
                + Hex2(S3KRam.U8(host,
                    baseAddress + S3KRam.OffStatusSecondary))
                + "\",\"object_control\":\"0x"
                + Hex2(S3KRam.U8(host, baseAddress + S3KRam.OffObjectControl))
                + "\"}";
        }

        /// <summary>
        /// write_sidekick_interact_object_state: sidekick-present only.
        /// Object fields stay zeroed with object_destroyed:true when the
        /// interact word does not resolve to an OST slot in 1..109.
        /// </summary>
        private static string FormatSidekickInteractObject(
            int traceFrame, int vfc, IGpgxHost host)
        {
            int interactAddr =
                S3KRam.U16(host, S3KRam.SidekickBase + S3KRam.OffInteract);
            int interactSlot = S3KRam.InteractAddrToSlot(interactAddr);
            int tailsStatus =
                S3KRam.U8(host, S3KRam.SidekickBase + S3KRam.OffStatus);

            uint objectCode = 0;
            int objectRoutine = 0;
            int objectStatus = 0;
            int objectX = 0;
            int objectY = 0;
            int objectSubtype = 0;
            int objectRenderFlags = 0;
            int objectObjectControl = 0;
            bool objectActive = false;
            bool objectDestroyed = true;
            bool objectP1Standing = false;
            bool objectP2Standing = false;
            if (interactSlot > 0 && interactSlot < S3KRam.TotalObjectSlots)
            {
                int objAddr = S3KRam.SlotAddress(interactSlot);
                objectCode = S3KRam.U32(host, objAddr);
                objectRoutine = S3KRam.U8(host, objAddr + S3KRam.OffRoutine);
                objectStatus = S3KRam.U8(host, objAddr + S3KRam.OffStatus);
                objectX = S3KRam.U16(host, objAddr + S3KRam.OffXPos);
                objectY = S3KRam.U16(host, objAddr + S3KRam.OffYPos);
                objectSubtype = S3KRam.U8(host, objAddr + S3KRam.OffSubtype);
                objectRenderFlags =
                    S3KRam.U8(host, objAddr + S3KRam.OffRenderFlags);
                objectObjectControl =
                    S3KRam.U8(host, objAddr + S3KRam.OffObjectControl);
                objectActive = objectCode != 0;
                objectDestroyed = objectCode == 0;
                objectP1Standing = (objectStatus & 0x08) != 0;
                objectP2Standing = (objectStatus & 0x10) != 0;
            }

            return "{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"sidekick_interact_object\",\"character\":\"tails\""
                + ",\"interact\":\"0x" + Hex4(interactAddr)
                + "\",\"interact_slot\":" + Dec(interactSlot)
                + ",\"tails_render_flags\":\"0x"
                + Hex2(S3KRam.U8(host,
                    S3KRam.SidekickBase + S3KRam.OffRenderFlags))
                + "\",\"tails_object_control\":\"0x"
                + Hex2(S3KRam.U8(host,
                    S3KRam.SidekickBase + S3KRam.OffObjectControl))
                + "\",\"tails_invulnerability_timer\":\"0x"
                + Hex2(S3KRam.U8(host,
                    S3KRam.SidekickBase + S3KRam.OffInvulnerabilityTimer))
                + "\",\"tails_width_pixels\":\"0x"
                + Hex2(S3KRam.U8(host,
                    S3KRam.SidekickBase + S3KRam.OffWidthPixels))
                + "\",\"tails_height_pixels\":\"0x"
                + Hex2(S3KRam.U8(host,
                    S3KRam.SidekickBase + S3KRam.OffHeightPixels))
                + "\",\"camera_x_copy\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.CameraXCopy))
                + "\",\"camera_y_copy\":\"0x"
                + Hex4(S3KRam.U16(host, S3KRam.CameraYCopy))
                + "\",\"tails_status\":\"0x" + Hex2(tailsStatus)
                + "\",\"tails_on_object\":"
                + Bool((tailsStatus & S3KRam.StatusOnObject) != 0)
                + ",\"object_code\":\"0x" + Hex8(objectCode)
                + "\",\"object_routine\":\"0x" + Hex2(objectRoutine)
                + "\",\"object_status\":\"0x" + Hex2(objectStatus)
                + "\",\"object_x\":\"0x" + Hex4(objectX)
                + "\",\"object_y\":\"0x" + Hex4(objectY)
                + "\",\"object_subtype\":\"0x" + Hex2(objectSubtype)
                + "\",\"object_render_flags\":\"0x" + Hex2(objectRenderFlags)
                + "\",\"object_object_control\":\"0x"
                + Hex2(objectObjectControl)
                + "\",\"object_active\":" + Bool(objectActive)
                + ",\"object_destroyed\":" + Bool(objectDestroyed)
                + ",\"object_p1_standing\":" + Bool(objectP1Standing)
                + ",\"object_p2_standing\":" + Bool(objectP2Standing)
                + "}";
        }

        /// <summary>
        /// write_air_countdown_state_per_frame: two events per frame for
        /// the fixed Breathing_bubbles slots (94 owner p1, 95 owner p2),
        /// emitted even when the slot code is 0. visible_children scans
        /// dynamic slots 3..92 for Obj_AirCountdown children whose
        /// parent-pointer low word matches the owner, only when the
        /// owner_ptr is non-zero.
        /// </summary>
        private static void EmitAirCountdownStates(
            List<string> lines, int traceFrame, int vfc, IGpgxHost host)
        {
            uint rngSeed = S3KRam.U32(host, S3KRam.RngSeed);
            EmitAirCountdownState(lines, traceFrame, vfc, "p1",
                S3KRam.BreathingBubblesSlot, rngSeed, host);
            EmitAirCountdownState(lines, traceFrame, vfc, "p2",
                S3KRam.BreathingBubblesP2Slot, rngSeed, host);
        }

        private static void EmitAirCountdownState(
            List<string> lines, int traceFrame, int vfc, string owner,
            int fixedSlot, uint rngSeed, IGpgxHost host)
        {
            int addr = S3KRam.SlotAddress(fixedSlot);
            uint objCode = S3KRam.U32(host, addr);
            uint ownerPtr = S3KRam.U32(host, addr + S3KRam.OffParentPtr);
            int ownerAddr = (int)(ownerPtr & 0xFFFF);
            string ownerResolved = ownerAddr == S3KRam.PlayerBase
                ? "p1"
                : (ownerAddr == S3KRam.SidekickBase ? "p2" : "unknown");
            int ownerAirLeft = 0;
            int ownerStatus = 0;
            int ownerStatusSecondary = 0;
            bool ownerFacingLeft = false;
            bool ownerUnderwater = false;
            if (ownerResolved != "unknown")
            {
                ownerAirLeft = S3KRam.U8(host, ownerAddr + S3KRam.OffSubtype);
                ownerStatus = S3KRam.U8(host, ownerAddr + S3KRam.OffStatus);
                ownerStatusSecondary =
                    S3KRam.U8(host, ownerAddr + S3KRam.OffStatusSecondary);
                ownerFacingLeft =
                    (ownerStatus & S3KRam.StatusFacingLeft) != 0;
                ownerUnderwater =
                    (ownerStatus & S3KRam.StatusUnderwater) != 0;
            }

            var children = new StringBuilder();
            if (ownerPtr != 0)
            {
                int dynamicEnd =
                    S3KRam.FirstDynamicSlot + S3KRam.DynamicSlotCount;
                for (int slot = S3KRam.FirstDynamicSlot;
                    slot < dynamicEnd;
                    slot++)
                {
                    int childAddr = S3KRam.SlotAddress(slot);
                    uint childCode = S3KRam.U32(host, childAddr);
                    if (childCode != S3KRam.ObjAirCountdown)
                    {
                        continue;
                    }
                    uint childParent =
                        S3KRam.U32(host, childAddr + S3KRam.OffParentPtr);
                    if ((int)(childParent & 0xFFFF) != ownerAddr)
                    {
                        continue;
                    }
                    if (children.Length > 0)
                    {
                        children.Append(',');
                    }
                    children.Append("{\"slot\":").Append(Dec(slot))
                        .Append(",\"object_code\":\"0x").Append(Hex8(childCode))
                        .Append("\",\"routine\":\"0x")
                        .Append(Hex2(S3KRam.U8(host,
                            childAddr + S3KRam.OffRoutine)))
                        .Append("\",\"subtype\":\"0x")
                        .Append(Hex2(S3KRam.U8(host,
                            childAddr + S3KRam.OffSubtype)))
                        .Append("\",\"x\":\"0x")
                        .Append(Hex4(S3KRam.U16(host,
                            childAddr + S3KRam.OffXPos)))
                        .Append("\",\"y\":\"0x")
                        .Append(Hex4(S3KRam.U16(host,
                            childAddr + S3KRam.OffYPos)))
                        .Append("\",\"x_sub\":\"0x")
                        .Append(Hex4(S3KRam.U16(host,
                            childAddr + S3KRam.OffXSub)))
                        .Append("\",\"y_sub\":\"0x")
                        .Append(Hex4(S3KRam.U16(host,
                            childAddr + S3KRam.OffYSub)))
                        .Append("\",\"y_vel\":\"0x")
                        .Append(Hex4(S3KRam.U16(host,
                            childAddr + S3KRam.OffYVel)))
                        .Append("\",\"render_flags\":\"0x")
                        .Append(Hex2(S3KRam.U8(host,
                            childAddr + S3KRam.OffRenderFlags)))
                        .Append("\",\"anim\":\"0x")
                        .Append(Hex2(S3KRam.U8(host,
                            childAddr + S3KRam.OffAnimId)))
                        .Append("\",\"mapping_frame\":\"0x")
                        .Append(Hex2(S3KRam.U8(host,
                            childAddr + S3KRam.OffMappingFrame)))
                        .Append("\",\"anim_frame\":\"0x")
                        .Append(Hex2(S3KRam.U8(host,
                            childAddr + S3KRam.OffAnimFrame)))
                        .Append("\",\"anim_frame_timer\":\"0x")
                        .Append(Hex2(S3KRam.U8(host,
                            childAddr + S3KRam.OffAnimFrameTimer)))
                        .Append("\",\"angle\":\"0x")
                        .Append(Hex2(S3KRam.U8(host,
                            childAddr + S3KRam.OffAngle)))
                        .Append("\",\"obj34\":\"0x")
                        .Append(Hex4(S3KRam.U16(host, childAddr + 0x34)))
                        .Append("\",\"obj3c\":\"0x")
                        .Append(Hex4(S3KRam.U16(host, childAddr + 0x3C)))
                        .Append("\",\"parent_ptr\":\"0x")
                        .Append(Hex8(childParent))
                        .Append("\"}");
                }
            }

            lines.Add("{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"air_countdown_state\",\"owner\":\"" + owner
                + "\",\"fixed_slot\":" + Dec(fixedSlot)
                + ",\"object_code\":\"0x" + Hex8(objCode)
                + "\",\"routine\":\"0x"
                + Hex2(S3KRam.U8(host, addr + S3KRam.OffRoutine))
                + "\",\"subtype\":\"0x"
                + Hex2(S3KRam.U8(host, addr + S3KRam.OffSubtype))
                + "\",\"obj30\":\"0x" + Hex4(S3KRam.U16(host, addr + 0x30))
                + "\",\"obj36\":\"0x" + Hex2(S3KRam.U8(host, addr + 0x36))
                + "\",\"obj37\":\"0x" + Hex2(S3KRam.U8(host, addr + 0x37))
                + "\",\"obj38\":\"0x" + Hex2(S3KRam.U8(host, addr + 0x38))
                + "\",\"obj3a\":\"0x" + Hex4(S3KRam.U16(host, addr + 0x3A))
                + "\",\"obj3c\":\"0x" + Hex4(S3KRam.U16(host, addr + 0x3C))
                + "\",\"obj3e\":\"0x" + Hex4(S3KRam.U16(host, addr + 0x3E))
                + "\",\"owner_ptr\":\"0x" + Hex8(ownerPtr)
                + "\",\"owner_resolved\":\"" + ownerResolved
                + "\",\"owner_air_left\":\"0x" + Hex2(ownerAirLeft)
                + "\",\"owner_status\":\"0x" + Hex2(ownerStatus)
                + "\",\"owner_status_secondary\":\"0x"
                + Hex2(ownerStatusSecondary)
                + "\",\"owner_facing_left\":" + Bool(ownerFacingLeft)
                + ",\"owner_underwater\":" + Bool(ownerUnderwater)
                + ",\"rng_seed\":\"0x" + Hex8(rngSeed)
                + "\",\"visible_children\":[" + children + "]}");
        }

        /// <summary>
        /// V67_CNZ.emit_cnz_cylinder_state_per_frame: frames 4490..4512
        /// (no zone gate), one event per OST slot 0..109 holding the CNZ
        /// cylinder object code.
        /// </summary>
        private static void EmitCnzCylinderStates(
            List<string> lines, int traceFrame, int vfc, IGpgxHost host)
        {
            if (traceFrame < CnzCylinderWindowStart
                || traceFrame > CnzCylinderWindowEnd)
            {
                return;
            }
            for (int slot = 0; slot < S3KRam.TotalObjectSlots; slot++)
            {
                int addr = S3KRam.SlotAddress(slot);
                if (S3KRam.U32(host, addr) != S3KRam.ObjCnzCylinder)
                {
                    continue;
                }
                lines.Add("{\"frame\":" + Dec(traceFrame)
                    + ",\"vfc\":" + Dec(vfc)
                    + ",\"event\":\"cnz_cylinder_state\",\"slot\":" + Dec(slot)
                    + ",\"x\":\"0x"
                    + Hex4(S3KRam.U16(host, addr + S3KRam.OffXPos))
                    + "\",\"y\":\"0x"
                    + Hex4(S3KRam.U16(host, addr + S3KRam.OffYPos))
                    + "\",\"subtype\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + S3KRam.OffSubtype))
                    + "\",\"status\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + S3KRam.OffStatus))
                    + "\",\"routine\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + S3KRam.OffRoutine))
                    + "\",\"render_flags\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + S3KRam.OffRenderFlags))
                    + "\",\"p1_state\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x32))
                    + "\",\"p1_angle\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x33))
                    + "\",\"p1_distance\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x34))
                    + "\",\"p1_threshold\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x35))
                    + "\",\"p2_state\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x36))
                    + "\",\"p2_angle\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x37))
                    + "\",\"p2_distance\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x38))
                    + "\",\"p2_threshold\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x39))
                    + "\"}");
            }
        }

        /// <summary>
        /// CAGE_DIAG.emit_cage_state_per_frame: no window/zone gate; one
        /// event per OST slot 0..109 whose 32-bit code is the CNZ wire
        /// cage init (0x00033836) or per-frame (0x0003385E) pointer. Note
        /// the field order p1_phase, p1_state, p2_phase, p2_state with
        /// phases at slot offsets 0x30/0x34 and states at 0x31/0x35.
        /// </summary>
        private static void EmitCageStates(
            List<string> lines, int traceFrame, int vfc, IGpgxHost host)
        {
            for (int slot = 0; slot < S3KRam.TotalObjectSlots; slot++)
            {
                int addr = S3KRam.SlotAddress(slot);
                uint code = S3KRam.U32(host, addr);
                if (code != S3KRam.ObjCnzWireCageInit
                    && code != S3KRam.ObjCnzWireCagePerFrame)
                {
                    continue;
                }
                lines.Add("{\"frame\":" + Dec(traceFrame)
                    + ",\"vfc\":" + Dec(vfc)
                    + ",\"event\":\"cage_state\",\"slot\":" + Dec(slot)
                    + ",\"x\":\"0x"
                    + Hex4(S3KRam.U16(host, addr + S3KRam.OffXPos))
                    + "\",\"y\":\"0x"
                    + Hex4(S3KRam.U16(host, addr + S3KRam.OffYPos))
                    + "\",\"subtype\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + S3KRam.OffSubtype))
                    + "\",\"status\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + S3KRam.OffStatus))
                    + "\",\"p1_phase\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x30))
                    + "\",\"p1_state\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x31))
                    + "\",\"p2_phase\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x34))
                    + "\",\"p2_state\":\"0x"
                    + Hex2(S3KRam.U8(host, addr + 0x35))
                    + "\"}");
            }
        }

        /// <summary>
        /// write_control_lock_state: emitted when any of the four compared
        /// values changes (first sample always emits) or when the 60-frame
        /// baseline forces it.
        /// </summary>
        private void EmitControlLockState(
            List<string> lines, int traceFrame, int vfc, bool force,
            IGpgxHost host)
        {
            int locked1 = S3KRam.U8(host, S3KRam.Ctrl1Locked);
            int locked2 = S3KRam.U8(host, S3KRam.Ctrl2Locked);
            int logical1 = S3KRam.U16(host, S3KRam.Ctrl1Logical);
            int logical2 = S3KRam.U16(host, S3KRam.Ctrl2Logical);

            bool changed = force
                || prevCtrl1LockedGlobal == null
                || locked1 != prevCtrl1LockedGlobal
                || locked2 != prevCtrl2LockedGlobal
                || logical1 != prevCtrl1Logical
                || logical2 != prevCtrl2Logical;
            if (!changed)
            {
                return;
            }

            lines.Add("{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"control_lock_state\",\"ctrl1_locked\":"
                + Dec(locked1)
                + ",\"ctrl2_locked\":" + Dec(locked2)
                + ",\"ctrl1_logical\":\"0x" + Hex4(logical1)
                + "\",\"ctrl2_logical\":\"0x" + Hex4(logical2)
                + "\"}");

            prevCtrl1LockedGlobal = locked1;
            prevCtrl2LockedGlobal = locked2;
            prevCtrl1Logical = logical1;
            prevCtrl2Logical = logical2;
        }

        /// <summary>
        /// V613_AIZ_WALL.write_terrain_wall_sensor: frames 7549..7560,
        /// zone 0 only; nested sonic + tails snapshots.
        /// </summary>
        private static void EmitTerrainWallSensor(
            List<string> lines, int traceFrame, int vfc, IGpgxHost host)
        {
            if (traceFrame < TerrainWallWindowStart
                || traceFrame > TerrainWallWindowEnd)
            {
                return;
            }
            if (S3KRam.U8(host, S3KRam.Zone) != 0)
            {
                return;
            }
            lines.Add("{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"terrain_wall_sensor\","
                + SnapshotWallPlayer(S3KRam.PlayerBase, "sonic", host)
                + ","
                + SnapshotWallPlayer(S3KRam.SidekickBase, "tails", host)
                + "}");
        }

        private static string SnapshotWallPlayer(
            int baseAddress, string label, IGpgxHost host)
        {
            int status = S3KRam.U8(host, baseAddress + S3KRam.OffStatus);
            return "\"" + label + "\":{\"x_pos\":\"0x"
                + Hex4(S3KRam.U16(host, baseAddress + S3KRam.OffXPos))
                + "\",\"x_sub\":\"0x"
                + Hex4(S3KRam.U16(host, baseAddress + S3KRam.OffXSub))
                + "\",\"y_pos\":\"0x"
                + Hex4(S3KRam.U16(host, baseAddress + S3KRam.OffYPos))
                + "\",\"y_sub\":\"0x"
                + Hex4(S3KRam.U16(host, baseAddress + S3KRam.OffYSub))
                + "\",\"x_vel\":\"0x"
                + Hex4(S3KRam.U16(host, baseAddress + S3KRam.OffXVel))
                + "\",\"y_vel\":\"0x"
                + Hex4(S3KRam.U16(host, baseAddress + S3KRam.OffYVel))
                + "\",\"angle\":\"0x"
                + Hex2(S3KRam.U8(host, baseAddress + S3KRam.OffAngle))
                + "\",\"status\":\"0x" + Hex2(status)
                + "\",\"status2\":\"0x"
                + Hex2(S3KRam.U8(host,
                    baseAddress + S3KRam.OffStatusSecondary))
                + "\",\"object_control\":\"0x"
                + Hex2(S3KRam.U8(host,
                    baseAddress + S3KRam.OffObjectControl))
                + "\",\"x_radius\":"
                + Dec(S3KRam.U8(host, baseAddress + S3KRam.OffRadiusX))
                + ",\"y_radius\":"
                + Dec(S3KRam.U8(host, baseAddress + S3KRam.OffRadiusY))
                + ",\"top_solid_bit\":\"0x"
                + Hex2(S3KRam.U8(host, baseAddress + S3KRam.OffTopSolidBit))
                + "\",\"lrb_solid_bit\":\"0x"
                + Hex2(S3KRam.U8(host, baseAddress + S3KRam.OffLrbSolidBit))
                + "\",\"airborne\":"
                + Bool((status & S3KRam.StatusInAir) != 0)
                + "}";
        }

        /// <summary>
        /// V615_CRL end-of-frame poll fallback: zone 3, frames 618..624,
        /// one event per in-window frame even with hooks off. Count word
        /// at 0xE380 is the payload BYTE count (capped 0x7E); entries are
        /// word OST addresses from 0xE382. Entries outside the OST render
        /// slot -1 with zeroed fields; entries whose object code is a
        /// Clamer spring-child routine gain a routine_label suffix.
        /// </summary>
        private static void EmitCollisionResponseListEndOfFrame(
            List<string> lines, int traceFrame, int vfc, IGpgxHost host)
        {
            if (S3KRam.U8(host, S3KRam.Zone) != 0x03)
            {
                return;
            }
            if (traceFrame < CrlWindowStart || traceFrame > CrlWindowEnd)
            {
                return;
            }

            int countWord =
                S3KRam.U16(host, S3KRam.CollisionResponseList);
            if (countWord > S3KRam.CollisionResponseCountClamp)
            {
                countWord = S3KRam.CollisionResponseCountClamp;
            }

            var entries = new StringBuilder();
            int maxAddr = S3KRam.PlayerBase
                + (S3KRam.TotalObjectSlots * S3KRam.ObjectSlotSize);
            for (int i = 0; i < countWord / 2; i++)
            {
                int ostLo = S3KRam.U16(host,
                    S3KRam.CollisionResponseEntries + (i * 2));
                if (entries.Length > 0)
                {
                    entries.Append(',');
                }
                if (ostLo >= S3KRam.PlayerBase && ostLo < maxAddr)
                {
                    int slot = (ostLo - S3KRam.PlayerBase)
                        / S3KRam.ObjectSlotSize;
                    uint objectCode = S3KRam.U32(host, ostLo);
                    entries.Append(FormatCrlEntry(slot, ostLo, objectCode,
                        S3KRam.U8(host, ostLo + S3KRam.OffCollisionFlags),
                        S3KRam.U8(host, ostLo + S3KRam.OffCollisionProperty),
                        S3KRam.U16(host, ostLo + S3KRam.OffXPos),
                        S3KRam.U16(host, ostLo + S3KRam.OffYPos)));
                }
                else
                {
                    entries.Append(FormatCrlEntry(-1, ostLo, 0, 0, 0, 0, 0));
                }
            }

            var children = new StringBuilder();
            for (int slot = 0; slot < S3KRam.TotalObjectSlots; slot++)
            {
                int addr = S3KRam.SlotAddress(slot);
                uint code = S3KRam.U32(host, addr);
                string label = SpringChildLabel(code);
                if (label == null)
                {
                    continue;
                }
                if (children.Length > 0)
                {
                    children.Append(',');
                }
                children.Append("{\"slot\":").Append(Dec(slot))
                    .Append(",\"ost_lo\":\"0x").Append(Hex4(addr))
                    .Append("\",\"object_code\":\"0x").Append(Hex8(code))
                    .Append("\",\"routine_label\":\"").Append(label)
                    .Append("\",\"x_pos\":\"0x")
                    .Append(Hex4(S3KRam.U16(host, addr + S3KRam.OffXPos)))
                    .Append("\",\"y_pos\":\"0x")
                    .Append(Hex4(S3KRam.U16(host, addr + S3KRam.OffYPos)))
                    .Append("\",\"collision_property\":\"0x")
                    .Append(Hex2(S3KRam.U8(host,
                        addr + S3KRam.OffCollisionProperty)))
                    .Append("\",\"collision_flags\":\"0x")
                    .Append(Hex2(S3KRam.U8(host,
                        addr + S3KRam.OffCollisionFlags)))
                    .Append("\",\"cooldown_byte\":\"0x")
                    .Append(Hex2(S3KRam.U8(host,
                        addr + S3KRam.OffObjectControl)))
                    .Append("\"}");
            }

            lines.Add("{\"frame\":" + Dec(traceFrame)
                + ",\"vfc\":" + Dec(vfc)
                + ",\"event\":\"collision_response_list_end_of_frame\""
                + ",\"list_count\":" + Dec(countWord)
                + ",\"list_entries\":[" + entries
                + "],\"spring_children\":[" + children + "]}");
        }

        private static string FormatCrlEntry(
            int slot, int ostLo, uint objectCode, int collisionFlags,
            int collisionProperty, int xPos, int yPos)
        {
            string label = SpringChildLabel(objectCode);
            string labelPart = label == null
                ? ""
                : ",\"routine_label\":\"" + label + "\"";
            return "{\"slot\":" + Dec(slot)
                + ",\"ost_lo\":\"0x" + Hex4(ostLo)
                + "\",\"object_code\":\"0x" + Hex8(objectCode)
                + "\",\"collision_flags\":\"0x" + Hex2(collisionFlags)
                + "\",\"collision_property\":\"0x" + Hex2(collisionProperty)
                + "\",\"x_pos\":\"0x" + Hex4(xPos)
                + "\",\"y_pos\":\"0x" + Hex4(yPos)
                + "\"" + labelPart + "}";
        }

        private static string SpringChildLabel(uint objectCode)
        {
            if (objectCode == S3KRam.ObjClamerSpringFire)
            {
                return "loc_890AA_fire";
            }
            if (objectCode == S3KRam.ObjClamerSpringCooldown)
            {
                return "loc_890C8_cooldown";
            }
            if (objectCode == S3KRam.ObjClamerSpringReset)
            {
                return "loc_890D0_reset";
            }
            return null;
        }

        /// <summary>
        /// scan_objects: slots 1..109 against the known-object cache
        /// (full 32-bit codes; interleaved appeared/removed/near per
        /// slot); proximity for object_near is 160 px per-axis of P1
        /// only. The balloon extra (angle + base_y at slot+0x32) rides
        /// appeared and near lines. slot_dump (dynamic slots 3..92,
        /// non-zero codes) fires once after the loop iff any slot
        /// appeared this frame.
        /// </summary>
        private void ScanObjects(
            List<string> lines, int traceFrame, int vfc,
            int playerX, int playerY, IGpgxHost host)
        {
            bool anyAppeared = false;

            for (int slot = 1; slot < S3KRam.TotalObjectSlots; slot++)
            {
                int addr = S3KRam.SlotAddress(slot);
                uint objCode = S3KRam.U32(host, addr);
                uint prevCode = knownObjects[slot];

                if (objCode != 0 && objCode != prevCode)
                {
                    lines.Add("{\"frame\":" + Dec(traceFrame)
                        + ",\"vfc\":" + Dec(vfc)
                        + ",\"event\":\"object_appeared\",\"slot\":" + Dec(slot)
                        + ",\"object_type\":\"0x" + Hex8(objCode)
                        + "\",\"x\":\"0x"
                        + Hex4(S3KRam.U16(host, addr + S3KRam.OffXPos))
                        + "\",\"y\":\"0x"
                        + Hex4(S3KRam.U16(host, addr + S3KRam.OffYPos))
                        + "\"" + BalloonExtra(objCode, addr, host) + "}");
                    anyAppeared = true;
                }

                if (objCode == 0 && prevCode != 0)
                {
                    lines.Add("{\"frame\":" + Dec(traceFrame)
                        + ",\"vfc\":" + Dec(vfc)
                        + ",\"event\":\"object_removed\",\"slot\":" + Dec(slot)
                        + ",\"object_type\":\"0x" + Hex8(prevCode) + "\"}");
                }

                if (objCode != 0)
                {
                    int objX = S3KRam.U16(host, addr + S3KRam.OffXPos);
                    int objY = S3KRam.U16(host, addr + S3KRam.OffYPos);
                    if (Math.Abs(objX - playerX) <= ObjectProximity
                        && Math.Abs(objY - playerY) <= ObjectProximity)
                    {
                        lines.Add("{\"frame\":" + Dec(traceFrame)
                            + ",\"vfc\":" + Dec(vfc)
                            + ",\"event\":\"object_near\",\"slot\":" + Dec(slot)
                            + ",\"type\":\"0x" + Hex8(objCode)
                            + "\",\"x\":\"0x" + Hex4(objX)
                            + "\",\"y\":\"0x" + Hex4(objY)
                            + "\",\"routine\":\"0x"
                            + Hex2(S3KRam.U8(host, addr + S3KRam.OffRoutine))
                            + "\",\"status\":\"0x"
                            + Hex2(S3KRam.U8(host, addr + S3KRam.OffStatus))
                            + "\"" + BalloonExtra(objCode, addr, host) + "}");
                    }
                }

                knownObjects[slot] = objCode;
            }

            if (anyAppeared)
            {
                var dump = new StringBuilder();
                int dynamicEnd =
                    S3KRam.FirstDynamicSlot + S3KRam.DynamicSlotCount;
                for (int slot = S3KRam.FirstDynamicSlot;
                    slot < dynamicEnd;
                    slot++)
                {
                    uint code = S3KRam.U32(host, S3KRam.SlotAddress(slot));
                    if (code == 0)
                    {
                        continue;
                    }
                    if (dump.Length > 0)
                    {
                        dump.Append(',');
                    }
                    dump.Append('[').Append(Dec(slot))
                        .Append(",\"0x").Append(Hex8(code)).Append("\"]");
                }
                lines.Add("{\"frame\":" + Dec(traceFrame)
                    + ",\"vfc\":" + Dec(vfc)
                    + ",\"event\":\"slot_dump\",\"slots\":[" + dump + "]}");
            }
        }

        private static string BalloonExtra(
            uint objCode, int addr, IGpgxHost host)
        {
            if (objCode != S3KRam.ObjCnzBalloon)
            {
                return "";
            }
            return ",\"angle\":\"0x"
                + Hex2(S3KRam.U8(host, addr + S3KRam.OffAngle))
                + "\",\"base_y\":\"0x"
                + Hex4(S3KRam.U16(host, addr + S3KRam.OffMoveLock))
                + "\"";
        }

        /// <summary>
        /// build_object_fields: all 0x4A raw slot bytes as
        /// "off_%02X":"0x%02X" entries (uppercase-hex keys, offset
        /// order), then the semantic aliases in the Lua's exact order —
        /// note mapping_frame (0x22) precedes anim (0x20). The velocity
        /// aliases are s16be reads wrapped +0x10000, byte-identical to the
        /// raw unsigned word.
        /// </summary>
        private static string BuildObjectFields(int addr, IGpgxHost host)
        {
            var fields = new StringBuilder("{");
            for (int off = 0; off < S3KRam.ObjectSlotSize; off++)
            {
                if (off > 0)
                {
                    fields.Append(',');
                }
                fields.Append("\"off_").Append(Hex2(off))
                    .Append("\":\"0x")
                    .Append(Hex2(S3KRam.U8(host, addr + off)))
                    .Append('"');
            }
            fields.Append(",\"x_pos\":\"0x")
                .Append(Hex4(S3KRam.U16(host, addr + S3KRam.OffXPos)))
                .Append("\",\"x_sub\":\"0x")
                .Append(Hex4(S3KRam.U16(host, addr + S3KRam.OffXSub)))
                .Append("\",\"y_pos\":\"0x")
                .Append(Hex4(S3KRam.U16(host, addr + S3KRam.OffYPos)))
                .Append("\",\"y_sub\":\"0x")
                .Append(Hex4(S3KRam.U16(host, addr + S3KRam.OffYSub)))
                .Append("\",\"x_vel\":\"0x")
                .Append(Hex4(S3KRam.U16(host, addr + S3KRam.OffXVel)))
                .Append("\",\"y_vel\":\"0x")
                .Append(Hex4(S3KRam.U16(host, addr + S3KRam.OffYVel)))
                .Append("\",\"render_flags\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffRenderFlags)))
                .Append("\",\"height_pixels\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffHeightPixels)))
                .Append("\",\"width_pixels\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffWidthPixels)))
                .Append("\",\"status\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffStatus)))
                .Append("\",\"routine\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffRoutine)))
                .Append("\",\"mapping_frame\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffMappingFrame)))
                .Append("\",\"anim\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffAnimId)))
                .Append("\",\"anim_frame\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffAnimFrame)))
                .Append("\",\"anim_frame_timer\":\"0x")
                .Append(Hex2(S3KRam.U8(host,
                    addr + S3KRam.OffAnimFrameTimer)))
                .Append("\",\"angle\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffAngle)))
                .Append("\",\"subtype\":\"0x")
                .Append(Hex2(S3KRam.U8(host, addr + S3KRam.OffSubtype)))
                .Append("\",\"collision_flags\":\"0x")
                .Append(Hex2(S3KRam.U8(host,
                    addr + S3KRam.OffCollisionFlags)))
                .Append("\",\"collision_property\":\"0x")
                .Append(Hex2(S3KRam.U8(host,
                    addr + S3KRam.OffCollisionProperty)))
                .Append("\"}");
            return fields.ToString();
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
