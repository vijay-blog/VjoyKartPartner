SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE delivery_partner_profiles ADD COLUMN date_of_birth VARCHAR(20) NULL',
    'SELECT 1'
) INTO @add_profile_date_of_birth
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'delivery_partner_profiles'
  AND column_name = 'date_of_birth';
PREPARE add_profile_date_of_birth_statement FROM @add_profile_date_of_birth;
EXECUTE add_profile_date_of_birth_statement;
DEALLOCATE PREPARE add_profile_date_of_birth_statement;

SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE delivery_partner_profiles ADD COLUMN driving_license_number VARCHAR(50) NULL',
    'SELECT 1'
) INTO @add_profile_driving_license_number
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'delivery_partner_profiles'
  AND column_name = 'driving_license_number';
PREPARE add_profile_driving_license_number_statement FROM @add_profile_driving_license_number;
EXECUTE add_profile_driving_license_number_statement;
DEALLOCATE PREPARE add_profile_driving_license_number_statement;

SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE delivery_partner_profiles ADD COLUMN aadhaar_number VARCHAR(20) NULL',
    'SELECT 1'
) INTO @add_profile_aadhaar_number
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'delivery_partner_profiles'
  AND column_name = 'aadhaar_number';
PREPARE add_profile_aadhaar_number_statement FROM @add_profile_aadhaar_number;
EXECUTE add_profile_aadhaar_number_statement;
DEALLOCATE PREPARE add_profile_aadhaar_number_statement;

SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE delivery_partner_profiles ADD COLUMN aadhaar_photo_data LONGBLOB NULL',
    'SELECT 1'
) INTO @add_profile_aadhaar_photo_data
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'delivery_partner_profiles'
  AND column_name = 'aadhaar_photo_data';
PREPARE add_profile_aadhaar_photo_data_statement FROM @add_profile_aadhaar_photo_data;
EXECUTE add_profile_aadhaar_photo_data_statement;
DEALLOCATE PREPARE add_profile_aadhaar_photo_data_statement;

SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE delivery_partner_profiles ADD COLUMN aadhaar_photo_content_type VARCHAR(100) NULL',
    'SELECT 1'
) INTO @add_profile_aadhaar_photo_content_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'delivery_partner_profiles'
  AND column_name = 'aadhaar_photo_content_type';
PREPARE add_profile_aadhaar_photo_content_type_statement FROM @add_profile_aadhaar_photo_content_type;
EXECUTE add_profile_aadhaar_photo_content_type_statement;
DEALLOCATE PREPARE add_profile_aadhaar_photo_content_type_statement;

CREATE TABLE IF NOT EXISTS otp_challenges (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(20) NOT NULL,
  otp_hash VARCHAR(128) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP(6) NOT NULL,
  KEY idx_otp_phone_created (phone, created_at)
);
