using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Differential gate proving the native S2 plain trace capture
    /// (default profile gameplay_unlock, segment 0) reproduces the Lua
    /// recorder byte-for-byte against the canonical EHZ1 fixture
    /// (src/test/resources/traces/s2/ehz1_fullrun/). Runs the trace-mode
    /// CLI end-to-end through run.sh with only --mode trace (game
    /// auto-detected from the S2 ROM) and asserts the canonical
    /// physics.csv and aux_state.jsonl sha256 hashes (the gzipped fixture
    /// aux is decompressed to a temp file first; the fixture itself is
    /// never touched), the detected BK2 frame offset of 899, and
    /// metadata.json equality normalized only on the recording_date value
    /// and the fixture's lua_script_version "9.11-s2" being produced as
    /// "9.12-s2" (the v9.12 Lua header declares plain-mode output
    /// byte-identical to 9.11-s2 except that string). Skips (does not
    /// pass) when S2_ROM_PATH or a BizHawk distribution is absent; fails
    /// (does not skip) on any hash mismatch.
    /// </summary>
    internal static class S2TraceDifferentialTests
    {
        private const string CanonicalPhysicsSha256 =
            "efeb90112d36f897317f688881140c042792a2b640cf8313470216db91f57a83";
        private const string CanonicalAuxStateSha256 =
            "5522e70caa8134570eb5acdcfc3c188655d929b2e777101ae70785168e122dc2";
        private const int CanonicalBk2FrameOffset = 899;
        private const int CanonicalTraceFrameCount = 5852;
        private const int CanonicalMovieFrameCount = 6778;
        private const int CaptureTimeoutMilliseconds = 600000;
        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");
        private const string FixtureLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.11-s2\",";
        private const string ProducedLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.12-s2\",";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native capture matches canonical EHZ1"
                + " trace",
                NativeCaptureMatchesCanonicalEhz1Trace));
        }

        private static void NativeCaptureMatchesCanonicalEhz1Trace()
        {
            S2DifferentialDependencies dependencies =
                ResolveS2DifferentialDependencies();
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            string traceDirectory = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s2",
                "ehz1_fullrun");
            string moviePath = Path.Combine(
                traceDirectory,
                "s2-ehz1.bk2");

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-s2-trace-differential-"
                + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                // The fixture's aux_state.jsonl ships gzipped; decompress
                // it read-only into the temp root so the canonical hash is
                // asserted against the exact bytes the Lua recorder wrote.
                Directory.CreateDirectory(root);
                string fixtureAuxPath = Path.Combine(
                    root,
                    "fixture-aux_state.jsonl");
                Gunzip(
                    Path.Combine(traceDirectory, "aux_state.jsonl.gz"),
                    fixtureAuxPath);
                AssertEx.Equal(
                    CanonicalPhysicsSha256,
                    EndToEndTests.ComputeSha256(
                        Path.Combine(traceDirectory, "physics.csv")));
                AssertEx.Equal(
                    CanonicalAuxStateSha256,
                    EndToEndTests.ComputeSha256(fixtureAuxPath));

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
                AssertNormalizedMetadataEquality(
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
        /// Resolves the S2 ROM (S2_ROM_PATH) and BizHawk installation
        /// (BIZHAWK_HOME, falling back to the repository's
        /// docs/BizHawk-2.11-linux-x64) with the S1 differential gate's
        /// semantics: absent inputs skip, present-but-invalid inputs fail.
        /// </summary>
        private static S2DifferentialDependencies
            ResolveS2DifferentialDependencies()
        {
            string suppliedRomPath =
                Environment.GetEnvironmentVariable("S2_ROM_PATH");
            string suppliedBizHawkHome =
                Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            string fallbackBizHawkHome = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "docs",
                "BizHawk-2.11-linux-x64");

            var missing = new List<string>();
            string romPath = null;
            if (string.IsNullOrEmpty(suppliedRomPath))
            {
                missing.Add("S2_ROM_PATH is not set");
            }
            else
            {
                romPath = Path.GetFullPath(suppliedRomPath);
            }

            string bizHawkHome = null;
            if (!string.IsNullOrEmpty(suppliedBizHawkHome))
            {
                bizHawkHome = Path.GetFullPath(suppliedBizHawkHome);
            }
            else if (Directory.Exists(fallbackBizHawkHome))
            {
                bizHawkHome = Path.GetFullPath(fallbackBizHawkHome);
            }
            else
            {
                missing.Add("BizHawk distribution is not installed");
            }

            if (missing.Count != 0)
            {
                throw new TestMain.SkipTestException(
                    string.Join("; ", missing.ToArray()));
            }

            // Present inputs are validated, not skipped over.
            if (!File.Exists(romPath))
            {
                throw new InvalidOperationException(
                    "Supplied S2_ROM_PATH does not exist: " + romPath + ".");
            }
            RomIdentity.ValidateSonic2Rev01(File.ReadAllBytes(romPath));
            BizHawkInstallation.Validate(bizHawkHome);
            return new S2DifferentialDependencies(romPath, bizHawkHome);
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
                + "ROM SHA-1: " + RomIdentity.Sonic2Rev01Sha1 + "\n"
                + "Movie frames: " + CanonicalMovieFrameCount + "\n"
                + "Trace profile: gameplay_unlock\n"
                + "Gameplay segment: 0\n"
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
        /// fixture's except for the two permitted normalizations: the
        /// recording_date value (which must still carry the exact key
        /// formatting and an ISO date value), and the fixture's
        /// lua_script_version "9.11-s2" line, which the native port must
        /// produce as exactly "9.12-s2".
        /// </summary>
        private static void AssertNormalizedMetadataEquality(
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
            var luaScriptVersionLines = 0;
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
                else if (fixtureLines[index] == FixtureLuaScriptVersionLine)
                {
                    luaScriptVersionLines++;
                    AssertEx.Equal(
                        ProducedLuaScriptVersionLine,
                        producedLines[index]);
                }
                else
                {
                    AssertEx.Equal(
                        fixtureLines[index],
                        producedLines[index]);
                }
            }
            AssertEx.Equal(1, recordingDateLines);
            AssertEx.Equal(1, luaScriptVersionLines);
        }

        private static void Gunzip(string sourcePath, string destinationPath)
        {
            using (FileStream source = File.OpenRead(sourcePath))
            using (var gzip = new GZipStream(
                source,
                CompressionMode.Decompress))
            using (FileStream destination = File.Create(destinationPath))
            {
                gzip.CopyTo(destination);
            }
        }

        private sealed class S2DifferentialDependencies
        {
            public S2DifferentialDependencies(
                string romPath,
                string bizHawkHome)
            {
                RomPath = romPath;
                BizHawkHome = bizHawkHome;
            }

            public string RomPath { get; private set; }
            public string BizHawkHome { get; private set; }
        }
    }
}
