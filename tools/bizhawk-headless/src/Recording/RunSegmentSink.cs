using System;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// The two output streams of one armed S1/S2 run segment, handed to the
    /// runner by its sink at every arm.
    ///
    /// A special-stage segment gets an <see cref="AuxStateJsonl"/> writer
    /// too even when nothing is ever written to it: the S1 Lua opens the
    /// file on the SS path and never writes a line, so every S1 SS fixture
    /// ships a 0-byte aux_state.jsonl and the port must PUBLISH the empty
    /// file rather than omit it. (S2's SS aux stream carries the v9.13-s2
    /// §11.3 events and is genuinely non-empty.)
    /// </summary>
    public sealed class RunSegmentStreams
    {
        public RunSegmentStreams(
            TextWriter physicsCsv, TextWriter auxStateJsonl)
        {
            if (physicsCsv == null)
            {
                throw new ArgumentNullException("physicsCsv");
            }
            if (auxStateJsonl == null)
            {
                throw new ArgumentNullException("auxStateJsonl");
            }
            PhysicsCsv = physicsCsv;
            AuxStateJsonl = auxStateJsonl;
        }

        public TextWriter PhysicsCsv { get; private set; }
        public TextWriter AuxStateJsonl { get; private set; }
    }

    /// <summary>
    /// Where an S1/S2 run capture's segments go — the S1/S2 counterpart of
    /// <see cref="IS3KCompleteRunSegmentSink"/>. The runner never buffers a
    /// segment: it asks the sink for writers at the arm, streams rows into
    /// them frame by frame, and hands back the finished manifest entry plus
    /// the final metadata.json bytes at the finalize.
    ///
    /// Neither run machine can throw an armed segment away — every arm
    /// reaches exactly one finalize (S1: the level/ss finalizes plus the
    /// run-end funnel; S2: the same three plus the $8C reload boundary) —
    /// so there is nothing for a buffer to protect and the streams are
    /// written straight through. That is what keeps peak memory at the OS
    /// write buffers rather than at the whole run: the S2 complete-emeralds
    /// pass alone produced ~375 MB of output, held as UTF-16
    /// StringBuilders before this interface existed.
    /// </summary>
    public interface IRunSegmentSink
    {
        /// <summary>
        /// Called once per arm, BEFORE the segment's CSV header is written.
        /// <paramref name="dirToken"/> is the per-segment output
        /// subdirectory name the runner has just allocated (ghz1, ss,
        /// seg1_ehz1, ss_2, ...).
        /// </summary>
        RunSegmentStreams BeginSegment(string dirToken);

        /// <summary>
        /// Called once per finalize, after the last row. metadata.json is
        /// materialised exactly once, here — it is also the only moment its
        /// bytes are known, since trace_frame_count and the finalize-time
        /// RAM samples are not available at the arm.
        /// </summary>
        void EndSegment(RunManifestSegment entry, string metadataJson);
    }
}
