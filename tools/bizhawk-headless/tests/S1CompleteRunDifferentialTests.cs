using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Text;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Differential gate proving one native complete-run capture of the
    /// canonical S1 playthrough movie
    /// (src/test/resources/traces/s1/_movies/s1-complete-run.bk2, 195,493
    /// input rows) reproduces all 19 *_completerun fixture directories
    /// byte-for-byte. A single --trace-profile complete_run CLI pass is
    /// captured into a temp directory, then every segment's physics.csv and
    /// aux_state.jsonl sha256 is asserted against the canonical (gunzipped)
    /// fixture bytes and each metadata.json is compared with exactly two
    /// normalizations: the recording_date value and the fixture's
    /// lua_script_version "3.14" line, which the native port must produce
    /// as the current recorder version "3.18"
    /// (docs/s1-complete-run-behavior.md section 2 verified, via the Lua's
    /// version-bump commit diffs, that the stage-free level path's output
    /// bytes are otherwise identical between the two stamps). The output
    /// root must contain exactly the 19 segment directories and no
    /// run_manifest.json: the stage-free movie takes no giant-ring detour
    /// and no --run-id is supplied, so the Lua manifest gate stays closed;
    /// the 8 credits_* fixture dirs come from a different (stable-retro)
    /// pipeline and are never emitted by this recorder (section 0). Two
    /// segment dir tokens differ from their fixture dir names because the
    /// ROM encodes SBZ3 as LZ act 4 and Final Zone as SBZ act 3 — the
    /// fixture dirs were renamed by hand, the recorder tokens were not
    /// (section 4.3). Skips (does not pass) when S1_ROM_PATH or a BizHawk
    /// distribution is absent; fails (does not skip) on any mismatch.
    /// </summary>
    internal static class S1CompleteRunDifferentialTests
    {
        // One movie pass records 194,035 emulated frames; allow well past
        // the single-level gates' 10-minute budget.
        private const int CaptureTimeoutMilliseconds = 3600000;
        private const string MovieFileName = "s1-complete-run.bk2";
        private const int MovieFrameCount = 195493;
        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");
        private const string FixtureLuaScriptVersionLine =
            "  \"lua_script_version\": \"3.18\",";
        private const string ProducedLuaScriptVersionLine =
            "  \"lua_script_version\": \"3.18\",";

        /// <summary>
        /// The 19 canonical segments in recorder emission order. DirToken
        /// is the directory the recorder writes under the output root;
        /// FixtureDirectoryName is the hand-renamed committed fixture dir
        /// under src/test/resources/traces/s1/ holding that segment's
        /// canonical bytes.
        /// </summary>
        private static readonly S1CompleteRunSegmentCase[] SegmentCases =
        {
            new S1CompleteRunSegmentCase(
                "ghz1", "ghz1_completerun", 788, 5598,
                "40d2e2ac75a539dc941224ddd3f5655a0db78350694ca223a2124e8a"
                + "a844db9d",
                "0ba6542eeff84203627364bfbe51877690d45a89ed61142108d88397"
                + "2a603541"),
            new S1CompleteRunSegmentCase(
                "ghz2", "ghz2_completerun", 6622, 4028,
                "79149ba8dd59b8e5fb53db79397aef768ed0bbfa9ae97d3c859af145"
                + "31cadc3c",
                "1f66d9430cc24f16408c980919839a36971ed136ff1c01dd679045be"
                + "22308b2e"),
            new S1CompleteRunSegmentCase(
                "ghz3", "ghz3_completerun", 10885, 9678,
                "345f4fc7a098da1917fea4f5d0ae0fdfb37b93e24818c1e625d2b580"
                + "9f89251c",
                "66aed6de83a529a112716eac992ed9b764a7d90bb9be1650b82a16ef"
                + "9f221746"),
            new S1CompleteRunSegmentCase(
                "mz1", "mz1_completerun", 20791, 8060,
                "f9e0d8966bb163296ad701cfbdaab8282a08fbce004e0c507b259703"
                + "6dfac190",
                "27823d2f1bade8574afd707bce5f887ce2c851b476c778767c89c7cd"
                + "7a499483"),
            new S1CompleteRunSegmentCase(
                "mz2", "mz2_completerun", 29079, 14136,
                "e01cd86ac397900878691b4d252a85c324170609fc3a5f03fec669f2"
                + "87e52513",
                "1cc631b03626d938a5a64b815489592872232293a6caebc7487f555d"
                + "11818cc9"),
            new S1CompleteRunSegmentCase(
                "mz3", "mz3_completerun", 43443, 17875,
                "d88401a2078bd2d1676840035aa805a44baa9179cd81542158e721eb"
                + "d057b9e7",
                "8aed295ff3efb95c24376f0728a63b0c3ec5f5e9c0cda2594358e16f"
                + "490102cf"),
            new S1CompleteRunSegmentCase(
                "syz1", "syz1_completerun", 61548, 9729,
                "4dbc064b999e77120f525b0dc35b39ee7ee8d44aa41d8a5204b8fbb1"
                + "f5718410",
                "f59b929658d80977b4c73d9ae09ae039d7361dd1fb9fb53c21c2e114"
                + "fd63a467"),
            new S1CompleteRunSegmentCase(
                "syz2", "syz2_completerun", 71507, 7994,
                "f9d2bf0a61d41252343c01f7aa0dc246478c0cdccaaaaaa6005a378f"
                + "42f48e37",
                "a4a24215cfcd643219cc82998f0fa952b541f2d83721fcab2ee8eb0b"
                + "1076d831"),
            new S1CompleteRunSegmentCase(
                "syz3", "syz3_completerun", 79731, 13710,
                "943399846809a71e78b3bc2a0877d7598e5d765086a58b2396985ca1"
                + "d7260c4e",
                "8292ac8df3f3a9514c239cceac3c56448c2244b0f6a7a1f36681ab0c"
                + "1f61c186"),
            new S1CompleteRunSegmentCase(
                "lz1", "lz1_completerun", 93657, 13070,
                "9d3629f712303bdd6acb1c68ba950c808f448e53b9e6569de2259876"
                + "6b07ce69",
                "ad1e0e11fafb30134389fcb4feb830426cb9cebedd40456db64148b7"
                + "2d5cf86f"),
            new S1CompleteRunSegmentCase(
                "lz2", "lz2_completerun", 106944, 10173,
                "4bfd9803eaf04d547dc2acdbaa3fb4d700aa569fab514d848dcf1431"
                + "db83437f",
                "5e68bb59235ace7ee11e4b29cc52ed8a32ea48e09f1ea2492985d6d7"
                + "69f18aa1"),
            new S1CompleteRunSegmentCase(
                "lz3", "lz3_completerun", 117333, 19107,
                "d213718029dfb62bbdc34dafe682bf4aba85e2cef1f6689a9f53e5af"
                + "bd39631b",
                "c4d8f0e949116f5eabde5e943c6256c388ffcbede0a9e262134984d0"
                + "a3e3725d"),
            new S1CompleteRunSegmentCase(
                "slz1", "slz1_completerun", 136660, 6411,
                "2ba66f8646d405d339f8c3c7991d7e1736df9e85d2f030164717ef9e"
                + "127be27e",
                "cc1415296fa4027b712b4bc41b35668fe5774b00497f795761e6c151"
                + "0c116b0b"),
            new S1CompleteRunSegmentCase(
                "slz2", "slz2_completerun", 143290, 5894,
                "36ee97714a0e7fe006cc38b5068d2169e74730bdfac5a2bf92280aac"
                + "548a8fc1",
                "49b75242b4d7f02727c6d704b6c2fd4f72bc0f2c1d47abf553157ad1"
                + "9c5b0824"),
            new S1CompleteRunSegmentCase(
                "slz3", "slz3_completerun", 149403, 13732,
                "06352e938f19dccd3146cc003ca44fc6f8484cab94424605be049138"
                + "d09780db",
                "d3c67e9276d49fcd884d88dd627f0af79b750dc0b6ac5b14ebfb08d2"
                + "33106751"),
            new S1CompleteRunSegmentCase(
                "sbz1", "sbz1_completerun", 163354, 7619,
                "1f19c437badaa7f2d486be81aa7a035c7284855f6f07a71e9991ae32"
                + "904fcdaf",
                "4b08ee428c8397f4125562df91378a87232e1292a595d8be2632fc9b"
                + "f7ab587a"),
            new S1CompleteRunSegmentCase(
                "sbz2", "sbz2_completerun", 171193, 9594,
                "65d0e950638d5763306a9fc1ce4cf7589dd51ec654fee9a11c8c15a3"
                + "5a75d0b3",
                "65f55d668398e34addc72e22885a074d4ac96e6c8e78eb0dd3a2afb8"
                + "d8711765"),
            // SBZ3 is ROM-encoded as LZ act 4: the recorder writes lz4/.
            new S1CompleteRunSegmentCase(
                "lz4", "sbz3_completerun", 181004, 8354,
                "de974fffac8682fd26863360dd1dde28c8e27a3497adfb042991e6b3"
                + "55916ea7",
                "41fdbfdbd646a6d949ce39e734ce988b576abf13414a081e89eb636a"
                + "ba2347d4"),
            // Final Zone is ROM-encoded as SBZ act 3: the recorder writes
            // sbz3/.
            new S1CompleteRunSegmentCase(
                "sbz3", "fz_completerun", 189578, 4457,
                "e314dd4a948a10adc3caaa16518fff9a66a2b3ff5bf76f4bcfdb1a3c"
                + "35815c09",
                "e3a1b45e68a315733389ae88f44c9eb8c80f88170ecb6970e2dc4797"
                + "5953e69d")
        };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1CompleteRunDifferential native capture matches all 19"
                + " canonical complete-run segments",
                NativeCaptureMatchesCanonicalCompleteRun,
                game: "s1",
                movie: "s1-complete-run",
                kind: TestKind.Gate,
                estimatedSeconds: 90.0));
        }

        private sealed class S1CompleteRunSegmentCase
        {
            public S1CompleteRunSegmentCase(
                string dirToken,
                string fixtureDirectoryName,
                int bk2FrameOffset,
                int traceFrameCount,
                string physicsSha256,
                string auxStateSha256)
            {
                DirToken = dirToken;
                FixtureDirectoryName = fixtureDirectoryName;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                PhysicsSha256 = physicsSha256;
                AuxStateSha256 = auxStateSha256;
            }

            public string DirToken { get; private set; }
            public string FixtureDirectoryName { get; private set; }
            public int Bk2FrameOffset { get; private set; }
            public int TraceFrameCount { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public string AuxStateSha256 { get; private set; }
        }

        private static void NativeCaptureMatchesCanonicalCompleteRun()
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

            string tracesRoot = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s1");
            string moviePath = Path.Combine(
                tracesRoot,
                "_movies",
                MovieFileName);

            string root = TestScratch.CreateRootPath(
                "openggf-s1-completerun-differential");
            string output = Path.Combine(root, "capture");
            try
            {
                // Self-check the canonical fixture bytes first so a hash
                // mismatch reports whether the fixture or the capture
                // diverged. Gzipped fixtures are decompressed read-only
                // into the temp root; the fixtures are never modified.
                Directory.CreateDirectory(root);
                foreach (S1CompleteRunSegmentCase segment in SegmentCases)
                {
                    string fixtureDirectory = Path.Combine(
                        tracesRoot,
                        segment.FixtureDirectoryName);
                    string scratch = Path.Combine(
                        root,
                        "fixture-" + segment.FixtureDirectoryName);
                    Directory.CreateDirectory(scratch);
                    AssertSha256(
                        segment.FixtureDirectoryName
                        + "/physics.csv (fixture)",
                        segment.PhysicsSha256,
                        EndToEndTests.ComputeSha256(MaterializeFixture(
                            fixtureDirectory,
                            "physics.csv",
                            Path.Combine(scratch, "physics.csv"))));
                    AssertSha256(
                        segment.FixtureDirectoryName
                        + "/aux_state.jsonl (fixture)",
                        segment.AuxStateSha256,
                        EndToEndTests.ComputeSha256(MaterializeFixture(
                            fixtureDirectory,
                            "aux_state.jsonl",
                            Path.Combine(scratch, "aux_state.jsonl"))));
                }

                string stdout = RunCompleteRunCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output);

                AssertEx.Equal(
                    ExpectedStdout(installation, output),
                    stdout);
                foreach (S1CompleteRunSegmentCase segment in SegmentCases)
                {
                    AssertSha256(
                        segment.DirToken + "/physics.csv",
                        segment.PhysicsSha256,
                        EndToEndTests.ComputeSha256(Path.Combine(
                            output,
                            segment.DirToken,
                            "physics.csv")));
                    AssertSha256(
                        segment.DirToken + "/aux_state.jsonl",
                        segment.AuxStateSha256,
                        EndToEndTests.ComputeSha256(Path.Combine(
                            output,
                            segment.DirToken,
                            "aux_state.jsonl")));
                    AssertNormalizedMetadataEquality(
                        Path.Combine(
                            tracesRoot,
                            segment.FixtureDirectoryName,
                            "metadata.json"),
                        Path.Combine(
                            output,
                            segment.DirToken,
                            "metadata.json"));
                }
                AssertOutputLayoutIsExactlyTheSegments(output);
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
        /// The stage-free pass must publish exactly the 19 segment
        /// directories (three files each) plus the mandatory audit
        /// run_manifest.json, with no credits/endz directories or other
        /// stray root files.
        /// </summary>
        private static void AssertOutputLayoutIsExactlyTheSegments(
            string output)
        {
            AssertEx.Equal(
                true,
                File.Exists(Path.Combine(output, "run_manifest.json")));
            AssertEx.Equal(
                1,
                Directory.GetFiles(output).Length);

            var expectedDirectories = new List<string>();
            foreach (S1CompleteRunSegmentCase segment in SegmentCases)
            {
                expectedDirectories.Add(segment.DirToken);
            }
            expectedDirectories.Sort(StringComparer.Ordinal);

            var actualDirectories = new List<string>();
            foreach (string path in Directory.GetDirectories(output))
            {
                actualDirectories.Add(Path.GetFileName(path));
            }
            actualDirectories.Sort(StringComparer.Ordinal);

            AssertEx.Equal(
                string.Join("\n", expectedDirectories.ToArray()),
                string.Join("\n", actualDirectories.ToArray()));

            foreach (string dirToken in actualDirectories)
            {
                var actualFiles = new List<string>();
                foreach (string path in Directory.GetFiles(
                    Path.Combine(output, dirToken)))
                {
                    actualFiles.Add(Path.GetFileName(path));
                }
                actualFiles.Sort(StringComparer.Ordinal);
                AssertEx.Equal(
                    "aux_state.jsonl\nmetadata.json\nphysics.csv",
                    string.Join("\n", actualFiles.ToArray()));
                AssertEx.Equal(
                    0,
                    Directory.GetDirectories(
                        Path.Combine(output, dirToken)).Length);
            }
        }

        /// <summary>
        /// Returns a readable path holding the canonical bytes of the
        /// named fixture file: the plain file when it exists, otherwise
        /// its .gz sibling decompressed to the given scratch path.
        /// </summary>
        private static string MaterializeFixture(
            string traceDirectory,
            string fileName,
            string scratchPath)
        {
            string plainPath = Path.Combine(traceDirectory, fileName);
            if (File.Exists(plainPath))
            {
                return plainPath;
            }
            Gunzip(plainPath + ".gz", scratchPath);
            return scratchPath;
        }

        private static string RunCompleteRunCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output)
        {
            string arguments =
                EndToEndTests.Quote(
                    Path.Combine(EndToEndTests.ToolDirectory, "run.sh"))
                + " --mode trace"
                + EndToEndTests.NoCompressArgument
                + " --rom " + EndToEndTests.Quote(romPath)
                + " --movie " + EndToEndTests.Quote(moviePath)
                + " --output " + EndToEndTests.Quote(output)
                + " --trace-profile "
                + S1RunCaptureRunner.LevelTraceProfile;
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments = arguments,
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
                    "Complete-run capture exited " + result.ExitCode
                    + ". stderr: " + result.StandardError);
            }
            AssertEx.Equal(string.Empty, result.StandardError);
            return result.StandardOutput;
        }

        private static string ExpectedStdout(
            BizHawkInstallation installation,
            string output)
        {
            var expected = new StringBuilder();
            expected.Append("BizHawk: ")
                .Append(installation.ManagedVersion).Append('\n');
            expected.Append("ROM SHA-1: ")
                .Append(RomIdentity.Sonic1Rev01Sha1).Append('\n');
            expected.Append("Movie frames: ")
                .Append(MovieFrameCount).Append('\n');
            expected.Append("Trace profile: ")
                .Append(S1RunCaptureRunner.LevelTraceProfile).Append('\n');
            expected.Append("Segments: ")
                .Append(SegmentCases.Length).Append('\n');
            expected.Append("Transitions: 0\n");
            foreach (S1CompleteRunSegmentCase segment in SegmentCases)
            {
                expected.Append("Segment ").Append(segment.DirToken)
                    .Append(": kind=level, BK2 frame offset=")
                    .Append(segment.Bk2FrameOffset)
                    .Append(", trace frames=")
                    .Append(segment.TraceFrameCount)
                    .Append('\n');
            }
            expected.Append("Run manifest: ")
                .Append(Path.Combine(output, "run_manifest.json"))
                .Append('\n');
            return expected.ToString();
        }

        /// <summary>
        /// First-divergence hash assertion: names the diverging file so a
        /// gate failure reports which of the canonical byte sets broke
        /// first.
        /// </summary>
        private static void AssertSha256(
            string context,
            string expected,
            string actual)
        {
            if (expected != actual)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context + ": expected sha256 <"
                    + expected + "> but was <" + actual + ">.");
            }
        }

        /// <summary>
        /// Asserts the produced metadata.json is byte-identical to the
        /// fixture's except for the two permitted normalizations: the
        /// recording_date value (which must still carry the exact key
        /// formatting and an ISO date value), and the fixture's
        /// lua_script_version "3.14" line, which the native port must
        /// produce as exactly "3.18" (the byte-compat of every other
        /// level-path output byte across those stamps is verified in
        /// docs/s1-complete-run-behavior.md section 2). Both files must be
        /// LF-only: this fixture set carries no CRLF expansion
        /// (docs/s1-complete-run-behavior.md section 8).
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
    }
}
