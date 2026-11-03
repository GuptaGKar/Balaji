-- Test-only, invoker-security fixture helper; created inside each rollback transaction.
CREATE FUNCTION pg_temp.make_profile(p_id uuid,p_role public.user_role,p_name text)
RETURNS void LANGUAGE plpgsql SET search_path='' AS $fn$
BEGIN
 INSERT INTO auth.users(id,email,aud,role) VALUES(p_id,'fixture-'||p_id::text||'@test.invalid','authenticated','authenticated');
 UPDATE public.profiles SET role=p_role,display_name=p_name,is_active=true,must_change_password=false,
  login_id=CASE WHEN p_role='delivery' THEN 'QA-'||upper(replace(p_id::text,'-','')) ELSE NULL END
 WHERE id=p_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Signup trigger did not create the fixture profile'; END IF;
END;
$fn$;
