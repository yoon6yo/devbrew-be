package com.daybrew.admin

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface DailyPageViewsRepository : JpaRepository<DailyPageViews, Long> {

    fun findByViewDate(date: LocalDate): DailyPageViews?

    @Modifying
    @Query("UPDATE DailyPageViews d SET d.count = d.count + 1 WHERE d.viewDate = :date")
    fun incrementCount(@Param("date") date: LocalDate): Int

    fun findTop7ByOrderByViewDateDesc(): List<DailyPageViews>
}
