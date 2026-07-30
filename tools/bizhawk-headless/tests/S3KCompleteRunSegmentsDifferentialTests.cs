using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Differential gate proving ONE native complete-run capture of the
    /// canonical S3K playthrough movie
    /// (src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2,
    /// 466,334 input rows) reproduces all fifteen committed
    /// <c>*_completerun</c> fixture directories — identity (A) of
    /// docs/s3k-run-publication.md §0.1 — byte for byte.
    ///
    /// The pass is deliberately NOT truncated. The canonical capture ran
    /// the whole movie and publishes fifteen committed segments.
    /// Running to DDZ and asserting the full fifteen-line segment summary
    /// proves the segmenter's arm/finalize ordering stays correct on both
    /// sides of every fixture boundary — in particular that <c>mhz</c> ends
    /// at 28,156 rows because <c>fbz</c> arms at BK2 frame 237,913, not
    /// because the capture ran out of movie.
    ///
    /// Comparison strength, per fixture:
    /// - physics.csv and aux_state.jsonl by raw byte length AND sha256,
    ///   with ZERO normalization.
    /// - metadata.json line for line at exact
    ///   v6.38/schema-7/hardware-schema-2 identity; recording_date values
    ///   may differ. Later unapproved schema-one fixtures therefore remain
    ///   an explicit publication-boundary failure.
    ///   No other key or line may move, and the absence of a <c>run_id</c>
    ///   key is asserted explicitly.
    ///
    /// The first seven physics.csv hashes below were last moved by Lua
    /// 6.33-s3k-completerun, the ADDR_VBLA_WORD fix: vblank_counter reads
    /// 0xFE0E (the V_int_run_count low word) instead of 0xFE12
    /// (Life_count), turning column 6 from lives &lt;&lt; 8 into a live
    /// per-frame counter. Because the column is fixed-width every
    /// PhysicsLength below is unchanged, and every AuxStateSha256 is
    /// unchanged too — no aux field ever read that address. A gate that
    /// pinned only lengths would have missed the whole change, which is
    /// why both are pinned.
    ///
    /// The output root must hold exactly the fifteen segment directories,
    /// four files each, and NO run_manifest.json: the Sonic route takes no
    /// bonus/giant-ring detour and no --run-id is supplied, so the Lua's
    /// manifest gate (a disjunction of those two) stays closed. Pre-created
    /// dirs for unvisited zones are not published because the publisher
    /// stages files rather than directories (spec §1.4).
    ///
    /// Cost, measured: 5m57s wall, 235 MB peak RSS (the streaming segment
    /// sink keeps a 266 MB aux stream off the heap), 2.84 GB of scratch.
    /// That scratch is why the capture goes under the tool directory's
    /// .scratch/ rather than Path.GetTempPath(): /tmp is frequently a
    /// RAM-backed tmpfs, where 2.84 GB is both likely to ENOSPC and
    /// actively harmful. The tool directory sits beside the existing bin/
    /// and obj/ build scratch, is covered by the repository's tools/*
    /// ignore rule, and is deleted in a finally block.
    ///
    /// Skips (does not pass) when S3K_ROM_PATH, a BizHawk distribution, the
    /// movie or the fixture directories are absent; fails (does not skip)
    /// on any mismatch.
    /// </summary>
    internal static class S3KCompleteRunSegmentsDifferentialTests
    {
        // One movie pass emulates 466,334 frames and writes 2.84 GB in
        // about six minutes; allow a wide margin over that.
        private const int CaptureTimeoutMilliseconds = 3600000;
        private const string MovieFileName = "s3k-complete-sonic-tails.bk2";
        private const int MovieFrameCount = 466334;

        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");

        /// <summary>
        /// Exact published and current literals. Only this reviewed
        /// schema-one to schema-two migration is normalized.
        /// </summary>
        private const string PublishedLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.39-s3k-completerun\",";
        private const string PublishedDirectLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.39-s3k-completerun\",";
        private const string CurrentLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.39-s3k-completerun\",";
        private const string CurrentTraceSchemaLine =
            "  \"trace_schema\": 7,";
        private const string CurrentHardwareTimingSchemaLine =
            "  \"hardware_timing_schema\": 2,\n";
        private const string PublishedHardwareTimingSchemaLine =
            "  \"hardware_timing_schema\": 2,\n";
        private static readonly HashSet<string> DirectPredecessorSegments =
            new HashSet<string>(StringComparer.Ordinal)
            {
                "ddz", "dez", "ending", "fbz", "hpz", "lbz", "lrz",
                "mhz", "soz", "ssz"
            };

        /// <summary>
        /// Identity (A) carries no run_id key at all. Identities (B) and
        /// (C) do, so a port that started stamping one would still satisfy
        /// line-count equality only by displacing another key — this
        /// explicit probe names the failure instead.
        /// </summary>
        private const string RunIdLinePrefix = "  \"run_id\":";

        /// <summary>
        /// All fifteen segments the full pass publishes, in recorder
        /// emission order. Every row carries the semantic committed fixture
        /// directory plus canonical decompressed lengths and sha256s.
        /// </summary>
        private static readonly SegmentCase[] SegmentCases =
        {
            new SegmentCase(
                "aiz", "aiz_completerun", 941, 26228,
                4249570,
                "2f8d3d0c2f5a4b3f30b7784ed28fa37071951f6d8d538f08573b4631"
                + "fa33f872",
                184288496,
                "48d01a1d3cee368ac5be20a55cae94cbfc8f46ad9caf353adc4c6816"
                + "ab5d1d23"),
            new SegmentCase(
                "hcz", "hcz_completerun", 27170, 31482,
                5100718,
                "5d829f35729bb9254f272283dd078d3c6b259c771ca3d57eea3fb249"
                + "d7ed73c7",
                225206246,
                "d476cfca26294d7e8170f24e6b611a4566edb154d68b197c7f655eea"
                + "d43dfb9e"),
            new SegmentCase(
                "mgz", "mgz_completerun", 58653, 39398,
                6383110,
                "ddfcc9851a6c6b100e9366ebe9fccfecd9a99745639a8192f0f93e24"
                + "1879ae52",
                226795845,
                "cf447cbd6dca54ce25b993e19f32e91c279468b1c29353a1ab86ca82"
                + "80059ca6"),
            new SegmentCase(
                "cnz", "cnz_completerun", 98052, 40064,
                6491002,
                "2d1ba19a27d614c25ceb8962f7506552cc8b038cc3a36a00b08f4337"
                + "d329d404",
                230008602,
                "6a6c744975c1c040219d80e66e2634b962d9e49cf78e7969619b5991"
                + "76a60b8c"),
            new SegmentCase(
                "icz", "icz_completerun", 138117, 25393,
                4114300,
                "386cf6e8e62b61c8cd03c252668db47d3511fc1fd6c43399830e6655"
                + "086d0c99",
                185249490,
                "dbe441b24c35673e61531111ff24ec45eb88e7ed65f6a9eadb1fad30"
                + "271e593b"),
            new SegmentCase(
                "lbz", "lbz_completerun", 163511, 46244,
                7492162,
                "dba472735a28d1bb3235a4fe79ab6734202456f97bca6ca00cac2f5d"
                + "64c8a139",
                287296733,
                "c0d14b642e22b1b7f3d4b358ec9c1dcc48d01f39e6cc1c23e3c886c9"
                + "317a5b42"),
            new SegmentCase(
                "mhz", "mhz_completerun", 209756, 28156,
                4561906,
                "d502ee1305f363c448d5507aae54b732d851433713f809fdd79ce8cc"
                + "c21c9c03",
                197021051,
                "325138abc35130b5bab5e170fef4cd8bf7f263a79c3f68a126854099"
                + "b30fa7e2"),
            new SegmentCase(
                "fbz", "fbz_completerun", 237913, 44281,
                7174156,
                "337f7619d3b516cfb5c475aed978d023ba370e8fddb3473939c4b65f"
                + "d2fdd4ca",
                291595676,
                "15307f0d833156b9da8d2c3bb4b665ba7577fe7587aed05baceaf5bc"
                + "481288c1"),
            new SegmentCase(
                "soz", "soz_completerun", 282195, 59507,
                9640768,
                "d67337f964240e3ad06854b31e68eb723cc29174d490db15be27669b5"
                + "3f236bc",
                363005107,
                "1d1e1de21fe43ce81a259768b2e9500145a704e49a42cc5b63243a7"
                + "3848ec6fb"),
            new SegmentCase(
                "lrz", "lrz_completerun", 341703, 38755,
                6278944,
                "9c464a019b6fbdfb8086b3adcc47c5ee52d14ca13281b09cea5b648c"
                + "cf4a4fac",
                247949217,
                "02150b12577c5ccd14687fa34eade9b1aa252fdeda164df14fcda0f8"
                + "17c333c6"),
            new SegmentCase(
                "hpz22", "hpz_completerun", 380459, 16260,
                2634754,
                "a61cad6c194fc87393d4186a55bbb7d6d47465314f913a75f855d9b9"
                + "1c671f1b",
                110061568,
                "29c3cdf8e8d606a4e96d2db095633a00a6409705193b439936daff30"
                + "d738f925"),
            new SegmentCase(
                "hpz", "ssz_completerun", 396720, 18641,
                3020476,
                "cb7b1b368810000d8ef9e6927ebc721588ec446af0748429e0573e63"
                + "6789e6dc",
                112195352,
                "b2ca3b45f4d3b93e67d48e24a315ca77dfabf2ff3484a0daf5877e356"
                + "a21efa0"),
            new SegmentCase(
                "ssz", "dez_completerun", 415362, 44147,
                7152448,
                "16aa55471d830106abe26bec42c16cf0bc356f290de5e77a7ec9437d3"
                + "dbb42c2",
                268713958,
                "b38e3e0e97b313cf4628e972b9bdb6aaaf758930b293bb7008f1273d"
                + "8846509f"),
            new SegmentCase(
                "dez23", "ddz_completerun", 459510, 6103,
                989320,
                "697c5be5a6a52c395d10845625626f10d61a539582012d846cc88f98"
                + "319da150",
                40285969,
                "6d9a65e2b18ba52eba761efb56f3e70bc22e041cf42d4f7753718fd8"
                + "15fac357"),
            new SegmentCase(
                "ddz", "ending_completerun", 465614, 719,
                117112,
                "18233e2ca65529b4b34ba7c917689100fba0f57a8fba00b56858f757"
                + "e3830fff",
                2591747,
                "389edfd9460787f24344ca08736842524fac629d5dadf224d547ec84"
                + "6bbf3f1b")
        };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunSegmentsDifferential schema migration shapes"
                + " fail closed",
                MetadataMigrationShapesFailClosed));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunSegmentsDifferential native capture matches"
                + " all fifteen canonical completerun segments",
                NativeCaptureMatchesCanonicalCompleteRunSegments,
                game: "s3k",
                movie: "s3k-complete-sonic-tails",
                kind: TestKind.Gate,
                // 466,334 input rows: the suite's critical path. It has
                // to be the first thing a parallel run starts, which is
                // what the longest-first ordering is for.
                estimatedSeconds: 370.0));
        }

        private static void NativeCaptureMatchesCanonicalCompleteRunSegments()
        {
            string tracesRoot = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s3k");
            string moviePath =
                Path.Combine(tracesRoot, "_movies", MovieFileName);
            Dependencies dependencies = Resolve(tracesRoot, moviePath);
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            string root = Path.Combine(
                EndToEndTests.ToolDirectory,
                ".scratch",
                "s3k-completerun-segments-"
                + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                Directory.CreateDirectory(root);

                // Self-check the canonical bytes first, so a hash mismatch
                // reports whether the fixture or the capture moved. The
                // .gz fixtures are hashed by STREAMING the decompressed
                // bytes through SHA256 — never materialised — which keeps
                // the gate's scratch at the capture's own 2.84 GB instead
                // of 4.3 GB. Nothing under src/test/resources/traces/ is
                // opened for write.
                foreach (SegmentCase segment in SegmentCases)
                {
                    if (!segment.HasFixture)
                    {
                        continue;
                    }
                    string fixtureDirectory = Path.Combine(
                        tracesRoot, segment.FixtureDirectoryName);
                    AssertFixtureBytes(
                        segment.FixtureDirectoryName + "/physics.csv"
                        + " (fixture)",
                        Path.Combine(fixtureDirectory, "physics.csv"),
                        segment.PhysicsLength,
                        segment.PhysicsSha256);
                    AssertFixtureBytes(
                        segment.FixtureDirectoryName + "/aux_state.jsonl"
                        + " (fixture)",
                        Path.Combine(fixtureDirectory, "aux_state.jsonl"),
                        segment.AuxStateLength,
                        segment.AuxStateSha256);
                }

                string stdout = RunCompleteRunCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output);

                // The whole fifteen-segment summary, so a segmentation
                // regression anywhere in the movie is reported as a summary
                // diff before any byte comparison runs.
                AssertEx.Equal(ExpectedStdout(installation), stdout);

                foreach (SegmentCase segment in SegmentCases)
                {
                    if (!segment.HasFixture)
                    {
                        continue;
                    }
                    string produced =
                        Path.Combine(output, segment.DirToken);
                    AssertProducedBytes(
                        segment.DirToken + "/physics.csv",
                        Path.Combine(produced, "physics.csv"),
                        segment.PhysicsLength,
                        segment.PhysicsSha256);
                    AssertProducedBytes(
                        segment.DirToken + "/aux_state.jsonl",
                        Path.Combine(produced, "aux_state.jsonl"),
                        segment.AuxStateLength,
                        segment.AuxStateSha256);
                    AssertMetadataEqualExceptRecordingDate(
                        segment.DirToken,
                        Path.Combine(
                            tracesRoot,
                            segment.FixtureDirectoryName,
                            "metadata.json"),
                        Path.Combine(produced, "metadata.json"));
                    AssertEx.Equal(
                        File.ReadAllText(Path.Combine(
                            tracesRoot,
                            segment.FixtureDirectoryName,
                            "hardware_timing.jsonl")),
                        File.ReadAllText(Path.Combine(
                            produced, "hardware_timing.jsonl")));
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
        /// The pass must publish exactly the fifteen segment directories
        /// with four files each and nothing else — in particular no
        /// run_manifest.json, whose absence is the observable form of the
        /// Lua's closed manifest gate for a detour-free, run-id-free
        /// capture (spec §4.1).
        /// </summary>
        private static void AssertOutputLayoutIsExactlyTheSegments(
            string output)
        {
            AssertEx.Equal(
                false,
                File.Exists(Path.Combine(output, "run_manifest.json")));
            AssertEx.Equal(0, Directory.GetFiles(output).Length);

            var expectedDirectories = new List<string>();
            foreach (SegmentCase segment in SegmentCases)
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
                string directory = Path.Combine(output, dirToken);
                var actualFiles = new List<string>();
                foreach (string path in Directory.GetFiles(directory))
                {
                    actualFiles.Add(Path.GetFileName(path));
                }
                actualFiles.Sort(StringComparer.Ordinal);
                AssertEx.Equal(
                    "aux_state.jsonl\nhardware_timing.jsonl\nmetadata.json\nphysics.csv",
                    string.Join("\n", actualFiles.ToArray()));
                AssertEx.Equal(
                    0, Directory.GetDirectories(directory).Length);
            }
        }

        /// <summary>
        /// Requires exact v6.38/schema-7/hardware-schema-2 equality apart
        /// from recording_date. Both sides stay LF-only and carry no run_id.
        /// </summary>
        private static void AssertMetadataEqualExceptRecordingDate(
            string context, string fixturePath, string producedPath)
        {
            string fixtureText = File.ReadAllText(fixturePath);
            string producedText = File.ReadAllText(producedPath);
            AssertMetadataShape(
                context + " (fixture)", fixtureText);
            AssertMetadataShape(context, producedText);
            RequireExactMetadataMigrationShapes(
                context, fixtureText, producedText);
            bool directPredecessor =
                DirectPredecessorSegments.Contains(context);
            AssertEx.Equal(
                1,
                CountOccurrences(
                    producedText,
                    directPredecessor
                        ? PublishedDirectLuaScriptVersionLine
                        : PublishedLuaScriptVersionLine));
            AssertEx.Equal(
                1,
                CountOccurrences(
                    producedText,
                    directPredecessor
                        ? PublishedHardwareTimingSchemaLine
                        : CurrentHardwareTimingSchemaLine));
            AssertEx.Equal(
                1,
                CountOccurrences(
                    producedText, CurrentTraceSchemaLine));

            string[] fixtureLines = fixtureText.Split('\n');
            string[] producedLines = producedText.Split('\n');
            if (fixtureLines.Length != producedLines.Length)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context
                    + "/metadata.json: line count is "
                    + producedLines.Length + "; expected "
                    + fixtureLines.Length + ".");
            }

            var recordingDateLines = 0;
            var versionLines = 0;
            for (var index = 0; index < fixtureLines.Length; index++)
            {
                if (fixtureLines[index].StartsWith(
                    RecordingDateLinePrefix, StringComparison.Ordinal))
                {
                    recordingDateLines++;
                    if (!RecordingDateLine.IsMatch(producedLines[index]))
                    {
                        throw new InvalidOperationException(
                            "First divergence at " + context
                            + "/metadata.json line " + (index + 1)
                            + ": recording_date is malformed: <"
                            + producedLines[index] + ">.");
                    }
                    continue;
                }
                if (fixtureLines[index]
                    == (directPredecessor
                        ? PublishedDirectLuaScriptVersionLine
                        : PublishedLuaScriptVersionLine))
                {
                    versionLines++;
                }
                if (fixtureLines[index] != producedLines[index])
                {
                    throw new InvalidOperationException(
                        "First divergence at " + context
                        + "/metadata.json line " + (index + 1) + ": was <"
                        + producedLines[index] + ">; expected <"
                        + fixtureLines[index] + ">.");
                }
            }
            AssertEx.Equal(1, recordingDateLines);
            AssertEx.Equal(1, versionLines);
        }

        private static void MetadataMigrationShapesFailClosed()
        {
            string published = PublishedLuaScriptVersionLine + "\n"
                + CurrentTraceSchemaLine + "\n"
                + CurrentHardwareTimingSchemaLine;
            string current = CurrentLuaScriptVersionLine + "\n"
                + CurrentTraceSchemaLine + "\n"
                + CurrentHardwareTimingSchemaLine;
            string direct = PublishedDirectLuaScriptVersionLine + "\n"
                + CurrentTraceSchemaLine + "\n"
                + PublishedHardwareTimingSchemaLine;
            RequireExactMetadataMigrationShapes(
                "valid", published, current);
            RequireExactMetadataMigrationShapes(
                "valid direct", direct, current);
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactMetadataMigrationShapes(
                    "wrong fixture version",
                    published.Replace("6.39", "6.38"), current),
                "fixture");
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactMetadataMigrationShapes(
                    "mixed", published + CurrentVersionLineForTest(),
                    current),
                "fixture");
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactMetadataMigrationShapes(
                    "duplicate", published,
                    current + CurrentLuaScriptVersionLine + "\n"),
                "produced");
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactMetadataMigrationShapes(
                    "wrong schema",
                    published.Replace(
                        CurrentTraceSchemaLine,
                        "  \"trace_schema\": 6,"),
                    current),
                "fixture");
        }

        private static string CurrentVersionLineForTest()
        {
            return CurrentLuaScriptVersionLine + "\n";
        }

        private static void RequireExactMetadataMigrationShapes(
            string context, string fixtureText, string producedText)
        {
            RequireLiteralCount(context + " fixture", fixtureText,
                CurrentLuaScriptVersionLine, 1);
            RequireLiteralCount(context + " fixture", fixtureText,
                CurrentTraceSchemaLine, 1);
            RequireLiteralCount(context + " fixture", fixtureText,
                CurrentHardwareTimingSchemaLine, 1);
            RequireLiteralCount(context + " produced", producedText,
                CurrentLuaScriptVersionLine, 1);
            RequireLiteralCount(context + " produced", producedText,
                CurrentTraceSchemaLine, 1);
            RequireLiteralCount(context + " produced", producedText,
                CurrentHardwareTimingSchemaLine, 1);
        }

        private static void RequireLiteralCount(
            string context, string text, string literal, int expected)
        {
            int actual = CountOccurrences(text, literal);
            if (actual != expected)
            {
                throw new InvalidOperationException(
                    context + " contains " + actual + " copies of <"
                    + literal.TrimEnd('\n') + ">; expected " + expected + ".");
            }
        }

        private static int CountOccurrences(
            string value, string expected)
        {
            var count = 0;
            var index = 0;
            while ((index = value.IndexOf(
                expected, index, StringComparison.Ordinal)) >= 0)
            {
                count++;
                index += expected.Length;
            }
            return count;
        }

        private static void AssertMetadataShape(string context, string text)
        {
            if (text.IndexOf('\r') >= 0)
            {
                throw new InvalidOperationException(
                    context + "/metadata.json contains CR; the S3K"
                    + " complete-run recorder publishes LF.");
            }
            if (!text.EndsWith("\n", StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    context + "/metadata.json is not newline-terminated.");
            }
            foreach (string line in text.Split('\n'))
            {
                if (line.StartsWith(
                    RunIdLinePrefix, StringComparison.Ordinal))
                {
                    throw new InvalidOperationException(
                        context + "/metadata.json carries a run_id line <"
                        + line + ">; identity (A) has none.");
                }
            }
        }

        /// <summary>
        /// Length-then-hash assertion over a gzipped fixture, streamed so
        /// the decompressed bytes are never written to disk.
        /// </summary>
        private static void AssertFixtureBytes(
            string context,
            string plainPath,
            long expectedLength,
            string expectedSha256)
        {
            if (File.Exists(plainPath))
            {
                AssertProducedBytes(
                    context, plainPath, expectedLength, expectedSha256);
                return;
            }
            long length;
            string actual = HashGzip(plainPath + ".gz", out length);
            AssertBytes(context, expectedLength, expectedSha256, length,
                actual);
        }

        private static void AssertProducedBytes(
            string context,
            string path,
            long expectedLength,
            string expectedSha256)
        {
            var info = new FileInfo(path);
            if (!info.Exists)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context + ": file is absent.");
            }
            AssertBytes(
                context,
                expectedLength,
                expectedSha256,
                info.Length,
                EndToEndTests.ComputeSha256(path));
        }

        /// <summary>
        /// Reports the byte length first: a length mismatch localises a
        /// truncated or over-long stream far better than a hash mismatch,
        /// which only says "different".
        /// </summary>
        private static void AssertBytes(
            string context,
            long expectedLength,
            string expectedSha256,
            long actualLength,
            string actualSha256)
        {
            if (expectedLength != actualLength)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context + ": length is "
                    + actualLength + " bytes; expected " + expectedLength
                    + ".");
            }
            if (expectedSha256 != actualSha256)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context
                    + ": length matches but sha256 is <" + actualSha256
                    + ">; expected <" + expectedSha256 + ">.");
            }
        }

        private static string HashGzip(string gzipPath, out long length)
        {
            var total = 0L;
            var buffer = new byte[1 << 16];
            using (FileStream source = File.OpenRead(gzipPath))
            using (var gzip = new GZipStream(
                source, CompressionMode.Decompress))
            using (SHA256 sha256 = SHA256.Create())
            {
                while (true)
                {
                    int read = gzip.Read(buffer, 0, buffer.Length);
                    if (read <= 0)
                    {
                        break;
                    }
                    sha256.TransformBlock(buffer, 0, read, null, 0);
                    total += read;
                }
                sha256.TransformFinalBlock(buffer, 0, 0);
                length = total;
                return BitConverter.ToString(sha256.Hash)
                    .Replace("-", string.Empty)
                    .ToLowerInvariant();
            }
        }

        private static string ExpectedStdout(
            BizHawkInstallation installation)
        {
            var expected = new StringBuilder();
            expected.Append("BizHawk: ")
                .Append(installation.ManagedVersion).Append('\n');
            expected.Append("ROM SHA-1: ")
                .Append(RomIdentity.Sonic3kLockOnSha1).Append('\n');
            expected.Append("Movie frames: ")
                .Append(MovieFrameCount).Append('\n');
            // No "Run ID" line and no "Effective movie length" line: this
            // is the untruncated, run-id-free identity (A) invocation.
            expected.Append("Trace profile: ")
                .Append(S3KCompleteRunSegmenter.LevelTraceProfile)
                .Append('\n');
            expected.Append("Segments: ")
                .Append(SegmentCases.Length).Append('\n');
            expected.Append("Transitions: 0\n");
            foreach (SegmentCase segment in SegmentCases)
            {
                expected.Append("Segment ").Append(segment.DirToken)
                    .Append(": kind=level, BK2 frame offset=")
                    .Append(segment.Bk2FrameOffset)
                    .Append(", trace frames=")
                    .Append(segment.TraceFrameCount)
                    .Append('\n');
            }
            // No "Run manifest" line: the manifest gate stays closed.
            return expected.ToString();
        }

        private static string RunCompleteRunCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output)
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments =
                    EndToEndTests.Quote(Path.Combine(
                        EndToEndTests.ToolDirectory, "run.sh"))
                    + " --mode trace"
                    + EndToEndTests.NoCompressArgument
                    + " --load-queue-state"
                    + " --rom " + EndToEndTests.Quote(romPath)
                    + " --movie " + EndToEndTests.Quote(moviePath)
                    + " --output " + EndToEndTests.Quote(output)
                    + " --trace-profile "
                    + S3KCompleteRunSegmenter.LevelTraceProfile,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            start.EnvironmentVariables["BIZHAWK_HOME"] = bizHawkHome;
            start.EnvironmentVariables["DISPLAY"] = ":99";
            // The port refuses every output-affecting complete-run recorder
            // environment variable it does not model
            // (Program.RejectUnmodeledS3kCompleteRunEnvironment). The
            // fixtures were captured with all eight unset, so the gate
            // clears them rather than inheriting a stray value from the
            // developer's shell — which would turn a byte gate into a
            // refusal.
            foreach (string unmodeled in new[]
            {
                "OGGF_S3K_CNZ_EVENT_RAM_RANGE",
                "OGGF_S3K_AIZ_WALL_SENSOR_RANGE",
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START",
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END",
                "OGGF_S3K_CRL_RANGE",
                "OGGF_S3K_CNZ_CYLINDER_RANGE",
                "OGGF_TRACE_STOP_FRAME",
                "OGGF_BK2_FRAME_COUNT"
            })
            {
                start.EnvironmentVariables[unmodeled] = null;
            }

            EndToEndTests.ProcessResult result =
                EndToEndTests.RunProcess(start, CaptureTimeoutMilliseconds);
            if (result.ExitCode != 0)
            {
                throw new InvalidOperationException(
                    "S3K complete-run capture exited " + result.ExitCode
                    + ". stderr: " + result.StandardError);
            }
            AssertEx.Equal(string.Empty, result.StandardError);
            return result.StandardOutput;
        }

        private static Dependencies Resolve(
            string tracesRoot, string moviePath)
        {
            string suppliedRomPath =
                Environment.GetEnvironmentVariable("S3K_ROM_PATH");
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
                missing.Add("S3K_ROM_PATH is not set");
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

            if (!File.Exists(moviePath))
            {
                missing.Add("canonical movie is absent: " + moviePath);
            }
            foreach (SegmentCase segment in SegmentCases)
            {
                if (!segment.HasFixture)
                {
                    continue;
                }
                string fixtureDirectory = Path.Combine(
                    tracesRoot, segment.FixtureDirectoryName);
                if (!Directory.Exists(fixtureDirectory))
                {
                    missing.Add(
                        "canonical fixture directory is absent: "
                        + fixtureDirectory);
                }
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
                    "Supplied S3K_ROM_PATH does not exist: " + romPath
                    + ".");
            }
            RomIdentity.ValidateSonic3kLockOn(File.ReadAllBytes(romPath));
            BizHawkInstallation.Validate(bizHawkHome);
            return new Dependencies(romPath, bizHawkHome);
        }

        private sealed class Dependencies
        {
            public Dependencies(string romPath, string bizHawkHome)
            {
                RomPath = romPath;
                BizHawkHome = bizHawkHome;
            }

            public string RomPath { get; private set; }
            public string BizHawkHome { get; private set; }
        }

        /// <summary>
        /// One published segment, with the raw recorder token and semantic
        /// committed fixture destination kept distinct.
        /// </summary>
        private sealed class SegmentCase
        {
            public SegmentCase(
                string dirToken,
                string fixtureDirectoryName,
                int bk2FrameOffset,
                int traceFrameCount,
                long physicsLength,
                string physicsSha256,
                long auxStateLength,
                string auxStateSha256)
            {
                DirToken = dirToken;
                FixtureDirectoryName = fixtureDirectoryName;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                PhysicsLength = physicsLength;
                PhysicsSha256 = physicsSha256;
                AuxStateLength = auxStateLength;
                AuxStateSha256 = auxStateSha256;
            }

            public string DirToken { get; private set; }
            public string FixtureDirectoryName { get; private set; }
            public int Bk2FrameOffset { get; private set; }
            public int TraceFrameCount { get; private set; }
            public long PhysicsLength { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public long AuxStateLength { get; private set; }
            public string AuxStateSha256 { get; private set; }

            public bool HasFixture
            {
                get { return FixtureDirectoryName != null; }
            }
        }
    }
}
