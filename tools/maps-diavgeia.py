#!/usr/bin/env python3
"""ΑμεΑ parking spots from the municipalities' own decisions on Διαύγεια (diavgeia.gov.gr, open API, no account), all of
Greece (the user, 21 Oct: "Continue the same way for all over Greece add what you are certain of and I will check (give
links) the rest").

1. Every decision whose subject speaks of an ΑμεΑ parking spot, half a year at a time from 2012 (the API answers a few
   months per question), written to diavgeia-amea.tsv:  org <TAB> org label <TAB> date <TAB> ada <TAB> subject
2. Each general (not exclusive to one person's car), not abolished spot with a street and number is looked up in
   greece-addresses.tsv near its municipality's town: found → greece-amea-diavgeia.tsv (the search list's form, kind
   disabled_parking, the decision's link in the last field); not found → diavgeia-amea-check.tsv (to be placed by hand).

Usage: maps-diavgeia.py <maps dir>   (fetches when diavgeia-amea.tsv is older than a week or missing)
"""
import datetime
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

API = "https://diavgeia.gov.gr/opendata"
TERMS = ("στάθμευσης ΑμεΑ", "στάθμευση ΑμεΑ", "στάθμευσης αναπηρία", "θέσεων ΑμεΑ", "θέσης ΑμεΑ")
LINK = "https://diavgeia.gov.gr/decision/view/"


def get(path, params):
    url = API + path + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "Tetra maps-diavgeia"})
    for attempt in range(4):
        try:
            time.sleep(0.3 + 5 * attempt)
            with urllib.request.urlopen(req, timeout=120) as r:
                return json.loads(r.read())
        except OSError:
            if attempt == 3:
                raise


def amea_parking(subject):
    s = subject.lower()
    return ("στάθμευ" in s or "ρ-72" in s or "ρ72" in s) and any(k in s for k in ("αμεα", "α.μ.ε.α", "αναπηρ", "ρ-72", "ρ72"))


def window(start, end):
    got = {}
    for term in TERMS:
        page = 0
        while True:
            d = get("/search", {"subject": term, "from_issue_date": start.isoformat(), "to_issue_date": end.isoformat(), "size": 100, "page": page})
            for x in d.get("decisions", []):
                if amea_parking(x.get("subject", "")):
                    got[x["ada"]] = (x.get("organizationId", ""), datetime.date.fromtimestamp(x["issueDate"] / 1000).isoformat(), x["ada"],
                                     " ".join(x.get("subject", "").split()))
            page += 1
            if page * 100 >= d.get("info", {}).get("total", 0) or page >= 30:
                break
    return got


def fetch(path):
    orgs = {o["uid"]: o["label"] for o in get("/organizations", {"status": "active"}).get("organizations", [])}
    jobs, start = [], datetime.date(2012, 1, 1)
    while start < datetime.date.today():
        end = min(start + datetime.timedelta(days=90), datetime.date.today())
        jobs.append((start, end)); start = end + datetime.timedelta(days=1)
    found = {}
    with ThreadPoolExecutor(6) as pool:
        for got in pool.map(lambda j: window(*j), jobs):
            found.update(got)
    with open(path + ".part", "w", encoding="utf-8") as f:
        for org, date, ada, subj in sorted(found.values(), key=lambda r: (r[0], r[1])):
            f.write(f"{org}\t{orgs.get(org, '')}\t{date}\t{ada}\t{subj}\n")
    os.replace(path + ".part", path)
    print(f"{len(found)} decisions", file=sys.stderr)


def fold(s):
    return "".join(c for c in unicodedata.normalize("NFD", s.lower()) if unicodedata.category(c) != "Mn").replace("ς", "σ").strip(" .,")


