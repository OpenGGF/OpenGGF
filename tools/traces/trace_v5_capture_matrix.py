#!/usr/bin/env python3
"""Expand and preflight the reviewed native trace-v5 scratch matrix.

This module is deliberately a scratch-only tool.  It never writes beneath the
installed fixture root and the assembler refuses to replace a candidate root
or any file already present there.  The matrix is the maintained authority for
the Task 9 capture command set; a capture is not started by this program.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shlex
import shutil
import subprocess
import sys
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))
MATRIX_FORMAT = "openggf-trace-v5-capture-matrix-v1"
MATRIX_DOCUMENT = (REPOSITORY_ROOT / "docs" / "architecture" / "validation" /
                   "trace" / "2026-08-04-trace-v5-capture-matrix.json")
FREEZE = {
    "source_commit": "cd89d6ab4f623c99afc76629eb423cd03f246809",
    "development_baseline": "3573af57be947284a1f8398c7b4b4e05a8b12f14",
    "source_diff_sha256": "b45bfc7e521cddc5caa18fc4363ec9240a09d7a678e2a8fb36b431abf152335b",
    "native_artifact": {
        "path": "tools/bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.exe",
        "size": 359424,
        "sha256": "81b072f37a1b3a1202d6ac02b5e230365adbe3e9a6e2be9bb2fbee274738f459",
    },
    "native_test_artifact": {
        "path": "tools/bizhawk-headless/bin/Release/BizHawk.Headless.Gpgx.Tests.exe",
        "size": 619520,
        "sha256": "3f90d1dc4df4fb80b9e3b3b4445b949934a209c7da2b964f3cbbb078f0730f4b",
    },
    "fixture_inventory": {
        "path": "docs/architecture/validation/trace/2026-08-03-trace-v5-baseline-inventory.json",
        "aggregate_sha256": "52ea19afea7250121c35a94927e3a4b950c6b00b8fac9570284401db3f0615bd",
        "file_count": 913,
    },
}

ROMS = {
    "s1": {
        "environment": "S1_ROM_PATH",
        "filename": "Sonic The Hedgehog (W) (REV01) [!].gen",
        "crc32": "AFE05EEE",
        "sha1": "69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B",
        "sha256": "1b7f6635bd713f37f3c2f44f302b872c2e3c5f56e63637918dad4637146900fd",
    },
    "s2": {
        "environment": "S2_ROM_PATH",
        "filename": "Sonic The Hedgehog 2 (W) (REV01) [!].gen",
        "crc32": "7B905383",
        "sha1": "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9",
        "sha256": "193bc4064ce0daf27ea9e908ed246d87ec576cc294833badebb590b6ad8e8f6b",
    },
    "s3k": {
        "environment": "S3K_ROM_PATH",
        "filename": "Sonic and Knuckles & Sonic 3 (W) [!].gen",
        "crc32": "63522553",
        "sha1": "CFBF98C36C776677290A872547AC47C53D2761D6",
        "sha256": "fba0677fde9f76df93f3e98d6310d8af68b9847bde16e253d73cd4dd8134ed23",
    },
}

# Paths are relative to src/test/resources/traces.  ``source`` is relative to
# a capture root; an empty source maps all output files at the root.  Keeping
# this declarative makes the copy plan reviewable before any capture exists.
def _mapping(source: str, destination: str, duplicate: bool = False) -> dict[str, Any]:
    item = {"source": source, "destination": destination}
    if duplicate:
        item["allow_duplicate"] = True
    return item


ROWS: list[dict[str, Any]] = [
    {"id": "s1-ghz1", "game": "s1", "movie": "s1/ghz1_fullrun/ghz1_fullrun.bk2", "movie_sha256": "dced61b2d3a3346b2ecd62254140497ef2827374c1de8597780f91e39ca0dcea", "selectors": [], "mappings": [_mapping("", "s1/ghz1_fullrun")]},
    {"id": "s1-mz1", "game": "s1", "movie": "s1/mz1_fullrun/s1-mz1.bk2", "movie_sha256": "30ec610949961b5862321ad419be34ce8d4dbecc815ceef688114af3c5657cf8", "selectors": [], "mappings": [_mapping("", "s1/mz1_fullrun")]},
    {"id": "s1-complete", "game": "s1", "movie": "s1/_movies/s1-complete-run.bk2", "movie_sha256": "f744c814d8e00d6c367f7fe83bb663cab123b5a4ed385a320d71b74d63146bde", "selectors": ["--trace-profile", "complete_run"], "mappings": [_mapping("", "s1")]},
    {"id": "s1-maze-run", "game": "s1", "movie": "s1/runs/s1-ghz-maze-roundtrip/s1-ghz-maze-roundtrip.bk2", "movie_sha256": "68e56a8db849e24afd95c038e789d05e3ac100d1d49e350a7191db1fce60053f", "selectors": ["--run-id", "s1-ghz-maze-roundtrip"], "mappings": [_mapping("", "s1/runs/s1-ghz-maze-roundtrip"), _mapping("ss", "s1/special_stage", True)]},
    {"id": "s1-emeralds-run", "game": "s1", "movie": "s1/runs/s1-sonic-complete-withemeralds/sonic1-complete-withemeralds.bk2", "movie_sha256": "f2e817936d07b2b1f2b80d61451f174189509a2817da2b2349ce0e19b8a5567b", "selectors": ["--run-id", "s1-sonic-complete-withemeralds"], "mappings": [_mapping("", "s1/runs/s1-sonic-complete-withemeralds")]},
    {"id": "s1-credits-a", "game": "s1", "movie": None, "movie_sha256": None, "selectors": ["--trace-profile", "credits_demo", "--credits-target", "all"], "credits": {"raw_sidecar": "raw/s1-credits-a.jsonl", "observation_id": "s1-credits-a"}, "mappings": [_mapping("", "s1")]},
    {"id": "s1-credits-b", "game": "s1", "movie": None, "movie_sha256": None, "selectors": ["--trace-profile", "credits_demo", "--credits-target", "all"], "credits": {"raw_sidecar": "raw/s1-credits-b.jsonl", "observation_id": "s1-credits-b"}, "mappings": []},
    {"id": "s2-ehz1", "game": "s2", "movie": "s2/ehz1_fullrun/s2-ehz1.bk2", "movie_sha256": "db310fa5e70a3cbaca4bafb06d98509894df920e4ab267d3e22db3f530104eed", "selectors": ["--trace-profile", "gameplay_unlock"], "mappings": [_mapping("", "s2/ehz1_fullrun")]},
]

for zone in ("arz", "cnz", "cpz", "htz", "mcz", "ooz"):
    ROWS.extend([
        {"id": f"s2-{zone}-0", "game": "s2", "movie": f"s2/{zone}/s2-lvl-select-{zone.upper()}.bk2", "movie_sha256": {"arz": "258a9441727d3746ca55d0a697a9613e5e0cfa94464b06afc6b3930a7eaffc11", "cnz": "fd84ccd7851d687b0ec2459152076b8a7bcb6f2a15c9bc2c72cdcef79d11db15", "cpz": "7e28cf822d5dbbe64646965cd857f264bf51d3349075af94db1a818cac7311e4", "htz": "44c55e255313e0731a405284575afe138843392f152f8f46ef8d7fc14a05daaa", "mcz": "b5424fc931bd6cbe343c1c2595e06bd9a488ef779f0987a29252e5bfddb92dfd", "ooz": "5ea2c45e3c7672a758959e3a13a70c7fb0bd65761acf0cc0ee904b660759c3eb"}[zone], "selectors": ["--trace-profile", "level_gated_reset_aware", "--gameplay-segment", "0"], "mappings": [_mapping("", f"s2/{zone}")]},
        {"id": f"s2-{zone}-1", "game": "s2", "movie": f"s2/{zone}/s2-lvl-select-{zone.upper()}.bk2", "movie_sha256": {"arz": "258a9441727d3746ca55d0a697a9613e5e0cfa94464b06afc6b3930a7eaffc11", "cnz": "fd84ccd7851d687b0ec2459152076b8a7bcb6f2a15c9bc2c72cdcef79d11db15", "cpz": "7e28cf822d5dbbe64646965cd857f264bf51d3349075af94db1a818cac7311e4", "htz": "44c55e255313e0731a405284575afe138843392f152f8f46ef8d7fc14a05daaa", "mcz": "b5424fc931bd6cbe343c1c2595e06bd9a488ef779f0987a29252e5bfddb92dfd", "ooz": "5ea2c45e3c7672a758959e3a13a70c7fb0bd65761acf0cc0ee904b660759c3eb"}[zone], "selectors": ["--trace-profile", "level_gated_reset_aware", "--gameplay-segment", "1"], "mappings": [_mapping("", f"s2/{zone}2")]},
    ])
ROWS.extend([
    {"id": "s2-mtz-0", "game": "s2", "movie": "s2/mtz/s2-lvl-select-MTZ.bk2", "movie_sha256": "7381a5e1d64094f7ade780bb60f63cb2e65b67db36bee96453071476d1dcd322", "selectors": ["--trace-profile", "level_gated_reset_aware", "--gameplay-segment", "0"], "mappings": [_mapping("", "s2/mtz")]},
    {"id": "s2-mtz-1", "game": "s2", "movie": "s2/mtz/s2-lvl-select-MTZ.bk2", "movie_sha256": "7381a5e1d64094f7ade780bb60f63cb2e65b67db36bee96453071476d1dcd322", "selectors": ["--trace-profile", "level_gated_reset_aware", "--gameplay-segment", "1"], "mappings": [_mapping("", "s2/mtz2")]},
    {"id": "s2-mtz-2", "game": "s2", "movie": "s2/mtz/s2-lvl-select-MTZ.bk2", "movie_sha256": "7381a5e1d64094f7ade780bb60f63cb2e65b67db36bee96453071476d1dcd322", "selectors": ["--trace-profile", "level_gated_reset_aware", "--gameplay-segment", "2"], "mappings": [_mapping("", "s2/mtz3")]},
    {"id": "s2-dez", "game": "s2", "movie": "s2/dez_ending/s2-lvl-select-DEZ-Ending.bk2", "movie_sha256": "b9da5105004d9efd9f613b8e54c5eb4df56fba13172c2e2fd916c08b5132882d", "selectors": ["--trace-profile", "level_gated_reset_aware", "--gameplay-segment", "0"], "mappings": [_mapping("", "s2/dez_ending")]},
    {"id": "s2-scz", "game": "s2", "movie": "s2/scz/s2-lvl-select-SCZ.bk2", "movie_sha256": "13e27a032e3c55a3f3fba8247bf0103b1bfad7202f45c473e25ae07dd4398342", "selectors": ["--trace-profile", "level_gated_reset_aware", "--gameplay-segment", "0"], "mappings": [_mapping("", "s2/scz")]},
    {"id": "s2-wfz", "game": "s2", "movie": "s2/wfz/s2-lvl-select-WFZ.bk2", "movie_sha256": "7fa8452d17d13636f8ad4f26bdab989e8a8e8db8b3d16c4d68bdc1d32460c842", "selectors": ["--trace-profile", "level_gated_reset_aware", "--gameplay-segment", "0"], "mappings": [_mapping("", "s2/wfz")]},
    {"id": "s2-special-stage", "game": "s2", "movie": "s2/special_stage/s2-lvl-select-special-stage.bk2", "movie_sha256": "b2a5edccdf14f986e25d635929d92b2d1bd2d16b1f2da0af9f1814c113ddcc8b", "selectors": ["--trace-profile", "s2_special_stage"], "mappings": [_mapping("", "s2/special_stage")]},
    {"id": "s2-halfpipe-run", "game": "s2", "movie": "s2/runs/s2-ehz-halfpipe-roundtrip/s2-ehz-halfpipe-roundtrip.bk2", "movie_sha256": "afc95984a7eb69b6464df0364e96554a97fa7d59ca7c12a66ed00ac1fb3f4446", "selectors": ["--run-id", "s2-ehz-halfpipe-roundtrip", "--effective-movie-length", "22612"], "mappings": [_mapping("", "s2/runs/s2-ehz-halfpipe-roundtrip")]},
    {"id": "s2-emeralds-run", "game": "s2", "movie": "s2/runs/s2-sonic-tails-complete-emeralds/sonic-2-sonic-tails-complete-emeralds.bk2", "movie_sha256": "e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5", "selectors": ["--run-id", "s2-sonic-tails-complete-emeralds"], "mappings": [_mapping("", "s2/runs/s2-sonic-tails-complete-emeralds")]},
    {"id": "s3k-aiz", "game": "s3k", "movie": "s3k/aiz1_to_hcz_fullrun/s3-aiz1-2-sonictails.bk2", "movie_sha256": "6837de0f67db7eb68f20b6f6df6a2872713a613d8b4dbc804847209c16b56e97", "selectors": ["--trace-profile", "aiz_end_to_end", "--load-queue-state"], "mappings": [_mapping("", "s3k/aiz1_to_hcz_fullrun")]},
    {"id": "s3k-cnz", "game": "s3k", "movie": "s3k/cnz/s3k-cnz-sonic-tails.bk2", "movie_sha256": "09bd6fa87f41ed85113254f80c5cbadcc31bbdb43605389933e2836339c9c340", "selectors": ["--trace-profile", "level_gated_reset_aware", "--load-queue-state"], "mappings": [_mapping("", "s3k/cnz")]},
    {"id": "s3k-mgz", "game": "s3k", "movie": "s3k/mgz/s3k-mgz-sonic-tails.bk2", "movie_sha256": "fd576d4096c9208742162449e756491d1030decae28d52ae7c93a4d249d60c02", "selectors": ["--trace-profile", "level_gated_reset_aware", "--load-queue-state"], "mappings": [_mapping("", "s3k/mgz")]},
    {"id": "s3k-complete", "game": "s3k", "movie": "s3k/_movies/s3k-complete-sonic-tails.bk2", "movie_sha256": "82eabfbc65e33c160ce209baa1ca3f967cb677fe22350bc100625d8c41a8e1bf", "selectors": ["--trace-profile", "complete_run", "--load-queue-state"], "mappings": [_mapping("", "s3k")]},
    {"id": "s3k-multibonus-c", "game": "s3k", "movie": "s3k/_movies/s3-knux-multibonus-ss.bk2", "movie_sha256": "d7485e13f427d1b335cbbb3c405ff6136a77326239b79d3aed61e902078af45c", "selectors": ["--run-id", "s3k-multibonus", "--load-queue-state"], "mappings": [_mapping("", "s3k")]},
    {"id": "s3k-multibonus-b", "game": "s3k", "movie": "s3k/_movies/s3-knux-multibonus-ss.bk2", "movie_sha256": "d7485e13f427d1b335cbbb3c405ff6136a77326239b79d3aed61e902078af45c", "selectors": ["--run-id", "s3-knux-multibonus-ss", "--load-queue-state"], "mappings": [_mapping("", "s3k/runs/s3-knux-multibonus-ss")]},
    {"id": "s3k-knuckles-superemeralds", "game": "s3k", "movie": "s3k/_movies/s3k-knuckles-complete-superemeralds.bk2", "movie_sha256": "aa892856df22b7bb1fe5accb48db10b90dc26845d1dccee90352da30349f53cc", "selectors": ["--run-id", "s3k-knuckles-complete-superemeralds", "--load-queue-state"], "mappings": [_mapping("", "s3k/runs/s3k-knuckles-complete-superemeralds")]},
])


def matrix_document() -> dict[str, Any]:
    """Return the reviewed on-disk matrix, including its publication mappings.

    The constants above retain the original command-generation defaults for
    callers that import them, but publication mappings are deliberately owned
    by the JSON document so a corrected destination cannot be silently masked
    by a stale embedded copy.
    """
    return load_document(MATRIX_DOCUMENT)


def load_document(path: Path = MATRIX_DOCUMENT) -> dict[str, Any]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"cannot read capture matrix {path}: {error}") from error
    validate_document(document)
    return document


def validate_document(document: dict[str, Any]) -> None:
    if document.get("format") != MATRIX_FORMAT:
        raise ValueError("capture matrix format is unsupported")
    rows = document.get("rows")
    if not isinstance(rows, list) or len(rows) != 36:
        raise ValueError("capture matrix must contain exactly 36 rows")
    ids = [row.get("id") for row in rows]
    if any(not isinstance(identifier, str) or not identifier for identifier in ids) or len(ids) != len(set(ids)):
        raise ValueError("capture row ids must be nonempty and unique")
    for row in rows:
        if row.get("game") not in ROMS:
            raise ValueError(f"unknown game in capture row {row.get('id')}")
        movie = row.get("movie")
        if movie is None and row["game"] != "s1":
            raise ValueError(f"only S1 credits rows may omit a movie: {row['id']}")
        if movie is not None and (not row.get("movie_sha256") or len(row["movie_sha256"]) != 64):
            raise ValueError(f"movie hash missing for {row['id']}")
        if movie is None and row.get("movie_sha256") is not None:
            raise ValueError(f"movie hash must be absent for {row['id']}")
        if not isinstance(row.get("selectors"), list):
            raise ValueError(f"selectors missing for {row['id']}")
        if not isinstance(row.get("mappings"), list):
            raise ValueError(f"publication mappings missing for {row['id']}")
    credits = [row for row in rows if row.get("credits")]
    if {row["id"] for row in credits} != {"s1-credits-a", "s1-credits-b"}:
        raise ValueError("matrix must contain exactly two credits captures")
    if any(row["id"] != "s1-credits-b" and row["mappings"] == [] for row in credits):
        raise ValueError("the first credits capture must own publication paths")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha1_file(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def crc32_file(path: Path) -> str:
    value = 0
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            value = zlib.crc32(chunk, value)
    return f"{value & 0xffffffff:08X}"


def verify_freeze(repository_root: Path, document: dict[str, Any]) -> None:
    freeze = document["freeze"]
    source_commit = freeze["source_commit"]
    try:
        subprocess.run(["git", "-C", str(repository_root), "cat-file", "-e", f"{source_commit}^{{commit}}"], check=True, capture_output=True)
        subprocess.run(["git", "-C", str(repository_root), "merge-base", "--is-ancestor", source_commit, "HEAD"], check=True, capture_output=True)
        actual_diff = subprocess.run(["git", "-C", str(repository_root), "diff", "--full-index", "--binary", f"origin/develop..{source_commit}"], check=True, capture_output=True).stdout
    except subprocess.CalledProcessError as error:
        raise ValueError(f"frozen source boundary is unavailable: {error}") from error
    if hashlib.sha256(actual_diff).hexdigest() != freeze["source_diff_sha256"]:
        raise ValueError("source diff hash does not match the replacement freeze")
    for key in ("native_artifact", "native_test_artifact"):
        artifact = freeze[key]
        path = repository_root / artifact["path"]
        if not path.is_file() or path.stat().st_size != artifact["size"] or sha256_file(path) != artifact["sha256"]:
            raise ValueError(f"frozen {key} identity mismatch: {path}")


def verify_roms(repository_root: Path, document: dict[str, Any]) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for game, expected in document["roms"].items():
        configured = os.environ.get(expected["environment"])
        path = Path(configured).expanduser() if configured else repository_root / expected["filename"]
        path = path.resolve()
        if not path.is_file():
            raise ValueError(f"verified {game} ROM is absent: {path}")
        if sha256_file(path).lower() != expected["sha256"].lower() or sha1_file(path).lower() != expected["sha1"].lower() or crc32_file(path) != expected["crc32"]:
            raise ValueError(f"verified {game} ROM identity mismatch: {path}")
        result[game] = path
    return result


def verify_movies(repository_root: Path, document: dict[str, Any]) -> None:
    for row in document["rows"]:
        if row["movie"] is None:
            continue
        path = repository_root / "src/test/resources/traces" / row["movie"]
        if not path.is_file() or sha256_file(path).lower() != row["movie_sha256"].lower():
            raise ValueError(f"movie identity mismatch for {row['id']}: {path}")


def preflight(repository_root: Path, batch_root: Path, candidate_root: Path, document: dict[str, Any], *, require_capacity: bool = True) -> dict[str, Any]:
    repository_root = repository_root.resolve()
    batch_root = batch_root.resolve()
    candidate_root = candidate_root.resolve()
    installed_root = repository_root / "src/test/resources/traces"
    if candidate_root.exists():
        raise ValueError(f"candidate root must be absent: {candidate_root}")
    if batch_root.is_relative_to(installed_root) or candidate_root.is_relative_to(installed_root):
        raise ValueError("scratch paths must remain outside installed fixtures")
    verify_freeze(repository_root, document)
    verify_roms(repository_root, document)
    verify_movies(repository_root, document)
    inventory = repository_root / document["freeze"]["fixture_inventory"]["path"]
    from tools.traces.trace_fixture_inventory import load_inventory, verify_inventory
    verify_inventory(installed_root, load_inventory(inventory))
    absent: list[str] = []
    for row in document["rows"]:
        output = batch_root / document["capture"]["output_template"].format(id=row["id"])
        if output.exists():
            raise ValueError(f"capture output must be absent: {output}")
        absent.append(str(output))
        if row.get("credits"):
            sidecar = batch_root / row["credits"]["raw_sidecar"]
            if sidecar.exists():
                raise ValueError(f"raw sidecar must be absent: {sidecar}")
    usage = shutil.disk_usage(batch_root.parent if batch_root.parent.exists() else batch_root)
    required = int(document["capture"]["estimated_peak_bytes"] * document["capture"]["required_free_space_multiplier"])
    if require_capacity and usage.free < required:
        raise ValueError(f"scratch capacity {usage.free} is below required {required} bytes")
    return {"batch_root": str(batch_root), "candidate_root": str(candidate_root), "required_free_bytes": required, "available_free_bytes": usage.free, "absent_outputs": absent}


def expand_commands(repository_root: Path, batch_root: Path, document: dict[str, Any], roms: dict[str, Path] | None = None) -> list[str]:
    repository_root = repository_root.resolve()
    batch_root = batch_root.resolve()
    roms = roms or {game: repository_root / expected["filename"] for game, expected in document["roms"].items()}
    runner = repository_root / document["capture"]["runner"]
    commands: list[str] = []
    for row in document["rows"]:
        output = batch_root / document["capture"]["output_template"].format(id=row["id"])
        args = [str(runner), "--mode", document["capture"]["mode"], "--rom", str(roms[row["game"]])]
        if row["movie"] is not None:
            args.extend(["--movie", str(repository_root / "src/test/resources/traces" / row["movie"])])
        args.extend(["--output", str(output)])
        args.extend(row["selectors"])
        if row.get("credits"):
            args.extend(["--credits-raw-observations", str(batch_root / row["credits"]["raw_sidecar"]), "--credits-raw-observation-id", row["credits"]["observation_id"]])
        commands.append(" ".join(shlex.quote(argument) for argument in args))
    return commands


def copy_tree_no_replace(source_root: Path, source_prefix: str, destination_root: Path, destination_prefix: str, seen: set[str], allow_duplicate: bool = False) -> int:
    source = source_root / source_prefix
    if not source.is_dir():
        raise ValueError(f"capture mapping source is absent: {source}")
    copied = 0
    for path in sorted(source.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(source).as_posix()
        # Track identity relative to the capture root, not the mapping's
        # source prefix: ``source=""`` and ``source="ss"`` may intentionally
        # refer to the same file when publishing a standalone special stage.
        key = f"{source_root.resolve()}::{path.relative_to(source_root).as_posix()}"
        if key in seen and not allow_duplicate:
            raise ValueError(f"capture output is mapped more than once: {key}")
        seen.add(key)
        destination = destination_root / destination_prefix / relative
        if destination.exists():
            raise ValueError(f"candidate file would be replaced: {destination}")
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(path.read_bytes())
        copied += 1
    return copied


def assemble(repository_root: Path, batch_root: Path, candidate_root: Path, document: dict[str, Any]) -> dict[str, Any]:
    repository_root = repository_root.resolve()
    batch_root = batch_root.resolve()
    candidate_root = candidate_root.resolve()
    installed_root = repository_root / "src/test/resources/traces"
    if candidate_root.exists():
        raise ValueError(f"candidate root must be absent: {candidate_root}")
    if candidate_root.is_relative_to(installed_root):
        raise ValueError("candidate root must remain outside installed fixtures")
    candidate_root.mkdir(parents=True)
    copied = 0
    # Static inputs are copied byte-for-byte; generated trace payloads are not.
    for path in sorted(installed_root.rglob("*.bk2")):
        destination = candidate_root / path.relative_to(installed_root)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(path.read_bytes())
        copied += 1
    for relative_static in document.get("static_paths", []):
        static_path = installed_root / relative_static
        if not static_path.is_file():
            raise ValueError(f"declared static input is absent: {static_path}")
        destination = candidate_root / relative_static
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(static_path.read_bytes())
        copied += 1
    seen: set[str] = set()
    for row in document["rows"]:
        capture = batch_root / document["capture"]["output_template"].format(id=row["id"])
        if not capture.is_dir():
            raise ValueError(f"capture output is absent: {capture}")
        for mapping in row["mappings"]:
            copied += copy_tree_no_replace(capture, mapping["source"], candidate_root, mapping["destination"], seen, mapping.get("allow_duplicate", False))
    return {"candidate_root": str(candidate_root), "copied_files": copied, "capture_rows": len(document["rows"])}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("validate", "expand", "preflight", "assemble"))
    parser.add_argument("--repository-root", type=Path, default=REPOSITORY_ROOT)
    parser.add_argument("--matrix", type=Path, default=MATRIX_DOCUMENT)
    parser.add_argument("--batch-root", type=Path)
    parser.add_argument("--candidate-root", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--skip-capacity", action="store_true")
    args = parser.parse_args(argv)
    try:
        document = load_document(args.matrix)
        if args.command == "validate":
            print(json.dumps({"format": document["format"], "rows": len(document["rows"])}, sort_keys=True))
        elif args.command == "expand":
            if args.batch_root is None:
                raise ValueError("--batch-root is required for expand")
            content = ("\n".join(expand_commands(args.repository_root, args.batch_root, document)) + "\n").encode()
            if args.output:
                from tools.traces.no_replace_output import write_bytes_no_replace
                write_bytes_no_replace(args.output, content, "capture command ledger")
            else:
                sys.stdout.buffer.write(content)
        elif args.command == "preflight":
            if args.batch_root is None or args.candidate_root is None:
                raise ValueError("--batch-root and --candidate-root are required for preflight")
            result = preflight(args.repository_root, args.batch_root, args.candidate_root, document, require_capacity=not args.skip_capacity)
            print(json.dumps(result, indent=2, sort_keys=True))
        else:
            if args.batch_root is None or args.candidate_root is None:
                raise ValueError("--batch-root and --candidate-root are required for assemble")
            result = assemble(args.repository_root, args.batch_root, args.candidate_root, document)
            print(json.dumps(result, indent=2, sort_keys=True))
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"trace-v5 capture matrix failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
