# drawings.xlsx: a small table, a column chart of it anchored E2:L16, a picture at B9 (oneCellAnchor-like), a text box.
import sys
from openpyxl import Workbook
from openpyxl.chart import BarChart, Reference
from openpyxl.drawing.image import Image
from PIL import Image as PI, ImageDraw
out = sys.argv[1]
wb = Workbook(); ws = wb.active; ws.title = "Chart"
rows = [("Month", "Sales", "Costs"), ("Jan", 120, 80), ("Feb", 150, 95), ("Mar", 90, 70), ("Apr", 180, 110)]
for r in rows: ws.append(r)
ch = BarChart(); ch.title = "Sales and costs"
ch.add_data(Reference(ws, min_col=2, min_row=1, max_col=3, max_row=5), titles_from_data=True)
ch.set_categories(Reference(ws, min_col=1, min_row=2, max_row=5))
ch.width = 12; ch.height = 7
ws.add_chart(ch, "E2")
im = PI.new("RGB", (120, 80), (16, 124, 65)); d = ImageDraw.Draw(im); d.ellipse((30, 10, 90, 70), fill=(255, 255, 255))
p = out.replace(".xlsx", ".png"); im.save(p)
ws.add_image(Image(p), "B9")
wb.save(out)
