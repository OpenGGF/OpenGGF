// Purpose: one cold, read-only HCZ completed-frame RAM observation.
// Inputs: explicitly verified original S3K ROM and unmodified Sonic/Tails BK2.
// Origin: 2026-11-10 original-E frame-contract investigation; pinned TraceChaser API.
using System;
using System.IO;
using System.Collections.Generic;
using System.Reflection;
using System.Security.Cryptography;
using Newtonsoft.Json;
using OpenGGF.BizHawk.Headless;

public static class S3kHczColdProbe
{
    const int First = 37055, Last = 39471, Checkpoint = 37054;
    static string Hash(byte[] bytes)
    {
        using (var h = SHA256.Create()) return BitConverter.ToString(h.ComputeHash(bytes))
            .Replace("-", "")
            .ToLowerInvariant();
    }
    static string FileHash(string path)
    {
        return Hash(File.ReadAllBytes(path));
    }
    static string Hex(GpgxHost host, int address, int count)
    {
        var b = new byte[count];
        for (int i = 0; i < count; i++)
            b[i] = host.ReadMainRamByte(address + i);
        return BitConverter.ToString(b).Replace("-", "");
    }
    static uint Read(GpgxHost host, int address, int count)
    {
        uint v = 0;
        for (int i = 0; i < count; i++)
            v = (v << 8) | host.ReadMainRamByte(address + i);
        return v;
    }
    static void SaveJson(string path, object value)
    {
        using (var f = new StreamWriter(new FileStream(path, FileMode.CreateNew, FileAccess.Write)))
            f.WriteLine(JsonConvert.SerializeObject(value, Formatting.Indented));
    }
    static void Apply(Bk2Frame frame, GpgxHost host)
    {
        // The pinned reader rejects active P2 input. The original entire movie
        // was independently checked to have neutral P2, which remains neutral.
        if (frame.P2Active)
            throw new InvalidDataException(
                "Active P2 cannot be represented faithfully by the pinned reader.");
        host.ClearButtons();
        string[] names = { "Power",    "Reset", "P1 Up", "P1 Down", "P1 Left",
                           "P1 Right", "P1 A",  "P1 B",  "P1 C",    "P1 Start" };
        bool[] values = { frame.Power,   frame.Reset, frame.P1Up, frame.P1Down, frame.P1Left,
                          frame.P1Right, frame.P1A,   frame.P1B,  frame.P1C,    frame.P1Start };
        for (int i = 0; i < names.Length; i++)
            host.SetButton(names[i], values[i]);
        foreach (var button in new[] { "P2 Up", "P2 Down", "P2 Left", "P2 Right", "P2 A", "P2 B",
                                       "P2 C", "P2 Start" })
            host.SetButton(button, false);
    }
    public static int Main(string[] args)
    {
        if (args.Length != 3)
            throw new ArgumentException("Explicit ROM, BK2 and new output directory required.");
        string rom = Path.GetFullPath(args[0]), moviePath = Path.GetFullPath(args[1]),
               output = Path.GetFullPath(args[2]);
        if (Directory.Exists(output))
            throw new IOException("Output already exists.");
        Directory.CreateDirectory(output);
        var summary =
            new Dictionary<string, object> { { "schema", 1 },
                                             { "boundary", "after ordinary Advance returns" },
                                             { "video_enabled", false },
                                             { "execute_callbacks", 0 },
                                             { "RAM_writes", 0 },
                                             { "state_loads", 0 },
                                             { "first_requested_completed_frame", First },
                                             { "last_requested_completed_frame", Last },
                                             { "expected_samples", Last - First + 1 },
                                             { "movie_sha256", FileHash(moviePath) } };
        int inputIndex = 0, samples = 0, last = 0;
        bool complete = false;
        string observations = Path.Combine(output, "completed-frames.jsonl");
        try
        {
            using (var sha = SHA1.Create()) if (
                BitConverter.ToString(sha.ComputeHash(File.ReadAllBytes(rom))).Replace("-", "") !=
                "CFBF98C36C776677290A872547AC47C53D2761D6") throw new InvalidDataException("Canonical S3K SHA1 mismatch.");
            if (FileHash(moviePath) !=
                "82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf")
                throw new InvalidDataException("Original BK2 hash mismatch.");
            var movie = Bk2Reader.Read(moviePath);
            if (movie.FrameCount != 466334)
                throw new InvalidDataException("Original input record count mismatch.");
            summary["movie_sync_settings"] = JsonConvert.SerializeObject(movie.SyncSettings);
            // Existing pinned CloneSavestate is a read/serialize operation. This
            // exact internal method is invoked only to retain an owned same-movie
            // checkpoint. No load/restore, RAM writer or callback is accessed.
            var clone = typeof(GpgxHost).GetMethod("CloneSavestate",
                                                   BindingFlags.Instance | BindingFlags.NonPublic);
            if (clone != null &&
                (clone.ReturnType != typeof(byte[]) || clone.GetParameters().Length != 0))
                throw new InvalidOperationException("Unexpected pinned checkpoint API shape.");
            using (var host = GpgxHost.Open(rom, movie.SyncSettings)) using (
                var writer = new StreamWriter(
                    new FileStream(observations, FileMode.CreateNew, FileAccess.Write)))
            {
                summary["initial_completed_frame"] = host.CompletedFrame;
                summary["main_RAM_domain"] = host.MainRamDomainName;
                summary["main_RAM_bytes"] = host.MainRamDomainSize;
                if (host.CompletedFrame != 0)
                    throw new InvalidOperationException(
                        "Cold host did not begin at completed frame0.");
                foreach (var frame in movie.OpenFrameStream())
                {
                    Apply(frame, host);
                    host.Advance();
                    last = host.CompletedFrame;
                    if (last != inputIndex + 1)
                        throw new InvalidOperationException(
                            "Original input/completed-frame discontinuity.");
                    if (last == Checkpoint)
                    {
                        if (clone == null)
                            summary["checkpoint_status"] = "not available from pinned host";
                        else
                        {
                            var bytes = (byte[])clone.Invoke(host, null);
                            string state = Path.Combine(output, "native-37054.state");
                            using (var file =
                                       new FileStream(state, FileMode.CreateNew, FileAccess.Write))
                                file.Write(bytes, 0, bytes.Length);
                            var receipt = new {
                                completed_frame = last,
                                input_index_just_applied = inputIndex,
                                next_input_index = inputIndex + 1,
                                is_lagged = host.IsLagged,
                                lag_count = host.LagCount,
                                bytes = bytes.Length,
                                sha256 = Hash(bytes),
                                method =
                                    "pinned GpgxHost.CloneSavestate; serialization only; no loads"
                            };
                            SaveJson(Path.Combine(output, "native-37054.json"), receipt);
                            summary["checkpoint"] = receipt;
                        }
                    }
                    if (last >= First)
                    {
                        int wall = 0xB000 + 11 * 0x4A;
                        uint code = Read(host, wall, 4);
                        var raw = new Dictionary<string, string> {
                            { "B000_004A", Hex(host, 0xB000, 0x4A) },
                            { "EE00_0100", Hex(host, 0xEE00, 0x100) },
                            { "F600_0080", Hex(host, 0xF600, 0x80) },
                            { "FE00_0020", Hex(host, 0xFE00, 0x20) },
                            { "wall_slot11_B32E_004A", Hex(host, wall, 0x4A) }
                        };
                        var words = new Dictionary<string, object> {
                            { "player_x", Read(host, 0xB010, 2) },
                            { "player_y", Read(host, 0xB014, 2) },
                            { "player_routine", Read(host, 0xB005, 1) },
                            { "camera_x", Read(host, 0xEE78, 2) },
                            { "camera_y", Read(host, 0xEE7C, 2) },
                            { "camera_x_copy", Read(host, 0xEE80, 2) },
                            { "camera_y_copy", Read(host, 0xEE84, 2) },
                            { "BG_x_copy", Read(host, 0xEE8C, 2) },
                            { "BG_y_copy_word", Read(host, 0xEE90, 2) },
                            { "BG_y_copy_long", Read(host, 0xEE90, 4) },
                            { "FG_event_stage", Read(host, 0xEEC0, 2) },
                            { "BG_event_stage", Read(host, 0xEEC2, 2) },
                            { "end_pulse", Read(host, 0xEEC6, 2) },
                            { "redraw_position", Read(host, 0xEEC8, 2) },
                            { "redraw_rows_word", Read(host, 0xEECA, 2) },
                            { "shake_flag_word", Read(host, 0xEECC, 2) },
                            { "shake_offset_word", Read(host, 0xEECE, 2) },
                            { "shake_last_word", Read(host, 0xEED0, 2) },
                            { "wall_accumulator_u32", Read(host, 0xEED2, 4) },
                            { "BG_collision_flag", Read(host, 0xF664, 1) },
                            { "level_frame_counter", Read(host, 0xFE04, 2) },
                            { "vblank_word", Read(host, 0xFE0E, 2) },
                            { "ROM_lag_word", Read(host, 0xF628, 2) },
                            { "zone", Read(host, 0xFE10, 1) },
                            { "act", Read(host, 0xFE11, 1) },
                            { "wall_slot11_code", code },
                            { "wall_slot11_correct_owner", code == 0x000510DA },
                            { "wall_slot11_x", Read(host, wall + 0x10, 2) },
                            { "wall_slot11_y", Read(host, wall + 0x14, 2) }
                        };
                        writer.WriteLine(JsonConvert.SerializeObject(
                            new { sequence = ++samples, completed_frame = last,
                                  bk2_input_index_0based = inputIndex,
                                  trace_row_decimal = last - 27170 - 1, is_lagged = host.IsLagged,
                                  core_lag_count = host.LagCount,
                                  input_mask = frame.OpenGgfInputMask, raw_regions_hex = raw,
                                  named_unsigned = words }));
                    }
                    inputIndex++;
                    if (last == Last)
                    {
                        complete = true;
                        break;
                    }
                }
                writer.Flush();
            }
            if (!complete || samples != Last - First + 1)
                throw new InvalidOperationException(
                    "Incomplete requested cold observation window.");
            summary["execution_status"] = "completed";
        }
        catch (Exception error)
        {
            summary["execution_status"] = "incomplete";
            summary["error"] = error.ToString();
        }
        finally
        {
            summary["inputs_applied"] = inputIndex;
            summary["last_completed_frame"] = last;
            summary["samples"] = samples;
            if (File.Exists(observations))
                summary["observations_sha256"] = FileHash(observations);
            SaveJson(Path.Combine(output, "summary.json"), summary);
        }
        Console.WriteLine(JsonConvert.SerializeObject(summary));
        return complete && samples == Last - First + 1 &&
                       (string)summary["execution_status"] == "completed"
                   ? 0
                   : 2;
    }
}
