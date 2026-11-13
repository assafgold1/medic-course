# מסלול חובש

לימוד, תרגול ומבחנים לקורס חובשים: 49 נושאים ו-556 שאלות, לפי מצגות הקורס.

| תיקייה | מה יש בה |
|---|---|
| `shared/` | כל האפליקציה ב-Kotlin, Compose Multiplatform (מסכים, מודל, סנכרון). משותפת לאנדרואיד ולאייפון. |
| `androidApp/` | מעטפת אנדרואיד (`com.taninoni.medic`). |
| `iosApp/` | מעטפת iOS ב-Swift שמציגה את ה-Compose (XcodeGen: `xcodegen generate`). |
| `server/` | שרת Ktor: תוכן, הרשמה/התחברות וסנכרון התקדמות (SQLite). |
| `docs/` | האתר, https://assafgold1.github.io/medic-course/ |
| `tools/export-content.js` | מייצא את התוכן מהאתר ל-`content.json` לשרת ולאפליקציות. |

## הרצה

השרת (פורט 8787, מסד נתונים ב-`server/data/medic.db`):

```bash
./gradlew :server:run
```

משתני סביבה: `PORT`, `MEDIC_DB`. נקודות קצה: `/health`, `/api/content`, `/api/auth/register|login|logout`, `/api/me`, `/api/progress`.

אנדרואיד (אמולטור או טלפון מחובר):

```bash
./gradlew :androidApp:installDebug
```

```bash
adb reverse tcp:8787 tcp:8787
```

`adb reverse` מחבר את `localhost:8787` במכשיר לשרת במחשב.

iOS: פותחים את `iosApp/iosApp.xcodeproj` ב-Xcode ומריצים (שלב ה-build בונה את ה-framework של Kotlin). הסימולטור מגיע לשרת ב-`localhost` בלי הגדרה נוספת.

כתובת השרת קבועה בקוד, בקובץ `shared/src/commonMain/kotlin/com/taninoni/medic/Platform.kt` (`SERVER_URL`), ואי אפשר לשנות אותה מתוך האפליקציה. לפני הפצה מחליפים שם לכתובת של השרת האמיתי.

האפליקציות עובדות גם בלי שרת: התוכן מובנה בהן וההתקדמות נשמרת במכשיר. כשמתחברים לחשבון, ההתקדמות והמבחנים מסתנכרנים בין המכשירים.

## עדכון תוכן

החומר הבסיסי נמצא ב-`docs/index.html`. חומר נוסף שנכתב מתוך המצגות נמצא ב-`content/extra/<נושא>.json` (פרקים ושאלות), ותמונות הלמידה ב-`content/images/` עם רשימת הכיתובים ב-`content/extra/_image-picks.json`. אחרי עריכה של כל אחד מהם:

```bash
node tools/export-content.js
```

זה מעדכן את `content.json` בשרת ובאפליקציות, מעתיק את התמונות לאפליקציות ולאתר, ויוצר את `docs/extra.js` לאתר. השרת מגיש את הגרסה החדשה, והאפליקציות מורידות אותה בפתיחה.

החומר נועד לחזרה ולתרגול, ואינו מחליף את מצגות הקורס או את הפרוטוקולים הרשמיים.
