# Screen Guide

Ek Android app jo tumhari screen dekhta hai (screenshot leta hai), Gemini AI se poochta hai aur Hinglish me step-by-step guide karta hai. Video editing, coding, settings, kisi bhi app me kaam aata hai.

## Features

- Floating **"AI" bubble** jo kisi bhi app ke upar dikhta hai (drag karke kahin bhi rakh sakte ho)
- Sawal **likhkar** ya **voice se (🎤)** pooch sakte ho
- Gemini ko screenshot + sawal jata hai, jawab panel me aata hai
- Pichli kuch baatein yaad rakhta hai, follow-up sawal chalte hain
- Customize: bubble ka size, color, opacity, panel theme (Dark/Light), text size, voice language
- API key app ke andar save hoti hai, code me nahi (repo public rakhne par bhi safe)

## Repo structure

Repo me files bilkul isi jagah honi chahiye:

| File | Repo me path |
|---|---|
| README.md | `README.md` |
| settings.gradle.kts | `settings.gradle.kts` |
| build.gradle.kts (pehla wala) | `build.gradle.kts` |
| gradle.properties | `gradle.properties` |
| build.gradle.kts (doosra wala) | `app/build.gradle.kts` |
| AndroidManifest.xml | `app/src/main/AndroidManifest.xml` |
| Prefs.kt | `app/src/main/java/com/screenguide/app/Prefs.kt` |
| GeminiClient.kt | `app/src/main/java/com/screenguide/app/GeminiClient.kt` |
| MainActivity.kt | `app/src/main/java/com/screenguide/app/MainActivity.kt` |
| ScreenService.kt | `app/src/main/java/com/screenguide/app/ScreenService.kt` |
| build.yml | `.github/workflows/build.yml` |

Do `build.gradle.kts` files alag hain: ek bahar (root) wala, ek `app/` folder wala. Root wale me sirf 4 line ka `plugins { ... }` block hai, `app` wale me `android { ... }` hai.

## GitHub par upload kaise kare

Phone par sabse aasan tareeka:

1. GitHub par naya repo banao (Public ya Private, dono chalega).
2. Repo me **Add file → Create new file** dabao.
3. File name wali box me **poora path** likho, jaise `app/src/main/java/com/screenguide/app/ScreenService.kt`. Jaise hi `/` likhoge, folder apne aap ban jata hai.
4. Neeche us file ka content paste karo aur **Commit changes** dabao.
5. Upar table ki saari 11 files ke liye yahi karo. `.github/workflows/build.yml` bhi isi tarah banao, kyunki hidden folder (`.github`) browser upload me aksar chhoot jata hai.

Computer par ho to files ko folder structure ke saath drag-and-drop bhi kar sakte ho.

## APK kaise banaye

1. Repo ke **Actions** tab me jao. Agar "workflows disabled" jaisa message aaye to enable karo.
2. Left side me **Build APK** chuno, phir **Run workflow** dabao.
3. 3-5 minute wait karo. Green tick aane par repo ke **Releases** section me jao.
4. `ScreenGuide.apk` download karo aur install karo (phone me "Install unknown apps" allow karna padega).

Har baar workflow chalane par naya release `build-1`, `build-2`... ban jata hai.

## Phone par pehli baar use

1. App kholo, **Gemini API key** daalo. Free key [aistudio.google.com](https://aistudio.google.com) se milti hai.
2. Settings (size, color, theme, language) apne hisaab se chuno.
3. **Start** dabao. Ek ek karke permissions do:
   - **Display over other apps** (bubble dikhane ke liye)
   - **Microphone** (voice sawal ke liye)
   - **Notifications**
4. Screen share dialog aayega, usme **Entire screen / Poori screen** chuno. "Single app" chunoge to baki apps screenshot me nahi dikhenge.
5. Screen par **AI** bubble aa jayega. Kisi bhi app me jao, bubble dabao.

## Use kaise kare

- **Likhkar:** box me sawal likho aur **Bhejo** dabao.
- **Voice se:** 🎤 dabao, bolo. Bolna band karoge to text aa jayega. "Voice se bolne ke baad automatically bhej do" ON hai to turant Gemini ko chala jayega, warna **Bhejo** dabao.
- **Chhupao:** panel chhupa deta hai, bubble rehta hai.
- **Clear:** purani chat bhula deta hai (naye topic ke liye use karo).
- **Band:** pura app band.
- Jab sawal bhejte ho to panel aur bubble ek pal ke liye gayab hote hain taaki screenshot saaf aaye. Yeh normal hai.

Example sawal: "Is timeline me clip ko split kaise karu?", "Yeh error kya hai aur kaise fix karu?", "Ab agla step kya hai?"

## Settings

| Setting | Matlab |
|---|---|
| Bubble size | 40 se 100 dp |
| Bubble opacity | 30 se 100 percent. Kam karoge to bubble halka dikhega |
| Jawab ka text size | 12 se 22 sp |
| Bubble color | Purple, Blue, Green, Red, Orange, Pink, Black |
| Panel theme | Dark ya Light |
| Voice language | Hindi, English (India), English (US) |
| Auto send | Voice ke baad turant bhejna |

Settings badalne ke baad **Stop** dabao, badlo, phir **Start** dabao. Start dabane par purani service apne aap band hokar nayi settings ke saath shuru hoti hai.

## Gemini ko kaise guide karwana hai (customize)

`GeminiClient.kt` me `SYSTEM_PROMPT` hai. Isme badlav karke Gemini ka andaaz badal sakte ho, jaise "sirf English me jawab do" ya "tum video editing ke expert ho, CapCut ke buttons ke naam se batao". Model badalna ho to app ke "Model name" box me naya naam likh do.

## Problems aur fix

| Problem | Fix |
|---|---|
| GitHub Actions build fail | Failed run kholkar red step ka error copy karo aur Claude ko bhejo |
| Workflow "Run workflow" button nahi dikhta | `.github/workflows/build.yml` default branch (main) par honi chahiye |
| Bubble nahi dikhta | "Display over other apps" permission check karo, phir Stop aur Start |
| Screenshot me sirf ek app dikh raha hai | Screen share me "Entire screen" chuno |
| 🎤 dabane par error | Microphone permission do. Phone me Google app / Speech Services install aur update karo. Voice ke liye internet chahiye |
| Hindi voice galat sun raha hai | Voice language badlo (Hindi / English India) |
| "Gemini error 400/403" | API key galat hai ya model naam galat hai |
| "Gemini error 429" | Free limit khatam hui, thodi der baad try karo |
| Screen rotate karne par galat screenshot | Stop aur Start karo |
| Kuch phones (Xiaomi, Oppo, Vivo) par app band ho jata hai | Phone settings me app ke liye Battery saver "No restrictions" karo aur Autostart ON karo |

## Dhyan rakhne wali baatein

- Screenshot **Google ke Gemini API** ko jata hai. Banking app, OTP, password jaisi private screen par bubble use mat karo.
- App sirf dekhta aur guide karta hai, khud tap ya click nahi karta.
- Free API key ki daily limit hoti hai.
- Yeh debug APK hai (Play Store ke liye nahi). Apne use ke liye install karna bilkul theek hai.
