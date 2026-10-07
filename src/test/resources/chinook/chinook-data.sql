-- Trimmed Chinook seed data (SQLite) for driver regression testing.
-- A handful of rows per table: enough for joins, aggregates, composite keys,
-- self-referential FKs, NULL handling and DATETIME/NUMERIC round-trips.

INSERT INTO [Artist] ([ArtistId], [Name]) VALUES
    (1, 'AC/DC'),
    (2, 'Accept'),
    (3, 'Aerosmith');

INSERT INTO [Album] ([AlbumId], [Title], [ArtistId]) VALUES
    (1, 'For Those About To Rock We Salute You', 1),
    (2, 'Balls to the Wall', 2),
    (3, 'Restless and Wild', 2),
    (4, 'Let There Be Rock', 1);

INSERT INTO [Genre] ([GenreId], [Name]) VALUES
    (1, 'Rock'),
    (2, 'Jazz'),
    (3, 'Metal');

INSERT INTO [MediaType] ([MediaTypeId], [Name]) VALUES
    (1, 'MPEG audio file'),
    (2, 'Protected AAC audio file');

INSERT INTO [Employee] ([EmployeeId], [LastName], [FirstName], [Title], [ReportsTo], [BirthDate], [HireDate], [City], [Country], [Email]) VALUES
    (1, 'Adams', 'Andrew', 'General Manager', NULL, '1962-02-18 00:00:00', '2002-08-14 00:00:00', 'Calgary', 'Canada', 'andrew@chinookcorp.com'),
    (2, 'Edwards', 'Nancy', 'Sales Manager', 1, '1958-12-08 00:00:00', '2002-05-01 00:00:00', 'Calgary', 'Canada', 'nancy@chinookcorp.com'),
    (3, 'Peacock', 'Jane', 'Sales Support Agent', 2, '1973-08-29 00:00:00', '2002-04-01 00:00:00', 'Calgary', 'Canada', 'jane@chinookcorp.com');

INSERT INTO [Customer] ([CustomerId], [FirstName], [LastName], [Company], [City], [Country], [Email], [SupportRepId]) VALUES
    (1, 'Luís', 'Gonçalves', 'Embraer', 'São Paulo', 'Brazil', 'luisg@embraer.com.br', 3),
    (2, 'Leonie', 'Köhler', NULL, 'Stuttgart', 'Germany', 'leonekohler@surfeu.de', 2),
    (3, 'François', 'Tremblay', NULL, 'Montréal', 'Canada', 'ftremblay@gmail.com', NULL);

INSERT INTO [Track] ([TrackId], [Name], [AlbumId], [MediaTypeId], [GenreId], [Composer], [Milliseconds], [Bytes], [UnitPrice]) VALUES
    (1, 'For Those About To Rock (We Salute You)', 1, 1, 1, 'Angus Young, Malcolm Young, Brian Johnson', 343719, 11170334, 0.99),
    (2, 'Balls to the Wall', 2, 2, 1, 'U. Dirkschneider, W. Hoffmann, H. Frank', 342562, 5510424, 0.99),
    (3, 'Fast As a Shark', 3, 2, 1, 'F. Baltes, S. Kaufman, U. Dirkscneider', 230619, 3990994, 0.99),
    (4, 'Let There Be Rock', 4, 1, 1, 'AC/DC', 366654, 12021261, 0.99),
    (5, 'Desafinado', 2, 1, 2, NULL, 185338, NULL, 0.99),
    (6, 'Enter Sandman', 1, 1, 3, 'Apocalyptica', 221701, 7286305, 1.29);

INSERT INTO [Playlist] ([PlaylistId], [Name]) VALUES
    (1, 'Music'),
    (2, 'Movies');

INSERT INTO [PlaylistTrack] ([PlaylistId], [TrackId]) VALUES
    (1, 1),
    (1, 2),
    (1, 3),
    (2, 1),
    (2, 6);

INSERT INTO [Invoice] ([InvoiceId], [CustomerId], [InvoiceDate], [BillingAddress], [BillingCity], [BillingCountry], [Total]) VALUES
    (1, 1, '2009-01-01 00:00:00', 'Av. Brigadeiro Faria Lima, 2170', 'São Paulo', 'Brazil', 1.98),
    (2, 2, '2009-02-15 00:00:00', 'Theodor-Heuss-Straße 34', 'Stuttgart', 'Germany', 3.96);

INSERT INTO [InvoiceLine] ([InvoiceLineId], [InvoiceId], [TrackId], [UnitPrice], [Quantity]) VALUES
    (1, 1, 1, 0.99, 1),
    (2, 1, 2, 0.99, 1),
    (3, 2, 3, 0.99, 2),
    (4, 2, 6, 1.29, 2);
