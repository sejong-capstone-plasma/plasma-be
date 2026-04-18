package com.plasma.be.chat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class JsonTestHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static long readLong(String json, String fieldName) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(json);
        return node.get(fieldName).asLong();
    }
}
