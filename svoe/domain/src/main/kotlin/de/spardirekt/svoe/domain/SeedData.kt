package de.spardirekt.svoe.domain

object SeedData {
    const val WATER_COLOR = 0xFF3D7EA6.toInt()
    const val WALK_COLOR = 0xFF3F6B4F.toInt()
    const val READ_COLOR = 0xFFC45C26.toInt()

    fun populated(clock: AppClock, prefs: Prefs): LifeState {
        val today = clock.today()
        val yesterday = today.minusDays(1)
        var state = LifeState(prefs = prefs)
        state = state.addTask("Купить продукты на неделю", "Молоко, хлеб, овощи", TaskPriority.HIGH, today.toEpochDay(), clock)
        state = state.addTask("Позвонить родителям", "", TaskPriority.NORMAL, today.toEpochDay(), clock)
        state = state.addTask("Разобрать фото с отпуска", "", TaskPriority.LOW, today.plusDays(4).toEpochDay(), clock)
        state = state.addHabit("Вода", "💧", WATER_COLOR, clock)
        state = state.addHabit("Прогулка", "🚶", WALK_COLOR, clock)
        state = state.addHabit("Чтение", "📖", READ_COLOR, clock)
        val water = state.habits[0].id
        val walk = state.habits[1].id
        val read = state.habits[2].id
        state = state.toggleHabit(water, today.toEpochDay())
        state = state.toggleHabit(water, yesterday.toEpochDay())
        state = state.toggleHabit(water, today.minusDays(2).toEpochDay())
        state = state.toggleHabit(walk, yesterday.toEpochDay())
        state = state.toggleHabit(read, today.toEpochDay())
        state = state.upsertJournal(
            epochDay = yesterday.toEpochDay(),
            mood = 4,
            body = "Спокойный вечер, много прогулялись и рано легли.",
            clock = clock,
        )
        state = state.addTx(MoneyKind.EXPENSE, 1240_00, SpendCategory.FOOD, "Магнит", today.toEpochDay(), clock)
        state = state.addTx(MoneyKind.EXPENSE, 390_00, SpendCategory.CAFE, "Кофе", yesterday.toEpochDay(), clock)
        state = state.addTx(MoneyKind.INCOME, 85_000_00, SpendCategory.OTHER, "Зарплата", today.withDayOfMonth(1).toEpochDay(), clock)
        return state
    }
}
