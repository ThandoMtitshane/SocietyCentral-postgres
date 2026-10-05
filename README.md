# SocietyCentral

A centralised platform for managing student societies, events, RSVPs, tasks, and announcements at NMU.

This is the Spring Boot backend (Java 25, Maven, SQL Server).

---

## Prerequisites

Before you start, make sure you have:

- **JDK 25** (the project uses Java 25 - check via `java -version`)
- **IntelliJ IDEA** (Community or Ultimate)
- **SQL Server** (Developer or Express edition) installed and running locally
- **SQL Server Management Studio (SSMS)** or Azure Data Studio, to run the schema script

---

## Setup Steps

### 1. Clone the repo

```bash
git clone <repo-url>
cd SocietyCentral
```

### 2. Create the database

1. Open SSMS (or Azure Data Studio) and connect to your local SQL Server instance.
2. Open the schema script located at `database/SocietyCentral_Schema.sql`.
3. Run the entire script. This creates the `SocietyCentral` database and all 15 tables with the correct constraints, composite keys, and foreign keys.

> **Important:** Everyone on the team must run this *exact* script. Do not modify table structures locally - if the schema needs to change, the change should be made to this script and shared with the team.

### 3. Create your own SQL Server login

Each team member should create their **own** SQL Server login dedicated to this project (don't use `sa` or share credentials).

In SSMS:
- Security → Logins → New Login
- Choose SQL Server Authentication, set a username and password
- Under "User Mapping", give this login access to the `SocietyCentral` database with `db_owner` (or at minimum `db_datareader` + `db_datawriter`) permissions

You'll use these credentials in the next step - they stay on **your machine only**.

### 4. Configure `application.properties`

This file is **not** included in the repo (it's gitignored) because it contains personal database credentials.

1. Navigate to `src/main/resources/`
2. Copy `application.properties.example` and rename the copy to `application.properties`
3. Open it and fill in:
   - `spring.datasource.username` → your SQL Server login from Step 3
   - `spring.datasource.password` → your SQL Server login's password
   - `spring.datasource.url` → adjust only if your SQL Server setup differs from the default (see note below)

Email is disabled locally when `RESEND_API_KEY` is unset. Set that environment
variable to a valid Resend API key when you want the application to send email.

> **Named instance note:** If your SQL Server uses a named instance (common with SQL Server Express, e.g. `SQLEXPRESS`), your URL should look like:
> ```
> spring.datasource.url=jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=SocietyCentral;encrypt=true;trustServerCertificate=true
> ```
> Note the double backslash (`\\`) - this is required for Java string escaping.

### 5. Open and run the project in IntelliJ

1. Open IntelliJ → Open → select the `SocietyCentral` folder
2. Wait for Maven to download dependencies (first time may take a few minutes)
3. Run `SocietyCentralApplication.java`

### 6. Confirm a clean startup

You should see in the console:

```
Initialized JPA EntityManagerFactory for persistence unit 'default'
...
Started SocietyCentralApplication in XX seconds
```

If you see a `SchemaManagementException` mentioning a missing column - double-check you ran the **full** schema script from Step 2, and that `application.properties` has the naming strategy line shown in the example file.

If you see a connection error - double-check your SQL Server is running, and your credentials/URL in `application.properties` are correct.

---

## Project Structure

```
com.societycentral
├── SocietyCentralApplication.java   - main entry point
├── config/                          - security, CORS, app-wide config
├── model/                           - JPA entities, enums, composite keys
├── repository/                      - Spring Data JPA repositories
├── dto/                              - request/response objects for the API
├── service/                          - business logic
├── controller/                       - REST endpoints
├── exception/                        - error handling
└── util/                              - shared helper classes
```

---

## Notes for Contributors

- `spring.jpa.hibernate.ddl-auto=validate` is set deliberately - Hibernate will **check** your entities match the database schema, but will never auto-create or alter tables. If you change an entity, update `database/SocietyCentral_Schema.sql` to match, and let the team know to re-run it.
- Do **not** commit `application.properties` - it's personal and gitignored.
- If you add a new dependency to `pom.xml`, briefly note in your PR/commit message what it's for.
