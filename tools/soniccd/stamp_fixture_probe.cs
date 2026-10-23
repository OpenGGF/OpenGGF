// CD-05 pure-device fixture producer, 2026-10-23. Reuses frozen CD-02 ABI checks.
using System;
using System.IO;
using System.IO.Compression;
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
using Newtonsoft.Json.Linq;

internal sealed class StampFixtureMemory
{
    readonly WaterboxHost host;
    readonly IntPtr ram;
    public StampFixtureMemory(GPGX core)
    {
        host = (WaterboxHost)typeof(GPGX).GetField("_elf", BindingFlags.Instance | BindingFlags.NonPublic).GetValue(core);
        var native = (LibGPGX)typeof(GPGX).GetField("Core", BindingFlags.Instance | BindingFlags.NonPublic).GetValue(core);
        host.Enter();
        try {
            int size = 0; IntPtr area = IntPtr.Zero;
            if (native.gpgx_get_memdom(7, ref area, ref size) == IntPtr.Zero || size != 0x40000)
                throw new InvalidOperationException("Missing bound 2M Word RAM domain");
            ram = area;
        } finally { host.Exit(); }
    }
    public byte[] Read()
    {
        byte[] result = new byte[0x40000];
        host.Enter();
        try { Marshal.Copy(ram, result, 0, result.Length); }
        finally { host.Exit(); }
        for (int n = 0; n < result.Length; n += 2) {
            byte tmp = result[n]; result[n] = result[n + 1]; result[n + 1] = tmp;
        }
        return result;
    }
}

internal sealed class StampFixturePending
{
    public int ordinal, frame;
    public JObject request;
    public SortedDictionary<string, long> ingress;
    public byte[] ram;
}

