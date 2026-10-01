-- Root cause of the partner OTP / login HTTP 500:
-- users.phone never had a unique constraint, so the same mobile number could exist on several
-- rows. Spring Data's `Optional<UserAccount> findByPhone(..)` then threw
-- IncorrectResultSizeDataAccessException, which the global handler reported as HTTP 500 with a
-- correlation id. This migration normalizes stored numbers, resolves duplicates, and prevents
-- the situation from reoccurring.

SET SESSION group_concat_max_len = 1000000;

-- 1) Normalize every stored number to the 10-digit Indian national format used by the app.
UPDATE users
SET phone = CASE
    WHEN CHAR_LENGTH(phone) = 14 AND LEFT(phone, 4) = '0091' THEN SUBSTRING(phone, 5)
    WHEN CHAR_LENGTH(phone) = 13 AND LEFT(phone, 3) = '+91'  THEN SUBSTRING(phone, 4)
    WHEN CHAR_LENGTH(phone) = 12 AND LEFT(phone, 2) = '91'   THEN SUBSTRING(phone, 3)
    WHEN CHAR_LENGTH(phone) = 11 AND LEFT(phone, 1) = '0'    THEN SUBSTRING(phone, 2)
    ELSE phone
END
WHERE phone IS NOT NULL AND phone <> '';

UPDATE users SET phone = NULL WHERE phone = '';

-- 2) Keep one account per number (delivery partner wins, then admin, then the oldest account)
--    and release the number from the remaining duplicates. No account row is deleted, so orders,
--    earnings, notifications and profiles stay intact.
UPDATE users u
JOIN (
    SELECT phone,
           CAST(SUBSTRING_INDEX(
               GROUP_CONCAT(id ORDER BY
                   CASE role WHEN 'DELIVERY_PARTNER' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END,
                   CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END,
                   id ASC),
               ',', 1) AS UNSIGNED) AS keep_id
    FROM users
    WHERE phone IS NOT NULL AND phone <> ''
    GROUP BY phone
    HAVING COUNT(*) > 1
) dup ON u.phone = dup.phone AND u.id <> dup.keep_id
SET u.phone = NULL;

-- 3) Enforce uniqueness going forward. Guarded so the migration can never fail the deployment:
--    the index is only created when the column is already clean and the index is absent.
--    (MySQL unique indexes permit multiple NULLs, so accounts without a number are unaffected.)
SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'users'
        AND index_name = 'uk_users_phone') = 0
    AND (SELECT COUNT(*) FROM (
            SELECT phone FROM users
            WHERE phone IS NOT NULL AND phone <> ''
            GROUP BY phone HAVING COUNT(*) > 1
         ) remaining) = 0,
    'CREATE UNIQUE INDEX uk_users_phone ON users (phone)',
    'SELECT 1'
) INTO @create_users_phone_unique;
PREPARE create_users_phone_unique_statement FROM @create_users_phone_unique;
EXECUTE create_users_phone_unique_statement;
DEALLOCATE PREPARE create_users_phone_unique_statement;

-- 4) Speed up OTP rate-limit lookups (count by phone within the last hour).
SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'otp_challenges'
        AND index_name = 'idx_otp_phone_created') = 0,
    'CREATE INDEX idx_otp_phone_created ON otp_challenges (phone, created_at)',
    'SELECT 1'
) INTO @create_otp_phone_created;
PREPARE create_otp_phone_created_statement FROM @create_otp_phone_created;
EXECUTE create_otp_phone_created_statement;
DEALLOCATE PREPARE create_otp_phone_created_statement;
