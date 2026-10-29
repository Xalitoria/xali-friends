package mc.xalitoria.friends.paper.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mc.xalitoria.util.message.formatArgs

@Serializable
data class MessageConfig(
    val commands: Commands = Commands(),
    val friend: Friend = Friend(),
    val notify: Notify = Notify(),
    val words: Words = Words(),
) {
    @Serializable
    data class Commands(
        @SerialName("no-permission")
        val noPermission: String = "No permission.",
        @SerialName("player-only")
        val playerCommandOnly: String = "You must be a player to use this command.",
        @SerialName("unexpected-error")
        val unexpectedError: String = "An unexpected error occurred. Please try again.",
        private val help: String = """
            {#a0a>}&lXALI{#fff<}&f&lFRIENDS &7> &dHELP
            &d/{0} &r- open the friend menu
            &d/{0} &radd|remove|accept|deny|favourite <player> &7- accept and deny take 'all' too
            &d/{0} &rlist [page] &7- your friends
            &d/{0} &rrequests [page] &7- requests waiting for you
            &d/{0} &rstatus <text> &7- say what you are up to (&rremovestatus&7 clears it)
            &d/{0} &rtoggle <invites|notifies|msgs|lastseen> &7- change a setting
        """.trimIndent(),
        val reload: Reload = Reload(),
    ) {
        fun help(label: String): String = help.formatArgs(label)

        @Serializable
        data class Reload(
            val pass: String = "Configuration reloaded.",
        )
    }

    @Serializable
    data class Friend(
        @SerialName("target-invalid")
        private val targetInvalid: String = "Player '{0}' not found!",
        val self: String = "You can't do that with yourself!",
        private val sent: String = "Friend request sent to {0}.",
        private val received: String = "{0} wants to be your friend. &d/friend accept {0}&r or &d/friend deny {0}",
        @SerialName("already-friends")
        private val alreadyFriends: String = "You are already friends with {0}.",
        @SerialName("already-received")
        private val alreadyReceived: String = "{0} has already asked you. &d/friend accept {0}",
        @SerialName("already-requested")
        private val alreadyRequested: String = "You already asked {0}.",
        @SerialName("invites-off")
        private val invitesOff: String = "{0} isn't taking friend requests.",
        @SerialName("friend-limit")
        private val friendLimit: String = "You have reached the limit of {0} friends.",
        private val accepted: String = "You are now friends with {0}.",
        @SerialName("accepted-other")
        private val acceptedOther: String = "{0} accepted your friend request.",
        @SerialName("no-request")
        private val noRequest: String = "{0} has not asked you to be friends.",
        private val denied: String = "Turned down {0}'s request.",
        @SerialName("denied-other")
        private val deniedOther: String = "{0} turned down your friend request.",
        private val removed: String = "{0} is no longer your friend.",
        @SerialName("removed-other")
        private val removedOther: String = "{0} ended your friendship.",
        @SerialName("not-friends")
        private val notFriends: String = "{0} is not your friend.",
        @SerialName("favourite-added")
        private val favouriteAdded: String = "{0} is now a favourite.",
        @SerialName("favourite-removed")
        private val favouriteRemoved: String = "{0} is no longer a favourite.",
        @SerialName("toggle-invites")
        private val toggleInvites: String = "Friend requests: {0}",
        @SerialName("toggle-notifies")
        private val toggleNotifies: String = "Friend notifications: {0}",
        @SerialName("toggle-msgs")
        private val toggleMsgs: String = "Private messages from: {0}",
        @SerialName("toggle-last-seen")
        private val toggleLastSeen: String = "Showing when you were last online: {0}",
        @SerialName("status-updated")
        private val statusUpdated: String = "Your status is now '{0}'.",
        @SerialName("status-removed")
        val statusRemoved: String = "Your status was removed.",
        @SerialName("status-too-long")
        private val statusTooLong: String = "A status can be {0} characters at most.",
        @SerialName("list-header")
        private val listHeader: String = "&dFriends &7(page {0}/{1})",
        @SerialName("list-entry")
        private val listEntry: String = "{0} &f{1} &7- {2}",
        @SerialName("requests-header")
        private val requestsHeader: String = "&dRequests &7(page {0}/{1})",
        @SerialName("requests-entry")
        private val requestsEntry: String = "&7- &f{0} &7- {1}",
        @SerialName("page-footer")
        private val pageFooter: String = "&7/{0} {1} to see more",
        @SerialName("no-friends")
        val noFriends: String = "&7You have no friends yet.",
        @SerialName("no-requests")
        val noRequests: String = "&7No requests are waiting.",
        @SerialName("no-menu")
        val noMenu: String = "The friend menu isn't available here; use /friend help.",
    ) {
        fun targetInvalid(name: String) = targetInvalid.formatArgs(name)
        fun sent(name: String) = sent.formatArgs(name)
        fun received(name: String) = received.formatArgs(name)
        fun alreadyFriends(name: String) = alreadyFriends.formatArgs(name)
        fun alreadyReceived(name: String) = alreadyReceived.formatArgs(name)
        fun alreadyRequested(name: String) = alreadyRequested.formatArgs(name)
        fun invitesOff(name: String) = invitesOff.formatArgs(name)
        fun friendLimit(limit: Int) = friendLimit.formatArgs(limit)
        fun accepted(name: String) = accepted.formatArgs(name)
        fun acceptedOther(name: String) = acceptedOther.formatArgs(name)
        fun noRequest(name: String) = noRequest.formatArgs(name)
        fun denied(name: String) = denied.formatArgs(name)
        fun deniedOther(name: String) = deniedOther.formatArgs(name)
        fun removed(name: String) = removed.formatArgs(name)
        fun removedOther(name: String) = removedOther.formatArgs(name)
        fun notFriends(name: String) = notFriends.formatArgs(name)
        fun favouriteAdded(name: String) = favouriteAdded.formatArgs(name)
        fun favouriteRemoved(name: String) = favouriteRemoved.formatArgs(name)
        fun toggleInvites(state: String) = toggleInvites.formatArgs(state)
        fun toggleNotifies(state: String) = toggleNotifies.formatArgs(state)
        fun toggleMsgs(state: String) = toggleMsgs.formatArgs(state)
        fun toggleLastSeen(state: String) = toggleLastSeen.formatArgs(state)
        fun statusUpdated(status: String) = statusUpdated.formatArgs(status)
        fun statusTooLong(max: Int) = statusTooLong.formatArgs(max)
        fun listHeader(page: Int, pages: Int) = listHeader.formatArgs(page, pages)
        fun listEntry(marker: String, name: String, detail: String) = listEntry.formatArgs(marker, name, detail)
        fun requestsHeader(page: Int, pages: Int) = requestsHeader.formatArgs(page, pages)
        fun requestsEntry(name: String, detail: String) = requestsEntry.formatArgs(name, detail)
        fun pageFooter(command: String, next: Int) = pageFooter.formatArgs(command, next)
    }

    @Serializable
    data class Notify(
        private val join: String = "&a{0} &7joined.",
        private val leave: String = "&c{0} &7left.",
        @SerialName("online-friends")
        private val onlineFriends: String = "&7You have &f{0} &7friends online.",
        @SerialName("waiting-requests")
        private val waitingRequests: String = "&7You have &f{0} &7friend requests waiting. &d/friend requests",
    ) {
        fun join(name: String) = join.formatArgs(name)
        fun leave(name: String) = leave.formatArgs(name)
        fun onlineFriends(count: Int) = onlineFriends.formatArgs(count)
        fun waitingRequests(count: Int) = waitingRequests.formatArgs(count)
    }

    /** Short words that go into the messages and the menus' placeholders. */
    @Serializable
    data class Words(
        val on: String = "&aOn",
        val off: String = "&cOff",
        val everyone: String = "&aEveryone",
        @SerialName("friends-only")
        val friendsOnly: String = "&eFriends only",
        val nobody: String = "&cNobody",
        val online: String = "&aOnline",
        val offline: String = "&7Offline",
        @SerialName("last-seen")
        private val lastSeen: String = "last seen {0}",
        @SerialName("last-seen-hidden")
        val lastSeenHidden: String = "last seen hidden",
        val never: String = "never",
        @SerialName("no-status")
        val noStatus: String = "-",
        @SerialName("favourite-marker")
        val favouriteMarker: String = "&6★",
        @SerialName("plain-marker")
        val plainMarker: String = "&7-",
    ) {
        fun lastSeen(date: String) = lastSeen.formatArgs(date)
    }
}
