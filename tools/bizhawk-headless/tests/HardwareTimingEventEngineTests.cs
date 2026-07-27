using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class HardwareTimingEventEngineTests
    {
        private const string GoldenFingerprint =
            "sha256:11609213811e60294ea19488a1e3c6e87cd91f0af35480541091f5f7f478863b";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "HardwareTiming pending final module emits one LF event",
                PendingFinalModuleEmitsOneLfEvent));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming repeated zero emits a zero-byte stream",
                RepeatedZeroEmitsZeroByteStream));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming later lifecycle increments the ordinal",
                LaterLifecycleIncrementsOrdinal));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming unexported lifecycle preserves later ordinal",
                UnexportedLifecyclePreservesLaterOrdinal));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming FIFO head shift emits without a sampled zero",
                FifoHeadShiftEmitsWithoutSampledZero));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming fingerprint matches the Java golden vector",
                FingerprintMatchesJavaGoldenVector));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming descriptor refill precedes boundary command payload",
                DescriptorRefillPrecedesBoundaryCommandPayload));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming module alignment preserves the following header",
                ModuleAlignmentPreservesFollowingHeader));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming S3K ROM spans match the production decoder",
                S3kRomSpansMatchProductionDecoder,
                game: "s3k"));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming Lua and native scanners match at descriptor refill",
                LuaAndNativeScannersMatchAtDescriptorRefill));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming corrected identity has a new recorder version",
                CorrectedIdentityHasNewRecorderVersion));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming four-file publication is atomic and no-replace",
                FourFilePublicationIsAtomicAndNoReplace));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming standard Lua bounds a never-armed capture",
                StandardLuaBoundsNeverArmedCapture));
        }

        private static void PendingFinalModuleEmitsOneLfEvent()
        {
            const int source = 0x100;
            const int destination = 0xA400;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageActive(host, source, destination, 0x81);
            engine.ObservePostObjects(12, host, writer);
            StageEmpty(host);
            engine.ObservePostObjects(13, host, writer);
            engine.ObservePostObjects(14, host, writer);

            string fingerprint =
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_MODULE_QUEUE",
                    source,
                    7,
                    destination,
                    1,
                    "kosinski_moduled",
                    1);
            AssertEx.Equal(
                "{\"event\":\"hardware_work_completed\",\"raw_frame\":13,"
                + "\"boundary\":\"post_objects\",\"kind\":\"kos_module_queue\","
                + "\"ordinal\":0,\"submission_fingerprint\":\""
                + fingerprint + "\"}\n",
                writer.ToString());
        }

        private static void RepeatedZeroEmitsZeroByteStream()
        {
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(new byte[0x400]);

            StageEmpty(host);
            engine.ObservePostObjects(0, host, writer);
            engine.ObservePostObjects(1, host, writer);
            engine.ObservePostObjects(2, host, writer);

            AssertEx.Equal("", writer.ToString());
        }

        private static void LaterLifecycleIncrementsOrdinal()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageActive(host, source, 0x4000, 0x81);
            engine.ObservePostObjects(0, host, writer);
            StageEmpty(host);
            engine.ObservePostObjects(1, host, writer);
            StageActive(host, source, 0x4000, 0x81);
            engine.ObservePostObjects(2, host, writer);
            StageEmpty(host);
            engine.ObservePostObjects(3, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
        }

        private static void UnexportedLifecyclePreservesLaterOrdinal()
        {
            const int source = 0x100;
            byte[] rom = RomWithSingleModule(source);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageActive(host, source, 0x4000, 0x81);
            engine.ObservePostObjects(0, host, null);
            StageEmpty(host);
            engine.ObservePostObjects(1, host, null);
            StageActive(host, source, 0x6000, 0x81);
            engine.ObservePostObjects(2, host, writer);
            StageEmpty(host);
            engine.ObservePostObjects(3, host, writer);

            string output = writer.ToString();
            AssertEx.Equal(
                false, output.Contains("\"ordinal\":0"));
            AssertEx.Equal(
                true, output.Contains("\"ordinal\":1"));
        }

        private static void FifoHeadShiftEmitsWithoutSampledZero()
        {
            const int firstSource = 0x100;
            const int secondSource = 0x200;
            byte[] rom = RomWithSingleModules(firstSource, secondSource);
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);

            StageActive(host, firstSource, 0x4000, 0x81);
            StageQueued(host, 1, secondSource, 0x6000);
            engine.ObservePostObjects(20, host, writer);

            // Process_Kos_Module_Queue retires the first head, shifts the
            // second entry, and initializes it before returning. No frame-end
            // sample ever sees Kos_modules_left == 0.
            StageActive(host, secondSource, 0x6000, 0x01);
            engine.ObservePostObjects(21, host, writer);
            StageActive(host, secondSource, 0x6000, 0x81);
            engine.ObservePostObjects(22, host, writer);
            StageEmpty(host);
            engine.ObservePostObjects(23, host, writer);

            string[] lines = writer.ToString().Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            AssertEx.Equal(2, lines.Length);
            AssertEx.Equal(true, lines[0].Contains("\"raw_frame\":21"));
            AssertEx.Equal(true, lines[0].Contains("\"ordinal\":0"));
            AssertEx.Equal(true, lines[1].Contains("\"raw_frame\":23"));
            AssertEx.Equal(true, lines[1].Contains("\"ordinal\":1"));
        }

        private static void FingerprintMatchesJavaGoldenVector()
        {
            AssertEx.Equal(
                GoldenFingerprint,
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_MODULE_QUEUE",
                    0x12345678,
                    0x01020304,
                    0x0000ABCD,
                    0x11223344,
                    "KosM",
                    7));
        }

        private static void DescriptorRefillPrecedesBoundaryCommandPayload()
        {
            const int source = 0x100;
            const int destination = 0xA520;
            byte[] rom = RomWithDescriptorBoundaryModule(source, false);
            string output = CompleteSingleSubmission(
                rom, source, destination);

            // sonic3k.asm:2572-2585 refills d5 immediately when dbf consumes
            // descriptor bit 16, before the command reads its literal/match
            // payload. ResumableKosinskiDecoder therefore consumes 25 bytes
            // for this archive: two header bytes plus a 23-byte module.
            string expected =
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_MODULE_QUEUE",
                    source,
                    25,
                    destination,
                    16,
                    "kosinski_moduled",
                    1);
            AssertEx.Equal(
                true,
                output.Contains(
                    "\"submission_fingerprint\":\"" + expected + "\""));
        }

        private static void ModuleAlignmentPreservesFollowingHeader()
        {
            const int source = 0x100;
            const int destination = 0x2000;
            byte[] rom = RomWithDescriptorBoundaryModule(source, true);
            string output = CompleteSingleSubmission(
                rom, source, destination);

            // The first 23-byte module aligns to offset 32 relative to the
            // archive payload. The following five-byte module then ends at
            // archive byte 39; delayed descriptor refill instead mistakes
            // command data for a descriptor and crosses this module boundary.
            string expected =
                HardwareTimingEventEngine.ComputeSubmissionFingerprint(
                    "KOS_MODULE_QUEUE",
                    source,
                    39,
                    destination,
                    0x1001,
                    "kosinski_moduled",
                    2);
            AssertEx.Equal(
                true,
                output.Contains(
                    "\"submission_fingerprint\":\"" + expected + "\""));
        }

        private static void S3kRomSpansMatchProductionDecoder()
        {
            string romPath =
                Environment.GetEnvironmentVariable("S3K_ROM_PATH");
            if (string.IsNullOrEmpty(romPath))
            {
                throw new TestMain.SkipTestException(
                    "S3K_ROM_PATH is not set.");
            }
            if (!File.Exists(romPath))
            {
                throw new InvalidOperationException(
                    "Supplied S3K_ROM_PATH does not exist: " + romPath + ".");
            }
            byte[] rom = File.ReadAllBytes(romPath);
            using (SHA1 sha1 = SHA1.Create())
            {
                string actual = BitConverter.ToString(
                        sha1.ComputeHash(rom))
                    .Replace("-", "");
                AssertEx.Equal(
                    "CFBF98C36C776677290A872547AC47C53D2761D6",
                    actual);
            }

            // Literal spans are independently established by the production
            // ResumableKosinskiDecoder. The matching disassembly owners are
            // ArtKosM_AIZIntroPlane / ArtKosM_AIZIntroEmeralds and
            // ArtKosM_TitleCardRedAct.
            AssertSubmissionFingerprint(
                rom,
                0x382624,
                0xA520,
                "sha256:4423f6be47e039925c8575c68ed5eb22e9cba75f2aadd05f1d288d6c9579e723");
            AssertSubmissionFingerprint(
                rom,
                0x387CA6,
                0xB620,
                "sha256:34b575dc3ee07365ac9f621cf3d1f8afb74e90e851c6ed50d6b9e1d1c92f62c5");
            AssertSubmissionFingerprint(
                rom,
                0x0D6F28,
                0xA000,
                "sha256:10eb568a70724c579f022914f56227c2c7fa421aafa8578aebaa874f0cffb0ca");
        }

        private static void AssertSubmissionFingerprint(
            byte[] rom,
            int source,
            int destination,
            string expected)
        {
            string output = CompleteSingleSubmission(
                rom, source, destination);
            AssertEx.Equal(
                true,
                output.Contains(
                    "\"submission_fingerprint\":\"" + expected + "\""));
        }

        private static void LuaAndNativeScannersMatchAtDescriptorRefill()
        {
            const string lua = "/usr/bin/lua";
            if (!File.Exists(lua))
            {
                throw new TestMain.SkipTestException(
                    "/usr/bin/lua is not installed.");
            }

            const int source = 0x100;
            const int destination = 0xA520;
            byte[] rom = RomWithDescriptorBoundaryModule(source, false);
            string expected = CompleteSingleSubmission(
                rom, source, destination);
            string root = TestScratch.CreateRootPath(
                "hardware-timing-lua-vector");
            try
            {
                Directory.CreateDirectory(root);
                string romPath = Path.Combine(root, "vector.gen");
                string scriptPath = Path.Combine(root, "vector.lua");
                File.WriteAllBytes(romPath, rom);
                File.WriteAllText(
                    scriptPath,
                    LuaBehaviorVectorScript(),
                    new UTF8Encoding(false));

                var start = new ProcessStartInfo
                {
                    FileName = lua,
                    Arguments = Quote(scriptPath)
                        + " " + Quote(Path.Combine(
                            EndToEndTests.RepositoryRoot,
                            "tools", "bizhawk", "lib",
                            "oggf_hardware_timing.lua"))
                        + " " + Quote(romPath)
                        + " " + source
                        + " " + destination,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true
                };
                using (Process process = Process.Start(start))
                {
                    string actual = process.StandardOutput.ReadToEnd();
                    string error = process.StandardError.ReadToEnd();
                    process.WaitForExit();
                    if (process.ExitCode != 0)
                    {
                        throw new InvalidOperationException(
                            "Lua hardware timing vector failed: " + error);
                    }
                    AssertEx.Equal(expected, actual);
                }
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static void CorrectedIdentityHasNewRecorderVersion()
        {
            AssertEx.Equal(
                "6.35-s3k",
                S3KTraceMetadataWriter.LuaScriptVersion);
            AssertEx.Equal(
                "6.35-s3k-completerun",
                S3KCompleteRunMetadataWriter.LuaScriptVersion);

            string standard = File.ReadAllText(Path.Combine(
                EndToEndTests.RepositoryRoot,
                "tools", "bizhawk", "s3k_trace_recorder.lua"));
            string complete = File.ReadAllText(Path.Combine(
                EndToEndTests.RepositoryRoot,
                "tools", "bizhawk", "s3k_complete_run_recorder.lua"));
            AssertEx.Equal(
                true,
                standard.Contains(
                    "\"lua_script_version\": \"6.35-s3k\""));
            AssertEx.Equal(
                true,
                complete.Contains(
                    "LUA_SCRIPT_VERSION = \"6.35-s3k-completerun\""));
        }

        private static string LuaBehaviorVectorScript()
        {
            return
                "local module_path, rom_path = arg[1], arg[2]\n"
                + "local source, destination = tonumber(arg[3]), tonumber(arg[4])\n"
                + "local input = assert(io.open(rom_path, 'rb'))\n"
                + "local rom = input:read('*a')\n"
                + "input:close()\n"
                + "local ram = {}\n"
                + "memory = {read_u8=function(address, domain)\n"
                + "  assert(domain == 'MD CART')\n"
                + "  return assert(rom:byte(address + 1))\n"
                + "end}\n"
                + "mainmemory = {}\n"
                + "function mainmemory.read_u8(address) return ram[address] or 0 end\n"
                + "function mainmemory.read_u16_be(address)\n"
                + "  return (mainmemory.read_u8(address) << 8)\n"
                + "    | mainmemory.read_u8(address + 1)\n"
                + "end\n"
                + "function mainmemory.read_u32_be(address)\n"
                + "  return (mainmemory.read_u16_be(address) << 16)\n"
                + "    | mainmemory.read_u16_be(address + 2)\n"
                + "end\n"
                + "local function set16(address, value)\n"
                + "  ram[address] = (value >> 8) & 0xFF\n"
                + "  ram[address + 1] = value & 0xFF\n"
                + "end\n"
                + "local function set32(address, value)\n"
                + "  set16(address, (value >> 16) & 0xFFFF)\n"
                + "  set16(address + 2, value & 0xFFFF)\n"
                + "end\n"
                + "local timing = assert(loadfile(module_path))()\n"
                + "local tracker = timing.new_tracker()\n"
                + "local output = assert(io.tmpfile())\n"
                + "ram[0xFF60] = 0x81\n"
                + "set32(0xFF64, source + 2)\n"
                + "set16(0xFF68, destination)\n"
                + "timing.observe(tracker, 0, output)\n"
                + "for address = 0xFF64, 0xFF7B do ram[address] = 0 end\n"
                + "ram[0xFF60] = 0\n"
                + "timing.observe(tracker, 1, output)\n"
                + "output:seek('set', 0)\n"
                + "io.write(output:read('*a'))\n"
                + "output:close()\n";
        }

        private static string Quote(string value)
        {
            return "\"" + value.Replace("\\", "\\\\")
                .Replace("\"", "\\\"") + "\"";
        }

        private static string CompleteSingleSubmission(
            byte[] rom,
            int source,
            int destination)
        {
            var host = NewHost();
            var writer = new StringWriter();
            var engine = new HardwareTimingEventEngine(rom);
            StageActive(host, source, destination, 0x81);
            engine.ObservePostObjects(0, host, writer);
            StageEmpty(host);
            engine.ObservePostObjects(1, host, writer);
            return writer.ToString();
        }

        private static void FourFilePublicationIsAtomicAndNoReplace()
        {
            string root = TestScratch.CreateRootPath("hardware-timing-publish");
            string output = Path.Combine(root, "out");
            string[] names =
            {
                "physics.csv",
                "aux_state.jsonl",
                "hardware_timing.jsonl",
                "metadata.json"
            };
            try
            {
                var publisher = new NoReplacePublisher();
                NoReplacePublisher.StagedPublicationSet staged =
                    publisher.StageAll(
                        output,
                        names,
                        writers =>
                        {
                            writers[0].Write("physics\n");
                            writers[1].Write("");
                            writers[2].Write("timing\n");
                            writers[3].Write("{}\n");
                        });

                foreach (string name in names)
                {
                    AssertEx.Equal(
                        false, File.Exists(Path.Combine(output, name)));
                }
                staged.Publish();
                foreach (string name in names)
                {
                    AssertEx.Equal(
                        true, File.Exists(Path.Combine(output, name)));
                }

                using (NoReplacePublisher.StagedPublicationSet second =
                    publisher.StageAll(
                        output,
                        names,
                        writers => { }))
                {
                    AssertEx.Throws<IOException>(
                        () => second.Publish(),
                        "already exists");
                }
                AssertEx.Equal(
                    "timing\n",
                    File.ReadAllText(Path.Combine(
                        output, "hardware_timing.jsonl"),
                        Encoding.UTF8));
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static void StandardLuaBoundsNeverArmedCapture()
        {
            string source = File.ReadAllText(Path.Combine(
                EndToEndTests.RepositoryRoot,
                "tools", "bizhawk", "s3k_trace_recorder.lua"));
            int frameEnd = source.IndexOf(
                "function on_frame_end()", StringComparison.Ordinal);
            int captureMovieGuard = source.IndexOf(
                "Reached movie input end before/after arm",
                frameEnd,
                StringComparison.Ordinal);
            int startedGuard = source.IndexOf(
                "if HEADLESS and started then",
                frameEnd,
                StringComparison.Ordinal);
            int frameCap = source.IndexOf(
                "if not finished and emu.framecount() >= FRAME_CAP then",
                StringComparison.Ordinal);
            int noArmCleanup = source.IndexOf(
                "No trace segment armed; closing zero-byte timing stream.",
                StringComparison.Ordinal);
            int openEmpty = source.IndexOf(
                "open_empty_hardware_timing_file()",
                source.IndexOf(
                    "os.execute(\"mkdir", StringComparison.Ordinal),
                StringComparison.Ordinal);

            AssertEx.Equal(true, frameEnd >= 0);
            AssertEx.Equal(
                true,
                captureMovieGuard > frameEnd
                    && captureMovieGuard < startedGuard);
            AssertEx.Equal(true, frameCap > startedGuard);
            AssertEx.Equal(true, noArmCleanup > frameCap);
            AssertEx.Equal(true, openEmpty >= 0);
        }

        private static FakeS1Host NewHost()
        {
            return new FakeS1Host((h, frame) => { });
        }

        private static byte[] RomWithSingleModule(int source)
        {
            return RomWithSingleModules(source);
        }

        private static byte[] RomWithSingleModules(params int[] sources)
        {
            int length = 0x400;
            foreach (int source in sources)
            {
                length = Math.Max(length, source + 7);
            }
            var rom = new byte[length];
            foreach (int source in sources)
            {
                // One-byte KosM archive. Descriptor bits 0,1 select a full
                // match and the third zero byte terminates the module.
                rom[source] = 0x00;
                rom[source + 1] = 0x01;
                rom[source + 2] = 0x02;
                rom[source + 3] = 0x00;
                rom[source + 4] = 0x00;
                rom[source + 5] = 0x00;
                rom[source + 6] = 0x00;
            }
            return rom;
        }

        private static byte[] RomWithDescriptorBoundaryModule(
            int source,
            bool appendSecondModule)
        {
            int compressedLength = appendSecondModule ? 39 : 25;
            var rom = new byte[source + compressedLength];
            int destinationLength = appendSecondModule ? 0x1001 : 16;
            rom[source] = (byte)(destinationLength >> 8);
            rom[source + 1] = (byte)destinationLength;

            int position = source + 2;
            rom[position++] = 0xFF;
            rom[position++] = 0xFF;
            for (int literal = 0; literal < 15; literal++)
            {
                rom[position++] = (byte)(literal + 1);
            }
            // The next descriptor is fetched after bit 16 but before the
            // sixteenth literal. Its first two bits select a full match.
            rom[position++] = 0x02;
            rom[position++] = 0x00;
            rom[position++] = 0x10;
            rom[position++] = 0x00;
            rom[position++] = 0x00;
            rom[position++] = 0x00;

            if (appendSecondModule)
            {
                int relative = position - (source + 2);
                position += (16 - (relative & 15)) & 15;
                rom[position++] = 0x02;
                rom[position++] = 0x00;
                rom[position++] = 0x00;
                rom[position++] = 0x00;
                rom[position++] = 0x00;
            }
            AssertEx.Equal(source + compressedLength, position);
            return rom;
        }

        private static void StageActive(
            FakeS1Host host,
            int canonicalSource,
            int destination,
            int modulesLeft)
        {
            ClearQueue(host);
            host.Ram[S3KRam.KosModulesLeft] = (byte)modulesLeft;
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
            host.SetU32(
                S3KRam.KosModuleQueue,
                (uint)(canonicalSource + 2));
            host.SetU16(
                S3KRam.KosModuleDestination,
                (ushort)destination);
        }

        private static void StageQueued(
            FakeS1Host host,
            int index,
            int canonicalSource,
            int destination)
        {
            int entry = S3KRam.KosModuleQueue
                + (index * S3KRam.KosModuleQueueEntrySize);
            host.SetU32(entry, (uint)canonicalSource);
            host.SetU16(entry + 4, (ushort)destination);
        }

        private static void StageEmpty(FakeS1Host host)
        {
            ClearQueue(host);
            host.Ram[S3KRam.KosModulesLeft] = 0;
            host.SetU16(S3KRam.KosDecompQueueCount, 0);
        }

        private static void ClearQueue(FakeS1Host host)
        {
            for (int offset = 0;
                offset < S3KRam.KosModuleQueueEntrySize
                    * S3KRam.KosModuleQueueCapacity;
                offset++)
            {
                host.Ram[S3KRam.KosModuleQueue + offset] = 0;
            }
        }
    }
}
