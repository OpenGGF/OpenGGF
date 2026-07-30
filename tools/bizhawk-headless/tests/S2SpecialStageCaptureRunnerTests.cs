using System.Collections.Generic;
using System;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S2SpecialStageCaptureRunnerTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2 standalone special-stage runner exposes its exact profile",
                ExposesExactProfile));
            tests.Add(new TestMain.TestCase(
                "S2 standalone special-stage observer requires CPU registers",
                RequiresCpuRegisters));
            tests.Add(new TestMain.TestCase(
                "S2 standalone special-stage observer filters input callbacks",
                FiltersInputCallbacks));
            tests.Add(new TestMain.TestCase(
                "S2 standalone special-stage observer queues across lag",
                QueuesAcrossLag));
            tests.Add(new TestMain.TestCase(
                "S2 standalone special-stage observer enforces one pass per sample",
                EnforcesOnePassPerSample));
            tests.Add(new TestMain.TestCase(
                "S2 standalone special-stage runner includes first mode frame and stops on exit",
                IncludesFirstModeFrameAndStopsOnExit));
            tests.Add(new TestMain.TestCase(
                "S2 special-stage publication rejects scratch legacy"
                + " audit omission",
                PublicationRejectsScratchLegacyAuditOmission));
        }

        private static void PublicationRejectsScratchLegacyAuditOmission()
        {
            WithMovie(new[] { "|..|........|........|" }, movie =>
            {
                AssertEx.Throws<ArgumentNullException>(
                    () => S2SpecialStageCaptureRunner.Capture(
                        movie,
                        new ScriptedCaptureHost(),
                        "synthetic.bk2",
                        "2026-07-30",
                        new StringWriter(),
                        new StringWriter(),
                        new StringWriter(),
                        null),
                    "requires native load audit");
            });
        }

        private static void RequiresCpuRegisters()
        {
            AssertEx.Throws<InvalidOperationException>(() =>
                new S2SpecialStageRunObjectsObserver(
                    new RamBackedHost(), 100, () => 0),
                "requires CPU register access");
        }

        private static void FiltersInputCallbacks()
        {
            var host = ReadyHost();
            using (var observer = new S2SpecialStageRunObjectsObserver(
                host, 100, () => 7))
            {
                host.A0 = 0;
                host.Trigger(0x1156);
                AssertEx.Throws<InvalidOperationException>(
                    () => host.Trigger(0x52B2),
                    "without a preceding input sample");

                host.A0 = 0xF608;
                host.SetU32(0x0100, 0x00001234);
                host.Trigger(0x1156);
                AssertEx.Throws<InvalidOperationException>(
                    () => host.Trigger(0x52B2),
                    "without a preceding input sample");

                host.SetU32(0x0100, 0x0000088E);
                host.Trigger(0x1156);
                host.Trigger(0x52B2);
                IList<string> lines = observer.PublishForRow(8, false);
                AssertEx.Equal(1, lines.Count);
                AssertContains(lines[0], "\"input_sample_frame\":0");
                AssertContains(lines[0], "\"completion_cursor_frame\":7");
            }
        }

        private static void IncludesFirstModeFrameAndStopsOnExit()
        {
            WithMovie(new[]
            {
                "|..|........|........|",
                "|..|........|........|",
                "|..|U......S|........|",
                "|..|.D......|........|",
                "|..|........|........|"
            }, movie =>
            {
                var host = new ScriptedCaptureHost();
                var physics = new StringWriter();
                var aux = new StringWriter();
                var metadata = new StringWriter();
                S2SpecialStageCaptureResult result =
                    S2SpecialStageCaptureRunner.Capture(
                        movie,
                        host,
                        "synthetic.bk2",
                        "2026-07-29",
                        physics,
                        aux,
                        metadata,
                        S2DynamicArtObserverTests.CreateRom());
                AssertEx.Equal(2, result.Bk2FrameOffset);
                AssertEx.Equal(2, result.TraceFrameCount);
                string[] rows = physics.ToString().Split(
                    new[] { "\r\n" },
                    StringSplitOptions.None);
                AssertEx.Equal(
                    S2SpecialStageCsvWriter.Header,
                    rows[0]);
                AssertEx.Equal(true, rows[1].StartsWith("0,81,0,0,"));
                AssertEx.Equal(true, rows[2].StartsWith("1,2,0,1,"));
                AssertEx.Equal(
                    true,
                    metadata.ToString().Contains(
                        "\"bk2_frame_offset\": 2,\r\n"
                        + "  \"trace_frame_count\": 2,"));
                AssertEx.Equal(false, metadata.ToString().Contains("\n")
                    && metadata.ToString().Replace("\r\n", "")
                        .Contains("\n"));
                AssertEx.Equal(
                    result.TraceFrameCount,
                    Count(aux.ToString(),
                        "\"event\":\"dynamic_art_transfer_state\""));
                AssertContains(
                    metadata.ToString(),
                    "\"dynamic_art_transfer_state_per_frame_v1\"");
                AssertEx.Equal(
                    false,
                    metadata.ToString().Contains(
                        "\"load_queue_state_per_frame\""));
            });
        }

        private static void QueuesAcrossLag()
        {
            var host = ReadyHost();
            using (var observer = new S2SpecialStageRunObjectsObserver(
                host, 100, () => 4))
            {
                host.Trigger(0x1156);
                host.Trigger(0x52B2);
                AssertEx.Equal(0, observer.PublishForRow(4, true).Count);
                IList<string> lines = observer.PublishForRow(5, false);
                AssertEx.Equal(1, lines.Count);
                AssertContains(lines[0], "\"frame\":5");
                AssertContains(lines[0], "\"first_eligible_frame\":0");
            }
        }

        private static void EnforcesOnePassPerSample()
        {
            var host = ReadyHost();
            using (var observer = new S2SpecialStageRunObjectsObserver(
                host, 100, () => 4))
            {
                host.Trigger(0x1156);
                host.Trigger(0x52B2);
                AssertEx.Throws<InvalidOperationException>(
                    () => host.Trigger(0x52B2),
                    "consumed the same input sample");
            }
        }

        private static FakeObserverHost ReadyHost()
        {
            var host = new FakeObserverHost();
            host.CompletedFrameValue = 100;
            host.Ram[0xF600] = 0x10;
            host.Ram[0xDB23] = 0xFF;
            host.A0 = 0xF608;
            host.A7 = 0x0100;
            host.SetU32(0x0100, 0x0000088E);
            return host;
        }

        private static void AssertContains(string value, string fragment)
        {
            if (value.IndexOf(fragment, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected <" + value + "> to contain <" + fragment + ">.");
            }
        }

        private static int Count(string value, string needle)
        {
            int count = 0;
            int index = 0;
            while ((index = value.IndexOf(
                needle, index, StringComparison.Ordinal)) >= 0)
            {
                count++;
                index += needle.Length;
            }
            return count;
        }

        private sealed class FakeObserverHost
            : IGpgxHost, ICpuRegisterReader
        {
            private readonly Dictionary<uint, Action> callbacks =
                new Dictionary<uint, Action>();

            public FakeObserverHost()
            {
                Ram = new byte[0x10000];
            }

            public byte[] Ram { get; private set; }
            public uint A0 { get; set; }
            public uint A7 { get; set; }
            public int CompletedFrameValue { get; set; }
            public int CompletedFrame { get { return CompletedFrameValue; } }
            public bool IsLagged { get { return false; } }
            public int LagCount { get { return 0; } }

            public IDisposable RegisterExecuteCallback(
                uint address,
                Action callback)
            {
                callbacks[address] = callback;
                return NoOpCallbackRegistration.Instance;
            }

            public void Trigger(uint address)
            {
                callbacks[address]();
            }

            public uint ReadCpuRegister(string name)
            {
                return name == "M68K A0" ? A0 : A7;
            }

            public byte ReadMainRamByte(int offset) { return Ram[offset]; }
            public void ClearButtons() { }
            public void SetButton(string name, bool pressed) { }
            public void Advance() { CompletedFrameValue++; }
            public void Dispose() { }

            public void SetU32(int offset, uint value)
            {
                Ram[offset] = (byte)(value >> 24);
                Ram[offset + 1] = (byte)(value >> 16);
                Ram[offset + 2] = (byte)(value >> 8);
                Ram[offset + 3] = (byte)value;
            }
        }

        private sealed class ScriptedCaptureHost
            : IGpgxHost, ICpuRegisterReader
        {
            private readonly Dictionary<uint, Action> callbacks =
                new Dictionary<uint, Action>();
            private readonly byte[] ram = new byte[0x10000];

            public int CompletedFrame { get; private set; }
            public bool IsLagged { get { return CompletedFrame == 3; } }
            public int LagCount { get { return IsLagged ? 1 : 0; } }
            public uint ReadCpuRegister(string name)
            {
                return name == "M68K A0" ? 0xF608u : 0x0100u;
            }
            public IDisposable RegisterExecuteCallback(
                uint address,
                Action callback)
            {
                callbacks[address] = callback;
                return NoOpCallbackRegistration.Instance;
            }
            public void Advance()
            {
                CompletedFrame++;
                ram[0xF600] = CompletedFrame >= 2 && CompletedFrame <= 3
                    ? (byte)0x10 : (byte)0x0C;
            }
            public byte ReadMainRamByte(int offset) { return ram[offset]; }
            public void ClearButtons() { }
            public void SetButton(string name, bool pressed) { }
            public void Dispose() { }
        }

        private static void WithMovie(
            IEnumerable<string> rows,
            Action<Bk2Movie> body)
        {
            string directory = Path.Combine(
                Path.GetTempPath(),
                "openggf-s2-ss-" + Guid.NewGuid().ToString("N"));
            string path = Path.Combine(directory, "synthetic.bk2");
            Directory.CreateDirectory(directory);
            try
            {
                using (var stream = File.Create(path))
                using (var archive = new ZipArchive(
                    stream,
                    ZipArchiveMode.Create,
                    false))
                {
                    WriteEntry(
                        archive,
                        "Header.txt",
                        File.ReadAllText(Path.Combine(
                            AppDomain.CurrentDomain.BaseDirectory,
                            "fixtures",
                            "ghz1-header.txt")));
                    WriteEntry(
                        archive,
                        "SyncSettings.json",
                        File.ReadAllText(Path.Combine(
                            AppDomain.CurrentDomain.BaseDirectory,
                            "fixtures",
                            "ghz1-sync-settings.json")));
                    WriteEntry(
                        archive,
                        "Input Log.txt",
                        "[Input]\r\n"
                        + "LogKey:#Power|Reset|"
                        + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
                        + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|\r\n"
                        + string.Join("\r\n", rows)
                        + "\r\n[/Input]\r\n");
                }
                body(Bk2Reader.Read(path));
            }
            finally
            {
                Directory.Delete(directory, true);
            }
        }

        private static void WriteEntry(
            ZipArchive archive,
            string name,
            string content)
        {
            ZipArchiveEntry entry =
                archive.CreateEntry(name, CompressionLevel.NoCompression);
            using (Stream stream = entry.Open())
            using (var writer = new StreamWriter(
                stream,
                new UTF8Encoding(false)))
            {
                writer.Write(content);
            }
        }

        private static void ExposesExactProfile()
        {
            AssertEx.Equal(
                "s2_special_stage",
                S2SpecialStageCaptureRunner.TraceProfile);
        }
    }
}
