using System;
using System.IO;
using System.Text;

// The line-ending policy and its rationale live with the CLI that selects
// it; the alias avoids "BizHawk" resolving to OpenGGF.BizHawk below.
using CliProgram = BizHawk.Headless.Gpgx.Program;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Production sink for <see cref="S1RunCaptureRunner"/> and
    /// <see cref="S2RunCaptureRunner"/>: stages each segment's three files
    /// into a
    /// <see cref="NoReplacePublisher.IncrementalStagingSession"/> as the
    /// capture streams them, so nothing lands under a final name until the
    /// whole run publishes and no segment is ever held in memory.
    ///
    /// physics.csv and aux_state.jsonl are STREAMED
    /// (<see cref="NoReplacePublisher.IncrementalStagingSession.OpenFile"/>);
    /// metadata.json is a few hundred bytes and is staged as a string at
    /// finalize, which is also the only moment its bytes are known
    /// (trace_frame_count and the finalize-time RAM samples).
    ///
    /// Per-file staging order inside a segment is physics, aux, metadata —
    /// the same order as the plain trace mode's three-file publication —
    /// and the caller stages run_manifest.json after the whole capture, so
    /// link(2) publication can never produce a manifest without its segment
    /// files.
    ///
    /// Line endings: <paramref name="expandNewlines"/> selects the
    /// per-fixture-set policy Program.ExpandRunNewlines documents — the
    /// canonical S1/S2 run-mode captures ran under Windows EmuHawk, whose
    /// text-mode io.open expanded every logical "\n" to "\r\n". Applying it
    /// through a writer decorator rather than a whole-string Replace is
    /// what makes the streaming form byte-identical to the buffered one:
    /// every '\n' the runner writes becomes "\r\n" and nothing else
    /// changes, exactly as <c>content.Replace("\n", "\r\n")</c> did. The S1
    /// complete-run profile (no --run-id) stays LF and passes false.
    /// </summary>
    internal sealed class StagedRunSegmentSink : IRunSegmentSink, IDisposable
    {
        /// <summary>
        /// Mirrors CommandLineOptions.TraceOutputFileNames; kept as
        /// literals because that table lives in the CLI namespace.
        /// </summary>
        internal const string PhysicsFileName = "physics.csv";
        internal const string AuxStateFileName = "aux_state.jsonl";
        internal const string MetadataFileName = "metadata.json";

        private readonly NoReplacePublisher.IncrementalStagingSession session;
        private readonly bool expandNewlines;

        private NoReplacePublisher.StagedStream physics;
        private NoReplacePublisher.StagedStream aux;
        private TextWriter physicsWriter;
        private TextWriter auxWriter;
        private string dirToken;

        internal StagedRunSegmentSink(
            NoReplacePublisher.IncrementalStagingSession session,
            bool expandNewlines)
        {
            if (session == null)
            {
                throw new ArgumentNullException("session");
            }
            this.session = session;
            this.expandNewlines = expandNewlines;
        }

        public RunSegmentStreams BeginSegment(string dirToken)
        {
            if (string.IsNullOrEmpty(dirToken))
            {
                throw new ArgumentException(
                    "A segment directory token is required.",
                    "dirToken");
            }
            if (physics != null || aux != null)
            {
                throw new InvalidOperationException(
                    "Segment " + this.dirToken + " is still open.");
            }
            this.dirToken = dirToken;
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
            physicsWriter = Decorate(physics.Writer);
            auxWriter = Decorate(aux.Writer);
            return new RunSegmentStreams(physicsWriter, auxWriter);
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
            physicsWriter = null;
            physics.Complete();
            physics = null;
            auxWriter = null;
            aux.Complete();
            aux = null;
            session.StageFile(
                entry.Dir + "/" + MetadataFileName,
                expandNewlines
                    ? CliProgram.ExpandRunNewlines(metadataJson)
                    : metadataJson);
            dirToken = null;
        }

        /// <summary>
        /// Discards a half-written segment (a capture that threw
        /// mid-segment). The session itself owns everything already
        /// completed and revokes it on its own Dispose.
        /// </summary>
        public void Dispose()
        {
            physicsWriter = null;
            auxWriter = null;
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

        private TextWriter Decorate(TextWriter writer)
        {
            return expandNewlines
                ? new NewlineExpandingTextWriter(writer)
                : writer;
        }

        /// <summary>
        /// Writes through to <paramref name="inner"/> with every '\n'
        /// expanded to "\r\n" — the streaming equivalent of
        /// <c>content.Replace("\n", "\r\n")</c>, applied character by
        /// character so the result cannot depend on where the runner
        /// happens to split its writes.
        ///
        /// It deliberately does NOT own the inner writer: the staged stream
        /// owns it and is the thing that flushes and closes the file.
        /// </summary>
        private sealed class NewlineExpandingTextWriter : TextWriter
        {
            private readonly TextWriter inner;

            internal NewlineExpandingTextWriter(TextWriter inner)
            {
                this.inner = inner;
            }

            public override Encoding Encoding
            {
                get { return inner.Encoding; }
            }

            public override void Write(char value)
            {
                if (value == '\n')
                {
                    inner.Write('\r');
                }
                inner.Write(value);
            }

            public override void Write(string value)
            {
                if (value == null)
                {
                    return;
                }
                int newline = value.IndexOf('\n');
                if (newline < 0)
                {
                    // The common case by far: every trace row and aux line
                    // the runners emit is newline-free, the terminator
                    // arriving as a separate Write(char).
                    inner.Write(value);
                    return;
                }
                var start = 0;
                while (newline >= 0)
                {
                    inner.Write(value.Substring(start, newline - start));
                    inner.Write("\r\n");
                    start = newline + 1;
                    newline = value.IndexOf('\n', start);
                }
                if (start < value.Length)
                {
                    inner.Write(value.Substring(start));
                }
            }

            public override void Write(
                char[] buffer, int index, int count)
            {
                if (buffer == null)
                {
                    return;
                }
                int end = index + count;
                int start = index;
                for (int scan = index; scan < end; scan++)
                {
                    if (buffer[scan] != '\n')
                    {
                        continue;
                    }
                    if (scan > start)
                    {
                        inner.Write(buffer, start, scan - start);
                    }
                    inner.Write("\r\n");
                    start = scan + 1;
                }
                if (end > start)
                {
                    inner.Write(buffer, start, end - start);
                }
            }

            public override void Flush()
            {
                inner.Flush();
            }
        }
    }
}
