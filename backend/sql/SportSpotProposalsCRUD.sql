begin;

-- Авторизованный пользователь может читать только свои предложения.
grant select on table public.spot_proposals to authenticated;

-- Пользователь может отправлять описание площадки.
-- Поля user_id, status и поля модерации недоступны для прямой вставки.
grant insert (
  name, sport_type, latitude, longitude,
  address, surface, lighting, equipment, description
) on table public.spot_proposals to authenticated;

create policy "Users can read own spot proposals"
on public.spot_proposals
for select to authenticated
using (user_id = (select auth.uid()));

create policy "Users can submit own pending spot proposals"
on public.spot_proposals
for insert to authenticated
with check (
  user_id = (select auth.uid())
  and status = 'pending'
  and moderator_id is null
  and reviewed_at is null
  and reject_reason is null
);

commit;
