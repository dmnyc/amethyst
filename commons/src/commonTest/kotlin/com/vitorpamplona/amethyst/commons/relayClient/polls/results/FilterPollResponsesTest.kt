/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.relayClient.polls.results

import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relays.MutableTime
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FilterPollResponsesTest {
    private val pollId = "a".repeat(64)

    private fun norm(url: String) = RelayUrlNormalizer.normalize(url)

    /** A kind-1068 poll with the given `relay` tags, seen on [seenOn], from an author with no inbox loaded. */
    private fun pollNote(
        relayTags: List<String> = emptyList(),
        seenOn: List<NormalizedRelayUrl> = emptyList(),
    ): Note {
        val event =
            PollEvent(
                id = pollId,
                pubKey = "1".repeat(64),
                createdAt = 1000,
                tags = relayTags.map { arrayOf("relay", it) }.toTypedArray(),
                content = "Question?",
                sig = "0".repeat(128),
            )
        val note = Note(pollId)
        note.loadEvent(event, User("1".repeat(64)) { addr -> Note(addr.toValue()) }, emptyList())
        seenOn.forEach { note.addRelay(it) }
        return note
    }

    @Test
    fun voteQueryIsThePollRelayTagsPlusReactionRelays() {
        val note =
            pollNote(
                relayTags = listOf("wss://relay.azzamo.net", "wss://relay.primal.net"),
                seenOn = listOf(norm("wss://nostr.wine"), norm("wss://relay.azzamo.net")),
            )

        val relays = filterPollResponses(note, since = null)?.map { it.relay.url }

        // Deduplicated, poll tags first: relay.azzamo.net is named by both the poll and
        // the seen-on list, and appears once, at the poll's position.
        assertEquals(listOf("wss://relay.azzamo.net/", "wss://relay.primal.net/", "wss://nostr.wine/"), relays)
    }

    @Test
    fun aPollWithNoRelayHintsFallsBackToTheCuratedDefaults() {
        // No relay tags, nothing seen anywhere, author inbox empty: without the fallback
        // this poll would be silently uncountable.
        val filters = filterPollResponses(pollNote(), since = null)

        val byRelay = assertNotNull(filters).associateBy { it.relay.url }
        assertEquals(Constants.eventFinderRelays.map { it.url }.toSet(), byRelay.keys)

        val sample = byRelay.getValue("wss://nostr.wine/")
        assertEquals(listOf(PollResponseEvent.KIND), sample.filter.kinds)
        assertEquals(listOf(pollId), sample.filter.tags?.get("e"))
    }

    @Test
    fun sinceCarriesOverPerRelay() {
        val note = pollNote(relayTags = listOf("wss://relay.azzamo.net"))
        val since: SincePerRelayMap = mutableMapOf(norm("wss://relay.azzamo.net") to MutableTime(500))

        val filters = assertNotNull(filterPollResponses(note, since)).associateBy { it.relay.url }

        assertEquals(500, filters.getValue("wss://relay.azzamo.net/").filter.since)
    }

    @Test
    fun broadcastTargetsAlwaysIncludeTheVotersOutboxAndThePollRelayTags() {
        val targets =
            pollBroadcastTargets(
                voterOutbox = setOf(norm("wss://voter.example"), norm("wss://nos.lol")),
                pollRelays = listOf(norm("wss://relay.azzamo.net"), norm("wss://nos.lol")),
                authorInbox = listOf(norm("wss://ignored-when-tags-exist.example")),
            )

        assertEquals(setOf("wss://voter.example/", "wss://nos.lol/", "wss://relay.azzamo.net/"), targets.map { it.url }.toSet())
    }

    @Test
    fun aPollWithoutRelayTagsSubstitutesTheAuthorsInbox() {
        val targets =
            pollBroadcastTargets(
                voterOutbox = setOf(norm("wss://voter.example")),
                pollRelays = emptyList(),
                authorInbox = listOf(norm("wss://author-inbox.example")),
            )

        assertEquals(setOf("wss://voter.example/", "wss://author-inbox.example/"), targets.map { it.url }.toSet())
    }
}
