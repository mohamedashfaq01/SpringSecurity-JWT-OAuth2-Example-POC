# Swagger UI / OpenAPI Testing Guide

We have successfully integrated `springdoc-openapi` into the application. This provides a beautiful web interface to interact with your API endpoints directly, without needing tools like Postman.

## 1. Accessing Swagger
Start your Spring Boot application, then open your browser and navigate to:
**👉 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

*(Note: If you changed your server port, replace `8080` with your actual port).*

## 2. How to Test the API

Since this application is secured with JWTs, most endpoints will return `401 Unauthorized` unless you provide a valid token. Here is the workflow to test successfully:

### Step A: Register / Login
1. In the Swagger UI, scroll down to the `auth-controller` section.
2. Expand the `POST /auth/signup` endpoint, click **"Try it out"**, provide a sample JSON payload (email, password, name, roles), and click **Execute**.
3. Next, expand `POST /auth/login`. Click **"Try it out"**, provide the email and password you just created, and click **Execute**.
4. In the response body below, you will see your `accessToken`. **Copy this token string** (without the quotes).

### Step B: Authorize Swagger
1. Scroll to the very top of the Swagger UI page.
2. Click the green **"Authorize 🔓"** button.
3. In the popup window under `bearerAuth`, paste the `accessToken` you copied.
4. Click **"Authorize"**, then click **"Close"**. The padlock icon 🔒 should now appear closed.
5. *Swagger will now automatically attach `Authorization: Bearer <your-token>` to every subsequent request you make from the UI.*

### Step C: Test Protected Endpoints
1. Scroll down to the `post-controller` section.
2. Expand `POST /posts` and click **"Try it out"**.
3. Since Swagger is now authorized, you can send a payload to create a post.
4. *Important Note:* The `POST /posts` endpoint requires the `ADMIN` or `CREATOR` role. If you registered with the `USER` role, you will get a `403 Forbidden` response. This proves your Role-Based Access Control (RBAC) is working perfectly!

## 3. Refreshing the Token
1. Your access token expires in 10 minutes. 
2. Because the `AuthController` returns the refresh token as an `HttpOnly` cookie, your browser automatically saves it.
3. When your access token expires, you can call `POST /auth/refresh` from Swagger. The browser will automatically attach the cookie, and you will receive a fresh access token in the response!
4. Copy the new token, go back to the top **Authorize** button, log out, and log back in with the new token.
