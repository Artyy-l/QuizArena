CREATE TABLE users(
    id BIGSERIAL PRIMARY KEY,
    login VARCHAR(32) NOT NULL UNIQUE,
    password VARCHAR(64) NOT NULL
);

CREATE TABLE quizzes(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    prompt TEXT NOT NULL,
    created_by BIGINT NOT NULL,
    has_material BOOLEAN NOT NULL,
    material_url VARCHAR(255),
    question_number INTEGER,
    time_per_question_seconds INTEGER,
    is_private BOOLEAN NOT NULL,
    is_static BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quizzes_created_by_fk FOREIGN KEY(created_by) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE questions(
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    text TEXT NOT NULL,
    type VARCHAR(32) NOT NULL,
    explanation TEXT NOT NULL,
    image BYTEA,
    CONSTRAINT questions_quiz_id_fk FOREIGN KEY(quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
);

CREATE TABLE answer_options(
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    text TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    is_na_option BOOLEAN NOT NULL,
    CONSTRAINT answer_options_question_id_fk FOREIGN KEY(question_id) REFERENCES questions(id) ON DELETE CASCADE
);

CREATE TABLE user_quiz_attempts(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    finish_time TIMESTAMP WITH TIME ZONE,
    score BIGINT,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT user_quiz_attempts_user_id_fk FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT user_quiz_attempts_quiz_id_fk FOREIGN KEY(quiz_id) REFERENCES quizzes(id)
);
