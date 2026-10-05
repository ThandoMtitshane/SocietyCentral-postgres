\# Masego Madisha Development Journal



\## Project



SocietyCentral



\## Module



A101 – Register Student Development Officer (SDO)



\## Status



Completed



\## Date



29 June 2026



\---



\# Objective



The objective of A101 was to implement the functionality that allows an Administrator to register a new Student Development Officer (SDO). This registration process creates both a User account for authentication and an SDO profile containing staff-specific information.



\---



\# Development Process



\## 1. Created the RegisterSDORequest DTO



Designed a request object to receive all information required for SDO registration.



Fields included:



\* Email

\* Password

\* Title

\* First Name

\* Last Name

\* Campus

\* Staff Number

\* Office Number

\* Phone Extension



Validation annotations were added to ensure required fields are supplied before processing the request.



\---



\## 2. Created the SDO Repository



Created the Spring Data JPA repository responsible for accessing the SDO table.



Implemented repository methods required for validating duplicate staff numbers and persisting SDO records.



\---



\## 3. Implemented registerSDO() in AuthService



Created the business logic responsible for registering a Student Development Officer.



The method was annotated with @Transactional to ensure that both the User and SDO records are saved together. If any part of the process fails, the transaction is rolled back to maintain database consistency.



Business rules implemented:



\* Prevent duplicate email addresses.

\* Prevent duplicate staff numbers.

\* Encrypt passwords using BCrypt.

\* Create a User entity.

\* Create an SDO entity.

\* Save both entities to SQL Server.



\---



\## 4. Validation



Implemented validation to ensure duplicate users cannot be created.



Checks performed:



\* Existing email address.

\* Existing staff number.



If either already exists, an IllegalArgumentException is thrown with an appropriate error message.



\---



\## 5. Password Encryption



Implemented password hashing using BCryptPasswordEncoder.



Passwords are never stored as plain text.



Only the BCrypt hash is persisted in the database.



\---



\## 6. Created the REST Endpoint



Added the following endpoint to the AuthController.



POST



/api/auth/register-sdo



The endpoint:



\* Accepts JSON input.

\* Validates the request.

\* Delegates processing to AuthService.

\* Returns HTTP 201 Created after successful registration.



\---



\## 7. SQL Server Configuration



Configured the Spring Boot application to connect to SQL Server.



Completed the following tasks:



\* Configured SQL Server Authentication.

\* Created SQL login.

\* Created database user.

\* Assigned database permissions.

\* Configured application.properties.

\* Verified Spring Boot database connectivity.

\* Verified Hibernate schema validation.



Resolved several SQL Server authentication issues before the backend could communicate successfully with the database.



\---



\## 8. Spring Boot Verification



Successfully started the application.



Verified:



\* SQL Server connection.

\* Hibernate EntityManager initialisation.

\* Tomcat startup.

\* Spring Security configuration.

\* Repository scanning.



\---



\## 9. Installed and Configured Postman



Installed Postman to test REST APIs.



Configured:



\* HTTP POST requests.

\* JSON request body.

\* Request headers.



Learned how Postman communicates directly with the Spring Boot backend without requiring a frontend.



\---



\## 10. API Testing



\### Test Case 1



Scenario



Register a new Student Development Officer.



Expected Result



The application should create both User and SDO records and return HTTP 201 Created.



Actual Result



Passed.



\---



\### Test Case 2



Scenario



Attempt to register another SDO using an existing email address.



Expected Result



Registration should fail.



Actual Result



Passed.



The application returned HTTP 400 Bad Request together with an appropriate error message indicating that the email already exists.



\---



\### Test Case 3



Scenario



Login using the newly created SDO account.



Expected Result



Successful authentication and JWT generation.



Actual Result



Passed.



The application successfully authenticated the user and returned:



\* JWT Token

\* Email

\* User Type

\* Dashboard Type

\* Campus

\* User profile information



\---



\## 11. Database Verification



Verified database records using SQL Server Management Studio.



Confirmed:



\* User record created successfully.

\* SDO record created successfully.

