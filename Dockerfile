# Используем образ с Java 17
FROM openjdk:17-jdk-slim

# Создаем папку для приложения
WORKDIR /app

# Копируем всё из твоего проекта в контейнер
COPY . .

# Собираем проект внутри контейнера
RUN ./gradlew clean build -x test

# Запускаем сервер (проверь, что имя файла совпадает с твоим проектом)
CMD ["java", "-jar", "build/libs/myhabitserver-all.jar"]
