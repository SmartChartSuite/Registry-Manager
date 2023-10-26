# Registry-Manager


## Docker
This section of the Readme is provided as a quick build and run test for this component of the Registry stack only.
For a full deployment, please see the complete repository.

### Docker Build
To build this Docker image, use: `docker build -t registrymanager .`

### Docker Run and Environment Variables

This project requires the following environment variables defined:
* AUTH_BASIC - In the form of `user:password`. This specifies the login for the Registry Manager API itself.
* JDBC_URL - Should be the URL for the Registry Database provided as a PostgreSQL JDBC connection string such as: `jdbc:postgresql://examplepsql.com:5432/database`.
* JDBC_USERNAME - The username for the Registry Database.
* JDBC_PASSWORD - The password for the associated user in the Registry Database.
* JDBC_DATASOURCENAME=org.postgresql.ds.PGSimpleDataSource
* JDBC_POOLSIZE=5
And, belows are schema naems for data and vocabulary in the database specified above.
* JDBC_DATA_SCHEMA="syphilis"
* JDBC_VOCABS_SCHEMA="vocab"

To run this image, you can define these in a `.env` file as follows:

```
AUTH_BASIC=user:password
JDBC_URL=jdbc:postgresql://examplepsql.com:5432/database
JDBC_USERNAME=postgresuser
JDBC_PASSWORD=postgrespassword
```

Then, to run the built image using this `.env` file, run: `docker run -p 8080:8080 --env-file .env registrymanager`

You can confirm the Registry Manager is running at: http://localhost:8080/registrymanager/
