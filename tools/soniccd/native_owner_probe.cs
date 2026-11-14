// Private CD-02 owner-ledger controls, 2026-10-23. No display or audio device.
// Reuses only the frozen oracle's exact-hash firmware/media adapter classes.
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using BizHawk.BizInvoke;
using BizHawk.Emulation.Common;
using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using BizHawk.Emulation.Cores.Waterbox;
using Newtonsoft.Json;

public abstract class NativeOwnerExports
{
    [BizImport(CallingConvention.Cdecl)] public abstract uint native_owner_abi_version();
    [BizImport(CallingConvention.Cdecl)] public abstract uint native_owner_event_size();
    [BizImport(CallingConvention.Cdecl)] public abstract uint native_owner_capacity();
    [BizImport(CallingConvention.Cdecl)] public abstract uint native_owner_fault();
    [BizImport(CallingConvention.Cdecl)] public abstract uint native_owner_dropped();
    [BizImport(CallingConvention.Cdecl)] public abstract uint native_owner_count();
    [BizImport(CallingConvention.Cdecl)] public abstract int native_owner_configure(uint enabled, uint limit);
    [BizImport(CallingConvention.Cdecl)] public abstract int native_owner_poll(IntPtr destination, uint bytes);
}

internal sealed class OwnerLedger : IDisposable
{
    public const int Capacity = 8192, EventBytes = 256;
    public readonly NativeOwnerExports Api;
    readonly IntPtr buffer;
    readonly bool enabled, allowOverflow;
    readonly uint limit;
    public bool InvalidApiRejected;
    public OwnerLedger(GPGX core, bool enabled, bool overflow, int expectedBytes)
    {
        this.enabled = enabled; allowOverflow = overflow; limit = overflow ? 1U : Capacity;
        if (IntPtr.Size != 8 || !BitConverter.IsLittleEndian) throw new InvalidOperationException("Require x86-64 little-endian ABI");
        var host = (WaterboxHost)typeof(GPGX).GetField("_elf", BindingFlags.Instance | BindingFlags.NonPublic).GetValue(core);
        // Typed exports go through Waterbox's public ABI adapter and monitor.
        // No raw guest function pointer, callback replacement or address guess.
        var adapter = CallingConventionAdapters.MakeWaterbox(new Delegate[0], host);
        host.Enter();
        try { Api = BizInvoker.GetInvoker<NativeOwnerExports>(host, host, adapter); }
        finally { host.Exit(); }
        if (Api.native_owner_abi_version() != 1 || Api.native_owner_event_size() != expectedBytes
                || expectedBytes != EventBytes || Api.native_owner_capacity() != Capacity)
            throw new InvalidOperationException("Native owner ABI/layout mismatch before polling");
        buffer = Marshal.AllocHGlobal(Capacity * EventBytes);
        Configure();
        uint before = Api.native_owner_count();
        InvalidApiRejected = Api.native_owner_configure(2, Capacity) == -1
            && Api.native_owner_configure(1, 0) == -1
            && Api.native_owner_configure(1, Capacity + 1) == -1
            && Api.native_owner_poll(buffer, Capacity * EventBytes - 4) == -1
            && Api.native_owner_count() == before;
        if (!InvalidApiRejected) throw new InvalidOperationException("Invalid observation API was accepted");
    }
    public void Configure()
    {
        if (Api.native_owner_configure(enabled ? 1U : 0U, limit) != 0)
            throw new InvalidOperationException("Observation configure rejected");
    }
    public byte[] Drain()
    {
        uint expected = Api.native_owner_count();
        int count = Api.native_owner_poll(buffer, Capacity * EventBytes);
        if (count < 0 || count != expected || count > Capacity)
            throw new InvalidOperationException("Observation poll count/size mismatch");
        if (!allowOverflow && (Api.native_owner_fault() != 0 || Api.native_owner_dropped() != 0))
            throw new InvalidOperationException("Native owner ledger overflow: evidence incomplete");
        if (!enabled && count != 0) throw new InvalidOperationException("Disabled observer recorded events");
        byte[] bytes = new byte[count * EventBytes];
        Marshal.Copy(buffer, bytes, 0, bytes.Length);
        return bytes;
    }
    public void Dispose()
    {
        Api.native_owner_configure(0, Capacity);
        if (buffer != IntPtr.Zero) Marshal.FreeHGlobal(buffer);
    }
}

