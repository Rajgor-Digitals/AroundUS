package com.aroundus.app

import com.aroundus.app.utils.ProductMessageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceContactSellerTest {

    @Test
    fun testFormatPrice() {
        assertEquals("₹2,500", ProductMessageUtils.formatPrice("₹2,500"))
        assertEquals("₹2,500", ProductMessageUtils.formatPrice("2,500"))
        assertEquals("Free", ProductMessageUtils.formatPrice("Free"))
        assertEquals("Free", ProductMessageUtils.formatPrice("free"))
        assertEquals("₹0", ProductMessageUtils.formatPrice(""))
    }

    @Test
    fun testFormatInquiryText() {
        val inquiry = ProductMessageUtils.formatInquiryText("Antique Wooden Chair", "2,500")
        assertEquals("Hi, is 'Antique Wooden Chair' (₹2,500) still available?", inquiry)

        val freeInquiry = ProductMessageUtils.formatInquiryText("Baby Stroller", "Free")
        assertEquals("Hi, is 'Baby Stroller' (Free) still available?", freeInquiry)
    }

    @Test
    fun testEncodeAndParseProductMessage() {
        val encoded = ProductMessageUtils.encodeProductMessage(
            id = "prod_999",
            title = "Solid Teak Wood Study Desk",
            price = "₹3,400",
            imageUrl = "https://example.com/desk.jpg",
            imageRes = 0
        )

        assertTrue(encoded.contains("Hi, is 'Solid Teak Wood Study Desk' (₹3,400) still available?"))
        assertTrue(encoded.contains("[PRODUCT_CARD:"))

        val (cleanText, attachment) = ProductMessageUtils.parseMessage(encoded)
        assertEquals("Hi, is 'Solid Teak Wood Study Desk' (₹3,400) still available?", cleanText)
        assertNotNull(attachment)
        assertEquals("prod_999", attachment?.id)
        assertEquals("Solid Teak Wood Study Desk", attachment?.title)
        assertEquals("₹3,400", attachment?.price)
        assertEquals("https://example.com/desk.jpg", attachment?.imageUrl)
    }

    @Test
    fun testParseNormalMessageWithoutProductCard() {
        val normalText = "Hey, let's meet tomorrow evening!"
        val (cleanText, attachment) = ProductMessageUtils.parseMessage(normalText)
        assertEquals(normalText, cleanText)
        assertNull(attachment)
    }

    @Test
    fun testCleanMessagePreview() {
        val encoded = ProductMessageUtils.encodeProductMessage(
            id = "prod_456",
            title = "Mountain Bike",
            price = "5,000",
            imageUrl = "https://example.com/bike.jpg"
        )

        val preview = ProductMessageUtils.cleanMessagePreview(encoded)
        assertEquals("Hi, is 'Mountain Bike' (₹5,000) still available?", preview)

        val regularText = "Can you send the location?"
        assertEquals(regularText, ProductMessageUtils.cleanMessagePreview(regularText))
    }
}
