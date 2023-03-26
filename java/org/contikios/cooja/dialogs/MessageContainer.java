package org.contikios.cooja.dialogs;

import org.contikios.cooja.Cooja;

public class MessageContainer 
{

    public final int type;

    public MessageContainer(int t) {
        type = t;
    }

    /** Select UI based on withUI in combination with Cooja.isVisualized. */
    public static MessageList createMessageList(boolean withUI) {
        if (withUI && Cooja.isVisualized()) {
            return new Cooja.RunnableInEDT<MessageList>() {
                @Override
                public MessageList work() {
                    return new MessageListUI();
                }
            }.invokeAndWait();
        } else {
            return new MessageListText();
        }
    }
}
