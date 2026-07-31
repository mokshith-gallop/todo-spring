package com.todo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Priority {
    @JsonProperty("none") NONE,
    @JsonProperty("low")  LOW,
    @JsonProperty("med")  MED,
    @JsonProperty("high") HIGH;
}
