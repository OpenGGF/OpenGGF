using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Mirrors the S3K Kosinski-module FIFO and emits the authoritative
    /// completion edge at the post-objects observation boundary.
    /// Submission identity is retained while the active RAM aliases advance.
    /// </summary>
    public sealed class HardwareTimingEventEngine
    {
        private const string KindName = "KOS_MODULE_QUEUE";
        private const string EventKind = "kos_module_queue";
        private const string CompressionVariant = "kosinski_moduled";

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

        private readonly byte[] rom;
        private readonly List<Submission> queue = new List<Submission>();
        private long nextOrdinal;
        private byte priorModulesLeft;

        public HardwareTimingEventEngine(byte[] rom)
        {
            if (rom == null)
            {
                throw new ArgumentNullException("rom");
            }
            this.rom = rom;
        }

        /// <summary>
        /// Observes one eligible post-objects boundary. A null writer keeps
        /// the run-wide FIFO/ordinal ledger current without exporting an
        /// event into the current segment.
        /// </summary>
        public void ObservePostObjects(
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
            int physicalCount = ReadPhysicalCount(host);

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
                    WriteCompletion(writer, rawFrame, completed);
                }
            }

            ReconcileQueue(host, physicalCount);
            priorModulesLeft = modulesLeft;
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
                    KindName,
                    checked((int)canonicalSource),
                    shape.CompressedLength,
                    destination,
                    shape.DestinationLength,
                    CompressionVariant,
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
                if (descriptorBits == 0)
                {
                    RequireRomBytes(position, 2);
                    descriptor = rom[position]
                        | (rom[position + 1] << 8);
                    position += 2;
                    descriptorBits = 16;
                }
                int first = descriptor & 1;
                descriptor >>= 1;
                descriptorBits--;
                if (first != 0)
                {
                    RequireRomBytes(position, 1);
                    position++;
                    continue;
                }

                if (descriptorBits == 0)
                {
                    RequireRomBytes(position, 2);
                    descriptor = rom[position]
                        | (rom[position + 1] << 8);
                    position += 2;
                    descriptorBits = 16;
                }
                int second = descriptor & 1;
                descriptor >>= 1;
                descriptorBits--;
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

        private int ReadPhysicalCount(IGpgxHost host)
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
            Submission submission)
        {
            writer.Write("{\"event\":\"hardware_work_completed\",");
            writer.Write("\"raw_frame\":");
            writer.Write(rawFrame);
            writer.Write(",\"boundary\":\"post_objects\",");
            writer.Write("\"kind\":\"");
            writer.Write(EventKind);
            writer.Write("\",\"ordinal\":");
            writer.Write(submission.Ordinal);
            writer.Write(",\"submission_fingerprint\":\"");
            writer.Write(submission.Fingerprint);
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
