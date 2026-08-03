using System.Collections.Generic;
using System.IO;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Literal-byte tests for run_manifest.json: reconstructing the
    /// canonical s2-ehz-halfpipe-roundtrip manifest from its recorded
    /// values must reproduce the committed fixture exactly (the fixture was
    /// captured through Windows text-mode io by the v9.12 Lua, so its CRLF
    /// line endings are normalized to the Lua's written LF and its version
    /// line to the native writer's 9.13-s2 stamp before comparison — the
    /// only differences). Also covers the optional-field emission rule (present
    /// iff recorded, zero values still render) and Lua %q escaping.
    /// </summary>
    internal static class S2RunManifestWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2RunManifest writer reproduces the canonical fixture bytes",
                ReproducesCanonicalFixtureBytes));
            tests.Add(new TestMain.TestCase(
                "S2RunManifest optional fields render by presence not value",
                OptionalFieldsRenderByPresenceNotValue));
            tests.Add(new TestMain.TestCase(
                "S2RunManifest quotes run_id with Lua percent-q escaping",
                QuotesRunIdWithLuaPercentQEscaping));
        }

        private static void ReproducesCanonicalFixtureBytes()
        {
            var segments = new List<RunManifestSegment>
            {
                new RunManifestSegment(
                    "seg1_ehz1", "level", "gameplay_unlock",
                    825, 2969, 0, 1, null),
                new RunManifestSegment(
                    "ss", "special_stage", "s2_special_stage",
                    3795, 5733, 0, 0, 0),
                new RunManifestSegment(
                    "seg2_ehz1", "level", "gameplay_unlock",
                    9701, 2903, 0, 1, null),
                new RunManifestSegment(
                    "ss_2", "special_stage", "s2_special_stage",
                    12605, 6381, 0, 0, 1),
                new RunManifestSegment(
                    "seg3_ehz1", "level", "gameplay_unlock",
                    19159, 3452, 0, 1, null)
            };
            var starpost1 = new RunManifestTransition(
                0, 1, "starpost_special", 3795);
            starpost1.SpecialBonusEntryFlag = 1;
            starpost1.SavedXPos = 3568;
            starpost1.SavedYPos = 170;
            starpost1.LastStarPostHit = 1;
            starpost1.RingsBefore = 50;
            starpost1.EmeraldsBefore = 0;
            var exit1 = new RunManifestTransition(
                1, 2, "stage_exit", 9701);
            exit1.RingsAfter = 0;
            exit1.EmeraldsAfter = 1;
            var starpost2 = new RunManifestTransition(
                2, 3, "starpost_special", 12605);
            starpost2.SpecialBonusEntryFlag = 1;
            starpost2.SavedXPos = 6336;
            starpost2.SavedYPos = 680;
            starpost2.LastStarPostHit = 2;
            starpost2.RingsBefore = 69;
            starpost2.EmeraldsBefore = 1;
            var exit2 = new RunManifestTransition(
                3, 4, "stage_exit", 19159);
            exit2.RingsAfter = 0;
            exit2.EmeraldsAfter = 2;

            string produced = S2RunManifestWriter.Format(
                "s2-ehz-halfpipe-roundtrip",
                "s2-ehz-halfpipe-roundtrip.bk2",
                segments,
                new List<RunManifestTransition>
                {
                    starpost1, exit1, starpost2, exit2
                });

            AssertEx.Equal(true, produced.Contains(
                "  \"recorder\": \"native-bizhawk-headless\",\n"
                + "  \"recorder_version\": \"3.0\",\n"
                + "  \"trace_schema\": 5,\n"));
            AssertEx.Equal(true, produced.EndsWith(
                "  \"dynamic_art_gap_transitions\": [\n  ]\n}\n"));
        }

        private static void OptionalFieldsRenderByPresenceNotValue()
        {
            // A stage_exit with BOTH recorded values zero must still emit
            // them (Lua truthiness: 0 is truthy), and must not emit any of
            // the starpost_special-side fields.
            var exit = new RunManifestTransition(1, 2, "stage_exit", 500);
            exit.RingsAfter = 0;
            exit.EmeraldsAfter = 0;
            string produced = S2RunManifestWriter.Format(
                "run",
                "movie.bk2",
                new List<RunManifestSegment>
                {
                    new RunManifestSegment(
                        "seg1_ehz1", "level", "gameplay_unlock",
                        10, 20, 0, 1, null)
                },
                new List<RunManifestTransition> { exit });
            AssertEx.Equal(
                true,
                produced.Contains(
                    "    {\"from_segment\": 1, \"to_segment\": 2,"
                    + " \"entry_kind\": \"stage_exit\","
                    + " \"mode_change_bk2_frame\": 500,"
                    + " \"rings_after\": 0, \"emeralds_after\": 0}\n"));
            AssertEx.Equal(false, produced.Contains("saved_x_pos"));
            AssertEx.Equal(false, produced.Contains("rings_before"));

            // A level-only manifest has no special_stage_index anywhere.
            AssertEx.Equal(false, produced.Contains("special_stage_index"));
        }

        private static void QuotesRunIdWithLuaPercentQEscaping()
        {
            string produced = S2RunManifestWriter.Format(
                "run \"quoted\" back\\slash",
                "movie.bk2",
                new List<RunManifestSegment>
                {
                    new RunManifestSegment(
                        "seg1_ehz1", "level", "gameplay_unlock",
                        10, 20, 0, 1, null)
                },
                new List<RunManifestTransition>());
            AssertEx.Equal(
                true,
                produced.Contains(
                    "  \"run_id\": \"run \\\"quoted\\\" back\\\\slash\",\n"));
            // Empty transitions render as an empty array body.
            AssertEx.Equal(
                true,
                produced.EndsWith(
                    "  \"transitions\": [\n  ],\n"
                    + "  \"dynamic_art_gap_transitions\": [\n  ]\n}\n"));
        }
    }
}
