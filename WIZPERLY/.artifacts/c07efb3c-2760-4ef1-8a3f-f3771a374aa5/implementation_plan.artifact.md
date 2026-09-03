# Regression Testing Plan for WizPrly

This plan outlines the steps to verify that all core features of the WizPrly app are functioning correctly after the recent logo and resource updates.

## Proposed Changes

### [Component Name]

#### [MODIFY] [task.artifact.md](file:///C:/Users/MaphutiTeffo/AndroidStudioProjects/New folder/WIZPERLY/.artifacts/c07efb3c-2760-4ef1-8a3f-f3771a374aa5/task.artifact.md)
*   Create a task list to track testing progress.

## Verification Plan

### Automated Tests
*   Run existing unit tests if available.
*   Check for build errors using `gradle_build`.

### Manual Verification
1.  **Onboarding**: Verify the onboarding flow starts on a clean install and the new logo is visible.
2.  **Chat List**: Verify chats are displayed, search works, and the top bar logo is correct.
3.  **New Chat**: Verify a new chat can be created with an AI persona.
4.  **Chatting**: Verify messages can be sent and received in a chat session.
5.  **Profile**: Verify settings (Theme, Dark Mode, Name, Profile Picture) can be updated and persist.
6.  **Notifications**: Trigger a test notification (if possible) or verify the logic.
7.  **Resource Check**: Ensure no missing resource errors (R.drawable.logo vs R.drawable.ic_splash_logo).
