package com.example.mybible.reminders

/**
 * The 6 hourly slots x 6 themed message rotate through these 36 lines so the
 * 16 daily notifications don't recite the same line every day. Ported
 * verbatim from the Capacitor app's `READING_REMINDER_MESSAGES`. `{ref}` is
 * replaced with the user's current reading position (e.g. "John 3").
 */
object ReminderMessages {
    val ALL: List<String> = listOf(
        // The word draws us close to God
        "Open {ref} today \u2014 His word is how He speaks straight into your day.",
        "Every page of {ref} is God drawing nearer to you. Come and listen.",
        "You're in {ref}. Let it quiet your mind and turn your heart toward Him.",
        "The Bible isn't just words \u2014 it's God's voice. Pick up where you left off in {ref}.",
        "Nothing brings you closer to God like His own words. Continue in {ref}.",
        "A few minutes in {ref} can reshape your whole day. Go on, open it.",
        // The gospel
        "Christ died, was buried, and rose again \u2014 that's the hope at the center of {ref} and every page before it.",
        "The gospel is simple: He loved you enough to die, and rose so you could live. Remember that in {ref}.",
        "Whatever {ref} says today, it's still pointing to the cross and the empty tomb.",
        "Good news doesn't get old: Jesus paid it all. Let {ref} remind you why that matters.",
        "The cross wasn't the end of the story \u2014 the resurrection was the beginning of yours. Keep reading in {ref}.",
        "Every book of the Bible leads back to one gospel: Christ crucified and risen. Continue in {ref}.",
        // His love
        "Before you did anything right or wrong, He loved you. Let that truth meet you in {ref}.",
        "His love isn't based on how today went. Rest in that as you read {ref}.",
        "You are loved right now, not because you earned it, but because He chose to. Open {ref}.",
        "Nothing you did today can separate you from His love. Come back to {ref} and remember.",
        "God's love for you didn't start today, and it won't end tomorrow. Sit with {ref} for a minute.",
        "You're not just written about in {ref} \u2014 you're loved by the One who wrote it.",
        // Our place / identity
        "You're not a stranger to God \u2014 you're His child. Let {ref} remind you where you belong.",
        "Your worth was never up for debate; it was settled at the cross. Read on in {ref}.",
        "You have a seat at His table, not because you're perfect, but because you're His. Continue in {ref}.",
        "Whatever today held, your place with Him hasn't changed. Return to {ref}.",
        "You were made for more than just getting through the day \u2014 you were made for Him. Open {ref}.",
        "Adopted, chosen, kept \u2014 that's who you are in Christ. Let {ref} remind you.",
        // Deception / watchfulness
        "Not everything that sounds true is true. Let {ref} sharpen what you can recognize.",
        "The enemy rarely comes as an obvious lie \u2014 he comes as almost the truth. Stay in {ref} to know the difference.",
        "Guard your heart today; deception rarely announces itself. Keep {ref} close.",
        "The best defense against a counterfeit is knowing the real thing well. Study {ref}.",
        "Stay alert \u2014 what looks harmless can quietly pull you off course. Let {ref} anchor you.",
        "Test everything against His word, not just your feelings. Return to {ref}.",
        // Our purpose
        "You weren't made to just pass time \u2014 you were made for a purpose. Let {ref} remind you of it.",
        "Whatever your day looks like, you were made to reflect Him in it. Continue in {ref}.",
        "Your life has a calling bigger than today's to-do list. Open {ref} and remember it.",
        "You're here for more than yourself \u2014 go live like it. Let {ref} point the way.",
        "God didn't save you to sit still \u2014 He saved you to be sent. Keep reading in {ref}.",
        "Every chapter you read is preparing you for the purpose ahead. Continue in {ref}."
    )
}
