package com.daybrew.admin

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "daily_page_views")
class DailyPageViews(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(unique = true, nullable = false)
    val viewDate: LocalDate,
    var count: Int = 0,
)
