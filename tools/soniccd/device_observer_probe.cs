// CD-02 research observer, 2026-10-23. Uses the unchanged oracle probe's media adapters.
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using BizHawk.Emulation.Common;
using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using BizHawk.Emulation.Cores.Waterbox;
using BizHawk.Emulation.DiscSystem;
using Newtonsoft.Json;

internal sealed class DeviceField
{
    public string name, kind;
    public int offset, size;
}

internal sealed class NativeDevices
{
    const long Base = 0x36f00552448;
    const long DataStream = 0x36f004acf40, AudioStream = 0x36f004aef40;
    readonly WaterboxHost host;
    readonly List<DeviceField> fields;
    public readonly Dictionary<string, object> Api = new Dictionary<string, object>();
    public NativeDevices(GPGX core, string layout)
    {
        if (Probe.Hash(File.ReadAllBytes(layout), SHA256.Create()) != "e53146a7b8908c5c8ccd2e565211cfa5c27efcae19a462ec43bfa58a8f8058b4")
            throw new InvalidOperationException("Pinned header-derived layout mismatch");
        host = (WaterboxHost)typeof(GPGX).GetField("_elf", BindingFlags.Instance | BindingFlags.NonPublic).GetValue(core);
        fields = File.ReadAllLines(layout).Select(line => {
            var p = line.Split('\t');
            return new DeviceField {name = p[0], offset = int.Parse(p[1]), size = int.Parse(p[2]), kind = p[3]};
        }).ToList();
        if (IntPtr.Size != 8 || !BitConverter.IsLittleEndian) throw new InvalidOperationException("Require x86-64 little-endian ABI");
        host.Enter();
        try {
            Api["ext_lookup"] = host.GetProcAddrOrZero("ext").ToInt64();
            long exported = host.GetProcAddrOrThrow("gpgx_getregs").ToInt64();
            Api["gpgx_getregs"] = exported;
            if (exported != 0x36f001be7c0) throw new InvalidOperationException("ELF runtime address mismatch");
            var native = (LibGPGX)typeof(GPGX).GetField("Core", BindingFlags.Instance | BindingFlags.NonPublic).GetValue(core);
            var names = new[] {"bootrom", "prg_ram", "word_ram_0", "word_ram_1", "word_ram_2M", "bram"};
            for (int n = 0; n < names.Length; n++) {
                IntPtr area = IntPtr.Zero; int bytes = 0;
                IntPtr label = native.gpgx_get_memdom(n + 3, ref area, ref bytes);
                var field = fields.Single(f => f.name == names[n]);
                if (label == IntPtr.Zero || area.ToInt64() != Base + field.offset || bytes != field.size)
                    throw new InvalidOperationException("Pinned C layout disagrees with native RAM domain " + names[n]);
                Api[names[n] + "_abi_match"] = true;
            }
        } finally { host.Exit(); }
    }
    public SortedDictionary<string, long> Snapshot()
    {
        var result = new SortedDictionary<string, long>(StringComparer.Ordinal);
        host.Enter();
        try {
            foreach (var f in fields.Where(f => f.kind != "area" && f.kind != "meta")) {
                long origin = f.kind == "audio_stream" ? AudioStream : f.kind == "data_stream" ? DataStream : Base;
                IntPtr address = new IntPtr(origin + f.offset);
                long value;
                switch (f.size) {
                    case 1: value = Marshal.ReadByte(address); if (f.kind == "s") value = (sbyte)value; break;
                    case 2: value = (ushort)Marshal.ReadInt16(address); if (f.kind == "s") value = (short)value; break;
                    case 4: value = (uint)Marshal.ReadInt32(address); if (f.kind == "s") value = (int)value; break;
                    case 8: value = Marshal.ReadInt64(address); break;
                    default: throw new InvalidOperationException("Unexpected scalar width");
                }
                if (f.kind == "nonnull") value = value == 0 ? 0 : 1;
                if (f.kind == "stream_ptr") {
                    if (value != DataStream && value != AudioStream) throw new InvalidOperationException("Unexpected native disc stream");
                    value = value == DataStream ? 0 : 1;
                }
                if (f.kind == "pcm_ptr" || f.kind == "wordram_ptr") {
                    var area = fields.Single(p => p.name == (f.kind == "pcm_ptr" ? "pcm_hw.ram" : "word_ram_2M"));
                    value = value == 0 ? -1 : value - Base - area.offset;
                    if (value < -1 || value >= area.size) throw new InvalidOperationException("Device pointer outside owning native RAM");
                }
                result.Add(f.name, value);
            }
        } finally { host.Exit(); }
        return result;
    }
    public object GraphicsRequest(SortedDictionary<string, long> snapshot)
    {
        if ((snapshot["regs.58"] & 0x8000) == 0) return null;
        long start = (snapshot["regs.66"] << 2) & 0x3fff8;
        long cursor = snapshot["gfx_hw.tracePtr"];
        long completed = (cursor - start) / 8;
        long remaining = snapshot["regs.64"] & 0xff;
        long lines = completed + remaining;
        if (cursor < start || (cursor - start) % 8 != 0 || lines < 1 || lines > 256 || start + lines * 8 > 0x40000)
            throw new InvalidOperationException("Graphics trace descriptor outside native Word RAM");
        var words = new ushort[lines * 4];
        long ram = Base + fields.Single(f => f.name == "word_ram_2M").offset;
        host.Enter();
        try {
            for (int n = 0; n < words.Length; n++) words[n] = (ushort)Marshal.ReadInt16(new IntPtr(ram + start + n * 2));
        } finally { host.Exit(); }
        return new { trace_base = start, cursor, completed_lines = completed, remaining_lines = remaining,
            reconstructed_total_lines = lines, width = snapshot["regs.62"] & 0x1ff,
            image_start_register = snapshot["regs.5e"], stamp_size_register = snapshot["regs.58"] & 0xff,
            wordram_mode_register = snapshot["regs.02"], map_offset = snapshot["gfx_hw.mapPtr"],
            scd_cycles = snapshot["cycles"], gfx_cycles = snapshot["gfx_hw.cycles"],
            gfx_cycles_per_line = snapshot["gfx_hw.cyclesPerLine"], trace_words = words };
    }
}

