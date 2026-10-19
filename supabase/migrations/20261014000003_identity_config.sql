-- M1: identity and configuration tables (IMPLEMENTATION_PLAN.md §4.2)

CREATE TABLE IF NOT EXISTS profiles (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  role user_role NOT NULL DEFAULT 'customer',
  display_name text,
  phone text,
  login_id text UNIQUE,
  is_active boolean NOT NULL DEFAULT true,
  must_change_password boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_profiles_role ON profiles(role);
CREATE INDEX IF NOT EXISTS idx_profiles_login_id ON profiles(login_id);

DROP TRIGGER IF EXISTS trg_profiles_updated_at ON profiles;
CREATE TRIGGER trg_profiles_updated_at BEFORE UPDATE ON profiles
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS store_settings (
  id boolean PRIMARY KEY DEFAULT true CHECK (id),
  store_open boolean NOT NULL DEFAULT true,
  timezone text NOT NULL DEFAULT 'Asia/Kolkata',
  weekly_hours jsonb NOT NULL DEFAULT '{}'::jsonb,
  contact_phone text NOT NULL DEFAULT '',
  estimated_window_text text,
  delivery_fee_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_fee_paise >= 0),
  delivery_charge_threshold_paise bigint NOT NULL DEFAULT 0 CHECK (delivery_charge_threshold_paise >= 0),
  free_delivery_threshold_paise bigint
    CHECK (free_delivery_threshold_paise IS NULL
           OR free_delivery_threshold_paise >= delivery_charge_threshold_paise),
  mid_tier_fee_paise bigint CHECK (mid_tier_fee_paise IS NULL OR mid_tier_fee_paise >= 0),
  max_order_paise bigint CHECK (max_order_paise IS NULL OR max_order_paise >= 0),
  cancellation_policy_text text,
  tax_mode tax_mode NOT NULL DEFAULT 'none',
  tax_rate_bps integer NOT NULL DEFAULT 0 CHECK (tax_rate_bps >= 0),
  online_payment_enabled boolean NOT NULL DEFAULT false,
  pos_price_override_enabled boolean NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS service_areas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  kind service_area_kind NOT NULL,
  value text NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_service_areas_kind_value_lower
  ON service_areas (kind, lower(value));
CREATE INDEX IF NOT EXISTS idx_service_areas_active ON service_areas(active);
