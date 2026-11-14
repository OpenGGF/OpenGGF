// Purpose: repeatable native Sega CD boot/domain evidence for CD-02 (2026-10-23).
// Inputs: BizHawk 2.11, user-supplied CUE/BIOS directory, frame bound and output root.
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Security.Cryptography;
using BizHawk.Emulation.Common;
using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using BizHawk.Emulation.DiscSystem;
using Newtonsoft.Json;

internal sealed class CdAsset : IDiscAsset
{
    public Disc DiscData { get; set; }
    public DiscType DiscType { get { return DiscType.MegaCD; } }
    public string DiscName { get; set; }
}
internal sealed class Firmware : ICoreFileProvider
{
    public readonly List<object> Selected = new List<object>();
    private readonly string directory;
    private readonly string expectedSha1;
    public Firmware(string directory, string expectedSha1)
        { this.directory = directory; this.expectedSha1 = expectedSha1; }
    public byte[] GetFirmware(FirmwareID id, string msg = null)
    {
        var valid = FirmwareDatabase.FirmwareOptions.Where(o => o.ID.Equals(id)
            && (o.Status == FirmwareOptionStatus.Ideal || o.Status == FirmwareOptionStatus.Acceptable)).ToList();
        foreach (string path in Directory.GetFiles(directory).OrderBy(p => p, StringComparer.Ordinal))
        {
            byte[] bytes = File.ReadAllBytes(path);
            string sha1 = Probe.Hash(bytes, SHA1.Create());
            if (!sha1.Equals(expectedSha1, StringComparison.OrdinalIgnoreCase)) continue;
            var matches = valid.Where(o => o.Hash.Equals(sha1, StringComparison.OrdinalIgnoreCase)).ToList();
            if (matches.Count == 0) continue;
            var file = FirmwareDatabase.FirmwareFilesByHash[matches[0].Hash];
            Selected.Add(new { id = id.ToString(), path, sha1, bytes = bytes.Length,
                description = file.Description, status = matches[0].Status.ToString() });
            return bytes;
        }
        throw new InvalidOperationException("No acceptable exact-hash firmware for " + id + " matching " + expectedSha1);
    }
    public byte[] GetFirmwareOrThrow(FirmwareID id, string msg = null) { return GetFirmware(id, msg); }
    public (byte[] FW, GameInfo Game) GetFirmwareWithGameInfoOrThrow(FirmwareID id, string msg = null)
        { return (GetFirmware(id, msg), new GameInfo()); }
    public string GetRetroSaveRAMDirectory(IGameInfo game) { throw new NotSupportedException(); }
    public string GetRetroSystemPath(IGameInfo game) { throw new NotSupportedException(); }
    public string GetUserPath(string system, bool temp) { throw new NotSupportedException(); }
}
internal sealed class Pad : IController
{
    public ControllerDefinition Definition { get; set; }
    public bool Start;
    public bool IsPressed(string name) { return name == "P1 Start" && Start; }
    public int AxisValue(string name) { return 0; }
    public IReadOnlyCollection<(string Name, int Strength)> GetHapticsSnapshot()
        { return new (string Name, int Strength)[0]; }
    public void SetHapticChannelStrength(string name, int strength) { }
}
internal sealed class FrameAv
{
    public int frame, audio_samples;
    public bool start, lag, audio_nonzero;
    public string audio_sha256, video_sha256;
}
internal static class Probe
{
    private static HashSet<int> startFrames;
    internal static string Hash(byte[] bytes, HashAlgorithm algorithm)
    {
        using (algorithm) return BitConverter.ToString(algorithm.ComputeHash(bytes)).Replace("-", "").ToLowerInvariant();
    }
    static byte[] Read(MemoryDomain d)
    {
        byte[] b = new byte[checked((int)d.Size)];
        for (int i = 0; i < b.Length; i++) b[i] = d.PeekByte(i);
        return b;
    }
    static string VideoHash(GPGX core)
    {
        byte[] pixels = new byte[core.GetVideoBuffer().Length * 4];
        Buffer.BlockCopy(core.GetVideoBuffer(), 0, pixels, 0, pixels.Length);
        return Hash(pixels, SHA256.Create());
    }
    static object Snapshot(GPGX core, IMemoryDomains domains)
    {
        var hashes = domains.Where(d => !d.Name.EndsWith(" BUS") && !d.Name.StartsWith("Waterbox "))
            .ToDictionary(d => d.Name, d => Hash(Read(d), SHA256.Create()));
        var regs = core.GetCpuFlagsAndRegisters().ToDictionary(p => p.Key, p => p.Value.Value);
        return new { frame = core.Frame, lag = core.IsLagFrame, lag_count = core.LagCount,
            domains = hashes, registers = regs, width = core.BufferWidth, height = core.BufferHeight,
            video_sha256 = VideoHash(core), drive_light = core.DriveLightOn,
            m68k_gate_peek = BusWindow(domains["M68K BUS"], 0xA12000, 0x40),
            s68k_gate_peek = BusWindow(domains["S68K BUS"], 0xFF8000, 0x200),
            s68k_pcm_peek = BusWindow(domains["S68K BUS"], 0xFF0000, 0x40) };
    }
    static string BusWindow(MemoryDomain domain, long address, int size)
    {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = domain.PeekByte(address + i);
        return BitConverter.ToString(bytes).Replace("-", "").ToLowerInvariant();
    }
    static void Ppm(GPGX core, string path)
    {
        using (var stream = File.Create(path))
        {
            byte[] header = System.Text.Encoding.ASCII.GetBytes("P6\n" + core.BufferWidth + " " + core.BufferHeight + "\n255\n");
            stream.Write(header, 0, header.Length);
            int[] pixels = core.GetVideoBuffer();
            for (int i = 0; i < core.BufferWidth * core.BufferHeight; i++)
            { stream.WriteByte((byte)(pixels[i] >> 16)); stream.WriteByte((byte)(pixels[i] >> 8)); stream.WriteByte((byte)pixels[i]); }
        }
    }
    static FrameAv Advance(GPGX core, Pad pad, int frame)
    {
        pad.Start = startFrames.Contains(frame);
        core.FrameAdvance(pad, true, true);
        short[] audio; int ns; core.GetSamplesSync(out audio, out ns);
        byte[] bytes = new byte[ns * 4];
        Buffer.BlockCopy(audio, 0, bytes, 0, bytes.Length);
        return new FrameAv { frame = core.Frame, start = pad.Start, lag = core.IsLagFrame,
            audio_samples = ns, audio_nonzero = audio.Take(ns * 2).Any(s => s != 0),
            audio_sha256 = Hash(bytes, SHA256.Create()), video_sha256 = VideoHash(core) };
    }
    static string Continuation(GPGX core, Pad pad, IMemoryDomains domains, int from)
    {
        var frames = new List<object>();
        for (int f = from + 1; f <= from + 120; f++) frames.Add(Advance(core, pad, f));
        return JsonConvert.SerializeObject(new { frames, final = Snapshot(core, domains) });
    }
    public static int Main(string[] args)
    {
        try
        {
            if (args.Length != 6) throw new ArgumentException("Expected cue, firmware directory, output, frames, start frames CSV, BIOS SHA1");
            if (typeof(GPGX).Assembly.GetName().Version != new Version(2, 11, 0, 0))
                throw new InvalidOperationException("Exactly BizHawk 2.11 is required");
            string cue = Path.GetFullPath(args[0]), firmwareDirectory = Path.GetFullPath(args[1]);
            string output = Path.GetFullPath(args[2]); int count = int.Parse(args[3]);
            if (count < 1 || count > 36000) throw new ArgumentOutOfRangeException("frames");
            startFrames = new HashSet<int>(args[4].Split(new[] { ',' }, StringSplitOptions.RemoveEmptyEntries).Select(int.Parse));
            if (startFrames.Any(f => f < 1 || f > count)) throw new ArgumentException("Start frames must be in the capture bound");
            if (Directory.Exists(output)) throw new IOException("Output already exists");
            Directory.CreateDirectory(output);
            var fw = new Firmware(firmwareDirectory, args[5]);
            var disc = Disc.LoadAutomagic(cue);
            var game = new GameInfo { Name = "Sonic CD native CD-02 probe", System = "GEN" };
            using (var core = new GPGX(new CoreLoadParameters<GPGX.GPGXSettings, GPGX.GPGXSyncSettings>
            {
                Comm = new CoreComm(m => Console.WriteLine(m), (m, n) => { }, fw, CoreComm.CorePreferencesFlags.None, null),
                Game = game, Settings = new GPGX.GPGXSettings(), SyncSettings = new GPGX.GPGXSyncSettings(),
                Discs = { new CdAsset { DiscData = disc, DiscName = Path.GetFileName(cue) } },
                DeterministicEmulationRequested = true
            }))
            {
                var domains = core.ServiceProvider.GetService<IMemoryDomains>();
                var pad = new Pad { Definition = core.ControllerDefinition };
                long samples = 0; int nonzeroAudioFrames = 0, nonlag = 0;
                using (var frames = new StreamWriter(Path.Combine(output, "frames.jsonl")))
                using (var observations = new StreamWriter(Path.Combine(output, "av.jsonl")))
                {
                    for (int frame = 1; frame <= count; frame++)
                    {
                        var observed = Advance(core, pad, frame);
                        observations.WriteLine(JsonConvert.SerializeObject(observed));
                        samples += observed.audio_samples;
                        if (observed.audio_nonzero) nonzeroAudioFrames++;
                        if (!core.IsLagFrame) nonlag++;
                        if (frame % 120 == 0 || pad.Start || frame == count)
                            frames.WriteLine(JsonConvert.SerializeObject(Snapshot(core, domains)));
                        if (frame % 300 == 0 || frame == count) Ppm(core, Path.Combine(output, "frame-" + frame.ToString("D5") + ".ppm"));
                    }
                }
                byte[] checkpoint = ((IStatable)core).CloneSavestate();
                string forward = Continuation(core, pad, domains, count);
                ((IStatable)core).LoadStateBinary(checkpoint);
                core.DiscardSamples();
                string replay = Continuation(core, pad, domains, count);
                if (forward != replay) throw new InvalidOperationException("Savestate forward replay snapshot mismatch");
                var summary = new {
                    schema = "openggf.soniccd.native-oracle-probe.v1", purpose = "Native boot capability, not gameplay parity or a V5 trace",
                    cue, cue_sha256 = Hash(File.ReadAllBytes(cue), SHA256.Create()), firmware = fw.Selected,
                    managed_version = typeof(GPGX).Assembly.GetName().Version.ToString(), core = "Genesis Plus GX",
                    settings = core.GetSettings(), sync_settings = core.GetSyncSettings(),
                    deterministic = core.DeterministicEmulation, is_mega_cd = core.IsMegaCD,
                    refresh_numerator = core.VsyncNumerator, refresh_denominator = core.VsyncDenominator,
                    advanced_hardware_frames = count, movie_frames = (int?)null,
                    gameplay_updates = (int?)null, nonlag_frames = nonlag, audio_stereo_samples = samples,
                    nonzero_audio_frames = nonzeroAudioFrames, input_start_frames = startFrames.OrderBy(f => f),
                    domains = domains.Select(d => new { name = d.Name, bytes = d.Size, endian = d.EndianType.ToString() }),
                    memory_callback_scopes = core.MemoryCallbacks.AvailableScopes,
                    register_names = core.GetCpuFlagsAndRegisters().Keys,
                    savestate_bytes = checkpoint.Length, savestate_sha256 = Hash(checkpoint, SHA256.Create()),
                    replay_120_frames_equal = true, replay_snapshot_sha256 = Hash(System.Text.Encoding.UTF8.GetBytes(replay), SHA256.Create())
                };
                File.WriteAllText(Path.Combine(output, "summary.json"), JsonConvert.SerializeObject(summary, Formatting.Indented));
                Console.WriteLine("CD boot and 120-frame save/restore replay completed. " + count + " hardware frames.");
            }
            return 0;
        }
        catch (Exception error) { Console.Error.WriteLine(error); return 1; }
    }
}
