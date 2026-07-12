-- Retrofit blue/green columns onto api_group_route for environments where the table
-- pre-dated V1 (created via database/script/create_table.sql), so V1's
-- CREATE TABLE IF NOT EXISTS was a no-op and never added these columns.
ALTER TABLE public.api_group_route
    ADD COLUMN IF NOT EXISTS blue_uri    varchar(255) NULL,
    ADD COLUMN IF NOT EXISTS green_uri   varchar(255) NULL,
    ADD COLUMN IF NOT EXISTS active_slot varchar(5)   NOT NULL DEFAULT 'BLUE';

COMMENT ON COLUMN public.api_group_route.blue_uri    IS 'Blue slot backend URI (stable)';
COMMENT ON COLUMN public.api_group_route.green_uri   IS 'Green slot backend URI (new version)';
COMMENT ON COLUMN public.api_group_route.active_slot IS 'BLUE | GREEN — which slot receives live traffic';

ALTER TABLE public.api_route
    ADD COLUMN IF NOT EXISTS priority int4 DEFAULT 1;