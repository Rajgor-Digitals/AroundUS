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

CREATE POLICY "Allow public read messages" ON public.messages FOR SELECT USING (true);
CREATE POLICY "Allow public insert messages" ON public.messages FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow public read events" ON public.events FOR SELECT USING (true);
CREATE POLICY "Allow public insert events" ON public.events FOR INSERT WITH CHECK (true);
