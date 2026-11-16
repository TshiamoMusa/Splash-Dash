package com.example.splashanddash

import android.os.Bundle
import android.view.Gravity
import android.widget.Toast

/** Contact details and a simple message form. */
class ContactActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        val main = column()

        main.addView(
            sectionTitle(
                "Get In Touch",
                "We're always ready to help with your vehicle cleaning needs."
            )
        )

        // ---------------------------------------------------------
        // CONTACT INFORMATION
        // ---------------------------------------------------------

        main.addView(
            card(
                "Splash & Dash",
                """
                Professional vehicle cleaning made simple.
                
                Phone
                012 345 6789
                
                Email
                info@splashanddash.co.za
                
                Location
                Rosebank, Johannesburg
                """.trimIndent()
            )
        )

        addVerticalSpace(main, 10)

        // ---------------------------------------------------------
        // CONTACT FORM
        // ---------------------------------------------------------

        main.addView(
            sectionTitle(
                "Send Us a Message",
                "We'll get back to you as soon as possible."
            )
        )

        val name = input("Your Name")
        val email = input("Email Address")
        val message = input("Your Message")

        // Make the message box tall and top-aligned
        message.setSingleLine(false)
        message.minLines = 5
        message.gravity = Gravity.TOP
        message.setPadding(dp(15), dp(14), dp(15), dp(14))

        main.addView(name)
        main.addView(email)
        main.addView(message)

        main.addView(
            button("SEND MESSAGE") {

                // All three fields are required
                if (
                    name.text.isBlank() ||
                    email.text.isBlank() ||
                    message.text.isBlank()
                ) {
                    Toast.makeText(
                        this,
                        "Please complete all fields.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@button
                }

                // Demo only: the message is not sent anywhere yet.
                Toast.makeText(
                    this,
                    "Message sent successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                // Clear the form after "sending"
                name.text.clear()
                email.text.clear()
                message.text.clear()
            }
        )

        // The old "BACK TO HOME" button was removed: the header has a back arrow now.

        main.addView(footer())

        screen.addView(main)

        // Fixed header (back arrow) + scrolling content
        showScreen(screen)
    }
}