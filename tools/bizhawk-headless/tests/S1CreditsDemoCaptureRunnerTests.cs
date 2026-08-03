using System;
using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S1CreditsDemoCaptureRunnerTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1 credits catalog exposes the eight ROM ending demos",
                CreditsCatalogExposesEightRomDemos));
            tests.Add(new TestMain.TestCase(
                "S1 credits converts ROM controller input without Start",
                ConvertsRomControllerInput));
            tests.Add(new TestMain.TestCase(
                "S1 credits redirect writes only the verified setup state",
                RedirectWritesOnlyVerifiedSetupState));
            tests.Add(new TestMain.TestCase(
                "S1 credits collection rolls staged output back on duplicate",
                CollectionRollsBackOnDuplicate));
            tests.Add(new TestMain.TestCase(
                "S1 credits title redirect timeout includes lifecycle state",
                TitleRedirectTimeoutIncludesLifecycleState));
        }

        private static void ConvertsRomControllerInput()
        {
            AssertEx.Equal(0x0F, S1InputMask.FromRomControllerByte(0x8F));
            AssertEx.Equal(0x10, S1InputMask.FromRomControllerByte(0x70));
            AssertEx.Equal(0x1A, S1InputMask.FromRomControllerByte(0x9A));
        }

        private static void RedirectWritesOnlyVerifiedSetupState()
        {
            var writer = new RecordingWriter();
            S1CreditsDemoCaptureRunner.RedirectToCredits(writer);
            AssertEx.Equal("F600,FFF0,FFF1,FFF4,FFF5", writer.Keys());
            AssertEx.Equal((byte)0x1C, writer.Get(S1Ram.GameMode));
            AssertEx.Equal((byte)0, writer.Get(S1Ram.DemoFlag));
            AssertEx.Equal((byte)0, writer.Get(S1Ram.CreditsNum));
        }

        private static void CollectionRollsBackOnDuplicate()
        {
            string root = TestScratch.CreateRootPath("credits-rollback");
            NoReplacePublisher.IncrementalStagingSession session = null;
            try
            {
                var publisher = new NoReplacePublisher(
                    new TracePayloadCompressor(0));
                session = publisher.OpenSession(root);
                using (var sink = new S1CreditsDemoCollectionSink(session))
                {
                    S1CreditsDemoDefinition demo = S1CreditsDemoCatalog.Get(0);
                    TextWriter aux;
                    TextWriter physics = sink.Begin(demo, out aux);
                    physics.Write("frame\n0000\n");
                    aux.Write("{}\n");
                    sink.Complete("{}\n");
                    AssertEx.Throws<InvalidOperationException>(
                        () => sink.Begin(demo, out aux), "captured twice");
                }
                session.Dispose();
                session = null;
                AssertEx.Equal(false, File.Exists(Path.Combine(root,
                    "00_ghz1_credits_demo_1", "physics.csv.gz")));
            }
            finally
            {
                if (session != null) session.Dispose();
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void TitleRedirectTimeoutIncludesLifecycleState()
        {
            var host = new FakeS1Host(null);
            host.Ram[S1Ram.GameMode] = 0x04;
            AssertEx.Throws<InvalidOperationException>(
                () => S1CreditsDemoCaptureRunner.ThrowIfPreRedirectTimedOut(
                    host, 2401),
                "timed out waiting to redirect title to credits: mode=0x04");
        }

        private sealed class RecordingWriter : IMainRamWriter
        {
            private readonly SortedDictionary<int, byte> writes =
                new SortedDictionary<int, byte>();

            public void WriteMainRamByte(int offset, byte value)
            {
                writes.Add(offset, value);
            }

            public byte Get(int offset) { return writes[offset]; }

            public string Keys()
            {
                var keys = new List<string>();
                foreach (int key in writes.Keys) keys.Add(key.ToString("X4"));
                return string.Join(",", keys.ToArray());
            }
        }

        private static void CreditsCatalogExposesEightRomDemos()
        {
            Type catalog = typeof(S1InputMask).Assembly.GetType(
                "OpenGGF.BizHawk.Headless.S1CreditsDemoCatalog");
            AssertEx.Equal(true, catalog != null);
            var all = (Array)catalog.GetMethod("All").Invoke(null, null);
            AssertEx.Equal(8, all.Length);
            object lz3 = all.GetValue(3);
            AssertEx.Equal("lz3_credits_demo", (string)lz3.GetType()
                .GetProperty("Slug").GetValue(lz3, null));
            AssertEx.Equal(0x0102, (int)lz3.GetType()
                .GetProperty("ZoneActWord").GetValue(lz3, null));
            AssertEx.Equal(510, (int)lz3.GetType()
                .GetProperty("TimerFrames").GetValue(lz3, null));
        }
    }
}
