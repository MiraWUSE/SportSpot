begin;

grant insert (spot_id, rating, text) on table public.reviews to authenticated;
grant update (rating, text) on table public.reviews to authenticated;
grant delete on table public.reviews to authenticated;

create policy "Users can create own reviews"
on public.reviews
for insert to authenticated
with check (user_id = (select auth.uid()));

create policy "Users can update own reviews"
on public.reviews
for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

create policy "Users can delete own reviews"
on public.reviews
for delete to authenticated
using (user_id = (select auth.uid()));

commit;
