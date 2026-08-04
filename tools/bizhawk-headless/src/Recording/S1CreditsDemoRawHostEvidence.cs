using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using Newtonsoft.Json;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Streams the 20 predecessor-common credits fields directly from the
    /// stopped emulator host before the CSV writer formats the row. The
    /// stream deliberately has no access to emitted rows, predecessor data,
    /// comparator output, or candidate hashes.
    /// </summary>
    internal sealed class S1CreditsRawHostEvidenceCollector : IDisposable
    {
        internal const string Format =
            "openggf-s1-credits-raw-observations-v1";
        internal const int MaximumObservations = 86400;
        internal const long MaximumBytes = 64L * 1024L * 1024L;

        private static readonly string[] Routes =
        {
            "credits_00_ghz1", "credits_01_mz2", "credits_02_syz3",
            "credits_03_lz3", "credits_04_slz3", "credits_05_sbz1",
            "credits_06_sbz2", "credits_07_ghz1b"
        };

        private readonly string finalPath;
        private readonly string spoolPath;
        private readonly string captureId;
        private readonly string candidateRoot;
        private readonly int maximumObservations;
        private readonly long maximumBytes;
        private readonly ILinkOperation linkOperation;
        private readonly FileStream spool;
        private readonly SHA256 precedingHash;
        private readonly int[] routeRows = new int[8];
        private int nextDemo;
        private int nextRow;
        private int observationCount;
        private long precedingByteCount;
        private bool finished;
        private Exception cleanupFailure;

        internal Exception CleanupFailure
        {
            get { return cleanupFailure; }
        }

        internal S1CreditsRawHostEvidenceCollector(
            string finalPath,
            string captureId,
            string candidateRoot,
            string romSha1)
            : this(finalPath, captureId, candidateRoot, romSha1,
                MaximumObservations, MaximumBytes,
                LibcLinkOperation.Instance)
        {
        }

        internal S1CreditsRawHostEvidenceCollector(
            string finalPath,
            string captureId,
            string candidateRoot,
            string romSha1,
            int maximumObservations,
            long maximumBytes,
            ILinkOperation linkOperation)
        {
            ValidateIdentity(captureId);
            if (string.IsNullOrEmpty(finalPath))
            {
                throw new ArgumentException(
                    "A raw-observation final path is required.", "finalPath");
            }
            if (string.IsNullOrEmpty(candidateRoot))
            {
                throw new ArgumentException(
                    "A credits candidate root is required.", "candidateRoot");
            }
            if (!string.Equals(
                romSha1, RomIdentity.Sonic1Rev01Sha1,
                StringComparison.Ordinal))
            {
                throw new ArgumentException(
                    "Raw credits observations require the Sonic 1 World REV01 ROM SHA-1.",
                    "romSha1");
            }
            if (maximumObservations < 1)
            {
                throw new ArgumentOutOfRangeException("maximumObservations");
            }
            if (maximumBytes < 1)
            {
                throw new ArgumentOutOfRangeException("maximumBytes");
            }
            if (linkOperation == null)
            {
                throw new ArgumentNullException("linkOperation");
            }

            this.finalPath = Path.GetFullPath(finalPath);
            this.captureId = captureId;
            this.candidateRoot = Path.GetFullPath(candidateRoot)
                .TrimEnd(Path.DirectorySeparatorChar);
            this.maximumObservations = maximumObservations;
            this.maximumBytes = maximumBytes;
            this.linkOperation = linkOperation;
            if (LinuxPathEntry.Exists(this.finalPath))
            {
                throw new IOException(
                    "Raw-observation final output already exists and will not be replaced: "
                    + this.finalPath);
            }

            string parent = Path.GetDirectoryName(this.finalPath);
            Directory.CreateDirectory(parent);
            spoolPath = Path.Combine(parent,
                Path.GetFileName(this.finalPath) + ".tmp."
                + System.Diagnostics.Process.GetCurrentProcess().Id + "."
                + RandomToken());
            FileStream opened = null;
            SHA256 hash = null;
            try
            {
                opened = new FileStream(
                    spoolPath, FileMode.CreateNew, FileAccess.Write,
                    FileShare.None, 64 * 1024, FileOptions.SequentialScan);
                if (Chmod(spoolPath, Convert.ToUInt32("600", 8)) != 0)
                {
                    throw new IOException(
                        "Unable to make the raw-observation spool private.");
                }
                hash = SHA256.Create();
                spool = opened;
                precedingHash = hash;
                WritePreceding(Header(romSha1));
            }
            catch (Exception error)
            {
                var cleanupFailures = new List<Exception>();
                if (hash != null)
                {
                    try { hash.Dispose(); }
                    catch (Exception cleanup) { cleanupFailures.Add(cleanup); }
                }
                if (opened != null)
                {
                    try { opened.Dispose(); }
                    catch (Exception cleanup) { cleanupFailures.Add(cleanup); }
                }
                try { DeleteOrThrow(spoolPath); }
                catch (Exception cleanup) { cleanupFailures.Add(cleanup); }
                if (cleanupFailures.Count != 0)
                {
                    cleanupFailures.Insert(0, error);
                    throw new AggregateException(
                        "Raw-observation spool initialization failed and cleanup was incomplete.",
                        cleanupFailures);
                }
                throw;
            }
        }

        internal void Observe(int demoIndex, int row, IGpgxHost host)
        {
            ThrowIfFinished();
            if (host == null) throw new ArgumentNullException("host");
            if (demoIndex != nextDemo || row != nextRow)
            {
                throw new InvalidOperationException(
                    "Raw credits observations are out of canonical order; expected demo "
                    + nextDemo.ToString(CultureInfo.InvariantCulture)
                    + " row " + nextRow.ToString(CultureInfo.InvariantCulture)
                    + ".");
            }
            if (observationCount > maximumObservations - 20)
            {
                throw new InvalidOperationException(
                    "Raw credits observations exceed the 86,400-observation limit.");
            }

            byte controller = ReadU8(host, S1Ram.Ctrl1);
            byte status = ReadU8(host, S1Ram.PlayerBase + S1Ram.OffStatus);
            byte angle = ReadU8(host, S1Ram.PlayerBase + S1Ram.OffAngle);
            bool air = (status & S1Ram.StatusInAir) != 0;
            bool rolling = (status & S1Ram.StatusRolling) != 0;

            AddDerived(demoIndex, row, "frame", "trace_row_ordinal", Hex4(row));
            AddDerived(demoIndex, row, "input", "s1_rom_controller_mask",
                Hex4(ControllerMask(controller)));
            AddU16(demoIndex, row, "x", 0xFFFFD008, host,
                S1Ram.PlayerBase + S1Ram.OffXPos);
            AddU16(demoIndex, row, "y", 0xFFFFD00C, host,
                S1Ram.PlayerBase + S1Ram.OffYPos);
            AddU16(demoIndex, row, "x_speed", 0xFFFFD010, host,
                S1Ram.PlayerBase + S1Ram.OffXVel);
            AddU16(demoIndex, row, "y_speed", 0xFFFFD012, host,
                S1Ram.PlayerBase + S1Ram.OffYVel);
            AddU16(demoIndex, row, "g_speed", 0xFFFFD014, host,
                S1Ram.PlayerBase + S1Ram.OffInertia);
            AddByte(demoIndex, row, "angle", 0xFFFFD026, angle);
            AddDerived(demoIndex, row, "air", "s1_status_air_bit",
                air ? "1" : "0");
            AddDerived(demoIndex, row, "rolling", "s1_status_rolling_bit",
                rolling ? "1" : "0");
            AddDerived(demoIndex, row, "ground_mode", "s1_ground_mode",
                GroundMode(air, angle).ToString(CultureInfo.InvariantCulture));
            AddU16(demoIndex, row, "x_sub", 0xFFFFD00A, host,
                S1Ram.PlayerBase + S1Ram.OffXSub);
            AddU16(demoIndex, row, "y_sub", 0xFFFFD00E, host,
                S1Ram.PlayerBase + S1Ram.OffYSub);
            AddByte(demoIndex, row, "routine", 0xFFFFD024,
                ReadU8(host, S1Ram.PlayerBase + S1Ram.OffRoutine));
            AddU16(demoIndex, row, "camera_x", 0xFFFFF700, host,
                S1Ram.CameraX);
            AddU16(demoIndex, row, "camera_y", 0xFFFFF704, host,
                S1Ram.CameraY);
            AddU16(demoIndex, row, "rings", 0xFFFFFE20, host,
                S1Ram.RingCount);
            AddByte(demoIndex, row, "status_byte", 0xFFFFD022, status);
            AddU16(demoIndex, row, "v_framecount", 0xFFFFFE04, host,
                S1Ram.FrameCount);
            AddByte(demoIndex, row, "stand_on_obj", 0xFFFFD03D,
                ReadU8(host, S1Ram.PlayerBase + S1Ram.OffStandOnObj));
            nextRow++;
        }

        internal void CompleteRoute(int demoIndex, int rowCount)
        {
            ThrowIfFinished();
            if (demoIndex != nextDemo || rowCount != nextRow || rowCount < 1)
            {
                throw new InvalidOperationException(
                    "Raw credits route completion is inconsistent for demo "
                    + demoIndex.ToString(CultureInfo.InvariantCulture) + ".");
            }
            routeRows[demoIndex] = rowCount;
            nextDemo++;
            nextRow = 0;
        }

        internal void Seal(S1CreditsDemoCaptureResult result)
        {
            ThrowIfFinished();
            if (result == null) throw new ArgumentNullException("result");
            if (nextDemo != 8 || !AllEight(result.CapturedIndices))
            {
                throw new InvalidOperationException(
                    "Raw credits observations cannot seal without all eight routes.");
            }
            if (!Directory.Exists(candidateRoot))
            {
                throw new InvalidOperationException(
                    "Raw credits observations cannot seal before candidate publication.");
            }

            precedingHash.TransformFinalBlock(new byte[0], 0, 0);
            string completion = Completion(
                precedingHash.Hash, precedingByteCount);
            WriteUnhashed(completion, true);
            spool.Flush(true);
            spool.Dispose();
            precedingHash.Dispose();

            try
            {
                linkOperation.Create(spoolPath, finalPath);
            }
            catch
            {
                finished = true;
                DeleteOrThrow(spoolPath);
                throw;
            }
            finished = true;
            try
            {
                DeleteOrThrow(spoolPath);
            }
            catch (Exception cleanup)
            {
                cleanupFailure = cleanup;
            }
        }

        public void Dispose()
        {
            if (finished)
            {
                if (cleanupFailure != null)
                {
                    throw new IOException(
                        "Raw-observation spool cleanup failed after publication.",
                        cleanupFailure);
                }
                return;
            }
            finished = true;
            var failures = new List<Exception>();
            try { spool.Dispose(); }
            catch (Exception cleanup) { failures.Add(cleanup); }
            try { precedingHash.Dispose(); }
            catch (Exception cleanup) { failures.Add(cleanup); }
            try { DeleteOrThrow(spoolPath); }
            catch (Exception cleanup) { failures.Add(cleanup); }
            if (failures.Count != 0)
            {
                throw new AggregateException(
                    "Raw-observation spool cleanup failed; final sidecar remains absent.",
                    failures);
            }
        }

        internal static void ValidateIdentity(string identity)
        {
            bool invalid = string.IsNullOrEmpty(identity)
                || identity == "." || identity == "..";
            if (!invalid)
            {
                foreach (char item in identity)
                {
                    if (item < 0x20 || item > 0x7E
                        || item == '/' || item == '\\')
                    {
                        invalid = true;
                        break;
                    }
                }
            }
            if (invalid)
            {
                throw new ArgumentException(
                    "Credits raw-observation identity must be printable ASCII without path separators and cannot be . or ...",
                    "identity");
            }
        }

        private void AddU16(
            int demo, int row, string field, uint address,
            IGpgxHost host, int hostAddress)
        {
            AddDirect(demo, row, field, address, "big",
                Hex4(ReadU16(host, hostAddress)));
        }

        private void AddByte(
            int demo, int row, string field, uint address, byte value)
        {
            AddDirect(demo, row, field, address, "byte", Hex2(value));
        }

        private void AddDirect(
            int demo, int row, string field, uint address,
            string endianness, string value)
        {
            WriteObservation(demo, row, field,
                "\"ram_address\":" + Quote("0x" + address.ToString("X8"))
                + ",\"endianness\":" + Quote(endianness), value);
        }

        private void AddDerived(
            int demo, int row, string field,
            string derivation, string value)
        {
            WriteObservation(demo, row, field,
                "\"derivation\":" + Quote(derivation), value);
        }

        private void WriteObservation(
            int demo, int row, string field,
            string provenance, string value)
        {
            S1CreditsDemoDefinition definition = S1CreditsDemoCatalog.Get(demo);
            string candidateDirectory =
                S1CreditsDemoCollectionSink.DirectoryName(definition);
            string line = "{\"record_type\":\"observation\""
                + ",\"demo_index\":" + Dec(demo)
                + ",\"route\":" + Quote(Routes[demo])
                + ",\"candidate_directory\":" + Quote(candidateDirectory)
                + ",\"row\":" + Dec(row)
                + ",\"common_field\":" + Quote(field)
                + "," + provenance
                + ",\"raw_value\":" + Quote(value) + "}";
            WritePreceding(line);
            observationCount++;
        }

        private string Header(string romSha1)
        {
            return "{\"record_type\":\"header\""
                + ",\"format\":" + Quote(Format)
                + ",\"capture_id\":" + Quote(captureId)
                + ",\"candidate_root\":" + Quote(candidateRoot)
                + ",\"rom_sha1\":" + Quote(romSha1.ToLowerInvariant())
                + ",\"recorder\":" + Quote(TraceContract.NativeRecorder)
                + ",\"recorder_version\":" + Quote(TraceContract.RecorderVersion)
                + "}";
        }

        private string Completion(byte[] digest, long byteCount)
        {
            int totalRows = 0;
            var rows = new StringBuilder();
            rows.Append('{');
            for (int demo = 0; demo < Routes.Length; demo++)
            {
                if (demo != 0) rows.Append(',');
                rows.Append(Quote(Routes[demo])).Append(':')
                    .Append(Dec(routeRows[demo]));
                totalRows += routeRows[demo];
            }
            rows.Append('}');
            return "{\"record_type\":\"completion\""
                + ",\"capture_id\":" + Quote(captureId)
                + ",\"candidate_root\":" + Quote(candidateRoot)
                + ",\"all_eight_complete\":true"
                + ",\"route_rows\":" + rows.ToString()
                + ",\"total_rows\":" + Dec(totalRows)
                + ",\"observation_count\":" + Dec(observationCount)
                + ",\"preceding_byte_count\":"
                + byteCount.ToString(CultureInfo.InvariantCulture)
                + ",\"preceding_sha256\":" + Quote(LowerHex(digest))
                + "}";
        }

        private void WritePreceding(string line)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(line + "\n");
            if (precedingByteCount > maximumBytes - bytes.Length)
            {
                throw new InvalidOperationException(
                    "Raw credits observations exceed the 64-MiB limit.");
            }
            spool.Write(bytes, 0, bytes.Length);
            precedingHash.TransformBlock(bytes, 0, bytes.Length, bytes, 0);
            precedingByteCount += bytes.Length;
        }

        private void WriteUnhashed(string line, bool enforceTotalLimit)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(line + "\n");
            if (enforceTotalLimit
                && precedingByteCount > maximumBytes - bytes.Length)
            {
                throw new InvalidOperationException(
                    "Raw credits observations exceed the 64-MiB limit.");
            }
            spool.Write(bytes, 0, bytes.Length);
        }

        private void ThrowIfFinished()
        {
            if (finished)
            {
                throw new InvalidOperationException(
                    "Raw credits observations are already finalized.");
            }
        }

        private static bool AllEight(IList<int> captured)
        {
            if (captured == null || captured.Count != 8) return false;
            for (int index = 0; index < 8; index++)
            {
                if (captured[index] != index) return false;
            }
            return true;
        }

        private static string Quote(string value)
        {
            return JsonConvert.ToString(value);
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        private static byte ReadU8(IGpgxHost host, int address)
        {
            return host.ReadMainRamByte(address);
        }

        private static ushort ReadU16(IGpgxHost host, int address)
        {
            return (ushort)((ReadU8(host, address) << 8)
                | ReadU8(host, address + 1));
        }

        private static int ControllerMask(byte raw)
        {
            int mask = raw & 0x0F;
            if ((raw & 0x70) != 0) mask |= 0x10;
            return mask;
        }

        private static int GroundMode(bool air, byte angle)
        {
            if (air) return 0;
            if (angle <= 0x1F || angle >= 0xE0) return 0;
            if (angle <= 0x5F) return 1;
            if (angle <= 0x9F) return 2;
            return 3;
        }

        private static string Hex4(int value)
        {
            return value.ToString("X4", CultureInfo.InvariantCulture);
        }

        private static string Hex2(int value)
        {
            return value.ToString("X2", CultureInfo.InvariantCulture);
        }

        private static string LowerHex(byte[] bytes)
        {
            var result = new StringBuilder(bytes.Length * 2);
            foreach (byte item in bytes)
            {
                result.Append(item.ToString("x2", CultureInfo.InvariantCulture));
            }
            return result.ToString();
        }

        private static string RandomToken()
        {
            var bytes = new byte[16];
            using (RandomNumberGenerator random = RandomNumberGenerator.Create())
            {
                random.GetBytes(bytes);
            }
            return BitConverter.ToString(bytes).Replace("-", "");
        }

        private static void DeleteOrThrow(string path)
        {
            File.Delete(path);
            if (LinuxPathEntry.Exists(path))
            {
                throw new IOException(
                    "Raw-observation spool still exists after cleanup: " + path);
            }
        }

        [DllImport("libc", EntryPoint = "chmod", SetLastError = true)]
        private static extern int Chmod(string path, uint mode);
    }

    /// <summary>
    /// Resolves credits candidate, installed fixture, and raw-observation
    /// paths before capture creates any of them. Symlink traversal is refused
    /// rather than merely followed, and absent suffixes participate in the
    /// overlap comparison.
    /// </summary>
    internal static class CreditsRawObservationPathPolicy
    {
        internal static void Validate(
            string candidateRoot,
            string sidecarPath,
            string installedRoot)
        {
            if (string.IsNullOrEmpty(candidateRoot)
                || string.IsNullOrEmpty(sidecarPath)
                || string.IsNullOrEmpty(installedRoot))
            {
                throw new ArgumentException(
                    "Credits raw-observation path validation requires candidate, sidecar, and installed roots.");
            }
            string candidate = Path.GetFullPath(candidateRoot);
            string sidecar = Path.GetFullPath(sidecarPath);
            string installed = Path.GetFullPath(installedRoot);
            if (LinuxPathEntry.Exists(candidate))
            {
                throw new IOException(
                    "Credits candidate output root already exists: " + candidate);
            }
            if (LinuxPathEntry.Exists(sidecar))
            {
                throw new IOException(
                    "Raw-observation final output already exists and will not be replaced: "
                    + sidecar);
            }

            RejectSymlinkTraversal(candidate);
            RejectSymlinkTraversal(sidecar);
            RejectSymlinkTraversal(installed);
            string resolvedCandidate = LinuxPathEntry.ResolveProposedPath(candidate);
            string resolvedSidecar = LinuxPathEntry.ResolveProposedPath(sidecar);
            string resolvedInstalled = LinuxPathEntry.ResolveProposedPath(installed);
            if (Overlaps(resolvedCandidate, resolvedInstalled))
            {
                throw new ArgumentException(
                    "credits_demo candidate output must remain outside the installed fixture root.");
            }
            if (Overlaps(resolvedSidecar, resolvedCandidate))
            {
                throw new ArgumentException(
                    "Credits raw observations must remain outside the candidate root.");
            }
            if (Overlaps(resolvedSidecar, resolvedInstalled))
            {
                throw new ArgumentException(
                    "Credits raw observations must remain outside the installed fixture root.");
            }
        }

        private static void RejectSymlinkTraversal(string path)
        {
            string full = Path.GetFullPath(path);
            string current = Path.GetPathRoot(full);
            string suffix = full.Substring(current.Length);
            string[] parts = suffix.Split(new[] { Path.DirectorySeparatorChar },
                StringSplitOptions.RemoveEmptyEntries);
            foreach (string part in parts)
            {
                current = Path.Combine(current, part);
                if (!LinuxPathEntry.Exists(current)) break;
                if (LinuxPathEntry.IsSymbolicLink(current))
                {
                    throw new ArgumentException(
                        "Credits raw-observation paths reject symlink traversal: "
                        + current);
                }
            }
        }

        private static bool Overlaps(string first, string second)
        {
            return IsSameOrChild(first, second)
                || IsSameOrChild(second, first);
        }

        private static bool IsSameOrChild(string path, string root)
        {
            string normalizedPath = path.TrimEnd(Path.DirectorySeparatorChar);
            string normalizedRoot = root.TrimEnd(Path.DirectorySeparatorChar);
            return normalizedPath.Equals(normalizedRoot, StringComparison.Ordinal)
                || normalizedPath.StartsWith(
                    normalizedRoot + Path.DirectorySeparatorChar,
                    StringComparison.Ordinal);
        }
    }
}
