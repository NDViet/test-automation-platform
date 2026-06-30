-- V7: drop the legacy team_members RBAC table. Its grants were migrated to user_roles in V6 and the
-- code that read/wrote it (RbacService, RoleController/RoleService, the ADO bootstrap grant path) has
-- been removed in favor of users + user_roles. Runs after V6, so the migration has already consumed
-- the data.
DROP TABLE IF EXISTS team_members;
