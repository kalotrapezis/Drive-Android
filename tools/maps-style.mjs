// Makes app/src/main/assets/maps/{light,dark}.json from @protomaps/basemaps 5.7.2 (npm install @protomaps/basemaps@5.7.2, then node this);
// MAP_FILE is replaced by the app with the greece.pmtiles path. Glyphs and sprites are basemaps-assets, in the same folder.
import { layers, namedFlavor } from "@protomaps/basemaps";
import fs from "fs";
for (const flavor of ["light", "dark"]) {
  const style = {
    version: 8,
    glyphs: "asset://maps/fonts/{fontstack}/{range}.pbf",
    sprite: `asset://maps/sprites/${flavor}`,
    sources: { protomaps: { type: "vector", url: "pmtiles://MAP_FILE", attribution: "© OpenStreetMap, Protomaps" } },
    layers: layers("protomaps", namedFlavor(flavor), { lang: "el" }),
  };
  fs.writeFileSync(`${flavor}.json`, JSON.stringify(style));
}
