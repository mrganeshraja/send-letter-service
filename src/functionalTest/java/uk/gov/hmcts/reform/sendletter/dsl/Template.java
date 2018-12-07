package uk.gov.hmcts.reform.sendletter.dsl;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

interface Template {

    default RequestSpecification getNewSpecification() {
        return RestAssured.given().relaxedHTTPSValidation();
    }
}
