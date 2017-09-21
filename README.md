build
    ./gradlew build

publish local
    ./gradlew publishToMavenLocal

run
    ./gradlew installDist
    ./build/install/twitch-browser-service/bin/twitch-browser-server
