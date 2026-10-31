"""Check every component in apps.py against the two reference databases. Exit 1 on any miss."""
import sys, os, re
sys.path.insert(0, os.path.dirname(__file__))
from apps import APPS
src = sys.argv[1]
db = {}
for name in ("lawnicons", "arctic"):
    txt = open(os.path.join(src, name + ".xml"), encoding="utf-8", errors="ignore").read()
    db[name] = set(re.findall(r"ComponentInfo\{([^}]+)\}", txt))
def has(d, pkg, cls):
    return f"{pkg}/{cls}" in d or (cls.startswith(pkg) and f"{pkg}/{cls[len(pkg):]}" in d)
bad = 0; rows = []
for k, (_, _, pk) in APPS.items():
    for pkg, classes in pk:
        for c in classes:
            a, b = has(db["lawnicons"], pkg, c), has(db["arctic"], pkg, c)
            rows.append((k, pkg, c, "both" if a and b else "lawnicons" if a else "arcticons" if b else "NONE"))
            bad += not (a or b)
with open(os.path.join(os.path.dirname(__file__), "..", "docs", "COMPONENT_SOURCES.md"), "w") as f:
    f.write("# Component verification\n\nEach launcher activity below was found in the named public appfilter database(s):\n"
            "[Lawnicons](https://github.com/LawnchairLauncher/lawnicons) and [Arcticons](https://github.com/Donnnno/Arcticons).\n"
            "This is community evidence, **not** an on-device check; use the in-app Diagnostics screen on your phone.\n\n"
            "| icon | package | activity | found in |\n|---|---|---|---|\n")
    for r in rows: f.write("| %s | `%s` | `%s` | %s |\n" % r)
print(len(rows), "components,", bad, "missing")
for r in rows:
    if r[3] == "NONE": print("MISSING", r)
sys.exit(1 if bad else 0)
