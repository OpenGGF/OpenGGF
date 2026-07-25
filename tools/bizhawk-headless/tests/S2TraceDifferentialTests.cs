using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Differential gates proving the native S2 trace capture reproduces
    /// the Lua recorder byte-for-byte against the canonical fixtures:
    /// the plain gameplay_unlock profile against
    /// src/test/resources/traces/s2/ehz1_fullrun/, and
    /// level_gated_reset_aware segment selection against
    /// src/test/resources/traces/s2/arz/ (segment 0) and
    /// src/test/resources/traces/s2/arz2/ (segment 1) — both ARZ fixtures
    /// were recorded from the same s2-lvl-select-ARZ.bk2 level-select
    /// movie, so the two cases prove segment skipping as well as capture.
    /// Each case runs the trace-mode CLI end-to-end through run.sh (game
    /// auto-detected from the S2 ROM) and asserts the canonical
    /// physics.csv and aux_state.jsonl sha256 hashes (gzipped fixtures are
    /// decompressed to a temp file first; the fixtures themselves are
    /// never touched), the exact detected BK2 frame offset, and
    /// metadata.json equality normalized only on the recording_date value
    /// and the fixture's lua_script_version "9.11-s2" being produced as
    /// "9.13-s2" (the v9.12/v9.13 Lua headers declare plain-mode output
    /// byte-identical to 9.11-s2 except that string). A fourth gate runs
    /// one --run-id capture against
    /// src/test/resources/traces/s2/runs/s2-ehz-halfpipe-roundtrip/ —
    /// injecting the canonical capture session's movie-length signal via
    /// --effective-movie-length (see HalfpipeRunEffectiveMovieLength) —
    /// and asserts all five segment directories plus run_manifest.json. A
    /// fifth gate runs one --run-id capture of the full complete-emeralds
    /// movie against
    /// src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/
    /// (35 segments including all seven special stages, 34 transitions, no
    /// movie-length injection). Both run gates also assert the exact
    /// output layout: the canonical segment directories plus
    /// run_manifest.json and nothing else. Skips (does not pass) when
    /// S2_ROM_PATH or a BizHawk distribution is absent; fails (does not
    /// skip) on any hash mismatch.
    /// </summary>
    internal static class S2TraceDifferentialTests
    {
        private const int CaptureTimeoutMilliseconds = 600000;
        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");
        private const string FixtureLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.11-s2\",";
        // Run fixtures were regenerated from a verified native 9.13-s2
        // capture (SS-aux enrichment, §11.3), so fixture and produced
        // version lines coincide.
        private const string RunFixtureLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.13-s2\",";
        private const string ProducedLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.13-s2\",";

        private static readonly S2DifferentialCase Ehz1Case =
            new S2DifferentialCase(
                "ehz1_fullrun",
                "s2-ehz1.bk2",
                null,
                null,
                "gameplay_unlock",
                0,
                899,
                5852,
                6778,
                "efeb90112d36f897317f688881140c042792a2b640cf8313470216db"
                + "91f57a83",
                "5522e70caa8134570eb5acdcfc3c188655d929b2e777101ae7078516"
                + "8e122dc2");

        // Both ARZ segment cases replay the same level-select movie
        // (checked in under traces/s2/arz/); only --gameplay-segment
        // differs, so segment 1 also proves the skip-counted-segment path.
        private static readonly S2DifferentialCase ArzSegment0Case =
            new S2DifferentialCase(
                "arz",
                "s2-lvl-select-ARZ.bk2",
                "level_gated_reset_aware",
                0,
                "level_gated_reset_aware",
                0,
                2752,
                5073,
                15853,
                "72c0a49ca19e26248889aee82e68b3cd7a2f503965c1ae80eb1be16e"
                + "a01578ec",
                "390dc8862377ffb8c77c72d75938acbe1a06bf72cf94392b2ffdd2dd"
                + "6929d772");

        private static readonly S2DifferentialCase ArzSegment1Case =
            new S2DifferentialCase(
                "arz2",
                Path.Combine("..", "arz", "s2-lvl-select-ARZ.bk2"),
                "level_gated_reset_aware",
                1,
                "level_gated_reset_aware",
                1,
                7998,
                7809,
                15853,
                "83056cfcb9b059165fdd8710d7d510c9db249700a57d287610ce02d5"
                + "2ac35451",
                "bae3b1654a7356dbbc6729e56767c0e0718e842163ecc236f1c60c51"
                + "21b9c1e8");

        // Run-mode gate: one native --run-id capture of the canonical EHZ
        // halfpipe round-trip movie (level -> ss -> level -> ss -> level)
        // must reproduce all five segment directories and the run manifest.
        // The fixture set was regenerated from a verified native 9.13-s2
        // capture (SS-aux enrichment, §11.3; Lua-parity-proven against a
        // Lua 9.13 capture of the same movie) and keeps the run
        // convention's CRLF line endings (docs/s2-run-mode-behavior.md §9),
        // so physics/aux bytes are asserted without any normalization;
        // each segment metadata.json and run_manifest.json are normalized
        // on the recording_date value (metadata only) and the version
        // line.
        private const string RunFixtureDirectoryName =
            "s2-ehz-halfpipe-roundtrip";

        // The canonical capture session's movie-length signal
        // (s2-run-mode-behavior.md §2 capture-time caveat): EmuHawk's
        // chromeless movie.length() under-reported the committed BK2's
        // 22819 rows by 207 frames, so the Lua's 4b movie-done guard ended
        // the run at emu frame 22612 while seg3 was armed — before the
        // movie's tail act-transition reload ($8C at the same frame) could
        // be seen. Reproducing the fixture therefore requires injecting the
        // session value; a v9.13 runner fed the file-derived 22819 would
        // instead survive the reload and record a sixth segment (seg4_ehz2)
        // plus a level_advance transition.
        private const int HalfpipeRunEffectiveMovieLength = 22612;

        private static readonly S2RunSegmentCase[] HalfpipeRunSegmentCases =
        {
            new S2RunSegmentCase(
                "seg1_ehz1",
                "level",
                825,
                2969,
                "19f1712ccd56f95724c50256efefb49e3b65531bea864cada32a3178"
                + "d4da7320",
                "bfb475c238449f8844aec22612b25f6ac5c131db25aec98244bf6de4"
                + "5f2c3d55"),
            new S2RunSegmentCase(
                "ss",
                "special_stage",
                3795,
                5733,
                "9c2ed10bf732f76398b20e1763ddfbb5ed3df0b66394e68a78f8ec53"
                + "00129d1b",
                "c095cf82185c326404a735b7c97b515c74cc1e6f1efd98c8be823629"
                + "ed1e906c"),
            new S2RunSegmentCase(
                "seg2_ehz1",
                "level",
                9701,
                2903,
                "6e373f9cb786391813f8d50dff5bfbd57575cf525c5f272e2aca510a"
                + "70725c45",
                "307aa3380304204e02576b85b6c6886fda9b772f1b1d39a2ee90d8cf"
                + "f734d05b"),
            new S2RunSegmentCase(
                "ss_2",
                "special_stage",
                12605,
                6381,
                "13c6ea30eae9361bfb9e7c03b2cfb50bb3193d2a7a5809df780d8cd3"
                + "e5bd84ab",
                "f1451857d9102382ccfe2ea5dedd177eb5659d38cc249c7d91ecf91f"
                + "70cfe89a"),
            new S2RunSegmentCase(
                "seg3_ehz1",
                "level",
                19159,
                3452,
                "7632445f5ef5cdc1c429db3b375f95c4c34198c2abd2a86f81a49b69"
                + "3a50aea6",
                "6538660383c358770246b1a628ba89bd83969a61bca7059077a855e0"
                + "f5cd5259")
        };

        private static readonly S2RunDifferentialCase HalfpipeRunCase =
            new S2RunDifferentialCase(
                RunFixtureDirectoryName,
                "s2-ehz-halfpipe-roundtrip.bk2",
                22819,
                HalfpipeRunEffectiveMovieLength,
                "8981a13f893d0d31ce51145c4e1aff5aa51c4c59c924d75e5e7f750c38"
                + "036cd3",
                HalfpipeRunSegmentCases);

        private static readonly S2RunSegmentCase[]
            CompleteEmeraldsRunSegmentCases =
        {
            new S2RunSegmentCase(
                "seg1_ehz1",
                "level",
                769,
                3710,
                "fc6848831f153721d75c5c3451645649cdb10e0cc080bd64ab578df5"
                + "afb19d7f",
                "ddd24a35651d50697c97a4bfbe96b1d994565909ad393cbff51309d2"
                + "6367f34c"),
            new S2RunSegmentCase(
                "ss",
                "special_stage",
                4480,
                5681,
                "ac316a4639c16e181ea540dc5e456c9e9a2620600bcddfd7b56f5de0"
                + "d3a2fdff",
                "2747b9d2ec32fff2b99fe00a10b72940015c39c2f90a046da6f4b121"
                + "09244040"),
            new S2RunSegmentCase(
                "seg2_ehz1",
                "level",
                10334,
                3377,
                "b4f2e319152c7904b7a4c3a262947cc77ecdbad1142875e25643f75a"
                + "b730c3e9",
                "e5b80a5be36ea6cef24132ab821aa1b89ed6174788f468716503681e"
                + "515bd272"),
            new S2RunSegmentCase(
                "ss_2",
                "special_stage",
                13712,
                6361,
                "5034e3c3aa2234db2503745d512b1ec536f7f60bb9d409e09308f679"
                + "21aafcc2",
                "c46937bcdc3924903d8dbac8994dd190c51165fae2feb12fe430e15e"
                + "f4bdb2c5"),
            new S2RunSegmentCase(
                "seg3_ehz1",
                "level",
                20246,
                3960,
                "aa5305a1276336a3fe48ade227634ffb267ef6da5e04cd731ae63605"
                + "2e2ca729",
                "87abf88d0e005fdf0be12a8c7f445f7087ea911b3612d6a2f2c3e103"
                + "bab7c290"),
            new S2RunSegmentCase(
                "ss_3",
                "special_stage",
                24207,
                7092,
                "d8b99752b2394019649b654c3966b6020fbca6269a06d412c480baf3"
                + "c3f3ff04",
                "c5670b0fb0c09087441d493706fe6bac8972c33c278983cff72c7102"
                + "676b8b4b"),
            new S2RunSegmentCase(
                "seg4_ehz1",
                "level",
                31472,
                1288,
                "15f00ce23a0d44c884d169cbc812689e57a6b733ed03d61ba82e3b61"
                + "ed5d48fe",
                "6f4a0e4dad1c635791483f22c55659291c9326e1f5f29429b858ce49"
                + "0f42b07f"),
            new S2RunSegmentCase(
                "seg5_ehz2",
                "level",
                32931,
                6046,
                "7d40bcc30a221dec6139f5c03b3eb959061b780ab3abb2a62e4480f8"
                + "37927720",
                "212f55fddebf60651ee5b5e7f6db0a19c6245948c658b2978116ff46"
                + "df951565"),
            new S2RunSegmentCase(
                "ss_4",
                "special_stage",
                38978,
                7224,
                "d60abc7560f5cb43885ea432f38d109cb81349c8d7cda7feed0cf182"
                + "3a3d6845",
                "c2770559f83a66c12cfd3dff25a45de9011970f3b24cd5eb093a04ba"
                + "c240dd1b"),
            new S2RunSegmentCase(
                "seg6_ehz2",
                "level",
                46374,
                3794,
                "10f69aeb6e8a0af5f585db8d90af33c47c4d7420f19f08a880c0e15f"
                + "06e44949",
                "69ff4a1363f7db58612489d5ca29666fe4517cffc19c0aaad505908b"
                + "b73f5712"),
            new S2RunSegmentCase(
                "ss_5",
                "special_stage",
                50169,
                6690,
                "4ab2ff898bf8c65ffcd68568383585605f30c4c1503871172d9fb2a5"
                + "e5b3431c",
                "33f18eedf72b2f53156ec2c5c8f773ba7c3ec9d6ec0cb3e717c46fe2"
                + "be051c00"),
            new S2RunSegmentCase(
                "seg7_ehz2",
                "level",
                57031,
                3997,
                "fa7f237f71d15ca6e735673bb67e9a70f7038ca24688f3a5accb00af"
                + "932fccda",
                "6c89cc4525dbf3fea945148ffd0c223d42f2d1281e4bbc8edcd5fffa"
                + "53fa9514"),
            new S2RunSegmentCase(
                "seg8_cpz1",
                "level",
                61206,
                6613,
                "15a8226ea4b31b2d607b7a30fd8d9968250f51b46bc5f2bbad9041af"
                + "af93626e",
                "256f1334c0db01dbd585cc8077e061c16deb3bd36ea5a8fe9a688301"
                + "8d8158f1"),
            new S2RunSegmentCase(
                "seg9_cpz2",
                "level",
                67996,
                5837,
                "f0e42d35839a5864f2e1b3c60c289c1e3ca5f95383b5f055bcacc015"
                + "6b1c4a52",
                "33d65ab85d2962890e76f26dce521d35acd8a8a51ed8bb6503eafa60"
                + "57ba3669"),
            new S2RunSegmentCase(
                "ss_6",
                "special_stage",
                73834,
                8310,
                "7e6fd61427c34bd1cff8e5576438064ac9ca404807a7f7c38464434e"
                + "5985fe84",
                "550ab42db3ba5f3ad8db9e1a9164f8bafcb0e4cd07f2ec42e05d6496"
                + "ae9cdd76"),
            new S2RunSegmentCase(
                "seg10_cpz2",
                "level",
                82342,
                7088,
                "0065caecaf599f0980cec0f0e91a0e890e4fc180c941f573606c8232"
                + "04fd7e4b",
                "b37ef9c7251069bb7e10b9b353a564d6725a586f2706a408be0c33c8"
                + "8e612797"),
            new S2RunSegmentCase(
                "seg11_arz1",
                "level",
                89600,
                3420,
                "11e32a45d98b7454b4849077b933bcb43fd7e582b37f4e6851dea510"
                + "8ed66239",
                "8c06b55df18b0dbcd3ec7c4386c954695e6b7125015fb8f8d15e857f"
                + "4af774a6"),
            new S2RunSegmentCase(
                "ss_7",
                "special_stage",
                93021,
                8498,
                "1326ec2956eff47736b9d34b266e8918f2f75a57942815d4bc9830ef"
                + "ec57f6ba",
                "d5a336e5586beb032cf4c75af4e8533a3eeee6dfbdb1a703a9b7ff00"
                + "8b5ef6f9"),
            new S2RunSegmentCase(
                "seg12_arz1",
                "level",
                101691,
                4889,
                "937dc7bd1a68d471df96cbb83e7b090c205ab5aee995c933917efd3f"
                + "3c301d42",
                "6a221914ddde74a6b89da9aab86e5262c8307a513e7d93c561409336"
                + "f143cce1"),
            new S2RunSegmentCase(
                "seg13_arz2",
                "level",
                106753,
                6409,
                "fcb606e2c8ca60430e7dc040f4a75dea98c7dd2b33c6566c6f793462"
                + "c5a18e59",
                "082b5c9f9d16988daec69f7e38b160d6faead49082e698c350e0f3be"
                + "3d565d8e"),
            new S2RunSegmentCase(
                "seg14_cnz1",
                "level",
                113340,
                12145,
                "7cbfc23d57af5fb262db4948de8a2c667f232c03c1335904bac35884"
                + "e70e80a7",
                "666cddefc19d37ccf5e3bda8c3988a8817993402df588dd10f8704d4"
                + "9649184a"),
            new S2RunSegmentCase(
                "seg15_cnz2",
                "level",
                125661,
                13045,
                "87d022ecf827f0c841336517c140f2772bf84ce655a58ce3095c55e9"
                + "2a8125c2",
                "ea8a894e93e24a6c4288359deeedb53f578a77ca077a009752125d7f"
                + "2d3c9586"),
            new S2RunSegmentCase(
                "seg16_htz1",
                "level",
                138902,
                7535,
                "9f209ebc87aab1f6d6acc71c980d7fb3dc5005f022af85b6874b18bf"
                + "e7fcf467",
                "41d495b74832ed9373bf5aa004a8fa17199c7a85e672dbf616bff5d3"
                + "623ccab3"),
            new S2RunSegmentCase(
                "seg17_htz2",
                "level",
                146636,
                8460,
                "9be54e3e3b042e7301a5c1157da037dc1b41fddf239c042c2d03a189"
                + "e8447b25",
                "3624cfee1006dbfa330b73e03ce6a23a8dbfe4663fda29f94a3df0e0"
                + "cbed241c"),
            new S2RunSegmentCase(
                "seg18_mcz1",
                "level",
                155265,
                6213,
                "5889ef280e6adcbcdc6221af768a71aa3cede2d03a7fa5a1de6384aa"
                + "c6a3cf2d",
                "b2dc44087045d440e06652a73ba1131317db6fe49b4c4b31eb883766"
                + "23277b40"),
            new S2RunSegmentCase(
                "seg19_mcz2",
                "level",
                161649,
                8610,
                "3e84cd412028465552738aae878bd64769c1ea47cc893d25e1d6ae86"
                + "4655dd2c",
                "c6deffe4f380e732a1d969b1b5241866749f3b654df336efe63b92af"
                + "5e527ad8"),
            new S2RunSegmentCase(
                "seg20_ooz1",
                "level",
                170435,
                11557,
                "7a5a292d98ed57dc4759d85302c0652bf9678ea2aad466cd89f39186"
                + "6734583b",
                "7f8b0bc8a8f151020b51d480040d45ada8ab6f9f2fe8ae49795ec29a"
                + "bedc3526"),
            new S2RunSegmentCase(
                "seg21_ooz2",
                "level",
                182168,
                8591,
                "28fec87d297619ab962ffe9844b81e03831fc36a3fbbf8a790a78c45"
                + "14b83883",
                "46dfdc622986129f494b26bc5904c6a59a36defde20b7e9229b68aaa"
                + "73db9abc"),
            new S2RunSegmentCase(
                "seg22_mtz1",
                "level",
                190944,
                7590,
                "9b31dae348a28e1113c25e51ab81a6a865741b0a3dfda30f91ed5b98"
                + "f8c16194",
                "3a0df1404aab0df9ee1cb0b92141233e78d4c8fe52223d0260fed3b2"
                + "19a82cd7"),
            new S2RunSegmentCase(
                "seg23_mtz2",
                "level",
                198719,
                6542,
                "ea63f203ee211aded75ae6ccf423a7727980864b31162ef1848763a5"
                + "2f8d59b5",
                "d8178dc4bfd5095621fea57d790ed0bf982d6b852950071ee9aa21df"
                + "503fc507"),
            new S2RunSegmentCase(
                "seg24_mtz3",
                "level",
                205445,
                11341,
                "91ea91a0ea90c273f57b1444e11301e50074ef3bcad2dc3c874ff886"
                + "992382e8",
                "3dd0b6f3e159069b54ec2dac06202ac41d7e7ddec3662de513d798c2"
                + "28f05f42"),
            new S2RunSegmentCase(
                "seg25_scz1",
                "level",
                216944,
                4707,
                "05f40470d1b066637c0036f2c30bce7a90837b6b22d2b1ab1e58a9d4"
                + "f328ce88",
                "1796f46e52ab4b637d06d239eb19f57612c4946280c2ef89c7a51070"
                + "9f5a0725"),
            new S2RunSegmentCase(
                "seg26_scz1",
                "level",
                221809,
                7611,
                "4f3f1a32fa1dd95ca7154f2cc68c155ada4cff183f3bad7464a0687b"
                + "a39348ff",
                "9b779c72cfdd6d2d04bd6bf1de890397a9e730d979c15dffd81d0a82"
                + "9edeb5f9"),
            new S2RunSegmentCase(
                "seg27_wfz1",
                "level",
                229619,
                9667,
                "517c4b8e65d1c791a790d01cca21c289864be3ab95e8258dfa92eb66"
                + "a2308070",
                "248ac1bf06826cc92ab416b38833800044492b14c72eded0fc4ee441"
                + "fe96badf"),
            new S2RunSegmentCase(
                "seg28_dez1",
                "level",
                239443,
                5578,
                "73cd33d8bd6525695dfa085d04cc8a05e9dd15f00462c0a7798b55c8"
                + "5f2de8d5",
                "a404258707b9d3b08db96e63592cf3e7d7217c24629f6b363b815996"
                + "34ab5286")
        };

        // Full-run gate: one native --run-id capture of the canonical
        // complete-emeralds movie (title -> EHZ1 through DEZ, all seven
        // special stages, one SCZ death restart; 35 segments, 34
        // transitions) against
        // src/test/resources/traces/s2/runs/s2-sonic-tails-complete-emeralds/.
        // The fixture set was installed from a verified native 9.13-s2
        // capture proven content-identical (modulo CRLF and
        // recording_date) to the validated Lua 9.13-s2 reference capture
        // of the same movie. This session's movie.length() matched the
        // BK2's 259590 rows, so no --effective-movie-length injection is
        // needed.
        private static readonly S2RunDifferentialCase
            CompleteEmeraldsRunCase = new S2RunDifferentialCase(
                "s2-sonic-tails-complete-emeralds",
                "sonic-2-sonic-tails-complete-emeralds.bk2",
                259590,
                null,
                "5d21531926cdbdc4ffe0adfe5e7edb6c914f7b06bb1b99b2bceff839"
                + "04032412",
                CompleteEmeraldsRunSegmentCases);

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native capture matches canonical EHZ1"
                + " trace",
                () => NativeCaptureMatchesCanonicalTrace(Ehz1Case)));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native segment 0 capture matches"
                + " canonical ARZ trace",
                () => NativeCaptureMatchesCanonicalTrace(ArzSegment0Case)));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native segment 1 capture matches"
                + " canonical ARZ2 trace",
                () => NativeCaptureMatchesCanonicalTrace(ArzSegment1Case)));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native run mode capture matches"
                + " canonical halfpipe round trip",
                () => NativeRunModeCaptureMatchesCanonicalRun(
                    HalfpipeRunCase)));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native run mode capture matches"
                + " canonical complete emeralds run",
                () => NativeRunModeCaptureMatchesCanonicalRun(
                    CompleteEmeraldsRunCase)));
        }

        /// <summary>
        /// One canonical fixture comparison: the fixture directory name
        /// under src/test/resources/traces/s2/, the movie path relative to
        /// that directory, the optional --trace-profile /
        /// --gameplay-segment CLI arguments (null = argument omitted), the
        /// profile and segment values the CLI must report, the canonical
        /// BK2 frame offset / trace frame count / movie frame count, and
        /// the canonical sha256 hashes of the Lua recorder's physics.csv
        /// and aux_state.jsonl bytes.
        /// </summary>
        private sealed class S2DifferentialCase
        {
            public S2DifferentialCase(
                string fixtureDirectoryName,
                string movieRelativePath,
                string traceProfileArgument,
                int? gameplaySegmentArgument,
                string expectedTraceProfile,
                int expectedGameplaySegment,
                int bk2FrameOffset,
                int traceFrameCount,
                int movieFrameCount,
                string physicsSha256,
                string auxStateSha256)
            {
                FixtureDirectoryName = fixtureDirectoryName;
                MovieRelativePath = movieRelativePath;
                TraceProfileArgument = traceProfileArgument;
                GameplaySegmentArgument = gameplaySegmentArgument;
                ExpectedTraceProfile = expectedTraceProfile;
                ExpectedGameplaySegment = expectedGameplaySegment;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                MovieFrameCount = movieFrameCount;
                PhysicsSha256 = physicsSha256;
                AuxStateSha256 = auxStateSha256;
            }

            public string FixtureDirectoryName { get; private set; }
            public string MovieRelativePath { get; private set; }
            public string TraceProfileArgument { get; private set; }
            public int? GameplaySegmentArgument { get; private set; }
            public string ExpectedTraceProfile { get; private set; }
            public int ExpectedGameplaySegment { get; private set; }
            public int Bk2FrameOffset { get; private set; }
            public int TraceFrameCount { get; private set; }
            public int MovieFrameCount { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public string AuxStateSha256 { get; private set; }
        }

        /// <summary>
        /// One run-mode segment expectation: the segment directory token
        /// under the run output root, the manifest kind the CLI reports
        /// for it on stdout, the canonical BK2 frame offset and trace
        /// frame count, and the canonical sha256 hashes of the segment's
        /// physics.csv and aux_state.jsonl bytes (CRLF line endings
        /// included; special-stage aux carries the §11.3 event stream).
        /// </summary>
        private sealed class S2RunSegmentCase
        {
            public S2RunSegmentCase(
                string dirToken,
                string kind,
                int bk2FrameOffset,
                int traceFrameCount,
                string physicsSha256,
                string auxStateSha256)
            {
                DirToken = dirToken;
                Kind = kind;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                PhysicsSha256 = physicsSha256;
                AuxStateSha256 = auxStateSha256;
            }

            public string DirToken { get; private set; }
            public string Kind { get; private set; }
            public int Bk2FrameOffset { get; private set; }
            public int TraceFrameCount { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public string AuxStateSha256 { get; private set; }
        }

        /// <summary>
        /// One canonical run-mode fixture comparison: the run fixture
        /// directory name under src/test/resources/traces/s2/runs/ (also
        /// the --run-id argument), the movie file name inside that
        /// directory, the movie's BK2 frame count, the optional
        /// --effective-movie-length injection (null = argument omitted and
        /// no "Effective movie length" stdout line expected), the
        /// canonical run_manifest.json sha256, and the ordered per-segment
        /// expectations.
        /// </summary>
        private sealed class S2RunDifferentialCase
        {
            public S2RunDifferentialCase(
                string fixtureDirectoryName,
                string movieFileName,
                int movieFrameCount,
                int? effectiveMovieLength,
                string runManifestSha256,
                S2RunSegmentCase[] segments)
            {
                FixtureDirectoryName = fixtureDirectoryName;
                MovieFileName = movieFileName;
                MovieFrameCount = movieFrameCount;
                EffectiveMovieLength = effectiveMovieLength;
                RunManifestSha256 = runManifestSha256;
                Segments = segments;
            }

            public string FixtureDirectoryName { get; private set; }
            public string MovieFileName { get; private set; }
            public int MovieFrameCount { get; private set; }
            public int? EffectiveMovieLength { get; private set; }
            public string RunManifestSha256 { get; private set; }
            public S2RunSegmentCase[] Segments { get; private set; }
        }

        private static void NativeRunModeCaptureMatchesCanonicalRun(
            S2RunDifferentialCase runCase)
        {
            S2DifferentialDependencies dependencies =
                ResolveS2DifferentialDependencies();
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            string runDirectory = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s2",
                "runs",
                runCase.FixtureDirectoryName);
            string moviePath = Path.GetFullPath(Path.Combine(
                runDirectory,
                runCase.MovieFileName));

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-s2-run-differential-"
                + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                // Self-check the canonical fixture bytes first so a hash
                // mismatch reports whether the fixture or the capture
                // diverged. Gzipped fixtures are decompressed read-only
                // into the temp root; the fixtures are never modified.
                Directory.CreateDirectory(root);
                foreach (S2RunSegmentCase segment in runCase.Segments)
                {
                    string scratch = Path.Combine(
                        root,
                        "fixture-" + segment.DirToken);
                    Directory.CreateDirectory(scratch);
                    AssertSha256(
                        segment.DirToken + "/physics.csv (fixture)",
                        segment.PhysicsSha256,
                        EndToEndTests.ComputeSha256(MaterializeFixture(
                            Path.Combine(runDirectory, segment.DirToken),
                            "physics.csv",
                            Path.Combine(scratch, "physics.csv"))));
                    AssertSha256(
                        segment.DirToken + "/aux_state.jsonl (fixture)",
                        segment.AuxStateSha256,
                        EndToEndTests.ComputeSha256(MaterializeFixture(
                            Path.Combine(runDirectory, segment.DirToken),
                            "aux_state.jsonl",
                            Path.Combine(scratch, "aux_state.jsonl"))));
                }
                AssertSha256(
                    "run_manifest.json (fixture)",
                    runCase.RunManifestSha256,
                    EndToEndTests.ComputeSha256(Path.Combine(
                        runDirectory,
                        "run_manifest.json")));

                string stdout = RunRunModeCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output,
                    runCase);

                AssertEx.Equal(
                    ExpectedRunStdout(installation, output, runCase),
                    stdout);
                AssertExactRunLayout(output, runCase);
                foreach (S2RunSegmentCase segment in runCase.Segments)
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
                    AssertNormalizedRunMetadataEquality(
                        Path.Combine(
                            runDirectory,
                            segment.DirToken,
                            "metadata.json"),
                        Path.Combine(
                            output,
                            segment.DirToken,
                            "metadata.json"));
                }
                AssertNormalizedRunManifestEquality(
                    Path.Combine(runDirectory, "run_manifest.json"),
                    Path.Combine(output, "run_manifest.json"));
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static void NativeCaptureMatchesCanonicalTrace(
            S2DifferentialCase differentialCase)
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
                differentialCase.FixtureDirectoryName);
            string moviePath = Path.GetFullPath(Path.Combine(
                traceDirectory,
                differentialCase.MovieRelativePath));

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-s2-trace-differential-"
                + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                // Canonical fixture bytes may ship plain or gzipped;
                // gzipped ones are decompressed read-only into the temp
                // root so the canonical hashes are asserted against the
                // exact bytes the Lua recorder wrote. The fixtures
                // themselves are never modified.
                Directory.CreateDirectory(root);
                AssertEx.Equal(
                    differentialCase.PhysicsSha256,
                    EndToEndTests.ComputeSha256(MaterializeFixture(
                        traceDirectory,
                        "physics.csv",
                        Path.Combine(root, "fixture-physics.csv"))));
                AssertEx.Equal(
                    differentialCase.AuxStateSha256,
                    EndToEndTests.ComputeSha256(MaterializeFixture(
                        traceDirectory,
                        "aux_state.jsonl",
                        Path.Combine(root, "fixture-aux_state.jsonl"))));

                string stdout = RunTraceCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output,
                    differentialCase);

                AssertEx.Equal(
                    ExpectedStdout(installation, output, differentialCase),
                    stdout);
                AssertEx.Equal(
                    differentialCase.PhysicsSha256,
                    EndToEndTests.ComputeSha256(
                        Path.Combine(output, "physics.csv")));
                AssertEx.Equal(
                    differentialCase.AuxStateSha256,
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
            string output,
            S2DifferentialCase differentialCase)
        {
            string arguments =
                EndToEndTests.Quote(
                    Path.Combine(EndToEndTests.ToolDirectory, "run.sh"))
                + " --mode trace"
                + " --rom " + EndToEndTests.Quote(romPath)
                + " --movie " + EndToEndTests.Quote(moviePath)
                + " --output " + EndToEndTests.Quote(output);
            if (differentialCase.TraceProfileArgument != null)
            {
                arguments += " --trace-profile "
                    + differentialCase.TraceProfileArgument;
            }
            if (differentialCase.GameplaySegmentArgument.HasValue)
            {
                arguments += " --gameplay-segment "
                    + differentialCase.GameplaySegmentArgument.Value;
            }
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
                    "Trace capture exited " + result.ExitCode + ". stderr: "
                    + result.StandardError);
            }
            AssertEx.Equal(string.Empty, result.StandardError);
            return result.StandardOutput;
        }

        private static string RunRunModeCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output,
            S2RunDifferentialCase runCase)
        {
            string arguments =
                EndToEndTests.Quote(
                    Path.Combine(EndToEndTests.ToolDirectory, "run.sh"))
                + " --mode trace"
                + " --rom " + EndToEndTests.Quote(romPath)
                + " --movie " + EndToEndTests.Quote(moviePath)
                + " --output " + EndToEndTests.Quote(output)
                + " --run-id " + runCase.FixtureDirectoryName;
            if (runCase.EffectiveMovieLength.HasValue)
            {
                arguments += " --effective-movie-length "
                    + runCase.EffectiveMovieLength.Value;
            }
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
                    "Run capture exited " + result.ExitCode + ". stderr: "
                    + result.StandardError);
            }
            AssertEx.Equal(string.Empty, result.StandardError);
            return result.StandardOutput;
        }

        private static string ExpectedStdout(
            BizHawkInstallation installation,
            string output,
            S2DifferentialCase differentialCase)
        {
            return
                "BizHawk: " + installation.ManagedVersion + "\n"
                + "ROM SHA-1: " + RomIdentity.Sonic2Rev01Sha1 + "\n"
                + "Movie frames: "
                + differentialCase.MovieFrameCount + "\n"
                + "Trace profile: "
                + differentialCase.ExpectedTraceProfile + "\n"
                + "Gameplay segment: "
                + differentialCase.ExpectedGameplaySegment + "\n"
                + "BK2 frame offset: "
                + differentialCase.Bk2FrameOffset + "\n"
                + "Trace frames: "
                + differentialCase.TraceFrameCount + "\n"
                + "Physics CSV: "
                + Path.Combine(output, "physics.csv") + "\n"
                + "Aux state JSONL: "
                + Path.Combine(output, "aux_state.jsonl") + "\n"
                + "Metadata JSON: "
                + Path.Combine(output, "metadata.json") + "\n";
        }

        private static string ExpectedRunStdout(
            BizHawkInstallation installation,
            string output,
            S2RunDifferentialCase runCase)
        {
            string expected =
                "BizHawk: " + installation.ManagedVersion + "\n"
                + "ROM SHA-1: " + RomIdentity.Sonic2Rev01Sha1 + "\n"
                + "Movie frames: " + runCase.MovieFrameCount + "\n";
            if (runCase.EffectiveMovieLength.HasValue)
            {
                expected += "Effective movie length: "
                    + runCase.EffectiveMovieLength.Value + "\n";
            }
            expected +=
                "Run ID: " + runCase.FixtureDirectoryName + "\n"
                + "Segments: " + runCase.Segments.Length + "\n"
                + "Transitions: " + (runCase.Segments.Length - 1) + "\n";
            foreach (S2RunSegmentCase segment in runCase.Segments)
            {
                expected += "Segment " + segment.DirToken
                    + ": kind=" + segment.Kind
                    + ", BK2 frame offset=" + segment.Bk2FrameOffset
                    + ", trace frames=" + segment.TraceFrameCount + "\n";
            }
            expected += "Run manifest: "
                + Path.Combine(output, "run_manifest.json") + "\n";
            return expected;
        }

        /// <summary>
        /// Asserts the run output root holds exactly the expected layout:
        /// one directory per canonical segment (each containing exactly
        /// physics.csv, aux_state.jsonl, and metadata.json) plus
        /// run_manifest.json, and nothing else.
        /// </summary>
        private static void AssertExactRunLayout(
            string output,
            S2RunDifferentialCase runCase)
        {
            var expectedDirs = new List<string>();
            foreach (S2RunSegmentCase segment in runCase.Segments)
            {
                expectedDirs.Add(segment.DirToken);
            }
            expectedDirs.Sort(StringComparer.Ordinal);
            var actualDirs = new List<string>();
            foreach (string dir in Directory.GetDirectories(output))
            {
                actualDirs.Add(Path.GetFileName(dir));
            }
            actualDirs.Sort(StringComparer.Ordinal);
            AssertEx.Equal(
                string.Join("\n", expectedDirs.ToArray()),
                string.Join("\n", actualDirs.ToArray()));

            var rootFiles = new List<string>();
            foreach (string file in Directory.GetFiles(output))
            {
                rootFiles.Add(Path.GetFileName(file));
            }
            rootFiles.Sort(StringComparer.Ordinal);
            AssertEx.Equal(
                "run_manifest.json",
                string.Join("\n", rootFiles.ToArray()));

            foreach (S2RunSegmentCase segment in runCase.Segments)
            {
                string segmentDir = Path.Combine(output, segment.DirToken);
                AssertEx.Equal(
                    0,
                    Directory.GetDirectories(segmentDir).Length);
                var segmentFiles = new List<string>();
                foreach (string file in Directory.GetFiles(segmentDir))
                {
                    segmentFiles.Add(Path.GetFileName(file));
                }
                segmentFiles.Sort(StringComparer.Ordinal);
                AssertEx.Equal(
                    "aux_state.jsonl\nmetadata.json\nphysics.csv",
                    string.Join("\n", segmentFiles.ToArray()));
            }
        }

        /// <summary>
        /// First-divergence hash assertion: names the diverging file so a
        /// run-gate failure reports which of the canonical byte sets broke
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
        /// lua_script_version "9.11-s2" line, which the native port must
        /// produce as exactly "9.13-s2".
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

        /// <summary>
        /// Run-mode variant of the metadata comparison: run fixture
        /// metadata.json files carry the run convention's CRLF line
        /// endings and are stamped lua_script_version 9.13-s2, so the
        /// produced file must be byte-identical except the recording_date
        /// value (which must still carry the exact key formatting and an
        /// ISO date value); the version line must remain exactly
        /// "9.13-s2".
        /// </summary>
        private static void AssertNormalizedRunMetadataEquality(
            string fixturePath,
            string producedPath)
        {
            CompareRunLines(fixturePath, producedPath, true);
        }

        /// <summary>
        /// run_manifest.json comparison: the fixture manifest carries CRLF
        /// line endings and the 9.13-s2 stamp; the produced manifest must
        /// be byte-identical including the version line. There is no
        /// recording_date in the manifest.
        /// </summary>
        private static void AssertNormalizedRunManifestEquality(
            string fixturePath,
            string producedPath)
        {
            CompareRunLines(fixturePath, producedPath, false);
        }

        private static void CompareRunLines(
            string fixturePath,
            string producedPath,
            bool expectRecordingDate)
        {
            string fixtureText = File.ReadAllText(fixturePath);
            string producedText = File.ReadAllText(producedPath);
            // CRLF-only: no lone LF or CR may remain once CRLF pairs are
            // removed, and both files end with a CRLF-terminated line.
            AssertEx.Equal(
                false,
                fixtureText.Replace("\r\n", "").IndexOfAny(
                    new[] { '\r', '\n' }) >= 0);
            AssertEx.Equal(
                false,
                producedText.Replace("\r\n", "").IndexOfAny(
                    new[] { '\r', '\n' }) >= 0);
            AssertEx.Equal(true, fixtureText.EndsWith("\r\n"));
            AssertEx.Equal(true, producedText.EndsWith("\r\n"));

            string[] fixtureLines = fixtureText.Split(
                new[] { "\r\n" },
                StringSplitOptions.None);
            string[] producedLines = producedText.Split(
                new[] { "\r\n" },
                StringSplitOptions.None);
            AssertEx.Equal(fixtureLines.Length, producedLines.Length);
            var recordingDateLines = 0;
            var luaScriptVersionLines = 0;
            for (var index = 0; index < fixtureLines.Length; index++)
            {
                if (expectRecordingDate
                    && fixtureLines[index].StartsWith(
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
                else if (fixtureLines[index]
                    == RunFixtureLuaScriptVersionLine)
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
            AssertEx.Equal(expectRecordingDate ? 1 : 0, recordingDateLines);
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
