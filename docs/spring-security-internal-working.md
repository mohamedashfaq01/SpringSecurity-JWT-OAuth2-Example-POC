# Internal Workings of Spring Security: Detailed Report

Spring Security is a powerful and highly customizable authentication and access-control framework. Its architecture intercepts web requests and applies authorization, authentication, and security features (like CSRF and CORS protections) before the request ever reaches your actual business logic (Controllers).

Here is a detailed breakdown of the key components and how they orchestrate security internally.

---

## 1. The Big Picture: The Filter Chain

Spring Security is fundamentally a chain of Servlet **Filters**. A web request comes into the server, passes through a series of security filters, and finally hits the `DispatcherServlet`.

### DelegatingFilterProxy
The standard Servlet container (like Tomcat) doesn't know about Spring's dependency injection context. The `DelegatingFilterProxy` bridges the gap. It's a standard Servlet Filter that intercepts the request and delegates it to a Spring-managed Bean (which happens to be the `FilterChainProxy`).

### FilterChainProxy
This is the central routing component of Spring Security. It looks at the incoming request's URL and determines which `SecurityFilterChain` should be applied to it. 

### SecurityFilterChain
A collection of specific filters applied to a specific URL pattern. You configure this in your `WebSecurityConfig` class. The order of filters here is strict (e.g., CORS filter runs before Auth filter). Our custom `JwtAuthFilter` was manually injected into this chain using `.addFilterBefore(...)`.

---

## 2. Authentication Architecture

When a request attempts to authenticate (e.g., logging in), Spring Security uses a specific set of components to verify identity.

### AuthenticationManager
This is the primary interface for authentication. It has one method: `authenticate(Authentication auth)`. When our `AuthService` attempts to log someone in, it calls the `AuthenticationManager`. It doesn't do the checking itself; instead, it delegates to a list of `AuthenticationProvider`s.

### AuthenticationProvider
Providers are where the actual logic lives. The most common one is the `DaoAuthenticationProvider`. It handles username/password checks. A provider will:
1. Take the provided credentials.
2. Fetch the "true" user details from the database.
3. Compare the hashed passwords.
4. If successful, return a fully populated `Authentication` object containing the user's roles.

### UserDetailsService
The `AuthenticationProvider` doesn't query the database directly. It delegates data retrieval to the `UserDetailsService`. This interface defines one crucial method: `loadUserByUsername(String username)`. In our app, we implemented this in the `UserService` to query our `UserRepository` for the user's email.

---

## 3. Holding the Security State

### SecurityContextHolder
Once a user is successfully authenticated (either by logging in, or by passing a valid JWT via the `JwtAuthFilter`), the `Authentication` object must be stored so the rest of the application knows who is logged in.

This is stored in the `SecurityContext`, which is held in the `SecurityContextHolder`. By default, Spring uses a `ThreadLocal` strategy for this. This means the authenticated state is tied to the specific Thread processing the current HTTP request. When the Controller is finally reached, it can call:
```java
SecurityContextHolder.getContext().getAuthentication();
```
To instantly verify who is making the request and enforce `@PreAuthorize` or URL-level Role-Based Access Controls.

---

## Summary of the Flow

1. **Request Arrives**: `DelegatingFilterProxy` catches it and passes it to `FilterChainProxy`.
2. **Filter Execution**: The request passes through the `SecurityFilterChain`. 
3. **Authentication Phase**: If it's a login request, `AuthenticationManager` delegates to `AuthenticationProvider`, which uses `UserDetailsService` and `PasswordEncoder` to verify credentials.
4. **Token Generation (Our Custom Logic)**: Upon success, we generate a JWT and a Refresh Token, saving the Refresh Token in our `SessionEntity` (enforcing max 1 session per user).
5. **Context Population**: For protected routes, `JwtAuthFilter` validates the token and populates the `SecurityContextHolder`.
6. **Authorization Phase**: Spring checks if the populated user has the necessary roles to hit the requested endpoint. If yes, the request reaches the Controller.
