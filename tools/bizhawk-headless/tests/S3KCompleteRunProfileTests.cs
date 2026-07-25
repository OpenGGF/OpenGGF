using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Stage-B tests for the S3K COMPLETE-RUN recorder's profile surface
    /// (tools/bizhawk/s3k_complete_run_recorder.lua v6.32-s3k-completerun;
    /// spec tools/bizhawk-headless/docs/s3k-completerun-profiles.md):
    ///
    /// - the 42-column complete_run / s3k_bonus_stage physics row, which
    ///   is now the standard recorder's row with NO substitution at all:
    ///   gameplay_frame_counter reads Level_frame_counter (0xFE04) in both
    ///   recorders since Lua v6.31-s3k moved the standard one off the dead
    ///   Debug_placement_mode read at 0xFE08;
    /// - the 20-column s3k_special_stage physics row, a completely separate
    ///   writer with the opposite numeric convention;
    /// - game_paused_state, the ONE aux family the complete-run recorder
    ///   adds over the standard recorder (verified by diffing the two Lua
    ///   scripts' "event" literals: game_paused_state is the only delta),
    ///   including its exact emission slot and its vfc source.
    ///
    /// Every expected string below is LITERAL fixture bytes, grepped from
    /// the gunzipped (A)/(C) fixtures under
    /// src/test/resources/traces/s3k/. Never edit one to make a test pass.
    /// </summary>
    internal static class S3KCompleteRunProfileTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun level header matches the completerun fixture"
                + " header",
                LevelHeaderMatchesCompleteRunFixtureHeader));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun reproduces aiz_completerun physics row 0",
                ReproducesAizCompleteRunRowZero));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun reproduces bonus_gumball physics row 0",
                ReproducesBonusGumballRowZero));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun row and standard row both read"
                + " Level_frame_counter",
                LevelAndStandardRowsReadTheSameFrameCounter));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun special-stage header matches the"
                + " special_stage fixture header",
                SpecialStageHeaderMatchesFixtureHeader));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun reproduces special_stage physics row 0",
                ReproducesSpecialStageRowZero));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun special-stage row emits rate before"
                + " rate_timer (fixture row 1000)",
                ReproducesSpecialStageRowOneThousand));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun special-stage row wraps negative velocity"
                + " (fixture row 3295)",
                ReproducesSpecialStageNegativeVelocityRow));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun special-stage row renders y_pos unsigned"
                + " (fixture row 4629)",
                ReproducesSpecialStageUnsignedYPosRow));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun special-stage row reports the host lag flag"
                + " (fixture row 22)",
                ReproducesSpecialStageLagRow));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun special-stage started renders as a 0/1 flag",
                SpecialStageStartedRendersAsFlag));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun special-stage input mask excludes Start",
                SpecialStageInputMaskExcludesStart));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun emits the aiz_completerun frame-0"
                + " game_paused_state line",
                EmitsAizGamePausedStateLine));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun emits the hcz_completerun paused"
                + " game_paused_state line from a WORD read",
                EmitsHczPausedGamePausedStateLine));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun emits game_paused_state between"
                + " oscillation_state and object_state",
                EmitsGamePausedStateInExactCascadeSlot));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun emits exactly one game_paused_state per"
                + " recorded frame",
                EmitsExactlyOneGamePausedStatePerFrame));
            tests.Add(new TestMain.TestCase(
                "S3K standard profiles emit no game_paused_state",
                StandardProfilesEmitNoGamePausedState));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun aux vfc and the standard profiles' aux vfc"
                + " both read Level_frame_counter",
                AuxVfcReadsLevelFrameCounterForEveryProfile));
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRun profile emits no checkpoint, no"
                + " aiz_fire_transition and no finalization event",
                CompleteRunProfileEmitsNoProfileGatedFamilies));
            tests.Add(new TestMain.TestCase(
                "S3KAuxEventEngine reads ADDR_FRAMECOUNT 0xFE04 for every"
                + " profile with no address seam",
                FrameCounterAddressIsUnifiedAcrossProfiles));
        }

        // ------------------------------------------------------------------
        // 42-column level / bonus rows
        // ------------------------------------------------------------------

        private static void LevelHeaderMatchesCompleteRunFixtureHeader()
        {
            // LITERAL header line of the gunzipped
            // src/test/resources/traces/s3k/aiz_completerun/physics.csv.
            // Byte-identical across all seven *_completerun fixtures, all
            // three standalone bonus_* fixtures, and every level/bonus
            // segment of runs/s3-knux-multibonus-ss — the complete-run
            // level schema IS the standard recorder's v7 schema.
            AssertEx.Equal(
                "frame,input,camera_x,camera_y,rings,gameplay_frame_counter,"
                + "vblank_counter,lag_counter,player_present,player_x,"
                + "player_y,player_x_speed,player_y_speed,player_g_speed,"
                + "player_angle,player_air,player_rolling,"
                + "player_ground_mode,player_x_sub,player_y_sub,"
                + "player_routine,player_status_byte,player_stand_on_obj,"
                + "player_animation_id,player_mapping_frame,"
                + "sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,"
                + "sidekick_y_speed,sidekick_g_speed,sidekick_angle,"
                + "sidekick_air,sidekick_rolling,sidekick_ground_mode,"
                + "sidekick_x_sub,sidekick_y_sub,sidekick_routine,"
                + "sidekick_status_byte,sidekick_stand_on_obj,"
                + "sidekick_animation_id,sidekick_mapping_frame",
                S3KTraceCsvWriter.Header);
        }

        private static void ReproducesAizCompleteRunRowZero()
        {
            var host = new RamBackedHost();
            host.SetWord(S3KRam.CameraX, 0x0000);
            host.SetWord(S3KRam.CameraY, 0x0390);
            // ADDR_FRAMECOUNT is LIVE: row 0 of every (A)/(C) fixture
            // carries gameplay_frame_counter 0001 straight out of
            // Level_frame_counter.
            host.SetWord(S3KRam.LevelFrameCounter, 0x0001);
            host.SetWord(S3KRam.VblankWord, 0x0300);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffXPos, 0x0040);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffYPos, 0x0420);
            host.Ram[S3KRam.PlayerBase + S3KRam.OffRoutine] = 0x02;
            // Tails present in slot 1 (aux frame 0 shows object_code
            // 0x0001365C at x 0x7F00), airborne via status 0x02.
            host.SetLong(
                S3KRam.SidekickBase + S3KRam.OffObjectCode, 0x0001365C);
            host.SetWord(S3KRam.SidekickBase + S3KRam.OffXPos, 0x7F00);
            host.Ram[S3KRam.SidekickBase + S3KRam.OffRoutine] = 0x02;
            host.Ram[S3KRam.SidekickBase + S3KRam.OffStatus] = 0x02;

            // LITERAL data row 0 of the gunzipped
            // src/test/resources/traces/s3k/aiz_completerun/physics.csv.
            AssertEx.Equal(
                "0000,0000,0000,0390,0000,0001,0300,0000,1,0040,0420,0000,"
                + "0000,0000,00,0,0,0,0000,0000,02,00,00,00,00,1,7F00,0000,"
                + "0000,0000,0000,00,1,0,0,0000,0000,02,02,00,00,00",
                S3KTraceCsvWriter.FormatRow(0, 0x0000, host));
        }

        private static void ReproducesBonusGumballRowZero()
        {
            // A bonus segment runs the SAME 42-column writer as a level
            // segment (spec §1.2) — only metadata.json and the manifest
            // differ. This row is also the Knuckles-alone case: the
            // sidekick block renders as the constant absent zero block.
            var host = new RamBackedHost();
            host.SetWord(S3KRam.CameraX, 0x0060);
            host.SetWord(S3KRam.CameraY, 0x00C0);
            host.SetWord(S3KRam.RingCount, 0x003B);
            host.SetWord(S3KRam.LevelFrameCounter, 0x0001);
            host.SetWord(S3KRam.VblankWord, 0x0400);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffXPos, 0x00FF);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffYPos, 0x0120);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffXSub, 0xF400);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffXVel, 0xFFF4);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffInertia, 0xFFF4);
            host.Ram[S3KRam.PlayerBase + S3KRam.OffRoutine] = 0x02;
            host.Ram[S3KRam.PlayerBase + S3KRam.OffStatus] = 0x03;
            host.Ram[S3KRam.PlayerBase + S3KRam.OffMappingFrame] = 0x07;

            // LITERAL data row 0 of the gunzipped
            // src/test/resources/traces/s3k/bonus_gumball/physics.csv.
            AssertEx.Equal(
                "0000,0004,0060,00C0,003B,0001,0400,0000,1,00FF,0120,FFF4,"
                + "0000,FFF4,00,1,0,0,F400,0000,02,03,00,00,07,0,0000,0000,"
                + "0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00",
                S3KTraceCsvWriter.FormatRow(0, 0x0004, host));
        }

        private static void LevelAndStandardRowsReadTheSameFrameCounter()
        {
            // The two recorders' rows USED to differ in column 5: the
            // complete-run recorder read Level_frame_counter (0xFE04) while
            // the standard recorder read the dead-zero
            // Debug_placement_mode (0xFE08). Lua v6.31-s3k moved the
            // standard recorder to 0xFE04 and its three canonical fixtures
            // were regenerated on it, so there is one row writer, one
            // address, and no recorder-identity parameter left. Staging
            // 0xFE08 with a DIFFERENT value is what proves the dead read is
            // gone rather than merely unexercised.
            var host = new RamBackedHost();
            host.SetWord(S3KRam.LevelFrameCounter, 0x1234);
            host.SetWord(0xFE08, 0x5678);

            AssertEx.Equal(0xFE04, S3KRam.LevelFrameCounter);
            AssertEx.Equal(
                "1234",
                S3KTraceCsvWriter.FormatRow(0, 0, host).Split(',')[5]);

            // And it tracks 0xFE04 rather than being a constant.
            host.SetWord(S3KRam.LevelFrameCounter, 0x00FF);
            AssertEx.Equal(
                "00FF",
                S3KTraceCsvWriter.FormatRow(0, 0, host).Split(',')[5]);
        }

        // ------------------------------------------------------------------
        // 20-column special-stage rows
        // ------------------------------------------------------------------

        private static void SpecialStageHeaderMatchesFixtureHeader()
        {
            // LITERAL header line of the gunzipped
            // src/test/resources/traces/s3k/special_stage/physics.csv.
            AssertEx.Equal(
                "frame,input,input_p2,lag,anim_frame,x_pos,y_pos,angle,"
                + "velocity,turning,jumping,fade_timer,spheres_left,"
                + "ring_count,rings_left,rate,rate_timer,clear_timer,"
                + "clear_routine,started",
                S3KSpecialStageCsvWriter.Header);
        }

        private static void ReproducesSpecialStageRowZero()
        {
            var host = new RamBackedHost();
            StageSpecialStage(
                host,
                animFrame: 0x400,
                xPos: 0x106,
                yPos: 0x400,
                angle: 0x01,
                velocity: 0x400,
                turning: 0x01,
                jumping: 0x01,
                fadeTimer: 0x06,
                spheresLeft: 0x400,
                ringCount: 0x106,
                ringsLeft: 0x106,
                rate: 0x400,
                rateTimer: 0x106,
                clearTimer: 0x106,
                clearRoutine: 0x04,
                started: 0x01);

            // LITERAL data row 0 of the gunzipped
            // src/test/resources/traces/s3k/special_stage/physics.csv.
            AssertEx.Equal(
                "0,0,0,0,400,106,400,1,400,1,1,6,400,106,106,400,106,106,4,1",
                S3KSpecialStageCsvWriter.FormatRow(0, 0, 0, false, host));
        }

        private static void ReproducesSpecialStageRowOneThousand()
        {
            // rate (0xE444) is column 16 and rate_timer (0xE43E) is column
            // 17 — TABLE order, not address order. Staging the two RAM
            // words with distinct values proves the writer does not sort
            // them by address.
            var host = new RamBackedHost();
            StageSpecialStage(
                host,
                animFrame: 0x5,
                xPos: 0xB00,
                yPos: 0x5A8,
                angle: 0x80,
                velocity: 0x1000,
                turning: 0xFC,
                jumping: 0x00,
                fadeTimer: 0x00,
                spheresLeft: 0x53,
                ringCount: 0x10,
                ringsLeft: 0x30,
                rate: 0x1000,
                rateTimer: 0x3BC,
                clearTimer: 0x0000,
                clearRoutine: 0x00,
                started: 0x01);

            // LITERAL data row 1000 of the special_stage fixture.
            AssertEx.Equal(
                "1000,8,0,0,5,b00,5a8,80,1000,fc,0,0,53,10,30,1000,3bc,0,0,1",
                S3KSpecialStageCsvWriter.FormatRow(1000, 0x8, 0, false, host));
        }

        private static void ReproducesSpecialStageNegativeVelocityRow()
        {
            var host = new RamBackedHost();
            StageSpecialStage(
                host,
                animFrame: 0xB,
                xPos: 0x10B0,
                yPos: 0x1710,
                angle: 0x40,
                // Special_stage_velocity is read SIGNED: -0x1000 wraps to
                // 0xF000 through the Lua's local uhex.
                velocity: 0xF000,
                turning: 0x00,
                jumping: 0x00,
                fadeTimer: 0x00,
                spheresLeft: 0x1F,
                ringCount: 0x30,
                ringsLeft: 0x10,
                rate: 0x1400,
                rateTimer: 0x1CD,
                clearTimer: 0x0000,
                clearRoutine: 0x00,
                started: 0x01);

            // LITERAL data row 3295 of the special_stage fixture.
            AssertEx.Equal(
                "3295,1,0,0,b,10b0,1710,40,f000,0,0,0,1f,30,10,1400,1cd,0,0,1",
                S3KSpecialStageCsvWriter.FormatRow(3295, 0x1, 0, false, host));
        }

        private static void ReproducesSpecialStageUnsignedYPosRow()
        {
            // y_pos is an UNSIGNED u16 read: 0xFC24 renders "fc24", not a
            // sign-wrapped or negative form.
            var host = new RamBackedHost();
            StageSpecialStage(
                host,
                animFrame: 0x7,
                xPos: 0x510,
                yPos: 0xFC24,
                angle: 0x00,
                velocity: 0x800,
                turning: 0x00,
                jumping: 0x00,
                fadeTimer: 0x00,
                spheresLeft: 0x00,
                ringCount: 0x40,
                ringsLeft: 0x00,
                rate: 0x1800,
                rateTimer: 0x39F,
                clearTimer: 0x0000,
                clearRoutine: 0x03,
                started: 0x01);

            // LITERAL final data row (4629) of the special_stage fixture.
            AssertEx.Equal(
                "4629,0,0,0,7,510,fc24,0,800,0,0,0,0,40,0,1800,39f,0,3,1",
                S3KSpecialStageCsvWriter.FormatRow(4629, 0, 0, false, host));
        }

        private static void ReproducesSpecialStageLagRow()
        {
            // lag is emu.islagged() for the just-completed frame, not a RAM
            // read and not a placeholder: 113 of the fixture's 4630 rows
            // carry 1.
            var host = new RamBackedHost();

            // LITERAL data row 22 of the special_stage fixture.
            AssertEx.Equal(
                "22,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
                S3KSpecialStageCsvWriter.FormatRow(22, 0, 0, true, host));
            AssertEx.Equal(
                "22,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
                S3KSpecialStageCsvWriter.FormatRow(22, 0, 0, false, host));
        }

        private static void SpecialStageStartedRendersAsFlag()
        {
            // Special_stage_started is emitted as (byte ~= 0) and 1 or 0,
            // never as the raw byte.
            var host = new RamBackedHost();
            host.Ram[S3KRam.SsStarted] = 0x00;
            AssertEx.Equal(
                "0",
                LastField(
                    S3KSpecialStageCsvWriter.FormatRow(0, 0, 0, false, host)));
            host.Ram[S3KRam.SsStarted] = 0x01;
            AssertEx.Equal(
                "1",
                LastField(
                    S3KSpecialStageCsvWriter.FormatRow(0, 0, 0, false, host)));
            host.Ram[S3KRam.SsStarted] = 0xFF;
            AssertEx.Equal(
                "1",
                LastField(
                    S3KSpecialStageCsvWriter.FormatRow(0, 0, 0, false, host)));
        }

        private static void SpecialStageInputMaskExcludesStart()
        {
            // ss_input_mask folds only the four directions plus A|B|C —
            // unlike the S2 special-stage mask (which ORs in 0x80), Start
            // never contributes here.
            AssertEx.Equal(
                0,
                S3KSpecialStageCsvWriter.InputMask(
                    new Bk2Frame { P1Start = true }));
            AssertEx.Equal(
                0x18,
                S3KSpecialStageCsvWriter.InputMask(
                    new Bk2Frame { P1Right = true, P1C = true }));
            AssertEx.Equal(
                0x10,
                S3KSpecialStageCsvWriter.InputMask(
                    new Bk2Frame { P1A = true, P1B = true, P1C = true }));
            AssertEx.Equal(
                0x0F,
                S3KSpecialStageCsvWriter.InputMask(new Bk2Frame
                {
                    P1Up = true,
                    P1Down = true,
                    P1Left = true,
                    P1Right = true
                }));
        }

        // ------------------------------------------------------------------
        // game_paused_state
        // ------------------------------------------------------------------

        private static void EmitsAizGamePausedStateLine()
        {
            var host = new RamBackedHost();
            StageMinimalLevelFrame(host);
            host.SetWord(S3KRam.LevelFrameCounter, 1);
            host.SetWord(S3KRam.GamePaused, 0);

            IList<string> lines =
                NewCompleteRunEngine().ProcessFrame(0, host);

            // LITERAL line 7 of the gunzipped
            // src/test/resources/traces/s3k/aiz_completerun/aux_state.jsonl.
            AssertEx.Equal(
                "{\"frame\":0,\"vfc\":1,\"event\":\"game_paused_state\","
                + "\"game_paused\":0}",
                Single(lines, "game_paused_state"));
        }

        private static void EmitsHczPausedGamePausedStateLine()
        {
            // Game_paused is a WORD (ds.w 1): the ROM writes
            // move.w #1,(Game_paused).w, so the paused state lives in the
            // LOW byte at 0xF63B. A byte read of 0xF63A would render 0 and
            // this fixture line could never be reproduced.
            var host = new RamBackedHost();
            StageMinimalLevelFrame(host);
            host.SetWord(S3KRam.LevelFrameCounter, 23442);
            host.SetWord(S3KRam.GamePaused, 1);
            AssertEx.Equal(0x00, (int)host.Ram[S3KRam.GamePaused]);

            IList<string> lines =
                NewCompleteRunEngine().ProcessFrame(23511, host);

            // LITERAL line from the gunzipped
            // src/test/resources/traces/s3k/hcz_completerun/aux_state.jsonl
            // (the accidental in-game pause window; the level frame counter
            // is frozen at 23442 across the paused rows).
            AssertEx.Equal(
                "{\"frame\":23511,\"vfc\":23442,\"event\":"
                + "\"game_paused_state\",\"game_paused\":1}",
                Single(lines, "game_paused_state"));
        }

        private static void EmitsGamePausedStateInExactCascadeSlot()
        {
            // Lua on_frame_end source order: write_oscillation_per_frame,
            // write_game_paused_per_frame, write_object_states_per_frame.
            var host = new RamBackedHost();
            StageMinimalLevelFrame(host);
            StageNearbyObject(host, 5, 0x00067472);

            IList<string> lines =
                NewCompleteRunEngine().ProcessFrame(1, host);

            int oscillation = IndexOf(lines, "oscillation_state");
            int paused = IndexOf(lines, "game_paused_state");
            int firstObject = IndexOf(lines, "object_state");
            AssertEx.Equal(true, oscillation >= 0);
            AssertEx.Equal(oscillation + 1, paused);
            AssertEx.Equal(paused + 1, firstObject);
        }

        private static void EmitsExactlyOneGamePausedStatePerFrame()
        {
            var host = new RamBackedHost();
            StageMinimalLevelFrame(host);
            S3KAuxEventEngine engine = NewCompleteRunEngine();

            for (int frame = 0; frame < 5; frame++)
            {
                host.SetWord(S3KRam.LevelFrameCounter, frame + 1);
                AssertEx.Equal(
                    1, Count(engine.ProcessFrame(frame, host),
                        "game_paused_state"));
            }
        }

        private static void StandardProfilesEmitNoGamePausedState()
        {
            // grep -c game_paused tools/bizhawk/s3k_trace_recorder.lua = 0:
            // the family does not exist in the standard recorder, so its
            // three profiles must never emit it.
            var host = new RamBackedHost();
            StageMinimalLevelFrame(host);
            host.SetWord(S3KRam.GamePaused, 1);

            AssertEx.Equal(0, Count(
                new S3KAuxEventEngine(S3KTraceProfile.GameplayUnlock)
                    .ProcessFrame(0, host),
                "game_paused_state"));
            AssertEx.Equal(0, Count(
                new S3KAuxEventEngine(S3KTraceProfile.AizEndToEnd)
                    .ProcessFrame(0, host),
                "game_paused_state"));
            AssertEx.Equal(0, Count(
                new S3KAuxEventEngine(S3KTraceProfile.LevelGatedResetAware)
                    .ProcessFrame(0, host),
                "game_paused_state"));
        }

        // ------------------------------------------------------------------
        // Recorder-identity seams
        // ------------------------------------------------------------------

        private static void AuxVfcReadsLevelFrameCounterForEveryProfile()
        {
            // Every aux "vfc" field is one ADDR_FRAMECOUNT read, and it is
            // 0xFE04 for the complete-run recorder AND for all three
            // standard profiles. 0xFE08 is staged with a different value so
            // a regression back to the dead read cannot render 0 and pass.
            var host = new RamBackedHost();
            StageMinimalLevelFrame(host);
            host.SetWord(S3KRam.LevelFrameCounter, 0x0141);
            host.SetWord(0xFE08, 0x0BB8);

            // oscillation_state emits the SAME read twice (vfc and
            // level_frame_counter), so it pins both at once.
            const string expected =
                "{\"vfc\":321,\"level_frame_counter\":321}";
            AssertEx.Equal(
                expected,
                VfcProbe(NewCompleteRunEngine().ProcessFrame(7, host)));
            foreach (S3KTraceProfile profile in new[]
            {
                S3KTraceProfile.GameplayUnlock,
                S3KTraceProfile.AizEndToEnd,
                S3KTraceProfile.LevelGatedResetAware
            })
            {
                AssertEx.Equal(
                    expected,
                    VfcProbe(new S3KAuxEventEngine(profile)
                        .ProcessFrame(7, host)));
            }

            // The pre-trace cpu_state_snapshot uses the same address, in
            // both recorders.
            AssertEx.Equal(
                true,
                NewCompleteRunEngine().EmitPreTraceSnapshots(host)[0].IndexOf(
                    "{\"frame\":-1,\"vfc\":321,",
                    StringComparison.Ordinal) == 0);
            AssertEx.Equal(
                true,
                new S3KAuxEventEngine(S3KTraceProfile.AizEndToEnd)
                    .EmitPreTraceSnapshots(host)[0].IndexOf(
                        "{\"frame\":-1,\"vfc\":321,",
                        StringComparison.Ordinal) == 0);
        }

        private static void CompleteRunProfileEmitsNoProfileGatedFamilies()
        {
            // TRACE_PROFILE is a hard-pinned "complete_run" local, so
            // is_level_gated_reset_aware_profile() and
            // is_aiz_end_to_end_profile() are both false: no checkpoint
            // vocabulary, no aiz_fire_transition, no finalisation event.
            // Confirmed against the fixtures: zero occurrences of any of
            // the three across all eleven (A)/(C) aux streams.
            var host = new RamBackedHost();
            StageMinimalLevelFrame(host);
            // Zone 3 / level-started, which is exactly what would trip the
            // level-gated gameplay_start checkpoint.
            host.Ram[S3KRam.Zone] = 0x03;
            host.Ram[S3KRam.LevelStartedFlag] = 0x01;

            S3KAuxEventEngine engine = NewCompleteRunEngine();
            IList<string> lines = engine.ProcessFrame(0, host);
            AssertEx.Equal(0, Count(lines, "checkpoint"));
            AssertEx.Equal(0, Count(lines, "aiz_fire_transition"));
            AssertEx.Equal(1, Count(lines, "zone_act_state"));
            AssertEx.Equal(0, Count(engine.EmitFinalization(1, host),
                "checkpoint"));
        }

        private static void FrameCounterAddressIsUnifiedAcrossProfiles()
        {
            // The seam this replaces was S3KAuxEventEngine's
            // FrameCounterAddressFor(profile) plus its explicit-address
            // constructor, which selected 0xFE08 for the standard profiles
            // and 0xFE04 for complete_run and let a test pin the legacy
            // 0xFE08-era complete-run captures. Both halves are gone: Lua
            // v6.31-s3k unified the address, and the legacy
            // runs/s3-knux-multibonus-ss captures were themselves
            // regenerated on 0xFE04 (commit 63eccd290), so nothing needs a
            // second address. The engine therefore exposes ONE constructor
            // and reads S3KRam.LevelFrameCounter unconditionally.
            AssertEx.Equal(0xFE04, S3KRam.LevelFrameCounter);
            AssertEx.Equal(
                1,
                typeof(S3KAuxEventEngine).GetConstructors().Length);
            AssertEx.Equal(
                1,
                typeof(S3KAuxEventEngine)
                    .GetConstructors()[0].GetParameters().Length);
            AssertEx.Equal(
                null,
                typeof(S3KAuxEventEngine).GetMethod(
                    "FrameCounterAddressFor"));

            // Every profile advances its vfc with 0xFE04 and ignores
            // 0xFE08 entirely.
            var host = new RamBackedHost();
            StageMinimalLevelFrame(host);
            host.SetWord(0xFE08, 0x0BB8);
            foreach (S3KTraceProfile profile in new[]
            {
                S3KTraceProfile.CompleteRun,
                S3KTraceProfile.GameplayUnlock,
                S3KTraceProfile.AizEndToEnd,
                S3KTraceProfile.LevelGatedResetAware
            })
            {
                host.SetWord(S3KRam.LevelFrameCounter, 0x0007);
                AssertEx.Equal(
                    "{\"vfc\":7,\"level_frame_counter\":7}",
                    VfcProbe(new S3KAuxEventEngine(profile)
                        .ProcessFrame(7, host)));
            }
        }

        // ------------------------------------------------------------------
        // Helpers
        // ------------------------------------------------------------------

        private static S3KAuxEventEngine NewCompleteRunEngine()
        {
            return new S3KAuxEventEngine(S3KTraceProfile.CompleteRun);
        }

        /// <summary>
        /// Minimal level-family RAM so the whole per-frame cascade runs:
        /// Game_mode 0x0C in AIZ act 1 with the player at a plausible spot.
        /// No object slots are populated, so object_state / object_near /
        /// slot_dump stay empty unless a test stages one.
        /// </summary>
        private static void StageMinimalLevelFrame(RamBackedHost host)
        {
            host.Ram[S3KRam.GameMode] = 0x0C;
            host.Ram[S3KRam.Zone] = 0x00;
            host.Ram[S3KRam.Act] = 0x00;
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffXPos, 0x0040);
            host.SetWord(S3KRam.PlayerBase + S3KRam.OffYPos, 0x0420);
            host.Ram[S3KRam.PlayerBase + S3KRam.OffRoutine] = 0x02;
        }

        private static void StageNearbyObject(
            RamBackedHost host, int slot, uint objectCode)
        {
            int addr = S3KRam.SlotAddress(slot);
            host.SetLong(addr + S3KRam.OffObjectCode, objectCode);
            host.SetWord(addr + S3KRam.OffXPos, 0x0040);
            host.SetWord(addr + S3KRam.OffYPos, 0x0420);
        }

        private static void StageSpecialStage(
            RamBackedHost host,
            int animFrame,
            int xPos,
            int yPos,
            int angle,
            int velocity,
            int turning,
            int jumping,
            int fadeTimer,
            int spheresLeft,
            int ringCount,
            int ringsLeft,
            int rate,
            int rateTimer,
            int clearTimer,
            int clearRoutine,
            int started)
        {
            host.SetWord(S3KRam.SsAnimFrame, animFrame);
            host.SetWord(S3KRam.SsXPos, xPos);
            host.SetWord(S3KRam.SsYPos, yPos);
            host.Ram[S3KRam.SsAngle] = (byte)angle;
            host.SetWord(S3KRam.SsVelocity, velocity);
            host.Ram[S3KRam.SsTurning] = (byte)turning;
            host.Ram[S3KRam.SsJumping] = (byte)jumping;
            host.Ram[S3KRam.SsFadeTimer] = (byte)fadeTimer;
            host.SetWord(S3KRam.SsSpheresLeft, spheresLeft);
            host.SetWord(S3KRam.SsRingCount, ringCount);
            host.SetWord(S3KRam.SsRingsLeft, ringsLeft);
            host.SetWord(S3KRam.SsRate, rate);
            host.SetWord(S3KRam.SsRateTimer, rateTimer);
            host.SetWord(S3KRam.SsClearTimer, clearTimer);
            host.Ram[S3KRam.SsClearRoutine] = (byte)clearRoutine;
            host.Ram[S3KRam.SsStarted] = (byte)started;
        }

        private static string LastField(string row)
        {
            string[] fields = row.Split(',');
            return fields[fields.Length - 1];
        }

        private static string Needle(string eventName)
        {
            return "\"event\":\"" + eventName + "\"";
        }

        private static int Count(IList<string> lines, string eventName)
        {
            string needle = Needle(eventName);
            int count = 0;
            foreach (string line in lines)
            {
                if (line.IndexOf(needle, StringComparison.Ordinal) >= 0)
                {
                    count++;
                }
            }
            return count;
        }

        private static int IndexOf(IList<string> lines, string eventName)
        {
            string needle = Needle(eventName);
            for (int i = 0; i < lines.Count; i++)
            {
                if (lines[i].IndexOf(needle, StringComparison.Ordinal) >= 0)
                {
                    return i;
                }
            }
            return -1;
        }

        private static string Single(IList<string> lines, string eventName)
        {
            int index = IndexOf(lines, eventName);
            if (index < 0)
            {
                throw new InvalidOperationException(
                    "No " + eventName + " line was emitted.");
            }
            AssertEx.Equal(1, Count(lines, eventName));
            return lines[index];
        }

        /// <summary>
        /// Extracts the vfc and level_frame_counter values from the frame's
        /// oscillation_state line as a compact comparable string.
        /// </summary>
        private static string VfcProbe(IList<string> lines)
        {
            string line = Single(lines, "oscillation_state");
            return "{" + Field(line, "\"vfc\":") + ","
                + Field(line, "\"level_frame_counter\":") + "}";
        }

        private static string Field(string line, string key)
        {
            int start = line.IndexOf(key, StringComparison.Ordinal);
            if (start < 0)
            {
                throw new InvalidOperationException(
                    "Missing " + key + " in: " + line);
            }
            int valueStart = start + key.Length;
            int end = valueStart;
            while (end < line.Length && line[end] != ','
                && line[end] != '}')
            {
                end++;
            }
            return key + line.Substring(valueStart, end - valueStart);
        }
    }
}
