# Local Google API configuration

The tracked app/google-services.json and wear/google-services.json files are keyless templates. Actual Google API keys must remain outside Git. The source history was cleaned only of the two known inherited public Google key literals.

## Firebase

Provide an app Firebase configuration as app/google-services.local.json, or set AAPS_GOOGLE_SERVICES_JSON to the path of a private complete google-services.json file. The environment path takes priority. Use a configuration for the intended Firebase project and the application IDs being built. The local file is ignored by Git. The wear module currently does not apply the Google Services Gradle plugin; its tracked JSON is retained as a keyless template.

The app Google Services Gradle task falls back to the tracked keyless template so resource generation can run without credentials. Keeping project/client metadata and current_key fields avoids missing-file and missing-field task errors. A build without an actual API key cannot authenticate to Firebase. In particular, MainApp.setupRemoteConfig initializes Firebase Remote Config; its API calls may fail, and application startup without configured credentials is not validated. Configure the private JSON before producing an APK intended for use. This change does not update installed applications.

## Maps and Places

Set AAPS_GOOGLE_MAPS_API_KEY, or set googleMapsApiKey in the ignored root local.properties file. The environment variable takes priority. Gradle generates the existing key_google_apis_android string resource consumed by the automation module manifest. An unset key produces an empty resource so compilation can proceed, but Google Maps/Places authentication will fail until a valid restricted key is supplied.

Do not put keys in tracked gradle.properties, this document, commit messages, or build logs. Keep generated build directories and local configuration files excluded from Git.
