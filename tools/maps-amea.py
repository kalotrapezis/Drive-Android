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
import os
import sys
import time
import urllib.parse
import urllib.request

API = "https://data.gov.gr/api/3/action/package_search?rows=200&q="
QUERIES = ("ΑμεΑ στάθμευσης", "ΑΜΕΑ θέσεις", "θέση ΑμεΑ", "πινακίδας ΑμεΑ")  # (22 Oct: "Θέσεις ΑΜΕΑ (GIS 2009)" has no "στάθμευσης")
# datasets in other forms than a city's WFS: (data.gov.gr ZIP, the file in it, its reader)
FILES = [
    ("https://data.gov.gr/dataset/e8b5e0a1-da1d-4c37-b44c-444153748586/resource/7b3fba78-88bc-418b-b58e-2df613990085/download/"
     "dhmosies-8eseis-sta8meyshs-amea-toy-dhmoy-hrakleioy1-56vtoepq.zip", "parking-areas-for-amea.csv", "Ηράκλειο", "heraklion"),
    ("https://data.gov.gr/dataset/a685a43c-d821-42b8-9b82-10189ce75313/resource/a0311d54-5877-4167-a33f-cb3f6323d29d/download/"
     "larissa-parking-amea.zip", "AMEA_GEN", "Λάρισα", "shp-egsa87"),
]
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


PLATE_KEYS = ("arkykl", "ar_kykl", "arithmos_kykloforias", "pinakida", "plate", "αρ. κυκλ", "αριθμός κυκλοφορίας")


def is_personal(props):
    """A spot given to one person or car: a kind that says so (ΑΤΟΜΙΚΗ, ΑΠΟΚΛΕΙΣΤΙΚΗ, ΠΑΡΑΧΩΡΗΣΗ), or a plate filled in."""
    for k, v in props.items():
        t = str(v or "").upper()
        if any(w in t for w in ("ΑΤΟΜΙΚ", "ΑΠΟΚΛΕΙΣΤ", "ΠΑΡΑΧΩΡ", "PERSONAL", "PRIVATE")):
            return True
        if str(k).lower() in PLATE_KEYS and str(v or "").strip() not in ("", "None", "-"):
            return True
    return False


# A whole dataset that says its spots are public ones (the user, 22 Oct: "keep the general ones, if its not clear remove all
# of them"): Athens' placements by council decision, Heraklion's "public" spots. Any other dataset counts only where each
# spot says it is general (Thessaloniki's eidos_thesis).
GENERAL_DATASETS = ("χωροθέτηση", "δημόσιες θέσεις")


def clearly_general(title, props):
    """True only for a spot clearly public: marked general (ΓΕΝΙΚΗ), or in a dataset that is all public spots; never one
    personal (ΑΤΟΜΙΚΗ, ΠΑΡΑΧΩΡΗΣΗ, a plate, an applicant's name) or abolished (ΚΑΤΑΡΓΗΣΗ)."""
    keys = {str(k).lower() for k, v in props.items() if str(v or "").strip() not in ("", "None", "-")}
    if is_personal(props) or keys & {"aitountas", "aitountes", "onomateponymo", "ar_kykl_oximatos"}:
        return False
    text = " ".join(str(v or "").upper() for v in props.values())
    if "ΚΑΤΑΡΓ" in text:
        return False
    return "ΓΕΝΙΚ" in text or any(w in title.lower() for w in GENERAL_DATASETS)


def clean(s):
    return str(s or "").replace("\t", " ").replace("\n", " ").strip()


def where(props, city):
    low = {str(k).lower(): v for k, v in props.items()}
    street = next((clean(low[k]) for k in STREET if low.get(k)), "")
    number = next((clean(low[k]) for k in NUMBER if low.get(k)), "")
    return " ".join(x for x in (street.title() if street.isupper() else street, number) if x) + (f", {city}" if street else city)


