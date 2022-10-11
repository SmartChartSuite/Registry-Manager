#Build the Maven project
FROM maven:3.8.3-openjdk-17 as builder
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN mvn clean install

#Build the Tomcat container
FROM tomcat:9.0.65-jre17

# Copy war file to webapps.
COPY --from=builder /usr/src/app/registry-fhir-server/target/registry-fhir-server.war $CATALINA_HOME/webapps/registrymanager.war

EXPOSE 8080
