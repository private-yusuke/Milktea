package net.pantasystem.milktea.data.infrastructure.notes.impl

import kotlinx.coroutines.runBlocking
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.model.notes.NoteDeletedException
import net.pantasystem.milktea.model.notes.NoteNotFoundException
import net.pantasystem.milktea.model.notes.make
import net.pantasystem.milktea.model.user.User
import org.junit.Assert
import org.junit.Test

class InMemoryNoteDataSourceTest {

    @Test
    fun get_ThrowsNoteDeletedExceptionGiveDeletedNote(): Unit = runBlocking {
        val noteDataSource = InMemoryNoteDataSource()
        val id = Note.Id(0L, "testId")
        noteDataSource.remove(id)
        val result = noteDataSource.get(id)
        Assert.assertNotNull(result.exceptionOrNull())
        Assert.assertThrows(NoteDeletedException::class.java) {
            result.getOrThrow()
        }
    }

    @Test
    fun get_ThrowsNoteNotFoundExceptionGiveNotExistsNote(): Unit = runBlocking {
        val noteDataSource = InMemoryNoteDataSource()
        val id = Note.Id(0L, "testId")
        val result = noteDataSource.get(id)
        Assert.assertThrows(NoteNotFoundException::class.java) {
            result.getOrThrow()
        }
    }

    @Test
    fun get_ReturnsNoteGiveExistsNote(): Unit = runBlocking {
        val noteDataSource = InMemoryNoteDataSource()
        val id = Note.Id(0L, "testId")
        val testNote = Note.make(id, User.Id(0L, "testUserId"))
        noteDataSource.add(testNote)
        val result = noteDataSource.get(id)
        Assert.assertEquals(testNote, result.getOrThrow())
    }
}