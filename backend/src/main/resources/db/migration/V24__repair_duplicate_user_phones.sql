-- V23 was already marked as applied in some Railway databases before its
-- duplicate-cleanup SQL was corrected. Re-run the cleanup as a new migration.
-- No user rows are deleted; duplicate rows simply release their phone value.

SET SESSION group_concat_max_len = 1000000;

UPDATE users
SET phone = CASE
    WHEN CHAR_LENGTH(phone) = 14 AND LEFT(phone, 4) = '0091' THEN SUBSTRING(phone, 5)
    WHEN CHAR_LENGTH(phone) = 13 AND LEFT(phone, 3) = '+91' THEN SUBSTRING(phone, 4)
    WHEN CHAR_LENGTH(phone) = 12 AND LEFT(phone, 2) = '91' THEN SUBSTRING(phone, 3)
    WHEN CHAR_LENGTH(phone) = 11 AND LEFT(phone, 1) = '0' THEN SUBSTRING(phone, 2)
    ELSE phone
END
WHERE phone IS NOT NULL AND phone <> '';

UPDATE users SET phone = NULL WHERE phone = '';

CREATE TEMPORARY TABLE otp_phone_duplicates (
  phone VARCHAR(40) NOT NULL PRIMARY KEY,
  keep_id BIGINT NOT NULL
);

INSERT INTO otp_phone_duplicates (phone, keep_id)
SELECT phone,
       CAST(SUBSTRING_INDEX(
           GROUP_CONCAT(id ORDER BY
               CASE role WHEN 'DELIVERY_PARTNER' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END,
               CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END,
               id ASC),
           ',', 1) AS UNSIGNED)
FROM users
WHERE phone IS NOT NULL AND phone <> ''
GROUP BY phone
HAVING COUNT(*) > 1;

UPDATE users u
JOIN otp_phone_duplicates d ON d.phone = u.phone AND d.keep_id <> u.id
SET u.phone = NULL;

DROP TEMPORARY TABLE otp_phone_duplicates;

SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'users'
        AND index_name = 'uk_users_phone') = 0,
    'CREATE UNIQUE INDEX uk_users_phone ON users (phone)',
    'SELECT 1'
) INTO @create_users_phone_unique_v24;
PREPARE create_users_phone_unique_v24_statement FROM @create_users_phone_unique_v24;
EXECUTE create_users_phone_unique_v24_statement;
DEALLOCATE PREPARE create_users_phone_unique_v24_statement;
