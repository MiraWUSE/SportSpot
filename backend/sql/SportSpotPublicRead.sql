begin;

grant usage on schema public to anon, authenticated;
grant select on table public.spots, public.reviews to anon, authenticated;

create policy "Public can read spots"
on public.spots for select to anon, authenticated
using (true);

create policy "Public can read reviews"
on public.reviews for select to anon, authenticated
using (true);

commit;
