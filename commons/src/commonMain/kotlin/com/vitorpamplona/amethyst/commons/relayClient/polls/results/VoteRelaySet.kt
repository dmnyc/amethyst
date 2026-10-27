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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

/**
 * The relays to ask for a poll's votes: the poll's own `relay` tags (NIP-88's
 * canonical home for ballots) plus the reaction relays (the author's inbox plus
 * where we saw the poll). When neither names anything, the curated defaults
 * stand in rather than leaving the poll uncountable.
 *
 * The subscription that fetches the ballots and the COUNT that estimates what
 * it missed build their relay list through this one function, so the bars and
 * the vote total cannot disagree.
 */
fun voteRelaySet(
    poll: PollEvent?,
    reactionRelays: List<NormalizedRelayUrl>,
): Set<NormalizedRelayUrl> {
    val named = poll?.relays().orEmpty() + reactionRelays
    return named.ifEmpty { Constants.eventFinderRelays.toList() }.toSet()
}

/**
 * Where a respondent's ballot should be broadcast: the voter's own write
 * relays (so the voter keeps a copy) plus the poll's `relay` tags, the
 * canonical home NIP-88 asks respondents to use. A poll without `relay` tags
 * substitutes the author's inbox relays, so the vote still reaches the
 * author's corner of the network.
 */
fun pollBroadcastTargets(
    voterOutbox: Set<NormalizedRelayUrl>,
    pollRelays: List<NormalizedRelayUrl>,
    authorInbox: List<NormalizedRelayUrl>,
): Set<NormalizedRelayUrl> = voterOutbox + pollRelays + (if (pollRelays.isEmpty()) authorInbox else emptyList())
