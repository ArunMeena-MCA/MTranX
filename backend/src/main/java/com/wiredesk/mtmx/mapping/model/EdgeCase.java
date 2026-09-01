package com.wiredesk.mtmx.mapping.model;

public class EdgeCase {
    private String condition;
    private String handling;

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getHandling() {
        return handling;
    }

    public void setHandling(String handling) {
        this.handling = handling;
    }

    @Override
    public String toString() {
        return "{condition='" + condition + "', handling='" + handling + "'}";
    }
}
