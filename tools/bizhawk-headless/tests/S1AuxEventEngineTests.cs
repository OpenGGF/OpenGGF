using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S1AuxEventEngineTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1AuxEventEngine reproduces ghz1_fullrun frame 0 aux lines",
                ReproducesGhz1FullrunFrameZeroAuxLines));
            tests.Add(new TestMain.TestCase(
                "S1AuxEventEngine emits fixture object_removed line",
                EmitsFixtureObjectRemovedLine));
            tests.Add(new TestMain.TestCase(
                "S1AuxEventEngine emits fixture object_near at boundary 160",
                EmitsFixtureObjectNearAtBoundary));
            tests.Add(new TestMain.TestCase(
                "S1AuxEventEngine emits fixture air and rolling mode changes",
                EmitsFixtureAirAndRollingModeChanges));
            tests.Add(new TestMain.TestCase(
                "S1AuxEventEngine emits control_locked transitions",
                EmitsControlLockedTransitions));
            tests.Add(new TestMain.TestCase(
                "S1AuxEventEngine emits hurt routine change with stand context",
                EmitsHurtRoutineChangeWithStandContext));
            tests.Add(new TestMain.TestCase(
                "S1AuxEventEngine emits s1_obj64_state before proximity",
                EmitsObj64StateBeforeProximity));
            tests.Add(new TestMain.TestCase(
                "S1AuxEventEngine emits slot_dump only on appearance frames",
                EmitsSlotDumpOnlyOnAppearanceFrames));
            tests.Add(new TestMain.TestCase(
                "S1AuxEventEngine emits fixture cursor_state with dir L",
                EmitsFixtureCursorStateWithDirLeft));
        }

        private static void ReproducesGhz1FullrunFrameZeroAuxLines()
        {
            var host = new RamBackedHost();
            host.SetWord(S1Ram.FrameCount, 1);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffXPos, 0x0050);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffYPos, 0x03B0);
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRoutine] = 0x02;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffAnimId] = 5;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRadiusY] = 19;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRadiusX] = 9;

            StageObject(host, 1, 0x21, 0x0090, 0x0000);
            StageObject(host, 2, 0x34, 0x0120, 0x0000);
            StageObject(host, 3, 0x34, 0x013C, 0x0000);
            StageObject(host, 4, 0x34, 0x0154, 0x0000);
            StageObject(host, 5, 0x34, 0x0154, 0x0000);
            StageObject(host, 32, 0x25, 0x0144, 0x0360);
            StageObject(host, 33, 0x26, 0x0248, 0x0351);
            StageObject(host, 34, 0x25, 0x015C, 0x0360);
            StageObject(host, 35, 0x25, 0x0174, 0x0360);

            host.SetWord(S1Ram.OplScreen, 0x0000);
            host.SetLong(S1Ram.OplDataForward, 0x0006B0A2);
            host.SetLong(S1Ram.OplDataBackward, 0x0006B096);
            host.Ram[S1Ram.ObjStateForwardCounter] = 3;
            host.Ram[S1Ram.ObjStateBackwardCounter] = 1;

            IList<string> lines = new S1AuxEventEngine().ProcessFrame(0, host);

            // LITERAL first 13 lines of
            // src/test/resources/traces/s1/ghz1_fullrun/aux_state.jsonl.
            string[] expected =
            {
                "{\"frame\":0,\"vfc\":1,\"event\":\"routine_change\","
                + "\"from\":\"0x00\",\"to\":\"0x02\",\"sonic_x\":\"0x0050\","
                + "\"sonic_y\":\"0x03B0\",\"x_vel\":0,\"y_vel\":0,\"inertia\":0,"
                + "\"status\":\"0x00\",\"stand_on_obj\":0}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"state_snapshot\","
                + "\"control_locked\":false,\"anim_id\":5,"
                + "\"status_byte\":\"0x00\",\"routine\":\"0x02\","
                + "\"y_radius\":19,\"x_radius\":9,\"on_object\":false,"
                + "\"pushing\":false,\"underwater\":false,"
                + "\"roll_jumping\":false}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\","
                + "\"slot\":1,\"object_type\":\"0x21\",\"x\":\"0x0090\","
                + "\"y\":\"0x0000\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\","
                + "\"slot\":2,\"object_type\":\"0x34\",\"x\":\"0x0120\","
                + "\"y\":\"0x0000\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\","
                + "\"slot\":3,\"object_type\":\"0x34\",\"x\":\"0x013C\","
                + "\"y\":\"0x0000\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\","
                + "\"slot\":4,\"object_type\":\"0x34\",\"x\":\"0x0154\","
                + "\"y\":\"0x0000\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\","
                + "\"slot\":5,\"object_type\":\"0x34\",\"x\":\"0x0154\","
                + "\"y\":\"0x0000\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\","
                + "\"slot\":32,\"object_type\":\"0x25\",\"x\":\"0x0144\","
                + "\"y\":\"0x0360\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\","
                + "\"slot\":33,\"object_type\":\"0x26\",\"x\":\"0x0248\","
                + "\"y\":\"0x0351\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\","
                + "\"slot\":34,\"object_type\":\"0x25\",\"x\":\"0x015C\","
                + "\"y\":\"0x0360\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"object_appeared\","
                + "\"slot\":35,\"object_type\":\"0x25\",\"x\":\"0x0174\","
                + "\"y\":\"0x0360\"}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"slot_dump\","
                + "\"slots\":[[32,\"0x25\"],[33,\"0x26\"],[34,\"0x25\"],"
                + "[35,\"0x25\"]]}",
                "{\"frame\":0,\"vfc\":1,\"event\":\"cursor_state\","
                + "\"opl_screen\":\"0x0000\",\"fwd_ptr\":\"0x0006B0A2\","
                + "\"bwd_ptr\":\"0x0006B096\",\"fwd_ctr\":3,\"bwd_ctr\":1,"
                + "\"dir\":\"R\"}"
            };

            AssertEx.Equal(expected.Length, lines.Count);
            for (int i = 0; i < expected.Length; i++)
            {
                AssertEx.Equal(expected[i], lines[i]);
            }
        }

        private static void EmitsFixtureObjectRemovedLine()
        {
            var host = new RamBackedHost();
            var engine = new S1AuxEventEngine();

            // Prime: slot 5 occupied on the previous frame. Keep the player
            // far away so no proximity event muddies the frame under test.
            host.SetWord(S1Ram.FrameCount, 66);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffXPos, 0x0500);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffYPos, 0x0500);
            StageObject(host, 5, 0x34, 0x0154, 0x0000);
            engine.ProcessFrame(65, host);

            // Slot 5 frees this frame.
            host.Ram[S1Ram.SlotAddress(5)] = 0x00;
            host.SetWord(S1Ram.FrameCount, 67);
            IList<string> lines = engine.ProcessFrame(66, host);

            // LITERAL fixture line.
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":66,\"vfc\":67,\"event\":\"object_removed\","
                + "\"slot\":5,\"object_type\":\"0x34\"}",
                lines[0]);
        }

        private static void EmitsFixtureObjectNearAtBoundary()
        {
            var host = new RamBackedHost();
            host.SetWord(S1Ram.FrameCount, 80);
            // dx is exactly 160 (0x0144 - 0x00A4 = 0xA0) — inclusive boundary.
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffXPos, 0x00A4);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffYPos, 0x0360);
            StageObject(host, 32, 0x25, 0x0144, 0x0360);
            host.Ram[S1Ram.SlotAddress(32) + S1Ram.OffRoutine] = 0x02;

            IList<string> lines = new S1AuxEventEngine().ProcessFrame(79, host);

            // Ordering: appeared, near, slot_dump, cursor_state.
            AssertEx.Equal(4, lines.Count);
            // LITERAL fixture line.
            AssertEx.Equal(
                "{\"frame\":79,\"vfc\":80,\"event\":\"object_near\","
                + "\"slot\":32,\"type\":\"0x25\",\"x\":\"0x0144\","
                + "\"y\":\"0x0360\",\"routine\":\"0x02\",\"status\":\"0x00\"}",
                lines[1]);

            // One pixel further (dx = 161) is out of proximity: only
            // appeared, slot_dump, and cursor_state remain.
            var farHost = new RamBackedHost();
            farHost.SetWord(S1Ram.FrameCount, 80);
            farHost.SetWord(S1Ram.PlayerBase + S1Ram.OffXPos, 0x00A3);
            farHost.SetWord(S1Ram.PlayerBase + S1Ram.OffYPos, 0x0360);
            StageObject(farHost, 32, 0x25, 0x0144, 0x0360);
            IList<string> farLines =
                new S1AuxEventEngine().ProcessFrame(79, farHost);
            AssertEx.Equal(3, farLines.Count);
            AssertEx.Equal(
                true, farLines[1].Contains("\"event\":\"slot_dump\""));
        }

        private static void EmitsFixtureAirAndRollingModeChanges()
        {
            var host = new RamBackedHost();
            var engine = new S1AuxEventEngine();

            // Prime: grounded, not rolling, routine already 0x02.
            host.SetWord(S1Ram.FrameCount, 95);
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRoutine] = 0x02;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffAnimId] = 0;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRadiusY] = 14;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRadiusX] = 7;
            engine.ProcessFrame(94, host);

            // Air and rolling both set this frame (roll jump takeoff).
            host.Ram[S1Ram.PlayerBase + S1Ram.OffStatus] = 0x06;
            host.SetWord(S1Ram.FrameCount, 96);
            IList<string> lines = engine.ProcessFrame(95, host);

            AssertEx.Equal(3, lines.Count);
            // LITERAL fixture lines (frame 95 in ghz1_fullrun).
            AssertEx.Equal(
                "{\"frame\":95,\"vfc\":96,\"event\":\"mode_change\","
                + "\"field\":\"air\",\"from\":0,\"to\":1}",
                lines[0]);
            // The air transition emits an immediate snapshot BETWEEN the two
            // mode_change lines.
            AssertEx.Equal(
                "{\"frame\":95,\"vfc\":96,\"event\":\"state_snapshot\","
                + "\"control_locked\":false,\"anim_id\":0,"
                + "\"status_byte\":\"0x06\",\"routine\":\"0x02\","
                + "\"y_radius\":14,\"x_radius\":7,\"on_object\":false,"
                + "\"pushing\":false,\"underwater\":false,"
                + "\"roll_jumping\":false}",
                lines[1]);
            AssertEx.Equal(
                "{\"frame\":95,\"vfc\":96,\"event\":\"mode_change\","
                + "\"field\":\"rolling\",\"from\":0,\"to\":1}",
                lines[2]);
        }

        private static void EmitsControlLockedTransitions()
        {
            var host = new RamBackedHost();
            var engine = new S1AuxEventEngine();

            host.SetWord(S1Ram.FrameCount, 11);
            engine.ProcessFrame(10, host);

            // Lock engages.
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffCtrlLock, 0x001E);
            host.SetWord(S1Ram.FrameCount, 12);
            IList<string> lines = engine.ProcessFrame(11, host);
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":11,\"vfc\":12,\"event\":\"mode_change\","
                + "\"field\":\"control_locked\",\"from\":0,\"to\":1}",
                lines[0]);

            // Held lock does not re-fire.
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffCtrlLock, 0x0010);
            host.SetWord(S1Ram.FrameCount, 13);
            AssertEx.Equal(0, engine.ProcessFrame(12, host).Count);

            // Lock releases.
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffCtrlLock, 0x0000);
            host.SetWord(S1Ram.FrameCount, 14);
            lines = engine.ProcessFrame(13, host);
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":13,\"vfc\":14,\"event\":\"mode_change\","
                + "\"field\":\"control_locked\",\"from\":1,\"to\":0}",
                lines[0]);
        }

        private static void EmitsHurtRoutineChangeWithStandContext()
        {
            var host = new RamBackedHost();
            var engine = new S1AuxEventEngine();

            // Prime with routine 0x02 and airborne status so the transition
            // under test is 2 -> 4 with no simultaneous air mode_change.
            host.SetWord(S1Ram.FrameCount, 200);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffXPos, 0x0500);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffYPos, 0x0500);
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRoutine] = 0x02;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffStatus] = 0x02;
            engine.ProcessFrame(199, host);

            // Hurt transition while standing on slot 1, knocked back left.
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRoutine] = 0x04;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffStandOnObj] = 1;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffAnimId] = 26;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRadiusY] = 19;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffRadiusX] = 9;
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffXVel, 0xFE00);   // -512
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffYVel, 0xFC00);   // -1024
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffInertia, 0x0000);
            StageObject(host, 1, 0x21, 0x0510, 0x0520);
            host.Ram[S1Ram.SlotAddress(1) + S1Ram.OffRoutine] = 0x02;
            host.SetWord(S1Ram.FrameCount, 201);
            IList<string> lines = engine.ProcessFrame(200, host);

            // routine_change with stand-obj suffix, then immediate snapshot
            // (new routine 0x04), then object events.
            AssertEx.Equal(
                "{\"frame\":200,\"vfc\":201,\"event\":\"routine_change\","
                + "\"from\":\"0x02\",\"to\":\"0x04\",\"sonic_x\":\"0x0500\","
                + "\"sonic_y\":\"0x0500\",\"x_vel\":-512,\"y_vel\":-1024,"
                + "\"inertia\":0,\"status\":\"0x02\",\"stand_on_obj\":1,"
                + "\"stand_obj_slot\":1,\"stand_obj_type\":\"0x21\","
                + "\"stand_obj_x\":\"0x0510\",\"stand_obj_y\":\"0x0520\","
                + "\"stand_obj_routine\":\"0x02\"}",
                lines[0]);
            AssertEx.Equal(
                true, lines[1].Contains("\"event\":\"state_snapshot\""));

            // stand_on_obj >= 128 suppresses the suffix.
            var strayHost = new RamBackedHost();
            strayHost.SetWord(S1Ram.FrameCount, 1);
            strayHost.Ram[S1Ram.PlayerBase + S1Ram.OffRoutine] = 0x06;
            strayHost.Ram[S1Ram.PlayerBase + S1Ram.OffStandOnObj] = 200;
            IList<string> strayLines =
                new S1AuxEventEngine().ProcessFrame(3, strayHost);
            AssertEx.Equal(
                "{\"frame\":3,\"vfc\":1,\"event\":\"routine_change\","
                + "\"from\":\"0x00\",\"to\":\"0x06\",\"sonic_x\":\"0x0000\","
                + "\"sonic_y\":\"0x0000\",\"x_vel\":0,\"y_vel\":0,"
                + "\"inertia\":0,\"status\":\"0x00\",\"stand_on_obj\":200}",
                strayLines[0]);
        }

        private static void EmitsObj64StateBeforeProximity()
        {
            var host = new RamBackedHost();
            host.SetWord(S1Ram.FrameCount, 500);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffXPos, 0x0400);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffYPos, 0x0200);

            int addr = S1Ram.SlotAddress(40);
            StageObject(host, 40, 0x64, 0x0410, 0x0210);
            host.Ram[addr + S1Ram.OffRoutine] = 0x02;
            host.Ram[addr + S1Ram.OffStatus] = 0x80;
            host.Ram[addr + S1Ram.OffRenderFlags] = 0x04;
            host.Ram[addr + S1Ram.OffSubtype] = 0x81;
            host.Ram[addr + S1Ram.OffAnimId] = 0x01;
            host.Ram[addr + 0x32] = 0x12;
            host.Ram[addr + 0x33] = 0x07;
            host.SetWord(addr + 0x34, 0x0102);
            host.SetWord(addr + 0x36, 0x00FF);
            host.SetWord(addr + 0x38, 0xABCD);
            host.SetLong(addr + 0x3C, 0x0001E240);

            IList<string> lines = new S1AuxEventEngine().ProcessFrame(499, host);

            // appeared, s1_obj64_state, near, slot_dump, cursor_state.
            AssertEx.Equal(5, lines.Count);
            // Synthesized from the Lua write_s1_obj64_state template (GHZ has
            // no object 0x64, so no fixture line exists).
            AssertEx.Equal(
                "{\"frame\":499,\"vfc\":500,\"event\":\"s1_obj64_state\","
                + "\"slot\":40,\"x\":\"0x0410\",\"y\":\"0x0210\","
                + "\"routine\":\"0x02\",\"status\":\"0x80\","
                + "\"render_flags\":\"0x04\",\"subtype\":\"0x81\","
                + "\"anim\":\"0x01\",\"objoff_32\":\"0x12\","
                + "\"objoff_33\":\"0x07\",\"objoff_34\":\"0x0102\","
                + "\"objoff_36\":\"0x00FF\",\"objoff_38\":\"0xABCD\","
                + "\"objoff_3c\":\"0x0001E240\"}",
                lines[1]);
            AssertEx.Equal(
                true, lines[2].Contains("\"event\":\"object_near\""));
            AssertEx.Equal(
                "{\"frame\":499,\"vfc\":500,\"event\":\"slot_dump\","
                + "\"slots\":[[40,\"0x64\"]]}",
                lines[3]);
        }

        private static void EmitsSlotDumpOnlyOnAppearanceFrames()
        {
            var host = new RamBackedHost();
            var engine = new S1AuxEventEngine();

            host.SetWord(S1Ram.FrameCount, 2);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffXPos, 0x0500);
            host.SetWord(S1Ram.PlayerBase + S1Ram.OffYPos, 0x0500);
            StageObject(host, 33, 0x26, 0x0248, 0x0351);
            IList<string> first = engine.ProcessFrame(1, host);
            AssertEx.Equal(
                true, first[first.Count - 2].Contains("\"event\":\"slot_dump\""));

            // Unchanged slots the next frame: no appearance, no slot_dump —
            // in fact no events at all (cursor unchanged, out of proximity).
            host.SetWord(S1Ram.FrameCount, 3);
            AssertEx.Equal(0, engine.ProcessFrame(2, host).Count);

            // An id CHANGE (non-zero to different non-zero) fires only
            // object_appeared (never object_removed) plus a fresh slot_dump.
            host.Ram[S1Ram.SlotAddress(33)] = 0x27;
            host.SetWord(S1Ram.FrameCount, 4);
            IList<string> changed = engine.ProcessFrame(3, host);
            AssertEx.Equal(2, changed.Count);
            AssertEx.Equal(
                "{\"frame\":3,\"vfc\":4,\"event\":\"object_appeared\","
                + "\"slot\":33,\"object_type\":\"0x27\",\"x\":\"0x0248\","
                + "\"y\":\"0x0351\"}",
                changed[0]);
            AssertEx.Equal(
                "{\"frame\":3,\"vfc\":4,\"event\":\"slot_dump\","
                + "\"slots\":[[33,\"0x27\"]]}",
                changed[1]);
        }

        private static void EmitsFixtureCursorStateWithDirLeft()
        {
            var host = new RamBackedHost();
            var engine = new S1AuxEventEngine();

            // Prime: OPL cursor sitting right of the frame under test.
            host.SetWord(S1Ram.FrameCount, 255);
            host.SetWord(S1Ram.OplScreen, 0x0190);
            engine.ProcessFrame(254, host);

            // Camera chunk steps left.
            host.SetWord(S1Ram.OplScreen, 0x0180);
            host.SetLong(S1Ram.OplDataForward, 0x0006B0B4);
            host.SetLong(S1Ram.OplDataBackward, 0x0006B096);
            host.Ram[S1Ram.ObjStateForwardCounter] = 5;
            host.Ram[S1Ram.ObjStateBackwardCounter] = 1;
            host.SetWord(S1Ram.FrameCount, 256);
            IList<string> lines = engine.ProcessFrame(255, host);

            // LITERAL fixture line.
            AssertEx.Equal(1, lines.Count);
            AssertEx.Equal(
                "{\"frame\":255,\"vfc\":256,\"event\":\"cursor_state\","
                + "\"opl_screen\":\"0x0180\",\"fwd_ptr\":\"0x0006B0B4\","
                + "\"bwd_ptr\":\"0x0006B096\",\"fwd_ctr\":5,\"bwd_ctr\":1,"
                + "\"dir\":\"L\"}",
                lines[0]);
        }

        private static void StageObject(
            RamBackedHost host, int slot, int objectId, int x, int y)
        {
            int addr = S1Ram.SlotAddress(slot);
            host.Ram[addr] = (byte)objectId;
            host.SetWord(addr + S1Ram.OffXPos, x);
            host.SetWord(addr + S1Ram.OffYPos, y);
        }
    }
}
