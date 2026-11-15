// CD-02 service-boundary research, 2026-10-23. Reuses the frozen oracle and device observer.
// Inputs are a pinned official core, user media, layout and authored controller intervals.
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

internal sealed class ServicePad : IController
{
    public ControllerDefinition Definition { get; set; }
    public readonly HashSet<string> Buttons = new HashSet<string>();
    public bool IsPressed(string name) { return name.StartsWith("P1 ", StringComparison.Ordinal) && Buttons.Contains(name.Substring(3)); }
    public int AxisValue(string name) { return 0; }
    public IReadOnlyCollection<(string Name, int Strength)> GetHapticsSnapshot() { return new (string Name, int Strength)[0]; }
    public void SetHapticChannelStrength(string name, int strength) { }
}

internal sealed class ServiceInput
{
    public int first { get; set; }
    public int last { get; set; }
    public string button { get; set; }
}

internal sealed class ServiceCallbacks : IDisposable
{
    readonly GPGX core;
    readonly LibGPGX native;
    readonly LibGPGX observingNative;
    readonly NativeDevices devices;
    readonly LibGPGX.cd_read_cb originalDisc, wrappedDisc;
    readonly LibGPGX.CDCallback logger;
    readonly MemoryCallbackDelegate read, write;
    readonly Dictionary<uint, uint> lastRead = new Dictionary<uint, uint>();
    readonly bool cdl;
    public readonly List<string> Rows = new List<string>();
    public readonly SortedDictionary<string, long> CdlCounts = new SortedDictionary<string, long>();
    public int FrameCall, Ordinal;
    public long Reads, Writes, SuppressedReads, DiscBefore, DiscOriginal, DiscAfter;
    public string Failure;
    bool attached;