internal static class StampFixtureProbe
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
    static void Compressed(string path, byte[] bytes)
    {
        using (var stream = File.Create(path))
        using (var gzip = new GZipStream(stream, CompressionLevel.Optimal)) gzip.Write(bytes, 0, bytes.Length);
    }
    static bool Title(JObject request)
    {
        return (long)request["trace_base"] == 0x1c800 && (long)request["map_offset"] == 0x20000 &&
            (long)request["image_start_register"] == 0x7400 && (long)request["width"] == 256 &&
            (long)request["stamp_size_register"] == 7 && (long)request["reconstructed_total_lines"] == 96;
    }
    static readonly Dictionary<string, long> TitleFields = new Dictionary<string, long> {
        {"gfx_hw.dotMask", 0x7fffff}, {"gfx_hw.stampShift", 16}, {"gfx_hw.mapShift", 7},
        {"gfx_hw.mapPtr", 0x20000}, {"gfx_hw.bufferOffset", 761}, {"regs.5a", 0x8000},
        {"regs.5c", 11}, {"regs.60", 0}, {"regs.5e", 0x7400}, {"regs.62", 256}, {"regs.66", 0x7200}
    };
    internal static void ValidateIngress(JObject request, SortedDictionary<string, long> snapshot)
    {
        // Pinned scd.c:1299-1393: RET=bit 0, DMNA=bit 1, MODE=bit 2.
        // gfx.c:581 selects priority from bits 3-4; only normal priority is covered.
        if ((snapshot["regs.02"] & 7) != 2 || (snapshot["regs.02"] & 0x18) != 0 ||
            (long)request["wordram_mode_register"] != snapshot["regs.02"])
            throw new InvalidOperationException("Ingress requires matching raw 2M sub-owner, normal-priority mode");
        if (!Title(request) || snapshot["regs.58"] != ((long)request["stamp_size_register"] | 0x8000))
            throw new InvalidOperationException("Ingress stamp mode disagrees with title request");
        long completed = (long)request["completed_lines"], remaining = (long)request["remaining_lines"];
        if (completed < 1 || remaining < 1 || completed + remaining != 96 ||
            snapshot["gfx_hw.tracePtr"] != (long)request["trace_base"] + completed * 8 ||
            (long)request["cursor"] != snapshot["gfx_hw.tracePtr"] ||
            snapshot["gfx_hw.bufferStart"] != 0x3a000 + completed * 8 ||
            (snapshot["regs.64"] & 255) != remaining)
            throw new InvalidOperationException("Ingress cursor or remaining count disagrees");
        foreach (var field in TitleFields)
            if (snapshot[field.Key] != field.Value)
                throw new InvalidOperationException("Native derived descriptor disagrees with title setup");
    }
    internal static bool EgressReady(StampFixturePending p, SortedDictionary<string, long> end)
    {
        // MODE/PM0-1 stay fixed (scd.c:1393); RET/DMNA must change on return.
        // gfx.c:671,709 sets/clears busy. No other stamp-register change is allowed.
        if (((end["regs.02"] ^ p.ingress["regs.02"]) & 0x1c) != 0 ||
            (end["regs.58"] & ~0x8000L) != (p.ingress["regs.58"] & ~0x8000L))
            throw new InvalidOperationException("Request stamp mode or Word RAM mode/priority changed before egress");
        foreach (string key in TitleFields.Keys)
            if (end[key] != p.ingress[key])
                throw new InvalidOperationException("Request descriptor changed before egress");
        if ((end["regs.02"] & 7) != 1 || (end["regs.58"] & 0x8000) != 0) return false;
        long total = (long)p.request["reconstructed_total_lines"];
        if (end["regs.64"] != 0 || end["gfx_hw.tracePtr"] != (long)p.request["trace_base"] + total * 8 ||
            end["gfx_hw.bufferStart"] != p.ingress["gfx_hw.bufferStart"] + (long)p.request["remaining_lines"] * 8)
            throw new InvalidOperationException("Egress does not match pending native request cursor");
        return true;
    }
    public static int Main(string[] args)
    {
        try {
            if (args.Length != 9) throw new ArgumentException("cue bios-dir output frames starts bios-sha1 layout mode fixtures");
            string output = args[2]; int bound = int.Parse(args[3]), limit = int.Parse(args[8]);
            bool enabled = args[7] == "observe";
            if (!enabled && args[7] != "control") throw new ArgumentException("Unknown mode");
            if (bound < 1 || bound > 1800 || limit < 3 || limit > 16) throw new ArgumentException("Bounds");
            if (Directory.Exists(output)) throw new IOException("Output exists");
            Directory.CreateDirectory(output);
            var starts = new HashSet<int>(args[4].Split(',').Where(x => x != "").Select(int.Parse));
            var fw = new Firmware(args[1], args[5]);
            using (var core = new GPGX(new CoreLoadParameters<GPGX.GPGXSettings, GPGX.GPGXSyncSettings> {
                Comm = new CoreComm(Console.WriteLine, (m, n) => {}, fw, CoreComm.CorePreferencesFlags.None, null),
                Game = new GameInfo { Name = "Sonic CD partial stamp device fixtures", System = "GEN" },
                Settings = new GPGX.GPGXSettings(), SyncSettings = new GPGX.GPGXSyncSettings(),
                Discs = {new CdAsset { DiscData = Disc.LoadAutomagic(args[0]), DiscName = Path.GetFileName(args[0]) }},
                DeterministicEmulationRequested = true
            })) {
                var pad = new Pad { Definition = core.ControllerDefinition };
                var devices = enabled ? new NativeDevices(core, args[6]) : null;
                var memory = enabled ? new StampFixtureMemory(core) : null;
                var state = (IStatable)core;
                var fixtures = new List<object>();
                StampFixturePending pending = null;
                int currentFrame = 0, pollCount = 0, wordAccessesWhilePending = 0;
                MemoryCallbackDelegate guard = (address, value, flags) => {
                    if (pending != null) {
                        wordAccessesWhilePending++;
                        throw new InvalidOperationException("Main CPU touched Word RAM before matched egress poll");
                    }
                    return null;
                };
                MemoryCallbackDelegate poll = (address, value, flags) => {
                    if (pending == null) return null;
                    pollCount++;
                    var end = devices.Snapshot();
                    var p = pending;
                    if (!EgressReady(p, end)) return null;
                    byte[] result = memory.Read();
                    string label = "request-" + p.ordinal.ToString("D3");
                    Compressed(Path.Combine(output, label + "-ingress.bin.gz"), p.ram);
                    Compressed(Path.Combine(output, label + "-egress.bin.gz"), result);
                    fixtures.Add(new { ordinal = p.ordinal, ingress_frame = p.frame, egress_frame_call = currentFrame,
                        ingress_boundary = "after FrameAdvance and GetSamplesSync, partial progress",
                        egress_boundary = "M68K BUS memory-mode read callback, main owner and graphics idle, before image read",
                        callback_address = address, callback_value = value, callback_flags = flags,
                        request = p.request, ingress_devices = p.ingress, egress_devices = end,
                        wordram_encoding = "68000-address-order-big-endian", wordram_bytes = 0x40000,
                        ingress_file = label + "-ingress.bin.gz", egress_file = label + "-egress.bin.gz",
                        ingress_sha256 = Hash(p.ram), egress_sha256 = Hash(result),
                        image_base = 0x1d000, image_bytes = 256 * 96 / 2,
                        observation_kind = "partial-device-continuation" });
                    pending = null;
                    return null;
                };
                if (enabled) {
                    core.MemoryCallbacks.Add(new MemoryCallback("M68K BUS", MemoryCallbackType.Read,
                        "Stamp fixture ownership poll", poll, 0xa12002, 0xfffffe));
                    core.MemoryCallbacks.Add(new MemoryCallback("M68K BUS", MemoryCallbackType.Read,
                        "Stamp fixture pre-egress Word RAM read guard", guard, 0x200000, 0xfc0000));
                    core.MemoryCallbacks.Add(new MemoryCallback("M68K BUS", MemoryCallbackType.Write,
                        "Stamp fixture pre-egress Word RAM write guard", guard, 0x200000, 0xfc0000));
                }
                using (var av = new StreamWriter(Path.Combine(output, "av.jsonl"))) {
                    for (int f = 1; f <= bound; f++) {
                        currentFrame = f;
                        av.WriteLine(JsonConvert.SerializeObject(Advance(core, pad, f, starts)));
                        if (!enabled || fixtures.Count >= limit) continue;
                        var snapshot = devices.Snapshot();
                        var raw = devices.GraphicsRequest(snapshot);
                        if (raw == null) continue;
                        var request = JObject.FromObject(raw);
                        if (!Title(request)) continue;
                        if (pending != null) continue;
                        ValidateIngress(request, snapshot);
                        pending = new StampFixturePending { ordinal = fixtures.Count + 1, frame = core.Frame,
                            request = request, ingress = snapshot, ram = memory.Read() };
                    }
                }
                if (enabled) { core.MemoryCallbacks.Remove(poll); core.MemoryCallbacks.Remove(guard); }
                if (pending != null || (enabled && fixtures.Count != limit)) throw new InvalidOperationException("Incomplete bounded fixture capture");
                byte[] before = state.CloneSavestate();
                if (enabled) for (int n = 0; n < 20; n++) { devices.Snapshot(); memory.Read(); }
                if (!before.SequenceEqual(state.CloneSavestate())) throw new InvalidOperationException("Observer changed native state");
                var forward = new List<string>(); var replay = new List<string>();
                for (int f = bound + 1; f <= bound + 120; f++) forward.Add(JsonConvert.SerializeObject(Advance(core, pad, f, starts)));
                state.LoadStateBinary(before); core.DiscardSamples();
                for (int f = bound + 1; f <= bound + 120; f++) replay.Add(JsonConvert.SerializeObject(Advance(core, pad, f, starts)));
                if (!forward.SequenceEqual(replay)) throw new InvalidOperationException("Restored continuation differs");
                File.WriteAllText(Path.Combine(output, "fixtures.json"), JsonConvert.SerializeObject(fixtures, Formatting.Indented));
                File.WriteAllLines(Path.Combine(output, "continuation.jsonl"), forward);
                File.WriteAllText(Path.Combine(output, "summary.json"), JsonConvert.SerializeObject(new {
                    schema = "openggf.soniccd.stamp-fixtures.v1", frames = bound, mode = args[7], fixture_count = fixtures.Count,
                    ownership_polls_while_pending = pollCount, wordram_accesses_before_egress = wordAccessesWhilePending,
                    observation_savestate_unchanged = true, restore_120_frames_equal = true,
                    checkpoint_sha256 = Hash(before), firmware = fw.Selected,
                    api = enabled ? devices.Api : null }, Formatting.Indented));
            }
            return 0;
        } catch (Exception error) { Console.Error.WriteLine(error); return 1; }
    }
}
