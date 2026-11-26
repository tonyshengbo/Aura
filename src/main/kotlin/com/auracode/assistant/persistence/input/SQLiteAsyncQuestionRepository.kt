package com.auracode.assistant.persistence.input

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/** Keeps question resolution durable without changing existing session or attachment schemas. */
internal class SQLiteAsyncQuestionRepository(private val dbPath: Path) : AsyncQuestionRepository {
    init {
        Class.forName("org.sqlite.JDBC")
        dbPath.parent?.let(java.nio.file.Files::createDirectories)
        connection { db ->
            db.createStatement().use { statement ->
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS async_questions (
                        session_id TEXT NOT NULL, thread_id TEXT NOT NULL, request_id TEXT NOT NULL,
                        request_json TEXT NOT NULL, status TEXT, response_summary TEXT,
                        PRIMARY KEY (session_id, thread_id, request_id)
                    )
                """.trimIndent())
            }
        }
    }

    override fun saveAsyncQuestion(question: PersistedAsyncQuestion) = connection { db ->
        db.prepareStatement("""
            INSERT INTO async_questions (session_id, thread_id, request_id, request_json, status, response_summary)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(session_id, thread_id, request_id) DO UPDATE SET
                request_json = excluded.request_json,
                status = COALESCE(excluded.status, async_questions.status),
                response_summary = COALESCE(excluded.response_summary, async_questions.response_summary)
        """.trimIndent()).use { statement ->
            statement.setString(1, question.sessionId)
            statement.setString(2, question.threadId)
            statement.setString(3, question.requestId)
            statement.setString(4, question.requestJson)
            statement.setString(5, question.status)
            statement.setString(6, question.responseSummary)
            statement.executeUpdate()
            Unit
        }
    }

    override fun loadAsyncQuestions(sessionId: String, threadId: String) = connection { db ->
        db.prepareStatement("SELECT * FROM async_questions WHERE session_id = ? AND thread_id = ? ORDER BY rowid").use { statement ->
            statement.setString(1, sessionId)
            statement.setString(2, threadId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(PersistedAsyncQuestion(
                        sessionId, threadId, rows.getString("request_id"), rows.getString("request_json"),
                        rows.getString("status"), rows.getString("response_summary"),
                    ))
                }
            }
        }
    }

    override fun deleteAsyncQuestions(sessionId: String) = connection { db ->
        db.prepareStatement("DELETE FROM async_questions WHERE session_id = ?").use { statement ->
            statement.setString(1, sessionId)
            statement.executeUpdate()
            Unit
        }
    }

    private fun <T> connection(action: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { db ->
            db.createStatement().use { it.execute("PRAGMA busy_timeout = 5000") }
            action(db)
        }
}
