package uk.gov.hmcts.reform.sendletter.controllers;

import com.google.common.io.Resources;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.io.Resources.getResource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ComponentScan(basePackages = "...", lazyInit = true)
@ContextConfiguration
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class SendLetterWithPdfTest {

    @Autowired
    private MockMvc mvc;

    @Test
    public void should_return_200_when_valid_json_is_sent() throws Exception {
        String json = Resources.toString(getResource("letter-with-pdf.json"), UTF_8);

        mvc
            .perform(
                post("/letters")
                    .header("ServiceAuthorization", "auth-header-value")
                    .contentType(MediaTypes.LETTER_V2)
                    .content(json)
            )
            .andExpect(
                status().isOk()
            );
    }

    @Test
    public void should_return_400_when_invalid_pdf_field_is_sent() throws Exception {
        String json = Resources.toString(getResource("letter-with-invalid-pdf.json"), UTF_8);

        mvc
            .perform(
                post("/letters")
                    .header("ServiceAuthorization", "auth-header-value")
                    .contentType(MediaTypes.LETTER_V2)
                    .content(json)
            )
            .andExpect(
                status().isBadRequest()
            );
    }

    @Test
    public void should_return_400_when_not_pdf_field_is_sent() throws Exception {
        String json = Resources.toString(getResource("letter-with-not-pdf.json"), UTF_8);

        mvc
            .perform(
                post("/letters")
                    .header("ServiceAuthorization", "auth-header-value")
                    .contentType(MediaTypes.LETTER_V2)
                    .content(json)
            )
            .andExpect(
                status().isBadRequest()
            );
    }
}
