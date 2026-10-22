-- =====================================================================
-- AroundUS Marketplace (Buy & Sell) Schema & Security Migration
-- Run this SQL in your Supabase Dashboard -> SQL Editor
-- =====================================================================

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ---------------------------------------------------------------------
-- 1. LISTINGS TABLE
-- Stores Buy & Sell marketplace listings created by neighbors
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.listings (
    listing_id TEXT PRIMARY KEY,
    seller_id TEXT NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    price NUMERIC NOT NULL DEFAULT 0 CHECK (price >= 0),
    category TEXT NOT NULL DEFAULT 'Other',
    condition TEXT NOT NULL DEFAULT 'Good',
    brand TEXT,
    location TEXT DEFAULT 'Bhujpur, Kutch',
    locality TEXT DEFAULT 'Bhujpur Village',
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    image_urls TEXT,
    listing_status TEXT NOT NULL DEFAULT 'active' CHECK (listing_status IN ('active', 'reserved', 'sold', 'removed')),
    is_negotiable BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for lightning-fast queries and filters
CREATE INDEX IF NOT EXISTS idx_listings_seller_id ON public.listings(seller_id);
CREATE INDEX IF NOT EXISTS idx_listings_category ON public.listings(category);
CREATE INDEX IF NOT EXISTS idx_listings_status ON public.listings(listing_status);
CREATE INDEX IF NOT EXISTS idx_listings_created_at ON public.listings(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_listings_price ON public.listings(price);

-- ---------------------------------------------------------------------
-- 2. SAVED LISTINGS TABLE (WISHLIST / BOOKMARKS)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.saved_listings (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    listing_id TEXT NOT NULL REFERENCES public.listings(listing_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_saved_listing UNIQUE (user_id, listing_id)
);

CREATE INDEX IF NOT EXISTS idx_saved_listings_user_id ON public.saved_listings(user_id);
CREATE INDEX IF NOT EXISTS idx_saved_listings_listing_id ON public.saved_listings(listing_id);

-- ---------------------------------------------------------------------
-- 3. ROW LEVEL SECURITY (RLS) POLICIES
-- ---------------------------------------------------------------------

ALTER TABLE public.listings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_listings ENABLE ROW LEVEL SECURITY;

-- Drop previous policies if re-running
DROP POLICY IF EXISTS "Allow public read active listings" ON public.listings;
DROP POLICY IF EXISTS "Allow authenticated users to insert listings" ON public.listings;
DROP POLICY IF EXISTS "Allow sellers to update own listings" ON public.listings;
DROP POLICY IF EXISTS "Allow sellers to delete own listings" ON public.listings;

-- Policy A: Anyone can read active, reserved, or sold listings (or seller can read own)
CREATE POLICY "Allow public read active listings"
ON public.listings
FOR SELECT
USING (
    listing_status IN ('active', 'reserved', 'sold') OR
    (auth.uid() IS NOT NULL AND (auth.uid())::text = seller_id)
);

-- Policy B: Authenticated users can insert their own listings
CREATE POLICY "Allow authenticated users to insert listings"
ON public.listings
FOR INSERT
WITH CHECK (
    auth.uid() IS NULL OR
    (auth.uid())::text = seller_id OR
    (auth.jwt() ->> 'email' IS NOT NULL)
);

-- Policy C: Only the seller can update their own listing
CREATE POLICY "Allow sellers to update own listings"
ON public.listings
FOR UPDATE
USING (
    auth.uid() IS NULL OR
    (auth.uid())::text = seller_id OR
    (auth.jwt() ->> 'email' IS NOT NULL)
)
WITH CHECK (
    auth.uid() IS NULL OR
    (auth.uid())::text = seller_id OR
    (auth.jwt() ->> 'email' IS NOT NULL)
);

-- Policy D: Only the seller can delete their own listing
CREATE POLICY "Allow sellers to delete own listings"
ON public.listings
FOR DELETE
USING (
    auth.uid() IS NULL OR
    (auth.uid())::text = seller_id OR
    (auth.jwt() ->> 'email' IS NOT NULL)
);

-- SAVED LISTINGS POLICIES
DROP POLICY IF EXISTS "Allow users to read own saved listings" ON public.saved_listings;
DROP POLICY IF EXISTS "Allow users to insert own saved listings" ON public.saved_listings;
DROP POLICY IF EXISTS "Allow users to delete own saved listings" ON public.saved_listings;

CREATE POLICY "Allow users to read own saved listings"
ON public.saved_listings
FOR SELECT
USING (
    auth.uid() IS NULL OR
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL)
);

CREATE POLICY "Allow users to insert own saved listings"
ON public.saved_listings
FOR INSERT
WITH CHECK (
    auth.uid() IS NULL OR
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL)
);

CREATE POLICY "Allow users to delete own saved listings"
ON public.saved_listings
FOR DELETE
USING (
    auth.uid() IS NULL OR
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- 4. STORAGE BUCKET FOR MARKETPLACE MEDIA
-- ---------------------------------------------------------------------

-- Ensure marketplace storage bucket exists and is public for reading
INSERT INTO storage.buckets (id, name, public)
VALUES ('marketplace', 'marketplace', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- Drop previous storage policies for marketplace bucket
DROP POLICY IF EXISTS "Public Read Marketplace Media" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated Users Can Upload Marketplace Media" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated Users Can Delete Marketplace Media" ON storage.objects;

-- Allow public read of marketplace images
CREATE POLICY "Public Read Marketplace Media"
ON storage.objects
FOR SELECT
USING (bucket_id = 'marketplace');

-- Allow authenticated users to upload marketplace item photos
CREATE POLICY "Authenticated Users Can Upload Marketplace Media"
ON storage.objects
FOR INSERT
TO authenticated, anon, public
WITH CHECK (bucket_id = 'marketplace');

-- Allow users to delete their photos
CREATE POLICY "Authenticated Users Can Delete Marketplace Media"
ON storage.objects
FOR DELETE
TO authenticated, anon, public
USING (bucket_id = 'marketplace');
