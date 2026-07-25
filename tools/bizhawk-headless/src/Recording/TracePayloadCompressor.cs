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
            ThresholdBytes = thresholdBytes;
            this.compress = compress;
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
            using (var gzip = new GZipStream(
                destination,
                CompressionLevel.Optimal,
                true))
            {
                CopyStream(source, gzip);
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
                return new PayloadDigest(
                    total,
                    BitConverter.ToString(sha.Hash)
                        .Replace("-", string.Empty)
                        .ToLowerInvariant());
            }
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
