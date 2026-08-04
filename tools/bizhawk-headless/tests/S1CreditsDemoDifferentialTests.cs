using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using BizHawk.Headless.Gpgx;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Safe pre-capture evidence for the one-time 20-to-42-column credits
    /// migration. Candidate-root comparison is owned by the Task 7 Python
    /// comparator; this native registry contains only predecessor inventory,
    /// raw-host independence, and deterministic real-ROM capture evidence.
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

        public static void RegisterPreCapture(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1 credits predecessor evidence keeps eight 20-column fixtures",
                PredecessorEvidenceIsComplete));
            tests.Add(new TestMain.TestCase(
                "S1 credits raw-host sidecar streams canonical independent observations",
                RawHostSidecarStreamsCanonicalIndependentObservations));
            tests.Add(new TestMain.TestCase(
                "S1 credits raw-host sidecar rejects order limits and seal failure",
                RawHostSidecarRejectsOrderLimitsAndSealFailure));
            tests.Add(new TestMain.TestCase(
                "S1 credits CLI publication failure removes raw sidecar spool",
                RunS1CreditsPublicationFailureRemovesRawSpool));
            tests.Add(new TestMain.TestCase(
                "S1 credits CLI seal failure quarantines published output",
                RunS1CreditsSealFailureQuarantinesCandidate));
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

        private static void RawHostSidecarStreamsCanonicalIndependentObservations()
        {
            string root = TestScratch.CreateRootPath("credits-raw-unit");
            string candidate = Path.Combine(root, "candidate");
            string sidecarPath = Path.Combine(root, "evidence", "raw.jsonl");
            Directory.CreateDirectory(candidate);
            var host = new FakeS1Host(null);
            host.Ram[S1Ram.Ctrl1] = 0x78;
            host.SetU16(S1Ram.PlayerBase + S1Ram.OffXPos, 0x1234);
            host.SetU16(S1Ram.PlayerBase + S1Ram.OffYPos, 0x5678);
            host.Ram[S1Ram.PlayerBase + S1Ram.OffStatus] = 0x06;
            host.Ram[S1Ram.PlayerBase + S1Ram.OffAngle] = 0x80;
            try
            {
                using (var collector = new S1CreditsRawHostEvidenceCollector(
                    sidecarPath, "capture-unit", candidate,
                    RomIdentity.Sonic1Rev01Sha1))
                {
                    for (int demo = 0; demo < 8; demo++)
                    {
                        if (demo == 3)
                        {
                            host.SetU16(S1Ram.PlayerBase + S1Ram.OffXPos, 0x1234);
                        }
                        collector.Observe(demo, 0, host);
                        collector.CompleteRoute(demo, 1);
                        host.SetU16(S1Ram.PlayerBase + S1Ram.OffXPos, 0x9999);
                    }
                    collector.Seal(AllEightResult());
                }

                string[] lines = File.ReadAllLines(sidecarPath);
                AssertEx.Equal(162, lines.Length);
                JObject header = JObject.Parse(lines[0]);
                AssertEx.Equal("header", (string)header["record_type"]);
                AssertEx.Equal("openggf-s1-credits-raw-observations-v1",
                    (string)header["format"]);
                AssertEx.Equal("capture-unit", (string)header["capture_id"]);
                AssertEx.Equal(Path.GetFullPath(candidate),
                    (string)header["candidate_root"]);

                JObject x = JObject.Parse(lines[1 + 3 * 20 + 2]);
                AssertEx.Equal("observation", (string)x["record_type"]);
                AssertEx.Equal(3, (int)x["demo_index"]);
                AssertEx.Equal("credits_03_lz3", (string)x["route"]);
                AssertEx.Equal("x", (string)x["common_field"]);
                AssertEx.Equal("0xFFFFD008", (string)x["ram_address"]);
                AssertEx.Equal("big", (string)x["endianness"]);
                AssertEx.Equal("1234", (string)x["raw_value"]);
                AssertEx.Equal(null, x["emitted_value"]);
                AssertEx.Equal(null, x["predecessor_value"]);
                AssertEx.Equal(null, x["candidate_logical_sha256"]);

                JObject completion = JObject.Parse(lines[lines.Length - 1]);
                AssertEx.Equal("completion", (string)completion["record_type"]);
                AssertEx.Equal(true, (bool)completion["all_eight_complete"]);
                AssertEx.Equal(8, (int)completion["total_rows"]);
                AssertEx.Equal(160, (int)completion["observation_count"]);
                byte[] preceding = Encoding.UTF8.GetBytes(
                    string.Join("\n", lines, 0, lines.Length - 1) + "\n");
                AssertEx.Equal(preceding.Length,
                    (long)completion["preceding_byte_count"]);
                AssertEx.Equal(Sha256(preceding),
                    (string)completion["preceding_sha256"]);
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void RawHostSidecarRejectsOrderLimitsAndSealFailure()
        {
            string root = TestScratch.CreateRootPath("credits-raw-failures");
            string candidate = Path.Combine(root, "candidate");
            Directory.CreateDirectory(candidate);
            try
            {
                string order = Path.Combine(root, "order.jsonl");
                using (var collector = new S1CreditsRawHostEvidenceCollector(
                    order, "order", candidate, RomIdentity.Sonic1Rev01Sha1))
                {
                    AssertEx.Throws<InvalidOperationException>(
                        () => collector.Observe(1, 0, new FakeS1Host(null)),
                        "expected demo 0 row 0");
                }
                AssertEx.Equal(false, File.Exists(order));

                string count = Path.Combine(root, "count.jsonl");
                using (var collector = new S1CreditsRawHostEvidenceCollector(
                    count, "count", candidate, RomIdentity.Sonic1Rev01Sha1,
                    19, 64 * 1024 * 1024, LibcLinkOperation.Instance))
                {
                    AssertEx.Throws<InvalidOperationException>(
                        () => collector.Observe(0, 0, new FakeS1Host(null)),
                        "86,400-observation limit");
                }
                AssertEx.Equal(false, File.Exists(count));

                string bytes = Path.Combine(root, "bytes.jsonl");
                AssertEx.Throws<InvalidOperationException>(
                    () => new S1CreditsRawHostEvidenceCollector(
                        bytes, "bytes", candidate, RomIdentity.Sonic1Rev01Sha1,
                        86400, 1, LibcLinkOperation.Instance),
                    "64-MiB limit");
                AssertEx.Equal(false, File.Exists(bytes));
                AssertEx.Equal(0, Directory.GetFiles(
                    root, "bytes.jsonl.tmp.*", SearchOption.AllDirectories).Length);

                string abandoned = Path.Combine(root, "abandoned.jsonl");
                using (var collector = new S1CreditsRawHostEvidenceCollector(
                    abandoned, "abandoned", candidate,
                    RomIdentity.Sonic1Rev01Sha1))
                {
                    RecordOneRowAllRoutes(collector, new FakeS1Host(null));
                    // Models capture or candidate publication failure: no
                    // completion/seal is legal, so disposal removes the spool.
                }
                AssertEx.Equal(false, File.Exists(abandoned));

                string sealedPath = Path.Combine(root, "seal.jsonl");
                using (var collector = new S1CreditsRawHostEvidenceCollector(
                    sealedPath, "seal", candidate, RomIdentity.Sonic1Rev01Sha1,
                    86400, 64 * 1024 * 1024, new FailingLinkOperation()))
                {
                    RecordOneRowAllRoutes(collector, new FakeS1Host(null));
                    AssertEx.Throws<InvalidOperationException>(
                        () => global::BizHawk.Headless.Gpgx.Program
                            .SealCreditsRawEvidenceAfterCandidatePublication(
                                collector, AllEightResult(), candidate),
                        "QUARANTINED");
                }
                AssertEx.Equal(true, Directory.Exists(candidate));
                AssertEx.Equal(false, File.Exists(sealedPath));

                string raced = Path.Combine(root, "raced.jsonl");
                using (var collector = new S1CreditsRawHostEvidenceCollector(
                    raced, "raced", candidate, RomIdentity.Sonic1Rev01Sha1))
                {
                    RecordOneRowAllRoutes(collector, new FakeS1Host(null));
                    File.WriteAllText(raced, "competing-writer");
                    AssertEx.Throws<IOException>(
                        () => collector.Seal(AllEightResult()),
                        "will not be replaced");
                }
                AssertEx.Equal("competing-writer", File.ReadAllText(raced));
                File.Delete(raced);
                AssertEx.Equal(0, Directory.GetFiles(
                    root, "*.tmp.*", SearchOption.AllDirectories).Length);
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void RunS1CreditsPublicationFailureRemovesRawSpool()
        {
            string root = TestScratch.CreateRootPath("credits-cli-publication-failure");
            string candidate = Path.Combine(root, "candidate");
            string sidecar = Path.Combine(root, "raw", "publication.jsonl");
            Directory.CreateDirectory(Path.GetDirectoryName(sidecar));
            try
            {
                CommandLineOptions options = CommandLineOptions.Parse(new[]
                {
                    "--mode", "trace", "--rom", "s1.gen", "--output", candidate,
                    "--trace-profile", "credits_demo", "--credits-target", "all",
                    "--credits-raw-observations", sidecar,
                    "--credits-raw-observation-id", "publication-failure"
                });
                int result = global::BizHawk.Headless.Gpgx.Program
                    .RunS1CreditsDemoForTests(
                        options, new Version(2, 11),
                        RomIdentity.Sonic1Rev01Sha1, new byte[0],
                        new StringWriter(), new StringWriter(),
                        () => new NoReplacePublisher(
                            new AlwaysFailLinkOperation(), File.Delete,
                            new TracePayloadCompressor(0)),
                        DefaultRawEvidenceFactory,
                        StageEightSyntheticCredits);
                AssertEx.Equal(1, result);
                AssertEx.Equal(false, File.Exists(sidecar));
                AssertEx.Equal(0, TemporaryFiles(root).Length);
                AssertEx.Equal(0, PublishedFiles(candidate).Length);
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void RunS1CreditsSealFailureQuarantinesCandidate()
        {
            string root = TestScratch.CreateRootPath("credits-cli-seal-failure");
            string candidate = Path.Combine(root, "candidate");
            string sidecar = Path.Combine(root, "raw", "seal.jsonl");
            Directory.CreateDirectory(Path.GetDirectoryName(sidecar));
            try
            {
                CommandLineOptions options = CommandLineOptions.Parse(new[]
                {
                    "--mode", "trace", "--rom", "s1.gen", "--output", candidate,
                    "--trace-profile", "credits_demo", "--credits-target", "all",
                    "--credits-raw-observations", sidecar,
                    "--credits-raw-observation-id", "seal-failure"
                });
                int result = global::BizHawk.Headless.Gpgx.Program
                    .RunS1CreditsDemoForTests(
                        options, new Version(2, 11),
                        RomIdentity.Sonic1Rev01Sha1, new byte[0],
                        new StringWriter(), new StringWriter(),
                        () => new NoReplacePublisher(
                            new TracePayloadCompressor(0)),
                        (path, id, candidateRoot, sha1) =>
                            new S1CreditsRawHostEvidenceCollector(
                                path, id, candidateRoot, sha1,
                                S1CreditsRawHostEvidenceCollector.MaximumObservations,
                                S1CreditsRawHostEvidenceCollector.MaximumBytes,
                                new AlwaysFailLinkOperation()),
                        StageEightSyntheticCredits);
                AssertEx.Equal(1, result);
                AssertEx.Equal(false, File.Exists(sidecar));
                AssertEx.Equal(0, TemporaryFiles(root).Length);
                AssertEx.Equal(true, PublishedFiles(candidate).Length > 0);
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
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
            string firstSidecar = first + "-raw.jsonl";
            string secondSidecar = second + "-raw.jsonl";
            try
            {
                CaptureAll(romPath, first, firstSidecar, "real-rom-a");
                CaptureAll(romPath, second, secondSidecar, "real-rom-b");
                AssertDeterministicCandidate(first, second);
                AssertDynamicArtEvidence(first);
                AssertDynamicArtEvidence(second);
                AssertFirstDivergencesMatchRawHost(first, firstSidecar);
                AssertFirstDivergencesMatchRawHost(second, secondSidecar);
                AssertObservationStreamsEqual(firstSidecar, secondSidecar);
            }
            finally
            {
                if (Directory.Exists(first)) Directory.Delete(first, true);
                if (Directory.Exists(second)) Directory.Delete(second, true);
                if (File.Exists(firstSidecar)) File.Delete(firstSidecar);
                if (File.Exists(secondSidecar)) File.Delete(secondSidecar);
            }
        }

        private static void CaptureAll(
            string romPath,
            string outputRoot,
            string sidecarPath,
            string captureId)
        {
            var stdout = new StringWriter(CultureInfo.InvariantCulture);
            var stderr = new StringWriter(CultureInfo.InvariantCulture);
            int exitCode = global::BizHawk.Headless.Gpgx.Program.Run(new[]
            {
                "--mode", "trace",
                "--rom", romPath,
                "--output", outputRoot,
                "--trace-profile", "credits_demo",
                "--credits-target", "all",
                "--credits-raw-observations", sidecarPath,
                "--credits-raw-observation-id", captureId
            }, stdout, stderr);
            AssertEx.Equal(string.Empty, stderr.ToString());
            AssertEx.Equal(0, exitCode);
            AssertContains(stdout.ToString(),
                "Credits raw observations: " + Path.GetFullPath(sidecarPath));
            AssertEx.Equal(true, File.Exists(sidecarPath));
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
                JObject firstMetadata = JObject.Parse(File.ReadAllText(
                    Path.Combine(firstDirectory, "metadata.json")));
                JObject secondMetadata = JObject.Parse(File.ReadAllText(
                    Path.Combine(secondDirectory, "metadata.json")));
                AssertEx.Equal(true, firstMetadata["recording_date"] != null);
                firstMetadata.Remove("recording_date");
                secondMetadata.Remove("recording_date");
                AssertEx.Equal(firstMetadata.ToString(), secondMetadata.ToString());
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
            string sidecarPath)
        {
            Dictionary<string, JObject> evidence = ReadRawObservations(sidecarPath);
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
                        string key = demo + ":" + (line - 1) + ":"
                            + oldHeader[column];
                        JObject record = evidence[key];
                        AssertEx.Equal(emitted, (string)record["raw_value"]);
                        AssertEx.Equal(null, record["emitted_value"]);
                        AssertEx.Equal(null, record["candidate_logical_sha256"]);
                        seen.Add(oldHeader[column]);
                    }
                }
                // Constant-zero v_framecount guarantees at least one
                // independently verified predecessor divergence per route.
                AssertEx.Equal(true, seen.Contains("v_framecount"));
            }
        }

        private static Dictionary<string, JObject> ReadRawObservations(
            string sidecarPath)
        {
            string[] lines = File.ReadAllLines(sidecarPath);
            AssertEx.Equal(true, lines.Length > 2);
            JObject completion = JObject.Parse(lines[lines.Length - 1]);
            byte[] preceding = Encoding.UTF8.GetBytes(
                string.Join("\n", lines, 0, lines.Length - 1) + "\n");
            AssertEx.Equal(preceding.Length,
                (long)completion["preceding_byte_count"]);
            AssertEx.Equal(Sha256(preceding),
                (string)completion["preceding_sha256"]);
            var result = new Dictionary<string, JObject>(StringComparer.Ordinal);
            for (int index = 1; index < lines.Length - 1; index++)
            {
                JObject item = JObject.Parse(lines[index]);
                string key = ((int)item["demo_index"]).ToString()
                    + ":" + ((int)item["row"]).ToString()
                    + ":" + (string)item["common_field"];
                result.Add(key, item);
            }
            AssertEx.Equal((int)completion["observation_count"], result.Count);
            return result;
        }

        private static void AssertObservationStreamsEqual(
            string firstSidecar, string secondSidecar)
        {
            Dictionary<string, JObject> first = ReadRawObservations(firstSidecar);
            Dictionary<string, JObject> second = ReadRawObservations(secondSidecar);
            AssertEx.Equal(first.Count, second.Count);
            foreach (KeyValuePair<string, JObject> pair in first)
            {
                AssertEx.Equal(pair.Value.ToString(), second[pair.Key].ToString());
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

        private static S1CreditsDemoCaptureResult AllEightResult()
        {
            return new S1CreditsDemoCaptureResult(
                new List<int> { 0, 1, 2, 3, 4, 5, 6, 7 });
        }

        private static void RecordOneRowAllRoutes(
            S1CreditsRawHostEvidenceCollector collector,
            IGpgxHost host)
        {
            for (int demo = 0; demo < 8; demo++)
            {
                collector.Observe(demo, 0, host);
                collector.CompleteRoute(demo, 1);
            }
        }

        private static S1CreditsRawHostEvidenceCollector
            DefaultRawEvidenceFactory(
                string path, string id, string candidateRoot, string sha1)
        {
            return new S1CreditsRawHostEvidenceCollector(
                path, id, candidateRoot, sha1);
        }

        private static S1CreditsDemoCaptureResult StageEightSyntheticCredits(
            S1CreditsRawHostEvidenceCollector rawEvidence,
            S1CreditsDemoCollectionSink sink)
        {
            if (rawEvidence != null)
            {
                RecordOneRowAllRoutes(rawEvidence, new FakeS1Host(null));
            }
            foreach (S1CreditsDemoDefinition demo in S1CreditsDemoCatalog.All())
            {
                TextWriter aux;
                TextWriter physics = sink.Begin(demo, out aux);
                physics.Write(S1TraceCsvWriter.Header);
                physics.Write('\n');
                aux.Write("{\"event\":\"synthetic\"}\n");
                sink.Complete("{}\n");
            }
            return AllEightResult();
        }

        private static string[] TemporaryFiles(string root)
        {
            return Directory.GetFiles(
                root, "*.tmp.*", SearchOption.AllDirectories);
        }

        private static string[] PublishedFiles(string root)
        {
            return Directory.Exists(root)
                ? Directory.GetFiles(root, "*", SearchOption.AllDirectories)
                : new string[0];
        }

        private sealed class FailingLinkOperation : ILinkOperation
        {
            public void Create(string temporary, string finalPath)
            {
                throw new IOException("synthetic sidecar seal failure");
            }
        }

        private sealed class AlwaysFailLinkOperation : ILinkOperation
        {
            public void Create(string temporary, string finalPath)
            {
                throw new IOException("synthetic candidate publication failure");
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
