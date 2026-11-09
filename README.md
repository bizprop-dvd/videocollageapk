# Video Collage (Android)

Combine 2–4 videos into one collage video, on the phone, no internet needed.

**Layouts:** 1 top + 2 bottom · 2 top + 1 bottom · top/bottom · left/right · 4-grid
**Shape:** 9:16 (Reels/TikTok/Story), 1:1, 16:9
**Sound:** from video 1 only, mix all, or none
**Length:** the longest video sets the length; shorter ones loop.
Output: H.264 MP4, saved to Gallery › Movies/VideoCollage. Requires Android 10+.

## Get the APK — option A: GitHub (no Android Studio needed)
1. Create a new GitHub repo and upload this whole folder (including the hidden `.github` folder).
2. Open the repo's **Actions** tab — the "Build APK" run starts automatically (~5 min).
3. Open the finished run → **Artifacts** → download `VideoCollage-apk` (a zip containing `app-debug.apk`).
4. Send the APK to your phone, open it, allow "Install unknown apps".

## Option B: Android Studio
Open this folder → wait for Gradle sync → **Build › Build APK(s)**.
The file lands in `app/build/outputs/apk/debug/app-debug.apk`.

## How it works
`CollageExporter.kt` uses Google's Media3 Transformer: each clip is cropped to fill its box,
then all boxes are composited with the phone's hardware encoder. Layout definitions are in
`Model.kt` (boxes as fractions of the canvas) — add a new layout by adding one enum entry.

The debug APK is fine for personal/team use. For Play Store you'd add a release signing key.
