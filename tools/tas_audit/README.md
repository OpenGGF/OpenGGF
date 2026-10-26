# TAS evidence report tools

Preserved from the 2026-10-26 TAS audit and verdict work (delivery base
`c69447d77`). These analysis tools consume the original author-note audit and
reviewed, executed per-game verdict records. They do not execute tests or infer
unsupported behavior from missing evidence.

```bash
python3 tools/tas_audit/normalize_verdicts.py --source-dir "$TASK_DIR" \
  --repository "$REPOSITORY" --validation "$TASK_DIR/validation.json"
python3 tools/tas_audit/render_verdict_report.py --audit "$AUDIT_DIR/audit.json" \
  --reviewed "$TASK_DIR/reviewed-verdicts.json" --output-dir "$TASK_DIR/report"
```

The normalizer requires all 82 original family IDs and rejects interim records.
The renderer preserves the publication census and per-scenario publication IDs,
requires binary verdicts and evidence, and keeps the single undefined S1 source
note separate. Its output is a self-contained HTML page and machine-readable
JSON. Counts are scenario assessments, not unique tests. Full-movie playback
remains unverified. Inputs and generated reports live outside the repository.
See the [validation record](../../docs/architecture/validation/2026-10-26-tas-trick-verdicts.md).

The separate supportability report groups reviewed repair estimates while keeping
every executed unsupported verdict and publication attribution unchanged:

```bash
python3 tools/tas_audit/render_supportability_report.py \
  --verdicts "$TASK_DIR/report/verdict-report.json" \
  --assessment "$REEL_DIR/full/s1/supportability.json" \
  --assessment "$REEL_DIR/full/s2/supportability.json" \
  --assessment "$REEL_DIR/full/s3k/supportability.json" \
  --out "$REEL_DIR/full/repair-report"
```

Missing/duplicate scenario IDs, changed publication attribution and incomplete
repair evidence fail reconciliation. Shared groups use the highest affected-owner
complexity and retain individual estimates. JSON/Markdown/HTML describe prospective
repairs, acceptance checks and source owners, never implemented support. See the
[repair assessment](../../docs/architecture/validation/2026-10-26-tas-supportability.md).
