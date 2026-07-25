using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Threading;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Executes the selected tests, sequentially or across a worker pool.
    ///
    /// Three properties are contractual and every change here has to hold
    /// them:
    ///
    /// 1. <c>--jobs 1</c> produces exactly what the pre-parallel runner
    ///    produced: registration order, unbuffered writes, no report.
    /// 2. A parallel run produces the same per-test outcomes as a serial
    ///    one. Tests that mutate process-global state are tagged
    ///    <c>serial: true</c> and run alone, before the parallel phase,
    ///    because they cannot be made concurrency-safe without weakening
    ///    what they assert.
    /// 3. The <c>PASS </c>/<c>FAIL </c>/<c>SKIP </c> line shapes and their
    ///    streams never change, whatever the job count.
    /// </summary>
    internal static class TestRunner
    {
        private enum Status
        {
            Pass,
            Fail,
            Skip
        }

        private sealed class TestResult
        {
            internal TestResult(string name, Status status, double seconds)
            {
                Name = name;
                Status = status;
                Seconds = seconds;
            }

            internal string Name { get; private set; }

            internal Status Status { get; private set; }

            internal double Seconds { get; private set; }
        }

        internal static int Run(
            List<TestMain.TestCase> tests,
            TestOptions options)
        {
            var selected = new List<TestMain.TestCase>();
            foreach (TestMain.TestCase test in tests)
            {
                if (options.Matches(test))
                {
                    selected.Add(test);
                }
            }

            if (selected.Count == 0)
            {
                Console.Error.WriteLine(
                    "No tests matched filter: " + options.DescribeSelection());
                return 2;
            }

            string timingsPath = options.TimingsPath ?? TestTimings.DefaultPath;
            Dictionary<string, double> recorded = TestTimings.Load(timingsPath);

            var results = new List<TestResult>();
            Stopwatch wall = Stopwatch.StartNew();
            if (options.Jobs <= 1)
            {
                RunSequential(selected, results);
            }
            else
            {
                RunParallel(selected, options, recorded, results);
            }

            wall.Stop();

            var failed = 0;
            var skipped = 0;
            foreach (TestResult result in results)
            {
                if (result.Status == Status.Fail)
                {
                    failed++;
                }
                else if (result.Status == Status.Skip)
                {
                    skipped++;
                }
            }

            // Every selected test must have produced a result. Without
            // this, a worker lost to an unexpected error leaves a run
            // that prints fewer results and still exits 0 — which is
            // exactly what a full /tmp produced once here, and it looked
            // like a clean pass.
            bool incomplete = results.Count != selected.Count;
            if (incomplete)
            {
                Console.Error.WriteLine(
                    "RUNNER ERROR: "
                    + selected.Count.ToString(CultureInfo.InvariantCulture)
                    + " tests selected but only "
                    + results.Count.ToString(CultureInfo.InvariantCulture)
                    + " results were recorded. The run is incomplete.");
            }

            if (options.ResolvedSlowest > 0)
            {
                ReportTimings(
                    results,
                    results.Count - failed - skipped,
                    failed,
                    skipped,
                    wall.Elapsed.TotalSeconds,
                    options);
            }

            if (options.UpdateTimings)
            {
                SaveTimings(timingsPath, recorded, results);
            }

            return failed == 0 && !incomplete ? 0 : 1;
        }

        /// <summary>
        /// The pre-parallel path, kept intact as the debugging route: one
        /// test at a time, in registration order, writing straight to the
        /// console with no buffering in between.
        /// </summary>
        private static void RunSequential(
            List<TestMain.TestCase> selected,
            List<TestResult> results)
        {
            foreach (TestMain.TestCase test in selected)
            {
                Stopwatch elapsed = Stopwatch.StartNew();
                try
                {
                    test.Body();
                    elapsed.Stop();
                    Console.WriteLine("PASS " + test.Name);
                    results.Add(new TestResult(
                        test.Name, Status.Pass, elapsed.Elapsed.TotalSeconds));
                }
                catch (TestMain.SkipTestException exception)
                {
                    elapsed.Stop();
                    Console.WriteLine(
                        "SKIP " + test.Name + ": " + exception.Message);
                    results.Add(new TestResult(
                        test.Name, Status.Skip, elapsed.Elapsed.TotalSeconds));
                }
                catch (Exception exception)
                {
                    elapsed.Stop();
                    Console.Error.WriteLine(
                        "FAIL " + test.Name + ": " + exception);
                    results.Add(new TestResult(
                        test.Name, Status.Fail, elapsed.Elapsed.TotalSeconds));
                }
            }
        }

        /// <summary>
        /// Serial-tagged tests first and alone, then everything else
        /// across the pool, longest-first.
        /// </summary>
        private static void RunParallel(
            List<TestMain.TestCase> selected,
            TestOptions options,
            Dictionary<string, double> recorded,
            List<TestResult> results)
        {
            var serial = new List<TestMain.TestCase>();
            var parallel = new List<TestMain.TestCase>();
            foreach (TestMain.TestCase test in selected)
            {
                if (test.Serial)
                {
                    serial.Add(test);
                }
                else
                {
                    parallel.Add(test);
                }
            }

            TextWriter realOut = Console.Out;
            TextWriter realError = Console.Error;
            var consoleLock = new object();
            var resultsLock = new object();
            Console.SetOut(new TestConsoleRouter(realOut, false));
            Console.SetError(new TestConsoleRouter(realError, true));
            try
            {
                foreach (TestMain.TestCase test in serial)
                {
                    Execute(
                        test,
                        realOut,
                        realError,
                        consoleLock,
                        results,
                        resultsLock);
                }

                var scheduler = new Scheduler(
                    OrderLongestFirst(parallel, recorded));
                int workerCount = Math.Min(options.Jobs, parallel.Count);
                var workers = new Thread[Math.Max(workerCount, 0)];
                for (var i = 0; i < workers.Length; i++)
                {
                    workers[i] = new Thread(delegate()
                    {
                        while (true)
                        {
                            TestMain.TestCase test = scheduler.Take();
                            if (test == null)
                            {
                                return;
                            }

                            try
                            {
                                Execute(
                                    test,
                                    realOut,
                                    realError,
                                    consoleLock,
                                    results,
                                    resultsLock);
                            }
                            catch (Exception unexpected)
                            {
                                // Not a test failure — a failure of the
                                // runner around it, such as the console
                                // stream itself erroring. Left uncaught it
                                // kills this worker, and the run then
                                // reports only the tests that happened to
                                // finish. Record it as a failure so the
                                // exit code and the completeness check
                                // both see it.
                                RecordRunnerFailure(
                                    test,
                                    unexpected,
                                    realError,
                                    consoleLock,
                                    results,
                                    resultsLock);
                            }
                        }
                    });
                    workers[i].IsBackground = false;
                    workers[i].Start();
                }

                foreach (Thread worker in workers)
                {
                    worker.Join();
                }
            }
            finally
            {
                Console.SetOut(realOut);
                Console.SetError(realError);
            }
        }

        /// <summary>
        /// Slowest first, so the critical path starts at frame zero of the
        /// run rather than becoming a straggler behind a queue of unit
        /// tests. A recorded timing wins over the static estimate; ties
        /// keep registration order.
        /// </summary>
        private static List<TestMain.TestCase> OrderLongestFirst(
            List<TestMain.TestCase> tests,
            Dictionary<string, double> recorded)
        {
            var order = new int[tests.Count];
            var weights = new double[tests.Count];
            for (var i = 0; i < tests.Count; i++)
            {
                order[i] = i;
                double seconds;
                weights[i] = recorded.TryGetValue(tests[i].Name, out seconds)
                    ? seconds
                    : tests[i].EstimatedSeconds;
            }

            Array.Sort(order, delegate(int left, int right)
            {
                int byWeight = weights[right].CompareTo(weights[left]);
                return byWeight != 0 ? byWeight : left.CompareTo(right);
            });

            var ordered = new List<TestMain.TestCase>(tests.Count);
            foreach (int index in order)
            {
                ordered.Add(tests[index]);
            }

            return ordered;
        }

        /// <summary>
        /// Runs one test with its console output captured, then emits the
        /// captured output and the result line as a single locked block.
        /// The buffered output precedes the result line, which is the
        /// order a sequential run produces.
        /// </summary>
        private static void Execute(
            TestMain.TestCase test,
            TextWriter realOut,
            TextWriter realError,
            object consoleLock,
            List<TestResult> results,
            object resultsLock)
        {
            var outputBuffer = new StringWriter(CultureInfo.InvariantCulture);
            var errorBuffer = new StringWriter(CultureInfo.InvariantCulture);
            Status status;
            string detail = null;
            Stopwatch elapsed = Stopwatch.StartNew();
            TestConsoleRouter.Begin(outputBuffer, errorBuffer);
            try
            {
                test.Body();
                status = Status.Pass;
            }
            catch (TestMain.SkipTestException exception)
            {
                status = Status.Skip;
                detail = exception.Message;
            }
            catch (Exception exception)
            {
                status = Status.Fail;
                detail = exception.ToString();
            }
            finally
            {
                TestConsoleRouter.End();
                elapsed.Stop();
            }

            string bufferedOutput = outputBuffer.ToString();
            string bufferedError = errorBuffer.ToString();
            lock (consoleLock)
            {
                if (bufferedOutput.Length > 0)
                {
                    realOut.Write(bufferedOutput);
                }

                if (bufferedError.Length > 0)
                {
                    realError.Write(bufferedError);
                }

                if (status == Status.Pass)
                {
                    realOut.WriteLine("PASS " + test.Name);
                }
                else if (status == Status.Skip)
                {
                    realOut.WriteLine("SKIP " + test.Name + ": " + detail);
                }
                else
                {
                    realError.WriteLine("FAIL " + test.Name + ": " + detail);
                }

                realOut.Flush();
                realError.Flush();
            }

            lock (resultsLock)
            {
                results.Add(new TestResult(
                    test.Name, status, elapsed.Elapsed.TotalSeconds));
            }
        }

        /// <summary>
        /// Records a runner-level failure for a test whose execution
        /// wrapper itself threw. Best effort on the console — the stream
        /// is the most likely thing to have broken — but the result is
        /// always recorded, so the exit code cannot come out green.
        /// </summary>
        private static void RecordRunnerFailure(
            TestMain.TestCase test,
            Exception unexpected,
            TextWriter realError,
            object consoleLock,
            List<TestResult> results,
            object resultsLock)
        {
            try
            {
                lock (consoleLock)
                {
                    realError.WriteLine(
                        "FAIL " + test.Name + ": " + unexpected);
                    realError.Flush();
                }
            }
            catch (Exception)
            {
                // Deliberately swallowed: the result below is what the
                // exit code and the completeness check read.
            }

            lock (resultsLock)
            {
                results.Add(new TestResult(test.Name, Status.Fail, 0.0));
            }
        }

        /// <summary>
        /// Slowest-N plus a one-line summary. Every line starts with two
        /// spaces or three dashes, so nothing here can be mistaken for a
        /// result line by a script grepping the stream.
        /// </summary>
        private static void ReportTimings(
            List<TestResult> results,
            int passed,
            int failed,
            int skipped,
            double wallSeconds,
            TestOptions options)
        {
            var ordered = new List<TestResult>(results);
            ordered.Sort(delegate(TestResult left, TestResult right)
            {
                int byDuration = right.Seconds.CompareTo(left.Seconds);
                return byDuration != 0
                    ? byDuration
                    : string.CompareOrdinal(left.Name, right.Name);
            });

            int count = Math.Min(options.ResolvedSlowest, ordered.Count);
            Console.WriteLine();
            Console.WriteLine(
                "--- slowest " + count.ToString(CultureInfo.InvariantCulture)
                + " of " + ordered.Count.ToString(CultureInfo.InvariantCulture)
                + " tests ---");
            for (var i = 0; i < count; i++)
            {
                TestResult result = ordered[i];
                Console.WriteLine(string.Format(
                    CultureInfo.InvariantCulture,
                    "  {0,9:F2}s  {1}  {2}",
                    result.Seconds,
                    result.Status.ToString().ToUpperInvariant(),
                    result.Name));
            }

            Console.WriteLine(string.Format(
                CultureInfo.InvariantCulture,
                "--- {0} passed, {1} failed, {2} skipped in {3:F1}s"
                + " (jobs={4}) ---",
                passed,
                failed,
                skipped,
                wallSeconds,
                options.Jobs));
        }

        private static void SaveTimings(
            string path,
            Dictionary<string, double> recorded,
            List<TestResult> results)
        {
            var measured = new Dictionary<string, double>(
                StringComparer.Ordinal);
            foreach (TestResult result in results)
            {
                // Only passing runs are representative: a skip measures a
                // missing ROM, and a failure measures how fast the
                // assertion tripped.
                if (result.Status == Status.Pass)
                {
                    measured[result.Name] = result.Seconds;
                }
            }

            TestTimings.Save(path, recorded, measured);
        }

        /// <summary>
        /// Hands out the next test, in the order it was queued.
        ///
        /// The queue arrives sorted longest-first, which is the whole of
        /// the scheduling policy: the suite's floor is set by its single
        /// longest gate, so that gate has to start first. Nothing here
        /// throttles on a resource. A capture is a flat ~231 MB whatever
        /// the movie length, so memory does not bind, and the box has
        /// far more cores than the suite has concurrent work.
        /// </summary>
        private sealed class Scheduler
        {
            private readonly List<TestMain.TestCase> pending;
            private readonly object gate = new object();
            private int next;

            internal Scheduler(List<TestMain.TestCase> pending)
            {
                this.pending = pending;
            }

            /// <summary>
            /// The next queued test, or null once the queue is drained.
            /// </summary>
            internal TestMain.TestCase Take()
            {
                lock (gate)
                {
                    if (next >= pending.Count)
                    {
                        return null;
                    }

                    return pending[next++];
                }
            }
        }
    }
}