\* BCrypt password hash stored correctly.

\* User type stored as SDO.

\* Campus stored correctly.

\* Relationship between User and SDO established correctly.



\---



\# Technologies Used



\* Java

\* Spring Boot

\* Spring Security

\* Spring Data JPA

\* Hibernate

\* SQL Server

\* SQL Server Management Studio

\* Maven

\* BCrypt Password Encoder

\* JWT

\* IntelliJ IDEA

\* Postman

\* Git

\* GitHub



\---



\# Challenges Encountered



Several SQL Server authentication issues prevented the application from connecting to the database.



Problems encountered included:



\* SQL Server Authentication configuration.

\* SQL login creation.

\* Database user mapping.

\* Database permissions.

\* Spring Boot datasource configuration.

\* SQL Server connection troubleshooting.



These issues were resolved by recreating the SQL login, configuring SQL Server Authentication correctly, validating database permissions, and testing connectivity until Spring Boot successfully connected.



\---



\# Skills Developed



Through implementing A101 I improved my understanding of:



\* Spring Boot REST API development.

\* Spring Security.

\* BCrypt password hashing.

\* Transaction management.

\* SQL Server configuration.

\* Spring Data JPA.

\* Repository pattern.

\* REST API testing using Postman.

\* JSON request handling.

\* SQL Server debugging.

\* Git workflow.



\---



\# Outcome



A101 was successfully completed.



The implemented functionality now:



\* Registers Student Development Officers.

\* Creates User accounts.

\* Creates SDO profiles.

\* Encrypts passwords.

\* Prevents duplicate registrations.

\* Stores records in SQL Server.

\* Authenticates users.

\* Generates JWT tokens.

\* Successfully passes all Postman tests.

\* Successfully persists data to the database.



The Register Student Development Officer backend is complete and ready for frontend integration.

---

# Module

A102 – Update Student Development Officer (SDO)

## Status

Completed

## Date

30 June 2026

---

# Objective

The objective of A102 was to implement the functionality that allows an Administrator to search for and update an existing Student Development Officer (SDO). The update process retrieves information from both the User and SDO tables, validates all changes, persists the updated information to SQL Server, and records an audit trail for every field that changes.

---

# Development Process

## 1. Analysed the Existing Domain Model

Reviewed the existing implementation of the User and SDO entities to understand how Student Development Officers are represented within the system.

Confirmed that:

* User stores authentication and general profile information.
* SDO stores staff-specific information.
* Both entities are linked through the email address.

---

## 2. Created the UpdateSDORequest DTO

Designed a request object to receive updated Student Development Officer information from the frontend.

Fields included:

* Email
* Title
* First Name
* Last Name
* Campus
* Staff Number
* Office Number
* Phone Extension

Validation annotations were added to ensure all mandatory information is supplied before processing the update.

---

## 3. Created the SDOProfileResponse DTO

Designed a response object used when searching for an existing Student Development Officer.

The response combines information from both the User table and the SDO table into a single object returned to the client.

Returned information includes:

* Email
* Title
* First Name
* Last Name
* Campus
* Staff Number
* Office Number
* Phone Extension

---

## 4. Implemented searchSDO() in AuthService

Implemented the business logic responsible for locating an existing Student Development Officer.

The method allows searching using either:

* Staff Number
* Email Address

The implementation retrieves information from both the User and SDO tables before constructing an SDOProfileResponse object.

Validation was implemented to ensure that at least one search criterion is supplied.

---

## 5. Implemented updateSDO() in AuthService

Implemented the business logic responsible for updating Student Development Officer information.

The method was annotated with @Transactional to ensure that updates to both the User table and the SDO table occur as a single transaction.

If any validation fails, the transaction is rolled back automatically.

Business rules implemented include:

* Validate duplicate email addresses.
* Validate duplicate staff numbers.
* Update User information.
* Update SDO information.
* Save both entities within a single transaction.

---

## 6. Implemented Audit Logging

Implemented field-level audit logging to improve accountability and traceability.

Created the AuditLog entity and corresponding AuditLogRepository.

