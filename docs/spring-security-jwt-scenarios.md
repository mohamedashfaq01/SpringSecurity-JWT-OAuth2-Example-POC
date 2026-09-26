# Scenario-Based Spring Security & JWT Guide

This document outlines key real-world scenarios handled by this application's security architecture.

## Scenario 1: Standard Username/Password Login
**Flow:**
1. User submits credentials to `POST /auth/login`.
2. `AuthService` delegates to `AuthenticationManager` to verify credentials.
3. Upon success, `JwtService` generates:
   - **Access Token:** Short lifespan (e.g., 10 minutes), containing user ID, email, and roles.
   - **Refresh Token:** Long lifespan (e.g., 6 months).
4. `SessionService` saves the refresh token in the DB to track the active session.
5. `AuthController` returns the Access Token in the JSON body and sets the Refresh Token as an `HttpOnly`, `Secure` cookie.

## Scenario 2: OAuth2 (Social) Login
**Flow:**
1. User navigates to `/oauth2/authorization/<provider>`.
2. After authenticating with the provider, Spring Security triggers `OAuth2SuccessHandler`.
3. The handler extracts user details (like email) from the OAuth2 response.
4. If the user doesn't exist in the database, they are registered automatically.
5. The handler generates both tokens, sets the Refresh Token cookie, and redirects the user to the frontend (`home.html?token=<accessToken>`).

## Scenario 3: Accessing Protected Resources
**Flow:**
1. Client requests a protected endpoint (e.g., `POST /posts`) and includes the Access Token in the `Authorization: Bearer <token>` header.
2. `JwtAuthFilter` intercepts the request.
3. The filter extracts the token, verifies the signature using the secret key, and extracts the `userId`.
4. It fetches the `User` from the database and sets an `Authentication` object in the `SecurityContext`.
5. Spring Security checks if the user has the required roles (e.g., `ADMIN` or `CREATOR`). If yes, the request proceeds.

## Scenario 4: Access Token Expiry and Refresh Flow
**Flow:**
1. The Access Token expires after 10 minutes. Subsequent requests return a `401 Unauthorized` or `403 Forbidden`.
2. The client intercepts the 401 error and makes a request to `POST /auth/refresh`.
3. The browser automatically includes the `HttpOnly` cookie containing the Refresh Token.
4. `AuthController` reads the cookie.
5. `AuthService` extracts the `userId` from the token and calls `SessionService.validateSession(refreshToken)` to ensure the token hasn't been revoked or replaced.
6. A new Access Token is generated and returned to the client.

## Scenario 5: Concurrent Session Control (Device Limits)
**Scenario:** A user logs in on their Phone, Laptop, and then tries to log in on a Tablet.
**Implementation:**
1. The system has a `SESSION_LIMIT` of 2.
2. During login, `SessionService.generateNewSession` is called.
3. It fetches all active sessions for the user from the database.
4. If the count equals the limit (2), it sorts the sessions by `lastUsedAt`.
5. It deletes the least recently used session (e.g., the Phone) and saves the new session (the Tablet).
6. Result: The user's phone will be logged out upon its next refresh attempt because its refresh token is no longer in the DB.

## Scenario 6: Role-Based Access Control (RBAC)
**Scenario:** Differentiating read vs. write privileges.
**Implementation:**
- Configured in `WebSecurityConfig` via `SecurityFilterChain`.
- `requestMatchers(HttpMethod.GET, "/posts/**").permitAll()`: Anyone (even unauthenticated users) can view posts.
- `requestMatchers(HttpMethod.POST, "/posts/**").hasAnyRole(ADMIN.name(), CREATOR.name())`: Only authenticated users who possess the `ADMIN` or `CREATOR` role can create new posts. Normal `USER` roles will receive a `403 Forbidden`.
