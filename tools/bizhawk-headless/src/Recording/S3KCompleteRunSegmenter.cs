using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// What the segmenter decided for one evaluated frame — the observable
    /// result of running the whole on_frame_end predicate chain once.
    /// </summary>
    public enum S3KCompleteRunFrameAction
    {
        /// <summary>
        /// on_frame_end returned without recording anything. Covers the
        /// pre-first-arm frames, every arm frame (which belongs to no
        /// segment), the special-stage entry frame, the special-stage exit
        /// frame, and every non-level-family excursion while armed.
        /// </summary>
        None,

        /// <summary>
        /// Write one 42-column level/bonus physics.csv row plus the aux
        /// cascade, for row index <see cref="S3KCompleteRunSegmenter.RowIndex"/>.
        /// </summary>
        LevelRow,

        /// <summary>
        /// Write one 20-column special-stage physics.csv row for row index
        /// <see cref="S3KCompleteRunSegmenter.RowIndex"/>. SS segments emit
        /// no aux events at all — their aux_state.jsonl is opened and closed
        /// byte-empty (spec §5.3).
        /// </summary>
        SpecialStageRow,

        /// <summary>
        /// A stop predicate latched `finished`. The caller must stop
        /// advancing and call
        /// <see cref="S3KCompleteRunSegmenter.FinalizeRunEnd"/>.
        /// </summary>
        Finished
    }

    /// <summary>
    /// Everything captured at one segment's arm, handed to the writer layer
    /// so it can open the segment's files and emit its first metadata.json.
    /// The level/bonus fields and the special-stage field are mutually
    /// exclusive; <see cref="Kind"/> says which set is live.
    ///
    /// <see cref="GameplayFrameCounter"/> is the one mutable field: it is
    /// read at arm and then OVERWRITTEN on the segment's first recorded
    /// frame (Lua step 13), so for an arm-and-return segment the shipped
    /// value is one V-int later than the arm-time read.
    /// </summary>
    public sealed class S3KSegmentArm
    {
        public S3KSegmentArm(
            string dirToken,
            string kind,
            string traceProfile,
            int bk2FrameOffset,
            int segmentIndex)
        {
            if (dirToken == null)
            {
                throw new ArgumentNullException("dirToken");
            }
            if (kind == null)
            {
                throw new ArgumentNullException("kind");
            }
            if (traceProfile == null)
            {
                throw new ArgumentNullException("traceProfile");
            }
            DirToken = dirToken;
            Kind = kind;
            TraceProfile = traceProfile;
            Bk2FrameOffset = bk2FrameOffset;
            SegmentIndex = segmentIndex;
        }

        public string DirToken { get; private set; }
        public string Kind { get; private set; }
        public string TraceProfile { get; private set; }
        public int Bk2FrameOffset { get; private set; }

        /// <summary>
        /// #segments_done at arm time, which IS this segment's own 0-based
        /// index because both metadata writers run before the segments_done
        /// append (spec §6.6).
        /// </summary>
        public int SegmentIndex { get; private set; }

        // Level / bonus arm context (zone_token_for(zone_id) for ZoneToken,
        // so a bonus segment's metadata "zone" is its bonus token).
        public int ZoneId { get; set; }
        public string ZoneToken { get; set; }

        /// <summary>Raw Current_act; every emission is this + 1.</summary>
        public int ActRaw { get; set; }

        public int StartX { get; set; }
        public int StartY { get; set; }
        public uint RngSeed { get; set; }

        /// <summary>gumball / pachinko / slots; null for a level.</summary>
        public string BonusStageType { get; set; }

        /// <summary>
        /// u32be 0xFE0C captured at arm, bonus segments ONLY. Null (=> the
        /// metadata key is absent, not null) for level and SS segments.
        /// </summary>
        public uint? VIntRunCount { get; set; }

        public int GameplayFrameCounter { get; set; }

        /// <summary>u8 0xFE16 at SS arm; null for level/bonus.</summary>
        public int? SpecialStageIndex { get; set; }
    }

    /// <summary>
    /// One finalized segment (Lua segments_done entry) — the segmentation
    /// half of a run_manifest.json segment record plus the base token.
    /// </summary>
    public sealed class S3KCompleteRunSegment
    {
        public S3KCompleteRunSegment(
            string token,
            string dir,
            string kind,
            string traceProfile,
            int bk2FrameOffset,
            int traceFrameCount,
            int zoneId,
            int act,
            string bonusStageType,
            int? specialStageIndex,
            int segmentIndex)
        {
            Token = token;
            Dir = dir;
            Kind = kind;
            TraceProfile = traceProfile;
            Bk2FrameOffset = bk2FrameOffset;
            TraceFrameCount = traceFrameCount;
            ZoneId = zoneId;
            Act = act;
            BonusStageType = bonusStageType;
            SpecialStageIndex = specialStageIndex;
            SegmentIndex = segmentIndex;
        }

        public string Token { get; private set; }
        public string Dir { get; private set; }
        public string Kind { get; private set; }
        public string TraceProfile { get; private set; }
        public int Bk2FrameOffset { get; private set; }
        public int TraceFrameCount { get; private set; }
        public int ZoneId { get; private set; }

        /// <summary>1-based (start_act + 1); hardcoded 0 for SS.</summary>
        public int Act { get; private set; }

        public string BonusStageType { get; private set; }
        public int? SpecialStageIndex { get; private set; }
        public int SegmentIndex { get; private set; }
    }

    /// <summary>
    /// Native port of the S3K complete-run recorder's SEGMENTATION engine
    /// (tools/bizhawk/s3k_complete_run_recorder.lua v6.34-s3k-completerun;
    /// spec tools/bizhawk-headless/docs/s3k-complete-run-behavior.md): the
    /// level / bonus / special-stage state machine, every arm / finalize /
    /// stop predicate in the Lua's exact on_frame_end source order, the
    /// bk2_frame_offset and trace_frame_count derivation, the repeat-visit
    /// "_2"/"_3" directory suffixes, and each segment's zone_id / act /
    /// kind / trace_profile / bonus_stage_type / special_stage_index /
    /// segment_index.
    ///
    /// It owns ONLY segmentation. Everything the complete-run recorder
    /// shares character-for-character with the STANDARD recorder — the RAM
    /// map, the 42-column physics.csv row, the aux cascade, the metadata
    /// byte layout — stays in <see cref="S3KRam"/>,
    /// <see cref="S3KTraceCsvWriter"/>, <see cref="S3KAuxEventEngine"/> and
    /// <see cref="S3KTraceMetadataWriter"/> and is DELEGATED to, never
    /// forked (spec §2.1). The one table it must not delegate is the zone
    /// name: see <see cref="S3KZoneTokens"/>.
    ///
    /// Contract. <see cref="Step"/> is the whole on_frame_end chain,
    /// evaluated POST-advance against the end-of-frame state of
    /// host.CompletedFrame, in source order, with the early returns
    /// preserved (spec §1.1/§10.1 — this exact ordering was independently
    /// got wrong by both the S1 and S2 ports). The Lua loop calls
    /// on_frame_end BEFORE the first frameadvance, so a faithful driver
    /// calls Step once at CompletedFrame 0 and then once after each
    /// advance; iteration 0 is a no-op for every real capture because
    /// nothing is armed and the arm gate needs Game_mode 0x0C. When Step
    /// returns <see cref="S3KCompleteRunFrameAction.Finished"/> the driver
    /// stops advancing and calls <see cref="FinalizeRunEnd"/>.
    ///
    /// Two frame indices are in play and must not be conflated (spec §5.6):
    /// a row is WRITTEN while observing BK2 framecount
    /// bk2_frame_offset + 1 + rowIndex, but its INPUT column is BK2 input
    /// row bk2_frame_offset + rowIndex — correct because BizHawk applies
    /// the input recorded at frame k during the advance from k to k+1.
    ///
    /// There is no discard path. Every armed segment is published however
    /// short; the Lua's reset_recording_state(false) file-delete branch has
    /// no call site in this recorder (spec §5.7).
    /// </summary>
    public sealed class S3KCompleteRunSegmenter
    {
        public const string LevelTraceProfile = "complete_run";
        public const string BonusStageTraceProfile = "s3k_bonus_stage";
        public const string SpecialStageTraceProfile = "s3k_special_stage";

        private const int FrameCapMargin = 64;
        private const int UnboundedFrameCap = 2000000;

        private readonly bool movieLoaded;
        private readonly int movieFrameCount;
        private readonly int traceStopFrame;
        private readonly int bk2FrameCount;

        private readonly List<S3KCompleteRunSegment> segments =
            new List<S3KCompleteRunSegment>();
        private readonly List<RunManifestTransition> transitions =
            new List<RunManifestTransition>();

        // segment_dir_counts: base token -> arm count. Never reset, counts
        // ARMS rather than zone visits, and the "ss" detour token shares
        // the table with every zone/bonus token (spec §5.5).
        private readonly Dictionary<string, int> dirCounts =
            new Dictionary<string, int>();

        // current_segment_zone: the ROM zone id of the armed LEVEL/BONUS
        // segment, null when none. Cleared by BOTH finalize paths and never
        // set by the SS arm — which is the only reason the arm gate can
        // re-fire into the SAME zone after a detour (spec §10.4).
        private int? currentSegmentZone;

        private bool preTraceSnapshotsWritten;
        private bool runEndFinalized;

        /// <param name="movieLoaded">
        /// movie.isloaded(). Always true for the native harness, which
        /// drives from a Bk2Movie; false only exercises the Lua's unused
        /// no-movie configuration.
        /// </param>
        /// <param name="movieFrameCount">movie.length() (0 when unloaded).</param>
        /// <param name="traceStopFrame">OGGF_TRACE_STOP_FRAME; 0 = unset.</param>
        /// <param name="bk2FrameCount">OGGF_BK2_FRAME_COUNT; 0 = unset.</param>
        public S3KCompleteRunSegmenter(
            bool movieLoaded,
            int movieFrameCount,
            int traceStopFrame,
            int bk2FrameCount)
        {
            if (movieFrameCount < 0)
            {
                throw new ArgumentOutOfRangeException("movieFrameCount");
            }
            if (traceStopFrame < 0)
            {
                throw new ArgumentOutOfRangeException("traceStopFrame");
            }
            if (bk2FrameCount < 0)
            {
                throw new ArgumentOutOfRangeException("bk2FrameCount");
            }
            this.movieLoaded = movieLoaded;
            this.movieFrameCount = movieFrameCount;
            this.traceStopFrame = traceStopFrame;
            this.bk2FrameCount = bk2FrameCount;
            AbsoluteFrameCap = ComputeAbsoluteFrameCap(
                movieLoaded ? movieFrameCount : 0,
                traceStopFrame,
                bk2FrameCount);
            RowIndex = -1;
        }

        /// <summary>
        /// The ordinary native configuration: a loaded movie, no env stops.
        /// </summary>
        public S3KCompleteRunSegmenter(int movieFrameCount)
            : this(true, movieFrameCount, 0, 0)
        {
        }

        /// <summary>Raised at every arm, before the first row.</summary>
        public Action<S3KSegmentArm> SegmentOpened { get; set; }

        /// <summary>Raised at every finalize, after the row count is final.</summary>
        public Action<S3KCompleteRunSegment> SegmentClosed { get; set; }

        public bool Finished { get; private set; }

        /// <summary>
        /// A segment is armed and open. SHARED between level/bonus and SS
        /// segments (spec §10.3) — the SS arm sets the same started /
        /// trace_frame / bk2_frame_offset state, which is why the env stops
        /// truncate SS segments too and why the SS entry branch is gated on
        /// the detour flag rather than on this.
        /// </summary>
        public bool Started { get; private set; }

        public bool SpecialStageDetourActive { get; private set; }

        /// <summary>Rows written into the armed segment so far.</summary>
        public int TraceFrame { get; private set; }

        public int Bk2FrameOffset { get; private set; }

        /// <summary>
        /// The row index the last <see cref="Step"/> asked the caller to
        /// write; -1 when it asked for no row.
        /// </summary>
        public int RowIndex { get; private set; }

        /// <summary>
        /// The last <see cref="Step"/> crossed the one-shot pre-trace
        /// snapshot latch, i.e. this is the segment's FIRST recorded frame:
        /// the caller must emit the cpu_state_snapshot + object_state
        /// snapshots into aux before the row. The latch resets per segment.
        /// </summary>
        public bool EmittedPreTraceSnapshots { get; private set; }

        /// <summary>The armed segment's arm record; null when none.</summary>
        public S3KSegmentArm CurrentArm { get; private set; }

        public IList<S3KCompleteRunSegment> Segments
        {
            get { return segments; }
        }

        public IList<RunManifestTransition> Transitions
        {
            get { return transitions; }
        }

        /// <summary>
        /// FRAME_CAP: a runaway backstop evaluated by the DRIVER's loop,
        /// not by on_frame_end. It never fires in any fixture capture.
        /// </summary>
        public int AbsoluteFrameCap { get; private set; }

        internal static int ComputeAbsoluteFrameCap(
            int movieFrameCount, int traceStopFrame, int bk2FrameCount)
        {
            int len = movieFrameCount;
            if (bk2FrameCount > len)
            {
                len = bk2FrameCount;
            }
            if (traceStopFrame > 0 && (len == 0 || traceStopFrame < len))
            {
                len = traceStopFrame;
            }
            return len > 0 ? len + FrameCapMargin : UnboundedFrameCap;
        }

        /// <summary>
        /// The main loop's backstop check, run after Step and before the
        /// next advance. Returns true once the cap has latched `finished`.
        /// </summary>
        public bool ApplyFrameCap(int completedFrame)
        {
            if (!Finished && completedFrame >= AbsoluteFrameCap)
            {
                Finished = true;
            }
            return Finished;
        }

        /// <summary>
        /// One full on_frame_end evaluation against the end-of-frame state
        /// of host.CompletedFrame. The numbered steps below are the spec's
        /// §1.2 chain and the Lua's source order; do not reorder, merge or
        /// hoist them.
        /// </summary>
        public S3KCompleteRunFrameAction Step(IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            RowIndex = -1;
            EmittedPreTraceSnapshots = false;

            // (1) Terminal latch.
            if (Finished)
            {
                return S3KCompleteRunFrameAction.Finished;
            }

            int frameNow = host.CompletedFrame;

            // (2) HARD movie-input-end guard. UNCONDITIONAL — not gated on
            // `started` — and above everything else, which is why a
            // movie-terminated final segment ends at movie.length() - 1
            // instead of running into the post-movie attract loop, and why
            // it preempts steps (11)/(12) whenever a movie is loaded. The
            // STANDARD recorder has no equivalent.
            if (movieLoaded && movieFrameCount > 0
                && frameNow >= movieFrameCount)
            {
                return Finish();
            }

            // (3) Headless env stops. Gated on `started`, so they truncate
            // whichever segment is open — SS segments included.
            if (Started)
            {
                if (traceStopFrame > 0 && TraceFrame >= traceStopFrame)
                {
                    return Finish();
                }
                if (bk2FrameCount > 0
                    && Bk2FrameOffset + TraceFrame >= bk2FrameCount)
                {
                    return Finish();
                }
                if (!movieLoaded)
                {
                    return Finish();
                }
            }

            // (4) The frame's two driving reads.
            int gameMode = S3KRam.U8(host, S3KRam.GameMode);
            int zoneId = S3KRam.U8(host, S3KRam.Zone);

            // (5)/(6) Special-stage detour. The entry test is gated on the
            // DETOUR flag and never on `started` alone: StartSpecialStage
            // sets Started, so a started-only test would re-finalize and
            // re-open on every 0x34 frame. `started` is nonetheless
            // required to enter at all, so a 0x34 detour occurring before
            // the first level segment ever armed produces no SS segment.
            if (Started && gameMode == S3KRam.GameModeSpecialStage)
            {
                if (!SpecialStageDetourActive)
                {
                    FinalizeSegment();
                    PushGiantRingTransition(frameNow, host);
                    StartSpecialStageSegment(frameNow, host);
                    SpecialStageDetourActive = true;
                    // Entry frame is recorded in NEITHER segment.
                    return S3KCompleteRunFrameAction.None;
                }
                RowIndex = TraceFrame;
                TraceFrame++;
                return S3KCompleteRunFrameAction.SpecialStageRow;
            }

            // (7) Detour exit: the first non-0x34 frame. Evaluated BEFORE
            // the arm gate so the SS segment closes exactly once whatever
            // mode follows. When that mode is level-family we fall through
            // to the arm gate on this SAME frame (it will not pass on a
            // 0x4C/0x8C handoff frame, and will pass on the first settled
            // 0x0C); on 0x48 SS-results / fade frames we return.
            if (SpecialStageDetourActive)
            {
                FinalizeSpecialStageSegment();
                SpecialStageDetourActive = false;
                if (!S3KRam.IsLevelFamilyMode(gameMode))
                {
                    return S3KCompleteRunFrameAction.None;
                }
            }

            // (8) Level / bonus arm gate. RAW 0x0C, not the level-family
            // mask: during the 0x4C/0x8C load handoff the player object is
            // not placed yet (x_pos/y_pos read 0) and Ctrl_1_locked can
            // briefly read 0, so arming there yields a wrong frame-0
            // camera/position. Both control gates skip the locked level
            // intro. `zone_id != current_segment_zone` is a ONE-TIME gate
            // per segment: once armed for zone Z everything after — later
            // control locks, the seamless act1->act2 transition, the
            // act2->next-zone exit handoff — records into the same segment.
            if (gameMode == S3KRam.GameModeLevel
                && (!currentSegmentZone.HasValue
                    || zoneId != currentSegmentZone.Value))
            {
                int ctrlLockTimer = S3KRam.U16(
                    host, S3KRam.PlayerBase + S3KRam.OffMoveLock);
                int ctrlLocked = S3KRam.U8(host, S3KRam.Ctrl1Locked);
                if (ctrlLockTimer == 0 && ctrlLocked == 0)
                {
                    if (Started)
                    {
                        FinalizeSegment();
                    }
                    // Two INDEPENDENT ifs, not an if/elseif: a
                    // bonus->bonus boundary would push both with identical
                    // from/to indices (no fixture exercises it, but port
                    // the shape verbatim).
                    if (segments.Count > 0)
                    {
                        string lastKind = segments[segments.Count - 1].Kind;
                        if (lastKind == RunManifestSegment.BonusStageKind
                            || lastKind
                                == RunManifestSegment.SpecialStageKind)
                        {
                            PushStageExitTransition(frameNow, host);
                        }
                    }
                    if (S3KZoneTokens.BonusToken(zoneId) != null)
                    {
                        PushStarpostBonusTransition(frameNow, host);
                    }
                    StartNewSegment(zoneId, frameNow, host);
                    // Arm-and-return: the arm frame belongs to no segment.
                    return S3KCompleteRunFrameAction.None;
                }
            }

            // (9) Nothing armed yet (Sega/title/level-load frames before
            // the first zone's settled 0x0C).
            if (!Started)
            {
                return S3KCompleteRunFrameAction.None;
            }

            // (10) Hard mode guard on the row write. The Lua re-reads
            // 0xF600 into a fresh local here; same address, same frame,
            // same value — a redundant read with no behavioral effect,
            // reproduced rather than "fixed" into a reordering. Returning
            // (not finishing) is what makes the trailing 0x4C/0x8C
            // zone-exit handoff frames land in the CURRENT segment and
            // what lets a 0x00/0x04/0x08 excursion skip rows without ever
            // closing the segment.
            int guardMode = S3KRam.U8(host, S3KRam.GameMode);
            if (!S3KRam.IsLevelFamilyMode(guardMode))
            {
                return S3KCompleteRunFrameAction.None;
            }

            // (11)/(12) Movie-end stops. OGGF_BK2_FRAME_COUNT above
            // movie.length() raises the limit and sets allow_post_movie_tail
            // (which suppresses the FINISHED stop), but it can never
            // actually extend recording while a movie is loaded: step (2)
            // is unconditional and fires a frame earlier. Both are
            // therefore unreachable in the movie-backed configuration and
            // are ported for fidelity, not effect.
            if (movieLoaded)
            {
                int endFrameLimit = movieFrameCount;
                bool allowPostMovieTail = false;
                if (bk2FrameCount > endFrameLimit)
                {
                    endFrameLimit = bk2FrameCount;
                    allowPostMovieTail = true;
                }
                if (endFrameLimit > 0
                    && Bk2FrameOffset + TraceFrame >= endFrameLimit)
                {
                    return Finish();
                }
                if (!allowPostMovieTail && frameNow >= movieFrameCount)
                {
                    return Finish();
                }
            }

            // (13) First recorded frame of THIS segment: the pre-trace
            // snapshots, and the Level_frame_counter recapture that
            // overwrites the arm-time value.
            if (!preTraceSnapshotsWritten)
            {
                preTraceSnapshotsWritten = true;
                EmittedPreTraceSnapshots = true;
                CurrentArm.GameplayFrameCounter =
                    S3KRam.U16(host, S3KRam.LevelFrameCounter);
            }

            // (14) The row.
            RowIndex = TraceFrame;
            TraceFrame++;
            return S3KCompleteRunFrameAction.LevelRow;
        }

        /// <summary>
        /// End-of-run finalize (spec §5.7), run by the driver once the loop
        /// has stopped. An open SS segment MUST route through the SS
        /// finalize: the level finalize hardcodes kind level/bonus_stage,
        /// which would stamp "level" on a directory full of 20-column SS
        /// rows — and because the giant_ring transition was already pushed
        /// at SS open, the corrupt manifest would still validate. A
        /// truncated SS segment is legitimate, correctly-labeled data.
        /// Idempotent.
        /// </summary>
        public void FinalizeRunEnd()
        {
            if (runEndFinalized)
            {
                return;
            }
            runEndFinalized = true;
            if (SpecialStageDetourActive)
            {
                FinalizeSpecialStageSegment();
                SpecialStageDetourActive = false;
            }
            else
            {
                FinalizeSegment();
            }
        }

        private S3KCompleteRunFrameAction Finish()
        {
            Finished = true;
            return S3KCompleteRunFrameAction.Finished;
        }

        /// <summary>
        /// start_new_segment. trace_frame is NOT explicitly reset here — it
        /// is already 0, either from load or from the reset inside the
        /// preceding finalize.
        /// </summary>
        private void StartNewSegment(
            int zoneId, int frameNow, IGpgxHost host)
        {
            string baseToken = S3KZoneTokens.ZoneTokenFor(zoneId);
            string bonusToken = S3KZoneTokens.BonusToken(zoneId);
            bool isBonus = bonusToken != null;
            var arm = new S3KSegmentArm(
                NextDirToken(baseToken),
                isBonus
                    ? RunManifestSegment.BonusStageKind
                    : RunManifestSegment.LevelKind,
                isBonus ? BonusStageTraceProfile : LevelTraceProfile,
                frameNow,
                segments.Count);
            arm.ZoneId = zoneId;
            arm.ZoneToken = baseToken;
            arm.ActRaw = S3KRam.U8(host, S3KRam.Act);
            arm.StartX = S3KRam.U16(
                host, S3KRam.PlayerBase + S3KRam.OffXPos);
            arm.StartY = S3KRam.U16(
                host, S3KRam.PlayerBase + S3KRam.OffYPos);
            arm.RngSeed = S3KRam.U32(host, S3KRam.RngSeed);
            arm.GameplayFrameCounter =
                S3KRam.U16(host, S3KRam.LevelFrameCounter);
            arm.BonusStageType = bonusToken;
            // Only bonus segments carry the free-running V-int counter
            // base; for a level segment the key is ABSENT, not null.
            arm.VIntRunCount = isBonus
                ? (uint?)S3KRam.U32(host, S3KRam.VIntRunCount)
                : null;

            Started = true;
            currentSegmentZone = zoneId;
            Bk2FrameOffset = frameNow;
            preTraceSnapshotsWritten = false;
            CurrentArm = arm;
            Notify(SegmentOpened, arm);
        }

        /// <summary>
        /// finalize_segment. The metadata write happens BEFORE the
        /// segments_done append, which is exactly why segment_index equals
        /// the segment's own 0-based index.
        /// </summary>
        private void FinalizeSegment()
        {
            if (!Started)
            {
                return;
            }
            S3KSegmentArm arm = CurrentArm;
            var record = new S3KCompleteRunSegment(
                S3KZoneTokens.ZoneTokenFor(arm.ZoneId),
                arm.DirToken,
                arm.Kind,
                arm.TraceProfile,
                Bk2FrameOffset,
                TraceFrame,
                arm.ZoneId,
                arm.ActRaw + 1,
                arm.BonusStageType,
                null,
                arm.SegmentIndex);
            CloseSegment(record);
        }

        /// <summary>
        /// start_ss_segment. It deliberately does NOT set
        /// current_segment_zone: that stays null from the preceding
        /// finalize, so the level arm gate's zone test is trivially true
        /// when the detour ends — including a detour that returns into the
        /// very same zone.
        /// </summary>
        private void StartSpecialStageSegment(int frameNow, IGpgxHost host)
        {
            var arm = new S3KSegmentArm(
                NextDirToken(S3KZoneTokens.SpecialStageToken),
                RunManifestSegment.SpecialStageKind,
                SpecialStageTraceProfile,
                frameNow,
                segments.Count);
            // Tracks the ROM's own stage counter, NOT the _N dir suffix;
            // never derive one from the other.
            arm.SpecialStageIndex =
                S3KRam.U8(host, S3KRam.CurrentSpecialStage);

            Started = true;
            Bk2FrameOffset = frameNow;
            TraceFrame = 0;
            preTraceSnapshotsWritten = false;
            CurrentArm = arm;
            Notify(SegmentOpened, arm);
        }

        /// <summary>
        /// finalize_ss_segment: zone_id and act are HARDCODED 0 in the
        /// segments_done record (and omitted entirely from SS metadata).
        /// </summary>
        private void FinalizeSpecialStageSegment()
        {
            if (!Started)
            {
                return;
            }
            S3KSegmentArm arm = CurrentArm;
            var record = new S3KCompleteRunSegment(
                S3KZoneTokens.SpecialStageToken,
                arm.DirToken,
                RunManifestSegment.SpecialStageKind,
                SpecialStageTraceProfile,
                Bk2FrameOffset,
                TraceFrame,
                0,
                0,
                null,
                arm.SpecialStageIndex,
                arm.SegmentIndex);
            CloseSegment(record);
        }

        /// <summary>
        /// The shared tail of both finalize paths: append, hand the record
        /// to the writer layer, then reset_recording_state(true) — which
        /// clears the armed state and the pre-trace latch, and clears
        /// current_segment_zone (spec §10.4). keep_files is true at BOTH
        /// call sites, so the Lua's file-delete branch is dead code and a
        /// native port must never delete a published segment.
        /// </summary>
        private void CloseSegment(S3KCompleteRunSegment record)
        {
            segments.Add(record);
            Notify(SegmentClosed, record);
            Started = false;
            TraceFrame = 0;
            Bk2FrameOffset = 0;
            preTraceSnapshotsWritten = false;
            CurrentArm = null;
            currentSegmentZone = null;
        }

        /// <summary>
        /// giant_ring, pushed at SS entry AFTER the level finalize so
        /// from/to already count the just-finished segment. Never derive
        /// the indices from list position: plain level->level zone changes
        /// are boundaries with NO transition record.
        /// </summary>
        private void PushGiantRingTransition(int frameNow, IGpgxHost host)
        {
            var entry = new RunManifestTransition(
                segments.Count - 1,
                segments.Count,
                RunManifestTransition.GiantRingKind,
                frameNow);
            ApplyEntryFields(entry, host);
            transitions.Add(entry);
        }

        /// <summary>starpost_bonus: the same six entry-side fields.</summary>
        private void PushStarpostBonusTransition(int frameNow, IGpgxHost host)
        {
            var entry = new RunManifestTransition(
                segments.Count - 1,
                segments.Count,
                RunManifestTransition.StarpostBonusKind,
                frameNow);
            ApplyEntryFields(entry, host);
            transitions.Add(entry);
        }

        private void PushStageExitTransition(int frameNow, IGpgxHost host)
        {
            var entry = new RunManifestTransition(
                segments.Count - 1,
                segments.Count,
                RunManifestTransition.StageExitKind,
                frameNow);
            entry.RingsAfter = S3KRam.U16(host, S3KRam.RingCount);
            entry.EmeraldsAfter = S3KRam.U8(host, S3KRam.EmeraldCount);
            transitions.Add(entry);
        }

        private static void ApplyEntryFields(
            RunManifestTransition entry, IGpgxHost host)
        {
            entry.SpecialBonusEntryFlag =
                S3KRam.U8(host, S3KRam.SpecialBonusEntryFlag);
            entry.SavedXPos = S3KRam.U16(host, S3KRam.SavedXPos);
            entry.SavedYPos = S3KRam.U16(host, S3KRam.SavedYPos);
            entry.LastStarPostHit =
                S3KRam.U8(host, S3KRam.LastStarPostHit);
            entry.RingsBefore = S3KRam.U16(host, S3KRam.RingCount);
            entry.EmeraldsBefore = S3KRam.U8(host, S3KRam.EmeraldCount);
        }

        /// <summary>
        /// The first arm of a base token yields the bare token; the n-th
        /// (n &gt; 1) yields token_n. Counts arms, never resets.
        /// </summary>
        private string NextDirToken(string baseToken)
        {
            int count;
            dirCounts.TryGetValue(baseToken, out count);
            count++;
            dirCounts[baseToken] = count;
            return count == 1
                ? baseToken
                : baseToken + "_" + count.ToString(
                    System.Globalization.CultureInfo.InvariantCulture);
        }

        private static void Notify<T>(Action<T> handler, T value)
        {
            if (handler != null)
            {
                handler(value);
            }
        }
    }
}
