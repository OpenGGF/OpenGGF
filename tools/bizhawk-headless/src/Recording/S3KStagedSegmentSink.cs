using System;
using System.IO;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Production sink for
    /// <see cref="S3KCompleteRunCaptureRunner"/>: stages each segment's
    /// three files into a
    /// <see cref="NoReplacePublisher.IncrementalStagingSession"/> as the
    /// capture streams them, so nothing lands under a final name until the
    /// whole run publishes and no segment is ever held in memory.
    ///
    /// physics.csv and aux_state.jsonl are STREAMED
    /// (<see cref="NoReplacePublisher.IncrementalStagingSession.OpenFile"/>)
    /// because a single complete-run segment reaches 266 MB of aux;
    /// metadata.json is a few hundred bytes and is staged as a string at
    /// finalize, which is also the only moment its bytes are known
    /// (trace_frame_count, recording date and Player_mode are finalize-time
    /// samples).
    ///
    /// Per-file staging order inside a segment is physics, aux, metadata —
    /// the same order as the plain trace mode's three-file publication —
    /// and the caller stages run_manifest.json after every segment, so
    /// link(2) publication can never produce a manifest without its
    /// segment files.
    ///
    /// Line endings: the content is written verbatim, and the S3K
    /// complete-run recorder publishes LF in BOTH plain and run mode
    /// (docs/s3k-run-publication.md §6). The S1/S2 run-mode CRLF expansion
    /// must never be applied here — it would corrupt the (C) gate, itself a
    /// run-mode capture with LF output.
    /// </summary>
    internal sealed class S3KStagedSegmentSink
        : IS3KCompleteRunSegmentSink, IDisposable
    {
        /// <summary>
        /// Mirrors CommandLineOptions.TraceOutputFileNames; kept as
        /// literals because that table lives in the CLI namespace.
        /// </summary>
        internal const string PhysicsFileName = "physics.csv";
        internal const string AuxStateFileName = "aux_state.jsonl";
        internal const string MetadataFileName = "metadata.json";

        private readonly NoReplacePublisher.IncrementalStagingSession session;

        private NoReplacePublisher.StagedStream physics;
        private NoReplacePublisher.StagedStream aux;
        private string dirToken;

        internal S3KStagedSegmentSink(
            NoReplacePublisher.IncrementalStagingSession session)
        {
            if (session == null)
            {
                throw new ArgumentNullException("session");
            }
            this.session = session;
        }

        public S3KSegmentStreams BeginSegment(S3KSegmentArm arm)
        {
            if (arm == null)
            {
                throw new ArgumentNullException("arm");
            }
            if (physics != null || aux != null)
            {
                throw new InvalidOperationException(
                    "Segment " + dirToken + " is still open.");
            }
            dirToken = arm.DirToken;
            physics = session.OpenFile(dirToken + "/" + PhysicsFileName);
            try
            {
                aux = session.OpenFile(dirToken + "/" + AuxStateFileName);
            }
            catch
            {
                physics.Dispose();
                physics = null;
                throw;
            }
            return new S3KSegmentStreams(physics.Writer, aux.Writer);
        }

        public void EndSegment(RunManifestSegment entry, string metadataJson)
        {
            if (entry == null)
            {
                throw new ArgumentNullException("entry");
            }
            if (metadataJson == null)
            {
                throw new ArgumentNullException("metadataJson");
            }
            if (physics == null || aux == null)
            {
                throw new InvalidOperationException(
                    "No segment is open for " + entry.Dir + ".");
            }
            physics.Complete();
            physics = null;
            aux.Complete();
            aux = null;
            session.StageFile(
                entry.Dir + "/" + MetadataFileName,
                metadataJson);
            dirToken = null;
        }

        /// <summary>
        /// Discards a half-written segment (a capture that threw
        /// mid-segment). The session itself owns everything already
        /// completed and revokes it on its own Dispose.
        /// </summary>
        public void Dispose()
        {
            if (physics != null)
            {
                physics.Dispose();
                physics = null;
            }
            if (aux != null)
            {
                aux.Dispose();
                aux = null;
            }
        }
    }
}
