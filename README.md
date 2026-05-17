# Donat Events

Donat Events is a NeoForge 26.1 client-side streamer mod for DonationAlerts.

Current features:

- DonationAlerts OAuth login from the mod config screen.
- Automatic access token refresh.
- Donation polling with connection status.
- Top donator overlay with draggable position and quick placement buttons.
- Top mode: today or all time.
- Compact chat notification for new donations.
- Totem-like donation animation with a gift texture.
- Test donation button for local checks.

## Requirements

- Minecraft / NeoForge `26.1`
- NeoForge `26.1.0.7-beta`
- Java `25`
- Gradle wrapper `9.1.0`

## Build

```powershell
.\gradlew.bat build
```

The mod JAR will be created in:

```text
build/libs
```

## Install

Put the built JAR into the client `mods` folder.

This mod is currently intended as a client-side streamer overlay/integration. It should not be required on a dedicated server.

## DonationAlerts Setup

1. Open DonationAlerts OAuth API applications.
2. Create or edit an application.
3. Use any clear application name, for example:

```text
Donat Events
```

4. Set redirect URL exactly:

```text
http://localhost:17845/donationalerts/callback
```

5. Save the application.
6. Copy:

```text
Client ID
Client Secret / API key
```

Do not share the client secret publicly.

## Mod Setup

1. Launch Minecraft with the mod installed.
2. Open:

```text
Mods -> Donat Events -> Config
```

3. Paste the DonationAlerts Client ID.
4. Paste the DonationAlerts Client Secret.
5. Click:

```text
Login with DA
```

6. Your browser will open DonationAlerts.
7. Approve access.
8. Return to Minecraft.
9. Click:

```text
Check token
```

If the token is valid, the mod can read DonationAlerts donations.

## Testing

Use `Test donation` in the config screen to test:

- chat message;
- gift animation;
- top donator overlay;
- top mode behavior.

This does not send a real DonationAlerts donation.

DonationAlerts widget test alerts may not appear in the donations API. For API-level testing, use DonationAlerts donation history/tools that create a real donation record.

## Overlay

The overlay supports:

- enable/disable;
- drag placement;
- quick placement buttons;
- scale;
- custom top donator text;
- custom empty-day text;
- today/all-time mode.

Text placeholders:

```text
{name}
{amount}
```

Example:

```text
Царь дня: {name} - {amount}
```

If the empty-day text is blank, the overlay is hidden when there are no donations for the selected mode.

## Local Config

Client settings are saved locally:

```text
config/donat_events-client.json
```

This file can contain OAuth tokens and secrets. Do not publish it.
