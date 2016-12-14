# To enable ProGuard in your project, edit project.properties
# to define the proguard.config property as described in that file.
#
# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the ProGuard
# include property in project.properties.
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

-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-keepattributes !LocalVariableTable, !LocalVariableTypeTable, **


-dontwarn android.support.**
-dontwarn com.squareup.**
-dontwarn android.util.Singleton
-dontwarn android.os.SystemProperties
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn net.soureceforge.pinyin4j.**
-dontwarn demo.**
-dontwarn org.greenrobot.**
-dontwarn android.**
-dontwarn com.google.**
-dontwarn org.slf4j.**
-dontwarn com.polestar.multiaccount.utils.**

-keepnames class * implements java.io.Serializable

-keep class android.support.v4.** { *; }
-keep public class * extends android.support.v4.**
-keep public class * extends android.app.Fragment
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep interface android.support.v4.app.** { *; }
-keep class android.support.v4.** { *; }
-keep class * extends android.support.v4.** { *; }
-keep public class org.apache.** { *; }
-keep public class android.app.**{ *; }
-keep class com.squareup.** { *; }
-keep public class com.android.vending.licensing.ILicensingService
-keep class android.util.Singleton
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

-keepclassmembers class * implements java.io.Serializable {
	static final long serialVersionUID;
	private static final java.io.ObjectStreamField[] serialPersistentFields;
	private void writeObject(java.io.ObjectOutputStream);
	private void readObject(java.io.ObjectInputStream);
	!static !transient <fields>;
	java.lang.Object writeReplace();
	java.lang.Object readResolve();
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclasseswithmembers class * {
	public <init>(android.content.Context);
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}



-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** e(...);
    public static *** i(...);
    public static *** w(...);
}

-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

-keepclassmembers class **.R$* {
  public static <fields>;
}

-keepclasseswithmembers public class * {
    public static void main(java.lang.String[]);
}

-keep class com.tencent.stat.**  {* ;}
-keep class com.tencent.mid.**  {* ;}
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}