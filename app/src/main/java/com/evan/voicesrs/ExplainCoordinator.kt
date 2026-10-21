package com.evan.voicesrs

/**
 * Per drill session: clear the review log, keep note ids, and enqueue work
 * only after a card is graded. Lookahead never blocks the microphone;
 * the network prefetch of the due list runs on the session thread, and the
 * model call runs on [ExplainQueue]'s background thread.
 * Explanations are never spoken.
 */
class ExplainSession private constructor(
    private val context: android.content.Context,
    private val cfg: AnkiConfig
) {
    private val notes = HashMap<Long, OfflineCard>()
    private val missing = HashSet<Long>()
    private var ahead: List<OfflineCard> = emptyList()
    private var sinceRefresh = LookaheadWindow.REFRESH_EVERY
    private val seen = HashSet<Long>()

    fun recordOnline(
        anki: AnkiConnect,
        cardId: Long,
        frontHtml: String,
        backHtml: String,
        transcript: String,
        ease: Int
    ) {
        val info = resolve(anki, cardId)
        record(
            cardId,
            info?.noteId,
            info?.front ?: frontHtml,
            info?.back ?: backHtml,
            transcript,
            ease
        )
        seen += cardId
    }

    fun recordOffline(card: OfflineCard, transcript: String, ease: Int) {
        notes[card.cardId] = card
        record(card.cardId, card.noteId, card.front, card.back, transcript, ease)
        seen += card.cardId
    }

    fun prefetchOffline(batch: List<OfflineCard>, index: Int) {
        if (!cfg.aiEnabled) return
        val cards = batch.map { AheadCard(it.cardId, it.noteId, it.front, it.back) }
        offer(LookaheadPicker.nextAfter(cards, index, cfg.aiLookahead, cachedKeys(cards)))
    }

    fun prefetchOnline(anki: AnkiConnect) {
        if (!cfg.aiEnabled || LookaheadWindow.clamp(cfg.aiLookahead) == 0) return
        if (!ExplainQueue.lookaheadOpen()) return
        if (LookaheadWindow.shouldRefresh(sinceRefresh) || ahead.isEmpty()) {
            val limit = (cfg.aiLookahead + 8).coerceAtLeast(10)
            val cards = try {
                val ids = anki.dueCardIds(cfg.deck, limit)
                if (ids.isEmpty()) emptyList() else anki.cardsInfo(ids)
            } catch (_: Exception) {
                emptyList()
            }
            if (cards.isNotEmpty()) {
                ahead = cards
                for (card in cards) notes[card.cardId] = card
            }
            sinceRefresh = 0
        }
        val cards = ahead.map { AheadCard(it.cardId, it.noteId, it.front, it.back) }
        offer(LookaheadPicker.nextUnseen(cards, seen, cfg.aiLookahead, cachedKeys(cards)))
        sinceRefresh += 1
    }

    private fun resolve(anki: AnkiConnect, cardId: Long): OfflineCard? {
        notes[cardId]?.let { return it }
        if (cardId == 0L || cardId in missing) return null
        val card = try {
            anki.cardsInfo(listOf(cardId)).firstOrNull { it.cardId == cardId }
        } catch (_: Exception) {
            null
        }
        if (card == null) {
            missing += cardId
            return null
        }
        notes[cardId] = card
        return card
    }

    private fun record(
        cardId: Long,
        noteId: Long?,
        front: String,
        back: String,
        transcript: String,
        ease: Int
    ) {
        val hash = ContentHash.of(PromptBuilder.PROMPT_VERSION, front, back)
        val noteKey = noteId ?: cardId
        val cached = if (cfg.aiEnabled) cachedText(noteKey, hash) else null
        val turn = ReviewLog.appendAfterGrade(
            ease = ease,
            cardId = cardId,
            noteId = noteId,
            frontText = BubbleModel.fullQuestion(front).ifBlank { AnkiText.stripHtml(front) },
            backText = AnkiText.stripHtml(back),
            contentHash = hash,
            transcript = transcript,
            cachedText = cached,
            aiEnabled = cfg.aiEnabled
        ) ?: return
        if (!cfg.aiEnabled) return
        if (cached != null) {
            ExplainLog.ready(turn.turnId, cached = true)
            return
        }
        ExplainQueue.enqueueAnswered(
            context,
            ExplainKey(noteKey, hash),
            cardId,
            front,
            back,
            turn.turnId
        )
    }

    private fun cachedText(noteKey: Long, hash: String): String? {
        return try {
            AiCache.lookup(context, noteKey, hash, PromptBuilder.PROMPT_VERSION, cfg.aiModel)?.text
        } catch (_: Exception) {
            null
        }
    }

    private fun cachedKeys(cards: List<AheadCard>): Set<ExplainKey> {
        val out = HashSet<ExplainKey>()
        for (card in cards) {
            val hash = ContentHash.of(PromptBuilder.PROMPT_VERSION, card.front, card.back)
            val key = ExplainKey(card.noteKey, hash)
            val hit = try {
                AiCache.has(context, key.noteId, hash, PromptBuilder.PROMPT_VERSION)
            } catch (_: Exception) {
                false
            }
            if (hit) out += key
        }
        return out
    }

    private fun offer(cards: List<AheadCard>) {
        for (card in cards) {
            val hash = ContentHash.of(PromptBuilder.PROMPT_VERSION, card.front, card.back)
            ExplainQueue.enqueueLookahead(
                context,
                ExplainKey(card.noteKey, hash),
                card.cardId,
                card.front,
                card.back
            )
        }
    }

    fun noteSeen(cardId: Long) {
        if (cardId != 0L) seen += cardId
    }

    fun stop() {
        ExplainQueue.onDrillStopped()
    }

    companion object {
        fun start(context: android.content.Context, cfg: AnkiConfig): ExplainSession {
            ReviewLog.clear()
            if (cfg.aiEnabled) ExplainQueue.ensureStarted(context)
            return ExplainSession(context.applicationContext, cfg)
        }
    }
}
