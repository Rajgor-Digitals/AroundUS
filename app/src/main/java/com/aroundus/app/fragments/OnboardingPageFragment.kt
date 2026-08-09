package com.aroundus.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aroundus.app.R
import com.aroundus.app.utils.DummyData

class OnboardingPageFragment : Fragment() {

    companion object {
        private const val ARG_POSITION = "position"

        fun newInstance(position: Int): OnboardingPageFragment {
            return OnboardingPageFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_POSITION, position)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val position = arguments?.getInt(ARG_POSITION) ?: 0
        val page = DummyData.onboardingPages.getOrNull(position) ?: return

        val layoutPage1 = view.findViewById<View>(R.id.layoutPage1)
        val layoutPageStandard = view.findViewById<View>(R.id.layoutPageStandard)
        val layoutPage2 = view.findViewById<View>(R.id.layoutPage2)
        val layoutPage3 = view.findViewById<View>(R.id.layoutPage3)
        val layoutPage4 = view.findViewById<View>(R.id.layoutPage4)
        val layoutPage5 = view.findViewById<View>(R.id.layoutPage5)
        val layoutPage6 = view.findViewById<View>(R.id.layoutPage6)
        val layoutPage7 = view.findViewById<View>(R.id.layoutPage7)

        val greenColor = Color.parseColor("#16A34A")

        when {
            page.isFirstPage -> {
                // --- Slide 1: AroundUs brand intro ---
                layoutPage1.visibility = View.VISIBLE
                layoutPageStandard.visibility = View.GONE
                layoutPage2.visibility = View.GONE
                layoutPage3.visibility = View.GONE
                layoutPage4.visibility = View.GONE
                layoutPage5.visibility = View.GONE
                layoutPage6.visibility = View.GONE
                layoutPage7.visibility = View.GONE

                val tvTitle1 = view.findViewById<TextView>(R.id.tvTitle1)
                val tvSubtitle1 = view.findViewById<TextView>(R.id.tvSubtitle1)
                val tvDesc1 = view.findViewById<TextView>(R.id.tvDesc1)
                val ivIllustration1 = view.findViewById<ImageView>(R.id.ivIllustration1)
                val ivLogoPin1 = view.findViewById<ImageView>(R.id.ivLogoPin1)

                val logoRes = page.headerLogoRes
                if (logoRes != null && logoRes != 0) {
                    ivLogoPin1.setImageResource(logoRes)
                }

                tvTitle1.text = formatTwoToneText(page.titleFull, page.titleHighlight, greenColor)

                if (!page.subtitleFull.isNullOrEmpty()) {
                    tvSubtitle1.visibility = View.VISIBLE
                    tvSubtitle1.text = formatTwoToneText(page.subtitleFull, page.subtitleHighlight ?: "", greenColor)
                } else {
                    tvSubtitle1.visibility = View.GONE
                }

                tvDesc1.text = page.description
                ivIllustration1.setImageResource(page.illustrationRes)
            }

            position == 1 -> {
                // --- Slide 2: "Stay Updated" — dedicated layout matching reference ---
                layoutPage1.visibility = View.GONE
                layoutPageStandard.visibility = View.GONE
                layoutPage2.visibility = View.VISIBLE
                layoutPage3.visibility = View.GONE
                layoutPage4.visibility = View.GONE
                layoutPage5.visibility = View.GONE
                layoutPage6.visibility = View.GONE
                layoutPage7.visibility = View.GONE

                val tvTitlePage2 = view.findViewById<TextView>(R.id.tvTitlePage2)
                val tvDescPage2 = view.findViewById<TextView>(R.id.tvDescPage2)

                tvTitlePage2.text = formatTwoToneText(page.titleFull, page.titleHighlight, greenColor)
                tvDescPage2.text = page.description
            }

            position == 2 -> {
                // --- Slide 3: "Connect & Help" — dedicated layout matching reference ---
                layoutPage1.visibility = View.GONE
                layoutPageStandard.visibility = View.GONE
                layoutPage2.visibility = View.GONE
                layoutPage3.visibility = View.VISIBLE
                layoutPage4.visibility = View.GONE
                layoutPage5.visibility = View.GONE
                layoutPage6.visibility = View.GONE
                layoutPage7.visibility = View.GONE

                val tvTitlePage3 = view.findViewById<TextView>(R.id.tvTitlePage3)
                val tvDescPage3 = view.findViewById<TextView>(R.id.tvDescPage3)

                tvTitlePage3.text = formatTwoToneText(page.titleFull, page.titleHighlight, greenColor)
                tvDescPage3.text = page.description
            }

            position == 3 -> {
                // --- Slide 4: "Buy, Sell & Share" — marketplace slide ---
                layoutPage1.visibility = View.GONE
                layoutPageStandard.visibility = View.GONE
                layoutPage2.visibility = View.GONE
                layoutPage3.visibility = View.GONE
                layoutPage4.visibility = View.VISIBLE
                layoutPage5.visibility = View.GONE
                layoutPage6.visibility = View.GONE
                layoutPage7.visibility = View.GONE

                val tvTitlePage4 = view.findViewById<TextView>(R.id.tvTitlePage4)
                val tvDescPage4 = view.findViewById<TextView>(R.id.tvDescPage4)

                tvTitlePage4.text = formatTwoToneText(page.titleFull, page.titleHighlight, greenColor)
                tvDescPage4.text = page.description
            }

            position == 4 -> {
                // --- Slide 5: "Events & Activities" — events calendar slide ---
                layoutPage1.visibility = View.GONE
                layoutPageStandard.visibility = View.GONE
                layoutPage2.visibility = View.GONE
                layoutPage3.visibility = View.GONE
                layoutPage4.visibility = View.GONE
                layoutPage5.visibility = View.VISIBLE
                layoutPage6.visibility = View.GONE
                layoutPage7.visibility = View.GONE

                val tvTitlePage5 = view.findViewById<TextView>(R.id.tvTitlePage5)
                val tvDescPage5 = view.findViewById<TextView>(R.id.tvDescPage5)

                tvTitlePage5.text = formatTwoToneText(page.titleFull, page.titleHighlight, greenColor)
                tvDescPage5.text = page.description
            }

            position == 5 -> {
                // --- Slide 6: "Stay Safe & Secure" — safety slide ---
                layoutPage1.visibility = View.GONE
                layoutPageStandard.visibility = View.GONE
                layoutPage2.visibility = View.GONE
                layoutPage3.visibility = View.GONE
                layoutPage4.visibility = View.GONE
                layoutPage5.visibility = View.GONE
                layoutPage6.visibility = View.VISIBLE
                layoutPage7.visibility = View.GONE

                val tvTitlePage6 = view.findViewById<TextView>(R.id.tvTitlePage6)
                val tvDescPage6 = view.findViewById<TextView>(R.id.tvDescPage6)

                tvTitlePage6.text = formatTwoToneText(page.titleFull, page.titleHighlight, greenColor)
                tvDescPage6.text = page.description
            }

            position == 6 -> {
                // --- Slide 7: "Welcome to Around Us!" — grand finale ---
                layoutPage1.visibility = View.GONE
                layoutPageStandard.visibility = View.GONE
                layoutPage2.visibility = View.GONE
                layoutPage3.visibility = View.GONE
                layoutPage4.visibility = View.GONE
                layoutPage5.visibility = View.GONE
                layoutPage6.visibility = View.GONE
                layoutPage7.visibility = View.VISIBLE

                val tvTitlePage7 = view.findViewById<TextView>(R.id.tvTitlePage7)
                val tvDescPage7 = view.findViewById<TextView>(R.id.tvDescPage7)

                tvTitlePage7.text = formatTwoToneText(page.titleFull, page.titleHighlight, greenColor)
                tvDescPage7.text = page.description
            }

            else -> {
                // --- Fallback for any future slides: standard layout ---
                layoutPage1.visibility = View.GONE
                layoutPage2.visibility = View.GONE
                layoutPage3.visibility = View.GONE
                layoutPage4.visibility = View.GONE
                layoutPage5.visibility = View.GONE
                layoutPage6.visibility = View.GONE
                layoutPage7.visibility = View.GONE
                layoutPageStandard.visibility = View.VISIBLE

                val tvTitleStandard = view.findViewById<TextView>(R.id.tvTitleStandard)
                val tvDescStandard = view.findViewById<TextView>(R.id.tvDescStandard)
                val ivIllustrationStandard = view.findViewById<ImageView>(R.id.ivIllustrationStandard)

                tvTitleStandard.text = formatTwoToneText(page.titleFull, page.titleHighlight, greenColor)
                tvDescStandard.text = page.description
                ivIllustrationStandard.setImageResource(page.illustrationRes)
            }
        }
    }

    private fun formatTwoToneText(fullText: String, highlightText: String, highlightColor: Int): SpannableString {
        val spannable = SpannableString(fullText)
        if (highlightText.isNotEmpty()) {
            val startIndex = fullText.indexOf(highlightText)
            if (startIndex >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(highlightColor),
                    startIndex,
                    startIndex + highlightText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return spannable
    }
}
