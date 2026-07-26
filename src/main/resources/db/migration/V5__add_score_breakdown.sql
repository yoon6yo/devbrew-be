ALTER TABLE idea
    ADD COLUMN score_market_fit  SMALLINT,
    ADD COLUMN score_novelty      SMALLINT,
    ADD COLUMN score_feasibility  SMALLINT,
    ADD COLUMN score_monetization SMALLINT,
    ADD COLUMN score_trend        SMALLINT;
