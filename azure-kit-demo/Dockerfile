FROM openjdk:17-jdk-slim
COPY target/*.jar /usr/app/app.jar
RUN useradd -m myuser
USER myuser
EXPOSE 8080
CMD java -jar /usr/app/app.jar
