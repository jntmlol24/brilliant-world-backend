-- Migration: Add indexes for userAccount fuzzy search optimization
-- Date: 2026-04-27
-- Description: Add indexes to improve fuzzy search performance on userAccount field

-- Add a regular index on userAccount for exact and prefix matches
-- This will help with queries that start with a specific prefix
ALTER TABLE `user` ADD INDEX idx_user_account (userAccount);

-- Add a lowercase functional index for case-insensitive fuzzy search optimization
-- This is available in MySQL 8.0+ and provides better performance for LOWER() function-based queries
ALTER TABLE `user` ADD INDEX idx_user_account_lower ((LOWER(userAccount)));

-- Alternative: If using MySQL 8.0+ with generated columns, you can create a generated column for case-insensitive search
-- ALTER TABLE `user` ADD COLUMN userAccount_lower VARCHAR(256) GENERATED ALWAYS AS (LOWER(userAccount)) STORED;
-- ALTER TABLE `user` ADD INDEX idx_user_account_lower ON userAccount_lower;

-- For very large datasets, consider full-text index (but note: full-text search works differently than LIKE)
-- ALTER TABLE `user` ADD FULLTEXT INDEX ft_user_account (userAccount);

-- Note: For the fuzzy search using LOWER(userAccount) LIKE CONCAT('%', keyword, '%')
-- The performance considerations are:
-- 1. Leading wildcard (%) prevents use of B-tree index for range scans
-- 2. However, the index still helps with:
--    - Exact matches (no leading %)
--    - Prefix matches (WHERE userAccount LIKE 'test%')
--    - Reducing the number of rows that need to be scanned
-- 3. For better fuzzy search performance with leading wildcards, consider:
--    - Elasticsearch or similar search engine
--    - MySQL's full-text search (limited)
--    - Caching frequently searched terms

-- If you encounter performance issues with very large datasets, consider implementing:
-- 1. Caching layer for popular search results
-- 2. Debouncing search requests in the frontend
-- 3. Limiting search result set size
-- 4. Using a dedicated search solution like Elasticsearch
