package com.leo.politicas_de_negocio.iaeditorflujo.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowAiEditOperationDto {

    private String type;
    @JsonAlias({"from_node_name", "fromNode", "sourceNodeName", "sourceNode"})
    private String fromNodeName;
    @JsonAlias({"to_node_name", "toNode", "targetNode"})
    private String toNodeName;
    @JsonAlias({"name", "activityName", "node_name", "activity_name"})
    private String nodeName;
    @JsonAlias({"target_node_name"})
    private String targetNodeName;
    private String condition;
    private String responsableTipo;
    private String responsibleTipo;
    private String responsableId;
    private String responsibleId;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonAnySetter
    public void addProperty(String key, Object value) {
        additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> any() {
        return additionalProperties;
    }

    public Object property(String key) {
        return additionalProperties.get(key);
    }
}
