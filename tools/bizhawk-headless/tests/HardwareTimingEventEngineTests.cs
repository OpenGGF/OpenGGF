using System;
using System.Collections.Generic;
using System.IO;
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
                "HardwareTiming FIFO head shift emits without a sampled zero",
                FifoHeadShiftEmitsWithoutSampledZero));
            tests.Add(new TestMain.TestCase(
                "HardwareTiming fingerprint matches the Java golden vector",
                FingerprintMatchesJavaGoldenVector));
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
