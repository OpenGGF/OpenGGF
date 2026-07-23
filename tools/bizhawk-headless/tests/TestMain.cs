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
            SmokeCaptureRunnerTests.Register(tests);
            NoReplacePublisherTests.Register(tests);
            TraceCliTests.Register(tests);
            EndToEndTests.Register(tests);

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