internal static class DeviceObserver
{
    static string Hash(byte[] bytes) { return Probe.Hash(bytes, SHA256.Create()); }
    static object Advance(GPGX core, Pad pad, int frame, HashSet<int> starts)
    {
        pad.Start = starts.Contains(frame); core.FrameAdvance(pad, true, true);
        short[] audio; int count; core.GetSamplesSync(out audio, out count);
        byte[] samples = new byte[count * 4]; Buffer.BlockCopy(audio, 0, samples, 0, samples.Length);
        byte[] pixels = new byte[core.GetVideoBuffer().Length * 4];
        Buffer.BlockCopy(core.GetVideoBuffer(), 0, pixels, 0, pixels.Length);
        return new { frame = core.Frame, start = pad.Start, lag = core.IsLagFrame, audio_samples = count,
            audio_sha256 = Hash(samples), video_sha256 = Hash(pixels) };
    }
    static List<string> Continue(GPGX core, Pad pad, NativeDevices observer, int from, HashSet<int> starts)
    {
        var rows = new List<string>();
        for (int frame = from + 1; frame <= from + 120; frame++) {
            var av = Advance(core, pad, frame, starts);
            var devices = observer == null ? null : observer.Snapshot();
            rows.Add(JsonConvert.SerializeObject(new { av, devices,
                graphics = observer == null ? null : observer.GraphicsRequest(devices) }));
        }
        return rows;
    }
    public static int Main(string[] args)
    {
        try {
            if (args.Length != 8) throw new ArgumentException("cue bios-dir output frames starts bios-sha1 layout mode");
            string dll = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "dll");
            var pins = new Dictionary<string, string> {
                {"gpgx.wbx.zst", "c4231296ec5ba59b431df22b68e234ae7bfbbfc87b6e72fa471234ac1b220d12"},
                {"libwaterboxhost.so", "d2367818aafb4e520ad5ab005b5762c61506b0c819c4d79687235acfb0fc0c78"},
                {"BizHawk.Emulation.Cores.dll", "0144e6e236be68ce126eb771dcb5a9ae7c153a083fa0333f345ac37b4a60acf7"}
            };
            foreach (var pin in pins)
                if (Hash(File.ReadAllBytes(Path.Combine(dll, pin.Key))) != pin.Value)
                    throw new InvalidOperationException("Pinned runtime mismatch " + pin.Key);
            if (typeof(GPGX).Assembly.GetName().Version != new Version(2, 11, 0, 0)) throw new InvalidOperationException("Require BizHawk 2.11");
            string output = args[2]; int bound = int.Parse(args[3]);
            if (bound < 1 || bound > 36000) throw new ArgumentOutOfRangeException("frames");
            if (args[7] != "symbols" && args[7] != "callbacks" && args[7] != "off") throw new ArgumentException("Unknown observer mode");
            bool enabled = args[7] != "off", callbacks = args[7] == "callbacks";
            var starts = new HashSet<int>(args[4].Split(',').Where(x => x != "").Select(int.Parse));
            if (starts.Any(f => f < 1 || f > bound)) throw new ArgumentException("Input outside capture interval");
            if (Directory.Exists(output)) throw new IOException("Output exists");
            Directory.CreateDirectory(output);
            var fw = new Firmware(args[1], args[5]);
            using (var core = new GPGX(new CoreLoadParameters<GPGX.GPGXSettings, GPGX.GPGXSyncSettings> {
                Comm = new CoreComm(Console.WriteLine, (m, n) => {}, fw, CoreComm.CorePreferencesFlags.None, null),
                Game = new GameInfo {Name = "Sonic CD device observation", System = "GEN"},
                Settings = new GPGX.GPGXSettings(), SyncSettings = new GPGX.GPGXSyncSettings(),
                Discs = {new CdAsset {DiscData = Disc.LoadAutomagic(args[0]), DiscName = Path.GetFileName(args[0])}},
                DeterministicEmulationRequested = true
            })) {
                var pad = new Pad {Definition = core.ControllerDefinition};
                var observer = enabled ? new NativeDevices(core, args[6]) : null;
                var state = (IStatable)core;
                var callbackRows = new List<object>();
                int currentFrame = 0;
                MemoryCallbackDelegate write = (address, value, flags) => {
                    if (callbackRows.Count < 10000) callbackRows.Add(new {frame_call = currentFrame, address, value, flags});
                    return null;
                };
                if (callbacks) core.MemoryCallbacks.Add(new MemoryCallback("M68K BUS", MemoryCallbackType.Write,
                    "Read-only gate-array observation", write, 0xa12000, 0xffff00));
                var domains = core.ServiceProvider.GetService<IMemoryDomains>();
                using (var devices = new StreamWriter(Path.Combine(output, "devices.jsonl")))
                using (var av = new StreamWriter(Path.Combine(output, "av.jsonl")))
                using (var graphics = new StreamWriter(Path.Combine(output, "graphics.jsonl"))) {
                    for (int f = 1; f <= bound; f++) {
                        currentFrame = f;
                        av.WriteLine(JsonConvert.SerializeObject(Advance(core, pad, f, starts)));
                        if (observer != null) {
                            var snapshot = observer.Snapshot();
                            if (JsonConvert.SerializeObject(snapshot) != JsonConvert.SerializeObject(observer.Snapshot()))
                                throw new InvalidOperationException("Consecutive snapshots differ without advancement");
                            devices.WriteLine(JsonConvert.SerializeObject(new {frame = core.Frame,
                                boundary = "after FrameAdvance and GetSamplesSync", devices = snapshot}));
                            var request = observer.GraphicsRequest(snapshot);
                            if (request != null) graphics.WriteLine(JsonConvert.SerializeObject(new {frame = core.Frame,
                                boundary = "after FrameAdvance and GetSamplesSync, operation already in progress", request}));
                        }
                    }
                }
                if (callbacks) core.MemoryCallbacks.Remove(write);
                byte[] before = state.CloneSavestate();
                if (observer != null) for (int n = 0; n < 20; n++) {
                    var snapshot = observer.Snapshot(); observer.GraphicsRequest(snapshot);
                }
                byte[] after = state.CloneSavestate();
                if (!before.SequenceEqual(after)) throw new InvalidOperationException("Observer changed savestate bytes");
                var forward = Continue(core, pad, observer, bound, starts);
                state.LoadStateBinary(before); core.DiscardSamples();
                var replay = Continue(core, pad, observer, bound, starts);
                if (!forward.SequenceEqual(replay)) throw new InvalidOperationException("Device restore continuation differs");
                File.WriteAllLines(Path.Combine(output, "continuation.jsonl"), forward);
                File.WriteAllText(Path.Combine(output, "callbacks.json"), JsonConvert.SerializeObject(callbackRows));
                File.WriteAllText(Path.Combine(output, "summary.json"), JsonConvert.SerializeObject(new {
                    schema = "openggf.soniccd.device-observer.v1", frames = bound, mode = args[7], firmware = fw.Selected,
                    refresh_numerator = core.VsyncNumerator, refresh_denominator = core.VsyncDenominator,
                    api = observer == null ? null : observer.Api, scopes = core.MemoryCallbacks.AvailableScopes,
                    callback_events = callbackRows.Count, observation_savestate_unchanged = true,
                    checkpoint_sha256 = Hash(before), restore_120_frames_equal = true,
                    final_registers = core.GetCpuFlagsAndRegisters().ToDictionary(p => p.Key, p => p.Value.Value),
                    main_io_peek = domains["M68K BUS"].PeekByte(0xa12002),
                    sub_io_peek = domains["S68K BUS"].PeekByte(0xff8002)
                }, Formatting.Indented));
            }
            return 0;
        } catch (Exception error) { Console.Error.WriteLine(error); return 1; }
    }
}
