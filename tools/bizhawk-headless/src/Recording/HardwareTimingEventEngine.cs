using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Mirrors the S3K direct and Kosinski-module FIFOs and emits each
    /// authoritative completion at its ROM-owned phase boundary. Submission
    /// identity is retained while active RAM aliases advance.
    /// </summary>
    public sealed class HardwareTimingEventEngine
    {
        public const uint ModuleChildSubmissionPc = 0x001B46;
        private const string ModuleKindName = "KOS_MODULE_QUEUE";
        private const string ModuleEventKind = "kos_module_queue";
        private const string ModuleCompressionVariant = "kosinski_moduled";
        private const string DirectKindName = "KOS_DECOMPRESSION_QUEUE";
        private const string DirectEventKind = "kos_decompression_queue";
        private const string DirectCompressionVariant = "kosinski";
        private const int MaxKosinskiOutput = 0x100000;
        private const uint TitleCardObjectCode = 0x0002D690;
        private const int TitleCardParentSlot = 8;
        private const int TitleCardWaitOffset = 0x48;

        private sealed class Submission
        {
            public long Ordinal;
            public uint Source;
            public ushort Destination;
            public string Fingerprint;
        }

        private sealed class KosModuleShape
        {
            public int CompressedLength;
            public int DestinationLength;
            public int ModuleCount;
        }

        private sealed class DirectSubmission
        {
            public long Ordinal;
            public uint Source;
            public uint Destination;
            public string Fingerprint;
            public StandardKosShape Shape;
            public bool ExactCallback;
            public bool UnclassifiedService;
            public int ServiceOpportunities;
            public int SubmissionRawFrame = int.MinValue;
            public string ParentFingerprint;
        }

        private sealed class DeferredDirectCompletion
        {
            public string Boundary;
            public long Ordinal;
            public string Fingerprint;
        }

        private sealed class StandardKosShape
        {
            public int CompressedLength;
            public int DestinationLength;
            public int LiteralCommands;
            public int ShortCopyCommands;
            public int LongCopyCommands;
            public int CopiedOutputLength;
        }

        private readonly byte[] rom;
        private readonly List<Submission> queue = new List<Submission>();
        private readonly List<DirectSubmission> directQueue =
            new List<DirectSubmission>();
        private long nextOrdinal;
        private long nextDirectOrdinal;
        private byte priorModulesLeft;
        private ushort? priorLevelFrameCounter;
        private byte? priorGameMode;
        private bool moduleResetClearPending;
        private bool titleCardLoadLoopActive;
        private bool priorDirectBusy;
        private int directEntryShift;
        private readonly List<DirectSubmission> stagedDirectRetirements =
            new List<DirectSubmission>();
        private readonly TextWriter measurementWriter;
        private readonly string measurementFixture;
        private readonly string measurementMovieSha256;
        private readonly string measurementRomSha1;
        private int measurementEpoch;
        private int priorMeasurementRawFrame = int.MinValue;
        private int measurementSequenceInFrame;

        public HardwareTimingEventEngine(byte[] rom)
            : this(rom, null, null, null, null)
        {
        }

        public HardwareTimingEventEngine(
            byte[] rom,
            TextWriter measurementWriter,
            string measurementFixture)
            : this(
                rom, measurementWriter, measurementFixture,
                new string('0', 64), new string('0', 40))
        {
        }

        public HardwareTimingEventEngine(
            byte[] rom,
            TextWriter measurementWriter,
            string measurementFixture,
            string movieSha256,
            string romSha1)
        {
            if (rom == null)
            {
                throw new ArgumentNullException("rom");
            }
            this.rom = rom;
            this.measurementWriter = measurementWriter;
            this.measurementFixture = measurementFixture;
            this.measurementMovieSha256 = movieSha256;
            this.measurementRomSha1 = romSha1;
        }

        public void Reset()
        {
            queue.Clear();
            directQueue.Clear();
            nextOrdinal = 0;
            nextDirectOrdinal = 0;
            priorModulesLeft = 0;
            priorLevelFrameCounter = null;
            priorGameMode = null;
            moduleResetClearPending = false;
            titleCardLoadLoopActive = false;
            priorDirectBusy = false;
            stagedDirectRetirements.Clear();
            if (measurementWriter != null)
            {
                measurementEpoch++;
            }
        }

        /// <summary>
        /// Observes the exact post-Queue_Kos module-child submission boundary.
        /// This callback proves one enqueue occurred. It may stage one
        /// intervening PRE head retirement, but completion ownership remains
        /// with the next frame-end reconciliation.
        /// </summary>
        public void ObserveDirectSubmissions(IGpgxHost host)
        {
            ObserveDirectSubmissions(int.MinValue, host);
        }

        public void ObserveDirectSubmissions(int rawFrame, IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            int physicalCount =
                S3KRam.U16(host, S3KRam.KosDecompQueueCount) & 0x7FFF;
            if (physicalCount < 1
                || physicalCount > S3KRam.KosDecompQueueCapacity)
            {
                throw new InvalidDataException(
                    "Post-Queue_Kos observation requires one to four"
                    + " occupied direct FIFO entries; observed "
                    + physicalCount + ".");
            }

            if (directQueue.Count == 0)
            {
                for (int index = 0; index < physicalCount; index++)
                {
                    directQueue.Add(CreateDirectSubmission(
                        host, index, index == physicalCount - 1,
                        index == physicalCount - 1 ? rawFrame : int.MinValue));
                }
                return;
            }

            int priorCount = directQueue.Count;
            if (physicalCount == priorCount + 1)
            {
                RequireDirectOverlap(
                    host,
                    physicalCount,
                    priorCount,
                    0,
                    "at the module-child submission callback");
                directQueue.Add(
                    CreateDirectSubmission(
                        host, physicalCount - 1, true, rawFrame));
                return;
            }

            if (priorCount != 0 && physicalCount == priorCount)
            {
                int retainedCount = priorCount - 1;
                RequireDirectOverlap(
                    host,
                    physicalCount,
                    retainedCount,
                    1,
                    "after one PRE head retirement at the"
                    + " module-child submission callback");
                stagedDirectRetirements.Add(directQueue[0]);
                directQueue.RemoveAt(0);
                directQueue.Add(
                    CreateDirectSubmission(
                        host, physicalCount - 1, true, rawFrame));
                return;
            }

            throw new InvalidDataException(
                "Post-Queue_Kos observation must represent exactly one"
                + " enqueue with no retirement or one PRE head retirement;"
                + " logical count was " + priorCount + " and physical count"
                + " is " + physicalCount + ".");
        }

        /// <summary>
        /// Observes one frame-end RAM sample. A changed Level_frame_counter
        /// proves that the main loop and its post-objects boundary ran.
        /// A duplicate counter normally proves only VInt admission. A
        /// canonical final-active module head shift independently proves
        /// Process_Kos_Module_Queue POST service. The in-level title-card
        /// loop is the other ROM-owned exception: loc_62CC
        /// executes Process_Sprites and Process_Kos_Module_Queue without
        /// incrementing Level_frame_counter, while its Obj_TitleCard parent
        /// remains in the SST. A null writer keeps the run-wide FIFO/ordinal
        /// ledger current without exporting an event into the current
        /// segment.
        /// </summary>
        public void ObserveFrameEnd(
            int rawFrame,
            IGpgxHost host,
            TextWriter writer)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }

            byte modulesLeft =
                S3KRam.U8(host, S3KRam.KosModulesLeft);
            int physicalCount = ReadModulePhysicalCount(host);
            ushort levelFrameCounter =
                S3KRam.U16(host, S3KRam.LevelFrameCounter);
            byte gameMode = S3KRam.U8(host, S3KRam.GameMode);
            bool titleCardLoopAdmitted =
                UpdateTitleCardLoadLoop(host, levelFrameCounter);
            string observedRowBoundary =
                priorLevelFrameCounter.HasValue
                    && priorLevelFrameCounter.Value == levelFrameCounter
                    && !titleCardLoopAdmitted
                ? "vint_service"
                : "post_objects";
            ModuleTransition moduleTransition = ClassifyModuleTransition(
                host, modulesLeft, physicalCount, gameMode);
            string moduleBoundary =
                moduleTransition == ModuleTransition.FinalHeadRetired
                    ? "post_objects" : observedRowBoundary;
            ObserveMeasurementService(
                rawFrame, observedRowBoundary != "vint_service");
            ushort directCountWord =
                S3KRam.U16(host, S3KRam.KosDecompQueueCount);
            List<DeferredDirectCompletion> deferredDirectCompletions = null;
            EmitStagedDirectRetirements(
                rawFrame, writer, ref deferredDirectCompletions);
            ObserveDirectQueue(
                rawFrame,
                host,
                writer,
                directCountWord & 0x7FFF,
                (directCountWord & 0x8000) != 0,
                observedRowBoundary != "vint_service",
                ref deferredDirectCompletions);

            // 0x81 is specifically "the final module is active in the
            // direct decoder". A fall from any other busy value is only a
            // per-module transition and is not an eligible completion.
            bool retired =
                moduleTransition == ModuleTransition.FinalHeadRetired;
            if (retired)
            {
                Submission completed = queue[0];
                queue.RemoveAt(0);
                if (writer != null)
                {
                    WriteCompletion(
                        writer,
                        rawFrame,
                        moduleBoundary,
                        ModuleEventKind,
                        completed.Ordinal,
                        completed.Fingerprint);
                }
            }
            WriteDeferredDirectCompletions(
                writer, rawFrame, deferredDirectCompletions);

            if (moduleTransition == ModuleTransition.QueueReset)
            {
                queue.Clear();
                moduleResetClearPending = false;
            }
            else if (moduleTransition
                    == ModuleTransition.QueueResetAndReconcile
                || moduleTransition
                    == ModuleTransition.QueueResetAndReconcilePending)
            {
                queue.Clear();
                ReconcileQueue(host, physicalCount);
                moduleResetClearPending = moduleTransition
                    == ModuleTransition.QueueResetAndReconcilePending;
            }
            else
            {
                ReconcileQueue(host, physicalCount);
            }
            priorModulesLeft = modulesLeft;
            priorLevelFrameCounter = levelFrameCounter;
            priorGameMode = gameMode;
        }

        private enum ModuleTransition
        {
            None,
            FinalHeadRetired,
            QueueReset,
            QueueResetAndReconcile,
            QueueResetAndReconcilePending
        }

        private ModuleTransition ClassifyModuleTransition(
            IGpgxHost host,
            byte modulesLeft,
            int physicalCount,
            byte gameMode)
        {
            bool modeChanged = priorGameMode.HasValue
                && priorGameMode.Value != gameMode;
            bool levelResetObservation = modeChanged
                && S3KRam.IsLevelFamilyMode(gameMode)
                && ((gameMode & 0x80) != 0
                    || (S3KRam.IsLevelFamilyMode(priorGameMode.Value)
                        && (priorGameMode.Value & 0x80) != 0));
            bool lostPhysicalHead = physicalCount < queue.Count;
            bool shiftedToNext = physicalCount != 0
                && queue.Count >= 2
                && ActiveEntryMatches(host, queue[1]);
            bool headChanged = lostPhysicalHead || shiftedToNext;
            if (levelResetObservation)
            {
                // Level clears the old Kos state before the first observable
                // $8C loading sample. Work first observed there, or after the
                // later $8C->$0C loading-bit exit, was queued after that clear
                // and keeps normal FIFO identity and ordinals.
                return physicalCount == 0
                    ? ModuleTransition.QueueReset
                    : ModuleTransition.QueueResetAndReconcile;
            }
            if (moduleResetClearPending && lostPhysicalHead)
            {
                // A FIFO first observed on a mode-transition sample can be
                // old-mode work submitted after the preceding observation.
                // Ledger it so the ordinal is consumed, but keep the reset
                // fence until its delayed physical clear is observed.
                return ModuleTransition.QueueReset;
            }
            if (modeChanged)
            {
                // Title_Screen and Level bulk-clear the complete queue RAM
                // during mode changes (sonic3k.asm:5391-5397,7507-7516).
                // Fence the logical mirror at the transition sample even if
                // the physical clear is not observable until the next sample;
                // otherwise that delayed clear can be misclassified later as
                // a Process_Kos_Module_Queue retirement.
                if (queue.Count == 0 && physicalCount != 0)
                {
                    // Outside Level's proven post-clear observation window,
                    // transition-visible work can still be predecessor-mode
                    // residue. Ledger it so its ordinal is consumed, but keep
                    // the fence pending through the later physical clear.
                    return ModuleTransition.QueueResetAndReconcilePending;
                }
                if (!headChanged || (modulesLeft == 0 && physicalCount == 0))
                {
                    return ModuleTransition.QueueReset;
                }
                throw new InvalidDataException(
                    "Kos module FIFO changed across a game-mode transition;");
            }

            if (queue.Count == 0 || !headChanged)
            {
                return ModuleTransition.None;
            }

            if (priorModulesLeft != 0x81)
            {
                return ModuleTransition.None;
            }
            int expectedCount = queue.Count - 1;
            bool retirementWithAppend = physicalCount == queue.Count
                && queue.Count >= 2
                && ActiveEntryMatches(host, queue[1])
                && ModuleQueueSlotMatchesActive(
                    host, 0, queue[1]);
            bool singleRetirementWithAppend = queue.Count == 1
                && physicalCount == 1
                && !ActiveEntryMatches(host, queue[0]);
            if (physicalCount != expectedCount
                && !retirementWithAppend
                && !singleRetirementWithAppend)
            {
                throw new InvalidDataException(
                    "Kos module final-head retirement changed FIFO"
                    + " cardinality from " + queue.Count + " to "
                    + physicalCount + "; expected " + expectedCount + ".");
            }
            if (physicalCount == 0)
            {
                if (modulesLeft != 0)
                {
                    throw new InvalidDataException(
                        "Kos module empty final-head retirement retained"
                        + " a nonzero module count.");
                }
                return ModuleTransition.FinalHeadRetired;
            }
            bool busyShiftedHead = retirementWithAppend
                || singleRetirementWithAppend;
            if (((modulesLeft & 0x80) != 0 && !busyShiftedHead)
                || (modulesLeft & 0x7F) == 0)
            {
                throw new InvalidDataException(
                    "Kos module shifted head was not canonically"
                    + " initialized after final-head retirement.");
            }
            if (!ActiveEntryMatches(host, queue[1]))
            {
                throw new InvalidDataException(
                    "Kos module final-head retirement did not install the"
                    + " mirrored next head.");
            }
            int shiftedExistingCount = Math.Min(
                physicalCount, queue.Count - 1);
            for (int index = 1; index < shiftedExistingCount; index++)
            {
                int entry = S3KRam.KosModuleQueue
                    + index * S3KRam.KosModuleQueueEntrySize;
                Submission expected = queue[index + 1];
                if (S3KRam.U32(host, entry) != expected.Source
                    || S3KRam.U16(host, entry + 4)
                        != expected.Destination)
                {
                    throw new InvalidDataException(
                        "Kos module final-head retirement did not preserve"
                        + " canonical shifted entry " + index + ".");
                }
            }
            return ModuleTransition.FinalHeadRetired;
        }

        private static bool ModuleQueueSlotMatchesActive(
            IGpgxHost host,
            int index,
            Submission submission)
        {
            int entry = S3KRam.KosModuleQueue
                + index * S3KRam.KosModuleQueueEntrySize;
            return S3KRam.U32(host, entry) == submission.Source + 2
                && S3KRam.U16(host, entry + 4)
                    == submission.Destination;
        }

        private static bool ActiveEntryMatches(
            IGpgxHost host, Submission submission)
        {
            return S3KRam.U32(host, S3KRam.KosModuleSource)
                    == submission.Source + 2
                && S3KRam.U16(host, S3KRam.KosModuleDestination)
                    == submission.Destination;
        }

        private void EmitStagedDirectRetirements(
            int rawFrame,
            TextWriter writer,
            ref List<DeferredDirectCompletion> deferredCompletions)
        {
            if (stagedDirectRetirements.Count == 0)
            {
                return;
            }

            // The staged head owned the prior busy-state sample. Its proven
            // PRE retirement ends that evidence before current physical RAM
            // is reconciled.
            priorDirectBusy = false;
            foreach (DirectSubmission completed in stagedDirectRetirements)
            {
                WriteMeasurement(completed, rawFrame, "pre_main_loop");
                if (writer != null)
                {
                    DeferDirectCompletion(
                        ref deferredCompletions,
                        "pre_main_loop",
                        completed.Ordinal,
                        completed.Fingerprint);
                }
            }
            stagedDirectRetirements.Clear();
        }

        private void ObserveDirectQueue(
            int rawFrame,
            IGpgxHost host,
            TextWriter writer,
            int physicalCount,
            bool busy,
            bool directServiceAdmitted,
            ref List<DeferredDirectCompletion> deferredCompletions)
        {
            directEntryShift = 0;
            if (HeadRetirementWriteInFlight(host, physicalCount, busy))
            {
                // Read the sample as the post-retirement state the ROM has
                // already committed everywhere except the count decrement
                // and the entry shift-up.
                directEntryShift = 1;
                physicalCount--;
            }
            try
            {
                ObserveDirectQueueCore(
                    rawFrame,
                    host,
                    writer,
                    physicalCount,
                    busy,
                    directServiceAdmitted,
                    ref deferredCompletions);
            }
            finally
            {
                directEntryShift = 0;
            }
        }

        private void ObserveDirectQueueCore(
            int rawFrame,
            IGpgxHost host,
            TextWriter writer,
            int physicalCount,
            bool busy,
            bool directServiceAdmitted,
            ref List<DeferredDirectCompletion> deferredCompletions)
        {
            if (physicalCount < 0
                || physicalCount > S3KRam.KosDecompQueueCapacity)
            {
                throw new InvalidDataException(
                    "Kosinski decompression FIFO count is outside its"
                    + " four-entry capacity: " + physicalCount + ".");
            }
            if (busy && physicalCount == 0)
            {
                throw new InvalidDataException(
                    "Kosinski decompression FIFO is busy with no occupied"
                    + " head.");
            }
            int priorCount = directQueue.Count;
            int overlap;
            int retiredCount;
            if (priorDirectBusy && !busy)
            {
                retiredCount = 1;
                overlap = priorCount - 1;
                RequireDirectOverlap(
                    host,
                    physicalCount,
                    overlap,
                    1,
                    "after busy head retirement");
            }
            else if (priorDirectBusy || busy)
            {
                retiredCount = 0;
                overlap = priorCount;
                RequireDirectOverlap(
                    host,
                    physicalCount,
                    overlap,
                    0,
                    priorDirectBusy
                        ? "while the prior head remains busy"
                        : "when the prior head starts busy service");
            }
            else
            {
                overlap = FindLongestDirectOverlap(host, physicalCount);
                retiredCount = priorCount - overlap;
            }
            if (retiredCount > 1)
            {
                throw new InvalidDataException(
                    "Kosinski decompression FIFO lost " + retiredCount
                    + " mirrored submissions between observable"
                    + " boundaries.");
            }
            if (retiredCount == 1
                && overlap == 0
                && physicalCount >= priorCount
                && !directServiceAdmitted)
            {
                throw new InvalidDataException(
                    "Kosinski decompression FIFO changed occupied slot zero"
                    + " without an admitted direct-service boundary.");
            }

            if (retiredCount == 1)
            {
                DirectSubmission completed = directQueue[0];
                directQueue.RemoveAt(0);
                WriteMeasurement(completed, rawFrame, "pre_main_loop");
                if (writer != null)
                {
                    DeferDirectCompletion(
                        ref deferredCompletions,
                        "pre_main_loop",
                        completed.Ordinal,
                        completed.Fingerprint);
                }
            }

            if (directQueue.Count != overlap)
            {
                throw new InvalidDataException(
                    "Kosinski decompression FIFO reconciliation retained an"
                    + " inconsistent canonical overlap.");
            }

            for (int index = overlap;
                index < physicalCount;
                index++)
            {
                directQueue.Add(CreateDirectSubmission(
                    host, index, false, int.MinValue));
            }
            priorDirectBusy = busy;
        }

        private static void DeferDirectCompletion(
            ref List<DeferredDirectCompletion> completions,
            string boundary,
            long ordinal,
            string fingerprint)
        {
            if (completions == null)
            {
                completions = new List<DeferredDirectCompletion>();
            }
            completions.Add(new DeferredDirectCompletion
            {
                Boundary = boundary,
                Ordinal = ordinal,
                Fingerprint = fingerprint
            });
        }

        private static void WriteDeferredDirectCompletions(
            TextWriter writer,
            int rawFrame,
            List<DeferredDirectCompletion> completions)
        {
            if (writer == null || completions == null)
            {
                return;
            }
            foreach (DeferredDirectCompletion completion in completions)
            {
                WriteCompletion(
                    writer,
                    rawFrame,
                    completion.Boundary,
                    DirectEventKind,
                    completion.Ordinal,
                    completion.Fingerprint);
            }
        }

        private void RequireDirectOverlap(
            IGpgxHost host,
            int physicalCount,
            int overlap,
            int priorStart,
            string context)
        {
            if (overlap < 0 || physicalCount < overlap)
            {
                throw new InvalidDataException(
                    "Kosinski decompression FIFO lost mirrored submissions "
                    + context + ".");
            }
            for (int index = 0; index < overlap; index++)
            {
                if (!DirectEntryMatches(
                    host,
                    index,
                    directQueue[priorStart + index]))
                {
                    throw new InvalidDataException(
                        "Kosinski decompression FIFO changed entry "
                        + index + " " + context + ".");
                }
            }
        }

        private int FindLongestDirectOverlap(
            IGpgxHost host,
            int physicalCount)
        {
            int maximum = Math.Min(directQueue.Count, physicalCount);
            for (int overlap = maximum; overlap > 0; overlap--)
            {
                int priorStart = directQueue.Count - overlap;
                bool matches = true;
                for (int index = 0; index < overlap; index++)
                {
                    if (!DirectEntryMatches(
                        host,
                        index,
                        directQueue[priorStart + index]))
                    {
                        matches = false;
                        break;
                    }
                }
                if (matches)
                {
                    return overlap;
                }
            }
            return 0;
        }

        /// <summary>
        /// Address of physical FIFO entry <paramref name="index"/> as seen
        /// after the ROM finishes the retirement write sequence in
        /// Process_Kos_Queue_EndReached. While that sequence is mid-flight
        /// the entries have not been shifted up yet, so logical entry n
        /// still lives in physical slot n+1.
        /// </summary>
        private int DirectEntryAddress(int index)
        {
            return S3KRam.KosDecompQueue
                + (index + directEntryShift)
                    * S3KRam.KosDecompQueueEntrySize;
        }

        /// <summary>
        /// Process_Kos_Queue_EndReached (sonic3k.asm) writes the finished
        /// stream's read cursor over the head entry
        /// (<c>move.l a0,(Kos_decomp_queue).w</c> /
        /// <c>move.l a1,(Kos_decomp_destination).w</c>), then clears the
        /// busy bit, then decrements Kos_decomp_queue_count, and only then
        /// shifts the remaining entries up. A frame-end RAM sample can land
        /// on any instruction boundary inside that sequence — measured at
        /// PC 0x001CE0, between the <c>andi.w #$7FFF</c> and the
        /// <c>subq.w #1</c>, in
        /// docs/BizHawk-2.11-linux-x64/Movies/s3k-sonic-tails-complete-emeralds.bk2.
        /// There the count still claims the retired head is occupied while
        /// slot zero already holds the decompressor's post-decode cursors,
        /// which are not the start of any archive. Recognising that state
        /// from the mirrored head's own ROM-derived shape lets the sample be
        /// read as the stable state the ROM is two instructions away from
        /// committing.
        /// </summary>
        private bool HeadRetirementWriteInFlight(
            IGpgxHost host, int physicalCount, bool busy)
        {
            if (busy || physicalCount < 1 || directQueue.Count < 1)
            {
                return false;
            }
            DirectSubmission head = directQueue[0];
            uint retiredSource = head.Source
                + (uint)head.Shape.CompressedLength;
            uint retiredDestination = head.Destination
                + (uint)head.Shape.DestinationLength;
            return S3KRam.U32(host, S3KRam.KosDecompQueue) == retiredSource
                && S3KRam.U32(host, S3KRam.KosDecompQueue + 4)
                    == retiredDestination;
        }

        private bool DirectEntryMatches(
            IGpgxHost host,
            int index,
            DirectSubmission submission)
        {
            int entry = DirectEntryAddress(index);
            return S3KRam.U32(host, entry) == submission.Source
                && S3KRam.U32(host, entry + 4)
                    == submission.Destination;
        }

        private DirectSubmission CreateDirectSubmission(
            IGpgxHost host,
            int index,
            bool exactCallback,
            int submissionRawFrame)
        {
            int entry = DirectEntryAddress(index);
            uint source = S3KRam.U32(host, entry);
            uint destination = S3KRam.U32(host, entry + 4);
            StandardKosShape shape = InspectStandardKos(source);
            long ordinal = nextDirectOrdinal++;
            return new DirectSubmission
            {
                Ordinal = ordinal,
                Source = source,
                Destination = destination,
                Shape = shape,
                ExactCallback = exactCallback,
                SubmissionRawFrame = submissionRawFrame,
                ParentFingerprint = exactCallback
                    ? ResolveActiveParentFingerprint(host) : null,
                Fingerprint = ComputeSubmissionFingerprint(
                    DirectKindName,
                    checked((int)source),
                    shape.CompressedLength,
                    unchecked((int)destination),
                    shape.DestinationLength,
                    DirectCompressionVariant,
                    1)
            };
        }

        private string ResolveActiveParentFingerprint(IGpgxHost host)
        {
            if (queue.Count != 0)
            {
                return queue[0].Fingerprint;
            }
            if (measurementWriter == null)
            {
                return null;
            }

            uint canonicalSource = CheckedActiveHeader(
                S3KRam.U32(host, S3KRam.KosModuleSource));
            ushort destination =
                S3KRam.U16(host, S3KRam.KosModuleDestination);
            KosModuleShape shape = InspectKosModule(canonicalSource);
            return ComputeSubmissionFingerprint(
                ModuleKindName,
                checked((int)canonicalSource),
                shape.CompressedLength,
                destination,
                shape.DestinationLength,
                ModuleCompressionVariant,
                shape.ModuleCount);
        }

        private StandardKosShape InspectStandardKos(uint canonicalSource)
        {
            int start = checked((int)canonicalSource);
            int position = start;
            int descriptor = 0;
            int descriptorBits = 0;
            int outputLength = 0;
            int literals = 0;
            int shortCopies = 0;
            int longCopies = 0;
            int copiedOutputLength = 0;

            while (true)
            {
                int first = PopDescriptorBit(
                    ref descriptor, ref descriptorBits, ref position);
                if (first != 0)
                {
                    RequireRomBytes(position, 1);
                    position++;
                    outputLength = CheckedOutputLength(outputLength, 1);
                    literals++;
                    continue;
                }

                int second = PopDescriptorBit(
                    ref descriptor, ref descriptorBits, ref position);
                int distance;
                int count;
                if (second != 0)
                {
                    RequireRomBytes(position, 2);
                    int lowByte = rom[position++];
                    int highByte = rom[position++];
                    distance = ((highByte & 0xF8) << 5) | lowByte;
                    distance = ((distance ^ 0x1FFF) + 1) & 0x1FFF;
                    count = highByte & 7;
                    if (count != 0)
                    {
                        count += 2;
                    }
                    else
                    {
                        RequireRomBytes(position, 1);
                        count = rom[position++] + 1;
                        if (count == 1)
                        {
                            return new StandardKosShape
                            {
                                CompressedLength = position - start,
                                DestinationLength = outputLength,
                                LiteralCommands = literals,
                                ShortCopyCommands = shortCopies,
                                LongCopyCommands = longCopies,
                                CopiedOutputLength = copiedOutputLength
                            };
                        }
                        if (count == 2)
                        {
                            continue;
                        }
                    }
                }
                else
                {
                    count = 2;
                    if (PopDescriptorBit(
                        ref descriptor, ref descriptorBits, ref position)
                        != 0)
                    {
                        count += 2;
                    }
                    if (PopDescriptorBit(
                        ref descriptor, ref descriptorBits, ref position)
                        != 0)
                    {
                        count++;
                    }
                    RequireRomBytes(position, 1);
                    distance = (rom[position++] ^ 0xFF) + 1;
                    distance &= 0xFF;
                }

                if (second != 0)
                {
                    longCopies++;
                }
                else
                {
                    shortCopies++;
                }
                copiedOutputLength = CheckedOutputLength(
                    copiedOutputLength, count);
                if (outputLength - distance < 0)
                {
                    throw new InvalidDataException(
                        "Kosinski backreference precedes output at ROM"
                        + " 0x" + position.ToString("X") + ".");
                }
                outputLength = CheckedOutputLength(outputLength, count);
            }
        }

        private static int CheckedOutputLength(int outputLength, int count)
        {
            if (count < 0 || outputLength > MaxKosinskiOutput - count)
            {
                throw new InvalidDataException(
                    "Kosinski decompression exceeds the maximum output"
                    + " length.");
            }
            return outputLength + count;
        }

        private void ObserveMeasurementService(
            int rawFrame, bool classified)
        {
            if (measurementWriter == null || directQueue.Count == 0)
            {
                return;
            }
            DirectSubmission head = directQueue[0];
            if (head.ExactCallback
                && head.SubmissionRawFrame == rawFrame)
            {
                return;
            }
            if (!classified)
            {
                head.UnclassifiedService = true;
                return;
            }
            head.ServiceOpportunities++;
        }

        private void WriteMeasurement(
            DirectSubmission submission,
            int rawFrame,
            string boundary)
        {
            if (measurementWriter == null)
            {
                return;
            }
            StandardKosShape shape = submission.Shape;
            measurementWriter.Write("{\"measurement_schema\":1,");
            measurementWriter.Write("\"recorder_version\":\"load-time-measurement-v1\",");
            measurementWriter.Write("\"fixture\":\"");
            WriteJsonString(measurementWriter, measurementFixture ?? "");
            measurementWriter.Write("\",\"movie_sha256\":\"");
            measurementWriter.Write(measurementMovieSha256);
            measurementWriter.Write("\",\"rom_sha1\":\"");
            measurementWriter.Write(measurementRomSha1);
            measurementWriter.Write("\",\"service_model\":\"s3k-kos-v1\",");
            measurementWriter.Write("\"epoch\":");
            measurementWriter.Write(measurementEpoch);
            measurementWriter.Write(",\"raw_frame\":");
            measurementWriter.Write(rawFrame);
            measurementWriter.Write(",\"sequence_in_frame\":");
            measurementWriter.Write(NextMeasurementSequence(rawFrame));
            measurementWriter.Write(",\"boundary\":\"");
            measurementWriter.Write(boundary);
            measurementWriter.Write("\",\"kind\":\"");
            measurementWriter.Write(DirectKindName);
            measurementWriter.Write("\",\"ordinal\":");
            measurementWriter.Write(submission.Ordinal);
            measurementWriter.Write(",\"fingerprint\":\"");
            measurementWriter.Write(submission.Fingerprint);
            measurementWriter.Write("\",\"parent_fingerprint\":");
            if (submission.ParentFingerprint == null)
            {
                measurementWriter.Write("null");
            }
            else
            {
                measurementWriter.Write('"');
                measurementWriter.Write(submission.ParentFingerprint);
                measurementWriter.Write('"');
            }
            measurementWriter.Write(",\"observation_precision\":\"");
            measurementWriter.Write(submission.ExactCallback
                ? "exact_callback" : "frame_end_censored");
            measurementWriter.Write("\",\"classified\":");
            measurementWriter.Write(
                submission.UnclassifiedService ? "false" : "true");
            measurementWriter.Write(",\"service_opportunities\":");
            measurementWriter.Write(submission.ServiceOpportunities);
            measurementWriter.Write(",\"source\":");
            measurementWriter.Write(submission.Source);
            measurementWriter.Write(",\"destination\":");
            measurementWriter.Write(submission.Destination);
            measurementWriter.Write(",\"compressed_length\":");
            measurementWriter.Write(shape.CompressedLength);
            measurementWriter.Write(",\"decompressed_length\":");
            measurementWriter.Write(shape.DestinationLength);
            measurementWriter.Write(",\"literal_commands\":");
            measurementWriter.Write(shape.LiteralCommands);
            measurementWriter.Write(",\"short_copy_commands\":");
            measurementWriter.Write(shape.ShortCopyCommands);
            measurementWriter.Write(",\"long_copy_commands\":");
            measurementWriter.Write(shape.LongCopyCommands);
            measurementWriter.Write(",\"copied_output_length\":");
            measurementWriter.Write(shape.CopiedOutputLength);
            measurementWriter.Write("}\n");
        }

        private int NextMeasurementSequence(int rawFrame)
        {
            if (priorMeasurementRawFrame != rawFrame)
            {
                priorMeasurementRawFrame = rawFrame;
                measurementSequenceInFrame = 0;
                return 0;
            }
            return ++measurementSequenceInFrame;
        }

        private static void WriteJsonString(TextWriter writer, string value)
        {
            foreach (char character in value)
            {
                switch (character)
                {
                    case '\\':
                        writer.Write("\\\\");
                        break;
                    case '"':
                        writer.Write("\\\"");
                        break;
                    case '\n':
                        writer.Write("\\n");
                        break;
                    case '\r':
                        writer.Write("\\r");
                        break;
                    case '\t':
                        writer.Write("\\t");
                        break;
                    default:
                        if (character < 0x20)
                        {
                            writer.Write("\\u");
                            writer.Write(((int)character).ToString("x4"));
                        }
                        else
                        {
                            writer.Write(character);
                        }
                        break;
                }
            }
        }

        private bool UpdateTitleCardLoadLoop(
            IGpgxHost host,
            ushort levelFrameCounter)
        {
            int parent = S3KRam.SlotAddress(TitleCardParentSlot);
            ushort parentWait =
                S3KRam.U16(host, parent + TitleCardWaitOffset);
            bool armNow = levelFrameCounter == 0
                && S3KRam.U32(host, parent) == TitleCardObjectCode
                && parentWait != 0;
            bool admitted = levelFrameCounter == 0
                && (titleCardLoadLoopActive || armNow);

            // loc_62CC exists only before gameplay starts. Arm from its
            // exact Obj_TitleCard parent state, then retain through the
            // tail using the two raw loop predicates. The final iteration
            // has already run Process_Sprites before both predicates clear,
            // so admitted is computed from lifecycle state on entry and the
            // clear applies to the following sample. A Nemesis job by itself
            // can never arm or reclassify an unrelated gameplay stall.
            if (levelFrameCounter != 0)
            {
                titleCardLoadLoopActive = false;
            }
            else if (!titleCardLoadLoopActive)
            {
                titleCardLoadLoopActive = armNow;
            }
            else if (parentWait == 0
                && S3KRam.U32(host, S3KRam.NemDecompQueue) == 0)
            {
                titleCardLoadLoopActive = false;
            }
            return admitted;
        }

        public static string ComputeSubmissionFingerprint(
            string kind,
            int source,
            int compressedLength,
            int destination,
            int destinationLength,
            string compressionVariant,
            int moduleCount)
        {
            if (kind == null)
            {
                throw new ArgumentNullException("kind");
            }
            if (compressionVariant == null)
            {
                throw new ArgumentNullException("compressionVariant");
            }

            using (var payload = new MemoryStream())
            {
                WriteLengthPrefixedUtf8(payload, kind);
                WriteInt32BigEndian(payload, source);
                WriteInt32BigEndian(payload, compressedLength);
                WriteInt32BigEndian(payload, destination);
                WriteInt32BigEndian(payload, destinationLength);
                WriteLengthPrefixedUtf8(payload, compressionVariant);
                WriteInt32BigEndian(payload, moduleCount);
                payload.Position = 0;
                using (SHA256 sha = SHA256.Create())
                {
                    byte[] digest = sha.ComputeHash(payload);
                    var result = new StringBuilder("sha256:", 71);
                    for (int i = 0; i < digest.Length; i++)
                    {
                        result.Append(digest[i].ToString("x2"));
                    }
                    return result.ToString();
                }
            }
        }

        private void ReconcileQueue(IGpgxHost host, int physicalCount)
        {
            int retained = Math.Min(queue.Count, physicalCount);
            for (int index = 1; index < retained; index++)
            {
                int entry = S3KRam.KosModuleQueue
                    + (index * S3KRam.KosModuleQueueEntrySize);
                uint source = S3KRam.U32(host, entry);
                ushort destination = S3KRam.U16(host, entry + 4);
                Submission existing = queue[index];
                if (source != existing.Source
                    || destination != existing.Destination)
                {
                    throw new InvalidDataException(
                        "Kos module FIFO changed without retiring its"
                        + " mirrored head at entry " + index + ".");
                }
            }

            if (physicalCount < queue.Count)
            {
                throw new InvalidDataException(
                    "Kos module FIFO lost " + (queue.Count - physicalCount)
                    + " mirrored submission(s) without an eligible"
                    + " final-module retirement.");
            }

            for (int index = queue.Count; index < physicalCount; index++)
            {
                int entry = S3KRam.KosModuleQueue
                    + (index * S3KRam.KosModuleQueueEntrySize);
                uint observedSource = S3KRam.U32(host, entry);
                uint canonicalSource = index == 0
                    ? CheckedActiveHeader(observedSource)
                    : observedSource;
                ushort destination = S3KRam.U16(host, entry + 4);
                queue.Add(CreateSubmission(canonicalSource, destination));
            }
        }

        private Submission CreateSubmission(
            uint canonicalSource,
            ushort destination)
        {
            KosModuleShape shape = InspectKosModule(canonicalSource);
            long ordinal = nextOrdinal++;
            return new Submission
            {
                Ordinal = ordinal,
                Source = canonicalSource,
                Destination = destination,
                Fingerprint = ComputeSubmissionFingerprint(
                    ModuleKindName,
                    checked((int)canonicalSource),
                    shape.CompressedLength,
                    destination,
                    shape.DestinationLength,
                    ModuleCompressionVariant,
                    shape.ModuleCount)
            };
        }

        private KosModuleShape InspectKosModule(uint canonicalSource)
        {
            int source = checked((int)canonicalSource);
            RequireRomBytes(source, 2);
            int destinationLength = (rom[source] << 8) | rom[source + 1];
            if (destinationLength == 0xA000)
            {
                destinationLength = 0x8000;
            }
            int moduleCount = (destinationLength + 0xFFF) / 0x1000;
            int position = source + 2;
            for (int module = 0; module < moduleCount; module++)
            {
                position = ScanKosinskiModule(position);
                if (module + 1 < moduleCount)
                {
                    int relative = position - (source + 2);
                    position += (16 - (relative & 15)) & 15;
                }
            }
            return new KosModuleShape
            {
                CompressedLength = position - source,
                DestinationLength = destinationLength,
                ModuleCount = moduleCount
            };
        }

        private int ScanKosinskiModule(int position)
        {
            int descriptor = 0;
            int descriptorBits = 0;
            while (true)
            {
                int first = PopDescriptorBit(
                    ref descriptor, ref descriptorBits, ref position);
                if (first != 0)
                {
                    RequireRomBytes(position, 1);
                    position++;
                    continue;
                }

                int second = PopDescriptorBit(
                    ref descriptor, ref descriptorBits, ref position);
                if (second == 0)
                {
                    // Short match: two more descriptor bits select the
                    // length, followed by a one-byte distance.
                    PopDescriptorBit(ref descriptor, ref descriptorBits,
                        ref position);
                    PopDescriptorBit(ref descriptor, ref descriptorBits,
                        ref position);
                    RequireRomBytes(position, 1);
                    position++;
                    continue;
                }

                RequireRomBytes(position, 2);
                int countByte = rom[position + 1] & 7;
                position += 2;
                if (countByte != 0)
                {
                    continue;
                }
                RequireRomBytes(position, 1);
                byte terminator = rom[position++];
                if (terminator == 0)
                {
                    return position;
                }
                // A value of one is the Kosinski no-output marker; values
                // above one are an extended match length.
            }
        }

        private int ReadModulePhysicalCount(IGpgxHost host)
        {
            int count = 0;
            for (int index = 0;
                index < S3KRam.KosModuleQueueCapacity;
                index++)
            {
                int entry = S3KRam.KosModuleQueue
                    + (index * S3KRam.KosModuleQueueEntrySize);
                if (S3KRam.U32(host, entry) == 0)
                {
                    break;
                }
                count++;
            }
            return count;
        }

        private int PopDescriptorBit(
            ref int descriptor,
            ref int descriptorBits,
            ref int position)
        {
            if (descriptorBits == 0)
            {
                RequireRomBytes(position, 2);
                descriptor = rom[position] | (rom[position + 1] << 8);
                position += 2;
                descriptorBits = 16;
            }
            int bit = descriptor & 1;
            descriptor >>= 1;
            descriptorBits--;
            if (descriptorBits == 0)
            {
                // Kos_Decomp_Loop / Kos_Decomp_Match reload d5 as soon as
                // dbf consumes descriptor bit 16, before the selected
                // command reads its literal or match bytes
                // (sonic3k.asm:2572-2600).
                RequireRomBytes(position, 2);
                descriptor = rom[position] | (rom[position + 1] << 8);
                position += 2;
                descriptorBits = 16;
            }
            return bit;
        }

        private static uint CheckedActiveHeader(uint observedSource)
        {
            if (observedSource < 2)
            {
                throw new InvalidDataException(
                    "Active Kos module source has no two-byte header.");
            }
            return observedSource - 2;
        }

        private void RequireRomBytes(int offset, int count)
        {
            if (offset < 0 || count < 0 || offset > rom.Length - count)
            {
                throw new InvalidDataException(
                    "Kos module descriptor reads outside the supplied ROM"
                    + " at 0x" + offset.ToString("X") + ".");
            }
        }

        private static void WriteCompletion(
            TextWriter writer,
            int rawFrame,
            string boundary,
            string eventKind,
            long ordinal,
            string fingerprint)
        {
            writer.Write("{\"event\":\"hardware_work_completed\",");
            writer.Write("\"raw_frame\":");
            writer.Write(rawFrame);
            writer.Write(",\"boundary\":\"");
            writer.Write(boundary);
            writer.Write("\",");
            writer.Write("\"kind\":\"");
            writer.Write(eventKind);
            writer.Write("\",\"ordinal\":");
            writer.Write(ordinal);
            writer.Write(",\"submission_fingerprint\":\"");
            writer.Write(fingerprint);
            writer.Write("\"}\n");
        }

        private static void WriteLengthPrefixedUtf8(
            Stream stream,
            string value)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(value);
            WriteInt32BigEndian(stream, bytes.Length);
            stream.Write(bytes, 0, bytes.Length);
        }

        private static void WriteInt32BigEndian(Stream stream, int value)
        {
            unchecked
            {
                stream.WriteByte((byte)(value >> 24));
                stream.WriteByte((byte)(value >> 16));
                stream.WriteByte((byte)(value >> 8));
                stream.WriteByte((byte)value);
            }
        }
    }

    /// <summary>Replays a whole S3K movie and writes only load-time diagnostics.</summary>
    public static class LoadTimeMeasurementCaptureRunner
    {
        public static void Capture(
            Bk2Movie movie,
            IGpgxHost host,
            byte[] rom,
            string fixture,
            string movieSha256,
            string romSha1,
            TextWriter output)
        {
            if (movie == null) throw new ArgumentNullException("movie");
            if (host == null) throw new ArgumentNullException("host");
            if (rom == null) throw new ArgumentNullException("rom");
            if (output == null) throw new ArgumentNullException("output");

            var engine = new HardwareTimingEventEngine(
                rom, output, fixture, movieSha256, romSha1);
            int rawFrame = 0;
            using (IDisposable observer = host.RegisterExecuteCallback(
                HardwareTimingEventEngine.ModuleChildSubmissionPc,
                () => engine.ObserveDirectSubmissions(rawFrame, host)))
            using (IEnumerator<Bk2Frame> frames =
                movie.OpenFrameStream().GetEnumerator())
            {
                while (frames.MoveNext())
                {
                    Bk2Frame frame = frames.Current;
                    if (frame.Power || frame.Reset)
                    {
                        engine.Reset();
                    }
                    ApplyFrame(frame, host);
                    host.Advance();
                    engine.ObserveFrameEnd(rawFrame, host, null);
                    rawFrame++;
                }
            }
        }

        private static void ApplyFrame(Bk2Frame frame, IGpgxHost host)
        {
            host.ClearButtons();
            Set(host, "Power", frame.Power);
            Set(host, "Reset", frame.Reset);
            Set(host, "P1 Up", frame.P1Up);
            Set(host, "P1 Down", frame.P1Down);
            Set(host, "P1 Left", frame.P1Left);
            Set(host, "P1 Right", frame.P1Right);
            Set(host, "P1 A", frame.P1A);
            Set(host, "P1 B", frame.P1B);
            Set(host, "P1 C", frame.P1C);
            Set(host, "P1 Start", frame.P1Start);
        }

        private static void Set(
            IGpgxHost host, string button, bool pressed)
        {
            if (pressed)
            {
                host.SetButton(button, true);
            }
        }
    }
}
