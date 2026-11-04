begin;

create policy "Moderators can read proposals"
on public.spot_proposals
for select to authenticated
using (
  exists (
    select 1
    from public.profiles as my_profile
    where my_profile.id = (select auth.uid())
      and my_profile.role = 'moderator'
  )
);

commit;
