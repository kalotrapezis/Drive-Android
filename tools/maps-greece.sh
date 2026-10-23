#!/usr/bin/env bash
# Builds Tetra's offline map of Greece on the computer (Plan.md § Maps): the Protomaps basemap cut to Greece's border
# (greece.pmtiles — one file MapLibre opens on Android and, later, on the desktop) and Geofabrik's OSM extract of Greece
# (greece-latest.osm.pbf), and from it the routing graph for car, foot, bike and wheelchair (greece-gh/, GraphHopper 10.2)
# and the search list and addresses (greece-places.tsv, greece-addresses.tsv) and the lanes (greece-lanes.tsv).
# Long and heavy: ~0.5 GB + 0.35 GB of downloads, then several minutes and ~4 GB of memory for the graph.
# Usage: tools/maps-greece.sh [out-dir]   (default ~/Tetra-maps; MAXZOOM=15 by default)
set -euo pipefail
HERE=$(dirname "$(readlink -f "$0")")
OUT=${1:-$HOME/Tetra-maps}
MAXZOOM=${MAXZOOM:-15}
PMTILES_VERSION=1.31.2
mkdir -p "$OUT/bin"
cd "$OUT"

if [ ! -x bin/pmtiles ]; then
  t=go-pmtiles_${PMTILES_VERSION}_Linux_x86_64.tar.gz
  curl -fL -o "bin/$t" "https://github.com/protomaps/go-pmtiles/releases/download/v$PMTILES_VERSION/$t"
  tar xzf "bin/$t" -C bin pmtiles && rm "bin/$t"
fi

# Greece's border (Geofabrik's .poly, which includes the sea around the islands) as GeoJSON for pmtiles extract
curl -fsL -o greece.poly https://download.geofabrik.de/europe/greece.poly
python3 -I - greece.poly greece.geojson <<'EOF'
import json, sys
rings, ring = [], None
for line in open(sys.argv[1]).read().split("\n")[1:]:
    w = line.split()
    if not w: continue
    if w[0] == "END":
        if ring is None: break
        rings.append(ring); ring = None
    elif ring is None: ring = []
    else: ring.append([float(w[0]), float(w[1])])
assert rings, "no rings in the .poly"
json.dump({"type": "MultiPolygon", "coordinates": [[r + [r[0]]] for r in rings]}, open(sys.argv[2], "w"))
EOF

if [ ! -f greece.pmtiles ]; then
  build=$(curl -fs https://build-metadata.protomaps.dev/builds.json | python3 -I -c "import json,sys; print(json.load(sys.stdin)[-1]['key'])")
  echo "Protomaps build $build, maxzoom $MAXZOOM"
  bin/pmtiles extract "https://build.protomaps.com/$build" greece.pmtiles.part --region=greece.geojson --maxzoom="$MAXZOOM" --download-threads=4
  mv greece.pmtiles.part greece.pmtiles
  echo "$build" > greece.pmtiles.build
fi

if [ ! -f greece-latest.osm.pbf ]; then
  curl -fL -C - -o greece-latest.osm.pbf.part https://download.geofabrik.de/europe/greece-latest.osm.pbf
  curl -fsL https://download.geofabrik.de/europe/greece-latest.osm.pbf.md5 | sed 's/greece-latest.osm.pbf/greece-latest.osm.pbf.part/' | md5sum -c -
  mv greece-latest.osm.pbf.part greece-latest.osm.pbf
fi

# a new graph is built beside the old one and swapped in when whole (REBUILD=1 makes a new one when one is there)
if [ ! -f greece-gh/properties ] || [ "${REBUILD:-0}" = 1 ]; then
  [ -f bin/graphhopper-web-10.2.jar ] || curl -fL -o bin/graphhopper-web-10.2.jar https://github.com/graphhopper/graphhopper/releases/download/10.2/graphhopper-web-10.2.jar
  rm -rf greece-gh.new
  "${JAVA:-/usr/lib/jvm/java-17-openjdk-amd64/bin/java}" -Xmx${GH_HEAP:-4g} -Ddw.graphhopper.graph.location=greece-gh.new \
    -jar bin/graphhopper-web-10.2.jar import "$HERE/maps-graph.yml"
  rm -f greece-gh.new/gh.lock
  rm -rf greece-gh.old; [ -d greece-gh ] && mv greece-gh greece-gh.old; mv greece-gh.new greece-gh; rm -rf greece-gh.old
fi

# the offline search list: towns and the kinds of place Search's pills find (tools/maps-places.py)
if [ ! -f greece-addresses.tsv ] || [ greece-latest.osm.pbf -nt greece-places.tsv ]; then
  [ -x venv/bin/python ] || { python3 -m venv venv && venv/bin/pip install -q osmium; }
  venv/bin/python -I "$HERE/maps-places.py" greece-latest.osm.pbf greece-places.tsv greece-addresses.tsv greece-streets.tsv
fi

# the lane arrows before turns, for lane guidance (tools/maps-lanes.py)
if [ ! -f greece-lanes.tsv ] || [ greece-latest.osm.pbf -nt greece-lanes.tsv ]; then
  venv/bin/python -I "$HERE/maps-lanes.py" greece-latest.osm.pbf greece-lanes.tsv
fi

# EOPYY's own pharmacies, from EOPYY's site (tools/maps-eopyy.py); a failed fetch keeps the last list
venv/bin/python -I "$HERE/maps-eopyy.py" greece-eopyy.tsv || echo "EOPYY's list not refreshed"
# cities' own disabled (ΑμεΑ) parking spots from data.gov.gr (tools/maps-amea.py); a failed fetch keeps the last list
venv/bin/python -I "$HERE/maps-amea.py" greece-amea.tsv || echo "The ΑμεΑ spots not refreshed"
# the ΑμεΑ spots in the municipalities' decisions (Διαύγεια): the sure ones placed, the rest a list to place by hand
python3 -I "$HERE/maps-diavgeia.py" . || echo "Διαύγεια not refreshed"

bin/pmtiles show greece.pmtiles | head -20
ls -lh "$OUT"
