// Purpose: comparison-only S2 REV01 Obj7A exhausted-allocation/fixed-tail observations.
// Origin: 2026-10-24 Sideways ownership research, base c008b4d209f7e3a7b930f243f075c9837d970729.
// Inputs: original ROM, original CPZ2 BK2, absent output directory, case name.
// Uses public GpgxHost APIs from TraceChaser 322851cbfa2e1c1803332142e11ab74b4eb34eea.
// Native citations below use S2 pin 34fc7f130a96db171e1a1c43e28e0bad81cd6ca7.
// Declared RAM setup is diagnostic construction, never natural-route/canonical fixture authority.
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using Newtonsoft.Json;
using OpenGGF.BizHawk.Headless;

internal sealed class S2FixedTailPressureProbe
{
    const int Tail = 0xD000, Last = Tail - 0x40, Sidekick = 0xB040;
    const int Cache = 0xF7DF, Camera = 0xEE00, Coarse = 0xF7DA;
    const int MaxIngress = 11000, Window = 360;
    readonly GpgxHost host;
    readonly StreamWriter events;
    readonly string variant;
    readonly int[] parents;
    readonly Dictionary<string, int> counts = new Dictionary<string, int>();
    readonly Dictionary<int, byte[]> before = new Dictionary<int, byte[]>();
    readonly List<string> failures = new List<string>();
    bool setup, dplc, pendingQueue;
    int inputRow, setupRow, sequence, queueBefore, deleteParent;
    int latestParent;
    readonly List<IDisposable> hooks = new List<IDisposable>();

    static void Require(bool condition, string message)
    { if (!condition) throw new InvalidOperationException(message); }
    int B(int address) { return host.ReadMainRamByte(address); }
    int W(int address) { return (B(address) << 8) | B(address + 1); }
    uint L(int address) { return ((uint)W(address) << 16) | (uint)W(address + 2); }
    uint R(string name) { return host.ReadCpuRegister("M68K " + name); }
    int A0 { get { return (int)(R("A0") & 0xFFFF); } }
    byte[] Slot(int address)
    { return Enumerable.Range(address, 64).Select(x => (byte)B(x)).ToArray(); }
    static string Hex(byte[] bytes) { return BitConverter.ToString(bytes).Replace("-", ""); }
    static int Word(byte[] bytes, int offset) { return bytes[offset] * 256 + bytes[offset + 1]; }
    static uint Long(byte[] bytes, int offset)
    { return ((uint)Word(bytes, offset) << 16) | (uint)Word(bytes, offset + 2); }
    static string Sha(string path, bool sha1 = false)
    { using (var h = sha1 ? (HashAlgorithm)SHA1.Create() : SHA256.Create())
        using (var s = File.OpenRead(path)) return Hex(h.ComputeHash(s)).ToLowerInvariant(); }
    void Count(string name) { counts[name] = CountOf(name) + 1; }
    int CountOf(string name) { return counts.ContainsKey(name) ? counts[name] : 0; }
    void Check(bool pass, string message)
    { if (!pass) { failures.Add(message); Record("assertion_failed", new { message }); } }
    object Registers()
    {
        return new { a0 = R("A0"), a1 = R("A1"), a2 = R("A2"), a7 = R("A7"),
            pc = R("PC"), sr = R("SR"), d0 = R("D0"), d1 = R("D1"),
            d2 = R("D2"), d3 = R("D3"), caller = L((int)(R("A7") & 0xFFFF)) };
    }
    void Record(string boundary, object detail = null)
    {
        Count(boundary);
        events.WriteLine(JsonConvert.SerializeObject(new { sequence = ++sequence, boundary,
            input_row = inputRow, completed_frame = host.CompletedFrame, detail,
            registers = Registers(), tail = Hex(Slot(Tail)), tail_id = B(Tail),
            tail_parent_word = W(Tail + 0x3E), cache = B(Cache),
            dma_slot = L(0xDCFC), camera = W(Camera), camera_coarse = W(Coarse),
            parents = parents.Select(p => new { address = p, bytes = Hex(Slot(p)),
                remember = B(0xFC02 + (p == Last ? 0xBD : 0xBC)) }).ToArray() }));
    }
    void Write(int address, byte value, string reason)
    {
        int old = B(address);
        events.WriteLine(JsonConvert.SerializeObject(new { sequence = ++sequence,
            boundary = "setup_write", input_row = inputRow, completed_frame = host.CompletedFrame,
            address, before = old, after = value, reason }));
        host.WriteMainRamByte(address, value);
        Count("setup_write");
    }
    void WriteWord(int address, int value, string reason)
    { Write(address, (byte)(value >> 8), reason); Write(address + 1, (byte)value, reason); }

