using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using static OpenGGF.BizHawk.Headless.DynamicArtTransferState;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// The only two ownership domains for a player-art submission. Segment
    /// events are emitted into aux_state.jsonl; run-gap events remain in the
    /// manifest because no stored physics row owns their callbacks.
    /// </summary>
    public enum DynamicArtSubmissionOrigin
    {
        Segment,
        RunGap
    }

    public enum DynamicArtTransferPhase
    {
        Submitted,
        Completed
    }

    /// <summary>
    /// Recorder-only callback evidence boundary. It derives the permitted
    /// source PCs exclusively from the immutable retail-ROM profile and never
    /// exposes them to an engine comparator.
    /// </summary>
    public sealed class DynamicArtCallbackValidator
    {
        private readonly DynamicArtRomProfile.GameProfile profile;
        private readonly string profileId;
        private readonly HashSet<int> submissionCallbacks;
        private readonly HashSet<int> completionCallbacks;

        public DynamicArtCallbackValidator(DynamicArtRomProfile.GameProfile profile)
        {
            if (profile == null) throw new ArgumentNullException("profile");
            this.profile = profile;
            profileId = profile.Id;
            submissionCallbacks = new HashSet<int>();
            completionCallbacks = new HashSet<int>();
            for (int index = 0; index < profile.DecisionWindows.Count; index++)
            {
                submissionCallbacks.Add(profile.DecisionWindows[index].ReturnAddress);
            }
            if (profile.AcceptedDmaReturn != 0)
            {
                submissionCallbacks.Add(profile.AcceptedDmaReturn);
            }
            for (int index = 0; index < profile.VBlankCompletionSites.Count; index++)
            {
                completionCallbacks.Add(profile.VBlankCompletionSites[index]);
                if (profileId == "s1-rev01")
                {
                    submissionCallbacks.Add(
                        profile.VBlankCompletionSites[index] - 0x30);
                }
            }
        }

        internal bool IsSonic1 { get { return profileId == "s1-rev01"; } }
        internal string ProfileId { get { return profileId; } }

        internal void ValidateSonic1StagingCompletion(
            IList<DynamicArtRequest> requests)
        {
            DynamicArtRequest request = requests[0];
            if (!IsSonic1 || requests.Count != 1
                || request.RamSourceAddress != profile.Ram.StagingBuffer
                || request.VramDestination != profile.VramBanks[0].Destination
                || request.ByteLength != profile.Ram.StagingBufferLength)
            {
                throw new ArgumentException(
                    "S1 staging-buffer completion does not match the pinned ROM profile",
                    "requests");
            }
        }

        internal void ValidateCallback(
            DynamicArtTransferPhase phase, int romCallbackPc)
        {
            ISet<int> allowed = phase == DynamicArtTransferPhase.Submitted
                ? submissionCallbacks
                : completionCallbacks;
            if (!allowed.Contains(romCallbackPc))
            {
                throw new ArgumentException(
                    "rom_callback_pc is not permitted by the pinned ROM profile",
                    "romCallbackPc");
            }
        }
    }

    /// <summary>
    /// One requested player-art transfer. A request has exactly one source
    /// domain: ROM runs carry a ROM address and source tile; S1's physical
    /// completion carries a RAM staging-buffer address. Unused domains are
    /// represented only by -1, never by an omitted JSON field.
    /// </summary>
    public sealed class DynamicArtRequest
    {
        public DynamicArtRequest(
            int romSourceAddress,
            int sourceTileIndex,
            int ramSourceAddress,
            int vramDestination,
            int byteLength)
        {
            bool romBacked = romSourceAddress >= 0
                && sourceTileIndex >= 0
                && ramSourceAddress == -1;
            bool ramBacked = romSourceAddress == -1
                && sourceTileIndex == -1
                && ramSourceAddress >= 0;
            if (!romBacked && !ramBacked)
            {
                if (romSourceAddress >= 0 && sourceTileIndex < 0
                    && ramSourceAddress == -1)
                {
                    throw new ArgumentException(
                        "ROM request source_tile_index must be nonnegative",
                        "sourceTileIndex");
                }
                throw new ArgumentException(
                    "request must select exactly one source domain",
                    "romSourceAddress");
            }
            if (vramDestination < 0)
            {
                throw new ArgumentOutOfRangeException("vramDestination");
            }
            if (byteLength <= 0)
            {
                throw new ArgumentOutOfRangeException("byteLength");
            }

            RomSourceAddress = romSourceAddress;
            SourceTileIndex = sourceTileIndex;
            RamSourceAddress = ramSourceAddress;
            VramDestination = vramDestination;
            ByteLength = byteLength;
        }

        public int RomSourceAddress { get; private set; }
        public int SourceTileIndex { get; private set; }
        public int RamSourceAddress { get; private set; }
        public int VramDestination { get; private set; }
        public int ByteLength { get; private set; }

        public bool IsRomBacked { get { return RomSourceAddress >= 0; } }
        public bool IsRamBacked { get { return RamSourceAddress >= 0; } }

        internal void AppendJson(StringBuilder json)
        {
            json.Append("{\"rom_source_address\":").Append(Dec(RomSourceAddress))
                .Append(",\"source_tile_index\":").Append(Dec(SourceTileIndex))
                .Append(",\"ram_source_address\":").Append(Dec(RamSourceAddress))
                .Append(",\"vram_destination\":").Append(Dec(VramDestination))
                .Append(",\"byte_length\":").Append(Dec(ByteLength)).Append("}");
        }
    }

    /// <summary>
    /// Immutable pending-ledger identity. It is the submission fact retained
    /// until the matching ROM completion callback retires it.
    /// </summary>
    public sealed class DynamicArtTransferDescriptor
    {
        public DynamicArtTransferDescriptor(
            long transferId,
            string owner,
            int mappingFrame,
            DynamicArtSubmissionOrigin submissionOrigin,
            IList<DynamicArtRequest> requests)
        {
            ValidateTransferId(transferId);
            ValidateOwner(owner);
            ValidateNonnegative(mappingFrame, "mappingFrame");
            ValidateOrigin(submissionOrigin, "submissionOrigin");
            Requests = FreezeRequests(requests, "requests");
            ValidateSubmissionRequests(Requests, "requests");

            TransferId = transferId;
            Owner = owner;
            MappingFrame = mappingFrame;
            SubmissionOrigin = submissionOrigin;
            Fingerprint = ComputeFingerprint(this);
        }

        public long TransferId { get; private set; }
        public string Owner { get; private set; }
        public int MappingFrame { get; private set; }
        public DynamicArtSubmissionOrigin SubmissionOrigin { get; private set; }
        public ReadOnlyCollection<DynamicArtRequest> Requests { get; private set; }
        public string Fingerprint { get; private set; }

        internal void AppendJson(StringBuilder json)
        {
            json.Append("{\"transfer_id\":").Append(Dec(TransferId))
                .Append(",\"owner\":\"").Append(Owner).Append("\"")
                .Append(",\"mapping_frame\":").Append(Dec(MappingFrame))
                .Append(",\"submission_origin\":\"")
                .Append(OriginWire(SubmissionOrigin)).Append("\"")
                .Append(",\"requests\":[");
            AppendRequests(json, Requests);
            json.Append("],\"fingerprint\":\"").Append(Fingerprint).Append("\"}");
        }
    }

    /// <summary>
    /// A lifecycle callback published on a stored segment row. Callback PCs
    /// are ROM-validation evidence only; no engine comparator should receive
    /// or compare an address against this field.
    /// </summary>
    public sealed class DynamicArtTransferEdge
    {
        public DynamicArtTransferEdge(
            long edgeOrdinal,
            long transferId,
            DynamicArtTransferPhase phase,
            string owner,
            DynamicArtSubmissionOrigin submissionOrigin,
            int mappingFrame,
            int logicalFrame,
            int logicalEdgeIndex,
            int publicationFrame,
            bool terminalForwarded,
            DynamicArtCallbackValidator callbackValidator,
            int romCallbackPc,
            IList<DynamicArtRequest> requests)
        {
            ValidateEdgeValues(edgeOrdinal, transferId, phase, owner,
                submissionOrigin, mappingFrame, callbackValidator, romCallbackPc, requests);
            ValidateNonnegative(logicalFrame, "logicalFrame");
            ValidateNonnegative(logicalEdgeIndex, "logicalEdgeIndex");
            ValidateNonnegative(publicationFrame, "publicationFrame");
            if (submissionOrigin != DynamicArtSubmissionOrigin.Segment
                && !(phase == DynamicArtTransferPhase.Completed
                    && submissionOrigin == DynamicArtSubmissionOrigin.RunGap))
            {
                throw new ArgumentException(
                    "segment edge must be segment-owned or complete inherited run-gap work",
                    "submissionOrigin");
            }

            EdgeOrdinal = edgeOrdinal;
            TransferId = transferId;
            Phase = phase;
            Owner = owner;
            SubmissionOrigin = submissionOrigin;
            MappingFrame = mappingFrame;
            LogicalFrame = logicalFrame;
            LogicalEdgeIndex = logicalEdgeIndex;
            PublicationFrame = publicationFrame;
            TerminalForwarded = terminalForwarded;
            CallbackValidator = callbackValidator;
            RomCallbackPc = romCallbackPc;
            Requests = FreezeRequests(requests, "requests");
        }

        public long EdgeOrdinal { get; private set; }
        public long TransferId { get; private set; }
        public DynamicArtTransferPhase Phase { get; private set; }
        public string Owner { get; private set; }
        public DynamicArtSubmissionOrigin SubmissionOrigin { get; private set; }
        public int MappingFrame { get; private set; }
        public int LogicalFrame { get; private set; }
        public int LogicalEdgeIndex { get; private set; }
        public int PublicationFrame { get; private set; }
        public bool TerminalForwarded { get; private set; }
        internal DynamicArtCallbackValidator CallbackValidator { get; private set; }
        public int RomCallbackPc { get; private set; }
        public ReadOnlyCollection<DynamicArtRequest> Requests { get; private set; }

        internal DynamicArtTransferDescriptor SubmissionDescriptor()
        {
            return new DynamicArtTransferDescriptor(
                TransferId, Owner, MappingFrame, SubmissionOrigin, Requests);
        }

        internal void AppendJson(StringBuilder json)
        {
            json.Append("{\"edge_ordinal\":").Append(Dec(EdgeOrdinal))
                .Append(",\"transfer_id\":").Append(Dec(TransferId))
                .Append(",\"phase\":\"").Append(PhaseWire(Phase)).Append("\"")
                .Append(",\"owner\":\"").Append(Owner).Append("\"")
                .Append(",\"submission_origin\":\"")
                .Append(OriginWire(SubmissionOrigin)).Append("\"")
                .Append(",\"mapping_frame\":").Append(Dec(MappingFrame))
                .Append(",\"logical_frame\":").Append(Dec(LogicalFrame))
                .Append(",\"logical_edge_index\":").Append(Dec(LogicalEdgeIndex))
                .Append(",\"publication_frame\":").Append(Dec(PublicationFrame))
                .Append(",\"terminal_forwarded\":")
                .Append(TerminalForwarded ? "true" : "false")
                .Append(",\"rom_callback_pc\":").Append(Dec(RomCallbackPc))
                .Append(",\"requests\":[");
            AppendRequests(json, Requests);
            json.Append("]}");
        }
    }

    /// <summary>
    /// The mandatory one-per-stored-row heartbeat. Empty edges are observable
    /// evidence that recording was armed, rather than an omitted event.
    /// </summary>
    public sealed class DynamicArtTransferEnvelope
    {
        public DynamicArtTransferEnvelope(
            int frame,
            IList<DynamicArtTransferEdge> edges,
            IList<long> outstandingTransferIds)
        {
            ValidateNonnegative(frame, "frame");
            Edges = FreezeEdges(edges, "edges");
            for (int index = 0; index < Edges.Count; index++)
            {
                if (Edges[index].PublicationFrame != frame)
                {
                    throw new ArgumentException(
                        "edge publication_frame must equal its envelope frame",
                        "edges");
                }
            }
            OutstandingTransferIds = FreezeIds(
                outstandingTransferIds, "outstandingTransferIds");
            Frame = frame;
        }

        public int Frame { get; private set; }
        public ReadOnlyCollection<DynamicArtTransferEdge> Edges { get; private set; }
        public ReadOnlyCollection<long> OutstandingTransferIds { get; private set; }

        public string Format()
        {
            var json = new StringBuilder();
            json.Append("{\"frame\":").Append(Dec(Frame))
                .Append(",\"event\":\"dynamic_art_transfer_state\",\"edges\":[");
            for (int index = 0; index < Edges.Count; index++)
            {
                if (index != 0) json.Append(",");
                Edges[index].AppendJson(json);
            }
            json.Append("],\"outstanding_transfer_ids\":[");
            AppendIds(json, OutstandingTransferIds);
            return json.Append("]}").ToString();
        }
    }

    /// <summary>
    /// An edge observed while no segment owns a stored physics row. It has a
    /// run-wide movie cursor only: segment logical/publication fields are
    /// deliberately absent from both the type and its JSON representation.
    /// </summary>
    public sealed class DynamicArtGapEdge
    {
        public DynamicArtGapEdge(
            long edgeOrdinal,
            long transferId,
            DynamicArtTransferPhase phase,
            string owner,
            DynamicArtSubmissionOrigin submissionOrigin,
            int mappingFrame,
            int movieLogicalFrame,
            int gapEdgeIndex,
            DynamicArtCallbackValidator callbackValidator,
            int romCallbackPc,
            IList<DynamicArtRequest> requests)
        {
            ValidateEdgeValues(edgeOrdinal, transferId, phase, owner,
                submissionOrigin, mappingFrame, callbackValidator, romCallbackPc, requests);
            ValidateNonnegative(movieLogicalFrame, "movieLogicalFrame");
            ValidateNonnegative(gapEdgeIndex, "gapEdgeIndex");
            if (phase == DynamicArtTransferPhase.Submitted
                && submissionOrigin != DynamicArtSubmissionOrigin.RunGap)
            {
                throw new ArgumentException(
                    "gap submission submission_origin must be run_gap",
                    "submissionOrigin");
            }

            EdgeOrdinal = edgeOrdinal;
            TransferId = transferId;
            Phase = phase;
            Owner = owner;
            SubmissionOrigin = submissionOrigin;
            MappingFrame = mappingFrame;
            MovieLogicalFrame = movieLogicalFrame;
            GapEdgeIndex = gapEdgeIndex;
            CallbackValidator = callbackValidator;
            RomCallbackPc = romCallbackPc;
            Requests = FreezeRequests(requests, "requests");
        }

        public long EdgeOrdinal { get; private set; }
        public long TransferId { get; private set; }
        public DynamicArtTransferPhase Phase { get; private set; }
        public string Owner { get; private set; }
        public DynamicArtSubmissionOrigin SubmissionOrigin { get; private set; }
        public int MappingFrame { get; private set; }
        public int MovieLogicalFrame { get; private set; }
        public int GapEdgeIndex { get; private set; }
        internal DynamicArtCallbackValidator CallbackValidator { get; private set; }
        public int RomCallbackPc { get; private set; }
        public ReadOnlyCollection<DynamicArtRequest> Requests { get; private set; }

        internal DynamicArtTransferDescriptor SubmissionDescriptor()
        {
            return new DynamicArtTransferDescriptor(
                TransferId, Owner, MappingFrame, SubmissionOrigin, Requests);
        }

        internal void AppendJson(StringBuilder json)
        {
            json.Append("{\"edge_ordinal\":").Append(Dec(EdgeOrdinal))
                .Append(",\"transfer_id\":").Append(Dec(TransferId))
                .Append(",\"phase\":\"").Append(PhaseWire(Phase)).Append("\"")
                .Append(",\"owner\":\"").Append(Owner).Append("\"")
                .Append(",\"submission_origin\":\"")
                .Append(OriginWire(SubmissionOrigin)).Append("\"")
                .Append(",\"mapping_frame\":").Append(Dec(MappingFrame))
                .Append(",\"movie_logical_frame\":").Append(Dec(MovieLogicalFrame))
                .Append(",\"gap_edge_index\":").Append(Dec(GapEdgeIndex))
                .Append(",\"rom_callback_pc\":").Append(Dec(RomCallbackPc))
                .Append(",\"requests\":[");
            AppendRequests(json, Requests);
            json.Append("]}");
        }
    }

    /// <summary>
    /// One manifest-only state transition. The hash names the exact ledger
    /// before this edge; descriptors make the post-edge ledger auditable
    /// without ever letting the trace seed a production ledger.
    /// </summary>
    public sealed class DynamicArtGapTransition
    {
        public DynamicArtGapTransition(
            DynamicArtGapEdge edge,
            string beforeLedgerHash,
            IList<DynamicArtTransferDescriptor> afterLedgerDescriptors)
        {
            if (edge == null) throw new ArgumentNullException("edge");
            ValidateFingerprint(beforeLedgerHash, "beforeLedgerHash");
            Edge = edge;
            BeforeLedgerHash = beforeLedgerHash;
            AfterLedgerDescriptors = FreezeDescriptors(
                afterLedgerDescriptors, "afterLedgerDescriptors");
        }

        public DynamicArtGapEdge Edge { get; private set; }
        public string BeforeLedgerHash { get; private set; }
        public ReadOnlyCollection<DynamicArtTransferDescriptor> AfterLedgerDescriptors
        {
            get;
            private set;
        }

        public string Format()
        {
            var json = new StringBuilder();
            json.Append("{\"dynamic_art_gap_edge\":");
            Edge.AppendJson(json);
            json.Append(",\"before_ledger_hash\":\"")
                .Append(BeforeLedgerHash)
                .Append("\",\"after_ledger_descriptors\":[");
            for (int index = 0; index < AfterLedgerDescriptors.Count; index++)
            {
                if (index != 0) json.Append(",");
                AfterLedgerDescriptors[index].AppendJson(json);
            }
            return json.Append("]}").ToString();
        }
    }

    /// <summary>
    /// Run-wide ordinal authority shared by segment and manifest-gap
    /// validators. Capture owns one instance for the complete movie so an
    /// ordinal can never be reused when no physics row exists.
    /// </summary>
    internal sealed class DynamicArtLifecycleIdentityValidator
    {
        private readonly HashSet<long> edgeOrdinals = new HashSet<long>();

        public void ObserveSegment(DynamicArtTransferEdge edge)
        {
            if (edge == null) throw new ArgumentNullException("edge");
            Observe(edge.EdgeOrdinal);
        }

        public void ObserveGap(DynamicArtGapEdge edge)
        {
            if (edge == null) throw new ArgumentNullException("edge");
            Observe(edge.EdgeOrdinal);
        }

        private void Observe(long edgeOrdinal)
        {
            if (!edgeOrdinals.Add(edgeOrdinal))
            {
                throw new ArgumentException(
                    "duplicate edge_ordinal " + edgeOrdinal,
                    "edgeOrdinal");
            }
        }
    }

    /// <summary>
    /// Required run-scoped validation owner. It binds the immutable ROM
    /// profile identity and the global edge-ordinal ledger for one movie;
    /// callers cannot validate an individual segment or gap with a fresh
    /// implicit identity set.
    /// </summary>
    public sealed class DynamicArtRunLifecycleContext
    {
        private readonly string profileId;
        private readonly DynamicArtLifecycleIdentityValidator identity =
            new DynamicArtLifecycleIdentityValidator();

        public DynamicArtRunLifecycleContext(DynamicArtRomProfile.GameProfile profile)
        {
            if (profile == null) throw new ArgumentNullException("profile");
            profileId = profile.Id;
            CallbackValidator = new DynamicArtCallbackValidator(profile);
        }

        public DynamicArtCallbackValidator CallbackValidator { get; private set; }

        public void ValidateSegment(IList<DynamicArtTransferEnvelope> envelopes)
        {
            DynamicArtTransferState.ValidateSegmentLifecycle(envelopes, this);
        }

        public void ValidateGap(
            IList<DynamicArtGapTransition> transitions,
            IList<DynamicArtTransferDescriptor> openingLedger)
        {
            DynamicArtTransferState.ValidateGapLifecycle(
                transitions, openingLedger, this);
        }

        internal void ValidateProfile(DynamicArtCallbackValidator callbackValidator)
        {
            if (callbackValidator == null
                || callbackValidator.ProfileId != profileId)
            {
                throw new ArgumentException(
                    "edge callback validator does not match the run ROM profile",
                    "callbackValidator");
            }
        }

        internal DynamicArtLifecycleIdentityValidator Identity
        {
            get { return identity; }
        }
    }

    /// <summary>
    /// Fail-closed lifecycle validation shared by later S1/S2 observers.
    /// This is recorder-only validation; it contains no engine or renderer
    /// reference and cannot schedule transfer work.
    /// </summary>
    public static class DynamicArtTransferState
    {
        internal static void ValidateSegmentLifecycle(
            IList<DynamicArtTransferEnvelope> envelopes,
            DynamicArtRunLifecycleContext context)
        {
            if (envelopes == null) throw new ArgumentNullException("envelopes");
            if (context == null) throw new ArgumentNullException("context");
            var ledger = new List<DynamicArtTransferDescriptor>();
            int previousFrame = -1;
            int previousLogicalFrame = -1;
            int previousLogicalIndex = -1;
            for (int envelopeIndex = 0; envelopeIndex < envelopes.Count;
                 envelopeIndex++)
            {
                DynamicArtTransferEnvelope envelope = envelopes[envelopeIndex];
                if (envelope == null)
                {
                    throw new ArgumentException("envelopes contains null", "envelopes");
                }
                if (envelope.Frame <= previousFrame)
                {
                    throw new ArgumentException(
                        "envelope frames must be strictly increasing", "envelopes");
                }
                previousFrame = envelope.Frame;
                for (int edgeIndex = 0; edgeIndex < envelope.Edges.Count; edgeIndex++)
                {
                    DynamicArtTransferEdge edge = envelope.Edges[edgeIndex];
                    if (edge.PublicationFrame != envelope.Frame)
                    {
                        throw new ArgumentException(
                            "edge publication_frame must equal its envelope frame",
                            "envelopes");
                    }
                    context.ValidateProfile(edge.CallbackValidator);
                    context.Identity.ObserveSegment(edge);
                    if (edge.LogicalFrame < previousLogicalFrame
                        || (edge.LogicalFrame == previousLogicalFrame
                            && edge.LogicalEdgeIndex <= previousLogicalIndex))
                    {
                        throw new ArgumentException(
                            "segment logical cursor must be strictly increasing",
                            "envelopes");
                    }
                    previousLogicalFrame = edge.LogicalFrame;
                    previousLogicalIndex = edge.LogicalEdgeIndex;
                    ApplySegmentEdge(ledger, edge);
                }
                ValidateLedgerIds(
                    ledger, envelope.OutstandingTransferIds, "outstandingTransferIds");
            }
        }

        internal static void ValidateGapLifecycle(
            IList<DynamicArtGapTransition> transitions,
            IList<DynamicArtTransferDescriptor> openingLedger,
            DynamicArtRunLifecycleContext context)
        {
            if (transitions == null) throw new ArgumentNullException("transitions");
            if (context == null) throw new ArgumentNullException("context");
            var ledger = CopyDescriptors(openingLedger, "openingLedger");
            int previousMovieFrame = -1;
            int previousGapIndex = -1;
            for (int index = 0; index < transitions.Count; index++)
            {
                DynamicArtGapTransition transition = transitions[index];
                if (transition == null)
                {
                    throw new ArgumentException("transitions contains null", "transitions");
                }
                DynamicArtGapEdge edge = transition.Edge;
                context.ValidateProfile(edge.CallbackValidator);
                context.Identity.ObserveGap(edge);
                if (edge.MovieLogicalFrame < previousMovieFrame
                    || (edge.MovieLogicalFrame == previousMovieFrame
                        && edge.GapEdgeIndex <= previousGapIndex))
                {
                    throw new ArgumentException(
                        "gap cursor must be strictly increasing", "transitions");
                }
                previousMovieFrame = edge.MovieLogicalFrame;
                previousGapIndex = edge.GapEdgeIndex;
                string actualBefore = ComputeLedgerHash(ledger);
                if (!String.Equals(actualBefore, transition.BeforeLedgerHash,
                    StringComparison.Ordinal))
                {
                    throw new ArgumentException(
                        "before_ledger_hash does not match the pending ledger",
                        "transitions");
                }
                ApplyGapEdge(ledger, edge);
                ValidateDescriptors(
                    ledger, transition.AfterLedgerDescriptors,
                    "afterLedgerDescriptors");
            }
        }

        public static string ComputeLedgerHash(
            IList<DynamicArtTransferDescriptor> descriptors)
        {
            IList<DynamicArtTransferDescriptor> values = CopyDescriptors(
                descriptors, "descriptors");
            using (var payload = new MemoryStream())
            {
                payload.WriteByte((byte)'O');
                payload.WriteByte((byte)'D');
                payload.WriteByte((byte)'A');
                payload.WriteByte((byte)'L');
                payload.WriteByte(1);
                WriteInt32(payload, values.Count);
                for (int index = 0; index < values.Count; index++)
                {
                    WriteUtf8(payload, values[index].Fingerprint);
                }
                return Sha256(payload);
            }
        }

        internal static string ComputeFingerprint(DynamicArtTransferDescriptor descriptor)
        {
            using (var payload = new MemoryStream())
            {
                payload.WriteByte((byte)'O');
                payload.WriteByte((byte)'D');
                payload.WriteByte((byte)'A');
                payload.WriteByte((byte)'T');
                payload.WriteByte(1);
                WriteInt64(payload, descriptor.TransferId);
                WriteUtf8(payload, descriptor.Owner);
                WriteInt32(payload, descriptor.MappingFrame);
                payload.WriteByte((byte)descriptor.SubmissionOrigin);
                WriteInt32(payload, descriptor.Requests.Count);
                for (int index = 0; index < descriptor.Requests.Count; index++)
                {
                    DynamicArtRequest request = descriptor.Requests[index];
                    WriteInt32(payload, request.RomSourceAddress);
                    WriteInt32(payload, request.SourceTileIndex);
                    WriteInt32(payload, request.RamSourceAddress);
                    WriteInt32(payload, request.VramDestination);
                    WriteInt32(payload, request.ByteLength);
                }
                return Sha256(payload);
            }
        }

        internal static void ValidateOwner(string owner)
        {
            if (owner != "sonic" && owner != "tails" && owner != "tails-tails"
                && owner != "ss-sonic" && owner != "ss-tails"
                && owner != "ss-tails-tails")
            {
                throw new ArgumentException("unknown dynamic-art owner", "owner");
            }
        }

        internal static void ValidateEdgeValues(
            long edgeOrdinal,
            long transferId,
            DynamicArtTransferPhase phase,
            string owner,
            DynamicArtSubmissionOrigin submissionOrigin,
            int mappingFrame,
            DynamicArtCallbackValidator callbackValidator,
            int romCallbackPc,
            IList<DynamicArtRequest> requests)
        {
            if (edgeOrdinal < 0)
            {
                throw new ArgumentOutOfRangeException("edgeOrdinal");
            }
            ValidateTransferId(transferId);
            if (!Enum.IsDefined(typeof(DynamicArtTransferPhase), phase))
            {
                throw new ArgumentException("unknown lifecycle phase", "phase");
            }
            ValidateOwner(owner);
            ValidateOrigin(submissionOrigin, "submissionOrigin");
            ValidateNonnegative(mappingFrame, "mappingFrame");
            if (callbackValidator == null)
            {
                throw new ArgumentNullException("callbackValidator");
            }
            callbackValidator.ValidateCallback(phase, romCallbackPc);
            ReadOnlyCollection<DynamicArtRequest> frozen = FreezeRequests(
                requests, "requests");
            if (phase == DynamicArtTransferPhase.Submitted)
            {
                ValidateSubmissionRequests(frozen, "requests");
            }
            else
            {
                ValidateCompletionRequests(frozen, "requests",
                    callbackValidator.IsSonic1);
            }
        }

        internal static ReadOnlyCollection<DynamicArtRequest> FreezeRequests(
            IList<DynamicArtRequest> requests, string name)
        {
            if (requests == null) throw new ArgumentNullException(name);
            var copied = new List<DynamicArtRequest>();
            for (int index = 0; index < requests.Count; index++)
            {
                if (requests[index] == null)
                {
                    throw new ArgumentException(name + " contains null", name);
                }
                copied.Add(requests[index]);
            }
            if (copied.Count == 0)
            {
                throw new ArgumentException(name + " must not be empty", name);
            }
            return new ReadOnlyCollection<DynamicArtRequest>(copied);
        }

        internal static void ValidateSubmissionRequests(
            IList<DynamicArtRequest> requests, string name)
        {
            bool romBacked = requests[0].IsRomBacked;
            for (int index = 0; index < requests.Count; index++)
            {
                if (requests[index].IsRomBacked != romBacked)
                {
                    throw new ArgumentException(
                        name + " submissions must use one source domain", name);
                }
            }
        }

        private static void ValidateCompletionRequests(
            IList<DynamicArtRequest> requests, string name, bool sonic1)
        {
            bool ramBacked = requests[0].IsRamBacked;
            for (int index = 0; index < requests.Count; index++)
            {
                if (requests[index].IsRamBacked != ramBacked)
                {
                    throw new ArgumentException(
                        name + " completion requests must use one source domain",
                        name);
                }
            }
            if (sonic1 && ramBacked && requests.Count != 1)
            {
                throw new ArgumentException(
                    name + " RAM completion must have one physical request", name);
            }
        }

        private static void ApplySegmentEdge(
            IList<DynamicArtTransferDescriptor> ledger,
            DynamicArtTransferEdge edge)
        {
            if (edge.Phase == DynamicArtTransferPhase.Submitted)
            {
                AddSubmission(ledger, edge.SubmissionDescriptor(), "envelopes");
                return;
            }
            Complete(
                ledger, edge.TransferId, edge.Owner, edge.MappingFrame,
                edge.SubmissionOrigin, edge.CallbackValidator, edge.Requests,
                "completion without submission", "envelopes");
        }

        private static void ApplyGapEdge(
            IList<DynamicArtTransferDescriptor> ledger,
            DynamicArtGapEdge edge)
        {
            if (edge.Phase == DynamicArtTransferPhase.Submitted)
            {
                AddSubmission(ledger, edge.SubmissionDescriptor(), "transitions");
                return;
            }
            Complete(
                ledger, edge.TransferId, edge.Owner, edge.MappingFrame,
                edge.SubmissionOrigin, edge.CallbackValidator, edge.Requests,
                "completion without submission", "transitions");
        }

        private static void AddSubmission(
            IList<DynamicArtTransferDescriptor> ledger,
            DynamicArtTransferDescriptor descriptor,
            string name)
        {
            for (int index = 0; index < ledger.Count; index++)
            {
                if (ledger[index].TransferId == descriptor.TransferId)
                {
                    throw new ArgumentException(
                        "duplicate transfer_id " + descriptor.TransferId, name);
                }
            }
            ledger.Add(descriptor);
        }

        private static void Complete(
            IList<DynamicArtTransferDescriptor> ledger,
            long transferId,
            string owner,
            int mappingFrame,
            DynamicArtSubmissionOrigin origin,
            DynamicArtCallbackValidator callbackValidator,
            IList<DynamicArtRequest> completionRequests,
            string missingMessage,
            string name)
        {
            for (int index = 0; index < ledger.Count; index++)
            {
                DynamicArtTransferDescriptor descriptor = ledger[index];
                if (descriptor.TransferId != transferId) continue;
                if (descriptor.Owner != owner || descriptor.MappingFrame != mappingFrame
                    || descriptor.SubmissionOrigin != origin)
                {
                    throw new ArgumentException(
                        "completion does not match submission descriptor", name);
                }
                if (completionRequests[0].IsRamBacked)
                {
                    if (callbackValidator.IsSonic1)
                    {
                        callbackValidator.ValidateSonic1StagingCompletion(
                            completionRequests);
                    }
                    else if (!RequestsMatch(descriptor.Requests, completionRequests))
                    {
                        throw new ArgumentException(
                            "RAM completion requests do not match the submitted batch",
                            name);
                    }
                }
                else if (!RequestsMatch(descriptor.Requests, completionRequests))
                {
                    throw new ArgumentException(
                        "ROM completion requests do not match the submitted batch",
                        name);
                }
                ledger.RemoveAt(index);
                return;
            }
            throw new ArgumentException(missingMessage + " " + transferId, name);
        }

        private static void ValidateLedgerIds(
            IList<DynamicArtTransferDescriptor> ledger,
            IList<long> ids,
            string name)
        {
            if (ledger.Count != ids.Count)
            {
                throw new ArgumentException(name + " does not match pending ledger", name);
            }
            for (int index = 0; index < ledger.Count; index++)
            {
                if (ledger[index].TransferId != ids[index])
                {
                    throw new ArgumentException(
                        name + " does not match pending ledger", name);
                }
            }
        }

        private static bool RequestsMatch(
            IList<DynamicArtRequest> expected,
            IList<DynamicArtRequest> actual)
        {
            if (expected.Count != actual.Count) return false;
            for (int index = 0; index < expected.Count; index++)
            {
                DynamicArtRequest left = expected[index];
                DynamicArtRequest right = actual[index];
                if (left.RomSourceAddress != right.RomSourceAddress
                    || left.SourceTileIndex != right.SourceTileIndex
                    || left.RamSourceAddress != right.RamSourceAddress
                    || left.VramDestination != right.VramDestination
                    || left.ByteLength != right.ByteLength)
                {
                    return false;
                }
            }
            return true;
        }

        private static void ValidateDescriptors(
            IList<DynamicArtTransferDescriptor> ledger,
            IList<DynamicArtTransferDescriptor> actual,
            string name)
        {
            if (ledger.Count != actual.Count)
            {
                throw new ArgumentException(name + " does not match pending ledger", name);
            }
            for (int index = 0; index < ledger.Count; index++)
            {
                if (ledger[index].TransferId != actual[index].TransferId
                    || ledger[index].Fingerprint != actual[index].Fingerprint)
                {
                    throw new ArgumentException(
                        name + " does not match pending ledger", name);
                }
            }
        }

        private static List<DynamicArtTransferDescriptor> CopyDescriptors(
            IList<DynamicArtTransferDescriptor> values, string name)
        {
            if (values == null) throw new ArgumentNullException(name);
            var copied = new List<DynamicArtTransferDescriptor>();
            var ids = new HashSet<long>();
            for (int index = 0; index < values.Count; index++)
            {
                DynamicArtTransferDescriptor descriptor = values[index];
                if (descriptor == null)
                {
                    throw new ArgumentException(name + " contains null", name);
                }
                if (!ids.Add(descriptor.TransferId))
                {
                    throw new ArgumentException(
                        name + " has duplicate transfer_id", name);
                }
                copied.Add(descriptor);
            }
            return copied;
        }

        internal static ReadOnlyCollection<DynamicArtTransferEdge> FreezeEdges(
            IList<DynamicArtTransferEdge> edges, string name)
        {
            if (edges == null) throw new ArgumentNullException(name);
            var copied = new List<DynamicArtTransferEdge>();
            for (int index = 0; index < edges.Count; index++)
            {
                if (edges[index] == null)
                {
                    throw new ArgumentException(name + " contains null", name);
                }
                copied.Add(edges[index]);
            }
            return new ReadOnlyCollection<DynamicArtTransferEdge>(copied);
        }

        internal static ReadOnlyCollection<long> FreezeIds(
            IList<long> values, string name)
        {
            if (values == null) throw new ArgumentNullException(name);
            var copied = new List<long>();
            var seen = new HashSet<long>();
            for (int index = 0; index < values.Count; index++)
            {
                ValidateTransferId(values[index]);
                if (!seen.Add(values[index]))
                {
                    throw new ArgumentException(name + " has duplicate transfer_id", name);
                }
                copied.Add(values[index]);
            }
            return new ReadOnlyCollection<long>(copied);
        }

        internal static ReadOnlyCollection<DynamicArtTransferDescriptor>
            FreezeDescriptors(IList<DynamicArtTransferDescriptor> values, string name)
        {
            return new ReadOnlyCollection<DynamicArtTransferDescriptor>(
                CopyDescriptors(values, name));
        }

        internal static void AppendRequests(
            StringBuilder json, IList<DynamicArtRequest> requests)
        {
            for (int index = 0; index < requests.Count; index++)
            {
                if (index != 0) json.Append(",");
                requests[index].AppendJson(json);
            }
        }

        internal static void AppendIds(StringBuilder json, IList<long> ids)
        {
            for (int index = 0; index < ids.Count; index++)
            {
                if (index != 0) json.Append(",");
                json.Append(Dec(ids[index]));
            }
        }

        internal static string OriginWire(DynamicArtSubmissionOrigin origin)
        {
            return origin == DynamicArtSubmissionOrigin.Segment ? "segment" : "run_gap";
        }

        internal static string PhaseWire(DynamicArtTransferPhase phase)
        {
            return phase == DynamicArtTransferPhase.Submitted ? "submitted" : "completed";
        }

        internal static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        internal static string Dec(long value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        internal static void ValidateTransferId(long value)
        {
            if (value < 0) throw new ArgumentOutOfRangeException("transferId");
        }

        internal static void ValidateNonnegative(int value, string name)
        {
            if (value < 0) throw new ArgumentOutOfRangeException(name);
        }

        internal static void ValidateOrigin(
            DynamicArtSubmissionOrigin origin, string name)
        {
            if (!Enum.IsDefined(typeof(DynamicArtSubmissionOrigin), origin))
            {
                throw new ArgumentException("unknown submission origin", name);
            }
        }

        internal static void ValidateFingerprint(string value, string name)
        {
            if (value == null || value.Length != 71 || !value.StartsWith("sha256:",
                StringComparison.Ordinal))
            {
                throw new ArgumentException("must be a lowercase sha256 fingerprint", name);
            }
            for (int index = 7; index < value.Length; index++)
            {
                char character = value[index];
                if (!((character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f')))
                {
                    throw new ArgumentException(
                        "must be a lowercase sha256 fingerprint", name);
                }
            }
        }

        private static void WriteInt32(Stream stream, int value)
        {
            uint unsigned = unchecked((uint)value);
            stream.WriteByte((byte)(unsigned >> 24));
            stream.WriteByte((byte)(unsigned >> 16));
            stream.WriteByte((byte)(unsigned >> 8));
            stream.WriteByte((byte)unsigned);
        }

        private static void WriteInt64(Stream stream, long value)
        {
            ulong unsigned = unchecked((ulong)value);
            for (int shift = 56; shift >= 0; shift -= 8)
            {
                stream.WriteByte((byte)(unsigned >> shift));
            }
        }

        private static void WriteUtf8(Stream stream, string value)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(value);
            WriteInt32(stream, bytes.Length);
            stream.Write(bytes, 0, bytes.Length);
        }

        private static string Sha256(MemoryStream payload)
        {
            payload.Position = 0;
            using (SHA256 sha = SHA256.Create())
            {
                byte[] hash = sha.ComputeHash(payload);
                var result = new StringBuilder("sha256:", 71);
                for (int index = 0; index < hash.Length; index++)
                {
                    result.Append(hash[index].ToString("x2", CultureInfo.InvariantCulture));
                }
                return result.ToString();
            }
        }
    }
}
