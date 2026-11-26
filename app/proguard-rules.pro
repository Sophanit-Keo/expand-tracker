# Room, Compose, Navigation and DataStore ship their own consumer rules.

# PDFBox (PDF statement import):
# - JPEG2000 images need an optional decoder that isn't bundled; statements don't use them.
-dontwarn com.gemalto.jp2.**
# - Encryption handlers and font loaders are created by reflection, so keep the library intact
#   (password-protected statements would otherwise fail in release builds).
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