internal static class NativeOwner
{
    internal static string Hash(byte[] bytes) { return Probe.Hash(bytes, SHA256.Create()); }
    static object Advance(GPGX core, Pad pad, int frame, HashSet<int> starts)
    {
        pad.Start = starts.Contains(frame);
        core.FrameAdvance(pad, true, true);
        short[] audio; int samples;
        core.GetSamplesSync(out audio, out samples);
        byte[] bytes = new byte[samples * 4];
        Buffer.BlockCopy(audio, 0, bytes, 0, bytes.Length);
        byte[] pixels = new byte[core.GetVideoBuffer().Length * 4];
        Buffer.BlockCopy(core.GetVideoBuffer(), 0, pixels, 0, pixels.Length);
        return new { frame = core.Frame, start = pad.Start, lag = core.IsLagFrame,
            audio_samples = samples, audio_sha256 = Hash(bytes), video_sha256 = Hash(pixels) };
    }
    static object View(GPGX core, IMemoryDomains domains)
    {
        // This is the public memory-domain contract, not a masked savestate.
        // Handler-backed CPU buses are deliberately excluded; reads can be I/O.
        // Waterbox pages contain loader/code/pointer identity across binaries.
        var hashes = new SortedDictionary<string, string>(StringComparer.Ordinal);
        foreach (var d in domains.Where(d => !d.Name.EndsWith(" BUS", StringComparison.Ordinal)
                    && !d.Name.StartsWith("Waterbox ", StringComparison.Ordinal))) {
            byte[] data = new byte[checked((int)d.Size)];
            for (int i = 0; i < data.Length; i++) data[i] = d.PeekByte(i);
            hashes.Add(d.Name, Hash(data));
        }
        return new { frame = core.Frame, lag_count = core.LagCount, domains = hashes,
            registers = core.GetCpuFlagsAndRegisters().OrderBy(p => p.Key, StringComparer.Ordinal)
                .ToDictionary(p => p.Key, p => p.Value.Value) };
    }
    static List<string> Continue(GPGX core, Pad pad, int from, HashSet<int> starts, OwnerLedger ledger, string eventPath)
    {
        var rows = new List<string>();
        if (ledger != null) ledger.Configure();
        using (var events = File.Create(eventPath))
        for (int frame = from + 1; frame <= from + 120; frame++) {
            rows.Add(JsonConvert.SerializeObject(Advance(core, pad, frame, starts)));
            if (ledger != null) {
                byte[] bytes = ledger.Drain();
                events.Write(bytes, 0, bytes.Length);
            }
        }
        return rows;
    }
    public static int Main(string[] args)
    {
        try {
            if (args.Length != 8) throw new ArgumentException("cue bios-dir output frames starts bios-sha1 expected-core-sha256 mode");
            string dll = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "dll");
            var pins = new Dictionary<string, string> {
                {"gpgx.wbx.zst", args[6]},
                {"libwaterboxhost.so", "d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78"},
                {"BizHawk.Emulation.Cores.dll", "0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7"}
            };
            foreach (var pin in pins)
                if (Hash(File.ReadAllBytes(Path.Combine(dll, pin.Key))) != pin.Value)
                    throw new InvalidOperationException("Native owner runtime identity mismatch: " + pin.Key);
            if (typeof(GPGX).Assembly.GetName().Version != new Version(2, 11, 0, 0))
                throw new InvalidOperationException("Require BizHawk 2.11");
            int count = int.Parse(args[3]);
            var starts = new HashSet<int>(args[4].Split(new[]{','}, StringSplitOptions.RemoveEmptyEntries).Select(int.Parse));
            string mode = args[7];
            if (count < 1 || count > 3600 || starts.Any(f => f < 1 || f > count)
                    || !new[]{"control", "disabled", "enabled", "overflow", "bad-layout"}.Contains(mode))
                throw new ArgumentException("Invalid bounded scenario or mode");
            if (Directory.Exists(args[2])) throw new IOException("Output already exists");
            var fw = new Firmware(args[1], args[5]);
            var disc = BizHawk.Emulation.DiscSystem.Disc.LoadAutomagic(args[0]);
            using (var core = new GPGX(new CoreLoadParameters<GPGX.GPGXSettings, GPGX.GPGXSyncSettings> {
                Comm = new CoreComm(m => Console.WriteLine(m), (m, n) => {}, fw, CoreComm.CorePreferencesFlags.None, null),
                Game = new GameInfo { Name = "Sonic CD private owner-ledger", System = "GEN" },
                Settings = new GPGX.GPGXSettings(), SyncSettings = new GPGX.GPGXSyncSettings(),
                Discs = { new CdAsset { DiscData = disc, DiscName = Path.GetFileName(args[0]) } },
                DeterministicEmulationRequested = true
            })) {
                using (var ledger = mode == "control" ? null : new OwnerLedger(core,
                        mode != "disabled", mode == "overflow", mode == "bad-layout" ? 252 : 256)) {
                Directory.CreateDirectory(args[2]);
                var domains = core.ServiceProvider.GetService<IMemoryDomains>();
                var pad = new Pad { Definition = core.ControllerDefinition };
                using (var av = new StreamWriter(Path.Combine(args[2], "av.jsonl")))
                using (var views = new StreamWriter(Path.Combine(args[2], "views.jsonl")))
                using (var events = File.Create(Path.Combine(args[2], "events.bin")))
                using (var counts = new StreamWriter(Path.Combine(args[2], "counts.jsonl"))) {
                    for (int frame = 1; frame <= count; frame++) {
                        av.WriteLine(JsonConvert.SerializeObject(Advance(core, pad, frame, starts)));
                        if (ledger != null) {
                            byte[] bytes = ledger.Drain();
                            events.Write(bytes, 0, bytes.Length);
                            counts.WriteLine(JsonConvert.SerializeObject(new { frame, events = bytes.Length / OwnerLedger.EventBytes,
                                fault = ledger.Api.native_owner_fault(), dropped = ledger.Api.native_owner_dropped() }));
                        }
                        if (frame % 120 == 0 || frame == count)
                            views.WriteLine(JsonConvert.SerializeObject(View(core, domains)));
                    }
                }
                byte[] checkpoint = ((IStatable)core).CloneSavestate();
                File.WriteAllBytes(Path.Combine(args[2], "checkpoint.state"), checkpoint);
                var checkpointView = View(core, domains);
                uint overflowFault = ledger == null ? 0U : ledger.Api.native_owner_fault();
                uint overflowDropped = ledger == null ? 0U : ledger.Api.native_owner_dropped();
                for (int i = 0; ledger != null && i < 20; i++) { ledger.Drain(); ledger.Configure(); }
                bool observationUnchanged = checkpoint.SequenceEqual(((IStatable)core).CloneSavestate());
                var forward = Continue(core, pad, count, starts, ledger, Path.Combine(args[2], "continuation-forward.bin"));
                string forwardView = JsonConvert.SerializeObject(View(core, domains));
                ((IStatable)core).LoadStateBinary(checkpoint);
                core.DiscardSamples();
                GC.Collect(); GC.WaitForPendingFinalizers(); GC.Collect();
                var replay = Continue(core, pad, count, starts, ledger, Path.Combine(args[2], "continuation-events.bin"));
                string replayView = JsonConvert.SerializeObject(View(core, domains));
                bool eventRestored = Hash(File.ReadAllBytes(Path.Combine(args[2], "continuation-forward.bin")))
                    == Hash(File.ReadAllBytes(Path.Combine(args[2], "continuation-events.bin")));
                bool restored = forward.SequenceEqual(replay) && forwardView == replayView && eventRestored;
                File.WriteAllLines(Path.Combine(args[2], "continuation.jsonl"), replay);
                File.WriteAllText(Path.Combine(args[2], "summary.json"), JsonConvert.SerializeObject(new {
                    frames = count, mode = args[7], is_mega_cd = core.IsMegaCD, firmware = fw.Selected,
                    core_sha256 = args[6], fps_num = core.VsyncNumerator, fps_den = core.VsyncDenominator,
                    input_start_frames = starts.OrderBy(f => f), checkpoint_bytes = checkpoint.Length,
                    checkpoint_sha256 = Hash(checkpoint), checkpoint_view = checkpointView,
                    final_view = JsonConvert.DeserializeObject(replayView),
                    restore_120_frames_equal = restored, observation_savestate_unchanged = observationUnchanged,
                    event_restore_equal = eventRestored, invalid_abi_calls_rejected = ledger == null ? (bool?)null : ledger.InvalidApiRejected,
                    overflow_fault = overflowFault, overflow_dropped = overflowDropped,
                    display = (string)null, audio_device = (string)null
                }, Formatting.Indented));
                if (!restored || !observationUnchanged) throw new InvalidOperationException("Native control failed");
                if (mode == "overflow" && (overflowFault != 1 || overflowDropped == 0))
                    throw new InvalidOperationException("Overflow negative control failed to overflow");
                }
            }
            return 0;
        } catch (Exception e) { Console.Error.WriteLine(e); return 1; }
    }
}
