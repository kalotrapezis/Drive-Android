#!/usr/bin/env python3
"""ΑμεΑ parking spots from the municipalities' own decisions on Διαύγεια (diavgeia.gov.gr, open API, no account): every
decision whose subject speaks of a parking spot for disabled people (ΑμεΑ, αναπηρία, Ρ-72), half a year at a time from 2012
(the API answers a few months per question). The user, 21 Oct: Καβάλα and Ξάνθη have many, in no open data.

Prints them as a table (date, kind, street, ada) and writes the raw list to the file given, one decision per line:
    org <TAB> date <TAB> ada <TAB> subject
Usage: maps-diavgeia.py out.tsv [org-uid …]   (default: Καβάλα 6126, Ξάνθη 6220, Δράμα 6081)
"""
import datetime
import json
import sys
import time
import urllib.parse
import urllib.request

API = "https://diavgeia.gov.gr/opendata/search"
TERMS = ("ΑμεΑ", "αναπηρία", "Ρ-72")  # the search ignores case: ΑΜΕΑ finds the same


def get(params):
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "Tetra maps-diavgeia"})
    for attempt in range(3):
        try:
            time.sleep(0.4 + 5 * attempt)
            with urllib.request.urlopen(req, timeout=90) as r:
                return json.loads(r.read())
        except OSError:
            if attempt == 2:
                raise


def parking(subject):
    s = subject.lower()
    return "στάθμευ" in s or "ρ-72" in s or "ρ72" in s


def window(org, start, end):
    """The parking decisions of [org] issued [start]..[end], for every term."""
    got = {}
    for term in TERMS:
        page = 0
        while True:
            d = get({"org": org, "subject": term, "from_issue_date": start.isoformat(), "to_issue_date": end.isoformat(), "size": 100, "page": page})
            for x in d.get("decisions", []):
                if parking(x.get("subject", "")):
                    got[x["ada"]] = (org, datetime.date.fromtimestamp(x["issueDate"] / 1000).isoformat(), x["ada"], " ".join(x.get("subject", "").split()))
            page += 1
            if page * 100 >= d.get("info", {}).get("total", 0):
                break
    return got


if __name__ == "__main__":
    from concurrent.futures import ThreadPoolExecutor
    out = sys.argv[1]
    orgs = sys.argv[2:] or ["6126", "6220", "6081"]
    jobs = []
    for org in orgs:
        start = datetime.date(2012, 1, 1)
        while start < datetime.date.today():
            end = min(start + datetime.timedelta(days=180), datetime.date.today())
            jobs.append((org, start, end))
            start = end + datetime.timedelta(days=1)
    found = {}
    with ThreadPoolExecutor(6) as pool, open(out, "w", encoding="utf-8") as f:
        for got in pool.map(lambda j: window(*j), jobs):  # each half-year written as it comes
            for ada, r in got.items():
                if ada not in found:
                    found[ada] = r
                    f.write("\t".join(r) + "\n"); f.flush()
    for r in sorted(found.values()):
        print(r[0], r[1], r[3][:170])
