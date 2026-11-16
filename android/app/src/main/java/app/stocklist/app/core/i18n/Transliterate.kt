package app.stocklist.app.core.i18n

/**
 * Writes a name in the other script by SOUND, not by meaning: "Sugar" becomes "शुगर" (not "चीनी") and "शुगर" becomes
 * "Sugar". Works offline. Common shop words are looked up in a word list; other words are spelled out by letter sounds,
 * which is only approximate, so the screens always let the user correct the result.
 */
object Transliterate {
    private val words: Map<String, String> = mapOf(
        "sugar" to "शुगर",
        "salt" to "सॉल्ट",
        "rice" to "राइस",
        "oil" to "ऑयल",
        "flour" to "फ्लोर",
        "wheat" to "व्हीट",
        "atta" to "आटा",
        "dal" to "दाल",
        "chawal" to "चावल",
        "basmati" to "बासमती",
        "tea" to "टी",
        "chai" to "चाय",
        "coffee" to "कॉफी",
        "milk" to "मिल्क",
        "butter" to "बटर",
        "ghee" to "घी",
        "paneer" to "पनीर",
        "cheese" to "चीज़",
        "curd" to "कर्ड",
        "dahi" to "दही",
        "bread" to "ब्रेड",
        "biscuit" to "बिस्कुट",
        "biscuits" to "बिस्कुट्स",
        "chocolate" to "चॉकलेट",
        "soap" to "सोप",
        "shampoo" to "शैम्पू",
        "toothpaste" to "टूथपेस्ट",
        "brush" to "ब्रश",
        "powder" to "पाउडर",
        "masala" to "मसाला",
        "spice" to "स्पाइस",
        "spices" to "स्पाइसेस",
        "turmeric" to "टर्मरिक",
        "haldi" to "हल्दी",
        "chilli" to "चिली",
        "chili" to "चिली",
        "mirch" to "मिर्च",
        "jeera" to "जीरा",
        "cumin" to "क्यूमिन",
        "coriander" to "कोरिएंडर",
        "dhania" to "धनिया",
        "pepper" to "पेपर",
        "onion" to "ओनियन",
        "pyaz" to "प्याज़",
        "potato" to "पोटैटो",
        "aloo" to "आलू",
        "tomato" to "टोमैटो",
        "tamatar" to "टमाटर",
        "garlic" to "गार्लिक",
        "lahsun" to "लहसुन",
        "ginger" to "जिंजर",
        "adrak" to "अदरक",
        "lemon" to "लेमन",
        "nimbu" to "नींबू",
        "apple" to "एप्पल",
        "banana" to "बनाना",
        "mango" to "मैंगो",
        "orange" to "ऑरेंज",
        "grapes" to "ग्रेप्स",
        "water" to "वॉटर",
        "juice" to "जूस",
        "cold" to "कोल्ड",
        "drink" to "ड्रिंक",
        "soda" to "सोडा",
        "packet" to "पैकेट",
        "box" to "बॉक्स",
        "bottle" to "बॉटल",
        "bag" to "बैग",
        "pack" to "पैक",
        "tin" to "टिन",
        "jar" to "जार",
        "can" to "कैन",
        "piece" to "पीस",
        "pieces" to "पीसेज़",
        "pcs" to "पीसी",
        "pc" to "पीसी",
        "kg" to "केजी",
        "gm" to "जीएम",
        "gram" to "ग्राम",
        "ml" to "एमएल",
        "ltr" to "लीटर",
        "litre" to "लीटर",
        "liter" to "लीटर",
        "dozen" to "डज़न",
        "tray" to "ट्रे",
        "carton" to "कार्टन",
        "case" to "केस",
        "bundle" to "बंडल",
        "big" to "बिग",
        "small" to "स्मॉल",
        "medium" to "मीडियम",
        "large" to "लार्ज",
        "mini" to "मिनी",
        "fresh" to "फ्रेश",
        "pure" to "प्योर",
        "organic" to "ऑर्गेनिक",
        "red" to "रेड",
        "green" to "ग्रीन",
        "white" to "व्हाइट",
        "black" to "ब्लैक",
        "yellow" to "येलो",
        "blue" to "ब्लू",
        "brown" to "ब्राउन",
        "sweet" to "स्वीट",
        "hot" to "हॉट",
        "spicy" to "स्पाइसी",
        "salty" to "सॉल्टी",
        "instant" to "इंस्टेंट",
        "noodles" to "नूडल्स",
        "pasta" to "पास्ता",
        "maggi" to "मैगी",
        "sauce" to "सॉस",
        "ketchup" to "केचप",
        "jam" to "जैम",
        "honey" to "हनी",
        "nuts" to "नट्स",
        "almond" to "आल्मंड",
        "cashew" to "कैशू",
        "raisin" to "रेज़िन",
        "dates" to "डेट्स",
        "and" to "एंड",
        "of" to "ऑफ",
        "the" to "द",
        "with" to "विद",
        "for" to "फॉर",
        "no" to "नो",
        "new" to "न्यू",
        "old" to "ओल्ड",
        "plus" to "प्लस",
        "extra" to "एक्स्ट्रा",
        "special" to "स्पेशल",
        "gold" to "गोल्ड",
        "premium" to "प्रीमियम",
        "classic" to "क्लासिक",
        "super" to "सुपर",
        "family" to "फैमिली",
        "net" to "नेट",
        "total" to "टोटल",
        "item" to "आइटम",
        "stock" to "स्टॉक",
        "list" to "लिस्ट",
        "cake" to "केक",
        "cream" to "क्रीम",
        "ice" to "आइस",
        "candy" to "कैंडी",
        "chips" to "चिप्स",
        "namkeen" to "नमकीन",
        "papad" to "पापड़",
        "pickle" to "पिकल",
        "achar" to "अचार",
        "vinegar" to "विनेगर",
        "detergent" to "डिटर्जेंट",
        "surf" to "सर्फ़",
        "washing" to "वॉशिंग",
        "liquid" to "लिक्विड",
        "cleaner" to "क्लीनर",
        "tissue" to "टिशू",
        "paper" to "पेपर",
        "plastic" to "प्लास्टिक",
        "pen" to "पेन",
        "pencil" to "पेंसिल",
        "notebook" to "नोटबुक",
        "copy" to "कॉपी",
        "book" to "बुक",
        "mobile" to "मोबाइल",
        "charger" to "चार्जर",
        "cable" to "केबल",
        "battery" to "बैटरी",
        "bulb" to "बल्ब",
        "light" to "लाइट",
        "fan" to "फैन",
        "tape" to "टेप",
        "glue" to "ग्लू",
        "cup" to "कप",
        "plate" to "प्लेट",
        "spoon" to "स्पून",
        "glass" to "ग्लास",
        "bowl" to "बाउल",
        "knife" to "नाइफ",
        "rope" to "रोप",
        "mustard" to "मस्टर्ड",
        "sunflower" to "सनफ्लावर",
        "coconut" to "कोकोनट",
        "groundnut" to "ग्राउंडनट",
        "peanut" to "पीनट",
        "soya" to "सोया",
        "chana" to "चना",
        "rajma" to "राजमा",
        "moong" to "मूंग",
        "urad" to "उड़द",
        "toor" to "तूर",
        "masoor" to "मसूर",
        "besan" to "बेसन",
        "maida" to "मैदा",
        "suji" to "सूजी",
        "poha" to "पोहा",
        "sabudana" to "साबूदाना",
        "jaggery" to "जैगरी",
        "gur" to "गुड़",
        "kaju" to "काजू",
        "badam" to "बादाम",
        "kishmish" to "किशमिश",
        "elaichi" to "इलायची",
        "laung" to "लौंग",
        "dalchini" to "दालचीनी",
        "hing" to "हींग",
        "ajwain" to "अजवाइन",
        "methi" to "मेथी",
        "saunf" to "सौंफ",
        "rai" to "राई",
        "tej" to "तेज",
        "patta" to "पत्ता",
        "namak" to "नमक",
        "cheeni" to "चीनी",
        "chini" to "चीनी",
        "sabzi" to "सब्ज़ी",
        "doodh" to "दूध",
        "makhan" to "मक्खन",
        "roti" to "रोटी",
        "supplier" to "सप्लायर",
        "traders" to "ट्रेडर्स",
        "trader" to "ट्रेडर",
        "trading" to "ट्रेडिंग",
        "store" to "स्टोर",
        "stores" to "स्टोर्स",
        "enterprises" to "एंटरप्राइज़ेज़",
        "enterprise" to "एंटरप्राइज़",
        "agency" to "एजेंसी",
        "agencies" to "एजेंसीज़",
        "wholesale" to "होलसेल",
        "retail" to "रिटेल",
        "mart" to "मार्ट",
        "general" to "जनरल",
        "kirana" to "किराना",
        "sons" to "संस",
        "brothers" to "ब्रदर्स",
        "bros" to "ब्रदर्स",
        "company" to "कंपनी",
        "ltd" to "लिमिटेड",
        "shri" to "श्री",
        "sri" to "श्री",
        "sharma" to "शर्मा",
        "gupta" to "गुप्ता",
        "verma" to "वर्मा",
        "singh" to "सिंह",
        "kumar" to "कुमार",
        "ram" to "राम",
        "shyam" to "श्याम",
        "mohan" to "मोहन",
        "ravi" to "रवि",
        "raj" to "राज",
        "ali" to "अली",
        "khan" to "खान",
        "patel" to "पटेल",
        "jain" to "जैन",
        "agarwal" to "अग्रवाल",
        "medical" to "मेडिकल",
        "pharma" to "फार्मा",
        "tablet" to "टैबलेट",
        "syrup" to "सिरप",
        "capsule" to "कैप्सूल",
        "strip" to "स्ट्रिप",
        "food" to "फूड",
        "foods" to "फूड्स",
        "grocery" to "ग्रोसरी",
        "dairy" to "डेयरी",
        "frozen" to "फ्रोज़न",
        "snacks" to "स्नैक्स",
        "snack" to "स्नैक",
        "beverages" to "बेवरेजेज़",
        "beverage" to "बेवरेज",
        "household" to "हाउसहोल्ड",
        "cleaning" to "क्लीनिंग",
        "personal" to "पर्सनल",
        "care" to "केयर",
        "stationery" to "स्टेशनरी",
        "electronics" to "इलेक्ट्रॉनिक्स",
        "hardware" to "हार्डवेयर",
        "vegetables" to "वेजिटेबल्स",
        "vegetable" to "वेजिटेबल",
        "fruits" to "फ्रूट्स",
        "fruit" to "फ्रूट",
        "pulses" to "पल्सेस",
        "grains" to "ग्रेन्स",
        "dry" to "ड्राई",
        "cooking" to "कुकिंग",
        "edible" to "एडिबल",
        "refined" to "रिफाइंड",
        "sunrise" to "सनराइज़",
        "brand" to "ब्रांड",
        "unit" to "यूनिट",
        "one" to "वन",
        "two" to "टू",
        "three" to "थ्री",
        "five" to "फाइव",
        "ten" to "टेन",
    )

