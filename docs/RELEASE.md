# Building the production Android app

The release APK is signed with **your** key and has your server address built in. The key and its passwords are
never stored in the source code.

## 1. Create the signing key (once)

```powershell
cd C:\Users\<you>\Projects\stock-list-manager\android
& "$env:JAVA_HOME\bin\keytool" -genkeypair -v -keystore ..\..\stocklist-release.jks -alias stocklist `
    -keyalg RSA -keysize 4096 -validity 10000
```
Keep `stocklist-release.jks` and its passwords **outside the project folder and backed up** (for example in a
password manager plus an offline copy). If it is lost, installed phones can never receive an update of the app;
users would have to uninstall and reinstall (their data comes back from the server, unsent changes are lost).

## 2. Tell the build where the key and server are

Create `android/keystore.properties` (it is in `.gitignore`; never commit it):

```properties
storeFile=../../stocklist-release.jks
storePassword=<keystore password>
keyAlias=stocklist
keyPassword=<key password>
api.baseUrl=https://api.yourshop.in/
```
The build refuses to make a release APK without a signing key or with a non-HTTPS server address.

## 3. Build

```powershell
cd android
$env:JAVA_HOME="$HOME\.jdks\jbr-21.0.11"
.\gradlew.bat clean assembleRelease
```
Output: `android\app\build\outputs\apk\release\app-release.apk` (minified with R8, resources shrunk, not
debuggable, cleartext HTTP disabled, no network logging). For Google Play build an App Bundle instead:
`.\gradlew.bat bundleRelease` → `app\build\outputs\bundle\release\app-release.aab`.

Check the signature:
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

## 4. Install

- On a phone: copy the APK and open it (allow "install unknown apps" for the file manager), or
  `adb install -r app-release.apk`.
- A release APK cannot be installed over a debug build (different signature): uninstall the debug app first.

## 5. Next versions

Increase `versionCode` (and `versionName`) in `android/app/build.gradle.kts` for every release, sign with the same
key, and install over the old version: the encrypted local database is upgraded automatically (Room migrations are
tested in `MigrationTest`) and unsent changes are kept.
