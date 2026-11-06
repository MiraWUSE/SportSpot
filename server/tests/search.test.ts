import * as assert from "node:assert/strict";
import {
  SPORTS,
  coord,
  convert,
  makeCacheKey,
  query,
} from "../../backend/supabase/functions/search-sports-spots-v1/search-logic.ts";

Deno.test("coord: принимает корректные координаты", () => {
  assert.equal(coord("56.326912", 90), 56.326912);
  assert.equal(coord("44.005987", 180), 44.005987);
  assert.equal(coord("-12.5", 90), -12.5);
});

Deno.test("coord: принимает границы диапазонов", () => {
  assert.equal(coord("-90", 90), -90);
  assert.equal(coord("90", 90), 90);
  assert.equal(coord("-180", 180), -180);
  assert.equal(coord("180", 180), 180);
});

Deno.test("coord: отклоняет отсутствие и неверный формат", () => {
  assert.equal(coord(null, 90), null);
  assert.equal(coord("", 90), null);
  assert.equal(coord("abc", 90), null);
  assert.equal(coord("56,326", 90), null);
  assert.equal(coord("1e2", 180), null);
});

Deno.test("coord: отклоняет выход за границы и более 6 знаков", () => {
  assert.equal(coord("90.000001", 90), null);
  assert.equal(coord("-180.000001", 180), null);
  assert.equal(coord("56.1234567", 90), null);
});

Deno.test("SPORTS: поддерживаются только заявленные виды спорта", () => {
  assert.deepEqual([...SPORTS].sort(), [
    "all", "basketball", "fitness", "soccer", "tennis", "volleyball",
  ]);
  assert.equal(SPORTS.has("hockey"), false);
});

Deno.test("query: поиск всех площадок использует точный радиус", () => {
  const result = query(56.326912, 44.005987, 123, "all");
  assert.ok(result.startsWith("[out:json][timeout:20];"));
  assert.ok(result.includes('node(around:123,56.326912,44.005987)["leisure"="pitch"]'));
  assert.ok(result.includes('way(around:123,56.326912,44.005987)["leisure"="pitch"]'));
  assert.ok(result.includes('relation(around:123,56.326912,44.005987)["leisure"="pitch"]'));
  assert.ok(result.endsWith("out center 100;"));
});

Deno.test("query: фильтр футбольных площадок", () => {
  const result = query(56.3, 44, 500, "soccer");
  assert.ok(result.includes('["leisure"="pitch"]["sport"~"(^|;)soccer(;|$)"]'));
});

Deno.test("query: воркаут использует fitness_station", () => {
  const result = query(56.3, 44, 500, "fitness");
  assert.ok(result.includes('["leisure"="fitness_station"]'));
  assert.ok(!result.includes('["leisure"="pitch"]'));
});

Deno.test("convert: корректно преобразует OSM node со всеми полями", () => {
  const result = convert({
    type: "node",
    id: 12345,
    lat: 56.326912,
    lon: 44.005987,
    tags: {
      name: "Школьная площадка",
      sport: "basketball",
      surface: "asphalt",
      lit: "yes",
      "addr:street": "Большая Покровская",
      "addr:housenumber": "1",
      fitness_station: "horizontal_bar",
    },
  });

  assert.deepEqual(result, {
    id: "osm:node:12345",
    source: "osm",
    osm_type: "node",
    osm_id: 12345,
    name: "Школьная площадка",
    sport_type: "basketball",
    latitude: 56.326912,
    longitude: 44.005987,
    address: "Большая Покровская, 1",
    surface: "asphalt",
    lighting: true,
    equipment: "horizontal_bar",
  });
});

Deno.test("convert: для OSM way получает координаты из center", () => {
  const result = convert({
    type: "way", id: 100,
    center: { lat: 56.32, lon: 44.01 },
    tags: { name: "Теннисный корт", lit: "no" },
  });
  assert.equal(result?.id, "osm:way:100");
  assert.equal(result?.latitude, 56.32);
  assert.equal(result?.longitude, 44.01);
  assert.equal(result?.lighting, false);
});

Deno.test("convert: поддерживает OSM relation", () => {
  const result = convert({
    type: "relation", id: 200,
    center: { lat: 56.31, lon: 44.02 },
    tags: { sport: "volleyball" },
  });
  assert.equal(result?.id, "osm:relation:200");
  assert.equal(result?.sport_type, "volleyball");
});

Deno.test("convert: применяет значения по умолчанию", () => {
  const result = convert({ type: "node", id: 1, lat: 0, lon: 0 });
  assert.equal(result?.name, "Спортивная площадка");
  assert.equal(result?.sport_type, "unspecified");
  assert.equal(result?.address, null);
  assert.equal(result?.surface, null);
  assert.equal(result?.lighting, null);
  assert.equal(result?.equipment, null);
});

Deno.test("convert: определяет fitness по leisure=fitness_station", () => {
  const result = convert({
    type: "node", id: 2, lat: 56.3, lon: 44,
    tags: { leisure: "fitness_station" },
  });
  assert.equal(result?.sport_type, "fitness");
});

Deno.test("convert: отклоняет некорректные исходные значения", () => {
  assert.equal(convert(null), null);
  assert.equal(convert("not an object"), null);
  assert.equal(convert([]), null);
  assert.equal(convert({}), null);
});

Deno.test("convert: отклоняет неверный OSM тип или идентификатор", () => {
  assert.equal(convert({ type: "area", id: 1, lat: 1, lon: 2 }), null);
  assert.equal(convert({ type: "node", id: "1", lat: 1, lon: 2 }), null);
  assert.equal(convert({ type: "node", id: 1.5, lat: 1, lon: 2 }), null);
});

Deno.test("convert: отклоняет отсутствующие и недопустимые координаты", () => {
  assert.equal(convert({ type: "node", id: 1, lon: 44 }), null);
  assert.equal(convert({ type: "node", id: 1, lat: 91, lon: 44 }), null);
  assert.equal(convert({ type: "node", id: 1, lat: 56, lon: -181 }), null);
});

Deno.test("convert: ограничивает длину названия 250 символами", () => {
  const result = convert({
    type: "node", id: 8, lat: 56, lon: 44,
    tags: { name: "А".repeat(300) },
  });
  assert.equal(result?.name.length, 250);
});

Deno.test("makeCacheKey: выдаёт версионный и повторяемый ключ", () => {
  const first = makeCacheKey(56.326912, 44.005987, 100, "all");
  const second = makeCacheKey(56.326912, 44.005987, 100, "all");
  assert.equal(first, "v2:56.326912:44.005987:100:all");
  assert.equal(first, second);
});

Deno.test("makeCacheKey: различает радиусы без округления до 500 м", () => {
  const key100 = makeCacheKey(56.326912, 44.005987, 100, "all");
  const key101 = makeCacheKey(56.326912, 44.005987, 101, "all");
  assert.notEqual(key100, key101);
});

Deno.test("makeCacheKey: учитывает вид спорта и точные координаты", () => {
  const base = makeCacheKey(56.326912, 44.005987, 500, "all");
  assert.notEqual(base, makeCacheKey(56.326913, 44.005987, 500, "all"));
  assert.notEqual(base, makeCacheKey(56.326912, 44.005988, 500, "all"));
  assert.notEqual(base, makeCacheKey(56.326912, 44.005987, 500, "soccer"));
});
