"""Pixel-level checks the JVM test cannot do: alpha corners, opaque centre, equal visual size, wallpaper size."""
import sys, os, glob
from PIL import Image
import numpy as np
root = sys.argv[1]
bad = 0
bbs = []
for f in sorted(glob.glob(f"{root}/dist/icons/*.png")):
    im = Image.open(f)
    a = np.asarray(im.convert("RGBA"))[..., 3]
    ok = im.size == (512, 512) and im.mode == "RGBA" and a[0, 0] == a[0, -1] == a[-1, 0] == a[-1, -1] == 0 and a[256, 256] == 255
    ys, xs = np.where(a > 127)
    bb = (xs.min(), ys.min(), xs.max(), ys.max()); bbs.append(bb)
    if not ok or bb != bbs[0]:
        bad += 1; print("BAD", f, im.size, bb)
print(len(bbs), "icons checked; bounding box of every icon:", bbs[0], "; problems:", bad)
for k in ("home", "lock"):
    im = Image.open(f"{root}/dist/wallpapers/rob_neon_{k}.jpg"); assert im.size == (1440, 3120), im.size
print("wallpapers 1440x3120 OK")
sys.exit(1 if bad else 0)
