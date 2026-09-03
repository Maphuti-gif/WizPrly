# Implementation Plan - Create and Integrate App Logo

I will design a new, modern vector logo for **WizPrly** that combines "Wizardry" and "Whisper" (Chat) concepts. Then, I will integrate this logo as the app icon and splash screen icon.

## User Review Required

> [!IMPORTANT]
> I am proposing a new **Vector Drawable** logo. If you prefer me to use the existing `Logo.png` file instead, please let me know. Using a Vector Drawable is recommended for Android as it stays crisp at any size.

## Proposed Changes

### Assets & Resources

#### [MODIFY] [ic_launcher_foreground.xml](file:///C:/Users/MaphutiTeffo/AndroidStudioProjects/New folder/WIZPERLY/app/src/main/res/drawable/ic_launcher_foreground.xml)
- Replace current design with a more refined "Wizard Hat + Chat Bubble" concept.
- Colors: Neon Purple (#A855F7), Indigo (#6366F1), and Teal (#10B981).

#### [MODIFY] [ic_splash_logo.xml](file:///C:/Users/MaphutiTeffo/AndroidStudioProjects/New folder/WIZPERLY/app/src/main/res/drawable/ic_splash_logo.xml)
- Sync with the new launcher foreground design.

#### [NEW] [ic_logo_full.xml](file:///C:/Users/MaphutiTeffo/AndroidStudioProjects/New folder/WIZPERLY/app/src/main/res/drawable/ic_logo_full.xml)
- A full version of the logo including the "WizPrly" text in a matching font style (using vector paths).

### UI Integration

#### [MODIFY] [ChatListScreen.kt](file:///C:/Users/MaphutiTeffo/AndroidStudioProjects/New folder/WIZPERLY/app/src/main/java/com/maphutimoviousteffo/wizprly/ui/screens/ChatListScreen.kt)
- Ensure the logo is used correctly in the header.

## Verification Plan

### Automated Tests
- I will run a Gradle build to ensure the new vector drawables are valid and don't cause build errors.

### Manual Verification
- Use `render_compose_preview` (if available for these screens) to see the new logo in context.
- The user can run the app to see the new splash screen and launcher icon.
