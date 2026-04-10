# Audio Course Learner 🎧🗣️

Turn passive language audio courses into **interactive speaking practice**. 

This Android application listens to your audio/video files, pauses at designated timestamps, and uses Speech-to-Text (STT) to verify that you spoke the correct expected phrase before continuing.

## ⚠️ Current Project Status: Seeking Contributors

**The app itself is fully functional, but the project is currently paused.** 

**The Bottleneck:** The app requires paired `.txt` transcript files to know when to pause and what phrase to listen for. Generating these transcripts automatically using AI (like OpenAI's Whisper) currently yields ~40-80% accuracy. The remaining requires manual cleanup in the app's built-in editor. Until the automated pipeline is perfected, creating courses requires too much manual effort to justify its active use.

Here's a link to the data pipeline project: https://github.com/Severus0/Audio_Course_Learner_Data_Pipeline 

If you are a developer interested in **Audio Processing**, **On-Device STT**, or **AI Data Pipelines**, then I welcome you to contact me to potentially solve this problem, honestly, I would consider reactivating this when the annotation and txt labelling is at least 95% accurate, where the vast majority of the expected phrases are correctly assigned, and it's just a few odd ones to fix before publishing.

If you manage to solve the automatic transcription bottleneck, please open a PR or let me know. You can also join my discord for language apps I develop: https://discord.com/invite/t2zyfCq6KH

---

## ✨ Features

* **Hands-Free Learning:** The app pauses automatically, listens for your speech, grades it using Fuzzy String Matching, and resumes automatically if you are correct.
* **Zip File Import/Export:** Easily share courses. A single `.zip` containing `.mp3`/`.mp4` files and optionally also their paired `.txt` annotations is all you need.
* **Built-in Editor UI:** Tap the screen at any time to add a timestamp, or use the "Raw File Editor" to fix typos.
* **Auto-Pause Detection:** Includes an experimental tool that analyzes audio waveforms to detect silences and pre-fill timestamps.
* **Clean Architecture:** Built with modern Android standards: Jetpack Compose, ExoPlayer (Media3), and decoupled logic managers.

---

## 📝 Data Schema: The Annotation Contract

For the app to correctly pause the audio and listen for speech, the transcript **must** follow a strict `MM:SS Phrase` format and share the exact same filename as the audio file (e.g., `Lesson_1.mp3` and `Lesson_1.txt`).

**Format Rules:**

- Each interaction must be on a new line.
- The timestamp must be exact (Minutes:Seconds).
- There must be a single space between the timestamp and the target phrase.
- Blank lines are ignored.

**Example `Lesson_1.txt`:**

```text
00:15 Hello
00:42 I would like a coffee.
01:05 Where is the train station?
```

---

## 🚀 Installation

You don't need to compile the app yourself to use it! 
Every time a new version is tagged, **GitHub Actions automatically builds the APK**.

1. Go to the [Releases](../../releases) page.
2. Download the latest `app-debug.apk`.
3. Install it on your Android device.

---

## 🛠️ For Developers: Code Architecture

* **UI:** 100% Jetpack Compose. Screens and components are split logically in `ui/screens/` and `ui/components/`.
* **ViewModel:** `PlayerViewModel.kt` acts as the state holder, delegating heavy lifting to dedicated logic managers.
* **Logic Managers:**
  * `SpeechManager.kt`: Wraps Android's `SpeechRecognizer`. *(Want to integrate Google Cloud Speech or Whisper on-device? This is the only file you need to change)*
  * `AnnotationManager.kt`: Handles parsing and saving the `MM:SS Phrase` text files.
  * `FeedbackAudioManager.kt`: Handles the SoundPool success/error chimes.
* **Video/Audio:** Powered by AndroidX Media3 `ExoPlayer`.

### How to Contribute

Here are a few areas that need improvement:

1. **The Data Pipeline:** Work on the python pipeline available under https://github.com/Severus0/Audio_Course_Learner_Data_Pipeline to increase its accuracy in determining correct timestamps and expected phrases.
2. **Offline STT:** Android's default `SpeechRecognizer` requires an internet connection for many languages. Integrating an offline library (like Vosk) would be a +.
3. **Better Silence Detection:** Improve `SilenceDetector.kt` to catch pauses more accurately - although this may be deprecated if step 1 above works much better.

---

## 📄 License

Licensed under the **GNU General Public License v3.0 or later (GPLv3+)**.  
See the [COPYING](./COPYING) file for details.

**Author:** Seweryn Polec  
**Contact:** sewerynpolec@gmail.com  

---

## Legal Notice

This software is provided **“as is”**, without any express or implied warranty. In no event shall the author be held liable for any damages arising from the use of this software.

© 2026 Seweryn Polec. All rights reserved.
