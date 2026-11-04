begin;

-- Разрешаем только необходимые операции клиентской роли.
grant select on table public.favorites to authenticated;
grant insert (spot_id) on table public.favorites to authenticated;
grant delete on table public.favorites to authenticated;

-- SELECT: пользователь видит только свое избранное.
create policy "Users can read own favorites"
on public.favorites
for select to authenticated
using (user_id = (select auth.uid()));

-- INSERT: пользователь добавляет площадку только в свое избранное.
create policy "Users can create own favorites"
on public.favorites
for insert to authenticated
with check (user_id = (select auth.uid()));

-- DELETE: пользователь удаляет только собственные записи.
create policy "Users can delete own favorites"
on public.favorites
for delete to authenticated
using (user_id = (select auth.uid()));

commit;
