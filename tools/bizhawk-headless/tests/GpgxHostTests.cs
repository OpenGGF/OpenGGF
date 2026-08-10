using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Runtime.InteropServices;
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
                "GpgxHost ten-frame RAM prefix is deterministic",
                TenFrameRamPrefixIsDeterministic,
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
            if (Environment.GetEnvironmentVariable("OPENGGF_GPGX_OBSERVER_PROOF") == "1")
            {
                tests.Add(new TestMain.TestCase(
                    "GpgxHost observer departures marshal the frozen ABI",
                    ObserverDeparturesMarshalFrozenAbi,
                    game: "s1",
                    serial: true,
                    estimatedSeconds: 2.0));
                tests.Add(new TestMain.TestCase(
                    "GpgxHost patched core boots all five Genesis FM selectors",
                    PatchedCoreBootsAllGenesisFmSelectors,
                    game: "s1",
                    serial: true,
                    estimatedSeconds: 10.0));
            }
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

        private static void TenFrameRamPrefixIsDeterministic()
        {
            using (IGpgxHost host = GpgxHost.Open(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                GpgxHost.CreateGhz1SyncSettings()))
            using (SHA256 sha = SHA256.Create())
            {
                for (var i = 0; i < 10; i++) host.Advance();
                var ram = new byte[65536];
                for (var i = 0; i < ram.Length; i++) ram[i] = host.ReadMainRamByte(i);
                string digest = BitConverter.ToString(sha.ComputeHash(ram))
                    .Replace("-", string.Empty).ToLowerInvariant();
                Console.WriteLine("GPGX ten-frame 68K RAM SHA-256: " + digest);
                AssertEx.Equal("de2f256064a0af797747c2b97505dc0b9f3df0de4f489eac731c23ae9ca9cc31", digest);
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

        private static void ObserverDeparturesMarshalFrozenAbi()
        {
            AssertEx.Equal(64, Marshal.SizeOf(typeof(GpgxAudioObserverAdapter.Config)));
            AssertEx.Equal(0, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "Magic"));
            AssertEx.Equal(4, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "AbiVersion"));
            AssertEx.Equal(6, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "StructSize"));
            AssertEx.Equal(8, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "HookSize"));
            AssertEx.Equal(10, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "RangeSize"));
            AssertEx.Equal(12, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "EventSize"));
            AssertEx.Equal(14, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "MaxDepth"));
            AssertEx.Equal(15, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "MaxOpcodeBytes"));
            AssertEx.Equal(16, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "ResetServiceKind"));
            AssertEx.Equal(18, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "MaxContinuationFrames"));
            AssertEx.Equal(20, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "Flags"));
            AssertEx.Equal(24, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "WatchMaskBytes"));
            AssertEx.Equal(28, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "HookCount"));
            AssertEx.Equal(32, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "RangeCount"));
            AssertEx.Equal(36, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "SnapshotBytesTotal"));
            AssertEx.Equal(40, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "EventCapacity"));
            AssertEx.Equal(44, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "MaxServiceTokensPerFrame"));
            AssertEx.Equal(48, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "KindSize"));
            AssertEx.Equal(50, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "KindCount"));
            AssertEx.Equal(52, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "Reserved0"));
            AssertEx.Equal(56, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "Reserved1"));
            AssertEx.Equal(60, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Config), "Reserved2"));
            AssertEx.Equal(16, Marshal.SizeOf(typeof(GpgxAudioObserverAdapter.ServiceKind)));
            AssertEx.Equal(0, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceKind), "KindId"));
            AssertEx.Equal(1, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceKind), "Flags"));
            AssertEx.Equal(2, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceKind), "CancellationRangeFirst"));
            AssertEx.Equal(4, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceKind), "CancellationRangeCount"));
            AssertEx.Equal(6, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceKind), "ContinuationFrameLimit"));
            AssertEx.Equal(7, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceKind), "Reserved0"));
            AssertEx.Equal(8, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceKind), "Reserved1"));
            AssertEx.Equal(12, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceKind), "Reserved2"));
            AssertEx.Equal(32, Marshal.SizeOf(typeof(GpgxAudioObserverAdapter.ServiceHook)));
            AssertEx.Equal(0, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "HookToken"));
            AssertEx.Equal(2, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "Action"));
            AssertEx.Equal(3, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "Cpu"));
            AssertEx.Equal(4, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "Pc"));
            AssertEx.Equal(8, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "ServiceKindId"));
            AssertEx.Equal(9, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "ExpectedActiveKind"));
            AssertEx.Equal(10, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "Flags"));
            AssertEx.Equal(11, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "OpcodeLength"));
            AssertEx.Equal(16, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "Opcode"));
            AssertEx.Equal(12, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "RangeFirst"));
            AssertEx.Equal(14, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "RangeCount"));
            AssertEx.Equal(24, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.ServiceHook), "Reserved"));
            AssertEx.Equal(16, Marshal.SizeOf(typeof(GpgxAudioObserverAdapter.SnapshotRange)));
            AssertEx.Equal(0, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.SnapshotRange), "RangeId"));
            AssertEx.Equal(2, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.SnapshotRange), "Start"));
            AssertEx.Equal(4, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.SnapshotRange), "Length"));
            AssertEx.Equal(6, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.SnapshotRange), "Flags"));
            AssertEx.Equal(8, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.SnapshotRange), "Reserved0"));
            AssertEx.Equal(12, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.SnapshotRange), "Reserved1"));
            AssertEx.Equal(32, Marshal.SizeOf(typeof(GpgxAudioObserverAdapter.Event)));
            AssertEx.Equal(0, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Ordinal"));
            AssertEx.Equal(4, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "ServiceToken"));
            AssertEx.Equal(6, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "ParentToken"));
            AssertEx.Equal(8, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Pc"));
            AssertEx.Equal(12, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Subject"));
            AssertEx.Equal(14, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Offset"));
            AssertEx.Equal(16, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Kind"));
            AssertEx.Equal(17, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "ServiceKindId"));
            AssertEx.Equal(18, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Depth"));
            AssertEx.Equal(19, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "SourceCpu"));
            AssertEx.Equal(20, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "PayloadLength"));
            AssertEx.Equal(21, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Value"));
            AssertEx.Equal(22, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Flags"));
            AssertEx.Equal(23, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Reserved"));
            AssertEx.Equal(24, (int)Marshal.OffsetOf(typeof(GpgxAudioObserverAdapter.Event), "Payload"));
            var vector = new GpgxAudioObserverAdapter.Event
                { Ordinal = 0x04030201, ServiceToken = 0x0605, Pc = 0x0C0B0A09, Payload = 0x18 };
            IntPtr vectorPointer = Marshal.AllocHGlobal(32);
            try
            {
                Marshal.StructureToPtr(vector, vectorPointer, false);
                var vectorBytes = new byte[32]; Marshal.Copy(vectorPointer, vectorBytes, 0, 32);
                AssertEx.Equal((byte)1, vectorBytes[0]); AssertEx.Equal((byte)4, vectorBytes[3]);
                AssertEx.Equal((byte)5, vectorBytes[4]); AssertEx.Equal((byte)6, vectorBytes[5]);
                AssertEx.Equal((byte)9, vectorBytes[8]); AssertEx.Equal((byte)12, vectorBytes[11]);
                AssertEx.Equal((byte)0x18, vectorBytes[24]);
            }
            finally { Marshal.FreeHGlobal(vectorPointer); }
            using (var host = GpgxHost.Open(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                GpgxHost.CreateGhz1SyncSettings()))
            {
                GpgxAudioObserverAdapter adapter = host.CreateAudioObserverAdapter();
                AssertEx.Equal(1u, adapter.AbiVersion());
                AssertEx.Equal(32u, adapter.EventSize());
                AssertEx.Equal(65536u, adapter.Capacity());
                var config = new GpgxAudioObserverAdapter.Config
                {
                    Magic = 0x31544147, AbiVersion = 1, StructSize = 64, KindSize = 16,
                    HookSize = 32, RangeSize = 16, EventSize = 32, MaxDepth = 8,
                    MaxOpcodeBytes = 8, ResetServiceKind = 1, WatchMaskBytes = 8192,
                    KindCount = 1, HookCount = 1, RangeCount = 1,
                    SnapshotBytesTotal = 1, MaxServiceTokensPerFrame = 65535,
                    EventCapacity = 65536
                };
                var mask = new byte[8192];
                var kinds = new[] { new GpgxAudioObserverAdapter.ServiceKind
                    { KindId = 1, CancellationRangeCount = 1 } };
                var hooks = new[] { new GpgxAudioObserverAdapter.ServiceHook
                    { HookToken = 1, Action = 1, ServiceKindId = 1, Cpu = 2,
                      OpcodeLength = 1, Opcode = 0, Pc = 0xFFFFFF } };
                var ranges = new[] { new GpgxAudioObserverAdapter.SnapshotRange
                    { RangeId = 1, Start = 0, Length = 1 } };
                AssertEx.Throws<ArgumentException>(() => adapter.Configure(
                    ref config, new byte[8191], kinds, hooks, ranges), "length");
                AssertEx.Throws<ArgumentException>(() => adapter.Configure(
                    ref config, new byte[8193], kinds, hooks, ranges), "length");
                AssertEx.Throws<ArgumentException>(() => adapter.Configure(
                    ref config, mask, new GpgxAudioObserverAdapter.ServiceKind[0], hooks, ranges), "length");
                AssertEx.Throws<ArgumentException>(() => adapter.Configure(
                    ref config, mask, new GpgxAudioObserverAdapter.ServiceKind[2], hooks, ranges), "length");
                AssertEx.Throws<ArgumentException>(() => adapter.Configure(
                    ref config, mask, kinds, new GpgxAudioObserverAdapter.ServiceHook[0], ranges), "length");
                AssertEx.Throws<ArgumentException>(() => adapter.Configure(
                    ref config, mask, kinds, new GpgxAudioObserverAdapter.ServiceHook[2], ranges), "length");
                AssertEx.Throws<ArgumentException>(() => adapter.Configure(
                    ref config, mask, kinds, hooks, new GpgxAudioObserverAdapter.SnapshotRange[0]), "length");
                AssertEx.Throws<ArgumentException>(() => adapter.Configure(
                    ref config, mask, kinds, hooks, new GpgxAudioObserverAdapter.SnapshotRange[2]), "length");
                AssertEx.Equal(0, adapter.Configure(ref config, mask, kinds, hooks, ranges));
                Array.Clear(mask, 0, mask.Length); kinds[0] = new GpgxAudioObserverAdapter.ServiceKind();
                hooks[0] = new GpgxAudioObserverAdapter.ServiceHook();
                ranges[0] = new GpgxAudioObserverAdapter.SnapshotRange();
                config = new GpgxAudioObserverAdapter.Config();
                AssertEx.Equal(0, adapter.BeginFrame());
                host.SetButton("Reset", true); host.Advance(); host.ClearButtons();
                AssertEx.Equal(0, adapter.EndFrame());
                uint count, overflow;
                AssertEx.Equal(0, adapter.EventCount(out count, out overflow));
                AssertEx.Equal(5u, count); AssertEx.Equal(0u, overflow);
                var events = new GpgxAudioObserverAdapter.Event[5];
                AssertEx.Throws<ArgumentException>(() =>
                {
                    uint ignored;
                    adapter.Drain(new GpgxAudioObserverAdapter.Event[4], 5, out ignored);
                }, "shorter");
                AssertEx.Equal(0, adapter.Drain(events, 5, out count));
                AssertEx.Equal((byte)8, events[0].Kind);
                AssertEx.Equal((byte)1, events[0].ServiceKindId);
                AssertEx.Equal((byte)3, events[0].SourceCpu);
                AssertEx.Equal(0u, events[0].Pc);
                AssertEx.Equal((byte)5, events[1].Kind);
                AssertEx.Equal((ushort)1, events[1].Subject);
                AssertEx.Equal((byte)6, events[2].Kind);
                AssertEx.Equal((ushort)1, events[2].Subject);
                AssertEx.Equal((ushort)0, events[2].Offset);
                AssertEx.Equal((byte)1, events[2].PayloadLength);
                AssertEx.Equal((byte)0, (byte)events[2].Payload);
                AssertEx.Equal((byte)7, events[3].Kind);
                AssertEx.Equal((ushort)1, events[3].Subject);
                AssertEx.Equal((ushort)1, events[3].Offset);
                AssertEx.Equal((byte)9, events[4].Kind);
                AssertEx.Equal(events[0].ServiceToken, events[4].ServiceToken);
                for (int i = 0; i < events.Length; i++)
                {
                    AssertEx.Equal((ushort)0, events[i].ParentToken);
                    AssertEx.Equal((byte)0, events[i].Depth);
                    AssertEx.Equal((byte)3, events[i].SourceCpu);
                    AssertEx.Equal((byte)0, events[i].Flags);
                }
                AssertEx.Equal(0, adapter.BeginFrame());
                byte[] state = host.CloneSavestate();
                AssertEx.Equal(0, adapter.AbortFrame());
                host.LoadSavestate(state);
                AssertEx.Equal(0, adapter.BeginFrame());
                AssertEx.Equal(0, adapter.EndFrame());
                AssertEx.Equal(0, adapter.Drain(null, 0, out count));
                AssertEx.Equal(0, adapter.BeginFrame());
                AssertEx.Equal(0, adapter.EndFrame());
                AssertEx.Equal(0, adapter.Drain(new GpgxAudioObserverAdapter.Event[0], 0, out count));
                AssertEx.Equal(0, adapter.BeginFrame());
                AssertEx.Equal(0, adapter.AbortFrame());
                AssertEx.Equal(0, adapter.Disable());
                AssertEx.Equal(0, adapter.Disable());
            }
        }

        private static void PatchedCoreBootsAllGenesisFmSelectors()
        {
            for (int selector = 0; selector < 5; selector++)
            {
                GPGX.GPGXSyncSettings settings = GpgxHost.CreateGhz1SyncSettings();
                settings.GenesisFMSoundChip =
                    (LibGPGX.InitSettings.GenesisFMSoundChipType)selector;
                using (var host = GpgxHost.Open(
                    Environment.GetEnvironmentVariable("S1_ROM_PATH"), settings))
                {
                    host.Advance();
                    AssertEx.Equal(1, host.CompletedFrame);
                }
            }
        }
    }
}
