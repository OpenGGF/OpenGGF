using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Golden wire-contract tests for the native player-art audit. These
    /// expected strings are deliberately hand-written: changing the lifecycle
    /// model must not silently redefine the trace bytes Java compares.
    /// </summary>
    internal static class DynamicArtTransferStateTests
    {
        private static readonly DynamicArtRunLifecycleContext Sonic1Context =
            new DynamicArtRunLifecycleContext(DynamicArtRomProfile.Sonic1Rev01);
        private static readonly DynamicArtRunLifecycleContext Sonic2Context =
            new DynamicArtRunLifecycleContext(DynamicArtRomProfile.Sonic2Rev01);

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "DynamicArtTransferState formats an empty heartbeat envelope",
                FormatsEmptyHeartbeat));
            tests.Add(new TestMain.TestCase(
                "DynamicArtTransferState preserves ordered submission requests",
                PreservesOrderedRequests));
            tests.Add(new TestMain.TestCase(
                "DynamicArtTransferState validates submission completion pairing",
                ValidatesSubmissionCompletionPairing));
            tests.Add(new TestMain.TestCase(
                "DynamicArtTransferState requires S2 completion requests to match their submission",
                RequiresS2CompletionRequestsToMatchSubmission));
            tests.Add(new TestMain.TestCase(
                "DynamicArtTransferState rejects a cross-profile completion",
                RejectsCrossProfileCompletion));
            tests.Add(new TestMain.TestCase(
                "DynamicArtTransferState formats terminal forwarding",
                FormatsTerminalForwarding));
            tests.Add(new TestMain.TestCase(
                "DynamicArtTransferState pins request address domains and lowercase fingerprints",
                PinsRequestDomainsAndFingerprints));
            tests.Add(new TestMain.TestCase(
                "DynamicArtTransferState formats manifest-only gap transitions",
                FormatsManifestOnlyGapTransitions));
            tests.Add(new TestMain.TestCase(
                "DynamicArtTransferState rejects malformed lifecycle values",
                RejectsMalformedLifecycleValues));
        }

        private static void FormatsEmptyHeartbeat()
        {
            var envelope = new DynamicArtTransferEnvelope(
                7,
                new DynamicArtTransferEdge[0],
                new long[0]);

            AssertEx.Equal(
                "{\"frame\":7,\"event\":\"dynamic_art_transfer_state\","
                + "\"edges\":[],\"outstanding_transfer_ids\":[]}",
                envelope.Format());
            AssertEx.Equal(false, envelope.Format().Contains("\r"));
        }

        private static void PreservesOrderedRequests()
        {
            var edge = SubmittedSegmentEdge(
                11,
                42,
                5,
                9,
                0,
                7,
                false,
                new[]
                {
                    RomRequest(0x50000, 0x12, 0xF000, 0x40),
                    RomRequest(0x50040, 0x34, 0xF080, 0x20)
                });
            var envelope = new DynamicArtTransferEnvelope(
                7,
                new[] { edge },
                new long[] { 42 });

            AssertEx.Equal(
                "{\"frame\":7,\"event\":\"dynamic_art_transfer_state\",\"edges\":["
                + "{\"edge_ordinal\":11,\"transfer_id\":42,\"phase\":\"submitted\","
                + "\"owner\":\"sonic\",\"submission_origin\":\"segment\","
                + "\"mapping_frame\":5,\"logical_frame\":9,\"logical_edge_index\":0,"
                + "\"publication_frame\":7,\"terminal_forwarded\":false,"
                + "\"rom_callback_pc\":82794,\"requests\":["
                + "{\"rom_source_address\":327680,\"source_tile_index\":18,"
                + "\"ram_source_address\":-1,\"vram_destination\":61440,\"byte_length\":64},"
                + "{\"rom_source_address\":327744,\"source_tile_index\":52,"
                + "\"ram_source_address\":-1,\"vram_destination\":61568,\"byte_length\":32}]}],"
                + "\"outstanding_transfer_ids\":[42]}",
                envelope.Format());
        }

        private static void ValidatesSubmissionCompletionPairing()
        {
            var submission = new DynamicArtTransferEnvelope(
                1,
                new[] { SubmittedSegmentEdge(0, 4, 3, 1, 0, 1, false,
                    new[] { RomRequest(0x22610, 2, 0xF000, 0x20) }) },
                new long[] { 4 });
            var completion = new DynamicArtTransferEnvelope(
                2,
                new[] { CompletedSegmentEdge(1, 4, 3, 2, 0, 2, false,
                    new[] { RamRequest(0xC800, 0xF000, 0x2E0) }) },
                new long[0]);

            Sonic1Context.ValidateSegment(new[] { submission, completion });

            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtRunLifecycleContext(
                    DynamicArtRomProfile.Sonic1Rev01).ValidateSegment(
                    new[] { completion }),
                "completion without submission");

            var malformedStagingCompletion = new DynamicArtTransferEnvelope(
                2,
                new[] { CompletedSegmentEdge(1, 4, 3, 2, 0, 2, false,
                    new[] { RamRequest(0xC800, 0xF000, 0x20) }) },
                new long[0]);
            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtRunLifecycleContext(
                    DynamicArtRomProfile.Sonic1Rev01).ValidateSegment(
                    new[] { submission, malformedStagingCompletion }),
                "S1 staging-buffer completion");
        }

        private static void RequiresS2CompletionRequestsToMatchSubmission()
        {
            DynamicArtRequest[] submittedRequests = new[]
            {
                RomRequest(0x64320, 1, 0xF400, 0x20),
                RomRequest(0x64340, 2, 0xF440, 0x20)
            };
            var submission = new DynamicArtTransferEnvelope(
                0,
                new[]
                {
                    new DynamicArtTransferEdge(
                        20, 12, DynamicArtTransferPhase.Submitted, "tails",
                        DynamicArtSubmissionOrigin.Segment, 4, 0, 0, 0, false,
                        Sonic2Context.CallbackValidator, 0x14AA, submittedRequests)
                },
                new long[] { 12 });
            var matchingCompletion = new DynamicArtTransferEnvelope(
                1,
                new[]
                {
                    new DynamicArtTransferEdge(
                        21, 12, DynamicArtTransferPhase.Completed, "tails",
                        DynamicArtSubmissionOrigin.Segment, 4, 1, 0, 1, false,
                        Sonic2Context.CallbackValidator, 0x14AC, submittedRequests)
                },
                new long[0]);
            Sonic2Context.ValidateSegment(new[] { submission, matchingCompletion });

            DynamicArtRequest[] specialStageRequests = new[]
            {
                RamRequest(0xFFA000, 0x5CA0, 0x20),
                RamRequest(0xFFA020, 0x5CC0, 0x40)
            };
            var specialStageSubmission = new DynamicArtTransferEnvelope(
                2,
                new[]
                {
                    new DynamicArtTransferEdge(
                        22, 13, DynamicArtTransferPhase.Submitted, "ss-sonic",
                        DynamicArtSubmissionOrigin.Segment, 4, 2, 0, 2, false,
                        Sonic2Context.CallbackValidator, 0x33B3E,
                        specialStageRequests)
                },
                new long[] { 13 });
            var specialStageCompletion = new DynamicArtTransferEnvelope(
                3,
                new[]
                {
                    new DynamicArtTransferEdge(
                        23, 13, DynamicArtTransferPhase.Completed, "ss-sonic",
                        DynamicArtSubmissionOrigin.Segment, 4, 3, 0, 3, false,
                        Sonic2Context.CallbackValidator, 0x14AC,
                        specialStageRequests)
                },
                new long[0]);
            Sonic2Context.ValidateSegment(
                new[] { specialStageSubmission, specialStageCompletion });

            var reorderedCompletion = new DynamicArtTransferEnvelope(
                1,
                new[]
                {
                    new DynamicArtTransferEdge(
                        21, 12, DynamicArtTransferPhase.Completed, "tails",
                        DynamicArtSubmissionOrigin.Segment, 4, 1, 0, 1, false,
                        Sonic2Context.CallbackValidator, 0x14AC,
                        new[] { submittedRequests[1], submittedRequests[0] })
                },
                new long[0]);
            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtRunLifecycleContext(
                    DynamicArtRomProfile.Sonic2Rev01).ValidateSegment(
                    new[] { submission, reorderedCompletion }),
                "ROM completion requests do not match");
        }

        private static void RejectsCrossProfileCompletion()
        {
            var submission = new DynamicArtTransferEnvelope(
                0,
                new[]
                {
                    new DynamicArtTransferEdge(
                        30, 13, DynamicArtTransferPhase.Submitted, "sonic",
                        DynamicArtSubmissionOrigin.Segment, 5, 0, 0, 0, false,
                        Sonic2Context.CallbackValidator, 0x14AA,
                        new[] { RomRequest(0x50000, 1, 0xF000, 0x20) })
                },
                new long[] { 13 });
            var sonic1Completion = new DynamicArtTransferEnvelope(
                1,
                new[]
                {
                    new DynamicArtTransferEdge(
                        31, 13, DynamicArtTransferPhase.Completed, "sonic",
                        DynamicArtSubmissionOrigin.Segment, 5, 1, 0, 1, false,
                        Sonic1Context.CallbackValidator, 0x0D50,
                        new[] { RamRequest(0xC800, 0xF000, 0x2E0) })
                },
                new long[0]);

            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtRunLifecycleContext(
                    DynamicArtRomProfile.Sonic2Rev01).ValidateSegment(
                    new[] { submission, sonic1Completion }),
                "ROM profile");
        }

        private static void FormatsTerminalForwarding()
        {
            var envelope = new DynamicArtTransferEnvelope(
                12,
                new[]
                {
                    CompletedSegmentEdge(6, 9, 4, 21, 1, 12, true,
                        new[] { RamRequest(0xC800, 0xF000, 0x2E0) })
                },
                new long[0]);

            AssertEx.Equal(true, envelope.Format().Contains(
                "\"terminal_forwarded\":true,\"rom_callback_pc\":3408"));
        }

        private static void PinsRequestDomainsAndFingerprints()
        {
            var rom = RomRequest(0x50000, 3, 0xF000, 0x20);
            var ram = RamRequest(0xC800, 0xF000, 0x2E0);
            AssertEx.Equal(-1, rom.RamSourceAddress);
            AssertEx.Equal(-1, ram.RomSourceAddress);
            AssertEx.Equal(-1, ram.SourceTileIndex);

            var descriptor = new DynamicArtTransferDescriptor(
                8,
                "sonic",
                3,
                DynamicArtSubmissionOrigin.Segment,
                new[] { rom });
            AssertEx.Equal(
                "sha256:d88407d809c9b640e2484aa26486f551fc0ce75f62728e40c4a150e45b6673ca",
                descriptor.Fingerprint);
            AssertEx.Equal(true, descriptor.Fingerprint == descriptor.Fingerprint.ToLowerInvariant());

            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtRequest(0x50000, -1, -1, 0xF000, 0x20),
                "ROM request source_tile_index");
            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtRequest(-1, -1, -1, 0xF000, 0x20),
                "exactly one source domain");
            new DynamicArtTransferDescriptor(
                9, "ss-sonic", 3, DynamicArtSubmissionOrigin.Segment,
                new[] { ram });
            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtTransferDescriptor(
                    10, "sonic", 3, DynamicArtSubmissionOrigin.Segment,
                    new[] { rom, ram }),
                "one source domain");
        }

        private static void FormatsManifestOnlyGapTransitions()
        {
            var descriptor = new DynamicArtTransferDescriptor(
                9,
                "tails",
                4,
                DynamicArtSubmissionOrigin.RunGap,
                new[] { RomRequest(0x64320, 1, 0xF400, 0x20) });
            var edge = new DynamicArtGapEdge(
                3,
                9,
                DynamicArtTransferPhase.Submitted,
                "tails",
                DynamicArtSubmissionOrigin.RunGap,
                4,
                18,
                0,
                Sonic2Context.CallbackValidator,
                0x14AA,
                descriptor.Requests);
            var transition = new DynamicArtGapTransition(
                edge,
                "sha256:42f87419ea3765ece5e0a63ffa9f9ebe5e60d91c115090adf9133c0bd0aca3c9",
                new[] { descriptor });

            Sonic2Context.ValidateGap(
                new[] { transition }, new DynamicArtTransferDescriptor[0]);

            AssertEx.Equal(
                "{\"dynamic_art_gap_edge\":{\"edge_ordinal\":3,\"transfer_id\":9,"
                + "\"phase\":\"submitted\",\"owner\":\"tails\","
                + "\"submission_origin\":\"run_gap\",\"mapping_frame\":4,"
                + "\"movie_logical_frame\":18,\"gap_edge_index\":0,"
                + "\"rom_callback_pc\":5290,\"requests\":[{\"rom_source_address\":410400,"
                + "\"source_tile_index\":1,\"ram_source_address\":-1,"
                + "\"vram_destination\":62464,\"byte_length\":32}]},"
                + "\"before_ledger_hash\":\"sha256:42f87419ea3765ece5e0a63ffa9f9ebe5e60d91c115090adf9133c0bd0aca3c9\","
                + "\"after_ledger_descriptors\":[{\"transfer_id\":9,\"owner\":\"tails\","
                + "\"mapping_frame\":4,\"submission_origin\":\"run_gap\",\"requests\":[{"
                + "\"rom_source_address\":410400,\"source_tile_index\":1,\"ram_source_address\":-1,"
                + "\"vram_destination\":62464,\"byte_length\":32}],\"fingerprint\":"
                + "\"sha256:ff64bf72b7cc56fbd3c31656213363d447e149e3f4cc6c3f35f4f6a6a294cd63\"}]}",
                transition.Format());

            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtRunLifecycleContext(
                    DynamicArtRomProfile.Sonic2Rev01).ValidateGap(
                    new[]
                    {
                        new DynamicArtGapTransition(
                            edge,
                            "sha256:42f87419ea3765ece5e0a63ffa9f9ebe5e60d91c115090adf9133c0bd0aca3c9",
                            new DynamicArtTransferDescriptor[0])
                    }, new DynamicArtTransferDescriptor[0]),
                "afterLedgerDescriptors");

            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtGapEdge(
                    4, 10, DynamicArtTransferPhase.Submitted, "sonic",
                    DynamicArtSubmissionOrigin.Segment, 1, 19, 0, Sonic1Context.CallbackValidator, 0x1436A,
                    new[] { RomRequest(0x22610, 0, 0xF000, 0x20) }),
                "run_gap");
        }

        private static void RejectsMalformedLifecycleValues()
        {
            AssertEx.Throws<ArgumentOutOfRangeException>(
                () => SubmittedSegmentEdge(0, 1, 0, -1, 0, 0, false,
                    new[] { RomRequest(0x22610, 0, 0xF000, 0x20) }),
                "logicalFrame");
            AssertEx.Throws<ArgumentOutOfRangeException>(
                () => new DynamicArtGapEdge(
                    0, 1, DynamicArtTransferPhase.Submitted, "sonic",
                    DynamicArtSubmissionOrigin.RunGap, 0, -1, 0, Sonic1Context.CallbackValidator, 0x1436A,
                    new[] { RomRequest(0x22610, 0, 0xF000, 0x20) }),
                "movieLogicalFrame");
            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtTransferEdge(
                    0, 1, DynamicArtTransferPhase.Submitted, "knuckles",
                    DynamicArtSubmissionOrigin.Segment, 0, 0, 0, 0, false,
                    Sonic1Context.CallbackValidator, 0x1436A,
                    new[] { RomRequest(0x22610, 0, 0xF000, 0x20) }),
                "owner");
            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtTransferEdge(
                    0, 1, (DynamicArtTransferPhase)99, "sonic",
                    DynamicArtSubmissionOrigin.Segment, 0, 0, 0, 0, false,
                    Sonic1Context.CallbackValidator, 0x1436A,
                    new[] { RomRequest(0x22610, 0, 0xF000, 0x20) }),
                "phase");
            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtGapTransition(
                    new DynamicArtGapEdge(0, 1, DynamicArtTransferPhase.Submitted,
                        "sonic", DynamicArtSubmissionOrigin.RunGap, 0, 0, 0,
                        Sonic1Context.CallbackValidator, 0x1436A,
                        new[] { RomRequest(0x22610, 0, 0xF000, 0x20) }),
                    "not-a-fingerprint",
                    new DynamicArtTransferDescriptor[0]),
                "beforeLedgerHash");

            var first = new DynamicArtTransferEnvelope(
                0,
                new[] { SubmittedSegmentEdge(2, 1, 0, 0, 0, 0, false,
                    new[] { RomRequest(0x22610, 0, 0xF000, 0x20) }) },
                new long[] { 1 });
            var duplicateOrdinal = new DynamicArtTransferEnvelope(
                1,
                new[] { CompletedSegmentEdge(2, 1, 0, 1, 0, 1, false,
                    new[] { RamRequest(0xC800, 0xF000, 0x20) }) },
                new long[0]);
            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtRunLifecycleContext(
                    DynamicArtRomProfile.Sonic1Rev01).ValidateSegment(
                    new[] { first, duplicateOrdinal }),
                "duplicate edge_ordinal");

            var identity = new DynamicArtRunLifecycleContext(
                DynamicArtRomProfile.Sonic1Rev01);
            identity.ValidateSegment(new[] { first });
            var duplicateGapEdge = new DynamicArtGapEdge(
                2, 1, DynamicArtTransferPhase.Completed, "sonic",
                DynamicArtSubmissionOrigin.Segment, 0, 4, 0, Sonic1Context.CallbackValidator,
                0x0D50, new[] { RamRequest(0xC800, 0xF000, 0x20) });
            AssertEx.Throws<ArgumentException>(
                () => identity.ValidateGap(
                    new[]
                    {
                        new DynamicArtGapTransition(
                            duplicateGapEdge,
                            "sha256:0000000000000000000000000000000000000000000000000000000000000000",
                            new DynamicArtTransferDescriptor[0])
                    }, new[]
                    {
                        new DynamicArtTransferDescriptor(
                            1, "sonic", 0, DynamicArtSubmissionOrigin.Segment,
                            new[] { RomRequest(0x22610, 0, 0xF000, 0x20) })
                    }),
                "duplicate edge_ordinal");

            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtTransferEnvelope(
                    2,
                    new[] { SubmittedSegmentEdge(3, 2, 0, 2, 0, 3, false,
                        new[] { RomRequest(0x22610, 0, 0xF000, 0x20) }) },
                    new long[] { 2 }),
                "publication_frame");
            AssertEx.Throws<ArgumentException>(
                () => new DynamicArtTransferEdge(
                    4, 3, DynamicArtTransferPhase.Submitted, "sonic",
                    DynamicArtSubmissionOrigin.Segment, 0, 2, 0, 2, false,
                    Sonic1Context.CallbackValidator, 0x14AA,
                    new[] { RomRequest(0x22610, 0, 0xF000, 0x20) }),
                "rom_callback_pc");
        }

        private static DynamicArtTransferEdge SubmittedSegmentEdge(
            long ordinal, long transferId, int mappingFrame, int logicalFrame,
            int logicalEdgeIndex, int publicationFrame, bool terminalForwarded,
            IList<DynamicArtRequest> requests)
        {
            return new DynamicArtTransferEdge(
                ordinal, transferId, DynamicArtTransferPhase.Submitted, "sonic",
                DynamicArtSubmissionOrigin.Segment, mappingFrame, logicalFrame,
                logicalEdgeIndex, publicationFrame, terminalForwarded, Sonic1Context.CallbackValidator,
                0x1436A,
                requests);
        }

        private static DynamicArtTransferEdge CompletedSegmentEdge(
            long ordinal, long transferId, int mappingFrame, int logicalFrame,
            int logicalEdgeIndex, int publicationFrame, bool terminalForwarded,
            IList<DynamicArtRequest> requests)
        {
            return new DynamicArtTransferEdge(
                ordinal, transferId, DynamicArtTransferPhase.Completed, "sonic",
                DynamicArtSubmissionOrigin.Segment, mappingFrame, logicalFrame,
                logicalEdgeIndex, publicationFrame, terminalForwarded, Sonic1Context.CallbackValidator,
                0x0D50,
                requests);
        }

        private static DynamicArtRequest RomRequest(
            int romSourceAddress, int sourceTileIndex, int vramDestination,
            int byteLength)
        {
            return new DynamicArtRequest(
                romSourceAddress, sourceTileIndex, -1, vramDestination, byteLength);
        }

        private static DynamicArtRequest RamRequest(
            int ramSourceAddress, int vramDestination, int byteLength)
        {
            return new DynamicArtRequest(
                -1, -1, ramSourceAddress, vramDestination, byteLength);
        }
    }
}
