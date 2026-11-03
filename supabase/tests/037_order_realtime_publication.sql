BEGIN;
SELECT plan(1);
SELECT is((SELECT count(*) FROM pg_publication_tables
 WHERE pubname='supabase_realtime' AND schemaname='public' AND tablename='orders'),
 1::bigint,'order changes are published for authorized Realtime subscribers');
SELECT * FROM finish();
ROLLBACK;
