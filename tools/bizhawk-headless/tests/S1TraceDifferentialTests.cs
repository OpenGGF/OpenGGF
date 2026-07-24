using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Differential gate proving the native trace capture reproduces the Lua
    /// recorder byte-for-byte against the canonical GHZ1 fixture
    /// (src/test/resources/traces/s1/ghz1_fullrun/). Runs the trace-mode CLI
    /// end-to-end through run.sh and asserts the canonical physics.csv and
    /// aux_state.jsonl sha256 hashes, the detected BK2 frame offset of 840,
    /// and metadata.json equality normalized only on the recording_date
    /// value. Skips (does not pass) when S1_ROM_PATH or a BizHawk
    /// distribution is absent; fails (does not skip) on any hash mismatch.
    /// </summary>
    internal static class S1TraceDifferentialTests
    {
        private const string CanonicalPhysicsSha256 =
            "dd0a03bfddefa9570d4b49ee2d4ea5e35e2b8141147e17ab482a3654d311cb66";
        private const string CanonicalAuxStateSha256 =
            "026794b175c7fea65491f57cbf5a83684f183b802c7fabaa15eb699e82184a86";
        private const int CanonicalBk2FrameOffset = 840;
        private const int CanonicalTraceFrameCount = 3905;
        private const int CanonicalMovieFrameCount = 4806;
        private const int CaptureTimeoutMilliseconds = 600000;
        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1TraceDifferential native capture matches canonical GHZ1"
                + " trace",
                NativeCaptureMatchesCanonicalGhz1Trace));
        }

        private static void NativeCaptureMatchesCanonicalGhz1Trace()
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
                "ghz1_fullrun");
            string moviePath = Path.Combine(
                traceDirectory,
                "ghz1_fullrun.bk2");
            AssertEx.Equal(
                CanonicalPhysicsSha256,
                EndToEndTests.ComputeSha256(
                    Path.Combine(traceDirectory, "physics.csv")));
            AssertEx.Equal(
                CanonicalAuxStateSha256,
                EndToEndTests.ComputeSha256(
                    Path.Combine(traceDirectory, "aux_state.jsonl")));

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-trace-differential-" + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                string stdout = RunTraceCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output);

                AssertEx.Equal(
                    ExpectedStdout(installation, output),
                    stdout);
                AssertEx.Equal(
                    CanonicalPhysicsSha256,
                    EndToEndTests.ComputeSha256(
                        Path.Combine(output, "physics.csv")));
                AssertEx.Equal(
                    CanonicalAuxStateSha256,
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
            string output)
        {
            return
                "BizHawk: " + installation.ManagedVersion + "\n"
                + "ROM SHA-1: " + RomIdentity.Sonic1Rev01Sha1 + "\n"
                + "Movie frames: " + CanonicalMovieFrameCount + "\n"
                + "BK2 frame offset: " + CanonicalBk2FrameOffset + "\n"
                + "Trace frames: " + CanonicalTraceFrameCount + "\n"
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
