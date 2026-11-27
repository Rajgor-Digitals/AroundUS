-- =====================================================================
-- AroundUS: Fix Profile Photo Cross-Device Sync & Database RLS Policies
-- Run this SQL in your Supabase Dashboard -> SQL Editor -> Run
-- =====================================================================

-- 1. Ensure public.users has all required profile columns
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS profile_photo TEXT;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS profile TEXT;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS bio TEXT;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT false;
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS username_last_changed BIGINT;

-- Backfill profile_photo from legacy 'profile' column (and vice versa) if any exist
UPDATE public.users
SET profile_photo = profile
WHERE (profile_photo IS NULL OR profile_photo = '')
  AND profile IS NOT NULL
  AND profile <> '';

UPDATE public.users
SET profile = profile_photo
WHERE (profile IS NULL OR profile = '')
  AND profile_photo IS NOT NULL
  AND profile_photo <> '';

-- 2. USERS TABLE ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Public read access so all users can view other users' profile photos & names
DROP POLICY IF EXISTS "Allow public read users" ON public.users;
CREATE POLICY "Allow public read users"
ON public.users
FOR SELECT
USING (true);

-- Insert policy (supports Supabase Auth JWT and fallback/signup sessions)
DROP POLICY IF EXISTS "Allow public insert users" ON public.users;
DROP POLICY IF EXISTS "Allow users to insert own profile" ON public.users;
CREATE POLICY "Allow users to insert own profile"
ON public.users
FOR INSERT
WITH CHECK (
    auth.uid() IS NULL OR
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL AND lower(auth.jwt() ->> 'email') = lower(email))
);

-- Update policy (matches posts/listings pattern: works with authenticated JWT AND anon/OTP fallback sessions)
DROP POLICY IF EXISTS "Allow public update users" ON public.users;
DROP POLICY IF EXISTS "Allow users to update own profile" ON public.users;
CREATE POLICY "Allow users to update own profile"
ON public.users
FOR UPDATE
TO public, anon, authenticated
USING (
    auth.uid() IS NULL OR
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL AND lower(auth.jwt() ->> 'email') = lower(email))
)
WITH CHECK (
    auth.uid() IS NULL OR
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL AND lower(auth.jwt() ->> 'email') = lower(email))
);

-- Keep user_id and created_at immutable on update
CREATE OR REPLACE FUNCTION public.protect_user_fields()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.user_id <> OLD.user_id THEN
        RAISE EXCEPTION 'Security Violation: Modifying user_id is prohibited.';
    END IF;
    NEW.created_at := OLD.created_at;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trg_protect_user_fields ON public.users;
CREATE TRIGGER trg_protect_user_fields
BEFORE UPDATE ON public.users
FOR EACH ROW EXECUTE FUNCTION public.protect_user_fields();

-- 3. SUPABASE STORAGE: Ensure 'avatars' bucket is public
INSERT INTO storage.buckets (id, name, public)
VALUES ('avatars', 'avatars', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- 4. STORAGE OBJECTS POLICIES (RLS is already enabled on storage.objects in Supabase)
-- Wrapped safely so SQL Editor ownership restrictions (42501 on storage.objects)
-- do not roll back the public.users and storage.buckets updates above.
DO $$
BEGIN
    EXECUTE 'DROP POLICY IF EXISTS "Public Read Avatars" ON storage.objects';
    EXECUTE 'DROP POLICY IF EXISTS "Allow public upload avatars" ON storage.objects';
    EXECUTE 'DROP POLICY IF EXISTS "Allow public update avatars" ON storage.objects';
    EXECUTE 'DROP POLICY IF EXISTS "Allow public delete avatars" ON storage.objects';
    EXECUTE 'DROP POLICY IF EXISTS "Authenticated Users Can Upload Own Avatar" ON storage.objects';
    EXECUTE 'DROP POLICY IF EXISTS "Authenticated Users Can Update Own Avatar" ON storage.objects';
    EXECUTE 'DROP POLICY IF EXISTS "Authenticated Users Can Delete Own Avatar" ON storage.objects';

    EXECUTE $p$
        CREATE POLICY "Public Read Avatars"
        ON storage.objects
        FOR SELECT
        USING (bucket_id = 'avatars')
    $p$;

    EXECUTE $p$
        CREATE POLICY "Authenticated Users Can Upload Own Avatar"
        ON storage.objects
        FOR INSERT
        TO public, anon, authenticated
        WITH CHECK (
            bucket_id = 'avatars' AND
            (
                auth.uid() IS NULL OR
                (storage.foldername(name))[1] = (auth.uid())::text OR
                (auth.jwt() ->> 'email' IS NOT NULL AND lower((storage.foldername(name))[1]) = lower(auth.jwt() ->> 'email'))
            )
        )
    $p$;

    EXECUTE $p$
        CREATE POLICY "Authenticated Users Can Update Own Avatar"
        ON storage.objects
        FOR UPDATE
        TO public, anon, authenticated
        USING (
            bucket_id = 'avatars' AND
            (
                auth.uid() IS NULL OR
                (storage.foldername(name))[1] = (auth.uid())::text OR
                (auth.jwt() ->> 'email' IS NOT NULL AND lower((storage.foldername(name))[1]) = lower(auth.jwt() ->> 'email'))
            )
        )
        WITH CHECK (
            bucket_id = 'avatars' AND
            (
                auth.uid() IS NULL OR
                (storage.foldername(name))[1] = (auth.uid())::text OR
                (auth.jwt() ->> 'email' IS NOT NULL AND lower((storage.foldername(name))[1]) = lower(auth.jwt() ->> 'email'))
            )
        )
    $p$;

    EXECUTE $p$
        CREATE POLICY "Authenticated Users Can Delete Own Avatar"
        ON storage.objects
        FOR DELETE
        TO public, anon, authenticated
        USING (
            bucket_id = 'avatars' AND
            (
                auth.uid() IS NULL OR
                (storage.foldername(name))[1] = (auth.uid())::text OR
                (auth.jwt() ->> 'email' IS NOT NULL AND lower((storage.foldername(name))[1]) = lower(auth.jwt() ->> 'email'))
            )
        )
    $p$;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE NOTICE 'storage.objects policies require Dashboard -> Storage -> Policies UI (%). public.users & avatars bucket were updated successfully.', SQLERRM;
END $$;
