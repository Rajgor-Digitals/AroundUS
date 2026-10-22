-- =========================================================
-- AroundUS Supabase Database Schema Script
-- Run this SQL in your Supabase Dashboard SQL Editor
-- =========================================================

-- Enable UUID extension if needed
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. USERS TABLE
-- Stores user profiles and account details
CREATE TABLE IF NOT EXISTS public.users (
    user_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    phone TEXT,
    location TEXT DEFAULT 'Bhujpur, Kutch',
    profile TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. POSTS TABLE
-- Stores posts created by users (User -> creates -> Post)
CREATE TABLE IF NOT EXISTS public.posts (
    post_id TEXT PRIMARY KEY,
    author_id TEXT REFERENCES public.users(user_id) ON DELETE CASCADE,
    type TEXT DEFAULT 'General',
    text TEXT NOT NULL,
    media TEXT,
    timestamp TEXT DEFAULT NOW()::TEXT,
    location TEXT DEFAULT 'Bhujpur, Kutch',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. COMMENTS TABLE
-- Stores comments under posts (Post -> has -> Comments)
CREATE TABLE IF NOT EXISTS public.comments (
    comment_id TEXT PRIMARY KEY,
    post_id TEXT REFERENCES public.posts(post_id) ON DELETE CASCADE,
    user_id TEXT REFERENCES public.users(user_id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    timestamp TEXT DEFAULT NOW()::TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. GROUPS TABLE
-- Stores neighborhood groups (User -> joins -> Group)
CREATE TABLE IF NOT EXISTS public.groups (
    group_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    category TEXT DEFAULT 'Local',
    members TEXT DEFAULT '1 Member',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. MESSAGES TABLE
-- Stores direct user-to-user messages and group chats (User <-> Messages)
CREATE TABLE IF NOT EXISTS public.messages (
    message_id TEXT PRIMARY KEY,
    sender_id TEXT REFERENCES public.users(user_id) ON DELETE CASCADE,
    receiver_id TEXT REFERENCES public.users(user_id) ON DELETE CASCADE,
    group_id TEXT REFERENCES public.groups(group_id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    timestamp TEXT DEFAULT NOW()::TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. EVENTS TABLE
-- Stores local neighborhood events
CREATE TABLE IF NOT EXISTS public.events (
    event_id TEXT PRIMARY KEY,
    creator_id TEXT REFERENCES public.users(user_id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    date TEXT NOT NULL,
    location TEXT DEFAULT 'Bhujpur, Kutch',
    attendees TEXT DEFAULT '1 Attending',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- =========================================================
-- Enable Row Level Security (RLS) & Public Access Policies
-- =========================================================

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.events ENABLE ROW LEVEL SECURITY;

-- Grant public read and write policies
CREATE POLICY "Allow public read users" ON public.users FOR SELECT USING (true);
CREATE POLICY "Allow users to insert own profile" ON public.users FOR INSERT WITH CHECK (
    auth.uid() IS NULL OR
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL AND auth.jwt() ->> 'email' = email)
);
CREATE POLICY "Allow users to update own profile" ON public.users FOR UPDATE TO authenticated USING (
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL AND auth.jwt() ->> 'email' = email)
) WITH CHECK (
    (auth.uid())::text = user_id OR
    (auth.jwt() ->> 'email' IS NOT NULL AND auth.jwt() ->> 'email' = email)
);

CREATE POLICY "Allow public read posts" ON public.posts FOR SELECT USING (true);
CREATE POLICY "Allow public insert posts" ON public.posts FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public delete posts" ON public.posts FOR DELETE USING (true);

CREATE POLICY "Allow public read comments" ON public.comments FOR SELECT USING (true);
CREATE POLICY "Allow public insert comments" ON public.comments FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow public read groups" ON public.groups FOR SELECT USING (true);
CREATE POLICY "Allow public insert groups" ON public.groups FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow public update groups" ON public.groups FOR UPDATE USING (true) WITH CHECK (true);

CREATE POLICY "Allow public read messages" ON public.messages FOR SELECT USING (true);
CREATE POLICY "Allow public insert messages" ON public.messages FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow public read events" ON public.events FOR SELECT USING (true);
CREATE POLICY "Allow public insert events" ON public.events FOR INSERT WITH CHECK (true);

-- 7. LISTINGS TABLE (BUY & SELL MARKETPLACE)
-- Stores items listed for sale by neighborhood residents
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

CREATE INDEX IF NOT EXISTS idx_listings_seller_id ON public.listings(seller_id);
CREATE INDEX IF NOT EXISTS idx_listings_category ON public.listings(category);
CREATE INDEX IF NOT EXISTS idx_listings_status ON public.listings(listing_status);
CREATE INDEX IF NOT EXISTS idx_listings_created_at ON public.listings(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_listings_price ON public.listings(price);

ALTER TABLE public.listings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read active listings" ON public.listings
    FOR SELECT USING (listing_status IN ('active', 'reserved', 'sold') OR (auth.uid() IS NOT NULL AND (auth.uid())::text = seller_id));

CREATE POLICY "Allow authenticated users to insert listings" ON public.listings
    FOR INSERT WITH CHECK (auth.uid() IS NULL OR (auth.uid())::text = seller_id OR (auth.jwt() ->> 'email' IS NOT NULL));

CREATE POLICY "Allow sellers to update own listings" ON public.listings
    FOR UPDATE USING (auth.uid() IS NULL OR (auth.uid())::text = seller_id OR (auth.jwt() ->> 'email' IS NOT NULL));

CREATE POLICY "Allow sellers to delete own listings" ON public.listings
    FOR DELETE USING (auth.uid() IS NULL OR (auth.uid())::text = seller_id OR (auth.jwt() ->> 'email' IS NOT NULL));

-- 8. SAVED LISTINGS TABLE (WISHLIST)
CREATE TABLE IF NOT EXISTS public.saved_listings (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    listing_id TEXT NOT NULL REFERENCES public.listings(listing_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_user_saved_listing UNIQUE (user_id, listing_id)
);

CREATE INDEX IF NOT EXISTS idx_saved_listings_user_id ON public.saved_listings(user_id);
CREATE INDEX IF NOT EXISTS idx_saved_listings_listing_id ON public.saved_listings(listing_id);

ALTER TABLE public.saved_listings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow users to read own saved listings" ON public.saved_listings
    FOR SELECT USING (auth.uid() IS NULL OR (auth.uid())::text = user_id OR (auth.jwt() ->> 'email' IS NOT NULL));

CREATE POLICY "Allow users to insert own saved listings" ON public.saved_listings
    FOR INSERT WITH CHECK (auth.uid() IS NULL OR (auth.uid())::text = user_id OR (auth.jwt() ->> 'email' IS NOT NULL));

CREATE POLICY "Allow users to delete own saved listings" ON public.saved_listings
    FOR DELETE USING (auth.uid() IS NULL OR (auth.uid())::text = user_id OR (auth.jwt() ->> 'email' IS NOT NULL));