    void Setup()
    {
        Require(!setup, "Setup may run only once");
        Require((B(0xF600) & 0x7F) == 0x0C && B(0xFE10) == 0x0D && B(0xFE11) == 1,
            "Setup must occur in native CPZ2 level dispatch");
        Require(B(Sidekick) == 2 && B(Sidekick + 0x24) == 2 && B(Tail) == 5,
            "Expected naturally live Sidekick and fixed tail before declared setup");
        Require(parents.All(p => B(p) == 0), "Constructed parent slots must be ID-empty");
        Record("setup_before");
        int origin = W(Camera) + 0xA8;
        foreach (int p in parents)
        {
            for (int i = 0; i < 64; i++) Write(p + i, 0, "clear ID-empty parent storage");
            Write(p, 0x7A, "construct native Obj7A ID");
            Write(p + 0x28, 6, "Obj7A_Properties subtype06 paired form");
            WriteWord(p + 8, origin, "prepared original X; native Init applies offsets/bounds");
            WriteWord(p + 12, W(0xEE04) + 0x200, "prepared Y below viewport, avoids route contact");
            int remember = p == Last ? 0xBD : 0xBC;
            Require((B(0xFC02 + remember) & 0x80) == 0, "Remember bit must initially be unused");
            Write(p + 0x23, (byte)remember, "declared unused respawn index, not placement-loader proof");
            Write(0xFC02 + remember, (byte)(B(0xFC02 + remember) | 0x80), "declared remembered parent");
            origin += 0x80;
        }
        if (variant.StartsWith("empty") || variant.StartsWith("reinstall"))
            for (int i = 0; i < 64; i++) Write(Tail + i, 0, "declared absent fixed target; DPLC cache untouched");
        if (variant.StartsWith("reinstall"))
            Write(Sidekick + 0x24, 0, "one-time native Sidekick Init trigger after its dispatch; later stores are ROM-only");
        setup = true; setupRow = inputRow;
        Record("setup_after");
    }

