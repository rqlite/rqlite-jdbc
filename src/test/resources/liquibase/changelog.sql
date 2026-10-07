--liquibase formatted sql

--changeset rqlite-test:1
CREATE TABLE lb_test (id INTEGER PRIMARY KEY, name TEXT);

--changeset rqlite-test:2
INSERT INTO lb_test (id, name) VALUES (1, 'liquibase');

--changeset rqlite-test:3
UPDATE lb_test SET name = 'updated' WHERE id = 1;
