using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// What kind of test a case is. <see cref="Gate"/> means a ROM-backed
    /// differential gate: it replays a real movie through the harness and
    /// compares the capture against a committed fixture, so it costs
    /// minutes and hundreds of megabytes rather than milliseconds.
    /// Everything else is <see cref="Unit"/> and stays the default, which
    /// is why the ~330 registrations that need no metadata compile
    /// untouched.
    /// </summary>
    internal enum TestKind
    {
        Unit,
        Gate
    }

    internal static class TestMain
    {
        /// <summary>
        /// One registered test, plus optional scheduling and selection
        /// metadata. Every metadata parameter is optional: a registration
        /// that passes only a name and a body behaves exactly as it did
        /// before the metadata existed.
        /// </summary>
        internal sealed class TestCase
        {
            /// <summary>
            /// Ordering hint for an untagged unit test. Unit tests are
            /// milliseconds; the value only has to sort them below the
            /// gates.
            /// </summary>
            private const double DefaultUnitSeconds = 0.05;

            /// <summary>
            /// Ordering hint for a gate that did not declare one and has
            /// no recorded timing yet. Deliberately large so an unknown
            /// gate is scheduled early rather than becoming a straggler.
            /// </summary>
            private const double DefaultGateSeconds = 120.0;

            public TestCase(
                string name,
                Action body,
                string game = null,
                string movie = null,
                TestKind kind = TestKind.Unit,
                bool serial = false,
                double estimatedSeconds = 0.0)
            {
                Name = name;
                Body = body;
                Game = game;
                Movie = movie;
                Kind = kind;
                Serial = serial;
                EstimatedSeconds = estimatedSeconds > 0.0
                    ? estimatedSeconds
                    : (kind == TestKind.Gate
                        ? DefaultGateSeconds
                        : DefaultUnitSeconds);
            }

            public string Name { get; private set; }

            public Action Body { get; private set; }

            /// <summary>
            /// "s1", "s2" or "s3k" for a test bound to one game's ROM;
            /// null when the test is game-agnostic. Null is excluded by
            /// <c>--game</c> and ignored by every other selector.
            /// </summary>
            public string Game { get; private set; }

            /// <summary>
            /// The BK2 movie basename a gate replays, without extension;
            /// null when the test replays no movie. Null is excluded by
            /// <c>--movie</c> and ignored by every other selector.
            /// </summary>
            public string Movie { get; private set; }

            public TestKind Kind { get; private set; }

            /// <summary>
            /// True when the test mutates process-global state — the
            /// environment block, or the in-process emulator core — and
            /// therefore cannot run beside anything else. Serial tests
            /// run alone, before the parallel phase.
            /// </summary>
            public bool Serial { get; private set; }

            /// <summary>
            /// Scheduling hint only: never an assertion, never a timeout.
            /// A recorded timing from <c>--update-timings</c> overrides
            /// it when one exists.
            /// </summary>
            public double EstimatedSeconds { get; private set; }

            /// <summary>
            /// A copy of this case that must run alone. Used to mark a
            /// whole class serial at registration, so the property cannot
            /// be forgotten on a case added to that class later.
            /// </summary>
            internal TestCase AsSerial()
            {
                return new TestCase(
                    Name,
                    Body,
                    Game,
                    Movie,
                    Kind,
                    true,
                    EstimatedSeconds);
            }
        }

        internal sealed class SkipTestException : Exception
        {
            public SkipTestException(string message)
                : base(message)
            {
            }
        }

        private static int Main(string[] args)
        {
            TestOptions options;
            string error;
            if (!TestOptions.TryParse(args, out options, out error))
            {
                Console.Error.WriteLine(error);
                Console.Error.WriteLine(TestOptions.Usage);
                return TestOptions.UsageExitCode;
            }

            if (options.ShowHelp)
            {
                Console.WriteLine(TestOptions.Usage);
                return 0;
            }

            return TestRunner.Run(BuildRegistry(), options);
        }

        /// <summary>
        /// Builds the flat registry. Registration order is the order a
        /// <c>--jobs 1</c> run executes in, and remains the tie-break for
        /// the parallel scheduler.
        /// </summary>
        internal static List<TestCase> BuildRegistry()
        {
            var tests = new List<TestCase>
            {
                new TestCase("Harness scaffold runs", () => { })
            };
            BootstrapTests.Register(tests);
            Bk2ReaderTests.Register(tests);
            DynamicArtRomProfileTests.Register(tests);
            DynamicArtTransferStateTests.Register(tests);
            S1DynamicArtObserverTests.Register(tests);
            S2DynamicArtObserverTests.Register(tests);
            GpgxHostTests.Register(tests);
            GpgxAudioObserverSourceLockTests.Register(tests);
            GpgxAudioObserverBuildTests.Register(tests);
            RegisterSerial(tests, GpgxAudioTraceNativeTests.Register);
            RegisterSerial(tests, CompleteRunAudioObserverTests.Register);
            RegisterSerial(tests, S1CompleteRunAudioReferenceCaptureTests.Register);
            RegisterSerial(tests, GpgxZ80AudioCapabilityTests.Register);
            RegisterSerial(tests, S2AudioObserverProfileTests.Register);
            S1SmokeRecorderTests.Register(tests);
            S1TraceCsvWriterTests.Register(tests);
            S1TraceMetadataWriterTests.Register(tests);
            S1AuxEventEngineTests.Register(tests);
            LoadQueueStateEventTests.Register(tests);
            S1TraceCaptureRunnerTests.Register(tests);
            S1CreditsDemoCaptureRunnerTests.Register(tests);
            // Pre-capture selection is deliberately method-level: candidate
            // comparison belongs to the Task 7 Python comparator and must not
            // become a skip when Task 9 candidate roots do not exist yet.
            S1CreditsDemoDifferentialTests.RegisterPreCapture(tests);
            S1CompleteRunMetadataWriterTests.Register(tests);
            S1RunCaptureRunnerStageFreeTests.Register(tests);
            S1SpecialStageWriterTests.Register(tests);
            S1RunManifestWriterTests.Register(tests);
            S1RunCaptureRunnerTests.Register(tests);
            S2TraceCsvWriterTests.Register(tests);
            S2AuxEventEngineTests.Register(tests);
            S2AuxArmBlockTests.Register(tests);
            S2TraceMetadataWriterTests.Register(tests);
            S2TraceCaptureRunnerTests.Register(tests);
            S2SpecialStageWriterTests.Register(tests);
            S2SpecialStageCaptureRunnerTests.Register(tests);
            S2RunManifestWriterTests.Register(tests);
            S2RunCaptureRunnerTests.Register(tests);
            S3KAuxEventEngineTests.Register(tests);
            HardwareTimingEventEngineTests.Register(tests);
            S3KTraceCsvWriterTests.Register(tests);
            S3KTraceMetadataWriterTests.Register(tests);
            S3KTraceCaptureRunnerTests.Register(tests);
            S3KCompleteRunSegmenterTests.Register(tests);
            S3KCompleteRunProfileTests.Register(tests);
            S3KHookAbsenceTests.Register(tests);
            SmokeCaptureRunnerTests.Register(tests);
            NoReplacePublisherTests.Register(tests);
            TracePayloadCompressorTests.Register(tests);
            // These two classes drive the CLI in-process, and the CLI
            // wraps its capture in NativeStandardOutputSilencer, which
            // dup2()s /dev/null onto file descriptors 1 and 2 for the
            // whole process. Anything another thread writes in that
            // window is not merely interleaved, it is destroyed — which
            // is how this was found: a parallel run reported 34 of 352
            // tests and still exited 0. Marking the classes rather than
            // the cases means a case added to either later inherits the
            // constraint instead of silently eating the suite's output.
            RegisterSerial(tests, TraceCliTests.Register);
            RegisterSerial(tests, EndToEndTests.Register);
            return tests;
        }

        /// <summary>
        /// Registers a whole class as serial, preserving registration
        /// order and every other tag the class set on its cases.
        /// </summary>
        private static void RegisterSerial(
            List<TestCase> tests,
            Action<ICollection<TestCase>> register)
        {
            var collected = new List<TestCase>();
            register(collected);
            foreach (TestCase test in collected)
            {
                tests.Add(test.AsSerial());
            }
        }
    }
}
