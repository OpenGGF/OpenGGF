using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Read-only predecessor evidence for the one-time 20-to-42-column
    /// credits migration. The capture gate compares common columns by name;
    /// columns absent from the predecessor are additions, never normalized
    /// mismatches. Canonical fixture bytes are never opened for writing.
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
