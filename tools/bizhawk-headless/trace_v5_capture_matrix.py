#!/usr/bin/env python3
"""CLI entry point for the reviewed trace-v5 scratch capture matrix.

The implementation lives in ``tools.traces`` so the pure preflight and
assembler functions can be unit-tested without invoking BizHawk.  This entry
point is intentionally under the native harness directory next to ``run.sh``
and does not launch a capture itself.
"""

from __future__ import annotations

import sys
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from tools.traces.trace_v5_capture_matrix import main


if __name__ == "__main__":
    raise SystemExit(main())
