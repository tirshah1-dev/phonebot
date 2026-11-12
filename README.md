# PhoneBot — کنترل و رصد امن گوشی

اپی که روی گوشی نصب می‌شود، با یک مخزن خصوصی گیت‌هاب رمزنگاری‌شده همگام می‌شود، و از لپ‌تاپت (حتی از جای دیگر دنیا، بدون نیاز به وای‌فای مشترک) باهاش کار می‌کنی.

## چطور کار می‌کند (خلاصه‌ی امنیتی)
- هیچ پورتی روی گوشی باز نمی‌شود؛ گوشی فقط به‌صورت دوره‌ای (هر ۲۰ ثانیه) با گیت‌هاب ارتباط می‌گیرد.
- همه‌ی داده‌ها قبل از ارسال با **AES-256-GCM** و رمزی که فقط خودت می‌دانی رمزنگاری می‌شوند (کلید با PBKDF2 از رمز عبور ساخته می‌شود). رمز عبور هیچ‌وقت در گیت‌هاب ذخیره نمی‌شود.
- حتی اگر توکن گیت‌هاب لو برود یا مخزن به اشتباه عمومی بماند، بدون رمز عبور چیزی قابل خواندن نیست.
- مخزن را **Private** نگه دار (Settings → Danger Zone → Change visibility در گیت‌هاب).
- از یک **Fine-grained personal access token** با دسترسی فقط به همین مخزن و فقط Contents: Read/Write استفاده کن، نه توکن کامل حساب.

## ۱. ساخت توکن گیت‌هاب
1. برو به: github.com/settings/personal-access-tokens/new
2. Resource owner: حساب خودت. Repository access: **Only select repositories → phonebot**.
3. Permissions → Repository permissions → **Contents: Read and write**.
4. Generate و توکن را یک‌جا کپی کن (دیگر نشانش نمی‌دهد).

## ۲. ساخت APK و نصب
هر پوشی که این ریپو push بشه، گیت‌هاب خودش APK را می‌سازد (Actions tab). از صفحه‌ی **Releases** آخرین نسخه را با مرورگر گوشی دانلود و نصب کن.

## ۳. راه‌اندازی روی هر گوشی (یک‌بار برای هر گوشی)
1. دکمه‌ی «فعال‌سازی دسترسی اعلان‌ها» → PhoneBot را روشن کن.
2. دکمه‌ی «فعال‌سازی Accessibility» → PhoneBot را روشن کن.
   *(این دو تاییدِ دستیِ درون‌گوشی، محدودیت خودِ اندروید است و دور زدنش بدون روت ممکن نیست — ولی فقط همین یک‌بار لازم است.)*
3. در بخش «اتصال ابری»: توکن، نام‌کاربری گیت‌هاب، نام ریپو (`phonebot`)، یک **نام یکتا برای این دستگاه** (مثلاً `gushi-man`، `tablet-sara`)، و یک **رمز عبور رمزنگاری** دلخواه بگذار (همین رمز را روی لپ‌تاپ هم استفاده می‌کنی).
4. «ذخیره» سپس «شروع اتصال» را بزن.

برای نصب روی چند گوشی، همین APK را روی هرکدام نصب کن و فقط **نام دستگاه** را برای هرکدام متفاوت بگذار؛ رمز عبور و توکن می‌توانند یکی باشند.

## ۴. نصب و تنظیم روی لپ‌تاپ
فایل `phonebot_client.py` را دانلود کن (پایتون باید نصب باشد).

```
pip install requests cryptography
python phonebot_client.py config
```

توکن، نام‌کاربری، نام ریپو و همان رمز رمزنگاری را وارد کن.

## دستورها (در cmd / ترمینال لپ‌تاپ)
```
python phonebot_client.py devices
python phonebot_client.py status gushi-man
python phonebot_client.py run gushi-man "ids"
python phonebot_client.py run gushi-man "notifs"
python phonebot_client.py run gushi-man "screen"
python phonebot_client.py run gushi-man "shot"
python phonebot_client.py run gushi-man "tap 500 800"
python phonebot_client.py run gushi-man "swipe 500 1800 500 600"
python phonebot_client.py run gushi-man "type سلام"
python phonebot_client.py run gushi-man "find ارسال"
python phonebot_client.py run gushi-man "open telegram"
python phonebot_client.py run gushi-man "sms 0912xxxxxxx متن پیام"
```
`run` دستور را می‌فرستد، گوشی در چرخه‌ی بعدی (حداکثر ~۲۰ ثانیه) آن را اجرا می‌کند، و نتیجه برمی‌گردد (تا ۶۰ ثانیه صبر می‌کند).

## نکات
- این ابزار را فقط روی گوشی‌هایی نصب کن که صاحبشان می‌داند و راضی است؛ اپ یک آیکون و نام عادی دارد و مجوزهایش در تنظیمات گوشی قابل دیدن و لغو است — شفافیت عمدی است.
- اگر گوشی سامسونگ/شیائومی و… باتری‌خور اپ را ببندد، از تنظیمات باتری، PhoneBot را «بدون محدودیت» کن تا سرویس ابری قطع نشود.
- فایل `~/.phonebot_config.json` روی لپ‌تاپ حاوی توکن و رمز توست؛ آن را جای امنی نگه دار.
- کد کامل در `app/src/main/java/com/phonebot/app/` است.
