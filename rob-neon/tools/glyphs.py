"""Glyph definitions for the 30 icons. Unit space 0..100, glyph box ~ 24..76."""
import math
from engine import *


def phone():
    I = Icon()
    C, R_ = (67, 33), 30
    body = arcpts(C[0], C[1], R_, 96, 184)
    def tip(d, th):
        c, s_ = math.cos(math.radians(th)), math.sin(math.radians(th))
        rrot(d, C[0] + (R_ - 5) * c, C[1] + (R_ - 5) * s_, 19, 13, th + 90)
    I.add("S", lambda d: ln(d, body, 11), bevel=5.5)
    I.add("B", lambda d: (tip(d, 96), tip(d, 184)), bevel=5)
    return I


def contacts():
    I = Icon()
    I.add("S", lambda d: el(d, 50, 36, 11.5), bevel=9)
    def sh(d):
        pg(d, arcpts(50, 78, 25, 180, 360)); rr(d, 25, 77, 75, 79, 1)
    I.add("B", sh, bevel=10)
    return I


def smessages():
    I = Icon()
    def bub(d):
        rr(d, 24, 28, 76, 66, 16); pg(d, [(34, 62), (32, 78), (50, 64)])
        rr(d, 31, 35, 69, 59, 10, 0)
        pg(d, [(36, 58), (35.5, 70), (46, 59)], 0)
    I.add("S", bub, bevel=6)
    I.add("R", lambda d: [el(d, x, 47, 3.6) for x in (39, 50, 61)], bevel=3.5)
    return I


def gmessages():
    I = Icon()
    I.add("B", lambda d: (rr(d, 24, 26, 76, 66, 14), pg(d, [(33, 60), (30, 79), (50, 63)])), bevel=11)
    I.add("S", lambda d: [rr(d, 34, 36, 66, 41, 2.5), rr(d, 34, 47, 56, 52, 2.5)], bevel=2.6)
    I.add("R", lambda d: el(d, 63, 50, 3.4), bevel=3.2)
    return I


def camera():
    I = Icon()
    def body(d):
        rr(d, 22, 33, 78, 71, 8); rr(d, 38, 25, 62, 38, 4)
    I.add("S", body, bevel=7)
    I.add("D", lambda d: el(d, 50, 52, 15.5), bevel=6)
    I.add("B", lambda d: ring(d, 50, 52, 12, 5.5) , bevel=3.5)
    I.add("R", lambda d: el(d, 70, 40, 2.8), bevel=2.8, shadow=False)
    return I


def gallery():
    I = Icon("B")
    I.add("S", lambda d: [pg(d, [(22, 72), (42, 38), (58, 66)]), pg(d, [(46, 72), (62, 46), (78, 72)])], bevel=5)
    I.add("R", lambda d: el(d, 67, 31, 6.5), bevel=6)
    return I


def settings():
    I = Icon()
    def gear(d):
        el(d, 50, 50, 21)
        for i in range(8):
            rrot(d, 50, 50, 11, 52, i * 22.5, 255) if False else None
        for i in range(8):
            a = i * 45
            pts = rot([(44, 22), (56, 22), (55, 32), (45, 32)], 50, 50, a)
            pg(d, pts)
        el(d, 50, 50, 8, v=0)
    I.add("S", gear, bevel=6)
    I.add("R", lambda d: ring(d, 50, 50, 9, 6.4), bevel=2.4, shadow=False)
    I.add("B", lambda d: el(d, 50, 50, 6), bevel=5)
    return I


def calendar():
    I = Icon()
    I.add("S", lambda d: [rr(d, 31, 21, 36, 33, 2.5), rr(d, 64, 21, 69, 33, 2.5)], bevel=2.3)
    def card(d):
        rr(d, 24, 26, 76, 76, 7)
    I.add("D", card, bevel=5)
    I.add("B", lambda d: pg(d, [(24, 40), (24, 33), (31, 26), (69, 26), (76, 33), (76, 40)]), bevel=5)
    I.add("S", lambda d: txt(d, "31", 50, 59, 23), bevel=3.2)
    return I


def clock():
    I = Icon()
    I.add("S", lambda d: ring(d, 50, 50, 28, 22.5), bevel=3.2)
    I.add("D", lambda d: el(d, 50, 50, 22.5), bevel=4)
    I.add("S", lambda d: [ln(d, [(50, 50), (50, 34)], 4.2), ln(d, [(50, 50), (61, 56)], 4.2)], bevel=2)
    I.add("R", lambda d: [ln(d, [(50, 50), (39, 63)], 1.8), el(d, 50, 50, 3.2)], bevel=1.6)
    I.add("B", lambda d: [el(d, 50, 27.5, 1.4), el(d, 50, 72.5, 1.4), el(d, 27.5, 50, 1.4), el(d, 72.5, 50, 1.4)], bevel=1.4, shadow=False)
    return I


