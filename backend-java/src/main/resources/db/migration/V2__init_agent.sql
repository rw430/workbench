create table agents (
    id varchar(64) primary key,
    name varchar(128) not null,
    role varchar(32) not null,
    description text not null,
    skills text not null,
    domain_tags text not null,
    enabled boolean not null default true,
    score numeric(5, 4) not null default 0,
    recommendation_reason text not null,
    sort_order integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_agents_enabled_sort on agents(enabled, sort_order);
create index idx_agents_role on agents(role);
