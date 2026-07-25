using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Gzip compression of the two trace payload files, applied inside the
    /// publisher's all-or-nothing publish (see
    /// <see cref="NoReplacePublisher.StagedPublicationSet.Publish"/>).
    ///
    /// It exists because these payloads must never reach a commit
    /// uncompressed: a full complete-run aux stream measures ~254 MB raw
    /// against ~12 MB gzipped, past GitHub's 100 MB per-file hard limit, so
    /// an uncompressed fixture is unpushable rather than merely large. The
    /// manual gzip step it replaces was also error-prone in a specific,
    /// already-observed way: different gzip implementations emit different
    /// container bytes for identical content, which surfaces as a spurious
    /// binary diff in a fixture commit.
    ///
    /// The semantics are a deliberate port of tools/traces/compress-traces.ps1
    /// (which remains the Windows Lua-route path and is not superseded for
    /// that route):
    ///
    /// - only aux_state*.jsonl and physics*.csv are targeted; metadata.json
    ///   and run_manifest.json are stored uncompressed in the fixtures too;
    /// - files below <see cref="ThresholdBytes"/> (default 1 MiB) are left
    ///   alone;
    /// - **verify before destroying**: the gzip is written to its own file,
    ///   decompressed again, and compared against the source by SHA-256 AND
    ///   length. Only then is the uncompressed source discarded. A
    ///   verification failure deletes the gzip and throws, leaving the
    ///   source untouched. That ordering is the safety property this class
    ///   exists to preserve.
    ///
    /// Compression is ON by default (--no-compress opts out); see
    /// CommandLineOptions.Compress for why that default, and why the 1 MiB
    /// threshold makes it agree with the repo's commit policy by
    /// construction. The ROM-backed differential gates opt out: they capture
    /// into a temp directory, compare raw bytes, and commit nothing.
    ///
    /// Output determinism: Mono 6.12's zlib-backed GZipStream writes a fixed
    /// 10-byte header with MTIME zero (1F 8B 08 00 | 00 00 00 00 | 00 03),
    /// i.e. the equivalent of GNU `gzip -n`, so the same input always yields
    /// the same bytes. That is verified rather than assumed — see the
    /// determinism cases in TracePayloadCompressorTests, which fail loudly
    /// if a runtime ever starts stamping a timestamp. Container-level
    /// differences could not affect the differential gates in any case (they
    /// hash decompressed bytes), but reproducible output keeps a fixture
    /// commit free of noise diffs.
    /// </summary>
    public sealed class TracePayloadCompressor
    {
        /// <summary>
        /// compress-traces.ps1's -ThresholdBytes default: 1 MiB. Files
        /// strictly below it are skipped, so a file exactly at the
        /// threshold is compressed.
        /// </summary>
        public const long DefaultThresholdBytes = 1048576;

        internal const string GzipExtension = ".gz";

        private const int CopyBufferBytes = 1048576;

        private readonly Action<Stream, Stream> compress;
        private readonly Func<Stream, Stream> openCompressor;
        private readonly List<string> report = new List<string>();

        public TracePayloadCompressor()
            : this(DefaultThresholdBytes)
        {
        }

        public TracePayloadCompressor(long thresholdBytes)
            : this(thresholdBytes, GzipCopy)
        {
        }

        /// <summary>
        /// Test seam: <paramref name="compress"/> replaces the gzip write so
        /// the verification-failure path can be exercised by injecting a
        /// compressor that produces the wrong bytes, rather than by trusting
        /// the happy path.
        /// </summary>
        internal TracePayloadCompressor(
            long thresholdBytes,
            Action<Stream, Stream> compress)
            : this(thresholdBytes, compress, OpenGzip)
        {
        }

        /// <summary>
        /// Test seam for the STREAMING path:
        /// <paramref name="openCompressor"/> replaces the compressing stream
        /// a staged payload is written through, so the streamed form's
        /// verification failure can be exercised the same way the bulk
        /// form's is.
        /// </summary>
        internal TracePayloadCompressor(
            long thresholdBytes,
            Action<Stream, Stream> compress,
            Func<Stream, Stream> openCompressor)
        {
            if (thresholdBytes < 0)
            {
                throw new ArgumentOutOfRangeException(
                    "thresholdBytes",
                    "The compression threshold must be at least 0 bytes.");
            }
            if (compress == null)
            {
                throw new ArgumentNullException("compress");
            }
            if (openCompressor == null)
            {
                throw new ArgumentNullException("openCompressor");
            }
            ThresholdBytes = thresholdBytes;
            this.compress = compress;
            this.openCompressor = openCompressor;
        }

        public long ThresholdBytes { get; private set; }

        /// <summary>
        /// One line per payload actually COMPRESSED, in publication order;
        /// the CLI writes them to stdout after publication commits. A
        /// payload left alone is not reported: since compression is the
        /// default, a below-threshold capture is the ordinary case and
        /// silence there keeps a capture's stdout unchanged from before the
        /// feature existed.
        /// </summary>
        public string[] Report
        {
            get { return report.ToArray(); }
        }

        /// <summary>
        /// The compress-traces.ps1 file filter ("aux_state*.jsonl" and
        /// "physics*.csv"), matched ordinally: every name this harness
        /// publishes is a fixed lower-case literal.
        /// </summary>
        public static bool IsTracePayloadName(string fileName)
        {
            return MatchesPrefixAndExtension(fileName, "aux_state", ".jsonl")
                || MatchesPrefixAndExtension(fileName, "physics", ".csv");
        }

        internal bool ShouldCompress(string fileName, long length)
        {
            return IsTracePayloadName(fileName) && length >= ThresholdBytes;
        }

        /// <summary>
        /// Compresses <paramref name="sourcePath"/> into
        /// <paramref name="destinationPath"/> and verifies the round trip
        /// before returning. On any failure — including a verification
        /// mismatch — the destination is removed and the exception
        /// propagates with the source still intact.
        /// </summary>
        internal void CompressAndVerify(
            string sourcePath,
            string destinationPath)
        {
            try
            {
                using (FileStream source = File.OpenRead(sourcePath))
                using (var destination = new FileStream(
                    destinationPath,
                    FileMode.CreateNew,
                    FileAccess.Write,
                    FileShare.None))
                {
                    compress(source, destination);
                    destination.Flush(true);
                }

                PayloadDigest expected = DigestFile(sourcePath);
                PayloadDigest actual = DigestGzip(destinationPath);
                if (expected.Length != actual.Length
                    || expected.Hash != actual.Hash)
                {
                    throw new IOException(
                        "gzip verification failed for " + sourcePath
                        + ": decompressed "
                        + Format(actual.Length) + " bytes (sha256 "
                        + actual.Hash + ") from a "
                        + Format(expected.Length) + " byte source (sha256 "
                        + expected.Hash + ").");
                }
            }
            catch
            {
                TryDelete(destinationPath);
                throw;
            }
        }

        /// <summary>
        /// Opens a streaming payload over <paramref name="destination"/>:
        /// the caller writes the PLAINTEXT into
        /// <see cref="StreamingPayload.PlaintextStream"/> and the gzip lands
        /// in the destination as it goes, so the uncompressed form never
        /// exists anywhere. The plaintext is SHA-256'd and counted on its
        /// way into the compressor, which is what lets
        /// <see cref="VerifyStreamedGzip"/> preserve the same
        /// verify-before-destroy guarantee
        /// <see cref="CompressAndVerify"/> gets from re-reading its source.
        /// </summary>
        internal StreamingPayload BeginStreaming(Stream destination)
        {
            if (destination == null)
            {
                throw new ArgumentNullException("destination");
            }
            return new StreamingPayload(openCompressor(destination));
        }

        /// <summary>
        /// The streaming counterpart of the round-trip check inside
        /// <see cref="CompressAndVerify"/>: decompresses the finished gzip
        /// and compares it against the plaintext digest accumulated while
        /// writing, by SHA-256 AND length. When
        /// <paramref name="plainDestinationPath"/> is non-null the
        /// decompressed bytes are also written there — the below-threshold
        /// case, where the published file must be the plain payload and the
        /// single decompress both produces and verifies it.
        ///
        /// Verification failure throws with nothing adopted; the caller's
        /// rollback discards both temporaries and publishes nothing.
        /// </summary>
        internal void VerifyStreamedGzip(
            string gzipPath,
            string plainDestinationPath,
            long expectedLength,
            string expectedHash)
        {
            PayloadDigest actual = plainDestinationPath == null
                ? DigestGzip(gzipPath)
                : DecompressGzipTo(gzipPath, plainDestinationPath);
            if (expectedLength == actual.Length
                && expectedHash == actual.Hash)
            {
                return;
            }
            if (plainDestinationPath != null)
            {
                TryDelete(plainDestinationPath);
            }
            throw new IOException(
                "gzip verification failed for " + gzipPath
                + ": decompressed "
                + Format(actual.Length) + " bytes (sha256 "
                + actual.Hash + ") from a "
                + Format(expectedLength) + " byte payload (sha256 "
                + expectedHash + ").");
        }

        internal void RecordCompressed(
            string finalPath,
            long sourceLength,
            long compressedLength)
        {
            report.Add(
                "Compressed " + finalPath + " -> "
                + finalPath + GzipExtension + " ("
                + Format(sourceLength) + " -> "
                + Format(compressedLength) + " bytes)");
        }

        private static void GzipCopy(Stream source, Stream destination)
        {
            using (Stream gzip = OpenGzip(destination))
            {
                CopyStream(source, gzip);
            }
        }

        private static Stream OpenGzip(Stream destination)
        {
            return new GZipStream(
                destination,
                CompressionLevel.Optimal,
                true);
        }

        private static PayloadDigest DecompressGzipTo(
            string gzipPath, string destinationPath)
        {
            using (FileStream source = File.OpenRead(gzipPath))
            using (var gzip = new GZipStream(
                source,
                CompressionMode.Decompress))
            using (var destination = new FileStream(
                destinationPath,
                FileMode.CreateNew,
                FileAccess.Write,
                FileShare.None))
            {
                using (SHA256 sha = SHA256.Create())
                {
                    var buffer = new byte[CopyBufferBytes];
                    long total = 0;
                    int read;
                    while ((read = gzip.Read(buffer, 0, buffer.Length)) > 0)
                    {
                        total += read;
                        sha.TransformBlock(buffer, 0, read, null, 0);
                        destination.Write(buffer, 0, read);
                    }
                    sha.TransformFinalBlock(new byte[0], 0, 0);
                    destination.Flush(true);
                    return new PayloadDigest(total, FormatHash(sha.Hash));
                }
            }
        }

        private static PayloadDigest DigestFile(string path)
        {
            using (FileStream stream = File.OpenRead(path))
            {
                return Digest(stream);
            }
        }

        private static PayloadDigest DigestGzip(string path)
        {
            using (FileStream stream = File.OpenRead(path))
            using (var gzip = new GZipStream(
                stream,
                CompressionMode.Decompress))
            {
                return Digest(gzip);
            }
        }

        private static PayloadDigest Digest(Stream stream)
        {
            using (SHA256 sha = SHA256.Create())
            {
                var buffer = new byte[CopyBufferBytes];
                long total = 0;
                int read;
                while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
                {
                    total += read;
                    sha.TransformBlock(buffer, 0, read, null, 0);
                }
                sha.TransformFinalBlock(new byte[0], 0, 0);
                return new PayloadDigest(total, FormatHash(sha.Hash));
            }
        }

        internal static string FormatHash(byte[] hash)
        {
            return BitConverter.ToString(hash)
                .Replace("-", string.Empty)
                .ToLowerInvariant();
        }

        private static void CopyStream(Stream source, Stream destination)
        {
            var buffer = new byte[CopyBufferBytes];
            int read;
            while ((read = source.Read(buffer, 0, buffer.Length)) > 0)
            {
                destination.Write(buffer, 0, read);
            }
        }

        private static bool MatchesPrefixAndExtension(
            string fileName,
            string prefix,
            string extension)
        {
            return fileName != null
                && fileName.Length >= prefix.Length + extension.Length
                && fileName.StartsWith(prefix, StringComparison.Ordinal)
                && fileName.EndsWith(extension, StringComparison.Ordinal);
        }

        private static void TryDelete(string path)
        {
            try
            {
                File.Delete(path);
            }
            catch (Exception)
            {
                // The compression failure is what gets reported; a leftover
                // file is safer than obscuring why publication failed.
            }
        }

        private static string Format(long value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        /// <summary>
        /// One payload being compressed as it is written. Write the
        /// plaintext into <see cref="PlaintextStream"/>, then call
        /// <see cref="Finish"/> exactly once to close the deflate stream and
        /// settle <see cref="PlaintextLength"/> / <see cref="PlaintextHash"/>
        /// — the values <see cref="VerifyStreamedGzip"/> checks the finished
        /// gzip against.
        ///
        /// Flush() is deliberately swallowed rather than forwarded to the
        /// compressor: a StreamWriter flushes its character buffer through
        /// this stream, and forwarding that to the deflater could inject a
        /// sync-flush point, which would make the container bytes depend on
        /// where the caller happened to flush. Swallowing it keeps a
        /// streamed gzip byte-identical to the bulk-compressed one (pinned
        /// by the determinism cases in TracePayloadCompressorTests).
        /// </summary>
        internal sealed class StreamingPayload : IDisposable
        {
            private readonly Stream compressor;
            private readonly SHA256 sha;
            private readonly HashingStream plaintext;
            private bool finished;

            internal StreamingPayload(Stream compressor)
            {
                this.compressor = compressor;
                sha = SHA256.Create();
                plaintext = new HashingStream(compressor, sha);
            }

            internal Stream PlaintextStream
            {
                get { return plaintext; }
            }

            internal long PlaintextLength { get; private set; }

            internal string PlaintextHash { get; private set; }

            internal void Finish()
            {
                if (finished)
                {
                    throw new InvalidOperationException(
                        "The streaming payload is already finished.");
                }
                finished = true;
                compressor.Dispose();
                sha.TransformFinalBlock(new byte[0], 0, 0);
                PlaintextLength = plaintext.Length;
                PlaintextHash = FormatHash(sha.Hash);
                sha.Dispose();
            }

            public void Dispose()
            {
                if (finished)
                {
                    return;
                }
                finished = true;
                try
                {
                    compressor.Dispose();
                }
                catch (Exception)
                {
                    // Abandoning a half-written payload: the caller deletes
                    // the temporary, and a disposal failure here must not
                    // mask why the capture failed.
                }
                sha.Dispose();
            }

            private sealed class HashingStream : Stream
            {
                private readonly Stream inner;
                private readonly SHA256 sha;
                private long written;

                internal HashingStream(Stream inner, SHA256 sha)
                {
                    this.inner = inner;
                    this.sha = sha;
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
                    get { return written; }
                }

                public override long Position
                {
                    get { return written; }
                    set
                    {
                        throw new NotSupportedException();
                    }
                }

                public override void Write(
                    byte[] buffer, int offset, int count)
                {
                    if (count <= 0)
                    {
                        return;
                    }
                    written += count;
                    sha.TransformBlock(buffer, offset, count, null, 0);
                    inner.Write(buffer, offset, count);
                }

                public override void Flush()
                {
                    // Intentionally not forwarded; see the class remarks.
                }

                public override int Read(
                    byte[] buffer, int offset, int count)
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
            }
        }

        private struct PayloadDigest
        {
            internal readonly long Length;
            internal readonly string Hash;

            internal PayloadDigest(long length, string hash)
            {
                Length = length;
                Hash = hash;
            }
        }
    }
}