def myfiles():
    I = Icon()
    I.add("B", lambda d: (rr(d, 24, 30, 76, 70, 5), rr(d, 24, 25, 44, 38, 4)), bevel=5)
    I.add("S", lambda d: pg(d, [(22, 72), (30, 42), (80, 42), (72, 72)]), bevel=6)
    I.add("R", lambda d: rr(d, 42, 50, 62, 53, 1.5), bevel=1.6, shadow=False)
    return I


def calculator():
    I = Icon()
    I.add("B", lambda d: rr(d, 27, 24, 73, 40, 4), bevel=5)
    def keys(d):
        for r in range(3):
            for c in range(3):
                rr(d, 27 + c * 16.5, 46 + r * 11.5, 27 + c * 16.5 + 13, 46 + r * 11.5 + 8.5, 2.5)
    I.add("S", keys, bevel=3.2)
    I.add("R", lambda d: rr(d, 60, 46, 73, 80, 2.5), bevel=3.2) if False else None
    I.add("R", lambda d: rr(d, 60.0, 46, 73, 54.5, 2.5), bevel=3.2)
    return I


def sinternet():
    I = Icon()
    I.add("B", lambda d: el(d, 50, 50, 21), bevel=14)
    I.add("S", lambda d: ln(d, arcpts(50, 50, 29, 200, 340, 40), 2.6) if False else ln(d, [(21, 56), (35, 62), (50, 64), (65, 62), (79, 56)], 2.0), bevel=1) if False else None
    def orbit(d):
        pts = []
        for i in range(0, 361, 6):
            a = math.radians(i)
            x, y = 29 * math.cos(a), 9 * math.sin(a)
            r = math.radians(-22)
            pts.append((50 + x * math.cos(r) - y * math.sin(r), 50 + x * math.sin(r) + y * math.cos(r)))
        ln(d, pts, 3.8)
    # back half then planet then front half of the ring
    I.layers = []
    I.add("B", lambda d: el(d, 50, 50, 20), bevel=14)
    def front(d):
        pts = []
        for i in range(0, 181, 5):
            a = math.radians(i)
            x, y = 31 * math.cos(a), 10 * math.sin(a)
            r = math.radians(-22)
            pts.append((50 + x * math.cos(r) - y * math.sin(r), 50 + x * math.sin(r) + y * math.cos(r)))
        ln(d, pts, 4.4)
    I.add("R", front, bevel=2.2)
    I.add("S", lambda d: el(d, 43, 42, 3.5), bevel=3.4, shadow=False)
    return I


def chrome():
    I = Icon()
    I.add("S", lambda d: el(d, 50, 50, 28), bevel=10)
    I.add("R", lambda d: pie(d, 50, 50, 28, 210, 330), bevel=8, shadow=False)
    I.add("D", lambda d: pie(d, 50, 50, 28, 330, 450), bevel=8, shadow=False)
    I.add("B", lambda d: el(d, 50, 50, 14.5), bevel=9)
    I.add("S", lambda d: ring(d, 50, 50, 15.5, 13.2), bevel=1.3, shadow=False)
    return I


def gmail():
    I = Icon()
    I.add("S", lambda d: rr(d, 22, 30, 78, 70, 6), bevel=7)
    I.add("R", lambda d: ln(d, [(27, 36), (50, 55), (73, 36)], 5.5), bevel=2.8)
    I.add("B", lambda d: [rr(d, 22, 30, 28, 70, 3), rr(d, 72, 30, 78, 70, 3)], bevel=3, shadow=False)
    return I


def maps():
    I = Icon()
    I.add("S", lambda d: pg(d, [(24, 40), (38, 34), (50, 40), (64, 34), (76, 40), (76, 72), (64, 66), (50, 72), (38, 66), (24, 72)]), bevel=5)
    I.add("B", lambda d: pg(d, [(38, 34), (50, 40), (50, 72), (38, 66)]), bevel=3, shadow=False)
    def pin(d):
        el(d, 50, 36, 14)
        pg(d, [(37, 42), (63, 42), (50, 66)])
        el(d, 50, 36, 5.2, v=0)
    I.add("R", pin, bevel=7)
    return I