Implemented a dedicated createAuditRecord() method responsible for recording audit information whenever monitored fields change.

Each audit record stores:

* Staff Number
* Entity Name
* Operation
* Field Changed
* Previous Value
* Updated Value
* User performing the change
* Date and Time
* Reason for the update

Audit records are automatically created before modified values are persisted.

---

## 7. Created REST Endpoints

Added the following endpoints to the AuthController.

GET

/api/auth/search-sdo

Purpose:

Retrieve an existing Student Development Officer profile using either the staff number or email address.

PUT

/api/auth/update-sdo/{staffNumber}

Purpose:

Update an existing Student Development Officer profile.

Both endpoints:

* Validate incoming requests.
* Delegate business logic to AuthService.
* Return appropriate HTTP responses.

---

## 8. Validation

Implemented validation to preserve database integrity.

Validation checks include:

* Existing email addresses.
* Existing staff numbers.
* Missing search criteria.
* Required request fields.

Meaningful exceptions are returned whenever validation fails.

---

## 9. Spring Boot Verification

Successfully started the application after implementing the new functionality.

Verified:

* Spring Boot startup.
* Repository scanning.
* Hibernate EntityManager initialisation.
* SQL Server connectivity.
* Spring Security configuration.

---

## 10. API Testing

### Test Case 1

Scenario

Search for an existing Student Development Officer using a staff number.

Expected Result

The complete Student Development Officer profile should be returned.

Actual Result

Passed.

---

### Test Case 2

Scenario

Search for an existing Student Development Officer using an email address.

Expected Result

The complete Student Development Officer profile should be returned.

Actual Result

Passed.

---

### Test Case 3

Scenario

Update an existing Student Development Officer.

Expected Result

The User and SDO records should be updated successfully.

Actual Result

Passed.

The application successfully updated both database tables and returned a successful response.

---

### Test Case 4

Scenario

Verify audit logging.

Expected Result

An audit record should be created for every modified field.

Actual Result

Passed.

Audit records were successfully persisted in the AuditLog table.

---

## 11. Database Verification

Verified database records using SQL Server Management Studio.

Confirmed:

* User table updated successfully.
* SDO table updated successfully.
* AuditLog records created successfully.
* Foreign key relationships remained valid.
* Updated information persisted correctly.

---

# Technologies Used

* Java
* Spring Boot
* Spring Security
* Spring Data JPA
* Hibernate
* SQL Server
* SQL Server Management Studio
* Maven
* BCrypt Password Encoder
* JWT
* IntelliJ IDEA
* Postman
* Git
* GitHub

---

# Challenges Encountered

Several design decisions were required while implementing the update functionality.

Challenges included:

* Designing a reusable audit logging mechanism.
* Determining when audit records should be created.
* Maintaining transactional consistency across multiple database tables.
* Validating duplicate email addresses and staff numbers.
* Preserving referential integrity while updating related entities.

These challenges were resolved through careful validation, transaction management and separating business logic into dedicated service methods.

---

# Skills Developed

Through implementing A102 I improved my understanding of:

* Spring Boot service layer design.
* Transaction management.
* REST API design.
* DTO design.
* Audit logging.
* Repository pattern.
* Spring Data JPA.
* SQL Server verification.
* Postman API testing.
* Backend validation.
* Software architecture.
* Git workflow.

---

# Outcome

A102 was successfully completed.

The implemented functionality now:

* Searches Student Development Officers.
* Retrieves complete Student Development Officer profiles.
* Updates User information.
* Updates SDO information.
* Prevents duplicate email addresses.
* Prevents duplicate staff numbers.
* Records field-level audit history.
* Maintains transactional consistency.
* Successfully passes all Postman tests.
* Successfully persists updates to SQL Server.

The Update Student Development Officer backend is complete and ready for frontend integration.

# Module

A103 – Delete Student Development Officer (SDO)

## Status

Completed (Frontend) – Backend Deletion Requires Final Debugging

## Date

20 July 2026

---

# Objective

