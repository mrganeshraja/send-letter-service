package uk.gov.hmcts.reform.sendletter.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.sendletter.exception.ConnectionException;
import uk.gov.hmcts.reform.sendletter.model.Letter;
import uk.gov.hmcts.reform.sendletter.services.AuthChecker;
import uk.gov.hmcts.reform.sendletter.services.LetterService;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest
public class SendLetterControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private LetterService letterService;
    @MockBean private AuthTokenValidator tokenValidator;
    @MockBean private AuthChecker authChecker;


    @Test
    public void should_return_message_id_when_letter_is_successfully_sent() throws Exception {
        UUID letterId = UUID.randomUUID();

        given(tokenValidator.getServiceName("auth-header-value")).willReturn("service-name");
        given(letterService.send(any(Letter.class), anyString())).willReturn(letterId);

        sendLetter(readResource("letter.json"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(letterId.toString())));

        verify(tokenValidator).getServiceName("auth-header-value");
        verify(letterService).send(any(Letter.class), eq("service-name"));
        verifyNoMoreInteractions(tokenValidator, letterService);
    }

    @Test
    public void should_return_connection_exception_when_service_fails_due_to_service_bus() throws Exception {
        given(tokenValidator.getServiceName("auth-header-value")).willReturn("service-name");
        given(letterService.send(any(Letter.class), anyString()))
            .willThrow(
                new ConnectionException("Unable to connect to Azure service bus",
                    new ServiceBusException(false))
            );

        MvcResult mvcResult = sendLetter(readResource("letter.json"))
            .andExpect(status().is5xxServerError())
            .andReturn();

        assertThat(mvcResult.getResolvedException()).isInstanceOf(ConnectionException.class);
        assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Unable to connect to Azure service bus");
        assertThat(mvcResult.getResolvedException().getCause()).isInstanceOf(ServiceBusException.class);

        verify(tokenValidator).getServiceName("auth-header-value");
        verify(letterService).send(any(Letter.class), anyString());
        verifyNoMoreInteractions(tokenValidator, letterService);
    }

    @Test
    public void should_return_connection_exception_when_service_fails_due_to_thread_interruption() throws Exception {
        given(tokenValidator.getServiceName("auth-header-value")).willReturn("service-name");
        given(letterService.send(any(Letter.class), anyString()))
            .willThrow(
                new ConnectionException("Unable to connect to Azure service bus",
                    new InterruptedException())
            );

        MvcResult mvcResult = sendLetter(readResource("letter.json"))
            .andExpect(status().is5xxServerError())
            .andReturn();

        assertThat(mvcResult.getResolvedException()).isInstanceOf(ConnectionException.class);
        assertThat(mvcResult.getResolvedException().getMessage()).isEqualTo("Unable to connect to Azure service bus");
        assertThat(mvcResult.getResolvedException().getCause()).isInstanceOf(InterruptedException.class);


        verify(tokenValidator).getServiceName("auth-header-value");
        verify(letterService).send(any(Letter.class), anyString());
        verifyNoMoreInteractions(tokenValidator, letterService);
    }

    @Test
    public void should_return_json_processing_exception_when_service_fails_to_serialize_letter() throws Exception {
        given(tokenValidator.getServiceName("auth-header-value")).willReturn("service-name");
        willThrow(JsonProcessingException.class).given(letterService).send(any(Letter.class), anyString());

        sendLetter(readResource("letter.json"))
            .andExpect(status().is4xxClientError())
            .andExpect(content().string(
                containsString("Exception occured while parsing letter contents")));


        verify(tokenValidator).getServiceName("auth-header-value");
        verifyNoMoreInteractions(tokenValidator);

    }

    @Test
    public void should_return_400_client_error_when_invalid_letter_is_sent() throws Exception {
        sendLetter("").andExpect(status().is4xxClientError());

        verify(letterService, never()).send(any(Letter.class), anyString());
    }

    @Test
    public void should_return_400_client_error_when_letter_is_sent_without_documents() throws Exception {
        sendLetter(readResource("letter-without-doc.json"))
            .andExpect(status().is4xxClientError())
            .andExpect(content()
                .json("{\"errors\":[{\"field_name\":\"documents\",\"message\":\"size must be between 1 and 10\"}]}"));

        verify(letterService, never()).send(any(Letter.class), anyString());
    }

    @Test
    public void should_return_400_client_error_when_letter_is_sent_without_type() throws Exception {
        sendLetter(readResource("letter-without-type.json"))
            .andExpect(status().is4xxClientError())
            .andExpect(content()
                .json("{\"errors\":[{\"field_name\":\"type\",\"message\":\"may not be empty\"}]}"));

        verify(letterService, never()).send(any(Letter.class), anyString());
    }

    @Test
    public void should_return_400_client_error_when_letter_is_sent_without_template_in_document() throws Exception {
        sendLetter(readResource("letter-without-template.json"))
            .andExpect(status().is4xxClientError())
            .andExpect(content()
                .json("{\"errors\":[{\"field_name\":\"documents[0].template\",\"message\":\"may not be empty\"}]}"));

        verify(letterService, never()).send(any(Letter.class), anyString());
    }

    @Test
    public void should_return_400_client_error_when_letter_is_sent_without_template_values_in_document()
        throws Exception {
        sendLetter(readResource("letter-without-template-values.json"))
            .andExpect(status().is4xxClientError())
            .andExpect(content()
                .json("{\"errors\":[{\"field_name\":\"documents[0].values\",\"message\":\"may not be empty\"}]}"));

        verify(letterService, never()).send(any(Letter.class), anyString());
    }

    @Test
    public void should_return_400_client_error_when_letter_is_with_more_than_10_documents()
        throws Exception {
        sendLetter(readResource("letter-with-multiple-docs.json"))
            .andExpect(status().is4xxClientError())
            .andExpect(content()
                .json("{\"errors\":[{\"field_name\":\"documents\",\"message\":\"size must be between 1 and 10\"}]}"));

        verify(letterService, never()).send(any(Letter.class), anyString());
    }

    private ResultActions sendLetter(String json) throws Exception {
        return mockMvc.perform(
            post("/letters")
                .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE)
                .header("ServiceAuthorization", "auth-header-value")
                .content(json)
        );
    }

    private String readResource(final String fileName) throws IOException {
        return Resources.toString(Resources.getResource(fileName), Charsets.UTF_8);
    }
}