def egsa87(x, y):
    """Greek Grid (ΕΓΣΑ87, EPSG:2100) metres to WGS84 latitude and longitude: inverse transverse Mercator on GRS80, then
    GGRS87's datum shift (-199.87, 74.79, 246.62 m) through geocentric coordinates. Good to a metre or two."""
    import math
    a, f = 6378137.0, 1 / 298.257222101
    e2 = f * (2 - f); k0 = 0.9996; lon0 = math.radians(24)
    x -= 500000; m = y / k0
    mu = m / (a * (1 - e2 / 4 - 3 * e2 ** 2 / 64 - 5 * e2 ** 3 / 256))
    e1 = (1 - math.sqrt(1 - e2)) / (1 + math.sqrt(1 - e2))
    p = mu + (3 * e1 / 2 - 27 * e1 ** 3 / 32) * math.sin(2 * mu) + (21 * e1 ** 2 / 16 - 55 * e1 ** 4 / 32) * math.sin(4 * mu) \
        + (151 * e1 ** 3 / 96) * math.sin(6 * mu) + (1097 * e1 ** 4 / 512) * math.sin(8 * mu)
    ep2 = e2 / (1 - e2); c1 = ep2 * math.cos(p) ** 2; t1 = math.tan(p) ** 2
    n1 = a / math.sqrt(1 - e2 * math.sin(p) ** 2); r1 = a * (1 - e2) / (1 - e2 * math.sin(p) ** 2) ** 1.5; d = x / (n1 * k0)
    lat = p - (n1 * math.tan(p) / r1) * (d ** 2 / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 ** 2 - 9 * ep2) * d ** 4 / 24
                                       + (61 + 90 * t1 + 298 * c1 + 45 * t1 ** 2 - 252 * ep2 - 3 * c1 ** 2) * d ** 6 / 720)
    lon = lon0 + (d - (1 + 2 * t1 + c1) * d ** 3 / 6 + (5 - 2 * c1 + 28 * t1 - 3 * c1 ** 2 + 8 * ep2 + 24 * t1 ** 2) * d ** 5 / 120) / math.cos(p)
    # GGRS87 → WGS84
    n = a / math.sqrt(1 - e2 * math.sin(lat) ** 2)
    X = n * math.cos(lat) * math.cos(lon) - 199.87; Y = n * math.cos(lat) * math.sin(lon) + 74.79; Z = n * (1 - e2) * math.sin(lat) + 246.62
    lon = math.atan2(Y, X); pp = math.hypot(X, Y); lat = math.atan2(Z, pp * (1 - e2))
    for _ in range(5):
        n = a / math.sqrt(1 - e2 * math.sin(lat) ** 2); lat = math.atan2(Z + e2 * n * math.sin(lat), pp)
    return math.degrees(lat), math.degrees(lon)


def files():
    """The datasets of FILES, as (lat, lng, where) rows."""
    import io, zipfile, tempfile, os
    out = []
    for url, name, city, how in FILES:
        try:
            z = zipfile.ZipFile(io.BytesIO(get(url)))
        except Exception as e:
            print(f"{city}: {e}", file=sys.stderr); continue
        n = 0
        if how == "heraklion":  # cp1253, "35.337.201" for 35.337201
            text = z.read(name).decode("cp1253", "replace")
            for line in text.splitlines()[1:]:
                c = line.split(";")
                if len(c) < 3:
                    continue
                num = lambda v: float(v.replace(".", "")[:2] + "." + v.replace(".", "")[2:])
                try:
                    out.append((num(c[1]), num(c[2]), f"{clean(c[0])}, {city}" if clean(c[0]) else city, city)); n += 1
                except ValueError:
                    pass
        elif how == "shp-egsa87":
            import shapefile
            with tempfile.TemporaryDirectory() as d:
                z.extractall(d)
                r = shapefile.Reader(os.path.join(d, name))
                for sh, rec in zip(r.shapes(), r.records()):
                    if not sh.points:
                        continue
                    x = sum(p[0] for p in sh.points) / len(sh.points); y = sum(p[1] for p in sh.points) / len(sh.points)
                    lat, lng = egsa87(x, y)
                    rd = rec.as_dict()
                    street = clean(rd.get("address__1") or rd.get("name") or "")
                    out.append((lat, lng, f"{street}, {city}" if street else city, city)); n += 1
        print(f"{city}: {n}")
    return out


