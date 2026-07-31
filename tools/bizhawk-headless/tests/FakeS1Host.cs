using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal sealed class NoOpCallbackRegistration : IDisposable
    {
        public static readonly NoOpCallbackRegistration Instance =
            new NoOpCallbackRegistration();

        private NoOpCallbackRegistration()
        {
        }

        public void Dispose()
        {
        }
    }

    /// <summary>
    /// Fake S1 host whose Advance() stamps the completed frame into vfc
    /// (0xFE04), then runs the per-advance script with the host itself
    /// so scripts drive RAM, the lag flag and the cumulative lag count
    /// by completed-frame number. Player position is script-controlled
    /// (never auto-stamped) so aux expectations stay byte-exact. Shared
    /// by the S1 run/complete-run runner tests and the CLI tests.
    /// </summary>
    internal sealed class FakeS1Host : IGpgxHost, ICpuRegisterReader
    {
        private readonly Action<FakeS1Host, int> onAdvance;
        private readonly Dictionary<uint, Action> executeCallbacks =
            new Dictionary<uint, Action>();
        private readonly Dictionary<string, uint> registers =
            new Dictionary<string, uint>(StringComparer.Ordinal);

        public FakeS1Host(Action<FakeS1Host, int> onAdvance)
        {
            this.onAdvance = onAdvance;
            Ram = new byte[0x10000];
        }

        public byte[] Ram { get; private set; }
        public int CompletedFrame { get; private set; }
        public bool IsLagged { get; set; }
        public int LagCount { get; set; }
        public uint? ExecuteCallbackAddress { get; private set; }
        public bool ExecuteCallbackDisposed { get; private set; }

        public void ClearButtons()
        {
        }

        public void SetButton(string name, bool pressed)
        {
        }

        public IDisposable RegisterExecuteCallback(
            uint address, Action callback)
        {
            ExecuteCallbackAddress = address;
            ExecuteCallbackDisposed = false;
            executeCallbacks[address] = callback;
            return new CallbackRegistration(this, address, callback);
        }

        public void FireExecuteCallback()
        {
            if (!ExecuteCallbackAddress.HasValue)
            {
                throw new InvalidOperationException(
                    "No execute callback is registered.");
            }
            FireExecuteCallback(ExecuteCallbackAddress.Value);
        }

        public void FireExecuteCallback(uint address)
        {
            Action callback;
            if (!executeCallbacks.TryGetValue(address, out callback))
            {
                throw new InvalidOperationException(
                    "No execute callback is registered at 0x"
                    + address.ToString("X") + ".");
            }
            callback();
        }

        public void Advance()
        {
            CompletedFrame++;
            SetU16(0xFE04, (ushort)CompletedFrame);
            if (onAdvance != null)
            {
                onAdvance(this, CompletedFrame);
            }
        }

        public byte ReadMainRamByte(int offset)
        {
            return Ram[offset];
        }

        public uint ReadCpuRegister(string name)
        {
            uint value;
            return registers.TryGetValue(name, out value) ? value : 0;
        }

        public void SetCpuRegister(string name, uint value)
        {
            registers[name] = value;
        }

        public void Dispose()
        {
        }

        public void SetU16(int offset, ushort value)
        {
            Ram[offset] = (byte)(value >> 8);
            Ram[offset + 1] = (byte)value;
        }

        public void SetU32(int offset, uint value)
        {
            Ram[offset] = (byte)(value >> 24);
            Ram[offset + 1] = (byte)(value >> 16);
            Ram[offset + 2] = (byte)(value >> 8);
            Ram[offset + 3] = (byte)value;
        }

        private sealed class CallbackRegistration : IDisposable
        {
            private FakeS1Host owner;
            private readonly uint address;
            private readonly Action callback;

            public CallbackRegistration(
                FakeS1Host owner, uint address, Action callback)
            {
                this.owner = owner;
                this.address = address;
                this.callback = callback;
            }

            public void Dispose()
            {
                if (owner == null)
                {
                    return;
                }
                Action registered;
                if (owner.executeCallbacks.TryGetValue(
                    address, out registered)
                    && registered == callback)
                {
                    owner.executeCallbacks.Remove(address);
                }
                owner.ExecuteCallbackDisposed = true;
                owner = null;
            }
        }
    }
}
