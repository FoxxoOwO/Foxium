# GeckoView ships consumer rules. Keep the WebExtension bridge entry points that
# are resolved reflectively by the Gecko runtime.
-keep class com.chrogecko.browser.core.messaging.** { *; }
-keep class com.chrogecko.browser.feature.cast.** { *; }

# Cast SDK
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.**

# Ktor debugger detector references desktop JVM management APIs absent on Android.
-dontwarn java.lang.management.**
