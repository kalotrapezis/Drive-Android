#!/usr/bin/env python3
"""
A's touch-target check (Plan.md › A › Touch points): on a device with Tetra Office open on a file, editing, taps each
ribbon tab in turn, dumps the screen (uiautomator) and lists every tappable thing smaller than 48 dp across or down.

    tools/touch-targets.py <device> <tab> [<tab> …]      e.g. tools/touch-targets.py emulator-5554 Home Insert Draw

Prints one line per small target: tab, its label (or content description), its size in dp. Things at the screen's very
edge are marked "edge" (Android's back gesture is there). Nothing is changed on the device but the tab shown.
"""
import re, subprocess, sys, time, os

ADB = os.path.expanduser("~/Android/Sdk/platform-tools/adb")

def adb(dev, *args):
    return subprocess.run([ADB, "-s", dev, *args], capture_output=True, text=True).stdout

def dump(dev):
    adb(dev, "shell", "uiautomator", "dump", "/sdcard/tt.xml")
    return adb(dev, "shell", "cat", "/sdcard/tt.xml")

def nodes(xml):
    for m in re.finditer(r"<node [^>]*>", xml):
        n = m.group(0)
        a = dict(re.findall(r'(\w[\w-]*)="([^"]*)"', n))
        b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
        if b: a["box"] = tuple(map(int, b.groups()))
        yield a

def main():
    dev, tabs = sys.argv[1], sys.argv[2:]
    density = int(re.search(r"(\d+)", adb(dev, "shell", "wm", "density")).group(1)) / 160
    width = int(re.search(r"(\d+)x(\d+)", adb(dev, "shell", "wm", "size")).group(1))
    small = 0
    for tab in tabs or ["(as shown)"]:
        if tab != "(as shown)":
            hit = next((n for n in nodes(dump(dev)) if n.get("text") == tab or n.get("content-desc") == tab), None)
            if not hit: print(f"{tab}: no such tab on screen"); continue
            x0, y0, x1, y1 = hit["box"]; adb(dev, "shell", "input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2)); time.sleep(1.2)
        seen = set()
        everything = list(nodes(dump(dev)))
        def inside(n):
            x0, y0, x1, y1 = n["box"]
            return next((k.get("text") or k.get("content-desc") for k in everything if "box" in k and (k.get("text") or k.get("content-desc"))
                         and k["box"][0] >= x0 and k["box"][1] >= y0 and k["box"][2] <= x1 and k["box"][3] <= y1), None)
        for n in everything:
            if n.get("clickable") != "true" or "box" not in n: continue
            x0, y0, x1, y1 = n["box"]; w = (x1 - x0) / density; h = (y1 - y0) / density
            if w <= 0 or h <= 0 or (w >= 48 and h >= 48): continue
            # the label: its own words, else the first child's with words (Compose puts a button's text in a child)
            label = n.get("text") or n.get("content-desc") or inside(n) or "?"
            key = (label, x0, y0)
            if key in seen: continue
            seen.add(key); small += 1
            edge = " edge" if x0 < 8 * density or x1 > width - 8 * density else ""
            print(f"{tab}\t{label[:40]}\t{w:.0f}×{h:.0f} dp{edge}")
    print(f"{small} small targets")

if __name__ == "__main__":
    main()
