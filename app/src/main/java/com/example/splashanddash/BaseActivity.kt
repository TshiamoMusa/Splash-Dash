package com.example.splashanddash

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth

/**
 * Base class for every screen in the Splash & Dash customer app.
 *
 * All colours come from res/values/colors.xml (light) and
 * res/values-night/colors.xml (dark), so every screen follows the
 * same theme automatically. Dark mode is not just a colour swap:
 *  - light mode lifts cards with soft shadows
 *  - dark mode lifts cards with a lighter surface and a thin border
 *  - buttons switch to sky blue with dark text so they stay readable
 */
open class BaseActivity : AppCompatActivity() {

    // ---------------------------------------------------------
    // Brand palette (same names as the website theme)
    // ---------------------------------------------------------

    protected val indigoBlue: Int get() = getColor(R.color.indigo_blue)
    protected val deepOceanBlue: Int get() = getColor(R.color.deep_ocean_blue)
    protected val oceanBlue: Int get() = getColor(R.color.ocean_blue)
    protected val skyBlue: Int get() = getColor(R.color.sky_blue)
    protected val iceBlue: Int get() = getColor(R.color.ice_blue)
    protected val white: Int get() = getColor(R.color.white)

    // ---------------------------------------------------------
    // Semantic colours (these adapt to light / dark mode)
    // ---------------------------------------------------------

    protected val backgroundColor: Int get() = getColor(R.color.background)
    protected val surface: Int get() = getColor(R.color.surface)
    protected val surfaceAlt: Int get() = getColor(R.color.surface_alt)
    protected val textColor: Int get() = getColor(R.color.text)
    protected val heading: Int get() = getColor(R.color.heading)
    protected val muted: Int get() = getColor(R.color.muted)
    protected val border: Int get() = getColor(R.color.border)
    protected val accent: Int get() = getColor(R.color.accent)
    protected val primary: Int get() = getColor(R.color.primary)
    protected val onPrimary: Int get() = getColor(R.color.on_primary)
    protected val headerStart: Int get() = getColor(R.color.header_start)
    protected val headerEnd: Int get() = getColor(R.color.header_end)
    protected val success: Int get() = getColor(R.color.success)
    protected val warning: Int get() = getColor(R.color.warning)
    protected val danger: Int get() = getColor(R.color.danger)

    // Older names still used by some screens. They now map to the new theme.
    protected val navy: Int get() = getColor(R.color.navy)
    protected val blue: Int get() = getColor(R.color.blue)
    protected val brightBlue: Int get() = getColor(R.color.bright_blue)

    /** True when the phone is currently in dark mode. */
    protected val isDark: Boolean
        get() = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES


    // ---------------------------------------------------------
    // Activity setup
    // ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // System bars blend into the screen, icons flip for light/dark.
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    override fun onStart() {
        super.onStart()

        if (!requiresLogin()) return

        if (!isSignedIn()) {
            android.util.Log.d("SplashDashAuth", "No session. Redirecting to Login.")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    /** Screens that only a logged-in customer may open. */
    private fun requiresLogin(): Boolean {
        return this is CustomerDashboardActivity ||
                this is BookingActivity ||
                this is BookingHistoryActivity ||
                this is PaymentActivity
    }

    /**
     * Google Sign-In goes through Firebase Auth, so a Firebase user means
     * "signed in". When the API login (JWT) is added, extend this one
     * function and every screen is covered.
     */
    private fun isSignedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }


    // ---------------------------------------------------------
    // Drawable helpers (everything is built in code so it follows
    // light/dark mode with no extra XML files)
    // ---------------------------------------------------------

    protected fun rounded(
        fill: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
        }
    }

    private fun withRipple(
        content: GradientDrawable,
        rippleColor: Int,
        radiusDp: Int
    ): RippleDrawable {
        val mask = rounded(Color.WHITE, radiusDp)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }


    // ---------------------------------------------------------
    // Screen container helpers
    // ---------------------------------------------------------

    open fun createScreen(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
    }

