# Magic Downloader (MediaGrab)

مشروع Android أصلي مبني باستخدام Kotlin وJetpack Compose.

## بناء APK عبر GitHub Actions
1. ارفع محتويات هذا الأرشيف إلى جذر مستودع GitHub.
2. افتح تبويب Actions.
3. اختر Build APK ثم Run workflow، أو ادفع commit جديدًا إلى main.
4. بعد اكتمال البناء، افتح نتيجة التشغيل (Run) ثم Artifacts ونزّل MediaGrab-debug-apk.

## البناء محليًا
يتطلب JDK 17 وAndroid SDK 36 وGradle 8.11.1. نفّذ: `gradle assembleDebug`

ملف APK الناتج: `app/build/outputs/apk/debug/`.
