#!/usr/bin/env python3
"""Atomic no-replace publication for frozen trace evidence files."""

from __future__ import annotations

import os
import tempfile
from pathlib import Path


def write_bytes_no_replace(path: Path, content: bytes, label: str) -> None:
    parent = path.parent
    if not parent.is_dir():
        raise ValueError(f"{label} parent directory does not exist: {parent}")
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
                mode="wb", dir=parent, prefix=f".{path.name}.", delete=False) as output:
            temporary = Path(output.name)
            output.write(content)
            output.flush()
            os.fsync(output.fileno())
        try:
            os.link(temporary, path)
        except FileExistsError as error:
            raise ValueError(f"{label} already exists: {path}") from error
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
