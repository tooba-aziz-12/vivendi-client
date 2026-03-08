## Introduction

This project implements a minimal Kotlin client for interacting with the Vivendi demo system API. The goal of the project is to reverse-engineer the authentication flow and the resident retrieval request used by the Vivendi web application and reproduce these interactions programmatically using Kotlin and Ktor.

The client performs two primary operations:

Authentication against the Vivendi API using RSA-encrypted credentials

Fetching residents through the Vivendi GraphQL endpoint

The implementation focuses on a clean and testable structure, separating responsibilities into authentication, client communication, and service layers. Only a small subset of the resident data model is implemented, as required by the task.

Tests are included for the critical parts of the system, including authentication, GraphQL query generation, encryption, and resident retrieval.

### Note on Scope

Only a minimal subset of the resident model is implemented, as required by the assignment.

## How to verify integration?

Integration can be verified by running Tests

The project uses Kotlin + Gradle. Tests can be executed using:

./gradlew test

Most tests use Ktor MockEngine, so they run without requiring access to the Vivendi server.

One integration-style test **(VivendiFlowIntegrationTest)** can optionally run against the Vivendi demo system.

## Architecture Decisions

### Project Naming

The project is intentionally named vivendi-client rather than something more generic like care-integration-service.

At the moment, the scope of the implementation is strictly limited to integrating with the Vivendi API. Using a broader name would suggest responsibilities that the project does not currently have and could make the architecture misleading.

By naming it vivendi-client, the project clearly communicates its current responsibility: a client responsible for interacting with the Vivendi system.

If the system later evolves to integrate with multiple care documentation systems or external providers, the project name can be revisited and expanded to better reflect the broader scope.

### Architectural Considerations

The implementation separates external system communication from application-level use cases.

VivendiClient is responsible for the technical integration with the Vivendi API. It handles HTTP communication, authentication requests, cookie extraction, GraphQL requests, and response parsing.

AuthService and ResidentService represent application use cases. They orchestrate the interaction with the client and expose operations that are meaningful from the application's perspective.

This separation keeps the integration logic reusable and isolated, while the services remain simple to test by mocking the client.

#### Why Hexagonal Architecture Was Not Used

I considered implementing a hexagonal (ports and adapters) architecture, but for the scope of this task it would introduce unnecessary complexity.

In the smallest possible hexagonal version of this solution, the structure would look roughly like this:

`domain/
    resident/
        Resident.kt
application/
    auth/
        AuthenticateUseCase.kt
    resident/
        GetResidentsUseCase.kt
    port/
        output/
            AuthPort.kt
            ResidentPort.kt
adapters/
    output/
        vivendi/
        VivendiClientAdapter.kt
        dto/
config/
    DependencyInjection.kt

Application.kt
`
This structure is useful when:

* multiple external systems are supported
* integrations are frequently swapped or replaced
* domain logic must remain completely independent from infrastructure
* none of these conditions apply to this task. The system integrates with exactly one external provider, and there is no domain logic that requires isolation from infrastructure concerns.
* Introducing ports, adapters, and additional abstraction layers would therefore increase the number of files and concepts without improving clarity or flexibility. For a small integration client like this, the current structure keeps the code direct, readable, and easy to test while still maintaining a clear separation between application logic and external communication.

## Approach taken to Reverse Engineer the Vivendi API

I could not find any public documentation of the Vivendi authentication or GraphQL API. 

To implement the integration, I inspected the requests performed by the web application using Chrome DevTools (Network tab).

I copied the captured requests as cURL commands and used as reference for reproducing the authentication flow and GraphQL queries in Kotlin.

Reference document containing the captured requests, check following screenshots to see how I fetched them:

**docs/Vivendi-reverse-engineered.pdf**

![img.png](assets/rsa-key-api.png)![img_1.png](assets/login-api.png)![img_2.png](assets/resident-fetch-api.png)![img_3.png](assets/resident-fetch-api-response.png)

### Residents Fetch Flow:

Network inspection revealed the following authentication flow used by the Vivendi web client:

User **->** Browser **->** GET /auth/rsa-key **->** Client encrypts password **->** POST /auth/login **->** Auth cookies issued **->** GraphQL API calls

#### Steps:

* Client fetches a public RSA key
* Password is encrypted using RSA-OAEP
* Login request is sent with encrypted password
* Server returns authentication cookies
* Cookies are used for subsequent GraphQL requests

### Password Encryption

The login request contains an encrypted Base64 string instead of a plaintext password, indicating that the frontend encrypts credentials before transmission.

To reproduce this behavior, the vivendi client encrypts the password using:

RSA/ECB/OAEPWithSHA-256AndMGF1Padding

This was inspired by the encryption code sent in the task.

### Session Handling

After successful authentication, the API returns authentication information through both the response body and cookies.

The Auth-Token (JWT) is available in the response and also set as a cookie. However, the Xsrf-Token is only provided via the Set-Cookie header.

