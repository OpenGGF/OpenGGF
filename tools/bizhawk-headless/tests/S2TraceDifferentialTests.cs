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
        private static Func<ProcessStartInfo, int, EndToEndTests.ProcessResult>
            runCaptureChild = EndToEndTests.RunProcess;
        private const int CaptureTimeoutMilliseconds = 600000;
        private const int CompleteEmeraldsCaptureTimeoutMilliseconds = 2400000;
        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");
        private const string FixtureLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.13-s2\",";
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
                "baad7f87f5830f6cbe693bdeab23592196421c94788dce190aee9ea7"
                + "39d6d3a7");

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
                "937fe943e550d06038668d59cb7ac697cc458365f4fe80f0c511da37"
                + "4111c380");

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
                "46b83d0997aa45b0053f67241a8aa979427d1c333e04e1ce22026db3"
                + "3dc6b16c");

        private static readonly S2DifferentialCase SpecialStageCase =
            new S2DifferentialCase(
                "special_stage",
                "s2-lvl-select-special-stage.bk2",
                "s2_special_stage",
                null,
                "s2_special_stage",
                0,
                2754,
                5299,
                8053,
                "418033a193690be9ddd8bd7ca5ebaeea48efce08c13b807883a318d6"
                + "e415777b",
                "d86ad76fbac0c6e7dcd79458595a0682edec29e0e520d42c57233f96"
                + "26e13a98");

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
                "57e8781af9e222713e21bffdd710c9db3049e8d98d165b6ee151d9a5"
                + "edd504b2"),
            new S2RunSegmentCase(
                "ss",
                "special_stage",
                3795,
                5733,
                "9c2ed10bf732f76398b20e1763ddfbb5ed3df0b66394e68a78f8ec53"
                + "00129d1b",
                "f82abe1075ac9365fa1c04016b90450acbb687ecb96d2ee471458f97"
                + "a6a0e4b0"),
            new S2RunSegmentCase(
                "seg2_ehz1",
                "level",
                9701,
                2903,
                "6e373f9cb786391813f8d50dff5bfbd57575cf525c5f272e2aca510a"
                + "70725c45",
                "62761383367433f14507325fc4abc07cd6d1844f293a6f20bace551c"
                + "81a193ca"),
            new S2RunSegmentCase(
                "ss_2",
                "special_stage",
                12605,
                6381,
                "13c6ea30eae9361bfb9e7c03b2cfb50bb3193d2a7a5809df780d8cd3"
                + "e5bd84ab",
                "d245e105227a6e832f9ec3ca3472979f4e89890f7bed6790a67d65a5"
                + "2ccbe601"),
            new S2RunSegmentCase(
                "seg3_ehz1",
                "level",
                19159,
                3452,
                "7632445f5ef5cdc1c429db3b375f95c4c34198c2abd2a86f81a49b69"
                + "3a50aea6",
                "4379aec94f3ba4ed414944b9c9d48b153803f25f002dd6bd89dc0da1"
                + "560b9e54")
        };

        private static readonly S2RunDifferentialCase HalfpipeRunCase =
            new S2RunDifferentialCase(
                RunFixtureDirectoryName,
                "s2-ehz-halfpipe-roundtrip.bk2",
                22819,
                HalfpipeRunEffectiveMovieLength,
                "b643c2c60ff953e24c3f36afcf72925010a4f6ec17770de5c5e50e98a3"
                + "711032",
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
                "7f1fe6e6fccaa0cc3499d0d75bd18a90e65adcca8d90246cb02997ee"
                + "b10850da"),
            new S2RunSegmentCase(
                "ss",
                "special_stage",
                4480,
                5681,
                "ac316a4639c16e181ea540dc5e456c9e9a2620600bcddfd7b56f5de0"
                + "d3a2fdff",
                "f7429894c6cb2ff22bb96e53fd28093801c319f0c046c8af294d2141"
                + "201d1824"),
            new S2RunSegmentCase(
                "seg2_ehz1",
                "level",
                10334,
                3377,
                "b4f2e319152c7904b7a4c3a262947cc77ecdbad1142875e25643f75a"
                + "b730c3e9",
                "4b0e8b9412d17b27df9040bfbc6d943075751f78f2253561c05bc943"
                + "efe6e1ad"),
            new S2RunSegmentCase(
                "ss_2",
                "special_stage",
                13712,
                6361,
                "5034e3c3aa2234db2503745d512b1ec536f7f60bb9d409e09308f679"
                + "21aafcc2",
                "d99da064ba8a774088b2a2cab1a310379b2dcc2409ece3faf47a62db"
                + "a71b393e"),
            new S2RunSegmentCase(
                "seg3_ehz1",
                "level",
                20246,
                3960,
                "aa5305a1276336a3fe48ade227634ffb267ef6da5e04cd731ae63605"
                + "2e2ca729",
                "5ffde6abd1d6b825916c40a8d04ebbcd10f83bbf31d9b15b7f283a86"
                + "3266896e"),
            new S2RunSegmentCase(
                "ss_3",
                "special_stage",
                24207,
                7092,
                "d8b99752b2394019649b654c3966b6020fbca6269a06d412c480baf3"
                + "c3f3ff04",
                "5ebd69e0f63aa12dd12e175da0931045d33c041058da9ff0ebfba1dc"
                + "af209f13"),
            new S2RunSegmentCase(
                "seg4_ehz1",
                "level",
                31472,
                1288,
                "15f00ce23a0d44c884d169cbc812689e57a6b733ed03d61ba82e3b61"
                + "ed5d48fe",
                "0afeb04e4416babb504a489bc66af092b864ac6333cf253fdd3bb687"
                + "746bb7ae"),
            new S2RunSegmentCase(
                "seg5_ehz2",
                "level",
                32931,
                6046,
                "7d40bcc30a221dec6139f5c03b3eb959061b780ab3abb2a62e4480f8"
                + "37927720",
                "bf7030c0b57811c673800bba2ebba0e8955c3fda621de5eb7ca98a80"
                + "bb710cfe"),
            new S2RunSegmentCase(
                "ss_4",
                "special_stage",
                38978,
                7224,
                "d60abc7560f5cb43885ea432f38d109cb81349c8d7cda7feed0cf182"
                + "3a3d6845",
                "5ea11107186482f6f3642f9925ae808738ff367922cd72bebf11b284"
                + "3dbce37a"),
            new S2RunSegmentCase(
                "seg6_ehz2",
                "level",
                46374,
                3794,
                "10f69aeb6e8a0af5f585db8d90af33c47c4d7420f19f08a880c0e15f"
                + "06e44949",
                "f864953865e22b34b9547b42c7b722b634d2b54601f73f3bffb31c94"
                + "f6227dfa"),
            new S2RunSegmentCase(
                "ss_5",
                "special_stage",
                50169,
                6690,
                "4ab2ff898bf8c65ffcd68568383585605f30c4c1503871172d9fb2a5"
                + "e5b3431c",
                "8c6991dd605c1dae78c92fb8ff4dfbc7ef4dff6ac7bef2822bf502fa"
                + "4df396de"),
            new S2RunSegmentCase(
                "seg7_ehz2",
                "level",
                57031,
                3997,
                "fa7f237f71d15ca6e735673bb67e9a70f7038ca24688f3a5accb00af"
                + "932fccda",
                "9fa05a91b436f9bee10ceb67a659ae7f856d5cab4d8e6e4350459929"
                + "1c54e372"),
            new S2RunSegmentCase(
                "seg8_cpz1",
                "level",
                61206,
                6613,
                "15a8226ea4b31b2d607b7a30fd8d9968250f51b46bc5f2bbad9041af"
                + "af93626e",
                "54c2605c72dd013eeca5b68660f99282a351f488ac7bbeecb7610eca"
                + "7f97867b"),
            new S2RunSegmentCase(
                "seg9_cpz2",
                "level",
                67996,
                5837,
                "f0e42d35839a5864f2e1b3c60c289c1e3ca5f95383b5f055bcacc015"
                + "6b1c4a52",
                "ea059fbb09d38017b7439c555666a7cbcf76d3c81a74206f715236ff"
                + "2777c579"),
            new S2RunSegmentCase(
                "ss_6",
                "special_stage",
                73834,
                8310,
                "7e6fd61427c34bd1cff8e5576438064ac9ca404807a7f7c38464434e"
                + "5985fe84",
                "8f2701dc44ecb0a8cd157fea6226908ec12c72578f5540398bb325e2"
                + "8e561045"),
            new S2RunSegmentCase(
                "seg10_cpz2",
                "level",
                82342,
                7088,
                "0065caecaf599f0980cec0f0e91a0e890e4fc180c941f573606c8232"
                + "04fd7e4b",
                "7b139e832ec4b23901d0169eaf31baa7f95fc8ee2acad6e987a6d346"
                + "e26c45f3"),
            new S2RunSegmentCase(
                "seg11_arz1",
                "level",
                89600,
                3420,
                "11e32a45d98b7454b4849077b933bcb43fd7e582b37f4e6851dea510"
                + "8ed66239",
                "9877b753c365ffb8485ebfd25847c65fd794b91ff71de95a9618e52d"
                + "2518f930"),
            new S2RunSegmentCase(
                "ss_7",
                "special_stage",
                93021,
                8498,
                "1326ec2956eff47736b9d34b266e8918f2f75a57942815d4bc9830ef"
                + "ec57f6ba",
                "213a82c5bab5c6cc453dbbfc92101ae33c1ff53856fee7a5ada9e014"
                + "ba847f7c"),
            new S2RunSegmentCase(
                "seg12_arz1",
                "level",
                101691,
                4889,
                "937dc7bd1a68d471df96cbb83e7b090c205ab5aee995c933917efd3f"
                + "3c301d42",
                "ea12d6fb42eac5bbe2a9368705bfef442cbfd3bb006806f8848a9c3d"
                + "ea535a98"),
            new S2RunSegmentCase(
                "seg13_arz2",
                "level",
                106753,
                6409,
                "fcb606e2c8ca60430e7dc040f4a75dea98c7dd2b33c6566c6f793462"
                + "c5a18e59",
                "3c058768861e96a7dacffee51ac66d4296ccd4112172b6312133d244"
                + "2a1a1e51"),
            new S2RunSegmentCase(
                "seg14_cnz1",
                "level",
                113340,
                12145,
                "7cbfc23d57af5fb262db4948de8a2c667f232c03c1335904bac35884"
                + "e70e80a7",
                "2ee1617080157449704fd3135130086af05143a81f8a979f765ba77d"
                + "35b2a63c"),
            new S2RunSegmentCase(
                "seg15_cnz2",
                "level",
                125661,
                13045,
                "87d022ecf827f0c841336517c140f2772bf84ce655a58ce3095c55e9"
                + "2a8125c2",
                "230ca9ef038e05697cccf532d0aa63db18ecbc6a533bc4bcd26ac856"
                + "d4a31627"),
            new S2RunSegmentCase(
                "seg16_htz1",
                "level",
                138902,
                7535,
                "9f209ebc87aab1f6d6acc71c980d7fb3dc5005f022af85b6874b18bf"
                + "e7fcf467",
                "08d81cac234d8f44ddef25b2bc8423e6347e0c3f809042927eab7812"
                + "a907e8db"),
            new S2RunSegmentCase(
                "seg17_htz2",
                "level",
                146636,
                8460,
                "9be54e3e3b042e7301a5c1157da037dc1b41fddf239c042c2d03a189"
                + "e8447b25",
                "9bedcf72f40da0c2cb7104e28b5c94f6a5b0e5f746d5591f39850fd7"
                + "35e88297"),
            new S2RunSegmentCase(
                "seg18_mcz1",
                "level",
                155265,
                6213,
                "5889ef280e6adcbcdc6221af768a71aa3cede2d03a7fa5a1de6384aa"
                + "c6a3cf2d",
                "d180ea4a629d521bedbf9009921e35daa49d47091edea39223396649"
                + "84482f5a"),
            new S2RunSegmentCase(
                "seg19_mcz2",
                "level",
                161649,
                8610,
                "3e84cd412028465552738aae878bd64769c1ea47cc893d25e1d6ae86"
                + "4655dd2c",
                "a1e04c485c93a764bb2d47e09713b43f8d504715ceebc844e9c0d513"
                + "c33c557f"),
            new S2RunSegmentCase(
                "seg20_ooz1",
                "level",
                170435,
                11557,
                "7a5a292d98ed57dc4759d85302c0652bf9678ea2aad466cd89f39186"
                + "6734583b",
                "c2e3dbc68e098afe5eedd71887d9e20031b5e65cfa8de30e54732ea1"
                + "d86b97b1"),
            new S2RunSegmentCase(
                "seg21_ooz2",
                "level",
                182168,
                8591,
                "28fec87d297619ab962ffe9844b81e03831fc36a3fbbf8a790a78c45"
                + "14b83883",
                "6725fc2608b899eb17fd3f804b022d9d2a3cd900ebcc4b5b3a38bebd"
                + "2637176b"),
            new S2RunSegmentCase(
                "seg22_mtz1",
                "level",
                190944,
                7590,
                "9b31dae348a28e1113c25e51ab81a6a865741b0a3dfda30f91ed5b98"
                + "f8c16194",
                "56a6e419d206f5bcc5a3bdde81afb7f2f0ca323735ea26382489d175"
                + "2ff5f579"),
            new S2RunSegmentCase(
                "seg23_mtz2",
                "level",
                198719,
                6542,
                "ea63f203ee211aded75ae6ccf423a7727980864b31162ef1848763a5"
                + "2f8d59b5",
                "f648ceefc429e9d40d65e986f9a9bf5dabe7e821ba8dd19ba695f076"
                + "8deb7f3a"),
            new S2RunSegmentCase(
                "seg24_mtz3",
                "level",
                205445,
                11341,
                "91ea91a0ea90c273f57b1444e11301e50074ef3bcad2dc3c874ff886"
                + "992382e8",
                "205a4170f16ecc57eabd5822de7fb807df2e3998425f10d7d3a7fc65"
                + "90ab13c5"),
            new S2RunSegmentCase(
                "seg25_scz1",
                "level",
                216944,
                4707,
                "05f40470d1b066637c0036f2c30bce7a90837b6b22d2b1ab1e58a9d4"
                + "f328ce88",
                "34d4e0429a74bc57d656bf53fe99d168d707a50b85014c16a9a8d57b"
                + "f5932d6a"),
            new S2RunSegmentCase(
                "seg26_scz1",
                "level",
                221809,
                7611,
                "4f3f1a32fa1dd95ca7154f2cc68c155ada4cff183f3bad7464a0687b"
                + "a39348ff",
                "f60ce08ede27c96b6ef440017b4fd6991afc1552012be67167b155c7"
                + "9c1bf34d"),
            new S2RunSegmentCase(
                "seg27_wfz1",
                "level",
                229619,
                9667,
                "517c4b8e65d1c791a790d01cca21c289864be3ab95e8258dfa92eb66"
                + "a2308070",
                "0cdd4ce1b3886e24c8f7ac23a2324cd0693e20dd13a75e61b8ded141"
                + "ebab0ac8"),
            new S2RunSegmentCase(
                "seg28_dez1",
                "level",
                239443,
                5578,
                "73cd33d8bd6525695dfa085d04cc8a05e9dd15f00462c0a7798b55c8"
                + "5f2de8d5",
                "54af0c14bc457eed33bc36ec976a434c835d9de6dd19307e4ddba952"
                + "4a95a809")
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
                "627aeec524f497304db89365a384c59203cad2d480f84972af0c8a48"
                + "263b6cc7",
                CompleteEmeraldsRunSegmentCases);

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential selects the measured complete-emeralds timeout only for that route",
                SelectsRouteSpecificTimeout));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential timeout cannot promote partial staged output",
                TimeoutCannotPromotePartialStaging,
                serial: true));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native capture matches canonical EHZ1"
                + " trace",
                () => NativeCaptureMatchesCanonicalTrace(Ehz1Case),
                game: "s2",
                movie: "s2-ehz1",
                kind: TestKind.Gate,
                estimatedSeconds: 3.0));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native segment 0 capture matches"
                + " canonical ARZ trace",
                () => NativeCaptureMatchesCanonicalTrace(ArzSegment0Case),
                game: "s2",
                movie: "s2-lvl-select-ARZ",
                kind: TestKind.Gate,
                estimatedSeconds: 3.0));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native segment 1 capture matches"
                + " canonical ARZ2 trace",
                () => NativeCaptureMatchesCanonicalTrace(ArzSegment1Case),
                game: "s2",
                movie: "s2-lvl-select-ARZ",
                kind: TestKind.Gate,
                estimatedSeconds: 6.0));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native standalone special-stage capture"
                + " matches canonical trace",
                () => NativeCaptureMatchesCanonicalTrace(SpecialStageCase),
                game: "s2",
                movie: "s2-lvl-select-special-stage",
                kind: TestKind.Gate,
                estimatedSeconds: 8.0));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native run mode capture matches"
                + " canonical halfpipe round trip",
                () => NativeRunModeCaptureMatchesCanonicalRun(
                    HalfpipeRunCase),
                game: "s2",
                movie: "s2-ehz-halfpipe-roundtrip",
                kind: TestKind.Gate,
                estimatedSeconds: 8.0));
            // The 259,590-row emeralds movie: the second longest capture
            // in the suite, so it has to start early.
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native run mode capture matches"
                + " canonical complete emeralds run",
                () => NativeRunModeCaptureMatchesCanonicalRun(
                    CompleteEmeraldsRunCase),
                game: "s2",
                movie: "sonic-2-sonic-tails-complete-emeralds",
                kind: TestKind.Gate,
                estimatedSeconds: 227.0));
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

            string root = TestScratch.CreateRootPath(
                "openggf-s2-run-differential");
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

            string root = TestScratch.CreateRootPath(
                "openggf-s2-trace-differential");
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
                if (differentialCase.ExpectedTraceProfile
                    == S2SpecialStageCaptureRunner.TraceProfile)
                {
                    AssertNormalizedStandaloneSpecialStageMetadataEquality(
                        Path.Combine(traceDirectory, "metadata.json"),
                        Path.Combine(output, "metadata.json"));
                }
                else
                {
                    AssertNormalizedMetadataEquality(
                        Path.Combine(traceDirectory, "metadata.json"),
                        Path.Combine(output, "metadata.json"));
                }
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
                + EndToEndTests.NoCompressArgument
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

        private static int CaptureTimeoutFor(S2RunDifferentialCase runCase)
        {
            return Object.ReferenceEquals(runCase, CompleteEmeraldsRunCase)
                ? CompleteEmeraldsCaptureTimeoutMilliseconds
                : CaptureTimeoutMilliseconds;
        }

        private static void SelectsRouteSpecificTimeout()
        {
            AssertEx.Equal(CompleteEmeraldsCaptureTimeoutMilliseconds,
                CaptureTimeoutFor(CompleteEmeraldsRunCase));
            AssertEx.Equal(CaptureTimeoutMilliseconds,
                CaptureTimeoutFor(HalfpipeRunCase));
        }

        private static void TimeoutCannotPromotePartialStaging()
        {
            string root = TestScratch.CreateRootPath(
                "openggf-s2-timeout-staging");
            Directory.CreateDirectory(root);
            string staged = Path.Combine(root, "run_manifest.json.tmp");
            string final = Path.Combine(root, "run_manifest.json");
            var original = runCaptureChild;
            bool productionBoundaryReached = false;
            runCaptureChild = (start, timeout) =>
            {
                productionBoundaryReached =
                    start.FileName == "/bin/bash"
                    && start.Arguments.Contains("--mode trace")
                    && start.Arguments.Contains("--run-id "
                        + HalfpipeRunCase.FixtureDirectoryName);
                File.WriteAllText(staged, "{\"partial\":true}");
                throw new TimeoutException(
                    "Trace capture exceeded " + timeout + " ms and was killed");
            };
            try
            {
                AssertEx.Throws<TimeoutException>(
                    () => RunRunModeCapture(
                        "stub-rom", "stub-bizhawk", "stub-movie",
                        root, HalfpipeRunCase),
                    "was killed");
                AssertEx.Equal(true, productionBoundaryReached);
                AssertEx.Equal(true, File.Exists(staged));
                AssertEx.Equal(false, File.Exists(final));
            }
            finally
            {
                runCaptureChild = original;
            }
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
                + EndToEndTests.NoCompressArgument
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
            EndToEndTests.ProcessResult result = runCaptureChild(
                start,
                CaptureTimeoutFor(runCase));
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
                + (differentialCase.ExpectedTraceProfile
                    == S2SpecialStageCaptureRunner.TraceProfile
                    ? string.Empty
                    : "Gameplay segment: "
                        + differentialCase.ExpectedGameplaySegment + "\n")
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

        private static void
            AssertNormalizedStandaloneSpecialStageMetadataEquality(
                string fixturePath,
                string producedPath)
        {
            string[] fixtureLines = File.ReadAllText(fixturePath)
                .Replace("\r\n", "\n").Split('\n');
            string[] producedLines = File.ReadAllText(producedPath)
                .Replace("\r\n", "\n").Split('\n');
            AssertEx.Equal(fixtureLines.Length, producedLines.Length);
            for (var index = 0; index < fixtureLines.Length; index++)
            {
                if (fixtureLines[index].StartsWith(
                    RecordingDateLinePrefix,
                    StringComparison.Ordinal))
                {
                    if (!RecordingDateLine.IsMatch(producedLines[index]))
                    {
                        throw new InvalidOperationException(
                            "Produced recording_date line is malformed.");
                    }
                }
                else if (fixtureLines[index]
                    == "  \"lua_script_version\": \"1.4-s2ss\",")
                {
                    AssertEx.Equal(
                        "  \"lua_script_version\": \"1.4-s2ss-native\",",
                        producedLines[index]);
                }
                else
                {
                    AssertEx.Equal(fixtureLines[index], producedLines[index]);
                }
            }
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
