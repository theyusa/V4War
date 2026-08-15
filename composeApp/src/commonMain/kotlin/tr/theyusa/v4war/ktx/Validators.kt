package tr.theyusa.v4war.ktx

import tr.theyusa.v4war.fmt.AbstractBean
import tr.theyusa.v4war.fmt.hysteria.HysteriaBean
import tr.theyusa.v4war.fmt.shadowsocks.ShadowsocksBean
import tr.theyusa.v4war.fmt.socks.SOCKSBean
import tr.theyusa.v4war.fmt.trojan.TrojanBean
import tr.theyusa.v4war.fmt.tuic.TuicBean
import tr.theyusa.v4war.fmt.v2ray.VLESSBean
import tr.theyusa.v4war.fmt.v2ray.VMessBean
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.warn_hysteria_legacy
import tr.theyusa.v4war.resources.warn_insecure
import tr.theyusa.v4war.resources.warn_not_encrypted
import tr.theyusa.v4war.resources.warn_quic_0_rtt
import tr.theyusa.v4war.resources.warn_shadowsocks_stream_cipher
import tr.theyusa.v4war.resources.warn_vmess_md5_auth
import org.jetbrains.compose.resources.StringResource

sealed interface ValidateResult {
    object Secure : ValidateResult
    class Deprecated(val textRes: StringResource) : ValidateResult
    class Insecure(val textRes: StringResource) : ValidateResult
}

val ssSecureList = "(gcm|poly1305)".toRegex()

fun AbstractBean.isInsecure(): ValidateResult {
    if (serverAddress.isIpAddress()) {
        if (serverAddress.startsWith("127.") || serverAddress.startsWith("::")) {
            return ValidateResult.Secure
        }
    }
    when (this) {
        is ShadowsocksBean -> {
            if (plugin.isBlank() || plugin.startsWith("obfs-local;")) {
                if (!method.contains(ssSecureList)) {
                    return ValidateResult.Insecure(Res.string.warn_shadowsocks_stream_cipher)
                }
            }
        }

        is SOCKSBean -> return ValidateResult.Insecure(Res.string.warn_not_encrypted)

        is VMessBean -> {
            if (alterId > 0) return ValidateResult.Insecure(Res.string.warn_vmess_md5_auth)
            if (encryption in arrayOf("none", "zero")) {
                if (!isTLS) return ValidateResult.Insecure(Res.string.warn_not_encrypted)
            }
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        }

        is VLESSBean -> {
            if (encryption in arrayOf("", "none")) {
                if (!isTLS) return ValidateResult.Insecure(Res.string.warn_not_encrypted)
                if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
            }
        }

        is TrojanBean -> {
            if (!isTLS) return ValidateResult.Insecure(Res.string.warn_not_encrypted)
            if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        }

        is HysteriaBean -> {
            if (protocolVersion < HysteriaBean.PROTOCOL_VERSION_2) {
                return ValidateResult.Deprecated(Res.string.warn_hysteria_legacy)
            }
        }

        is TuicBean -> {
            if (zeroRTT) return ValidateResult.Insecure(Res.string.warn_quic_0_rtt)
        }
    }

    return ValidateResult.Secure
}