    void Hook(uint address, string expected, Action action, byte[] rom)
    {
        string actual = Hex(rom.Skip((int)address).Take(expected.Length / 2).ToArray());
        Require(actual == expected, "Hook opcode mismatch at " + address.ToString("X"));
        hooks.Add(host.RegisterExecuteCallback(address, action));
    }
    void InstallHooks(byte[] rom)
    {
        // RunObject/RunNextObject (s2.asm:29832-29846): sample even ID-zero fixed storage.
        Hook(0x15FCC, "1010670C", () => {
            int p = A0;
            if (!setup && p == parents[0]) Setup();
            if (setup && p == Tail) {
                if (CountOf("fixed_walk") == 0)
                    Check(B(Tail) == (variant.StartsWith("live") ? 5 : 0), "First fixed walk must see declared live/empty target");
                if (B(Tail) == 0) Count("empty_fixed_walk");
                Record("fixed_walk");
            }
        }, rom);
        Hook(0x15FDC, "41E80040", () => {
            if (setup && deleteParent == A0 && B(A0) == 0)
            {
                Record("parent_deleted", new { address = A0 });
                Check(Slot(A0).All(x => x == 0), "Parent deletion must clear complete SST");
                Check((B(0xFC02 + (A0 == Last ? 0xBD : 0xBC)) & 0x80) == 0,
                    "Native retirement must release remembered bit");
                if (deleteParent == A0)
                {
                    Check(Slot(Tail).All(x => x == 0), "Paired retirement must clear current physical target");
                    Record("pair_clear_confirmed", new { address = A0 }); deleteParent = 0;
                }
            }
        }, rom);
        // Obj7A init/failure tail (s2.asm:56295-56353); no child construction on failure.
        Hook(0x293CC, "54280024", () => { if (setup && parents.Contains(A0)) Record("parent_init"); }, rom);
        Hook(0x29408, "61000174663C", () => {
            if (setup && parents.Contains(A0)) { before[A0] = Slot(Tail); Record("allocator_call"); }
        }, rom);
        Hook(0x18014, "4E75", () => {
            if (setup && parents.Contains(A0) && before.ContainsKey(A0))
            {
                Record("allocator_return");
                Check(R("A1") == 0xFFFFD000U, "Exhaustion must leave exact fixed-tail address in A1");
                Check((R("SR") & 4) == 0, "Exhausted allocation must return Z clear");
            }
        }, rom);
        Hook(0x29426, "236800040004", () => {
            if (setup && parents.Contains(A0)) {
                Record("load_subobject"); Check((R("A1") & 0xFFFF) == A0, "Only parent's own initialization is allowed");
            }
        }, rom);
        Hook(0x2948E, "61644A78FFD8", () => {
            int p = A0;
            if (!setup || !before.ContainsKey(p)) return;
            byte[] prior = before[p], now = Slot(Tail);
            Record("post_failure_writes", new { address = p, prior_target = Hex(prior) });
            Check(L(p + 0x3C) == 0xFFFFD000U && L(Tail + 0x3C) == (0xFFFF0000U | (uint)p), "Reciprocal physical pointers");
            Check(W(Tail + 0x32) == W(p + 0x32) && W(Tail + 0x34) == W(p + 0x34), "Shared boundaries");
            Check(W(Tail + 8) == ((Word(prior, 8) + 0x40) & 0xFFFF), "Failure adds child offset to existing X word");
            for (int i = 0; i < 64; i++)
                if (i != 8 && i != 9 && (i < 0x32 || i > 0x35) && i < 0x3C)
                    Check(now[i] == prior[i], "Failure unexpectedly changed untouched target byte " + i);
            latestParent = p; before.Remove(p);
        }, rom);
        // Obj05 parent/animation and independent DPLC request boundaries (s2.asm:41713-41764,41637-41693).
        Hook(0x1D212, "54280024", () => { if (setup && A0 == Tail) Record("obj05_init"); }, rom);
        Hook(0x1D23A, "3468003E", () => { if (setup && A0 == Tail) Record("obj05_main"); }, rom);
        Hook(0x1D288, "43F90001D2C0", () => {
            if (!setup || A0 != Tail) return;
            int parent = W(Tail + 0x3E);
            Record("obj05_copied_parent", new { resolved_parent = parent, parent_bytes = Hex(Slot(parent)) });
            Check(W(Tail + 8) == W(parent + 8) && W(Tail + 12) == W(parent + 12)
                && B(Tail + 0x26) == B(parent + 0x26) && B(Tail + 0x22) == B(parent + 0x22), "Actual Obj05 parent copies");
            if (parents.Contains(parent)) Count("redirected_parent_consumed");
        }, rom);
        Hook(0x1D184, "70001028001A", () => {
            if (setup && A0 == Tail) { dplc = true; Record("tail_dplc_entry"); }
        }, rom);
        Hook(0x1D1FE, "4E75", () => {
            if (dplc && A0 == Tail) { Record("tail_dplc_return"); dplc = false; }
        }, rom);
        Hook(0x144E, "2278DCFC", () => {
            if (!dplc || A0 != Tail || (L((int)(R("A7") & 0xFFFF)) & 0xFFFFFF) != 0x1D1FA) return;
            pendingQueue = true; queueBefore = (int)L(0xDCFC);
            Record("tail_dma_request", new { source = R("D1"), destination = R("D2"), words = R("D3") });
        }, rom);
        Hook(0x14AA, "4E75", () => {
            if (!pendingQueue) return;
            Record("tail_dma_return", new { queue_before = queueBefore, queue_after = L(0xDCFC),
                commands = Hex(Enumerable.Range(0xDC00, 252).Select(x => (byte)B(x)).ToArray()) });
            if ((int)L(0xDCFC) == queueBefore + 14) Count("tail_dma_accepted");
            pendingQueue = false;
        }, rom);
        // Obj02_Control follows actual installation stores, gated by entry through Init_Continued.
        bool installing = false;
        Hook(0x1B96E, "31E80008", () => { if (setup && A0 == Sidekick) { installing = true; Record("native_install_entry"); } }, rom);
        Hook(0x1B9B4, "B0FCB000", () => {
            if (installing && A0 == Sidekick) {
                Record("native_install_completed"); installing = false;
                Check(B(Tail) == 5 && W(Tail + 0x3E) == Sidekick, "Native installation must install actual Sidekick parent");
            }
        }, rom);
        // Physical pair retirement (s2.asm:56374-56387,30334-30345).
        Hook(0x294C4, "2268003C", () => {
            if (setup && parents.Contains(A0)) { deleteParent = A0; Record("parent_retire"); }
        }, rom);
        Hook(0x164E8, "7200700F", () => {
            if (deleteParent != 0 && A0 == deleteParent && (R("A1") & 0xFFFF) == Tail)
                Record("physical_tail_delete", new { current_id = B(Tail), current_parent = W(Tail + 0x3E) });
        }, rom);
    }

