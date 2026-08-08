using System;
using System.Collections.Generic;
using System.Globalization;

namespace OpenGGF.BizHawk.Headless
{
    internal sealed class S2SpecialStageRunObjectsObserver : IDisposable
    {
        private const uint ReadJoypadsReturn = 0x1156;
        private const uint PostRunObjects = 0x52B2;
        // s2.asm SpecialStage_MainLoop is two loops, not one. The pre-start
        // loop (s2.asm:6683-6690, `jsr (RunObjects).l` at ROM $5234, next
        // instruction $523A) runs the same VintID_S2SS / WaitForVint /
        // SSTrack_Draw / SSObjectsManager / RunObjects sequence as the
        // recurring loop and exits only once Obj5F's ring-requirement message
        // sets SpecialStage_Started (s2.asm:9745). Hooking only the recurring
        // loop's $52B2 left every pass before control start unobserved, so a
        // consumer had to pace those frames off the lag column instead --
        // which the ROM does not: the V-int clock and the main-loop pass clock
        // are distinct there.
        private const uint PostRunObjectsBeforeStart = 0x523A;
        private const int GameMode = 0xF600;
        private const int SpecialStageStarted = 0xDB23;
        private const int P1Held = 0xF604;
        private const int P2Held = 0xF606;
        private readonly IGpgxHost host;
        private readonly ICpuRegisterReader registers;
        private readonly Func<int> traceCursor;
        private readonly int bk2Offset;
        private readonly IDisposable inputRegistration;
        private readonly IDisposable passRegistration;
        private readonly IDisposable preStartPassRegistration;
        private readonly List<Pass> pending = new List<Pass>();
        private Sample latest;
        private int nextInputSequence;
        private int nextPassSequence;
        private int? lastCompletedInputSequence;
        private int previousP1;
        private int previousP2;

        public S2SpecialStageRunObjectsObserver(
            IGpgxHost host,
            int bk2Offset,
            Func<int> traceCursor)
        {
            this.host = host;
            registers = host as ICpuRegisterReader;
            if (registers == null)
            {
                throw new InvalidOperationException(
                    "Standalone S2 special-stage capture requires CPU"
                    + " register access.");
            }
            this.bk2Offset = bk2Offset;
            this.traceCursor = traceCursor;
            inputRegistration = host.RegisterExecuteCallback(
                ReadJoypadsReturn, OnInputSample);
            passRegistration = host.RegisterExecuteCallback(
                PostRunObjects, OnPassComplete);
            preStartPassRegistration = host.RegisterExecuteCallback(
                PostRunObjectsBeforeStart, OnPassComplete);
        }

        public IList<string> PublishForRow(int frame, bool lagged)
        {
            if (lagged || pending.Count == 0)
            {
                return new List<string>();
            }
            return PublishPending(frame);
        }

        public IList<string> PublishTerminal(int frame)
        {
            if (pending.Count != 1)
            {
                throw new InvalidOperationException(
                    "stage finish expected exactly one pending RunObjects pass,"
                    + " got " + pending.Count);
            }
            Pass pass = pending[0];
            if (pass.CompletionCursorFrame != frame)
            {
                throw new InvalidOperationException(
                    "terminal pass completion cursor differs from finish"
                    + " observation");
            }
            if (pass.State[13] == 0)
            {
                throw new InvalidOperationException(
                    "terminal pending pass did not raise SS_Check_Rings_flag");
            }
            return PublishPending(frame);
        }

        private void OnInputSample()
        {
            if (S2Ram.U8(host, GameMode) != 0x10)
            {
                return;
            }
            int a0 = (int)(registers.ReadCpuRegister("M68K A0") & 0xFFFF);
            int a7 = (int)(registers.ReadCpuRegister("M68K A7") & 0xFFFF);
            uint returnPc = S2Ram.U32(host, a7) & 0xFFFFFF;
            if (a0 != 0xF608 || returnPc != 0x88E)
            {
                return;
            }
            int bk2Frame = host.CompletedFrame;
            int frame = bk2Frame - bk2Offset;
            Sample prior = latest;
            latest = new Sample
            {
                Sequence = nextInputSequence++,
                Frame = frame,
                Bk2Frame = bk2Frame,
                PreviousFrame = prior == null ? frame - 1 : prior.Frame,
                PreviousBk2Frame =
                    prior == null ? bk2Frame - 1 : prior.Bk2Frame,
                Started = S2Ram.U8(host, SpecialStageStarted),
                P1 = S2Ram.U8(host, P1Held),
                P2 = S2Ram.U8(host, P2Held),
                PreviousP1 = previousP1,
                PreviousP2 = previousP2
            };
            previousP1 = latest.P1;
            previousP2 = latest.P2;
        }

