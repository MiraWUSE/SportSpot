import { convert, type OSMSpot, type SpotPhoto } from "./search-logic.ts";

const COMMONS = "https://commons.wikimedia.org";
const MAX_FILES = 20;
const THUMB_WIDTH = 500;
const MAX_RESPONSE_BYTES = 256 * 1024;
const IMAGE_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function list(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function httpsUrl(
  value: unknown,
  hostname: string,
  withQuery = false,
): URL | null {
  if (
    typeof value !== "string" || value.length > 2048 ||
    !value.startsWith("https://") || /[\s\\\p{C}]/u.test(value)
  ) return null;
  try {
    const url = new URL(value);
    return url.hostname === hostname && !url.username && !url.password &&
        !url.port && (withQuery || !url.search) && !url.hash
      ? url
      : null;
  } catch {
    return null;
  }
}

function fileTitle(value: unknown): string | null {
  if (typeof value !== "string" || value.length > 2048) return null;
  let title = value.trim();
  if (title.startsWith("https://")) {
    const url = httpsUrl(title, "commons.wikimedia.org");
    if (!url?.pathname.startsWith("/wiki/File:")) return null;
    try {
      title = decodeURIComponent(url.pathname.slice("/wiki/".length));
    } catch {
      return null;
    }
  }
  if (!title.startsWith("File:") || /[|#?%<>\[\]{}\\/;\p{C}]/u.test(title)) {
    return null;
  }
  const name = title.slice(5).replace(/[_ ]+/g, " ").trim().normalize("NFC");
  if (
    new TextEncoder().encode(name).length > 240 ||
    !/^.+\.(?:jpe?g|png|webp)$/iu.test(name)
  ) return null;
  // Commons capitalizes the first character of a File title.
  return "File:" + name[0].toUpperCase() + name.slice(1);
}

export function commonsFile(element: unknown): string | null {
  const tags = record(record(element).tags);
  return fileTitle(tags.wikimedia_commons) ?? fileTitle(tags.image);
}

function authorText(value: string): string | null {
  if (value.length > 4096) return null;
  // Only flatten ordinary inline metadata. Unhandled markup/entities are rejected.
  const text = value.replace(
    /<\/?(?:a|span|b|strong|i|em)(?:\s+[^<>]*)?>/gi,
    "",
  )
    .replace(/<br\s*\/?>/gi, " ")
    .replace(/&(?:amp|quot|apos|nbsp|#39|#160);/gi, (entity) => {
      const entities: Record<string, string> = {
        "&amp;": "&",
        "&quot;": '"',
        "&apos;": "'",
        "&nbsp;": " ",
        "&#39;": "'",
        "&#160;": " ",
      };
      return entities[entity.toLowerCase()];
    }).replace(/\s+/g, " ").trim();
  return text && text.length <= 300 &&
      !/[<>\p{C}]|&(?:#|[a-z]+;)|https?:\/\//iu.test(text) &&
      !/^(?:unknown(?: author)?|anonymous|n\/?a|see below|own work)$/i.test(
        text,
      )
    ? text
    : null;
}

function verifiedPhoto(
  info: Record<string, unknown>,
  title: string,
): SpotPhoto | null {
  const metadata = record(info.extmetadata);
  const field = (name: string) => {
    const value = record(metadata[name]).value;
    return typeof value === "string" ? value.trim() : "";
  };
  // This first version supports the standard 4.0 attribution terms.
  // Skip custom credits/permissions rather than dropping any required terms.
  if (
    field("Attribution") || field("Restrictions") || field("DeletionReason") ||
    field("NonFree").toLowerCase() === "true" || field("Permission")
  ) return null;
  const credit = field("Credit");
  if (
    credit &&
    credit.replace(/<\/?(?:span|a)(?:\s+[^<>]*)?>/gi, "").trim()
        .toLowerCase() !== "own work"
  ) return null;
  const author = authorText(field("Artist"));
  const license = field("LicenseShortName");
  const licensePath = license === "CC BY 4.0"
    ? "by"
    : license === "CC BY-SA 4.0"
    ? "by-sa"
    : null;
  if (!author || !licensePath) return null;
  const licenseUrl = `https://creativecommons.org/licenses/${licensePath}/4.0/`;
  if (![licenseUrl, licenseUrl.slice(0, -1)].includes(field("LicenseUrl"))) {
    return null;
  }
  const url = httpsUrl(info.thumburl, "thumb.wikimedia.org", true) ??
    httpsUrl(info.thumburl, "upload.wikimedia.org", true);
  const source = httpsUrl(info.descriptionurl, "commons.wikimedia.org");
  if (
    !url?.pathname.startsWith("/wikipedia/commons/thumb/") ||
    !IMAGE_TYPES.has(String(info.mime)) || !source ||
    fileTitle(source.href) !== title ||
    typeof info.thumbwidth !== "number" || info.thumbwidth < 1 ||
    info.thumbwidth > THUMB_WIDTH
  ) return null;
  // Imageinfo may add campaign parameters to the CDN URL. Keep the returned path.
  if (
    [...url.searchParams.keys()].some((key) =>
      !["utm_source", "utm_campaign", "utm_content"].includes(key)
    )
  ) return null;
  url.search = "";
  return {
    url: url.href,
    source_url: source.href,
    author,
    license,
    license_url: licenseUrl,
  };
}

async function readMetadata(response: Response): Promise<unknown> {
  if (
    !response.ok || !response.body ||
    Number(response.headers.get("content-length")) > MAX_RESPONSE_BYTES
  ) {
    await response.body?.cancel();
    return null;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let bytes = 0;
  let body = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) return JSON.parse(body + decoder.decode());
      bytes += value.byteLength;
      if (bytes > MAX_RESPONSE_BYTES) return null;
      body += decoder.decode(value, { stream: true });
    }
  } finally {
    await reader.cancel();
    reader.releaseLock();
  }
}

async function resolvePhotos(
  titles: string[],
  request: typeof fetch,
  signal: AbortSignal,
): Promise<Map<string, SpotPhoto>> {
  const photos = new Map<string, SpotPhoto>();
  if (!titles.length) return photos;
  const params = new URLSearchParams({
    action: "query",
    format: "json",
    formatversion: "2",
    prop: "imageinfo",
    iiprop: "url|mime|extmetadata",
    iiurlwidth: String(THUMB_WIDTH),
    iiextmetadatafilter:
      "Artist|Credit|Attribution|Permission|LicenseShortName|LicenseUrl|NonFree|Restrictions|DeletionReason",
    titles: titles.join("|"),
  });

  try {
    const response = await request(`${COMMONS}/w/api.php?${params}`, {
      headers: {
        "User-Agent": "SportSpot/1.0 (+https://github.com/MiraWUSE/SportSpot)",
        Accept: "application/json",
      },
      redirect: "error",
      signal,
    });
    const query = record(record(await readMetadata(response)).query);
    const wanted = new Set(titles);
    for (const value of list(query.pages)) {
      const page = record(value);
      const title = fileTitle(page.title);
      if (!title || !wanted.has(title) || page.missing || page.ns !== 6) {
        continue;
      }
      const photo = verifiedPhoto(record(list(page.imageinfo)[0]), title);
      if (photo) photos.set(title, photo);
    }
  } catch {
    // Commons is optional. Keep the same search results on any transport/parse failure.
  }
  return photos;
}

export async function searchSpotsWithPhotos(
  elements: unknown[],
  request: typeof fetch = fetch,
  signal?: AbortSignal,
): Promise<OSMSpot[]> {
  const results: { spot: OSMSpot; title: string | null }[] = [];
  for (const element of elements) {
    const spot = convert(element);
    if (spot) results.push({ spot, title: commonsFile(element) });
    if (results.length === 100) break;
  }

  const titles = [
    ...new Set(results.flatMap(({ title }) => title ? [title] : [])),
  ].slice(0, MAX_FILES);
  const photos = await resolvePhotos(
    titles,
    request,
    signal ?? AbortSignal.timeout(2000),
  );
  return results.map(({ spot, title }) => {
    const photo = title ? photos.get(title) : undefined;
    return photo ? { ...spot, photo } : spot;
  });
}
