CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    login VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS quizzes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    prompt TEXT,
    created_by BIGINT NOT NULL,
    has_material BOOLEAN NOT NULL DEFAULT FALSE,
    material_url VARCHAR(255),
    question_number INTEGER,
    time_per_question_seconds INTEGER,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    is_static BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    default_question_type VARCHAR(32),
    CONSTRAINT quizzes_created_by_fk
        FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT quizzes_default_question_type_check
        CHECK (default_question_type IS NULL OR default_question_type IN (
            'SINGLE_CHOICE',
            'MULTIPLE_CHOICE',
            'HUNDRED_TO_ONE',
            'TEXT',
            'TRUE_FALSE'
        ))
);

CREATE TABLE IF NOT EXISTS questions (
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    text TEXT,
    type VARCHAR(32) NOT NULL,
    explanation TEXT,
    image BYTEA,
    generation_set_id BIGINT,
    CONSTRAINT questions_quiz_id_fk
        FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE,
    CONSTRAINT questions_type_check
        CHECK (type IN (
            'SINGLE_CHOICE',
            'MULTIPLE_CHOICE',
            'HUNDRED_TO_ONE',
            'TEXT',
            'TRUE_FALSE'
        ))
);

CREATE TABLE IF NOT EXISTS answer_options (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    text TEXT,
    is_correct BOOLEAN,
    nominal DECIMAL(10, 2),
    CONSTRAINT answer_options_question_id_fk
        FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_quiz_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE,
    finish_time TIMESTAMP WITH TIME ZONE,
    score BIGINT,
    base_score BIGINT,
    accuracy_percent INTEGER,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    session_id VARCHAR(100),
    current_question_id BIGINT,
    current_question_started_at TIMESTAMP WITH TIME ZONE,
    current_question_deadline_at TIMESTAMP WITH TIME ZONE,
    cat_stake INTEGER,
    cat_stake_bonus INTEGER,
    CONSTRAINT user_quiz_attempts_user_id_fk
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT user_quiz_attempts_quiz_id_fk
        FOREIGN KEY (quiz_id) REFERENCES quizzes(id)
);

CREATE TABLE IF NOT EXISTS user_answers (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    selected_answer_id BIGINT,
    is_correct BOOLEAN,
    accuracy_ratio DOUBLE PRECISION,
    CONSTRAINT user_answers_attempt_id_fk
        FOREIGN KEY (attempt_id) REFERENCES user_quiz_attempts(id) ON DELETE CASCADE,
    CONSTRAINT user_answers_question_id_fk
        FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT user_answers_selected_answer_id_fk
        FOREIGN KEY (selected_answer_id) REFERENCES answer_options(id)
);

CREATE TABLE IF NOT EXISTS attempt_questions (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_order INTEGER NOT NULL,
    CONSTRAINT attempt_questions_attempt_id_fk
        FOREIGN KEY (attempt_id) REFERENCES user_quiz_attempts(id) ON DELETE CASCADE,
    CONSTRAINT attempt_questions_question_id_fk
        FOREIGN KEY (question_id) REFERENCES questions(id)
);

CREATE TABLE IF NOT EXISTS multiplayer_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    quiz_id BIGINT NOT NULL,
    host_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    cat_question_index INTEGER,
    CONSTRAINT multiplayer_sessions_quiz_id_fk
        FOREIGN KEY (quiz_id) REFERENCES quizzes(id),
    CONSTRAINT multiplayer_sessions_host_user_id_fk
        FOREIGN KEY (host_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS generation_sets (
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    prompt TEXT,
    status VARCHAR(20) NOT NULL,
    generated_count INTEGER,
    valid_count INTEGER,
    duplicate_count INTEGER,
    final_count INTEGER,
    created_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT generation_sets_quiz_id_fk
        FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE,
    CONSTRAINT generation_sets_status_check
        CHECK (status IN (
            'GENERATING',
            'VALIDATING',
            'DEDUPLICATING',
            'READY',
            'FAILED'
        ))
);

CREATE INDEX IF NOT EXISTS idx_quizzes_created_by_created_at
    ON quizzes(created_by, created_at);

CREATE INDEX IF NOT EXISTS idx_quizzes_visibility_created_at
    ON quizzes(is_private, created_at);

CREATE INDEX IF NOT EXISTS idx_questions_quiz_id
    ON questions(quiz_id);

CREATE INDEX IF NOT EXISTS idx_answer_options_question_id
    ON answer_options(question_id);

CREATE INDEX IF NOT EXISTS idx_user_quiz_attempts_user_id
    ON user_quiz_attempts(user_id);

CREATE INDEX IF NOT EXISTS idx_user_quiz_attempts_quiz_completed_score
    ON user_quiz_attempts(quiz_id, is_completed, score, finish_time);

CREATE INDEX IF NOT EXISTS idx_user_quiz_attempts_session_id
    ON user_quiz_attempts(session_id);

CREATE INDEX IF NOT EXISTS idx_user_quiz_attempts_quiz_session_completed
    ON user_quiz_attempts(quiz_id, session_id, is_completed);

CREATE INDEX IF NOT EXISTS idx_user_quiz_attempts_user_quiz_session
    ON user_quiz_attempts(user_id, quiz_id, session_id);

CREATE INDEX IF NOT EXISTS idx_user_answers_attempt_id
    ON user_answers(attempt_id);

CREATE INDEX IF NOT EXISTS idx_user_answers_question_id
    ON user_answers(question_id);

CREATE INDEX IF NOT EXISTS idx_user_answers_selected_answer_id
    ON user_answers(selected_answer_id);

CREATE INDEX IF NOT EXISTS idx_attempt_questions_attempt_order
    ON attempt_questions(attempt_id, question_order);

CREATE INDEX IF NOT EXISTS idx_multiplayer_sessions_quiz_id
    ON multiplayer_sessions(quiz_id);

CREATE INDEX IF NOT EXISTS idx_multiplayer_sessions_host_user_id
    ON multiplayer_sessions(host_user_id);

CREATE INDEX IF NOT EXISTS idx_generation_sets_quiz_id
    ON generation_sets(quiz_id);

CREATE INDEX IF NOT EXISTS idx_generation_sets_status
    ON generation_sets(status);
