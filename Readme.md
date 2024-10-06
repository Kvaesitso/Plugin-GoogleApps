# Google Apps integration for Kvaesitso

This plugin integrates the following Google services into Kvaesitso:

- Google Drive
- Google Calendar
- Google Tasks

## Limitations

- Unfortunately, the Google Tasks API
  only [exposes the date information of due times, not the precise times](https://developers.google.com/tasks/reference/rest/v1/tasks).
  Because of that, tasks will only appear with a due date, but not a due time in Kvaesitso.

## Building

To build this plugin, you need to setup a project in the Google Cloud Console.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
1. Create a new project.
1. Enable the Google Drive, Google Calendar, and Google Tasks APIs.
    1. Go to APIs & Services > Library.
    1. Search for the respective API and enable it.
1. Setup your OAuth consent screen.
    1. Go to APIs & Services > OAuth consent screen.
    1. Fill out the required fields.
    1. Add the following scopes:
        - `drive.file.metadata.readonly`
        - `calendar.readonly`
        - `tasks.readonly`
        - `userinfo.profile`
1. Create a new Oauth 2.0 client ID (you need to do this twice, for debug builds and for release
   builds)
    1. Go to APIs & Services > Credentials.
    1. Create a new OAuth 2.0 client ID.
    1. Choose "Android" as the application type.
    1. Enter the package name (`de.mm20.launcher2.plugin.google`) and the SHA-1 certificate fingerprint
    1. Click "Create".
1. Download the client config file (again, you need to do this twice, for debug builds and for
   release
   builds).

    1. Go to APIs & Services > Credentials.
    1. Click on the client ID you just created.
    1. Click on the "Download" icon next to the client config file > "Download JSON". The downloaded
       file should look like this:

       ```json
       {
         "installed": {
           "client_id": "<client_id>.apps.googleusercontent.com",
           "project_id": "<project_id>",
           "auth_uri": "https://accounts.google.com/o/oauth2/auth",
           "token_uri": "https://oauth2.googleapis.com/token",
           "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs"
         }
       }
       ```

    1. Save the file as `google_auth.json` in the `app/src/release/res/raw` (or
       `app/src/debug/res/raw`) directory.

1. You can now build the project normally in Android Studio.

## License

This plugin is licensed under the Apache License 2.0.

```
Copyright 2023 MM2-0 and the Kvaesitso contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