        private void OnPassComplete()
        {
            if (S2Ram.U8(host, GameMode) != 0x10)
            {
                return;
            }
            if (latest == null)
            {
                throw new InvalidOperationException(
                    "RunObjects return observed without a preceding input"
                    + " sample");
            }
            if (lastCompletedInputSequence.HasValue
                && latest.Sequence <= lastCompletedInputSequence.Value)
            {
                throw new InvalidOperationException(
                    "more than one active RunObjects pass consumed the same"
                    + " input sample");
            }
            pending.Add(new Pass
            {
                Sequence = nextPassSequence++,
                CompletionCursorFrame = traceCursor(),
                Sample = latest,
                State = ReadState()
            });
            lastCompletedInputSequence = latest.Sequence;
        }

        private IList<string> PublishPending(int frame)
        {
            var lines = new List<string>();
            foreach (Pass pass in pending)
            {
                lines.Add(Format(frame, pass));
            }
            pending.Clear();
            return lines;
        }

        private int[] ReadState()
        {
            string[] fields = S2SpecialStageCsvWriter.FormatRow(
                0, 0, 0, false, host).Split(',');
            var state = new int[fields.Length];
            for (int index = 4; index < fields.Length; index++)
            {
                state[index] = int.Parse(
                    fields[index],
                    NumberStyles.HexNumber,
                    CultureInfo.InvariantCulture);
            }
            return state;
        }

        private static string Format(int frame, Pass pass)
        {
            int[] s = pass.State;
            var parts = new List<string>
            {
                "{\"frame\":" + Dec(frame),
                "\"type\":\"run_objects_end\"",
                "\"pass_sequence\":" + Dec(pass.Sequence),
                "\"first_eligible_frame\":" + Dec(pass.Sample.Frame),
                "\"completion_cursor_frame\":"
                    + Dec(pass.CompletionCursorFrame),
                "\"input_sample_frame\":" + Dec(pass.Sample.Frame),
                "\"input_sample_bk2_frame\":" + Dec(pass.Sample.Bk2Frame),
                "\"previous_input_sample_frame\":"
                    + Dec(pass.Sample.PreviousFrame),
                "\"previous_input_sample_bk2_frame\":"
                    + Dec(pass.Sample.PreviousBk2Frame),
                "\"input_sample_sequence\":" + Dec(pass.Sample.Sequence),
                "\"input_source\":\"vint_s2ss_read_joypads\"",
                "\"started_at_input_sample\":" + Dec(pass.Sample.Started),
                "\"p1_held\":" + Dec(pass.Sample.P1),
                "\"p2_held\":" + Dec(pass.Sample.P2),
                "\"previous_p1_held\":" + Dec(pass.Sample.PreviousP1),
                "\"previous_p2_held\":" + Dec(pass.Sample.PreviousP2)
            };
            string[] names =
            {
                "speed_factor", "track_anim", "track_anim_frame",
                "track_drawing_index", "track_orientation",
                "track_duration_timer", "current_segment",
                "player_anim_frame_timer", "rings_togo_bcd",
                "check_rings_flag", "tails_control_counter",
                "swap_positions_flag", "sonic_present", "sonic_ss_x",
                "sonic_ss_x_sub", "sonic_ss_y", "sonic_ss_y_sub",
                "sonic_ss_z", "sonic_angle", "sonic_routine",
                "sonic_routine_secondary", "sonic_status", "sonic_anim",
                "sonic_anim_frame", "sonic_rings_bcd", "sonic_hurt_timer",
                "sonic_slide_timer", "sonic_flip_timer", "tails_present",
                "tails_ss_x", "tails_ss_x_sub", "tails_ss_y",
                "tails_ss_y_sub", "tails_ss_z", "tails_angle",
                "tails_routine", "tails_routine_secondary", "tails_status",
                "tails_anim", "tails_anim_frame", "tails_rings_bcd",
                "tails_hurt_timer", "tails_slide_timer", "tails_flip_timer"
            };
            for (int index = 0; index < names.Length; index++)
            {
                parts.Add("\"" + names[index] + "\":" + Dec(s[index + 4]));
            }
            return string.Join(",", parts.ToArray()) + "}";
        }

        public void Dispose()
        {
            preStartPassRegistration.Dispose();
            passRegistration.Dispose();
            inputRegistration.Dispose();
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        private sealed class Sample
        {
            public int Sequence;
            public int Frame;
            public int Bk2Frame;
            public int PreviousFrame;
            public int PreviousBk2Frame;
            public int Started;
            public int P1;
            public int P2;
            public int PreviousP1;
            public int PreviousP2;
        }

        private sealed class Pass
        {
            public int Sequence;
            public int CompletionCursorFrame;
            public Sample Sample;
            public int[] State;
        }
    }
}
