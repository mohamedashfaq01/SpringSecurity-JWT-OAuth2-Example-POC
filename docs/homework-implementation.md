# Homework Implementation Details

This document outlines the three homework tasks provided and the technical details of how they were implemented within this Spring Boot application.

---

## Homework Task 1: Custom LoggingFilter
> **Requirement:** Implement a custom `LoggingFilter`. Use this filter to log the incoming requests and outgoing responses.

### Implementation Details:
1. **Creation:** We created a new class named `LoggingFilter` inside the `com.example.demo4.SecurityApp.filters` package.
2. **Architecture:** The class extends Spring's `OncePerRequestFilter`, which guarantees that the filter logic is executed exactly once per HTTP request, even if internal forwards occur.
3. **Execution Order:** We annotated it with `@Component` to make it a Spring Bean, and `@Order(Ordered.HIGHEST_PRECEDENCE)` to ensure this filter runs *before* any of the Security filters. This guarantees we log the request even if it eventually gets blocked by Spring Security.
4. **Logic:** 
   - Before `filterChain.doFilter()`, we capture `System.currentTimeMillis()` and log the incoming HTTP Method, URI, and Client IP.
   - After `filterChain.doFilter()` returns, we calculate the total time taken and log the outgoing HTTP Status code, URI, and the execution time in milliseconds.

---

## Homework Task 2: Internal Working of Spring Security
> **Requirement:** Write a detailed report on the internal working of Spring Security. Explain all of the key components in Spring Security.

### Implementation Details:
1. **Creation:** A comprehensive markdown document was created outlining the complete lifecycle of a secured request.
2. **Location:** The report is located at [docs/spring-security-internal-working.md](./spring-security-internal-working.md).
3. **Content Covered:**
   - **The Filter Chain:** Detailed explanations of `DelegatingFilterProxy`, `FilterChainProxy`, and the `SecurityFilterChain`.
   - **Authentication:** Explaining the flow from `AuthenticationManager` to `AuthenticationProvider`, and how `UserDetailsService` fetches the data.
   - **Context:** How the `SecurityContextHolder` utilizes `ThreadLocal` to maintain the logged-in state across the lifecycle of the request.

---

## Homework Task 3: Maintain Only One Active Session
> **Requirement:** Create a `SessionEntity` to store (user_id, token) and use this table to maintain only one active session per user, remove the last session token, and always use DB lookups to check if the token is valid.

### Implementation Details:
1. **Entity Design:** The `Session` entity (which maps to the `session` table) already existed, tracking the `refreshToken`, `lastUsedAt`, and mapping a `ManyToOne` relationship to the `app_user` table (User ID).
2. **Updating the Logic:** We modified the `SessionService.java` class, specifically the `generateNewSession()` method.
3. **Previous Behavior:** It allowed 2 sessions. If a 3rd was created, it deleted the oldest one based on the `lastUsedAt` timestamp.
4. **New Implementation:** 
   - When a user logs in, we fetch all existing sessions via `sessionRepository.findByUser(user)`.
   - If the list is not empty, we immediately call `sessionRepository.deleteAll(userSessions)`. 
   - Finally, we save the brand-new session.
   - **Result:** This guarantees there is strictly **only one** active session per user in the database at any given time. The old token is immediately orphaned and rendered useless.
5. **DB Lookups:** Whenever the user attempts to refresh their access token, `validateSession(refreshToken)` queries the database to ensure the token hasn't been deleted. If it was deleted (because they logged in somewhere else), an exception is thrown and access is denied.
