                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         # Connecting Spring Boot to a Local SQL Server Database

## Prerequisites

* SQL Server installed
* SQL Server Management Studio (SSMS) installed
* Spring Boot project in IntelliJ

---

## Step 1: Verify SQL Server is Running

### Open SSMS

Connect using:

```text
Server Name: tcp:localhost,1433
Authentication: Windows Authentication
```

If you can see your databases in Object Explorer, SQL Server is running.

---

## Step 2: Enable TCP/IP

### Open SQL Server Configuration Manager

Navigate to:

```text
SQL Server Network Configuration
└── Protocols for MSSQLSERVER
```

Enable:

```text
TCP/IP = Enabled
```

---

## Step 3: Configure Port 1433

Right-click:

```text
TCP/IP → Properties
```

Open:

```text
IP Addresses
```

Scroll to:

```text
IPAll
```

Configure:

```text
TCP Dynamic Ports = (blank)
TCP Port = 1433
```

Click OK.

---

## Step 4: Restart SQL Server

Navigate to:

```text
SQL Server Services
```

Restart:

```text
SQL Server (MSSQLSERVER)
```

---

## Step 5: Verify SQL Server is Listening on Port 1433

Run:

```sql
SELECT local_tcp_port
FROM sys.dm_exec_connections
WHERE session_id = @@SPID;
```

Expected result:

```text
1433
```

If you get:

```text
NULL
```

then you're connected through Shared Memory instead of TCP/IP.
then restart the connection and make sure you have Server Name: tcp:localhost,1433


---

## Step 6: Create the database by running a query. (see SocieyCentral_Schema.sql and possibly Database_Documentation.md if needed.)

## Step 7: Create a SQL Login

In SSMS:

```text
Security
└── Logins
    └── New Login
```

Choose:

```text
SQL Server Authentication
```

Example:

```text
Username: Tman
Password: Tman@1234
```

Optional:

```text
Uncheck:
- Enforce password policy
- Enforce password expiration
```

---

## Step 8: Give Login Access to Database

Open:

```text
User Mapping
```

Select:

```text
SocietyCentral
```

For testing:

```text
Database Role Membership:
✔ db_owner
```

Click OK.

## Step 9: Test Connection in IntelliJ

Run the application.

Expected startup logs:

```text
HikariPool-1 - Start completed.
Initialized JPA EntityManagerFactory
Started Application
```

No errors should appear.

---

# Troubleshooting

### Login failed for user

```text
Login failed for user 'Tman'
```

Check:

* Username is correct
* Password is correct
* Login is enabled
* User is mapped to the database

---

### Connection refused

```text
Connection refused localhost:1433
```

Check:

* SQL Server service is running
* TCP/IP is enabled
* Port 1433 configured
* SQL Server restarted after configuration

---

### How to Quickly Verify SQL Server is Listening

Connect using:

```text
tcp:localhost,1433
```

If SSMS connects successfully, SQL Server is listening on TCP port 1433 and Spring Boot should be able to connect using:

```text
jdbc:sqlserver://localhost:1433;databaseName=SocietyCentral
```

That's the fastest verification method I know.