The objective of A103 was to implement the functionality that allows an Administrator to delete an existing Student Development Officer (SDO). The deletion process removes the Student Development Officer record together with the associated User account after administrator confirmation.

---

# Development Process

## 1. Analysed the Existing Domain Model

Reviewed the relationship between the User and SDO entities to determine the correct deletion order.

Confirmed that:

* User stores authentication and profile information.
* SDO stores staff-specific information.
* Both entities are linked using the email address.

---

## 2. Implemented deleteSDO() in AdminService

Created the business logic responsible for deleting an existing Student Development Officer.

The method was annotated with `@Transactional` to ensure that the deletion process occurs within a single database transaction.

The implementation performs the following steps:

* Locate the Student Development Officer using the staff number.
* Retrieve the associated User account.
* Delete the Student Development Officer record.
* Delete the associated User account.

Appropriate exceptions are thrown if either record cannot be found.

---

## 3. Created the REST Endpoint

Added the following endpoint to the AdminController.

DELETE

`/api/admin/sdo/{staffNumber}`

The endpoint:

* Accepts the staff number as a path variable.
* Delegates processing to the AdminService.
* Returns an appropriate HTTP response after processing the request.

---

## 4. Created the Frontend API

Added a reusable Axios API method for deleting Student Development Officers.

The API method sends an HTTP DELETE request to the backend endpoint.

---

## 5. Updated the Administration Interface

Extended the Update Student Development Officer page to include delete functionality.

Implemented:

* Delete button.
* Confirmation dialog.
* Cancel option.
* Confirm Delete option.
* Automatic refresh of the Student Development Officer table after successful deletion.

The confirmation dialog helps prevent accidental deletion of user accounts.

---

## 6. Frontend Error Handling

Implemented frontend error handling to notify administrators when deletion is unsuccessful.

Errors returned by the backend are logged to the browser console to assist with debugging.

---

## 7. API Testing

### Test Case 1

Scenario

Delete an existing Student Development Officer.

Expected Result

The Student Development Officer and associated User account should be removed from the database.

Actual Result

The DELETE request successfully reached the backend.

The backend currently returns an HTTP 500 Internal Server Error during the deletion process and requires additional debugging.

---

### Test Case 2

Scenario

Attempt to delete a non-existent Student Development Officer.

Expected Result

The application should return an appropriate error indicating that the Student Development Officer could not be found.

Actual Result

Passed.

Entity validation correctly prevents deletion of non-existent records.

---

## 8. Backend Investigation

Investigated the deletion process after receiving HTTP 500 responses.

Confirmed:

* The frontend correctly sends the DELETE request.
* Axios integration is functioning correctly.
* The backend controller receives the request.
* The exception originates within the backend deletion process.

Further debugging will focus on resolving the persistence or referential integrity issue causing the deletion failure.

---

# Technologies Used

* Java
* Spring Boot
* Spring Security
* Spring Data JPA
* Hibernate
* SQL Server
* SQL Server Management Studio
* React
* Axios
* IntelliJ IDEA
* Maven
* Git
* GitHub

---

# Challenges Encountered

The primary challenge involved diagnosing an HTTP 500 Internal Server Error during the deletion process.

Although the frontend successfully communicated with the backend, the deletion operation failed during database processing.

The issue is currently being investigated and is believed to be related to entity persistence or referential integrity within the database.

---

# Skills Developed

Through implementing A103 I improved my understanding of:

* RESTful DELETE endpoints.
* Transaction management.
* Entity deletion using Spring Data JPA.
* React state management.
* Confirmation dialog design.
* Axios DELETE requests.
* Frontend error handling.
* Backend debugging.
* Git workflow.

---

# Outcome

A103 has been substantially completed.

The implemented functionality now:

* Displays all registered Student Development Officers.
* Allows administrators to initiate deletion.
* Requires confirmation before deletion.
* Sends DELETE requests to the backend.
* Handles backend responses appropriately.
* Refreshes the interface after successful operations.

The remaining task is to resolve the backend HTTP 500 error so that Student Development Officer and User records are deleted successfully.

---

# Maintenance Update

