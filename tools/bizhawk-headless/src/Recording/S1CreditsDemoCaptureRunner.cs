using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    internal sealed class S1CreditsDemoCaptureResult
    {
        internal S1CreditsDemoCaptureResult(IList<int> capturedIndices)
        {
            CapturedIndices = capturedIndices;
        }

        public IList<int> CapturedIndices { get; private set; }
    }

    /// <summary>
    /// Movie-free native S1 credits recorder. It uses the ROM's own ending
    /// demo controller stream; the only writes redirect normal level entry
    /// into the ROM-owned credits flow and never select a demo directly.
    /// </summary>
    internal static class S1CreditsDemoCaptureRunner
    {
        private const byte TitleGameMode = 0x04;
        private const byte LevelGameMode = 0x0C;
        private const byte CreditsGameMode = 0x1C;
        private const byte DemoGameMode = 0x08;
        private const int DefaultStartTimeout = 2400;
        private const int TitleStartDelayFrames = 120;
        private const int DefaultMaxTraceFrames = 2000;

        internal static S1CreditsDemoCaptureResult Capture(
            IGpgxHost host,
            IMainRamWriter ramWriter,
            int? target,
            string recordingDate,
            S1CreditsDemoCollectionSink sink,
            byte[] requiredDynamicArtRom)
        {
            return Capture(
                host, ramWriter, target, recordingDate, sink,
                requiredDynamicArtRom, null);
        }

        internal static S1CreditsDemoCaptureResult Capture(
            IGpgxHost host,
            IMainRamWriter ramWriter,
            int? target,
            string recordingDate,
            S1CreditsDemoCollectionSink sink,
            byte[] requiredDynamicArtRom,
            S1CreditsRawHostEvidenceCollector rawEvidence)
        {
            if (host == null) throw new ArgumentNullException("host");
            if (ramWriter == null)
            {
                throw new ArgumentException(
                    "Credits capture requires the optional IMainRamWriter authority.",
                    "ramWriter");
            }
            if (target.HasValue) S1CreditsDemoCatalog.Get(target.Value);
            if (recordingDate == null) throw new ArgumentNullException("recordingDate");
            if (sink == null) throw new ArgumentNullException("sink");
            if (requiredDynamicArtRom == null)
            {
                throw new ArgumentNullException("requiredDynamicArtRom",
                    "Canonical credits publication requires native load audit.");
            }

            bool redirected = false;
            bool powered = false;
            int preRedirectFrames = 0;
            int titleFramesSeen = 0;
            int waitFrames = 0;
            Segment segment = null;
            int nextExpected = 0;
            int nextPriorExpected = 0;
            int lastObservedDemo = -1;
            var captured = new List<int>();
            int dynamicArtFrame = 0;
            using (var dynamicArt = new S1DynamicArtObserver(
                requiredDynamicArtRom, host, () => dynamicArtFrame))
            {
                while (true)
                {
                    // External input exists only long enough to leave title.
                    host.ClearButtons();
                    if (!powered)
                    {
                        host.SetButton("Power", true);
                        powered = true;
                    }
                    if (!redirected && BaseMode(host) == TitleGameMode)
                    {
                        titleFramesSeen++;
                        // The title code polls a rising edge. Repeated
                        // short presses preserve a usable edge after the
                        // title has become input-ready instead of holding
                        // Start from an earlier unready frame.
                        if (titleFramesSeen >= TitleStartDelayFrames
                            && ((titleFramesSeen - TitleStartDelayFrames)
                                % 10) < 5)
                        {
                            host.SetButton("P1 Start", true);
                        }
                    }
                    dynamicArtFrame = host.CompletedFrame;
                    if (segment != null && segment.TraceFrames > 0)
                    {
                        // The next advance may be the demo-exit frame. Mark
                        // that boundary before it happens so terminal
                        // forwarding can attach only observed callbacks to
                        // the last stored row.
                        dynamicArt.MarkAdvanceBoundary(segment.TraceFrames - 1);
                    }
                    host.Advance();

                    if (!redirected)
                    {
                        preRedirectFrames++;
                        ThrowIfPreRedirectTimedOut(host, preRedirectFrames);
                        if (BaseMode(host) == LevelGameMode)
                        {
                            RedirectToCredits(ramWriter);
                            redirected = true;
                            waitFrames = 0;
                        }
                        continue;
                    }

                    if (segment == null)
                    {
                        waitFrames++;
                        ThrowIfDemoWaitTimedOut(host, waitFrames);
                        S1CreditsDemoDefinition demo;
                        if (!TryGetActiveDemo(host, out demo)) continue;
                        if (!target.HasValue)
                        {
                            try
                            {
                                ValidateAllRouteOrder(
                                    demo, nextExpected, captured);
                            }
                            catch (InvalidOperationException exception)
                            {
                                throw LifecycleFailure(
                                    exception.Message, host, demo);
                            }
                        }
                        else
                        {
                            bool progressed;
                            try
                            {
                                progressed = ObserveSingleTargetProgression(
                                    demo.Index, target.Value,
                                    ref nextPriorExpected,
                                    ref lastObservedDemo);
                            }
                            catch (InvalidOperationException exception)
                            {
                                throw LifecycleFailure(
                                    exception.Message, host, demo);
                            }
                            if (demo.Index < target.Value)
                            {
                                if (progressed) waitFrames = 0;
                                continue;
                            }
                        }
                        if (!ShouldCapture(demo, target, captured))
                        {
                            continue;
                        }
                        dynamicArt.PublishGap();
                        dynamicArt.ArmSegment();
                        segment = Segment.Begin(demo, host.CompletedFrame, sink);
                        waitFrames = 0;
                        continue; // Detection frame is not a trace row.
                    }

                    if (ExactGameMode(host) != DemoGameMode)
                    {
                        segment.Finish(host, recordingDate, dynamicArt, true);
                        captured.Add(segment.Demo.Index);
                        if (!target.HasValue) nextExpected++;
                        segment = null;
                        if (sink.IsComplete(target)) break;
                        continue; // Exit frame is not a trace row.
                    }
                    ThrowIfSegmentTimedOut(
                        host, segment.Demo, segment.TraceFrames);
                    segment.Record(host, dynamicArt, rawEvidence);
                }
            }
            return new S1CreditsDemoCaptureResult(captured);
        }

        internal static void RedirectToCredits(IMainRamWriter writer)
        {
            // f_demo, v_creditsnum and v_gamemode only. Demo selection,
            // lamppost restore and water state remain owned by the ROM.
            writer.WriteMainRamByte(S1Ram.DemoFlag, 0);
            writer.WriteMainRamByte(S1Ram.DemoFlag + 1, 0);
            writer.WriteMainRamByte(S1Ram.CreditsNum, 0);
            writer.WriteMainRamByte(S1Ram.CreditsNum + 1, 0);
            writer.WriteMainRamByte(S1Ram.GameMode, CreditsGameMode);
        }

        internal static void ThrowIfPreRedirectTimedOut(
            IGpgxHost host, int framesWaited)
        {
            if (framesWaited > DefaultStartTimeout)
            {
                throw LifecycleFailure(
                    "timed out waiting to redirect title to credits", host, null);
            }
        }

        internal static void ThrowIfDemoWaitTimedOut(
            IGpgxHost host, int framesWaited)
        {
            if (framesWaited > DefaultStartTimeout)
            {
                throw LifecycleFailure(
                    "timed out waiting for credits demo", host, null);
            }
        }

        internal static void ThrowIfSegmentTimedOut(
            IGpgxHost host,
            S1CreditsDemoDefinition demo,
            int traceFrames)
        {
            if (traceFrames >= DefaultMaxTraceFrames)
            {
                throw LifecycleFailure(
                    "credits demo exceeded capture limit", host, demo);
            }
        }

        internal static void ValidateAllRouteOrder(
            S1CreditsDemoDefinition demo,
            int nextExpected,
            IList<int> captured)
        {
            if (demo == null) throw new ArgumentNullException("demo");
            if (captured == null) throw new ArgumentNullException("captured");
            if (captured.Contains(demo.Index))
            {
                throw new InvalidOperationException(
                    "credits flow duplicated demo " + demo.Index);
            }
            if (demo.Index != nextExpected)
            {
                throw new InvalidOperationException(
                    "credits flow skipped or reordered demo " + demo.Index);
            }
        }

        internal static bool ObserveSingleTargetProgression(
            int demoIndex,
            int target,
            ref int nextPriorExpected,
            ref int lastObservedDemo)
        {
            if (demoIndex > target)
            {
                throw new InvalidOperationException(
                    "credits flow passed requested demo " + target
                    + " with demo " + demoIndex);
            }
            if (demoIndex == target)
            {
                if (nextPriorExpected != target)
                {
                    throw new InvalidOperationException(
                        "credits flow skipped prior demo "
                        + nextPriorExpected + " before requested demo "
                        + target);
                }
                return false;
            }
            if (demoIndex == lastObservedDemo)
            {
                return false;
            }
            if (demoIndex < nextPriorExpected)
            {
                throw new InvalidOperationException(
                    "credits flow duplicated or reordered prior demo "
                    + demoIndex);
            }
            if (demoIndex > nextPriorExpected)
            {
                throw new InvalidOperationException(
                    "credits flow skipped prior demo "
                    + nextPriorExpected + " and observed demo "
                    + demoIndex);
            }
            lastObservedDemo = demoIndex;
            nextPriorExpected++;
            return true;
        }

        private static bool ShouldCapture(
            S1CreditsDemoDefinition demo, int? target, IList<int> captured)
        {
            if (demo == null || captured.Contains(demo.Index)) return false;
            return !target.HasValue || target.Value == demo.Index;
        }

        private static bool TryGetActiveDemo(
            IGpgxHost host, out S1CreditsDemoDefinition demo)
        {
            demo = null;
            if (ExactGameMode(host) != DemoGameMode
                || S1Ram.U16(host, S1Ram.DemoFlag) != 0x8001)
            {
                return false;
            }
            int index = S1Ram.U16(host, S1Ram.CreditsNum) - 1;
            if (index < 0 || index > 7) return false;
            S1CreditsDemoDefinition candidate = S1CreditsDemoCatalog.Get(index);
            if (S1Ram.U16(host, S1Ram.Zone) != candidate.ZoneActWord
                || S1Ram.U8(host, S1Ram.PlayerBase + S1Ram.OffRoutine) < 2)
            {
                return false;
            }
            demo = candidate;
            return true;
        }

        private static byte ExactGameMode(IGpgxHost host)
        {
            return S1Ram.U8(host, S1Ram.GameMode);
        }

        private static byte BaseMode(IGpgxHost host)
        {
            return (byte)(ExactGameMode(host) & 0x7F);
        }

        private static InvalidOperationException LifecycleFailure(
            string reason, IGpgxHost host, S1CreditsDemoDefinition demo)
        {
            return new InvalidOperationException(reason + ": mode=0x"
                + ExactGameMode(host).ToString("X2") + ", credits="
                + S1Ram.U16(host, S1Ram.CreditsNum) + ", demo="
                + (demo == null ? "none" : demo.Index.ToString())
                + ", completed_frame=" + host.CompletedFrame + ".");
        }

        private sealed class Segment
        {
            private readonly TextWriter physics;
            private readonly TextWriter aux;
            private readonly S1AuxEventEngine auxEngine;
            private readonly DynamicArtCaptureRowBuffer rowBuffer;
            private bool firstRow;
            private int startX;
            private int startY;
            private int zoneId;
            private int actRaw;
            private uint rngSeed;

            private Segment(
                S1CreditsDemoDefinition demo, int startFrame,
                TextWriter physics, TextWriter aux,
                S1CreditsDemoCollectionSink sink)
            {
                Demo = demo;
                StartFrame = startFrame;
                this.physics = physics;
                this.aux = aux;
                this.sink = sink;
                auxEngine = new S1AuxEventEngine();
                rowBuffer = new DynamicArtCaptureRowBuffer(physics, aux, "\n");
                physics.Write(S1TraceCsvWriter.Header);
                physics.Write('\n');
            }

            public S1CreditsDemoDefinition Demo { get; private set; }
            public int StartFrame { get; private set; }
            public int TraceFrames { get; private set; }

            public static Segment Begin(
                S1CreditsDemoDefinition demo, int startFrame,
                S1CreditsDemoCollectionSink sink)
            {
                TextWriter aux;
                TextWriter physics = sink.Begin(demo, out aux);
                return new Segment(demo, startFrame, physics, aux, sink);
            }

            public void Record(
                IGpgxHost host,
                S1DynamicArtObserver dynamicArt,
                S1CreditsRawHostEvidenceCollector rawEvidence)
            {
                if (!firstRow)
                {
                    firstRow = true;
                    startX = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffXPos);
                    startY = S1Ram.U16(host, S1Ram.PlayerBase + S1Ram.OffYPos);
                    zoneId = S1Ram.U8(host, S1Ram.Zone);
                    actRaw = S1Ram.U8(host, S1Ram.Act);
                    rngSeed = S1Ram.U32(host, S1Ram.Random);
                }
                if (rawEvidence != null)
                {
                    rawEvidence.Observe(Demo.Index, TraceFrames, host);
                }
                var lines = new List<string>();
                foreach (string line in auxEngine.ProcessFrame(TraceFrames, host)) lines.Add(line);
                rowBuffer.Queue(
                    S1TraceCsvWriter.FormatRow(TraceFrames,
                        S1InputMask.FromRomControllerByte(S1Ram.U8(host, S1Ram.Ctrl1)), host),
                    lines,
                    dynamicArt.PublishRow(TraceFrames, host.IsLagged));
                TraceFrames++;
            }

            public void Finish(
                IGpgxHost host, string recordingDate,
                S1DynamicArtObserver dynamicArt,
                bool exitedOnMarkedBoundary)
            {
                if (TraceFrames == 0)
                {
                    throw LifecycleFailure("credits demo exited before first recordable frame", host, Demo);
                }
                rowBuffer.FlushTerminal(exitedOnMarkedBoundary
                    ? dynamicArt.PublishBoundaryTerminal(TraceFrames - 1)
                    : dynamicArt.PublishTerminal(TraceFrames - 1));
                dynamicArt.EndSegment();
                // Sink owns the actual streams and its metadata stage is the
                // final member of this segment's transaction.
                sink.Complete(S1CreditsDemoMetadataWriter.Format(
                    Demo, StartFrame, TraceFrames, startX, startY, zoneId,
                    actRaw, rngSeed, recordingDate));
            }

            private S1CreditsDemoCollectionSink sink;
        }
    }
}
