# Используем легкий образ с Java 17
FROM bellsoft/liberica-openjdk-alpine:17

# Указываем рабочую папку внутри сервера
WORKDIR /app

# Копируем абсолютно все файлы проекта в сервер
COPY . .

# Даем права на выполнение скрипта сборки
RUN chmod +x gradlew

# Собираем "толстый" JAR-файл (shadowJar), который содержит твой код и все библиотеки
RUN ./gradlew shadowJar --no-daemon

# Команда для запуска. Мы используем маску *-all.jar,
# так как плагин shadow всегда создает файл с таким окончанием.
CMD ["sh", "-c", "java -jar build/libs/*-all.jar"]


