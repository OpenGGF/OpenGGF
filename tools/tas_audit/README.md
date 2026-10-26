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
