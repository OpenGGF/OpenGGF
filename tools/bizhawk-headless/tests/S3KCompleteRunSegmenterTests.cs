using System;
using System.Collections.Generic;
using System.Globalization;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Unit gate for the S3K complete-run SEGMENTATION engine
    /// (<see cref="S3KCompleteRunSegmenter"/> /
    /// <see cref="S3KZoneTokens"/>; spec
    /// tools/bizhawk-headless/docs/s3k-complete-run-behavior.md).
    ///
    /// Two layers:
    ///
    /// 1. Synthetic single-behaviour cases that drive a
    ///    <see cref="FakeS1Host"/> through a hand-built (Game_mode,
    ///    Current_zone, Current_act, move_lock, Ctrl_1_locked) stream and
    ///    pin one predicate each — the raw-0x0C arm gate, both control
    ///    gates, the arm-frame-belongs-to-no-segment rule, the one-time
    ///    zone gate, the level-family row guard, the special-stage detour
    ///    state machine, the dir suffixes, the transition pushes and every
    ///    stop terminator's row arithmetic.
    ///
    /// 2. Fixture-literal replays that reproduce the committed (A) and (B)
    ///    offset / frame-count tables end to end. These are built from the
    ///    fixtures' bk2_frame_offsets (plus, for the three special stages,
    ///    their published last-row frames) and the REAL movie length, so
    ///    every trace_frame_count is DERIVED by the segmenter and then
    ///    asserted against the fixture literal rather than fed in. Set
    ///    (B)'s movie length 114622 is the actual
    ///    s3-knux-multibonus-ss.bk2 input-row count, which makes its
    ///    terminal mgz_3 count (8517) a fully independent derivation.
    ///
    /// The tests are hermetic: no ROM, no BizHawk, no fixture file I/O.
    /// </summary>
    internal static class S3KCompleteRunSegmenterTests
    {
        // ------------------------------------------------------------------
        // Synthetic frame-stream plumbing
        // ------------------------------------------------------------------

        private sealed class Leg
        {
            internal int First;
            internal int Last;
            internal byte GameMode;
            internal byte ZoneId;
            internal byte ActRaw;
            internal ushort CtrlLockTimer;
            internal byte CtrlLocked;
            internal byte SpecialStageIndex;
        }

        /// <summary>
        /// A contiguous, non-overlapping, ascending cover of BK2 frames.
        /// Any frame outside every leg reads Game_mode 0x00 (SEGA screen),
        /// which can never arm.
        /// </summary>
        private sealed class SyntheticPlan
        {
            private readonly List<Leg> legs = new List<Leg>();
            private int cursor;

            internal SyntheticPlan Mode(int first, int last, int gameMode)
            {
                return Add(first, last, gameMode, 0, 0, 0, 0, 0);
            }

            /// <summary>An unlocked, settled level/bonus span.</summary>
            internal SyntheticPlan Level(
                int first, int last, int zoneId, int actRaw)
            {
                return Add(
                    first, last, S3KRam.GameModeLevel, zoneId, actRaw,
                    0, 0, 0);
            }

            /// <summary>Raw 0x0C but still inside the locked intro.</summary>
            internal SyntheticPlan LockedLevel(
                int first,
                int last,
                int zoneId,
                int actRaw,
                int ctrlLockTimer,
                int ctrlLocked)
            {
                return Add(
                    first, last, S3KRam.GameModeLevel, zoneId, actRaw,
                    ctrlLockTimer, ctrlLocked, 0);
            }

            internal SyntheticPlan SpecialStage(
                int first, int last, int specialStageIndex)
            {
                return Add(
                    first, last, S3KRam.GameModeSpecialStage, 0, 0, 0, 0,
                    specialStageIndex);
            }

            internal SyntheticPlan Add(
                int first,
                int last,
                int gameMode,
                int zoneId,
                int actRaw,
                int ctrlLockTimer,
                int ctrlLocked,
                int specialStageIndex)
            {
                if (last < first)
                {
                    throw new ArgumentException(
                        "Empty leg [" + first + ", " + last + "].");
                }
                if (legs.Count > 0 && first <= legs[legs.Count - 1].Last)
                {
                    throw new ArgumentException(
                        "Legs must ascend and not overlap; got first="
                        + first + " after last="
                        + legs[legs.Count - 1].Last + ".");
                }
                legs.Add(new Leg
                {
                    First = first,
                    Last = last,
                    GameMode = (byte)gameMode,
                    ZoneId = (byte)zoneId,
                    ActRaw = (byte)actRaw,
                    CtrlLockTimer = (ushort)ctrlLockTimer,
                    CtrlLocked = (byte)ctrlLocked,
                    SpecialStageIndex = (byte)specialStageIndex
                });
                return this;
            }

            internal void Apply(FakeS1Host host, int frame)
            {
                while (cursor < legs.Count && legs[cursor].Last < frame)
                {
                    cursor++;
                }
                Leg leg = cursor < legs.Count && legs[cursor].First <= frame
                    ? legs[cursor]
                    : null;
                host.Ram[S3KRam.GameMode] =
                    leg == null ? (byte)0 : leg.GameMode;
                host.Ram[S3KRam.Zone] = leg == null ? (byte)0 : leg.ZoneId;
                host.Ram[S3KRam.Act] = leg == null ? (byte)0 : leg.ActRaw;
                host.SetU16(
                    S3KRam.PlayerBase + S3KRam.OffMoveLock,
                    leg == null ? (ushort)0 : leg.CtrlLockTimer);
                host.Ram[S3KRam.Ctrl1Locked] =
                    leg == null ? (byte)0 : leg.CtrlLocked;
                host.Ram[S3KRam.CurrentSpecialStage] =
                    leg == null ? (byte)0 : leg.SpecialStageIndex;
            }
        }

        /// <summary>
        /// Runs the Lua main loop literally: on_frame_end, then the
        /// FRAME_CAP backstop, then (only if not finished) the advance —
        /// with the first evaluation happening at CompletedFrame 0, BEFORE
        /// any advance. Records each segment's observed row count and the
        /// BK2 framecounts of its first and last recorded rows so the
        /// "row N is observed at offset + 1 + N" identity can be asserted
        /// independently of the segmenter's own bookkeeping.
        /// </summary>
        private sealed class PlanRunner
        {
            internal readonly List<int> ObservedRowCounts = new List<int>();
            internal readonly List<int> FirstRowFrames = new List<int>();
            internal readonly List<int> LastRowFrames = new List<int>();
            internal readonly List<string> OpenedDirs = new List<string>();
            internal readonly List<S3KSegmentArm> Arms =
                new List<S3KSegmentArm>();
            internal int PreTraceSnapshotEmissions;

            private int rows;
            private int firstRowFrame = -1;
            private int lastRowFrame = -1;

            internal S3KCompleteRunSegmenter Run(
                S3KCompleteRunSegmenter segmenter,
                SyntheticPlan plan,
                int maxFrames)
            {
                segmenter.SegmentOpened = arm =>
                {
                    OpenedDirs.Add(arm.DirToken);
                    Arms.Add(arm);
                    rows = 0;
                    firstRowFrame = -1;
                    lastRowFrame = -1;
                };
                segmenter.SegmentClosed = segment =>
                {
                    ObservedRowCounts.Add(rows);
                    FirstRowFrames.Add(firstRowFrame);
                    LastRowFrames.Add(lastRowFrame);
                };

                var host = new FakeS1Host(
                    (h, frame) => plan.Apply(h, frame));
                plan.Apply(host, 0);

                while (true)
                {
                    S3KCompleteRunFrameAction action = segmenter.Step(host);
                    if (action == S3KCompleteRunFrameAction.LevelRow
                        || action
                            == S3KCompleteRunFrameAction.SpecialStageRow)
                    {
                        if (segmenter.RowIndex != rows)
                        {
                            throw new InvalidOperationException(
                                "RowIndex " + segmenter.RowIndex
                                + " does not match the rows written so far "
                                + rows + " at frame "
                                + host.CompletedFrame + ".");
                        }
                        if (firstRowFrame < 0)
                        {
                            firstRowFrame = host.CompletedFrame;
                        }
                        lastRowFrame = host.CompletedFrame;
                        rows++;
                    }
                    else if (segmenter.RowIndex != -1)
                    {
                        throw new InvalidOperationException(
                            "RowIndex must be -1 for a rowless frame.");
                    }
                    if (segmenter.EmittedPreTraceSnapshots)
                    {
                        PreTraceSnapshotEmissions++;
                    }
                    segmenter.ApplyFrameCap(host.CompletedFrame);
                    if (segmenter.Finished)
                    {
                        break;
                    }
                    if (host.CompletedFrame >= maxFrames)
                    {
                        throw new InvalidOperationException(
                            "Plan ran past " + maxFrames
                            + " frames without a stop predicate firing.");
                    }
                    host.Advance();
                }

                segmenter.FinalizeRunEnd();
                return segmenter;
            }
        }

        // ------------------------------------------------------------------
        // Fixture-literal expectations
        // ------------------------------------------------------------------

        private sealed class ExpectedSegment
        {
            internal ExpectedSegment(
                string dir,
                string kind,
                string traceProfile,
                int bk2FrameOffset,
                int traceFrameCount,
                int zoneId,
                int act,
                string bonusStageType,
                int? specialStageIndex)
            {
                Dir = dir;
                Kind = kind;
                TraceProfile = traceProfile;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                ZoneId = zoneId;
                Act = act;
                BonusStageType = bonusStageType;
                SpecialStageIndex = specialStageIndex;
            }

            internal string Dir;
            internal string Kind;
            internal string TraceProfile;
            internal int Bk2FrameOffset;
            internal int TraceFrameCount;
            internal int ZoneId;
            internal int Act;
            internal string BonusStageType;
            internal int? SpecialStageIndex;
        }

        private const string Level = RunManifestSegment.LevelKind;
        private const string Bonus = RunManifestSegment.BonusStageKind;
        private const string Ss = RunManifestSegment.SpecialStageKind;
        private const string LevelProfile =
            S3KCompleteRunSegmenter.LevelTraceProfile;
        private const string BonusProfile =
            S3KCompleteRunSegmenter.BonusStageTraceProfile;
        private const string SsProfile =
            S3KCompleteRunSegmenter.SpecialStageTraceProfile;

        /// <summary>
        /// Spec §0.1 — the seven committed
        /// src/test/resources/traces/s3k/&lt;zone&gt;_completerun/ dirs.
        /// </summary>
        private static readonly ExpectedSegment[] SetA =
        {
            new ExpectedSegment(
                "aiz", Level, LevelProfile, 941, 26228, 0, 1, null, null),
            new ExpectedSegment(
                "hcz", Level, LevelProfile, 27170, 31482, 1, 1, null, null),
            new ExpectedSegment(
                "mgz", Level, LevelProfile, 58653, 39398, 2, 1, null, null),
            new ExpectedSegment(
                "cnz", Level, LevelProfile, 98052, 40064, 3, 1, null, null),
            new ExpectedSegment(
                "icz", Level, LevelProfile, 138117, 25393, 5, 1, null, null),
            new ExpectedSegment(
                "lbz", Level, LevelProfile, 163511, 46244, 6, 1, null, null),
            new ExpectedSegment(
                "mhz", Level, LevelProfile, 209756, 28156, 7, 1, null, null)
        };

        /// <summary>
        /// Spec §0.2 — the 25 segment dirs under
        /// src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/.
        /// </summary>
        private static readonly ExpectedSegment[] SetB =
        {
            new ExpectedSegment(
                "aiz", Level, LevelProfile, 915, 4654, 0, 1, null, null),
            new ExpectedSegment(
                "gumball", Bonus, BonusProfile, 5570, 1430, 0x13, 1,
                "gumball", null),
            new ExpectedSegment(
                "aiz_2", Level, LevelProfile, 7001, 2140, 0, 1, null, null),
            new ExpectedSegment(
                "slots", Bonus, BonusProfile, 9142, 1200, 0x15, 1, "slots",
                null),
            new ExpectedSegment(
                "aiz_3", Level, LevelProfile, 10343, 7568, 0, 2, null, null),
            new ExpectedSegment(
                "slots_2", Bonus, BonusProfile, 17912, 1278, 0x15, 1,
                "slots", null),
            new ExpectedSegment(
                "aiz_4", Level, LevelProfile, 19191, 3210, 0, 2, null, null),
            new ExpectedSegment(
                "gumball_2", Bonus, BonusProfile, 22402, 1648, 0x13, 1,
                "gumball", null),
            new ExpectedSegment(
                "aiz_5", Level, LevelProfile, 24051, 3631, 0, 2, null, null),
            new ExpectedSegment(
                "hcz", Level, LevelProfile, 27683, 3176, 1, 1, null, null),
            new ExpectedSegment(
                "slots_3", Bonus, BonusProfile, 30860, 5379, 0x15, 1,
                "slots", null),
            new ExpectedSegment(
                "hcz_2", Level, LevelProfile, 36240, 11933, 1, 1, null,
                null),
            new ExpectedSegment(
                "ss", Ss, SsProfile, 48174, 4630, 0, 0, null, 0),
            new ExpectedSegment(
                "hcz_3", Level, LevelProfile, 54274, 3949, 1, 2, null, null),
            new ExpectedSegment(
                "slots_4", Bonus, BonusProfile, 58224, 1603, 0x15, 1,
                "slots", null),
            new ExpectedSegment(
                "hcz_4", Level, LevelProfile, 59828, 2097, 1, 2, null, null),
            new ExpectedSegment(
                "ss_2", Ss, SsProfile, 61926, 7194, 0, 0, null, 1),
            new ExpectedSegment(
                "hcz_5", Level, LevelProfile, 70590, 3435, 1, 2, null, null),
            new ExpectedSegment(
                "slots_5", Bonus, BonusProfile, 74026, 1791, 0x15, 1,
                "slots", null),
            new ExpectedSegment(
                "hcz_6", Level, LevelProfile, 75818, 8422, 1, 2, null, null),
            new ExpectedSegment(
                "mgz", Level, LevelProfile, 84241, 8721, 2, 1, null, null),
            new ExpectedSegment(
                "pachinko", Bonus, BonusProfile, 92963, 3051, 0x14, 1,
                "pachinko", null),
            new ExpectedSegment(
                "mgz_2", Level, LevelProfile, 96015, 2076, 2, 1, null, null),
            new ExpectedSegment(
                "ss_3", Ss, SsProfile, 98092, 6537, 0, 0, null, 2),
            new ExpectedSegment(
                "mgz_3", Level, LevelProfile, 106104, 8517, 2, 1, null, null)
        };

        /// <summary>
        /// Spec §0.2: the three SS exits are the only boundaries that are
        /// NOT a "+1" succession, because SS-results (0x48) and the level
        /// reload run between them with no recording. These are the
        /// published "last SS row frame" values; the resulting gaps
        /// (1469 / 1469 / 1474) are route state, NOT a constant, and are
        /// never hard-coded here.
        /// </summary>
        private static readonly int[] SetBSpecialStageLastRowFrames =
        {
            52804, 69120, 104629
        };

        /// <summary>
        /// s3-knux-multibonus-ss.bk2's actual input-row count, verified by
        /// counting the movie's Input Log rows. The terminal mgz_3 segment
        /// therefore stops on step (2) at framecount 114622 and its
        /// trace_frame_count 8517 == 114622 - 106104 - 1 is derived, not
        /// supplied.
        /// </summary>
        private const int SetBMovieFrameCount = 114622;

        /// <summary>
        /// Spec §0.1: mhz is the one set-(A) count with no published
        /// successor. The "+1" identity predicts the (unpublished) FBZ arm
        /// at 209756 + 28156 + 1; the replay below uses that frame as the
        /// synthetic movie end so the step-(2) arithmetic reproduces 28156.
        /// The independent confirmation is the differential gate: a real
        /// capture run past MHZ must arm FBZ at exactly this frame.
        /// </summary>
        private const int SetAPredictedFbzArmFrame = 237913;

        internal static void Register(List<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3K zone tokens diverge from the standard zone names",
                ZoneTokensDivergeFromZoneNames));
            tests.Add(new TestMain.TestCase(
                "S3K zone tokens resolve bonus zones before the zone table",
                ZoneTokensResolveBonusFirst));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run arm requires raw 0x0C, not the level family",
                ArmRequiresRawLevelMode));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run arm requires both control gates clear",
                ArmRequiresBothControlGates));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run arm frame belongs to no segment",
                ArmFrameIsNotRecorded));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run zone gate is one-time per segment",
                ZoneGateIsOneTimePerSegment));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run records the level-family handoff tail",
                LevelFamilyTailIsRecorded));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run skips non-level-family frames without closing",
                NonLevelFamilyExcursionSkipsRowsWithoutClosing));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run re-arms into the same zone after a detour",
                DetourIntoSameZoneReArms));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run special-stage detour opens and closes once",
                SpecialStageDetourOpensAndClosesOnce));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run ignores a special stage before any arm",
                SpecialStageBeforeAnyArmProducesNothing));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run finalizes a truncated special stage as ss",
                TruncatedSpecialStageFinalizesAsSpecialStage));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run pushes stage_exit and starpost_bonus independently",
                StageExitAndStarpostBonusArePushedIndependently));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run transition fields come from the push frame",
                TransitionFieldsComeFromThePushFrame));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run movie-end stop drops the terminal frame",
                MovieEndStopDropsTheTerminalFrame));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run trace stop frame truncates at exactly N rows",
                TraceStopFrameTruncatesAtExactlyNRows));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run BK2 frame count stop truncates at the limit",
                Bk2FrameCountStopTruncatesAtTheLimit));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run pre-trace snapshot latch resets per segment",
                PreTraceSnapshotLatchResetsPerSegment));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run absolute frame cap matches the Lua",
                AbsoluteFrameCapMatchesTheLua));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run reproduces the set (A) fixture table",
                ReproducesSetAFixtureTable));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run reproduces the set (B) fixture table",
                ReproducesSetBFixtureTable));
            tests.Add(new TestMain.TestCase(
                "S3K complete-run reproduces the set (B) transition table",
                ReproducesSetBTransitionTable));
        }

        // ------------------------------------------------------------------
        // Zone tokens
        // ------------------------------------------------------------------

        private static void ZoneTokensDivergeFromZoneNames()
        {
            // Spec §6.1 / §10.8: ZONE_TOKEN and ZONE_NAMES agree for ids
            // 0-9 and disagree from 10 up. Pinning the disagreement is the
            // point — a port that reuses the STANDARD recorder's table
            // mislabels every SOZ-and-later segment's directory AND its
            // metadata "zone".
            for (var zoneId = 0; zoneId <= 9; zoneId++)
            {
                AssertEx.Equal(
                    S3KRam.ZoneName(zoneId), S3KZoneTokens.ZoneTokenFor(zoneId));
            }
            AssertEx.Equal("ssz", S3KRam.ZoneName(10));
            AssertEx.Equal("hpz", S3KZoneTokens.ZoneTokenFor(10));
            AssertEx.Equal("dez", S3KRam.ZoneName(11));
            AssertEx.Equal("ssz", S3KZoneTokens.ZoneTokenFor(11));
            AssertEx.Equal("ddz", S3KRam.ZoneName(12));
            AssertEx.Equal("zone0c", S3KZoneTokens.ZoneTokenFor(12));
            AssertEx.Equal("hpz", S3KRam.ZoneName(13));
            AssertEx.Equal("ddz", S3KZoneTokens.ZoneTokenFor(13));
            AssertEx.Equal("hpz22", S3KZoneTokens.ZoneTokenFor(22));
            AssertEx.Equal("dez23", S3KZoneTokens.ZoneTokenFor(23));
            // string.format("zone%02x", id): lowercase, two digits.
            AssertEx.Equal("zone0e", S3KZoneTokens.ZoneTokenFor(14));
            AssertEx.Equal("zoneff", S3KZoneTokens.ZoneTokenFor(255));
        }

        private static void ZoneTokensResolveBonusFirst()
        {
            AssertEx.Equal("gumball", S3KZoneTokens.ZoneTokenFor(0x13));
            AssertEx.Equal("pachinko", S3KZoneTokens.ZoneTokenFor(0x14));
            AssertEx.Equal("slots", S3KZoneTokens.ZoneTokenFor(0x15));
            AssertEx.Equal("gumball", S3KZoneTokens.BonusToken(0x13));
            AssertEx.Equal(null, S3KZoneTokens.BonusToken(0x12));
            AssertEx.Equal(null, S3KZoneTokens.BonusToken(0x16));
            // Precreated dirs: 15 zone tokens + 3 bonus tokens + "ss".
            AssertEx.Equal(19, S3KZoneTokens.PrecreatedTokens().Count);
        }

        // ------------------------------------------------------------------
        // Arm gate
        // ------------------------------------------------------------------

        private static void ArmRequiresRawLevelMode()
        {
            // 0x4C and 0x8C are level-family but NOT raw 0x0C: during the
            // load handoff the player is not placed and Ctrl_1_locked can
            // briefly read 0, so arming there is wrong (spec §5.1).
            var plan = new SyntheticPlan()
                .Add(0, 9, 0x4C, 0, 0, 0, 0, 0)
                .Add(10, 19, 0x8C, 0, 0, 0, 0, 0)
                .Level(20, 39, 0, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(40), plan, 400);

            AssertEx.Equal(1, segmenter.Segments.Count);
            AssertEx.Equal(20, segmenter.Segments[0].Bk2FrameOffset);
            // Rows at frames 21..39; frame 40 hits the step-(2) stop.
            AssertEx.Equal(19, segmenter.Segments[0].TraceFrameCount);
        }

        private static void ArmRequiresBothControlGates()
        {
            // move_lock non-zero, then Ctrl_1_locked non-zero, then clear.
            var plan = new SyntheticPlan()
                .LockedLevel(0, 9, 0, 0, 30, 0)
                .LockedLevel(10, 19, 0, 0, 0, 1)
                .Level(20, 49, 0, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(50), plan, 500);

            AssertEx.Equal(1, segmenter.Segments.Count);
            AssertEx.Equal(20, segmenter.Segments[0].Bk2FrameOffset);
            AssertEx.Equal(29, segmenter.Segments[0].TraceFrameCount);
        }

        private static void ArmFrameIsNotRecorded()
        {
            // Spec §5.6/§10.2: row N is observed at offset + 1 + N, so the
            // first recorded frame is the one AFTER the arm.
            var plan = new SyntheticPlan()
                .Mode(0, 4, 0x00)
                .Level(5, 14, 0, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(15), plan, 200);

            AssertEx.Equal(1, segmenter.Segments.Count);
            AssertEx.Equal(5, segmenter.Segments[0].Bk2FrameOffset);
            AssertEx.Equal(9, segmenter.Segments[0].TraceFrameCount);
            AssertEx.Equal(9, runner.ObservedRowCounts[0]);
            AssertEx.Equal(6, runner.FirstRowFrames[0]);
            AssertEx.Equal(14, runner.LastRowFrames[0]);
        }

        private static void ZoneGateIsOneTimePerSegment()
        {
            // Armed for zone 0; a later control lock inside the same zone
            // must NOT re-arm, and neither must the act flip.
            var plan = new SyntheticPlan()
                .Level(0, 9, 0, 0)
                .LockedLevel(10, 19, 0, 1, 60, 1)
                .Level(20, 29, 0, 1)
                .Level(30, 49, 1, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(50), plan, 500);

            AssertEx.Equal(2, segmenter.Segments.Count);
            AssertEx.Equal("aiz", segmenter.Segments[0].Dir);
            AssertEx.Equal(0, segmenter.Segments[0].Bk2FrameOffset);
            // Every frame 1..29 recorded into the one segment, act still 1
            // (the arm-frame act was 0 and is never re-read).
            AssertEx.Equal(29, segmenter.Segments[0].TraceFrameCount);
            AssertEx.Equal(1, segmenter.Segments[0].Act);
            AssertEx.Equal("hcz", segmenter.Segments[1].Dir);
            AssertEx.Equal(30, segmenter.Segments[1].Bk2FrameOffset);
        }

        private static void LevelFamilyTailIsRecorded()
        {
            // Spec §5.1: the trailing 0x4C/0x8C zone-exit handoff frames
            // land in the CURRENT segment (step 10 lets them through).
            var plan = new SyntheticPlan()
                .Level(0, 9, 0, 0)
                .Add(10, 14, 0x8C, 1, 0, 0, 0, 0)
                .LockedLevel(15, 19, 1, 0, 0, 1)
                .Level(20, 29, 1, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(30), plan, 300);

            AssertEx.Equal(2, segmenter.Segments.Count);
            // Frames 1..19 all recorded into aiz: the 0x8C handoff and the
            // next zone's locked intro belong to the OUTGOING segment.
            AssertEx.Equal(19, segmenter.Segments[0].TraceFrameCount);
            AssertEx.Equal(19, runner.LastRowFrames[0]);
            AssertEx.Equal(20, segmenter.Segments[1].Bk2FrameOffset);
        }

        private static void NonLevelFamilyExcursionSkipsRowsWithoutClosing()
        {
            // Spec §1.2 note on step 10: a 0x00/0x04/0x08 excursion
            // silently skips rows without ever closing the segment.
            var plan = new SyntheticPlan()
                .Level(0, 4, 0, 0)
                .Mode(5, 9, 0x08)
                .Level(10, 19, 0, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(20), plan, 200);

            AssertEx.Equal(1, segmenter.Segments.Count);
            AssertEx.Equal(0, segmenter.Segments[0].Bk2FrameOffset);
            // Rows at 1..4 and 10..19 == 14; frames 5..9 skipped.
            AssertEx.Equal(14, segmenter.Segments[0].TraceFrameCount);
            AssertEx.Equal(19, runner.LastRowFrames[0]);
        }

        private static void DetourIntoSameZoneReArms()
        {
            // Spec §10.4: current_segment_zone is cleared by finalize, so
            // aiz -> gumball -> aiz produces aiz, gumball, aiz_2.
            var plan = new SyntheticPlan()
                .Level(0, 9, 0, 0)
                .Level(10, 19, 0x13, 0)
                .Level(20, 39, 0, 1);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(40), plan, 400);

            AssertEx.Equal(3, segmenter.Segments.Count);
            AssertEx.Equal("aiz", segmenter.Segments[0].Dir);
            AssertEx.Equal("gumball", segmenter.Segments[1].Dir);
            AssertEx.Equal("aiz_2", segmenter.Segments[2].Dir);
            AssertEx.Equal(Bonus, segmenter.Segments[1].Kind);
            AssertEx.Equal(BonusProfile, segmenter.Segments[1].TraceProfile);
            AssertEx.Equal(
                "gumball", segmenter.Segments[1].BonusStageType);
            AssertEx.Equal(0x13, segmenter.Segments[1].ZoneId);
            AssertEx.Equal(null, segmenter.Segments[0].BonusStageType);
            // segment_index is the segment's own 0-based index.
            AssertEx.Equal(0, segmenter.Segments[0].SegmentIndex);
            AssertEx.Equal(1, segmenter.Segments[1].SegmentIndex);
            AssertEx.Equal(2, segmenter.Segments[2].SegmentIndex);
        }

        private static void SpecialStageDetourOpensAndClosesOnce()
        {
            // Spec §5.3: entry finalizes the level and opens ss without
            // recording the entry frame; the SS rows follow; the first
            // non-0x34 frame closes it exactly once; a 0x48 exit returns
            // rather than falling through to the arm gate.
            var plan = new SyntheticPlan()
                .Level(0, 9, 1, 1)
                .SpecialStage(10, 19, 2)
                .Mode(20, 29, 0x48)
                .Level(30, 49, 1, 1);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(50), plan, 500);

            AssertEx.Equal(3, segmenter.Segments.Count);
            AssertEx.Equal("hcz", segmenter.Segments[0].Dir);
            AssertEx.Equal(9, segmenter.Segments[0].TraceFrameCount);

            S3KCompleteRunSegment ss = segmenter.Segments[1];
            AssertEx.Equal("ss", ss.Dir);
            AssertEx.Equal("ss", ss.Token);
            AssertEx.Equal(Ss, ss.Kind);
            AssertEx.Equal(SsProfile, ss.TraceProfile);
            AssertEx.Equal(10, ss.Bk2FrameOffset);
            AssertEx.Equal(9, ss.TraceFrameCount);
            AssertEx.Equal(11, runner.FirstRowFrames[1]);
            AssertEx.Equal(19, runner.LastRowFrames[1]);
            // zone_id / act are hardcoded 0; the index is the ROM counter.
            AssertEx.Equal(0, ss.ZoneId);
            AssertEx.Equal(0, ss.Act);
            AssertEx.Equal(2, ss.SpecialStageIndex.Value);
            AssertEx.Equal(null, ss.BonusStageType);

            AssertEx.Equal("hcz_2", segmenter.Segments[2].Dir);
            AssertEx.Equal(30, segmenter.Segments[2].Bk2FrameOffset);

            AssertEx.Equal(2, segmenter.Transitions.Count);
            AssertEx.Equal(
                RunManifestTransition.GiantRingKind,
                segmenter.Transitions[0].EntryKind);
            AssertEx.Equal(0, segmenter.Transitions[0].FromSegment);
            AssertEx.Equal(1, segmenter.Transitions[0].ToSegment);
            AssertEx.Equal(10, segmenter.Transitions[0].ModeChangeBk2Frame);
            AssertEx.Equal(
                RunManifestTransition.StageExitKind,
                segmenter.Transitions[1].EntryKind);
            AssertEx.Equal(1, segmenter.Transitions[1].FromSegment);
            AssertEx.Equal(2, segmenter.Transitions[1].ToSegment);
            AssertEx.Equal(30, segmenter.Transitions[1].ModeChangeBk2Frame);
        }

        private static void SpecialStageBeforeAnyArmProducesNothing()
        {
            // Spec §5.3: `started` is required for entry, so a 0x34 detour
            // before the first level arm creates no ss segment and no
            // from_segment == -1 transition.
            var plan = new SyntheticPlan()
                .SpecialStage(0, 9, 0)
                .Level(10, 29, 0, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(30), plan, 300);

            AssertEx.Equal(1, segmenter.Segments.Count);
            AssertEx.Equal("aiz", segmenter.Segments[0].Dir);
            AssertEx.Equal(0, segmenter.Transitions.Count);
        }

        private static void TruncatedSpecialStageFinalizesAsSpecialStage()
        {
            // Spec §5.7 step 1 / §10.11: the end-of-run finalize must NOT
            // route an open SS segment through the level finalize, which
            // would stamp kind "level" on 20-column SS rows.
            var plan = new SyntheticPlan()
                .Level(0, 9, 1, 0)
                .SpecialStage(10, 29, 1);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(25), plan, 300);

            AssertEx.Equal(2, segmenter.Segments.Count);
            S3KCompleteRunSegment ss = segmenter.Segments[1];
            AssertEx.Equal(Ss, ss.Kind);
            AssertEx.Equal(SsProfile, ss.TraceProfile);
            AssertEx.Equal("ss", ss.Dir);
            AssertEx.Equal(1, ss.SpecialStageIndex.Value);
            // Step (2) stops at framecount 25; rows at 11..24.
            AssertEx.Equal(10, ss.Bk2FrameOffset);
            AssertEx.Equal(14, ss.TraceFrameCount);
        }

        private static void StageExitAndStarpostBonusArePushedIndependently()
        {
            // Spec §5.1: the two pushes are independent ifs, not
            // if/elseif, so a bonus -> bonus boundary emits BOTH with
            // identical from/to indices. No fixture exercises it; the
            // shape is ported verbatim.
            var plan = new SyntheticPlan()
                .Level(0, 4, 0, 0)
                .Level(5, 9, 0x13, 0)
                .Level(10, 14, 0x15, 0)
                .Level(15, 29, 0, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(30), plan, 300);

            AssertEx.Equal(4, segmenter.Segments.Count);
            AssertEx.Equal("gumball", segmenter.Segments[1].Dir);
            AssertEx.Equal("slots", segmenter.Segments[2].Dir);

            AssertEx.Equal(4, segmenter.Transitions.Count);
            AssertEx.Equal(
                RunManifestTransition.StarpostBonusKind,
                segmenter.Transitions[0].EntryKind);
            AssertEx.Equal(0, segmenter.Transitions[0].FromSegment);
            AssertEx.Equal(1, segmenter.Transitions[0].ToSegment);
            // The bonus -> bonus boundary at frame 10: stage_exit FIRST,
            // then starpost_bonus, same from/to.
            AssertEx.Equal(
                RunManifestTransition.StageExitKind,
                segmenter.Transitions[1].EntryKind);
            AssertEx.Equal(1, segmenter.Transitions[1].FromSegment);
            AssertEx.Equal(2, segmenter.Transitions[1].ToSegment);
            AssertEx.Equal(10, segmenter.Transitions[1].ModeChangeBk2Frame);
            AssertEx.Equal(
                RunManifestTransition.StarpostBonusKind,
                segmenter.Transitions[2].EntryKind);
            AssertEx.Equal(1, segmenter.Transitions[2].FromSegment);
            AssertEx.Equal(2, segmenter.Transitions[2].ToSegment);
            AssertEx.Equal(10, segmenter.Transitions[2].ModeChangeBk2Frame);
            AssertEx.Equal(
                RunManifestTransition.StageExitKind,
                segmenter.Transitions[3].EntryKind);
            AssertEx.Equal(2, segmenter.Transitions[3].FromSegment);
            AssertEx.Equal(3, segmenter.Transitions[3].ToSegment);
        }

        private static void TransitionFieldsComeFromThePushFrame()
        {
            // Spec §7.2: entry-kind records carry the same six fields;
            // stage_exit carries only rings_after / emeralds_after. A
            // sampled 0 still renders (presence is by kind, never value).
            var plan = new SyntheticPlan()
                .Level(0, 4, 0, 0)
                .Level(5, 9, 0x14, 0)
                .Level(10, 19, 0, 0);
            var host = new FakeS1Host((h, frame) =>
            {
                plan.Apply(h, frame);
                if (frame == 5)
                {
                    h.Ram[S3KRam.SpecialBonusEntryFlag] = 0x81;
                    h.SetU16(S3KRam.SavedXPos, 0x1234);
                    h.SetU16(S3KRam.SavedYPos, 0x0567);
                    h.Ram[S3KRam.LastStarPostHit] = 3;
                    h.SetU16(S3KRam.RingCount, 42);
                    h.Ram[S3KRam.EmeraldCount] = 0;
                }
                if (frame == 10)
                {
                    h.SetU16(S3KRam.RingCount, 77);
                    h.Ram[S3KRam.EmeraldCount] = 2;
                }
            });
            plan.Apply(host, 0);

            var segmenter = new S3KCompleteRunSegmenter(20);
            while (true)
            {
                segmenter.Step(host);
                segmenter.ApplyFrameCap(host.CompletedFrame);
                if (segmenter.Finished)
                {
                    break;
                }
                host.Advance();
            }
            segmenter.FinalizeRunEnd();

            AssertEx.Equal(2, segmenter.Transitions.Count);
            RunManifestTransition starpost = segmenter.Transitions[0];
            AssertEx.Equal(
                RunManifestTransition.StarpostBonusKind, starpost.EntryKind);
            AssertEx.Equal(0x81, starpost.SpecialBonusEntryFlag.Value);
            AssertEx.Equal(0x1234, starpost.SavedXPos.Value);
            AssertEx.Equal(0x0567, starpost.SavedYPos.Value);
            AssertEx.Equal(3, starpost.LastStarPostHit.Value);
            AssertEx.Equal(42, starpost.RingsBefore.Value);
            AssertEx.Equal(0, starpost.EmeraldsBefore.Value);
            AssertEx.Equal(false, starpost.RingsAfter.HasValue);
            AssertEx.Equal(false, starpost.EmeraldsAfter.HasValue);

            RunManifestTransition exit = segmenter.Transitions[1];
            AssertEx.Equal(
                RunManifestTransition.StageExitKind, exit.EntryKind);
            AssertEx.Equal(77, exit.RingsAfter.Value);
            AssertEx.Equal(2, exit.EmeraldsAfter.Value);
            AssertEx.Equal(false, exit.RingsBefore.HasValue);
            AssertEx.Equal(false, exit.SpecialBonusEntryFlag.HasValue);
        }

        // ------------------------------------------------------------------
        // Stop terminators (spec §5.6 table)
        // ------------------------------------------------------------------

        private static void MovieEndStopDropsTheTerminalFrame()
        {
            // rows == M - F - 1, last row observed at framecount M - 1.
            const int movieFrameCount = 100;
            const int armFrame = 10;
            var plan = new SyntheticPlan()
                .Mode(0, armFrame - 1, 0x00)
                .Level(armFrame, movieFrameCount + 50, 0, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(movieFrameCount), plan, 500);

            AssertEx.Equal(1, segmenter.Segments.Count);
            AssertEx.Equal(
                movieFrameCount - armFrame - 1,
                segmenter.Segments[0].TraceFrameCount);
            AssertEx.Equal(movieFrameCount - 1, runner.LastRowFrames[0]);
        }

        private static void TraceStopFrameTruncatesAtExactlyNRows()
        {
            const int stop = 25;
            var plan = new SyntheticPlan()
                .Mode(0, 9, 0x00)
                .Level(10, 500, 0, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(true, 400, stop, 0), plan, 600);

            AssertEx.Equal(1, segmenter.Segments.Count);
            AssertEx.Equal(stop, segmenter.Segments[0].TraceFrameCount);
            // Rows 0..24 are observed at framecounts 11..35 == F + S.
            AssertEx.Equal(10 + stop, runner.LastRowFrames[0]);
        }

        private static void Bk2FrameCountStopTruncatesAtTheLimit()
        {
            // rows == L - F. The limit is compared against the INPUT index
            // bk2_frame_offset + trace_frame, not the framecount.
            const int limit = 60;
            const int armFrame = 10;
            var plan = new SyntheticPlan()
                .Mode(0, armFrame - 1, 0x00)
                .Level(armFrame, 500, 0, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(true, 400, 0, limit), plan, 600);

            AssertEx.Equal(1, segmenter.Segments.Count);
            AssertEx.Equal(
                limit - armFrame, segmenter.Segments[0].TraceFrameCount);
            AssertEx.Equal(limit, runner.LastRowFrames[0]);
        }

        private static void PreTraceSnapshotLatchResetsPerSegment()
        {
            // Spec §4: reset_recording_state clears
            // pre_trace_snapshots_written, so EVERY segment emits its own
            // pre-trace snapshots on its own first recorded frame — and the
            // arm-time Level_frame_counter is overwritten there.
            var plan = new SyntheticPlan()
                .Level(0, 9, 0, 0)
                .Level(10, 19, 1, 0)
                .Level(20, 29, 2, 0);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(30), plan, 300);

            AssertEx.Equal(3, segmenter.Segments.Count);
            AssertEx.Equal(3, runner.PreTraceSnapshotEmissions);
            // FakeS1Host stamps 0xFE04 with the completed frame, so the
            // overwrite is observable: arm at 10 would have read 10, the
            // first recorded frame is 11.
            AssertEx.Equal(11, runner.Arms[1].GameplayFrameCounter);
            AssertEx.Equal(21, runner.Arms[2].GameplayFrameCounter);
        }

        private static void AbsoluteFrameCapMatchesTheLua()
        {
            AssertEx.Equal(
                164, S3KCompleteRunSegmenter.ComputeAbsoluteFrameCap(
                    100, 0, 0));
            // BK2_FRAME_COUNT raises the bound only when larger.
            AssertEx.Equal(
                264, S3KCompleteRunSegmenter.ComputeAbsoluteFrameCap(
                    100, 0, 200));
            AssertEx.Equal(
                164, S3KCompleteRunSegmenter.ComputeAbsoluteFrameCap(
                    100, 0, 50));
            // TRACE_STOP_FRAME lowers it only when strictly smaller.
            AssertEx.Equal(
                114, S3KCompleteRunSegmenter.ComputeAbsoluteFrameCap(
                    100, 50, 0));
            AssertEx.Equal(
                164, S3KCompleteRunSegmenter.ComputeAbsoluteFrameCap(
                    100, 500, 0));
            // No bound at all: the absolute backstop.
            AssertEx.Equal(
                2000000, S3KCompleteRunSegmenter.ComputeAbsoluteFrameCap(
                    0, 0, 0));
            // len == 0 lets any positive stop frame set the bound.
            AssertEx.Equal(
                564, S3KCompleteRunSegmenter.ComputeAbsoluteFrameCap(
                    0, 500, 0));
        }

        // ------------------------------------------------------------------
        // Fixture-literal replays
        // ------------------------------------------------------------------

        /// <summary>
        /// Builds the (mode, zone, act, lock) stream implied by a fixture
        /// table: each segment occupies [offset, legEnd] and the successor
        /// arms at legEnd + 1, except across a special-stage EXIT, where an
        /// unrecorded 0x48 gap runs from the SS's last row frame + 1 to the
        /// next arm - 1. Level and bonus leg ends come from the SUCCESSOR's
        /// published offset (never from the row count under test); SS leg
        /// ends come from the published last-row frames; the terminal
        /// segment's leg ends at movieFrameCount - 1.
        /// </summary>
        private static SyntheticPlan BuildFixturePlan(
            ExpectedSegment[] expected,
            int[] specialStageLastRowFrames,
            int movieFrameCount)
        {
            var plan = new SyntheticPlan();
            if (expected[0].Bk2FrameOffset > 0)
            {
                plan.Mode(0, expected[0].Bk2FrameOffset - 1, 0x00);
            }
            var ssIndex = 0;
            for (var index = 0; index < expected.Length; index++)
            {
                ExpectedSegment segment = expected[index];
                bool terminal = index == expected.Length - 1;
                bool isSs = segment.Kind == Ss;
                int legEnd;
                if (isSs)
                {
                    legEnd = specialStageLastRowFrames[ssIndex++];
                }
                else if (terminal)
                {
                    legEnd = movieFrameCount - 1;
                }
                else
                {
                    legEnd = expected[index + 1].Bk2FrameOffset - 1;
                }

                if (isSs)
                {
                    plan.SpecialStage(
                        segment.Bk2FrameOffset,
                        legEnd,
                        segment.SpecialStageIndex.Value);
                }
                else
                {
                    plan.Level(
                        segment.Bk2FrameOffset,
                        legEnd,
                        segment.ZoneId,
                        segment.Act - 1);
                }

                if (!terminal)
                {
                    int nextOffset = expected[index + 1].Bk2FrameOffset;
                    if (nextOffset > legEnd + 1)
                    {
                        // SS-results + level reload + the locked intro. The
                        // gap length is route state, never a constant.
                        plan.Mode(legEnd + 1, nextOffset - 1, 0x48);
                    }
                }
            }
            return plan;
        }

        private static void AssertMatchesFixtureTable(
            ExpectedSegment[] expected, S3KCompleteRunSegmenter segmenter)
        {
            AssertEx.Equal(expected.Length, segmenter.Segments.Count);
            for (var index = 0; index < expected.Length; index++)
            {
                ExpectedSegment want = expected[index];
                S3KCompleteRunSegment got = segmenter.Segments[index];
                string where = "segment " + Dec(index) + " (" + want.Dir + ") ";
                AssertField(where + "dir", want.Dir, got.Dir);
                AssertField(where + "kind", want.Kind, got.Kind);
                AssertField(
                    where + "trace_profile",
                    want.TraceProfile,
                    got.TraceProfile);
                AssertField(
                    where + "bk2_frame_offset",
                    Dec(want.Bk2FrameOffset),
                    Dec(got.Bk2FrameOffset));
                AssertField(
                    where + "trace_frame_count",
                    Dec(want.TraceFrameCount),
                    Dec(got.TraceFrameCount));
                AssertField(
                    where + "zone_id", Dec(want.ZoneId), Dec(got.ZoneId));
                AssertField(where + "act", Dec(want.Act), Dec(got.Act));
                AssertField(
                    where + "bonus_stage_type",
                    want.BonusStageType,
                    got.BonusStageType);
                AssertField(
                    where + "special_stage_index",
                    want.SpecialStageIndex.HasValue
                        ? Dec(want.SpecialStageIndex.Value)
                        : null,
                    got.SpecialStageIndex.HasValue
                        ? Dec(got.SpecialStageIndex.Value)
                        : null);
                AssertField(
                    where + "segment_index",
                    Dec(index),
                    Dec(got.SegmentIndex));
            }
        }

        /// <summary>
        /// Spec §10.9: offset(i+1) == offset(i) + rows(i) + 1 at every
        /// boundary EXCEPT a special-stage exit, where SS-results and the
        /// level reload run unrecorded in between.
        /// </summary>
        private static void AssertSuccessionIdentity(
            S3KCompleteRunSegmenter segmenter)
        {
            for (var index = 0;
                index < segmenter.Segments.Count - 1;
                index++)
            {
                S3KCompleteRunSegment current = segmenter.Segments[index];
                S3KCompleteRunSegment next = segmenter.Segments[index + 1];
                int predicted = current.Bk2FrameOffset
                    + current.TraceFrameCount + 1;
                if (current.Kind == Ss)
                {
                    if (next.Bk2FrameOffset <= predicted)
                    {
                        throw new InvalidOperationException(
                            "Special-stage exit " + current.Dir + " -> "
                            + next.Dir + " must leave an unrecorded gap;"
                            + " predicted " + predicted + ", got "
                            + next.Bk2FrameOffset + ".");
                    }
                    continue;
                }
                AssertField(
                    "succession " + current.Dir + " -> " + next.Dir,
                    Dec(predicted),
                    Dec(next.Bk2FrameOffset));
            }
        }

        private static void ReproducesSetAFixtureTable()
        {
            SyntheticPlan plan = BuildFixturePlan(
                SetA, new int[0], SetAPredictedFbzArmFrame);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(SetAPredictedFbzArmFrame),
                plan,
                SetAPredictedFbzArmFrame + 10);

            AssertMatchesFixtureTable(SetA, segmenter);
            AssertSuccessionIdentity(segmenter);
            // No detour and no bonus zone in the whole pass, so the Lua's
            // manifest emission gate (#transitions == 0 and no run id) is
            // what leaves set (A) without a run_manifest.json.
            AssertEx.Equal(0, segmenter.Transitions.Count);
            for (var index = 0; index < SetA.Length; index++)
            {
                AssertField(
                    "observed rows " + SetA[index].Dir,
                    Dec(SetA[index].TraceFrameCount),
                    Dec(runner.ObservedRowCounts[index]));
                AssertField(
                    "first row frame " + SetA[index].Dir,
                    Dec(SetA[index].Bk2FrameOffset + 1),
                    Dec(runner.FirstRowFrames[index]));
                AssertField(
                    "last row frame " + SetA[index].Dir,
                    Dec(SetA[index].Bk2FrameOffset
                        + SetA[index].TraceFrameCount),
                    Dec(runner.LastRowFrames[index]));
            }
        }

        private static void ReproducesSetBFixtureTable()
        {
            SyntheticPlan plan = BuildFixturePlan(
                SetB, SetBSpecialStageLastRowFrames, SetBMovieFrameCount);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(SetBMovieFrameCount),
                plan,
                SetBMovieFrameCount + 10);

            AssertMatchesFixtureTable(SetB, segmenter);
            AssertSuccessionIdentity(segmenter);
            for (var index = 0; index < SetB.Length; index++)
            {
                AssertField(
                    "observed rows " + SetB[index].Dir,
                    Dec(SetB[index].TraceFrameCount),
                    Dec(runner.ObservedRowCounts[index]));
                AssertField(
                    "opened dir " + Dec(index),
                    SetB[index].Dir,
                    runner.OpenedDirs[index]);
            }
        }

        private static void ReproducesSetBTransitionTable()
        {
            SyntheticPlan plan = BuildFixturePlan(
                SetB, SetBSpecialStageLastRowFrames, SetBMovieFrameCount);
            var runner = new PlanRunner();
            S3KCompleteRunSegmenter segmenter = runner.Run(
                new S3KCompleteRunSegmenter(SetBMovieFrameCount),
                plan,
                SetBMovieFrameCount + 10);

            // Spec §7.2: 22 transitions for 24 boundaries — the two plain
            // level -> level boundaries 8->9 (aiz_5 -> hcz) and 19->20
            // (hcz_6 -> mgz) produce no record at all, which is exactly why
            // from/to must never be derived from list position.
            AssertEx.Equal(22, segmenter.Transitions.Count);

            var expected = new List<string>();
            for (var index = 0; index < SetB.Length - 1; index++)
            {
                ExpectedSegment from = SetB[index];
                ExpectedSegment to = SetB[index + 1];
                if (from.Kind == Bonus || from.Kind == Ss)
                {
                    expected.Add(Transition(
                        index,
                        index + 1,
                        RunManifestTransition.StageExitKind,
                        to.Bk2FrameOffset));
                }
                if (to.Kind == Bonus)
                {
                    expected.Add(Transition(
                        index,
                        index + 1,
                        RunManifestTransition.StarpostBonusKind,
                        to.Bk2FrameOffset));
                }
                else if (to.Kind == Ss)
                {
                    expected.Add(Transition(
                        index,
                        index + 1,
                        RunManifestTransition.GiantRingKind,
                        to.Bk2FrameOffset));
                }
            }

            AssertEx.Equal(expected.Count, segmenter.Transitions.Count);
            for (var index = 0; index < expected.Count; index++)
            {
                RunManifestTransition got = segmenter.Transitions[index];
                AssertField(
                    "transition " + Dec(index),
                    expected[index],
                    Transition(
                        got.FromSegment,
                        got.ToSegment,
                        got.EntryKind,
                        got.ModeChangeBk2Frame));
            }

            // Spot-check the boundary whose record index does NOT equal its
            // boundary index: hcz_2 -> ss is boundary 11->12 but transition
            // record 10, because boundaries 8->9 and 19->20 contribute no
            // record. mode_change_bk2_frame is the entry frame it was
            // pushed on, i.e. the TO segment's bk2_frame_offset.
            AssertEx.Equal(
                RunManifestTransition.GiantRingKind,
                segmenter.Transitions[10].EntryKind);
            AssertEx.Equal(11, segmenter.Transitions[10].FromSegment);
            AssertEx.Equal(12, segmenter.Transitions[10].ToSegment);
            AssertEx.Equal(48174, segmenter.Transitions[10].ModeChangeBk2Frame);
        }

        private static string Transition(
            int from, int to, string kind, int frame)
        {
            return Dec(from) + "->" + Dec(to) + " " + kind + " @"
                + Dec(frame);
        }

        private static void AssertField(
            string what, string expected, string actual)
        {
            if (!string.Equals(expected, actual, StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    what + ": expected <" + (expected ?? "(null)")
                    + "> but was <" + (actual ?? "(null)") + ">.");
            }
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }
    }
}
