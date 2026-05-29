# Help Me! (Emergency Safety App)

A hybrid mobile application built to provide reliable offline emergency alerts. It uses native Android APIs for SMS/Calls while leveraging React for a modern UI.

## Key Features

-   **SOS / Panic Button**: Instantly triggers emergency SMS and Calls.
-   **Offline-First**: Uses **SQLite** for storing panic history locally.
-   **Native Reliability**:
    -   `SmsManager` & `AlarmManager` for background execution.
    -   WorkManager for guaranteed task completion.
    -   Direct multi-SIM support.

## creating a build

1.  **Install Dependencies**:
    ```bash
    npm install
    ```

2.  **Sync Native Project**:
    ```bash
    npx cap sync android
    ```

3.  **Run on Device**:
    ```bash
    npx cap run android
    ```

## Architecture

-   **Frontend**: React 19 + Vite + TailwindCSS
-   **Mobile Runtime**: Capacitor 8
-   **Storage**: `@capacitor-community/sqlite` (Native Database)
-   **Icons/Splash**: Managed via `capacitor-assets`

## Permissions
This app requires `SEND_SMS`, `CALL_PHONE`, and `ACCESS_FINE_LOCATION` to function. These are critical for the safety features.
