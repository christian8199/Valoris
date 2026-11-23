# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Moshi codegen instantiates generated <Dto>JsonAdapter(Moshi) reflectively
-keepclassmembers class * extends com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-keepnames @com.squareup.moshi.JsonClass class com.example.**
-keepclassmembers @com.squareup.moshi.JsonClass class com.example.** {
    <init>(...);
}

# Glance AppWidget & ActionCallback classes retained across R8 minification
-keep class * extends androidx.glance.appwidget.GlanceAppWidget {
    public <init>();
}
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver {
    public <init>();
}
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
-keep class * implements androidx.glance.state.GlanceStateDefinition { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# OkHttp optional dependencies
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**




