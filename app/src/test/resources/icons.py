# icons.xlsx: icon sets on A2:A11 (1..10, arrows), B (traffic lights), C (symbols, values hidden), D (flags), E (4Rating).
import sys
from openpyxl import Workbook
from openpyxl.formatting.rule import IconSetRule
out = sys.argv[1]
wb = Workbook(); ws = wb.active; ws.title = "Icons"
ws.append(["arrows", "lights", "symbols", "flags", "rating"])
for i in range(1, 11): ws.append([i, i, i, i, i])
ws.conditional_formatting.add("A2:A11", IconSetRule("3Arrows", "percent", [0, 33, 67]))
ws.conditional_formatting.add("B2:B11", IconSetRule("3TrafficLights1", "percent", [0, 33, 67]))
ws.conditional_formatting.add("C2:C11", IconSetRule("3Symbols", "percent", [0, 33, 67], showValue=False))
ws.conditional_formatting.add("D2:D11", IconSetRule("3Flags", "num", [0, 4, 8]))
ws.conditional_formatting.add("E2:E11", IconSetRule("4Rating", "percent", [0, 25, 50, 75]))
wb.save(out)