def play():
    I = Icon()
    A, B_, C, Dp = (30, 24), (30, 76), (50, 50), None
    I.add("B", lambda d: pg(d, [(30, 24), (30, 76), (50, 50)]), bevel=4)
    I.add("S", lambda d: pg(d, [(30, 24), (50, 50), (62, 38)]), bevel=4)
    I.add("R", lambda d: pg(d, [(30, 76), (50, 50), (62, 62)]), bevel=4)
    I.add("D", lambda d: pg(d, [(62, 38), (50, 50), (62, 62), (76, 50)]), bevel=4)
    return I


def galaxystore():
    I = Icon()
    I.add("S", lambda d: ln(d, arcpts(50, 36, 12, 180, 360), 4.2), bevel=2)
    I.add("B", lambda d: rr(d, 26, 38, 74, 76, 7), bevel=8)
    def star(d):
        pts = []
        for i in range(10):
            r = 11 if i % 2 == 0 else 4.8
            a = math.radians(-90 + i * 36)
            pts.append((50 + r * math.cos(a), 57 + r * math.sin(a)))
        pg(d, pts)
    I.add("R", star, bevel=3.4)
    return I


def notes():
    I = Icon()
    I.add("S", lambda d: rr(d, 28, 24, 72, 77, 6), bevel=7)
    I.add("D", lambda d: [rr(d, 36, 36, 64, 39, 1.5), rr(d, 36, 46, 64, 49, 1.5), rr(d, 36, 56, 55, 59, 1.5)], bevel=1.6, shadow=False)
    I.add("B", lambda d: rr(d, 24, 24, 31, 77, 3), bevel=3.4)
    I.add("R", lambda d: pg(d, [(62, 24), (70, 24), (70, 38), (66, 34), (62, 38)]), bevel=2.5, shadow=False)
    return I


def whatsapp():
    I = Icon()
    I.add("S", lambda d: (el(d, 50, 48, 27), pg(d, [(26, 62), (22, 78), (40, 71)]), el(d, 50, 48, 21.5, v=0)), bevel=4)
    I.add("B", lambda d: el(d, 50, 48, 21.5), bevel=10)
    hs = arcpts(55, 43, 14, 100, 190, 24)
    I.add("S", lambda d: ln(d, hs, 6), bevel=3)
    return I


def telegram():
    I = Icon("B")
    I.add("S", lambda d: pg(d, [(22, 48), (78, 25), (63, 76), (48, 62), (40, 72), (38, 58), (68, 36)]), bevel=4)
    I.add("S", lambda d: pg(d, [(22, 48), (78, 25), (38, 58)]), bevel=4, shadow=False)
    I.add("R", lambda d: pg(d, [(38, 58), (40, 72), (48, 62)]), bevel=2.6, shadow=False)
    return I


def youtube():
    I = Icon()
    I.add("R", lambda d: rr(d, 22, 31, 78, 69, 12), bevel=11)
    I.add("S", lambda d: pg(d, [(43, 40), (43, 60), (62, 50)]), bevel=3.2)
    return I


def _note(d, dx=0, dy=0):
    ln(d, [(55 + dx, 28 + dy), (55 + dx, 62 + dy)], 7)
    el(d, 46 + dx, 63 + dy, 10, 8)
    pg(d, [(55 + dx, 28 + dy), (68 + dx, 36 + dy), (68 + dx, 44 + dy), (55 + dx, 38 + dy)])


def tiktok():
    I = Icon()
    I.add("R", lambda d: _note(d, 2.6, 2.4), bevel=3.6, shadow=False)
    I.add("B", lambda d: _note(d, -2.2, -1.6), bevel=3.6, shadow=False)
    I.add("S", lambda d: _note(d), bevel=3.8)
    return I


def instagram():
    I = Icon()
    I.add("S", lambda d: (rr(d, 24, 24, 76, 76, 16), rr(d, 30.5, 30.5, 69.5, 69.5, 10, 0)), bevel=3.2)
    I.add("B", lambda d: ring(d, 50, 50, 15, 9.6), bevel=2.8)
    I.add("R", lambda d: el(d, 65, 35, 3.6), bevel=3.4)
    return I


def facebook():
    I = Icon("B")
    def f(d):
        rr(d, 47, 38, 59, 80, 3)
        rr(d, 38, 48, 66, 58, 3)
        pg(d, arcpts(63, 38, 16, 180, 285, 30) + arcpts(63, 38, 5, 285, 180, 30))
        d_ = None
    I.add("S", f, bevel=4)
    return I


def spotify():
    I = Icon()
    I.add("B", lambda d: el(d, 50, 50, 28), bevel=18)
    I.add("S", lambda d: [ln(d, arcpts(50, 70, 31, 238, 302, 24), 5.4), ln(d, arcpts(50, 70, 24, 238, 302, 24), 4.6)], bevel=2.4)
    I.add("R", lambda d: ln(d, arcpts(50, 70, 17, 240, 300, 20), 4), bevel=1.9)
    return I


