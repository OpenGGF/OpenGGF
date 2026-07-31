using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S1DynamicArtObserverTests
    {
        private const int DecisionEntry = 0x14312;
        private const int DecisionReturn = 0x1436A;

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver emits an ordered multi-run submission and physical completion",
                EmitsMultiRunSubmissionAndCompletion));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver suppresses duplicate mappings and reuses empty DPLC entries",
                SuppressesDuplicateAndEmptyDplc));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver recognizes every pinned VBlank completion site",
                RecognizesEveryVblankCompletionSite));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver preserves same-frame callback order and forwards across lag and terminal rows",
                PreservesOrderAcrossLagAndTerminalRows));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver requires an empty ledger when a segment arms",
                RequiresEmptyLedgerAtSegmentArm));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver carries unpublished preparation into a named-run segment",
                CarriesPreparationIntoNamedRunSegment));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver replaces repeated preparations before VBlank promotion",
                ReplacesPreparationBeforeVblank));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver rejects a changed nonempty DPLC without the staging flag",
                RejectsSubmissionWithoutPendingFlag));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver requires a nonzero pre-transfer probe before completion",
                RequiresNonzeroPreTransferProbe));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver writes callback lifecycles outside stored rows as run-gap transitions",
                WritesRunGapTransitions));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver drains a terminal segment ledger through a run gap",
                DrainsTerminalLedgerThroughGap));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver resets segment-local edge cursors when a later segment arms",
                ResetsSegmentLocalCursor));
            tests.Add(new TestMain.TestCase(
                "S1DynamicArtObserver rejects an unverified retail callback window",
                RejectsUnverifiedCallbackWindow));
            string romPath = Environment.GetEnvironmentVariable("S1_ROM_PATH");
            if (!String.IsNullOrEmpty(romPath) && File.Exists(romPath))
            {
                tests.Add(new TestMain.TestCase(
                    "S1DynamicArtObserver captures a paired lifecycle from the retail ROM",
                    CapturesRetailRomLifecycle,
                    game: "s1",
                    kind: TestKind.Gate,
                    serial: true,
                    estimatedSeconds: 3.0));
                tests.Add(new TestMain.TestCase(
                    "S1DynamicArtObserver observes Sonic_LoadGfx activity during a retail special stage",
                    CapturesRetailSpecialStageLifecycle,
                    game: "s1",
                    movie: "s1-ghz-maze-roundtrip",
                    kind: TestKind.Gate,
                    serial: true,
                    estimatedSeconds: 20.0));
            }
        }

        private static void EmitsMultiRunSubmissionAndCompletion()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 4, new[] { 0x1003, 0x200A });
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => 7))
            {
                observer.ArmSegment();
                host.SetByte(S1Ram.PlayerBase + S1Ram.OffMappingFrame, 4);
                host.SetByte(0xF766, 0);
                host.Fire(DecisionEntry);
                host.SetByte(0xF767, 1);
                host.Fire(DecisionReturn);

                AssertEx.Equal(0, observer.PublishRow(3, false).Edges.Count);

                CompleteVblank(host, 0x0D50);
                DynamicArtTransferEnvelope submitted = observer.PublishRow(4, false);
                AssertEx.Equal(2, submitted.Edges.Count);
                DynamicArtTransferEdge edge = submitted.Edges[0];
                AssertEx.Equal(DynamicArtTransferPhase.Submitted, edge.Phase);
                AssertEx.Equal(7, edge.LogicalFrame);
                AssertEx.Equal(0, edge.LogicalEdgeIndex);
                AssertEx.Equal(4, edge.PublicationFrame);
                AssertEx.Equal(2, edge.Requests.Count);
                AssertRequest(edge.Requests[0], 0x22670, 3, -1, 0xF000, 0x40);
                AssertRequest(edge.Requests[1], 0x22750, 10, -1, 0xF040, 0x60);
                AssertEx.Equal(0, submitted.OutstandingTransferIds.Count);

                edge = submitted.Edges[1];
                AssertEx.Equal(DynamicArtTransferPhase.Completed, edge.Phase);
                AssertEx.Equal(1, edge.LogicalEdgeIndex);
                AssertEx.Equal(1, edge.Requests.Count);
                AssertRequest(edge.Requests[0], -1, -1, 0xC800, 0xF000, 0x2E0);
                AssertEx.Equal(0, submitted.OutstandingTransferIds.Count);
            }
        }

        private static void SuppressesDuplicateAndEmptyDplc()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 5, new int[0]);
            DefineDplc(rom, 6, new[] { 0x0000 });
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => 0))
            {
                observer.ArmSegment();
                host.SetByte(S1Ram.PlayerBase + S1Ram.OffMappingFrame, 5);
                host.SetByte(0xF766, 5);
                host.Fire(DecisionEntry);
                host.Fire(DecisionReturn);
                AssertEx.Equal(0, observer.PublishRow(0, false).Edges.Count);

                host.SetByte(S1Ram.PlayerBase + S1Ram.OffMappingFrame, 5);
                host.SetByte(0xF766, 4);
                host.Fire(DecisionEntry);
                host.Fire(DecisionReturn);
                AssertEx.Equal(0, observer.PublishRow(1, false).Edges.Count);

                host.SetByte(S1Ram.PlayerBase + S1Ram.OffMappingFrame, 6);
                host.SetByte(0xF766, 5);
                host.Fire(DecisionEntry);
                host.SetByte(0xF767, 1);
                host.Fire(DecisionReturn);
                CompleteVblank(host, 0x0D50);
                AssertEx.Equal(2, observer.PublishRow(2, false).Edges.Count);
            }
        }

        private static void RecognizesEveryVblankCompletionSite()
        {
            foreach (int completionSite in new[] { 0x0D50, 0x0E64, 0x0F54, 0x1060 })
            {
                var host = new DynamicArtFakeHost();
                byte[] rom = CreateRom();
                DefineDplc(rom, 4, new[] { 0x0000 });
                using (var observer = new S1DynamicArtObserver(
                    rom, host, () => 1))
                {
                    observer.ArmSegment();
                    Submit(host, 4, 0);
                    CompleteVblank(host, completionSite);
                    DynamicArtTransferEnvelope completed = observer.PublishRow(1, false);
                    AssertEx.Equal(2, completed.Edges.Count);
                    AssertEx.Equal(DynamicArtTransferPhase.Completed,
                        completed.Edges[1].Phase);
                    AssertEx.Equal(completionSite, completed.Edges[1].RomCallbackPc);
                }
            }
        }

        private static void PreservesOrderAcrossLagAndTerminalRows()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 5, new[] { 0x0000 });
            int logicalFrame = 11;
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => logicalFrame))
            {
                observer.ArmSegment();
                Submit(host, 5, 0);
                CompleteVblank(host, 0x0D50);
                DynamicArtTransferEnvelope lag = observer.PublishRow(5, true);
                AssertEx.Equal(0, lag.Edges.Count);
                AssertEx.Equal(0, lag.OutstandingTransferIds.Count);

                DynamicArtTransferEnvelope forwarded = observer.PublishRow(6, false);
                AssertEx.Equal(2, forwarded.Edges.Count);
                AssertEx.Equal(DynamicArtTransferPhase.Submitted, forwarded.Edges[0].Phase);
                AssertEx.Equal(DynamicArtTransferPhase.Completed, forwarded.Edges[1].Phase);
                AssertEx.Equal(0L, forwarded.Edges[0].EdgeOrdinal);
                AssertEx.Equal(1L, forwarded.Edges[1].EdgeOrdinal);

                logicalFrame = 12;
                Submit(host, 5, 1);
                host.Fire((uint)VblankProbe(0x0D50));
                DynamicArtTransferEnvelope terminal = observer.PublishTerminal(7);
                AssertEx.Equal(1, terminal.Edges.Count);
                AssertEx.Equal(true, terminal.Edges[0].TerminalForwarded);
                AssertEx.Equal(7, terminal.Edges[0].PublicationFrame);
            }
        }

        private static void RequiresEmptyLedgerAtSegmentArm()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 6, new[] { 0x0000 });
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => 0))
            {
                observer.ArmSegment();
                Submit(host, 6, 0);
                host.SetByte(0xF767, 1);
                host.Fire((uint)VblankProbe(0x0D50));
                observer.PublishRow(0, false);
                observer.EndSegment();
                AssertEx.Throws<InvalidOperationException>(
                    () => observer.ArmSegment(), "pending ledger");
            }
        }

        private static void CarriesPreparationIntoNamedRunSegment()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 48, new[] { 0x0000 });
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => 0))
            {
                Submit(host, 48, 0);
                IList<DynamicArtGapTransition> gap = observer.PublishGap();
                AssertEx.Equal(0, gap.Count);
                observer.ArmSegment();

                CompleteVblank(host, 0x1060);
                DynamicArtTransferEnvelope row =
                    observer.PublishRow(0, false);
                AssertEx.Equal(2, row.Edges.Count);
                AssertEx.Equal(DynamicArtTransferPhase.Submitted,
                    row.Edges[0].Phase);
                AssertEx.Equal(DynamicArtTransferPhase.Completed,
                    row.Edges[1].Phase);
                AssertEx.Equal(DynamicArtSubmissionOrigin.Segment,
                    row.Edges[0].SubmissionOrigin);
                AssertEx.Equal(row.Edges[0].TransferId,
                    row.Edges[1].TransferId);
                AssertEx.Equal(0, row.OutstandingTransferIds.Count);
            }
        }

        private static void ReplacesPreparationBeforeVblank()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 9, new[] { 0x5054 });
            DefineDplc(rom, 10, new[] { 0x2000 });
            using (var observer = new S1DynamicArtObserver(rom, host, () => 136632))
            {
                Submit(host, 9, 0);
                Submit(host, 10, 9);
                AssertEx.Equal(0, observer.PublishGap().Count);

                CompleteVblank(host, 0x0D50);
                IList<DynamicArtGapTransition> transitions = observer.PublishGap();
                AssertEx.Equal(2, transitions.Count);
                AssertEx.Equal(10, transitions[0].Edge.MappingFrame);
                AssertEx.Equal(0, transitions[0].Edge.TransferId);
                AssertRequest(transitions[0].Edge.Requests[0],
                    0x22610, 0, -1, 0xF000, 0x60);
                AssertEx.Equal(transitions[0].Edge.TransferId,
                    transitions[1].Edge.TransferId);
            }
        }

        private static void RejectsSubmissionWithoutPendingFlag()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 7, new[] { 0x1001 });
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => 2))
            {
                observer.ArmSegment();
                host.SetByte(S1Ram.PlayerBase + S1Ram.OffMappingFrame, 7);
                host.SetByte(0xF766, 0);
                host.Fire(DecisionEntry);
                AssertEx.Throws<InvalidOperationException>(
                    () => host.Fire(DecisionReturn), "staging flag");
                AssertEx.Equal(0, observer.PublishRow(0, false).Edges.Count);
            }
        }

        private static void RequiresNonzeroPreTransferProbe()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 7, new[] { 0x1001 });
            using (var observer = new S1DynamicArtObserver(rom, host, () => 2))
            {
                observer.ArmSegment();
                Submit(host, 7, 0);
                host.SetByte(0xF767, 0);
                host.Fire((uint)VblankProbe(0x0D50));
                host.Fire(0x0D50);
                DynamicArtTransferEnvelope envelope = observer.PublishRow(0, false);
                AssertEx.Equal(0, envelope.Edges.Count);
                AssertEx.Equal(0, envelope.OutstandingTransferIds.Count);
            }
        }

        private static void WritesRunGapTransitions()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 4, new[] { 0x0000 });
            int movieFrame = 40;
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => movieFrame))
            {
                Submit(host, 4, 0);
                IList<DynamicArtGapTransition> submitted = observer.PublishGap();
                AssertEx.Equal(0, submitted.Count);

                movieFrame = 41;
                CompleteVblank(host, 0x0D50);
                IList<DynamicArtGapTransition> completed = observer.PublishGap();
                AssertEx.Equal(2, completed.Count);
                AssertEx.Equal(DynamicArtTransferPhase.Submitted,
                    completed[0].Edge.Phase);
                AssertEx.Equal(DynamicArtSubmissionOrigin.RunGap,
                    completed[0].Edge.SubmissionOrigin);
                AssertEx.Equal(41, completed[0].Edge.MovieLogicalFrame);
                AssertEx.Equal(1, completed[0].AfterLedgerDescriptors.Count);
                AssertEx.Equal(DynamicArtTransferPhase.Completed,
                    completed[1].Edge.Phase);
                AssertEx.Equal(0, completed[1].AfterLedgerDescriptors.Count);
                observer.ArmSegment();
            }
        }

        private static void DrainsTerminalLedgerThroughGap()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 4, new[] { 0x0000 });
            using (var observer = new S1DynamicArtObserver(rom, host, () => 9))
            {
                observer.ArmSegment();
                Submit(host, 4, 0);
                host.Fire((uint)VblankProbe(0x0D50));
                DynamicArtTransferEnvelope terminal = observer.PublishTerminal(0);
                AssertEx.Equal(1, terminal.OutstandingTransferIds.Count);
                observer.EndSegment();

                host.SetByte(0xF767, 0);
                host.Fire(0x0D50);
                IList<DynamicArtGapTransition> transitions = observer.PublishGap();
                AssertEx.Equal(1, transitions.Count);
                AssertEx.Equal(DynamicArtTransferPhase.Completed,
                    transitions[0].Edge.Phase);
                AssertEx.Equal(DynamicArtSubmissionOrigin.Segment,
                    transitions[0].Edge.SubmissionOrigin);
                AssertEx.Equal(0, transitions[0].AfterLedgerDescriptors.Count);
                observer.ArmSegment();
            }
        }

        private static void ResetsSegmentLocalCursor()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            DefineDplc(rom, 4, new[] { 0x0000 });
            int logicalFrame = 0;
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => logicalFrame))
            {
                observer.ArmSegment();
                Submit(host, 4, 0);
                host.Fire((uint)VblankProbe(0x0D50));
                DynamicArtTransferEdge firstSubmission = observer.PublishRow(0, false)
                    .Edges[0];
                AssertEx.Equal(0, firstSubmission.LogicalEdgeIndex);
                host.SetByte(0xF767, 0);
                host.Fire(0x0D50);
                observer.PublishRow(1, false);
                observer.EndSegment();

                observer.ArmSegment();
                Submit(host, 4, 3);
                host.Fire((uint)VblankProbe(0x0D50));
                DynamicArtTransferEdge laterSubmission = observer.PublishRow(0, false)
                    .Edges[0];
                AssertEx.Equal(0, laterSubmission.LogicalEdgeIndex);
                AssertEx.Equal(true,
                    laterSubmission.TransferId > firstSubmission.TransferId);
                AssertEx.Equal(true,
                    laterSubmission.EdgeOrdinal > firstSubmission.EdgeOrdinal);
            }
        }

        private static void RejectsUnverifiedCallbackWindow()
        {
            var host = new DynamicArtFakeHost();
            byte[] rom = CreateRom();
            rom[DecisionEntry] ^= 0xFF;
            AssertEx.Throws<InvalidOperationException>(
                () => new S1DynamicArtObserver(rom, host, () => 0),
                "opcode window");
        }

        private static void CapturesRetailRomLifecycle()
        {
            byte[] rom = File.ReadAllBytes(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"));
            string moviePath = Path.GetFullPath(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "..", "..", "..", "..",
                "src", "test", "resources", "traces", "s1",
                "ghz1_fullrun", "ghz1_fullrun.bk2"));
            Bk2Movie movie = Bk2Reader.Read(moviePath);
            int logicalFrame = 0;
            int submissions = 0;
            int completions = 0;
            using (IGpgxHost host = GpgxHost.Open(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                movie.SyncSettings))
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => logicalFrame))
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
                        if (envelope.Edges[index].Phase
                            == DynamicArtTransferPhase.Submitted)
                        {
                            submissions++;
                        }
                        else
                        {
                            completions++;
                        }
                    }
                    if (submissions != 0 && submissions == completions)
                    {
                        return;
                    }
                    logicalFrame++;
                }
            }
            throw new InvalidOperationException(
                "retail S1 smoke did not observe a paired dynamic-art lifecycle");
        }

        private static void CapturesRetailSpecialStageLifecycle()
        {
            byte[] rom = File.ReadAllBytes(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"));
            string moviePath = Path.GetFullPath(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "..", "..", "..", "..",
                "src", "test", "resources", "traces", "s1", "runs",
                "s1-ghz-maze-roundtrip", "s1-ghz-maze-roundtrip.bk2"));
            Bk2Movie movie = Bk2Reader.Read(moviePath);
            int logicalFrame = 0;
            int specialStageRows = 0;
            int submissions = 0;
            int completions = 0;
            using (IGpgxHost host = GpgxHost.Open(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                movie.SyncSettings))
            using (var observer = new S1DynamicArtObserver(
                rom, host, () => logicalFrame))
            {
                observer.ArmSegment();
                foreach (Bk2Frame frame in movie.OpenFrameStream())
                {
                    ApplyFrame(host, frame);
                    host.Advance();
                    bool specialStage = S1Ram.U8(host, S1Ram.GameMode) == 0x10;
                    DynamicArtTransferEnvelope envelope = observer.PublishRow(
                        logicalFrame, host.IsLagged);
                    if (specialStage)
                    {
                        specialStageRows++;
                        for (int index = 0; index < envelope.Edges.Count; index++)
                        {
                            DynamicArtTransferEdge edge = envelope.Edges[index];
                            if (edge.Phase == DynamicArtTransferPhase.Submitted)
                            {
                                submissions++;
                            }
                            else
                            {
                                completions++;
                                AssertEx.Equal(0x0E64, edge.RomCallbackPc);
                            }
                        }
                    }
                    else if (specialStageRows != 0)
                    {
                        AssertEx.Equal(true, submissions != 0);
                        AssertEx.Equal(submissions, completions);
                        return;
                    }
                    logicalFrame++;
                }
            }
            throw new InvalidOperationException(
                "retail S1 special-stage run did not observe a paired dynamic-art lifecycle");
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
            if (pressed)
            {
                host.SetButton(name, true);
            }
        }

        private static void Submit(
            DynamicArtFakeHost host,
            int mappingFrame,
            int previousFrame)
        {
            host.SetByte(S1Ram.PlayerBase + S1Ram.OffMappingFrame, mappingFrame);
            host.SetByte(0xF766, previousFrame);
            host.Fire(DecisionEntry);
            host.SetByte(0xF767, 1);
            host.Fire(DecisionReturn);
        }

        private static void CompleteVblank(
            DynamicArtFakeHost host, int completionPc)
        {
            host.SetByte(0xF767, 1);
            host.Fire((uint)VblankProbe(completionPc));
            host.SetByte(0xF767, 0);
            host.Fire((uint)completionPc);
        }

        private static int VblankProbe(int completionPc)
        {
            switch (completionPc)
            {
                case 0x0D50: return 0x0D20;
                case 0x0E64: return 0x0E34;
                case 0x0F54: return 0x0F24;
                case 0x1060: return 0x1030;
                default: throw new InvalidOperationException("unknown VBlank completion");
            }
        }

        internal static byte[] CreateRom()
        {
            byte[] rom = new byte[0x2D000];
            DynamicArtRomProfile.GameProfile profile = DynamicArtRomProfile.Sonic1Rev01;
            foreach (DynamicArtRomProfile.OpcodeWindow window in profile.OpcodeWindows)
            {
                for (int index = 0; index < window.Bytes.Count; index++)
                {
                    rom[window.Address + index] = window.Bytes[index];
                }
            }
            return rom;
        }

        internal static void DefineDplc(byte[] rom, int frame, int[] entries)
        {
            const int table = 0x22310;
            int entry = 0x22380 + (frame * 0x10);
            WriteU16(rom, table + (frame * 2), entry - table);
            rom[entry] = (byte)entries.Length;
            for (int index = 0; index < entries.Length; index++)
            {
                WriteU16(rom, entry + 1 + (index * 2), entries[index]);
            }
        }

        private static void WriteU16(byte[] bytes, int offset, int value)
        {
            bytes[offset] = (byte)(value >> 8);
            bytes[offset + 1] = (byte)value;
        }

        private static void AssertRequest(
            DynamicArtRequest request,
            int romSource,
            int tile,
            int ramSource,
            int vram,
            int length)
        {
            AssertEx.Equal(romSource, request.RomSourceAddress);
            AssertEx.Equal(tile, request.SourceTileIndex);
            AssertEx.Equal(ramSource, request.RamSourceAddress);
            AssertEx.Equal(vram, request.VramDestination);
            AssertEx.Equal(length, request.ByteLength);
        }

        private sealed class DynamicArtFakeHost : IGpgxHost, ICpuRegisterReader
        {
            private readonly Dictionary<uint, Action> callbacks =
                new Dictionary<uint, Action>();
            private readonly Dictionary<string, uint> registers =
                new Dictionary<string, uint>();

            public DynamicArtFakeHost()
            {
                Ram = new byte[0x10000];
                registers["M68K A0"] = S1Ram.PlayerBase;
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

            public IDisposable RegisterExecuteCallback(uint address, Action callback)
            {
                callbacks.Add(address, callback);
                return new Registration(callbacks, address);
            }

            public void Fire(uint address)
            {
                callbacks[address]();
            }

            public void SetByte(int address, int value)
            {
                Ram[address] = (byte)value;
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
