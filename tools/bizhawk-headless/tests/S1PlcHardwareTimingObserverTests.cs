using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Behavioural cover for the S1 PLC hardware-timing readiness stream.
    ///
    /// The expected fingerprints are FROZEN LITERALS, computed once from
    /// the documented submission-identity fields, not re-derived by calling
    /// the production fingerprint routine — a test that recomputes what it
    /// asserts cannot notice the routine's inputs changing.
    /// </summary>
    internal static class S1PlcHardwareTimingObserverTests
    {
        /// <summary>
        /// NEMESIS_PLC_QUEUE / source 0x022670 / compressed 0 / destination
        /// tile 0x640 (VRAM 0xC800 / 32) / 0x2E patterns / "nemesis" / 0
        /// modules.
        /// </summary>
        private const string FirstFingerprint =
            "sha256:ca21ba81e5cb7ab33a5f9875d15e37631662451562aff02529bbaa"
            + "7d845f1e5e";

        /// <summary>
        /// NEMESIS_PLC_QUEUE / source 0x0304F0 / compressed 0 / destination
        /// tile 0x680 (VRAM 0xD000 / 32) / 0x11 patterns / "nemesis" / 0
        /// modules.
        /// </summary>
        private const string SecondFingerprint =
            "sha256:71a9ac1a6ad75a03ce592966c6370497d5ea23862149c00d4e0d1ec"
            + "ba5744f4b";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1PlcHardwareTimingObserver writes an armed edge in the v5 per-segment shape",
                WritesArmedEdge));
            tests.Add(new TestMain.TestCase(
                "S1PlcHardwareTimingObserver ignores a RunPLC entry that arms nothing",
                IgnoresNonArmingEntry));
            tests.Add(new TestMain.TestCase(
                "S1PlcHardwareTimingObserver keeps ordinals gapless across dropped frames",
                KeepsOrdinalsGaplessAcrossDroppedFrames));
            tests.Add(new TestMain.TestCase(
                "S1PlcHardwareTimingObserver drops everything observed before the arm",
                DropsPreArmObservations));
            tests.Add(new TestMain.TestCase(
                "S1PlcHardwareTimingObserver carries its ordinal across segments",
                CarriesOrdinalAcrossSegments));
            tests.Add(new TestMain.TestCase(
                "S1PlcHardwareTimingObserver rejects an impossible armed descriptor",
                RejectsImpossibleDescriptor));
            tests.Add(new TestMain.TestCase(
                "S1PlcHardwareTimingObserver unregisters its callback deterministically",
                UnregistersCallback));
            string romPath = Environment.GetEnvironmentVariable("S1_ROM_PATH");
            if (!String.IsNullOrEmpty(romPath) && File.Exists(romPath))
            {
                tests.Add(new TestMain.TestCase(
                    "S1PlcHardwareTimingObserver pins RunPLC's entry PC in the retail ROM",
                    PinsRunPlcEntryInRetailRom,
                    game: "s1",
                    kind: TestKind.Gate,
                    estimatedSeconds: 0.2));
            }
        }

        private static void WritesArmedEdge()
        {
            var host = new FakeS1Host(null);
            var writer = new StringWriter();
            using (var observer = new S1PlcHardwareTimingObserver(
                CreateRom(), host))
            {
                observer.ArmSegment(writer);
                observer.BeginFrame();
                ArmHead(host, 0x022670, 0xC800);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);
                observer.CommitRow(0);
            }
            AssertEx.Equal(
                "{\"event\":\"hardware_work_completed\",\"raw_frame\":0,"
                + "\"boundary\":\"pre_main_loop\","
                + "\"kind\":\"nemesis_plc_queue\",\"ordinal\":0,"
                + "\"submission_fingerprint\":\"" + FirstFingerprint
                + "\"}\n",
                writer.ToString());
        }

        /// <summary>
        /// RunPLC returns immediately when the queue is empty or a section
        /// counter is already set (sonic.asm:1380-1383). Neither entry is
        /// an arming edge, and a capture that sees only these must leave
        /// its lazy writer unopened — no file at all.
        /// </summary>
        private static void IgnoresNonArmingEntry()
        {
            var host = new FakeS1Host(null);
            var lazy = new LazyOpenTextWriter(
                () => { throw new InvalidOperationException("opened"); });
            using (var observer = new S1PlcHardwareTimingObserver(
                CreateRom(), host))
            {
                observer.ArmSegment(lazy);

                observer.BeginFrame();
                host.SetU32(S1Ram.PlcBuffer, 0);
                host.SetU16(S1Ram.PlcPatternsLeft, 0);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);
                observer.CommitRow(0);

                observer.BeginFrame();
                ArmHead(host, 0x022670, 0xC800);
                host.SetU16(S1Ram.PlcPatternsLeft, 0x2E);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);
                observer.CommitRow(1);
            }
            AssertEx.Equal(false, lazy.Opened);
        }

        private static void KeepsOrdinalsGaplessAcrossDroppedFrames()
        {
            var host = new FakeS1Host(null);
            var writer = new StringWriter();
            using (var observer = new S1PlcHardwareTimingObserver(
                CreateRom(), host))
            {
                observer.ArmSegment(writer);

                // Two arms inside one frame, in call order.
                observer.BeginFrame();
                ArmHead(host, 0x022670, 0xC800);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);
                ArmHead(host, 0x0304F0, 0xD000);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);
                observer.CommitRow(4);

                // A frame that produces no row: its edge is dropped and
                // consumes no ordinal.
                observer.BeginFrame();
                ArmHead(host, 0x022670, 0xC800);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);

                observer.BeginFrame();
                ArmHead(host, 0x0304F0, 0xD000);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);
                observer.CommitRow(5);
            }
            string[] lines = Lines(writer.ToString());
            AssertEx.Equal(3, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"raw_frame\":4,"));
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0,"));
            AssertEx.Equal(true, lines[0].Contains(FirstFingerprint));
            AssertEx.Equal(true, lines[1].Contains("\"raw_frame\":4,"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1,"));
            AssertEx.Equal(true, lines[1].Contains(SecondFingerprint));
            AssertEx.Equal(true, lines[2].Contains("\"raw_frame\":5,"));
            AssertEx.Equal(true, lines[2].Contains("\"ordinal\":2,"));
        }

        /// <summary>
        /// The level load's own PLC arming happens before any row exists.
        /// It belongs to no raw_frame and must not appear.
        /// </summary>
        private static void DropsPreArmObservations()
        {
            var host = new FakeS1Host(null);
            var writer = new StringWriter();
            using (var observer = new S1PlcHardwareTimingObserver(
                CreateRom(), host))
            {
                ArmHead(host, 0x022670, 0xC800);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);
                observer.ArmSegment(writer);
                observer.CommitRow(0);
            }
            AssertEx.Equal("", writer.ToString());
        }

        private static void CarriesOrdinalAcrossSegments()
        {
            var host = new FakeS1Host(null);
            var first = new StringWriter();
            var second = new StringWriter();
            using (var observer = new S1PlcHardwareTimingObserver(
                CreateRom(), host))
            {
                observer.ArmSegment(first);
                observer.BeginFrame();
                ArmHead(host, 0x022670, 0xC800);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);
                observer.CommitRow(0);
                observer.EndSegment();

                observer.ArmSegment(second);
                observer.BeginFrame();
                ArmHead(host, 0x0304F0, 0xD000);
                host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc);
                observer.CommitRow(0);
                observer.EndSegment();
            }
            AssertEx.Equal(true, first.ToString().Contains("\"ordinal\":0,"));
            AssertEx.Equal(
                true, second.ToString().Contains("\"ordinal\":1,"));
        }

        private static void RejectsImpossibleDescriptor()
        {
            var host = new FakeS1Host(null);
            using (var observer = new S1PlcHardwareTimingObserver(
                CreateRom(), host))
            {
                observer.ArmSegment(new StringWriter());
                observer.BeginFrame();
                ArmHead(host, 0x0F0000, 0xC800);
                AssertEx.Throws<InvalidDataException>(
                    () => host.FireExecuteCallback(
                        S1PlcHardwareTimingObserver.RunPlcEntryPc),
                    "outside the supplied");

                ArmHead(host, 0x000100, 0xC800);
                AssertEx.Throws<InvalidDataException>(
                    () => host.FireExecuteCallback(
                        S1PlcHardwareTimingObserver.RunPlcEntryPc),
                    "zero patterns");
            }
        }

        private static void UnregistersCallback()
        {
            var host = new FakeS1Host(null);
            var observer = new S1PlcHardwareTimingObserver(
                CreateRom(), host);
            AssertEx.Equal(
                S1PlcHardwareTimingObserver.RunPlcEntryPc,
                host.ExecuteCallbackAddress.Value);
            observer.Dispose();
            AssertEx.Equal(true, host.ExecuteCallbackDisposed);
            AssertEx.Throws<InvalidOperationException>(
                () => host.FireExecuteCallback(
                    S1PlcHardwareTimingObserver.RunPlcEntryPc),
                "No execute callback is registered");
            observer.Dispose();
        }

        /// <summary>
        /// RunPLC's entry, identified by the routine's own opening test
        /// pair (sonic.asm:1380-1383): tst.l (v_plc_buffer).w / beq.s /
        /// tst.w (v_plc_patternsleft).w / bne.s. The pattern occurs exactly
        /// once in the retail ROM, so the pinned PC is not a coincidence of
        /// one build's layout.
        /// </summary>
        private static void PinsRunPlcEntryInRetailRom()
        {
            byte[] rom = File.ReadAllBytes(
                Environment.GetEnvironmentVariable("S1_ROM_PATH"));
            var matches = new List<int>();
            for (int offset = 0; offset + 11 <= rom.Length; offset++)
            {
                if (rom[offset] == 0x4A && rom[offset + 1] == 0xB8
                    && rom[offset + 2] == 0xF6 && rom[offset + 3] == 0x80
                    && rom[offset + 4] == 0x67
                    && rom[offset + 6] == 0x4A && rom[offset + 7] == 0x78
                    && rom[offset + 8] == 0xF6 && rom[offset + 9] == 0xF8
                    && rom[offset + 10] == 0x66)
                {
                    matches.Add(offset);
                }
            }
            AssertEx.Equal(1, matches.Count);
            AssertEx.Equal(
                (int)S1PlcHardwareTimingObserver.RunPlcEntryPc, matches[0]);
        }

        /// <summary>
        /// Puts a descriptor at the head of v_plc_buffer with no section
        /// counter set — the state RunPLC arms from.
        /// </summary>
        private static void ArmHead(
            FakeS1Host host, uint source, ushort destination)
        {
            host.SetU32(S1Ram.PlcBuffer, source);
            host.SetU16(S1Ram.PlcBuffer + 4, destination);
            host.SetU16(S1Ram.PlcPatternsLeft, 0);
        }

        /// <summary>
        /// A ROM image carrying Nemesis headers at the two sources the
        /// cases arm, and a deliberate zero-pattern header at 0x000100.
        /// </summary>
        private static byte[] CreateRom()
        {
            var rom = new byte[0x40000];
            WriteHeader(rom, 0x022670, 0x2E);
            WriteHeader(rom, 0x0304F0, 0x11);
            WriteHeader(rom, 0x000100, 0);
            return rom;
        }

        private static void WriteHeader(byte[] rom, int offset, int patterns)
        {
            rom[offset] = (byte)(patterns >> 8);
            rom[offset + 1] = (byte)patterns;
        }

        private static string[] Lines(string content)
        {
            return content.Split(
                new[] { '\n' }, StringSplitOptions.RemoveEmptyEntries);
        }
    }
}
