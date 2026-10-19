# HLS Finder

Android app with a built-in WebView browser that detects visible HTTP/HTTPS HLS playlist URLs ending in `.m3u8`.

## Features
- Integrated browser
- Automatic M3U8 URL detection from WebView requests
- Copy individual URLs or all detected URLs
- Open a playlist in an installed compatible player
- Deduplicates URLs

## Build on GitHub
Every push to `main` triggers GitHub Actions. You can also run it manually from the Actions tab using **Build HLS Finder APK**.

After a successful run, download the `HLS-Finder-debug` artifact. The artifact is a ZIP containing `app-debug.apk`.

## Limitations
Detection is limited to requests visible to the app's WebView. It cannot guarantee detection of links hidden by a site's implementation, DRM-protected playback, or traffic in other apps. Use only with content you are authorized to access.
