using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Observes Sonic 2 player DPLC lifecycle facts at their ROM-owned
    /// boundaries. A QueueDMATransfer call is evidence only after its return
    /// advances the next command-buffer slot; a player transfer is evidence
    /// only when those accepted calls are enclosed by one verified player
    /// decision invocation.
    /// </summary>
    public sealed class S2DynamicArtObserver : IDisposable
    {
        private const int MainRamAddressBase = 0xFF0000;

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
        private Decision activeDecision;
        private DirectPilotLatch directPilotLatch;
        private DynamicArtRomProfile.DecisionWindow deferredObjectDecision;
        private bool deferredObjectDecisionInterrupted;
        private QueueAttempt activeQueueAttempt;
        private bool segmentArmed;
        private readonly HashSet<long> inheritedRunGapTransferIds =
            new HashSet<long>();
        private bool disposed;
        private long nextEdgeOrdinal;
        private long nextTransferId;

        public S2DynamicArtObserver(
            byte[] rom, IGpgxHost host, Func<int> logicalFrame)
        {
            if (rom == null) throw new ArgumentNullException("rom");
            if (host == null) throw new ArgumentNullException("host");
            if (logicalFrame == null) throw new ArgumentNullException("logicalFrame");
            registers = host as ICpuRegisterReader;
            if (registers == null)
            {
                throw new InvalidOperationException(
                    "S2 dynamic-art observation requires CPU register access.");
            }

            profile = DynamicArtRomProfile.Sonic2Rev01;
            ValidateOpcodeWindows(rom, profile);
            this.rom = rom;
            this.host = host;
            this.logicalFrame = logicalFrame;
            callbackValidator = new DynamicArtCallbackValidator(profile);

            var entries =
                new Dictionary<int, List<DynamicArtRomProfile.DecisionWindow>>();
            var returns = new Dictionary<int, bool>();
            var pilotCallerProbes =
                new Dictionary<int, DynamicArtRomProfile.DecisionWindow>();
            var mappingProbes =
                new Dictionary<int, DynamicArtRomProfile.DecisionWindow>();
            for (int index = 0; index < profile.DecisionWindows.Count; index++)
            {
                DynamicArtRomProfile.DecisionWindow window =
                    profile.DecisionWindows[index];
                List<DynamicArtRomProfile.DecisionWindow> entryWindows;
                if (!entries.TryGetValue(window.Entry, out entryWindows))
                {
                    entryWindows = new List<DynamicArtRomProfile.DecisionWindow>();
                    entries.Add(window.Entry, entryWindows);
                }
                entryWindows.Add(window);
                returns[window.ReturnAddress] = true;
                if (window.EntryKind
                    == DynamicArtRomProfile.DecisionEntryKind.DirectD0)
                {
                    pilotCallerProbes.Add(window.PilotCallerProbe, window);
                }
                if (window.EntryKind
                        == DynamicArtRomProfile.DecisionEntryKind.ObjectMapping
                    && window.MappingReadAddress != 0)
                {
                    mappingProbes.Add(window.MappingReadAddress, window);
                }
            }
            foreach (KeyValuePair<int, List<DynamicArtRomProfile.DecisionWindow>>
                entry in entries)
            {
                List<DynamicArtRomProfile.DecisionWindow> captured = entry.Value;
                if (captured.Count == 1)
                {
                    DynamicArtRomProfile.DecisionWindow window = captured[0];
                    registrations.Add(host.RegisterExecuteCallback((uint)entry.Key,
                        () =>
                        {
                            if (window.MappingReadAddress == 0)
                            {
                                OnDecisionEntry(window);
                            }
                            else
                            {
                                OnDeferredObjectDecisionEntry(window);
                            }
                        }));
                }
                else
                {
                    for (int index = 0; index < captured.Count; index++)
                    {
                        if (captured[index].EntryKind
                            != DynamicArtRomProfile.DecisionEntryKind.SpecialSharedRegisters)
                        {
                            throw new InvalidOperationException(
                                "S2 dynamic-art profile shares a non-special decision entry");
                        }
                    }
                    registrations.Add(host.RegisterExecuteCallback((uint)entry.Key,
                        () => OnSpecialSharedEntry(captured)));
                }
            }
            foreach (KeyValuePair<int, DynamicArtRomProfile.DecisionWindow>
                mappingProbe in mappingProbes)
            {
                DynamicArtRomProfile.DecisionWindow captured = mappingProbe.Value;
                registrations.Add(host.RegisterExecuteCallback((uint)mappingProbe.Key,
                    () => OnDeferredObjectMappingProbe(captured)));
            }
            foreach (int returnAddress in returns.Keys)
            {
                int capturedReturn = returnAddress;
                registrations.Add(host.RegisterExecuteCallback(
                    (uint)capturedReturn,
                    () => OnDecisionReturn(capturedReturn)));
            }
            foreach (KeyValuePair<int, DynamicArtRomProfile.DecisionWindow>
                pilotCallerProbe in pilotCallerProbes)
            {
                DynamicArtRomProfile.DecisionWindow captured =
                    pilotCallerProbe.Value;
                registrations.Add(host.RegisterExecuteCallback(
                    (uint)pilotCallerProbe.Key,
                    () => OnPilotCallerProbe(captured)));
            }
            registrations.Add(host.RegisterExecuteCallback(0x144E, OnQueueEntry));
            registrations.Add(host.RegisterExecuteCallback(
                (uint)profile.AcceptedDmaReturn, OnQueueReturn));
            registrations.Add(host.RegisterExecuteCallback(
                (uint)profile.VBlankCompletionSites[0], OnProcessDmaQueue));
        }

        public void ArmSegment()
        {
            ArmSegment(false);
        }

        public IList<DynamicArtTransferDescriptor> ArmRunSegment()
        {
            ArmSegment(true);
            return CopyLedger(ledger);
        }

        private void ArmSegment(bool allowRunGapCarry)
        {
            ThrowIfDisposed();
            if (segmentArmed)
            {
                throw new InvalidOperationException("dynamic-art segment is already armed");
            }
            if ((!allowRunGapCarry && ledger.Count != 0)
                || bufferedEdges.Count != 0
                || activeDecision != null || directPilotLatch != null
                || deferredObjectDecision != null || activeQueueAttempt != null)
            {
                throw new InvalidOperationException(
                    "cannot arm dynamic-art segment with a pending ledger");
            }
            inheritedRunGapTransferIds.Clear();
            for (int index = 0; index < ledger.Count; index++)
            {
                if (ledger[index].SubmissionOrigin
                    != DynamicArtSubmissionOrigin.RunGap)
                {
                    throw new InvalidOperationException(
                        "named-run segment carry must originate in run_gap");
                }
                inheritedRunGapTransferIds.Add(ledger[index].TransferId);
            }
            segmentArmed = true;
            Replace(publishedLedger, ledger);
            nextLogicalEdgeIndex.Clear();
        }

        public void MarkAdvanceBoundary(int gapLogicalFrame)
        {
            ThrowIfDisposed();
            RequireSegment();
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

        public bool TailsPilotDirectPart2Observed { get; private set; }

        public void EndSegment()
        {
            ThrowIfDisposed();
            if (!segmentArmed)
            {
                throw new InvalidOperationException("dynamic-art segment is not armed");
            }
            if ((bufferedEdges.Count != 0 && !boundaryGapReady)
                || activeDecision != null
                || directPilotLatch != null || deferredObjectDecision != null
                || activeQueueAttempt != null)
            {
                throw new InvalidOperationException(
                    "cannot end dynamic-art segment with unpublished callbacks");
            }
            segmentArmed = false;
            inheritedRunGapTransferIds.Clear();
            boundaryGapReady = false;
            ClearAdvanceBoundary();
        }

        public DynamicArtTransferEnvelope PublishRow(int publicationFrame, bool lagged)
        {
            ThrowIfDisposed();
            RequireSegment();
            if (lagged)
            {
                DynamicArtTransferEnvelope heartbeat =
                    new DynamicArtTransferEnvelope(
                    publicationFrame, new List<DynamicArtTransferEdge>(),
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

        public DynamicArtTransferEnvelope PublishTerminal(int publicationFrame)
        {
            ThrowIfDisposed();
            RequireSegment();
            List<DynamicArtTransferEdge> edges = BuildSegmentEdges(
                publicationFrame, true);
            Replace(publishedLedger, ledger);
            return new DynamicArtTransferEnvelope(
                publicationFrame, edges, TransferIds(publishedLedger));
        }

        public DynamicArtTransferEnvelope PublishBoundaryTerminal(
            int publicationFrame)
        {
            ThrowIfDisposed();
            RequireSegment();
            if (advanceBoundaryEdgeCount < 0)
            {
                throw new InvalidOperationException(
                    "cannot publish a dynamic-art boundary terminal without a marked advance");
            }
            if (activeDecision != null || directPilotLatch != null
                || deferredObjectDecision != null || activeQueueAttempt != null)
            {
                throw new InvalidOperationException(
                    "cannot close a dynamic-art boundary inside a callback decision");
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
                    edge, DynamicArtTransferState.ComputeLedgerHash(raw.BeforeLedger),
                    raw.AfterLedger));
                RecordPublishedPilotProof(raw);
            }
            bufferedEdges.Clear();
            Replace(publishedLedger, ledger);
            return transitions;
        }

        private void OnDecisionEntry(DynamicArtRomProfile.DecisionWindow window)
        {
            if (window.EntryKind
                == DynamicArtRomProfile.DecisionEntryKind.SpecialSharedRegisters)
            {
                throw new InvalidOperationException(
                    "S2 special shared decision must be registered as a context set");
            }
            if (window.EntryKind == DynamicArtRomProfile.DecisionEntryKind.DirectD0
                && activeDecision != null)
            {
                if (activeDecision.Window.EntryKind
                        != DynamicArtRomProfile.DecisionEntryKind.ObjectMapping
                    || activeDecision.Window.Owner != window.Owner)
                {
                    throw new InvalidOperationException(
                        "S2 direct Part2 callback did not continue its matching object decision");
                }
                return;
            }
            if (activeDecision != null || activeQueueAttempt != null
                || deferredObjectDecision != null)
            {
                throw new InvalidOperationException(
                    "S2 player-DPLC decision entered before the prior decision returned");
            }
            int mappingFrame;
            bool directPilotPart2 = false;
            if (window.EntryKind == DynamicArtRomProfile.DecisionEntryKind.DirectD0)
            {
                if (directPilotLatch == null
                    || directPilotLatch.Window.Entry != window.Entry)
                {
                    throw new InvalidOperationException(
                        "S2 direct Part2 entry requires its pinned pilot caller latch");
                }
                mappingFrame = directPilotLatch.MappingFrame;
                directPilotLatch = null;
                directPilotPart2 = true;
            }
            else
            {
                if (directPilotLatch != null)
                {
                    throw new InvalidOperationException(
                        "S2 pilot caller latch was not consumed by its direct Part2 entry");
                }
                int objectAddress = (int)(registers.ReadCpuRegister("M68K A0") & 0xFFFF);
                if (objectAddress < 0 || objectAddress + S2Ram.OffMappingFrame >= 0x10000)
                {
                    throw new InvalidOperationException(
                        "S2 player-DPLC object register points outside main RAM");
                }
                mappingFrame = host.ReadMainRamByte(
                    objectAddress + S2Ram.OffMappingFrame);
            }
            BeginDecision(window, mappingFrame, directPilotPart2);
        }

        private void OnDeferredObjectDecisionEntry(
            DynamicArtRomProfile.DecisionWindow window)
        {
            if (deferredObjectDecision != null
                && deferredObjectDecisionInterrupted
                && deferredObjectDecision == window
                && activeDecision == null && activeQueueAttempt == null
                && directPilotLatch == null)
            {
                deferredObjectDecision = null;
                deferredObjectDecisionInterrupted = false;
            }
            if (activeDecision != null || activeQueueAttempt != null
                || directPilotLatch != null || deferredObjectDecision != null)
            {
                throw new InvalidOperationException(
                    "S2 gated player-DPLC decision entered with an open dynamic-art scope");
            }
            deferredObjectDecision = window;
            deferredObjectDecisionInterrupted = false;
        }

        private void OnDeferredObjectMappingProbe(
            DynamicArtRomProfile.DecisionWindow window)
        {
            if (deferredObjectDecision != window)
            {
                throw new InvalidOperationException(
                    "S2 player-DPLC mapping callback did not follow its pinned decision entry");
            }
            deferredObjectDecision = null;
            deferredObjectDecisionInterrupted = false;
            OnDecisionEntry(window);
        }

        private void OnSpecialSharedEntry(
            IList<DynamicArtRomProfile.DecisionWindow> windows)
        {
            if (activeDecision != null || activeQueueAttempt != null
                || directPilotLatch != null)
            {
                throw new InvalidOperationException(
                    "S2 special shared decoder entered with an open dynamic-art scope");
            }
            int a4 = (int)(registers.ReadCpuRegister("M68K A4") & 0xFFFF);
            int d4 = (int)(registers.ReadCpuRegister("M68K D4") & 0xFFFF);
            int d1 = (int)(registers.ReadCpuRegister("M68K D1") & 0xFFFF);
            DynamicArtRomProfile.DecisionWindow matched = null;
            for (int index = 0; index < windows.Count; index++)
            {
                DynamicArtRomProfile.DecisionWindow candidate = windows[index];
                if (candidate.ExpectedA4 == a4 && candidate.ExpectedD4 == d4
                    && candidate.ExpectedD1 == d1)
                {
                    matched = candidate;
                    break;
                }
            }
            if (matched == null)
            {
                throw new InvalidOperationException(
                    "S2 special shared decoder register context is not pinned");
            }
            int objectAddress = (int)(registers.ReadCpuRegister("M68K A0") & 0xFFFF);
            if (objectAddress + S2Ram.OffMappingFrame >= 0x10000)
            {
                throw new InvalidOperationException(
                    "S2 special shared player register points outside main RAM");
            }
            int mappingFrame = host.ReadMainRamByte(
                objectAddress + S2Ram.OffMappingFrame);
            BeginDecision(matched, mappingFrame);
        }

        private void BeginDecision(DynamicArtRomProfile.DecisionWindow window,
            int mappingFrame, bool directPilotPart2 = false)
        {
            int previousMappingFrame = host.ReadMainRamByte(
                LastLoadedAddress(window.Owner));
            bool changed = mappingFrame != previousMappingFrame;
            activeDecision = new Decision
            {
                Window = window,
                MappingFrame = mappingFrame,
                ExpectedRequests = changed
                    ? DecodeExpectedRequests(window.Owner, mappingFrame)
                    : new List<ExpectedRequest>(),
                AcceptedRequests = new List<DynamicArtRequest>(),
                NextExpectedRequest = 0,
                DirectPilotPart2 = directPilotPart2
            };
        }

        private void OnPilotCallerProbe(
            DynamicArtRomProfile.DecisionWindow directWindow)
        {
            if (activeDecision != null || activeQueueAttempt != null
                || directPilotLatch != null)
            {
                throw new InvalidOperationException(
                    "S2 pilot caller reached direct Part2 with an open dynamic-art scope");
            }
            directPilotLatch = new DirectPilotLatch
            {
                Window = directWindow,
                MappingFrame = (int)(registers.ReadCpuRegister("M68K D0") & 0xFF)
            };
        }

        private void OnDecisionReturn(int returnAddress)
        {
            if (activeDecision == null && deferredObjectDecision != null)
            {
                if (deferredObjectDecision.ReturnAddress != returnAddress)
                {
                    throw new InvalidOperationException(
                        "S2 gated player-DPLC decision returned through the wrong callback");
                }
                deferredObjectDecision = null;
                deferredObjectDecisionInterrupted = false;
                return;
            }
            if (activeDecision == null)
            {
                throw new InvalidOperationException(
                    "S2 player-DPLC decision return observed without its entry callback at "
                    + returnAddress.ToString("X"));
            }
            if (activeQueueAttempt != null)
            {
                throw new InvalidOperationException(
                    "S2 player-DPLC decision returned while QueueDMATransfer was active");
            }
            Decision decision = activeDecision;
            activeDecision = null;
            if (decision.Window.ReturnAddress != returnAddress)
            {
                throw new InvalidOperationException(
                    "S2 player-DPLC decision returned through the wrong pinned callback");
            }
            if (decision.NextExpectedRequest != decision.ExpectedRequests.Count)
            {
                throw new InvalidOperationException(
                    "S2 player-DPLC decision did not reach every pinned DPLC queue call");
            }
            if (decision.AcceptedRequests.Count == 0) return;
            AddSubmission(decision.Window.Owner, decision.MappingFrame,
                decision.AcceptedRequests, returnAddress, decision.DirectPilotPart2);
        }

        private void OnQueueEntry()
        {
            if (activeQueueAttempt != null)
            {
                throw new InvalidOperationException(
                    "QueueDMATransfer entered before its prior return");
            }
            if (activeDecision == null) return;
            if (activeDecision.NextExpectedRequest >= activeDecision.ExpectedRequests.Count)
            {
                throw new InvalidOperationException(
                    "S2 player-DPLC decision attempted an unpinned DMA request");
            }
            activeQueueAttempt = new QueueAttempt
            {
                SourceAddress = NormalizeBusAddress(registers.ReadCpuRegister("M68K D1")),
                VramDestination = (int)(registers.ReadCpuRegister("M68K D2") & 0xFFFF),
                WordLength = (int)(registers.ReadCpuRegister("M68K D3") & 0xFFFF),
                SlotBefore = ReadU32(profile.Ram.DmaCommandBufferSlot)
            };
        }

        private void OnQueueReturn()
        {
            if (activeQueueAttempt == null) return;
            QueueAttempt attempt = activeQueueAttempt;
            activeQueueAttempt = null;
            ExpectedRequest expected = activeDecision.ExpectedRequests[
                activeDecision.NextExpectedRequest++];
            DynamicArtRequest acceptedRequest = VerifyQueueAttempt(expected, attempt);

            int slotAfter = ReadU32(profile.Ram.DmaCommandBufferSlot);
            if (slotAfter == attempt.SlotBefore)
            {
                return;
            }
            if (slotAfter != attempt.SlotBefore + profile.Ram.DmaCommandStrideBytes)
            {
                throw new InvalidOperationException(
                    "QueueDMATransfer returned without one verified next-slot advance");
            }
            activeDecision.AcceptedRequests.Add(acceptedRequest);
        }

        private void OnProcessDmaQueue()
        {
            // VBlank may abandon a gated decision before its mapping probe.
            // Keep that zero-work scope available for a matching resume, but
            // let only the identical pinned entry replace it on the next poll.
            if (deferredObjectDecision != null)
            {
                deferredObjectDecisionInterrupted = true;
            }
            if (activeQueueAttempt != null
                || (activeDecision != null
                    && activeDecision.AcceptedRequests.Count != 0))
            {
                throw new InvalidOperationException(
                    "ProcessDMAQueue overlapped accepted current-decision work");
            }
            while (ledger.Count != 0)
            {
                DynamicArtTransferDescriptor descriptor = ledger[0];
                List<DynamicArtTransferDescriptor> before = CopyLedger(ledger);
                ledger.RemoveAt(0);
                AddRawEdge(descriptor.TransferId, DynamicArtTransferPhase.Completed,
                    descriptor.Owner, descriptor.SubmissionOrigin,
                    descriptor.MappingFrame, descriptor.Requests,
                    profile.VBlankCompletionSites[0], before, false);
            }
        }

        private DynamicArtRequest VerifyQueueAttempt(
            ExpectedRequest expected, QueueAttempt attempt)
        {
            int byteLength = attempt.WordLength * 2;
            if (attempt.WordLength <= 0 || byteLength <= 0
                || attempt.VramDestination != expected.VramDestination
                || byteLength != expected.ByteLength)
            {
                throw new InvalidOperationException(
                    "QueueDMATransfer request does not match the active player DPLC run for "
                    + activeDecision.Window.Owner + " mapping "
                    + activeDecision.MappingFrame.ToString("X")
                    + ": expected destination "
                    + expected.VramDestination.ToString("X") + " length "
                    + expected.ByteLength.ToString("X") + ", observed destination "
                    + attempt.VramDestination.ToString("X") + " length "
                    + byteLength.ToString("X"));
            }
            if (expected.RomBacked)
            {
                if (attempt.SourceAddress != expected.SourceAddress)
                {
                    throw new InvalidOperationException(
                        "QueueDMATransfer ROM source does not match the active player DPLC run");
                }
                return new DynamicArtRequest(attempt.SourceAddress,
                    expected.SourceTileIndex, -1, attempt.VramDestination, byteLength);
            }
            if (attempt.SourceAddress < MainRamAddressBase)
            {
                throw new InvalidOperationException(
                    "special-stage player DPLC did not queue a main-RAM art source");
            }
            return new DynamicArtRequest(-1, -1, attempt.SourceAddress,
                attempt.VramDestination, byteLength);
        }

        private IList<ExpectedRequest> DecodeExpectedRequests(string owner, int mappingFrame)
        {
            if (owner == "sonic" || owner == "tails" || owner == "tails-tails")
            {
                DynamicArtRomProfile.DplcTable table = owner == "sonic"
                    ? profile.DplcTables[0] : profile.DplcTables[1];
                DynamicArtRomProfile.ArtSpan art = owner == "sonic"
                    ? profile.ArtSpans[0] : profile.ArtSpans[1];
                return DecodeDplc(table, mappingFrame, BankDestination(owner), art,
                    owner);
            }
            int specialFrame = mappingFrame;
            if (owner == "ss-tails") specialFrame += 0x12;
            if (owner == "ss-tails-tails")
            {
                return DecodeSingleSpecialDplc(profile.DplcTables[2],
                    specialFrame + 0x24, BankDestination(owner));
            }
            return DecodeDplc(profile.DplcTables[2], specialFrame,
                BankDestination(owner), null, owner);
        }

        private IList<ExpectedRequest> DecodeSingleSpecialDplc(
            DynamicArtRomProfile.DplcTable table, int mappingFrame,
            int destination)
        {
            int tableEntry = table.Address + (mappingFrame * 2);
            RequireRange(tableEntry, 2, "S2 Tails-tail DPLC table entry");
            int entryOffset = ReadU16(tableEntry);
            int entryAddress = table.Address + entryOffset;
            RequireRange(entryAddress, 2, "S2 Tails-tail DPLC run");
            int encoded = ReadU16(entryAddress);
            int byteLength = ((encoded >> 8) & 0xF0) + 0x10;
            byteLength *= 2;
            return new List<ExpectedRequest>
            {
                new ExpectedRequest
                {
                    RomBacked = false,
                    VramDestination = destination,
                    ByteLength = byteLength
                }
            };
        }

        private IList<ExpectedRequest> DecodeDplc(
            DynamicArtRomProfile.DplcTable table, int mappingFrame,
            int destination, DynamicArtRomProfile.ArtSpan art, string owner = null)
        {
            int tableEntry = table.Address + (mappingFrame * 2);
            RequireRange(tableEntry, 2, "S2 DPLC table entry");
            int entryOffset = ReadU16(tableEntry);
            int entryAddress = table.Address + entryOffset;
            RequireRange(entryAddress, 2, "S2 DPLC entry header");
            int runCount = ReadU16(entryAddress);
            RequireRange(entryAddress + 2, runCount * 2, "S2 DPLC runs");
            var requests = new List<ExpectedRequest>();
            for (int index = 0; index < runCount; index++)
            {
                int encoded = ReadU16(entryAddress + 2 + (index * 2));
                int byteLength = ((encoded >> 8) & 0xF0) + 0x10;
                byteLength *= 2;
                int sourceTile = encoded & 0x0FFF;
                if (destination < 0 || byteLength > 0x10000)
                {
                    throw new InvalidOperationException(
                        "S2 DPLC run exceeds the pinned destination bank"
                        + (owner == null ? "" : " for " + owner
                            + " mapping " + mappingFrame.ToString("X")
                            + " at " + entryAddress.ToString("X")
                            + " destination " + destination.ToString("X")
                            + " length " + byteLength.ToString("X")));
                }
                if (art != null)
                {
                    int sourceAddress = art.Address + (sourceTile * 0x20);
                    if (sourceAddress < art.Address
                        || sourceAddress + byteLength > art.Address + art.Length)
                    {
                        throw new InvalidOperationException(
                            "S2 DPLC run reads outside the pinned player art span");
                    }
                    requests.Add(new ExpectedRequest
                    {
                        RomBacked = true,
                        SourceAddress = sourceAddress,
                        SourceTileIndex = sourceTile,
                        VramDestination = destination & 0xFFFF,
                        ByteLength = byteLength
                    });
                }
                else
                {
                    requests.Add(new ExpectedRequest
                    {
                        RomBacked = false,
                        VramDestination = destination & 0xFFFF,
                        ByteLength = byteLength
                    });
                }
                destination = (destination + byteLength) & 0xFFFF;
            }
            return requests;
        }

        private void AddSubmission(string owner, int mappingFrame,
            IList<DynamicArtRequest> requests, int callbackPc, bool directPilotPart2)
        {
            DynamicArtSubmissionOrigin origin = segmentArmed
                ? DynamicArtSubmissionOrigin.Segment
                : DynamicArtSubmissionOrigin.RunGap;
            long transferId = nextTransferId++;
            var descriptor = new DynamicArtTransferDescriptor(
                transferId, owner, mappingFrame, origin, requests);
            List<DynamicArtTransferDescriptor> before = CopyLedger(ledger);
            ledger.Add(descriptor);
            AddRawEdge(transferId, DynamicArtTransferPhase.Submitted, owner,
                origin, mappingFrame, requests, callbackPc, before, directPilotPart2);
        }

        private void AddRawEdge(long transferId, DynamicArtTransferPhase phase,
            string owner, DynamicArtSubmissionOrigin origin, int mappingFrame,
            IList<DynamicArtRequest> requests, int callbackPc,
            IList<DynamicArtTransferDescriptor> before, bool directPilotPart2)
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
                AfterLedger = CopyLedger(ledger),
                TailsPilotDirectPart2 = directPilotPart2 && owner == "tails"
            });
        }

        private List<DynamicArtTransferEdge> BuildSegmentEdges(
            int publicationFrame, bool terminalForwarded)
        {
            var edges = new List<DynamicArtTransferEdge>();
            for (int index = 0; index < bufferedEdges.Count; index++)
            {
                RawEdge raw = bufferedEdges[index];
                if (raw.Origin != DynamicArtSubmissionOrigin.Segment
                    && !(raw.Phase == DynamicArtTransferPhase.Completed
                        && raw.Origin == DynamicArtSubmissionOrigin.RunGap
                        && inheritedRunGapTransferIds.Contains(raw.TransferId)))
                {
                    throw new InvalidOperationException(
                        "run-gap dynamic-art callback cannot be published in a segment");
                }
                edges.Add(new DynamicArtTransferEdge(raw.EdgeOrdinal,
                    raw.TransferId, raw.Phase, raw.Owner, raw.Origin,
                    raw.MappingFrame, raw.LogicalFrame, raw.LogicalEdgeIndex,
                    publicationFrame, terminalForwarded, callbackValidator,
                    raw.RomCallbackPc, raw.Requests));
                RecordPublishedPilotProof(raw);
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
                RecordPublishedPilotProof(raw);
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

        private void RecordPublishedPilotProof(RawEdge raw)
        {
            if (raw.TailsPilotDirectPart2)
            {
                TailsPilotDirectPart2Observed = true;
            }
        }

        private int LastLoadedAddress(string owner)
        {
            switch (owner)
            {
                case "sonic":
                case "ss-sonic": return profile.Ram.LastLoadedDplc;
                case "tails":
                case "ss-tails": return profile.Ram.TailsLastLoadedDplc;
                case "tails-tails":
                case "ss-tails-tails": return profile.Ram.TailsTailsLastLoadedDplc;
                default: throw new InvalidOperationException("unknown S2 DPLC owner");
            }
        }

        private int BankDestination(string owner)
        {
            for (int index = 0; index < profile.VramBanks.Count; index++)
            {
                if (profile.VramBanks[index].Owner == owner)
                {
                    return profile.VramBanks[index].Destination;
                }
            }
            throw new InvalidOperationException("missing pinned S2 DPLC destination bank");
        }

        private int ReadU16(int address)
        {
            return (rom[address] << 8) | rom[address + 1];
        }

        private int ReadU32(int address)
        {
            return (host.ReadMainRamByte(address) << 24)
                | (host.ReadMainRamByte(address + 1) << 16)
                | (host.ReadMainRamByte(address + 2) << 8)
                | host.ReadMainRamByte(address + 3);
        }

        private static int NormalizeBusAddress(uint address)
        {
            return (int)(address & 0x00FFFFFF);
        }

        private void RequireRange(int address, int length, string name)
        {
            if (address < 0 || length < 0 || address > rom.Length - length)
            {
                throw new InvalidOperationException(name + " is outside the supplied S2 ROM");
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
                        "S2 dynamic-art ROM window is outside the supplied ROM: "
                        + window.Name);
                }
                for (int byteIndex = 0; byteIndex < window.Bytes.Count; byteIndex++)
                {
                    if (rom[window.Address + byteIndex] != window.Bytes[byteIndex])
                    {
                        throw new InvalidOperationException(
                            "S2 dynamic-art ROM opcode window did not match: "
                            + window.Name);
                    }
                }
            }
        }

        private void RequireSegment()
        {
            if (!segmentArmed)
            {
                throw new InvalidOperationException(
                    "cannot publish a dynamic-art row outside an armed segment");
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
            for (int index = 0; index < source.Count; index++) copy.Add(source[index]);
            return copy;
        }

        private static void Replace(IList<DynamicArtTransferDescriptor> target,
            IList<DynamicArtTransferDescriptor> source)
        {
            target.Clear();
            for (int index = 0; index < source.Count; index++) target.Add(source[index]);
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
            if (disposed) throw new ObjectDisposedException("S2DynamicArtObserver");
        }

        private sealed class Decision
        {
            public DynamicArtRomProfile.DecisionWindow Window;
            public int MappingFrame;
            public IList<ExpectedRequest> ExpectedRequests;
            public IList<DynamicArtRequest> AcceptedRequests;
            public int NextExpectedRequest;
            public bool DirectPilotPart2;
        }

        private sealed class QueueAttempt
        {
            public int SourceAddress;
            public int VramDestination;
            public int WordLength;
            public int SlotBefore;
        }

        private sealed class DirectPilotLatch
        {
            public DynamicArtRomProfile.DecisionWindow Window;
            public int MappingFrame;
        }

        private sealed class ExpectedRequest
        {
            public bool RomBacked;
            public int SourceAddress;
            public int SourceTileIndex;
            public int VramDestination;
            public int ByteLength;
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
            public bool TailsPilotDirectPart2;
        }
    }
}
