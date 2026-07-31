using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    public static class LoadQueueStateProjector
    {
        public static string CaptureS1(int frame, byte[] rom, IGpgxHost host)
        {
            return CapturePlc(frame, "s1_nemesis_plc", 1, rom, host,
                S1Ram.PlcBuffer, S1Ram.PlcPatternsLeft,
                S1Ram.PlcEntrySize, S1Ram.PlcCapacity);
        }

        public static string CaptureS2(int frame, byte[] rom, IGpgxHost host)
        {
            return CapturePlc(frame, "s2_nemesis_plc", 2, rom, host,
                S2Ram.PlcBuffer, S2Ram.PlcPatternsLeft,
                S2Ram.PlcEntrySize, S2Ram.PlcCapacity);
        }

        public static IList<string> CaptureS3k(
            int frame, byte[] rom, IGpgxHost host)
        {
            if (rom == null) throw new ArgumentNullException("rom");
            if (host == null) throw new ArgumentNullException("host");
            return new[]
            {
                CaptureS3kDirect(frame, host),
                CaptureS3kModule(frame, rom, host)
            };
        }

        private static string CaptureS3kDirect(int frame, IGpgxHost host)
        {
            int rawCount = S3KRam.U16(host, S3KRam.KosDecompQueueCount);
            int count = rawCount & 0x7FFF;
            if (count > S3KRam.KosDecompQueueCapacity)
            {
                throw new InvalidDataException("S3K direct queue count exceeds capacity.");
            }
            var waiting = new List<string>();
            for (int index = 1; index < count; index++)
            {
                int entry = S3KRam.KosDecompQueue
                    + index * S3KRam.KosDecompQueueEntrySize;
                waiting.Add(LoadQueueStateEvent.Fingerprint(
                    3, S3KRam.U32(host, entry) & 0x00FFFFFFU,
                    S3KRam.U32(host, entry + 4), null));
            }
            bool busy = count != 0;
            bool prepared = (rawCount & 0x8000) != 0;
            int source = busy
                ? checked((int)(S3KRam.U32(host, S3KRam.KosDecompQueue)
                    & 0x00FFFFFFU)) : -1;
            int destination = busy
                ? unchecked((int)S3KRam.U32(
                    host, S3KRam.KosDecompQueue + 4)) : -1;
            return LoadQueueStateEvent.Format(
                frame, "s3k_kos_direct", busy, prepared, source, destination,
                -1, -1, waiting,
                new LoadQueueStateEvent.Observation[0]);
        }

        private static string CaptureS3kModule(
            int frame, byte[] rom, IGpgxHost host)
        {
            int rawRemaining = S3KRam.U8(host, S3KRam.KosModulesLeft);
            int remaining = rawRemaining & 0x7F;
            bool prepared = remaining != 0;
            var waiting = new List<string>();
            int start = prepared ? 1 : 0;
            for (int index = start; index < S3KRam.KosModuleQueueCapacity; index++)
            {
                int entry = S3KRam.KosModuleQueue
                    + index * S3KRam.KosModuleQueueEntrySize;
                uint source = S3KRam.U32(host, entry) & 0x00FFFFFFU;
                if (source == 0) break;
                int total = KosModuleCount(rom, source);
                // Queue_Kos_Module stores an already-byte VRAM address here
                // (sonic3k.asm:2683), and plreq emits tiles_to_bytes(toVRAMaddr)
                // (sonic3k.macros.asm:195-198), so this must not be scaled again.
                uint destination = (uint)S3KRam.U16(host, entry + 4);
                waiting.Add(LoadQueueStateEvent.Fingerprint(
                    4, source, destination, total));
            }
            bool busy = prepared || waiting.Count != 0;
            return LoadQueueStateEvent.Format(
                frame, "s3k_kos_module", busy, prepared, -1, -1, -1,
                prepared ? remaining : -1, waiting,
                new LoadQueueStateEvent.Observation[0]);
        }

        private static string CapturePlc(
            int frame, string kind, int kindId, byte[] rom, IGpgxHost host,
            int buffer, int patternsAddress, int entrySize, int capacity)
        {
            if (rom == null) throw new ArgumentNullException("rom");
            if (host == null) throw new ArgumentNullException("host");
            int remaining = S1Ram.U16(host, patternsAddress);
            bool prepared = remaining != 0;
            var fingerprints = new List<string>();
            int start = prepared ? 1 : 0;
            for (int index = start; index < capacity; index++)
            {
                int entry = buffer + index * entrySize;
                uint source = S1Ram.U32(host, entry) & 0x00FFFFFFU;
                if (source == 0) break;
                uint destinationTile = (uint)(S1Ram.U16(host, entry + 4) / 32);
                int total = NemesisPatternCount(rom, source);
                fingerprints.Add(LoadQueueStateEvent.Fingerprint(
                    kindId, source, destinationTile, total));
            }
            bool busy = prepared || fingerprints.Count != 0;
            IList<LoadQueueStateEvent.Observation> observations =
                new LoadQueueStateEvent.Observation[0];
            return LoadQueueStateEvent.Format(
                frame, kind, busy, prepared, -1, -1, -1,
                prepared ? remaining : -1, fingerprints, observations);
        }

        private static int NemesisPatternCount(byte[] rom, uint source)
        {
            int offset = checked((int)source);
            if (offset < 0 || offset > rom.Length - 2)
            {
                throw new InvalidDataException(
                    "PLC Nemesis source is outside the supplied ROM.");
            }
            int count = ((rom[offset] << 8) | rom[offset + 1]) & 0x7FFF;
            if (count == 0)
            {
                throw new InvalidDataException(
                    "PLC Nemesis stream declares zero patterns.");
            }
            return count;
        }

        private static int KosModuleCount(byte[] rom, uint source)
        {
            int offset = checked((int)source);
            if (offset < 0 || offset > rom.Length - 2)
            {
                throw new InvalidDataException(
                    "KosM source is outside the supplied ROM.");
            }
            int destinationLength = (rom[offset] << 8) | rom[offset + 1];
            if (destinationLength == 0xA000) destinationLength = 0x8000;
            if (destinationLength == 0)
            {
                throw new InvalidDataException("KosM stream declares zero output.");
            }
            return (destinationLength + 0xFFF) / 0x1000;
        }
    }
}
