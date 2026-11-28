import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createSearchHandler } from "./search-handler.ts";

Deno.serve(createSearchHandler({
  supabaseUrl: Deno.env.get("SUPABASE_URL"),
  serviceKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"),
  overpassKey: Deno.env.get("OVERPASS_CH_KEY"),
  overpassPerMinute: Deno.env.get("SPOT_SEARCH_OVERPASS_PER_MINUTE"),
  overpassPerDay: Deno.env.get("SPOT_SEARCH_OVERPASS_PER_DAY"),
}));
