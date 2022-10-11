#Build the Maven project
FROM maven:3.6.3 as builder
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN mvn clean install

#Build the Tomcat container
FROM tomcat:8.5.41-alpine
#set environment variables below and uncomment the line. Or, you can manually set your environment on your server.
#ENV JDBC_URL=jdbc:postgresql://<host>:<port>/<database> JDBC_USERNAME=<username> JDBC_PASSWORD=<password>
#RUN apk update
#RUN apk add zip postgresql-client

# Copy war file to webapps.
COPY --from=builder /usr/src/app/registry-fhir-server/target/registry-fhir-server.war $CATALINA_HOME/webapps/registrymanager.war

EXPOSE 8080
