using System;
using System.Collections.Generic;
using System.IO;
using BizHawk.Emulation.Common;
using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;

namespace OpenGGF.BizHawk.Headless
{
    public sealed class GpgxHost : IGpgxHost
    {
        private readonly GPGX core;
        private readonly MutableController controller;
        private readonly MemoryDomain mainRam;
        private readonly IMemoryCallbackSystem memoryCallbacks;
        private readonly List<ExecuteCallbackRegistration>
            executeCallbackRegistrations =
                new List<ExecuteCallbackRegistration>();
        private Exception pendingExecuteCallbackException;
        private bool disposed;

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
            IDebuggable debugger =
                core.ServiceProvider.GetService<IDebuggable>();
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
                Name = traceGame == "s2"
                    ? "Sonic The Hedgehog 2"
                    : "Sonic The Hedgehog",
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
            return mainRam.PeekByte(offset);
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
