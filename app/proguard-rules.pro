# Published releases are minified release builds, so these rules are load-bearing.

# Room generates implementations reflectively referenced by the runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# The HostApduService is instantiated by the platform by name from the manifest.
-keep class org.snrkl.nfcbusinesscard.hce.** { *; }

# ZXing reflectively touches its own enums for encode hints.
-keep enum com.google.zxing.** { *; }
