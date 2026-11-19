# LoopVpn (Android MVP)

An Android Studio starter app for organizing VPN profiles from providers. Import a profile URI or a local config file, review the detected protocol/server, and select a server.

## Imported formats in this MVP

- VLESS, VMess, Trojan, and Shadowsocks URI lines
- WireGuard `.conf` text containing `[Interface]` and `[Peer]`
- OpenVPN `.ovpn` text containing `client` and `remote`

This is an import/UI prototype, **not yet a working VPN client**: no tunnel engine or Android `VpnService` is included, so the Connect button does not establish a connection. Parsing a link only recognizes its format and displays basic server metadata; it does not validate credentials or guarantee every provider's subscription format will work.

Profiles are kept in memory only and are discarded when the app process ends. Do not use real credentials until encrypted persistence and a VPN engine are implemented.

## Build

Open this repository in Android Studio (with Android SDK 35 installed), sync Gradle, then run the `app` configuration on an Android 8.0+ device/emulator. This initial scaffold does not include a Gradle wrapper.

## Next steps

1. Choose and integrate maintained protocol engines for the formats to support.
2. Implement Android `VpnService`, its permission/foreground-service lifecycle, and connection state.
3. Add encrypted local storage, subscription refresh, and safe profile deletion.
4. Test imports against provider samples and add automated parser tests.
