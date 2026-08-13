using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using BizHawk.Emulation.Common;
using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;

namespace OpenGGF.BizHawk.Headless
{
    public sealed partial class GpgxHost : IGpgxHost, ICpuRegisterReader, IMainRamWriter
    {
        private readonly GPGX core;
        private readonly MutableController controller;
        private readonly MemoryDomain mainRam;
        private readonly MemoryDomain z80Ram;
        private readonly IMemoryCallbackSystem memoryCallbacks;
        private readonly IDebuggable debugger;
        private readonly IVideoProvider videoProvider;
        private readonly ISoundProvider soundProvider;
        private readonly List<ExecuteCallbackRegistration>
            executeCallbackRegistrations =
                new List<ExecuteCallbackRegistration>();
        private Exception pendingExecuteCallbackException;
        private bool disposed;
        internal int LastCheckpointVideoLength { get; private set; }
        internal int LastCheckpointAudioFrames { get; private set; }

        private GpgxHost(GPGX core)
        {
            this.core = core;
            controller = new MutableController(core.ControllerDefinition);
            IMemoryDomains memoryDomains =
                core.ServiceProvider.GetService<IMemoryDomains>();
            // BizHawk 2.11 GPGX names the Genesis work-RAM domain "68K RAM".
            // A source-backed future API may use "Main RAM", so retain it only
            // as the compatibility fallback after the pinned-runtime name.
            mainRam = memoryDomains["68K RAM"] ?? memoryDomains["Main RAM"];
            if (mainRam == null)
            {
                core.Dispose();
                throw new InvalidOperationException(
                    "GPGX did not expose the required 68K RAM memory domain "
                    + "(Main RAM is a compatibility fallback). Available: "
                    + string.Join(", ", memoryDomains));
            }
            if (mainRam.Size != 65536L)
            {
                core.Dispose();
                throw new InvalidOperationException(
                    "GPGX memory domain '" + mainRam.Name + "' has size "
                    + mainRam.Size + "; expected exactly 65536 bytes.");
            }
            z80Ram = memoryDomains["Z80 RAM"];
            if (z80Ram == null || z80Ram.Size != 8192L)
            {
                core.Dispose();
                throw new InvalidOperationException("GPGX did not expose the exact 8192-byte Z80 RAM domain.");
            }
            debugger =
                core.ServiceProvider.GetService<IDebuggable>();
            videoProvider = core.ServiceProvider.GetService<IVideoProvider>();
            soundProvider = core.ServiceProvider.GetService<ISoundProvider>();
            if (debugger == null
                || !debugger.MemoryCallbacks.ExecuteCallbacksAvailable)
            {
                core.Dispose();
                throw new InvalidOperationException(
                    "GPGX did not expose required M68K execute callbacks.");
            }
            memoryCallbacks = debugger.MemoryCallbacks;
            bool hasM68kBus = false;
            foreach (string scope in memoryCallbacks.AvailableScopes)
            {
                if (scope == "M68K BUS")
                {
                    hasM68kBus = true;
                    break;
                }
            }
            if (!hasM68kBus)
            {
                core.Dispose();
                throw new InvalidOperationException(
                    "GPGX execute callbacks do not expose M68K BUS.");
            }
        }

        public int CompletedFrame
        {
            get { return core.Frame; }
        }

        public bool IsLagged
        {
            get { return ((IInputPollable)core).IsLagFrame; }
        }

        public int LagCount
        {
            get { return ((IInputPollable)core).LagCount; }
        }

        public string MainRamDomainName
        {
            get { return mainRam.Name; }
        }

        public long MainRamDomainSize
        {
            get { return mainRam.Size; }
        }

        internal IGpgxAudioTraceApi CreateAudioTraceApi()
        {
            if (disposed) throw new ObjectDisposedException("GpgxHost");
            return new GpgxAudioTraceNative(new GpgxAudioObserverAdapter(core));
        }

        internal byte[] CloneSavestate()
        {
            if (disposed) throw new ObjectDisposedException("GpgxHost");
            return ((IStatable)core).CloneSavestate();
        }

        internal byte ReadZ80RamByte(int offset)
        {
            if (offset < 0 || offset >= z80Ram.Size) throw new ArgumentOutOfRangeException("offset");
            return z80Ram.PeekByte(offset);
        }

