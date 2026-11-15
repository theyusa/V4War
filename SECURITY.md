# Security Policy

## Supported Versions

Security fixes are applied to the `main` branch and published in the latest release on [GitHub Releases](https://github.com/theyusa/V4War/releases).

| Version | Supported |
|---|---|
| Latest (`main` / `2.0.21+`) | :white_check_mark: |
| Older releases | :x: |

## Security Practices in V4War

- **Update Verification:** In-app updates check `v4war-update.json` for expected file sizes, SHA-256 digests, and release signing certificate pins (`UPDATE_SIGNER_SHA256`) over verified TLS.
- **Credential Sanitization:** Exported `.thrbackup` files strip sensitive Hotspot Share credentials via `HotspotBackupSanitizer`, and exported logs redact secret fields.
- **Fail-Closed Root Protection:** Root tethering mode supports fail-closed leak protection (`Block Traffic` or `Stop Hotspot`) and IPv6 leak prevention if the VPN tunnel drops unexpectedly.

## Reporting a Vulnerability

If you discover a security vulnerability in V4War:

1. **Do not** open a public issue with exploit details or sensitive traces.
2. Use GitHub's private vulnerability reporting on the repository's **Security** tab (if enabled), or reach out directly to the maintainer (`@theyusa`) with reproduction steps and affected version details.
3. Allow reasonable time for diagnosis and a fix before public disclosure.
