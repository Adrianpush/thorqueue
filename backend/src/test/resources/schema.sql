-- ============================================================
-- ThorQueue Database Schema v2
-- PostgreSQL 16
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- ENUM TYPES
-- ============================================================

CREATE TYPE project_role AS ENUM ('LEAD', 'MEMBER');

CREATE TYPE requirement_status AS ENUM ('IN_PROGRESS', 'FULFILLED');

CREATE TYPE task_status AS ENUM (
    'PROPOSED',
    'PENDING',
    'ASSIGNED',
    'IN_REVIEW',
    'COMPLETED'
);

CREATE TYPE project_ticket_change AS ENUM (
    'UPDATE_PROJECT_DESCRIPTION',
    'CREATE_REQUIREMENT',
    'UPDATE_REQUIREMENT_DESCRIPTION',
    'UPDATE_REQUIREMENT_STATUS'
);

CREATE TYPE task_ticket_change AS ENUM (
    'CREATE_TASK',
    'UPDATE_TASK_DESCRIPTION',
    'UPDATE_STATUS',
    'ASSIGN',
    'REASSIGN',
    'UPDATE_ESTIMATE',
    'UPDATE_DUE_DATE'
);

CREATE TYPE ticket_status AS ENUM (
    'PENDING_APPROVAL',
    'APPROVED',
    'REJECTED',
    'APPLIED'
);

-- ============================================================
-- TABLES
-- ============================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    keycloak_id     VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(255) NOT NULL,
    is_app_admin    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE projects (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE project_memberships (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    role            project_role NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    UNIQUE (user_id, project_id)
);

CREATE TABLE requirements (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    description     TEXT NOT NULL,
    status          requirement_status NOT NULL DEFAULT 'IN_PROGRESS',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE tasks (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    requirement_id  UUID NOT NULL REFERENCES requirements(id) ON DELETE RESTRICT,
    assigned_to     UUID REFERENCES users(id) ON DELETE SET NULL,
    created_by      UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    status          task_status NOT NULL DEFAULT 'PROPOSED',
    due_date        DATE,
    estimated_hours DECIMAL(8, 2),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Project tickets: changes to projects and their requirements
-- requirement_id is NULL when the change targets the project itself
CREATE TABLE project_tickets (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    raised_by       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    requirement_id  UUID REFERENCES requirements(id) ON DELETE CASCADE,
    change_type     project_ticket_change NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}',
    status          ticket_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    auto_approved   BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP WITH TIME ZONE
);

-- Task tickets: changes to tasks
CREATE TABLE task_tickets (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    raised_by       UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    task_id         UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    change_type     task_ticket_change NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}',
    status          ticket_status NOT NULL DEFAULT 'PENDING_APPROVAL',
    auto_approved   BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP WITH TIME ZONE
);

-- ============================================================
-- INDEXES
-- ============================================================

-- Membership lookups
CREATE INDEX idx_memberships_user ON project_memberships(user_id);
CREATE INDEX idx_memberships_project ON project_memberships(project_id);
CREATE INDEX idx_memberships_role ON project_memberships(project_id, role);

-- Requirement lookups
CREATE INDEX idx_requirements_project ON requirements(project_id);
CREATE INDEX idx_requirements_status ON requirements(project_id, status);

-- Task lookups
CREATE INDEX idx_tasks_project ON tasks(project_id);
CREATE INDEX idx_tasks_requirement ON tasks(requirement_id);
CREATE INDEX idx_tasks_assigned_to ON tasks(assigned_to);
CREATE INDEX idx_tasks_status ON tasks(project_id, status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date) WHERE due_date IS NOT NULL;

-- Project ticket lookups
CREATE INDEX idx_proj_tickets_project ON project_tickets(project_id);
CREATE INDEX idx_proj_tickets_raised_by ON project_tickets(raised_by);
CREATE INDEX idx_proj_tickets_requirement ON project_tickets(requirement_id)
    WHERE requirement_id IS NOT NULL;
CREATE INDEX idx_proj_tickets_pending ON project_tickets(project_id, status)
    WHERE status = 'PENDING_APPROVAL';

-- Task ticket lookups
CREATE INDEX idx_task_tickets_task ON task_tickets(task_id);
CREATE INDEX idx_task_tickets_project ON task_tickets(project_id);
CREATE INDEX idx_task_tickets_raised_by ON task_tickets(raised_by);
CREATE INDEX idx_task_tickets_pending ON task_tickets(project_id, status)
    WHERE status = 'PENDING_APPROVAL';

-- ============================================================
-- VIEWS
-- ============================================================

-- Overdue tasks
CREATE VIEW overdue_tasks AS
SELECT t.*, p.name AS project_name, r.description AS requirement_description
FROM tasks t
JOIN projects p ON p.id = t.project_id
JOIN requirements r ON r.id = t.requirement_id
WHERE t.due_date < CURRENT_DATE
  AND t.status != 'COMPLETED';

-- Project summary for reporting
CREATE VIEW project_summary AS
SELECT
    p.id AS project_id,
    p.name AS project_name,
    COUNT(DISTINCT t.id) AS total_tasks,
    COUNT(DISTINCT t.id) FILTER (WHERE t.status = 'COMPLETED') AS completed_tasks,
    COUNT(DISTINCT t.id) FILTER (WHERE t.due_date < CURRENT_DATE AND t.status != 'COMPLETED') AS overdue_tasks,
    COUNT(DISTINCT r.id) AS total_requirements,
    COUNT(DISTINCT r.id) FILTER (WHERE r.status = 'FULFILLED') AS fulfilled_requirements
FROM projects p
LEFT JOIN tasks t ON t.project_id = p.id
LEFT JOIN requirements r ON r.project_id = p.id
GROUP BY p.id, p.name;

-- Pending approvals for a project (union of both ticket types)
CREATE VIEW pending_approvals AS
SELECT
    id, raised_by, project_id, change_type::TEXT, payload,
    created_at, 'PROJECT' AS ticket_category
FROM project_tickets
WHERE status = 'PENDING_APPROVAL'
UNION ALL
SELECT
    id, raised_by, project_id, change_type::TEXT, payload,
    created_at, 'TASK' AS ticket_category
FROM task_tickets
WHERE status = 'PENDING_APPROVAL';