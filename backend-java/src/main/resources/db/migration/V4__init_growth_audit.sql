create table reflections (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    run_id varchar(64) not null,
    content text not null,
    created_at timestamptz not null default now(),
    unique (run_id),
    constraint fk_reflections_project_run
        foreign key (project_id, run_id)
        references orchestrator_runs(project_id, id)
        on delete cascade
);

create table lessons (
    id varchar(64) primary key,
    reflection_id varchar(64) not null references reflections(id) on delete cascade,
    category varchar(64) not null,
    content text not null,
    confidence varchar(32) not null,
    created_at timestamptz not null default now()
);

create index idx_lessons_reflection on lessons(reflection_id);

create table evolution_records (
    id varchar(64) primary key,
    agent_id varchar(64) not null references agents(id) on delete cascade,
    summary text not null,
    created_at timestamptz not null default now()
);

create table audit_logs (
    id varchar(64) primary key,
    actor_id varchar(64) not null,
    action varchar(96) not null,
    target_type varchar(64) not null,
    target_id varchar(64) not null,
    payload jsonb not null,
    created_at timestamptz not null default now()
);

create index idx_audit_actor_created_id on audit_logs(actor_id, created_at desc, id desc);
create index idx_audit_target on audit_logs(target_type, target_id);
