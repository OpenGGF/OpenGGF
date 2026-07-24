using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class NoReplacePublisherTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "Publisher creates missing nested directory",
                CreatesMissingNestedDirectory));
            tests.Add(new TestMain.TestCase(
                "Publisher removes temporary output after writer failure",
                RemovesTemporaryAfterWriterFailure));
            tests.Add(new TestMain.TestCase(
                "Publisher preserves a pre-existing final file",
                PreservesPreExistingFinalFile));
            tests.Add(new TestMain.TestCase(
                "Publisher loses deterministic final-link race without replacement",
                LosesDeterministicFinalLinkRace));
            tests.Add(new TestMain.TestCase(
                "Publisher leaves one UTF-8 LF final file",
                LeavesOneUtf8LfFinalFile));
            tests.Add(new TestMain.TestCase(
                "Publisher ignores temporary cleanup failure after final link",
                IgnoresTemporaryCleanupFailureAfterFinalLink));
            tests.Add(new TestMain.TestCase(
                "Publisher set stages and publishes three UTF-8 LF finals",
                SetStagesAndPublishesThreeFinals));
            tests.Add(new TestMain.TestCase(
                "Publisher set removes all temporaries after writer failure",
                SetRemovesAllTemporariesAfterWriterFailure));
            tests.Add(new TestMain.TestCase(
                "Publisher set dispose without publish removes temporaries",
                SetDisposeWithoutPublishRemovesTemporaries));
            tests.Add(new TestMain.TestCase(
                "Publisher set refuses to publish over an existing final",
                SetRefusesToPublishOverExistingFinal));
            tests.Add(new TestMain.TestCase(
                "Publisher set rolls back published finals on later link race",
                SetRollsBackPublishedFinalsOnLaterLinkRace));
        }

        private static readonly string[] TraceFileNames =
        {
            "physics.csv",
            "aux_state.jsonl",
            "metadata.json"
        };

        private static NoReplacePublisher.StagedPublicationSet StageTraceSet(
            NoReplacePublisher publisher,
            string output)
        {
            return publisher.StageAll(
                output,
                TraceFileNames,
                writers =>
                {
                    writers[0].WriteLine("physics");
                    writers[1].WriteLine("aux");
                    writers[2].WriteLine("metadata");
                });
        }

        private static void SetStagesAndPublishesThreeFinals()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "set", "nested");
                    NoReplacePublisher.StagedPublicationSet staged =
                        StageTraceSet(new NoReplacePublisher(), output);

                    // Nothing is committed while capture output is staged.
                    foreach (string name in TraceFileNames)
                    {
                        AssertEx.Equal(
                            false,
                            File.Exists(Path.Combine(output, name)));
                    }

                    staged.Publish();

                    string[] entries = Directory.GetFileSystemEntries(output)
                        .Select(Path.GetFileName)
                        .OrderBy(name => name, StringComparer.Ordinal)
                        .ToArray();
                    AssertEx.Equal(
                        "aux_state.jsonl,metadata.json,physics.csv",
                        string.Join(",", entries));
                    string[] expectedContents =
                    {
                        "physics\n",
                        "aux\n",
                        "metadata\n"
                    };
                    for (var index = 0; index < TraceFileNames.Length; index++)
                    {
                        byte[] bytes = File.ReadAllBytes(
                            Path.Combine(output, TraceFileNames[index]));
                        AssertBytesEqual(
                            Encoding.UTF8.GetBytes(expectedContents[index]),
                            bytes);
                        AssertEx.Equal(false, HasUtf8Bom(bytes));
                    }
                    AssertEx.Throws<InvalidOperationException>(
                        () => staged.Publish(),
                        "already finalized");
                });
        }

        private static void SetRemovesAllTemporariesAfterWriterFailure()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "failed-set");
                    AssertEx.Throws<InvalidOperationException>(
                        () => new NoReplacePublisher().StageAll(
                            output,
                            TraceFileNames,
                            writers =>
                            {
                                writers[0].WriteLine("partial physics");
                                writers[2].WriteLine("partial metadata");
                                throw new InvalidOperationException(
                                    "intentional writer failure");
                            }),
                        "intentional writer failure");

                    AssertEx.Equal(true, Directory.Exists(output));
                    AssertEx.Equal(
                        0,
                        Directory.GetFileSystemEntries(output).Length);
                });
        }

        private static void SetDisposeWithoutPublishRemovesTemporaries()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "abandoned-set");
                    NoReplacePublisher.StagedPublicationSet staged =
                        StageTraceSet(new NoReplacePublisher(), output);
                    AssertEx.Equal(
                        3,
                        Directory.GetFileSystemEntries(output).Length);

                    staged.Dispose();

                    AssertEx.Equal(
                        0,
                        Directory.GetFileSystemEntries(output).Length);
                });
        }

        private static void SetRefusesToPublishOverExistingFinal()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "existing-set");
                    string existingPath = Path.Combine(
                        output,
                        "aux_state.jsonl");
                    byte[] original = { 0x00, 0x7F, 0x80, 0xFF };
                    Directory.CreateDirectory(output);
                    File.WriteAllBytes(existingPath, original);
                    NoReplacePublisher.StagedPublicationSet staged =
                        StageTraceSet(new NoReplacePublisher(), output);

                    AssertEx.Throws<IOException>(
                        () => staged.Publish(),
                        "already exists");

                    // No partial finals: the pre-existing file is untouched
                    // and it is the only entry left in the directory.
                    string[] entries =
                        Directory.GetFileSystemEntries(output);
                    AssertEx.Equal(1, entries.Length);
                    AssertEx.Equal(
                        "aux_state.jsonl",
                        Path.GetFileName(entries[0]));
                    AssertBytesEqual(
                        original,
                        File.ReadAllBytes(existingPath));
                });
        }

        private static void SetRollsBackPublishedFinalsOnLaterLinkRace()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "race-set");
                    byte[] competing = Encoding.UTF8.GetBytes(
                        "competing-writer\n");
                    var link = new TargetedCompetingLinkOperation(
                        "metadata.json",
                        competing);
                    NoReplacePublisher.StagedPublicationSet staged =
                        StageTraceSet(new NoReplacePublisher(link), output);

                    AssertEx.Throws<IOException>(
                        () => staged.Publish(),
                        "already exists");

                    AssertEx.Equal(3, link.CreateCount);
                    string[] entries =
                        Directory.GetFileSystemEntries(output);
                    AssertEx.Equal(1, entries.Length);
                    AssertEx.Equal(
                        "metadata.json",
                        Path.GetFileName(entries[0]));
                    AssertBytesEqual(
                        competing,
                        File.ReadAllBytes(entries[0]));
                });
        }

        private static void CreatesMissingNestedDirectory()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "missing", "nested");
                    new NoReplacePublisher().Publish(
                        output,
                        writer => writer.WriteLine("created"));

                    AssertEx.Equal(true, Directory.Exists(output));
                    AssertEx.Equal(
                        "created\n",
                        File.ReadAllText(Path.Combine(output, "smoke.csv")));
                });
        }

        private static void RemovesTemporaryAfterWriterFailure()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "failed");
                    AssertEx.Throws<InvalidOperationException>(
                        () => new NoReplacePublisher().Publish(
                            output,
                            writer =>
                            {
                                writer.WriteLine("partial");
                                throw new InvalidOperationException(
                                    "intentional writer failure");
                            }),
                        "intentional writer failure");

                    AssertEx.Equal(true, Directory.Exists(output));
                    AssertEx.Equal(
                        false,
                        File.Exists(Path.Combine(output, "smoke.csv")));
                    AssertEx.Equal(0, Directory.GetFileSystemEntries(output).Length);
                });
        }

        private static void PreservesPreExistingFinalFile()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "existing");
                    string finalPath = Path.Combine(output, "smoke.csv");
                    byte[] original = { 0x00, 0x7F, 0x80, 0xFF };
                    Directory.CreateDirectory(output);
                    File.WriteAllBytes(finalPath, original);

                    AssertEx.Throws<IOException>(
                        () => new NoReplacePublisher().Publish(
                            output,
                            writer => writer.WriteLine("replacement")),
                        "already exists");

                    AssertBytesEqual(original, File.ReadAllBytes(finalPath));
                    AssertOnlyFinalFile(output);
                });
        }

        private static void LosesDeterministicFinalLinkRace()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "race");
                    string finalPath = Path.Combine(output, "smoke.csv");
                    byte[] competing = Encoding.UTF8.GetBytes(
                        "competing-writer\n");
                    var link = new CompetingLinkOperation(competing);

                    AssertEx.Throws<IOException>(
                        () => new NoReplacePublisher(link).Publish(
                            output,
                            writer => writer.WriteLine("losing-writer")),
                        "already exists");

                    AssertEx.Equal(1, link.CreateCount);
                    AssertEx.Equal(
                        Path.GetFullPath(finalPath),
                        link.FinalPath);
                    AssertBytesEqual(competing, File.ReadAllBytes(finalPath));
                    AssertOnlyFinalFile(output);
                });
        }

        private static void LeavesOneUtf8LfFinalFile()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "success");
                    new NoReplacePublisher().Publish(
                        output,
                        writer =>
                        {
                            writer.WriteLine("frame,input");
                            writer.WriteLine("0000,0000");
                        });

                    AssertOnlyFinalFile(output);
                    byte[] bytes = File.ReadAllBytes(
                        Path.Combine(output, "smoke.csv"));
                    AssertBytesEqual(
                        Encoding.UTF8.GetBytes(
                            "frame,input\n0000,0000\n"),
                        bytes);
                    AssertEx.Equal(false, HasUtf8Bom(bytes));
                    AssertEx.Equal(false, bytes.Contains((byte)'\r'));
                });
        }

        private static void IgnoresTemporaryCleanupFailureAfterFinalLink()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "cleanup-failure");
                    var deleteCalls = 0;
                    var publisher = new NoReplacePublisher(
                        LibcLinkOperation.Instance,
                        path =>
                        {
                            deleteCalls++;
                            throw new IOException(
                                "intentional temporary cleanup failure");
                        });

                    publisher.Publish(
                        output,
                        writer => writer.WriteLine("committed"));

                    AssertEx.Equal(1, deleteCalls);
                    AssertEx.Equal(
                        "committed\n",
                        File.ReadAllText(Path.Combine(output, "smoke.csv")));
                });
        }

        private static void AssertOnlyFinalFile(string output)
        {
            string[] entries = Directory.GetFileSystemEntries(output);
            AssertEx.Equal(1, entries.Length);
            AssertEx.Equal(
                "smoke.csv",
                Path.GetFileName(entries[0]));
        }

        private static void AssertBytesEqual(
            byte[] expected,
            byte[] actual)
        {
            AssertEx.Equal(
                BitConverter.ToString(expected),
                BitConverter.ToString(actual));
        }

        private static bool HasUtf8Bom(byte[] bytes)
        {
            return bytes.Length >= 3
                && bytes[0] == 0xEF
                && bytes[1] == 0xBB
                && bytes[2] == 0xBF;
        }

        private static void WithTemporaryDirectory(Action<string> body)
        {
            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-publisher-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(root);
            try
            {
                body(root);
            }
            finally
            {
                Directory.Delete(root, true);
            }
        }

        private sealed class TargetedCompetingLinkOperation : ILinkOperation
        {
            private readonly string targetFileName;
            private readonly byte[] competing;

            public TargetedCompetingLinkOperation(
                string targetFileName,
                byte[] competing)
            {
                this.targetFileName = targetFileName;
                this.competing = competing;
            }

            public int CreateCount { get; private set; }

            public void Create(string temporary, string finalPath)
            {
                CreateCount++;
                if (Path.GetFileName(finalPath) == targetFileName)
                {
                    File.WriteAllBytes(finalPath, competing);
                }
                LibcLinkOperation.Instance.Create(temporary, finalPath);
            }
        }

        private sealed class CompetingLinkOperation : ILinkOperation
        {
            private readonly byte[] competing;

            public CompetingLinkOperation(byte[] competing)
            {
                this.competing = competing;
            }

            public int CreateCount { get; private set; }
            public string FinalPath { get; private set; }

            public void Create(string temporary, string finalPath)
            {
                CreateCount++;
                FinalPath = finalPath;
                File.WriteAllBytes(finalPath, competing);
                LibcLinkOperation.Instance.Create(temporary, finalPath);
            }
        }
    }
}
