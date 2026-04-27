package com.leo.politicas_de_negocio.formulariointeligente.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FormularioInteligenteRequestJsonTest {

    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();

    @Test
    void debeDeserializarAliasesComunesDelFrontendYServicioIa() throws Exception {
        String body = """
                {
                  "task_id": "task-123",
                  "task_name": "Revision documental",
                  "policy_name": "Licencia de funcionamiento",
                  "fields": [
                    {
                      "name": "decision",
                      "label": "Decision",
                      "field_type": "select",
                      "required": true,
                      "options": ["aprobado", "rechazado"]
                    }
                  ],
                  "values": {
                    "decision": "aprobado"
                  },
                  "prompt": "Completa el formulario"
                }
                """;

        FormularioInteligenteRequest request = JSON.readValue(body, FormularioInteligenteRequest.class);

        assertEquals("task-123", request.getActivityId());
        assertEquals("Revision documental", request.getActivityName());
        assertEquals("Licencia de funcionamiento", request.getPolicyName());
        assertEquals("Completa el formulario", request.getUserPrompt());
        assertNotNull(request.getCurrentValues());
        assertEquals("aprobado", request.getCurrentValues().get("decision"));
        assertNotNull(request.getFormSchema());
        assertEquals(1, request.getFormSchema().size());
        assertEquals("decision", request.getFormSchema().get(0).getId());
        assertEquals("select", request.getFormSchema().get(0).getType());
    }
}
