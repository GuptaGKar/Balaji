#!/usr/bin/env python3
"""Isolate the pill's own pixels by differencing two builds over an identical scene.

The two screenshots `ab-A-alpha030.png` and `ab-B-alpha094.png` are the same redesign build with
one constant changed (BalajiDesign.GlassAlpha), cold-started and scrolled by the same amounts, so
every pixel outside the pill is identical - verified, not assumed: the scene signature of a column
left of the pill matched exactly (9d3b91d6d566 in both).

That makes the difference between the two images a direct map of what the pill covers. Sampling
only those pixels, and only the ones that read as plain surface in the new build, gives the
legibility comparison on the same physical backdrop:

  alpha 0.30  ->  the page behind is mixed in at 70 %
  alpha 0.94  ->  the page behind is mixed in at 6 %

Untracked scratch tool.
"""
import statistics

from PIL import Image

from pass_pill import is_brand, rel_lum

A_PATH = "shots/ab-A-alpha030.png"
B_PATH = "shots/ab-B-alpha094.png"
DP = 450.0 / 160.0


def main():
    ia = Image.open(A_PATH).convert("RGB")
    ib = Image.open(B_PATH).convert("RGB")
    pa, pb = ia.load(), ib.load()
    w, h = ia.size
    y0, y1 = 1990, h
    x_lo, x_hi = 40, w - 40

    lum_a, lum_b = {}, {}
    for y in range(y0, y1):
        for x in range(x_lo, x_hi):
            lum_a[(x, y)] = rel_lum(pa[x, y][:3]) * 255
            lum_b[(x, y)] = rel_lum(pb[x, y][:3]) * 255

    pill = [k for k in lum_a if abs(lum_a[k] - lum_b[k]) > 8]
    print(f"pixels the pill changes: {len(pill)} of {len(lum_a)} sampled")
    if not pill:
        print("!! the two shots are identical - the alpha did not reach the build")
        return
    ys = sorted(k[1] for k in pill)
    xs = sorted(k[0] for k in pill)
    print(f"  rows {ys[0]}-{ys[-1]} = {round((ys[-1] - ys[0]) / DP, 1)} dp tall")
    print(f"  cols {xs[0]}-{xs[-1]} = {round((xs[-1] - xs[0]) / DP, 1)} dp wide")

    # The same physical surface pixels, read in both builds.
    surface = [k for k in pill if not is_brand(pb[k[0], k[1]][:3]) and lum_b[k] >= 230]

    def report(tag, vals):
        vals = sorted(vals)
        if not vals:
            print(f"  {tag}: no samples")
            return None
        print(f"  {tag}: n={len(vals)} median={round(statistics.median(vals), 1)} "
              f"stddev={round(statistics.pstdev(vals), 2)} "
              f"spread(p98-p02)={round(vals[int(len(vals) * 0.98)] - vals[int(len(vals) * 0.02)], 1)} "
              f"min={round(vals[0], 1)} max={round(vals[-1], 1)}")
        return statistics.pstdev(vals)

    print(f"\nsame {len(surface)} surface pixels, page behind them identical:")
    da = report("alpha 0.30", [lum_a[k] for k in surface])
    db = report("alpha 0.94", [lum_b[k] for k in surface])
    if da and db:
        print(f"\nbleed reduced by {round(da / db, 2)}x")
        print(f"surface lifted from {round(statistics.median([lum_a[k] for k in surface]), 1)} "
              f"to {round(statistics.median([lum_b[k] for k in surface]), 1)} of 255")


if __name__ == "__main__":
    main()
