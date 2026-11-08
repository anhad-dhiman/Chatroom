-- Every new user is automatically added to this room on sign-up.
INSERT INTO conversations (type, name, description, created_at)
VALUES ('ROOM', 'general', 'Company-wide chatter. Everyone joins this room automatically.', CURRENT_TIMESTAMP);
