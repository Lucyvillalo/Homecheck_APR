package com.casacheck.app.domain.account

import java.net.URI
import java.net.URLDecoder

class InvalidResetLinkException : IllegalArgumentException("Invalid password-reset link")

/** Accepts direct Firebase actions and current Hosting mobile-link wrappers, never follows URLs. */
fun resetCodeFromLink(value: String, projectId: String): String {
    try {
        var current = value.trim()
        repeat(3) {
            if (current.length > 8192) throw InvalidResetLinkException()
            val uri = URI(current)
            if (uri.scheme?.lowercase() != "https" || uri.userInfo != null || uri.port !in listOf(-1, 443) ||
                uri.host?.lowercase() !in setOf("$projectId.firebaseapp.com", "$projectId.web.app")) throw InvalidResetLinkException()
            val params = (uri.rawQuery ?: "").split('&').filter { it.isNotBlank() }.map {
                val pair = it.split('=', limit = 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            }.groupBy({ it.first }, { it.second })
            if (uri.path == "/__/auth/action" || uri.path == "/__/auth/links") {
                if (params["mode"]?.singleOrNull() == "resetPassword") {
                    val code = params["oobCode"]?.singleOrNull() ?: throw InvalidResetLinkException()
                    if (code.length !in 8..2048 || code.any { it.isWhitespace() || it.isISOControl() }) throw InvalidResetLinkException()
                    return code
                }
                if (uri.path == "/__/auth/links") {
                    val nested = listOfNotNull(params["link"]?.singleOrNull(), params["deep_link_id"]?.singleOrNull()).singleOrNull()
                        ?: throw InvalidResetLinkException()
                    current = nested
                } else throw InvalidResetLinkException()
            } else throw InvalidResetLinkException()
        }
    } catch (error: Exception) { throw InvalidResetLinkException() }
    throw InvalidResetLinkException()
}
