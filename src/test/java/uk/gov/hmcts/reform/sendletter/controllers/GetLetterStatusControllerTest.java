package uk.gov.hmcts.reform.sendletter.controllers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.hmcts.reform.authorisation.exceptions.InvalidTokenException;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.sendletter.domain.LetterStatus;
import uk.gov.hmcts.reform.sendletter.exception.LetterNotFoundException;
import uk.gov.hmcts.reform.sendletter.services.AuthChecker;
import uk.gov.hmcts.reform.sendletter.services.LetterService;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest
public class GetLetterStatusControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private LetterService service;
    @MockBean private AuthTokenValidator tokenValidator;
    @MockBean private AuthChecker authChecker;

    private UUID letterId;

    @Before
    public void setUp() {
        letterId = UUID.randomUUID();
    }

    @Test
    public void should_return_letter_status_when_it_is_found_in_database() throws Exception {
        given(tokenValidator.getServiceName("auth-header-value")).willReturn("service-name");
        given(service.getStatus(letterId, "service-name")).willReturn(new LetterStatus(letterId));

        getLetter(letterId)
            .andExpect(status().isOk())
            .andExpect(content().json(
                "{"
                    + "\"id\":\"" + letterId.toString() + "\""
                    + "}"
            ));
    }

    @Test
    public void should_return_404_client_error_when_letter_is_not_found_in_database() throws Exception {
        given(tokenValidator.getServiceName("auth-header-value")).willReturn("service-name");
        willThrow(LetterNotFoundException.class).given(service).getStatus(letterId, "service-name");

        getLetter(letterId).andExpect(status().is(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    public void should_return_404_client_error_when_invalid_uuid_is_provided() throws Exception {
        getLetter("0987654321").andExpect(status().is(HttpStatus.NOT_FOUND.value()));
        getLetter("X558ff55-37R0-4p6e-80fo-5Lb05b650c44").andExpect(status().is(HttpStatus.NOT_FOUND.value()));

        verify(tokenValidator, never()).getServiceName(anyString());
        verify(service, never()).getStatus(any(UUID.class), anyString());
    }

    @Test
    public void should_return_401_client_error_when_authorisation_header_is_invalid() throws Exception {
        willThrow(InvalidTokenException.class).given(tokenValidator).getServiceName("auth-header-value");

        getLetter(letterId).andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));

        verify(service, never()).getStatus(any(UUID.class), anyString());
    }

    private ResultActions getLetter(String letterId) throws Exception {
        return mockMvc.perform(
            get("/letters/" + letterId)
                .header("ServiceAuthorization", "auth-header-value")
        );
    }

    private ResultActions getLetter(UUID letterId) throws Exception {
        return getLetter(letterId.toString());
    }
}
