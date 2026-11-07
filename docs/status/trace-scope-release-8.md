# Trace scope for release 8

The 0.8 roadmap inherits the comparison and evidence rules from
[release 7](trace-scope-release-7.md). Historical failures are not reset by
branch promotion, and old measurements are not claimed as new passing results.

- Preserve passing traces and compare known failures by test identity, owning
  report, first divergence, and failure details against a fixed baseline.
- Keep fixture integrity, timing authority, and structural guards enabled.
- Record newly verified frontiers in [the frontier log](trace-frontier-log.md).
- The 0.7 campaign milestones carried to 0.8 (complete S3K character routes and
  the four full-run trace chains) are tracked by the
  [0.8 roadmap](../project/v0.8-roadmap.md). Branch integration alone does not
  prove campaign completion or hardware/audio parity.