# a spot reserved for one person's car (Ρ-72 for "του Π.Μ.", a plate), or a request, abolition, move-away: not a public spot
PERSONAL = re.compile(r"αποκλειστικ|αίτηση|αιτηση|κατάργηση|καταργηση|αριθμ[όο] κυκλοφορ|\b[Α-Ω]\.[Α-Ω]\.|ρ-?72|ανάκληση|ανακληση|μεταφορ[άα]ς? .*από", re.I)
STREET = re.compile(r"(?:οδ[όο]ύ?|οδών|παρόδου|λεωφόρου|λεωφ\.|πλατείας)\s+([^,()«»\"]+?)\s+(?:αρ\.?\s*)?(\d{1,4}[Α-Ωα-ωA-Za-z]?)(?=$|[\s,.)»\"-])")


def town_of(label, settlements):
    """Where a municipality is: its name ("ΔΗΜΟΣ ΞΑΝΘΗΣ" → Ξάνθη) among the towns, by the genitive's stem."""
    name = fold(re.sub(r"^(δημοσ|δ\.)\s+", "", fold(label)))
    if not name:
        return None
    stem = name[:max(4, len(name) - 2)]
    best = [s for s in settlements if s[0].startswith(stem)]
    best.sort(key=lambda s: ("city", "town", "village", "suburb").index(s[3]) if s[3] in ("city", "town", "village", "suburb") else 9)
    return best[0] if best else None


def place(maps):
    settlements = []
    for line in open(os.path.join(maps, "greece-places.tsv"), encoding="utf-8"):
        c = line.split("\t")
        if c[0] in ("city", "town", "village", "suburb") and c[3]:
            settlements.append((fold(c[3]), float(c[1]), float(c[2]), c[0], c[3]))
    addresses = {}
    for line in open(os.path.join(maps, "greece-addresses.tsv"), encoding="utf-8"):
        c = line.rstrip("\n").split("\t")
        addresses.setdefault((fold(c[2]), c[3].lower().replace(" ", "")), []).append((float(c[0]), float(c[1])))
    placed, check, skipped = [], [], 0
    for line in open(os.path.join(maps, "diavgeia-amea.tsv"), encoding="utf-8"):
        org, label, date, ada, subj = line.rstrip("\n").split("\t")
        if PERSONAL.search(subj):
            skipped += 1
            continue
        town = town_of(label, settlements)
        hits = list(STREET.finditer(subj))
        if not hits:
            check.append((label, date, "", subj, LINK + ada)); continue
        for m in hits:
            street, number = m.group(1).strip(), m.group(2).lower()
            near = [p for p in addresses.get((fold(street), number), [])
                    if town is None or abs(p[0] - town[1]) < 0.15 and abs(p[1] - town[2]) < 0.2]
            if len(near) == 1 or (near and max(abs(a[0] - near[0][0]) + abs(a[1] - near[0][1]) for a in near) < 0.0005):
                la, lo = near[0]
                more = f"{street} {m.group(2)}, {town[4] if town else label}"
                placed.append(f"disabled_parking\t{la:.6f}\t{lo:.6f}\tΑμεΑ parking\t\t{more}\t\t\t{LINK + ada}\t\t{label} ({date})\n")
            else:
                check.append((label, date, f"{street} {m.group(2)}", subj, LINK + ada))
    with open(os.path.join(maps, "greece-amea-diavgeia.tsv"), "w", encoding="utf-8") as f:
        f.writelines(placed)
    with open(os.path.join(maps, "diavgeia-amea-check.tsv"), "w", encoding="utf-8") as f:
        f.write("municipality\tdate\taddress\tsubject\tlink\n")
        f.writelines("\t".join(r) + "\n" for r in check)
    print(f"placed {len(placed)}, to check {len(check)}, personal/abolished/requests left out {skipped}", file=sys.stderr)


if __name__ == "__main__":
    maps = sys.argv[1]
    raw = os.path.join(maps, "diavgeia-amea.tsv")
    if not os.path.isfile(raw) or time.time() - os.path.getmtime(raw) > 7 * 86400 or "--fetch" in sys.argv:
        fetch(raw)
    place(maps)
