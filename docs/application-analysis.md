# Spring Security JWT Application Analysis

## 1. Overview
This is a modern Spring Boot application (v3.3.2) utilizing Java 22. It provides a RESTful API demonstrating comprehensive authentication and authorization flows using Spring Security, JSON Web Tokens (JWT), and OAuth2. It uses Spring Data JPA for persistence with an in-memory H2 database.

## 2. Technology Stack
- **Framework:** Spring Boot 3.3.2, Java 22
- **Database:** H2 Database via Spring Data JPA
- **Security:** Spring Security, OAuth2 Client, JJWT (io.jsonwebtoken)
- **Utilities:** Lombok, ModelMapper

## 3. Core Entities
1. **User:** Implements Spring Security's `UserDetails` interface. Contains email, password, name, and a set of `Role` enums.
2. **Session:** Tracks active refresh tokens for users. It is mapped to `User` and includes `refreshToken` and `lastUsedAt`.
3. **PostEntity:** Represents a resource that users can interact with (create/view posts).
4. **Role (Enum):** Defines the roles available in the system (e.g., `ADMIN`, `CREATOR`, `USER`).

## 4. Key Architectural Components

### A. Configuration (`config` package)
- **`WebSecurityConfig`**: The heart of the security setup. It configures the `SecurityFilterChain`, disables CSRF, sets session management to `STATELESS`, configures public vs. protected routes (RBAC), and registers the `JwtAuthFilter` and `OAuth2SuccessHandler`.

### B. Filters (`filters` package)
- **`JwtAuthFilter`**: A `OncePerRequestFilter` that intercepts incoming requests, extracts the JWT from the `Authorization: Bearer <token>` header, validates it, and sets the `UsernamePasswordAuthenticationToken` in the `SecurityContextHolder`.

### C. Services (`services` package)
- **`AuthService`**: Handles business logic for standard authentication (login) and refreshing tokens.
- **`JwtService`**: Responsible for generating short-lived Access Tokens (10 mins) and long-lived Refresh Tokens (6 months), and extracting claims (like `userId`) from tokens.
- **`UserService`**: Implements `UserDetailsService` to load users by email for authentication. Also handles user registration.
- **`SessionService`**: Implements concurrent session management by limiting active sessions (refresh tokens) per user to a maximum of 2.

### D. Controllers (`controllers` package)
- **`AuthController`**: Exposes `/auth/signup`, `/auth/login`, and `/auth/refresh` endpoints. It properly secures the refresh token by setting it in an `HttpOnly` cookie.
- **`PostController`**: Exposes resource endpoints (`/posts`) that demonstrate Role-Based Access Control (RBAC).

### E. Handlers (`handlers` package)
- **`OAuth2SuccessHandler`**: Invoked upon successful third-party OAuth2 login. It checks if the user exists (creates them if not), generates JWTs, sets the refresh token cookie, and redirects the user to the frontend.

## 5. Security Best Practices Implemented
- **Stateless Sessions:** Server doesn't store JSESSIONID; scales easily.
- **Token Segregation:** Separates short-lived access tokens from long-lived refresh tokens.
- **XSS Protection for Refresh Tokens:** Refresh tokens are sent as `HttpOnly` cookies, preventing JavaScript access.
- **Concurrent Session Control:** Limits a user to 2 active devices simultaneously.
