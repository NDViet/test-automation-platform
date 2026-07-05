-- Epic F: notification preferences. Per-project routing of quality events to a channel/destination.
CREATE TABLE notification_pref (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    channel     VARCHAR(20)  NOT NULL,                    -- SLACK | EMAIL | WEBHOOK
    destination VARCHAR(500) NOT NULL,                    -- webhook url / email address
    events      TEXT         NOT NULL,                    -- JSON array of subscribed event names
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_pref_project ON notification_pref (project_id);
