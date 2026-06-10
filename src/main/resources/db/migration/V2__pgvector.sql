-- Install the pgvector extension. The `embeddings` table itself is created by
-- quarkus-langchain4j-pgvector at startup (create-table=true, the default) —
-- Flyway deliberately does not create it so the schema always matches what the
-- extension expects. Documented in README.md.
CREATE EXTENSION IF NOT EXISTS vector;
