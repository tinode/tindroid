# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ~/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# Classes which define json wire protocol.
-keep class co.tinode.tinodesdk.model.** { *; }

-keepattributes *Annotation*,EnclosingMethod,Signature
-keepattributes SourceFile,LineNumberTable

# JacksonXML.
-keepnames class com.fasterxml.jackson.** { *; }
-keepnames interface com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-keep class org.codehaus.** { *; }

-keep public class * extends java.lang.Exception

# Keep WebRTC classes as is.
-keep class org.webrtc.** {*;}
-keepclasseswithmembernames class * { native <methods>; }

# Don't mangle classes which are saved to DB.
-keep class * implements java.io.Serializable

# As suggested by Crashlytics
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**

# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
