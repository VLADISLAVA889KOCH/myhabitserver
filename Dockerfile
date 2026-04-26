FROM bellsoft/liberica-openjdk-alpine:17
WORKDIR /app
COPY . .
RUN chmod +x gradlew

# Сборка стандартного дистрибутива
RUN ./gradlew installDist -x test --no-daemon

# Команда запуска из папки bin
# ВНИМАНИЕ: Проверь название папки ниже. Обычно это название проекта в нижнем регистре.
CMD ["./build/install/HabitServer/bin/HabitServer"]