        internal byte[] CaptureDeterministicCheckpoint()
        {
            if (disposed) throw new ObjectDisposedException("GpgxHost");
            using (var stream = new MemoryStream())
            using (var writer = new BinaryWriter(stream))
            {
                writer.Write(core.Frame); writer.Write(IsLagged); writer.Write(LagCount);
                for (long i = 0; i < mainRam.Size; i++) writer.Write(mainRam.PeekByte(i));
                for (long i = 0; i < z80Ram.Size; i++) writer.Write(z80Ram.PeekByte(i));
                foreach (var register in debugger.GetCpuFlagsAndRegisters().OrderBy(x => x.Key, StringComparer.Ordinal))
                { writer.Write(register.Key); writer.Write(register.Value.Value); }
                int[] video = videoProvider.GetVideoBuffer();
                LastCheckpointVideoLength = video.Length;
                writer.Write(video.Length); for (int i = 0; i < video.Length; i++) writer.Write(video[i]);
                short[] samples; int count; soundProvider.GetSamplesSync(out samples, out count);
                LastCheckpointAudioFrames = count;
                writer.Write(count); for (int i = 0; i < count * 2; i++) writer.Write(samples[i]);
                writer.Flush(); return stream.ToArray();
            }
        }

        internal void LoadSavestate(byte[] state)
        {
            if (disposed) throw new ObjectDisposedException("GpgxHost");
            ((IStatable)core).LoadStateBinary(state);
            soundProvider.DiscardSamples();
        }

        internal void LoadSavestate(byte[] state, CompleteRunAudioObserver observer,
            CompleteRunAudioObserver.Checkpoint checkpoint)
        {
            if (observer == null) throw new ArgumentNullException("observer");
            observer.ValidateCheckpoint(checkpoint);
            LoadSavestate(state);
            observer.ApplyCheckpoint(checkpoint);
        }

        public static GpgxHost Open(
            string romPath,
            GPGX.GPGXSyncSettings syncSettings)
        {
            if (string.IsNullOrEmpty(romPath))
            {
                throw new ArgumentException("A ROM path is required.", "romPath");
            }
            if (syncSettings == null)
            {
                throw new ArgumentNullException("syncSettings");
            }

            byte[] romBytes = File.ReadAllBytes(romPath);
            // DetectGame validates the ROM is one of the supported trace
            // ROMs (S1/S2 World REV01) and picks the GameInfo name.
            string traceGame = RomIdentity.DetectGame(romBytes);
            string romSha1 = RomIdentity.ComputeSha1(romBytes);
            var game = new GameInfo
            {
                Name = ResolveManagedGameName(traceGame),
                System = VSystemID.Raw.GEN,
                Hash = romSha1
            };
            var comm = new CoreComm(
                _ => { },
                (_, __) => { },
                new NoFirmwareProvider(),
                CoreComm.CorePreferencesFlags.None,
                null);
            var core = new GPGX(
                new CoreLoadParameters<GPGX.GPGXSettings, GPGX.GPGXSyncSettings>
                {
                    Comm = comm,
                    Game = game,
                    Settings = new GPGX.GPGXSettings(),
                    SyncSettings = syncSettings,
                    Roms = { new RomAsset(romBytes, romPath, game) },
                    DeterministicEmulationRequested = true
                });
            return new GpgxHost(core);
        }

        internal static string ResolveManagedGameName(string traceGame)
        {
            if (traceGame == "s1") return "Sonic The Hedgehog";
            if (traceGame == "s2") return "Sonic The Hedgehog 2";
            if (traceGame == "s3k") return "Sonic 3 & Knuckles";
            throw new InvalidOperationException("Unsupported trace game identity: " + traceGame);
        }

        public static GPGX.GPGXSyncSettings CreateGhz1SyncSettings()
        {
            return new GPGX.GPGXSyncSettings
            {
                UseSixButton = false,
                ControlTypeLeft = GPGX.ControlType.Normal,
                ControlTypeRight = GPGX.ControlType.Normal,
                Region = LibGPGX.Region.Autodetect,
                ForceVDP = LibGPGX.ForceVDP.Disabled,
                LoadBIOS = false,
                Overscan = LibGPGX.InitSettings.OverscanType.All,
                GGExtra = false,
                SMSFMSoundChip =
                    LibGPGX.InitSettings.SMSFMSoundChipType.YM2413_MAME,
                GenesisFMSoundChip =
                    LibGPGX.InitSettings.GenesisFMSoundChipType.MAME_YM2612,
                Filter = LibGPGX.InitSettings.FilterType.None,
                LowPassRange = 26214,
                LowFreq = 880,
                HighFreq = 5000,
                LowGain = 1.0f,
                MidGain = 1.0f,
                HighGain = 1.0f,
                BackdropColor = 4294902015u,
                SpritesAlwaysOnTop = false
            };
        }

