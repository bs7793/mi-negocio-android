-- Seed: legacy default workspace id (used by early migrations). New signups get a personal workspace via DB trigger.
INSERT INTO workspaces (id, name)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'Default Workspace')
ON CONFLICT (id) DO NOTHING;
