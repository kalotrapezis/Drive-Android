# Usage: python3 tools/file-icons.py app/src/main/res Assets [../Drive-worktrees/…/desktop]
# Files' category icons (the user, 24 Oct: "icons for file types … so the user can see at a glance what file it is, on
# phone or desktop"), from the user's tiles in Assets: corners cut away, 256 px, as drawable-nodpi/kind_*.png and, given
# the desktop's folder, as desktop/public/kinds/*.png. Which extensions each one stands for: app/src/main/resources/file-kinds.json.
import sys, os, json
from PIL import Image, ImageDraw
res, assets = sys.argv[1], sys.argv[2]
desktop = sys.argv[3] if len(sys.argv) > 3 else None
here = os.path.dirname(os.path.abspath(__file__))
kinds = json.load(open(os.path.join(here, "..", "app", "src", "main", "resources", "file-kinds.json")))["kinds"]
for kind in kinds.values():
    icon = kind["icon"]
    # each tile fills its picture (one, the scripts', to its very corners): one rounded corner for all, a bit inside the roundest
    a = Image.open(f"{assets}/{icon}.png").convert("RGBA").resize((256, 256), Image.LANCZOS)
    mask = Image.new("L", (1024, 1024), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 1023, 1023), radius=round(1024 * 0.23), fill=255)
    a.putalpha(mask.resize((256, 256), Image.LANCZOS))
    a.save(f"{res}/drawable-nodpi/kind_{icon.lower().replace("-", "_")}.png", optimize=True)
    if desktop:
        os.makedirs(f"{desktop}/public/kinds", exist_ok=True)
        a.save(f"{desktop}/public/kinds/{icon.lower()}.png", optimize=True)
