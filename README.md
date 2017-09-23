build
    ./gradlew build

publish local
    ./gradlew publishToMavenLocal

run
    java -Dspring.profiles.active=dev -jar build/libs/twitch-browser-service-1.0.jar
