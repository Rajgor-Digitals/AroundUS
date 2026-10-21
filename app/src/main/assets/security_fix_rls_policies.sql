-- =====================================================================
-- AroundUS Security Migration: Cross-User Profile Modification Fix
-- Priority: P0 Critical Security Fix
-- Run this SQL in your Supabase Dashboard -> SQL Editor
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. USERS TABLE ROW LEVEL SECURITY (RLS) POLICIES
-- ---------------------------------------------------------------------

-- Ensure RLS is enabled on public.users
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Drop insecure permissive update policy
DROP POLICY IF EXISTS "Allow public update users" ON public.users;
DROP POLICY IF EXISTS "Allow users to update own profile" ON public.users;

-- Public can read user profiles (necessary for neighborhood feed, author cards & discovery)
DROP POLICY IF EXISTS "Allow public read users" ON public.users;
CREATE POLICY "Allow public read users" 
ON public.users 
FOR SELECT 
USING (true);

-- Authenticated users or signup flow can insert their own profile
DROP POLICY IF EXISTS "Allow public insert users" ON public.users;
DROP POLICY IF EXISTS "Allow users to insert own profile" ON public.users;
CREATE POLICY "Allow users to insert own profile" 
ON public.users 
FOR INSERT 
WITH CHECK (
    auth.uid() IS NULL OR
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL AND auth.jwt() ->> 'email' = email)
);

-- STRICT UPDATE POLICY: Only the authenticated user can update their own row.
-- Enforces identity via auth.uid() or verified JWT email claim.
CREATE POLICY "Allow users to update own profile" 
ON public.users 
FOR UPDATE 
TO authenticated
USING (
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL AND auth.jwt() ->> 'email' = email)
)
WITH CHECK (
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL AND auth.jwt() ->> 'email' = email)
);

-- Protect against tampering with user_id or created_at via an immutable trigger
CREATE OR REPLACE FUNCTION public.protect_user_fields()
RETURNS TRIGGER AS $$
BEGIN
    -- Prevent altering primary key user_id
    IF NEW.user_id <> OLD.user_id THEN
        RAISE EXCEPTION 'Security Violation: Modifying user_id is prohibited.';
    END IF;
    -- Preserve original creation timestamp
    NEW.created_at := OLD.created_at;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_protect_user_fields ON public.users;
CREATE TRIGGER trg_protect_user_fields
BEFORE UPDATE ON public.users
FOR EACH ROW EXECUTE FUNCTION public.protect_user_fields();


-- ---------------------------------------------------------------------
-- 2. SUPABASE STORAGE RLS POLICIES FOR 'avatars' BUCKET
-- ---------------------------------------------------------------------

-- Ensure avatars bucket exists and is marked public for reading
INSERT INTO storage.buckets (id, name, public)
VALUES ('avatars', 'avatars', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- Enable RLS on storage.objects
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- Drop previous permissive policies on avatars
DROP POLICY IF EXISTS "Public Read Avatars" ON storage.objects;
DROP POLICY IF EXISTS "Allow public upload avatars" ON storage.objects;
DROP POLICY IF EXISTS "Allow public update avatars" ON storage.objects;
DROP POLICY IF EXISTS "Allow public delete avatars" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated Users Can Upload Own Avatar" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated Users Can Update Own Avatar" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated Users Can Delete Own Avatar" ON storage.objects;

-- Policy A: Anyone can read/view avatar images (public bucket)
CREATE POLICY "Public Read Avatars"
ON storage.objects 
FOR SELECT
USING (bucket_id = 'avatars');

-- Policy B: Authenticated users can ONLY upload to their own user folder (avatars/<user_id>/*)
CREATE POLICY "Authenticated Users Can Upload Own Avatar"
ON storage.objects 
FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'avatars' AND
    (
        (storage.foldername(name))[1] = (auth.uid())::text OR
        (storage.foldername(name))[1] = (auth.jwt() ->> 'email')
    )
);

-- Policy C: Authenticated users can ONLY update/replace photos in their own folder
CREATE POLICY "Authenticated Users Can Update Own Avatar"
ON storage.objects 
FOR UPDATE
TO authenticated
USING (
    bucket_id = 'avatars' AND
    (
        (storage.foldername(name))[1] = (auth.uid())::text OR
        (storage.foldername(name))[1] = (auth.jwt() ->> 'email')
    )
)
WITH CHECK (
    bucket_id = 'avatars' AND
    (
        (storage.foldername(name))[1] = (auth.uid())::text OR
        (storage.foldername(name))[1] = (auth.jwt() ->> 'email')
    )
);

-- Policy D: Authenticated users can ONLY delete photos from their own folder
CREATE POLICY "Authenticated Users Can Delete Own Avatar"
ON storage.objects 
FOR DELETE
TO authenticated
USING (
    bucket_id = 'avatars' AND
    (
        (storage.foldername(name))[1] = (auth.uid())::text OR
        (storage.foldername(name))[1] = (auth.jwt() ->> 'email')
    )
);
