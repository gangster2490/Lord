"""Generate every raster/resource of the icon pack. Usage: python3 build_assets.py <project_root>"""
import sys, os, json
sys.path.insert(0, os.path.dirname(__file__))
from concurrent.futures import ProcessPoolExecutor
from PIL import Image
import glyphs, wallpapers
from apps import APPS

root = os.path.abspath(sys.argv[1])
RES = f"{root}/app/src/main/res"
AST = f"{root}/app/src/main/assets"
DIST = f"{root}/dist"
for d in (f"{RES}/drawable-nodpi", f"{RES}/xml", f"{RES}/mipmap-xxxhdpi", AST + "/wallpapers", DIST + "/icons", DIST + "/wallpapers"):
    os.makedirs(d, exist_ok=True)


def icon_job(k):
    im = glyphs.GLYPHS[k]().render(512)
    im.save(f"{DIST}/icons/{k}.png", optimize=True)
    im.save(f"{RES}/drawable-nodpi/ic_{k}.png", optimize=True)
    return k


def wp_job(kind):
    im = wallpapers.render(kind, 1440, ss=2)
    assert im.size == (1440, 3120)
    im.save(f"{DIST}/wallpapers/rob_neon_{kind}.jpg", quality=95, subsampling=0)
    im.save(f"{AST}/wallpapers/{kind}.jpg", quality=92)
    return kind


def launcher_job(_):
    ic = glyphs.rob().render(432)
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    s = int(432 * 0.64)
    t = ic.resize((s, s), Image.LANCZOS)
    fg.paste(t, ((432 - s) // 2, (432 - s) // 2), t)
    fg.save(f"{RES}/mipmap-xxxhdpi/ic_launcher_foreground.png", optimize=True)
    return "launcher"


if __name__ == "__main__":
    with ProcessPoolExecutor(4) as ex:
        futs = [ex.submit(wp_job, k) for k in ("home", "lock")] + [ex.submit(launcher_job, 0)] + [ex.submit(icon_job, k) for k in APPS]
        for f in futs:
            print("done", f.result(), flush=True)

    # appfilter.xml (ADW / Nova / Lawnchair standard), drawable.xml, catalog.json
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>", '    <iconback img1="" />' if False else "", ]
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>", '    <scale factor="1.0" />']
    cat = []
    for k, (ru, de, pk) in APPS.items():
        comps = []
        for pkg, classes in pk:
            for c in classes:
                comps.append(f"{pkg}/{c}")
                lines.append(f'    <item component="ComponentInfo{{{pkg}/{c}}}" drawable="ic_{k}" />')
        cat.append({"id": k, "ru": ru, "de": de, "packages": [p for p, _ in pk], "components": comps})
    lines.append("</resources>")
    xml = "\n".join(lines) + "\n"
    open(f"{RES}/xml/appfilter.xml", "w").write(xml)
    open(f"{AST}/appfilter.xml", "w").write(xml)
    dr = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>", '    <version>1</version>', '    <category title="ROB Neon" />']
    dr += [f'    <item drawable="ic_{k}" />' for k in APPS] + ["</resources>"]
    open(f"{RES}/xml/drawable.xml", "w").write("\n".join(dr) + "\n")
    open(f"{AST}/drawable.xml", "w").write("\n".join(dr) + "\n")
    json.dump(cat, open(f"{AST}/catalog.json", "w"), ensure_ascii=False, indent=1)
    print("resources written")
