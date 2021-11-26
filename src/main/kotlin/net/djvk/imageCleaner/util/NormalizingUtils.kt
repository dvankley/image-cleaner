package net.djvk.imageCleaner.util

object NormalizingUtils {
    fun normalizeValue(value: String): String {
        val builder = StringBuilder()
        for (char in value) {
            if (unicodePunctuation.containsKey(char.toInt())) {
                builder.append(unicodePunctuation[char.toInt()])
            } else {
                builder.append(char)
            }
        }
        return builder.toString()
    }

    // Shamelessly ripped off from https://stackoverflow.com/a/17280168 and somewhat modified
    private val unicodePunctuation = mapOf(
        0x00A0 to ' ',
        0x00AB to '"',
        0x00AD to '-',
        0x00B4 to '\'',
        0x00BB to '"',
        0x00F7 to '/',
        0x01C0 to '|',
        0x01C3 to '!',
        0x02B9 to '\'',
        0x02BA to '"',
        0x02BC to '\'',
        0x02C4 to '^',
        0x02C6 to '^',
        0x02C8 to '\'',
        0x02CB to '`',
        0x02CD to '_',
        0x02DC to '~',
        0x0300 to '`',
        0x0301 to '\'',
        0x0302 to '^',
        0x0303 to '~',
        0x030B to '"',
        0x030E to '"',
        0x0331 to '_',
        0x0332 to '_',
        0x0338 to '/',
        0x0589 to ':',
        0x05C0 to '|',
        0x05C3 to ':',
        0x066A to '%',
        0x066D to '*',
        0x200B to ' ',
        0x2010 to '-',
        0x2011 to '-',
        0x2012 to '-',
        0x2013 to '-',
        0x2014 to '-',
        0x2015 to '-',
        0x2016 to '|',
        0x2017 to '_',
        0x2018 to '\'',
        0x2019 to '\'',
        0x201A to ',',
        0x201B to '\'',
        0x201C to '"',
        0x201D to '"',
        0x201E to '"',
        0x201F to '"',
        0x2032 to '\'',
        0x2033 to '"',
        0x2034 to '\'',
        0x2035 to '`',
        0x2036 to '"',
        0x2037 to '\'',
        0x2038 to '^',
        0x2039 to '<',
        0x203A to '>',
        0x203D to '?',
        0x2044 to '/',
        0x204E to '*',
        0x2052 to '%',
        0x2053 to '~',
        0x2060 to ' ',
        0x20E5 to '\\',
        0x2212 to '-',
        0x2215 to '/',
        0x2216 to '\\',
        0x2217 to '*',
        0x2223 to '|',
        0x2236 to ':',
        0x223C to '~',
        0x2264 to '<',
        0x2265 to '>',
        0x2266 to '<',
        0x2267 to '>',
        0x2303 to '^',
        0x2329 to '<',
        0x232A to '>',
        0x266F to '#',
        0x2731 to '*',
        0x2758 to '|',
        0x2762 to '!',
        0x27E6 to '[',
        0x27E8 to '<',
        0x27E9 to '>',
        0x2983 to '{',
        0x2984 to '}',
        0x3003 to '"',
        0x3008 to '<',
        0x3009 to '>',
        0x301B to ']',
        0x301C to '~',
        0x301D to '"',
        0x301E to '"',
        0xFEFF to ' '
    )

    private val asciiToUnicodeAlternatives = unicodePunctuation.entries.groupBy { it.value }
    fun groupByAsciiForRegex(ascii: Char): String {
        return asciiToUnicodeAlternatives[ascii]!!.map { it.key.toChar() }.joinToString("") + ascii
    }
}