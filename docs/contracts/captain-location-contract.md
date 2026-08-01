# Captain Location Contract

## Client prerequisites

A captain may transition online only when:

- the authenticated role is `CAPTAIN`;
- foreground location permission is granted;
- device location services are enabled;
- a fresh coordinate is available;
- the coordinate can be submitted successfully through the API gateway.

## API writes

### Availability

`PUT /api/v1/captains/status`

```json
{
  "online": true,
  "longitude": 79.4192,
  "latitude": 13.6288
}
```

### Location update

`PUT /api/v1/captains/location`

```json
{
  "longitude": 79.4192,
  "latitude": 13.6288
}
```

## Lifecycle

- Foreground updates are used while online and the application is active.
- Background updates are enabled only for an accepted active delivery and only after background permission is granted.
- Background updates use an Android foreground service notification.
- Tracking stops on offline transition, sign-out, completed delivery, or task shutdown.
- Demo coordinates are permitted only when explicit demo mode is enabled.

## Authority boundary

Coordinates are observational data. They must never directly change order or dispatch state. The dispatch service validates assignment, delivery state and proof transitions independently.