Because subsequent GraphQL requests require both the authentication cookie and the XSRF header, the client extracts these values from the response cookies and stores them for later use.

![img_4.png](assets/login-cookies.png)

These tokens are then attached to all GraphQL requests as:

cookies (Auth-Token, Xsrf-Token)
header (x-xsrf-token)

### GraphQL Query Simplification

The original GraphQL query used by the web client returns a large number of fields.
For this implementation, the query was simplified to only fetch the essential resident fields:
* id
* name
* vorname
* geburtsdatum

### Why Apollo Client Was Not Used

Although the API uses GraphQL, Ktor was used instead of Apollo Client to keep the HTTP interaction explicit.

This makes the following aspects transparent:

* authentication cookies
* custom headers
* encrypted login payload
* exact GraphQL request structure

Apollo would introduce additional abstraction that is unnecessary for this small integration client.

### Error Handling Strategy

The client handles common failure scenarios explicitly and uses custom exceptions to make errors clearer for callers.

Two custom exceptions are defined:
* **AuthenticationException** — thrown when authentication fails (e.g., non-success login response or missing authentication cookies).
* **VivendiClientException** — used for general API and integration errors.

#### Handled scenarios include:

* HTTP errors returned by the Vivendi API
* GraphQL errors in the response
* Missing data in the response
* Invalid response format during JSON parsing

Using custom exceptions makes it easier to tell the difference between login problems and API errors, and prevents low-level HTTP or parsing errors from appearing directly in the higher parts of the application.

## Handling Large Resident Lists

### 1. Local Dataset Synchronization

The GraphQL query used by Vivendi returns the **full list of residents for a section (`bereichId`)**. 

From the inspected request, I could not find any indication of built-in pagination parameters such as `limit`, `offset`, or cursors. This suggests that the API is designed to return the complete dataset for the requested section in a single response.

Directly fetching the full dataset on every client request would not scale well in a production environment when (**in case**) there thousands of records coming in. 

A more scalable architecture would introduce an **integration layer with local synchronization**:

#### In this approach:

- A **scheduled synchronization job** periodically fetches residents from Vivendi.
- The data is stored in a **local database**.
- Client applications query the **local service**, not Vivendi directly.

#### This has several advantages:

- **Reduced load on Vivendi** – only the sync job calls the external API.
- **Low latency for clients** – local database queries are faster than remote API calls.
- **Better filtering and search capabilities** – complex queries can be executed locally.
- **Resilience** – the system can continue operating temporarily even if Vivendi is unavailable.

In containerized environments (e.g., Kubernetes), the synchronization job should run as a **dedicated CronJob** rather than inside application pods. This ensures the job executes **once per schedule**, independent of how many application instances are running.

### 2. Server-Side Pagination in the Integration Layer

Another approach is to fetch the dataset from Vivendi and expose a **paginated API from the integration service**.

#### In this case:

- The integration service calls Vivendi when needed.
- The service **splits the response into smaller pages** before returning it to clients.

#### This approach makes sense when:

- The dataset is relatively small
- Real-time freshness is important
- Maintaining a local data store is unnecessary
- The integration service acts mainly as a **thin proxy layer**

However, if many clients frequently request the data, the synchronization approach generally scales better because it avoids repeatedly calling the external API.

## Production Readiness Considerations

Several aspects should be addressed if this client were used in a production environment.

### Timeouts and Retries
External API calls should have **configured timeouts** to prevent requests from hanging indefinitely.  
Retries can be applied for **transient failures** such as network timeouts or temporary server errors (5xx responses), typically with a small exponential backoff.

### Logging and Observability
Structured logging should be used to make debugging easier in distributed environments.  
Sensitive data such as **passwords, authentication tokens, and cookies** must be redacted to avoid leaking credentials in logs.

### Rate Limiting and Backoff
If multiple services depend on this client, rate limiting and backoff strategies help prevent overwhelming the Vivendi API during traffic spikes or failure loops.

### Resilience
The system should degrade gracefully if the Vivendi API becomes temporarily unavailable.  
For example, services could rely on **cached or previously synchronized data** instead of failing immediately.

### Monitoring
Operational metrics should be collected for:

- request latency
- error rates
- authentication failures
- retry counts

These metrics help detect integration issues early and provide visibility into the health of the Vivendi integration.

## Deployment and Scaling Strategy

In a production environment, the client would typically run as part of an **integration service deployed in containers** (e.g., Kubernetes).

Application pods can scale horizontally to handle incoming traffic, since the client itself is stateless.

If resident data synchronization is implemented, the synchronization process should run as a **dedicated scheduled job (e.g., Kubernetes CronJob)** rather than inside application pods. This ensures that the job executes **once per schedule**, regardless of how many service instances are running.

This architecture allows the system to scale client-facing workloads independently while maintaining controlled interaction with the Vivendi API.