package wirecli.message

import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.user.UserId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageMentionDetectionTest {
    @Test
    fun `text content detects explicit self mention`() {
        val content =
            MessageContent.Text(
                value = "hello me",
                mentions = listOf(mention(isSelfMention = true)),
            )

        assertTrue(content.mentionsSelf())
    }

    @Test
    fun `text content ignores mentions of other users`() {
        val content =
            MessageContent.Text(
                value = "hello Alice",
                mentions = listOf(mention(isSelfMention = false)),
            )

        assertFalse(content.mentionsSelf())
    }

    @Test
    fun `non-text content does not report a self mention`() {
        assertFalse(MessageContent.Knock(hotKnock = false).mentionsSelf())
    }

    private fun mention(isSelfMention: Boolean) =
        MessageMention(
            start = 0,
            length = 5,
            userId = UserId("me", "example.com"),
            isSelfMention = isSelfMention,
        )
}
