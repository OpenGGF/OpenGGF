// Purpose: one separately bounded, byte-verified, read-only native phase observation.
// Inputs: original S3K ROM/BK2 and frozen sites.json; pinned stock GPGX API.
// Origin: original-E investigation, 2026-11-10. No state loads, patches or RAM writes.
using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Security.Cryptography;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using OpenGGF.BizHawk.Headless;

public static class S3kHczPhaseProbe
{
    const int First = 37055, Last = 39471;
    static GpgxHost host;
    static StreamWriter events;
    static int inputIndex, advanceBefore, eventSequence;
    static Dictionary<string, int> counts = new Dictionary<string, int>();
    static string Hash(byte[] data)
    {
        using (var s = SHA256.Create()) return BitConverter.ToString(s.ComputeHash(data))
            .Replace("-", "")
            .ToLowerInvariant();
    }
    static byte[] Bytes(string hex)
    {
        return Enumerable.Range(0, hex.Length / 2)
            .Select(i => Convert.ToByte(hex.Substring(i * 2, 2), 16))
            .ToArray();
    }
    static uint Read(int a, int n)
    {
        uint v = 0;
        for (int i = 0; i < n; i++)
            v = (v << 8) | host.ReadMainRamByte(a + i);
        return v;
    }
    static string Hex(int a, int n)
    {
        var b = new byte[n];
        for (int i = 0; i < n; i++)
            b[i] = host.ReadMainRamByte(a + i);
        return BitConverter.ToString(b).Replace("-", "");
    }
    static void Save(string path, object value)
    {
        using (var f = new StreamWriter(new FileStream(path, FileMode.CreateNew, FileAccess.Write)))
            f.WriteLine(JsonConvert.SerializeObject(value, Formatting.Indented));
    }
    static Dictionary<string, string> Raw()
    {
        return new Dictionary<string, string> { { "B000_004A", Hex(0xB000, 0x4A) },
                                                { "EE00_0100", Hex(0xEE00, 0x100) },
                                                { "F600_0080", Hex(0xF600, 0x80) },
                                                { "FE00_0020", Hex(0xFE00, 0x20) },
                                                { "wall_slot11_B32E_004A", Hex(0xB32E, 0x4A) } };
    }
    static Dictionary<string, uint> Named()
    {
        return new Dictionary<string, uint> { { "player_x", Read(0xB010, 2) },
                                              { "player_y", Read(0xB014, 2) },
                                              { "player_routine", Read(0xB005, 1) },
                                              { "camera_x", Read(0xEE78, 2) },
                                              { "camera_y", Read(0xEE7C, 2) },
                                              { "camera_x_copy", Read(0xEE80, 2) },
                                              { "camera_y_copy", Read(0xEE84, 2) },
                                              { "BG_x_copy", Read(0xEE8C, 2) },
                                              { "BG_y_copy_word", Read(0xEE90, 2) },
                                              { "BG_y_copy_long", Read(0xEE90, 4) },
                                              { "camera_x_diff", Read(0xEE3E, 2) },
                                              { "camera_y_diff", Read(0xEE40, 2) },
                                              { "FG_event_stage", Read(0xEEC0, 2) },
                                              { "BG_event_stage", Read(0xEEC2, 2) },
                                              { "end_pulse", Read(0xEEC6, 2) },
                                              { "redraw_position", Read(0xEEC8, 2) },
                                              { "redraw_rows_word", Read(0xEECA, 2) },
                                              { "shake_flag_word", Read(0xEECC, 2) },
                                              { "shake_offset_word", Read(0xEECE, 2) },
                                              { "shake_last_word", Read(0xEED0, 2) },
                                              { "wall_accumulator_u32", Read(0xEED2, 4) },
                                              { "BG_collision_flag", Read(0xF664, 1) },
                                              { "level_frame_counter", Read(0xFE04, 2) },
                                              { "vblank_word", Read(0xFE0E, 2) },
                                              { "ROM_lag_word", Read(0xF628, 2) },
                                              { "zone", Read(0xFE10, 1) },
                                              { "act", Read(0xFE11, 1) },
                                              { "wall_slot11_code", Read(0xB32E, 4) },
                                              { "wall_slot11_x", Read(0xB33E, 2) },
                                              { "wall_slot11_y", Read(0xB342, 2) } };
    }
    static void Apply(Bk2Frame f)
    {
        if (f.P2Active)
            throw new InvalidDataException("Pinned reader cannot faithfully represent active P2.");
        host.ClearButtons();
        string[] n = { "Power",    "Reset", "P1 Up", "P1 Down", "P1 Left",
                       "P1 Right", "P1 A",  "P1 B",  "P1 C",    "P1 Start" };
        bool[] v = { f.Power,   f.Reset, f.P1Up, f.P1Down, f.P1Left,
                     f.P1Right, f.P1A,   f.P1B,  f.P1C,    f.P1Start };
        for (int i = 0; i < n.Length; i++)
            host.SetButton(n[i], v[i]);
        foreach (string name in new[] { "P2 Up", "P2 Down", "P2 Left", "P2 Right", "P2 A", "P2 B",
                                        "P2 C", "P2 Start" })
            host.SetButton(name, false);
    }
    static void Observe(JToken site)
    {
        string name = (string)site["name"];
        counts[name]++;
        var regs = new Dictionary<string, uint>();
        foreach (var r in new[] { "PC", "SR", "D0", "D1", "D2", "D3", "D7", "A0", "A1", "A2", "A3",
                                  "A4", "A5", "A7" })
            regs[r] = host.ReadCpuRegister("M68K " + r);
        uint sp = regs["A7"] & 0xFFFFFF, actor = regs["A0"] & 0xFFFFFF;
        bool stackRam = sp >= 0xFF0000 && sp <= 0xFFFFFF;
        int stackOffset = (int)(sp & 0xFFFF),
            stackLength = stackRam ? Math.Min(32, 65536 - stackOffset) : 0;
        uint actorLow = actor & 0xFFFF;
        bool actorSlot = actor >= 0xFFB000 && actorLow >= 0xB000 &&
                         actorLow < 0xB000 + 110 * 0x4A && (actorLow - 0xB000) % 0x4A == 0;
        string actorClass = actor == 0xFFB000   ? "P1_A0"
                            : actor == 0xFFB04A ? "native_P2_Tails_A0"
                            : actorSlot         ? "other_native_object_slot_A0"
                                                : "unclassified_A0";
        events.WriteLine(JsonConvert.SerializeObject(
            new { sequence = ++eventSequence, site = name, registered_trigger_pc = (uint)site["pc"],
                  observed_cpu_pc = regs["PC"], host_completed_frame_raw = host.CompletedFrame,
                  inflight_input_index_0based = inputIndex,
                  advance_before_completed_frame = advanceBefore,
                  scheduled_completed_target = inputIndex + 1, is_lagged_raw = host.IsLagged,
                  core_lag_count_raw = host.LagCount, registers = regs,
                  stack_address_raw = regs["A7"],
                  stack_MainRAM_hex = stackLength > 0 ? Hex(stackOffset, stackLength) : null,
                  stack_top_u32 = stackLength >= 4 ? (uint?)Read(stackOffset, 4) : null,
                  actor_classification_from_A0_only = actorClass,
                  actor_slot = actorSlot ? (int?)((actorLow - 0xB000) / 0x4A) : null,
                  actor_MainRAM_hex = actorSlot ? Hex((int)actorLow, 0x4A) : null,
                  raw_regions_hex = Raw(), named_unsigned = Named() }));
    }
    static void VerifyBytes(byte[] rom, JToken site, string address, string expected)
    {
        if (site[address] == null)
            return;
        int pc = (int)site[address];
        byte[] bytes = Bytes((string)site[expected]);
        if (pc < 0 || pc + bytes.Length > rom.Length ||
            !rom.Skip(pc).Take(bytes.Length).SequenceEqual(bytes))
            throw new InvalidDataException("Original ROM opcode mismatch before registration: " +
                                           (string)site["name"] + " " + address);
    }
    public static int Main(string[] args)
    {
        if (args.Length != 4)
            throw new ArgumentException(
                "Explicit ROM, movie, frozen sites and new output directory required.");
        string romPath = Path.GetFullPath(args[0]), moviePath = Path.GetFullPath(args[1]),
               sitesPath = Path.GetFullPath(args[2]), output = Path.GetFullPath(args[3]);
        if (Directory.Exists(output))
            throw new IOException("Output exists; no reuse.");
        Directory.CreateDirectory(output);
        byte[] rom = File.ReadAllBytes(romPath);
        using (var sha = SHA1.Create()) if (
            BitConverter.ToString(sha.ComputeHash(rom)).Replace("-", "") !=
            "CFBF98C36C776677290A872547AC47C53D2761D6") throw new InvalidDataException("Wrong canonical ROM.");
        if (Hash(File.ReadAllBytes(moviePath)) !=
            "82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf")
            throw new InvalidDataException("Wrong original movie.");
        var sites = (JArray)JObject.Parse(File.ReadAllText(sitesPath))["sites"];
        foreach (var site in sites)
        {
            VerifyBytes(rom, site, "pc", "expected");
            VerifyBytes(rom, site, "guard_pc", "guard_expected");
            counts[(string)site["name"]] = 0;
        }
        var summary =
            new Dictionary<string, object> { { "first_requested_completed_frame", First },
                                             { "last_requested_completed_frame", Last },
                                             { "opcode_checks_before_registration", true },
                                             { "sites_sha256", Hash(File.ReadAllBytes(sitesPath)) },
                                             { "movie_sha256", Hash(File.ReadAllBytes(moviePath)) },
                                             { "RAM_writes", 0 },
                                             { "state_loads", 0 },
                                             { "video_enabled", false } };
        var hooks = new List<IDisposable>();
        int sampleCount = 0, last = 0;
        bool complete = false;
        try
        {
            var movie = Bk2Reader.Read(moviePath);
            if (movie.FrameCount != 466334)
                throw new InvalidDataException("Wrong full movie input count.");
            using (host = GpgxHost.Open(romPath, movie.SyncSettings)) using (
                events = new StreamWriter(new FileStream(
                    Path.Combine(output, "execute-events.jsonl"), FileMode.CreateNew,
                    FileAccess.Write))) using (var frames =
                                                   new StreamWriter(new FileStream(
                                                       Path.Combine(output,
                                                                    "completed-frames.jsonl"),
                                                       FileMode.CreateNew, FileAccess.Write)))
            {
                try
                {
                    if (host.CompletedFrame != 0)
                        throw new InvalidOperationException("Host is not cold.");
                    foreach (var frame in movie.OpenFrameStream())
                    {
                        Apply(frame);
                        advanceBefore = host.CompletedFrame;
                        if (inputIndex + 1 == First)
                        {
                            summary["registration_before_completed_frame"] = host.CompletedFrame;
                            foreach (var site in sites)
                            {
                                var selected = site;
                                hooks.Add(host.RegisterExecuteCallback((uint)selected["pc"],
                                                                       () => Observe(selected)));
                            }
                        }
                        host.Advance();
                        last = host.CompletedFrame;
                        if (last != inputIndex + 1)
                            throw new InvalidOperationException(
                                "Original movie/completed-frame continuity failed.");
                        if (last >= First)
                        {
                            frames.WriteLine(JsonConvert.SerializeObject(
                                new { sequence = ++sampleCount, completed_frame = last,
                                      bk2_input_index_0based = inputIndex,
                                      trace_row_decimal = last - 27170 - 1,
                                      is_lagged = host.IsLagged, core_lag_count = host.LagCount,
                                      individual_button_mask = frame.OpenGgfInputMask,
                                      trace_logical_mask = S1InputMask.FromFrame(frame),
                                      raw_regions_hex = Raw(), named_unsigned = Named() }));
                            frames.Flush();
                            events.Flush();
                        }
                        inputIndex++;
                        if (last == Last)
                        {
                            complete = true;
                            break;
                        }
                    }
                }
                finally
                {
                    foreach (var hook in hooks)
                        hook.Dispose();
                    hooks.Clear();
                    events.Flush();
                    frames.Flush();
                }
            }
            if (!complete || sampleCount != 2417)
                throw new InvalidOperationException("Incomplete requested native window.");
            summary["execution_status"] = "completed";
        }
        catch (Exception error)
        {
            summary["execution_status"] = "incomplete";
            summary["error"] = error.ToString();
        }
        finally
        {
            summary["samples"] = sampleCount;
            summary["inputs_applied"] = inputIndex;
            summary["last_completed_frame"] = last;
            summary["execute_event_count"] = eventSequence;
            summary["hit_counts"] = counts;
            summary["registered_callbacks_disposed"] = hooks.Count == 0;
            foreach (var name in new[] { "completed-frames.jsonl", "execute-events.jsonl" })
                if (File.Exists(Path.Combine(output, name)))
                    summary[name + "_sha256"] = Hash(File.ReadAllBytes(Path.Combine(output, name)));
            Save(Path.Combine(output, "summary.json"), summary);
        }
        Console.WriteLine(JsonConvert.SerializeObject(summary));
        return complete && (string)summary["execution_status"] == "completed" ? 0 : 2;
    }
}
