# Add project specific ProGuard rules here.

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class com.ebooks.reader.data.db.** { *; }
-keep enum com.ebooks.reader.data.db.entities.** { *; }

# ── ViewModels ────────────────────────────────────────────────────────────────
-keep class com.ebooks.reader.viewmodel.** { *; }

# ── UI data / state classes ──────────────────────────────────────────────────
-keep class com.ebooks.reader.data.parser.** { *; }

# ── WebView JavaScript interface ─────────────────────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── XML Pull Parser (used by EpubParser) ─────────────────────────────────────
-keep class org.xmlpull.v1.** { *; }

# ── Coil ─────────────────────────────────────────────────────────────────────
-dontwarn coil.**
-keep class coil.** { *; }
-keepclassmembers class * extends coil.fetch.Fetcher { *; }
-keepclassmembers class * extends coil.decode.Decoder { *; }

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── sshj + BouncyCastle (SFTP shares, ADR-009) ───────────────────────────────
# sshj discovers algorithm implementations reflectively and BouncyCastle is a
# JCE provider registered at runtime; R8 cannot see either, so keep them.
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**

# ── jcifs-ng (SMB network shares, ADR-010) ───────────────────────────────────
# jcifs-ng optionally integrates with servlet containers and JGSS/Kerberos;
# neither exists on Android and the app never touches those code paths.
-dontwarn javax.servlet.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.kerberos.**
# slf4j 1.x (jcifs-ng's logging facade) looks up a StaticLoggerBinder
# reflectively; none is bundled, so it falls back to its NOP logger at runtime.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# ── Jetpack Compose ───────────────────────────────────────────────────────────
# Compose (like Room and Coil) ships consumer ProGuard rules; nothing to keep
# manually. A previous catch-all `-keepclassmembers class * { *** *(...); }`
# rule here kept every method of every class, which defeated R8 shrinking
# entirely and bloated the release APK.
-dontwarn androidx.compose.**
