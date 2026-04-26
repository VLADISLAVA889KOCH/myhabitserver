package com.example.database

import com.example.models.*
import io.ktor.server.config.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime

data class UserRow(
    val id: Int,
    val login: String,
    val email: String,
    val isVerified: Boolean
)

// --- ОПИСАНИЕ ТАБЛИЦ ---

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val login = varchar("login", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val email = varchar("email", 100).uniqueIndex()
    val isVerified = bool("is_verified").default(false)
    val verificationCode = varchar("verification_code", 6).nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val avatarUrl = varchar("avatar_url", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object Habits : Table("habits") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val description = text("description").nullable()
    val goalDays = varchar("goal_days", 255)
    val priority = integer("priority").default(1)
    val colorHex = varchar("color_hex", 7).default("#6200EE")
    val isActive = bool("is_active").default(true)
    val showRemindersOnCard = bool("show_reminders_on_card").default(true)
    override val primaryKey = PrimaryKey(id)
}

object HabitReminders : Table("habit_reminders") {
    val id = integer("id").autoIncrement()
    val habitId = integer("habit_id").references(Habits.id, onDelete = ReferenceOption.CASCADE)
    val reminderTime = varchar("reminder_time", 5)
    val dayOfWeek = varchar("day_of_week", 20).default("Каждый день")
    override val primaryKey = PrimaryKey(id)
}

object Completions : Table("completions") {
    val id = integer("id").autoIncrement()
    val habitId = integer("habit_id").references(Habits.id, onDelete = ReferenceOption.CASCADE)
    val completionDate = varchar("completion_date", 20)
    val status = bool("status").default(true)
    override val primaryKey = PrimaryKey(id)
}

object VisionBoard : Table("vision_board") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 100)
    val imageUrl = text("image_url").nullable()
    val isCompleted = bool("is_completed").default(false)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

object DreamHabits : Table("dream_habits") {
    val dreamId = integer("dream_id").references(VisionBoard.id, onDelete = ReferenceOption.CASCADE)
    val habitId = integer("habit_id").references(Habits.id, onDelete = ReferenceOption.CASCADE)
}


// --- ЛОГИКА БАЗЫ ДАННЫХ ---

object DatabaseFactory {

