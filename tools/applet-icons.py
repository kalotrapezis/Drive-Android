# Usage: python3 tools/applet-icons.py app/src/main/res <folder with DocScan.png, QRScan.png, Files.png, Calendar.png, Maps.png, Timer.png, Sync.png, Settings.png — any of them> (Assets/Icons/Applets)
# The applets' launcher icons from Home's own art, each on its own colour (asked 17 Oct: the corners in the applet's colour).
import sys, os, glob
from PIL import Image, ImageDraw
res, dl = sys.argv[1], sys.argv[2]
nodpi = f"{res}/drawable-nodpi"
# the user's new tiles (17 Oct): their solid corners cut away (flood from each corner), at the other arts' size
for src, out in [("DocScan", "scanner_art"), ("QRScan", "codes_art"), ("Files", "files_art"), ("Calendar", "calendar_art"), ("Contacts", "contacts_art"), ("Maps", "maps_art"), ("Timer", "timer_art"), ("Sync", "sync_art"), ("Settings", "settings_art")]:
    if not os.path.exists(f"{dl}/{src}.png"): continue  # only the tiles given are made again
    a = Image.open(f"{dl}/{src}.png").convert("RGBA")
    for c in [(0, 0), (a.width - 1, 0), (0, a.height - 1), (a.width - 1, a.height - 1)]:
        ImageDraw.floodfill(a, c, (0, 0, 0, 0), thresh=60)
    a.resize((627, 627), Image.LANCZOS).save(f"{nodpi}/{out}.png", optimize=True)
apps = [("photos", "gallery_art"), ("files", "files_art"), ("notes", "notes_art"), ("scanner", "scanner_art"),
        ("codes", "codes_art"), ("office", "office_art"), ("text_editor", "text_editor_art"), ("calendar", "calendar_art"), ("contacts", "contacts_art"), ("maps", "maps_art"), ("timer", "timer_art")]
for f in glob.glob(f"{res}/drawable/applet_*_fg.xml"): os.remove(f)
FALLBACK = {"notes": (0xF2, 0xB7, 0x05)}  # Home's Notes yellow (Category.Notes)
colors = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- The applet icons' backgrounds: each art's own edge colour, so its tile's corners blend in (tools/applet-icons.py). -->", "<resources>"]
for name, art in apps:
    a = Image.open(f"{nodpi}/{art}.png").convert("RGBA")
    a = a.crop(a.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox())
    # the colour just inside the tile's four edges (their middles); Notes' paper has no tile of its own: Home's yellow
    w, h = a.size; i = round(min(w, h) * 0.05)
    edge = [a.getpixel(p) for p in [(w // 2, i), (w // 2, h - 1 - i), (i, h // 2), (w - 1 - i, h // 2)]]
    solid = [p for p in edge if p[3] > 200]
    bg = FALLBACK.get(name) or tuple(sorted(p[c] for p in solid)[len(solid) // 2] for c in range(3))
    colors.append(f'    <color name="applet_{name}">#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}</color>')
    # a tile just inside the launcher's visible 72 dp of 108 (the colour around it is its own); Notes' bare paper smaller, as the main icon's cube
    k = (0.50 if name in FALLBACK else 0.62) * 432 / max(a.size)
    a = a.resize((round(a.width * k), round(a.height * k)), Image.LANCZOS)
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0)); fg.alpha_composite(a, ((432 - a.width) // 2, (432 - a.height) // 2))
    fg.save(f"{nodpi}/applet_{name}_fg.png", optimize=True)
    open(f"{res}/mipmap-anydpi-v26/applet_{name}.xml", "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Settings › App icons: Home\'s art for this part on its own colour (tools/applet-icons.py). -->\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        f'    <background android:drawable="@color/applet_{name}" />\n'
        f'    <foreground android:drawable="@drawable/applet_{name}_fg" />\n</adaptive-icon>\n')
colors.append("</resources>")
open(f"{res}/values/applet_colors.xml", "w").write("\n".join(colors) + "\n")
