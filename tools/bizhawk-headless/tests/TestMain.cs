using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class TestMain
    {
        internal sealed class TestCase
        {
            public TestCase(string name, Action body)
            {
                Name = name;
                Body = body;
            }

            public string Name { get; private set; }
            public Action Body { get; private set; }
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
            string filter = null;
            if (args.Length == 2 && args[0] == "--filter")
            {
                filter = args[1];
            }

            var tests = new List<TestCase>
            {
                new TestCase("Harness scaffold runs", () => { })
            };
            BootstrapTests.Register(tests);
            Bk2ReaderTests.Register(tests);
            GpgxHostTests.Register(tests);
            S1SmokeRecorderTests.Register(tests);
            S1TraceCsvWriterTests.Register(tests);
            S1TraceMetadataWriterTests.Register(tests);
            S1AuxEventEngineTests.Register(tests);
            S1TraceCaptureRunnerTests.Register(tests);
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
            S2RunManifestWriterTests.Register(tests);
            S2RunCaptureRunnerTests.Register(tests);
            S3KAuxEventEngineTests.Register(tests);
            S3KTraceCsvWriterTests.Register(tests);
            S3KTraceMetadataWriterTests.Register(tests);
            S3KTraceCaptureRunnerTests.Register(tests);
            S3KCompleteRunSegmenterTests.Register(tests);
            S3KCompleteRunProfileTests.Register(tests);
            S3KCompleteRunPublicationTests.Register(tests);
            S3KHookAbsenceTests.Register(tests);
            SmokeCaptureRunnerTests.Register(tests);
            NoReplacePublisherTests.Register(tests);
            TraceCliTests.Register(tests);
            EndToEndTests.Register(tests);
            S1TraceDifferentialTests.Register(tests);
            S1CompleteRunDifferentialTests.Register(tests);
            S1RunModeDifferentialTests.Register(tests);
            S2TraceDifferentialTests.Register(tests);
            S3KTraceDifferentialTests.Register(tests);
            S3KCompleteRunDifferentialTests.Register(tests);
            S3KCompleteRunSegmentsDifferentialTests.Register(tests);

            var matched = 0;
            var failed = 0;
            foreach (TestCase test in tests)
            {
                if (filter != null
                    && test.Name.IndexOf(filter, StringComparison.OrdinalIgnoreCase) < 0)
                {
                    continue;
                }

                matched++;
                try
                {
                    test.Body();
                    Console.WriteLine("PASS " + test.Name);
                }
                catch (SkipTestException exception)
                {
                    Console.WriteLine(
                        "SKIP " + test.Name + ": " + exception.Message);
                }
                catch (Exception exception)
                {
                    failed++;
                    Console.Error.WriteLine("FAIL " + test.Name + ": " + exception);
                }
            }

            if (matched == 0)
            {
                Console.Error.WriteLine("No tests matched filter: " + filter);
                return 2;
            }

            return failed == 0 ? 0 : 1;
        }
    }
}
