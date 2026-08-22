using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class GpgxYmWriteTimingLabTests
    {
        private const string RomSha1 =
            "cfbf98c36c776677290a872547ac47c53d2761d6";
        private const string MovieSha256 =
            "ad40fb0b0a74fa12b08ab71b2e48a7455b388d14f43f4cded502ac4a15d1b3c0";
        private const string CorrectedWriteProjectionSha256 =
            "33cef3472ad2c9c0d0d50e27f6ae574b51e02755420cd9c542b0443996013f99";
        private const string NativeFm5Sha256 =
            "4277bc5f29fa086013b49f006fd887b9795ebfbb17e8288de4c50005bb97e6d8";
        private const int FirstCaptureFrame = 3000;
        private const int LastCaptureFrame = 3380;
        private const int OnsetWindowSamples = 5334;

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            if (Environment.GetEnvironmentVariable(
                    "OPENGGF_GPGX_YM_TIMING_LAB") != "1")
                return;
            tests.Add(new TestMain.TestCase(
                "GpgxYmWriteTimingLabTests capture corrected S3K Blue Sphere YM timing",
                CaptureCorrectedS3kBlueSphereYmTiming,
                game: "s3k", serial: true, estimatedSeconds: 45.0));
        }

        private static void CaptureCorrectedS3kBlueSphereYmTiming()
        {
            string rom = RequiredEnvironment("S3K_ROM_PATH");
            string moviePath = RequiredEnvironment("S3K_BK2_PATH");
            string output = RequiredEnvironment("OPENGGF_YM_TIMING_OUTPUT");
            string rawDirectory = RequiredEnvironment(
                "OPENGGF_YM_TIMING_RAW_DIRECTORY");
            string patchSha256 = RequiredEnvironment(
                "OPENGGF_YM_TIMING_PATCH_SHA256");
            string coreSha256 = RequiredEnvironment(
                "OPENGGF_YM_TIMING_CORE_SHA256");
            AssertEx.Equal(RomSha1, Sha1(rom));
            AssertEx.Equal(MovieSha256, Sha256(moviePath));
            if (File.Exists(output))
                throw new IOException("YM timing output already exists: " + output);
            if (Directory.Exists(rawDirectory) || File.Exists(rawDirectory))
                throw new IOException("YM timing raw path already exists: " + rawDirectory);
            Directory.CreateDirectory(rawDirectory);

            Bk2Movie movie = Bk2Reader.Read(moviePath);
            var fm5 = new List<int>();
            var rawWrites = new List<string> {
                "frame\tfm5_ordinal\tcycles\tport\tregister\tvalue\tdma_stall_count"
            };
            var groups = new List<WriteGroup>();
            var attenuation = new int[4];
            var fmAddress = new uint[2];
            List<TimedWrite> currentGroup = null;
            bool blueSphereVoice = false;
            using (var host = GpgxHost.Open(
                rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                GpgxAudioTraceNative api =
                    (GpgxAudioTraceNative)host.CreateAudioTraceApi();
                ConfigureDiagnosticOnly(api);
                using (IEnumerator<Bk2Frame> rows =
                    movie.OpenFrameStream().GetEnumerator())
                {
                    for (int frame = 0; frame <= LastCaptureFrame; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        AssertEx.Equal(0, api.BeginFrame());
                        host.AdvanceDiagnosticAudio();
                        AssertEx.Equal(0, api.EndFrame());
                        int stereoFrames;
                        host.DrainDiagnosticAudio(out stereoFrames);
                        if (stereoFrames <= 0)
                            throw new InvalidDataException(
                                "Diagnostic audio produced no samples at frame " + frame + ".");
                        uint count, overflow, copied;
                        AssertEx.Equal(0, api.EventCount(out count, out overflow));
                        AssertEx.Equal(0u, overflow);
                        GpgxAudioObserverAdapter.FirstFault fault;
                        AssertEx.Equal(0, api.GetFirstFault(out fault));
                        AssertEx.Equal(0u, fault.Reason);
                        GpgxAudioTraceEvent[] events;
                        AssertEx.Equal(0,
                            api.DrainNative(count, out copied, out events));
                        AssertEx.Equal(count, copied);
                        for (int index = 0; index < copied; index++)
                        {
                            GpgxAudioTraceEvent value = events[index];
                            AssertEx.Equal((uint)index, value.Ordinal);
                            if (value.Kind != 24) continue;
                            int sample = unchecked((int)(uint)value.Payload);
                            if (value.Subject == 4)
                            {
                                fm5.Add(sample);
                                continue;
                            }
                            if (value.Subject >= 8 && value.Subject <= 11)
                            {
                                attenuation[value.Subject - 8] = sample;
                                continue;
                            }
                            if (value.Subject != 12) continue;
                            uint packed = unchecked((uint)value.Payload);
                            uint masterCycle = unchecked(
                                (uint)(value.Payload >> 32));
                            if ((value.Flags & 1) == 0)
                                throw new InvalidDataException(
                                    "FM write preceded fm_update frontier at frame "
                                    + frame + ".");
                            uint address = (packed >> 8) & 3u;
                            uint data = packed & 0xffu;
                            if ((address & 1u) == 0u)
                            {
                                fmAddress[address >> 1] = data;
                                continue;
                            }
                            int port = checked((int)(address >> 1));
                            int register = checked((int)fmAddress[port]);
                            int dmaStallCount = value.Offset;
                            rawWrites.Add(frame + "\t" + fm5.Count + "\t"
                                + masterCycle + "\t" + port + "\t"
                                + register + "\t" + data + "\t"
                                + dmaStallCount);
                            if (frame < FirstCaptureFrame) continue;
                            if (IsFirstMaximumRelease(
                                port, register, checked((int)data)))
                            {
                                currentGroup = new List<TimedWrite>();
                                blueSphereVoice = false;
                            }
                            if (currentGroup != null && IsFm5Write(
                                port, register, checked((int)data)))
                            {
                                currentGroup.Add(new TimedWrite(
                                    frame, fm5.Count, masterCycle, port,
                                    register, checked((int)data),
                                    dmaStallCount));
                            }
                            if (currentGroup != null && port == 1
                                && register == 0xb1 && data == 0x05)
                                blueSphereVoice = true;
                            if (currentGroup != null && IsFm5KeyOn(
                                port, register, checked((int)data)))
                            {
                                if (blueSphereVoice)
                                    AddGroup(groups, currentGroup, attenuation);
                                currentGroup = null;
                                blueSphereVoice = false;
                            }
                        }
                    }
                }
            }
            if (currentGroup != null)
                throw new InvalidDataException("The final YM write group is incomplete.");
            AssertEx.Equal(12, groups.Count);
            AssertEx.Equal(3262, groups[7].Frame);
            AssertEx.Equal(151590u, groups[7].RelativeLastMasterCycle);
            AssertEx.Equal(33, groups[7].Writes.Count - 1);
            foreach (WriteGroup group in groups)
                if (group.Writes.Any(write => write.DmaStallCount != 0))
                    throw new InvalidDataException(
                        "An audited YM write group overlapped VDP DMA.");

            string writesPath = Path.Combine(rawDirectory, "native-writes.tsv");
            string fm5Path = Path.Combine(rawDirectory, "native-fm5.s32le");
            File.WriteAllLines(writesPath, rawWrites);
            WriteInts(fm5Path, fm5);
            string rawWritesSha256 = Sha256(writesPath);
            string fm5Sha256 = Sha256(fm5Path);
            AssertEx.Equal(NativeFm5Sha256, fm5Sha256);
            PopulateOnsetRms(groups, fm5);
            WriteOracleCreateNew(output, groups, patchSha256, coreSha256,
                rawWritesSha256, fm5Sha256);
            Console.WriteLine("YM_TIMING_ORACLE groups=" + groups.Count
                + " writes_sha256=" + rawWritesSha256
                + " fm5_sha256=" + fm5Sha256
                + " projection_sha256=" + CorrectedWriteProjectionSha256
                + " group7_relative_cycles="
                + groups[7].RelativeLastMasterCycle
                + " group7_native_keyon=["
                + string.Join(",", groups[7].KeyOnAttenuation) + "]"
                + " group7_native_rms="
                + groups[7].OnsetRms.ToString("F2", CultureInfo.InvariantCulture));
        }

        private static void ConfigureDiagnosticOnly(GpgxAudioTraceNative api)
        {
            var config = new GpgxAudioObserverAdapter.Config {
                Magic = 0x31544147, AbiVersion = 1, StructSize = 64,
                KindSize = 16, HookSize = 32, RangeSize = 16,
                EventSize = 32, MaxDepth = 8, MaxOpcodeBytes = 8,
                ResetServiceKind = 1, WatchMaskBytes = 8192,
                KindCount = 1, HookCount = 1, RangeCount = 1,
                SnapshotBytesTotal = 1, EventCapacity = 65536,
                MaxServiceTokensPerFrame = 65535
            };
            var kinds = new[] { new GpgxAudioObserverAdapter.ServiceKind {
                KindId = 1, CancellationRangeCount = 1
            } };
            var hooks = new[] { new GpgxAudioObserverAdapter.ServiceHook {
                HookToken = 1, Action = 1, Cpu = 2, Pc = 0xffffff,
                ServiceKindId = 1, OpcodeLength = 1, Opcode = 0
            } };
            var ranges = new[] { new GpgxAudioObserverAdapter.SnapshotRange {
                RangeId = 1, Start = 0, Length = 1
            } };
            AssertEx.Equal(0, api.Configure(ref config, new byte[8192],
                kinds, hooks, ranges));
        }

        private static bool IsFirstMaximumRelease(
            int port, int register, int value)
        {
            return port == 1 && register == 0x81 && value == 0xff;
        }

        private static bool IsFm5Write(int port, int register, int value)
        {
            return (port == 1 && ((register >= 0x30 && register <= 0x9d
                    && (register & 3) == 1)
                || register == 0xa1 || register == 0xa5
                || register == 0xb1 || register == 0xb5))
                || (port == 0 && register == 0x28 && (value & 7) == 5);
        }

        private static bool IsFm5KeyOn(int port, int register, int value)
        {
            return port == 0 && register == 0x28 && value == 0xf5;
        }

        private static void AddGroup(List<WriteGroup> groups,
            List<TimedWrite> writes, int[] attenuation)
        {
            if (writes.Count != 34)
                throw new InvalidDataException(
                    "Expected 34 FM5 writes, got " + writes.Count + ".");
            uint firstCycle = writes[0].MasterCycle;
            for (int index = 0; index < 4; index++)
            {
                TimedWrite write = writes[index];
                if (write.Port != 1 || write.Register != 0x81 + index * 4
                    || write.Value != 0xff)
                    throw new InvalidDataException(
                        "YM group does not begin with four maximum-release writes.");
            }
            for (int index = 0; index < writes.Count; index++)
            {
                TimedWrite write = writes[index];
                write.SourceOrdinal = index;
                write.RelativeMasterCycle = write.MasterCycle - firstCycle;
            }
            groups.Add(new WriteGroup {
                GroupOrdinal = groups.Count,
                Frame = writes[0].Frame,
                FirstInternalOrdinal = writes[0].InternalOrdinal,
                KeyOnInternalOrdinal = writes[writes.Count - 1].InternalOrdinal,
                RelativeLastMasterCycle =
                    writes[writes.Count - 1].MasterCycle - firstCycle,
                KeyOnAttenuation = new[] {
                    attenuation[0], attenuation[2], attenuation[1], attenuation[3]
                },
                Writes = writes
            });
        }

        private static void PopulateOnsetRms(
            List<WriteGroup> groups, List<int> fm5)
        {
            foreach (WriteGroup group in groups)
            {
                long squareSum = 0;
                int start = checked((int)group.KeyOnInternalOrdinal);
                if (start + OnsetWindowSamples > fm5.Count)
                    throw new InvalidDataException(
                        "FM5 capture ended inside the onset window.");
                for (int index = start;
                    index < start + OnsetWindowSamples; index++)
                    squareSum += (long)fm5[index] * fm5[index];
                group.OnsetRms = Math.Round(
                    Math.Sqrt(squareSum / (double)OnsetWindowSamples), 2);
            }
        }

        private static void WriteOracleCreateNew(string path,
            List<WriteGroup> groups, string patchSha256, string coreSha256,
            string rawWritesSha256, string fm5Sha256)
        {
            var root = new JObject {
                ["schema"] = "openggf.s3k-ym-write-timing-oracle.v1",
                ["event_phase"] = "post_fm_update",
                ["provenance"] = new JObject {
                    ["bizhawk_version"] = "2.11",
                    ["bizhawk_commit"] =
                        "427556b5ef3ac437eba754d90c5e7e9096c9a8df",
                    ["gpgx_commit"] =
                        "051d430d3d1b54625f9900c8f152d7f232e06daf",
                    ["rom_sha1"] = RomSha1,
                    ["bk2_sha256"] = MovieSha256,
                    ["diagnostic_patch_sha256"] = patchSha256,
                    ["diagnostic_core_sha256"] = coreSha256,
                    ["native_writes_sha256"] = rawWritesSha256,
                    ["native_writes_projection_sha256"] =
                        CorrectedWriteProjectionSha256,
                    ["native_fm5_sha256"] = fm5Sha256
                },
                ["groups"] = new JArray(groups.Select(ToJson))
            };
            string payload = root.ToString(Formatting.None);
            root["terminal_sha256"] = Sha256(
                Encoding.UTF8.GetBytes(payload));
            byte[] bytes = Encoding.UTF8.GetBytes(
                root.ToString(Formatting.None) + "\n");
            using (var stream = new FileStream(path, FileMode.CreateNew,
                FileAccess.Write, FileShare.None))
                stream.Write(bytes, 0, bytes.Length);
        }

        private static JObject ToJson(WriteGroup group)
        {
            return new JObject {
                ["group_ordinal"] = group.GroupOrdinal,
                ["frame"] = group.Frame,
                ["first_internal_ordinal"] = group.FirstInternalOrdinal,
                ["key_on_internal_ordinal"] = group.KeyOnInternalOrdinal,
                ["relative_last_master_cycle"] =
                    group.RelativeLastMasterCycle,
                ["key_on_attenuation"] =
                    new JArray(group.KeyOnAttenuation),
                ["onset_rms"] = group.OnsetRms,
                ["writes"] = new JArray(group.Writes.Select(write =>
                    new JObject {
                        ["source_ordinal"] = write.SourceOrdinal,
                        ["master_cycle"] = write.MasterCycle,
                        ["relative_master_cycle"] =
                            write.RelativeMasterCycle,
                        ["internal_ordinal"] = write.InternalOrdinal,
                        ["port"] = write.Port,
                        ["register"] = write.Register,
                        ["value"] = write.Value,
                        ["dma_stall_count"] = write.DmaStallCount
                    }))
            };
        }

        private static void WriteInts(string path, IEnumerable<int> values)
        {
            using (var writer = new BinaryWriter(
                new FileStream(path, FileMode.CreateNew, FileAccess.Write)))
                foreach (int value in values) writer.Write(value);
        }

        private static string RequiredEnvironment(string name)
        {
            string value = Environment.GetEnvironmentVariable(name);
            if (string.IsNullOrWhiteSpace(value))
                throw new InvalidOperationException(
                    "Required environment variable is missing: " + name);
            return value;
        }

        private static string Sha1(string path)
        {
            using (SHA1 digest = SHA1.Create())
            using (FileStream stream = File.OpenRead(path))
                return Hex(digest.ComputeHash(stream));
        }

        private static string Sha256(string path)
        {
            using (SHA256 digest = SHA256.Create())
            using (FileStream stream = File.OpenRead(path))
                return Hex(digest.ComputeHash(stream));
        }

        private static string Sha256(byte[] bytes)
        {
            using (SHA256 digest = SHA256.Create())
                return Hex(digest.ComputeHash(bytes));
        }

        private static string Hex(byte[] bytes)
        {
            return string.Concat(bytes.Select(value =>
                value.ToString("x2", CultureInfo.InvariantCulture)));
        }

        private sealed class TimedWrite
        {
            internal TimedWrite(int frame, long internalOrdinal,
                uint masterCycle, int port, int register, int value,
                int dmaStallCount)
            {
                Frame = frame;
                InternalOrdinal = internalOrdinal;
                MasterCycle = masterCycle;
                Port = port;
                Register = register;
                Value = value;
                DmaStallCount = dmaStallCount;
            }

            internal int Frame { get; private set; }
            internal long InternalOrdinal { get; private set; }
            internal uint MasterCycle { get; private set; }
            internal int Port { get; private set; }
            internal int Register { get; private set; }
            internal int Value { get; private set; }
            internal int DmaStallCount { get; private set; }
            internal int SourceOrdinal { get; set; }
            internal uint RelativeMasterCycle { get; set; }
        }

        private sealed class WriteGroup
        {
            internal int GroupOrdinal { get; set; }
            internal int Frame { get; set; }
            internal long FirstInternalOrdinal { get; set; }
            internal long KeyOnInternalOrdinal { get; set; }
            internal uint RelativeLastMasterCycle { get; set; }
            internal int[] KeyOnAttenuation { get; set; }
            internal double OnsetRms { get; set; }
            internal List<TimedWrite> Writes { get; set; }
        }
    }
}
