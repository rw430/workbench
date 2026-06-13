create table projects (
    id varchar(64) primary key,
    goal text not null,
    mode varchar(32) not null,
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table rooms (
    id varchar(64) primary key,
    project_id varchar(64) not null references projects(id) on delete cascade,
    name varchar(128) not null,
    created_at timestamptz not null default now()
);

create unique index uq_rooms_project on rooms(project_id);

create table orchestrator_runs (
    id varchar(64) primary key,
    project_id varchar(64) not null references projects(id) on delete cascade,
    template_id varchar(128) not null,
    status varchar(32) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (project_id, id)
);

create index idx_runs_project on orchestrator_runs(project_id);

create table orchestrator_tasks (
    id varchar(64) primary key,
    run_id varchar(64) not null references orchestrator_runs(id) on delete cascade,
    node_id varchar(128) not null,
    name varchar(128) not null,
    kind varchar(64) not null,
    role varchar(32) not null,
    status varchar(32) not null,
    output text not null default '',
    log text not null default '',
    sort_order integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (run_id, node_id),
    unique (run_id, id)
);

create index idx_tasks_run_sort on orchestrator_tasks(run_id, sort_order);
create index idx_tasks_run_status on orchestrator_tasks(run_id, status);

create table task_edges (
    id varchar(64) primary key,
    run_id varchar(64) not null,
    source_node_id varchar(128) not null,
    target_node_id varchar(128) not null,
    unique (run_id, source_node_id, target_node_id),
    constraint fk_task_edges_source
        foreign key (run_id, source_node_id)
        references orchestrator_tasks(run_id, node_id)
        on delete cascade,
    constraint fk_task_edges_target
        foreign key (run_id, target_node_id)
        references orchestrator_tasks(run_id, node_id)
        on delete cascade
);

create table human_gates (
    id varchar(64) primary key,
    run_id varchar(64) not null references orchestrator_runs(id) on delete cascade,
    task_id varchar(64) not null,
    status varchar(32) not null,
    prompt text not null,
    decision_reason text,
    decided_by varchar(64),
    decided_at timestamptz,
    created_at timestamptz not null default now(),
    constraint fk_human_gates_run_task
        foreign key (run_id, task_id)
        references orchestrator_tasks(run_id, id)
        on delete cascade
);

create index idx_human_gates_run on human_gates(run_id);
create unique index uq_human_gates_task on human_gates(task_id);

create table runtime_events (
    id varchar(64) primary key,
    run_id varchar(64) not null references orchestrator_runs(id) on delete cascade,
    event_type varchar(96) not null,
    payload text not null,
    created_at timestamptz not null default now()
);

create index idx_runtime_events_run_created_id on runtime_events(run_id, created_at, id);

create table artifacts (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    run_id varchar(64) not null,
    content text not null,
    created_at timestamptz not null default now(),
    unique (run_id),
    constraint fk_artifacts_project_run
        foreign key (project_id, run_id)
        references orchestrator_runs(project_id, id)
        on delete cascade
);
