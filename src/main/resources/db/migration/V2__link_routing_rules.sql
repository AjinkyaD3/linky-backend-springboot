-- Conditional redirects (device / os / country rules), stored as a JSON array.
ALTER TABLE urls ADD COLUMN routing_rules TEXT;
