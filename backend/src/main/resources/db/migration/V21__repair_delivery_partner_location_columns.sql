SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE delivery_partner_profiles ADD COLUMN latitude DOUBLE NULL',
    'SELECT 1'
)
INTO @add_profile_latitude
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'delivery_partner_profiles'
  AND column_name = 'latitude';
PREPARE add_profile_latitude_statement FROM @add_profile_latitude;
EXECUTE add_profile_latitude_statement;
DEALLOCATE PREPARE add_profile_latitude_statement;

SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE delivery_partner_profiles ADD COLUMN longitude DOUBLE NULL',
    'SELECT 1'
)
INTO @add_profile_longitude
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'delivery_partner_profiles'
  AND column_name = 'longitude';
PREPARE add_profile_longitude_statement FROM @add_profile_longitude;
EXECUTE add_profile_longitude_statement;
DEALLOCATE PREPARE add_profile_longitude_statement;

SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE delivery_partner_profiles ADD COLUMN location_updated_at TIMESTAMP(6) NULL',
    'SELECT 1'
)
INTO @add_profile_location_updated_at
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'delivery_partner_profiles'
  AND column_name = 'location_updated_at';
PREPARE add_profile_location_updated_at_statement FROM @add_profile_location_updated_at;
EXECUTE add_profile_location_updated_at_statement;
DEALLOCATE PREPARE add_profile_location_updated_at_statement;

SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX idx_delivery_partner_location ON delivery_partner_profiles (available, latitude, longitude)',
    'SELECT 1'
)
INTO @add_delivery_partner_location_index
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'delivery_partner_profiles'
  AND index_name = 'idx_delivery_partner_location';
PREPARE add_delivery_partner_location_index_statement FROM @add_delivery_partner_location_index;
EXECUTE add_delivery_partner_location_index_statement;
DEALLOCATE PREPARE add_delivery_partner_location_index_statement;
