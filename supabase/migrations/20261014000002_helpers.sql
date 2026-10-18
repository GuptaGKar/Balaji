-- M1: shared helper functions (IMPLEMENTATION_PLAN.md §4.1 conventions)

-- Keeps updated_at current on every UPDATE for tables that carry the column.
-- Attached by trigger in the table migrations below.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;
