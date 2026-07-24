using System;
using System.Collections.Generic;
using System.Globalization;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S2 Lua trace recorder's run-mode special-stage
    /// aux_state.jsonl event generation (tools/bizhawk/s2_trace_recorder.lua
    /// v9.13-s2, write_ss_pretrace_snapshot / ss_check_control_state /
    /// ss_check_checkpoint / ss_check_message_state /
    /// ss_check_results_started; spec
    /// tools/bizhawk-headless/docs/s2-run-mode-behavior.md §11.3): the
    /// hook-free subset of s2_ss_trace_recorder.lua v1.4-s2ss's aux surface
    /// (templates verbatim from that file — all events are "type"-keyed,
    /// lowercase zero-padded hex). NOT ported: run_objects_end (needs the
    /// standalone's two event.onmemoryexecute hooks; hard rule for the run
    /// port is no execute hooks), so at the finish frame the standalone's
    /// checkpoint -&gt; terminal run_objects_end -&gt; stage_finished order
    /// becomes checkpoint -&gt; stage_finished here.
    ///
    /// One instance per ss detour, constructed at ss arm time (the $10
    /// entry frame): the constructor seeds the prev_* trackers from RAM
    /// (standalone seeds at arm, v1.4-s2ss L676-679) so ss_2+ segments
    /// re-emit their own frame -1 snapshot and first-row control_state.
    /// Aux "frame" is the run-mode ss trace_frame — the same base as the
    /// segment's physics.csv rows, i.e. one emu frame later than the
    /// interior recorder's convention because the run port skips the $10
    /// entry frame (frame -1 = pre-row-0, sampled at the entry/arm frame).
    /// Lines are returned WITHOUT the trailing newline; the caller
    /// terminates every line with a single LF (expanded to CRLF at
    /// publication like every other run-mode file).
    /// </summary>
    public sealed class S2SpecialStageAuxEventEngine
    {
        // Special-stage aux RAM addresses (mainmemory form; the Lua's
        // SS_ADDR_* table).
        private const int AddrCurSpeedFactor = 0xDB16;      // u16be
        private const int AddrSpecialStageStarted = 0xDB23; // u8
        private const int AddrCheckRingsFlag = 0xDB86;      // u8
        private const int AddrRingRequirement = 0xDB8C;     // u16be
        private const int AddrCurrentLevelLayout = 0xDB8E;  // u32be
        private const int AddrPerfectRingsLeft = 0xDB9A;    // u16be
        private const int AddrNoRingsTogoLifetime = 0xDBA2; // u16be
        private const int AddrHideRingsToGo = 0xDBA6;       // u8
        private const int AddrTriggerRingsToGo = 0xDBA7;    // u8

        // ObjID_SSResults, sought across the full 128-slot SST scan.
        private const int SsResultsObjectId = 0x6F;

        // Per-detour trackers (the Lua's ss_aux_* globals). prevSpecialStage
        // Started is nullable so the first row always emits control_state.
        private int prevCheckRingsFlag;
        private int prevHideRingsToGo;
        private int prevTriggerRingsToGo;
        private int prevNoRingsTogoLifetime;
        private int? prevSpecialStageStarted;
        private bool stageFinishedEmitted;
        private bool resultsStartedEmitted;
        private int lastNonlagTraceFrame = -1;

        public S2SpecialStageAuxEventEngine(IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            prevCheckRingsFlag = S2Ram.U8(host, AddrCheckRingsFlag);
            prevHideRingsToGo = S2Ram.U8(host, AddrHideRingsToGo);
            prevTriggerRingsToGo = S2Ram.U8(host, AddrTriggerRingsToGo);
            prevNoRingsTogoLifetime =
                S2Ram.U16(host, AddrNoRingsTogoLifetime);
        }

        /// <summary>
        /// Frame -1 pre-trace snapshot: fixed special-stage parameters
        /// captured once per ss segment, at arm (standalone:
        /// write_pretrace_snapshot, L431-441). All-zero values at entry are
        /// correct — SS init has not populated these yet on the $10 entry
        /// frame.
        /// </summary>
        public string FormatPretraceSnapshot(IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            return "{\"frame\":-1,\"type\":\"state_snapshot\","
                + "\"ring_requirement\":\"0x"
                + Hex4(S2Ram.U16(host, AddrRingRequirement)) + "\","
                + "\"current_level_layout\":\"0x"
                + Hex8(S2Ram.U32(host, AddrCurrentLevelLayout)) + "\","
                + "\"initial_speed_factor\":\"0x"
                + Hex4(S2Ram.U16(host, AddrCurSpeedFactor)) + "\","
                + "\"perfect_rings_left\":\"0x"
                + Hex4(S2Ram.U16(host, AddrPerfectRingsLeft)) + "\"}";
        }

        /// <summary>
        /// Per-row event pass, called once per recorded $10 frame with the
        /// row's trace frame index and lag flag: maintains the hook-free
        /// stage_finished frame source (standalone record_frame L617-621:
        /// on a non-lag row, last_nonlag_trace_frame updates BEFORE the
        /// checks run), then emits in the standalone's record_frame order
        /// (v1.4-s2ss L650-653): control_state -&gt; checkpoint (+
        /// stage_finished) -&gt; message_state -&gt; results_started.
        /// </summary>
        public IList<string> EmitRowEvents(
            int traceFrame,
            bool lagged,
            IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            if (!lagged)
            {
                lastNonlagTraceFrame = traceFrame;
            }
            var lines = new List<string>();
            CheckControlState(lines, traceFrame, host);
            CheckCheckpoint(lines, traceFrame, host);
            CheckMessageState(lines, traceFrame, host);
            CheckResultsStarted(lines, traceFrame, host);
            return lines;
        }

        /// <summary>
        /// SpecialStage_Started edge (standalone: check_control_state,
        /// L496-505). Emits on change OR on the first row (null seed).
        /// </summary>
        private void CheckControlState(
            IList<string> lines,
            int traceFrame,
            IGpgxHost host)
        {
            int specialStageStarted =
                S2Ram.U8(host, AddrSpecialStageStarted);
            if (!prevSpecialStageStarted.HasValue
                || specialStageStarted != prevSpecialStageStarted.Value)
            {
                lines.Add("{\"frame\":" + Dec(traceFrame)
                    + ",\"type\":\"control_state\",\"started\":"
                    + (specialStageStarted != 0 ? "1" : "0") + "}");
                prevSpecialStageStarted = specialStageStarted;
            }
        }

        /// <summary>
        /// 0-&gt;nonzero edge of SS_Check_Rings_flag (standalone:
        /// check_checkpoint, L459-477), ported WITHOUT
        /// publish_pending_finish_pass and WITHOUT the error() assertions
        /// inside it (run_objects_end machinery). The standalone's own
        /// "last_nonlag_trace_frame &lt; 0" guard IS kept: it validates the
        /// stage_finished frame source, not the per-pass records.
        /// stage_finished's "frame" is the last non-lag trace_frame,
        /// "observed_frame" the current trace_frame.
        /// </summary>
        private void CheckCheckpoint(
            IList<string> lines,
            int traceFrame,
            IGpgxHost host)
        {
            int checkRingsFlag = S2Ram.U8(host, AddrCheckRingsFlag);
            if (prevCheckRingsFlag == 0 && checkRingsFlag != 0)
            {
                lines.Add("{\"frame\":" + Dec(traceFrame)
                    + ",\"type\":\"checkpoint\",\"check_rings_flag\":\"0x"
                    + Hex2(checkRingsFlag) + "\"}");
                if (!stageFinishedEmitted)
                {
                    if (lastNonlagTraceFrame < 0)
                    {
                        throw new InvalidOperationException(
                            "final checkpoint resolved before any logical"
                            + " observation");
                    }
                    lines.Add("{\"frame\":" + Dec(lastNonlagTraceFrame)
                        + ",\"observed_frame\":" + Dec(traceFrame)
                        + ",\"type\":\"stage_finished\","
                        + "\"check_rings_flag\":\"0x"
                        + Hex2(checkRingsFlag) + "\"}");
                    stageFinishedEmitted = true;
                }
            }
            prevCheckRingsFlag = checkRingsFlag;
        }

        /// <summary>
        /// Rings-to-go HUD message state changes (standalone:
        /// check_message_state, L479-494).
        /// </summary>
        private void CheckMessageState(
            IList<string> lines,
            int traceFrame,
            IGpgxHost host)
        {
            int hideRingsToGo = S2Ram.U8(host, AddrHideRingsToGo);
            int triggerRingsToGo = S2Ram.U8(host, AddrTriggerRingsToGo);
            int noRingsTogoLifetime =
                S2Ram.U16(host, AddrNoRingsTogoLifetime);
            if (hideRingsToGo != prevHideRingsToGo
                || triggerRingsToGo != prevTriggerRingsToGo
                || noRingsTogoLifetime != prevNoRingsTogoLifetime)
            {
                lines.Add("{\"frame\":" + Dec(traceFrame)
                    + ",\"type\":\"message_state\","
                    + "\"hide_rings_to_go\":\"0x"
                    + Hex2(hideRingsToGo) + "\","
                    + "\"trigger_rings_to_go\":\"0x"
                    + Hex2(triggerRingsToGo) + "\","
                    + "\"no_rings_togo_lifetime\":\"0x"
                    + Hex4(noRingsTogoLifetime) + "\"}");
                prevHideRingsToGo = hideRingsToGo;
                prevTriggerRingsToGo = triggerRingsToGo;
                prevNoRingsTogoLifetime = noRingsTogoLifetime;
            }
        }

        /// <summary>
        /// First sighting of ObjID_SSResults ($6F) across the 128-slot SST
        /// scan (standalone: check_results_started, L446-457); at most once
        /// per segment.
        /// </summary>
        private void CheckResultsStarted(
            IList<string> lines,
            int traceFrame,
            IGpgxHost host)
        {
            if (resultsStartedEmitted)
            {
                return;
            }
            for (var slot = 0; slot < S2Ram.TotalObjectSlots; slot++)
            {
                if (S2Ram.U8(host, S2Ram.SlotAddress(slot))
                    == SsResultsObjectId)
                {
                    lines.Add("{\"frame\":" + Dec(traceFrame)
                        + ",\"type\":\"results_started\",\"slot\":"
                        + Dec(slot) + "}");
                    resultsStartedEmitted = true;
                    return;
                }
            }
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        private static string Hex2(int value)
        {
            return value.ToString("x2", CultureInfo.InvariantCulture);
        }

        private static string Hex4(int value)
        {
            return value.ToString("x4", CultureInfo.InvariantCulture);
        }

        private static string Hex8(uint value)
        {
            return value.ToString("x8", CultureInfo.InvariantCulture);
        }
    }
}
