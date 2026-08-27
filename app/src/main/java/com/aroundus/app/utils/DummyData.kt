package com.aroundus.app.utils

import com.aroundus.app.R
import com.aroundus.app.models.*

object DummyData {

    val stories = listOf(
        Story("add", "Add Story", R.drawable.shape_avatar_placeholder, isAddStory = true),
        Story("1", "Sarah", R.drawable.shape_avatar_placeholder),
        Story("2", "Mike", R.drawable.shape_avatar_placeholder),
        Story("3", "Emma", R.drawable.shape_avatar_placeholder),
        Story("4", "James", R.drawable.shape_avatar_placeholder),
        Story("5", "Priya", R.drawable.shape_avatar_placeholder)
    )

    val posts = listOf(
        Post(
            id = "1",
            userName = "Sarah Johnson",
            userAvatar = R.drawable.shape_avatar_placeholder,
            neighborhood = "Maple Grove",
            timeAgo = "2h ago",
            category = "Nature",
            text = "Spotted a family of deer near the park this morning! So peaceful. Remember to give them space and not to feed them. 🦌🌿",
            imageRes = R.drawable.bg_image_placeholder,
            likesCount = 124,
            commentsCount = 32
        ),
        Post(
            id = "2",
            userName = "Mike Thompson",
            userAvatar = R.drawable.shape_avatar_placeholder,
            neighborhood = "Maple Grove",
            timeAgo = "4h ago",
            category = "Safety",
            text = "⚠️ Heads up neighbors: There's a pothole near the junction of Oak St and Maple Ave. Please drive carefully and I've already reported it to the municipality.",
            likesCount = 87,
            commentsCount = 14
        ),
        Post(
            id = "3",
            userName = "Emma Rodriguez",
            userAvatar = R.drawable.shape_avatar_placeholder,
            neighborhood = "Maple Grove",
            timeAgo = "6h ago",
            category = "Events",
            text = "🎉 Don't forget – Community Cleanup Day is this Saturday! Meet at the park entrance at 9 AM. Gloves and bags will be provided.",
            imageRes = R.drawable.bg_image_placeholder,
            likesCount = 203,
            commentsCount = 56
        ),
        Post(
            id = "4",
            userName = "James Wilson",
            userAvatar = R.drawable.shape_avatar_placeholder,
            neighborhood = "Maple Grove",
            timeAgo = "1d ago",
            category = "Recommendation",
            text = "Just tried the new café on Birch Lane – The Cozy Corner. The cold brew and homemade muffins are absolutely amazing! Highly recommend 🙌☕",
            imageRes = R.drawable.bg_image_placeholder,
            likesCount = 56,
            commentsCount = 8
        )
    )

    val products = listOf(
        Product(
            id = "1",
            name = "Wooden Study Desk - Solid Oak",
            price = "₹1,200",
            imageRes = R.drawable.bg_image_placeholder,
            location = "Maple Grove",
            timeAgo = "3h ago",
            condition = "Like New"
        ),
        Product(
            id = "2",
            name = "Road Bicycle - Trek 21 Speed",
            price = "₹8,500",
            imageRes = R.drawable.bg_image_placeholder,
            location = "Elm Street",
            timeAgo = "5h ago",
            condition = "Good"
        ),
        Product(
            id = "3",
            name = "Sony Bluetooth Speaker",
            price = "₹2,000",
            imageRes = R.drawable.bg_image_placeholder,
            location = "Maple Grove",
            timeAgo = "1d ago",
            condition = "Like New"
        ),
        Product(
            id = "4",
            name = "Sofa Set - 3+1+1",
            price = "Free",
            imageRes = R.drawable.bg_image_placeholder,
            location = "Oak Avenue",
            timeAgo = "2d ago",
            condition = "Good"
        )
    )

