.PHONY: up dev test clean

up:
	docker compose up -d --wait

dev: up
	./mvnw quarkus:dev

test:
	./mvnw verify

clean:
	./mvnw clean
	docker compose down
