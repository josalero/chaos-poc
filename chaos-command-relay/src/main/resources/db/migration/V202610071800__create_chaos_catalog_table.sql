create table chaos_catalog_entry (
  catalog_id uuid not null primary key,
  target_application varchar(255) not null,
  label varchar(255) not null,
  action varchar(40) not null,
  assault varchar(8000),
  created_at timestamp not null,
  constraint uk_chaos_catalog_label unique (target_application, label)
);

create index idx_chaos_catalog_app
  on chaos_catalog_entry (target_application, label);
