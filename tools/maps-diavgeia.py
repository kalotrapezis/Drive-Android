#!/usr/bin/env python3
"""ΑμεΑ parking spots from the municipalities' own decisions on Διαύγεια (diavgeia.gov.gr, open API, no account), all of
Greece (the user, 21 Oct: "Continue the same way for all over Greece add what you are certain of and I will check (give
links) the rest").

1. Every decision whose subject speaks of an ΑμεΑ parking spot, half a year at a time from 2012 (the API answers a few
   months per question), written to diavgeia-amea.tsv:  org <TAB> org label <TAB> date <TAB> ada <TAB> subject
2. Each public (created, set out; not given to one person's car), not abolished spot with a street and number is looked up
   in greece-addresses.tsv near its municipality's town: found → greece-amea-diavgeia.tsv (the search list's form, kind
   disabled_parking, the decision's link in the web field); not found → greece-amea-check.tsv, the app's list of spots to
   place by hand (Maps › Saved › ΑμεΑ to place):  id <TAB> lat <TAB> lng <TAB> town <TAB> address <TAB> subject
   <TAB> link <TAB> date, at the street's middle (greece-streets.tsv) when its name is known there, else the town's.

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


# Most decisions give a spot to one person (outside the home, for one car: "παραχώρηση … της κ. …", a plate): not public, left
# out. A public spot is created, set or laid out (δημιουργία, καθορισμός, χωροθέτηση, καθιέρωση, a "γενική θέση").
PERSONAL = re.compile(r"αποκλειστικ|ατομικ|παραχ[ωώ]ρ|αίτη|αιτη|κατ[αά]ργ|αφα[ιί]ρεσ|αν[αά]κλησ|μη έγκριση|ή μη|εξ[εέ]ταση|επικαιροπο|"
                      r"κυκλ|[οό]χημ|αυτοκ[ιί]νητ|του κ\.|της κ\.|\bκας\b|\bκου\b|δικαιο[υύ]χ|οικ[ιί]α|κατοικ|ρ-?72|μεταφορ|\b[Α-Ω]\. ?[Α-Ω]\.", re.I)
PUBLIC = re.compile(r"δημιουργ|καθορισμ|χωροθ[εέ]τ|καθι[εέ]ρωσ|γενικ|διαμ[οό]ρφωσ|δημιουρ", re.I)
NAMED = re.compile(r"(?:οδ[όο]ύ?|οδών|παρόδου|λεωφόρου|λεωφ\.)\s+([Α-ΩΆ-Ώ][^,()«»\"]{2,40}?)(?=\s+(?:στην|στη|στο|στον|της|του|έναντι|έμπροσθεν|μπροστά|και|ως|$)|[,.()«»\"]|$)")
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
    streets = {}
    path = os.path.join(maps, "greece-streets.tsv")
    if os.path.isfile(path):
        for line in open(path, encoding="utf-8"):
            c = line.rstrip("\n").split("\t")
            if len(c) == 3:
                streets.setdefault(fold(c[0]), []).append((float(c[1]), float(c[2])))
    addresses = {}
    for line in open(os.path.join(maps, "greece-addresses.tsv"), encoding="utf-8"):
        c = line.rstrip("\n").split("\t")
        addresses.setdefault((fold(c[2]), c[3].lower().replace(" ", "")), []).append((float(c[0]), float(c[1])))
    placed, check, skipped, seen = [], [], 0, set()

    def street_at(street, town):
        """The street's piece nearest the town's middle, within 12 km: by its name, else by a name holding its words
        ("Πατριάρχου Κυρίλλου" in "Πατριάρχου Κυρίλλου Ε΄"), else by its last word when that is long and sure."""
        def near(names):
            pieces = [p for n in names for p in streets.get(n, []) if abs(p[0] - town[1]) < 0.11 and abs(p[1] - town[2]) < 0.14]
            return min(pieces, key=lambda p: (p[0] - town[1]) ** 2 + (p[1] - town[2]) ** 2) if pieces else None
        f = fold(street)
        hit = near([f])
        if hit or len(f) < 4:
            return hit
        words = [w for w in re.split(r"[\s.]+", f) if len(w) >= 3]
        local = [n for n in local_names(town) if all(w in n for w in words)]
        if not local and words and len(words[-1]) >= 6:
            local = [n for n in local_names(town) if words[-1] in n.split()]
        return near(local[:3]) if 0 < len(local) <= 3 else None

    town_names = {}
    def local_names(town):
        if town[4] not in town_names:
            town_names[town[4]] = [n for n, ps in streets.items() if any(abs(p[0] - town[1]) < 0.11 and abs(p[1] - town[2]) < 0.14 for p in ps)]
        return town_names[town[4]]
    for line in open(os.path.join(maps, "diavgeia-amea.tsv"), encoding="utf-8"):
        org, label, date, ada, subj = line.rstrip("\n").split("\t")
        # only the municipalities' own (a region's review of one repeats it), only public spots
        if not label.startswith("ΔΗΜΟΣ") or PERSONAL.search(subj) or not PUBLIC.search(subj):
            skipped += 1
            continue
        town = town_of(label, settlements)
        if town is None:
            continue  # nowhere to show it
        hits = list(STREET.finditer(subj))
        if not hits:
            # a street without a number: shown at the street; nothing named: at the town
            named = NAMED.search(subj)
            street = named.group(1).strip() if named else ""
            at = street_at(street, town) if street else None
            check.append((ada, (at or town[1:3])[0], (at or town[1:3])[1], town[4], f"{street}, {town[4]}" if at else town[4], subj, LINK + ada, date)); continue
        for m in hits:
            street, number = m.group(1).strip(), m.group(2).lower()
            near = [p for p in addresses.get((fold(street), number), []) if abs(p[0] - town[1]) < 0.15 and abs(p[1] - town[2]) < 0.2]
            address = f"{street} {m.group(2)}, {town[4]}"
            if (fold(address)) in seen:
                continue  # the same spot again (a correction, a renewal)
            seen.add(fold(address))
            if len(near) == 1 or (near and max(abs(a[0] - near[0][0]) + abs(a[1] - near[0][1]) for a in near) < 0.0005):
                la, lo = near[0]
                placed.append(f"disabled_parking\t{la:.6f}\t{lo:.6f}\tΑμεΑ parking\t\t{address}\t\t\t{LINK + ada}\t\t{label} ({date})\n")
            else:
                at = street_at(street, town) or (town[1], town[2])
                check.append((f"{ada}-{len(check)}", at[0], at[1], town[4], address, subj, LINK + ada, date))
    with open(os.path.join(maps, "greece-amea-diavgeia.tsv"), "w", encoding="utf-8") as f:
        f.writelines(placed)
    with open(os.path.join(maps, "greece-amea-check.tsv"), "w", encoding="utf-8") as f:
        for r in check:
            f.write(f"{r[0]}\t{r[1]:.6f}\t{r[2]:.6f}\t{r[3]}\t{r[4]}\t{r[5]}\t{r[6]}\t{r[7]}\n")
    print(f"placed {len(placed)}, to check {len(check)}, left out (personal, requests, abolished, not a municipality) {skipped}", file=sys.stderr)


if __name__ == "__main__":
    maps = sys.argv[1]
    raw = os.path.join(maps, "diavgeia-amea.tsv")
    if not os.path.isfile(raw) or time.time() - os.path.getmtime(raw) > 7 * 86400 or "--fetch" in sys.argv:
        fetch(raw)
    place(maps)
