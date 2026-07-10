-- LeetCode-style test-runner metadata per problem (entry point, param types, test
-- cases). Nullable: problems without it keep the plain "run source as-is" behavior.
ALTER TABLE problems ADD COLUMN harness jsonb;
