FROM openjdk:17-jdk-slim
COPY target/*.jar /usr/app/app.jar
COPY observability/agent.properties /usr/app/agent/
COPY observability/vaadin-opentelemetry-javaagent-1.0.0.rc1.jar /usr/app/agent/
RUN useradd -m myuser
USER myuser
WORKDIR /home/myuser
RUN mkdir .vaadin
COPY observability/offlineKey .vaadin/
EXPOSE 8080
CMD java -jar /usr/app/app.jar
