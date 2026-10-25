package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet

/** What a place starts with before the user changes anything. */
data class CalcDefaults(val method: CalcMethod, val asr: AsrRule)

/**
 * Sensible starting points by country, from the method each country's authorities or most of its
 * mosques follow. They are only a starting point: the settings let the user pick any method.
 * Countries are ISO 3166-1 alpha-2 codes, as in the city list.
 */
object MethodDefaults {
    private val hanafiCountries = setOf("PK", "IN", "BD", "AF")

    private val methods: Map<String, CalcMethod> = buildMap {
        put("SA", CalcMethod.UMM_AL_QURA)
        put("AE", CalcMethod.DUBAI)
        put("KW", CalcMethod.KUWAIT)
        put("QA", CalcMethod.QATAR)
        put("TR", CalcMethod.TURKEY)
        put("EG", CalcMethod.EGYPTIAN)
        for (code in listOf("US", "CA")) put(code, CalcMethod.NORTH_AMERICA)
        for (code in hanafiCountries) put(code, CalcMethod.KARACHI)
        // MUIS, JAKIM (Malaysia) and Kemenag (Indonesia) all use Fajr 20 and Isha 18 degrees.
        for (code in listOf("SG", "MY", "ID", "BN")) put(code, CalcMethod.SINGAPORE)
    }

    fun forCountry(country: String): CalcDefaults {
        val code = country.uppercase()
        return CalcDefaults(
            methods[code] ?: CalcMethod.MUSLIM_WORLD_LEAGUE,
            if (code in hanafiCountries) AsrRule.HANAFI else AsrRule.STANDARD,
        )
    }

    /** The Malay names are what Singapore and Malaysia use; elsewhere the English/Arabic ones read better. */
    fun namesForCountry(country: String): NameSet =
        if (country.uppercase() in setOf("SG", "MY")) NameSet.MALAY else NameSet.ENGLISH
}
