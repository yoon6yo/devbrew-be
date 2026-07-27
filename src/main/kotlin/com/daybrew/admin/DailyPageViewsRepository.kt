package com.daybrew.admin

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyPageViewsRepository : JpaRepository<DailyPageViews, Long> {

    fun findByViewDate(date: LocalDate): DailyPageViews?

    fun findTop7ByOrderByViewDateDesc(): List<DailyPageViews>
}
