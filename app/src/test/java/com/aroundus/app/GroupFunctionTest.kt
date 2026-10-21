package com.aroundus.app

import com.aroundus.app.models.GroupEntity
import com.aroundus.app.models.MessageEntity
import com.aroundus.app.models.resolveGroupCoverRes
import com.aroundus.app.models.toUIModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupFunctionTest {

    @Test
    fun testGroupEntityMemberParsingCommaSeparated() {
        val group = GroupEntity(
            groupId = "grp_101",
            name = "Bhujpur Runners",
            members = "usr_1,usr_2,usr_3"
        )

        val members = group.getMemberList()
        assertEquals(3, members.size)
        assertTrue(members.contains("usr_1"))
        assertTrue(members.contains("usr_2"))
        assertTrue(members.contains("usr_3"))
        assertEquals("3 Members", group.formatMembersDisplay())
    }

    @Test
    fun testGroupEntityMemberParsingJsonArray() {
        val group = GroupEntity(
            groupId = "grp_102",
            name = "Tech Hub",
            members = "[\"usr_alpha\", \"usr_beta\"]"
        )

        val members = group.getMemberList()
        assertEquals(2, members.size)
        assertTrue(members.contains("usr_alpha"))
        assertTrue(members.contains("usr_beta"))
        assertEquals("2 Members", group.formatMembersDisplay())
    }

    @Test
    fun testGroupEntityMemberParsingLegacyText() {
        val group = GroupEntity(
            groupId = "grp_103",
            name = "Bhujpur Residents",
            members = "14 Members"
        )

        val memberIds = group.getMemberList()
        assertTrue(memberIds.isEmpty())
        assertEquals(14, group.getMemberCount())
        assertEquals("14 Members", group.formatMembersDisplay())
    }

    @Test
    fun testGroupEntityIsMember() {
        val group = GroupEntity(
            groupId = "grp_104",
            name = "Green Volunteers",
            members = "user_amit,user_priya"
        )

        assertTrue(group.isMember("user_amit"))
        assertTrue(group.isMember("user_priya"))
        assertFalse(group.isMember("user_stranger"))
        assertFalse(group.isMember(null))
        assertFalse(group.isMember(""))
    }

    @Test
    fun testGroupEntityToUIModelIsJoined() {
        val group = GroupEntity(
            groupId = "grp_105",
            name = "Local Art Club",
            description = "Community artists",
            category = "Activities",
            members = "user_me,user_friend"
        )

        val uiModelJoined = group.toUIModel("user_me")
        assertTrue(uiModelJoined.isJoined)
        assertEquals("Activities", uiModelJoined.category)
        assertEquals("Local Art Club", uiModelJoined.name)

        val uiModelNotJoined = group.toUIModel("user_other")
        assertFalse(uiModelNotJoined.isJoined)
    }

    @Test
    fun testGroupCoverResolution() {
        assertEquals(R.drawable.img_group_bhujpur_buysell, resolveGroupCoverRes("Buy / Sell", "Garage Sale"))
        assertEquals(R.drawable.img_group_help_each_other, resolveGroupCoverRes("Help & Support", "Elderly Care"))
        assertEquals(R.drawable.img_group_yoga_wellness, resolveGroupCoverRes("Activities", "Morning Yoga"))
        assertEquals(R.drawable.img_group_pet_lovers, resolveGroupCoverRes("General", "Kutch Pet Lovers"))
        assertEquals(R.drawable.img_group_students_hub, resolveGroupCoverRes("Education", "Bhujpur Students Club"))
        assertEquals(R.drawable.img_group_clean_green, resolveGroupCoverRes("Local", "Clean Village Green Village"))
    }

    @Test
    fun testGroupMembershipJoinAndLeaveLogic() {
        val group = GroupEntity(
            groupId = "grp_106",
            name = "Cycling Club",
            members = "usr_1,usr_2"
        )

        // Join: Add usr_3
        val currentMembers = group.getMemberList().toMutableList()
        val newUserId = "usr_3"
        if (!currentMembers.contains(newUserId)) {
            currentMembers.add(newUserId)
        }
        val joinedGroup = group.copy(members = currentMembers.joinToString(","))
        assertEquals(3, joinedGroup.getMemberList().size)
        assertTrue(joinedGroup.isMember("usr_3"))

        // Leave: Remove usr_1
        currentMembers.remove("usr_1")
        val leftGroup = joinedGroup.copy(members = currentMembers.joinToString(","))
        assertEquals(2, leftGroup.getMemberList().size)
        assertFalse(leftGroup.isMember("usr_1"))
        assertTrue(leftGroup.isMember("usr_2"))
        assertTrue(leftGroup.isMember("usr_3"))
    }

    @Test
    fun testGroupMessageFiltering() {
        val messages = listOf(
            MessageEntity(messageId = "m1", senderId = "u1", receiverId = "u2", groupId = null, text = "Direct msg"),
            MessageEntity(messageId = "m2", senderId = "u1", receiverId = null, groupId = "grp_100", text = "Hello Group 100"),
            MessageEntity(messageId = "m3", senderId = "u2", receiverId = null, groupId = "grp_100", text = "Welcome all!"),
            MessageEntity(messageId = "m4", senderId = "u3", receiverId = null, groupId = "grp_200", text = "Hello Group 200")
        )

        val targetGroupId = "grp_100"
        val group100Messages = messages.filter { it.groupId == targetGroupId }

        assertEquals(2, group100Messages.size)
        assertEquals("m2", group100Messages[0].messageId)
        assertEquals("m3", group100Messages[1].messageId)
    }
}
