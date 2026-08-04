using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class HardwareTimingEventEngineTests
    {
        private const string GoldenFingerprint =
            "sha256:11609213811e60294ea19488a1e3c6e87cd91f0af35480541091f5f7f478863b";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "HardwareTiming pending final module emits one LF event",
                PendingFinalModuleEmitsOneLfEvent));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming duplicate level frame without retirement writes no event",
                DuplicateLevelFrameWithoutRetirementWritesNoEvent));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming held level frame canonical head shift uses POST boundary",
                HeldLevelFrameCanonicalHeadShiftUsesPostBoundary));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming held level frame shift and append reconciles",
                HeldLevelFrameShiftAndAppendReconciles));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming held level frame rejects multi-head loss",
                HeldLevelFrameRejectsMultiHeadLoss));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming held level frame rejects malformed trailing shift",
                HeldLevelFrameRejectsMalformedTrailingShift));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming stale final-active state cannot certify a later shift",
                StaleFinalActiveStateCannotCertifyLaterShift));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming mode reset crossing emits no completion",
                ModeResetCrossingEmitsNoCompletion));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming mode transition fences a delayed queue clear",
                ModeTransitionFencesDelayedQueueClear));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming transition-visible work stays fenced until clear",
                TransitionVisibleWorkStaysFencedUntilClear));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming post-clear level queues preserve FIFO ordinals",
                PostClearLevelQueuesPreserveFifoOrdinals));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming empty-to-active transition manufactures no completion",
                EmptyToActiveTransitionManufacturesNoCompletion));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming advanced level frame uses post-objects boundary",
                AdvancedLevelFrameUsesPostObjectsBoundary));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming title-card scan keeps post-objects boundary",
                TitleCardScanKeepsPostObjectsBoundary));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming title-card predicate rejects wrong slot and flag",
                TitleCardPredicateRejectsWrongSlotAndFlag));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming armed title-card Nem tail stays post-objects",
                ArmedTitleCardNemTailStaysPostObjects));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming armed title-card raw wait survives code deletion",
                ArmedTitleCardRawWaitSurvivesCodeDeletion));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming gameplay Nem retirement is state-proven POST",
                GameplayNemRetirementIsStateProvenPost));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming exited title-card retirement is state-proven POST",
                ExitedTitleCardRetirementIsStateProvenPost));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming repeated zero emits a zero-byte stream",
                RepeatedZeroEmitsZeroByteStream));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming later lifecycle increments the ordinal",
                LaterLifecycleIncrementsOrdinal));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming unexported lifecycle preserves later ordinal",
                UnexportedLifecyclePreservesLaterOrdinal));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming FIFO head shift emits without a sampled zero",
                FifoHeadShiftEmitsWithoutSampledZero));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine direct busy count retires despite stale slot zero",
                DirectBusyCountRetiresDespiteStaleSlotZero));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine direct shift and append preserve FIFO ordinals",
                DirectShiftAndAppendPreserveFifoOrdinals));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine direct 1-to-1 retire and append reconciles",
                DirectOneToOneRetireAndAppendReconciles));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine direct 1-to-2 retire and append reconciles",
                DirectOneToTwoRetireAndAppendReconciles));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine direct 2-to-3 retire and append reconciles",
                DirectTwoToThreeRetireAndAppendReconciles));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine longer unchanged count uses maximum overlap",
                DirectLongerUnchangedCountUsesMaximumOverlap));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine unexplained direct slot zero mutation fails",
                UnexplainedDirectSlotZeroMutationFails));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine busy-to-idle identical replacement gets a new ordinal",
                BusyToIdleIdenticalReplacementGetsNewOrdinal));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine still-busy slot zero mutation fails",
                StillBusyDirectSlotZeroMutationFails));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine stable identical head emits no completion",
                StableIdenticalDirectHeadEmitsNoCompletion));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine identical direct jobs do not fabricate shifts",
                IdenticalDirectJobsDoNotFabricateShifts));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine module POST sorts before direct PRE",
                ModulePostSortsBeforeDirectPre));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine callback observes short-lived child",
                CallbackObservesShortLivedChild));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine callback bootstraps occupied FIFO",
                CallbackBootstrapsOccupiedFifo));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine callback stages shift and append",
                CallbackStagesShiftAndAppend));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine callbacks stage repeated same-frame shifts",
                CallbackStagesRepeatedSameFrameShifts));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine callback distinguishes identical replacement",
                CallbackDistinguishesIdenticalReplacement));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine callback rejects multi-head loss",
                CallbackRejectsMultiHeadLoss));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine callback gap and reset preserve authority",
                CallbackGapAndResetPreserveAuthority));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine callback module POST sorts before direct PRE",
                CallbackModulePostSortsBeforeDirectPre));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine scanner matches language-neutral vectors",
                DirectScannerMatchesLanguageNeutralVectors));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine reset clears both ledgers and ordinal bases",
                ResetClearsBothLedgersAndOrdinalBases));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming measurement counts exact child service after submission frame",
                MeasurementCountsExactChildServiceAfterSubmissionFrame));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming measurement excludes unclassified exact child",
                MeasurementExcludesUnclassifiedExactChild));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming fingerprint matches the Java golden vector",
                FingerprintMatchesJavaGoldenVector));
            tests.Add(new TestMain.TestCase(
                "HardwareTimingEventEngine AIZ module child identity",
                AizModuleChildIdentityMatchesRomQueue));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming descriptor refill precedes boundary command payload",
                DescriptorRefillPrecedesBoundaryCommandPayload));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming module alignment preserves the following header",
                ModuleAlignmentPreservesFollowingHeader));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming S3K ROM spans match the production decoder",
                S3kRomSpansMatchProductionDecoder,
                game: "s3k"));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming frozen Lua retains held-retirement attribution",
                FrozenLuaRetainsHeldRetirementAttribution));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming Lua and native title-card scans stay post-objects",
                LuaAndNativeTitleCardScansStayPostObjects));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming frozen Lua differs only at held retirement attribution",
                FrozenLuaDiffersOnlyAtHeldRetirementAttribution));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming lag boundary has a new recorder version",
                LagBoundaryHasNewRecorderVersion));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming four-file publication is atomic and no-replace",
                FourFilePublicationIsAtomicAndNoReplace));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming standard Lua bounds a never-armed capture",
                StandardLuaBoundsNeverArmedCapture));
        }

        private static void PendingFinalModuleEmitsOneLfEvent()
        {
            const int source = 0x100;
            const int destination = 0xA400;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageActive(host, source, destination, 0x81);
            engine.ObserveFrameEnd(12, host, writer);
            SetLevelFrame(host, 0x1235);
            StageEmpty(host);
            engine.ObserveFrameEnd(13, host, writer);
            engine.ObserveFrameEnd(14, host, writer);

            string fingerprint =
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_MODULE_QUEUE",
                    source,
                    7,
                    destination,
                    1,
                    "kosinski_moduled",
                    1);
            AssertEx.Equal(
                "{\"event\":\"hardware_work_completed\",\"raw_frame\":13,"
                + "\"boundary\":\"post_objects\",\"kind\":\"kos_module_queue\","
                + "\"ordinal\":0,\"submission_fingerprint\":\""
                + fingerprint + "\"}\n",
                writer.ToString());
        }

        private static void DuplicateLevelFrameWithoutRetirementWritesNoEvent()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0);
            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(20, host, writer);
            // No parent retirement: the final-active head and physical FIFO
            // remain byte-identical across the held-counter interval.
            engine.ObserveFrameEnd(21, host, writer);

            AssertEx.Equal(string.Empty, writer.ToString());
        }

        private static void HeldLevelFrameCanonicalHeadShiftUsesPostBoundary()
        {
            const int first = 0x100;
            const int second = 0x120;
            const int third = 0x140;
            byte[] rom = RomWithSingleModules(first, second, third);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            SetLevelFrame(host, 0x18CB);
            StageActive(host, first, 0xA900, 0x81);
            StageQueued(host, 1, second, 0xA540);
            StageQueued(host, 2, third, 0xABE0);
            engine.ObserveFrameEnd(6350, host, writer);

            StageActive(host, second, 0xA540, 0x01);
            StageQueued(host, 1, third, 0xABE0);
            engine.ObserveFrameEnd(6351, host, writer);

            string fingerprint =
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_MODULE_QUEUE",
                    first,
                    7,
                    0xA900,
                    1,
                    "kosinski_moduled",
                    1);
            AssertEx.Equal(
                "{\"event\":\"hardware_work_completed\","
                + "\"raw_frame\":6351,\"boundary\":\"post_objects\","
                + "\"kind\":\"kos_module_queue\",\"ordinal\":0,"
                + "\"submission_fingerprint\":\"" + fingerprint + "\"}\n",
                writer.ToString());

        }

        private static void HeldLevelFrameShiftAndAppendReconciles()
        {
            const int first = 0x100;
            const int second = 0x120;
            const int appended = 0x140;
            byte[] rom = RomWithSingleModules(first, second, appended);
            var host = NewHost();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            SetLevelFrame(host, 7);
            StageActive(host, first, 0x4000, 0x81);
            StageQueued(host, 1, second, 0x6000);
            engine.ObserveFrameEnd(0, host, new StringWriter());

            StageActive(host, second, 0x6000, 0x01);
            StageQueued(host, 1, appended, 0x7000);
            var writer = new StringWriter();
            engine.ObserveFrameEnd(1, host, writer);
            AssertEx.Equal(
                true,
                writer.ToString().Contains("\"ordinal\":0"));

            StageActive(host, second, 0x6000, 0x81);
            StageQueued(host, 1, appended, 0x7000);
            engine.ObserveFrameEnd(2, host, writer);
            StageActive(host, appended, 0x7000, 0x01);
            engine.ObserveFrameEnd(3, host, writer);
            AssertEx.Equal(
                true,
                writer.ToString().Contains("\"ordinal\":1"));
        }

        private static void StaleFinalActiveStateCannotCertifyLaterShift()
        {
            const int first = 0x100;
            const int second = 0x120;
            byte[] rom = RomWithSingleModules(first, second);
            var host = NewHost();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            SetLevelFrame(host, 9);
            StageActive(host, first, 0x4000, 0x81);
            StageQueued(host, 1, second, 0x6000);
            engine.ObserveFrameEnd(0, host, new StringWriter());
            host.Ram[S3KRam.KosModulesLeft] = 0x82;
            engine.ObserveFrameEnd(1, host, new StringWriter());
            StageActive(host, second, 0x6000, 0x01);

            AssertEx.Throws<InvalidDataException>(
                () => engine.ObserveFrameEnd(2, host, new StringWriter()),
                "without an eligible final-module retirement");
        }

        private static void HeldLevelFrameRejectsMultiHeadLoss()
        {
            const int first = 0x100;
            const int second = 0x120;
            const int third = 0x140;
            byte[] rom = RomWithSingleModules(first, second, third);
            var host = NewHost();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            SetLevelFrame(host, 8);
            StageActive(host, first, 0x4000, 0x81);
            StageQueued(host, 1, second, 0x5000);
            StageQueued(host, 2, third, 0x6000);
            engine.ObserveFrameEnd(0, host, new StringWriter());

            StageActive(host, third, 0x6000, 0x01);
            AssertEx.Throws<InvalidDataException>(
                () => engine.ObserveFrameEnd(1, host, new StringWriter()),
                "cardinality");
        }

        private static void HeldLevelFrameRejectsMalformedTrailingShift()
        {
            const int first = 0x100;
            const int second = 0x120;
            const int third = 0x140;
            const int unrelated = 0x160;
            byte[] rom = RomWithSingleModules(
                first, second, third, unrelated);
            var host = NewHost();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            SetLevelFrame(host, 8);
            StageActive(host, first, 0x4000, 0x81);
            StageQueued(host, 1, second, 0x5000);
            StageQueued(host, 2, third, 0x6000);
            engine.ObserveFrameEnd(0, host, new StringWriter());

            StageActive(host, second, 0x5000, 0x01);
            StageQueued(host, 1, unrelated, 0x7000);
            AssertEx.Throws<InvalidDataException>(
                () => engine.ObserveFrameEnd(1, host, new StringWriter()),
                "canonical shifted entry 1");
        }

        private static void ModeResetCrossingEmitsNoCompletion()
        {
            const int first = 0x100;
            const int second = 0x120;
            byte[] rom = RomWithSingleModules(first, second);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            StageActive(host, first, 0x4000, 0x81);
            engine.ObserveFrameEnd(0, host, writer);
            host.Ram[S3KRam.GameMode] = S3KRam.GameModeTitle;
            StageEmpty(host);
            engine.ObserveFrameEnd(1, host, writer);
            AssertEx.Equal(string.Empty, writer.ToString());

            StageActive(host, second, 0x6000, 0x81);
            engine.ObserveFrameEnd(2, host, writer);
            SetLevelFrame(host, 1);
            StageEmpty(host);
            engine.ObserveFrameEnd(3, host, writer);
            AssertEx.Equal(
                true,
                writer.ToString().Contains("\"ordinal\":1"));
        }

        private static void ModeTransitionFencesDelayedQueueClear()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(30, host, writer);

            // A mode transition is the reset fence even when the bulk clear
            // is not visible until the next frame-end sample.
            host.Ram[S3KRam.GameMode] = 0x04;
            engine.ObserveFrameEnd(31, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(32, host, writer);

            AssertEx.Equal(string.Empty, writer.ToString());

            // The pre-transition lifecycle consumed ordinal zero even though
            // reset fencing suppressed its completion event.
            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            StageEmpty(host);
            engine.ObserveFrameEnd(33, host, writer);
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(34, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(35, host, writer);
            AssertEx.Equal(
                true, writer.ToString().Contains("\"ordinal\":1"));
        }

        private static void TransitionVisibleWorkStaysFencedUntilClear()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            StageEmpty(host);
            engine.ObserveFrameEnd(50, host, writer);

            // Work first visible on the transition sample is ledgered so its
            // ordinal is consumed, but it is not proof of new-mode ownership:
            // the following physical disappearance may be the delayed reset.
            host.Ram[S3KRam.GameMode] = S3KRam.GameModeTitle;
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(51, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(52, host, writer);
            AssertEx.Equal(string.Empty, writer.ToString());

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            engine.ObserveFrameEnd(53, host, writer);
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(54, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(55, host, writer);
            AssertEx.Equal(
                true, writer.ToString().Contains("\"ordinal\":1"));
        }

        private static void PostClearLevelQueuesPreserveFifoOrdinals()
        {
            AssertPostClearLevelQueueOrdinals(
                S3KRam.GameModeLevel, 0x8C);
            AssertPostClearLevelQueueOrdinals(
                0x8C, S3KRam.GameModeLevel);
        }

        private static void AssertPostClearLevelQueueOrdinals(
            byte priorMode, byte transitionMode)
        {
            const int first = 0x100;
            const int second = 0x200;
            byte[] rom = RomWithSingleModules(first, second);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = priorMode;
            StageEmpty(host);
            engine.ObserveFrameEnd(60, host, writer);

            // Level clears the old FIFO before its first loading sample; it
            // can then publish one or two genuine post-clear parents. The
            // loading-bit exit likewise first exposes sprite-owned work that
            // was queued after the same clear. Neither case may re-ledger a
            // surviving suffix after the first canonical retirement.
            host.Ram[S3KRam.GameMode] = transitionMode;
            StageActive(host, first, 0x4000, 0x81);
            StageQueued(host, 1, second, 0x6000);
            engine.ObserveFrameEnd(61, host, writer);
            StageActive(host, second, 0x6000, 0x01);
            engine.ObserveFrameEnd(62, host, writer);
            StageActive(host, second, 0x6000, 0x81);
            engine.ObserveFrameEnd(63, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(64, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
        }

        private static void EmptyToActiveTransitionManufacturesNoCompletion()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            host.Ram[S3KRam.GameMode] = S3KRam.GameModeLevel;
            StageEmpty(host);
            engine.ObserveFrameEnd(0, host, writer);
            StageActive(host, source, 0x4000, 0x81);
            engine.ObserveFrameEnd(1, host, writer);

            AssertEx.Equal(string.Empty, writer.ToString());
        }

        private static void AdvancedLevelFrameUsesPostObjectsBoundary()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x3210);
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(20, host, writer);
            SetLevelFrame(host, 0x3211);
            StageEmpty(host);
            engine.ObserveFrameEnd(21, host, writer);

            AssertEx.Equal(
                true,
                writer.ToString().Contains(
                    "\"raw_frame\":21,\"boundary\":\"post_objects\""));
            AssertEx.Equal(
                false,
                writer.ToString().Contains("\"boundary\":\"vint_service\""));
        }

        private static void TitleCardScanKeepsPostObjectsBoundary()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0);
            SetTitleCardParent(host);
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(20, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(21, host, writer);

            AssertEx.Equal(
                true,
                writer.ToString().Contains(
                    "\"raw_frame\":21,\"boundary\":\"post_objects\""));
            AssertEx.Equal(
                false,
                writer.ToString().Contains("\"boundary\":\"vint_service\""));
        }

        private static void TitleCardPredicateRejectsWrongSlotAndFlag()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);

            AssertEx.Equal(
                true,
                CompleteDuplicateSubmission(
                    rom, source, true, false).Contains(
                        "\"boundary\":\"post_objects\""));
            AssertEx.Equal(
                true,
                CompleteDuplicateSubmission(
                    rom, source, false, true).Contains(
                        "\"boundary\":\"post_objects\""));
        }

        private static void ArmedTitleCardNemTailStaysPostObjects()
        {
            AssertEx.Equal(
                true,
                CompleteTitleCardLifecycle(
                    nemQueueAtCompletion: true).Contains(
                        "\"boundary\":\"post_objects\""));
        }

        private static void ArmedTitleCardRawWaitSurvivesCodeDeletion()
        {
            AssertEx.Equal(
                true,
                CompleteTitleCardRawWaitTail().Contains(
                    "\"boundary\":\"post_objects\""));
        }

        private static void GameplayNemRetirementIsStateProvenPost()
        {
            AssertEx.Equal(
                true,
                CompleteGameplayNemSubmission().Contains(
                    "\"boundary\":\"post_objects\""));
        }

        private static string CompleteGameplayNemSubmission()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x3210);
            host.SetU32(S3KRam.NemDecompQueue, 0x00123456);
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(0, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(1, host, writer);
            return writer.ToString();
        }

        private static void ExitedTitleCardRetirementIsStateProvenPost()
        {
            string output = CompleteExitedTitleCardLifecycle();
            AssertEx.Equal(
                true,
                output.Contains(
                    "\"raw_frame\":1,\"boundary\":\"post_objects\""));
            AssertEx.Equal(
                true,
                output.Contains(
                    "\"raw_frame\":3,\"boundary\":\"post_objects\""));
        }

        private static string CompleteTitleCardLifecycle(
            bool nemQueueAtCompletion)
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0);
            SetTitleCardParent(host);
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(0, host, writer);

            int parent = S3KRam.SlotAddress(8);
            host.SetU16(parent + 0x48, 0);
            host.SetU32(
                S3KRam.NemDecompQueue,
                nemQueueAtCompletion ? 0x00123456u : 0u);
            StageEmpty(host);
            engine.ObserveFrameEnd(1, host, writer);
            return writer.ToString();
        }

        private static string CompleteTitleCardRawWaitTail()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0);
            SetTitleCardParent(host);
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(0, host, writer);
            host.SetU32(S3KRam.SlotAddress(8), 0);
            StageEmpty(host);
            engine.ObserveFrameEnd(1, host, writer);
            return writer.ToString();
        }

        private static string CompleteExitedTitleCardLifecycle()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0);
            SetTitleCardParent(host);
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(0, host, writer);
            int parent = S3KRam.SlotAddress(8);
            host.SetU32(parent, 0);
            host.SetU16(parent + 0x48, 0);
            StageEmpty(host);
            engine.ObserveFrameEnd(1, host, writer);

            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(2, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(3, host, writer);
            return writer.ToString();
        }

        private static string CompleteDuplicateSubmission(
            byte[] rom,
            int source,
            bool wrongSlot,
            bool clearedWait)
        {
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);
            SetLevelFrame(host, 0x3210);
            int slot = wrongSlot ? 7 : 8;
            int parent = S3KRam.SlotAddress(slot);
            host.SetU32(parent, 0x0002D690);
            host.SetU16(parent + 0x48, (ushort)(clearedWait ? 0 : 1));
            StageActive(host, source, 0xA400, 0x81);
            engine.ObserveFrameEnd(20, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(21, host, writer);
            return writer.ToString();
        }

        private static void RepeatedZeroEmitsZeroByteStream()
        {
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(new byte[0x400]);

            StageEmpty(host);
            engine.ObserveFrameEnd(0, host, writer);
            engine.ObserveFrameEnd(1, host, writer);
            engine.ObserveFrameEnd(2, host, writer);

            AssertEx.Equal("", writer.ToString());
        }

        private static void LaterLifecycleIncrementsOrdinal()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageActive(host, source, 0x4000, 0x81);
            engine.ObserveFrameEnd(0, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(1, host, writer);
            StageActive(host, source, 0x4000, 0x81);
            engine.ObserveFrameEnd(2, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(3, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
        }

        private static void UnexportedLifecyclePreservesLaterOrdinal()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageActive(host, source, 0x4000, 0x81);
            engine.ObserveFrameEnd(0, host, null);
            StageEmpty(host);
            engine.ObserveFrameEnd(1, host, null);
            StageActive(host, source, 0x6000, 0x81);
            engine.ObserveFrameEnd(2, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(3, host, writer);

            string output = writer.ToString();
            AssertEx.Equal(
                false, output.Contains("\"ordinal\":0"));
            AssertEx.Equal(
                true, output.Contains("\"ordinal\":1"));
        }

        private static void FifoHeadShiftEmitsWithoutSampledZero()
        {
            const int firstSource = 0x100;
            const int secondSource = 0x200;
            byte[] rom = RomWithSingleModules(firstSource, secondSource);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageActive(host, firstSource, 0x4000, 0x81);
            StageQueued(host, 1, secondSource, 0x6000);
            engine.ObserveFrameEnd(20, host, writer);

            // Process_Kos_Module_Queue retires the first head, shifts the
            // second entry, and initializes it before returning. No frame-end
            // sample ever sees Kos_modules_left == 0.
            StageActive(host, secondSource, 0x6000, 0x01);
            engine.ObserveFrameEnd(21, host, writer);
            StageActive(host, secondSource, 0x6000, 0x81);
            engine.ObserveFrameEnd(22, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(23, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"raw_frame\":21"));
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"raw_frame\":23"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
        }

        private static void DirectBusyCountRetiresDespiteStaleSlotZero()
        {
            const int source = 0x100;
            byte[] rom = RomWithStandardStream(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageDirect(host, 0, source, unchecked((int)0xFFFF9268));
            host.SetU16(S3KRam.KosDecompQueueCount, 0x8001);
            engine.ObserveFrameEnd(10, host, writer);

            // Process_Kos_Queue writes advanced saved pointers into slot
            // zero on final retirement. Count, not a zero sentinel, owns
            // physical occupancy.
            host.SetU32(S3KRam.KosDecompQueue, 0x00ABCDEF);
            host.SetU32(S3KRam.KosDecompQueue + 4, 0xFFFF9999);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(11, host, writer);

            AssertEx.Equal(
                true,
                writer.ToString().Contains(
                    "\"raw_frame\":11,\"boundary\":\"pre_main_loop\","
                    + "\"kind\":\"kos_decompression_queue\","
                    + "\"ordinal\":0"));
        }

        private static void DirectShiftAndAppendPreserveFifoOrdinals()
        {
            const int first = 0x100;
            const int second = 0x120;
            const int third = 0x140;
            byte[] rom = RomWithStandardStreams(first, second, third);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageDirect(host, 0, first, unchecked((int)0xFFFF9268));
            StageDirect(host, 1, second, unchecked((int)0xFFFF0A00));
            host.SetU16(S3KRam.KosDecompQueueCount, 2);
            engine.ObserveFrameEnd(20, host, writer);

            // Head retires and a tail appends between samples: count stays 2.
            StageDirect(host, 0, second, unchecked((int)0xFFFF0A00));
            StageDirect(host, 1, third, unchecked((int)0xFFFFD400));
            host.SetU16(S3KRam.KosDecompQueueCount, 2);
            engine.ObserveFrameEnd(21, host, writer);

            StageDirect(host, 0, third, unchecked((int)0xFFFFD400));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveFrameEnd(22, host, writer);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(23, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(3, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
            AssertEx.Equal(true, lines[2].Contains("\"ordinal\":2"));
        }

        private static void IdenticalDirectJobsDoNotFabricateShifts()
        {
            const int source = 0x100;
            const int destination = unchecked((int)0xFFFF9268);
            byte[] rom = RomWithStandardStream(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageDirect(host, 0, source, destination);
            StageDirect(host, 1, source, destination);
            host.SetU16(S3KRam.KosDecompQueueCount, 2);
            engine.ObserveFrameEnd(0, host, writer);
            engine.ObserveFrameEnd(1, host, writer);
            AssertEx.Equal("", writer.ToString());

            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveFrameEnd(2, host, writer);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(3, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
        }

        private static void DirectOneToOneRetireAndAppendReconciles()
        {
            AssertGrowingRetireAndAppend(
                new[] {0x100},
                new[] {0x120});
        }

        private static void DirectOneToTwoRetireAndAppendReconciles()
        {
            AssertGrowingRetireAndAppend(
                new[] {0x100},
                new[] {0x120, 0x140});
        }

        private static void DirectTwoToThreeRetireAndAppendReconciles()
        {
            AssertGrowingRetireAndAppend(
                new[] {0x100, 0x120},
                new[] {0x120, 0x140, 0x160});
        }

        private static void DirectLongerUnchangedCountUsesMaximumOverlap()
        {
            AssertGrowingRetireAndAppend(
                new[] {0x100, 0x120, 0x140},
                new[] {0x120, 0x140, 0x160});
        }

        private static void UnexplainedDirectSlotZeroMutationFails()
        {
            byte[] rom = RomWithStandardStreams(0x100, 0x120);
            var host = NewHost();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageDirect(
                host, 0, 0x100, unchecked((int)0xFFFF9000));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveFrameEnd(0, host, TextWriter.Null);

            // A duplicate level frame admits no main-loop direct service,
            // so a changed occupied head is mutation rather than a
            // retire-plus-append transition.
            StageDirect(
                host, 0, 0x120, unchecked((int)0xFFFF9200));
            AssertEx.Throws<InvalidDataException>(
                () => engine.ObserveFrameEnd(1, host, TextWriter.Null),
                "changed");
        }

        private static void BusyToIdleIdenticalReplacementGetsNewOrdinal()
        {
            const int source = 0x100;
            const int destination = unchecked((int)0xFFFF9268);
            byte[] rom = RomWithStandardStream(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageDirect(host, 0, source, destination);
            host.SetU16(S3KRam.KosDecompQueueCount, 0x8001);
            engine.ObserveFrameEnd(0, host, writer);

            // The old busy A completed after an identical A was appended
            // and shifted into slot zero. Identity alone is ambiguous; the
            // sampled busy-to-idle edge proves the old ordinal retired.
            SetLevelFrame(host, 0x1235);
            StageDirect(host, 0, source, destination);
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveFrameEnd(1, host, writer);

            SetLevelFrame(host, 0x1236);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(2, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
        }

        private static void StillBusyDirectSlotZeroMutationFails()
        {
            byte[] rom = RomWithStandardStreams(0x100, 0x120);
            var host = NewHost();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageDirect(
                host, 0, 0x100, unchecked((int)0xFFFF9000));
            host.SetU16(S3KRam.KosDecompQueueCount, 0x8001);
            engine.ObserveFrameEnd(0, host, TextWriter.Null);

            SetLevelFrame(host, 0x1235);
            StageDirect(
                host, 0, 0x120, unchecked((int)0xFFFF9200));
            host.SetU16(S3KRam.KosDecompQueueCount, 0x8001);
            AssertEx.Throws<InvalidDataException>(
                () => engine.ObserveFrameEnd(1, host, TextWriter.Null),
                "busy");
        }

        private static void StableIdenticalDirectHeadEmitsNoCompletion()
        {
            const int source = 0x100;
            const int destination = unchecked((int)0xFFFF9268);
            byte[] rom = RomWithStandardStream(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageDirect(host, 0, source, destination);
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveFrameEnd(0, host, writer);

            SetLevelFrame(host, 0x1235);
            engine.ObserveFrameEnd(1, host, writer);
            AssertEx.Equal("", writer.ToString());

            SetLevelFrame(host, 0x1236);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(2, host, writer);
            AssertEx.Equal(
                true, writer.ToString().Contains("\"ordinal\":0"));
        }

        private static void AssertGrowingRetireAndAppend(
            int[] priorSources,
            int[] sampledSources)
        {
            var allSources = new List<int>();
            allSources.AddRange(priorSources);
            foreach (int source in sampledSources)
            {
                if (!allSources.Contains(source))
                {
                    allSources.Add(source);
                }
            }
            byte[] rom = RomWithStandardStreams(allSources.ToArray());
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageDirectSources(host, priorSources);
            host.SetU16(
                S3KRam.KosDecompQueueCount,
                checked((ushort)priorSources.Length));
            engine.ObserveFrameEnd(10, host, writer);

            SetLevelFrame(host, 0x1235);
            StageDirectSources(host, sampledSources);
            host.SetU16(
                S3KRam.KosDecompQueueCount,
                checked((ushort)sampledSources.Length));
            engine.ObserveFrameEnd(11, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(1, lines.Length);
            AssertEx.Equal(
                true,
                lines[0].Contains(
                    "\"raw_frame\":11,\"boundary\":\"pre_main_loop\","
                    + "\"kind\":\"kos_decompression_queue\","
                    + "\"ordinal\":0"));
        }

        private static void StageDirectSources(
            FakeS1Host host,
            int[] sources)
        {
            for (int index = 0; index < sources.Length; index++)
            {
                StageDirect(
                    host,
                    index,
                    sources[index],
                    unchecked((int)(0xFFFF9000u
                        + (uint)(sources[index] * 4))));
            }
        }

        private static void ModulePostSortsBeforeDirectPre()
        {
            const int directSource = 0x100;
            const int moduleSource = 0x200;
            byte[] rom = RomWithStandardStreams(directSource);
            byte[] moduleRom = RomWithSingleModule(moduleSource);
            Array.Resize(ref rom, moduleRom.Length);
            Array.Copy(moduleRom, moduleSource, rom, moduleSource, 7);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageActive(host, moduleSource, 0xA400, 0x81);
            StageDirect(host, 0, directSource, unchecked((int)0xFFFF9268));
            host.SetU16(S3KRam.KosDecompQueueCount, 0x8001);
            engine.ObserveFrameEnd(30, host, writer);

            SetLevelFrame(host, 0x1235);
            StageEmpty(host);
            // Keep stale direct slot bytes while the count becomes zero.
            StageDirect(host, 0, directSource + 3, unchecked((int)0xFFFF926B));
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(31, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(
                true,
                lines[0].Contains(
                    "\"boundary\":\"post_objects\","
                    + "\"kind\":\"kos_module_queue\""));
            AssertEx.Equal(
                true,
                lines[1].Contains(
                    "\"boundary\":\"pre_main_loop\","
                    + "\"kind\":\"kos_decompression_queue\""));
        }

        private static void CallbackObservesShortLivedChild()
        {
            const int source = 0x100;
            byte[] rom = RomWithStandardStream(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(20, host, writer);

            StageDirect(
                host, 0, source, unchecked((int)0xFFFFD000));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveDirectSubmissions(host);
            AssertEx.Equal("", writer.ToString());

            SetLevelFrame(host, 0x1235);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(21, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(1, lines.Length);
            AssertEx.Equal(
                true,
                lines[0].Contains(
                    "\"raw_frame\":21,\"boundary\":\"pre_main_loop\","
                    + "\"kind\":\"kos_decompression_queue\","
                    + "\"ordinal\":0"));
        }

        private static void CallbackBootstrapsOccupiedFifo()
        {
            byte[] rom = RomWithStandardStreams(0x100, 0x120);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(24, host, null);

            // The first lifecycle observation is the exact post-Queue_Kos
            // callback after an empty frame-end sample in an unexported boot
            // gap. A was already pending; B is the proven new module child.
            StageDirectSources(host, new[] {0x100, 0x120});
            host.SetU16(S3KRam.KosDecompQueueCount, 2);
            engine.ObserveDirectSubmissions(host);
            engine.ObserveFrameEnd(25, host, null);

            StageDirectSources(host, new[] {0x120});
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveFrameEnd(26, host, writer);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(27, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
        }

        private static void CallbackStagesShiftAndAppend()
        {
            const int first = 0x100;
            const int second = 0x120;
            const int third = 0x140;
            byte[] rom = RomWithStandardStreams(first, second, third);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageDirectSources(host, new[] {first, second});
            host.SetU16(S3KRam.KosDecompQueueCount, 2);
            engine.ObserveFrameEnd(30, host, writer);

            // One PRE retires A; the exact module callback then observes
            // Queue_Kos having appended C behind the retained B.
            StageDirectSources(host, new[] {second, third});
            host.SetU16(S3KRam.KosDecompQueueCount, 2);
            engine.ObserveDirectSubmissions(host);
            AssertEx.Equal("", writer.ToString());

            SetLevelFrame(host, 0x1235);
            engine.ObserveFrameEnd(31, host, writer);
            StageDirectSources(host, new[] {third});
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            SetLevelFrame(host, 0x1236);
            engine.ObserveFrameEnd(32, host, writer);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            SetLevelFrame(host, 0x1237);
            engine.ObserveFrameEnd(33, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(3, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
            AssertEx.Equal(true, lines[2].Contains("\"ordinal\":2"));
        }

        private static void CallbackDistinguishesIdenticalReplacement()
        {
            const int source = 0x100;
            const int destination = unchecked((int)0xFFFFD000);
            byte[] rom = RomWithStandardStream(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageDirect(host, 0, source, destination);
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveFrameEnd(40, host, writer);

            // The exact callback proves Queue_Kos appended a new A after
            // the old A retired, despite identical physical bytes/count.
            engine.ObserveDirectSubmissions(host);
            SetLevelFrame(host, 0x1235);
            engine.ObserveFrameEnd(41, host, writer);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            SetLevelFrame(host, 0x1236);
            engine.ObserveFrameEnd(42, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
        }

        private static void CallbackStagesRepeatedSameFrameShifts()
        {
            byte[] rom = RomWithStandardStreams(0x100, 0x120, 0x140);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageDirectSources(host, new[] {0x100});
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveFrameEnd(40, host, writer);

            StageDirectSources(host, new[] {0x120});
            engine.ObserveDirectSubmissions(41, host);
            StageDirectSources(host, new[] {0x140});
            engine.ObserveDirectSubmissions(41, host);
            SetLevelFrame(host, 0x1235);
            engine.ObserveFrameEnd(41, host, writer);

            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            SetLevelFrame(host, 0x1236);
            engine.ObserveFrameEnd(42, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(3, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
            AssertEx.Equal(true, lines[2].Contains("\"ordinal\":2"));
            string firstFingerprint = ExtractSubmissionFingerprint(lines[0]);
            string secondFingerprint = ExtractSubmissionFingerprint(lines[1]);
            string thirdFingerprint = ExtractSubmissionFingerprint(lines[2]);
            AssertEx.Equal(false, firstFingerprint == secondFingerprint);
            AssertEx.Equal(false, secondFingerprint == thirdFingerprint);
            AssertEx.Equal(false, firstFingerprint == thirdFingerprint);
        }

        private static string ExtractSubmissionFingerprint(string eventJson)
        {
            const string prefix = "\"submission_fingerprint\":\"";
            int start = eventJson.IndexOf(prefix, StringComparison.Ordinal);
            AssertEx.Equal(true, start >= 0);
            start += prefix.Length;
            int end = eventJson.IndexOf('"', start);
            AssertEx.Equal(true, end > start);
            return eventJson.Substring(start, end - start);
        }

        private static void CallbackRejectsMultiHeadLoss()
        {
            byte[] rom = RomWithStandardStreams(0x100, 0x120, 0x140);
            var host = NewHost();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageDirectSources(host, new[] {0x100, 0x120});
            host.SetU16(S3KRam.KosDecompQueueCount, 2);
            engine.ObserveFrameEnd(50, host, TextWriter.Null);

            StageDirectSources(host, new[] {0x140});
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            AssertEx.Throws<InvalidDataException>(
                () => engine.ObserveDirectSubmissions(host),
                "one");
        }

        private static void CallbackGapAndResetPreserveAuthority()
        {
            byte[] rom = RomWithStandardStreams(0x100, 0x120);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageDirect(
                host, 0, 0x100, unchecked((int)0xFFFFD000));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveDirectSubmissions(host);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(60, host, null);

            StageDirect(
                host, 0, 0x120, unchecked((int)0xFFFFD000));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveDirectSubmissions(host);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(61, host, writer);
            AssertEx.Equal(
                true, writer.ToString().Contains("\"ordinal\":1"));
            AssertEx.Equal(
                false, writer.ToString().Contains("\"ordinal\":0"));

            engine.Reset();
            writer.GetStringBuilder().Length = 0;
            StageDirect(
                host, 0, 0x100, unchecked((int)0xFFFFD000));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveDirectSubmissions(host);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(62, host, writer);
            AssertEx.Equal(
                true, writer.ToString().Contains("\"ordinal\":0"));
        }

        private static void MeasurementCountsExactChildServiceAfterSubmissionFrame()
        {
            const int source = 0x100;
            const int moduleSource = 0x200;
            byte[] rom = RomWithStandardStream(source);
            byte[] moduleRom = RomWithSingleModule(moduleSource);
            Array.Resize(ref rom, moduleRom.Length);
            Array.Copy(moduleRom, moduleSource, rom, moduleSource, 7);
            var host = NewHost();
            var measurements = new StringWriter();
            var engine = new HardwareTimingEventEngine(
                rom, measurements, "fixture.bk2");

            StageActive(host, moduleSource, 0xA400, 1);
            StageDirect(
                host, 0, source, unchecked((int)0xFFFFD000));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            SetLevelFrame(host, 10);
            engine.ObserveDirectSubmissions(0, host);
            engine.ObserveFrameEnd(0, host, null);

            host.SetU16(S3KRam.KosDecompQueueCount, 0x8001);
            SetLevelFrame(host, 11);
            engine.ObserveFrameEnd(1, host, null);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            SetLevelFrame(host, 12);
            engine.ObserveFrameEnd(2, host, null);

            string output = measurements.ToString();
            AssertEx.Equal(true, output.Contains(
                "\"observation_precision\":\"exact_callback\""));
            AssertEx.Equal(true, output.Contains(
                "\"parent_fingerprint\":\"sha256:"));
            AssertEx.Equal(true, output.Contains("\"classified\":true"));
            AssertEx.Equal(true, output.Contains(
                "\"service_opportunities\":2"));
            AssertEx.Equal(true, output.Contains("\"literal_commands\":3"));
        }

        private static void MeasurementExcludesUnclassifiedExactChild()
        {
            const int source = 0x100;
            const int moduleSource = 0x200;
            byte[] rom = RomWithStandardStream(source);
            byte[] moduleRom = RomWithSingleModule(moduleSource);
            Array.Resize(ref rom, moduleRom.Length);
            Array.Copy(moduleRom, moduleSource, rom, moduleSource, 7);
            var host = NewHost();
            var measurements = new StringWriter();
            var engine = new HardwareTimingEventEngine(
                rom, measurements, "fixture.bk2");

            StageActive(host, moduleSource, 0xA400, 1);
            StageDirect(
                host, 0, source, unchecked((int)0xFFFFD000));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            SetLevelFrame(host, 20);
            engine.ObserveDirectSubmissions(0, host);
            engine.ObserveFrameEnd(0, host, null);
            engine.ObserveFrameEnd(1, host, null);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(2, host, null);

            AssertEx.Equal(true, measurements.ToString().Contains(
                "\"classified\":false"));
        }

        private static void CallbackModulePostSortsBeforeDirectPre()
        {
            const int directSource = 0x100;
            const int moduleSource = 0x200;
            byte[] rom = RomWithStandardStream(directSource);
            byte[] moduleRom = RomWithSingleModule(moduleSource);
            Array.Resize(ref rom, moduleRom.Length);
            Array.Copy(moduleRom, moduleSource, rom, moduleSource, 7);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            SetLevelFrame(host, 0x1234);
            StageActive(host, moduleSource, 0xA400, 0x81);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(70, host, writer);

            StageDirect(
                host, 0, directSource, unchecked((int)0xFFFFD000));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveDirectSubmissions(host);
            SetLevelFrame(host, 0x1235);
            StageEmpty(host);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(71, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(
                true,
                lines[0].Contains(
                    "\"boundary\":\"post_objects\","
                    + "\"kind\":\"kos_module_queue\""));
            AssertEx.Equal(
                true,
                lines[1].Contains(
                    "\"boundary\":\"pre_main_loop\","
                    + "\"kind\":\"kos_decompression_queue\""));
        }

        private static void DirectScannerMatchesLanguageNeutralVectors()
        {
            var coveredFeatures = new HashSet<string>();
            foreach (StandardKosVector vector in LoadStandardKosVectors())
            {
                foreach (string feature in vector.Features.Split(','))
                {
                    coveredFeatures.Add(feature);
                }

                var rom = new byte[
                    checked(vector.SourceAddress + vector.Bytes.Length)];
                Array.Copy(
                    vector.Bytes,
                    0,
                    rom,
                    vector.SourceAddress,
                    vector.Bytes.Length);
                var host = NewHost();
                var writer = new StringWriter();
                var engine = new HardwareTimingEventEngine(rom);
                StageDirect(
                    host,
                    0,
                    vector.SourceAddress,
                    vector.DestinationAddress);
                host.SetU16(S3KRam.KosDecompQueueCount, 1);

                if (vector.Outcome == "invalid_backreference")
                {
                    AssertEx.Throws<InvalidDataException>(
                        () => engine.ObserveFrameEnd(
                            0, host, TextWriter.Null),
                        "backreference");
                    continue;
                }
                if (vector.Outcome == "rom_bound")
                {
                    AssertEx.Throws<InvalidDataException>(
                        () => engine.ObserveFrameEnd(
                            0, host, TextWriter.Null),
                        "outside the supplied ROM");
                    continue;
                }

                AssertEx.Equal("ok", vector.Outcome);
                AssertEx.Equal(
                    vector.Fingerprint,
                    HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                        "KOS_DECOMPRESSION_QUEUE",
                        vector.SourceAddress,
                        vector.CompressedLength,
                        vector.DestinationAddress,
                        vector.DecodedLength,
                        "kosinski",
                        1));
                engine.ObserveFrameEnd(0, host, writer);
                host.SetU16(S3KRam.KosDecompQueueCount, 0);
                engine.ObserveFrameEnd(1, host, writer);
                AssertEx.Equal(
                    true,
                    writer.ToString().Contains(
                        "\"submission_fingerprint\":\""
                        + vector.Fingerprint + "\""));
            }

            string[] requiredFeatures =
            {
                "descriptor_refill",
                "literal",
                "short_match",
                "long_match",
                "extended_match",
                "no_output",
                "invalid_backreference",
                "terminator",
                "rom_bound"
            };
            AssertEx.Equal(
                requiredFeatures.Length, coveredFeatures.Count);
            foreach (string feature in requiredFeatures)
            {
                AssertEx.Equal(true, coveredFeatures.Contains(feature));
            }
        }

        private static void ResetClearsBothLedgersAndOrdinalBases()
        {
            const int source = 0x100;
            byte[] rom = RomWithStandardStream(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            CompleteDirect(engine, host, writer, source);
            engine.Reset();
            CompleteDirect(engine, host, writer, source);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":0"));
        }

        private static IEnumerable<StandardKosVector>
            LoadStandardKosVectors()
        {
            string path = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "kosinski",
                "standard-scanner-vectors.tsv");
            foreach (string line in File.ReadAllLines(path))
            {
                if (line.Length == 0 || line.StartsWith("#"))
                {
                    continue;
                }
                string[] fields = line.Split('\t');
                if (fields.Length != 9)
                {
                    throw new InvalidDataException(
                        "Standard Kosinski vector must have nine fields: "
                        + line);
                }
                bool success = fields[1] == "ok";
                yield return new StandardKosVector(
                    fields[0],
                    fields[1],
                    fields[2],
                    checked((int)Convert.ToUInt32(fields[3], 16)),
                    unchecked((int)Convert.ToUInt32(fields[4], 16)),
                    ParseHexBytes(fields[5]),
                    success
                        ? int.Parse(
                            fields[6],
                            System.Globalization.CultureInfo.InvariantCulture)
                        : -1,
                    success
                        ? int.Parse(
                            fields[7],
                            System.Globalization.CultureInfo.InvariantCulture)
                        : -1,
                    fields[8]);
            }
        }

        private static byte[] ParseHexBytes(string value)
        {
            if ((value.Length & 1) != 0)
            {
                throw new InvalidDataException(
                    "Kosinski vector hex must have an even length.");
            }
            var bytes = new byte[value.Length / 2];
            for (int index = 0; index < bytes.Length; index++)
            {
                bytes[index] = Convert.ToByte(
                    value.Substring(index * 2, 2), 16);
            }
            return bytes;
        }

        private sealed class StandardKosVector
        {
            public StandardKosVector(
                string name,
                string outcome,
                string features,
                int sourceAddress,
                int destinationAddress,
                byte[] bytes,
                int compressedLength,
                int decodedLength,
                string fingerprint)
            {
                Name = name;
                Outcome = outcome;
                Features = features;
                SourceAddress = sourceAddress;
                DestinationAddress = destinationAddress;
                Bytes = bytes;
                CompressedLength = compressedLength;
                DecodedLength = decodedLength;
                Fingerprint = fingerprint;
            }

            public string Name { get; private set; }
            public string Outcome { get; private set; }
            public string Features { get; private set; }
            public int SourceAddress { get; private set; }
            public int DestinationAddress { get; private set; }
            public byte[] Bytes { get; private set; }
            public int CompressedLength { get; private set; }
            public int DecodedLength { get; private set; }
            public string Fingerprint { get; private set; }
        }

        private static void CompleteDirect(
            HardwareTimingEventEngine engine,
            FakeS1Host host,
            TextWriter writer,
            int source)
        {
            StageDirect(host, 0, source, unchecked((int)0xFFFF9268));
            host.SetU16(S3KRam.KosDecompQueueCount, 1);
            engine.ObserveFrameEnd(0, host, writer);
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            engine.ObserveFrameEnd(1, host, writer);
        }

        private static void FingerprintMatchesJavaGoldenVector()
        {
            AssertEx.Equal(
                GoldenFingerprint,
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_MODULE_QUEUE",
                    0x12345678,
                    0x01020304,
                    0x0000ABCD,
                    0x11223344,
                    "KosM",
                    7));
        }

        private static void AizModuleChildIdentityMatchesRomQueue()
        {
            AssertEx.Equal(
                "sha256:c381a8f75b41d3e2d1e52fb90ae8a5c269b1daeb88dd198bd8fb3d07d3703a7b",
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_DECOMPRESSION_QUEUE",
                    0x382626,
                    1894,
                    unchecked((int)0xFFFFD000),
                    4096,
                    "kosinski",
                    1));
        }

        private static void DescriptorRefillPrecedesBoundaryCommandPayload()
        {
            const int source = 0x100;
            const int destination = 0xA520;
            byte[] rom = RomWithDescriptorBoundaryModule(source, false);
            string output = CompleteSingleSubmission(
                rom, source, destination);

            // sonic3k.asm:2572-2585 refills d5 immediately when dbf consumes
            // descriptor bit 16, before the command reads its literal/match
            // payload. ResumableKosinskiDecoder therefore consumes 25 bytes
            // for this archive: two header bytes plus a 23-byte module.
            string expected =
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_MODULE_QUEUE",
                    source,
                    25,
                    destination,
                    16,
                    "kosinski_moduled",
                    1);
            AssertEx.Equal(
                true,
                output.Contains(
                    "\"submission_fingerprint\":\"" + expected + "\""));
        }

        private static void ModuleAlignmentPreservesFollowingHeader()
        {
            const int source = 0x100;
            const int destination = 0x2000;
            byte[] rom = RomWithDescriptorBoundaryModule(source, true);
            string output = CompleteSingleSubmission(
                rom, source, destination);

            // The first 23-byte module aligns to offset 32 relative to the
            // archive payload. The following five-byte module then ends at
            // archive byte 39; delayed descriptor refill instead mistakes
            // command data for a descriptor and crosses this module boundary.
            string expected =
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_MODULE_QUEUE",
                    source,
                    39,
                    destination,
                    0x1001,
                    "kosinski_moduled",
                    2);
            AssertEx.Equal(
                true,
                output.Contains(
                    "\"submission_fingerprint\":\"" + expected + "\""));
        }

        private static void S3kRomSpansMatchProductionDecoder()
        {
            string romPath =
                Environment.GetEnvironmentVariable("S3K_ROM_PATH");
            if (string.IsNullOrEmpty(romPath))
            {
                throw new TestMain.SkipTestException(
                    "S3K_ROM_PATH is not set.");
            }
            if (!File.Exists(romPath))
            {
                throw new InvalidOperationException(
                    "Supplied S3K_ROM_PATH does not exist: " + romPath + ".");
            }
            byte[] rom = File.ReadAllBytes(romPath);
            using (SHA1 sha1 = SHA1.Create())
            {
                string actual = BitConverter.ToString(
                        sha1.ComputeHash(rom))
                    .Replace("-", "");
                AssertEx.Equal(
                    "CFBF98C36C776677290A872547AC47C53D2761D6",
                    actual);
            }

            // Literal spans are independently established by the production
            // ResumableKosinskiDecoder. The matching disassembly owners are
            // ArtKosM_AIZIntroPlane / ArtKosM_AIZIntroEmeralds and
            // ArtKosM_TitleCardRedAct.
            AssertSubmissionFingerprint(
                rom,
                0x382624,
                0xA520,
                "sha256:4423f6be47e039925c8575c68ed5eb22e9cba75f2aadd05f1d288d6c9579e723");
            AssertSubmissionFingerprint(
                rom,
                0x387CA6,
                0xB620,
                "sha256:34b575dc3ee07365ac9f621cf3d1f8afb74e90e851c6ed50d6b9e1d1c92f62c5");
            AssertSubmissionFingerprint(
                rom,
                0x0D6F28,
                0xA000,
                "sha256:10eb568a70724c579f022914f56227c2c7fa421aafa8578aebaa874f0cffb0ca");
        }

        private static void AssertSubmissionFingerprint(
            byte[] rom,
            int source,
            int destination,
            string expected)
        {
            string output = CompleteSingleSubmission(
                rom, source, destination);
            AssertEx.Equal(
                true,
                output.Contains(
                    "\"submission_fingerprint\":\"" + expected + "\""));
        }

        private static void FrozenLuaRetainsHeldRetirementAttribution()
        {
            const int source = 0x100;
            const int destination = 0xA520;
            byte[] rom = RomWithDescriptorBoundaryModule(source, false);
            string expected = CompleteSingleSubmission(
                rom, source, destination);
            string frozenLua = RunLuaBehaviorVector(
                rom, source, destination, TimingVector.None);
            AssertEx.Equal(
                true,
                expected.Contains("\"boundary\":\"post_objects\""));
            AssertEx.Equal(
                true,
                frozenLua.Contains("\"boundary\":\"vint_service\""));
            AssertEx.Equal(
                expected,
                frozenLua.Replace(
                    "\"boundary\":\"vint_service\"",
                    "\"boundary\":\"post_objects\""));
        }

        private static void LuaAndNativeTitleCardScansStayPostObjects()
        {
            const int source = 0x100;
            const int destination = 0xA520;
            byte[] rom = RomWithSingleModule(source);
            string expected = CompleteSingleSubmission(
                rom, source, destination, true);
            AssertEx.Equal(
                true,
                expected.Contains("\"boundary\":\"post_objects\""));
            AssertEx.Equal(
                expected,
                RunLuaBehaviorVector(
                    rom, source, destination, TimingVector.TitleParentWait));
        }

        private static void FrozenLuaDiffersOnlyAtHeldRetirementAttribution()
        {
            const int source = 0x100;
            const int destination = 0xA400;
            byte[] rom = RomWithSingleModule(source);

            AssertEx.Equal(
                CompleteTitleCardLifecycle(nemQueueAtCompletion: true),
                RunLuaBehaviorVector(
                    rom, source, destination, TimingVector.TitleNemTail));
            AssertEx.Equal(
                CompleteTitleCardRawWaitTail(),
                RunLuaBehaviorVector(
                    rom, source, destination, TimingVector.TitleRawWaitTail));
            string gameplayNative = CompleteGameplayNemSubmission();
            string gameplayLua = RunLuaBehaviorVector(
                rom, source, destination, TimingVector.GameplayNem);
            AssertEx.Equal(
                gameplayNative,
                gameplayLua.Replace(
                    "\"boundary\":\"vint_service\"",
                    "\"boundary\":\"post_objects\""));
            string exitedNative = CompleteExitedTitleCardLifecycle();
            string exitedLua = RunLuaBehaviorVector(
                rom, source, destination, TimingVector.TitleExited);
            AssertEx.Equal(
                exitedNative,
                exitedLua.Replace(
                    "\"raw_frame\":3,\"boundary\":\"vint_service\"",
                    "\"raw_frame\":3,\"boundary\":\"post_objects\""));
        }

        private enum TimingVector
        {
            None,
            TitleParentWait,
            TitleNemTail,
            TitleRawWaitTail,
            GameplayNem,
            TitleExited
        }

        private static string RunLuaBehaviorVector(
            byte[] rom,
            int source,
            int destination,
            TimingVector vector)
        {
            const string lua = "/usr/bin/lua";
            if (!File.Exists(lua))
            {
                throw new TestMain.SkipTestException(
                    "/usr/bin/lua is not installed.");
            }

            string root = TestScratch.CreateRootPath(
                "hardware-timing-lua-vector");
            try
            {
                Directory.CreateDirectory(root);
                string romPath = Path.Combine(root, "vector.gen");
                string scriptPath = Path.Combine(root, "vector.lua");
                File.WriteAllBytes(romPath, rom);
                File.WriteAllText(
                    scriptPath,
                    LuaBehaviorVectorScript(vector),
                    new UTF8Encoding(false));

                var start = new ProcessStartInfo
                {
                    FileName = lua,
                    Arguments = Quote(scriptPath)
                        + " " + Quote(Path.Combine(
                            EndToEndTests.RepositoryRoot,
                            "tools", "bizhawk", "lib",
                            "oggf_hardware_timing.lua"))
                        + " " + Quote(romPath)
                        + " " + source
                        + " " + destination,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true
                };
                using (Process process = Process.Start(start))
                {
                    string actual = process.StandardOutput.ReadToEnd();
                    string error = process.StandardError.ReadToEnd();
                    process.WaitForExit();
                    if (process.ExitCode != 0)
                    {
                        throw new InvalidOperationException(
                            "Lua hardware timing vector failed: " + error);
                    }
                    return actual;
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

        private static void LagBoundaryHasNewRecorderVersion()
        {
            AssertEx.Equal(
                "3.0",
                TraceContract.RecorderVersion);
            AssertEx.Equal(
                "3.0",
                TraceContract.RecorderVersion);

            string standard = File.ReadAllText(Path.Combine(
                EndToEndTests.RepositoryRoot,
                "tools", "bizhawk", "s3k_trace_recorder.lua"));
            string complete = File.ReadAllText(Path.Combine(
                EndToEndTests.RepositoryRoot,
                "tools", "bizhawk", "s3k_complete_run_recorder.lua"));
            AssertEx.Equal(
                true,
                standard.Contains(
                    "\"recorder\": \"lua-bizhawk-diagnostic\""));
            AssertEx.Equal(
                true,
                complete.Contains(
                    "\"recorder_version\": \"3.0\""));
        }

        private static string LuaBehaviorVectorScript(
            TimingVector vector)
        {
            string initialState = "";
            string completionState = "";
            string afterCompletionState = "";
            if (vector == TimingVector.TitleParentWait
                || vector == TimingVector.TitleNemTail
                || vector == TimingVector.TitleRawWaitTail
                || vector == TimingVector.TitleExited)
            {
                initialState =
                    "local title=0xB000+8*0x4A;"
                    + " set32(title,0x2D690);"
                    + " set16(title+0x48,1)\n";
            }
            if (vector == TimingVector.TitleNemTail)
            {
                completionState =
                    "set16(title+0x48,0); set32(0xF680,0x123456)\n";
            }
            else if (vector == TimingVector.TitleRawWaitTail)
            {
                completionState = "set32(title,0)\n";
            }
            else if (vector == TimingVector.TitleExited)
            {
                completionState =
                    "set32(title,0); set16(title+0x48,0);"
                    + " set32(0xF680,0)\n";
                afterCompletionState =
                    "ram[0xFF60]=0x81; set32(0xFF64,source+2);"
                    + " set16(0xFF68,destination)\n"
                    + "timing.observe(tracker,2,output)\n"
                    + "for address=0xFF64,0xFF7B do ram[address]=0 end\n"
                    + "ram[0xFF60]=0\n"
                    + "timing.observe(tracker,3,output)\n";
            }
            else if (vector == TimingVector.GameplayNem)
            {
                initialState =
                    "set16(0xFE04,0x3210); set32(0xF680,0x123456)\n";
            }

            return
                "local module_path, rom_path = arg[1], arg[2]\n"
                + "local source, destination = tonumber(arg[3]), tonumber(arg[4])\n"
                + "local input = assert(io.open(rom_path, 'rb'))\n"
                + "local rom = input:read('*a')\n"
                + "input:close()\n"
                + "local ram = {}\n"
                + "memory = {read_u8=function(address, domain)\n"
                + "  assert(domain == 'MD CART')\n"
                + "  return assert(rom:byte(address + 1))\n"
                + "end}\n"
                + "mainmemory = {}\n"
                + "function mainmemory.read_u8(address) return ram[address] or 0 end\n"
                + "function mainmemory.read_u16_be(address)\n"
                + "  return (mainmemory.read_u8(address) << 8)\n"
                + "    | mainmemory.read_u8(address + 1)\n"
                + "end\n"
                + "function mainmemory.read_u32_be(address)\n"
                + "  return (mainmemory.read_u16_be(address) << 16)\n"
                + "    | mainmemory.read_u16_be(address + 2)\n"
                + "end\n"
                + "local function set16(address, value)\n"
                + "  ram[address] = (value >> 8) & 0xFF\n"
                + "  ram[address + 1] = value & 0xFF\n"
                + "end\n"
                + "local function set32(address, value)\n"
                + "  set16(address, (value >> 16) & 0xFFFF)\n"
                + "  set16(address + 2, value & 0xFFFF)\n"
                + "end\n"
                + "local timing = assert(loadfile(module_path))()\n"
                + "local tracker = timing.new_tracker()\n"
                + "local output = assert(io.tmpfile())\n"
                + initialState
                + "ram[0xFF60] = 0x81\n"
                + "set32(0xFF64, source + 2)\n"
                + "set16(0xFF68, destination)\n"
                + "timing.observe(tracker, 0, output)\n"
                + completionState
                + "for address = 0xFF64, 0xFF7B do ram[address] = 0 end\n"
                + "ram[0xFF60] = 0\n"
                + "timing.observe(tracker, 1, output)\n"
                + afterCompletionState
                + "output:seek('set', 0)\n"
                + "io.write(output:read('*a'))\n"
                + "output:close()\n";
        }

        private static string Quote(string value)
        {
            return "\"" + value.Replace("\\", "\\\\")
                .Replace("\"", "\\\"") + "\"";
        }

        private static string CompleteSingleSubmission(
            byte[] rom,
            int source,
            int destination)
        {
            return CompleteSingleSubmission(
                rom, source, destination, false);
        }

        private static string CompleteSingleSubmission(
            byte[] rom,
            int source,
            int destination,
            bool titleCardParent)
        {
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);
            if (titleCardParent)
            {
                SetTitleCardParent(host);
            }
            StageActive(host, source, destination, 0x81);
            engine.ObserveFrameEnd(0, host, writer);
            StageEmpty(host);
            engine.ObserveFrameEnd(1, host, writer);
            return writer.ToString();
        }

        private static void FourFilePublicationIsAtomicAndNoReplace()
        {
            string root = TestScratch.CreateRootPath("hardware-timing-publish");
            string output = Path.Combine(root, "out");
            string[] names =
            {
                "physics.csv",
                "aux_state.jsonl",
                "hardware_timing.jsonl",
                "metadata.json"
            };
            try
            {
                var publisher = new NoReplacePublisher();
                NoReplacePublisher.StagedPublicationSet staged =
                    publisher.StageAll(
                        output,
                        names,
                        writers =>
                        {
                            writers[0].Write("physics\n");
                            writers[1].Write("");
                            writers[2].Write("timing\n");
                            writers[3].Write("{}\n");
                        });

                foreach (string name in names)
                {
                    AssertEx.Equal(
                        false, File.Exists(Path.Combine(output, name)));
                }
                staged.Publish();
                foreach (string name in names)
                {
                    AssertEx.Equal(
                        true, File.Exists(Path.Combine(output, name)));
                }

                using (NoReplacePublisher.StagedPublicationSet second =
                    publisher.StageAll(
                        output,
                        names,
                        writers => { }))
                {
                    AssertEx.Throws<IOException>(
                        () => second.Publish(),
                        "already exists");
                }
                AssertEx.Equal(
                    "timing\n",
                    File.ReadAllText(Path.Combine(
                        output, "hardware_timing.jsonl"),
                        Encoding.UTF8));
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static void StandardLuaBoundsNeverArmedCapture()
        {
            string source = File.ReadAllText(Path.Combine(
                EndToEndTests.RepositoryRoot,
                "tools", "bizhawk", "s3k_trace_recorder.lua"));
            int frameEnd = source.IndexOf(
                "function on_frame_end()", StringComparison.Ordinal);
            int captureMovieGuard = source.IndexOf(
                "Reached movie input end before/after arm",
                frameEnd,
                StringComparison.Ordinal);
            int startedGuard = source.IndexOf(
                "if HEADLESS and started then",
                frameEnd,
                StringComparison.Ordinal);
            int frameCap = source.IndexOf(
                "if not finished and emu.framecount() >= FRAME_CAP then",
                StringComparison.Ordinal);
            int noArmCleanup = source.IndexOf(
                "No trace segment armed; closing zero-byte timing stream.",
                StringComparison.Ordinal);
            int openEmpty = source.IndexOf(
                "open_empty_hardware_timing_file()",
                source.IndexOf(
                    "os.execute(\"mkdir", StringComparison.Ordinal),
                StringComparison.Ordinal);

            AssertEx.Equal(true, frameEnd >= 0);
            AssertEx.Equal(
                true,
                captureMovieGuard > frameEnd
                    && captureMovieGuard < startedGuard);
            AssertEx.Equal(true, frameCap > startedGuard);
            AssertEx.Equal(true, noArmCleanup > frameCap);
            AssertEx.Equal(true, openEmpty >= 0);
        }

        private static FakeS1Host NewHost()
        {
            return new FakeS1Host((h, frame) => { });
        }

        private static byte[] RomWithSingleModule(int source)
        {
            return RomWithSingleModules(source);
        }

        private static byte[] RomWithSingleModules(params int[] sources)
        {
            int length = 0x400;
            foreach (int source in sources)
            {
                length = Math.Max(length, source + 7);
            }
            var rom = new byte[length];
            foreach (int source in sources)
            {
                // One-byte KosM archive. Descriptor bits 0,1 select a full
                // match and the third zero byte terminates the module.
                rom[source] = 0x00;
                rom[source + 1] = 0x01;
                rom[source + 2] = 0x02;
                rom[source + 3] = 0x00;
                rom[source + 4] = 0x00;
                rom[source + 5] = 0x00;
                rom[source + 6] = 0x00;
            }
            return rom;
        }

        private static byte[] RomWithStandardStream(int source)
        {
            return RomWithStandardStreams(source);
        }

        private static byte[] RomWithStandardStreams(params int[] sources)
        {
            int length = 0x400;
            foreach (int source in sources)
            {
                length = Math.Max(length, source + 8);
            }
            var rom = new byte[length];
            foreach (int source in sources)
            {
                rom[source] = 0x17;
                rom[source + 1] = 0x00;
                rom[source + 2] = (byte)'A';
                rom[source + 3] = (byte)'B';
                rom[source + 4] = (byte)'C';
                rom[source + 5] = 0x00;
                rom[source + 6] = 0x00;
                rom[source + 7] = 0x00;
            }
            return rom;
        }

        private static byte[] RomWithDescriptorBoundaryModule(
            int source,
            bool appendSecondModule)
        {
            int compressedLength = appendSecondModule ? 39 : 25;
            var rom = new byte[source + compressedLength];
            int destinationLength = appendSecondModule ? 0x1001 : 16;
            rom[source] = (byte)(destinationLength >> 8);
            rom[source + 1] = (byte)destinationLength;

            int position = source + 2;
            rom[position++] = 0xFF;
            rom[position++] = 0xFF;
            for (int literal = 0; literal < 15; literal++)
            {
                rom[position++] = (byte)(literal + 1);
            }
            // The next descriptor is fetched after bit 16 but before the
            // sixteenth literal. Its first two bits select a full match.
            rom[position++] = 0x02;
            rom[position++] = 0x00;
            rom[position++] = 0x10;
            rom[position++] = 0x00;
            rom[position++] = 0x00;
            rom[position++] = 0x00;

            if (appendSecondModule)
            {
                int relative = position - (source + 2);
                position += (16 - (relative & 15)) & 15;
                rom[position++] = 0x02;
                rom[position++] = 0x00;
                rom[position++] = 0x00;
                rom[position++] = 0x00;
                rom[position++] = 0x00;
            }
            AssertEx.Equal(source + compressedLength, position);
            return rom;
        }

        private static void StageActive(
            FakeS1Host host,
            int canonicalSource,
            int destination,
            int modulesLeft)
        {
            ClearQueue(host);
            host.Ram[S3KRam.KosModulesLeft] = (byte)modulesLeft;
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            host.SetU32(
                S3KRam.KosModuleQueue,
                (uint)(canonicalSource + 2));
            host.SetU16(
                S3KRam.KosModuleDestination,
                (ushort)destination);
        }

        private static void SetLevelFrame(FakeS1Host host, int value)
        {
            host.SetU16(S3KRam.LevelFrameCounter, (ushort)value);
        }

        private static void SetTitleCardParent(FakeS1Host host)
        {
            int parent = S3KRam.SlotAddress(8);
            host.SetU32(parent, 0x0002D690);
            host.SetU16(parent + 0x48, 1);
        }

        private static void StageQueued(
            FakeS1Host host,
            int index,
            int canonicalSource,
            int destination)
        {
            int entry = S3KRam.KosModuleQueue
                + (index * S3KRam.KosModuleQueueEntrySize);
            host.SetU32(entry, (uint)canonicalSource);
            host.SetU16(entry + 4, (ushort)destination);
        }

        private static void StageEmpty(FakeS1Host host)
        {
            ClearQueue(host);
            host.Ram[S3KRam.KosModulesLeft] = 0;
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
        }

        private static void StageDirect(
            FakeS1Host host,
            int index,
            int source,
            int destination)
        {
            int entry = S3KRam.KosDecompQueue
                + index * S3KRam.KosDecompQueueEntrySize;
            host.SetU32(entry, (uint)source);
            host.SetU32(entry + 4, unchecked((uint)destination));
        }

        private static void ClearQueue(FakeS1Host host)
        {
            for (int offset = 0;
                offset < S3KRam.KosModuleQueueEntrySize
                    * S3KRam.KosModuleQueueCapacity;
                offset++)
            {
                host.Ram[S3KRam.KosModuleQueue + offset] = 0;
            }
        }
    }
}
