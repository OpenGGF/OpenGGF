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
        public const int LegacySchema = 1;
        public const int CurrentSchema = 2;
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
        }

        private sealed class StandardKosShape
        {
            public int CompressedLength;
            public int DestinationLength;
        }

        private readonly byte[] rom;
        private readonly List<Submission> queue = new List<Submission>();
        private readonly List<DirectSubmission> directQueue =
            new List<DirectSubmission>();
        private long nextOrdinal;
        private long nextDirectOrdinal;
        private byte priorModulesLeft;
        private ushort? priorLevelFrameCounter;
        private bool titleCardLoadLoopActive;
        private bool priorDirectBusy;
        private int stagedDirectRetirements;
        private readonly int hardwareTimingSchema;

        public HardwareTimingEventEngine(byte[] rom)
            : this(rom, CurrentSchema)
        {
        }

        public HardwareTimingEventEngine(byte[] rom, int hardwareTimingSchema)
        {
            if (rom == null)
            {
                throw new ArgumentNullException("rom");
            }
            if (hardwareTimingSchema != LegacySchema
                && hardwareTimingSchema != CurrentSchema)
            {
                throw new ArgumentOutOfRangeException(
                    "hardwareTimingSchema");
            }
            this.rom = rom;
            this.hardwareTimingSchema = hardwareTimingSchema;
        }

        public void Reset()
        {
            queue.Clear();
            directQueue.Clear();
            nextOrdinal = 0;
            nextDirectOrdinal = 0;
            priorModulesLeft = 0;
            priorLevelFrameCounter = null;
            titleCardLoadLoopActive = false;
            priorDirectBusy = false;
            stagedDirectRetirements = 0;
        }

        /// <summary>
        /// Observes the exact post-Queue_Kos module-child submission boundary.
        /// This callback proves one enqueue occurred. It may stage one
        /// intervening PRE head retirement, but completion ownership remains
        /// with the next frame-end reconciliation.
        /// </summary>
        public void ObserveDirectSubmissions(IGpgxHost host)
        {
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            if (stagedDirectRetirements != 0)
            {
                throw new InvalidDataException(
                    "Kosinski decompression FIFO received another module"
                    + " submission callback before its staged PRE retirement"
                    + " reached frame-end reconciliation.");
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
                    directQueue.Add(CreateDirectSubmission(host, index));
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
                    CreateDirectSubmission(host, physicalCount - 1));
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
                stagedDirectRetirements = 1;
                directQueue.Add(
                    CreateDirectSubmission(host, physicalCount - 1));
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
        /// A duplicate counter normally proves only VInt admission. The
        /// in-level title-card loop is the ROM-owned exception: loc_62CC
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
            bool titleCardLoopAdmitted =
                UpdateTitleCardLoadLoop(host, levelFrameCounter);
            string boundary =
                priorLevelFrameCounter.HasValue
                    && priorLevelFrameCounter.Value == levelFrameCounter
                    && !titleCardLoopAdmitted
                ? "vint_service"
                : "post_objects";
            ushort directCountWord =
                S3KRam.U16(host, S3KRam.KosDecompQueueCount);
            EmitStagedDirectRetirements(rawFrame, writer);
            ObserveDirectQueue(
                rawFrame,
                host,
                writer,
                directCountWord & 0x7FFF,
                (directCountWord & 0x8000) != 0,
                boundary != "vint_service");

            // 0x81 is specifically "the final module is active in the
            // direct decoder". A fall from any other busy value is only a
            // per-module transition and is not an eligible completion.
            bool retired = priorModulesLeft == 0x81
                && queue.Count != 0
                && HeadRetired(host, modulesLeft, physicalCount);
            if (retired)
            {
                Submission completed = queue[0];
                queue.RemoveAt(0);
                if (writer != null)
                {
                    WriteCompletion(
                        writer,
                        rawFrame,
                        boundary,
                        ModuleEventKind,
                        completed.Ordinal,
                        completed.Fingerprint);
                }
            }

            ReconcileQueue(host, physicalCount);
            priorModulesLeft = modulesLeft;
            priorLevelFrameCounter = levelFrameCounter;
        }

        private void EmitStagedDirectRetirements(
            int rawFrame, TextWriter writer)
        {
            if (stagedDirectRetirements == 0)
            {
                return;
            }
            if (stagedDirectRetirements != 1 || directQueue.Count == 0)
            {
                throw new InvalidDataException(
                    "Kosinski decompression FIFO staged an inconsistent PRE"
                    + " retirement.");
            }

            DirectSubmission completed = directQueue[0];
            directQueue.RemoveAt(0);
            stagedDirectRetirements = 0;

            // The staged head owned the prior busy-state sample. Its proven
            // PRE retirement ends that evidence before current physical RAM
            // is reconciled.
            priorDirectBusy = false;
            if (hardwareTimingSchema == CurrentSchema && writer != null)
            {
                WriteCompletion(
                    writer,
                    rawFrame,
                    "pre_main_loop",
                    DirectEventKind,
                    completed.Ordinal,
                    completed.Fingerprint);
            }
        }

        private void ObserveDirectQueue(
            int rawFrame,
            IGpgxHost host,
            TextWriter writer,
            int physicalCount,
            bool busy,
            bool directServiceAdmitted)
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
                if (hardwareTimingSchema == CurrentSchema
                    && writer != null)
                {
                    WriteCompletion(
                        writer,
                        rawFrame,
                        "pre_main_loop",
                        DirectEventKind,
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
                directQueue.Add(CreateDirectSubmission(host, index));
            }
            priorDirectBusy = busy;
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

        private bool DirectEntryMatches(
            IGpgxHost host,
            int index,
            DirectSubmission submission)
        {
            int entry = S3KRam.KosDecompQueue
                + index * S3KRam.KosDecompQueueEntrySize;
            return S3KRam.U32(host, entry) == submission.Source
                && S3KRam.U32(host, entry + 4)
                    == submission.Destination;
        }

        private DirectSubmission CreateDirectSubmission(
            IGpgxHost host,
            int index)
        {
            int entry = S3KRam.KosDecompQueue
                + index * S3KRam.KosDecompQueueEntrySize;
            uint source = S3KRam.U32(host, entry);
            uint destination = S3KRam.U32(host, entry + 4);
            StandardKosShape shape = InspectStandardKos(source);
            long ordinal = nextDirectOrdinal++;
            return new DirectSubmission
            {
                Ordinal = ordinal,
                Source = source,
                Destination = destination,
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

        private StandardKosShape InspectStandardKos(uint canonicalSource)
        {
            int start = checked((int)canonicalSource);
            int position = start;
            int descriptor = 0;
            int descriptorBits = 0;
            int outputLength = 0;

            while (true)
            {
                int first = PopDescriptorBit(
                    ref descriptor, ref descriptorBits, ref position);
                if (first != 0)
                {
                    RequireRomBytes(position, 1);
                    position++;
                    outputLength = CheckedOutputLength(outputLength, 1);
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
                                DestinationLength = outputLength
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

        private bool HeadRetired(
            IGpgxHost host,
            byte modulesLeft,
            int physicalCount)
        {
            if (modulesLeft == 0 || physicalCount == 0)
            {
                return true;
            }
            if (queue.Count < 2)
            {
                return false;
            }

            Submission next = queue[1];
            uint observedSource =
                S3KRam.U32(host, S3KRam.KosModuleSource);
            ushort observedDestination =
                S3KRam.U16(host, S3KRam.KosModuleDestination);
            return observedSource == next.Source + 2
                && observedDestination == next.Destination;
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
}
