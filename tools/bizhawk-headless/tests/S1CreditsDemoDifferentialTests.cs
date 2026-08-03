using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Deferred Task 10 predecessor evidence for the one-time 20-to-42-column
    /// credits migration. This source is intentionally not registered by
    /// Task 6, so its installed-fixture comparisons and capture gates are
    /// inactive until the credits fleet is migrated or retired.
    /// </summary>
    internal static class S1CreditsDemoDifferentialTests
    {
        private static readonly string[] Directories =
        {
            "credits_00_ghz1", "credits_01_mz2", "credits_02_syz3",
            "credits_03_lz3", "credits_04_slz3", "credits_05_sbz1",
            "credits_06_sbz2", "credits_07_ghz1b"
        };
        private static readonly string[] CandidateDirectories =
        {
            "00_ghz1_credits_demo_1", "01_mz2_credits_demo",
            "02_syz3_credits_demo", "03_lz3_credits_demo",
            "04_slz3_credits_demo", "05_sbz1_credits_demo",
            "06_sbz2_credits_demo", "07_ghz1_credits_demo_2"
        };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1 credits predecessor evidence keeps eight 20-column fixtures",
                PredecessorEvidenceIsComplete));
            tests.Add(new TestMain.TestCase(
                "S1 credits native candidate preserves every predecessor column",
                NativeCandidatePreservesCommonColumns,
                game: "s1", kind: TestKind.Gate));
            tests.Add(new TestMain.TestCase(
                "S1 credits diagnostic candidate reports literal common-field deltas",
                ReportCandidateDeltas));
            tests.Add(new TestMain.TestCase(
                "S1 credits raw-host evidence is independent and hash-bound",
                RawHostEvidenceIsIndependentAndHashBound));
            tests.Add(new TestMain.TestCase(
                "S1 credits captures twice with deterministic logical evidence",
                CapturesTwiceWithDeterministicLogicalEvidence,
                game: "s1", kind: TestKind.Gate, serial: true,
                estimatedSeconds: 45.0));
        }

        private static void PredecessorEvidenceIsComplete()
        {
            string root = Path.Combine(EndToEndTests.RepositoryRoot,
                "src", "test", "resources", "traces", "s1");
            foreach (string directory in Directories)
            {
                string physics = Path.Combine(root, directory, "physics.csv");
                AssertEx.Equal(true, File.Exists(physics));
                string header;
                using (var reader = new StreamReader(physics))
                {
                    header = reader.ReadLine();
                }
                AssertEx.Equal(20, header.Split(',').Length);
            }
        }

        private static void NativeCandidatePreservesCommonColumns()
        {
            string candidateRoot = Environment.GetEnvironmentVariable(
                "OGGF_S1_CREDITS_CANDIDATE_ROOT");
            if (string.IsNullOrEmpty(candidateRoot)
                || !Directory.Exists(candidateRoot))
            {
                throw new TestMain.SkipTestException(
                    "OGGF_S1_CREDITS_CANDIDATE_ROOT not set.");
            }
            string predecessorRoot = Path.Combine(EndToEndTests.RepositoryRoot,
                "src", "test", "resources", "traces", "s1");
            for (int index = 0; index < Directories.Length; index++)
            {
                CompareCommonColumns(
                    Path.Combine(predecessorRoot, Directories[index], "physics.csv"),
                    Path.Combine(candidateRoot, CandidateDirectories[index],
                        "physics.csv.gz"));
            }
        }

        private static void CompareCommonColumns(
            string predecessorPath, string candidateGzipPath)
        {
            string[] oldLines = File.ReadAllLines(predecessorPath);
            string[] candidateLines;
            using (FileStream input = File.OpenRead(candidateGzipPath))
            using (var gzip = new System.IO.Compression.GZipStream(
                input, System.IO.Compression.CompressionMode.Decompress))
            using (var reader = new StreamReader(gzip))
            {
                var lines = new List<string>();
                string line;
                while ((line = reader.ReadLine()) != null) lines.Add(line);
                candidateLines = lines.ToArray();
            }
            AssertEx.Equal(oldLines.Length, candidateLines.Length);
            string[] oldHeader = oldLines[0].Split(',');
            string[] newHeader = candidateLines[0].Split(',');
            var newIndex = new Dictionary<string, int>(StringComparer.Ordinal);
            for (int index = 0; index < newHeader.Length; index++)
            {
                newIndex.Add(newHeader[index], index);
            }
            string[] mapped =
            {
                "frame", "input", "player_x", "player_y", "player_x_speed",
                "player_y_speed", "player_g_speed", "player_angle", "player_air",
                "player_rolling", "player_ground_mode", "player_x_sub", "player_y_sub",
                "player_routine", "camera_x", "camera_y", "rings", "player_status_byte",
                "gameplay_frame_counter", "player_stand_on_obj"
            };
            for (int row = 1; row < oldLines.Length; row++)
            {
                string[] oldFields = oldLines[row].Split(',');
                string[] newFields = candidateLines[row].Split(',');
                for (int column = 0; column < oldHeader.Length; column++)
                {
                    string actual = newFields[newIndex[mapped[column]]];
                    if (oldFields[column] != actual)
                    {
                        throw new InvalidOperationException(
                            Path.GetFileName(Path.GetDirectoryName(predecessorPath))
                            + " row " + (row - 1) + " field " + oldHeader[column]
                            + ": expected " + oldFields[column]
                            + " but was " + actual + ".");
                    }
                }
            }
        }

        private static void ReportCandidateDeltas()
        {
            string candidateRoot = Environment.GetEnvironmentVariable(
                "OGGF_S1_CREDITS_DIAGNOSTIC_ROOT");
            if (string.IsNullOrEmpty(candidateRoot)
                || !Directory.Exists(candidateRoot))
            {
                throw new TestMain.SkipTestException(
                    "OGGF_S1_CREDITS_DIAGNOSTIC_ROOT not set.");
            }
            string predecessorRoot = Path.Combine(EndToEndTests.RepositoryRoot,
                "src", "test", "resources", "traces", "s1");
            for (int demo = 0; demo < Directories.Length; demo++)
            {
                string[] oldLines = File.ReadAllLines(Path.Combine(
                    predecessorRoot, Directories[demo], "physics.csv"));
                string[] newLines = ReadGzipLines(Path.Combine(candidateRoot,
                    CandidateDirectories[demo], "physics.csv.gz"));
                string[] oldHeader = oldLines[0].Split(',');
                string[] newHeader = newLines[0].Split(',');
                var newIndex = new Dictionary<string, int>(StringComparer.Ordinal);
                for (int index = 0; index < newHeader.Length; index++) newIndex.Add(newHeader[index], index);
                string[] mapped = { "frame", "input", "player_x", "player_y", "player_x_speed", "player_y_speed", "player_g_speed", "player_angle", "player_air", "player_rolling", "player_ground_mode", "player_x_sub", "player_y_sub", "player_routine", "camera_x", "camera_y", "rings", "player_status_byte", "gameplay_frame_counter", "player_stand_on_obj" };
                var counts = new Dictionary<string, int>(StringComparer.Ordinal);
                var first = new Dictionary<string, string>(StringComparer.Ordinal);
                int compared = Math.Min(oldLines.Length, newLines.Length);
                for (int row = 1; row < compared; row++)
                {
                    string[] oldFields = oldLines[row].Split(',');
                    string[] newFields = newLines[row].Split(',');
                    for (int column = 0; column < oldHeader.Length; column++)
                    {
                        string key = oldHeader[column];
                        string actual = newFields[newIndex[mapped[column]]];
                        if (oldFields[column] == actual) continue;
                        counts[key] = counts.ContainsKey(key) ? counts[key] + 1 : 1;
                        if (!first.ContainsKey(key)) first.Add(key,
                            "row " + (row - 1) + " " + oldFields[column] + "->" + actual);
                    }
                }
                var summary = new List<string>();
                foreach (string field in oldHeader)
                {
                    if (counts.ContainsKey(field)) summary.Add(field + "=" + counts[field] + " (" + first[field] + ")");
                }
                Console.WriteLine("CREDITS-DIAGNOSTIC " + Directories[demo]
                    + " old_rows=" + (oldLines.Length - 1)
                    + " new_rows=" + (newLines.Length - 1)
                    + " deltas=" + string.Join("; ", summary.ToArray()));
            }
        }

        private static void RawHostEvidenceIsIndependentAndHashBound()
        {
            var host = new FakeS1Host(null);
            host.Ram[S1Ram.Ctrl1] = 0x78;
            host.SetU16(S1Ram.PlayerBase + S1Ram.OffXPos, 0x1234);
            host.SetU16(S1Ram.PlayerBase + S1Ram.OffYPos, 0x5678);
            host.Ram[S1Ram.PlayerBase + S1Ram.OffStatus] = 0x06;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffAngle] = 0x80;
            var collector = new S1CreditsRawHostEvidenceCollector();
            collector.Observe(3, 17, host);
            string emitted = S1TraceCsvWriter.FormatRow(
                17, S1InputMask.FromRomControllerByte(host.Ram[S1Ram.Ctrl1]), host);

            // Prove verification consumes the frozen raw observation, not a
            // second read performed after the writer has run.
            host.SetU16(S1Ram.PlayerBase + S1Ram.OffXPos, 0x9999);
            S1CreditsRawHostEvidenceRecord record = collector.Verify(
                3, "03_lz3_credits_demo", 17, "x", "player_x",
                emitted, new string('A', 64));
            AssertEx.Equal("$FFD008 u16be", record.RawSource);
            AssertEx.Equal("1234", record.RawValue);
            AssertEx.Equal("1234", record.EmittedValue);
            AssertEx.Equal(new string('A', 64), record.CandidateLogicalPayloadSha256);
            AssertEx.Throws<InvalidOperationException>(
                () => collector.Verify(
                    3, "03_lz3_credits_demo", 17, "x", "player_x",
                    emitted.Replace(",1234,", ",9999,"), new string('A', 64)),
                "raw-host mismatch");
        }

        private static void CapturesTwiceWithDeterministicLogicalEvidence()
        {
            string romPath = Environment.GetEnvironmentVariable("S1_ROM_PATH");
            string bizHawkHome = Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            if (string.IsNullOrEmpty(romPath) || !File.Exists(romPath))
            {
                throw new TestMain.SkipTestException("S1_ROM_PATH not set.");
            }
            if (string.IsNullOrEmpty(bizHawkHome) || !Directory.Exists(bizHawkHome))
            {
                throw new TestMain.SkipTestException("BIZHAWK_HOME not set.");
            }

            string first = TestScratch.CreateRootPath("credits-determinism-a");
            string second = TestScratch.CreateRootPath("credits-determinism-b");
            try
            {
                var firstEvidence = new S1CreditsRawHostEvidenceCollector();
                var secondEvidence = new S1CreditsRawHostEvidenceCollector();
                CaptureAll(romPath, first, firstEvidence);
                CaptureAll(romPath, second, secondEvidence);
                AssertDeterministicCandidate(first, second);
                AssertDynamicArtEvidence(first);
                AssertDynamicArtEvidence(second);
                AssertFirstDivergencesMatchRawHost(first, firstEvidence);
                AssertFirstDivergencesMatchRawHost(second, secondEvidence);
            }
            finally
            {
                if (Directory.Exists(first)) Directory.Delete(first, true);
                if (Directory.Exists(second)) Directory.Delete(second, true);
            }
        }

        private static void CaptureAll(
            string romPath,
            string outputRoot,
            S1CreditsRawHostEvidenceCollector evidence)
        {
            var publisher = new NoReplacePublisher(new TracePayloadCompressor(0));
            NoReplacePublisher.IncrementalStagingSession session = null;
            NoReplacePublisher.StagedPublicationSet staged = null;
            try
            {
                session = publisher.OpenSession(outputRoot);
                using (IGpgxHost host = GpgxHost.Open(
                    romPath, GpgxHost.CreateGhz1SyncSettings()))
                using (var sink = new S1CreditsDemoCollectionSink(session))
                {
                    S1CreditsDemoCaptureResult result =
                        S1CreditsDemoCaptureRunner.Capture(
                            host, host as IMainRamWriter, null,
                            "2000-01-02", sink, File.ReadAllBytes(romPath),
                            evidence);
                    AssertEx.Equal(8, result.CapturedIndices.Count);
                }
                staged = session.Complete();
                session = null;
                staged.Publish();
                staged = null;
            }
            finally
            {
                if (staged != null) staged.Dispose();
                if (session != null) session.Dispose();
            }
        }

        private static void AssertDeterministicCandidate(
            string first, string second)
        {
            string[] firstInventory = RelativeFileInventory(first);
            string[] secondInventory = RelativeFileInventory(second);
            AssertEx.Equal(24, firstInventory.Length);
            AssertEx.Equal(
                string.Join("\n", firstInventory),
                string.Join("\n", secondInventory));
            for (int demo = 0; demo < CandidateDirectories.Length; demo++)
            {
                string firstDirectory = Path.Combine(
                    first, CandidateDirectories[demo]);
                string secondDirectory = Path.Combine(
                    second, CandidateDirectories[demo]);
                AssertEx.Equal(
                    File.ReadAllText(Path.Combine(firstDirectory, "metadata.json")),
                    File.ReadAllText(Path.Combine(secondDirectory, "metadata.json")));
                AssertContains(
                    File.ReadAllText(Path.Combine(firstDirectory, "metadata.json")),
                    "\"recording_date\": \"2000-01-02\"");
                AssertBytesEqual(
                    ReadGzipBytes(Path.Combine(firstDirectory, "physics.csv.gz")),
                    ReadGzipBytes(Path.Combine(secondDirectory, "physics.csv.gz")));
                AssertBytesEqual(
                    ReadGzipBytes(Path.Combine(firstDirectory, "aux_state.jsonl.gz")),
                    ReadGzipBytes(Path.Combine(secondDirectory, "aux_state.jsonl.gz")));
            }
        }

        private static void AssertFirstDivergencesMatchRawHost(
            string candidateRoot,
            S1CreditsRawHostEvidenceCollector evidence)
        {
            string predecessorRoot = Path.Combine(EndToEndTests.RepositoryRoot,
                "src", "test", "resources", "traces", "s1");
            string[] mapped =
            {
                "frame", "input", "player_x", "player_y", "player_x_speed",
                "player_y_speed", "player_g_speed", "player_angle", "player_air",
                "player_rolling", "player_ground_mode", "player_x_sub", "player_y_sub",
                "player_routine", "camera_x", "camera_y", "rings", "player_status_byte",
                "gameplay_frame_counter", "player_stand_on_obj"
            };
            for (int demo = 0; demo < Directories.Length; demo++)
            {
                string[] oldLines = File.ReadAllLines(Path.Combine(
                    predecessorRoot, Directories[demo], "physics.csv"));
                string candidatePath = Path.Combine(candidateRoot,
                    CandidateDirectories[demo], "physics.csv.gz");
                string[] newLines = ReadGzipLines(candidatePath);
                string[] oldHeader = oldLines[0].Split(',');
                string[] newHeader = newLines[0].Split(',');
                var newIndex = new Dictionary<string, int>(StringComparer.Ordinal);
                for (int index = 0; index < newHeader.Length; index++)
                {
                    newIndex.Add(newHeader[index], index);
                }
                var seen = new HashSet<string>(StringComparer.Ordinal);
                int rows = Math.Min(oldLines.Length, newLines.Length);
                string logicalHash = Sha256(ReadGzipBytes(candidatePath));
                for (int line = 1; line < rows; line++)
                {
                    string[] oldFields = oldLines[line].Split(',');
                    string[] newFields = newLines[line].Split(',');
                    for (int column = 0; column < oldHeader.Length; column++)
                    {
                        if (seen.Contains(oldHeader[column])) continue;
                        string emitted = newFields[newIndex[mapped[column]]];
                        if (oldFields[column] == emitted) continue;
                        S1CreditsRawHostEvidenceRecord record = evidence.Verify(
                            demo, CandidateDirectories[demo], line - 1,
                            oldHeader[column], mapped[column], newLines[line],
                            logicalHash);
                        AssertEx.Equal(emitted, record.RawValue);
                        AssertEx.Equal(emitted, record.EmittedValue);
                        AssertEx.Equal(logicalHash,
                            record.CandidateLogicalPayloadSha256);
                        seen.Add(oldHeader[column]);
                    }
                }
                // Constant-zero v_framecount guarantees at least one
                // independently verified predecessor divergence per route.
                AssertEx.Equal(true, seen.Contains("v_framecount"));
            }
        }

        private static void AssertDynamicArtEvidence(string candidateRoot)
        {
            foreach (string directoryName in CandidateDirectories)
            {
                string directory = Path.Combine(candidateRoot, directoryName);
                int physicsRows = ReadGzipLines(Path.Combine(
                    directory, "physics.csv.gz")).Length - 1;
                string[] auxLines = ReadGzipLines(Path.Combine(
                    directory, "aux_state.jsonl.gz"));
                int dynamicRows = 0;
                int lastDynamicFrame = -1;
                int finalOutstanding = -1;
                foreach (string line in auxLines)
                {
                    JObject item = JObject.Parse(line);
                    if ((string)item["event"] != "dynamic_art_transfer_state")
                    {
                        continue;
                    }
                    dynamicRows++;
                    int frame = (int)item["frame"];
                    lastDynamicFrame = frame;
                    finalOutstanding = ((JArray)item[
                        "outstanding_transfer_ids"]).Count;
                    foreach (JToken edge in (JArray)item["edges"])
                    {
                        if ((bool)edge["terminal_forwarded"])
                        {
                            AssertEx.Equal(physicsRows - 1, frame);
                        }
                    }
                }
                AssertEx.Equal(physicsRows, dynamicRows);
                AssertEx.Equal(physicsRows - 1, lastDynamicFrame);
                AssertEx.Equal(0, finalOutstanding);
            }
        }

        private static string[] RelativeFileInventory(string root)
        {
            string[] files = Directory.GetFiles(root, "*", SearchOption.AllDirectories);
            for (int index = 0; index < files.Length; index++)
            {
                files[index] = files[index].Substring(root.Length)
                    .TrimStart(Path.DirectorySeparatorChar);
            }
            Array.Sort(files, StringComparer.Ordinal);
            return files;
        }

        private static byte[] ReadGzipBytes(string path)
        {
            using (FileStream input = File.OpenRead(path))
            using (var gzip = new System.IO.Compression.GZipStream(input,
                System.IO.Compression.CompressionMode.Decompress))
            using (var output = new MemoryStream())
            {
                gzip.CopyTo(output);
                return output.ToArray();
            }
        }

        private static void AssertBytesEqual(byte[] expected, byte[] actual)
        {
            AssertEx.Equal(
                Convert.ToBase64String(expected),
                Convert.ToBase64String(actual));
        }

        private static string Sha256(byte[] bytes)
        {
            using (SHA256 hash = SHA256.Create())
            {
                var value = new StringBuilder(64);
                foreach (byte item in hash.ComputeHash(bytes))
                {
                    value.Append(item.ToString("x2"));
                }
                return value.ToString();
            }
        }

        private static void AssertContains(string actual, string expected)
        {
            if (actual == null || actual.IndexOf(expected, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected text to contain '" + expected + "' but was '" + actual + "'.");
            }
        }

        private static string[] ReadGzipLines(string path)
        {
            using (FileStream input = File.OpenRead(path))
            using (var gzip = new System.IO.Compression.GZipStream(input,
                System.IO.Compression.CompressionMode.Decompress))
            using (var reader = new StreamReader(gzip))
            {
                var lines = new List<string>();
                string line;
                while ((line = reader.ReadLine()) != null) lines.Add(line);
                return lines.ToArray();
            }
        }
    }
}
