using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Gate for the S3K complete-run PUBLICATION layer (spec
    /// tools/bizhawk-headless/docs/s3k-run-publication.md): the three
    /// metadata.json shapes, run_manifest.json, the driver that streams
    /// rows into a sink, and the staged no-replace publication.
    ///
    /// Four layers:
    ///
    /// 1. metadata.json reproduced BYTE-FOR-BYTE against six committed
    ///    fixtures — two level segments, three bonus segments and one
    ///    special stage — with zero normalization. Every fixture's own
    ///    recording_date is fed in, so these are full-file equality
    ///    assertions rather than "contains" checks, and they cover the
    ///    identity-(A) shape (capture_mode, no run_id) and the
    ///    identity-(C) shape (run_id, v_int_run_count) simultaneously.
    /// 2. run_manifest.json reproduced against the committed (B) manifest
    ///    BYTE FOR BYTE, with zero normalization. The two deltas this gate
    ///    used to pin as literals — CRLF line endings and the 6.31 version
    ///    stamp — were artifacts of a legacy Windows/6.31 capture and were
    ///    removed at the source when commit 63eccd290 re-captured the
    ///    fixture on Linux at 6.32 (spec §0.2.1). That gates all 25 segment
    ///    entries, all 22 transition records, every optional field's
    ///    presence rule and the whole byte layout.
    /// 3. The driver over a synthetic movie and a scripted host: a
    ///    level -> bonus -> level -> special-stage -> level round trip with
    ///    its dir tokens, row counts, per-kind file sets, per-kind metadata
    ///    shapes, transition records, input-column alignment,
    ///    finalize-time sampling and the manifest emission gate in all
    ///    three of its states.
    /// 4. The staged publication: nothing lands under a final name before
    ///    Publish(), the manifest links last, and every published byte is
    ///    LF.
    ///
    /// Hermetic apart from reading the checked-in fixtures' (uncompressed)
    /// metadata.json / run_manifest.json. No ROM, no BizHawk. Nothing under
    /// src/test/resources/traces/ is written.
    /// </summary>
    internal static class S3KCompleteRunPublicationTests
    {
        private const string LogKey =
            "LogKey:#Power|Reset|"
            + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";

        private const string Level = RunManifestSegment.LevelKind;
        private const string Bonus = RunManifestSegment.BonusStageKind;
        private const string Ss = RunManifestSegment.SpecialStageKind;
        private const string LevelProfile =
            S3KCompleteRunSegmenter.LevelTraceProfile;
        private const string BonusProfile =
            S3KCompleteRunSegmenter.BonusStageTraceProfile;
        private const string SsProfile =
            S3KCompleteRunSegmenter.SpecialStageTraceProfile;

        private const string CompleteRunSourceBk2 =
            "s3k-complete-sonic-tails.bk2";
        private const string MultiBonusSourceBk2 =
            "s3-knux-multibonus-ss.bk2";
        private const string MultiBonusRunId = "s3k-multibonus";
        private const string FixtureVersionLine =
            "  \"lua_script_version\": \"6.33-s3k-completerun\",";
        private const string PublishedHardwareVersionLine =
            "  \"lua_script_version\": \"6.35-s3k-completerun\",";
        private const string PublishedDirectPredecessorVersionLine =
            "  \"lua_script_version\": \"6.37-s3k-completerun\",";
        private const string PublishedQueuePredecessorVersionLine =
            "  \"lua_script_version\": \"6.38-s3k-completerun\",";
        private const string CurrentVersionLine =
            "  \"lua_script_version\": \"6.40-s3k-completerun\",";
        private const string FixtureTraceSchemaLine =
            "  \"trace_schema\": 6,";
        private const string CurrentTraceSchemaLine =
            "  \"trace_schema\": 7,";
        private const string HardwareTimingSchemaLine =
            "  \"hardware_timing_schema\": 2,\n";
        private const string LegacyHardwareTimingSchemaLine =
            "  \"hardware_timing_schema\": 1,\n";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunPublication metadata compatibility accepts"
                + " only exact current, published, or legacy shapes",
                MetadataCompatibilityShapesAreExact));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunPublication metadata defaults to schema two"
                + " and selects schema one",
                MetadataDefaultsToSchemaTwoAndSelectsSchemaOne));
            foreach (MetadataFixture fixture in MetadataFixtures)
            {
                MetadataFixture captured = fixture;
                tests.Add(new TestMain.TestCase(
                    "S3KCompleteRunPublication metadata.json reproduces "
                    + captured.Directory + " with approved schema bump",
                    () => MetadataReproducesFixture(captured)));
            }
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunPublication level metadata omits the bonus"
                + " keys and special-stage metadata omits capture_mode",
                ShapeAbsencesAreStructural));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunPublication run_manifest.json reproduces the"
                + " s3-knux-multibonus-ss manifest byte for byte",
                RunManifestReproducesSetB));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunPublication manifest emission gate is"
                + " transition-or-run-id",
                ManifestEmissionGate));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunCaptureRunner publishes a level-bonus-level"
                + "-ss-level round trip",
                CapturesRoundTrip));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunCaptureRunner records direct then module"
                + " queue state for every special-stage row",
                CapturesSpecialStageQueueStatePerStoredRow));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunCaptureRunner reconciles unexported"
                + " special-stage results work across native and Lua",
                CapturesSpecialStageResultsHardwareTiming));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunCaptureRunner aligns each row's input column"
                + " with BK2 row bk2_frame_offset + N",
                AlignsInputColumns));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunCaptureRunner indexes the input column by"
                + " BK2 row across a mid-segment excursion",
                AlignsInputColumnsAcrossMidSegmentExcursion));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunCaptureRunner omits the manifest for a"
                + " detour-free pass without a run id",
                OmitsManifestForDetourFreeUnnamedPass));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunCaptureRunner emits an empty-transitions"
                + " manifest for a detour-free pass with a run id",
                EmitsEmptyTransitionManifestWithRunId));
            tests.Add(new TestMain.TestCase(
                "S3KStagedSegmentSink publishes nothing until the staged"
                + " set is published, then every file as LF",
                StagesEverythingBeforePublishing));
            tests.Add(new TestMain.TestCase(
                "S3KStagedSegmentSink discards a capture that fails"
                + " mid-segment",
                DiscardsFailedCapture));
            tests.Add(new TestMain.TestCase(
                "S3KStagedSegmentSink discards every final path when"
                + " special-stage queue projection fails",
                DiscardsFailedSpecialStageQueueProjection));
        }

        // ------------------------------------------------------------------
        // 1. metadata.json byte gates
        // ------------------------------------------------------------------

        /// <summary>
        /// One committed fixture's metadata.json plus every recorder input
        /// needed to regenerate it. All values are the fixture's own,
        /// transcribed as literals — the point of the gate is that the
        /// writer, fed these, emits the fixture's exact bytes.
        /// </summary>
        private sealed class MetadataFixture
        {
            internal string Directory;
            internal bool SpecialStage;
            internal string ZoneToken;
            internal int ZoneId;
            internal int ActRaw;
            internal int Bk2FrameOffset;
            internal int TraceFrameCount;
            internal int PreTraceOscFrames;
            internal int StartX;
            internal int StartY;
            internal uint RngSeed;
            internal string BonusStageType;
            internal uint? VIntRunCount;
            internal int? SpecialStageIndex;
            internal int SegmentIndex;
            internal int PlayerMode;
            internal string SourceBk2;
            internal string RunId;
            internal string RecordingDate;
        }

        /// <summary>
        /// Identity (A) — the complete-run pass, no run_id, Sonic+Tails
        /// (Player_mode 0) — and identity (C) — the run pass under
        /// --run-id s3k-multibonus, Knuckles solo (Player_mode 3), whose
        /// three bonus dirs carry capture_mode AND v_int_run_count while
        /// special_stage/ carries neither. AIZ and HCZ were republished with
        /// hardware timing on 2026-07-29; the remaining fixtures retain
        /// their 2026-07-25 capture date. These comparisons are FULL-FILE
        /// equality, so each date is injected to match the fixture rather
        /// than allowed to differ.
        /// </summary>
        private static readonly MetadataFixture[] MetadataFixtures =
        {
            new MetadataFixture
            {
                Directory = "aiz_completerun",
                ZoneToken = "aiz",
                ZoneId = 0,
                ActRaw = 0,
                Bk2FrameOffset = 941,
                TraceFrameCount = 26228,
                PreTraceOscFrames = 1,
                StartX = 0x0040,
                StartY = 0x0420,
                RngSeed = 0x00000000,
                SegmentIndex = 0,
                PlayerMode = 0,
                SourceBk2 = CompleteRunSourceBk2,
                RunId = null,
                RecordingDate = "2026-08-01"
            },
            new MetadataFixture
            {
                Directory = "hcz_completerun",
                ZoneToken = "hcz",
                ZoneId = 1,
                ActRaw = 0,
                Bk2FrameOffset = 27170,
                TraceFrameCount = 31482,
                PreTraceOscFrames = 1,
                StartX = 0x0280,
                StartY = 0x0020,
                RngSeed = 0x00000000,
                SegmentIndex = 1,
                PlayerMode = 0,
                SourceBk2 = CompleteRunSourceBk2,
                RunId = null,
                RecordingDate = "2026-08-01"
            },
            new MetadataFixture
            {
                Directory = "bonus_gumball",
                ZoneToken = "gumball",
                ZoneId = 19,
                ActRaw = 0,
                Bk2FrameOffset = 5570,
                TraceFrameCount = 1430,
                PreTraceOscFrames = 1,
                StartX = 0x0100,
                StartY = 0x0120,
                RngSeed = 0x00001598,
                BonusStageType = "gumball",
                VIntRunCount = 5529,
                SegmentIndex = 1,
                PlayerMode = 3,
                SourceBk2 = MultiBonusSourceBk2,
                RunId = MultiBonusRunId,
                RecordingDate = "2026-08-01"
            },
            new MetadataFixture
            {
                Directory = "bonus_slots",
                ZoneToken = "slots",
                ZoneId = 21,
                ActRaw = 0,
                Bk2FrameOffset = 9142,
                TraceFrameCount = 1200,
                PreTraceOscFrames = 1,
                StartX = 0x0460,
                StartY = 0x0360,
                RngSeed = 0x00000000,
                BonusStageType = "slots",
                VIntRunCount = 9097,
                SegmentIndex = 3,
                PlayerMode = 3,
                SourceBk2 = MultiBonusSourceBk2,
                RunId = MultiBonusRunId,
                RecordingDate = "2026-08-01"
            },
            new MetadataFixture
            {
                Directory = "bonus_pachinko",
                ZoneToken = "pachinko",
                ZoneId = 20,
                ActRaw = 0,
                Bk2FrameOffset = 92963,
                TraceFrameCount = 3051,
                PreTraceOscFrames = 1,
                StartX = 0x0140,
                StartY = 0x0DC0,
                RngSeed = 0x00000000,
                BonusStageType = "pachinko",
                VIntRunCount = 92662,
                SegmentIndex = 21,
                PlayerMode = 3,
                SourceBk2 = MultiBonusSourceBk2,
                RunId = MultiBonusRunId,
                RecordingDate = "2026-08-01"
            },
            new MetadataFixture
            {
                Directory = "special_stage",
                SpecialStage = true,
                Bk2FrameOffset = 48174,
                TraceFrameCount = 4630,
                SpecialStageIndex = 0,
                SegmentIndex = 12,
                PlayerMode = 3,
                SourceBk2 = MultiBonusSourceBk2,
                RunId = MultiBonusRunId,
                RecordingDate = "2026-08-01"
            }
        };

        private static void MetadataReproducesFixture(
            MetadataFixture fixture)
        {
            string path = Path.Combine(
                FixtureDirectory(fixture.Directory), "metadata.json");
            if (!File.Exists(path))
            {
                throw new InvalidOperationException(
                    "Checked-in S3K fixture missing: " + path);
            }
            string expected = ReadAllBytesAsText(path);
            // Both fixture sets were captured on Linux/Mono: LF only, no
            // CR anywhere. Pin that here so the LF policy cannot regress
            // into the S1/S2 run-mode CRLF expansion.
            if (expected.IndexOf('\r') >= 0)
            {
                throw new InvalidOperationException(
                    "Fixture " + fixture.Directory
                    + "/metadata.json unexpectedly contains CR; the S3K"
                    + " complete-run recorder publishes LF in both modes.");
            }
            string actual = FormatFixture(fixture);
            actual = NormalizeCurrentMetadataForFixture(expected, actual);
            AssertEx.Equal(expected, actual);
        }

        private static string NormalizeCurrentMetadataForFixture(
            string fixtureText,
            string producedText)
        {
            RequireMetadataShape(
                producedText,
                CurrentVersionLine,
                CurrentTraceSchemaLine,
                true,
                "produced");

            if (HasMetadataShape(
                fixtureText,
                CurrentVersionLine,
                CurrentTraceSchemaLine,
                true))
            {
                return producedText;
            }
            if (HasMetadataShape(
                fixtureText,
                PublishedQueuePredecessorVersionLine,
                CurrentTraceSchemaLine,
                true))
            {
                return producedText.Replace(
                    CurrentVersionLine,
                    PublishedQueuePredecessorVersionLine);
            }
            if (HasMetadataShape(
                fixtureText,
                PublishedDirectPredecessorVersionLine,
                CurrentTraceSchemaLine,
                true))
            {
                return producedText
                    .Replace(
                        CurrentVersionLine,
                        PublishedDirectPredecessorVersionLine)
                    .Replace(
                        HardwareTimingSchemaLine,
                        LegacyHardwareTimingSchemaLine);
            }
            if (HasMetadataShape(
                fixtureText,
                PublishedHardwareVersionLine,
                CurrentTraceSchemaLine,
                true))
            {
                return producedText
                    .Replace(
                        CurrentVersionLine,
                        PublishedHardwareVersionLine)
                    .Replace(
                        HardwareTimingSchemaLine,
                        LegacyHardwareTimingSchemaLine);
            }
            if (HasMetadataShape(
                fixtureText,
                FixtureVersionLine,
                FixtureTraceSchemaLine,
                false))
            {
                return producedText
                    .Replace(CurrentVersionLine, FixtureVersionLine)
                    .Replace(
                        CurrentTraceSchemaLine,
                        FixtureTraceSchemaLine)
                    .Replace(HardwareTimingSchemaLine, "");
            }
            if (HasMetadataShape(
                fixtureText,
                FixtureVersionLine,
                null,
                false))
            {
                return producedText
                    .Replace(CurrentVersionLine, FixtureVersionLine)
                    .Replace(CurrentTraceSchemaLine + "\n", "")
                    .Replace(HardwareTimingSchemaLine, "");
            }

            throw new InvalidOperationException(
                "Fixture metadata has an unknown or mixed S3K complete-run"
                + " publication version/schema shape.");
        }

        private static bool HasMetadataShape(
            string text,
            string versionLine,
            string traceSchemaLine,
            bool hasHardwareTiming)
        {
            int expectedTraceSchemas = traceSchemaLine == null ? 0 : 1;
            return CountOccurrences(text, "\"lua_script_version\":") == 1
                && CountOccurrences(text, versionLine) == 1
                && CountOccurrences(text, "\"trace_schema\":")
                    == expectedTraceSchemas
                && (traceSchemaLine == null
                    || CountOccurrences(text, traceSchemaLine) == 1)
                && CountOccurrences(text, "\"hardware_timing_schema\":")
                    == (hasHardwareTiming ? 1 : 0)
                && (!hasHardwareTiming
                    || CountOccurrences(text, HardwareTimingSchemaLine)
                        + CountOccurrences(
                            text, LegacyHardwareTimingSchemaLine) == 1);
        }

        private static void RequireMetadataShape(
            string text,
            string versionLine,
            string traceSchemaLine,
            bool hasHardwareTiming,
            string owner)
        {
            if (!HasMetadataShape(
                text, versionLine, traceSchemaLine, hasHardwareTiming)
                || (hasHardwareTiming
                    && CountOccurrences(
                        text, HardwareTimingSchemaLine) != 1))
            {
                throw new InvalidOperationException(
                    owner + " metadata does not have the exact current"
                    + " S3K complete-run publication version/schema shape.");
            }
        }

        private static void MetadataCompatibilityShapesAreExact()
        {
            string current = CurrentVersionLine + "\n"
                + CurrentTraceSchemaLine + "\n"
                + HardwareTimingSchemaLine;
            string published = PublishedDirectPredecessorVersionLine + "\n"
                + CurrentTraceSchemaLine + "\n"
                + LegacyHardwareTimingSchemaLine;
            string legacy = FixtureVersionLine + "\n"
                + FixtureTraceSchemaLine + "\n";
            string legacyWithoutSchema = FixtureVersionLine + "\n";

            AssertEx.Equal(
                current,
                NormalizeCurrentMetadataForFixture(current, current));
            AssertEx.Equal(
                published,
                NormalizeCurrentMetadataForFixture(published, current));
            AssertEx.Equal(
                legacy,
                NormalizeCurrentMetadataForFixture(legacy, current));
            AssertEx.Equal(
                legacyWithoutSchema,
                NormalizeCurrentMetadataForFixture(
                    legacyWithoutSchema, current));
            AssertEx.Throws<InvalidOperationException>(
                () => NormalizeCurrentMetadataForFixture(
                    "  \"lua_script_version\":"
                        + " \"9.99-s3k-completerun\",\n"
                        + CurrentTraceSchemaLine + "\n"
                        + HardwareTimingSchemaLine,
                    current),
                "unknown or mixed");
            AssertEx.Throws<InvalidOperationException>(
                () => NormalizeCurrentMetadataForFixture(
                    current.Replace(
                        CurrentVersionLine,
                        CurrentVersionLine + "\n"
                            + PublishedHardwareVersionLine),
                    current),
                "unknown or mixed");
        }

        private static void MetadataDefaultsToSchemaTwoAndSelectsSchemaOne()
        {
            MetadataFixture fixture = MetadataFixtures[0];
            S3KSegmentArm arm = ArmFor(fixture);
            string current = S3KCompleteRunMetadataWriter.Format(
                arm,
                fixture.TraceFrameCount,
                fixture.SourceBk2,
                fixture.RecordingDate,
                fixture.RunId,
                fixture.PlayerMode);
            string legacy = S3KCompleteRunMetadataWriter.Format(
                arm,
                fixture.TraceFrameCount,
                fixture.SourceBk2,
                fixture.RecordingDate,
                fixture.RunId,
                fixture.PlayerMode,
                HardwareTimingEventEngine.LegacySchema);

            AssertContains(
                current,
                "\"lua_script_version\": \"6.40-s3k-completerun\"");
            AssertContains(current, "\"hardware_timing_schema\": 2");
            AssertContains(legacy, "\"hardware_timing_schema\": 1");
        }

        private static string FormatFixture(MetadataFixture fixture)
        {
            S3KSegmentArm arm = ArmFor(fixture);
            return fixture.SpecialStage
                ? S3KCompleteRunMetadataWriter.FormatSpecialStage(
                    arm,
                    fixture.TraceFrameCount,
                    fixture.SourceBk2,
                    fixture.RecordingDate,
                    fixture.RunId,
                    fixture.PlayerMode,
                    HardwareTimingEventEngine.CurrentSchema,
                    true)
                : S3KCompleteRunMetadataWriter.Format(
                    arm,
                    fixture.TraceFrameCount,
                    fixture.SourceBk2,
                    fixture.RecordingDate,
                    fixture.RunId,
                    fixture.PlayerMode,
                    HardwareTimingEventEngine.CurrentSchema,
                    true);
        }

        private static S3KSegmentArm ArmFor(MetadataFixture fixture)
        {
            var arm = new S3KSegmentArm(
                "unused",
                fixture.SpecialStage
                    ? Ss
                    : (fixture.BonusStageType != null ? Bonus : Level),
                fixture.SpecialStage
                    ? SsProfile
                    : (fixture.BonusStageType != null
                        ? BonusProfile
                        : LevelProfile),
                fixture.Bk2FrameOffset,
                fixture.SegmentIndex);
            arm.ZoneToken = fixture.ZoneToken;
            arm.ZoneId = fixture.ZoneId;
            arm.ActRaw = fixture.ActRaw;
            arm.StartX = fixture.StartX;
            arm.StartY = fixture.StartY;
            arm.RngSeed = fixture.RngSeed;
            arm.BonusStageType = fixture.BonusStageType;
            arm.VIntRunCount = fixture.VIntRunCount;
            arm.SpecialStageIndex = fixture.SpecialStageIndex;
            // pre_trace_osc_frames is the FIRST-RECORDED-FRAME sample that
            // overwrites the arm-time one, which is what this field carries
            // by the time metadata is written.
            arm.GameplayFrameCounter = fixture.PreTraceOscFrames;
            return arm;
        }

        /// <summary>
        /// The three shape absences that the spec insists are structural
        /// rather than env/version driven (§3.3): a level segment carries
        /// neither bonus_stage_type nor v_int_run_count even when the RAM
        /// sample exists, and the special-stage shape carries neither
        /// capture_mode nor v_int_run_count even in the very same
        /// --run-id pass whose bonus segments carry both.
        /// </summary>
        private static void ShapeAbsencesAreStructural()
        {
            var level = new MetadataFixture
            {
                ZoneToken = "aiz",
                ZoneId = 0,
                ActRaw = 0,
                Bk2FrameOffset = 941,
                TraceFrameCount = 10,
                PreTraceOscFrames = 1,
                SegmentIndex = 0,
                PlayerMode = 0,
                SourceBk2 = CompleteRunSourceBk2,
                RecordingDate = "2026-07-23"
            };
            S3KSegmentArm levelArm = ArmFor(level);
            // A level arm that (wrongly) carries the bonus-only V-int
            // sample must still omit the key: the emit is gated on the
            // bonus identity, not on the sample being present.
            levelArm.VIntRunCount = 4242;
            string levelJson = S3KCompleteRunMetadataWriter.Format(
                levelArm, 10, CompleteRunSourceBk2, "2026-07-23", null, 0);
            AssertAbsent(levelJson, "bonus_stage_type");
            AssertAbsent(levelJson, "v_int_run_count");
            AssertAbsent(levelJson, "run_id");
            AssertContains(
                levelJson,
                "  \"capture_mode\": \""
                + S3KCompleteRunMetadataWriter.CaptureMode + "\",\n");

            var ss = new MetadataFixture
            {
                SpecialStage = true,
                Bk2FrameOffset = 48174,
                TraceFrameCount = 4630,
                SpecialStageIndex = 0,
                SegmentIndex = 12,
                PlayerMode = 3,
                SourceBk2 = MultiBonusSourceBk2,
                RunId = MultiBonusRunId,
                RecordingDate = "2026-07-23"
            };
            S3KSegmentArm ssArm = ArmFor(ss);
            ssArm.BonusStageType = "gumball";
            ssArm.VIntRunCount = 4242;
            string ssJson =
                S3KCompleteRunMetadataWriter.FormatSpecialStage(
                    ssArm, 4630, MultiBonusSourceBk2, "2026-07-23",
                    MultiBonusRunId, 3);
            AssertAbsent(ssJson, "capture_mode");
            AssertAbsent(ssJson, "v_int_run_count");
            AssertAbsent(ssJson, "bonus_stage_type");
            AssertAbsent(ssJson, "aux_schema_extras");
            AssertAbsent(ssJson, "pre_trace_osc_frames");
            AssertAbsent(ssJson, "\"zone\"");
            AssertAbsent(ssJson, "notes");
            AssertContains(ssJson, "  \"fresh_load\": false,\n");
            AssertContains(
                ssJson, "  \"run_id\": \"" + MultiBonusRunId + "\",\n");
        }

        // ------------------------------------------------------------------
        // 2. run_manifest.json byte gate
        // ------------------------------------------------------------------

        private sealed class TransitionSpec
        {
            internal int From;
            internal int To;
            internal string Kind;
            internal int Frame;
            internal int? SpecialBonusEntryFlag;
            internal int? SavedXPos;
            internal int? SavedYPos;
            internal int? LastStarPostHit;
            internal int? RingsBefore;
            internal int? RingsAfter;
            internal int? EmeraldsBefore;
            internal int? EmeraldsAfter;
        }

        private static RunManifestSegment Segment(
            string dir,
            string kind,
            string profile,
            int offset,
            int rows,
            int zoneId,
            int act,
            int? specialStageIndex,
            string bonusStageType)
        {
            return new RunManifestSegment(
                dir, kind, profile, offset, rows, zoneId, act,
                specialStageIndex, bonusStageType);
        }

        private static TransitionSpec Tx(
            int from,
            int to,
            string kind,
            int frame,
            int? specialBonusEntryFlag,
            int? savedXPos,
            int? savedYPos,
            int? lastStarPostHit,
            int? ringsBefore,
            int? ringsAfter,
            int? emeraldsBefore,
            int? emeraldsAfter)
        {
            return new TransitionSpec
            {
                From = from,
                To = to,
                Kind = kind,
                Frame = frame,
                SpecialBonusEntryFlag = specialBonusEntryFlag,
                SavedXPos = savedXPos,
                SavedYPos = savedYPos,
                LastStarPostHit = lastStarPostHit,
                RingsBefore = ringsBefore,
                RingsAfter = ringsAfter,
                EmeraldsBefore = emeraldsBefore,
                EmeraldsAfter = emeraldsAfter
            };
        }

        /// <summary>
        /// The 25 segments of
        /// src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/run_manifest.json,
        /// transcribed as literals: three kinds, the bonus_stage_type and
        /// special_stage_index extras, the special stages' hardcoded
        /// zone_id 0 / act 0, and the 1-based monotone dir suffixes.
        /// </summary>
        private static readonly RunManifestSegment[] SetBSegments =
        {
            Segment("aiz", Level, LevelProfile, 915, 4654, 0, 1, null, null),
            Segment("gumball", Bonus, BonusProfile, 5570, 1430, 19, 1, null, "gumball"),
            Segment("aiz_2", Level, LevelProfile, 7001, 2140, 0, 1, null, null),
            Segment("slots", Bonus, BonusProfile, 9142, 1200, 21, 1, null, "slots"),
            Segment("aiz_3", Level, LevelProfile, 10343, 7568, 0, 2, null, null),
            Segment("slots_2", Bonus, BonusProfile, 17912, 1278, 21, 1, null, "slots"),
            Segment("aiz_4", Level, LevelProfile, 19191, 3210, 0, 2, null, null),
            Segment("gumball_2", Bonus, BonusProfile, 22402, 1648, 19, 1, null, "gumball"),
            Segment("aiz_5", Level, LevelProfile, 24051, 3631, 0, 2, null, null),
            Segment("hcz", Level, LevelProfile, 27683, 3176, 1, 1, null, null),
            Segment("slots_3", Bonus, BonusProfile, 30860, 5379, 21, 1, null, "slots"),
            Segment("hcz_2", Level, LevelProfile, 36240, 11933, 1, 1, null, null),
            Segment("ss", Ss, SsProfile, 48174, 4630, 0, 0, 0, null),
            Segment("hcz_3", Level, LevelProfile, 54274, 3949, 1, 2, null, null),
            Segment("slots_4", Bonus, BonusProfile, 58224, 1603, 21, 1, null, "slots"),
            Segment("hcz_4", Level, LevelProfile, 59828, 2097, 1, 2, null, null),
            Segment("ss_2", Ss, SsProfile, 61926, 7194, 0, 0, 1, null),
            Segment("hcz_5", Level, LevelProfile, 70590, 3435, 1, 2, null, null),
            Segment("slots_5", Bonus, BonusProfile, 74026, 1791, 21, 1, null, "slots"),
            Segment("hcz_6", Level, LevelProfile, 75818, 8422, 1, 2, null, null),
            Segment("mgz", Level, LevelProfile, 84241, 8721, 2, 1, null, null),
            Segment("pachinko", Bonus, BonusProfile, 92963, 3051, 20, 1, null, "pachinko"),
            Segment("mgz_2", Level, LevelProfile, 96015, 2076, 2, 1, null, null),
            Segment("ss_3", Ss, SsProfile, 98092, 6537, 0, 0, 2, null),
            Segment("mgz_3", Level, LevelProfile, 106104, 8517, 2, 1, null, null)
        };

        /// <summary>
        /// The 22 transition records of the same manifest. Note the index
        /// gaps at 8-&gt;9 and 19-&gt;20: plain level-&gt;level zone changes
        /// (AIZ-&gt;HCZ, HCZ-&gt;MGZ) are boundaries with NO record, which is
        /// why indices are captured at push time and never derived from
        /// array position. Note also last_star_post_hit 0 and
        /// emeralds_before 0 rendering: in Lua 0 is truthy, so a sampled 0
        /// still emits.
        /// </summary>
        private static readonly TransitionSpec[] SetBTransitions =
        {
            Tx(0, 1, "starpost_bonus", 5570, 2, 10104, 604, 0, 59, null, 0, null),
            Tx(1, 2, "stage_exit", 7001, null, null, null, null, null, 69, null, 0),
            Tx(2, 3, "starpost_bonus", 9142, 2, 736, 701, 0, 75, null, 0, null),
            Tx(3, 4, "stage_exit", 10343, null, null, null, null, null, 85, null, 0),
            Tx(4, 5, "starpost_bonus", 17912, 2, 8288, 1688, 0, 32, null, 0, null),
            Tx(5, 6, "stage_exit", 19191, null, null, null, null, null, 34, null, 0),
            Tx(6, 7, "starpost_bonus", 22402, 2, 13904, 1880, 0, 62, null, 0, null),
            Tx(7, 8, "stage_exit", 24051, null, null, null, null, null, 72, null, 0),
            Tx(9, 10, "starpost_bonus", 30860, 2, 6848, 876, 0, 80, null, 0, null),
            Tx(10, 11, "stage_exit", 36240, null, null, null, null, null, 43, null, 0),
            Tx(11, 12, "giant_ring", 48174, 1, 2528, 332, 2, 102, null, 0, null),
            Tx(12, 13, "stage_exit", 54274, null, null, null, null, null, 102, null, 1),
            Tx(13, 14, "starpost_bonus", 58224, 2, 13696, 748, 0, 123, null, 1, null),
            Tx(14, 15, "stage_exit", 59828, null, null, null, null, null, 129, null, 1),
            Tx(15, 16, "giant_ring", 61926, 1, 13696, 748, 3, 163, null, 1, null),
            Tx(16, 17, "stage_exit", 70590, null, null, null, null, null, 163, null, 2),
            Tx(17, 18, "starpost_bonus", 74026, 2, 17088, 872, 0, 168, null, 2, null),
            Tx(18, 19, "stage_exit", 75818, null, null, null, null, null, 71, null, 2),
            Tx(20, 21, "starpost_bonus", 92963, 2, 7832, 948, 0, 47, null, 2, null),
            Tx(21, 22, "stage_exit", 96015, null, null, null, null, null, 98, null, 2),
            Tx(22, 23, "giant_ring", 98092, 1, 7832, 948, 2, 150, null, 2, null),
            Tx(23, 24, "stage_exit", 106104, null, null, null, null, null, 150, null, 3)
        };

        /// <summary>
        /// The formatter's output must equal the committed (B) manifest
        /// exactly, with no normalization at all. Two deltas used to be
        /// pinned here as literals — CRLF, because the legacy fixture was
        /// captured on Windows EmuHawk where io.open text mode expands
        /// every "\n", and a 6.31 version stamp, because commit 9e3ccdb41
        /// hand-edited only the bonus segments' metadata and never rewrote
        /// the manifest. Commit 63eccd290 re-captured the run on Linux at
        /// 6.32 (spec §0.2.1), so both are gone and the folding is deleted
        /// rather than left dormant. The two facts that made it possible
        /// are asserted directly, so a fixture that regressed to CRLF or
        /// to a stale stamp fails here by name.
        /// </summary>
        private static void RunManifestReproducesSetB()
        {
            string path = Path.Combine(
                FixtureDirectory(Path.Combine(
                    "runs", "s3-knux-multibonus-ss")),
                "run_manifest.json");
            if (!File.Exists(path))
            {
                throw new InvalidOperationException(
                    "Checked-in S3K fixture missing: " + path);
            }
            string expected = ReadAllBytesAsText(path);
            if (expected.IndexOf('\r') >= 0)
            {
                throw new InvalidOperationException(
                    "The (B) run manifest carries CR. Every committed S3K"
                    + " fixture is LF and this port publishes LF in both"
                    + " modes (docs/s3k-run-publication.md section 6).");
            }
            const string FixtureManifestVersionLine =
                "  \"lua_script_version\": \"6.40-s3k-completerun\",\n";
            AssertContains(expected, FixtureManifestVersionLine);

            var transitions = new List<RunManifestTransition>();
            foreach (TransitionSpec spec in SetBTransitions)
            {
                var entry = new RunManifestTransition(
                    spec.From, spec.To, spec.Kind, spec.Frame);
                entry.SpecialBonusEntryFlag = spec.SpecialBonusEntryFlag;
                entry.SavedXPos = spec.SavedXPos;
                entry.SavedYPos = spec.SavedYPos;
                entry.LastStarPostHit = spec.LastStarPostHit;
                entry.RingsBefore = spec.RingsBefore;
                entry.RingsAfter = spec.RingsAfter;
                entry.EmeraldsBefore = spec.EmeraldsBefore;
                entry.EmeraldsAfter = spec.EmeraldsAfter;
                transitions.Add(entry);
            }

            string actual = S3KRunManifestWriter.Format(
                    "s3-knux-multibonus-ss",
                    MultiBonusSourceBk2,
                    SetBSegments,
                    transitions);
            const string CurrentManifestVersionLine =
                "  \"lua_script_version\": \"6.40-s3k-completerun\",\n";
            AssertEx.Equal(
                1,
                CountOccurrences(actual, CurrentManifestVersionLine));
            actual = actual.Replace(
                CurrentManifestVersionLine, FixtureManifestVersionLine);
            AssertEx.Equal(expected, actual);
        }

        /// <summary>
        /// The Lua gate is a disjunction (L1459), not a conjunction — a
        /// detour-free run WITH an id still writes a manifest, and a
        /// detour-ful run WITHOUT one writes a manifest with no run_id
        /// line.
        /// </summary>
        private static void ManifestEmissionGate()
        {
            var none = new List<RunManifestTransition>();
            var some = new List<RunManifestTransition>
            {
                new RunManifestTransition(
                    0, 1, RunManifestTransition.StageExitKind, 100)
            };
            AssertEx.Equal(
                false, S3KRunManifestWriter.ShouldEmit(none, null));
            AssertEx.Equal(
                true, S3KRunManifestWriter.ShouldEmit(none, "run"));
            AssertEx.Equal(
                true, S3KRunManifestWriter.ShouldEmit(some, null));
            AssertEx.Equal(
                true, S3KRunManifestWriter.ShouldEmit(some, "run"));

            // Empty arrays render as an open bracket line followed by the
            // close line; the game literal is "s3k", never "sonic3k".
            string json = S3KRunManifestWriter.Format(
                "run",
                "movie.bk2",
                new RunManifestSegment[0],
                none);
            AssertEx.Equal(
                "{\n"
                + "  \"run_schema\": 1,\n"
                + "  \"game\": \"s3k\",\n"
                + "  \"run_id\": \"run\",\n"
                + "  \"source_bk2\": \"movie.bk2\",\n"
                + "  \"rom_checksum\": \""
                + S3KCompleteRunMetadataWriter.RomChecksum + "\",\n"
                + "  \"lua_script_version\": \""
                + S3KCompleteRunMetadataWriter.LuaScriptVersion + "\",\n"
                + "  \"segments\": [\n"
                + "  ],\n"
                + "  \"transitions\": [\n"
                + "  ]\n"
                + "}\n",
                json);

            // No --run-id: the key is absent, not null.
            AssertAbsent(
                S3KRunManifestWriter.Format(
                    null, "movie.bk2", new RunManifestSegment[0], some),
                "run_id");
        }

        // ------------------------------------------------------------------
        // 3. Driver over a synthetic movie
        // ------------------------------------------------------------------

        /// <summary>
        /// Buffered sink for the tests: exactly what production must NOT do
        /// at 266 MB a segment, but the only practical way to assert the
        /// bytes of a 30-frame capture.
        /// </summary>
        private sealed class RecordingSink : IS3KCompleteRunSegmentSink
        {
            internal readonly List<string> DirTokens = new List<string>();
            internal readonly List<string> Physics = new List<string>();
            internal readonly List<string> Aux = new List<string>();
            internal readonly List<string> Timing = new List<string>();
            internal readonly List<string> Metadata = new List<string>();
            internal readonly List<RunManifestSegment> Entries =
                new List<RunManifestSegment>();

            private StringWriter physics;
            private StringWriter aux;
            private StringWriter timing;

            public S3KSegmentStreams BeginSegment(S3KSegmentArm arm)
            {
                DirTokens.Add(arm.DirToken);
                physics = new StringWriter(CultureInfo.InvariantCulture);
                aux = new StringWriter(CultureInfo.InvariantCulture);
                timing = new StringWriter(CultureInfo.InvariantCulture);
                return new S3KSegmentStreams(physics, aux, timing);
            }

            public void EndSegment(
                RunManifestSegment entry, string metadataJson)
            {
                Entries.Add(entry);
                Physics.Add(physics.ToString());
                Aux.Add(aux.ToString());
                Timing.Add(timing.ToString());
                Metadata.Add(metadataJson);
                physics = null;
                aux = null;
                timing = null;
            }
        }

        /// <summary>
        /// One (Game_mode, zone, act, special-stage index) leg of a
        /// synthetic frame stream. Frames outside every leg read Game_mode
        /// 0x00 and can never arm.
        /// </summary>
        private sealed class Leg
        {
            internal int First;
            internal int Last;
            internal byte GameMode;
            internal byte ZoneId;
            internal byte ActRaw;
            internal byte SpecialStageIndex;
        }

        private static readonly Leg[] RoundTripPlan =
        {
            // aiz arms at 4 (arm frame is recorded by no segment), rows 5-9.
            NewLeg(4, 9, S3KRam.GameModeLevel, 0, 0, 0),
            // gumball (zone 0x13) arms at 10, rows 11-14.
            NewLeg(10, 14, S3KRam.GameModeLevel, 0x13, 0, 0),
            // back to aiz -> aiz_2 arms at 15, rows 16-19.
            NewLeg(15, 19, S3KRam.GameModeLevel, 0, 0, 0),
            // special stage: entry frame 20 opens ss, rows 21-24.
            NewLeg(20, 24, S3KRam.GameModeSpecialStage, 0, 0, 7),
            // hcz arms at 25 on the same frame the detour closes,
            // rows 26-39 (the movie's 40 input rows stop it at frame 40).
            NewLeg(25, 45, S3KRam.GameModeLevel, 1, 1, 0)
        };

        private static Leg NewLeg(
            int first,
            int last,
            int gameMode,
            int zoneId,
            int actRaw,
            int specialStageIndex)
        {
            return new Leg
            {
                First = first,
                Last = last,
                GameMode = (byte)gameMode,
                ZoneId = (byte)zoneId,
                ActRaw = (byte)actRaw,
                SpecialStageIndex = (byte)specialStageIndex
            };
        }

        private static FakeS1Host RoundTripHost(
            bool failSpecialStageQueueProjection = false)
        {
            return new FakeS1Host((host, frame) =>
            {
                Leg active = null;
                foreach (Leg leg in RoundTripPlan)
                {
                    if (leg.First <= frame && frame <= leg.Last)
                    {
                        active = leg;
                        break;
                    }
                }
                host.Ram[S3KRam.GameMode] =
                    active == null ? (byte)0 : active.GameMode;
                host.Ram[S3KRam.Zone] =
                    active == null ? (byte)0 : active.ZoneId;
                host.Ram[S3KRam.Act] =
                    active == null ? (byte)0 : active.ActRaw;
                host.Ram[S3KRam.CurrentSpecialStage] =
                    active == null ? (byte)0 : active.SpecialStageIndex;
                host.SetU16(
                    S3KRam.PlayerBase + S3KRam.OffMoveLock, 0);
                host.Ram[S3KRam.Ctrl1Locked] = 0;
                // Knuckles solo, so the character triple is not the
                // Sonic+Tails default.
                host.SetU16(S3KRam.PlayerMode, 3);
                // Free-running V-int counter: sampled at a BONUS arm only.
                host.SetU32(S3KRam.VIntRunCount, (uint)(frame * 7));
                // Transition RAM, distinguishable per frame.
                host.Ram[S3KRam.SpecialBonusEntryFlag] =
                    active != null
                    && active.GameMode == S3KRam.GameModeSpecialStage
                        ? (byte)1
                        : (byte)2;
                host.SetU16(S3KRam.RingCount, (ushort)frame);
                host.Ram[S3KRam.EmeraldCount] = (byte)(frame / 10);
                host.SetU16(S3KRam.SavedXPos, (ushort)(frame + 100));
                host.SetU16(S3KRam.SavedYPos, (ushort)(frame + 200));
                host.Ram[S3KRam.LastStarPostHit] = (byte)(frame % 5);
                host.IsLagged = frame == 23;
                if (failSpecialStageQueueProjection
                    && active != null
                    && active.GameMode == S3KRam.GameModeSpecialStage
                    && frame >= 21)
                {
                    host.Ram[S3KRam.KosModulesLeft] = 0x81;
                    host.SetU32(
                        S3KRam.KosModuleQueue
                            + S3KRam.KosModuleQueueEntrySize,
                        0x100);
                }
            });
        }

        private static void CapturesSpecialStageQueueStatePerStoredRow()
        {
            WithMovie(MaskRows(40), movie =>
            {
                var sink = new RecordingSink();
                S3KCompleteRunCaptureRunner.Capture(
                    movie,
                    RoundTripHost(),
                    MultiBonusRunId,
                    "synthetic.bk2",
                    "2026-07-24",
                    0,
                    new byte[0],
                    sink,
                    true);

                string[] lines = sink.Aux[3].Split(
                    new[] { '\n' },
                    StringSplitOptions.RemoveEmptyEntries);
                AssertEx.Equal(8, lines.Length);
                for (var row = 0; row < 4; row++)
                {
                    AssertContains(
                        lines[row * 2],
                        "\"frame\":" + row
                        + ",\"event\":\"load_queue_state\","
                        + "\"kind\":\"s3k_kos_direct\"");
                    AssertContains(
                        lines[row * 2 + 1],
                        "\"frame\":" + row
                        + ",\"event\":\"load_queue_state\","
                        + "\"kind\":\"s3k_kos_module\"");
                }
                AssertContains(
                    sink.Physics[3].Split('\n')[3],
                    ",1,");
                AssertContains(
                    sink.Metadata[3],
                    "\"aux_schema_extras\": [\"load_queue_state_per_frame\"]");
            });
        }

        private static void CapturesRoundTrip()
        {
            WithMovie(MaskRows(40), movie =>
            {
                var sink = new RecordingSink();
                var host = RoundTripHost();
                S3KCompleteRunCaptureResult result =
                    S3KCompleteRunCaptureRunner.Capture(
                        movie,
                        host,
                        MultiBonusRunId,
                        "synthetic.bk2",
                        "2026-07-24",
                        0,
                        sink);

                AssertEx.Equal(
                    (uint?)HardwareTimingEventEngine.ModuleChildSubmissionPc,
                    host.ExecuteCallbackAddress);
                AssertEx.Equal(true, host.ExecuteCallbackDisposed);
                AssertEx.Equal(5, result.Segments.Count);
                AssertSegment(
                    result.Segments[0], "aiz", Level, LevelProfile,
                    4, 5, 0, 1, null, null);
                AssertSegment(
                    result.Segments[1], "gumball", Bonus, BonusProfile,
                    10, 4, 0x13, 1, null, "gumball");
                AssertSegment(
                    result.Segments[2], "aiz_2", Level, LevelProfile,
                    15, 4, 0, 1, null, null);
                AssertSegment(
                    result.Segments[3], "ss", Ss, SsProfile,
                    20, 4, 0, 0, 7, null);
                AssertSegment(
                    result.Segments[4], "hcz", Level, LevelProfile,
                    25, 14, 1, 2, null, null);

                // Every arm went through the sink in the same order.
                AssertEx.Equal(5, sink.DirTokens.Count);
                AssertEx.Equal("aiz", sink.DirTokens[0]);
                AssertEx.Equal("ss", sink.DirTokens[3]);

                // Level/bonus segments: the 42-column header plus one row
                // each; special-stage segments: the 20-column header.
                AssertHeaderAndRows(
                    sink.Physics[0], S3KTraceCsvWriter.Header, 5);
                AssertHeaderAndRows(
                    sink.Physics[1], S3KTraceCsvWriter.Header, 4);
                AssertHeaderAndRows(
                    sink.Physics[3], S3KSpecialStageCsvWriter.Header, 4);
                AssertHeaderAndRows(
                    sink.Physics[4], S3KTraceCsvWriter.Header, 14);

                // With load-queue capture disabled, a special-stage segment
                // has no profile aux events; the opened empty file is still
                // published.
                AssertEx.Equal(string.Empty, sink.Aux[3]);
                AssertEx.Equal(true, sink.Aux[0].Length > 0);

                // Nothing anywhere is CR: S3K is LF in both modes.
                foreach (string content in sink.Physics)
                {
                    AssertEx.Equal(-1, content.IndexOf('\r'));
                }
                foreach (string content in sink.Metadata)
                {
                    AssertEx.Equal(-1, content.IndexOf('\r'));
                }

                // metadata: level shape, first-recorded-frame
                // pre_trace_osc_frames (5, not the arm-time 4 — FakeS1Host
                // stamps Level_frame_counter with the completed frame), and
                // the finalize-time Player_mode.
                AssertContains(sink.Metadata[0], "  \"zone\": \"aiz\",\n");
                AssertContains(
                    sink.Metadata[0], "  \"pre_trace_osc_frames\": 5,\n");
                AssertContains(
                    sink.Metadata[0], "  \"trace_frame_count\": 5,\n");
                AssertContains(
                    sink.Metadata[0], "  \"segment_index\": 0,\n");
                AssertContains(
                    sink.Metadata[0],
                    "  \"characters\": [\"knuckles\"],\n");
                AssertContains(
                    sink.Metadata[0],
                    "  \"run_id\": \"" + MultiBonusRunId + "\",\n");

                // metadata: bonus shape, with the V-int counter sampled at
                // the ARM frame (10 * 7), not at finalize.
                AssertContains(
                    sink.Metadata[1], "  \"zone\": \"gumball\",\n");
                AssertContains(
                    sink.Metadata[1],
                    "  \"trace_profile\": \"s3k_bonus_stage\",\n"
                    + "  \"bonus_stage_type\": \"gumball\",\n"
                    + "  \"v_int_run_count\": 70,\n");

                // metadata: special-stage shape.
                AssertContains(
                    sink.Metadata[3],
                    "  \"trace_profile\": \"s3k_special_stage\",\n"
                    + "  \"special_stage_index\": 7,\n"
                    + "  \"ss_csv_version\": 1,\n");
                AssertAbsent(sink.Metadata[3], "capture_mode");

                // Transitions: stage_exit is pushed at the RETURN level
                // arm, starpost_bonus at the bonus arm, giant_ring at the
                // special-stage open; a plain level->level boundary emits
                // none, which is why there are four records for five
                // segments.
                AssertEx.Equal(4, result.Transitions.Count);
                AssertTransition(
                    result.Transitions[0], 0, 1,
                    RunManifestTransition.StarpostBonusKind, 10);
                AssertEx.Equal(
                    2,
                    result.Transitions[0].SpecialBonusEntryFlag ?? -1);
                AssertEx.Equal(
                    10, result.Transitions[0].RingsBefore ?? -1);
                AssertEx.Equal(
                    true, result.Transitions[0].RingsAfter == null);
                AssertTransition(
                    result.Transitions[1], 1, 2,
                    RunManifestTransition.StageExitKind, 15);
                AssertEx.Equal(
                    true,
                    result.Transitions[1].SpecialBonusEntryFlag == null);
                AssertEx.Equal(
                    15, result.Transitions[1].RingsAfter ?? -1);
                AssertTransition(
                    result.Transitions[2], 2, 3,
                    RunManifestTransition.GiantRingKind, 20);
                AssertEx.Equal(
                    1,
                    result.Transitions[2].SpecialBonusEntryFlag ?? -1);
                AssertTransition(
                    result.Transitions[3], 3, 4,
                    RunManifestTransition.StageExitKind, 25);

                // The manifest lists every segment, including the bonus
                // extra and the special-stage extra.
                AssertContains(
                    result.RunManifestJson,
                    "    {\"dir\": \"gumball\", \"kind\": \"bonus_stage\","
                    + " \"trace_profile\": \"s3k_bonus_stage\","
                    + " \"bk2_frame_offset\": 10, \"trace_frame_count\": 4,"
                    + " \"zone_id\": 19, \"act\": 1,"
                    + " \"bonus_stage_type\": \"gumball\"},\n");
                AssertContains(
                    result.RunManifestJson,
                    "    {\"dir\": \"ss\", \"kind\": \"special_stage\","
                    + " \"trace_profile\": \"s3k_special_stage\","
                    + " \"bk2_frame_offset\": 20, \"trace_frame_count\": 4,"
                    + " \"zone_id\": 0, \"act\": 0,"
                    + " \"special_stage_index\": 7},\n");
            });
        }

        private static void CapturesSpecialStageResultsHardwareTiming()
        {
            const int firstSource = 0x100;
            const int destination = 0x6000;
            byte[] rom = FiveSingleModuleArchives(firstSource);
            WithMovie(MaskRows(26), movie =>
            {
                var host = new FakeS1Host((h, frame) =>
                {
                    h.Ram[S3KRam.GameMode] = frame >= 4 && frame <= 7
                        ? (byte)S3KRam.GameModeLevel
                        : frame >= 8 && frame <= 10
                            ? (byte)S3KRam.GameModeSpecialStage
                            : frame >= 11 && frame <= 20
                                ? (byte)S3KRam.GameModeSpecialStageResults
                                : frame >= 21
                                    ? (byte)S3KRam.GameModeLevel
                                    : (byte)0;
                    h.Ram[S3KRam.Zone] = frame >= 21 ? (byte)1 : (byte)0;
                    h.SetU16(
                        S3KRam.PlayerBase + S3KRam.OffMoveLock, 0);
                    h.Ram[S3KRam.Ctrl1Locked] = 0;
                    h.SetU16(S3KRam.LevelFrameCounter, 0x4444);
                    ClearKosQueue(h);
                    if (frame >= 11 && frame <= 18)
                    {
                        int lifecycle = (frame - 11) / 2;
                        if (((frame - 11) & 1) == 0)
                        {
                            int source = firstSource + lifecycle * 0x20;
                            h.Ram[S3KRam.KosModulesLeft] = 0x81;
                            h.SetU32(
                                S3KRam.KosModuleQueue,
                                (uint)(source + 2));
                            h.SetU16(
                                S3KRam.KosModuleDestination,
                                (ushort)(destination + lifecycle * 0x1000));
                        }
                    }
                    else if (frame == 22)
                    {
                        h.Ram[S3KRam.KosModulesLeft] = 0x81;
                        h.SetU32(
                            S3KRam.KosModuleQueue,
                            (uint)(firstSource + 4 * 0x20 + 2));
                        h.SetU16(
                            S3KRam.KosModuleDestination,
                            (ushort)(destination + 4 * 0x1000));
                    }
                });
                var sink = new RecordingSink();
                S3KCompleteRunCaptureResult result =
                    S3KCompleteRunCaptureRunner.Capture(
                    movie,
                    host,
                    "results-timing",
                    "synthetic.bk2",
                    "2026-07-27",
                    0,
                    rom,
                    sink);

                AssertEx.Equal(3, sink.Timing.Count);
                AssertEx.Equal(string.Empty, sink.Timing[1]);
                string[] events = sink.Timing[2].Split(
                    new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
                AssertEx.Equal(1, events.Length);
                string fingerprint =
                    HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                        "KOS_MODULE_QUEUE",
                        firstSource + 4 * 0x20,
                        7,
                        destination + 4 * 0x1000,
                        1,
                        "kosinski_moduled",
                        1);
                AssertEx.Equal(
                    "{\"event\":\"hardware_work_completed\","
                    + "\"raw_frame\":1,"
                    // Results mode leaves Level_frame_counter unchanged,
                    // so frame-end observation proves only VInt admission.
                    + "\"boundary\":\"vint_service\","
                    + "\"kind\":\"kos_module_queue\","
                    + "\"ordinal\":4,"
                    + "\"submission_fingerprint\":\""
                    + fingerprint + "\"}",
                    events[0]);

                for (var segment = 0;
                    segment < sink.Timing.Count;
                    segment++)
                {
                    foreach (string line in sink.Timing[segment].Split(
                        new[] {'\n'},
                        StringSplitOptions.RemoveEmptyEntries))
                    {
                        int rawFrame = JsonInteger(line, "\"raw_frame\":");
                        AssertEx.Equal(
                            true,
                            rawFrame >= 0
                                && rawFrame
                                    < result.Segments[segment]
                                        .TraceFrameCount);
                    }
                }
                AssertLuaResultsParity(sink.Timing[2]);
            });
        }

        private static byte[] FiveSingleModuleArchives(int firstSource)
        {
            var rom = new byte[0x200];
            for (var index = 0; index < 5; index++)
            {
                int source = firstSource + index * 0x20;
                rom[source] = 0x00;
                rom[source + 1] = 0x01;
                rom[source + 2] = 0x02;
            }
            return rom;
        }

        private static int JsonInteger(string json, string key)
        {
            int start = json.IndexOf(key, StringComparison.Ordinal);
            if (start < 0)
            {
                throw new InvalidOperationException(
                    "Missing JSON integer key " + key + " in " + json);
            }
            start += key.Length;
            int end = start;
            while (end < json.Length
                && json[end] >= '0' && json[end] <= '9')
            {
                end++;
            }
            return int.Parse(
                json.Substring(start, end - start),
                CultureInfo.InvariantCulture);
        }

        private static void AssertLuaResultsParity(string nativeOutput)
        {
            const string lua = "/usr/bin/lua";
            if (!File.Exists(lua))
            {
                throw new TestMain.SkipTestException(
                    "/usr/bin/lua is absent; native results invariants"
                    + " passed but the Lua byte differential cannot run.");
            }
            string root = TestScratch.CreateRootPath(
                "s3k-results-lua-parity");
            string harness = Path.Combine(root, "harness.lua");
            string output = Path.Combine(root, "timing.jsonl");
            try
            {
                Directory.CreateDirectory(root);
                File.WriteAllText(
                    harness,
                    "local module_path, output_path = arg[1], arg[2]\n"
                    + "local ram, rom = {}, {}\n"
                    + "mainmemory = {}\n"
                    + "function mainmemory.read_u8(a) return ram[a] or 0 end\n"
                    + "function mainmemory.read_u16_be(a) return"
                    + " ((ram[a] or 0)<<8)|(ram[a+1] or 0) end\n"
                    + "function mainmemory.read_u32_be(a) return"
                    + " ((ram[a] or 0)<<24)|((ram[a+1] or 0)<<16)|"
                    + "((ram[a+2] or 0)<<8)|(ram[a+3] or 0) end\n"
                    + "memory = {}\n"
                    + "function memory.read_u8(a, domain)"
                    + " return rom[a] or 0 end\n"
                    + "local function put16(a,v) ram[a]=(v>>8)&255;"
                    + " ram[a+1]=v&255 end\n"
                    + "local function put32(a,v) ram[a]=(v>>24)&255;"
                    + " ram[a+1]=(v>>16)&255;"
                    + " ram[a+2]=(v>>8)&255; ram[a+3]=v&255 end\n"
                    + "local function clear() for i=0,23 do"
                    + " ram[0xFF64+i]=0 end; ram[0xFF60]=0 end\n"
                    + "local function active(source,dest) clear();"
                    + " ram[0xFF60]=0x81; put32(0xFF64,source+2);"
                    + " put16(0xFF68,dest) end\n"
                    + "for i=0,4 do local s=0x100+i*0x20;"
                    + " rom[s]=0; rom[s+1]=1; rom[s+2]=2 end\n"
                    + "local M=assert(loadfile(module_path))()\n"
                    + "local tracker=M.new_tracker()\n"
                    + "for i=0,3 do active(0x100+i*0x20,"
                    + "0x6000+i*0x1000); M.observe(tracker,0,nil);"
                    + " clear(); M.observe(tracker,0,nil) end\n"
                    + "local out=assert(io.open(output_path,'w'))\n"
                    + "active(0x180,0xA000); M.observe(tracker,0,out);"
                    + " clear(); M.observe(tracker,1,out); out:close()\n",
                    new UTF8Encoding(false));
                var start = new ProcessStartInfo
                {
                    FileName = lua,
                    Arguments = harness + " "
                        + Path.Combine(
                            EndToEndTests.RepositoryRoot,
                            "tools", "bizhawk", "lib",
                            "oggf_hardware_timing.lua")
                        + " " + output,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                };
                using (Process process = Process.Start(start))
                {
                    string stdout = process.StandardOutput.ReadToEnd();
                    string stderr = process.StandardError.ReadToEnd();
                    process.WaitForExit();
                    if (process.ExitCode != 0)
                    {
                        throw new InvalidOperationException(
                            "Lua timing differential failed: " + stderr
                            + stdout);
                    }
                }
                AssertEx.Equal(nativeOutput, File.ReadAllText(output));
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static void ClearKosQueue(FakeS1Host host)
        {
            host.Ram[S3KRam.KosModulesLeft] = 0;
            for (var offset = 0;
                offset < S3KRam.KosModuleQueueCapacity
                    * S3KRam.KosModuleQueueEntrySize;
                offset++)
            {
                host.Ram[S3KRam.KosModuleQueue + offset] = 0;
            }
        }

        /// <summary>
        /// Row N's input column is BK2 input row bk2_frame_offset + N —
        /// the row consumed by the advance immediately before the row is
        /// observed, NOT the row that will be consumed next. Every
        /// synthetic movie row here carries a distinct 5-bit mask, so a
        /// one-frame slip in either direction fails.
        /// </summary>
        private static void AlignsInputColumns()
        {
            WithMovie(MaskRows(40), movie =>
            {
                var sink = new RecordingSink();
                S3KCompleteRunCaptureResult result =
                    S3KCompleteRunCaptureRunner.Capture(
                        movie, RoundTripHost(), null, "synthetic.bk2",
                        "2026-07-24", 0, sink);

                for (var index = 0; index < result.Segments.Count; index++)
                {
                    RunManifestSegment entry = result.Segments[index];
                    string[] lines = sink.Physics[index]
                        .Split(new[] { '\n' }, StringSplitOptions.None);
                    for (var row = 0; row < entry.TraceFrameCount; row++)
                    {
                        string[] columns = lines[row + 1].Split(',');
                        int expected =
                            MaskForRow(entry.Bk2FrameOffset + row);
                        // The level writer emits %04X, the special-stage
                        // writer lowercase unpadded hex.
                        int actual = int.Parse(
                            columns[1],
                            NumberStyles.HexNumber,
                            CultureInfo.InvariantCulture);
                        if (actual != expected)
                        {
                            throw new InvalidOperationException(
                                "Segment " + entry.Dir + " row " + row
                                + " input column was 0x"
                                + actual.ToString("X")
                                + "; expected BK2 row "
                                + (entry.Bk2FrameOffset + row) + "'s 0x"
                                + expected.ToString("X") + ".");
                        }
                    }
                }
            });
        }

        /// <summary>
        /// The same invariant where "row N" and "the previous advance" are
        /// NOT the same BK2 row. Segmenter step (10) suppresses the row on
        /// a non-level-family Game_mode while leaving the segment armed
        /// (game-over/continue, a pause+A soft reset to the title, an
        /// ending/credits excursion), and the arm gate is one-time per
        /// zone, so returning to the SAME zone resumes into the already
        /// open segment. From that point on the last-applied BK2 row runs
        /// ahead of bk2_frame_offset + N by the excursion length, and the
        /// Lua's index — movie.getinput(bk2_frame_offset + trace_row, 1) —
        /// is the authority. No canonical fixture segment skips a frame, so
        /// only a synthetic plan can pin this.
        /// </summary>
        private static void AlignsInputColumnsAcrossMidSegmentExcursion()
        {
            WithMovie(MaskRows(40), movie =>
            {
                var sink = new RecordingSink();
                S3KCompleteRunCaptureResult result =
                    S3KCompleteRunCaptureRunner.Capture(
                        movie,
                        ExcursionHost(),
                        null,
                        "synthetic.bk2",
                        "2026-07-24",
                        0,
                        sink);

                // One segment, armed at frame 4 and never closed by the
                // 0x08 excursion at frames 10-14: rows are frames 5-9 and
                // 15-39, so 30 rows rather than the 35 a contiguous run
                // would have produced.
                AssertEx.Equal(1, result.Segments.Count);
                RunManifestSegment entry = result.Segments[0];
                AssertEx.Equal("aiz", entry.Dir);
                AssertEx.Equal(4, entry.Bk2FrameOffset);
                AssertEx.Equal(30, entry.TraceFrameCount);

                string[] lines = sink.Physics[0]
                    .Split(new[] { '\n' }, StringSplitOptions.None);
                for (var row = 0; row < entry.TraceFrameCount; row++)
                {
                    string[] columns = lines[row + 1].Split(',');
                    int expected = MaskForRow(entry.Bk2FrameOffset + row);
                    int actual = int.Parse(
                        columns[1],
                        NumberStyles.HexNumber,
                        CultureInfo.InvariantCulture);
                    if (actual != expected)
                    {
                        throw new InvalidOperationException(
                            "Row " + row + " input column was 0x"
                            + actual.ToString("X") + "; expected BK2 row "
                            + (entry.Bk2FrameOffset + row) + "'s 0x"
                            + expected.ToString("X")
                            + " (indexing by last-applied frame would give"
                            + " 0x"
                            + MaskForRow(row < 5 ? row + 4 : row + 9)
                                .ToString("X") + ").");
                    }
                }
            });
        }

        /// <summary>
        /// AIZ arms at frame 4 (rows 0-4 at frames 5-9), a five-frame
        /// attract-mode 0x08 excursion at frames 10-14 skips rows without
        /// closing the segment, then AIZ — the SAME zone, so the one-time
        /// arm gate cannot re-fire — records rows 5-29 at frames 15-39.
        /// </summary>
        private static FakeS1Host ExcursionHost()
        {
            return new FakeS1Host((host, frame) =>
            {
                byte mode = 0;
                if (frame >= 4 && frame <= 9)
                {
                    mode = (byte)S3KRam.GameModeLevel;
                }
                else if (frame >= 10 && frame <= 14)
                {
                    // 0x08: attract-mode demo, deliberately NOT
                    // level-family for recording purposes.
                    mode = 0x08;
                }
                else if (frame >= 15)
                {
                    mode = (byte)S3KRam.GameModeLevel;
                }
                host.Ram[S3KRam.GameMode] = mode;
                host.Ram[S3KRam.Zone] = 0;
                host.Ram[S3KRam.Act] = 0;
                host.SetU16(S3KRam.PlayerBase + S3KRam.OffMoveLock, 0);
                host.Ram[S3KRam.Ctrl1Locked] = 0;
                host.SetU16(S3KRam.PlayerMode, 0);
            });
        }

        private static void OmitsManifestForDetourFreeUnnamedPass()
        {
            WithMovie(MaskRows(20), movie =>
            {
                var sink = new RecordingSink();
                S3KCompleteRunCaptureResult result =
                    S3KCompleteRunCaptureRunner.Capture(
                        movie, DetourFreeHost(), null, "synthetic.bk2",
                        "2026-07-24", 0, sink);
                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(0, result.Transitions.Count);
                AssertEx.Equal(true, result.RunManifestJson == null);
                AssertAbsent(sink.Metadata[0], "run_id");
            });
        }

        private static void EmitsEmptyTransitionManifestWithRunId()
        {
            WithMovie(MaskRows(20), movie =>
            {
                var sink = new RecordingSink();
                S3KCompleteRunCaptureResult result =
                    S3KCompleteRunCaptureRunner.Capture(
                        movie, DetourFreeHost(), "named", "synthetic.bk2",
                        "2026-07-24", 0, sink);
                AssertEx.Equal(0, result.Transitions.Count);
                AssertEx.Equal(true, result.RunManifestJson != null);
                AssertContains(
                    result.RunManifestJson, "  \"run_id\": \"named\",\n");
                AssertContains(
                    result.RunManifestJson, "  \"transitions\": [\n  ]\n}\n");
                AssertContains(
                    sink.Metadata[0], "  \"run_id\": \"named\",\n");
            });
        }

        private static FakeS1Host DetourFreeHost()
        {
            return new FakeS1Host((host, frame) =>
            {
                host.Ram[S3KRam.GameMode] = frame >= 4
                    ? (byte)S3KRam.GameModeLevel
                    : (byte)0;
                host.Ram[S3KRam.Zone] = 0;
                host.Ram[S3KRam.Act] = 0;
                host.SetU16(S3KRam.PlayerBase + S3KRam.OffMoveLock, 0);
                host.Ram[S3KRam.Ctrl1Locked] = 0;
                host.SetU16(S3KRam.PlayerMode, 0);
            });
        }

        // ------------------------------------------------------------------
        // 4. Staged publication
        // ------------------------------------------------------------------

        private static void StagesEverythingBeforePublishing()
        {
            WithMovie(MaskRows(40), movie => WithOutputDirectory(output =>
            {
                var publisher = new NoReplacePublisher();
                NoReplacePublisher.StagedPublicationSet staged;
                S3KCompleteRunCaptureResult result;
                using (NoReplacePublisher.IncrementalStagingSession session =
                    publisher.OpenSession(output))
                using (var sink = new S3KStagedSegmentSink(session))
                {
                    result = S3KCompleteRunCaptureRunner.Capture(
                        movie, RoundTripHost(), MultiBonusRunId,
                        "synthetic.bk2", "2026-07-24", 0,
                        new byte[0], sink, true);
                    if (result.RunManifestJson != null)
                    {
                        session.StageFile(
                            "run_manifest.json", result.RunManifestJson);
                    }
                    // Nothing under a final name yet: every staged file is
                    // still a ".tmp." sibling.
                    AssertNoFinalOutputs(output);
                    staged = session.Complete();
                }
                using (staged)
                {
                    AssertNoFinalOutputs(output);
                    staged.Publish();
                }

                foreach (RunManifestSegment entry in result.Segments)
                {
                    string dir = Path.Combine(output, entry.Dir);
                    AssertFilePublished(
                        Path.Combine(dir, "physics.csv"));
                    AssertFilePublished(
                        Path.Combine(dir, "aux_state.jsonl"));
                    AssertFilePublished(
                        Path.Combine(dir, "metadata.json"));
                }
                // Physical queue state and its capability metadata cross
                // the same staged publication boundary as the CSV.
                AssertEx.Equal(
                    true,
                    new FileInfo(Path.Combine(
                        output, "ss", "aux_state.jsonl")).Length > 0);
                AssertContains(
                    ReadAllBytesAsText(Path.Combine(
                        output, "ss", "metadata.json")),
                    "load_queue_state_per_frame");
                AssertFilePublished(
                    Path.Combine(output, "run_manifest.json"));

                // Every published byte is LF: the S1/S2 run-mode CRLF
                // expansion must never touch an S3K path.
                foreach (string file in
                    Directory.GetFiles(output, "*", SearchOption.AllDirectories))
                {
                    if (ReadAllBytesAsText(file).IndexOf('\r') >= 0)
                    {
                        throw new InvalidOperationException(
                            "Published file " + file + " contains CR.");
                    }
                }
            }));
        }

        /// <summary>
        /// A capture that throws mid-segment must leave no final path
        /// behind and no half-written temporary either — the streaming
        /// staging session owns both.
        /// </summary>
        private static void DiscardsFailedCapture()
        {
            WithMovie(MaskRows(40), movie => WithOutputDirectory(output =>
            {
                var publisher = new NoReplacePublisher();
                var threw = false;
                using (NoReplacePublisher.IncrementalStagingSession session =
                    publisher.OpenSession(output))
                using (var sink = new S3KStagedSegmentSink(session))
                {
                    try
                    {
                        S3KCompleteRunCaptureRunner.Capture(
                            movie,
                            new FakeS1Host((host, frame) =>
                            {
                                host.Ram[S3KRam.GameMode] = frame >= 4
                                    ? (byte)S3KRam.GameModeLevel
                                    : (byte)0;
                                host.SetU16(
                                    S3KRam.PlayerBase + S3KRam.OffMoveLock,
                                    0);
                                host.Ram[S3KRam.Ctrl1Locked] = 0;
                                if (frame == 12)
                                {
                                    throw new InvalidOperationException(
                                        "synthetic capture failure");
                                }
                            }),
                            MultiBonusRunId,
                            "synthetic.bk2",
                            "2026-07-24",
                            0,
                            sink);
                    }
                    catch (InvalidOperationException exception)
                    {
                        threw = exception.Message.IndexOf(
                            "synthetic capture failure",
                            StringComparison.Ordinal) >= 0;
                    }
                }
                AssertEx.Equal(true, threw);
                if (Directory.Exists(output))
                {
                    AssertEx.Equal(
                        0,
                        Directory.GetFiles(
                            output, "*", SearchOption.AllDirectories).Length);
                }
            }));
        }

        private static void DiscardsFailedSpecialStageQueueProjection()
        {
            WithMovie(MaskRows(40), movie => WithOutputDirectory(output =>
            {
                var publisher = new NoReplacePublisher();
                var threw = false;
                using (NoReplacePublisher.IncrementalStagingSession session =
                    publisher.OpenSession(output))
                using (var sink = new S3KStagedSegmentSink(session))
                {
                    try
                    {
                        S3KCompleteRunCaptureRunner.Capture(
                            movie,
                            RoundTripHost(true),
                            MultiBonusRunId,
                            "synthetic.bk2",
                            "2026-07-24",
                            0,
                            new byte[0],
                            sink,
                            true);
                    }
                    catch (InvalidDataException exception)
                    {
                        threw = exception.Message.IndexOf(
                            "outside the supplied ROM",
                            StringComparison.Ordinal) >= 0;
                    }
                }
                AssertEx.Equal(true, threw);
                if (Directory.Exists(output))
                {
                    AssertEx.Equal(
                        0,
                        Directory.GetFiles(
                            output, "*", SearchOption.AllDirectories).Length);
                }
            }));
        }

        private static void AssertNoFinalOutputs(string output)
        {
            if (!Directory.Exists(output))
            {
                return;
            }
            foreach (string file in Directory.GetFiles(
                output, "*", SearchOption.AllDirectories))
            {
                if (Path.GetFileName(file).IndexOf(
                    ".tmp.", StringComparison.Ordinal) < 0)
                {
                    throw new InvalidOperationException(
                        "Output " + file
                        + " exists under its final name before the staged"
                        + " set was published.");
                }
            }
        }

        private static void AssertFilePublished(string path)
        {
            if (!File.Exists(path))
            {
                throw new InvalidOperationException(
                    "Expected published file: " + path);
            }
        }

        // ------------------------------------------------------------------
        // Shared helpers
        // ------------------------------------------------------------------

        private static void AssertSegment(
            RunManifestSegment entry,
            string dir,
            string kind,
            string profile,
            int offset,
            int rows,
            int zoneId,
            int act,
            int? specialStageIndex,
            string bonusStageType)
        {
            AssertEx.Equal(dir, entry.Dir);
            AssertEx.Equal(kind, entry.Kind);
            AssertEx.Equal(profile, entry.TraceProfile);
            AssertEx.Equal(offset, entry.Bk2FrameOffset);
            AssertEx.Equal(rows, entry.TraceFrameCount);
            AssertEx.Equal(zoneId, entry.ZoneId);
            AssertEx.Equal(act, entry.Act);
            AssertEx.Equal(
                specialStageIndex ?? -1, entry.SpecialStageIndex ?? -1);
            AssertEx.Equal(bonusStageType, entry.BonusStageType);
        }

        private static void AssertTransition(
            RunManifestTransition entry,
            int from,
            int to,
            string kind,
            int frame)
        {
            AssertEx.Equal(from, entry.FromSegment);
            AssertEx.Equal(to, entry.ToSegment);
            AssertEx.Equal(kind, entry.EntryKind);
            AssertEx.Equal(frame, entry.ModeChangeBk2Frame);
        }

        private static void AssertHeaderAndRows(
            string content, string header, int rows)
        {
            string[] lines = content.Split(
                new[] { '\n' }, StringSplitOptions.None);
            AssertEx.Equal(header, lines[0]);
            // Every line, including the last, is LF-terminated, so the
            // split yields one trailing empty element.
            AssertEx.Equal(rows + 2, lines.Length);
            AssertEx.Equal(string.Empty, lines[lines.Length - 1]);
        }

        /// <summary>
        /// The 5-bit mask carried by synthetic BK2 input row
        /// <paramref name="row"/>: Up/Down/Left/Right/A in the P1 block,
        /// which the shared input fold turns into 0x01/0x02/0x04/0x08/0x10.
        /// Deliberately never 0 for two consecutive rows in the same way,
        /// so an off-by-one in the input column is visible.
        /// </summary>
        private static int MaskForRow(int row)
        {
            return (row * 7 + 3) & 0x1F;
        }

        private static string[] MaskRows(int count)
        {
            var rows = new string[count];
            for (var index = 0; index < count; index++)
            {
                int mask = MaskForRow(index);
                var buttons = new StringBuilder("........");
                if ((mask & S1InputMask.Up) != 0)
                {
                    buttons[0] = 'U';
                }
                if ((mask & S1InputMask.Down) != 0)
                {
                    buttons[1] = 'D';
                }
                if ((mask & S1InputMask.Left) != 0)
                {
                    buttons[2] = 'L';
                }
                if ((mask & S1InputMask.Right) != 0)
                {
                    buttons[3] = 'R';
                }
                if ((mask & S1InputMask.Jump) != 0)
                {
                    buttons[4] = 'A';
                }
                rows[index] = "|..|" + buttons + "|........|";
            }
            return rows;
        }

        private static void WithMovie(
            IEnumerable<string> rows, Action<Bk2Movie> body)
        {
            string directory = Path.Combine(
                Path.GetTempPath(),
                "openggf-s3k-completerun-" + Guid.NewGuid().ToString("N"));
            string path = Path.Combine(directory, "synthetic.bk2");
            Directory.CreateDirectory(directory);
            try
            {
                using (var stream = File.Create(path))
                using (var archive = new ZipArchive(
                    stream, ZipArchiveMode.Create, false))
                {
                    WriteEntry(
                        archive, "Header.txt", Fixture("ghz1-header.txt"));
                    WriteEntry(
                        archive,
                        "SyncSettings.json",
                        Fixture("ghz1-sync-settings.json"));
                    WriteEntry(
                        archive,
                        "Input Log.txt",
                        "[Input]\r\n"
                        + LogKey + "\r\n"
                        + string.Join("\r\n", ToArray(rows))
                        + "\r\n[/Input]\r\n");
                }
                body(Bk2Reader.Read(path));
            }
            finally
            {
                Directory.Delete(directory, true);
            }
        }

        private static void WithOutputDirectory(Action<string> body)
        {
            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-s3k-publish-" + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "output");
            try
            {
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

        private static string[] ToArray(IEnumerable<string> rows)
        {
            var list = new List<string>();
            foreach (string row in rows)
            {
                list.Add(row);
            }
            return list.ToArray();
        }

        private static void WriteEntry(
            ZipArchive archive, string name, string content)
        {
            ZipArchiveEntry entry =
                archive.CreateEntry(name, CompressionLevel.NoCompression);
            using (Stream stream = entry.Open())
            using (var writer = new StreamWriter(
                stream, new UTF8Encoding(false)))
            {
                writer.Write(content);
            }
        }

        private static string Fixture(string name)
        {
            return File.ReadAllText(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory, "fixtures", name));
        }

        private static string FixtureDirectory(string relativeName)
        {
            string fixtureDirectory = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s3k",
                relativeName);
            if (!Directory.Exists(fixtureDirectory))
            {
                throw new InvalidOperationException(
                    "Checked-in S3K fixture directory missing: "
                    + fixtureDirectory);
            }
            return fixtureDirectory;
        }

        /// <summary>
        /// Reads a file as raw bytes decoded as Latin-1 so no line-ending
        /// or BOM normalization can hide a difference.
        /// </summary>
        private static string ReadAllBytesAsText(string path)
        {
            byte[] bytes = File.ReadAllBytes(path);
            var text = new StringBuilder(bytes.Length);
            foreach (byte value in bytes)
            {
                text.Append((char)value);
            }
            return text.ToString();
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

        private static void AssertContains(
            string value, string expectedFragment)
        {
            if (value.IndexOf(expectedFragment, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected text to contain <" + expectedFragment + ">.");
            }
        }

        private static void AssertAbsent(string value, string fragment)
        {
            if (value.IndexOf(fragment, StringComparison.Ordinal) >= 0)
            {
                throw new InvalidOperationException(
                    "Expected text NOT to contain <" + fragment + ">.");
            }
        }
    }
}
