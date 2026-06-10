package nz.co.ksktech.fundlens.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import nz.co.ksktech.fundlens.testsupport.WireMockTestResource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
class FundResourceTest {

  @Test
  void listsSeededFundsWithLatestMetrics() {
    given()
        .when()
        .get("/api/v1/funds")
        .then()
        .statusCode(200)
        .body("size()", greaterThanOrEqualTo(3))
        .body(
            "name", hasItems("Westpac Active Growth", "Milford Active Growth", "Simplicity Growth"))
        .body(
            "find { it.name == 'Simplicity Growth' }.latestMetrics.totalAnnualFundCharge",
            equalTo(0.29f))
        .body(
            "find { it.name == 'Westpac Active Growth' }.latestMetrics.pastYearReturnNet",
            equalTo(-4.10f))
        .body(
            "find { it.name == 'Westpac Active Growth' }.latestMetrics.periodEnd",
            equalTo("2026-03-31"))
        .body(
            "find { it.name == 'Milford Active Growth' }.latestMetrics.investmentMix",
            notNullValue());
  }

  @Test
  void getsSingleFundById() {
    Integer id =
        given()
            .when()
            .get("/api/v1/funds")
            .then()
            .statusCode(200)
            .extract()
            .path("find { it.name == 'Westpac Active Growth' }.id");

    given()
        .when()
        .get("/api/v1/funds/{id}", id)
        .then()
        .statusCode(200)
        .body("name", equalTo("Westpac Active Growth"))
        .body("provider", equalTo("Westpac"))
        .body("riskIndicator", equalTo(4))
        .body("latestMetrics.totalAnnualFundCharge", equalTo(1.05f));
  }

  @Test
  void unknownFundIsProblemDetail404() {
    given()
        .when()
        .get("/api/v1/funds/999999")
        .then()
        .statusCode(404)
        .body("title", equalTo("Not found"))
        .body("status", equalTo(404));
  }

  @Test
  void blankQuestionIsProblemDetail400() {
    given()
        .contentType("application/json")
        .body("{\"question\":\"  \"}")
        .when()
        .post("/api/v1/explain")
        .then()
        .statusCode(400)
        .body("title", equalTo("Validation failed"))
        .body("status", equalTo(400));
  }
}
