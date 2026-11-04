begin;

create schema if not exists app_private;
revoke all on schema app_private from public;

create or replace function app_private.create_profile_for_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, name, role)
  values (
    new.id,
    coalesce(left(nullif(btrim(new.raw_user_meta_data ->> 'name'), ''), 80), ''),
    'user'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

revoke all on function app_private.create_profile_for_new_auth_user() from public;

create trigger sportspot_auth_user_created
after insert on auth.users
for each row
execute function app_private.create_profile_for_new_auth_user();

commit;
