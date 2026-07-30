using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Native observer for Sonic 1's player dynamic-art lifecycle. Sonic_LoadGfx
    /// decodes ordered ROM runs into a fixed RAM staging buffer; the VBlank
    /// routines later move that whole buffer to VRAM. Those are deliberately
    /// separate lifecycle facts.
    /// </summary>
    public sealed class S1DynamicArtObserver : IDisposable
    {
        private readonly IGpgxHost host;
        private readonly ICpuRegisterReader registers;
        private readonly Func<int> logicalFrame;
        private readonly DynamicArtRomProfile.GameProfile profile;
        private readonly DynamicArtCallbackValidator callbackValidator;
        private readonly byte[] rom;
        private readonly List<IDisposable> registrations =
            new List<IDisposable>();
        private readonly List<DynamicArtTransferDescriptor> ledger =
            new List<DynamicArtTransferDescriptor>();
        private readonly List<DynamicArtTransferDescriptor> publishedLedger =
            new List<DynamicArtTransferDescriptor>();
        private readonly List<RawEdge> bufferedEdges = new List<RawEdge>();
        private int advanceBoundaryEdgeCount = -1;
        private int advanceBoundaryGapLogicalFrame;
        private List<DynamicArtTransferDescriptor> advanceBoundaryLedger;
        private bool boundaryGapReady;
        private readonly Dictionary<int, int> nextLogicalEdgeIndex =
            new Dictionary<int, int>();
        private readonly HashSet<int> armedVBlankCompletions =
            new HashSet<int>();
        private Decision activeDecision;
        private Preparation preparedTransfer;
        private bool segmentArmed;
        private bool disposed;
        private long nextEdgeOrdinal;
        private long nextTransferId;

        public S1DynamicArtObserver(
            byte[] rom,
            IGpgxHost host,
            Func<int> logicalFrame)
        {
            if (rom == null) throw new ArgumentNullException("rom");
            if (host == null) throw new ArgumentNullException("host");
            if (logicalFrame == null) throw new ArgumentNullException("logicalFrame");
            registers = host as ICpuRegisterReader;
            if (registers == null)
            {
                throw new InvalidOperationException(
                    "S1 dynamic-art observation requires CPU register access.");
            }
            profile = DynamicArtRomProfile.Sonic1Rev01;
            ValidateOpcodeWindows(rom, profile);
            this.rom = rom;
            this.host = host;
            this.logicalFrame = logicalFrame;
            callbackValidator = new DynamicArtCallbackValidator(profile);

            DynamicArtRomProfile.DecisionWindow decision =
                profile.DecisionWindows[0];
            registrations.Add(host.RegisterExecuteCallback(
                (uint)decision.Entry, OnDecisionEntry));
            registrations.Add(host.RegisterExecuteCallback(
                (uint)decision.ReturnAddress, OnDecisionReturn));
            for (int index = 0; index < profile.VBlankCompletionSites.Count; index++)
            {
                int completionPc = profile.VBlankCompletionSites[index];
                int probePc = Sonic1VBlankProbe(completionPc);
                registrations.Add(host.RegisterExecuteCallback(
                    (uint)probePc,
                    () => OnVBlankTransferProbe(completionPc)));
                registrations.Add(host.RegisterExecuteCallback(
                    (uint)completionPc,
                    () => OnVBlankCompletion(completionPc)));
            }
        }

        /// <summary>
        /// A segment has no trace-supplied initial ledger. Native work from a
        /// prior gap must have retired before a stored row can be armed.
        /// </summary>
        public void ArmSegment()
        {
            ThrowIfDisposed();
            if (segmentArmed)
            {
                throw new InvalidOperationException("dynamic-art segment is already armed");
            }
            if (bufferedEdges.Count != 0 || ledger.Count != 0)
            {
                throw new InvalidOperationException(
                    "cannot arm dynamic-art segment with a pending ledger");
            }
            segmentArmed = true;
            publishedLedger.Clear();
            nextLogicalEdgeIndex.Clear();
        }

        /// <summary>
        /// Snapshots the segment state immediately before one emulator
        /// advance. If that advance leaves the segment, only callbacks
        /// already present at this boundary may be terminal-forwarded.
        /// </summary>
        public void MarkAdvanceBoundary(int gapLogicalFrame)
        {
            ThrowIfDisposed();
            if (!segmentArmed)
            {
                throw new InvalidOperationException(
                    "cannot mark an advance boundary outside an armed segment");
            }
            if (advanceBoundaryEdgeCount >= 0)
            {
                throw new InvalidOperationException(
                    "dynamic-art advance boundary is already marked");
            }
            if (gapLogicalFrame < 0)
            {
                throw new ArgumentOutOfRangeException("gapLogicalFrame");
            }
            advanceBoundaryEdgeCount = bufferedEdges.Count;
            advanceBoundaryGapLogicalFrame = gapLogicalFrame;
            advanceBoundaryLedger = CopyLedger(ledger);
        }

        public void EndSegment()
        {
            ThrowIfDisposed();
            if (!segmentArmed)
            {
                throw new InvalidOperationException("dynamic-art segment is not armed");
            }
            if (bufferedEdges.Count != 0 && !boundaryGapReady)
            {
                throw new InvalidOperationException(
                    "cannot end dynamic-art segment with unpublished edges");
            }
            segmentArmed = false;
            boundaryGapReady = false;
            ClearAdvanceBoundary();
        }

        /// <summary>
        /// Emits the mandatory heartbeat for one stored row. A lag row keeps
        /// the last published ledger; callbacks become visible at the next
        /// non-lag row without changing their source-frame cursor.
        /// </summary>
        public DynamicArtTransferEnvelope PublishRow(int publicationFrame, bool lagged)
        {
            ThrowIfDisposed();
            if (!segmentArmed)
            {
                throw new InvalidOperationException(
                    "cannot publish a dynamic-art row outside an armed segment");
            }
            if (lagged)
            {
                DynamicArtTransferEnvelope heartbeat =
                    new DynamicArtTransferEnvelope(
                    publicationFrame,
                    new List<DynamicArtTransferEdge>(),
                    TransferIds(publishedLedger));
                ClearAdvanceBoundary();
                return heartbeat;
            }

            List<DynamicArtTransferEdge> edges = BuildSegmentEdges(
                publicationFrame, false);
            Replace(publishedLedger, ledger);
            ClearAdvanceBoundary();
            return new DynamicArtTransferEnvelope(
                publicationFrame, edges, TransferIds(publishedLedger));
        }

        /// <summary>
        /// Flushes only callbacks actually observed after the final normal row.
        /// Pending, uncompleted ROM work remains visible in the terminal ledger.
        /// </summary>
        public DynamicArtTransferEnvelope PublishTerminal(int publicationFrame)
        {
            ThrowIfDisposed();
            if (!segmentArmed)
            {
                throw new InvalidOperationException(
                    "cannot publish a dynamic-art terminal outside an armed segment");
            }
            List<DynamicArtTransferEdge> edges = BuildSegmentEdges(
                publicationFrame, true);
            Replace(publishedLedger, ledger);
            return new DynamicArtTransferEnvelope(
                publicationFrame, edges, TransferIds(publishedLedger));
        }

        /// <summary>
        /// Closes a segment on an advance that changed ownership. Callbacks
        /// present before the advance are terminal-forwarded; callbacks
        /// raised by the advance remain buffered for run-gap publication.
        /// </summary>
        public DynamicArtTransferEnvelope PublishBoundaryTerminal(
            int publicationFrame)
        {
            ThrowIfDisposed();
            if (!segmentArmed || advanceBoundaryEdgeCount < 0)
            {
                throw new InvalidOperationException(
                    "cannot publish a dynamic-art boundary terminal without a marked advance");
            }
            ReclassifyBoundaryCallbacksAsGap();
            List<DynamicArtTransferEdge> edges = BuildSegmentEdgePrefix(
                advanceBoundaryEdgeCount, publicationFrame);
            Replace(publishedLedger, advanceBoundaryLedger);
            boundaryGapReady = bufferedEdges.Count != 0;
            DynamicArtTransferEnvelope envelope =
                new DynamicArtTransferEnvelope(
                    publicationFrame, edges, TransferIds(publishedLedger));
            ClearAdvanceBoundary();
            return envelope;
        }

        /// <summary>
        /// Publishes callbacks observed while no segment owns a physics row.
        /// The caller writes these manifest-only transitions in callback order.
        /// </summary>
        public IList<DynamicArtGapTransition> PublishGap()
        {
            ThrowIfDisposed();
            if (segmentArmed)
            {
                throw new InvalidOperationException(
                    "cannot publish a dynamic-art gap while a segment is armed");
            }
            var transitions = new List<DynamicArtGapTransition>();
            var nextGapIndex = new Dictionary<int, int>();
            for (int index = 0; index < bufferedEdges.Count; index++)
            {
                RawEdge raw = bufferedEdges[index];
                if (raw.Phase == DynamicArtTransferPhase.Submitted
                    && raw.Origin != DynamicArtSubmissionOrigin.RunGap)
                {
                    throw new InvalidOperationException(
                        "segment dynamic-art callback cannot be published in a gap");
                }
                int gapIndex;
                if (!nextGapIndex.TryGetValue(raw.LogicalFrame, out gapIndex))
                {
                    gapIndex = 0;
                }
                nextGapIndex[raw.LogicalFrame] = gapIndex + 1;
                var edge = new DynamicArtGapEdge(
                    raw.EdgeOrdinal, raw.TransferId, raw.Phase, raw.Owner,
                    raw.Origin, raw.MappingFrame, raw.LogicalFrame, gapIndex,
                    callbackValidator, raw.RomCallbackPc, raw.Requests);
                transitions.Add(new DynamicArtGapTransition(
                    edge,
                    DynamicArtTransferState.ComputeLedgerHash(raw.BeforeLedger),
                    raw.AfterLedger));
            }
            bufferedEdges.Clear();
            Replace(publishedLedger, ledger);
            return transitions;
        }

        private void OnDecisionEntry()
        {
            if (activeDecision != null)
            {
                throw new InvalidOperationException(
                    "Sonic_LoadGfx decision entry re-entered before its return");
            }
            int objectAddress = (int)(registers.ReadCpuRegister("M68K A0") & 0xFFFF);
            if (objectAddress < 0 || objectAddress + S1Ram.OffMappingFrame >= 0x10000)
            {
                throw new InvalidOperationException(
                    "Sonic_LoadGfx object register points outside main RAM");
            }
            int mappingFrame = host.ReadMainRamByte(
                objectAddress + S1Ram.OffMappingFrame);
            int previousMappingFrame = host.ReadMainRamByte(
                profile.Ram.LastLoadedDplc);
            activeDecision = new Decision
            {
                MappingFrame = mappingFrame,
                Changed = mappingFrame != previousMappingFrame,
                Requests = mappingFrame == previousMappingFrame
                    ? null
                    : DecodeRequests(mappingFrame)
            };
        }

        private void OnDecisionReturn()
        {
            if (activeDecision == null)
            {
                throw new InvalidOperationException(
                    "Sonic_LoadGfx return observed without its entry callback");
            }
            Decision decision = activeDecision;
            activeDecision = null;
            if (!decision.Changed || decision.Requests.Count == 0)
            {
                return;
            }
            if (host.ReadMainRamByte(profile.Ram.PendingTransferFlag) == 0)
            {
                throw new InvalidOperationException(
                    "S1 Sonic_LoadGfx changed a nonempty DPLC without raising the staging flag");
            }
            preparedTransfer = new Preparation
            {
                Owner = "sonic",
                MappingFrame = decision.MappingFrame,
                Requests = decision.Requests
            };
        }

        private void OnVBlankTransferProbe(int completionPc)
        {
            if (host.ReadMainRamByte(profile.Ram.PendingTransferFlag) != 0)
            {
                if (preparedTransfer == null)
                {
                    throw new InvalidOperationException(
                        "S1 flagged VBlank probe has no prepared staging transfer");
                }
                AddSubmission(
                    preparedTransfer.Owner,
                    preparedTransfer.MappingFrame,
                    preparedTransfer.Requests,
                    Sonic1VBlankProbe(completionPc));
                preparedTransfer = null;
                armedVBlankCompletions.Add(completionPc);
            }
        }

        private void OnVBlankCompletion(int completionPc)
        {
            if (!armedVBlankCompletions.Remove(completionPc)) return;
            if (host.ReadMainRamByte(profile.Ram.PendingTransferFlag) != 0)
            {
                throw new InvalidOperationException(
                    "S1 post-DMA completion callback observed before the staging flag cleared");
            }
            if (ledger.Count != 1)
            {
                throw new InvalidOperationException(
                    "S1 VBlank staging transfer requires exactly one pending ledger entry; got "
                    + ledger.Count);
            }
            DynamicArtTransferDescriptor descriptor = ledger[0];
            var completion = new List<DynamicArtRequest>
            {
                new DynamicArtRequest(
                    -1, -1, profile.Ram.StagingBuffer,
                    profile.VramBanks[0].Destination,
                    profile.Ram.StagingBufferLength)
            };
            List<DynamicArtTransferDescriptor> before = CopyLedger(ledger);
            ledger.RemoveAt(0);
            AddRawEdge(
                descriptor.TransferId, DynamicArtTransferPhase.Completed,
                descriptor.Owner, descriptor.SubmissionOrigin,
                descriptor.MappingFrame, completion, completionPc, before);
        }

        private void AddSubmission(
            string owner,
            int mappingFrame,
            IList<DynamicArtRequest> requests,
            int callbackPc)
        {
            if (ledger.Count != 0)
            {
                throw new InvalidOperationException(
                    "S1 Sonic_LoadGfx raised a staging transfer while a prior transfer is pending");
            }
            DynamicArtSubmissionOrigin origin = segmentArmed
                ? DynamicArtSubmissionOrigin.Segment
                : DynamicArtSubmissionOrigin.RunGap;
            long transferId = nextTransferId++;
            var descriptor = new DynamicArtTransferDescriptor(
                transferId, owner, mappingFrame, origin, requests);
            List<DynamicArtTransferDescriptor> before = CopyLedger(ledger);
            ledger.Add(descriptor);
            AddRawEdge(
                transferId, DynamicArtTransferPhase.Submitted, owner, origin,
                mappingFrame, requests, callbackPc, before);
        }

        private void AddRawEdge(
            long transferId,
            DynamicArtTransferPhase phase,
            string owner,
            DynamicArtSubmissionOrigin origin,
            int mappingFrame,
            IList<DynamicArtRequest> requests,
            int callbackPc,
            IList<DynamicArtTransferDescriptor> before)
        {
            int sourceFrame = logicalFrame();
            if (sourceFrame < 0)
            {
                throw new InvalidOperationException(
                    "dynamic-art callback has a negative logical frame");
            }
            int logicalIndex;
            if (!nextLogicalEdgeIndex.TryGetValue(sourceFrame, out logicalIndex))
            {
                logicalIndex = 0;
            }
            nextLogicalEdgeIndex[sourceFrame] = logicalIndex + 1;
            bufferedEdges.Add(new RawEdge
            {
                EdgeOrdinal = nextEdgeOrdinal++,
                TransferId = transferId,
                Phase = phase,
                Owner = owner,
                Origin = origin,
                MappingFrame = mappingFrame,
                LogicalFrame = sourceFrame,
                LogicalEdgeIndex = logicalIndex,
                RomCallbackPc = callbackPc,
                Requests = new List<DynamicArtRequest>(requests),
                BeforeLedger = CopyLedger(before),
                AfterLedger = CopyLedger(ledger)
            });
        }

        private List<DynamicArtTransferEdge> BuildSegmentEdges(
            int publicationFrame, bool terminalForwarded)
        {
            var edges = new List<DynamicArtTransferEdge>();
            for (int index = 0; index < bufferedEdges.Count; index++)
            {
                RawEdge raw = bufferedEdges[index];
                if (raw.Origin != DynamicArtSubmissionOrigin.Segment)
                {
                    throw new InvalidOperationException(
                        "run-gap dynamic-art callback cannot be published in a segment");
                }
                edges.Add(new DynamicArtTransferEdge(
                    raw.EdgeOrdinal, raw.TransferId, raw.Phase, raw.Owner,
                    raw.Origin, raw.MappingFrame, raw.LogicalFrame,
                    raw.LogicalEdgeIndex, publicationFrame, terminalForwarded,
                    callbackValidator, raw.RomCallbackPc, raw.Requests));
            }
            bufferedEdges.Clear();
            return edges;
        }

        private List<DynamicArtTransferEdge> BuildSegmentEdgePrefix(
            int count, int publicationFrame)
        {
            var edges = new List<DynamicArtTransferEdge>();
            for (int index = 0; index < count; index++)
            {
                RawEdge raw = bufferedEdges[index];
                if (raw.Origin != DynamicArtSubmissionOrigin.Segment)
                {
                    throw new InvalidOperationException(
                        "run-gap dynamic-art callback cannot be terminal-forwarded");
                }
                edges.Add(new DynamicArtTransferEdge(
                    raw.EdgeOrdinal, raw.TransferId, raw.Phase, raw.Owner,
                    raw.Origin, raw.MappingFrame, raw.LogicalFrame,
                    raw.LogicalEdgeIndex, publicationFrame, true,
                    callbackValidator, raw.RomCallbackPc, raw.Requests));
            }
            bufferedEdges.RemoveRange(0, count);
            return edges;
        }

        private void ReclassifyBoundaryCallbacksAsGap()
        {
            var boundarySubmissions = new HashSet<long>();
            for (int index = advanceBoundaryEdgeCount;
                index < bufferedEdges.Count; index++)
            {
                RawEdge raw = bufferedEdges[index];
                raw.LogicalFrame = advanceBoundaryGapLogicalFrame;
                if (raw.Phase == DynamicArtTransferPhase.Submitted)
                {
                    boundarySubmissions.Add(raw.TransferId);
                }
            }
            if (boundarySubmissions.Count == 0)
            {
                return;
            }
            for (int index = advanceBoundaryEdgeCount;
                index < bufferedEdges.Count; index++)
            {
                RawEdge raw = bufferedEdges[index];
                if (boundarySubmissions.Contains(raw.TransferId))
                {
                    raw.Origin = DynamicArtSubmissionOrigin.RunGap;
                }
                raw.BeforeLedger = ReclassifyDescriptors(
                    raw.BeforeLedger, boundarySubmissions);
                raw.AfterLedger = ReclassifyDescriptors(
                    raw.AfterLedger, boundarySubmissions);
            }
            Replace(ledger, ReclassifyDescriptors(
                ledger, boundarySubmissions));
        }

        private static List<DynamicArtTransferDescriptor>
            ReclassifyDescriptors(
                IList<DynamicArtTransferDescriptor> source,
                ISet<long> transferIds)
        {
            var rewritten = new List<DynamicArtTransferDescriptor>();
            for (int index = 0; index < source.Count; index++)
            {
                DynamicArtTransferDescriptor descriptor = source[index];
                rewritten.Add(transferIds.Contains(descriptor.TransferId)
                    ? new DynamicArtTransferDescriptor(
                        descriptor.TransferId,
                        descriptor.Owner,
                        descriptor.MappingFrame,
                        DynamicArtSubmissionOrigin.RunGap,
                        descriptor.Requests)
                    : descriptor);
            }
            return rewritten;
        }

        private void ClearAdvanceBoundary()
        {
            advanceBoundaryEdgeCount = -1;
            advanceBoundaryLedger = null;
        }

        private IList<DynamicArtRequest> DecodeRequests(int mappingFrame)
        {
            DynamicArtRomProfile.DplcTable table = profile.DplcTables[0];
            int tableEntry = table.Address + (mappingFrame * 2);
            RequireRange(tableEntry, 2, "S1 DPLC table entry");
            int entryOffset = ReadU16(tableEntry);
            int entryAddress = table.Address + entryOffset;
            RequireRange(entryAddress, 1, "S1 DPLC entry header");
            int runCount = rom[entryAddress];
            RequireRange(entryAddress + 1, runCount * 2, "S1 DPLC runs");
            var requests = new List<DynamicArtRequest>();
            int destination = profile.VramBanks[0].Destination;
            DynamicArtRomProfile.ArtSpan art = profile.ArtSpans[0];
            for (int index = 0; index < runCount; index++)
            {
                int encoded = ReadU16(entryAddress + 1 + (index * 2));
                int tileCount = (encoded >> 12) + 1;
                int sourceTile = encoded & 0x0FFF;
                int length = tileCount * 0x20;
                int sourceAddress = art.Address + (sourceTile * 0x20);
                if (sourceAddress < art.Address
                    || sourceAddress + length > art.Address + art.Length)
                {
                    throw new InvalidOperationException(
                        "S1 DPLC run reads outside the pinned Sonic art span");
                }
                if (destination + length
                    > profile.VramBanks[0].Destination + profile.Ram.StagingBufferLength)
                {
                    throw new InvalidOperationException(
                        "S1 DPLC runs exceed the pinned staging buffer length");
                }
                requests.Add(new DynamicArtRequest(
                    sourceAddress, sourceTile, -1, destination, length));
                destination += length;
            }
            return requests;
        }

        private int ReadU16(int address)
        {
            return (rom[address] << 8) | rom[address + 1];
        }

        private static int Sonic1VBlankProbe(int completionPc)
        {
            switch (completionPc)
            {
                case 0x0D50: return 0x0D20;
                case 0x0E64: return 0x0E34;
                case 0x0F54: return 0x0F24;
                case 0x1060: return 0x1030;
                default:
                    throw new InvalidOperationException(
                        "unknown pinned S1 VBlank completion callback "
                        + completionPc);
            }
        }

        private void RequireRange(int address, int length, string name)
        {
            if (address < 0 || length < 0 || address > rom.Length - length)
            {
                throw new InvalidOperationException(
                    name + " is outside the supplied S1 ROM");
            }
        }

        private static void ValidateOpcodeWindows(
            byte[] rom, DynamicArtRomProfile.GameProfile profile)
        {
            for (int windowIndex = 0; windowIndex < profile.OpcodeWindows.Count;
                windowIndex++)
            {
                DynamicArtRomProfile.OpcodeWindow window =
                    profile.OpcodeWindows[windowIndex];
                if (window.Address < 0
                    || window.Address > rom.Length - window.Bytes.Count)
                {
                    throw new InvalidOperationException(
                        "S1 dynamic-art ROM window is outside the supplied ROM: "
                        + window.Name);
                }
                for (int byteIndex = 0; byteIndex < window.Bytes.Count; byteIndex++)
                {
                    if (rom[window.Address + byteIndex] != window.Bytes[byteIndex])
                    {
                        throw new InvalidOperationException(
                            "S1 dynamic-art ROM opcode window did not match: "
                            + window.Name);
                    }
                }
            }
        }

        private static IList<long> TransferIds(
            IList<DynamicArtTransferDescriptor> descriptors)
        {
            var ids = new List<long>();
            for (int index = 0; index < descriptors.Count; index++)
            {
                ids.Add(descriptors[index].TransferId);
            }
            return ids;
        }

        private static List<DynamicArtTransferDescriptor> CopyLedger(
            IList<DynamicArtTransferDescriptor> source)
        {
            var copy = new List<DynamicArtTransferDescriptor>();
            for (int index = 0; index < source.Count; index++)
            {
                copy.Add(source[index]);
            }
            return copy;
        }

        private static void Replace(
            IList<DynamicArtTransferDescriptor> target,
            IList<DynamicArtTransferDescriptor> source)
        {
            target.Clear();
            for (int index = 0; index < source.Count; index++)
            {
                target.Add(source[index]);
            }
        }

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            for (int index = registrations.Count - 1; index >= 0; index--)
            {
                registrations[index].Dispose();
            }
            registrations.Clear();
        }

        private void ThrowIfDisposed()
        {
            if (disposed) throw new ObjectDisposedException("S1DynamicArtObserver");
        }

        private sealed class Decision
        {
            public int MappingFrame;
            public bool Changed;
            public IList<DynamicArtRequest> Requests;
        }

        private sealed class Preparation
        {
            public string Owner;
            public int MappingFrame;
            public IList<DynamicArtRequest> Requests;
        }

        private sealed class RawEdge
        {
            public long EdgeOrdinal;
            public long TransferId;
            public DynamicArtTransferPhase Phase;
            public string Owner;
            public DynamicArtSubmissionOrigin Origin;
            public int MappingFrame;
            public int LogicalFrame;
            public int LogicalEdgeIndex;
            public int RomCallbackPc;
            public IList<DynamicArtRequest> Requests;
            public IList<DynamicArtTransferDescriptor> BeforeLedger;
            public IList<DynamicArtTransferDescriptor> AfterLedger;
        }
    }
}