    private val reverseWords: Map<String, String> by lazy {
        val m = LinkedHashMap<String, String>()
        for ((en, hi) in words) m.putIfAbsent(hi, en)
        m
    }

    private val consonants = listOf(
        "ksh" to "क्ष", "chh" to "छ", "tch" to "च", "sh" to "श", "ch" to "च", "th" to "थ", "ph" to "फ", "kh" to "ख",
        "gh" to "घ", "bh" to "भ", "dh" to "ध", "ck" to "क", "qu" to "क्व",
        "b" to "ब", "c" to "क", "d" to "ड", "f" to "फ", "g" to "ग", "h" to "ह", "j" to "ज", "k" to "क", "l" to "ल",
        "m" to "म", "n" to "न", "p" to "प", "q" to "क", "r" to "र", "s" to "स", "t" to "ट", "v" to "व", "w" to "व",
        "x" to "क्स", "y" to "य", "z" to "ज़",
    )

    /** vowel letters → (sign after a consonant, letter on its own). A null sign means the consonant's own short "a". */
    private val vowels = listOf(
        "aa" to Pair("ा", "आ"), "ee" to Pair("ी", "ई"), "ea" to Pair("ी", "ई"), "oo" to Pair("ू", "ऊ"),
        "ai" to Pair("ै", "ऐ"), "ay" to Pair("े", "ए"), "au" to Pair("ौ", "औ"), "aw" to Pair("ौ", "औ"),
        "ou" to Pair("ाउ", "आउ"), "ow" to Pair("ाउ", "आउ"), "oy" to Pair("ॉय", "ऑय"), "oi" to Pair("ॉय", "ऑय"),
        "ie" to Pair("ी", "ई"), "ei" to Pair("े", "ए"), "oa" to Pair("ो", "ओ"), "ue" to Pair("ू", "ऊ"),
        "ui" to Pair("ू", "ऊ"),
        "a" to Pair("", "अ"), "e" to Pair("े", "ए"), "i" to Pair("ि", "इ"), "o" to Pair("ो", "ओ"), "u" to Pair("ु", "उ"),
    )

