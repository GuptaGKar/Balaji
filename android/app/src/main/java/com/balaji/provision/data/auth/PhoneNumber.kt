package com.balaji.provision.data.auth

object PhoneNumber {
    fun normalizeIndian(raw: String): String? {
        val digits = raw.filter(Char::isDigit)
        val national = when {
            digits.length == 10 -> digits
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            else -> return null
        }
        if (national.length != 10 || national.first() !in '6'..'9') return null
        return "+91$national"
    }
}
