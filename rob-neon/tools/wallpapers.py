"""Procedural wallpapers: a twisted ribbon, dark titanium on one face, blue glass on
the other, thin red edge line. Software rasteriser (painter's algorithm), no external 3D."""
import sys, math
import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi

W0, H0 = 1440, 3120
ASPECT = H0 / W0


def catmull(pts, n, closed=True):
    P = np.array(pts, float)
    m = len(P)
    out = []
    for i in range(m if closed else m - 3):
        p0, p1, p2, p3 = (P[(i + k) % m] for k in range(4)) if closed else P[i:i + 4]
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    return np.array(out)


def smooth(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def env(r, seed):
    rx, ry = r[:, 0], r[:, 1]
    base = 0.10 + 0.50 * smooth(-ry, -0.4, 0.9)
    band1 = 0.60 * np.exp(-((rx - 0.35) / 0.10) ** 2) * smooth(-ry, -0.9, 0.2)
    band2 = 0.45 * np.exp(-((ry + 0.55) / 0.07) ** 2)
    band3 = 0.25 * np.exp(-((rx + 0.55) / 0.16) ** 2)
    return base + band1 + band2 + band3


def ribbon_quads(ctrl, width, twist, N, M, thick, seed):
    C = catmull(ctrl, N // len(ctrl) + 1)
    n = len(C)
    t = np.arange(n) / n
    T = np.roll(C, -1, 0) - np.roll(C, 1, 0)
    T /= np.linalg.norm(T, axis=1, keepdims=True)
    ref = np.array([0.0, 0.0, 1.0])
    B0 = np.cross(T, ref)
    B0 /= np.linalg.norm(B0, axis=1, keepdims=True)
    N0 = np.cross(B0, T)
    th = twist(t)
    Bv = np.cos(th)[:, None] * B0 + np.sin(th)[:, None] * N0
    wv = width(t)[:, None]
    v = np.linspace(-1, 1, M + 1)
    P = C[:, None, :] + (v[None, :, None] * wv[:, None, :]) * Bv[:, None, :]
    Pn = np.roll(P, -1, 0)
    return P, Pn, T, Bv, t, v


def render(kind, out_w=1440, ss=2, seed=3):
    rng = np.random.default_rng(seed)
    Wd, Hd = out_w * ss, int(out_w * ss * ASPECT)
    img = Image.new("RGB", (Wd, Hd), (0, 0, 0))
    X = (np.arange(Wd) / Wd - 0.5)[None, :]
    Y = (np.arange(Hd) / Hd - 0.5)[:, None]

    # ---------------- background: graphite, soft key light, floor
    if kind == "lock":
        cx, cy, rad = 0.0, -0.18, 0.95
    else:
        cx, cy, rad = 0.12, -0.30, 0.85
    d2 = ((X - cx) / 0.9) ** 2 + ((Y - cy) / rad) ** 2
    g = np.exp(-d2 * 1.6)
    bg = 9 + 34 * g + 6 * (0.5 - Y)
    floor_y = 0.30 if kind == "lock" else 0.26
    fl = smooth(Y, floor_y - 0.01, floor_y + 0.02)
    bg = bg * (1 - 0.55 * fl) + 3 * fl
    noise = ndi.gaussian_filter(rng.standard_normal((Hd // 4 + 1, Wd // 4 + 1)).astype(np.float32), 3)
    noise = np.kron(noise, np.ones((4, 4), np.float32))[:Hd, :Wd]
    bg += noise * 7 * (0.4 + 0.6 * fl)
    bg3 = np.dstack([bg * 0.98, bg * 1.0, bg * 1.08])
    base = np.clip(bg3, 0, 255)

    # ---------------- ribbon geometry (screen units: X in [-.5,.5], Y in [-ASPECT/2, ASPECT/2])
    A = 1.0
    if kind == "lock":
        u = np.linspace(0, 2 * math.pi, 14, endpoint=False)
        ctrl = [(0.36 * math.sin(2 * a) - 0.01, 0.10 + 0.62 * math.sin(a + 0.3) * 1.0, 0.38 * math.cos(a)) for a in u]
        wid = lambda t: 0.17 + 0.04 * np.sin(2 * math.pi * t * 2 + 1)
        tw = lambda t: 2 * math.pi * 2 * t + 0.4
        ctrl = [(x, y * 1.15 + 0.12 * ASPECT / 2 * 0.0, z) for x, y, z in ctrl]
    else:
        u = np.linspace(0, 2 * math.pi, 12, endpoint=False)
        ctrl = [(0.30 * math.sin(2 * a) + 0.10, -0.62 + 0.30 * math.sin(a + 0.3), 0.30 * math.cos(a)) for a in u]
        wid = lambda t: 0.10 + 0.025 * np.sin(2 * math.pi * t * 2 + 1)
        tw = lambda t: 2 * math.pi * 2 * t + 0.9
    P, Pn, T, Bv, t, v = ribbon_quads(ctrl, wid, tw, 900, 44, 0.0, seed)
    n_, m_ = P.shape[0], P.shape[1] - 1
    Pq = [P[:, :-1], P[:, 1:], Pn[:, 1:], Pn[:, :-1]]  # four corners (n,m,3)
    Q = np.stack(Pq, 2)  # n,m,4,3
    ctr = Q.mean(2)
    e1 = Q[:, :, 2] - Q[:, :, 0]
    e2 = Q[:, :, 3] - Q[:, :, 1]
    nrm = np.cross(e1, e2)
    nrm /= np.linalg.norm(nrm, axis=-1, keepdims=True) + 1e-9
    # camera looks +z; view vector toward camera
    view = np.array([0, 0, -1.0])
    facing = nrm @ view
    side = facing >= 0               # True -> titanium, False -> glass
    nrm = np.where(side[..., None], nrm, -nrm)
    nz = np.abs(nrm @ view)
    refl = 2 * nz[..., None] * nrm - view
    # perspective projection to pixels
    def proj(p):
        s = 1.0 / (1.0 + 0.55 * p[..., 2])
        return (p[..., 0] * s + 0.5) * Wd, (p[..., 1] * s + 0.5 * 1.0) * Wd + (Hd - Wd) / 2.0 * 0 + 0.0
    xs, ys = proj(Q)
    ys = ys + (Hd / 2 - Wd / 2)      # screen centre
    depth = ctr[..., 2].reshape(-1)
    order = np.argsort(-depth)       # far first

    # shading
    vi = np.arange(m_)[None, :].repeat(n_, 0)
    streak = ndi.gaussian_filter(rng.standard_normal((m_ * 4, 1)).astype(np.float32), 0.6)[:, 0]
    streak = streak[(vi * 4) % len(streak)]
    ti = np.arange(n_)[:, None].repeat(m_, 1)
    e = env(refl.reshape(-1, 3), seed).reshape(n_, m_)
    # titanium
    tcol = np.zeros((n_, m_, 3), np.float32)
    tv = e * (0.86 + 0.05 * streak) + 0.05 * nz
    tcol[:] = (np.clip(tv, 0, 1.2)[..., None]) * np.array([178, 178, 184], np.float32)
    tcol += 6
    # glass
    fres = (1 - nz) ** 1.4
    vfrac = np.abs(np.arange(m_)[None, :] / (m_ - 1) * 2 - 1)
    rimg = smooth(vfrac, 0.80, 1.0)
    gf = np.clip(0.06 + 0.50 * fres + 0.55 * e ** 1.7 + 0.04 * streak + 0.55 * rimg, 0, 1)
    c0, c1 = np.array([2, 8, 46], np.float32), np.array([44, 118, 255], np.float32)
    gcol = c0 + (c1 - c0) * gf[..., None]
    gcol += (np.clip(e - 0.62, 0, 1) ** 1.2 * 230 + rimg * 150)[..., None] * np.array([0.6, 0.78, 1.0], np.float32)
    wmix = (smooth(facing, 0.30, 0.62))[..., None]
    col = np.where(side[..., None], gcol * (1 - wmix) + tcol * wmix, gcol)
    # red edge lines (outer rows) on the titanium side, soft on glass
    edge = np.zeros((m_,), bool); edge[0] = True
    rcol = np.array([215, 20, 30], np.float32) * (0.35 + 0.7 * e[..., None])
    emask = edge[None, :, None] & (side[..., None] | True)
    col = np.where(emask, np.where(side[..., None], rcol, rcol * 0.8), col)
    col = np.clip(col, 0, 255)

    lay = Image.new("RGBA", (Wd, Hd), (0, 0, 0, 0))
    dr = ImageDraw.Draw(lay)
    flat_xy = np.stack([xs, ys], -1).reshape(-1, 4, 2)
    flat_col = col.reshape(-1, 3).astype(np.uint8)
    for i in order:
        c = tuple(int(x) for x in flat_col[i])
        pts = [tuple(p) for p in flat_xy[i]]
        dr.polygon(pts, fill=c + (255,), outline=c + (255,))

    L = np.asarray(lay, np.float32)
    alpha = L[..., 3:4] / 255
    # soft shadow + reflection on the floor
    shadow = ndi.gaussian_filter(L[..., 3], 38 * ss / 2) / 255
    base = base * (1 - 0.35 * shadow[..., None])
    fy = int((floor_y + 0.5) * Hd) if False else int(Hd * (0.5 + floor_y))
    refl_im = np.zeros_like(L)
    h = min(fy, Hd - fy)
    refl_im[fy:fy + h] = L[fy - h:fy][::-1]
    refl_im[..., :3] = ndi.gaussian_filter(refl_im[..., :3], (6 * ss, 6 * ss, 0))
    fade = np.clip(1 - (np.arange(Hd) - fy) / (0.22 * Hd), 0, 1)[:, None, None] ** 2
    ra = ndi.gaussian_filter(refl_im[..., 3], 6 * ss)[..., None] / 255 * 0.30 * fade
    base = base * (1 - ra) + refl_im[..., :3] * ra
    comp = base * (1 - alpha) + L[..., :3] * alpha
    # bloom
    br = np.clip(comp - 170, 0, None)
    comp = comp + 0.45 * ndi.gaussian_filter(br, (10 * ss, 10 * ss, 0))
    # vignette + grain
    vig = 1 - 0.45 * np.clip(((X) ** 2 + (Y * 0.55) ** 2) * 2.2, 0, 1)
    comp = comp * vig[..., None]
    comp += rng.standard_normal((Hd, Wd, 1)).astype(np.float32) * 1.6
    im = Image.fromarray(np.clip(comp, 0, 255).astype(np.uint8))
    return im.resize((out_w, int(out_w * ASPECT)), Image.LANCZOS)


if __name__ == "__main__":
    kind, out, w = sys.argv[1], sys.argv[2], int(sys.argv[3])
    render(kind, w, ss=1 if w < 900 else 2).save(out)
