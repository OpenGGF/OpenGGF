"""Disposable-repository acceptance controls for issue #230."""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[2]
HELPER = SOURCE / "tools/testing/checked_commit.py"
AUTHOR = "OpenAI Codex GPT-6.1 <noreply@openai.com>"
MESSAGE = ("chore: exercise checked commit\n\n"
           "Changelog: n/a\nGuide: n/a\nKnown-Discrepancies: n/a\n"
           "S3K-Known-Discrepancies: n/a\nAgent-Docs: n/a\n"
           "Configuration-Docs: n/a\nSkills: n/a\n\n"
           f"Co-authored-by: {AUTHOR}\n")


class CheckedCommitTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = os.environ.copy()
        for key in list(self.env):
            if key.startswith("GIT_"):
                del self.env[key]
        self.env.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
        self.git("init", "-b", "feature/ai-test")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.com")
        (self.root / "base.txt").write_text("base\n")
        self.git("add", "base.txt")
        self.git("commit", "-m", "initial")
        self.before = self.git("rev-parse", "HEAD")
        shutil.copytree(SOURCE / ".githooks", self.root / ".githooks")
        self.git("config", "core.hooksPath", ".githooks")
        self.message = self.root / "message.txt"
        self.message.write_text(MESSAGE)
        self.trace = self.root / "trace"
        self.env["GIT_TRACE"] = str(self.trace)
        self.stage("change.txt")

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.root, env=self.env,
                              check=True, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE).stdout

    def stage(self, path):
        (self.root / path).write_text("new\n")
        self.git("add", "--", path)

    def run_helper(self, *paths, author=AUTHOR):
        authors = [author] if isinstance(author, str) else author
        command = [sys.executable, str(HELPER), "--message-file", str(self.message)]
        for value in authors:
            command.extend(["--co-author", value])
        return subprocess.run([*command, "--", *paths], cwd=self.root,
                              env=self.env, capture_output=True)

    def assert_not_invoked(self, result):
        self.assertNotEqual(0, result.returncode, result.stderr.decode())
        self.assertEqual(self.before, self.git("rev-parse", "HEAD"))
        self.assertNotIn("built-in: git commit ", self.trace.read_text())

    def test_failing_validator_never_invokes_commit(self):
        (self.root / ".githooks/run-policy").write_text("#!/bin/sh\nexit 19\n")
        self.assert_not_invoked(self.run_helper("change.txt"))

    def test_valid_separate_policy_and_final_coauthor_commits_exact_message(self):
        result = self.run_helper("change.txt")
        self.assertEqual(0, result.returncode, result.stderr.decode())
        self.assertIn("built-in: git commit ", self.trace.read_text())
        self.assertEqual(MESSAGE, self.git("show", "-s", "--format=%B", "HEAD").decode().rstrip("\n") + "\n")
        self.assertEqual(b"change.txt\n", self.git("diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"))

    def test_policy_failure_prevents_commit(self):
        self.message.write_text(MESSAGE.replace("Guide: n/a\n", ""))
        self.assert_not_invoked(self.run_helper("change.txt"))

    def test_wrong_policy_mapping_prevents_commit(self):
        (self.root / "docs/guide").mkdir(parents=True)
        self.stage("docs/guide/new.md")
        self.assert_not_invoked(self.run_helper("change.txt", "docs/guide/new.md"))

    def test_expected_scope_must_match_exactly(self):
        for paths in [("missing.txt",), ("change.txt", "extra"),
                      ("change.txt", "change.txt"), ("*.txt",)]:
            with self.subTest(paths=paths):
                self.assert_not_invoked(self.run_helper(*paths))

    def test_deletion_and_rename_paths_are_included(self):
        self.git("mv", "base.txt", "renamed.txt")
        self.assert_not_invoked(self.run_helper("change.txt", "renamed.txt"))
        result = self.run_helper("change.txt", "base.txt", "renamed.txt")
        self.assertEqual(0, result.returncode, result.stderr.decode())

    def test_literal_unusual_paths(self):
        path = "literal * space.txt"
        self.stage(path)
        result = self.run_helper("change.txt", path)
        self.assertEqual(0, result.returncode, result.stderr.decode())

    def test_existing_policy_rejection_of_newline_path_is_preserved(self):
        path = "line\nbreak.txt"
        self.stage(path)
        self.assert_not_invoked(self.run_helper("change.txt", path))

    def test_final_coauthor_must_parse_and_match(self):
        for message in [MESSAGE + "after block\n", MESSAGE.replace("\n\nCo-authored", "\nCo-authored"),
                        MESSAGE.replace(AUTHOR, "Other <other@example.com>")]:
            with self.subTest(message=message):
                self.message.write_text(message)
                self.assert_not_invoked(self.run_helper("change.txt"))

    def test_multiple_intended_coauthors(self):
        second = "Another Model 1.0 <other@example.com>"
        self.assert_not_invoked(self.run_helper("change.txt", author=[AUTHOR, second]))
        self.message.write_text(MESSAGE + f"Co-authored-by: {second}\n")
        result = self.run_helper("change.txt", author=[AUTHOR, second])
        self.assertEqual(0, result.returncode, result.stderr.decode())
        self.assertEqual(MESSAGE + f"Co-authored-by: {second}\n",
                         self.git("show", "-s", "--format=%B", "HEAD").decode().rstrip("\n") + "\n")

    def test_validator_changing_branch_prevents_commit(self):
        self.git("branch", "feature/ai-other")
        (self.root / ".githooks/run-policy").write_text(
            '#!/bin/sh\ngit symbolic-ref HEAD refs/heads/feature/ai-other\n')
        self.assert_not_invoked(self.run_helper("change.txt"))

    def test_validator_changing_index_prevents_commit(self):
        (self.root / ".githooks/run-policy").write_text(
            '#!/bin/sh\nprintf changed > change.txt\ngit add change.txt\n')
        self.assert_not_invoked(self.run_helper("change.txt"))

    def test_validator_changing_message_prevents_commit(self):
        (self.root / ".githooks/run-policy").write_text(
            '#!/bin/sh\nprintf changed > "$2"\n')
        self.assert_not_invoked(self.run_helper("change.txt"))

    def test_ordinary_hook_can_reject_commit(self):
        hook = self.root / ".githooks/pre-commit"
        hook.write_text('#!/bin/sh\nprintf ran > hook-ran\nexit 23\n')
        hook.chmod(0o755)
        result = self.run_helper("change.txt")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("built-in: git commit ", self.trace.read_text())
        self.assertTrue((self.root / "hook-ran").exists())
        self.assertEqual(self.before, self.git("rev-parse", "HEAD"))


if __name__ == "__main__":
    unittest.main()
