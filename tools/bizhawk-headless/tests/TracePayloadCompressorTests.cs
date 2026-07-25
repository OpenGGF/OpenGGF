using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using BizHawk.Headless.Gpgx;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Covers publish-time payload compression: the filter and threshold
    /// ported from tools/traces/compress-traces.ps1, the verify-before-
    /// destroy ordering that makes the port safe, output determinism, and
    /// the CLI wiring (compression on by default, --no-compress to opt out).
    ///
    /// The verification-failure cases inject a compressor that produces the
    /// wrong bytes rather than trusting the happy path — the whole value of
    /// the round-trip check is what it does when it fails.
    /// </summary>
    internal static class TracePayloadCompressorTests
    {
        private static readonly string[] TraceFileNames =
        {
            "physics.csv",
            "aux_state.jsonl",
            "metadata.json"
        };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor targets only the payload names",
                TargetsOnlyThePayloadNames));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor honors the size threshold in both"
                + " directions",
                HonorsTheSizeThresholdInBothDirections));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor round-trips published payloads"
                + " byte-for-byte",
                RoundTripsPublishedPayloadsByteForByte));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor verification failure keeps the"
                + " original and leaves no gz",
                VerificationFailureKeepsOriginalAndLeavesNoGz));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor verification failure publishes"
                + " nothing at all",
                VerificationFailurePublishesNothing));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor writes deterministic gzip bytes",
                WritesDeterministicGzipBytes));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor compresses streamed session files",
                CompressesStreamedSessionFiles));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor streams a payload straight to gzip"
                + " with bulk-identical bytes",
                StreamsStraightToGzipWithBulkIdenticalBytes));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor expands a below-threshold streamed"
                + " payload back to its plain name",
                ExpandsBelowThresholdStreamedPayload));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor streamed verification failure"
                + " publishes nothing at all",
                StreamedVerificationFailurePublishesNothing));
            tests.Add(new TestMain.TestCase(
                "TracePayloadCompressor reports every payload it considered",
                ReportsEveryPayloadItConsidered));
            tests.Add(new TestMain.TestCase(
                "TraceCli compresses by default and opts out with"
                + " --no-compress",
                CompressesByDefaultAndOptsOut));
            tests.Add(new TestMain.TestCase(
                "TraceCli parses the compression threshold",
                ParsesTheCompressionThreshold));
            tests.Add(new TestMain.TestCase(
                "TraceCli rejects incompatible compression arguments",
                RejectsIncompatibleCompressionArguments));
            tests.Add(new TestMain.TestCase(
                "TraceCli refuses an existing compressed final unless"
                + " compression is off",
                RefusesExistingCompressedFinalUnlessCompressionIsOff));
        }

        /// <summary>
        /// The compress-traces.ps1 filter: aux_state*.jsonl and
        /// physics*.csv, nothing else. metadata.json and run_manifest.json
        /// are stored uncompressed in the fixtures and must stay that way,
        /// and an already-compressed name must never be compressed twice.
        /// </summary>
        private static void TargetsOnlyThePayloadNames()
        {
            foreach (string name in new[]
            {
                "physics.csv",
                "physics_special_stage.csv",
                "aux_state.jsonl",
                "aux_state_2.jsonl"
            })
            {
                AssertEx.Equal(
                    true,
                    TracePayloadCompressor.IsTracePayloadName(name));
            }
            foreach (string name in new[]
            {
                "metadata.json",
                "run_manifest.json",
                "smoke.csv",
                "physics.csv.gz",
                "aux_state.jsonl.gz",
                "aux_state.json",
                "csv",
                "physics"
            })
            {
                AssertEx.Equal(
                    false,
                    TracePayloadCompressor.IsTracePayloadName(name));
            }
        }

        /// <summary>
        /// compress-traces.ps1 skips files strictly below the threshold, so
        /// a payload exactly at it is compressed and one byte under it is
        /// not. Both sides are asserted in one publication so the boundary
        /// cannot drift in either direction unnoticed.
        /// </summary>
        private static void HonorsTheSizeThresholdInBothDirections()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "threshold");
                    string atThreshold = new string('c', 1024);
                    string belowThreshold = new string('j', 1023);
                    Publish(
                        output,
                        new TracePayloadCompressor(1024),
                        new[] { atThreshold, belowThreshold, "metadata\n" });

                    AssertEx.Equal(
                        "aux_state.jsonl,metadata.json,physics.csv.gz",
                        JoinFileNames(output));
                    AssertEx.Equal(
                        belowThreshold,
                        File.ReadAllText(
                            Path.Combine(output, "aux_state.jsonl")));
                    AssertBytesEqual(
                        Encoding.UTF8.GetBytes(atThreshold),
                        Decompress(
                            Path.Combine(output, "physics.csv.gz")));
                });
        }

        /// <summary>
        /// The published gzip decompresses to exactly the bytes the capture
        /// wrote — including CRLF (run mode's text-mode expansion) and
        /// non-ASCII UTF-8, neither of which the compression path may touch.
        /// </summary>
        private static void RoundTripsPublishedPayloadsByteForByte()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "round-trip");
                    string physics = "frame,input\r\n0000,0000\r\n"
                        + new string('x', 4096);
                    string aux = "{\"zone\":\"ghz\",\"note\":\"é✓\"}\n"
                        + new string('y', 4096);
                    Publish(
                        output,
                        new TracePayloadCompressor(64),
                        new[] { physics, aux, "metadata\n" });

                    AssertEx.Equal(
                        "aux_state.jsonl.gz,metadata.json,physics.csv.gz",
                        JoinFileNames(output));
                    AssertBytesEqual(
                        Encoding.UTF8.GetBytes(physics),
                        Decompress(Path.Combine(output, "physics.csv.gz")));
                    AssertBytesEqual(
                        Encoding.UTF8.GetBytes(aux),
                        Decompress(
                            Path.Combine(output, "aux_state.jsonl.gz")));
                    // A gzip container, and the uncompressed name is gone.
                    byte[] container = File.ReadAllBytes(
                        Path.Combine(output, "physics.csv.gz"));
                    AssertEx.Equal(0x1F, container[0]);
                    AssertEx.Equal(0x8B, container[1]);
                });
        }

        /// <summary>
        /// The property the port exists to preserve: the source is destroyed
        /// only AFTER the gzip has been decompressed and compared by SHA-256
        /// and length. Both mismatch branches are injected — a shorter
        /// payload (length) and an equal-length different one (hash) — and
        /// in each case the source survives untouched with no .gz left
        /// behind.
        /// </summary>
        private static void VerificationFailureKeepsOriginalAndLeavesNoGz()
        {
            byte[] original = Encoding.UTF8.GetBytes(
                "frame,input\n0000,0000\n" + new string('z', 2048));
            var injections = new[]
            {
                new object[]
                {
                    (Action<Stream, Stream>)WrongLengthGzip,
                    "decompressed 15 bytes"
                },
                new object[]
                {
                    (Action<Stream, Stream>)(
                        (source, destination) =>
                            EqualLengthGzip(source, destination, original)),
                    "sha256"
                }
            };
            foreach (object[] injection in injections)
            {
                WithTemporaryDirectory(
                    root =>
                    {
                        string source = Path.Combine(root, "physics.csv");
                        string destination = source + ".gz";
                        File.WriteAllBytes(source, original);
                        var compressor = new TracePayloadCompressor(
                            0,
                            (Action<Stream, Stream>)injection[0]);

                        AssertEx.Throws<IOException>(
                            () => compressor.CompressAndVerify(
                                source, destination),
                            "gzip verification failed");
                        AssertEx.Throws<IOException>(
                            () => compressor.CompressAndVerify(
                                source, destination),
                            (string)injection[1]);

                        AssertBytesEqual(
                            original, File.ReadAllBytes(source));
                        AssertEx.Equal(false, File.Exists(destination));
                        AssertEx.Equal(
                            "physics.csv",
                            JoinFileNames(root));
                    });
            }
        }

        /// <summary>
        /// Compression runs inside the publication, before the first
        /// link(2), so a verification failure is indistinguishable from any
        /// other staging failure: no final exists, not even for the payload
        /// that compressed successfully, and no temporary survives.
        /// </summary>
        private static void VerificationFailurePublishesNothing()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "failed-compression");
                    var publisher = new NoReplacePublisher(
                        new TracePayloadCompressor(0, WrongLengthGzip));
                    NoReplacePublisher.StagedPublicationSet staged =
                        StageTraceSet(
                            publisher,
                            output,
                            new[] { "physics\n", "aux\n", "metadata\n" });

                    AssertEx.Throws<IOException>(
                        () => staged.Publish(),
                        "gzip verification failed");

                    AssertEx.Equal(true, Directory.Exists(output));
                    AssertEx.Equal(
                        0,
                        Directory.GetFileSystemEntries(output).Length);
                });
        }

        /// <summary>
        /// Compressing the same bytes twice yields the same bytes, so a
        /// recompressed capture never shows up as a noise diff in a fixture
        /// commit. The header's MTIME field is asserted zero (the GNU
        /// `gzip -n` property) rather than assumed: this fails loudly if a
        /// runtime ever starts stamping a timestamp there.
        /// </summary>
        private static void WritesDeterministicGzipBytes()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string source = Path.Combine(root, "physics.csv");
                    File.WriteAllText(
                        source,
                        "frame,input\n" + new string('q', 8192));
                    var compressor = new TracePayloadCompressor(0);
                    string first = Path.Combine(root, "first.gz");
                    string second = Path.Combine(root, "second.gz");
                    compressor.CompressAndVerify(source, first);
                    compressor.CompressAndVerify(source, second);

                    byte[] firstBytes = File.ReadAllBytes(first);
                    AssertBytesEqual(
                        firstBytes, File.ReadAllBytes(second));
                    // Bytes 4-7 are the gzip MTIME field.
                    AssertEx.Equal(
                        "00-00-00-00",
                        BitConverter.ToString(firstBytes, 4, 4));
                });
        }

        /// <summary>
        /// The complete-run layout stages its two payloads as STREAMS (a
        /// single S3K segment reaches hundreds of megabytes and is never
        /// held in memory), so the streamed path must compress too — it is
        /// the path that benefits most.
        /// </summary>
        private static void CompressesStreamedSessionFiles()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "session");
                    string physics = "streamed physics\n"
                        + new string('s', 4096);
                    var publisher = new NoReplacePublisher(
                        new TracePayloadCompressor(64));
                    NoReplacePublisher.IncrementalStagingSession session =
                        publisher.OpenSession(output);
                    using (NoReplacePublisher.StagedStream stream =
                        session.OpenFile("aiz1/physics.csv"))
                    {
                        stream.Writer.Write(physics);
                        stream.Complete();
                    }
                    session.StageFile("aiz1/metadata.json", "metadata\n");
                    session.StageFile("run_manifest.json", "manifest\n");
                    NoReplacePublisher.StagedPublicationSet staged =
                        session.Complete();
                    staged.Publish();

                    AssertEx.Equal(
                        "aiz1/metadata.json,aiz1/physics.csv.gz,"
                        + "run_manifest.json",
                        JoinRelativeFileNames(output));
                    AssertBytesEqual(
                        Encoding.UTF8.GetBytes(physics),
                        Decompress(Path.Combine(
                            output, "aiz1", "physics.csv.gz")));
                });
        }

        /// <summary>
        /// A streamed payload never materialises uncompressed: only the
        /// .gz temporary is ever created, and the bytes it holds are
        /// IDENTICAL to what the bulk compress-then-verify path produces
        /// from the same content. That equality is the determinism claim
        /// under streaming — the container cannot depend on how the caller
        /// chunked its writes (this case writes one short line at a time,
        /// the way a trace runner does) — and it is also why a fixture
        /// captured either way shows no diff.
        /// </summary>
        private static void StreamsStraightToGzipWithBulkIdenticalBytes()
        {
            WithTemporaryDirectory(
                root =>
                {
                    var content = new StringBuilder();
                    for (var row = 0; row < 4096; row++)
                    {
                        content.Append("frame,").Append(row)
                            .Append(",0x0000,idle\n");
                    }
                    string payload = content.ToString();

                    string output = Path.Combine(root, "streamed");
                    var publisher = new NoReplacePublisher(
                        new TracePayloadCompressor(64));
                    NoReplacePublisher.IncrementalStagingSession session =
                        publisher.OpenSession(output);
                    string[] duringCapture;
                    using (NoReplacePublisher.StagedStream stream =
                        session.OpenFile("seg1_ehz1/physics.csv"))
                    {
                        foreach (string line in payload.Split('\n'))
                        {
                            if (line.Length == 0)
                            {
                                continue;
                            }
                            stream.Writer.Write(line);
                            stream.Writer.Write('\n');
                        }
                        stream.Complete();
                        // Nothing uncompressed was ever staged: the only
                        // temporary is the gzip itself.
                        duringCapture = RelativeFileNames(output);
                    }
                    NoReplacePublisher.StagedPublicationSet staged =
                        session.Complete();
                    staged.Publish();

                    AssertEx.Equal(1, duringCapture.Length);
                    AssertEx.Equal(
                        true,
                        duringCapture[0].EndsWith(
                            ".gz", StringComparison.Ordinal));
                    AssertEx.Equal(
                        "seg1_ehz1/physics.csv.gz",
                        JoinRelativeFileNames(output));

                    string bulkSource = Path.Combine(root, "physics.csv");
                    string bulkGzip = bulkSource + ".gz";
                    File.WriteAllBytes(
                        bulkSource, Encoding.UTF8.GetBytes(payload));
                    new TracePayloadCompressor(64)
                        .CompressAndVerify(bulkSource, bulkGzip);

                    AssertBytesEqual(
                        File.ReadAllBytes(bulkGzip),
                        File.ReadAllBytes(Path.Combine(
                            output, "seg1_ehz1", "physics.csv.gz")));
                    AssertBytesEqual(
                        Encoding.UTF8.GetBytes(payload),
                        Decompress(Path.Combine(
                            output, "seg1_ehz1", "physics.csv.gz")));
                });
        }

        /// <summary>
        /// The threshold rule is unchanged by streaming: a payload that
        /// turns out to be below it publishes under its PLAIN name, and the
        /// verifying decompression that establishes the bytes are intact is
        /// the same pass that writes them. The gzip temporary does not
        /// survive, and nothing is reported as compressed.
        /// </summary>
        private static void ExpandsBelowThresholdStreamedPayload()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "small");
                    string payload = "frame,input\n0,idle\n";
                    var compressor = new TracePayloadCompressor(1024);
                    var publisher = new NoReplacePublisher(compressor);
                    NoReplacePublisher.IncrementalStagingSession session =
                        publisher.OpenSession(output);
                    using (NoReplacePublisher.StagedStream stream =
                        session.OpenFile("ss/physics.csv"))
                    {
                        stream.Writer.Write(payload);
                        stream.Complete();
                    }
                    NoReplacePublisher.StagedPublicationSet staged =
                        session.Complete();
                    staged.Publish();

                    AssertEx.Equal(
                        "ss/physics.csv", JoinRelativeFileNames(output));
                    AssertBytesEqual(
                        Encoding.UTF8.GetBytes(payload),
                        File.ReadAllBytes(Path.Combine(
                            output, "ss", "physics.csv")));
                    AssertEx.Equal(0, compressor.Report.Length);
                });
        }

        /// <summary>
        /// Verify-before-destroy holds for the streamed form too, and it
        /// fails EARLIER: the round-trip check runs at the stream's
        /// Complete(), so a corrupted payload aborts the capture before any
        /// staging session finishes and no final is ever linked. The
        /// injected compressor drops a byte, so the decompressed length no
        /// longer matches the digest taken on the way in.
        /// </summary>
        private static void StreamedVerificationFailurePublishesNothing()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "corrupted");
                    var publisher = new NoReplacePublisher(
                        new TracePayloadCompressor(
                            0, BulkGzip, DroppingGzip));
                    NoReplacePublisher.IncrementalStagingSession session =
                        publisher.OpenSession(output);
                    using (session)
                    using (NoReplacePublisher.StagedStream stream =
                        session.OpenFile("seg1_ehz1/physics.csv"))
                    {
                        stream.Writer.Write("frame,input\n0,idle\n");
                        AssertEx.Throws<IOException>(
                            () => stream.Complete(),
                            "gzip verification failed");
                    }

                    AssertEx.Equal(true, Directory.Exists(output));
                    AssertEx.Equal(
                        string.Empty, JoinRelativeFileNames(output));
                });
        }

        private static void BulkGzip(Stream source, Stream destination)
        {
            using (var gzip = new GZipStream(
                destination, CompressionLevel.Optimal, true))
            {
                source.CopyTo(gzip);
            }
        }

        private static Stream DroppingGzip(Stream destination)
        {
            return new DroppingStream(new GZipStream(
                destination, CompressionLevel.Optimal, true));
        }

        /// <summary>
        /// Silently drops the first byte written through it, so the gzip
        /// that lands on disk decompresses to something one byte shorter
        /// than the plaintext the digest saw.
        /// </summary>
        private sealed class DroppingStream : Stream
        {
            private readonly Stream inner;
            private bool dropped;

            internal DroppingStream(Stream inner)
            {
                this.inner = inner;
            }

            public override bool CanRead
            {
                get { return false; }
            }

            public override bool CanSeek
            {
                get { return false; }
            }

            public override bool CanWrite
            {
                get { return true; }
            }

            public override long Length
            {
                get { throw new NotSupportedException(); }
            }

            public override long Position
            {
                get { throw new NotSupportedException(); }
                set { throw new NotSupportedException(); }
            }

            public override void Write(
                byte[] buffer, int offset, int count)
            {
                if (!dropped && count > 0)
                {
                    dropped = true;
                    offset++;
                    count--;
                }
                if (count > 0)
                {
                    inner.Write(buffer, offset, count);
                }
            }

            public override void Flush()
            {
            }

            public override int Read(byte[] buffer, int offset, int count)
            {
                throw new NotSupportedException();
            }

            public override long Seek(long offset, SeekOrigin origin)
            {
                throw new NotSupportedException();
            }

            public override void SetLength(long value)
            {
                throw new NotSupportedException();
            }

            protected override void Dispose(bool disposing)
            {
                if (disposing)
                {
                    inner.Dispose();
                }
                base.Dispose(disposing);
            }
        }

        /// <summary>
        /// Every payload the publication considered gets one report line
        /// naming its final path, so the operator can see what landed
        /// compressed and what fell below the threshold. Non-payload files
        /// are not reported at all.
        /// </summary>
        private static void ReportsEveryPayloadItConsidered()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "report");
                    var compressor = new TracePayloadCompressor(1024);
                    Publish(
                        output,
                        compressor,
                        new[] { new string('c', 2048), "aux\n", "meta\n" });

                    // Only the payload that was actually compressed is
                    // reported: with compression on by default, a
                    // below-threshold payload is the ordinary case and
                    // must not add noise to every capture's stdout.
                    string[] report = compressor.Report;
                    AssertEx.Equal(1, report.Length);
                    AssertContains(
                        report[0],
                        "Compressed " + Path.Combine(output, "physics.csv")
                        + " -> "
                        + Path.Combine(output, "physics.csv")
                        + ".gz (2048 ->");
                });
        }

        /// <summary>
        /// The default is ON: a bare trace invocation compresses, because
        /// the payload that must never reach a commit uncompressed is
        /// produced by exactly that invocation. --no-compress is the opt-out
        /// for consumers that read a capture raw and never commit it, and
        /// --compress states the default explicitly.
        /// </summary>
        private static void CompressesByDefaultAndOptsOut()
        {
            WithTemporaryDirectory(
                root =>
                {
                    CommandLineOptions defaulted = ParseTrace(root);
                    AssertEx.Equal(true, defaulted.Compress);
                    AssertEx.Equal(
                        1048576L,
                        defaulted.CompressThresholdBytes);
                    AssertEx.Equal(
                        1048576L,
                        defaulted.CreateCompressor().ThresholdBytes);

                    AssertEx.Equal(
                        true, ParseTrace(root, "--compress").Compress);

                    CommandLineOptions off =
                        ParseTrace(root, "--no-compress");
                    AssertEx.Equal(false, off.Compress);
                    AssertEx.Equal(null, off.CreateCompressor());
                });
        }

        private static void ParsesTheCompressionThreshold()
        {
            WithTemporaryDirectory(
                root =>
                {
                    // The valueless switches must not disturb the strict
                    // name/value pairing of the arguments around them.
                    CommandLineOptions tuned = ParseTrace(
                        root,
                        "--compress",
                        "--trace-profile", "aiz_end_to_end",
                        "--compress-threshold", "4096");
                    AssertEx.Equal(true, tuned.Compress);
                    AssertEx.Equal(4096L, tuned.CompressThresholdBytes);
                    AssertEx.Equal(
                        "aiz_end_to_end", tuned.TraceProfile);
                    AssertEx.Equal(
                        4096L, tuned.CreateCompressor().ThresholdBytes);

                    // 0 keeps its meaning (compress every payload) rather
                    // than reading as "argument absent".
                    AssertEx.Equal(
                        0L,
                        ParseTrace(root, "--compress-threshold", "0")
                            .CompressThresholdBytes);
                });
        }

        private static void RejectsIncompatibleCompressionArguments()
        {
            WithTemporaryDirectory(
                root =>
                {
                    AssertEx.Throws<ArgumentException>(
                        () => ParseTrace(
                            root,
                            "--no-compress",
                            "--compress-threshold", "4096"),
                        "cannot be combined with --no-compress");
                    AssertEx.Throws<ArgumentException>(
                        () => ParseTrace(
                            root, "--compress", "--no-compress"),
                        "--compress cannot be combined with --no-compress");
                    AssertEx.Throws<ArgumentException>(
                        () => ParseTrace(
                            root, "--compress", "--compress"),
                        "Duplicate argument: --compress.");
                    AssertEx.Throws<ArgumentException>(
                        () => ParseTrace(
                            root, "--no-compress", "--no-compress"),
                        "Duplicate argument: --no-compress.");
                    AssertEx.Throws<ArgumentException>(
                        () => ParseTrace(
                            root,
                            "--compress-threshold", "half a megabyte"),
                        "must be an integer");
                    AssertEx.Throws<ArgumentOutOfRangeException>(
                        () => ParseTrace(
                            root, "--compress-threshold", "-1"),
                        "at least 0");
                    // Smoke mode publishes smoke.csv, which is not a payload,
                    // so every compression argument is refused there.
                    foreach (string argument in new[]
                    {
                        "--compress",
                        "--no-compress"
                    })
                    {
                        AssertEx.Throws<ArgumentException>(
                            () => CommandLineOptions.Parse(new[]
                            {
                                "--mode", "smoke",
                                "--rom", Path.Combine(root, "rom.gen"),
                                "--movie", Path.Combine(root, "movie.bk2"),
                                "--output", Path.Combine(root, "out"),
                                argument
                            }),
                            "only supported in trace mode");
                    }
                });
        }

        /// <summary>
        /// Whether a payload lands compressed is only known once it has been
        /// written, so a compressing capture preflights both names it could
        /// publish under. Under --no-compress the .gz name is none of that
        /// capture's business and must not block it.
        /// </summary>
        private static void RefusesExistingCompressedFinalUnlessCompressionIsOff()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "existing");
                    Directory.CreateDirectory(output);
                    File.WriteAllText(
                        Path.Combine(output, "aux_state.jsonl.gz"),
                        "stale\n");

                    AssertEx.Throws<IOException>(
                        () => ParseTrace(root),
                        "aux_state.jsonl.gz");
                    AssertEx.Equal(
                        false, ParseTrace(root, "--no-compress").Compress);
                });
        }

        private static CommandLineOptions ParseTrace(
            string root,
            params string[] extra)
        {
            var args = new List<string>
            {
                "--mode", "trace",
                "--rom", Path.Combine(root, "rom.gen"),
                "--movie", Path.Combine(root, "movie.bk2"),
                "--output", Path.Combine(root, "existing")
            };
            args.AddRange(extra);
            return CommandLineOptions.Parse(args.ToArray());
        }

        private static void Publish(
            string output,
            TracePayloadCompressor compressor,
            string[] contents)
        {
            NoReplacePublisher.StagedPublicationSet staged = StageTraceSet(
                new NoReplacePublisher(compressor),
                output,
                contents);
            staged.Publish();
        }

        private static NoReplacePublisher.StagedPublicationSet StageTraceSet(
            NoReplacePublisher publisher,
            string output,
            string[] contents)
        {
            return publisher.StageAll(
                output,
                TraceFileNames,
                writers =>
                {
                    for (var index = 0; index < contents.Length; index++)
                    {
                        writers[index].Write(contents[index]);
                    }
                });
        }

        /// <summary>
        /// Drains the source like a real compressor would, then writes a
        /// gzip of unrelated, shorter content: the round-trip check has
        /// something valid to decompress and must still reject it on length.
        /// </summary>
        private static void WrongLengthGzip(
            Stream source,
            Stream destination)
        {
            Drain(source);
            WriteGzip(
                destination, Encoding.UTF8.GetBytes("not the payload"));
        }

        /// <summary>
        /// Same length as the source, different bytes — the case a length
        /// check alone would wave through.
        /// </summary>
        private static void EqualLengthGzip(
            Stream source,
            Stream destination,
            byte[] original)
        {
            Drain(source);
            var corrupted = (byte[])original.Clone();
            corrupted[0] = (byte)(corrupted[0] ^ 0xFF);
            WriteGzip(destination, corrupted);
        }

        private static void Drain(Stream source)
        {
            var buffer = new byte[8192];
            while (source.Read(buffer, 0, buffer.Length) > 0)
            {
            }
        }

        private static void WriteGzip(Stream destination, byte[] content)
        {
            using (var gzip = new GZipStream(
                destination,
                CompressionLevel.Optimal,
                true))
            {
                gzip.Write(content, 0, content.Length);
            }
        }

        private static byte[] Decompress(string path)
        {
            using (FileStream stream = File.OpenRead(path))
            using (var gzip = new GZipStream(
                stream,
                CompressionMode.Decompress))
            using (var buffer = new MemoryStream())
            {
                var chunk = new byte[8192];
                int read;
                while ((read = gzip.Read(chunk, 0, chunk.Length)) > 0)
                {
                    buffer.Write(chunk, 0, read);
                }
                return buffer.ToArray();
            }
        }

        private static string JoinFileNames(string directory)
        {
            return string.Join(
                ",",
                Directory.GetFiles(directory)
                    .Select(Path.GetFileName)
                    .OrderBy(name => name, StringComparer.Ordinal)
                    .ToArray());
        }

        private static string JoinRelativeFileNames(string directory)
        {
            return string.Join(",", RelativeFileNames(directory));
        }

        private static string[] RelativeFileNames(string directory)
        {
            return Directory.GetFiles(
                    directory, "*", SearchOption.AllDirectories)
                .Select(path => path.Substring(directory.Length + 1))
                .OrderBy(name => name, StringComparer.Ordinal)
                .ToArray();
        }

        private static void AssertBytesEqual(byte[] expected, byte[] actual)
        {
            AssertEx.Equal(expected.Length, actual.Length);
            AssertEx.Equal(
                BitConverter.ToString(expected),
                BitConverter.ToString(actual));
        }

        private static void AssertContains(
            string value,
            string expectedFragment)
        {
            if (value.IndexOf(
                expectedFragment,
                StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected <" + value + "> to contain <"
                    + expectedFragment + ">.");
            }
        }

        private static void WithTemporaryDirectory(Action<string> body)
        {
            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-compressor-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(root);
            try
            {
                body(root);
            }
            finally
            {
                Directory.Delete(root, true);
            }
        }
    }
}
