# HTML Editor App

Android app to write and preview HTML/CSS/JS projects on your phone.
Built automatically into an APK using **GitHub Actions** — no Android Studio needed.

## Features

- Syntax-highlighted code editor (HTML/CSS/JS) with line numbers
- Open a whole project:
  - **Open ZIP** — pick a `.zip` containing `index.html`, `style.css`, `script.js`, images, etc.
  - **Open Files** — or pick multiple individual files at once, no zip needed
- File list drawer to switch between files, rename, delete, create new files
- Quick-insert toolbar for common HTML tags
- **Two preview modes**:
  - **Preview** button → renders inside the app (in-app WebView)
  - **Browser** button → opens the same project in your phone's default browser
- Export current project back to a `.zip` (share button in the menu)
- Works fully offline — no CDN/internet dependency for the editor itself

## How to build the APK using GitHub Actions

1. Create a new **public or private GitHub repository**.
2. Upload/push all the files from this project (keep the folder structure exactly as-is).
3. Go to your repo → **Actions** tab. GitHub will detect the workflow file at
   `.github/workflows/build-apk.yml` automatically.
4. The workflow runs automatically on every push to `main`/`master`.
   You can also trigger it manually: **Actions → Build APK → Run workflow**.
5. Once it finishes (green checkmark), open the workflow run:
   - Under **Artifacts**, download `app-debug-apk` — this is your `.apk` file.
   - It's also attached to a new entry under your repo's **Releases** tab.
6. Transfer the APK to your Android phone and install it
   (you may need to enable "Install unknown apps" for your file manager/browser).

## Pushing this project to GitHub (from your computer)

```bash
git init
git add .
git commit -m "Initial commit: HTML Editor app"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```

After the push, check the **Actions** tab — the build will start automatically.

## Project structure

```
HTMLEditorApp/
├── .github/workflows/build-apk.yml   ← GitHub Actions build workflow
├── app/
│   ├── src/main/java/com/example/htmleditor/
│   │   ├── MainActivity.kt           ← main screen logic
│   │   ├── PreviewActivity.kt        ← in-app preview screen
│   │   ├── ProjectManager.kt         ← file/zip/project handling
│   │   ├── HtmlCombiner.kt           ← relative path resolving helper
│   │   └── FileListAdapter.kt        ← file list UI (drawer)
│   ├── src/main/assets/editor/       ← the code editor itself (HTML/JS/CSS)
│   └── src/main/res/                 ← layouts, icons, colors, themes
├── build.gradle / settings.gradle / gradle.properties
└── gradlew                           ← Gradle wrapper launcher
```

## Notes

- Minimum Android version supported: **Android 7.0 (API 24)**
- The generated APK is a **debug build** — fine for personal/testing use.
  For a Play Store–ready release build, you'd additionally need to configure
  app signing (a keystore) — ask if you'd like this added.
- The editor is a lightweight custom syntax highlighter (no external library),
  so the whole app works without any internet connection.
