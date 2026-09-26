# 🎯 Spring Security & JWT: Senior Engineer Interview Guide

Welcome to the grill session. This guide is designed to simulate a technical interview with a Senior Engineer who wants to ensure you truly understand *why* you are writing the code you're writing, not just *how* to copy-paste it.

---

## 🟢 Part 1: The Basics (Warming Up)

**Q1: What is the fundamental difference between Authentication and Authorization in Spring Security?**
* **Answer:** **Authentication** answers the question *"Who are you?"* (e.g., logging in with email/password). **Authorization** answers the question *"Are you allowed to do this?"* (e.g., checking if the logged-in user has the `ADMIN` role to delete a post).

**Q2: Why do we use JWTs (JSON Web Tokens) instead of traditional Session Cookies (`JSESSIONID`)?**
* **Answer:** Traditional sessions store user state in the server's memory. This makes scaling difficult because if a user's next request goes to a different server, that server doesn't know who they are (unless you use a shared cache like Redis). JWTs are **stateless**. The token itself contains all the information needed to verify the user. The server doesn't need to remember the session, making the app highly scalable and completely decoupled.

**Q3: What are the three parts of a JWT? Can a hacker read the data inside a JWT without the secret key?**
* **Answer:** A JWT consists of a **Header**, **Payload**, and **Signature**. 
* **Yes, a hacker can read it.** The Header and Payload are simply Base64Url encoded, not encrypted. Anyone can decode it. The Secret Key is *only* used to create the Signature. The Signature ensures that the data inside the token hasn't been tampered with. Because anyone can read the payload, **never put sensitive data like passwords inside a JWT.**

---

## 🟡 Part 2: Implementation Details (Getting Technical)

**Q4: In our application, we created a `JwtAuthFilter`. Why did we extend `OncePerRequestFilter` instead of just implementing the standard servlet `Filter` interface?**
* **Answer:** In standard Java Servlets, a single HTTP request can sometimes trigger a filter multiple times (for example, during internal request forwards or async dispatches). By extending `OncePerRequestFilter` provided by Spring, we guarantee that our JWT validation logic is executed exactly **one time** per incoming HTTP request, preventing duplicate processing overhead.

**Q5: In our `WebSecurityConfig`, we configured `sessionCreationPolicy(SessionCreationPolicy.STATELESS)`. What does this actually do under the hood, and why is it critical?**
* **Answer:** By default, Spring Security creates an `HttpSession` and stores the authenticated user's `SecurityContext` in it, linking it to the browser via a `JSESSIONID` cookie. Setting it to `STATELESS` tells Spring Security: *"Do not create an HttpSession. Do not store the user in memory."* This is critical for JWT architecture because we want to force the application to re-authenticate the user on every single request using the provided Bearer token, ensuring true statelessness.

**Q6: After our `JwtAuthFilter` extracts and validates the token, how does Spring Security know who the current user is for the rest of the request?**
* **Answer:** Once we validate the token and extract the `userId`, we load the user's details. We then create a `UsernamePasswordAuthenticationToken` (passing in the user object and their roles/authorities). Finally, we set this token into the `SecurityContextHolder`. From that point on, any controller or service can call `SecurityContextHolder.getContext().getAuthentication()` to see who is logged in.

---

## 🟠 Part 3: Scenario-Based Questions (The Grill)

**Q7: Scenario: A malicious script steals a user's Access Token. What can the attacker do, and how do we architect our system to limit the damage?**
* **Answer:** The attacker can access any API the user has access to. Because JWTs are stateless, we cannot easily "revoke" an access token. To limit damage, we architect a **Two-Token System**:
  1. We give the Access Token a very short lifespan (e.g., 10 minutes). The attacker's window of opportunity is extremely small.
  2. We issue a long-lived Refresh Token (e.g., 6 months) used *only* to get new access tokens.

