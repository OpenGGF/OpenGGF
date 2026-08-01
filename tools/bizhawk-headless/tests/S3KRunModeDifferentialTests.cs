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
    /// Differential gate for the native S3K complete-run recorder in RUN
    /// MODE (<c>--run-id</c>), over the canonical Knuckles multi-bonus
    /// movie
    /// (src/test/resources/traces/s3k/_movies/s3-knux-multibonus-ss.bk2,
    /// 114,622 input rows). Two untruncated passes, one per capture
    /// identity, both driven through the real CLI:
    ///
    /// 1. <c>--run-id s3k-multibonus</c> reproduces all four committed
    ///    identity-(C) directories — <c>bonus_gumball</c>,
    ///    <c>bonus_slots</c>, <c>bonus_pachinko</c>, <c>special_stage</c> —
    ///    with physics.csv and aux_state.jsonl byte-identical (length AND
    ///    sha256, ZERO normalization) and metadata.json equal line for line
    ///    with exact v6.37/schema-7/hardware-schema metadata apart from the
    ///    recording_date VALUE. Three of those four were
    ///    previously verified only by hand
    ///    (docs/s3k-run-publication.md §10.5); this closes them.
    ///
    /// 2. <c>--run-id s3-knux-multibonus-ss</c> reproduces the committed
    ///    identity-(B) run tree —
    ///    src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/ — all
    ///    25 segment directories and run_manifest.json, on the same terms:
    ///    physics.csv and aux_state.jsonl by raw length AND sha256 with
    ///    ZERO normalization, run_manifest.json byte-identical, and
    ///    metadata.json line for line apart from recording_date.
    ///
    /// ## (B) is byte-reproducible; it did not used to be
    ///
    /// Until the fixtures were regenerated (commit 63eccd290), (B) was a
    /// 2026-07-19 Windows EmuHawk capture by Lua 6.31-s3k-completerun,
    /// three recorder builds behind HEAD, and this case could only assert
    /// STRUCTURE with three separately pinned non-byte-exact deltas:
    ///
    /// - **CRLF.** The Lua writes only "\n"; io.open(…, "w") is text mode,
    ///   so the Windows host expanded every newline, leaving all 25 (B)
    ///   segments and the (B) manifest CRLF. Identity (A) and identity (C)
    ///   are Linux/Mono captures and are LF, and this port publishes LF in
    ///   both plain and run mode — ExpandRunNewlines must never reach an
    ///   S3K path.
    /// - **ADDR_FRAMECOUNT 0xFE08 → 0xFE04** (Lua commit 6564667eb, no
    ///   version bump). 0xFE08 is Debug_placement_mode, dead-zero in
    ///   gameplay; 0xFE04 is the live Level_frame_counter. The old (B)
    ///   carried the constant "0000" in physics.csv column 5 and a
    ///   constant 0 vfc / level_frame_counter on every aux line.
    /// - **Diagnostic hooks were armed.** The old (B) predated the
    ///   LIGHTWEIGHT_REGEN inversion (Lua commit 192d9c976), so four of
    ///   its 25 segments carried hook-driven aux families a hooks-off
    ///   capture cannot emit: hcz_2 95 lines, hcz_6 48, mgz 69, mgz_3 69.
    ///
    /// The regenerated fixtures were captured on Linux by
    /// s3k_complete_run_recorder.lua with the diagnostic hooks off, so all
    /// three are gone at the source: the tree is LF, carries the live
    /// 0xFE04 counter, carries capture_mode, reports pre_trace_osc_frames
    /// 1, and contains no hook-driven aux line. That is the same recorder
    /// identity the port targets, so the CRLF folding, the counter-column
    /// masking, the per-segment hook-line-count literals and the
    /// differing-key allowances are all deleted rather than
    /// retained-but-unused — a normalization nobody needs is a
    /// normalization that silently absorbs the next regression.
    ///
    /// Both sets were regenerated once more at Lua 6.33-s3k-completerun,
    /// the ADDR_VBLA_WORD fix: vblank_counter reads 0xFE0E (the
    /// V_int_run_count low word) instead of 0xFE12 (Life_count), so
    /// physics.csv column 6 became a live per-frame counter instead of
    /// lives &lt;&lt; 8. Every physics hash below moved EXCEPT the three
    /// special-stage segments' (ss / ss_2 / ss_3, and identity (C)'s
    /// special_stage), whose s3k_special_stage writer has no
    /// vblank_counter column at all — that asymmetry is itself pinned
    /// here. No aux hash moved: no aux field ever read that address. The
    /// manifest moved only because it stamps the version, and every
    /// physics LENGTH is unchanged because the column is fixed-width.
    ///
    /// What (B) gates that (A) and (C) do not is run_manifest.json: 8,740
    /// bytes covering all 25 segment records (dir, kind, trace_profile,
    /// bk2_frame_offset, trace_frame_count, zone_id, act, bonus_stage_type
    /// / special_stage_index) and all 22 transition records with their
    /// sampled RAM (special_bonus_entry_flag, saved_x_pos, saved_y_pos,
    /// last_star_post_hit, rings_before, rings_after, emeralds_before,
    /// emeralds_after). S3KCompleteRunPublicationTests already gates the
    /// formatter given the right data; this gates the recorder discovering
    /// that data from the movie. The manifest carries no recording_date, so
    /// it has no normalized field.
    ///
    /// Cost, measured: 1m08s wall and 234 MB peak RSS per pass, 370 MB of
    /// output each. The passes run sequentially and each output tree is
    /// deleted before the next, so peak scratch is one pass. That scratch
    /// lives under the tool directory's .scratch/ rather than
    /// Path.GetTempPath() because /tmp is frequently a RAM-backed tmpfs.
    ///
    /// Skips (does not pass) when S3K_ROM_PATH, a BizHawk distribution, the
    /// movie or the fixtures are absent; fails (does not skip) on any
    /// mismatch.
    /// </summary>
    internal static class S3KRunModeDifferentialTests
    {
        private const int CaptureTimeoutMilliseconds = 3600000;
        private const string MovieFileName = "s3-knux-multibonus-ss.bk2";
        private const int MovieFrameCount = 114622;

        /// <summary>The identity-(C) run id, from its fixtures' metadata.</summary>
        private const string SetCRunId = "s3k-multibonus";

        /// <summary>The identity-(B) run id, from its manifest.</summary>
        private const string SetBRunId = "s3-knux-multibonus-ss";

        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");

        /// <summary>
        /// Exact v6.40 stamps shared by the freshly published fixture and
        /// the current native capture. No schema/version normalization is
        /// permitted.
        /// </summary>
        private const string PublishedVersionLine =
            "  \"lua_script_version\": \"6.40-s3k-completerun\",";
        private const string CurrentVersionLine =
            "  \"lua_script_version\": \"6.40-s3k-completerun\",";
        private const string CurrentTraceSchemaLine =
            "  \"trace_schema\": 7,";
        private const string PublishedHardwareTimingSchemaLine =
            "  \"hardware_timing_schema\": 2,\n";
        private const string CurrentHardwareTimingSchemaLine =
            "  \"hardware_timing_schema\": 2,\n";

        // ------------------------------------------------------------------
        // Identity (C): four byte-exact directories.
        // ------------------------------------------------------------------

        /// <summary>
        /// docs/s3k-run-publication.md §0.3, independently recomputed from
        /// the committed .gz files. bonus_gumball is also covered by the
        /// five-second truncated gate in S3KCompleteRunDifferentialTests;
        /// it is repeated here so this pass stands alone. The published
        /// candidate replaces the legacy empty special_stage aux payload
        /// with physical direct/module queue state.
        /// </summary>
        private static readonly SetCCase[] SetCCases =
        {
            new SetCCase(
                "gumball", "bonus_gumball",
                232294,
                "8d6e3e3004e811a124c516ac224fe9e9dd5476cce1d6c3097b3b7c65"
                + "c2526dd6",
                9057942,
                "612914268da742c0da96896b938aa0c531b2446562243b9537f93344"
                + "fb68c416"),
            new SetCCase(
                "slots", "bonus_slots",
                195034,
                "7fe7de5bb8dd97bf98ef595e46899097bb0b9e01a999aeff0d9953e6"
                + "3504809b",
                2909404,
                "ebc012ecbdeb2aaea94527ce7aa7718a438047d5242e707f660b0c74"
                + "4b0262c2"),
            new SetCCase(
                "pachinko", "bonus_pachinko",
                494896,
                "86c2c655d41153bde45ab762d2f382c51b59d1b7063b2e8b1c7b42a2"
                + "cc13308b",
                12122249,
                "cb647ebc3f94a5d7f43302eeb5f4edd7960d92546b6189ae9314d8f6"
                + "fb789537"),
            new SetCCase(
                "ss", "special_stage",
                272711,
                "b6afb3f5f9708f974bf71d5fcfb973aced8e60d81e51f64e01a88012"
                + "996fa5a1",
                2090546,
                "136e8d8fda694df46d731235b3359174df34eba56b6c0d5f8b925083"
                + "4b7f1b97")
        };

        // ------------------------------------------------------------------
        // Identity (B): the 25-segment run tree.
        // ------------------------------------------------------------------

        private const string Level = "level";
        private const string Bonus = "bonus_stage";
        private const string Ss = "special_stage";

        /// <summary>
        /// The committed run_manifest.json, recomputed from the file. It is
        /// the one published artefact identities (A) and (C) do not have,
        /// and it carries no recording_date or other host-dependent field,
        /// so it is gated raw with nothing excluded.
        /// </summary>
        private const long ManifestLength = 8740;

        private const string ManifestSha256 =
            "be3f3940abd8932559dd556913be792f6968edbd39310c70ebb32c9d5b08"
            + "d550";

        /// <summary>
        /// The 25 published segments in recorder emission order, with the
        /// bk2_frame_offset / trace_frame_count pairs transcribed from the
        /// committed manifest and the canonical byte lengths and sha256s
        /// recomputed from the committed files. The published candidate
        /// replaces each legacy empty special-stage aux payload with
        /// physical direct/module queue state. The dir tokens pin
        /// the repeat-visit suffix rule: one segment_dir_counts table keyed
        /// by BASE token, shared by the level, bonus and ss paths, 1-based,
        /// no zero padding, never reset — so aiz_5 is the fifth AIZ segment
        /// even though four other zones' segments were interleaved.
        ///
        /// gumball, slots, pachinko and ss are cut from this same movie at
        /// the same offsets as the four identity-(C) fixtures, so their
        /// hashes here equal the SetCCases hashes above. That agreement is
        /// a property of the fixtures, not an assumption of this gate:
        /// each set is pinned independently and a drift in either fails.
        /// </summary>
        private static readonly SetBCase[] SetBCases =
        {
            new SetBCase(
                "aiz", Level, 915, 4654,
                754582,
                "28cff717d9be01526ba5085a30e7365ac99f39b4cba195d1e155619c"
                + "2e3310ce",
                20148941,
                "61ceefb3466d0049a14436e5c58f499f492e20dedd6560d811329dff"
                + "c4ab43aa"),
            new SetBCase(
                "gumball", Bonus, 5570, 1430,
                232294,
                "8d6e3e3004e811a124c516ac224fe9e9dd5476cce1d6c3097b3b7c65"
                + "c2526dd6",
                9057942,
                "612914268da742c0da96896b938aa0c531b2446562243b9537f93344"
                + "fb68c416"),
            new SetBCase(
                "aiz_2", Level, 7001, 2140,
                347314,
                "12fafead29b8fc5a19dbf1ff4c7593c83094660c6ed541bb2996c6be"
                + "5c4f5258",
                9908887,
                "cecce89c0341aa7a3ee3d45d21e93cbda1d609345fc71f7877249c1a"
                + "a4dcca77"),
            new SetBCase(
                "slots", Bonus, 9142, 1200,
                195034,
                "7fe7de5bb8dd97bf98ef595e46899097bb0b9e01a999aeff0d9953e6"
                + "3504809b",
                2909404,
                "ebc012ecbdeb2aaea94527ce7aa7718a438047d5242e707f660b0c74"
                + "4b0262c2"),
            new SetBCase(
                "aiz_3", Level, 10343, 7568,
                1226650,
                "80ed52afd35943599df7ec33865d72b313ef8b0735a9c81980c58fc0"
                + "21ac927d",
                40422240,
                "b772d481389e0c5cd33fe80b5538ca0a3a1b37221f3cd9d61d790e97"
                + "f665c743"),
            new SetBCase(
                "slots_2", Bonus, 17912, 1278,
                207670,
                "d73a419cd5df1aa5a096a6c15cc4bc3f0e8b3745c570c84f8c11536d"
                + "29f7d62c",
                2920720,
                "362f061712bae35dceabf1dbc042ef4255f8c0ccf97472b56dda3300"
                + "d0607c77"),
            new SetBCase(
                "aiz_4", Level, 19191, 3210,
                520654,
                "2509a49506a2dc85ed76738958d48e369c00e8ca671d0138c30c606e"
                + "25a9dd82",
                15409994,
                "4451ba3b322c28dea77a8dedd069d26b06347b87f56c9c40a8fba868"
                + "11c9c577"),
            new SetBCase(
                "gumball_2", Bonus, 22402, 1648,
                267610,
                "8d0e6f4225232c868da5c605104f284a36f86dcbc4dbbd6a876e6d46"
                + "bba9f813",
                9497616,
                "2c83a18ee9bdfa6012228b3d4993075b5ca9a03bbfa1881c5dbef1b2"
                + "dc53234c"),
            new SetBCase(
                "aiz_5", Level, 24051, 3631,
                588856,
                "74550e535b7e8616d3a5b7b01022d000f1dfb0e7f68540833914038a"
                + "cbbca9d5",
                22358123,
                "bedc19f955feddd8a004ba41dd79301a778d1fd0e02d8454aba56ab4"
                + "75192079"),
            new SetBCase(
                "hcz", Level, 27683, 3176,
                515146,
                "3cbe8e16420257f39f2975cab0321c4a801526208e0888cbe390d453"
                + "e8736c54",
                9492272,
                "a03eaa0103ada47a75d2a71219325964449bed385c95ce22b8484e76"
                + "9086b83e"),
            new SetBCase(
                "slots_3", Bonus, 30860, 5379,
                872032,
                "4dbb1771ea0a51f9c0f322dc48d9440d3eed531dbf9daa3042dd6bf7"
                + "25c32ee8",
                15051619,
                "50daeb325780d87a38f263906e5b3b0fd514b8f6bdd861f3de63a2f4"
                + "9a1f4356"),
            new SetBCase(
                "hcz_2", Level, 36240, 11933,
                1933780,
                "326e64b5370622d8ee2ea2ae8d22703febeea3aaea8e2fea78cfb812"
                + "0143f023",
                64745218,
                "164daf64d9f53a1c35add0dfd1ab74fda68ac16e3cb0f524ba390de0"
                + "815194d5"),
            new SetBCase(
                "ss", Ss, 48174, 4630,
                272711,
                "b6afb3f5f9708f974bf71d5fcfb973aced8e60d81e51f64e01a88012"
                + "996fa5a1",
                2090546,
                "136e8d8fda694df46d731235b3359174df34eba56b6c0d5f8b925083"
                + "4b7f1b97"),
            new SetBCase(
                "hcz_3", Level, 54274, 3949,
                640372,
                "ef0ae48f3b5b76c5a6c86b01a4efbc503d3b6f363e115351dfd692ee"
                + "f74c9ec8",
                18224831,
                "9f2dd1b714dcd4cdc45f5085a7e58a965926ee58bdc2a41dd95d4352"
                + "c53775e4"),
            new SetBCase(
                "slots_4", Bonus, 58224, 1603,
                260320,
                "e9e6b054d0f3aea9a639ee4bfb726b6434102212581e0b3b704cc8cd"
                + "5a2986fb",
                3748905,
                "fc199eb6f612baf1209119572543f72a64d1bd414683139d605b231e"
                + "9d1a84c7"),
            new SetBCase(
                "hcz_4", Level, 59828, 2097,
                340348,
                "6d4bde24a8af3674c59e16688342f6275e8e18240293f6d78a9e23f0"
                + "660e4156",
                7969005,
                "89984b9b0b0c30f0611b339e7162933f205d51a318c423f9dd7ba777"
                + "99e8342c"),
            new SetBCase(
                "ss_2", Ss, 61926, 7194,
                429484,
                "1ccbc30da3673f16507a2d0b358c865d26da3d0ad58fbf13e86a2a73"
                + "0ab4608f",
                3249477,
                "7be97a85ab6e9e3b1f3cf4cf59a662ea7052a9c3c49741a4a3e765c6"
                + "59f6ed3a"),
            new SetBCase(
                "hcz_5", Level, 70590, 3435,
                557104,
                "8055565bfb24d87fc131acb06dd0460b4b96d82bca1a4ff39a7678d0"
                + "bc5edb3e",
                11803435,
                "ec69071af5c4ec9a85b7c5cb5f8b26efdcabb518cf83b33a31f358a5"
                + "6fd3e507"),
            new SetBCase(
                "slots_5", Bonus, 74026, 1791,
                290776,
                "97b17ee7c44f34be21b3281c45436359323538cbad4238f1a9d7229f"
                + "dd53c669",
                5143758,
                "21b9d8439f91fcc9ed8ce3ab5b1c084a6e21ba971b5870ed797e82fb"
                + "a302e613"),
            new SetBCase(
                "hcz_6", Level, 75818, 8422,
                1364998,
                "09d7c5877287f497ac4f8efb9035375a1e1b61ffc6c4a9ef9b2172ad"
                + "2d4605cb",
                54810001,
                "8be1e0b6ce9fb37ca8cc417b7d504881f476a15ee808bd57fae1aeab"
                + "aff19a45"),
            new SetBCase(
                "mgz", Level, 84241, 8721,
                1413436,
                "d9bfd06a2a94dbeb89799a10bc9b4ed729107e645008289697091a04"
                + "b56dcf32",
                33142083,
                "526b091e887d57d8fa5e3e21fef6b2c82da61435fda6e53437f31c07"
                + "3eefb2ad"),
            new SetBCase(
                "pachinko", Bonus, 92963, 3051,
                494896,
                "86c2c655d41153bde45ab762d2f382c51b59d1b7063b2e8b1c7b42a2"
                + "cc13308b",
                12122249,
                "cb647ebc3f94a5d7f43302eeb5f4edd7960d92546b6189ae9314d8f6"
                + "fb789537"),
            new SetBCase(
                "mgz_2", Level, 96015, 2076,
                336946,
                "c877cc8b8ae72f2c0ee687a9443399c33cdc743156480d887d833045"
                + "ead7a85c",
                7742175,
                "565ee0891ed4ba28737c603ae9a06d4df7b19369ecd957954c02bdb9"
                + "408cf6fd"),
            new SetBCase(
                "ss_3", Ss, 98092, 6537,
                387486,
                "885b9ac9a1ed75787f07d03da7db7228a34b6c29ace72b7efea84ab7"
                + "a08fff64",
                2952510,
                "2ad98efae99b4acb70bc7bd41d3b38e8424f7f51be8725cf59eba08b"
                + "39a4a39b"),
            new SetBCase(
                "mgz_3", Level, 106104, 8517,
                1380388,
                "6752c5d444bf653ee8a1d45c1feda14c5d91040b5b6d2ed0ea83b4eb"
                + "1951162b",
                35911562,
                "c865a4a7f4cdb13aeec3508c3526052890c9452c380b9be6fb5f3e71"
                + "bb562755")
        };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KRunModeDifferential schema migration shapes fail closed",
                MigrationShapesFailClosed));
            tests.Add(new TestMain.TestCase(
                "S3KRunModeDifferential native run-mode capture matches the"
                + " four canonical identity-(C) segments",
                NativeRunCaptureMatchesSetC,
                game: "s3k",
                movie: "s3-knux-multibonus-ss",
                kind: TestKind.Gate,
                estimatedSeconds: 71.0));
            tests.Add(new TestMain.TestCase(
                "S3KRunModeDifferential native run-mode capture reproduces"
                + " the s3-knux-multibonus-ss run byte for byte",
                NativeRunCaptureReproducesSetB,
                game: "s3k",
                movie: "s3-knux-multibonus-ss",
                kind: TestKind.Gate,
                estimatedSeconds: 75.0));
        }

        // ==================================================================
        // Case 1 — identity (C), byte-exact
        // ==================================================================

        private static void NativeRunCaptureMatchesSetC()
        {
            string tracesRoot = TracesRoot();
            string moviePath =
                Path.Combine(tracesRoot, "_movies", MovieFileName);
            var fixtureDirectories = new List<string>();
            foreach (SetCCase segment in SetCCases)
            {
                fixtureDirectories.Add(
                    Path.Combine(tracesRoot, segment.FixtureDirectoryName));
            }
            Dependencies dependencies =
                Resolve(moviePath, fixtureDirectories);
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            WithCapture(
                dependencies, moviePath, SetCRunId, installation,
                delegate(string output)
                {
                    // Self-check the canonical bytes first, streamed from
                    // the .gz so nothing is materialised, so that a hash
                    // mismatch says whether the fixture or the capture
                    // moved.
                    foreach (SetCCase segment in SetCCases)
                    {
                        string fixtureDirectory = Path.Combine(
                            tracesRoot, segment.FixtureDirectoryName);
                        AssertFixtureBytes(
                            segment.FixtureDirectoryName + "/physics.csv"
                            + " (fixture)",
                            Path.Combine(fixtureDirectory, "physics.csv"),
                            segment.PhysicsLength,
                            segment.PhysicsSha256);
                        AssertFixtureBytes(
                            segment.FixtureDirectoryName
                            + "/aux_state.jsonl (fixture)",
                            Path.Combine(
                                fixtureDirectory, "aux_state.jsonl"),
                            segment.AuxStateLength,
                            segment.AuxStateSha256);
                    }

                    foreach (SetCCase segment in SetCCases)
                    {
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
                            SetCRunId,
                            Path.Combine(
                                tracesRoot,
                                segment.FixtureDirectoryName,
                                "metadata.json"),
                            Path.Combine(produced, "metadata.json"));
                    }

                    AssertOutputLayout(output);
                });
        }

        /// <summary>
        /// Byte-identical to the fixture apart from the recording_date
        /// VALUE. Both sides must be LF-only and newline-terminated, have
        /// the same line count, carry exactly one recording_date line at
        /// the same index (well-formed on the produced side), carry exactly
        /// one lua_script_version line equal to the pinned v6.37 literal,
        /// and carry the run_id line for the id this pass was given — which
        /// is not a delta at all in either case, because each fixture set
        /// was itself captured under the run id its case supplies.
        ///
        /// Shared by both cases: the two identities differ only in which
        /// run id and which fixture tree they point at, so a single
        /// comparison serves both and neither can drift into a weaker
        /// allowance than the other.
        /// </summary>
        private static void AssertMetadataEqualExceptRecordingDate(
            string context,
            string runId,
            string fixturePath,
            string producedPath)
        {
            string fixtureText = ReadAllTextExact(fixturePath);
            string producedText = ReadAllTextExact(producedPath);
            AssertPublishedShape(context + " (fixture)", fixtureText);
            AssertPublishedShape(context, producedText);
            RequireExactMetadataMigrationShapes(
                context, fixtureText, producedText);
            producedText = producedText.Replace(
                CurrentVersionLine, PublishedVersionLine).Replace(
                    CurrentHardwareTimingSchemaLine,
                    PublishedHardwareTimingSchemaLine);
            AssertEx.Equal(
                1,
                CountOccurrences(
                    producedText, PublishedVersionLine));
            AssertEx.Equal(
                1,
                CountOccurrences(
                    producedText, PublishedHardwareTimingSchemaLine));
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
            var runIdLines = 0;
            string expectedRunIdLine =
                "  \"run_id\": \"" + runId + "\",";
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
                if (fixtureLines[index] == PublishedVersionLine)
                {
                    versionLines++;
                }
                if (fixtureLines[index] == expectedRunIdLine)
                {
                    runIdLines++;
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
            AssertEx.Equal(1, runIdLines);
        }

        // ==================================================================
        // Case 2 — identity (B), byte-exact
        // ==================================================================

        private static void NativeRunCaptureReproducesSetB()
        {
            string tracesRoot = TracesRoot();
            string moviePath =
                Path.Combine(tracesRoot, "_movies", MovieFileName);
            string runRoot = Path.Combine(
                tracesRoot, "runs", SetBRunId);
            var fixtureDirectories = new List<string> { runRoot };
            foreach (SetBCase segment in SetBCases)
            {
                fixtureDirectories.Add(
                    Path.Combine(runRoot, segment.DirToken));
            }
            Dependencies dependencies =
                Resolve(moviePath, fixtureDirectories);
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            WithCapture(
                dependencies, moviePath, SetBRunId, installation,
                delegate(string output)
                {
                    // Self-check the canonical bytes first, streamed from
                    // the .gz so nothing is materialised, so that a hash
                    // mismatch says whether the fixture or the capture
                    // moved. 370 MB decompressed across the 25 segments.
                    foreach (SetBCase segment in SetBCases)
                    {
                        string fixtureDirectory =
                            Path.Combine(runRoot, segment.DirToken);
                        AssertFixtureBytes(
                            segment.DirToken + "/physics.csv (fixture)",
                            Path.Combine(fixtureDirectory, "physics.csv"),
                            segment.PhysicsLength,
                            segment.PhysicsSha256);
                        AssertFixtureBytes(
                            segment.DirToken
                            + "/aux_state.jsonl (fixture)",
                            Path.Combine(
                                fixtureDirectory, "aux_state.jsonl"),
                            segment.AuxStateLength,
                            segment.AuxStateSha256);
                    }
                    AssertFixtureBytes(
                        "run_manifest.json (fixture)",
                        Path.Combine(runRoot, "run_manifest.json"),
                        ManifestLength,
                        ManifestSha256);

                    AssertRunManifestReproduced(runRoot, output);
                    foreach (SetBCase segment in SetBCases)
                    {
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
                            SetBRunId,
                            Path.Combine(
                                runRoot,
                                segment.DirToken,
                                "metadata.json"),
                            Path.Combine(produced, "metadata.json"));
                    }
                    AssertOutputLayout(output);
                });
        }

        /// <summary>
        /// The produced manifest must equal the committed one exactly: no
        /// newline folding, no version substitution, and no free field at
        /// all, because run_manifest.json carries no recording_date. The
        /// decoded-text comparison runs first so a mismatch names the
        /// diverging line; the raw length-and-sha256 assertion behind it is
        /// what makes the check byte-exact rather than text-exact.
        /// </summary>
        private static void AssertRunManifestReproduced(
            string runRoot, string output)
        {
            string producedPath =
                Path.Combine(output, "run_manifest.json");
            if (!File.Exists(producedPath))
            {
                throw new InvalidOperationException(
                    "First divergence at run_manifest.json: the run-mode"
                    + " pass published none. The Lua's gate (L1459) is a"
                    + " disjunction — a run id alone forces emission.");
            }
            string producedText = ReadAllTextExact(producedPath);
            AssertPublishedShape("run_manifest.json", producedText);
            string fixtureText = ReadAllTextExact(
                Path.Combine(runRoot, "run_manifest.json"));
            RequireExactManifestMigrationShapes(
                "run_manifest.json", fixtureText, producedText);
            producedText = producedText.Replace(
                CurrentVersionLine, PublishedVersionLine);
            AssertEx.Equal(
                1,
                CountOccurrences(
                    producedText, PublishedVersionLine));
            AssertTextEqual(
                "run_manifest.json",
                fixtureText,
                producedText);
        }

        private static void MigrationShapesFailClosed()
        {
            string published = PublishedVersionLine + "\n"
                + CurrentTraceSchemaLine + "\n"
                + PublishedHardwareTimingSchemaLine;
            string current = CurrentVersionLine + "\n"
                + CurrentTraceSchemaLine + "\n"
                + CurrentHardwareTimingSchemaLine;
            RequireExactMetadataMigrationShapes(
                "valid metadata", published, current);
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactMetadataMigrationShapes(
                    "wrong fixture version",
                    published.Replace("6.40", "6.39"), current),
                "fixture");
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactMetadataMigrationShapes(
                    "mixed metadata",
                    published + CurrentVersionLine + "\n", current),
                "fixture");
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactMetadataMigrationShapes(
                    "duplicate metadata", published,
                    current + CurrentHardwareTimingSchemaLine),
                "produced");
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactMetadataMigrationShapes(
                    "wrong schema metadata",
                    published.Replace(
                        CurrentTraceSchemaLine,
                        "  \"trace_schema\": 6,"),
                    current),
                "fixture");

            RequireExactManifestMigrationShapes(
                "valid manifest",
                PublishedVersionLine + "\n",
                CurrentVersionLine + "\n");
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactManifestMigrationShapes(
                    "wrong fixture manifest",
                    PublishedVersionLine.Replace("6.40", "6.39") + "\n",
                    CurrentVersionLine + "\n"),
                "fixture");
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactManifestMigrationShapes(
                    "mixed manifest",
                    PublishedVersionLine + "\n"
                        + CurrentVersionLine + "\n",
                    CurrentVersionLine + "\n"),
                "fixture");
            AssertEx.Throws<InvalidOperationException>(
                () => RequireExactManifestMigrationShapes(
                    "duplicate manifest",
                    PublishedVersionLine + "\n",
                    CurrentVersionLine + "\n"
                        + CurrentVersionLine + "\n"),
                "produced");
        }

        private static void RequireExactMetadataMigrationShapes(
            string context, string fixtureText, string producedText)
        {
            RequireLiteralCount(context + " fixture", fixtureText,
                PublishedVersionLine, 1);
            RequireLiteralCount(context + " fixture", fixtureText,
                CurrentTraceSchemaLine, 1);
            RequireLiteralCount(context + " fixture", fixtureText,
                PublishedHardwareTimingSchemaLine, 1);
            RequireLiteralCount(context + " produced", producedText,
                CurrentVersionLine, 1);
            RequireLiteralCount(context + " produced", producedText,
                CurrentTraceSchemaLine, 1);
            RequireLiteralCount(context + " produced", producedText,
                CurrentHardwareTimingSchemaLine, 1);
        }

        private static void RequireExactManifestMigrationShapes(
            string context, string fixtureText, string producedText)
        {
            RequireLiteralCount(context + " fixture", fixtureText,
                PublishedVersionLine, 1);
            RequireLiteralCount(context + " produced", producedText,
                CurrentVersionLine, 1);
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

        // ==================================================================
        // Shared capture driver and helpers
        // ==================================================================

        private static void WithCapture(
            Dependencies dependencies,
            string moviePath,
            string runId,
            BizHawkInstallation installation,
            Action<string> body)
        {
            string root = Path.Combine(
                EndToEndTests.ToolDirectory,
                ".scratch",
                "s3k-runmode-" + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                Directory.CreateDirectory(root);
                string stdout = RunCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output,
                    runId);
                // The whole 25-segment summary, so a segmentation
                // regression anywhere in the movie is reported as a
                // summary diff before any byte comparison runs.
                AssertTextEqual(
                    "stdout",
                    ExpectedStdout(installation, runId, output),
                    stdout);
                body(output);
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
        /// Exactly the 25 segment directories with three files each, plus
        /// run_manifest.json and nothing else. Directories the recorder
        /// pre-creates for zones the movie never visits are not published,
        /// because the publisher stages files rather than directories.
        /// </summary>
        private static void AssertOutputLayout(string output)
        {
            var expectedDirectories = new List<string>();
            foreach (SetBCase segment in SetBCases)
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
            AssertTextEqual(
                "published directories",
                string.Join("\n", expectedDirectories.ToArray()),
                string.Join("\n", actualDirectories.ToArray()));

            var actualRootFiles = new List<string>();
            foreach (string path in Directory.GetFiles(output))
            {
                actualRootFiles.Add(Path.GetFileName(path));
            }
            actualRootFiles.Sort(StringComparer.Ordinal);
            AssertTextEqual(
                "published root files",
                "run_manifest.json",
                string.Join("\n", actualRootFiles.ToArray()));

            foreach (string dirToken in actualDirectories)
            {
                string directory = Path.Combine(output, dirToken);
                var actualFiles = new List<string>();
                foreach (string path in Directory.GetFiles(directory))
                {
                    actualFiles.Add(Path.GetFileName(path));
                }
                actualFiles.Sort(StringComparer.Ordinal);
                AssertTextEqual(
                    dirToken + " files",
                    "aux_state.jsonl\nhardware_timing.jsonl\nmetadata.json\nphysics.csv",
                    string.Join("\n", actualFiles.ToArray()));
                AssertEx.Equal(
                    0, Directory.GetDirectories(directory).Length);
            }
        }

        private static string ExpectedStdout(
            BizHawkInstallation installation,
            string runId,
            string output)
        {
            var expected = new StringBuilder();
            expected.Append("BizHawk: ")
                .Append(installation.ManagedVersion).Append('\n');
            expected.Append("ROM SHA-1: ")
                .Append(RomIdentity.Sonic3kLockOnSha1).Append('\n');
            expected.Append("Movie frames: ")
                .Append(MovieFrameCount).Append('\n');
            // No "Effective movie length" line: both passes are
            // untruncated. A "Run ID" line rather than a "Trace profile"
            // one, because --run-id selects the recorder here.
            expected.Append("Run ID: ").Append(runId).Append('\n');
            expected.Append("Segments: ")
                .Append(SetBCases.Length).Append('\n');
            expected.Append("Transitions: 22\n");
            foreach (SetBCase segment in SetBCases)
            {
                expected.Append("Segment ").Append(segment.DirToken)
                    .Append(": kind=").Append(segment.Kind)
                    .Append(", BK2 frame offset=")
                    .Append(segment.Bk2FrameOffset)
                    .Append(", trace frames=")
                    .Append(segment.TraceFrameCount)
                    .Append('\n');
            }
            // The manifest line is present in BOTH passes: the Lua's gate
            // (L1459) is a disjunction, so a run id alone forces emission
            // even before the movie's first detour is seen. Its absence is
            // what makes identity (A) identity (A).
            expected.Append("Run manifest: ")
                .Append(Path.Combine(output, "run_manifest.json"))
                .Append('\n');
            return expected.ToString();
        }

        private static string RunCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output,
            string runId)
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
                    + " --run-id " + EndToEndTests.Quote(runId),
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
                    "S3K run-mode capture exited " + result.ExitCode
                    + ". stderr: " + result.StandardError);
            }
            AssertEx.Equal(string.Empty, result.StandardError);
            return result.StandardOutput;
        }

        private static void AssertTextEqual(
            string context, string expected, string actual)
        {
            if (expected == actual)
            {
                return;
            }
            string[] expectedLines = expected.Split('\n');
            string[] actualLines = actual.Split('\n');
            int shared = Math.Min(
                expectedLines.Length, actualLines.Length);
            for (var index = 0; index < shared; index++)
            {
                if (expectedLines[index] != actualLines[index])
                {
                    throw new InvalidOperationException(
                        "First divergence at " + context + " line "
                        + (index + 1) + ": was <"
                        + Truncate(actualLines[index]) + ">; expected <"
                        + Truncate(expectedLines[index]) + ">.");
                }
            }
            throw new InvalidOperationException(
                "First divergence at " + context + ": line count is "
                + actualLines.Length + "; expected "
                + expectedLines.Length + ".");
        }

        private static string Truncate(string value)
        {
            return value.Length <= 240
                ? value
                : value.Substring(0, 240) + "…";
        }

        /// <summary>
        /// Every file this recorder publishes is LF-only and
        /// newline-terminated, in BOTH plain and run mode: identity (C) is
        /// a run-mode Linux capture with LF output, so the S1/S2 "run mode
        /// implies CRLF" rule (ExpandRunNewlines) must never reach an S3K
        /// path.
        /// </summary>
        private static void AssertPublishedShape(
            string context, string text)
        {
            if (text.IndexOf('\r') >= 0)
            {
                throw new InvalidOperationException(
                    context + " contains CR; the S3K complete-run recorder"
                    + " publishes LF in both plain and run mode.");
            }
            if (text.Length != 0
                && !text.EndsWith("\n", StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    context + " is not newline-terminated.");
            }
        }

        /// <summary>
        /// Reads a published or committed text file exactly: no newline
        /// translation, no BOM insertion.
        /// </summary>
        private static string ReadAllTextExact(string path)
        {
            using (var reader = new StreamReader(
                path, new UTF8Encoding(false)))
            {
                return reader.ReadToEnd();
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
            AssertBytes(
                context, expectedLength, expectedSha256, length, actual);
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

        private static string TracesRoot()
        {
            return Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s3k");
        }

        private static Dependencies Resolve(
            string moviePath, IList<string> fixtureDirectories)
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
            foreach (string directory in fixtureDirectories)
            {
                if (!Directory.Exists(directory))
                {
                    missing.Add(
                        "canonical fixture directory is absent: "
                        + directory);
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

        private sealed class SetCCase
        {
            public SetCCase(
                string dirToken,
                string fixtureDirectoryName,
                long physicsLength,
                string physicsSha256,
                long auxStateLength,
                string auxStateSha256)
            {
                DirToken = dirToken;
                FixtureDirectoryName = fixtureDirectoryName;
                PhysicsLength = physicsLength;
                PhysicsSha256 = physicsSha256;
                AuxStateLength = auxStateLength;
                AuxStateSha256 = auxStateSha256;
            }

            public string DirToken { get; private set; }
            public string FixtureDirectoryName { get; private set; }
            public long PhysicsLength { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public long AuxStateLength { get; private set; }
            public string AuxStateSha256 { get; private set; }
        }

        private sealed class SetBCase
        {
            public SetBCase(
                string dirToken,
                string kind,
                int bk2FrameOffset,
                int traceFrameCount,
                long physicsLength,
                string physicsSha256,
                long auxStateLength,
                string auxStateSha256)
            {
                DirToken = dirToken;
                Kind = kind;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                PhysicsLength = physicsLength;
                PhysicsSha256 = physicsSha256;
                AuxStateLength = auxStateLength;
                AuxStateSha256 = auxStateSha256;
            }

            public string DirToken { get; private set; }
            public string Kind { get; private set; }
            public int Bk2FrameOffset { get; private set; }
            public int TraceFrameCount { get; private set; }
            public long PhysicsLength { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public long AuxStateLength { get; private set; }
            public string AuxStateSha256 { get; private set; }
        }
    }
}
