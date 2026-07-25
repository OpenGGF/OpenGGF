using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Literal-fixture coverage for the S2 arm-time aux emission
    /// (S2AuxEventEngine.EmitArmEvents) and the step-1 per-frame
    /// zone_act_state/checkpoint events (ProcessFrameStart). The RAM
    /// hydration parses the raw byte/list fields of the SAME literal
    /// fixture lines (the off_XX bytes and history lists ARE the RAM
    /// contents); every alias, derived field, and ordering decision is
    /// computed independently by the engine, so the byte comparison
    /// exercises the full formatter surface.
    /// </summary>
    internal static class S2AuxArmBlockTests
    {
        // LITERAL lines 1-9 of the gunzipped
        // src/test/resources/traces/s2/ehz1_fullrun/aux_state.jsonl.gz:
        // player_history_snapshot, cpu_state_snapshot, five
        // object_state_snapshot lines (slots 1-4 and 16), then the arm-time
        // zone_act_state and gameplay_start checkpoint.
        private static readonly string[] Ehz1ArmBlock =
        {
            "{\"frame\":-1,\"vfc\":0,\"event\":\"player_history_snapshot\",\"history_pos\":104,\"x_history\":[96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,96,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64,64],\"y_history\":[656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,656,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659,659],\"input_history\":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],\"status_history\":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]}",
            "{\"frame\":-1,\"vfc\":0,\"event\":\"cpu_state_snapshot\",\"character\":\"tails\",\"control_counter\":0,\"respawn_counter\":0,\"cpu_routine\":6,\"target_x\":\"0x0000\",\"target_y\":\"0x0000\",\"interact_id\":\"0x01\",\"jumping\":0}",
            "{\"frame\":-1,\"vfc\":0,\"event\":\"object_state_snapshot\",\"slot\":1,\"object_type\":\"0x02\",\"fields\":{\"off_00\":\"0x02\",\"off_01\":\"0x84\",\"off_02\":\"0x07\",\"off_03\":\"0xA0\",\"off_04\":\"0x00\",\"off_05\":\"0x07\",\"off_06\":\"0x39\",\"off_07\":\"0xE2\",\"off_08\":\"0x00\",\"off_09\":\"0x4B\",\"off_0A\":\"0x94\",\"off_0B\":\"0x00\",\"off_0C\":\"0x02\",\"off_0D\":\"0x94\",\"off_0E\":\"0x00\",\"off_0F\":\"0x00\",\"off_10\":\"0x00\",\"off_11\":\"0x78\",\"off_12\":\"0x00\",\"off_13\":\"0x00\",\"off_14\":\"0x00\",\"off_15\":\"0x78\",\"off_16\":\"0x0F\",\"off_17\":\"0x09\",\"off_18\":\"0x02\",\"off_19\":\"0x18\",\"off_1A\":\"0x11\",\"off_1B\":\"0x02\",\"off_1C\":\"0x00\",\"off_1D\":\"0x00\",\"off_1E\":\"0x06\",\"off_1F\":\"0x00\",\"off_20\":\"0x00\",\"off_21\":\"0x00\",\"off_22\":\"0x00\",\"off_23\":\"0x00\",\"off_24\":\"0x02\",\"off_25\":\"0x00\",\"off_26\":\"0x00\",\"off_27\":\"0x00\",\"off_28\":\"0x1E\",\"off_29\":\"0x00\",\"off_2A\":\"0x00\",\"off_2B\":\"0x00\",\"off_2C\":\"0x00\",\"off_2D\":\"0x04\",\"off_2E\":\"0x00\",\"off_2F\":\"0x00\",\"off_30\":\"0x00\",\"off_31\":\"0x00\",\"off_32\":\"0x00\",\"off_33\":\"0x00\",\"off_34\":\"0x00\",\"off_35\":\"0x00\",\"off_36\":\"0xFF\",\"off_37\":\"0x01\",\"off_38\":\"0x00\",\"off_39\":\"0x00\",\"off_3A\":\"0x00\",\"off_3B\":\"0x00\",\"off_3C\":\"0x00\",\"off_3D\":\"0x00\",\"off_3E\":\"0x0C\",\"off_3F\":\"0x0D\",\"x_pos\":\"0x004B\",\"x_sub\":\"0x9400\",\"y_pos\":\"0x0294\",\"y_sub\":\"0x0000\",\"x_vel\":\"0x0078\",\"y_vel\":\"0x0000\",\"id\":\"0x02\",\"render_flags\":\"0x84\",\"status\":\"0x00\",\"routine\":\"0x02\",\"routine_secondary\":\"0x00\",\"mapping_frame\":\"0x11\",\"anim\":\"0x00\",\"anim_frame\":\"0x02\",\"anim_frame_timer\":\"0x06\",\"subtype\":\"0x1E\"}}",
            "{\"frame\":-1,\"vfc\":0,\"event\":\"object_state_snapshot\",\"slot\":2,\"object_type\":\"0x34\",\"fields\":{\"off_00\":\"0x34\",\"off_01\":\"0x80\",\"off_02\":\"0x00\",\"off_03\":\"0x00\",\"off_04\":\"0x00\",\"off_05\":\"0x01\",\"off_06\":\"0x47\",\"off_07\":\"0xBA\",\"off_08\":\"0x01\",\"off_09\":\"0x20\",\"off_0A\":\"0x00\",\"off_0B\":\"0xB8\",\"off_0C\":\"0x00\",\"off_0D\":\"0x00\",\"off_0E\":\"0x00\",\"off_0F\":\"0x00\",\"off_10\":\"0x00\",\"off_11\":\"0x00\",\"off_12\":\"0x00\",\"off_13\":\"0x00\",\"off_14\":\"0x00\",\"off_15\":\"0x00\",\"off_16\":\"0x00\",\"off_17\":\"0x00\",\"off_18\":\"0x00\",\"off_19\":\"0x80\",\"off_1A\":\"0x00\",\"off_1B\":\"0x00\",\"off_1C\":\"0x00\",\"off_1D\":\"0x00\",\"off_1E\":\"0x00\",\"off_1F\":\"0x2D\",\"off_20\":\"0x00\",\"off_21\":\"0x00\",\"off_22\":\"0x00\",\"off_23\":\"0x00\",\"off_24\":\"0x16\",\"off_25\":\"0x00\",\"off_26\":\"0x00\",\"off_27\":\"0x00\",\"off_28\":\"0x00\",\"off_29\":\"0x00\",\"off_2A\":\"0x00\",\"off_2B\":\"0x00\",\"off_2C\":\"0x00\",\"off_2D\":\"0x00\",\"off_2E\":\"0x00\",\"off_2F\":\"0x00\",\"off_30\":\"0x01\",\"off_31\":\"0x20\",\"off_32\":\"0x02\",\"off_33\":\"0x40\",\"off_34\":\"0x00\",\"off_35\":\"0x00\",\"off_36\":\"0x00\",\"off_37\":\"0x00\",\"off_38\":\"0x00\",\"off_39\":\"0x00\",\"off_3A\":\"0x00\",\"off_3B\":\"0x00\",\"off_3C\":\"0x00\",\"off_3D\":\"0x00\",\"off_3E\":\"0xFF\",\"off_3F\":\"0xFF\",\"x_pos\":\"0x0120\",\"x_sub\":\"0x00B8\",\"y_pos\":\"0x0000\",\"y_sub\":\"0x0000\",\"x_vel\":\"0x0000\",\"y_vel\":\"0x0000\",\"id\":\"0x34\",\"render_flags\":\"0x80\",\"status\":\"0x00\",\"routine\":\"0x16\",\"routine_secondary\":\"0x00\",\"mapping_frame\":\"0x00\",\"anim\":\"0x00\",\"anim_frame\":\"0x00\",\"anim_frame_timer\":\"0x00\",\"subtype\":\"0x00\"}}",
            "{\"frame\":-1,\"vfc\":0,\"event\":\"object_state_snapshot\",\"slot\":3,\"object_type\":\"0x34\",\"fields\":{\"off_00\":\"0x34\",\"off_01\":\"0x80\",\"off_02\":\"0x00\",\"off_03\":\"0x00\",\"off_04\":\"0x00\",\"off_05\":\"0x01\",\"off_06\":\"0x47\",\"off_07\":\"0xBA\",\"off_08\":\"0x01\",\"off_09\":\"0x48\",\"off_0A\":\"0x00\",\"off_0B\":\"0xD0\",\"off_0C\":\"0x00\",\"off_0D\":\"0x00\",\"off_0E\":\"0x00\",\"off_0F\":\"0x00\",\"off_10\":\"0x00\",\"off_11\":\"0x00\",\"off_12\":\"0x00\",\"off_13\":\"0x00\",\"off_14\":\"0x00\",\"off_15\":\"0x00\",\"off_16\":\"0x00\",\"off_17\":\"0x00\",\"off_18\":\"0x00\",\"off_19\":\"0x40\",\"off_1A\":\"0x11\",\"off_1B\":\"0x00\",\"off_1C\":\"0x00\",\"off_1D\":\"0x00\",\"off_1E\":\"0x00\",\"off_1F\":\"0x2D\",\"off_20\":\"0x00\",\"off_21\":\"0x00\",\"off_22\":\"0x00\",\"off_23\":\"0x00\",\"off_24\":\"0x16\",\"off_25\":\"0x00\",\"off_26\":\"0x00\",\"off_27\":\"0x00\",\"off_28\":\"0x00\",\"off_29\":\"0x00\",\"off_2A\":\"0x00\",\"off_2B\":\"0x00\",\"off_2C\":\"0x00\",\"off_2D\":\"0x00\",\"off_2E\":\"0x00\",\"off_2F\":\"0x00\",\"off_30\":\"0x01\",\"off_31\":\"0x48\",\"off_32\":\"0x00\",\"off_33\":\"0x28\",\"off_34\":\"0x00\",\"off_35\":\"0x00\",\"off_36\":\"0x00\",\"off_37\":\"0x00\",\"off_38\":\"0x00\",\"off_39\":\"0x00\",\"off_3A\":\"0x00\",\"off_3B\":\"0x00\",\"off_3C\":\"0x00\",\"off_3D\":\"0x00\",\"off_3E\":\"0x00\",\"off_3F\":\"0x00\",\"x_pos\":\"0x0148\",\"x_sub\":\"0x00D0\",\"y_pos\":\"0x0000\",\"y_sub\":\"0x0000\",\"x_vel\":\"0x0000\",\"y_vel\":\"0x0000\",\"id\":\"0x34\",\"render_flags\":\"0x80\",\"status\":\"0x00\",\"routine\":\"0x16\",\"routine_secondary\":\"0x00\",\"mapping_frame\":\"0x11\",\"anim\":\"0x00\",\"anim_frame\":\"0x00\",\"anim_frame_timer\":\"0x00\",\"subtype\":\"0x00\"}}",
            "{\"frame\":-1,\"vfc\":0,\"event\":\"object_state_snapshot\",\"slot\":4,\"object_type\":\"0x34\",\"fields\":{\"off_00\":\"0x34\",\"off_01\":\"0x80\",\"off_02\":\"0x00\",\"off_03\":\"0x00\",\"off_04\":\"0x00\",\"off_05\":\"0x01\",\"off_06\":\"0x47\",\"off_07\":\"0xBA\",\"off_08\":\"0x01\",\"off_09\":\"0x88\",\"off_0A\":\"0x00\",\"off_0B\":\"0xD0\",\"off_0C\":\"0x00\",\"off_0D\":\"0x00\",\"off_0E\":\"0x00\",\"off_0F\":\"0x00\",\"off_10\":\"0x00\",\"off_11\":\"0x00\",\"off_12\":\"0x00\",\"off_13\":\"0x00\",\"off_14\":\"0x00\",\"off_15\":\"0x00\",\"off_16\":\"0x00\",\"off_17\":\"0x00\",\"off_18\":\"0x00\",\"off_19\":\"0x18\",\"off_1A\":\"0x12\",\"off_1B\":\"0x00\",\"off_1C\":\"0x00\",\"off_1D\":\"0x00\",\"off_1E\":\"0x00\",\"off_1F\":\"0x2D\",\"off_20\":\"0x00\",\"off_21\":\"0x00\",\"off_22\":\"0x00\",\"off_23\":\"0x00\",\"off_24\":\"0x16\",\"off_25\":\"0x00\",\"off_26\":\"0x00\",\"off_27\":\"0x00\",\"off_28\":\"0x00\",\"off_29\":\"0x00\",\"off_2A\":\"0x00\",\"off_2B\":\"0x00\",\"off_2C\":\"0x00\",\"off_2D\":\"0x00\",\"off_2E\":\"0x00\",\"off_2F\":\"0x00\",\"off_30\":\"0x01\",\"off_31\":\"0x88\",\"off_32\":\"0x00\",\"off_33\":\"0x68\",\"off_34\":\"0x00\",\"off_35\":\"0x00\",\"off_36\":\"0x00\",\"off_37\":\"0x00\",\"off_38\":\"0x00\",\"off_39\":\"0x00\",\"off_3A\":\"0x00\",\"off_3B\":\"0x00\",\"off_3C\":\"0x00\",\"off_3D\":\"0x00\",\"off_3E\":\"0x00\",\"off_3F\":\"0x00\",\"x_pos\":\"0x0188\",\"x_sub\":\"0x00D0\",\"y_pos\":\"0x0000\",\"y_sub\":\"0x0000\",\"x_vel\":\"0x0000\",\"y_vel\":\"0x0000\",\"id\":\"0x34\",\"render_flags\":\"0x80\",\"status\":\"0x00\",\"routine\":\"0x16\",\"routine_secondary\":\"0x00\",\"mapping_frame\":\"0x12\",\"anim\":\"0x00\",\"anim_frame\":\"0x00\",\"anim_frame_timer\":\"0x00\",\"subtype\":\"0x00\"}}",
            "{\"frame\":-1,\"vfc\":0,\"event\":\"object_state_snapshot\",\"slot\":16,\"object_type\":\"0x9D\",\"fields\":{\"off_00\":\"0x9D\",\"off_01\":\"0x04\",\"off_02\":\"0x03\",\"off_03\":\"0xEE\",\"off_04\":\"0x00\",\"off_05\":\"0x03\",\"off_06\":\"0x7D\",\"off_07\":\"0x96\",\"off_08\":\"0x02\",\"off_09\":\"0x27\",\"off_0A\":\"0x00\",\"off_0B\":\"0x00\",\"off_0C\":\"0x02\",\"off_0D\":\"0x20\",\"off_0E\":\"0x00\",\"off_0F\":\"0x00\",\"off_10\":\"0x00\",\"off_11\":\"0x00\",\"off_12\":\"0xFF\",\"off_13\":\"0x00\",\"off_14\":\"0x00\",\"off_15\":\"0x00\",\"off_16\":\"0x00\",\"off_17\":\"0x00\",\"off_18\":\"0x05\",\"off_19\":\"0x0C\",\"off_1A\":\"0x01\",\"off_1B\":\"0x02\",\"off_1C\":\"0x00\",\"off_1D\":\"0x00\",\"off_1E\":\"0x04\",\"off_1F\":\"0x00\",\"off_20\":\"0x09\",\"off_21\":\"0x00\",\"off_22\":\"0x00\",\"off_23\":\"0x01\",\"off_24\":\"0x04\",\"off_25\":\"0x00\",\"off_26\":\"0x00\",\"off_27\":\"0x00\",\"off_28\":\"0x1E\",\"off_29\":\"0x00\",\"off_2A\":\"0x18\",\"off_2B\":\"0x00\",\"off_2C\":\"0x00\",\"off_2D\":\"0x02\",\"off_2E\":\"0x00\",\"off_2F\":\"0x00\",\"off_30\":\"0x00\",\"off_31\":\"0x00\",\"off_32\":\"0x00\",\"off_33\":\"0x00\",\"off_34\":\"0x00\",\"off_35\":\"0x00\",\"off_36\":\"0x00\",\"off_37\":\"0x00\",\"off_38\":\"0x00\",\"off_39\":\"0x00\",\"off_3A\":\"0x00\",\"off_3B\":\"0x00\",\"off_3C\":\"0x00\",\"off_3D\":\"0x00\",\"off_3E\":\"0x00\",\"off_3F\":\"0x00\",\"x_pos\":\"0x0227\",\"x_sub\":\"0x0000\",\"y_pos\":\"0x0220\",\"y_sub\":\"0x0000\",\"x_vel\":\"0x0000\",\"y_vel\":\"0xFF00\",\"id\":\"0x9D\",\"render_flags\":\"0x04\",\"status\":\"0x00\",\"routine\":\"0x04\",\"routine_secondary\":\"0x00\",\"mapping_frame\":\"0x01\",\"anim\":\"0x00\",\"anim_frame\":\"0x02\",\"anim_frame_timer\":\"0x04\",\"subtype\":\"0x1E\"}}",
            "{\"frame\":0,\"event\":\"zone_act_state\",\"actual_zone_id\":0,\"engine_zone_id\":0,\"actual_act\":0,\"apparent_act\":0,\"game_mode\":12}",
            "{\"frame\":0,\"event\":\"checkpoint\",\"name\":\"gameplay_start\",\"actual_zone_id\":0,\"engine_zone_id\":0,\"actual_act\":0,\"apparent_act\":0,\"game_mode\":12}"
        };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine reproduces ehz1_fullrun arm block",
                ReproducesEhz1FullrunArmBlock));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine dedups frame-0 zone_act_state after arm priming",
                DedupsFrameZeroZoneActStateAfterArmPriming));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine emits act transition checkpoints once per name",
                EmitsActTransitionCheckpointsOncePerName));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine renders object snapshot velocities unsigned",
                RendersObjectSnapshotVelocitiesUnsigned));
            tests.Add(new TestMain.TestCase(
                "S2AuxEventEngine gates cnz_slot_machine_state on armed CNZ zone",
                GatesCnzSlotMachineStateOnArmedCnzZone));
        }

        /// <summary>
        /// RAM state reproducing the ehz1_fullrun arm-time (frame -1)
        /// recorder inputs. History buffers and SST slot bytes are hydrated
        /// from the literal fixture lines' raw fields; scalar Tails-CPU and
        /// zone state are poked explicitly. Addresses are spelled as
        /// literals on purpose, guarding the S2Ram constants.
        /// </summary>
        internal static RamBackedHost BuildEhz1ArmHost()
        {
            var host = new RamBackedHost();
            host.Ram[0xF600] = 0x0C;   // Game_Mode: level (armed)
            // Current_Zone/Current_Act = 0 (EHZ act 1); vfc word = 0.
            // cpu_state_snapshot scalars (fixture line 2): cpu_routine 6,
            // interact_id 0x01, everything else zero.
            host.SetWord(0xF708, 6);
            host.Ram[0xF70E] = 0x01;
            HydrateHistoryFromLiteral(host, Ehz1ArmBlock[0]);
            for (int i = 2; i < 7; i++)
            {
                HydrateObjectSlotFromLiteral(host, Ehz1ArmBlock[i]);
            }
            return host;
        }

        private static void ReproducesEhz1FullrunArmBlock()
        {
            var engine = new S2AuxEventEngine();
            IList<string> lines =
                engine.EmitArmEvents(0x00, 0, BuildEhz1ArmHost());
            AssertEx.Equal(
                string.Join("\n", Ehz1ArmBlock), string.Join("\n", lines));
        }

        private static void DedupsFrameZeroZoneActStateAfterArmPriming()
        {
            var engine = new S2AuxEventEngine();
            RamBackedHost host = BuildEhz1ArmHost();
            engine.EmitArmEvents(0x00, 0, host);

            // The arm-time emission primed the dedup key with frame=0, so
            // the first recorded frame emits nothing in step 1...
            AssertEx.Equal(0, engine.ProcessFrameStart(0, host).Count);

            // ...and every later frame re-emits because the frame number is
            // part of the key. LITERAL frame-1 line of the gunzipped
            // ehz1_fullrun aux_state.jsonl.gz.
            IList<string> frame1 = engine.ProcessFrameStart(1, host);
            AssertEx.Equal(1, frame1.Count);
            AssertEx.Equal(
                "{\"frame\":1,\"event\":\"zone_act_state\",\"actual_zone_id\":0,\"engine_zone_id\":0,\"actual_act\":0,\"apparent_act\":0,\"game_mode\":12}",
                frame1[0]);
        }

        private static void EmitsActTransitionCheckpointsOncePerName()
        {
            // Synthetic from the Lua template: MTZ alternate zone id 0x05
            // (engine zone 7, apparent act = actual + 2) exercises the
            // apparent-act naming.
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            host.Ram[0xF600] = 0x0C;
            host.Ram[0xFE10] = 0x05;
            IList<string> lines = engine.EmitArmEvents(0x05, 0, host);
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":0,\"event\":\"zone_act_state\",\"actual_zone_id\":5,\"engine_zone_id\":7,\"actual_act\":0,\"apparent_act\":2,\"game_mode\":12}"));
            AssertEx.Equal(true, lines.Contains(
                "{\"frame\":0,\"event\":\"checkpoint\",\"name\":\"gameplay_start\",\"actual_zone_id\":5,\"engine_zone_id\":7,\"actual_act\":0,\"apparent_act\":2,\"game_mode\":12}"));

            // Act change: zone_act_state + the act-transition checkpoint
            // named from the START zone name and CURRENT apparent act.
            host.Ram[0xFE11] = 0x01;
            lines = engine.ProcessFrameStart(50, host);
            AssertEx.Equal(2, lines.Count);
            AssertEx.Equal(
                "{\"frame\":50,\"event\":\"zone_act_state\",\"actual_zone_id\":5,\"engine_zone_id\":7,\"actual_act\":1,\"apparent_act\":3,\"game_mode\":12}",
                lines[0]);
            AssertEx.Equal(
                "{\"frame\":50,\"event\":\"checkpoint\",\"name\":\"act_transition_to_mtz4\",\"actual_zone_id\":5,\"engine_zone_id\":7,\"actual_act\":1,\"apparent_act\":3,\"game_mode\":12}",
                lines[1]);

            // Same name never fires twice; a NEW act yields a new name.
            lines = engine.ProcessFrameStart(51, host);
            AssertEx.Equal(1, lines.Count);
            host.Ram[0xFE11] = 0x02;
            lines = engine.ProcessFrameStart(52, host);
            AssertEx.Equal(2, lines.Count);
            AssertEx.Equal(
                "{\"frame\":52,\"event\":\"checkpoint\",\"name\":\"act_transition_to_mtz5\",\"actual_zone_id\":5,\"engine_zone_id\":7,\"actual_act\":2,\"apparent_act\":4,\"game_mode\":12}",
                lines[1]);
        }

        private static void RendersObjectSnapshotVelocitiesUnsigned()
        {
            // The x_vel/y_vel aliases are s16be reads with +0x10000 on
            // negatives — the raw unsigned word (no fixture object moves
            // with negative velocity at arm time; synthetic from the Lua).
            var engine = new S2AuxEventEngine();
            var host = new RamBackedHost();
            host.Ram[0xF600] = 0x0C;
            int addr = 0xB000 + (5 * 0x40);
            host.Ram[addr] = 0x11;
            host.SetWord(addr + 0x10, 0xFF00);   // x_vel -256
            host.SetWord(addr + 0x12, 0xFE00);   // y_vel -512
            IList<string> lines = engine.EmitArmEvents(0x00, 0, host);

            string snapshot = null;
            foreach (string line in lines)
            {
                if (line.IndexOf("\"slot\":5,", StringComparison.Ordinal) >= 0)
                {
                    snapshot = line;
                }
            }
            AssertEx.Equal(true, snapshot != null);
            AssertEx.Equal(true, snapshot.IndexOf(
                "\"x_vel\":\"0xFF00\",\"y_vel\":\"0xFE00\",\"id\":\"0x11\"",
                StringComparison.Ordinal) >= 0);
        }

        private static void GatesCnzSlotMachineStateOnArmedCnzZone()
        {
            // Synthetic from the Lua write_cnz_slot_machine_state template
            // (no CNZ fixture movie exists). The gate keys on the ARMED
            // start_rom_zone_id, not the live zone byte.
            var host = new RamBackedHost();
            host.Ram[0xF600] = 0x0C;
            host.Ram[0xFE10] = 0x0C;   // Current_Zone: CNZ
            host.SetWord(0xFE04, 0x0100);   // vfc
            host.SetWord(0xFE0E, 0x0BEE);   // vbc word
            host.SetWord(0xFF4C, 0x0001);   // in_use
            host.Ram[0xFF4E] = 0x04;        // routine
            host.Ram[0xFF4F] = 0x1E;        // timer
            host.Ram[0xFF51] = 0x02;        // index
            host.SetWord(0xFF52, 0x0064);   // reward
            host.SetWord(0xFF54, 0x0123);   // slot1_pos
            host.Ram[0xFF56] = 0x08;        // slot1_speed
            host.Ram[0xFF57] = 0x02;        // slot1_routine
            host.SetWord(0xFF58, 0x0456);   // slot2_pos
            host.Ram[0xFF5A] = 0x10;        // slot2_speed
            host.Ram[0xFF5B] = 0x04;        // slot2_routine
            host.SetWord(0xFF5C, 0x0789);   // slot3_pos
            host.Ram[0xFF5E] = 0x18;        // slot3_speed
            host.Ram[0xFF5F] = 0x06;        // slot3_routine
            // Junk in the one unread gap byte must not leak into the output
            // (0xFF53 is NOT a gap — it is the reward word's low byte).
            host.Ram[0xFF50] = 0xAA;

            var engine = new S2AuxEventEngine();
            engine.EmitArmEvents(0x0C, 0, host);
            IList<string> lines = engine.ProcessFrame(7, host);
            string expected =
                "{\"frame\":7,\"vfc\":256,\"vbc\":\"0x0BEE\",\"event\":\"cnz_slot_machine_state\","
                + "\"in_use\":\"0x0001\",\"routine\":\"0x04\",\"timer\":\"0x1E\",\"index\":\"0x02\","
                + "\"reward\":\"0x0064\",\"slot1_pos\":\"0x0123\",\"slot1_speed\":\"0x08\",\"slot1_routine\":\"0x02\","
                + "\"slot2_pos\":\"0x0456\",\"slot2_speed\":\"0x10\",\"slot2_routine\":\"0x04\","
                + "\"slot3_pos\":\"0x0789\",\"slot3_speed\":\"0x18\",\"slot3_routine\":\"0x06\"}";
            int cnzIndex = lines.IndexOf(expected);
            AssertEx.Equal(true, cnzIndex > 0);
            // Emitted immediately after the per-frame cpu_state...
            AssertEx.Equal(true, lines[cnzIndex - 1].IndexOf(
                "\"event\":\"cpu_state\"", StringComparison.Ordinal) >= 0);
            // ...on EVERY recorded frame while armed in CNZ.
            AssertEx.Equal(true,
                engine.ProcessFrame(8, host).Contains(expected.Replace(
                    "{\"frame\":7,", "{\"frame\":8,")));

            // An engine armed outside CNZ never emits it, even with the
            // live zone byte pointing at CNZ.
            var ehzEngine = new S2AuxEventEngine();
            ehzEngine.EmitArmEvents(0x00, 0, host);
            foreach (string line in ehzEngine.ProcessFrame(7, host))
            {
                AssertEx.Equal(false, line.IndexOf(
                    "cnz_slot_machine_state", StringComparison.Ordinal) >= 0);
            }
        }

        /// <summary>
        /// Fills the Sonic history buffers (0xE400/0xE500, 4-byte stride)
        /// and record index (0xEED2) from a literal
        /// player_history_snapshot line's decimal lists.
        /// </summary>
        private static void HydrateHistoryFromLiteral(
            RamBackedHost host, string line)
        {
            host.SetWord(0xEED2, ExtractInt(line, "\"history_pos\":"));
            int[] xHistory = ExtractList(line, "\"x_history\":[");
            int[] yHistory = ExtractList(line, "\"y_history\":[");
            int[] inputHistory = ExtractList(line, "\"input_history\":[");
            int[] statusHistory = ExtractList(line, "\"status_history\":[");
            for (int i = 0; i < 64; i++)
            {
                int offset = i * 4;
                host.SetWord(0xE500 + offset, xHistory[i]);
                host.SetWord(0xE500 + offset + 2, yHistory[i]);
                host.SetWord(0xE400 + offset, inputHistory[i]);
                host.Ram[0xE400 + offset + 2] = (byte)statusHistory[i];
            }
        }

        /// <summary>
        /// Fills a 64-byte SST slot at 0xB000 + slot*0x40 from a literal
        /// object_state_snapshot line's raw off_XX byte fields (which ARE
        /// the slot's RAM bytes; the engine re-derives all aliases).
        /// </summary>
        private static void HydrateObjectSlotFromLiteral(
            RamBackedHost host, string line)
        {
            int slot = ExtractInt(line, "\"slot\":");
            int baseAddress = 0xB000 + (slot * 0x40);
            for (int off = 0; off < 0x40; off++)
            {
                string key = "\"off_" + off.ToString("X2") + "\":\"0x";
                int at = line.IndexOf(key, StringComparison.Ordinal);
                AssertEx.Equal(true, at >= 0);
                host.Ram[baseAddress + off] = Convert.ToByte(
                    line.Substring(at + key.Length, 2), 16);
            }
        }

        private static int ExtractInt(string line, string key)
        {
            int at = line.IndexOf(key, StringComparison.Ordinal);
            AssertEx.Equal(true, at >= 0);
            int start = at + key.Length;
            int end = start;
            while (end < line.Length && char.IsDigit(line[end]))
            {
                end++;
            }
            return int.Parse(line.Substring(start, end - start));
        }

        private static int[] ExtractList(string line, string key)
        {
            int at = line.IndexOf(key, StringComparison.Ordinal);
            AssertEx.Equal(true, at >= 0);
            int start = at + key.Length;
            int end = line.IndexOf(']', start);
            string[] parts = line.Substring(start, end - start).Split(',');
            var values = new int[parts.Length];
            for (int i = 0; i < parts.Length; i++)
            {
                values[i] = int.Parse(parts[i]);
            }
            return values;
        }
    }
}
