-- you will need a row defining the one tag, in order to import
-- the sample tables.

INSERT INTO tag (id, tag, description) VALUES (38, 'cs181sy', 'Starter data set');

-- later you will need this to bootstrap the web site UI.

INSERT INTO token (id, token, tag, refcode, expiration, level)
VALUES (1, 'pF6ikYqbT79m', 38, '32769660', '2022-01-01', 4);

