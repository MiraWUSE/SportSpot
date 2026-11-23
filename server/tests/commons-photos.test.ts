import * as assert from "node:assert/strict";
import {
  commonsFile,
  searchSpotsWithPhotos,
} from "../../backend/supabase/functions/search-sports-spots-v1/commons-photos.ts";

const title = "File:Test court.jpg";
const source = "https://commons.wikimedia.org/wiki/File:Test_court.jpg";
const thumbnail =
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Test_court.jpg/500px-Test_court.jpg";
const license = "https://creativecommons.org/licenses/by-sa/4.0/";
const node = (id: number, file?: string) => ({
  type: "node",
  id,
  lat: 56,
  lon: 44,
  tags: { wikimedia_commons: file },
});

const imageinfo = {
  mime: "image/jpeg",
  thumburl: thumbnail +
    "?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail",
  thumbwidth: 500,
  descriptionurl: source,
  extmetadata: {
    Artist: { value: '<a href="/wiki/User:Test">Test &amp; Partner</a>' },
    Credit: { value: "Own work" },
    LicenseShortName: { value: "CC BY-SA 4.0" },
    LicenseUrl: { value: license.slice(0, -1) },
  },
};
const commonsResponse = (info = imageinfo, pageTitle = title) =>
  Response.json({
    query: { pages: [{ ns: 6, title: pageTitle, imageinfo: [info] }] },
  });

Deno.test("Commons file parser accepts only a single supported file from the same object's tags", () => {
  assert.equal(commonsFile(node(1, "File:Test__court.jpg")), title);
  assert.equal(commonsFile({ tags: { image: source } }), title);
  assert.equal(
    commonsFile({ tags: { image: "File:École.jpg" } }),
    "File:École.jpg",
  );
  for (
    const value of [
      "",
      "Category:Courts",
      "File:Test.svg",
      "File:a.jpg|File:b.jpg",
      "File:../a.jpg",
      "File:" + "a".repeat(250) + ".jpg",
      "https://example.com/wiki/File:Test.jpg",
      "https://commons.wikimedia.org@other.example/wiki/File:Test.jpg",
      "https://commons.wikimedia.org/wiki/File:Test.jpg?x=1",
    ]
  ) assert.equal(commonsFile(node(1, value)), null, value);
});

Deno.test("one request deduplicates and caps titles without altering spot order or count", async () => {
  let calls = 0;
  const elements = [
    node(1, title),
    node(2),
    node(3, "File:Test_court.jpg"),
    ...Array.from(
      { length: 100 },
      (_, i) => node(i + 4, `File:Court ${i}.jpg`),
    ),
  ];
  const spots = await searchSpotsWithPhotos(elements, (input, options) => {
    calls++;
    const url = new URL(String(input));
    assert.equal(url.origin, "https://commons.wikimedia.org");
    assert.equal(url.pathname, "/w/api.php");
    const titles = url.searchParams.get("titles")!.split("|");
    assert.equal(titles.length, 20);
    assert.equal(titles.filter((t) => t === title).length, 1);
    assert.equal(options?.redirect, "error");
    return Promise.resolve(commonsResponse());
  });
  const photo = {
    url: thumbnail,
    source_url: source,
    author: "Test & Partner",
    license: "CC BY-SA 4.0",
    license_url: license,
  };
  assert.equal(calls, 1);
  assert.equal(spots.length, 100);
  assert.equal(spots[0].id, "osm:node:1");
  assert.deepEqual(spots[0].photo, photo);
  assert.equal("photo" in spots[1], false);
  assert.deepEqual(spots[2].photo, photo);
  assert.equal(spots[99].id, "osm:node:100");
});

Deno.test("wrong file, untrusted media and incomplete or restrictive metadata are omitted", async () => {
  const invalid = [
    () => commonsResponse(imageinfo, "File:Another court.jpg"),
    () =>
      commonsResponse({
        ...imageinfo,
        descriptionurl: source.replace("Test_court", "Another_court"),
      }),
    () =>
      commonsResponse({
        ...imageinfo,
        thumburl: "https://other.example/test.jpg",
      }),
    () =>
      commonsResponse({
        ...imageinfo,
        extmetadata: {
          ...imageinfo.extmetadata,
          Artist: { value: "<script>bad</script>" },
        },
      }),
    () =>
      commonsResponse({
        ...imageinfo,
        extmetadata: { ...imageinfo.extmetadata, LicenseUrl: { value: "" } },
      }),
    () =>
      commonsResponse(
        {
          ...imageinfo,
          extmetadata: {
            ...imageinfo.extmetadata,
            Restrictions: { value: "personality" },
          },
        } as typeof imageinfo,
      ),
  ];
  for (const response of invalid) {
    const [spot] = await searchSpotsWithPhotos(
      [node(1, title)],
      () => Promise.resolve(response()),
    );
    assert.equal("photo" in spot, false);
  }
});

Deno.test("rate limiting, 5xx, invalid JSON, oversize and abort leave search results intact", async () => {
  const responses = [
    () => new Response(null, { status: 429 }),
    () => new Response(null, { status: 503 }),
    () => new Response("{"),
    () => new Response("x".repeat(256 * 1024 + 1)),
  ];
  for (const response of responses) {
    const spots = await searchSpotsWithPhotos(
      [node(1, title), node(2)],
      () => Promise.resolve(response()),
    );
    assert.deepEqual(spots.map((s) => [s.id, s.photo]), [[
      "osm:node:1",
      undefined,
    ], ["osm:node:2", undefined]]);
  }
  const controller = new AbortController();
  const result = await searchSpotsWithPhotos([node(1, title)], (_, options) => {
    controller.abort();
    return Promise.reject(options?.signal?.reason);
  }, controller.signal);
  assert.equal("photo" in result[0], false);
});

Deno.test("no tagged file avoids a Commons request", async () => {
  let calls = 0;
  const [spot] = await searchSpotsWithPhotos([node(1)], () => {
    calls++;
    return Promise.reject(new Error("Unexpected request"));
  });
  assert.equal(calls, 0);
  assert.equal("photo" in spot, false);
});
