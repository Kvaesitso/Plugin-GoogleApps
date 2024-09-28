# Google Apps integration for Kvaesitso

This plugin integrates the following Google services into Kvaesitso:

- Google Drive
- Google Calendar
- Google Tasks

## Limitations

- Unfortunately, the Google Tasks API
  only [exposes the date information of due times, not the precise times](https://developers.google.com/tasks/reference/rest/v1/tasks).
  Because of that, tasks will only appear with a due date, but not a due time in Kvaesitso.

