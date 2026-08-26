# Test session tooling

## Frozen-next baseline adapter

Frozen commit `84d9a3761` predates the session-output Maven properties. Its
baseline evidence therefore uses `frozen-next-session-launch.sh` and
`frozen-next-session-adapter.sh` with the pinned detached develop harness.
They are historical baseline-only tooling, never a production launcher. The
adapter creates a validated ignored `target` symlink into the coordinator
session and removes only that exact link during recovery. Adapter Maven
arguments must never include `clean`, because frozen next's clean plugin can
replace the routed symlink with a worktree-local directory.
