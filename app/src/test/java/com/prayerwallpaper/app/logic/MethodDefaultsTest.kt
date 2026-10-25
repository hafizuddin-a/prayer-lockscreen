package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import org.junit.Assert.assertEquals
import org.junit.Test

class MethodDefaultsTest {
    private fun defaults(country: String) = MethodDefaults.forCountry(country)

    @Test fun `Gulf countries use their own methods`() {
        assertEquals(CalcMethod.UMM_AL_QURA, defaults("SA").method)
        assertEquals(CalcMethod.DUBAI, defaults("AE").method)
        assertEquals(CalcMethod.KUWAIT, defaults("KW").method)
        assertEquals(CalcMethod.QATAR, defaults("QA").method)
    }

    @Test fun `North America uses ISNA`() {
        assertEquals(CalcMethod.NORTH_AMERICA, defaults("US").method)
        assertEquals(CalcMethod.NORTH_AMERICA, defaults("CA").method)
    }

    @Test fun `South Asia uses Karachi with the Hanafi Asr`() {
        for (code in listOf("PK", "IN", "BD", "AF")) {
            assertEquals(code, CalcDefaults(CalcMethod.KARACHI, AsrRule.HANAFI), defaults(code))
        }
    }

    @Test fun `Southeast Asia uses the 20 and 18 degree method with the standard Asr`() {
        for (code in listOf("SG", "MY", "ID", "BN")) {
            assertEquals(code, CalcDefaults(CalcMethod.SINGAPORE, AsrRule.STANDARD), defaults(code))
        }
    }

    @Test fun `Egypt and Turkey have their own`() {
        assertEquals(CalcMethod.EGYPTIAN, defaults("EG").method)
        assertEquals(CalcMethod.TURKEY, defaults("TR").method)
    }

    @Test fun `anywhere else starts with the Muslim World League and the standard Asr`() {
        for (code in listOf("GB", "FR", "DE", "AU", "NZ", "NG", "ZA", "BR", "JP", "IR", "ZZ", "")) {
            assertEquals(code, CalcDefaults(CalcMethod.MUSLIM_WORLD_LEAGUE, AsrRule.STANDARD), defaults(code))
        }
    }

    @Test fun `country codes are not case sensitive`() {
        assertEquals(defaults("SA"), defaults("sa"))
    }

    @Test fun `Malay names for Singapore and Malaysia, English and Arabic elsewhere`() {
        assertEquals(NameSet.MALAY, MethodDefaults.namesForCountry("SG"))
        assertEquals(NameSet.MALAY, MethodDefaults.namesForCountry("my"))
        for (code in listOf("ID", "SA", "GB", "US", "PK", "")) {
            assertEquals(code, NameSet.ENGLISH, MethodDefaults.namesForCountry(code))
        }
    }
}
