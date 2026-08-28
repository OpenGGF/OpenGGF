using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Runtime.InteropServices;
using System.Text;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using OpenGGF.BizHawk.Headless;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class GpgxS3kAudioParityManifestTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "GpgxS3kAudioParityManifestTests pin diagnostic ABI layouts",
                PinsDiagnosticAbiLayouts));
            if (Environment.GetEnvironmentVariable(
                    "OPENGGF_GPGX_S3K_FIRST_SLICE_CAPTURE") == "1")
                tests.Add(new TestMain.TestCase(
                    "GpgxS3kAudioParityManifestTests capture injected first slice",
                    CaptureInjectedFirstSlice,
                    game: "s3k", serial: true, estimatedSeconds: 45.0));
        }

        private static void PinsDiagnosticAbiLayouts()
        {
            AssertEx.Equal(64, Marshal.SizeOf(typeof(GpgxS3kAudioParityConfig)));
            AssertEx.Equal(20, Marshal.SizeOf(typeof(GpgxS3kAudioParityDescriptor)));
            AssertEx.Equal(38, Marshal.SizeOf(typeof(GpgxS3kAudioParityEvent)));
            AssertEx.Equal(16, Marshal.SizeOf(typeof(GpgxS3kAudioParityFault)));
            AssertEx.Equal(32, Marshal.SizeOf(typeof(GpgxS3kPcmConfig)));
            AssertEx.Equal(28, Marshal.SizeOf(typeof(GpgxS3kPcmEvent)));
        }

        private static void CaptureInjectedFirstSlice()
        {
            string rom = RequiredEnvironment("S3K_ROM_PATH");
            string output = RequiredEnvironment("OPENGGF_S3K_PARITY_OUTPUT");
            string captureCase = RequiredEnvironment("OPENGGF_S3K_PARITY_CASE");
            if (File.Exists(output) || Directory.Exists(output))
                throw new IOException("Parity output already exists: " + output);
            byte request;
            int frames;
            byte expectedKind;
            byte expectedTrackType;
            if (captureCase == "collapse")
            { request = 0x59; frames = 125; expectedKind = 11; expectedTrackType = 2; }
            else if (captureCase == "spindash-release")
            { request = 0xB6; frames = 90; expectedKind = 11; expectedTrackType = 2; }
            else if (captureCase == "invincibility")
            { request = 0x2C; frames = 600; expectedKind = 12; expectedTrackType = 1; }
            else if (captureCase == "explode-repeat")
            { request = 0xB4; frames = 160; expectedKind = 11; expectedTrackType = 2; }
            else throw new InvalidDataException("Unknown first-slice case: " + captureCase);

            using (var host = GpgxHost.Open(rom, GpgxHost.CreateGhz1SyncSettings()))
            {
                IGpgxAudioTraceApi trace = host.CreateAudioTraceApi();
                GpgxAudioServiceManifest.Load(Path.Combine(
                    EndToEndTests.ToolDirectory,
                    "fixtures/gpgx-audio-service-manifests-v1.json"),
                    "s3k", trace);
                for (int frame = 0; frame < 600; frame++)
                {
                    AssertEx.Equal(0, trace.BeginFrame());
                    host.ClearButtons();
                    host.AdvanceDiagnosticAudio();
                    AssertEx.Equal(0, trace.EndFrame());
                    DrainOrdinary(trace);
                    int discarded;
                    host.DrainDiagnosticAudio(out discarded);
                }
                host.WriteZ80RamByte(0x1C0A, 0xE2);
                for (int frame = 0; frame < 4; frame++)
                {
                    AssertEx.Equal(0, trace.BeginFrame());
                    host.AdvanceDiagnosticAudio();
                    AssertEx.Equal(0, trace.EndFrame());
                    DrainOrdinary(trace);
                    int discarded;
                    host.DrainDiagnosticAudio(out discarded);
                }

                GpgxS3kAudioParityDepartures parity =
                    host.BindDiagnosticDeparture<GpgxS3kAudioParityDepartures>();
                GpgxS3kPcmDepartures pcm =
                    host.BindDiagnosticDeparture<GpgxS3kPcmDepartures>();
                AssertEx.Equal(1u, parity.gpgx_s3k_audio_parity_abi_version());
                AssertEx.Equal(38u, parity.gpgx_s3k_audio_parity_event_size());
                AssertEx.Equal(32768u, parity.gpgx_s3k_audio_parity_capacity());
                AssertEx.Equal(1u, pcm.gpgx_s3k_pcm_abi_version());
                AssertEx.Equal(28u, pcm.gpgx_s3k_pcm_event_size());
                AssertEx.Equal(16384u, pcm.gpgx_s3k_pcm_capacity());
                var config = new GpgxS3kAudioParityConfig
                {
                    Magic = 0x31503353u,
                    AbiVersion = 1,
                    StructSize = 64,
                    DescriptorSize = 20,
                    EventSize = 38,
                    DescriptorCount = 1,
                    EventCapacity = 32768,
                    SongTrackFirst = 0x1C40,
                    SongTrackEnd = 0x1DF0,
                    SfxTrackFirst = 0x1DF0,
                    SfxTrackEnd = 0x1F40,
                    TrackSize = 0x30,
                    SongBankAddress = 0x1C3E,
                    FixedSfxBank = 0x1F
                };
                var descriptors = new[] { new GpgxS3kAudioParityDescriptor
                {
                    DescriptorId = 1,
                    BeginPc = 0x01E9,
                    EndPc = 0x01C7,
                    BeginOpcode = 0xDD,
                    EndOpcode = 0x11,
                    ExpectedServiceKind = expectedKind,
                    ExpectedTrackType = expectedTrackType
                }};
                var pcmConfig = new GpgxS3kPcmConfig
                {
                    Magic = 0x314D3353u,
                    AbiVersion = 1,
                    StructSize = 32,
                    EventSize = 28,
                    EventCapacity = 16384
                };
                AssertEx.Equal(0, parity.gpgx_s3k_audio_parity_configure(
                    ref config, descriptors));
                AssertEx.Equal(0, pcm.gpgx_s3k_pcm_configure(ref pcmConfig));

                // The YM2612 address latch survives VInts.  Observe neutral
                // configured-mode writes before arming publication so a first
                // data-port write is bound to real chip state, never guessed.
                for (int frame = 0; frame < 4; frame++)
                {
                    AssertEx.Equal(0, trace.BeginFrame());
                    host.ClearButtons();
                    host.AdvanceDiagnosticAudio();
                    AssertEx.Equal(0, trace.EndFrame());
                    DrainOrdinary(trace);
                    int discarded;
                    host.DrainDiagnosticAudio(out discarded);
                }

                using (var writer = new StreamWriter(output, false,
                    new UTF8Encoding(false)))
                using (SHA256 body = SHA256.Create())
                {
                    WriteBody(writer, body, new JObject
                    {
                        ["row"] = "metadata",
                        ["schema"] = "openggf.s3k-first-slice-raw.v1",
                        ["case"] = captureCase,
                        ["rom_sha1"] = "cfbf98c36c776677290a872547ac47c53d2761d6",
                        ["source_mode"] = "injected_z80_queue",
                        ["request"] = request,
                        ["begin_pc"] = descriptors[0].BeginPc,
                        ["end_pc"] = descriptors[0].EndPc,
                        ["service_kind"] = expectedKind,
                        ["track_type"] = expectedTrackType
                    });
                    ulong writeCount = 0;
                    ulong pcmCount = 0;
                    for (int frame = 0; frame < frames; frame++)
                    {
                        host.ClearButtons();
                        if (frame == 0 || (captureCase == "explode-repeat"
                            && frame <= 30 && frame % 3 == 0))
                            host.WriteZ80RamByte(captureCase == "invincibility"
                                ? 0x1C0A : 0x1C0B, request);
                        AssertEx.Equal(0, trace.BeginFrame());
                        AssertEx.Equal(0, parity.gpgx_s3k_audio_parity_begin_frame(
                            checked((uint)frame)));
                        AssertEx.Equal(0, pcm.gpgx_s3k_pcm_begin_frame());
                        host.AdvanceDiagnosticAudio();
                        AssertEx.Equal(0, pcm.gpgx_s3k_pcm_end_frame());
                        int parityEnd = parity.gpgx_s3k_audio_parity_end_frame();
                        if (parityEnd != 0)
                        {
                            GpgxS3kAudioParityFault fault;
                            parity.gpgx_s3k_audio_parity_first_fault(out fault);
                            throw new InvalidDataException("Parity frame " + frame
                                + " failed status=" + parityEnd + " reason="
                                + fault.Reason + " pc=" + fault.Pc.ToString("x")
                                + " track=" + fault.TrackBase.ToString("x"));
                        }
                        AssertEx.Equal(0, trace.EndFrame());
                        DrainParity(writer, body, parity, frame, ref writeCount);
                        DrainPcm(writer, body, pcm, frame, ref pcmCount);
                        DrainOrdinary(trace);
                        int audioFrames;
                        host.DrainDiagnosticAudio(out audioFrames);
                        if (audioFrames <= 0)
                            throw new InvalidDataException("No diagnostic audio at frame " + frame);
                    }
                    body.TransformFinalBlock(new byte[0], 0, 0);
                    writer.WriteLine(new JObject
                    {
                        ["row"] = "terminal",
                        ["frames"] = frames,
                        ["write_count"] = writeCount,
                        ["pcm_count"] = pcmCount,
                        ["overflow"] = 0,
                        ["fault"] = 0,
                        ["body_sha256"] = Hex(body.Hash)
                    }.ToString(Formatting.None));
                }
                AssertEx.Equal(0, parity.gpgx_s3k_audio_parity_disable());
                AssertEx.Equal(0, pcm.gpgx_s3k_pcm_disable());
                AssertEx.Equal(0, trace.Disable());
            }
        }

        private static void DrainParity(StreamWriter writer, SHA256 body,
            GpgxS3kAudioParityDepartures api, int frame, ref ulong total)
        {
            uint count, overflow, copied;
            AssertEx.Equal(0, api.gpgx_s3k_audio_parity_event_count(out count, out overflow));
            AssertEx.Equal(0u, overflow);
            GpgxS3kAudioParityEvent[] values = count == 0 ? null
                : new GpgxS3kAudioParityEvent[checked((int)count)];
            AssertEx.Equal(0, api.gpgx_s3k_audio_parity_drain(values, count, out copied));
            AssertEx.Equal(count, copied);
            for (int i = 0; i < copied; i++)
            {
                GpgxS3kAudioParityEvent value = values[i];
                AssertEx.Equal((uint)i, value.EventOrdinal);
                WriteBody(writer, body, new JObject
                {
                    ["row"] = "write", ["frame"] = frame,
                    ["event"] = value.EventOrdinal, ["cycle"] = value.MasterCycle,
                    ["vint"] = value.VintOrdinal,
                    ["service_entry"] = value.ServiceEntryMasterCycle,
                    ["transaction"] = value.TransactionId,
                    ["service_ordinal"] = value.ServiceOrdinal,
                    ["generation"] = value.Generation,
                    ["track"] = value.TrackBase, ["pointer"] = value.SourcePointer,
                    ["pc"] = value.SourcePc, ["service_kind"] = value.ServiceKind,
                    ["track_type"] = value.TrackType, ["channel"] = value.ChannelId,
                    ["bank"] = value.Bank, ["chip"] = value.Chip,
                    ["port"] = value.Port, ["register"] = value.RegisterId,
                    ["value"] = value.Value
                });
                total++;
            }
        }

        private static void DrainPcm(StreamWriter writer, SHA256 body,
            GpgxS3kPcmDepartures api, int frame, ref ulong total)
        {
            uint count, overflow, copied;
            AssertEx.Equal(0, api.gpgx_s3k_pcm_event_count(out count, out overflow));
            AssertEx.Equal(0u, overflow);
            GpgxS3kPcmEvent[] values = count == 0 ? null
                : new GpgxS3kPcmEvent[checked((int)count)];
            AssertEx.Equal(0, api.gpgx_s3k_pcm_drain(values, count, out copied));
            AssertEx.Equal(count, copied);
            for (int i = 0; i < copied; i++)
            {
                GpgxS3kPcmEvent value = values[i];
                AssertEx.Equal((uint)i, value.EventOrdinal);
                WriteBody(writer, body, new JObject
                {
                    ["row"] = "pcm", ["frame"] = frame,
                    ["event"] = value.EventOrdinal, ["sample"] = value.SampleOrdinal,
                    ["cycle"] = value.MasterCycle, ["tap"] = value.Tap,
                    ["left"] = value.Left, ["right"] = value.Right
                });
                total++;
            }
        }

        private static void DrainOrdinary(IGpgxAudioTraceApi api)
        {
            uint count, overflow, copied;
            AssertEx.Equal(0, api.EventCount(out count, out overflow));
            AssertEx.Equal(0u, overflow);
            var values = count == 0 ? null : new GpgxAudioTraceEvent[checked((int)count)];
            AssertEx.Equal(0, api.Drain(values, count, out copied));
            AssertEx.Equal(count, copied);
        }

        private static void WriteBody(StreamWriter writer, SHA256 sha, JObject row)
        {
            string line = row.ToString(Formatting.None) + "\n";
            byte[] bytes = Encoding.UTF8.GetBytes(line);
            sha.TransformBlock(bytes, 0, bytes.Length, null, 0);
            writer.Write(line);
        }

        private static string RequiredEnvironment(string name)
        {
            string value = Environment.GetEnvironmentVariable(name);
            if (string.IsNullOrEmpty(value))
                throw new InvalidOperationException(name + " is required.");
            return value;
        }

        private static string Hex(byte[] value)
        {
            var result = new StringBuilder(value.Length * 2);
            for (int i = 0; i < value.Length; i++) result.Append(value[i].ToString("x2"));
            return result.ToString();
        }
    }
}
