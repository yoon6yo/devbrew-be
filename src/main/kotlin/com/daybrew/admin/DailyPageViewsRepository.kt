package com.daybrew.admin

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface DailyPageViewsRepository : JpaRepository<DailyPageViews, Long> {

    @Modifying
    @Query(
        value = """
            INSERT INTO daily_page_views (view_date, count)
            VALUES (:date, 1)
            ON CONFLICT (view_date) DO UPDATE SET count = daily_page_views.count + 1
        """,
        nativeQuery = true,
    )
    fun upsertIncrement(date: LocalDate)

    fun findTop7ByOrderByViewDateDesc(): List<DailyPageViews>
}
