# Machine-specific settings live in a local .env file (gitignored), e.g.:
#   OLLAMA_BASE_URL=http://192.168.1.5:11434
# Every variable defined there is exported to docker compose and Quarkus.
-include .env
export

.PHONY: up dev test clean

up:        ## start Postgres (pgvector/pgvector:pg16) on localhost:5433
	docker compose up -d --wait

dev: up    ## start Quarkus dev mode on :8080 (live reload + dev seed ingestion)
	./mvnw quarkus:dev

test:      ## full offline build + test suite (WireMock + Testcontainers)
	./mvnw verify

clean:     ## remove build output and stop the compose stack
	./mvnw clean
	docker compose down
