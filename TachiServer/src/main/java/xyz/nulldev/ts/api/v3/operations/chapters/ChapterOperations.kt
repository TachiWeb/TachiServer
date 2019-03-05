package xyz.nulldev.ts.api.v3.operations.chapters

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory
import xyz.nulldev.ts.api.v2.java.impl.util.getChapters
import xyz.nulldev.ts.api.v3.OperationGroup
import xyz.nulldev.ts.api.v3.models.chapters.WChapter
import xyz.nulldev.ts.api.v3.models.chapters.WChapterReadingStatus
import xyz.nulldev.ts.api.v3.op
import xyz.nulldev.ts.api.v3.opWithParams
import xyz.nulldev.ts.api.v3.util.await
import xyz.nulldev.ts.ext.kInstanceLazy

private const val CHAPTER_ID_PARAM = "chapterId"

class ChapterOperations : OperationGroup {
    private val db: DatabaseHelper by kInstanceLazy()

    override fun register(routerFactory: OpenAPI3RouterFactory) {
        routerFactory.op(::getChapters.name, ::getChapters)
        routerFactory.opWithParams(::getChapterByChapterId.name, CHAPTER_ID_PARAM, ::getChapterByChapterId)
        routerFactory.opWithParams(::getReadingStatusOfChapterByChapterId.name, CHAPTER_ID_PARAM, ::getReadingStatusOfChapterByChapterId)
        routerFactory.opWithParams(::putReadingStatusOfChapterByChapterId.name, CHAPTER_ID_PARAM, ::putReadingStatusOfChapterByChapterId)
        routerFactory.opWithParams(::deleteLastReadFromReadingStatusOfChapterByChapterId.name, CHAPTER_ID_PARAM, ::deleteLastReadFromReadingStatusOfChapterByChapterId)
    }

    suspend fun getChapters(): List<WChapter> {
        return db.getChapters().await().map { it.asWeb() }
    }

    suspend fun getChapterByChapterId(chapterId: Long): WChapter {
        return db.getChapter(chapterId).await()?.asWeb() ?: notFound()
    }

    suspend fun getReadingStatusOfChapterByChapterId(chapterId: Long): WChapterReadingStatus {
        return db.getChapter(chapterId).await()?.readingStatusAsWeb() ?: notFound()
    }

    suspend fun putReadingStatusOfChapterByChapterId(chapterId: Long, wChapterReadingStatus: WChapterReadingStatus): WChapterReadingStatus {
        val existingChapter = db.getChapter(chapterId).await() ?: notFound()

        existingChapter.last_page_read = wChapterReadingStatus.lastPageRead.toInt()
        existingChapter.read = wChapterReadingStatus.read

        val history = if (wChapterReadingStatus.lastRead != null) {
            val history = db.getHistoryByChapterUrl(existingChapter.url).await() ?: History.create(existingChapter)
            history.last_read = wChapterReadingStatus.lastRead
            db.updateHistoryLastRead(history).await()
            history
        } else null

        return existingChapter.readingStatusAsWeb(history)
    }

    suspend fun deleteLastReadFromReadingStatusOfChapterByChapterId(chapterId: Long): WChapterReadingStatus {
        val existingChapter = db.getChapter(chapterId).await() ?: notFound()
        val history = db.getHistoryByChapterUrl(existingChapter.url).await()
        if (history != null) {
            history.last_read = 0
            db.updateHistoryLastRead(history).await()
        }

        return WChapterReadingStatus(existingChapter.last_page_read.toLong(), null, existingChapter.read)
    }

    suspend fun Chapter.asWeb() = WChapter(
            bookmark,
            chapter_number,
            date_fetch,
            date_upload,
            id!!,
            manga_id!!,
            name,
            readingStatusAsWeb(),
            scanlator,
            source_order.toLong(),
            url
    )

    suspend fun Chapter.readingStatusAsWeb(history: History? = null) = WChapterReadingStatus(
            last_page_read.toLong(),
            (history ?: db.getHistoryByChapterUrl(url).await())?.last_read.let {
                if (it == 0L) null else it
            },
            read
    )
}