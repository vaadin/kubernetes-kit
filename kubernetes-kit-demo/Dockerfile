FROM openjdk:17-jdk-slim
COPY target/*.jar /usr/app/app.jar
RUN useradd -m myuser
USER myuser
EXPOSE 8080
CMD java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar /usr/app/app.jar
