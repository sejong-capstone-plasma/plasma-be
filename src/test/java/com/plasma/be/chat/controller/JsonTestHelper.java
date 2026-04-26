package com.plasma.be.chat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class JsonTestHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static long readLong(String json, String fieldName) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(json);
        JsonNode current = node;
        for (String token : fieldName.split("\\.")) {
            if (token.contains("[")) {
                String name = token.substring(0, token.indexOf('['));
                int index = Integer.parseInt(token.substring(token.indexOf('[') + 1, token.indexOf(']')));
                current = current.get(name).get(index);
            } else {
                current = current.get(token);
            }
        }
        return current.asLong();
    }
}
