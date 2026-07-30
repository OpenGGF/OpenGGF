using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Pins the ROM evidence used by the native player-DPLC observers. The
    /// literals here are deliberately independent of DynamicArtRomProfile:
    /// a shifted callback, pointer table or instruction window must fail
    /// against the retail REV01 image before it can reach an observer.
    /// </summary>
    internal static class DynamicArtRomProfileTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "DynamicArtRomProfile pins Sonic 1 REV01 player-DPLC callbacks and data",
                PinsSonic1Rev01,
                game: "s1"));
            tests.Add(new TestMain.TestCase(
                "DynamicArtRomProfile pins Sonic 2 REV01 player-DPLC callbacks and data",
                PinsSonic2Rev01,
                game: "s2"));
        }

        private static void PinsSonic1Rev01()
        {
            byte[] rom = ReadRom("S1_ROM_PATH");
            RomIdentity.ValidateSonic1Rev01(rom);

            DynamicArtRomProfile.GameProfile profile =
                DynamicArtRomProfile.Sonic1Rev01;
            AssertEx.Equal("s1-rev01", profile.Id);
            AssertEx.Equal(1, profile.DecisionWindows.Count);
            AssertDecision(profile.DecisionWindows[0], "sonic", 0x14312, 0x1436A);
            AssertEx.Equal(0, profile.AcceptedDmaReturn);
            AssertEx.Equal(4, profile.VBlankCompletionSites.Count);
            AssertEx.Equal(0x0D50, profile.VBlankCompletionSites[0]);
            AssertEx.Equal(0x0E64, profile.VBlankCompletionSites[1]);
            AssertEx.Equal(0x0F54, profile.VBlankCompletionSites[2]);
            AssertEx.Equal(0x1060, profile.VBlankCompletionSites[3]);
            AssertEx.Equal(0xF766, profile.Ram.LastLoadedDplc);
            AssertEx.Equal(0xF767, profile.Ram.PendingTransferFlag);
            AssertEx.Equal(0xC800, profile.Ram.StagingBuffer);
            AssertEx.Equal(0x02E0, profile.Ram.StagingBufferLength);
            AssertEx.Equal(0x22310, profile.DplcTables[0].Address);
            AssertEx.Equal(0x22610, profile.ArtSpans[0].Address);
            AssertEx.Equal(0xA120, profile.ArtSpans[0].Length);
            AssertEx.Equal(0xF000, profile.VramBanks[0].Destination);
            AssertImmutable(profile);
            AssertProfileOpcodeWindows(profile, rom);

            AssertWindow(rom, 0x14312,
                0x70, 0x00, 0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38,
                0xF7, 0x66, 0x67, 0x4C, 0x11, 0xC0, 0xF7, 0x66);
            AssertWindow(rom, 0x1436A, 0x4E, 0x75);
            AssertWindow(rom, 0x14336, 0x47, 0xF8, 0xC8, 0x00);
            AssertEx.Equal(profile.Ram.StagingBuffer, ReadU16(rom, 0x14338));
            AssertWindow(rom, 0x0D20, 0x4A, 0x38, 0xF7, 0x67);
            AssertWindow(rom, 0x0E34, 0x4A, 0x38, 0xF7, 0x67);
            AssertWindow(rom, 0x0F24, 0x4A, 0x38, 0xF7, 0x67);
            AssertWindow(rom, 0x1030, 0x4A, 0x38, 0xF7, 0x67);
            AssertWindow(rom, 0x0D50, 0x33, 0xFC, 0x00, 0x00, 0x00, 0xA1);
            AssertWindow(rom, 0x0E64, 0x4A, 0x78, 0xF6, 0x14);
            AssertWindow(rom, 0x0F54, 0x33, 0xFC, 0x00, 0x00, 0x00, 0xA1);
            AssertWindow(rom, 0x1060, 0x4A, 0x78, 0xF6, 0x14);
            AssertWindow(rom, 0x0D2C, 0x2A, 0xBC, 0x94, 0x01, 0x93, 0x70);
            AssertEx.Equal(profile.Ram.StagingBufferLength,
                ReadDmaLengthBytes(rom, 0x0D2C));
            AssertWindow(rom, 0x0D3C, 0x3A, 0xBC, 0x70, 0x00);
            AssertEx.Equal(profile.VramBanks[0].Destination,
                DecodeVramDestination(ReadU16(rom, 0x0D3E)));
            AssertWindow(rom, 0x22310,
                0x00, 0xB0, 0x00, 0xB1, 0x00, 0xBA, 0x00, 0xC1);
            AssertWindow(rom, 0x22610,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x33, 0x33, 0x00, 0x22, 0x22, 0x33);
            AssertWindow(rom, 0x2C720,
                0x00, 0x01, 0x11, 0x00, 0x00, 0x00, 0x00, 0x00);
            AssertWindow(rom, 0x2C730,
                0x80, 0x1B, 0x80, 0x03, 0x00, 0x15, 0x13, 0x26);
            AssertEx.Equal(0x2C730,
                profile.ArtSpans[0].Address + profile.ArtSpans[0].Length);
        }

        private static void PinsSonic2Rev01()
        {
            byte[] rom = ReadRom("S2_ROM_PATH");
            RomIdentity.ValidateSonic2Rev01(rom);

            DynamicArtRomProfile.GameProfile profile =
                DynamicArtRomProfile.Sonic2Rev01;
            AssertEx.Equal("s2-rev01", profile.Id);
            AssertEx.Equal(8, profile.DecisionWindows.Count);
            AssertDecision(profile.DecisionWindows[0], "sonic", 0x1B848, 0x1B89A);
            AssertDecision(profile.DecisionWindows[1], "sonic", 0x1B84E, 0x1B89A,
                DynamicArtRomProfile.DecisionEntryKind.DirectD0, 0x3AF90);
            AssertDecision(profile.DecisionWindows[2], "tails-tails", 0x1D184, 0x1D1FE);
            AssertDecision(profile.DecisionWindows[3], "tails", 0x1D1AC, 0x1D1FE);
            AssertDecision(profile.DecisionWindows[4], "tails", 0x1D1B2, 0x1D1FE,
                DynamicArtRomProfile.DecisionEntryKind.DirectD0, 0x3AF98);
            AssertSpecialSharedDecision(profile.DecisionWindows[5], "ss-sonic",
                0xF766, 0x5CA0, 0);
            AssertSpecialSharedDecision(profile.DecisionWindows[6], "ss-tails",
                0xF7DE, 0x6000, 0x12);
            AssertDeferredObjectDecision(profile.DecisionWindows[7],
                "ss-tails-tails", 0x34AB0, 0x34B1A, 0x34AC4);
            AssertEx.Equal(0x14AA, profile.AcceptedDmaReturn);
            AssertEx.Equal(1, profile.VBlankCompletionSites.Count);
            AssertEx.Equal(0x14AC, profile.VBlankCompletionSites[0]);
            AssertEx.Equal(0xF766, profile.Ram.LastLoadedDplc);
            AssertEx.Equal(0xF7DE, profile.Ram.TailsLastLoadedDplc);
            AssertEx.Equal(0xF7DF, profile.Ram.TailsTailsLastLoadedDplc);
            AssertEx.Equal(0xDC00, profile.Ram.DmaCommandBuffer);
            AssertEx.Equal(0xDCFC, profile.Ram.DmaCommandBufferSlot);
            AssertEx.Equal(14, profile.Ram.DmaCommandStrideBytes);
            AssertEx.Equal(18, profile.Ram.DmaCommandCapacity);
            AssertEx.Equal(0x714E0, profile.DplcTables[0].Address);
            AssertEx.Equal(0x7446C, profile.DplcTables[1].Address);
            AssertEx.Equal(0x345FA, profile.DplcTables[2].Address);
            AssertEx.Equal(0x50000, profile.ArtSpans[0].Address);
            AssertEx.Equal(0x14320, profile.ArtSpans[0].Length);
            AssertEx.Equal(0x64320, profile.ArtSpans[1].Address);
            AssertEx.Equal(0x0B8C0, profile.ArtSpans[1].Length);
            AssertEx.Equal(0xF000, profile.VramBanks[0].Destination);
            AssertEx.Equal(0xF400, profile.VramBanks[1].Destination);
            AssertEx.Equal(0xF600, profile.VramBanks[2].Destination);
            AssertEx.Equal(0x5CA0, profile.VramBanks[3].Destination);
            AssertEx.Equal(0x6000, profile.VramBanks[4].Destination);
            AssertEx.Equal(0x62C0, profile.VramBanks[5].Destination);
            AssertImmutable(profile);
            AssertProfileOpcodeWindows(profile, rom);

            AssertWindow(rom, 0x144E,
                0x22, 0x78, 0xDC, 0xFC, 0xB2, 0xFC, 0xDC, 0xFC);
            AssertWindow(rom, 0x14AA, 0x4E, 0x75);
            AssertWindow(rom, 0x14AC,
                0x4B, 0xF9, 0x00, 0xC0, 0x00, 0x04, 0x43, 0xF8,
                0xDC, 0x00);
            int dmaQueueBytes = ReadU16(rom, 0x1450) - ReadU16(rom, 0x14B4);
            int dmaCommandStride = DeriveQueuedDmaCommandStride(rom);
            AssertEx.Equal(0x00FC, dmaQueueBytes);
            AssertEx.Equal(dmaCommandStride, profile.Ram.DmaCommandStrideBytes);
            AssertEx.Equal(0, dmaQueueBytes % dmaCommandStride);
            AssertEx.Equal(dmaQueueBytes / dmaCommandStride,
                profile.Ram.DmaCommandCapacity);
            AssertWindow(rom, 0x1B848,
                0x70, 0x00, 0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38,
                0xF7, 0x66);
            AssertWindow(rom, 0x1B84E, 0xB0, 0x38, 0xF7, 0x66);
            AssertWindow(rom, 0x1B89A, 0x4E, 0x75);
            AssertWindow(rom, 0x3AF8C,
                0x10, 0x3B, 0x00, 0x0E, 0x60, 0x00, 0x3A, 0xFE);
            AssertWindow(rom, 0x1B86A, 0x38, 0x3C, 0xF0, 0x00);
            AssertWindow(rom, 0x1B884, 0x06, 0x81, 0x00, 0x05, 0x00, 0x00);
            AssertWindow(rom, 0x1D184,
                0x70, 0x00, 0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38,
                0xF7, 0xDF);
            AssertWindow(rom, 0x1D1AC,
                0x70, 0x00, 0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38,
                0xF7, 0xDE);
            AssertWindow(rom, 0x1D1B2, 0xB0, 0x38, 0xF7, 0xDE);
            AssertWindow(rom, 0x1D1FE, 0x4E, 0x75);
            AssertWindow(rom, 0x3AF98, 0x60, 0x00, 0x3A, 0xF0);
            AssertWindow(rom, 0x1D1A6, 0x38, 0x3C, 0xF6, 0x00);
            AssertWindow(rom, 0x1D1CE, 0x38, 0x3C, 0xF4, 0x00);
            AssertWindow(rom, 0x1D1E8, 0x06, 0x81, 0x00, 0x06, 0x43, 0x20);
            AssertWindow(rom, 0x33ADA,
                0x10, 0x28, 0x00, 0x1A, 0xB0, 0x14, 0x67, 0x5C);
            AssertWindow(rom, 0x34AB0,
                0x10, 0x29, 0x00, 0x23, 0x67, 0x08, 0x02, 0x00);
            AssertWindow(rom, 0x34AC4,
                0x10, 0x28, 0x00, 0x1A, 0xB0, 0x38, 0xF7, 0xDF);
            AssertWindow(rom, 0x33B3E, 0x4E, 0x75);
            AssertWindow(rom, 0x34B1A, 0x4E, 0x75);
            AssertWindow(rom, 0x33AD2, 0x38, 0x3C, 0x5C, 0xA0);
            AssertWindow(rom, 0x349E8, 0x38, 0x3C, 0x60, 0x00);
            AssertWindow(rom, 0x34AF8, 0x34, 0x3C, 0x62, 0xC0);
            AssertWindow(rom, 0x714E0,
                0x01, 0xAC, 0x01, 0xAE, 0x01, 0xB8, 0x01, 0xBE);
            AssertWindow(rom, 0x7446C,
                0x01, 0x16, 0x01, 0x18, 0x01, 0x1E, 0x01, 0x24);
            AssertWindow(rom, 0x345FA,
                0x00, 0x72, 0x00, 0x7A, 0x00, 0x82, 0x00, 0x8A);
            AssertWindow(rom, 0x50000,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
            AssertWindow(rom, 0x64320,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x0D, 0xDB, 0x00);
            AssertWindow(rom, 0x6FBD0,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
            AssertWindow(rom, 0x6FBE0,
                0x01, 0xAC, 0x01, 0xAE, 0x01, 0xD0, 0x01, 0xE2);
            AssertEx.Equal(ReadU32(rom, 0x1B886), profile.ArtSpans[0].Address);
            AssertEx.Equal(ReadU32(rom, 0x1D1EA), profile.ArtSpans[1].Address);
            AssertEx.Equal(profile.ArtSpans[0].Length,
                profile.ArtSpans[1].Address - profile.ArtSpans[0].Address);
            AssertEx.Equal(profile.ArtSpans[1].Length,
                0x6FBE0 - profile.ArtSpans[1].Address);
        }

        private static void AssertDecision(
            DynamicArtRomProfile.DecisionWindow window,
            string owner,
            int entry,
            int returnAddress)
        {
            AssertEx.Equal(owner, window.Owner);
            AssertEx.Equal(entry, window.Entry);
            AssertEx.Equal(returnAddress, window.ReturnAddress);
            AssertEx.Equal(DynamicArtRomProfile.DecisionEntryKind.ObjectMapping,
                window.EntryKind);
            AssertEx.Equal(0, window.PilotCallerProbe);
            AssertEx.Equal(0, window.MappingReadAddress);
            AssertEx.Equal(0, window.ExpectedA4);
            AssertEx.Equal(0, window.ExpectedD4);
            AssertEx.Equal(0, window.ExpectedD1);
        }

        private static void AssertDecision(
            DynamicArtRomProfile.DecisionWindow window,
            string owner,
            int entry,
            int returnAddress,
            DynamicArtRomProfile.DecisionEntryKind entryKind,
            int pilotCallerProbe)
        {
            AssertEx.Equal(owner, window.Owner);
            AssertEx.Equal(entry, window.Entry);
            AssertEx.Equal(returnAddress, window.ReturnAddress);
            AssertEx.Equal(entryKind, window.EntryKind);
            AssertEx.Equal(pilotCallerProbe, window.PilotCallerProbe);
            AssertEx.Equal(0, window.MappingReadAddress);
            AssertEx.Equal(0, window.ExpectedA4);
            AssertEx.Equal(0, window.ExpectedD4);
            AssertEx.Equal(0, window.ExpectedD1);
        }

        private static void AssertSpecialSharedDecision(
            DynamicArtRomProfile.DecisionWindow window, string owner,
            int expectedA4, int expectedD4, int expectedD1)
        {
            AssertEx.Equal(owner, window.Owner);
            AssertEx.Equal(0x33ADA, window.Entry);
            AssertEx.Equal(0x33B3E, window.ReturnAddress);
            AssertEx.Equal(
                DynamicArtRomProfile.DecisionEntryKind.SpecialSharedRegisters,
                window.EntryKind);
            AssertEx.Equal(0, window.PilotCallerProbe);
            AssertEx.Equal(0x33ADE, window.MappingReadAddress);
            AssertEx.Equal(expectedA4, window.ExpectedA4);
            AssertEx.Equal(expectedD4, window.ExpectedD4);
            AssertEx.Equal(expectedD1, window.ExpectedD1);
        }

        private static void AssertDeferredObjectDecision(
            DynamicArtRomProfile.DecisionWindow window, string owner,
            int entry, int returnAddress, int mappingReadAddress)
        {
            AssertEx.Equal(owner, window.Owner);
            AssertEx.Equal(entry, window.Entry);
            AssertEx.Equal(returnAddress, window.ReturnAddress);
            AssertEx.Equal(DynamicArtRomProfile.DecisionEntryKind.ObjectMapping,
                window.EntryKind);
            AssertEx.Equal(0, window.PilotCallerProbe);
            AssertEx.Equal(mappingReadAddress, window.MappingReadAddress);
            AssertEx.Equal(0, window.ExpectedA4);
            AssertEx.Equal(0, window.ExpectedD4);
            AssertEx.Equal(0, window.ExpectedD1);
        }

        private static void AssertWindow(byte[] rom, int address, params byte[] expected)
        {
            for (int index = 0; index < expected.Length; index++)
            {
                AssertEx.Equal(expected[index], rom[address + index]);
            }
        }

        private static int ReadU16(byte[] rom, int address)
        {
            return (rom[address] << 8) | rom[address + 1];
        }

        private static int ReadU32(byte[] rom, int address)
        {
            return (rom[address] << 24)
                | (rom[address + 1] << 16)
                | (rom[address + 2] << 8)
                | rom[address + 3];
        }

        private static int ReadDmaLengthBytes(byte[] rom, int address)
        {
            return ((rom[address + 3] << 8) | rom[address + 5]) * 2;
        }

        private static int DecodeVramDestination(int commandWord)
        {
            return (commandWord & 0x3FFF) | 0xC000;
        }

        private static int DeriveQueuedDmaCommandStride(byte[] rom)
        {
            int[] wordStoreAddresses =
            {
                0x145E, 0x1468, 0x1472, 0x147C, 0x1486
            };
            foreach (int address in wordStoreAddresses)
            {
                AssertWindow(rom, address, 0x32, 0xC0);
            }
            AssertWindow(rom, 0x149A, 0x22, 0xC2);
            return wordStoreAddresses.Length * 2 + 4;
        }

        private static void AssertProfileOpcodeWindows(
            DynamicArtRomProfile.GameProfile profile,
            byte[] rom)
        {
            foreach (DynamicArtRomProfile.OpcodeWindow window
                in profile.OpcodeWindows)
            {
                for (int index = 0; index < window.Bytes.Count; index++)
                {
                    if (window.Bytes[index] != rom[window.Address + index])
                    {
                        throw new InvalidOperationException(
                            "profile opcode window mismatch " + window.Name
                            + " at " + (window.Address + index).ToString("X")
                            + ": expected " + window.Bytes[index]
                            + " but was " + rom[window.Address + index]);
                    }
                }
            }
        }

        private static void AssertImmutable(
            DynamicArtRomProfile.GameProfile profile)
        {
            AssertEx.Throws<NotSupportedException>(
                () => ((IList<DynamicArtRomProfile.DecisionWindow>)
                    profile.DecisionWindows).Add(null),
                "read-only");
        }

        private static byte[] ReadRom(string variable)
        {
            string path = Environment.GetEnvironmentVariable(variable);
            if (string.IsNullOrEmpty(path))
            {
                throw new TestMain.SkipTestException(
                    variable + " is not set.");
            }
            if (!File.Exists(path))
            {
                throw new InvalidOperationException(
                    "Supplied " + variable + " does not exist: " + path + ".");
            }
            return File.ReadAllBytes(path);
        }
    }
}
