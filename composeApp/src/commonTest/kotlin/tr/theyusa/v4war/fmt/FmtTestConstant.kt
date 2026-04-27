package tr.theyusa.v4war.fmt

object FmtTestConstant {

    const val VMESS_DUCKSOFT_URL =
        "vmess://uuid@example.com:10086?type=ws&security=tls&sni=sni.example.com&host=host.example.com&path=/path&encryption=auto#test-vmess"
    const val VLESS_GRPC_URL =
        "vless://uuid@example.com:443?type=grpc&security=tls&sni=sni.example.com&serviceName=/grpc&flow=xtls-rprx-vision#test-vless"

    const val HYSTERIA1_URL =
        "hysteria://example.com:9080?auth=secret&peer=sni.example.com&insecure=1&alpn=hysteria#test"
    const val HYSTERIA1_FAKETCP_URL = "hysteria://example.com:9080?auth=abc&protocol=faketcp"
    const val HYSTERIA2_URL =
        "hysteria2://secret@example.com:9443?sni=sni.example.com&insecure=1#test"
    const val HYSTERIA2_USER_PASS_URL = "hysteria2://user:pass@example.com:9443"

    const val SOCKS5_URL = "socks5://user:pass@example.com:1080#test-node"
    const val SOCKS4_URL = "socks4://example.com"
    const val SOCKS4A_URL = "socks4a://example.com:1234"

    const val SHADOWSOCKS_2022_URL = "ss://2022-blake3-aes-128-gcm:password@example.com:8388#test"
    const val SHADOWSOCKS_UNPADDED_BASE64_URL =
        "ss://YWVzLTI1Ni1nY206WEtGS2wyclVMaklwNzQ@127.0.0.1:8009#JP"

    const val TROJAN_URL =
        "trojan://password@example.com:443?security=tls&sni=sni.example.com&type=ws&path=/ws#test-node"
    const val TROJAN_DEFAULT_TLS_URL = "trojan://password@example.com:443"
    const val TROJAN_PEER_URL =
        "trojan://password@example.com:443?security=tls&peer=peer.example.com"

    const val TUIC_URL =
        "tuic://uuid:token@example.com:9443?sni=sni.example.com&congestion_control=bbr&udp_relay_mode=native&alpn=h3&allow_insecure=1&disable_sni=1#test-node"
    const val TUIC_DEFAULT_PORT_URL = "tuic://uuid:token@example.com"

    const val URLTEST_URL = "https://www.gstatic.com/generate_204"

}