    val events = listOf(
        Event(
            id = "1",
            title = "Community Farmers Market",
            month = "AUG",
            day = "15",
            time = "Sat, Aug 15 · 9:00 AM – 2:00 PM",
            location = "Riverside Park, Maple Grove",
            attendeesCount = 128,
            isFree = true,
            imageRes = R.drawable.bg_gradient_onboarding
        ),
        Event(
            id = "2",
            title = "Neighborhood Yoga & Wellness",
            month = "AUG",
            day = "18",
            time = "Tue, Aug 18 · 6:30 AM",
            location = "Community Garden",
            attendeesCount = 45,
            isFree = true,
            imageRes = R.drawable.bg_gradient_onboarding
        ),
        Event(
            id = "3",
            title = "Block Party & BBQ Night",
            month = "AUG",
            day = "22",
            time = "Sat, Aug 22 · 5:00 PM",
            location = "Oak Street Cul-de-sac",
            attendeesCount = 76,
            isFree = false,
            imageRes = R.drawable.bg_gradient_onboarding
        )
    )

    val chats = listOf(
        ChatItem("1", "Michael Chen", R.drawable.shape_avatar_placeholder, "Hey! Are you coming to the community meeting?", "2m", 3, true),
        ChatItem("2", "Emma Rodriguez", R.drawable.shape_avatar_placeholder, "Thanks for the flowers! They're beautiful 🌺", "15m", 0, false),
        ChatItem("3", "The Gardening Club", R.drawable.shape_avatar_placeholder, "James: Don't forget the seeds!", "1h", 5, false),
        ChatItem("4", "Priya Sharma", R.drawable.shape_avatar_placeholder, "Is the sofa still available?", "3h", 1, false),
        ChatItem("5", "Safety Watch Group", R.drawable.shape_avatar_placeholder, "Alert: Suspicious vehicle spotted on Birch Ln", "5h", 0, false)
    )

    val notifications = listOf(
        Notification("1", "like", "Sarah Johnson liked your post about the deer sighting", R.drawable.shape_avatar_placeholder, "2m"),
        Notification("2", "comment", "Mike Thompson commented: \"I'll be there with extra chairs!\"", R.drawable.shape_avatar_placeholder, "15m"),
        Notification("3", "follow", "Emma Rodriguez started following you", R.drawable.shape_avatar_placeholder, "1h", false),
        Notification("4", "event", "Community Farmers Market is tomorrow! Don't miss it 🌽", R.drawable.shape_avatar_placeholder, "3h"),
        Notification("5", "comment", "James Wilson replied to your comment", R.drawable.shape_avatar_placeholder, "5h", false)
    )

    val groupCategories = listOf(
        GroupCategory("1", "All", R.drawable.ic_grid_category, isSelected = true),
        GroupCategory("2", "Local", R.drawable.ic_home, isSelected = false),
        GroupCategory("3", "Buy / Sell", R.drawable.ic_sell, isSelected = false),
        GroupCategory("4", "Help & Support", R.drawable.ic_support_heart, isSelected = false),
        GroupCategory("5", "Activities", R.drawable.ic_event, isSelected = false),
        GroupCategory("6", "Education", R.drawable.ic_school, isSelected = false),
        GroupCategory("7", "More", R.drawable.ic_more_horiz, isSelected = false)
    )

    val myGroups = listOf(
        Group(
            id = "1",
            name = "Bhujpur Residents",
            description = "Stay updated with local news and events.",
            coverRes = R.drawable.img_group_bhujpur_residents,
            membersText = "1.2K Members",
            category = "Local",
            isAdmin = true,
            iconBadgeRes = R.drawable.ic_people,
            isJoined = true,
            isNotificationOn = true
        ),
        Group(
            id = "2",
            name = "Bhujpur Buy & Sell",
            description = "Buy, sell and exchange items in your area.",
            coverRes = R.drawable.img_group_bhujpur_buysell,
            membersText = "856 Members",
            category = "Buy / Sell",
            isAdmin = true,
            iconBadgeRes = R.drawable.ic_sell,
            isJoined = true,
            isNotificationOn = true
        ),
        Group(
            id = "3",
            name = "Help Each Other",
            description = "Community support and help for everyone.",
            coverRes = R.drawable.img_group_help_each_other,
            membersText = "634 Members",
            category = "Help & Support",
            isAdmin = false,
            iconBadgeRes = R.drawable.ic_support_heart,
            isJoined = true,
            isNotificationOn = true
        )
    )

