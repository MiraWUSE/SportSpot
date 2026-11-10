package com.mirawuse.sportspot.data.remote

import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/** Gson's default adapter coerces unknown strings to false, losing the API's three-state meaning. */
internal class BooleanJsonAdapter : TypeAdapter<Boolean>() {
    override fun read(reader: JsonReader): Boolean {
        if (reader.peek() != JsonToken.BOOLEAN) throw JsonParseException("Expected a JSON boolean")
        return reader.nextBoolean()
    }

    override fun write(
        writer: JsonWriter,
        value: Boolean?,
    ) {
        writer.value(value)
    }
}
