-- ============================================================
-- 20261105000003  Close the anonymous write surface on the storefront RPCs
-- ============================================================
-- Measured on the live project right after 20261105000001/000002 were applied:
--
--   has_function_privilege('anon', 'save_storefront_section(...)', 'EXECUTE') = true
--
-- for all four manager write functions. Nothing in 20261105000001 or
-- 20261105000002 granted that. Supabase creates functions with EXECUTE already
-- granted to PUBLIC and to anon, so an explicit "GRANT ... TO authenticated"
-- narrows nobody: it only adds a grantee. A signed-out caller could therefore
-- reach the body of every write RPC.
--
-- It was not exploitable - which is why this is hygiene, not an incident. Proven
-- against the live database:
--   * save_/reorder_/delete_storefront_section open with a current_role() guard,
--     so anon got "Only a manager can change the storefront layout." back and
--     wrote nothing;
--   * set_product_categories has no such guard on purpose (suite 048 S48-16 pins
--     the 42501 it raises), and an anon call died at
--     "permission denied for table products" before touching a row.
--
-- So the authority boundary held at the second and third layer. This migration
-- puts it at the first: an unauthenticated caller should not be able to execute
-- the body of a manager write at all.
--
-- Deliberately unchanged:
--   * get_storefront stays callable by anon. The read policies already limit it
--     to active sections, and a signed-out shopper is a supported state.
--   * No role guard is added to set_product_categories. 42501 is the stronger
--     boundary - it is a table privilege, not a policy sentence - and S48-16
--     asserts exactly that error code.
--   * service_role keeps EXECUTE: the dashboard's server-side client uses it, and
--     revoking it would break manager saves rather than protect anything.
--
-- Signatures are resolved through to_regprocedure, so if a later migration changes
-- a parameter list this one refuses loudly instead of leaving the old grant in
-- place under a name that no longer matches.

DO $$
DECLARE
  v_identity text;
  v_reg      regprocedure;
BEGIN
  FOREACH v_identity IN ARRAY ARRAY[
    'public.save_storefront_section(uuid, text, text, text, text, boolean, uuid[], timestamp with time zone)',
    'public.reorder_storefront_sections(text, uuid[])',
    'public.delete_storefront_section(uuid)',
    'public.set_product_categories(uuid, uuid, uuid[])'
  ]
  LOOP
    v_reg := to_regprocedure(v_identity);
    IF v_reg IS NULL THEN
      RAISE EXCEPTION 'Expected function % is not present, so its privileges cannot be adjusted.', v_identity
        USING HINT = 'Check whether a later migration changed the signature, and update this one to match.';
    END IF;

    EXECUTE format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC, anon', v_reg);
    -- Re-assert the grantees that must survive the revoke above. PUBLIC membership
    -- and the anon entry were created by Supabase's default privileges, not here,
    -- so both are removed; these two are the whole intended surface.
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated, service_role', v_reg);
  END LOOP;
END;
$$;

-- The read path must not have been narrowed by accident: an anonymous shopper with
-- no JWT has to keep reaching get_storefront, or the customer app's Home page cannot
-- render a composed storefront before sign-in completes.
DO $$
BEGIN
  IF NOT has_function_privilege('anon', 'public.get_storefront(text)', 'EXECUTE') THEN
    RAISE EXCEPTION 'get_storefront is no longer executable by anon, which would break the customer storefront read.'
      USING HINT = 'Restore: GRANT EXECUTE ON FUNCTION public.get_storefront(text) TO anon;';
  END IF;
END;
$$;

NOTIFY pgrst, 'reload schema';
