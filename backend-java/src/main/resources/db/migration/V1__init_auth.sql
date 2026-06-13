create table user_roles (
    id bigserial primary key,
    code varchar(64) not null unique,
    name varchar(128) not null,
    created_at timestamptz not null default now()
);

create table user_accounts (
    id varchar(64) primary key,
    email varchar(255) not null unique,
    display_name varchar(128) not null,
    password_hash varchar(255) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table user_account_roles (
    user_id varchar(64) not null references user_accounts(id) on delete cascade,
    role_id bigint not null references user_roles(id) on delete cascade,
    primary key (user_id, role_id)
);
