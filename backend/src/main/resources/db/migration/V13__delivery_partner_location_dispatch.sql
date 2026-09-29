ALTER TABLE delivery_partner_profiles
  ADD COLUMN latitude DOUBLE NULL,
  ADD COLUMN longitude DOUBLE NULL,
  ADD COLUMN location_updated_at TIMESTAMP(6) NULL;

CREATE INDEX idx_delivery_partner_location
  ON delivery_partner_profiles (available, latitude, longitude);