    S2FixedTailPressureProbe(GpgxHost host, StreamWriter events, string variant)
    {
        this.host = host; this.events = events; this.variant = variant;
        parents = variant.EndsWith("two") ? new[] { Last - 64, Last } : new[] { Last };
    }
    static void Apply(Bk2Frame f, GpgxHost h)
    {
        h.ClearButtons();
        string[] names = { "Power", "Reset", "P1 Up", "P1 Down", "P1 Left", "P1 Right", "P1 A", "P1 B", "P1 C", "P1 Start" };
        bool[] values = { f.Power, f.Reset, f.P1Up, f.P1Down, f.P1Left, f.P1Right, f.P1A, f.P1B, f.P1C, f.P1Start };
        for (int i = 0; i < names.Length; i++) if (values[i]) h.SetButton(names[i], true);
    }
    public static int Main(string[] args)
    {
        Require(args.Length == 4, "Arguments: original ROM, original CPZ2 BK2, absent output directory, case");
        Require(new[] { "live-last", "empty-last", "reinstall-last", "live-two", "empty-two", "reinstall-two" }.Contains(args[3]), "Unknown case");
        string rom = Path.GetFullPath(args[0]), moviePath = Path.GetFullPath(args[1]), output = Path.GetFullPath(args[2]);
        Require(!Directory.Exists(output) && !File.Exists(output), "Output must be absent");
        Require(Sha(rom, true) == "8bca5dcef1af3e00098666fd892dc1c2a76333f9", "S2 REV01 SHA1 mismatch");
        Directory.CreateDirectory(output);
        var movie = Bk2Reader.Read(moviePath);
        var summary = new Dictionary<string, object> { { "execution_status", "started" }, { "case", args[3] },
            { "rom_sha1", Sha(rom, true) }, { "movie_sha256", Sha(moviePath) }, { "max_ingress_frames", MaxIngress }, { "window_frames", Window } };
        int result = 2;
        try
        {
            using (var host = GpgxHost.Open(rom, movie.SyncSettings))
            using (var events = new StreamWriter(Path.Combine(output, "observations.jsonl")))
            {
                var p = new S2FixedTailPressureProbe(host, events, args[3]);
                bool armed = false; int previous = -1;
                foreach (Bk2Frame frame in movie.OpenFrameStream())
                {
                    p.inputRow++;
                    if (p.setup) Require(!frame.Power && !frame.Reset, "Reset/power inside observation window");
                    Apply(frame, host); host.Advance();
                    if (p.setup)
                    {
                        if (previous >= 0) p.Check(host.CompletedFrame == previous + 1, "Completed-frame discontinuity");
                        previous = host.CompletedFrame;
                        p.Record("frame_end");
                        if (p.inputRow - p.setupRow >= Window) break;
                    }
                    if (!armed && (p.B(0xF600) & 0x7F) == 0x0C && p.B(0xFE10) == 0x0D && p.B(0xFE11) == 1
                        && p.B(0xF7CC) == 0 && p.B(0xB024) == 2 && p.B(Sidekick) == 2 && p.B(Sidekick + 0x24) == 2
                        && p.B(Tail) == 5 && p.B(Tail + 0x24) == 2 && p.parents.All(x => p.B(x) == 0))
                    { p.InstallHooks(File.ReadAllBytes(rom)); armed = true; }
                    if (!p.setup && p.inputRow >= MaxIngress) break;
                }
                p.Check(p.setup && p.inputRow - p.setupRow == Window, "Required setup and complete finite window");
                foreach (string boundary in new[] { "parent_init", "allocator_call", "allocator_return", "load_subobject", "post_failure_writes" })
                    p.Check(p.CountOf(boundary) == p.parents.Length, "Exact per-parent boundary count: " + boundary);
                p.Check(p.CountOf("fixed_walk") > 0, "Fixed slot must actually be visited");
                p.Check(p.CountOf("parent_retire") == p.parents.Length && p.CountOf("pair_clear_confirmed") == p.parents.Length,
                    "Every constructed parent must reach retirement and confirmed paired clear");
                if (args[3].StartsWith("live"))
                    p.Check(p.CountOf("redirected_parent_consumed") > 0, "Live target must consume redirected parent");
                if (args[3].StartsWith("reinstall"))
                    p.Check(p.CountOf("native_install_completed") == 1 && p.CountOf("obj05_init") == 1,
                        "Exactly one real native installation and Obj05 initialization required");
                summary["setup_input_row"] = p.setupRow; summary["last_input_row"] = p.inputRow;
                summary["counts"] = p.counts; summary["unmet_assertions"] = p.failures;
                summary["execution_status"] = p.failures.Count == 0 ? "completed" : "incomplete";
                result = p.failures.Count == 0 ? 0 : 2;
                foreach (var hook in p.hooks) hook.Dispose();
            }
        }
        catch (Exception e) { summary["execution_status"] = "failed"; summary["error"] = e.ToString(); result = 1; }
        summary["observations_sha256"] = File.Exists(Path.Combine(output, "observations.jsonl")) ? Sha(Path.Combine(output, "observations.jsonl")) : null;
        File.WriteAllText(Path.Combine(output, "summary.json"), JsonConvert.SerializeObject(summary, Formatting.Indented) + "\n");
        Console.WriteLine(JsonConvert.SerializeObject(summary));
        return result;
    }
}
