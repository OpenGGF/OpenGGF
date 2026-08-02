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
    /// docs/s3k-run-publication.md §0.1 — under the reviewed recorder
    /// migration contract below.
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
    ///   schema-7/hardware-schema-2 identity; only the reviewed recorder
    ///   version and recording_date values may differ. Later unapproved
    ///   schema-one fixtures therefore remain an explicit
    ///   publication-boundary failure. No other key or line may move, and
    ///   the absence of a <c>run_id</c> key is asserted explicitly.
    /// - hardware_timing.jsonl first attests the committed predecessor by
    ///   byte length, line count, and sha256, then attests the prospective
    ///   capture by the same three measures. Its 25 changed rows across 14
    ///   segments must be exact, in-place VINT-to-POST substitutions; the
    ///   ending segment must remain byte-identical.
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
        /// recorder-version migration is normalized.
        /// </summary>
        private const string PublishedLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.40-s3k-completerun\",";
        private const string PublishedDirectLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.40-s3k-completerun\",";
        private const string CurrentLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.42-s3k-completerun\",";
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
        private const string PublishedAizHeldTimingEdge16 =
            "{\"event\":\"hardware_work_completed\",\"raw_frame\":6351,"
            + "\"boundary\":\"vint_service\",\"kind\":\"kos_module_queue\","
            + "\"ordinal\":16,\"submission_fingerprint\":"
            + "\"sha256:5c387ee74a9433eebd0f6700c270d1def12cc8157434d37e33eaf8c422312399\"}";
        private const string CorrectedAizHeldTimingEdge16 =
            "{\"event\":\"hardware_work_completed\",\"raw_frame\":6351,"
            + "\"boundary\":\"post_objects\",\"kind\":\"kos_module_queue\","
            + "\"ordinal\":16,\"submission_fingerprint\":"
            + "\"sha256:5c387ee74a9433eebd0f6700c270d1def12cc8157434d37e33eaf8c422312399\"}";
        private const string PublishedAizHeldTimingEdge41 =
            "{\"event\":\"hardware_work_completed\",\"raw_frame\":26189,"
            + "\"boundary\":\"vint_service\",\"kind\":\"kos_module_queue\","
            + "\"ordinal\":41,\"submission_fingerprint\":"
            + "\"sha256:6d16d4e9eac92bb7ff5ee450e2fa0c12db8d5d16dd652676932a47b9a04306c3\"}";
        private const string CorrectedAizHeldTimingEdge41 =
            "{\"event\":\"hardware_work_completed\",\"raw_frame\":26189,"
            + "\"boundary\":\"post_objects\",\"kind\":\"kos_module_queue\","
            + "\"ordinal\":41,\"submission_fingerprint\":"
            + "\"sha256:6d16d4e9eac92bb7ff5ee450e2fa0c12db8d5d16dd652676932a47b9a04306c3\"}";
        private const string PublishedAizHeldTimingEdge42 =
            "{\"event\":\"hardware_work_completed\",\"raw_frame\":26208,"
            + "\"boundary\":\"vint_service\",\"kind\":\"kos_module_queue\","
            + "\"ordinal\":42,\"submission_fingerprint\":"
            + "\"sha256:6f27fe001c4a21687a98eb0dc8178340e7434967941c7a2fb7df442285b4c6f0\"}";
        private const string CorrectedAizHeldTimingEdge42 =
            "{\"event\":\"hardware_work_completed\",\"raw_frame\":26208,"
            + "\"boundary\":\"post_objects\",\"kind\":\"kos_module_queue\","
            + "\"ordinal\":42,\"submission_fingerprint\":"
            + "\"sha256:6f27fe001c4a21687a98eb0dc8178340e7434967941c7a2fb7df442285b4c6f0\"}";

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
                184407249,
                "7ea46ac823cb29e59fa17203fa221c04f5fb9125fb52e9adb7d4d289"
                + "077c4f13"),
            new SegmentCase(
                "hcz", "hcz_completerun", 27170, 31482,
                5100718,
                "5d829f35729bb9254f272283dd078d3c6b259c771ca3d57eea3fb249"
                + "d7ed73c7",
                225332151,
                "05345f60c609b5d30f1381aa93fe7ed1f6cddfce72c59e0073c43025"
                + "72719bc0"),
            new SegmentCase(
                "mgz", "mgz_completerun", 58653, 39398,
                6383110,
                "ddfcc9851a6c6b100e9366ebe9fccfecd9a99745639a8192f0f93e24"
                + "1879ae52",
                226981797,
                "512dc7d936178c4231c53e33dc8f3d99d518866e81fbf95e7bc8aba9"
                + "736a300f"),
            new SegmentCase(
                "cnz", "cnz_completerun", 98052, 40064,
                6491002,
                "2d1ba19a27d614c25ceb8962f7506552cc8b038cc3a36a00b08f4337"
                + "d329d404",
                230193511,
                "8938b4d72a93fe8bec414010c3fa91323ec7af5e48940d8f96844cde"
                + "6b23c886"),
            new SegmentCase(
                "icz", "icz_completerun", 138117, 25393,
                4114300,
                "386cf6e8e62b61c8cd03c252668db47d3511fc1fd6c43399830e6655"
                + "086d0c99",
                185371521,
                "66af2062d484908a6866805a51a62ba72661e3f56ab3a6228af3fe10"
                + "f0d2312f"),
            new SegmentCase(
                "lbz", "lbz_completerun", 163511, 46244,
                7492162,
                "dba472735a28d1bb3235a4fe79ab6734202456f97bca6ca00cac2f5d"
                + "64c8a139",
                287511442,
                "527f999afb68e83a6bc615dff39c3d24c2a35c988b472e8fda38ced9"
                + "8152130b"),
            new SegmentCase(
                "mhz", "mhz_completerun", 209756, 28156,
                4561906,
                "d502ee1305f363c448d5507aae54b732d851433713f809fdd79ce8cc"
                + "c21c9c03",
                197144721,
                "e2423b57d5716984444ae9de86e7ce3372fd46ac70b830dc82e8a7d2"
                + "7a0e088f"),
            new SegmentCase(
                "fbz", "fbz_completerun", 237913, 44281,
                7174156,
                "337f7619d3b516cfb5c475aed978d023ba370e8fddb3473939c4b65f"
                + "d2fdd4ca",
                291797422,
                "276df58fc9ebe612a1063d3fe294fa3b55a579be5687273220aa55f7"
                + "7d99eda7"),
            new SegmentCase(
                "soz", "soz_completerun", 282195, 59507,
                9640768,
                "d67337f964240e3ad06854b31e68eb723cc29174d490db15be27669b5"
                + "3f236bc",
                363276436,
                "f8f889d9e9e812259b85673f44010a2c07fc01a57326ebbf092a540"
                + "915017338"),
            new SegmentCase(
                "lrz", "lrz_completerun", 341703, 38755,
                6278944,
                "9c464a019b6fbdfb8086b3adcc47c5ee52d14ca13281b09cea5b648c"
                + "cf4a4fac",
                248156625,
                "79e830901e685e622c041913fe0e1bf8412048954775a188ce95dc3e"
                + "b526cf42"),
            new SegmentCase(
                "hpz22", "hpz_completerun", 380459, 16260,
                2634754,
                "a61cad6c194fc87393d4186a55bbb7d6d47465314f913a75f855d9b9"
                + "1c671f1b",
                110140240,
                "b618fdd238ff2e53bbf8d4b90a11f410dbbe092b81738d4d1b1daff5"
                + "0085f5dd"),
            new SegmentCase(
                "hpz", "ssz_completerun", 396720, 18641,
                3020476,
                "cb7b1b368810000d8ef9e6927ebc721588ec446af0748429e0573e63"
                + "6789e6dc",
                112284007,
                "1d464737895ea06a8f6cc3c8780ac7ce1f2ce4c5651f4f30f54913424"
                + "1b2567a"),
            new SegmentCase(
                "ssz", "dez_completerun", 415362, 44147,
                7152448,
                "16aa55471d830106abe26bec42c16cf0bc356f290de5e77a7ec9437d3"
                + "dbb42c2",
                268918088,
                "189a643833be9d87c2d9b9b913b2b82f4c9e2ce988eac97c1b893b32"
                + "3d8483f4"),
            new SegmentCase(
                "dez23", "ddz_completerun", 459510, 6103,
                989320,
                "697c5be5a6a52c395d10845625626f10d61a539582012d846cc88f98"
                + "319da150",
                40311448,
                "544b131e50e16aa66eb6429ff044a093a87b65d10af306404fc2c4d7"
                + "38666fe7"),
            new SegmentCase(
                "ddz", "ending_completerun", 465614, 719,
                117112,
                "18233e2ca65529b4b34ba7c917689100fba0f57a8fba00b56858f757"
                + "e3830fff",
                2593535,
                "774fd617b1323049d4a56019bc326772771253523d90b13fe5c16914"
                + "0c8f7c43")
        };

        /// <summary>
        /// Exact attestation of the published timing fixtures and the
        /// reviewed prospective 6.42 capture. Each nonzero delta count is
        /// made exclusively of same-edge VINT-to-POST substitutions.
        /// </summary>
        private static readonly Dictionary<string, TimingCase> TimingCases =
            new Dictionary<string, TimingCase>(StringComparer.Ordinal)
            {
                {"aiz", Timing(107, 23788, 3,
                    "c80a9c2f0383cfb3ad153ea5448684657543676f1c5920a0e472095a09f8d9e4",
                    "b8ebb4662c7361984e21541824166fbd597970171eed5025b6fdadbee6b4df24")},
                {"hcz", Timing(101, 22429, 2,
                    "a19d98bd7cf341ffcf1c19871e22044abc00bbde99ad352a0e1d41e8f3a34aeb",
                    "f055e4863d0048dd5143d353ad5946544a09da9d14325fe0fdf113a3d002a811")},
                {"mgz", Timing(104, 23274, 1,
                    "82cc794ff12d811cc4be3c99af2e28d1cb8ea4b8ddb04f0ea53142275d387562",
                    "e87d25f5778461ada46fc52ef84da722f864cfb3440ac282f561a08b84ad1f8c")},
                {"cnz", Timing(69, 15377, 2,
                    "821810c7cd400064f2f204eed333b16880f86a3b81dc333e7c7b74d16d086c2f",
                    "cb0d1ddc860f6984654ee0a9ed794a100733ffe11002facde19592724a9a91af")},
                {"icz", Timing(65, 14531, 2,
                    "bc006d24b4065ac13fd9d464a14d12618f4811e9a9679ce2f45f62b554677b92",
                    "4d6fd592d37b07ecce4462483b18ca5446a8f444ab98f7a110929af35410ee6e")},
                {"lbz", Timing(94, 21016, 2,
                    "50ced6a921fbf006ed0175a068d2441fe7e7255a63b1da64027c2b18597594b6",
                    "a1cce9f42ec0e7b164b2cf002a2f225ee55bf00b367e7f209537be2862d9fe6b")},
                {"mhz", Timing(91, 20309, 1,
                    "8741efc93c731c207905850e97362b704225c93462d9ffa612530c2c22c34c77",
                    "a015ea48eb6c3068e9f1c2bdc706aeeadf5e38e02f5386b8d326b6b94deef415")},
                {"fbz", Timing(72, 16060, 2,
                    "90f519de7f679b7d91a9fc34dff2a68e5afb38318235ef4f6d31e07e588997ce",
                    "c7513a1399cbd5b99b0136a03641e11e7fccffff93316fba9074ebdc2001d7c2")},
                {"soz", Timing(100, 22334, 2,
                    "bd531c00e5b6958ae5b41965fc2968417da72b7a0ebfdca14eab8660166b5a78",
                    "c4b80609ead1d6d9e5d7365de8357679fd0df30401e99f0ee00e92da74a038a8")},
                {"lrz", Timing(78, 17422, 2,
                    "5716fc06ced7cd112fd11a9e3d800b95c3029b20858300b08bea3e7b2cb9e685",
                    "6f9bf1ce3bd9cfd99c90ae613bda78c1148ed1849e87b39f356e0c6e8e6f5c6c")},
                {"hpz22", Timing(64, 14268, 4,
                    "8585a2e0f65c1f9c63a12b76b662e75039a791027e8f1300aa5bc66810826a34",
                    "fb894bc710be8b8e28ef9ed7078cc5be3c95d205be39d1d906a5a68cfa7fc264")},
                {"hpz", Timing(65, 14503, 2,
                    "3cc18542ac2b3539d95ad81a1090f37f3a8070affe390da8b48d534406a4a741",
                    "84565e6b08321137e5b175511a4a88f8ba8374839391ff4db0f75c96f07e0179")},
                {"ssz", Timing(50, 11174, 1,
                    "de34b9fe7b358246577418ff7242a06c606d6428f0ff2b1cb13865a17edc815e",
                    "391d1ad57d44156f9f3e6a84a71d241260183ef9bf85fc4eb4e575db696a4528")},
                {"dez23", Timing(17, 3785, 1,
                    "88407da56ad67405229bdedab0d1f18362a455a247644efbccc370ded57654fd",
                    "56c6b5d3bc2ff996154d21f41520d5a86e7f4b58453714ece5b06d58c06989f8")},
                {"ddz", Timing(10, 2207, 0,
                    "f414d1a774ff97c012f626c08b1dd6a896c71719c386f6635abd20d7bb8dddec",
                    "f414d1a774ff97c012f626c08b1dd6a896c71719c386f6635abd20d7bb8dddec")}
            };

        private static TimingCase Timing(
            int lineCount,
            long byteLength,
            int reviewedDeltaCount,
            string publishedSha256,
            string correctedSha256)
        {
            return new TimingCase(
                lineCount,
                byteLength,
                reviewedDeltaCount,
                publishedSha256,
                correctedSha256);
        }

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunSegmentsDifferential schema migration shapes"
                + " fail closed",
                MetadataMigrationShapesFailClosed));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunSegmentsDifferential AIZ timing delta is exact",
                ReviewedAizTimingDeltaIsExact));
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
                    TimingCase timing = TimingCases[segment.DirToken];
                    string fixtureTimingPath = Path.Combine(
                        fixtureDirectory, "hardware_timing.jsonl");
                    AssertProducedBytes(
                        segment.FixtureDirectoryName
                        + "/hardware_timing.jsonl (published fixture)",
                        fixtureTimingPath,
                        timing.ByteLength,
                        timing.PublishedSha256);
                    AssertTimingLineCount(
                        segment.FixtureDirectoryName
                        + "/hardware_timing.jsonl (published fixture)",
                        fixtureTimingPath,
                        timing.LineCount);
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
                    string fixtureTimingPath = Path.Combine(
                        tracesRoot,
                        segment.FixtureDirectoryName,
                        "hardware_timing.jsonl");
                    string producedTimingPath = Path.Combine(
                        produced, "hardware_timing.jsonl");
                    TimingCase timing = TimingCases[segment.DirToken];
                    AssertProducedBytes(
                        segment.DirToken + "/hardware_timing.jsonl"
                        + " (reviewed prospective capture)",
                        producedTimingPath,
                        timing.ByteLength,
                        timing.CorrectedSha256);
                    AssertTimingLineCount(
                        segment.DirToken + "/hardware_timing.jsonl"
                        + " (reviewed prospective capture)",
                        producedTimingPath,
                        timing.LineCount);
                    string fixtureTiming = File.ReadAllText(fixtureTimingPath);
                    string producedTiming = File.ReadAllText(producedTimingPath);
                    AssertReviewedTimingDelta(
                        segment.DirToken,
                        fixtureTiming,
                        producedTiming,
                        timing.ReviewedDeltaCount);
                    if (segment.DirToken == "aiz")
                    {
                        AssertReviewedAizTimingDelta(
                            fixtureTiming,
                            producedTiming);
                    }
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

        private static void ReviewedAizTimingDeltaIsExact()
        {
            string published = PublishedAizHeldTimingEdge16 + "\n"
                + PublishedAizHeldTimingEdge41 + "\n"
                + PublishedAizHeldTimingEdge42 + "\n";
            string corrected = CorrectedAizHeldTimingEdge16 + "\n"
                + CorrectedAizHeldTimingEdge41 + "\n"
                + CorrectedAizHeldTimingEdge42 + "\n";

            AssertReviewedTimingDelta("AIZ unit vector", published, corrected, 3);
            AssertReviewedAizTimingDelta(published, corrected);
            AssertEx.Throws<InvalidOperationException>(
                () => AssertReviewedTimingDelta(
                    "AIZ unit vector", published, corrected, 2),
                "expected 2");
            AssertEx.Throws<InvalidOperationException>(
                () => AssertReviewedTimingDelta(
                    "AIZ unit vector",
                    published,
                    corrected.Replace("\"ordinal\":42", "\"ordinal\":43"),
                    3),
                "changed beyond");
            AssertEx.Throws<InvalidOperationException>(
                () => AssertReviewedAizTimingDelta(published, published),
                "corrected edge");
            AssertEx.Throws<InvalidOperationException>(
                () => AssertReviewedAizTimingDelta(
                    published,
                    corrected.Replace(
                        CorrectedAizHeldTimingEdge42,
                        PublishedAizHeldTimingEdge42)),
                "corrected edge");
            AssertEx.Throws<InvalidOperationException>(
                () => AssertReviewedAizTimingDelta(
                    published + "unchanged\n",
                    corrected + "changed\n"),
                "beyond the reviewed edge");
        }

        private static void AssertReviewedAizTimingDelta(
            string published, string corrected)
        {
            RequireReviewedAizEdgePair(
                published, corrected,
                PublishedAizHeldTimingEdge16,
                CorrectedAizHeldTimingEdge16);
            RequireReviewedAizEdgePair(
                published, corrected,
                PublishedAizHeldTimingEdge41,
                CorrectedAizHeldTimingEdge41);
            RequireReviewedAizEdgePair(
                published, corrected,
                PublishedAizHeldTimingEdge42,
                CorrectedAizHeldTimingEdge42);
            string normalized = corrected
                .Replace(
                    CorrectedAizHeldTimingEdge16,
                    PublishedAizHeldTimingEdge16)
                .Replace(
                    CorrectedAizHeldTimingEdge41,
                    PublishedAizHeldTimingEdge41)
                .Replace(
                    CorrectedAizHeldTimingEdge42,
                    PublishedAizHeldTimingEdge42);
            if (normalized != published)
            {
                throw new InvalidOperationException(
                    "AIZ hardware timing changed beyond the reviewed edge");
            }
        }

        private static void RequireReviewedAizEdgePair(
            string published,
            string corrected,
            string publishedEdge,
            string correctedEdge)
        {
            if (CountOccurrences(published, publishedEdge) != 1
                || CountOccurrences(published, correctedEdge) != 0)
            {
                throw new InvalidOperationException(
                    "published fixture does not contain exactly one reviewed"
                    + " predecessor edge");
            }
            if (CountOccurrences(corrected, correctedEdge) != 1
                || CountOccurrences(corrected, publishedEdge) != 0)
            {
                throw new InvalidOperationException(
                    "corrected edge is not the exact reviewed AIZ attribution");
            }
        }

        private static void AssertReviewedTimingDelta(
            string context,
            string published,
            string corrected,
            int expectedDeltaCount)
        {
            string[] publishedLines = published.Split('\n');
            string[] correctedLines = corrected.Split('\n');
            if (publishedLines.Length != correctedLines.Length)
            {
                throw new InvalidOperationException(
                    context + " timing line count changed from "
                    + publishedLines.Length + " to "
                    + correctedLines.Length + ".");
            }

            var deltaCount = 0;
            for (var index = 0; index < publishedLines.Length; index++)
            {
                if (publishedLines[index] == correctedLines[index])
                {
                    continue;
                }
                deltaCount++;
                string expectedCorrected = publishedLines[index].Replace(
                    "\"boundary\":\"vint_service\"",
                    "\"boundary\":\"post_objects\"");
                if (expectedCorrected == publishedLines[index]
                    || correctedLines[index] != expectedCorrected)
                {
                    throw new InvalidOperationException(
                        context + " timing line " + (index + 1)
                        + " changed beyond one exact VINT-to-POST"
                        + " attribution substitution.");
                }
            }
            if (deltaCount != expectedDeltaCount)
            {
                throw new InvalidOperationException(
                    context + " timing changed " + deltaCount
                    + " lines; expected " + expectedDeltaCount + ".");
            }
        }

        private static void AssertTimingLineCount(
            string context, string path, int expectedLineCount)
        {
            var actualLineCount = 0;
            using (var reader = new StreamReader(path))
            {
                while (reader.ReadLine() != null)
                {
                    actualLineCount++;
                }
            }
            if (actualLineCount != expectedLineCount)
            {
                throw new InvalidOperationException(
                    context + " has " + actualLineCount
                    + " lines; expected " + expectedLineCount + ".");
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
        /// Requires exact published-v6.40 to current-v6.42 equality apart
        /// from recorder version and recording_date. Both sides stay
        /// LF-only and carry no run_id.
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
            producedText = producedText.Replace(
                CurrentLuaScriptVersionLine,
                directPredecessor
                    ? PublishedDirectLuaScriptVersionLine
                    : PublishedLuaScriptVersionLine);
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
                    published.Replace("6.40", "6.39"), current),
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
                PublishedLuaScriptVersionLine, 1);
            RequireLiteralCount(context + " fixture", fixtureText,
                CurrentLuaScriptVersionLine, 0);
            RequireLiteralCount(context + " fixture", fixtureText,
                CurrentTraceSchemaLine, 1);
            RequireLiteralCount(context + " fixture", fixtureText,
                CurrentHardwareTimingSchemaLine, 1);
            RequireLiteralCount(context + " produced", producedText,
                CurrentLuaScriptVersionLine, 1);
            RequireLiteralCount(context + " produced", producedText,
                PublishedLuaScriptVersionLine, 0);
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

        private sealed class TimingCase
        {
            public TimingCase(
                int lineCount,
                long byteLength,
                int reviewedDeltaCount,
                string publishedSha256,
                string correctedSha256)
            {
                LineCount = lineCount;
                ByteLength = byteLength;
                ReviewedDeltaCount = reviewedDeltaCount;
                PublishedSha256 = publishedSha256;
                CorrectedSha256 = correctedSha256;
            }

            public int LineCount { get; private set; }
            public long ByteLength { get; private set; }
            public int ReviewedDeltaCount { get; private set; }
            public string PublishedSha256 { get; private set; }
            public string CorrectedSha256 { get; private set; }
        }
    }
}