if __name__ == "__main__":
    found = []
    seen_ds = set()
    for q in QUERIES:
        for ds in json.loads(get(API + urllib.parse.quote(q)))["result"]["results"]:
            if ds["id"] in seen_ds:
                continue
            seen_ds.add(ds["id"])
            t = ds["title"].lower()
            if any(k in t for k in ("αμεα", "α.μ.ε.α")) and any(k in t for k in ("στάθμευσ", "θέσ", "πινακίδ")) and \
                    not any(k in t for k in ("αιτήσεις", "ράμπες", "πρόσβαση", "κέντρα", "δομές", "στοιχεία", "πράξη")):
                found.append(ds)
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
        personal = 0
        for f in features:
            # never a spot given to one person (Thessaloniki's layer, 22 Oct: 483 "ΑΤΟΜΙΚΕΣ ΘΕΣΕΙΣ ΑΜΕΑ" with the car's plate,
            # beside 62 general ones): the user, "never make personal or exclusive ΑμεΑ spots public"
            props = f.get("properties") or {}
            if not clearly_general(ds["title"], props):
                personal += 1
                continue
            at = middle(f.get("geometry"))
            if not at or not (34 < at[0] < 42 and 19 < at[1] < 30):  # not in Greece: a projection not asked for
                continue
            rows.append(f"disabled_parking\t{at[0]:.6f}\t{at[1]:.6f}\tΑμεΑ parking\t\t{where(f.get('properties') or {}, city)}\t\t\t\t\t{city} (data.gov.gr)\n")
            n += 1
        print(f"{city}: {n}" + (f" ({personal} personal, abolished or unclear left out)" if personal else ""))
    for lat, lng, where_, city in files():
        if 34 < lat < 42 and 19 < lng < 30:
            rows.append(f"disabled_parking\t{lat:.6f}\t{lng:.6f}\tΑμεΑ parking\t\t{where_}\t\t\t\t\t{city} (data.gov.gr)\n")
    # cities read from their own GIS by hand (tools/amea-municipal.tsv: Καβάλα, Πάτρα, Thessaloniki's general layer, Αθήνα):
    # places as the city counts them in the address line
    extra = os.path.join(os.path.dirname(os.path.abspath(__file__)), "amea-municipal.tsv")
    if os.path.isfile(extra):
        for line in open(extra, encoding="utf-8"):
            if line.startswith("#") or not line.strip():
                continue
            city, addr, places, lat, lng, source = line.rstrip("\n").split("\t")
            where_ = f"{addr}, {city}" if not addr.isdigit() else f"{city} (σημείο {addr})"
            if int(places) > 1:
                where_ += f" · {places} θέσεις"
            rows.append(f"disabled_parking\t{float(lat):.6f}\t{float(lng):.6f}\tΑμεΑ parking\t\t{where_}\t\t\t\t\t{city} ({source})\n")
        print(f"tools/amea-municipal.tsv: {sum(1 for l in open(extra, encoding='utf-8') if l.strip() and not l.startswith('#'))}")
    # one spot once: some cities publish the same spots in two datasets
    kept, cells = [], {}  # every spot against all kept ones near it (a grid of ~50 m cells)
    for r in rows:
        la, lo = map(float, r.split("\t")[1:3])
        cx, cy = int(la * 2000), int(lo * 2000)
        near = [p for dx in (-1, 0, 1) for dy in (-1, 0, 1) for p in cells.get((cx + dx, cy + dy), [])]
        if not any(abs(la - a) < 0.00005 and abs(lo - b) < 0.00006 for a, b in near):
            kept.append(r); cells.setdefault((cx, cy), []).append((la, lo))
    rows = kept
    with open(sys.argv[1] + ".part", "w", encoding="utf-8") as out:
        out.writelines(rows)
    import os
    os.replace(sys.argv[1] + ".part", sys.argv[1])
    print(f"{len(rows)} spots; not read (no GeoJSON): {skipped}")
