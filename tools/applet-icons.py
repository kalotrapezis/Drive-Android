# Usage: python3 tools/applet-icons.py app/src/main/res <folder with DocScan.png, QRScan.png, Files.png>
# The applets' launcher icons from Home's own art, on Tetra's dark tile as the main icon (content at its cube's size).
import sys, os, glob
from PIL import Image, ImageDraw
res, dl = sys.argv[1], sys.argv[2]
nodpi = f"{res}/drawable-nodpi"
# the user's new tiles (17 Oct): their solid corners cut away (flood from each corner), at the other arts' size
for src, out in [("DocScan", "scanner_art"), ("QRScan", "codes_art"), ("Files", "files_art")]:
    a = Image.open(f"{dl}/{src}.png").convert("RGBA")
    for c in [(0, 0), (a.width - 1, 0), (0, a.height - 1), (a.width - 1, a.height - 1)]:
        ImageDraw.floodfill(a, c, (0, 0, 0, 0), thresh=60)
    a.resize((627, 627), Image.LANCZOS).save(f"{nodpi}/{out}.png", optimize=True)
apps = [("photos", "gallery_art"), ("files", "files_art"), ("notes", "notes_art"), ("scanner", "scanner_art"),
        ("codes", "codes_art"), ("office", "office_art"), ("text_editor", "text_editor_art")]
for f in glob.glob(f"{res}/drawable/applet_*_fg.xml"): os.remove(f)
if os.path.exists(f"{res}/values/applet_colors.xml"): os.remove(f"{res}/values/applet_colors.xml")
for name, art in apps:
    a = Image.open(f"{nodpi}/{art}.png").convert("RGBA")
    a = a.crop(a.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox())
    k = 0.50 * 432 / max(a.size)  # the main icon's cube spans 47% of its 432 px foreground; a tile reads a little smaller
    a = a.resize((round(a.width * k), round(a.height * k)), Image.LANCZOS)
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0)); fg.alpha_composite(a, ((432 - a.width) // 2, (432 - a.height) // 2))
    fg.save(f"{nodpi}/applet_{name}_fg.png", optimize=True)
    open(f"{res}/mipmap-anydpi-v26/applet_{name}.xml", "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Settings › App icons: Home\'s art for this part on Tetra\'s own dark tile (tools: gen from the art, 17 Oct). -->\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        f'    <foreground android:drawable="@drawable/applet_{name}_fg" />\n</adaptive-icon>\n')
