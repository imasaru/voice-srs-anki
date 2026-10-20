package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

class GradeMapperTest {
    @Test
    fun `grade words map to eases`() {
        assertEquals(1, GradeMapper.wordToEase("again"))
        assertEquals(2, GradeMapper.wordToEase("hard"))
        assertEquals(3, GradeMapper.wordToEase("good"))
        assertEquals(4, GradeMapper.wordToEase("easy"))
    }

    @Test
    fun `matching is fuzzy contains`() {
        assertEquals(3, GradeMapper.wordToEase("that was good"))
        assertEquals(1, GradeMapper.wordToEase("say that again please"))
    }

    @Test
    fun `non grade words return null`() {
        assertNull(GradeMapper.wordToEase("hello there"))
        assertNull(GradeMapper.wordToEase(""))
    }

    @Test
    fun `canonical grade word is an exact match`() {
        assertEquals("again", GradeMapper.canonicalGradeWord("again"))
        assertEquals("hard", GradeMapper.canonicalGradeWord(" HARD "))
        assertEquals("good", GradeMapper.canonicalGradeWord("Good"))
        assertEquals("easy", GradeMapper.canonicalGradeWord("easy"))
        assertNull(GradeMapper.canonicalGradeWord("goodbye"))
        assertNull(GradeMapper.canonicalGradeWord("repeat"))
        assertNull(GradeMapper.canonicalGradeWord("that was good"))
        assertNull(GradeMapper.canonicalGradeWord(""))
        assertNull(GradeMapper.canonicalGradeWord(null))
    }

    @Test
    fun `repeat is detected separately`() {
        assertTrue(GradeMapper.isRepeat("repeat that"))
        assertNull(GradeMapper.wordToEase("repeat that"))
    }

    @Test
    fun `ease passes through when offered`() {
        assertEquals(4, GradeMapper.toAvailableEase(4, listOf(1, 2, 3, 4)))
        assertEquals(1, GradeMapper.toAvailableEase(1, listOf(1, 2, 3)))
    }

    @Test
    fun `easy falls back to closest button`() {
        // New/learning card offering only [1,2,3]: "easy" -> 3
        assertEquals(3, GradeMapper.toAvailableEase(4, listOf(1, 2, 3)))
    }

    @Test
    fun `again falls back to closest button`() {
        assertEquals(2, GradeMapper.toAvailableEase(1, listOf(2, 3, 4)))
    }

    @Test
    fun `empty buttons returns clamped ease`() {
        assertEquals(3, GradeMapper.toAvailableEase(3, emptyList()))
    }
}
