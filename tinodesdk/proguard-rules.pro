# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ~/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# For com.fasterxml.jackson to work on versions of Android prior to L.
-keep class java.beans.Transient.** {*;}
-keep class java.beans.ConstructorProperties.** {*;}
-keep class java.nio.file.Path.** {*;}

# Classes which define json wire protocol.
-keep class co.tinode.tinodesdk.model.** {*;}

-keepattributes *Annotation*,EnclosingMethod,Signature
-keepattributes SourceFile,LineNumberTable

# JacksonXML.
-keepnames class com.fasterxml.jackson.** {*;}
-keepnames interface com.fasterxml.jackson.** {*;}
-dontwarn com.fasterxml.jackson.databind.**
-keep class org.codehaus.** {*;}

-keep public class * extends java.lang.Exception {*;}

# Keep WebRTC classes as is.
-keep class org.webrtc.** {*;}
-keepclasseswithmembernames class * { native <methods>; }

# Don't mangle serializable classes.
-keep class * implements java.io.Serializable {*;}

# Crashlytics
-keep class com.crashlytics.** {*;}
-dontwarn com.crashlytics.**