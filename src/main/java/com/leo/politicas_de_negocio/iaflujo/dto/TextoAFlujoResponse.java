package com.leo.politicas_de_negocio.iaflujo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TextoAFlujoResponse {
    private Policy policy;
    private List<Department> departments;
    private List<Role> roles;
    private List<Node> nodes;
    private List<Transition> transitions;
    private List<Form> forms;
    private List<BusinessRule> businessRules;
    private Analysis analysis;

    @Data
    public static class Policy {
        private String name;
        private String description;
        private String objective;
        private String version;
    }

    @Data
    public static class Department {
        private String id;
        private String name;
        private String description;
        private List<String> aliases;
    }

    @Data
    public static class Role {
        private String id;
        private String name;
        private String description;
    }

    @Data
    public static class Node {
        private String id;
        private String type;
        private String name;
        private String description;
        private String responsibleRoleId;
        private String formId;
        private String decisionCriteria;
        private String responsibleType;
        private String departmentHint;
    }

    @Data
    public static class Transition {
        private String id;
        @JsonProperty("from")
        private String from;
        private String to;
        private String label;
        private String condition;
    }

    @Data
    public static class Form {
        private String id;
        private String nodeId;
        private String name;
        private List<FormField> fields;
    }

    @Data
    public static class FormField {
        private String id;
        private String label;
        private String type;
        private boolean required;
        private List<Object> options;
    }

    @Data
    public static class BusinessRule {
        private String id;
        private String name;
        private String description;
        private String appliesToNodeId;
        private String expression;
        private String severity;
    }

    @Data
    public static class Analysis {
        private String summary;
        private List<String> assumptions;
        private List<String> warnings;
        private String complexity;
    }
}
