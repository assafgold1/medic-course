# App Review notes — מסלול חובש

Paste the English section below into **App Store Connect → App Review Information → Notes**.
Fill in every `[[...]]` first. The items in "Before you resubmit" are things only you can do.

## Before you resubmit (checklist)

- [ ] **Demo accounts.** Create two accounts on the production server (`https://uh.taninoni.com`) and put the logins below and in the "Sign-in required" fields:
  - a **student** account: sign up in the app.
  - an **instructor** account: sign up in the app, then add its email to `MEDIC_INSTRUCTORS` on the server and restart the service.
- [ ] **Screen recording on a real iPhone** running the latest iOS. Start with the app closed and record in this order:
  1. Launch the app.
  2. Sign up with a new account.
  3. Home: the streak card, then open a topic (text and images).
  4. Run a short practice quiz with an explanation, then the results screen.
  5. The streak celebration (meet the daily goal), and the streak page with the reminder switch (allow notifications).
  6. The defibrillator simulator and the CPR metronome.
  7. Sign out and sign in again.
  8. Settings → חשבון וסנכרון → מחיקת חשבון: delete the account, and show that signing in with it now fails.

  Attach the video link (iCloud/Drive/YouTube unlisted) in the notes.
- [ ] **Item 6 (third-party material).** The content is based on the medic course presentations. Confirm you have permission to use that material (and the images), and say so in item 6. If you have a letter or written permission, attach or describe it. If you don't have permission, don't submit until this is sorted out: Apple may reject under 5.2 (intellectual property).
- [ ] **Old review note.** `STORE_LISTING.md` still says an anonymous account is created automatically. That is no longer true (sign-in is required), so don't reuse it.
- [ ] Replace `DOMAIN` in the privacy/support URLs in `STORE_LISTING.md` if that hasn't been done.

---

## Notes (English, for App Review)

**1. Screen recording (physical device, latest iOS):** [[link to video]]

**2. Purpose and audience**
מסלול חובש ("Medic Track") is a free study app for students in Israeli emergency medical technician (EMT / "חובש") courses, and for their instructors. It turns the course syllabus into structured study material and practice: about 50 topics in five chapters, about 800 multiple-choice questions with an explanation for every answer, 270+ illustrative images, flashcards, a "numbers to memorise" table, a timed final exam with a pass mark, practice of the student's own mistakes, a daily study streak with optional reminders, a CPR metronome, and an AED (defibrillator) training simulator. It helps students review the material and practise until they pass the course exam. Instructors can follow their students' progress. The app is for study and practice only. It does not replace the course, its instructors or official medical protocols, and it gives no medical advice or diagnosis. The interface and content are in Hebrew.

**3. How to use it, and demo logins**
Sign-in is required, because instructors follow each student's progress.

- Student demo account: email `[[student email]]`, password `[[password]]`
- Instructor demo account: email `[[instructor email]]`, password `[[password]]`

New users can create an account on the sign-in screen (the "הרשמה" (sign up) tab: name, email, password).

Main features after signing in:
- **Home (נושאים tab):** the streak card, mastery, "smart practice" (תרגול חכם) and the final exam (מבחן מסכם). Tap a topic to read it and take its quiz.
- **Practice (תרגול tab):** choose categories and start a quiz.
- **Defibrillator (דפיברילטור tab):** press the green button and follow the voice and on-screen prompts.
- **Numbers (מספרים tab):** reference tables.
- **Settings (הגדרות tab):** the account, the streak and daily reminder, the theme and text size. Instructors also see "התקדמות החניכים" (student progress).
- **Account deletion:** Settings → חשבון וסנכרון (Account and sync) → מחיקת חשבון (Delete account). This immediately deletes the account and all its data from our server. It's also described at [[privacy URL]]#delete.

The app has no user-generated content shared with other users, no in-app purchases, no subscriptions and no ads.

**4. External services**
- Our own backend server (`uh.taninoni.com`, self-hosted, HTTPS) for accounts, syncing study progress, and content updates. No third-party authentication, analytics, advertising, payment or AI services are used.
- Apple on-device text-to-speech (AVSpeechSynthesizer) for the simulator's spoken prompts, and local notifications (UNUserNotificationCenter) for the optional daily study reminder. Both run on the device.

**5. Regional differences**
None. The app works the same in all regions. It's in Hebrew and is aimed at Israeli EMT course students.

**6. Regulated industry / third-party material**
The app is an educational study aid. It is not a medical device, and it gives no diagnosis, treatment or medical services. [[Statement about the course material, e.g. "The study content is based on the EMT course presentations and is used with the permission of …" plus any documentation.]]
