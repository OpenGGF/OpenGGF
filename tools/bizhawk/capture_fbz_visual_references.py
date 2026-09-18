#!/usr/bin/env python3
"""Compatibility FBZ profile for capture_native_references.py.

Keeps the reviewed FBZ manifest, offsets and boundary recipes at the zone owner.
New zone diagnostics use the shared command with an explicit exporter and plan.
"""
import json
from pathlib import Path
import sys

# Also supports the existing file-based import used by the framebuffer guards.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from capture_native_references import argument_parser, digest, framebuffer_content, probe, run_capture


def capture_plan(start_frame: int, window: int) -> str:
    channels = ("200", "208", "210", "230", "238")
    return ('return {manifest_sha256="261535247F627A3A48E088C4E640A544453D3AC9602054570088BD24737406D1",'
            'bk2_frame_offset=237913,observation_limit_frames=' + str(window)
            + ',checkpoints={{id="fbz1-start-outdoor",bk2_frame=' + str(start_frame) + '}}'
            + ',cadence_series={' + ','.join('["aniplc-cadence-' + ch + '"]={' + str(start_frame) + '}'
                                            for ch in channels) + '}}')


def boundary_plan(checkpoint: str) -> str:
    """Only approved boundary setup fields; no measured native inputs."""
    manifest = Path(__file__).resolve().parents[2] / "docs/architecture/research/s3k-zones/fbz-visual-checkpoints.json"
    if digest(manifest) != "261535247F627A3A48E088C4E640A544453D3AC9602054570088BD24737406D1":
        raise ValueError("Unreviewed boundary manifest")
    data = json.loads(manifest.read_text())
    allowed = {f"fbz1-boundary-{i}-outdoor" for i in (1,2,3,5,6)} | {"fbz1-boundary-4-horizontal", "fbz2-boundary-outdoor"}
    if checkpoint not in allowed:
        raise ValueError("Only reviewed boundaries supported")
    recipe = data["setup_recipes"][checkpoint]
    target = next(c for c in data["checkpoints"] if c["id"] == checkpoint)["player"]
    axis = "x" if checkpoint == "fbz1-boundary-4-horizontal" else "y"
    return ('{id=' + json.dumps(checkpoint) + ',x=' + str(recipe["centre"]["x"])
            + ',y=' + str(recipe["centre"]["y"]) + ',region=' + str(recipe["state"].get("Events_bg_00", recipe["state"].get("Events_fg_00")))
            + ',control=' + str(recipe.get("control", {}).get("native_object_control", 0))
            + ',act=' + str(recipe["act"]) + ',normal=' + str(recipe["state"]["Events_routine_bg"])
            + ',region_address=' + str(0xEED2 if recipe["act"]==1 else 0xEEC0)
            + ',address=' + str(0xB010 if axis == "x" else 0xB014)
            + ',forward=' + str(target[axis]) + ',reverse=' + str(recipe["centre"][axis]) + '}')


def main(argv=None):
    parser = argument_parser(__doc__)
    parser.add_argument("--fresh-entry-act", type=int, choices=(1, 2))
    parser.add_argument("--boundary-checkpoint", help="reviewed FBZ boundary recipe")
    parser.add_argument("--start-frame", type=int, default=237914)
    parser.add_argument("--window", type=int, default=1024)
    parser.set_defaults(rom_sha1="CFBF98C36C776677290A872547AC47C53D2761D6",
                        exporter=Path(__file__).with_suffix(".lua"))
    args = parser.parse_args(argv)
    if args.start_frame < 237914 or args.window < 1:
        parser.error("invalid FBZ capture window")
    if args.rom_sha1.upper() != "CFBF98C36C776677290A872547AC47C53D2761D6":
        parser.error("FBZ profile requires the verified locked-on S3K ROM")
    plan_text = capture_plan(args.start_frame, args.window)
    if args.boundary_checkpoint:
        if not args.fixture_state or args.exporter == Path(__file__).with_suffix(".lua"):
            parser.error("--boundary-checkpoint requires --fixture-state and --exporter")
        plan_text = plan_text[:-1] + ",boundary=" + boundary_plan(args.boundary_checkpoint) + "}"
    if args.fresh_entry_act:
        plan_text = plan_text[:-1] + ",fresh_entry_act=" + str(args.fresh_entry_act) + "}"
    aliases = {"OGGF_FBZ_" + old: "OGGF_NATIVE_" + new for old, new in (
        ("VISUAL_PLAN", "PLAN"), ("VISUAL_OUTPUT", "OUTPUT"), ("ROM_SHA1", "ROM_SHA1"),
        ("BK2_SHA256", "BK2_SHA256"), ("HOST_RECEIPT", "HOST_RECEIPT"),
        ("FRAMEBUFFER_PROBE", "FRAMEBUFFER_PROBE"), ("PYTHON", "PYTHON"),
        ("FIXTURE_STATE", "FIXTURE_STATE"))}
    return run_capture(args, parser, plan_text=plan_text,
                       environment_aliases=aliases, entrypoint=__file__)


if __name__ == "__main__":
    sys.exit(main())
