using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Differential gates proving the native trace capture reproduces the Lua
    /// recorder byte-for-byte against the canonical S1 fixtures
    /// (src/test/resources/traces/s1/ghz1_fullrun/ and
    /// src/test/resources/traces/s1/mz1_fullrun/). Each case runs the
    /// trace-mode CLI end-to-end through run.sh and asserts the canonical
    /// physics.csv and aux_state.jsonl sha256 hashes (fixtures stored only as
    /// .gz are hashed after decompression), the detected BK2 frame offset,
    /// and metadata.json equality normalized only on the recording_date
    /// value. Skips (does not pass) when S1_ROM_PATH or a BizHawk
    /// distribution is absent; fails (does not skip) on any hash mismatch.
    /// </summary>
    internal static class S1TraceDifferentialTests
    {
        private const int CaptureTimeoutMilliseconds = 600000;
        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");

        private sealed class CanonicalTrace
        {
            public string CaseName;
            public string FixtureDirectoryName;
            public string MovieFileName;
            public string PhysicsSha256;
            public string AuxStateSha256;
            public int Bk2FrameOffset;
            public int TraceFrameCount;
            public int MovieFrameCount;
        }

        private static readonly CanonicalTrace Ghz1 = new CanonicalTrace
        {
            CaseName = "GHZ1",
            FixtureDirectoryName = "ghz1_fullrun",
            MovieFileName = "ghz1_fullrun.bk2",
            PhysicsSha256 =
                "dd0a03bfddefa9570d4b49ee2d4ea5e35e2b8141147e17ab482a3654d3"
                + "11cb66",
            AuxStateSha256 =
                "d0aa928a346eb4f5c0917cac26aff11363f00b3b35b4402e82fddf8b40"
                + "4925e3",
            Bk2FrameOffset = 840,
            TraceFrameCount = 3905,
            MovieFrameCount = 4806
        };

        private static readonly CanonicalTrace Mz1 = new CanonicalTrace
        {
            CaseName = "MZ1",
            FixtureDirectoryName = "mz1_fullrun",
            MovieFileName = "s1-mz1.bk2",
            PhysicsSha256 =
                "25f511e6470e60c5769897161755d0634f9c9f02b2df9c164dcbef7b6c"
                + "1828f3",
            AuxStateSha256 =
                "fc0e6c1f35e15af87b51215b66df5a05d782eda7a1d9462d0e6e032784"
                + "ad071b",
            Bk2FrameOffset = 1075,
            TraceFrameCount = 7936,
            MovieFrameCount = 9012
        };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1TraceDifferential native capture matches canonical GHZ1"
                + " trace",
                () => NativeCaptureMatchesCanonicalTrace(Ghz1),
                game: "s1",
                movie: "ghz1_fullrun",
                kind: TestKind.Gate,
                estimatedSeconds: 2.0));
            tests.Add(new TestMain.TestCase(
                "S1TraceDifferential native capture matches canonical MZ1"
                + " trace",
                () => NativeCaptureMatchesCanonicalTrace(Mz1),
                game: "s1",
                movie: "s1-mz1",
                kind: TestKind.Gate,
                estimatedSeconds: 4.0));
        }

        private static void NativeCaptureMatchesCanonicalTrace(
            CanonicalTrace trace)
        {
            EndToEndTests.EndToEndDependencies dependencies =
                EndToEndTests.ResolveEndToEndDependencies(
                    Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                    Environment.GetEnvironmentVariable("BIZHAWK_HOME"),
                    Path.Combine(
                        EndToEndTests.RepositoryRoot,
                        "docs",
                        "BizHawk-2.11-linux-x64"),
                    path => RomIdentity.ValidateSonic1Rev01(
                        File.ReadAllBytes(path)),
                    path => BizHawkInstallation.Validate(path));
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            string traceDirectory = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s1",
                trace.FixtureDirectoryName);
            string moviePath = Path.Combine(
                traceDirectory,
                trace.MovieFileName);
            AssertEx.Equal(
                trace.PhysicsSha256,
                ComputeFixtureSha256(
                    Path.Combine(traceDirectory, "physics.csv")));
            AssertEx.Equal(
                trace.AuxStateSha256,
                ComputeFixtureSha256(
                    Path.Combine(traceDirectory, "aux_state.jsonl")));

            string root = TestScratch.CreateRootPath(
                "openggf-trace-differential");
            string output = Path.Combine(root, "capture");
            try
            {
                string stdout = RunTraceCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output);

                AssertEx.Equal(
                    ExpectedStdout(installation, trace, output),
                    stdout);
                AssertEx.Equal(
                    trace.PhysicsSha256,
                    EndToEndTests.ComputeSha256(
                        Path.Combine(output, "physics.csv")));
                AssertEx.Equal(
                    trace.AuxStateSha256,
                    EndToEndTests.ComputeSha256(
                        Path.Combine(output, "aux_state.jsonl")));
                AssertDateNormalizedMetadataEquality(
                    Path.Combine(traceDirectory, "metadata.json"),
                    Path.Combine(output, "metadata.json"));
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        /// <summary>
        /// Hashes a canonical fixture file that may be stored plain or only
        /// gzip-compressed (mz1_fullrun ships physics.csv.gz and
        /// aux_state.jsonl.gz without plain copies). The plain file wins when
        /// present; otherwise the .gz sibling is decompressed in memory and
        /// the decompressed bytes are hashed. The fixture itself is never
        /// written to.
        /// </summary>
        private static string ComputeFixtureSha256(string plainPath)
        {
            if (File.Exists(plainPath))
            {
                return EndToEndTests.ComputeSha256(plainPath);
            }
            string gzipPath = plainPath + ".gz";
            if (!File.Exists(gzipPath))
            {
                throw new InvalidOperationException(
                    "Fixture file missing (plain and .gz): " + plainPath);
            }
            using (FileStream compressed = File.OpenRead(gzipPath))
            using (var gzip = new GZipStream(
                compressed,
                CompressionMode.Decompress))
            using (var sha = System.Security.Cryptography.SHA256.Create())
            {
                byte[] hash = sha.ComputeHash(gzip);
                return BitConverter
                    .ToString(hash)
                    .Replace("-", string.Empty)
                    .ToLowerInvariant();
            }
        }

        private static string RunTraceCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output)
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments =
                    EndToEndTests.Quote(
                        Path.Combine(EndToEndTests.ToolDirectory, "run.sh"))
                    + " --mode trace"
                    + EndToEndTests.NoCompressArgument
                    + " --rom " + EndToEndTests.Quote(romPath)
                    + " --movie " + EndToEndTests.Quote(moviePath)
                    + " --output " + EndToEndTests.Quote(output),
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            start.EnvironmentVariables["BIZHAWK_HOME"] = bizHawkHome;
            start.EnvironmentVariables["DISPLAY"] = ":99";
            EndToEndTests.ProcessResult result = EndToEndTests.RunProcess(
                start,
                CaptureTimeoutMilliseconds);
            if (result.ExitCode != 0)
            {
                throw new InvalidOperationException(
                    "Trace capture exited " + result.ExitCode + ". stderr: "
                    + result.StandardError);
            }
            AssertEx.Equal(string.Empty, result.StandardError);
            return result.StandardOutput;
        }

        private static string ExpectedStdout(
            BizHawkInstallation installation,
            CanonicalTrace trace,
            string output)
        {
            return
                "BizHawk: " + installation.ManagedVersion + "\n"
                + "ROM SHA-1: " + RomIdentity.Sonic1Rev01Sha1 + "\n"
                + "Movie frames: " + trace.MovieFrameCount + "\n"
                + "BK2 frame offset: " + trace.Bk2FrameOffset + "\n"
                + "Trace frames: " + trace.TraceFrameCount + "\n"
                + "Physics CSV: "
                + Path.Combine(output, "physics.csv") + "\n"
                + "Aux state JSONL: "
                + Path.Combine(output, "aux_state.jsonl") + "\n"
                + "Metadata JSON: "
                + Path.Combine(output, "metadata.json") + "\n";
        }

        /// <summary>
        /// Asserts the produced metadata.json is byte-identical to the
        /// fixture's except for the recording_date value, which is the only
        /// permitted normalization: that one line must still carry the exact
        /// key formatting and an ISO date value.
        /// </summary>
        private static void AssertDateNormalizedMetadataEquality(
            string fixturePath,
            string producedPath)
        {
            string fixtureText = File.ReadAllText(fixturePath);
            string producedText = File.ReadAllText(producedPath);
            AssertEx.Equal(false, fixtureText.IndexOf('\r') >= 0);
            AssertEx.Equal(false, producedText.IndexOf('\r') >= 0);
            AssertEx.Equal(true, fixtureText.EndsWith("\n"));
            AssertEx.Equal(true, producedText.EndsWith("\n"));

            string[] fixtureLines = fixtureText.Split('\n');
            string[] producedLines = producedText.Split('\n');
            AssertEx.Equal(fixtureLines.Length, producedLines.Length);
            var recordingDateLines = 0;
            for (var index = 0; index < fixtureLines.Length; index++)
            {
                if (fixtureLines[index].StartsWith(
                    RecordingDateLinePrefix,
                    StringComparison.Ordinal))
                {
                    recordingDateLines++;
                    if (!RecordingDateLine.IsMatch(producedLines[index]))
                    {
                        throw new InvalidOperationException(
                            "Produced recording_date line is malformed: <"
                            + producedLines[index] + ">.");
                    }
                }
                else
                {
                    AssertEx.Equal(
                        fixtureLines[index],
                        producedLines[index]);
                }
            }
            AssertEx.Equal(1, recordingDateLines);
        }
    }
}
