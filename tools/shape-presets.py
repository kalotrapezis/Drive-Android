#!/usr/bin/env python3
"""Word's preset shapes (DrawingML's presetShapeDefinitions, as LibreOffice ships them in
/usr/lib/libreoffice/share/filter/oox-drawingml-cs-presets, MPL 2.0) made compact for Writer:
app/src/main/resources/shapes/presets.txt — one shape a line, tab-separated:
  name, adjustments (name=value,…), equations (;), path coordinates (x,y pairs: 12 / ?3 / $0), segments (cmd:count,…),
  sub-view sizes (w:h,…), text frame (x,y,x,y)
Run: python3 tools/shape-presets.py [presets file]"""
import re, sys
src = sys.argv[1] if len(sys.argv) > 1 else "/usr/lib/libreoffice/share/filter/oox-drawingml-cs-presets"
s = open(src, encoding="utf-8").read()
names = [m.group(1) for m in re.finditer(r"^/\* (\w+) \*/$", s, re.M)]
starts = [m.start() for m in re.finditer(r"^/\* (\w+) \*/$", s, re.M)] + [len(s)]
P = r"\{ Value = \(any\) \{ \(long\) (-?\d+) \}, Type = \(short\) (\d+) \}"
def param(v, t): return {"0": v, "1": "?" + v, "2": "$" + v}.get(t, v)
out = ["# Word's preset shapes from LibreOffice's oox-drawingml-cs-presets (MPL 2.0): tools/shape-presets.py"]
for k, name in enumerate(names):
    e = s[starts[k]:starts[k + 1]]
    adj = ",".join(f"{n}={v}" for v, n in re.findall(r'\(long\) (-?\d+) \}, State = \(com\.sun\.star\.beans\.PropertyState\) DIRECT_VALUE, Name = "(\w+)"', re.search(r"AdjustmentValues\n(.*)\n", e).group(1)))
    eqs = ";".join(x.strip() for x in re.findall(r'"([^"]*)"', re.search(r"Equations\n(.*)\n", e).group(1)))
    path = re.search(r"\nPath\n(.*)\n", e).group(1)
    def prop(n):
        m = re.search(r'Name = "' + n + r'", Handle = \(long\) 0, Value = \(any\) \{ (.*?) \}, State', path)
        return m.group(1) if m else ""
    coords = " ".join(f"{param(a, b)},{param(c, d)}" for a, b, c, d in re.findall(r"First = \(com\.sun\.star\.drawing\.EnhancedCustomShapeParameter\) " + P + r", Second = \(com\.sun\.star\.drawing\.EnhancedCustomShapeParameter\) " + P, prop("Coordinates")))
    segs = ",".join(f"{c}:{n}" for c, n in re.findall(r"Command = \(short\) (\d+), Count = \(short\) (\d+)", prop("Segments")))
    subs = ",".join(f"{w}:{h}" for w, h in re.findall(r"Width = \(long\) (-?\d+), Height = \(long\) (-?\d+)", prop("SubViewSize")))
    tf = re.findall(P, prop("TextFrames"))[:4]
    tfs = ",".join(param(v, t) for v, t in tf)
    out.append("\t".join([name, adj, eqs, coords, segs, subs, tfs]))
open("app/src/main/resources/shapes/presets.txt", "w", encoding="utf-8").write("\n".join(out) + "\n")
print(len(names), "shapes")
