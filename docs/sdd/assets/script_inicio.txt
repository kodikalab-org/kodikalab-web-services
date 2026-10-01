CREATE TABLE users
(
  id            BIGINT                   NOT NULL GENERATED ALWAYS AS IDENTITY,
  first_name    VARCHAR(80)              NOT NULL,
  last_name     VARCHAR(80)              NOT NULL,
  email         VARCHAR(255)             NOT NULL UNIQUE,
  password_hash VARCHAR(255)             NOT NULL,
  status        VARCHAR(30)              NOT NULL DEFAULT 'ACTIVE',
  role          VARCHAR(30)              NOT NULL,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE competitive_profiles
(
  id         BIGINT      NOT NULL GENERATED ALWAYS AS IDENTITY,
  level      VARCHAR(30) NOT NULL,
  experience TEXT,
  user_id    BIGINT      NOT NULL UNIQUE,
  PRIMARY KEY (id)
);

CREATE TABLE external_accounts
(
  id                     BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY,
  platform               VARCHAR(50)  NOT NULL,
  handle                 VARCHAR(100) NOT NULL,
  profile_url            VARCHAR(500),
  verified               BOOLEAN      NOT NULL DEFAULT FALSE,
  competitive_profile_id BIGINT       NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE teams
(
  id          BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY,
  name        VARCHAR(120) NOT NULL,
  level       VARCHAR(30)  NOT NULL,
  description TEXT,
  status      VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
  coach_id    BIGINT       NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE team_schedules
(
  id         BIGINT      NOT NULL GENERATED ALWAYS AS IDENTITY,
  team_id    BIGINT      NOT NULL,
  week_day   VARCHAR(15) NOT NULL,
  start_time TIME        NOT NULL,
  end_time   TIME        NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE user_availabilities
(
  id         BIGINT      NOT NULL GENERATED ALWAYS AS IDENTITY,
  user_id    BIGINT      NOT NULL,
  week_day   VARCHAR(15) NOT NULL,
  start_time TIME        NOT NULL,
  end_time   TIME        NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE team_memberships
(
  id        BIGINT                   NOT NULL GENERATED ALWAYS AS IDENTITY,
  joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status    VARCHAR(30)              NOT NULL DEFAULT 'ACTIVE',
  user_id   BIGINT                   NOT NULL,
  team_id   BIGINT                   NOT NULL,
  left_at   TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);

CREATE TABLE join_requests
(
  id           BIGINT                   NOT NULL GENERATED ALWAYS AS IDENTITY,
  requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status       VARCHAR(30)              NOT NULL DEFAULT 'PENDING',
  observation  TEXT,
  user_id      BIGINT                   NOT NULL,
  team_id      BIGINT                   NOT NULL,
  responded_at TIMESTAMP WITH TIME ZONE,
  reviewer_id  BIGINT,
  PRIMARY KEY (id)
);

CREATE TABLE competitions
(
  id       BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY,
  name     VARCHAR(150) NOT NULL,
  date     DATE         NOT NULL,
  platform VARCHAR(80)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE competition_results
(
  id              BIGINT           NOT NULL GENERATED ALWAYS AS IDENTITY,
  rank_position   INTEGER,
  score           DOUBLE PRECISION,
  solved_problems INTEGER          NOT NULL DEFAULT 0,
  status          VARCHAR(30)      NOT NULL DEFAULT 'PENDING',
  team_id         BIGINT           NOT NULL,
  competition_id  BIGINT           NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE topics
(
  id          BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY,
  name        VARCHAR(100) NOT NULL UNIQUE,
  description TEXT,
  PRIMARY KEY (id)
);

CREATE TABLE problems
(
  id          BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY,
  title       VARCHAR(200) NOT NULL,
  description TEXT,
  difficulty  VARCHAR(30)  NOT NULL,
  url         VARCHAR(500) NOT NULL,
  status      VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (id)
);

CREATE TABLE problem_topics
(
  problem_id BIGINT NOT NULL,
  topic_id   BIGINT NOT NULL,
  PRIMARY KEY (problem_id, topic_id)
);

CREATE TABLE academic_resources
(
  id     BIGINT       NOT NULL GENERATED ALWAYS AS IDENTITY,
  title  VARCHAR(200) NOT NULL,
  author VARCHAR(150),
  type   VARCHAR(50)  NOT NULL,
  url    VARCHAR(500) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE academic_resource_topics
(
  academic_resource_id BIGINT NOT NULL,
  topic_id             BIGINT NOT NULL,
  PRIMARY KEY (academic_resource_id, topic_id)
);

CREATE TABLE problem_academic_resources
(
  academic_resource_id BIGINT NOT NULL,
  problem_id           BIGINT NOT NULL,
  PRIMARY KEY (academic_resource_id, problem_id)
);

CREATE TABLE assignments
(
  id          BIGINT                   NOT NULL GENERATED ALWAYS AS IDENTITY,
  title       VARCHAR(150)             NOT NULL,
  description TEXT,
  assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  due_at      TIMESTAMP WITH TIME ZONE,
  status      VARCHAR(30)              NOT NULL DEFAULT 'ACTIVE',
  creator_id  BIGINT                   NOT NULL,
  team_id     BIGINT                   NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE assignment_recipients
(
  assignment_id      BIGINT NOT NULL,
  team_membership_id BIGINT NOT NULL,
  PRIMARY KEY (assignment_id, team_membership_id)
);

CREATE TABLE assignment_details
(
  id            BIGINT  NOT NULL GENERATED ALWAYS AS IDENTITY,
  display_order INTEGER NOT NULL DEFAULT 1,
  assignment_id BIGINT  NOT NULL,
  problem_id    BIGINT  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE submissions
(
  id                     BIGINT                   NOT NULL GENERATED ALWAYS AS IDENTITY,
  submitted_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status                 VARCHAR(30)              NOT NULL DEFAULT 'PENDING',
  score                  DOUBLE PRECISION,
  evidence_url           VARCHAR(500),
  team_membership_id     BIGINT                   NOT NULL,
  assignment_detail_id   BIGINT                   NOT NULL,
  evidence_platform      VARCHAR(50),
  external_submission_id VARCHAR(150),
  validated_at           TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);

CREATE TABLE ai_conversations
(
  id                  BIGINT                   NOT NULL GENERATED ALWAYS AS IDENTITY,
  user_id             BIGINT                   NOT NULL,
  started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
  last_interaction_at TIMESTAMP WITH TIME ZONE NOT NULL,
  status              VARCHAR(30)              NOT NULL,
  title               VARCHAR(200),
  PRIMARY KEY (id)
);

CREATE TABLE ai_messages
(
  id              BIGINT                   NOT NULL GENERATED ALWAYS AS IDENTITY,
  conversation_id BIGINT                   NOT NULL,
  role            VARCHAR(20)              NOT NULL,
  content         TEXT                     NOT NULL,
  created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
  metadata        JSONB,
  PRIMARY KEY (id)
);

CREATE TABLE agent_actions
(
  id                    BIGINT                   NOT NULL GENERATED ALWAYS AS IDENTITY,
  conversation_id       BIGINT                   NOT NULL,
  origin_message_id     BIGINT,
  action_type           VARCHAR(100)             NOT NULL,
  parameters            JSONB                    NOT NULL DEFAULT '{}'::jsonb,
  status                VARCHAR(30)              NOT NULL,
  requested_at          TIMESTAMP WITH TIME ZONE NOT NULL,
  executed_at           TIMESTAMP WITH TIME ZONE,
  result                JSONB,
  requires_confirmation BOOLEAN                  NOT NULL DEFAULT false,
  confirmed_at          TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);

ALTER TABLE competitive_profiles ADD CONSTRAINT fk_users_to_competitive_profiles FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE external_accounts ADD CONSTRAINT fk_competitive_profiles_to_external_accounts FOREIGN KEY (competitive_profile_id) REFERENCES competitive_profiles (id);
ALTER TABLE teams ADD CONSTRAINT fk_users_to_teams FOREIGN KEY (coach_id) REFERENCES users (id);
ALTER TABLE team_schedules ADD CONSTRAINT fk_teams_to_team_schedules FOREIGN KEY (team_id) REFERENCES teams (id);
ALTER TABLE user_availabilities ADD CONSTRAINT fk_users_to_user_availabilities FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE team_memberships ADD CONSTRAINT fk_users_to_team_memberships FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE team_memberships ADD CONSTRAINT fk_teams_to_team_memberships FOREIGN KEY (team_id) REFERENCES teams (id);
ALTER TABLE join_requests ADD CONSTRAINT fk_users_to_join_requests FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE join_requests ADD CONSTRAINT fk_teams_to_join_requests FOREIGN KEY (team_id) REFERENCES teams (id);
ALTER TABLE join_requests ADD CONSTRAINT fk_users_to_join_requests_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id);
ALTER TABLE competition_results ADD CONSTRAINT fk_teams_to_competition_results FOREIGN KEY (team_id) REFERENCES teams (id);
ALTER TABLE competition_results ADD CONSTRAINT fk_competitions_to_competition_results FOREIGN KEY (competition_id) REFERENCES competitions (id);
ALTER TABLE problem_topics ADD CONSTRAINT fk_problems_to_problem_topics FOREIGN KEY (problem_id) REFERENCES problems (id);
ALTER TABLE problem_topics ADD CONSTRAINT fk_topics_to_problem_topics FOREIGN KEY (topic_id) REFERENCES topics (id);
ALTER TABLE academic_resource_topics ADD CONSTRAINT fk_academic_resources_to_academic_resource_topics FOREIGN KEY (academic_resource_id) REFERENCES academic_resources (id);
ALTER TABLE academic_resource_topics ADD CONSTRAINT fk_topics_to_academic_resource_topics FOREIGN KEY (topic_id) REFERENCES topics (id);
ALTER TABLE problem_academic_resources ADD CONSTRAINT fk_academic_resources_to_problem_academic_resources FOREIGN KEY (academic_resource_id) REFERENCES academic_resources (id);
ALTER TABLE problem_academic_resources ADD CONSTRAINT fk_problems_to_problem_academic_resources FOREIGN KEY (problem_id) REFERENCES problems (id);
ALTER TABLE assignments ADD CONSTRAINT fk_users_to_assignments FOREIGN KEY (creator_id) REFERENCES users (id);
ALTER TABLE assignments ADD CONSTRAINT fk_teams_to_assignments FOREIGN KEY (team_id) REFERENCES teams (id);
ALTER TABLE assignment_recipients ADD CONSTRAINT fk_assignments_to_assignment_recipients FOREIGN KEY (assignment_id) REFERENCES assignments (id);
ALTER TABLE assignment_recipients ADD CONSTRAINT fk_team_memberships_to_assignment_recipients FOREIGN KEY (team_membership_id) REFERENCES team_memberships (id);
ALTER TABLE assignment_details ADD CONSTRAINT fk_assignments_to_assignment_details FOREIGN KEY (assignment_id) REFERENCES assignments (id);
ALTER TABLE assignment_details ADD CONSTRAINT fk_problems_to_assignment_details FOREIGN KEY (problem_id) REFERENCES problems (id);
ALTER TABLE submissions ADD CONSTRAINT fk_team_memberships_to_submissions FOREIGN KEY (team_membership_id) REFERENCES team_memberships (id);
ALTER TABLE submissions ADD CONSTRAINT fk_assignment_details_to_submissions FOREIGN KEY (assignment_detail_id) REFERENCES assignment_details (id);
ALTER TABLE ai_conversations ADD CONSTRAINT fk_users_to_ai_conversations FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE ai_messages ADD CONSTRAINT fk_ai_conversations_to_ai_messages FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id);
ALTER TABLE agent_actions ADD CONSTRAINT fk_ai_conversations_to_agent_actions FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id);
ALTER TABLE agent_actions ADD CONSTRAINT fk_ai_messages_to_agent_actions FOREIGN KEY (origin_message_id) REFERENCES ai_messages (id);

CREATE UNIQUE INDEX uq_assignment_details_assignment_problem ON assignment_details (assignment_id, problem_id);
CREATE UNIQUE INDEX uq_competition_results_team_competition ON competition_results (team_id, competition_id);
CREATE UNIQUE INDEX uq_external_accounts_platform_handle ON external_accounts (platform, handle);
CREATE UNIQUE INDEX uq_submissions_membership_detail ON submissions (team_membership_id, assignment_detail_id);
CREATE UNIQUE INDEX uq_teams_coach_name ON teams (coach_id, name);
CREATE UNIQUE INDEX uq_problems_url ON problems (url);
CREATE UNIQUE INDEX uq_academic_resources_url ON academic_resources (url);
CREATE UNIQUE INDEX uq_team_schedules_team_day_interval ON team_schedules (team_id, week_day, start_time, end_time);
CREATE UNIQUE INDEX uq_user_availabilities_user_day_interval ON user_availabilities (user_id, week_day, start_time, end_time);

CREATE INDEX idx_ai_conversations_user_last_interaction ON ai_conversations (user_id, last_interaction_at);
CREATE INDEX idx_ai_messages_conversation_created_at ON ai_messages (conversation_id, created_at);
CREATE INDEX idx_agent_actions_conversation_status ON agent_actions (conversation_id, status);
CREATE INDEX idx_agent_actions_origin_message ON agent_actions (origin_message_id);
CREATE INDEX idx_join_requests_user_team_status ON join_requests (user_id, team_id, status);
CREATE INDEX idx_join_requests_team_status ON join_requests (team_id, status);
CREATE INDEX idx_team_memberships_user_team_status ON team_memberships (user_id, team_id, status);
CREATE INDEX idx_team_memberships_team_status ON team_memberships (team_id, status);
CREATE INDEX idx_assignments_team ON assignments (team_id);
CREATE INDEX idx_assignments_creator ON assignments (creator_id);
CREATE INDEX idx_competition_results_competition ON competition_results (competition_id);
CREATE INDEX idx_external_accounts_profile ON external_accounts (competitive_profile_id);
CREATE INDEX idx_assignment_details_problem ON assignment_details (problem_id);
CREATE INDEX idx_submissions_assignment_detail ON submissions (assignment_detail_id);
CREATE INDEX idx_problem_topics_topic ON problem_topics (topic_id);
CREATE INDEX idx_problem_academic_resources_problem ON problem_academic_resources (problem_id);
CREATE INDEX idx_assignment_recipients_membership ON assignment_recipients (team_membership_id);
CREATE INDEX idx_academic_resource_topics_topic ON academic_resource_topics (topic_id);
