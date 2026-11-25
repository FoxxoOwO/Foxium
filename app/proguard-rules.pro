# GeckoView ships consumer rules. Keep the WebExtension bridge entry points that
# are resolved reflectively by the Gecko runtime.
-keep class com.chrogecko.browser.core.messaging.** { *; }
-keep class com.chrogecko.browser.feature.cast.** { *; }

# Cast SDK
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.**

# FIDO2 results cross Binder as Parcelables whose class names are resolved reflectively.
# Without this, R8 renames them and WebAuthn assertions never complete in release builds
# (Mozilla Bug 2093295; fixed in GeckoView 147, we are on 146).
-keepnames class com.google.android.gms.fido.** implements android.os.Parcelable

# Ktor debugger detector references desktop JVM management APIs absent on Android.
-dontwarn java.lang.management.**