    protected fun content(): ScrollView {
        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(backgroundColor)
        }
    }

    protected fun column(padding: Int = 20): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (padding > 0) {
                setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            }
        }
    }


    // ---------------------------------------------------------
    // Top bar
    // ---------------------------------------------------------

    protected fun navBar(): LinearLayout {

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(16), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(headerStart, headerEnd)
            ).apply { cornerRadius = dp(24).toFloat() }
            elevation = if (isDark) 0f else dp(4).toFloat()
        }

        val logo = TextView(this).apply {
            text = "Splash & Dash"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
        navigation.addView(
            logo,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val settingsButton = navButton("Settings")
        settingsButton.setOnClickListener { go(SettingsActivity::class.java) }
        navigation.addView(settingsButton)

        return navigation
    }

    protected fun navBar(title: String, subtitle: String? = null): LinearLayout {
        return navBar()
    }

    private fun navButton(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rounded(Color.argb(40, 255, 255, 255), 20)
            isClickable = true
            isFocusable = true
        }
    }


    // ---------------------------------------------------------
    // Navigation helper
    // ---------------------------------------------------------

    protected fun go(destination: Class<*>) {
        startActivity(Intent(this, destination))
    }


    // ---------------------------------------------------------
    // Text helpers
    // ---------------------------------------------------------

    protected fun text(
        value: String,
        size: Float = 16f,
        isBold: Boolean = false,
        color: Int = textColor
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (isBold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    protected fun pageHeader(
        title: String,
        subtitle: String? = null
    ): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(20), dp(4), dp(16))
        }

        header.addView(TextView(this).apply {
            text = title
            textSize = 28f
            setTextColor(heading)
            typeface = Typeface.DEFAULT_BOLD
        })

        if (!subtitle.isNullOrBlank()) {
            header.addView(TextView(this).apply {
                text = subtitle
                textSize = 14f
                setTextColor(muted)
                setPadding(0, dp(4), 0, 0)
            })
        }

        return header
    }

    /** Small spaced-out label, same style as "PROFESSIONAL CAR CARE" on the website. */
    open fun sectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title.uppercase()
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(16), 0, dp(8))
        }
    }

    protected fun sectionTitle(title: String, subtitle: String?): View {
        return pageHeader(title, subtitle)
    }


    // ---------------------------------------------------------
    // Cards
    // ---------------------------------------------------------

    protected fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            // Light: soft shadow. Dark: lighter surface + thin border.
            background = rounded(surface, 20, if (isDark) border else null)
            elevation = if (isDark) 0f else dp(3).toFloat()
        }
    }

    protected fun card(
        title: String,
        body: String,
        actionLabel: String? = null,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val container = card().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(2), dp(6), dp(2), dp(14)) }
        }

        container.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(heading)
            typeface = Typeface.DEFAULT_BOLD
        })

        container.addView(TextView(this).apply {
            text = body
            textSize = 14f
            setTextColor(textColor)
            setPadding(0, dp(6), 0, dp(6))
        })

        if (actionLabel != null || onClick != null) {
            container.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                addView(TextView(this@BaseActivity).apply {
                    text = actionLabel ?: "Open"
                    textSize = 13f
                    setTextColor(onPrimary)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(18), dp(8), dp(18), dp(8))
                    background = rounded(primary, 20)
                })
            })
            if (onClick != null) {
                container.setOnClickListener { onClick() }
                container.isClickable = true
                container.isFocusable = true
            }
        }

        return container
    }

    protected fun metricCard(title: String, value: String): LinearLayout {
        return metricCardValueLabel(value, title)
    }

    protected fun metricCardValueLabel(value: String, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(18), dp(14), dp(18))
            background = rounded(surfaceAlt, 18, if (isDark) border else null)

            addView(TextView(this@BaseActivity).apply {
                text = value
                textSize = 26f
                setTextColor(accent)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })

            addView(TextView(this@BaseActivity).apply {
                text = label
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    protected fun metricRow(vararg cards: View): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(14)) }
            cards.forEach { card ->
                addView(
                    card,
                    LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { setMargins(dp(4), 0, dp(4), 0) }
                )
            }
        }
    }


    // ---------------------------------------------------------
    // Buttons & inputs
    // ---------------------------------------------------------

    /** Solid pill button. Deep ocean blue in light mode, sky blue in dark mode. */
    protected fun primaryButton(title: String): Button {
        return Button(this).apply {
            text = title
            textSize = 16f
            setTextColor(onPrimary)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            stateListAnimator = null
            elevation = 0f
            minHeight = dp(52)
            minimumHeight = dp(52)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = withRipple(
                rounded(primary, 28),
                Color.argb(50, Color.red(onPrimary), Color.green(onPrimary), Color.blue(onPrimary)),
                28
            )
        }
    }

    protected fun button(title: String, onClick: () -> Unit): Button {
        return primaryButton(title).apply { setOnClickListener { onClick() } }
    }

    /** Pill button with an accent outline, used for secondary actions. */
    protected fun outlineButton(title: String): Button {
        return Button(this).apply {
            text = title
            textSize = 16f
            setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            stateListAnimator = null
            elevation = 0f
            minHeight = dp(52)
            minimumHeight = dp(52)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = withRipple(
                rounded(Color.TRANSPARENT, 28, accent, 2),
                Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent)),
                28
            )
        }
    }

    protected fun outlineButton(title: String, onClick: () -> Unit): Button {
        return outlineButton(title).apply { setOnClickListener { onClick() } }
    }

    protected fun input(hint: String): EditText {
        return EditText(this).apply {
            setHint(hint)
            textSize = 16f
            setTextColor(textColor)
            setHintTextColor(muted)
            styleInput(this)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(12)) }
        }
    }

    /** Rounded field with a thin border that turns accent blue when focused. */
    protected fun styleInput(view: View) {
        view.background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                rounded(surface, 14, accent, 2)
            )
            addState(intArrayOf(), rounded(surface, 14, border, 1))
        }
        view.setPadding(dp(16), dp(14), dp(16), dp(14))
    }


    // ---------------------------------------------------------
    // Spacing & footer
    // ---------------------------------------------------------

    protected fun addSpacing(parent: LinearLayout, height: Int = 12) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height))
        )
    }

    protected fun addVerticalSpace(height: Int = 12): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(height)
            )
        }
    }

    protected fun addVerticalSpace(parent: LinearLayout, height: Int) {
        addSpacing(parent, height)
    }

    protected fun footer(): TextView {
        return TextView(this).apply {
            text = "© 2026 Splash & Dash Car Wash"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(12))
        }
    }

    protected fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    protected fun showAppMessage(
        message: String,
        title: String = "Splash & Dash"
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}