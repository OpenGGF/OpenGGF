---
name: soniccd-packet-integration
description: Use when drafting manifests from externally pinned FINAL Sonic CD sources, integrating frozen source packets or attributing fresh focused JUnit reports in an explicitly selected Git worktree. Provides read-only drafting, packet validation, optional import and exact report summaries; does not run gameplay, probes or tests.
---

# Sonic CD packet integration

Use `tools/soniccd/integration_packet.py` to replace repeated ad hoc packet and
Surefire inspection snippets. It uses the Python standard library and the existing
worktree build/test lock. Run it from the checkout containing this tool, selecting
the destination worktree explicitly; it does not stage, commit, fetch, push, launch
Maven, inspect foreign jobs or execute any supplied source.

Keep the full USA then JP/EU integration goal in the owning task. A validated
source packet is an integration aid, not evidence that a region or feature works.

## Freeze and pin the packet

Accept a worker's FINAL source-only handoff with absolute external frozen mirror
paths, SHA256, byte lengths and explicit canonical destinations. Keep Root-owned
R2 preserved originals and source evidence intact outside the destination tree;
the importer does not create backups. Do not treat an author report marked UNRUN
as execution evidence or reconstruct expected outputs by executing gameplay,
helpers or probes. Independently check the fixed research/source pin named by the
owning task against its retained provenance; this tool verifies file bytes and
the destination worktree HEAD, not the truth of a source-model/oracle claim.

### Draft from an explicit external mapping

Use the read-only `draft` subcommand to avoid handwriting a packet manifest.
Supply an external JSON spec with exactly `version` and nonempty `files`; every
entry has exactly `source`, `sha256`, `bytes` and `destination`. Source pins must
come from the externally supplied FINAL handoff. Do not manufacture those pins
by measuring unknown input. Destinations explicitly name reviewed canonical
relative POSIX paths; a mirror name or Java package is never path authority.
This illustrative spec uses dummy pins:

```json
{
  "version": 1,
  "files": [
    {
      "source": "/external/frozen-worker/ReviewMirror.java",
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
      "bytes": 123,
      "destination": "src/test/java/example/TestCanonicalOwner.java"
    }
  ]
}
```

```bash
python3 -B tools/soniccd/integration_packet.py draft \
  --worktree "$DESTINATION_WORKTREE" --spec "$EXTERNAL_MAPPING_JSON"
```

Draft verifies the supplied source pins and frozen-text contract below, using the
same packet path, alias and source-budget checks. The spec must be canonical,
external, regular, non-symlink UTF-8 JSON, at most 16 MiB; unknown/duplicate keys
are rejected. It observes the actual destination HEAD and current destination
bytes, including uncommitted changes, or absence. New files require existing
parents. It emits only the existing version-1 manifest to stdout, with source
pins retained, `mode=0444`, `utf8=true`, `lf=true`, and observed destination
`expected` pins. It never infers destinations, approves source claims or dirty
baselines, writes files, acquires a lease, queries jobs or executes supplied code.
Only the existing fixed read-only Git root/HEAD calls run.

Before emitting, draft rechecks the spec, sources, destination content/metadata,
destination directories and HEAD; observed changes reject the draft. These are
**non-atomic observations**, not a filesystem snapshot: changes between or after
checks, including a HEAD change and restoration between reads, can escape them.
Frozen mode does not establish provenance or prevent owner writes; reads may
update atimes. Review the complete draft and independently establish FINAL source
custody and acceptable current baselines before saving a manifest outside the
repository and using normal read-only `packet`, then the authorized apply flow.
Draft does not weaken or bypass either validation. Exit `0` emits a draft; `2`
rejects it with diagnostics on stderr and no manifest stdout. There is no output,
apply, Maven or scheduler option.

### Review the normal packet manifest

Supply a version-1 manifest outside the repository with actual invocation paths,
externally supplied FINAL source pins and reviewed observed destination baselines.
This illustrative JSON uses dummy pins:

```json
{
  "version": 1,
  "worktree_head": "0000000000000000000000000000000000000000",
  "files": [
    {
      "source": "/external/frozen-worker/ReviewMirror.java",
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
      "bytes": 123,
      "mode": "0444",
      "utf8": true,
      "lf": true,
      "destination": "src/test/java/example/TestCanonicalOwner.java",
      "expected": {
        "sha256": "1111111111111111111111111111111111111111111111111111111111111111"
      }
    }
  ]
}
```

`worktree_head` is the exact destination worktree commit; destination SHA256 pins
cover its actual existing bytes, including reviewed uncommitted inputs. For a new
file use `"expected": {"absent": true}` instead. No unknown fields or duplicate
JSON keys are accepted. The deliberately odd source mirror name has no package
authority: `destination` must explicitly name the reviewed canonical source path.
The tool does not infer a package move, rename a class or transform source bytes.