    public ServiceCallbacks(GPGX core, NativeDevices devices, bool cdl)
    {
        this.core = core; this.devices = devices; this.cdl = cdl;
        native = (LibGPGX)typeof(GPGX).GetField("Core", BindingFlags.Instance | BindingFlags.NonPublic).GetValue(core);
        originalDisc = (LibGPGX.cd_read_cb)typeof(GPGX).GetField("CDReadCallback", BindingFlags.Instance | BindingFlags.NonPublic).GetValue(core);
        if (originalDisc == null) throw new InvalidOperationException("Missing original typed disc delegate");
        read = (address, value, flags) => {
            Reads++;
            uint previous;
            if (lastRead.TryGetValue(address, out previous) && previous == value) SuppressedReads++;
            else { lastRead[address] = value; SafeRecord("bus_read_after_handler", new {scope = "M68K BUS", cpu = (string)null, address, value, flags, width = (int?)null}); }
            return null;
        };
        write = (address, value, flags) => {
            Writes++;
            SafeRecord("bus_write_before_handler", new {scope = "M68K BUS", cpu = (string)null, address, value, flags, width = (int?)null});
            return null;
        };
        wrappedDisc = (lba, dest, subcode) => {
            DiscBefore++;
            SafeRecord("disc_sector_before_original_callback", new {lba, subcode});
            originalDisc(lba, dest, subcode);
            DiscOriginal++;
            try {
                byte[] bytes = new byte[subcode ? 96 : 2352];
                Marshal.Copy(dest, bytes, 0, bytes.Length);
                Record("disc_sector_after_original_callback_before_stream_copy", new {lba, subcode, bytes = bytes.Length,
                    payload_sha256 = ServiceEvents.Hash(bytes)});
                DiscAfter++;
            } catch (Exception e) { Failure = e.ToString(); }
        };
        logger = (address, kind, flags) => {
            string key = ((int)kind).ToString() + "/" + ((int)flags).ToString();
            if (!CdlCounts.ContainsKey(key)) {
                CdlCounts[key] = 0;
                SafeRecord("codedatalog_first_address_kind_flags", new {address, address_type = (int)kind, flags = (int)flags});
            }
            CdlCounts[key]++;
        };
        var host = (WaterboxHost)typeof(GPGX).GetField("_elf", BindingFlags.Instance | BindingFlags.NonPublic).GetValue(core);
        var slots = new List<Delegate>();
        foreach (string name in new[] {"LoadCallback", "_inputCallback", "ExecCallback", "ReadCallback", "WriteCallback"})
            slots.Add((Delegate)typeof(GPGX).GetField(name, BindingFlags.Instance | BindingFlags.NonPublic).GetValue(core));
        slots.Add(logger); slots.Add(wrappedDisc);
        var adapter = CallingConventionAdapters.MakeWaterbox(slots.ToArray(), host);
        host.Enter();
        try { observingNative = BizInvoker.GetInvoker<LibGPGX>(host, host, adapter); }
        finally { host.Exit(); }
    }
    public void BeginFrame(int frame)
    {
        FrameCall = frame; Ordinal = 0; lastRead.Clear(); Rows.Clear();
        Reads = Writes = SuppressedReads = 0;
    }
    void SafeRecord(string phase, object access)
    {
        try { Record(phase, access); } catch (Exception e) { Failure = e.ToString(); }
    }
    void Record(string phase, object access)
    {
        if (Rows.Count >= 20000) throw new InvalidOperationException("Event bound exceeded in one hardware frame");
        Rows.Add(JsonConvert.SerializeObject(new {frame_call = FrameCall, ordinal_in_call = ++Ordinal,
            phase, access, raw = devices.Snapshot()}));
    }
    public void Check()
    {
        if (Failure != null) throw new InvalidOperationException("Observation callback failed: " + Failure);
        if (DiscBefore != DiscOriginal || DiscOriginal != DiscAfter)
            throw new InvalidOperationException("Disc callback was not forwarded exactly once");
    }
    public void Attach()
    {
        if (attached) throw new InvalidOperationException("Already attached");
        core.MemoryCallbacks.Add(new MemoryCallback("M68K BUS", MemoryCallbackType.Read, "CD passive reads", read, 0xa12000, 0xffff00));
        core.MemoryCallbacks.Add(new MemoryCallback("M68K BUS", MemoryCallbackType.Write, "CD passive writes", write, 0xa12000, 0xffff00));
        core.MemoryCallbacks.Add(new MemoryCallback("M68K BUS", MemoryCallbackType.Read, "CD sub-device candidate reads", read, 0xff8000, 0xffff00));
        core.MemoryCallbacks.Add(new MemoryCallback("M68K BUS", MemoryCallbackType.Write, "CD sub-device candidate writes", write, 0xff8000, 0xffff00));
        core.MemoryCallbacks.Add(new MemoryCallback("M68K BUS", MemoryCallbackType.Write, "CD PCM candidate writes", write, 0xff0000, 0xffffe0));
        observingNative.gpgx_set_cdd_callback(wrappedDisc);
        if (cdl) observingNative.gpgx_set_cd_callback(logger);
        attached = true;
    }
    public void Detach()
    {
        if (!attached) return;
        native.gpgx_set_cdd_callback(originalDisc);
        if (cdl) native.gpgx_set_cd_callback(null);
        core.MemoryCallbacks.Remove(read); core.MemoryCallbacks.Remove(write);
        attached = false;
    }
    public void Dispose()
    {
        Detach();
        GC.KeepAlive(read); GC.KeepAlive(write); GC.KeepAlive(wrappedDisc);
        GC.KeepAlive(logger); GC.KeepAlive(originalDisc);
    }
}

