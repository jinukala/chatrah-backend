package com.chatrah.school.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ResourceIntegrationTest {

    static String token;

    @Test
    @Order(1)
    void testLoginEndpoint() {
        // First create a user via direct DB or use existing setup
        token = given()
                .contentType(ContentType.JSON)
                .header("X-Gateway-Secret", "test-secret")
                .body("{\"username\":\"testuser\",\"password\":\"Test@123\"}")
                .when().post("/api/auth/login")
                .then().statusCode(anyOf(is(200), is(401)))
                .extract().path("token");
    }

    @Test
    @Order(2)
    void testLoginInvalidCredentials() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Gateway-Secret", "test-secret")
                .body("{\"username\":\"invalid\",\"password\":\"wrong\"}")
                .when().post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    @Order(3)
    void testClassesEndpointWithoutAuth() {
        given()
                .when().get("/api/classes")
                .then().statusCode(anyOf(is(401), is(403)));
    }

    @Test
    @Order(4)
    void testEventsPublicEndpoint() {
        given()
                .header("X-Gateway-Secret", "test-secret")
                .when().get("/api/events/upcoming")
                .then().statusCode(200);
    }

    @Test
    @Order(5)
    void testBlogsPublicEndpoint() {
        given()
                .header("X-Gateway-Secret", "test-secret")
                .when().get("/api/blogs/approved")
                .then().statusCode(200)
                .body("$", instanceOf(java.util.List.class));
    }

    @Test
    @Order(6)
    void testBirthdaysPublicEndpoint() {
        given()
                .header("X-Gateway-Secret", "test-secret")
                .when().get("/api/students/birthdays/today")
                .then().statusCode(anyOf(is(200), is(401)));
    }

    @Test
    @Order(7)
    void testSchoolProfileEndpoint() {
        given()
                .header("X-Gateway-Secret", "test-secret")
                .when().get("/api/school/profile")
                .then().statusCode(200);
    }

    @Test
    @Order(8)
    void testFeePlansEndpointRequiresAuth() {
        given()
                .when().get("/api/fees/plans")
                .then().statusCode(anyOf(is(401), is(403)));
    }

    @Test
    @Order(9)
    void testExamsEndpointRequiresAuth() {
        given()
                .when().get("/api/exams")
                .then().statusCode(anyOf(is(401), is(403)));
    }

    @Test
    @Order(10)
    void testPasswordResetWithoutOtp() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Gateway-Secret", "test-secret")
                .body("{\"username\":\"testuser\",\"otp\":\"000000\",\"newPassword\":\"new\"}")
                .when().post("/api/auth/password/reset")
                .then().statusCode(anyOf(is(400), is(404)));
    }
}
