using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S1CreditsDemoCaptureRunnerTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1 credits catalog exposes the eight ROM ending demos",
                CreditsCatalogExposesEightRomDemos));
            tests.Add(new TestMain.TestCase(
                "S1 credits converts ROM controller input without Start",
                ConvertsRomControllerInput));
            tests.Add(new TestMain.TestCase(
                "S1 credits redirect writes only the verified setup state",
                RedirectWritesOnlyVerifiedSetupState));
            tests.Add(new TestMain.TestCase(
                "S1 credits collection rolls staged output back on duplicate",
                CollectionRollsBackOnDuplicate));
            tests.Add(new TestMain.TestCase(
                "S1 credits title redirect timeout includes lifecycle state",
                TitleRedirectTimeoutIncludesLifecycleState));
            tests.Add(new TestMain.TestCase(
                "S1 credits rejects skipped and duplicate all-route indices",
                RejectsSkippedAndDuplicateIndices));
            tests.Add(new TestMain.TestCase(
                "S1 credits forced compression publishes canonical inventory",
                ForcedCompressionPublishesCanonicalInventory));
            tests.Add(new TestMain.TestCase(
                "S1 credits metadata envelope is exact",
                MetadataEnvelopeIsExact));
            tests.Add(new TestMain.TestCase(
                "S1 credits synthetic all-eight capture owns lifecycle boundaries",
                SyntheticAllEightCaptureOwnsLifecycleBoundaries));
        }

        private static void ConvertsRomControllerInput()
        {
            AssertEx.Equal(0x0F, S1InputMask.FromRomControllerByte(0x8F));
            AssertEx.Equal(0x10, S1InputMask.FromRomControllerByte(0x70));
            AssertEx.Equal(0x1A, S1InputMask.FromRomControllerByte(0x9A));
        }

        private static void RedirectWritesOnlyVerifiedSetupState()
        {
            var writer = new RecordingWriter();
            S1CreditsDemoCaptureRunner.RedirectToCredits(writer);
            AssertEx.Equal("F600,FFF0,FFF1,FFF4,FFF5", writer.Keys());
            AssertEx.Equal((byte)0x1C, writer.Get(S1Ram.GameMode));
            AssertEx.Equal((byte)0, writer.Get(S1Ram.DemoFlag));
            AssertEx.Equal((byte)0, writer.Get(S1Ram.CreditsNum));
        }

        private static void CollectionRollsBackOnDuplicate()
        {
            string root = TestScratch.CreateRootPath("credits-rollback");
            NoReplacePublisher.IncrementalStagingSession session = null;
            try
            {
                var publisher = new NoReplacePublisher(
                    new TracePayloadCompressor(0));
                session = publisher.OpenSession(root);
                using (var sink = new S1CreditsDemoCollectionSink(session))
                {
                    S1CreditsDemoDefinition demo = S1CreditsDemoCatalog.Get(0);
                    TextWriter aux;
                    TextWriter physics = sink.Begin(demo, out aux);
                    physics.Write("frame\n0000\n");
                    aux.Write("{}\n");
                    sink.Complete("{}\n");
                    AssertEx.Throws<InvalidOperationException>(
                        () => sink.Begin(demo, out aux), "captured twice");
                }
                session.Dispose();
                session = null;
                AssertEx.Equal(false, File.Exists(Path.Combine(root,
                    "00_ghz1_credits_demo_1", "physics.csv.gz")));
            }
            finally
            {
                if (session != null) session.Dispose();
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void TitleRedirectTimeoutIncludesLifecycleState()
        {
            var host = new FakeS1Host(null);
            host.Ram[S1Ram.GameMode] = 0x04;
            AssertEx.Throws<InvalidOperationException>(
                () => S1CreditsDemoCaptureRunner.ThrowIfPreRedirectTimedOut(
                    host, 2401),
                "timed out waiting to redirect title to credits: mode=0x04");
            AssertEx.Throws<InvalidOperationException>(
                () => S1CreditsDemoCaptureRunner.ThrowIfDemoWaitTimedOut(
                    host, 2401),
                "timed out waiting for credits demo: mode=0x04");
            AssertEx.Throws<InvalidOperationException>(
                () => S1CreditsDemoCaptureRunner.ThrowIfSegmentTimedOut(
                    host, S1CreditsDemoCatalog.Get(3), 2000),
                "credits demo exceeded capture limit: mode=0x04");
        }

        private static void RejectsSkippedAndDuplicateIndices()
        {
            var captured = new List<int>();
            S1CreditsDemoCaptureRunner.ValidateAllRouteOrder(
                S1CreditsDemoCatalog.Get(0), 0, captured);
            captured.Add(0);
            AssertEx.Throws<InvalidOperationException>(
                () => S1CreditsDemoCaptureRunner.ValidateAllRouteOrder(
                    S1CreditsDemoCatalog.Get(2), 1, captured),
                "skipped or reordered demo 2");
            AssertEx.Throws<InvalidOperationException>(
                () => S1CreditsDemoCaptureRunner.ValidateAllRouteOrder(
                    S1CreditsDemoCatalog.Get(0), 1, captured),
                "duplicated demo 0");
        }

        private static void ForcedCompressionPublishesCanonicalInventory()
        {
            string root = TestScratch.CreateRootPath("credits-compression");
            NoReplacePublisher.IncrementalStagingSession session = null;
            NoReplacePublisher.StagedPublicationSet staged = null;
            try
            {
                session = new NoReplacePublisher(
                    new TracePayloadCompressor(0)).OpenSession(root);
                using (var sink = new S1CreditsDemoCollectionSink(session))
                {
                    TextWriter aux;
                    TextWriter physics = sink.Begin(
                        S1CreditsDemoCatalog.Get(0), out aux);
                    physics.Write(S1TraceCsvWriter.Header + "\n");
                    aux.Write("{}\n");
                    sink.Complete("{}\n");
                }
                staged = session.Complete();
                session = null;
                staged.Publish();
                staged = null;
                string directory = Path.Combine(
                    root, "00_ghz1_credits_demo_1");
                AssertEx.Equal(true, File.Exists(Path.Combine(
                    directory, "physics.csv.gz")));
                AssertEx.Equal(true, File.Exists(Path.Combine(
                    directory, "aux_state.jsonl.gz")));
                AssertEx.Equal(true, File.Exists(Path.Combine(
                    directory, "metadata.json")));
                AssertEx.Equal(false, File.Exists(Path.Combine(
                    directory, "physics.csv")));
            }
            finally
            {
                if (staged != null) staged.Dispose();
                if (session != null) session.Dispose();
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void MetadataEnvelopeIsExact()
        {
            string actual = S1CreditsDemoMetadataWriter.Format(
                S1CreditsDemoCatalog.Get(3), 123, 510,
                0x1111, 0x2222, 1, 2, 0x12345678, "2000-01-02");
            string expected =
                "{\n"
                + "  \"game\": \"s1\",\n"
                + "  \"zone\": \"lz\",\n"
                + "  \"zone_id\": 1,\n"
                + "  \"act\": 3,\n"
                + "  \"trace_type\": \"credits_demo\",\n"
                + "  \"input_source\": \"rom_ending_demo\",\n"
                + "  \"credits_demo_index\": 3,\n"
                + "  \"credits_demo_slug\": \"lz3_credits_demo\",\n"
                + "  \"emu_frame_start\": 123,\n"
                + "  \"bk2_frame_offset\": 0,\n"
                + "  \"trace_frame_count\": 510,\n"
                + "  \"start_x\": \"0x1111\",\n"
                + "  \"start_y\": \"0x2222\",\n"
                + "  \"characters\": [\"sonic\"],\n"
                + "  \"main_character\": \"sonic\",\n"
                + "  \"sidekicks\": [],\n"
                + "  \"rng_seed\": \"0x12345678\",\n"
                + "  \"recording_date\": \"2000-01-02\",\n"
                + "  \"recorder\": \"native-bizhawk-headless\",\n"
                + "  \"recorder_version\": \"3.0\",\n"
                + "  \"trace_schema\": 5,\n"
                + "  \"aux_schema_extras\": [\"s1_obj64_state_per_frame\", \"dynamic_art_transfer_state_per_frame\"],\n"
                + "  \"rom_checksum\": \"\",\n"
                + "  \"notes\": \"\"\n"
                + "}\n";
            AssertEx.Equal(expected, actual);
        }

        private static void SyntheticAllEightCaptureOwnsLifecycleBoundaries()
        {
            string root = TestScratch.CreateRootPath("credits-synthetic-all");
            int currentDemo = -1;
            int activeRows = 0;
            var host = new FakeS1Host((fake, completedFrame) =>
            {
                if (completedFrame == 1)
                {
                    fake.Ram[S1Ram.GameMode] = 0x0C;
                    return;
                }
                if (fake.Ram[S1Ram.GameMode] == 0x1C)
                {
                    currentDemo++;
                    activeRows = 0;
                    S1CreditsDemoDefinition demo =
                        S1CreditsDemoCatalog.Get(currentDemo);
                    fake.Ram[S1Ram.GameMode] = 0x08;
                    fake.SetU16(S1Ram.DemoFlag, 0x8001);
                    fake.SetU16(S1Ram.CreditsNum,
                        (ushort)(currentDemo + 1));
                    fake.SetU16(S1Ram.Zone,
                        (ushort)demo.ZoneActWord);
                    fake.Ram[S1Ram.PlayerBase + S1Ram.OffRoutine] = 2;
                    fake.SetU16(S1Ram.PlayerBase + S1Ram.OffXPos,
                        (ushort)(0xE000 + currentDemo));
                    fake.SetU16(S1Ram.PlayerBase + S1Ram.OffYPos,
                        (ushort)(0xE100 + currentDemo));
                    return;
                }
                if (fake.Ram[S1Ram.GameMode] != 0x08) return;
                if (activeRows < 2)
                {
                    fake.SetU16(S1Ram.PlayerBase + S1Ram.OffXPos,
                        (ushort)(0x1000 + currentDemo * 0x10 + activeRows));
                    fake.SetU16(S1Ram.PlayerBase + S1Ram.OffYPos,
                        (ushort)(0x2000 + currentDemo * 0x10 + activeRows));
                    fake.Ram[S1Ram.Ctrl1] = activeRows == 0
                        ? (byte)0x71
                        : (byte)0x80;
                    activeRows++;
                    return;
                }
                fake.SetU16(S1Ram.PlayerBase + S1Ram.OffXPos,
                    (ushort)(0xF000 + currentDemo));
                fake.Ram[S1Ram.GameMode] = 0x1C;
                fake.SetU16(S1Ram.DemoFlag, 0);
            });

            NoReplacePublisher.IncrementalStagingSession session = null;
            NoReplacePublisher.StagedPublicationSet staged = null;
            try
            {
                session = new NoReplacePublisher(
                    new TracePayloadCompressor(0)).OpenSession(root);
                S1CreditsDemoCaptureResult result;
                using (var sink = new S1CreditsDemoCollectionSink(session))
                {
                    result = S1CreditsDemoCaptureRunner.Capture(
                        host, host, null, "2000-01-02", sink,
                        S1DynamicArtObserverTests.CreateRom());
                }
                AssertEx.Equal("0,1,2,3,4,5,6,7",
                    string.Join(",", result.CapturedIndices));
                staged = session.Complete();
                session = null;
                staged.Publish();
                staged = null;

                AssertEx.Equal(24, Directory.GetFiles(
                    root, "*", SearchOption.AllDirectories).Length);
                for (int demo = 0; demo < 8; demo++)
                {
                    string directory = Path.Combine(root,
                        S1CreditsDemoCollectionSink.DirectoryName(
                            S1CreditsDemoCatalog.Get(demo)));
                    string[] rows = ReadGzipLines(Path.Combine(
                        directory, "physics.csv.gz"));
                    AssertEx.Equal(3, rows.Length);
                    string[] header = rows[0].Split(',');
                    string[] first = rows[1].Split(',');
                    string[] second = rows[2].Split(',');
                    AssertEx.Equal((0x1000 + demo * 0x10).ToString("X4"),
                        first[Array.IndexOf(header, "player_x")]);
                    AssertEx.Equal((0x1000 + demo * 0x10 + 1).ToString("X4"),
                        second[Array.IndexOf(header, "player_x")]);
                    AssertEx.Equal("0011", first[Array.IndexOf(header, "input")]);
                    AssertEx.Equal("0000", second[Array.IndexOf(header, "input")]);
                    AssertEx.Equal(false,
                        string.Join(",", rows).Contains((0xE000 + demo).ToString("X4")));
                    AssertEx.Equal(false,
                        string.Join(",", rows).Contains((0xF000 + demo).ToString("X4")));
                    string aux = ReadGzipText(Path.Combine(
                        directory, "aux_state.jsonl.gz"));
                    string firstAuxLine = aux.Split('\n')[0];
                    AssertContains(firstAuxLine, "\"frame\":0");
                    AssertContains(firstAuxLine, "\"event\":\"routine_change\"");
                    string metadata = File.ReadAllText(Path.Combine(
                        directory, "metadata.json"));
                    AssertContains(metadata, "\"trace_frame_count\": 2");
                }
                AssertEx.Equal(host.CompletedFrame, host.ClearButtonsCount);
                foreach (string button in host.ButtonWrites)
                {
                    AssertEx.Equal("Power=True", button);
                }
            }
            finally
            {
                if (staged != null) staged.Dispose();
                if (session != null) session.Dispose();
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static string[] ReadGzipLines(string path)
        {
            return ReadGzipText(path).Split(new[] { '\n' },
                StringSplitOptions.RemoveEmptyEntries);
        }

        private static string ReadGzipText(string path)
        {
            using (FileStream input = File.OpenRead(path))
            using (var gzip = new System.IO.Compression.GZipStream(input,
                System.IO.Compression.CompressionMode.Decompress))
            using (var reader = new StreamReader(gzip))
            {
                return reader.ReadToEnd();
            }
        }

        private static void AssertContains(string actual, string expected)
        {
            if (actual.IndexOf(expected, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected text to contain '" + expected + "' but was '"
                    + actual + "'.");
            }
        }

        private sealed class RecordingWriter : IMainRamWriter
        {
            private readonly SortedDictionary<int, byte> writes =
                new SortedDictionary<int, byte>();

            public void WriteMainRamByte(int offset, byte value)
            {
                writes.Add(offset, value);
            }

            public byte Get(int offset) { return writes[offset]; }

            public string Keys()
            {
                var keys = new List<string>();
                foreach (int key in writes.Keys) keys.Add(key.ToString("X4"));
                return string.Join(",", keys.ToArray());
            }
        }

        private static void CreditsCatalogExposesEightRomDemos()
        {
            Type catalog = typeof(S1InputMask).Assembly.GetType(
                "OpenGGF.BizHawk.Headless.S1CreditsDemoCatalog");
            AssertEx.Equal(true, catalog != null);
            var all = (Array)catalog.GetMethod("All").Invoke(null, null);
            AssertEx.Equal(8, all.Length);
            object lz3 = all.GetValue(3);
            AssertEx.Equal("lz3_credits_demo", (string)lz3.GetType()
                .GetProperty("Slug").GetValue(lz3, null));
            AssertEx.Equal(0x0102, (int)lz3.GetType()
                .GetProperty("ZoneActWord").GetValue(lz3, null));
            AssertEx.Equal(510, (int)lz3.GetType()
                .GetProperty("TimerFrames").GetValue(lz3, null));
        }
    }
}
