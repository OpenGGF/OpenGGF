using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S2AuxEventEngineTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine reproduces ehz1_fullrun frame 0 aux sequence",
                ReproducesEhz1FullrunFrameZeroAuxSequence));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine emits air mode change with immediate snapshot",
                EmitsAirModeChangeWithImmediateSnapshot));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine emits hurt routine change with stand object context",
                EmitsHurtRoutineChangeWithStandObjectContext));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine emits control_locked transitions from S2 move_lock",
                EmitsControlLockedTransitionsFromS2MoveLock));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine zeroes tails tracking while sidekick absent",
                ZeroesTailsTrackingWhileSidekickAbsent));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine gates snapshots on interval and hardcoded windows",
                GatesSnapshotsOnIntervalAndHardcodedWindows));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine emits tornado state with unsigned y velocity",
                EmitsTornadoStateWithUnsignedYVelocity));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine scopes object_near per character and skips tails self",
                ScopesObjectNearPerCharacterAndSkipsTailsSelf));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine maps ROM joypad bytes to engine masks",
                MapsRomJoypadBytesToEngineMasks));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine emits cursor_state on change with direction rule",
                EmitsCursorStateOnChangeWithDirectionRule));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine wraps the delayed cpu_state history index",
                WrapsDelayedCpuStateHistoryIndex));
        }

        /// <summary>
        /// RAM state reproducing the trace-frame-0 recorder inputs of
        /// src/test/resources/traces/s2/ehz1_fullrun (aux_state.jsonl frame-0
        /// block minus the step-1 zone_act_state/checkpoint events, which
        /// ride ProcessFrameStart — see S2AuxArmBlockTests).
        /// </summary>
        private static RamBackedHost BuildEhz1FrameZeroHost()
        {
            RamBackedHost host = S2TraceCsvWriterTests.BuildEhz1RowZeroHost();
            host.Ram[S2Ram.Ctrl1Raw] = 0x08;
            host.Ram[S2Ram.Ctrl1Logical] = 0x08;
            // Tails CPU state feeding the per-frame cpu_state event.
            host.Ram[S2Ram.TailsInteractId] = 0x01;
            host.SetWord(S2Ram.TailsCpuRoutine, 6);
            host.Ram[S2Ram.Ctrl2Held] = 0x08;
            host.Ram[S2Ram.Ctrl2Pressed] = 0x08;
            host.SetWord(S2Ram.SonicPosRecordIndex, 0x6C);
            // Delayed history entry at raw byte offset 0x6C - 68 = 0x28.
            host.SetWord(S2Ram.SonicPosRecordBuf + 0x28, 0x0060);
            host.SetWord(S2Ram.SonicPosRecordBuf + 0x2A, 0x0290);
            // state_snapshot extras.
            host.Ram[S2Ram.PlayerBase + S2Ram.OffRadiusY] = 19;
            host.Ram[S2Ram.PlayerBase + S2Ram.OffRadiusX] = 9;
            host.Ram[S2Ram.PlayerBase + S2Ram.OffTopSolidBit] = 0x39;
            host.Ram[S2Ram.PlayerBase + S2Ram.OffLrbSolidBit] = 0xE2;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffRadiusY] = 15;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffRadiusX] = 9;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffTopSolidBit] = 0x47;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffLrbSolidBit] = 0xBA;
            // Waterfall objects (0x34) in low slots, first badnik in slot 16.
            host.Ram[S2Ram.SlotAddress(2)] = 0x34;
            host.SetWord(S2Ram.SlotAddress(2) + S2Ram.OffXPos, 0x0120);
            host.Ram[S2Ram.SlotAddress(3)] = 0x34;
            host.SetWord(S2Ram.SlotAddress(3) + S2Ram.OffXPos, 0x0148);
            host.Ram[S2Ram.SlotAddress(4)] = 0x34;
            host.SetWord(S2Ram.SlotAddress(4) + S2Ram.OffXPos, 0x0188);
            host.Ram[S2Ram.SlotAddress(16)] = 0x9D;
            host.SetWord(S2Ram.SlotAddress(16) + S2Ram.OffXPos, 0x0227);
            host.SetWord(S2Ram.SlotAddress(16) + S2Ram.OffYPos, 0x021F);
            // ObjPosLoad cursor.
            host.SetLong(S2Ram.OplDataForward, 0x000E6850);
            host.SetLong(S2Ram.OplDataBackward, 0x000E684A);
            host.Ram[S2Ram.ObjStateForwardCounter] = 2;
            host.Ram[S2Ram.ObjStateBackwardCounter] = 1;
            return host;
        }

        private static void ReproducesEhz1FullrunFrameZeroAuxSequence()
        {
            var engine = new S2AuxEventEngine();
            IList<string> lines = engine.ProcessFrame(0, BuildEhz1FrameZeroHost());

            // LITERAL frame-0 lines of the gunzipped
            // src/test/resources/traces/s2/ehz1_fullrun/aux_state.jsonl.gz,
            // in recorder order (zone_act_state/checkpoint are step-1
            // events riding ProcessFrameStart — see S2AuxArmBlockTests).
            string[] expected =
            {
                "{\"frame\":0,\"vfc\":1,\"event\":\"routine_change\",\"character\":\"sonic\",\"from\":\"0x00\",\"to\":\"0x02\",\"x\":\"0x0060\",\"y\":\"0x0290\",\"x_vel\":12,\"y_vel\":0,\"inertia\":12,\"status\":\"0x00\",\"stand_on_obj\":0}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"routine_change\",\"character\":\"tails\",\"from\":\"0x00\",\"to\":\"0x02\",\"x\":\"0x004D\",\"y\":\"0x0294\",\"x_vel\":132,\"y_vel\":0,\"inertia\":132,\"status\":\"0x00\",\"stand_on_obj\":0}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"cpu_state\",\"character\":\"tails\",\"interact\":\"0x0001\",\"idle_timer\":0,\"flight_timer\":0,\"cpu_routine\":6,\"target_x\":\"0x0000\",\"target_y\":\"0x0000\",\"auto_fly_timer\":0,\"auto_jump_flag\":0,\"ctrl2_held\":\"0x08\",\"ctrl2_pressed\":\"0x08\",\"ctrl2_raw_held\":\"0x00\",\"ctrl1_logical\":\"0x0800\",\"pos_table_index\":\"0x6C\",\"delayed_index\":\"0x28\",\"delayed_x\":\"0x0060\",\"delayed_y\":\"0x0290\",\"delayed_input\":\"0x0000\",\"delayed_status\":\"0x00\",\"tails_status\":\"0x00\",\"tails_interact\":\"0x00\",\"tails_inertia\":\"0x0084\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"state_snapshot\",\"character\":\"sonic\",\"control_locked\":false,\"move_lock\":\"0x0000\",\"anim_id\":0,\"status_byte\":\"0x00\",\"routine\":\"0x02\",\"y_radius\":19,\"x_radius\":9,\"top_solid_bit\":\"0x39\",\"lrb_solid_bit\":\"0xE2\",\"raw_input\":\"0x08\",\"raw_input_mask\":\"0x08\",\"logical_input\":\"0x08\",\"logical_input_mask\":\"0x08\",\"on_object\":false,\"pushing\":false,\"underwater\":false,\"roll_jumping\":false}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"state_snapshot\",\"character\":\"tails\",\"control_locked\":false,\"move_lock\":\"0x0000\",\"anim_id\":0,\"status_byte\":\"0x00\",\"routine\":\"0x02\",\"y_radius\":15,\"x_radius\":9,\"top_solid_bit\":\"0x47\",\"lrb_solid_bit\":\"0xBA\",\"raw_input\":\"0x08\",\"raw_input_mask\":\"0x08\",\"logical_input\":\"0x08\",\"logical_input_mask\":\"0x08\",\"on_object\":false,\"pushing\":false,\"underwater\":false,\"roll_jumping\":false}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\",\"slot\":1,\"object_type\":\"0x02\",\"x\":\"0x004D\",\"y\":\"0x0294\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_near\",\"character\":\"sonic\",\"slot\":1,\"type\":\"0x02\",\"x\":\"0x004D\",\"y\":\"0x0294\",\"routine\":\"0x02\",\"status\":\"0x00\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\",\"slot\":2,\"object_type\":\"0x34\",\"x\":\"0x0120\",\"y\":\"0x0000\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\",\"slot\":3,\"object_type\":\"0x34\",\"x\":\"0x0148\",\"y\":\"0x0000\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\",\"slot\":4,\"object_type\":\"0x34\",\"x\":\"0x0188\",\"y\":\"0x0000\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\",\"slot\":16,\"object_type\":\"0x9D\",\"x\":\"0x0227\",\"y\":\"0x021F\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"slot_dump\",\"slots\":[[16,\"0x9D\"]]}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"cursor_state\",\"opl_screen\":\"0x0000\",\"fwd_ptr\":\"0x000E6850\",\"bwd_ptr\":\"0x000E684A\",\"fwd_ctr\":2,\"bwd_ctr\":1,\"dir\":\"R\"}"
            };
            AssertEx.Equal(
                string.Join("\n", expected), string.Join("\n", lines));
        }

        private static void EmitsAirModeChangeWithImmediateSnapshot()
        {
            var engine = new S2AuxEventEngine();
            RamBackedHost host = BuildEhz1FrameZeroHost();
            engine.ProcessFrame(74, host);   // prime prev status/routine

            // Reproduce the fixture's frame-75 sonic jump: air 0->1 with the
            // snapshot emitted BETWEEN the air and rolling mode changes.
            host.SetWord(S2Ram.FrameCount, 76);
            host.Ram[S2Ram.PlayerBase + S2Ram.OffStatus] =
                (byte)(S2Ram.StatusInAir | S2Ram.StatusRolling);
            host.Ram[S2Ram.PlayerBase + S2Ram.OffAnimId] = 2;
            host.Ram[S2Ram.PlayerBase + S2Ram.OffRadiusY] = 14;
            host.Ram[S2Ram.PlayerBase + S2Ram.OffRadiusX] = 7;
            host.Ram[S2Ram.Ctrl1Raw] = 0x48;
            host.Ram[S2Ram.Ctrl1Logical] = 0x48;
            IList<string> lines = engine.ProcessFrame(75, host);

            // LITERAL frame-75 lines of the gunzipped ehz1_fullrun
            // aux_state.jsonl.gz.
            AssertEx.Equal(
                "{\"frame\":75,\"vfc\":76,\"event\":\"mode_change\",\"character\":\"sonic\",\"field\":\"air\",\"from\":0,\"to\":1}",
                lines[0]);
            AssertEx.Equal(
                "{\"frame\":75,\"vfc\":76,\"event\":\"state_snapshot\",\"character\":\"sonic\",\"control_locked\":false,\"move_lock\":\"0x0000\",\"anim_id\":2,\"status_byte\":\"0x06\",\"routine\":\"0x02\",\"y_radius\":14,\"x_radius\":7,\"top_solid_bit\":\"0x39\",\"lrb_solid_bit\":\"0xE2\",\"raw_input\":\"0x48\",\"raw_input_mask\":\"0x18\",\"logical_input\":\"0x48\",\"logical_input_mask\":\"0x18\",\"on_object\":false,\"pushing\":false,\"underwater\":false,\"roll_jumping\":false}",
                lines[1]);
            AssertEx.Equal(
                "{\"frame\":75,\"vfc\":76,\"event\":\"mode_change\",\"character\":\"sonic\",\"field\":\"rolling\",\"from\":0,\"to\":1}",
                lines[2]);
        }

        private static void EmitsHurtRoutineChangeWithStandObjectContext()
        {
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            // Tails rolling in the air at frame 798 (primes prev status 0x06).
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x02;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffStatus] =
                (byte)(S2Ram.StatusInAir | S2Ram.StatusRolling);
            host.Ram[S2Ram.SidekickBase + S2Ram.OffRoutine] = 0x02;
            // Slot 27: the leaf platform Tails was standing on.
            host.Ram[S2Ram.SlotAddress(27)] = 0x8C;
            host.SetWord(S2Ram.SlotAddress(27) + S2Ram.OffXPos, 0x08E2);
            host.SetWord(S2Ram.SlotAddress(27) + S2Ram.OffYPos, 0x02B4);
            host.Ram[S2Ram.SlotAddress(27) + S2Ram.OffRoutine] = 0x02;
            engine.ProcessFrame(798, host);

            // Frame 799 (arz2 fixture): hurt transition 0x02->0x04.
            host.SetWord(S2Ram.FrameCount, 785);
            host.Ram[S2Ram.SidekickBase + S2Ram.OffStatus] = 0x02;
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffXPos, 0x0891);
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffYPos, 0x0503);
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffXVel, 0xFE00);  // -512
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffYVel, 0xFC00);  // -1024
            host.Ram[S2Ram.SidekickBase + S2Ram.OffRoutine] = 0x04;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffStandOnObj] = 27;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffAnimId] = 26;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffRadiusY] = 15;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffRadiusX] = 9;
            host.Ram[S2Ram.Ctrl1Raw] = 0x18;
            host.Ram[S2Ram.Ctrl1Logical] = 0x18;
            // Tails CPU state feeding the frame-799 cpu_state event.
            host.Ram[S2Ram.TailsInteractId] = 0x8C;
            host.SetWord(S2Ram.TailsCpuRoutine, 6);
            host.Ram[S2Ram.TailsCpuJumping] = 0x01;
            host.Ram[S2Ram.Ctrl2Held] = 0x78;
            host.SetWord(S2Ram.SonicPosRecordIndex, 0xAC);
            // Delayed history entry at raw byte offset 0xAC - 68 = 0x68.
            host.SetWord(S2Ram.SonicPosRecordBuf + 0x68, 0x088B);
            host.SetWord(S2Ram.SonicPosRecordBuf + 0x6A, 0x04D5);
            host.SetWord(S2Ram.SonicStatRecordBuf + 0x68, 0x1800);
            host.Ram[S2Ram.SonicStatRecordBuf + 0x6A] = 0x06;
            IList<string> lines = engine.ProcessFrame(799, host);

            // LITERAL frame-799 lines of the gunzipped
            // src/test/resources/traces/s2/arz2/aux_state.jsonl.gz: rolling
            // clears, then the hurt routine_change with the stand-object
            // context, the immediate hurt snapshot, then the per-frame
            // cpu_state.
            string[] expected =
            {
                "{\"frame\":799,\"vfc\":785,\"event\":\"mode_change\",\"character\":\"tails\",\"field\":\"rolling\",\"from\":1,\"to\":0}",
                "{\"frame\":799,\"vfc\":785,\"event\":\"routine_change\",\"character\":\"tails\",\"from\":\"0x02\",\"to\":\"0x04\",\"x\":\"0x0891\",\"y\":\"0x0503\",\"x_vel\":-512,\"y_vel\":-1024,\"inertia\":0,\"status\":\"0x02\",\"stand_on_obj\":27,\"stand_obj_slot\":27,\"stand_obj_type\":\"0x8C\",\"stand_obj_x\":\"0x08E2\",\"stand_obj_y\":\"0x02B4\",\"stand_obj_routine\":\"0x02\"}",
                "{\"frame\":799,\"vfc\":785,\"event\":\"state_snapshot\",\"character\":\"tails\",\"control_locked\":false,\"move_lock\":\"0x0000\",\"anim_id\":26,\"status_byte\":\"0x02\",\"routine\":\"0x04\",\"y_radius\":15,\"x_radius\":9,\"top_solid_bit\":\"0x00\",\"lrb_solid_bit\":\"0x00\",\"raw_input\":\"0x18\",\"raw_input_mask\":\"0x18\",\"logical_input\":\"0x18\",\"logical_input_mask\":\"0x18\",\"on_object\":false,\"pushing\":false,\"underwater\":false,\"roll_jumping\":false}",
                "{\"frame\":799,\"vfc\":785,\"event\":\"cpu_state\",\"character\":\"tails\",\"interact\":\"0x008C\",\"idle_timer\":0,\"flight_timer\":0,\"cpu_routine\":6,\"target_x\":\"0x0000\",\"target_y\":\"0x0000\",\"auto_fly_timer\":0,\"auto_jump_flag\":1,\"ctrl2_held\":\"0x78\",\"ctrl2_pressed\":\"0x00\",\"ctrl2_raw_held\":\"0x00\",\"ctrl1_logical\":\"0x1800\",\"pos_table_index\":\"0xAC\",\"delayed_index\":\"0x68\",\"delayed_x\":\"0x088B\",\"delayed_y\":\"0x04D5\",\"delayed_input\":\"0x1800\",\"delayed_status\":\"0x06\",\"tails_status\":\"0x02\",\"tails_interact\":\"0x1B\",\"tails_inertia\":\"0x0000\"}"
            };
            AssertEx.Equal(
                string.Join("\n", expected), string.Join("\n", lines));
        }

        private static void EmitsControlLockedTransitionsFromS2MoveLock()
        {
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            host.Ram[S2Ram.PlayerBase + S2Ram.OffId] = 0x01;
            host.Ram[S2Ram.PlayerBase + S2Ram.OffRoutine] = 0x02;
            engine.ProcessFrame(3211, host);

            // move_lock lives at the S2 offset +0x2E (NOT S1's +0x3E).
            host.SetWord(S2Ram.FrameCount, 3213);
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffMoveLock, 0x001E);
            IList<string> lines = engine.ProcessFrame(3212, host);
            // LITERAL frame-3212 line of the gunzipped ehz1_fullrun
            // aux_state.jsonl.gz.
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":3212,\"vfc\":3213,\"event\":\"mode_change\",\"character\":\"sonic\",\"field\":\"control_locked\",\"from\":0,\"to\":1}"));

            // Writing junk at S1's +0x3E must NOT re-trigger a transition.
            host.SetWord(S2Ram.PlayerBase + 0x3E, 0x1234);
            host.SetWord(S2Ram.FrameCount, 3214);
            lines = engine.ProcessFrame(3213, host);
            AssertEx.Equal(0, CountContaining(lines, "control_locked\""));

            // Lock expiry emits the 1 -> 0 transition.
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffMoveLock, 0x0000);
            host.SetWord(S2Ram.FrameCount, 3215);
            lines = engine.ProcessFrame(3214, host);
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":3214,\"vfc\":3215,\"event\":\"mode_change\",\"character\":\"sonic\",\"field\":\"control_locked\",\"from\":1,\"to\":0}"));
        }

        private static void ZeroesTailsTrackingWhileSidekickAbsent()
        {
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            host.Ram[S2Ram.PlayerBase + S2Ram.OffId] = 0x01;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x02;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffRoutine] = 0x02;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffStatus] = S2Ram.StatusInAir;
            IList<string> lines = engine.ProcessFrame(0, host);
            AssertEx.Equal(1, CountContaining(
                lines, "\"routine_change\",\"character\":\"tails\""));

            // Sidekick despawns: no tails-scoped tracker events, only the
            // slot-1 object_removed from the scan plus the unconditional
            // per-frame cpu_state (which reads the empty slot bytes).
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x00;
            lines = engine.ProcessFrame(1, host);
            AssertEx.Equal(1, CountContaining(lines, "\"character\":\"tails\""));
            AssertEx.Equal(1, CountContaining(
                lines, "\"event\":\"cpu_state\",\"character\":\"tails\""));
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":1,\"vfc\":0,\"event\":\"object_removed\",\"slot\":1,\"object_type\":\"0x02\"}"));

            // Reappearing restarts from the zeroed tracker: routine_change
            // fires from 0x00 (not the pre-despawn 0x02) and the air bit
            // re-fires 0 -> 1.
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x02;
            lines = engine.ProcessFrame(2, host);
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":2,\"vfc\":0,\"event\":\"mode_change\",\"character\":\"tails\",\"field\":\"air\",\"from\":0,\"to\":1}"));
            AssertEx.Equal(1, CountContaining(
                lines, "\"routine_change\",\"character\":\"tails\",\"from\":\"0x00\",\"to\":\"0x02\""));
        }

        private static void GatesSnapshotsOnIntervalAndHardcodedWindows()
        {
            // The %60 cadence plus the two hardcoded windows baked into the
            // Lua recorder (5104-5106 and 5995-6005) — recorder byte
            // contract, reproduced verbatim.
            AssertEx.Equal(true, S2AuxEventEngine.IsSnapshotFrame(0));
            AssertEx.Equal(true, S2AuxEventEngine.IsSnapshotFrame(5100));
            AssertEx.Equal(false, S2AuxEventEngine.IsSnapshotFrame(5103));
            AssertEx.Equal(true, S2AuxEventEngine.IsSnapshotFrame(5104));
            AssertEx.Equal(true, S2AuxEventEngine.IsSnapshotFrame(5106));
            AssertEx.Equal(false, S2AuxEventEngine.IsSnapshotFrame(5107));
            AssertEx.Equal(false, S2AuxEventEngine.IsSnapshotFrame(5994));
            AssertEx.Equal(true, S2AuxEventEngine.IsSnapshotFrame(5995));
            AssertEx.Equal(true, S2AuxEventEngine.IsSnapshotFrame(6005));
            AssertEx.Equal(false, S2AuxEventEngine.IsSnapshotFrame(6006));

            // Window frames snapshot sonic then tails.
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            host.Ram[S2Ram.PlayerBase + S2Ram.OffId] = 0x01;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x02;
            IList<string> lines = engine.ProcessFrame(5104, host);
            AssertEx.Equal(2, CountContaining(lines, "\"state_snapshot\""));
            AssertEx.Equal(1, CountContaining(
                lines, "\"state_snapshot\",\"character\":\"sonic\""));
            lines = engine.ProcessFrame(5107, host);
            AssertEx.Equal(0, CountContaining(lines, "\"state_snapshot\""));

            // An absent character is skipped inside the gate.
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x00;
            lines = engine.ProcessFrame(5995, host);
            AssertEx.Equal(1, CountContaining(lines, "\"state_snapshot\""));
            AssertEx.Equal(1, CountContaining(
                lines, "\"state_snapshot\",\"character\":\"sonic\""));
        }

        private static void EmitsTornadoStateWithUnsignedYVelocity()
        {
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            // Sonic hangs below the Tornado (subject reads are unconditional).
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffXPos, 0x1234);
            host.SetWord(S2Ram.PlayerBase + S2Ram.OffYPos, 0x0400);
            int addr = S2Ram.SlotAddress(20);
            host.Ram[addr] = S2Ram.TornadoObjectId;
            host.SetWord(addr + S2Ram.OffXPos, 0x1234);
            host.SetWord(addr + S2Ram.OffYPos, 0x0456);
            host.SetWord(addr + S2Ram.OffYSub, 0x8000);
            host.SetWord(addr + S2Ram.OffYVel, 0xFF00);   // -256 signed; raw unsigned
            host.Ram[addr + S2Ram.OffRoutine] = 0x02;
            host.Ram[addr + S2Ram.OffRoutineSecondary] = 0x04;
            host.Ram[addr + S2Ram.OffStatus] = 0x08;
            host.Ram[addr + 0x2E] = 0x01;
            host.Ram[addr + 0x2F] = 0x02;
            host.Ram[addr + 0x30] = 0x03;
            host.Ram[addr + 0x31] = 0x04;

            IList<string> lines = engine.ProcessFrame(0, host);
            // Tornado diagnostic precedes the proximity events for the slot
            // and renders y_vel as the raw unsigned word (0xFF00, not -256).
            int tornadoIndex = lines.IndexOf(
                "{\"frame\":0,\"vfc\":0,\"event\":\"s2_tornado_state\",\"slot\":20,\"x\":\"0x1234\",\"y\":\"0x0456\",\"y_sub\":\"0x8000\",\"y_vel\":\"0xFF00\",\"routine\":\"0x02\",\"routine_secondary\":\"0x04\",\"status_byte\":\"0x08\",\"objoff_2e\":\"0x01\",\"objoff_2f\":\"0x02\",\"objoff_30\":\"0x03\",\"objoff_31\":\"0x04\"}");
            AssertEx.Equal(true, tornadoIndex >= 0);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"object_near\",\"character\":\"sonic\",\"slot\":20,\"type\":\"0xB2\",\"x\":\"0x1234\",\"y\":\"0x0456\",\"routine\":\"0x02\",\"status\":\"0x08\"}",
                lines[tornadoIndex + 1]);
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":0,\"vfc\":0,\"event\":\"slot_dump\",\"slots\":[[20,\"0xB2\"]]}"));
        }

        private static void ScopesObjectNearPerCharacterAndSkipsTailsSelf()
        {
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            host.SetWord(S2Ram.FrameCount, 137);
            // Sonic far away at (0,0); Tails adjacent to the slot-16 badnik.
            host.Ram[S2Ram.PlayerBase + S2Ram.OffId] = 0x01;
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x02;
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffXPos, 0x0250);
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffYPos, 0x0230);
            int addr = S2Ram.SlotAddress(16);
            host.Ram[addr] = 0x9D;
            host.SetWord(addr + S2Ram.OffXPos, 0x0227);
            host.SetWord(addr + S2Ram.OffYPos, 0x0227);
            host.Ram[addr + S2Ram.OffRoutine] = 0x04;

            IList<string> lines = engine.ProcessFrame(136, host);
            // LITERAL frame-136 tails-scoped line of the gunzipped
            // ehz1_fullrun aux_state.jsonl.gz.
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":136,\"vfc\":137,\"event\":\"object_near\",\"character\":\"tails\",\"slot\":16,\"type\":\"0x9D\",\"x\":\"0x0227\",\"y\":\"0x0227\",\"routine\":\"0x04\",\"status\":\"0x00\"}"));
            // Sonic is out of range of everything, and Tails never reports
            // proximity to its own slot 1.
            AssertEx.Equal(0, CountContaining(
                lines, "\"object_near\",\"character\":\"sonic\""));
            AssertEx.Equal(0, CountContaining(
                lines, "\"object_near\",\"character\":\"tails\",\"slot\":1,"));
        }

        private static void MapsRomJoypadBytesToEngineMasks()
        {
            AssertEx.Equal(0x00, S2AuxEventEngine.RomJoypadToMask(0x00));
            AssertEx.Equal(0x0F, S2AuxEventEngine.RomJoypadToMask(0x0F));
            // Any of A/B/C (bits 4-6) collapses to 0x10.
            AssertEx.Equal(0x18, S2AuxEventEngine.RomJoypadToMask(0x48));
            AssertEx.Equal(0x10, S2AuxEventEngine.RomJoypadToMask(0x70));
            AssertEx.Equal(0x10, S2AuxEventEngine.RomJoypadToMask(0x20));
            // Start (bit 7) never contributes.
            AssertEx.Equal(0x00, S2AuxEventEngine.RomJoypadToMask(0x80));
            AssertEx.Equal(0x1F, S2AuxEventEngine.RomJoypadToMask(0xFF));
        }

        private static void EmitsCursorStateOnChangeWithDirectionRule()
        {
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            host.SetWord(S2Ram.OplScreen, 0x0080);
            host.SetLong(S2Ram.OplDataForward, 0x000E6850);
            host.SetLong(S2Ram.OplDataBackward, 0x000E684A);
            host.Ram[S2Ram.ObjStateForwardCounter] = 2;
            host.Ram[S2Ram.ObjStateBackwardCounter] = 1;

            // First recorded frame always fires, direction "R" (prev is -1).
            IList<string> lines = engine.ProcessFrame(0, host);
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":0,\"vfc\":0,\"event\":\"cursor_state\",\"opl_screen\":\"0x0080\",\"fwd_ptr\":\"0x000E6850\",\"bwd_ptr\":\"0x000E684A\",\"fwd_ctr\":2,\"bwd_ctr\":1,\"dir\":\"R\"}"));

            // Unchanged screen emits nothing.
            lines = engine.ProcessFrame(1, host);
            AssertEx.Equal(0, CountContaining(lines, "cursor_state"));

            // Moving to a lower chunk emits direction "L".
            host.SetWord(S2Ram.OplScreen, 0x0000);
            lines = engine.ProcessFrame(2, host);
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":2,\"vfc\":0,\"event\":\"cursor_state\",\"opl_screen\":\"0x0000\",\"fwd_ptr\":\"0x000E6850\",\"bwd_ptr\":\"0x000E684A\",\"fwd_ctr\":2,\"bwd_ctr\":1,\"dir\":\"L\"}"));
        }

        private static void WrapsDelayedCpuStateHistoryIndex()
        {
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            // arz frame 37: pos_table_index 0x00 wraps to delayed index
            // (0x00 - 68) & 0xFF = 0xBC, used as a raw byte offset into the
            // history buffers.
            host.SetWord(S2Ram.FrameCount, 38);
            host.Ram[S2Ram.TailsInteractId] = 0x01;
            host.SetWord(S2Ram.TailsCpuRoutine, 6);
            host.Ram[S2Ram.Ctrl2Held] = 0x08;
            host.Ram[S2Ram.Ctrl1Logical] = 0x08;
            host.SetWord(S2Ram.SonicPosRecordBuf + 0xBC, 0x006D);
            host.SetWord(S2Ram.SonicPosRecordBuf + 0xBE, 0x037D);
            host.SetWord(S2Ram.SonicStatRecordBuf + 0xBC, 0x0800);
            host.Ram[S2Ram.SidekickBase + S2Ram.OffId] = 0x02;
            host.SetWord(S2Ram.SidekickBase + S2Ram.OffInertia, 0x012D);

            IList<string> lines = engine.ProcessFrame(37, host);
            // LITERAL frame-37 cpu_state line of the gunzipped
            // src/test/resources/traces/s2/arz/aux_state.jsonl.gz.
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":37,\"vfc\":38,\"event\":\"cpu_state\",\"character\":\"tails\",\"interact\":\"0x0001\",\"idle_timer\":0,\"flight_timer\":0,\"cpu_routine\":6,\"target_x\":\"0x0000\",\"target_y\":\"0x0000\",\"auto_fly_timer\":0,\"auto_jump_flag\":0,\"ctrl2_held\":\"0x08\",\"ctrl2_pressed\":\"0x00\",\"ctrl2_raw_held\":\"0x00\",\"ctrl1_logical\":\"0x0800\",\"pos_table_index\":\"0x00\",\"delayed_index\":\"0xBC\",\"delayed_x\":\"0x006D\",\"delayed_y\":\"0x037D\",\"delayed_input\":\"0x0800\",\"delayed_status\":\"0x00\",\"tails_status\":\"0x00\",\"tails_interact\":\"0x00\",\"tails_inertia\":\"0x012D\"}"));
        }

        private static int CountContaining(IList<string> lines, string fragment)
        {
            int count = 0;
            foreach (string line in lines)
            {
                if (line.IndexOf(fragment, StringComparison.Ordinal) >= 0)
                {
                    count++;
                }
            }
            return count;
        }
    }
}
