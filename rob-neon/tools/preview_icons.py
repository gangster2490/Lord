import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image
from glyphs import GLYPHS
ids = sys.argv[2:] or list(GLYPHS)
out = sys.argv[1]
cols = 6
size = 256
rows = (len(ids) + cols - 1) // cols
sheet = Image.new("RGB", (cols * size, rows * size), (20, 22, 26))
for i, k in enumerate(ids):
    im = GLYPHS[k]().render(size)
    sheet.paste(im, ((i % cols) * size, (i // cols) * size), im)
sheet.save(out)
