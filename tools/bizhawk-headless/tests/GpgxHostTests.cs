using System;
using System.Collections.Generic;
using System.IO;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class GpgxHostTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "GpgxHost GHZ1 sync settings match tracked movie",
                Ghz1SyncSettingsMatchTrackedMovie));

            string romPath = Environment.GetEnvironmentVariable("S1_ROM_PATH");
            if (string.IsNullOrEmpty(romPath) || !File.Exists(romPath))
            {
                Console.WriteLine(
                    "SKIP GpgxHost advances ten frames: S1_ROM_PATH not set");
                return;
            }

            // Both of these open the waterbox core inside the test
            // process itself. Two live cores in one process is not a
            // supported configuration, so they are tagged serial rather
            // than merely mutually exclusive.
            tests.Add(new TestMain.TestCase(
                "GpgxHost binds 64KiB 68K RAM before compatibility Main RAM",
                BindsPinnedMainRamDomain,
                game: "s1",
                serial: true,
                estimatedSeconds: 2.0));
            tests.Add(new TestMain.TestCase(
                "GpgxHost advances ten frames",
                AdvancesTenFrames,
                game: "s1",
                serial: true,
                estimatedSeconds: 2.0));
            tests.Add(new TestMain.TestCase(
                "GpgxHost exposes bounded optional main RAM writing",
                WritesBoundedMainRam,
                game: "s1",
                serial: true,
                estimatedSeconds: 2.0));
            tests.Add(new TestMain.TestCase(
                "GpgxHost S1 boot accepts a delayed title Start",
                BootAcceptsDelayedTitleStart,
                game: "s1",
                serial: true,
                estimatedSeconds: 2.0));
        }

        private static void BindsPinnedMainRamDomain()
        {
            using (var host = GpgxHost.Open(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                GpgxHost.CreateGhz1SyncSettings()))
            {
                AssertEx.Equal("68K RAM", host.MainRamDomainName);
                AssertEx.Equal(65536L, host.MainRamDomainSize);
            }
        }

        private static void AdvancesTenFrames()
        {
            using (IGpgxHost host = GpgxHost.Open(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                GpgxHost.CreateGhz1SyncSettings()))
            {
                for (var i = 0; i < 10; i++)
                {
                    host.Advance();
                }
                Console.WriteLine("GPGX completed frame: " + host.CompletedFrame);
                AssertEx.Equal(10, host.CompletedFrame);
            }
        }

        private static void WritesBoundedMainRam()
        {
            using (var host = GpgxHost.Open(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                GpgxHost.CreateGhz1SyncSettings()))
            {
                IMainRamWriter writer = host;
                writer.WriteMainRamByte(0, 0x5A);
                AssertEx.Equal((byte)0x5A, host.ReadMainRamByte(0));
                AssertEx.Throws<ArgumentOutOfRangeException>(
                    () => writer.WriteMainRamByte(-1, 0), "offset");
                AssertEx.Throws<ArgumentOutOfRangeException>(
                    () => writer.WriteMainRamByte(65536, 0), "offset");
            }
        }

        private static void BootAcceptsDelayedTitleStart()
        {
            using (IGpgxHost host = GpgxHost.Open(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                GpgxHost.CreateGhz1SyncSettings()))
            {
                int titleFrames = 0;
                for (int frame = 0; frame < 2400; frame++)
                {
                    host.ClearButtons();
                    if ((host.ReadMainRamByte(S1Ram.GameMode) & 0x7F) == 0x04)
                    {
                        titleFrames++;
                        if (titleFrames >= 120
                            && ((titleFrames - 120) % 10) < 5)
                        {
                            host.SetButton("P1 Start", true);
                        }
                    }
                    host.Advance();
                    if ((host.ReadMainRamByte(S1Ram.GameMode) & 0x7F) == 0x0C)
                    {
                        return;
                    }
                }
                throw new InvalidOperationException("S1 stayed in mode 0x"
                    + host.ReadMainRamByte(S1Ram.GameMode).ToString("X2")
                    + " after " + host.CompletedFrame + " frames; title frames="
                    + titleFrames + ".");
            }
        }

        private static void Ghz1SyncSettingsMatchTrackedMovie()
        {
            GPGX.GPGXSyncSettings settings =
                GpgxHost.CreateGhz1SyncSettings();
            AssertEx.Equal(false, settings.UseSixButton);
            AssertEx.Equal(GPGX.ControlType.Normal, settings.ControlTypeLeft);
            AssertEx.Equal(GPGX.ControlType.Normal, settings.ControlTypeRight);
            AssertEx.Equal(LibGPGX.Region.Autodetect, settings.Region);
            AssertEx.Equal(LibGPGX.ForceVDP.Disabled, settings.ForceVDP);
            AssertEx.Equal(false, settings.LoadBIOS);
            AssertEx.Equal(
                LibGPGX.InitSettings.OverscanType.All,
                settings.Overscan);
            AssertEx.Equal(false, settings.GGExtra);
            AssertEx.Equal(
                LibGPGX.InitSettings.SMSFMSoundChipType.YM2413_MAME,
                settings.SMSFMSoundChip);
            AssertEx.Equal(
                LibGPGX.InitSettings.GenesisFMSoundChipType.MAME_YM2612,
                settings.GenesisFMSoundChip);
            AssertEx.Equal(
                LibGPGX.InitSettings.FilterType.None,
                settings.Filter);
            AssertEx.Equal((ushort)26214, settings.LowPassRange);
            AssertEx.Equal((short)880, settings.LowFreq);
            AssertEx.Equal((short)5000, settings.HighFreq);
            AssertEx.Equal(1.0f, settings.LowGain);
            AssertEx.Equal(1.0f, settings.MidGain);
            AssertEx.Equal(1.0f, settings.HighGain);
            AssertEx.Equal(4294902015u, settings.BackdropColor);
            AssertEx.Equal(false, settings.SpritesAlwaysOnTop);
        }
    }
}