        public void ClearButtons()
        {
            controller.Clear();
        }

        public void SetButton(string name, bool pressed)
        {
            controller.Set(name, pressed);
        }

        public void Advance()
        {
            core.FrameAdvance(controller, false, false);
            if (pendingExecuteCallbackException != null)
            {
                Exception callbackFailure =
                    pendingExecuteCallbackException;
                pendingExecuteCallbackException = null;
                throw new InvalidOperationException(
                    "An M68K execute callback failed during FrameAdvance: "
                    + callbackFailure.Message,
                    callbackFailure);
            }
        }

        public IDisposable RegisterExecuteCallback(
            uint address, Action callback)
        {
            if (callback == null)
            {
                throw new ArgumentNullException("callback");
            }
            if (disposed)
            {
                throw new ObjectDisposedException("GpgxHost");
            }

            MemoryCallbackDelegate rootedDelegate =
                (callbackAddress, value, flags) =>
                {
                    try
                    {
                        callback();
                    }
                    catch (Exception exception)
                    {
                        if (pendingExecuteCallbackException == null)
                        {
                            pendingExecuteCallbackException = exception;
                        }
                    }
                    return null;
                };
            var memoryCallback = new MemoryCallback(
                "M68K BUS",
                MemoryCallbackType.Execute,
                "OpenGGF hardware-timing submission observer",
                rootedDelegate,
                address,
                null);
            memoryCallbacks.Add(memoryCallback);
            var registration = new ExecuteCallbackRegistration(
                this, rootedDelegate, callback);
            executeCallbackRegistrations.Add(registration);
            return registration;
        }

        public byte ReadMainRamByte(int offset)
        {
            CheckMainRamOffset(offset);
            return mainRam.PeekByte(offset);
        }

        public void WriteMainRamByte(int offset, byte value)
        {
            CheckMainRamOffset(offset);
            mainRam.PokeByte(offset, value);
        }

        private void CheckMainRamOffset(int offset)
        {
            if (offset < 0 || offset >= mainRam.Size)
            {
                throw new ArgumentOutOfRangeException(
                    "offset", "Main RAM offset must be within the 68K RAM domain.");
            }
        }

        public uint ReadCpuRegister(string name)
        {
            IDictionary<string, RegisterValue> registers =
                debugger.GetCpuFlagsAndRegisters();
            RegisterValue value;
            if (!registers.TryGetValue(name, out value))
            {
                throw new InvalidOperationException(
                    "GPGX did not expose CPU register '" + name + "'.");
            }
            return (uint)value.Value;
        }

        public void Dispose()
        {
            if (disposed)
            {
                return;
            }
            disposed = true;
            while (executeCallbackRegistrations.Count != 0)
            {
                executeCallbackRegistrations[
                    executeCallbackRegistrations.Count - 1].Dispose();
            }
            core.Dispose();
        }

        private void UnregisterExecuteCallback(
            ExecuteCallbackRegistration registration,
            MemoryCallbackDelegate callback)
        {
            memoryCallbacks.Remove(callback);
            executeCallbackRegistrations.Remove(registration);
        }

        private sealed class ExecuteCallbackRegistration : IDisposable
        {
            private GpgxHost owner;
            private readonly MemoryCallbackDelegate callback;

            // Strongly root the user callback independently of the native
            // interop delegate for the full registration lifetime.
            private readonly Action observedAction;

            public ExecuteCallbackRegistration(
                GpgxHost owner,
                MemoryCallbackDelegate callback,
                Action observedAction)
            {
                this.owner = owner;
                this.callback = callback;
                this.observedAction = observedAction;
            }

            public void Dispose()
            {
                if (owner == null)
                {
                    return;
                }
                GpgxHost registeredOwner = owner;
                owner = null;
                registeredOwner.UnregisterExecuteCallback(this, callback);
                GC.KeepAlive(observedAction);
            }
        }
    }
}