    val discoverGroups = listOf(
        Group(
            id = "4",
            name = "Yoga & Wellness Bhujpur",
            description = "Join yoga sessions, wellness tips and a healthy lifestyle community.",
            coverRes = R.drawable.img_group_yoga_wellness,
            membersText = "789 Members",
            category = "Health",
            isJoined = false
        ),
        Group(
            id = "5",
            name = "Students Hub Bhujpur",
            description = "Study materials, notes, guidance and student discussions.",
            coverRes = R.drawable.img_group_students_hub,
            membersText = "1.1K Members",
            category = "Education",
            isJoined = false
        ),
        Group(
            id = "6",
            name = "Pet Lovers Bhujpur",
            description = "For pet lovers to share tips, stories and help each other.",
            coverRes = R.drawable.img_group_pet_lovers,
            membersText = "642 Members",
            category = "Pets",
            isJoined = false
        ),
        Group(
            id = "7",
            name = "Clean & Green Bhujpur",
            description = "Let's keep our city clean and green together.",
            coverRes = R.drawable.img_group_clean_green,
            membersText = "512 Members",
            category = "Environment",
            isJoined = false
        )
    )

    val groups = myGroups + discoverGroups


    val onboardingPages = listOf(
        OnboardingPage(
            titleFull = "AroundUs",
            titleHighlight = "Us",
            subtitleFull = "Your Neighborhood,\nConnected.",
            subtitleHighlight = "Connected.",
            description = "AroundUs helps you stay\nupdated, share, and connect\nwith people around you.",
            illustrationRes = R.drawable.img_onboarding_1,
            headerLogoRes = R.drawable.ic_aroundus_custom_logo,
            isFirstPage = true
        ),
        OnboardingPage(
            titleFull = "Stay Updated",
            titleHighlight = "Updated",
            description = "Get real-time updates about\nannouncements, events, and\nwhat's happening nearby.",
            illustrationRes = R.drawable.img_onboarding_stay_updated,
            isFirstPage = false
        ),
        OnboardingPage(
            titleFull = "Connect & Help",
            titleHighlight = "Help",
            description = "Connect with neighbors, ask for\nhelp, share useful information,\nand build a stronger community.",
            illustrationRes = R.drawable.img_onboarding_connect_help,
            isFirstPage = false
        ),
        OnboardingPage(
            titleFull = "Buy, Sell & Share",
            titleHighlight = "Share",
            description = "Buy and sell locally, or give\naway things you no longer need.\nYour neighborhood marketplace.",
            illustrationRes = R.drawable.img_onboarding_buy_sell_share,
            isFirstPage = false
        ),
        OnboardingPage(
            titleFull = "Events & Activities",
            titleHighlight = "Activities",
            description = "Discover local events, join\ncommunity activities, and never\nmiss what's happening near you.",
            illustrationRes = R.drawable.img_onboarding_events_activities,
            isFirstPage = false
        ),
        OnboardingPage(
            titleFull = "Stay Safe & Secure",
            titleHighlight = "Safe & Secure",
            description = "Share safety alerts, report\nincidents, and look out for\neach other in your neighborhood.",
            illustrationRes = R.drawable.img_onboarding_safety,
            isFirstPage = false
        ),
        OnboardingPage(
            titleFull = "Welcome to Around Us!",
            titleHighlight = "Around Us!",
            description = "Everything you need to connect,\ncollaborate, and build a better\nneighborhood \u2013 all in one place.",
            illustrationRes = R.drawable.img_onboarding_welcome,
            isFirstPage = false
        )
    )
}