```bash
python3 -B tools/soniccd/integration_packet.py packet \
  --worktree "$DESTINATION_WORKTREE" --manifest "$FINAL_PACKET_JSON"
```

Inspect the entire plan: current versus expected baseline, incoming hash/length/
mode and exact destination for every file. All sources must be external regular
files with exact mode `0444`, strict UTF-8, no CR/NUL, and a final LF unless empty.
Destinations are relative POSIX paths with existing parent directories. The tool
rejects symlink paths, traversal, Git metadata paths, duplicate destinations,
source/destination and destination/destination hardlink aliases, changed pins and
files over 16 MiB or packets over 64 MiB. Create/review new directories separately.
Frozen mode is a preservation convention, not a guarantee against owner writes.

## Import only when the worktree is ready

Before apply, the caller must ensure **all task-owned queued and running Maven
requests for this worktree are terminal** and stop other writers to these files.
The build/test lock excludes cooperating active builds/tests; it does **not**
prove that a queued request is terminal. Do not inspect/control foreign jobs or
delete any lock file to make progress.

```bash
python3 -B tools/soniccd/integration_packet.py packet \
  --worktree "$DESTINATION_WORKTREE" --manifest "$FINAL_PACKET_JSON" \
  --apply --maven-terminal
```

The declaration is caller supplied, not an automatic scheduler check. Apply
preflights the whole packet, acquires the existing worktree build/test lock, then
preflights everything again after any wait. It replaces each destination
atomically with identical bytes, mode `0644` and a fresh mtime; frozen source
content, mode and mtime remain unchanged. Sources may acquire normal read atimes.
Apply needs POSIX directory descriptors and `O_NOFOLLOW`; read-only planning
remains available elsewhere. Parents are opened without following symlinks.

The packet is **not** one multi-file transaction. On an I/O or detected concurrent
change, the tool stops, lists completed replacements and remaining files, attempts
to remove its own temporary file, and reports a cleanup failure with its exact
owned path. It does not roll back completed or unrelated data. Keep
cooperating writers excluded: the build lock cannot make a hash-check/rename pair
an atomic compare-and-swap against an arbitrary external editor. Reconcile actual
files and intentionally repin a remaining packet before retrying; never relax a
baseline merely to get apply accepted. Exit codes: `0` validated/applied, `2`
rejected or failed before any confirmed replacement, `3` partial import.

## Attribute focused test evidence

Follow the owning task's test-first flow: observe the named new regression RED
against the pinned original, import production source, observe its positive run,
then complete the bounded affected consumer/guard checks. Choose these from the
actual change. This skill adds no Maven invocation, approval ceremony, engine
suite or repeated unchanged review. Use repository queue/selection policy when
the owning task authorizes execution.

Once the task-owned command is terminal, inspect only its worktree's fresh
`target/surefire-reports`. Supply the actual run-start UTC timestamp and the
complete exact class set for that invocation, plus expected media/EGL properties:

```bash
python3 -B tools/soniccd/integration_packet.py junit \
  --worktree "$DESTINATION_WORKTREE" --since "$RUN_START_UTC" \
  --class example.TestCanonicalOwner --class example.TestAffectedConsumer \
  --property "soniccd.media.root=$MEDIA_ROOT" \
  --property openggf.headless.gl=surfaceless-egl
```

This reads flat `TEST-*.xml` files only and requires an exact fresh class set,
matching suite/testcase ownership, unique actual identities, and consistent
declared/observed counts. Unrelated reports older than the invocation cutoff
are counted and ignored without parsing or deleting them. A requested class's
stale report still fails, and any unexpected fresh class fails. The tool never
accepts a foreign qualified testcase owner. Exact simple testcase class names
(as emitted by ArchUnit) are attributed to the full suite owner while their
reported name is retained separately in the output. The tool never
deletes reports. It rejects stale requested, missing, unexpected fresh, empty
and malformed evidence (reports also have a 16 MiB
per-file read limit). It reports every observed test
identity and exact failure/error/skip message. Missing `java.version`, missing or
wrong `user.dir`, and absent/mismatched requested properties are flagged. Read the
actual Java version when the task requires Java 25. Only the fixed media/EGL/JVM
allowlist is exposed; arbitrary JVM properties and credentials are not printed.

Exit `0` means the specified XML observations have no failures/errors/skips or
environment issues; `1` reports those negative observations; `2` rejects the
evidence. XML freshness does not prove process completion, source identity or
test discovery. Attribute the result to the exact command, tested SHA and relevant
uncommitted source, invocation cutoff, classes, environment, identities and skips.
`head_at_inspection` is not a claimed execution SHA. Never combine older narrow
passes into an invented fresh total. No output is retained unless the caller
explicitly redirects text; do not create pass caches, receipts or raw-log archives.