A102 – Update Student Development Officer Reliability and Confirmation Feedback

## Status

Completed

## Date

21 July 2026

---

# Objective

The objective of this maintenance task was to diagnose and resolve the HTTP 500 error that occurred when an Administrator changed a Student Development Officer's email address. The task also investigated the confirmation feedback shown after a successful update.

---

# Problem Investigation

The update route, administrator authorisation and unchanged-value update were tested successfully. The failure occurred specifically when the email address was changed.

The investigation confirmed that:

* `User.email` is the primary key of the User entity.
* The original service attempted to change the email directly on a managed User entity.
* Hibernate does not allow the identifier of a managed entity to be changed.
* The operation therefore returned HTTP 500 and the transaction rolled back.
* A second persistence issue occurred because the managed SDO relationship still referenced the old User after the replacement account was created.

---

# Backend Resolution

Updated `AdminService.updateSDO()` to distinguish between ordinary profile updates and email-address changes.

Ordinary updates continue to modify the existing User and SDO records. When the email changes, the service now performs an atomic account migration:

1. Validate that the requested email address is not already registered.
2. Create a replacement User account using the new email address.
3. Preserve the existing password hash, user role and profile picture.
4. Apply the updated title, first name, last name and campus.
5. Move all email-based foreign-key references to the replacement account.
6. Update both the SDO email column and its User relationship.
7. Delete the old User account only after all references have moved successfully.

The complete operation remains protected by `@Transactional`, ensuring that any failure rolls back every related database change.

---

# Repository Updates

Added focused repository update operations for email references stored in:

* Student records.
* Tasks assigned by the user.
* Announcements sent by the user.
* Notifications belonging to the user.
* Password reset tokens.

These updates preserve referential integrity when the User primary key changes.

---

# Testing and Verification

Added `AdminServiceTests` to verify the email migration workflow.

The automated test confirms that:

* A separate replacement User is created.
* Authentication information is preserved.
* The SDO points to the new email and replacement User.
* All dependent repositories receive the old and new email addresses.
* The old User account is removed after migration.

The focused `AdminServiceTests` test passed:

* Tests run: 1
* Failures: 0
* Errors: 0

The first complete Maven test run exposed a local SQL Server schema mismatch: the `SocietyMember` table was missing the `expireDate` column required by the JPA entity. An idempotent migration was added and applied to create the column, populate existing null values with the end of the current year, and add the same default for future memberships.

After applying the migration, the complete Maven test suite passed:

* Tests run: 2
* Failures: 0
* Errors: 0

The compiled Spring Boot application was also launched successfully and completed startup with an initialized JPA `EntityManagerFactory` and embedded Tomcat server. The temporary verification process was stopped after startup was confirmed.

The development-only startup hook that printed the seeded administrator password and a generated password hash was removed from `SocietyCentralApplication.java` so credentials are no longer exposed in application logs.

An end-to-end API test was also completed against SQL Server. An existing SDO was changed to a temporary email address, the new email was retrieved successfully, and the original email was then restored successfully.

---

# Confirmation Feedback Implementation

Implemented a dedicated success confirmation modal in `EditSDOPage.jsx`.

After a successful update, the page now:

* Keeps the Administrator on the edit page instead of redirecting immediately.
* Displays an accessible modal with an "Update Successful" heading.
* Shows the success message returned by the backend.
* Provides a "Return to SDO List" button.
* Navigates back to the SDO management page only after the Administrator dismisses the confirmation.

The modal uses `role="dialog"`, `aria-modal="true"`, and labelled title and message elements to improve accessibility. Responsive styling was added in `EditSDOPage.css`, and the confirmation icon is supplied by `lucide-react`.

The backend response was also corrected to place the confirmation text in the standard `ApiResponse.message` field while returning `null` data.

---

# Outcome

The SDO update backend now supports both normal profile changes and email-address changes without producing HTTP 500 errors. Account credentials and dependent records are preserved, database changes remain transactional, and the fix has been verified through automated and live API testing.

The update workflow now provides clear confirmation before returning the Administrator to the SDO list.