def chatgpt():
    I = Icon()
    def petals(d):
        for i in range(6):
            a = i * 60
            c = rot([(50, 33)], 50, 50, a)[0]
            # stroked elongated loop
            pts = []
            for k in range(0, 361, 10):
                t = math.radians(k)
                x, y = 7.2 * math.cos(t), 15 * math.sin(t)
                pts.append((x, y))
            q = rot([(50 + x, 33 + y) for x, y in pts], 50, 50, a)
            ln(d, q + [q[0]], 2.7)
    I.add("S", petals, bevel=1.4)
    I.add("B", lambda d: el(d, 50, 50, 8), bevel=7)
    I.add("R", lambda d: el(d, 50, 50, 2.6), bevel=2.5, shadow=False)
    return I


def drive():
    I = Icon()
    I.add("S", lambda d: pg(d, [(50, 26), (63, 26), (80, 56), (73, 66), (50, 26)]) if False else pg(d, [(42, 26), (58, 26), (76, 56), (68, 56), (50, 26)]), bevel=3) if False else None
    I.layers = []
    I.add("S", lambda d: pg(d, [(41, 25), (59, 25), (77, 56), (59, 56), (41, 25)]) if False else pg(d, [(41, 25), (58, 25), (76, 55), (67.5, 70), (50, 40)]), bevel=4)
    I.add("B", lambda d: pg(d, [(23, 59), (58, 59), (67.5, 75), (32, 75)]), bevel=4, shadow=False)
    I.add("R", lambda d: pg(d, [(41, 25), (50, 40), (31.5, 70), (23, 59)]), bevel=4, shadow=False)
    return I


def photos():
    I = Icon()
    def petal(d, a, v=255):
        pts = [(50, 50)] + arcpts(50, 33, 17, 90, 270, 20)  # half-disc above, then rotate
        pts = rot([(50 + 17 * math.cos(math.radians(t)), 33 + 17 * math.sin(math.radians(t))) for t in range(-90, 91, 9)] + [(50, 50)], 50, 50, a)
        pg(d, pts, v)
    mats = ["S", "B", "R", "D"]
    for i, m in enumerate(mats):
        I.add(m, (lambda ang: (lambda d: petal(d, ang)))(i * 90), bevel=7, shadow=(i == 0 or True))
    return I


def wallet():
    I = Icon()
    I.add("B", lambda d: rr(d, 24, 30, 76, 66, 8), bevel=8)
    I.add("S", lambda d: pg(d, [(24, 44), (24, 38), (30, 34), (70, 34), (76, 40), (76, 44), (24, 44)]) if False else rr(d, 24, 40, 76, 72, 8), bevel=7)
    I.add("D", lambda d: rr(d, 58, 50, 76, 62, 6), bevel=4, shadow=False)
    I.add("R", lambda d: el(d, 65, 56, 2.8), bevel=2.6, shadow=False)
    return I


def goodlock():
    I = Icon()
    I.add("S", lambda d: ln(d, arcpts(50, 44, 14, 180, 360, 40) + [(64, 52)] , 6.5) if False else (ln(d, [(36, 50), (36, 42)] + arcpts(50, 42, 14, 180, 360, 40) + [(64, 50)], 6.5)), bevel=3)
    I.add("B", lambda d: rr(d, 28, 48, 72, 78, 9), bevel=10)
    I.add("R", lambda d: (el(d, 50, 60, 5.2), pg(d, [(47, 62), (53, 62), (55, 72), (45, 72)])), bevel=3.2)
    return I


GLYPHS = {
    "phone": phone, "contacts": contacts, "samsung_messages": smessages, "google_messages": gmessages,
    "camera": camera, "gallery": gallery, "settings": settings, "calendar": calendar, "clock": clock,
    "my_files": myfiles, "calculator": calculator, "samsung_internet": sinternet, "chrome": chrome,
    "gmail": gmail, "maps": maps, "play_store": play, "galaxy_store": galaxystore, "samsung_notes": notes,
    "whatsapp": whatsapp, "telegram": telegram, "youtube": youtube, "tiktok": tiktok, "instagram": instagram,
    "facebook": facebook, "spotify": spotify, "chatgpt": chatgpt, "google_drive": drive,
    "google_photos": photos, "samsung_wallet": wallet, "good_lock": goodlock,
}


def rob():
    I = Icon()
    I.add("S", lambda d: txt(d, "R", 50, 49, 52), bevel=4.5)
    I.add("R", lambda d: rr(d, 33, 71, 67, 74.5, 1.7), bevel=1.7, shadow=False)
    return I
