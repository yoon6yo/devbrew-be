CREATE TABLE daily_page_views (
    id        BIGSERIAL PRIMARY KEY,
    view_date DATE UNIQUE NOT NULL,
    count     INT NOT NULL DEFAULT 0
);
