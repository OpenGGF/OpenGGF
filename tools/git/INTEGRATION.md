# Shared-checkout integration

`integrate.py` gives cooperative coordinators one machine-local owner for a
destination checkout during Git mutation. It never switches that checkout's
branch. Existing repository delivery rules and Git hooks still apply.
PR-based delivery does not need this command.

Finish implementation and long tests in the task worktree first. Use `deliver`
only for the final fetch, fast-forward, merge, affected verification, remote
recheck and ordinary push. Each prerequisite must succeed before the next runs.
The verification command is mandatory argv after `--`, executed without a shell
in the destination checkout. Keep it bounded and relevant to this integration.
Do not hold integration ownership across unrelated tests or Maven queue waits.
If final validation requires a long campaign, use the PR delivery flow instead.

```bash
python3 tools/git/integrate.py --checkout /path/to/destination status
python3 tools/git/integrate.py --checkout /path/to/destination deliver \
  --owner coordinator-a --task issue-256 --branch develop \
  --expected-head <full-destination-commit> --source <full-task-commit> \
  -- python3 -m unittest discover -s tools/git -p test_integrate.py
```

Run only with the user's authority for that destination. In particular, a
`master` push can publish a release. `--source` and `--expected-head` require
full commit IDs. The source must already exist in the destination's object
database. `--remote` defaults to `origin` and must name a configured Git remote.
Delivery refuses a dirty index/worktree, untracked files, dirty or changed
submodules, an active merge/rebase/cherry-pick/revert/bisect, or a mismatched
branch/HEAD. It never
stashes, resets, aborts, cleans, force-pushes or retries a failed mutation.
Refused prerequisites leave the prior ownership record untouched. A checkout
with preserved dirty reference submodules or user files needs the existing
manual coordination or PR delivery flow; this tool does not clean them.

The command fetches the exact remote branch and fast-forwards with `merge
--ff-only`, equivalent to the fast-forward part of pull. Local commits already
ahead of the remote are allowed. Divergence stops before the task merge. A
second fetch must report the same remote commit before push. Advancement stops
for normal reconciliation and fresh verification. A later server-side race is
left to ordinary push rejection. A failed or uncertain push remains failed;
inspect remote state before deciding whether to retry.

## Recovery and handoff

`status` reports `active`, the latest ownership record, and the live `state_id`.
It does not claim ownership. Read a stable snapshot when `active` is false.
Status briefly probes the OS lock; another probe can report it active. Retry
after the probe returns rather than treating `active` as a durable claim.
The record contains owner/task, branch, initial expected HEAD, observed HEAD,
`MERGE_HEAD`, mutation phase and a random ID. The state ID covers branch, HEAD,
operation presence, tracked diffs, staged entries and untracked paths. It does
not hash untracked file contents or nested submodule worktrees.

After failure, cancellation or supervisor death, the next command must name
the current record ID. Same-owner `deliver --record <id>` is allowed only when
the recorded snapshot still matches and the checkout is clean. Otherwise inspect
the files and use explicit recovery. Each accepted operation rotates the record
ID. Old IDs cannot resume or hand off a newer operation.

```bash
python3 tools/git/integrate.py --checkout /path/to/destination recover \
  --owner coordinator-a --task issue-256 --branch develop \
  --expected-head <observed-head> --record <current-record-id> \
  --expected-state <current-state-id> -- git add resolved-file
```

`recover` holds the same checkout lease while running exactly one explicit
repair command. It requires the exact current HEAD/state and recorded owner/task.
It never starts delivery or pushes afterward. Inspect status again for the new
record ID before another repair, commit or delivery. Commit preparation and
policy remain the existing hook owners' responsibility. Keep repair commands
bounded and preserve the destination branch. Arbitrary commands are cooperative
authority, not a sandbox; do not use a shell sequence to bypass fail-fast delivery.

An outgoing coordinator hands off an idle checkout explicitly.

```bash
python3 tools/git/integrate.py --checkout /path/to/destination handoff \
  --owner coordinator-a --task issue-256 --branch develop \
  --expected-head <observed-head> --record <current-record-id> \
  --expected-state <current-state-id> \
  --to-owner coordinator-b --to-task issue-256
```

Handoff changes only the ownership record. It preserves staged bytes, conflicts,
`MERGE_HEAD` and all user files. The receiving owner uses the new record ID with
`recover` or a clean `deliver`; the old identity and ID are rejected. If a
coordinator died, its successor must inspect the stopped operation and explicitly
name the recorded outgoing identity in this handoff. A dead PID or stale timestamp
alone never transfers ownership. An active lease cannot be handed off or stolen.

## Boundary and checks

The OS `flock` is authoritative, not the JSON record or a PID. Lock files live
in the destination's absolute Git directory, including for linked worktrees.
Independent task worktrees do not share this lease. No Maven locks or admission
settings change. Never delete a lock file to release ownership; that creates
another inode and permits simultaneous owners.

POSIX local filesystems are supported. Windows is refused; network filesystem
lock semantics and multiple machines are outside this contract. This is
cooperative protection. Direct Git commands, hooks or editors that ignore the
protocol can still mutate files. Snapshot checks detect ordinary drift but do
not make uncooperative mutation safe.

SIGINT/SIGTERM stop and reap the owned command group before closing the lease.
Git/check/repair children inherit the lock descriptor. If the supervisor dies
abruptly, a surviving child retains exclusion until it exits. Commands must
remain foreground and must not close inherited descriptors or daemonize. No
automatic cleanup of Git state occurs after cancellation.

Run the disposable-repository tests with the following command. They do not
invoke integration against real user branches or run Maven.

```bash
python3 -m unittest discover -s tools/git -p test_integrate.py -v
```
