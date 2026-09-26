# Step-by-Step Guide: Implementing Spring Security with JWT

This guide provides a visual and step-by-step approach to implementing Authentication and Authorization in a Spring Boot application using JSON Web Tokens (JWT) and OAuth2.

---

## 1. High-Level Security Architecture

Before writing code, it's essential to understand how a request flows through the Spring Security filter chain.

```mermaid
graph TD
    Client[Client App / Browser] -->|1. HTTP Request| API[Spring Boot Application]
    
    subgraph Spring Security Filter Chain
        API --> Filter[JwtAuthFilter]
        Filter -->|2. Extract Bearer Token| TokenCheck{Token Valid?}
        TokenCheck -->|No| Reject[401 Unauthorized]
        TokenCheck -->|Yes| ExtractUser[Extract User ID & Roles]
        ExtractUser --> Context[Set SecurityContextHolder]
    end
    
    Context -->|3. Route Request| Dispatcher[DispatcherServlet]
    Dispatcher --> RouteCheck{Has Role?}
    
    RouteCheck -->|Yes| Controller[Controllers / Business Logic]
    RouteCheck -->|No| Forbidden[403 Forbidden]
```

---

## 2. The Implementation Steps

### Step 1: Add Required Dependencies
Start by adding the core Spring Security and JWT dependencies in your `pom.xml`.
* **Spring Boot Starter Security**: Core security framework.
* **Spring Boot Starter OAuth2 Client**: For social logins (Google, GitHub).
* **JJWT (io.jsonwebtoken)**: For generating and parsing JWTs.

### Step 2: Define Core Entities
You need a `User` entity that implements Spring Security's `UserDetails` interface.

```mermaid
classDiagram
    class UserDetails {
        <<interface>>
        +getAuthorities()
        +getPassword()
        +getUsername()
    }
    class User {
        +Long id
        +String email
        +String password
        +Set~Role~ roles
    }
    class Session {
        +String refreshToken
        +LocalDateTime lastUsedAt
    }
    User ..|> UserDetails : Implements
    User "1" *-- "Many" Session : Has
```

### Step 3: Create the JWT Service (`JwtService.java`)
This service is responsible for securely generating and validating tokens using a secret key (HMAC-SHA).
* **Access Token:** Short lifespan (e.g., 10-15 mins). Contains roles and user info.
* **Refresh Token:** Long lifespan (e.g., 6 months). Only used to get new Access Tokens.

### Step 4: Implement the JWT Auth Filter (`JwtAuthFilter.java`)
This is a `OncePerRequestFilter`. It intercepts every incoming HTTP request.
1. Looks for the `Authorization: Bearer <token>` header.
2. Extracts and validates the token via `JwtService`.
3. Loads the user and populates the `SecurityContextHolder`.

---

## 3. Core Authentication Flows

### A. The Standard Login Flow
When a user logs in with their email and password, the system validates the credentials and returns two tokens. For maximum security, the Refresh Token is sent as an `HttpOnly` cookie.

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant AuthController
    participant AuthService
    participant JwtService
    participant DB
    
    Client->>AuthController: POST /auth/login (email, password)
    AuthController->>AuthService: authenticate()
    AuthService->>DB: verify credentials (UserDetailsService)
    DB-->>AuthService: Valid User
    AuthService->>JwtService: generateAccessToken(user)
    AuthService->>JwtService: generateRefreshToken(user)
    JwtService-->>AuthService: Both Tokens
    AuthService->>DB: saveSession(refreshToken)
    AuthService-->>AuthController: LoginResponse
    AuthController-->>Client: AccessToken (Body) + RefreshToken (HttpOnly Cookie)
```

### B. The Token Refresh Flow
When the Access Token expires (resulting in a 401 error), the client uses the Refresh Token to get a new one without forcing the user to log in again.

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant AuthController
    participant SessionService
    participant JwtService
    
    Client->>AuthController: POST /auth/refresh (Cookie: refreshToken)
    AuthController->>SessionService: validateSession(refreshToken)
    SessionService-->>AuthController: Session is valid
    AuthController->>JwtService: getUserIdFromToken(refreshToken)
    AuthController->>JwtService: generateAccessToken(user)
    JwtService-->>AuthController: New Access Token
    AuthController-->>Client: New Access Token (JSON body)
```

---

## 4. Role-Based Access Control (RBAC) Setup

Finally, configure route protections in your `WebSecurityConfig`.

```mermaid
graph LR
    User([Standard User]) -->|GET /posts| API[Allow]
    User -.->|POST /posts| Block[403 Forbidden]
    
    Admin([Admin / Creator]) -->|GET /posts| API
    Admin -->|POST /posts| API
```

**Implementation in `SecurityFilterChain`:**
* Use `requestMatchers(HttpMethod.GET, "/posts/**").permitAll()` to make reads public.
* Use `requestMatchers(HttpMethod.POST, "/posts/**").hasAnyRole("ADMIN", "CREATOR")` to restrict writes.
* Attach the `JwtAuthFilter` before the standard `UsernamePasswordAuthenticationFilter`.
* Set session management to `SessionCreationPolicy.STATELESS` (vital for JWTs).
