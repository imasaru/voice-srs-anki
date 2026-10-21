package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelListTest {
    @Test
    fun `reads data ids and drops blanks and duplicates`() {
        val body = """
            {"object":"list","data":[
              {"id":"mlx-community/Qwen3.6-35B-A3B-OptiQ-4bit","object":"model"},
              {"id":"  "},
              {"id":"mlx-community/Qwen3.6-35B-A3B-OptiQ-4bit:no-think"},
              {"id":"mlx-community/Qwen3.6-35B-A3B-OptiQ-4bit"},
              "skip-me"
            ]}
        """.trimIndent()
        assertEquals(
            listOf(
                "mlx-community/Qwen3.6-35B-A3B-OptiQ-4bit",
                "mlx-community/Qwen3.6-35B-A3B-OptiQ-4bit:no-think"
            ),
            ModelList.parse(body)
        )
    }

    @Test
    fun `garbage and a missing data array are empty`() {
        assertTrue(ModelList.parse("not-json").isEmpty())
        assertTrue(ModelList.parse("{\"object\":\"list\"}").isEmpty())
        assertTrue(ModelList.parse("{\"data\":[]}").isEmpty())
    }
}
