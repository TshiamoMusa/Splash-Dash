package com.example.splashanddash

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout

/** Photo gallery of cleaned vehicles. Back arrow comes from the shared header. */
class GalleryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        val main = column()

        // ---------------------------------------------------------
        // PAGE TITLE
        // ---------------------------------------------------------

        main.addView(text("Our Work Gallery", 30f, true))

        main.addView(
            text("Take a look at some of the vehicles we have professionally cleaned.", 15f)
        )

        // Blue accent line under the title
        val accentLine = View(this)
        accentLine.setBackgroundColor(android.graphics.Color.rgb(0, 168, 255))

        val accentParams = LinearLayout.LayoutParams(dp(115), dp(5))
        accentParams.setMargins(0, dp(10), 0, dp(18))

        accentLine.layoutParams = accentParams
        main.addView(accentLine)

        // ---------------------------------------------------------
        // GALLERY GRID (3 columns)
        // ---------------------------------------------------------

        val gallery = GridLayout(this)

        gallery.columnCount = 3
        gallery.rowCount = GridLayout.UNDEFINED
        gallery.alignmentMode = GridLayout.ALIGN_MARGINS
        gallery.useDefaultMargins = false

        val images = intArrayOf(
            R.drawable.wash1,
            R.drawable.wash2,
            R.drawable.wash3,
            R.drawable.wash4,
            R.drawable.wash5,
            R.drawable.wash6,
            R.drawable.wash7,
            R.drawable.wash8,
            R.drawable.wash9,
            R.drawable.wash10,
            R.drawable.wash11,
            R.drawable.wash12,
            R.drawable.wash13
        )

        images.forEachIndexed { index, imageRes ->

            val image = ImageView(this)

            image.setImageResource(imageRes)
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setBackgroundResource(R.drawable.gallery_image_bg)
            image.clipToOutline = true

            // Make every image a clean square tile that shares the row equally.
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = dp(115)
            params.columnSpec = GridLayout.spec(index % 3, 1f)
            params.setMargins(dp(4), dp(4), dp(4), dp(4))
            image.layoutParams = params

            // Small zoom effect when tapped
            image.setOnClickListener {
                image.animate()
                    .scaleX(1.04f)
                    .scaleY(1.04f)
                    .setDuration(120)
                    .withEndAction {
                        image.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120)
                            .start()
                    }
                    .start()
            }

            gallery.addView(image)
        }

        gallery.layoutParams = LinearLayout.LayoutParams(-1, -2)
        main.addView(gallery)

        // ---------------------------------------------------------
        // QUALITY MESSAGE + BOOK BUTTON
        // ---------------------------------------------------------

        addVerticalSpace(main, 18)

        val qualityCard = LinearLayout(this)

        qualityCard.orientation = LinearLayout.VERTICAL
        qualityCard.gravity = Gravity.CENTER
        qualityCard.setPadding(dp(20), dp(20), dp(20), dp(20))
        qualityCard.setBackgroundResource(R.drawable.gallery_callout_bg)

        val qualityParams = LinearLayout.LayoutParams(-1, -2)
        qualityParams.setMargins(0, dp(8), 0, dp(8))
        qualityCard.layoutParams = qualityParams

        val qualityTitle = text("We take pride in every detail", 20f, true)
        qualityTitle.gravity = Gravity.CENTER
        qualityCard.addView(qualityTitle)

        val qualityText = text(
            "From quick washes to full detailing, your car is in good hands.",
            14f
        )
        qualityText.gravity = Gravity.CENTER
        qualityCard.addView(qualityText)

        addVerticalSpace(qualityCard, 8)

        qualityCard.addView(
            button("BOOK A WASH") {
                go(BookingActivity::class.java)
            }
        )

        main.addView(qualityCard)

        // The old "BACK TO HOME" button was removed: the header has a back arrow now.

        main.addView(footer())

        screen.addView(main)

        // Fixed header (back arrow) + scrolling content
        showScreen(screen)
    }
}