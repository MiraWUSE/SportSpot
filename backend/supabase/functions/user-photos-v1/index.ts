import { photoHandler } from "./photo-handler.ts";

Deno.serve(photoHandler({
  origin: Deno.env.get("SUPABASE_URL"),
  serverKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"),
  cleanupToken: Deno.env.get("SPORTSPOT_PHOTO_CLEANUP_TOKEN"),
}));
