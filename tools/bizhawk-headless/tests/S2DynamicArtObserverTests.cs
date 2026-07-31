using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S2DynamicArtObserverTests
    {
        private const int QueueEntry = 0x144E;
        private const int QueueReturn = 0x14AA;
        private const int ProcessQueue = 0x14AC;
        private const int SonicEntry = 0x1B848;
        private const int SonicPart2Entry = 0x1B84E;
        private const int SonicReturn = 0x1B89A;
        private const int SonicPilotCaller = 0x3AF90;
        private const int TailsTailsEntry = 0x1D184;
        private const int TailsEntry = 0x1D1AC;
        private const int TailsPart2Entry = 0x1D1B2;
        private const int TailsReturn = 0x1D1FE;
        private const int TailsPilotCaller = 0x3AF98;
        private const int SsSharedEntry = 0x33ADA;
        private const int SsPlayerReturn = 0x33B3E;
        private const int SsTailsTailsEntry = 0x34AB0;
        private const int SsTailsTailsMapping = 0x34AC4;
        private const int SsTailsTailsReturn = 0x34B1A;

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver batches only accepted DMA runs inside a verified owner decision",
                BatchesAcceptedRunsInsideDecision));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver rejects full DMA queue attempts and permits forced repeated mappings",
                RejectsFullQueueAndAcceptsRepeatedMapping));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver isolates Tails-tail queue-full and suppressed mappings from Tails",
                IsolatesTailsTailsQueueFullAndSuppression));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver requires a pinned pilot caller before direct Part2 can open",
                RequiresPilotCallerForDirectPart2));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver preserves one normal decision through its Part2 fallthrough",
                TreatsNormalPart2AsContinuation));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver derives direct Sonic and Tails Part2 mappings from the pinned pilot latch",
                BatchesDirectPart2Mappings));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver does not publish Tails pilot proof until its direct Part2 submission returns",
                PublishesTailsPilotProofOnlyAfterReturnedSubmission));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver retires player batches in mixed FIFO order at ProcessDMAQueue",
                RetiresMixedFifoInOrder));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver forwards lifecycle order across lag and terminal rows",
                ForwardsAcrossLagAndTerminalRows));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver publishes a terminal submission completion through the following gap",
                PublishesTerminalCompletionThroughGap));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver publishes run-gap submission and completion transitions",
                PublishesRunGapTransitions));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver carries exact accepted run-gap FIFO work into a named segment",
                CarriesAcceptedRunGapWorkIntoNamedSegment));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver resets logical cursors only when a later segment arms",
                ResetsCursorsOnlyAtNextArm));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver identifies all special-stage player owners from decision scope",
                IdentifiesSpecialStageOwners));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver rejects an unpinned special-stage shared-decoder context",
                RejectsUnpinnedSpecialSharedContext));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver ignores a gated Tails-tail decision that exits before its mapping read",
                IgnoresGatedTailsTailsEarlyReturn));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver preserves a gated Tails-tail decision across ProcessDMAQueue",
                PreservesGatedTailsTailsDecisionAcrossVBlank));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver replaces only an identical interrupted gated decision",
                ReplacesOnlyIdenticalInterruptedGatedDecision));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver rejects ProcessDMAQueue after accepting current-decision work",
                RejectsVBlankAfterAcceptedCurrentDecisionWork));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver requires an empty ledger when a later segment arms",
                RequiresEmptyLedgerAtSegmentArm));
            tests.Add(new TestMain.TestCase(
                "S2DynamicArtObserver rejects an unverified retail callback window",
                RejectsUnverifiedCallbackWindow));

            string romPath = Environment.GetEnvironmentVariable("S2_ROM_PATH");
            if (!String.IsNullOrEmpty(romPath) && File.Exists(romPath))
            {
                tests.Add(new TestMain.TestCase(
                    "S2DynamicArtObserver sees level and special-stage player owners in the retail complete-emeralds movie",
                    CapturesRetailOwnerSmoke,
                    game: "s2",
                    movie: "s2-sonic-tails-complete-emeralds",
                    kind: TestKind.Gate,
                    serial: true,
                    estimatedSeconds: 120.0));
                tests.Add(new TestMain.TestCase(
                    "S2DynamicArtObserver finds a bounded retail Tails pilot direct-Part2 path",
                    CapturesRetailTailsPilotProbe,
                    game: "s2",
                    kind: TestKind.Gate,
                    serial: true,
                    estimatedSeconds: 120.0));
            }
        }

        private static void BatchesAcceptedRunsInsideDecision()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0003, 0x100A });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 7))
            {
                observer.ArmSegment();
                Queue(host, 0x50060, 0xF000, 0x10, true);
                AssertEx.Equal(0, observer.PublishRow(0, false).Edges.Count);

                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0);
                Queue(host, 0x50060, 0xF000, 0x10, true);
                Queue(host, 0x50140, 0xF020, 0x20, true);
                host.Fire(SonicReturn);

                DynamicArtTransferEnvelope envelope = observer.PublishRow(1, false);
                AssertEx.Equal(1, envelope.Edges.Count);
                DynamicArtTransferEdge edge = envelope.Edges[0];
                AssertEx.Equal(DynamicArtTransferPhase.Submitted, edge.Phase);
                AssertEx.Equal("sonic", edge.Owner);
                AssertEx.Equal(2, edge.Requests.Count);
                AssertRequest(edge.Requests[0], 0x50060, 3, -1, 0xF000, 0x20);
                AssertRequest(edge.Requests[1], 0x50140, 10, -1, 0xF020, 0x40);
                AssertEx.Equal(1, envelope.OutstandingTransferIds.Count);
            }
        }

        private static void RejectsFullQueueAndAcceptsRepeatedMapping()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 5, new[] { 0x0001 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 3))
            {
                observer.ArmSegment();
                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 5, 0xF766, 0);
                Queue(host, 0x50020, 0xF000, 0x10, false);
                host.Fire(SonicReturn);
                AssertEx.Equal(0, observer.PublishRow(0, false).Edges.Count);

                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 5, 0xF766, 0xFF);
                Queue(host, 0x50020, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                DynamicArtTransferEdge first = observer.PublishRow(1, false).Edges[0];
                host.Fire(ProcessQueue);
                observer.PublishRow(2, false);

                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 5, 0xF766, 0xFF);
                Queue(host, 0x50020, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                DynamicArtTransferEdge second = observer.PublishRow(3, false).Edges[0];
                AssertEx.Equal(true, second.TransferId > first.TransferId);
                AssertEx.Equal(5, second.MappingFrame);
            }
        }

        private static void IsolatesTailsTailsQueueFullAndSuppression()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineTailsDplc(rom, 4, new[] { 0x0000 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 4))
            {
                observer.ArmSegment();
                BeginDecision(host, TailsTailsEntry, 0xB080, 4, 0xF7DF, 0);
                Queue(host, 0x64320, 0xF600, 0x10, false);
                host.Fire(TailsReturn);
                AssertEx.Equal(0, observer.PublishRow(0, false).Edges.Count);

                BeginDecision(host, TailsTailsEntry, 0xB080, 4, 0xF7DF, 0xFF);
                Queue(host, 0x64320, 0xF600, 0x10, true);
                host.Fire(TailsReturn);
                DynamicArtTransferEdge tailsTails = observer.PublishRow(1, false).Edges[0];
                AssertEx.Equal("tails-tails", tailsTails.Owner);
                host.Fire(ProcessQueue);
                observer.PublishRow(2, false);

                BeginDecision(host, TailsTailsEntry, 0xB080, 4, 0xF7DF, 4);
                host.Fire(TailsReturn);
                AssertEx.Equal(0, observer.PublishRow(3, false).Edges.Count);

                BeginDecision(host, TailsEntry, S2Ram.SidekickBase, 4, 0xF7DE, 0);
                Queue(host, 0x64320, 0xF400, 0x10, true);
                host.Fire(TailsReturn);
                DynamicArtTransferEdge tails = observer.PublishRow(4, false).Edges[0];
                AssertEx.Equal("tails", tails.Owner);
                AssertEx.Equal(true, tails.TransferId > tailsTails.TransferId);
            }
        }

        private static void RetiresMixedFifoInOrder()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0002 });
            DefineTailsDplc(rom, 4, new[] { 0x0004 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 12))
            {
                observer.ArmSegment();
                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0);
                Queue(host, 0x50040, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                Queue(host, 0x90000, 0xA000, 0x10, true);
                BeginDecision(host, TailsEntry, S2Ram.SidekickBase, 4, 0xF7DE, 0);
                Queue(host, 0x643A0, 0xF400, 0x10, true);
                host.Fire(TailsReturn);
                observer.PublishRow(0, false);

                host.Fire(ProcessQueue);
                DynamicArtTransferEnvelope completed = observer.PublishRow(1, false);
                AssertEx.Equal(2, completed.Edges.Count);
                AssertEx.Equal(DynamicArtTransferPhase.Completed, completed.Edges[0].Phase);
                AssertEx.Equal(DynamicArtTransferPhase.Completed, completed.Edges[1].Phase);
                AssertEx.Equal("sonic", completed.Edges[0].Owner);
                AssertEx.Equal("tails", completed.Edges[1].Owner);
                AssertEx.Equal(0, completed.OutstandingTransferIds.Count);
            }
        }

        private static void RequiresPilotCallerForDirectPart2()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            using (var observer = new S2DynamicArtObserver(rom, host, () => 0))
            {
                observer.ArmSegment();
                AssertEx.Throws<InvalidOperationException>(
                    () => host.Fire(SonicPart2Entry), "pilot caller");
                AssertEx.Throws<InvalidOperationException>(
                    () => host.Fire(TailsPart2Entry), "pilot caller");
            }
        }

        private static void TreatsNormalPart2AsContinuation()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0001 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 8))
            {
                observer.ArmSegment();
                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0);
                host.Fire(SonicPart2Entry);
                Queue(host, 0x50020, 0xF000, 0x10, true);
                host.Fire(SonicReturn);

                DynamicArtTransferEnvelope envelope = observer.PublishRow(0, false);
                AssertEx.Equal(1, envelope.Edges.Count);
                AssertEx.Equal("sonic", envelope.Edges[0].Owner);
                AssertEx.Equal(4, envelope.Edges[0].MappingFrame);
            }
        }

        private static void BatchesDirectPart2Mappings()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0001 });
            DefineTailsDplc(rom, 5, new[] { 0x0002 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 9))
            {
                observer.ArmSegment();
                host.SetByte(0xF766, 0);
                host.SetRegister("M68K D0", 4);
                host.Fire(SonicPilotCaller);
                host.SetRegister("M68K D0", 0);
                host.Fire(SonicPart2Entry);
                Queue(host, 0x50020, 0xF000, 0x10, true);
                host.Fire(SonicReturn);

                host.SetByte(0xF7DE, 0);
                host.SetRegister("M68K D0", 5);
                host.Fire(TailsPilotCaller);
                host.SetRegister("M68K D0", 0);
                host.Fire(TailsPart2Entry);
                Queue(host, 0x64360, 0xF400, 0x10, true);
                host.Fire(TailsReturn);

                DynamicArtTransferEnvelope envelope = observer.PublishRow(0, false);
                AssertEx.Equal(2, envelope.Edges.Count);
                AssertEx.Equal("sonic", envelope.Edges[0].Owner);
                AssertEx.Equal(4, envelope.Edges[0].MappingFrame);
                AssertEx.Equal("tails", envelope.Edges[1].Owner);
                AssertEx.Equal(5, envelope.Edges[1].MappingFrame);
            }
        }

        private static void PublishesTailsPilotProofOnlyAfterReturnedSubmission()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineTailsDplc(rom, 5, new[] { 0x0002 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 10))
            {
                observer.ArmSegment();
                host.SetByte(0xF7DE, 0);
                host.SetRegister("M68K D0", 5);
                host.Fire(TailsPilotCaller);
                AssertEx.Equal(false, observer.TailsPilotDirectPart2Observed);
                AssertEx.Equal(0, observer.PublishRow(10, false).Edges.Count);
                AssertEx.Equal(false, observer.TailsPilotDirectPart2Observed);

                host.SetRegister("M68K D0", 0);
                host.Fire(TailsPart2Entry);
                Queue(host, 0x64360, 0xF400, 0x10, true);
                host.Fire(TailsReturn);
                AssertEx.Equal(false, observer.TailsPilotDirectPart2Observed);

                DynamicArtTransferEnvelope envelope = observer.PublishRow(10, false);
                AssertEx.Equal(1, envelope.Edges.Count);
                AssertEx.Equal("tails", envelope.Edges[0].Owner);
                AssertEx.Equal(DynamicArtTransferPhase.Submitted,
                    envelope.Edges[0].Phase);
                AssertEx.Equal(TailsReturn, envelope.Edges[0].RomCallbackPc);
                AssertEx.Equal(true, observer.TailsPilotDirectPart2Observed);
            }
        }

        private static void ForwardsAcrossLagAndTerminalRows()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0000 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 19))
            {
                observer.ArmSegment();
                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0);
                Queue(host, 0x50000, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                host.Fire(ProcessQueue);

                DynamicArtTransferEnvelope lag = observer.PublishRow(5, true);
                AssertEx.Equal(0, lag.Edges.Count);
                DynamicArtTransferEnvelope forwarded = observer.PublishRow(6, false);
                AssertEx.Equal(2, forwarded.Edges.Count);
                AssertEx.Equal(0L, forwarded.Edges[0].EdgeOrdinal);
                AssertEx.Equal(1L, forwarded.Edges[1].EdgeOrdinal);

                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0xFF);
                Queue(host, 0x50000, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                DynamicArtTransferEnvelope terminal = observer.PublishTerminal(7);
                AssertEx.Equal(1, terminal.Edges.Count);
                AssertEx.Equal(true, terminal.Edges[0].TerminalForwarded);
            }
        }

        private static void PublishesTerminalCompletionThroughGap()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0000 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 30))
            {
                observer.ArmSegment();
                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0);
                Queue(host, 0x50000, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                DynamicArtTransferEnvelope terminal = observer.PublishTerminal(30);
                observer.EndSegment();

                host.Fire(ProcessQueue);
                IList<DynamicArtGapTransition> gap = observer.PublishGap();
                AssertEx.Equal(1, terminal.Edges.Count);
                AssertEx.Equal(1, gap.Count);
                AssertEx.Equal(terminal.Edges[0].TransferId, gap[0].Edge.TransferId);
                AssertEx.Equal(DynamicArtTransferPhase.Completed, gap[0].Edge.Phase);
                AssertEx.Equal(DynamicArtSubmissionOrigin.Segment,
                    gap[0].Edge.SubmissionOrigin);
            }
        }

        private static void PublishesRunGapTransitions()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0000 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 31))
            {
                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0);
                Queue(host, 0x50000, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                IList<DynamicArtGapTransition> submitted = observer.PublishGap();

                host.Fire(ProcessQueue);
                IList<DynamicArtGapTransition> completed = observer.PublishGap();
                AssertEx.Equal(1, submitted.Count);
                AssertEx.Equal(DynamicArtSubmissionOrigin.RunGap,
                    submitted[0].Edge.SubmissionOrigin);
                AssertEx.Equal(1, completed.Count);
                AssertEx.Equal(submitted[0].Edge.TransferId,
                    completed[0].Edge.TransferId);
                AssertEx.Equal(DynamicArtTransferPhase.Completed,
                    completed[0].Edge.Phase);
            }
        }

        private static void CarriesAcceptedRunGapWorkIntoNamedSegment()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineTailsDplc(rom, 13, new[] { 0xB06E });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 126))
            {
                BeginDecision(host, TailsTailsEntry, 0xB080, 13, 0xF7DF, 0);
                Queue(host, 0x650E0, 0xF600, 0xC0, true);
                host.Fire(TailsReturn);
                DynamicArtGapTransition submitted = observer.PublishGap()[0];

                IList<DynamicArtTransferDescriptor> opening =
                    observer.ArmRunSegment();
                AssertEx.Equal(1, opening.Count);
                AssertEx.Equal(submitted.Edge.TransferId, opening[0].TransferId);
                AssertEx.Equal("tails-tails", opening[0].Owner);
                AssertEx.Equal(13, opening[0].MappingFrame);

                host.Fire(ProcessQueue);
                DynamicArtTransferEnvelope row =
                    observer.PublishRow(126, false);
                AssertEx.Equal(1, row.Edges.Count);
                AssertEx.Equal(DynamicArtTransferPhase.Completed,
                    row.Edges[0].Phase);
                AssertEx.Equal(DynamicArtSubmissionOrigin.RunGap,
                    row.Edges[0].SubmissionOrigin);
                AssertEx.Equal(opening[0].TransferId,
                    row.Edges[0].TransferId);
            }
        }

        private static void ResetsCursorsOnlyAtNextArm()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0000 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 32))
            {
                observer.ArmSegment();
                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0);
                Queue(host, 0x50000, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                DynamicArtTransferEdge first = observer.PublishTerminal(32).Edges[0];
                observer.EndSegment();
                host.Fire(ProcessQueue);
                DynamicArtGapEdge completion = observer.PublishGap()[0].Edge;
                AssertEx.Equal(0, completion.GapEdgeIndex);

                observer.ArmSegment();
                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0xFF);
                Queue(host, 0x50000, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                DynamicArtTransferEdge second = observer.PublishRow(32, false).Edges[0];
                AssertEx.Equal(0, second.LogicalEdgeIndex);
                AssertEx.Equal(true, second.TransferId > first.TransferId);
                AssertEx.Equal(true, second.EdgeOrdinal > completion.EdgeOrdinal);
            }
        }

        private static void IdentifiesSpecialStageOwners()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineSpecialDplc(rom, 4, new[] { 0x0000 });
            DefineSpecialDplc(rom, 0x16, new[] { 0x0000 });
            DefineSpecialDplc(rom, 0x28, new[] { 0x0000 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 22))
            {
                observer.ArmSegment();
                BeginSpecialSharedDecision(host, S2Ram.PlayerBase, 4,
                    0xF766, 0xFF, unchecked((int)0xFFFFF766), 0x5CA0, 0);
                Queue(host, 0xFFA000, 0x5CA0, 0x10, true);
                host.Fire(SsPlayerReturn);
                BeginSpecialSharedDecision(host, S2Ram.SidekickBase, 4,
                    0xF7DE, 0xFF, unchecked((int)0xFFFFF7DE), 0x6000, 0x12);
                Queue(host, 0xFFB000, 0x6000, 0x10, true);
                host.Fire(SsPlayerReturn);
                BeginTailsTailsDecision(host, 0xB080, 4, 0xF7DF, 0xFF);
                Queue(host, 0xFFC000, 0x62C0, 0x10, true);
                host.Fire(SsTailsTailsReturn);

                DynamicArtTransferEnvelope envelope = observer.PublishRow(0, false);
                AssertEx.Equal(3, envelope.Edges.Count);
                AssertEx.Equal("ss-sonic", envelope.Edges[0].Owner);
                AssertEx.Equal("ss-tails", envelope.Edges[1].Owner);
                AssertEx.Equal("ss-tails-tails", envelope.Edges[2].Owner);
                AssertRequest(envelope.Edges[0].Requests[0], -1, -1, 0xFFA000, 0x5CA0, 0x20);
            }
        }

        private static void RejectsUnpinnedSpecialSharedContext()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            using (var observer = new S2DynamicArtObserver(rom, host, () => 22))
            {
                observer.ArmSegment();
                host.SetRegister("M68K A0", S2Ram.PlayerBase);
                host.SetRegister("M68K A4", 0xFFFFF766u);
                host.SetRegister("M68K D4", 0x5CA0);
                host.SetRegister("M68K D1", 0x12);
                AssertEx.Throws<InvalidOperationException>(
                    () => host.Fire(SsSharedEntry), "register context");
            }
        }

        private static void IgnoresGatedTailsTailsEarlyReturn()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            using (var observer = new S2DynamicArtObserver(rom, host, () => 22))
            {
                observer.ArmSegment();
                host.Fire(SsTailsTailsEntry);
                host.Fire(SsTailsTailsReturn);
                AssertEx.Equal(0, observer.PublishRow(0, false).Edges.Count);
            }
        }

        private static void PreservesGatedTailsTailsDecisionAcrossVBlank()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0000 });
            DefineSpecialDplc(rom, 4, new[] { 0x0000 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 2473))
            {
                observer.ArmSegment();

                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0xFF);
                Queue(host, 0x50000, 0xF000, 0x10, true);
                host.Fire(SonicReturn);
                long priorTransferId =
                    observer.PublishRow(2472, false).Edges[0].TransferId;

                host.Fire(SsTailsTailsEntry);
                host.Fire(ProcessQueue);
                host.SetRegister("M68K A0", 0xB080);
                host.SetByte(0xB080 + S2Ram.OffMappingFrame, 4);
                host.SetByte(0xF7DF, 0xFF);
                host.Fire(SsTailsTailsMapping);
                Queue(host, 0xFFC000, 0x62C0, 0x10, true);
                host.Fire(SsTailsTailsReturn);

                DynamicArtTransferEnvelope envelope =
                    observer.PublishRow(2473, false);
                AssertEx.Equal(2, envelope.Edges.Count);
                AssertEx.Equal(priorTransferId, envelope.Edges[0].TransferId);
                AssertEx.Equal(DynamicArtTransferPhase.Completed,
                    envelope.Edges[0].Phase);
                AssertEx.Equal(DynamicArtTransferPhase.Submitted,
                    envelope.Edges[1].Phase);
                AssertEx.Equal("ss-tails-tails", envelope.Edges[1].Owner);
            }
        }

        private static void ReplacesOnlyIdenticalInterruptedGatedDecision()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            using (var observer = new S2DynamicArtObserver(rom, host, () => 2473))
            {
                observer.ArmSegment();
                host.Fire(SsTailsTailsEntry);
                host.Fire(ProcessQueue);

                AssertEx.Throws<InvalidOperationException>(
                    () => host.Fire(SonicEntry), "prior decision");

                host.Fire(SsTailsTailsEntry);
                host.Fire(SsTailsTailsReturn);
                AssertEx.Equal(0, observer.PublishRow(2473, false).Edges.Count);
            }
        }

        private static void RejectsVBlankAfterAcceptedCurrentDecisionWork()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineLevelDplc(rom, 4, new[] { 0x0000 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 2473))
            {
                observer.ArmSegment();
                BeginDecision(host, SonicEntry, S2Ram.PlayerBase, 4, 0xF766, 0xFF);
                Queue(host, 0x50000, 0xF000, 0x10, true);
                AssertEx.Throws<InvalidOperationException>(
                    () => host.Fire(ProcessQueue), "accepted current-decision work");
            }
        }

        private static void RequiresEmptyLedgerAtSegmentArm()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            DefineTailsDplc(rom, 4, new[] { 0x0000 });
            using (var observer = new S2DynamicArtObserver(rom, host, () => 0))
            {
                observer.ArmSegment();
                BeginDecision(host, TailsTailsEntry, 0xB080, 4, 0xF7DF, 0);
                Queue(host, 0x64320, 0xF600, 0x10, true);
                host.Fire(TailsReturn);
                observer.PublishRow(0, false);
                observer.EndSegment();
                AssertEx.Throws<InvalidOperationException>(
                    () => observer.ArmSegment(), "pending ledger");
            }
        }

        private static void RejectsUnverifiedCallbackWindow()
        {
            var host = new FakeHost();
            byte[] rom = CreateRom();
            rom[SonicEntry] ^= 0xFF;
            AssertEx.Throws<InvalidOperationException>(
                () => new S2DynamicArtObserver(rom, host, () => 0),
                "opcode window");
        }

        private static void CapturesRetailOwnerSmoke()
        {
            byte[] rom = File.ReadAllBytes(Environment.GetEnvironmentVariable("S2_ROM_PATH"));
            string moviePath = Path.GetFullPath(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..",
                "src", "test", "resources", "traces", "s2", "runs",
                "s2-sonic-tails-complete-emeralds",
                "sonic-2-sonic-tails-complete-emeralds.bk2"));
            Bk2Movie movie = Bk2Reader.Read(moviePath);
            var expected = new HashSet<string>
            {
                "sonic", "tails", "tails-tails", "ss-sonic", "ss-tails", "ss-tails-tails"
            };
            var seen = new HashSet<string>();
            int logicalFrame = 0;
            using (IGpgxHost host = GpgxHost.Open(
                Environment.GetEnvironmentVariable("S2_ROM_PATH"), movie.SyncSettings))
            using (var observer = new S2DynamicArtObserver(rom, host, () => logicalFrame))
            {
                observer.ArmSegment();
                foreach (Bk2Frame frame in movie.OpenFrameStream())
                {
                    ApplyFrame(host, frame);
                    host.Advance();
                    DynamicArtTransferEnvelope envelope = observer.PublishRow(
                        logicalFrame, host.IsLagged);
                    for (int index = 0; index < envelope.Edges.Count; index++)
                    {
                        DynamicArtTransferEdge edge = envelope.Edges[index];
                        if (edge.Phase == DynamicArtTransferPhase.Submitted)
                        {
                            seen.Add(edge.Owner);
                        }
                    }
                    if (seen.SetEquals(expected)) return;
                    logicalFrame++;
                }
            }
            if (!seen.SetEquals(expected))
            {
                throw new InvalidOperationException(
                    "retail S2 smoke did not observe every expected player dynamic-art owner; seen "
                    + String.Join(",", seen));
            }
        }

        private static void CapturesRetailTailsPilotProbe()
        {
            byte[] rom = File.ReadAllBytes(Environment.GetEnvironmentVariable("S2_ROM_PATH"));
            string tracesRoot = Path.GetFullPath(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..",
                "src", "test", "resources", "traces", "s2"));
            string requestedMovie = Environment.GetEnvironmentVariable(
                "S2_PILOT_PROBE_MOVIE");
            string[] moviePaths = String.IsNullOrEmpty(requestedMovie)
                ? Directory.GetFiles(tracesRoot, "*.bk2", SearchOption.AllDirectories)
                : new[] { requestedMovie };
            Array.Sort(moviePaths, StringComparer.Ordinal);
            if (String.IsNullOrEmpty(requestedMovie))
            {
                for (int index = 0; index < moviePaths.Length; index++)
                {
                    string directory = Path.GetFileName(
                        Path.GetDirectoryName(moviePaths[index]));
                    if (!String.Equals(directory, "scz",
                        StringComparison.OrdinalIgnoreCase)) continue;
                    string first = moviePaths[0];
                    moviePaths[0] = moviePaths[index];
                    moviePaths[index] = first;
                    break;
                }
            }
            const int MaxFramesPerMovie = 5000;
            const int EndOfStreamSafetyFrames = 120;
            int scannedMovies = 0;
            int scannedFrames = 0;
            var inventory = new List<string>();
            for (int movieIndex = 0; movieIndex < moviePaths.Length; movieIndex++)
            {
                string moviePath = moviePaths[movieIndex];
                Bk2Movie movie = Bk2Reader.Read(moviePath);
                int frameLimit = Math.Min(MaxFramesPerMovie,
                    Math.Max(0, movie.FrameCount - EndOfStreamSafetyFrames));
                scannedMovies++;
                inventory.Add(Path.GetFileName(moviePath) + "=" + frameLimit
                    + "/" + movie.FrameCount);
                int logicalFrame = 0;
                using (IGpgxHost host = GpgxHost.Open(
                    Environment.GetEnvironmentVariable("S2_ROM_PATH"), movie.SyncSettings))
                using (var observer = new S2DynamicArtObserver(rom, host,
                    () => logicalFrame))
                {
                    observer.ArmSegment();
                    foreach (Bk2Frame frame in movie.OpenFrameStream())
                    {
                        if (logicalFrame >= frameLimit) break;
                        ApplyFrame(host, frame);
                        host.Advance();
                        DynamicArtTransferEnvelope envelope = observer.PublishRow(
                            logicalFrame, host.IsLagged);
                        scannedFrames++;
                        if (observer.TailsPilotDirectPart2Observed)
                        {
                            bool publishedTailsReturn = false;
                            for (int index = 0; index < envelope.Edges.Count; index++)
                            {
                                DynamicArtTransferEdge edge = envelope.Edges[index];
                                if (edge.Owner == "tails"
                                    && edge.Phase == DynamicArtTransferPhase.Submitted
                                    && edge.RomCallbackPc == TailsReturn)
                                {
                                    publishedTailsReturn = true;
                                    break;
                                }
                            }
                            if (!publishedTailsReturn)
                            {
                                throw new InvalidOperationException(
                                    "retail Tails pilot proof was not published through its "
                                    + "direct Part2 return");
                            }
                            Console.WriteLine("S2 pilot probe hit "
                                + Path.GetFileName(moviePath) + " at frame "
                                + (logicalFrame + 1));
                            return;
                        }
                        logicalFrame++;
                    }
                }
            }
            throw new InvalidOperationException(
                "retail Tails pilot direct-Part2 submission was not observed after "
                + "$3AF98 in " + scannedMovies
                + " bounded S2 BK2 fixtures (" + scannedFrames + " frames): "
                + String.Join(",", inventory));
        }

        private static void BeginDecision(
            FakeHost host, uint entry, int objectAddress, int mappingFrame,
            int lastLoadedAddress, int previousFrame)
        {
            host.SetRegister("M68K A0", (uint)objectAddress);
            host.SetByte(objectAddress + S2Ram.OffMappingFrame, mappingFrame);
            host.SetByte(lastLoadedAddress, previousFrame);
            host.Fire(entry);
        }

        private static void BeginSpecialSharedDecision(
            FakeHost host, int objectAddress, int mappingFrame,
            int lastLoadedAddress, int previousFrame,
            int a4, int d4, int d1)
        {
            host.SetRegister("M68K A0", (uint)objectAddress);
            host.SetByte(objectAddress + S2Ram.OffMappingFrame, mappingFrame);
            host.SetByte(lastLoadedAddress, previousFrame);
            host.SetRegister("M68K A4", (uint)a4);
            host.SetRegister("M68K D4", (uint)d4);
            host.SetRegister("M68K D1", (uint)d1);
            host.Fire(SsSharedEntry);
        }

        private static void BeginTailsTailsDecision(
            FakeHost host, int objectAddress, int mappingFrame,
            int lastLoadedAddress, int previousFrame)
        {
            host.SetRegister("M68K A0", (uint)objectAddress);
            host.SetByte(objectAddress + S2Ram.OffMappingFrame, mappingFrame);
            host.SetByte(lastLoadedAddress, previousFrame);
            host.Fire(SsTailsTailsEntry);
            host.Fire(SsTailsTailsMapping);
        }

        private static void Queue(
            FakeHost host, int source, int destination, int wordLength, bool accepted)
        {
            host.SetRegister("M68K D1", (uint)source);
            host.SetRegister("M68K D2", (uint)destination);
            host.SetRegister("M68K D3", (uint)wordLength);
            host.Fire(QueueEntry);
            if (accepted)
            {
                int before = ReadU32(host.Ram, 0xDCFC);
                WriteU32(host.Ram, 0xDCFC, before + 14);
            }
            host.Fire(QueueReturn);
        }

        internal static byte[] CreateRom()
        {
            byte[] rom = new byte[0x80000];
            foreach (DynamicArtRomProfile.OpcodeWindow window
                in DynamicArtRomProfile.Sonic2Rev01.OpcodeWindows)
            {
                for (int index = 0; index < window.Bytes.Count; index++)
                {
                    rom[window.Address + index] = window.Bytes[index];
                }
            }
            return rom;
        }

        internal static void DefineLevelDplc(
            byte[] rom, int frame, int[] entries)
        {
            DefineDplc(rom, 0x714E0, 0x71600 + (frame * 0x20), frame, entries);
        }

        private static void DefineTailsDplc(byte[] rom, int frame, int[] entries)
        {
            DefineDplc(rom, 0x7446C, 0x74800 + (frame * 0x20), frame, entries);
        }

        private static void DefineSpecialDplc(byte[] rom, int frame, int[] entries)
        {
            DefineDplc(rom, 0x345FA, 0x34800 + (frame * 0x20), frame, entries);
        }

        private static void DefineDplc(
            byte[] rom, int table, int entry, int frame, int[] entries)
        {
            WriteU16(rom, table + (frame * 2), entry - table);
            WriteU16(rom, entry, entries.Length);
            for (int index = 0; index < entries.Length; index++)
            {
                WriteU16(rom, entry + 2 + (index * 2), entries[index]);
            }
        }

        private static void ApplyFrame(IGpgxHost host, Bk2Frame frame)
        {
            host.ClearButtons();
            SetIfPressed(host, "Power", frame.Power);
            SetIfPressed(host, "Reset", frame.Reset);
            SetIfPressed(host, "P1 Up", frame.P1Up);
            SetIfPressed(host, "P1 Down", frame.P1Down);
            SetIfPressed(host, "P1 Left", frame.P1Left);
            SetIfPressed(host, "P1 Right", frame.P1Right);
            SetIfPressed(host, "P1 A", frame.P1A);
            SetIfPressed(host, "P1 B", frame.P1B);
            SetIfPressed(host, "P1 C", frame.P1C);
            SetIfPressed(host, "P1 Start", frame.P1Start);
        }

        private static void SetIfPressed(IGpgxHost host, string name, bool pressed)
        {
            if (pressed) host.SetButton(name, true);
        }

        private static void AssertRequest(
            DynamicArtRequest request, int romSource, int tile,
            int ramSource, int vram, int length)
        {
            AssertEx.Equal(romSource, request.RomSourceAddress);
            AssertEx.Equal(tile, request.SourceTileIndex);
            AssertEx.Equal(ramSource, request.RamSourceAddress);
            AssertEx.Equal(vram, request.VramDestination);
            AssertEx.Equal(length, request.ByteLength);
        }

        private static void WriteU16(byte[] bytes, int address, int value)
        {
            bytes[address] = (byte)(value >> 8);
            bytes[address + 1] = (byte)value;
        }

        private static int ReadU32(byte[] bytes, int address)
        {
            return (bytes[address] << 24) | (bytes[address + 1] << 16)
                | (bytes[address + 2] << 8) | bytes[address + 3];
        }

        private static void WriteU32(byte[] bytes, int address, int value)
        {
            bytes[address] = (byte)(value >> 24);
            bytes[address + 1] = (byte)(value >> 16);
            bytes[address + 2] = (byte)(value >> 8);
            bytes[address + 3] = (byte)value;
        }

        private sealed class FakeHost : IGpgxHost, ICpuRegisterReader
        {
            private readonly Dictionary<uint, Action> callbacks =
                new Dictionary<uint, Action>();
            private readonly Dictionary<string, uint> registers =
                new Dictionary<string, uint>();

            public FakeHost()
            {
                Ram = new byte[0x10000];
                SetRegister("M68K A0", S2Ram.PlayerBase);
                WriteU32(Ram, 0xDCFC, 0xDC00);
            }

            public byte[] Ram { get; private set; }
            public int CompletedFrame { get { return 0; } }
            public bool IsLagged { get { return false; } }
            public int LagCount { get { return 0; } }
            public void ClearButtons() { }
            public void SetButton(string name, bool pressed) { }
            public void Advance() { }
            public byte ReadMainRamByte(int offset) { return Ram[offset]; }
            public uint ReadCpuRegister(string name) { return registers[name]; }
            public void SetRegister(string name, uint value) { registers[name] = value; }
            public void SetByte(int address, int value) { Ram[address] = (byte)value; }

            public IDisposable RegisterExecuteCallback(uint address, Action callback)
            {
                callbacks.Add(address, callback);
                return new Registration(callbacks, address);
            }

            public void Fire(uint address)
            {
                callbacks[address]();
            }

            public void Dispose() { }

            private sealed class Registration : IDisposable
            {
                private Dictionary<uint, Action> callbacks;
                private readonly uint address;

                public Registration(Dictionary<uint, Action> callbacks, uint address)
                {
                    this.callbacks = callbacks;
                    this.address = address;
                }

                public void Dispose()
                {
                    if (callbacks != null)
                    {
                        callbacks.Remove(address);
                        callbacks = null;
                    }
                }
            }
        }
    }
}