    fun init(config: ApplicationConfig) {
        val hikariConfig = HikariConfig().apply {
            driverClassName = config.property("storage.driverClassName").getString()
            jdbcUrl = config.property("storage.jdbcURL").getString()
            username = config.property("storage.user").getString()
            password = config.property("storage.password").getString()
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(hikariConfig))

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users, Habits, HabitReminders, Completions, VisionBoard, DreamHabits
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction { block() }

    // --- ПОЛЬЗОВАТЕЛИ И НАСТРОЙКИ ---

    suspend fun getUserProfile(uId: Int): UserProfile? = dbQuery {
        val userRow = Users.select { Users.id eq uId }.singleOrNull() ?: return@dbQuery null
        val habitsCount = Habits.select { Habits.userId eq uId }.count().toInt()
        val registrationDate = userRow[Users.createdAt].toLocalDate()
        val daysInApp = java.time.temporal.ChronoUnit.DAYS.between(registrationDate, LocalDate.now()).toInt() + 1

        val habitIds = Habits.select { Habits.userId eq uId }.map { it[Habits.id] }
        val successRate = if (habitIds.isNotEmpty()) {
            val totalPossible = habitIds.size * daysInApp
            val actualCompletions = Completions.select { Completions.habitId inList habitIds }.count().toInt()
            if (totalPossible > 0) ((actualCompletions.toDouble() / totalPossible) * 100).toInt()
                .coerceAtMost(100) else 0
        } else 0

        // Добавлено 6-е поле avatarUrl, как мы и планировали
        UserProfile(
            userRow[Users.login],
            userRow[Users.email],
            daysInApp,
            habitsCount,
            successRate,
            userRow[Users.avatarUrl]
        )


    }

    suspend fun updateAvatar(userId: Int, url: String): Boolean = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[avatarUrl] = url
        } > 0
    }

    suspend fun getUserPasswordHash(uId: Int): String? = dbQuery {
        Users.select { Users.id eq uId }.map { it[Users.passwordHash] }.singleOrNull()
    }

    suspend fun updatePassword(uId: Int, newHash: String): Boolean = dbQuery {
        Users.update({ Users.id eq uId }) { it[passwordHash] = newHash } > 0
    }

    suspend fun deleteUserAccount(uId: Int): Boolean = dbQuery {
        Users.deleteWhere { Users.id eq uId } > 0
    }

    // --- ПРИВЫЧКИ ---

    suspend fun getAllHabits(uId: Int): List<Habit> = dbQuery {
        val today = LocalDate.now().toString()
        Habits.select { Habits.userId eq uId }.map { row ->
            val hId = row[Habits.id]
            val isDone = Completions.select { (Completions.habitId eq hId) and (Completions.completionDate eq today) }.count() > 0
            val times = HabitReminders.select { HabitReminders.habitId eq hId }.map { "${it[HabitReminders.dayOfWeek]}: ${it[HabitReminders.reminderTime]}" }
            rowToHabit(row, times, isDone)
        }
    }

    suspend fun addHabit(habit: Habit, uId: Int) = dbQuery {
        val id = Habits.insert {
            it[userId] = uId
            it[name] = habit.name
            it[description] = habit.description
            it[goalDays] = habit.goalDays
            it[priority] = habit.priority
            it[colorHex] = habit.colorHex
            it[isActive] = habit.isActive
            it[showRemindersOnCard] = habit.showRemindersOnCard // Добавлено
        }[Habits.id]
        habit.reminders.forEach { r ->
            val parts = r.split(": ")
            HabitReminders.insert {
                it[habitId] = id
                it[dayOfWeek] = parts.getOrNull(0) ?: "Каждый день"
                it[reminderTime] = parts.getOrNull(1) ?: "00:00"
            }
        }
    }

    suspend fun updateHabit(habitId: Int, uId: Int, habit: Habit): Boolean = dbQuery {
        val updated = Habits.update({ (Habits.id eq habitId) and (Habits.userId eq uId) }) {
            it[name] = habit.name
            it[description] = habit.description
            it[goalDays] = habit.goalDays
            it[priority] = habit.priority
            it[colorHex] = habit.colorHex
            it[isActive] = habit.isActive
            it[showRemindersOnCard] = habit.showRemindersOnCard
        } > 0
        if (updated) {
            HabitReminders.deleteWhere { HabitReminders.habitId eq habitId }
            habit.reminders.forEach { r ->
                val parts = r.split(": ")
                HabitReminders.insert {
                    it[HabitReminders.habitId] = habitId
                    it[dayOfWeek] = parts.getOrNull(0) ?: "Каждый день"
                    it[reminderTime] = parts.getOrNull(1) ?: "00:00"
                }
            }
        }
        updated
    }

    suspend fun deleteHabit(habitId: Int, uId: Int) = dbQuery {
        Habits.deleteWhere { (Habits.id eq habitId) and (Habits.userId eq uId) }
    }

    suspend fun markHabitCompleted(hId: Int, uId: Int): Boolean = dbQuery {
        val today = LocalDate.now().toString()
        val owns = Habits.select { (Habits.id eq hId) and (Habits.userId eq uId) }.count() > 0
        if (!owns) return@dbQuery false
        val already = Completions.select { (Completions.habitId eq hId) and (Completions.completionDate eq today) }.count() > 0
        if (!already) {
            Completions.insert { it[habitId] = hId; it[completionDate] = today; it[status] = true }
        } else {
            Completions.deleteWhere { (Completions.habitId eq hId) and (Completions.completionDate eq today) }
        }
        true
    }

    // --- VISION BOARD ---

    suspend fun getVisionBoard(uId: Int): List<DreamResponse> = dbQuery {
        VisionBoard.select { VisionBoard.userId eq uId }.orderBy(VisionBoard.createdAt, SortOrder.DESC).map { row ->
            val dId = row[VisionBoard.id]
            val linkedIds = DreamHabits.select { DreamHabits.dreamId eq dId }.map { it[DreamHabits.habitId] }
            DreamResponse(dId, row[VisionBoard.title], row[VisionBoard.imageUrl], row[VisionBoard.isCompleted], linkedIds)
        }
    }

    suspend fun addDream(uId: Int, title: String, imageUrl: String?, habitIds: List<Int>) = dbQuery {
        val dId = VisionBoard.insert {
            it[userId] = uId
            it[VisionBoard.title] = title
            it[VisionBoard.imageUrl] = imageUrl
            it[createdAt] = LocalDateTime.now()
        }[VisionBoard.id]
        habitIds.forEach { hId -> DreamHabits.insert { it[dreamId] = dId; it[habitId] = hId } }
    }

    suspend fun updateDream(dId: Int, uId: Int, title: String, imageUrl: String?, habitIds: List<Int>): Boolean = dbQuery {
        val updated = VisionBoard.update({ (VisionBoard.id eq dId) and (VisionBoard.userId eq uId) }) {
            it[VisionBoard.title] = title
            it[VisionBoard.imageUrl] = imageUrl
        } > 0
        if (updated) {
            DreamHabits.deleteWhere { DreamHabits.dreamId eq dId }
            habitIds.forEach { hId -> DreamHabits.insert { it[dreamId] = dId; it[habitId] = hId } }
        }
        updated
    }

    suspend fun deleteDream(dId: Int, uId: Int): Boolean = dbQuery {
        VisionBoard.deleteWhere { (VisionBoard.id eq dId) and (VisionBoard.userId eq uId) } > 0
    }

    suspend fun toggleDreamStatus(dId: Int, uId: Int): Boolean = dbQuery {
        val status = VisionBoard.select { (VisionBoard.id eq dId) and (VisionBoard.userId eq uId) }.map { it[VisionBoard.isCompleted] }.singleOrNull() ?: return@dbQuery false
        VisionBoard.update({ VisionBoard.id eq dId }) { it[isCompleted] = !status } > 0
    }

    // --- СТАТИСТИКА ---

    suspend fun getUserStatistics(uId: Int): UserStatsResponse = dbQuery {
        val allHabitsRows = Habits.select { Habits.userId eq uId }.toList()
        val today = LocalDate.now()

        // Определяем текущий день недели (Пн, Вт и т.д.)
        val currentDayOfWeek = when(today.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "Пн"
            java.time.DayOfWeek.TUESDAY -> "Вт"
            java.time.DayOfWeek.WEDNESDAY -> "Ср"
            java.time.DayOfWeek.THURSDAY -> "Чт"
            java.time.DayOfWeek.FRIDAY -> "Пт"
            java.time.DayOfWeek.SATURDAY -> "Сб"
            java.time.DayOfWeek.SUNDAY -> "Вс"
        }

        val habitStatsList = allHabitsRows.mapNotNull { row ->
            val hId = row[Habits.id]
            val goalValue = parseGoalToDays(row[Habits.goalDays])

            // Получаем все даты выполнения этой привычки
            val completions = Completions.select { Completions.habitId eq hId }
                .map { it[Completions.completionDate] }

            val doneInMonth = completions.count { it >= today.minusDays(30).toString() }
            val percent = if (goalValue > 0 && doneInMonth > 0) {
                val calculated = (doneInMonth.toDouble() / goalValue * 100).toInt()
                if (calculated == 0) 1 else calculated // Если результат 0.3%, покажем хотя бы 1%
            } else if (goalValue > 0) {
                0
            } else 0

            // РАСЧИТЫВАЕМ СЕРИЮ ВМЕСТО 0
            val streak = calculateHabitStreak(completions)

            HabitStat(
                habitId = hId,
                name = row[Habits.name],
                color = row[Habits.colorHex],
                completedCount = doneInMonth,
                goalValue = goalValue,
                progressPercent = percent.coerceAtMost(100),
                currentStreak = streak // ТЕПЕРЬ ТУТ РЕАЛЬНОЕ ЧИСЛО
            )
        }

        UserStatsResponse(
            // Передаем текущий день для расчета прогресса за сегодня
            progressDay = getProgressForToday(uId, allHabitsRows, currentDayOfWeek),
            progressWeek = getProgressForPeriod(uId, 7, allHabitsRows),
            progressMonth = getProgressForPeriod(uId, 30, allHabitsRows),
            activeStreaks = calculateStreak(uId),
            bestHabit = habitStatsList.maxByOrNull { it.completedCount }?.name ?: "---",
            habitsDistribution = habitStatsList
        )
    }

    private fun getProgressForToday(uId: Int, habits: List<ResultRow>, currentDay: String): Int {
        if (habits.isEmpty()) return 0

        val todayDate = LocalDate.now().toString()

        // Фильтруем привычки, которые актуальны именно СЕГОДНЯ
        val relevantHabitsForToday = habits.filter { row ->
            val goal = row[Habits.goalDays]
            goal.contains("Каждый день") || goal.contains(currentDay)
        }

        if (relevantHabitsForToday.isEmpty()) return 0

        val habitIds = relevantHabitsForToday.map { it[Habits.id] }

        // Считаем, сколько из актуальных на сегодня привычек выполнено
        val doneToday = Completions.select {
            (Completions.habitId inList habitIds) and (Completions.completionDate eq todayDate)
        }.count().toInt()

        // Если привычки выполнены, но при делении получается меньше 1%, возвращаем 1%
        return if (doneToday > 0) {
            val percent = ((doneToday.toDouble() / relevantHabitsForToday.size) * 100).toInt()
            if (percent == 0) 1 else percent.coerceAtMost(100)
        } else {
            0
        }
    }

    private fun getProgressForPeriod(uId: Int, days: Int, habits: List<ResultRow>): Int {
        if (habits.isEmpty()) return 0
        val startDate = LocalDate.now().minusDays(days.toLong()).toString()
        val habitIds = habits.map { it[Habits.id] }
        val totalDone = Completions.select { (Completions.habitId inList habitIds) and (Completions.completionDate greaterEq startDate) }.count().toInt()
        val divisor = if (days == 0) habits.size else habits.size * (days + 1)
        return ((totalDone.toDouble() / divisor) * 100).toInt().coerceAtMost(100)
    }

    private fun calculateStreak(uId: Int): Int {
        val dates = (Habits innerJoin Completions).slice(Completions.completionDate).select { Habits.userId eq uId }.orderBy(Completions.completionDate, SortOrder.DESC).map { it[Completions.completionDate] }.distinct()
        if (dates.isEmpty()) return 0
        var streak = 0
        var check = LocalDate.now()
        if (dates.first() != check.toString() && dates.first() != check.minusDays(1).toString()) return 0
        check = LocalDate.parse(dates.first())
        for (d in dates) { if (d == check.toString()) { streak++; check = check.minusDays(1) } else break }
        return streak
    }
    private fun calculateHabitStreak(completions: List<String>): Int {
        if (completions.isEmpty()) return 0

        val sortedDates = completions.distinct().sortedDescending()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        // Если нет отметки ни за сегодня, ни за вчера — серия прервана
        if (sortedDates.first() != today.toString() && sortedDates.first() != yesterday.toString()) {
            return 0
        }

        var streak = 0
        var checkDate = LocalDate.parse(sortedDates.first())

        for (dateStr in sortedDates) {
            if (dateStr == checkDate.toString()) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    private fun parseGoalToDays(goal: String): Int {
        return when {
            goal.contains("недел") -> (goal.filter { it.isDigit() }.toIntOrNull() ?: 1) * 7
            goal == "21 день" -> 21
            goal.contains("месяц") -> (goal.filter { it.isDigit() }.toIntOrNull() ?: 1) * 30
            goal == "1 год" -> 365
            else -> 30
        }
    }

    private fun rowToHabit(row: ResultRow, times: List<String>, isDone: Boolean) = Habit(
        row[Habits.id],
        row[Habits.userId],
        row[Habits.name],
        row[Habits.description],
        row[Habits.goalDays],
        row[Habits.priority],
        row[Habits.colorHex],
        times,
        row[Habits.isActive],
        isDone,
        showRemindersOnCard = row[Habits.showRemindersOnCard]
    )

    // --- НОВЫЕ МЕТОДЫ ДЛЯ ПОВТОРНОЙ РЕГИСТРАЦИИ ---

    // Найти пользователя по логину (нужно для проверки существования)
    suspend fun findUserByLogin(userLogin: String): UserRow? = dbQuery {
        Users.select { Users.login eq userLogin }
            .map {
                UserRow(
                    id = it[Users.id],
                    login = it[Users.login],
                    email = it[Users.email],
                    isVerified = it[Users.isVerified]
                )
            }
            .singleOrNull()
    }

    // Обновить код верификации для существующего пользователя
    suspend fun updateVerificationCode(userLogin: String, newCode: String): Boolean = dbQuery {
        Users.update({ Users.login eq userLogin }) {
            it[verificationCode] = newCode
        } > 0
    }

}