**Q8: Scenario: If Access Tokens are stored in local storage, they are vulnerable to XSS (Cross-Site Scripting). How do we securely handle the long-lived Refresh Token to prevent it from being stolen?**
* **Answer:** We never send the Refresh Token in the JSON response body. Instead, our `AuthController` sets it as an `HttpOnly` and `Secure` cookie.
  * `HttpOnly` means Javascript running in the browser cannot read the cookie, making it immune to XSS attacks.
  * `Secure` ensures the cookie is only sent over HTTPS.
  * When the access token expires, the frontend calls `POST /auth/refresh`, and the browser automatically includes the cookie.

**Q9: Scenario: A user suspects their account is compromised and wants to "Log out of all devices". Because Access Tokens are stateless and can't be revoked, how does our system handle this?**
* **Answer:** While access tokens are stateless, our `Session` entity in the database tracks the active **Refresh Tokens**. When the user clicks "Log out of all devices", we delete all `Session` records associated with their user ID from the database. 
Their stolen *access token* will still work for a maximum of 10 minutes, but once it expires, the attacker will be unable to get a new one because the refresh token has been invalidated in our DB.

**Q10: Scenario: We have a route `POST /posts` that should only be accessed by the `ADMIN` role. In Spring Security, what is the exact difference between configuring `hasRole("ADMIN")` vs `hasAuthority("ADMIN")`?**
* **Answer:** `hasRole("ADMIN")` automatically prepends the prefix `ROLE_` under the hood. For it to work, the authority string returned by our `User` entity must be exactly `ROLE_ADMIN`. Conversely, `hasAuthority("ADMIN")` does an exact string match. It looks for exactly `ADMIN` without any prefixes.

---

## 🔴 Part 4: Advanced Scenarios (The Deep End)

**Q11: Scenario: Like Netflix, we want to limit users to a maximum of 2 active devices. If they log in on a 3rd device, the oldest device should be logged out. How did we implement this with JWTs?**
* **Answer:** We handled this in our custom `SessionService`. Every time a user logs in (generating a new refresh token), we query the database for their existing sessions. 
If `sessions.size() == 2`, we sort them by `lastUsedAt` to find the oldest one. We delete the oldest session from the DB and insert the new one. 
**Result:** When the oldest device's short-lived access token expires, it will try to use its refresh token. The server will reject it because it's no longer in the DB, effectively logging out that device.

**Q12: Scenario: What happens if an exception is thrown inside our `JwtAuthFilter` (e.g., the JWT is expired, throwing `ExpiredJwtException`)? Will our `@ExceptionHandler` in our global `@ControllerAdvice` catch it and return a neat JSON error?**
* **Answer:** **No, it will not.** Filters sit in the application chain *before* the `DispatcherServlet`. The `@ControllerAdvice` only intercepts exceptions thrown *inside* the `DispatcherServlet` (like in Controllers or Services).
* **The Fix:** To handle this gracefully, we injected Spring's `HandlerExceptionResolver` (qualified as `"handlerExceptionResolver"`) directly into our `JwtAuthFilter`. When we catch the JWT exception in the filter, we manually pass the request, response, and exception to the resolver: `handlerExceptionResolver.resolveException(request, response, null, ex);`. This bridges the gap and allows our global exception handler to process filter-level exceptions.

---

## 🟣 Part 5: Master-Level Scenarios (The Deep Dive)

**Q13: Scenario: We have multiple microservices. Should each microservice validate the JWT by talking to a central Auth Server, or can they validate it independently?**
* **Answer:** One of the biggest advantages of JWT is that it contains the signature. Each microservice only needs the **Secret Key** (or the Public Key if using RSA/Asymmetric encryption) to independently verify the signature and read the payload. They do *not* need to make a network call to the Auth Server, which eliminates a massive bottleneck.

**Q14: What is the difference between Symmetric (HS256) and Asymmetric (RS256) Encryption in JWT? Which one is our app using?**
* **Answer:** 
  * **Symmetric (HS256):** Uses the *same* secret key to both create and verify the token. Our app uses this (`Keys.hmacShaKeyFor`). The downside is if we have 10 microservices, we have to share this exact secret key with all 10. If one gets compromised, the whole system is compromised.
  * **Asymmetric (RS256):** Uses a Private/Public key pair. The Auth Server holds the Private Key and uses it to *sign* the tokens. The other microservices only hold the Public Key, which can *only verify* tokens. This is much safer for distributed architectures.

