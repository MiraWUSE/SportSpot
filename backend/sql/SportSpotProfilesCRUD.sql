begin;

grant select on table public.profiles to authenticated;

-- Клиент может менять только имя, но не роль, ID или дату создания.
grant update (name) on table public.profiles to authenticated;

create policy "Users can read own profile"
on public.profiles
for select to authenticated
using (id = (select auth.uid()));

create policy "Users can update own profile"
on public.profiles
for update to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

commit;
