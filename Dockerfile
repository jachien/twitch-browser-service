FROM java:8-jdk-alpine
ENV SPRING_PROFILES_ACTIVE=prod
VOLUME /tmp
ADD build/libs/twitch-browser-service-1.0.jar app.jar
RUN sh -c 'touch /app.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
EXPOSE 8081
EXPOSE 62898
