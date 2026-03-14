CREATE TABLE teams (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL UNIQUE,
    slug       VARCHAR(50)  NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
