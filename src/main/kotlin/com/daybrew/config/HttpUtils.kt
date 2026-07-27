package com.daybrew.config

import jakarta.servlet.http.HttpServletRequest

fun resolveClientIp(request: HttpServletRequest): String {
    val directPeer = request.remoteAddr
    return if (isPrivateAddress(directPeer))
        request.getHeader("X-Real-IP")?.takeIf { it.isNotBlank() } ?: directPeer
    else directPeer
}

private fun isPrivateAddress(addr: String): Boolean =
    addr == "127.0.0.1" || addr == "::1" ||
        addr.startsWith("10.") ||
        addr.startsWith("192.168.") ||
        Regex("""^172\.(1[6-9]|2\d|3[01])\.""").containsMatchIn(addr)
