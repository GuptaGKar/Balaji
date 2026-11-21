package com.balaji.provision.ui.i18n

/**
 * Hindi forms for strings that carry a number, a price or a store-provided name.
 *
 * The patterns only match the app's own sentence shapes ("Your Cart (3)",
 * "Add ₹40 more for free delivery"), so a product name is captured and inserted
 * verbatim instead of being translated. The static [HindiStrings] map is checked
 * first by [AppStrings.text]; these rules cover the remaining composed labels.
 */
internal val HindiDynamicStrings: List<Pair<Regex, String>> = listOf(
    Regex("^Your Cart \\((\\d+)\\)$") to "आपका कार्ट ($1)",
    Regex("^Deliver to (.+)$") to "यहाँ डिलीवरी: $1",
    Regex("^Image of (.+)$") to "$1 की तस्वीर",
    Regex("^Pin at (.+)$") to "$1 पर पिन",
    Regex("^(\\d+) pack$") to "$1 पैक",
    Regex("^Your items \\((\\d+)\\)$") to "आपका सामान ($1)",
    Regex("^In stock \\((\\d+)\\)$") to "स्टॉक में ($1)",
    Regex("^All \\((\\d+)\\)$") to "सभी ($1)",
    Regex("^Current \\((\\d+)\\)$") to "वर्तमान ($1)",
    Regex("^Previous \\((\\d+)\\)$") to "पिछले ($1)",
    Regex("^(\\d+) pack sizes available$") to "$1 पैक साइज़ उपलब्ध",
    Regex("^(\\d+) pack sizes$") to "$1 पैक साइज़",
    Regex("^(\\d+) packs$") to "$1 पैक",
    Regex("^(\\d+) items?$") to "$1 सामान",
    Regex("^(\\d+) in basket$") to "$1 बास्केट में",
    Regex("^(\\d+) active · (\\d+) past$") to "$1 सक्रिय · $2 पिछले",
    Regex("^In cart · (.+)$") to "कार्ट में · $1",
    Regex("^Add to Cart · (.+)$") to "कार्ट में जोड़ें · $1",
    Regex("^(\\d+)% OFF$") to "$1% छूट",
    Regex("^Add (.+) more for free delivery$") to "मुफ़्त डिलीवरी के लिए $1 और जोड़ें",
    Regex("^Add (.+) more for FREE delivery\\.$") to "मुफ़्त डिलीवरी के लिए $1 और जोड़ें।",
    Regex("^Free delivery above (.+)$") to "$1 से ऊपर मुफ़्त डिलीवरी",
    Regex("^Free delivery on orders of (.+) or more$") to "$1 या उससे अधिक के ऑर्डर पर मुफ़्त डिलीवरी",
    Regex("^Delivery in (.+)$") to "डिलीवरी $1 में",
    Regex("^(.+) delivery charge applies$") to "$1 डिलीवरी शुल्क लागू",
    Regex("^(.+) delivery$") to "$1 डिलीवरी",
    Regex("^Step (\\d+) of 3$") to "चरण $1 / 3",
    Regex("^Order #(.+)$") to "ऑर्डर #$1",
    Regex("^Cancel order #(.+)\\?$") to "ऑर्डर #$1 रद्द करें?",
    Regex("^Rate order #(.+)$") to "ऑर्डर #$1 को रेट करें",
    Regex("^Placed (.+)$") to "$1 को ऑर्डर किया",
    Regex("^Store reply: (.+)$") to "स्टोर का जवाब: $1",
    Regex("^(.+) will be removed from your saved addresses\\.$") to
        "$1 आपके सहेजे गए पतों से हटा दिया जाएगा।",
    Regex("^OTP sent to (.+) to create your account\\.$") to
        "खाता बनाने के लिए $1 पर OTP भेजा गया।",
    Regex("^OTP sent to (.+)\\.$") to "$1 पर OTP भेजा गया।",
    Regex("^Enter the 6-digit code sent to (.+)\\.$") to "$1 पर भेजा गया 6 अंकों का कोड दर्ज करें।",
    Regex("^Signed in as (.+)$") to "$1 के रूप में साइन इन",
    Regex("^Resend in (\\d+)s$") to "$1 सेकंड में फिर भेजें",
    Regex("^Enter quantity in grams$") to "मात्रा ग्राम में दर्ज करें",
    Regex("^Enter quantity in kg$") to "मात्रा किलोग्राम में दर्ज करें",
    Regex("^Add one more to (.+)$") to "$1 में एक और जोड़ें",
    Regex("^Remove one from (.+)$") to "$1 से एक हटाएँ",
    Regex("^Add (.+), (.+), to cart$") to "$1, $2, कार्ट में जोड़ें",
    Regex("^Open cart, (\\d+) items$") to "कार्ट खोलें, $1 सामान",
    Regex("^Open (.+)$") to "$1 खोलें",
    Regex("^Favorite (.+)$") to "पसंदीदा $1",
    Regex("^Remove (.+) from cart$") to "$1 को कार्ट से हटाएँ",
    Regex("^Decrease quantity of (.+)$") to "$1 की मात्रा घटाएँ",
    Regex("^Increase quantity of (.+)$") to "$1 की मात्रा बढ़ाएँ",
    Regex("^We could not find \"(.+)\" in the store\\.$") to
        "हमें स्टोर में \"$1\" नहीं मिला।",
    Regex("^We could not find \"(.+)\" in this category\\.$") to
        "हमें इस श्रेणी में \"$1\" नहीं मिला।",
    Regex("^We could not find \"(.+)\"\\. Check the spelling, or ask us to stock it\\.$") to
        "हमें \"$1\" नहीं मिला। वर्तनी जाँचें या हमसे इसे स्टॉक करने को कहें।"
)
