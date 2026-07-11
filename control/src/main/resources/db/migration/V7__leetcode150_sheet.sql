-- Converge any pre-existing catalogue onto the LeetCode 150 sheet. Overlapping slugs
-- are re-seeded (and their sheet reset) by ProblemSeeder on boot; this clears the
-- remaining legacy default so the column no longer references the retired sheet.
UPDATE problems SET sheet = 'leetcode150' WHERE sheet = 'neetcode150';
ALTER TABLE problems ALTER COLUMN sheet SET DEFAULT 'leetcode150';
