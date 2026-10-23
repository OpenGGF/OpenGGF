#!/usr/bin/env python3
"""Summarize the bounded CD-02 USA title proof from native observer outputs."""
import argparse
import json
from pathlib import Path

from device_observer_run import OUTPUT_ROOT, digest, verify_run


def analyze(root, prior_oracle=None):
    manifest = json.loads((root / "manifest.json").read_text())
    for label, files in manifest["streams_sha256"].items():
        for name, expected in files.items():
            if digest(root / label / name) != expected:
                raise ValueError(f"Capture changed after validation: {label}/{name}")
        verify_run(root / label, manifest["frames"], label != "control")
    rows = [json.loads(line) for line in (root / "run-1/devices.jsonl").read_text().splitlines()]
    graphics = [json.loads(line) for line in (root / "run-1/graphics.jsonl").read_text().splitlines()]
    active_pcm = [r for r in rows if r["devices"]["pcm_hw.enabled"] and r["devices"]["pcm_hw.status"]]
    channels = {}
    for n in range(8):
        key = f"pcm_hw.chan[{n}].addr"
        active = [r for r in active_pcm if r["devices"]["pcm_hw.status"] & (1 << n)]
        if active:
            channels[str(n)] = {"first_active_frame": active[0]["frame"], "last_active_frame": active[-1]["frame"],
                                "distinct_active_cursors": len({r["devices"][key] for r in active}),
                                "first_cursor_16_11": active[0]["devices"][key],
                                "last_cursor_16_11": active[-1]["devices"][key]}
    audio = []
    for previous, row in zip(rows, rows[1:]):
        a, b = previous["devices"], row["devices"]
        if b["cdd_hw.index"] > 0 and b["cdd_hw.index"] == a["cdd_hw.index"] and b["cdd_hw.latency"] == 0:
            if b["audio_stream.current_offset"] > a["audio_stream.current_offset"]:
                audio.append(row)
    # TitleScreen InitGfxOperation/RunGfxOperation and _Common.inc at scddisasm 1ecc5f3.
    title = [r for r in graphics if all(r["request"][k] == v for k, v in {
        "trace_base": 0x1c800, "map_offset": 0x20000, "image_start_register": 0x1d000 // 4,
        "width": 256, "reconstructed_total_lines": 96, "stamp_size_register": 7}.items())]
    if not channels or not audio or not title:
        raise ValueError("Known USA boot proof lacks PCM, CDDA or source-matched title graphics")
    first_title = dict(title[0]["request"])
    words = first_title.pop("trace_words")
    first_title.update(frame=title[0]["frame"], vector_word_count=len(words), first_vector_words=words[:4])
    summary = {"schema": "openggf.soniccd.device-evidence-summary.v1", "diagnostic_only": True,
               "frame_boundary": "after FrameAdvance and GetSamplesSync, before next input",
               "frames": len(rows), "active_pcm_channels_zero_based": channels,
               "cdda_first_cursor_advance_after_latency_frame": audio[0]["frame"],
               "cdda_last_cursor_advance_frame": audio[-1]["frame"],
               "cdda_final_index_zero_based": rows[-1]["devices"]["cdd_hw.index"],
               "cdda_final_lba": rows[-1]["devices"]["cdd_hw.lba"],
               "cdda_final_next_unread_byte": rows[-1]["devices"]["audio_stream.current_offset"],
               "graphics_busy_samples": len(graphics), "title_descriptor_samples": len(title),
               "first_source_matched_title_descriptor": first_title,
               "timer_first_nonzero_frame": next(r["frame"] for r in rows if r["devices"]["timer"]),
               "cdc_dma_active_samples": sum(r["devices"]["cdc_hw.dma_active"] for r in rows),
               "checkpoint_sha256": manifest["summaries"]["run-1"]["checkpoint_sha256"],
               "callbacks": manifest["summaries"].get("callbacks", {}).get("callback_events"),
               "prior_oracle_av_equal": None}
    if prior_oracle:
        prior = [json.loads(line) for line in (prior_oracle / "av.jsonl").read_text().splitlines()]
        current = [json.loads(line) for line in (root / "run-1/av.jsonl").read_text().splitlines()]
        same = len(prior) == len(current) and all(all(old[k] == v for k, v in new.items()) for old, new in zip(prior, current))
        if not same:
            raise ValueError("Observer boot differs from the completed original boot oracle")
        summary["prior_oracle_av_equal"] = True
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture-root", type=Path, required=True)
    parser.add_argument("--prior-oracle", type=Path)
    args = parser.parse_args()
    root = args.capture_root.resolve(strict=True)
    if not root.is_relative_to(OUTPUT_ROOT):
        parser.error("Capture must be under the external observer root")
    summary = analyze(root, args.prior_oracle)
    destination = root / "event-summary.json"
    destination.write_text(json.dumps(summary, indent=2) + "\n")
    print(destination)


if __name__ == "__main__":
    main()