internal static class ServiceEvents
{
    public static string Hash(byte[] bytes) { return Probe.Hash(bytes, SHA256.Create()); }
    static string Advance(GPGX core, ServicePad pad, ServiceCallbacks callbacks, List<ServiceInput> inputs, int frame)
    {
        pad.Buttons.Clear();
        foreach (var input in inputs.Where(p => p.first <= frame && frame <= p.last)) pad.Buttons.Add(input.button);
        if (callbacks != null) callbacks.BeginFrame(frame);
        core.FrameAdvance(pad, true, true);
        short[] audio; int count; core.GetSamplesSync(out audio, out count);
        if (callbacks != null) callbacks.Check();
        byte[] samples = new byte[count * 4]; Buffer.BlockCopy(audio, 0, samples, 0, samples.Length);
        byte[] pixels = new byte[core.GetVideoBuffer().Length * 4]; Buffer.BlockCopy(core.GetVideoBuffer(), 0, pixels, 0, pixels.Length);
        return JsonConvert.SerializeObject(new {frame = core.Frame, buttons = pad.Buttons.OrderBy(p => p, StringComparer.Ordinal),
            lag = core.IsLagFrame, audio_samples = count, audio_sha256 = Hash(samples), video_sha256 = Hash(pixels)});
    }
    static List<string> Continue(GPGX core, ServicePad pad, NativeDevices devices, ServiceCallbacks cb, List<ServiceInput> inputs, int from)
    {
        var rows = new List<string>();
        for (int frame = from + 1; frame <= from + 120; frame++) {
            string av = Advance(core, pad, cb, inputs, frame);
            rows.Add(JsonConvert.SerializeObject(new {av, devices = devices.Snapshot(), events = cb == null ? null : cb.Rows.ToArray(),
                reads = cb == null ? 0 : cb.Reads, writes = cb == null ? 0 : cb.Writes,
                suppressed_reads = cb == null ? 0 : cb.SuppressedReads}));
        }
        return rows;
    }
    static void Ppm(GPGX core, string path)
    {
        using (var s = File.Create(path)) {
            byte[] header = System.Text.Encoding.ASCII.GetBytes("P6\n" + core.BufferWidth + " " + core.BufferHeight + "\n255\n");
            s.Write(header, 0, header.Length);
            int[] pixels = core.GetVideoBuffer();
            for (int i = 0; i < core.BufferWidth * core.BufferHeight; i++) {
                s.WriteByte((byte)(pixels[i] >> 16)); s.WriteByte((byte)(pixels[i] >> 8)); s.WriteByte((byte)pixels[i]);
            }
        }
    }
    public static int Main(string[] args)
    {
        try {
            if (args.Length != 8) throw new ArgumentException("cue bios-dir output frames input-json bios-sha1 layout mode");
            string dll = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "dll");
            foreach (var pin in new Dictionary<string, string> {
                {"gpgx.wbx.zst", "c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12"},
                {"libwaterboxhost.so", "d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78"},
                {"BizHawk.Emulation.Cores.dll", "0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7"}})
                if (Hash(File.ReadAllBytes(Path.Combine(dll, pin.Key))) != pin.Value) throw new InvalidOperationException("Pinned runtime mismatch " + pin.Key);
            if (typeof(GPGX).Assembly.GetName().Version != new Version(2, 11, 0, 0)) throw new InvalidOperationException("Require BizHawk 2.11");
            int frames = int.Parse(args[3]);
            if (frames < 1 || frames > 36000) throw new ArgumentException("Invalid frame bound");
            if (args[7] != "events" && args[7] != "events-cdl" && args[7] != "control") throw new ArgumentException("Unknown mode");
            var inputs = JsonConvert.DeserializeObject<List<ServiceInput>>(File.ReadAllText(args[4]));
            var allowed = new HashSet<string>(new[] {"Start", "A", "B", "C", "Up", "Down", "Left", "Right"});
            if (inputs == null || inputs.Any(p => p.first < 1 || p.last < p.first || p.last > frames || !allowed.Contains(p.button)))
                throw new ArgumentException("Invalid authored button interval");
            if (Directory.Exists(args[2])) throw new IOException("Output already exists");
            Directory.CreateDirectory(args[2]);
            var fw = new Firmware(args[1], args[5]);
            using (var core = new GPGX(new CoreLoadParameters<GPGX.GPGXSettings, GPGX.GPGXSyncSettings> {
                Comm = new CoreComm(Console.WriteLine, (m, n) => {}, fw, CoreComm.CorePreferencesFlags.None, null),
                Game = new GameInfo {Name = "CD native service boundaries", System = "GEN"},
                Settings = new GPGX.GPGXSettings(), SyncSettings = new GPGX.GPGXSyncSettings(),
                Discs = {new CdAsset {DiscData = BizHawk.Emulation.DiscSystem.Disc.LoadAutomagic(args[0]), DiscName = Path.GetFileName(args[0])}},
                DeterministicEmulationRequested = true
            })) {
                var pad = new ServicePad {Definition = core.ControllerDefinition};
                var devices = new NativeDevices(core, args[6]);
                var state = (IStatable)core;
                using (var cb = args[7] == "control" ? null : new ServiceCallbacks(core, devices, args[7] == "events-cdl")) {
                    if (cb != null) cb.Attach();
                    using (var av = new StreamWriter(Path.Combine(args[2], "av.jsonl")))
                    using (var events = new StreamWriter(Path.Combine(args[2], "events.jsonl")))
                    using (var samples = new StreamWriter(Path.Combine(args[2], "devices.jsonl")))
                    using (var counts = new StreamWriter(Path.Combine(args[2], "counts.jsonl"))) {
                        for (int frame = 1; frame <= frames; frame++) {
                            av.WriteLine(Advance(core, pad, cb, inputs, frame));
                            if (cb != null) {
                                foreach (string row in cb.Rows) events.WriteLine(row);
                                counts.WriteLine(JsonConvert.SerializeObject(new {frame, reads = cb.Reads, writes = cb.Writes,
                                    suppressed_reads = cb.SuppressedReads, retained_events = cb.Rows.Count}));
                            }
                            samples.WriteLine(JsonConvert.SerializeObject(new {frame, boundary = "after FrameAdvance and GetSamplesSync", raw = devices.Snapshot()}));
                            if (frame % 300 == 0 || frame == frames) Ppm(core, Path.Combine(args[2], "frame-" + frame.ToString("D5") + ".ppm"));
                        }
                    }
                    if (cb != null) cb.Detach();
                    byte[] checkpoint = state.CloneSavestate();
                    for (int n = 0; n < 20; n++) devices.Snapshot();
                    if (!checkpoint.SequenceEqual(state.CloneSavestate())) throw new InvalidOperationException("Observation changed savestate");
                    if (cb != null) cb.Attach();
                    GC.Collect(); GC.WaitForPendingFinalizers();
                    var forward = Continue(core, pad, devices, cb, inputs, frames);
                    if (cb != null) cb.Detach();
                    state.LoadStateBinary(checkpoint); core.DiscardSamples();
                    if (cb != null) cb.Attach();
                    GC.Collect(); GC.WaitForPendingFinalizers();
                    var replay = Continue(core, pad, devices, cb, inputs, frames);
                    if (!forward.SequenceEqual(replay)) throw new InvalidOperationException("Event/AV/device restore continuation differs");
                    File.WriteAllLines(Path.Combine(args[2], "continuation.jsonl"), forward);
                    File.WriteAllText(Path.Combine(args[2], "summary.json"), JsonConvert.SerializeObject(new {
                        schema = "openggf.soniccd.service-events.v1", purpose = "Diagnostic observation only, not trace v5 or engine input",
                        frames, mode = args[7], firmware = fw.Selected, refresh_numerator = core.VsyncNumerator,
                        refresh_denominator = core.VsyncDenominator, observation_savestate_unchanged = true,
                        restore_120_frames_equal = true, checkpoint_sha256 = Hash(checkpoint),
                        disc_before = cb == null ? 0 : cb.DiscBefore, disc_original = cb == null ? 0 : cb.DiscOriginal,
                        disc_after = cb == null ? 0 : cb.DiscAfter, codedatalog_counts = cb == null ? null : cb.CdlCounts,
                        scopes = core.MemoryCallbacks.AvailableScopes,
                        final_registers = core.GetCpuFlagsAndRegisters().ToDictionary(p => p.Key, p => p.Value.Value)
                    }, Formatting.Indented));
                }
            }
            return 0;
        } catch (Exception e) { Console.Error.WriteLine(e); return 1; }
    }
}
