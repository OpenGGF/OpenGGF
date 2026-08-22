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
            string game = Environment.GetEnvironmentVariable(
                "OPENGGF_YM_TIMING_GAME") ?? "s3k";
            if (game == "s1" || game == "s2")
            {
                RingAuditConfig config = game == "s1"
                    ? RingAuditConfig.Sonic1 : RingAuditConfig.Sonic2;
                tests.Add(new TestMain.TestCase(
                    "GpgxYmWriteTimingLabTests capture corrected "
                        + game.ToUpperInvariant() + " ring YM timing",
                    () => CaptureCorrectedRingYmTiming(config),
                    game: game, serial: true, estimatedSeconds: 90.0));
                return;
            }
            tests.Add(new TestMain.TestCase(
                "GpgxYmWriteTimingLabTests capture corrected S3K Blue Sphere YM timing",
                CaptureCorrectedS3kBlueSphereYmTiming,
                game: "s3k", serial: true, estimatedSeconds: 45.0));
        }

        private static void CaptureCorrectedRingYmTiming(
            RingAuditConfig config)
        {
            string rom = RequiredEnvironment(config.RomEnvironment);
            string moviePath = RequiredEnvironment(config.MovieEnvironment);
            string output = RequiredEnvironment("OPENGGF_YM_TIMING_OUTPUT");
            string rawDirectory = RequiredEnvironment(
                "OPENGGF_YM_TIMING_RAW_DIRECTORY");
            string patchSha256 = RequiredEnvironment(
                "OPENGGF_YM_TIMING_PATCH_SHA256");
            string coreSha256 = RequiredEnvironment(
                "OPENGGF_YM_TIMING_CORE_SHA256");
            AssertEx.Equal(config.RomSha1, Sha1(rom));
            AssertEx.Equal(config.MovieSha256, Sha256(moviePath));
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
            var rawInstructions = new List<string> {
                "frame\tgroup_ordinal\tafter_source_ordinal\tcpu\tpc\topcode\tcycles"
            };
            var groups = new List<WriteGroup>();
            var attenuation = new int[4];
            var fmAddress = new uint[2];
            var preGroupContext = new List<byte>();
            int[] atomicAttenuation = null;
            int[] timedAttenuation = null;
            bool keyOnPending = false;
            List<TimedWrite> currentGroup = null;
            int currentRequestFrame = -1;
            int previousRequestFrame = -1;
            bool currentOverlap = false;
            bool managedAdmissionPending = false;
            using (var host = GpgxHost.Open(
                rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                GpgxYmTimingLabDepartures api =
                    host.BindDiagnosticDeparture<GpgxYmTimingLabDepartures>();
                AssertEx.Equal(32u, api.gpgx_ym_timing_lab_event_size());
                AssertEx.Equal(8192u, api.gpgx_ym_timing_lab_capacity());
                if (config.Z80Admission)
                    AssertEx.Equal(0,
                        api.gpgx_ym_timing_lab_configure_z80_admission(
                            config.RequestPc, config.GroupStartPc,
                            config.SoundId,
                            config.FmChannel,
                            config.OwnerIx));
                using (IDisposable admissionCallback = config.Z80Admission
                    ? null : host.RegisterExecuteCallback(config.RequestPc, () =>
                {
                    uint sound = host.ReadCpuRegister(config.SoundRegister)
                        & 0xffu;
                    if (sound == config.SoundId)
                        managedAdmissionPending = true;
                }))
                using (IDisposable groupStartCallback = config.Z80Admission
                    ? null : host.RegisterExecuteCallback(config.GroupStartPc, () =>
                {
                    if (managedAdmissionPending
                        && (host.ReadCpuRegister("M68K A5") & 0xffffu)
                            == config.FmTrackAddress)
                    {
                        AssertEx.Equal(0,
                            api.gpgx_ym_timing_lab_mark_sound_request(
                                config.SoundId, config.FmChannel));
                        managedAdmissionPending = false;
                    }
                }))
                using (IEnumerator<Bk2Frame> rows =
                    movie.OpenFrameStream().GetEnumerator())
                {
                    for (int frame = 0; frame <= config.LastCaptureFrame; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        AssertEx.Equal(0, api.gpgx_ym_timing_lab_begin_frame());
                        host.AdvanceDiagnosticAudio();
                        AssertEx.Equal(0, api.gpgx_ym_timing_lab_end_frame());
                        int stereoFrames;
                        host.DrainDiagnosticAudio(out stereoFrames);
                        if (stereoFrames <= 0)
                            throw new InvalidDataException(
                                "Diagnostic audio produced no samples at frame " + frame + ".");
                        uint count, overflow, copied;
                        AssertEx.Equal(0, api.gpgx_ym_timing_lab_event_count(
                            out count, out overflow));
                        AssertEx.Equal(0u, overflow);
                        uint fault;
                        AssertEx.Equal(0, api.gpgx_ym_timing_lab_first_fault(out fault));
                        AssertEx.Equal(0u, fault);
                        GpgxAudioTraceEvent[] events = count == 0
                            ? null : new GpgxAudioTraceEvent[checked((int)count)];
                        AssertEx.Equal(0, api.gpgx_ym_timing_lab_drain(
                            events, count, out copied));
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
                            if (value.Subject == 13)
                            {
                                uint packedRequest = unchecked((uint)value.Payload);
                                AssertEx.Equal(config.SoundId, packedRequest & 0xffu);
                                AssertEx.Equal(config.FmChannel,
                                    (packedRequest >> 8) & 0xffu);
                                if (currentGroup != null
                                    && currentGroup.Count != 0)
                                    throw new InvalidDataException(
                                        "A second source-auth ring request preceded key-on.");
                                currentRequestFrame = frame;
                                currentOverlap = previousRequestFrame >= 0
                                    && frame - previousRequestFrame
                                        <= config.SourceDurationFrames;
                                previousRequestFrame = frame;
                                currentGroup = new List<TimedWrite>();
                                preGroupContext.Clear();
                                atomicAttenuation = null;
                                timedAttenuation = null;
                                keyOnPending = false;
                                continue;
                            }
                            if (value.Subject == 14)
                            {
                                if ((value.Flags & 1) != 0)
                                    preGroupContext.Clear();
                                for (int byteIndex = 0;
                                     byteIndex < value.PayloadLength; byteIndex++)
                                    preGroupContext.Add((byte)(value.Payload
                                            >> (byteIndex * 8)));
                                continue;
                            }
                            if (value.Subject == 15)
                            {
                                int[] lane = new int[4];
                                for (int operatorIndex = 0;
                                     operatorIndex < 4; operatorIndex++)
                                    lane[operatorIndex] = (int)((value.Payload
                                            >> (operatorIndex * 16)) & 0xffffu);
                                if (value.Flags == 1) atomicAttenuation = lane;
                                else if (value.Flags == 2) timedAttenuation = lane;
                                else throw new InvalidDataException(
                                    "Unknown native counterfactual lane.");
                                if (keyOnPending && atomicAttenuation != null
                                    && timedAttenuation != null)
                                {
                                    AssertEx.Equal(
                                        string.Join(",", attenuation),
                                        string.Join(",", timedAttenuation));
                                    AddAuditGroup(groups, currentGroup,
                                        attenuation, currentRequestFrame,
                                        currentOverlap, preGroupContext,
                                        atomicAttenuation, timedAttenuation);
                                    currentGroup = null;
                                    currentRequestFrame = -1;
                                    keyOnPending = false;
                                }
                                continue;
                            }
                            if (value.Subject == 16)
                            {
                                if (currentGroup == null) continue;
                                uint pc = unchecked((uint)value.Payload);
                                uint cycles = unchecked((uint)(value.Payload >> 32));
                                rawInstructions.Add(frame + "\t" + groups.Count
                                    + "\t" + (currentGroup.Count - 1) + "\t"
                                    + value.Flags + "\t0x"
                                    + pc.ToString("X", CultureInfo.InvariantCulture)
                                    + "\t0x" + value.Value.ToString(
                                        "X2", CultureInfo.InvariantCulture)
                                    + "\t" + cycles);
                                continue;
                            }
                            if (value.Subject != 12) continue;
                            uint packed = unchecked((uint)value.Payload);
                            uint masterCycle = unchecked((uint)(value.Payload >> 32));
                            if ((value.Flags & 1) == 0)
                                throw new InvalidDataException(
                                    "FM write preceded fm_update frontier at frame " + frame + ".");
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
                            if (currentGroup != null && IsFm5Write(
                                port, register, checked((int)data)))
                            {
                                currentGroup.Add(new TimedWrite(frame, fm5.Count,
                                    masterCycle, port, register,
                                    checked((int)data), dmaStallCount));
                                if (IsFm5KeyOn(port, register,
                                    checked((int)data)))
                                {
                                    keyOnPending = true;
                                }
                            }
                        }
                    }
                }
            }
            if (currentGroup != null)
                throw new InvalidDataException("The final ring request has no key-on group.");
            if (!groups.Any(group => !group.Overlapping)
                || !groups.Any(group => group.Overlapping))
                throw new InvalidDataException(
                    "The reviewed movie did not produce both isolated and overlapping rings.");
            foreach (WriteGroup group in groups)
                if (group.Writes.Any(write => write.DmaStallCount != 0))
                    throw new InvalidDataException(
                        "An audited ring group overlapped VDP DMA.");

            string writesPath = Path.Combine(rawDirectory, "native-writes.tsv");
            string instructionsPath = Path.Combine(rawDirectory,
                "native-instructions.tsv");
            string fm5Path = Path.Combine(rawDirectory, "native-fm5.s32le");
            File.WriteAllLines(writesPath, rawWrites);
            File.WriteAllLines(instructionsPath, rawInstructions);
            WriteInts(fm5Path, fm5);
            PopulateOnsetRms(groups, fm5);
            WriteAuditOracleCreateNew(output, config, groups, patchSha256,
                coreSha256, Sha256(writesPath), Sha256(instructionsPath),
                Sha256(fm5Path));
            Console.WriteLine("YM_TIMING_AUDIT game=" + config.Game
                + " groups=" + groups.Count
                + " isolated=" + groups.Count(group => !group.Overlapping)
                + " overlap=" + groups.Count(group => group.Overlapping)
                + " maximum_relative_cycles="
                + groups.Max(group => group.RelativeLastMasterCycle));
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
                GpgxYmTimingLabDepartures api =
                    host.BindDiagnosticDeparture<GpgxYmTimingLabDepartures>();
                AssertEx.Equal(32u, api.gpgx_ym_timing_lab_event_size());
                AssertEx.Equal(8192u, api.gpgx_ym_timing_lab_capacity());
                using (IEnumerator<Bk2Frame> rows =
                    movie.OpenFrameStream().GetEnumerator())
                {
                    for (int frame = 0; frame <= LastCaptureFrame; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        AssertEx.Equal(0,
                            api.gpgx_ym_timing_lab_begin_frame());
                        host.AdvanceDiagnosticAudio();
                        AssertEx.Equal(0,
                            api.gpgx_ym_timing_lab_end_frame());
                        int stereoFrames;
                        host.DrainDiagnosticAudio(out stereoFrames);
                        if (stereoFrames <= 0)
                            throw new InvalidDataException(
                                "Diagnostic audio produced no samples at frame " + frame + ".");
                        uint count, overflow, copied;
                        AssertEx.Equal(0,
                            api.gpgx_ym_timing_lab_event_count(
                                out count, out overflow));
                        AssertEx.Equal(0u, overflow);
                        uint fault;
                        AssertEx.Equal(0,
                            api.gpgx_ym_timing_lab_first_fault(out fault));
                        AssertEx.Equal(0u, fault);
                        GpgxAudioTraceEvent[] events = count == 0
                            ? null
                            : new GpgxAudioTraceEvent[checked((int)count)];
                        AssertEx.Equal(0,
                            api.gpgx_ym_timing_lab_drain(
                                events, count, out copied));
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

        private static void AddAuditGroup(List<WriteGroup> groups,
            List<TimedWrite> writes, int[] attenuation, int requestFrame,
            bool overlapping, List<byte> preGroupContext,
            int[] atomicAttenuation, int[] timedAttenuation)
        {
            if (writes.Count == 0)
                throw new InvalidDataException("A ring request produced no FM5 writes.");
            uint firstCycle = writes[0].MasterCycle;
            for (int index = 0; index < writes.Count; index++)
            {
                TimedWrite write = writes[index];
                write.SourceOrdinal = index;
                write.RelativeMasterCycle = write.MasterCycle - firstCycle;
            }
            groups.Add(new WriteGroup {
                GroupOrdinal = groups.Count,
                Frame = writes[0].Frame,
                RequestFrame = requestFrame,
                Overlapping = overlapping,
                FirstInternalOrdinal = writes[0].InternalOrdinal,
                KeyOnInternalOrdinal = writes[writes.Count - 1].InternalOrdinal,
                RelativeLastMasterCycle =
                    writes[writes.Count - 1].MasterCycle - firstCycle,
                KeyOnAttenuation = new[] {
                    attenuation[0], attenuation[2], attenuation[1], attenuation[3]
                },
                PreGroupContext = preGroupContext.ToArray(),
                AtomicAttenuation = (int[])atomicAttenuation.Clone(),
                TimedAttenuation = (int[])timedAttenuation.Clone(),
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

        private static void WriteAuditOracleCreateNew(string path,
            RingAuditConfig config, List<WriteGroup> groups,
            string patchSha256, string coreSha256,
            string rawWritesSha256, string rawInstructionsSha256,
            string fm5Sha256)
        {
            var root = new JObject {
                ["schema"] = "openggf.s1-s2-ym-write-timing-audit.v2",
                ["game"] = config.Game,
                ["event_phase"] = "post_fm_update",
                ["source_authentication"] = new JObject {
                    ["admission_pc"] = "0x" + config.RequestPc.ToString(
                        "X", CultureInfo.InvariantCulture),
                    ["group_start_pc"] = "0x" + config.GroupStartPc.ToString(
                        "X", CultureInfo.InvariantCulture),
                    ["sound_id"] = "0x" + config.SoundId.ToString(
                        "X2", CultureInfo.InvariantCulture),
                    ["fm_channel"] = config.FmChannel,
                    ["source_duration_frames"] = config.SourceDurationFrames
                },
                ["provenance"] = new JObject {
                    ["bizhawk_version"] = "2.11",
                    ["bizhawk_commit"] =
                        "427556b5ef3ac437eba754d90c5e7e9096c9a8df",
                    ["gpgx_commit"] =
                        "051d430d3d1b54625f9900c8f152d7f232e06daf",
                    ["rom_sha1"] = config.RomSha1,
                    ["bk2_sha256"] = config.MovieSha256,
                    ["diagnostic_patch_sha256"] = patchSha256,
                    ["diagnostic_core_sha256"] = coreSha256,
                    ["native_writes_sha256"] = rawWritesSha256,
                    ["native_instructions_sha256"] = rawInstructionsSha256,
                    ["native_fm5_sha256"] = fm5Sha256
                },
                ["ruling"] = new JObject {
                    ["isolated_material"] = groups.Any(group =>
                        !group.Overlapping
                        && group.RelativeLastMasterCycle >= 4032
                        && MaximumAttenuationDifference(group) >= 8)
                },
                ["groups"] = new JArray(groups.Select(group => {
                    JObject value = ToJson(group);
                    value["request_frame"] = group.RequestFrame;
                    value["classification"] = group.Overlapping
                        ? "overlap" : "isolated";
                    value["native_counterfactual"] = new JObject {
                        ["pre_group_context_size"] = group.PreGroupContext.Length,
                        ["pre_group_context_sha256"] =
                            Sha256(group.PreGroupContext),
                        ["pre_group_context_base64"] =
                            Convert.ToBase64String(group.PreGroupContext),
                        ["atomic_key_on_attenuation"] =
                            new JArray(group.AtomicAttenuation),
                        ["timed_key_on_attenuation"] =
                            new JArray(group.TimedAttenuation),
                        ["maximum_attenuation_difference"] =
                            MaximumAttenuationDifference(group)
                    };
                    return value;
                }))
            };
            string payload = root.ToString(Formatting.None);
            root["terminal_sha256"] = Sha256(Encoding.UTF8.GetBytes(payload));
            byte[] bytes = Encoding.UTF8.GetBytes(
                root.ToString(Formatting.None) + "\n");
            using (var stream = new FileStream(path, FileMode.CreateNew,
                FileAccess.Write, FileShare.None))
                stream.Write(bytes, 0, bytes.Length);
        }

        private static int MaximumAttenuationDifference(WriteGroup group)
        {
            int maximum = 0;
            for (int index = 0; index < 4; index++)
                maximum = Math.Max(maximum, Math.Abs(
                    group.AtomicAttenuation[index]
                    - group.TimedAttenuation[index]));
            return maximum;
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
            internal int RequestFrame { get; set; }
            internal bool Overlapping { get; set; }
            internal long FirstInternalOrdinal { get; set; }
            internal long KeyOnInternalOrdinal { get; set; }
            internal uint RelativeLastMasterCycle { get; set; }
            internal int[] KeyOnAttenuation { get; set; }
            internal double OnsetRms { get; set; }
            internal List<TimedWrite> Writes { get; set; }
            internal byte[] PreGroupContext { get; set; }
            internal int[] AtomicAttenuation { get; set; }
            internal int[] TimedAttenuation { get; set; }
        }

        private sealed class RingAuditConfig
        {
            internal static readonly RingAuditConfig Sonic1 = new RingAuditConfig(
                "s1", "S1_ROM_PATH", "S1_BK2_PATH",
                "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b",
                "f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b",
                0x721C6u, 0x72C26u, "M68K D7", false, 0xF280u,
                0xB5u, 4u, 0u, 37, 5000);
            internal static readonly RingAuditConfig Sonic2 = new RingAuditConfig(
                "s2", "S2_ROM_PATH", "S2_BK2_PATH",
                "8bca5dcef1af3e00098666fd892dc1c2a76333f9",
                "e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5",
                0x975u, 0xE03u, null, true, 0u,
                0xB5u, 4u, 0x1D90u, 37, 5000);

            private RingAuditConfig(string game, string romEnvironment,
                string movieEnvironment, string romSha1, string movieSha256,
                uint requestPc, uint groupStartPc, string soundRegister,
                bool z80Admission, uint fmTrackAddress,
                uint soundId, uint fmChannel,
                uint ownerIx,
                int sourceDurationFrames, int lastCaptureFrame)
            {
                Game = game;
                RomEnvironment = romEnvironment;
                MovieEnvironment = movieEnvironment;
                RomSha1 = romSha1;
                MovieSha256 = movieSha256;
                RequestPc = requestPc;
                GroupStartPc = groupStartPc;
                SoundRegister = soundRegister;
                Z80Admission = z80Admission;
                FmTrackAddress = fmTrackAddress;
                SoundId = soundId;
                FmChannel = fmChannel;
                OwnerIx = ownerIx;
                SourceDurationFrames = sourceDurationFrames;
                LastCaptureFrame = lastCaptureFrame;
            }

            internal string Game { get; private set; }
            internal string RomEnvironment { get; private set; }
            internal string MovieEnvironment { get; private set; }
            internal string RomSha1 { get; private set; }
            internal string MovieSha256 { get; private set; }
            internal uint RequestPc { get; private set; }
            internal uint GroupStartPc { get; private set; }
            internal string SoundRegister { get; private set; }
            internal bool Z80Admission { get; private set; }
            internal uint FmTrackAddress { get; private set; }
            internal uint SoundId { get; private set; }
            internal uint FmChannel { get; private set; }
            internal uint OwnerIx { get; private set; }
            internal int SourceDurationFrames { get; private set; }
            internal int LastCaptureFrame { get; private set; }
        }
    }

}
