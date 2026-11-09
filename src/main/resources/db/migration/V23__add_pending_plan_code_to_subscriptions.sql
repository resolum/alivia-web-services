ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS pending_plan_code character varying(10);
