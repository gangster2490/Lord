"""Procedural 'dark titanium / blue glass / red accent' icon renderer for ROB Neon.

Everything is drawn from vector primitives, turned into height-fields and lit
with a simple physically-inspired model (bevel normals, studio-environment
reflection, specular).  No artwork is cut out of any screenshot.
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage as ndi

S = 1024  # supersampled canvas; icons are exported at 512
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
_rng = np.random.default_rng(7)
_YY, _XX = np.mgrid[0:S, 0:S].astype(np.float32)
_BRUSH = ndi.gaussian_filter(_rng.standard_normal((S, S)).astype(np.float32), (0.6, 38))
_BRUSH /= np.abs(_BRUSH).max()
_GRAIN = ndi.gaussian_filter(_rng.standard_normal((S, S)).astype(np.float32), 0.8)
_GRAIN /= np.abs(_GRAIN).max()

LIGHT = np.array([-0.45, -0.65, 0.62], dtype=np.float32)
LIGHT /= np.linalg.norm(LIGHT)
_HV = LIGHT + np.array([0, 0, 1], dtype=np.float32)
_HV /= np.linalg.norm(_HV)


def px(v):
    return v * S / 100.0


# ---------------------------------------------------------------- primitives
def rr(d, x0, y0, x1, y1, r, v=255):
    d.rounded_rectangle([px(x0), px(y0), px(x1), px(y1)], radius=px(r), fill=v)


def el(d, cx, cy, rx, ry=None, v=255):
    ry = rx if ry is None else ry
    d.ellipse([px(cx - rx), px(cy - ry), px(cx + rx), px(cy + ry)], fill=v)


def pg(d, pts, v=255):
    d.polygon([(px(x), px(y)) for x, y in pts], fill=v)


def ln(d, pts, w, v=255):
    P = [(px(x), px(y)) for x, y in pts]
    r = px(w) / 2
    for (x0, y0), (x1, y1) in zip(P[:-1], P[1:]):
        n = max(1, int(math.hypot(x1 - x0, y1 - y0) / max(1.0, r / 4)))
        for i in range(n + 1):
            x, y = x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n
            d.ellipse([x - r, y - r, x + r, y + r], fill=v)


def arcpts(cx, cy, r, a0, a1, n=64):
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def ring(d, cx, cy, ro, ri, v=255):
    el(d, cx, cy, ro, v=v)
    el(d, cx, cy, ri, v=0)


def pie(d, cx, cy, r, a0, a1, v=255):
    pg(d, [(cx, cy)] + arcpts(cx, cy, r, a0, a1), v)


def rot(pts, cx, cy, ang):
    c, s = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    return [(cx + (x - cx) * c - (y - cy) * s, cy + (x - cx) * s + (y - cy) * c) for x, y in pts]


def rrot(d, cx, cy, w, h, ang, v=255):
    pg(d, rot([(cx - w / 2, cy - h / 2), (cx + w / 2, cy - h / 2), (cx + w / 2, cy + h / 2), (cx - w / 2, cy + h / 2)], cx, cy, ang), v)


def txt(d, s, cx, cy, size, v=255):
    f = ImageFont.truetype(FONT, int(px(size)))
    d.text((px(cx), px(cy)), s, font=f, fill=v, anchor="mm")


# ------------------------------------------------------------------ shading
def _interp(t, xs, ys):
    return np.interp(t, xs, ys).astype(np.float32)


def height(maskf, bevel, inset=False):
    dist = ndi.distance_transform_edt(maskf > 0.5).astype(np.float32)
    t = np.clip(dist / bevel, 0, 1)
    h = t * (2 - t) * bevel * 0.9
    h = ndi.gaussian_filter(h, 1.3)
    gy, gx = np.gradient(h)
    sgn = 1 if inset else -1
    n = np.stack([sgn * gx, sgn * gy, np.ones_like(h)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    return n, dist


def material(name, n, dist):
    nx, ny, nz = n[..., 0], n[..., 1], n[..., 2]
    diff = np.clip(n @ LIGHT, 0, 1)
    nh = np.clip(n @ _HV, 0, 1)
    ygrad = (0.5 - _YY / S)
    slope = 1 - nz
    if name == "rim":
        t = ny * 1.0 + nx * 0.8
        env = _interp(t, [-1.3, -0.6, -0.1, 0.3, 0.8, 1.3], [1.0, 0.82, 0.38, 0.20, 0.55, 0.85])
        col = env[..., None] * np.array([205, 216, 235], np.float32) + 10
        col += (nh ** 45 * 110)[..., None]
    elif name in ("S", "steel"):
        t = ny * 1.1 + nx * 0.45
        env = _interp(t, [-1.2, -0.7, -0.25, 0.1, 0.5, 1.2], [0.38, 1.0, 0.86, 0.66, 0.32, 0.08])
        flat = (nz > 0.985).astype(np.float32)
        env = env + flat * (ygrad * 0.30 + _BRUSH * 0.025 + _GRAIN * 0.01)
        k = 0.60 if name == "steel" else 1.0
        col = env[..., None] * np.array([214, 220, 230], np.float32) * k
        col += (nh ** 60 * 150)[..., None] * k
    elif name == "B":
        f = 0.22 + 0.45 * diff + slope * 1.4 + ygrad * 0.30
        f += np.clip(ny - 0.25, 0, 1) * 0.7
        f += np.exp(-dist / 40.0) * 0.12
        f = np.clip(f, 0, 1)[..., None]
        c0, c1 = np.array([5, 14, 70], np.float32), np.array([38, 108, 250], np.float32)
        col = c0 + (c1 - c0) * f
        col += (nh ** 20 * 150 + nh ** 90 * 260)[..., None] * np.array([0.65, 0.82, 1.0], np.float32)
    elif name == "R":
        f = 0.25 + 0.5 * diff + slope * 1.2 + ygrad * 0.25
        f += np.clip(ny - 0.25, 0, 1) * 0.5
        f = np.clip(f, 0, 1)[..., None]
        c0, c1 = np.array([85, 3, 9], np.float32), np.array([240, 48, 52], np.float32)
        col = c0 + (c1 - c0) * f
        col += (nh ** 40 * 200)[..., None] * np.array([1.0, 0.75, 0.75], np.float32)
    elif name == "D":
        f = 0.16 + 0.5 * diff + slope * 0.9 + ygrad * 0.12 + _BRUSH * 0.03
        f = np.clip(f, 0, 1)[..., None]
        c0, c1 = np.array([13, 14, 18], np.float32), np.array([88, 93, 106], np.float32)
        col = c0 + (c1 - c0) * f
        col += (nh ** 50 * 110)[..., None]
    else:
        raise ValueError(name)
    return np.clip(col, 0, 255)


def soften(m):
    return np.asarray(m, np.float32) / 255.0


class Icon:
    def __init__(self, face="G"):
        self.layers = []
        self.face = face

    def add(self, mat, fn, bevel=13, shadow=True):
        m = Image.new("L", (S, S), 0)
        fn(ImageDraw.Draw(m))
        self.layers.append((mat, soften(m), px(bevel) * 0.62, shadow))
        return self

    # ------------------------------------------------------------ render
    def render(self, out=512):
        M, R = 56, 205  # tile margin / corner radius
        W = 34          # bezel width

        def rmask(inset, r):
            m = Image.new("L", (S, S), 0)
            ImageDraw.Draw(m).rounded_rectangle([inset, inset, S - inset, S - inset], radius=r, fill=255)
            return soften(m)

        outer, inner = rmask(M, R), rmask(M + W, R - W)
        ringm = np.clip(outer - inner, 0, 1)
        comp = np.zeros((S, S, 3), np.float32)

        # bezel
        n, d = height(ringm, W / 2 + 2)
        a = ringm[..., None]
        comp = comp * (1 - a) + material("rim", n, d) * a * 0.92
        # outer thin dark edge line for separation from any background
        edge = np.clip(outer - rmask(M + 5, R - 5), 0, 1)
        comp = comp * (1 - edge[..., None] * 0.45)

        # face
        Y = _YY / S
        if self.face == "B":
            c0, c1 = np.array([4, 12, 58], np.float32), np.array([22, 70, 190], np.float32)
            g = np.clip(1.15 - Y * 1.1 + 0.25 * np.exp(-((_XX / S - 0.3) ** 2 + (Y - 0.25) ** 2) * 9), 0, 1)
            face = c0 + (c1 - c0) * g[..., None]
        else:
            top, bot = np.array([62, 69, 84], np.float32), np.array([9, 11, 16], np.float32)
            g = np.clip((Y - 0.08) / 0.78, 0, 1) ** 0.85
            face = top + (bot - top) * g[..., None]
            face += np.array([0, 4, 14], np.float32) * np.exp(-((1 - Y) * 7))[..., None]   # cool glow low in the glass
            face += (_GRAIN * 1.6)[..., None]
        n, d = height(inner, 26, inset=True)
        shade = (0.62 + 0.70 * np.clip(n @ LIGHT, 0, 1))[..., None] if self.face != "B" else (0.8 + 0.3 * np.clip(n @ LIGHT, 0, 1))[..., None]
        face = face * shade
        # glass sheen on the upper-left of the face
        sheen = np.clip(1 - (_XX / S * 0.55 + Y * 1.0 - 0.10) * 2.1, 0, 1) ** 1.3 * 0.17
        face += sheen[..., None] * 255
        a = inner[..., None]
        comp = comp * (1 - a) + np.clip(face, 0, 255) * a
        # inner vignette darkening near the rim (depth)
        _, di = height(inner, 60)
        vig = 1 - 0.30 * np.exp(-di / 28.0)
        comp = comp * np.where(inner[..., None] > 0, vig[..., None], 1)

        # glyph layers
        for mat, m, bevel, shadow in self.layers:
            if shadow:
                sh = ndi.gaussian_filter(ndi.shift(m, (14, 8), order=1), 9)
                comp = comp * (1 - 0.60 * sh[..., None] * inner[..., None])
            n, dist = height(m, bevel)
            col = material(mat, n, dist)
            a = ndi.gaussian_filter(m, 0.6)[..., None]
            comp = comp * (1 - a) + col * a

        alpha = ndi.gaussian_filter(outer, 0.7)
        rgba = np.dstack([np.clip(comp, 0, 255), alpha * 255]).astype(np.uint8)
        im = Image.fromarray(rgba, "RGBA")
        return im.resize((out, out), Image.LANCZOS)
