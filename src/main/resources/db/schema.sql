CREATE TABLE person (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    age INTEGER NOT NULL
);

CREATE TABLE documents (
   id SERIAL PRIMARY KEY,
   name VARCHAR(255) NOT NULL,
   person INTEGER REFERENCES person(id) ON DELETE CASCADE,
   data BYTEA NOT NULL
);