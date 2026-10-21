-- =====================================================================
-- FIX GROUPS ROW LEVEL SECURITY (RLS) FOR JOINING / LEAVING GROUPS
-- Run this in your Supabase Dashboard -> SQL Editor -> Run
-- =====================================================================

-- 1. Ensure RLS is enabled on groups table
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;

-- 2. Drop existing update policies if any
DROP POLICY IF EXISTS "Allow public update groups" ON public.groups;
DROP POLICY IF EXISTS "Allow users to update groups" ON public.groups;

-- 3. Create permissive UPDATE policy so members list can be updated when joining/leaving
CREATE POLICY "Allow public update groups" 
ON public.groups 
FOR UPDATE 
TO public, anon, authenticated
USING (true)
WITH CHECK (true);

-- 4. Ensure SELECT and INSERT policies are active
DROP POLICY IF EXISTS "Allow public read groups" ON public.groups;
CREATE POLICY "Allow public read groups" 
ON public.groups 
FOR SELECT 
USING (true);

DROP POLICY IF EXISTS "Allow public insert groups" ON public.groups;
CREATE POLICY "Allow public insert groups" 
ON public.groups 
FOR INSERT 
WITH CHECK (true);
