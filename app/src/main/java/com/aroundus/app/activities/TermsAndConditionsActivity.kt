package com.aroundus.app.activities

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aroundus.app.databinding.ActivityTermsAndConditionsBinding

class TermsAndConditionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTermsAndConditionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermsAndConditionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupContactUsSpannable()
        setupExpandableCards()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.btnAgree.setOnClickListener {
            Toast.makeText(this, "Thank you for accepting AroundUS Terms & Conditions", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun setupContactUsSpannable() {
        val email = "support@aroundus.in"
        val fullText = "If you have any questions about these Terms and Conditions, please contact us at\n$email"
        val spannable = SpannableString(fullText)

        val startIndex = fullText.indexOf(email)
        if (startIndex >= 0) {
            val endIndex = startIndex + email.length
            val greenColor = Color.parseColor("#16A34A")

            spannable.setSpan(
                ForegroundColorSpan(greenColor),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    try {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$email")
                            putExtra(Intent.EXTRA_SUBJECT, "AroundUS Terms and Conditions Query")
                        }
                        startActivity(Intent.createChooser(emailIntent, "Send email"))
                    } catch (_: Exception) {
                        Toast.makeText(this@TermsAndConditionsActivity, "Contact: $email", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = greenColor
                    ds.isUnderlineText = false
                }
            }

            spannable.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.tvDescContact.text = spannable
        binding.tvDescContact.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupExpandableCards() {
        setupToggle(binding.cardAcceptance, binding.tvDescAcceptance, binding.ivChevronAcceptance)
        setupToggle(binding.cardEligibility, binding.tvDescEligibility, binding.ivChevronEligibility)
        setupToggle(binding.cardAccount, binding.tvDescAccount, binding.ivChevronAccount)
        setupToggle(binding.cardContent, binding.tvDescContent, binding.ivChevronContent)
        setupToggle(binding.cardProhibited, binding.tvDescProhibited, binding.ivChevronProhibited)
        setupToggle(binding.cardPrivacy, binding.tvDescPrivacy, binding.ivChevronPrivacy)
        setupToggle(binding.cardChanges, binding.tvDescChanges, binding.ivChevronChanges)
        setupToggle(binding.cardContact, binding.tvDescContact, binding.ivChevronContact)
    }

    private fun setupToggle(card: View, desc: TextView, chevron: ImageView) {
        card.setOnClickListener {
            val isCurrentlyVisible = desc.visibility == View.VISIBLE
            if (isCurrentlyVisible) {
                desc.visibility = View.GONE
                chevron.animate().rotation(180f).setDuration(200).start()
            } else {
                desc.visibility = View.VISIBLE
                chevron.animate().rotation(0f).setDuration(200).start()
            }
        }
    }
}
