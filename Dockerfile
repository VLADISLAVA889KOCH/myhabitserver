FROM bellsoft/liberica-openjdk-alpine:17

WORKDIR /app

COPY . .

# Даем права на запуск градла (на всякий случай)
RUN chmod +x gradlew

RUN ./gradlew clean build -x test

CMD ["java", "-jar", "build/libs/myhabitserver-all.jar"]

FROM bellsoft/liberica-openjdk-alpine:17

WORKDIR /app

COPY . .

# Даем права на запуск градла (на всякий случай)
RUN chmod +x gradlew

RUN ./gradlew clean build -x test

CMD ["java", "-jar", "build/libs/myhabitserver-all.jar"]

