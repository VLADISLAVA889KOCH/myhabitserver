FROM bellsoft/liberica-openjdk-alpine:17

WORKDIR /app

# Копируем всё
COPY . .

# Магия для исправления ошибок Windows и прав доступа
RUN tr -d '\r' < gradlew > gradlew_unix && \
    mv gradlew_unix gradlew && \
    chmod +x gradlew

# Пробуем собрать. Если упадет, --info покажет НАСТОЯЩУЮ причину
RUN ./gradlew shadowJar --no-daemon --info

CMD ["sh", "-c", "java -jar build/libs/*-all.jar"]