**Q15: Scenario: A user is assigned a new role (e.g., promoted to `ADMIN`) while they are actively logged in. Because JWTs are stateless, their current token only has the `USER` role. How does the system handle this so they get their new privileges?**
* **Answer:** Since the token payload is immutable, the current Access Token won't reflect the new role until it expires. Once the short-lived access token expires (e.g., in 10 mins), the frontend will hit the `/auth/refresh` endpoint. The `AuthService` will validate the refresh token and fetch the fresh `User` details from the database. The *new* Access Token generated will now contain the `ADMIN` role. 

**Q16: In `WebSecurityConfig`, we configured OAuth2 with `.oauth2Login()`. When a user logs in via Google, how does Spring Security bridge the gap between Google's identity and our custom `User` entity and JWT system?**
* **Answer:** This is bridged via our custom `OAuth2SuccessHandler`. When Google successfully authenticates the user, Spring Security triggers this handler and passes it the `OAuth2AuthenticationToken`. 
1. We extract the email and name from Google's payload.
2. We check our database. If the user doesn't exist, we auto-register them.
3. We then bypass traditional Spring Security session logic entirely by generating our custom JWT Access and Refresh tokens, setting the cookie, and manually redirecting the user back to the frontend with the access token.

**Q17: Scenario: What is CSRF (Cross-Site Request Forgery), and why did we explicitly disable it (`csrf(csrf -> csrf.disable())`) in our configuration? Isn't that dangerous?**
* **Answer:** CSRF is an attack where a malicious site tricks the browser into sending a request to your site. This attack historically relies on the browser automatically attaching `JSESSIONID` cookies to every request. 
Because our architecture uses **Bearer Tokens** sent in the `Authorization` header (which the browser does *not* attach automatically), we are inherently immune to standard CSRF attacks for API calls. Therefore, disabling CSRF protection is safe and necessary for our REST API. (Note: Our refresh token *is* a cookie, but it's only accepted on the `/auth/refresh` endpoint, which doesn't perform state-changing actions).

**Q18: What is CORS (Cross-Origin Resource Sharing), and how does it relate to our Spring Security setup?**
* **Answer:** By default, browsers block frontend apps (running on e.g., `localhost:3000`) from making API requests to a backend on a different port/domain (e.g., `localhost:8080`). This is enforced by sending an HTTP `OPTIONS` (Preflight) request. If Spring Security blocks this preflight request, the frontend will fail. We usually need to configure a `CorsConfigurationSource` bean in Spring Security to explicitly permit our frontend's origin and allow methods like GET/POST/OPTIONS.

**Q19: Scenario: In `JwtAuthFilter`, we use `SecurityContextHolder.getContext().setAuthentication(token)`. Under the hood, Spring stores this context in a `ThreadLocal`. What does this mean, and what is the danger if we spawn a new asynchronous thread inside our controller?**
* **Answer:** A `ThreadLocal` variable is only accessible to the specific thread that created it (in this case, the HTTP worker thread processing the request). If our Controller spawns a *new* thread (e.g., using `CompletableFuture.runAsync()`), that new thread will have an **empty** `SecurityContext`. If code in that new thread tries to get the current user, it will return `null` or throw an unauthenticated error. We'd have to use `SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)` or manually pass the context to the new thread.

**Q20: Why do we use `PasswordEncoder` (specifically `BCryptPasswordEncoder`) instead of plain text or simple MD5 hashing?**
* **Answer:** 
  1. Plain text means anyone with DB access sees the passwords.
  2. MD5 is fast and predictable, making it vulnerable to "Rainbow Table" attacks (pre-computed hash lists).
  3. **BCrypt** incorporates a "Salt" (random data appended before hashing) so two users with the exact same password (e.g., "password123") will have completely different hashes in the DB. BCrypt is also intentionally computationally expensive (slow), which makes brute-force attacks practically impossible.
