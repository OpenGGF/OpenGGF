using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Immutable, retail-ROM-pinned callback and data boundaries for native
    /// player DPLC observation. These are intentionally separate from the
    /// Nemesis PLC queue addresses: the similarly named historical S1
    /// ProcessDPLC routine does not service player sprite art.
    /// </summary>
    public static class DynamicArtRomProfile
    {
        public static readonly GameProfile Sonic1Rev01 = new GameProfile(
            "s1-rev01",
            new[]
            {
                new DecisionWindow("sonic", 0x14312, 0x1436A)
            },
            0,
            new[] { 0x0D50, 0x0E64, 0x0F54, 0x1060 },
            new RamLayout(
                lastLoadedDplc: 0xF766,
                pendingTransferFlag: 0xF767,
                stagingBuffer: 0xC800,
                stagingBufferLength: 0x02E0,
                tailsLastLoadedDplc: 0,
                tailsTailsLastLoadedDplc: 0,
                dmaCommandBuffer: 0,
                dmaCommandBufferSlot: 0,
                dmaCommandStrideBytes: 0,
                dmaCommandCapacity: 0),
            new[]
            {
                new DplcTable("sonic", 0x22310)
            },
            new[]
            {
                new ArtSpan("sonic", 0x22610, 0xA120)
            },
            new[]
            {
                new VramBank("sonic", 0xF000)
            },
            new[]
            {
                new OpcodeWindow("sonic-decision-entry", 0x14312,
                    0x70, 0x00, 0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38,
                    0xF7, 0x66, 0x67, 0x4C, 0x11, 0xC0, 0xF7, 0x66),
                new OpcodeWindow("sonic-decision-return", 0x1436A,
                    0x4E, 0x75),
                new OpcodeWindow("level-vblank-transfer-probe", 0x0D20,
                    0x4A, 0x38, 0xF7, 0x67),
                new OpcodeWindow("special-stage-vblank-transfer-probe", 0x0E34,
                    0x4A, 0x38, 0xF7, 0x67),
                new OpcodeWindow("title-card-vblank-transfer-probe", 0x0F24,
                    0x4A, 0x38, 0xF7, 0x67),
                new OpcodeWindow("continue-vblank-transfer-probe", 0x1030,
                    0x4A, 0x38, 0xF7, 0x67),
                new OpcodeWindow("level-vblank-completion", 0x0D50,
                    0x33, 0xFC, 0x00, 0x00, 0x00, 0xA1),
                new OpcodeWindow("special-stage-vblank-completion", 0x0E64,
                    0x4A, 0x78, 0xF6, 0x14),
                new OpcodeWindow("title-card-vblank-completion", 0x0F54,
                    0x33, 0xFC, 0x00, 0x00, 0x00, 0xA1),
                new OpcodeWindow("continue-vblank-completion", 0x1060,
                    0x4A, 0x78, 0xF6, 0x14),
                new OpcodeWindow("sonic-dplc-table", 0x22310,
                    0x00, 0xB0, 0x00, 0xB1, 0x00, 0xBA, 0x00, 0xC1),
                new OpcodeWindow("sonic-art", 0x22610,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x33, 0x33, 0x00, 0x22, 0x22, 0x33)
            });

        public static readonly GameProfile Sonic2Rev01 = new GameProfile(
            "s2-rev01",
            new[]
            {
                new DecisionWindow("sonic", 0x1B848, 0x1B89A),
                new DecisionWindow("sonic", 0x1B84E, 0x1B89A,
                    DecisionEntryKind.DirectD0, 0x3AF90),
                new DecisionWindow("tails-tails", 0x1D184, 0x1D1FE),
                new DecisionWindow("tails", 0x1D1AC, 0x1D1FE),
                new DecisionWindow("tails", 0x1D1B2, 0x1D1FE,
                    DecisionEntryKind.DirectD0, 0x3AF98),
                new DecisionWindow("ss-sonic", 0x33ADA, 0x33B3E,
                    DecisionEntryKind.SpecialSharedRegisters, 0,
                    0x33ADE, 0xF766, 0x5CA0, 0),
                new DecisionWindow("ss-tails", 0x33ADA, 0x33B3E,
                    DecisionEntryKind.SpecialSharedRegisters, 0,
                    0x33ADE, 0xF7DE, 0x6000, 0x12),
                new DecisionWindow("ss-tails-tails", 0x34AB0, 0x34B1A,
                    DecisionEntryKind.ObjectMapping, 0,
                    0x34AC4, 0, 0, 0)
            },
            0x14AA,
            new[] { 0x14AC },
            new RamLayout(
                lastLoadedDplc: 0xF766,
                pendingTransferFlag: 0,
                stagingBuffer: 0,
                stagingBufferLength: 0,
                tailsLastLoadedDplc: 0xF7DE,
                tailsTailsLastLoadedDplc: 0xF7DF,
                dmaCommandBuffer: 0xDC00,
                dmaCommandBufferSlot: 0xDCFC,
                dmaCommandStrideBytes: 14,
                dmaCommandCapacity: 18),
            new[]
            {
                new DplcTable("sonic", 0x714E0),
                new DplcTable("tails", 0x7446C),
                new DplcTable("special-stage", 0x345FA)
            },
            new[]
            {
                new ArtSpan("sonic", 0x50000, 0x14320),
                new ArtSpan("tails", 0x64320, 0x0B8C0)
            },
            new[]
            {
                new VramBank("sonic", 0xF000),
                new VramBank("tails", 0xF400),
                new VramBank("tails-tails", 0xF600),
                new VramBank("ss-sonic", 0x5CA0),
                new VramBank("ss-tails", 0x6000),
                new VramBank("ss-tails-tails", 0x62C0)
            },
            new[]
            {
                new OpcodeWindow("queue-dma-entry", 0x144E,
                    0x22, 0x78, 0xDC, 0xFC, 0xB2, 0xFC, 0xDC, 0xFC),
                new OpcodeWindow("queue-dma-accepted-return", 0x14AA,
                    0x4E, 0x75),
                new OpcodeWindow("process-dma-queue", 0x14AC,
                    0x4B, 0xF9, 0x00, 0xC0, 0x00, 0x04, 0x43, 0xF8,
                    0xDC, 0x00),
                new OpcodeWindow("sonic-decision-entry", 0x1B848,
                    0x70, 0x00, 0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38,
                    0xF7, 0x66),
                new OpcodeWindow("sonic-decision-return", 0x1B89A,
                    0x4E, 0x75),
                new OpcodeWindow("sonic-part2-decision-entry", 0x1B84E,
                    0xB0, 0x38, 0xF7, 0x66),
                new OpcodeWindow("sonic-pilot-dplc-caller", 0x3AF8C,
                    0x10, 0x3B, 0x00, 0x0E, 0x60, 0x00, 0x3A, 0xFE),
                new OpcodeWindow("tails-tails-decision-entry", 0x1D184,
                    0x70, 0x00, 0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38,
                    0xF7, 0xDF),
                new OpcodeWindow("tails-decision-entry", 0x1D1AC,
                    0x70, 0x00, 0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38,
                    0xF7, 0xDE),
                new OpcodeWindow("tails-decision-return", 0x1D1FE,
                    0x4E, 0x75),
                new OpcodeWindow("tails-part2-decision-entry", 0x1D1B2,
                    0xB0, 0x38, 0xF7, 0xDE),
                new OpcodeWindow("tails-pilot-dplc-caller", 0x3AF98,
                    0x60, 0x00, 0x3A, 0xF0),
                new OpcodeWindow("special-stage-shared-player-decision-entry", 0x33ADA,
                    0x10, 0x28, 0x00, 0x1A, 0xB0, 0x14, 0x67, 0x5C),
                new OpcodeWindow("special-stage-tails-tails-decision-entry", 0x34AB0,
                    0x10, 0x29, 0x00, 0x23, 0x67, 0x08, 0x02, 0x00),
                new OpcodeWindow("special-stage-tails-tails-mapping-entry", 0x34AC4,
                    0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38, 0xF7, 0xDF),
                new OpcodeWindow("special-stage-player-decision-return", 0x33B3E,
                    0x4E, 0x75),
                new OpcodeWindow("special-stage-tails-tails-decision-return", 0x34B1A,
                    0x4E, 0x75),
                new OpcodeWindow("sonic-dplc-table", 0x714E0,
                    0x01, 0xAC, 0x01, 0xAE, 0x01, 0xB8, 0x01, 0xBE),
                new OpcodeWindow("tails-dplc-table", 0x7446C,
                    0x01, 0x16, 0x01, 0x18, 0x01, 0x1E, 0x01, 0x24),
                new OpcodeWindow("special-stage-dplc-table", 0x345FA,
                    0x00, 0x72, 0x00, 0x7A, 0x00, 0x82, 0x00, 0x8A),
                new OpcodeWindow("sonic-art", 0x50000,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
                new OpcodeWindow("tails-art", 0x64320,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x0D, 0xDB, 0x00)
            });

        public sealed class GameProfile
        {
            internal GameProfile(
                string id,
                DecisionWindow[] decisionWindows,
                int acceptedDmaReturn,
                int[] vBlankCompletionSites,
                RamLayout ram,
                DplcTable[] dplcTables,
                ArtSpan[] artSpans,
                VramBank[] vramBanks,
                OpcodeWindow[] opcodeWindows)
            {
                Id = id;
                DecisionWindows = Freeze(decisionWindows);
                AcceptedDmaReturn = acceptedDmaReturn;
                VBlankCompletionSites = Freeze(vBlankCompletionSites);
                Ram = ram;
                DplcTables = Freeze(dplcTables);
                ArtSpans = Freeze(artSpans);
                VramBanks = Freeze(vramBanks);
                OpcodeWindows = Freeze(opcodeWindows);
            }

            public string Id { get; private set; }
            public ReadOnlyCollection<DecisionWindow> DecisionWindows { get; private set; }
            public int AcceptedDmaReturn { get; private set; }
            public ReadOnlyCollection<int> VBlankCompletionSites { get; private set; }
            public RamLayout Ram { get; private set; }
            public ReadOnlyCollection<DplcTable> DplcTables { get; private set; }
            public ReadOnlyCollection<ArtSpan> ArtSpans { get; private set; }
            public ReadOnlyCollection<VramBank> VramBanks { get; private set; }
            public ReadOnlyCollection<OpcodeWindow> OpcodeWindows { get; private set; }
        }

        public enum DecisionEntryKind
        {
            ObjectMapping,
            DirectD0,
            SpecialSharedRegisters
        }

        public sealed class DecisionWindow
        {
            internal DecisionWindow(string owner, int entry, int returnAddress)
                : this(owner, entry, returnAddress,
                    DecisionEntryKind.ObjectMapping, 0, 0, 0, 0, 0)
            {
            }

            internal DecisionWindow(
                string owner, int entry, int returnAddress,
                DecisionEntryKind entryKind, int pilotCallerProbe)
                : this(owner, entry, returnAddress, entryKind, pilotCallerProbe,
                    0, 0, 0, 0)
            {
            }

            internal DecisionWindow(
                string owner, int entry, int returnAddress,
                DecisionEntryKind entryKind, int pilotCallerProbe,
                int mappingReadAddress, int expectedA4, int expectedD4,
                int expectedD1)
            {
                Owner = owner;
                Entry = entry;
                ReturnAddress = returnAddress;
                EntryKind = entryKind;
                PilotCallerProbe = pilotCallerProbe;
                MappingReadAddress = mappingReadAddress;
                ExpectedA4 = expectedA4;
                ExpectedD4 = expectedD4;
                ExpectedD1 = expectedD1;
            }

            public string Owner { get; private set; }
            public int Entry { get; private set; }
            public int ReturnAddress { get; private set; }
            public DecisionEntryKind EntryKind { get; private set; }
            public int PilotCallerProbe { get; private set; }
            public int MappingReadAddress { get; private set; }
            public int ExpectedA4 { get; private set; }
            public int ExpectedD4 { get; private set; }
            public int ExpectedD1 { get; private set; }
        }

        public sealed class RamLayout
        {
            internal RamLayout(
                int lastLoadedDplc,
                int pendingTransferFlag,
                int stagingBuffer,
                int stagingBufferLength,
                int tailsLastLoadedDplc,
                int tailsTailsLastLoadedDplc,
                int dmaCommandBuffer,
                int dmaCommandBufferSlot,
                int dmaCommandStrideBytes,
                int dmaCommandCapacity)
            {
                LastLoadedDplc = lastLoadedDplc;
                PendingTransferFlag = pendingTransferFlag;
                StagingBuffer = stagingBuffer;
                StagingBufferLength = stagingBufferLength;
                TailsLastLoadedDplc = tailsLastLoadedDplc;
                TailsTailsLastLoadedDplc = tailsTailsLastLoadedDplc;
                DmaCommandBuffer = dmaCommandBuffer;
                DmaCommandBufferSlot = dmaCommandBufferSlot;
                DmaCommandStrideBytes = dmaCommandStrideBytes;
                DmaCommandCapacity = dmaCommandCapacity;
            }

            public int LastLoadedDplc { get; private set; }
            public int PendingTransferFlag { get; private set; }
            public int StagingBuffer { get; private set; }
            public int StagingBufferLength { get; private set; }
            public int TailsLastLoadedDplc { get; private set; }
            public int TailsTailsLastLoadedDplc { get; private set; }
            public int DmaCommandBuffer { get; private set; }
            public int DmaCommandBufferSlot { get; private set; }
            public int DmaCommandStrideBytes { get; private set; }
            public int DmaCommandCapacity { get; private set; }
        }

        public sealed class DplcTable
        {
            internal DplcTable(string owner, int address)
            {
                Owner = owner;
                Address = address;
            }

            public string Owner { get; private set; }
            public int Address { get; private set; }
        }

        public sealed class ArtSpan
        {
            internal ArtSpan(string owner, int address, int length)
            {
                Owner = owner;
                Address = address;
                Length = length;
            }

            public string Owner { get; private set; }
            public int Address { get; private set; }
            public int Length { get; private set; }
        }

        public sealed class VramBank
        {
            internal VramBank(string owner, int destination)
            {
                Owner = owner;
                Destination = destination;
            }

            public string Owner { get; private set; }
            public int Destination { get; private set; }
        }

        public sealed class OpcodeWindow
        {
            internal OpcodeWindow(string name, int address, params byte[] bytes)
            {
                Name = name;
                Address = address;
                Bytes = Freeze(bytes);
            }

            public string Name { get; private set; }
            public int Address { get; private set; }
            public ReadOnlyCollection<byte> Bytes { get; private set; }
        }

        private static ReadOnlyCollection<T> Freeze<T>(T[] values)
        {
            if (values == null)
            {
                throw new ArgumentNullException("values");
            }
            T[] copy = new T[values.Length];
            Array.Copy(values, copy, values.Length);
            return new ReadOnlyCollection<T>(copy);
        }
    }
}
