#!/usr/bin/env python3
"""EOPYY's own pharmacies (the user, 21 Oct: "the EOpYY farmacies … add them as a list as well as a pill"), from EOPYY's
site (eopyy.gov.gr/PharmacyList and each pharmacy's page, which carries its map position), as lines of the search list
(see maps-places.py) with the kind "eopyy":

    eopyy <TAB> lat <TAB> lng <TAB> name <TAB> <TAB> address <TAB> hours <TAB> phone <TAB> page <TAB> <TAB> note

The pharmacist's name on each page is left out. Usage: maps-eopyy.py greece-eopyy.tsv
"""
import html
import re
import sys
import time
import urllib.parse
import urllib.request

SITE = "https://www.eopyy.gov.gr"


def get(path):
    """A page, gently: a second between requests, and up to three tries (the site resets a quick run of them)."""
    req = urllib.request.Request(SITE + urllib.parse.quote(path, safe="/?=&"), headers={"User-Agent": "Mozilla/5.0 (Tetra maps-eopyy)"})
    for attempt in range(3):
        time.sleep(1 + 10 * attempt)
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read().decode("utf-8")
        except OSError:
            if attempt == 2:
                raise


def text(fragment):
    t = re.sub(r"<script.*?</script>", "", fragment, flags=re.S)
    t = html.unescape(re.sub(r"<[^>]+>", "\n", t))
    return [x.strip() for x in t.split("\n") if x.strip()]


def pharmacy(path):
    h = get(path)
    lat = float(re.search(r"const latitude = ([0-9.]+)", h).group(1))
    lng = float(re.search(r"const longitude = ([0-9.]+)", h).group(1))
    start = h.find("});", h.find(".openPopup();")) + 3
    lines = text(h[start:h.find("Αναζήτηση διαθέσιμων φαρμάκων", start)])
    # name, then hours, the pharmacist, the address, phones and an e-mail, in some order
    name = lines[0] if lines else ""
    address = next((x for x in lines[1:] if ", Ν." in x), "") \
        or next((x for x in lines[1:] if re.search(r"\b\d{3} ?\d{2}\b", x) and not re.match(r"\d{3}", x) and ":" not in x), "")
    phone = next((x.split("(")[0].strip() for x in lines[1:] if re.match(r"\d{3}", x) and x != address), "")
    days = ("Δευτέρα", "Τρίτη", "Τετάρτη", "Πέμπτη", "Παρασκευή")
    hours = "; ".join(x for x in lines[1:] if any(d in x for d in days) and x != address)  # "Αγία Παρασκευή" is a town too
    note = next((x for x in lines[1:] if x.startswith("Δεν διαθέτει")), "")
    return f"eopyy\t{lat:.6f}\t{lng:.6f}\t{name}\t\t{address}\t{hours}\t{phone}\t{SITE}{path}\t\t{note}\n"


if __name__ == "__main__":
    first = get("/PharmacyList")
    pages = int(re.search(r'data-total-pages="(\d+)"', first).group(1))
    listing = first + "".join(get(f"/PharmacyList?page={p}") for p in range(2, pages + 1))
    paths = sorted({html.unescape(p) for p in re.findall(r'href="(/el/pharmacylist/[^"]+)"', listing)})
    rows = [pharmacy(p) for p in paths]
    with open(sys.argv[1] + ".part", "w", encoding="utf-8") as out:
        out.writelines(rows)
    import os
    os.replace(sys.argv[1] + ".part", sys.argv[1])
    print(f"{len(rows)} EOPYY pharmacies")
