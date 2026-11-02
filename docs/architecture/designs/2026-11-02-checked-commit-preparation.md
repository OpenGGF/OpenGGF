# Checked commit preparation

## Problem and ownership

Issue #230 requires a failed preparation check to prevent the dependent Git
commit invocation. Separate shell commands did not establish that dependency.
`.githooks/run-policy` owns platform dispatch. Its `commit-msg` mode owns staged
content and the seven policy lines, reading the whole message independently of
Git's final trailer parser. Normal hooks remain the final Git lifecycle owners.

## Usage and shape

The caller stages reviewed content, writes the exact message to a file, and runs:

```bash
python3 tools/testing/checked_commit.py --message-file /tmp/message.txt \
  --co-author 'OpenAI Codex GPT-6.1 <noreply@openai.com>' -- reviewed-file.txt
```

The data shape is prepared message bytes, an ordered list of intended coauthors,
and a set of literal repository-relative staged paths. The narrow function
`checked_commit(message: bytes, paths: list[str], coauthors: list[str]) -> None`
owns checked subprocess sequencing. Failures raise before the commit call.
`main() -> int` parses arguments and reports a nonzero exit status.

Boundary Discipline keeps guards at the CLI and subprocess boundary. Model the
Domain uses a path set and ordered author list rather than shell word splitting.
Policy remains in the existing dispatcher. The helper adds no policy-key list,
validator plugin interface, or arbitrary Git arguments.

## Synthesis decision

Two bounded candidates were compared with a read-only design helper. Configured
routes were from the same model family, so this was not cross-family consensus.

A shell wrapper with a newline-delimited scope manifest would hide subprocess
ordering but expose filename encoding and quoting to callers. Python with literal
path arguments hides those details and snapshots message bytes in a temporary
file. Python was selected because it compares NUL-delimited Git names without
shell splitting. Both candidates delegate policy instead of duplicating it.

The chosen interface exposes only the information the caller must attest to.
It checks the separate final parsed author block, invokes the existing validator,
rechecks message and repository state, then calls ordinary Git commit. The final
message uses verbatim cleanup so Git does not strip prepared bytes by default.

## Verification and limits

Test Behavior, Not Implementation drives disposable repositories through the CLI.
Git tracing distinguishes no commit invocation from a hook rejecting an invoked
commit. Controls include deliberately failed validation, valid separated policy
and author blocks, missing policy lines, incorrect guide mapping, scope mismatch,
renames, literal spaces and wildcards, message or index drift, and hook rejection.
Existing policy rejects newline filenames; that rejection is preserved.

The helper supports ordinary commits on existing non-master branches. It does not
provide atomic isolation from concurrent Git writers or prevent normal hooks
from changing messages or staged content during Git's lifecycle. Those would
require a different ownership contract. No engine, Maven selection, or runtime
behavior changes, so focused Git-process controls replace broad engine smoke.
