using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// S3KAuxEventEngine tests: every frame-polled aux family present in
    /// the three gated S3K fixtures is verified against a LITERAL line
    /// grepped from the gunzipped fixture aux streams (RAM staged to the
    /// values the ROM held at that fixture instant), plus gating /
    /// dedup / emission-order checks. Expected strings are byte-exact
    /// fixture bytes — never edit them to make a test pass.
    /// </summary>
    internal static class S3KAuxEventEngineTests
    {
        private static FakeS1Host NewHost()
        {
            return new FakeS1Host((h, f) => { });
        }

        private static int Slot(int index)
        {
            return 0xB000 + (index * 0x4A);
        }

        private static List<string> Of(IList<string> lines, string eventName)
        {
            var matched = new List<string>();
            string needle = "\"event\":\"" + eventName + "\"";
            foreach (string line in lines)
            {
                if (line.IndexOf(needle, StringComparison.Ordinal) >= 0)
                {
                    matched.Add(line);
                }
            }
            return matched;
        }

        private static string EventName(string line)
        {
            int start = line.IndexOf("\"event\":\"", StringComparison.Ordinal)
                + "\"event\":\"".Length;
            int end = line.IndexOf('"', start);
            return line.Substring(start, end - start);
        }

        private static void SetBytes(
            FakeS1Host host, int address, string hex)
        {
            for (int i = 0; i < hex.Length / 2; i++)
            {
                host.Ram[address + i] = Convert.ToByte(
                    hex.Substring(i * 2, 2), 16);
            }
        }

        /// <summary>
        /// Stages the CNZ-fixture frame-0 recorder state shared by several
        /// tests: level-gated CNZ1 arm frame with Sonic airborne at
        /// (0x18, 0x600) and Tails present in slot 1.
        /// </summary>
        private static void StageCnzFrame0(FakeS1Host host)
        {
            host.Ram[0xF600] = 0x0C;   // Game_mode Level
            host.Ram[0xFE10] = 0x03;   // zone CNZ
            host.Ram[0xFE11] = 0x00;   // act 1
            host.Ram[0xEE4F] = 0x00;   // apparent act
            host.Ram[0xF711] = 0x01;   // level-started flag
            // P1: airborne, routine 0x02, anim 5, radii 19/9 at (0x18, 0x600).
            host.SetU16(0xB010, 0x0018);
            host.SetU16(0xB014, 0x0600);
            host.Ram[0xB000 + 0x2A] = 0x02;
            host.Ram[0xB000 + 0x05] = 0x02;
            host.Ram[0xB000 + 0x20] = 5;
            host.Ram[0xB000 + 0x1E] = 19;
            host.Ram[0xB000 + 0x1F] = 9;
            // Tails in slot 1 (present): code, pos, status, radii, art.
            host.SetU32(Slot(1), 0x0001365C);
            host.SetU16(Slot(1) + 0x10, 0x0018);
            host.SetU16(Slot(1) + 0x14, 0x0600);
            host.Ram[Slot(1) + 0x2A] = 0x02;
            host.Ram[Slot(1) + 0x05] = 0x02;
            host.Ram[Slot(1) + 0x2C] = 0x1E;
            host.Ram[Slot(1) + 0x1F] = 9;
            host.Ram[Slot(1) + 0x1E] = 15;
            host.Ram[Slot(1) + 0x04] = 0x84;
            host.Ram[Slot(1) + 0x07] = 0x18;
            host.Ram[Slot(1) + 0x06] = 0x18;
            host.SetU16(0xEE84, 0x05A0);   // camera_y_copy
            host.SetU16(0xEE26, 0x0004);   // Pos_table_index
            host.SetU16(0xF708, 12);       // Tails CPU routine
            host.SetU32(0xF636, 0x14A7ABBB); // RNG seed
            SetBytes(host, 0xFE6E,
                "007D008200020082000200820002008200020084000400880008"
                + "008800080084000400820002393400EC213200B2318B010B523D"
                + "01BD72EF026F0082000240FC00FC");
        }

        public static void Register(List<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3K aux: cpu_state_snapshot asserts the current CNZ contract",
                CpuStateSnapshotMatchesCurrentContract));
            tests.Add(new TestMain.TestCase(
                "S3K aux: object_state_snapshot emits the CNZ balloon literal for balloon slots only",
                ObjectStateSnapshotMatchesCurrentContract));
            tests.Add(new TestMain.TestCase(
                "S3K aux: zone_act_state baseline and gameplay_start match CNZ literals and dedup",
                ZoneActStateAndGameplayStart));
            tests.Add(new TestMain.TestCase(
                "S3K aux: act_transition_to_cnz2 fires once on the act edge",
                ActTransitionCheckpoint));
            tests.Add(new TestMain.TestCase(
                "S3K aux: level-gated gameplay_start is zone-3-literal (MGZ emits none)",
                MgzGameplayStartQuirk));
            tests.Add(new TestMain.TestCase(
                "S3K aux: finalization emits the MGZ gameplay_end literal for level-gated only",
                FinalizationGameplayEnd));
            tests.Add(new TestMain.TestCase(
                "S3K aux: aiz profile emits the intro_begin literal at frame 0",
                AizIntroBegin));
            tests.Add(new TestMain.TestCase(
                "S3K aux: player_mode_set emits baseline then only on change",
                PlayerModeSet));
            tests.Add(new TestMain.TestCase(
                "S3K aux: mode_change air, state_snapshot, routine_change match CNZ frame-0 literals in order",
                ModeChangeBlockFrame0));
            tests.Add(new TestMain.TestCase(
                "S3K aux: routine_change carries the stood-on object context literal",
                RoutineChangeWithStandContext));
            tests.Add(new TestMain.TestCase(
                "S3K aux: cpu_state matches the CNZ frame-0 literal",
                CpuStateMatchesCurrentContract));
            tests.Add(new TestMain.TestCase(
                "S3K aux: oscillation_state matches the CNZ frame-0 literal",
                OscillationStateMatchesCurrentContract));
            tests.Add(new TestMain.TestCase(
                "S3K aux: control_lock_state baseline literal, change suppression, 60-frame force",
                ControlLockState));
            tests.Add(new TestMain.TestCase(
                "S3K aux: object_state matches the CNZ literal and honors both proximity arms",
                ObjectStateProximity));
            tests.Add(new TestMain.TestCase(
                "S3K aux: interact_state emits sonic always and tails only when present",
                InteractStates));
            tests.Add(new TestMain.TestCase(
                "S3K aux: sidekick_interact_object matches the CNZ frame-0 literal",
                SidekickInteractObject));
            tests.Add(new TestMain.TestCase(
                "S3K aux: air_countdown_state emits the empty p1/p2 literals every frame",
                AirCountdownEmpty));
            tests.Add(new TestMain.TestCase(
                "S3K aux: air_countdown_state resolves the owner and visible children literal",
                AirCountdownWithChildren));
            tests.Add(new TestMain.TestCase(
                "S3K aux: cage_state matches the CNZ literal for cage-coded slots",
                CageState));
            tests.Add(new TestMain.TestCase(
                "S3K aux: cnz_cylinder_state matches the CNZ literal inside its window only",
                CnzCylinderState));
            tests.Add(new TestMain.TestCase(
                "S3K aux: collision_response_list_end_of_frame matches the CNZ frame-618 literal",
                CollisionResponseListEndOfFrame));
            tests.Add(new TestMain.TestCase(
                "S3K aux: aiz_fire_transition matches the AIZ literal and is profile-gated",
                AizFireTransition));
            tests.Add(new TestMain.TestCase(
                "S3K aux: terrain_wall_sensor matches the AIZ frame-7549 literal and zone gate",
                TerrainWallSensor));
            tests.Add(new TestMain.TestCase(
                "S3K aux: aiz_handoff_terrain_state emits the hook-less skeleton literal in-window",
                AizHandoffTerrainState));
            tests.Add(new TestMain.TestCase(
                "S3K aux: scan_objects asserts the current lifecycle contract",
                ScanObjectsLiterals));
            tests.Add(new TestMain.TestCase(
                "S3K aux: frame-0 emission order asserts the current CNZ contract",
                Frame0EmissionOrder));
        }

        private static void CpuStateSnapshotMatchesCurrentContract()
        {
            var host = NewHost();
            host.SetU16(0xF708, 12);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            IList<string> lines = engine.EmitPreTraceSnapshots(host);
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":-1,\"vfc\":0,\"event\":\"cpu_state_snapshot\","
                + "\"character\":\"tails\",\"control_counter\":0,"
                + "\"respawn_counter\":0,\"cpu_routine\":12,"
                + "\"target_x\":\"0x0000\",\"target_y\":\"0x0000\","
                + "\"interact_id\":\"0x00\",\"jumping\":0}",
                lines[0]);
        }

        private static void ObjectStateSnapshotMatchesCurrentContract()
        {
            var host = NewHost();
            host.SetU16(0xF708, 12);
            // CNZ fixture slot 4 balloon raw bytes (off_00..off_49).
            SetBytes(host, Slot(4),
                "00031754040020100280035100230502"
                + "01800000068300000000000000000000"
                + "0808140106001200D700000004000000"
                + "00000680000000000000000000000000"
                + "0000000000000000EB02");
            // A non-balloon dynamic object must NOT snapshot.
            host.SetU32(Slot(5), 0x0002D690);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            IList<string> lines = engine.EmitPreTraceSnapshots(host);
            AssertEx.Equal(2, lines.Count);
            AssertEx.Equal(
                "{\"frame\":-1,\"vfc\":0,\"event\":\"object_state_snapshot\","
                + "\"slot\":4,\"object_type\":\"0x41\","
                + "\"object_code\":\"0x00031754\",\"fields\":{"
                + "\"off_00\":\"0x00\",\"off_01\":\"0x03\",\"off_02\":\"0x17\","
                + "\"off_03\":\"0x54\",\"off_04\":\"0x04\",\"off_05\":\"0x00\","
                + "\"off_06\":\"0x20\",\"off_07\":\"0x10\",\"off_08\":\"0x02\","
                + "\"off_09\":\"0x80\",\"off_0A\":\"0x03\",\"off_0B\":\"0x51\","
                + "\"off_0C\":\"0x00\",\"off_0D\":\"0x23\",\"off_0E\":\"0x05\","
                + "\"off_0F\":\"0x02\",\"off_10\":\"0x01\",\"off_11\":\"0x80\","
                + "\"off_12\":\"0x00\",\"off_13\":\"0x00\",\"off_14\":\"0x06\","
                + "\"off_15\":\"0x83\",\"off_16\":\"0x00\",\"off_17\":\"0x00\","
                + "\"off_18\":\"0x00\",\"off_19\":\"0x00\",\"off_1A\":\"0x00\","
                + "\"off_1B\":\"0x00\",\"off_1C\":\"0x00\",\"off_1D\":\"0x00\","
                + "\"off_1E\":\"0x00\",\"off_1F\":\"0x00\",\"off_20\":\"0x08\","
                + "\"off_21\":\"0x08\",\"off_22\":\"0x14\",\"off_23\":\"0x01\","
                + "\"off_24\":\"0x06\",\"off_25\":\"0x00\",\"off_26\":\"0x12\","
                + "\"off_27\":\"0x00\",\"off_28\":\"0xD7\",\"off_29\":\"0x00\","
                + "\"off_2A\":\"0x00\",\"off_2B\":\"0x00\",\"off_2C\":\"0x04\","
                + "\"off_2D\":\"0x00\",\"off_2E\":\"0x00\",\"off_2F\":\"0x00\","
                + "\"off_30\":\"0x00\",\"off_31\":\"0x00\",\"off_32\":\"0x06\","
                + "\"off_33\":\"0x80\",\"off_34\":\"0x00\",\"off_35\":\"0x00\","
                + "\"off_36\":\"0x00\",\"off_37\":\"0x00\",\"off_38\":\"0x00\","
                + "\"off_39\":\"0x00\",\"off_3A\":\"0x00\",\"off_3B\":\"0x00\","
                + "\"off_3C\":\"0x00\",\"off_3D\":\"0x00\",\"off_3E\":\"0x00\","
                + "\"off_3F\":\"0x00\",\"off_40\":\"0x00\",\"off_41\":\"0x00\","
                + "\"off_42\":\"0x00\",\"off_43\":\"0x00\",\"off_44\":\"0x00\","
                + "\"off_45\":\"0x00\",\"off_46\":\"0x00\",\"off_47\":\"0x00\","
                + "\"off_48\":\"0xEB\",\"off_49\":\"0x02\","
                + "\"x_pos\":\"0x0180\",\"x_sub\":\"0x0000\","
                + "\"y_pos\":\"0x0683\",\"y_sub\":\"0x0000\","
                + "\"x_vel\":\"0x0000\",\"y_vel\":\"0x0000\","
                + "\"render_flags\":\"0x04\",\"height_pixels\":\"0x20\","
                + "\"width_pixels\":\"0x10\",\"status\":\"0x00\","
                + "\"routine\":\"0x00\",\"mapping_frame\":\"0x14\","
                + "\"anim\":\"0x08\",\"anim_frame\":\"0x01\","
                + "\"anim_frame_timer\":\"0x06\",\"angle\":\"0x12\","
                + "\"subtype\":\"0x04\",\"collision_flags\":\"0xD7\","
                + "\"collision_property\":\"0x00\"}}",
                lines[1]);
        }

        private static void ZoneActStateAndGameplayStart()
        {
            var host = NewHost();
            StageCnzFrame0(host);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            IList<string> lines = engine.ProcessFrame(0, host);
            List<string> zas = Of(lines, "zone_act_state");
            AssertEx.Equal(1, zas.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"event\":\"zone_act_state\","
                + "\"actual_zone_id\":3,\"actual_act\":0,\"apparent_act\":0,"
                + "\"game_mode\":12}",
                zas[0]);
            List<string> checkpoints = Of(lines, "checkpoint");
            AssertEx.Equal(1, checkpoints.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"event\":\"checkpoint\","
                + "\"name\":\"gameplay_start\",\"actual_zone_id\":3,"
                + "\"actual_act\":0,\"apparent_act\":0,\"game_mode\":12}",
                checkpoints[0]);

            // Unchanged tuple: no re-emission; gameplay_start is once-only.
            IList<string> next = engine.ProcessFrame(1, host);
            AssertEx.Equal(0, Of(next, "zone_act_state").Count);
            AssertEx.Equal(0, Of(next, "checkpoint").Count);
        }

        private static void ActTransitionCheckpoint()
        {
            var host = NewHost();
            host.Ram[0xF600] = 0x0C;
            host.Ram[0xFE10] = 0x03;
            host.Ram[0xFE11] = 0x00;
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            engine.ProcessFrame(16668, host);

            host.Ram[0xFE11] = 0x01;
            IList<string> lines = engine.ProcessFrame(16669, host);
            List<string> checkpoints = Of(lines, "checkpoint");
            AssertEx.Equal(1, checkpoints.Count);
            AssertEx.Equal(
                "{\"frame\":16669,\"event\":\"checkpoint\","
                + "\"name\":\"act_transition_to_cnz2\",\"actual_zone_id\":3,"
                + "\"actual_act\":1,\"apparent_act\":0,\"game_mode\":12}",
                checkpoints[0]);

            // The edge latch has moved past (3, 0): never re-fires.
            AssertEx.Equal(
                0, Of(engine.ProcessFrame(16670, host), "checkpoint").Count);
        }

        private static void MgzGameplayStartQuirk()
        {
            var host = NewHost();
            host.Ram[0xF600] = 0x0C;
            host.Ram[0xFE10] = 0x02;   // MGZ: zone 2 — the Lua literal wants 3
            host.Ram[0xF711] = 0x01;
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            IList<string> lines = engine.ProcessFrame(0, host);
            AssertEx.Equal(0, Of(lines, "checkpoint").Count);
        }

        private static void FinalizationGameplayEnd()
        {
            var host = NewHost();
            host.Ram[0xFE10] = 0x03;
            host.Ram[0xFE11] = 0x00;
            host.Ram[0xEE4F] = 0x00;
            host.Ram[0xF600] = 0x8C;
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            IList<string> lines = engine.EmitFinalization(35912, host);
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":35912,\"event\":\"checkpoint\","
                + "\"name\":\"gameplay_end\",\"actual_zone_id\":3,"
                + "\"actual_act\":0,\"apparent_act\":0,\"game_mode\":140}",
                lines[0]);

            var aizEngine = new S3KAuxEventEngine(
                S3KTraceProfile.AizEndToEnd);
            AssertEx.Equal(0, aizEngine.EmitFinalization(20798, host).Count);
        }

        private static void AizIntroBegin()
        {
            var host = NewHost();
            host.Ram[0xF600] = 0x4C;   // transitional level-family mode
            var engine = new S3KAuxEventEngine(S3KTraceProfile.AizEndToEnd);
            IList<string> lines = engine.ProcessFrame(0, host);
            List<string> zas = Of(lines, "zone_act_state");
            AssertEx.Equal(1, zas.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"event\":\"zone_act_state\","
                + "\"actual_zone_id\":0,\"actual_act\":0,\"apparent_act\":0,"
                + "\"game_mode\":76}",
                zas[0]);
            List<string> checkpoints = Of(lines, "checkpoint");
            AssertEx.Equal(1, checkpoints.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"event\":\"checkpoint\","
                + "\"name\":\"intro_begin\",\"actual_zone_id\":0,"
                + "\"actual_act\":0,\"apparent_act\":0,\"game_mode\":76}",
                checkpoints[0]);
        }

        private static void PlayerModeSet()
        {
            var host = NewHost();
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> first = Of(engine.ProcessFrame(0, host),
                "player_mode_set");
            AssertEx.Equal(1, first.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"player_mode_set\","
                + "\"mode\":0}",
                first[0]);

            AssertEx.Equal(
                0, Of(engine.ProcessFrame(1, host), "player_mode_set").Count);

            host.SetU16(0xFF08, 2);
            List<string> changed = Of(engine.ProcessFrame(2, host),
                "player_mode_set");
            AssertEx.Equal(1, changed.Count);
            AssertEx.Equal(
                "{\"frame\":2,\"vfc\":0,\"event\":\"player_mode_set\","
                + "\"mode\":2}",
                changed[0]);
        }

        private static void ModeChangeBlockFrame0()
        {
            var host = NewHost();
            StageCnzFrame0(host);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            IList<string> lines = engine.ProcessFrame(0, host);

            List<string> modeChanges = Of(lines, "mode_change");
            AssertEx.Equal(1, modeChanges.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"mode_change\","
                + "\"field\":\"air\",\"from\":0,\"to\":1}",
                modeChanges[0]);

            // Two identical snapshots: the air-transition one and the
            // frame%60==0 baseline (both present at CNZ fixture frame 0).
            List<string> snapshots = Of(lines, "state_snapshot");
            AssertEx.Equal(2, snapshots.Count);
            string expectedSnapshot =
                "{\"frame\":0,\"vfc\":0,\"event\":\"state_snapshot\","
                + "\"control_locked\":false,\"anim_id\":5,"
                + "\"status_byte\":\"0x02\",\"routine\":\"0x02\","
                + "\"y_radius\":19,\"x_radius\":9,\"on_object\":false,"
                + "\"pushing\":false,\"underwater\":false,"
                + "\"roll_jumping\":false,"
                // Collision-plane diagnostic context. StageCnzFrame0 stages
                // only the fields this case asserts, so the five new sources
                // read zero from the synthetic host; ROM-plausibility of the
                // real values is covered by the ROM-backed gates.
                + "\"top_solid_bit\":\"0x00\",\"lrb_solid_bit\":\"0x00\","
                + "\"stick_to_convex\":\"0x00\","
                + "\"primary_collision_addr\":\"0x00000000\","
                + "\"secondary_collision_addr\":\"0x00000000\"}";
            AssertEx.Equal(expectedSnapshot, snapshots[0]);
            AssertEx.Equal(expectedSnapshot, snapshots[1]);

            List<string> routineChanges = Of(lines, "routine_change");
            AssertEx.Equal(1, routineChanges.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"routine_change\","
                + "\"from\":\"0x00\",\"to\":\"0x02\","
                + "\"sonic_x\":\"0x0018\",\"sonic_y\":\"0x0600\","
                + "\"x_vel\":0,\"y_vel\":0,\"inertia\":0,"
                + "\"status\":\"0x02\",\"stand_on_obj\":0}",
                routineChanges[0]);

            // Order within the frame: mode_change, then its snapshot, then
            // routine_change (Lua check_mode_changes source order).
            AssertEx.Equal(true,
                lines.IndexOf(modeChanges[0]) < lines.IndexOf(snapshots[0])
                && lines.IndexOf(snapshots[0])
                    < lines.IndexOf(routineChanges[0]));
        }

        private static void RoutineChangeWithStandContext()
        {
            var host = NewHost();
            // Fixture ADDR_FRAMECOUNT (Level_frame_counter, 0xFE04)
            // at that instant; the recorder reads it live since Lua
            // v6.31-s3k.
            host.SetU16(0xFE04, 15130);
            host.Ram[0xF600] = 0x0C;
            host.Ram[0xFE10] = 0x03;
            // CNZ fixture F15129: hurt transition while standing on slot 4.
            host.SetU16(0xB010, 0x3280);
            host.SetU16(0xB014, 0x02C8);
            host.SetU16(0xB000 + 0x18, 0xFE00);   // x_vel -512
            host.SetU16(0xB000 + 0x1A, 0xFC00);   // y_vel -1024
            host.Ram[0xB000 + 0x2A] = 0x02;
            host.Ram[0xB000 + 0x05] = 0x02;
            host.SetU16(0xB000 + 0x42, (ushort)Slot(4));
            host.SetU32(Slot(4), 0x00051FCE);
            host.Ram[Slot(4) + 0x05] = 0x04;
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            engine.ProcessFrame(15128, host);

            host.Ram[0xB000 + 0x05] = 0x04;   // routine -> hurt
            IList<string> lines = engine.ProcessFrame(15129, host);
            List<string> routineChanges = Of(lines, "routine_change");
            AssertEx.Equal(1, routineChanges.Count);
            AssertEx.Equal(
                "{\"frame\":15129,\"vfc\":15130,\"event\":\"routine_change\","
                + "\"from\":\"0x02\",\"to\":\"0x04\","
                + "\"sonic_x\":\"0x3280\",\"sonic_y\":\"0x02C8\","
                + "\"x_vel\":-512,\"y_vel\":-1024,\"inertia\":0,"
                + "\"status\":\"0x02\",\"stand_on_obj\":4,"
                + "\"stand_obj_slot\":4,\"stand_obj_type\":\"0x00051FCE\","
                + "\"stand_obj_x\":\"0x0000\",\"stand_obj_y\":\"0x0000\","
                + "\"stand_obj_routine\":\"0x04\"}",
                routineChanges[0]);
            // Hurt routine (0x04) forces an extra state_snapshot.
            AssertEx.Equal(1, Of(lines, "state_snapshot").Count);
        }

        private static void CpuStateMatchesCurrentContract()
        {
            var host = NewHost();
            StageCnzFrame0(host);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> lines = Of(engine.ProcessFrame(0, host), "cpu_state");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"cpu_state\","
                + "\"character\":\"tails\",\"interact\":\"0x0000\","
                + "\"idle_timer\":0,\"flight_timer\":0,\"cpu_routine\":12,"
                + "\"target_x\":\"0x0000\",\"target_y\":\"0x0000\","
                + "\"auto_fly_timer\":0,\"auto_jump_flag\":0,"
                + "\"ctrl2_held\":\"0x00\",\"ctrl2_pressed\":\"0x00\","
                + "\"pos_table_index\":\"0x0004\"}",
                lines[0]);
        }

        private static void OscillationStateMatchesCurrentContract()
        {
            var host = NewHost();
            StageCnzFrame0(host);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> lines = Of(engine.ProcessFrame(0, host),
                "oscillation_state");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"oscillation_state\","
                + "\"level_frame_counter\":0,\"osc_table\":\""
                + "007D008200020082000200820002008200020084000400880008"
                + "008800080084000400820002393400EC213200B2318B010B523D"
                + "01BD72EF026F0082000240FC00FC\"}",
                lines[0]);
        }

        private static void ControlLockState()
        {
            var host = NewHost();
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> first = Of(engine.ProcessFrame(0, host),
                "control_lock_state");
            AssertEx.Equal(1, first.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"control_lock_state\","
                + "\"ctrl1_locked\":0,\"ctrl2_locked\":0,"
                + "\"ctrl1_logical\":\"0x0000\",\"ctrl2_logical\":\"0x0000\"}",
                first[0]);

            // Unchanged mid-interval: suppressed.
            AssertEx.Equal(0,
                Of(engine.ProcessFrame(1, host), "control_lock_state").Count);

            // Change: emits.
            host.Ram[0xF7CA] = 1;
            AssertEx.Equal(1,
                Of(engine.ProcessFrame(2, host), "control_lock_state").Count);

            // 60-frame baseline: forced even with no change.
            AssertEx.Equal(1,
                Of(engine.ProcessFrame(60, host), "control_lock_state").Count);
        }

        private static void ObjectStateProximity()
        {
            var host = NewHost();
            StageCnzFrame0(host);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> lines = Of(engine.ProcessFrame(0, host),
                "object_state");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"object_state\","
                + "\"slot\":1,\"object_code\":\"0x0001365C\","
                + "\"routine\":\"0x02\",\"status\":\"0x02\","
                + "\"subtype\":\"0x1E\",\"x\":\"0x0018\",\"y\":\"0x0600\","
                + "\"x_radius\":9,\"y_radius\":15}",
                lines[0]);

            // Far from P1 but near P2 (Tails at slot 1): P2 arm keeps it.
            host.SetU32(Slot(8), 0x0002D690);
            host.SetU16(Slot(8) + 0x10, 0x0060);
            host.SetU16(Slot(8) + 0x14, 0x0600);
            host.SetU16(0xB010, 0x0800);   // move P1 far away
            List<string> nearP2 = Of(engine.ProcessFrame(1, host),
                "object_state");
            AssertEx.Equal(2, nearP2.Count);   // Tails self-slot + slot 8

            // Far from both players: dropped.
            host.SetU16(Slot(1) + 0x10, 0x0800);
            List<string> far = Of(engine.ProcessFrame(2, host),
                "object_state");
            AssertEx.Equal(1, far.Count);      // only Tails self-slot near P1
        }

        private static void InteractStates()
        {
            var host = NewHost();
            StageCnzFrame0(host);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> lines = Of(engine.ProcessFrame(0, host),
                "interact_state");
            AssertEx.Equal(2, lines.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"interact_state\","
                + "\"character\":\"sonic\",\"interact\":\"0x0000\","
                + "\"interact_slot\":0,\"status\":\"0x02\","
                + "\"status_secondary\":\"0x00\",\"object_control\":\"0x00\"}",
                lines[0]);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"interact_state\","
                + "\"character\":\"tails\",\"interact\":\"0x0000\","
                + "\"interact_slot\":0,\"status\":\"0x02\","
                + "\"status_secondary\":\"0x00\",\"object_control\":\"0x00\"}",
                lines[1]);

            // Sidekick absent: sonic only, and no sidekick_interact_object.
            host.SetU32(Slot(1), 0);
            IList<string> absent = engine.ProcessFrame(1, host);
            AssertEx.Equal(1, Of(absent, "interact_state").Count);
            AssertEx.Equal(0, Of(absent, "sidekick_interact_object").Count);
        }

        private static void SidekickInteractObject()
        {
            var host = NewHost();
            StageCnzFrame0(host);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> lines = Of(engine.ProcessFrame(0, host),
                "sidekick_interact_object");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"sidekick_interact_object\","
                + "\"character\":\"tails\",\"interact\":\"0x0000\","
                + "\"interact_slot\":0,\"tails_render_flags\":\"0x84\","
                + "\"tails_object_control\":\"0x00\","
                + "\"tails_invulnerability_timer\":\"0x00\","
                + "\"tails_width_pixels\":\"0x18\","
                + "\"tails_height_pixels\":\"0x18\","
                + "\"camera_x_copy\":\"0x0000\",\"camera_y_copy\":\"0x05A0\","
                + "\"tails_status\":\"0x02\",\"tails_on_object\":false,"
                + "\"object_code\":\"0x00000000\",\"object_routine\":\"0x00\","
                + "\"object_status\":\"0x00\",\"object_x\":\"0x0000\","
                + "\"object_y\":\"0x0000\",\"object_subtype\":\"0x00\","
                + "\"object_render_flags\":\"0x00\","
                + "\"object_object_control\":\"0x00\","
                + "\"object_active\":false,\"object_destroyed\":true,"
                + "\"object_p1_standing\":false,\"object_p2_standing\":false}",
                lines[0]);
        }

        private static void AirCountdownEmpty()
        {
            var host = NewHost();
            host.SetU32(0xF636, 0x14A7ABBB);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> lines = Of(engine.ProcessFrame(0, host),
                "air_countdown_state");
            AssertEx.Equal(2, lines.Count);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"air_countdown_state\","
                + "\"owner\":\"p1\",\"fixed_slot\":94,"
                + "\"object_code\":\"0x00000000\",\"routine\":\"0x00\","
                + "\"subtype\":\"0x00\",\"obj30\":\"0x0000\","
                + "\"obj36\":\"0x00\",\"obj37\":\"0x00\",\"obj38\":\"0x00\","
                + "\"obj3a\":\"0x0000\",\"obj3c\":\"0x0000\","
                + "\"obj3e\":\"0x0000\",\"owner_ptr\":\"0x00000000\","
                + "\"owner_resolved\":\"unknown\",\"owner_air_left\":\"0x00\","
                + "\"owner_status\":\"0x00\","
                + "\"owner_status_secondary\":\"0x00\","
                + "\"owner_facing_left\":false,\"owner_underwater\":false,"
                + "\"rng_seed\":\"0x14A7ABBB\",\"visible_children\":[]}",
                lines[0]);
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":0,\"event\":\"air_countdown_state\","
                + "\"owner\":\"p2\",\"fixed_slot\":95,"
                + "\"object_code\":\"0x00000000\",\"routine\":\"0x00\","
                + "\"subtype\":\"0x00\",\"obj30\":\"0x0000\","
                + "\"obj36\":\"0x00\",\"obj37\":\"0x00\",\"obj38\":\"0x00\","
                + "\"obj3a\":\"0x0000\",\"obj3c\":\"0x0000\","
                + "\"obj3e\":\"0x0000\",\"owner_ptr\":\"0x00000000\","
                + "\"owner_resolved\":\"unknown\",\"owner_air_left\":\"0x00\","
                + "\"owner_status\":\"0x00\","
                + "\"owner_status_secondary\":\"0x00\","
                + "\"owner_facing_left\":false,\"owner_underwater\":false,"
                + "\"rng_seed\":\"0x14A7ABBB\",\"visible_children\":[]}",
                lines[1]);
        }

        private static void AirCountdownWithChildren()
        {
            var host = NewHost();
            // Fixture ADDR_FRAMECOUNT (Level_frame_counter, 0xFE04)
            // at that instant; the recorder reads it live since Lua
            // v6.31-s3k.
            host.SetU16(0xFE04, 17822);
            host.SetU32(0xF636, 0xF3F7AD13);
            // Fixed slot 94: Obj_AirCountdown controller owned by P1.
            int addr = Slot(94);
            host.SetU32(addr, 0x00018164);
            host.Ram[addr + 0x05] = 0x0A;
            host.Ram[addr + 0x2C] = 0x81;
            host.Ram[addr + 0x37] = 0x01;
            host.SetU16(addr + 0x3A, 0x0001);
            host.SetU16(addr + 0x3C, 0x003B);
            host.SetU16(addr + 0x3E, 0x000F);
            host.SetU32(addr + 0x40, 0xFFFFB000);
            // Owner P1 block: air_left 0x1D, underwater status.
            host.Ram[0xB000 + 0x2C] = 0x1D;
            host.Ram[0xB000 + 0x2A] = 0x40;
            // Visible child in dynamic slot 6.
            int child = Slot(6);
            host.SetU32(child, 0x00018164);
            host.Ram[child + 0x2C] = 0x06;
            host.SetU16(child + 0x10, 0x0FC0);
            host.SetU16(child + 0x14, 0x0A97);
            host.SetU32(child + 0x40, 0xFFFFB000);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> lines = Of(engine.ProcessFrame(17824, host),
                "air_countdown_state");
            AssertEx.Equal(2, lines.Count);
            AssertEx.Equal(
                "{\"frame\":17824,\"vfc\":17822,\"event\":\"air_countdown_state\","
                + "\"owner\":\"p1\",\"fixed_slot\":94,"
                + "\"object_code\":\"0x00018164\",\"routine\":\"0x0A\","
                + "\"subtype\":\"0x81\",\"obj30\":\"0x0000\","
                + "\"obj36\":\"0x00\",\"obj37\":\"0x01\",\"obj38\":\"0x00\","
                + "\"obj3a\":\"0x0001\",\"obj3c\":\"0x003B\","
                + "\"obj3e\":\"0x000F\",\"owner_ptr\":\"0xFFFFB000\","
                + "\"owner_resolved\":\"p1\",\"owner_air_left\":\"0x1D\","
                + "\"owner_status\":\"0x40\","
                + "\"owner_status_secondary\":\"0x00\","
                + "\"owner_facing_left\":false,\"owner_underwater\":true,"
                + "\"rng_seed\":\"0xF3F7AD13\",\"visible_children\":["
                + "{\"slot\":6,\"object_code\":\"0x00018164\","
                + "\"routine\":\"0x00\",\"subtype\":\"0x06\","
                + "\"x\":\"0x0FC0\",\"y\":\"0x0A97\",\"x_sub\":\"0x0000\","
                + "\"y_sub\":\"0x0000\",\"y_vel\":\"0x0000\","
                + "\"render_flags\":\"0x00\",\"anim\":\"0x00\","
                + "\"mapping_frame\":\"0x00\",\"anim_frame\":\"0x00\","
                + "\"anim_frame_timer\":\"0x00\",\"angle\":\"0x00\","
                + "\"obj34\":\"0x0000\",\"obj3c\":\"0x0000\","
                + "\"parent_ptr\":\"0xFFFFB000\"}]}",
                lines[0]);
        }

        private static void CageState()
        {
            var host = NewHost();
            // Fixture ADDR_FRAMECOUNT (Level_frame_counter, 0xFE04)
            // at that instant; the recorder reads it live since Lua
            // v6.31-s3k.
            host.SetU16(0xFE04, 1650);
            int addr = Slot(4);
            host.SetU32(addr, 0x0003385E);
            host.SetU16(addr + 0x10, 0x1300);
            host.SetU16(addr + 0x14, 0x07C0);
            host.Ram[addr + 0x2C] = 0x28;
            host.Ram[addr + 0x2A] = 0x01;
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            List<string> lines = Of(engine.ProcessFrame(1649, host),
                "cage_state");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":1649,\"vfc\":1650,\"event\":\"cage_state\","
                + "\"slot\":4,\"x\":\"0x1300\",\"y\":\"0x07C0\","
                + "\"subtype\":\"0x28\",\"status\":\"0x01\","
                + "\"p1_phase\":\"0x00\",\"p1_state\":\"0x00\","
                + "\"p2_phase\":\"0x00\",\"p2_state\":\"0x00\"}",
                lines[0]);
        }

        private static void CnzCylinderState()
        {
            var host = NewHost();
            // Fixture ADDR_FRAMECOUNT (Level_frame_counter, 0xFE04)
            // at that instant; the recorder reads it live since Lua
            // v6.31-s3k.
            host.SetU16(0xFE04, 4491);
            int addr = Slot(9);
            host.SetU32(addr, 0x00032188);
            host.SetU16(addr + 0x10, 0x1BB6);
            host.SetU16(addr + 0x14, 0x07E0);
            host.Ram[addr + 0x2C] = 0x41;
            host.Ram[addr + 0x2A] = 0x11;
            host.Ram[addr + 0x04] = 0x84;
            SetBytes(host, addr + 0x32, "0046197F01000310");
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);

            AssertEx.Equal(0,
                Of(engine.ProcessFrame(4489, host), "cnz_cylinder_state").Count);
            List<string> lines = Of(engine.ProcessFrame(4490, host),
                "cnz_cylinder_state");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":4490,\"vfc\":4491,\"event\":\"cnz_cylinder_state\","
                + "\"slot\":9,\"x\":\"0x1BB6\",\"y\":\"0x07E0\","
                + "\"subtype\":\"0x41\",\"status\":\"0x11\","
                + "\"routine\":\"0x00\",\"render_flags\":\"0x84\","
                + "\"p1_state\":\"0x00\",\"p1_angle\":\"0x46\","
                + "\"p1_distance\":\"0x19\",\"p1_threshold\":\"0x7F\","
                + "\"p2_state\":\"0x01\",\"p2_angle\":\"0x00\","
                + "\"p2_distance\":\"0x03\",\"p2_threshold\":\"0x10\"}",
                lines[0]);
            AssertEx.Equal(0,
                Of(engine.ProcessFrame(4513, host), "cnz_cylinder_state").Count);
        }

        private static void CollisionResponseListEndOfFrame()
        {
            var host = NewHost();
            // Fixture ADDR_FRAMECOUNT (Level_frame_counter, 0xFE04)
            // at that instant; the recorder reads it live since Lua
            // v6.31-s3k.
            host.SetU16(0xFE04, 619);
            host.Ram[0xFE10] = 0x03;
            host.SetU16(0xE380, 6);
            host.SetU16(0xE382, (ushort)Slot(4));
            host.SetU16(0xE384, (ushort)Slot(5));
            host.SetU16(0xE386, (ushort)Slot(7));
            host.SetU32(Slot(4), 0x00088FBA);
            host.Ram[Slot(4) + 0x28] = 0x0A;
            host.SetU16(Slot(4) + 0x10, 0x0578);
            host.SetU16(Slot(4) + 0x14, 0x0690);
            host.SetU32(Slot(5), 0x000890AA);
            host.Ram[Slot(5) + 0x28] = 0xD7;
            host.SetU16(Slot(5) + 0x10, 0x0578);
            host.SetU16(Slot(5) + 0x14, 0x0688);
            host.SetU32(Slot(7), 0x00031754);
            host.Ram[Slot(7) + 0x28] = 0xD7;
            host.SetU16(Slot(7) + 0x10, 0x06C0);
            host.SetU16(Slot(7) + 0x14, 0x061F);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);

            AssertEx.Equal(0, Of(engine.ProcessFrame(617, host),
                "collision_response_list_end_of_frame").Count);
            List<string> lines = Of(engine.ProcessFrame(618, host),
                "collision_response_list_end_of_frame");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":618,\"vfc\":619,"
                + "\"event\":\"collision_response_list_end_of_frame\","
                + "\"list_count\":6,\"list_entries\":["
                + "{\"slot\":4,\"ost_lo\":\"0xB128\","
                + "\"object_code\":\"0x00088FBA\","
                + "\"collision_flags\":\"0x0A\","
                + "\"collision_property\":\"0x00\",\"x_pos\":\"0x0578\","
                + "\"y_pos\":\"0x0690\"},"
                + "{\"slot\":5,\"ost_lo\":\"0xB172\","
                + "\"object_code\":\"0x000890AA\","
                + "\"collision_flags\":\"0xD7\","
                + "\"collision_property\":\"0x00\",\"x_pos\":\"0x0578\","
                + "\"y_pos\":\"0x0688\",\"routine_label\":\"loc_890AA_fire\"},"
                + "{\"slot\":7,\"ost_lo\":\"0xB206\","
                + "\"object_code\":\"0x00031754\","
                + "\"collision_flags\":\"0xD7\","
                + "\"collision_property\":\"0x00\",\"x_pos\":\"0x06C0\","
                + "\"y_pos\":\"0x061F\"}],\"spring_children\":["
                + "{\"slot\":5,\"ost_lo\":\"0xB172\","
                + "\"object_code\":\"0x000890AA\","
                + "\"routine_label\":\"loc_890AA_fire\","
                + "\"x_pos\":\"0x0578\",\"y_pos\":\"0x0688\","
                + "\"collision_property\":\"0x00\","
                + "\"collision_flags\":\"0xD7\",\"cooldown_byte\":\"0x00\"}]}",
                lines[0]);

            // Outside zone 3 the family never emits, window or not.
            host.Ram[0xFE10] = 0x00;
            AssertEx.Equal(0, Of(engine.ProcessFrame(619, host),
                "collision_response_list_end_of_frame").Count);
        }

        private static void AizFireTransition()
        {
            var host = NewHost();
            // Fixture ADDR_FRAMECOUNT (Level_frame_counter, 0xFE04)
            // at that instant; the recorder reads it live since Lua
            // v6.31-s3k.
            host.SetU16(0xFE04, 4911);
            host.Ram[0xFE10] = 0x00;
            host.SetU32(0xEE90, 0x01700000);
            host.SetU16(0xEE96, 0x0170);
            host.SetU16(0xEED2, 0xFFFF);
            host.SetU16(0xEEC2, 0x0008);
            host.SetU16(0xEE78, 0x2F10);
            host.SetU16(0xEE14, 0x2F10);
            host.SetU16(0xEE16, 0x2F10);
            host.SetU16(0xB010, 0x2FFB);
            var engine = new S3KAuxEventEngine(S3KTraceProfile.AizEndToEnd);
            List<string> lines = Of(engine.ProcessFrame(5200, host),
                "aiz_fire_transition");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":5200,\"vfc\":4911,\"event\":\"aiz_fire_transition\","
                + "\"camera_y_bg_copy\":\"0x01700000\","
                + "\"camera_y_bg_rounded\":\"0x0170\","
                + "\"events_bg_00_word\":\"0xFFFF\","
                + "\"events_bg_02_word\":\"0x0000\","
                + "\"events_routine_bg\":\"0x0008\","
                + "\"events_fg_5\":\"0x0000\",\"camera_x\":\"0x2F10\","
                + "\"camera_min_x\":\"0x2F10\",\"camera_max_x\":\"0x2F10\","
                + "\"player_x\":\"0x2FFB\",\"act\":\"0x00\"}",
                lines[0]);

            // Profile gate: never emitted by the level-gated profile.
            var levelGated = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            AssertEx.Equal(0, Of(levelGated.ProcessFrame(5200, host),
                "aiz_fire_transition").Count);
        }

        private static void TerrainWallSensor()
        {
            var host = NewHost();
            // Fixture ADDR_FRAMECOUNT (Level_frame_counter, 0xFE04)
            // at that instant; the recorder reads it live since Lua
            // v6.31-s3k.
            host.SetU16(0xFE04, 7250);
            host.Ram[0xFE10] = 0x00;
            // Sonic (AIZ F7549): airborne + rolling against the wall clamp.
            host.SetU16(0xB010, 0x11F4);
            host.SetU16(0xB012, 0x0200);
            host.SetU16(0xB014, 0x032F);
            host.SetU16(0xB016, 0xC800);
            host.SetU16(0xB018, 0xFE41);
            host.SetU16(0xB01A, 0x04C0);
            host.Ram[0xB02A] = 0x06;
            host.Ram[0xB02B] = 0x11;
            host.Ram[0xB01F] = 7;
            host.Ram[0xB01E] = 14;
            host.Ram[0xB046] = 0x0C;
            host.Ram[0xB047] = 0x0D;
            // Tails.
            host.SetU16(Slot(1) + 0x10, 0x1201);
            host.SetU16(Slot(1) + 0x12, 0x5900);
            host.SetU16(Slot(1) + 0x14, 0x031E);
            host.SetU16(Slot(1) + 0x16, 0xF700);
            host.SetU16(Slot(1) + 0x18, 0x0200);
            host.SetU16(Slot(1) + 0x1A, 0xFC30);
            host.Ram[Slot(1) + 0x2A] = 0x02;
            host.Ram[Slot(1) + 0x1F] = 9;
            host.Ram[Slot(1) + 0x1E] = 15;
            host.Ram[Slot(1) + 0x46] = 0x0C;
            host.Ram[Slot(1) + 0x47] = 0x0D;
            var engine = new S3KAuxEventEngine(S3KTraceProfile.AizEndToEnd);
            List<string> lines = Of(engine.ProcessFrame(7549, host),
                "terrain_wall_sensor");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":7549,\"vfc\":7250,\"event\":\"terrain_wall_sensor\","
                + "\"sonic\":{\"x_pos\":\"0x11F4\",\"x_sub\":\"0x0200\","
                + "\"y_pos\":\"0x032F\",\"y_sub\":\"0xC800\","
                + "\"x_vel\":\"0xFE41\",\"y_vel\":\"0x04C0\","
                + "\"angle\":\"0x00\",\"status\":\"0x06\","
                + "\"status2\":\"0x11\",\"object_control\":\"0x00\","
                + "\"x_radius\":7,\"y_radius\":14,"
                + "\"top_solid_bit\":\"0x0C\",\"lrb_solid_bit\":\"0x0D\","
                + "\"airborne\":true},"
                + "\"tails\":{\"x_pos\":\"0x1201\",\"x_sub\":\"0x5900\","
                + "\"y_pos\":\"0x031E\",\"y_sub\":\"0xF700\","
                + "\"x_vel\":\"0x0200\",\"y_vel\":\"0xFC30\","
                + "\"angle\":\"0x00\",\"status\":\"0x02\","
                + "\"status2\":\"0x00\",\"object_control\":\"0x00\","
                + "\"x_radius\":9,\"y_radius\":15,"
                + "\"top_solid_bit\":\"0x0C\",\"lrb_solid_bit\":\"0x0D\","
                + "\"airborne\":true}}",
                lines[0]);

            // Zone gate: same window frame outside AIZ emits nothing.
            host.Ram[0xFE10] = 0x01;
            AssertEx.Equal(0, Of(engine.ProcessFrame(7550, host),
                "terrain_wall_sensor").Count);
        }

        private static void AizHandoffTerrainState()
        {
            var host = NewHost();
            // Fixture ADDR_FRAMECOUNT (Level_frame_counter, 0xFE04)
            // at that instant; the recorder reads it live since Lua
            // v6.31-s3k.
            host.SetU16(0xFE04, 5141);
            host.Ram[0xFE10] = 0x00;
            host.Ram[0xFE11] = 0x00;
            host.SetU16(0xEEC2, 0x0014);
            host.SetU16(0xEEC8, 0xFFF0);
            host.SetU16(0xEECA, 0xFFFF);
            host.Ram[0xEE33] = 0x08;
            host.Ram[0xF76C] = 0x04;
            host.Ram[0xF710] = 0x02;
            host.SetU16(0xB010, 0x2FCD);
            host.SetU16(0xB014, 0x0379);
            host.Ram[0xB01E] = 0x13;
            host.Ram[0xB046] = 0x0C;
            var engine = new S3KAuxEventEngine(S3KTraceProfile.AizEndToEnd);

            AssertEx.Equal(0, Of(engine.ProcessFrame(5429, host),
                "aiz_handoff_terrain_state").Count);
            List<string> lines = Of(engine.ProcessFrame(5430, host),
                "aiz_handoff_terrain_state");
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":5430,\"vfc\":5141,"
                + "\"event\":\"aiz_handoff_terrain_state\","
                + "\"events_bg\":\"0x0014\",\"draw_pos\":\"0xFFF0\","
                + "\"draw_rows\":\"0xFFFF\",\"kos_modules_left\":\"0x00\","
                + "\"current_zone_act\":\"0x0000\","
                + "\"dynamic_resize\":\"0x08\",\"object_load\":\"0x04\","
                + "\"rings_manager\":\"0x02\",\"p1_x\":\"0x2FCD\","
                + "\"p1_y\":\"0x0379\",\"p1_status\":\"0x00\","
                + "\"p1_y_radius\":\"0x13\",\"p1_top_solid\":\"0x0C\","
                + "\"sonic_floor_seen\":false,"
                + "\"sonic_floor_distance\":\"0x0000\","
                + "\"sonic_floor_angle\":\"0x00\","
                + "\"sonic_floor_probe_x\":\"0x0000\","
                + "\"sonic_floor_probe_y\":\"0x0000\","
                + "\"solid_vertical_seen\":false,"
                + "\"solid_pre_y\":\"0x0000\","
                + "\"solid_surface_y\":\"0x0000\","
                + "\"solid_delta\":\"0x0000\"}",
                lines[0]);
            AssertEx.Equal(0, Of(engine.ProcessFrame(5439, host),
                "aiz_handoff_terrain_state").Count);
        }

        private static void ScanObjectsLiterals()
        {
            var host = NewHost();
            // Slot 1: plain object at P1's position (appeared + near).
            host.SetU32(Slot(1), 0x0001365C);
            host.SetU16(Slot(1) + 0x10, 0x0018);
            host.SetU16(Slot(1) + 0x14, 0x0600);
            host.Ram[Slot(1) + 0x05] = 0x02;
            host.Ram[Slot(1) + 0x2A] = 0x02;
            host.SetU16(0xB010, 0x0018);
            host.SetU16(0xB014, 0x0600);
            // Slots 4 and 7: CNZ balloons (appeared extra + slot_dump).
            foreach (int slot in new[] { 4, 7 })
            {
                host.SetU32(Slot(slot), 0x00031754);
                host.SetU16(Slot(slot) + 0x10, 0x0180);
                host.SetU16(Slot(slot) + 0x14, 0x0683);
                host.Ram[Slot(slot) + 0x26] = 0x12;
                host.SetU16(Slot(slot) + 0x32, 0x0680);
            }
            // Slot 12: appears now, removed next frame.
            host.SetU32(Slot(12), 0x0002D8E2);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            IList<string> first = engine.ProcessFrame(2613, host);

            List<string> appeared = Of(first, "object_appeared");
            AssertEx.Equal(4, appeared.Count);
            AssertEx.Equal(
                "{\"frame\":2613,\"vfc\":0,\"event\":\"object_appeared\","
                + "\"slot\":1,\"object_type\":\"0x0001365C\","
                + "\"x\":\"0x0018\",\"y\":\"0x0600\"}",
                appeared[0]);
            AssertEx.Equal(
                "{\"frame\":2613,\"vfc\":0,\"event\":\"object_appeared\","
                + "\"slot\":4,\"object_type\":\"0x00031754\","
                + "\"x\":\"0x0180\",\"y\":\"0x0683\",\"angle\":\"0x12\","
                + "\"base_y\":\"0x0680\"}",
                appeared[1]);

            List<string> near = Of(first, "object_near");
            AssertEx.Equal(1, near.Count);
            AssertEx.Equal(
                "{\"frame\":2613,\"vfc\":0,\"event\":\"object_near\","
                + "\"slot\":1,\"type\":\"0x0001365C\",\"x\":\"0x0018\","
                + "\"y\":\"0x0600\",\"routine\":\"0x02\",\"status\":\"0x02\"}",
                near[0]);

            // slot_dump lists dynamic slots 3..92 only (slots 4, 7, 12).
            List<string> dump = Of(first, "slot_dump");
            AssertEx.Equal(1, dump.Count);
            AssertEx.Equal(
                "{\"frame\":2613,\"vfc\":0,\"event\":\"slot_dump\","
                + "\"slots\":[[4,\"0x00031754\"],[7,\"0x00031754\"],"
                + "[12,\"0x0002D8E2\"]]}",
                dump[0]);

            // Removal next frame; no appearances, so no slot_dump.
            host.SetU32(Slot(12), 0);
            IList<string> second = engine.ProcessFrame(2614, host);
            List<string> removed = Of(second, "object_removed");
            AssertEx.Equal(1, removed.Count);
            AssertEx.Equal(
                "{\"frame\":2614,\"vfc\":0,\"event\":\"object_removed\","
                + "\"slot\":12,\"object_type\":\"0x0002D8E2\"}",
                removed[0]);
            AssertEx.Equal(0, Of(second, "slot_dump").Count);

            // Balloon proximity carries the extra on object_near too
            // (CNZ fixture F146 literal, balloon risen to y 0x0679). Only
            // this block replays a real fixture line, so only it stages the
            // fixture's ADDR_FRAMECOUNT (Level_frame_counter, 0xFE04 — read
            // live since Lua v6.31-s3k); the frame-2613/2614 assertions
            // above are composed staging and leave it 0.
            host.SetU16(0xFE04, 147);
            host.SetU16(Slot(4) + 0x10, 0x0180);
            host.SetU16(Slot(4) + 0x14, 0x0679);
            host.Ram[Slot(4) + 0x26] = 0xA4;
            host.SetU16(0xB010, 0x0180);
            IList<string> third = engine.ProcessFrame(146, host);
            List<string> balloonNear = Of(third, "object_near");
            AssertEx.Equal(true, balloonNear.Count >= 1);
            string balloonLine = null;
            foreach (string line in balloonNear)
            {
                if (line.IndexOf("\"slot\":4", StringComparison.Ordinal) >= 0)
                {
                    balloonLine = line;
                }
            }
            AssertEx.Equal(
                "{\"frame\":146,\"vfc\":147,\"event\":\"object_near\","
                + "\"slot\":4,\"type\":\"0x00031754\",\"x\":\"0x0180\","
                + "\"y\":\"0x0679\",\"routine\":\"0x00\",\"status\":\"0x00\","
                + "\"angle\":\"0xA4\",\"base_y\":\"0x0680\"}",
                balloonLine);
        }

        private static void Frame0EmissionOrder()
        {
            var host = NewHost();
            StageCnzFrame0(host);
            var engine = new S3KAuxEventEngine(
                S3KTraceProfile.LevelGatedResetAware);
            IList<string> lines = engine.ProcessFrame(0, host);
            // The CNZ fixture's frame-0 event sequence, reduced to the
            // families this minimal RAM staging produces (slot 1 only, so
            // one object_state / object_appeared / object_near and an
            // empty-list slot_dump).
            string[] expected =
            {
                "zone_act_state",
                "checkpoint",
                "player_mode_set",
                "mode_change",
                "state_snapshot",
                "routine_change",
                "cpu_state",
                "oscillation_state",
                "object_state",
                "interact_state",
                "interact_state",
                "sidekick_interact_object",
                "air_countdown_state",
                "air_countdown_state",
                "state_snapshot",
                "control_lock_state",
                "object_appeared",
                "object_near",
                "slot_dump"
            };
            AssertEx.Equal(expected.Length, lines.Count);
            for (int i = 0; i < expected.Length; i++)
            {
                AssertEx.Equal(expected[i], EventName(lines[i]));
            }
        }
    }
}
