export const SPORTS = new Set([
  "all",
  "soccer",
  "basketball",
  "volleyball",
  "tennis",
  "fitness",
]);

export function coord(value: string | null, limit: number): number | null {
  if (!value || !/^-?\d{1,3}(\.\d{1,6})?$/.test(value)) {
    return null;
  }
  const number = Number(value);
  return Math.abs(number) <= limit ? number : null;
}

export function query(
  lat: number,
  lon: number,
  radius: number,
  sport: string,
): string {
  const filter = sport === "all"
    ? '["leisure"="pitch"]'
    : sport === "fitness"
    ? '["leisure"="fitness_station"]'
    : '["leisure"="pitch"]["sport"~"(^|;)' + sport + '(;|$)"]';

  return '[out:json][timeout:20];(node(around:' + radius + ',' + lat + ',' + lon + ')' + filter +
    ';way(around:' + radius + ',' + lat + ',' + lon + ')' + filter +
    ';relation(around:' + radius + ',' + lat + ',' + lon + ')' + filter +
    ';);out center 100;';
}

export interface SpotPhoto {
  url: string;
  source_url: string;
  author: string;
  license: string;
  license_url: string;
}

export interface OSMSpot {
  id: string;
  source: "osm";
  osm_type: "node" | "way" | "relation";
  osm_id: number;
  name: string;
  sport_type: string;
  latitude: number;
  longitude: number;
  address: string | null;
  surface: string | null;
  lighting: boolean | null;
  equipment: string | null;
  photo?: SpotPhoto;
}

const fallbackNames = new Map([
  ["soccer", "Футбольная площадка"],
  ["basketball", "Баскетбольная площадка"],
  ["volleyball", "Волейбольная площадка"],
  ["tennis", "Теннисный корт"],
  ["table_tennis", "Площадка для настольного тенниса"],
  ["hockey", "Хоккейная площадка"],
  ["ice_hockey", "Хоккейная площадка"],
  ["fitness", "Воркаут-площадка"],
]);

function readSports(tag: unknown): string[] {
  if (typeof tag !== "string") return [];
  const tokens = tag.split(";").map((value) => value.trim().toLowerCase());
  return [...new Set(tokens.filter((value) =>
    value.length > 0 && value.length <= 64 && value !== "unspecified"
  ))].slice(0, 8);
}

function describeSpot(sports: string[], fitnessStation: boolean): string {
  if (sports.length > 1) return "Многофункциональная спортивная площадка";
  if (fitnessStation) return "Воркаут-площадка";
  return fallbackNames.get(sports[0]) ?? "Спортивная площадка";
}

export function convert(value: unknown): OSMSpot | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }

  const item = value as Record<string, unknown>;
  const osmType = item.type;
  if (osmType !== "node" && osmType !== "way" && osmType !== "relation") {
    return null;
  }
  const osmId = item.id;
  if (typeof osmId !== "number" || !Number.isSafeInteger(osmId)) {
    return null;
  }

  const center = (
    item.center && typeof item.center === "object" ? item.center : item
  ) as Record<string, unknown>;
  const lat = center.lat;
  const lon = center.lon;
  if (typeof lat !== "number" || Math.abs(lat) > 90 ||
      typeof lon !== "number" || Math.abs(lon) > 180) {
    return null;
  }

  const tags = (
    item.tags && typeof item.tags === "object" ? item.tags : {}
  ) as Record<string, unknown>;
  const getTag = (key: string): string | null => {
    const tag = tags[key];
    return typeof tag === "string" ? tag.trim().slice(0, 250).trimEnd() || null : null;
  };
  const fitnessStation = tags.leisure === "fitness_station";
  const sports = readSports(tags.sport);
  if (sports.length === 0 && fitnessStation) sports.push("fitness");
  const streetOrPlace = getTag("addr:street") ?? getTag("addr:place");

  return {
    id: `osm:${osmType}:${osmId}`,
    source: "osm",
    osm_type: osmType,
    osm_id: osmId,
    name: getTag("name") ?? describeSpot(sports, fitnessStation),
    sport_type: sports.join(";") || "unspecified",
    latitude: lat,
    longitude: lon,
    address: [streetOrPlace, getTag("addr:housenumber")]
      .filter(Boolean).join(", ") || null,
    surface: getTag("surface"),
    lighting: tags.lit === "yes" ? true : tags.lit === "no" ? false : null,
    equipment: getTag("fitness_station"),
  };
}

export function makeCacheKey(
  lat: number,
  lon: number,
  radius: number,
  sport: string,
): string {
  return "v3:" + lat.toFixed(6) + ":" + lon.toFixed(6) + ":" + radius + ":" + sport;
}
