package com.aroundus.app.utils

import com.aroundus.app.models.ProductCardAttachment

object ProductMessageUtils {

    private const val TAG_PREFIX = "[PRODUCT_CARD:"
    private const val TAG_SUFFIX = "]"

    fun formatPrice(price: String): String {
        val trimmed = price.trim()
        if (trimmed.isEmpty()) return "₹0"
        if (trimmed.equals("Free", ignoreCase = true)) return "Free"
        if (trimmed.startsWith("₹") || trimmed.startsWith("Rs", ignoreCase = true)) {
            return trimmed
        }
        return "₹$trimmed"
    }

    fun formatInquiryText(productName: String, price: String): String {
        val cleanName = productName.trim().ifEmpty { "this item" }
        val cleanPrice = formatPrice(price)
        return "Hi, is '$cleanName' ($cleanPrice) still available?"
    }

    fun encodeProductMessage(
        id: String,
        title: String,
        price: String,
        imageUrl: String? = null,
        imageRes: Int = 0,
        customText: String? = null
    ): String {
        val formattedPrice = formatPrice(price)
        val text = customText?.trim()?.ifEmpty { null } ?: formatInquiryText(title, formattedPrice)
        val safeId = id.replace("|", "_").replace("]", "").trim()
        val safeTitle = title.replace("|", " ").replace("]", "").replace("\n", " ").trim()
        val safePrice = formattedPrice.replace("|", " ").replace("]", "").trim()
        val safeImage = (imageUrl ?: "").replace("|", "%7C").replace("]", "").replace("\n", "").trim()
        val tag = "$TAG_PREFIX" + "id=$safeId|title=$safeTitle|price=$safePrice|image=$safeImage|res=$imageRes" + "$TAG_SUFFIX"
        return "$text\n$tag"
    }

    fun parseMessage(rawText: String): Pair<String, ProductCardAttachment?> {
        val startIndex = rawText.indexOf(TAG_PREFIX)
        if (startIndex == -1) {
            return Pair(rawText, null)
        }

        val endIndex = rawText.indexOf(TAG_SUFFIX, startIndex + TAG_PREFIX.length)
        if (endIndex == -1) {
            return Pair(rawText, null)
        }

        val tagContent = rawText.substring(startIndex + TAG_PREFIX.length, endIndex)
        val displayText = (rawText.substring(0, startIndex) + rawText.substring(endIndex + TAG_SUFFIX.length)).trim()

        var id = ""
        var title = ""
        var price = ""
        var image = ""
        var res = 0

        tagContent.split("|").forEach { part ->
            val eqIdx = part.indexOf("=")
            if (eqIdx > 0) {
                val key = part.substring(0, eqIdx).trim()
                val value = part.substring(eqIdx + 1).trim()
                when (key) {
                    "id" -> id = value
                    "title" -> title = value
                    "price" -> price = value
                    "image" -> image = value.replace("%7C", "|")
                    "res" -> res = value.toIntOrNull() ?: 0
                }
            }
        }

        val attachment = if (title.isNotBlank() || id.isNotBlank()) {
            ProductCardAttachment(
                id = id,
                title = title,
                price = formatPrice(price),
                imageUrl = image.ifBlank { null },
                imageRes = res
            )
        } else {
            null
        }

        return Pair(displayText, attachment)
    }

    fun cleanMessagePreview(rawText: String): String {
        if (!rawText.contains(TAG_PREFIX)) {
            return rawText
        }
        val (clean, _) = parseMessage(rawText)
        return clean.ifBlank { "Marketplace listing inquiry" }
    }
}