    private val longAtEnd = mapOf("a" to Pair("ा", "आ"), "i" to Pair("ी", "ई"), "u" to Pair("ू", "ऊ"))

    private sealed class Tok {
        data class C(val hi: String, val latin: String) : Tok()
        data class V(val sign: String, val full: String) : Tok()
    }

    /** Latin letters → Devanagari. Anything that is not a letter (spaces, digits, punctuation) stays as typed. */
    fun toHindi(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (!isLatin(text[i])) { out.append(text[i]); i++; continue }
            var j = i
            while (j < text.length && isLatin(text[j])) j++
            out.append(wordToHindi(text.substring(i, j)))
            i = j
        }
        return out.toString()
    }

    private fun isLatin(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    private fun wordToHindi(word: String): String {
        val w = word.lowercase()
        words[w]?.let { return it }
        if (w.length > 1 && w.endsWith("s")) words[w.dropLast(1)]?.let { return it + "्स" }
        val toks = tokens(w)
        val sb = StringBuilder()
        for ((idx, t) in toks.withIndex()) {
            val next = toks.getOrNull(idx + 1)
            val prev = toks.getOrNull(idx - 1)
            when (t) {
                is Tok.C -> {
                    if (t.latin == "n" && prev is Tok.V && next is Tok.C && (next.latin == "g" || next.latin == "k")) {
                        sb.append("ं")
                    } else {
                        sb.append(t.hi)
                        if (next is Tok.C && !(next.latin == "g" && t.latin == "n")) sb.append("्")
                    }
                }
                is Tok.V -> {
                    sb.append(if (prev is Tok.C) t.sign else t.full)
                }
            }
        }
        return sb.toString()
    }

    private fun tokens(w: String): List<Tok> {
        val toks = ArrayList<Tok>()
        var end = w.length
        // a silent final "e" after a consonant ("rice", "cake") is not spoken but lengthens the vowel before it
        val silentE = end > 2 && w.endsWith("e") && w[end - 2] !in "aeiouy" && w[end - 3] in "aeiou"
        if (silentE) end -= 1
        var i = 0
        while (i < end) {
            val rest = w.substring(i, end)
            val v = vowels.firstOrNull { rest.startsWith(it.first) }
            if (v != null) {
                var pair = v.second
                val atEnd = i + v.first.length == end && !silentE
                if (atEnd && longAtEnd.containsKey(v.first) && toks.isNotEmpty()) pair = longAtEnd.getValue(v.first)
                if (atEnd && v.first == "e" && toks.size > 1) pair = Pair("", "")
                if (silentE && i + v.first.length == end - 1) {
                    pair = when (v.first) {
                        "a" -> Pair("े", "ए")
                        "i" -> Pair("ाइ", "आइ")
                        "o" -> Pair("ो", "ओ")
                        "u" -> Pair("ू", "यू")
                        else -> pair
                    }
                }
                toks.add(Tok.V(pair.first, pair.second))
                i += v.first.length
                continue
            }
            if (rest.startsWith("y")) {
                val atEnd = i + 1 == end
                if (atEnd && toks.isNotEmpty() && toks.last() is Tok.C) { toks.add(Tok.V("ी", "ई")); i++; continue }
            }
            if (rest.startsWith("c") && rest.length > 1 && rest[1] in "eiy") {
                toks.add(Tok.C("स", "s")); i++; continue
            }
            if (silentE && rest.startsWith("c") && i == end - 1) { toks.add(Tok.C("स", "s")); i++; continue }
            val c = consonants.firstOrNull { rest.startsWith(it.first) }
            if (c == null) { i++; continue }
            // a doubled letter ("tt", "ll") is one sound
            val doubled = c.first.length == 1 && rest.length > 1 && rest[1] == rest[0]
            toks.add(Tok.C(c.second, c.first))
            i += c.first.length + if (doubled) 1 else 0
        }
        if (silentE && toks.isNotEmpty()) {
            val last = toks.last()
            if (last is Tok.C && w.last() == 'e' && w[w.length - 2] == 'c') toks[toks.size - 1] = Tok.C("स", "s")
        }
        return toks
    }

    private val consonantsHi = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh",
        'ञ' to "n", 'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d",
        'ध' to "dh", 'न' to "n", 'प' to "p", 'फ' to "f", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r",
        'ल' to "l", '\u095C' to "d", '\u095D' to "dh", '\u095B' to "z", '\u095E' to "f", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l",
    )
    private val vowelSigns = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ृ' to "ri", 'ॉ' to "o", 'ॅ' to "e",
    )
    private val vowelLetters = mapOf(
        'अ' to "a", 'आ' to "a", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o",
        'औ' to "au", 'ऋ' to "ri", 'ऑ' to "o",
    )

    /** Devanagari → Latin letters, each word starting with a capital. Anything else stays as typed. */
    fun toEnglish(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (!isDevanagariLetter(text[i])) { out.append(text[i]); i++; continue }
            var j = i
            while (j < text.length && isDevanagariLetter(text[j])) j++
            out.append(wordToEnglish(text.substring(i, j)))
            i = j
        }
        return out.toString()
    }

    private fun isDevanagariLetter(c: Char) = c in 'ऀ'..'ॿ' && c != '।' && c != '॥' && c !in '०'..'९'

    private fun wordToEnglish(word: String): String {
        reverseWords[word]?.let { return it.replaceFirstChar { c -> c.uppercase() } }
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val ch = word[i]
            val hasNukta = i + 1 < word.length && word[i + 1] == '\u093C'
            val cons = if (hasNukta) when (ch) { '\u0921' -> "d"; '\u0922' -> "dh"; '\u091C' -> "z"; '\u092B' -> "f"; else -> consonantsHi[ch] } else consonantsHi[ch]
            if (cons != null) {
                sb.append(cons)
                i += if (hasNukta) 2 else 1
                val sign = word.getOrNull(i)
                when {
                    sign == '्' -> i++
                    sign != null && vowelSigns.containsKey(sign) -> { sb.append(vowelSigns.getValue(sign)); i++ }
                    i >= word.length -> {}
                    word[i] == 'ं' || word[i] == 'ँ' -> { sb.append("a") }
                    else -> sb.append("a")
                }
                continue
            }
            val v = vowelLetters[ch]
            when {
                v != null -> sb.append(v)
                ch == 'ं' || ch == 'ँ' -> sb.append("n")
                ch == 'ः' -> sb.append("h")
                ch == '़' || ch == '्' -> {}
                else -> {}
            }
            i++
        }
        return sb.toString().replaceFirstChar { it.uppercase() }
    }
}
