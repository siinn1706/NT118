#!/usr/bin/env python3
"""Compute MAE, RMSE, MAPE, and Bland-Altman statistics/plot for camera PPG validation."""
from __future__ import annotations

import csv
import math
import pathlib
import statistics
import sys


ROOT = pathlib.Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "docs" / "ppg-validation.csv"
PLOT_PATH = ROOT / "docs" / "ppg-bland-altman.png"


def load_pairs(path: pathlib.Path) -> list[tuple[float, float]]:
    pairs: list[tuple[float, float]] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            raw_ppg = (row.get("ppg_bpm") or "").strip()
            raw_ox = (row.get("oximeter_bpm") or "").strip()
            if not raw_ppg or not raw_ox:
                continue
            pairs.append((float(raw_ppg), float(raw_ox)))
    return pairs


def main() -> int:
    if not CSV_PATH.exists():
        print(f"Missing {CSV_PATH}")
        return 1
    pairs = load_pairs(CSV_PATH)
    if not pairs:
        print(f"No paired readings recorded yet in {CSV_PATH}. Collect real device readings first.")
        return 0

    diffs = [ppg - ox for ppg, ox in pairs]
    means = [(ppg + ox) / 2.0 for ppg, ox in pairs]
    abs_err = [abs(d) for d in diffs]
    mae = statistics.fmean(abs_err)
    rmse = math.sqrt(statistics.fmean([d * d for d in diffs]))
    mape = statistics.fmean([abs(ppg - ox) / ox * 100.0 for ppg, ox in pairs if ox > 0])
    bias = statistics.fmean(diffs)
    sd = statistics.stdev(diffs) if len(diffs) > 1 else 0.0
    loa_hi = bias + 1.96 * sd
    loa_lo = bias - 1.96 * sd

    print(f"Paired readings : {len(pairs)}")
    print(f"MAE             : {mae:.2f} BPM (target <= 5.00 BPM)")
    print(f"RMSE            : {rmse:.2f} BPM")
    print(f"MAPE            : {mape:.2f}%")
    print(f"Bias (PPG - Ox) : {bias:+.2f} BPM (SD = {sd:.2f})")
    print(f"95% LoA         : [{loa_lo:+.2f}, {loa_hi:+.2f}] BPM")

    try:
        import matplotlib.pyplot as plt  # type: ignore

        plt.figure(figsize=(7, 4.5))
        plt.scatter(means, diffs, color="#0F766E", alpha=0.85)
        plt.axhline(bias, color="#13201C", linestyle="-", label=f"Bias ({bias:+.2f} BPM)")
        plt.axhline(loa_hi, color="#E11D48", linestyle="--", label=f"+1.96 SD ({loa_hi:+.2f})")
        plt.axhline(loa_lo, color="#E11D48", linestyle="--", label=f"-1.96 SD ({loa_lo:+.2f})")
        plt.title("Camera PPG vs Fingertip Pulse Oximeter (Bland-Altman)")
        plt.xlabel("Mean HR (BPM)")
        plt.ylabel("Difference: PPG - Oximeter (BPM)")
        plt.legend()
        plt.tight_layout()
        plt.savefig(PLOT_PATH, dpi=150)
        print(f"Saved plot to {PLOT_PATH}")
    except ImportError:
        print("matplotlib not installed; skipping Bland-Altman PNG generation.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
