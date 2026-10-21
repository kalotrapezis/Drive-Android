#!/usr/bin/env python3
"""Municipalities' own disabled (ΑμεΑ) parking spots (the user, 21 Oct: "thesaloniki and at least one more place in greece list
their AMEA parking spots look for them on the internet to add them to our map"): every dataset on data.gov.gr that a search
for "ΑμεΑ στάθμευσης" finds and that its city serves as GeoJSON (a WFS), each spot as a line of the search list
(maps-places.py's form) with the kind "disabled_parking":

    disabled_parking <TAB> lat <TAB> lng <TAB> ΑμεΑ parking <TAB> <TAB> street and number, or the city <TAB> … <TAB> source

A shape (an area) is put at its middle. Datasets only as a shapefile or a page are named at the end, not read.
Usage: maps-amea.py greece-amea.tsv
"""
import json
import sys
import time
import urllib.parse
import urllib.request

API = "https://data.gov.gr/api/3/action/package_search?rows=100&q=" + urllib.parse.quote("ΑμεΑ στάθμευσης")
STREET = ("street", "odos", "odos_name", "dieythinsi", "dieuthinsi", "address", "onoma_odou", "οδός", "διεύθυνση")
NUMBER = ("number", "arithmos", "ar", "αριθμός")
TOWN = {"Αθηναίων": "Αθήνα", "Θεσσαλονίκης": "Θεσσαλονίκη", "Χανίων": "Χανιά", "Χαλανδρίου": "Χαλάνδρι", "Παπάγου-Χολαργού": "Παπάγου-Χολαργός",
        "Νάξου και Μικρών Κυκλάδων": "Νάξος", "Λαρισαίων": "Λάρισα", "Ηρακλείου Κρήτης": "Ηράκλειο"}


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Tetra maps-amea)"})
    with urllib.request.urlopen(req, timeout=90) as r:
        return r.read()


def as_wgs84(url):
    """A WFS GetFeature link asked again as WFS 1.0.0 GeoJSON in EPSG:4326 (longitude, latitude)."""
    u = urllib.parse.urlsplit(url)
    q = {k.lower(): v for k, v in urllib.parse.parse_qsl(u.query)}
    layer = q.get("typenames") or q.get("typename")
    if not layer:
        return None
    query = urllib.parse.urlencode({"service": "WFS", "version": "1.0.0", "request": "GetFeature", "typeName": layer,
                                    "outputFormat": "application/json", "srsName": "EPSG:4326"})
    return urllib.parse.urlunsplit((u.scheme, u.netloc, u.path, query, ""))


def middle(geometry):
    pts = []
    def walk(c):
        if isinstance(c, (list, tuple)) and c and isinstance(c[0], (int, float)):
            pts.append(c)
        elif isinstance(c, (list, tuple)):
            for x in c:
                walk(x)
    walk((geometry or {}).get("coordinates"))
    if not pts:
        return None
    return sum(p[1] for p in pts) / len(pts), sum(p[0] for p in pts) / len(pts)


def clean(s):
    return str(s or "").replace("\t", " ").replace("\n", " ").strip()


def where(props, city):
    low = {str(k).lower(): v for k, v in props.items()}
    street = next((clean(low[k]) for k in STREET if low.get(k)), "")
    number = next((clean(low[k]) for k in NUMBER if low.get(k)), "")
    return " ".join(x for x in (street.title() if street.isupper() else street, number) if x) + (f", {city}" if street else city)


if __name__ == "__main__":
    found = json.loads(get(API))["result"]["results"]
    rows, skipped = [], []
    for ds in found:
        city = (ds.get("organization") or {}).get("title", "").replace("Δήμος ", "")
        city = TOWN.get(city, city)
        if "αμεα" not in ds["title"].lower().replace("ά", "α").replace("έ", "ε"):  # the search also finds taxis, abandoned cars
            continue
        links = [r["url"] for r in ds["resources"] if "wfs" in r.get("url", "").lower() and "getfeature" in r.get("url", "").lower()]
        if not links or "πολύγωνα" in ds["title"].lower():  # Παπάγου-Χολαργού gives the same spots as points too
            if not links:
                skipped.append(f"{city}: {ds['title']}")
            continue
        url = as_wgs84(links[0])
        try:
            time.sleep(1)
            features = json.loads(get(url)).get("features", [])
        except Exception as e:
            skipped.append(f"{city}: {ds['title']} ({e})")
            continue
        n = 0
        for f in features:
            at = middle(f.get("geometry"))
            if not at or not (34 < at[0] < 42 and 19 < at[1] < 30):  # not in Greece: a projection not asked for
                continue
            rows.append(f"disabled_parking\t{at[0]:.6f}\t{at[1]:.6f}\tΑμεΑ parking\t\t{where(f.get('properties') or {}, city)}\t\t\t\t\t{city} (data.gov.gr)\n")
            n += 1
        print(f"{city}: {n}")
    # one spot once: some cities publish the same spots in two datasets
    kept = []
    for r in rows:
        la, lo = map(float, r.split("\t")[1:3])
        if not any(abs(la - a) < 0.00005 and abs(lo - b) < 0.00006 for a, b, _ in kept[-400:]):
            kept.append((la, lo, r))
    rows = [r for _, _, r in kept]
    with open(sys.argv[1] + ".part", "w", encoding="utf-8") as out:
        out.writelines(rows)
    import os
    os.replace(sys.argv[1] + ".part", sys.argv[1])
    print(f"{len(rows)} spots; not read (no GeoJSON): {skipped}")
