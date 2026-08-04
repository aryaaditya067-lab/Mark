package com.example.mark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mark.model.Message
import com.example.mark.ui.theme.MarkEmber
import com.example.mark.ui.theme.MarkLine
import com.example.mark.ui.theme.MarkMuted
import com.example.mark.utils.toChatTime

/**
 * Moonstone chat bubbles.
 *
 *  - Mark's replies: charcoal surface with a hairline border, warm off-white text.
 *  - User: quiet dark bubble with an ember edge — the accent marks WHO is
 *    speaking without shouting a solid orange block at the reader.
 *  - Tool replies: the ember rule line, unchanged in spirit, refined in weight.
 */
@Composable
fun MessageBubble(message: Message) {
    val isUser = message.isUser
    val content = message.content ?: return

    if (message.isToolReply) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MarkEmber)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = content,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 18.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(MarkEmber.copy(alpha = 0.14f))
                        .border(1.dp, MarkEmber.copy(alpha = 0.35f), shape)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MarkLine, shape)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = message.createdAt.toChatTime(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MarkMuted,
                textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}