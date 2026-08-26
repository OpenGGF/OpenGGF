using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Records the Sonic 1 Nemesis PLC queue's ARMING edge — the moment
    /// RunPLC accepts the queue head for decompression — as a per-segment
    /// hardware-timing readiness stream.
    ///
    /// RunPLC (docs/s1disasm/sonic.asm:1379-1420, ROM 0x0015E4 in Sonic 1
    /// World REV01) is the main-loop tail routine that arms the head of
    /// v_plc_buffer. It arms iff, on entry, v_plc_buffer (0xF680, u32) is
    /// non-zero AND v_plc_patternsleft (0xF6F8, u16) is zero.
    ///
    /// It is observed at ENTRY rather than sampled per frame because the
    /// routine DESTROYS the head identity: `move.l a0,(v_plc_buffer).w`
    /// (sonic.asm:1405) writes back a pointer already advanced past the
    /// Nemesis header, so no later RAM sample can recover the descriptor
    /// that was armed. The arm predicate itself is likewise only true at
    /// entry — the assembly is built with FixBugs = 0 (sonic.asm:20), so
    /// v_plc_patternsleft is written BEFORE NemDec_BuildCodeTable
    /// (sonic.asm:1396-1399) and is already non-zero by the time the
    /// routine returns. This models that un-fixed path; the FixBugs = 1
    /// path would defer that write to the end of the routine, which does
    /// not change the entry-observed predicate.
    ///
    /// This is an OBSERVER: it reads RAM and the supplied ROM image, and
    /// mutates nothing. It carries no gameplay value, selects no sync
    /// point, and enables no other hook family.
    /// </summary>
    public sealed class S1PlcHardwareTimingObserver : IDisposable
    {
        /// <summary>
        /// RunPLC entry, Sonic 1 World REV01 (SHA-1
        /// 69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B). The byte pattern
        /// `4A B8 F6 80 67 xx 4A 78 F6 F8 66` — tst.l (v_plc_buffer).w /
        /// beq.s / tst.w (v_plc_patternsleft).w / bne.s — occurs exactly
        /// once in that ROM, here.
        /// </summary>
        public const uint RunPlcEntryPc = 0x0015E4;

        private const string KindName = "NEMESIS_PLC_QUEUE";
        private const string EventKind = "nemesis_plc_queue";
        private const string CompressionVariant = "nemesis";
        private const string Boundary = "pre_main_loop";

        private readonly byte[] rom;
        private readonly IGpgxHost host;
        // The registered delegate is held so the Mono runtime cannot
        // collect it while the native core still holds the callback.
        private readonly Action callback;
        private readonly IDisposable registration;
        private readonly List<string> pending = new List<string>();

        private TextWriter writer;
        private long nextOrdinal;
        private bool disposed;

        public S1PlcHardwareTimingObserver(byte[] rom, IGpgxHost host)
        {
            if (rom == null)
            {
                throw new ArgumentNullException("rom");
            }
            if (host == null)
            {
                throw new ArgumentNullException("host");
            }
            this.rom = rom;
            this.host = host;
            callback = OnRunPlcEntry;
            registration = host.RegisterExecuteCallback(
                RunPlcEntryPc, callback);
        }

        /// <summary>
        /// Binds the observer to one segment's stream. Anything observed
        /// before the arm belongs to no row and is discarded, so the level
        /// load's own PLC arming never reaches a trace file.
        /// </summary>
        public void ArmSegment(TextWriter segmentWriter)
        {
            if (segmentWriter == null)
            {
                throw new ArgumentNullException("segmentWriter");
            }
            ThrowIfDisposed();
            if (writer != null)
            {
                throw new InvalidOperationException(
                    "An S1 PLC hardware-timing segment is already armed.");
            }
            pending.Clear();
            writer = segmentWriter;
        }

        /// <summary>
        /// Drops whatever the frame about to be advanced inherits. Called
        /// immediately before every emulator advance, so an edge can only
        /// ever be attributed to the advance that produced it.
        /// </summary>
        public void BeginFrame()
        {
            ThrowIfDisposed();
            pending.Clear();
        }

        /// <summary>
        /// Commits the edges observed during the advance that produced
        /// trace row <paramref name="rawFrame"/>, in call order. Ordinals
        /// are allocated HERE rather than at observation, so the sequence
        /// a capture publishes is gapless even though the frames that
        /// belong to no row are dropped.
        /// </summary>
        public void CommitRow(int rawFrame)
        {
            ThrowIfDisposed();
            if (rawFrame < 0)
            {
                throw new ArgumentOutOfRangeException("rawFrame");
            }
            if (writer == null)
            {
                throw new InvalidOperationException(
                    "No S1 PLC hardware-timing segment is armed.");
            }
            for (int index = 0; index < pending.Count; index++)
            {
                Write(rawFrame, nextOrdinal, pending[index]);
                nextOrdinal++;
            }
            pending.Clear();
        }

        /// <summary>
        /// Releases the segment's stream. The per-capture ordinal counter
        /// deliberately survives: a run capture's segments are one
        /// recording, and repeating an identity across two of its files
        /// would describe two different pieces of work as the same one.
        /// </summary>
        public void EndSegment()
        {
            ThrowIfDisposed();
            pending.Clear();
            writer = null;
        }

        public void Dispose()
        {
            if (disposed)
            {
                return;
            }
            disposed = true;
            pending.Clear();
            writer = null;
            registration.Dispose();
        }

        private void OnRunPlcEntry()
        {
            if (disposed || writer == null)
            {
                return;
            }
            uint head = S1Ram.U32(host, S1Ram.PlcBuffer);
            int patternsLeft = S1Ram.U16(host, S1Ram.PlcPatternsLeft);
            if (head == 0 || patternsLeft != 0)
            {
                return;     // RunPLC returns without arming anything.
            }

            uint source = head & 0x00FFFFFFU;
            int destinationTile =
                S1Ram.U16(host, S1Ram.PlcBuffer + 4) / 32;
            pending.Add(HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                KindName,
                checked((int)source),
                0,
                destinationTile,
                NemesisPatternCount(source),
                CompressionVariant,
                0));
        }

        /// <summary>
        /// The armed descriptor's total pattern count, from the Nemesis
        /// stream header the ROM itself reads at sonic.asm:1388 (`move.w
        /// (a0)+,d2`) and masks at :1394 (`andi.w #$7FFF,d2`). A zero
        /// count or an out-of-ROM source is a broken observation, not a
        /// record to publish.
        /// </summary>
        private int NemesisPatternCount(uint source)
        {
            int offset = checked((int)source);
            if (offset < 0 || offset > rom.Length - 2)
            {
                throw new InvalidDataException(
                    "RunPLC armed a Nemesis source outside the supplied"
                    + " ROM: 0x" + offset.ToString("X"));
            }
            int count = ((rom[offset] << 8) | rom[offset + 1]) & 0x7FFF;
            if (count == 0)
            {
                throw new InvalidDataException(
                    "RunPLC armed a Nemesis stream declaring zero patterns"
                    + " at 0x" + offset.ToString("X"));
            }
            return count;
        }

        /// <summary>
        /// One record, in the v5 per-segment field order the Java
        /// HardwareTimingStreamLoader enforces, LF-terminated.
        /// </summary>
        private void Write(int rawFrame, long ordinal, string fingerprint)
        {
            writer.Write("{\"event\":\"hardware_work_completed\",");
            writer.Write("\"raw_frame\":");
            writer.Write(rawFrame.ToString(CultureInfo.InvariantCulture));
            writer.Write(",\"boundary\":\"");
            writer.Write(Boundary);
            writer.Write("\",\"kind\":\"");
            writer.Write(EventKind);
            writer.Write("\",\"ordinal\":");
            writer.Write(ordinal.ToString(CultureInfo.InvariantCulture));
            writer.Write(",\"submission_fingerprint\":\"");
            writer.Write(fingerprint);
            writer.Write("\"}\n");
        }

        private void ThrowIfDisposed()
        {
            if (disposed)
            {
                throw new ObjectDisposedException(
                    "S1PlcHardwareTimingObserver");
            }
        }
    }
}
