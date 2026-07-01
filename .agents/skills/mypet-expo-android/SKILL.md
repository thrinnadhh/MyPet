---
name: mypet-expo-android
description: Build, debug, review, and optimize the MyPet customer and merchant/captain Android apps using Expo SDK 56, Expo Router, React Native, and TypeScript. Use for screens, navigation, API integration, Android bugs, performance, accessibility, and app validation inside apps/customer-app or apps/merchant-captain-app.
---

# MyPet Expo Android Development

Target Android first while avoiding unnecessary breakage to iOS and web.

The repository contains:

- `apps/customer-app`
- `apps/merchant-captain-app`

Both applications use Expo Router and Expo SDK 56.

## Before editing

1. Identify which application is being changed.
2. Read that application's:
   - `AGENTS.md`
   - `package.json`
   - `app.json`
   - relevant routes, components, constants and API utilities
3. Inspect existing patterns before introducing new architecture.
4. Make the smallest safe change.
5. Do not modify the other application unless shared behaviour requires it.
6. Read the exact Expo SDK 56 documentation before using unstable or version-sensitive APIs.

## Project rules

- Use TypeScript and preserve strict typing.
- Do not use `any` when the response type can be defined.
- Keep API calls outside large screen components when practical.
- Put shared API logic under `src/services`, `src/api`, or the existing equivalent.
- Preserve Expo Router route names and typed-route compatibility.
- Do not replace the existing navigation system without an explicit requirement.
- Do not install a new package when Expo or an existing dependency already solves the problem.
- Never place private keys, service-role keys or secrets in the mobile app.

## Android networking

Use:

```typescript
process.env.EXPO_PUBLIC_API_BASE_URL
```
